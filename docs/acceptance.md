# Observable acceptance scenarios

Revision: 1. Status: proposed; no scenarios below are claimed as passing Writhelm tests.
Target: [specification revision 1](specification.md). Preparation task: [SPEC-001](tasks/SPEC-001.md).

## Test rules

Use synthetic projects P and Q, owner O, planner L, worker W and independent verifier V. Distinct
credentials and recorded provenance, not just different session labels, establish their roles.
Documents A/B and trees T1/T2 are synthetic revisions, not renamed private examples.
Place each check at the narrowest level that can establish its property. Pure policy/transition
checks may use controlled inputs and doubles; durability, transaction, filesystem and process-crash
claims need the relevant real selected boundary, not a mocked promise of success. Narrow integration
checks do not require a whole harness or every public transport. Use focused transport/auth/contract
checks plus a few assembled user journeys; do not repeat every branch/fault case through the full
stack. External model/provider behavior may be mocked for model-free harness checks, with those
limits explicit. Every automated run records its exact test/version/environment and artifacts.
A mocked result does not qualify real persistence, live model quality, provider acceptance, billing
or unsupported deployment modes. Cost classification remains separate from test level.

The tables define Given / When / Then behavior, not test-framework code. Each scenario needs executable
checks before its milestone can pass. Negative outcomes must assert unchanged authoritative state and
absence of prohibited side effects, not merely an error string. Unsupported behavior cannot pass as
a successful no-op. Add concrete parameters when the corresponding contract/design is ratified.

Under accepted D17, these scenarios are derived design aids: write the governing contract before
writing its tests, and make the tested clauses/cases explicit. Do not treat this catalogue or an
existing implementation as an independent source of expected behavior. The first local contract
draft is `docs/contracts/accepted-knowledge.md`; AC-01/03/04/05/06 provide related example cases,
not evidence that its clauses are verified or a substitute for resolving its open decisions.

## Knowledge, access and publication

| ID | Requirement | Given / When | Required observation |
| --- | --- | --- | --- |
| AC-01 | WH-01, WH-03 | O accepts A; two thin clients read it over supported HTTP and MCP | Identical pinned revision/digest/text; no corpus overlay or client Git administration required |
| AC-02 | WH-02, WH-11 | W or an ungranted caller forges actor/owner fields, uses broad operational privileges, or targets Q | No unauthorized read/write/decision; no owner authority inferred from claims or ADMIN alone |
| AC-03 | WH-03 | Propose UTF-8 text with CRLF, non-ASCII and an explicit final-newline variant; read its revision | Exact byte-derived digest and text round trip; earlier revision remains unchanged |
| AC-04 | WH-04 | Reorder/rename document sections, then split/merge one; read by pinned document revision and stable section ID | Reordering/renaming preserves references; an old task reads the exact old section; document revisions do not blanket-reset clause approval/check applicability (D18); split/merge lineage or retirement is explicit; no identity silently reused |
| AC-05 | WH-05 | A is accepted; W proposes B, confirms a code fact, or describes nonconforming code in a mixed-content document | A remains authoritative until an authorized exact-change approval; factual verification needs evidence, not owner assent; no proposal/fact/container label silently creates an obligation; implementation mismatch is reported rather than rewriting the norm |
| AC-06 | WH-06 | Two tasks/branches pin S1/S2; a norm receives an ordinary update, then an old unsafe revision is explicitly withdrawn | Ordinary update notifies affected tasks without replacing snapshots or stopping them; withdrawn obligations cannot authorize new work, affected active work stops at the supported safe boundary, and resumption requires explicit input update; historical text remains readable; no latest fallback |
| AC-07 | WH-07 | Publication fails before/after content storage and around authority commit; restart after each injection | Only a whole durable revision/decision is exposed; no uncommitted acceptance, orphan promotion or lost acknowledged write |
| AC-08 | WH-07 | Accepted content or its referenced object is missing/corrupt | Explicit integrity failure; no silent substitution from newer proposals or a mutable worktree |

## Workflow and authority

