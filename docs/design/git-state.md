# Git-authoritative state and atomic publication

Draft revision: 2. Status: conversational P2 architecture scope accepted within S1 (Git SSOT).
Remaining concrete fields, credential/runtime/durability details and test scheduling stay proposed.
Canonical task: [DESIGN-003 revision 2](../tasks/DESIGN-003.md).
Accepted representation: [R1](knowledge-model.md). Contract obligations: [K-APP](../contracts/accepted-knowledge.md).
P2 is a storage-protocol label, separate from the publication-scope proposal also named P2.
Local-only design; no implementation, behavioral qualification or independent review.

## Accepted requirement and concrete recommendation

**S1:** all canonical Writhelm knowledge, authority and workflow state must be versioned together
under Git SSOT. A canonical Git document store plus independently canonical SQLite approvals/tasks
does not satisfy this structural requirement. The earlier P1 proposal was not accepted.

**Accepted architecture direction:** one private service-data Git repository per service instance, with one publication
ref whose commit identifies the entire canonical state. Documents are text blobs and related state
is typed, versioned metadata in the same tree. SQLite, if later useful, is a disposable projection,
not a transaction/authority store. M1 can perform exact reads directly from Git without SQLite.

This is the service's private data repository, **not** the public Writhelm source repository or a
corpus overlay in coding projects. No private project data may be exported into public source Git.

## GS-01 — The SSOT boundary

The canonical tree contains, as those capabilities are introduced:

- Exact document revisions, structure indexes, stable clause identities and lineage.
- Scoped approvals/withdrawals, continuity, planner assessments, known findings and resolutions.
- Task revisions, dependencies, assignments/claims, execution profiles and progress checkpoints.
- Pinned input manifests, evidence records and canonical evidence material needed to support them,
  review/acceptance/integration records. Code itself stays in its existing source repository; an
  exact source tree/commit reference is an input, not another editable Writhelm-owned code copy.
- Principal/project grants, authority-policy configuration and schema/format versions.
- Durable command identities, fingerprints, outcomes and attributable audit/provenance.

No business fact required to recover these records may exist only in SQLite, an untracked JSON file,
a reflog, an in-memory queue or commit-message prose. Metadata has explicit typed content; commit
messages and timestamps do not grant owner rights, approval or precedence. This need not be an
event-sourcing framework: each published Git tree is a complete state, with normal Git history.

Derived search indexes, caches, temporary files and live process handles need not be versioned:
they may contain **no unique canonical information** and cannot independently advance workflow.
Raw diagnostic logs are not canonical evidence merely by existing; evidence relied on must have
its exact retained material or original immutable source identified in the canonical record.

Proposed credential boundary, to finish in the authority design: grants, public verifiers and
versioned secret references belong in Git; secret values must not be committed in plaintext.
Encrypted storage versus an external credential facility is still open. Such a facility cannot
become an unversioned authority-policy store. Restoring canonical business state is distinct from
restoring external key material needed to operate the service. No undeclared secret-backup guarantee.

## GS-02 — One publication root, one structurally complete version

For M1 use a bare service-data repository and one ref, illustratively `refs/heads/state`. A reader
captures its commit OID H once. Every record/body/index for that read is resolved through H, never
by repeatedly consulting mutable HEAD, another ref or an unversioned worktree.

The tree has project namespaces plus shared authority/configuration and command receipts. Exact
path grammar/metadata encoding remains wire design; conceptual groupings are documents, authority,
tasks, snapshots, evidence and commands. Service-generated paths are not caller filesystem paths.

Create a candidate commit C whose parent is H and whose tree includes the whole proposed next state.
Only an atomic expected-old update `state: H -> C` publishes it. Creating blobs/trees/commits or
temporary candidate refs does not publish a business operation. Git reuses unchanged objects;
"complete tree" does not mean copying every unchanged document's bytes on every write.

