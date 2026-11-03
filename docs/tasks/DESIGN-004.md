# DESIGN-004 — Executable first-slice design and authority profile

Task revision: 2. Status: clarified draft prepared/statically self-checked; A1 remains proposed.
Design only. Executor: primary session, no delegation.
Authorization: owner acknowledged the Git-SSOT architecture and directed continuation.

## Objective

Close the bounded first-slice design with concrete canonical records/command boundaries, a safe
initial authority profile and a finite verification/implementation plan. Do not reopen F3/R1/S1/P2,
invent another general design catalogue or ask the owner to ratify field names. Present only a
consequential remaining authority/deployment decision conversationally. No implementation yet.
Revision 2 addresses the owner's question: authentication alone did not specify how a genuine
human decision reaches the service without giving the planner owner authority.

## Exact inputs

Retained drafting snapshots by SHA-256. Accepted conversational scopes are distinct from draft
schemas, whole-contract approval, behavioral verification and publication.

| Input | SHA-256 |
| --- | --- |
| `README.md` | `6d3f09c622a8e46a737af24cc6eb5474ee246e1d2e89dd8bfbe2d97e057b9379` |
| `AGENTS.md` | `e737009d14320aa0bc1d5a8006a2ebfdc3862c282504f94a436e645513f035e9` |
| `docs/design/knowledge-model.md` | `3f071d8577e7f9f93a7a40feb29f6098d24c2f7bbffbfb27d4a215a46e5e2728` |
| `docs/design/git-state.md`, revision 2 | `e8873a69d84a67f83ff1b416a3dd390423b31dadcd77c45df812dcb6fdf92a20` |
| `docs/contracts/accepted-knowledge.md` | `a741f498df4a84d2992f51b6696634f9e038f1ddb9098e658675d8c2409b02da` |
| `docs/specification.md` | `8a24e42e5bc2baf35fc068080f6735d5fcada420b6383740ba45880cd37b16d9` |
| `docs/workflow.md` | `287cea745b8bb4d267378f935311d69dae39c585359aa59b928f89959d534bbb` |
| `docs/acceptance.md` | `0154cc13924f9fa2cf37b08ff9a0e3a524ee977ec28bf471eb3d7286867714b0` |
| `docs/tasks/DESIGN-003.md` | `fcfb9978e7204810a563cfc8a83d28a9ef7bc0231ce03d760d641e6fa04de628` |
| `docs/publication.md` | `a1ebfef50444ac70720abd8a4ca176f71a955d4a2e480c8eec2e3076a9f8d8e7` |

Additional exact revision-2 drafting inputs, retained before clarification:

| Input | SHA-256 |
| --- | --- |
| `docs/design/first-slice.md`, draft 1 | `3e7c140788f32924fea9f3ae3bd1cab8ab2f63c8003b45b6ef2d9a21b9eb19bd` |
| `docs/tasks/DESIGN-004.md`, revision 1 | `b8266afeb2061c1ccab81591a0c86fc348503807ba02f50caaf7aab6c120ab10` |

Build reference only: `backend/settings.gradle.kts`, `backend/build.gradle.kts` and
`backend/gradle/libs.versions.toml` at imported upstream `d9efd9f1546aa3020b54467366a215d378e8bbb3`.
They show the existing `:current` aggregator and library pins, not an approved dependency extraction
or independently qualified own-core runtime. Public primary security/encoding docs may be consulted.

## Scope and constraints

- Read: listed inputs, relevant public standards/security documentation and pinned build references.
- Write: this task and `docs/design/first-slice.md`; bounded refinements to K-APP and acceptance
  traceability, architecture/P2 continuation links and pending publication paths. External snapshots
  and static self-check evidence remain outside public Git.
- Decide: derive field/command invariants and the planned test portfolio; propose A1 authority /
  deployment profile. Owner approves new authority/deployment consequences, not routine naming.
- Dependencies: accepted F3/R1/S1/P2 scope and D1-D18. No unique SQL state, automatic clause merge,
  global clause reset, expensive-evidence impact oracle or owner credential in worker packets.
- No behavioral source/test changes, baseline deletion, live model/embedding calls, extraction,
  delegation, expensive runs, staging/commit/push or user-wide configuration changes.

## Acceptance

1. Name the canonical record identities/links, strict command/response fields and structural laws
   needed for the X/Y two-consumer journey, mixed approvals, retry and recovery cases.
2. Keep current identity/grants in Git, secrets out of plaintext Git/model context, bootstrap distinct
   from ongoing configuration, and every mutating route subject to the same authority path.
