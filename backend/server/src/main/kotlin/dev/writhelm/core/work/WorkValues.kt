// SPDX-License-Identifier: AGPL-3.0-only
// Generated from frozen-v2; see external CORE result for exact input identity.
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import java.util.UUID

internal data class WSnapshot(
    val `repositoryId`: UUID,
    val `commit`: WorkGitOid?,
    val `tree`: WorkGitOid,
    val `dirtyContentDigest`: Digest?,
    val `retainedLocatorId`: UUID
)

internal data class WBase(
    val `code`: List<WSnapshot>,
    val `knowledgeManifestId`: UUID,
    val `knowledgeManifestDigest`: Digest
)

internal data class WScope(
    val `read`: String,
    val `write`: String,
    val `decision`: String,
    val `exclusions`: List<String>
)

internal data class WDependency(
    val `producer`: WorkKey.TaskRevision,
    val `readiness`: String,
    val `selectedResult`: WorkReference?,
    val `deferredGateIds`: List<UUID>,
    val `rationale`: String
)

internal sealed interface WSource

internal data class WSourceVariant0(
    val `kind`: String,
    val `reference`: KnowledgeReference
)

internal data class WSourceVariant0Value(val value: WSourceVariant0) : WSource

internal data class WSourceVariant1(
    val `kind`: String,
    val `artifact`: WSnapshot
)

internal data class WSourceVariant1Value(val value: WSourceVariant1) : WSource

internal data class WSourceVariant2(
    val `kind`: String,
    val `reference`: WorkReference
)

internal data class WSourceVariant2Value(val value: WSourceVariant2) : WSource

internal data class WCriterion(
    val `id`: UUID,
    val `statement`: String,
    val `clauses`: List<WSource>
)

internal data class WTaskRevisions(
    val `kind`: String,
    val `state`: String,
    val `objective`: String,
    val `base`: WBase?,
    val `scope`: WScope,
    val `constraints`: List<String>,
    val `dependencies`: List<WDependency>,
    val `criteria`: List<WCriterion>,
    val `escalation`: List<String>,
    val `expectedOutput`: String,
    val `executionProfile`: WorkKey.Profile?,
    val `verificationPlan`: WorkKey.Revision?,
    val `approvedDesign`: List<KnowledgeReference>,
    val `unresolved`: List<String>
)

internal data class WTaskHeads(
    val `generation`: Long,
    val `title`: String,
    val `draft`: WorkKey.TaskRevision?,
    val `executable`: WorkKey.TaskRevision?
)

internal data class WStep(
    val `id`: UUID,
    val `description`: String,
    val `state`: String,
    val `obligations`: List<String>,
    val `blockers`: List<WSource>
)

internal data class WAssignmentSubject(
    val `assignmentId`: UUID,
    val `generation`: Long
)

internal data class WPlans(
    val `task`: WorkKey.TaskRevision,
    val `generation`: Long,
    val `steps`: List<WStep>,
    val `reason`: String,
    val `previousCheckpoint`: WorkReference?,
    val `assignment`: WAssignmentSubject?
)

internal data class WManifestItem(
    val `source`: WSource,
    val `mandatory`: Boolean,
    val `reason`: String,
    val `exactFragments`: List<WorkBodyReference>
)

internal data class WInputManifests(
    val `task`: WorkKey.TaskRevision,
    val `base`: WBase,
    val `applicability`: WorkKey.Revision,
    val `items`: List<WManifestItem>,
    val `declaredClosureAssessment`: WSource,
    val `knownGaps`: List<String>
)

internal data class WLoadedItem(
    val `source`: WSource,
    val `reason`: String,
    val `obligationIds`: List<UUID>
)

internal data class WLoadedViews(
    val `task`: WorkKey.TaskRevision,
    val `manifest`: WorkReference,
    val `assignment`: WAssignmentSubject,
    val `previous`: WorkKey.Revision?,
    val `additions`: List<WLoadedItem>,
    val `discoveredGaps`: List<String>
)

internal data class WProfilePermissionsItem(
    val `tool`: String,
    val `action`: String,
    val `scope`: String
)

internal data class WProfile(
    val `nativeProfileId`: String,
    val `qualificationEvidence`: List<WSource>,
    val `model`: WorkModelReference,
    val `route`: String,
    val `effort`: String,
    val `contextTokens`: Long,
    val `outputReserveTokens`: Long,
    val `toolReserveTokens`: Long,
    val `compactionReserveTokens`: Long,
    val `compactionPolicyId`: String,
    val `requestAccountingProfileId`: String,
    val `executionAssurance`: String,
    val `permissions`: List<WProfilePermissionsItem>,
    val `platform`: String,
    val `qualification`: String
)

internal typealias WProfiles = WProfile

internal data class WExecution(
    val `executionId`: UUID,
    val `principalId`: UUID,
    val `credentialId`: UUID,
    val `nativeHostId`: UUID,
    val `nativeSessionId`: String,
    val `workspaceId`: UUID
)

internal data class WExecutionIntent(
    val `controllerId`: UUID,
    val `executionId`: UUID,
    val `template`: String,
    val `nativeHostId`: UUID,
    val `nativeSessionId`: String
)

