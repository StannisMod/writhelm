# DESIGN-008 — Authentication and project authority contract

Task revision: 2. Status: K-AUTH revision-1 disclosed scope approved; no auth implementation/verification.
Executor: parent session. The owner requested this design in parallel with delegated CORE-003.
The codec child has its separate revision-4 task and source/test scope; do not duplicate or edit
its assessed implementation while it is active. This task grants no additional delegation.

## Objective and knowledge inputs

Specify the smallest initial service authentication/current-authorization boundary under accepted
T1/U1/R1/P2: credential origin/storage, initial principals/owner/grants, scoped access, revocation/
rotation, current-policy replay, owner answer path and explicit lockout/transport limits. Surface
required extensions to codec/publication schemas without silently amending accepted contracts.

Inputs: at source commit `0c7a7757`, README/AGENTS/publication policy, K-CONTENT, K-WORKFLOW, K-APP,
`docs/design/first-slice.md` FS-02/03 (A1 is still proposed),
`docs/design/approval-interface.md` UI-01–06 and accepted T1 qualification,
`docs/specification.md` WH-02/08/16 and T1. Local accepted inputs: K-CODEC revision 2 and
K-PUBLISH revision 1 with their approval provenance. Read the actual source sections, not indexes.
Public security grounding: OWASP Session Management (random entropy, identifier/verifier split,
one-way storage, transport and browser storage) and RFC 6750 sections 1/2/3/5. Read source material;
distinguish borrowed mechanisms from full OAuth/RFC conformance and unqualified local exceptions.

## Scope and constraints

Write only this task and `docs/contracts/authentication-access.md`, local/ignored. No code, keys,
server, model call, live auth experiment, credential/config change, schema/publication allowlist
change, staging/commit/push or edits to the child's files. Security/product design remains proposed
until the owner approves disclosed consequences. The owner subsequently approved K-AUTH revision 1;
this is not runtime verification or approval of additional unscoped work. Do not reopen T1 as an enterprise isolation gate.

Concrete auth lifecycle commands/new namespaces require an explicit extension boundary against
K-CODEC's six v1 command variants and K-PUBLISH's core-only write descriptors; do not claim they
already fit existing schemas or authorize the worker to implement them. Real credentials, remote
TLS/hosting, native-client isolation, account recovery and broad project lifecycle are not inferred.

## Acceptance and escalation

Provide closed operation-to-capability rules, current-head validation/retry behavior, no secret in
Git/packets/logs, exact credential and revocation identity, atomic rotation, bootstrap-only initial
authority and last-usable-owner checks. Distinguish policy from secret possession, fact from human
attestation, historical grants from current authority and unavailable/unknown from terminal refusal.
Derive finite positive/negative cases and name compatibility changes. Escalate real conflict; do not
weaken accepted scope or promise API/hook resistance excluded by T1. Output owner-readable proposal
and a source-linked compatibility assessment; no passing behavior/independent review claim.

## Result and consistency assessment

Prepared K-AUTH revision 1, AU-01–10, with identifier/secret verifier split, explicit project-bound
credentials, current access on ordinary and replay/CAS paths, fixed initial owner profile, atomic
rotation/revocation and usable-owner guard. T1 is preserved: no hard anti-hook/automation attestation
is made a prerequisite. Long-lived revocable credentials and loopback HTTP are disclosed proposed
profile tradeoffs, not attributed to full RFC 6750 security conformance.

Compared against WF-01/02/03/04/08, CD-01/03/05/06/08, PB-02/03/04/05/06/07/08, proposed A1 and
accepted T1/U1. There are REQUIRED ADDITIVE EXTENSIONS, explicitly proposed in AU-09: auth namespace,
new authority command envelope/fingerprint v2 and journal v2 for credential provenance plus auth/core
write sets. Existing v1 command bytes, core model, fingerprints and the child's inputs are unchanged.
These extensions require approval with K-AUTH, now explicitly given; no compatibility with an
unextended writer is claimed.
WF's multi-manager pure model remains valid; the single-owner restriction is a narrower proposed
network-service profile, not a hidden amendment to the accepted core contract.

Public primary security sections were read for bearer possession/header/disclosure/TLS/lifetime,
randomness/verifier separation, constant-time comparison and browser secret storage. This is author
design review, not a security audit, passing implementation test, credential experiment or claim
that source guidance validates the profile's explicit local/long-lived exceptions.

The owner approved "auth-1", identifying K-AUTH revision 1 and its disclosed AU-09 extensions; the
exact pre-status-update bytes are retained by digest in the contract. Next: exact auth schemas and a
separate executable task. CORE-003 implementation remains delegated under its own revision-4 scope;
do not mutate its pinned contract/schema/profile inputs while it runs.
