# AUTH-002 — Pure authentication and project authority implementation

Task revision: 3. Status: bounded pure implementation author-tested; parent header finding closed.
Parent source/evidence inspection completed; independent auth review and integration remain pending.
Executor: one new native child for the accepted K-AUTH design; no further delegation.
Baseline commit: `f94108b5f64bf5389199a26436048e34c714e51c`.

## Revision-2 correction — header admission consistency (resolved)

Parent source/evidence inspection verified the first five source/test hashes, task hash and four
actual JUnit reports (60 passing cases). This is not independent auth review or local acceptance.
Concrete finding: AuthRecordCodec.header currently calls StrictJson.encode outside the configured
JsonConstruction bound, unlike its ordinary encode path. With a valid positive auth profile whose
token bound is below the six-token header, header() returns bytes that checkHeader() under that SAME
codec refuses. Field-name/collection bounds are similarly bypassed by header encoding.

Required correction under CD-02/AU-10 and auth readiness's per-header bounds: encode the header with
the same configured construction/depth/collection/token/name/byte admission rules as other auth
records. Preserve its canonical bytes under adequate limits. Add failing regression first, including
small N-1/N/N+1 token, name/collection and encoded-byte checks (and unchanged exact golden). Keep
old assertions; do not map admission to corrupt data or weaken read/resource distinction.
Only your existing allowed auth source/test files and sanitized task result may change. Archive
worker-v1 evidence unchanged; resumed evidence root is supplied at launch. No changes to core,
publisher, schemas, approved laws, dependency/resource caps or publication scope are required.

## Parent inspection of revision-2 correction

Verified the final five source/test hashes plus three changed-file hashes, unchanged frozen server
source/build and exact header-only production diff. Read auth source/tests and retained result/log/XML.
New regression source is byte-identical between its executed negative witness and the final run.
Negative XML contains 73 cases and exactly three assertion failures (token/name/collection admission
bypasses, not compilation failures); corrected XML contains 73 passing cases, no failures/errors/
skips. No unchanged suite was rerun for handoff. Canonical adequate-limit header bytes remain exact.

No further blocking departure was established in this bounded pure scope. This records parent
source/evidence inspection, not fresh-context independent auth review, whole-store startup validation,
atomic publication, real credentials/transport or service acceptance. Actual H-to-policy binding,
complete core/auth/grant-patch/journal transaction and current authorization on publication/replay
remain required integration work. The concurrent store worker's source/inputs were not changed.

## Objective and exact knowledge inputs

Implement K-AUTH revision 1's pure credential verification, canonical auth record/command-v2 codec,
current scoped authorization and atomic in-memory authority transitions. No Git publisher or listener.
Inputs are the exact baseline-commit README/AGENTS/publication policy;
`docs/contracts/{authentication-access,state-codec,command-publication,knowledge-workflow}.md`;
`docs/schemas/{auth-service-v1,auth-service-goldens-v1,core-codec-v1}.json`;
`docs/design/{auth-service-readiness,core-codec-readiness}.md`; existing core APIs/source/tests/build.
Read primary documents. AU-01–10 and approved AU-09 govern semantics; field schemas are shape
oracles, not authority/graph/UTF-8 validation. No role hierarchy, inferred ownership or secret-in-Git.
The baseline core is author-tested/parent-inspected, with independent review running, not integrated.

## Read/write/decision scope and dependencies

Write ONLY `backend/server/src/main/kotlin/dev/writhelm/core/auth/*.kt`, corresponding
`backend/server/src/test/kotlin/dev/writhelm/core/auth/*.kt`, this task's sanitized result and your
designated scratch root. No edits to existing core/codec/workflow/knowledge/tests/build/contracts/
schemas, publication rules or the publisher worker's `store/` paths. New files stay ignored/local.
No index/history changes, commit/push, downloads, new dependencies, live keys, network services,
model/provider calls or user-wide configuration changes. Existing Jackson core/JDK are sufficient.

The publisher worker is independent. Do NOT import its in-flight classes or force an interface by
editing shared files. Expose immutable typed auth state/results/patches for later parent binding.
Journal-v2 serialization and Git/runtime ownership belong to the publisher task; auth record and
command-v2 codecs belong here. Reuse existing internal strict JSON primitives without changing them.
Trusted new grants may be applied using an internal constrained same-project grant patch against
the existing snapshot APIs after AU validation, or returned as a typed proposed patch; never add a
public generic state/actor override or a second effective capability store. Report the exact boundary.

Initial enrollment/profile/verifier bindings are immutable. Existing v1 core commands/fingerprints
remain unchanged. Enforce project-bound active credentials, current explicit grants, fixed owner,
last-active-owner key, checked target generations, no credential-ID reuse/reactivation, one rotation
successor and current-access principal-scoped replay hooks. No raw credentials in outputs/receipts/
logs; only fixed synthetic secrets in tests. T1 is not adversarial human-origin proof.

## Execution and evidence protocol

First verify/pin baseline inputs and source bytes, retain tests-before-implementation evidence, then
write contract-derived tests. Read baseline from the frozen copy supplied at launch; working repo
receives only your new allowed files. Build/test from YOUR isolated scratch assembly of unchanged
baseline plus your own files, excluding concurrent publisher code. Do not edit copied original
source/test/build bytes. Before each test snapshot, hash own files so the report binds exact bytes.

