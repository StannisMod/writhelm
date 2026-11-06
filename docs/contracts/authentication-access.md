# Initial authentication and project access

Contract K-AUTH, revision 1. Status: scope approved; pure auth/internal publication binding implemented.
Author/parent checks and bounded non-author service review completed; installed-client/operational
qualification remains pending.
Task: [DESIGN-008](../tasks/DESIGN-008.md). Dependencies: [K-WORKFLOW](knowledge-workflow.md),
[K-CODEC revision 2](state-codec.md), [K-PUBLISH revision 1](command-publication.md),
[T1/UI](../design/approval-interface.md), [A1 proposal](../design/first-slice.md).
This concretizes the previously unaccepted credential/deployment profile; it is not another ruling
on Git SSOT, clause-local authority or enterprise owner-origin attestation.

Subsequent H1 extension: [K-WORK-AUTH revision 3](work-authority.md) is agreed for explicit work
permissions and bounded delegated execution identities. The bootstrap-only/knowledge-only profile
below remains its original versioned baseline; old PLAN/READ or role labels are not silently promoted.
New enrollment/scopes/grants require the explicit H1 schema/namespace/command/journal profile before
implementation. Existing verifier/token/owner protections are not weakened by the extension.

## AU-01 — Trust and the initial supported profile

Authenticate possession of a Writhelm access credential, then authorize the operation against
current canonical project grants. A valid credential is neither owner status nor proof that a human
read a packet. No identity/rights from body actor fields, role labels, session names, localhost,
physical store access, MCP tool identity or a reported GUI success.

Initial profile: one service process, explicit local loopback HTTP/MCP clients, one bootstrap owner
principal per project, and separately provisioned planner/contributor/reader principals. Project and
principal enrollment is bootstrap-only for this slice; no user signup/ownership-transfer/SSO/OAuth
authorization server or password login. The same human may own several projects but each project
has its own explicit owner binding and project-bound credentials. Human identity is an operator
provisioning responsibility, not something the service infers from principal names.

T1 trusts compliant agents/harnesses not to take owner keys or call owner-only approval paths. Do not
place those keys in model context/worker environments, permit self-approval by policy or advertise
protection against deliberate same-user API/hook/browser/process bypass. Actual service grants,
structured exact answer validation and decision-information completeness remain mandatory. Stronger
human-origin isolation and execution sandboxing are separate future qualified modes, not hidden gates.

## AU-02 — Canonical authority and bounded records

All public credential verifiers, principal/project bindings, credential status and authority guards
belong in the private Git state with the core and command journal. Raw secrets do NOT. Introduce an
`auth/` namespace registered in the whole-store version header; each typed record is a separate
bounded sharded JSON file using CD-01a key derivation and strict CD-02/03 laws. No growing registry file.

| Family / key | Required material |
| --- | --- |
| principals / principal UUID | Fixed principal identity, never reused after revocation of its grants |
| projects / project UUID | Fixed `ownerPrincipalId`; profile version; no inferred default owner |
| credentials / credential UUID | Store/project/principal bindings, verifier version/digest, status active/revoked, generation, nullable `replacedBy` credential ID |
| grant-guards / principal+project | Monotonic generation for changes to that core grant, retained even if the grant is removed |

Core grants remain the SINGLE source of capability values. Auth records bind credential identity
and guard concurrent edits; they do not duplicate a second set of effective permissions. Exact
schemas/tags and auth namespace version are implementation inputs, not part of the current codec's
family set. Startup validates the links bidirectionally: served projects/owners/principals exist,
credentials reference their exact project/principal, replacement chains do not cycle/change identity,
grant guards cover managed grant identities and owner liveness meets AU-05.
Grant-guard keys are the fixed enrolled principal/project pairs from genesis: every core grant and
credential must refer to one; removing a grant never removes that enrollment/guard. No implicit
cross-project enrollment from a globally known principal ID.

The initial network profile reserves APPROVE and MANAGE grants to the bound owner principal of the
project. It does not add implicit permissions: the owner still needs each explicit capability used.
Other principals may receive only READ/PROPOSE/PLAN, in arbitrary explicit combinations. This is a
deliberately narrower INITIAL SERVICE profile, not a change to WF-01's pure library, which can model
multiple approvers/managers. Multi-owner/delegated authority administration needs a later profile.

## AU-03 — Credential origin, encoding and verifier

A trusted owner/operator-side setup helper allocates store/credential IDs and generates an independent
32-byte secret with the OS CSPRNG. Public IDs contribute no secret entropy. Each bootstrap client
gets a separate credential; do not share the owner credential with a planner. The helper retains
the raw secret in the human/client's protected storage and supplies only its public registration
fields and verifier to bootstrap/rotation. No production secret is generated through this design task.

