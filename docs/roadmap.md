# Capability roadmap

Status: proposed. Each phase needs an approved design and independently checkable acceptance criteria.
This is a capability map, not a delivery schedule or a statement of implemented features.

| Phase | Deliverable | Acceptance gate |
| --- | --- | --- |
| 0. Contracts and foundation | Authority model; task/revision/evidence schemas; upstream extension strategy; licensing; first harness execution profiles | Owner approves the design; upstream baseline is reproducible; model/effort/context/compaction behavior is qualified with explicit gaps |
| 1. Central service | Authenticated clients, project isolation, durable workflow, retry semantics and API/MCP access | Multiple thin clients share state without canonical corpus copies in coding repositories; unauthorized mutations fail |
| 2. Canonical knowledge | Internally managed Git, stable clause identifiers, section access, historical reads and provenance | Structural edits preserve clause identity; publication failures cannot expose uncommitted content as accepted |
| 3. Ratification and snapshots | Exact-revision owner approval; proposed/accepted/superseded states; task and branch knowledge manifests | Changed content does not inherit approval; distinct snapshots remain complete and reproducible |
| 4. Bounded delegation | Required task contract before dispatch; child tasks, dependencies, claims and strict reviewer/waiver authority | Incomplete tasks cannot dispatch; workers cannot approve themselves or grant their own review exceptions |
| 5. Context assembly | Pinned task plus mandatory dependency closure; separate optional retrieval; budgets and manifests | Obligations survive or assembly explicitly refuses; no silent truncation; all outgoing requests respect the supported budget policy |
| 6. Evidence and acceptance | Distinct accepted/implemented/verified/integrated states; evidence bound to task revision and code tree | Stale evidence cannot advance a changed implementation; independent review and retry behavior are reproducible |
| 7. Native lifecycle adapters | Planner/worker/verifier flow, child compaction and restart recovery; two harness integrations | A workflow resumes across supported harnesses without losing authority, scope or open obligations |
| 8. Operational pilot | Backup/restore, upgrades, security and concurrency checks, interrupted-publication recovery | The complete design-to-integration flow passes acceptance; no acknowledged writes are silently lost |
| 9. Quality and economics | Aggregate planner/worker/verifier cost, caching, rework and context-completeness evaluation | Owner accepts the measured quality/cost tradeoff and documented limitations; paid tests remain separately authorized |

## First vertical slices

### Owner-directed pilot target — governed OpenCode workflow

On 2026-11-05 the owner directed design of the next slice and rejected a standalone knowledge-only
M1 user trial: the first user pilot must exercise full agent management inside the harness. Call this
planning target H1, combining the M1 knowledge/authority foundation with the bounded-work workflow:
canonical tasks, bound mandatory inputs, native assignments/lifecycle, checkpoints/resume, applicable
evidence, independent review and acceptance/integration. OpenCode remains the first selected harness;
this is a managed integration, not authorization to replace OpenCode with a new model runtime.

This changes pilot sequencing, not previous acceptance results or laws. M1 remains an unaccepted
foundation with explicit pending defects/gates. Do not schedule another standalone M1 GUI/user pilot
by default; preserve current bounded remediation/evidence and cheap checks, then plan combined
qualification at an explicitly agreed checkpoint. No safety/verification gate or cross-platform target
is waived. H1 design/contracts and technical feasibility still require their own scoped approvals.
Live inference, embeddings and paid trials remain separately authorized; this request is design work.

The owner subsequently approved the H1 boundary and service-control/native-execution split on
2026-11-05; current scope: [K-HARNESS revision 3](contracts/agent-workflow.md). This unblocks
component contract design, beginning with K-TASK, not blanket behavioral implementation or live calls.

### Current H1 boundary and approved completion order

The approved connected-work amendment assigns blocker resolution, consistent environment preparation,
minimal sufficient notification and eligible continuation to the engine. Exact identities remain
technical evidence, not repeated chat payloads. A bounded portable implementation candidate has
completed independent review and scoped parent acceptance in isolated development worktrees; it is
not yet integrated into this source tree or qualified as an operational OpenCode service.

The owner approved autonomous execution of this completion plan. Existing separate permissions for
live inference, working-profile installation, the user pilot and public push remain unchanged.

1. Integrate the exact accepted portable candidate into the target source tree, preserving existing
   work and upstream notices. Review the exact new publication paths/content and reproducible test
   prerequisites before tracking them. Verify the affected assembled target; retained evidence is not
   relabeled as a fresh run. Commit integration separately from this documentation checkpoint.
2. Complete the actual service/native composition: protected scoped credentials and trusted session/
   host binding, retained request/observation custody, qualified source/work-area capture and a real
   bridge between the service engine and pinned OpenCode APIs, including task-shaped checkpoint,
   compaction and restoration. No permissive production defaults or owner-key forwarding. Unsupported
   OS/profile adapters refuse explicitly.
3. Complete in-harness task/progress/blocker/review/acceptance/integration presentation against the
   actual service. Prepare it before the combined checkpoint rather than launching a broad campaign
   per small edit.
4. Qualify Linux/OpenCode Desktop 2.2.5 through one bounded combined model-free integration checkpoint:
   shared-mechanism repair and confirmation, unchanged-base and authorized-rebind continuation,
   minimal notification, current-rights changes, loss/retry/restart, pause/next-work barriers and
   in-harness review/result recovery. Distinguish actual host evidence from portable mocks; keep
   unproved physical writer/source custody and abnormal recovery gates visible.
   Keep K-RUN recovery, the historical unexplained timeout, licensing/release and per-platform gates
   explicit. Windows/macOS remain service targets with separately qualified adapters.
5. Run the first complete H1 owner pilot only after its prerequisites and a separately authorized
   model/provider/effort/spend profile are ready. Live inference, installation into the working harness,
   GUI/user pilot and public push remain separate permissions, not consequences of local commits.

Future delegated work uses fresh low-effort sessions and task-shaped continuation, not resumed
accumulated worker histories. Implementation, evidence, acceptance, target integration and actual
platform support remain distinct facts.

1. Ratify one knowledge revision; read it from two clients; prove failed publication leaves the accepted revision unchanged.
2. Dispatch one bounded implementation task; independently verify the current code tree; accept the result with linked evidence.
3. Interrupt and resume that workflow in a second harness without importing the whole conversation.

## Out of initial scope

A new harness or model runtime, universal retrieval, automated ingestion of private chat histories,
enterprise multi-tenancy, a large UI and support for every harness at once.
