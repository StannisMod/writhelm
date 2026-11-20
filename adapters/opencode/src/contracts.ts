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
export const WorkFamilies = ["task-heads", "task-revisions", "assignments", "assignment-replacements", "plans", "profiles", "conditions", "condition-confirmations", "continuations", "memory", "controller-continuations", "results", "verification-plans", "verification-reports", "evidence", "dispositions", "acceptances", "integration-intents", "integrations", "checkpoints", "retrospectives", "deliveries", "claims", "recoveries"] as const
// Closed read2 listing registry, deliberately narrower than the exact-record registry.
export const WorkListFamilies = ["task-heads", "assignments", "results", "verification-reports", "acceptances", "integrations", "controller-continuations", "assignment-replacements", "conditions", "condition-confirmations", "continuations"] as const
export type WorkFamily = typeof WorkFamilies[number]
const workFamily = Schema.Literals(WorkFamilies)
export const Session = Schema.Struct({ key: text(256), id: text(256), server: text(256), directory: text(4096), projectRoot: text(4096) })
const workSelector = { session: Session, family: workFamily, keyJSON: text(4096),
  digest: text(64).check(Schema.isPattern(/^(|[0-9a-f]{64})$/)),
  generation: text(19).check(Schema.isPattern(/^(|0|[1-9][0-9]{0,18})$/)) }
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
  answer: closed(Schema.Struct({ session: Session, handle: uuid, choice: Schema.Literals(["approve", "reject", "changes-required"]), reason:Schema.optional(text(4096)), comments: Schema.Array(Schema.Struct({ itemId: uuid, text: text(4096) })).check(Schema.isMaxLength(16)) })),
  reconcile: closed(Schema.Struct({ session: Session })), rotate: closed(Schema.Struct({ session: Session })),
  setup: closed(Schema.Struct({ session: Session, origin: text(4096) })),
  dismiss: closed(Schema.Struct({ session: Session, handle: text(36) })),
  workList: closed(Schema.Struct({ session: Session, family: Schema.Literals(WorkListFamilies), cursor: text(36) })),
  workOpen: closed(Schema.Struct(workSelector)),
  workBody: closed(Schema.Struct({ ...workSelector, bodyDigest: digest })),
}
export const Review = Ipc.define({ id: "writhelm-opencode.review", methods: {
  status: { input: Inputs.status, output: Reply }, connect: { input: Inputs.connect, output: Reply },
  pending: { input: Inputs.pending, output: Reply }, open: { input: Inputs.open, output: Reply },
  answer: { input: Inputs.answer, output: Reply }, reconcile: { input: Inputs.reconcile, output: Reply },
  rotate: { input: Inputs.rotate, output: Reply }, setup: { input: Inputs.setup, output: Reply },
  dismiss: { input: Inputs.dismiss, output: Reply },
  workList: { input: Inputs.workList, output: Reply }, workOpen: { input: Inputs.workOpen, output: Reply },
  workBody: { input: Inputs.workBody, output: Reply },
} })
/** Native GUI/main only. A rendered acknowledgement does not submit an owner decision. */
export const PresentationRequest=closed(Schema.Struct({session:Session,
  language:Schema.optional(text(12).check(Schema.isPattern(/^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-[A-Z]{2}|-[0-9]{3})?$/)))}))
export const PresentationShown=closed(Schema.Struct({session:Session,requestId:uuid,requestDigest:digest,packetDigest:digest,
  language:text(12).check(Schema.isPattern(/^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-[A-Z]{2}|-[0-9]{3})?$/))}))
export const ApprovalPresentation=Ipc.define({id:"writhelm-opencode.approval-presentation",methods:{
  requested:{input:PresentationRequest,output:Reply},
  shown:{input:PresentationShown,output:Reply},
}})
const draftComments=Schema.Array(Schema.Struct({itemId:uuid,text:text(4096)})).check(Schema.isMaxLength(16))
const draftRef={session:Session,requestId:uuid,requestDigest:digest,packetDigest:digest}
export const DraftInputs={
  load:closed(Schema.Struct(draftRef)),
  save:closed(Schema.Struct({...draftRef,expectedDigest:text(64).check(Schema.isPattern(/^(|[a-f0-9]{64})$/)),
    comments:draftComments,reason:text(4096)})),
}
export const ReviewDrafts=Ipc.define({id:"writhelm-opencode.review-drafts",methods:{
  load:{input:DraftInputs.load,output:Reply},save:{input:DraftInputs.save,output:Reply},
}})
export const OwnerOrderInputs={
  view:Inputs.status,
  select:closed(Schema.Struct({session:Session,proposalId:uuid,proposalDigest:digest,expectedDecision:text(64)})),
}
export const OwnerOrderPresentation=Ipc.define({id:"writhelm-opencode.owner-order",methods:{
  view:{input:OwnerOrderInputs.view,output:Reply},
  select:{input:OwnerOrderInputs.select,output:Reply},
}})
export type Input<K extends keyof typeof Inputs> = (typeof Inputs)[K]["Type"]
export const reply = (code: Code = "OK", fields: Partial<Reply> = {}): Reply => ({ ok: code === "OK", code, handle: "", bundleJSON: "", pageJSON: "", resultJSON: "", pending: false, ...fields })
export class Fault extends Error { constructor(readonly code: Code) { super(code) } }
export function requireThat(ok: unknown, code: Code = "INVALID_INPUT"): asserts ok { if (!ok) throw new Fault(code) }
