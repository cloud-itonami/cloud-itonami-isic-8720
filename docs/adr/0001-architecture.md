# ADR-0001: cloud-itonami-isic-8720 -- BehavioralOps-LLM as a contained intelligence node

- Status: Accepted (2026-07-08)
- Related: `cloud-itonami-isic-6511`/`6512`/`6621`/`6622`/`6629`/`6520`/
  `6530`/`6820`/`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/
  `7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/
  `8610`/`9311`/`8510`/`9412`/`6491` ADR-0001s (the pattern this ADR
  ports); ADR-2607071250/ADR-2607071320/ADR-2607071351/ADR-2607071618/
  ADR-2607071640/ADR-2607071654/ADR-2607071717/ADR-2607071732/
  ADR-2607071752/ADR-2607071819/ADR-2607071849/ADR-2607071922/
  ADR-2607072715/ADR-2607072730/ADR-2607072745/ADR-2607072800/
  ADR-2607072815/ADR-2607072830/ADR-2607072845/ADR-2607072900/
  ADR-2607072915/ADR-2607080100/ADR-2607080200 (`6612`/`6492`/`6920`/
  `6611`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/
  `8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`/`9311`/`8510`/
  `9412`/`6491`, the twenty-three verticals built outside
  ADR-2607032000's original insurance/real-estate batch -- this is
  the twenty-fourth)
- Context: Continuing the standing "pick a new ISIC blueprint
  vertical" direction past `6491`, this ADR deepens `cloud-itonami-
  isic-8720` (residential care activities for mental retardation,
  mental health and substance abuse) from `:blueprint` to
  `:implemented`, the thirty-eighth actor in this fleet -- a SECOND
  human-health vertical alongside `8610`'s hospital and `8620`'s
  clinic, but for residential behavioral-health care rather than acute
  inpatient/outpatient medical care.

## Problem

A behavioral-health facility's treatment-plan-finalization/crisis-
response-finalization workflow bundles several distinct concerns
under one governed workflow:

1. **Jurisdiction behavioral-health-facility licensing correctness** --
   an official spec-basis citation from a real regulator (厚生労働省
   under the Mental Health Act/CMS's psychiatric-hospital special
   Conditions of Participation/the CQC/state PsychKG Acts), never
   fabricated.
2. **Supervision sufficiency** -- does a facility's own current
   resident-to-staff ratio stay within its own required maximum? The
   SECOND ratio-based instance in this fleet's check-family taxonomy
   (`leasing.registry/collateral-coverage-ratio-insufficient?`
   established the first, for a MINIMUM required ratio).
3. **Medication-adherence resolution verification** -- has a
   resident's medication-adherence concern actually stayed unresolved
   before a treatment plan is finalized? The behavioral-care-specific
   application of the unconditional-evaluation screening discipline
   this fleet's `casualty.governor/sanctions-violations` originally
   established -- a TWENTY-SECOND distinct grounding overall, and the
   FIRST specifically for a medication-adherence-flag concept.
4. **Real, high-stakes actuation, twice** -- finalizing a real
   treatment plan and finalizing a real crisis response are two
   independently-gated real-world acts on the SAME entity (a
   resident).

An LLM has no authority or grounding for any of these. The design
problem is therefore not "run a behavioral-health facility with an
LLM" but "seal the LLM inside a trust boundary and layer evidence-
sufficiency, supervision-ratio verification, medication-adherence-
resolution verification, audit and human-approval on top of it, while
structurally fixing both real actuation events as human-only."

## Decision

### 1. BehavioralOps-LLM is sealed into the bottom node; it never finalizes a treatment plan or crisis response directly

`behavioral.behavioralopsllm` returns exactly five kinds of proposal:
intake normalization, jurisdiction behavioral-health-facility
checklist, medication-adherence screening, treatment-plan-finalization
draft, and crisis-response-finalization draft. No proposal writes the
SSoT or commits a real treatment-plan/crisis-response finalization
directly.

### 2. OperationActor = langgraph-clj StateGraph, 1 run = 1 behavioral-care operation

`behavioral.operation/build` is the SAME StateGraph shape as every
sibling actor's operation namespace, copied verbatim.

### 3. `supervision-ratio-insufficient?` is the SECOND ratio-based instance in this fleet's check-family taxonomy, validating the MAXIMUM direction

`leasing.registry/collateral-coverage-ratio-insufficient?` established
the FIRST ratio-based check in this fleet, comparing a lease's
collateral value over its financed amount against a required MINIMUM
floor. `supervision-ratio-insufficient?` is the SECOND instance,
applying the SAME quotient-comparison shape to the opposite (MAXIMUM)
direction: a facility's own current resident count divided by its own
current staff count must NOT exceed a required ceiling -- proving the
ratio family's generality across both directions, the same way the
two-field direct-comparison families (MINIMUM-threshold, MAXIMUM-
ceiling) were each shown to generalize across temporal and non-
temporal ground truths.

### 4. Medication-adherence-flag screening reuses the unconditional-evaluation discipline for a twenty-second distinct grounding, and a first for this concept

`medication-adherence-flag-unresolved-violations` reuses `casualty.
governor/sanctions-violations`'s fix (evaluated unconditionally, not
scoped to a specific op, so the screening op itself can HARD-hold on
its own finding) for `:medication-adherence/screen` AND `:treatment-
plan/finalize` -- the TWENTY-SECOND distinct application of this exact
discipline in this fleet overall, and the FIRST specifically for a
medication-adherence-flag concept. This check deliberately does NOT
gate `:crisis-response/finalize` -- a medication-adherence concern
about a resident's ongoing treatment plan is a distinct clinical
question from whether the FACILITY currently has adequate staffing to
safely execute a crisis response, so extending the same "presence of a
problem blocks the actuation" shape to that op would conflate two
unrelated concerns (the same reasoning `leasing`'s ADR-0001 already
established for scoping an unconditional-evaluation check to only one
of two actuations, per that ADR's own Decision 4).

### 5. The unconditional-evaluation check is tested via the SCREENING op directly, per the lesson already recorded by `parksafety` and eleven later siblings

`medication-adherence-flag-is-held-and-unoverridable` calls
`:medication-adherence/screen` directly against `resident-4` (an
unresolved concern), NOT `:treatment-plan/finalize` against an
unscreened resident -- because a failing screen is itself a HARD hold
whose payload never persists to the store, so the actuation op alone
could never discover the bad ground-truth flag through this check
family without the screening op having actually been run first. This
build applied that lesson PROACTIVELY for a twelfth consecutive
vertical (after `eldercare`, `museum`, `conservation`, `salon`,
`entertainment`, `casework`, `hospital`, `facility`, `school`,
`association` and `leasing`), further reinforcing that lessons
recorded in this fleet's ADRs transfer forward reliably.

### 6. Dual actuation, matching `6512`/`6622`/`6520`/`6530`/`6820`/`6920`/`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/`8510`/`9412`'s shape

