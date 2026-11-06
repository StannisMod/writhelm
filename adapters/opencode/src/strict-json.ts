// SPDX-License-Identifier: AGPL-3.0-only
import { parse, LosslessNumber } from "lossless-json"

/** Shared GUI/server byte-preserving parser. No Electron, host startup, credentials or schema fetch. */
export function exactJSON(text: string, fail: () => never): any {
  const check = (value: unknown) => { if (!value) fail() }
  check(text.isWellFormed() && !text.startsWith("\ufeff") && new TextEncoder().encode(text).length <= 4 * 1024 * 1024)
  let nodes = 0
  return parse(text, (key, value) => {
    check(++nodes <= 500000); check(key.isWellFormed())
    if (typeof value === "string") check(value.isWellFormed())
    return value
  }, { onDuplicateKey: fail, parseNumber: value => {
    check(/^(0|-?[1-9][0-9]*)$/.test(value))
    const number = BigInt(value); check(number >= -9223372036854775808n && number <= 9223372036854775807n)
    return new LosslessNumber(value)
  } })
}
export function exactLoopback(origin: string, fail: () => never): string {
  const url = new URL(origin)
  if (!(url.protocol === "http:" && ["127.0.0.1", "[::1]"].includes(url.hostname) && url.port && !url.username && !url.password
    && !url.search && !url.hash && url.pathname === "/" && url.origin === origin)) fail()
  return origin
}
