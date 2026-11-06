// SPDX-License-Identifier: AGPL-3.0-only
// Generated solely from parent-sealed coordination frozen-v1 model2 additions.
package dev.writhelm.core.work
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import java.util.UUID

internal data class WAssignmentReplacementsOldRecord(val `key`: WorkKey.Id,
    val `digest`: Digest,
    val `payload`: WAssignments)

internal data class WAssignmentReplacementsNextResourcesItem(val `resource`: WorkKey.Resource,
    val `expectedGeneration`: Long?)

internal data class WAssignmentReplacementsNext(val `assignmentId`: UUID,
    val `task`: WorkKey.TaskRevision,
    val `manifest`: WorkReference,
    val `profile`: WorkKey.Profile,
    val `workspaceId`: UUID,
    val `resources`: List<WAssignmentReplacementsNextResourcesItem>,
    val `intendedExecution`: WExecutionIntent,
    val `intentId`: UUID,
    val `correlationId`: UUID)

internal data class WAssignmentReplacementsContinuation(val `checkpoint`: WorkReference?,
    val `gaps`: List<String>,
    val `pending`: List<WPending>)

internal data class WAssignmentReplacementsProof(val `retainedLocatorId`: UUID,
    val `digest`: Digest)

internal data class WAssignmentReplacements(val `decision`: String,
    val `previous`: WorkReference?,
    val `oldRecord`: WAssignmentReplacementsOldRecord,
    val `recovery`: WorkReference?,
    val `next`: WAssignmentReplacementsNext,
    val `continuation`: WAssignmentReplacementsContinuation,
    val `proof`: WAssignmentReplacementsProof,
    val `basisH`: WorkGitOid,
    val `isolatedResources`: List<WorkKey.Resource>,
    val `retainedClaims`: List<WorkKey.Resource>)

internal sealed interface WConditionAssessor

internal data class WConditionAssessorVariant0(val `kind`: String,
    val `controllerId`: UUID)

internal data class WConditionAssessorVariant0Value(val value: WConditionAssessorVariant0) : WConditionAssessor

internal data class WConditionAssessorVariant1(val `kind`: String,
    val `assignment`: WAssignmentSubject,
    val `binding`: WorkReference)

internal data class WConditionAssessorVariant1Value(val value: WConditionAssessorVariant1) : WConditionAssessor

internal data class WConditionsConsumerNotice(val `digest`: Digest,
    val `byteLength`: Long)

internal data class WConditions(val `consumerTask`: WorkKey.TaskRevision,
    val `consumer`: WAssignmentSubject,
    val `responsibleTask`: WorkKey.TaskRevision,
    val `condition`: WorkBodyReference,
    val `assessor`: WConditionAssessor,
    val `requiredGateIds`: List<UUID>,
    val `agreement`: List<WSource>,
    val `recordedBy`: UUID,
    val `consumerNotice`: WConditionsConsumerNotice,
    val `governingPlan`: WorkReference,
    val `supersedes`: List<WorkReference>,
    val `decisionSources`: List<WSource>)

internal sealed interface WConditionAssessorProvenance

internal data class WConditionAssessorProvenanceVariant0(val `kind`: String,
    val `controllerId`: UUID,
    val `principalId`: UUID,
    val `credentialId`: UUID,
    val `executionId`: UUID,
    val `nativeHostId`: UUID,
    val `nativeSessionId`: String)

internal data class WConditionAssessorProvenanceVariant0Value(val value: WConditionAssessorProvenanceVariant0) : WConditionAssessorProvenance

internal data class WConditionAssessorProvenanceVariant1(val `kind`: String,
    val `assignment`: WAssignmentSubject,
    val `binding`: WorkReference,
    val `execution`: WExecution)

internal data class WConditionAssessorProvenanceVariant1Value(val value: WConditionAssessorProvenanceVariant1) : WConditionAssessorProvenance

internal data class WConditionConfirmations(val `condition`: WorkReference,
    val `result`: WorkReference,
    val `evidence`: List<WorkReference>,
    val `assessment`: WorkBodyReference,
    val `decision`: String,
    val `assessedBy`: WConditionAssessorProvenance,
    val `supersedes`: List<WorkReference>,
    val `correctionBasis`: List<WSource>,
    val `applicability`: WorkReference?)

internal data class WConditionPair(val `condition`: WorkReference,
    val `confirmation`: WorkReference)

internal data class WConditionCoverage(val `basisH`: WorkGitOid,
    val `conditionsDigest`: Digest,
    val `conditionCount`: Long)

internal data class WContinuationRebind(val `oldBinding`: WorkReference,
    val `newBinding`: WorkReference,
    val `task`: WorkKey.TaskRevision,
    val `manifest`: WorkReference,
    val `decisionSources`: List<WSource>,
    val `observation`: List<WSource>)

internal data class WContinuationsNotification(val `digest`: Digest,
    val `byteLength`: Long)

internal data class WContinuationsSupersedes(val `key`: WorkKey.Id,
    val `generation`: Long)

internal data class WContinuationEffect(val `attemptId`: UUID,
    val `requestKey`: UUID,
    val `requestDigest`: Digest,
    val `state`: String,
    val `previousAttempt`: UUID?,
    val `basis`: List<WSource>)

internal data class WContinuations(val `origin`: WAssignmentSubject,
    val `assignment`: WAssignmentSubject,
    val `binding`: WorkReference,
    val `checkpoint`: WorkReference?,
    val `sourceCheckpoint`: WorkReference,
    val `conditions`: List<WConditionPair>,
    val `coverage`: WConditionCoverage,
    val `expectedAssignmentGeneration`: Long,
    val `before`: WSnapshot,
    val `after`: WSnapshot,
    val `mode`: String,
    val `rebind`: WContinuationRebind?,
    val `preparationBasis`: List<WSource>,
    val `nativeMessageId`: String,
    val `notification`: WContinuationsNotification,
    val `supersedes`: WContinuationsSupersedes?,
    val `generation`: Long,
    val `phase`: String,
    val `stage`: String,
    val `observations`: List<WSource>,
    val `evidence`: List<WorkReference>,
    val `delivery`: WorkKey.Id?,
    val `nativeIntent`: WorkKey.Id?,
    val `reason`: WorkBodyReference?,
    val `effect`: WContinuationEffect)

internal data class CReplaceIsolatedAssignmentOld(val `key`: WorkKey.Id,
    val `expectedGeneration`: Long,
    val `executionGeneration`: Long,
    val `digest`: Digest)

internal data class CReplaceIsolatedAssignmentNextResourcesItem(val `resource`: WorkKey.Resource,
    val `expectedGeneration`: Long?)

internal data class CReplaceIsolatedAssignmentNext(val `assignmentId`: UUID,
    val `task`: WorkKey.TaskRevision,
    val `manifest`: WorkReference,
    val `profile`: WorkKey.Profile,
    val `workspaceId`: UUID,
    val `resources`: List<CReplaceIsolatedAssignmentNextResourcesItem>,
    val `intendedExecution`: WExecutionIntent,
    val `intentId`: UUID,
    val `correlationId`: UUID)

internal data class CReplaceIsolatedAssignmentContinuation(val `checkpoint`: WorkReference?,
    val `gaps`: List<String>,
    val `pending`: List<WPending>)

internal data class CReplaceIsolatedAssignmentProof(val `retainedLocatorId`: UUID,
    val `digest`: Digest)

internal data class CReplaceIsolatedAssignment(val `key`: WorkKey.Id,
    val `previous`: WorkReference?,
    val `old`: CReplaceIsolatedAssignmentOld,
    val `recovery`: WorkReference?,
    val `next`: CReplaceIsolatedAssignmentNext,
    val `continuation`: CReplaceIsolatedAssignmentContinuation,
    val `proof`: CReplaceIsolatedAssignmentProof,
    val `bodies`: List<JsonValue>)

internal data class CRecordConditionDataConsumerNotice(val `digest`: Digest,
    val `byteLength`: Long)

internal data class CRecordConditionData(val `consumerTask`: WorkKey.TaskRevision,
    val `consumer`: WAssignmentSubject,
    val `responsibleTask`: WorkKey.TaskRevision,
    val `condition`: WorkBodyReference,
    val `assessor`: WConditionAssessor,
    val `requiredGateIds`: List<UUID>,
    val `agreement`: List<WSource>,
    val `consumerNotice`: CRecordConditionDataConsumerNotice,
    val `governingPlan`: WorkReference,
    val `supersedes`: List<WorkReference>,
    val `decisionSources`: List<WSource>)

internal data class CRecordConditionBodiesItem(val `digest`: Digest,
    val `body`: String)

internal data class CRecordCondition(val `key`: WorkKey.Id,
    val `data`: CRecordConditionData,
    val `bodies`: List<CRecordConditionBodiesItem>)

internal data class CConfirmConditionData(val `condition`: WorkReference,
    val `result`: WorkReference,
    val `evidence`: List<WorkReference>,
    val `assessment`: WorkBodyReference,
    val `decision`: String,
    val `supersedes`: List<WorkReference>,
    val `correctionBasis`: List<WSource>,
    val `applicability`: WorkReference?)

internal data class CConfirmConditionBodiesItem(val `digest`: Digest,
    val `body`: String)

