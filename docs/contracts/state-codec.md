# Versioned core records, export and command codec

Contract K-CODEC, revision 2. Status: disclosed scope accepted; bounded pure implementation in CORE-003.
Author tests and parent source/evidence inspection completed; independent review/integration pending.
Concrete core schemas/development bounds are pinned in CORE-003; production capacity is unqualified.
Preparation task: [DESIGN-006](../tasks/DESIGN-006.md). No code, fixtures or tests implemented here.
Scope: the complete CORE-001/002 pure model plus typed command encoding needed by the future P2 publisher.
Dependencies: [K-CONTENT](knowledge-content.md), [K-WORKFLOW](knowledge-workflow.md),
[Git-state GS-01–05/07](../design/git-state.md), [FS-01/02/04](../design/first-slice.md).
Those accepted directions are not reopened. This draft does not accept the separate A1 deployment profile.

## CD-01 — Explicit formats, not runtime object serialization

Use explicit typed DTOs and validators, not reflection over private fields, Java serialization or
polymorphic class names. Envelope `formatVersion` is integer `1`; record variants have fixed type
tags. Unknown versions/types fail closed. No guessing, legacy fallback or automatic migration.
Nested records use this envelope's version; independently stored metadata carries its own envelope.
Changing these laws requires a versioned migration contract, not a permissive reader option.

Three representations are distinct: independently encoded storage records, a streaming export of
those records/bodies, and a bounded command envelope. No single growing state JSON is required.
Together the core records represent the complete existing workflow, not yet a complete service-data
tree. Store identity, credential verifiers, durable
receipts and Git layout are separate publisher/authority records when introduced; they must never be
silently discarded by calling this pure snapshot the whole service store. No SQLite is introduced.
Snapshot restoration is internal; no upload/replace-state client endpoint or authority bypass follows.

### CD-01a — Per-record physical layout

The future Git adapter stores one logical record per metadata blob and exact document bytes in
separate body blobs. A concrete core namespace is:

```text
core/format.json
core/bodies/ab/<body-sha256>.txt
core/records/<family>/ab/<key-sha256>.json
```

`family` is one of the inventory names in CD-04. The two-character shard is the first two lowercase
hex digits of the following digest. A record's key hash is SHA-256 over length-framed domain
`writhelm.record-key.v1`, length-framed family name and length-framed canonical JSON typed key.
The record envelope has exactly `formatVersion`, `family`, `key`, `payload`; verify embedded key,
family and generated path agree. Hash collisions cannot overwrite a different embedded key: refuse
the candidate explicitly. UUID-only record families still retain/validate their project in payload.
No caller-supplied filesystem path, raw clause ID as filename, symlink or alternate spelling is used.

`core/format.json` has only `formatVersion: 1` and `modelVersion: 1`; it contains no growing record
list, counters, global packet or credentials. The Git tree itself enumerates records. Unchanged
records/bodies reuse Git objects; an update does not concatenate history into an existing JSON.
Mutable heads/generations/grants are individually keyed records, not one global policy/cursor file.
Proposals and revisions remain separate records; this keeps their exact existing identities explicit.

Every metadata record AND body must fit explicit per-file admission limits below the selected
hosting profile's hard limit, with room for encoding overhead. A raw-document limit alone is
insufficient: large structure indexes, decision packets and manifests can exceed a metadata limit.
Measure the final encoded bytes before publication; refuse oversize inputs, never omit required
material or split a coherent decision into independently approved parts to fit. Chunked documents,
external object stores and Git LFS are not implicit fallback paths; each needs a separate contract.
This bounds individual file size, not repository history size or guaranteed hosting suitability.

### CD-01b — Streaming export, not another canonical store

The core export interface is a stream of `(typed entry key, exact bytes)` entries: the format header,
each metadata record and each referenced unique body. It is not one JSON array/object or a full
in-memory byte map. A transport archive/container is a later adapter choice, not a second SSOT.
Order entries by header first, then generated relative key in unsigned UTF-8 byte order. Consume and
produce incrementally; whole-state validated indexes may still require explicitly budgeted memory.
Byte stability means identical ordered entry keys/content, not unspecified archive timestamps/headers.

