# Writhelm native OpenCode V2 adapter — author candidate

This is a **native GUI plugin candidate**, not an installed-host acceptance claim. It targets the
experimental GUI contracts of OpenCode Desktop **2.2.5**, using actual `@opencode/plugin` **2.2.5**
GUI/main/Vite exports. CLI 2.2.2 is a different profile. TUI/web/other Desktop versions refuse rather
than use a standalone or external-browser approval fallback.

## Boundaries

### Governed primary entrypoint (uninstalled source implementation)

The package now exports an actual server entrypoint, `build/src/primary-server.js`, alongside the
GUI. A GUI panel is not a governed primary session. Trusted local provisioning must supply
`WRITHELM_PRIMARY_ROOT` pointing to qualified protected custody, already enrolled controller
credentials, fixed service/native origins and registration file, peer proofs and exact operation
schemas. No `ctx.options` actor/token, owner-profile import or default service discovery is used.
`preparePrimaryBootstrap` rechecks actual controller scope before retaining the key; it does not
issue grants or register a new owner. Native client discovery reads only the explicitly selected
registration file and never starts a service. Production startup pins the real2.2.5 executable/PID.

Trusted provisioning installs a fresh `opencode.json` native policy in the primary project before
enabling the server part: deny ordinary tool actions except native reads/questions and the separately service-
authorized Writhelm tools. Existing project config is never overwritten or permissively merged.
Integrating an existing configuration requires explicit review. The policy is a native fallback
against plugin failure; model/tool/permission hooks independently enforce current bindings while
loaded. It is NOT OS containment or a claim that arbitrary third-party plugins are sandboxed.

The first ordinary prompt in a fresh root session elects one immutable controller/session binding
in protected custody; concurrent candidates, children, moved/reverted sessions and unmanaged old
history refuse. Original user input is not rewritten. Primary context explains requirements/design
clarification, structured exact owner approval, draft versus executable tasks, separate workers,
independent review, acceptance and integration. Shell/edit/Code Mode/unbound subagents are
removed from its outgoing tools AND refused at live execution/permission boundaries. Native deny
rules persist on that primary session; selecting another agent does not make it an executor.

Primary project inspection reuses native `read`, preserving its configured secret/owner permission
decisions instead of implementing a filesystem reader that bypasses them. External-directory access
is denied; protected profiles/canonical Git must remain outside the coding work area. Knowledge tools use only the same current
controller's v1 propose/request/assessment/pin and exact read operations; owner approval and grant
administration cannot pass them. Work tools retain existing command3/read2 authorization. Missing
bootstrap, missing native fallback policy, service/current READ loss or an unenrolled stand location
cannot silently become ordinary unrestricted coding. Unknown location instances hold and ask the
operator to open the explicitly governed project; they do not select another controller.

After primary binding the server composes the existing protected native transport and scoped work
tools, then requests the service's existing runtime connection. The private startup identity witness
does NOT reenter service HTTP while its connect request owns admission. Current controller identity/
rights remain independently checked by the service; duplicate connection cannot create two engines.

This implements initial primary admission/contract tooling, not an operational full-H1 claim.
Semantic clarification/approval sufficiency still belongs to planner and independent verification,
not a field or finite fixture. Generic primary compaction holds without the Effect-native companion
and qualified task-shaped controller deployment; it never silently discards obligations. Native
worker/work-area qualification, profiles, physical/source safety, incarnation adoption, end-to-end
compaction/restore and installed pilot gates still require their respective qualification.
Unqualified execution stays held. No actual model request or working-profile install follows from
building this package. A live stand must not be restarted implicitly to test the new entrypoint.

### Reserved worker preparation and distinct initial start

`writhelm_prepare_worker` is controller-only orchestration over the existing canonical reserve,
bounded delegation registration and bind commands. Native IDs, credential/intent/request IDs are
retained privately before posting or creating anything. Every passive native effect rechecks the
actual service preparation projection: own controller/host/generation, READ/TASK_DISPATCH, current
required inputs/claims and trusted native safety. Canonical profile `qualified` text is not enough.
Unavailable native safety remains a production refusal, not a permissive file/RPC flag.

