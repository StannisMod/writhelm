// SPDX-License-Identifier: AGPL-3.0-only
import { createHash } from "node:crypto"
import { NativeCredentialRegistry } from "./native-credentials.js"
import { NativeContextSources } from "./native-context-source.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { nativeUnavailable } from "./native-runtime-client.js"
import { sendContext } from "./native-context-protocol.js"

const parse = (s: string) => exactJSON(s, nativeUnavailable)
const hash = (s: string) => createHash("sha256").update(s, "utf8").digest("hex")
const same = (a: unknown, b: unknown) => contextJSON(a) === contextJSON(b)

/** Exact own-controller recovery. The service derives the unique canonical continuation tip;
 * cache loss never selects a latest timestamp or another controller's record. No model, wake,
 * assignment, approval, acceptance or integration action is introduced by reading restoration. */
export class NativeControllerRestoration {
  constructor(private readonly registry: NativeCredentialRegistry, private readonly sources: NativeContextSources,
    private readonly profileJSON: string) {}
  async recover(sessionID: string, signal: AbortSignal) {
    const frame = await this.registry.controllerContext(sessionID, this.profileJSON, signal)
    if (frame.previousJSON === null || frame.previousReferenceJSON === null) nativeUnavailable()
    const row = parse(frame.previousJSON), ref = parse(frame.previousReferenceJSON)
    const scope = await this.sources.currentScope(sessionID)
    if (scope.scope !== "controller" || ref.family !== "controller-continuations" || ref.key.controllerId !== scope.controllerId
      || !same(row.key, ref.key) || hash(contextJSON(row)) !== ref.digest || row.payload.gaps.length
      || !same(row.payload.profile, parse(this.profileJSON))) nativeUnavailable()
    const source = await this.sources.readCut(sessionID, { ...row.payload.source, profile: row.payload.profile })
    const channel = await this.registry.contextChannel(sessionID, signal)
    const body = async (value: any) => {
      const response = await sendContext(channel, "reads", contextJSON({ formatVersion: 2, projectId: channel.projectID,
        operation: "read_work_body", subject: { digest: value.digest, record: ref } }), signal)
      if (response.kind !== "read" || typeof response.data?.body !== "string" || hash(response.data.body) !== value.digest
        || BigInt(Buffer.byteLength(response.data.body)) !== BigInt(value.byteLength.value ?? value.byteLength)) nativeUnavailable()
      return response.data.body as string
    }
    const entries: Array<Record<string, unknown>> = []
    for (const item of row.payload.entries) {
      const exactRequiredFragments: string[] = []
      for (const fragment of item.entry.exactRequiredFragments) exactRequiredFragments.push(await body(fragment))
      entries.push({ tasks: item.tasks, id: item.entry.id, status: item.entry.status, content: await body(item.entry.content),
        sources: item.entry.sources, remainingObligationIds: item.entry.remainingObligationIds,
        uncertainty: item.entry.uncertainty, exactRequiredFragments })
    }
    const report = await body(row.payload.report)
    const latest = await this.registry.controllerContext(sessionID, this.profileJSON, signal)
    if (latest.previousReferenceJSON !== frame.previousReferenceJSON || latest.frontierJSON !== frame.frontierJSON
      || latest.packet !== frame.packet || !same(latest.plansJSON,frame.plansJSON)) nativeUnavailable()
    return Object.freeze({ reference: ref, packet: frame.packet, residual: contextJSON({ formatVersion: 1,
      kind: "restored-controller-residual-not-norms", continuation: ref, originalFrontier: row.payload.frontier,
      currentFrontier: parse(frame.frontierJSON), currentPlans: frame.plansJSON.map(parse), entries, pending: row.payload.pending, report,
      sourceLocatorId: source.locatorId, rule: "Exact mandatory sources restore separately; no authority is granted by this continuation" },256*1024) })
  }
}
