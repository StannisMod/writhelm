# DESIGN-005 — Structured approval UI feasibility and information completeness

Task revision: 1. Status: initial feasibility/contract/prototype investigation prepared and self-checked.
Protected owner UI and human decision-usability qualification remain outstanding. Design/research
only. Executor: primary session, no delegation.
Subsequent T1 accepts the prototype as an initial design basis and defers stronger anti-bypass /
visual qualification; those are not cooperative-first-version blockers. Completeness is retained.
Authorization: owner directed investigation of structured GUI choices, item-local change comments,
reliable subject binding, cross-client feasibility and complete yet usable decision presentation.

## Objective

Qualify what can actually be offered, not assume every harness can render or protect the required
GUI. Separate presentation capability, structural binding, human-only authorization and completeness /
decision usability. Preserve D1: one coherent semantic ruling may cover several clauses; a comment's
concrete anchor must not turn independent unchanged clauses into new approval chores.

Owner direction U1: no free-text assent/remapping. Choices are structured; text is allowed only
under the selected concrete item for "changes required". Presentation limits must preserve the
information necessary to decide. This does not approve a particular plugin/browser architecture.

## Exact local inputs

Draft snapshots retained outside public Git by SHA-256; accepted scopes and implementation claims
remain distinct. No private project examples/transcripts are publication inputs.

| Input | SHA-256 |
| --- | --- |
| `README.md` | `6d3f09c622a8e46a737af24cc6eb5474ee246e1d2e89dd8bfbe2d97e057b9379` |
| `AGENTS.md` | `e737009d14320aa0bc1d5a8006a2ebfdc3862c282504f94a436e645513f035e9` |
| `docs/specification.md` | `8a24e42e5bc2baf35fc068080f6735d5fcada420b6383740ba45880cd37b16d9` |
| `docs/workflow.md` | `287cea745b8bb4d267378f935311d69dae39c585359aa59b928f89959d534bbb` |
| `docs/design/first-slice.md`, draft 2 | `d191f65fe0e5145ffd39da8bd8813d997678c796eadd374d9c00656fff380ea0` |
| `docs/design/git-state.md`, revision 2 | `e8873a69d84a67f83ff1b416a3dd390423b31dadcd77c45df812dcb6fdf92a20` |
| `docs/tasks/DESIGN-004.md`, revision 2 | `abb443487132e2505d694e30c722acd2cbdfa58d9bd41988a8a1063502414488` |
| `docs/publication.md` | `915d92319896196766269d0feca03fab4617f91f76c4d5065c1ed836a7742d5d` |
| `docs/acceptance.md`, retained before U1 catalogue update | `0154cc13924f9fa2cf37b08ff9a0e3a524ee977ec28bf471eb3d7286867714b0` |

Primary external inputs: actual OpenCode V2 plugin GUI/TUI/RPC/API/MCP docs; actual installed
read-only version/OpenAPI observations; MCP elicitation specification; representative other-client
official docs; relevant W3C/UX guidance. Titles/indexes do not substitute for applicable text.

## Scope and constraints

- Read: listed inputs, public primary sources, installed harness API/schema/code needed for precise
  claims. Do not inspect owner sessions/config secrets or unrelated project data.
- Write: this task and `docs/design/approval-interface.md`; bounded U1/traceability refinements to
  specification, acceptance, first-slice/architecture/current task and proposed publication manifest.
- Scratch only: synthetic HTML/interface/packet probes and their checks outside public Git; optional
  isolated model-free harness-form probes in a newly created scratch session. Own scratch session
  cleanup is allowed; never reply to or change an existing user's form/session.
- Decide: derived coverage/usability criteria, source-grounded capability classification and a
  recommendation. No universal native-GUI/semantic-completeness guarantee from a few examples.
- No production source/test changes, real credentials/approvals, model calls, paid studies, agents,
  embeddings, expensive suites, plugin installation/reload, user-wide config changes, staging/push.
- Prototype has no privileged backend. A successful DOM interaction is not a qualified owner-only
  trust boundary, actual native plugin proof or evidence a human comprehended the decision.

## Acceptance

1. Enforce closed choices and item/digest-bound comments structurally, not through an LLM's guess.
   Cancel/dismiss is not approval; multi-clause coherent decisions cannot accidentally approve partly.
