# Architecture direction

Status: proposal. These are design boundaries, not implementation claims.

## Ownership

The service owns canonical knowledge revisions, workflow records, accepted decisions, task snapshots,
context assembly and evidence applicability. Coding repositories retain their own source and history;
they do not need maintained copies of the canonical knowledge corpus.

Knowledge content has one canonical owner. Git publication and database state need an explicit
recovery protocol; an uncommitted working file must not be exposed as an accepted revision.

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

## First-slice design, revision 1

Status: proposed for owner approval, except for accepted decisions F1 and F2 below.
Behavioral implementation still requires approval. Source import is governed by publication decision I1.
Its acceptance scope is one centrally stored knowledge document, exact-revision ratification and two
authenticated thin clients. No worker dispatch, retrieval, embeddings or model calls are required.

### Foundation and maintenance

**Accepted decision F1 (2026-11-02):** the owner selected a maintained fork of Task Orchestrator
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
workflow authority beside it. The proposed source baseline is upstream commit
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
