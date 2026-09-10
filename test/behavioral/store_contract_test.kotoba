(ns behavioral.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [behavioral.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Tanaka" (:resident-name (store/resident s "resident-1"))))
      (is (= "JPN" (:jurisdiction (store/resident s "resident-1"))))
      (is (= 8 (:current-resident-count (store/resident s "resident-1"))))
      (is (= 2 (:current-staff-count (store/resident s "resident-1"))))
      (is (false? (:medication-adherence-flag? (store/resident s "resident-1"))))
      (is (= 20 (:current-resident-count (store/resident s "resident-3"))))
      (is (true? (:medication-adherence-flag? (store/resident s "resident-4"))))
      (is (false? (:treatment-plan-finalized? (store/resident s "resident-1"))))
      (is (false? (:crisis-response-finalized? (store/resident s "resident-1"))))
      (is (= ["resident-1" "resident-2" "resident-3" "resident-4"]
             (mapv :id (store/all-residents s))))
      (is (nil? (store/medication-screen-of s "resident-1")))
      (is (nil? (store/assessment-of s "resident-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/treatment-plan-history s)))
      (is (= [] (store/crisis-response-history s)))
      (is (zero? (store/next-treatment-plan-sequence s "JPN")))
      (is (zero? (store/next-crisis-response-sequence s "JPN")))
      (is (false? (store/resident-already-treatment-planned? s "resident-1")))
      (is (false? (store/resident-already-crisis-responded? s "resident-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :resident/upsert
                                 :value {:id "resident-1" :resident-name "Sakura Tanaka"}})
        (is (= "Sakura Tanaka" (:resident-name (store/resident s "resident-1"))))
        (is (= 8 (:current-resident-count (store/resident s "resident-1"))) "unrelated field preserved"))
      (testing "assessment / medication-screen payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["resident-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "resident-1")))
        (store/commit-record! s {:effect :medication-screen/set :path ["resident-1"]
                                 :payload {:resident-id "resident-1" :verdict :resolved}})
        (is (= {:resident-id "resident-1" :verdict :resolved} (store/medication-screen-of s "resident-1"))))
      (testing "treatment-plan finalization drafts a plan record and advances the sequence"
        (store/commit-record! s {:effect :resident/mark-treatment-planned :path ["resident-1"]})
        (is (= "JPN-TPL-000000" (get (first (store/treatment-plan-history s)) "record_id")))
        (is (= "treatment-plan-finalization-draft" (get (first (store/treatment-plan-history s)) "kind")))
        (is (true? (:treatment-plan-finalized? (store/resident s "resident-1"))))
        (is (= 1 (count (store/treatment-plan-history s))))
        (is (= 1 (store/next-treatment-plan-sequence s "JPN")))
        (is (true? (store/resident-already-treatment-planned? s "resident-1")))
        (is (false? (store/resident-already-treatment-planned? s "resident-2"))))
      (testing "crisis-response finalization drafts a record and advances the sequence"
        (store/commit-record! s {:effect :resident/mark-crisis-responded :path ["resident-1"]})
        (is (= "JPN-CRS-000000" (get (first (store/crisis-response-history s)) "record_id")))
        (is (= "crisis-response-finalization-draft" (get (first (store/crisis-response-history s)) "kind")))
        (is (true? (:crisis-response-finalized? (store/resident s "resident-1"))))
        (is (= 1 (count (store/crisis-response-history s))))
        (is (= 1 (store/next-crisis-response-sequence s "JPN")))
        (is (true? (store/resident-already-crisis-responded? s "resident-1")))
        (is (false? (store/resident-already-crisis-responded? s "resident-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/resident s "nope")))
    (is (= [] (store/all-residents s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/treatment-plan-history s)))
    (is (= [] (store/crisis-response-history s)))
    (is (zero? (store/next-treatment-plan-sequence s "JPN")))
    (is (zero? (store/next-crisis-response-sequence s "JPN")))
    (store/with-residents s {"x" {:id "x" :resident-name "n" :current-resident-count 8
                                  :current-staff-count 2 :medication-adherence-flag? false
                                  :treatment-plan-finalized? false :crisis-response-finalized? false
                                  :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:resident-name (store/resident s "x"))))))