The optional protected `passive-profile/current` record selects repository/native-project mapping,
worktree parent, pinned Git executable digest and native executor/reviewer agent IDs. The first
Linux adapter accepts one exact clean Git base; dirty/multiple repositories, unqualified strategy,
startup commands, collisions and changed source/receiver attributes refuse rather than omit inputs.
Actual commit/tree/blob bytes and ownership are checked. Original create markers mean reconcile
only, never another create based on missing inbox/path. Scoped credentials are enrolled/bound only
through current canonical policy. Passive preparation/binding does not wake a model or fence writers.

`writhelm_start_worker` then uses a DISTINCT retained normal user prompt for the canonical start
intent. It never fabricates a continuation, synthetic notification or result. Mandatory instruction
packet and explicit protected `execution-profile/<exact profile-key JSON>` deployment are required.
Deployment pins model/provider/variant, route/baseURL, effort key/options and output reserve; no
inherited planner defaults. Native permission translation currently supports the known native
actions and explicit workspace/wildcard scopes only; other scopes remain unqualified and refuse.
Reviewer mutation/shell/Code Mode and unmanaged subagents remain denied.

The matching promoted context is held before model dispatch. Its private immutable witness can be
consumed by the service without HTTP reentry; only then can the existing observe-start command
confirm canonical ACTIVE and release native admission. Lost acknowledgements retain original IDs;
replay/active restoration cannot send a second wake. ACTIVE is admission, not model completion or
writer fencing. Connected-work synthetic continuation retains its separate protocol and takes over
the same worker's admission when its actual continuation gate exists.

On a same-incarnation plugin reload, bounded protected initial-original inventory rebuilds closed
gates. Only the exact still-bound generation, unchanged qualified profile/packet, original held
boundary and current canonical ACTIVE may restore admission; this sends no prompt or canonical
start/observe command. Pending, revoked, changed or another-incarnation receivers stay held. Invalid
protected original records are not treated as absence. New-process incarnation adoption and actual
writer recovery remain separate unimplemented/unqualified gates, not implied by plugin reload.

Native permission release retains its exact original target before the first update. Reconciliation
of the same target does not post another update. An observed changed permission set, including a
new deny-all or stricter owner restriction, holds instead of being overwritten; a lost reply with an
unchanged passive deny is unknown, not permission to repeat. Local close/release barriers invalidate
in-flight admission checks. This is not a native compare-and-swap guarantee: actual simultaneous
external permission edits and host serialization still require installed-profile qualification.

Worker-location instances use an in-process runtime reference only to find the real controller-owned
gate; reference/metadata presence grants nothing. Missing/reloaded/other-host references hold, and
every admission rechecks current binding, packet and service policy. Pinned context/message DTOs lack
sessionID and are checked under the exact queried receiver; inbox DTOs still require their explicit
sessionID. Actual host module sharing, lifecycle ordering, request options/accounting and physical
source/writer safety remain installed-qualification gates. Missing task-context composition refuses
generic compaction rather than falling back to a narrative summary.

### Effect-native task-context companion (source composition, not installed qualification)

`build/src/context-server.js` (package export `./task-context`) is a separate Effect-native server
plugin alongside the Promise primary entrypoint. A governed deployment must explicitly load both;
enabling only the primary does not authorize generic generation/compaction. The companion reads
the same protected bootstrap root, registers native hooks in the host lifetime Scope and starts no
service, model or second compaction scheduler. Extraction calls remain direct host Effects, never
`runPromise` nested inside a Promise hook. Missing/unqualified profiles hold.

For a worker, its current canonical profile key selects the protected `execution-profile` record.
For the controller, `controller-context/current` contains `{formatVersion:1,profileKeyJSON,
governingTaskJSON}`; the task must belong to the actual captured frontier. Its exact profile key
selects the same explicit deployment shape described above. Output must fit both canonical output
and compaction reserves. Configuration is a deployment selector, never qualification evidence.

On native compaction the dispatcher checks actual current scope/READ, worker binding/admission,
profile and native safety; lazily registers the per-session coordinator's extraction hooks in the
plugin lifetime Scope; then invokes its foreground worker/controller protocol. Current-source cuts,
checkpoint publication/confirmation, late-input detection and full-controller frontier retain their
existing independent checks before any replacement result is set. Finished-worker retirement makes
no extraction call; persisted native retirement metadata is checked before lazy coordinator creation
on later ordinary context/tool events too.

