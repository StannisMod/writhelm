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

## Public repository boundary

Follow [docs/publication.md](docs/publication.md). This repository must never become an export of
private project material, session history or local evaluation workspaces.

Use explicit file staging, inspect the complete staged diff and track only files allowed by the
publication manifest. New publication paths require an explicit scoped decision. Never assume that
access to a file authorizes publishing it. Preserve upstream license notices when reusing code.
