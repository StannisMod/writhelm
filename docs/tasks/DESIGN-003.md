# DESIGN-003 — Publication, retry and process-crash recovery

Task revision: 2. Status: conversational P2 architecture scope accepted; design outputs self-checked.
S1 is an owner requirement; P1 is not accepted. No implementation.
Local design only. Executor: primary session, no delegation.
Authorization: continue the bounded own-core design after owner acceptance of R1.

## Objective

Specify the first-slice storage/commit/retry protocol for exact document/index publication,
clause-scoped approval, continuity and immutable consumer manifests. The owner requires structural
SSOT/versioning of all canonical state in Git (S1), not separate canonical content and authority stores.
Replace P1 with a concrete Git-authoritative P2 protocol and derived-only optional SQLite.
R1/F3 remain settled. No implementation or wholesale adoption of the held G1-G6 design.

## Exact drafting inputs

Snapshots retained outside public Git by SHA-256; accepted directions remain distinct from draft
contract wording, logical fields and implementation proposals.

| Input | SHA-256 |
| --- | --- |
| `README.md` | `92515454c3c3b4188d0e865f31d60c005b548fb66e88de903179aabce796f067` |
| `AGENTS.md` | `e737009d14320aa0bc1d5a8006a2ebfdc3862c282504f94a436e645513f035e9` |
| `docs/design/knowledge-model.md`, revision 2 with R1 scope recorded | `f6775a0a3d04842836111d09b5d128dfb13cb1c88fe0f50792b66c47e2cba069` |
| `docs/tasks/DESIGN-002.md` | `10387765ea1ad541362d6d3b751d91f87a871d61c9912fed0a051126cdedf832` |
| `docs/contracts/accepted-knowledge.md` | `e9cba3802e427522ea157810bf5c72d7513f09ba2fb676dc363820c53f70d4b6` |
| `docs/specification.md` | `10e20626b301569a468c1ec91de4ce828505af5ccd75fa394844edd10124c61e` |
| `docs/workflow.md` | `287cea745b8bb4d267378f935311d69dae39c585359aa59b928f89959d534bbb` |
| `docs/architecture.md` | `d021a397bed2e471be1018f8b9eac051e4480c70b89faf3b54d493d46aadcad3` |
| `docs/publication.md` | `4c146dcdfa7d577c934b99ab2be5da9e47f51c715828e29ad7e38e2d866200bf` |
| `backend/gradle/libs.versions.toml`, pinned upstream reference only | `0d8edf1275c9cfe480b7edfc5f638e018819dd475547680757a6060e73e13732` |

Primary public mechanism references: SQLite WAL/foreign-key/transaction documentation and Git
update-ref/hash-object manuals. Read actual applicable sections; do not equate documented mechanisms
with a qualified Writhelm deployment. Keep third-party page snapshots/evidence outside public Git.

### Revision-2 inputs retained before correction

Revision-1 inputs above remain historical. These exact snapshots preserve the rejected storage
proposal and its dependent descriptions before S1 corrections; they are not approved designs.

| Input | SHA-256 |
| --- | --- |
| `README.md` | `92515454c3c3b4188d0e865f31d60c005b548fb66e88de903179aabce796f067` |
| `docs/specification.md` | `10e20626b301569a468c1ec91de4ce828505af5ccd75fa394844edd10124c61e` |
| `docs/design/knowledge-model.md` | `f6775a0a3d04842836111d09b5d128dfb13cb1c88fe0f50792b66c47e2cba069` |
| `docs/design/publication-recovery.md`, rejected P1 | `0cc53321053a05d8b650bf2113b8a20612c715fcf0cfc16ae72733d1938d9e09` |
| `docs/tasks/DESIGN-003.md`, revision 1 | `bea619360602ffb71eae893bc2ebc02a8821cc406f2a3c7620af08f6a5e456b0` |
| `docs/architecture.md` | `639774009b1492ce98fcec2c412495d9900b1e730d0d54c6570582f7f9c16c53` |
| `docs/contracts/accepted-knowledge.md` | `e9cba3802e427522ea157810bf5c72d7513f09ba2fb676dc363820c53f70d4b6` |
| `docs/publication.md` | `8adfca1373ffe3c8af848e67fa25badc3ddc2ab29f73d1fd89510b1a0183ae38` |

## Scope and constraints

- Read: listed sources, relevant earlier source findings and public primary storage documentation.
- Write: this task, historical `docs/design/publication-recovery.md` and new `docs/design/git-state.md`;
  status/link/traceability refinements
  in `docs/architecture.md`, `docs/design/knowledge-model.md`, `docs/contracts/accepted-knowledge.md`,
  `docs/tasks/DESIGN-002.md` and proposed paths in `docs/publication.md`. Revision 2 also permits
  aligning README and `docs/specification.md` with S1. Local external snapshots only.
- Decide: derive protocol invariants/failure cases and recommend P2 within accepted S1. Owner selects
  its atomic publication unit/concurrency/recovery realization, not whether SSOT is required; authority
  provisioning, exact wire/index grammar, concrete limits and test schedules remain later design.