Capture the source Git commit H once; enumerate and read all core entries from H only. Export
completion requires successful source enumeration, exact entry coverage and an explicit successful
end of stream. No best-effort skip or successful truncated export. Reject duplicate/unknown entries.
Within the core namespace require body keys to equal referenced body digests; unrelated service
namespaces are outside the core exporter and must not be deleted by it. A whole-service backup
cannot be implemented by exporting only this namespace. This contract makes no backup durability claim.

## CD-02 — Strict bounded JSON

Decode strict UTF-8 without replacement; reject metadata BOM, malformed sequences and unpaired
surrogates in raw or escaped strings. Document bodies retain valid BOM and all other exact bytes.
Exactly one JSON value is allowed, followed only by JSON whitespace. Reject comments, trailing
commas, duplicate object members (including escaped spellings of the same decoded name), unknown
fields, missing required fields and wrong types. Detect duplicates before conversion into a map.

No string/number/boolean coercion. Integer tokens have JSON integer spelling only: no fraction,
exponent, negative zero or leading zeros. Nonnegative offsets/counts fit signed 32-bit integers;
generations fit nonnegative signed 64-bit integers. Reject overflow before narrowing/incrementing.
Generation overflow refuses the operation without changing state; it must not wrap an ABA guard.
UUIDs are lowercase fixed `8-4-4-4-12` hexadecimal strings; digests are 64 lowercase hex digits.
Enums use fixed documented tags, not ordinal numbers or case-insensitive aliases. Existing clause-ID
and decision-text validation remains unchanged. No trimming, newline or Unicode normalization.

All fields are required unless a variant explicitly excludes them. `before` and a context subject's
`clauseId` use explicit null; arrays/maps use empty collections, not omitted/null/default values.
Answer variants approve/reject exclude comments; changes-required requires its nonempty comment map.

Before implementation, bind three distinct positive limits profiles in its executable task:

- Admission limits for NEW commands/records: envelope bytes, per-file encoded metadata/body bytes,
  decoded string/collection sizes and nesting. Envelope budget accounts for JSON escaping expansion.
- Versioned format/read bounds: integer bounds and structural constraints supported for historical
  records. Tightening today's admission policy does not retroactively invalidate accepted records.
- Operational restore/export budgets: consumed bytes/entries/edges, working memory and traversal
  depth. Exhaustion reports resource unavailability, not corrupt canonical data. Do not call an
  incomplete restore successful or silently start an empty store. Restore may need larger budgets
  than a single mutation; do not impose a request-size cap on the whole corpus.

Enforce bounds during consumption, before unbounded allocation/recursion, with checked aggregate
arithmetic. Traversal must terminate under the budgets. Never truncate, omit context, skip a bad
record or return a partial state. An operator may refuse new growth without making existing data
unreadable. Reader/format support needed for accepted older records cannot disappear through a
routine admission-setting change. Test exact configured N-1/N/N+1; no throughput target is invented.

## CD-03 — Deterministic encoding and identity

Canonical JSON for each record or command is UTF-8, no BOM, whitespace or final LF. Object members sort by decoded field name
using Unicode scalar-value order. Strings emit literal Unicode except quote/backslash (escaped),
and U+0000–U+001F (lowercase `\u00xx`); slash is not escaped. Integers use shortest decimal form.
This is Writhelm's restricted format, not an unqualified RFC 8785/JCS implementation claim.

All model Lists remain sequences, preserving order even where current behavior happens not to
observe it: clauses/context spans, packet items/material/context/dependencies, assessment refs/links/
blocked refs, manifest selections and trace assessment IDs. Do not sort these during fingerprinting.
Only actual sets/maps are canonicalized: capabilities sort by tag; maps inside a record serialize
as sorted entry arrays keyed by canonical key bytes. Across records, use CD-01b's entry-key order,
not one growing JSON map. Each blocked-state reference is an independently keyed set-membership record.
Duplicate set elements or map keys fail; they are not silently deduplicated. Byte sorting is unsigned
lexicographic. Structured comment maps become entries sorted by item UUID; their text stays exact.

