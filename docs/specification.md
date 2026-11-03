# Writhelm product specification

Revision: 1. Status: proposed; not ratified for implementation or publication.
Exceptions: the approval-wave rule D1 in `workflow.md` and principles D2-D18 below are accepted;
the surrounding requirements remain drafts. Architecture F3 records the confirmed own-core direction;
the foundation comparison does not reopen that choice.
Owner requirement S1 below fixes Git SSOT for all canonical state. The conversational P2 architecture
scope is accepted in `docs/design/git-state.md`; undisclosed implementation/qualification details
remain proposed. U1 below fixes structured owner choices and decision-complete presentation;
particular renderers and human-only approval modes remain unqualified.
Canonical preparation task: [SPEC-001](tasks/SPEC-001.md).
Observable scenarios: [acceptance catalogue](acceptance.md).
Development protocol: [workflow](workflow.md).

## Purpose and boundaries

Writhelm is a self-hosted authority for project knowledge and governed agent work across coding
harnesses. A conversation is an interface, not the canonical record. The owner can recover what
was required, authorized, attempted, checked, accepted and integrated without reconstructing chat.

These requirements describe behavior, not a Kotlin stack, table layout, HTTP route naming or
upstream compatibility obligation. A maintained fork and a new core with selective reuse must be
evaluated against the same requirements and scenarios. The imported upstream baseline is evidence
of its own behavior, not evidence that Writhelm meets this specification.

## Actors and terms

- Owner: the explicitly authorized human decision-maker for a project; credentials represent that
  authority. The system does not prove that a human physically read a document.
- Planner: the primary human-facing session; designs, decomposes, requests decisions and coordinates.
- Worker: a bounded execution session assigned one canonical task revision.
- Verifier: an authorized reviewer independent of the implementation producer, with depth set by risk.
- Revision: an immutable identified version of content. A snapshot pins a compatible set of revisions.
- Evidence: reproducible observations tied to the exact requirements and implementation assessed.
- Acceptance: an authorized decision on a specific revision/result, not a synonym for a passing test.

Accepted principle D3: do not make individual decisions the canonical units of the coding knowledge
corpus or require its atomization into a product-model vocabulary. Decision-sized approval waves
(D1) concern the owner interaction, not storage granularity. Broader conceptual frameworks may inform
provenance, applicability, dependency and completeness reasoning without adopting their ontology,
classification rules or source material. The engineering-level knowledge unit remains to be specified.

## Required behavior

### Service and knowledge

**WH-01 — Central ownership.** One logical service owns canonical knowledge, workflow and authority
records. Thin clients share that state; coding repositories require neither corpus overlays nor
manually maintained metarepositories. Code and its history remain in coding repositories.

Accepted requirement S1: consistency/versioning must be structural through SSOT. All canonical
knowledge, authority and workflow state belongs in Git, not versioned content plus unversioned
independently canonical approval/task databases. A database may be a disposable, fully recoverable
projection with no unique canonical facts. The number of repositories/publication roots, transaction
protocol, secret handling and supported durability mode are concrete design choices, not selected
merely by this requirement. Proposed realization: `docs/design/git-state.md`.

**WH-02 — Identity and project isolation.** Authenticate callers and enforce project-scoped grants
on reads, writes, search, events, retries and alternate routes. Never take identity/authority from
request text. Missing authority fails closed; broad operational rights do not imply owner rights.

**WH-03 — Exact immutable knowledge.** Store and retrieve exact accepted UTF-8 content with revision
identity, digest and provenance. Changes create revisions. The service manages its knowledge Git
history internally; clients do not operate that Git repository. Uncommitted files are not accepted text.

**WH-04 — Addressable knowledge.** Support exact section/clause reads and historical reads. Stable
clause identity survives reordering/renaming; a split/merge explicitly records lineage or retirement
rather than silently assigning an old identity to a different obligation.

Accepted decision D4: a document (such as an SOP, contract or subsystem description) is the unit
of maintenance and revision; an addressable section is the unit of selective reading and packet
inclusion. ~~Sections do not acquire independent revision/approval lifecycles.~~ D18 refines this
overbroad wording: structural contract clauses retain their own approval/check applicability without
turning every clause into a separately maintained document or a fine-grained decision knowledge object.
References pin document,
revision and stable section identity, not just a heading or ordinal. Packet inclusion preserves
applicable content and its mandatory dependencies rather than substituting a free-form summary.

