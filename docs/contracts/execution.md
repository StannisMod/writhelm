# Cooperative worktree execution and immutable review

Contract K-EXECUTION, revision 6. Status: owner approved the disclosed worktree/cooperative-profile,
read-only review, separate integration and deterministic non-LLM correction scope. No operational
runtime/isolation qualification claimed. Governing [K-HARNESS](agent-workflow.md),
[K-TASK](task-contract.md), [K-DISPATCH](dispatch.md), [K-CONTEXT](context-continuity.md).

## EX-01 — Initial profile E-COOP-1, honest assurance

First H1 pilot uses separate worktrees, qualified OpenCode native permissions and semantic whole-diff
review. It does NOT promise OS confinement against arbitrary shell/subprocess escape. Stronger
OS-sandbox profiles remain separately designed/qualified capabilities, not a first-pilot prerequisite.
Service authorization is real; owner credentials never enter worker prompts/tools/assigned inputs.
Canonical data access is through authorized service interfaces, not a granted raw-store edit route.
This is explicit owner-approved assurance scope, not a claim that a worktree is a sandbox.

## EX-02 — Work area, not guessed file allowlist

Worker receives an exclusively owned worktree/working area from pinned base and may edit its files
as necessary for the task. Planner need not predict complete file list. Ordinary additional files/
reads/plan refinements don't require permission expansion; goal/behavior/interface/requirement or
authority changes still follow task/design scope. Necessary temp/build/VCS facilities belong to
runtime profile, not a semantic per-file prediction. Main checkout, other workers' areas and controller/
service config are not authorized mutation targets. E-COOP-1 states boundaries without claiming
arbitrary-shell OS prevention.

## EX-03 — Review semantic scope against complete diff

Worker explains material accompanying changes. Reviewer assesses the entire submitted diff for
necessity, unrelated edits, requirements/test preservation and scope. Another filename is not an
automatic violation; a clean file allowlist isn't conformance proof. Repo-editable config doesn't
confer service authority. Effective native config/tool behavior needs readiness pins and honest claims.

## EX-04 — Immutable reviewer inputs

Provide exact read-only result snapshot including tests and separate writable report/scratch/build
outputs. Reviewer must not edit assessed inputs or chmod them writable to fix a finding. Check
identities before/after; changed subject cannot be accepted as unchanged review. E-COOP-1 doesn't
claim hostile-same-user chmod/shell resistance. Strong enforced containment needs separate profile.
Producer under another role/session label isn't independent review.

## EX-05 — Result, live worktree and integration authority differ

Immutable R1 may be reviewed while authorized producer continues permitted R2 work in its worktree.
Old subject/checks remain exact; R1 publication doesn't release ongoing worktree ownership. Only
authorized integration changes target checkout/branch from applicable accepted result; changed base/
dirty state needs applicability/conflict handling, not blind overwrite. Acceptance doesn't merge or
grant push/publication; those follow explicit project policy. Routine authorized actions need no new
owner prompt; design/exception decisions remain owner-controlled.

## EX-06 — Native-first mechanisms and scoped recovery

Use native OpenCode/ordinary facilities first, with visible profile limits; no custom runtime/guard
by default. Track session/worktree/resources and stop/unknown/replacement under K-DISPATCH. Quarantine/
revocation isn't confirmed physical shutdown. Uncontained shared effects block conflicting reuse,
not independent work. Context discard isn't tool termination/rollback. Store-publication recovery
protections remain unchanged.

## EX-07 — Per-platform evidence

Linux/Windows/macOS remain targets with separately qualified native/VCS/path/permission behavior.
Check actual permitted operations, read-only snapshot integrity, native permissions, service denials
and diff review; do not label E-COOP-1 a proven OS sandbox. No silent Linux-only product or unavailable
mechanism claim. Cheap/model-free checks first, live/expensive qualification separately authorized.
Stronger-profile requirements apply before advertising that stronger profile.

## EX-08 — Mechanical application is not another coding agent

An authorized deterministic patch applicator may operate only within the remediation task's exact
base and exclusive work area. The service authorizes and records the operation; the native adapter
performs bounded project effects, not arbitrary raw-store edits by the service or primary. No fuzzy
matching, autonomous conflict resolution, accompanying improvements or interpretation of patch text
as executable commands. A mismatch refuses without guessing or destroying unrelated/user work.

Mechanical checks use the task's permitted execution/verification profile, not arbitrary commands
embedded in patch data. Actual resulting bytes/check outcomes form a new result. Original-effect
identity and uncertain native outcomes must be retained/reconciled before any permitted retry; no
cross-system atomicity, automatic rollback or exactly-once execution claim is inferred.

The applicator is not an LLM, semantic reviewer or independent verifier. It cannot alter assessed
immutable inputs, grant scope, accept the result, integrate into another target or publish merely
because patch application succeeded. Existing per-platform/profile and physical-effect gates apply.

## EX-09 — Phase-specific machine admission; platform verification is project tests

Owner accepted on 2026-11-16. Verification of the platform implementation belongs to this project's
development/release tests, not a model review or a six-campaign evidence registration required for
each assignment. Tests must establish advertised platform/adapter guarantees; a passed or declared
profile alone never grants service authority or proves an unsupported capability.

