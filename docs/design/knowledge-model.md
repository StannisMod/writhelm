# Knowledge model — document revisions, scoped authority and pinned inputs

Draft revision: 2. Status: R1's conversational representation scope accepted; logical implementation
details beyond that scope remain proposed. No implementation or behavioral verification.
Storage clarification S1: all canonical records below are Git-versioned. Any SQLite view is derived,
not an independent canonical store. Concrete publication protocol P2 is proposed in `git-state.md`.
Canonical task: [DESIGN-002](../tasks/DESIGN-002.md).
Governing contract: [K-APP draft 2](../contracts/accepted-knowledge.md).
Direction: own core/selective reuse (architecture F3), not a maintained fork.
Local-only drafting. No implementation, independent verification or publication is claimed.

## R1 — Separate immutable content, authority and input selection

**Accepted representation direction:** keep whole engineering documents as immutable revision units, but record owner
approval against an explicit set of structural clause references. A task-input manifest selects
exact references with their authority provenance. Neither a Git commit nor a document/manifest
container grants blanket acceptance. Stable IDs identify obligations, not independently maintained
clause documents, and a decision record is not a canonical knowledge unit.

The consequence is deliberate: a revision may contain accepted, carried unchanged and proposed
clauses together. The interface must show that distinction; it cannot give the revision a single
unqualified `accepted=true` that downstream readers interpret as approval of every statement.

### Alternatives

| Model | Assessment |
| --- | --- |
| Accept or invalidate the whole document at each revision | Simpler flag, but loses D18 locality and D1 exact scope. Not viable as the authority model. |
| Make every clause a separately edited/versioned knowledge document | Easy independent pointers, but changes the agreed document-maintenance model and encourages unwanted atomization. Not recommended. |
| Whole document revisions + scoped approval links + explicit manifests | Preserves document editing/history and clause-local status; requires explicit authority/context metadata and prevents a simplistic whole-revision accepted flag. Recommended. |

## Proposed logical records

These are concrete logical fields/relationships, not approved SQL tables, JSON schemas or routes.
All IDs are service-scoped to the project; titles, ordinals and body-supplied actor names are not IDs.

| Record | Identity/content | Purpose and invariant |
| --- | --- | --- |
| Document | Project ID + document ID | Stable maintenance unit across titles, layouts and revisions. No ordinary destructive deletion in M1. |
| Document revision | Document + immutable revision ID; parent reference(s); exact UTF-8 body digest; internal Git content identity; immutable structure-index digest | One full document and its exact structural interpretation. Reinterpreting the same body with another index creates a new revision, not changed old references. |
| Content reference | Project/document/revision + selector (`clause ID` or `document context`) + exact addressed-content digest | A clause occurrence or the ordered context ranges inside a complete immutable document, not a separately editable object. Changing content does not allocate a new stable clause ID for the same continuing obligation. "Clause ref" below is the clause-selector form. |
| Approval | Immutable decision ID; authorized actor; exact prior/target references and disclosed semantic scope; bound context; outcome/provenance | Approves only the enumerated change; creation has no prior accepted subject. It can cover several clauses as one coherent ruling. Caller authority is checked by the service, never by document text. |
| Continuity link | Exact old/new references; original approval/check references; consistency assessment ID; actor and basis | Explains why an unchanged clause occurrence retains its original status in a later document revision. It is not a new owner ruling or a new passed test. |
| Consistency assessment | Planner provenance; exact compared revisions/refs; changes considered; affected/dependent clauses; open findings and resolutions | Records the planner's semantic work. Known relevant conflicts block use; a clean structural check or this record alone does not prove semantic completeness. |
| Consumer manifest | Immutable ID/digest; consumer/task-input identity; explicit clause/context references and authority/continuity links | Pins the selected inputs. Creation is neither dispatch nor ratification; a new manifest cannot rewrite the old one. |
| Check/evidence link | Original assessed content/code-tree/task identities, result and limits | Remains about those inputs. Retaining Y's record never requalifies an old implementation run for a changed tree. |

