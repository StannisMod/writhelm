# Canonical assignments and native-first execution control

Contract K-DISPATCH, revision 4. Status: original protocol/native-first decisions, practical-control and connected-work coordination
amendments approved by owner; no implementation/qualification implied. Governing [K-HARNESS](agent-workflow.md),
[K-TASK](task-contract.md), existing authority/publication/T1/cross-platform laws remain unchanged.
Draft revision 1's prescribed per-model-call boundary approach is superseded by the qualified
native-first approach, not by weaker task, stop/revocation or context guarantees.

## DP-01 — Canonical assignment and claims

Authorized planner requests exact executable task/snapshot/mandatory packet/profile/workspace.
Service verifies readiness/current rights and prevents duplicate active executors of one logical
task, acquiring the required exclusive task/workspace/incompatible-resource claims atomically with
assignment/intent. Independent compatible tasks may execute concurrently; no whole-project lock
merely by association. Resource vocabulary/compatibility is explicit, not semantic conflict inference.

## DP-02 — Reserve, bind, then start

Plugin creates passive native session/workspace for recorded intent, binds exact native identities
through service validation, and starts work only after confirmed active binding. Unbound/orphan/
ambiguous sessions have no execution right. Preparation alone cannot authorize an unapproved task.

## DP-03 — Reconcile lost external outcomes

Durable current-authorized retries recover same assignment/intent; changed semantics conflict.
OpenCode create/start effects are not atomic with service Git. Lost reply requires reconciliation
before another create/start; unresolved outcome blocks conflicting launch, never blind duplicate
execution. Selected native profile must support safe binding/discovery, not title-only authority or
an exactly-once model-execution promise.

Recovery has explicit outcomes, not a permanent generic error: reconcile and bind an identified
existing session; establish that start did not occur; or revoke old execution right and isolate/
quarantine its work area/resources before replacement. If scope/effects cannot be isolated safely,
retain affected claims and expose operator recovery. Record decision/basis/remaining uncertainty.
Quarantined/obsolete execution is not physically stopped; unknown blocks conflicting actions, not
unrelated work. Passive orphan without active execution right differs from uncertain started worker.
These paths don't clear canonical-store publication barriers or override K-RUN fencing requirements.

## DP-04 — Execution generations, native mechanisms first

Execution right binds assignment/task/packet/profile/workspace and monotonic generation. Replaced
generation cannot resume or submit an authoritative result for its replacement. Current scoped
identity, not labels/reported status, governs service actions. Worker gets no owner/store access or
self-assignment right. Lost service contact does not permit new actions needing current authority.

Use qualified OpenCode lifecycle, permissions and session mechanisms first. This contract does NOT
prescribe a bespoke interceptor or service round trip for every LLM call. Pin actual continuation/
safe boundaries and demonstrate pause/revocation/stale-generation behavior there. Add a guard only
for an evidenced native gap; no prompt-only substitute or silent weakening of required guarantees.
Unmanaged sessions outside this profile are not advertised as Writhelm-controlled.

## DP-05 — Pause/stop are confirmed protocols

Use qualified native interruption to prevent subsequent execution steps and establish completion
or reliable fencing of current work. Already-running tool/response is not rolled back. Until proved,
report stopping/unknown, not definitely stopped/resources free. Timeout, heartbeat expiry or parent
death alone never permits conflicting reassignment while old writers may act. Exact per-tool/native/
process boundaries require the execution profile, not assumed custom LLM hooks or kill-by-PID logic.

## DP-06 — Resume/rebind preserve subjects

Resume after checking current rights/inputs/checkpoint/native session/work area. Changed conditions
require explicit safe rebind to new revision/packet/generation. Old generation stays fenced; old
progress/evidence retain original subjects. Replace lost session only after former execution is
known stopped/fenced, or its execution right is demonstrably revoked and resources isolated from
replacement. Uncontained shared/external effects still block conflicting execution.
Ordinary norm update doesn't force rebind; withdrawal blocks affected use at
the qualified native boundary.

