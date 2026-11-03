# Publication and recovery — own-core first slice

Draft revision: 1, historical P1. Status: not accepted; incompatible with owner requirement S1.
Canonical task: [DESIGN-003](../tasks/DESIGN-003.md).
Accepted representation: [R1](knowledge-model.md). Governing obligations: [K-APP](../contracts/accepted-knowledge.md).
Local drafting only. No behavioral implementation, storage qualification or independent review.

**Do not implement this proposal.** It split canonical Git content from independently canonical
SQLite authority/workflow state. The owner requires structurally versioned Git SSOT for all canonical
state. This original draft is retained as a superseded proposal; the active replacement is
[Git-authoritative P2](git-state.md). Its previous self-check was structural, not owner acceptance.

## P1 — Content first, authority second, durable command identity

Recommend **service-owned Git for immutable document/index content and SQLite for authority/workflow
metadata**, in one service process on persistent local storage. Publish and verify content before a
database commit can expose its revision. Commit the exact scoped authority change and its recorded
command result together. Replays use that durable result; no timeout turns stored content into approval.

This is an own-core protocol, not adoption of the fork's document model, migration chain, global
accepted-document pointer or default/detached idempotency cache. It preserves accepted R1 locality.

| Alternative | Assessment |
| --- | --- |
| All content and authority in Git, one journal/ref transaction model | Viable direction, but requires designing authority/query/index/retry state around Git; ref updates do not supply a SQLite-style multi-record reader snapshot. Not selected by this proposal. |
| Store editable canonical bodies in both Git and SQLite | Adds competing content owners/synchronization; avoid. A verified immutable cache could exist later, but is not a second editable corpus. |
| Git immutable content + SQLite authority/command transactions | Uses established store mechanisms and keeps query/authority updates transactional; requires explicit cross-store ordering, retention and consistent future backup. Recommended. |

The two stores do **not** share a transaction. Their safe ordering may leave unused Git content,
but must not expose authority pointing to an incompletely published revision.

## P1-01 — Supported deployment and durability boundary

- One service process owns its store root; an exclusive lifetime OS lock prevents a second service
  instance using that root. Several authenticated clients/concurrent requests are supported. This is
  not a distributed lock, HA service, multi-process writer or shared-network-filesystem deployment.
- Persistent local filesystems with the required Git/SQLite locking semantics; trusted operator.
  Workers/clients have no direct store or owner-credential access. Direct privileged modification,
  malicious storage, disk corruption and arbitrary operator deletion are outside crash durability;
  detected integrity damage still fails visibly, not via fallback.
- Promise to qualify ordinary process termination, including abrupt termination around commits and
  Git subprocess activity. No acknowledged successful write may disappear after such a supported
  restart. A timeout/connection loss before acknowledgement means **outcome unknown**, not rollback.
- Power loss, kernel/host failure, backup/restore, upgrades and failover are not qualified by M1.
  They remain required before operational readiness; no claim that Git plus SQLite automatically
  makes a consistent backup or offers a cross-store power-loss guarantee.
- Provisioning a new empty store is explicit. Existing-store startup cannot silently recreate a
  missing database/repository. A store/project binding identity detects a mismatched pair; missing
  required stores fail closed. Full restore-set consistency remains a later qualification gate.

Proposed SQLite mode: WAL, `synchronous=FULL`, foreign keys enabled and verified on **each** application
connection; bounded write transactions started with `BEGIN IMMEDIATE`. Read back effective settings;
unsupported/unexpected modes fail qualification, not silently weaken the promise. Runtime SQLite must
include the documented WAL-reset fix; check the actual engine, not a driver label alone. The pinned
upstream catalog names `sqlite-jdbc` 3.53.4.0, but that does not qualify a future own-core runtime.
Exact driver/migration/pool APIs remain implementation design; no upstream schema is inherited.

## P1-02 — Canonical store contents and visibility

| Store | Canonical responsibility | Visibility/lifetime |
| --- | --- | --- |
| Service-owned project Git repository | Whole exact UTF-8 document bytes, immutable structure-index bytes, revision commit/tree identities and history | Retained internal ref per reserved revision; raw object/ref existence is not an externally visible proposal or approval. No working-file reads, hooks/filters as authority, or automatic pruning in M1. |
| One instance SQLite database | Projects/documents/revision identities and hashes/Git locations; scoped approvals, continuity, assessments, manifest entries; command journal/results and transactional audit | Only committed rows are visible. Composite project-scoped constraints protect references; immutable records are not updated in place. No cascading history deletion. |
| In-memory locks/caches | Coordinate publishers, avoid duplicate work, accelerate reads | Not durable authority. Loss on restart cannot erase results, silently rebind a manifest or prove approval. |

