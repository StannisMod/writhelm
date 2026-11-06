# M1 service interface, navigation and human review flow

Contract K-API, revision 3. Status: revision-2 decisions approved with the native-harness correction below;
Application/native candidate implemented and bounded-tested/reviewed; installed-harness and operational
qualification remain held. Retained revision-1 technical proposals are distinguished
from that approval; assembly does not expand its scope.
Task: [DESIGN-009](../tasks/DESIGN-009.md). Dependencies: [K-CODEC](state-codec.md),
[K-PUBLISH](command-publication.md), [K-AUTH](authentication-access.md),
[K-WORKFLOW](knowledge-workflow.md), [T1/U1](../design/approval-interface.md).
Existing domain laws, command bytes/fingerprints and atomicity remain unchanged.

## Revision-3 owner correction — presenter inside the harness

The owner explicitly requires review inside the coding harness through a plugin, extension, panel
or equivalent integration. A separate desktop application or external browser window is not an
acceptable M1 presenter/fallback. This supersedes revision-2 decision 5's non-native allowance and
retained revision-1 external-view alternatives, not domain commands, current rights, completeness or T1.
A local credential/controller component may support the integration but must not create a separate
user-facing decision workflow. It reads protected credentials outside the model path; the native
view renders canonical packets and submits exact choices. Tokens never become model tool arguments
or approval-form fields. Qualify actual plugin/session/request binding and complete rendering for
the selected harness/profile. Unsupported profiles leave pending, not external-window/free-text
approval. No universal native support or adversarial human-origin proof is implied.
The owner selected OpenCode as the first harness on 2026-11-04. Target OpenCode V2; pin and qualify
the concrete GUI/TUI release/profile before claiming support. Historic feasibility is not qualification.
The GUI plugin API is experimental; native panels/IPC are documented candidates, not passing tests.

## Approved revision-2 decision scope

1. **Shared service.** HTTP and MCP use common handlers and authority. An identical command retry
   does not create a second effect. Unknown publication is never represented as rollback.
2. **Navigation without prior IDs.** Clients can enumerate accessible projects, documents and their
   revisions, approval requests and pinned consumer manifests. Pages bind to one snapshot; access is
   checked on every request. An incomplete page is not a complete decision packet.
3. **Exact reads.** Selected records retain immutable references, status and provenance. Navigation
   helps discover a record but never replaces a pinned revision with the latest revision.
4. **Approval in the working flow.** A planner creates a request; the human client fetches it from
   the service, presents the complete packet and submits the exact structured answer with its own
   credential. The model does not receive the owner key. MCP exposes READ/PROPOSE/PLAN, not owner actions.
5. **M1 human client deliverable.** One supported human interface provides pending-decision listing,
   complete review, approve/reject/changes-required and result recovery after disconnection. Chat may
   open an exact request by link. Native in-harness approval requires separate integration qualification;
   universal support is not promised.
6. **No hidden reductions.** An incomplete, inaccessible or oversized packet blocks approval. Closing
   the view leaves the request pending; unrelated state movement does not require repeat approval.
7. **M1 boundary and gates.** Tasks, dispatch, mandatory context closure, evidence lifecycle and worker
   control require later contracts. Launch/abnormal recovery is a separate contract. Schemas, libraries
   and limits must be frozen before implementation; acceptance requires an actual HTTP/MCP/client journey.

These are the complete owner-facing revision-2 semantic decisions. The numbered API-01–07 text below
retains the revision-1 technical elaboration as a preparation baseline: existing accepted domain laws
remain binding, but undisclosed transport/version/session/error/schema details are not independently
ratified by this approval. Reconcile the technical profile with this scope before dispatch; escalate
consequential additional choices rather than silently treating this document as blanket approval.

Navigation is a new required read surface alongside exact reads. Its closed selectors, authorization
mapping, ordering, cursor/snapshot lifetime, page bounds and explicit unavailable/expired behavior
remain readiness inputs, not invented completed schemas. Project discovery must not disclose denied
projects. Snapshot pagination must not preserve revoked access. Listing never supplies approval or
proves packet completeness. No full-text search, RAG or canonical mutation is implied.

Under revision 3 the M1 presenter MUST be integrated into the selected harness. A nonsecret request
reference may open the exact native view; it is not a credential or assent. Neither manual terminal/
API ceremony nor a separate desktop/browser view substitutes for this deliverable. Client secret
handling and actual harness/profile qualification remain gates.
The owner subsequently approved [K-SETUP revision 1](client-setup.md): local setup and a client
component backed by protected non-project files/OS permissions or ACLs; no owner key in chat, command
arguments, links, embedded HTML or approval forms. Integrate that profile without weakening current
authority or claiming other-OS support from Linux evidence. Exact component mechanics remain readiness inputs.

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

