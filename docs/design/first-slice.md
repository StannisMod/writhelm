# Concrete first slice — records, commands, authority and verification

Draft revision: 2. Status: proposed A1 profile, clarified key origin and human approval flow.
Draft 1 described credential checks but did not adequately specify the owner's approval action.
Owner direction U1 subsequently replaces free-text assent with structured GUI choices and item-local
text only for changes-required. Native UI capability, complete decision presentation and protected
human delivery are under investigation in `docs/design/approval-interface.md` (DESIGN-005).
Subsequent T1 accepts the shown structured layout as the starting point and selects cooperative
initial approval. Deliberate instruction-violating API/hook bypass resistance is not an M1 blocker;
do not claim it. Service grants, exact binding and completeness remain. Detailed visual refinement
is deferred. This unlocks bounded pure-core implementation, not an unrestricted credential/API rollout.
Task: [DESIGN-004](../tasks/DESIGN-004.md). Contracts: [K-APP](../contracts/accepted-knowledge.md).
Accepted architecture: [R1](knowledge-model.md) and [P2](git-state.md). No implementation/publication.

## Deliverable, not another product expansion

Build one bounded own-core flow: propose a structural contract, approve X without approving an
independent Y edit, preserve Y's original records, pin two consumers to exact inputs, and recover
command outcomes after supported process crashes. HTTP and MCP are two thin authenticated views of
the same command/read handlers. No model call, search/SQLite, worker runtime or second service.

The concrete records/codec below implement already accepted representation/SSOT directions; field
names do not need individual owner rulings. A1 is the remaining consequential initial authority /
deployment profile. Exact positive resource caps are required provisioning values, not invented
throughput promises or an excuse for unbounded request parsing.

## FS-01 — Canonical records and structural laws

All records use an explicit format version and stable IDs; server-generated record IDs are separate
from state commit OIDs. Documents remain whole-byte revision units. Records are typed data, not
free-form commit messages. Their proposed logical payloads are:

| Record | Required identity/content |
| --- | --- |
| Store/policy | Store ID, supported format version, configured limits, principals/credentials and explicit project grants |
| Document revision | Project/document/revision IDs, domain parent IDs, exact UTF-8 body blob/digest, structure-index blob/digest |
| Structure index | Unique stable clause IDs, exact UTF-8 ranges including headings, ordered context ranges, declared context/dependency references |
| Approval request | Immutable request ID/digest, exact prior/target refs/context and guards, one coherent proposed decision with consequences, proposer provenance; no approval merely from creation |
| Approval | Approval ID, exact request ID/digest, authenticated actor/credential provenance, exact prior/target refs/context, decision scope/outcome, original command ID |
| Assessment/continuity | Exact source/target refs, planner identity, considered changes/dependencies, original approval/check links and open findings/resolutions |
| Consumer manifest | Manifest/consumer IDs, exact selected refs/context/dependencies, authority/continuity and assessment refs; immutable after publication |
| Evidence reference | Evidence ID, exact original assessed task/content/tree identities, result/artifact references and limits; no new passing run through copying |
| Command receipt | Scoped key, versioned semantic fingerprint, operation, immutable logical outcome/effect IDs, actor provenance and prior validation basis |

Validate before publication: every required reference resolves in the candidate state/project; IDs
and selectors are unique; body/index digests agree; byte ranges partition clauses/context without
overlap, omission or invalid UTF-8 boundaries; provenance has no cycle; old immutable records are not
rewritten; selected prescriptions have applicable approval; known relevant conflicts block use.
An assessment is a claim with provenance, not semantic proof or authority to change a prescription.

For a new revision of unchanged Y, continuity points to original approval/check records rather than
manufacturing another approval or successful run. Reorder/rename retains IDs but must not silently
assign an old ID to a new obligation. Unsupported split/merge is refused. Context prescriptions
cannot acquire authority from a container label; factual claims still need evidence, not owner assent.