## DP-07 — Completion is distinct from acceptance

Worker report doesn't freeze assessed artifacts or release write claims. Adapter establishes
execution completion/fencing and exact result before conflicting reuse. Stopped, produced, reviewed,
accepted and integrated remain distinct facts. Native idle/DONE alone is insufficient if background
writers may remain. Unknown ownership blocks conflicting execution, not independent work.

Immutable R1, mutable worktree and execution state are separate objects. Once a consistent immutable
R1 snapshot is identified, reviewer can assess it while authorized worker continues permitted work/
prepares R2 in its owned worktree. R1 cannot change; new changes/checks aren't silently attributed
to it. No need to stop the entire worker solely to review R1. Continuing work doesn't release/reassign
write claims; conflicting reuse still requires completion/fencing/isolation.

## DP-08 — Visible in-harness control

Show tasks/assignments/sessions/profiles, progress, blockers/uncertainty and pause/resume/rebind in
OpenCode. No silent duplicate workers/rollback claims. Verify lost/duplicate dispatch, exclusive
claims, stale generations/current rights/withdrawals, connectivity loss, interruption/resume/result
identity using actual selected native mechanisms. No live model call without separate authorization.

## DP-09 — Engine-mediated connected-work continuation

Track blockers, their responsible work, agreed resolution conditions and required confirmation.
After confirmed resolution, recheck current authority/bindings and remaining continuation conditions,
prepare a consistent authorized work environment, notify the affected executor and resume eligible
work through qualified native mechanisms. The planner need not manually copy changes, relay each
dependency update or repeat an already authorized instruction to continue.

Preserve actual user/worker changes, exact result/base identities, claims and external-effect recovery.
Preparation is not a blind merge or silent base substitution; applicable explicit rebind rules remain.
Conflicting or ambiguous preparation blocks affected continuation with a visible reason, not a guessed
resolution or a project-wide stop. Lost/repeated notifications and restart must reconcile delivery and
continuation without duplicate execution or repeated external effects. A notification grants no rights.

Qualify a connected H1 scenario: one executor is blocked by a shared mechanism, another repairs it,
the required check confirms resolution, and the first continues in a consistent environment without
planner-mediated diff transfer or a repeated assignment. Include lost/duplicate notification, restart
and conflicting work changes. These are required checks, not claimed passing cases.

## Context/compaction boundary

Context budgets and compaction have their own contract/profile. First configure native OpenCode
compaction with suitable reserve and qualify what it counts and when it acts for the chosen model/
route. If sufficient, reuse directly: no duplicate counter/summarizer or outgoing-call interception.
Additional protection needs a demonstrated gap, not presumed necessity. Distinguish compaction
trigger from proven hard ceiling; no silent downgrade to a soft target. Automated accounting does
not imply human approval or canonical Git record of every model call.

## Readiness and approval provenance

Concrete schemas, task-role grants/credential delegation, namespaces/journal compatibility, mandatory
context/run profiles and workspace enforcement remain explicit prerequisites. No unknown API shapes,
capability changes, expensive checkpoints, live-model usage or blanket dependent implementation is
approved here. T1 does not waive worker/reviewer restrictions; each target OS/profile needs evidence.

On 2026-11-05 owner accepted native-first correction after questioning bespoke per-request controls,
requested rewritten contract, and explicitly approved the eight-point K-DISPATCH v2 plus context/
compaction boundary with “утвердить”. This manual decision records that scope, not passing execution,
a fabricated service event, source publication, commit/push or live-call permission.

Revision 3 records owner approval on 2026-11-05 of scoped recovery and separation of immutable
results/live workspaces/execution. No canonical-store recovery relaxation, unverified stop, duplicate
active authority, weaker evidence applicability or new live/host checkpoint permission follows.

Revision 4 records owner approval with “Согласовано” of engine-mediated connected-work continuation
and its H1 checks. This is a requirement, not implemented synchronization or qualified native delivery.