internal data class WAssignments(
    val `task`: WorkKey.TaskRevision,
    val `manifest`: WorkReference,
    val `profile`: WorkKey.Profile,
    val `generation`: Long,
    val `executionGeneration`: Long,
    val `execution`: WExecution?,
    val `binding`: WorkReference?,
    val `workspaceId`: UUID,
    val `requiredResources`: List<WorkKey.Resource>,
    val `reviewSubjects`: List<WorkReference>,
    val `state`: String,
    val `currentIntent`: WorkKey.Id?,
    val `checkpoint`: WorkReference?,
    val `recovery`: WorkReference?,
    val `intendedExecution`: WExecutionIntent,
    val `delegationId`: UUID?
)

internal data class WAssignmentBindings(
    val `task`: WorkKey.TaskRevision,
    val `manifest`: WorkReference,
    val `profile`: WorkKey.Profile,
    val `execution`: WExecution,
    val `workspaceId`: UUID,
    val `resources`: List<WorkKey.Resource>,
    val `intendedExecution`: WExecutionIntent,
    val `delegationId`: UUID,
    val `reviewSubjects`: List<WorkReference>
)

internal sealed interface WClaimsHolder

internal data class WClaimsHolderVariant0(
    val `kind`: String,
    val `subject`: WAssignmentSubject
)

internal data class WClaimsHolderVariant0Value(val value: WClaimsHolderVariant0) : WClaimsHolder

internal data class WClaimsHolderVariant1(
    val `kind`: String,
    val `integrationIntentId`: UUID
)

internal data class WClaimsHolderVariant1Value(val value: WClaimsHolderVariant1) : WClaimsHolder

internal data class WClaims(
    val `generation`: Long,
    val `holder`: WClaimsHolder?,
    val `workspaceId`: UUID?,
    val `state`: String,
    val `incompatible`: List<WorkKey.Resource>,
    val `basis`: WSource
)

internal data class WNativeIntents(
    val `assignment`: WAssignmentSubject,
    val `generation`: Long,
    val `correlationId`: UUID,
    val `operation`: String,
    val `expectedSession`: String?,
    val `expectedWorkspace`: UUID,
    val `outcome`: String,
    val `observations`: List<WSource>,
    val `reconciliation`: WorkReference?
)

internal data class WRecoveries(
    val `assignment`: WAssignmentSubject,
    val `decision`: String,
    val `basis`: List<WSource>,
    val `isolatedResources`: List<WorkKey.Resource>,
    val `retainedClaims`: List<WorkKey.Resource>,
    val `remainingUncertainty`: List<String>
)

internal data class WPending(
    val `id`: UUID,
    val `kind`: String,
    val `source`: WSource?,
    val `description`: String,
    val `responsibleTask`: WorkKey.TaskRevision
)

internal data class WCheckpoints(
    val `task`: WorkKey.TaskRevision,
    val `assignment`: WAssignmentSubject,
    val `manifest`: WorkReference,
    val `loadedView`: WorkKey.Revision,
    val `sourceCutId`: UUID,
    val `plan`: WorkKey.Revision,
    val `planGeneration`: Long,
    val `memory`: WorkKey.Revision,
    val `planSteps`: List<WStep>,
    val `artifacts`: List<WSnapshot>,
    val `pending`: List<WPending>,
    val `report`: WorkBodyReference,
    val `handoff`: String,
    val `retrospective`: WorkReference?,
    val `result`: WorkReference?,
    val `notification`: WorkKey.Id?
)

internal data class WDeliveries(
    val `assignment`: WAssignmentSubject,
    val `sourceCutId`: UUID,
    val `destinationTask`: WorkKey.TaskRevision,
    val `content`: WorkBodyReference,
    val `generation`: Long,
    val `state`: String,
    val `basis`: List<WSource>
)

internal data class WMemoryEntry(
    val `id`: UUID,
    val `status`: String,
    val `content`: WorkBodyReference,
    val `sources`: List<WSource>,
    val `remainingObligationIds`: List<UUID>,
    val `uncertainty`: String,
    val `exactRequiredFragments`: List<WorkBodyReference>
)

internal data class WMemory(
    val `task`: WorkKey.TaskRevision,
    val `assignment`: WAssignmentSubject,
    val `previous`: WorkKey.Revision?,
    val `sourceCutId`: UUID,
    val `entries`: List<WMemoryEntry>,
    val `gaps`: List<String>,
    val `pending`: List<WPending>
)

internal data class WRetrospectivesObservationsItem(
    val `observed`: WSource,
    val `expectation`: WSource?,
    val `newExpectation`: String?,
    val `consequence`: String,
    val `causeHypothesis`: String,
    val `uncertainty`: String,
    val `measure`: String,
    val `proposal`: WorkBodyReference?,
    val `status`: String,
    val `normativeDecision`: KnowledgeReference?,
    val `effectEvidence`: List<WorkReference>
)

internal data class WRetrospectives(
    val `task`: WorkKey.TaskRevision,
    val `assignment`: WAssignmentSubject,
    val `result`: WorkReference?,
    val `outcome`: String,
    val `observations`: List<WRetrospectivesObservationsItem>
)