Strict deterministic codec v1: typed JSON metadata with fixed field/set serialization, strict UTF-8,
no duplicate/unknown fields or non-finite/floating-point offsets. IDs/enums/ranges have validated
types; timestamps are explicit strings, not approval ordering rules. Document body bytes are separate
Git blobs and are never newline-normalized. Transport decoding must reject malformed Unicode, not
hash a replacement-character version. Canonical serialization/fingerprint rules have fixture cases
before implementation; transport field order/JSON whitespace must not change command identity.
Duplicate-key detection must happen before parsing into a map that would silently discard an
earlier value. Each collection declares whether order is meaningful or canonicalized as a set;
fingerprinting cannot silently sort a sequence whose order changes behavior.

## FS-02 — One command boundary, shared transports

Command envelope: `formatVersion`, `requestKey`, `operation`, `projectId`, typed `expected` guards and
typed `payload`. Principal/credential identity is established outside the payload by authentication;
body-supplied actor/role fields are rejected. Unknown operations/selects do not route to generic writes.

| Command/read | Effect or observation | Authority |
| --- | --- | --- |
| `propose_revision` | Publish an immutable full body/index and receipt, not approval | Explicit proposal grant |
| `record_assessment` / `record_continuity` | Record planner findings/applicability mappings with exact provenance, never a new owner ruling | Explicit planning grant |
| `request_approval` | Publish the exact proposed ruling, subjects/context and consequences for owner review; no authority transition | Explicit planning grant |
| `resolve_approval` | Answer one pending request by exact ID/digest; approval, where authorized, applies only its enumerated unchanged subjects | Explicit owner approval grant |
| `pin_consumer` | Validate and publish the complete immutable manifest; not executable dispatch | Explicit planning grant |
| `read_revision` / `read_clause` / `read_manifest` / `read_approval_request` | Exact bytes/metadata plus explicit proposal/approval/history/request status | Current project read grant |
| `revoke_credential` / `rotate_credential` / `replace_grants` | Canonical authority changes for replay/revocation qualification; rotation publishes new verifier and old revocation together | Explicit authority-management grant |

An original logical result contains its record/effect IDs and outcome; transport envelope can add
publication/read commit and replay flag. No record in C embeds C's own hash. Denied requests reveal
no protected project content/IDs; no raw Git-object/ref filesystem API is exposed. Reads of old
manifests authenticate using current policy, not the old snapshot's credentials.

Supported commands go through one transition/validation/publication service. There is no generic
patch-the-canonical-files endpoint, mutation hidden in a read, or owner shortcut based on ADMIN /
HUMAN labels. Destructive project/document lifecycle, ownership transfer, full norm-withdrawal /
running-worker control, general facts ingestion and batch mutation are explicitly outside this M1
profile. Their requirements remain later gates, not silently satisfied by a historical-read fixture.

## A1 / FS-03 — Initial authority and credential profile (proposed)

Recommend an explicit service bootstrap and opaque high-entropy bearer credentials, not a new login
system/JWT issuer. One service process; first qualification binds to loopback HTTP/MCP. Remote use
requires a separately qualified TLS ingress/profile, not sending owner tokens over plaintext LAN.
Loopback is not authentication: all requests still require credentials. Restrict Host/Origin to the
configured local profile, reject cross-origin browser use and do not accept tokens from query strings,
cookies or body actor fields. HTTP and the private MCP profile use explicit authorization headers;
this does not claim an OAuth server or qualification of every harness's authentication integration.

1. A trusted operator initializes a **new empty** private store with project/principal/grant records,
   schema/limit settings and credential verifiers in the genesis Git state. Existing stores cannot
   be reset/reconfigured through bootstrap. The bootstrap input is not a maintained second config.
2. Credential secrets are independently generated with a CSPRNG (proposed 256-bit random values),
   not human passwords. The client/operator retains the secret; bootstrap/registration consumes
   only its credential ID/verifier. Store a domain-separated SHA-256 verifier of that high-entropy
   secret and compare presented credentials without data-dependent early equality exits. This is
   not a password-storage design or a proof that a human physically read an approval.
3. Principal identity, credential active/revoked state and grants live in current Git state. No secret
   plaintext in Git, command receipts, normal logs, model packets or tool arguments. Secret input /
   client storage belongs in a protected human-client channel; public fixtures use synthetic secrets.
