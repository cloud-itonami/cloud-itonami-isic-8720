(ns behavioral.registry
  "Pure-function treatment-plan-finalization + crisis-response-
  finalization record construction -- an append-only behavioral-care
  book-of-record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a treatment-plan-finalization
  or crisis-response-finalization reference number -- every facility/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `behavioral.facts` uses.

  `supervision-ratio-insufficient?` is the SECOND ratio-based
  sufficiency check in this fleet's check-family taxonomy
  (`leasing.registry/collateral-coverage-ratio-insufficient?`
  established the first, for a MINIMUM required ratio -- collateral
  value over financed amount must reach a floor). This check applies
  the SAME ratio-comparison shape to the opposite (MAXIMUM) direction:
  a facility's own current resident count divided by its own current
  staff count must NOT exceed a maximum permitted supervision ratio
  before a crisis response can be finalized -- proving the ratio
  family's generality across BOTH the MINIMUM-floor and MAXIMUM-
  ceiling directions, the same way the two-field direct-comparison
  families (MINIMUM-threshold, MAXIMUM-ceiling) were each shown to
  generalize across temporal and non-temporal ground truths.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real behavioral-health-information system. It builds the
  RECORD a facility would keep, not the act of finalizing the
  treatment plan or crisis response itself (that is `behavioral.
  operation`'s `:treatment-plan/finalize`/`:crisis-response/finalize`,
  always human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  facility's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(def maximum-supervision-ratio
  "A single representative maximum permitted resident-to-staff
  supervision ratio before a crisis response may be finalized. A
  single representative figure, not a facility-type-by-facility-type/
  jurisdiction-by-jurisdiction survey of every staffing-standard
  variant -- see ns docstring for the honest simplification this
  makes."
  4)

(defn supervision-ratio-insufficient?
  "Does `facility`'s own `:current-resident-count` divided by its own
  `:current-staff-count` exceed `maximum-supervision-ratio`? A pure
  ground-truth check against the facility's own permanent fields -- no
  upstream comparison needed. The SECOND ratio-based instance in this
  fleet's sufficiency-check taxonomy (see ns docstring), applying the
  MAXIMUM direction rather than `leasing.registry`'s MINIMUM one."
  [{:keys [current-resident-count current-staff-count]}]
  (and (number? current-resident-count) (number? current-staff-count) (pos? current-staff-count)
       (> (/ current-resident-count current-staff-count) maximum-supervision-ratio)))

(defn register-treatment-plan-finalization
  "Validate + construct the TREATMENT-PLAN-FINALIZATION registration
  DRAFT -- the facility's own legal act of finalizing a real
  resident's treatment plan. Pure function -- does not touch any real
  behavioral-health-information system; it builds the RECORD a
  facility would keep. `behavioral.governor` independently re-verifies
  the resident's own medication-adherence status, and blocks a
  double-finalization of the same resident's treatment plan, before
  this is ever allowed to commit."
  [resident-id jurisdiction sequence]
  (when-not (and resident-id (not= resident-id ""))
    (throw (ex-info "treatment-plan-finalization: resident_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "treatment-plan-finalization: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "treatment-plan-finalization: sequence must be >= 0" {})))
  (let [plan-number (str (str/upper-case jurisdiction) "-TPL-" (zero-pad sequence 6))
        record {"record_id" plan-number
                "kind" "treatment-plan-finalization-draft"
                "resident_id" resident-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "plan_number" plan-number
     "certificate" (unsigned-certificate "TreatmentPlanFinalization" plan-number plan-number)}))

(defn register-crisis-response-finalization
  "Validate + construct the CRISIS-RESPONSE-FINALIZATION registration
  DRAFT -- the facility's own legal act of finalizing a real crisis
  response for a resident. Pure function -- does not touch any real
  behavioral-health-information system; it builds the RECORD a
  facility would keep. `behavioral.governor` independently re-verifies
  the facility's own supervision-ratio sufficiency, and blocks a
  double-finalization of the same resident's crisis response, before
  this is ever allowed to commit."
  [resident-id jurisdiction sequence]
  (when-not (and resident-id (not= resident-id ""))
    (throw (ex-info "crisis-response-finalization: resident_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "crisis-response-finalization: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "crisis-response-finalization: sequence must be >= 0" {})))
  (let [response-number (str (str/upper-case jurisdiction) "-CRS-" (zero-pad sequence 6))
        record {"record_id" response-number
                "kind" "crisis-response-finalization-draft"
                "resident_id" resident-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "response_number" response-number
     "certificate" (unsigned-certificate "CrisisResponseFinalization" response-number response-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
