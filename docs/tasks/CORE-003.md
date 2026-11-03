# CORE-003 — Core codec readiness and bounded implementation

Task revision: 5. Status: pure implementation delivered/author-tested; parent source/evidence checked.
Independent fresh-context review and service integration remain pending; no durable service acceptance.
Executor: one native harness child, explicitly authorized by owner; parent handles auth design and
checks the returned evidence. Scope derives from the accepted K-CODEC revision 2 and
K-PUBLISH revision 1; design approval is not implemented/verified/integrated status.

## Objective and phases

1. Freeze a machine-checkable core record/command field schema, synthetic independent goldens,
   concrete development admission/read/restore bounds and JSON dependency qualification.
2. After those exact inputs are complete and pinned, implement the strict pure codec/restorer with
   contract-derived offline tests. No Git publisher or network/client/credential implementation.

Phase 1 is complete to the bounded observations below. Phase 2 was executed under revision 4's exact
inputs/write scope; its actual result and parent inspection are recorded separately from readiness.

## Inputs and scope

- Accepted K-CODEC CD-01–08, `docs/contracts/state-codec.md`; pre-acceptance revision-2 source
  digest `de93b44b1bc9c90d35d51a5782f826e41dfc2c29edadbbcb10a9ed9e6bc6085e`.
- Accepted K-PUBLISH PB-01–09, `docs/contracts/command-publication.md`; pre-approval source
  digest `a0ec392d3cb878a49de7864616dac9d05b3efaa7d98fff45cbdecb2dc0a80150`.
- At source commit `0c7a7757`: README/AGENTS/publication policy, K-CONTENT revision 1,
  K-WORKFLOW revision 1, pure knowledge/workflow implementation and isolated server build.
- Scope constraints: exact bodies/digests, clause-local authority, frozen historical provenance,
  ABA/current grants/blockers and no-fallback reads remain binding. No wholesale document approval,
  hash-granted authority, automatic migration or unproven generic semantic impact oracle.

Read existing core/build and available qualified offline dependency cache. Phase-1 writes are this
task, `docs/schemas/core-codec-v1.json`, `docs/schemas/core-codec-goldens-v1.json`,
`docs/design/core-codec-readiness.md` and synthetic scratch
qualification/evidence outside public Git. The schema is a field-shape oracle, not a JSON token,
Unicode, range-partition, graph-validity or temporal-authorization validator.
All new paths stay ignored/local; no allowlist change, staging/commit/push or force-add.

## Qualification constraints and acceptance

Use only bounded offline local probes, the existing JDK 21 and cached dependencies. No download,
upstream suite, server, Git data store, worker/model call, embeddings, configuration outside this
repository or unrelated source changes. Prefer one small parser probe over repeated whole builds.
Pin actual dependency bytes/API/configuration and expected refusal cases before executing it.

Qualification must distinguish library guarantees from required wrapper checks, including raw UTF-8,
duplicate decoded keys, trailing values, numeric token spelling, escaped surrogates, limits and
canonical writer behavior. A library accepting an input the wrapper must reject is a disclosed
boundary, not a false passing strict-codec test. Concrete limits are development qualification
values, not owner-approved capacity or production/hosting promises.

Escalate contract contradictions or unavailability of a suitable offline dependency rather than
weakening strictness or expanding scope. Expected output: pinned schema/goldens/profile/dependency
readiness record, then a revised executable task or an exact unresolved blocker. No independent,
integration or paid/production qualification is implied.

## Delegation boundary — revision 4

The owner requested parallel authentication design and delegated implementation of the current
codec contract. This task is the canonical worker assignment, not a rewritten conversation. Run in
the shared repository, writing ONLY the phase-2 paths below, this task's result section and designated
scratch evidence. The parent writes authentication docs separately and will not edit assessed source
while the worker is active. No further delegation/model calls, public tracking or Git commands that
alter the index/history. Do not overwrite unrelated ignored/local files or change approved contracts.

