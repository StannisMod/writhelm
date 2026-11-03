# SPEC-001 — Product specification and observable acceptance

Task revision: 2 (scope expanded by the owner's contract-first direction).
Execution: primary planning session; no worker delegated.
Status: draft artifacts prepared, pending owner ratification; no behavioral implementation.

## Objective

Specify Writhelm's required behavior and observable positive/negative acceptance scenarios before
deepening implementation design. Apply the intended governed-work process to Writhelm itself.

## Canonical inputs

- `README.md`: product purpose and current implementation limits.
- `AGENTS.md`: work contracts and publication boundary.
- `docs/roadmap.md`: capability scope and milestone gates (proposed).
- `docs/architecture.md`: architecture hypotheses, accepted fork/layout history and reopened foundation choice.
- `docs/publication.md`: current approved publication scope and exclusions.

These are current drafting inputs, not a claim that every proposal is accepted knowledge. The owner
authorized this specification/scenario pass and self-application of the process. Detailed requirements,
procedure, implementation choices and new public paths still require their corresponding approval.
No private project document or historical transcript is an input to be copied into the output.
The committed input baseline is Writhelm `dbc2d6d7`; `docs/architecture.md` also had the uncommitted
revision-2 source-check proposal at task preparation. That proposal is an input hypothesis, not a
ratified implementation contract. The checkpoint change keeps its reopened status explicit.

## Scope and responsibility

- Read: the inputs above and relevant pinned upstream source when needed to distinguish facts from hypotheses.
- Write: new local drafts `docs/specification.md`, `docs/acceptance.md`, `docs/workflow.md`,
  `docs/tasks/SPEC-001.md`, and `docs/contracts/accepted-knowledge.md`; update `docs/architecture.md`
  and `docs/publication.md` to expose this checkpoint. Revision 1's scope excluded the contract path;
  revision 2 records its explicit addition for local drafting only, not publication.
- Decide: wording, traceability and proposed decomposition. Do not select fork versus new core,
  grant owner authority, ratify requirements or invent performance/cost acceptance thresholds.
- Constraints: no behavioral code, source deletion, live models/embeddings/paid experiments,
  private-material export, user-wide configuration changes or automatic publication.
- Dependencies: existing public scope and owner direction to prepare these documents. There is no
  implementation prerequisite for drafting; upstream regression success is not Writhelm acceptance.

## Acceptance criteria

1. Requirements have stable IDs covering knowledge, authority, tasks, context, harness lifecycle,
   verification, recovery, publication and economics without committing to upstream internals.
2. Every requirement maps to an observable scenario; concurrency, stale evidence, omitted mandatory
   context, retries and compaction/restart have negative cases and state assertions.
3. First-slice versus later gates are explicit; no scenario is marked passed without execution.
4. Reopened implementation choices and all non-ratified decisions are visible.
5. The manual bootstrap process separates proposed/accepted and implemented/verified/integrated;
   owner approval applies to exact revisions and delegation requires a separate canonical task.
6. Local document validation checks IDs, coverage and links. Label it a self-check, not independent
   verification or a product test. New-file publication remains pending explicit scoped approval.

## Escalation and output

Escalate conflicting requirements, a missing canonical reference, needed scope changes, private-data
provenance concerns or any implementation/publishing request beyond this contract. Do not resolve
product tradeoffs by silently shrinking the specification.

Expected output: the four preparation drafts plus the initial contract draft, traceability/self-check result and a concise owner review
request. Next task after ratification: compare implementation strategies against accepted scenarios.

## Evidence and decisions

- D1 / revision 1: owner accepted one coherent decision as the unit of approval, with disclosed
  consequences and a mapping to affected clauses/revisions; documents accumulate the rulings.
  Meaning-preserving editorial assembly does not reopen a decision; semantic changes do.
  Applied to `docs/workflow.md` D1, `docs/specification.md` WH-08 and `docs/acceptance.md` AC-30.
  This approves the wave rule only, not the remaining specification or its publication.
- Requirement/scenario drafts: revision 1; unratified.
- D2 / revision 1: owner accepted explicit norm applicability scopes, inheritance by affected task
  areas and explicit additions; the planner cannot silently exclude inherited obligations and must
  clarify ambiguous applicability. Applied to specification WH-12 and scenarios AC-14/AC-15.
  Scope vocabulary and implementation remain open; the rest of the specification is not ratified.
- D3 / revision 1: owner ruled out individual decisions as coding knowledge units. Conceptual
  frameworks may inspire the design but do not mandate an ontology, fine-grained atomization or
  import of their source material. D1 remains an interaction rule, not a knowledge storage model.
- D4 / revision 1: owner accepted documents as maintenance/revision units and stable addressable
  sections as reading/packet units, without independent section lifecycles. References pin document,
  revision and section; packets preserve applicable content and mandatory dependencies. Applied to
  specification WH-04 and scenario AC-04. Physical storage/markup remain implementation choices.
- D5 / revision 1: owner accepted notification without automatic stop/rebinding on ordinary norm
  updates; explicit withdrawal of an unsafe old norm blocks affected work at supported safe boundaries
  and requires explicit updated inputs before resumption. Historical snapshots remain auditable,
  not executable authority for withdrawn obligations. Applied to specification WH-06 and AC-06.
- D6 / revision 1: owner accepted the distinction between prescriptions and factual knowledge.
  Prescriptions need owner/delegated approval; facts need verifiable sources and current evidence.
  Unconfirmed material cannot create obligations; mixed documents do not flatten authority and code
  mismatches do not amend norms. Applied to specification WH-05 and AC-05.
- D7 / revision 1: owner accepted explicit authorized exceptions rather than implicit specificity/
  recency precedence for conflicting norms. A local refinement cannot silently weaken a general rule;
  unresolved conflicts block affected work and escalate. Applied to WH-12 and new scenario AC-31.
- D8 / revision 1: owner accepted engineering documents with explicit links/checks, without mandatory
  formalization. Structural/declared-closure guarantees are distinct from fallible semantic analysis;
  uncertainty/no finding is not proof of completeness or consistency. D7 blocking behavior remains.
  Applied to WH-12 and new scenario AC-32.
- D9 / revision 1: owner accepted snapshot finalization during execution-ready task preparation just
  before dispatch, with exact task/snapshot/packet binding and compatibility/withdrawal checks. A
  valid chosen branch snapshot need not be globally latest; dispatched inputs follow D5 rather than
  silently changing. Applied to WH-09 and new scenario AC-33.
- D10 / revision 1: owner accepted meaningful progress checkpoints at substantial results, blockers
  and managed pauses/compaction, rather than mandatory central logging of every tool call. Resume
  reconciles reported progress with actual artifacts; checkpoints do not prove completion or fresh
  verification. Applied to WH-15 and new scenario AC-34.
- D11 / revision 1: owner accepted separate, fresh-context verification by a non-author, without
  requiring another model/provider, and explicitly prohibited reviewer code edits. Objections to
  implementation choices not specified by the task require concrete scenarios demonstrating the
  claimed incorrect behavior of the exact code relative to the task/product. Applied to WH-18,
  workflow verification and new scenarios AC-35/AC-36. Remediation is not a review-side write scope.
  Owner accepted the clarification that reproduction may use a designated scratch/sandbox without
  changing the project; a scenario may instead be justified by a precise code trace, with assumptions
  and observed/inferred status explicit. Existing experiment/safety restrictions remain unchanged.
- D12 / revision 1: owner delegated routine task-result acceptance and integration permission to
  the planner within approved task/design scope, with criteria, consequential reviewer claims,
  evidence applicability and absence of blocking findings checked. Requirements changes, exceptions
  and agreed owner checkpoints remain owner decisions. Applied to WH-19, AC-23 and workflow step 8;
  this delegation does not itself accept the current preparation artifacts or permit their publication.
- D13 / revision 1: owner accepted technical enforcement of hard prohibitions in qualified modes:
  service authority and host/write boundaries, no worker owner/store access, read-only reviewer
  project code with separate report/scratch writes. Shell/subprocess routes cannot silently bypass
  guarantees; an incapable mode cannot claim them. Applied to WH-16, AC-37 and bootstrap caveat.
- D14 / revision 1: owner broadened the cost issue beyond model calls to expensive tests, including
  long runtime and machine load. Cheap tests may support iteration; expensive suites belong only at
  designated acceptance checkpoints and may run less often than each task handoff. Applied to
  WH-23 and AC-38. Detailed classification/checkpoint/exception policy remains open; the preceding
  model-run authorization proposal is not recorded as accepted. No private project examples copied.
  Owner also accepted project-defined classification, cheap/targeted iteration and scheduled costly
  checks across task groups or integration milestones, with exact-state evidence and explicit pending
  verification. A gate requiring a deferred check cannot be marked passed. No universal time cutoff.
- D15 / revision 1: owner accepted consumer-specific dependency readiness instead of universal done.
  A plan may permit implementation on implemented/cheap-checked results before a joint expensive
  checkpoint, while exposing outstanding coverage and keeping the required integration/milestone
  gate closed. No ad-hoc worker dependency bypass. Applied to WH-10 and AC-39.
- Unratified impact-reuse proposal: the owner identified that neither producer assertions, planner
  judgment nor generic static analysis establishes a reliable unaffected scope. Do not treat that
  proposal as accepted. Prior results remain evidence for the assessed state; changed required checks
  can await the next authorized checkpoint without automatic expensive reruns. Generic reliable
  impact inference is not an established system capability.
- D16 / revision 1: owner directed maximal effective cheap verification of load-bearing components
  with a minimal sufficient test portfolio for important paths/conditions, and a few real user E2E
  journeys rather than E2E finding most ordinary errors. Applied to WH-23 and AC-40. Exact adequacy
  criteria/optimization methods remain design work, not a claimed proof of all-path completeness.
  Owner acknowledged the clarification on finite adequacy criteria, cost versus test level and
  moving reproducible E2E-discovered defects into cheaper regression checks when feasible. Public
  research references are method candidates, not a mandatory framework. Added AC-41.
- D17 / revision 1: owner directed contract-first testing rather than starting from a test portfolio.
  Applied to WH-23 and catalogue guidance; prepared local contract draft K-APP, without treating its
  wording/open implementation choices as already accepted. Scenario examples remain subordinate to
  the contract; no private source text/code or project examples are publication inputs.
- D18 / revision 1: owner clarified structural contracts: changing a clause must not automatically
  reset another; the planner owns consistency review. Corrected the overbroad D4 sentence in place,
  refined WH-04/05 and K-APP, and added AC-42. Clause-level approval/applicability does not mean
  atomizing the knowledge corpus into decisions or assuming generic reliable code-impact inference.
- Branch-integration clarification: the owner pointed to existing worktree/Git and semantic
  merge-resolution practice. The preceding proposal about competing clause edits is not ratified as
  a new reconciliation mechanism. Reuse the established branch integration approach; distinguish
  text conflicts from semantic consistency and the merged tree from either input branch. No private
  SOP text/examples or project-specific repository/commit rules were imported. Server command
  concurrency, if needed, remains a separate implementation concern rather than a new product fork.
- D16 self-application finding: the catalogue's earlier instruction to test faults/concurrency with
  real storage and public transports was overbroad. Replaced it with narrowest-sufficient-level
  checks, real-boundary requirements for actual durability claims, focused adapter checks and a few
  E2E journeys. Added a proposed M1 allocation; no implementation/test execution is claimed.
- Document self-check after D1-D10: 24 unique requirements / 34 unique scenarios, complete ID
  coverage, links, whitespace and private-pattern checks passed. The four drafts remain local-only
  and nothing is staged. Reviewed interactions D4/D6 (document lifecycle versus statement authority),
  D5/D9 (withdrawal versus pinned inputs), D7/D8 (known conflicts versus fallible detection) and
  D6/D10 (progress claims versus evidence): no contradiction identified in this self-review.
  Most behavior remains unimplemented; upstream document versions are only a partial basis for D4,
  and baseline test success does not verify any new D1-D10 product gate.
  These are document self-checks, not product tests or independent review.
  After D11-D12, repeated structural validation passed for 24 requirements / 36 scenarios and the
  new conceptual W1 walkthrough. W1 follows proposal, bound dispatch, checkpoint/resume, read-only
  review, scenario-backed defect, separate remediation, re-review and delegated acceptance. No
  contradiction identified in this self-review; execution/enforcement are not tested by this trace.
- Independent verification: not performed.
- Owner acceptance of detailed requirements/procedure: pending.
- Publication approval for new paths: pending.
- Implementation/integration: not applicable to this preparation task.