3. Disclose first supported client/network/credential mode and security limits; no claim that service
   access control is a qualified host sandbox or physical proof of human review.
   Specify key generation/bootstrap, exact approval requests and trusted human-answer delivery;
   distinguish target conversational UX from unqualified native harness/controller capabilities.
4. Separate pure model laws, actual Git atomicity/process checks and one assembled journey. Schedule
   expensive checks at the integrated M1 checkpoint, not each worker/edit; do not qualify changed
   integrated inputs using an unproven unaffected-scope assertion.
5. Give bounded implementation units/readiness dependencies, remaining closure gates and publication /
   review status. No premature implementation task dispatch or passing-test claim.
6. Self-check references/input hashes/coverage/local boundary, distinct from independent verification.

Escalate a real requirement conflict or authority/deployment choice with a concise recommendation.
Expected output: local concrete design, derived checks and implementation sequence, conversational
authority-profile proposal and exact continuation point. New public paths remain unapproved.

## Historical draft-1 checkpoint

Prepared `docs/design/first-slice.md` draft 1, SHA-256
`3e7c140788f32924fea9f3ae3bd1cab8ab2f63c8003b45b6ef2d9a21b9eb19bd`.
It defines logical records/strict codec/command boundaries, proposes A1 bootstrap and separate
human-owner/client credentials, current Git grants, revocation/rotation and loopback qualification,
and identifies I1-I3 with one integrated M1-C1 verification checkpoint. No SQLite or model runtime.

The owner acknowledged P2 before this task; its exact source draft was preserved by DESIGN-003.
No additional SSOT/foundation/clause-locality ruling is requested. A1 is a new initial authority /
deployment profile, not authorization to ship a general remote login/OAuth service or a host sandbox.

Author static self-check passed: seven FS sections, six case families referencing all nine K-APP
obligations, ten retained input digests, three build-reference files byte-matching the upstream pin,
preserved P2 drafting source, local links/whitespace, ignored/proposed-only new paths, no staged files
or tracked backend changes. Simple private-identifier scan found none; not a complete publication audit.

Inspected the actual pinned build layout/library catalog and relevant OWASP session-management
sections on random verifier entropy/storage/transport. Those sources support mechanism choices, not
certification of this unimplemented profile. No real credentials, behavioral tests, crash experiment,
source extraction, expensive run, model call, independent review or publication occurred.

The owner asked where the keys originate and how approvals actually occur. This exposed a design
gap: the first draft described bearer checks and a human client without binding a concrete pending
decision to a trusted user action. Draft 2 adds explicit request/answer records, key provenance and
the trusted controller boundary; it does not retroactively approve A1 or qualify a harness feature.

## Draft-2 checkpoint and continuation

Prepared first-slice draft 2, SHA-256
`d191f65fe0e5145ffd39da8bd8813d997678c796eadd374d9c00656fff380ea0`.
Owner-side CSPRNG generation/verifier-only bootstrap now has an explicit origin; planner credentials
are separate. A canonical pending request is distinct from a trusted owner answer and resulting
approval. The protected client fetches/renders the actual request, correlates a real human action and
sends the exact ID/digest; the language model is not trusted to assert that assent occurred.

Author static self-check passed: seven FS sections, seven derived case families, all nine K-APP
obligations referenced, twelve retained input entries, local links/whitespace and simple private-
identifier checks, no staged files or tracked backend changes. The direct approval command was
removed from the proposed command table in favor of request/resolve. No behavior/UI/credential-
isolation qualification, actual keys, model calls, independent review or publication occurred.

Next: explain key origin and the complete user-facing approval sequence, including that native chat
approval requires a protected controller and authenticated user-event correlation. A model report,
ordinary MCP access or a body-supplied event ID is not proof. M1's explicit human client can qualify
the service flow; it cannot certify the final conversational integration. Do not imply an existing
native API/safe same-user secret store or require the owner to read Markdown. Preserve A1 as proposed
until its actual interaction/security boundary is settled; implementation remains separately gated.

The owner then directed structured GUI choices (U1), with text only under a concrete changes-required
item and decision-complete presentation. Continuation is `docs/tasks/DESIGN-005.md`; free-text assent
is no longer a proposed alternative. A1, native presenter capability and human-only approval remain
unqualified, not approved by this UI direction.

T1 then selected a cooperative initial trust profile and accepted the shown screen as a starting
design, deferring anti-deliberate-bypass/visual refinement without waiving service authorization or
complete decision information. The pure content/reference subset proceeds as CORE-001. This does
not bulk-approve undisclosed credential/runtime/wire fields or count the complete M1 flow as built.
