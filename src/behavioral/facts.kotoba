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
  overlap.

  The IRL entry cites HIQA (Health Information and Quality Authority)
  under the Health Act 2007 and the two 2013 designated-centres
  Regulations (S.I. No. 366/2013 registration, S.I. No. 367/2013 care
  and support), chosen over Ireland's OTHER verified regulator for
  this domain -- the Mental Health Commission, established under the
  Mental Health Act 2001, which registers and annually inspects
  'approved centres' under the Mental Health Act 2001 (Approved
  Centres) Regulations 2006 (S.I. No. 551/2006, e.g. Reg 15 individual
  care plan / Reg 26 staffing / Reg 32 risk management). Both are
  real, independently verified spec-bases; HIQA's designated-centres
  regime was picked as the single citation here because its own
  statutory language is 'residential care'/'designated centres'
  (the closest terminological match to this catalog's ISIC 8720
  'residential care activities' scope) and because its 2013 Care and
  Support Regulations map one-to-one, by regulation number, onto this
  catalog's four generic evidence categories: Regulation 5
  (individualised assessment and personal plan) for treatment-plan-
  disclosure, Regulation 15 (staffing) for supervision-staffing,
  Regulation 24 (admissions and contract for the provision of
  services) for resident-intake, and Regulation 26 (risk management
  procedures) for risk-assessment -- all four confirmed directly from
  irishstatutebook.ie this session. The Mental Health Commission path
  is recorded here, not fabricated as a second catalog entry, so a
  future extension has a real starting citation rather than a guess.
  NOT independently confirmed this session, and therefore NOT claimed
  by this entry: a distinct Irish statutory registration/inspection
  regime specific to standalone SUBSTANCE-ABUSE residential treatment
  centres (as opposed to disability- or mental-illness-designated
  facilities) -- this is an honest coverage gap, not an omission to
  be read as 'unregulated'.")

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
                              "Personalschlüsselnachweis (supervision-staffing certification)"]}
   "IRL" {:name "Ireland"
          :owner-authority "Health Information and Quality Authority (HIQA) -- Office of the Chief Inspector of Social Services"
          :legal-basis "Health Act 2007 (Act No. 23 of 2007, as amended), Part 7 (Office of the Chief Inspector of Social Services) and Part 8 (Regulation of Designated Centres, incl. s.46 prohibition on an unregistered designated centre); Health Act 2007 (Registration of Designated Centres for Persons (Children and Adults) with Disabilities) Regulations 2013 (S.I. No. 366/2013, as amended); Health Act 2007 (Care and Support of Residents in Designated Centres for Persons (Children and Adults) with Disabilities) Regulations 2013 (S.I. No. 367/2013)"
          :national-spec "S.I. No. 367/2013 Regulation 5 (individualised assessment and personal plan, prepared within 28 days of admission), Regulation 15 (staffing numbers/qualifications/skill-mix), Regulation 24 (admissions and contract for the provision of services) and Regulation 26 (risk management procedures), inspected against HIQA's National Standards for Residential Services for Children and Adults with Disabilities"
          :provenance "https://www.hiqa.ie/areas-we-work/disability-services"
          :required-evidence ["Resident-intake record"
                              "Treatment-plan-disclosure document"
                              "Risk-assessment report"
                              "Supervision-staffing certification"]}})

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