Wire token is ASCII `wh1.<store-uuid>.<credential-uuid>.<secret>`: lowercase canonical UUIDs and
unpadded base64url of exactly 32 bytes (43 characters). Require exact decode/re-encode equality;
reject padding, alternate encodings, whitespace inside token and unexpected segments. Token version
is not a claim of any external token standard. The public store ID selects/binds the intended store;
a token for another instance must not authenticate even if someone copied a credential ID.

Verifier v1 is SHA-256 over K-CONTENT framing: length-framed domain
`writhelm.credential-verifier.v1`, then store/project/principal/credential UUIDs in that order as
two big-endian 64-bit values each, then length-framed raw 32 secret bytes. Principal/project come
from the credential record, never a body claim. Use a standard fixed-size constant-time byte compare
for the verifier; do not claim complete request timing indistinguishability from this primitive.
The verifier itself is not a bearer secret and cannot authenticate in place of the raw secret.

This fast verifier is for independently random high-entropy secrets, NOT a password hash. A supplied
32-byte-looking value does not prove entropy; trusted client generation is an explicit assumption.
Store values are verifier-only, including Git history/receipts. Clients must not persist tokens in
project files, model/tool arguments, URLs, browser localStorage/sessionStorage or normal diagnostic
logs. Use a protected client secret store or restricted non-project file; do not promise same-user
OS isolation merely from its pathname. Browser presentation gets no embedded token in HTML/assets.
This governs credential plumbing and normal logging, not a claimed universal detector of secrets
deliberately pasted into arbitrary document bodies. Raw-repository modification by a privileged
operator is outside API authority enforcement; hashing verifiers is not tamper-proof storage.

Proposed initial credentials are long-lived until explicit revocation/rotation, without automatic
expiry or refresh tokens. Possession permits replay until revocation; this is a disclosed operational
risk/convenience tradeoff, NOT RFC 6750's short-lived OAuth profile. Token theft requires explicit
revocation; closing a UI or restarting the service does not revoke a credential. Expiring credentials
can be a later versioned profile, not an unstated clock dependency in this slice.

## AU-04 — Authentication, authorization and reads/replays

Capture current published H. Resolve credential from H, check store binding, active status and
verifier; then check exact project binding and the operation's explicit core grant in H. Only then
construct the trusted actor for core handlers. A transport/MCP session is not a reusable cached
authorization: each request/side-effecting tool call rechecks current state. Derived verifier caches
bind exact store/H and cannot revive revoked status or missing grants.

| Operation | Required project capability |
| --- | --- |
| Exact content/request/resolution/manifest/authority reads | READ |
| `propose_revision` | PROPOSE |
| `request_approval`, `record_assessment`, `pin_consumer` | PLAN |
| `resolve_approval` | APPROVE, through the exact WF-02/03 answer path only |
| `revoke_access`, `replace_grants`, `revoke_credential`, `rotate_credential` | MANAGE |

Capabilities imply no other capability. No generic ADMIN/HUMAN shortcut and no direct approve-edit
endpoint. Credential-management reads expose only bounded nonsecret IDs/status needed by an explicit
MANAGE operation in the same project; they do not grant unrestricted core reads or raw store export.

Historical reads authorize against current H, not credentials/grants from the requested historical
state. Same-principal command replay may use a NEW active credential after rotation; the PB identity
tuple remains principal/project/operation/requestKey, never credential ID. Preserve original outcome
and original credential provenance, not today's presentation identity. The old revoked credential
cannot replay anything. A different principal cannot take over that key space or an answer's actor.

On CAS retry or an uncertain publication outcome, reauthenticate the SAME presented credential and
recheck current grants against the newly captured state before returning protected results or
rebuilding a candidate. Never substitute another credential automatically. A self-revoking command
may return its original success after publishing based on the prior authorized H; a later retry with
that revoked token is denied. Rotation therefore requires retaining the replacement secret client-side
before submission, so its active credential can recover a lost result under the same principal key.

## AU-05 — Genesis, grant updates and lockout

Explicit trusted initialization provisions fixed project owners, principals, credentials/verifiers,
grants and guard generation zero in the new-store genesis. Reopening is not bootstrap and cannot
reload a stale configuration over current grants. Refuse a served project without an owner having
explicit APPROVE and MANAGE and at least one active correctly bound credential. The operator must
retain the real secret: the service can validate verifier structure, not prove future possession.

