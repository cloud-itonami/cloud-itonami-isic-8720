(ns behavioral.governor
  "Behavioral Care Governor -- the independent compliance layer that
  earns the BehavioralOps-LLM the right to commit. The LLM has no
  notion of jurisdictional behavioral-health-facility licensing law,
  whether a facility's own current resident-to-staff ratio actually
  stays within its own required maximum, whether a resident's
  medication-adherence concern has actually stayed unresolved, or when
  an act stops being a draft and becomes a real-world treatment-plan
  finalization or crisis-response finalization, so this MUST be a
  separate system able to *reject* a proposal and fall back to HOLD --
  the behavioral-care analog of `cloud-itonami-isic-6512`'s
  CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated jurisdiction spec-basis, incomplete evidence, an
  insufficient supervision ratio, an unresolved medication-adherence
  flag, or a double treatment-plan/crisis-response finalization). The
  confidence/actuation gate is SOFT: it asks a human to look (low
  confidence / actuation), and the human may approve -- but see
  `behavioral.phase`: for `:stake :actuation/finalize-treatment-plan`/
  `:actuation/finalize-crisis-response` (a real resident-record act)
  NO phase ever allows auto-commit either. Two independent layers
  agree that actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source (`behavioral.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:treatment-plan/finalize`/
                                       `:crisis-response/finalize`, has
                                       the jurisdiction actually been
                                       assessed with a full resident-
                                       intake/treatment-plan-disclosure/
                                       risk-assessment/supervision-
                                       staffing evidence checklist on
                                       file?
    3. Supervision ratio
       insufficient                  -- for `:crisis-response/
                                       finalize`, INDEPENDENTLY
                                       recompute whether the facility's
                                       own current resident-to-staff
                                       ratio exceeds `behavioral.
                                       registry/maximum-supervision-
                                       ratio` (`behavioral.registry/
                                       supervision-ratio-insufficient?`)
                                       -- needs no proposal inspection
                                       or stored-verdict lookup at all.
                                       The SECOND RATIO-based instance
                                       in this fleet's sufficiency-
                                       check taxonomy (`leasing.
                                       governor/collateral-coverage-
                                       insufficient-violations`
                                       established the first, for a
                                       MINIMUM required ratio), applied
                                       here in the MAXIMUM direction.
    4. Medication-adherence flag
       unresolved                     -- reported by THIS proposal
                                       itself (a `:medication-
                                       adherence/screen` that just
                                       found an unresolved concern), or
                                       already on file for the resident
                                       (`:medication-adherence/screen`/
                                       `:treatment-plan/finalize`).
                                       Evaluated UNCONDITIONALLY (not
                                       scoped to a specific op), the
                                       SAME discipline `casualty.
                                       governor/sanctions-violations`/
                                       ...(twenty-one prior
                                       siblings)... established -- the
                                       TWENTY-SECOND distinct
                                       application of this exact
                                       discipline, and the FIRST
                                       specifically for a medication-
                                       adherence-flag concept. Like the
                                       eleven most recent siblings'
                                       equivalent checks, this is
                                       exercised in tests/demo via
                                       `:medication-adherence/screen`
                                       DIRECTLY, not via an actuation
                                       op against an unscreened
                                       resident -- see this ns's own
                                       test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:treatment-plan/
                                       finalize`/`:crisis-response/
                                       finalize` (REAL resident-record
                                       acts) -> escalate.

  Two more guards, double-treatment-plan/double-crisis-response
  prevention, are enforced but NOT listed as numbered HARD checks
  above because they need no upstream comparison at all --
  `already-treatment-planned-violations`/`already-crisis-responded-
  violations` refuse to finalize a treatment plan/crisis response for
  the SAME resident twice, off dedicated `:treatment-plan-finalized?`/
  `:crisis-response-finalized?` facts (never a `:status` value) -- the
  SAME 'check a dedicated boolean, not status' discipline every prior
  sibling governor's guards establish, informed by `cloud-itonami-
  isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [behavioral.facts :as facts]
            [behavioral.registry :as registry]
            [behavioral.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Finalizing a real treatment plan and finalizing a real crisis
  response are the two real-world actuation events this actor
  performs -- a two-member set, matching every prior dual-actuation
  sibling's shape."
  #{:actuation/finalize-treatment-plan :actuation/finalize-crisis-response})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:treatment-plan/finalize`/`:crisis-
  response/finalize`) proposal with no spec-basis citation is a HARD
  violation -- never invent a jurisdiction's behavioral-health-
  facility licensing requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :treatment-plan/finalize :crisis-response/finalize} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:treatment-plan/finalize`/`:crisis-response/finalize`, the
  jurisdiction's required resident-intake/treatment-plan-disclosure/
  risk-assessment/supervision-staffing evidence must actually be
  satisfied -- do not trust the advisor's self-reported confidence
  alone."
  [{:keys [op subject]} st]
  (when (contains? #{:treatment-plan/finalize :crisis-response/finalize} op)
    (let [r (store/resident st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction r) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(入所記録/療養計画開示書/リスクアセスメント報告書/人員配置証明書等)が充足していない状態での提案"}]))))

(defn- supervision-ratio-insufficient-violations
  "For `:crisis-response/finalize`, INDEPENDENTLY recompute whether
  the facility's own current resident-to-staff ratio exceeds
  `behavioral.registry/maximum-supervision-ratio` via `behavioral.
  registry/supervision-ratio-insufficient?` -- needs no proposal
  inspection or stored-verdict lookup at all, since its input is a
  permanent ground-truth field already on the resident record."
  [{:keys [op subject]} st]
  (when (= op :crisis-response/finalize)
    (let [r (store/resident st subject)]
      (when (registry/supervision-ratio-insufficient? r)
        [{:rule :supervision-ratio-insufficient
          :detail (str subject " の入所者数(" (:current-resident-count r)
                      ")対職員数(" (:current-staff-count r) ")比率が上限を超過")}]))))

(defn- medication-adherence-flag-unresolved-violations
  "An unresolved medication-adherence flag -- reported by THIS
  proposal (e.g. a `:medication-adherence/screen` that itself just
  found one), or already on file in the store for the resident
  (`:medication-adherence/screen`/`:treatment-plan/finalize`) -- is a
  HARD, un-overridable hold. Evaluated UNCONDITIONALLY (not scoped to
  a specific op) so the screening op itself can HARD-hold on its own
  finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        resident-id (when (contains? #{:medication-adherence/screen :treatment-plan/finalize} op) subject)
        hit-on-file? (and resident-id (= :unresolved (:verdict (store/medication-screen-of st resident-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :medication-adherence-flag-unresolved
        :detail "未解決の服薬アドヒアランス懸念がある状態での療養計画確定提案は進められない"}])))

(defn- already-treatment-planned-violations
  "For `:treatment-plan/finalize`, refuses to finalize a treatment
  plan for the SAME resident twice, off a dedicated `:treatment-plan-
  finalized?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :treatment-plan/finalize)
    (when (store/resident-already-treatment-planned? st subject)
      [{:rule :already-treatment-planned
        :detail (str subject " は既に療養計画確定済み")}])))

(defn- already-crisis-responded-violations
  "For `:crisis-response/finalize`, refuses to finalize a crisis
  response for the SAME resident twice, off a dedicated `:crisis-
  response-finalized?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :crisis-response/finalize)
    (when (store/resident-already-crisis-responded? st subject)
      [{:rule :already-crisis-responded
        :detail (str subject " は既に危機対応確定済み")}])))

(defn check
  "Censors a BehavioralOps-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (supervision-ratio-insufficient-violations request st)
                           (medication-adherence-flag-unresolved-violations request proposal st)
                           (already-treatment-planned-violations request st)
                           (already-crisis-responded-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
