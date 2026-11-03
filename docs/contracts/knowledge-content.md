# Immutable knowledge content and exact references

Contract: K-CONTENT, revision 1. Implementation task: `docs/tasks/CORE-001.md`.
Derived from accepted R1/P2, WH-03/04/06 and K-APP-04/05. This is the first pure-core subset, not
approval authority, a persistence protocol, a dispatchable task schema or a complete working service.
Local implementation only; no publication approval is inferred.

## Contract

**KC-01 — Exact UTF-8.** Valid UTF-8 bytes round trip without line-ending, newline, BOM or Unicode
normalization. Invalid UTF-8 or an unpaired surrogate in a text input is rejected, not replaced.
The content digest is SHA-256 of the exact stored bytes. Empty valid content is allowed.

**KC-02 — Complete structural index.** Clauses have unique explicit stable IDs and nonempty byte
ranges. Context is an ordered list of nonempty byte ranges, not dropped text. Clause and context
ranges together cover the entire body exactly once, with valid UTF-8 boundaries and no gaps,
overlap or out-of-bounds offsets. Each list is in document order; do not silently reorder invalid
input. An empty body has an empty partition. Heading text belongs to the addressed clause bytes.

**KC-03 — Identity binds interpretation.** A revision identity contains project, document and revision
IDs plus a digest binding its exact body and structural index. Changing bytes, clause identity/ranges
or context partition cannot silently reinterpret an old reference. Stable clause ID alone and
content hash equality do not prove semantic equivalence, independence or approval.

**KC-04 — Pinned reads, no fallback.** An exact clause/context reference resolves only in its named
revision with matching revision and addressed-content digests. Missing revision/selector or digest
mismatch is a typed failure, never latest/same-heading/worktree substitution. Adding a new revision,
including a reorder/rename, preserves old addressed reads. Duplicate revision identity is rejected,
not overwritten. Reads grant no approval and perform no authentication themselves.

**KC-05 — In-memory immutability.** Mutating caller-owned input arrays/collections or a returned byte
array cannot change existing content, indexes, catalogs, digests or reads. Exposed structural
collections cannot be mutated through a Java/Kotlin mutable cast. A new catalog does not alter its
parent catalog. This is object-boundary integrity, not isolation from hostile in-process code.

**KC-06 — Context preserves fragment boundaries.** Context reads return ordered located fragments,
not a manufactured concatenated rule. Its digest binds the ordered bytes and their fragment
boundaries; `[ab,c]` and `[a,bc]` cannot be identified merely because both concatenate to `abc`.
The revision/index identity also binds their original locations. No meaning/dependency inference.

## Digest framing v1 (engineering realization, not a wire/API schema)

Use SHA-256. Framed metadata uses big-endian 32-bit nonnegative lengths/counts/offsets, exact UTF-8
strings prefixed by byte length, and UUIDs as two big-endian 64-bit values. Every metadata digest
starts with its length-prefixed ASCII domain string; no delimiter-concatenation ambiguity.

- Structure: domain `writhelm.structure-index.v1`, clause count, each ordered clause's ID/start/end,
  context count, then each ordered context start/end.
- Revision: domain `writhelm.document-revision.v1`, project/document/revision UUIDs, raw 32-byte
  body digest, raw 32-byte structure digest.
- Context: domain `writhelm.document-context.v1`, fragment count, each exact fragment byte length
  and raw bytes. Positions are separately bound by the revision's index digest.

Changing this format requires a new explicit version; it is not an implicit adoption of the future
JSON record/command codec or Git object-hash format. SHA-256 identifies bytes, not semantic truth.

## Derived finite checks, before implementation

| Cases | Clauses | Required observations |
| --- | --- | --- |
| Valid empty/ASCII/non-ASCII/CRLF/final-newline/BOM variants; malformed/overlong/truncated UTF-8 and unpaired surrogate | KC-01 | Exact independent expected bytes/hash or explicit rejection; no replacement/normalization |
| Valid interleaved clauses/context; duplicate ID, negative/empty/reversed range, gap, overlap, out-of-bounds, out-of-order and mid-codepoint boundary | KC-02 | Correct slices, or refused invalid index; positive all-valid case included |
| Same body/different index, same index/different body, different IDs and unchanged clause at moved offsets | KC-03 | Bound revision digests differ where interpretation/identity differs; clause content digest is not authority |
| Old/new catalog reads after reorder/rename; missing revision/selector and tampered revision/content digest | KC-04 | Exact original bytes/identity or distinct typed failure, never fallback |
| Mutate input/output arrays, input lists and attempted mutable casts | KC-05 | Already constructed objects/reads remain unchanged; exposed collections resist mutation |
| Ordered context fragments and ambiguous concatenation shapes | KC-06 | Preserve original spans/bytes/order and distinguish different fragment sequences |

Tests derive expected bytes/errors from these laws, not snapshots copied from production output.
Pure local checks are the designated fast checkpoint for CORE-001; no Git process, harness, network,
provider, upstream suite or whole-product E2E is needed to establish this subset. Tests must assert
outcomes and negative failure reasons, not merely construction success or test counts.

## CORE-001 author-check coverage map

Implemented subset: `backend/server/src/main/kotlin/dev/writhelm/core/knowledge/`.
The named test classes are under the corresponding `src/test/kotlin/` package. This is a case map,
not line-coverage measurement, a minimum-suite proof, passing M1 or independent review.

| Clause | Executed case bindings |
| --- | --- |
| KC-01 | `ContentTest`: exact known SHA-256/ASCII, eight valid encoding regions, byte-different variants, seven malformed byte regions, three surrogate regions, safe slicing and canonical digest parsing |
| KC-02 | `StructureTest`: full interleaved Unicode partition, four invalid range regions, ten invalid partition/identity/order regions, empty/context-only positive cases, stable-ID validation |
| KC-03 | `StructureTest`: independent structure/revision digest framing oracles and body/index/project/document/revision changes; `RevisionCatalogTest`: heading and interpretation changes |
| KC-04 | `RevisionCatalogTest`: interleaved old/new reads, missing scoped identities/selectors, distinct digest failures, no duplicate overwrite and revision-local foreign-identity rejection |
| KC-05 | `ContentTest`, `StructureTest`, `RevisionCatalogTest`: copied arrays/lists, immutable casts, parent catalog preservation and located-context read integrity |
| KC-06 | `StructureTest`, `RevisionCatalogTest`: ordered located fragments, ambiguous-concatenation distinction, independent context framing oracle and immutable returned fragments |

Final targeted run: 53 JUnit cases, zero failures/errors/skips. These observations cover this assessed
pure subset only. No Git persistence/CAS, approval/credential/GUI/dispatch or independent verification
is established. Exact source/test evidence is retained outside public Git by the implementation task.
