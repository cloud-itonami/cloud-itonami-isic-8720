(ns behavioral.store
  "SSoT for the behavioral-care actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/behavioral/store_contract_test.clj), which is the whole point:
  the actor, the Behavioral Care Governor and the audit ledger never
  know which SSoT they run on.

  Like `hospital.store`'s dual treatment/discharge history,
  `school.store`'s dual promotion/safeguarding-record history and
  every other dual-actuation sibling before it, this actor has TWO
  actuation events (finalizing a treatment plan, finalizing a crisis
  response) acting on the SAME entity (a resident), each with its OWN
  history collection, sequence counter and dedicated double-
  actuation-guard boolean (`:treatment-plan-finalized?`/`:crisis-
  response-finalized?`, never a `:status` value) -- the same
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320).

  The ledger stays append-only on every backend: 'which resident was
  screened for an unresolved medication-adherence flag, which
  treatment plan was finalized, which crisis response was finalized,
  on what jurisdictional basis, approved by whom' is always a query
  over an immutable log -- the audit trail a resident's family
  trusting a facility needs, and the evidence an operator needs if a
  treatment plan or crisis response is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [behavioral.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (resident [s id])
  (all-residents [s])
  (medication-screen-of [s resident-id] "committed medication-adherence screening verdict for a resident, or nil")
  (assessment-of [s resident-id] "committed jurisdiction assessment, or nil")
  (ledger [s])
  (treatment-plan-history [s] "the append-only treatment-plan-finalization history (behavioral.registry drafts)")
  (crisis-response-history [s] "the append-only crisis-response-finalization history (behavioral.registry drafts)")
  (next-treatment-plan-sequence [s jurisdiction] "next treatment-plan-number sequence for a jurisdiction")
  (next-crisis-response-sequence [s jurisdiction] "next crisis-response-number sequence for a jurisdiction")
  (resident-already-treatment-planned? [s resident-id] "has this resident's treatment plan already been finalized?")
  (resident-already-crisis-responded? [s resident-id] "has this resident's crisis response already been finalized?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-residents [s residents] "replace/seed the resident directory (map id->resident)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained resident set covering both actuation
  lifecycles (finalizing a treatment plan, finalizing a crisis
  response) so the actor + tests run offline."
  []
  {:residents
   {"resident-1" {:id "resident-1" :resident-name "Sakura Tanaka"
                  :current-resident-count 8 :current-staff-count 2 :medication-adherence-flag? false
                  :treatment-plan-finalized? false :crisis-response-finalized? false
                  :jurisdiction "JPN" :status :intake}
    "resident-2" {:id "resident-2" :resident-name "Atlantis Doe"
                  :current-resident-count 8 :current-staff-count 2 :medication-adherence-flag? false
                  :treatment-plan-finalized? false :crisis-response-finalized? false
                  :jurisdiction "ATL" :status :intake}
    "resident-3" {:id "resident-3" :resident-name "鈴木一郎"
                  :current-resident-count 20 :current-staff-count 2 :medication-adherence-flag? false
                  :treatment-plan-finalized? false :crisis-response-finalized? false
                  :jurisdiction "JPN" :status :intake}
    "resident-4" {:id "resident-4" :resident-name "田中花子"
                  :current-resident-count 8 :current-staff-count 2 :medication-adherence-flag? true
                  :treatment-plan-finalized? false :crisis-response-finalized? false
                  :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- finalize-treatment-plan!
  "Backend-agnostic `:resident/mark-treatment-planned` -- looks up the
  resident via the protocol and drafts the treatment-plan-finalization
  record, and returns {:result .. :resident-patch ..} for the caller
  to persist."
  [s resident-id]
  (let [r (resident s resident-id)
        seq-n (next-treatment-plan-sequence s (:jurisdiction r))
        result (registry/register-treatment-plan-finalization resident-id (:jurisdiction r) seq-n)]
    {:result result
     :resident-patch {:treatment-plan-finalized? true
                      :plan-number (get result "plan_number")}}))

(defn- finalize-crisis-response!
  "Backend-agnostic `:resident/mark-crisis-responded` -- looks up the
  resident via the protocol and drafts the crisis-response-
  finalization record, and returns {:result .. :resident-patch ..} for
  the caller to persist."
  [s resident-id]
  (let [r (resident s resident-id)
        seq-n (next-crisis-response-sequence s (:jurisdiction r))
        result (registry/register-crisis-response-finalization resident-id (:jurisdiction r) seq-n)]
    {:result result
     :resident-patch {:crisis-response-finalized? true
                      :response-number (get result "response_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (resident [_ id] (get-in @a [:residents id]))
  (all-residents [_] (sort-by :id (vals (:residents @a))))
  (medication-screen-of [_ id] (get-in @a [:medication-screens id]))
  (assessment-of [_ resident-id] (get-in @a [:assessments resident-id]))
  (ledger [_] (:ledger @a))
  (treatment-plan-history [_] (:treatment-plans @a))
  (crisis-response-history [_] (:crisis-responses @a))
  (next-treatment-plan-sequence [_ jurisdiction] (get-in @a [:treatment-plan-sequences jurisdiction] 0))
  (next-crisis-response-sequence [_ jurisdiction] (get-in @a [:crisis-response-sequences jurisdiction] 0))
  (resident-already-treatment-planned? [_ resident-id] (boolean (get-in @a [:residents resident-id :treatment-plan-finalized?])))
  (resident-already-crisis-responded? [_ resident-id] (boolean (get-in @a [:residents resident-id :crisis-response-finalized?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :resident/upsert
      (swap! a update-in [:residents (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :medication-screen/set
      (swap! a assoc-in [:medication-screens (first path)] payload)

      :resident/mark-treatment-planned
      (let [resident-id (first path)
            {:keys [result resident-patch]} (finalize-treatment-plan! s resident-id)
            jurisdiction (:jurisdiction (resident s resident-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:treatment-plan-sequences jurisdiction] (fnil inc 0))
                       (update-in [:residents resident-id] merge resident-patch)
                       (update :treatment-plans registry/append result))))
        result)

      :resident/mark-crisis-responded
      (let [resident-id (first path)
            {:keys [result resident-patch]} (finalize-crisis-response! s resident-id)
            jurisdiction (:jurisdiction (resident s resident-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:crisis-response-sequences jurisdiction] (fnil inc 0))
                       (update-in [:residents resident-id] merge resident-patch)
                       (update :crisis-responses registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-residents [s residents] (when (seq residents) (swap! a assoc :residents residents)) s))

(defn seed-db
  "A MemStore seeded with the demo resident set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :medication-screens {} :ledger [] :treatment-plan-sequences {}
                           :treatment-plans [] :crisis-response-sequences {} :crisis-responses []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment/medication-screen payloads, ledger
  facts, treatment-plan/crisis-response records) are stored as EDN
  strings so `langchain.db` doesn't expand them into sub-entities --
  the same convention every sibling actor's store uses."
  {:resident/id                            {:db/unique :db.unique/identity}
   :assessment/resident-id                 {:db/unique :db.unique/identity}
   :medication-screen/resident-id          {:db/unique :db.unique/identity}
   :ledger/seq                             {:db/unique :db.unique/identity}
   :treatment-plan/seq                     {:db/unique :db.unique/identity}
   :crisis-response/seq                    {:db/unique :db.unique/identity}
   :treatment-plan-sequence/jurisdiction   {:db/unique :db.unique/identity}
   :crisis-response-sequence/jurisdiction  {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- resident->tx [{:keys [id resident-name current-resident-count current-staff-count medication-adherence-flag?
                             treatment-plan-finalized? crisis-response-finalized?
                             jurisdiction status plan-number response-number]}]
  (cond-> {:resident/id id}
    resident-name                          (assoc :resident/resident-name resident-name)
    current-resident-count                  (assoc :resident/current-resident-count current-resident-count)
    current-staff-count                     (assoc :resident/current-staff-count current-staff-count)
    (some? medication-adherence-flag?)      (assoc :resident/medication-adherence-flag? medication-adherence-flag?)
    (some? treatment-plan-finalized?)       (assoc :resident/treatment-plan-finalized? treatment-plan-finalized?)
    (some? crisis-response-finalized?)       (assoc :resident/crisis-response-finalized? crisis-response-finalized?)
    jurisdiction                           (assoc :resident/jurisdiction jurisdiction)
    status                                 (assoc :resident/status status)
    plan-number                            (assoc :resident/plan-number plan-number)
    response-number                        (assoc :resident/response-number response-number)))

(def ^:private resident-pull
  [:resident/id :resident/resident-name :resident/current-resident-count :resident/current-staff-count
   :resident/medication-adherence-flag? :resident/treatment-plan-finalized? :resident/crisis-response-finalized?
   :resident/jurisdiction :resident/status :resident/plan-number :resident/response-number])

(defn- pull->resident [m]
  (when (:resident/id m)
    {:id (:resident/id m) :resident-name (:resident/resident-name m)
     :current-resident-count (:resident/current-resident-count m)
     :current-staff-count (:resident/current-staff-count m)
     :medication-adherence-flag? (boolean (:resident/medication-adherence-flag? m))
     :treatment-plan-finalized? (boolean (:resident/treatment-plan-finalized? m))
     :crisis-response-finalized? (boolean (:resident/crisis-response-finalized? m))
     :jurisdiction (:resident/jurisdiction m) :status (:resident/status m)
     :plan-number (:resident/plan-number m) :response-number (:resident/response-number m)}))

(defrecord DatomicStore [conn]
  Store
  (resident [_ id]
    (pull->resident (d/pull (d/db conn) resident-pull [:resident/id id])))
  (all-residents [_]
    (->> (d/q '[:find [?id ...] :where [?e :resident/id ?id]] (d/db conn))
         (map #(pull->resident (d/pull (d/db conn) resident-pull [:resident/id %])))
         (sort-by :id)))
  (medication-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?rid
                :where [?k :medication-screen/resident-id ?rid] [?k :medication-screen/payload ?p]]
              (d/db conn) id)))
  (assessment-of [_ resident-id]
    (dec* (d/q '[:find ?p . :in $ ?rid
                :where [?a :assessment/resident-id ?rid] [?a :assessment/payload ?p]]
              (d/db conn) resident-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (treatment-plan-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :treatment-plan/seq ?s] [?e :treatment-plan/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (crisis-response-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :crisis-response/seq ?s] [?e :crisis-response/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-treatment-plan-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :treatment-plan-sequence/jurisdiction ?j] [?e :treatment-plan-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-crisis-response-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :crisis-response-sequence/jurisdiction ?j] [?e :crisis-response-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (resident-already-treatment-planned? [s resident-id]
    (boolean (:treatment-plan-finalized? (resident s resident-id))))
  (resident-already-crisis-responded? [s resident-id]
    (boolean (:crisis-response-finalized? (resident s resident-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :resident/upsert
      (d/transact! conn [(resident->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/resident-id (first path) :assessment/payload (enc payload)}])

      :medication-screen/set
      (d/transact! conn [{:medication-screen/resident-id (first path) :medication-screen/payload (enc payload)}])

      :resident/mark-treatment-planned
      (let [resident-id (first path)
            {:keys [result resident-patch]} (finalize-treatment-plan! s resident-id)
            jurisdiction (:jurisdiction (resident s resident-id))
            next-n (inc (next-treatment-plan-sequence s jurisdiction))]
        (d/transact! conn
                     [(resident->tx (assoc resident-patch :id resident-id))
                      {:treatment-plan-sequence/jurisdiction jurisdiction :treatment-plan-sequence/next next-n}
                      {:treatment-plan/seq (count (treatment-plan-history s)) :treatment-plan/record (enc (get result "record"))}])
        result)

      :resident/mark-crisis-responded
      (let [resident-id (first path)
            {:keys [result resident-patch]} (finalize-crisis-response! s resident-id)
            jurisdiction (:jurisdiction (resident s resident-id))
            next-n (inc (next-crisis-response-sequence s jurisdiction))]
        (d/transact! conn
                     [(resident->tx (assoc resident-patch :id resident-id))
                      {:crisis-response-sequence/jurisdiction jurisdiction :crisis-response-sequence/next next-n}
                      {:crisis-response/seq (count (crisis-response-history s)) :crisis-response/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-residents [s residents]
    (when (seq residents) (d/transact! conn (mapv resident->tx (vals residents)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:residents ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [residents]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-residents s residents))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo resident set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