internal data class CConfirmCondition(val `key`: WorkKey.Id,
    val `data`: CConfirmConditionData,
    val `bodies`: List<CConfirmConditionBodiesItem>)

internal data class CPrepareContinuationDataNotification(val `digest`: Digest,
    val `byteLength`: Long)

internal data class CPrepareContinuationDataSupersedes(val `key`: WorkKey.Id,
    val `generation`: Long)

internal data class CPrepareContinuationData(val `origin`: WAssignmentSubject,
    val `assignment`: WAssignmentSubject,
    val `binding`: WorkReference,
    val `checkpoint`: WorkReference?,
    val `sourceCheckpoint`: WorkReference,
    val `conditions`: List<WConditionPair>,
    val `coverage`: WConditionCoverage,
    val `expectedAssignmentGeneration`: Long,
    val `before`: WSnapshot,
    val `after`: WSnapshot,
    val `mode`: String,
    val `rebind`: WContinuationRebind?,
    val `preparationBasis`: List<WSource>,
    val `nativeMessageId`: String,
    val `notification`: CPrepareContinuationDataNotification,
    val `supersedes`: CPrepareContinuationDataSupersedes?)

internal data class CPrepareContinuationBodiesItem(val `digest`: Digest,
    val `body`: String)

internal data class CPrepareContinuation(val `key`: WorkKey.Id,
    val `data`: CPrepareContinuationData,
    val `bodies`: List<CPrepareContinuationBodiesItem>)

internal data class CObserveContinuationBodiesItem(val `digest`: Digest,
    val `body`: String)

internal data class CObserveContinuation(val `key`: WorkKey.Id,
    val `expectedGeneration`: Long,
    val `stage`: String,
    val `outcome`: String,
    val `observations`: List<WSource>,
    val `evidence`: List<WorkReference>,
    val `delivery`: WorkKey.Id?,
    val `nativeIntent`: WorkKey.Id?,
    val `reason`: WorkBodyReference?,
    val `effect`: WContinuationEffect,
    val `bodies`: List<CObserveContinuationBodiesItem>)

internal data class FValidatedReplacementIsolationProof(val `retainedLocatorId`: UUID,
    val `digest`: Digest)

internal data class FValidatedReplacementIsolationClaimsItem(val `family`: String,
    val `key`: WorkKey.Resource,
    val `generation`: Long)

internal data class FValidatedReplacementIsolationObservedWriterEpochsItem(val `id`: UUID,
    val `digest`: Digest)

internal data class FValidatedReplacementIsolation(val `basisH`: WorkGitOid,
    val `proof`: FValidatedReplacementIsolationProof,
    val `oldAssignment`: WorkKey.Id,
    val `oldExecutionGeneration`: Long,
    val `oldRecordDigest`: Digest,
    val `oldBinding`: WorkReference?,
    val `oldIntendedExecution`: WExecutionIntent,
    val `oldWorkspaceId`: UUID,
    val `newAssignmentId`: UUID,
    val `newExecutionGeneration`: Long,
    val `newIntendedExecution`: WExecutionIntent,
    val `newWorkspaceId`: UUID,
    val `newAreaDescriptorDigest`: Digest,
    val `allocationState`: String,
    val `claims`: List<FValidatedReplacementIsolationClaimsItem>,
    val `isolatedResources`: List<WorkKey.Resource>,
    val `retainedClaims`: List<WorkKey.Resource>,
    val `adapterQualification`: WSource,
    val `isolationEpoch`: UUID,
    val `observedWriterEpochs`: List<FValidatedReplacementIsolationObservedWriterEpochsItem>,
    val `pendingEventIds`: List<UUID>,
    val `mandatoryClosureDigest`: Digest)

internal fun decodeWAssignmentReplacementsOldRecord(v: JsonValue): WAssignmentReplacementsOldRecord { val f=v.fields("key","digest","payload"); return WAssignmentReplacementsOldRecord(identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(f.getValue("key"))) as WorkKey.Id,digest(f.getValue("digest")),decodeWAssignments(f.getValue("payload"))) }

internal fun encodeWAssignmentReplacementsOldRecord(v: WAssignmentReplacementsOldRecord): JsonValue = obj("key" to (StrictJson.decode(identityCodec.encodeKey(v.`key`))),"digest" to (digest(v.`digest`)),"payload" to (encodeWAssignments(v.`payload`)))

internal fun decodeWAssignmentReplacementsNextResourcesItem(v: JsonValue): WAssignmentReplacementsNextResourcesItem { val f=v.fields("resource","expectedGeneration"); return WAssignmentReplacementsNextResourcesItem(identityCodec.decodeKey(WorkFamily.CLAIMS,StrictJson.encode(f.getValue("resource"))) as WorkKey.Resource,f.getValue("expectedGeneration").let { if(it === JNull) null else it.long().also { shape(it >= 0L) } }) }

internal fun encodeWAssignmentReplacementsNextResourcesItem(v: WAssignmentReplacementsNextResourcesItem): JsonValue = obj("resource" to (StrictJson.decode(identityCodec.encodeKey(v.`resource`))),"expectedGeneration" to (v.`expectedGeneration`?.let { JNumber(it) } ?: JNull))

internal fun decodeWAssignmentReplacementsNext(v: JsonValue): WAssignmentReplacementsNext { val f=v.fields("assignmentId","task","manifest","profile","workspaceId","resources","intendedExecution","intentId","correlationId"); return WAssignmentReplacementsNext(uuid(f.getValue("assignmentId")),identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,StrictJson.encode(f.getValue("task"))) as WorkKey.TaskRevision,identityCodec.decodeReference(StrictJson.encode(f.getValue("manifest"))),identityCodec.decodeKey(WorkFamily.PROFILES,StrictJson.encode(f.getValue("profile"))) as WorkKey.Profile,uuid(f.getValue("workspaceId")),workList(f.getValue("resources"), 1, 256, false) { element -> decodeWAssignmentReplacementsNextResourcesItem(element) },decodeWExecutionIntent(f.getValue("intendedExecution")),uuid(f.getValue("intentId")),uuid(f.getValue("correlationId"))) }

internal fun encodeWAssignmentReplacementsNext(v: WAssignmentReplacementsNext): JsonValue = obj("assignmentId" to (uuid(v.`assignmentId`)),"task" to (StrictJson.decode(identityCodec.encodeKey(v.`task`))),"manifest" to (StrictJson.decode(identityCodec.encodeReference(v.`manifest`))),"profile" to (StrictJson.decode(identityCodec.encodeKey(v.`profile`))),"workspaceId" to (uuid(v.`workspaceId`)),"resources" to (workArray(v.`resources`, 1, 256, false) { element -> encodeWAssignmentReplacementsNextResourcesItem(element) }),"intendedExecution" to (encodeWExecutionIntent(v.`intendedExecution`)),"intentId" to (uuid(v.`intentId`)),"correlationId" to (uuid(v.`correlationId`)))

internal fun decodeWAssignmentReplacementsContinuation(v: JsonValue): WAssignmentReplacementsContinuation { val f=v.fields("checkpoint","gaps","pending"); return WAssignmentReplacementsContinuation(f.getValue("checkpoint").let { if(it === JNull) null else identityCodec.decodeReference(StrictJson.encode(it)).also { shape(it.identity.family == WorkFamily.CHECKPOINTS) } },workList(f.getValue("gaps"), 0, 256, false) { element -> workText(element, 1, 65536) },workList(f.getValue("pending"), 0, 256, false) { element -> decodeWPending(element) }) }

