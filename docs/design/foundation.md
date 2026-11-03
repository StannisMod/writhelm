# Foundation comparison — first accepted-knowledge slice and bounded work

Revision: 2. Status: owner confirmed own-core/selective-reuse direction; detailed design remains proposed.
Canonical task: [DESIGN-001](../tasks/DESIGN-001.md).
Contract: [K-APP draft 2](../contracts/accepted-knowledge.md).
Local drafting only; no publication, implementation or baseline deletion is authorized.

## Established direction and scope of this comparison

The owner confirmed that abandoning the maintained fork was already decided: use a Writhelm-owned
core with selective upstream reuse. The comparison explains reuse boundaries and the alternative's
tradeoffs; it must not be presented as another foundation decision. This does not approve a storage/wire schema,
the held G1-G6 design, execution profiles, a publication bundle or a behavioral implementation task.
The imported baseline stays intact under either choice until a separate scoped change is approved.

Selected direction: **Writhelm-owned core, selective reuse qualified component by component**. This
prioritizes ownership of the knowledge/task/evidence authority model over compatibility with the
upstream workflow. It is an architectural judgment, not a measured claim of faster/cheaper delivery.
The strongest reason for the fork is real M2 workflow reuse, not document storage. Its strongest
cost is having to govern the inherited mutation/identity/gating paths as well as the new model.

## Shared observable target

M1 changes accepted clause X while independent Y retains its approval/check applicability; old/new
consumers read their own immutable inputs before/after restart. An unapproved extra edit cannot
inherit acceptance. Interrupted publication yields no partial authority; retries cannot duplicate or
widen a ruling. See K-APP O1-O9 for focused obligations and one assembled two-consumer journey.

M1 must therefore include stable clause identity, scoped approval and two pinned input manifests.
These are not a general task runtime or a clause-merging engine. Documents remain revision units;
Git and the established semantic integration process handle branch changes. The planner explicitly
checks consistency/dependencies; the service does not promise natural-language impact inference.

M2 adds executable canonical task revisions, declared mandatory packets, qualified native dispatch
and execution restrictions, producer/tree-bound evidence, fresh-context non-author review and
authorized result acceptance/integration. Partial producer readiness may unblock a planned consumer
without claiming deferred expensive verification passed. Old expensive results do not qualify a
changed implementation by a planner's unproven impact judgment.

Both options still need the new knowledge model, publication/recovery design, exact clause/snapshot
references, authority mapping, and M2 harness/budget/sandbox qualification. Neither upstream test
success nor the earlier isolated mock harness observations establish those behaviors.

## Source evidence and consequences

Pinned upstream: `d9efd9f1546aa3020b54467366a215d378e8bbb3`, imported mapping in
[provenance](../upstream.md). Main paths below are relative to
`backend/current/src/main/kotlin/io/github/jpicklyk/mcptask/current/`; test paths to the corresponding
`src/test/kotlin/io/github/jpicklyk/mcptask/current/`. Ranges identify the inspected baseline, not
permanent line anchors after implementation. Statements here are static source findings.

