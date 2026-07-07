# cloud-itonami-isic-8720

Open Business Blueprint for **ISIC Rev.5 8720**: Residential care
activities for mental retardation, mental health and substance abuse.
This repository publishes a behavioral-care actor -- resident intake,
jurisdiction assessment, medication-adherence screening, treatment-
plan finalization and crisis-response finalization -- as an OSS
business that any qualified, licensed behavioral-health facility
operator can fork, deploy, run, improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491)) --
a second human-health vertical alongside `8610`'s hospital and
`8620`'s clinic, but for residential behavioral-health care rather
than acute inpatient/outpatient medical care. Here it is
**BehavioralOps-LLM ⊣ Behavioral Care Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a
> resident-intake summary, normalizing records, and checking whether a
> facility's own current resident-to-staff supervision ratio has
> actually stayed within its own required maximum -- but it has **no
> notion of which jurisdiction's behavioral-health-facility licensing
> requirements are official, no license to finalize a real treatment
> plan or a real crisis response, and no way to know on its own
> whether a resident's medication-adherence concern has actually
> stayed unresolved**. Letting it finalize a treatment plan or crisis
> response directly invites fabricated jurisdiction citations, a
> crisis response finalized under insufficient supervision staffing,
> and an unresolved medication-adherence concern being quietly
> overlooked -- and liability, and resident-safety risk, for whoever
> runs it. This project seals the BehavioralOps-LLM into a single node
> and wraps it with an independent **Behavioral Care Governor**, a
> human **approval workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers resident intake through jurisdiction assessment,
medication-adherence screening, treatment-plan finalization and
crisis-response finalization. It does **not**, by itself, hold any
license required to operate a behavioral-health residential facility
in a given jurisdiction, and it does not claim to. It also does
**not** model a full clinical-decision-support/case-management engine
-- no diagnosis-specific treatment protocols, no full incident-
reporting/regulatory-notification workflow, no real-time behavioral
monitoring system (see `behavioral.registry`'s own docstring for the
honest simplification this makes: a single representative maximum
supervision ratio, not a facility-type-by-facility-type survey of
every staffing-standard variant). Whoever deploys and operates a live
instance (a licensed behavioral-health facility operator) supplies any
jurisdiction-specific license, the real clinical/behavioral-health
expertise and the real behavioral-health-information-system
integrations, and bears that jurisdiction's liability -- the software
supplies the governed, spec-cited, audited execution scaffold so that
operator does not have to build the compliance layer from scratch for
every new market.

### Actuation

