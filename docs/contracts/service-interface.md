# Shared service handlers, HTTP/MCP and human review flow

Contract K-API, draft revision 1. Status: proposed; not implemented or transport-qualified.
Task: [DESIGN-009](../tasks/DESIGN-009.md). Dependencies: [K-CODEC](state-codec.md),
[K-PUBLISH](command-publication.md), [K-AUTH](authentication-access.md),
[K-WORKFLOW](knowledge-workflow.md), [T1/U1](../design/approval-interface.md).
Existing domain laws, command bytes/fingerprints and atomicity remain unchanged.

## API-01 — One application boundary, not two authority implementations

HTTP and MCP adapt to the same typed command/read handlers. Transport code cannot set actor/grants,
write canonical records directly, infer approval from text or substitute current targets. The common
write path is bounded ingress, current-H authentication, operation/surface authorization, exact
domain decode/fingerprint, published receipt lookup, typed transition validation, complete candidate
validation and expected-old publication. Recheck current credential/access on every CAS rebuild and
protected retry. Auth/data/namespace ports must be concretely bound before claiming a working service;
an injected always-allow test callback is not a qualified authentication boundary.

Protocol route versions, domain command versions, journal versions, MCP protocol versions, JSON-RPC
IDs, session IDs and domain request keys are DIFFERENT identities. Only K-PUBLISH's scoped domain
tuple governs effect/replay. Neither a new HTTP connection nor a new RPC/session ID is a new business
attempt when the exact command key/payload is retained. Presentation/correlation metadata cannot
silently enter or alter domain identity, scope, expected guards or logical outcome.

## API-02 — Initial HTTP surface

Use the accepted loopback/Host/Origin/header-token profile, with no wildcard bind or credential
fallback. `/api/v1` is a SERVICE route version, not a requirement to rewrite command-v2 as v1.

| Route | Request and observation |
| --- | --- |
| `POST /api/v1/commands` | Exactly one supported core command-v1 or authority command-v2 envelope; original bounded logical outcome plus transport/publication envelope |
| `POST /api/v1/reads` | Closed read-v1 selector; exact protected content/metadata from one captured state, no mutation/receipt |
| `POST /mcp` | MCP 2025-11-25 initialization/messages/tools under API-04 |
| `GET /mcp` | 405 in initial no-SSE profile |
| `DELETE /mcp` | End the authenticated matching protocol session only; no business/credential revocation |

Reads use POST because complete exact references/selectors may not fit safe URL/header budgets;
they are still side-effect-free business operations. No command in GET, generic canonical-file route,
raw Git-object/worktree endpoint, cookie/query-token login or upload/replace-state endpoint. Static
review-client assets, if served, contain no secret or protected project content; loading a nonsecret
request link does not approve anything. Operator bootstrap is a separate explicit local procedure,
never a network initialization/reset route on an existing store.

Read envelope: `formatVersion: 1`, `projectId`, `operation`, `subject`. Supported operations are
`read_revision`, `read_clause`, `read_context`, `read_request`, `read_resolution`, `read_manifest`,
`read_approval_trace`, `read_review_bundle`. Revision/clause/context subjects are their exact bound
keys/refs; request/resolution/manifest subjects are exact IDs; review bundle includes request ID and
request/packet digests. No latest/title selector, supplied actor or free-form query in this slice.
Field/variant schemas are frozen before implementation, not a generic map accepted by handlers.

All ordinary protected reads require READ. Management-only public credential/status lookup, if
needed by AU lifecycle helpers, is a separate closed same-project MANAGE selector, not unrestricted
core/store read access or raw secret recovery. Do not infer READ from MANAGE/APPROVE/PLAN.

## API-03 — Results, errors and irrevocable uncertainty

Application outcomes have stable typed tags: published success, published domain rejection, identity
conflict, unauthenticated, forbidden, invalid input/unsupported format, unavailable, integrity fault
and publication unknown. Only the first two assert a canonical receipt/outcome. Results retain the
original logical identities/domainReplayed; commandReplayed and confirmed read/publication basis
are envelope facts. Never use today's commit as a fabricated original publication OID.