**WH-05 — Revision-bound ratification.** Proposed, accepted and superseded knowledge remain distinct.
Only an authorized owner action accepts the specified revision/digest. New proposals do not inherit
approval or silently change the accepted head. Supersession preserves prior decisions and content.

Accepted decision D6: distinguish prescriptions (requirements, SOP obligations, architectural
constraints) from factual descriptions and observations. Prescriptions require owner approval or
explicitly delegated decision authority. Facts require verifiable provenance and applicable evidence;
owner agreement does not prove them true. Unconfirmed findings/proposals remain labelled and cannot
silently acquire mandatory authority. A document may contain both: its container/type does not grant
every statement the same authority. A code/norm mismatch is a finding, not automatic permission to
rewrite the norm to match the implementation. D1 approval records need not become corpus knowledge units.

Accepted decision D18: contracts have stable structural clauses; changing one clause does not
automatically reset another clause's approval or checking status merely because the containing
document has a new revision. Track the changed meaning and its approval precisely. The planner
remains responsible for cross-clause consistency and explicitly identifying dependent clauses that
need reconsideration. This does not grant generic automatic change-impact inference or requalify an
old expensive test for a changed code tree; evidence remains bound to what it actually assessed.

**WH-06 — Reproducible snapshots.** Tasks and branches may pin different knowledge snapshots.
Addressed reads and packet assembly use the pinned snapshot, not whatever is latest. Missing or
conflicting pinned dependencies produce an explicit error; historical snapshots stay recoverable.

Accepted decision D5: an ordinary update notifies affected active tasks but does not silently rebind
or stop them; their snapshots remain unchanged. The new revision is available for new tasks. An
explicit authorized withdrawal of an unsafe old norm blocks its use, including in pinned snapshots;
affected active work stops at the next supported safe boundary. Resumption requires an explicit
input/snapshot update. Withdrawal preserves historical content and decisions for audit, not permission
to keep executing the withdrawn rule. Safe boundaries must be defined by the supported harness mode.

**WH-07 — Publication and recovery.** A failed or interrupted publication cannot expose partial or
uncommitted knowledge as accepted. Acknowledged durable writes survive supported process restarts.
Missing/corrupt accepted content is reported as an integrity failure, never replaced by a proposal.
Supported crash/durability assumptions must be documented and tested before operational use.

### Decisions and bounded work

**WH-08 — Design before implementation.** Requirements/scenarios and the consequential design
decisions are versioned and explicitly ratified before behavioral implementation. Worker discovery
of an intent, authority or design conflict triggers escalation, not an unapproved product change.
Under accepted decision D1, ratify one coherent decision and its disclosed consequences at a time;
map the ruling to exact resulting clauses/revisions. Meaning-preserving editorial assembly is not a
new semantic approval, and document packaging does not enlarge the ruling's scope.

Accepted direction U1: owner approval uses structured UI choices bound to the exact displayed
decision/item, not free chat remapped by the planner. User text is permitted only for "changes
required" under a concrete selected item. Output/display limits must preserve the information
needed to decide; short summaries and links do not establish completeness by themselves. Qualify
actual UI capability, protected human-action delivery and comprehensibility per supported profile;
do not promise universal native-GUI support or semantic completeness from structural counts.
One coherent decision can still cover several clauses under D1; independent unchanged clauses are
not new approval chores. Local research/contract: `docs/design/approval-interface.md` (DESIGN-005).

**WH-09 — Canonical delegation contract.** Every delegation has its own versioned task with objective,
exact inputs/knowledge references, read/write/decision scope, constraints, dependencies, acceptance
criteria, escalation conditions and expected output. Dispatch rejects an incomplete contract.
Workers receive that task, not a paraphrase of the planner's conversation.