The structure index is stored with the full revision in Git. Any SQL range/cache projection is
derived and digest-bound, never an editable alternative interpretation. Approval/provenance and
manifests are canonical database records; Git contains their referenced content, not an independent
second approval system. A project Git repository can therefore be intact while its authority store
is unavailable: do not reconstruct owner decisions by inspecting commit text or refs.

Git paths/retained refs are generated from service identities, not caller paths, refs or headings.
Use the Git CLI through an argument-array adapter and controlled execution environment, without
shell interpolation, client file reads, arbitrary hooks or content filters. Exact blobs are written
without line-ending conversion and verified by content digest plus Git identity. Parent/commit
metadata needed for a retry is fixed in the reservation, not regenerated from current time.
Git is an explicit runtime dependency; its exact supported adapter/toolchain needs qualification.

## P1-03 — Command identity and retention

Every mutating command uses `(authenticated principal, project, operation, request key)` plus a
versioned fingerprint of the decoded semantic payload. Include exact document/index bytes or their
verified digests, subjects, context, expected concurrency tokens and declared effects. Transport JSON
formatting/field order and HTTP versus MCP are not separate operations. Concrete canonical encoding
is fixed in the later wire design; a fingerprint must not omit a field that can change effects.

| Journal state | Meaning |
| --- | --- |
| `pending` | Proposal publication reserved identities/descriptor, but no visible revision/effect yet. Not success or approval. |
| `committed` | Logical effect, audit/provenance and encoded result committed together. Matching replay returns the original result without executing the effect again. |
| `rejected` | Recorded terminal semantic/precondition rejection with no domain effect. Matching replay returns that rejection; a changed command needs a new key. |

Same key/different fingerprint conflicts. In-flight matching duplicates return retryable in-progress,
not a second publisher. Database-only commands need not persist a pending phase: insert their result
and effect in one transaction. Syntax/access rejection before reservation need not create a journal
row. Loss of access on retry denies the request without replacing or erasing a prior stored result.

Transient I/O/BUSY/cancellation failures are not cached as successful outcomes. Roll back the failed
transaction; retain an existing pending reservation for a later authorized matching retry. If commit
outcome is uncertain, discard the connection/transaction context and inspect the durable command row
on a fresh connection before attempting any effect again. Never infer rollback from a lost reply.

No automatic journal expiry in M1, including pending/rejected identities. An old key cannot become a
new authorization after a TTL. This has an explicit disk-growth cost. Retention/GC/retirement needs a
separate authorized design; storage exhaustion returns an explicit failure, not silent deletion of
history or pending identity. Admission/body/pending limits must be set before implementation.

## P1-04 — Publishing a document revision

1. Authenticate and validate project rights, exact body/index and declared parent references. An
   old available parent is permitted; two proposals from it may coexist. No global latest overwrite.
2. In a short SQL transaction, reserve the command identity, fingerprint, stable revision ID and
   publication descriptor (body/index hashes, document/parents and fixed Git commit metadata).
   Existing matching terminal results replay; mismatched keys conflict. Commit `pending` reservation.
3. Outside any SQL transaction, acquire the in-process command/project publication locks. Recheck
   pending state after acquiring them. Write immutable Git content/index and a retained revision ref.
   The ref is create-if-absent with an expected old value, never an unconditional overwrite. If it
   already exists, verify the whole reserved descriptor/objects match; a mismatch is integrity failure.
4. Verify retained commit/tree, body/index digests and structure against the reservation. In a final
   short SQL transaction recheck current grants and relevant project/document/preconditions; insert
   the visible immutable revision, audit event and original result; mark the command `committed`.
5. Acknowledge proposal success **only after** that SQL commit. This makes a proposal readable, not
   its clauses approved. A final terminal precondition rejection may record `rejected`; leftover Git
   content stays retained and non-authoritative. Do not delete it as rollback compensation.

The body is not separately editable/staged canonical text in SQLite. If a process dies before Git
publication, resumption requires a matching caller retry resupplying the same bytes/fingerprint.
After content/ref publication, retry verifies and reuses the same reserved identities. Startup may
report pending work, but cannot promote it or replay approval under a stored historical credential.