internal data class WResults(
    val `task`: WorkKey.TaskRevision,
    val `assignment`: WAssignmentSubject,
    val `producer`: WExecution,
    val `manifest`: WorkReference,
    val `artifacts`: List<WSnapshot>,
    val `changes`: WorkBodyReference,
    val `claimedCriteria`: List<UUID>,
    val `evidence`: List<WorkReference>,
    val `retrospective`: WorkReference,
    val `pending`: List<WPending>
)

internal data class WGate(
    val `id`: UUID,
    val `criterionIds`: List<UUID>,
    val `boundary`: String,
    val `independent`: Boolean,
    val `stage`: String,
    val `groupId`: UUID?,
    val `deferred`: Boolean,
    val `reason`: String
)

internal data class WVerificationPlans(
    val `task`: WorkKey.TaskRevision,
    val `previous`: WorkKey.Revision?,
    val `criteria`: List<WCriterion>,
    val `gates`: List<WGate>,
    val `reason`: String,
    val `authorizedChanges`: List<KnowledgeReference>
)

internal data class WFinding(
    val `id`: UUID,
    val `category`: String,
    val `requirement`: WSource?,
    val `scenario`: String,
    val `expected`: String,
    val `actual`: String,
    val `evidence`: List<WorkReference>,
    val `affectedGateIds`: List<UUID>,
    val `affectedResultIds`: List<UUID>,
    val `blocking`: Boolean
)

internal data class WVerificationReports(
    val `task`: WorkKey.TaskRevision,
    val `assignment`: WAssignmentSubject,
    val `reviewer`: WExecution,
    val `subjects`: List<WorkReference>,
    val `plan`: WorkKey.Revision,
    val `gateIds`: List<UUID>,
    val `freshContextBasis`: WSource,
    val `readOnlySnapshotBasis`: List<WSource>,
    val `findings`: List<WFinding>,
    val `evidence`: List<WorkReference>,
    val `report`: WorkBodyReference,
    val `limits`: List<String>
)

internal data class WSignificantInput(
    val `source`: WSource,
    val `scope`: String
)

internal data class WEvidenceClauseCasesItem(
    val `clause`: WSource,
    val `caseId`: String,
    val `testId`: String
)

internal data class WEvidence(
    val `subjects`: List<WSource>,
    val `significantInputs`: List<WSignificantInput>,
    val `method`: String,
    val `boundary`: String,
    val `environment`: WorkBodyReference,
    val `runId`: UUID,
    val `outcome`: String,
    val `artifacts`: List<WorkBodyReference>,
    val `clauseCases`: List<WEvidenceClauseCasesItem>,
    val `limits`: List<String>,
    val `report`: WorkBodyReference
)

internal data class WDispositions(
    val `result`: WorkReference,
    val `report`: WorkReference,
    val `findingId`: UUID,
    val `gateIds`: List<UUID>,
    val `decision`: String,
    val `basis`: List<WSource>,
    val `replacementResult`: WorkReference?,
    val `ownerDecision`: KnowledgeReference?,
    val `recordedBy`: UUID
)

internal data class WAcceptances(
    val `result`: WorkReference,
    val `task`: WorkKey.TaskRevision,
    val `verificationPlan`: WorkKey.Revision,
    val `evidence`: List<WorkReference>,
    val `reports`: List<WorkReference>,
    val `dispositions`: List<WorkReference>,
    val `applicability`: WorkReference,
    val `criterionIds`: List<UUID>,
    val `permittedTargets`: List<UUID>,
    val `basis`: WorkBodyReference,
    val `acceptedBy`: UUID
)

internal data class WIntegrationIntents(
    val `result`: WorkReference,
    val `acceptance`: WorkReference,
    val `targetRepositoryId`: UUID,
    val `targetId`: UUID,
    val `expectedTarget`: WorkGitOid,
    val `expectedTree`: WorkGitOid,
    val `expectedDirtyDigest`: Digest?,
    val `requiredResources`: List<WorkKey.Resource>,
    val `correlationId`: UUID,
    val `generation`: Long,
    val `state`: String,
    val `observation`: WorkReference?
)

internal data class WIntegrations(
    val `intent`: WorkKey.Id,
    val `intentGeneration`: Long,
    val `result`: WorkReference,
    val `resultingSnapshot`: WSnapshot,
    val `applicability`: WorkReference,
    val `evidence`: List<WorkReference>,
    val `observations`: List<WSource>,
    val `integratedBy`: UUID,
    val `pending`: List<WPending>
)

internal data class WApplicabilityEntry(
    val `source`: WSource,
    val `areas`: List<String>,
    val `dependencies`: List<WSource>,
    val `state`: String,
    val `reason`: String
)

internal data class WApplicability(
    val `previous`: WorkKey.Revision?,
    val `entries`: List<WApplicabilityEntry>,
    val `basis`: List<WSource>
)

internal data class WEvidenceApplicabilityEvidenceItem(
    val `evidence`: WorkReference,
    val `state`: String,
    val `significantInputComparison`: List<WSignificantInput>,
    val `rationale`: String
)

internal data class WEvidenceApplicability(
    val `result`: WorkReference,
    val `evidence`: List<WEvidenceApplicabilityEvidenceItem>,
    val `heldGateIds`: List<UUID>,
    val `basis`: List<WSource>,
    val `assessedBy`: UUID
)

