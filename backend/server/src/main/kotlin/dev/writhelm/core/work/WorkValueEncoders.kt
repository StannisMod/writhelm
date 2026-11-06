// SPDX-License-Identifier: AGPL-3.0-only
// Generated from frozen-v2; see external CORE result for exact input identity.
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import java.util.UUID

internal fun encodeWSnapshot(v: WSnapshot): JsonValue = obj("repositoryId" to uuid(v.`repositoryId`),"commit" to (v.`commit`?.let { workOid(it) } ?: JNull),"tree" to workOid(v.`tree`),"dirtyContentDigest" to (v.`dirtyContentDigest`?.let { digest(it) } ?: JNull),"retainedLocatorId" to uuid(v.`retainedLocatorId`))

internal fun encodeWBase(v: WBase): JsonValue = obj("code" to workArray(v.`code`, 0, 128, false) { element -> encodeWSnapshot(element) },"knowledgeManifestId" to uuid(v.`knowledgeManifestId`),"knowledgeManifestDigest" to digest(v.`knowledgeManifestDigest`))

internal fun encodeWScope(v: WScope): JsonValue = obj("read" to str(v.`read`),"write" to str(v.`write`),"decision" to str(v.`decision`),"exclusions" to workArray(v.`exclusions`, 0, 256, false) { element -> str(element) })

