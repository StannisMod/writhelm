# Publication policy

## Default deny

Repository publication is an explicit allowlist, not a copy or synchronization of existing projects.
Permission to inspect or use material locally does not imply permission to publish it.

Initial publication manifest:

```text
.gitignore
README.md
AGENTS.md
LICENSE
docs/architecture.md
docs/publication.md
docs/roadmap.md
```

These documents are newly prepared public project descriptions and generic architecture/process rules.
No implementation, evaluation archive, private project export or historical session material is included.
The manifest also includes the unmodified GNU AGPLv3 license text from the Free Software Foundation.

## Never import by default

- Other projects' source, requirements, designs, examples, datasets or proprietary terminology.
- Chat/session transcripts, checkpoints, task histories, local reports, logs or temporary workspaces.
- Personal filesystem paths, machine identities, credentials, configuration exports or account metadata.
- Real fixtures, screenshots, attachments, generated artifacts, databases, embeddings or backups.
- Skills and prompts copied from another workspace without explicit review and approval.

Architecture, skills, SOPs and implementation can be added only through a scoped publication decision:
identify exact files, provenance, sanitization, rights/license and intended public purpose. Derived
examples must be genuinely synthetic, not renamed versions of confidential data.

## Before each push

1. Check the exact tracked file list against the approved publication manifest.
2. Inspect the complete staged diff and any new history, not just the working tree.
3. Check for private identifiers, local paths, secrets, embedded artifacts and external content rights.
4. Stage named files only; do not force-add ignored files to bypass the boundary.
5. Publish only reviewed material. Automated checks help but do not prove the absence of private content.

The root ignore rules reinforce this allowlist but are not a security boundary: tracked files remain
tracked, force-add can bypass ignores, and later changes still require review. No local evidence
archive or private checkout is ever configured as the public repository's source directory.

## Upstream import I1

Status: owner approved the selected source import with the `backend/` layout after choosing a fork.
The paths below extend the publication manifest for this pinned snapshot, subject to the review
gates below. Approval to import is not evidence of a passing build or approval of first-slice behavior.

### Source and destination

Use only the public repository `https://github.com/jpicklyk/task-orchestrator` at commit
`d9efd9f1546aa3020b54467366a215d378e8bbb3`. Map the selected upstream paths below to
`backend/<upstream-path>`. This is the initial development location of the fork, not a second service
or a separately updated vendor dependency. Preserve Kotlin package names initially to avoid a
mechanical rename obscuring functional changes.

The complete upstream tree contains 1,759 files. The following deterministic selection contains
1,382 files (16,970,971 bytes); 377 files are excluded. The counts describe the pinned tree, not a
floating glob against a later upstream version.

```text
current/**
gradle/**
LICENSE
README.md
CONTRIBUTING.md
CHANGELOG.md
.editorconfig
.gitattributes
.dockerignore
build.gradle.kts
settings.gradle.kts
gradle.properties
gradlew
gradlew.bat
version.properties
Dockerfile
docker-compose.yml
server.json
smithery.yaml
.taskorchestrator/config.yaml
deploy/global-config/.taskorchestrator/config.yaml
.claude/skills/implement/SKILL.md
claude-plugins/task-orchestrator/skills/orchestrate/SKILL.md
claude-plugins/task-orchestrator/_fragments/tier-classification.md
claude-plugins/task-orchestrator/_fragments/tier-classification.consumers.txt
claude-plugins/task-orchestrator/_fragments/generate.mjs
```

Selection fingerprint: sort selected file records by upstream path, then concatenate UTF-8 lines
`<git-mode> <git-blob-id> <upstream-path>\n`. SHA-256 of that manifest is
`68f9c8d56e1aa1d95dc147c38d0658d7120df9cf9f95e867149fcd6dcf84025b`.
This identifies the proposed bytes and executable modes; it is not a completed content/license audit.

`current/**` includes production sources, migrations, tests, resources and upstream API documentation.
The pinned tree has no symlinks or submodules. Upstream's version catalog and build scripts are
retained for baseline reproduction, not an assertion that all dependencies have been qualified.

### Why the extra files are selected

Gradle configures test inputs from the tier-classification consumer manifest even before tests run.
That manifest names the two selected skill files. Tests also read the distribution manifests and
both selected config files directly. Retain these inputs rather than deleting tests to make a
smaller import appear green. The fragment generator is included for reproducible maintenance.

