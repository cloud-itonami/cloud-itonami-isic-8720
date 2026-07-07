(ns behavioral.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:treatment-plan/finalize`/`:crisis-response/finalize`
  must NEVER be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [behavioral.phase :as phase]))

(deftest treatment-plan-finalize-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real treatment-plan finalization"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :treatment-plan/finalize))
          (str "phase " n " must not auto-commit :treatment-plan/finalize")))))

(deftest crisis-response-finalize-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real crisis-response finalization"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :crisis-response/finalize))
          (str "phase " n " must not auto-commit :crisis-response/finalize")))))

(deftest medication-adherence-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :medication-adherence/screen))
          (str "phase " n " must not auto-commit :medication-adherence/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":resident/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:resident/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :resident/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :treatment-plan/finalize} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :crisis-response/finalize} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :resident/intake} :commit)))))