Promise guards use lifecycle-owned routing references to require the exact extraction lease plus
current admission for auxiliary generation. Effect hooks independently validate source correlation,
model/route/options and current scope. Missing/closed/other-host or replaced-runtime routes cannot
authorize a request. Different per-session profiles do not reject each other's correlated extraction.
Actual module sharing, hook order, native cancellation/accounting and installed replacement/reopen
behavior remain qualification gates; source composition and model-free fixtures do not prove them.

Production composition also requires a protected `native-context-policy/<exact profile-key JSON>`
record: `{formatVersion:1,profileKeyJSON,policyId,nativeProfileId,auto:true,background:false,
keepTokens,bufferTokens}`. The IDs must match the current canonical profile; positive integer-token
keep/buffer must fit its declared context. Authenticated native `config.get` reads the exact current
location. Every explicit normalized document layer must agree on auto/keep/buffer and foreground
mode; missing settings, conflicting overrides, percentages and background extraction refuse. The
adapter never writes config or exposes raw config/provider fields. Initial/context/model admission,
task-context dispatch and the explicit context-plugin builder use this read-only guard.

Matching config is not qualification or an actual request-accounting/threshold proof. Canonical
nativeReady/current rights and source/writer/profile evidence remain independently required. The
conservative layer rule may refuse an otherwise valid overriding configuration; it never guesses
how to repair it or modifies user-wide settings. Historical source-pin/N-case and installed-profile
gates remain held; static observation of current distribution bytes is not their recovered evidence.

### Deterministic correction kernel (not an operational tool)

The approved TASK-08 / EX-08 / EV-09 route allows an attributed exact correction to be applied without
a second LLM reasoning pass. `src/exact-text-correction.ts` currently implements only pure bounded
preparation for one existing regular text file. It checks the supplied preimage SHA256, exact unified
diff positions/context/counts and unchanged file mode; preserves UTF8/BOM/CRLF/final-newline bytes;
and returns immutable prepared text/digests. No fuzzy matching, conflict repair, commands, filesystem
write, native session, provider call or model fallback. A conservative ASCII relative path subset
and text/line/hunk/output limits refuse unsupported input rather than silently retarget it.

This is NOT a registered primary patch tool or canonical result producer. The input hash does not
prove actual worktree contents, and path syntax does not prove filesystem identity/containment.
Canonical task/base/claims/authority, mechanical execution identity, complete applicable authorship,
qualified file/write/recovery adapter, permitted checks, new exact result and independent assessment
must be composed before operational application. Current single-producer/model-profile formats are
not silently repurposed to hide patch authors or invent a mechanical LLM session.

- `src/gui.ts`: native session panel, command and exact nonsecret link handler.
- `src/main.ts`: native main-process Ipc provider and file choosers; no model tool or server RPC.
- `src/controller.ts`: exact view/answer/current-session binding and retained original retries.
- `src/protected.ts`: explicit Linux/procfs protected-file adapter, 0700 private root/0600 files,
  ownership/link/bounded-read/atomic-write checks. Windows/macOS adapters fail explicitly and remain
  required target-platform work, not a Linux-only product decision.
- `src/helper.ts` + `backend/server/.../service/LocalSetup.kt`: OS-CSPRNG/verifier-only new-store setup
  through the real existing initialization API; persisted replacement-secret/exact rotation recovery.
- `src/generated`: deterministic Ajv standalone validators from pinned own schemas. No runtime eval
  is needed in the renderer. Their small declarations describe OWN generated validators, not SDK stubs.

### Governed work presentation (uninstalled implementation)

The native **Writhelm tasks and governed workflow** command opens a separate read-only panel.
It lists bounded selector pages only for the closed read2 listing families (task heads, assignments,
results/review reports, blockers/confirmations, continuations, replacements, acceptance and integration).
Related checkpoints, reports and evidence are opened through exact references in returned records,
not unsupported listing requests or invented latest selectors. It does not combine pages or
families into an allegedly atomic all-work view. Each page, record and explicitly requested body
shows its own service basis. Expired/partial/denied/unavailable views require explicit refresh;
there is no latest-record or cached-body fallback. The service's retained-snapshot limits still apply.

Mutable selectors preserve exact Int64 generations; immutable records are checked against their
selected digest. Body reads carry the exact parent selector, recheck service access and verify UTF-8
length/digest in the main process. Report text is rendered as text, not HTML or privileged instructions.
Only the first 64 body-navigation candidates are linked; a visible partial notice and full original
record retain the remaining identities. New selections/session changes discard delayed responses;
concurrent page clicks cannot duplicate selectors.