internal data class WSourceCutsNativeMessagesItem(
    val `messageId`: String,
    val `digest`: Digest
)

internal data class WSourceCuts(
    val `task`: WorkKey.TaskRevision,
    val `assignment`: WAssignmentSubject,
    val `nativeHostId`: UUID,
    val `nativeSessionId`: String,
    val `profile`: WorkKey.Profile,
    val `sourceFormatVersion`: Long,
    val `renderedRequestDigest`: Digest,
    val `packetDigest`: Digest,
    val `retainedLocatorId`: UUID,
    val `nativeMessages`: List<WSourceCutsNativeMessagesItem>,
    val `retainedFragments`: List<WorkBodyReference>,
    val `pending`: List<WPending>
)

internal data class WControllerContinuationsPrevious(
    val `key`: WorkKey.ControllerContinuation,
    val `digest`: Digest
)

internal data class WNativeSourceBasisNativeMessagesItem(
    val `messageId`: String,
    val `digest`: Digest
)

internal data class WNativeSourceBasis(
    val `nativeHostId`: UUID,
    val `nativeSessionId`: String,
    val `sourceFormatVersion`: Long,
    val `renderedRequestDigest`: Digest,
    val `packetDigest`: Digest,
    val `retainedLocatorId`: UUID,
    val `nativeMessages`: List<WNativeSourceBasisNativeMessagesItem>,
    val `retainedFragments`: List<WorkBodyReference>
)

internal data class WControllerContinuationsFrontierItemAssignmentsItem(
    val `assignmentId`: UUID,
    val `recordGeneration`: Long,
    val `executionGeneration`: Long
)

internal data class WControllerContinuationsFrontierItem(
    val `task`: WorkKey.TaskRevision,
    val `assignments`: List<WControllerContinuationsFrontierItemAssignmentsItem>,
    val `obligationIds`: List<UUID>
)

internal data class WControllerContinuationsEntriesItem(
    val `tasks`: List<WorkKey.TaskRevision>,
    val `entry`: WMemoryEntry
)

internal data class WControllerContinuations(
    val `profile`: WorkKey.Profile,
    val `previous`: WControllerContinuationsPrevious?,
    val `source`: WNativeSourceBasis,
    val `frontier`: List<WControllerContinuationsFrontierItem>,
    val `entries`: List<WControllerContinuationsEntriesItem>,
    val `gaps`: List<String>,
    val `pending`: List<WPending>,
    val `report`: WorkBodyReference
)

internal data class CCreateTaskRevisionNewVerificationPlan(
    val `key`: WorkKey.Revision,
    val `data`: WVerificationPlans
)

internal data class CCreateTaskRevisionBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CCreateTaskRevision(
    val `key`: WorkKey.TaskRevision,
    val `data`: WTaskRevisions,
    val `newVerificationPlan`: CCreateTaskRevisionNewVerificationPlan?,
    val `bodies`: List<CCreateTaskRevisionBodiesItem>
)

internal data class CSetTaskHeadBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CSetTaskHead(
    val `key`: WorkKey.Task,
    val `expectedGeneration`: Long?,
    val `title`: String,
    val `draft`: WorkKey.TaskRevision?,
    val `executable`: WorkKey.TaskRevision?,
    val `bodies`: List<CSetTaskHeadBodiesItem>
)

internal data class CPinInputManifestBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CPinInputManifest(
    val `key`: WorkKey.Id,
    val `data`: WInputManifests,
    val `bodies`: List<CPinInputManifestBodiesItem>
)

internal data class CCreateProfileBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CCreateProfile(
    val `key`: WorkKey.Profile,
    val `data`: WProfiles,
    val `bodies`: List<CCreateProfileBodiesItem>
)

internal data class CReviseVerificationPlanBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CReviseVerificationPlan(
    val `key`: WorkKey.Revision,
    val `data`: WVerificationPlans,
    val `bodies`: List<CReviseVerificationPlanBodiesItem>
)

internal data class CReviseApplicabilityBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CReviseApplicability(
    val `key`: WorkKey.Revision,
    val `data`: WApplicability,
    val `bodies`: List<CReviseApplicabilityBodiesItem>
)

internal data class CUpdatePlanBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CUpdatePlan(
    val `key`: WorkKey.Revision,
    val `task`: WorkKey.TaskRevision,
    val `expectedGeneration`: Long?,
    val `steps`: List<WStep>,
    val `reason`: String,
    val `previousCheckpoint`: WorkReference?,
    val `bodies`: List<CUpdatePlanBodiesItem>,
    val `assignment`: WAssignmentSubject?
)

internal data class CReserveAssignmentResourcesItem(
    val `resource`: WorkKey.Resource,
    val `expectedGeneration`: Long?
)

internal data class CReserveAssignmentBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CReserveAssignment(
    val `assignmentId`: UUID,
    val `task`: WorkKey.TaskRevision,
    val `manifest`: WorkReference,
    val `profile`: WorkKey.Profile,
    val `workspaceId`: UUID,
    val `resources`: List<CReserveAssignmentResourcesItem>,
    val `reviewSubjects`: List<WorkReference>,
    val `intentId`: UUID,
    val `correlationId`: UUID,
    val `bodies`: List<CReserveAssignmentBodiesItem>,
    val `intendedExecution`: WExecutionIntent
)

