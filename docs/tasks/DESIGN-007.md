# DESIGN-007 — Durable command results and Git publication contract

Task revision: 2. Status: disclosed K-PUBLISH revision-1 scope approved; no implementation/verification.
Executor: primary session; no delegation. Authority: owner requested the next contract and a check
of K-CODEC against preceding contracts. The owner's acknowledgement accepts the disclosed K-CODEC
revision-2 scope, not unspecified schemas, credential deployment, implementation or publication.

## Objective and inputs

Prepare K-PUBLISH revision 1: private store identity/genesis, per-command receipts, current access,
atomic expected-old publication, bounded retry/recovery and exact reads. Check K-CODEC against
K-CONTENT/K-WORKFLOW, applicable K-APP obligations, accepted R1/P2 and their stated scope limits.

Inputs at source commit `0c7a7757`: `README.md`, `AGENTS.md`, `docs/publication.md`,
`docs/contracts/{accepted-knowledge,knowledge-content,knowledge-workflow}.md`,
`docs/design/{knowledge-model,git-state,first-slice}.md`, and the existing CORE-001/002 implementation.
The relevant primary documents were read; draft historical/proposed details are not elevated merely
because they appear beside accepted R1/P2 decisions. Additional local input:

| Input before this task's status/review appendices | SHA-256 |
| --- | --- |
| K-CODEC draft revision 2, `docs/contracts/state-codec.md` | `de93b44b1bc9c90d35d51a5782f826e41dfc2c29edadbbcb10a9ed9e6bc6085e` |
| `docs/tasks/DESIGN-006.md`, task revision 2 | `7bb948b9feb08c7927d73e8f26afa1c6d07fa8027a0a8b6441cf523c03d7f156` |

Retain these original local bytes before appending review results. Future executable tasks must pin
the approved final contract/schema/limits/runtime, not rely on this preparation task as authorization.

## Scope and constraints

Write `docs/contracts/command-publication.md`, this task, and status/consistency notes in the two
local DESIGN-006/K-CODEC documents. Do not silently change accepted codec laws or tracked earlier
contracts. All four paths remain ignored pending explicit scoped tracking. No code/tests/dependency,
Git store creation, upstream edits, allowlist changes, staging/commit/push, model calls or delegation.
No new credential/hosting/retention architecture, destructive recovery, enterprise origin-proof or
external exactly-once execution. Concrete publication/rejection semantics are proposed for approval.

## Acceptance and escalation

Define the key/fingerprint/result distinction, bounded per-file receipt format, whole effect/result
publication, same-key conflict, current-access replay, terminal versus infrastructure failures,
domain replay versus command replay, CAS revalidation, startup/integrity and unknown-outcome rules.
Map consequential guarantees to exact preceding clauses; distinguish compatible narrowing from
conflict, missing implementation from contradiction, and source inspection from executed evidence.
Escalate actual conflicts instead of overriding them. Keep cost-classification/runtime schemas as
explicit executable-task readiness requirements, not another product expansion.

## Result and review

Prepared K-PUBLISH draft revision 1 and a clause-linked review appendix in K-CODEC. No contradiction
found with accepted R1/P2 or current K-CONTENT/K-WORKFLOW laws within the stated scope. Historical
draft fields/credential profiles remain distinct from implemented/accepted capabilities. This finding
is an author review, not an independent claim or a guarantee about unspecified future schemas.

Explicitly resolved integration boundaries: core body coverage versus receipt storage, domain answer
replay versus durable command replay, core export versus whole-store backup/writes, original basis
versus current authorization/publication OID, and earlier wider first-slice plans versus actual core.
K-PUBLISH proposes receipt-only commits for admitted authorized terminal rejections, preservation of
original logical outcomes, bounded per-file journal storage, unknown-outcome reconciliation and no
automatic TTL/history deletion. A fingerprint is not falsely advertised as retained full input text.

No code, Git data repository, process fault experiment, test suite or independent review was run.
The owner explicitly approved the disclosed K-PUBLISH revision-1 scope; approval provenance is
recorded in the contract without altering PB-01–09. Remaining readiness: exact core/journal schemas,
concrete bounds, qualified parser/runtime and publisher ownership/recovery runbook; separate actual
credential/client profile before network exposure. Costly real verification remains M1-C1 gated.
Continue with bounded codec schema/limit/parser readiness, not another architecture decision or an
unqualified Git-store implementation. Approval does not authorize code before its stated inputs exist.
