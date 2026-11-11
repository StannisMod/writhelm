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

The owner's minimum end-to-end acceptance journey is specified in [H1 owner acceptance](acceptance.md#h1-owner-acceptance--minimum-vertical-journey)
and [K-HARNESS H1-09](contracts/agent-workflow.md#h1-09--owner-directed-minimum-vertical-journey).
It requires autonomous dependency-limited parallel worktree execution, planner factual control and
candidate assembly, passing tests, then independent contract-based review. This is an acceptance
requirement, not evidence that the current implementation already performs that journey.

The approved connected-work amendment assigns blocker resolution, consistent environment preparation,
minimal sufficient notification and eligible continuation to the engine. Exact identities remain
technical evidence, not repeated chat payloads. The bounded portable implementation has completed
independent review/scoped parent acceptance and is included in the source tree. This is not an
operational OpenCode service or actual native/physical writer qualification.

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

### H1 automatic dispatcher design — approved, implementation pending

Status: **owner-approved, partially implemented and not qualified**. Current
prepare/start support controller-channel entry and restoration from retained original driver bytes.
The planned preparation path first publishes its retained exact reservation binding through a
controller-only channel, and holds on unknown binding outcomes. An isolated Linux JVM/HTTP/Node/Git
component test exercises that path through worktree, binding and a mocked initial prompt, including
lost native replies and cold Git reopening; no live model or installed host is qualified. An opt-in
frontier invokes this path, but its production composition and end-to-end readiness remain unqualified;
task revisions alone do not encode a unique manifest/resource selection. Under an explicitly selected
internal model4 profile, a plan command is authenticated and published in Git with a receipt; an
isolated Linux service test covers replay, conflicting reuse and cold reopening. The default production
composition remains model2.
The plan pins one immutable task revision/manifest and one attempt ID pair per entry. Plan format2
can additionally authorize selecting the unique exact result of a producer at `implemented` readiness;
zero results holds and multiple results are ambiguous. A pure materializer can construct an exact new
task revision, verification plan and manifest commands; the manifest explicitly retains a new closure
gap and is not admitted for dispatch until independently assessed. A private, current-authorized
controller read can build an unpublished same-head proposal; it is not an atomic publication and
must not be automatically submitted without rechecking result uniqueness at the publication head.
A pure work-graph transition now recomputes the selection and combines the task, verification plan
and manifest into one unpublished effect, preserving the closure gap. A candidate command codec
retains exact input identities and excludes any caller-selected result; an immutable model4 graph
record retains those inputs for historical audit. In the explicit internal model4 profile the service
checks current plan and dispatch grants, recomputes the result at the publication head, and publishes
all four records together. The H-to-C audit checks the original-head selection, fingerprint, author
and exact Git write set. An isolated Linux Git test covers replay, later ambiguity and cold audit;
it does not qualify the dispatcher, packet closure or production configuration.
A current-authorized internal read
can distinguish a durable exact reservation refusal from missing, successful or mismatched receipts;
the private controller channel can check the protected retained original. A model4 work-graph record
now binds an initial attempt to an exact reservation and rejects mismatched plan selections and
reused identities. The explicitly selected model4 service publishes that binding only when the original
reservation receipt is absent at the same H; the original-H audit repeats this check. A local Linux Git
test covers authorization, late-binding refusal and recovery. A versioned successor binding can now be
published only after the exact predecessor's canonical, fingerprint-matched rejection; no receipt or
success holds, and the graph excludes sibling successors. The controller projection exposes retained
attempts for recovery. The opt-in frontier now retains successor IDs after checking the exact original
and a definitive refusal. An isolated Linux JVM/HTTP/Node/Git/worktree component test covers a
synthetic packet refusal, lost successor-publication replies and recovery by a new frontier instance;
the OpenCode SDK and initial start are mocked, and the test does not establish installed-host or
cross-platform qualification. The opt-in frontier now retains and sends the atomic materialization
command when the current projection reports a unique producer result; an uncertain reply reuses the
same command, and the service remains the final same-head arbiter. This integration has model-free
mock coverage, not a dependent-task end-to-end test. A terminal refused materialization currently
remains held rather than creating another command. Verified packet/closure publication and dispatch
of the resulting revision are not connected to the frontier.
An isolated Linux Git-service test submits three reservations concurrently with synthetic readiness:
the independent resource succeeds, a competing stale generation is rejected, and an updated-generation
request for the occupied claim is refused. Replaying the original requests after cold reopen creates
no new assignments. This verifies the service's claim arbitration, not concurrent native worktrees or
end-to-end frontier scheduling. A separate Linux frontier component test overlaps preparation of two
independent implementation tasks in distinct real Git worktrees, recovers one synthetic lost SDK
reply and restarts the frontier without creating duplicate worktrees or receivers. The SDK, readiness
facts and initial starts are synthetic. A conflicting-claim variant confirms that the losing initial
reservation has a definitive refusal and remains held across repeated scans and a frontier restart,
without creating further attempts, worktrees or receivers while the claim remains occupied. The
claim-availability projection is only a scheduling hint; service reservation remains authoritative.
An isolated service/Git test also checks that a worker cannot submit a claimed diff without matching
`WorkSubmittedDiff` observations, that the planner can read the exact retained result and report after
an explicitly synthetic observation, and that submission does not imply acceptance or integration.
No production provider of `WorkSubmittedDiff` is installed; this is not real Git-diff qualification,
planner factual review, candidate assembly or end-to-end H1 evidence.
An isolated Linux read-only witness can observe the exact Git-generated diff between an ancestor
base and a clean result worktree, rechecking commit/tree and worktree bytes around the observation.
It is not connected to `WorkSubmittedDiff`, protected report transport or planner integration and is
not writer fencing; no Windows/macOS or installed-host qualification is implied.
An uninstalled service-side proof adapter now accepts only a trusted exact-diff proof bound to the
current head, command, active assignment, execution, base, result and diff digest/length. A synthetic
provider test exercises the existing result reducer and rejects mismatched proofs; the Linux witness
is not yet connected to this adapter and an arbitrary textual change summary is not such a proof.
The adapter also requires an independently established retained result snapshot: merely observing a
commit in a worktree does not establish that its locator will survive recovery.
An isolated Linux component test now feeds actual `LinuxGitWitness` diff bytes from a real Git
worktree into that service adapter via an explicitly test-only provider. It checks missing retention,
dirty-worktree refusal and planner reading the exact retained result body, then independently
recomputing its diff digest using the base and artifact recovered from service records. The retention fact is
synthetic, the native production bridge is not connected, and no model was invoked.
An additional isolated Linux component connects the protected native diff-observation transport to
the service-side adapter, submits a worker result over its scoped channel and reads the exact diff as
planner. The original version supplied synthetic retention, mocked the OpenCode SDK and did not qualify
production composition, native writer fencing, crash durability or Windows/macOS.
The diff-transport fixture now additionally exercises actual Git ref creation and protected retention
proof through the service facts adapter, instead of inserting the result snapshot directly into the
fixture facts. Before the effect it now uses an assignment-scoped current service permit applying the
existing submit-result authority and active-binding checks; wrong execution, repository and controller
scope are refused. It reads the result after service reopen. Storage qualification remains synthetic
and the effect coordinator is not installed in normal production composition; this does not qualify
abnormal recovery or durability.
The native runtime's configured passive profile now exposes the read-only retained-proof route over
the protected transport. Its effect callbacks deny unconditionally: this neither creates refs nor
installs a service qualification or authorizes production retention. Installed-host behavior remains
unqualified.
The local service composition now includes the retention and exact-diff observation adapters, but
the retention qualification is explicitly always false: no production retention fact follows from
that wiring. Result-ref creation, retention qualification, and crash recovery remain uninstalled or
unqualified. This is fail-closed composition, not a working end-to-end result path.
Worker result-submission integration also needs a replay-safe original-command check before any
retention preflight. The existing authorization explicitly admits original `submit_result` replay
in `REPLAY_ONLY` after the assignment stops being active, while a new retention effect requires
current active authority. Unconditionally running retention first would block that valid replay;
dispatching the command first when no receipt exists would instead publish a premature rejection.
Neither ordering is a safe substitute for an authenticated exact-original receipt decision at the
current published head. A bounded private read checks current assignment authority and the
fingerprint/scope of an exact original `submit_result` receipt. The worker tool now retains the
original command before preflight: MATCH follows ordinary canonical replay without another Git effect;
MISSING is only a current-head observation and may proceed to independently authorized retention
only for a newly retained original; stale custody, conflicting or unprovable receipts HOLD. Missing
never establishes absence of an unknown external effect. The private receipt read now has a dedicated
bounded ingress supporting the existing 2 MiB worker-command limit, including JSON-escaping overhead;
other native requests retain their smaller ingress limit. The production effect-qualification gate remains closed, and this
does not qualify installed-host operation, concurrency/fencing or unknown-effect recovery.
An isolated Linux JVM/HTTP/Node/Git component fixture now invokes that same runtime preflight through
the registered worker tool: it creates an actual result ref, submits and canonically replays an escaped
command larger than 8192 bytes, refuses changed bytes, and holds an originally retained command after a preflight failure with no receipt. The
OpenCode SDK and effect/storage qualification remain explicit fixtures; no model, actual process-fault
recovery, installed host, or production qualification was exercised.
The same service/native-tool fixture also exercises the effect-qualification gate set to false:
an active worker's exact result is held twice without creating a Git ref or service result, including
after service reopening. This is a negative component check, not production qualification.
The owner approved development and implementation of the disclosed result-retention direction: a
trusted adapter pins the exact result commit with a separate ref in the coding repository, binds it
to the locator and assignment, and supplies retention facts only after checking that binding. Unknown
or partial effects reconcile the original operation; absent or mismatched evidence holds the result.
This approval does not itself establish an implemented protocol, durable storage, fencing or pilot
readiness. The implementation must preserve K-RUN recovery and platform qualification and must not
use operational custody as a second canonical work store. Its exact synchronization, authority and
recovery boundaries still require implementation and verification; no model or pilot call is approved.
The current production composition does not install `ReservationProofControl`; moreover the existing
full assignment packet requires an already-bound assignment, whereas reservation requires a verified
packet and an empty `knownGaps`. The materialization gap therefore cannot be removed or treated as a
verified packet merely by the dispatcher. The owner has approved a two-stage approach: independently
check the selected task inputs before reservation, then form and check the full assignment packet after
binding. Existing authorization, completeness, claims and start checks are not weakened. A negative
fixture verifies that even an optimistic synthetic packet fact does not bypass a manifest closure gap.
The first stage now has a read-only structural inventory of a materialized dependency: it checks the
recorded materialization, selected results, mandatory inclusion, available referenced bodies and
declared source observations. It neither clears the gap nor issues a packet/closure proof; semantic
assessment, same-head authenticated publication and post-binding packet checks remain to be connected.
The focused graph test uses synthetic observations and confirms reservation remains blocked.
The explicitly selected model4 reducer now distinguishes a trusted, current-head and exact-command
prebinding input proof from the full assignment packet: it can use that proof only for reservation,
never for later start/rebind checks, and never bypasses a known closure gap. The proof-provider seam
now has an independent structural verifier that checks declared applicability and criterion sources,
artifact/diff observations and exact source bodies before requesting a separate same-head assessment.
The independent-assessment authority is still an uninstalled port, and focused tests use synthetic
attestations. They verify missing mandatory inputs, head/fingerprint mismatch and the held full-packet
gate. A genuine independent assessment, its authorized publication, the native provider and
dependent-task launch are not yet implemented.
An unpublished successor-manifest proposal can now be constructed from an independently supplied
assessment bound to the captured head and the exact materialized inventory. The old manifest stays
unchanged. A pure graph fixture demonstrates proposal, reservation with synthetic trusted facts, and
continued refusal without the full post-binding packet; it does not publish or establish an assessment.
A candidate closure command now binds the materialization, assessment source, successor manifest and
expected published head in a versioned fingerprint. Its unpublished reducer refuses a stale head or
wrong controller and reconstructs the successor from the assessment. The model4 service now registers
the command only as a fail-closed hold: it records a rejection receipt for a missing qualified assessor
or stale head, never a successful closure. It does not retain qualification provenance or qualify the
assessor. The historical boundary refuses every successful closure until that provenance is auditable.
The unpublished work effect now also includes an immutable model4 closure-command record. A graph
check binds it to the materialization and successor manifest, and a separate historical edge helper
checks the expected head and exact graph changes. The record also retains the asserted assessor and
delegation; the historical helper compares these against original-H receipt attribution and refuses
missing or mismatched subjects. This does not qualify the assessment itself, and the service still
forbids a successful closure pending durable qualification provenance and its audit.
Candidate current-H and historical authority checks now derive the controller from the retained
materialization plan and require both task-plan and task-dispatch grants. These checks are not yet
connected to a closure publisher or evidence of an independent assessment.
An attribution helper resolves an exact evidence record through the receipt-derived H1
index and checks its declared task, manifest and dependency subjects and the recorded principal.
A Linux Git-backed component fixture exercises that receipt path using an explicitly synthetic report;
neither attribution nor a passing label qualifies the report as an independent closure assessment.
An unpublished candidate composes this attribution with same-head inventory and an uninstalled trusted
qualification port. The service deliberately does not install that port or publish its candidate.
A model-free Linux Git-backed fixture now creates a separate test-assessor assignment and evidence
receipt, then forms an unpublished closure candidate with a synthetic qualification callback and
checks its original-H attribution. Forged authorship and mismatched qualification are refused; the
real service still holds the operation. This is component evidence, not a qualified assessor, a
successful closure publication, dependent native launch or H1 acceptance.
The candidate and historical attribution checks also refuse evidence whose recorded outcome is not
`passed`, before invoking the qualification port. A `passed` label remains necessary but never
sufficient evidence that the declared input closure has been independently assessed.
For model4 materialized revisions, the ordinary manifest-pin operation now refuses an empty-gap
manifest; only the dedicated closure reducer may construct that successor. Historical validation
likewise rejects such a pin on an H that already contains the materialization. A Git-backed fixture
confirms the attempted bypass is rejected without work writes; this does not enable closure.
The owner also approved a separate authorized candidate-assembly operation: combine selected results
on a candidate branch, retaining the original effect identity and resulting commit/tree, preserving
unrelated work and holding conflicts/unknown outcomes. This is neither acceptance nor publication.
The owner clarified that the harness should launch applicable candidate tests automatically as CI
jobs, with safe reruns rather than a separate user procedure. H1-09 and its acceptance scenario now
make that expected behavior explicit. No candidate test runner or automatic post-assembly test job
is implemented by this clarification; actual side effects and unknown outcomes still obey K-RUN.
An initial read-only candidate-input selector checks explicitly selected results against retained
snapshot and exact-diff observations and rejects duplicates, missing bodies and mismatched repositories.
Its fixture uses synthetic observations; no service-authorized assembly, Git mutation, semantic
planner assessment or candidate test execution is implied by this helper.
The owner authorized model-free process-fault tests limited to processes created for those tests.
These are decisions, not implementation, qualification, live model or pilot evidence.
An isolated Linux retention component now kills its own test-created Node child after the Git ref
write but before its local acknowledgement. Reopening custody leaves the exact original outcome
unknown and refuses another write; the extant ref does not fabricate a receipt. Authority and worktree
observations in that fixture are mocked. This is not K-RUN old-writer fencing, service-process crash,
filesystem durability, installed-host qualification or full H1 recovery.
Existing model2 task/receipt bytes must not be silently reinterpreted.

Approved minimum design:

1. The planner submits one bounded, versioned dispatch plan through an authenticated service operation.
   It identifies each exact task revision, intended executor role/profile, permitted manifest/base,
   explicit resource/claim policy and dependency/result-selection rule. The accepted plan is canonical
   and recoverable; native custody retains operational IDs but is not a second authority store. No
   automatic dispatch follows an incomplete plan or an unapproved design. Existing assignments and
   claims remain in the same canonical graph and continue to participate in exclusion.
2. An engine-owned controller channel—not a fabricated model tool call—rechecks current authority,
   task readiness, exact applicable dependency results, claims and capacity. It schedules compatible
   ready tasks concurrently, attempts newly eligible work without a batch barrier, and reports holds
   per task. The service's existing atomic reservation is the final arbiter; a stale/conflicting claim
   is not bypassed. Ambiguous result selection holds the affected task rather than guessing.
3. Reuse the existing passive preparation and initial-start mechanisms behind that channel. Persist
   each original plan/task/attempt and exact prepare/start command identities before external effects;
   recovery looks up the original receipt and native outcome, never launches a new ID merely because
   a reply was lost. Unknown active work retains its affected capacity/claims until qualified recovery;
   independent work may continue. Restart restores canonical frontier and protected originals, not a
   fresh empty queue. No claim of exactly-once execution or rollback is inferred.
4. The first implementation includes model-free tests of overlapping independent tasks, immediate
   dependent readiness, claim conflict/capacity holds, revoked authority, loss/restart and no duplicate
   wake, plus the real service/native integration needed to drive those paths. It does not replace
   the required profile/packet/safe-access gates, create a new LLM runtime, perform a live model call,
   claim cross-platform qualification, or mark worker output independently reviewed or accepted.

Alternative considered: drive the existing two tools with synthetic tool-call IDs from a background
loop. It would misrepresent the actual caller and make replay/cancellation depend on a fabricated
conversation context; it is not an acceptable authority or recovery mechanism. A transient-only queue
would lose the authorized plan across restart. Exact record/operation schema and version selection
must be specified compatibly before implementation; this proposal does not itself approve new bytes.

1. Ratify one knowledge revision; read it from two clients; prove failed publication leaves the accepted revision unchanged.
2. Dispatch one bounded implementation task; independently verify the current code tree; accept the result with linked evidence.
3. Interrupt and resume that workflow in a second harness without importing the whole conversation.

## Out of initial scope

A new harness or model runtime, universal retrieval, automated ingestion of private chat histories,
enterprise multi-tenancy, a large UI and support for every harness at once.