No external Git operation is inside a database body that may be retried. SQL-only retry scopes must
have no external side effects; bounded backoff/timeout mechanics will be fixed before code. Neither
timeouts nor lock expiry authorize takeover of a still-running publisher. A Git child surviving
the service process cannot commit SQLite authority; it is restricted to the same reserved immutable
publication slot. Encountering a surviving/stale Git lock yields a visible retryable/blocking state;
M1 never blindly deletes lockfiles or overwrites a ref. Subprocess supervision/lock recovery require
explicit implementation checks, including interrupted parent and child processes.
An interrupted Git lock can therefore leave a pending operation visibly blocked, rather than
guaranteeing unattended completion of every crash. Existing acknowledged inputs remain readable.
The implementation task must include a safe recovery/runbook and subprocess ownership checks;
an elapsed timeout alone is never evidence that a lock owner is dead.

## P1-05 — Scoped authority, continuity and consumer manifests

Approval, consistency/continuity and manifest commands reference **already visible, immutable**
revisions. Verify their Git bodies/indexes outside the SQL transaction first. With immutable retained
objects and no pruning, the subsequent SQL write cannot race an ordinary content mutation. Unexpected
external loss is still an integrity fault, not an authorized fallback.

In one SQL write transaction:

1. Recheck current caller grant, exact subjects/context, original provenance, relevant declared
   dependencies/blockers and expected authority tokens. Validate command replay before a new effect.
2. Insert the explicit scoped effect and its provenance/audit, plus its encoded result, atomically.
   An approval of several clauses is all-or-nothing for the stated set. Include required attached
   continuity/assessment records in that same transaction if the command promises them as one result.
3. Commit, then acknowledge. No Git write is required to grant authority or register a manifest.

Approval of X does not require Y to receive new approval. Continuity can reference Y's original
records without resetting their status. Registering a consumer manifest is a separate command over
committed authority: it cannot implicitly approve a selected draft. A crash between X approval and
later S2 registration may leave X approved and S2 absent, **not** a partial successful combined
operation. Existing S1 is unchanged. A manifest command inserts the entire immutable manifest, entries,
context/provenance links, audit and command result together, or none of them.

Expected tokens are scoped to touched clause/context authority cells and explicitly declared guard
records, not the entire document revision. Two conflicting stale transitions for X cannot both win.
Independent Y updates may serialize on the SQLite writer without being rejected merely because X
changed. Continuity to Y's unchanged occurrence does not create a new owner-approval generation for Y.
Known relevant blocker/dependency changes invalidate their declared guards. Missing semantic
dependencies remain a planner/review risk, not proof of an automatic impact oracle.

Tokens coordinate concurrent transitions; they do **not** withdraw older approvals or force old
snapshots to newest values. Deliberately old valid inputs remain readable/usable within D5. A stale
command cannot silently rebase its target or acquire a new meaning; it must be reconsidered explicitly.

## P1-06 — Reads, replay and recovery

- Resolve metadata/authority/manifest entries in one short SQL read snapshot. Then read their exact
  immutable Git objects and verify hashes/indexes; do not hold a long SQL reader while streaming Git.
  Recheck access at the applicable request/response boundary; credential/grant implementation is next.
  Define its authorization linearization point explicitly: no promise that a decision remains current
  indefinitely while a network response is already in flight. Mutating effects check at their commit.
- No mutable working-file, latest-revision or proposal fallback. Missing/corrupt body/index/ref or
  broken provenance produces explicit integrity/unavailable failure, preserving original history.
- A committed replay checks **current** access before revealing its original stored result. If the
  response promises available content or usable inputs, verify those references; current corruption
  returns integrity failure without erasing the original committed outcome or re-executing effects.
- Startup acquires the store lock, validates store pairing, schema/runtime settings and the pending
  journal. SQLite handles its journal recovery. Pending rows/Git refs are not accepted knowledge.
  On-access verification checks exact content; no startup claim of exhaustive semantic consistency.
- Keep WAL files with the database state; never delete them to "repair" startup. Do not repair
  missing history by rebuilding authority from Git, resetting migrations or creating an empty store.

## Failure matrix and derived checks

Each row is a finite verification obligation, not an executed check or assertion of exhaustive paths.

