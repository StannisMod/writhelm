# REVIEW-CORE-001 — Independent review of CORE-001/002/003

Task revision: 2. Status: independent bounded review completed; parent evidence inspection completed.
Assessed source commit: `f94108b5f64bf5389199a26436048e34c714e51c`.
Executor: a new native child, not either producer. No delegation of this review is allowed.

## Objective, inputs and authority

Critically review the first three own-core blocks for concrete violations of their contracts and
integration invariants: exact content, approval/pinned-input workflow, strict codec/whole-state restore.
User explicitly requested this review. Parent/producer checks are claims, not independent acceptance.

Read the canonical task and frozen commit copies of README/AGENTS, `docs/publication.md`,
`docs/contracts/{knowledge-content,knowledge-workflow,state-codec,accepted-knowledge}.md`,
`docs/design/{knowledge-model,git-state,core-codec-readiness}.md`, `docs/schemas/core-codec*.json`,
`docs/tasks/CORE-00{1,2,3}.md` and ALL own-core source/tests/build under `backend/server/`.
K-PUBLISH/K-AUTH describe later boundaries, not requirements to pretend persistence/auth already
exist in this library. Accepted K-CODEC CD-01–08 and CORE-003 revision-4 scope govern its code;
source status/proposed historical design wording is not an additional accepted feature requirement.
The frozen input manifest and read-only assessed-copy location are supplied at launch.

## Read/write/decision boundaries

Assess ONLY the exact frozen commit. No edits to assessed code, tests, build, fixtures, contracts or
any working-repository file, even to fix an issue or make a failing test pass. Assessed files and
directories are read-only; no chmod, replacement, links into mutable source or permission bypass.
This is cooperative-mode read-only enforcement, not a claim of hostile same-user OS isolation.

Write only the designated separate report/scratch area. Independent repro probes may be authored
there, never by modifying assessed source/tests or restoring deliberate bugs. A separate scratch
build may reference unchanged read-only assessed source/tests and add its own probe files; keep
their provenance distinct. Recheck assessed hashes on exit. No index/history/store/ref mutation,
paid/live inference, further agents, network/dependency download, config outside repo or upstream suite.

Use bounded inspection and smallest concrete cheap repros. Do not rerun the existing full suite merely
for handoff or perform Git/auth/process/crash/large-capacity experiments outside this scope. If a
Gradle probe is necessary, use the qualified JDK/cache/caps and the shared build lock supplied at
launch; no concurrent Gradle builds with implementation workers. No cap increase without escalation.

## Acceptance and expected output

Findings require severity, exact requirement clause, source location, a concrete failure scenario,
expected versus actual behavior and reproducible evidence where feasible. Search for counterexamples:
tampered references/partial state, hidden approval widening, loss of old traces, stale/ABA/replay,
current versus historical policy, JSON/token ambiguities, unknown/duplicate fields, graph completeness,
budget/allocation/refusal and immutable boundary failures. Include positive cases, not only rejection.

Style preferences, hypothetical unsupported deployment or missing later features are not blockers.
Separate established failures, static risks, unexecuted hypotheses and nonblocking improvements.
Do not declare the whole service accepted from passing pure tests. No finding is also a bounded
review result, not proof of exhaustive correctness.

Return `result.md` and `findings.json` in the designated scratch report root: requirement-linked
findings, precise checked scope/commands/results, untested limits, assessed manifest hash confirmation
and overall recommendation. Parent independently checks consequential findings before integration
or requesting producer fixes. Do not change this task's result in the repository yourself.

## Result — exact assessed commit, not whole-service acceptance

Fresh-context non-author reviewer inspected the full own-core source/tests/build and applicable
contracts for CORE-001/002/003 at the pinned commit. No concrete blocking contract violations were
established; no producer fix was requested. No style preference or unsupported future deployment
requirement was presented as a defect. This remains a finite review, not exhaustive proof.

Four independently authored probes ran against unchanged read-only production sources in separate
scratch build: scoped approval/stale ABA/replay; frozen continuity provenance after later direct
approval/blocker; incomplete/tampered streams without source mutation; strict JSON/independent
fingerprint and historical admission separation. Actual JUnit XML: four tests, zero failures/errors/
skips. The original 124-case suite was inspected but not rerun or called independent execution.

Parent read report, machine-readable findings, new probe source/build and executed log/XML, and
recomputed all 72 frozen file hashes/permissions. All match the original read-only manifest, whose
SHA-256 is `924562824ab3e7cda5f2a05d6504abe249e94fbbc9bad8e2b4c095afc2893b9e`.
No assessed source/test/doc/build changes occurred. Independent review evidence and raw reports are
retained outside public Git. New auth/store code is not part of this assessed commit and inherits
no independent-review status from it.

Git/auth/network, real process crashes/restart, source-H completeness, production/default-maxima/
deep-history capacity and integrated M1-C1 behavior remain unverified. The bounded pure-core review
gate is now satisfied for this exact input, not a blanket integration/release acceptance.