internal data class CBindAssignmentBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CBindAssignment(
    val `assignmentId`: UUID,
    val `expectedGeneration`: Long,
    val `intentId`: UUID,
    val `expectedIntentGeneration`: Long,
    val `nativeHostId`: UUID,
    val `nativeSessionId`: String,
    val `workspaceId`: UUID,
    val `observations`: List<WSource>,
    val `bodies`: List<CBindAssignmentBodiesItem>
)

internal data class CRequestNativeTransitionBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CRequestNativeTransition(
    val `assignmentId`: UUID,
    val `expectedGeneration`: Long,
    val `intentId`: UUID,
    val `correlationId`: UUID,
    val `transition`: String,
    val `checkpoint`: WorkReference?,
    val `bodies`: List<CRequestNativeTransitionBodiesItem>
)

internal data class CObserveNativeTransitionBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CObserveNativeTransition(
    val `assignmentId`: UUID,
    val `expectedGeneration`: Long,
    val `intentId`: UUID,
    val `expectedIntentGeneration`: Long,
    val `outcome`: String,
    val `observations`: List<WSource>,
    val `bodies`: List<CObserveNativeTransitionBodiesItem>
)

internal data class CRecordRecoveryBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CRecordRecovery(
    val `key`: WorkKey.Id,
    val `expectedGeneration`: Long,
    val `data`: WRecoveries,
    val `bodies`: List<CRecordRecoveryBodiesItem>
)

internal data class CRebindAssignmentBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CRebindAssignment(
    val `assignmentId`: UUID,
    val `expectedGeneration`: Long,
    val `task`: WorkKey.TaskRevision,
    val `manifest`: WorkReference,
    val `profile`: WorkKey.Profile,
    val `checkpoint`: WorkReference,
    val `recovery`: WorkReference,
    val `intentId`: UUID,
    val `correlationId`: UUID,
    val `bodies`: List<CRebindAssignmentBodiesItem>,
    val `intendedExecution`: WExecutionIntent
)

internal data class CCompleteAssignmentBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CCompleteAssignment(
    val `assignmentId`: UUID,
    val `expectedGeneration`: Long,
    val `checkpoint`: WorkReference,
    val `completionBasis`: List<WSource>,
    val `releaseResources`: List<WorkKey.Resource>,
    val `bodies`: List<CCompleteAssignmentBodiesItem>
)

internal data class CRecordLoadedViewData(
    val `task`: WorkKey.TaskRevision,
    val `manifest`: WorkReference,
    val `assignment`: WAssignmentSubject,
    val `previous`: WorkKey.Revision?,
    val `additions`: List<WLoadedItem>,
    val `discoveredGaps`: List<String>
)

internal data class CRecordLoadedViewBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CRecordLoadedView(
    val `key`: WorkKey.Revision,
    val `data`: CRecordLoadedViewData,
    val `bodies`: List<CRecordLoadedViewBodiesItem>
)

internal data class CRecordMemoryData(
    val `task`: WorkKey.TaskRevision,
    val `assignment`: WAssignmentSubject,
    val `previous`: WorkKey.Revision?,
    val `sourceCutId`: UUID,
    val `entries`: List<WMemoryEntry>,
    val `gaps`: List<String>,
    val `pending`: List<WPending>
)

internal data class CRecordMemoryBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CRecordMemory(
    val `key`: WorkKey.Revision,
    val `data`: CRecordMemoryData,
    val `bodies`: List<CRecordMemoryBodiesItem>
)

internal data class CRecordCheckpointData(
    val `task`: WorkKey.TaskRevision,
    val `assignment`: WAssignmentSubject,
    val `manifest`: WorkReference,
    val `loadedView`: WorkKey.Revision,
    val `sourceCutId`: UUID,
    val `plan`: WorkKey.Revision,
    val `planGeneration`: Long,
    val `memory`: WorkKey.Revision,
    val `planSteps`: List<WStep>,
    val `artifacts`: List<WSnapshot>,
    val `pending`: List<WPending>,
    val `report`: WorkBodyReference,
    val `handoff`: String,
    val `retrospective`: WorkReference?,
    val `result`: WorkReference?,
    val `notification`: WorkKey.Id?
)

internal data class CRecordCheckpointBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CRecordCheckpoint(
    val `key`: WorkKey.Id,
    val `data`: CRecordCheckpointData,
    val `expectedAssignmentGeneration`: Long,
    val `bodies`: List<CRecordCheckpointBodiesItem>
)

internal data class CRecordRetrospectiveDataObservationsItem(
    val `observed`: WSource,
    val `expectation`: WSource?,
    val `newExpectation`: String?,
    val `consequence`: String,
    val `causeHypothesis`: String,
    val `uncertainty`: String,
    val `measure`: String,
    val `proposal`: WorkBodyReference?,
    val `status`: String,
    val `normativeDecision`: KnowledgeReference?,
    val `effectEvidence`: List<WorkReference>
)

internal data class CRecordRetrospectiveData(
    val `task`: WorkKey.TaskRevision,
    val `assignment`: WAssignmentSubject,
    val `result`: WorkReference?,
    val `outcome`: String,
    val `observations`: List<CRecordRetrospectiveDataObservationsItem>
)

