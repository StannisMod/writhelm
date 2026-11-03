# Core codec implementation inputs — readiness profile 1

Status: bounded development inputs prepared after acceptance of K-CODEC/PUBLISH; not a production
capacity, hosting or security qualification. Task: [CORE-003](../tasks/CORE-003.md).
Contract: [K-CODEC revision 2](../contracts/state-codec.md).

## Exact field schema and identity

[Core schema v1](../schemas/core-codec-v1.json) specifies all twelve record families, the core header
and six command variants. The envelope is not a monolithic corpus file. Bodies are raw separate
entries, not schema JSON values. UUID-only record keys preserve the current library's global maps;
project context is resolved from their required record/request links, never guessed from a filename.
Schema variant tags are literal lowercase command/reference/answer tags; capabilities use existing
uppercase names. Decoders reject unknown fields/variants and missing required explicit nulls.

Structured maps use entry arrays: generation guards key by `subject`, manifest provenance by
`reference`, comments by `itemId`. They sort by CD-03 canonical key bytes, except comments use the
canonical item UUID order (equivalent for fixed lowercase UUID spelling). All other lists preserve
order. Check duplicate logical keys before building maps, even if duplicate entries have different
values. No `addressedDigest`, computed `references`, latest cursor or class name is serialized into
domain refs: reconstruct only defined derived values, preserving the supplied verifiable digests.

The schema validates shapes only. It does NOT reject all JSON numeric token spellings, duplicate
raw members, invalid Unicode, blank clause IDs/text, invalid partitioning, forged digests/graphs or
unordered map entries. These remain strict decoder/domain rules. `integer` in JSON Schema can accept
mathematically integral `1.0`; the token layer must still refuse it. A valid schema fixture is not a
valid restored workflow. Read inputs may have arbitrary JSON field order; canonical re-encoding is
deterministic. Logical sets/maps may arrive in different order; their canonical writer sorts them,
while duplicate elements fail. Export entry order follows CD-01b; no blanket list normalization.

[Independent goldens](../schemas/core-codec-goldens-v1.json) freeze exact canonical control escaping,
Unicode, set ordering, command framing and raw-body hashes before production code exists. They are
synthetic, not a serialization dump approved after a round trip. Domain/graph test cases must still
derive from K-CONTENT/K-WORKFLOW, including revoke/blocker/history/ABA and frozen provenance.

## Three distinct limit layers

These are initial DEVELOPMENT qualification values; no GitHub hosting/profile is selected. MiB
means 1,048,576 bytes. Larger values or production workloads need separate resource qualification.

| New-write admission value | Development limit |
| --- | --- |
| Exact body bytes | 1 MiB |
| Encoded metadata file | 4 MiB |
| Encoded command envelope | 8 MiB, accounting for worst-case body escaping plus metadata |
| Decoded individual string UTF-8 bytes | 1 MiB; field names 128 bytes |
| JSON nesting | 32 containers |
| Entries in one collection/object | 4,096 |
| Total JSON tokens in one input | 100,000 |
| Index clauses plus context spans | 4,096 |
| References traversed for one affected operation | 16,384 edges |

All limits apply together: a body/packet fitting one limit may still fail another. Final encoded
file size is authoritative, not an estimate from source text length. These do not define the future
receipt/file/retry limits for a Git publisher. Collection cap counts before accepting the next entry;
token/string checks cannot wait until an unbounded tree has already been allocated.

Format/read semantics retain version-1 signed 32-bit nonnegative offsets/counts and signed 64-bit
nonnegative generations. Valid larger historical records are not corrupt merely because new-write
admission was lowered. Their restoration can still fail explicitly for insufficient resources.
No new format-level small limit is retroactively inferred from the development profile.

Initial restore/export resource budget: 64 MiB consumed entry bytes, 8,192 metadata entries plus
8,192 unique body entries, 65,536 reference edges and 256 nested provenance steps. A single JSON
record may consume up to 16 MiB resource bytes / 4 MiB decoded string bytes / 128 nesting levels /
16,384 entries per collection / 500,000 tokens. Total decoded text is budgeted to 32 MiB and parsed
nodes to 250,000 across an active restoration. These are consumed-input/working-representation
budgets, NOT a claim of exact JVM object size or constant-memory streaming. Full core model metadata
and body content may remain resident after restore; bounded temporary buffers must not duplicate the
entire corpus needlessly. Runtime heap exhaustion is never translated into corrupt data or approval.