Accepted decision D9: finalize the knowledge snapshot when preparing the execution-ready task
revision immediately before dispatch, not when first drafting the task. Bind the exact task, chosen
snapshot and mandatory packet; validate compatibility and absence of withdrawn obligations before
assignment. The snapshot need not be globally latest: an explicitly selected valid branch snapshot
is allowed. Drafts are not dispatch authority. After dispatch the bound inputs do not silently change;
ordinary updates and withdrawals follow D5 and semantic task changes require an explicit revision.

**WH-10 — Revision changes and execution ownership.** Parent/child relationships, dependencies and
claims are explicit. Dispatch cannot bypass unmet dependencies or an existing exclusive claim.
Scope/input/acceptance changes create a task revision and an explicit stop/rebind/resume decision;
an active worker cannot silently switch terms. Retries do not create duplicate logical assignments.

Accepted decision D15: dependencies state the readiness required by their consumer rather than
relying on one universal done flag. A verification plan may allow implementation to continue against
an implemented result with cheap checks while a shared expensive check awaits a later checkpoint.
Consumers are told the outstanding coverage; consuming the result does not certify it. The designated
integration/milestone gate still requires its prescribed checks. This must be planned explicitly, not
invented by a worker to bypass a dependency; existing acceptance and safety constraints remain binding.

**WH-11 — Authority is not a worker claim.** Workers cannot ratify product decisions, self-approve
results, name themselves as independent reviewers or grant review exceptions. Only the owner may
grant a narrowly scoped, reasoned exception, bound to its subject/revision and visible in the audit.
Changing session IDs alone is not independent review; record producer/reviewer provenance and limits.

### Context and harness lifecycle

**WH-12 — Mandatory closure.** Assemble the canonical task and transitive mandatory obligations from
its snapshot, with a manifest of included revisions/clauses and inclusion reasons. Missing, denied,
contradictory or oversized mandatory inputs cause explicit refusal/escalation, not silent omission.
Optional retrieval/memories are separately identified and cannot override accepted obligations.

Accepted decision D2: norms have explicit applicability scopes; a task inherits the norms applicable
to its affected areas and may add explicit obligations. The planner identifies affected areas but
cannot silently exclude their mandatory norms. Ambiguous applicability requires clarification.
The scope vocabulary and applicability mechanism are not selected by this decision.

Accepted decision D7: specificity or recency alone does not give a norm precedence over another
applicable norm. A local norm may refine a general one; a deviation requires an explicit authorized
exception identifying the rule and affected scope. Without such a ruling, conflicting applicable
obligations block affected work and are escalated, not silently resolved by the worker or retrieval
ranking. An unrelated nonconflicting task is not blocked by association alone.

Accepted decision D8: use ordinary engineering documents with explicit references/dependencies and
checks, not mandatory translation of all norms into a formal language. In supported modes, guarantee
preservation of declared mandatory inputs and enforce checkable structural conditions. Semantic
analysis helps discover contradictions and omissions but does not prove their absence. Distinguish
passed structural checks, semantic findings, unassessed/uncertain areas and explicit authorizations.
Known conflicts remain blocking under D7; uncertainty must not become a conformance claim. Completeness
of a declared dependency closure is not completeness of the natural-language model or its extraction.

**WH-13 — Explicit execution profiles.** Select model, reasoning effort, effective input ceiling,
output reserve and compaction policy per worker independently of the planner. Profile identity and
version are part of assignment provenance. Large available planner context need not be loaded fully.

**WH-14 — Enforced outgoing budgets.** In each supported harness mode, enforce the applicable input
ceiling on every outgoing request, including instructions, tool schemas, history, tool results and
compaction calls; enforce any declared output cap through a supported provider mechanism. Count
using the qualified model/route method and explicit safety reserves. Unknown accounting or an
unfit request fails closed; an alias/compaction threshold or chars-per-token heuristic is not proof.
For worker profiles whose tariff boundary is 272k input tokens, set a safe ceiling below that boundary;
do not assume the same tariff or tokenizer for every model, authentication route or provider.

**WH-15 — Compaction and restart continuity.** A worker may compact and resume without losing task
identity/revision, snapshot, scope, evidence links or open obligations. Recover those facts from the
service; a lossy summary is not authority. Summary failure is visible and cannot mark work complete.

