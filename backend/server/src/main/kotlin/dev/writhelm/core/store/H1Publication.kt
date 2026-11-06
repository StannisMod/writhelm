// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*
import dev.writhelm.core.work.*

/** Closed frozen-v2 journal catalogue. This is not command payload admission or domain policy. */
internal object H1Catalog {
    val authOperations=setOf("replace_work_grants","register_delegation","rotate_delegated_credential","revoke_delegated_credential","revoke_delegation")
    val resultFamilies=mapOf(
        "create_task_revision" to "task-revisions", "set_task_head" to "task-heads",
        "pin_input_manifest" to "input-manifests", "create_profile" to "profiles",
        "revise_verification_plan" to "verification-plans", "revise_applicability" to "applicability",
        "update_plan" to "plans", "reserve_assignment" to "assignments", "bind_assignment" to "assignments",
        "request_native_transition" to "native-intents", "observe_native_transition" to "native-intents",
        "record_recovery" to "recoveries", "rebind_assignment" to "assignments", "complete_assignment" to "assignments",
        "record_loaded_view" to "loaded-views", "record_memory" to "memory", "record_checkpoint" to "checkpoints",
        "record_retrospective" to "retrospectives", "submit_result" to "results",
        "submit_verification_report" to "verification-reports", "record_evidence" to "evidence",
        "record_delivery" to "deliveries", "record_evidence_applicability" to "evidence-applicability",
        "record_disposition" to "dispositions", "accept_result" to "acceptances",
        "prepare_integration" to "integration-intents", "observe_integration" to "integration-intents",
        "record_source_cut" to "source-cuts", "record_controller_continuation" to "controller-continuations",
        "replace_isolated_assignment" to "assignment-replacements", "record_condition" to "conditions",
        "confirm_condition" to "condition-confirmations", "prepare_continuation" to "continuations",
        "observe_continuation" to "continuations")
    val workOperations=resultFamilies.keys
    val operations=workOperations+authOperations
    val authCodes=authRejections+setOf("AUTH_DUPLICATE_ID","AUTH_STALE_DELEGATION","AUTH_STALE_ASSIGNMENT","AUTH_INVALID_BINDING","AUTH_TEMPLATE_CONFLICT","AUTH_SCOPE_CLOSED")
    private val immutableCodes=setOf("BLOCKED_INPUT","DIGEST_MISMATCH","DUPLICATE_ID","FOREIGN_REFERENCE","INVALID_REFERENCE","MISSING_BODY","NOT_FOUND")
    private val ownCodes=setOf("DIGEST_MISMATCH","DUPLICATE_ID","INCOMPLETE_HANDOFF","INVALID_REFERENCE","INVALID_SUBJECT","MISSING_BODY","STALE_GENERATION","WRONG_ASSIGNMENT")
    val workCodes=workOperations.associateWith { op -> when(op) {
        in model2RefusalCodes -> model2RefusalCodes.getValue(op).map { it.name }.toSet()
        "set_task_head","record_delivery" -> setOf("DIGEST_MISMATCH","GENERATION_EXHAUSTED","INVALID_REFERENCE","MISSING_BODY","STALE_GENERATION")
        "update_plan" -> setOf("DIGEST_MISMATCH","GENERATION_EXHAUSTED","MISSING_BODY","STALE_GENERATION","WRONG_ASSIGNMENT")
        "reserve_assignment" -> setOf("BLOCKED_INPUT","CLAIM_CONFLICT","DIGEST_MISMATCH","GENERATION_EXHAUSTED","INVALID_REFERENCE","MISSING_BODY","NOT_READY","STALE_GENERATION")
        "bind_assignment" -> setOf("DIGEST_MISMATCH","GENERATION_EXHAUSTED","INVALID_BINDING","MISSING_BODY","NATIVE_UNKNOWN","NOT_READY","STALE_GENERATION")
        "request_native_transition" -> setOf("BLOCKED_INPUT","DIGEST_MISMATCH","GENERATION_EXHAUSTED","INVALID_TRANSITION","MISSING_BODY","NATIVE_UNKNOWN","NOT_READY","STALE_GENERATION")
        "observe_native_transition" -> setOf("DIGEST_MISMATCH","GENERATION_EXHAUSTED","INVALID_BINDING","INVALID_TRANSITION","MISSING_BODY","STALE_GENERATION")
        "record_recovery" -> immutableCodes+setOf("GENERATION_EXHAUSTED","STALE_GENERATION")
        "rebind_assignment" -> setOf("CLAIM_CONFLICT","DIGEST_MISMATCH","GENERATION_EXHAUSTED","MISSING_BODY","NATIVE_UNKNOWN","NOT_READY","STALE_GENERATION")
        "complete_assignment" -> setOf("CLAIM_CONFLICT","DIGEST_MISMATCH","GENERATION_EXHAUSTED","INCOMPLETE_HANDOFF","MISSING_BODY","NATIVE_UNKNOWN","STALE_GENERATION")
        "record_loaded_view","record_memory","record_retrospective","submit_result","submit_verification_report","record_evidence","record_source_cut" -> ownCodes
        "record_checkpoint" -> ownCodes+"GENERATION_EXHAUSTED"
        "prepare_integration" -> setOf("CLAIM_CONFLICT","DIGEST_MISMATCH","GENERATION_EXHAUSTED","INVALID_SUBJECT","MISSING_BODY","NATIVE_UNKNOWN","NOT_ACCEPTED","STALE_TARGET")
        "observe_integration" -> setOf("DIGEST_MISMATCH","GENERATION_EXHAUSTED","INVALID_REFERENCE","MISSING_BODY","NATIVE_UNKNOWN","STALE_GENERATION","STALE_TARGET")
        "record_controller_continuation" -> immutableCodes+"STALE_GENERATION"
        else -> immutableCodes
    } }
    fun family(tag: String)=WorkFamily.entries.singleOrNull { it.tag==tag } ?: fail(StoreProblem.UNSUPPORTED)
    fun effectFamilies(operation: String): Set<String> = setOf("bodies",resultFamilies.getValue(operation))+when(operation) {
        "create_task_revision" -> setOf("verification-plans")
        "reserve_assignment","rebind_assignment" -> setOf("assignments","claims","native-intents")
        "replace_isolated_assignment" -> setOf("assignments","claims","native-intents")
        "bind_assignment" -> setOf("assignments","assignment-bindings","native-intents")
        "request_native_transition","observe_native_transition" -> setOf("assignments","native-intents")
        "record_recovery" -> setOf("assignments","claims")
        "complete_assignment" -> setOf("claims")
        "record_checkpoint" -> setOf("assignments")
        "prepare_integration" -> setOf("claims")
        "observe_integration" -> setOf("integrations","claims")
        else -> emptySet()
    }
    fun result(operation: String,value: JsonValue): RecordKey {
        val f=value.fields("key","generation","digest")
        val key=RecordKey("work",resultFamilies[operation] ?: fail(StoreProblem.INTEGRITY),f.getValue("key"))
        digest(f.getValue("digest"))
        if(key.mutable)f.getValue("generation").long() else integrity(f.getValue("generation")===JNull)
        return key
    }
}

