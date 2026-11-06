// SPDX-License-Identifier: AGPL-3.0-only
// Generated from frozen-v2; see external CORE result for exact input identity.
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import java.util.UUID

internal val workRefusalCodes: Map<String,Set<WorkProblem>> = mapOf(
    "create_task_revision" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.FOREIGN_REFERENCE,WorkProblem.INVALID_REFERENCE,WorkProblem.MISSING_BODY,WorkProblem.NOT_FOUND),
    "set_task_head" to setOf(WorkProblem.DIGEST_MISMATCH,WorkProblem.GENERATION_EXHAUSTED,WorkProblem.INVALID_REFERENCE,WorkProblem.MISSING_BODY,WorkProblem.STALE_GENERATION),
    "pin_input_manifest" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.FOREIGN_REFERENCE,WorkProblem.INVALID_REFERENCE,WorkProblem.MISSING_BODY,WorkProblem.NOT_FOUND),
    "create_profile" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.FOREIGN_REFERENCE,WorkProblem.INVALID_REFERENCE,WorkProblem.MISSING_BODY,WorkProblem.NOT_FOUND),
    "revise_verification_plan" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.FOREIGN_REFERENCE,WorkProblem.INVALID_REFERENCE,WorkProblem.MISSING_BODY,WorkProblem.NOT_FOUND),
    "revise_applicability" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.FOREIGN_REFERENCE,WorkProblem.INVALID_REFERENCE,WorkProblem.MISSING_BODY,WorkProblem.NOT_FOUND),
    "update_plan" to setOf(WorkProblem.DIGEST_MISMATCH,WorkProblem.GENERATION_EXHAUSTED,WorkProblem.MISSING_BODY,WorkProblem.STALE_GENERATION,WorkProblem.WRONG_ASSIGNMENT),
    "reserve_assignment" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.CLAIM_CONFLICT,WorkProblem.DIGEST_MISMATCH,WorkProblem.GENERATION_EXHAUSTED,WorkProblem.INVALID_REFERENCE,WorkProblem.MISSING_BODY,WorkProblem.NOT_READY,WorkProblem.STALE_GENERATION),
    "bind_assignment" to setOf(WorkProblem.DIGEST_MISMATCH,WorkProblem.GENERATION_EXHAUSTED,WorkProblem.INVALID_BINDING,WorkProblem.MISSING_BODY,WorkProblem.NATIVE_UNKNOWN,WorkProblem.NOT_READY,WorkProblem.STALE_GENERATION),
    "request_native_transition" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.GENERATION_EXHAUSTED,WorkProblem.INVALID_TRANSITION,WorkProblem.MISSING_BODY,WorkProblem.NATIVE_UNKNOWN,WorkProblem.NOT_READY,WorkProblem.STALE_GENERATION),
    "observe_native_transition" to setOf(WorkProblem.DIGEST_MISMATCH,WorkProblem.GENERATION_EXHAUSTED,WorkProblem.INVALID_BINDING,WorkProblem.INVALID_TRANSITION,WorkProblem.MISSING_BODY,WorkProblem.STALE_GENERATION),
    "record_recovery" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.FOREIGN_REFERENCE,WorkProblem.GENERATION_EXHAUSTED,WorkProblem.INVALID_REFERENCE,WorkProblem.MISSING_BODY,WorkProblem.NOT_FOUND,WorkProblem.STALE_GENERATION),
    "rebind_assignment" to setOf(WorkProblem.CLAIM_CONFLICT,WorkProblem.DIGEST_MISMATCH,WorkProblem.GENERATION_EXHAUSTED,WorkProblem.MISSING_BODY,WorkProblem.NATIVE_UNKNOWN,WorkProblem.NOT_READY,WorkProblem.STALE_GENERATION),
    "complete_assignment" to setOf(WorkProblem.CLAIM_CONFLICT,WorkProblem.DIGEST_MISMATCH,WorkProblem.GENERATION_EXHAUSTED,WorkProblem.INCOMPLETE_HANDOFF,WorkProblem.MISSING_BODY,WorkProblem.NATIVE_UNKNOWN,WorkProblem.STALE_GENERATION),
    "record_loaded_view" to setOf(WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.INCOMPLETE_HANDOFF,WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT,WorkProblem.MISSING_BODY,WorkProblem.STALE_GENERATION,WorkProblem.WRONG_ASSIGNMENT),
    "record_memory" to setOf(WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.INCOMPLETE_HANDOFF,WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT,WorkProblem.MISSING_BODY,WorkProblem.STALE_GENERATION,WorkProblem.WRONG_ASSIGNMENT),
    "record_checkpoint" to setOf(WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.GENERATION_EXHAUSTED,WorkProblem.INCOMPLETE_HANDOFF,WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT,WorkProblem.MISSING_BODY,WorkProblem.STALE_GENERATION,WorkProblem.WRONG_ASSIGNMENT),
    "record_retrospective" to setOf(WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.INCOMPLETE_HANDOFF,WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT,WorkProblem.MISSING_BODY,WorkProblem.STALE_GENERATION,WorkProblem.WRONG_ASSIGNMENT),
    "submit_result" to setOf(WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.INCOMPLETE_HANDOFF,WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT,WorkProblem.MISSING_BODY,WorkProblem.STALE_GENERATION,WorkProblem.WRONG_ASSIGNMENT),
    "submit_verification_report" to setOf(WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.INCOMPLETE_HANDOFF,WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT,WorkProblem.MISSING_BODY,WorkProblem.STALE_GENERATION,WorkProblem.WRONG_ASSIGNMENT),
    "record_evidence" to setOf(WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.INCOMPLETE_HANDOFF,WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT,WorkProblem.MISSING_BODY,WorkProblem.STALE_GENERATION,WorkProblem.WRONG_ASSIGNMENT),
    "record_delivery" to setOf(WorkProblem.DIGEST_MISMATCH,WorkProblem.GENERATION_EXHAUSTED,WorkProblem.INVALID_REFERENCE,WorkProblem.MISSING_BODY,WorkProblem.STALE_GENERATION),
    "record_evidence_applicability" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.FOREIGN_REFERENCE,WorkProblem.INVALID_REFERENCE,WorkProblem.MISSING_BODY,WorkProblem.NOT_FOUND),
    "record_disposition" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.FOREIGN_REFERENCE,WorkProblem.INVALID_REFERENCE,WorkProblem.MISSING_BODY,WorkProblem.NOT_FOUND),
    "accept_result" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.FOREIGN_REFERENCE,WorkProblem.INVALID_REFERENCE,WorkProblem.MISSING_BODY,WorkProblem.NOT_FOUND),
    "prepare_integration" to setOf(WorkProblem.CLAIM_CONFLICT,WorkProblem.DIGEST_MISMATCH,WorkProblem.GENERATION_EXHAUSTED,WorkProblem.INVALID_SUBJECT,WorkProblem.MISSING_BODY,WorkProblem.NATIVE_UNKNOWN,WorkProblem.NOT_ACCEPTED,WorkProblem.STALE_TARGET),
    "observe_integration" to setOf(WorkProblem.DIGEST_MISMATCH,WorkProblem.GENERATION_EXHAUSTED,WorkProblem.INVALID_REFERENCE,WorkProblem.MISSING_BODY,WorkProblem.NATIVE_UNKNOWN,WorkProblem.STALE_GENERATION,WorkProblem.STALE_TARGET),
    "record_source_cut" to setOf(WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.INCOMPLETE_HANDOFF,WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT,WorkProblem.MISSING_BODY,WorkProblem.STALE_GENERATION,WorkProblem.WRONG_ASSIGNMENT),
    "record_controller_continuation" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.FOREIGN_REFERENCE,WorkProblem.INVALID_REFERENCE,WorkProblem.MISSING_BODY,WorkProblem.NOT_FOUND,WorkProblem.STALE_GENERATION)
) + model2RefusalCodes
