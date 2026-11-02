# Upstream provenance

## Initial source import I1

- Project: [Task Orchestrator](https://github.com/jpicklyk/task-orchestrator).
- Source commit: `d9efd9f1546aa3020b54467366a215d378e8bbb3`.
- Destination: `backend/`; upstream `current/` remains `backend/current/`.
- Scope: 1,382 selected files, 16,970,971 bytes. See the exact selection rules and
  exclusions in [publication policy](publication.md#upstream-import-i1).
- Selection fingerprint (SHA-256):
  `68f9c8d56e1aa1d95dc147c38d0658d7120df9cf9f95e867149fcd6dcf84025b`.
- Source changes on import: none. Bytes and executable modes were checked against
  the pinned upstream Git blob identities, not just an archive download. The archive
  exports different line endings for `gradlew.bat`; the canonical Git blob was used.
- History: selected snapshot only, not a merge of upstream history into public main.
  The source history is available locally as `upstream/baseline`; no upstream refs
  or tags were pushed to Writhelm's public repository.

The imported source is the starting point of the actively developed Writhelm backend,
not a separate upstream service behind a gateway. Native harness integrations will
live under `adapters/<harness>/`; none is implemented by this import.

## Licensing and distribution

Task Orchestrator is MIT-licensed, copyright (c) 2025 Jeff Picklyk. Its original license
is preserved verbatim at [backend/LICENSE](../backend/LICENSE); embedded notices are
retained. Writhelm additions are AGPL-3.0-only under the root [LICENSE](../LICENSE).

Upstream README, configurations, skills and distribution manifests describe upstream,
not implemented Writhelm guarantees. Selected skills/configs are retained as baseline
test inputs, not installed or adopted as Writhelm policy. Upstream image/registry names
must be adapted before publishing a Writhelm distribution.

No dependencies or bundled JARs other than the upstream Gradle bootstrap wrapper are
imported as production binaries. Dependency license/notice qualification remains open,
including third-party artifacts bundled by the upstream fat-JAR task. This is not a
completed distribution compliance review.

## Binary input review

### Gradle bootstrap wrapper

`backend/gradle/wrapper/gradle-wrapper.jar` has SHA-256
`91a239400bb638f36a1795d8fdf7939d532cdc7d794d1119b7261aac158b1e60`, matching the
[official Gradle 7.5.1 wrapper checksum](https://downloads.gradle.org/distributions/gradle-7.5.1-wrapper.jar.sha256).
It is an older official bootstrap JAR; the pinned upstream properties request the
Gradle **8.11.1** distribution. It does not match the newer 8.11.1 wrapper JAR checksum.
The import preserves this upstream state rather than silently upgrading the wrapper.

### Synthetic migration database

`backend/current/src/test/resources/upgrade/golden-v17-3.16.sqlite` has SHA-256
`322da8fabc677d16b9fdd096711ba44e24f191386eecc6d3fdce7cb16e48a036`, matching its
upstream manifest. The upstream `GoldenV17Generator.kt` creates a fresh migrated
database from `BaselineDataset.kt` literals and normalizes Flyway history; it does not
import a user's database. All non-FTS application rows and migration-history rows were
inspected read-only and agree with those synthetic fixtures. SQLite integrity passed;
the freelist contains no pages. This is an upstream test fixture, not owner project data.
The upstream test suite, including its migration tests, subsequently passed.
Standalone fixture regeneration was not invoked.

## Verification state

- Verified: selected source blob hashes and modes, selection fingerprint, wrapper
  origin, database manifest/integrity and synthetic fixture provenance.
- Checked: selected text for owner-project identifiers, credential candidates,
  private-key markers and personal home-directory paths; no matches in this scan.
  Pattern scanning is not proof that all sensitive content is absent.
- Verified baseline on JDK 21 and Gradle 8.11.1: `:current:test`, its `:current:serialTest`
  finalizer, `:current:ktlintCheck` and `:current:jar` succeeded. XML reports contain
  8,367 tests total: 8,353 executed, 14 skipped, zero failures and zero errors
  (regular suite: 8,324 tests / 3 skipped; serial suite: 43 tests / 11 skipped).
  Skipped cases are not claimed as verified. The successful final run reused compiled
  classes, the JAR and lint results from the preceding unchanged-source run.
- Still open: dependency license/notice inventory and Writhelm-specific behavior.
  A passing upstream baseline does not implement the proposed first slice.
- Baseline attempt: the initial wrapper download timed out before compilation. A
  local Gradle 8.11.1 distribution was verified against the official SHA-256
  `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`, copied into
  isolated build storage, and the same unchanged-source command was restarted.
  That run was interrupted during tests; the final retry completed successfully.
- Publication scope: source snapshot and public documentation only. Built JARs,
  caches, test reports and local upstream history are excluded.

## Baseline reproduction

Use JDK 21, isolated build storage and the pinned wrapper from `backend/`. The upstream
Git mode for `gradlew` is non-executable, so invoke it with Bash without changing the
imported mode:

```sh
bash ./gradlew --no-daemon :current:test :current:ktlintCheck :current:jar
```

The test task includes its serial-test finalizer. Do not regenerate or weaken goldens
just to pass the baseline.
Build/dependency downloads do not authorize live models, embeddings or paid experiments.