internal fun encodeWDependency(v: WDependency): JsonValue = obj("producer" to StrictJson.decode(identityCodec.encodeKey(v.`producer`)),"readiness" to str(v.`readiness`),"selectedResult" to (v.`selectedResult`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"deferredGateIds" to workArray(v.`deferredGateIds`, 0, 256, true) { element -> uuid(element) },"rationale" to str(v.`rationale`))

internal fun encodeWSourceVariant0(v: WSourceVariant0): JsonValue = obj("kind" to str(v.`kind`),"reference" to reference(v.`reference`))

internal fun encodeWSourceVariant1(v: WSourceVariant1): JsonValue = obj("kind" to str(v.`kind`),"artifact" to encodeWSnapshot(v.`artifact`))

internal fun encodeWSourceVariant2(v: WSourceVariant2): JsonValue = obj("kind" to str(v.`kind`),"reference" to StrictJson.decode(identityCodec.encodeReference(v.`reference`)))

internal fun encodeWSource(v: WSource): JsonValue = when(v) { is WSourceVariant0Value -> encodeWSourceVariant0(v.value); is WSourceVariant1Value -> encodeWSourceVariant1(v.value); is WSourceVariant2Value -> encodeWSourceVariant2(v.value) }

internal fun encodeWCriterion(v: WCriterion): JsonValue = obj("id" to uuid(v.`id`),"statement" to str(v.`statement`),"clauses" to workArray(v.`clauses`, 0, 256, false) { element -> encodeWSource(element) })

internal fun encodeWTaskRevisions(v: WTaskRevisions): JsonValue = obj("kind" to str(v.`kind`),"state" to str(v.`state`),"objective" to str(v.`objective`),"base" to (v.`base`?.let { encodeWBase(it) } ?: JNull),"scope" to encodeWScope(v.`scope`),"constraints" to workArray(v.`constraints`, 0, 256, false) { element -> str(element) },"dependencies" to workArray(v.`dependencies`, 0, 256, false) { element -> encodeWDependency(element) },"criteria" to workArray(v.`criteria`, 1, 256, false) { element -> encodeWCriterion(element) },"escalation" to workArray(v.`escalation`, 1, 256, false) { element -> str(element) },"expectedOutput" to str(v.`expectedOutput`),"executionProfile" to (v.`executionProfile`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"verificationPlan" to (v.`verificationPlan`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"approvedDesign" to workArray(v.`approvedDesign`, 0, 256, false) { element -> reference(element) },"unresolved" to workArray(v.`unresolved`, 0, 256, false) { element -> str(element) })

internal fun encodeWTaskHeads(v: WTaskHeads): JsonValue = obj("generation" to JNumber(v.`generation`),"title" to str(v.`title`),"draft" to (v.`draft`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"executable" to (v.`executable`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull))

internal fun encodeWStep(v: WStep): JsonValue = obj("id" to uuid(v.`id`),"description" to str(v.`description`),"state" to str(v.`state`),"obligations" to workArray(v.`obligations`, 0, 256, false) { element -> str(element) },"blockers" to workArray(v.`blockers`, 0, 256, false) { element -> encodeWSource(element) })

internal fun encodeWAssignmentSubject(v: WAssignmentSubject): JsonValue = obj("assignmentId" to uuid(v.`assignmentId`),"generation" to JNumber(v.`generation`))

internal fun encodeWPlans(v: WPlans): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"generation" to JNumber(v.`generation`),"steps" to workArray(v.`steps`, 0, 256, false) { element -> encodeWStep(element) },"reason" to str(v.`reason`),"previousCheckpoint" to (v.`previousCheckpoint`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"assignment" to (v.`assignment`?.let { encodeWAssignmentSubject(it) } ?: JNull))

internal fun encodeWManifestItem(v: WManifestItem): JsonValue = obj("source" to encodeWSource(v.`source`),"mandatory" to JBoolean(v.`mandatory`),"reason" to str(v.`reason`),"exactFragments" to workArray(v.`exactFragments`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeBodyReference(element)) })

internal fun encodeWInputManifests(v: WInputManifests): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"base" to encodeWBase(v.`base`),"applicability" to StrictJson.decode(identityCodec.encodeKey(v.`applicability`)),"items" to workArray(v.`items`, 1, 1024, false) { element -> encodeWManifestItem(element) },"declaredClosureAssessment" to encodeWSource(v.`declaredClosureAssessment`),"knownGaps" to workArray(v.`knownGaps`, 0, 256, false) { element -> str(element) })

internal fun encodeWLoadedItem(v: WLoadedItem): JsonValue = obj("source" to encodeWSource(v.`source`),"reason" to str(v.`reason`),"obligationIds" to workArray(v.`obligationIds`, 0, 256, true) { element -> uuid(element) })

internal fun encodeWLoadedViews(v: WLoadedViews): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"manifest" to StrictJson.decode(identityCodec.encodeReference(v.`manifest`)),"assignment" to encodeWAssignmentSubject(v.`assignment`),"previous" to (v.`previous`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"additions" to workArray(v.`additions`, 0, 256, false) { element -> encodeWLoadedItem(element) },"discoveredGaps" to workArray(v.`discoveredGaps`, 0, 256, false) { element -> str(element) })

internal fun encodeWProfilePermissionsItem(v: WProfilePermissionsItem): JsonValue = obj("tool" to str(v.`tool`),"action" to str(v.`action`),"scope" to str(v.`scope`))

internal fun encodeWProfile(v: WProfile): JsonValue = obj("nativeProfileId" to str(v.`nativeProfileId`),"qualificationEvidence" to workArray(v.`qualificationEvidence`, 0, 256, false) { element -> encodeWSource(element) },"model" to StrictJson.decode(identityCodec.encodeModelReference(v.`model`)),"route" to str(v.`route`),"effort" to str(v.`effort`),"contextTokens" to JNumber(v.`contextTokens`),"outputReserveTokens" to JNumber(v.`outputReserveTokens`),"toolReserveTokens" to JNumber(v.`toolReserveTokens`),"compactionReserveTokens" to JNumber(v.`compactionReserveTokens`),"compactionPolicyId" to str(v.`compactionPolicyId`),"requestAccountingProfileId" to str(v.`requestAccountingProfileId`),"executionAssurance" to str(v.`executionAssurance`),"permissions" to workArray(v.`permissions`, 0, 256, false) { element -> encodeWProfilePermissionsItem(element) },"platform" to str(v.`platform`),"qualification" to str(v.`qualification`))

internal fun encodeWProfiles(v: WProfiles): JsonValue = encodeWProfile(v)

internal fun encodeWExecution(v: WExecution): JsonValue = obj("executionId" to uuid(v.`executionId`),"principalId" to uuid(v.`principalId`),"credentialId" to uuid(v.`credentialId`),"nativeHostId" to uuid(v.`nativeHostId`),"nativeSessionId" to str(v.`nativeSessionId`),"workspaceId" to uuid(v.`workspaceId`))

internal fun encodeWExecutionIntent(v: WExecutionIntent): JsonValue = obj("controllerId" to uuid(v.`controllerId`),"executionId" to uuid(v.`executionId`),"template" to str(v.`template`),"nativeHostId" to uuid(v.`nativeHostId`),"nativeSessionId" to str(v.`nativeSessionId`))

internal fun encodeWAssignments(v: WAssignments): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"manifest" to StrictJson.decode(identityCodec.encodeReference(v.`manifest`)),"profile" to StrictJson.decode(identityCodec.encodeKey(v.`profile`)),"generation" to JNumber(v.`generation`),"executionGeneration" to JNumber(v.`executionGeneration`),"execution" to (v.`execution`?.let { encodeWExecution(it) } ?: JNull),"binding" to (v.`binding`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"workspaceId" to uuid(v.`workspaceId`),"requiredResources" to workArray(v.`requiredResources`, 1, 256, true) { element -> StrictJson.decode(identityCodec.encodeKey(element)) },"reviewSubjects" to workArray(v.`reviewSubjects`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"state" to str(v.`state`),"currentIntent" to (v.`currentIntent`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"checkpoint" to (v.`checkpoint`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"recovery" to (v.`recovery`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"intendedExecution" to encodeWExecutionIntent(v.`intendedExecution`),"delegationId" to (v.`delegationId`?.let { uuid(it) } ?: JNull))

internal fun encodeWAssignmentBindings(v: WAssignmentBindings): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"manifest" to StrictJson.decode(identityCodec.encodeReference(v.`manifest`)),"profile" to StrictJson.decode(identityCodec.encodeKey(v.`profile`)),"execution" to encodeWExecution(v.`execution`),"workspaceId" to uuid(v.`workspaceId`),"resources" to workArray(v.`resources`, 1, 256, true) { element -> StrictJson.decode(identityCodec.encodeKey(element)) },"intendedExecution" to encodeWExecutionIntent(v.`intendedExecution`),"delegationId" to uuid(v.`delegationId`),"reviewSubjects" to workArray(v.`reviewSubjects`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) })

internal fun encodeWClaimsHolderVariant0(v: WClaimsHolderVariant0): JsonValue = obj("kind" to str(v.`kind`),"subject" to encodeWAssignmentSubject(v.`subject`))

internal fun encodeWClaimsHolderVariant1(v: WClaimsHolderVariant1): JsonValue = obj("kind" to str(v.`kind`),"integrationIntentId" to uuid(v.`integrationIntentId`))

internal fun encodeWClaimsHolder(v: WClaimsHolder): JsonValue = when(v) { is WClaimsHolderVariant0Value -> encodeWClaimsHolderVariant0(v.value); is WClaimsHolderVariant1Value -> encodeWClaimsHolderVariant1(v.value) }

internal fun encodeWClaims(v: WClaims): JsonValue = obj("generation" to JNumber(v.`generation`),"holder" to (v.`holder`?.let { encodeWClaimsHolder(it) } ?: JNull),"workspaceId" to (v.`workspaceId`?.let { uuid(it) } ?: JNull),"state" to str(v.`state`),"incompatible" to workArray(v.`incompatible`, 0, 256, true) { element -> StrictJson.decode(identityCodec.encodeKey(element)) },"basis" to encodeWSource(v.`basis`))

internal fun encodeWNativeIntents(v: WNativeIntents): JsonValue = obj("assignment" to encodeWAssignmentSubject(v.`assignment`),"generation" to JNumber(v.`generation`),"correlationId" to uuid(v.`correlationId`),"operation" to str(v.`operation`),"expectedSession" to (v.`expectedSession`?.let { str(it) } ?: JNull),"expectedWorkspace" to uuid(v.`expectedWorkspace`),"outcome" to str(v.`outcome`),"observations" to workArray(v.`observations`, 0, 256, false) { element -> encodeWSource(element) },"reconciliation" to (v.`reconciliation`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull))

internal fun encodeWRecoveries(v: WRecoveries): JsonValue = obj("assignment" to encodeWAssignmentSubject(v.`assignment`),"decision" to str(v.`decision`),"basis" to workArray(v.`basis`, 1, 256, false) { element -> encodeWSource(element) },"isolatedResources" to workArray(v.`isolatedResources`, 0, 256, true) { element -> StrictJson.decode(identityCodec.encodeKey(element)) },"retainedClaims" to workArray(v.`retainedClaims`, 0, 256, true) { element -> StrictJson.decode(identityCodec.encodeKey(element)) },"remainingUncertainty" to workArray(v.`remainingUncertainty`, 0, 256, false) { element -> str(element) })

internal fun encodeWPending(v: WPending): JsonValue = obj("id" to uuid(v.`id`),"kind" to str(v.`kind`),"source" to (v.`source`?.let { encodeWSource(it) } ?: JNull),"description" to str(v.`description`),"responsibleTask" to StrictJson.decode(identityCodec.encodeKey(v.`responsibleTask`)))

internal fun encodeWCheckpoints(v: WCheckpoints): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"assignment" to encodeWAssignmentSubject(v.`assignment`),"manifest" to StrictJson.decode(identityCodec.encodeReference(v.`manifest`)),"loadedView" to StrictJson.decode(identityCodec.encodeKey(v.`loadedView`)),"sourceCutId" to uuid(v.`sourceCutId`),"plan" to StrictJson.decode(identityCodec.encodeKey(v.`plan`)),"planGeneration" to JNumber(v.`planGeneration`),"memory" to StrictJson.decode(identityCodec.encodeKey(v.`memory`)),"planSteps" to workArray(v.`planSteps`, 0, 256, false) { element -> encodeWStep(element) },"artifacts" to workArray(v.`artifacts`, 0, 256, false) { element -> encodeWSnapshot(element) },"pending" to workArray(v.`pending`, 0, 256, false) { element -> encodeWPending(element) },"report" to StrictJson.decode(identityCodec.encodeBodyReference(v.`report`)),"handoff" to str(v.`handoff`),"retrospective" to (v.`retrospective`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"result" to (v.`result`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"notification" to (v.`notification`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull))

internal fun encodeWDeliveries(v: WDeliveries): JsonValue = obj("assignment" to encodeWAssignmentSubject(v.`assignment`),"sourceCutId" to uuid(v.`sourceCutId`),"destinationTask" to StrictJson.decode(identityCodec.encodeKey(v.`destinationTask`)),"content" to StrictJson.decode(identityCodec.encodeBodyReference(v.`content`)),"generation" to JNumber(v.`generation`),"state" to str(v.`state`),"basis" to workArray(v.`basis`, 0, 256, false) { element -> encodeWSource(element) })

internal fun encodeWMemoryEntry(v: WMemoryEntry): JsonValue = obj("id" to uuid(v.`id`),"status" to str(v.`status`),"content" to StrictJson.decode(identityCodec.encodeBodyReference(v.`content`)),"sources" to workArray(v.`sources`, 1, 256, false) { element -> encodeWSource(element) },"remainingObligationIds" to workArray(v.`remainingObligationIds`, 0, 256, true) { element -> uuid(element) },"uncertainty" to str(v.`uncertainty`),"exactRequiredFragments" to workArray(v.`exactRequiredFragments`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeBodyReference(element)) })

internal fun encodeWMemory(v: WMemory): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"assignment" to encodeWAssignmentSubject(v.`assignment`),"previous" to (v.`previous`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"sourceCutId" to uuid(v.`sourceCutId`),"entries" to workArray(v.`entries`, 0, 256, false) { element -> encodeWMemoryEntry(element) },"gaps" to workArray(v.`gaps`, 0, 256, false) { element -> str(element) },"pending" to workArray(v.`pending`, 0, 256, false) { element -> encodeWPending(element) })

internal fun encodeWRetrospectivesObservationsItem(v: WRetrospectivesObservationsItem): JsonValue = obj("observed" to encodeWSource(v.`observed`),"expectation" to (v.`expectation`?.let { encodeWSource(it) } ?: JNull),"newExpectation" to (v.`newExpectation`?.let { str(it) } ?: JNull),"consequence" to str(v.`consequence`),"causeHypothesis" to str(v.`causeHypothesis`),"uncertainty" to str(v.`uncertainty`),"measure" to str(v.`measure`),"proposal" to (v.`proposal`?.let { StrictJson.decode(identityCodec.encodeBodyReference(it)) } ?: JNull),"status" to str(v.`status`),"normativeDecision" to (v.`normativeDecision`?.let { reference(it) } ?: JNull),"effectEvidence" to workArray(v.`effectEvidence`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) })

internal fun encodeWRetrospectives(v: WRetrospectives): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"assignment" to encodeWAssignmentSubject(v.`assignment`),"result" to (v.`result`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"outcome" to str(v.`outcome`),"observations" to workArray(v.`observations`, 0, 256, false) { element -> encodeWRetrospectivesObservationsItem(element) })

internal fun encodeWResults(v: WResults): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"assignment" to encodeWAssignmentSubject(v.`assignment`),"producer" to encodeWExecution(v.`producer`),"manifest" to StrictJson.decode(identityCodec.encodeReference(v.`manifest`)),"artifacts" to workArray(v.`artifacts`, 1, 128, false) { element -> encodeWSnapshot(element) },"changes" to StrictJson.decode(identityCodec.encodeBodyReference(v.`changes`)),"claimedCriteria" to workArray(v.`claimedCriteria`, 0, 256, true) { element -> uuid(element) },"evidence" to workArray(v.`evidence`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"retrospective" to StrictJson.decode(identityCodec.encodeReference(v.`retrospective`)),"pending" to workArray(v.`pending`, 0, 256, false) { element -> encodeWPending(element) })

internal fun encodeWGate(v: WGate): JsonValue = obj("id" to uuid(v.`id`),"criterionIds" to workArray(v.`criterionIds`, 1, 256, true) { element -> uuid(element) },"boundary" to str(v.`boundary`),"independent" to JBoolean(v.`independent`),"stage" to str(v.`stage`),"groupId" to (v.`groupId`?.let { uuid(it) } ?: JNull),"deferred" to JBoolean(v.`deferred`),"reason" to str(v.`reason`))

internal fun encodeWVerificationPlans(v: WVerificationPlans): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"previous" to (v.`previous`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"criteria" to workArray(v.`criteria`, 1, 256, false) { element -> encodeWCriterion(element) },"gates" to workArray(v.`gates`, 1, 256, false) { element -> encodeWGate(element) },"reason" to str(v.`reason`),"authorizedChanges" to workArray(v.`authorizedChanges`, 0, 256, false) { element -> reference(element) })

internal fun encodeWFinding(v: WFinding): JsonValue = obj("id" to uuid(v.`id`),"category" to str(v.`category`),"requirement" to (v.`requirement`?.let { encodeWSource(it) } ?: JNull),"scenario" to str(v.`scenario`),"expected" to str(v.`expected`),"actual" to str(v.`actual`),"evidence" to workArray(v.`evidence`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"affectedGateIds" to workArray(v.`affectedGateIds`, 0, 256, true) { element -> uuid(element) },"affectedResultIds" to workArray(v.`affectedResultIds`, 0, 256, true) { element -> uuid(element) },"blocking" to JBoolean(v.`blocking`))

internal fun encodeWVerificationReports(v: WVerificationReports): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"assignment" to encodeWAssignmentSubject(v.`assignment`),"reviewer" to encodeWExecution(v.`reviewer`),"subjects" to workArray(v.`subjects`, 1, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"plan" to StrictJson.decode(identityCodec.encodeKey(v.`plan`)),"gateIds" to workArray(v.`gateIds`, 1, 256, true) { element -> uuid(element) },"freshContextBasis" to encodeWSource(v.`freshContextBasis`),"readOnlySnapshotBasis" to workArray(v.`readOnlySnapshotBasis`, 1, 256, false) { element -> encodeWSource(element) },"findings" to workArray(v.`findings`, 0, 256, false) { element -> encodeWFinding(element) },"evidence" to workArray(v.`evidence`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"report" to StrictJson.decode(identityCodec.encodeBodyReference(v.`report`)),"limits" to workArray(v.`limits`, 0, 256, false) { element -> str(element) })

internal fun encodeWSignificantInput(v: WSignificantInput): JsonValue = obj("source" to encodeWSource(v.`source`),"scope" to str(v.`scope`))

internal fun encodeWEvidenceClauseCasesItem(v: WEvidenceClauseCasesItem): JsonValue = obj("clause" to encodeWSource(v.`clause`),"caseId" to str(v.`caseId`),"testId" to str(v.`testId`))

internal fun encodeWEvidence(v: WEvidence): JsonValue = obj("subjects" to workArray(v.`subjects`, 1, 256, false) { element -> encodeWSource(element) },"significantInputs" to workArray(v.`significantInputs`, 0, 256, false) { element -> encodeWSignificantInput(element) },"method" to str(v.`method`),"boundary" to str(v.`boundary`),"environment" to StrictJson.decode(identityCodec.encodeBodyReference(v.`environment`)),"runId" to uuid(v.`runId`),"outcome" to str(v.`outcome`),"artifacts" to workArray(v.`artifacts`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeBodyReference(element)) },"clauseCases" to workArray(v.`clauseCases`, 0, 1024, false) { element -> encodeWEvidenceClauseCasesItem(element) },"limits" to workArray(v.`limits`, 0, 256, false) { element -> str(element) },"report" to StrictJson.decode(identityCodec.encodeBodyReference(v.`report`)))

internal fun encodeWDispositions(v: WDispositions): JsonValue = obj("result" to StrictJson.decode(identityCodec.encodeReference(v.`result`)),"report" to StrictJson.decode(identityCodec.encodeReference(v.`report`)),"findingId" to uuid(v.`findingId`),"gateIds" to workArray(v.`gateIds`, 0, 256, true) { element -> uuid(element) },"decision" to str(v.`decision`),"basis" to workArray(v.`basis`, 1, 256, false) { element -> encodeWSource(element) },"replacementResult" to (v.`replacementResult`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"ownerDecision" to (v.`ownerDecision`?.let { reference(it) } ?: JNull),"recordedBy" to uuid(v.`recordedBy`))

internal fun encodeWAcceptances(v: WAcceptances): JsonValue = obj("result" to StrictJson.decode(identityCodec.encodeReference(v.`result`)),"task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"verificationPlan" to StrictJson.decode(identityCodec.encodeKey(v.`verificationPlan`)),"evidence" to workArray(v.`evidence`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"reports" to workArray(v.`reports`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"dispositions" to workArray(v.`dispositions`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"applicability" to StrictJson.decode(identityCodec.encodeReference(v.`applicability`)),"criterionIds" to workArray(v.`criterionIds`, 1, 256, true) { element -> uuid(element) },"permittedTargets" to workArray(v.`permittedTargets`, 0, 128, false) { element -> uuid(element) },"basis" to StrictJson.decode(identityCodec.encodeBodyReference(v.`basis`)),"acceptedBy" to uuid(v.`acceptedBy`))

internal fun encodeWIntegrationIntents(v: WIntegrationIntents): JsonValue = obj("result" to StrictJson.decode(identityCodec.encodeReference(v.`result`)),"acceptance" to StrictJson.decode(identityCodec.encodeReference(v.`acceptance`)),"targetRepositoryId" to uuid(v.`targetRepositoryId`),"targetId" to uuid(v.`targetId`),"expectedTarget" to workOid(v.`expectedTarget`),"expectedTree" to workOid(v.`expectedTree`),"expectedDirtyDigest" to (v.`expectedDirtyDigest`?.let { digest(it) } ?: JNull),"requiredResources" to workArray(v.`requiredResources`, 1, 256, true) { element -> StrictJson.decode(identityCodec.encodeKey(element)) },"correlationId" to uuid(v.`correlationId`),"generation" to JNumber(v.`generation`),"state" to str(v.`state`),"observation" to (v.`observation`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull))

internal fun encodeWIntegrations(v: WIntegrations): JsonValue = obj("intent" to StrictJson.decode(identityCodec.encodeKey(v.`intent`)),"intentGeneration" to JNumber(v.`intentGeneration`),"result" to StrictJson.decode(identityCodec.encodeReference(v.`result`)),"resultingSnapshot" to encodeWSnapshot(v.`resultingSnapshot`),"applicability" to StrictJson.decode(identityCodec.encodeReference(v.`applicability`)),"evidence" to workArray(v.`evidence`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"observations" to workArray(v.`observations`, 1, 256, false) { element -> encodeWSource(element) },"integratedBy" to uuid(v.`integratedBy`),"pending" to workArray(v.`pending`, 0, 256, false) { element -> encodeWPending(element) })

internal fun encodeWApplicabilityEntry(v: WApplicabilityEntry): JsonValue = obj("source" to encodeWSource(v.`source`),"areas" to workArray(v.`areas`, 0, 256, false) { element -> str(element) },"dependencies" to workArray(v.`dependencies`, 0, 256, false) { element -> encodeWSource(element) },"state" to str(v.`state`),"reason" to str(v.`reason`))

internal fun encodeWApplicability(v: WApplicability): JsonValue = obj("previous" to (v.`previous`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"entries" to workArray(v.`entries`, 0, 1024, false) { element -> encodeWApplicabilityEntry(element) },"basis" to workArray(v.`basis`, 1, 256, false) { element -> encodeWSource(element) })

internal fun encodeWEvidenceApplicabilityEvidenceItem(v: WEvidenceApplicabilityEvidenceItem): JsonValue = obj("evidence" to StrictJson.decode(identityCodec.encodeReference(v.`evidence`)),"state" to str(v.`state`),"significantInputComparison" to workArray(v.`significantInputComparison`, 0, 256, false) { element -> encodeWSignificantInput(element) },"rationale" to str(v.`rationale`))

internal fun encodeWEvidenceApplicability(v: WEvidenceApplicability): JsonValue = obj("result" to StrictJson.decode(identityCodec.encodeReference(v.`result`)),"evidence" to workArray(v.`evidence`, 0, 256, false) { element -> encodeWEvidenceApplicabilityEvidenceItem(element) },"heldGateIds" to workArray(v.`heldGateIds`, 0, 256, true) { element -> uuid(element) },"basis" to workArray(v.`basis`, 1, 256, false) { element -> encodeWSource(element) },"assessedBy" to uuid(v.`assessedBy`))

internal fun encodeWSourceCutsNativeMessagesItem(v: WSourceCutsNativeMessagesItem): JsonValue = obj("messageId" to str(v.`messageId`),"digest" to digest(v.`digest`))

internal fun encodeWSourceCuts(v: WSourceCuts): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"assignment" to encodeWAssignmentSubject(v.`assignment`),"nativeHostId" to uuid(v.`nativeHostId`),"nativeSessionId" to str(v.`nativeSessionId`),"profile" to StrictJson.decode(identityCodec.encodeKey(v.`profile`)),"sourceFormatVersion" to JNumber(v.`sourceFormatVersion`),"renderedRequestDigest" to digest(v.`renderedRequestDigest`),"packetDigest" to digest(v.`packetDigest`),"retainedLocatorId" to uuid(v.`retainedLocatorId`),"nativeMessages" to workArray(v.`nativeMessages`, 0, 4096, false) { element -> encodeWSourceCutsNativeMessagesItem(element) },"retainedFragments" to workArray(v.`retainedFragments`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeBodyReference(element)) },"pending" to workArray(v.`pending`, 0, 256, false) { element -> encodeWPending(element) })

internal fun encodeWControllerContinuationsPrevious(v: WControllerContinuationsPrevious): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"digest" to digest(v.`digest`))

internal fun encodeWNativeSourceBasisNativeMessagesItem(v: WNativeSourceBasisNativeMessagesItem): JsonValue = obj("messageId" to str(v.`messageId`),"digest" to digest(v.`digest`))

internal fun encodeWNativeSourceBasis(v: WNativeSourceBasis): JsonValue = obj("nativeHostId" to uuid(v.`nativeHostId`),"nativeSessionId" to str(v.`nativeSessionId`),"sourceFormatVersion" to JNumber(v.`sourceFormatVersion`),"renderedRequestDigest" to digest(v.`renderedRequestDigest`),"packetDigest" to digest(v.`packetDigest`),"retainedLocatorId" to uuid(v.`retainedLocatorId`),"nativeMessages" to workArray(v.`nativeMessages`, 0, 4096, false) { element -> encodeWNativeSourceBasisNativeMessagesItem(element) },"retainedFragments" to workArray(v.`retainedFragments`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeBodyReference(element)) })

internal fun encodeWControllerContinuationsFrontierItemAssignmentsItem(v: WControllerContinuationsFrontierItemAssignmentsItem): JsonValue = obj("assignmentId" to uuid(v.`assignmentId`),"recordGeneration" to JNumber(v.`recordGeneration`),"executionGeneration" to JNumber(v.`executionGeneration`))

internal fun encodeWControllerContinuationsFrontierItem(v: WControllerContinuationsFrontierItem): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"assignments" to workArray(v.`assignments`, 0, 256, false) { element -> encodeWControllerContinuationsFrontierItemAssignmentsItem(element) },"obligationIds" to workArray(v.`obligationIds`, 0, 256, true) { element -> uuid(element) })

internal fun encodeWControllerContinuationsEntriesItem(v: WControllerContinuationsEntriesItem): JsonValue = obj("tasks" to workArray(v.`tasks`, 1, 256, false) { element -> StrictJson.decode(identityCodec.encodeKey(element)) },"entry" to encodeWMemoryEntry(v.`entry`))

internal fun encodeWControllerContinuations(v: WControllerContinuations): JsonValue = obj("profile" to StrictJson.decode(identityCodec.encodeKey(v.`profile`)),"previous" to (v.`previous`?.let { encodeWControllerContinuationsPrevious(it) } ?: JNull),"source" to encodeWNativeSourceBasis(v.`source`),"frontier" to workArray(v.`frontier`, 0, 256, false) { element -> encodeWControllerContinuationsFrontierItem(element) },"entries" to workArray(v.`entries`, 0, 256, false) { element -> encodeWControllerContinuationsEntriesItem(element) },"gaps" to workArray(v.`gaps`, 0, 256, false) { element -> str(element) },"pending" to workArray(v.`pending`, 0, 256, false) { element -> encodeWPending(element) },"report" to StrictJson.decode(identityCodec.encodeBodyReference(v.`report`)))

internal fun encodeCCreateTaskRevisionNewVerificationPlan(v: CCreateTaskRevisionNewVerificationPlan): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeWVerificationPlans(v.`data`))

internal fun encodeCCreateTaskRevisionBodiesItem(v: CCreateTaskRevisionBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCCreateTaskRevision(v: CCreateTaskRevision): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeWTaskRevisions(v.`data`),"newVerificationPlan" to (v.`newVerificationPlan`?.let { encodeCCreateTaskRevisionNewVerificationPlan(it) } ?: JNull),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCCreateTaskRevisionBodiesItem(element) })

internal fun encodeCSetTaskHeadBodiesItem(v: CSetTaskHeadBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCSetTaskHead(v: CSetTaskHead): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"expectedGeneration" to (v.`expectedGeneration`?.let { JNumber(it) } ?: JNull),"title" to str(v.`title`),"draft" to (v.`draft`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"executable" to (v.`executable`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCSetTaskHeadBodiesItem(element) })

internal fun encodeCPinInputManifestBodiesItem(v: CPinInputManifestBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCPinInputManifest(v: CPinInputManifest): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeWInputManifests(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCPinInputManifestBodiesItem(element) })

internal fun encodeCCreateProfileBodiesItem(v: CCreateProfileBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCCreateProfile(v: CCreateProfile): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeWProfiles(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCCreateProfileBodiesItem(element) })

internal fun encodeCReviseVerificationPlanBodiesItem(v: CReviseVerificationPlanBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCReviseVerificationPlan(v: CReviseVerificationPlan): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeWVerificationPlans(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCReviseVerificationPlanBodiesItem(element) })

internal fun encodeCReviseApplicabilityBodiesItem(v: CReviseApplicabilityBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCReviseApplicability(v: CReviseApplicability): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeWApplicability(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCReviseApplicabilityBodiesItem(element) })

internal fun encodeCUpdatePlanBodiesItem(v: CUpdatePlanBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCUpdatePlan(v: CUpdatePlan): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"expectedGeneration" to (v.`expectedGeneration`?.let { JNumber(it) } ?: JNull),"steps" to workArray(v.`steps`, 0, 256, false) { element -> encodeWStep(element) },"reason" to str(v.`reason`),"previousCheckpoint" to (v.`previousCheckpoint`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCUpdatePlanBodiesItem(element) },"assignment" to (v.`assignment`?.let { encodeWAssignmentSubject(it) } ?: JNull))

internal fun encodeCReserveAssignmentResourcesItem(v: CReserveAssignmentResourcesItem): JsonValue = obj("resource" to StrictJson.decode(identityCodec.encodeKey(v.`resource`)),"expectedGeneration" to (v.`expectedGeneration`?.let { JNumber(it) } ?: JNull))

internal fun encodeCReserveAssignmentBodiesItem(v: CReserveAssignmentBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCReserveAssignment(v: CReserveAssignment): JsonValue = obj("assignmentId" to uuid(v.`assignmentId`),"task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"manifest" to StrictJson.decode(identityCodec.encodeReference(v.`manifest`)),"profile" to StrictJson.decode(identityCodec.encodeKey(v.`profile`)),"workspaceId" to uuid(v.`workspaceId`),"resources" to workArray(v.`resources`, 1, 256, false) { element -> encodeCReserveAssignmentResourcesItem(element) },"reviewSubjects" to workArray(v.`reviewSubjects`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"intentId" to uuid(v.`intentId`),"correlationId" to uuid(v.`correlationId`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCReserveAssignmentBodiesItem(element) },"intendedExecution" to encodeWExecutionIntent(v.`intendedExecution`))

internal fun encodeCBindAssignmentBodiesItem(v: CBindAssignmentBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCBindAssignment(v: CBindAssignment): JsonValue = obj("assignmentId" to uuid(v.`assignmentId`),"expectedGeneration" to JNumber(v.`expectedGeneration`),"intentId" to uuid(v.`intentId`),"expectedIntentGeneration" to JNumber(v.`expectedIntentGeneration`),"nativeHostId" to uuid(v.`nativeHostId`),"nativeSessionId" to str(v.`nativeSessionId`),"workspaceId" to uuid(v.`workspaceId`),"observations" to workArray(v.`observations`, 1, 256, false) { element -> encodeWSource(element) },"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCBindAssignmentBodiesItem(element) })

internal fun encodeCRequestNativeTransitionBodiesItem(v: CRequestNativeTransitionBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCRequestNativeTransition(v: CRequestNativeTransition): JsonValue = obj("assignmentId" to uuid(v.`assignmentId`),"expectedGeneration" to JNumber(v.`expectedGeneration`),"intentId" to uuid(v.`intentId`),"correlationId" to uuid(v.`correlationId`),"transition" to str(v.`transition`),"checkpoint" to (v.`checkpoint`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCRequestNativeTransitionBodiesItem(element) })

internal fun encodeCObserveNativeTransitionBodiesItem(v: CObserveNativeTransitionBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCObserveNativeTransition(v: CObserveNativeTransition): JsonValue = obj("assignmentId" to uuid(v.`assignmentId`),"expectedGeneration" to JNumber(v.`expectedGeneration`),"intentId" to uuid(v.`intentId`),"expectedIntentGeneration" to JNumber(v.`expectedIntentGeneration`),"outcome" to str(v.`outcome`),"observations" to workArray(v.`observations`, 1, 256, false) { element -> encodeWSource(element) },"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCObserveNativeTransitionBodiesItem(element) })

internal fun encodeCRecordRecoveryBodiesItem(v: CRecordRecoveryBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCRecordRecovery(v: CRecordRecovery): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"expectedGeneration" to JNumber(v.`expectedGeneration`),"data" to encodeWRecoveries(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCRecordRecoveryBodiesItem(element) })

internal fun encodeCRebindAssignmentBodiesItem(v: CRebindAssignmentBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCRebindAssignment(v: CRebindAssignment): JsonValue = obj("assignmentId" to uuid(v.`assignmentId`),"expectedGeneration" to JNumber(v.`expectedGeneration`),"task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"manifest" to StrictJson.decode(identityCodec.encodeReference(v.`manifest`)),"profile" to StrictJson.decode(identityCodec.encodeKey(v.`profile`)),"checkpoint" to StrictJson.decode(identityCodec.encodeReference(v.`checkpoint`)),"recovery" to StrictJson.decode(identityCodec.encodeReference(v.`recovery`)),"intentId" to uuid(v.`intentId`),"correlationId" to uuid(v.`correlationId`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCRebindAssignmentBodiesItem(element) },"intendedExecution" to encodeWExecutionIntent(v.`intendedExecution`))

internal fun encodeCCompleteAssignmentBodiesItem(v: CCompleteAssignmentBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCCompleteAssignment(v: CCompleteAssignment): JsonValue = obj("assignmentId" to uuid(v.`assignmentId`),"expectedGeneration" to JNumber(v.`expectedGeneration`),"checkpoint" to StrictJson.decode(identityCodec.encodeReference(v.`checkpoint`)),"completionBasis" to workArray(v.`completionBasis`, 1, 256, false) { element -> encodeWSource(element) },"releaseResources" to workArray(v.`releaseResources`, 0, 256, true) { element -> StrictJson.decode(identityCodec.encodeKey(element)) },"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCCompleteAssignmentBodiesItem(element) })

internal fun encodeCRecordLoadedViewData(v: CRecordLoadedViewData): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"manifest" to StrictJson.decode(identityCodec.encodeReference(v.`manifest`)),"assignment" to encodeWAssignmentSubject(v.`assignment`),"previous" to (v.`previous`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"additions" to workArray(v.`additions`, 0, 256, false) { element -> encodeWLoadedItem(element) },"discoveredGaps" to workArray(v.`discoveredGaps`, 0, 256, false) { element -> str(element) })

internal fun encodeCRecordLoadedViewBodiesItem(v: CRecordLoadedViewBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCRecordLoadedView(v: CRecordLoadedView): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeCRecordLoadedViewData(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCRecordLoadedViewBodiesItem(element) })

internal fun encodeCRecordMemoryData(v: CRecordMemoryData): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"assignment" to encodeWAssignmentSubject(v.`assignment`),"previous" to (v.`previous`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"sourceCutId" to uuid(v.`sourceCutId`),"entries" to workArray(v.`entries`, 0, 256, false) { element -> encodeWMemoryEntry(element) },"gaps" to workArray(v.`gaps`, 0, 256, false) { element -> str(element) },"pending" to workArray(v.`pending`, 0, 256, false) { element -> encodeWPending(element) })

internal fun encodeCRecordMemoryBodiesItem(v: CRecordMemoryBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCRecordMemory(v: CRecordMemory): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeCRecordMemoryData(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCRecordMemoryBodiesItem(element) })

internal fun encodeCRecordCheckpointData(v: CRecordCheckpointData): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"assignment" to encodeWAssignmentSubject(v.`assignment`),"manifest" to StrictJson.decode(identityCodec.encodeReference(v.`manifest`)),"loadedView" to StrictJson.decode(identityCodec.encodeKey(v.`loadedView`)),"sourceCutId" to uuid(v.`sourceCutId`),"plan" to StrictJson.decode(identityCodec.encodeKey(v.`plan`)),"planGeneration" to JNumber(v.`planGeneration`),"memory" to StrictJson.decode(identityCodec.encodeKey(v.`memory`)),"planSteps" to workArray(v.`planSteps`, 0, 256, false) { element -> encodeWStep(element) },"artifacts" to workArray(v.`artifacts`, 0, 256, false) { element -> encodeWSnapshot(element) },"pending" to workArray(v.`pending`, 0, 256, false) { element -> encodeWPending(element) },"report" to StrictJson.decode(identityCodec.encodeBodyReference(v.`report`)),"handoff" to str(v.`handoff`),"retrospective" to (v.`retrospective`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"result" to (v.`result`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"notification" to (v.`notification`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull))

internal fun encodeCRecordCheckpointBodiesItem(v: CRecordCheckpointBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCRecordCheckpoint(v: CRecordCheckpoint): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeCRecordCheckpointData(v.`data`),"expectedAssignmentGeneration" to JNumber(v.`expectedAssignmentGeneration`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCRecordCheckpointBodiesItem(element) })

internal fun encodeCRecordRetrospectiveDataObservationsItem(v: CRecordRetrospectiveDataObservationsItem): JsonValue = obj("observed" to encodeWSource(v.`observed`),"expectation" to (v.`expectation`?.let { encodeWSource(it) } ?: JNull),"newExpectation" to (v.`newExpectation`?.let { str(it) } ?: JNull),"consequence" to str(v.`consequence`),"causeHypothesis" to str(v.`causeHypothesis`),"uncertainty" to str(v.`uncertainty`),"measure" to str(v.`measure`),"proposal" to (v.`proposal`?.let { StrictJson.decode(identityCodec.encodeBodyReference(it)) } ?: JNull),"status" to str(v.`status`),"normativeDecision" to (v.`normativeDecision`?.let { reference(it) } ?: JNull),"effectEvidence" to workArray(v.`effectEvidence`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) })

internal fun encodeCRecordRetrospectiveData(v: CRecordRetrospectiveData): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"assignment" to encodeWAssignmentSubject(v.`assignment`),"result" to (v.`result`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"outcome" to str(v.`outcome`),"observations" to workArray(v.`observations`, 0, 256, false) { element -> encodeCRecordRetrospectiveDataObservationsItem(element) })

internal fun encodeCRecordRetrospectiveBodiesItem(v: CRecordRetrospectiveBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCRecordRetrospective(v: CRecordRetrospective): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeCRecordRetrospectiveData(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCRecordRetrospectiveBodiesItem(element) })

internal fun encodeCSubmitResultData(v: CSubmitResultData): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"assignment" to encodeWAssignmentSubject(v.`assignment`),"manifest" to StrictJson.decode(identityCodec.encodeReference(v.`manifest`)),"artifacts" to workArray(v.`artifacts`, 1, 128, false) { element -> encodeWSnapshot(element) },"changes" to StrictJson.decode(identityCodec.encodeBodyReference(v.`changes`)),"claimedCriteria" to workArray(v.`claimedCriteria`, 0, 256, true) { element -> uuid(element) },"evidence" to workArray(v.`evidence`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"retrospective" to StrictJson.decode(identityCodec.encodeReference(v.`retrospective`)),"pending" to workArray(v.`pending`, 0, 256, false) { element -> encodeWPending(element) })

internal fun encodeCSubmitResultBodiesItem(v: CSubmitResultBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCSubmitResult(v: CSubmitResult): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeCSubmitResultData(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCSubmitResultBodiesItem(element) })

internal fun encodeCSubmitVerificationReportData(v: CSubmitVerificationReportData): JsonValue = obj("task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"assignment" to encodeWAssignmentSubject(v.`assignment`),"subjects" to workArray(v.`subjects`, 1, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"plan" to StrictJson.decode(identityCodec.encodeKey(v.`plan`)),"gateIds" to workArray(v.`gateIds`, 1, 256, true) { element -> uuid(element) },"freshContextBasis" to encodeWSource(v.`freshContextBasis`),"readOnlySnapshotBasis" to workArray(v.`readOnlySnapshotBasis`, 1, 256, false) { element -> encodeWSource(element) },"findings" to workArray(v.`findings`, 0, 256, false) { element -> encodeWFinding(element) },"evidence" to workArray(v.`evidence`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"report" to StrictJson.decode(identityCodec.encodeBodyReference(v.`report`)),"limits" to workArray(v.`limits`, 0, 256, false) { element -> str(element) })

internal fun encodeCSubmitVerificationReportBodiesItem(v: CSubmitVerificationReportBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCSubmitVerificationReport(v: CSubmitVerificationReport): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeCSubmitVerificationReportData(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCSubmitVerificationReportBodiesItem(element) })

internal fun encodeCRecordEvidenceDataClauseCasesItem(v: CRecordEvidenceDataClauseCasesItem): JsonValue = obj("clause" to encodeWSource(v.`clause`),"caseId" to str(v.`caseId`),"testId" to str(v.`testId`))

internal fun encodeCRecordEvidenceData(v: CRecordEvidenceData): JsonValue = obj("subjects" to workArray(v.`subjects`, 1, 256, false) { element -> encodeWSource(element) },"significantInputs" to workArray(v.`significantInputs`, 0, 256, false) { element -> encodeWSignificantInput(element) },"method" to str(v.`method`),"boundary" to str(v.`boundary`),"environment" to StrictJson.decode(identityCodec.encodeBodyReference(v.`environment`)),"runId" to uuid(v.`runId`),"outcome" to str(v.`outcome`),"artifacts" to workArray(v.`artifacts`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeBodyReference(element)) },"clauseCases" to workArray(v.`clauseCases`, 0, 1024, false) { element -> encodeCRecordEvidenceDataClauseCasesItem(element) },"limits" to workArray(v.`limits`, 0, 256, false) { element -> str(element) },"report" to StrictJson.decode(identityCodec.encodeBodyReference(v.`report`)))

internal fun encodeCRecordEvidenceBodiesItem(v: CRecordEvidenceBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCRecordEvidence(v: CRecordEvidence): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeCRecordEvidenceData(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCRecordEvidenceBodiesItem(element) })

internal fun encodeCRecordDeliveryBodiesItem(v: CRecordDeliveryBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCRecordDelivery(v: CRecordDelivery): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"assignment" to encodeWAssignmentSubject(v.`assignment`),"sourceCutId" to uuid(v.`sourceCutId`),"destinationTask" to StrictJson.decode(identityCodec.encodeKey(v.`destinationTask`)),"content" to StrictJson.decode(identityCodec.encodeBodyReference(v.`content`)),"expectedGeneration" to (v.`expectedGeneration`?.let { JNumber(it) } ?: JNull),"state" to str(v.`state`),"basis" to workArray(v.`basis`, 0, 256, false) { element -> encodeWSource(element) },"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCRecordDeliveryBodiesItem(element) })

internal fun encodeCRecordEvidenceApplicabilityDataEvidenceItem(v: CRecordEvidenceApplicabilityDataEvidenceItem): JsonValue = obj("evidence" to StrictJson.decode(identityCodec.encodeReference(v.`evidence`)),"state" to str(v.`state`),"significantInputComparison" to workArray(v.`significantInputComparison`, 0, 256, false) { element -> encodeWSignificantInput(element) },"rationale" to str(v.`rationale`))

internal fun encodeCRecordEvidenceApplicabilityData(v: CRecordEvidenceApplicabilityData): JsonValue = obj("result" to StrictJson.decode(identityCodec.encodeReference(v.`result`)),"evidence" to workArray(v.`evidence`, 0, 256, false) { element -> encodeCRecordEvidenceApplicabilityDataEvidenceItem(element) },"heldGateIds" to workArray(v.`heldGateIds`, 0, 256, true) { element -> uuid(element) },"basis" to workArray(v.`basis`, 1, 256, false) { element -> encodeWSource(element) })

internal fun encodeCRecordEvidenceApplicabilityBodiesItem(v: CRecordEvidenceApplicabilityBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCRecordEvidenceApplicability(v: CRecordEvidenceApplicability): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeCRecordEvidenceApplicabilityData(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCRecordEvidenceApplicabilityBodiesItem(element) })

internal fun encodeCRecordDispositionData(v: CRecordDispositionData): JsonValue = obj("result" to StrictJson.decode(identityCodec.encodeReference(v.`result`)),"report" to StrictJson.decode(identityCodec.encodeReference(v.`report`)),"findingId" to uuid(v.`findingId`),"gateIds" to workArray(v.`gateIds`, 0, 256, true) { element -> uuid(element) },"decision" to str(v.`decision`),"basis" to workArray(v.`basis`, 1, 256, false) { element -> encodeWSource(element) },"replacementResult" to (v.`replacementResult`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"ownerDecision" to (v.`ownerDecision`?.let { reference(it) } ?: JNull))

internal fun encodeCRecordDispositionBodiesItem(v: CRecordDispositionBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCRecordDisposition(v: CRecordDisposition): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeCRecordDispositionData(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCRecordDispositionBodiesItem(element) })

internal fun encodeCAcceptResultData(v: CAcceptResultData): JsonValue = obj("result" to StrictJson.decode(identityCodec.encodeReference(v.`result`)),"task" to StrictJson.decode(identityCodec.encodeKey(v.`task`)),"verificationPlan" to StrictJson.decode(identityCodec.encodeKey(v.`verificationPlan`)),"evidence" to workArray(v.`evidence`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"reports" to workArray(v.`reports`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"dispositions" to workArray(v.`dispositions`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"applicability" to StrictJson.decode(identityCodec.encodeReference(v.`applicability`)),"criterionIds" to workArray(v.`criterionIds`, 1, 256, true) { element -> uuid(element) },"permittedTargets" to workArray(v.`permittedTargets`, 0, 128, false) { element -> uuid(element) },"basis" to StrictJson.decode(identityCodec.encodeBodyReference(v.`basis`)))

internal fun encodeCAcceptResultBodiesItem(v: CAcceptResultBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCAcceptResult(v: CAcceptResult): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeCAcceptResultData(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCAcceptResultBodiesItem(element) })

internal fun encodeCPrepareIntegrationResourcesItem(v: CPrepareIntegrationResourcesItem): JsonValue = obj("resource" to StrictJson.decode(identityCodec.encodeKey(v.`resource`)),"expectedGeneration" to (v.`expectedGeneration`?.let { JNumber(it) } ?: JNull))

internal fun encodeCPrepareIntegrationBodiesItem(v: CPrepareIntegrationBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCPrepareIntegration(v: CPrepareIntegration): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"result" to StrictJson.decode(identityCodec.encodeReference(v.`result`)),"acceptance" to StrictJson.decode(identityCodec.encodeReference(v.`acceptance`)),"targetRepositoryId" to uuid(v.`targetRepositoryId`),"targetId" to uuid(v.`targetId`),"expectedTarget" to workOid(v.`expectedTarget`),"expectedTree" to workOid(v.`expectedTree`),"expectedDirtyDigest" to (v.`expectedDirtyDigest`?.let { digest(it) } ?: JNull),"resources" to workArray(v.`resources`, 1, 256, false) { element -> encodeCPrepareIntegrationResourcesItem(element) },"correlationId" to uuid(v.`correlationId`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCPrepareIntegrationBodiesItem(element) })

internal fun encodeCObserveIntegrationBodiesItem(v: CObserveIntegrationBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCObserveIntegration(v: CObserveIntegration): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"intent" to StrictJson.decode(identityCodec.encodeKey(v.`intent`)),"expectedGeneration" to JNumber(v.`expectedGeneration`),"outcome" to str(v.`outcome`),"resultingSnapshot" to (v.`resultingSnapshot`?.let { encodeWSnapshot(it) } ?: JNull),"applicability" to (v.`applicability`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"evidence" to workArray(v.`evidence`, 0, 256, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) },"observations" to workArray(v.`observations`, 1, 256, false) { element -> encodeWSource(element) },"pending" to workArray(v.`pending`, 0, 256, false) { element -> encodeWPending(element) },"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCObserveIntegrationBodiesItem(element) })

internal fun encodeCRecordSourceCutBodiesItem(v: CRecordSourceCutBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCRecordSourceCut(v: CRecordSourceCut): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeWSourceCuts(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCRecordSourceCutBodiesItem(element) })

internal fun encodeCRecordControllerContinuationBodiesItem(v: CRecordControllerContinuationBodiesItem): JsonValue = obj("digest" to digest(v.`digest`),"body" to str(v.`body`))

internal fun encodeCRecordControllerContinuation(v: CRecordControllerContinuation): JsonValue = obj("key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"data" to encodeWControllerContinuations(v.`data`),"bodies" to workArray(v.`bodies`, 0, 64, false) { element -> encodeCRecordControllerContinuationBodiesItem(element) })

internal fun encodeRListWorkRecords(v: RListWorkRecords): JsonValue = obj("family" to str(v.`family`),"taskId" to (v.`taskId`?.let { uuid(it) } ?: JNull),"cursor" to (v.`cursor`?.let { uuid(it) } ?: JNull),"pageSize" to JNumber(v.`pageSize`))

internal fun encodeRReadWorkBodyRecordVariant0(v: RReadWorkBodyRecordVariant0): JsonValue = StrictJson.decode(identityCodec.encodeReference(v))

internal fun encodeWMutableSelectorVariant0(v: WMutableSelectorVariant0): JsonValue = obj("family" to str(v.`family`),"key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"generation" to JNumber(v.`generation`))

internal fun encodeWMutableSelectorVariant1(v: WMutableSelectorVariant1): JsonValue = obj("family" to str(v.`family`),"key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"generation" to JNumber(v.`generation`))

internal fun encodeWMutableSelectorVariant2(v: WMutableSelectorVariant2): JsonValue = obj("family" to str(v.`family`),"key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"generation" to JNumber(v.`generation`))

internal fun encodeWMutableSelectorVariant3(v: WMutableSelectorVariant3): JsonValue = obj("family" to str(v.`family`),"key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"generation" to JNumber(v.`generation`))

internal fun encodeWMutableSelectorVariant4(v: WMutableSelectorVariant4): JsonValue = obj("family" to str(v.`family`),"key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"generation" to JNumber(v.`generation`))

internal fun encodeWMutableSelectorVariant5(v: WMutableSelectorVariant5): JsonValue = obj("family" to str(v.`family`),"key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"generation" to JNumber(v.`generation`))

internal fun encodeWMutableSelectorVariant6(v: WMutableSelectorVariant6): JsonValue = obj("family" to str(v.`family`),"key" to StrictJson.decode(identityCodec.encodeKey(v.`key`)),"generation" to JNumber(v.`generation`))

internal fun encodeWMutableSelector(v: WMutableSelector): JsonValue = when(v) { is WMutableSelectorVariant0Value -> encodeWMutableSelectorVariant0(v.value); is WMutableSelectorVariant1Value -> encodeWMutableSelectorVariant1(v.value); is WMutableSelectorVariant2Value -> encodeWMutableSelectorVariant2(v.value); is WMutableSelectorVariant3Value -> encodeWMutableSelectorVariant3(v.value); is WMutableSelectorVariant4Value -> encodeWMutableSelectorVariant4(v.value); is WMutableSelectorVariant5Value -> encodeWMutableSelectorVariant5(v.value); is WMutableSelectorVariant6Value -> encodeWMutableSelectorVariant6(v.value) }

internal fun encodeRReadWorkBodyRecordVariant1(v: RReadWorkBodyRecordVariant1): JsonValue = encodeWMutableSelector(v)

internal fun encodeRReadWorkBodyRecord(v: RReadWorkBodyRecord): JsonValue = when(v) { is RReadWorkBodyRecordVariant0Value -> encodeRReadWorkBodyRecordVariant0(v.value); is RReadWorkBodyRecordVariant1Value -> encodeRReadWorkBodyRecordVariant1(v.value) }

internal fun encodeRReadWorkBody(v: RReadWorkBody): JsonValue = obj("digest" to digest(v.`digest`),"record" to encodeRReadWorkBodyRecord(v.`record`))
