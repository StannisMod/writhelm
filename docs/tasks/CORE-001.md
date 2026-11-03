# CORE-001 — Immutable document revisions and exact structural reads

Task revision: 1. Status: implemented and author cheap-checked, local-only; independent review pending.
Executor: primary session; no delegation. No service/integration acceptance.
Authority: accepted R1/P2 and continuation after T1. This is I1a, not completion of I1 or M1.

## Objective and exact inputs

Implement the pure in-memory K-CONTENT subset: exact UTF-8, complete byte-addressed structure,
version-bound references, immutable revision catalogs and explicit no-fallback read failures.
Contract and finite obligations were written before tests/code. All inputs are retained by digest.

| Input | SHA-256 |
| --- | --- |
| `README.md` | `6d3f09c622a8e46a737af24cc6eb5474ee246e1d2e89dd8bfbe2d97e057b9379` |
| `AGENTS.md` | `e737009d14320aa0bc1d5a8006a2ebfdc3862c282504f94a436e645513f035e9` |
| `docs/contracts/knowledge-content.md`, K-CONTENT revision 1 | `b4290c2e58856f9378e60961663176938fd62b118b8723b61252aae9f5ff9ef3` |
| `docs/design/knowledge-model.md` | `3f071d8577e7f9f93a7a40feb29f6098d24c2f7bbffbfb27d4a215a46e5e2728` |
| `docs/design/git-state.md` | `e8873a69d84a67f83ff1b416a3dd390423b31dadcd77c45df812dcb6fdf92a20` |
| `docs/specification.md`, including T1 | `43edccd646cff3d679dfac6fa3a13a3601e8be43c9ae334f0221373231dc0d2a` |
| `docs/design/first-slice.md` | `36ffad57100b662995e0765100c4bdc09bff1ddd51116bdad3ecaa3bce6c2e3d` |
| `docs/publication.md` | `4aa859315e3978a0a655b4f410e490cd41aa6bcf8df15043742da662dcdfff53` |

## Scope and constraints

- Write: newly authored local `backend/server/settings.gradle.kts`, `build.gradle.kts`,
  `gradle.properties`, `src/main/kotlin/dev/writhelm/core/knowledge/*.kt` and corresponding
  `src/test/kotlin/dev/writhelm/core/knowledge/*.kt`; this task/contract evidence map and bounded
  README/architecture/publication/status updates. No imported source is modified or copied.
- Decide: routine engineering realization of the accepted immutable-reference model and contract
  framing, not new approval semantics or corpus units. IDs stay explicit; digests are not authority.
- Do not implement approval policy/GUI/credentials/JSON command codec, Git persistence/CAS/journal,
  SQLite/indexes, task dispatch, model execution or semantic merge/impact inference in this unit.
- No paid/live model/embedding calls, delegation, upstream suite, source deletion, user-wide config,
  staging/commit/push. New source paths remain ignored/local until a scoped publication decision.
- Tests: only this small pure-core suite; use cached pinned Kotlin/JUnit/JDK and Gradle offline,
  one worker/test JVM, bounded heaps. No root `:current` tasks or inherited broad test finalizers.
  A missing cache/resource problem is investigated, not permission for a heavy download/build.

## Dependencies, acceptance and evidence

Accepted R1/P2/T1 permits this pure subset while A1's transport/credential realization and later
UI polish remain separate. Native privileged-UI/anti-deliberate-bypass qualification is not a gate
for a library that has no authentication or external side effects.

Acceptance: KC-01 through KC-06 hold for their finite case regions; positive and adversarial
boundary cases assert exact independent bytes/digests/failures; input/output collection mutation
does not alter existing records; old/new pinned reads remain distinct; source has no persistence /
network/harness dependency. Record actual target test results and assessed source hashes.

Evidence is author implementation/cheap checks only until a separate non-author review occurs.
Do not claim M1 acceptance, integration, durable Git behavior, approval safety or a working service.
Downstream I1b can consume implemented/cheap-checked content under a declared readiness dependency,
without pretending pending independent/full-slice verification has passed.

Escalate a requirement conflict, private/source-provenance issue, need for a paid/expensive action
or unrelated code change. Output: local core/tests, reproducible bounded check result, coverage map,
explicit limitations and next I1b record/transition/codec task boundary.

## Implemented result and evidence

Created an independent small `backend/server/` build with five production Kotlin files and three
test files. The content/index/reference model implements KC-01 through KC-06; context reads preserve
fragment locations/boundaries and revision-local/catalog reads explicitly reject mismatched identities.
There is no accepted-document boolean, latest fallback, persistence, network or approval authority.

Final bounded offline check from `backend/`, using JDK 21 and the qualified cache:

```sh
bash ./gradlew --offline --no-daemon --max-workers=1 -p server test --console=plain
```

Project properties bound Gradle to a 512 MiB heap/in-process compiler/two active CPUs/one worker
and tests to one 128 MiB JVM. No network dependency download or upstream `:current` task. Initial
run succeeded in 14 seconds; defensive identity checks and independent framing/slicing cases passed
in the next run. After final 140-column style cleanup, the assessed run succeeded in 9 seconds:
ContentTest 23, StructureTest 23,
RevisionCatalogTest 7; total **53 tests, zero failures/errors/skips**. Counts are execution facts,
not an adequacy proof or a claim of passing Writhelm service scenarios.

Author self-review checked byte-array/list mutation routes, complete UTF-8 partitions, unambiguous
digest framing and direct/internal versus catalog resolution. Separate non-author review has not
occurred. Publication P3 is a proposal only; sources remain ignored/local and no imported file was
changed. Do not stage generated build reports/artifacts or infer acceptance/integration from tests.

Final evidence self-check retained SHA-256 identities for all eleven new source/build files and
the three JUnit XML reports. Verified eight retained inputs and that the original normative
K-CONTENT bytes precede the later coverage appendix unchanged. Compared all 1,382 tracked imported
backend files' Git blobs/modes to the pinned upstream mapping: unchanged. New paths stay ignored,
source has no filesystem/network/database/harness dependency, line lengths respect the inherited
140-column setting, source privacy/whitespace/link checks passed, and nothing is staged. A task-table
digest transcription typo was caught and corrected against its already retained input bytes;
neither the input nor the implementation contract was changed to accommodate code.

## Continuation

I1a can be consumed as implemented/cheap-checked content in a planned I1b task. Next: canonical
approval-request/answer/continuity/manifest records, strict record/command codec and pure transitions,
retaining cooperative T1 and U1's exact structured choices/completeness. Then the Git publisher,
then real client wiring and the planned integrated verification checkpoint. Each unit needs its own
bounded task and current exact inputs. Stronger anti-bypass/GUI polish is not silently reintroduced
as a gate; authentication and correctness are not waived. No public push or independent acceptance.