| ID / K-APP | Injection or condition | Required observation / narrow boundary |
| --- | --- | --- |
| PR-01 / O1,O7 | Fail before reservation commit, or after reservation before Git | No visible revision/approval; either no row or pending reserved identity. Matching retry uses one logical publication. Real SQL/Git adapter boundary. |
| PR-02 / O5,O7 | Stop during body/index/commit write or before retained ref | No visible proposal or acceptance; retry validates/reconstructs the reserved publication, never uses partial bytes. Real Git with child interruption. |
| PR-03 / O1,O7 | Stop after retained ref, before final SQL commit | Content may remain; no visible revision or approval. Authorized retry can finish the same reserved revision. Real stores/process. |
| PR-04 / O7,O8 | Final SQL commit succeeds but reply is lost | Original visible revision/effect/result survive restart; retry returns it, not another revision/ruling/manifest. Real stores/process. |
| PR-05 / O2,O7 | Fail within multi-clause approval/required attached continuity transaction | Whole prior or whole new scoped result only; independent Y status/history unchanged. Real SQL transaction plus fault seam. |
| PR-06 / O3,O4,O7 | Stop between X approval and S2 command; separately interrupt S2 insert | X may be approved with S2 absent; no partial manifest accepted as success. Once acknowledged, C1/S1 and C2/S2 keep exact distinct inputs. Real SQL; one assembled journey. |
| PR-07 / O5,O7 | Lose/corrupt body, index or retained ref; substitute another project store | Explicit integrity/unavailable error, no latest/proposal/empty-store fallback. Focused real-store checks. |
| PR-08 / O8,O9 | Concurrent duplicate key; same key changed payload; revoked access on completed/pending retry | At most one effect; changed payload conflicts; current denied caller gets neither replayed protected result nor resumed mutation. Rules plus real concurrency/auth wiring. |
| PR-09 / O2,O6,O8 | Two stale approvals affect X; independently update Y; change a declared relevant guard | One conflicting X winner; no whole-document invalidation of independent Y; stale declared guard cannot be ignored. Focused rule/SQL checks. |
| PR-10 / O7,O8 | BUSY, disk full, cancellation or I/O error; ambiguous commit outcome | No error-to-success coercion/partial commit; pending survives where reserved; resolve outcome by durable key before retry. Real transaction/error translation checks. |
| PR-11 / O7,O9 | Second service startup, missing one store, unsupported settings/engine, existing WAL | Refuse unsafe startup; preserve WAL/state; no auto-repair or new empty authority. Real startup boundary. |
| PR-12 / O5,O7,O8 | Parent service dies with surviving Git child or lock artifact | No orphan authority commit, overwrite or blind lock deletion; deterministic slot verified or operation remains visibly blocked. Real supervised subprocess qualification. |

Fast candidates cover fingerprints, state transitions, scoped tokens, immutable refs and replay
policy with meaningful state assertions. Transaction/Git/process claims need the actual selected
boundary, not mocks. PR-06's representative client journey connects these mechanisms; do not repeat
the entire fault matrix through every transport or use E2E to compensate for missing focused checks.
Runtime/resource cost is measured separately from level. Exact cheap/expensive classifications and
authorized checkpoints remain to be planned; no expensive suite is run or implicitly authorized here.

## Grounding and limits

Primary references inspected for this proposal:

- [SQLite WAL](https://www.sqlite.org/wal.html): read snapshots, single writer, local-host restriction,
  FULL versus NORMAL sync, persistent WAL, and documented WAL-reset fix requirements.
- [SQLite transactions](https://www.sqlite.org/lang_transaction.html): `BEGIN IMMEDIATE`, BUSY,
  transaction state and explicit rollback after statement errors. Resolving a lost acknowledgement
  by durable command identity is this protocol's requirement, not a claim that every SQL error rolled back.
- [SQLite foreign keys](https://www.sqlite.org/foreignkeys.html): per-connection enablement,
  composite constraints/NULL considerations and restrictive versus cascading deletion.
- [Git update-ref 2.43.0](https://git-scm.com/docs/git-update-ref/2.43.0): expected-old/create-if-absent
  updates and the limitation that multi-ref updates do not give every concurrent reader one snapshot.
- [Git hash-object 2.43.0](https://git-scm.com/docs/git-hash-object/2.43.0): raw blob writes without
  filters/end-of-line conversion. Local `git --version` reported 2.43.0; no storage behavior was tested.

Documentation supports the mechanisms, not a qualified Writhelm implementation. No crash experiment,
selected-driver execution, benchmark, source extraction or independent review occurred. Fsync and
SQLite FULL are prudent configuration, not evidence that the two-store system survived power loss.

## Approval scope and continuation

Request P1 approval for the Git/SQLite split, content-before-authority protocol, durable non-expiring
command identity/replay, scoped authority concurrency and one-process/local-storage/process-crash
qualification boundary, with explicit retention/maintenance costs. It does not approve every logical
field, a schema/migration implementation, credential provisioning, limits/encoding/routes, power-loss
or backup guarantees, test execution or publication. Exact contract wording follows approved rulings.

After P1: authority provisioning/revocation and API/authoring inputs, including canonical fingerprints,
resource limits and testable seams. Prepare the planned verification checkpoints before implementation
tasks; do not silently adopt the held fork design or count this protocol as working behavior.