**Finalizing a real treatment plan or a real crisis response is never
autonomous, at any phase, by construction.** Two independent layers
enforce this (`behavioral.governor`'s `:actuation/finalize-treatment-
plan`/`:actuation/finalize-crisis-response` high-stakes gate and
`behavioral.phase`'s phase table, which never puts `:treatment-plan/
finalize`/`:crisis-response/finalize` in any phase's `:auto` set) --
see `behavioral.phase`'s docstring and `test/behavioral/phase_test.
clj`'s `treatment-plan-finalize-never-auto-at-any-phase`/`crisis-
response-finalize-never-auto-at-any-phase`. The actor may draft, check
and recommend; a human licensed behavioral-health professional is
always the one who actually finalizes a treatment plan or crisis
response. Like `6512`/`6622`/`6520`/`6530`/`6820`/`6920`/`6611`/
`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/`8510`/`9412`,
this actor has TWO actuation events.

## The core contract

```
resident intake + jurisdiction facts (behavioral.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ Behavioral-  │ ─────────────▶ │ Behavioral                    │  (independent system)
   │ Ops-LLM      │  + citations    │ Care Governor:                │
   │ (sealed)     │                 │ spec-basis · evidence-       │
   └──────────────┘         commit ◀────┼──────────▶ hold │ incomplete ·
                                 │             │           │ supervision-ratio-
                           record + ledger  escalate ─▶ human   insufficient (RATIO-
                                             (ALWAYS for         based, MAXIMUM direction) ·
                                              :treatment-plan/        medication-adherence-
                                              finalize /              flag-unresolved
                                              :crisis-response/       (unconditional) ·
                                              finalize)               already-finalized
```

**The BehavioralOps-LLM never finalizes a treatment plan or a crisis
response the Behavioral Care Governor would reject, and never does so
without a human sign-off.** Hard violations (fabricated jurisdiction
requirements; unsupported evidence; an insufficient supervision ratio;
an unresolved medication-adherence flag; a double treatment-plan or
crisis-response finalization) force **hold** and *cannot* be approved
past; a clean treatment-plan/crisis-response proposal still always
routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a resident-monitoring robot
supports physical safety checks, under the actor, gated by the
independent **Behavioral Care Governor**. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions require
human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Behavioral Care Governor, treatment-plan-finalization + crisis-response-finalization draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`8720`). Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/
`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`/`9311`/`8510`/
`9412`, this vertical's resident records are practice-specific rather
than a shared cross-operator data contract, so `behavioral.*` runs on
the generic identity/forms/dmn/bpmn/audit-ledger stack only -- no
bespoke domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/behavioral/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate treatment-plan-finalization/crisis-response-finalization history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded resident, and the double-finalization guards check dedicated `:treatment-plan-finalized?`/`:crisis-response-finalized?` booleans rather than a `:status` value |
| `src/behavioral/registry.cljc` | Treatment-plan-finalization + crisis-response-finalization draft records, plus `supervision-ratio-insufficient?` -- the SECOND ratio-based instance in this fleet's check-family taxonomy (`leasing.registry/collateral-coverage-ratio-insufficient?` established the first, for a MINIMUM required ratio; this applies the MAXIMUM direction) |
| `src/behavioral/facts.cljc` | Per-jurisdiction behavioral-health-facility licensing catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/behavioral/behavioralopsllm.cljc` | **BehavioralOps-LLM Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment/medication-adherence-screening/treatment-plan-finalization/crisis-response-finalization proposals |
| `src/behavioral/governor.cljc` | **Behavioral Care Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · supervision-ratio-insufficient, pure ground-truth RATIO-based recompute · medication-adherence-flag-unresolved, unconditional evaluation, the TWENTY-SECOND grounding of this discipline and FIRST specifically for the medication-adherence-flag concept) + already-treatment-planned/already-crisis-responded guards + 1 soft (confidence/actuation gate) |
| `src/behavioral/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (both treatment-plan and crisis-response finalization always human; resident intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/behavioral/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/behavioral/sim.cljc` | demo driver |
| `test/behavioral/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers resident intake through jurisdiction assessment,
medication-adherence screening, treatment-plan finalization and
crisis-response finalization -- the core governed lifecycle this
blueprint's own `docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Resident intake + per-jurisdiction behavioral-health-facility checklisting, HARD-gated on an official spec-basis citation (`:resident/intake`/`:jurisdiction/assess`) | A full clinical-decision-support/case-management engine (diagnosis-specific treatment protocols, full incident-reporting/regulatory-notification workflows -- see `behavioral.registry`'s docstring) |
| Medication-adherence screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:medication-adherence/screen`) | Real behavioral-health-information-system integration, real-time behavioral monitoring |
| Treatment-plan finalization, HARD-gated on full evidence and a double-finalization guard (`:treatment-plan/finalize`) | Ongoing residential-care/day-program workflows themselves |
| Crisis-response finalization, HARD-gated on full evidence and supervision-ratio sufficiency, plus a double-finalization guard (`:crisis-response/finalize`) | |
| Immutable audit ledger for every intake/assessment/screening/treatment-plan/crisis-response decision | |

Extending coverage is additive: add the next gate (e.g. a restraint-
duration-exceeded check) as its own governed op with its own HARD
checks and tests, following the SAME "an independent governor re-
verifies against the actor's own records before any real-world act"
pattern this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`behavioral.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `behavioral.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `behavioral.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to make
coverage look bigger.

## Maturity

`:implemented` -- `BehavioralOps-LLM` + `Behavioral Care Governor` run
as real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the thirty-one
prior actors' architecture. See `docs/adr/0001-architecture.md` for
the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