K-CONTENT and K-WORKFLOW v1 binary-framed digests remain unchanged. Recompute/check stored body,
index, revision, context, packet and request digests from validated material, never trust a supplied
digest or replace it with a hash of JSON. Canonical JSON provides deterministic storage and the new
command fingerprint only. JSON field order, whitespace and equivalent valid string escapes do not
change identity; changing an ordered sequence or exact body bytes does.

Use a maintained JSON library with qualified strict duplicate/Unicode/numeric handling and an
explicit deterministic writer configuration. Do not implement a new JSON parser. Library defaults
or runtime iteration order are not protocol definitions. Independent golden bytes/fingerprints must
remain unchanged across dependency upgrades. If the library cannot meet a law, escalate rather than
quietly broadening accepted input or changing the fingerprint. No library/version is selected here.

## CD-04 — Exhaustive pure-snapshot inventory

The following families are the exhaustive core inventory, not fields in one physical JSON object.
Each map entry/set member is independently stored using CD-01a; key/payload fields mirror the typed
values below. Internal maps and nullable selectors use explicit typed entries, not arbitrary JSON
object keys. Before implementation, freeze a machine-checkable field/variant schema and independent
golden encodings under this contract; adding a behavioral field needs review.

| Collection | Complete required material / identity |
| --- | --- |
| `revisions` | Project/document/revision key; body digest/byte length; ordered clause IDs/ranges and context ranges; structure/revision/context digests |
| `proposals` | Revision key and proposer principal; exactly one provenance entry for each revision |
| `grants` | Principal/project key and exact capability set, including an explicitly empty grant if present |
| `requests` | Request ID/project/proposer; every packet field from WF-02, exact ordered items/before/targets, contexts/dependencies/gaps; per-target generation map; packet/request digests |
| `resolutions` | Request ID, answering actor, closed answer/comments and exact ordered approved targets (empty for rejection/changes) |
| `approvals` | Exact reference and retained direct trace: origin, request, approving principal, assessment-ID sequence |
| `continuity` | Exact target reference, source reference and recording assessment ID |
| `assessments` | ID/project, recorded actor, ordered refs, rationale, links and blocked refs |
| `manifests` | ID/consumer/project/creator, ordered clauses/contexts/dependencies, assessment ID and complete frozen reference-to-trace provenance |
| `heads` | Stable subject key and exact current direct-approval reference |
| `generations` | Stable subject key and exact nonnegative counter; no resetting from document revision or latest hash |
| `blocked` | One exact-reference membership record per blocked ref, with assessment provenance resolvable in retained assessments |

A reference has a clause/context tag, complete revision key and revision digest, plus clause ID and
content digest for clauses or context digest for contexts. A subject key has project/document IDs and
nullable clause ID. Range ends are exclusive byte offsets. A trace is not a new approval record.

Body blobs are keyed by their K-CONTENT SHA-256, each with exact bytes. Equal bodies may share one
blob without merging revision IDs or approvals. Require the core blob-key set to equal the referenced
body digest set: no missing, substituted or unlisted material. Record decoding consumes bytes and
typed keys, not ambient file paths, mutable HEAD or a worktree; the adapter applies CD-01a/b.
No new timestamps, domain-parent lineage, credentials, tasks or evidence records are synthesized:
CORE-001/002 do not yet model them. A later service schema must add them explicitly as implemented.

## CD-05 — Restore transaction and structural validation

Full restoration parses and validates into isolated temporary state under restore budgets, with
bounded buffers rather than a second full byte copy. Expose a usable workflow only after the
entire graph passes. Any fault returns a typed failure and leaves the active state untouched. No
constructor-access escape hatch or general state import is exposed as a client operation.

Check every local constructor law and every declared key/digest/project/reference. Reject duplicate
identities even when payloads are equal, dangling/foreign refs, incomplete partitions, unknown
selectors, cycles, missing provenance and extraneous generation/provenance keys. Validate at least:

