# Durable command outcomes and atomic Git publication

Contract K-PUBLISH, revision 1. Status: scope approved; bounded internal mechanism/binding implemented.
Author tests/parent checks completed; independent store review and real crash/supervision gates pending.
Task: [DESIGN-007](../tasks/DESIGN-007.md). Dependencies:
[K-CODEC revision 2](state-codec.md), [K-CONTENT](knowledge-content.md),
[K-WORKFLOW](knowledge-workflow.md), [K-APP](accepted-knowledge.md),
[accepted P2](../design/git-state.md). No accepted representation/SSOT decision is reopened.

## PB-01 — Atomicity and deployment scope

One private bare service-data Git repository per instance, one publication ref `refs/heads/state`,
one service process and serialized publisher. Complete state C has exactly one parent H, the state
against which its command was validated. Readers resolve one immutable snapshot; publishing is one
atomic expected-old ref update H to C. Unchanged objects are reused, not copied into a giant JSON.
No canonical SQL, external reservation journal, per-document moving refs or writable raw-Git API.
This repository is not the public source repository or an overlay in client coding projects.

Qualification is ordinary process crashes on persistent local storage. Host/power loss, HA,
concurrent active service processes, remote filesystems, arbitrary operator writes, remote hosting,
disaster backup/restore and exactly-once external side effects are not qualified by this contract.
A later remote push is not the local publication point and cannot repair or undo an acknowledged
local command. GitHub/GitLab suitability is a separate hosting profile, not an intrinsic guarantee.

## PB-02 — Store identity, namespaces and genesis

`store.json` contains `formatVersion: 1`, a fixed `storeId` UUID, the repository's `gitObjectFormat`
(`sha1` or `sha256`) and a bounded namespace-version registry. It never enumerates business records.
Store identity is checked against operator deployment binding on every open; that binding is not
a second authority-policy database. Git OIDs carry their algorithm and canonical hex width; they
are distinct from K-CONTENT SHA-256 and stable domain IDs. No current commit embeds its own OID.

Initial namespaces are `core/` (K-CODEC), `commands/` (this contract) and `policy/` (versioned
admission limits). Core grants remain the only implemented grant policy; do not mirror them in a
second namespace. Namespace registry versions must match their headers and supported readers/writers.
Unknown namespaces or incompatible versions block a whole-store writer, not a silent lossy rewrite.
Adding real credential records requires their separately approved namespace/schema and auth contract.

Explicit initialize is allowed only for a new empty designated store, with operator-supplied store
identity, valid initial core grants and concrete admission limits. Genesis has no parent or command
receipt; its typed contents, not a commit message, establish the initial state. This does not invent
principals/rights from role labels or settle credential issuance. Existing data, ambiguous interrupted
initialization or missing state ref on an existing store is an explicit recovery condition, never an
automatic empty-store bootstrap. Runtime open cannot invoke initialize as a fallback.

`policy/` admission records are small fixed-schema versioned files initialized in genesis. Operational
memory/time budgets are not authorization policy and may be stricter locally. They cannot change
domain identity, historical readability rules or canonical outcomes by pretending exhaustion is a
business rejection. No general policy/grant replacement command is added to the current core here.

## PB-03 — Command identity and admission

Identity is `(principalId, projectId, operation, requestKey)`, scoped to this `storeId`. It is not
just a request UUID, body-supplied actor or transport correlation ID. Use exactly CD-06's versioned
fingerprint, including payload IDs, body bytes, sequence order and guards. A different operation is
a different identity even with the same request UUID. Principals cannot adopt another's key space.

Strict bounded decoding/canonicalization precedes domain execution. Authenticate and authorize
against current captured H, using the operation capability from WF-01. The pure adapter accepts an
already trusted actor; this is not credential verification. Network exposure requires the actual
credential boundary. Before disclosing any prior result, recheck the caller's current applicable
project/operation access; do not use grants from the receipt's historical basis.