Accepted decision D10: persist meaningful progress checkpoints after substantial results, on
blockers and before managed pauses/compaction, rather than requiring every tool call in the central
record. Include reported work, artifact/change/evidence locations, remaining work and open questions,
bound to the assignment. On restart reconcile checkpoints with actual artifacts; uncheckpointed work
may require reconstruction. A checkpoint is a progress report, not completion proof or authority to
change the task. Harness tool history may remain available without becoming the normative corpus.

**WH-16 — Portable lifecycle integration.** Use supported native dispatch/session/compaction surfaces
and qualify the complete workflow in a first, then a second harness. MCP connectivity alone is not
lifecycle enforcement. State the trust boundary: unrestricted host tools cannot be universally
controlled by an MCP service. Scope text is not filesystem/process isolation.

Accepted decision D13: hard prohibitions require technical enforcement in a qualified execution mode,
not only task instructions and after-the-fact diff review. Enforce service authority at the service
and host/file-operation restrictions at the execution boundary. Workers receive neither owner rights
nor direct access to service storage; their allowed write area is restricted by the execution mode.
Reviewers receive non-writable assessed code and a separate permitted report/scratch area. A shell
or subprocess cannot bypass the same restrictions. A mode unable to prevent these operations cannot
claim to provide the guarantee. Supported mechanisms and their limits require design/qualification;
semantic understanding and quality remain SOP/review obligations, not pretend technical guarantees.

Accepted initial profile T1: the owner explicitly chose a cooperative-agent first version, not
enterprise/adversarial assurance against intentional API/hook/UI bypasses that violate instructions.
For the initial human-approval channel, D13 is scoped accordingly: preserve real service grants,
exact request/item/version binding and U1's structured choices/completeness, but do not make anti-agent
attestation/OS isolation a prerequisite or claim those hard guarantees. Owner credentials are not
worker inputs and self-approval remains prohibited. This is not an assertion that any particular
Claude Code bypass necessarily requires several actions or violates its system prompt; that was the
owner's trust assumption, not a verified finding. Stronger execution isolation remains a separately
qualified capability before it is advertised. The shown synthetic screen is accepted as a design
basis; visual refinement may follow later, without deferring decision-information completeness.

### Evidence, review and completion

**WH-17 — Evidence applicability.** Evidence identifies task revision, knowledge snapshot, producer,
repository and actual code-tree identity, commands/checks, relevant environment, outcomes and retained
artifacts. Dirty trees require a digest of the assessed contents or an explicit refusal; branch name
or HEAD alone is insufficient. Changed assessed inputs invalidate automatic reuse of verification.

**WH-18 — Risk-based independent verification.** Reports are claims. Check against the original task,
reproduce consequential evidence and inspect critical paths proportionally to risk. Document review
scope, findings and untested limits. A summary rewrite, a different agent name or selective code
inspection alone is not verification. Unresolved blocking findings prevent acceptance progression.

Accepted decision D11: verification is a separate task in a fresh context, assigned to an authorized
reviewer who did not author the assessed changes. A different model/provider is not required; merely
changing role/session labels does not establish independence. The original task, exact assessed
result and evidence are inputs; the producer report is a set of claims to check, not authority.

The reviewer must not edit project code, including repository tests, or turn findings into fixes.
Its output is a review report; remediation belongs to a separate implementation task followed by
applicable verification. Reviewing a changed result requires identifying that new result explicitly.
The owner also permitted temporary reproduction checks in a designated scratch/sandbox scope without
changing the assessed project. Those checks do not authorize live models, paid experiments or unsafe
actions. A precise code trace may support a scenario without executing a destructive experiment;
the report states what was observed versus inferred and under which assumptions.

An objection to an implementation choice not explicitly constrained by the task must identify a
concrete scenario in which that exact code behaves incorrectly relative to the task/product, with
the governing requirement, code location/revision, relevant conditions/actions, expected behavior
and the claimed actual failure. An explicit task/accepted-product constraint may be cited directly;
the reviewer cannot invent a new requirement to make a preference blocking. Unsupported design
preferences remain recommendations, and missing product decisions are escalated as gaps. Separate
confirmed defects from hypotheses and state assumptions/limits rather than disguising them as proof.

