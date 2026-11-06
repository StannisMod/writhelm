# Architecture direction

Status: proposal. These are design boundaries, not implementation claims.

## Specification-first checkpoint

The owner directed a specification-and-scenarios pass before further implementation design.
The new local drafts are `docs/specification.md`, `docs/acceptance.md`, `docs/workflow.md` and
`docs/tasks/SPEC-001.md`. Their publication scope and detailed requirements await approval.
The contract-first direction subsequently added local draft `docs/contracts/accepted-knowledge.md`;
it too remains outside the approved publication scope.
The earlier G1-G6 proposal below is held for comparison, not queued for implementation.
The maintained-fork decision and imported baseline are historical facts. The owner has confirmed
the move away from a maintained fork to a Writhelm-owned core with selective reuse (F3 below).
Implementation must still follow approved requirements and a bounded design; this direction does
not accept the surrounding specification wholesale.

Local design task `docs/tasks/DESIGN-001.md` now compares the foundations in
`docs/design/foundation.md`, using K-APP draft 2. The first-slice target includes one changed stable
clause, preservation of independent clause approval/check applicability and two pinned consumers.
The whole-document-only gate below predates this refinement; neither it nor held G1-G6 supersedes
the clause-local direction. The comparison records the selected direction and reuse tradeoffs;
it is not another foundation approval request or authorization to change the imported code.

Local task `docs/tasks/DESIGN-002.md` records accepted representation direction R1 in
`docs/design/knowledge-model.md`: whole-document immutable revisions, scoped clause approvals,
explicit continuity/context and pinned consumer manifests, including explicit planner-assessed
mixed-revision selection. The ruling does not approve every draft field/test or select held G1-G6.
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
tests with no failures/errors/skips. The imported runtime is not a dependency. Independent review,
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

Status: historical proposal held, not queued for implementation. F1 is superseded by F3; F2 remains.
Behavioral implementation still requires approval. Source import is governed by publication decision I1.
Its acceptance scope is one centrally stored knowledge document, exact-revision ratification and two
authenticated thin clients. No worker dispatch, retrieval, embeddings or model calls are required.
Revision 2 adds the source-grounded decisions G1-G6 below; those decisions remain proposed.
Where they refine the earlier protocol, revision 2 is the proposal to review. No behavioral code
changes or new guarantees follow from the completed upstream import.

### Foundation and maintenance

**Accepted clarification F3:** the owner confirmed that the maintained-fork direction had already
been abandoned. Use a Writhelm-owned core with selective upstream reuse. Do not reopen this as a
new foundation choice. Preserve the imported baseline pending a separately scoped implementation
change. This supersedes F1 and the fork-specific stack/integration instructions below, not F2's
one-repository layout. Exact component, persistence and execution designs remain to be approved.

**Historical decision F1 (2026-11-02), superseded by F3:** the owner selected a maintained fork of Task Orchestrator
as Writhelm's workflow foundation. Writhelm is a distinct product built on that foundation, not a
renamed upstream distribution. A separate gateway/backend deployment is not the selected strategy.
This decision accepts the fork strategy only; it does not ratify the remaining first-slice design,
the source baseline, package layout or publication of imported files.

**Accepted decision F2 (2026-11-02):** use one repository with `backend/` for the actively developed
Writhelm server and `adapters/<harness>/` for native harness integrations. Preserve upstream's
`current/` Gradle module inside `backend/` during import. Shared versioned interface definitions
may later live in `contracts/`; no adapter or contract implementation is approved by this layout
decision. Separate package releases do not require separate repositories.

