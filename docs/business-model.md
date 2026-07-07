# Business Model: Residential care activities for mental retardation, mental ...

## Classification

- Repository: `cloud-itonami-isic-8720`
- ISIC Rev.5: `8720`
- Activity: residential care activities for persons with intellectual/developmental disabilities, mental health conditions or substance-use disorders
- Social impact: care quality, data sovereignty, transparent audit

## Customer

- independent residential-treatment facilities
- cooperative behavioral-health providers
- community recovery programs

## Offer

- resident intake
- care/treatment-plan proposal
- incident/crisis-response proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per facility
- support: monthly retainer with SLA
- migration: import from an incumbent behavioral-health system
- per-resident-month fee

## Trust Controls

- no treatment plan or crisis response is finalized without human sign-off (licensed behavioral-health staff)
- a fabricated jurisdiction citation, incomplete evidence, an
  insufficient resident-to-staff supervision ratio, or an unresolved
  medication-adherence flag -- each forces a hold, not an override
- a resident's treatment plan or crisis response cannot be finalized
  twice: a double-finalization attempt is held off this actor's own
  resident facts alone, with no upstream comparison needed
- every intake, assessment, screening, treatment-plan and crisis-
  response path is auditable
- resident health data stays outside Git
- emergency manual override paths remain outside LLM control
