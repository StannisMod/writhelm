# Accepted knowledge: approval boundary

Contract ID: K-APP. Draft revision: 2 (concrete first-slice obligations).
Status: proposed wording grounded in accepted D1/D4/D5/D6/D18; not an implementation contract or a passing test.
This local path is not approved for publication. Preparation: `docs/tasks/SPEC-001.md` and
`docs/tasks/DESIGN-001.md`. Draft 1 is retained in the latter task's exact input snapshot.

## Purpose and scope

A client must be able to distinguish a proposed change from the knowledge it is authorized to rely
on. Editing, publishing or reading a proposal is not an act of approval. This contract governs that
boundary for one project/document, not storage technology, wire routes or the whole work lifecycle.
The first slice includes stable clause reads and two pinned consumer manifests, not worker dispatch.
It does not require a separate lifecycle/store for each clause: documents remain revision units.

The implementation design must expose an exact subject comprising project, document revision/digest,
stable clause identity and the content/scope covered by the ruling. The representation is still open.
A digest identifies content; it does not prove semantic equivalence, independence or consistency.

## Observable contract

**K-APP-01 — Proposing preserves authority.** Given accepted revision A, creating or editing proposal B
does not change the accepted revision or the authority of A. Reading B must not represent it as
accepted merely because it is newer or its text claims approval. This holds for unchanged, edited
and conflicting proposed content, and before/after an ordinary supported service restart.

**K-APP-02 — Approval has an exact subject.** Approval identifies the authorized principal, exact
revision/digest and semantic change it covers. It applies neither to a later edit nor to unrelated
unapproved prescriptions in the same draft. Changes not covered by the ruling need their own
authorization; factual descriptions do not become prescriptions through a document label.

**K-APP-03 — Approval can take effect.** For an authorized approval of a valid available subject,
with its preconditions satisfied and no conflicting concurrent change, the system can make that
subject accepted and return an attributable result. Rejecting every operation does not satisfy this
contract. A caller lacking approval authority cannot cause that transition.

**K-APP-04 — Reads are revision-bound.** A read identifying accepted revision A and one of its sections
returns the content of that revision/section, not the same-named section of B. Reordering/renaming a
section does not silently redirect the old reference to different content. Approval/supersession
preserves historical content; withdrawal prevents further authorized execution under D5 without
pretending the historical text disappeared.
Two consumers may pin different valid snapshots. An ordinary approval cannot silently replace either
manifest. An absent, denied or corrupt pinned input produces an explicit error, never latest fallback.
An exact read identifies its revision, clause, digest and applicable authority/provenance; a readable
proposal is labelled as such, not presented as an authorized task input. Exact document bytes and
addressed clause content survive UTF-8/line-ending/final-newline differences without normalization.

**K-APP-05 — Clause-local change.** Contract clauses have stable identities. Changing one clause
does not reset approval/check status for another solely because the document revision changed.
The planner checks consistency and identifies any dependent clauses requiring reconsideration;
those consequences are explicit rather than an automatic blanket reset. A changed clause cannot
inherit approval for its former meaning. Historical test records remain about their assessed inputs.
Reordering/renaming must not assign an old identity to a different obligation. Split/merge lineage is
explicit when supported; the first slice may reject that operation rather than guess a correspondence.
Carried status retains its original subject/provenance and limits; it is not a new successful test run.

**K-APP-06 — Publication and restart preserve a whole authority state.** An interrupted or failed
publication cannot expose uncommitted content or partially apply a ruling. Content availability,
approval attribution and accepted-subject metadata must agree whenever acceptance is returned.
Failure before the authority transition leaves the old authority; a crash with an unknown commit
outcome may recover either whole old or whole new state. An acknowledged successful write survives
the supported process restart. Orphan stored content is not approval. Missing/corrupt accepted content
is an explicit integrity failure, not permission to use another revision. This applies to the changed
clause and to the preserved independent clause, not just a document-level status flag.

**K-APP-07 — Retries do not widen or duplicate a ruling.** A completed command replay has one logical
effect and an attributable original result; changed semantic payload under the same command identity
conflicts. Current access is rechecked on replay. Conflicting stale authority transitions cannot both
succeed. This does not require unrelated clause updates to conflict or provide a semantic merge engine.
The supported retention/concurrency protocol must be fixed before implementation.

**K-APP-08 — Alternate routes preserve authority and history.** Identity/grants are established by
the service, not document/request claims or operational privilege alone. Wrong-project or ungranted
reads/writes cannot bypass them. Ordinary document/project mutations, including batch/subtree paths
if exposed, cannot erase accepted history or pinned inputs or create acceptance indirectly. The first
slice may refuse destructive lifecycle operations; it must not silently cascade away those records.

## Deriving checks

These clauses, not implementation output, define expected outcomes. The following finite obligations
are derived checks, not passing tests, a second authority or a required number of methods. Each
executable check must identify the clause/case it actually reaches and assert the indicated state.

