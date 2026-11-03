# DESIGN-002 — Clause authority and pinned-input representation

Task revision: 1. Status: R1 representation direction accepted; design outputs statically self-checked.
Local design only. Executor: primary session, no delegation.
Authorization: owner directed continuation into the concrete own-core first-slice design.

## Objective

Design the relation between immutable document revisions, stable structural clauses, scoped
approval, retained check applicability and two pinned consumer manifests. Resolve mixed approved /
unapproved edits without a whole-document approval shortcut or a new merge engine. Present one
coherent representation decision conversationally; do not reopen the foundation or implement it.

## Exact inputs

Retained input bytes are identified by SHA-256. These are drafting inputs with accepted D1-D18/F3
distinguished from proposed wording, not a bulk-ratified specification.

| Input | SHA-256 |
| --- | --- |
| `README.md` | `92515454c3c3b4188d0e865f31d60c005b548fb66e88de903179aabce796f067` |
| `AGENTS.md` | `e737009d14320aa0bc1d5a8006a2ebfdc3862c282504f94a436e645513f035e9` |
| `docs/specification.md` | `10e20626b301569a468c1ec91de4ce828505af5ccd75fa394844edd10124c61e` |
| `docs/contracts/accepted-knowledge.md`, draft 2 | `2471ce66f90abe18f062c68ba4c79a43298a26b02f4966498e78a7313c17d451` |
| `docs/workflow.md` | `287cea745b8bb4d267378f935311d69dae39c585359aa59b928f89959d534bbb` |
| `docs/design/foundation.md`, revision 2 | `4a640a84f562797463cd2aabc3b4714d8930088654fe49d3a416607e5267b797` |
| `docs/architecture.md` | `63a71c3ac32ff569930b54adc9a395b11a9bd6ffdd5c536ff4d679cde247b564` |
| `docs/publication.md` | `008254d29a388c9c0e2464be880ec505a77908d3fa7c8bd39da76a5678fba42e` |
| `docs/tasks/DESIGN-001.md`, revision 2 | `2819f84e21930c29c85656ef3ded49692f4c630064b633c230b3a0acc5bef617` |

## Scope, dependencies and constraints

- Read: listed sources and applicable document links; imported baseline only if a new reuse claim
  needs checking. Existing comparison is static evidence, not a qualified extracted core.
- Write: this task and `docs/design/knowledge-model.md`; local links/checkpoint updates in
  `docs/architecture.md`, `docs/contracts/accepted-knowledge.md`, `docs/tasks/DESIGN-001.md` and
  proposed publication paths in `docs/publication.md`. Retain exact local snapshots outside Git.
- Decision scope: propose representation/transition invariants and derived checks; owner approval
  applies to a coherent semantic choice, not all implementation details in the document.
- Dependencies: own-core F3 and D1/D4/D5/D6/D8/D9/D17/D18. Knowledge documents stay revision units;
  planner handles semantic consistency. Prior contract wording and G1-G6 are not implemented authority.
- No behavioral source/test changes, baseline deletion, source extraction, live models/embeddings,
  expensive checks, delegation, staging, commit or push. Do not modify other repositories/config.
- Persistence/auth/retry/durability/wire details may be identified as subsequent decisions but are
  not silently selected by this task. No operational or executable-dispatch claim.

## Acceptance and escalation

1. Specify exact identities/links for content, approval, continuity, evidence and immutable manifests.
2. Work through X0/Y0 -> X1/Y0, both consumers, mixed X1/Y1 with X-only approval, structural edits,
   changed context, restart/read errors and unchanged Y with changed implementation evidence.
3. Explain what is mechanically enforced versus planner assessment; hash equality is not proof of
   semantic equivalence, consistency, inherited completeness or test applicability.
4. Preserve scope of owner approval; no document-wide acceptance through container labels, reuse
   of clause IDs or inclusion in a new manifest. Ordinary updates cannot silently change old inputs.
5. Identify focused checks, real-store obligations, limitations and subsequent design decisions.
6. Self-check links/IDs/traceability, retained input identities and local publication boundary;
   distinguish this from independent review and behavioral verification.

Escalate a real accepted-rule conflict or consequential representation tradeoff in the conversation,
with recommendation and concrete consequences. Do not ask the owner to read Markdown or settle
routine naming. Output: local design, concise conversational proposal, evidence limits and a precise
continuation point. No implementation acceptance or publication follows.

## Result and resumption checkpoint

Prepared `docs/design/knowledge-model.md` draft 1, SHA-256
`1f395a22c3ce886333eaede5ed14a0e091b484eb50fc5ad46a9c8862b5bf47af`.
Its R1 proposal separates whole-document content, scoped authority/continuity and immutable consumer
selection; it includes context and the explicit mixed-revision selection consequence. Four worked
examples cover independent change, mixed approval, context-dependent meaning and evidence binding.

Author static self-check passed: document links/whitespace, eight unique model-case IDs referencing
all nine K-APP obligations, nine retained input digests, ignored/proposed-only new paths, no staged
files and no tracked backend changes. A simple private-identifier scan found none; it is not proof
of a complete publication audit. No behavioral check, extraction, model call, expensive suite,
independent review or publication occurred. The design remains a proposal, not verified behavior.

The owner accepted the conversational R1 proposal, including explicit mixed-revision selection with
planner compatibility checking. Approval covers the disclosed representation consequences, not all
draft fields, K-APP wording, held G1-G6 design or implementation/publication. Preserved the exact
draft-1 bytes identified above before updating the status/mapping in design revision 2.

Continue in `docs/tasks/DESIGN-003.md`: publication/recovery/retry and supported durability, followed
by authority provisioning, concrete authoring/wire representation and planned test checkpoints.
No repeated R1/F3 decision request is needed. Independent review/behavioral qualification remain
outstanding; semantic approval is not verification.
