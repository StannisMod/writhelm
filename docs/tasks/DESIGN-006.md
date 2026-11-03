# DESIGN-006 — Strict codec and complete pure-state restoration contract

Task revision: 3. Status: disclosed K-CODEC revision-2 scope accepted; not implemented/verified.
Executor: primary session, no delegation. Authority: owner requested the next codec contract.

## Objective and exact scope

Prepare K-CODEC draft revision 2 before codec tests/code. Preserve accepted R1/P2/U1/T1 and the behavior
of CORE-001/002 at source commit `0c7a7757`. Specify strict encoding, full pure-state inventory,
historical restoration, typed commands/fingerprints and finite falsification cases. Do not reopen
the foundation, Git SSOT, clause-scoped authority or approval GUI design.

## Knowledge inputs

- `README.md`, `AGENTS.md`, `docs/publication.md`: status, approval and publication constraints.
- `docs/contracts/knowledge-content.md`, K-CONTENT revision 1: KC-01–06 and digest framing.
- `docs/contracts/knowledge-workflow.md`, K-WORKFLOW revision 1: WF-01–08.
- `docs/design/git-state.md`, revision 2: GS-01–05/07, accepted P2 boundary.
- `docs/design/first-slice.md`, revision 2: FS-01/02/04/06/07, proposed versus accepted scope.
- `backend/server/src/main/kotlin/dev/writhelm/core/knowledge/Content.kt` and
  `backend/server/src/main/kotlin/dev/writhelm/core/workflow/{ApprovalWorkflow,WorkflowRecords,ReviewPacket}.kt`:
  observed digest laws, every private state collection and exact command arguments.

All inputs above are pinned by source commit `0c7a7757`; this is a design-preparation task, not an
implementation task or delegation. A subsequent executable task must bind the approved contract
revision/digest, exact field schema/fixtures, concrete limits, source scope and qualified toolchain.

## Read/write/decision scope and constraints

Write only this task and `docs/contracts/state-codec.md`; both remain local/ignored until a scoped
tracking decision. No code, tests, dependency, ignore/allowlist or existing normative document changes.
No commit/push, worker, paid/live inference, Git data-store, credential or deployment work.
Routine field realization is proposed here; consequential behavioral changes require owner approval.
The initial drafting instruction alone did not accept the contract. The owner subsequently accepted
its disclosed revision-2 scope; unspecified schemas, credentials and implementation remain gated.

## Acceptance and escalation

Enumerate all current state, preserve content/digest laws, separate historical integrity from current
eligibility and temporal authorization, keep guards/ABA and replay, define canonical ordering and
strict parser/limit refusal, and disclose the missing durable receipt/store boundary. Derive positive
and negative cases before implementation. Escalate requirements inconsistent with current guarantees;
do not weaken validators or silently enlarge the already-implemented model.

## Result and status

Prepared K-CODEC draft revision 1, then revised it at the owner's request after discussion of per-file
hosting limits and author critique. Draft 1 is retained by digest in the contract's provenance section.
Source inspection found restoration must include policy,
proposal authors, request guards, heads/generations, direct and continuity authority, assessment
actors, blockers and frozen manifest provenance, not just documents and public-facing records.
The contract preserves historical records after revocation/blockers; it does not pretend an unordered
snapshot proves past authority. Canonical command encoding does not implement durable idempotency.
No behavior/test run or independent review is claimed. The owner has now accepted the disclosed
revision-2 scope and requested the next contract/consistency review in `docs/tasks/DESIGN-007.md`.
A separate bounded executable codec task still needs its exact schema/limits/dependency inputs;
receipt/publisher design precedes Git implementation.

Revision 2 separates per-record Git representation, streaming export and bounded API commands;
specifies generated sharded record keys and per-encoded-file admission limits; distinguishes full
restoration from affected-write checks, new-write limits from historical read/resource bounds, and
standalone structure from source/transition completeness. Added bidirectional inventory equalities,
parser qualification, version compatibility and explicit codec/publisher readiness gates. It does
not claim a standalone snapshot detects removal of every independent subgraph, unlimited corpus
capacity, trusted historical authorization from hashes, or a working Git/credential service.
Existing model semantics remain unchanged; neither new source tracking nor implementation follows
from the instruction to update the draft. Only the two local draft documents were modified then.
DESIGN-007 appends a source-linked author consistency review and acceptance/provenance status without
changing CD-01–08; it preserves the pre-review local bytes by their recorded digests. No public
tracking, implementation or passing independent/integration check follows from this status update.
