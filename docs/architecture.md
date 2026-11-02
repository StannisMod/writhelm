# Architecture direction

Status: proposal. These are design boundaries, not implementation claims.

## Ownership

The service owns canonical knowledge revisions, workflow records, accepted decisions, task snapshots,
context assembly and evidence applicability. Coding repositories retain their own source and history;
they do not need maintained copies of the canonical knowledge corpus.

Knowledge content has one canonical owner. Git publication and database state need an explicit
recovery protocol; an uncommitted working file must not be exposed as an accepted revision.

## Planning and execution

The human works in a primary planning session with a large available context. It handles design,
decomposition, owner decisions, dependencies and integration judgment rather than routine implementation.
Large available capacity does not imply that every request should carry the entire corpus.

Workers receive smaller task-specific contexts and isolated changes. Their model, reasoning effort,
context budget and compaction policy belong to an explicit execution profile. They may reason about
local implementation, but must escalate changes to product intent or delegated authority.

Independent verification checks the original task, consequential claims, evidence freshness and
critical implementation paths. Review depth follows risk. Selective inspection is not a substitute
for tests, and a worker's summary is not proof of completion.

After compaction or restart, the task revision, knowledge snapshot, scope and open obligations are
recovered from the service rather than inferred from a lossy conversation summary.

## Authority and budgets

An approval identifies the exact revision and authorized principal. A later proposal does not inherit
that approval. Historical decisions and evidence stay auditable without automatically applying to new work.

Context assembly distinguishes mandatory obligations from optional retrieval. If the mandatory closure
cannot fit, it must refuse or request an authorized scope change rather than silently omit requirements.
Budget enforcement must account for every outgoing request, including compaction, instructions and tools.

## Harness boundary

Adapters connect the workflow to native dispatch, session lifecycle and compaction surfaces. MCP alone
cannot constrain unrestricted shell access, native spawning or model history. Supported enforcement
boundaries must be documented and demonstrated for each harness.
