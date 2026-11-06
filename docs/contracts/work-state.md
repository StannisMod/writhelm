# H1 canonical work state and version compatibility

Contract K-WORK-STATE, revision 1. Status: six disclosed storage/version/readiness decisions approved
by owner; not executable schemas or migration/runtime qualification. Governing [H1](agent-workflow.md),
[work authority](work-authority.md), existing private Git SSOT/current-access/codec laws unchanged.

## WS-01 — One root and versioned work namespace

Tasks, assignments/claims, progress/continuation memory, retrospective/results/reviews, acceptance
and integration facts live in a new versioned work namespace of the same private instance Git root.
No second canonical database in plugin/SQLite. Code/history stays in coding repositories; work
records pin actual code/artifact/evidence identities and retained locators, not whole corpus exports.
Operational native/UI/heartbeat state is not canonical mutation by default; retain needed decision
basis and meaningful checkpoints, not a receipt for every tool/model/UI movement.

## WS-02 — Separate bounded records

Use individual bounded metadata records/body blobs and deterministic keys/tree enumeration, not a
growing all-tasks/all-progress JSON. Immutable task/result/report/memory revisions differ from
mutable current-state pointers. Exact refs/status/provenance, history and per-record refusal remain.
Large artifacts use exact accessible retained identities, not silent truncation or rawpath/URL API.

## WS-03 — Explicit work authority evolution

Work grants/delegated scopes/enrollment follow K-WORK-AUTH with explicit versioned auth profile/
record design. No silent new values in old closed formats, duplicate independently effective grants,
owner escalation or unversioned client authority. Canonical verifier/bindings only; raw secrets stay
client/controller-side. Registry versions select supported whole-state validators.

## WS-04 — Preserve existing command meaning, version new effects

Existing core command/fingerprint v1 and auth command/fingerprint v2 preserve byte/identity semantics.
New work/delegation operations get explicit next-version envelope/fingerprint schemas; journal
evolves to cover complete core/auth/work effect plus receipt in one expected-old publication.
Current-authority replay/unknown/absence laws persist. Don't bump unrelated formats for bookkeeping;
freeze necessary headers/policy/namespace/envelopes/goldens before source changes. Incompatible
whole-store writer fails rather than losing unfamiliar records.

## WS-05 — Initial H1 uses explicit fresh genesis

First H1 qualifies new explicit instance/store profile, not automatic in-place M1 migration. Existing
store refuses unsupported profile, never reset/bootstrap/history patch. No fabricated old actors/
receipts/evidence; old synthetic artifacts preserved. If old owned data must be carried forward,
separately approve verified import/migration preserving historical bindings first. No production
migration/backup readiness or destructive cleanup is implied.

## WS-06 — Freeze details then bounded implementation

Pin closed schemas/invariants, role/operation authorization matrix, compatibility/refusal, explicit
limits, independently derived examples and clause→case→test mapping; then versioned implementation
tasks/DAG/write scopes/affordable checks. Routine field names need no new owner ballot; consequential
semantic/compatibility expansion does. Contract acceptance alone isn't blanket implementation,
live/model/host qualification, new publication scope or permission to weaken existing tests.

## Approval provenance

Owner explicitly approved six-point K-WORK-STATE v1 with “утвердить” on 2026-11-05, after bounded
work authority was agreed. This manual record accepts disclosed storage/compatibility profile, not
unshown field schemas, code, migration or a passing H1 pilot. No commit/push or live-call permission.
