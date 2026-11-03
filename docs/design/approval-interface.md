# Structured owner review — feasibility and information contract

Draft revision: 1. Task: [DESIGN-005](../tasks/DESIGN-005.md).
Status: owner direction U1 recorded; proposed UI contract/recommendation with bounded observations.
No qualified privileged UI, human-usability result, production implementation or publication.
Subsequent owner ruling T1 accepts the shown layout as a starting design and deliberately chooses
a cooperative-agent initial mode. Strong adversarial human-attestation/anti-automation qualification
and screen refinement are not first-version blockers. Structured binding and decision completeness
remain requirements. This is qualitative owner feedback, not measured usability or a hard guarantee.

## U1 — Accepted direction and its boundary

Owner responses are structured choices, not free chat interpreted by an LLM. Free text is allowed
only under a concrete selected item when its choice is "changes required". The displayed information
must be complete enough to make the decision; an output limit cannot justify missing obligations,
qualifications or consequences. Investigate feasibility and comprehension rather than assume them.

D1 remains: one coherent semantic decision may change several clauses. UI items/comments have exact
anchors; they are not a rule that every clause needs independent ratification. Unchanged independent
clauses keep their status. Cancelling a view is not a rejection or approval.

## What the investigation establishes

**Not universally available natively.** MCP does not mandate a UI layout and clients negotiate
elicitation capabilities. The 2025-11-25 form schema is deliberately restricted; it does not supply
arbitrary nested review cards, conditional comment widgets or a faithful full-document diff layout.
An available form is neither proof of the required presentation nor proof of a genuine human action.

| Surface | Evidence | Consequence / qualification state |
| --- | --- | --- |
| OpenCode V2 custom desktop GUI | Official experimental GUI SDK documents panels, custom JSX dialogs, session-bound props and main/window IPC | Rich review presentation is a plausible native route. API is experimental; no installed custom plugin/native owner isolation was tested. Do not infer human-only authorization from plugin/IPC names. |
| OpenCode V2 terminal UI | Official TUI SDK documents custom dialogs/panels; installed API exposes enum choices, `custom: false`, conditional `when` and required fields | Structural controls are available in principle. Six real isolated form API cases were executed against the running service; this did not test native visual rendering. |
| OpenCode standard form reply | The same permitted CLI/API channel programmatically answered a synthetic form without any GUI/human event | A successful form answer alone cannot be treated as owner assent in a mode where the agent can use that channel. This is an intended automation capability, not a claimed unauthenticated server vulnerability. |
| MCP generic elicitation | Specification permits form/URL modes only when supported, with no mandated UI pattern; form mode is a restricted schema subset | No "all MCP clients can render this exact consent UI" guarantee. Native `Form.When` behavior is not proof that the generic MCP bridge preserves it. Never collect owner tokens through elicitation form fields. |
| Claude Code elicitation | Official MCP docs describe interactive forms/URL mode; official hook docs allow answering before a dialog and replacing the response afterwards | A plain `accept` response is not evidence that a human saw or selected it. No Claude installation/runtime was qualified; this is a source-grounded counterexample to trusting the protocol response alone. |
| Writhelm-controlled small review surface | Synthetic isolated HTML prototype rendered in the in-app browser; nine DOM checks passed | Shows a viable interaction shape, not a real authenticated product or measured human comprehensibility. This page is deliberately agent-automatable and has no owner privileges. |

Version observation: installed CLI reported 2.2.2; the connected service reported 2.2.5. The actual
OpenAPI/form observations belong to that service, not an assumed CLI/desktop version. Current V2
documentation is mechanism evidence, not qualification of every app release or remote/headless mode.

## Executed isolated observations

Created one fresh scratch session, sent no prompts/generation requests, created six synthetic forms,
and deleted that scratch session afterwards. Its reported cost and all token counters remained zero.
No existing user's form/session, plugin or config was modified.

| Form case | Observed state/result |
| --- | --- |
| Closed enum valid choice | `answered` with the exact structured choice |
| Arbitrary string instead of enum option | Refused; form stayed pending |
| Comment supplied while choice was approval | Refused as inactive field; stayed pending |
| Changes requested without required comment | Refused; stayed pending |
| Changes plus item-A comment | `answered` with that exact field binding |
| Unknown item-B comment | Refused as unknown field; stayed pending |

All replies were sent programmatically by the researcher through the CLI, so these observations
establish structural validation **and** the need for a distinct protected human-authorization path.
They do not establish the renderer's usability, physical user input or service-wide isolation.