Read README/AGENTS/publication policy, the exact pinned executable inputs, existing main/test core
and build. Preconditions: their hashes match the table; baseline source matches `0c7a7757`; the
qualified JDK 21/cache and direct dependency are available offline. Retain input/source hashes before
editing. Missing inputs, required semantic/schema changes, out-of-scope APIs/files, resource-cap
changes or unavailable offline dependencies are escalation conditions: report the precise issue,
do not weaken rules or silently expand scope. Routine explicit DTO/validation code is in scope.

Use the designated scratch/evidence root supplied at launch only for your new evidence;
do not overwrite phase-1 probes/evidence or place raw local artifacts in public-oriented docs.
Return: changed-file list/hashes, tests and counts from actual JUnit XML, source/test evidence,
contract-case mapping, limitations and any unresolved blockers. Update this task's result with
sanitized observations but keep paths/secret/local environment details out of public-oriented docs.
Do not call author tests independent review or Git/auth/whole-service acceptance. Parent must verify
consequential claims against returned artifacts and source. Preserve the existing test assertions.

## Pinned executable inputs

| Input | SHA-256 |
| --- | --- |
| `docs/contracts/state-codec.md`, accepted CD-01–08 plus status/review appendix | `fd276610bf885af519db3df1405491254e5ccc8a75167c8f16f476a49001ab80` |
| `docs/contracts/command-publication.md`, accepted PB-01–09 plus approval provenance | `07fa3c635ade007d562b4b9d1d20fab47335716bdfe37da85ff596a4d938985c` |
| `docs/contracts/knowledge-content.md` | `02ca64e51e0596f39fc9455e74850c78b55a4929451e87eab146054cde2f12bc` |
| `docs/contracts/knowledge-workflow.md` | `569f42b8a7d9057408a10dcaaf955caae4421a062c8d5702bce7c7598ce4f2d2` |
| `docs/schemas/core-codec-v1.json` | `0844cc9c7ba7b70833f2488de142ba626d0d5cd94d608b513e97ae05fc6bfcaa` |
| `docs/schemas/core-codec-goldens-v1.json` | `71c998b3d3518630d82865ffdc77880dd19bcad2d5b5b2c624f32575cf2ee93b` |
| `docs/design/core-codec-readiness.md`, limits and JSON configuration | `227b8582e9d796c6b604dfd550a812b15cae43c842a59979393a61cc8023af6f` |

Baseline code/build remains at `0c7a7757`; retain the exact own-core source manifest and these inputs
before phase 2. Direct dependency is jackson-core 2.21.7, artifact digest in the readiness document.
Do not substitute databind/reflection or a different cached version silently.

## Phase-2 write scope and expected checks

Within `backend/server/`, permitted production changes:

- `build.gradle.kts`: add only the pinned direct JSON dependency.
- `src/main/kotlin/dev/writhelm/core/codec/StrictJson.kt`, `CoreRecordCodec.kt`, `CommandCodec.kt`,
  `CoreSnapshotCodec.kt`: explicit strict token/DTO encoding and complete pure export/restore.
- `src/main/kotlin/dev/writhelm/core/workflow/WorkflowSnapshot.kt`, `WorkflowSnapshotValidator.kt`:
  immutable typed internal snapshot boundary and graph/guard/provenance validation.
- `src/main/kotlin/dev/writhelm/core/workflow/ApprovalWorkflow.kt`, `WorkflowRecords.kt`,
  `src/main/kotlin/dev/writhelm/core/knowledge/RevisionCatalog.kt`: minimal internal export/restore
  accessors needed to preserve every private collection; no reflection/public generic mutation API.
  Revision 4 additionally permits the exact CD-02 overflow correction described below in the
  existing approval transition and result enum; unrelated transition semantics remain unchanged.

Allowed new tests: `src/test/kotlin/dev/writhelm/core/codec/StrictJsonTest.kt`,
`RecordCommandCodecTest.kt`, `SnapshotCodecTest.kt`, `CodecFixture.kt`. Existing core tests are
regressions, not assertions to weaken. Docs schema/goldens/profile may only change for a documented
correction consistent with accepted contracts; semantic conflicts require escalation/new approval.

