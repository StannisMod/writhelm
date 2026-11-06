// SPDX-License-Identifier: AGPL-3.0-only
import { Schema } from "effect"
import { Ipc } from "@opencode/plugin/gui"

// Actual SDK codecs, not host declarations. Strictness is retained even if a host's default Struct
// decoding strips excess fields: the declaration validates the original input with strict options.
function closed<S extends Schema.ConstraintCodec<unknown, unknown>>(s: S) {
  return Schema.declare<S["Type"]>((u): u is S["Type"] => {
    try {
      if (new TextEncoder().encode(JSON.stringify(u)).length > 4 * 1024 * 1024 + 65536) return false
      Schema.decodeUnknownSync(s, { onExcessProperty: "error" })(u)
      return true
    } catch { return false }
  })
}
const text = (n: number) => Schema.String.check(Schema.isMaxLength(n))
const uuid = text(36).check(Schema.isPattern(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/))
const digest = text(64).check(Schema.isPattern(/^[0-9a-f]{64}$/))
export const Session = Schema.Struct({ key: text(256), id: text(256), server: text(256), directory: text(4096), projectRoot: text(4096) })
export type Session = typeof Session.Type
export const Codes = ["OK", "UNCONFIGURED", "INVALID_INPUT", "FORBIDDEN", "UNAUTHENTICATED", "STALE_VIEW", "EXPIRED_VIEW", "BUSY", "UNAVAILABLE", "INCOMPLETE", "UNSUPPORTED_PLATFORM", "PROTECTION_FAILED", "HELPER_UNAVAILABLE", "SETUP_UNKNOWN", "ROTATION_UNKNOWN", "PUBLICATION_UNKNOWN", "IDENTITY_CONFLICT", "INTEGRITY", "DOMAIN_REJECTED"] as const
export type Code = typeof Codes[number]
export const Reply = closed(Schema.Struct({ ok: Schema.Boolean, code: Schema.Literals(Codes), handle: text(36), bundleJSON: text(4 * 1024 * 1024), pageJSON: text(4 * 1024 * 1024), resultJSON: text(4 * 1024 * 1024), pending: Schema.Boolean }))
export type Reply = typeof Reply.Type
export const Inputs = {
  status: closed(Schema.Struct({ session: Session })),
  connect: closed(Schema.Struct({ session: Session })),
  pending: closed(Schema.Struct({ session: Session, cursor: text(36) })),
  open: closed(Schema.Struct({ session: Session, requestId: uuid, requestDigest: digest, packetDigest: digest })),
  answer: closed(Schema.Struct({ session: Session, handle: uuid, choice: Schema.Literals(["approve", "reject", "changes-required"]), comments: Schema.Array(Schema.Struct({ itemId: uuid, text: text(4096) })).check(Schema.isMaxLength(16)) })),
  reconcile: closed(Schema.Struct({ session: Session })), rotate: closed(Schema.Struct({ session: Session })),
  setup: closed(Schema.Struct({ session: Session, origin: text(4096) })),
  dismiss: closed(Schema.Struct({ session: Session, handle: text(36) })),
}
export const Review = Ipc.define({ id: "writhelm-opencode.review", methods: {
  status: { input: Inputs.status, output: Reply }, connect: { input: Inputs.connect, output: Reply },
  pending: { input: Inputs.pending, output: Reply }, open: { input: Inputs.open, output: Reply },
  answer: { input: Inputs.answer, output: Reply }, reconcile: { input: Inputs.reconcile, output: Reply },
  rotate: { input: Inputs.rotate, output: Reply }, setup: { input: Inputs.setup, output: Reply },
  dismiss: { input: Inputs.dismiss, output: Reply },
} })
export type Input<K extends keyof typeof Inputs> = (typeof Inputs)[K]["Type"]
export const reply = (code: Code = "OK", fields: Partial<Reply> = {}): Reply => ({ ok: code === "OK", code, handle: "", bundleJSON: "", pageJSON: "", resultJSON: "", pending: false, ...fields })
export class Fault extends Error { constructor(readonly code: Code) { super(code) } }
export function requireThat(ok: unknown, code: Code = "INVALID_INPUT"): asserts ok { if (!ok) throw new Fault(code) }
