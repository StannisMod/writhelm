# CORE-002 — Pure approval and pinned-input workflow

Task revision: 1. Status: implemented/author cheap-checked locally; independent review pending.
Primary session; no delegation. No integration or service acceptance.
Continuation authorized after CORE-001 under accepted R1/P2/T1/U1. This is I1b, not a complete service.

## Objective and inputs

Implement K-WORKFLOW revision 1: exact pending review packets, scoped current permissions,
structured owner answers, atomic in-memory approval, explicit unchanged-reference continuity,
known blockers and immutable compatible input manifests. Contract exists before tests/code.

| Retained input | SHA-256 |
| --- | --- |
| `README.md` | `3d5e14336f1276e61fea360f7f1a09b0317bbb6026fe8671a598508798c43aa2` |
| `AGENTS.md` | `e737009d14320aa0bc1d5a8006a2ebfdc3862c282504f94a436e645513f035e9` |
| `docs/contracts/knowledge-workflow.md` | `9066e545d4ecbec362c9e33f8a79b071b3c28a2efd5438e466f20e70141f52eb` |
| `docs/contracts/knowledge-content.md` | `02ca64e51e0596f39fc9455e74850c78b55a4929451e87eab146054cde2f12bc` |
| `docs/design/knowledge-model.md` | `3f071d8577e7f9f93a7a40feb29f6098d24c2f7bbffbfb27d4a215a46e5e2728` |
| `docs/design/git-state.md` | `e8873a69d84a67f83ff1b416a3dd390423b31dadcd77c45df812dcb6fdf92a20` |
| `docs/design/first-slice.md` | `e8fea3afc30cf33448ba76d22465a059b2b3bc2211cc0e3b0cea36c79d713c44` |
| `docs/specification.md` | `43edccd646cff3d679dfac6fa3a13a3601e8be43c9ae334f0221373231dc0d2a` |
| `docs/tasks/CORE-001.md` | `a1209edd2b1ecbbb668c0b60638e3389d51751a7a9d1a75a1a6507f5fd649f20` |
| `docs/publication.md` | `fbaf6058e48a4bd7039cdad500134645635389e8fa321c73a86751b6aeebcee9` |
| CORE-001 source/build manifest (sorted compact JSON bytes, without file's trailing LF) | `eba19ae9a2daab9af537ccb40e1ba2bf80cb01318a6b290377c142840e7f01b8` |

## Scope, readiness and constraints

- CORE-001 is consumed as implemented/cheap-checked, with its independent review still pending;
  the eleven source/build file hashes were checked unchanged before this task, without rerunning it.
- Write new `backend/server/src/{main,test}/kotlin/dev/writhelm/core/workflow/*.kt`; minimally extend
  knowledge reference/catalog APIs to expose typed reference/context resolution. Update this task,
  contract evidence map, bounded status docs and exact pending-publication paths. No upstream changes.
- Decide routine fields/invariants under accepted semantics. No new authority from hashes, whole-
  document approval, identity claims in answer text or automatic natural-language conflict analysis.
- No JSON wire codec, durable command-key journal, Git writes/CAS/recovery, real credentials, GUI,
  server endpoints, worker dispatch, source extraction, paid/live model calls, delegation or push.
- Run only the new core's bounded offline pure tests, including CORE-001 regression cases; same
  JDK/cache, one worker/test JVM and bounded heaps. No upstream suite or expensive integration run.
  Producer-only semantic mutation witnesses may use a disposable copy of this pure build and
  selected regression tests, under the same limits; never mutate the assessed project for a review.
- All new paths are ignored/local pending explicit publication scope. Do not stage artifacts or
  change user-wide configuration. Exact source/evidence snapshots remain outside public Git.

## Acceptance and escalation

WF-01 through WF-08 hold for finite case regions: initial/changed/mixed proposals, authorized success,
each denied capability/project, tampered/stale/ABA answers and exact replay, comment anchors,
unchanged/invalid continuity, atomic batch failure, scoped blockers, assessment/context requirements,
two historical consumers and input/cast immutability. Keep positive cases alongside refusals.

Failing transitions preserve the original state. Record source/test hashes and actual JUnit results;
do not call author tests independent review or persisted atomicity. In particular this unit does not
implement general branch authority streams, byte-different editorial mappings, finding resolution,
full mandatory-closure extraction or code-evidence qualification. Those limits stay explicit.

Escalate requirement conflicts or a need for unrelated/heavy/paid work. Output local implementation /
tests, case-to-contract map, exact evidence and the next strict record/command codec boundary before
the P2 Git publisher. No product acceptance, integration or publication is inferred.

## Result and verification

Added three workflow implementation files plus three test classes and their shared synthetic
fixture; extended only the knowledge reference/catalog interfaces needed by the workflow. The
runtime remains a pure immutable library with project-scoped grants, exact pending packets,
closed answers, all-or-nothing scope, target-generation guards, original approval/assessment traces,
explicit continuity/blockers and historical compatible manifests. No whole-document accepted flag.

The service restart occurred after source/initial tests were saved but before CORE-002 ran. Reconciled
those files and completed the missing tests without repeating prior GUI/upstream investigations.
The first new-core run passed 89 cases. Author source review then identified two concrete gaps:
different context could be supplied as a dependency, and an explicit new answer could conceal a
known blocked source behind an existing continuity target. Fixed both, added assertions/regression
and retained provenance-aware checks; no requirement was weakened to fit the implementation.

Producer mutation witness: a disposable own-core-only copy restored those two old conditions.
Only the two relevant regression methods ran, offline under the same resource limits: exactly two
failures. XML assertions showed expected UNSUPPORTED_CONTEXT_MIX / BLOCKED_INPUT versus forbidden
successful manifest / approval transitions. No compile error/unrelated crash is called sensitivity.
No assessed project source was changed for the witness.

Final corrected-source command from `backend/`, JDK 21 and the qualified cache:

```sh
bash ./gradlew --offline --no-daemon --max-workers=1 -p server test --console=plain
```

Completed in 10 seconds: ContentTest 23, RevisionCatalogTest 7, StructureTest 23,
ApprovalWorkflowTest 15, ContinuityManifestTest 16, WorkflowRecordsTest 6; total **90 cases,
zero failures/errors/skips**. Gradle stayed bounded to one worker/512 MiB in-process compiler and
tests to one 128 MiB JVM; no new dependency downloads, upstream suite or paid/model call.
These are author checks, not independent verification or durable publication evidence.

Final evidence self-check retained hashes for all eighteen own-core source/build files and six
JUnit XML reports. All eleven input entries matched the retained snapshots/canonical source-manifest
digest; normative K-WORKFLOW bytes were unchanged before the later evidence appendix. Only the two
declared knowledge reference/catalog interfaces changed from CORE-001; its other source/build files
matched their original hashes. All 1,382 tracked imported backend files still match the pinned Git
blobs/modes. Source dependency/privacy/140-column/whitespace/link checks passed; new files remain
ignored under pending P3 and no file is staged. This is author self-check, not independent review.

## Continuation and limits

Next is the strict versioned record/command codec and snapshot validation needed before the P2
Git publisher, then real client wiring/owner interaction. The in-memory actor remains a trusted
authenticated input, not a credential implementation. No command-key durable journal, whole-state
serialization, Git CAS/restart, native GUI, dispatched worker or evidence requalification is claimed.
General branch-authority streams, byte-different editorial continuity, finding resolution, deep /
large-graph resource qualification and full mandatory-closure extraction remain explicit later work.
Keep T1/U1; stronger anti-deliberate-bypass UI hardening is not reintroduced as a prerequisite.
At the implementation checkpoint all new source was local/ignored pending publication scope, with
no staging, push or integration acceptance. The owner's subsequent commit instruction authorizes
local tracking/commit of the named P2/P3 files after boundary/diff review. This is not permission to
push or a waiver/claim of independent verification; see `docs/publication.md` for the exact scope.
