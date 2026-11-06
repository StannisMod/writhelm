# Cross-platform launch and abnormal recovery

Contract K-RUN, revision 2. Status: six disclosed decisions approved by owner; not implemented or
process-fault/platform-qualified. Revision 1's Linux/systemd-only direction was not approved.
Dependencies: [K-PUBLISH](command-publication.md) PB-07–09, [K-AUTH](authentication-access.md),
[K-API revision 2](service-interface.md). Git SSOT, authority, command identity and exact data stay unchanged.

## RUN-01 — Target service platforms

Linux, Windows and macOS are target service platforms. Remote clients are independent of server OS;
remote-client compatibility alone does not establish service support on that client's OS. Qualify
each service platform separately; running on a JVM does not establish operational qualification.

## RUN-02 — Common operational guarantees

One active instance per private store, controlled publication-capable child processes, safe shutdown
and no new instance admitted until prior writers have terminated or are reliably fenced from further
publication. A dead parent, elapsed time or free owner lock alone is not proof. Platform-specific
mechanisms must preserve these common guarantees rather than silently weaken them.

## RUN-03 — Platform adapters

Linux systemd/cgroups and Windows Job Objects are implementation candidates. macOS needs a separately
selected and qualified mechanism. None is asserted to provide equivalent guarantees without evidence.
Executable discovery, process ownership/containment, permissions, filesystem locks and synchronization
must be qualified in the actual OS/runtime/filesystem profile. A platform unable to meet the guarantee
remains unqualified and fails explicitly, not a permissive fallback.

## RUN-04 — Fail-closed recovery

Recovery excludes old writers and concurrent normal launch, then validates the designated published
state before safely clearing abnormal runtime/publication markers. Inability to establish safety
leaves recovery blocked. No automatic ref reset, orphan promotion or deletion of ambiguous Git locks.
Exact operator tools, interruption-safe sequences and fencing mechanisms are readiness inputs, not
implemented procedures authorized by this document. Unknown publication remains unknown until
qualified reconciliation; confirmed F-001 cannot be waived by launch/transport code.

## RUN-05 — Original-command reconciliation

Clients retain and retry original key/payload/guards under current authorization. Only a receipt on
the validated published line establishes the original durable outcome; orphan objects do not. Missing
client acknowledgement does not justify automatic new keys, fabricated success or claimed rollback.

## RUN-06 — Per-platform acceptance

Qualify file locks, actual Git invocation/configuration, write synchronization, process containment,
normal stop/restart and abnormal recovery for each supported OS/profile. Tests must cover old writers
surviving parent death and exclude concurrent restart; mocked process success is not such evidence.
Implementation order is a separate explicit plan; Linux must not silently become the sole target.
Real process-fault/checkpoint execution still requires its designated authorization. No current
Windows/macOS support, host/power-loss, remote filesystem, HA or backup guarantee follows.

## Approval provenance and implementation status

On 2026-11-04 the owner challenged the proposed Linux-only K-RUN v1. The planner replaced it with
the six-point cross-platform K-RUN v2; the owner explicitly approved that version and requested
cross-platform project principles and an impact assessment of existing contracts. RUN-01–06 map
those disclosed decisions, not blanket approval of OS mechanism details or release sequencing.
This is a manual pre-service decision record, not a fabricated service approval event.

Existing NativeGit/RuntimeOwner at 05d1b9e2 use Linux-only qualification, POSIX permission checks,
/usr/bin/git and /dev/null and unqualified directory synchronization on other OSes. They need separate
adapter implementation and verification. Existing Linux evidence retains exactly its original scope.
No source/runtime change, new live key, deployment, commit/push or process-fault run is claimed here.

The owner subsequently explicitly approved the disclosed cross-platform clarifications to the
existing contracts and requested that the implementation worker apply them. Their scope is OS-adapter
boundaries and per-platform qualification; canonical bytes, authority and publication laws are not
weakened. It is not a claim that all three adapters have been implemented or verified.