internal data class CRecordRetrospectiveBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CRecordRetrospective(
    val `key`: WorkKey.Id,
    val `data`: CRecordRetrospectiveData,
    val `bodies`: List<CRecordRetrospectiveBodiesItem>
)

internal data class CSubmitResultData(
    val `task`: WorkKey.TaskRevision,
    val `assignment`: WAssignmentSubject,
    val `manifest`: WorkReference,
    val `artifacts`: List<WSnapshot>,
    val `changes`: WorkBodyReference,
    val `claimedCriteria`: List<UUID>,
    val `evidence`: List<WorkReference>,
    val `retrospective`: WorkReference,
    val `pending`: List<WPending>
)

internal data class CSubmitResultBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CSubmitResult(
    val `key`: WorkKey.Id,
    val `data`: CSubmitResultData,
    val `bodies`: List<CSubmitResultBodiesItem>
)

internal data class CSubmitVerificationReportData(
    val `task`: WorkKey.TaskRevision,
    val `assignment`: WAssignmentSubject,
    val `subjects`: List<WorkReference>,
    val `plan`: WorkKey.Revision,
    val `gateIds`: List<UUID>,
    val `freshContextBasis`: WSource,
    val `readOnlySnapshotBasis`: List<WSource>,
    val `findings`: List<WFinding>,
    val `evidence`: List<WorkReference>,
    val `report`: WorkBodyReference,
    val `limits`: List<String>
)

internal data class CSubmitVerificationReportBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CSubmitVerificationReport(
    val `key`: WorkKey.Id,
    val `data`: CSubmitVerificationReportData,
    val `bodies`: List<CSubmitVerificationReportBodiesItem>
)

internal data class CRecordEvidenceDataClauseCasesItem(
    val `clause`: WSource,
    val `caseId`: String,
    val `testId`: String
)

internal data class CRecordEvidenceData(
    val `subjects`: List<WSource>,
    val `significantInputs`: List<WSignificantInput>,
    val `method`: String,
    val `boundary`: String,
    val `environment`: WorkBodyReference,
    val `runId`: UUID,
    val `outcome`: String,
    val `artifacts`: List<WorkBodyReference>,
    val `clauseCases`: List<CRecordEvidenceDataClauseCasesItem>,
    val `limits`: List<String>,
    val `report`: WorkBodyReference
)

internal data class CRecordEvidenceBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CRecordEvidence(
    val `key`: WorkKey.Id,
    val `data`: CRecordEvidenceData,
    val `bodies`: List<CRecordEvidenceBodiesItem>
)

internal data class CRecordDeliveryBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CRecordDelivery(
    val `key`: WorkKey.Id,
    val `assignment`: WAssignmentSubject,
    val `sourceCutId`: UUID,
    val `destinationTask`: WorkKey.TaskRevision,
    val `content`: WorkBodyReference,
    val `expectedGeneration`: Long?,
    val `state`: String,
    val `basis`: List<WSource>,
    val `bodies`: List<CRecordDeliveryBodiesItem>
)

internal data class CRecordEvidenceApplicabilityDataEvidenceItem(
    val `evidence`: WorkReference,
    val `state`: String,
    val `significantInputComparison`: List<WSignificantInput>,
    val `rationale`: String
)

internal data class CRecordEvidenceApplicabilityData(
    val `result`: WorkReference,
    val `evidence`: List<CRecordEvidenceApplicabilityDataEvidenceItem>,
    val `heldGateIds`: List<UUID>,
    val `basis`: List<WSource>
)

internal data class CRecordEvidenceApplicabilityBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CRecordEvidenceApplicability(
    val `key`: WorkKey.Id,
    val `data`: CRecordEvidenceApplicabilityData,
    val `bodies`: List<CRecordEvidenceApplicabilityBodiesItem>
)

internal data class CRecordDispositionData(
    val `result`: WorkReference,
    val `report`: WorkReference,
    val `findingId`: UUID,
    val `gateIds`: List<UUID>,
    val `decision`: String,
    val `basis`: List<WSource>,
    val `replacementResult`: WorkReference?,
    val `ownerDecision`: KnowledgeReference?
)

internal data class CRecordDispositionBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CRecordDisposition(
    val `key`: WorkKey.Id,
    val `data`: CRecordDispositionData,
    val `bodies`: List<CRecordDispositionBodiesItem>
)

internal data class CAcceptResultData(
    val `result`: WorkReference,
    val `task`: WorkKey.TaskRevision,
    val `verificationPlan`: WorkKey.Revision,
    val `evidence`: List<WorkReference>,
    val `reports`: List<WorkReference>,
    val `dispositions`: List<WorkReference>,
    val `applicability`: WorkReference,
    val `criterionIds`: List<UUID>,
    val `permittedTargets`: List<UUID>,
    val `basis`: WorkBodyReference
)

internal data class CAcceptResultBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CAcceptResult(
    val `key`: WorkKey.Id,
    val `data`: CAcceptResultData,
    val `bodies`: List<CAcceptResultBodiesItem>
)

internal data class CPrepareIntegrationResourcesItem(
    val `resource`: WorkKey.Resource,
    val `expectedGeneration`: Long?
)