HTTP mapping: 200 for a published command success or completed exact read; 409 for published domain
rejection/identity conflict with distinct codes; 401 for failed authentication; 403 for current-access
or Origin refusal; 400 for malformed/unsupported domain input; 413 for encoded ingress size excess;
429 for explicit transient admission/rate limits; 503 for unavailable or publication-unknown with
distinct typed codes; 500 for integrity/internal faults with safe diagnostics. An authorized exact
read of an absent subject uses 404. Protocol/media/method errors retain their HTTP 405/406/415 classes.
Do not label an HTTP error durably settled unless the response explicitly contains a confirmed
published domain rejection. Authentication failure never leaks record existence or allocates receipt.

A proposed clause readable under READ returns its exact content with UNAPPROVED status, not an
assertion of accepted authority and not latest fallback. A historical manifest returns frozen refs/
provenance plus the captured read basis, not an execution-safety certificate. Current approval/blocker
checks must be explicitly labelled as observations at that basis, not rewrite historical traces.

Timeout/disconnection/cancel is NOT rollback. A possibly published mutation yields unknown until
the retained exact command is retried under current authority and its published receipt reconciled.
Clients retain the original key, payload and guards for that retry; no automatic new key, edited target
or retry of a terminal rejection until state happens to permit it. After lost self-rotation response,
use the retained new credential for the SAME principal's replay; another principal cannot inherit it.

Response-size planning happens before mutation where required. Command outcomes return bounded
IDs/codes, not the entire pending packet/document. If delivery fails AFTER publication, preserve
committed success and expose/reconcile unknown; never report that a published effect rolled back
because rendering/serialization failed. Protected response bodies and errors do not echo secrets.

## API-04 — Explicit initial MCP transport and lifecycle

Initial profile: Streamable HTTP, one `/mcp` endpoint, MCP version 2025-11-25, JSON responses only,
no server-initiated SSE, sampling, elicitation requests, roots, resource subscriptions, long-running
MCP tasks or legacy HTTP+SSE fallback. Advertise tools capability only, with no listChanged emission.
Clients send POST Accept including application/json and text/event-stream; GET is explicitly 405.
Do not claim every MCP client/version/harness can connect without qualifying its actual behavior.

Authenticate initialization and EVERY subsequent HTTP message with current K-AUTH credentials.
The basic MCP specification permits custom auth strategies: this is an explicitly configured private
static-token profile, NOT the OAuth 2.1 discovery/resource-metadata flow. Do not fabricate OAuth
endpoints/issuer metadata or claim those clients work automatically. Extending to general remote
OAuth/TLS interoperability needs a separate accepted profile, not weaker current access checks.

Initialize first; negotiate the supported version/capabilities, return an opaque random session ID,
then require initialized notification before tool requests (ping may be allowed per protocol).
If initialization requests another version, respond with the supported negotiated version per MCP
lifecycle; the client must decide whether it supports that result. Subsequent requests carry
MCP-Protocol-Version and MCP-Session-Id. Invalid/unsupported subsequent version headers get 400;
lost/expired session is 404 and requires reinitialize. Bind session to store/principal/project,
not a historical grant or irrevocable credential authorization. Rotation may preserve that session
for a new active credential of the SAME principal/project; the old key remains denied.

Sessions are bounded disposable protocol state, not Git-canonical workflow. Track per-session used
typed RPC IDs so reuse cannot dispatch another business operation. IDs must be non-null string or
integer; preserve exact identity/value within documented numeric/string bounds, no lossy conversion.
Bound session count, lifetime, ID history and active requests. At a bound refuse/end/reinitialize
explicitly; never forget old IDs while pretending the same session remains valid. Restart loses
sessions, not receipts; a new session may retry the SAME domain command with a new RPC ID.

Accepted notifications/responses use HTTP 202 with no body where required by the transport. Valid
notifications never receive a JSON-RPC response. A mutation disguised as notification cannot execute
a tool command: state changes require a valid tools/call REQUEST and explicit domain key. Unknown
methods/invalid envelope/unknown tool use the pinned protocol's error rules, not an approval/default
fallback. Unsolicited responses do not become commands. Lifecycle DELETE/cancellation affects only
protocol resources, never silently withdraws a norm or undoes an already published transaction.

