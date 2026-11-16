# Architecture direction

Status: proposal. These are design boundaries, not implementation claims.

## Specification-first checkpoint

The owner directed a specification-and-scenarios pass before further implementation design.
The new local drafts are `docs/specification.md`, `docs/acceptance.md`, `docs/workflow.md` and
`docs/tasks/SPEC-001.md`. Their publication scope and detailed requirements await approval.
The contract-first direction subsequently added local draft `docs/contracts/accepted-knowledge.md`;
it too remains outside the approved publication scope.
The owner selected a Writhelm-owned core (F3 below).
Implementation must still follow approved requirements and a bounded design; this direction does
not accept the surrounding specification wholesale.

Local design task `docs/tasks/DESIGN-001.md` records the foundation in
`docs/design/foundation.md`. The first-slice target includes one changed stable
clause, preservation of independent clause approval/check applicability and two pinned consumers.
The whole-document-only gate below predates this refinement and does not supersede the clause-local
direction. The foundation record is not another foundation approval request.

Local task `docs/tasks/DESIGN-002.md` records accepted representation direction R1 in
`docs/design/knowledge-model.md`: whole-document immutable revisions, scoped clause approvals,
explicit continuity/context and pinned consumer manifests, including explicit planner-assessed
mixed-revision selection. The ruling does not approve every draft field/test.
The active storage wave is `docs/tasks/DESIGN-003.md` revision 2. The owner requires structurally
versioned Git SSOT for all canonical state (S1); the split-canonical Git/SQLite P1 in
`docs/design/publication-recovery.md` was not accepted. The owner acknowledged P2's conversational
architecture scope in `docs/design/git-state.md`: one private instance repository/publication root,
whole-state/command-result commit, atomic publication, exact-snapshot reads and no required SQLite
in M1 (future index derived only). This is not bulk field/runtime/durability approval or implementation.
The next local task is `docs/tasks/DESIGN-004.md`, closing the concrete first-slice design.
Its local `docs/design/first-slice.md` proposes the A1 initial authority/client profile and derives
canonical record/command boundaries plus the I1-I3 implementation/M1-C1 verification plan. It is
not an implemented service, permission to publish new paths or blanket approval of that package.

Owner direction U1 requires structured approval choices, item-local changes-required comments and
decision-complete presentation. `docs/tasks/DESIGN-005.md` investigates actual client capabilities /
human-only delivery and proposes the contract in `docs/design/approval-interface.md`. Native form
support is not proof of human intent, complete presentation or comprehensibility; no universal
harness GUI or qualified privileged approval integration is claimed.

Accepted initial trust profile T1: cooperative agents following instructions; intentional approval
API/hook bypass resistance is not required for the first version. Keep server authorization, exact
structured request/item binding and complete decision information; no adversarial-isolation claim.
The displayed scratch screen is accepted as a design basis; polish is deferred. First bounded local
implementation is `docs/tasks/CORE-001.md`, the immutable document/reference part of I1, not the whole
service, approval policy, Git publisher or GUI.
CORE-001 is now implemented locally under `backend/server/` and author-checked by 53 targeted
tests with no failures/errors/skips. Independent review,
integration/service acceptance and publication of the new paths remain pending; P3 is only a
publication proposal. Next is I1b's canonical workflow records/codec/transitions, not UI hardening.
CORE-002 now implements the pure request/answer/approval/continuity/manifest transitions in memory,
with current project grants and exact bindings. The combined local suite has 90 passing cases,
including the 53 content regressions. Two targeted disposable semantic mutations were detected by
their regression assertions. JSON/wire codec, durable command identity/Git CAS and client wiring
remain unimplemented; no integration/publication/independent-review acceptance follows.

## Accepted cross-platform direction X1

The owner approved K-RUN revision 2 and explicitly required cross-platform development as a project
principle on 2026-11-04. Linux, Windows and macOS are target SERVICE platforms; remote client access
alone does not satisfy this direction. Preserve platform-independent domain, canonical encoding and
HTTP/MCP semantics. Isolate executable discovery, controlled Git environment, permissions, file locks/
synchronization, process containment, launcher and client secret storage behind qualified OS adapters.
systemd/cgroups and Windows Job Objects are candidates, not universal mechanisms or qualification.
The macOS mechanism remains to be selected and qualified. No silent fallback to weaker guarantees.