`replace_grants` targets an existing bootstrap principal in the same project and supplies an exact
expected grant generation AND digest of current capability set/presence, plus the complete new set.
`revoke_access` retains its existing core semantics but atomically advances this auth grant guard.
Every replacement/removal advances that guard once; no wrap/reset/reused absent-grant generation.
This prevents a stale replacement from resurrecting rights after revoke/regrant ABA. Guard digest
framing and fields are frozen before auth implementation; no global state commit guard that would
conflict merely because an unrelated clause changed.
For this profile the grant digest is SHA-256 with length-framed domain `writhelm.grant-state.v1`,
project/principal UUIDs in that order, one byte for grant presence (0 absent, 1 present), then a
big-endian 32-bit count and length-framed UTF-8 capability names sorted by ASCII name. An absent
grant has count zero; an existing empty grant remains distinct through the presence byte. Generation
is checked separately, so remove/regrant cannot pass merely by restoring equal capability bytes.
Both credential and grant generations are nonnegative signed 64-bit; overflow refuses atomically.

Do not let grant replacement assign APPROVE/MANAGE to another principal or erase them from the fixed
owner in this profile. Credential revocation/rotation must leave at least one ACTIVE owner credential
for the project alongside those grants in the SAME candidate state. Check actual usable credential
bindings, not merely an owner label or a surviving grant without any live credential. This is a
structural anti-lockout check, not proof that an active credential's secret has not been lost/stolen.

No unauthenticated key-reset, force-bootstrap, hidden permanent operator master token or raw-policy
patch route. Lost last owner secret is an explicit operator recovery condition outside normal API;
this draft does not authorize history rewriting or pretend backups contain excluded client secrets.
Revoking project grants does not delete the principal, credential history, approvals or manifests.

## AU-06 — Atomic credential lifecycle

`revoke_credential` names an existing same-project credential and expected credential generation.
It transitions active to revoked, advances generation and retains identity/verifier/provenance. An
already revoked target under a new command key gives an explicit domain refusal, not reactivation.

`rotate_credential` names an existing credential, expected generation and a NEW credential ID /
verifier for the SAME store/project/principal. It can replace an active or revoked bootstrap-lineage
credential whose `replacedBy` is still null. Validate all new bindings before revoking anything.
Publish old credential revoked + advanced generation + `replacedBy`, new credential active at zero
generation and the command receipt together. A credential has at most one successor; later rotation
uses the successor, not a second branch from the same old identity. Reject duplicate/new-ID reuse,
foreign targets, stale generation, changed principal/project or malformed verifier atomically.

The managing principal can rotate another bootstrap principal's credential only within its own
project authority. Issuing a replacement does not grant that principal any capabilities and is not
generic principal registration. Rotation preserves principal-scoped command identities. The caller
retains the replacement secret before submission; service responses never return/recover raw secrets.
No period with neither old nor new credential, and no temporary two-independent-key publication
created by splitting rotation into separate commits. Canonical state changes are not transactional
with the client's external secret-file write; supported failure instructions must state that boundary.

## AU-07 — Transport, client handling and disclosure

Require exactly one Authorization header using the Bearer scheme and the AU-03 token. Scheme matching
follows HTTP case-insensitivity; token bytes are case-sensitive and canonical. Bound header size before
parsing. Reject ambiguous/multiple credentials, joined header lists and credential-bearing query/body/
cookie alternatives rather than using the first one. Do not trust forwarded identity headers.
Apply bounded header/Host/Origin/authentication admission before expensive command-body parsing;
authentication does not require accepting an unbounded JSON payload first.
Clients pin the configured service/store destination and must not forward Authorization across a
redirect to another origin. Bind only to configured loopback addresses; no wildcard listener fallback.

Restrict Host/authority to the explicitly configured host+port and browser Origin to the configured
same origin. Reject `null`/foreign origins and wildcard CORS; absent Origin is allowed only with the
same authenticated nonbrowser request handling, not as human attestation. No state change in GET or
an unauthenticated browser-navigation/elicitation link. DNS rebinding, CSRF-like browser access and
header enforcement need actual transport tests; a loopback bind alone is not authentication.

Local plaintext loopback HTTP is an explicitly LIMITED trusted-host development profile inherited
from proposed A1, not RFC 6750 TLS conformance or safe plaintext LAN use. Remote use remains disabled
until a separately qualified authenticated TLS ingress/client profile. Do not downgrade from an
expected HTTPS destination to HTTP, or treat a proxy header as transport qualification. Owner client
secret storage, local OS threats and native harness integration must be described honestly under T1.