internal fun encodeWAssignmentReplacementsContinuation(v: WAssignmentReplacementsContinuation): JsonValue = obj("checkpoint" to (v.`checkpoint`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"gaps" to (workArray(v.`gaps`, 0, 256, false) { element -> str(element) }),"pending" to (workArray(v.`pending`, 0, 256, false) { element -> encodeWPending(element) }))

internal fun decodeWAssignmentReplacementsProof(v: JsonValue): WAssignmentReplacementsProof { val f=v.fields("retainedLocatorId","digest"); return WAssignmentReplacementsProof(uuid(f.getValue("retainedLocatorId")),digest(f.getValue("digest"))) }

internal fun encodeWAssignmentReplacementsProof(v: WAssignmentReplacementsProof): JsonValue = obj("retainedLocatorId" to (uuid(v.`retainedLocatorId`)),"digest" to (digest(v.`digest`)))

internal fun decodeWAssignmentReplacements(v: JsonValue): WAssignmentReplacements { val f=v.fields("decision","previous","oldRecord","recovery","next","continuation","proof","basisH","isolatedResources","retainedClaims"); return WAssignmentReplacements(workText(f.getValue("decision"), 0, 65536) .also { shape(it == "execution-closed-isolated-replacement") },f.getValue("previous").let { if(it === JNull) null else identityCodec.decodeReference(StrictJson.encode(it)).also { shape(it.identity.family == WorkFamily.ASSIGNMENT_REPLACEMENTS) } },decodeWAssignmentReplacementsOldRecord(f.getValue("oldRecord")),f.getValue("recovery").let { if(it === JNull) null else identityCodec.decodeReference(StrictJson.encode(it)).also { shape(it.identity.family == WorkFamily.RECOVERIES) } },decodeWAssignmentReplacementsNext(f.getValue("next")),decodeWAssignmentReplacementsContinuation(f.getValue("continuation")),decodeWAssignmentReplacementsProof(f.getValue("proof")),workOid(f.getValue("basisH")),workList(f.getValue("isolatedResources"), 0, 256, true) { element -> identityCodec.decodeKey(WorkFamily.CLAIMS,StrictJson.encode(element)) as WorkKey.Resource },workList(f.getValue("retainedClaims"), 0, 256, true) { element -> identityCodec.decodeKey(WorkFamily.CLAIMS,StrictJson.encode(element)) as WorkKey.Resource }) }

internal fun encodeWAssignmentReplacements(v: WAssignmentReplacements): JsonValue = obj("decision" to (str(v.`decision`)),"previous" to (v.`previous`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"oldRecord" to (encodeWAssignmentReplacementsOldRecord(v.`oldRecord`)),"recovery" to (v.`recovery`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"next" to (encodeWAssignmentReplacementsNext(v.`next`)),"continuation" to (encodeWAssignmentReplacementsContinuation(v.`continuation`)),"proof" to (encodeWAssignmentReplacementsProof(v.`proof`)),"basisH" to (workOid(v.`basisH`)),"isolatedResources" to (workArray(v.`isolatedResources`, 0, 256, true) { element -> StrictJson.decode(identityCodec.encodeKey(element)) }),"retainedClaims" to (workArray(v.`retainedClaims`, 0, 256, true) { element -> StrictJson.decode(identityCodec.encodeKey(element)) }))

internal fun decodeWConditionAssessorVariant0(v: JsonValue): WConditionAssessorVariant0 { val f=v.fields("kind","controllerId"); return WConditionAssessorVariant0(workText(f.getValue("kind"), 0, 65536) .also { shape(it == "controller") },uuid(f.getValue("controllerId"))) }

internal fun encodeWConditionAssessorVariant0(v: WConditionAssessorVariant0): JsonValue = obj("kind" to (str(v.`kind`)),"controllerId" to (uuid(v.`controllerId`)))

internal fun decodeWConditionAssessorVariant1(v: JsonValue): WConditionAssessorVariant1 { val f=v.fields("kind","assignment","binding"); return WConditionAssessorVariant1(workText(f.getValue("kind"), 0, 65536) .also { shape(it == "reviewer") },decodeWAssignmentSubject(f.getValue("assignment")),identityCodec.decodeReference(StrictJson.encode(f.getValue("binding"))).also { shape(it.identity.family == WorkFamily.ASSIGNMENT_BINDINGS) }) }

internal fun encodeWConditionAssessorVariant1(v: WConditionAssessorVariant1): JsonValue = obj("kind" to (str(v.`kind`)),"assignment" to (encodeWAssignmentSubject(v.`assignment`)),"binding" to (StrictJson.decode(identityCodec.encodeReference(v.`binding`))))

internal fun decodeWConditionAssessor(v: JsonValue): WConditionAssessor = workOneOf(listOf({ WConditionAssessorVariant0Value(decodeWConditionAssessorVariant0(v)) },{ WConditionAssessorVariant1Value(decodeWConditionAssessorVariant1(v)) }))

internal fun encodeWConditionAssessor(v: WConditionAssessor): JsonValue = when(v) { is WConditionAssessorVariant0Value -> encodeWConditionAssessorVariant0(v.value);is WConditionAssessorVariant1Value -> encodeWConditionAssessorVariant1(v.value) }

internal fun decodeWConditionsConsumerNotice(v: JsonValue): WConditionsConsumerNotice { val f=v.fields("digest","byteLength"); return WConditionsConsumerNotice(digest(f.getValue("digest")),f.getValue("byteLength").long().also { shape(it >= 1L && it <= 4096L) }) }

internal fun encodeWConditionsConsumerNotice(v: WConditionsConsumerNotice): JsonValue = obj("digest" to (digest(v.`digest`)),"byteLength" to (JNumber(v.`byteLength`)))

internal fun decodeWConditions(v: JsonValue): WConditions { val f=v.fields("consumerTask","consumer","responsibleTask","condition","assessor","requiredGateIds","agreement","recordedBy","consumerNotice","governingPlan","supersedes","decisionSources"); return WConditions(identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,StrictJson.encode(f.getValue("consumerTask"))) as WorkKey.TaskRevision,decodeWAssignmentSubject(f.getValue("consumer")),identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,StrictJson.encode(f.getValue("responsibleTask"))) as WorkKey.TaskRevision,identityCodec.decodeBodyReference(StrictJson.encode(f.getValue("condition"))),decodeWConditionAssessor(f.getValue("assessor")),workList(f.getValue("requiredGateIds"), 1, 32, true) { element -> uuid(element) },workList(f.getValue("agreement"), 1, 32, false) { element -> decodeWSource(element) },uuid(f.getValue("recordedBy")),decodeWConditionsConsumerNotice(f.getValue("consumerNotice")),identityCodec.decodeReference(StrictJson.encode(f.getValue("governingPlan"))).also { shape(it.identity.family == WorkFamily.VERIFICATION_PLANS) },workList(f.getValue("supersedes"), 0, 32, false) { element -> identityCodec.decodeReference(StrictJson.encode(element)).also { shape(it.identity.family == WorkFamily.CONDITIONS) } },workList(f.getValue("decisionSources"), 0, 32, false) { element -> decodeWSource(element) }) }

internal fun encodeWConditions(v: WConditions): JsonValue = obj("consumerTask" to (StrictJson.decode(identityCodec.encodeKey(v.`consumerTask`))),"consumer" to (encodeWAssignmentSubject(v.`consumer`)),"responsibleTask" to (StrictJson.decode(identityCodec.encodeKey(v.`responsibleTask`))),"condition" to (StrictJson.decode(identityCodec.encodeBodyReference(v.`condition`))),"assessor" to (encodeWConditionAssessor(v.`assessor`)),"requiredGateIds" to (workArray(v.`requiredGateIds`, 1, 32, true) { element -> uuid(element) }),"agreement" to (workArray(v.`agreement`, 1, 32, false) { element -> encodeWSource(element) }),"recordedBy" to (uuid(v.`recordedBy`)),"consumerNotice" to (encodeWConditionsConsumerNotice(v.`consumerNotice`)),"governingPlan" to (StrictJson.decode(identityCodec.encodeReference(v.`governingPlan`))),"supersedes" to (workArray(v.`supersedes`, 0, 32, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) }),"decisionSources" to (workArray(v.`decisionSources`, 0, 32, false) { element -> encodeWSource(element) }))

internal fun decodeWConditionAssessorProvenanceVariant0(v: JsonValue): WConditionAssessorProvenanceVariant0 { val f=v.fields("kind","controllerId","principalId","credentialId","executionId","nativeHostId","nativeSessionId"); return WConditionAssessorProvenanceVariant0(workText(f.getValue("kind"), 0, 65536) .also { shape(it == "controller") },uuid(f.getValue("controllerId")),uuid(f.getValue("principalId")),uuid(f.getValue("credentialId")),uuid(f.getValue("executionId")),uuid(f.getValue("nativeHostId")),workText(f.getValue("nativeSessionId"), 3, 256) .also { shape(Regex("^ses").containsMatchIn(it)) }) }

internal fun encodeWConditionAssessorProvenanceVariant0(v: WConditionAssessorProvenanceVariant0): JsonValue = obj("kind" to (str(v.`kind`)),"controllerId" to (uuid(v.`controllerId`)),"principalId" to (uuid(v.`principalId`)),"credentialId" to (uuid(v.`credentialId`)),"executionId" to (uuid(v.`executionId`)),"nativeHostId" to (uuid(v.`nativeHostId`)),"nativeSessionId" to (str(v.`nativeSessionId`)))

internal fun decodeWConditionAssessorProvenanceVariant1(v: JsonValue): WConditionAssessorProvenanceVariant1 { val f=v.fields("kind","assignment","binding","execution"); return WConditionAssessorProvenanceVariant1(workText(f.getValue("kind"), 0, 65536) .also { shape(it == "reviewer") },decodeWAssignmentSubject(f.getValue("assignment")),identityCodec.decodeReference(StrictJson.encode(f.getValue("binding"))).also { shape(it.identity.family == WorkFamily.ASSIGNMENT_BINDINGS) },decodeWExecution(f.getValue("execution"))) }

internal fun encodeWConditionAssessorProvenanceVariant1(v: WConditionAssessorProvenanceVariant1): JsonValue = obj("kind" to (str(v.`kind`)),"assignment" to (encodeWAssignmentSubject(v.`assignment`)),"binding" to (StrictJson.decode(identityCodec.encodeReference(v.`binding`))),"execution" to (encodeWExecution(v.`execution`)))

internal fun decodeWConditionAssessorProvenance(v: JsonValue): WConditionAssessorProvenance = workOneOf(listOf({ WConditionAssessorProvenanceVariant0Value(decodeWConditionAssessorProvenanceVariant0(v)) },{ WConditionAssessorProvenanceVariant1Value(decodeWConditionAssessorProvenanceVariant1(v)) }))

internal fun encodeWConditionAssessorProvenance(v: WConditionAssessorProvenance): JsonValue = when(v) { is WConditionAssessorProvenanceVariant0Value -> encodeWConditionAssessorProvenanceVariant0(v.value);is WConditionAssessorProvenanceVariant1Value -> encodeWConditionAssessorProvenanceVariant1(v.value) }

