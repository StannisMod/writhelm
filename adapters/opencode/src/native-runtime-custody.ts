// SPDX-License-Identifier: AGPL-3.0-only
import * as fs from "node:fs/promises"
import { constants } from "node:fs"
import path from "node:path"
import { createHash, randomUUID } from "node:crypto"

function unavailable(): never { throw new Error("protected native runtime unavailable") }
const hash = (s: string) => createHash("sha256").update(s, "utf8").digest("hex")

/** Explicit Linux operational custody, not an authority/credential database in canonical Git.
 * Every operation revalidates root identity. Existing insecure roots are refused, not chmod-repaired.
 * The profile must separately qualify persistence; sync/process evidence is not power-loss proof. */
export class LinuxNativeCustody {
  private identity: string | undefined
  private initialized = false
  private readonly forbiddenAreas: readonly string[]
  constructor(readonly root: string, forbiddenAreas: readonly string[], private readonly capacity = 1024) {
    if (!Number.isInteger(capacity) || capacity < 1 || capacity > 1024) unavailable()
    this.forbiddenAreas = Object.freeze([...forbiddenAreas])
  }
  async initialize(): Promise<void> {
    if (process.platform !== "linux" || !path.isAbsolute(this.root) || this.root !== path.resolve(this.root)
      || !this.forbiddenAreas.length) unavailable()
    for (const work of this.forbiddenAreas) await this.requireOutside(work)
    // Reject unsafe existing ancestors BEFORE recursive mkdir can follow a link or create files.
    await this.ancestors(true)
    await fs.mkdir(this.root, { recursive: true, mode: 0o700 })
    await this.verify()
    this.initialized = true
  }
  /** Also used when trusted bootstrap binds a newly created work area after startup. Neither
   * nesting direction is safe: coding inside the secret root is not separation either. */
  async requireOutside(work: string): Promise<void> {
    if (!path.isAbsolute(work) || !path.isAbsolute(this.root)) unavailable()
    const physical = await fs.realpath(work), root = path.resolve(this.root)
    const within = (parent: string, child: string) => {
      const relative = path.relative(parent, child)
      return !relative || !relative.startsWith(".." + path.sep) && relative !== ".." && !path.isAbsolute(relative)
    }
    if (within(physical, root) || within(root, physical)) unavailable()
  }
  private async ancestors(allowMissing = false) {
    if (process.platform !== "linux" || !process.getuid) unavailable()
    let current = path.parse(this.root).root
    for (const part of this.root.slice(current.length).split(path.sep).filter(Boolean)) {
      current = path.join(current, part)
      let s
      try { s = await fs.lstat(current) }
      catch (error) { if (allowMissing && (error as NodeJS.ErrnoException).code === "ENOENT") return; throw error }
      if (!s.isDirectory() || s.isSymbolicLink()) unavailable()
      // A root-owned sticky ancestor permits private test/profile directories without permitting
      // an ordinary writable ancestor. This is not hostile same-UID namespace confinement.
      if (s.mode & 0o022 && !(s.uid === 0 && s.mode & 0o1000)) unavailable()
      if (s.uid !== 0 && s.uid !== process.getuid()) unavailable()
    }
  }
  private async verify() {
    await this.ancestors()
    const s = await fs.lstat(this.root)
    if (s.uid !== process.getuid!() || s.mode & 0o077 || await fs.realpath(this.root) !== path.resolve(this.root)) unavailable()
    const id = `${s.dev}:${s.ino}`
    if (this.identity !== undefined && this.identity !== id) unavailable()
    this.identity = id
    return s
  }
  private name(kind: string, key: string) {
    if (!/^[a-z][a-z-]{0,31}$/.test(kind) || !key.isWellFormed() || !key || Buffer.byteLength(key) > 1024) unavailable()
    return `${kind}-${hash(key)}.json`
  }
  async read(kind: string, key: string): Promise<string | undefined> {
    if (!this.initialized) unavailable()
    await this.verify()
    return this.readName(this.name(kind, key))
  }
  private async readName(name: string): Promise<string | undefined> {
    const file = path.join(this.root, name)
    let handle: fs.FileHandle
    try { handle = await fs.open(file, constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK) }
    catch (error) { if ((error as NodeJS.ErrnoException).code === "ENOENT") return undefined; throw new Error("native custody read refused") }
    try {
      const before = await handle.stat()
      if (!before.isFile() || before.uid !== process.getuid!() || before.mode & 0o077 || before.nlink !== 1 || before.size > 2 * 1024 * 1024) unavailable()
      const bytes = await handle.readFile()
      const after = await handle.stat()
      if (bytes.length !== before.size || after.size !== before.size || after.mtimeMs !== before.mtimeMs || after.ctimeMs !== before.ctimeMs) unavailable()
      await this.verify()
      return new TextDecoder("utf-8", { fatal: true }).decode(bytes)
    } finally { await handle.close() }
  }
  /** Bounded protected startup inventory, not a model artifact/path lookup. Unknown crash leftovers
   * refuse; a missing cache must not make previously managed native sessions unmanaged. */
  async scan(kind: string): Promise<readonly Readonly<{ keyDigest: string; raw: string }>[]> {
    if (!this.initialized || !/^[a-z][a-z-]{0,31}$/.test(kind)) unavailable()
    await this.verify()
    const pattern = new RegExp(`^${kind}-([0-9a-f]{64})\\.json$`)
    const found: Array<Readonly<{ keyDigest: string; raw: string }>> = []
    let count = 0, bytes = 0
    for await (const entry of await fs.opendir(this.root)) {
      if (!/^[a-z][a-z-]{0,31}-[0-9a-f]{64}\.json$/.test(entry.name) || ++count > this.capacity) unavailable()
      const stat = await fs.lstat(path.join(this.root, entry.name))
      if (!stat.isFile() || stat.isSymbolicLink() || stat.uid !== process.getuid!() || stat.mode & 0o077
        || stat.nlink !== 1 || stat.size > 2 * 1024 * 1024) unavailable()
      bytes += stat.size; if (bytes > 64 * 1024 * 1024) unavailable()
      const match = pattern.exec(entry.name)
      if (match) {
        const raw = await this.readName(entry.name); if (raw === undefined) unavailable()
        found.push(Object.freeze({ keyDigest: match[1]!, raw }))
      }
    }
    await this.verify()
    return Object.freeze(found)
  }
  /** Complete bytes are synced before exclusive publication. Existing identity never dispatches
   * again. A crash or conflicting/partial leftover stays held, never becomes absence by cleanup. */
  async retain(kind: string, key: string, raw: string): Promise<boolean> {
    return this.write(kind, key, raw)
  }
  async replace(kind: string, key: string, expectedDigest: string, raw: string): Promise<boolean> {
    if (!/^[0-9a-f]{64}$/.test(expectedDigest)) unavailable()
    return this.write(kind, key, raw, expectedDigest)
  }
  private async write(kind: string, key: string, raw: string, expectedDigest?: string): Promise<boolean> {
    if (!this.initialized) unavailable()
    await this.verify()
    const lock = path.join(this.root, ".native-write-lock")
    try { await fs.mkdir(lock, { mode: 0o700 }) } catch { unavailable() }
    try { return await this.publish(kind, key, raw, expectedDigest) } finally { await fs.rmdir(lock) }
  }
  private async publish(kind: string, key: string, raw: string, expectedDigest?: string): Promise<boolean> {
    if (!raw.isWellFormed() || Buffer.byteLength(raw) > 2 * 1024 * 1024) unavailable()
    await this.verify()
    const previous = await this.read(kind, key)
    if (expectedDigest !== undefined) {
      if (previous === undefined || hash(previous) !== expectedDigest) unavailable()
      if (previous === raw) return false
    } else if (previous !== undefined) { if (previous !== raw) unavailable(); return false }
    let count = 0, bytes = 0
    for await (const entry of await fs.opendir(this.root)) {
      if (entry.name === ".native-write-lock") continue
      if (!/^[a-z][a-z-]{0,31}-[0-9a-f]{64}\.json$/.test(entry.name)) unavailable()
      if (++count > this.capacity) unavailable()
      const s = await fs.lstat(path.join(this.root, entry.name))
      if (!s.isFile() || s.isSymbolicLink() || s.uid !== process.getuid!() || s.mode & 0o077 || s.size > 2 * 1024 * 1024) unavailable()
      bytes += s.size
      if (bytes > 64 * 1024 * 1024) unavailable()
    }
    if (previous === undefined && count >= this.capacity || Buffer.byteLength(raw) > 64 * 1024 * 1024 - bytes + Buffer.byteLength(previous ?? "")) unavailable()
    const temporary = path.join(this.root, `.native-${randomUUID()}.tmp`)
    const destination = path.join(this.root, this.name(kind, key))
    const handle = await fs.open(temporary, "wx", 0o600)
    try { await handle.writeFile(raw, "utf8"); await handle.sync() } finally { await handle.close() }
    let created = false
    try {
      await this.verify()
      if (expectedDigest !== undefined) { await fs.rename(temporary, destination); created = true }
      else {
        try { await fs.link(temporary, destination); created = true }
        catch (error) { if ((error as NodeJS.ErrnoException).code !== "EEXIST") throw error }
      }
    } finally { try { await fs.unlink(temporary) } catch (error) { if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error } }
    const directory = await fs.open(this.root, "r")
    try { await directory.sync() } finally { await directory.close() }
    if (await this.read(kind, key) !== raw) unavailable()
    return created
  }
}