Write contract-derived finite test cases before the codec implementation. Test independent exact
bytes/fingerprint goldens; shape variants and each refusal class; malformed raw/escaped Unicode and
integers; caller mutation/casts; budgets and overflow; all twelve state families; restored next-command
equivalence; rejected/changes answers, stale requests, ABA, continuity plus later direct approval,
post-record blocker/revocation, historical manifest provenance and no partial state replacement.
Use small configurable budget boundaries, not a large load experiment. Distinguish structural
equivalence from temporal authenticity; never claim the standalone validator detects every deletion.

Run only the own-core offline suite using qualified JDK 21/cache, one Gradle worker and the existing
bounded compiler/test process settings. Do not rerun the upstream suite. If the measured targeted
test resource requirement exceeds the existing limits, explain/escalate instead of silently removing
caps or triggering a heavy run. No Git/crash tests or production artifact distribution in this task.

## Phase-1 result

Recorded K-PUBLISH approval without changing PB-01–09. Added the schema for twelve record families
and six operations, independent synthetic byte/digest fixtures, concrete development limit profiles
and pinned Jackson configuration. Final bounded parser/writer probe matched 39 expected observations;
the schema accepted 22 positive shapes and refused 541 targeted shape mutations. These are NOT
production-codec tests or independent review. No source/build, upstream, tracked Git tree or
publication boundary changed. K-CODEC/PUBLISH readiness is not claimed for a networked Git service.

## Phase-2 execution result — blocked scope clarification

All seven pinned executable-input hashes and the existing own-core source/build matched the specified
baseline. The cached direct JSON artifact matched its qualified digest. Contract-derived draft tests
were written before draft implementation, but neither was compiled or qualified. The unverified draft
was retained only as excluded scratch evidence; no phase-2 source/build/test changes remain.

The bounded implementation stopped at a source-scope issue: CD-02 requires generation increment
overflow to refuse without changing state, while the existing approval transition increments a signed
64-bit generation with unchecked addition. Its typed domain-result surface has no explicit overflow
or resource-unavailable variant. Revision 3 permits only minimal internal export/restore accessors in
that transition/record surface, not a transition-behavior or typed-result amendment. Clarify/extend that
scope and the intended typed refusal before resuming; do not silently reuse an unrelated domain code.
A small arithmetic witness confirmed signed-long wrapping only; it did not instantiate a workflow at
the maximum generation, establish practical reachability or demonstrate an accepted corrupt restore.

The authorized offline own-core command returned successfully with test tasks up to date. Existing
JUnit XML contains 90 cases, zero failures/errors/skips; these are baseline artifacts, not newly
executed codec tests. Source hashes after cleanup match the retained pre-edit manifest. No codec,
restoration, publisher, authentication, independent review or whole-service acceptance is established.

## Revision-4 scope decision and resumption

Parent independently checked the report/artifact hashes, the eighteen unchanged baseline source/build
files and the cited unchecked `generation + 1` in the approval transition. The contract already requires
refusal before overflow; this is a task-scope omission, not an unresolved product-design choice.

Permit ONLY this additional transition change: preflight all approved target generations, and if any
cannot advance within nonnegative signed 64-bit range return typed
`WorkflowProblem.GENERATION_EXHAUSTED`, without resolving the request, creating approval effects,
moving any head/counter or returning a partially changed workflow. Add that enum case explicitly;
do not map exhaustion to STALE_SUBJECT or allow raw ArithmeticException/wraparound. Request rejection
or changes-required does not advance approval generations and must not become blocked by this check.
Current authority/request/digest/replay/stale/blocker rules remain intact; existing resolved retries
remain original outcomes rather than attempting another counter increment.

A minimal internal checked-increment/predicate helper within the already permitted workflow files
is allowed for deterministic boundary tests. Test MAX-1/MAX and atomic all-target refusal logic without
making a public state setter, using reflection, weakening graph validation or pretending a short
history with MAX counters is a valid restore. A full practically reachable MAX-generation history is
not required or claimed; source control-flow inspection plus narrow arithmetic tests and existing
coherent-group/state-immutability regressions must be labelled for their actual boundary.