internal fun decodeWConditionConfirmations(v: JsonValue): WConditionConfirmations { val f=v.fields("condition","result","evidence","assessment","decision","assessedBy","supersedes","correctionBasis","applicability"); return WConditionConfirmations(identityCodec.decodeReference(StrictJson.encode(f.getValue("condition"))).also { shape(it.identity.family == WorkFamily.CONDITIONS) },identityCodec.decodeReference(StrictJson.encode(f.getValue("result"))).also { shape(it.identity.family == WorkFamily.RESULTS) },workList(f.getValue("evidence"), 0, 32, false) { element -> identityCodec.decodeReference(StrictJson.encode(element)).also { shape(it.identity.family == WorkFamily.EVIDENCE) } },identityCodec.decodeBodyReference(StrictJson.encode(f.getValue("assessment"))),workText(f.getValue("decision"), 0, 65536) .also { shape(it in setOf("resolved","not-resolved")) },decodeWConditionAssessorProvenance(f.getValue("assessedBy")),workList(f.getValue("supersedes"), 0, 32, false) { element -> identityCodec.decodeReference(StrictJson.encode(element)).also { shape(it.identity.family == WorkFamily.CONDITION_CONFIRMATIONS) } },workList(f.getValue("correctionBasis"), 0, 32, false) { element -> decodeWSource(element) },f.getValue("applicability").let { if(it === JNull) null else identityCodec.decodeReference(StrictJson.encode(it)).also { shape(it.identity.family == WorkFamily.EVIDENCE_APPLICABILITY) } }) }

internal fun encodeWConditionConfirmations(v: WConditionConfirmations): JsonValue = obj("condition" to (StrictJson.decode(identityCodec.encodeReference(v.`condition`))),"result" to (StrictJson.decode(identityCodec.encodeReference(v.`result`))),"evidence" to (workArray(v.`evidence`, 0, 32, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) }),"assessment" to (StrictJson.decode(identityCodec.encodeBodyReference(v.`assessment`))),"decision" to (str(v.`decision`)),"assessedBy" to (encodeWConditionAssessorProvenance(v.`assessedBy`)),"supersedes" to (workArray(v.`supersedes`, 0, 32, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) }),"correctionBasis" to (workArray(v.`correctionBasis`, 0, 32, false) { element -> encodeWSource(element) }),"applicability" to (v.`applicability`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull))

internal fun decodeWConditionPair(v: JsonValue): WConditionPair { val f=v.fields("condition","confirmation"); return WConditionPair(identityCodec.decodeReference(StrictJson.encode(f.getValue("condition"))).also { shape(it.identity.family == WorkFamily.CONDITIONS) },identityCodec.decodeReference(StrictJson.encode(f.getValue("confirmation"))).also { shape(it.identity.family == WorkFamily.CONDITION_CONFIRMATIONS) }) }

internal fun encodeWConditionPair(v: WConditionPair): JsonValue = obj("condition" to (StrictJson.decode(identityCodec.encodeReference(v.`condition`))),"confirmation" to (StrictJson.decode(identityCodec.encodeReference(v.`confirmation`))))

internal fun decodeWConditionCoverage(v: JsonValue): WConditionCoverage { val f=v.fields("basisH","conditionsDigest","conditionCount"); return WConditionCoverage(workOid(f.getValue("basisH")),digest(f.getValue("conditionsDigest")),f.getValue("conditionCount").long().also { shape(it >= 1L && it <= 256L) }) }

internal fun encodeWConditionCoverage(v: WConditionCoverage): JsonValue = obj("basisH" to (workOid(v.`basisH`)),"conditionsDigest" to (digest(v.`conditionsDigest`)),"conditionCount" to (JNumber(v.`conditionCount`)))

internal fun decodeWContinuationRebind(v: JsonValue): WContinuationRebind { val f=v.fields("oldBinding","newBinding","task","manifest","decisionSources","observation"); return WContinuationRebind(identityCodec.decodeReference(StrictJson.encode(f.getValue("oldBinding"))).also { shape(it.identity.family == WorkFamily.ASSIGNMENT_BINDINGS) },identityCodec.decodeReference(StrictJson.encode(f.getValue("newBinding"))).also { shape(it.identity.family == WorkFamily.ASSIGNMENT_BINDINGS) },identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,StrictJson.encode(f.getValue("task"))) as WorkKey.TaskRevision,identityCodec.decodeReference(StrictJson.encode(f.getValue("manifest"))).also { shape(it.identity.family == WorkFamily.INPUT_MANIFESTS) },workList(f.getValue("decisionSources"), 1, 32, false) { element -> decodeWSource(element) },workList(f.getValue("observation"), 1, 32, false) { element -> decodeWSource(element) }) }

internal fun encodeWContinuationRebind(v: WContinuationRebind): JsonValue = obj("oldBinding" to (StrictJson.decode(identityCodec.encodeReference(v.`oldBinding`))),"newBinding" to (StrictJson.decode(identityCodec.encodeReference(v.`newBinding`))),"task" to (StrictJson.decode(identityCodec.encodeKey(v.`task`))),"manifest" to (StrictJson.decode(identityCodec.encodeReference(v.`manifest`))),"decisionSources" to (workArray(v.`decisionSources`, 1, 32, false) { element -> encodeWSource(element) }),"observation" to (workArray(v.`observation`, 1, 32, false) { element -> encodeWSource(element) }))

internal fun decodeWContinuationsNotification(v: JsonValue): WContinuationsNotification { val f=v.fields("digest","byteLength"); return WContinuationsNotification(digest(f.getValue("digest")),f.getValue("byteLength").long().also { shape(it >= 1L && it <= 4096L) }) }

internal fun encodeWContinuationsNotification(v: WContinuationsNotification): JsonValue = obj("digest" to (digest(v.`digest`)),"byteLength" to (JNumber(v.`byteLength`)))

internal fun decodeWContinuationsSupersedes(v: JsonValue): WContinuationsSupersedes { val f=v.fields("key","generation"); return WContinuationsSupersedes(identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(f.getValue("key"))) as WorkKey.Id,f.getValue("generation").long().also { shape(it >= 0L) }) }

internal fun encodeWContinuationsSupersedes(v: WContinuationsSupersedes): JsonValue = obj("key" to (StrictJson.decode(identityCodec.encodeKey(v.`key`))),"generation" to (JNumber(v.`generation`)))

internal fun decodeWContinuationEffect(v: JsonValue): WContinuationEffect { val f=v.fields("attemptId","requestKey","requestDigest","state","previousAttempt","basis"); return WContinuationEffect(uuid(f.getValue("attemptId")),uuid(f.getValue("requestKey")),digest(f.getValue("requestDigest")),workText(f.getValue("state"), 0, 65536) .also { shape(it in setOf("not-issued","armed","outcome-unknown","not-performed","confirmed")) },f.getValue("previousAttempt").let { if(it === JNull) null else uuid(it) },workList(f.getValue("basis"), 0, 32, false) { element -> decodeWSource(element) }) }

internal fun encodeWContinuationEffect(v: WContinuationEffect): JsonValue = obj("attemptId" to (uuid(v.`attemptId`)),"requestKey" to (uuid(v.`requestKey`)),"requestDigest" to (digest(v.`requestDigest`)),"state" to (str(v.`state`)),"previousAttempt" to (v.`previousAttempt`?.let { uuid(it) } ?: JNull),"basis" to (workArray(v.`basis`, 0, 32, false) { element -> encodeWSource(element) }))

internal fun decodeWContinuations(v: JsonValue): WContinuations { val f=v.fields("origin","assignment","binding","checkpoint","sourceCheckpoint","conditions","coverage","expectedAssignmentGeneration","before","after","mode","rebind","preparationBasis","nativeMessageId","notification","supersedes","generation","phase","stage","observations","evidence","delivery","nativeIntent","reason","effect"); return WContinuations(decodeWAssignmentSubject(f.getValue("origin")),decodeWAssignmentSubject(f.getValue("assignment")),identityCodec.decodeReference(StrictJson.encode(f.getValue("binding"))).also { shape(it.identity.family == WorkFamily.ASSIGNMENT_BINDINGS) },f.getValue("checkpoint").let { if(it === JNull) null else identityCodec.decodeReference(StrictJson.encode(it)).also { shape(it.identity.family == WorkFamily.CHECKPOINTS) } },identityCodec.decodeReference(StrictJson.encode(f.getValue("sourceCheckpoint"))).also { shape(it.identity.family == WorkFamily.CHECKPOINTS) },workList(f.getValue("conditions"), 1, 256, false) { element -> decodeWConditionPair(element) },decodeWConditionCoverage(f.getValue("coverage")),f.getValue("expectedAssignmentGeneration").long().also { shape(it >= 0L) },decodeWSnapshot(f.getValue("before")),decodeWSnapshot(f.getValue("after")),workText(f.getValue("mode"), 0, 65536) .also { shape(it in setOf("unchanged-base","authorized-rebind")) },f.getValue("rebind").let { if(it === JNull) null else decodeWContinuationRebind(it) },workList(f.getValue("preparationBasis"), 1, 32, false) { element -> decodeWSource(element) },workText(f.getValue("nativeMessageId"), 3, 256) .also { shape(Regex("^msg").containsMatchIn(it)) },decodeWContinuationsNotification(f.getValue("notification")),f.getValue("supersedes").let { if(it === JNull) null else decodeWContinuationsSupersedes(it) },f.getValue("generation").long().also { shape(it >= 0L) },workText(f.getValue("phase"), 0, 65536) .also { shape(it in setOf("prepared","in-flight","environment-confirmed","delivery-confirmed","resume-confirmed","held","unknown")) },workText(f.getValue("stage"), 0, 65536) .also { shape(it in setOf("environment","delivery","resume")) },workList(f.getValue("observations"), 0, 32, false) { element -> decodeWSource(element) },workList(f.getValue("evidence"), 0, 32, false) { element -> identityCodec.decodeReference(StrictJson.encode(element)).also { shape(it.identity.family == WorkFamily.EVIDENCE) } },f.getValue("delivery").let { if(it === JNull) null else identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(it)) as WorkKey.Id },f.getValue("nativeIntent").let { if(it === JNull) null else identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(it)) as WorkKey.Id },f.getValue("reason").let { if(it === JNull) null else identityCodec.decodeBodyReference(StrictJson.encode(it)) },decodeWContinuationEffect(f.getValue("effect"))) }