`workList`, `workOpen` and `workBody` are closed READ-only main-process IPC methods. The renderer
cannot choose a token, origin, project, actor or command operation. The selected protected human
profile supplies credentials inside main memory only. This adds no generic owner-command forwarding,
automatic work acceptance/integration or new mutation controls. Existing exact knowledge approval
remains separate. Passed component/DOM/build checks do not qualify installed native rendering,
physical completion, cross-platform adapters or an operational H1 pilot.

New setup also writes a separate protected `agent.json` containing ONLY the independent agent key;
never hand the owner `profile.json` to an agent client. No live MCP configuration is installed by this
task. A compliant non-model agent client can load its own file without reading the owner profile.

Owner credentials remain in protected non-project files and native main memory. The renderer receives
canonical bounded material/status/outcomes, never the profile or token. No browser persistence, token
paste form, raw secret CLI argument, embedded key, generic owner proxy or session-text side channel.
Only the nonsecret profile-root pointer is remembered in native main storage. T1 remains cooperative;
no physical-human proof, hostile same-user/admin isolation or secure string/disk erasure is claimed.

Desktop2.2.5's delivered Markdown route supports this **virtual native reference as standalone inline
code** (surround the whole literal with backticks in a settled assistant-rendered message):

```text
writhelm-review/v1/<project UUID>/<request UUID>/<request SHA256>/<packet SHA256>
```

Do not put it in a Markdown `[label](destination)` or invent an HTTPS/file URL. The pinned host
classifies standalone inline-code paths, asks the plugin's `LinkHandler.exists`, and routes an ordinary
click through that handler with the originating session. It is NOT a real project/host file: the plugin
consumes its reserved namespace before the default file handler and performs no file read/creation or
URL fetch. Strict lowercase UUID/digest spelling and v1 are required; no extra fields, encoding,
query/fragment, traversal or alternate destination. The host trims inline-code text; the plugin does
not decode or normalize accepted identities. The old `writhelm://review/...` parser input remains
strict compatibility input only, NOT a working delivered-message link: the host strips its href.

The native LinkHandler opens only in the same explicit originating/current session; it does not
authorize a submission or choose a service/store. The configured protected profile remains the sole
destination; current rights and exact request/packet/guard checks are unchanged. Invalid/stale routed
references refuse visibly, and a different project refuses in the panel. Unconfigured, denied,
incomplete, stale, unsupported and oversized views cannot enable new assent. Closing an unsubmitted
view leaves pending; closing after submission does not imply rollback. Reconcile the ORIGINAL retained
command after disconnection. Native delivered-reference qualification and recovery remain held; the
finite OC003 scroll correction does not establish these cases or explain historical L001.

Source/SDK/synthetic routing tests establish this route's implementation, not an actual delivered
native-click pass. The next separately approved host checkpoint must verify the settled inline-code
reference is visibly clickable and opens the exact panel. Do not claim keyboard activation of the
inline-code reference from the host's separate anchor-Enter handler, or bypass with callback/href
injection. No external-app fallback or stronger physical-human assurance is supplied.

## Build and focused verification

Use the exact lock and task-local dependency cache. Acquisition was explicitly authorized with
ignore-scripts; no Electron runtime/browser download or dependency installation script was run.
The SDK's official Vite preset/shared-module guard remains active. Rollup uses its pinned WASM backend;
already compiled JS needs no native esbuild executable. Build subprocess/native-addon execution is
fenced by `build-guard.cjs`.

```text
node --require ./build-guard.cjs ./build-schemas.mjs
node --require ./build-guard.cjs ./node_modules/typescript/bin/tsc6 -p tsconfig.json
node --require ./build-guard.cjs ./node_modules/vite/bin/vite.js build --configLoader native
node --max-old-space-size=128 --conditions=browser --test --test-concurrency=1 build/tests/native.test.js
```

Set `WRITHELM_NATIVE_ROOT` to a fresh owned synthetic test root. Worker-level reproduction scripts
pin actual cached executables, CPU/heap limits and the real JVM/HTTP journey. Compiler application
types are checked against real SDK declarations; vendor declaration internals use normal consumer
`skipLibCheck`, not invented host definitions. This is not requalification of every vendor package.

`dist/gui-experimental.cjs` is the actual single-file bundle. Do not load it into the user's live
OpenCode as part of this handoff. A later **isolated** host run must verify native panel registration,
main/window Ipc, real session/location binding, link handling, file dialogs, complete rendering,
dismissal and same-command recovery. Component/DOM tests are not that pass.