The HTML prototype separately exercised: no default answer/free-text input, conditional anchored
comments, no comment in an approval payload, canonical packet fields bound to rendered fields,
missing-context approval blocking, changed-packet choice invalidation, retaining all 27 large-context
paragraphs, and cancellation without assent. Nine final DOM checks passed. An initial physical-click
tool attempt failed with a browser operation error and left the page unchanged; DOM interaction and
a screenshot succeeded. No physical-click qualification or complete accessibility audit is claimed.
The service subsequently restarted and cancelled the temporary prototype server. The HTML and
recorded observations remain scratch artifacts; completed probes were not repeated. Their version /
capability findings remain about the assessed pre-restart service, not a newly qualified deployment.

## Proposed review packet and response contract

The request and its review packet are immutable, Git-versioned, and fetched by a trusted renderer
from Writhelm. A generated explanation is not a separate authority. Proposed identifiers:
`requestID/requestDigest`, `reviewPacketDigest`, `decisionGroupID`, `itemID/itemDigest` and exact
clause/context references. Native session/form IDs are correlation values, not owner identity.

### UI-01 — Closed choices, anchored changes

Each actionable coherent item/group has the closed choices approve, reject, changes-required, with
no default approval and no "Other" free answer. Only changes-required can carry nonblank text, bound
to an explicitly selected affected clause/item anchor from that request. No floating global comment,
free-text assent or matching by title, visual order, current tab or model inference. The backend
validates the same law; front-end disabled controls alone are insufficient.

### UI-02 — Exact displayed subject and response lifecycle

An answer binds the displayed request/packet/item digests. A different revision, item, project,
withdrawn/already answered request, stale relevant guard or unknown anchor cannot be remapped to
"what is current". An unrelated update need not invalidate the same exact subject. Duplicate delivery
has one outcome. Closing/Escape leaves unanswered. Switching sessions/tabs must not retain an answer
under another item's displayed text. Coupled clauses are approved atomically, never accidentally as
separate incompatible pieces; comments can still point to their specific structural clauses.

### UI-03 — Full decision information is the input contract

Before asking for assent, assemble the packet from exact sources with a manifest containing:

1. What coherent choice is being made, applicable scope and explicitly excluded neighbouring changes.
2. Exact before/after wording for the changed subject, with immutable references and required context.
3. Applicable mandatory constraints/dependencies, relevant exceptions and compatibility findings.
4. Alternatives, rationale, consequences/costs/risks and affected interfaces/tasks where known.
5. Evidence/provenance and its assessed inputs; proposed, observed, verified, unknown and conflicting
   information are distinguished. Owner assent does not turn a factual claim true.
6. Which existing independent statuses remain, what needs reconsideration, and unresolved questions.
7. An inventory of the original material, its exact versions and how every declared necessary aspect
   is represented in the review view. Unknown extraction/applicability remains visible under D8.

The packet is subordinate to requirements/contracts, not an independent source overriding them.
Its declared closure can be mechanically checked; whether all relevant natural-language consequences
were discovered is a semantic planner/review obligation, not a theorem proved by counting source IDs.

### UI-04 — Presentation limits must preserve decision completeness

Separate four claims: (a) source inputs are present, (b) rendering/summary represents them faithfully,
(c) those inputs are semantically sufficient for this choice, (d) this person can understand/decide.
Passing (a) does not prove (b)-(d). "The full corpus is behind a link" is not a complete usable view.

Always present the actual choice/scope, exact relevant changes, material qualifications, known
conflicts, costs and uncertainties before assent; do not hide a decision-reversing exception in a
collapsed background section. Supporting source detail may be expandable, with exact versions and
unambiguous labels, only when the primary view preserves all known decision-relevant meaning.
Scrolling/reflow can preserve content; truncation, omitted dependencies or an unreviewed shorter
summary cannot be called equivalent merely because they fit the screen. No "I opened it" checkbox
is proof of comprehension or a substitute for a complete packet.

If required material is absent, denied, cropped, incompatible or known inadequately represented,
approval stays unavailable and the service rejects attempts to bypass the view. If the decision
cannot be presented coherently within usable bounds, refine the design/question or decompose only
where semantic independence permits; otherwise leave it pending. Do not silently reduce completeness
to satisfy a character limit, nor split interdependent choices solely to produce shorter cards.
Uncertainty can be explicitly part of the decision; it cannot be relabelled as proven completeness.

### UI-05 — Human-only approval is a separate qualified capability

T1 scopes this section to a future stronger assurance claim, **not** a prerequisite for the cooperative
initial version. Initial mode relies on instructions/trusted harness behavior against deliberate
programmatic/hook bypasses; it must say so. Service grants and exact structured answer validation
remain enforced, and agents are still forbidden to self-approve or receive owner credentials.