internal fun encodeWContinuations(v: WContinuations): JsonValue = obj("origin" to (encodeWAssignmentSubject(v.`origin`)),"assignment" to (encodeWAssignmentSubject(v.`assignment`)),"binding" to (StrictJson.decode(identityCodec.encodeReference(v.`binding`))),"checkpoint" to (v.`checkpoint`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"sourceCheckpoint" to (StrictJson.decode(identityCodec.encodeReference(v.`sourceCheckpoint`))),"conditions" to (workArray(v.`conditions`, 1, 256, false) { element -> encodeWConditionPair(element) }),"coverage" to (encodeWConditionCoverage(v.`coverage`)),"expectedAssignmentGeneration" to (JNumber(v.`expectedAssignmentGeneration`)),"before" to (encodeWSnapshot(v.`before`)),"after" to (encodeWSnapshot(v.`after`)),"mode" to (str(v.`mode`)),"rebind" to (v.`rebind`?.let { encodeWContinuationRebind(it) } ?: JNull),"preparationBasis" to (workArray(v.`preparationBasis`, 1, 32, false) { element -> encodeWSource(element) }),"nativeMessageId" to (str(v.`nativeMessageId`)),"notification" to (encodeWContinuationsNotification(v.`notification`)),"supersedes" to (v.`supersedes`?.let { encodeWContinuationsSupersedes(it) } ?: JNull),"generation" to (JNumber(v.`generation`)),"phase" to (str(v.`phase`)),"stage" to (str(v.`stage`)),"observations" to (workArray(v.`observations`, 0, 32, false) { element -> encodeWSource(element) }),"evidence" to (workArray(v.`evidence`, 0, 32, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) }),"delivery" to (v.`delivery`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"nativeIntent" to (v.`nativeIntent`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"reason" to (v.`reason`?.let { StrictJson.decode(identityCodec.encodeBodyReference(it)) } ?: JNull),"effect" to (encodeWContinuationEffect(v.`effect`)))

internal fun decodeCReplaceIsolatedAssignmentOld(v: JsonValue): CReplaceIsolatedAssignmentOld { val f=v.fields("key","expectedGeneration","executionGeneration","digest"); return CReplaceIsolatedAssignmentOld(identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(f.getValue("key"))) as WorkKey.Id,f.getValue("expectedGeneration").long().also { shape(it >= 0L) },f.getValue("executionGeneration").long().also { shape(it >= 0L) },digest(f.getValue("digest"))) }

internal fun encodeCReplaceIsolatedAssignmentOld(v: CReplaceIsolatedAssignmentOld): JsonValue = obj("key" to (StrictJson.decode(identityCodec.encodeKey(v.`key`))),"expectedGeneration" to (JNumber(v.`expectedGeneration`)),"executionGeneration" to (JNumber(v.`executionGeneration`)),"digest" to (digest(v.`digest`)))

internal fun decodeCReplaceIsolatedAssignmentNextResourcesItem(v: JsonValue): CReplaceIsolatedAssignmentNextResourcesItem { val f=v.fields("resource","expectedGeneration"); return CReplaceIsolatedAssignmentNextResourcesItem(identityCodec.decodeKey(WorkFamily.CLAIMS,StrictJson.encode(f.getValue("resource"))) as WorkKey.Resource,f.getValue("expectedGeneration").let { if(it === JNull) null else it.long().also { shape(it >= 0L) } }) }

internal fun encodeCReplaceIsolatedAssignmentNextResourcesItem(v: CReplaceIsolatedAssignmentNextResourcesItem): JsonValue = obj("resource" to (StrictJson.decode(identityCodec.encodeKey(v.`resource`))),"expectedGeneration" to (v.`expectedGeneration`?.let { JNumber(it) } ?: JNull))

internal fun decodeCReplaceIsolatedAssignmentNext(v: JsonValue): CReplaceIsolatedAssignmentNext { val f=v.fields("assignmentId","task","manifest","profile","workspaceId","resources","intendedExecution","intentId","correlationId"); return CReplaceIsolatedAssignmentNext(uuid(f.getValue("assignmentId")),identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,StrictJson.encode(f.getValue("task"))) as WorkKey.TaskRevision,identityCodec.decodeReference(StrictJson.encode(f.getValue("manifest"))),identityCodec.decodeKey(WorkFamily.PROFILES,StrictJson.encode(f.getValue("profile"))) as WorkKey.Profile,uuid(f.getValue("workspaceId")),workList(f.getValue("resources"), 1, 256, false) { element -> decodeCReplaceIsolatedAssignmentNextResourcesItem(element) },decodeWExecutionIntent(f.getValue("intendedExecution")),uuid(f.getValue("intentId")),uuid(f.getValue("correlationId"))) }

internal fun encodeCReplaceIsolatedAssignmentNext(v: CReplaceIsolatedAssignmentNext): JsonValue = obj("assignmentId" to (uuid(v.`assignmentId`)),"task" to (StrictJson.decode(identityCodec.encodeKey(v.`task`))),"manifest" to (StrictJson.decode(identityCodec.encodeReference(v.`manifest`))),"profile" to (StrictJson.decode(identityCodec.encodeKey(v.`profile`))),"workspaceId" to (uuid(v.`workspaceId`)),"resources" to (workArray(v.`resources`, 1, 256, false) { element -> encodeCReplaceIsolatedAssignmentNextResourcesItem(element) }),"intendedExecution" to (encodeWExecutionIntent(v.`intendedExecution`)),"intentId" to (uuid(v.`intentId`)),"correlationId" to (uuid(v.`correlationId`)))

internal fun decodeCReplaceIsolatedAssignmentContinuation(v: JsonValue): CReplaceIsolatedAssignmentContinuation { val f=v.fields("checkpoint","gaps","pending"); return CReplaceIsolatedAssignmentContinuation(f.getValue("checkpoint").let { if(it === JNull) null else identityCodec.decodeReference(StrictJson.encode(it)).also { shape(it.identity.family == WorkFamily.CHECKPOINTS) } },workList(f.getValue("gaps"), 0, 256, false) { element -> workText(element, 1, 65536) },workList(f.getValue("pending"), 0, 256, false) { element -> decodeWPending(element) }) }

internal fun encodeCReplaceIsolatedAssignmentContinuation(v: CReplaceIsolatedAssignmentContinuation): JsonValue = obj("checkpoint" to (v.`checkpoint`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"gaps" to (workArray(v.`gaps`, 0, 256, false) { element -> str(element) }),"pending" to (workArray(v.`pending`, 0, 256, false) { element -> encodeWPending(element) }))

internal fun decodeCReplaceIsolatedAssignmentProof(v: JsonValue): CReplaceIsolatedAssignmentProof { val f=v.fields("retainedLocatorId","digest"); return CReplaceIsolatedAssignmentProof(uuid(f.getValue("retainedLocatorId")),digest(f.getValue("digest"))) }

internal fun encodeCReplaceIsolatedAssignmentProof(v: CReplaceIsolatedAssignmentProof): JsonValue = obj("retainedLocatorId" to (uuid(v.`retainedLocatorId`)),"digest" to (digest(v.`digest`)))

internal fun decodeCReplaceIsolatedAssignment(v: JsonValue): CReplaceIsolatedAssignment { val f=v.fields("key","previous","old","recovery","next","continuation","proof","bodies"); return CReplaceIsolatedAssignment(identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(f.getValue("key"))) as WorkKey.Id,f.getValue("previous").let { if(it === JNull) null else identityCodec.decodeReference(StrictJson.encode(it)).also { shape(it.identity.family == WorkFamily.ASSIGNMENT_REPLACEMENTS) } },decodeCReplaceIsolatedAssignmentOld(f.getValue("old")),f.getValue("recovery").let { if(it === JNull) null else identityCodec.decodeReference(StrictJson.encode(it)).also { shape(it.identity.family == WorkFamily.RECOVERIES) } },decodeCReplaceIsolatedAssignmentNext(f.getValue("next")),decodeCReplaceIsolatedAssignmentContinuation(f.getValue("continuation")),decodeCReplaceIsolatedAssignmentProof(f.getValue("proof")),f.getValue("bodies").list().also { shape(it.isEmpty()) }) }