**WH-19 — Distinct completion facts.** Proposed/accepted knowledge, implemented changes, verified
results, authorized result acceptance and integration are separate linked facts. A merge/integration targets
the verified and accepted result. If integration changes assessed inputs, record and verify the new
result before claiming that it inherits verification. Passing tests never grants owner approval.

Accepted decision D12: the owner delegates routine task-result acceptance and integration permission
to the planner within the approved task/design and its decision scope. The planner checks the original
criteria, consequential reviewer claims and evidence applicability; acceptance requires satisfied
criteria and no unresolved blocking findings. Owner approval is not requested after every worker.
The planner cannot change requirements, waive a known norm violation or grant itself an exception to
accept a result. Product/design changes, exceptions and agreed owner checkpoints still require owner
decisions. Acceptance authority is attributable and bound to the specific result, not inferred from
passing tests or an assistant role label. This does not automatically accept any current draft.

**WH-20 — Retry, concurrency and audit.** Mutations carry durable command identity and unambiguous
retry semantics. Replaying an identical completed command does not duplicate its effects; reusing
identity with changed semantics conflicts. Concurrent stale decisions cannot both win. Recheck
current access on replay. Audit records attributable decisions and their exact subjects; successful
acknowledgement cannot precede the durable state promised by the supported deployment contract.
Retention periods, if any, must be explicit and cannot silently turn a retry into a new authorization.

### Operations, publication and economics

**WH-21 — Preservation and lifecycle.** Ordinary project/document operations cannot destroy accepted
history, pinned snapshots or evidence. Define an authorized retirement/deletion process before
supporting it. Demonstrate consistent backup/restore and safe schema upgrades before operational use.

**WH-22 — Data and publication boundary.** Permission to inspect a private workspace never implies
permission to publish it. Explicit manifests/provenance govern public exports; no implicit export of
sessions, project corpora, secrets or evaluation workspaces. Test fixtures are genuinely synthetic.
Enforce access to private projects separately from licensing Writhelm's own source.

**WH-23 — Authorized experiments and measured economics.** Live inference, embeddings and paid
experiments require separate owner authorization. Model-free tests remain possible. When experiments
are authorized, account for planner + workers + verification + compaction + rework and distinguish
measured route-specific usage/pricing from estimates. Quality/cost targets need owner-approved values.

Accepted direction D14: expensive verification is broader than paid/model calls: elapsed time and
machine/resource load matter too. Cheap project-approved tests may run during normal iteration;
expensive suites run only at designated acceptance checkpoints, possibly less often than individual
task handoffs. Neither an edit nor handing off a task automatically authorizes such a suite.
Plan their execution deliberately instead of repeatedly loading the machine without useful progress.
An expensive check not yet run is outstanding verification, not a passing result or permission to
claim its coverage. Separate authorization requirements for live/paid experiments remain unchanged.

The owner accepted project-defined cheap/expensive test classification and a planned verification
schedule, with expensive checkpoints potentially covering groups of tasks or an integration milestone.
Use cheap/targeted checks during iteration; record the exact assessed state of each expensive run and
do not repeat one merely because another edit occurred. Deferred required verification remains
visible and holds the acceptance gate that requires it. Do not invent a universal duration threshold
or infer test cost from whether it calls a model. Concrete schedules and exception policy still need
project-specific design. The earlier model-run authorization proposal was not ratified by D14.

Accepted direction D16: maximize the effective coverage and defect-detection capability of cheap
verification rather than relying on expensive end-to-end runs to find most ordinary defects.
Load-bearing components need focused checks of their behavior, important execution conditions,
boundaries and failures. Use the smallest practical sufficient suite for explicit coverage obligations,
not a large test count as a proxy for quality or one giant test hiding many expensive executions.
A few representative real user scenarios belong at end-to-end level; they validate assembled
behavior rather than substitute for component, contract and focused integration checks.
Test cost and test scope are separate dimensions: an expensive check is not necessarily end-to-end,
and a narrow real-store integration check need not be expensive.

If an E2E failure can be reproduced at a narrower cheaper level, add the corresponding regression
check in an implementation/remediation task; otherwise explain why that failure needs the broader
setup. The read-only reviewer may report the missing check but does not edit repository tests.