Set implementation test process heap/resource caps in the executable task. Budget failures are
typed unavailable, not domain rejections; they cannot silently trigger empty-store bootstrap.
Schema-only validation is not used to enforce byte caps or traversal budgets. Test profiles use
small configurable N-1/N/N+1 bounds to detect fencepost bugs without repeatedly allocating production
maxima; the values above are not thereby claimed load-tested. Tightening new-write caps must leave
historical reads possible under a supported adequate read/restore budget.

## JSON dependency qualification

Select direct `com.fasterxml.jackson.core:jackson-core:2.21.7` only, already available offline.
Qualified JAR SHA-256:
`8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261`.
No databind, Kotlin reflection module, upstream workflow or SQL dependency is required for explicit
stream tokens/DTOs. This is a pinned candidate qualified for the listed functions, not a current
vulnerability audit, a latest-version claim or qualification of Jackson's entire API surface.

Use `StreamReadFeature.STRICT_DUPLICATE_DETECTION`, disable source-in-error inclusion, leave lax
JSON read features disabled, and configure `StreamReadConstraints` for depth, name/string/number
length and token count. A library character-count limit is an early guard, not the required UTF-8
byte-count rule. Raw input must be byte-bounded and strict-UTF-8 checked before semantic use. Escaped
names/strings need explicit paired-surrogate validation; input metadata BOM must be explicitly
rejected. Ensure one complete root, exact integer token spelling, range-safe numeric conversion and
all field/variant laws before constructing domain objects. Validate every name and string token,
including values an unknown-field error might otherwise skip. No generic deserializer coercion.

The library accepts escaped lone surrogates, `-0` and fractions: it is NOT a sufficient standalone
strict codec. A disposable bounded Java probe exercised the required wrapper refusals. The prototype
predecodes a small capped buffer for UTF-8 validation; production must preserve its explicit byte /
allocation budget and does not inherit a permission to materialize arbitrarily large input twice.

Writer settings: disable `JsonWriteFeature.WRITE_HEX_UPPER_CASE`, `ESCAPE_FORWARD_SLASHES` and
`ESCAPE_NON_ASCII`; enable `COMBINE_UNICODE_SURROGATES_IN_UTF8`. Supply `CharacterEscapes` forcing
every U+0000–U+001F to lowercase six-byte `\u00xx`, with standard quote/backslash escaping.
Emit validated keys in CD-03 order and validated values; the generator does not sort fields or
domain sets automatically. No pretty printer, BOM, root separator or final LF. Do not use deprecated
legacy feature settings where the qualified public feature API exists.

The artifact retains its Apache-2.0 license/notice and embedded FastDoubleParser/Schubfach MIT and
Boost notices. No binary/source was copied into the public tree. Full shipped dependency notice /
license and security qualification remains open before artifact distribution.

## Executed bounded observations

- Jackson candidate probe: 39 expected observations matched, final run using the nondeprecated
  public writer feature configuration, JDK 21, 128 MiB heap and two active CPUs. Includes positive
  parses, duplicate escaped-name refusals, malformed UTF-8, surrogate/number wrapper rules, one-root,
  small depth/token/string-byte limits and an independently written exact writer vector.
- The field schema passed Draft 2020-12 schema validation. All twelve record families/six operations,
  header and extra answer/context/empty-grant variants passed 22 positive SHAPE samples. 541 targeted
  missing/unknown field, type/range/capability-duplication/actor-injection mutations were refused.
  Some shape samples intentionally have fake digests/invalid domain graphs; no workflow validity is
  inferred from their acceptance. There is no Writhelm codec implementation under test yet.
- These are dependency/schema readiness checks, not the promised production-codec tests, a new run
  of the 90-case core suite, independent review, large-limit load test or Git/restart qualification.

## Next execution gate

Pin the schema/goldens/profile and their hashes in a revised executable CORE-003 task before codec
implementation, retain exact inputs, and name allowed codec/test files plus minimal knowledge/workflow
snapshot APIs. Contract-derived tests precede production implementation. Preserve private pure-state
invariants; do not use reflection, public arbitrary state constructors or a generic import endpoint.
No Git publisher, receipt persistence, credentials or network wiring is included in that task.