| Evidence | Consequence, including the best case for reuse |
| --- | --- |
| `application/knowledge/document/DocumentService.kt:32-40,72-123,178-213`; `domain/model/Document.kt:78-92` | Useful whole-document history/CAS exists, but normalizes CRLF, keeps current text in SQLite and supports history deletion. Its version record lacks clause-local approval. Do not treat this as unchanged Git-backed governed knowledge. |
| `infrastructure/sqlite/schema/DocumentsTable.kt:31-34` | Root deletion cascades legacy documents. Adding protected records is possible; every exposed project/subtree mutation must then respect them. A new endpoint alone is insufficient. |
| `application/port/RepositoryProvider.kt`; `bootstrap/ServerComposition.kt:134-225` | Existing shared wiring is valuable. Provider/composition spans items, config, documents, claims, events, runs, seats and decisions. A new core cannot extract these services as unrelated leaf classes. |
| `interfaces/shared/tools/ToolSpec.kt:156-184`; `application/api/BatchRunner.kt`; `infrastructure/sqlite/UnitRunner.kt:33-46,80-108` | Write handlers run in database units, and BUSY/LOCKED retries the whole unit. External Git work must not be put inside that replayed body. A fork needs a transaction-owning command seam or equivalent; it need not replace all existing tools. |
| `application/service/IdempotencyService.kt:55-76,80-156`; `application/port/IdempotencyStore.kt` | The normal path atomically records effects/results, a useful mechanism. Its default 24-hour TTL is not permanent ruling identity; detached work may execute twice. The store itself does not impose TTL. Adapt policy/protocol rather than claiming all machinery unusable. |
| `infrastructure/sqlite/SqliteUnitOfWork.kt`; `infrastructure/sqlite/DatabaseManager.kt`; `infrastructure/sqlite/UnitRunner.kt` | Real transaction/retry/error/cancellation/pool infrastructure can be retained in a fork. Extracting it into a new core also pulls provider, events, config, errors and telemetry or requires replacing those dependencies. Library reuse is not drop-in source reuse. |
| `infrastructure/sqlite/schema/management/FlywayDatabaseSchemaManager.kt:19-30,73-88` | Fork retains released migration bytes/history and startup callbacks. New schema needs its own qualified migration setup or explicit adaptation; this manager is tied to upstream migrations/integrity/backfills, not just an independent Flyway wrapper. |
| `infrastructure/identity/CredentialResolver.kt:53-75,83-115,139-153` | Bearer/JWT validation has a callback seam for anchor validation and can be reused/adapted. It still uses upstream principal/scope/proof/capability types. Unauthenticated local mode supplies unrestricted ADMIN, so governed owner rights must be separately enforced; credential validation alone is not owner authorization. |
| `application/decision/DecisionService.kt:210-261` | Admitted answers, CAS, provenance, effects and events are atomic. Fork can add typed knowledge validation/effects rather than rewrite everything. Existing answer validation alone does not bind a clause/revision/digest approval. Extraction requires its stores, unit, caller/config/error/event contracts or their adaptation. |
| `application/run/SeatService.kt:123-189` | Real membership/schema/admission, attempt numbering, assignment supersession and claim release are valuable M2 reuse. `SeatAdmission` is an extension seam. Assigning a seat is not yet binding the full canonical task revision/snapshot/mandatory packet. All exposed assignment paths must enforce that new precondition. |
| `application/service/GatePredicate.kt:31-38,164-185,213-222` | Upstream can distinguish principal/seat pairs and waive same-actor findings through a temporal text marker. That predicate cannot be used unchanged as Writhelm's non-author/owner-exception review gate. This is a scoped mismatch, not a claim every upstream security gate is bypassable. |
| `interfaces/shared/tools/docs/DocsToolsBehaviorTest.kt:11-23,59-79` (test) | Tests couple the tool registry, parity harness and real SQLite fixture and intentionally pin LF-normalized text. Preserve them for legacy behavior in a fork; derive new knowledge tests from K-APP. Copying the class into a new core brings fixtures/dependencies and inappropriate oracles. |
| `infrastructure/sqlite/upgrade/GoldenV17UpgradeTest.kt:24-39,65-78,92-104` (test) | Upgrade coverage uses the upstream golden fixture, migration seeds, original chain and startup. This is valuable fork regression evidence, not a free new-schema upgrade suite. |

## Option A — Continue the maintained fork

Strongest viable form: add governed knowledge and canonical task/evidence records in the same
service, retaining upstream items/runs/claims/config/decision infrastructure where it conforms.
Keep legacy documents explicitly separate from accepted knowledge, with no implicit promotion or
dual authoritative body. A new model beside a legacy model inside one service is possible; a second
independent workflow authority is not the proposal.

- **Unchanged reuse:** libraries, existing persistence/transport composition, legacy behavior and
  its tests; much of run/claim/decision machinery can remain if new guarded paths bind it correctly.
- **M1 changes:** new clause-aware knowledge/approval/manifests, Git/recovery/retry protocol, owner
  grants, transaction-owning publication command wiring and protected project lifetime. Existing
  transport auth can be extended; upstream documents need not be rewritten into the new format.
- **M2 changes:** add canonical executable revisions/packets/evidence and enforce their gates at
  every relevant inherited entry point. Replace or exclude incompatible review-waiver semantics
  from governed transitions. Reuse admission/decision seams where adequate; native lifecycle,
  filesystem/process enforcement and outgoing budgets remain additional work.
- **Tests:** retain the legacy suite for behavior still exposed, add K-APP/new-workflow law and wiring
  tests, and explicitly classify/schedule broad regressions. Do not rerun the whole suite on every
  edit or weaken incompatible legacy assertions to pretend they test the new knowledge model.
- **Maintenance:** local Writhelm migrations extend an upstream-owned history; startup, common
  dispatch, identity and gate changes become recurring selective-update review points. Upstream
  fixes remain valuable. No automatic upstream merge, excluded-history publication or requirement
  to remain a drop-in upstream client is implied.

This option is preferable if retaining the rich upstream workflow as supported product behavior is
itself a priority. No evidence here proves the additive seams impossible or requires a total rewrite.

## Option B — Writhelm-owned core with selective reuse

