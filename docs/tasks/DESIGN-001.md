# DESIGN-001 — First-slice contract and foundation comparison

Task revision: 2. Status: outputs prepared and statically self-checked; no independent review.
Detailed M1 design remains open. Design only.
Executor: primary planning session; no delegation.
Authorization: owner requested the concrete first-slice comparison after the specification waves.
Local drafting only; no behavioral code or publication is authorized here. The owner clarified that
the own-core/selective-reuse direction was already chosen; this is not another foundation ballot.

## Objective

Finish the testable first-slice contract and compare extending the imported fork with building a
Writhelm-owned core using selected upstream mechanisms. Use actual seams and consumer-facing
requirements, including the next bounded-worker slice; do not choose a foundation solely because it
makes the first document demo easy.

## Exact inputs

The following drafting snapshots are retained by SHA-256 outside the public repository. They include
accepted D1-D18 and unratified surrounding proposals; use that distinction, not bulk document approval.

| Input | SHA-256 |
| --- | --- |
| `docs/contracts/accepted-knowledge.md`, draft 1 | `cd94e7aeeadc7c65caa2bfe6ea27e3a34a5050f43f23b4dfdc48f26c64810fbc` |
| `docs/specification.md`, draft 1 at task start | `c41143d254ad9841e78144c15a226d34e4bfed22ae14e679f388c6c7db063141` |
| `docs/acceptance.md`, draft 1 at task start | `afe13353fcec1a83749109b3593eadcc8a954367a43da2102f4c27718c19ccaa` |
| `docs/workflow.md`, draft 1 at task start | `287cea745b8bb4d267378f935311d69dae39c585359aa59b928f89959d534bbb` |
| `docs/tasks/SPEC-001.md`, revision 2 at task start | `5ba4a82e84012d04f3c2b568f25b0bff658abec1714f17360904e39751c8051e` |

Source: imported upstream `d9efd9f1546aa3020b54467366a215d378e8bbb3`, mapping recorded in
`docs/upstream.md`. Repository rules: `AGENTS.md`, `docs/publication.md`; product scope: `README.md`
and `docs/roadmap.md`. No private project source/SOP text is an output or publication input.

## Scope

- Read: the inputs above and relevant imported main/test/build source.
- Write: local drafts `docs/design/foundation.md`, this task, and refinements to the local K-APP
  contract/catalogue, relevant specification milestone wording and existing architecture/publication
  checkpoint documents. Revision 2 also permits correcting README's current foundation description
  to reflect the owner's clarification; no staging/push follows.
- Decide: derived test obligations, source-grounded comparison and recommendation only. Owner
  has confirmed own-core/selective reuse and approves subsequent implementation-sensitive designs.
- Constraints: no behavioral code, deletion of the imported baseline, source import, paid/live
  inference/embeddings, test-suite execution, native worker launches or public push.
- Dependencies: accepted design directions relevant to the first slice. The imported regression
  baseline is verified evidence about upstream, not proof of new Writhelm behavior.

## Acceptance

1. First slice covers one changed contract clause, preservation of independent clauses, exact reads
   by two task/client consumers and failure/restart refusal of unapproved/partial state.
2. Clause-level obligations, examples/regions, observable outcomes and cheap versus real-boundary
   checks are derived from the contract, not copied from implementation output.
3. Consequential reuse/adaptation/extraction claims cite actual files/symbols at the pinned source.
4. Compare M1 and implications for M2, tests and migration/upstream maintenance; no invented hour/cost
   estimates, automatic impact oracle, new merge engine or required universal knowledge formalization.
5. State evidence limits, unresolved contract details and what approval would authorize next.
6. User-facing output concisely states reuse consequences and the remaining bounded-design work,
   not another foundation choice or a request to read/ratify a document bundle. Mark self-review
   and independent verification separately. This replaces revision 1's repeated-choice output.

## Escalation and output

Escalate a genuine requirement conflict or a need to change accepted behavior. Source limitations
are implementation gaps, not reasons to weaken contracts. An unqualified extraction remains a
candidate, not a claim of drop-in reuse. New public paths need scoped publication approval.

Outputs: K-APP draft refinement, derived test obligations, `docs/design/foundation.md`, concise
conversational conclusion, and explicit verification state. No implementation or integration acceptance.

## Owner clarification and continuation

The owner corrected the repeated foundation question: abandoning the maintained fork was already
decided. Record own-core/selective reuse as established direction (architecture F3); do not infer
approval of K-APP draft 2, G1-G6, implementation, publication or source deletion. Keep the comparison
as source-grounded rationale and component-boundary analysis. Continue to bounded M1 design after
closing this comparison, rather than asking for the same foundation decision again.

## Result and verification checkpoint

- Prepared K-APP draft 2: eight clauses, nine derived obligations and one assembled two-consumer
  journey. Aligned the proposed M1 gate with stable clauses and pinned consumers, not whole-document
  approval or executable dispatch. All new wording beyond accepted directions remains proposed.
- Prepared the source-grounded M1/M2 comparison and extraction boundaries. Corrected architecture,
  specification and README to reflect the owner's established own-core/selective-reuse direction.
- Static self-check passed: 24 requirement IDs, 42 distinct AC scenarios, eight clause IDs, nine
  obligations, all five retained input digests, document links, 20 referenced source-path/range
  entries and simple private-identifier/trailing-whitespace checks. These are structural checks,
  not proof of semantic completeness or a complete private-content audit.
- Recomputed worktree Git blob hashes/modes and compared all 1,382 tracked backend
  files with the pinned upstream mapping: unchanged. Nothing staged. No behavioral code, source
  extraction, Writhelm tests, expensive suite, live inference or public push occurred.
- This is author self-check, not fresh-context independent verification. No claim of passing
  Writhelm behavior, approved contract wording, accepted implementation or integration follows.
- Resumption: bounded M1 implementation design for the own core, starting with document revision /
  stable clause / approval-subject / pinned-manifest representation. Then authority provisioning,
  publication/retry/recovery boundaries and concrete test seams/checkpoint scheduling. Present
  consequential design decisions conversationally; do not reopen the foundation choice or implement
  held G1-G6 by default. Publication and behavioral implementation remain separately gated.

Continuation is now `docs/tasks/DESIGN-002.md`, preparing the own-core representation decision R1;
the comparison is not reopened. Its exact input snapshots preserve this task before that link update.