### Exact addressing and surrounding context

An immutable structure index maps stable clause IDs to exact UTF-8 byte ranges of the full document,
including their headings. Ranges must be in bounds, non-overlapping and on valid UTF-8 boundaries;
IDs must be unique. Exact section reads return those bytes, their digest and the containing revision.
The index is bound into revision identity: changing a range cannot reinterpret an approved subject.
The authoring marker syntax/parser and canonical metadata serialization are subsequent wire/storage
details; the model does not infer identity from matching headings or offsets across revisions.
The source and target of a continuity link must be available immutable refs, retain the same stable
identity and form an acyclic provenance path to a real approval/check record. A missing link, cycle,
different project/document or reused retired ID cannot substitute for an approval. Detecting whether
an author dishonestly reused a live ID for a different meaning still needs semantic review.

Text outside indexed clauses is not discarded. The index also identifies its ordered context ranges
and digest, so together clause/context ranges account for the full body. Context includes introductions,
scope statements and other surrounding text not assigned to a clause. No authority arises merely
because text was labelled context, commentary or fact. Context prescriptions need their own scoped
approval; factual claims need evidence. A clause read includes the required context references, not
an undocumented assertion that extracting its body preserves every dependency.

For M1 each selected document's context is a conservative declared input; additional cross-clause
dependencies are explicit planner inputs. A changed context cannot inherit applicable authority just
because Y's body is identical. The planner determines which meanings/dependencies it changes and
records that assessment; the service checks the recorded identities, permissions and known blockers.
This is not a universal natural-language applicability extractor or mandatory closure implementation.

## Transitions and authority resolution

1. **Propose:** store a new immutable full revision and structure index. Verify structural integrity.
   Neither proposal success nor an approval-looking sentence changes any accepted subject.
2. **Assess:** the planner identifies changed clauses/context, dependencies and the coherent semantic
   ruling needed. Unchanged Y keeps its existing records. Record consequential findings explicitly;
   do not copy all status into a new document-wide flag or erase it pending a blanket reapproval.
3. **Approve:** an authorized owner action names exact changed references, prior subject/context
   and disclosed scope. Validate against those identities and the current authorization/concurrency
   preconditions. Apply approval only to that set. Store a ruling for X without approving Y's edits
   simply because they occupy the same revision. All-or-nothing persistence is required for this set.
4. **Retain independent status:** for unchanged Y in B, link its original approved occurrence in A
   to the exact occurrence in B, with a planner consistency assessment. The service can check same
   identity/bytes and unchanged declared context/dependencies; that does not prove semantic independence.
   A new document revision alone is not grounds to reset Y. A known dependent conflict is recorded
   separately from erasing Y's historical approval/check record and blocks the affected use.
5. **Pin:** create an immutable input manifest with selected clause refs and required context,
   approval/continuity provenance and relevant assessments. Refuse selected proposed prescriptions,
   missing/denied refs and known unresolved conflicts. Do not claim general completeness or an
   executable task: M2 will add applicability/closure, pre-dispatch and execution qualification.
6. **Read:** resolve only the requested immutable manifest/refs; check current caller access. Return
   exact content with provenance and explicit errors. New proposals/approvals cannot replace old
   refs. Historical reads remain distinct from authorization to execute a withdrawn rule.

A manifest selects at most one content for each document/clause identity. Conflicting duplicate
selections are refused, not resolved by order or recency. Required context and declared dependency
refs are explicit; mixed source revisions do not excuse their omission. Cross-project refs are not
supported by this M1 model. Continuity/assessment records may enumerate a set of unchanged clauses
in one operation: this is not a requirement for a separate owner interaction or expensive review
per unchanged clause. Each resulting link must still identify its exact scope and original record.

An old manifest may retain a valid superseded norm under D5; "not newest" is not a blocker by itself.
Withdrawal is different: it preserves historical reads but disallows affected execution. No M1
manifest can claim qualified withdrawal-safe dispatch/resumption; that remains a later harness gate.