| ID | Requirement | Given / When | Required observation |
| --- | --- | --- | --- |
| AC-09 | WH-08 | Design is unratified or worker discovers an intent/scope conflict; implementation dispatch is attempted | Dispatch is blocked or work escalated before the unapproved change; proposed design is not treated as accepted |
| AC-10 | WH-09 | Remove each required task field/reference in turn; dispatch; then dispatch a complete task | Every incomplete contract is refused; successful worker receives the canonical task revision and exact input references |
| AC-11 | WH-10 | Dependency is unmet, two workers race for a claim, or dispatch response is lost and retried | No premature dispatch; at most one exclusive owner; retry returns the same logical assignment |
| AC-39 | WH-10, WH-19, WH-23 | A implements a mechanism, B consumes it, and an expensive checkpoint checks both together; the plan permits B after A's implementation/cheap checks | B can proceed at its explicitly required readiness without certifying A; pending expensive coverage is visible to B; the shared integration/milestone gate remains closed until prescribed verification passes; no circular demand for a joint test before B can exist |
| AC-40 | WH-18, WH-23 | Design checks for a critical component and a few full user journeys; introduce representative condition/boundary/failure defects | Cheap focused tests have explicit finite coverage obligations and behavior assertions and detect the designated component defects without full end-to-end setup; E2E checks validate assembled journeys; uncovered obligations and limitations remain visible; test counts/line coverage alone cannot satisfy adequacy |
| AC-41 | WH-18, WH-23 | E2E finds a defect that has a cheap isolated reproduction; separately consider one requiring the real assembled environment | The implementer/remediation worker adds the cheaper regression where feasible; a genuinely necessary broader setup has a recorded reason; the reviewer reports findings without changing project tests; no requirement to blindly rerun the expensive suite after each edit |
| AC-42 | WH-04, WH-05, WH-18 | A contract has accepted clauses X/Y; change X and create a new document revision, then identify a real dependency affecting Y | Document revision alone does not reset Y's approval/check status; changed X needs applicable approval; the planner explicitly handles cross-clause consistency/dependencies; old test records do not become proof of changed code by implication |
| AC-12 | WH-09, WH-10 | Revise scope/inputs/acceptance while a worker is active or compacted | Old assignment remains identifiable; explicit stop/rebind/resume is required; evidence does not silently acquire new terms |
| AC-33 | WH-06, WH-09 | A task waits in draft while its SOP changes; prepare its executable revision, including a deliberately older valid branch snapshot | Finalization binds the exact task/snapshot/packet and rechecks compatibility/withdrawals before dispatch; it neither blindly uses draft-era inputs nor forces globally latest versions; assigned inputs remain immutable |
| AC-13 | WH-11, WH-18 | Producer attempts self-review, changes session name, or grants itself an exception | Independent gate remains closed; only an authorized owner can record a subject/revision-scoped exception with reason |

## Context and harness behavior

