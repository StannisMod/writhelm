// SPDX-License-Identifier: AGPL-3.0-only
import { createHash, randomUUID } from "node:crypto"
import { isLosslessNumber } from "lossless-json"
import { NativeCredentialRegistry } from "./native-credentials.js"
import { NativeContextSources } from "./native-context-source.js"
import type { ContextExtraction } from "./native-context-extraction.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { closedObject, nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { sendContext } from "./native-context-protocol.js"

const hash = (text: string) => createHash("sha256").update(text, "utf8").digest("hex")
const parse = (text: string) => exactJSON(text, nativeUnavailable)
const same = (a: unknown, b: unknown) => contextJSON(a) === contextJSON(b)
type Step = Readonly<{ operation: string; raw: string; recordJSON: string; reference: any }>
type Attempt = Readonly<{ formatVersion: 1; id: string; sessionID: string; sourceLocatorId: string; sourceDigest: string;
  bindingJSON: string; taskJSON: string; manifestJSON: string; profileJSON: string; steps: readonly Step[] }>

/** Hook-owned canonical worker publisher. Original IDs/bytes are retained BEFORE the first post.
 * Retries replay the exact commands and confirm exact records under current service policy; no
 * installation, execution permission or physical-completion fact follows from this helper. */
export class NativeWorkerCheckpoints {
  constructor(private readonly registry: NativeCredentialRegistry, private readonly sources: NativeContextSources,
    private readonly custody: LinuxNativeCustody) {}
  async prepare(sessionID: string, extracted: ContextExtraction): Promise<string> {
    if (extracted.gaps.length) nativeUnavailable()
    const frame = await this.registry.workerContext(sessionID)
    if (extracted.source.scope.scope !== "assignment" || extracted.source.scope.sessionID !== sessionID
      || extracted.source.scope.bindingJSON !== frame.bindingJSON || extracted.source.profileJSON !== frame.profileJSON
      || extracted.source.packet !== frame.packet) nativeUnavailable()
    const actualSource = await this.sources.read(sessionID, extracted.source.locatorId, extracted.sourceDigest)
    if (!same(actualSource, extracted.source) || !same(await this.sources.proof(actualSource.locatorId), extracted.proof)) nativeUnavailable()
    if (!Array.isArray(frame.obligationIds) || frame.obligationIds.some((id: unknown) => typeof id !== "string" || !uuidPattern.test(id))) nativeUnavailable()
    const covered = new Set(extracted.entries.flatMap(e => [...e.obligationIds]))
    if (frame.obligationIds.some((id: string) => !covered.has(id)) || extracted.entries.some(e => e.obligationIds.some(id => !frame.obligationIds.includes(id)))) nativeUnavailable()
    const projectId = extracted.source.scope.projectId, task = parse(frame.taskJSON), profile = parse(frame.profileJSON)
    const plan = parse(frame.planJSON), view = parse(frame.loadedViewJSON)
    const previousMemory = frame.memoryJSON === null ? null : parse(frame.memoryJSON)
    const checkpoint = frame.checkpointJSON === null ? null : parse(frame.checkpointJSON)
    if (checkpoint?.payload.handoff === "finished") nativeUnavailable() // finished-context retirement is not reopened as unfinished
    const assignment = { assignmentId: frame.assignmentId, generation: BigInt(frame.executionGeneration) }
    const id = randomUUID(), cutKey = { projectId, id: randomUUID() }, memoryKey = { projectId, id: randomUUID(), revision: 0 }
    const checkpointKey = { projectId, id: randomUUID() }
    const bodies = new Map<string, string>()
    const body = (text: string) => {
      if (!text.isWellFormed() || /wh1\.[0-9a-f-]{36}\.[0-9a-f-]{36}\.[A-Za-z0-9_-]{43}/.test(text)) nativeUnavailable()
      const digest = hash(text); bodies.set(digest, text)
      return { digest, byteLength: Buffer.byteLength(text) }
    }
    const pending = [...(previousMemory?.payload.pending ?? []), ...(checkpoint?.payload.pending ?? [])]
    for (const native of extracted.source.pendingNativeMessages) pending.push({ id: randomUUID(), kind: "event", source: null,
      description: `Native input ${native.messageId} remains outside source cut ${cutKey.id}; original body/metadata retained privately`, responsibleTask: task })
    for (const description of extracted.pending) pending.push({ id: randomUUID(), kind: "obligation", source: null, description, responsibleTask: task })
    const uniquePending = new Map<string, any>()
    for (const item of pending) {
      if (uniquePending.has(item.id) && !same(uniquePending.get(item.id), item)) nativeUnavailable()
      uniquePending.set(item.id, item)
    }
    if (uniquePending.size > 256) nativeUnavailable()
    const pendingItems = [...uniquePending.values()]
    const fragments = extracted.entries.flatMap(e => e.exactFragments.map(body))
    const uniqueFragments = [...new Map(fragments.map(f => [f.digest, f])).values()]
    const cut = { task, assignment, nativeHostId: extracted.source.scope.hostId, nativeSessionId: sessionID, profile,
      sourceFormatVersion: 1, renderedRequestDigest: extracted.proof.renderedRequestDigest, packetDigest: extracted.proof.packetDigest,
      retainedLocatorId: extracted.source.locatorId, nativeMessages: extracted.proof.nativeMessages, retainedFragments: uniqueFragments, pending: pendingItems }
    const record = (family: string, key: unknown, payload: unknown) => contextJSON({ formatVersion: 1, family, key, payload })
    const cutRecord = record("source-cuts", cutKey, cut), cutRef = { family: "source-cuts", key: cutKey, digest: hash(cutRecord) }
    const memoryBodies = new Map<string, string>()
    const entries = extracted.entries.map(entry => {
      const content = body(contextJSON({ statement: entry.text, sourceCut: cutRef, originalNativeMessageIds: entry.sourceMessageIds }))
      memoryBodies.set(content.digest, bodies.get(content.digest)!)
      return { id: randomUUID(), status: entry.status, content, sources: [{ kind: "work", reference: cutRef }],
        remainingObligationIds: entry.obligationIds, uncertainty: entry.uncertainty, exactRequiredFragments: entry.exactFragments.map(body) }
    })
    const memory = { task, assignment, previous: previousMemory?.key ?? null, sourceCutId: cutKey.id, entries, gaps: [], pending: pendingItems }
    const memoryRecord = record("memory", memoryKey, memory)
    const report = body(contextJSON({ kind: "task-shaped-context-checkpoint", sourceCut: cutRef,
      scope: "Attributed residual observations/proposals, not accepted norms or verification evidence",
      artifactStatus: "Earlier artifact identities retained as historical; no new code snapshot, correctness or physical completion asserted",
      remainingObligationIds: frame.obligationIds, pending: pendingItems }))
    const data = { task, assignment, manifest: parse(frame.manifestJSON), loadedView: view.key, sourceCutId: cutKey.id,
      plan: plan.key, planGeneration: plan.payload.generation, memory: memoryKey, planSteps: plan.payload.steps,
      artifacts: checkpoint?.payload.artifacts ?? [], pending: pendingItems, report,
      handoff: "unfinished", retrospective: null, result: null, notification: null }
    const checkpointRecord = record("checkpoints", checkpointKey, data)
    if (!Array.isArray(frame.reusableBodies)) nativeUnavailable()
    const reusable = new Map<string, number>()
    for (const ref of frame.reusableBodies) {
      if (typeof ref.digest !== "string" || !/^[0-9a-f]{64}$/.test(ref.digest) || !isLosslessNumber(ref.byteLength)
        || !/^(0|[1-9][0-9]*)$/.test(ref.byteLength.value) || BigInt(ref.byteLength.value) > 1048576n) nativeUnavailable()
      reusable.set(ref.digest, Number(ref.byteLength.value))
    }
    // The service derived this metadata using BOTH parent and body disclosure at current H.
    // Its command boundary rechecks reuse authority; guessed/global digest possession is not enough.
    const missing = (digest: string, text: string) => {
      const known = reusable.get(digest)
      if (known !== undefined && known !== Buffer.byteLength(text)) nativeUnavailable()
      return known === undefined
    }
    const bodyItems = (map: Map<string, string>) => [...map].filter(([digest, text]) => missing(digest, text)).map(([digest, body]) => ({ digest, body }))
    const step = (operation: string, payload: unknown, recordJSON: string, reference: unknown): Step => ({ operation,
      raw: contextJSON({ formatVersion: 3, projectId, requestKey: randomUUID(), operation, payload }), recordJSON, reference })
    const cutBodies = new Map(uniqueFragments.map(f => [f.digest, bodies.get(f.digest)!]))
    const steps = [
      step("record_source_cut", { key: cutKey, data: cut, bodies: bodyItems(cutBodies) }, cutRecord, cutRef),
      step("record_memory", { key: memoryKey, data: memory, bodies: bodyItems(memoryBodies) }, memoryRecord,
        { family: "memory", key: memoryKey, digest: hash(memoryRecord) }),
      step("record_checkpoint", { key: checkpointKey, data, expectedAssignmentGeneration: BigInt(frame.recordGeneration),
        bodies: bodyItems(new Map([[report.digest,bodies.get(report.digest)!]])) }, checkpointRecord,
      { family: "checkpoints", key: checkpointKey, digest: hash(checkpointRecord) }),
    ]
    const attempt: Attempt = { formatVersion: 1, id, sessionID, sourceLocatorId: extracted.source.locatorId, sourceDigest: extracted.sourceDigest,
      bindingJSON: frame.bindingJSON, taskJSON: frame.taskJSON, manifestJSON: frame.manifestJSON, profileJSON: frame.profileJSON, steps }
    await this.custody.retain("context-attempt", id, contextJSON(attempt))
    return id
  }
  async publish(sessionID: string, id: string, signal: AbortSignal) {
    if (!uuidPattern.test(id)) nativeUnavailable()
    const raw = await this.custody.read("context-attempt", id)
    if (raw === undefined) nativeUnavailable()
    const decoded = closedObject(parse(raw), ["formatVersion", "id", "sessionID", "sourceLocatorId", "sourceDigest", "bindingJSON", "taskJSON", "manifestJSON", "profileJSON", "steps"])
    if (decoded.id !== id || decoded.sessionID !== sessionID || !isLosslessNumber(decoded.formatVersion) || decoded.formatVersion.value !== "1"
      || !Array.isArray(decoded.steps) || decoded.steps.length !== 3) nativeUnavailable()
    const attempt = decoded as Attempt
    if (contextJSON(attempt) !== raw) nativeUnavailable()
    const source = await this.sources.read(sessionID, attempt.sourceLocatorId, attempt.sourceDigest)
    if (source.scope.bindingJSON !== attempt.bindingJSON) nativeUnavailable()
    const channel = await this.registry.contextChannel(sessionID, signal)
    const operations = ["record_source_cut", "record_memory", "record_checkpoint"]
    for (const [index, step] of attempt.steps.entries()) {
      if (signal.aborted) nativeUnavailable()
      closedObject(step, ["operation", "raw", "recordJSON", "reference"])
      if (step.operation !== operations[index]) nativeUnavailable()
      const command = parse(step.raw)
      if (command.operation !== step.operation || command.projectId !== channel.projectID) nativeUnavailable()
      const reply = await sendContext(channel, "commands", step.raw, signal)
      if (reply.kind !== "settled" || reply.outcome?.kind !== "success" || reply.outcome.operation !== step.operation
        || reply.identity?.requestKey !== command.requestKey || reply.identity?.projectId !== channel.projectID) {
        const code = reply.outcome?.code ?? reply.code
        throw new Error(`canonical context ${step.operation} refused: ${typeof code === "string" && /^[A-Z_]{1,64}$/.test(code) ? code : "unavailable"}`)
      }
      const { family, key, digest } = step.reference
      const observed = await sendContext(channel, "reads", contextJSON({ formatVersion: 2, projectId: channel.projectID,
        operation: "read_" + family.replaceAll("-", "_"), subject: { key, digest } }), signal)
      if (observed.kind !== "read" || contextJSON(observed.data) !== step.recordJSON || hash(step.recordJSON) !== digest) nativeUnavailable()
    }
    const current = await this.registry.workerContext(sessionID, signal)
    const checkpoint = parse(current.checkpointJSON)
    const last = attempt.steps[2]!
    if (contextJSON(checkpoint) !== last.recordJSON || current.bindingJSON !== attempt.bindingJSON || current.taskJSON !== attempt.taskJSON
      || current.manifestJSON !== attempt.manifestJSON || current.profileJSON !== attempt.profileJSON) nativeUnavailable()
    await this.custody.retain("context-confirmed", id, contextJSON({ formatVersion: 1, id, sessionID, checkpoint: last.reference }))
    return Object.freeze({ checkpoint: last.reference, source, frame: current })
  }
}
