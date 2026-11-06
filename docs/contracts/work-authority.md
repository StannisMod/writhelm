# Bounded H1 work authority and delegated execution identities

Contract K-WORK-AUTH, revision 3. Status: original six-point profile, noncascading dispatch revocation
and bounded own-controller continuation agreed by owner; no implementation/credential issuance
claimed. Extends K-AUTH's initial
bootstrap-only knowledge-client profile for H1 through explicit versioned design, not reinterpretation
of its existing enums/records. Governing [K-HARNESS](agent-workflow.md), [K-TASK](task-contract.md),
[K-DISPATCH](dispatch.md), [K-EXECUTION](execution.md), [K-EVIDENCE](evidence-integration.md).

## WA-01 — Explicit planner work permissions, no routine owner ceremony

Owner grants project work operations separately: task planning, dispatch, exact-result acceptance
and target integration. They are not implied by old knowledge PLAN/READ or session/role labels.
Normal H1 planner profile receives all four once at project setup; they may be selectively withheld/
revoked for other profiles. No separate key/process/agent per permission, no four human confirmations
or new owner prompt per ordinary task/launch/result. Same planner may accept and integrate if granted.

Planning creates/refines tasks; dispatch assigns/starts ready work; acceptance decides exact result
conformance under required checks; integration changes authorized target from applicable accepted
result. Work acceptance is NOT owner approval of new requirements/design or authority administration.
APPROVE/MANAGE/owner identity and project publication permissions stay separately explicit.

## WA-02 — Bounded delegated execution credentials

Authorized native controller generates distinct credentials/attributed execution identities constrained
to project/instance/task/role/generation. It can register only that bounded delegation, not arbitrary
project-wide grants/another owner. Client/controller retains secret; canonical state stores verifier/
bindings only. Owner key never enters worker/model inputs. Exact issuance authorization/credential
delivery/format/profile must be pinned before implementation, not put in tool argument or actor field.

## WA-03 — Worker and reviewer scope

Executor reads assigned contract/applicable inputs, writes own progress/memory/retrospective/result
and requests clarification. No other-task/grant/norm-approval changes or selfacceptance. Worktree
edits follow E-COOP-1, not raw canonical-record patching. Reviewer reads assigned exact result/task/
evidence and writes report, cannot change subject or appoint itself independent. Record actual
producer/reviewer execution provenance; different names/model/provider alone aren't independence.
Planner checks reviewer report; no automatic recursive reviewer for each report.

## WA-04 — Integration authority need not be another agent

Integration permission may belong to the planner. No compulsory separate integrator process for
routine changes. Target project/branch/result scope applies; acceptance doesn't authorize push or
waive checks. Independent implementation review remains non-author under verification plan; only
owner can grant explicit scoped exception under existing authority/recording rules.

## WA-05 — Completion/rebind closes new work, preserves attributable reconciliation

Completed/replaced generation cannot start new work, widen scope or submit a fresh authoritative
replacement result. Preserve permitted same-key outcome reconciliation/own retained-result reads
under current policy, not new business effects or revived old broad rights. Retain historical actor/
credential provenance. Lost reply isn't a new identity/operation. Exact postcompletion/revocation/
replay conditions require explicit schema/policy/test readiness, not assumed infinite active access.

## WA-06 — Current scoped checks and explicit evolution

Every exposed route/tool checks actual authenticated policy, not trusted body identity or session
cache. No generic owner forwarding/reset/unrestricted registration endpoint. Work authority doesn't
weaken knowledge/current-read rules. Pin versioned auth/command/fingerprint/journal/namespace and
legacy compatibility/refusal/migration before source changes; no extra canonical authority database.
Service API rights enforced; E-COOP-1 still doesn't promise arbitrary-shell OS isolation.

## WA-07 — Dispatch revocation is not delegation revocation

Removing a controller's TASK_DISPATCH blocks future dispatch, delegation registration and
controller-managed delegated credential rotation/revocation. It does not itself revoke separately
registered bounded delegations. Each delegated request still checks its own current credential,
delegation, exact assignment/role/execution generation and applicable current knowledge permissions.
Explicit credential/delegation revocation and work-generation closure/replacement remain effective;
restoring the controller's dispatch permission never revives a revoked scope or closed generation.
The fixed owner with current MANAGE may revoke a delegation after the controller loses dispatch.

Neither permission removal nor canonical delegation revocation proves physical native tool/process
termination or releases resource claims. Stop/fencing/recovery still follows K-DISPATCH and K-RUN.

## WA-08 — Planner's own cross-task continuation

A primary planner with current TASK_PLAN and an authenticated matching controller binding may
persist its own bounded continuation across tasks: exact task/assignment frontier, remaining
obligations, sourced residual working knowledge, pending decisions/events/unfinished operations,
gaps and source/profile identities. It is not a fabricated executor assignment or a copy of the
whole conversation. Exact task/norm/input obligations restore separately from their pinned sources.

This operation writes only that controller's continuation and directly referenced missing bodies
in the same private canonical root. It cannot mutate another controller's continuation, tasks,
assignments, grants, approvals, acceptance, integration or execution rights. No new capability,
implicit dispatch/owner authority, shared worker credential or promotion of old knowledge PLAN.
Protected disclosure still requires current READ and the matching controller scope. Restoring a
checkpoint grants no authority; current checks and source applicability remain mandatory.

## Approval provenance/readiness

On 2026-11-05 owner requested explanation of point1. Planner clarified four independently grantable
service operations, ordinary default grant once, no separate keys/processes or repeated owner
confirmation, and no promotion of knowledge PLAN into orchestration/owner authority. Owner replied
“согласовано”. This records the disclosed work profile with that clarification, not passing native/
delegation behavior, arbitrary grants or undisclosed schemas. No live credential/model, expensive
checkpoint, host install, source publication or commit/push permission follows.

Revision 2 records owner approval of WA-07 after being asked explicitly whether TASK_DISPATCH removal should
revoke existing assignments. The disclosed recommendation was no implicit cascading revocation:
deny future dispatch and revoke existing delegations/generations explicitly. Owner replied “ок”.
This accepts that authority distinction, not implementation, native stop or resource-release evidence.

Revision 3 records owner approval of WA-08. The disclosed proposal was to let the primary planner
use existing TASK_PLAN to save only its own controller checkpoint across tasks, including remaining
obligations, awaited decisions and unfinished operations, without new assignment/acceptance rights,
other-controller writes or a whole-chat export. Owner replied “да”. This accepts the bounded
authority scope, not implementation, extraction completeness, native qualification or publication.
