# Writhelm native OpenCode V2 adapter — author candidate

This is a **native GUI plugin candidate**, not an installed-host acceptance claim. It targets the
experimental GUI contracts of OpenCode Desktop **2.2.5**, using actual `@opencode/plugin` **2.2.5**
GUI/main/Vite exports. CLI 2.2.2 is a different profile. TUI/web/other Desktop versions refuse rather
than use a standalone or external-browser approval fallback.

## Boundaries

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
