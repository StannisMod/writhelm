// SPDX-License-Identifier: AGPL-3.0-only
// Generated from frozen-v2; see external CORE result for exact input identity.
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import java.util.UUID

internal fun decodeWorkPayload(f: WorkFamily, v: JsonValue): WorkPayload = when(f) {
    WorkFamily.TASK_REVISIONS -> WTaskRevisionsPayload(decodeWTaskRevisions(v))
    WorkFamily.TASK_HEADS -> WTaskHeadsPayload(decodeWTaskHeads(v))
    WorkFamily.PLANS -> WPlansPayload(decodeWPlans(v))
    WorkFamily.INPUT_MANIFESTS -> WInputManifestsPayload(decodeWInputManifests(v))
    WorkFamily.LOADED_VIEWS -> WLoadedViewsPayload(decodeWLoadedViews(v))
    WorkFamily.PROFILES -> WProfilesPayload(decodeWProfiles(v))
    WorkFamily.ASSIGNMENTS -> WAssignmentsPayload(decodeWAssignments(v))
    WorkFamily.ASSIGNMENT_BINDINGS -> WAssignmentBindingsPayload(decodeWAssignmentBindings(v))
    WorkFamily.CLAIMS -> WClaimsPayload(decodeWClaims(v))
    WorkFamily.NATIVE_INTENTS -> WNativeIntentsPayload(decodeWNativeIntents(v))
    WorkFamily.RECOVERIES -> WRecoveriesPayload(decodeWRecoveries(v))
    WorkFamily.CHECKPOINTS -> WCheckpointsPayload(decodeWCheckpoints(v))
    WorkFamily.DELIVERIES -> WDeliveriesPayload(decodeWDeliveries(v))
    WorkFamily.MEMORY -> WMemoryPayload(decodeWMemory(v))
    WorkFamily.RETROSPECTIVES -> WRetrospectivesPayload(decodeWRetrospectives(v))
    WorkFamily.RESULTS -> WResultsPayload(decodeWResults(v))
    WorkFamily.VERIFICATION_PLANS -> WVerificationPlansPayload(decodeWVerificationPlans(v))
    WorkFamily.VERIFICATION_REPORTS -> WVerificationReportsPayload(decodeWVerificationReports(v))
    WorkFamily.EVIDENCE -> WEvidencePayload(decodeWEvidence(v))
    WorkFamily.DISPOSITIONS -> WDispositionsPayload(decodeWDispositions(v))
    WorkFamily.ACCEPTANCES -> WAcceptancesPayload(decodeWAcceptances(v))
    WorkFamily.INTEGRATION_INTENTS -> WIntegrationIntentsPayload(decodeWIntegrationIntents(v))
    WorkFamily.INTEGRATIONS -> WIntegrationsPayload(decodeWIntegrations(v))
    WorkFamily.APPLICABILITY -> WApplicabilityPayload(decodeWApplicability(v))
    WorkFamily.EVIDENCE_APPLICABILITY -> WEvidenceApplicabilityPayload(decodeWEvidenceApplicability(v))
    WorkFamily.SOURCE_CUTS -> WSourceCutsPayload(decodeWSourceCuts(v))
    WorkFamily.CONTROLLER_CONTINUATIONS -> WControllerContinuationsPayload(decodeWControllerContinuations(v))
    WorkFamily.ASSIGNMENT_REPLACEMENTS -> WAssignmentReplacementsPayload(decodeWAssignmentReplacements(v))
    WorkFamily.CONDITIONS -> WConditionsPayload(decodeWConditions(v))
    WorkFamily.CONDITION_CONFIRMATIONS -> WConditionConfirmationsPayload(decodeWConditionConfirmations(v))
    WorkFamily.CONTINUATIONS -> WContinuationsPayload(decodeWContinuations(v))
}
internal fun encodeWorkPayload(v: WorkPayload): JsonValue = when(v) {
    is WTaskRevisionsPayload -> encodeWTaskRevisions(v.value)
    is WTaskHeadsPayload -> encodeWTaskHeads(v.value)
    is WPlansPayload -> encodeWPlans(v.value)
    is WInputManifestsPayload -> encodeWInputManifests(v.value)
    is WLoadedViewsPayload -> encodeWLoadedViews(v.value)
    is WProfilesPayload -> encodeWProfiles(v.value)
    is WMechanicalProfilesPayload -> ExecutionDescriptorCodec().encodeMechanicalProfile(v.value)
    is WAssignmentsPayload -> encodeWAssignments(v.value)
    is WAssignmentBindingsPayload -> encodeWAssignmentBindings(v.value)
    is WClaimsPayload -> encodeWClaims(v.value)
    is WNativeIntentsPayload -> encodeWNativeIntents(v.value)
    is WRecoveriesPayload -> encodeWRecoveries(v.value)
    is WCheckpointsPayload -> encodeWCheckpoints(v.value)
    is WDeliveriesPayload -> encodeWDeliveries(v.value)
    is WMemoryPayload -> encodeWMemory(v.value)
    is WRetrospectivesPayload -> encodeWRetrospectives(v.value)
    is WResultsPayload -> encodeWResults(v.value)
    is WVerificationPlansPayload -> encodeWVerificationPlans(v.value)
    is WVerificationReportsPayload -> encodeWVerificationReports(v.value)
    is WEvidencePayload -> encodeWEvidence(v.value)
    is WDispositionsPayload -> encodeWDispositions(v.value)
    is WAcceptancesPayload -> encodeWAcceptances(v.value)
    is WIntegrationIntentsPayload -> encodeWIntegrationIntents(v.value)
    is WIntegrationsPayload -> encodeWIntegrations(v.value)
    is WApplicabilityPayload -> encodeWApplicability(v.value)
    is WEvidenceApplicabilityPayload -> encodeWEvidenceApplicability(v.value)
    is WSourceCutsPayload -> encodeWSourceCuts(v.value)
    is WControllerContinuationsPayload -> encodeWControllerContinuations(v.value)
    is WAssignmentReplacementsPayload -> encodeWAssignmentReplacements(v.value)
    is WConditionsPayload -> encodeWConditions(v.value)
    is WConditionConfirmationsPayload -> encodeWConditionConfirmations(v.value)
    is WContinuationsPayload -> encodeWContinuations(v.value)
}
internal fun payloadFamily(v: WorkPayload): WorkFamily = when(v) {
    is WTaskRevisionsPayload -> WorkFamily.TASK_REVISIONS
    is WTaskHeadsPayload -> WorkFamily.TASK_HEADS
    is WPlansPayload -> WorkFamily.PLANS
    is WInputManifestsPayload -> WorkFamily.INPUT_MANIFESTS
    is WLoadedViewsPayload -> WorkFamily.LOADED_VIEWS
    is WProfilesPayload -> WorkFamily.PROFILES
    is WMechanicalProfilesPayload -> WorkFamily.PROFILES
    is WAssignmentsPayload -> WorkFamily.ASSIGNMENTS
    is WAssignmentBindingsPayload -> WorkFamily.ASSIGNMENT_BINDINGS
    is WClaimsPayload -> WorkFamily.CLAIMS
    is WNativeIntentsPayload -> WorkFamily.NATIVE_INTENTS
    is WRecoveriesPayload -> WorkFamily.RECOVERIES
    is WCheckpointsPayload -> WorkFamily.CHECKPOINTS
    is WDeliveriesPayload -> WorkFamily.DELIVERIES
    is WMemoryPayload -> WorkFamily.MEMORY
    is WRetrospectivesPayload -> WorkFamily.RETROSPECTIVES
    is WResultsPayload -> WorkFamily.RESULTS
    is WVerificationPlansPayload -> WorkFamily.VERIFICATION_PLANS
    is WVerificationReportsPayload -> WorkFamily.VERIFICATION_REPORTS
    is WEvidencePayload -> WorkFamily.EVIDENCE
    is WDispositionsPayload -> WorkFamily.DISPOSITIONS
    is WAcceptancesPayload -> WorkFamily.ACCEPTANCES
    is WIntegrationIntentsPayload -> WorkFamily.INTEGRATION_INTENTS
    is WIntegrationsPayload -> WorkFamily.INTEGRATIONS
    is WApplicabilityPayload -> WorkFamily.APPLICABILITY
    is WEvidenceApplicabilityPayload -> WorkFamily.EVIDENCE_APPLICABILITY
    is WSourceCutsPayload -> WorkFamily.SOURCE_CUTS
    is WControllerContinuationsPayload -> WorkFamily.CONTROLLER_CONTINUATIONS
    is WAssignmentReplacementsPayload -> WorkFamily.ASSIGNMENT_REPLACEMENTS
    is WConditionsPayload -> WorkFamily.CONDITIONS
    is WConditionConfirmationsPayload -> WorkFamily.CONDITION_CONFIRMATIONS
    is WContinuationsPayload -> WorkFamily.CONTINUATIONS
}
internal fun decodeWorkOperation(n: String, v: JsonValue): WorkOperation = when(n) {
    "create_task_revision" -> CCreateTaskRevisionOperation(decodeCCreateTaskRevision(v))
    "set_task_head" -> CSetTaskHeadOperation(decodeCSetTaskHead(v))
    "pin_input_manifest" -> CPinInputManifestOperation(decodeCPinInputManifest(v))
    "create_profile" -> CCreateProfileOperation(decodeCCreateProfile(v))
    "revise_verification_plan" -> CReviseVerificationPlanOperation(decodeCReviseVerificationPlan(v))
    "revise_applicability" -> CReviseApplicabilityOperation(decodeCReviseApplicability(v))
    "update_plan" -> CUpdatePlanOperation(decodeCUpdatePlan(v))
    "reserve_assignment" -> CReserveAssignmentOperation(decodeCReserveAssignment(v))
    "bind_assignment" -> CBindAssignmentOperation(decodeCBindAssignment(v))
    "request_native_transition" -> CRequestNativeTransitionOperation(decodeCRequestNativeTransition(v))
    "observe_native_transition" -> CObserveNativeTransitionOperation(decodeCObserveNativeTransition(v))
    "record_recovery" -> CRecordRecoveryOperation(decodeCRecordRecovery(v))
    "rebind_assignment" -> CRebindAssignmentOperation(decodeCRebindAssignment(v))
    "complete_assignment" -> CCompleteAssignmentOperation(decodeCCompleteAssignment(v))
    "record_loaded_view" -> CRecordLoadedViewOperation(decodeCRecordLoadedView(v))
    "record_memory" -> CRecordMemoryOperation(decodeCRecordMemory(v))
    "record_checkpoint" -> CRecordCheckpointOperation(decodeCRecordCheckpoint(v))
    "record_retrospective" -> CRecordRetrospectiveOperation(decodeCRecordRetrospective(v))
    "submit_result" -> CSubmitResultOperation(decodeCSubmitResult(v))
    "submit_verification_report" -> CSubmitVerificationReportOperation(decodeCSubmitVerificationReport(v))
    "record_evidence" -> CRecordEvidenceOperation(decodeCRecordEvidence(v))
    "record_delivery" -> CRecordDeliveryOperation(decodeCRecordDelivery(v))
    "record_evidence_applicability" -> CRecordEvidenceApplicabilityOperation(decodeCRecordEvidenceApplicability(v))
    "record_disposition" -> CRecordDispositionOperation(decodeCRecordDisposition(v))
    "accept_result" -> CAcceptResultOperation(decodeCAcceptResult(v))
    "prepare_integration" -> CPrepareIntegrationOperation(decodeCPrepareIntegration(v))
    "observe_integration" -> CObserveIntegrationOperation(decodeCObserveIntegration(v))
    "record_source_cut" -> CRecordSourceCutOperation(decodeCRecordSourceCut(v))
    "record_controller_continuation" -> CRecordControllerContinuationOperation(decodeCRecordControllerContinuation(v))
    "replace_isolated_assignment" -> CReplaceIsolatedAssignmentOperation(decodeCReplaceIsolatedAssignment(v))
    "record_condition" -> CRecordConditionOperation(decodeCRecordCondition(v))
    "confirm_condition" -> CConfirmConditionOperation(decodeCConfirmCondition(v))
    "prepare_continuation" -> CPrepareContinuationOperation(decodeCPrepareContinuation(v))
    "observe_continuation" -> CObserveContinuationOperation(decodeCObserveContinuation(v))
    else -> refuse(CodecProblem.INVALID_SHAPE)
}
internal fun encodeWorkOperation(v: WorkOperation): JsonValue = when(v) {
    is CCreateTaskRevisionOperation -> encodeCCreateTaskRevision(v.value)
    is CSetTaskHeadOperation -> encodeCSetTaskHead(v.value)
    is CPinInputManifestOperation -> encodeCPinInputManifest(v.value)
    is CCreateProfileOperation -> encodeCCreateProfile(v.value)
    is CReviseVerificationPlanOperation -> encodeCReviseVerificationPlan(v.value)
    is CReviseApplicabilityOperation -> encodeCReviseApplicability(v.value)
    is CUpdatePlanOperation -> encodeCUpdatePlan(v.value)
    is CReserveAssignmentOperation -> encodeCReserveAssignment(v.value)
    is CBindAssignmentOperation -> encodeCBindAssignment(v.value)
    is CRequestNativeTransitionOperation -> encodeCRequestNativeTransition(v.value)
    is CObserveNativeTransitionOperation -> encodeCObserveNativeTransition(v.value)
    is CRecordRecoveryOperation -> encodeCRecordRecovery(v.value)
    is CRebindAssignmentOperation -> encodeCRebindAssignment(v.value)
    is CCompleteAssignmentOperation -> encodeCCompleteAssignment(v.value)
    is CRecordLoadedViewOperation -> encodeCRecordLoadedView(v.value)
    is CRecordMemoryOperation -> encodeCRecordMemory(v.value)
    is CRecordCheckpointOperation -> encodeCRecordCheckpoint(v.value)
    is CRecordRetrospectiveOperation -> encodeCRecordRetrospective(v.value)
    is CSubmitResultOperation -> encodeCSubmitResult(v.value)
    is CSubmitVerificationReportOperation -> encodeCSubmitVerificationReport(v.value)
    is CRecordEvidenceOperation -> encodeCRecordEvidence(v.value)
    is CRecordDeliveryOperation -> encodeCRecordDelivery(v.value)
    is CRecordEvidenceApplicabilityOperation -> encodeCRecordEvidenceApplicability(v.value)
    is CRecordDispositionOperation -> encodeCRecordDisposition(v.value)
    is CAcceptResultOperation -> encodeCAcceptResult(v.value)
    is CPrepareIntegrationOperation -> encodeCPrepareIntegration(v.value)
    is CObserveIntegrationOperation -> encodeCObserveIntegration(v.value)
    is CRecordSourceCutOperation -> encodeCRecordSourceCut(v.value)
    is CRecordControllerContinuationOperation -> encodeCRecordControllerContinuation(v.value)
    is CReplaceIsolatedAssignmentOperation -> encodeCReplaceIsolatedAssignment(v.value)
    is CRecordConditionOperation -> encodeCRecordCondition(v.value)
    is CConfirmConditionOperation -> encodeCConfirmCondition(v.value)
    is CPrepareContinuationOperation -> encodeCPrepareContinuation(v.value)
    is CObserveContinuationOperation -> encodeCObserveContinuation(v.value)
}