`behavioral.governor`'s `high-stakes` set has exactly two members
(`:actuation/finalize-treatment-plan`, `:actuation/finalize-crisis-
response`), each acting on the SAME resident entity, each with its OWN
history collection (`treatment-plan-history`/`crisis-response-
history`), sequence counter and dedicated double-actuation-guard
boolean.

### 7. Double-finalization guards check dedicated booleans, not `:status`

`already-treatment-planned-violations`/`already-crisis-responded-
violations` check `:treatment-plan-finalized?`/`:crisis-response-
finalized?`, dedicated booleans set once and never cleared, rather
than a `:status` value that could legitimately advance past a checked
state (the exact trap `cloud-itonami-isic-6492`'s ADR-0001 documents
in detail, explicitly avoided BY DESIGN in every sibling actor's
equivalent guard since). This actor's `:status` never needs to encode
"has this actuation already happened" at all -- a deliberate
architectural choice applied here for a twenty-third consecutive time.

### 8. No bespoke capability lib

Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/
`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`/`9311`/`8510`/`9412`,
and unlike most other actors in this fleet, this vertical's resident
records are practice-specific rather than a shared cross-operator data
contract -- `behavioral.*` runs on the generic identity/forms/dmn/
bpmn/audit-ledger stack only, per the blueprint's own explicit
statement.

## Consequences

- (+) Residential behavioral-health care gets the same governed,
  auditable-actor treatment as the thirty-one prior actors, and this
  fleet now has a TWENTY-FOURTH concrete precedent for extending past
  ADR-2607032000's original scope, deepening human-health coverage
  alongside `8610`'s hospital and `8620`'s clinic with a genuinely
  different care model (residential behavioral health vs. acute
  medical care).
- (+) `supervision-ratio-insufficient?` is a genuine structural
  contribution: the second ratio-based instance in this fleet's
  taxonomy, validating the MAXIMUM direction and proving the family's
  generality alongside `leasing`'s MINIMUM-direction instance.
- (+) `medication-adherence-flag-unresolved-violations` is a genuine
  domain-modeling contribution: the first unconditional-evaluation
  grounding for a medication-adherence-flag concept, and deliberately
  scoped to only ONE of two actuations by the SAME domain-reasoning
  discipline `leasing`'s ADR-0001 established.