The finite coverage obligations and adequacy criteria must be specified per critical component and
validated against the actual implementation/tests. No universal minimum cardinality, full feasible-path
coverage or percentage of bugs detected is promised. Structural coverage requires meaningful outcome
assertions; executing a branch does not prove it correct. Research methods such as MC/DC, combinatorial
interaction coverage and cost-aware set-cover minimization are candidates to apply where justified,
not mandatory whole-product formalization or claims of an optimal suite.

Accepted direction D17: write the contract first, then derive tests from it. Expected behavior comes
from the product requirement/authorized design, never from copying the implementation's answer.
Give contractual obligations stable references and distinguish their actual tested cases from a mere
reference in test text. Behavioral promises and supporting system properties may be related without
turning an algorithm description into its own justification. The scenario catalogue is a derived
design aid, not a second independent authority overriding the contract. Exact binding/check tooling
is subsequent design work; no private SOP text, annotations or project examples are imported.

The first local contract draft is `docs/contracts/accepted-knowledge.md` (K-APP); its wording and
implementation-sensitive open points remain proposed. Its addition does not ratify all draft clauses.

Research references for selecting methods (not mandated tools or imported designs):
[test portfolio/pyramid](https://martinfowler.com/articles/practical-test-pyramid.html),
[test-suite minimization](https://llvm.org/pubs/2005-09-PASTE-GreedySuiteMinimization.pdf),
[MC/DC](https://shemesh.larc.nasa.gov/fm/papers/Hayhurst-2001-tm210876-MCDC.pdf),
[combinatorial testing](https://csrc.nist.gov/pubs/sp/800/142/final).

**WH-24 — Govern Writhelm's own development.** Use the same versioned tasks, design approval,
evidence applicability, independent review and acceptance distinctions while building Writhelm.
Until the service can own these records, use explicitly identified canonical documents in this
repository as a temporary manual protocol, not a claim that server enforcement already exists.

## Milestones, not weaker requirements

- **M0 — Contracts:** ratify this specification/scenarios; resolve implementation-sensitive open
  questions; compare implementation options with evidence and approve one bounded first-slice design.
- **M1 — Accepted knowledge:** satisfy the first-slice gate in the scenario catalogue. No claim yet
  of governed worker execution, general clause lineage/mandatory closure, budget enforcement or
  operational readiness. The refined first slice does include stable clauses, scoped approval and
  two pinned consumer manifests under K-APP draft 2; these are not executable task assignments.
- **M2 — Bounded work:** canonical tasks, mandatory packets, producer evidence and independent
  verification through one harness, including interruption/resume and qualified budget enforcement.
- **M3 — Portability and operations:** repeat through a second harness; complete clause/snapshot
  coverage, recovery/upgrade/backup gates and an explicitly authorized quality/economics pilot.

All requirements remain in scope across these milestones. Missing later capabilities are declared
limitations, not silently dropped requirements. A milestone cannot pass by redefining its gate.

## Open decisions before implementation

1. Foundation direction is settled: Writhelm-owned core with selective upstream reuse, not a
   maintained fork (architecture F3). Remaining design: component/dependency boundaries, adapted
   tests, update costs and license obligations. The comparison is not another foundation ballot.
2. Exact authority provisioning/revocation, identities and supported initial trust/deployment mode.
3. Persistence/publication/recovery protocol and supported durability guarantees; command retention
    and project retirement policy within S1's Git-SSOT constraint. No independently canonical SQLite
    journal/authority store is permitted; an optional derived index is not an additional SSOT.
4. Versioned wire schemas, clause lineage/snapshot conflict semantics, evidence/tree identity and
   task-rebinding rules; define before their respective milestone is implemented.
5. Supported harness/provider profiles and accounting methods; qualify hard limits independently
   from compaction plumbing. No live provider or billing qualification is implied by mock tests.
6. Concrete operational/performance limits and quality/cost targets; do not invent acceptance numbers.

## Excluded from the initial release

A new coding harness or model runtime; universal RAG; automatic ingestion of private chat archives;
enterprise multi-tenancy; a large UI; automatic support for every harness. These exclusions do not
remove authentication, project isolation or the supported-mode enforcement obligations above.