- Each packet has exactly its target-subject guards and actual contexts. Pending guards may be old;
  a legitimately stale pending request is preserved, not rejected or rebound to the current head.
- Each resolution names one request, has valid comment anchors and exactly its allowed answer effect.
  Each direct approval trace names a successful resolution containing that reference and actor;
  its origin is itself and its assessment sequence is empty. Retain the original trace on reapproval.
  The direct-approval key set equals the union of all successful resolutions' approved targets, not
  merely a subset whose surviving entries happen to have valid links. Rejected/changes answers grant none.
- Each continuity entry matches its assessment link, stable subject, equal bytes and framed context;
  sources resolve through retained authority records without cycles. A target may later also have a
  direct approval: preserve both records and existing direct-authority precedence, do not erase history.
  Continuity entries equal the union of recorded assessment links, with exact source/target and
  recording assessment; duplicate target mappings across assessments are invalid, not last-write-wins.
- Blocked-state refs equal the union recorded by assessments. Heads/generations have matching subject
  sets; successful approval resolutions advance each subject once. Per-subject stored request guards
  must describe the contiguous successful generations from zero, with matching before/target chain
  and final head/count, including return to an earlier reference (ABA).
- Manifests retain their original selections and trace chains. Their provenance keys equal the union
  of selected inputs; traces bind those exact refs to retained approvals and named assessment links.
  Require the original assessment coverage, actual contexts and supported mixed-revision shape.
  Do not regenerate historical traces from whichever authority path currently takes precedence.

Inventory correspondence is bidirectional: revisions and proposal keys are equal; resolution keys
are a subset of request keys with exactly one answer per resolved request; request guard keys equal
target subjects; heads/generation keys equal all successfully directly approved subjects; manifest
provenance keys equal selected inputs. Bodies, direct approvals, continuity and blockers satisfy the
exact equalities above. Empty families remain legal where the model permits them; absence of a
dependent independent subgraph cannot always be inferred from an otherwise valid standalone state.

Do NOT rerun historical commands against today's grants/blockers as a restoration algorithm. A
withdrawn grant or later blocker does not make an old attributable approval/manifest corrupt. After
restore, new reads/retries/commands check current policy as before; historical readability is not
execution eligibility. Preserve resolved retries, unresolved stale requests, old valid dependencies,
known blockers and generation guards exactly.

Standalone structural validity cannot prove the actor had authority at the time, that continuity
predated a later blocker, or which historical trace was first. It is not authentication, a signature
or a semantic audit. Trusted published history and validation of each new transition supply those
temporal guarantees. The future publisher validates new effects against prior H, preserves immutable
records and permitted mutable cursors/policy, and publishes only the command-derived candidate.
Neither a recomputed digest nor an arbitrary internally consistent uploaded snapshot grants authority.

### CD-05a — Three different completeness/validation guarantees

1. **Standalone restore:** validates internal structure and bidirectional correspondence of the data
   present. It cannot detect deletion of an entire independent document and all referring records,
   or substitution of a different internally valid state. Do not promise universal deletion detection.
2. **Export from H:** the trusted source adapter checks entry coverage against H, including successful
   termination. An importer alone cannot authenticate that this was the intended complete source
   without a separately trusted source identity/integrity envelope; no signature is implied here.
3. **New transition H to C:** the publisher must preserve all immutable prior records, enforce the
   exact permitted additions/mutable updates and retain unrelated namespaces. H identifies the
   validation basis. This is what detects unauthorized loss/rewrites relative to known prior state.

Full historical graph validation is a restore/startup/audit operation, not a required per-command
algorithm. Ordinary commands use the exact previously validated immutable H, current authority and
the proposed write set plus required references/dependencies, enforcing affected invariants and
preservation of untouched state. This relies on immutable content identity and a trusted validated
base, not a generic semantic change-impact oracle. Any validation cache is derived, bound to exact
store/snapshot/format identity and dispensable; an unvalidated base cannot acquire trusted status
through a cache miss, label or partial scan. Different H requires explicit preserved-state reasoning
or full validation before use. Bound affected traversal; refuse resource exhaustion explicitly.
The Git adapter must demonstrate that normal writes do not decode/hash every historical body or
revalidate the whole corpus. Restoring the existing in-memory model may still load all model metadata;
no constant-memory or production-scale capacity is claimed. Large-scale execution is separately gated.