- Dependencies: accepted R1/F3 and relevant D1-D18. No new merge engine, impact oracle or approval
  transfer. Old snapshots/history remain preserved; changed code cannot inherit old verification.
- No source/test implementation, baseline deletion, extraction, delegation, live model/embedding
  calls, expensive suites, staging, commit or push. No changes to other repositories or user config.

## Acceptance

1. Put all canonical content/authority/workflow/command state under Git versioning, with one exact
   acknowledgement/visibility transition. A derived index may hold no unique canonical fact.
2. Define durable command identity, changed-payload conflict, concurrency/replay/revoked-access
   behavior, pending recovery and uncertainty around a failed commit acknowledgement.
3. Enumerate faults before/after content and authority commits, missing/corrupt content/index,
   duplicate concurrent attempts, orphan subprocess/lock artifacts and startup store mismatch.
4. Preserve R1's clause locality and exact old/new consumers; no blanket accepted-document pointer.
5. Disclose publication-unit/concurrency and first deployment/process-crash limits, no Git power-loss
   or external-side-effect transaction illusion, retention cost and alternate viable designs.
6. Map finite checks to K-APP, distinguish cheap candidates from real-store/process qualification,
   and leave authorization/schedule of expensive runs explicit. No behavioral claims from a model.
7. Self-check IDs/links/input hashes/local-publication boundary and identify independent-review gaps.

Escalate a requirement conflict or actual consequential design choice conversationally, with a
recommendation and disclosed consequences. No request to read/ratify a Markdown bundle. Output:
proposed P2 protocol, finite failure/check table, concise owner decision and precise resumption point.

## Historical revision-1 checkpoint — not acceptance

Prepared `docs/design/publication-recovery.md` draft 1, SHA-256
`0cc53321053a05d8b650bf2113b8a20612c715fcf0cfc16ae72733d1938d9e09`.
P1 proposes the Git/SQLite split, content-before-authority ordering, scoped transactional effects,
durable command identity without automatic expiry, and a single-process/local-storage/process-crash
qualification boundary. It explicitly discloses retained-content/journal growth, blocked pending
Git locks, no automatic authority recovery from refs, and unqualified power-loss/backup behavior.

Author static self-check passed: six protocol-section IDs, twelve distinct fault-case IDs covering
all nine K-APP obligations, ten retained input digests, preserved exact R1 drafting source, local
links/whitespace/proposed-only ignored new paths, no staged files and no tracked backend changes.
A simple private-identifier scan found none; it is not a complete publication audit or semantic proof.

Read five official SQLite/Git mechanism references. Three SQLite HTML snapshots were retained
outside the public tree. Direct Git HTML retention returned HTTP 403; the versioned official manuals
were successfully read through webfetch, but no standalone HTML snapshot is claimed for them.
Observed Git version only (2.43.0); no store/crash behavior, selected-driver execution, expensive
suite, live model call, extraction, independent review or publication was performed.

P1 was not accepted. Owner requirement S1 rejects independent canonical SQLite authority and insists
that versioning/consistency be structural through Git SSOT. The earlier conditional framing of this
requirement was incorrect. Preserve the proposal as evidence of what changed, not a viable active
design. R1 remains accepted; concrete Git publication realization is the current subtask.

## Revision-2 result and resumption

Recorded S1 as an owner requirement and aligned README, specification, architecture, R1's storage
clarification and contract links. Marked P1 historical/not accepted rather than silently treating it
as approved. Preserved its exact source bytes in revision-2 inputs before adding that status banner.

Prepared `docs/design/git-state.md` draft 1, SHA-256
`0ef89467b2b32ec9538ab4b10f134d55fe9dbe269625bfbcc352a6afced2aa17`.
Proposes one private service-data repository/root, typed canonical records and command outcomes in
one complete state commit, structural validation, expected-old publication, exact-head reads and no
required SQLite in M1. A future index holds no unique canonical facts. Explicitly handles receipt /
commit self-hash avoidance and distinguishes storage publication from product approval.

Author self-check passed: seven protocol-section IDs, nine derived case IDs referencing all nine
K-APP obligations, eighteen retained input entries across both revisions, local links/whitespace,
simple private-identifier scan, ignored/proposed-only new path, no staged files or tracked backend
changes. This is not an independent review, a complete private-content audit, Git crash testing,
projection qualification or measured throughput. Read commit-tree/update-ref primary manuals;
no implementation, model call, extraction, expensive run or publication occurred.

The owner acknowledged the conversational P2 architecture proposal and asked to continue. Recorded
only its disclosed scope: private instance Git/root, complete state plus operation/result, structural
validation, atomic expected-old publication, exact-snapshot reads and derived-only optional indexing;
M1 need not introduce SQLite. Preserved the exact draft source above before the status update.

Continue in `docs/tasks/DESIGN-004.md`: concrete canonical records/commands, authority/secret handling
and bounded store/runtime/test-checkpoint design. S1/F3/R1/P2's accepted scope is not reopened.
Single-root write serialization/history growth remain disclosed costs; power-loss/backup behavior,
external harness side effects and undisclosed field/credential choices are not approved guarantees.
No behavioral implementation or publication is authorized merely by this status update.
