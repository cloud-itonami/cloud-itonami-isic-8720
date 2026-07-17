# Operator Quickstart

## Prerequisites

- **Clojure 1.11+** with `clojure` CLI installed
- **Git** (to fork and clone this repository)
- For monorepo checkout only: `langgraph-clj` and `langchain-clj` as monorepo siblings (see `deps.edn` `:dev` alias)
- For standalone fork: git coordinates in `deps.edn` will resolve from GitHub (no local workspace needed)

## Run the Demo

The demo walks a single resident through a clean dual-actuation lifecycle plus five HARD-hold cases:

```bash
clojure -M:dev:run
```

Output: lifecycle decisions, gate evaluations, audit ledger entries, and hold reasons.

## Run Tests

Tests verify the Behavioral Care Governor contract, phase invariants, store parity, registry conformance, and facts coverage:

```bash
clojure -M:dev:test
```

For CI (errors fail; linting mirrors this):

```bash
clojure -M:lint
```

## Publish the Landing Page

The landing page is static HTML:

```bash
# Simply serve docs/ as static files
python3 -m http.server 8000 --directory docs/
```

Then open: `http://localhost:8000/index.html`

## Behavioral Care Governor

The independent safety layer is implemented in:

- **File:** `src/behavioral/governor.cljc`
- **Key checks:**
  - `:spec/basis` — official jurisdiction citation validation
  - `:evidence/incomplete` — treatment-plan/crisis-response evidence gates
  - `:supervision-ratio/insufficient` — resident-to-staff ratio validation (maximum direction)
  - `:medication-adherence/flag-unresolved` — unconditional medication-adherence evaluation
  - `:already-treatment-planned` / `:already-crisis-responded` — double-finalization guards
- **Test coverage:** `test/behavioral/governor_test.clj` — governor contract and hold scenarios

## Phase Model

Resident lifecycle phases control which operations are auto-eligible vs. human-approval-required:

- **File:** `src/behavioral/phase.cljc`
- **Phases:**
  - Phase 0: read-only (no auto operations)
  - Phase 1: assisted intake (`:resident/intake` auto-eligible only)
  - Phase 2: assisted assessment
  - Phase 3: supervised (both `:treatment-plan/finalize` and `:crisis-response/finalize` always human)

## Audit Ledger

Every intake, assessment, screening, treatment-plan, and crisis-response decision is recorded:

- **File:** `src/behavioral/store.cljc` — Store protocol and append-only audit ledger
- **Immutable:** Separate treatment-plan-finalization and crisis-response-finalization history

## Store & Registry

- **Store:** `src/behavioral/store.cljc` — MemStore (dev) or DatomicStore (production) with append-only ledger
- **Registry:** `src/behavioral/registry.cljc` — Treatment-plan/crisis-response draft records and supervision-ratio checks
- **Facts:** `src/behavioral/facts.cljc` — Jurisdiction-specific behavioral-health-facility licensing catalog with spec-basis citations

## Next Steps

1. **Deploy:** Fork this repository and configure your facility details (license, jurisdiction, staff)
2. **Self-host claim:** Register at https://itonami.cloud/isco-1212/ to claim a free self-hosted instance
3. **Go live:** See https://itonami.cloud/docs/go-live.md for managed hosting, support, and compliance certification
4. **Governance:** See `GOVERNANCE.md` for decision-making and security reporting
5. **Architecture:** See `docs/adr/0001-architecture.md` for the full design and decision history

## Support

- See `business-model.md` for pricing, unit economics, and revenue models
- See `operator-guide.md` for deployment, controls, and certification requirements
- Report security issues via `SECURITY.md`
