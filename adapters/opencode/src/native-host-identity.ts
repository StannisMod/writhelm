// SPDX-License-Identifier: AGPL-3.0-only
import { createHash } from "node:crypto"
import * as fs from "node:fs/promises"
import type { OpenCodeClient } from "@opencode/client"
import type { Plugin } from "@opencode/plugin"
import { exactLoopback } from "./strict-json.js"
import { nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { NativeRuntimeClient } from "./native-runtime-client.js"
import { NativeCredentialRegistry } from "./native-credentials.js"
import { LinuxWorkRequestCustody } from "./connected-tools.js"

const OC225_LINUX = "8d060e1d2fbb9bb44cf09ae6ed1f3f6a7cff070b5227e2f90970132b23a92050"

/** Actual Linux process identity, not a title/PID-only label. This lower-level primitive is also
 * exercised against the test Node executable; production startup pins the real sidecar below. */
export async function linuxHostEpoch(info: Awaited<ReturnType<OpenCodeClient["server"]["info"]>>,
  nativeOrigin: string, expectedExecutableDigest: string): Promise<string> {
  exactLoopback(nativeOrigin, nativeUnavailable)
  if (process.platform !== "linux" || info.version !== "2.2.5" || info.pid !== process.pid
    || info.hostname !== "127.0.0.1" || !info.urls.some(url => new URL(url).origin === nativeOrigin)
    || !/^[0-9a-f]{64}$/.test(expectedExecutableDigest)) nativeUnavailable()
  const executable = await fs.open("/proc/self/exe", "r")
  let executableDigest: string
  try {
    const before = await executable.stat()
    if (!before.isFile() || before.size < 1 || before.size > 1024 * 1024 * 1024) nativeUnavailable()
    const digest = createHash("sha256"), buffer = Buffer.alloc(65536)
    let bytes = 0
    while (true) {
      const result = await executable.read(buffer, 0, buffer.length, null)
      if (!result.bytesRead) break
      bytes += result.bytesRead
      if (bytes > before.size) nativeUnavailable()
      digest.update(buffer.subarray(0, result.bytesRead))
    }
    const after = await executable.stat()
    if (bytes !== before.size || after.dev !== before.dev || after.ino !== before.ino || after.size !== before.size) nativeUnavailable()
    executableDigest = digest.digest("hex")
  } finally { await executable.close() }
  if (executableDigest !== expectedExecutableDigest) nativeUnavailable()
  const stat = await fs.readFile("/proc/self/stat", "utf8")
  const fields = stat.slice(stat.lastIndexOf(")") + 1).trim().split(/\s+/)
  const start = fields[19]
  const boot = (await fs.readFile("/proc/sys/kernel/random/boot_id", "utf8")).trim()
  if (!uuidPattern.test(boot) || !start || !/^[0-9]+$/.test(start)) nativeUnavailable()
  return createHash("sha256").update(JSON.stringify(["native-linux-process-v1", executableDigest, boot, process.pid, start, nativeOrigin])).digest("hex")
}

/** Explicit in-process pinned-host bootstrap. No discovery fallback, owner-profile loading,
 * working-profile installation or model dispatch. A different executable/version refuses. */
export async function createNativeCredentialRegistry(ctx: Plugin.Context, native: OpenCodeClient, nativeOrigin: string,
  custody: LinuxNativeCustody, service: NativeRuntimeClient, serviceOrigin: string, requests: LinuxWorkRequestCustody) {
  if (ctx.app.version !== "2.2.5") nativeUnavailable()
  const epoch = await linuxHostEpoch(await native.server.info(), nativeOrigin, OC225_LINUX)
  await custody.initialize()
  return new NativeCredentialRegistry(ctx, custody, service, serviceOrigin, requests, epoch)
}