## Setup runtime configuration (future isolated install only)

The connected-work adapter is an uninstalled candidate with a required protected bridge. Its
`current` projection must recapture the actual service continuation permit (complete condition
frontier, checkpoint context, authority, generation and claims), not just assignment state. A new
blocker after release uses explicit `rehold` before the durable existing pause intent. Rehold blocks
next managed tool/context admission; it neither fences existing writers nor confirms physical pause.
`claimPauseDispatch` must validate that exact current pause intent and retain the original dispatch
before `session.interrupt`; lost replies reconcile without a second interrupt or synthetic wake.
Only separately qualified retained native writer observations can confirm the canonical pause.
No default bridge or installed/native/platform qualification is supplied by these interfaces/tests.

### Task-shaped context composition (uninstalled implementation)

`native-context-runtime.ts` supplies an explicit Effect-native server entrypoint builder. Trusted
bootstrap provides the authenticated pinned native client, protected store/host/session binding and
explicit model/route/effort/reserve profiles; model arguments and `ctx.options` cannot choose them.
It neither discovers/starts a server nor installs a working profile. Production's service safety
observers remain explicitly unavailable until the separate native qualification gate passes.

- `native-context-source.ts` retains adapter-owned source-format1 privately: exact serialized native
  observations, prepared extraction instructions/draft and required-packet rendering. CD03 outer
  framing preserves domain Int64 bytes; native numeric metadata is not claimed as domain precision.
  Public source reads recheck current scoped READ; immutable proof callbacks never reenter service.
- `native-context-extraction.ts` composes direct `yield* ctx.session.generate` through the actual
  Effect hooks. It requires the exact scoped draft/model/route, removes inherited tools/history and
  refuses unknown concurrent generation. Cancellation does not create a nested runtime or orphan
  fork. Retained reports/proposals are not promoted to norms; actual usage remains native-owned.
- Worker checkpoint and controller-continuation publishers retain original commands before posting
  and confirm exact records under current policy. The controller keeps the complete own canonical
  assignment/task frontier, not a fabricated executor assignment or the most recent chat topic.
- Foreground compaction sets its native result only after canonical confirmation and source/packet
  recapture. A late unrecorded input retains old native context rather than falling back to a generic
  summary. Persisted/uninstalled is not silently reported as installed/acknowledged.
- Restoration reads exact canonical selectors and verifies private source availability/hashes. Exact
  mandatory instructions restore separately from attributed residual facts. Reading/restoring a
  checkpoint never wakes execution or grants review/acceptance/integration rights.
- A finished executor can retire after a complete durable own handoff without another extraction or
  waiting for downstream acceptance. New model/tool admission is held; physical completion and resource
  release still need their independent protocol. Proposals and uncertainties retain their status.

Component tests use real checked Git/HTTP/service boundaries and explicit MOCK native/provider
effects. They do not qualify installed compaction ordering, provider cancellation/accounting, all
native append paths, profile reload/incarnation adoption, physical writer custody, abnormal recovery
or Windows/macOS operation. An inherited old-generation checkpoint is not recursively disclosed to
a successor worker; unqualified rebind restoration holds for controller-mediated reconciliation.

The package's nonsecret `javaRuntime` option takes a qualified absolute Java executable and absolute
classpath entries for the compiled Writhelm helper and dependencies. No hard-coded Unix production
default or downloaded executable fallback. The helper currently explicitly requires the frozen Linux
store adapter. Its public stdin carries identifiers/verifiers only; raw secrets never go to bootstrap.
The native setup UI selects parents for NEW client/store directories and never resets an existing store.
Unknown/interrupted setup verifies the exact existing store ID rather than reinitializing or deleting
runtime markers. Production launch/supervision/recovery remains governed by K-RUN and is not installed.

Registered/current project roots are excluded from secret storage; the operator must also select a
location outside unregistered coding projects. Real Linux file checks are finite development evidence,
not exhaustive concurrent namespace/ACL qualification. Other-OS and production resource/operational
profiles, dependency release/security/notice review and fresh non-author review remain held.

All code/build outputs/evidence are scratch candidates. This directory is not implicitly authorized
for public tracking or distribution; do not publish node_modules, caches, synthetic private client/store
files, raw logs/XML or historical task material.