These upstream skills are inert compatibility fixtures, not adopted Writhelm SOPs. Do not install
the plugin, run the skills, sync its config into a service, or register its distribution manifests.
The selected configs belong to upstream, not to any owner's private project. Their continued
inclusion depends on content review; test dependencies do not override publication policy.

### Exclusions and history

Exclude every unselected path, including `.idea/`, `.github/`, `.claude-plugin/`, remaining `.claude/`
and `.taskorchestrator/` content, most plugin files, upstream site/archive documentation and scripts.
Do not import private evaluation checkouts, reports, sessions, local fixtures or harness settings.

Preserve upstream Git history locally through a separately fetched `upstream` remote and record
the source commit in import provenance. Do not merge upstream history into public `main`, push
upstream refs/tags or use a mirror push as part of I1. The initial public import is a selected source
snapshot atop Writhelm's existing clean history. Later updates can be reviewed against the pinned
baseline and applied selectively; this avoids publishing excluded files through old commits.

### Notices and Writhelm-owned changes

Keep the upstream MIT license verbatim at `backend/LICENSE` and preserve embedded notices.
The root AGPL license must not overwrite it. Update the root README to distinguish MIT-derived
source from Writhelm additions and link provenance. The approved additional path
`docs/upstream.md` will record source URL, baseline, selection fingerprint, licensing boundary and
any import modifications. It will contain newly written public provenance only.

Keep the source snapshot byte-identical initially. In a separate reviewed change, adapt image,
registry and runtime branding before any Writhelm distribution; imported upstream identifiers do
not authorize publishing under upstream names. Review bundled dependency notices before shipping
a fat JAR or container; the root license alone is not a dependency compliance inventory.

### Import gates and permitted next work

Review the selected content and rights, expand the allowlist to the exact
selected paths and add the provenance file. Never broadly unignore all of `backend/` without an
exact-path manifest check. Check imported bytes and modes against the pinned Git tree, inspect the
complete staged diff and publish only after the review passes. Do not run upstream automation on
import or enable CI/release workflows implicitly.

Two binary inputs need specific qualification: `gradle/wrapper/gradle-wrapper.jar` (verify against
the appropriate official Gradle checksum) and
`current/src/test/resources/upgrade/golden-v17-3.16.sqlite` (inspect contents and trace the upstream
fixture generator to establish that it contains synthetic data). Any private data, unexplained
artifact or incompatible rights stops import and requires an amended proposal, not a silent omission.

Then reproduce the unchanged baseline with JDK 21 and the pinned Gradle wrapper: run
`:current:test` (including its serial-test finalizer), `:current:ktlintCheck` and `:current:jar`
from `backend/`. Dependency/toolchain downloads are distinct from model calls; use isolated local
build storage. Record failures honestly, do not weaken tests, and qualify dependency licenses
before declaring the baseline ready. No live models, embeddings or paid experiments are authorized.

Implementation of the proposed first-slice behavior still requires separate design approval.

## Specification/contract/task scope P2 — local tracking and commit authorized

Status: the owner authorized committing the completed work on 2026-11-03. The exact paths below
are approved for local tracking/commit after content/diff review; `.gitignore` reflects this scope.
This does not ratify every surrounding draft requirement/design or authorize a public push. Raw
scratch evidence, sessions, prototypes and generated outputs remain excluded. Publication to the
remote still needs an explicit push instruction and the normal boundary review.

```text
docs/specification.md
docs/acceptance.md
docs/workflow.md
docs/tasks/SPEC-001.md
docs/contracts/accepted-knowledge.md
docs/tasks/DESIGN-001.md
docs/design/foundation.md
docs/tasks/DESIGN-002.md
docs/design/knowledge-model.md
docs/tasks/DESIGN-003.md
docs/design/publication-recovery.md
docs/design/git-state.md
docs/tasks/DESIGN-004.md
docs/design/first-slice.md
docs/tasks/DESIGN-005.md
docs/design/approval-interface.md
docs/contracts/knowledge-content.md
docs/tasks/CORE-001.md
docs/contracts/knowledge-workflow.md
docs/tasks/CORE-002.md
```

