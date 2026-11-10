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

## Cross-platform contract documentation scope P6

The owner approved the six-point K-RUN revision 2 and explicitly requested project-wide cross-platform
principles and contract impact assessment. Admit the single newly authored documentation path
`docs/contracts/launch-recovery.md` for this canonical public-safe contract; allow its exact ignore
exception. Existing README/AGENTS/architecture/specification/acceptance and codec/auth/publication/API
contract paths may record that scoped decision, clarifications and remaining qualification gates.
No runtime/source export, private evidence, platform qualification, commit or push is authorized by
this documentation scope. Historical task/test reports retain their exact assessed platform scope.

## Client setup contract documentation scope P7

The owner approved the six-point K-SETUP revision 1. Admit the single newly authored canonical
documentation path `docs/contracts/client-setup.md` and its exact ignore exception to record that
public-safe design. Existing API/task references may link the accepted setup decision. This scope
does not admit implementation paths, secret files, private task/evidence archives or generated keys,
and does not authorize a commit or push. Implementation/testing/qualification remain distinct.

## OpenCode checkpoint documentation scope P8

The owner approved K-OC-CHECK revision 1. Admit only newly authored
`docs/contracts/opencode-qualification.md` and its exact ignore exception for this public-safe
checkpoint contract. Raw GUI/host evidence, sessions, screenshots, synthetic secret files, SDK/cache/
display packages and private execution tasks remain excluded. No plugin/source publication, commit,
push or installation into the owner's live profile is authorized by this documentation scope.

## H1 boundary documentation scope P9

The owner approved the disclosed H1 boundary and service-control/native-execution split. Admit only
newly authored `docs/contracts/agent-workflow.md` and its exact ignore exception for that canonical
public-safe decision. Existing roadmap may reference its scoped approval. This does not admit future
task/runtime source, private design/execution workspaces or raw worker/session evidence, and does
not authorize commit/push, live-model experiments or automatic installation into the owner's harness.

## Task contract documentation scope P10

The owner approved K-TASK revision 1 with the explicit planner exact-result acceptance clarification.
Admit only newly authored `docs/contracts/task-contract.md` and its exact ignore exception to record
that public-safe scope. Existing H1 contract may link it. No task implementation paths, new capability/
namespace migration, private evidence/session material, model call or commit/push is authorized by
this documentation record. Component implementation/verification and publication remain distinct.

## Dispatch contract documentation scope P11

The owner approved K-DISPATCH revision 2 with its native-first clarification. Admit only newly
authored `docs/contracts/dispatch.md` and its exact ignore exception; existing H1 contract may link
the decision. This public-safe record does not admit task/adapter implementation paths, private
research/evidence/session material or runtime configuration. No commit/push/live call or standalone
M1/native checkpoint follows from this documentation scope.

## Retrospective/context documentation scope P12

The owner explicitly approved the separately named K-RETRO revision 1 and K-CONTEXT revision 2.
Admit only newly authored `docs/contracts/retrospective.md`, `docs/contracts/context-continuity.md`
and their exact ignore exceptions for canonical public-safe decisions; existing H1 contract may
reference them. No private source SOP, incident, session, raw evidence, extraction workspace, new
implementation path, runtime/live-call authorization or commit/push is admitted by this record.

## Practical H1 amendments and execution documentation scope P13

Owner approved the disclosed per-contract amendment table on 2026-11-05. Existing H1/task/dispatch/
context/specification/acceptance/roadmap paths may record their scoped changes; K-RETRO1 unchanged.
Admit only new `docs/contracts/execution.md` and its exact ignore exception for approved E-COOP-1/
worktree/read-only-review/integration scope. No implementation, private prior revisions/evidence,
raw sessions/runtime config, commit/push/live experiment or host install authorized. Old evidence
keeps its original assessed scope.

## Evidence/integration contract documentation scope P14

Owner approved K-EVIDENCE revision 2 including explicit contract/condition/test/executed-result
traceability. Admit only new `docs/contracts/evidence-integration.md` and its exact ignore exception;
existing H1 contract may link it. This public-safe decision doesn't admit private source SOPs, raw
reports/logs/sessions, implementation/runtime profiles or fixtures, and doesn't authorize commit/
push, host changes, model calls or another native/M1 campaign.

## H1 work-authority documentation scope P15