internal fun encodeCReplaceIsolatedAssignment(v: CReplaceIsolatedAssignment): JsonValue = obj("key" to (StrictJson.decode(identityCodec.encodeKey(v.`key`))),"previous" to (v.`previous`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"old" to (encodeCReplaceIsolatedAssignmentOld(v.`old`)),"recovery" to (v.`recovery`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"next" to (encodeCReplaceIsolatedAssignmentNext(v.`next`)),"continuation" to (encodeCReplaceIsolatedAssignmentContinuation(v.`continuation`)),"proof" to (encodeCReplaceIsolatedAssignmentProof(v.`proof`)),"bodies" to (JArray(v.`bodies`.also { shape(it.isEmpty()) })))

internal fun decodeCRecordConditionDataConsumerNotice(v: JsonValue): CRecordConditionDataConsumerNotice { val f=v.fields("digest","byteLength"); return CRecordConditionDataConsumerNotice(digest(f.getValue("digest")),f.getValue("byteLength").long().also { shape(it >= 1L && it <= 4096L) }) }

internal fun encodeCRecordConditionDataConsumerNotice(v: CRecordConditionDataConsumerNotice): JsonValue = obj("digest" to (digest(v.`digest`)),"byteLength" to (JNumber(v.`byteLength`)))

internal fun decodeCRecordConditionData(v: JsonValue): CRecordConditionData { val f=v.fields("consumerTask","consumer","responsibleTask","condition","assessor","requiredGateIds","agreement","consumerNotice","governingPlan","supersedes","decisionSources"); return CRecordConditionData(identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,StrictJson.encode(f.getValue("consumerTask"))) as WorkKey.TaskRevision,decodeWAssignmentSubject(f.getValue("consumer")),identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,StrictJson.encode(f.getValue("responsibleTask"))) as WorkKey.TaskRevision,identityCodec.decodeBodyReference(StrictJson.encode(f.getValue("condition"))),decodeWConditionAssessor(f.getValue("assessor")),workList(f.getValue("requiredGateIds"), 1, 32, true) { element -> uuid(element) },workList(f.getValue("agreement"), 1, 32, false) { element -> decodeWSource(element) },decodeCRecordConditionDataConsumerNotice(f.getValue("consumerNotice")),identityCodec.decodeReference(StrictJson.encode(f.getValue("governingPlan"))).also { shape(it.identity.family == WorkFamily.VERIFICATION_PLANS) },workList(f.getValue("supersedes"), 0, 32, false) { element -> identityCodec.decodeReference(StrictJson.encode(element)).also { shape(it.identity.family == WorkFamily.CONDITIONS) } },workList(f.getValue("decisionSources"), 0, 32, false) { element -> decodeWSource(element) }) }

internal fun encodeCRecordConditionData(v: CRecordConditionData): JsonValue = obj("consumerTask" to (StrictJson.decode(identityCodec.encodeKey(v.`consumerTask`))),"consumer" to (encodeWAssignmentSubject(v.`consumer`)),"responsibleTask" to (StrictJson.decode(identityCodec.encodeKey(v.`responsibleTask`))),"condition" to (StrictJson.decode(identityCodec.encodeBodyReference(v.`condition`))),"assessor" to (encodeWConditionAssessor(v.`assessor`)),"requiredGateIds" to (workArray(v.`requiredGateIds`, 1, 32, true) { element -> uuid(element) }),"agreement" to (workArray(v.`agreement`, 1, 32, false) { element -> encodeWSource(element) }),"consumerNotice" to (encodeCRecordConditionDataConsumerNotice(v.`consumerNotice`)),"governingPlan" to (StrictJson.decode(identityCodec.encodeReference(v.`governingPlan`))),"supersedes" to (workArray(v.`supersedes`, 0, 32, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) }),"decisionSources" to (workArray(v.`decisionSources`, 0, 32, false) { element -> encodeWSource(element) }))

internal fun decodeCRecordConditionBodiesItem(v: JsonValue): CRecordConditionBodiesItem { val f=v.fields("digest","body"); return CRecordConditionBodiesItem(digest(f.getValue("digest")),workText(f.getValue("body"), 0, 1048576)) }

internal fun encodeCRecordConditionBodiesItem(v: CRecordConditionBodiesItem): JsonValue = obj("digest" to (digest(v.`digest`)),"body" to (str(v.`body`)))

internal fun decodeCRecordCondition(v: JsonValue): CRecordCondition { val f=v.fields("key","data","bodies"); return CRecordCondition(identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(f.getValue("key"))) as WorkKey.Id,decodeCRecordConditionData(f.getValue("data")),workList(f.getValue("bodies"), 0, 4, false) { element -> decodeCRecordConditionBodiesItem(element) }) }

internal fun encodeCRecordCondition(v: CRecordCondition): JsonValue = obj("key" to (StrictJson.decode(identityCodec.encodeKey(v.`key`))),"data" to (encodeCRecordConditionData(v.`data`)),"bodies" to (workArray(v.`bodies`, 0, 4, false) { element -> encodeCRecordConditionBodiesItem(element) }))

internal fun decodeCConfirmConditionData(v: JsonValue): CConfirmConditionData { val f=v.fields("condition","result","evidence","assessment","decision","supersedes","correctionBasis","applicability"); return CConfirmConditionData(identityCodec.decodeReference(StrictJson.encode(f.getValue("condition"))).also { shape(it.identity.family == WorkFamily.CONDITIONS) },identityCodec.decodeReference(StrictJson.encode(f.getValue("result"))).also { shape(it.identity.family == WorkFamily.RESULTS) },workList(f.getValue("evidence"), 0, 32, false) { element -> identityCodec.decodeReference(StrictJson.encode(element)).also { shape(it.identity.family == WorkFamily.EVIDENCE) } },identityCodec.decodeBodyReference(StrictJson.encode(f.getValue("assessment"))),workText(f.getValue("decision"), 0, 65536) .also { shape(it in setOf("resolved","not-resolved")) },workList(f.getValue("supersedes"), 0, 32, false) { element -> identityCodec.decodeReference(StrictJson.encode(element)).also { shape(it.identity.family == WorkFamily.CONDITION_CONFIRMATIONS) } },workList(f.getValue("correctionBasis"), 0, 32, false) { element -> decodeWSource(element) },f.getValue("applicability").let { if(it === JNull) null else identityCodec.decodeReference(StrictJson.encode(it)).also { shape(it.identity.family == WorkFamily.EVIDENCE_APPLICABILITY) } }) }

internal fun encodeCConfirmConditionData(v: CConfirmConditionData): JsonValue = obj("condition" to (StrictJson.decode(identityCodec.encodeReference(v.`condition`))),"result" to (StrictJson.decode(identityCodec.encodeReference(v.`result`))),"evidence" to (workArray(v.`evidence`, 0, 32, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) }),"assessment" to (StrictJson.decode(identityCodec.encodeBodyReference(v.`assessment`))),"decision" to (str(v.`decision`)),"supersedes" to (workArray(v.`supersedes`, 0, 32, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) }),"correctionBasis" to (workArray(v.`correctionBasis`, 0, 32, false) { element -> encodeWSource(element) }),"applicability" to (v.`applicability`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull))

internal fun decodeCConfirmConditionBodiesItem(v: JsonValue): CConfirmConditionBodiesItem { val f=v.fields("digest","body"); return CConfirmConditionBodiesItem(digest(f.getValue("digest")),workText(f.getValue("body"), 0, 1048576)) }

internal fun encodeCConfirmConditionBodiesItem(v: CConfirmConditionBodiesItem): JsonValue = obj("digest" to (digest(v.`digest`)),"body" to (str(v.`body`)))

internal fun decodeCConfirmCondition(v: JsonValue): CConfirmCondition { val f=v.fields("key","data","bodies"); return CConfirmCondition(identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(f.getValue("key"))) as WorkKey.Id,decodeCConfirmConditionData(f.getValue("data")),workList(f.getValue("bodies"), 0, 4, false) { element -> decodeCConfirmConditionBodiesItem(element) }) }

internal fun encodeCConfirmCondition(v: CConfirmCondition): JsonValue = obj("key" to (StrictJson.decode(identityCodec.encodeKey(v.`key`))),"data" to (encodeCConfirmConditionData(v.`data`)),"bodies" to (workArray(v.`bodies`, 0, 4, false) { element -> encodeCConfirmConditionBodiesItem(element) }))

internal fun decodeCPrepareContinuationDataNotification(v: JsonValue): CPrepareContinuationDataNotification { val f=v.fields("digest","byteLength"); return CPrepareContinuationDataNotification(digest(f.getValue("digest")),f.getValue("byteLength").long().also { shape(it >= 1L && it <= 4096L) }) }

internal fun encodeCPrepareContinuationDataNotification(v: CPrepareContinuationDataNotification): JsonValue = obj("digest" to (digest(v.`digest`)),"byteLength" to (JNumber(v.`byteLength`)))

