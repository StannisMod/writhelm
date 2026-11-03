# DESIGN-009 — Shared HTTP/MCP handlers and review-client flow

Task revision: 1. Status: proposed transport/client contract prepared; no implementation.
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
Consequential initial MCP/owner exposure and lifecycle choices remain proposed pending approval.

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
