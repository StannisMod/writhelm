# Writhelm

**Decisions remembered. Work scoped. Results verified.**

Writhelm is a planned self-hosted service for project knowledge and governed work across AI
coding harnesses. It keeps accepted decisions, scoped tasks and verification evidence connected,
without making a conversation transcript the source of truth.

## Status

Design stage. There is no working Writhelm service yet. The roadmap describes intended capabilities,
not implemented guarantees or measured performance.

The workflow foundation is a maintained fork of
[Task Orchestrator](https://github.com/jpicklyk/task-orchestrator), imported at a pinned baseline
under `backend/`. The unchanged upstream baseline passes its tests, lint and JAR build;
Writhelm-specific features are not implemented. Dependency license qualification remains open.
See [upstream provenance](docs/upstream.md).
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
- [Upstream provenance](docs/upstream.md)
- [Publication policy](docs/publication.md)
- [Contributor and agent instructions](AGENTS.md)

## Licensing

Writhelm is licensed under the **GNU Affero General Public License, version 3 only**
(`AGPL-3.0-only`). See [LICENSE](LICENSE) for the full terms.

Any future upstream code reuse must preserve its provenance, license and required notices.
The imported Task Orchestrator source retains its [MIT license](backend/LICENSE) and notices;
Writhelm additions use AGPL-3.0-only.