internal fun decodeCPrepareContinuationDataSupersedes(v: JsonValue): CPrepareContinuationDataSupersedes { val f=v.fields("key","generation"); return CPrepareContinuationDataSupersedes(identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(f.getValue("key"))) as WorkKey.Id,f.getValue("generation").long().also { shape(it >= 0L) }) }

internal fun encodeCPrepareContinuationDataSupersedes(v: CPrepareContinuationDataSupersedes): JsonValue = obj("key" to (StrictJson.decode(identityCodec.encodeKey(v.`key`))),"generation" to (JNumber(v.`generation`)))

internal fun decodeCPrepareContinuationData(v: JsonValue): CPrepareContinuationData { val f=v.fields("origin","assignment","binding","checkpoint","sourceCheckpoint","conditions","coverage","expectedAssignmentGeneration","before","after","mode","rebind","preparationBasis","nativeMessageId","notification","supersedes"); return CPrepareContinuationData(decodeWAssignmentSubject(f.getValue("origin")),decodeWAssignmentSubject(f.getValue("assignment")),identityCodec.decodeReference(StrictJson.encode(f.getValue("binding"))).also { shape(it.identity.family == WorkFamily.ASSIGNMENT_BINDINGS) },f.getValue("checkpoint").let { if(it === JNull) null else identityCodec.decodeReference(StrictJson.encode(it)).also { shape(it.identity.family == WorkFamily.CHECKPOINTS) } },identityCodec.decodeReference(StrictJson.encode(f.getValue("sourceCheckpoint"))).also { shape(it.identity.family == WorkFamily.CHECKPOINTS) },workList(f.getValue("conditions"), 1, 256, false) { element -> decodeWConditionPair(element) },decodeWConditionCoverage(f.getValue("coverage")),f.getValue("expectedAssignmentGeneration").long().also { shape(it >= 0L) },decodeWSnapshot(f.getValue("before")),decodeWSnapshot(f.getValue("after")),workText(f.getValue("mode"), 0, 65536) .also { shape(it in setOf("unchanged-base","authorized-rebind")) },f.getValue("rebind").let { if(it === JNull) null else decodeWContinuationRebind(it) },workList(f.getValue("preparationBasis"), 1, 32, false) { element -> decodeWSource(element) },workText(f.getValue("nativeMessageId"), 3, 256) .also { shape(Regex("^msg").containsMatchIn(it)) },decodeCPrepareContinuationDataNotification(f.getValue("notification")),f.getValue("supersedes").let { if(it === JNull) null else decodeCPrepareContinuationDataSupersedes(it) }) }

internal fun encodeCPrepareContinuationData(v: CPrepareContinuationData): JsonValue = obj("origin" to (encodeWAssignmentSubject(v.`origin`)),"assignment" to (encodeWAssignmentSubject(v.`assignment`)),"binding" to (StrictJson.decode(identityCodec.encodeReference(v.`binding`))),"checkpoint" to (v.`checkpoint`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"sourceCheckpoint" to (StrictJson.decode(identityCodec.encodeReference(v.`sourceCheckpoint`))),"conditions" to (workArray(v.`conditions`, 1, 256, false) { element -> encodeWConditionPair(element) }),"coverage" to (encodeWConditionCoverage(v.`coverage`)),"expectedAssignmentGeneration" to (JNumber(v.`expectedAssignmentGeneration`)),"before" to (encodeWSnapshot(v.`before`)),"after" to (encodeWSnapshot(v.`after`)),"mode" to (str(v.`mode`)),"rebind" to (v.`rebind`?.let { encodeWContinuationRebind(it) } ?: JNull),"preparationBasis" to (workArray(v.`preparationBasis`, 1, 32, false) { element -> encodeWSource(element) }),"nativeMessageId" to (str(v.`nativeMessageId`)),"notification" to (encodeCPrepareContinuationDataNotification(v.`notification`)),"supersedes" to (v.`supersedes`?.let { encodeCPrepareContinuationDataSupersedes(it) } ?: JNull))

internal fun decodeCPrepareContinuationBodiesItem(v: JsonValue): CPrepareContinuationBodiesItem { val f=v.fields("digest","body"); return CPrepareContinuationBodiesItem(digest(f.getValue("digest")),workText(f.getValue("body"), 0, 1048576)) }

internal fun encodeCPrepareContinuationBodiesItem(v: CPrepareContinuationBodiesItem): JsonValue = obj("digest" to (digest(v.`digest`)),"body" to (str(v.`body`)))

internal fun decodeCPrepareContinuation(v: JsonValue): CPrepareContinuation { val f=v.fields("key","data","bodies"); return CPrepareContinuation(identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(f.getValue("key"))) as WorkKey.Id,decodeCPrepareContinuationData(f.getValue("data")),workList(f.getValue("bodies"), 0, 4, false) { element -> decodeCPrepareContinuationBodiesItem(element) }) }

internal fun encodeCPrepareContinuation(v: CPrepareContinuation): JsonValue = obj("key" to (StrictJson.decode(identityCodec.encodeKey(v.`key`))),"data" to (encodeCPrepareContinuationData(v.`data`)),"bodies" to (workArray(v.`bodies`, 0, 4, false) { element -> encodeCPrepareContinuationBodiesItem(element) }))

internal fun decodeCObserveContinuationBodiesItem(v: JsonValue): CObserveContinuationBodiesItem { val f=v.fields("digest","body"); return CObserveContinuationBodiesItem(digest(f.getValue("digest")),workText(f.getValue("body"), 0, 1048576)) }

internal fun encodeCObserveContinuationBodiesItem(v: CObserveContinuationBodiesItem): JsonValue = obj("digest" to (digest(v.`digest`)),"body" to (str(v.`body`)))

internal fun decodeCObserveContinuation(v: JsonValue): CObserveContinuation { val f=v.fields("key","expectedGeneration","stage","outcome","observations","evidence","delivery","nativeIntent","reason","effect","bodies"); return CObserveContinuation(identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(f.getValue("key"))) as WorkKey.Id,f.getValue("expectedGeneration").long().also { shape(it >= 0L) },workText(f.getValue("stage"), 0, 65536) .also { shape(it in setOf("environment","delivery","resume")) },workText(f.getValue("outcome"), 0, 65536) .also { shape(it in setOf("armed","confirmed","not-performed","held","unknown")) },workList(f.getValue("observations"), 1, 32, false) { element -> decodeWSource(element) },workList(f.getValue("evidence"), 0, 32, false) { element -> identityCodec.decodeReference(StrictJson.encode(element)).also { shape(it.identity.family == WorkFamily.EVIDENCE) } },f.getValue("delivery").let { if(it === JNull) null else identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(it)) as WorkKey.Id },f.getValue("nativeIntent").let { if(it === JNull) null else identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(it)) as WorkKey.Id },f.getValue("reason").let { if(it === JNull) null else identityCodec.decodeBodyReference(StrictJson.encode(it)) },decodeWContinuationEffect(f.getValue("effect")),workList(f.getValue("bodies"), 0, 4, false) { element -> decodeCObserveContinuationBodiesItem(element) }) }