See [K-RUN revision 2](contracts/launch-recovery.md). NativeGit/RuntimeOwner at 05d1b9e2 are explicitly
Linux-only implementation/evidence, not an approved permanent restriction. No Windows/macOS runtime,
cross-OS data-store migration, power-loss or same-user adversarial isolation guarantee is inferred.
An explicit platform implementation/verification order is still needed. Older proposals and exact
historical task/results below retain their original scope rather than acquiring new test coverage.

## Ownership

The service owns canonical knowledge revisions, workflow records, accepted decisions, task snapshots,
context assembly and evidence applicability. Coding repositories retain their own source and history;
they do not need maintained copies of the canonical knowledge corpus.

All canonical knowledge, authority and workflow state is Git-versioned under S1. A database index
may contain no unique canonical facts. The publication/recovery protocol must expose a complete
canonical state; an uncommitted working file or an index update is not an accepted state transition.

## Planning and execution

The human works in a primary planning session with a large available context. It handles design,
decomposition, owner decisions, dependencies and integration judgment rather than routine implementation.
Large available capacity does not imply that every request should carry the entire corpus.

Workers receive smaller task-specific contexts and isolated changes. Their model, reasoning effort,
context budget and compaction policy belong to an explicit execution profile. They may reason about
local implementation, but must escalate changes to product intent or delegated authority.

Independent verification checks the original task, consequential claims, evidence freshness and
critical implementation paths. Review depth follows risk. Selective inspection is not a substitute
for tests, and a worker's summary is not proof of completion.

After compaction or restart, the task revision, knowledge snapshot, scope and open obligations are
recovered from the service rather than inferred from a lossy conversation summary.

## Authority and budgets

An approval identifies the exact revision and authorized principal. A later proposal does not inherit
that approval. Historical decisions and evidence stay auditable without automatically applying to new work.

Context assembly distinguishes mandatory obligations from optional retrieval. If the mandatory closure
cannot fit, it must refuse or request an authorized scope change rather than silently omit requirements.
Budget enforcement must account for every outgoing request, including compaction, instructions and tools.

## Harness boundary

Adapters connect the workflow to native dispatch, session lifecycle and compaction surfaces. MCP alone
cannot constrain unrestricted shell access, native spawning or model history. Supported enforcement
boundaries must be documented and demonstrated for each harness.

## First-slice design, revision 2

Status: historical proposal held, not queued for implementation. F2/F3 define the current foundation.
Behavioral implementation still requires approval.
Its acceptance scope is one centrally stored knowledge document, exact-revision ratification and two
authenticated thin clients. No worker dispatch, retrieval, embeddings or model calls are required.

### Foundation and maintenance

**Accepted clarification F3:** Writhelm owns its core and canonical workflow authority.
Third-party component reuse requires scoped dependency/license review and project-contract tests.
Do not reopen the foundation choice. Detailed component, persistence and execution designs retain
their individual approval status.

**Accepted decision F2 (2026-11-02):** use one repository with `backend/` for the actively developed
Writhelm server (`backend/server/`) and `adapters/<harness>/` for native harness integrations.
Shared versioned interface definitions
may later live in `contracts/`; no adapter or contract implementation is approved by this layout
decision. Separate package releases do not require separate repositories.

The standalone server uses project-owned contracts, command publication and tests.
External component reuse requires explicit source/license review and separate publication approval.

### Storage and authority

- A service-managed Git repository stores immutable document bytes and history. Coding clients do
  not mount it or maintain corpus overlays. No remote Git hosting is required for this storage.
- SQLite owns document identity, revision metadata, accepted pointers, approval records, request
  deduplication and audit events. Git branches or working files do not grant acceptance.
- A revision identifies project, document, parent revision, content SHA-256, Git commit and blob,
  author principal and creation time. Revisions cannot be edited; changes create new revisions.
  Store and return the exact submitted UTF-8 bytes, without silent newline normalization.
- Each document has an accepted revision pointer and an acceptance generation. Approval records
  identify the revision, content hash, authenticated owner principal and resulting generation.
  A new proposal does not move the pointer. Previous approvals remain historical, not transferable.
- Initially each project has an explicitly provisioned owner. Contributors may propose revisions;
  readers may read permitted documents; only the owner may ratify. Identity comes from authenticated
  credentials, never an actor field in the request. Missing credentials or grants fail closed.

The deployment boundary is a trusted operator, one service process and persistent local storage.
An operator with direct database or filesystem access is outside the application authority boundary.
Credentials and project grants are service-managed; ordinary document/workflow tools cannot modify
them. Every exposed legacy mutation route must respect the same rules: an alternative tool must not
be able to edit immutable revision bytes, approval records or accepted pointers.

