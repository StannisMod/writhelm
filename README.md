# Writhelm

**Decisions remembered. Work scoped. Results verified.**

Writhelm is a planned self-hosted service for project knowledge and governed work across AI
coding harnesses. It keeps accepted decisions, scoped tasks and verification evidence connected,
without making a conversation transcript the source of truth.

## Status

Implementation in progress. Portable service/work mechanisms exist, but an operational Writhelm
service and complete H1 pilot are not qualified. The roadmap distinguishes intended capabilities
from implemented and verified guarantees.

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
The approved H1 connected-work implementation is included in this source tree after scoped review
and portable acceptance. Its real OpenCode bridge and installed execution remain unqualified. See
the [completion plan](docs/roadmap.md) and [portable test inputs](backend/server/src/test/resources/h1/README.md).
Linux, Windows and macOS are target service platforms. The current native store is Linux-specific;
other platform adapters and cross-platform operational qualification are not implemented or verified.
The owner-approved non-LLM correction route has a pure exact-text preparation kernel, a non-exposed
Linux exact-file effect primitive and staged mechanical profile/producer/result descriptors with
explicit author attribution and legacy-byte compatibility checks. These are not yet an enabled
writer profile or operational patch tool; task/authority/claims/effect/history integration and
independent qualification remain open. The native primitive's admission/retention ports remain
unconnected; read-only byte observations do not establish writer termination or release claims.
Mechanical profiles, assignments/bindings, retained original-effect and result records can be restored in
the existing bounded canonical graph under explicit candidate model3 selection, sharing claims and
workspace exclusion with native work. These are structural consistency checks, not authenticated
effect/writer facts. Candidate graph review bindings, reports and acceptance checks exclude declared
authors; actual review and acceptance command admission remains deliberately unavailable.
Legacy command/read/service writer selection remains unchanged and refuses it.
An isolated candidate command4 metadata codec covers mechanical reservation, binding, effect and
result shapes, with bounded digest-checked prepare attachments. It is not registered in the store,
journal, service or tool; decoding an attachment does not publish or authorize it.
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