| ID | Requirement | Given / When | Required observation |
| --- | --- | --- | --- |
| AC-14 | WH-12 | A task affects an area with scoped norms and transitive dependencies; the planner omits a direct norm link; optional retrieval disagrees | Applicable inherited norms still enter the pinned mandatory closure and inclusion manifest; optional material remains separately labelled and non-authoritative |
| AC-15 | WH-06, WH-12 | Applicability is ambiguous, a mandatory reference is missing/denied/conflicting, or closure exceeds available budget | Explicit clarification/refusal/escalation; no partial packet presented as complete; no dispatch based on omitted obligations |
| AC-31 | WH-11, WH-12 | An accepted local SOP contradicts an accepted general SOP; make the local one newer, then explicitly authorize a scoped exception | Specificity/recency does not silently win; affected work remains blocked until the conflict is resolved; an exception permits only its authorized scope; unaffected work stays available |
| AC-32 | WH-12, WH-18 | Structural checks pass but semantic analysis is absent, uncertain or finds nothing; separately inject a known contradiction | Results distinguish structural success from uncertain/unassessed semantics; no claim that all meaning is complete/consistent; the known contradiction still blocks affected work |
| AC-16 | WH-13 | Parallel workers have different model/effort/context/output/compaction profiles from the planner | Each receives its specified versioned profile; no cross-worker or planner-profile leakage |
| AC-17 | WH-14 | Fixed instructions/tools, history or a tool result push a normal/compaction request over its ceiling | Every outgoing request is checked; no oversized send; unsupported accounting fails closed; declared output limit reaches the qualified provider boundary |
| AC-18 | WH-14 | Configure a worker for a route with a 272k tariff boundary; test threshold edges and reserve exhaustion | Qualified accounting proves sends remain below the profile's safe ceiling; aliases/heuristics alone cannot satisfy the assertion |
| AC-19 | WH-15 | Compact a native child, restart and resume; separately inject summary failure | Task/snapshot/scope/open obligations/evidence are restored from canonical state; summary failure is visible and never completes the task |
| AC-34 | WH-15, WH-17 | W checkpoints a passing test, changes assessed code and crashes before the next checkpoint; then resume | Recover the bound task and reported progress, reconcile with actual artifacts, expose/reconstruct uncheckpointed work and reject old test applicability to changed code; checkpoint text alone does not complete or verify the task |
| AC-20 | WH-16 | Repeat the same bounded workflow through a second harness; try a documented unsupported bypass | Canonical semantics survive without importing chat history; supported enforcement and bypass limits are accurately distinguished |
| AC-37 | WH-02, WH-16, WH-18 | In a qualified execution mode, W attempts a write outside its task area or direct service-store/owner access; V attempts to edit a project test through shell/subprocess | Prohibited operations are prevented at the applicable boundary, not merely found in a later diff; allowed report/scratch writes still work; an incapable mode fails qualification rather than claiming enforcement |

## Evidence, acceptance and durability

| ID | Requirement | Given / When | Required observation |
| --- | --- | --- | --- |
| AC-21 | WH-17 | Evidence covers task R1/snapshot S1/tree T1; change one assessed input to R2/S2/T2 or leave dirty content | Evidence is not automatically applied to the changed subject; dirty content is identified by digest or refused, never hidden behind HEAD |
| AC-22 | WH-18 | W reports success but a consequential assertion is false or critical test fails | V/planner checks against the original contract, exposes the discrepancy and blocks progression; copied report text is not proof |
| AC-35 | WH-11, WH-18 | Assign review to the producer under another role/session, then to a separate non-author in a fresh review task; let the latter attempt a code/test fix | Role relabelling does not satisfy independence; the legitimate reviewer can report/check but cannot edit project code/tests; remediation is a separate task, not a review-side patch |
| AC-36 | WH-18 | V dislikes an unconstrained implementation detail; compare a preference-only objection with a requirement-linked concrete failure scenario | Preference alone cannot block; the scenario identifies exact code, conditions/actions, expected behavior and failure relative to the task/product; confirmed defects, hypotheses, recommendations and specification gaps remain distinguishable; no invented requirement |
| AC-23 | WH-19 | A routine result satisfies the approved task and review; separately leave a blocking defect, alter promised behavior, or change assessed inputs during integration | Delegated planner may accept the conforming exact result without another owner confirmation; tests alone confer no authority; blockers/design changes cannot be waived by the planner; facts remain distinct and changed integration requires applicable verification |
| AC-24 | WH-20 | Replay after lost acknowledgement, restart and the declared retention boundary; reuse a key with changed semantics | One logical effect, original applicable result or explicit expired-key refusal; no duplicate authorization or changed-payload reuse |
| AC-25 | WH-02, WH-20 | Two stale owner commands race; revoke a caller's access and replay its formerly successful command | At most one stale-generation winner; replay cannot leak protected data or regain revoked authority; audit identifies exact actors/subjects |
| AC-26 | WH-21 | Delete/reparent a project or alter document lifetime with accepted history, pinned tasks and evidence | Protected records remain intact or the explicit authorized lifecycle applies; alternate/subtree/batch routes cannot cascade away authority history |
| AC-27 | WH-07, WH-21 | Back up, restore and upgrade a supported deployment; interrupt the documented failure points | Consistent content/authority/evidence and acknowledged writes are recoverable; incompatible or partial state fails visibly |