## API-05 — Tools, protocol parsing and semantic fidelity

Expose `writhelm_command` and `writhelm_read` with valid closed JSON Schema 2020-12 arguments/output
schemas. Command arguments contain exactly `command`, the original typed domain envelope; reads
contain exactly `read`, the read-v1 envelope. No arbitrary tool-name-to-file routing. Tool listing is
filtered by current usable capabilities; a cached list or tool annotation grants no authority.
Published tool schemas are self-contained with local definitions, not unresolved repository/URN
references that require a client to fetch private schemas or discover local files. Client info,
icons, URLs and protocol metadata are untrusted data; the server does not dereference them as
network/filesystem requests or use them to establish identity.

The initial AGENT MCP surface supports READ/PROPOSE/PLAN operations. Owner resolution and authority
management use the human client's HTTP command route (whether rendered in a native panel or supported
review view), not model-controlled owner tools. Even an MCP call claiming owner context cannot add
a direct approval path. The underlying service still validates current rights on every exposed call;
this surface restriction is not advertised as isolation against deliberate API bypass under T1.

MCP execution results use structuredContent carrying the SAME typed application outcome as HTTP;
tool failures use isError true, successful operations false. Protocol parse/method/params failures
are JSON-RPC errors. Auth/Host/Origin failures remain HTTP-level refusals before protected dispatch.
Do not conflate protocol notification acceptance, tools/call success, domain proposal publication or
request approval. Where serialized JSON is also supplied as TextContent for client compatibility,
it must agree with structuredContent and count toward wire/output budgets, not be a different summary.

Do NOT blindly apply the core integer-only/closed-domain parser to all protocol metadata. MCP's
outer envelope and extension metadata follow the pinned protocol schema under their own limits;
they cannot supply application actor, guards or credentials. Preserve the original domain subtree's
number token spellings/Unicode until strict command decoding: decoding 1.0 through a generic numeric
tree and re-encoding it as 1 before the core sees it is a forbidden normalization. Use a qualified
stream parser/token-preserving bounded extraction, not a handwritten JSON parser or schema coercion.
Duplicate decoded names, malformed UTF-8/surrogates, unknown domain fields and integer/range rules
retain their strict domain failures in BOTH transports. Schemas alone do not prove token fidelity.

## API-06 — Complete exact reads and human review

One read captures current H, authenticates/authorizes against H, then resolves all returned record/
body/context/provenance through that immutable H. No repeated mutable-HEAD reads mid-response.
Return exact UTF-8 text/bytes and digest-bound refs; JSON escaping is transport encoding, not newline/
BOM/Unicode normalization. Context remains ordered located fragments, not a fabricated concatenation.
No unannounced field omission, partial records, latest/version fallback or silent server truncation.

read_review_bundle resolves the exact pending request/packet and its declared before/after/context/
dependency material from one H, with original status/provenance and known gaps/blockers. It is a
bounded view input, not a proof of semantic closure. Failure to fit/resolve/authorize required material
refuses the complete bundle; no partial bundle may enable approval. Supporting later multi-fetch/
paging needs explicit snapshot/completeness rules, not a silent partial first-page approval path.
Mark answered requests, known stale guards and incomplete/blocked material non-actionable in the
view. A historically pending record is not proof that its approval preconditions still hold.

The human client renders the accepted starting layout, exact scope/change, material consequences,
alternatives, conflicts and uncertainties before choice. Supporting detail can be expandable only
when it does not hide decision-reversing meaning. Show pending/accepted/proposed/unknown distinctly;
never turn hashes or an evidence-looking sentence into factual truth or new verification.
Closed approve/reject/changes-required choices; only changes carries nonblank comments under exact
item IDs. No default assent. Cancel/dismiss leaves pending. A changed request/packet invalidates a
locally prepared answer; unchanged independent clauses are not extra approval chores.

