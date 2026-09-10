(ns behavioral.facts-test
  (:require [clojure.test :refer [deftest is]]
            [behavioral.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest irl-has-a-spec-basis
  (let [basis (facts/spec-basis "IRL")]
    (is (some? basis))
    (is (= "Ireland" (:name basis)))
    (is (string? (:owner-authority basis)))
    (is (re-find #"Health Information and Quality Authority" (:owner-authority basis)))
    (is (string? (:legal-basis basis)))
    (is (re-find #"Health Act 2007" (:legal-basis basis)))
    (is (string? (:national-spec basis)))
    (is (= "https://www.hiqa.ie/areas-we-work/disability-services" (:provenance basis)))
    (is (= ["Resident-intake record"
            "Treatment-plan-disclosure document"
            "Risk-assessment report"
            "Supervision-staffing certification"]
           (:required-evidence basis))
        "same generic 4-item evidence shape as the GBR/USA entries")))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest irl-is-counted-in-full-catalog-coverage
  (let [report (facts/coverage)]
    (is (= 5 (:covered report)) "DEU/GBR/IRL/JPN/USA now seeded")
    (is (some #{"IRL"} (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

(deftest irl-required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "IRL")]
    (is (= 4 (count all)))
    (is (facts/required-evidence-satisfied? "IRL" all))
    (is (not (facts/required-evidence-satisfied? "IRL" (rest all))))))
