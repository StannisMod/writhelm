// SPDX-License-Identifier: AGPL-3.0-only
import { isLosslessNumber } from "lossless-json"
import { nativeUnavailable } from "./native-runtime-client.js"

/** CD03 outer framing. Native JSON stays inside strings: never round-trip its numbers through
 * domain Int64 parsing and call that verification of native metadata precision. */
export function contextJSON(value: unknown, maximum = 2 * 1024 * 1024): string {
  const chunks: string[] = []; let bytes = 0, nodes = 0
  const append = (s: string) => { bytes += Buffer.byteLength(s); if (bytes > maximum) nativeUnavailable(); chunks.push(s) }
  const quote = (s: string) => {
    if (!s.isWellFormed()) nativeUnavailable()
    let out = '"'
    for (const ch of s) {
      const code = ch.codePointAt(0)!
      out += code < 32 ? `\\u${code.toString(16).padStart(4, "0")}` : ch === '"' ? '\\"' : ch === "\\" ? "\\\\" : ch
    }
    return out + '"'
  }
  const order = (a: string, b: string) => {
    const x = Array.from(a, c => c.codePointAt(0)!), y = Array.from(b, c => c.codePointAt(0)!)
    for (let i = 0; i < Math.min(x.length, y.length); i++) if (x[i] !== y[i]) return x[i]! - y[i]!
    return x.length - y.length
  }
  const write = (v: unknown, depth: number): void => {
    if (++nodes > 100000 || depth > 64) nativeUnavailable()
    if (v === null || typeof v === "boolean") { append(String(v)); return }
    if (typeof v === "string") { append(quote(v)); return }
    if (typeof v === "number" || typeof v === "bigint" || isLosslessNumber(v)) {
      if (typeof v === "number" && !Number.isSafeInteger(v)) nativeUnavailable()
      const decimal = isLosslessNumber(v) ? v.value : String(v)
      if (!/^(0|-?[1-9][0-9]*)$/.test(decimal)) nativeUnavailable()
      const n = BigInt(decimal)
      if (n < -9223372036854775808n || n > 9223372036854775807n) nativeUnavailable()
      append(decimal); return
    }
    if (Array.isArray(v)) {
      if (v.length > 4096) nativeUnavailable()
      append("[")
      for (let i = 0; i < v.length; i++) { if (i) append(","); write(v[i], depth + 1) }
      append("]"); return
    }
    if (!v || typeof v !== "object" || Object.getPrototypeOf(v) !== Object.prototype && Object.getPrototypeOf(v) !== null) nativeUnavailable()
    const fields = Object.keys(v).sort(order)
    if (fields.length > 4096) nativeUnavailable()
    append("{")
    fields.forEach((key, i) => { if (i) append(","); append(quote(key)); append(":"); write((v as Record<string, unknown>)[key], depth + 1) })
    append("}")
  }
  write(value, 0)
  return chunks.join("")
}