/** A current-H decision, not a cached role or historical credential. */
internal sealed interface CurrentCommandScope {
    data object Denied: CurrentCommandScope
    class NewEffect(val actor: AuthenticatedActor): CurrentCommandScope
    class ReplayOnly(val actor: AuthenticatedActor): CurrentCommandScope
}

/** Required trusted APP integration, with NO permissive defaults. Never supplied by transport.
 * canonicalize must decode through actual CORE/AUTH command codecs, validate closed payload/project
 * laws and normalize only true sets. authorize must authenticate the SAME presentation at each H,
 * derive actual CORE assignment facts, and apply AUTH's exact current-scope matrix. allowedWrites
 * and validateTransition must come from the actual reducers, including atomic AUTH+CORE enrollment.
 * StorePorts.validateTransition must enforce consumed affected records4096/edges16384/depth256,
 * AUTH lineage8192 and derived whole-state entry bytes64MiB/record counts8192, using actual codec
 * state and unchanged-H proofs. Namespace validators enforce full restore bounds independently.
 * validateRecord/namespace validators use supported historical codecs, NOT today's admission caps.
 * preflight reserves the bounded primary/emergency/delivery outcome before evaluating a new effect.
 * validateBoundary receives the actual original H/C edge after the store's independent structural,
 * descriptor/diff/result checks, before CAS and on each audited edge. Bind real joint CORE/AUTH
 * correspondence and historical attribution here, without current/latest substitution or a cache
 * of after-records. Snapshot reads consume the active command/audit budgets. The legacy
 * StorePorts.validateHistoricalReceipt remains required for its existing metadata/provenance checks.
 */
internal class H1PublicationBindings(
    val canonicalize: (ByteArray)->ByteArray,
    val authorize: (StoreSnapshot,StoreCommand)->CurrentCommandScope,
    val allowedWrites: (StoreSnapshot,StoreCommand)->Set<RecordKey>,
    val validateRecord: (RecordKey,ByteArray,Boolean)->Unit,
    val preflight: (StoreSnapshot,StoreCommand,AuthenticatedActor)->Unit,
    val validateBoundary: (parentH: StoreSnapshot,candidateC: StoreSnapshot,receipt: Receipt)->Unit,
)
