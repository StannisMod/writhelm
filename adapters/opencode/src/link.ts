// SPDX-License-Identifier: AGPL-3.0-only
export type ExactLink = { project: string; requestId: string; requestDigest: string; packetDigest: string }

const uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
const digest = "[0-9a-f]{64}"
const closed = new RegExp(`^(?:writhelm-review/v1/|writhelm://review/)(${uuid})/(${uuid})/(${digest})/(${digest})$`)

/** A virtual reference, never a file/URL to resolve. Legacy URI input is not a delivered-link claim. */
export function exactLink(href: string): ExactLink | undefined {
  if (typeof href !== "string" || href.length > 256) return
  const match = closed.exec(href)
  if (!match || match[0] !== href) return // JS $ alone also matches before a trailing newline.
  return { project: match[1], requestId: match[2], requestDigest: match[3], packetDigest: match[4] }
}

/** Claim malformed reserved references before the host file fallback; NEVER accept decoded aliases. */
export function ownsReviewReference(href: string): boolean {
  if (typeof href !== "string") return false
  let candidate = href
  // Each changing %HH round removes at least two code units. The extra state checks its result;
  // inspect the whole input so a truncated prefix cannot hide or invent a namespace boundary.
  const states = Math.floor(href.length / 2) + 1
  for (let n = 0; n < states; n++) {
    if (/^writhelm-review(?:[/\\:?#%]|$)/i.test(candidate) || /^writhelm:/i.test(candidate)) return true
    const decoded = candidate.replace(/%([0-9a-f]{2})/gi, (_, hex: string) => String.fromCharCode(parseInt(hex, 16)))
    if (decoded === candidate) break
    candidate = decoded
  }
  return false
}
