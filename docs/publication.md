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