Malformed/unsupported/oversize-at-admission or unauthorized input allocates no receipt and makes no
business change. Identity conflicts do not overwrite the original receipt. Resource exhaustion,
storage failure and retry-budget exhaustion are not terminal domain outcomes. They yield explicit
unavailable/unknown results according to whether publication may already have occurred.

## PB-04 — Immutable bounded receipts

Store one receipt per command identity at `commands/receipts/ab/<key-sha256>.json`. Derive the key
with CD-01a's framing rule using family `command-receipt` and typed key containing the identity tuple;
embed and verify the complete key. Each record uses CD-02/03 strict/canonical encoding and a bounded
envelope `formatVersion`, `family`, `key`, `payload`. A different embedded key on a hash collision
fails explicitly. No append-to-one-file command log or globally growing receipt index is required.

Receipt payload has:

| Field | Meaning |
| --- | --- |
| `storeId` | Bound private-store identity |
| `fingerprintVersion`, `fingerprint` | `1` and locally computed CD-06 SHA-256 |
| `validatedAt` | Typed Git OID H, the published prior state used for this attempt |
| `outcome` | Tagged immutable logical success or terminal domain rejection |
| `writes` | Exact ordered core write descriptors: family/typed key, before-record SHA-256 or null, after-record SHA-256 or null |

Write descriptors sort by generated core path, contain no receipt self-digest and include actual
core metadata/body changes only (body keys are digest keys). Hashes here cover exact encoded bytes,
not Git blob framing. Current operations do not delete immutable records/bodies; revocation may
remove its scoped grant. An empty write set is valid for domain rejection or a successful domain
replay. No free-form result text, raw credentials, HTTP status, timestamps, current eligibility flag
or candidate C OID is canonical result material. Receipt files obey explicit encoded-size limits;
admission must account for receipt/write-set overhead as well as the command and core records.

Success outcome contains operation tag, the original typed result identity (revision key, request ID,
resolution request ID, assessment ID, manifest ID or revoked principal ID), and `domainReplayed`.
Results referencing domain records resolve exact immutable records, not mutable heads/latest policy.
Rejection contains the stable typed WF problem code and no business effect. Historical result access
does not reclassify a previously successful manifest as currently executable.

Receipts retain the fingerprint, not a second copy of the full command/document/packet. Matching
retries supply command material and recompute the fingerprint. A fingerprint alone is not a full
command reconstruction/audit transcript; effect attribution uses retained records, `validatedAt`
and append-only history. Full rejected-command text is not promised. This is consistent with P2's
receipt requirement and avoids turning the journal into an unbounded payload-duplication archive.

## PB-05 — Exactly one logical result per published identity

After current access checks, lookup only in published H:

- Same identity and fingerprint: return the original logical outcome; do not execute its business
  transition again or publish a second receipt. This includes recorded terminal rejection.
- Same identity and different fingerprint: return identity conflict without replacing the receipt
  or changing business state, even if the new payload would otherwise be valid.
- Absent identity: evaluate the exact command against H. Publish success effect plus receipt, or a
  terminal domain rejection receipt with no core changes. For this initial profile terminal WF
  failures after access/admission are recorded, not returned as durably settled without publication.

A prior rejection does not turn into success because state later changes; a genuinely revised
attempt requires a new request key. No automatic TTL expiry or ordinary receipt/history deletion.
Capacity limits may refuse further writes, not silently forget old identities. Compaction/retention
requires another explicit contract preserving or revising these guarantees; finite file sizes do not
bound total repository growth.

WF-03 resolution replay and command replay are distinct. A new command key for the same exact
answered request may get the core's original resolution with `domainReplayed: true`, no new approval,
and its own receipt. Same-key retry then returns that same logical outcome with transport envelope
`commandReplayed: true`. Do not recompute `domainReplayed` on the command retry. Different principals
still obey WF-03's original-answerer rule even though their command key spaces are separate.

## PB-06 — Candidate validation and preservation