4. Owner approval is sent by the human-controlled client with its separate credential. Planner /
   contributor / reader clients receive only their explicit per-project grants. Planning rights may
   record assessments/manifests, but cannot grant approval or change identity policy. Authority
   management is another explicit grant, not an inferred consequence of operational privilege.
5. Revocation and grant changes publish through the same Git command path. A historical verifier or
   old receipt cannot authenticate a revoked credential. Rotation uses a new credential identity /
   verifier; logical principal-scoped command replay remains attributable and current-grant checked.

Use an unambiguous fixed-width/versioned verifier input including credential identity, rather than
ambiguous string concatenation. Public lookup ID is not part of the secret's entropy and is never
accepted alone as proof. No real production credentials are generated by this design task.

The initial operator can provision one owner principal with explicit owner and authority-management
grants; no automatic elevation for a new project/client. Initial credential registration can occur
at bootstrap; this slice needs revocation/rotation/grant replacement, not a general credential-issuance UI.
Keep the bootstrap owner principal fixed in M1: grant replacement cannot silently transfer ownership
or create another owner/authority manager. Refuse disabling the last active owner/management credential
without an atomic supported replacement; recovery from lost raw keys is not an unauthenticated reset API.
No additional server-side secret manager is needed merely to validate these random bearer secrets.
Protecting raw human/client keys and consistent recovery of them remains an operator obligation.

Grounding: [OWASP Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html),
specifically entropy, server-side random-token verifier storage and transport protections. Those
sections support CSPRNG/identifier-secret separation and one-way verification for random tokens;
they do not certify this bearer profile or require importing a browser session/cookie system.

Service authority enforcement is not host sandboxing. M1 dispatches no live workers and does not
claim an unrestricted harness process cannot steal a locally accessible owner key. M2 must qualify
isolation, reviewer read-only access, subprocess restrictions and owner/store separation before
claiming those hard guarantees. The human owner key must not be placed in the agent environment.

### A1 clarification — where credentials come from

These are Writhelm access tokens, not model-provider keys and not yet a public/private signing-key
scheme. A proposed owner-side setup helper generates the random secret using the OS CSPRNG and
saves it in the protected human client. The operator supplies only the credential ID/verifier,
principal and explicit grants to new-store bootstrap; genesis publishes that identity/policy state.
The owner secret never has to pass through the model, a project file or Git. It is not inferred from
a chat role, an ADMIN flag or possession of the data repository path. Client generation is trusted;
the server cannot infer randomness/entropy merely by inspecting a supplied hash.

Limited planner/contributor/reader credentials are separate random values with explicit Git-versioned
grants, provisioned initially through the same trusted setup. No sharing the owner token with the
planner to let it "record approvals". Later rotation/revocation uses the authenticated canonical path;
initial setup is not an unversioned permanent source of rights or an unauthenticated reset mechanism.

### A1 clarification — the actual human approval path

1. The planner prepares exact proposed content and sends `request_approval` under planning rights.
   The canonical immutable request binds the selected clause changes, prior/target digests, required
   context, expected guards, plain-language decision and disclosed consequences. Neighbouring edits
   are explicitly outside the scope unless named. It is pending, not accepted knowledge.
2. A **trusted human-facing client/controller**, not the language model, fetches and presents the
   canonical request by its ID, rather than rendering arbitrary model-supplied display text for a
   hidden unrelated request. It presents what
   changes, the selected proposed wording/context and its consequences, without requiring the owner
   to open Markdown files. It binds the displayed decision to the request ID/digest. A misleading
   explanation remains a planner/review failure; a hash cannot prove that the human understood it.
3. The owner chooses accept, reject or changes-required in a structured view, never by free-text
   assent. Only changes-required exposes text under an exact selected clause/item. Bind the action
   to the displayed request/packet/item digests. A model assertion that "the owner said yes" or a
   generic free-answer widget is not this interface. Cancel/dismiss leaves the request unanswered.
