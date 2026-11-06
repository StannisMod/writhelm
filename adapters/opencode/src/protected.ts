// SPDX-License-Identifier: AGPL-3.0-only
import { promises as fs, constants } from "node:fs"
import type { FileHandle } from "node:fs/promises"
import path from "node:path"
import { randomUUID } from "node:crypto"
import { requireThat, Fault } from "./contracts.js"
import { profileResult, type Json } from "./wire.js"

export interface ProtectedFiles { read(): Promise<Json>; readAgent(): Promise<Json>; write(value: Json): Promise<void>; writeAgent(value: Json): Promise<void>; close(): Promise<void> }
/** Explicit Linux/procfs adapter. No Unix paths or POSIX permission assumption in other OS adapters. */
export class LinuxProtectedFiles implements ProtectedFiles {
  private constructor(readonly root: string, private readonly fd: FileHandle, private readonly identity: string) {}
  static async open(root: string, forbidden: readonly string[], create = false): Promise<LinuxProtectedFiles> {
    requireThat(process.platform === "linux" && typeof process.getuid === "function", "UNSUPPORTED_PLATFORM")
    root = path.resolve(root)
    for (const project of forbidden) {
      const relative = path.relative(path.resolve(project), root)
      requireThat(relative !== "" && (relative.startsWith(".." + path.sep) || path.isAbsolute(relative)), "PROTECTION_FAILED")
    }
    const uid = process.getuid()
    let current = path.parse(root).root
    for (const part of root.slice(current.length).split(path.sep).filter(Boolean).slice(0, -1)) {
      current = path.join(current, part); const st = await fs.lstat(current)
      requireThat(st.isDirectory() && !st.isSymbolicLink() && (st.uid === uid || st.uid === 0), "PROTECTION_FAILED")
      if (st.uid === 0 && (st.mode & 0o022)) requireThat((st.mode & 0o1000) !== 0, "PROTECTION_FAILED")
    }
    if (create) await fs.mkdir(root, { mode: 0o700 }) // no recursive creation or existing-root reset
    const st = await fs.lstat(root)
    requireThat(st.isDirectory() && !st.isSymbolicLink() && st.uid === uid && (st.mode & 0o777) === 0o700, "PROTECTION_FAILED")
    const fd = await fs.open(root, constants.O_RDONLY | constants.O_DIRECTORY | constants.O_NOFOLLOW)
    try { const opened = await fd.stat(); requireThat(opened.ino === st.ino && opened.dev === st.dev, "PROTECTION_FAILED") }
    catch (e) { await fd.close(); throw e }
    return new LinuxProtectedFiles(root, fd, `${st.dev}:${st.ino}`)
  }
  private async bound() {
    const st = await fs.lstat(this.root)
    requireThat(st.isDirectory() && !st.isSymbolicLink() && `${st.dev}:${st.ino}` === this.identity && st.uid === process.getuid!() && (st.mode & 0o777) === 0o700, "PROTECTION_FAILED")
  }
  private file(name: string) { return `/proc/self/fd/${this.fd.fd}/${name}` }
  read(): Promise<Json> { return this.readFile("profile.json") }
  readAgent(): Promise<Json> { return this.readFile("agent.json") }
  private async readFile(name: "profile.json" | "agent.json"): Promise<Json> {
    await this.bound()
    // O_NOFOLLOW alone still waits for a FIFO writer. Nonblocking open makes type refusal
    // handle-bound even if a regular path is replaced with a FIFO immediately before open.
    const file = await fs.open(this.file(name), constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK)
    try {
      const before = await file.stat()
      requireThat(before.isFile() && before.nlink === 1 && before.uid === process.getuid!() && (before.mode & 0o777) === 0o600 && before.size <= 256 * 1024, "PROTECTION_FAILED")
      const buffer = Buffer.alloc(256 * 1024 + 1); let total = 0
      while (total < buffer.length) { const r = await file.read(buffer, total, buffer.length - total, total); if (!r.bytesRead) break; total += r.bytesRead }
      requireThat(total <= 256 * 1024, "PROTECTION_FAILED")
      const data = buffer.subarray(0, total), after = await file.stat()
      requireThat(data.length === before.size && after.ino === before.ino && after.dev === before.dev && after.size === before.size && after.mtimeMs === before.mtimeMs && after.nlink === 1 && after.uid === before.uid && after.mode === before.mode, "PROTECTION_FAILED")
      await this.bound()
      return profileResult(new TextDecoder("utf-8", { fatal: true }).decode(data))
    } finally { await file.close() }
  }
  write(value: Json): Promise<void> { return this.writeFile(value, "profile.json") }
  writeAgent(value: Json): Promise<void> {
    const agent = { credentialId: value.agent.credentialId, token: value.agent.token }
    return this.writeFile({ formatVersion: 1, origin: value.origin, storeId: value.storeId, projectId: value.projectId, principalId: value.agent.principalId, active: agent, agent: { principalId: value.agent.principalId, ...agent }, pending: null }, "agent.json")
  }
  private async writeFile(value: Json, name: "profile.json" | "agent.json"): Promise<void> {
    await this.bound(); const raw = JSON.stringify(value)
    requireThat(Buffer.byteLength(raw) <= 256 * 1024, "PROTECTION_FAILED"); profileResult(raw)
    const temporary = "pending-" + randomUUID() + ".json"
    const file = await fs.open(this.file(temporary), constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW, 0o600)
    try { await file.writeFile(raw, "utf8"); await file.sync() } finally { await file.close() }
    // Never follow an existing symlink. Refuse an insecure replaced file rather than repairing it.
    try { const st = await fs.lstat(this.file(name)); requireThat(st.isFile() && st.nlink === 1 && st.uid === process.getuid!() && (st.mode & 0o777) === 0o600, "PROTECTION_FAILED") }
    catch (e) { if (!(e instanceof Error && "code" in e && e.code === "ENOENT")) throw e }
    await this.bound(); await fs.rename(this.file(temporary), this.file(name)); await this.fd.sync(); await this.readFile(name)
  }
  async close() { await this.fd.close() }
}
export async function openProtected(root: string, projects: readonly string[], create = false): Promise<ProtectedFiles> {
  if (process.platform !== "linux") throw new Fault("UNSUPPORTED_PLATFORM")
  return LinuxProtectedFiles.open(root, projects, create)
}
