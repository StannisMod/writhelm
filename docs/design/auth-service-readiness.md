# Auth-enabled service shapes and bounded readiness profile 1

Status: field-shape/fixture preparation under accepted K-AUTH revision 1; no implementation or
transport/security qualification. Task: [AUTH-001](../tasks/AUTH-001.md).
Contracts: [K-AUTH](../contracts/authentication-access.md),
[K-PUBLISH](../contracts/command-publication.md), [K-CODEC](../contracts/state-codec.md).

## Scope and exact representation

[Auth schema](../schemas/auth-service-v1.json) defines auth header/records, three service command-v2
variants and journal-v2 receipts with core/auth write descriptors. Its references to the existing
core v1 schema are resolved ONLY from pinned local bytes, never by network schema discovery. No
source/task/schema assigned to the codec worker changes. Existing core commands and fingerprints
stay version 1. New auth commands exclusively use envelope/fingerprint version 2; an old operation
sent with a changed version is refused, not automatically translated.

Physical auth entries are `auth/format.json` and
`auth/records/<family>/<prefix>/<key-sha256>.json`, derived per CD-01a. Header fields are
`formatVersion: 1` and `profileVersion: 1`. Families: principals/projects/credentials/grant-guards.
Registry versions in `store.json` must admit core 1/1, auth 1, journal 2 and supported admission
policy before a whole-store writer runs. No growing list of principals/projects/keys in the header.
Bootstrap enrollment is the fixed grant-guard key set. Credentials/core grants must reference an
enrolled same-project principal; removing a grant retains the guard. The principal registry is not
another capability store. Private data namespace versioning is distinct from the public source repo.

Credential IDs and immutable store/project/principal/verifier bindings never change. Revoke/rotation
changes only status, generation and one nullable successor link under AU-06. New replacement records
start active at zero, preserve identity bindings and never inherit extra capabilities. Owner/profile
records and enrollment keys are fixed in the first profile. Later registration/migration is not a
generic record upload. All UUID/digest/enum spellings and explicit-null rules are strict v1 laws.

Three new command payloads are explicit in the schema: exact target/principal IDs and generation
guards; replacement additionally binds the current grant-state digest and full capability set;
rotation adds a new credential ID/verifier, NEVER a raw secret or body-supplied actor. Hashes are
not automatically authentication material. Current trusted actor/credential are transport inputs.

## Journal v2 and non-schema invariants

Journal-v2 keeps the PB identity tuple and original logical outcome, adding
`authenticatedByCredentialId` and fingerprint version 1/2. Success `operation` and its typed `result`
must match the receipt key. For rotation the result has new `credentialId` plus `replacedCredentialId`;
for grant replacement/revocation it is the exact target ID. `domainReplayed` is the original core
observation, not a recomputed delivery flag. Auth mutations currently have no domain-level replay
shortcut: command replay recovers their original success, while a different command key is evaluated
normally and can fail a changed guard. No unsupported idempotency is inferred from equal-looking data.

Write descriptors carry namespace/family/typed key and nullable exact before/after SHA-256. Body
entries use family `bodies` and their content-digest key. Require actual changes (not equal hashes),
one descriptor per touched key, canonical generated-path order, and exact candidate diff, excluding
the receipt's self-digest. Only core grants can be removed by current authority commands; immutable
records/bodies remain. Revoke_access's auth guard update is included in the same receipt-v2 write
set; rejected commands have an empty set. Unknown namespaces cannot be dropped by an old writer.

Schema is NOT a graph/authority/temporal verifier. Exact outcome.operation equality to key.operation,
original authenticating credential/principal/project at H, same store/parent, targeted generation,
valid replacement lineage, grant-guard and owner liveness, canonical order and exact write-set
correspondence require explicit validators. A shape-valid result with a mismatched operation is not
a valid receipt. In particular, returning a correct-looking UUID cannot substitute for a retained
immutable result record or recover an orphan candidate as published success.
For self-rotation, the new same-principal credential can recover the caller's receipt. If a manager
rotates another principal's credential, recovery still uses the manager's active credential/key space;
the replacement target credential must NOT gain access to the manager's receipt merely by being the
result of that operation. This follows AU-04's principal-scoped replay, not a new privilege rule.