internal data class CPrepareIntegrationBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CPrepareIntegration(
    val `key`: WorkKey.Id,
    val `result`: WorkReference,
    val `acceptance`: WorkReference,
    val `targetRepositoryId`: UUID,
    val `targetId`: UUID,
    val `expectedTarget`: WorkGitOid,
    val `expectedTree`: WorkGitOid,
    val `expectedDirtyDigest`: Digest?,
    val `resources`: List<CPrepareIntegrationResourcesItem>,
    val `correlationId`: UUID,
    val `bodies`: List<CPrepareIntegrationBodiesItem>
)

internal data class CObserveIntegrationBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CObserveIntegration(
    val `key`: WorkKey.Id,
    val `intent`: WorkKey.Id,
    val `expectedGeneration`: Long,
    val `outcome`: String,
    val `resultingSnapshot`: WSnapshot?,
    val `applicability`: WorkReference?,
    val `evidence`: List<WorkReference>,
    val `observations`: List<WSource>,
    val `pending`: List<WPending>,
    val `bodies`: List<CObserveIntegrationBodiesItem>
)

internal data class CRecordSourceCutBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CRecordSourceCut(
    val `key`: WorkKey.Id,
    val `data`: WSourceCuts,
    val `bodies`: List<CRecordSourceCutBodiesItem>
)

internal data class CRecordControllerContinuationBodiesItem(
    val `digest`: Digest,
    val `body`: String
)

internal data class CRecordControllerContinuation(
    val `key`: WorkKey.ControllerContinuation,
    val `data`: WControllerContinuations,
    val `bodies`: List<CRecordControllerContinuationBodiesItem>
)

internal data class RListWorkRecords(
    val `family`: String,
    val `taskId`: UUID?,
    val `cursor`: UUID?,
    val `pageSize`: Long
)

internal sealed interface RReadWorkBodyRecord

internal typealias RReadWorkBodyRecordVariant0 = WorkReference

internal data class RReadWorkBodyRecordVariant0Value(val value: RReadWorkBodyRecordVariant0) : RReadWorkBodyRecord

internal sealed interface WMutableSelector

internal data class WMutableSelectorVariant0(
    val `family`: String,
    val `key`: WorkKey.Task,
    val `generation`: Long
)

internal data class WMutableSelectorVariant0Value(val value: WMutableSelectorVariant0) : WMutableSelector

internal data class WMutableSelectorVariant1(
    val `family`: String,
    val `key`: WorkKey.Revision,
    val `generation`: Long
)

internal data class WMutableSelectorVariant1Value(val value: WMutableSelectorVariant1) : WMutableSelector

internal data class WMutableSelectorVariant2(
    val `family`: String,
    val `key`: WorkKey.Id,
    val `generation`: Long
)

internal data class WMutableSelectorVariant2Value(val value: WMutableSelectorVariant2) : WMutableSelector

internal data class WMutableSelectorVariant3(
    val `family`: String,
    val `key`: WorkKey.Resource,
    val `generation`: Long
)

internal data class WMutableSelectorVariant3Value(val value: WMutableSelectorVariant3) : WMutableSelector

internal data class WMutableSelectorVariant4(
    val `family`: String,
    val `key`: WorkKey.Id,
    val `generation`: Long
)

internal data class WMutableSelectorVariant4Value(val value: WMutableSelectorVariant4) : WMutableSelector

internal data class WMutableSelectorVariant5(
    val `family`: String,
    val `key`: WorkKey.Id,
    val `generation`: Long
)

internal data class WMutableSelectorVariant5Value(val value: WMutableSelectorVariant5) : WMutableSelector

internal data class WMutableSelectorVariant6(
    val `family`: String,
    val `key`: WorkKey.Id,
    val `generation`: Long
)

internal data class WMutableSelectorVariant6Value(val value: WMutableSelectorVariant6) : WMutableSelector

internal typealias RReadWorkBodyRecordVariant1 = WMutableSelector

internal data class RReadWorkBodyRecordVariant1Value(val value: RReadWorkBodyRecordVariant1) : RReadWorkBodyRecord

internal data class RReadWorkBody(
    val `digest`: Digest,
    val `record`: RReadWorkBodyRecord
)

internal sealed interface WorkPayload

internal data class WTaskRevisionsPayload(val value: WTaskRevisions) : WorkPayload

internal data class WTaskHeadsPayload(val value: WTaskHeads) : WorkPayload

internal data class WPlansPayload(val value: WPlans) : WorkPayload

internal data class WInputManifestsPayload(val value: WInputManifests) : WorkPayload

internal data class WLoadedViewsPayload(val value: WLoadedViews) : WorkPayload

internal data class WProfilesPayload(val value: WProfiles) : WorkPayload

internal data class WAssignmentsPayload(val value: WAssignments) : WorkPayload

internal data class WAssignmentBindingsPayload(val value: WAssignmentBindings) : WorkPayload

internal data class WClaimsPayload(val value: WClaims) : WorkPayload

internal data class WNativeIntentsPayload(val value: WNativeIntents) : WorkPayload

internal data class WRecoveriesPayload(val value: WRecoveries) : WorkPayload

internal data class WCheckpointsPayload(val value: WCheckpoints) : WorkPayload