Submit the original request/packet digests and exact structured answer through resolve_approval,
not a generic approve-edit call. Service rechecks current relevant guards/rights/blockers; an unrelated
H movement alone does not require reapproval. Rendered data must never be replaced with hidden model
text for a different ID. Native form/session IDs are correlation only; T1 does not claim proof of a
physical human event or universal native-client support. Without a supported complete presenter,
leave pending or refuse, never free-chat automatic assent.

## API-07 — Resource, cancellation and integration gates

Before implementation pin HTTP/MCP library/runtime versions and positive ingress/header/response/
wire-expansion/session/queue/rate/concurrency/time budgets. Account for MCP wrapper and optional text
duplication, escaping of raw document bytes and complete review material. Reject overload before
unbounded queue/body allocation; isolate parser/auth failures from canonical journal traffic.
Do not publish a candidate that cannot fit its core/auth/receipt file limits. Read-response budget
failure is unavailable/too-large, not corrupt data. Lower new-write limits do not invalidate old reads
whose supported resource profile is adequate. No default maxima or throughput claim without evidence.

Cancellation stops unstarted work where possible, but cannot undo published state or defeat the
publisher's in-flight barrier. A cancellation reason is untrusted text; don't persist/log secrets or
make it a domain answer. Receiving or ignoring a cancellation never supplies owner assent. No MCP
task-support capability is advertised to imply durable worker dispatch or exactly-once execution.

Implementation binds actual checked auth/publisher APIs, not guessed mutable worker interfaces.
Cross-transport shared-key effects, real credentials/current-access checks, byte fidelity, UI/bundle
completeness, actual supported MCP lifecycle/client compatibility and process-restart journey remain
M1-C1 gate-holding until executed on the integrated tree. Runtime host/power-loss, remote TLS/OAuth,
worker dispatch/mandatory closure and enterprise anti-agent attestation stay outside initial scope.

## Finite derived obligations and source grounding

| Case | Required observation |
| --- | --- |
| Same command through HTTP, MCP then HTTP retry | One effect/result under the same scoped key; RPC/session IDs do not manufacture a new attempt |
| Versioned commands/read shapes, metadata floats versus domain 1.0, duplicate escaped keys | Correct protocol handling, strict unchanged domain refusal; no numeric/coercion loophole |
| Current grants/credential revoked during session or CAS retry | No cached-session authorization or protected replay under old rights |
| Initialize/ready/version/session expiry/reused IDs/GET/DELETE | Supported lifecycle; explicit failure/reinitialize; no business effects from lifecycle messages |
| Owner tool attempt, notification mutation, form success or canceled dialog | No owner bypass, notification mutation, implicit approval or cancellation-to-rejection conversion |
| Domain rejection/key conflict/unavailable/unknown after lost response | Stable distinct outcomes, same-key reconciliation, no new-key blind retry or false rollback |
| Old/new consumers interleaved with proposals and ordinary updates | Exact original refs/text/provenance and one captured H per read; no forced newest input |
| Large/denied/corrupt/incomplete review bundle and output-size failure | Explicit whole-view refusal, no cropped approval; post-publication delivery failure never erases receipt |
| HTTP/MCP alternate credential/header/Origin/media routes | Same actual authority and byte rules, safe error handling and no secret leakage |

Read primary MCP 2025-11-25 [basic/messages](https://modelcontextprotocol.io/specification/2025-11-25/basic),
[lifecycle](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle),
[transports](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports),
[authorization](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization),
[tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools) and
[cancellation](https://modelcontextprotocol.io/specification/2025-11-25/basic/utilities/cancellation),
plus [JSON-RPC 2.0](https://www.jsonrpc.org/specification). These define protocol/lifecycle/error rules,
not proof of current libraries, native renderers or arbitrary client support. Tool inputs/annotations
are not human attestation. The basic MCP specification permits custom auth strategies; the initial
profile explicitly omits OAuth discovery while preserving per-request private credentials. No runtime
probe, server/client implementation, paid call or passing integration result is claimed by this draft.