Rejected auth operations use stable `AUTH_*` domain codes from the schema. Pre-admission failures,
invalid/revoked credential, missing project/operation grant, resource exhaustion, key conflict and
unknown publication outcome are NOT durable domain rejections. Hence `FORBIDDEN` is not a receipt
rejection variant even though the pure reducer can return it. Validate access before protected lookup;
do not let a service wrapper convert access failures into canonical receipts or protected details.

## Independent fixture inputs

[Synthetic goldens](../schemas/auth-service-goldens-v1.json) contain a public fixed byte pattern, not
an actual randomly generated credential. Never register these values with a running service. They
freeze token encoding (121 ASCII token bytes), verifier framing/binding, absent versus empty grant
state and command-v2 fingerprint before auth code exists. Expectations were independently calculated
from AU-03/05/09 using standard byte/UUID/SHA-256 primitives, not dumps from production output.

Implementation tests must mutate store/project/principal/credential binding individually, check
base64url decode/re-encode equality (including unused final bits), refuse identifier/verifier-only
authentication and test all byte boundaries. Same principal under a new active key can recover a
receipt; the revoked key cannot. These fixture hashes do not prove entropy or human origin, and a
constant-time digest comparison alone does not certify full-request timing behavior.

## Development limits (not a hosted production profile)

| Boundary | Initial development value |
| --- | --- |
| Authorization field value | 512 bytes before splitting/trimming |
| Aggregate HTTP header bytes / header count | 16 KiB / 64 |
| Token itself | Exact AU-03 canonical structure, 121 ASCII bytes |
| Auth command-v2 encoded body | 16 KiB |
| Auth record/header encoded bytes | 16 KiB per file |
| Auth command/record depth / collection entries | 16 / 64 |
| Auth JSON tokens / decoded string bytes | 2,048 / 1 KiB per string |
| Journal-v2 receipt encoded file | 4 MiB |
| Receipt write descriptors | 4,096, AND encoded-file/token/resource limits |

Core commands retain their own core development profile (including encoded file limits). Every limit
applies together: a core command may fit its body/index budget but still need refusal if its receipt
cannot fit. Do not split a coherent approval into independent decisions or drop provenance to fit.
Restore uses supported historical format/resource bounds, not today's smaller new-write auth limits.
Malformed input consumes no receipt; resource unavailability cannot be misreported as a settled
domain rejection. Concrete time/retry/rate/concurrency budgets and Git sync/ownership settings remain
publisher/transport task inputs, not guessed numbers inserted by this schema preparation.

Transport implementation must bound headers before expensive body processing, disallow unqualified
content compression, restrict JSON to UTF-8, and reject duplicate credential sources without dumping
their values. Explicitly bind loopback literal addresses/Host+port and configured same-origin browser
requests; no wildcard CORS or implicit forwarded-identity trust. A token is never sent over a redirect
to a different origin. The initial profile's plaintext loopback/long-lived-key limitations remain
accepted, not falsely described as general TLS/OAuth conformance. No actual listener is run here.

Client/transport readiness must specify a concrete protected secret-store/helper path and native
presenter handling BEFORE real keys are generated; no browser-storage or model-prompt shortcut.
The public fixtures are not that helper and not permission to put owner keys into worker inputs.

## Executed shape/fixture checks and limits

The auth schema passed Draft 2020-12 schema validation. Twelve positive shapes cover header, all
four record families, all three commands, active/revoked credentials, success/rejection receipts and
core operations recorded in journal v2. 189 targeted missing/unknown field, wrong-version/actor,
active-successor, wrong fingerprint version, null/no-effect descriptor and forbidden/rejection-write
mutations were refused. This is shape coverage, not a complete semantic case matrix or auth tests.

Installed local JSON Schema tooling lacked the newer reference-registry module and its legacy
resolver mishandled nested opaque-URN fragments. The bounded check expanded exact references from
the two pinned local schemas in memory, without modifying them or fetching dependencies. This is
not qualification of a production schema resolver. Domain parsing will remain explicit strict DTOs,
not an unchecked generic schema-deserializer replacement. One intentionally mismatched receipt
operation was shape-valid to witness the explicitly remaining cross-field-validator obligation.

No auth runtime, real secret generation, network/restart experiment, paid model call, independent
review, production capacity or binary distribution was tested. These checks do not certify the
concurrent child's work or amend its accepted inputs. Record final schema/fixture/profile hashes in
AUTH-001, then prepare executable auth source scope against the completed and checked codec API;
publisher/transport integration has its own gates and cannot be declared complete from pure tests.