This avoids a cross-store transaction and a collection of independently moving document/approval
refs. Per-project repositories could also satisfy SSOT inside each project, but shared grants and
cross-project state would require defining separate transaction domains. One instance repository is
the simpler initial atomicity boundary; it serializes publication across projects and puts all data
within the trusted operator's store. Clients receive scoped API access, never a clone of this store.
Separate project administrators/raw Git access would require a separate deployment/isolation design.

Publishing C records application state, **not approval of all content in it**. A proposal can be
canonical and still unapproved; typed R1 authority records determine what may be relied upon.
Unchanged clause records/status remain unchanged even when the containing state commit changes.

One root supplies version-coherent visibility, not relational validity by itself. Before publication
the service must validate the candidate schema and declared structure: project-scoped references
resolve within that state; document/index/content identities agree; clause IDs/ranges and provenance
lineage are valid; manifests contain their declared context/dependencies; known blockers and exact
approval scope are respected; immutable IDs/records are not overwritten. Grants and write scope are
checked against the prior state, so a command cannot grant itself authority in its candidate tree.
Git is the atomic SSOT mechanism, while these contract-derived validators enforce structural laws.
Neither mechanism proves natural-language consistency, which remains planner/review work under D8.

## GS-03 — Typed command, validation, commit and acknowledgement

For an authenticated mutating command:

1. Resolve current H and authorize against H's canonical grants/policy. Decode the request and its
   exact subject/context/expected tokens. There is no writable SQL or raw Git route bypassing this.
2. Look up `(principal, project, operation, request key)` in H's command receipts. Matching payload
   replays its original logical outcome; changed semantic payload conflicts. Recheck current access
   before exposing an old result; historical grants cannot revive revoked rights.
3. Validate R1 references, scope, declared dependencies, blockers and structural invariants against
   H. Compute the command's exact write set and resulting records. No undeclared approval transfer,
   latest-input substitution or automatic semantic merge. A coherent multi-clause effect is one set.
4. Build C with the complete effect **and its immutable command receipt/outcome/provenance** in one
   tree. This applies equally to proposals, approvals, continuity/assessment changes and manifests.
   The receipt fingerprint covers all semantic fields, including exact bytes/structure and guards.
5. Publish by expected-old ref update H -> C, and acknowledge only after publication is confirmed
   under the qualified durability profile. A lost response means outcome unknown; check the published
   history/receipt rather than assume rollback or inspect an unpublished candidate for success.

There is no independent durable SQL reservation/journal. Before publication, in-flight coordination
is disposable; after publication, the receipt and effect are inseparable. Two attempts may create
different candidate objects/IDs, but at most one effect for a request identity can enter published
history. Unpublished IDs/results are not acknowledged canonical identities.

Syntactically valid authorized commands that end in a terminal domain/precondition rejection can
publish a rejection receipt without a business-state change. Malformed or unauthorized traffic need
not allocate a canonical command identity. Retain published receipts without automatic expiry in M1;
an old key must not become a new authorization after a TTL. Exact encoding/limits precede code.

## GS-04 — Concurrency without resetting independent clauses

Start with one service process and a serialized in-process publisher. Expected-old ref updates are
still required to fence stale candidates and publication subprocesses whose outcome became unknown.
Readers can concurrently read immutable H snapshots. This is not a qualified multi-writer service.

If publication loses the H comparison, inspect the new published head first. If the matching receipt
exists, return its outcome. Otherwise re-evaluate the command's original subjects/expected tokens,
current grants and declared guards against the new head. A changed relevant guard conflicts; an
unrelated Y update need not invalidate an X command. Rebuild the candidate tree on the new head
only if the same typed operation is still valid. Do not apply an old full tree over new state.

Rebuilding a storage transaction does not rewrite the proposed document, choose a new meaning or
rebase an approval onto an edited clause. It preserves the exact semantic command and current
unrelated records. Business-level branch conflicts still use Git/SOP semantic resolution and owner
approval where required; no clause-merging engine or semantic change-impact oracle is introduced.

## GS-05 — Immutable identities without a self-hash cycle