The parent authorizes this engineering clarification under accepted CD-02, not a waiver of any law.
All seven pinned semantic inputs remain unchanged; scratch root for the resumed attempt is supplied
at launch so worker-v1 evidence remains immutable. Reuse the retained unverified draft only after
critical reinspection; complete the missing graph validator, allocation/streaming controls and tests
before claiming implementation. Retain tests-before-code evidence honestly: prior drafts are not
passing tests or blessed fixtures. No source/staging/auth/schema-profile scope expansion follows.

## Phase-2 revision-4 result — bounded implementation and author checks

Implemented the pure strict JSON/record/command codec, deterministic framed command fingerprints,
typed core entry keys, incremental per-entry export/restore, isolated immutable snapshot boundary and
bounded structural graph validation. Export retains typed descriptors and referenced model content,
not an encoded whole-corpus byte map. Admission remains separate from historical read/restore bounds;
local record admission alone does not validate a complete candidate or grant authority.

The validator checks inventory equalities, digests, actual contexts, request guards and contiguous
successful subject histories (including ABA), direct traces, continuity/blocker provenance and frozen
manifest traces. It does not replay historical commands against current grants/blockers. All original
collections are retained, including empty grants and continuity followed by later direct approval.
Revision 4's all-target generation preflight and `GENERATION_EXHAUSTED` result are implemented before
resolution/effect construction. MAX arithmetic/group predicates are tested at their narrow boundary;
no short MAX-counter snapshot is represented as valid history.

The final authorized offline own-core invocation executed 124 JUnit cases: the unchanged 90 original
cases plus 34 codec/restorer cases, with zero failures/errors/skips. The initial resumed run exposed a
new test fixture's missing actual before-context; that fixture was corrected without weakening any
original assertion. Later author review added same-assessment bootstrap/cycle, numeric-format versus
resource-failure, digest, budget and immutable-boundary checks. Initial contract-derived tests were
retained before production edits; subsequent review regressions are not claimed to predate them.

All seven pinned semantic inputs, existing test sources, compiler/test caps and worker settings remain
unchanged. Source/test hashes, executed XML, run logs and case mapping are retained as excluded evidence
for parent inspection. No staging, commit, push, authentication code, upstream suite, live model call,
network store or schema extension was performed.

This is author verification of the bounded pure task, not independent review, integration or service
acceptance. Whole source-H entry coverage, Git file modes/symlinks, prior-state transition preservation,
receipts, credentials, crashes/recovery and production capacity require their later adapters/gates.
An internally valid standalone subset is not authenticated complete history. The model remains resident;
resource accounting bounds consumed bytes/tokens/text/traversal and temporary representations, not exact
JVM object size or constant-memory operation. No unresolved task-scope blocker is reported.

## Parent source/evidence inspection

Checked all fifteen retained changed-file hashes and all twenty-eight final source/build/test hashes
against working files; the source file set matches the assigned boundary. The seven input hashes,
seven original test files and original settings/resource caps matched the worker's frozen pre-edit
manifest. Nine current JUnit XML files match the retained evidence byte-for-byte: 124 cases, zero
failures/errors/skips. Final log shows the test task executed, not UP-TO-DATE. No identical test suite
was rerun merely for handoff. No production source or tests were changed by the parent during review.

Read all delivered codec/snapshot/validator source, the existing-source diff and new tests. Checked
original digest/framing preservation, immutable snapshot copying, delayed payload construction,
strict token and Unicode boundaries, body coverage, bidirectional correspondence, successful subject
chains/ABA, continuity and frozen provenance after later direct approval, and current-versus-historical
policy handling. No blocking departure was found in this bounded pure implementation. This is a
parent source/evidence inspection, NOT the fresh-context independent review gate or runtime Git/
credential/client acceptance. Local admission returns no usable state and cannot replace future
publisher candidate validation; real adapters must enforce final file limits and exact source-H
coverage. Cost/scale and deferred M1-C1 checks remain open as stated in the contract.

The parent updates K-CODEC's implementation status/coverage AFTER the worker completed. Its CD-01–08
laws remain unchanged; the worker's exact original input bytes/hashes remain retained. No retrospective
claim that the updated status text was an implementation input, no new source publication and no
auth/journal-v2 behavior is introduced by these status notes.