Using validated H, calculate the exact allowed write set from the typed core transition. Preserve
all old immutable core records, receipts and bodies; update only the operation's permitted mutable
keys. Preserve the store header, policy and unrelated namespaces. Reject generic subtree replacement,
caller-supplied patches or a candidate that grants itself authority through its resulting policy.
New file keys/digests/references and all affected CD-05 invariants must agree. Body key coverage
remains K-CODEC's core-only equality; receipts do not add stray bodies for rejected proposals.

State C contains this entire result and its receipt together; rejection-only C preserves the core
tree exactly. Every non-genesis published commit introduces exactly one new receipt. Its `validatedAt`
equals the parent H; declared writes equal the actual core diff; its outcome agrees with the typed
transition. A successful domain replay introduces no new business effect. No timestamp/message
replaces those bindings. Standalone receipt structure cannot prove historical access; validation
against H and trusted published history supply that temporal guarantee.

Ordinary writes check affected records/dependencies and share trusted unchanged objects per CD-05a;
they do not rehash every historical body or replay the entire journal. Validation cannot skip known
dependency blockers or project checks. Full startup validation and bounded audit remain distinct.
Receipts are cumulative in the current tree so normal identity lookup needs no history scan.

## PB-07 — CAS conflict and unknown publication outcome

Create objects/commit privately; only expected-old update of the publication ref makes them visible.
Confirm publication under the qualified runtime before acknowledging a canonical outcome. If H
comparison fails, capture the new head, recheck current access and inspect its receipts first. A
matching receipt wins; changed fingerprint conflicts. Otherwise reevaluate the SAME original typed
command against the new state and rebuild there if permitted, preserving unrelated updates.
Never reuse an obsolete full candidate tree or replace exact targets/guards with newer ones.

Use a positive configured retry/time budget; exhaustion without a possibly running publication is
unavailable, not a recorded domain rejection. A lost subprocess result/response is outcome unknown
until the published line is inspected. Never report definite rollback from a timeout/exit error.
If a publication child may still run, retain the publication barrier or explicitly fence/reconcile
it before treating absence as final or allowing an incompatible retry. Its only permitted update
remains expected-old H to C. Do not blindly delete locks or kill unrelated processes to make progress.

A command timeout/cancellation does not promise rollback. If C or a later published descendant
contains the matching receipt, its result is committed even if the caller never saw acknowledgement.
A receipt found only in an orphan candidate, temporary ref, reflog or arbitrary branch proves no
published success. Published history is append-only with no force-reset rollback.

## PB-08 — Reads, restart and integrity

For an ordinary exact read capture current published A, check current access in A, then resolve
requested immutable domain refs at that captured state. Historical commit reads, if later exposed,
still authorize from current A and accept only supported published ancestry of the same store;
they do not expose raw object IDs/paths as a bypass. Concurrent revocation linearizes after or before
the read's captured authorization state; the contract does not promise cancelling already returned data.

Response envelopes may name current/read basis and `commandReplayed`, distinct from original logical
outcome. A publication commit may be returned only if confirmed/recovered; finding the original commit
where a receipt appeared uses trusted retained history or a derived index. Bound that optional lookup;
if unavailable, do not invent an OID, substitute today's commit as the original, or reexecute a command.

On startup validate designated store identity, object format, publication root, supported whole-store
versions and complete current core/journal structure under restore budgets. Verify receipt result
references and that declared historical bases exist on retained published ancestry. Historical write
descriptors refer to their original parent/result boundary, not today's changed grant/head values.
Temporal authorization auditing of every historical transition is a separate audit, not reconstructed
by applying today's grants/blockers to old records. In-process bases validated by this publisher can
reuse unchanged objects; unrecognized head movement requires reconciliation/full validation.

Missing/corrupt objects, wrong identity/root, unsupported versions and incomplete receipt/effect
bindings fail explicitly, not latest/worktree/empty-store fallback. Resource insufficiency is an
availability failure, distinct from corruption. Recovery preserves the last readable canonical state;
there is no automatic force reset, cleanup of ambiguous locks or reconstruction from diagnostic logs.

