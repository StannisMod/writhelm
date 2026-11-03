# Knowledge approval and pinned-input transitions

Contract K-WORKFLOW, revision 1. Task: `docs/tasks/CORE-002.md`.
Derived from accepted R1/P2/T1/U1 and K-APP; logical in-memory transitions only, not a running service.
The contract precedes implementation/tests. Source publication and independent verification remain separate.

**WF-01 — Current scoped authority.** Proposal requires PROPOSE, planning operations PLAN, answering
an approval request APPROVE, reads READ, and project-access revocation MANAGE. Grants are explicit
per principal/project; there is no role-label/ADMIN shortcut or identity taken from an answer. The
actor is a separate trusted input to this pure boundary, not authenticated here. Replay/read checks
current authority. Revocation is explicit, cannot remove the project's last existing approval or
management authority, and does not rewrite earlier records. Credential generation/issuance is outside.

**WF-02 — Exact pending decision packet.** A request names immutable before/target references,
unique review-item IDs, decision question/scope, rationale, consequences, alternatives, uncertainties,
declared context/dependencies and information gaps. All included refs resolve exactly in one project.
Every clause's actual revision context must be included, not a similar context from another revision.
An empty context partition contains no prescription and may be omitted without an approval record.
Repeated targets for the same stable subject are invalid. The packet and its complete material bind
the request digest; changing wording, item/order, context or guards cannot reuse the old digest.
Creating a request does not approve anything. A known information gap is not an uncertainty that
can be hidden by a summary; approval remains blocked. No automatic semantic-completeness proof.

**WF-03 — Structured answer and exact replay.** Only approve, reject and changes-required exist.
Only changes-required carries text, with at least one nonblank comment under a valid item ID from
that request. The exact request and packet digests must match. Reject/changes settle the request
without granting approval. A matching answer retry by the original actor returns the same resolution
without another effect, after current-access checks. A different answer/actor cannot rewrite it.
Cancel/dismiss is no answer; no implicit cancellation-to-approval transition exists.

**WF-04 — All-or-nothing scoped approval.** An authorized valid approval applies to every enumerated
target in the coherent request and to no other edit in its document. Target-subject generation guards
and exact previous direct-approval refs are checked: stale conflicting requests cannot both win,
including ABA changes. Generations belong to touched subjects, not whole documents. Old accepted
refs remain valid for deliberate pinned reads/inputs; a newer ruling does not withdraw them.
Referenced contexts/dependencies must already have applicable approval unless explicitly included
in the same initial ruling. Unchanged declared old dependencies do not become invalid just because
their latest direct-approval cursor advanced. Known blockers on selected refs prevent new approval/use.

**WF-05 — Explicit continuity, not hash-granted authority.** A planner assessment names exact refs,
its rationale and any known blockers. It may attach an unchanged target to its original approval
through an explicit source/target link: same project/document/selector, exact equal addressed bytes,
valid original source authority, and unchanged equally framed context for clause links. Source and
target must be included in the assessment. Targets cannot overwrite an existing authority link and
sources must be already approved before the batch; no cycles/chain-based bootstrap approval.
Batch validation is atomic. Same bytes without an assessment are not approval. Changed context or
byte-different editorial equivalence is not inferred; those mappings are unsupported in this unit.
Known blockers remain separate from historical approvals; they also block use through continuity.
Adding exact-reference blockers is supported here; resolving findings, general exceptions and
byte-different editorial mappings remain later explicit transitions, not an implicit unblock.

**WF-06 — Immutable compatible manifests and historical reads.** A manifest names exact clause refs,
their actual contexts, declared dependencies and one planner assessment covering their joint use.
All selected inputs need applicable approval and no known blocker. At most one version per stable
clause can be selected across clauses/dependencies; explicit mixed revisions are allowed with the
same byte-framed context and the assessment. Different contexts for the same document are refused
as unsupported, not asserted semantically contradictory. Freeze original approval/continuity provenance.
New proposals, answers or blockers never rewrite an old manifest. A current authorized historical
manifest read returns its exact original refs/provenance, not a claim of current execution eligibility.
This unit has no dispatched workers, norm-withdrawal stopping or general mandatory-closure extraction.