The owner agreed to K-WORK-AUTH revision 1 after explicit planner-permission clarification. Admit
only new `docs/contracts/work-authority.md` and its exact ignore exception; existing auth/H1 records
may reference that scoped extension. No private key/profile/session/research/evidence, implementation
path, capability migration, live/native test, host install or commit/push is authorized by this record.

## H1 work-state documentation scope P16

Owner approved K-WORK-STATE revision 1. Admit only new `docs/contracts/work-state.md` and its exact
ignore exception; existing H1 documentation may reference the decision. No concrete future schema/
implementation paths, private artifact/session/evidence, live keys, migration/native run, host install,
commit/push or broad public export is authorized by this documentation record.

## Local checkpoint of approved contracts and existing source paths P17

The owner subsequently requested a commit of the current working state and a plan before further
implementation. This authorizes local commit of the already admitted P6–P16 documentation paths and
existing tracked README/AGENTS/architecture/specification/acceptance/API/task/publication/ignore-rule
updates, plus current changes to the already admitted server build, AppState, MechanismApp and
GitCommandStore paths. The connected-work amendment is recorded in the existing admitted H1/task/
dispatch/context/evidence contracts; the roadmap records proposed next work, not its execution.

This checkpoint does not import the isolated H1 implementation, adapter sources, private worktrees,
readiness catalogues, handoffs, sessions, logs, generated bundles, fixture archives or local paths.
The accepted portable candidate requires a separate exact source-path/content review and target
integration checkpoint before tracking; this commit is not that integration. No push, working-profile
installation, live-model or pilot permission follows from the local commit instruction.

## Portable H1 source integration scope P18

The owner approved autonomous execution of the completion plan, including exact source/content review,
target integration and a separate local commit. Admit only the individually enumerated file exceptions
under the `P18: exact reviewed portable H1 source paths` section of `.gitignore`. Those 121 explicit
paths extend the earlier manifest; they are not directory globs or permission for future files.
Previously admitted source/build paths may receive the corresponding accepted H1 implementation.
The following P18 synthetic-prerequisite section admits its 47 named JSON fixtures and README only.
These are bounded code/schema examples and mathematical vectors, not copies of readiness reports,
task/session histories, run evidence or instance stores. The server test task may default to these
in-repository fixtures while retaining explicit input overrides; native component execution has its
own prerequisite-checked task, not a silent passing native gate in the portable selection.

Provenance: Writhelm-owned AGPL-3.0-only portable work/auth/publication/application and OpenCode adapter
source, plus synthetic tests and structural schemas. Preserve upstream MIT notices and imported wrapper
bytes. The four individually named `src/generated` validators/declarations are code generated from the
selected synthetic service/profile schemas for this adapter, not an export of runtime state or a
bundled dependency tree. Package manifests/lock and build scripts identify the original dependency
versions; qualification of a distributed dependency bundle remains a release gate.

Content review selects exact source bytes and synthetic records, not worker histories or local artifact
directories. No private filesystem paths, live keys, raw sessions/reports, test repositories, logs,
cache/toolchain/dependency directories or compiled GUI `dist` bundle are admitted. The missing previous
GUI build is not silently represented as recovered or tested; a new build requires its own evidence.
Source identity recovery is not a passing behavioral test. Target checks and exact integration state
are recorded separately. Real native custody/fencing, other platforms and the full H1 pilot remain
unqualified. This local tracking/integration decision does not authorize a public push or live profile.

## Native bridge candidate scope P19 — local commit authorized

The owner requested committing the checked native bridge candidate and continuing implementation.
Admit only the 24 individually enumerated exceptions in `.gitignore`'s P19 section, alongside the
candidate's changes to ten already admitted source/test/build paths and this publication/ignore
record. This is authorization for a local source checkpoint, not independent review, acceptance,
target integration, a public push or installation into the working harness.

Provenance: newly authored Writhelm AGPL-3.0-only service/native adapter code and synthetic tests.
The scope includes private-transport implementations, protected operational custody and scoped
credential resolution, exact assignment-packet rendering, explicit fresh H1 setup and composition.
Tests include actual local HTTP/Git/application boundaries with explicitly mocked external native
effects. Preserve upstream notices; no dependency binaries or upstream implementation are added.

