(ns behavioral.behavioralopsllm
  "BehavioralOps-LLM client -- the *contained intelligence node* for
  the behavioral-care actor.

  It normalizes resident-intake, drafts a per-jurisdiction behavioral-
  health-facility evidence checklist, screens residents for an
  unresolved medication-adherence flag, drafts the treatment-plan-
  finalization action, and drafts the crisis-response-finalization
  action. CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record or a real treatment-plan/crisis-response
  finalization. Every output is censored downstream by `behavioral.
  governor` before anything touches the SSoT, and `:treatment-plan/
  finalize`/`:crisis-response/finalize` proposals NEVER auto-commit at
  any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/finalize-treatment-plan | :actuation/finalize-crisis-response | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [behavioral.facts :as facts]
            [behavioral.registry :as registry]
            [behavioral.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the resident, resident/staff-count figures or
  jurisdiction. High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "入所者記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :resident/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction behavioral-health-facility evidence checklist
  draft. `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `behavioral.facts` -- the Behavioral Care Governor must reject
  this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [r (store/resident db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction r))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "behavioral.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-medication-adherence
  "Medication-adherence screening draft. `:medication-adherence-
  flag?` on the resident record injects the failure mode: the
  Behavioral Care Governor must HOLD, un-overridably, on any
  unresolved concern."
  [db {:keys [subject]}]
  (let [r (store/resident db subject)]
    (cond
      (nil? r)
      {:summary "対象入所者記録が見つかりません" :rationale "no resident record"
       :cites [] :effect :medication-screen/set :value {:resident-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:medication-adherence-flag? r))
      {:summary    (str (:resident-name r) ": 服薬アドヒアランス懸念を検出")
       :rationale  "スクリーニングが未解決の服薬アドヒアランス懸念を検出。人手確認とホールドが必須。"
       :cites      [:medication-check]
       :effect     :medication-screen/set
       :value      {:resident-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:resident-name r) ": 服薬アドヒアランス懸念なし")
       :rationale  "服薬アドヒアランススクリーニング完了。"
       :cites      [:medication-check]
       :effect     :medication-screen/set
       :value      {:resident-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- propose-treatment-plan-finalization
  "Draft the actual TREATMENT-PLAN-FINALIZATION action -- finalizing a
  real resident's treatment plan. ALWAYS `:stake :actuation/finalize-
  treatment-plan` -- this is a REAL-WORLD resident-record act, never a
  draft the actor may auto-run. See README `Actuation`: no phase ever
  adds this op to a phase's `:auto` set (`behavioral.phase`); the
  governor also always escalates on `:actuation/finalize-treatment-
  plan`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [r (store/resident db subject)]
    {:summary    (str subject " 向け療養計画確定提案"
                      (when r (str " (resident=" (:resident-name r) ")")))
     :rationale  (if r
                   (str "medication-adherence-flag?=" (:medication-adherence-flag? r))
                   "入所者記録が見つかりません")
     :cites      (if r [subject] [])
     :effect     :resident/mark-treatment-planned
     :value      {:resident-id subject}
     :stake      :actuation/finalize-treatment-plan
     :confidence (if (and r (not (:medication-adherence-flag? r))) 0.9 0.3)}))

(defn- propose-crisis-response-finalization
  "Draft the actual CRISIS-RESPONSE-FINALIZATION action -- finalizing
  a real crisis response for a resident. ALWAYS `:stake :actuation/
  finalize-crisis-response` -- this is a REAL-WORLD resident-record
  act, never a draft the actor may auto-run. See README `Actuation`:
  no phase ever adds this op to a phase's `:auto` set (`behavioral.
  phase`); the governor also always escalates on `:actuation/finalize-
  crisis-response`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [r (store/resident db subject)]
    {:summary    (str subject " 向け危機対応確定提案"
                      (when r (str " (resident=" (:resident-name r) ")")))
     :rationale  (if r
                   (str "current-resident-count=" (:current-resident-count r)
                        " current-staff-count=" (:current-staff-count r))
                   "入所者記録が見つかりません")
     :cites      (if r [subject] [])
     :effect     :resident/mark-crisis-responded
     :value      {:resident-id subject}
     :stake      :actuation/finalize-crisis-response
     :confidence (if (and r (not (registry/supervision-ratio-insufficient? r))) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :resident/intake              (normalize-intake db request)
    :jurisdiction/assess          (assess-jurisdiction db request)
    :medication-adherence/screen  (screen-medication-adherence db request)
    :treatment-plan/finalize      (propose-treatment-plan-finalization db request)
    :crisis-response/finalize     (propose-crisis-response-finalization db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは行動保健施設の療養計画確定・危機対応確定エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:resident/upsert|:assessment/set|:medication-screen/set|"
       ":resident/mark-treatment-planned|:resident/mark-crisis-responded) "
       ":stake(:actuation/finalize-treatment-plan か :actuation/finalize-crisis-response か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :jurisdiction/assess          {:resident (store/resident st subject)}
    :medication-adherence/screen  {:resident (store/resident st subject)}
    :treatment-plan/finalize      {:resident (store/resident st subject)}
    :crisis-response/finalize     {:resident (store/resident st subject)}
    {:resident (store/resident st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Behavioral Care Governor
  escalates/holds -- an LLM hiccup can never auto-finalize a treatment
  plan or auto-finalize a crisis response."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :behavioralopsllm-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