**WF-07 — Immutable atomic results.** Inputs/records/collections are defensively frozen. Successful
transitions return a new state; rejected operations leave the original unchanged and return typed
problems. Duplicate IDs cannot overwrite records. No caller-controlled array/map can alter a pending
question, structured answer, assessment, authority or manifest. Content reads use K-CONTENT failures.

**WF-08 — Evidence/trust and excluded mechanisms.** An assessment is a planner claim, not proof of
truth, independence or verification of a changed code tree. Continuity retains original approval
provenance; this unit does not create or requalify code-test evidence. T1 trusts compliant callers /
harnesses against deliberate identity/approval-channel impersonation; this does not waive the above
domain checks. No JSON wire codec, command-key journal, durable Git publication, network, real GUI,
credential verifier or native execution is implemented by this contract.

## Finite case obligations

| Region | Expected observation |
| --- | --- |
| Owner success and each absent/wrong-project capability; access removed before replay/read | Exact permitted effect or denied current access, no protected result through stale grants |
| Changed packet material/ID/context/digest; malformed/foreign/missing refs; missing actual context | Pending question cannot silently change subject; typed rejection, no approval |
| Approve/reject/changes/invalid or unknown comment anchor, duplicate resolution/ID | Closed answer laws and one attributable outcome; no partial coherent approval |
| Two pending X requests, unrelated Y update and X ABA | One conflicting winner; independent Y preserved; old valid snapshots still usable |
| Exact unchanged Y/context with planner links; changed bytes/context; wrong ID; cyclic/new-source batch | Explicit original approval retained or mapping refused; no implicit hash transfer |
| Initial and changed mixed-content documents, two pinned consumers, compatible mixed revision manifest | Correct old/new refs and provenance; unapproved neighbour and unassessed/incompatible mix refused |
| Blockers added during pending approval, through a continuity source, and after old manifest creation | New affected use blocked; original historical record/read preserved; no norm-withdrawal/runtime claim |
| Mutated input lists/maps, cast attempts, failed multi-link batch or duplicate record | Existing states/digests/provenance unchanged, no partial writes |

Engineering digest v1 uses K-CONTENT's length-framed UTF-8/count/UUID primitives with explicit workflow
domains, tagged clause/context refs and all ordered packet material. Request digest binds request ID,
project, proposer, packet digest and target-generation guards. This is not the future JSON/wire codec;
the actual Git writer must later commit the whole transition and command receipt together under P2.

## CORE-002 author-check coverage map

`ApprovalWorkflowTest` exercises scoped permissions/current revocation, exact pending and answered
states, digest mismatch, closed comment anchors, unchanged-state failures, original-actor replay,
information gaps versus uncertainty, X/Y concurrency, ABA and deliberate old dependencies.
`ContinuityManifestTest` exercises the two-consumer journey, X-only mixed drafts, compatible mixed
revisions, actual context requirements, atomic/invalid/cyclic continuity, changed contexts, joint
assessment, duplicate/conflicting selections, scoped blockers and historical reads.
`WorkflowRecordsTest` exercises packet field binding, malformed shapes and defensive copies/casts of
packets, policies, answers, assessments and manifest provenance. This is finite case coverage, not
semantic completeness, line coverage or a whole-service acceptance claim.

Final targeted suite: 90 cases, zero failures/errors/skips (53 prior content cases + 37 workflow
cases). Two selected regression methods were also run in a disposable copy restoring the old context
selection and blocker checks: both failed on the expected forbidden-success outcomes, not unrelated
crashes. The assessed source was never mutated for that witness. Final corrected-source tests passed.
No credentials, model calls, network/Git persistence, native GUI or independent review were tested.