Private candidates, task handoffs, manifests, review drafts, logs/XML, runtime stores, generated
credentials/proofs, compiled output, toolchains and session history remain excluded. The checked
source is not a qualified installed service: full task-shaped context/compaction, physical effects,
abnormal recovery, other-platform qualification and the remaining pilot/release gates stay open.

## Task-context and workflow presentation scope P20 — local commit authorized

The owner explicitly requested committing the completed context and workflow presentation work.
Admit only the 23 individually enumerated exceptions in `.gitignore`'s P20 section, corresponding
changes to already admitted adapter/service/tests/build paths, and this publication/ignore record.
This is a local source checkpoint, not independent acceptance, target integration, public push or
permission to install a working profile or run live models.

Provenance: newly authored Writhelm AGPL-3.0-only task-shaped source/checkpoint/compaction/restoration
and read-only native workflow presentation code, with deterministic synthetic test inputs. Existing
upstream notices remain unchanged. Tests distinguish actual checked service/Git/HTTP boundaries
from mocked native/provider effects and DOM fixtures. No dependency implementation is imported.

Source/content review excludes private candidates, tasks/checkpoints/manifests, session histories,
logs/XML, instance stores, credentials, machine paths, toolchains, dependencies and compiled GUI
bundles. Author checks do not establish installed native ordering, provider accounting/cancellation,
physical writer/source safety, abnormal recovery, Windows/macOS support or an operational H1 pilot.
Independent review/acceptance, combined native qualification and release/license gates remain open.

## Governed primary and bounded worker lifecycle scope P21 — local commit authorized

The owner requested a local commit and continued implementation. Admit only the 23 exact P21
exceptions in `.gitignore`, corresponding changes to already admitted source/test/build paths,
and this publication/ignore record. This does not authorize a push, live inference, stand restart
or installation into the working harness.

Provenance: newly authored Writhelm AGPL-3.0-only primary admission/policy, passive Git/native
preparation, scoped enrollment, distinct initial-start protocol and synthetic tests. The context
coordinators also separate dispatcher execution from scoped hook registration and filter sessions.
Tests distinguish real local service/Git/HTTP checks from mocked OpenCode/provider effects.
No external dependency implementation or binary is imported; preserve existing notices.

Private tasks, reports, session histories, stand configuration/stores, keys/proofs, machine paths,
logs/XML, generated output and dependencies remain excluded. Author checks do not establish
installed lifecycle ordering, provider accounting/cancellation, physical source/writer safety,
abnormal recovery, other-platform qualification or complete H1 operation. Effect-native runtime
composition and remaining pilot/release gates are not represented as complete by this checkpoint.

## Effect-native context composition scope P22 — local source checkpoint

Under the owner's instruction to commit and continue implementation, admit the three exact P22
source exceptions in `.gitignore`, accompanying changes to admitted adapter sources/tests/package
exports/README and this publication/ignore record. Newly authored AGPL-3.0-only code composes the
existing approved task-context protocols in the native Effect lifetime; synthetic tests do not call
a provider. Real service/Git/HTTP component fixtures retain explicit mocked native/profile effects.

No private task/evidence/session/stand material, generated output, credentials, dependency source
or binary is admitted. This source checkpoint does not establish installed module sharing/hook
ordering, request cancellation/accounting, source/writer safety, abnormal recovery, Windows/macOS
qualification or full H1 operation. No push, live model call or stand restart is authorized here.

## Read-only native policy guard scope P23 — local source checkpoint

Admit the two exact P23 exceptions in `.gitignore` and accompanying changes to admitted adapter
sources/tests/README and this publication/ignore record under the owner's continued implementation
instruction. Newly authored AGPL-3.0-only code conservatively checks normalized OpenCode config
DTOs against a protected selected foreground policy. Tests use synthetic normalized config/client
fixtures. No external implementation, distribution bytes or source-review archive is imported.

Private static distribution observations, tasks/reports, session material, actual configuration,
credentials, artifacts/logs and compiled output remain excluded. This checker only adds refusal:
it does not create nativeReady, profile qualification, source/writer safety, request-budget proof,
historical N-case acceptance or installed-host evidence. No live/model call, stand restart or push.

## Exact-text correction kernel scope P24 — local source checkpoint

Following the owner's approved deterministic non-LLM correction direction, admit only the two exact
P24 exceptions in `.gitignore`, corresponding changes to admitted primary instructions/tests/README
and this publication/ignore record. Newly authored AGPL-3.0-only pure text preparation and synthetic tests; no upstream source
or dependencies are imported. The kernel has no filesystem, process, session, model or service effect.