### Editorial changes are not automatic semantic approvals

Reordering preserves clause IDs; moved byte offsets are not new identities. Renaming a heading can
change its meaning, so equal body text without the heading is not a continuity proof. Byte-different
editorial assembly can retain an original ruling only with an explicit planner mapping of old/new
exact subjects, context and meaning-preserving rationale under D1. If meaning changes or is uncertain,
do not label the mapping editorial to avoid owner approval: escalate the affected new meaning.
This records responsibility; the service does not mechanically prove editorial equivalence.
M1 rejects clause splits/merges without a supported lineage representation, not by guessing matches.

## Worked examples

Use synthetic document D in project P and clauses X/Y; X0 and X1 denote contents, not independent
clause files. Source revision digests, clause digests and authority links are always in the real refs.

### E1 — Independent change and two consumers

| State | Authority/input observation |
| --- | --- |
| Revision A contains X0/Y0, with exact owner approvals | C1 manifest S1 pins X@A and Y@A plus their context/provenance. |
| Propose B containing X1/Y0 | C1 still reads X0/Y0. X1 is proposed; Y's original approval/check record remains. |
| Owner approves the exact change X0 -> X1 | Approval applies to X@B only. Planner records that Y remains independent; continuity links Y@B to Y@A's original records. |
| Create C2 manifest S2 with X@B and Y@B | C2 reads X1/Y0 with the new X approval and original Y provenance. C1 still reads X0/Y0. |
| Propose C with X2; restart; interleave both readers | S1/S2 references do not change; X2 has no authority merely by being newer. Restart must preserve exactly these distinctions. |

### E2 — Two edits in one draft, only X approved

Revision B contains X1/Y1. The owner approves only X1. Y1 remains proposed, while the old Y0
approval/check records remain about Y@A. Reading the full B returns mixed statuses, never blanket
acceptance. A manifest selecting X@B and Y@B as accepted inputs is refused because Y1 is unapproved.

If a consumer needs X1/Y0 now, the planner may explicitly select X@B and Y@A, with their exact
context and a compatibility assessment. This is a list of original references, not a fabricated
"accepted document B" or automatic merge. Conflicting contexts or known dependencies block it.
Alternatively prepare a clean full document via normal Git/editorial work and map its resulting
exact subjects explicitly; a genuinely new meaning needs owner approval. No custom clause-merger.

Allowing explicit mixed-revision references is a disclosed R1 consequence, not permission for retrieval
to choose convenient fragments. All source revisions remain visible, context is not dropped and
the planner must assess compatibility. The first happy path E1 does not require mixed source revisions.

### E3 — Identical bytes, different applicability

Y's body is unchanged, but a changed introduction narrows its scope or X changes a declared dependency.
Keep Y's old approval/check records; do not infer applicability in B from its content digest. Record
the affected relationship and block affected new use until resolved. Old valid snapshots are not
silently rebound or stopped by an ordinary update. An unsafe old norm needs explicit withdrawal,
not a retroactive rewrite of its historical approval.

### E4 — Retained contract check, changed implementation

Y and its original check record are unchanged, but code tree T1 becomes T2. The original passing
result remains evidence for T1, not T2. Y's contract approval is not reset; required verification for
T2 is outstanding and follows the planned cheap/expensive checkpoints. This model provides no
generic "unaffected scope" exemption or reason to rerun an expensive suite on every edit.

## Mechanisms versus judgments

Mechanically checkable: project grants; exact revision/index/content identity; unique IDs and valid
ranges; selected subject membership in a ruling; provenance/continuity links; immutable manifests;
declared dependencies and recorded blockers; no missing-content fallback; atomic record visibility.

Planner/review work: whether IDs still denote the same continuing obligation, context is complete,
a change is editorial, two approved fragments are semantically compatible and the dependency list
misses something. The service enforces explicit structural facts and known findings, not a perfect
semantic oracle. Producer-planner attestations are claims subject to independent review.

