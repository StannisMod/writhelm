# Synthetic H1 test inputs

These bounded JSON files are source-owned shape/codec fixtures, not private instance data, real
project material, session exports or evidence that a workflow executed. UUIDs, source OIDs, native
profiles and content are synthetic. Canonical field/byte criteria remain in the tests.

- `frozen`: original model1 composition commands, independent fingerprints and journal schema.
- `work`: original work record shapes and independent key/fingerprint vectors.
- `auth`: synthetic credential/command vectors; no operator secrets.
- `body`: exact valid checkpoint command with a 70,000-byte synthetic `x` report.
- `composed`: model2 replacement/coordination commands, records, effects and closed schemas.

Portable tests use these committed inputs by default. Existing `WRITHELM_*_READY_ROOT` and body-probe
variables may explicitly select another identified input packet; they are not required local paths.
Disposable store/app directories default to fresh platform-selected JVM temporary directories outside
the source tree and may be explicitly overridden by `WRITHELM_STORE_TEST_ROOT` /
`WRITHELM_APP_TEST_ROOT`. Synthetic API output also defaults to a fresh temporary directory;
`API_EVIDENCE_ROOT` may explicitly override it. Gradle prints the selected default artifact paths.
Runtime outputs are never committed. Existing `build/test-runtime` evidence is not deleted or migrated
automatically. Keeping large generated Git trees outside the project avoids consuming the attached
development client's recursive filesystem watches.

From `backend/server`, with a qualified JDK 21, run `bash ./gradlew test` on Linux, or the corresponding
Gradle wrapper invocation on another platform. Running these tests does not qualify that platform's
service adapters. No source default selects a developer's home, shared cache or private artifact root.

The separate `nativeComponentTest` task requires a built adapter and explicit `WRITHELM_NODE` executable
and `WRITHELM_NATIVE_ADAPTER` adapter-directory paths. Its selections distinguish model-free
JVM/HTTP/Node/DOM fixtures from actual pinned-native journeys; neither establishes Desktop GUI,
physical writer-fencing or live-model qualification.
Do not silently treat its exclusion from the portable `test` task as that native gate passing.

## Installed Linux CLI/TUI acceptance adapters

The installed automatic reviewer journey also places a real `project-check-v1` Node test in the
canonical mandatory check plan. It runs the tracked `tests/candidate.test.cjs` from an exclusive
candidate worktree through NativeProjectChecks/native shell with real service permits and retained
receipts. Separate adapter probes cover passing/failing tests, dirty source preservation, clean env,
lost native job replies, exact replay and unknown/timeout holds; those extra probes have explicitly
test-only authorization. Qualification/start/source/report limitations below remain unchanged.

`automaticReviewerUsesInstalledNativePreparationWithoutModelCalls` additionally exercises the
automatic coordinator with the pinned isolated native2.2.5 service. Its reviewer reservation clears
fixture retainedSnapshots/verifiedPackets/submittedDiffs/knowledgeSources/approvedDecisions; production
input observers reconstruct exact candidate/result/base Git facts over authenticated native transport
and verify a mandatory pinned knowledge input against the canonical store. Native restart reconstructs
input proof, while revoked reservation holds. Current environment/native observations and later
start/source/report remain synthetic, and the initial-start port cuts before any model request. This is declared-input
component evidence, not live qualification, read-only isolation or ordinary task-text client acceptance.

The selected native-component method
`NativeWorkerPreparationJourneyTest.authenticatedReviewIssuancePacketReportRestartAndRevocationWithoutModelCalls`
is a distinct cross-language service integration, not an installed model review. It uses actual
H1/store/private HTTP/authority, registry/issuer/custody, real Git assembly/checks and canonical report
publication. Its Node receipt fixture uses real scoped service permits when given the protected
service configuration. Current environment/native, passive/start/source observations and report prose
are explicitly synthetic; no model calls or production qualification. It verifies positive ticket
issuance, mandatory packet, ticket/report restart, missing-current-process/receipt holds and real authority
revocation. Private configuration/evidence files contain test credentials and must never be published.
It also runs the real JavaScript registry and candidate delivery loop against that private service:
automatic canonical report discovery, exact replay without another check execution, partial/ambiguous
report holds and a revoked-permission loop hold. The pre-native-dispatch cut and native observations
are synthetic, not a real reviewer model execution. `review-recorded` does not authorize integration
or establish semantic correctness. Missing canonical reviewer assignment remains a visible wait,
not an automatically fabricated reviewer identity.

EX-09 regressions use project tests, not per-assignment qualification campaigns. Portable admission
tests cover preparation/binding of an unqualified profile, exact current-command execution proofs,
process/configuration/native restriction loss and continued retention admission without a catalogue.
The native initial adapter tests use actual private custody/HTTP transport and explicit mocked
native SDK behavior; no inference is performed. Resume and new-generation initial start are explicitly
unsupported by that adapter and stay held without preventing independent preparation.

The automatic native journeys additionally require `WRITHELM_NATIVE_CLI`, `WRITHELM_NATIVE_TEST_ROOT`
and a built JavaScript adapter. Use an explicitly private Linux test root with safe ancestors and
the pinned native executable; no user-wide login/configuration is imported. The unified launcher
journey depends on `installDist` and runs the source-owned Python helpers below with Python 3 on
Linux. Python/PTY success is not cross-platform operational qualification.

- `automatic-ui-client.py ROOT`: invokes only the installed `ROOT/client/installation/writhelm`,
  opens a fresh folder, sends ordinary New session/exit keys and reads native state. It submits no
  prompts and does not create canonical service/controller fixtures. By default it explicitly calls
  `--stop-service` after its disposable UI checks; closing the view itself must leave the service
  alive. `--attached` leaves the service running for another acceptance participant.
- `parallel-ui-client.py ROOT`: starts two installed clients concurrently in one folder, requires
  four distinct chat/controller bindings, checks the second view remains alive after the first exits
  with the same background/native processes, then explicitly stops its disposable service. No
  manual bootstrap or task/model submission is involved.

Helpers write private bounded terminal captures and JSON receipts under their explicit test root;
these are runtime evidence, never source fixtures or publication inputs. Unknown lifecycle markers
or uncertain original effects are not deleted/reset by recovery. The built user wrapper accepts a
project directory for ordinary use and `--stop-service` for normal shutdown of its authenticated,
ready, exact owned service. This is Linux normal lifecycle only; abnormal writer fencing remains
a separate held gate. Full worker/result/candidate/check/independent-review acceptance is not
established by empty-history session tests.