| Obligation | Clause | Cases/regions and independent oracle | Narrowest relevant boundary |
| --- | --- | --- | --- |
| O1 | K-APP-01 | B identical, edited, or contradicting A; before/after restart: proposal creation leaves accepted content/authority unchanged | Transition rules; real restart persistence for durability |
| O2 | K-APP-02, K-APP-03 | Authorized exact X change succeeds; changed revision/digest, wrong clause/project, forged actor or missing approval authority cannot approve it; a separate edited Y in the same draft stays unapproved | Focused policy plus auth wiring |
| O3 | K-APP-04 | C1 pins A/X0,Y0; C2 pins B/X1,Y0 after X approval; interleave/repeat reads across restart: exact respective text/identities/provenance; C1 never switches to X1 | Resolver rules plus real content/metadata stores |
| O4 | K-APP-04, K-APP-05 | Reorder/rename clauses, alter X, preserve Y: references retain meaning, Y retains its original approval/check record, X does not inherit old approval; duplicate IDs or explicitly inconsistent lineage refused; split/merge either explicit or refused | Structure/transition rules and stored read wiring |
| O5 | K-APP-04, K-APP-06 | Non-ASCII, CRLF/LF and final-newline variants round trip exactly; missing/denied/corrupt pinned content yields explicit error, no latest/proposal/worktree fallback | Real Git/content adapter and authorization |
| O6 | K-APP-05 | Planner records a dependency requiring Y reconsideration: consequence is visible and scoped; no automatic global reset or claim that structural success proves semantic consistency | Rules for recorded consequences; manual semantic assessment in this slice |
| O7 | K-APP-06 | Inject failure before content is durable, after content but before authority, during authority transaction, and after commit before response; restart at those boundaries: whole old/new state only, acknowledged success retained, no orphan promotion | Real chosen stores; controlled process termination for crash claim |
| O8 | K-APP-07 | Same command after lost reply/restart yields original effect; changed payload conflicts; revoked access cannot replay protected result; two conflicting stale commands have at most one winner | Focused command rules plus real transaction/concurrency boundary |
| O9 | K-APP-08 | Missing grant, other project, broad admin alone, body-claimed owner, and every exposed alternative mutation/deletion path: no forbidden read, authority change or history loss; allowed operation succeeds | Focused auth/route tests and real deletion constraints |

For O7 observe content identity, X/Y authority, approval provenance and command result together. An
error string or an unreachable injection seam is not an observation of recovery. Failures during a
proposed write leave old authority; an unavailable backing object after a committed write is instead
an integrity failure, not proof that an incomplete acceptance can be used.

For O2/O9 vary each approval predicate independently where feasible: valid subject, project grant,
approval authority and current transition precondition. Include all-valid success and individually
invalid cases; do not test only a caller failing every condition at once. For O3 interleave old/new
consumers rather than only reading both after the final update. Full Cartesian combinations are not
required without a justified interaction; omitted regions and design-dependent cases remain visible.

### One assembled journey

Through the supported authenticated client interfaces, accept A with clauses X0/Y0 and pin C1 to A.
Propose B changing X only; verify C1 still receives X0/Y0. Approve exactly X1, preserving Y0's
approval/check provenance; pin C2 to B. Interleaved C1/C2 reads produce X0/Y0 versus X1/Y0, including
after a supported restart. A later unapproved X2 cannot alter either input. Use HTTP and MCP clients
if those are the selected supported interfaces. The detailed fault matrix stays at the store boundary,
not repeated in every E2E. Consumers are recorded task-input fixtures, not claims of executable tasks,
mandatory closure, dispatch, withdrawal-safe harness stopping or budget enforcement.

A check must demonstrate sensitivity to a semantic violation of its own clause; an unrelated crash
does not establish that sensitivity. Pure rules belong in focused fast checks; real storage and
transport behavior need the smallest relevant real integration boundary. An assembled client journey
checks that these promises connect through the supported interface, not the entire internal matrix.
Reviewers do not edit assessed code/tests; any deliberate broken-subject experiment needs a separate
permitted disposable scope, not modification of the frozen review tree.

## Scope limits and open implementation design

The accepted own-core representation direction is R1 in `docs/design/knowledge-model.md`, prepared
by `docs/tasks/DESIGN-002.md`. Its conversational scope does not bulk-approve this contract wording,
all logical draft fields, storage schemas or an implementation. Publication/recovery/retry is the
next design wave in `docs/tasks/DESIGN-003.md`. Owner requirement S1 requires Git SSOT for all canonical
state. The accepted conversational P2 architecture in `docs/design/git-state.md` replaces the
unaccepted split-canonical P1 protocol. Concrete record/authority/verification design continues in
`docs/tasks/DESIGN-004.md`; neither architecture approval accepts every contract sentence nor proves
the behavior implemented.

- Representing/exposing clause-local approval within document revisions, including a draft containing
  more semantic changes than one ruling covers: document lifecycle must neither widen a scoped
  approval nor reset unrelated clause status. D18 sets the behavior; its representation is design work.
- Concrete approval preconditions, concurrent-command/retry semantics and supported durability mode.
- Proposed qualification boundary: one service process, persistent local stores and process crashes;
  no power-loss, multi-process writer, backup/restore or operational-readiness claim. The owner must
  approve the actual boundary/protocol; this contract does not select the earlier G1-G6 proposal.
- Full clause split/merge, general applicability/closure, norm withdrawal execution control and task
  dispatch remain later work. Historical read versus withdrawn execution is a retained obligation,
  not a passed first-slice check. The slice cannot claim these capabilities through fixtures alone.
- Actual test observation seams and cost classification. Missing instrumentation creates a testability
  requirement; it does not weaken the promised behavior or justify a manufactured expected result.

Pure/controlled focused checks are cheap candidates; real-store, process and assembled checks need
measured project classification. Expensive checks run at designated authorized checkpoints only;
deferred obligations remain gate-holding. No check has been executed for this draft.
