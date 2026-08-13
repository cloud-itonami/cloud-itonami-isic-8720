(ns behavioral.render-html
  "Build-time renderer for `docs/samples/operator-console.html`.

  This namespace does NOT describe the actor -- it RUNS it. Every id,
  status, verdict, rule, record number and count on the generated page
  comes out of one real execution of this repo's own stack:

      behavioral.operation  (langgraph StateGraph, `g/run*`)
        -> behavioral.behavioralopsllm  (contained advisor node)
        -> behavioral.governor          (independent censor)
        -> behavioral.phase             (rollout gate)
        -> behavioral.store             (MemStore SSoT + append-only ledger)

  over `behavioral.store/demo-data`'s real seed residents. Nothing on
  the page is written by hand. This matters more here than in most of
  this fleet: ISIC 8720 is RESIDENTIAL BEHAVIOURAL HEALTH CARE, so a
  fabricated resident, diagnosis or clinical status on a page that
  looks like an operator console would be an invented clinical record.
  If a value cannot be derived from the run, it is not printed.

  Three build-time invariants, all enforced (not documented):

    1. `-main` REFUSES to write the file if the run produced zero HARD
       governor holds (`hard-hold-invariant!`). A console whose whole
       point is showing that the censor can refuse is worthless if the
       censor never refused; a silent zero must fail the build, not
       render an empty section.
    2. Ledger facts are classified on their FACT TYPE first, never on
       `:violations` (`fact-class`). `:approval-rejected` carries
       `[{:rule :approver-rejected}]`, so a `:violations`-keyed counter
       silently reports 6 governor refusals where there are 5 -- the
       governor CLEARED that proposal and a human declined it. A
       `:phase-disabled` hold is likewise not a governor refusal: the
       rollout gate stopped it, the censor had nothing against it.
       `classify-ledger!` re-checks every classification against an
       INDEPENDENT signal (`:basis`) and throws on disagreement.
    3. Approver attribution is MEASURED at render time
       (`attribution-report`), never asserted. The page reports which
       effects kept the approver and which dropped it by scanning the
       registers this run actually produced -- so if someone fixes the
       store, the page stops claiming the defect on its own.

  Output is byte-identical across runs: no wall-clock, no UUID, no
  per-run identifier reaches the page, and every map/set is rendered
  through an explicit sorted column list rather than by printing a
  Clojure map whose key order is incidental.

  Regenerate:  clojure -M:dev:render-html [out-file]"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [jp-go-dds.skin :as skin]
            [behavioral.facts :as facts]
            [behavioral.governor :as governor]
            [behavioral.operation :as op]
            [behavioral.phase :as phase]
            [behavioral.registry :as registry]
            [behavioral.store :as store]
            [langgraph.graph :as g]))

(def ^:private default-out "docs/samples/operator-console.html")

