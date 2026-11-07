// SPDX-License-Identifier: AGPL-3.0-only
import path from "node:path"
import type { OpenCodeClient } from "@opencode/client"
import type { Plugin } from "@opencode/plugin"
import { NativeRuntimeClient, nativeUnavailable } from "./native-runtime-client.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { createNativeCredentialRegistry } from "./native-host-identity.js"
import { LinuxWorkRequestCustody, registerConnectedWorkTools } from "./connected-tools.js"
import { ProtectedNativeBridge } from "./native-bridge.js"
import { ConnectedNativeAdapter } from "./connected-native.js"
import { listenNativeTransport } from "./native-transport.js"
import { prepareNativePeerProofs } from "./native-bootstrap-secrets.js"

export type NativeRuntimeConfiguration = Readonly<{
  root: string; nativeOrigin: string; serviceOrigin: string; transportOrigin: string
  storeId: string; projectId: string; controllerId: string; hostId: string
  controllerSessionID: string; assignmentSessionIDs: readonly string[]
  serviceProof: Uint8Array; transportProof: Uint8Array
}>

/** Explicit pinned in-process startup composition. Configuration/proofs come from trusted local
 * bootstrap, never ctx.options/tool/RPC/model fields. Existing protected bindings are prerequisites;
 * this function does not enroll, grant, adopt a changed incarnation, install a working profile or
 * wake any session. Missing packet/source/physical qualification continues to hold execution. */
export async function startProtectedNativeRuntime(ctx: Plugin.Context, native: OpenCodeClient, config: NativeRuntimeConfiguration) {
  if (process.platform !== "linux" || ctx.app.version !== "2.2.5" || config.serviceProof.length !== 32
    || config.transportProof.length !== 32 || !path.isAbsolute(config.root)
    || config.assignmentSessionIDs.length > 256 || new Set(config.assignmentSessionIDs).size !== config.assignmentSessionIDs.length
    || config.assignmentSessionIDs.includes(config.controllerSessionID)) nativeUnavailable()
  // Derive excluded coding locations from actual SDK sessions, not caller-asserted work roots.
  const actual = await Promise.all([config.controllerSessionID, ...config.assignmentSessionIDs].map(async sessionID => {
    const value = await ctx.session.get({ sessionID })
    if (value.id !== sessionID || value.revert) nativeUnavailable()
    return value.location.directory
  }))
  const forbidden = [...new Set([ctx.location.directory, ...actual])]
  const custody = new LinuxNativeCustody(path.join(config.root, "native"), forbidden)
  const requests = new LinuxWorkRequestCustody(path.join(config.root, "requests"), forbidden)
  const service = new NativeRuntimeClient(config.serviceOrigin, Buffer.from(config.serviceProof).toString("base64url"),
    config.storeId, config.projectId, config.controllerId, config.hostId)
  const registry = await createNativeCredentialRegistry(ctx, native, config.nativeOrigin, custody, service, config.serviceOrigin, requests)
  const restored = await registry.restoreAll()
  const proofs = await prepareNativePeerProofs(custody, config)
  proofs.serviceProof.fill(0); proofs.transportProof.fill(0)
  const controller = await registry.inspect(config.controllerSessionID)
  if (!controller || controller.scope !== "controller") nativeUnavailable()
  const bridge = new ProtectedNativeBridge(registry, custody, config.controllerSessionID, config.hostId)
  await Promise.all([...new Set([...restored, ...config.assignmentSessionIDs])].map(sessionID => bridge.manage(sessionID)))
  const adapter = new ConnectedNativeAdapter(ctx, native.session, bridge, config.hostId)
  await adapter.register()
  let transport: Awaited<ReturnType<typeof listenNativeTransport>> | undefined
  try {
    transport = await listenNativeTransport(config.transportOrigin, config.transportProof, bridge, adapter)
    const tools = await registerConnectedWorkTools(ctx, registry)
    return Object.freeze({ registry, bridge, async close() {
      await transport!.close()
      await tools.dispose()
      await adapter.close() // managed sessions keep fail-closed admission hooks
      await bridge.close()
    } })
  } catch (error) {
    if (transport) await transport.close()
    await adapter.close()
    await bridge.close()
    throw error
  }
}
