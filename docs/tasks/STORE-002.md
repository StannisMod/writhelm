# STORE-002 — Enforce known retained-history capacity before publication

Task revision: 2. Status: narrow correction author-tested and parent source/evidence checked.
Only the demonstrated history-count frontier is closed; broader capacity/crash gates remain open.
Executor: existing K-PUBLISH/APP child; no new delegation. No retention/format/API design change.

## Parent verification and disposition

Read the exact single-file production diff and twelve new count-boundary cases. Verified both final
changed-source hashes against working/tested bytes, unchanged test-first source between witness and
final, 103 immutable input files and all other current server inputs. Retained negative XML contains
twelve cases/eleven intended assertion failures; thirteen final XML reports match the tested assembly
and contain 57 store + 23 unchanged app cases, zero failures/errors/skips. No unchanged suite rerun
was performed by the parent for handoff. All original app/auth/core/build source remains unchanged.

Count proof is private and derived at validated genesis/cold audit/confirmed child only; authorization
and receipt recovery precede the guard, and ordinary recognized writes do not acquire a full-history
scan. The demonstrated same-profile count frontier is fixed without retention, format, receipt or
default-limit changes. No remaining blocking departure was established in this narrow correction.
This is parent source/evidence inspection, not independent review or general restart availability.

## Objective and evidence

APP-001 revision-2 probe reproduced: historyCommits=1 accepts genesis plus one actual-token published
write, then identical-profile clean cold open refuses UNAVAILABLE. Exact effect/receipt/body survive;
this is NOT data loss, unknown publication or crash qualification. Root cause is the recognized-write
path bypassing a known history count while cold audit enforces it. Preserve PB-05 bounded-growth
refusal and K-APP-06 supported restart intent rather than advertising this frontier as qualified.

Treat the configured deterministic history count as an operational new-publication budget as well
as an audit bound. Genesis counts one. At capacity a new receipt commit refuses UNAVAILABLE BEFORE
publication/effect/settlement, never writes a terminal rejection receipt for that capacity failure.
Existing same-key replay/current denial/key conflict and protected reads retain their established
semantics and consume no extra history. Both business success and recorded domain rejection require
one new commit of capacity. This narrows only the demonstrated known count frontier; it does not
promise arbitrary byte/time/heap/crash availability or require a new owner retention decision.

## Exact inputs and write scope

Use the 93-file APP frozen input plus nine exact delivered APP-001 main/test bytes, all supplied as
a new immutable input/manifest at launch. The earlier source commit remains f94108b5, with already
parent-checked auth/store/app additions identified by their final manifests. Read accepted PB-05/07/08,
CD-02/05a, K-APP-06, APP-001 probe result and actual store/cache/audit implementation. Reports are
claims until corroborated by the retained probe/XML/source; do not substitute labels for behavior.

Production writes ONLY `backend/server/src/main/kotlin/dev/writhelm/core/store/GitCommandStore.kt`
and, only if necessary for a minimal immutable internal count descriptor, `store/StoreTypes.kt`.
Tests: new `backend/server/src/test/kotlin/dev/writhelm/core/store/HistoryCapacityTest.kt`, plus
additive focused cases/helper changes in existing store tests if needed (no weakening). This task's
sanitized result and designated scratch are allowed. ALL auth/core/codec/app/build/contracts/schemas,
old task/evidence files and public tracking/index/history remain immutable. No default-cap increase,
receipt expiry/pruning, history rewrite, admission-policy duplication or per-command full audit.

## Required implementation boundary

Keep a derived validated `(H, publishedCommitCount)` bound to the recognized immutable state, not
a second canonical fact or a caller-provided label. Seed one only after actual genesis validation;
derive count from successful existing cold audit; carry +1 only after confirmed single-parent
publication. Never advance it from an orphan/unknown candidate. Unknown/new H must use its own
validated count; ordinary recognized writes must not scan/decode/hash all history just to count.
Keep current access and receipt lookup BEFORE capacity refusal so settled retries still work at the
frontier. No evaluator/CAS should run for a genuinely new command with no capacity. CAS recapture
must notice a competitor consuming the last slot; a matching published receipt still recovers after
current authorization. Final reconciliation, deadline/barrier and original credential semantics stay.

## Verification, escalation and output

Retain failing regressions BEFORE production edits, then tiny count-boundary tests (1/2/3) for
positive permitted publication, at-capacity new-key refusal without receipt, successful same-key
replay, changed payload conflict/current denial, rejection receipt consumption, clean same-profile
reopen and CAS count refresh. Connect at least one actual-token app operation with the unchanged
delivered app adapter. No large/default4096 history construction or real process termination.

Build in a NEW isolated scratch assembly of immutable input + own store correction/tests. Run
targeted store checks plus the unchanged 23 app checks because the shared dependency changed; this
is a necessary affected-integration rerun, not handoff repetition. Preserve all old worker-v1/v2 and
APP evidence; inject new portable STORE/APP test roots beneath the supplied scratch. Shared build
lock, qualified JDK/cache, original offline/heap/worker/CPU caps apply. No further model/agent/network/
listener/key-generation/source-Git/config/commit/push action. No broad upstream or expensive M1-C1 run.

Escalate a concrete semantic/shared-file need; no generic app side-counter/bypass. Return actual
negative/final XML, exact source/input hashes, delta/case map and limitations. Parent will recheck
the correction and app dependency evidence. Capacity and real restart/supervisor gates not exercised
remain unpassed; do not turn this narrow fix into a whole-service or independent-review claim.

## Sanitized implementation result

Corrected only `store/GitCommandStore.kt`; `StoreTypes.kt` and all auth/core/codec/app/build/schema/
contract and earlier test/task inputs remain unchanged. A private immutable validated-head wrapper
now binds the snapshot H to its derived published commit count. Genesis seeds one after validation
and confirmed publication; successful cold audit supplies its actual history length; confirmed
single-parent publication carries the prior count plus one. Orphan/unknown candidates never advance it.

Current authorization and published receipt lookup precede the new-publication capacity check.
At capacity, genuinely new commands return UNAVAILABLE without evaluation, CAS, effect or receipt;
existing replay/conflict/denial and reads remain usable. CAS recapture and final receipt reconciliation
use the new head's audited count. No canonical count field, retention change, public API, default
limit increase or per-command historical scan was added.

New `HistoryCapacityTest.kt` was retained and executed before the production correction: 12 cases,
11 expected assertion failures, zero errors/skips. The same test bytes then passed all 12 cases.
Final affected-dependency run: **57 store tests plus all 23 unchanged app tests**, zero failures/
errors/skips; 33-second build and 28.263 seconds summed suite time. Tests exercise tiny limits 1/2/3,
receipt capacity, replay/conflict/current denial/reads, cold count derivation, competitor consumption,
final reconciliation, orphan/unknown behavior and actual-token unchanged-app clean reopen.

All 103 frozen input bytes, the nine delivered app source/test files and 18,748 prior evidence files
were verified unchanged. Only the authorized corrected store source differs in the assembly; new
tests/data/evidence use the designated fresh scratch with portable STORE/APP roots. Offline JDK21,
shared build lock and original compiler/test/worker/CPU caps were preserved. Exact input/source/test/
XML hashes, production diff, case map and negative/final evidence are retained in the private result.

This closes the demonstrated deterministic commit-count frontier only. Other audit byte/time/heap,
capacity, actual process restart/surviving-child supervision and independent/whole-service acceptance
gates remain held. No default-4096 load, real crash/kill, network, live credentials, extra agent/model,
source Git/index/history/config or publication-tracking action occurred.
