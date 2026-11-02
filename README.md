# Writhelm

**Decisions remembered. Work scoped. Results verified.**

Writhelm is a planned self-hosted service for project knowledge and governed work across AI
coding harnesses. It keeps accepted decisions, scoped tasks and verification evidence connected,
without making a conversation transcript the source of truth.

## Status

Design stage. There is no working Writhelm service yet. The roadmap describes intended capabilities,
not implemented guarantees or measured performance.

The intended workflow foundation is an extension of
[Task Orchestrator](https://github.com/jpicklyk/task-orchestrator). No upstream implementation is
vendored here. The extension strategy and licensing must be settled before implementation is released.
Writhelm is an independent project, not an official upstream distribution.

## Direction

- One service owns versioned knowledge, workflow state and internally managed Git.
- Human approval applies to a specific revision, not whatever text happens to be current.
- Every delegation has a separate task with scope, inputs, acceptance and escalation conditions.
- Context packets preserve mandatory obligations or explicitly refuse to fit a smaller budget.
- Accepted decisions, implemented changes, verified results and integration remain distinct.
- A planning session coordinates bounded workers and risk-based independent verification.
- Harness adapters provide native lifecycle integration; MCP access alone does not control history.

## Documents

- [Roadmap](docs/roadmap.md)
- [Architecture direction](docs/architecture.md)
- [Publication policy](docs/publication.md)
- [Contributor and agent instructions](AGENTS.md)

## Licensing

A project license has not yet been selected. Public visibility is not a license grant. Any future
upstream code reuse must preserve its provenance, license and required notices.