;; The EXECUTING actor and the HUMAN APPROVER are given deliberately
;; DIFFERENT ids. `behavioral.operation/commit-fact` stamps every ledger
;; commit with `:actor (:actor-id context)` -- the actor that ran the
;; graph, NOT the professional who approved it. `behavioral.sim` uses
;; "op-1" for both, which makes a wrong reading ("`:actor` is the
;; approver") agree with the right one on every row it prints. Keeping
;; the two strings distinct here is what lets `attribution-report`
;; actually decide the question instead of guessing it.
(def ^:private actor-id "op-1")
(def ^:private approver-id "approver-1")

(defn- ctx [phase]
  {:actor-id actor-id :actor-role :behavioral-health-professional :phase phase})

;; ----------------------------- scenarios -----------------------------

(def scenarios
  "The run, as data. `:approve` is what the human operator does when the
  actor interrupts for approval (`:approved` / `:rejected` / nil = the
  actor never got that far). `:intent` is prose for the page and is NOT
  used to classify anything -- outcomes are read back off the ledger."
  [{:tid "t01" :phase 3
    :request {:op :resident/intake :subject "resident-1"
              :patch {:id "resident-1" :resident-name "Sakura Tanaka"}}
    :intent "phase 3 の :auto に入る唯一の op。governor が clean なら人を介さず commit。"}

   {:tid "t02" :phase 2
    :request {:op :resident/intake :subject "resident-3"
              :patch {:id "resident-3" :resident-name "鈴木一郎"}}
    :approve :approved
    :intent "同じ op を phase 2 で。書き込みは許可だが :auto ではないので必ず人に上がる（:phase-approval）。"}

   {:tid "t03" :phase 3
    :request {:op :jurisdiction/assess :subject "resident-1"}
    :approve :approved
    :intent "JPN の公式 spec-basis を引いた必要書類チェックリスト。phase 3 でも :auto ではない。"}

   {:tid "t04" :phase 3
    :request {:op :medication-adherence/screen :subject "resident-1"}
    :approve :approved
    :intent "服薬アドヒアランスのスクリーニング（懸念なし）。どの phase でも自動化しない。"}

   {:tid "t05" :phase 3
    :request {:op :treatment-plan/finalize :subject "resident-1"}
    :approve :approved
    :intent "実在の療養計画を確定する actuation。governor の high-stakes と phase の :auto 不在が独立に人を要求する。"}

   {:tid "t06" :phase 3
    :request {:op :crisis-response/finalize :subject "resident-1"}
    :approve :approved
    :intent "実在の危機対応を確定する actuation。同じく二層が独立に人を要求する。"}

   {:tid "t07" :phase 3
    :request {:op :jurisdiction/assess :subject "resident-2" :no-spec? true}
    :intent "REFUSAL: behavioral.facts に無い法域（ATL）の要件を提案させる。要件の捏造は HARD hold。"}

   {:tid "t08" :phase 3
    :request {:op :jurisdiction/assess :subject "resident-3"}
    :approve :approved
    :intent "resident-3 を JPN の必要書類で assess しておく（t09 の evidence 前提を満たすため）。"}

   {:tid "t09" :phase 3
    :request {:op :crisis-response/finalize :subject "resident-3"}
    :intent "REFUSAL: resident-3 の入所者/職員比を governor が独立に再計算する。"}

   {:tid "t10" :phase 3
    :request {:op :medication-adherence/screen :subject "resident-4"}
    :intent "REFUSAL: スクリーニング自身が未解決の服薬アドヒアランス懸念を検出。人に上げずその場で止まる。"}

   {:tid "t11" :phase 3
    :request {:op :treatment-plan/finalize :subject "resident-1"}
    :intent "REFUSAL: t05 で確定済みの療養計画を二重確定させにいく。"}

   {:tid "t12" :phase 3
    :request {:op :crisis-response/finalize :subject "resident-1"}
    :intent "REFUSAL: t06 で確定済みの危機対応を二重確定させにいく。"}

   {:tid "t13" :phase 1
    :request {:op :jurisdiction/assess :subject "resident-1"}
    :intent "CONTRAST: phase 1 は :jurisdiction/assess を書けない。止めたのは rollout gate であって censor ではない。"}

   {:tid "t14" :phase 3
    :request {:op :medication-adherence/screen :subject "resident-3"}
    :approve :rejected
    :intent "CONTRAST: governor は通した（clean）が、人が却下した。この fact は :violations を持つが governor の拒否ではない。"}])

(defn run-demo!
  "Executes `scenarios` in order against ONE MemStore through ONE
  compiled OperationActor, and records what actually happened -- the
  langgraph status, the governor verdict, the escalation reason the
  graph itself emitted, the commit record the store was handed, and the
  exact ledger slice the scenario produced. Nothing here decides an
  outcome; it only observes one."
  []
  (let [db    (store/seed-db)
        actor (op/build db)
        runs  (reduce
               (fn [acc {:keys [tid phase request approve intent]}]
                 (let [before (count (store/ledger db))
                       r1     (g/run* actor {:request request :context (ctx phase)}
                                      {:thread-id tid})
                       r2     (when (and approve (= :interrupted (:status r1)))
                                (g/run* actor
                                        {:approval {:status approve :by approver-id}}
                                        {:thread-id tid :resume? true}))
                       final  (or r2 r1)
                       audit  (get-in final [:state :audit])
                       after  (count (store/ledger db))]
                   (conj acc
                         {:tid          tid
                          :phase        phase
                          :op           (:op request)
                          :subject      (:subject request)
                          :intent       intent
                          :first-status (:status r1)
                          :paused?      (= :interrupted (:status r1))
                          :approval     (when r2 approve)
                          :verdict      (get-in final [:state :verdict])
                          :disposition  (get-in final [:state :disposition])
                          :record       (get-in final [:state :record])
                          :escalation   (first (filter #(= :approval-requested (:t %)) audit))
                          :ledger-from  before
                          :ledger-to    after})))
               [] scenarios)]
    {:db db :runs runs}))

;; ----------------------------- classification -----------------------------

(defn fact-class
  "Classify ONE append-only ledger fact.

  The FACT TYPE decides first. `:violations` is deliberately not
  consulted: `:approval-rejected` facts carry
  `[{:rule :approver-rejected}]`, and a classifier keyed on
  `:violations` therefore counts a human's decision as the censor's.
  Among `:governor-hold` facts, a `:phase-reason` means the rollout
  gate stopped it (`behavioral.phase/gate` only attaches a reason when
  the GOVERNOR's own disposition was not `:hold`), so only a
  `:governor-hold` with no `:phase-reason` is a governor refusal."
  [f]
  (case (:t f)
    :committed         :commit
    :approval-rejected :approver-rejection
    :governor-hold     (if (:phase-reason f) :phase-gate-hold :governor-refusal)
    :unknown))

(defn classify-ledger!
  "Classifies every fact and CROSS-CHECKS each class against an
  independent signal in the same fact, throwing on any disagreement --
  a classifier nobody contradicts is a classifier nobody has tested.

    governor refusal   -> `:basis` non-empty, and equal to the rules of
                          `:violations` (the censor named what it refused)
    phase gate hold    -> `:basis` empty (the censor had nothing to say)
    approver rejection -> `:basis` is exactly [:approver-rejected]
    commit             -> carries no `:violations` at all

  Also enforces an evidence floor: an empty ledger is not a clean run,
  it is a run that did not happen."
  [ledger]
  (when (empty? ledger)
    (throw (ex-info "empty ledger: the actor produced no facts at all" {})))
  (let [tagged (mapv (fn [f] (assoc f ::class (fact-class f))) ledger)]
    (doseq [[i f] (map-indexed vector tagged)]
      (let [basis (vec (:basis f))
            rules (mapv :rule (:violations f))]
        (case (::class f)
          :governor-refusal
          (when-not (and (seq basis) (= basis rules))
            (throw (ex-info "governor refusal disagrees with its own basis"
                            {:index i :fact f :basis basis :rules rules})))
          :phase-gate-hold
          (when (seq basis)
            (throw (ex-info "phase gate hold carries a governor basis"
                            {:index i :fact f :basis basis})))
          :approver-rejection
          (when-not (= [:approver-rejected] basis)
            (throw (ex-info "approver rejection carries an unexpected basis"
                            {:index i :fact f :basis basis})))
          :commit
          (when (seq (:violations f))
            (throw (ex-info "commit fact carries violations"
                            {:index i :fact f})))
          (throw (ex-info "unclassifiable ledger fact" {:index i :fact f})))))
    tagged))

(defn hard-hold-invariant!
  "BUILD-TIME INVARIANT. The whole claim of this page is that the
  Behavioral Care Governor can refuse. If this run produced no HARD
  governor hold, the page would be a page about nothing, so the build
  fails and NO file is written.

  Deliberately keyed on `:governor-refusal` only: neither a
  `:phase-disabled` rollout hold nor a human's `:approval-rejected`
  satisfies it, because neither is the censor refusing."
  [tagged]
  (let [refusals (filterv #(= :governor-refusal (::class %)) tagged)]
    (when (empty? refusals)
      (throw (ex-info (str "REFUSING to write the operator console: this run produced "
                           "0 HARD governor holds. Scanned " (count tagged)
                           " ledger facts; classes present: "
                           (str/join ", " (sort (map name (distinct (map ::class tagged))))) ".")
                      {:scanned (count tagged)})))
    refusals))

;; ----------------------------- approver attribution -----------------------------

(defn- approver-shaped-keys
  "Keys in `m` that could name a human approver. Matches any key whose
  name contains \"approv\", plus the bare `:by` the approval facts use.

  `:actor` deliberately does NOT match: it is the id of the actor that
  EXECUTED the graph, and reading it as the approver is the one wrong
  answer that agrees with the right one whenever the two ids happen to
  be the same string (as they are in `behavioral.sim`)."
  [m]
  (when (map? m)
    (->> (keys m)
         (filter (fn [k]
                   (let [n (if (keyword? k) (name k) (str k))]
                     (or (some? (re-find #"(?i)approv" n)) (= "by" n)))))
         (sort-by str)
         vec)))

(defn- register-entries-for
  "The SSoT rows a committed effect actually wrote, read back through the
  Store protocol -- so the scan sees what a later operator would see,
  not what the actor intended to write."
  [db effect path]
  (let [id (first path)]
    (case effect
      :resident/upsert
      [["resident register" (store/resident db id)]]

      :assessment/set
      [["assessment register" (store/assessment-of db id)]]

      :medication-screen/set
      [["medication-screen register" (store/medication-screen-of db id)]]

      :resident/mark-treatment-planned
      [["resident register" (store/resident db id)]
       ["treatment-plan history" (first (filter #(= id (get % "resident_id"))
                                                (store/treatment-plan-history db)))]]

      :resident/mark-crisis-responded
      [["resident register" (store/resident db id)]
       ["crisis-response history" (first (filter #(= id (get % "resident_id"))
                                                 (store/crisis-response-history db)))]]
      [])))

(defn attribution-report
  "MEASURES, for every commit a human actually approved in this run,
  whether the approver survived into the SSoT -- by scanning the
  registers for approver-shaped keys, not by knowing the answer.

  If the store is fixed later, this scan reports the fix by itself."
  [db runs]
  (let [approved (filterv #(and (= :approved (:approval %))
                                (= :commit (:disposition %))
                                (:record %))
                          runs)
        probes (mapv (fn [{:keys [tid op record]}]
                       (let [{:keys [effect path]} record
                             regs (register-entries-for db effect path)]
                         {:tid tid :op op :effect effect :subject (first path)
                          :in-record (approver-shaped-keys (:payload record))
                          :registers (mapv (fn [[label entry]]
                                             {:label label
                                              :found? (some? entry)
                                              :keys (approver-shaped-keys entry)})
                                           regs)}))
                     approved)
        ledger-commits (filterv #(= :committed (:t %)) (store/ledger db))]
    {:probes probes
     :retained (filterv (fn [p] (some (comp seq :keys) (:registers p))) probes)
     :dropped  (filterv (fn [p] (not-any? (comp seq :keys) (:registers p))) probes)
     :ledger-commits (count ledger-commits)
     :ledger-with-approver (count (filterv (comp seq approver-shaped-keys) ledger-commits))
     :ledger-actors (vec (sort (distinct (keep :actor ledger-commits))))
     :approver approver-id}))

;; ----------------------------- html -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- cell
  "Escapes a value for a table cell. An absent value becomes an explicit
  em-dash -- never the string \"nil\", and never a guessed substitute."
  [v]
  (if (or (nil? v) (and (coll? v) (empty? v))) "&mdash;" (esc v)))

(defn- kw [v] (if (keyword? v) (name v) (str v)))

(defn- code [v] (str "<code>" (esc v) "</code>"))

(defn- codes [coll]
  (if (seq coll) (str/join " " (map #(code (kw %)) coll)) "&mdash;"))

(defn- row [cells] (str "<tr>" (apply str (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- head-row [cells] (str "<tr>" (apply str (map #(str "<th>" (esc %) "</th>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "<table>\n<thead>\n" (head-row headers) "\n</thead>\n<tbody>\n"
       (str/join "\n" rows) "\n</tbody>\n</table>"))

(defn- disposition-cell [d]
  (case d
    :commit   "<span class=\"ok\">commit</span>"
    :escalate "<span class=\"warn\">escalate</span>"
    :hold     "<span class=\"err\">hold</span>"
    (cell d)))

(defn- class-cell [c]
  (case c
    :commit             "<span class=\"ok\">commit</span>"
    :governor-refusal   "<span class=\"err\">governor refusal</span>"
    :phase-gate-hold    "<span class=\"warn\">phase gate hold</span>"
    :approver-rejection "<span class=\"warn\">approver rejection</span>"
    (cell c)))

;; ----------------------------- sections -----------------------------

(defn- section-header [blueprint refusals tagged]
  (str "<h1>Behavioral Care Governor &mdash; operator console</h1>\n"
       "<p class=\"bar\">"
       "<span class=\"badge\">ISIC " (esc (:itonami.blueprint/isic-rev5 blueprint)) "</span>"
       "<span class=\"badge\">" (esc (kw (:itonami.blueprint/domain blueprint))) "</span>"
       "<span class=\"badge\">governor: " (esc (kw (:itonami.blueprint/governor blueprint))) "</span>"
       "</p>\n"
       "<p class=\"subtitle\">" (esc (:itonami.blueprint/name blueprint)) "</p>\n"
       "<div class=\"banner\">\n"
       "<p><strong>このページは実行結果です。</strong> "
       "<code>behavioral.render-html</code> が <code>behavioral.operation</code> の "
       "langgraph StateGraph を <code>behavioral.store/demo-data</code> の実 seed "
       "入所者に対して実際に走らせ、その 1 回の実行が出した fact だけを描画しています。"
       "手で書いた入所者・診断名・臨床ステータスは 1 件もありません。</p>\n"
       "<p>この実行が書いた append-only ledger は <strong class=\"num\">" (count tagged)
       "</strong> fact、うち governor が un-overridable に拒否したものが "
       "<strong class=\"err num\">" (count refusals) "</strong> 件です。"
       "governor の拒否が 0 件なら <code>-main</code> は例外を投げてファイルを書きません"
       "（<code>hard-hold-invariant!</code>）。</p>\n"
       "</div>"))

(defn- section-refusals [refusals]
  (str "<h2>HARD governor holds &mdash; " (count refusals) " 件</h2>\n"
       "<p>いずれも <code>behavioral.governor/check</code> が <code>:hard? true</code> を返したもので、"
       "人間の承認では覆せません（承認ノードに到達すらしない）。"
       "各行の rule / detail は governor 自身が生成した violation そのものです。</p>\n"
       (table ["#" "op" "subject" "rule" "governor が拒否した理由 (detail)" "advisor confidence"]
              (map (fn [f]
                     (row [(str "<span class=\"num\">" (cell (::index f)) "</span>")
                           (code (kw (:op f)))
                           (cell (:subject f))
                           (str/join " " (map #(str "<span class=\"err\">" (code (kw (:rule %))) "</span>")
                                              (:violations f)))
                           (str/join "<br>" (map #(esc (:detail %)) (:violations f)))
                           (str "<span class=\"num\">" (cell (:confidence f)) "</span>")]))
                   refusals))))

(defn- section-classes [tagged]
  (let [counts (frequencies (map ::class tagged))
        rej    (first (filter #(= :approver-rejection (::class %)) tagged))
        phase  (first (filter #(= :phase-gate-hold (::class %)) tagged))]
    (str "<h2>拒否の種類を数え分ける</h2>\n"
         "<p>この 3 つは全部「commit されなかった」ですが、<strong>別の主体が別の理由で</strong>止めています。"
         "混ぜて数えると governor の拒否件数が狂います。"
         "<code>fact-class</code> は <strong>fact の型</strong>で先に分け、"
         "<code>:violations</code> では分けません &mdash; "
         "<code>classify-ledger!</code> が全 fact の分類を独立な signal（<code>:basis</code>）と"
         "突き合わせ、食い違えばビルドを落とします。</p>\n"
         (table ["分類" "止めたのは" "この実行での件数" "根拠にした signal"]
                [(row [(class-cell :commit) "誰も止めていない"
                       (str "<span class=\"num\">" (get counts :commit 0) "</span>")
                       (str (code ":t") " = " (code ":committed"))])
                 (row [(class-cell :governor-refusal) "Behavioral Care Governor（censor）"
                       (str "<span class=\"num err\">" (get counts :governor-refusal 0) "</span>")
                       (str (code ":t") " = " (code ":governor-hold") " かつ "
                            (code ":phase-reason") " 無し")])
                 (row [(class-cell :phase-gate-hold) "rollout phase gate（censor ではない）"
                       (str "<span class=\"num\">" (get counts :phase-gate-hold 0) "</span>")
                       (str (code ":phase-reason") " 有り、" (code ":basis") " は空")])
                 (row [(class-cell :approver-rejection) "人間の承認者"
                       (str "<span class=\"num\">" (get counts :approver-rejection 0) "</span>")
                       (str (code ":t") " = " (code ":approval-rejected"))])])
         "<div class=\"note\">\n"
         "<p><strong>なぜ <code>:violations</code> で数えてはいけないか（この実行で実際に起きた形）。</strong></p>\n"
         (if rej
           (str "<p>t14 の却下 fact は <code>:t " (esc (kw (:t rej)))
                "</code> で、<code>:violations "
                (esc (pr-str (mapv :rule (:violations rej))))
                "</code> を <strong>持っています</strong>。"
                "しかし governor 側の判定は clean（この提案は escalate されて人に渡っている）で、"
                "止めたのは承認者です。<code>:violations</code> が空でないことを governor の拒否の印にすると、"
                "この 1 件が governor の拒否として数えられ、"
                "件数は " (get counts :governor-refusal 0) " ではなく "
                (+ (get counts :governor-refusal 0) (get counts :approver-rejection 0))
                " になります。</p>\n")
           "<p>この実行には承認者による却下がありませんでした。</p>\n")
         (if phase
           (str "<p>逆に t13 の phase hold は <code>:basis</code> が空で "
                "<code>:phase-reason " (esc (kw (:phase-reason phase)))
                "</code> を持ちます &mdash; phase " (esc (:phase phase))
                " は <code>" (esc (kw (:op phase)))
                "</code> をまだ書けないので rollout gate が止めました。"
                "governor はこの提案に何の違反も見つけていません。</p>\n")
           "")
         "</div>")))

(defn- section-runs [runs]
  (str "<h2>実行したシナリオ &mdash; " (count runs) " 件</h2>\n"
       "<p>上から順に、1 つの MemStore と 1 つのコンパイル済み OperationActor に対して実行したものです。"
       "<code>paused</code> は langgraph が <code>interrupt-before #{:request-approval}</code> で"
       "実際に停止し、人間の再開を待ったことを意味します。</p>\n"
       (table ["thread" "phase" "op" "subject" "paused" "承認者の判断" "escalation reason"
               "最終 disposition" "ledger 行" "このシナリオの狙い"]
              (map (fn [r]
                     (row [(code (:tid r))
                           (str "<span class=\"num\">" (cell (:phase r)) "</span>")
                           (code (kw (:op r)))
                           (cell (:subject r))
                           (if (:paused? r) "<span class=\"warn\">yes</span>"
                               "<span class=\"muted\">no</span>")
                           (case (:approval r)
                             :approved "<span class=\"ok\">approved</span>"
                             :rejected "<span class=\"err\">rejected</span>"
                             "<span class=\"muted\">&mdash;</span>")
                           (if-let [e (:escalation r)] (code (kw (:reason e))) "&mdash;")
                           (disposition-cell (:disposition r))
                           (if (< (:ledger-from r) (:ledger-to r))
                             (str "<span class=\"num\">"
                                  (str/join ", " (range (:ledger-from r) (:ledger-to r)))
                                  "</span>")
                             "&mdash;")
                           (esc (:intent r))]))
                   runs))))

(defn- section-ledger [tagged]
  (str "<h2>append-only 監査 ledger &mdash; " (count tagged) " fact</h2>\n"
       "<p><code>behavioral.store/append-ledger!</code> が実行順に積んだものをそのまま出しています。"
       "<code>actor</code> 列は graph を <strong>実行した</strong> actor の id であり、"
       "承認者ではありません（下記「承認者の追跡可能性」参照）。</p>\n"
       (table ["#" "fact type" "分類" "op" "subject" "basis" "summary / detail" "confidence"]
              (map (fn [f]
                     (row [(str "<span class=\"num\">" (cell (::index f)) "</span>")
                           (code (kw (:t f)))
                           (class-cell (::class f))
                           (code (kw (:op f)))
                           (cell (:subject f))
                           (codes (:basis f))
                           (if (seq (:violations f))
                             (str/join "<br>" (map #(esc (:detail %)) (:violations f)))
                             (cell (:summary f)))
                           (if (some? (:confidence f))
                             (str "<span class=\"num\">" (esc (:confidence f)) "</span>")
                             "&mdash;")]))
                   tagged))))

(defn- section-residents [db]
  (let [rs (store/all-residents db)]
    (str "<h2>入所者レジスタ（実行後の SSoT） &mdash; " (count rs) " 名</h2>\n"
         "<p><code>behavioral.store/demo-data</code> の seed に、この実行が commit した変更だけが"
         "反映された状態です。<code>plan-number</code> / <code>response-number</code> は "
         "<code>behavioral.registry</code> が採番した draft 参照番号で、"
         "確定していない入所者には存在しません。</p>\n"
         (table ["id" "resident-name" "法域" "入所者数" "職員数" "比率" "服薬懸念フラグ"
                 "療養計画確定" "危機対応確定" "plan-number" "response-number"]
                (map (fn [r]
                       (let [ratio (when (and (number? (:current-resident-count r))
                                              (number? (:current-staff-count r))
                                              (pos? (:current-staff-count r)))
                                     (/ (:current-resident-count r) (:current-staff-count r)))
                             over? (registry/supervision-ratio-insufficient? r)]
                         (row [(code (:id r))
                               (cell (:resident-name r))
                               (code (:jurisdiction r))
                               (str "<span class=\"num\">" (cell (:current-resident-count r)) "</span>")
                               (str "<span class=\"num\">" (cell (:current-staff-count r)) "</span>")
                               (str "<span class=\"num" (when over? " err") "\">"
                                    (cell ratio)
                                    (when over? (str " &gt; " registry/maximum-supervision-ratio))
                                    "</span>")
                               (if (:medication-adherence-flag? r)
                                 "<span class=\"err\">true</span>"
                                 "<span class=\"muted\">false</span>")
                               (if (:treatment-plan-finalized? r)
                                 "<span class=\"ok\">true</span>"
                                 "<span class=\"muted\">false</span>")
                               (if (:crisis-response-finalized? r)
                                 "<span class=\"ok\">true</span>"
                                 "<span class=\"muted\">false</span>")
                               (if (:plan-number r) (code (:plan-number r)) "&mdash;")
                               (if (:response-number r) (code (:response-number r)) "&mdash;")])))
                     rs)))))

(defn- section-registers [db]
  (let [rs (store/all-residents db)
        assessed (keep (fn [r] (when-let [a (store/assessment-of db (:id r))] [r a])) rs)
        screened (keep (fn [r] (when-let [m (store/medication-screen-of db (:id r))] [r m])) rs)]
    (str "<h2>法域 assessment レジスタ &mdash; " (count assessed) " 件</h2>\n"
         (if (seq assessed)
           (table ["resident" "法域" "spec-basis" "必要書類（チェックリスト）" "approver-shaped key"]
                  (map (fn [[r a]]
                         (row [(code (:id r))
                               (code (:jurisdiction a))
                               (if (:spec-basis a) (esc (:spec-basis a))
                                   "<span class=\"err\">なし</span>")
                               (if (seq (:checklist a))
                                 (str "<ul><li>"
                                      (str/join "</li><li>" (map esc (:checklist a)))
                                      "</li></ul>")
                                 "&mdash;")
                               (codes (approver-shaped-keys a))]))
                       assessed))
           "<p class=\"muted\">この実行では commit された assessment がありません。</p>")
         "\n<h2>服薬アドヒアランス screening レジスタ &mdash; " (count screened) " 件</h2>\n"
         (if (seq screened)
           (table ["resident" "verdict" "approver-shaped key"]
                  (map (fn [[r m]]
                         (row [(code (:id r))
                               (if (= :unresolved (:verdict m))
                                 (str "<span class=\"err\">" (cell (kw (:verdict m))) "</span>")
                                 (str "<span class=\"ok\">" (cell (kw (:verdict m))) "</span>"))
                               (codes (approver-shaped-keys m))]))
                       screened))
           "<p class=\"muted\">この実行では commit された screening がありません。</p>"))))

(defn- record-table [records]
  (table ["record_id" "kind" "resident_id" "法域" "immutable"]
         (map (fn [rec]
                (row [(code (get rec "record_id"))
                      (cell (get rec "kind"))
                      (code (get rec "resident_id"))
                      (code (get rec "jurisdiction"))
                      (cell (get rec "immutable"))]))
              records)))

(defn- section-drafts [db]
  (let [tp (store/treatment-plan-history db)
        cr (store/crisis-response-history db)]
    (str "<h2>療養計画確定 draft &mdash; " (count tp) " 件</h2>\n"
         "<p><code>behavioral.registry/register-treatment-plan-finalization</code> が"
         "法域スコープの連番から組み立てた記録です。"
         "この actor は実際の行動保健情報システムには一切書き込みません &mdash; "
         "施設が保持すべき<strong>記録</strong>を作るだけで、証明書は "
         "<code>status: draft-unsigned</code> のままです（署名は施設自身の行為）。</p>\n"
         (if (seq tp) (record-table tp)
             "<p class=\"muted\">確定された療養計画はありません。</p>")
         "\n<h2>危機対応確定 draft &mdash; " (count cr) " 件</h2>\n"
         (if (seq cr) (record-table cr)
             "<p class=\"muted\">確定された危機対応はありません。</p>"))))

(defn- section-attribution [rep]
  (let [{:keys [probes retained dropped ledger-commits ledger-with-approver ledger-actors]} rep]
    (str "<h2>承認者の追跡可能性（この実行で測定）</h2>\n"
         "<p>「誰が承認したか」が SSoT に残るかどうかは、このページを描画する時点で"
         "レジスタを実際に走査して決めています &mdash; ソースを読んで決め打ちしていません。"
         "走査条件は「キー名が <code>approv</code> を含む、または <code>by</code> である」。"
         "<code>:actor</code> は<strong>意図的に一致させていません</strong>: "
         "それは graph を実行した actor の id であって承認者ではないからです。</p>\n"
         "<p>この実行では実行 actor を <code>" (esc actor-id)
         "</code>、承認者を <code>" (esc (:approver rep))
         "</code> と<strong>別の文字列</strong>にしてあります。"
         "同じ文字列だと「<code>:actor</code> を承認者と読む」誤りが全行で正解と一致してしまい、"
         "測定が成立しません。</p>\n"
         (if (seq probes)
           (table ["thread" "op" "effect" "store に渡された record の approver key"
                   "commit 後のレジスタに残った approver key" "判定"]
                  (map (fn [p]
                         (let [kept? (some (comp seq :keys) (:registers p))]
                           (row [(code (:tid p))
                                 (code (kw (:op p)))
                                 (code (kw (:effect p)))
                                 (codes (:in-record p))
                                 (str/join "<br>"
                                           (map (fn [{:keys [label found? keys]}]
                                                  (str (esc label) ": "
                                                       (cond (not found?) "<span class=\"muted\">行なし</span>"
                                                             (seq keys) (codes keys)
                                                             :else "<span class=\"err\">なし</span>")))
                                                (:registers p)))
                                 (if kept?
                                   "<span class=\"ok\">保持</span>"
                                   "<span class=\"err\">欠落</span>")])))
                       probes))
           "<p class=\"muted\">この実行では人間が承認した commit がありません。</p>")
         "\n<div class=\"note\">\n"
         (if (seq dropped)
           (str "<p><strong>開示（この描画タスクでは修正していない欠陥）。</strong> "
                "人間が承認した commit " (count probes) " 件のうち <strong class=\"err num\">"
                (count dropped) "</strong> 件で、承認者が SSoT に残りませんでした。"
                "残ったのは <strong class=\"num\">" (count retained) "</strong> 件です。"
                "分かれ目は effect ごとの実装で、"
                "<code>behavioral.operation/commit-record</code> は承認者を "
                "<code>:payload</code> にだけ載せ <code>:value</code> には載せないため、"
                "<code>:payload</code> を保存する effect では残り、"
                "<code>:value</code> を使う effect や record を無視する effect では落ちます。</p>\n"
                "<p>さらに、この実行の append-only ledger にある commit fact "
                "<strong class=\"num\">" ledger-commits "</strong> 件のうち、"
                "承認者名を持つものは <strong class=\"err num\">" ledger-with-approver
                "</strong> 件でした。commit fact が持つ <code>actor</code> の値は "
                (codes ledger-actors) " のみで、承認者 <code>" (esc (:approver rep))
                "</code> とは別の文字列です &mdash; つまり "
                "<strong>ledger だけからは誰が承認したかを復元できません</strong>。"
                "<code>behavioral.store</code> の ns docstring はこの ledger について"
                "「approved by whom は不変ログへの query で常に分かる」と書いていますが、"
                "この実行の実測はそうなっていません。</p>\n"
                "<p>これは governor の穴ではなく監査可能性の穴なので、"
                "描画タスクの中で黙って直さず、ここに開示します。</p>\n")
           (str "<p>人間が承認した commit " (count probes) " 件すべてで、"
                "承認者が SSoT に残っていました。</p>\n"))
         "</div>")))

(defn- section-governor-rules []
  (str "<h2>governor の判定基準（コードから）</h2>\n"
       "<p>この表は <code>behavioral.governor</code> / <code>behavioral.registry</code> の"
       "実際の値です。</p>\n"
       (table ["設定" "値" "意味"]
              [(row [(code "governor/confidence-floor")
                     (str "<span class=\"num\">" governor/confidence-floor "</span>")
                     "advisor の confidence がこれ未満なら人に上げる（SOFT: 承認で通せる）"])
               (row [(code "governor/high-stakes")
                     (codes (sort (map kw governor/high-stakes)))
                     "この stake は clean でも必ず人に上げる（実在の入所者記録に対する行為）"])
               (row [(code "registry/maximum-supervision-ratio")
                     (str "<span class=\"num\">" registry/maximum-supervision-ratio "</span>")
                     "危機対応を確定する前に超えていてはならない入所者数/職員数の上限"])])))

(defn- section-phases []
  (str "<h2>rollout phase gate</h2>\n"
       "<p><code>behavioral.phase/phases</code> そのものです。"
       "<code>:treatment-plan/finalize</code> と <code>:crisis-response/finalize</code> は"
       "<strong>どの phase の <code>:auto</code> にも入っていません</strong> &mdash; "
       "到達していない rollout の段階ではなく、恒久的な構造です。</p>\n"
       (table ["phase" "label" "書き込みを許す op" "governor が clean なら自動 commit してよい op"]
              (map (fn [p]
                     (let [{:keys [label writes auto]} (get phase/phases p)]
                       (row [(str "<span class=\"num\">" p "</span>"
                                  (when (= p phase/default-phase)
                                    " <span class=\"badge\">default</span>"))
                             (cell label)
                             (codes (sort (map kw writes)))
                             (codes (sort (map kw auto)))])))
                   (sort (keys phase/phases))))))

(defn- section-coverage []
  (let [cov (facts/coverage)]
    (str "<h2>法域 spec-basis のカバレッジ（正直な報告）</h2>\n"
         "<p>" (esc (:note cov)) "</p>\n"
         (table ["ISO3" "国" "所管当局" "法的根拠" "出典"]
                (map (fn [iso3]
                       (let [sb (facts/spec-basis iso3)]
                         (row [(code iso3)
                               (cell (:name sb))
                               (cell (:owner-authority sb))
                               (cell (:legal-basis sb))
                               (str "<a href=\"" (esc (:provenance sb)) "\">"
                                    (esc (:provenance sb)) "</a>")])))
                     (:covered-jurisdictions cov)))
         "<p class=\"muted\">この表に無い法域には spec-basis が <strong>ありません</strong>。"
         "そのとき governor は要件を推測させず HARD hold します &mdash; "
         "上の t07（<code>ATL</code>）がその実行結果です。</p>")))

(defn- section-footer [tagged]
  (str "<footer>\n"
       "<p>このファイルは <code>clojure -M:dev:render-html</code> の生成物です。"
       "手で編集しないでください &mdash; actor を走らせ直せば再生成されます。</p>\n"
       "<p>再現性: 壁時計・UUID・実行ごとに変わる識別子はこのページに 1 つも入れていないので、"
       "同じ commit から何度生成してもバイト一致します。"
       "描画した ledger は <span class=\"num\">" (count tagged) "</span> fact、"
       "すべて <code>behavioral.store</code> の append-only ログから読んだものです。</p>\n"
       "<p>ライセンス: AGPL-3.0-or-later / cloud-itonami-isic-8720</p>\n"
       "</footer>"))

;; ----------------------------- page -----------------------------

(defn- blueprint []
  (let [f (io/file "blueprint.edn")]
    (if (.exists f)
      (edn/read-string (slurp f))
      (throw (ex-info "blueprint.edn not found -- run from the repo root" {})))))

(defn render
  "Builds the whole page from ONE run. Returns the HTML string."
  []
  (let [{:keys [db runs]} (run-demo!)
        ledger   (store/ledger db)
        tagged   (mapv (fn [i f] (assoc f ::index i))
                       (range) (classify-ledger! ledger))
        refusals (hard-hold-invariant! tagged)
        rep      (attribution-report db runs)
        bp       (blueprint)]
    {:html
     (str "<!DOCTYPE html>\n<html lang=\"ja\">\n<head>\n"
          "<meta charset=\"UTF-8\">\n"
          "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
          "<title>cloud-itonami-isic-8720 &mdash; Behavioral Care Governor operator console</title>\n"
          "<style>\n" (skin/dds+skin) "\n</style>\n"
          "</head>\n<body>\n"
          (section-header bp refusals tagged) "\n"
          (section-refusals refusals) "\n"
          (section-classes tagged) "\n"
          (section-runs runs) "\n"
          (section-ledger tagged) "\n"
          (section-residents db) "\n"
          (section-registers db) "\n"
          (section-drafts db) "\n"
          (section-attribution rep) "\n"
          (section-governor-rules) "\n"
          (section-phases) "\n"
          (section-coverage) "\n"
          (section-footer tagged) "\n"
          "</body>\n</html>\n")
     :runs runs
     :tagged tagged
     :refusals refusals
     :report rep}))

(defn -main
  "Renders the console. Writes NOTHING if the run produced no HARD
  governor hold -- `render` throws first."
  [& args]
  (let [out (or (first args) default-out)
        {:keys [html runs tagged refusals report]} (render)]
    (io/make-parents (io/file out))
    (spit out html)
    (println (str "wrote " out " (" (count html) " chars)"))
    (println (str "  scenarios      " (count runs)))
    (println (str "  ledger facts   " (count tagged)))
    (println (str "  classes        "
                  (str/join ", " (map (fn [[k v]] (str (name k) "=" v))
                                      (sort-by key (frequencies (map ::class tagged)))))))
    (println (str "  HARD holds     " (count refusals) " -> "
                  (str/join ", " (map #(name (:rule %)) (mapcat :violations refusals)))))
    (println (str "  approver kept  " (count (:retained report)) "/" (count (:probes report))
                  " approved commits"))))