## CD-06 — Typed commands and fingerprint boundary

Command envelope has exactly `formatVersion`, `requestKey` (UUID), `projectId`, `operation`, `payload`.
Actor comes separately from the future authenticated boundary, never from payload actor/role fields.
The operation's typed payload includes every applicable guard; no duplicated generic `expected` bag.
This is a concrete narrow realization of FS-02, not an endpoint implementation.

| Operation | Exact logical payload fields |
| --- | --- |
| `propose_revision` | `revisionKey`, `body` (exact valid text), `structure` |
| `request_approval` | `requestId`, `packet` |
| `resolve_approval` | `requestId`, `requestDigest`, `packetDigest`, `answer` |
| `record_assessment` | `assessmentId`, `references`, `rationale`, `links`, `blocked` |
| `pin_consumer` | `manifestId`, `consumerId`, `clauses`, `contexts`, `dependencies`, `assessmentId` |
| `revoke_access` | `principalId` (the subject of revocation, not caller identity) |

All nested project IDs must match the envelope. Supplied record IDs preserve the existing pure
function inputs; server ID allocation/credentials are not invented by the codec. Structural decoding
does not execute a command, check grants or decide whether a well-formed reference exists in H.

Command fingerprint is SHA-256 over K-CONTENT length framing of ASCII domain
`writhelm.command.v1`, then length-framed canonical UTF-8 JSON of exactly
`{formatVersion,projectId,operation,payload}`. The request key is the lookup identity, not payload;
actor is bound by the lookup tuple `(principal, project, operation, requestKey)`. Every supplied
record ID, guard, exact body and ordered field participates. No body-supplied fingerprint is trusted.
No hidden nondefault field may affect execution without entering this fingerprint/version.

The later command service must retain fingerprint and original logical success/domain-rejection
outcome with the effect in the same published state; matching keys replay after current access checks,
changed payload conflicts, no TTL reuse. A resolution's existing request-ID replay is not the durable
command journal. Receipt schema/runtime, publication commit envelopes, store identity, CAS/restart
and unauthorized/malformed request handling remain a separate bounded contract before Git code.
No receipt in C contains C's own OID. No exactly-once external execution claim.

## CD-07 — Failure taxonomy and finite acceptance obligations

Return typed classes distinguishing unsupported format, malformed encoding/JSON, invalid shape/type,
admission-limit exceeded, restore/export resource unavailable, duplicate identity, digest mismatch
and invalid reference/graph. A safe location/code
may identify the fault; do not echo full bodies, decision text or secrets. Do not conflate a corrupt
snapshot with a normal domain refusal or silently bootstrap empty state. Partial values never escape.

| Case family | Required observation |
| --- | --- |
| Canonical fixtures | Independent expected bytes/digests, reordered object fields/JSON whitespace/escape spellings yield one canonical encoding/fingerprint; ordered-list changes do not |
| Strict parser | Duplicate escaped-name key at every nesting level; unknown/missing/null/wrong fields; trailing data; malformed UTF-8/surrogates; unsupported version and integer overflows all refuse |
| Exact bodies | Empty, multibyte, BOM, CRLF/LF, missing/final newline, decomposed Unicode round trip; wrong/missing/extra blob or mismatched index/digest refuses |
| Whole state | Export/restore/export preserves ordered entry keys/exact bytes; two consumers read original versions; all supported next commands have equivalent outcomes, states and replay flags before/after restore |
| History/current policy | Restore after revocation/blocker, ABA, reapproval of a continuity target, reject/changes and stale pending requests; old traces remain, current forbidden use stays forbidden |
| Corrupt graph | Remove one required counterpart while keeping its referring effect, forge provenance, introduce cycle/foreign ref, alter guards/head/count or omit context: typed refusal with no partial replacement |
| Completeness boundaries | An internally consistent standalone subset is not falsely certified source-complete; omission versus source H and loss versus prior H are detected by their separate export/transition checks |
| Limits/immutability | N-1/N/N+1 for each budget including final encoded files/escaping, deep/long graph, multibyte bytes, mutated buffers/maps; admission refusal versus restore resource failure remain distinct |
| Historical limits | Tighten new-write limits after storing a valid larger record: restore/read under adequate supported read budgets still succeeds; new oversize writes refuse |
| Physical representation | More records add bounded independent files, not a growing root/index JSON; embedded key/path mismatch, duplicate/colliding keys, unknown core files, symlink and oversize records refuse |
| Validation cost | Instrumented affected-write checks avoid reading unrelated historical bodies; unsupported or unvalidated bases never bypass required validation |
| Command identity | Change each payload/guard/ID/body independently; formatting-only equivalence; wrong variants and actor injection; fingerprint sensitivity without authorizing any command |