Domain revision, approval, manifest and command IDs are independent stable IDs with exact content
digests. The Git state commit OID is a transaction/read-snapshot identity, not a replacement for
document revision or clause identity. Domain parent references remain explicit even if service
publication history is serialized; two document proposals may share an older domain parent.

Do not put C's own OID inside a receipt stored in C: that would require a self-referential hash.
The receipt stores the logical outcome/IDs, request fingerprint and prior validation basis. The
response may add the publishing commit as an envelope field after C exists. Its original publication
commit can be recovered from retained append-only history where the receipt was introduced; an
optional index may cache that mapping, never own it. Replays preserve the logical outcome while
transport/request envelope fields may differ.

Records and their referenced original bytes/indexes stay reachable from published state/history.
Ordinary mutations cannot rewrite/remove history or repurpose old IDs. A future withdrawal changes
current authorization through a new commit; it does not erase the historical text/decision. Reading
an old task manifest uses its exact inputs but current access/withdrawal policy, not old credentials.

## GS-06 — Optional SQLite is a projection, not another authority

M1's exact-reference operations do not require SQLite. Introduce an index only for a justified query
need; do not build a general query/cache subsystem to reproduce a handful of pinned reads.

If added, its generation identifies `(store identity, source commit H, projection schema version)`.
Build/replace a complete generation atomically on the projection side. A query requiring H may use
only a compatible H generation, otherwise read Git, wait/rebuild, or explicitly report unavailable.
Never label stale/incomplete results as current. Critical identity/authority/command decisions resolve
canonical records in Git; index hits are not independent approvals or dispatch authority.
Projection correctness/completeness still needs its own contract checks; a generation label alone
does not prove a query correct. No index miss may stand in for a canonical lookup where the result
would authorize a mutation or assert the absence of a protected record.

Deleting the entire index in a designated disposable test must preserve all business records,
approvals, command outcomes and pinned inputs after rebuilding from Git. A projection failure after
H -> C publication cannot roll back C or turn the completed command into an uncommitted one. Rebuild
is not reverse synchronization and must never write "recovered" index rows back as canonical facts.

## GS-07 — Process crashes and recovery

| Crash boundary | Authoritative observation |
| --- | --- |
| During candidate object creation, before ref publication | H remains active; no effect/receipt is published. Orphan objects are not success. |
| During the single ref update | Read the ref and published receipt: whole H or whole C, never an app-level half approval/half manifest. Missing/corrupt objects are an explicit integrity fault. |
| After C publication but before acknowledgement | C contains both effect and receipt. A matching authorized retry returns that logical result. |
| After acknowledgement, before/during optional index update | C remains authoritative; index can be discarded/rebuilt. No acknowledged business state depends on SQL survival. |

Only commits on the published state line establish outcomes; finding a receipt in an orphan
candidate or arbitrary ref is insufficient. Root publication is append-only/fast-forward under the
service protocol, with no force reset as rollback. Startup validates the store identity, root and
supported schema; it never reconstructs an empty store when canonical data is missing.

Use controlled Git subprocesses with argument arrays, raw byte-preserving object writes, no inherited
arbitrary hooks/filters, and no client-chosen paths/ref names. A surviving publication child can only
attempt its expected-old transition; a newer published state fences it. Stale lock artifacts yield
visible blocking/recovery work, not timeout-based blind lock deletion. Exact supervision/runbook and
Git object/ref synchronization settings require implementation qualification.

Initial proposed qualification: one process on persistent local storage, ordinary process crashes;
no claimed host/power-loss durability, HA, remote filesystem support or operational backup/restore
readiness. One Git root gives a complete logical state/version, not automatic protection from storage
failure, lost keys, operator rewriting or Git corruption. Git integrity/authenticated service checks
and an authorized backup/restore qualification remain necessary. Git alone proves no semantic truth.

Future harness/model/filesystem side effects are not made transactional by a Git commit. M2 must
version dispatch intent/assignment/checkpoints and reconcile actual external execution; this P2 does
not claim exactly-once native dispatch or a qualified execution sandbox.