Unknown/bad/revoked/foreign-store credentials return a uniform authentication failure without
credential-existence details. Valid credentials lacking project/operation access get a non-disclosing
authorization refusal before protected lookups; do not reveal another project's IDs or content.
Responses carrying protected data use no-store cache policy; disable auth headers/body dumps in
normal logs, exceptions, traces, telemetry and browser diagnostic surfaces. No application can
promise erasure from already authorized users' memory or a hostile operator's raw disk captures.
Authentication/authorization failure allocates no durable rejection receipt. Resource exhaustion
is unavailable and publication uncertainty is unknown per PB-03/07, not permission to retry blindly.

## AU-08 — Owner approval path under T1/U1

Planner requests are pending, not approved. A human-facing client fetches the exact canonical packet,
presents decision-relevant material and sends only its closed answer/item-anchored changes bound to
the same request/packet. Its separate owner credential is not a worker input, prompt, form field or
preauthorized link. No default assent, free-chat interpretation or shortcut direct approval call.

Service checks current APPROVE, exact refs/digests/guards and known completeness gaps/blockers using
WF laws, irrespective of which presenter sent the answer. A native form accept merely reports an
interaction; it does not authenticate the owner or prove comprehension. Under T1 compliant harness
instructions prevent deliberate model use of the owner channel; this is not advertised as technical
resistance to all hooks/scripts/browser automation. If no supported structured presenter exists,
leave pending or use a supported human-facing view, never weaken to free-text automatic approval.

## AU-09 — Explicit extension to current codec/publication schemas

These are PROPOSED versioned extensions, not a claim that K-AUTH already fits the six-command/core-only
schemas. No changes to the concurrently implemented CORE-003 inputs or existing CD-01–08/PB-01–09.

- Keep existing core records/commands and fingerprint v1 byte-identical. Add a separately supported
  auth namespace v1 and project profile v1; whole-store readers/writers must explicitly support it.
- Define service command envelope version 2 for the three new operations `replace_grants`,
  `revoke_credential`, `rotate_credential`, with the same common envelope fields and exact typed
  targets/expected guards/AU payloads. Existing core operations remain command v1. Fingerprint v2
  uses domain `writhelm.command.v2` and canonical semantic fields per CD-06. No cross-version coercion.
