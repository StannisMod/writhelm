// Build-only execution fence: new dependency executables/addons/lifecycle tools are not authorized.
const cp = require('node:child_process')
const mod = require('node:module')
for (const name of ['spawn', 'spawnSync', 'exec', 'execSync', 'execFile', 'execFileSync', 'fork']) {
  cp[name] = () => { throw new Error('UNREVIEWED_BUILD_SUBPROCESS') }
}
mod.syncBuiltinESMExports()
require.extensions['.node'] = () => { throw new Error('UNREVIEWED_NATIVE_BUILD_ADDON') }
globalThis.fetch = () => Promise.reject(new Error('NETWORK_DISABLED_DURING_BUILD'))
