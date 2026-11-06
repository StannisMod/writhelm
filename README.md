# Writhelm

**Decisions remembered. Work scoped. Results verified.**

Writhelm is a planned self-hosted service for project knowledge and governed work across AI
coding harnesses. It keeps accepted decisions, scoped tasks and verification evidence connected,
without making a conversation transcript the source of truth.

## Status

Design stage. There is no working Writhelm service yet. The roadmap describes intended capabilities,
not implemented guarantees or measured performance.

The selected direction is a Writhelm-owned core with selective reuse, not a maintained
[Task Orchestrator](https://github.com/jpicklyk/task-orchestrator) fork. Its pinned source baseline
remains unchanged under `backend/` for reference and reuse qualification. That upstream baseline
passes its tests, lint and JAR build; this is not verification of a Writhelm core.
The local implementation includes immutable content/approval/input manifests, a strict state codec,
credential/authority rules, atomic Git publication, HTTP/MCP application adapters and a native
OpenCode Desktop plugin candidate. Bounded independent reviews and targeted combined checks have
run; this is not a qualified launched service. Installed OpenCode rendering/IPC, abnormal recovery,
other-OS operation and M1 acceptance remain open; one earlier component-start timeout is unexplained.
Dependency license qualification remains open.
The H1 connected-work contracts are approved, and a bounded portable implementation candidate has
scoped independent review and parent acceptance in isolated worktrees. It is not yet integrated into
this source tree or qualified for installed native execution. See the [completion plan](docs/roadmap.md).
Linux, Windows and macOS are target service platforms. The current native store is Linux-specific;
other platform adapters and cross-platform operational qualification are not implemented or verified.
See [upstream provenance](docs/upstream.md).
Writhelm is an independent project, not an official upstream distribution.

## Direction

- One service owns canonical knowledge, authority and workflow state, all versioned in internally
  managed Git; any database index is derived, not an independent source of truth.
- Human approval applies to a specific revision, not whatever text happens to be current.
- Every delegation has a separate task with scope, inputs, acceptance and escalation conditions.
- Context packets preserve mandatory obligations or explicitly refuse to fit a smaller budget.
- Accepted decisions, implemented changes, verified results and integration remain distinct.
- A planning session coordinates bounded workers and risk-based independent verification.
- Harness adapters provide native lifecycle integration; MCP access alone does not control history.
- Platform-independent semantics with separately qualified Linux, Windows and macOS runtime adapters;
  platform-specific dependencies must not silently narrow the product to one OS.

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
