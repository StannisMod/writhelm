# Publication policy

## Default deny

Repository publication is an explicit allowlist, not a copy or synchronization of existing projects.
Permission to inspect or use material locally does not imply permission to publish it.

Initial publication manifest:

```text
.gitignore
README.md
AGENTS.md
docs/architecture.md
docs/publication.md
docs/roadmap.md
```

These documents are newly prepared public project descriptions and generic architecture/process rules.
No implementation, evaluation archive, private project export or historical session material is included.

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
