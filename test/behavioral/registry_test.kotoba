(ns behavioral.registry-test
  (:require [clojure.test :refer [deftest is]]
            [behavioral.registry :as r]))

;; ----------------------------- supervision-ratio-insufficient? -----------------------------

(deftest not-insufficient-when-at-or-below-maximum-ratio
  (is (not (r/supervision-ratio-insufficient? {:current-resident-count 8 :current-staff-count 2})))
  (is (not (r/supervision-ratio-insufficient? {:current-resident-count 4 :current-staff-count 2}))))

(deftest insufficient-when-above-maximum-ratio
  (is (r/supervision-ratio-insufficient? {:current-resident-count 9 :current-staff-count 2}))
  (is (r/supervision-ratio-insufficient? {:current-resident-count 20 :current-staff-count 2})))

(deftest insufficient-is-false-on-missing-or-zero-fields
  (is (not (r/supervision-ratio-insufficient? {})))
  (is (not (r/supervision-ratio-insufficient? {:current-resident-count 20})))
  (is (not (r/supervision-ratio-insufficient? {:current-resident-count 0 :current-staff-count 0}))
      "zero staff-count -> no division by zero, never flagged"))

;; ----------------------------- register-treatment-plan-finalization -----------------------------

(deftest treatment-plan-is-a-draft-not-a-real-plan
  (let [result (r/register-treatment-plan-finalization "resident-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest treatment-plan-assigns-plan-number
  (let [result (r/register-treatment-plan-finalization "resident-1" "JPN" 7)]
    (is (= (get result "plan_number") "JPN-TPL-000007"))
    (is (= (get-in result ["record" "resident_id"]) "resident-1"))
    (is (= (get-in result ["record" "kind"]) "treatment-plan-finalization-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest treatment-plan-validation-rules
  (is (thrown? Exception (r/register-treatment-plan-finalization "" "JPN" 0)))
  (is (thrown? Exception (r/register-treatment-plan-finalization "resident-1" "" 0)))
  (is (thrown? Exception (r/register-treatment-plan-finalization "resident-1" "JPN" -1))))

;; ----------------------------- register-crisis-response-finalization -----------------------------

(deftest crisis-response-is-a-draft-not-a-real-response
  (let [result (r/register-crisis-response-finalization "resident-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest crisis-response-assigns-response-number
  (let [result (r/register-crisis-response-finalization "resident-1" "JPN" 3)]
    (is (= (get result "response_number") "JPN-CRS-000003"))
    (is (= (get-in result ["record" "resident_id"]) "resident-1"))
    (is (= (get-in result ["record" "kind"]) "crisis-response-finalization-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest crisis-response-validation-rules
  (is (thrown? Exception (r/register-crisis-response-finalization "" "JPN" 0)))
  (is (thrown? Exception (r/register-crisis-response-finalization "resident-1" "" 0)))
  (is (thrown? Exception (r/register-crisis-response-finalization "resident-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-treatment-plan-finalization "resident-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-treatment-plan-finalization "resident-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-TPL-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-TPL-000001" (get-in hist2 [1 "record_id"])))))
