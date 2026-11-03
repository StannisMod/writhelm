# AUTH-001 — Authentication schema and execution readiness

Task revision: 1. Status: schema/fixture preparation completed; auth implementation not started.
Executor: parent session. Scope: continuation of accepted K-AUTH revision 1 (owner's "auth-1"),
not another architecture ruling or a request to change the concurrently implemented core codec.

## Objective and inputs

Freeze field shapes for auth records, service command envelope v2 and journal v2, derive independent
synthetic verifier/grant/fingerprint fixtures and concrete development limits, then name the bounded
auth implementation task with dependencies. No live key generation or transport qualification.

Inputs: accepted K-AUTH AU-01–10, pre-approval bytes SHA-256
`0679e7020afc25935f8fe47c42085401ba7eaf521a0c1a63bf282aa59f8e7fe4`; accepted K-CODEC revision 2
and K-PUBLISH revision 1, K-CONTENT/K-WORKFLOW; existing core field schema v1. Follow README/AGENTS/
publication policy. Pin final schema/goldens/profile digests before implementation, not this draft
task alone. Semantic changes require escalation; routine explicit field realization is in scope.

## Read/write/decision boundary

Write only this task, `docs/schemas/auth-service-v1.json`, `docs/schemas/auth-service-goldens-v1.json`
and `docs/design/auth-service-readiness.md`, all local/ignored. Read contracts/schema inputs, not the
child's in-flight implementation as an unverified API guarantee. Do not edit core source/tests,
CORE-003, its seven pinned inputs, approved contract laws or user-wide configuration.

No new delegation, network service, real credential, random secret provisioning, binary distribution,
Git data repository, schema migration, allowlist changes, staging/commit/push or live inference.
Only small offline shape/fixture checks allowed; no expensive/resource-heavy/integration suite.

## Acceptance and expected output

Closed schemas distinguish data from actor claims, secret tokens from public verifiers and authority
failures from core WF failures. Version-1 commands/fingerprints remain unchanged; v2 is limited to
the accepted authority operations. Journal-v2 write sets cover both namespaces and preserve original
credential provenance, principal-scoped replay and one effect/result publication. No duplicate policy
store or inferred project enrollment. Document limits/shape-versus-graph obligations and compatibility
gates. Report exact checked examples and unverified boundaries, not a production-security claim.

Escalate contract conflicts, missing runtime/API dependencies or a need to touch the worker's scope.
Completion is schema/fixture/readiness output; auth/Git/HTTP implementation remains separate and
must bind the completed codec's actual API and qualified publisher/runtime rather than a guessed one.

## Exact outputs and author checks

| Output / accepted input | SHA-256 |
| --- | --- |
| `docs/contracts/authentication-access.md`, approval status added, AU-01–10 unchanged | `0b3c4b678c9a49e07a66da692b146b74f9f2041284b7183e699e643599cdf882` |
| `docs/schemas/auth-service-v1.json` | `ebbf4001997205a9fe2be76a09982f8739f90ebcf60224f224706f2e1fffdb8b` |
| `docs/schemas/auth-service-goldens-v1.json` | `cc333920692daab50266c74bf7d2b7b07c6bbade0e24999356fc3251eb3b1421` |
| `docs/design/auth-service-readiness.md` | `53ceca312d3027aec141da860aa0726dbd50f411a04829da7b4cc2b75fcb341e` |

Schema meta-validation passed; 12 positive field-shape samples and 189 targeted negative mutations
matched expected observations. Nested core references were expanded from pinned local bytes due to
the installed tool's legacy opaque-URN resolution limitation; no source-schema rewrite or dependency
download. This is not production resolver/auth qualification. A deliberately mismatched receipt
operation witnessed the separately required semantic equality check, not an accepted valid receipt.

Independent fixed synthetic byte vectors were verified for token spelling/length, verifier bindings,
absent versus empty grants and v2 command framing. No random/live credential was generated or
registered. Development byte/count budgets and remaining transport/semantic checks are explicit.
Original approved AU-01–10 bytes were compared unchanged after adding approval provenance. Existing
core source, the child's seven pinned inputs and delegated task were not modified by this work.

Next implementation binding requires the returned/checked CORE-003 API plus a separate named auth
execution scope; Git/credential/client integration also needs qualified publisher/runtime boundaries.
No auth implementation, current service credential, network listener, independent review, public
tracking, commit or push is established by these readiness checks.