- (+) The actuation invariant (governor + phase, two layers) is
  regression-tested by `test/behavioral/phase_test.clj`'s `treatment-
  plan-finalize-never-auto-at-any-phase`/`crisis-response-finalize-
  never-auto-at-any-phase`.
- (+) `MemStore` ‖ `DatomicStore` parity is proven by `test/
  behavioral/store_contract_test.clj`, the same `:db-api`-driven swap
  pattern every sibling actor uses.
- (+) The medication-adherence-flag test/demo correctly applied the
  established SCREENING-op-directly pattern for a twelfth consecutive
  vertical -- further evidence that lessons recorded in this fleet's
  ADRs continue to transfer forward reliably.
- (-) This R0 seeds only 4 jurisdictions (JPN, USA, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `behavioral.facts/
  coverage` reports this honestly rather than claiming broader
  coverage.
- (-) `supervision-ratio-insufficient?` models only a single
  representative maximum supervision ratio, not a full clinical-
  decision-support/case-management engine (diagnosis-specific
  treatment protocols, full incident-reporting/regulatory-notification
  workflows are out of scope -- see that fn's own docstring); real
  behavioral-health-information-system integration and real-time
  behavioral monitoring are all out of scope for this OSS actor --
  each operator's responsibility (see README's coverage table).
- 36 tests / 174 assertions, lint clean.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Add this as an addendum to any prior post-batch ADR | ❌ | All twenty-three of those ADRs' titles and scopes are explicitly `cloud-itonami-isic-6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`/`9311`/`8510`/`9412`/`6491`; mixing a different health-services sub-domain into `8610`'s or `8620`'s ADR would blur scope boundaries even where the broad human-health sector overlaps |
| Keep `cloud-itonami-isic-8720` at `:blueprint` only | ❌ | The standing direction continues past `6491`; residential behavioral-health care is a natural, well-precedented next domain, deepening this fleet's human-health coverage alongside `8610`'s hospital and `8620`'s clinic with a genuinely different care model |
| Scope `medication-adherence-flag-unresolved-violations` to gate BOTH actuations (matching `hospital`'s credential-not-current shape exactly) | ❌ | A resident's ongoing medication-adherence concern is a distinct clinical question from the facility's current staffing adequacy for crisis response -- blindly copying the "gate both actuations" shape would conflate two unrelated concerns, the same domain-specific reasoning `leasing`'s ADR-0001 already established for its own analogous scoping decision |
| Model `supervision-ratio-insufficient?` as a reuse of an existing MAXIMUM-ceiling direct-comparison check | ❌ | The actual comparison shape (a ratio of two fields against a required maximum, not a direct field-to-field comparison) is genuinely different; honestly framing this as the SECOND ratio-based instance, validating the MAXIMUM direction after `leasing`'s MINIMUM direction, keeps the fleet's check-family taxonomy accurate |
| Test `medication-adherence-flag-unresolved-violations` via an actuation op against an unscreened resident (the shape `parksafety`'s ORIGINAL, buggy test used) | ❌ | Already proven wrong by `parksafety`'s own ADR-2607071922 Decision 5 and reconfirmed by eleven later siblings' ADR-0001s -- a failing screen never persists its payload to the store, so the actuation op alone cannot discover the bad ground-truth flag through this check family; this build tested the SCREENING op directly from the start |
| Reference a capability lib (e.g. a hypothetical `kotoba-lang/behavioral`) for consistency with most prior actors | ❌ | The blueprint itself explicitly states this vertical's records are practice-specific, not a shared cross-operator contract -- inventing a capability lib reference where the blueprint says none exists would misrepresent the domain, the same reasoning established by every "no bespoke capability lib" sibling's ADR |

## References

- ADR-2607071250/ADR-2607071320/ADR-2607071351/ADR-2607071618/
  ADR-2607071640/ADR-2607071654/ADR-2607071717/ADR-2607071732/
  ADR-2607071752/ADR-2607071819/ADR-2607071849/ADR-2607071922/
  ADR-2607072715/ADR-2607072730/ADR-2607072745/ADR-2607072800/
  ADR-2607072815/ADR-2607072830/ADR-2607072845/ADR-2607072900/
  ADR-2607072915/ADR-2607080100/ADR-2607080200 (`6612`/`6492`/`6920`/
  `6611`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/
  `8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`/`9311`/`8510`/
  `9412`/`6491`, first twenty-three post-batch verticals)
- ADR-2607032000 (original insurance/real-estate batch, Addenda 1-7)
- `cloud-itonami-isic-8720/docs/adr/0001-architecture.md` (this ADR)
- `kotoba-lang/industry` `resources/kotoba/industry/registry.edn`
  (fleet-wide maturity registry)
