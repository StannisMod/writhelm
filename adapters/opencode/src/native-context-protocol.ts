// SPDX-License-Identifier: AGPL-3.0-only
import { setTimeout as wait } from "node:timers/promises"
import type { BoundWorkChannel } from "./connected-tools.js"
import { exactJSON } from "./strict-json.js"
import { nativeUnavailable } from "./native-runtime-client.js"

/** Bounded current-authorized retry of the EXACT retained canonical request/read. This is not
 * retry of a native/model effect, new-key generation or publication-barrier recovery. */
export async function sendContext(channel: BoundWorkChannel, route: "commands" | "reads", raw: string, signal: AbortSignal): Promise<any> {
  for (let retry = 0; retry < 3; retry++) {
    try {
      const reply = exactJSON(await channel.send(route, raw, signal), nativeUnavailable)
      if (reply.kind !== "error" || !["RATE_LIMIT", "UNAVAILABLE", "PUBLICATION_UNKNOWN"].includes(reply.code) || retry === 2) return reply
    } catch (error) { if (signal.aborted || retry === 2) throw error }
    await wait(1100, undefined, { signal })
  }
  nativeUnavailable()
}