## PB-09 — Runtime and verification gates

Before implementation pin Git/JDK/dependencies, positive file/command/receipt/retry/restore budgets,
supported object format/backend/filesystem, object/ref synchronization settings, process ownership,
publisher lock/supervision and restart runbook. Git calls use argument arrays, byte-preserving object
IO and controlled config/environment; no arbitrary inherited hooks, filters, alternates, replace refs
or client-selected refs/paths. Prevent a second active instance with a qualified exclusive-owner
mechanism; an in-process mutex alone does not cover restart with a surviving publication child.
Failures at these boundaries block the operation; no unqualified durability or isolation claim.

| Cases | Required outcome / smallest boundary |
| --- | --- |
| Same key, formatting-only equivalent command, changed field/body/guard, other principal/project/operation | Original logical outcome or scoped identity conflict/denial; no duplicate business effect; focused command tests |
| Terminal rejection, later valid state, retry with same/new key | Original rejection retained for same key; new key re-evaluates; malformed/denied/resource failures create no false durable rejection |
| WF answer replay under new command key, then same-key retry | One approval, distinct receipt where needed, immutable `domainReplayed`, separate `commandReplayed` |
| X approval with unrelated Y update; targeted stale/ABA guard; blocker/revocation during CAS retry | Preserve Y and exact X target, reject changed relevant preconditions/access; no stale-tree overwrite |
| Missing/forged receipt counterpart, altered prior immutable record, extra/deleted namespace, oversized receipt | Explicit structural/transition refusal, no partial publication; exact write-set assertions |
| Crash before objects, before/during ref update, after update before acknowledgement; surviving child | Whole old/new effect+receipt, unknown outcome reconciled from published line; real Git/process boundary |
| Orphan receipt, cache deletion, missing/corrupt root/object, wrong store/version, exhausted restore budget | No orphan promotion or empty reset; canonical replay survives cache loss; integrity versus unavailable distinguished |
| Old C1/new C2, explicit mixed inputs, X-only approval, revoked actor, later blocker across restart | Exact content/authority/provenance preserved; current access/new-use checks hold; one assembled journey |
| Many small records, receipt overhead/file cap, ordinary unrelated-body read counters | No growing monolithic file; oversize refused; normal mutation does not read/hash unrelated historical bodies |

These are obligations, not executed tests. Cheap pure checks come first; classify narrow real Git
checks by measured cost. Real process faults/assembled behavior remain planned M1-C1 gate-holding
coverage, not automatic expensive runs per edit. No model call, worker execution, public push,
host failure, backup qualification or independent verification is authorized by drafting this contract.

## Approval provenance

The owner explicitly approved the disclosed K-PUBLISH revision-1 scope after the author consistency
review. The pre-status-update contract bytes have SHA-256
`a0ec392d3cb878a49de7864616dac9d05b3efaa7d98fff45cbdecb2dc0a80150`.
This accepts PB-01–09 semantics, including receipt-only domain rejections, current-access replay and
the stated process-crash profile. It does not certify behavior, approve unspecified schema/runtime
details or credential deployment, authorize a public push or waive executable-task readiness gates.

## Current implementation status

STORE-001 implements the bounded local Git/journal-v2 mechanism with the approved AU-09 extension;
APP-001 binds actual auth/core evaluation and namespace/provenance checks. STORE-002 adds the validated
H-derived new-publication history-count guard, without a schema/retention/default-limit change.
Final affected-dependency run: 57 store + 23 unchanged app cases passed; parent inspected source and
exact evidence. Prior terminal-CAS and portable-root regressions are retained. PB-01–09 are unchanged.
Unknown outcomes still fail closed behind a recovery barrier. No automatic marker clearing or
qualified surviving-child recovery/runbook, real crash test, HTTP/MCP service or M1 acceptance exists.