Approved X1/K-RUN revision 2 adds Linux, Windows and macOS as service targets. Common schemas,
HTTP/MCP authority and exact bytes do not depend on OS. Isolate client secret storage, local launch,
executable discovery and filesystem/process behavior; do not add unconditional Unix dependencies.
Distinguish portable interface implementation, current Linux-backed execution and per-platform
qualified service support. Launch/abnormal recovery follow [K-RUN](launch-recovery.md); no new network
or remote TLS profile is inferred from client/server OS independence.

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
| Reconnect with no cached record IDs | Authorized project/document/revision/request/manifest navigation reaches exact reads without local canonical copies |
| Page while state changes, access is revoked or snapshot expires | One explicit snapshot across pages, current access enforced, no latest fallback or hidden incomplete result |
| Open exact request from chat, disconnect after answer, reopen client | Complete supported review and same-command result recovery; link/view closure is not assent |

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

## Revision and approval provenance

Revision 1 was an unapproved detailed proposal at source commit
`05d1b9e20f25c8fa28a2f69a7093160609f26049`; committing it did not approve it.
After comparing it with product/M1 requirements, the planner disclosed missing discovery/navigation,
unqualified native harness approval and the distinction between M1 and the full product. The owner
requested a short new contract and explicitly approved the seven-point K-API v2 shown in conversation
with “ладно, утверждаю” on 2026-11-04. The seven decisions recorded above map that exact scope;
technical document assembly, retained revision-1 details and future tasks do not enlarge it.

This is a manual pre-service approval record, not a fabricated service resolution/credential event.
It authorizes the disclosed interface direction, not a push, live credentials, paid experiments,
whole-service acceptance or bypass of the independently confirmed publication finding F-001.

Revision 3 records the owner's explicit rejection of desktop/browser-only alternatives and requirement
for a harness plugin/extension on 2026-11-04. Only presenter delivery/fallback scope is superseded;
no old verification or undisclosed plugin mechanism is ratified by document assembly.

## Current bounded implementation status

Common HTTP/MCP adapters, navigation/exact review reads and an OpenCode V2 GUI plugin/controller
candidate are locally integrated with protected Linux setup/rotation helpers. Target native profile:
OpenCode Desktop 2.2.5 with pinned actual plugin SDK 2.2.5; this is build/type compatibility, NOT an
installed panel/main-window IPC or human walkthrough pass. CLI/TUI/other-harness support is not claimed.

Non-author review found IF-001 (retained rotation versus async attach) and IF-002 (blocking FIFO open).
Separate correction OC-002 was parent-checked and independently rechecked: seven new independent
checks passed, rebuilt GUI bundle matched, no new defect established. Original author checks passed
24 Node cases and a final real Git/HTTP/component journey; its earlier first-controls timeout L-001
remains unexplained despite later passes. Historical failed evidence remains applicable to its inputs.

An initial combined run failed one of 104 cases: actual MCP SDK0.13.0 generated an optional concurrent
GET405 probe that contended with POST for one HTTP slot. API-003 explicitly refines the development
admission profile to four admitted lifetimes including delivery, one stateful dispatch and one
authenticated stateless GET405 lane, global32/s unchanged/no unbounded admission queue. Dispatch
ends only after SDK context cleanup and bounded immutable reply planning; delivery retains lifetime/
reply-reserve ownership. A 16-MiB ledger bounds retained frozen replies and reserves, NOT total JVM
heap/engine allocations. Resource maxima/production-engine qualification remains held.

The original unwrapped SDK expectations remain unchanged. API-003 author checks passed17 focused
cases; non-author review passed four independent cases and found no new blocking defect. Parent
checked source/hash/log/XML evidence, then ran one new exact combined checkpoint:120 affected JVM
cases passed (store/app/service, including actual SDK and native-component journey). This is neither
a full core/auth regression rerun nor installed-host/cross-platform/K-RUN/M1 acceptance. No live
plugin installation, user-wide configuration change, production service, commit or push occurred.

The subsequently approved K-OC-CHECK1 real Desktop checkpoint failed complete native presentation:
OC-CHECK-UI-001 clips content/choices in host overflowhidden layout without plugin-owned scrolling.
Actual registration/IPC/packet retrieval were observed, not full native acceptance. No answers or
rotations were submitted; remaining native scenarios are unexecuted. A separate layout correction
and new isolated checkpoint are required; earlier component/SDK passes and closed findings remain
about their exact assessed inputs. This does not close historical L-001 or any platform/K-RUN/M1 gate.