One service, not a new gateway in front of the imported engine. Writhelm owns the canonical
knowledge/task/approval/evidence transitions. Reuse libraries and selected public-source mechanisms
only after tracing their dependencies and qualifying them against the new contracts. Keeping
Kotlin/Ktor/SQLite or similar existing tooling is compatible with this choice; a stack rewrite is
not the point and is not required by the recommendation.

- **M1:** implement the new knowledge/authority model already required in A, plus a deliberately
  bounded server composition, auth/grant enforcement, transaction/migration/command infrastructure
  and client wiring. Those latter pieces are real extra work, not supplied by the word "core".
- **M2:** implement/qualify canonical dispatch, claims, dependencies, checkpoints and review/evidence
  gates, adapting upstream patterns when justified. Foregoing unchanged run/seat/decision services
  loses tested behavior and requires equivalent contract coverage; do not reduce the comparison to
  a knowledge-only demo or claim all workflow complexity disappears.
- **Extraction candidates:** credential validation; database fault/retry/cancellation/locking
  mechanisms; assignment/admission/CAS and decision-provenance patterns. Treat each as a separate
  candidate with dependency closure and license/notice accounting. No extracted component has been
  compiled or behaviorally qualified in isolation in this task.
- **Not unchanged imports:** whole `RepositoryProvider`/`ToolExecutionContext`, old document model,
  default retry policy, original migration manager/chain or independent-review predicate. Taking
  their complete dependencies could reconstruct the fork while mislabelling it selective reuse.
- **Tests:** carry contract-relevant cases and needed fixtures with provenance, rewrite oracles and
  wiring only where the new approved contract differs, and add missing cases. The upstream suite
  remains evidence for the retained baseline, not a pass certificate for extraction or the new core.
- **Maintenance:** upstream is a source of selected fixes/components, not the canonical workflow
  evolution stream. Each copied/adapted component becomes Writhelm's update and qualification
  responsibility. Track source pin, modifications and MIT notices; Writhelm's AGPL and dependency
  distribution review remain in force.

## Comparison and rationale for the selected direction

| Dimension | Maintained fork | Own core / selective reuse |
| --- | --- | --- |
| Knowledge/approval slice | New model plus integration into inherited runtime | Same new model plus bounded runtime infrastructure |
| M2 workflow | Material existing run/claim/decision reuse; new gates across inherited routes | More workflow implementation/qualification; one directly owned authority model |
| Compatibility obligation | Maintain every inherited behavior left supported; explicitly exclude incompatible governed paths | Support approved Writhelm contracts; no accidental promise of upstream API/data compatibility |
| Tests | Existing regressions retained, new contract checks added | Selected tests/fixtures adapted and requalified; no inherited suite claim |
| Upstream updates | Broader benefit, recurring conflicts in common seams/migrations/gates | Narrower uptake; manual component tracking and fixes |
| Principal risk | Two semantic models and bypass routes need continual reconciliation | Underestimating infrastructure/workflow rebuilding or extracting the whole fork by dependency creep |

The approved direction centers on exact knowledge, bounded task inputs, evidence and enforceable
authority, not on preserving upstream's configurable workflow. Given that priority, prefer B to
avoid making upstream compatibility a permanent constraint on those transitions. The advantage is
clearer authority ownership and a bounded supported surface, **not fewer total lines or proven lower
cost**. A remains technically credible and has a stronger immediate reuse case for M2, but is not
the chosen direction. Its benefits inform component reuse rather than reopen the owner ruling.

Falsification check on the recommendation: if the complete M2 contracts can be enforced through
additive admission/decision seams, without divergent authority or broad replacement, A could be the
more economical route. Static inspection establishes such seams exist, but not full sufficiency or
comparative delivery cost. Conversely a new core importing the whole provider/composition would
lose B's stated boundary benefit. Do not claim either uncertainty resolved by the upstream test count.

## Verification state and next authorized step

- Grounded by static source/test inspection against the pinned import. Source-identity and document
  structure/link/public-boundary checks are self-checks, recorded in DESIGN-001 when executed.
- No extraction proof, comparative benchmark, implementation, Writhelm test execution or independent
  fresh-context review occurred. No model calls, expensive suites, public staging or push occurred.
- Contract O1-O9 are finite design obligations, not executed coverage; cost classification and the
  expensive-checkpoint plan require the actual selected implementation/test boundaries.
- With the foundation direction confirmed, finish the bounded M1 design:
  clause/reference/approval representation, provisioning, transaction/recovery/retry/durability and
  test seams/schedule. Present consequential decisions conversationally under D1. Only then create
  implementation tasks. New publication paths need a separate scoped approval; do not delete the
  baseline or start behavioral code through this choice alone. README's description is corrected
  locally to distinguish the own-core direction from the retained import; nothing is staged/published.