## Finite derived verification obligations

| ID / K-APP | Condition | Required observation / smallest relevant boundary |
| --- | --- | --- |
| GS-T01 / O1,O2 | Proposal or X-only approval in a revision also editing Y | Canonical proposal is not blanket approval; exact authority scope remains X. Fast rules plus real state tree. |
| GS-T02 / O2,O4,O6 | Independent Y or context/dependency change | Retain Y's original status/provenance; known affected use is blocked, not silently transferred. Fast rules; semantic assessment separately reviewed. |
| GS-T03 / O3,O4,O5 | C1/S1 and C2/S2 interleaved while new commits publish | Every read uses one captured H and exact manifest refs; old/new inputs remain distinct; current grants checked. Resolver and real Git read boundary. |
| GS-T04 / O7 | Terminate before/during/after root update and before response | Published state has whole effect+receipt or neither; acknowledged results survive supported restart. Real Git/process qualification. |
| GS-T05 / O7,O8 | Concurrent same-key commands, lost response, orphan candidate receipt | One published effect; replay only from published history. Real expected-old/concurrency boundary plus focused rules. |
| GS-T06 / O8,O9 | Same key changed payload, revoked current grant, stale targeted guard | Conflict/denial without new effect or protected replay; unrelated updates are not clobbered. Policy and real publication check. |
| GS-T07 / O5,O7,O9 | Missing/corrupt root/body/index/receipt/schema; unsafe subprocess/lock condition | Explicit integrity/startup/recovery failure, no newest-content/empty-store fallback or blind unlock. Real store/startup/subprocess boundary. |
| GS-T08 / O3,O7,O8 | Index absent, stale, corrupt or lost after acknowledged commit | Same canonical records/outcomes recover from Git; no SQL write advances authority. Direct-Git first slice; projection tests only when an index exists. |
| GS-T09 / O4,O6 | Implementation tree changes, or global state commit changes only for another clause | Original evidence stays about assessed inputs; no blanket reset or new test applicability from storage commit movement. Fast binding checks. |

These are design obligations, not executed tests or an exhaustive-path claim. Expected outcomes come
from K-APP, R1 and S1, not upstream behavior. Focused rules should carry most detection; one real
two-consumer journey connects the mechanisms. Git/process checks need real boundaries. Cost and
expensive-checkpoint scheduling are separate and remain to be fixed before implementation tasks.

## Rationale, evidence and approval boundary

Git's [commit-tree manual](https://git-scm.com/docs/git-commit-tree/2.43.0) describes a commit over a
complete tree and immutable object contents. Its [update-ref manual](https://git-scm.com/docs/git-update-ref/2.43.0)
describes expected-old checks and individual-ref atomic updates, while warning that multiple refs
do not provide one concurrent-reader snapshot. P2 therefore uses **one** root/ref and explicit H
reads rather than infer a transaction over several refs. Those primary documents were read; no
Git crash experiment, performance measurement, implementation or independent review occurred.

S1 is already an owner requirement, not a new preference question. The owner acknowledged the
conversational P2 proposal: one private instance repository/root, a complete state commit including
the operation/result, structural validation and atomic expected-old publication, exact-snapshot reads
and no required SQLite in M1 (future index derived only). This maps to GS-01 through GS-06 and
retains R1's clause-local approval semantics. Its source draft was preserved at SHA-256
`0ef89467b2b32ec9538ab4b10f134d55fe9dbe269625bfbcc352a6afced2aa17`; that identifies the drafting
source, not blanket approval of every field, credential assumption, fault case or durability claim.
This trades single-root
write serialization/history growth for simpler structural atomicity. No delivery/throughput claim.

With that P2 scope recorded, fix credential/secret handling, canonical record/command encoding,
limits, store adapter/runtime and qualified recovery/test checkpoints. This proposal does not approve
those details, external secret backup, raw project Git sharing, implementation, paid calls or publication.