## Derived checks and their limits

| Design case | K-APP obligations | Expected observation and test boundary |
| --- | --- | --- |
| KM-01 exact subjects | O1/O2 | Change revision, index, clause ID, digest or project individually: no unintended approval; valid exact X succeeds. Fast rules plus authenticated-handler wiring. |
| KM-02 stable references | O3/O4 | E1 interleaved consumers retain refs/status; moving a clause changes offsets without redirecting old reads. Fast resolution/index checks; real stores for persisted identities. |
| KM-03 mixed authority | O2/O9 | E2 approves X only; selected Y1 is rejected while Y0 history remains; no document/manifest-wide acceptance shortcut. Fast policy and adapter outcome checks. |
| KM-04 context/dependencies | O4/O6 | E3 keeps historical Y status but refuses the known affected use; forged/broken/cyclic continuity cannot grant authority; conflicting duplicate inputs are refused. Fast structural/transition checks; semantic quality remains reviewed. |
| KM-05 editorial scope | O2/O4/O6 | Heading/ID/byte changes do not auto-prove equivalence; explicit permitted mapping retains original ruling provenance, semantic change requires approval. Controlled policy checks do not prove natural-language equivalence. |
| KM-06 evidence distinction | O4/O6 | E4 never requalifies T1 evidence for T2; no automatic blanket clause reset. Focused evidence-binding checks. |
| KM-07 storage integrity | O5/O7 | Wrong/missing body or index, partial approval/continuity/manifest records, interrupted write and restart yield explicit error or whole prior/new state, never usable partial authority. Real selected stores/process boundary. |
| KM-08 command/access | O8/O9 | Concurrent stale updates, lost replies/replay and wrong-project access preserve scope and history. Transaction/auth tests after those protocols are designed. |

Labels KM-01 through KM-08 name model cases, not product milestones or executed test methods. Expected
results derive from K-APP and R1, not outputs from imported code. A representative journey exercises
E1; internal cases stay narrow. Cost labels/checkpoint scheduling await actual runnable test seams.

## Failure/restart boundary requirements, not a storage selection

Git commit existence alone does not grant approval; typed authority records establish it. All canonical
metadata/content is Git-versioned under S1. Published metadata must not expose an
approval or usable manifest pointing to unverified/unavailable content or an inconsistent index.
Approval of a coherent multi-clause subject, continuity links required for that result and its
attributable command outcome must become visible consistently. An acknowledged manifest cannot
lose its exact refs on a supported restart. Corrupt/missing content fails visibly without latest fallback.

The persistence design must decide the Git publication boundary and retry identity; a database may
be only a derived projection, not an independently canonical journal/authority store. This model
does not select the held fork's migration chain, retry cache or G1-G6 protocol. See proposed P2.

## Accepted R1 scope and continuation

The owner accepted the conversational proposal: whole-document immutable Git revisions and stable
clause identities; approval of exact selected changes rather than a blanket document flag; retention
of independent clause approval/check records without requalifying changed implementation evidence;
immutable context-bound task inputs; explicit planner-assessed mixed-revision selection. This maps
to R1, E1-E4 and the structural/semantic distinction above. The drafting source was preserved at
SHA-256 `1f395a22c3ce886333eaede5ed14a0e091b484eb50fc5ad46a9c8862b5bf47af`.
That digest identifies the source used for mapping, not bulk approval of every draft field or test case.
The ruling does not bulk-ratify K-APP/specification or select a DB schema, metadata grammar,
credential provisioning, recovery/durability mode, command retention, routes, runtime or test budget.

With R1 accepted: fix persistence/publication/retry and the supported restart boundary; then provisioning /
wire/authoring representation and executable test seams/checkpoint plan. Refine the exact contract
wording before implementation tasks. No source changes, model calls, source deletion or publication
are authorized by writing this design. The owner reviews the substantive decision conversationally.
