// SPDX-License-Identifier: AGPL-3.0-only
import { createServer, type Server } from "node:http"
import { timingSafeEqual } from "node:crypto"
import { isLosslessNumber } from "lossless-json"
import { exactJSON, exactLoopback } from "./strict-json.js"
import { closedObject, nativeUnavailable } from "./native-runtime-client.js"
import { ProtectedNativeBridge } from "./native-bridge.js"
import { ConnectedNativeAdapter } from "./connected-native.js"
import type { NativeContextSources } from "./native-context-source.js"
import type { NativePassiveWorkers } from "./native-passive-worker.js"
import type { NativeInitialAdapter } from "./native-initial.js"
import type { NativeResultRetention, RetentionRequest } from "./native-result-retention.js"

/** Private service -> in-process native host transport, deliberately separate from service HTTP
 * admission. Immutable reconciliation authenticates the retained peer proof without calling current
 * service authority; effect methods perform their own current scoped checks through the bridge.
 * This transport installs no plugin/profile and starts no model or native host. */
export async function listenNativeTransport(origin: string, proof: Uint8Array, bridge: ProtectedNativeBridge,
  adapter: ConnectedNativeAdapter, sources?: NativeContextSources, workers?: NativePassiveWorkers,initial?:NativeInitialAdapter,
  processSelector?:()=>Readonly<{pid:number;nativeOrigin:string}>,retention?:NativeResultRetention): Promise<Readonly<{ close(): Promise<void> }>> {
  exactLoopback(origin, nativeUnavailable)
  if (process.platform !== "linux" || proof.length !== 32) nativeUnavailable()
  const secret = Buffer.from(proof), url = new URL(origin)
  let active = 0, closed = false
  const server: Server = createServer({ maxHeaderSize: 16384, requestTimeout: 10000, headersTimeout: 10000 }, async (request, response) => {
    const respond = (status: number, value: unknown) => {
      response.writeHead(status, { "Content-Type": "application/json", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" })
      response.end(JSON.stringify(value))
    }
    if (closed || active >= 32) { respond(503, { formatVersion: 1, kind: "unavailable" }); return }
    active++
    try {
      const header = (name: string) => request.headersDistinct[name]
      const supplied = header("x-writhelm-native-proof")
      if (request.method !== "POST" || request.url !== "/native/v1" || header("host")?.length !== 1
        || header("host")![0] !== url.host || supplied?.length !== 1 || !/^[A-Za-z0-9_-]{43}$/.test(supplied[0]!)
        || request.headers.origin || request.headers.cookie || request.headers.authorization
        || Object.keys(request.headers).some(k => k === "forwarded" || k.startsWith("x-forwarded-"))) nativeUnavailable()
      const decoded = Buffer.from(supplied[0]!, "base64url")
      try {
        if (decoded.length !== 32 || decoded.toString("base64url") !== supplied[0] || !timingSafeEqual(secret, decoded)) nativeUnavailable()
      } finally { decoded.fill(0) }
      if (header("content-type")?.length !== 1 || header("content-type")![0] !== "application/json"
        || request.headers["content-encoding"]) nativeUnavailable()
      let bytes = 0
      const chunks: Buffer[] = []
      for await (const chunk of request) {
        bytes += chunk.length
        if (bytes > 4 * 1024 * 1024) nativeUnavailable()
        chunks.push(Buffer.from(chunk))
      }
      const input = exactJSON(new TextDecoder("utf-8", { fatal: true }).decode(Buffer.concat(chunks)), nativeUnavailable)
      if (!isLosslessNumber(input.formatVersion) || input.formatVersion.value !== "1") nativeUnavailable()
      let result: unknown = null
      if(input.operation==="process-selector"){
        closedObject(input,["formatVersion","operation"])
        if(!processSelector)nativeUnavailable()
        const selected=processSelector()
        if(!Number.isSafeInteger(selected.pid)||selected.pid<=0||selected.pid>2147483647)nativeUnavailable()
        exactLoopback(selected.nativeOrigin,nativeUnavailable)
        result={pid:selected.pid,nativeOrigin:selected.nativeOrigin}
      } else if(input.operation==="execution-admission"){
        closedObject(input,["formatVersion","operation","requestJSON"])
        if(!initial||typeof input.requestJSON!=="string")nativeUnavailable()
        result=await initial.admission(input.requestJSON)
      } else if(input.operation==="initial-proof"){
        closedObject(input,["formatVersion","operation","intentId"])
        if(!initial||typeof input.intentId!=="string")nativeUnavailable()
        result=await initial.witness(input.intentId)
      } else if(input.operation==="candidate-inputs-proof"){
        closedObject(input,["formatVersion","operation","operationId","requestJSON"])
        if(!retention||typeof input.operationId!=="string"||typeof input.requestJSON!=="string")nativeUnavailable()
        result=await retention.candidateInputsProof(input.operationId,input.requestJSON,AbortSignal.timeout(9000))
      } else if(input.operation==="candidate-assembly-proof"){
        closedObject(input,["formatVersion","operation","operationId"])
        if(!retention||typeof input.operationId!=="string")nativeUnavailable()
        result=await retention.candidateProof(input.operationId,AbortSignal.timeout(9000))
      } else if(input.operation==="result-retention-proof"){
        closedObject(input,["formatVersion","operation","assignmentId","executionGeneration","executionId","snapshotJSON"])
        if(!retention||typeof input.assignmentId!=="string"||typeof input.executionGeneration!=="string"||
          typeof input.executionId!=="string"||typeof input.snapshotJSON!=="string")nativeUnavailable()
        const snapshot=exactJSON(input.snapshotJSON,nativeUnavailable) as RetentionRequest["snapshot"]
        result=await retention.proof({assignmentId:input.assignmentId,generation:input.executionGeneration,
          executionId:input.executionId,snapshot},AbortSignal.timeout(9000))
      } else if(input.operation==="result-diff-proof"){
        closedObject(input,["formatVersion","operation","assignmentId","executionGeneration","resultJSON"])
        if(!workers||typeof input.assignmentId!=="string"||typeof input.executionGeneration!=="string"||typeof input.resultJSON!=="string")nativeUnavailable()
        result=await workers.resultDiffProof(input.assignmentId,input.executionGeneration,input.resultJSON,AbortSignal.timeout(9000))
      } else if(input.operation==="worker-proof"){
        closedObject(input,["formatVersion","operation","assignmentId","executionGeneration"])
        if(!workers||typeof input.assignmentId!=="string"||typeof input.executionGeneration!=="string")nativeUnavailable()
        result=await workers.proof(input.assignmentId,input.executionGeneration)
      } else if (input.operation === "source-proof") {
        closedObject(input, ["formatVersion", "operation", "locatorId"])
        if (!sources || typeof input.locatorId !== "string") nativeUnavailable()
        result = await sources.proof(input.locatorId) // immutable facts only; never reenter service authority
      } else if (input.operation === "identity-immutable") {
        closedObject(input,["formatVersion","operation"])
        result=await bridge.startupIdentity()
      } else if (input.operation === "identity") {
        closedObject(input, ["formatVersion", "operation"])
        result = await bridge.identity()
      } else if (input.operation === "prepare") {
        closedObject(input, ["formatVersion", "operation", "subject", "target"])
        await bridge.prepareEnvelope(input.subject, input.target)
      } else if (input.operation === "retain") {
        closedObject(input, ["formatVersion", "operation", "kind", "subject", "target"])
        await bridge.retainOriginal(input.kind, input.subject, input.target)
      } else {
        closedObject(input, ["pause","cooperative-pause"].includes(input.operation) ? ["formatVersion", "operation", "key", "digest", "intent"]
          : ["formatVersion", "operation", "key", "digest"])
        if (typeof input.key !== "string" || typeof input.digest !== "string") nativeUnavailable()
        // Every operation names an already protected original, never an arbitrary supplied receiver.
        if (["rehold", "pause","cooperative-pause"].includes(input.operation)) {
          const target = await bridge.bindingTarget(input.key, input.digest)
          if(input.operation==="cooperative-pause")result=await adapter.cooperativePauseHeld(target,input.intent)
          else {
            await adapter.reholdBinding(target)
            if (input.operation === "pause") await adapter.pauseBinding(target, input.intent)
          }
        } else {
          const envelope = await bridge.retained(input.key)
          if (envelope.digest !== input.digest) nativeUnavailable()
          switch (input.operation) {
            case "hold": result = await adapter.prepare(input.key); break
            case "deliver": result = await adapter.deliver(input.key); break
            case "reconcile": result = await adapter.reconcile(input.key); break
            case "release": await adapter.release(input.key); break
            default: nativeUnavailable()
          }
        }
      }
      respond(200, { formatVersion: 1, kind: "native-result", result })
    } catch { respond(403, { formatVersion: 1, kind: "unavailable" }) }
    finally { active-- }
  })
  server.maxConnections = 32
  server.keepAliveTimeout = 1000
  try {
    await new Promise<void>((resolve, reject) => {
      server.once("error", reject)
      server.listen(Number(url.port), url.hostname === "[::1]" ? "::1" : url.hostname, () => { server.off("error", reject); resolve() })
    })
  } catch (error) { secret.fill(0); throw error }
  return Object.freeze({ async close() {
    closed = true
    // Do not dispose native admission hooks or manufacture original-call quiescence on close.
    await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()))
    secret.fill(0)
  } })
}