4. The protected client sends a typed `resolve_approval` with request/review-packet/item identities,
   closed answer and eligible anchored change comments, authenticated with its owner
   credential. Under T1 the model is prohibited from calling an owner-capable path or obtaining that
   key; the initial profile does not claim prevention of deliberate instruction-violating bypasses.
   The service rechecks current rights, pending-request status, exact immutable subjects and relevant
   guards/conflicts. New content needs a new exact request; an unrelated state update need not discard
   the answer. An obsolete/withdrawn request cannot approve a different current draft.
5. One published state commit contains the request resolution, scoped approval if accepted, required
   provenance and command result. Rejecting creates no approval. Duplicate delivery/retry preserves
   one logical answer. Independent Y records and old consumer inputs remain unchanged under R1/P2.

All owner approval routes use this request/answer path; retaining a direct `approve_change` model
tool beside it would be a bypass. Owner identity comes from current credential policy, not an
`ownerId`/`userEventId` body field. A recorded user-event reference alone is not authentication.

This adds a trust boundary, not a claim of existing harness functionality. The generic client-port
design has **not** qualified a native chat user-event API or credential isolation in any harness.
The protected surface must also resist the agent's allowed shell/browser/UI automation routes;
hiding a token while leaving an unrestricted approval proxy/button available to the model is not
separation. An unrestricted same-user process cannot be claimed isolated merely by a file location.
Ordinary MCP tool access cannot distinguish genuine human intent from model-generated claims.
For M1's model-free service qualification, use an explicit human-controlled owner client plus
separate HTTP/MCP role clients. A temporary direct human client is a bootstrap surface, not a plan
to require a second terminal/API ceremony for every future conversational decision. Native in-chat
approval may use the cooperative T1 harness/client path without claiming hard human-origin proof;
the stronger protected controller/event mode still needs qualification before it is advertised.
Complete decision information is a precondition of that view, not merely a full-text link or a
token/character limit. UI-03/UI-04 in the approval-interface draft define the proposed testable split
between declared source coverage, faithful rendering, semantic sufficiency and human comprehension.

## FS-04 — Limits and shared structural observations

Provisioning must supply positive versioned limits for document bytes, index entries, decoded
command/envelope size and selected manifest refs. Check byte limits before unbounded allocation;
envelope limits must accommodate the configured body/metadata encoding. Reject out-of-range data
explicitly, never normalize/truncate it or silently omit required context. Use configured N, N-1,
N+1 boundary cases and multibyte content in tests; these are not invented performance targets.

An approval command includes exact expected subject/context guards, not an expected global document
revision that would invalidate unrelated clauses. A CAS retry rechecks the same operation and
current rights; no edited target is silently approved. Single-root publication does not reset every
clause's status or force all consumers to newest inputs. No claims of exhaustive semantic coverage.

## FS-05 — Own-core implementation boundary

Proposed engineering organization: independent own-core code/build under `backend/server/`, keeping
the imported `backend/current/` baseline intact and not depending on its workflow/provider runtime.
This is one Writhelm service, not a gateway. The imported root currently includes only `:current`;
do not accidentally couple the new core to its complete fixtures/migration chain while calling it
selective reuse. Initial Kotlin/JVM + Ktor/MCP libraries can be retained as tooling choices, with
their compatible versions, notices/security and actual dependency graph checked in implementation.
Copying upstream source needs explicit component provenance; library reuse does not require importing
its `ToolExecutionContext` or database stack. No own-core source/build paths are published by this draft.

## FS-06 — Bounded implementation sequence and readiness

| Unit (prepare its separate versioned task before execution) | Deliverable | Allowed dependency readiness |
| --- | --- | --- |
| I1 — Pure model/codec | Canonical records, strict codec/fingerprints, structural/authority predicates, typed command transitions and fast contract checks | Accepted R1/P2 plus ratified applicable A1/contract scope and exact task |
| I2 — Git command store | Isolated Git adapter, single publisher/CAS, atomic effect+receipt, exact snapshot reads, startup/integrity and fault seams | I1 implemented/cheap-checked; deferred real process coverage explicitly visible |
| I3 — Authenticated clients | Bootstrap/credential checks, current versioned grants, common handler wiring through HTTP/MCP, one two-consumer journey | I1/I2 implemented/cheap-checked where allowed by plan, without claiming final crash qualification |