Use qualified offline JDK/cache, original one-worker/512 MiB compiler/128 MiB test heap/two CPU caps,
and the shared build lock supplied at launch. Run targeted `dev.writhelm.core.auth.*` tests only;
existing 124 cases need not be rerun with unchanged baseline. No whole-service, process-crash,
production-maxima or credential-security load tests; no silent resource increases. Report compile/
runtime blockers precisely. Parent later checks combined integration; this task cannot certify it.

## Acceptance, escalation and expected output

Positive exact credential/grant/owner operations plus individually failed predicates; independent
verifier/token/grant/fingerprint goldens; strict shapes/Unicode/duplicates/version refusal; byte and
generation bounds; foreign projects, role/actor injection, raw-verifier/ID-only use; immutable state;
revocation, rotation, stale/ABA, grant-presence distinction, owner lockout and one-successor atomicity;
original/current credential distinction and manager-versus-target principal key spaces. Pure header/
Host/Origin parsing may be implemented with explicit configuration, but no actual HTTP/TLS claim.

If a concrete requirement needs a shared baseline change or reveals a contract/schema contradiction,
return the smallest reproducible escalation; do not silently weaken checks or expand scope. Routine
typed field/API realization in your new namespace is authorized, not a need to ask for each name.
Keep verifier/secret lifetimes and unknown-publication boundary honest; external secret writes are
not made transactional by an in-memory transition.

Return changed-file/source/input hashes, exact test commands/JUnit XML counts, case-to-clause map,
limitations/integration ports and `result.md` in your designated evidence root. Update this task with
sanitized actual results only. No claim of fresh independent review, real auth deployment, publisher
integration, host isolation, owner approval of code or release readiness.

## Sanitized implementation result — revision 1 execution

Bounded pure implementation completed locally, not accepted or integrated. Added only three main
auth files (`Credentials.kt`, `AuthCodec.kt`, `Authority.kt`) and two auth test files (`AuthTest.kt`,
`AuthBoundaryTest.kt`) beneath the assigned main/test `dev/writhelm/core/auth/` directories. Existing
source/tests/build/contracts/schemas and publisher files were not edited. New source remains local.

The frozen 72-file input manifest and isolated assembly were hash-checked before execution and after
the final run. Contract-derived tests were retained before production source existed. Final targeted
offline suite: **60 tests, zero failures/errors/skips**, four retained JUnit XML reports. One earlier
52-case run had an incorrect test expectation for core `LAST_AUTHORITY`; the assertion was corrected
without changing production semantics. The final compile emitted only a redundant test `!!` warning.
Original build/resource caps were preserved; no baseline regressions, listener, live keys, paid calls,
Git publication or process-crash tests were run.

Implemented strict auth record/header and command-v2 codecs, independent verifier/token/grant/key/
fingerprint goldens, current explicit capability checks, immutable checked authority candidates,
guarded replacement/removal, fixed-owner lockout checks, revocation and one-successor rotation.
Pure replay hooks produce current principal-scoped identities, not persisted receipts or results.

Integration ports are internal `AuthorityView.checked`, `authorize`, `replayIdentity`, `execute` and
`revokeAccess`; `AuthTransition.Success` returns a candidate plus a constrained typed `GrantPatch`
where applicable. Parent must bind the supplied core `AccessPolicy` to the actual captured workflow H,
validate served-project coverage, atomically apply the complete candidate/patch and journal-v2 receipt,
preserve immutable bindings/history, and recheck the same token on each recapture/replay. No generic
workflow override, second effective grant registry or dependency on concurrent publisher code was added.
The explicit new-root trusted `checked` factory is not an enrollment/reset endpoint. Whole-store
startup budgets, temporal journal validation, transport/header handling, secret provisioning/client
storage and publication recovery remain parent/runtime responsibilities. External secret writes are
not made transactional by these pure transitions. T1/human-origin and full-request timing limits remain.

Exact input/source hashes, commands, retained pre-implementation tests, actual XML and clause/case map
are recorded in the designated worker evidence root's `result.md` and manifests. This is author-run
bounded evidence only, not independent review, whole-service qualification or release readiness.

## Sanitized correction result — revision 2 execution

Header admission finding corrected locally, pending parent verification. Only `AuthCodec.kt` and
`AuthBoundaryTest.kt` changed in the existing auth source/test scope, plus this result append.
`header()` now runs inside the configured `JsonConstruction.bounded` admission scope and validates
its output with the same codec, matching ordinary record encoding. Adequate-limit canonical bytes
remain exactly `{"formatVersion":1,"profileVersion":1}` (38 UTF-8 bytes).

Regressions were written and executed before the production edit: 73 targeted cases ran with exactly
three expected failures (header token bound 5, field-name byte bound 13 and collection bound 1 returned
bytes instead of refusing). The byte-bound regression already passed. After the narrow correction,
all **73 tests passed, zero failures/errors/skips**, with unchanged regression assertions. The 13 new
cases cover token 5/6/7, name bytes 13/14/15, collection 1/2/3, encoded bytes 37/38/39, exact golden and
minimum supported header depth. Both admission paths assert typed `ADMISSION_LIMIT` on undersize.

Both runs used only the isolated frozen-baseline-plus-auth assembly, offline qualified tooling, the
shared build lock and unchanged worker/heap/CPU caps. Retained negative/final source manifests and
four actual JUnit reports per run bind the executed bytes. All 72 frozen input files and their
assembly copies remained unchanged; revision-1 evidence was hash-verified unchanged. Exact hashes,
commands, negative witness and fresh XML are retained in the revision-2 worker evidence root.
No core/store/contracts/schemas/build/resource changes, integration, independent auth review,
transport/runtime qualification or release-readiness claim. Parent integration ports and limitations
from revision 1 are unchanged.
