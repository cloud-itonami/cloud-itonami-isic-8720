# Governance

`cloud-itonami-isic-8720` is an OSS open-business blueprint for residential care activities for persons with intellectual/developmental disabilities, mental health conditions or substance-use disorders.
Governance covers both the capability layer and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the Behavioral Care Governor remains independent of the advisor.
- hard policy violations (fabricated assessment, incomplete records) cannot be
  overridden by human approval.
- finalizing a treatment plan or crisis response always escalates to a human -- never automated.
- every hold, approval and care-action path is auditable.
- patient/resident and client data stay outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:

- bypassing the Behavioral Care Governor's policy checks
- mishandling patient/resident/client data
- misrepresenting certification status
- failing to respond to security incidents
- hiding material changes to customer-facing operation