No live model, embeddings, worker runtime, SQLite, search, large UI or imported-workflow rewrite in
these units. Each implementation task pins the final contract/schema/design inputs. Separate fresh-
context non-author review has read-only assessed code/tests and a designated report/scratch area;
do not claim technical enforcement until the execution mode is qualified. No delegation is currently
authorized by this plan. Routine acceptance remains within D12 only after its actual gates pass.

## FS-07 — Verification portfolio and checkpoint

| Case family | Governing obligation | Focused expected observation |
| --- | --- | --- |
| FS-T01 — Codec/identity | K-APP O2/O4/O5 | Duplicate/unknown fields, malformed Unicode/ranges/IDs, index/body mismatch refused; CRLF/non-ASCII/newline variants round trip exactly; transport formatting does not alter fingerprints |
| FS-T02 — Authority/continuity | O1/O2/O4/O6/O9 | Authorized exact X succeeds; each wrong principal/project/subject/guard is independently refused; Y keeps original records; context/dependency conflict blocks affected use; no fact-to-prescription promotion |
| FS-T03 — Manifest reads | O3/O4/O5 | S1 reads X0/Y0, S2 reads X1/Y0; mixed approved/proposed selection refused; explicit compatible mixed-revision selection retains all context; no latest fallback |
| FS-T04 — Command/Git publication | O7/O8 | Whole effect plus receipt or neither at each publication fault seam; matching retry preserves original logical result; orphan candidate receipt is not committed success |
| FS-T05 — Current credentials | O8/O9 | Bad/revoked secret or missing grant cannot mutate/read/replay protected data; grant changes/rotation are Git-versioned and atomic; bootstrap cannot reset an existing store; last-owner lockout, unsupported Host/Origin, query/body credential bypass and historical verifiers are refused |
| FS-T06 — Real assembly/restart | O1/O3/O4/O7/O8/O9 | Actual authenticated HTTP/MCP clients complete one X/Y journey with interleaved pinned reads and restart; declined side effects/old histories checked, no corpus overlay or model required |
| FS-T07 — Human/request binding | O1/O2/O7/O8/O9 | Planning request grants no authority; exact structured owner answer applies only its subject once; changed/withdrawn/answered request or wrong digest is refused by the service. T1 trusts compliant harness/client behavior against intentional owner-channel impersonation; stronger origin-proof claims require separate qualification |

During implementation run the project-approved cheap pure/targeted checks. Narrow real Git/auth
checks are cheap candidates only after their cost is measured/classified; real process termination
and assembled checks are not deemed cheap merely because no model is called. Record untested limits.

Plan one designated **M1-C1 integrated verification checkpoint** after I1-I3 are assembled: required
real Git/process fault cases, real auth/transport wiring and the small assembled journey on the exact
integrated assessed tree. Deferred coverage remains visible and holds M1 acceptance. Do not run a
broad/expensive suite at each edit or task handoff. If fixes change assessed inputs, previous expensive
results remain about the old tree; use cheap regressions and plan the next authorized checkpoint,
not an automatic rerun or an unproven unaffected-scope reuse waiver. No check is executed/authorized
merely by this proposed schedule; actual expensive execution and paid calls remain separately gated.

## Closure before implementation

T1 permits the bounded pure-content I1a implementation in `docs/tasks/CORE-001.md`. It does not
require solving credential transport or anti-deliberate-bypass attestation for a library with no
external effects. The remainder below applies to the later record/authority/store/client units.

Owner-facing decision: A1's initial human-owner/client credential separation, current Git grants,
bootstrap, bound request/answer flow and loopback-only service qualification profile. The native
chat controller/event boundary remains unqualified and must not be implied by API/MCP access.
Do not request another ruling on SSOT, clause
locality, JSON field names or the retained imported baseline. Bind the resulting scope to exact
contract/design records and prepare I1's task only after relevant approval; I2/I3 require their own
tasks and actual dependency readiness. Final schemas, configured limits, dependency/runtime pins,
Git synchronization/recovery runbook and cost classification must be explicit before their unit
executes. Structural self-check is not independent review or behavioral verification.