## Structured owner review

These cases derive from accepted U1, scoped by T1 for the cooperative initial version. Native
framework probes or a scratch HTML page are not passing Writhelm acceptance evidence. The local
contract/findings are in `docs/design/approval-interface.md`. Strong anti-deliberate-bypass presenter
qualification is deferred, not claimed; service authorization/binding and decision completeness remain.

| ID | Requirement | Given / When | Required observation |
| --- | --- | --- | --- |
| AC-43 | WH-08, WH-11 | Present multiple exact decision items, reorder them/change a request or tab, submit structured choices and try free text outside an item-local changes-required choice; separately automate a native form response | Choices/comments retain exact request/packet/item bindings or are refused; no model remapping or partial coupled approval; cancel is unanswered; actual service grants are enforced. T1 relies on cooperative instructions/trusted harness behavior against deliberate hook/API impersonation; native form success alone does not prove a stronger human-only channel |
| AC-44 | WH-08, WH-12, WH-18 | Required decision context is missing/denied/cropped, or a material exception is hidden by a short summary; separately grow supporting context around a complete candidate view | Approval is blocked when known necessary information is unavailable/inadequately represented; primary view preserves the actual choice, exact changes and material qualifications/costs/uncertainties; source coverage is not confused with semantic sufficiency or demonstrated human comprehension. T1 accepts the shown screen as an initial basis and defers visual/empirical refinement, not information completeness |

## Publication, experiments and self-application

| ID | Requirement | Given / When | Required observation |
| --- | --- | --- | --- |
| AC-28 | WH-22 | Private material is locally accessible but absent from an approved public manifest; export/stage is attempted | Publication is refused; reviewed public bytes/provenance match the approved scope; no implicit corpus/session export |
| AC-29 | WH-23 | A test/experiment requests live inference, embeddings or paid operations without approval | No such call is made; model-free validation remains available. Authorized experiments separately record aggregate cost, quality and estimation limits |
| AC-38 | WH-19, WH-23 | A project identifies cheap checks and an expensive suite; perform repeated edits and individual task handoffs between designated expensive-test checkpoints | Cheap checks remain usable; neither edits nor handoffs trigger the expensive suite; it runs only at its designated authorized checkpoint; deferred coverage remains explicit and is never reported as passed |
| AC-30 | WH-08, WH-24 | A Writhelm change is proposed, reviewed, implemented and integrated using the temporary manual protocol | Canonical task/spec/design, decision-sized owner rulings mapped to exact clauses/revisions, applicable evidence and review/acceptance links exist; unrelated semantic edits cannot inherit approval; absence of server enforcement is explicit |

## First working slice gate (M1)

Ratify the relevant contract clauses and a bounded implementation design, not a whole draft bundle.
For one structural document with independent clauses X/Y, two project identities (including access
denial), separately authenticated callers and two pinned consumers, demonstrate K-APP O1-O9 and its
assembled journey. Related scenarios: AC-01/02/03/05/07/08/24/25/26/42, stable-address portions of
AC-04 and pinned-read/ordinary-update portions of AC-06, plus manual-process/publication AC-28/30.
U1 adds AC-43/44 for the actual owner approval surface under T1's cooperative trust boundary.
Synthetic API/DOM exercises do not establish stronger owner isolation or measured comprehension;
those stronger claims are not initial-version gates. Unsupported native clients may leave approval
pending or use a structured view, never a free-text authority fallback.
The owner-directed refinement replaces the earlier whole-document-only gate. At this stage AC-26
may refuse project retirement; split/merge may be explicitly unsupported rather than infer lineage.
Fix exact supported storage/durability assumptions before executing AC-07.

This gate requires neither dispatched workers nor model calls. Pinned consumer manifests do not
qualify executable task snapshots, general clause lineage, packet closure, withdrawal-safe execution,
budget enforcement, second-harness portability, backup/upgrade or live economics. The remaining
portions of AC-04/06 and later scenarios stay outstanding, not falsely satisfied by input fixtures.

### M1 proposed test allocation

