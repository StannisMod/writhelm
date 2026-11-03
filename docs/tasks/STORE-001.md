# STORE-001 — Atomic Git command store and durable journal

Task revision: 3. Status: bounded store mechanism author-tested; parent findings/evidence checked.
Real auth/domain binding, independent review and real crash/supervision gates remain pending.
Executor: one new native child for K-PUBLISH with approved K-AUTH AU-09 extensions; no further agents.
Baseline commit: `f94108b5f64bf5389199a26436048e34c714e51c`.

## Revision-2 parent corrections (resolved)

Parent inspected the initial report, publication/runtime/receipt source and publication tests. Two
concrete findings need resolution within the already allowed store source/test namespace:

1. On the LAST allowed publication attempt, a completed update-ref may have advanced the published
   ref while returning an error/nonempty stderr. execute currently clears its joined-child barrier,
   leaves the changed head for a next iteration, and returns UNAVAILABLE when no iterations remain.
   PB-07 requires inspecting the new published head/receipt FIRST, not skipping reconciliation at
   the retry bound. Add a failing one-attempt injected-driver regression: perform real ref advance
   then return an error GitReply (not a thrown exception). Recover the original matching logical
   receipt after CURRENT access checks without reexecuting the transition. Changed identity payload
   conflicts; current denial reveals nothing; genuinely unrelated publication with no receipt can
   still exhaust as unavailable. If reconciliation itself cannot establish the result after possible
   publication, preserve explicit unknown/recovery-required, never imply rollback or orphan success.
   Final receipt reconciliation is not a new CAS rebuild and must not silently exceed its deadline.
2. Tests hard-code the first worker's absolute scratch directory. A fresh test environment without
   that directory fails before exercising the store. Replace this with an explicitly configurable
   test root / standard temporary-directory facility, without any worker-specific/machine path in
   public-oriented source. During this task inject a designated NEW scratch root and keep ALL test
   data repositories beneath it. Preserve old worker-v1 evidence; do not accidentally keep writing
   new test repositories there. Show a regression/check proving no dependency on that old root.

New correction evidence root is supplied at launch. Retain new tests before production correction;
do not weaken existing assertions or run process-kill/default-maxima tests. Only current allowed
source/tests, task result and `docs/tasks/STORE-001-result.md` may change. The latter report was added
outside the initial named task-file write scope; parent explicitly allows that sanitized report path
from this revision, with no tracking/publication authorization or permission to overwrite other docs.
No auth/core/schema/contract/build/resource or source-index/history changes are required.

## Parent inspection and local disposition

Read store/runtime/receipt/policy source and the correction flow, targeted publication tests and
reports. Recomputed all twelve final source/test hashes against working files and the exact tested
assembly, confirmed original core/build bytes unchanged, and checked seven final XML reports:
45 cases, zero failures/errors/skips. Pre-fix XML has exactly five intended assertion failures,
not compile/setup errors. The final log records executed tests. Old-evidence before/after manifests
match; test source contains no worker-specific absolute path. No identical suite rerun for handoff.

The last allowed publication attempt now reconciles the changed published head and receipt under
current authorization while preserving the original deadline and unknown-outcome barrier. The test
root is explicitly configurable with a verified standard-temp fallback. No remaining blocking
departure was established in this bounded mechanism scope. This is parent source/evidence inspection,
NOT independent store review, a complete working K-PUBLISH service or real crash recovery qualification.

Required next integration is actual auth/namespace/reducer/provenance binding against captured H;
test-only permissive callbacks do not satisfy it. Supported supervisor/operator recovery proof,
real process faults and the assembled M1-C1 journey remain held gates. No new commit/publication
authorization is implied by the local result. Original reviewer/worker manifests remain retained.

## Objective and exact inputs

Implement the bounded Git store, exact snapshot reads, durable command receipts and one-root CAS
publication under K-PUBLISH PB-01–09. Account for approved auth/journal-v2 extension WITHOUT depending
on concurrent auth implementation. Provide explicit trusted authorization/namespace/transition ports
for later parent integration, not fake credential verification or raw patch clients.

Read baseline README/AGENTS/publication policy;
`docs/contracts/{command-publication,state-codec,authentication-access,knowledge-workflow}.md`;
`docs/schemas/{auth-service-v1,auth-service-goldens-v1,core-codec-v1}.json`;
`docs/design/{git-state,auth-service-readiness,core-codec-readiness}.md`; current core/codec APIs.
Exact baseline commit pins these inputs; retain their hashes before code. Accepted auth namespace/
journal-v2 extends the earlier core-only descriptors; v1 core command/fingerprint bytes stay exact.

## Write/decision boundary and independent-worker isolation