internal data class WDeliveriesPayload(val value: WDeliveries) : WorkPayload

internal data class WMemoryPayload(val value: WMemory) : WorkPayload

internal data class WRetrospectivesPayload(val value: WRetrospectives) : WorkPayload

internal data class WResultsPayload(val value: WResults) : WorkPayload

internal data class WVerificationPlansPayload(val value: WVerificationPlans) : WorkPayload

internal data class WVerificationReportsPayload(val value: WVerificationReports) : WorkPayload

internal data class WEvidencePayload(val value: WEvidence) : WorkPayload

internal data class WDispositionsPayload(val value: WDispositions) : WorkPayload

internal data class WAcceptancesPayload(val value: WAcceptances) : WorkPayload

internal data class WIntegrationIntentsPayload(val value: WIntegrationIntents) : WorkPayload

internal data class WIntegrationsPayload(val value: WIntegrations) : WorkPayload

internal data class WApplicabilityPayload(val value: WApplicability) : WorkPayload

internal data class WEvidenceApplicabilityPayload(val value: WEvidenceApplicability) : WorkPayload

internal data class WSourceCutsPayload(val value: WSourceCuts) : WorkPayload

internal data class WControllerContinuationsPayload(val value: WControllerContinuations) : WorkPayload

internal sealed interface WorkOperation { val name: String }

internal data class CCreateTaskRevisionOperation(val value: CCreateTaskRevision) : WorkOperation { override val name get() = "create_task_revision" }

internal data class CSetTaskHeadOperation(val value: CSetTaskHead) : WorkOperation { override val name get() = "set_task_head" }

internal data class CPinInputManifestOperation(val value: CPinInputManifest) : WorkOperation { override val name get() = "pin_input_manifest" }

internal data class CCreateProfileOperation(val value: CCreateProfile) : WorkOperation { override val name get() = "create_profile" }

internal data class CReviseVerificationPlanOperation(val value: CReviseVerificationPlan) : WorkOperation { override val name get() = "revise_verification_plan" }

internal data class CReviseApplicabilityOperation(val value: CReviseApplicability) : WorkOperation { override val name get() = "revise_applicability" }

internal data class CUpdatePlanOperation(val value: CUpdatePlan) : WorkOperation { override val name get() = "update_plan" }

internal data class CReserveAssignmentOperation(val value: CReserveAssignment) : WorkOperation { override val name get() = "reserve_assignment" }

internal data class CBindAssignmentOperation(val value: CBindAssignment) : WorkOperation { override val name get() = "bind_assignment" }

internal data class CRequestNativeTransitionOperation(val value: CRequestNativeTransition) : WorkOperation { override val name get() = "request_native_transition" }

internal data class CObserveNativeTransitionOperation(val value: CObserveNativeTransition) : WorkOperation { override val name get() = "observe_native_transition" }

internal data class CRecordRecoveryOperation(val value: CRecordRecovery) : WorkOperation { override val name get() = "record_recovery" }

internal data class CRebindAssignmentOperation(val value: CRebindAssignment) : WorkOperation { override val name get() = "rebind_assignment" }

internal data class CCompleteAssignmentOperation(val value: CCompleteAssignment) : WorkOperation { override val name get() = "complete_assignment" }

internal data class CRecordLoadedViewOperation(val value: CRecordLoadedView) : WorkOperation { override val name get() = "record_loaded_view" }

internal data class CRecordMemoryOperation(val value: CRecordMemory) : WorkOperation { override val name get() = "record_memory" }

internal data class CRecordCheckpointOperation(val value: CRecordCheckpoint) : WorkOperation { override val name get() = "record_checkpoint" }

internal data class CRecordRetrospectiveOperation(val value: CRecordRetrospective) : WorkOperation { override val name get() = "record_retrospective" }

internal data class CSubmitResultOperation(val value: CSubmitResult) : WorkOperation { override val name get() = "submit_result" }

internal data class CSubmitVerificationReportOperation(val value: CSubmitVerificationReport) : WorkOperation { override val name get() = "submit_verification_report" }

internal data class CRecordEvidenceOperation(val value: CRecordEvidence) : WorkOperation { override val name get() = "record_evidence" }

internal data class CRecordDeliveryOperation(val value: CRecordDelivery) : WorkOperation { override val name get() = "record_delivery" }

internal data class CRecordEvidenceApplicabilityOperation(val value: CRecordEvidenceApplicability) : WorkOperation { override val name get() = "record_evidence_applicability" }

internal data class CRecordDispositionOperation(val value: CRecordDisposition) : WorkOperation { override val name get() = "record_disposition" }

internal data class CAcceptResultOperation(val value: CAcceptResult) : WorkOperation { override val name get() = "accept_result" }

internal data class CPrepareIntegrationOperation(val value: CPrepareIntegration) : WorkOperation { override val name get() = "prepare_integration" }

internal data class CObserveIntegrationOperation(val value: CObserveIntegration) : WorkOperation { override val name get() = "observe_integration" }

internal data class CRecordSourceCutOperation(val value: CRecordSourceCut) : WorkOperation { override val name get() = "record_source_cut" }

internal data class CRecordControllerContinuationOperation(val value: CRecordControllerContinuation) : WorkOperation { override val name get() = "record_controller_continuation" }