Private design/task/evidence/session material, actual project patches, source inventories, artifacts
and generated output remain excluded. This is not an operational apply tool, contributor/authority
schema, filesystem containment, independent verification or full correction-workflow qualification.
No public push, live inference, stand launch or user-wide configuration change follows.

## Mechanical descriptors and authorship scope P25 — local source checkpoint

The owner explicitly approved a separate mechanical execution kind without LLM profile/session,
explicit authors and continued legacy readability. Admit the four exact P25 exceptions in
`.gitignore`, corresponding admitted contract/status changes and this publication/ignore record.
Newly authored AGPL-3.0-only bounded profile/producer/result descriptor codecs, contributor-closure
predicates and synthetic tests; no external source, binaries, private fixtures or histories.

This is staged format/read compatibility work, not a newly enabled store/writer profile, command,
native apply tool or authority grant. New record2 shapes are not silently admitted by legacy
record1/command3. Mechanical result descriptors require explicit patch/source/author attribution;
legacy producer identity is not represented as complete contributor knowledge. Required runtime,
whole-state/history, rights/claims/effect, review/acceptance and per-platform gates remain separate.
Private designs/tasks/evidence, logs, stores and generated outputs stay excluded. No push/live call,
stand restart, migration/history rewrite or operational qualification follows from this checkpoint.

## Exact-file native primitive scope P26 — local source checkpoint

The owner's instruction to continue the approved deterministic applicator extends the two already
admitted paths `adapters/opencode/src/exact-text-correction.ts` and its existing test file, with
corresponding README/status updates. No new publication path or imported code is introduced.
Newly authored AGPL-3.0-only Linux exact-file application/read-only observation primitive and
synthetic temporary-filesystem tests; no actual patches, project files, paths, keys or run evidence.

The pure preparation export remains effect-free. The new primitive is not wired to an admission
route/tool and supplies no authority itself. Retention/admission ports are unconnected; component
tests mock them explicitly. Pending effects, non-atomic writes, cooperative path checks and held
Windows/macOS qualification are explicit. Service state/history/authorship/claims, permitted check
execution and operational tool integration remain unfinished. No push, install, stand restart,
live model call or process-fault qualification follows from this local source checkpoint.

## Candidate profile graph scope P27 — local source checkpoint

Extend existing admitted work codec/state/registry/query/reducer, execution descriptor and descriptor
test paths for explicit candidate model3 profile restoration in the same bounded graph. No new path,
external source, private fixtures or generated output is introduced. Legacy model2 bytes/readers stay
unchanged; the legacy command3 reducer/registration and read2 codecs do not become model3 writers.
Mechanical profiles cannot masquerade as native assignment/binding/controller profiles. Record2
result/service-operation admission and new store/journal/service selection remain held, not silently enabled.

## Mechanical assignment/effect graph scope P28 — local source checkpoint

Extend the existing execution-descriptor, work codec/state/registry/query and descriptor-test paths
with closed candidate record2 mechanical assignment, immutable binding and retained original-operation
shapes. Newly authored source and synthetic graph fixtures only; no new public path, external code,
actual patch/identity/witness, native store or private run artifact. Shared claims, source-result author
closure, cross-kind workspace exclusion and immutable original-effect edges are structural checks.
Stored writerCompleted metadata is not verified physical termination; no command4/read3/journal4,
store writer, authenticated admission, native port connection or primary tool is enabled here.

## Mechanical result graph scope P29 — local source checkpoint

Extend already admitted mechanical descriptor/codec/state/query/model2-semantics and synthetic test paths to decode
record2 results under candidate model3 and validate their exact assignment, binding, retained applied
operation, patch, declared authors, retrospective and criteria. A consistent graph is not proof of
physical effects or authenticated current authority. Candidate graph review bindings, reports and
acceptance checks exclude declared contributors, but actual review/acceptance admission remains
explicitly held until contributor exclusion is enforced through every relevant path. Candidate
acceptance conservatively requires the same exact task and verification plan for a mechanical
result's report; cross-task grouped coverage remains held pending explicit applicability proof. Legacy formats,
store writer, service operation and current access selection remain unchanged. No new public path,
external source, real patch, fixture, credential or local history is introduced.
