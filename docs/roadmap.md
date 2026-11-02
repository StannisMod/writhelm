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

1. Ratify one knowledge revision; read it from two clients; prove failed publication leaves the accepted revision unchanged.
2. Dispatch one bounded implementation task; independently verify the current code tree; accept the result with linked evidence.
3. Interrupt and resume that workflow in a second harness without importing the whole conversation.

## Out of initial scope

A new harness or model runtime, universal retrieval, automated ingestion of private chat histories,
enterprise multi-tenancy, a large UI and support for every harness at once.