### Write and recovery protocol

1. `propose` authenticates the caller and checks project access, document identity and parent revision.
   It takes a request key scoped to principal, project and operation. Reusing a key with different
   request bytes is a conflict; completed identical requests return the original result.
2. Under a service publication lock, write the content to Git and create a durable commit. Verify
   its blob and hash before a database transaction makes the revision metadata visible. Only then
   acknowledge success. Uncommitted working files are never a read source.
3. A crash before the database transaction may leave an orphan commit, but no visible revision.
   The revision metadata, audit event and completed request result are committed together. A retry
   after a lost response returns that result, not a second visible revision. Orphans stay retained
   in this slice; automatic Git pruning is disabled.
4. `ratify` supplies the exact revision/hash, request key and expected acceptance generation.
   Verify the referenced Git content is durable and available. In one database transaction, recheck
   owner authority and generation, then write approval, accepted pointer, incremented generation,
   audit event and request result. No Git ref update is required to grant acceptance.
5. Concurrent approvals with the same expected generation cannot both succeed. Failed Git writes,
   failed verification, stale generations and rolled-back database writes leave acceptance unchanged.
6. After restart, reads resolve the database pointer to the recorded Git object and verify its hash.
   Missing or corrupt accepted content produces an explicit integrity/unavailable error, never a
   fallback to a newer proposal. Startup must not promote orphan commits.

This protocol avoids pretending that Git and SQLite share a transaction: immutable content is
published before any authoritative pointer can reference it. Durability requires supported local
filesystem synchronization for both stores; power-loss guarantees need separate qualification.
The first slice tests process crashes. Backup/restore must later preserve both stores as a consistent
set before the service is approved for operational use.

### Client contract

Expose equivalent authenticated HTTP and MCP operations through shared application handlers:
`propose`, `read_revision`, `read_accepted` and `ratify`. These names describe contracts, not final
external route conventions. Every request is project-scoped and authorized server-side.

Reads return exact text plus revision ID, hash and Git identity. `read_accepted` also returns the
acceptance generation and approval identity from a consistent database snapshot. Concurrent reads
may observe different generations; reads pinned to the same revision must return identical bytes.
No client-side cache is authoritative. A successful proposal response does not mean approval.

### Acceptance tests

Use entirely synthetic documents and isolated local storage; no external inference is needed.

| Check | Required outcome |
| --- | --- |
| Owner ratifies revision A; HTTP and MCP clients read it | Same revision, hash and exact bytes |
| Contributor proposes B | A remains accepted; B is readable only as a proposal |
| Contributor attempts ratification or supplies a forged actor | Denied; no acceptance or approval changes |
| Caller targets another project without a grant | Reads and writes denied |
| Two owner requests ratify different revisions at the same generation | One succeeds; the other conflicts |
| Identical request is retried after a lost response | Original result; no duplicate visible revision or approval |
| Request key is reused with a changed payload | Conflict; no mutation |
| Git commit fails during proposal | No visible revision; accepted content remains A |
| Process stops after Git commit but before database commit | Restart exposes no orphan revision and still accepts A |
| Ratification transaction fails or process stops around its commit | Restart exposes either the whole old or whole new state, never partial approval |
| Accepted Git blob is missing or corrupt | Explicit integrity error; no proposal fallback |
| Generic document/workflow mutation attempts to bypass ratification | Denied; the same invariants hold through every exposed route |

The test suite must exercise the real Git/database stores and both transports, not only mocked
application handlers. Failure injection points and restart assertions form part of the deliverable.

### Next-slice contract boundaries

Before dispatch is implemented, approve a separate versioned task schema containing objective,
exact inputs and knowledge snapshot, read/write/decision scope, constraints, dependencies,
acceptance criteria, escalation conditions and expected output. Changing those terms creates a new
task revision; existing workers and evidence do not silently acquire the new terms.

Evidence must identify task revision, knowledge snapshot, repository/tree identity, producer,
commands, outcomes and retained artifacts. Independent verification must come from a different
authorized principal, not a worker-written claim of independence. Only the owner may grant a
recorded, narrowly scoped exception. Implementation, verification, acceptance and integration remain
separate records; a changed tree invalidates automatic application of earlier verification.

Harness execution profiles, strict outgoing-request budgets and compaction restoration require
their own approved design before native dispatch. They are not guarantees of this first slice.
