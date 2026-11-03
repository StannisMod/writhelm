# STORE-001 revision 2 — bounded implementation result

Status: implemented and author-tested against frozen baseline
`f94108b5f64bf5389199a26436048e34c714e51c`; not independent review or whole-service acceptance.

## Delivered

Five main files and seven test files under `dev/writhelm/core/store/` implement a single
bare SHA-1 Git store and publication ref, explicit genesis/open, immutable captured reads,
journal-v2 receipts, canonical admission policy, exact typed write descriptors, preservation checks,
expected-old publication and bounded same-command CAS rebuilds. Core command-v1 bytes remain unchanged.
Trusted current authorization runs on each attempt before receipt disclosure; principal identity
and original presented credential identity cannot silently change within a retry sequence.

Native Git 2.43.0 uses argument arrays, a cleared environment, rejected arbitrary repository config,
byte-preserving plumbing, bounded streams/deadlines and explicit object/reference synchronization:
`core.fsync=committed,reference`, `core.fsyncMethod=fsync`. The installed manual establishes that
`committed` alone covers objects, not references. This is not host/power-loss qualification.
Exclusive ownership, a durable runtime marker and an in-flight publication barrier fail closed.
No code deletes Git locks, kills children, resets history or automatically clears ambiguous markers.

## Verification and remaining gates

Final isolated offline targeted run: **45 cases, zero failures/errors/skips**, 12-second Gradle build,
7.867 seconds summed suite time. Original one-worker/512-MiB compiler/128-MiB test/two-CPU settings
were preserved. Supplied baseline hashes and all copied original source/test/build bytes match;
only the worker's store files were added. Concurrent auth code was excluded.

The revision-2 minimal native-Git qualification took approximately 20 ms for ten calls. A separate
JDK FileChannel cross-process refusal/release probe took approximately 68 ms. Tests cover successful
core effect publication, exact bodies, original rejection/domain replay, changed-payload conflict,
current-access replay, CAS rebuild/preservation, orphan exclusion, injected unknown response/barrier,
corrupt history/descriptors/modes/config, namespace refusal, canonical policy and small IO budgets.

## Revision-2 parent corrections

Before the production correction, new one-attempt regressions reproduced **five assertion failures**:
an actual ref advance followed by an error reply was misclassified instead of recovering success/
rejection, enforcing current denial, recognizing a changed fingerprint or retaining unknown on failed
reconciliation. The corrected final-attempt path audits the published head and rechecks current access
before receipt disclosure, without another evaluation/CAS or resetting the aggregate deadline.
Reconciliation failures retain unknown and the publication barrier; unrelated-head absence can still
exhaust as unavailable. Both error-exit and stderr-only replies are covered.

Test directories now use `WRITHELM_STORE_TEST_ROOT`, falling back to the standard temporary-directory
facility. The configurable-root test and a separate standard-fallback execution passed. New evidence
and all new test repositories use the designated revision-2 scratch; hashes of all 4,511 prior worker
files and their complete file set remained unchanged. No test source retains a worker-specific path.

`StorePorts`, `CommandAccess`, `ReadAccess` and `CommandEvaluator` are **trusted integration ports**,
not credential implementations. Parent integration must provide real current auth, auth namespace/
policy validation, original credential provenance and affected domain-transition validation bound to H.
Synthetic callbacks do not qualify authentication, anti-lockout policy or a complete service journey.

**Held M1 gates:** real surviving-child supervision/process-crash recovery, independent review,
assembled auth/transport and whole-service acceptance. Ambiguous runtime/publication markers require
verified supervisor/operator reconciliation; this implementation supplies no automatic recovery or
marker-clear API. Do not delete markers/locks merely because a timeout expired or an owner lock is free.

Raw evidence, exact input/source/XML/tool hashes, argv/environment, case list and detailed integration
notes are retained only in the designated private worker scratch `result.md` and evidence manifests.
No source index/history/config, shared core/auth/codec/build/schema/contract/publication file, baseline
permission or public tracking state was changed. New files remain subject to scoped publication review.
