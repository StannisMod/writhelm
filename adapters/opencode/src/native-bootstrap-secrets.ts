// SPDX-License-Identifier: AGPL-3.0-only
import { randomBytes } from "node:crypto"
import { isLosslessNumber } from "lossless-json"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { exactJSON } from "./strict-json.js"
import { closedObject, nativeUnavailable } from "./native-runtime-client.js"

/** Trusted local bootstrap only. Persist CSPRNG peer proofs BEFORE starting either endpoint.
 * This is not a model tool, secret RPC, discovery default or implicit rotation/reset operation. */
export async function prepareNativePeerProofs(custody: LinuxNativeCustody,
  supplied?: Readonly<{ serviceProof: Uint8Array; transportProof: Uint8Array }>) {
  const load = async (name: "service" | "transport", provided?: Uint8Array) => {
    if (provided && provided.length !== 32) nativeUnavailable()
    let raw = await custody.read("native-peer", name)
    if (raw === undefined) {
      const secret = provided ? Buffer.from(provided) : randomBytes(32)
      try {
        raw = JSON.stringify({ formatVersion: 1, kind: "native-peer-proof-v1", secret: secret.toString("base64url") })
        await custody.retain("native-peer", name, raw)
      } finally { secret.fill(0) }
    }
    const value = closedObject(exactJSON(raw, nativeUnavailable), ["formatVersion", "kind", "secret"])
    if (!isLosslessNumber(value.formatVersion) || value.formatVersion.value !== "1" || value.kind !== "native-peer-proof-v1"
      || typeof value.secret !== "string" || !/^[A-Za-z0-9_-]{43}$/.test(value.secret)) nativeUnavailable()
    const secret = Buffer.from(value.secret, "base64url")
    if (secret.length !== 32 || secret.toString("base64url") !== value.secret
      || provided && !secret.equals(Buffer.from(provided))) { secret.fill(0); nativeUnavailable() }
    return secret
  }
  const serviceProof = await load("service", supplied?.serviceProof)
  try { return Object.freeze({ serviceProof, transportProof: await load("transport", supplied?.transportProof) }) }
  catch (error) { serviceProof.fill(0); throw error }
}