Extend the fork in the same service and persistence boundary, rather than placing a second
workflow authority beside it. The imported source baseline is upstream commit
`d9efd9f1546aa3020b54467366a215d378e8bbb3`:
[pinned project description](https://github.com/jpicklyk/task-orchestrator/blob/d9efd9f1546aa3020b54467366a215d378e8bbb3/README.md).
Keep its Kotlin/Ktor, SQLite and migration stack for the initial deployment.

Proposed maintenance policy: preserve upstream history and provenance, keep an upstream remote,
and maintain small, reviewable changes to ease selective updates.
Do not track a moving upstream branch in production. Qualify each baseline update against upstream
tests and Writhelm's authority, persistence and recovery tests before migration. Reusable upstream
fixes can be proposed separately; upstream acceptance is not a delivery dependency.

Before importing source, approve the exact public paths, preserve MIT notices, review dependency
licenses and record the baseline. Writhelm additions remain AGPL-3.0-only. Import and package layout
are a separate publication decision, not covered by approval of this design.

### Source check and decisions for approval

This is a static source inspection of the pinned baseline, not a test of new behavior. File paths
in the table are relative to
`backend/current/src/main/kotlin/io/github/jpicklyk/mcptask/current/`.

| Existing mechanism | Consequence for this slice | Source |
| --- | --- | --- |
| Documents normalize CRLF, store current text in SQLite and allow history deletion | The existing document write path cannot promise immutable, exact-byte, Git-backed accepted knowledge | `application/knowledge/document/DocumentService.kt`, `application/port/DocumentStore.kt` |
| Documents cascade on project deletion; item commands explicitly delete anchored documents | Protect the project binding as well as the new document endpoints | `infrastructure/sqlite/schema/DocumentsTable.kt`, `application/service/ItemCommandService.kt` |
| REST and MCP share a dispatcher, decoded requests and handlers | Reuse transport/auth/envelope plumbing rather than build another server | `interfaces/shared/tools/ToolDispatcher.kt`, `application/api/docs/DocHandlers.kt` |
| `ADMIN` grants capabilities; local mode has unrestricted ADMIN access; HUMAN identifies credential kind, not project ownership | Require an explicit owner grant, and refuse unauthenticated/local authority for governed operations | `interfaces/shared/tools/CallAccess.kt`, `application/api/Principal.kt` |
| A write unit can retry; batch handlers already execute inside one | Do not execute external Git writes inside a retried DB transaction | `application/port/UnitOfWork.kt`, `application/api/BatchRunner.kt` |
| Normal idempotency expires after 24 hours; detached execution can run twice and records its result separately | Neither path supplies durable publication-command semantics unchanged | `application/service/IdempotencyService.kt` |

**G1 — Governed knowledge is a new model in the existing backend, not a mirror of legacy documents.**
Add dedicated tables for project bindings, document identities, immutable revisions, approval history
and publication commands in the same SQLite database and migration chain. Store text only in Git;
SQLite stores identity/hash/Git location and authority, not another editable canonical body.
Keep upstream plans/pages/config rules unchanged. They do not become owner-approved knowledge by
being copied or by carrying an upstream `adopted_by` link. References explicitly distinguish governed
knowledge from legacy documents; no automatic migration, dual-write or fallback to legacy text.
Applying accepted knowledge to worker packets and workflow gates belongs to the later slice.

**G2 — Service-owned Git, exact bytes, no new Git library initially.**
Use a bare repository per project behind a Git storage port and a Git CLI adapter. Git is an explicit
runtime prerequisite. Invoke it with argument arrays, never shell interpolation. Clients supply
inline UTF-8 text, not filesystem paths or Git refs. Reject malformed Unicode; cap each body at
65,536 UTF-8 bytes in this slice. Do not normalize line endings or final newlines.
Use server-generated UUID identities and retained internal refs for revision commits; request data
never determines filesystem paths. Verify both the content SHA-256 and Git object identities.
Git writes complete outside any SQLite unit; readers resolve immutable objects, never a worktree.
Initially support a single service process with project-scoped publication locking and no automatic
Git pruning. Process-crash recovery is in scope; full power-loss/backup qualification is not.

**G3 — A durable publication-command journal, not the upstream 24-hour retry cache.**
For `propose`, authenticate and validate, then reserve `(principal, project, operation, request key)`
with a fingerprint, revision UUID and `pending` status in a short DB transaction. Fingerprint the
decoded command, including exact body bytes and all semantic fields; JSON formatting/key order and
HTTP versus MCP transport are not command identity. Reusing the key with another command conflicts.
The journal has no automatic expiry in this slice and is not touched by the upstream retry pruner.

After reservation, publish the Git objects and retained ref under the project lock, outside a DB
transaction. In a final DB transaction, recheck authority/project/parent, insert the visible revision,
audit event and result, and mark the command `committed` together. A matching retry of a committed
command returns its original result. An in-flight duplicate returns a retryable in-progress result;
it must not dispatch a second concurrent publisher. After process restart, a matching caller retry
may resume a pending command using its reserved identity and verifying any existing Git objects.
A pending row or Git ref alone never makes a revision visible or accepted.

`ratify` needs no new Git commit: verify immutable content first, then recheck authorization and
acceptance generation inside one DB unit and commit approval, pointer, audit and command result
together. It uses the same non-expiring key/fingerprint contract. Recheck current authorization even
before replaying a stored result; a revoked credential must not regain access through retry.
Keep external I/O out of BUSY-retried transaction bodies. Retain failed/pending work for inspection;
there is no implicit timeout promotion or operator-owned garbage collection in this slice.

**G4 — Explicit owner credentials and project grants, no ADMIN shortcut.**
The first supported deployment uses authenticated bearer HTTP and streamable MCP with the existing
credential pipeline. Use a service-owned startup configuration mapping project root UUIDs and
authenticated principal IDs to owner/contributor/reader grants. This configuration is outside the
ordinary project `config` tool and is loaded consistently at startup; changes require restart for
this slice. Validate bindings and fail startup for malformed/contradictory grants.
Exactly one HUMAN principal is owner per project; contributors may propose, readers may read, and
the owner has both permissions. Keep the credential's existing project scope as an additional
restriction; a grant never widens it. Tag-filtered scopes are unsupported for governed operations in
this slice and fail closed rather than treating tags as project ownership.

Governed handlers require a real authenticated principal, a project grant and the applicable
transport capability. They refuse local/stdio/no-proof authority; HUMAN alone, ADMIN alone, a seat
token and a request-body actor do not confer owner rights. Ratification rejects seat-token calls.
No new login UI, credential issuance API, JWKS ownership mapping or ownership delegation is included.
Possession of the owner credential is the authority boundary; the service does not prove that a
human physically reviewed the text. Protecting that credential remains an operator responsibility.

**G5 — Project lifetime cannot erase approval history.**
Bind knowledge to an existing depth-0 upstream project UUID. Once governed storage is provisioned,
reject deletion, demotion or reparenting of that project through every exposed item mutation route.
Use restrictive foreign keys for governed records plus shared command-service guards for structural
changes; do not cascade approval history. Test subtree/batch deletion and rollback, not only direct
root deletion. Ordinary task changes remain available. Project retirement, knowledge deletion and
ownership transfer need a later explicit lifecycle design, including owner authorization.

**G6 — One shared command path, deliberately not a batch-write wrapper.**
Reuse the tool registry/dispatcher and HTTP `/api/v1/ops/{tool}` plus MCP adapters. Extend the tool-spec
model with a single-command execution mode for publication commands that own their transaction
boundaries, rather than hiding writes in a read select or using `executeDetached`.
Proposed tools: `knowledge_propose`, `knowledge_ratify`, and `knowledge_read` with `revision` and
`accepted` selects. The two command tools require request keys and reject batching/`atomic` options.
They share request validation, application handlers and responses across transports. Preserve existing
tool behavior and update registry, documentation and architectural tests for the new execution mode.

### Implementation order after approval

1. Define schema/contracts, grants and the new command execution mode; add authority and project
   lifecycle rejection tests before external publication is wired.
2. Implement Git storage and the proposal journal, including restart/retry and fault injection.
3. Implement exact-revision ratification and pinned reads; exercise the full flow through both HTTP
   and MCP. Add CRLF/final-newline byte checks, retries beyond 24 hours, concurrent duplicate keys,
   revoked-access replay and project structural-mutation tests to the acceptance table below.
4. Run independent verification of consequential paths and the unchanged upstream regressions.
   Record implemented and verified status separately; do not infer owner acceptance from green tests.

Before any delegation, prepare its separate versioned task contract. Before adding implementation
paths, approve their publication scope; I1 covers the pinned import, not arbitrary future files.

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
upstream route names. Every request is project-scoped and authorized server-side.

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