- **Reserve/prepare:** check current controller authority, task/dependency readiness, exact available
  mandatory inputs, resource/assignment conflicts and ability to prepare an isolated work area.
  Do not require model execution, result/review evidence or the full platform qualification bundle.
- **Bind:** check the actual created native session/workspace, canonical task/execution identity,
  scoped delegation/credentials and binding consistency. Binding does not authorize execution.
- **Start/resume:** recheck current authority and exact inputs, actual deployment/model/route
  configuration, applied role/tool restrictions, stop state and the original operation's outcome.
  No additional AI verifier. Unknown prior effects and unconfirmed conflicting writers remain held.
- **Result acceptance:** exact artifacts/diff, required actual project checks and independent
  substantive review/remediation apply here. Integration requires its own applicable authority.

Runtime checks remain machine checks of the current operation. Platform tests do not run anew for
every assignment or normal restart. Restart revalidates current process/session identity, authority,
bindings and retained obligations; it does not require recollecting six qualification campaigns.
Unsupported operations fail explicitly at their relevant boundary (for example, unavailable resume),
not as a blanket denial of independent supported preparation/work.

Replace the shared qualifiedProfiles prerequisite across reserve/bind/start/resume with these
phase-specific checks. Do not implement this by returning qualified=true or weakening reviewer
permissions, exact-input validation, current authority, unknown-outcome or recovery protections.
This supersedes contrary per-assignment full-campaign requirements/proposals in H1 task records,
including QC-EXACT-01 as the proposed next implementation step. It does not authorize live calls,
paid experiments, abnormal faults, integration or publication.

Implementation status: the local source now separates passive preparation/binding from exact
current-operation execution admission, active native access and retention. Execution observations
are tied to the current head, command fingerprint, binding and profile; native read-only preflight
checks deployment/model/session/permissions without waking a model or changing permissions.
The initial-only Linux path refuses resume without the separate original cooperative-pause witness
and same-binding continuation checks described in EX-11. Unknown prior effects, new-process adoption
and unconfirmed writers remain held; cooperative pause never supplies stop/claim-release fencing.
Project tests, not historical campaign registration, verify these mechanisms. This implementation
record does not claim installed H1 acceptance, semantic independent review, OS confinement or
Windows/macOS runtime verification.

## EX-10 — Native lifecycle and the minimum H1 journey

Owner clarified on 2026-11-16: coding workers retain bash/shell. Do not replace lifecycle work with
a shell ban, or make full containerization/arbitrary-descendant containment a prerequisite for H1.
Use OpenCode's actual native command/session outcomes and cancellation facilities within E-COOP-1.
UI visibility alone is not an outcome observation; unknown or ambiguous native effects stay held.
Do not advertise control of unobserved escaped processes or claim that interrupt proves OS fencing.

The first parallel task/result/candidate/check/review/remediation journey need not pause and resume
the same unfinished worker. Keep a running worker's base/worktree unchanged; dependent/remediation
work may use separate tasks and exclusive worktrees. Do not reuse an uncertain old work area or
release conflicting claims merely because another task can proceed. Full pause/resume continuation
remains a separately verified supported operation, not a blanket admission gate for that first journey.
This clarification withdraws the proposed shell-restricted pilot profile; it was not implemented.
It changes neither current service authority nor exact-input/review/integration requirements and
does not authorize inference, paid runs, integration or publication.

## EX-11 — Cooperative pause is not writer fencing

Owner explicitly approved separating cooperative pause from fencing. A cooperative pause retains
claims, the exact worktree/base and binding. It permits only same-binding continuation from the
exact checkpoint after current authority, mandatory restoration and native admission are checked.
It does not authorize conflicting writers, work-area reuse, claim release, stop/isolation facts or
base replacement. Unknown outcomes retain their original operation IDs and remain held.
Native cancellation acknowledgement/idle alone is not a writer-fencing observation. Stop and any
operation requiring fenced writers retain that requirement. Approval of this design is separate
from implementation/verification, live inference authorization and integration acceptance.

## Approval provenance/readiness

On 2026-11-05 owner challenged predicted file-edit allowlists; planner proposed exclusive worktrees
plus semantic diff review. Owner approved full practical-control amendment table with “Всё утверждаю”,
explicitly including E-COOP-1 without OS arbitrary-shell confinement and strong isolation as separate
profile. This approves the disclosed scope, not unshown earlier-draft details or weaker service grants/
owner keys in worker inputs. Pin actual roles/tools/credentials/runtime/result capture/recovery and
integration before implementation. No live install/model call/capability migration/source publication/
commit/push authorization follows from this record.

Revision 3 records the subsequent owner-approved non-LLM correction route described in TASK-08.
It preserves E-COOP-1's stated assurance and does not qualify a new native process/filesystem adapter
or supply missing protocol, authorship, permission or recovery formats.

Revision 4 records the owner's 2026-11-16 approval of phase-specific machine admission and explicit
clarification that platform implementation verification means tests of this project. This is an
accepted design change. The implementation status above is separate from operational acceptance.

Revision 5 records the owner's native-lifecycle/cooperative-scope clarification and instruction to
record it and continue the minimum H1 journey, without a bash ban or containerization requirement.
