// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*

/** Classify internal validation reasons in the exact operation's frozen refusal vocabulary.
 * This only narrows refusals; it never catches resource exhaustion, authorizes an effect, invents a
 * successful fallback, or changes AUTH's pre-evaluator denial/replay policy. Unclassified defects
 * are integrity failures and must not become a durable business rejection. */
internal fun workRefusal(operation: String,fault: WorkFault): WorkFault {
    val allowed=workRefusalCodes.getValue(operation)
    if(fault.problem in allowed)return fault
    val reason=fault.problem
    val classified: WorkProblem?=when(operation) {
        "create_task_revision","pin_input_manifest","create_profile","revise_verification_plan",
        "revise_applicability","record_evidence_applicability","record_disposition","accept_result" -> when(reason) {
            WorkProblem.INVALID_SUBJECT,WorkProblem.INVALID_BINDING,WorkProblem.WRONG_ASSIGNMENT -> WorkProblem.INVALID_REFERENCE
            WorkProblem.NOT_READY,WorkProblem.NATIVE_UNKNOWN -> WorkProblem.BLOCKED_INPUT
            else -> null
        }
        "set_task_head" -> when(reason) { WorkProblem.NOT_FOUND -> WorkProblem.INVALID_REFERENCE;else -> null }
        "update_plan" -> when(reason) {
            WorkProblem.INVALID_TRANSITION,WorkProblem.INVALID_SUBJECT,WorkProblem.INVALID_REFERENCE,WorkProblem.NOT_FOUND -> WorkProblem.WRONG_ASSIGNMENT
            else -> null
        }
        "reserve_assignment" -> when(reason) {
            WorkProblem.INVALID_SUBJECT,WorkProblem.INVALID_BINDING,WorkProblem.WRONG_ASSIGNMENT,WorkProblem.NOT_FOUND -> WorkProblem.INVALID_REFERENCE
            WorkProblem.DUPLICATE_ID -> WorkProblem.CLAIM_CONFLICT
            else -> null
        }
        "bind_assignment" -> when(reason) {
            WorkProblem.INVALID_TRANSITION,WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT,WorkProblem.WRONG_ASSIGNMENT,WorkProblem.DUPLICATE_ID -> WorkProblem.INVALID_BINDING
            WorkProblem.NOT_FOUND,WorkProblem.BLOCKED_INPUT -> WorkProblem.NOT_READY
            else -> null
        }
        "request_native_transition" -> when(reason) {
            WorkProblem.WRONG_ASSIGNMENT -> WorkProblem.INVALID_TRANSITION
            WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT -> WorkProblem.BLOCKED_INPUT
            WorkProblem.INCOMPLETE_HANDOFF,WorkProblem.NOT_FOUND -> WorkProblem.NOT_READY
            else -> null
        }
        "observe_native_transition" -> when(reason) {
            WorkProblem.NATIVE_UNKNOWN,WorkProblem.WRONG_ASSIGNMENT,WorkProblem.INVALID_REFERENCE,
            WorkProblem.INVALID_SUBJECT,WorkProblem.BLOCKED_INPUT,WorkProblem.NOT_FOUND -> WorkProblem.INVALID_BINDING
            else -> null
        }
        "record_recovery" -> when(reason) {
            WorkProblem.NATIVE_UNKNOWN,WorkProblem.INVALID_TRANSITION,WorkProblem.CLAIM_CONFLICT -> WorkProblem.BLOCKED_INPUT
            WorkProblem.WRONG_ASSIGNMENT,WorkProblem.INVALID_SUBJECT,WorkProblem.INVALID_BINDING -> WorkProblem.INVALID_REFERENCE
            else -> null
        }
        "rebind_assignment" -> when(reason) {
            WorkProblem.WRONG_ASSIGNMENT,WorkProblem.INVALID_BINDING -> WorkProblem.NATIVE_UNKNOWN
            WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT,WorkProblem.NOT_FOUND,WorkProblem.BLOCKED_INPUT -> WorkProblem.NOT_READY
            else -> null
        }
        "complete_assignment" -> when(reason) {
            WorkProblem.WRONG_ASSIGNMENT,WorkProblem.INVALID_TRANSITION,WorkProblem.INVALID_SUBJECT,
            WorkProblem.INVALID_REFERENCE,WorkProblem.NOT_FOUND -> WorkProblem.INCOMPLETE_HANDOFF
            else -> null
        }
        "record_loaded_view","record_memory","record_checkpoint","record_retrospective","submit_result",
        "submit_verification_report","record_evidence","record_source_cut" -> when(reason) {
            WorkProblem.INVALID_TRANSITION -> WorkProblem.WRONG_ASSIGNMENT
            WorkProblem.BLOCKED_INPUT,WorkProblem.FOREIGN_REFERENCE,WorkProblem.NOT_FOUND -> WorkProblem.INVALID_REFERENCE
            else -> null
        }
        "record_delivery" -> when(reason) {
            WorkProblem.INVALID_TRANSITION,WorkProblem.INCOMPLETE_HANDOFF,WorkProblem.NOT_FOUND,
            WorkProblem.INVALID_SUBJECT,WorkProblem.BLOCKED_INPUT -> WorkProblem.INVALID_REFERENCE
            else -> null
        }
        "prepare_integration" -> when(reason) {
            WorkProblem.STALE_GENERATION -> WorkProblem.CLAIM_CONFLICT
            WorkProblem.DUPLICATE_ID,WorkProblem.INVALID_REFERENCE,WorkProblem.FOREIGN_REFERENCE,WorkProblem.NOT_FOUND -> WorkProblem.INVALID_SUBJECT
            WorkProblem.BLOCKED_INPUT -> WorkProblem.NOT_ACCEPTED
            else -> null
        }
        "observe_integration" -> when(reason) {
            WorkProblem.CLAIM_CONFLICT -> WorkProblem.NATIVE_UNKNOWN
            WorkProblem.INVALID_SUBJECT,WorkProblem.BLOCKED_INPUT,WorkProblem.NOT_FOUND,WorkProblem.DUPLICATE_ID -> WorkProblem.INVALID_REFERENCE
            else -> null
        }
        "record_controller_continuation" -> when(reason) {
            WorkProblem.INVALID_SUBJECT,WorkProblem.WRONG_ASSIGNMENT,WorkProblem.INVALID_BINDING -> WorkProblem.INVALID_REFERENCE
            WorkProblem.INCOMPLETE_HANDOFF -> WorkProblem.BLOCKED_INPUT
            else -> null
        }
        else -> null
    }
    if(classified==null || classified !in allowed)refuse(CodecProblem.INVALID_GRAPH)
    return WorkFault(classified)
}
