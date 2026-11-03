# Developing Writhelm with its own work contracts

Revision: 1. Status: proposed procedure. The owner has directed specification/scenarios first and
self-application of the intended process; the detailed procedure below awaits ratification.

## Accepted decision D1 — Decision-sized approval waves

The owner accepted this rule: the unit of agreement is one coherent decision, not a whole document
or every individual clause. Present its grounded context, viable alternatives, consequences and a
recommendation; obtain the owner's ruling, then record the affected requirements/scenarios and exact
resulting revisions. Documents accumulate the decisions; they are not a bulk review assignment.

An approval covers only the stated decision and its disclosed consequences. Editorial assembly
that preserves the meaning does not require another decision. A semantic change or newly discovered
consequence must return for approval. Record the decision-to-clause/revision mapping so later work
can recover what was actually accepted. D1 does not approve the rest of these drafts.

## Temporary canonical records

Until a working service can own them, this repository's explicitly approved documents are the
canonical preparation artifacts: specification, scenarios, design decisions and one task per unit
of work. Chat is where the owner makes decisions; record the exact subject/revision and outcome in
the canonical record, without exporting transcripts. No extra metarepository or copied private corpus.

This manual protocol is not server enforcement. Do not claim automatic authority, dispatch, budget
or review gates exist. Move records to the service later through an explicit verified migration,
not an untracked second source of truth. Private run logs/evidence remain outside the public tree;
only separately approved public evidence summaries may be published.

## Work sequence

1. **Task:** identify objective, exact inputs, scope, constraints, dependencies, acceptance,
   escalation and expected output. Give the task a stable ID and revision before execution.
2. **Specification/scenarios:** state observable behavior and failure cases without choosing the
   implementation by implication. Map each requirement to at least one scenario; surface open choices.
3. **Design pass:** compare viable realizations, reuse and maintenance costs against those scenarios.
   Record consequential tradeoffs and the supported enforcement/durability boundary.
4. **Owner ratification:** present one coherent decision under D1 for explicit approval. Identify approved bytes
   by commit or retained content digest; do not grant approval to future edits. Requirements approval,
   design approval, publication permission and authorization for paid experiments are distinct.
5. **Implementation task:** only after its requirements/design/dependencies are accepted, create a
   separate bounded versioned task for each worker. Give it canonical references, not parent chat.
   Delegate only when delegation is explicitly authorized and the selected harness can support it.
6. **Evidence:** bind changes and checks to the original task/revision, knowledge snapshot and actual
   tree. Keep commands/outcomes/artifact references and unresolved limits. Do not call reports facts.
7. **Verification:** independently reproduce consequential checks and inspect critical paths by risk.
   A self-check is useful but must be labelled as such, not independent review. If no authorized
   independent review has occurred, leave that gate outstanding or request a scoped owner exception.
   Under accepted D11, use a separate review task/fresh context and a non-author reviewer; do not
   permit project code/test edits in that task. Report findings for separate remediation. Objections
   to unconstrained implementation choices need a concrete, code-specific failure scenario tied to
   the task/product; preference alone cannot become a blocking requirement.
   D13 requires technical enforcement before claiming a qualified read-only review mode. Until that
   mode exists in this bootstrap workflow, label adherence as manual discipline, not prevention.
8. **Acceptance/integration:** under accepted D12, the planner may accept routine conforming results
   and permit integration within the approved task/design scope, after checking consequential review
   claims and evidence applicability and confirming that no blocking findings remain. Record the exact
   result, accepting authority, integration tree and required re-verification. Product/design changes,
   exceptions and agreed owner checkpoints still go to the owner. Push permission is not a blanket
   grant of product/design approval; delegated acceptance cannot amend requirements or waive norms.

## Revisions and state

During drafting, identify the draft revision and open questions. Once a revision is ratified or
dispatched, preserve the exact approved/dispatched content by a retained digest or immutable commit.
Revise scope/spec/design explicitly; keep old approval and evidence historical, never transfer them
automatically. Resume after compaction by reading the task and exact references, not guessing from
a summary. Stop/escalate when references are missing, authority conflicts, or scope needs changing.

Track separate facts rather than one overloaded status:

- Knowledge/design: proposed, accepted, superseded; authorized principal and exact revision.
- Work: drafted, dispatched, implemented, verified, accepted, integrated, with applicable evidence.
- Publication: local-only, approved scope, reviewed/staged, published.

Unavailable later capabilities do not block drafting. They do block claims that those capabilities
are already enforced. Keep the smallest records needed to restore authority and open obligations,
not a transcript or an exhaustive diary of every tool call.

## Initial application

For branch/worktree changes, use Git integration plus semantic merge resolution rather than assume
a new clause-merging engine is needed. Worktrees isolate changes; textual conflicts surface at
integration, and a clean merge does not establish semantic compatibility. The integrator preserves
the governing accepted contracts, checks surrounding/consumer behavior and verifies the actual
resulting tree. A genuinely new meaning requires its authorized design decision; existing meaning
does not need to be asked again simply because a textual conflict occurred. Recompute derived
artifacts rather than treating them as independently authored decisions.

This is the owner's direction to reuse the established approach, not wholesale import of another
project's SOP, private examples, repository topology, launch counts or commit-authority rules.
Whether the running service needs additional concurrent-command handling is a separate implementation
question; this workflow does not claim that worktrees supply server transaction guarantees.

[SPEC-001](tasks/SPEC-001.md) prepares [specification revision 1](specification.md) and
[acceptance catalogue revision 1](acceptance.md). Its scope excludes behavioral implementation and
the fork-versus-new-core decision. Those become a subsequent design task after owner ratification.