Purpose: freshly authored public product requirements, synthetic acceptance scenarios, the temporary
self-application procedure, canonical preparation/design tasks, an initial contract and a
public-source-grounded foundation comparison, own-core knowledge representation and publication /
recovery designs, the bounded first-slice record/authority/verification design, and the structured
approval completeness/capability design. Synthetic scratch prototypes and raw local harness evidence
are excluded from this proposed publication scope.

## Own-core source scope P3 — local tracking and commit authorized

Status: the owner's 2026-11-03 commit instruction authorizes local tracking/commit of the exact
completed own-core paths below, not a public push or independent implementation acceptance. This is newly authored
Writhelm AGPL-3.0-only code, not an upstream source extraction or an export of private project code.
Track only the named source/build files after full review. Preserve the imported baseline; keep
build artifacts, caches, evidence and unlisted future files ignored. No force-add or broad source export.

Exact proposed source/build paths:

```text
backend/server/settings.gradle.kts
backend/server/build.gradle.kts
backend/server/gradle.properties
backend/server/src/main/kotlin/dev/writhelm/core/knowledge/Content.kt
backend/server/src/main/kotlin/dev/writhelm/core/knowledge/StructureIndex.kt
backend/server/src/main/kotlin/dev/writhelm/core/knowledge/References.kt
backend/server/src/main/kotlin/dev/writhelm/core/knowledge/DocumentRevision.kt
backend/server/src/main/kotlin/dev/writhelm/core/knowledge/RevisionCatalog.kt
backend/server/src/main/kotlin/dev/writhelm/core/workflow/WorkflowRecords.kt
backend/server/src/main/kotlin/dev/writhelm/core/workflow/ReviewPacket.kt
backend/server/src/main/kotlin/dev/writhelm/core/workflow/ApprovalWorkflow.kt
backend/server/src/test/kotlin/dev/writhelm/core/knowledge/ContentTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/knowledge/StructureTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/knowledge/RevisionCatalogTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/workflow/WorkflowFixture.kt
backend/server/src/test/kotlin/dev/writhelm/core/workflow/ApprovalWorkflowTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/workflow/ContinuityManifestTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/workflow/WorkflowRecordsTest.kt
```

The independent small build reuses the already qualified wrapper/cache as development tooling;
no upstream implementation or fixture is copied into the new core. Dependency versions/tooling are
public inputs, not permission to distribute dependencies without notice review. Exclude build/JAR
outputs, Gradle/Kotlin caches, local test reports, machine paths, raw evidence and prototype artifacts.
Inspect the exact source diff/provenance and the complete staged diff before committing. Future new
paths need another scoped tracking/publication decision; this does not broadly allow `backend/server/**`.
These are not exported private project
documents, evaluation reports, session transcripts or historical task records. No external code is
included; Writhelm's AGPL-3.0-only terms apply. Review the full proposed bytes and provenance before
approval. Existing `docs/architecture.md` and `README.md` may record the confirmed own-core direction
and checkpoint; they must not imply acceptance or implementation of the new specification.

## Codec/contracts scope P4 — local commit authorized

The owner's subsequent commit instruction authorizes local tracking/commit of the exact completed
codec and freshly authored service-contract/readiness paths below, after boundary and complete diff
review. This does not authorize push, publication of raw evidence/prototypes/session history, future
worker output or blanket ratification of unaccepted transport design. New source is Writhelm-owned
AGPL-3.0-only; synthetic fixture keys are fixed public test vectors, never live service credentials.

```text
docs/contracts/state-codec.md
docs/contracts/command-publication.md
docs/contracts/authentication-access.md
docs/design/core-codec-readiness.md
docs/design/auth-service-readiness.md
docs/tasks/DESIGN-006.md
docs/tasks/DESIGN-007.md
docs/tasks/DESIGN-008.md
docs/tasks/CORE-003.md
docs/tasks/AUTH-001.md
docs/schemas/core-codec-v1.json
docs/schemas/core-codec-goldens-v1.json
docs/schemas/auth-service-v1.json
docs/schemas/auth-service-goldens-v1.json
backend/server/src/main/kotlin/dev/writhelm/core/codec/StrictJson.kt
backend/server/src/main/kotlin/dev/writhelm/core/codec/CoreRecordCodec.kt
backend/server/src/main/kotlin/dev/writhelm/core/codec/CommandCodec.kt
backend/server/src/main/kotlin/dev/writhelm/core/codec/CoreSnapshotCodec.kt
backend/server/src/main/kotlin/dev/writhelm/core/workflow/WorkflowSnapshot.kt
backend/server/src/main/kotlin/dev/writhelm/core/workflow/WorkflowSnapshotValidator.kt
backend/server/src/test/kotlin/dev/writhelm/core/codec/StrictJsonTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/codec/RecordCommandCodecTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/codec/SnapshotCodecTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/codec/CodecFixture.kt
```