2. Define a completeness contract for review packets: exact change, decision scope, mandatory context /
   dependencies, alternatives/consequences, conflicts/evidence/uncertainties and original sources.
3. Separate full data availability, faithful rendering/summary, semantic sufficiency and actual human
   comprehension. "All source text is behind a link" does not establish usable complete presentation.
4. Check native GUI/TUI/generic form constraints and identify counterexamples to "always possible";
   distinguish docs, actual installed schema, executed scratch behavior and unqualified properties.
5. Exercise small/large/dependent/stale/missing/truncated/critical-exception cases at the narrowest
   permitted boundary. Do not silently crop, omit or auto-approve to fit a screen/budget.
6. Show a synthetic non-authorizing UI probe if useful; collect user understanding feedback only as
   an explicit next evaluation, not fabricate successful human-usability results.
7. Keep all new material local-only, check references/inputs/public boundary and retain claims/evidence
   outside the source tree. Record implemented/observed/proposed/verified states separately.

Escalate a real capability/authority/completeness conflict with consequences and viable alternatives.
Expected output: concise conversational findings and recommendation, local contract/capability matrix,
finite checks and exact qualification gaps. No request for the owner to read Markdown files.

## Result and evidence checkpoint

Prepared `docs/design/approval-interface.md` draft 1, SHA-256
`fc04c29a7441c9040e38039999aa87e601a463d9e6d2f3162cfbb73998ac6b04`.
Recorded U1 and replaced the first-slice free-text assent alternative with structured choices /
item-local changes-required comments. Added AC-43/44; no claim those Writhelm scenarios passed.

- Official-source findings: native rich OpenCode presenters are candidates (desktop GUI SDK is
  experimental); generic MCP does not mandate the required presentation; Claude elicitation hooks
  can synthesize/replace answers. A form response alone is not human attestation.
- Executed before the service restart: six isolated form API cases against the reported service
  2.2.5 with CLI 2.2.2. Valid choices/anchored comments succeeded; arbitrary enum, inactive/missing /
  unknown comments were refused. All answers were programmatic, showing the separate human-origin
  qualification need. Scratch session usage was zero and its deletion was confirmed.
- Synthetic HTML prototype: nine final DOM checks passed, including exact primary-field binding,
  choice/comment constraints, missing-context blocking, packet-change invalidation, all 27 context
  paragraphs retained and cancellation. Screenshot succeeded. One physical-click tool operation
  failed without changing state; physical input/native plugin/accessibility qualification is not claimed.
- No real credentials, owner approvals, model calls, paid studies, plugin/config changes, source
  implementation, expensive suite, delegation or independent review occurred.

The server restart cancelled the temporary prototype HTTP server. Reconciled the retained native
results, HTML and recorded DOM observations without repeating completed probes. Browser tools later
became available again; reopened the HTML file preview without restarting the temporary server or
rerunning tests. Do not infer post-restart deployment qualification from pre-restart results.

Author static self-check passed: six UI contract clauses, eight future qualification cases, 24 WH
requirements, 44 distinct AC scenarios, nine retained input digests, local links/whitespace and
simple private-identifier scan; native outcome/zero-usage/cleanup reconciliation and nine retained
DOM observations; ignored/proposed-only new paths, nothing staged and no tracked backend changes.
These checks do not prove semantic completeness, absence of private data or human comprehension.

## Continuation and genuine remaining gates

Present the bounded findings and complete-information rule conversationally. Recommend one canonical
review-packet/response contract with qualified presenters, potentially a small controlled common
view plus native integrations; do not silently select/implement it. No universal native-GUI claim.

Next human-dependent step: a bounded walkthrough of small/larger/dependent decision cases, checking
whether the owner can identify the actual choice, exclusion, material exception, cost/uncertainty
and intended comment anchor. Availability and DOM rendering cannot supply that result. Actual
protected controller/credential/API/hook/browser isolation, native visual behavior, cross-window /
stale-answer integration, renderer truncation handling and selected-profile runtime qualification
remain explicit unexecuted UI-T obligations. No blanket implementation/publication authorization.

Owner continuation T1: proceed with a cooperative-agent first version instead of enterprise-level
defence against deliberate instruction violations. Record this scoped trust choice rather than
pretend the earlier strong boundary was verified or silently remove all service authorization.
The prototype received qualitative approval as a starting screen, not a measured comprehension
result. Continue with a bounded pure-core task; do not keep reopening GUI/security design.
