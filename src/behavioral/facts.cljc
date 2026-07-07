(ns behavioral.facts
  "Per-jurisdiction residential-behavioral-care licensing catalog --
  the G2-style spec-basis table the Behavioral Care Governor checks
  every jurisdiction/assess proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's behavioral-health
  residential-facility requirements, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official behavioral-
  health/mental-health-facility regulator (see `:provenance`); they
  are a STARTING catalog, not a from-scratch survey of all ~194
  jurisdictions. Extending coverage is additive: add one map to
  `catalog`, cite a real source, done -- never invent a jurisdiction's
  requirements to make coverage look bigger.

  The USA entry cites CMS's special Conditions of Participation for
  psychiatric hospitals (42 CFR §482.60), a MORE SPECIFIC citation
  than `hospital.facts`'s general institutional CMS CoP citation --
  the same 'cite the most domain-specific real regulator available'
  discipline this fleet's federated-jurisdiction catalogs follow. The
  DEU entry cites the state Mental Health/Psychiatric Care Acts
  (PsychKG der Länder), distinct from `school.facts`'s/`hospital.
  facts`'s citations even where the broad health/education sectors
  overlap.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  resident-intake/treatment-plan-disclosure/risk-assessment/
  supervision-staffing evidence set submitted in some form;
  `:legal-basis` / `:owner-authority` / `:provenance` are the G2
  citation the governor requires before any :jurisdiction/assess
  proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "厚生労働省 (Ministry of Health, Labour and Welfare, MHLW)"
          :legal-basis "精神保健及び精神障害者福祉に関する法律 (Act on Mental Health and Welfare for the Mentally Disabled)"
          :national-spec "精神科病院等における処遇・行動制限・人員配置基準"
          :provenance "https://www.mhlw.go.jp/"
          :required-evidence ["入所記録 (resident-intake record)"
                              "療養計画開示書 (treatment-plan-disclosure document)"
                              "リスクアセスメント報告書 (risk-assessment report)"
                              "人員配置証明書 (supervision-staffing certification)"]}
   "USA" {:name "United States"
          :owner-authority "Centers for Medicare & Medicaid Services (CMS)"
          :legal-basis "CMS Special Conditions of Participation for Psychiatric Hospitals (42 CFR §482.60)"
          :national-spec "Special-staffing, treatment-plan and restraint/seclusion requirements for psychiatric facilities"
          :provenance "https://www.cms.gov/medicare/health-safety-standards/quality-safety-oversight-general-information/psychiatric"
          :required-evidence ["Resident-intake record"
                              "Treatment-plan-disclosure document"
                              "Risk-assessment report"
                              "Supervision-staffing certification"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Care Quality Commission (CQC)"
          :legal-basis "Health and Social Care Act 2008 (Regulated Activities) Regulations 2014"
          :national-spec "CQC fundamental standards for mental-health/residential-care providers"
          :provenance "https://www.cqc.org.uk/"
          :required-evidence ["Resident-intake record"
                              "Treatment-plan-disclosure document"
                              "Risk-assessment report"
                              "Supervision-staffing certification"]}
   "DEU" {:name "Germany"
          :owner-authority "Landesministerien für Gesundheit/Soziales"
          :legal-basis "PsychKG der Länder (state Mental Health/Psychiatric Care Acts)"
          :national-spec "Behandlungsplan-, Risikobewertungs- und Personalschlüsselvorgaben für psychiatrische Einrichtungen"
          :provenance "https://www.bundesgesundheitsministerium.de/"
          :required-evidence ["Aufnahmeprotokoll (resident-intake record)"
                              "Behandlungsplanoffenlegung (treatment-plan-disclosure document)"
                              "Risikobewertungsbericht (risk-assessment report)"
                              "Personalschlüsselnachweis (supervision-staffing certification)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to finalize a
  treatment plan or crisis response on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-8720 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `behavioral.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