Expected fixtures come from these rules, not dumps blessed from the encoder under test. Preserve
the existing 90-case suite as regressions. Checks are bounded/offline pure tests; no upstream suite,
network, Git crashes, model calls or large resource experiment is implied. Independent review and
M1-C1 integration remain separate gates. A test count is not proof of historical or semantic truth.

## CD-08 — Compatibility and readiness boundary

`formatVersion` selects physical/JSON representation; `modelVersion` selects domain record laws.
Initial reader/writer support is exactly version 1/1. Each release declares supported read versions
and its write version; unsupported stores fail explicitly without mutation. A writer must support
the entire store it changes and preserve existing data; it cannot convert unknown records by omission.
Unknown core families fail; unrelated service namespaces belong to the whole-store reader, not this
core codec. A broad whole-store writer must not claim compatibility from core-version support alone.

Default to read/write version 1/1 without rewriting old records. A new incompatible format/model
requires a separately approved migration, validated output and recovery plan. An older release that
cannot read the new version must refuse; downgrading the executable is not a data rollback. Migration
authorization, backup/restore qualification and older-reader support cannot be inferred from a version
number. Admission settings and library upgrades must not silently change version-1 interpretation or
canonical command fingerprints.

This contract delivers a codec/restorer for the CURRENT CORE, not a production-ready Writhelm store.
Implementation readiness requires the approved scope, exact record/variant schemas and goldens,
concrete three-way limits profiles, qualified parser/writer dependency and a versioned bounded task.
Git-publisher readiness additionally requires a receipt/store contract (durable command identity,
rejections/replay, genesis/store identity, whole-service namespaces, transition preservation, current
authority and acknowledgement/CAS recovery). Credentials/deployment retain their own approval gates.
No code implementing those missing guarantees is authorized by accepting codec semantics alone.

## Revision provenance

Draft 2 supersedes draft 1 (SHA-256
`c393606cd5a0118d57ebd8bb490eb1fc54b93d68cee44d178980aa78225bd51b`).
It incorporates the single-file-size concern and author review: separate representations, per-record
layout, restore versus affected-write validation, distinct limits, honest completeness guarantees,
bidirectional inventory checks, qualified canonical encoding and explicit version/readiness rules.
The owner subsequently accepted the disclosed revision-2 scope and requested the next contract plus
a consistency check. The original reviewed bytes are identified at SHA-256
`de93b44b1bc9c90d35d51a5782f826e41dfc2c29edadbbcb10a9ed9e6bc6085e`.
This status/provenance update and review appendix do not amend CD-01–08. Scope acceptance does not
approve unspecified schemas/credentials, new publication paths or implementation. Drafting/review is
not independent verification.

## Author consistency review — DESIGN-007

Compared CD-01–08 against the preceding primary contracts/design and existing model, not just their
titles. No conflicting requirement was found within this bounded core scope. This is author document
review, not proof of consistency for future schemas/implementations or an independent review.