- Extend journal schema to version 2: credential ID of original authentication as nonsecret
  provenance; fingerprint versions 1/2; typed authority failure/result variants; write descriptors
  covering BOTH core and auth namespaces (not just PB-04/06's core-only set). Every normal command
  still publishes exactly one receipt and its complete effects against the same parent H.
- A current `revoke_access` command remains v1/core semantics, but the auth-enabled adapter also
  advances its auth grant guard in that ONE version-2 journal transaction. A no-op command replay
  advances neither guard nor business state. The trusted pure reducer does not authenticate itself.
- Startup preserves current grant/credential policy while retaining historical author/credential
  provenance; do not reapply today's policy to old receipts. Unknown auth/journal versions fail a
  whole-store writer rather than silently deleting them. Whole-service export includes the added
  namespace; core-only export remains core-only.

No service-data store has yet been implemented here, so this proposes the first auth-enabled genesis,
not an executable migration of live data. If journal-v1 stores are deployed before it, migration must
be separately specified/approved and validated; do not fabricate old credential provenance. These
extensions must be approved WITH this contract before auth integration. They do not reopen P2's
single-root atomicity or expand the codec child's assigned implementation scope.

## AU-10 — Derived finite verification and readiness

Cross-platform clarification under approved X1/K-RUN revision 2: authority, token/verifier bytes and
capabilities remain identical on Linux, Windows and macOS. CSPRNG access, protected client secret
storage and private-store permission enforcement require platform-specific qualification. A POSIX
mode check or Unix-only secret helper is not the Windows implementation. Do not weaken protection
or pass owner keys to a model to accommodate an OS. No token/schema/profile version bump is implied.

| Cases | Expected observations |
| --- | --- |
| Valid secret/binding plus each absent/wrong/revoked/malformed predicate independently | Exact authorized operation succeeds; each failed predicate denies without protected data or receipt |
| Principal label ADMIN/HUMAN, valid PLAN token answering approval, body actor/role, wrong project | No elevation or scope bypass; explicit all-valid owner answer succeeds |
| Guessed/public verifier, ID-only token, wrong store, changed binding, invalid base64url/padding | Cannot authenticate; secret entropy is generation responsibility, not proven from input appearance |
| Owner bootstrap/reopen, zero usable owner credentials, owner grant removal/foreign privileged grant | Valid genesis succeeds; unsupported reset, lockout and ownership/authority transfer refuse atomically |
| Stale grant replacement, revoke/regrant ABA, unrelated clause write | Targeted guard detects stale authority edit; unrelated knowledge changes do not force global conflict |
| Rotation, duplicate successor/new ID, already revoked/no-secret recovery, lost reply | Old denied/new active atomically, one lineage, same-principal receipt recoverable with new token; no raw secret response |
| Current-access retry and CAS conflict after credential/grant revocation | No old-policy protected replay or stale authorization; original historical provenance preserved |
| Owner self-revoke with another live owner key versus last-key revoke | Allowed former effect; denied last usable key; caller retry may be denied without pretending rollback |
| Duplicate/header/query/cookie credentials, Host/Origin/redirect/forwarded-ID misuse | Reject alternate routes; loopback/CORS absent-Origin never substitutes for authentication |
| No supported UI, cancel, mismatched packet, missing information or scripted form success | Pending/explicit refusal, no implicit approval; T1 adversarial-origin resistance is not falsely claimed |
| Restart, historical state, unsupported namespace/journal, corrupt verifier/credential links | Current auth enforced; history intact; integrity/version failure without empty reset |
| Malformed/denied flood and bounded resource failure | No canonical receipt allocation from unauthenticated traffic; bounded unavailable, no secret logs or unsupported DoS guarantee |

Before implementation freeze exact auth/command-v2/journal-v2 schemas, verifier/key fixtures, grant
digest/generation rules, positive header/request/token/record budgets and real transport/client secret
handling. Service readiness also needs the actual bootstrap, shared-handler and TLS/local profile
qualification, not just pure reducer tests. Cheap checks first; real auth/HTTP/MCP/restart and the
small two-consumer journey remain M1-C1 gate-holding. This draft authorizes no live credential or
paid inference experiment and reports no passed implementation/integration/independent review.

## Security sources and their applicability

Read [OWASP Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html),
especially entropy, identifier/verifier split, constant-time comparison, one-way storage, accepted
exchange mechanisms, transport and browser storage. Those support random-secret/verifier design,
not a claim that a same-user browser/harness isolates owner authority. Cookie-specific browser-session
recommendations are not imported as an implicit cookie login system here.

Read [RFC 6750](https://www.rfc-editor.org/rfc/rfc6750.html), sections 1/2.1/3/5: bearer possession,
Authorization header, failures, disclosure/TLS/replay risks. This draft borrows the header mechanism,
NOT an OAuth issuer or full RFC security-conformance claim. Long-lived keys and trusted-loopback HTTP
are explicit initial-profile differences requiring owner acceptance. No source's general TLS/secret
guidance is silently described as validating those narrower assumptions.

## Approval provenance

The owner explicitly approved this contract as "auth-1". That identifies K-AUTH revision 1, including
AU-01–10 and the AU-09 versioned extensions, not an already implemented AUTH task. Pre-status-update
bytes have SHA-256 `0679e7020afc25935f8fe47c42085401ba7eaf521a0c1a63bf282aa59f8e7fe4`.
Approval includes the fixed initial project owner, long-lived explicitly revocable credentials,
trusted-loopback development profile and T1 limitations. No raw credentials, public publication,
remote TLS/hosting qualification, semantic changes to core v1 or passing runtime result is inferred.
Exact auth/command-v2/journal-v2 field shapes and bounded executable tasks remain required inputs.

## Current implementation status

AUTH-002 implements the pure verifier, auth record/command-v2 codecs and scoped authority transitions;
its final targeted suite has 73 passing cases after the header-limit regression correction. APP-001
binds actual token checks, core policy, reducers, constrained grants and journal-v2 publication; its
23 unchanged cases also passed with STORE-002's corrected history-capacity dependency. Parent checked
source/evidence. Independent auth/store/app review, real keys/client provisioning, HTTP/MCP and
crash/supervisor qualification remain unpassed. AU-01–10 are unchanged; this is not service acceptance.

Later bounded review inspected actual auth/store/app and interface credential bindings. Its store
F-001 and native retained-profile IF-001/file-open IF-002 findings were corrected in separate author
tasks, independently rechecked and parent-accepted for exact candidate bytes. Local integrated
HTTP/MCP/native-component checks now use synthetic credentials and protected Linux files; no live
operator keys, installed-harness, Windows/macOS or recovery qualification is inferred. An earlier
native-component first-controls timeout remains unexplained. AU semantics/token formats are unchanged.