A correctly shaped form/answer is not an owner attestation. Protect the owner credential and answer
path against the agent's allowed API, shell, RPC, browser and UI-automation routes. Hooks/programmatic
form replies must not be able to manufacture or modify an authoritative owner answer in a supported
mode. A same-user unrestricted process or agent-controlled browser does not meet this guarantee by
calling a page trusted. Authentication still uses current canonical policy, not body-supplied IDs.

### UI-06 — Capability fallback, not weaker semantics

Use one canonical packet/answer contract with qualified presenters. A native rich panel is optional,
not the authority model. A controlled small Writhelm review surface could provide consistent UI when
a harness lacks it; an adapter can offer a non-secret request link or notification, not a preauthorized
owner link. A URL-mode elicitation's `accept` only opens/permits navigation, not business approval.
Unsupported/headless/no-safe-owner-surface modes leave the request pending; do not fall back to
free-text approval. This recommendation does not select a cookie/login/credential architecture or
claim the external browser is outside agent control without qualification.

## Human-usability qualification still required

The synthetic example uses one coupled decision/two clauses with a material access-revocation
qualification and retention cost visible up front. Larger context tests preserve the same critical
qualification and all original fragments. This demonstrates availability, not low cognitive load.

A bounded owner walkthrough should check whether the person can accurately state the chosen
change, its exclusions, material exception, cost and uncertainty, and can place a change request
under the intended item without transcription/remapping. Use a small case, a larger related-context
case and a dependency/exception case. Observe navigation, confusions, decision errors and burden;
do not invent successful results, a universal word/time cutoff or a guarantee from expert inspection.
No human walkthrough has occurred yet; no paid or model study is authorized here.

## Focused obligations before enabling a presenter

| Case | Required observation |
| --- | --- |
| UI-T01 choice/comment law | Unknown choice/text outside changes/unknown item rejected; eligible comment retains exact anchor |
| UI-T02 request binding | Reordered items, multiple windows/sessions and changed digests cannot map an answer to another subject |
| UI-T03 lifecycle/grouping | Cancel is unanswered; duplicate is one result; coupled clauses do not partially approve |
| UI-T04 completeness failures | Missing/denied/cropped required input or known summary omission blocks approval, not only changes a cosmetic badge |
| UI-T05 material exception | The designated decision-changing condition stays explicitly represented in primary view as supporting context grows |
| UI-T06 semantic status | Structural source coverage is distinguished from semantic assessment/unknowns and actual human comprehension |
| UI-T07 owner boundary | Script/hook/API/browser automation cannot manufacture the accepted human action in the claimed execution mode |
| UI-T08 unsupported client | No elicitation/custom GUI/headless capability yields pending or a qualified separate view, never text-based automatic assent |

## Primary sources and recommendation

- [OpenCode V2 plugin overview](https://opencode.ai/v2/docs/build/plugins),
  [GUI](https://opencode.ai/v2/docs/build/plugins/gui), [TUI](https://opencode.ai/v2/docs/build/plugins/cli),
  [RPC](https://opencode.ai/v2/docs/build/plugins/rpc), [API](https://opencode.ai/v2/docs/api) and
  [MCP](https://opencode.ai/v2/docs/mcp-servers). Inspected actual installed OpenAPI and synthetic form behavior.
- [MCP elicitation 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation):
  capability/mode negotiation, restricted schema, UI freedom, action meanings, identity and URL safety.
- [Claude Code MCP](https://code.claude.com/docs/en/mcp#respond-to-mcp-elicitation-requests) and
  [hooks](https://code.claude.com/docs/en/hooks#elicitation): programmatic answers and post-response overrides.
- [W3C error prevention](https://www.w3.org/WAI/WCAG22/Understanding/error-prevention-legal-financial-data.html):
  review/check/correct important submissions; guidance, not an accessibility certification.
- [NN/g progressive disclosure](https://www.nngroup.com/articles/progressive-disclosure/): careful
  primary/secondary split and testing; warns about interdependent wizard steps. Not proof that a
  shortened engineering decision remains complete. No third-party prose/examples are imported.

Recommend making the complete versioned review packet and closed item-bound answer the stable
interface; qualify UI/owner isolation per supported profile rather than promise all native harnesses.
The owner accepted the displayed prototype as the initial design basis under T1, with visual
refinement deferred. Implement the bounded cooperative-mode core without claiming stronger isolation
or measured universal readability. Detailed layout/user studies and adversarial qualification can
follow; completeness of each decision packet is not waived.