This is a test-design proposal, not executable tests, implementation selection or a minimum-suite proof.

| Boundary | Focused obligations | Why this level |
| --- | --- | --- |
| Approval policy and command transitions | Exact subject/digest, owner authority, proposal versus acceptance, stale-generation refusal and command fingerprint mismatch | Exercise decisions/conditions cheaply without booting the whole service; meaningful expected-state assertions are required |
| Actual selected persistence/publication boundary | Concurrent commits/retries, all-or-nothing approval/result state, publication failure, process restart and missing/corrupt content | These properties cannot be established by a double that merely claims persistence or rollback; test the real boundary in isolation |
| HTTP/MCP adapters and authorization contract | Equivalent subjects/results, correct principal propagation, denied-project access and rejection of bypass attempts | Verify wiring/security at the exposed boundary without duplicating every internal case through both complete stacks |
| Small assembled E2E set | Approve a change to X, preserve independent Y's status, read old/new pinned inputs from two clients across restart, and show a later proposal cannot replace either | Validate one real user journey and component assembly, not the entire condition matrix again |

The selected design must turn these obligations into concrete cases, including infeasible/excluded
cases and explicit coverage gaps. Measured cost determines scheduling; an isolated crash test or a
transport test is not automatically classified as cheap. Counts and coverage percentages alone do
not establish oracle quality, and this allocation does not prove generic change-impact inference.

## Current execution status

All AC scenarios: **not executed for Writhelm**. The imported upstream regression suite is useful
baseline evidence only; it does not satisfy these scenarios by similarity of feature names.

## Integrated design walkthrough W1

Status: conceptual consistency check, not an executed test or an approved implementation design.
Use the same synthetic identities/projects as above. This walkthrough composes accepted D1-D12;
it does not approve the surrounding unratified requirements by association.

1. The owner accepts one behavior decision. The planner records its exact scope and corresponding
   document revision/scenarios; editorial assembly cannot expand the ruling (D1/D3/D4).
2. The planner prepares a bounded implementation task and identifies affected areas. Applicable
   norms and explicit additions form the required inputs. Known unresolved norm conflicts block
   this task, not unrelated work; a local/newer norm cannot silently win (D2/D7).
3. Just before dispatch, bind the executable task revision, chosen valid snapshot and packet. A
   withdrawn old norm prevents dispatch; using an older non-withdrawn branch snapshot is not an
   error merely because another accepted head exists (D5/D9).
4. The worker implements within the bound contract, records factual evidence rather than changing
   prescriptions, and checkpoints substantial progress. After compaction/restart, reconcile the
   checkpoint with actual artifacts; neither summary nor old passing tests prove the current result
   (D6/D10). Structural success does not prove semantic completeness (D8).
5. A separate non-author reviewer in a fresh review task checks the original contract and exact
   result. It may run permitted scratch checks but cannot edit project code/tests (D11).
6. A report says the worker's chosen implementation fails under specified concurrent inputs. It
   names the product/task expectation, assessed code, sequence and failure evidence/trace. The
   planner verifies the consequential claim. A stylistic preference without such a violation does
   not become a blocking defect; a genuine missing product decision goes to the owner (D11/D12).
7. A confirmed blocking defect prevents acceptance. A separate remediation task identifies the
   finding and required correction without silently revising the original task's promised behavior.
   A requested behavior change needs a new owner design decision, not a planner waiver (D1/D12).
8. The corrected tree is a new assessed result. Obtain applicable independent review and evidence
   for it; do not reuse the old result's approval or let the reviewer become the fixer (D11).
9. With criteria met and no blocking findings, the delegated planner accepts the exact result and
   permits integration within its scope. Record any changed integration result and applicable
   verification. No routine extra owner confirmation, no acceptance by passing-test implication
   (D12). Publication permissions and separately authorized experiments remain separate gates.

During any active step, ordinary applicable norm updates notify without silent rebinding; explicit
withdrawal blocks affected work at the supported safe boundary and requires updated inputs before
resume (D5). This trace describes required observations, not a claim of full conflict detection,
complete checkpoint capture on sudden failure, or implemented enforcement.