| Source clauses | Codec correspondence and limits |
| --- | --- |
| KC-01/02/06 | Raw UTF-8 body blobs, exact byte partitions/context framing remain unchanged; command JSON escaping is decoded before applying the same content laws. File size measures encoded metadata as well as body bytes. |
| KC-03/04; WF-02/03 | Domain digests/references and request/packet binding stay binary-framed v1; record-key/command hashes are additional identities, not replacements. No latest fallback or new authority from encoding. |
| KC-05; WF-07 | Full restore exposes immutable values only after complete validation; affected-write validation preserves untouched records and cannot become a mutable import bypass. |
| WF-04/05/06 | Preserve subject generations/ABA, old dependency refs, both continuity and later direct approval, and frozen original manifest traces. Blockers/revoked grants do not erase old records. No historical command replay against today's policy. |
| WF-01/08 | Current core grants stay canonical; actor is still trusted input, not implemented authentication. No body-supplied role or credential scheme is introduced. |
| K-APP-01–05; R1 | Per-record storage does not turn clauses into separately maintained documents or whole documents into blanket-approved objects. Mixed inputs remain explicit assessed selections, not a semantic merger. Facts/evidence are not newly implemented by the codec. |
| K-APP-06–08; GS-01–05/07 | Codec alone provides no durable receipt, atomicity or crash guarantee. These are supplied by the separately proposed K-PUBLISH, preserving one private root, effect/result atomicity, historical state and current-access replay. |
| FS-01/02/04 | Strict JSON/field/set rules and typed payload guards realize the earlier proposed envelope; no generic `expected` bag is needed. Current payload operations are deliberately narrower than future credential/client operations. |

### Integration boundaries found and made explicit

- Core blob coverage is exactly revision-referenced bodies. The command journal must not put rejected
  command bodies into `core/bodies/` as unexplained extra data. K-PUBLISH retains fingerprints and
  bounded result references, not a duplicate raw input archive.
- A domain answer replay and a command-key replay are different observations. K-PUBLISH preserves
  the original domain result/flag while its response separately marks command replay; otherwise a
  restored result could change merely because it was delivered twice.
- Core export excludes future service namespaces. Only the whole-store writer owns the namespace
  registry, store identity and effect/receipt publication; it must preserve both core and journal.
- Historical basis/current read snapshot/publication commit are distinct. No receipt embeds its own
  commit OID; a current policy check cannot relabel a historical manifest as executable.
- Earlier design tables mention parents/evidence/credentials and broader authority commands not yet
  in CORE-001/002. This codec explicitly does not synthesize them or claim the full first slice done.

Remaining schema checks before implementation include exact tag/key definitions, maximum encoded
receipt/write-set sizing, current-versus-domain replay encoding, and supported old-record bounds.
They are readiness obligations, not discovered permission to alter prior semantic guarantees.

## CORE-003 implementation/evidence status

Implemented the bounded pure codec/restorer under task revision 4, including the narrowly authorized
typed generation-exhaustion preflight. Actual executed suite: 124 cases (90 unchanged regressions,
34 new codec/restorer cases), zero failures/errors/skips. Parent compared all changed/final source
hashes, original test/input/resource settings, executed log and nine matching current/retained XML
reports, and inspected source/tests without modifying them. This is not fresh-context independent
review, a second test execution or whole-service acceptance.

Coverage includes strict tokens/Unicode/closed shapes, independent canonical bytes/fingerprints,
all twelve record families, exact bodies/partitions/digests, immutable entry/snapshot boundaries,
incremental export and isolated restore, all-six next-transition state/replay equivalence, ABA/stale
pending requests, revocation/blocker history and original frozen traces after later direct approval.
Small configurable budget boundaries were exercised; default maxima were not load-tested.

The codec consumes typed entries, not a filesystem/Git store. Source-H completeness, mode/symlink
checks, prior-state preservation, journal publication, actual authentication and network handlers
remain later gates. Normal pure commands were not wired to full-history validation. Standalone
structure still proves neither past authority nor completeness of an independently removed subgraph.
CD-01–08 are unchanged by this implementation/status record; exact original contract inputs remain
pinned in CORE-003's retained manifests. New source paths remain local pending scoped tracking.