Accompanying existing-path changes are limited to `.gitignore`, this publication record,
`backend/server/build.gradle.kts`, `RevisionCatalog.kt`, `ApprovalWorkflow.kt` and `WorkflowRecords.kt`
under their already named P3 paths. They add the pinned JSON dependency, internal snapshot access and
the accepted checked-generation refusal; original tests/settings/imported source remain unchanged.
The new direct dependency is referenced, not vendored or distributed. Artifact license/security
qualification remains open. Build output, caches, raw XML/logs and worker/reviewer scratch stay ignored.
Later reviewer/implementation tasks and worker source paths need their own scoped tracking decision.

## Auth/store/app scope P5 — local commit authorized

The owner's next commit instruction authorizes local tracking/commit of the exact completed paths
below and the listed status updates, after content/diff review. This is no push permission, no
approval of proposed K-API behavior, and no release/crash/whole-service qualification. All new code
is Writhelm-owned AGPL-3.0-only; test credentials are explicit fixed public synthetic vectors.

```text
docs/contracts/service-interface.md
docs/tasks/REVIEW-CORE-001.md
docs/tasks/AUTH-002.md
docs/tasks/STORE-001.md
docs/tasks/STORE-001-result.md
docs/tasks/STORE-002.md
docs/tasks/APP-001.md
docs/tasks/DESIGN-009.md
backend/server/src/main/kotlin/dev/writhelm/core/auth/Credentials.kt
backend/server/src/main/kotlin/dev/writhelm/core/auth/AuthCodec.kt
backend/server/src/main/kotlin/dev/writhelm/core/auth/Authority.kt
backend/server/src/main/kotlin/dev/writhelm/core/store/AdmissionPolicy.kt
backend/server/src/main/kotlin/dev/writhelm/core/store/StoreTypes.kt
backend/server/src/main/kotlin/dev/writhelm/core/store/JournalCodec.kt
backend/server/src/main/kotlin/dev/writhelm/core/store/NativeGit.kt
backend/server/src/main/kotlin/dev/writhelm/core/store/GitCommandStore.kt
backend/server/src/main/kotlin/dev/writhelm/core/app/AppState.kt
backend/server/src/main/kotlin/dev/writhelm/core/app/AppReduction.kt
backend/server/src/main/kotlin/dev/writhelm/core/app/MechanismApp.kt
backend/server/src/test/kotlin/dev/writhelm/core/auth/AuthTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/auth/AuthBoundaryTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/store/JournalContractTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/store/StoreContractTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/store/EffectContractTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/store/PublicationSafetyTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/store/IntegrityContractTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/store/PolicyContractTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/store/StoreTestRoot.kt
backend/server/src/test/kotlin/dev/writhelm/core/store/HistoryCapacityTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/app/AppFixture.kt
backend/server/src/test/kotlin/dev/writhelm/core/app/AppContractTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/app/AuthorityBindingTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/app/CurrentHeadBindingTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/app/KnowledgeJourneyTest.kt
backend/server/src/test/kotlin/dev/writhelm/core/app/NamespaceBindingTest.kt
```

Accompanying existing-path changes are only `.gitignore`, this record, `README.md`, the status /
evidence appendices in `docs/contracts/{state-codec,command-publication,authentication-access}.md`
and `docs/tasks/CORE-003.md`. Accepted CD-01–08/PB-01–09/AU-01–10 laws remain unchanged.
Original core/import/build/dependency/settings/test bytes remain unchanged. Raw logs/XML, worker/
reviewer evidence, private Git test repositories and handoff/session context are NOT publication
inputs. The requested Markdown context handoff is prepared outside public source Git and is not
included in this commit. No broad directory wildcard, future task or generated artifact is admitted.
