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

const parse = (s: string) => exactJSON(s, nativeUnavailable)
const hash = (s: string) => createHash("sha256").update(s, "utf8").digest("hex")
const same = (a: unknown, b: unknown) => contextJSON(a) === contextJSON(b)

/** Own-controller continuation without inventing an executor assignment. Canonical frontier is
 * complete at captured H; changed assignment generations or ambiguous previous branches refuse.
 * Native observations are attributed by the enclosing verified source basis. Entry work sources
 * identify exact task applicability, not a claim that task text proves the observation. */
export class NativeControllerContinuations {
  constructor(private readonly registry: NativeCredentialRegistry, private readonly sources: NativeContextSources,
    private readonly custody: LinuxNativeCustody, private readonly profileJSON: string, private readonly governingTaskJSON: string) {}
  async prepare(sessionID: string, basis: Record<string, any>, extracted: ContextExtraction): Promise<string> {
    const scope = await this.sources.currentScope(sessionID)
    if (scope.scope !== "controller" || extracted.gaps.length || !same(extracted.source.scope, scope)
      || extracted.source.profileJSON !== this.profileJSON || extracted.source.packet !== basis.packet) nativeUnavailable()
    const actual = await this.sources.read(sessionID, extracted.source.locatorId, extracted.sourceDigest)
    if (!same(actual, extracted.source) || !same(await this.sources.proof(actual.locatorId), extracted.proof)) nativeUnavailable()
    const renderedBasis = parse(parse(actual.prompt).basis).basis
    if (renderedBasis !== contextJSON(basis)) nativeUnavailable()
    const current = await this.registry.controllerContext(sessionID, this.profileJSON)
    if (current.frontierJSON !== basis.frontierJSON || !same(current.plansJSON, basis.plansJSON)
      || current.packet !== basis.packet || current.previousReferenceJSON !== basis.previousReferenceJSON) nativeUnavailable()
    const frontier = parse(basis.frontierJSON), governingTask = parse(this.governingTaskJSON)
    if (!Array.isArray(frontier) || !frontier.length || !frontier.some(f => same(f.task, governingTask))) nativeUnavailable()
    const coverage = new Set(extracted.entries.flatMap(e => [...e.obligationIds]))
    if (basis.obligationIds.some((id: string) => !coverage.has(id))
      || extracted.entries.some(e => e.obligationIds.some(id => !basis.obligationIds.includes(id)))) nativeUnavailable()
    const previous = basis.previousJSON === null ? null : parse(basis.previousJSON)
    const previousRef = basis.previousReferenceJSON === null ? null : parse(basis.previousReferenceJSON)
    const id = randomUUID(), key = { projectId: scope.projectId, controllerId: scope.controllerId, id: randomUUID() }
    const bodies = new Map<string, string>()
    const body = (text: string) => {
      if (!text.isWellFormed() || /wh1\.[0-9a-f-]{36}\.[0-9a-f-]{36}\.[A-Za-z0-9_-]{43}/.test(text)) nativeUnavailable()
      const digest = hash(text); bodies.set(digest, text); return { digest, byteLength: Buffer.byteLength(text) }
    }
    const fragments = [...new Map(extracted.entries.flatMap(e => e.exactFragments.map(body)).map(b => [b.digest, b])).values()]
    const nativeSource = { nativeHostId: scope.hostId, nativeSessionId: sessionID, sourceFormatVersion: 1,
      renderedRequestDigest: extracted.proof.renderedRequestDigest, packetDigest: extracted.proof.packetDigest,
      retainedLocatorId: actual.locatorId, nativeMessages: extracted.proof.nativeMessages, retainedFragments: fragments }
    const taskRecords = parse(parse(basis.packet)[0].value).exactTaskContracts
    const entries = extracted.entries.map(e => {
      const applicable = frontier.filter(f => e.obligationIds.some(id => f.obligationIds.includes(id)))
      const tasks = applicable.length ? applicable.map(f => f.task) : [governingTask]
      const taskSources = tasks.map((task: unknown) => {
        const row = taskRecords.find((r: any) => same(r.key, task))
        if (!row) nativeUnavailable()
        return { kind: "work", reference: { family: "task-revisions", key: row.key, digest: hash(contextJSON(row)) } }
      })
      const content = body(contextJSON({ statement: e.text, controllerContinuation: key, originalNativeMessageIds: e.sourceMessageIds,
        nativeSourceLocatorId: actual.locatorId, sourceRoles: "enclosing source is observation provenance; work references are task applicability, not truth/approval" }))
      return { tasks, entry: { id: randomUUID(), status: e.status, content, sources: taskSources, remainingObligationIds: e.obligationIds,
        uncertainty: e.uncertainty, exactRequiredFragments: e.exactFragments.map(body) } }
    })
    const pending = new Map<string, any>()
    for (const p of previous?.payload.pending ?? []) pending.set(p.id, p)
    for (const m of actual.pendingNativeMessages) {
      const p = { id: randomUUID(), kind: "event", source: null,
        description: `Native input ${m.messageId} remains outside cut; original subject/body retained in ${actual.locatorId}`, responsibleTask: governingTask }
      pending.set(p.id, p)
    }
    for (const description of extracted.pending) {
      const p = { id: randomUUID(), kind: "obligation", source: null, description, responsibleTask: governingTask }; pending.set(p.id, p)
    }
    if (pending.size > 256) nativeUnavailable()
    const report = body(contextJSON({ kind: "controller-context-not-executor-result", controllerContinuation: key, governingTask,
      source: nativeSource, frontier, statement: "No approval, acceptance, integration, verification or physical completion is asserted" }))
    const data = { profile: parse(this.profileJSON), previous: previousRef === null ? null : { key: previousRef.key, digest: previousRef.digest },
      source: nativeSource, frontier, entries, gaps: [], pending: [...pending.values()], report }
    if (!Array.isArray(basis.reusableBodies)) nativeUnavailable()
    const reusable = new Map(basis.reusableBodies.map((b: any) => [b.digest, b.byteLength.value]))
    const attachments = [...bodies].filter(([digest, text]) => {
      const existing = reusable.get(digest)
      if (existing !== undefined && BigInt(existing as string) !== BigInt(Buffer.byteLength(text))) nativeUnavailable()
      return existing === undefined
    }).map(([digest, body]) => ({ digest, body }))
    const recordJSON = contextJSON({ formatVersion: 1, family: "controller-continuations", key, payload: data })
    const reference = { family: "controller-continuations", key, digest: hash(recordJSON) }
    const raw = contextJSON({ formatVersion: 3, projectId: scope.projectId, requestKey: randomUUID(), operation: "record_controller_continuation",
      payload: { key, data, bodies: attachments } })
    await this.custody.retain("controller-context-attempt", id, contextJSON({ formatVersion: 1, id, sessionID, sourceLocatorId: actual.locatorId,
      sourceDigest: extracted.sourceDigest, profileJSON: this.profileJSON, raw, recordJSON, reference }))
    return id
  }
  async publish(sessionID: string, id: string, signal: AbortSignal) {
    if (!uuidPattern.test(id)) nativeUnavailable()
    const raw = await this.custody.read("controller-context-attempt", id)
    if (raw === undefined) nativeUnavailable()
    const job = closedObject(parse(raw), ["formatVersion", "id", "sessionID", "sourceLocatorId", "sourceDigest", "profileJSON", "raw", "recordJSON", "reference"])
    if (!isLosslessNumber(job.formatVersion) || job.formatVersion.value !== "1" || job.id !== id || job.sessionID !== sessionID
      || job.profileJSON !== this.profileJSON || contextJSON(job) !== raw) nativeUnavailable()
    const source = await this.sources.read(sessionID, job.sourceLocatorId, job.sourceDigest)
    if (source.scope.scope !== "controller") nativeUnavailable()
    const channel = await this.registry.contextChannel(sessionID, signal), command = parse(job.raw)
    if (command.operation !== "record_controller_continuation" || command.payload.key.controllerId !== source.scope.controllerId) nativeUnavailable()
    const reply = await sendContext(channel, "commands", job.raw, signal)
    if (reply.kind !== "settled" || reply.outcome?.kind !== "success" || reply.identity?.requestKey !== command.requestKey) {
      const code = reply.outcome?.code ?? reply.code
      throw new Error(`controller continuation refused: ${typeof code === "string" && /^[A-Z_]{1,64}$/.test(code) ? code : "unavailable"}`)
    }
    const record = await sendContext(channel, "reads", contextJSON({ formatVersion: 2, projectId: channel.projectID,
      operation: "read_controller_continuations", subject: { key: job.reference.key, digest: job.reference.digest } }), signal)
    if (record.kind !== "read" || contextJSON(record.data) !== job.recordJSON || hash(job.recordJSON) !== job.reference.digest) nativeUnavailable()
    const frame = await this.registry.controllerContext(sessionID, this.profileJSON, signal)
    if (frame.previousReferenceJSON !== contextJSON(job.reference)) nativeUnavailable()
    await this.custody.retain("controller-context-confirmed", id, contextJSON({ formatVersion: 1, id, reference: job.reference }))
    return Object.freeze({ reference: job.reference, source, frame })
  }
}
