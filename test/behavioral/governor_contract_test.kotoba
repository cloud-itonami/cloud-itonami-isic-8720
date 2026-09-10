(ns behavioral.governor-contract-test
  "The governor contract as executable tests -- the behavioral-care
  analog of `cloud-itonami-isic-6512`'s `casualty.governor-contract-
  test`. The single invariant under test:

    BehavioralOps-LLM never finalizes a treatment plan or crisis
    response the Behavioral Care Governor would reject, `:treatment-
    plan/finalize`/`:crisis-response/finalize` NEVER auto-commit at
    any phase, `:resident/intake` (no direct capital risk) MAY auto-
    commit when clean, and every decision (commit OR hold) leaves
    exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [behavioral.store :as store]
            [behavioral.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :behavioral-health-professional :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  "Walks `subject` through assess -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :jurisdiction/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(defn- screen!
  "Walks `subject` through medication-adherence screening -> approve,
  leaving a screening on file. Only safe to call for a resident whose
  medication-adherence status has already resolved -- an unresolved
  flag HARD-holds the screen itself (see
  `medication-adherence-flag-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :medication-adherence/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :resident/intake :subject "resident-1"
                   :patch {:id "resident-1" :resident-name "Sakura Tanaka"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Tanaka" (:resident-name (store/resident db "resident-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "resident-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "resident-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "resident-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "resident-1")) "no assessment written"))))

(deftest treatment-plan-finalize-without-assessment-is-held
  (testing "treatment-plan/finalize before any jurisdiction assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :treatment-plan/finalize :subject "resident-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest supervision-ratio-insufficient-is-held
  (testing "a facility whose resident-to-staff ratio exceeds its own maximum -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t5pre" "resident-3")
          res (exec-op actor "t5" {:op :crisis-response/finalize :subject "resident-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:supervision-ratio-insufficient} (-> (store/ledger db) last :basis)))
      (is (empty? (store/crisis-response-history db))))))

(deftest medication-adherence-flag-is-held-and-unoverridable
  (testing "an unresolved medication-adherence flag on a resident -> HOLD, and never reaches request-approval -- exercised via :medication-adherence/screen DIRECTLY, not via the actuation op against an unscreened resident (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's and leasing's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :medication-adherence/screen :subject "resident-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:medication-adherence-flag-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/medication-screen-of db "resident-4")) "no clearance written"))))

(deftest treatment-plan-finalize-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, resolved-medication resident still ALWAYS interrupts for human approval -- actuation/finalize-treatment-plan is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t7pre" "resident-1")
          r1 (exec-op actor "t7" {:op :treatment-plan/finalize :subject "resident-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, treatment-plan record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:treatment-plan-finalized? (store/resident db "resident-1"))))
          (is (= 1 (count (store/treatment-plan-history db))) "one draft treatment-plan record"))))))

(deftest crisis-response-finalize-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, within-ratio resident still ALWAYS interrupts for human approval -- actuation/finalize-crisis-response is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "resident-1")
          _ (screen! actor "t8pre2" "resident-1")
          r1 (exec-op actor "t8" {:op :crisis-response/finalize :subject "resident-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, crisis-response record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:crisis-response-finalized? (store/resident db "resident-1"))))
          (is (= 1 (count (store/crisis-response-history db))) "one draft crisis-response record"))))))

(deftest treatment-plan-finalize-double-finalization-is-held
  (testing "finalizing the same resident's treatment plan twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t9pre" "resident-1")
          _ (exec-op actor "t9a" {:op :treatment-plan/finalize :subject "resident-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :treatment-plan/finalize :subject "resident-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-treatment-planned} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/treatment-plan-history db))) "still only the one earlier plan"))))

(deftest crisis-response-finalize-double-finalization-is-held
  (testing "finalizing the same resident's crisis response twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t10pre" "resident-1")
          _ (screen! actor "t10pre2" "resident-1")
          _ (exec-op actor "t10a" {:op :crisis-response/finalize :subject "resident-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :crisis-response/finalize :subject "resident-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-crisis-responded} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/crisis-response-history db))) "still only the one earlier response"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :resident/intake :subject "resident-1"
                          :patch {:id "resident-1" :resident-name "Sakura Tanaka"}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "resident-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
