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
Disposable store/app directories default to the project's build directory and may be explicitly
overridden by `WRITHELM_STORE_TEST_ROOT` / `WRITHELM_APP_TEST_ROOT`. Synthetic API output defaults to
`build/test-runtime/evidence`; `API_EVIDENCE_ROOT` may explicitly override it. Runtime outputs are
never committed.

From `backend/server`, with a qualified JDK 21, run `bash ../gradlew test` on Linux, or the corresponding
Gradle wrapper invocation on another platform. Running these tests does not qualify that platform's
service adapters. No source default selects a developer's home, shared cache or private artifact root.

The separate `nativeComponentTest` task requires a built adapter and explicit `WRITHELM_NODE` executable
and `WRITHELM_NATIVE_ADAPTER` adapter-directory paths. It runs a model-free JVM/HTTP/Node/DOM fixture,
not an installed OpenCode Desktop, physical writer-fencing test, GUI walkthrough or live model call.
Do not silently treat its exclusion from the portable `test` task as that native gate passing.
