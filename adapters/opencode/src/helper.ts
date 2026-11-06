// SPDX-License-Identifier: AGPL-3.0-only
import { randomBytes, randomUUID, createHash } from "node:crypto"
import { spawn } from "node:child_process"
import path from "node:path"
import { Fault, requireThat, type Session } from "./contracts.js"
import { loopback, exact, type Json } from "./wire.js"

export interface JavaRuntime { executable: string; classpath: readonly string[] }
export function verifier(store: string, project: string, principal: string, credential: string, secret: Uint8Array): string {
  const hash = createHash("sha256")
  const frame = (bytes: Uint8Array) => { const size = Buffer.alloc(4); size.writeUInt32BE(bytes.length); hash.update(size); hash.update(bytes) }
  frame(Buffer.from("writhelm.credential-verifier.v1"))
  for (const id of [store, project, principal, credential]) hash.update(Buffer.from(id.replaceAll("-", ""), "hex"))
  frame(secret); return hash.digest("hex")
}
export function credential(store: string, project: string, principal: string) {
  const credentialId = randomUUID(), secret = randomBytes(32)
  try { return { credentialId, token: `wh1.${store}.${credentialId}.${secret.toString("base64url")}`, digest: verifier(store, project, principal, credentialId, secret) } }
  finally { secret.fill(0) }
}
function tokenDigest(profile: Json, key: Json, principal: string): string {
  requireThat(typeof key.token === "string" && key.token.startsWith(`wh1.${profile.storeId}.${key.credentialId}.`), "PROTECTION_FAILED")
  const encoded = key.token.split(".")[3]
  requireThat(/^[A-Za-z0-9_-]{43}$/.test(encoded), "PROTECTION_FAILED")
  const secret = Buffer.from(encoded, "base64url")
  try { requireThat(secret.length === 32 && secret.toString("base64url") === encoded, "PROTECTION_FAILED"); return verifier(profile.storeId, profile.projectId, principal, key.credentialId, secret) }
  finally { secret.fill(0) }
}
/** A protected retry file is not a general owner-command forwarding capability. */
export function validateRetained(profile: Json) {
  tokenDigest(profile, profile.active, profile.principalId); tokenDigest(profile, profile.agent, profile.agent.principalId)
  const pending = profile.pending; if (!pending) return
  const closed = (o: Json, fields: string[]) => requireThat(o && typeof o === "object" && !Array.isArray(o) && Object.keys(o).sort().join(",") === fields.sort().join(","), "PROTECTION_FAILED")
  const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/, digest = /^[0-9a-f]{64}$/
  if (pending.kind === "setup") {
    requireThat(pending.commandJSON === "" && pending.replacement === null, "PROTECTION_FAILED")
    exact(pending.registrationJSON); const r = JSON.parse(pending.registrationJSON)
    closed(r, ["formatVersion", "mode", "storeDirectory", "storeId", "projectId", "ownerPrincipalId", "ownerCredentialId", "ownerVerifierDigest", "agentPrincipalId", "agentCredentialId", "agentVerifierDigest"])
    requireThat(r.formatVersion === 1 && r.mode === "initialize" && r.storeDirectory === pending.storeDirectory && r.storeId === profile.storeId && r.projectId === profile.projectId && r.ownerPrincipalId === profile.principalId && r.ownerCredentialId === profile.active.credentialId && r.agentPrincipalId === profile.agent.principalId && r.agentCredentialId === profile.agent.credentialId, "PROTECTION_FAILED")
    requireThat(r.ownerVerifierDigest === tokenDigest(profile, profile.active, profile.principalId) && r.agentVerifierDigest === tokenDigest(profile, profile.agent, profile.agent.principalId), "PROTECTION_FAILED"); return
  }
  requireThat(pending.registrationJSON === "" && pending.storeDirectory === "", "PROTECTION_FAILED")
  exact(pending.commandJSON); const c = JSON.parse(pending.commandJSON)
  closed(c, ["formatVersion", "projectId", "requestKey", "operation", "payload"])
  requireThat(c.projectId === profile.projectId && uuid.test(c.requestKey), "PROTECTION_FAILED")
  if (pending.kind === "answer") {
    requireThat(c.formatVersion === 1 && c.operation === "resolve_approval" && pending.replacement === null, "PROTECTION_FAILED")
    closed(c.payload, ["requestId", "requestDigest", "packetDigest", "answer"])
    requireThat(uuid.test(c.payload.requestId) && digest.test(c.payload.requestDigest) && digest.test(c.payload.packetDigest), "PROTECTION_FAILED")
    const a = c.payload.answer
    requireThat(["approve", "reject", "changes-required"].includes(a.kind), "PROTECTION_FAILED")
    closed(a, a.kind === "changes-required" ? ["kind", "comments"] : ["kind"])
    if (a.kind === "changes-required") {
      requireThat(Array.isArray(a.comments) && a.comments.length > 0 && a.comments.length <= 16, "PROTECTION_FAILED")
      for (const comment of a.comments) { closed(comment, ["itemId", "text"]); requireThat(uuid.test(comment.itemId) && typeof comment.text === "string" && comment.text.trim() && Buffer.byteLength(comment.text) <= 4096, "PROTECTION_FAILED") }
      requireThat(new Set(a.comments.map((x: Json) => x.itemId)).size === a.comments.length, "PROTECTION_FAILED")
    }
  } else {
    requireThat(pending.kind === "rotation" && pending.replacement && c.formatVersion === 2 && c.operation === "rotate_credential", "PROTECTION_FAILED")
    closed(c.payload, ["credentialId", "expectedGeneration", "newCredentialId", "verifierVersion", "newVerifierDigest"])
    requireThat(c.payload.credentialId === profile.active.credentialId && c.payload.expectedGeneration === 0 && c.payload.newCredentialId === pending.replacement.credentialId && c.payload.verifierVersion === 1 && c.payload.newVerifierDigest === tokenDigest(profile, pending.replacement, profile.principalId), "PROTECTION_FAILED")
  }
}
export function prepareSetup(origin: string, session: Session, storeDirectory: string): Json {
  loopback(origin); requireThat(path.isAbsolute(storeDirectory))
  const storeId = randomUUID(), projectId = randomUUID(), principalId = randomUUID(), agentId = randomUUID()
  const owner = credential(storeId, projectId, principalId), agent = credential(storeId, projectId, agentId)
  const registration = { formatVersion: 1, mode: "initialize", storeDirectory, storeId, projectId,
    ownerPrincipalId: principalId, ownerCredentialId: owner.credentialId, ownerVerifierDigest: owner.digest,
    agentPrincipalId: agentId, agentCredentialId: agent.credentialId, agentVerifierDigest: agent.digest }
  return { formatVersion: 1, origin, storeId, projectId, principalId,
    active: { credentialId: owner.credentialId, token: owner.token },
    agent: { principalId: agentId, credentialId: agent.credentialId, token: agent.token },
    pending: { kind: "setup", session, commandJSON: "", replacement: null, registrationJSON: JSON.stringify(registration), storeDirectory } }
}
/** Only public bootstrap/verifier fields cross this local helper boundary. No token on argv/stdin. */
export class PublicSetupRunner {
  private running = false
  constructor(private readonly runtime: JavaRuntime | undefined) {}
  async run(registration: Json, verify = false): Promise<void> {
    requireThat(this.runtime && path.isAbsolute(this.runtime.executable) && this.runtime.classpath.length > 0 && this.runtime.classpath.every(p => path.isAbsolute(p)), "HELPER_UNAVAILABLE")
    requireThat(!this.running, "BUSY")
    const raw = JSON.stringify({ ...registration, mode: verify ? "verify" : "initialize" })
    requireThat(Buffer.byteLength(raw) <= 16384 && !raw.includes("wh1."), "INVALID_INPUT")
    this.running = true
    const child = spawn(this.runtime.executable, ["-Xmx128m", "-XX:MaxMetaspaceSize=128m", "-XX:ActiveProcessorCount=2", "-cp", this.runtime.classpath.join(path.delimiter), "dev.writhelm.service.LocalSetup"], {
      stdio: ["pipe", "pipe", "pipe"], env: { PATH: process.env.PATH ?? "", JAVA_HOME: path.dirname(path.dirname(this.runtime.executable)) },
    })
    await new Promise<void>((resolve, reject) => {
      let stdout = "", stderrBytes = 0, settled = false
      const fail = (code: "SETUP_UNKNOWN" | "HELPER_UNAVAILABLE") => { if (!settled) { settled = true; reject(new Fault(code)) } }
      const timer = setTimeout(() => fail("SETUP_UNKNOWN"), 60000)
      child.on("error", () => { this.running = false; clearTimeout(timer); fail("HELPER_UNAVAILABLE") })
      child.stdout.on("data", b => { if (Buffer.byteLength(stdout) + b.length > 4096) { fail("SETUP_UNKNOWN"); return }; stdout += b.toString("utf8") })
      child.stderr.on("data", b => { stderrBytes += b.length; if (stderrBytes > 4096) fail("SETUP_UNKNOWN") })
      child.on("close", code => {
        this.running = false; clearTimeout(timer)
        if (settled) return
        try {
          const result = JSON.parse(stdout)
          requireThat(code === 0 && result.storeId === registration.storeId && ["initialized", "verified"].includes(result.kind), "SETUP_UNKNOWN")
          settled = true; resolve()
        } catch { fail("SETUP_UNKNOWN") }
      })
      child.stdin.on("error", () => fail("SETUP_UNKNOWN"))
      child.stdin.end(raw)
    })
  }
}