Write ONLY `backend/server/src/main/kotlin/dev/writhelm/core/store/*.kt`, corresponding new
`backend/server/src/test/kotlin/dev/writhelm/core/store/*.kt`, this task's sanitized result and your
designated scratch root. No core/auth/codec/workflow/knowledge/test/build/schema/contract or ignore/
publication edits. No dependencies beyond JDK/Jackson and the qualified native Git. No index/history
mutation of the SOURCE repo, public tracking, commit/push, outside config or further agents/model calls.
Git writes are allowed ONLY to disposable private test data repositories under your scratch root.

Auth record/command-v2 codecs and credential transitions belong to AUTH-002. You own journal-v2
codec, store header/namespace handling, typed write descriptors and publication machinery. Implement
small immutable internal ports accepting the unchanged presented command plus current-head trusted
authorization/namespace validation/evaluation callbacks. Call current authorization on EVERY capture/
CAS retry and before protected receipt disclosure. Stable command identity/fingerprint must not
silently change across attempts. A callback cannot bypass your exact write-set/history/atomicity
validation. Do not import uncommitted auth worker code or claim that stub auth ports prove real auth.
Unknown namespace/version refuses a whole-store writer unless its supported validator is registered.

Allow routine internal port names/DTOs and native-Git realization preserving accepted semantics.
Do not implement raw-file patch endpoints, SQL/index SSOT, multi-writer/HA/remote hosting, live
workers, network listeners, secret provisioning, destructive lifecycle or backup/history rewriting.

## Concrete runtime preparation inside this task

Initial candidate runtime is Linux/JDK 21, native Git 2.43.0, local disposable bare repo, files ref
backend and `refs/heads/state`. Before mutation tests, inspect supported Git semantics/config and
record exact process arguments/environment. Use byte-preserving plumbing/argument arrays and a
controlled config disabling arbitrary hooks/filters/alternates/replace refs and system/global config.
Pin and minimally qualify object/ref sync settings (candidate `core.fsync=committed`,
`core.fsyncMethod=fsync`); do not infer host/power-loss guarantees. Unsupported settings fail visibly.

Development bounds: 8 MiB per Git stdin/body-facing buffer, 16 MiB bounded stdout per plumbing
call, 16 KiB stderr, 15-second per-process deadline, at most 3 CAS rebuild attempts and 45-second
aggregate command budget. Core/auth/receipt file limits remain their respective readiness profiles;
larger tree listing/restore has a separately explicit bounded path, not unlimited process output.
Tests use smaller injectable bounds, no default-maxima load experiment. Normal writer must avoid
rehashing all historical bodies; startup/full audit is distinct. Budget/refusal remains typed.

One service owner plus serialized publisher is mandatory. Implement/check an exclusive-owner and
in-flight publication barrier. A possibly surviving update-ref child cannot be ignored on timeout/
restart: preserve unknown outcome, reconcile or fail closed as recovery-required. A conservative
runtime marker/runbook may block startup pending verified process termination; it must contain no
unique canonical business fact, establish no success, and never authorize blind lock deletion.
Do not claim automatic crash recovery if only explicit safe operator reconciliation is delivered.
Report any missing supervision qualification as a held M1 gate, not permission for unsafe startup.

## Tests, evidence and readiness

Write contract-derived tests before implementation. Build from YOUR isolated scratch assembly of
the frozen baseline plus your allowed store files, excluding concurrent auth code. Do not modify
copied original source/tests/build. Use the qualified JDK/cache/original heap and worker caps; serialize
Gradle via the supplied shared build lock. Run only targeted `dev.writhelm.core.store.*` cases.
First measure one minimal local Git create/write/read/CAS round trip and record its cost; if it is
not a cheap bounded check or exceeds caps, escalate before any broad run. No real process termination,
kill/restart matrix, upstream suite, assembled HTTP/MCP or expensive M1-C1 checkpoint in this task.
Pure/injected fault seams are allowed; they do not establish real crash durability.

Acceptance: valid genesis/open and refused reset/wrong store/version; one snapshot per read; exact
body/record integrity; same-key original success/rejection replay, changed-key payload conflict;
current access before replay; empty effect on domain replay/rejection; complete effect+receipt and
one new receipt per non-genesis commit; self-OID exclusion; exact H-to-C diff/immutable preservation;
CAS same-command rebuild without unrelated clobber; orphan receipt never success; unknown publication
outcome/barrier; namespace and journal-v2 shape/descriptor/provenance rules; bounded safe process IO.

Escalate actual semantic conflicts or required shared-file edits, never weaken an original invariant
or fake a passing current-auth/process claim. Retain input/changed-source/test hashes, exact argv/
config qualification, measured cheap-check observations, JUnit XML/logs and case map. Return result.md,
remaining runtime/auth integration ports and limits. Parent checks consequential evidence; independent
review and planned real M1-C1 fault/assembled coverage remain unpassed and gate-holding.
