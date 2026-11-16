# Writhelm-owned foundation

Status: accepted foundation direction. Detailed contracts retain their individual approval status.
Canonical task: [DESIGN-001](../tasks/DESIGN-001.md).

Writhelm owns its knowledge, task, approval, authority and evidence model. The backend is the
standalone Gradle project in `backend/server/`; native integrations live in `adapters/<harness>/`.
There is one canonical service, not a gateway delegating workflow authority to another engine.

## First-slice obligations

- Immutable document revisions and stable clause identity, scoped approvals and explicit continuity.
- Exact pinned input manifests for independent consumers; old approval does not cover unapproved edits.
- Structurally versioned Git is the source of truth for knowledge, authority and workflow state.
  Any database index is derived and contains no unique canonical facts.
- Durable command identities, whole-state publication, exact-snapshot reads and fail-closed recovery.
- Canonical executable tasks, dependencies, scoped native sessions/worktrees and attributable results.
- Exact-candidate checks, independent non-author review and separately authorized integration.

Use [accepted knowledge](../contracts/accepted-knowledge.md), [Git state](git-state.md),
[first-slice design](first-slice.md) and [execution](../contracts/execution.md) as the relevant
normative sources. A foundation decision is not blanket approval of their drafts.

## Reuse boundary

Libraries and public-source mechanisms may be reused only with explicit dependency, license and
contract review. No external workflow API or storage compatibility is implied. Writhelm owns the
behavior it exposes and the tests establishing it. Project tests verify platform implementation;
ordinary task admission follows phase-specific machine checks, not an additional model review.
