# DESIGN-001 — Writhelm-owned foundation

Status: accepted foundation direction; subordinate designs retain their own approval/evidence status.

## Decision

Writhelm owns the canonical knowledge/task/authority/evidence model and its standalone backend.
The foundation is described in [foundation](../design/foundation.md).

## Requirements

- Preserve exact immutable knowledge references, clause-local approvals and consumer manifests.
- Use structurally versioned Git as the canonical store; indexes remain derived.
- Keep bounded task scope, dependency readiness, credentials, work areas and result provenance explicit.
- Distinguish implemented, verified, accepted and integrated work.
- Verify the platform implementation with project tests and admit ordinary operations using their
  phase-specific machine checks under [K-EXECUTION](../contracts/execution.md).
- Respect [publication policy](../publication.md) and license notices for any third-party reuse.

This decision does not approve every detailed contract or authorize model calls, publication or
integration. Subsequent design tasks define the representation, storage and client contracts.
