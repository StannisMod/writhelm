# Contributor and agent instructions

Read README.md and the document relevant to the assigned task. Do not load all documentation by default.

## Work contracts

- Design changes require explicit owner approval before implementation.
- Each delegation needs a separate versioned task: objective, inputs, read/write/decision scope,
  constraints, dependencies, acceptance criteria, escalation conditions and expected output.
- Give workers the canonical task and exact knowledge references, not a rewritten parent transcript.
- Treat worker reports as claims. Independently check consequential findings and reproducible evidence.
- Separate proposed/accepted knowledge and implemented/verified/integrated work.
- Live model calls, embeddings and paid experiments require explicit authorization.
- Do not modify user-wide harness configuration or unrelated repositories.

## Cross-platform development

- Linux, Windows and macOS are target service platforms, not Linux plus remote clients only.
- Keep domain logic and wire/storage formats platform-independent. Put OS-specific process,
  permissions, filesystem, executable discovery and secret-storage behavior behind explicit adapters.
- Do not introduce unguarded Unix paths, shell utilities, POSIX permissions or one-OS launch assumptions
  as portable defaults. Unsupported/unqualified adapters must fail explicitly, not weaken guarantees.
- Qualify build/run, Git publication, locking/synchronization and abnormal recovery separately for
  each OS/profile. A JVM build, mocked test or successful Linux run is not cross-platform verification.
- Record platform scope of evidence and held gates. Implementation order requires an explicit plan;
  a first Linux profile must not silently redefine the supported product as Linux-only.
- Follow the approved [launch/recovery contract](docs/contracts/launch-recovery.md). Do not claim
  platform support before its relevant acceptance checks pass.

## Public repository boundary

Follow [docs/publication.md](docs/publication.md). This repository must never become an export of
private project material, session history or local evaluation workspaces.

Use explicit file staging, inspect the complete staged diff and track only files allowed by the
publication manifest. New publication paths require an explicit scoped decision. Never assume that
access to a file authorizes publishing it. Preserve upstream license notices when reusing code.
