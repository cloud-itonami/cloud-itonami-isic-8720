(ns behavioral.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean resident through
  intake -> jurisdiction assessment -> medication-adherence screening
  -> treatment-plan-finalization proposal (always escalates) -> human
  approval -> commit, then through crisis-response-finalization
  proposal (always escalates) -> human approval -> commit, then shows
  five HARD holds (a jurisdiction with no spec-basis, an insufficient
  supervision ratio, an unresolved medication-adherence flag screened
  directly via `:medication-adherence/screen` [never via an actuation
  op against an unscreened resident -- see this actor's own governor
  ns docstring / the lesson `parksafety`'s ADR-2607071922 Decision 5,
  `eldercare`'s, `museum`'s, `conservation`'s, `salon`'s,
  `entertainment`'s, `casework`'s, `hospital`'s, `facility`'s,
  `school`'s, `association`'s and `leasing`'s ADR-0001s already
  recorded], and a double treatment-plan/crisis-response finalization
  of an already-processed resident) that never reach a human at all,
  and prints the audit ledger + the draft treatment-plan-finalization
  and crisis-response-finalization records."
  (:require [langgraph.graph :as g]
            [behavioral.store :as store]
            [behavioral.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :behavioral-health-professional :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== resident/intake resident-1 (JPN, clean; 8/2 resident-staff ratio, no medication flag) ==")
    (println (exec! actor "t1" {:op :resident/intake :subject "resident-1"
                                :patch {:id "resident-1" :resident-name "Sakura Tanaka"}} operator))

    (println "== jurisdiction/assess resident-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :jurisdiction/assess :subject "resident-1"} operator))
    (println (approve! actor "t2"))

    (println "== medication-adherence/screen resident-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :medication-adherence/screen :subject "resident-1"} operator))
    (println (approve! actor "t3"))

    (println "== treatment-plan/finalize resident-1 (always escalates -- actuation/finalize-treatment-plan) ==")
    (let [r (exec! actor "t4" {:op :treatment-plan/finalize :subject "resident-1"} operator)]
      (println r)
      (println "-- human professional approves --")
      (println (approve! actor "t4")))

    (println "== crisis-response/finalize resident-1 (always escalates -- actuation/finalize-crisis-response) ==")
    (let [r (exec! actor "t5" {:op :crisis-response/finalize :subject "resident-1"} operator)]
      (println r)
      (println "-- human professional approves --")
      (println (approve! actor "t5")))

    (println "== jurisdiction/assess resident-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :jurisdiction/assess :subject "resident-2" :no-spec? true} operator))

    (println "== jurisdiction/assess resident-3 (escalates -- human approves; sets up the supervision-ratio test) ==")
    (println (exec! actor "t7" {:op :jurisdiction/assess :subject "resident-3"} operator))
    (println (approve! actor "t7"))

    (println "== crisis-response/finalize resident-3 (20/2 resident-staff ratio -> HARD hold) ==")
    (println (exec! actor "t8" {:op :crisis-response/finalize :subject "resident-3"} operator))

    (println "== medication-adherence/screen resident-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :medication-adherence/screen :subject "resident-4"} operator))

    (println "== treatment-plan/finalize resident-1 AGAIN (double-finalization -> HARD hold) ==")
    (println (exec! actor "t10" {:op :treatment-plan/finalize :subject "resident-1"} operator))

    (println "== crisis-response/finalize resident-1 AGAIN (double-finalization -> HARD hold) ==")
    (println (exec! actor "t11" {:op :crisis-response/finalize :subject "resident-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft treatment-plan-finalization records ==")
    (doseq [r (store/treatment-plan-history db)] (println r))

    (println "== draft crisis-response-finalization records ==")
    (doseq [r (store/crisis-response-history db)] (println r))))
