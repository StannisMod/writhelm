# Initial setup and client secrets

Contract K-SETUP, revision 2. Status: six decisions approved with native-harness component correction;
Linux helper/controller candidate implemented; native-host and other-platform qualification held.
Dependencies: [K-AUTH](authentication-access.md) AU-03/05/06/07/08,
[K-API revision 2](service-interface.md), [K-RUN revision 2](launch-recovery.md).
Existing token/verifier encoding, explicit capabilities, fixed-owner profile and publication rules
remain unchanged. This is not a new login system or an unauthenticated recovery API.

## SETUP-01 — Explicit new-instance setup

A local setup helper targets Linux, Windows and macOS. It prepares a new instance, identities and
separate owner/agent-client credentials. It never overwrites or resets an existing store. Enrollment
and initial grants follow the existing K-AUTH bootstrap-only profile; setup does not create a generic
network registration/reset route. Exact interrupted-initialization handling requires qualification.

## SETUP-02 — Client-owned secrets

Generate secrets with the OS CSPRNG. Only identifiers, verifiers and explicit grants reach bootstrap
and canonical Git state. Owner secrets never pass through the model, command-line arguments, links,
ordinary logs or diagnostic output. The server never returns or recovers a raw client secret.

## SETUP-03 — Initial protected storage profile

Use protected client files outside coding projects. Establish and verify the relevant OS permissions/
ACLs; failure to establish or verify protection refuses the operation, without insecure fallback.
Exact file ownership, parent-directory, replacement and path/link rules must be qualified for each
platform before support is claimed. No isolation from an administrator or deliberately instruction-
violating agent is promised under T1. Browser localStorage/sessionStorage is not a token store.

## SETUP-04 — Human client connection

A local human-client component obtains the owner key from protected client storage. The key is not
entered through chat, embedded HTML resources or the approval form. Setup is performed once; the
owner is not required to reenter a key for every decision. Configure/bind the service/store destination
per K-AUTH; loading a nonsecret request link is not approval or credential delegation.
The exact component/transport/secret-memory lifecycle requires readiness design and tests; this
contract does not approve a model-accessible unrestricted approval proxy or universal native support.
Revision-2 owner clarification: the human presenter is a harness plugin/extension/panel, not a
separate desktop application or browser window. The local component supports that workflow without
exposing keys to the model. Qualify actual harness APIs/session binding/communication under T1;
do not invent stronger adversarial-isolation requirements as a prerequisite. First-time local setup
may use a helper but must not require leaving the harness for each decision.

## SETUP-05 — Rotation and lost responses

Persist the new replacement secret before submitting rotation. Retain the original command and
both necessary client states until its canonical outcome is reconciled. Client file changes and Git
publication are not one atomic transaction. Preserve exact key/payload/guards on retry and current
authorization/principal rules; do not manufacture a second rotation after an ambiguous response.
Loss of the last owner secret never enables an unauthenticated reset or history-rewriting bypass.

## SETUP-06 — Per-platform acceptance

Qualify file protection, absence of normal-path secret disclosure, interrupted initialization,
existing-store bootstrap refusal and lost-response rotation recovery separately on Linux, Windows
and macOS. Use only synthetic test credentials. A successful Linux test, JVM build or mocked ACL
does not qualify another OS. Real service launch/recovery remains governed separately by K-RUN.

## Approval provenance and boundaries

The owner explicitly approved the six-point K-SETUP v1 presented in conversation on 2026-11-04 with
“утверждаю”. SETUP-01–06 record that disclosed scope, not unshown helper/IPC/schema details. This is
a manual pre-service decision record, not a fabricated service resolution. Approval is not evidence
of implementation, safe cross-platform operation, real credential provisioning or M1 acceptance.
No live keys, production deployment, paid experiment, public push or blanket new source publication
is authorized by this record. Technical details must preserve accepted authority and fail-closed laws.

Revision 2 records the owner's explicit requirement for in-harness approval and rejection of desktop-
only/external-browser decision workflows on 2026-11-04. It narrows SETUP-04 delivery, not the accepted
secret format, bootstrap, rotation or cross-platform requirements.

## Current bounded implementation status

Local helper and native OpenCode controller candidate use protected Linux/procfs client files,
client-side CSPRNG secrets, verifier-only bootstrap and retained exact answer/rotation commands.
Owner/agent files remain separate. Unsupported OS adapters fail explicitly; Windows/macOS permission/
ACL/helpers are not implemented or verified. Native panel/IPC tests use component fixtures, not an
installed OpenCode host. IF-001/IF-002 were corrected and independently closed for exact candidate
bytes; combined local checks passed, but L-001's earlier first-controls timeout remains unexplained.
No live owner keys or production bootstrap occurred. SETUP-01–06 laws remain unchanged.
