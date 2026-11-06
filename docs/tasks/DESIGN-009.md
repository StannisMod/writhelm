# DESIGN-009 — Shared HTTP/MCP handlers and review-client flow

Task revision: 3. Status: approved K-API/native/secret scope has a locally integrated bounded candidate;
not an installed-host, cross-platform, operational or M1 acceptance.
Executor: parent session. Owner requested continuation to the next design while separate native
children review the first three blocks and implement accepted K-AUTH/K-PUBLISH. Do not edit their
source/tests/build/tasks, frozen review input or approved inputs while those jobs are active.

## Objective and exact knowledge

Define one application command/read boundary behind HTTP and MCP, precise result/error/retry and
snapshot behavior, bounded initial transport profile and structured human review flow under T1/U1.
Inputs at `f94108b5f64bf5389199a26436048e34c714e51c`: README/AGENTS/publication policy, K-CONTENT,
K-WORKFLOW, K-CODEC, K-PUBLISH, K-AUTH and their schemas/readiness, first-slice FS-02/06/07 and
approval-interface UI-01–06. Source baseline is committed but review/integration is not complete.

Read primary public MCP 2025-11-25 basic/messages, lifecycle, Streamable HTTP transports,
authorization, tools and cancellation, plus JSON-RPC 2.0. Do not mistake JSON-RPC IDs/sessions for
domain request keys, custom static-token profile for OAuth discovery, or tool success for approval.
Record relevant requirements/limits rather than importing third-party prose/code.

## Scope and expected output

Write only this task and `docs/contracts/service-interface.md`, local/ignored until a new tracking
decision. No server/client implementation, dependency/schema changes, keys, network listener,
model/provider call, extra delegation, Git data-store writes, public tracking/commit/push or changes
to any active worker's files. Public documentation reads are allowed; no paid/runtime experiment.
The revision-2 owner approval below supersedes the earlier pending status only for its disclosed scope.

Specify finite commands/reads and public routes, shared auth/transition/publication ordering, current
access and immutable read basis, structured outputs, terminal rejection versus unknown/unavailable,
transport/domain numeric/field distinction and cancellation, bounds/no truncation, owner packet
fetch/answer binding and actual capability negotiation. Keep native UI layout/stronger attestation,
remote TLS/OAuth server, streaming task execution and worker dispatch out of this slice.

Escalate real contradictions with accepted contracts or protocol sources instead of weakening them.
Output owner-readable proposed contract and source-linked finite test obligations. No implementation,
passing transport/client test, universal harness compatibility or independent acceptance is inferred.

## Result and consistency check

Prepared K-API draft revision 1. It preserves v1/v2 domain bytes, identity/receipt/replay and current
authority laws; routes only adapt to common handlers. Separate MCP correlation/session and business
identity, generic protocol metadata parsing and strict raw command subtree validation, and known
canonical outcomes versus unavailable/unknown publication. Complete bundle/rendering remains a
requirement under T1/U1; pending does not imply eligible, tool/form success does not grant approval.

New proposed surface choices: JSON-only MCP 2025-11-25 Streamable HTTP, bounded disposable sessions,
agent tools restricted to READ/PROPOSE/PLAN, owner actions through the human client's HTTP handler,
explicit configured static-token auth rather than OAuth discovery, and all-or-refuse review bundles.
No claim that every harness supports this profile. Public protocol sources were read, including
version negotiation (initial fallback versus invalid subsequent header), no-null/nonreused request
IDs, notification/HTTP-202 behavior, GET-405 support and cancellation races. Custom MCP auth is
permitted by its basic specification; full OAuth/TLS interoperability is not falsely promised.

No approved input or active worker file was changed. No transport/client code, listener, model call,
runtime experiment, paid operation or passing E2E test is claimed. Owner approval of this bounded
surface precedes implementation; exact read/result/wire schemas, budgets, runtime and actual checked
auth/store API binding remain executable-task inputs, not a new foundation/SSOT decision.

## Revision-2 owner decision and remaining readiness

Owner approved the concise seven-point replacement K-API v2 on 2026-11-04. Its canonical scope is
the approved-decision section of `docs/contracts/service-interface.md`: shared HTTP/MCP boundary,
ID-independent authorized navigation, snapshot pages/current access, exact pinned reads, separated
human owner actions, one supported M1 review interface/link/result recovery, complete packets and
explicit M1 versus later task/dispatch/evidence/harness boundaries. No universal native integration.

Revision-1 technical text remains a proposed preparation baseline except existing accepted domain
laws and choices actually disclosed in revision 2. Approval does not silently ratify protocol/version/
session/error details. Before implementation prepare the concrete closed list/read/result schemas,
page/snapshot and secret-handling profile, library/runtime pins and positive budgets; surface any
consequential choice outside approved scope. Launch/supervision/abnormal recovery needs its separate
contract. Confirmed review finding F-001 blocks integration pending separate remediation/verification.
No source fix, transport/client implementation, tests, commit or push is performed by this status update.

K-SETUP revision 1 was subsequently approved on 2026-11-04; see
`docs/contracts/client-setup.md` for the six-decision client secret/setup scope. API implementation
must consume that explicit profile instead of inventing token entry through chat/approval forms or
browser persistence. Per-platform qualification, precise local component mechanics and F-001 remain
visible readiness/integration obligations, not passed outcomes.

Subsequent owner correction: K-API revision 3 and K-SETUP revision 2 REQUIRE a harness-integrated
presenter; desktop/external-browser alternatives are rejected. Preserve completed common API work;
replace B-002's false two-way choice with a concrete native harness/profile selection and capability
qualification. No plugin installation/user-wide config change is implicitly authorized. T1 remains.

The owner selected OpenCode as the first harness. Use V2 primary plugin GUI/TUI/RPC documentation;
pin the actual host/SDK profile. Keep native review inside OpenCode, with protected non-model
credential handling and exact packet/session bindings. Generic form acceptance is not stronger
human-origin proof. GUI API experimentation/version compatibility and complete renderer execution
need explicit evidence. No automatic installation into the owner's live harness is authorized.

## Bounded implementation/integration disposition

API-001/OC-001 delivered common handlers/navigation/HTTP/MCP and native Desktop2.2.5 GUI/controller/
Linux setup helpers in isolated candidates. Independent review findings were corrected separately:
STORE-003 closes F-001 (10 independent law probes); OC-002 closes IF-001/IF-002 (7 independent checks).
Parent checked exact reviewed/tested bytes, not report wording alone. Initial combined104-case run
had one actual SDK admission failure; API-002 diagnosed concurrent optional GET versus POST, and
API-003 explicitly separated bounded control/stateful/delivery ownership. Four independent admission
probes passed, original SDK semantics retained; no client retry wrapper or limiter removal.

Final parent-owned affected combined run passed120 JVM cases on exact assembled source. Forty-five
explicit source/build/test paths were then integrated locally byte-identical to tested assembly;
no generated bundle, dependency/cache/evidence/private fixture export, staging, commit or push.
New source publication paths still require their scoped tracking/content review. Native-host GUI/
IPC execution, L-001 unexplained startup timeout, Windows/macOS, K-RUN recovery, maxima/release and
M1-C1 remain held. This result does not authorize a live plugin installation or claim complete M1.
