# Cooperative worktree execution and immutable review

Contract K-EXECUTION, revision 2. Status: owner approved the disclosed worktree/cooperative-profile,
read-only review and separate integration scope in the practical-control amendment table. No role/
runtime/isolation implementation or qualification claimed. Governing [K-HARNESS](agent-workflow.md),
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

## Approval provenance/readiness

On 2026-11-05 owner challenged predicted file-edit allowlists; planner proposed exclusive worktrees
plus semantic diff review. Owner approved full practical-control amendment table with “Всё утверждаю”,
explicitly including E-COOP-1 without OS arbitrary-shell confinement and strong isolation as separate
profile. This approves the disclosed scope, not unshown earlier-draft details or weaker service grants/
owner keys in worker inputs. Pin actual roles/tools/credentials/runtime/result capture/recovery and
integration before implementation. No live install/model call/capability migration/source publication/
commit/push authorization follows from this record.