internal fun encodeCObserveContinuation(v: CObserveContinuation): JsonValue = obj("key" to (StrictJson.decode(identityCodec.encodeKey(v.`key`))),"expectedGeneration" to (JNumber(v.`expectedGeneration`)),"stage" to (str(v.`stage`)),"outcome" to (str(v.`outcome`)),"observations" to (workArray(v.`observations`, 1, 32, false) { element -> encodeWSource(element) }),"evidence" to (workArray(v.`evidence`, 0, 32, false) { element -> StrictJson.decode(identityCodec.encodeReference(element)) }),"delivery" to (v.`delivery`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"nativeIntent" to (v.`nativeIntent`?.let { StrictJson.decode(identityCodec.encodeKey(it)) } ?: JNull),"reason" to (v.`reason`?.let { StrictJson.decode(identityCodec.encodeBodyReference(it)) } ?: JNull),"effect" to (encodeWContinuationEffect(v.`effect`)),"bodies" to (workArray(v.`bodies`, 0, 4, false) { element -> encodeCObserveContinuationBodiesItem(element) }))

internal fun decodeFValidatedReplacementIsolationProof(v: JsonValue): FValidatedReplacementIsolationProof { val f=v.fields("retainedLocatorId","digest"); return FValidatedReplacementIsolationProof(uuid(f.getValue("retainedLocatorId")),digest(f.getValue("digest"))) }

internal fun encodeFValidatedReplacementIsolationProof(v: FValidatedReplacementIsolationProof): JsonValue = obj("retainedLocatorId" to (uuid(v.`retainedLocatorId`)),"digest" to (digest(v.`digest`)))

internal fun decodeFValidatedReplacementIsolationClaimsItem(v: JsonValue): FValidatedReplacementIsolationClaimsItem { val f=v.fields("family","key","generation"); return FValidatedReplacementIsolationClaimsItem(workText(f.getValue("family"), 0, 65536) .also { shape(it == "claims") },identityCodec.decodeKey(WorkFamily.CLAIMS,StrictJson.encode(f.getValue("key"))) as WorkKey.Resource,f.getValue("generation").long().also { shape(it >= 0L) }) }

internal fun encodeFValidatedReplacementIsolationClaimsItem(v: FValidatedReplacementIsolationClaimsItem): JsonValue = obj("family" to (str(v.`family`)),"key" to (StrictJson.decode(identityCodec.encodeKey(v.`key`))),"generation" to (JNumber(v.`generation`)))

internal fun decodeFValidatedReplacementIsolationObservedWriterEpochsItem(v: JsonValue): FValidatedReplacementIsolationObservedWriterEpochsItem { val f=v.fields("id","digest"); return FValidatedReplacementIsolationObservedWriterEpochsItem(uuid(f.getValue("id")),digest(f.getValue("digest"))) }

internal fun encodeFValidatedReplacementIsolationObservedWriterEpochsItem(v: FValidatedReplacementIsolationObservedWriterEpochsItem): JsonValue = obj("id" to (uuid(v.`id`)),"digest" to (digest(v.`digest`)))

internal fun decodeFValidatedReplacementIsolation(v: JsonValue): FValidatedReplacementIsolation { val f=v.fields("basisH","proof","oldAssignment","oldExecutionGeneration","oldRecordDigest","oldBinding","oldIntendedExecution","oldWorkspaceId","newAssignmentId","newExecutionGeneration","newIntendedExecution","newWorkspaceId","newAreaDescriptorDigest","allocationState","claims","isolatedResources","retainedClaims","adapterQualification","isolationEpoch","observedWriterEpochs","pendingEventIds","mandatoryClosureDigest"); return FValidatedReplacementIsolation(workOid(f.getValue("basisH")),decodeFValidatedReplacementIsolationProof(f.getValue("proof")),identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(f.getValue("oldAssignment"))) as WorkKey.Id,f.getValue("oldExecutionGeneration").long().also { shape(it >= 0L) },digest(f.getValue("oldRecordDigest")),f.getValue("oldBinding").let { if(it === JNull) null else identityCodec.decodeReference(StrictJson.encode(it)).also { shape(it.identity.family == WorkFamily.ASSIGNMENT_BINDINGS) } },decodeWExecutionIntent(f.getValue("oldIntendedExecution")),uuid(f.getValue("oldWorkspaceId")),uuid(f.getValue("newAssignmentId")),f.getValue("newExecutionGeneration").long().also { shape(it >= 0L) },decodeWExecutionIntent(f.getValue("newIntendedExecution")),uuid(f.getValue("newWorkspaceId")),digest(f.getValue("newAreaDescriptorDigest")),workText(f.getValue("allocationState"), 0, 65536) .also { shape(it == "planned-absent") },workList(f.getValue("claims"), 1, 256, false) { element -> decodeFValidatedReplacementIsolationClaimsItem(element) },workList(f.getValue("isolatedResources"), 0, 256, true) { element -> identityCodec.decodeKey(WorkFamily.CLAIMS,StrictJson.encode(element)) as WorkKey.Resource },workList(f.getValue("retainedClaims"), 0, 256, true) { element -> identityCodec.decodeKey(WorkFamily.CLAIMS,StrictJson.encode(element)) as WorkKey.Resource },decodeWSource(f.getValue("adapterQualification")),uuid(f.getValue("isolationEpoch")),workList(f.getValue("observedWriterEpochs"), 0, 256, false) { element -> decodeFValidatedReplacementIsolationObservedWriterEpochsItem(element) },workList(f.getValue("pendingEventIds"), 0, 256, true) { element -> uuid(element) },digest(f.getValue("mandatoryClosureDigest"))) }

internal fun encodeFValidatedReplacementIsolation(v: FValidatedReplacementIsolation): JsonValue = obj("basisH" to (workOid(v.`basisH`)),"proof" to (encodeFValidatedReplacementIsolationProof(v.`proof`)),"oldAssignment" to (StrictJson.decode(identityCodec.encodeKey(v.`oldAssignment`))),"oldExecutionGeneration" to (JNumber(v.`oldExecutionGeneration`)),"oldRecordDigest" to (digest(v.`oldRecordDigest`)),"oldBinding" to (v.`oldBinding`?.let { StrictJson.decode(identityCodec.encodeReference(it)) } ?: JNull),"oldIntendedExecution" to (encodeWExecutionIntent(v.`oldIntendedExecution`)),"oldWorkspaceId" to (uuid(v.`oldWorkspaceId`)),"newAssignmentId" to (uuid(v.`newAssignmentId`)),"newExecutionGeneration" to (JNumber(v.`newExecutionGeneration`)),"newIntendedExecution" to (encodeWExecutionIntent(v.`newIntendedExecution`)),"newWorkspaceId" to (uuid(v.`newWorkspaceId`)),"newAreaDescriptorDigest" to (digest(v.`newAreaDescriptorDigest`)),"allocationState" to (str(v.`allocationState`)),"claims" to (workArray(v.`claims`, 1, 256, false) { element -> encodeFValidatedReplacementIsolationClaimsItem(element) }),"isolatedResources" to (workArray(v.`isolatedResources`, 0, 256, true) { element -> StrictJson.decode(identityCodec.encodeKey(element)) }),"retainedClaims" to (workArray(v.`retainedClaims`, 0, 256, true) { element -> StrictJson.decode(identityCodec.encodeKey(element)) }),"adapterQualification" to (encodeWSource(v.`adapterQualification`)),"isolationEpoch" to (uuid(v.`isolationEpoch`)),"observedWriterEpochs" to (workArray(v.`observedWriterEpochs`, 0, 256, false) { element -> encodeFValidatedReplacementIsolationObservedWriterEpochsItem(element) }),"pendingEventIds" to (workArray(v.`pendingEventIds`, 0, 256, true) { element -> uuid(element) }),"mandatoryClosureDigest" to (digest(v.`mandatoryClosureDigest`)))
internal data class WAssignmentReplacementsPayload(val value: WAssignmentReplacements) : WorkPayload
internal data class WConditionsPayload(val value: WConditions) : WorkPayload
internal data class WConditionConfirmationsPayload(val value: WConditionConfirmations) : WorkPayload
internal data class WContinuationsPayload(val value: WContinuations) : WorkPayload
internal data class CReplaceIsolatedAssignmentOperation(val value: CReplaceIsolatedAssignment) : WorkOperation { override val name get() = "replace_isolated_assignment" }
internal data class CRecordConditionOperation(val value: CRecordCondition) : WorkOperation { override val name get() = "record_condition" }
internal data class CConfirmConditionOperation(val value: CConfirmCondition) : WorkOperation { override val name get() = "confirm_condition" }
internal data class CPrepareContinuationOperation(val value: CPrepareContinuation) : WorkOperation { override val name get() = "prepare_continuation" }
internal data class CObserveContinuationOperation(val value: CObserveContinuation) : WorkOperation { override val name get() = "observe_continuation" }
internal val model2RefusalCodes: Map<String,Set<WorkProblem>> = mapOf(
"replace_isolated_assignment" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.CLAIM_CONFLICT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.FOREIGN_REFERENCE,WorkProblem.GENERATION_EXHAUSTED,WorkProblem.INCOMPLETE_HANDOFF,WorkProblem.INVALID_BINDING,WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT,WorkProblem.INVALID_TRANSITION,WorkProblem.NATIVE_UNKNOWN,WorkProblem.NOT_FOUND,WorkProblem.NOT_READY,WorkProblem.STALE_GENERATION),
"record_condition" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.FOREIGN_REFERENCE,WorkProblem.INVALID_BINDING,WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT,WorkProblem.INVALID_TRANSITION,WorkProblem.MISSING_BODY,WorkProblem.NOT_FOUND,WorkProblem.NOT_READY,WorkProblem.STALE_GENERATION,WorkProblem.WRONG_ASSIGNMENT),
"confirm_condition" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.FOREIGN_REFERENCE,WorkProblem.INVALID_BINDING,WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT,WorkProblem.INVALID_TRANSITION,WorkProblem.MISSING_BODY,WorkProblem.NOT_FOUND,WorkProblem.NOT_READY,WorkProblem.STALE_GENERATION,WorkProblem.WRONG_ASSIGNMENT,WorkProblem.INCOMPLETE_HANDOFF),
"prepare_continuation" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.FOREIGN_REFERENCE,WorkProblem.INVALID_BINDING,WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT,WorkProblem.INVALID_TRANSITION,WorkProblem.MISSING_BODY,WorkProblem.NOT_FOUND,WorkProblem.NOT_READY,WorkProblem.STALE_GENERATION,WorkProblem.WRONG_ASSIGNMENT,WorkProblem.CLAIM_CONFLICT,WorkProblem.INCOMPLETE_HANDOFF,WorkProblem.NATIVE_UNKNOWN),
"observe_continuation" to setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.FOREIGN_REFERENCE,WorkProblem.INVALID_BINDING,WorkProblem.INVALID_REFERENCE,WorkProblem.INVALID_SUBJECT,WorkProblem.INVALID_TRANSITION,WorkProblem.MISSING_BODY,WorkProblem.NOT_FOUND,WorkProblem.NOT_READY,WorkProblem.STALE_GENERATION,WorkProblem.WRONG_ASSIGNMENT,WorkProblem.CLAIM_CONFLICT,WorkProblem.GENERATION_EXHAUSTED,WorkProblem.INCOMPLETE_HANDOFF,WorkProblem.NATIVE_UNKNOWN)
)
