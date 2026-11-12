// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.knowledge.framedDigest
import dev.writhelm.core.knowledge.writeFramed
import dev.writhelm.core.knowledge.Utf8Content
import java.util.UUID

/** Immutable selections published by the explicitly selected model4 service profile. They are
 * not authority to reserve/start work. Version2 can declare a narrow future-result selection rule;
 * making a new executable revision/manifest and superseding an attempt remain separate operations. */
internal data class DispatchPlanEntry(
    val task:WorkKey.TaskRevision,val manifest:WorkReference,val profile:WorkKey.Profile,
    val resources:List<WorkKey.Resource>,val preparationId:UUID,val startId:UUID,
    val futureDependencies:List<DispatchFutureDependency> = emptyList()
)
/** Explicit narrow policy: exactly one immutable result of this producer, at implemented readiness.
 * Two results are ambiguous even if one is newer. No task or manifest is silently rewritten. */
internal data class DispatchFutureDependency(val producer:WorkKey.TaskRevision,val readiness:String)
internal sealed interface DispatchResultChoice {
    data object Pending:DispatchResultChoice
    data object Ambiguous:DispatchResultChoice
    data class Selected(val result:WorkReference):DispatchResultChoice
}
internal fun chooseDispatchResult(state:WorkState,rule:DispatchFutureDependency):DispatchResultChoice {
    workRequire(rule.producer.projectId==state.projectId && rule.readiness=="implemented",WorkProblem.INVALID_SUBJECT)
    val matches=state.records.values.filter { (it.payload as? WResultsPayload)?.value?.task==rule.producer }
    return when(matches.size) {
        0 -> DispatchResultChoice.Pending
        1 -> DispatchResultChoice.Selected(state.reference(WorkFamily.RESULTS,matches.single().identity.key))
        else -> DispatchResultChoice.Ambiguous
    }
}
internal data class DispatchMaterialization(val task:CCreateTaskRevision,val manifest:CPinInputManifest)
private const val MATERIALIZED_CLOSURE_GAP="Fresh mandatory-input closure assessment required for selected dependency results"

/** Exact closed successor for a planned future dependency. Missing or competing closures hold the
 * entry; this read-only selection does not authorize reservation, start or qualification. */
internal data class DispatchClosedSelection(val materialization:WorkReference,val closure:WorkReference,
    val task:WorkKey.TaskRevision,val manifest:WorkReference)
internal fun dispatchClosedSelection(state:WorkState,plan:WorkReference,entry:DispatchPlanEntry):DispatchClosedSelection? {
    workRequire(state.modelVersion==4 && plan.identity.family==WorkFamily.DISPATCH_PLANS,WorkProblem.INVALID_REFERENCE)
    val declared=(state.resolve(plan).payload as? WDispatchPlanPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    workRequire(declared.entries.singleOrNull { it.preparationId==entry.preparationId }==entry && entry.futureDependencies.isNotEmpty(),
        WorkProblem.INVALID_REFERENCE)
    val matches=state.records.values.mapNotNull { row ->
        val closure=(row.payload as? WDispatchClosurePayload)?.value ?: return@mapNotNull null
        val materialization=(state.resolve(closure.materialization).payload as? WDispatchMaterializationPayload)?.value ?: return@mapNotNull null
        if(materialization.plan!=plan || materialization.task!=entry.task)return@mapNotNull null
        val manifest=state.reference(WorkFamily.INPUT_MANIFESTS,closure.manifest)
        val value=(state.resolve(manifest).payload as? WInputManifestsPayload)?.value ?: return@mapNotNull null
        if(value.task!=materialization.newTask || value.knownGaps.isNotEmpty())return@mapNotNull null
        DispatchClosedSelection(closure.materialization,state.reference(WorkFamily.DISPATCH_CLOSURES,row.identity.key),materialization.newTask,manifest)
    }
    return matches.singleOrNull()
}

/** Structural input inventory for the first closure stage. This is not an assessment of semantic
 * completeness, packet proof, authorization, or permission to remove the closure gap. The caller
 * must bind any independent assessment to the captured service head and current authority. */
internal data class PrebindingInputInventory(val task:WorkReference,val manifest:WorkReference,val dependencies:List<WorkReference>)
/** Output of a separately trusted independent assessor. It is not accepted from a work command or
 * model field; the service must verify its origin and bind publication to this same captured H. */
internal data class PrebindingClosureAssessment(val basis:WorkGitOid,val materializationId:UUID,
    val inputs:PrebindingInputInventory,val assessment:WSource,val assessor:UUID,val delegation:UUID)

/** Candidate model4 command envelope. This codec alone does not register an operation, authorize
 * publication, establish independent assessment or permit a reservation. The same-head guard is
 * part of its fingerprint; retry always retains the original request identity and guard. */
internal data class DispatchClosureCommand(val requestKey:UUID,val materialization:WorkReference,
    val expectedHead:WorkGitOid,val assessment:WSource,val manifest:WorkKey.Id)
/** The identity claims must be checked against the original H receipt index; retaining them does
 * not by itself qualify the assessment or prove independence. */
internal data class WDispatchClosurePayload(val value:DispatchClosureCommand,val assessor:UUID,val delegation:UUID,
    val automatic:Boolean=false):WorkPayload
internal fun encodeDispatchClosureRecord(v:WDispatchClosurePayload):JsonValue = if(v.automatic)obj(
    "command" to StrictJson.decode(DispatchClosureCommandCodec().encode(v.value)),
    "kind" to str("declared-input-check"),"actor" to uuid(v.assessor),"credential" to uuid(v.delegation)) else obj(
    "command" to StrictJson.decode(DispatchClosureCommandCodec().encode(v.value)),
    "assessor" to uuid(v.assessor),"delegation" to uuid(v.delegation))
internal fun decodeDispatchClosureRecord(v:JsonValue):WDispatchClosurePayload {
    if(v is JObject && "kind" in v.entries) {
        val f=v.fields("command","kind","actor","credential")
        shape(f.getValue("kind").string()=="declared-input-check")
        return WDispatchClosurePayload(DispatchClosureCommandCodec().decode(StrictJson.encode(f.getValue("command"))),
            uuid(f.getValue("actor")),uuid(f.getValue("credential")),true)
    }
    val f=v.fields("command","assessor","delegation")
    return WDispatchClosurePayload(DispatchClosureCommandCodec().decode(StrictJson.encode(f.getValue("command"))),
        uuid(f.getValue("assessor")),uuid(f.getValue("delegation")))
}
internal class DispatchClosureCommandCodec {
    private fun value(v:DispatchClosureCommand,withKey:Boolean):JsonValue {
        val fields=linkedMapOf<String,JsonValue>("formatVersion" to num(3),"projectId" to uuid(v.manifest.projectId),
            "operation" to str("close_dispatch_inputs"),"payload" to obj(
                "materialization" to StrictJson.decode(identityCodec.encodeReference(v.materialization)),
                "expectedHead" to workOid(v.expectedHead),"assessment" to encodeWSource(v.assessment),
                "manifest" to StrictJson.decode(identityCodec.encodeKey(v.manifest))))
        if(withKey)fields["requestKey"]=uuid(v.requestKey)
        return JObject(fields)
    }
    fun encode(v:DispatchClosureCommand):ByteArray=StrictJson.encode(value(v,true),262144).also { decode(it) }
    fun decode(bytes:ByteArray):DispatchClosureCommand {
        val f=StrictJson.decode(bytes,JsonLimits(bytes=262144,stringBytes=65536,depth=32,collection=1024,tokens=100000))
            .fields("formatVersion","requestKey","projectId","operation","payload")
        shape(f.getValue("formatVersion").long()==3L && f.getValue("operation").string()=="close_dispatch_inputs")
        val project=uuid(f.getValue("projectId"));val p=f.getValue("payload").fields("materialization","expectedHead","assessment","manifest")
        val materialization=identityCodec.decodeReference(StrictJson.encode(p.getValue("materialization")))
        val manifest=identityCodec.decodeKey(WorkFamily.INPUT_MANIFESTS,StrictJson.encode(p.getValue("manifest"))) as WorkKey.Id
        val assessment=decodeWSource(p.getValue("assessment"))
        shape(materialization.identity.family==WorkFamily.DISPATCH_MATERIALIZATIONS && materialization.identity.key.projectId==project && manifest.projectId==project)
        when(assessment) {
            is WSourceVariant0Value -> shape(assessment.value.reference.revision.document.projectId==project)
            is WSourceVariant1Value -> Unit
            is WSourceVariant2Value -> shape(assessment.value.reference.identity.key.projectId==project)
        }
        return DispatchClosureCommand(uuid(f.getValue("requestKey")),materialization,workOid(p.getValue("expectedHead")),assessment,manifest)
    }
    fun fingerprint(v:DispatchClosureCommand):Digest {
        encode(v);return framedDigest("writhelm.command.v3") { writeFramed(StrictJson.encode(value(v,false))) }
    }
}
/** Unpublished graph candidate only. A real service must authenticate the controller, obtain an
 * independently qualified assessment, retain its provenance, and publish/audit at the same H. */
internal fun reduceDispatchClosure(state:WorkState,basis:WorkGitOid,actor:WorkExecutionInput,facts:WorkExternalFacts,
    original:DispatchClosureCommand,assessment:PrebindingClosureAssessment):WorkEffect {
    val codec=DispatchClosureCommandCodec();val command=codec.decode(codec.encode(original))
    workRequire(state.modelVersion==4 && command.expectedHead==basis,WorkProblem.STALE_GENERATION)
    val materialization=(state.resolve(command.materialization).payload as? WDispatchMaterializationPayload)?.value
        ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    val plan=(state.resolve(materialization.plan).payload as? WDispatchPlanPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    workRequire(actor.controllerId==plan.controllerId && actor.assignment==null && actor.execution==null,WorkProblem.INVALID_BINDING)
    workRequire(assessment.assessment==command.assessment,WorkProblem.INVALID_SUBJECT)
    val proposal=proposeClosedMaterializedManifest(state,basis,materialization.requestKey,command.manifest,facts,assessment)
    val effect=WorkReducer().reduceClosurePin(state,WorkCommand(command.requestKey,state.projectId,CPinInputManifestOperation(proposal)),
        actor,facts,proposal.data.task)
    val identity=WorkRecordIdentity(WorkFamily.DISPATCH_CLOSURES,WorkKey.Id(state.projectId,command.requestKey))
    workRequire(identity !in state.records,WorkProblem.DUPLICATE_ID)
    val after=effect.after.derive(listOf(WorkRecord(identity,WDispatchClosurePayload(command,assessment.assessor,assessment.delegation))),emptyList())
    return WorkEffect(state,after,identity,effect.changedRecords+after.records.getValue(identity),effect.addedBodies)
}

/** Automatic declared-input path. No assessor, delegation or reviewer is created. */
internal fun reduceDeclaredDispatchClosure(state:WorkState,basis:WorkGitOid,actor:WorkExecutionInput,
    facts:WorkExternalFacts,command:DispatchClosureCommand):WorkEffect {
    workRequire(state.modelVersion==4 && command.expectedHead==basis,WorkProblem.STALE_GENERATION)
    val materialization=(state.resolve(command.materialization).payload as? WDispatchMaterializationPayload)?.value
        ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    val plan=(state.resolve(materialization.plan).payload as WDispatchPlanPayload).value
    workRequire(actor.controllerId==plan.controllerId && actor.assignment==null && actor.execution==null,WorkProblem.INVALID_BINDING)
    workRequire(command.assessment==WSourceVariant2Value(WSourceVariant2("work",command.materialization)),WorkProblem.BLOCKED_INPUT)
    val proposal=proposeDeclaredMaterializedManifest(state,materialization.requestKey,command.manifest,facts)
    val effect=WorkReducer().reduceClosurePin(state,WorkCommand(command.requestKey,state.projectId,CPinInputManifestOperation(proposal)),
        actor,facts,proposal.data.task)
    val identity=WorkRecordIdentity(WorkFamily.DISPATCH_CLOSURES,WorkKey.Id(state.projectId,command.requestKey))
    workRequire(identity !in state.records,WorkProblem.DUPLICATE_ID)
    val record=WorkRecord(identity,WDispatchClosurePayload(command,actor.principalId,actor.credentialId,true))
    val after=effect.after.derive(listOf(record),emptyList())
    return WorkEffect(state,after,identity,effect.changedRecords+record,effect.addedBodies)
}
/** Historical work-graph check; does not re-execute an assessor or prove its identity. The service
 * must separately verify the receipt, authorization, assessment provenance, basis and write set. */
internal fun validateDispatchClosureEdge(before:WorkState,after:WorkState,basis:WorkGitOid,original:DispatchClosureCommand) {
    val codec=DispatchClosureCommandCodec();val c=codec.decode(codec.encode(original))
    workRequire(before.modelVersion==4 && after.modelVersion==4 && before.projectId==after.projectId && c.expectedHead==basis,
        WorkProblem.INVALID_REFERENCE)
    val materialization=(before.resolve(c.materialization).payload as? WDispatchMaterializationPayload)?.value
        ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    val previous=(before.record(WorkFamily.INPUT_MANIFESTS,materialization.manifest).payload as? WInputManifestsPayload)?.value
        ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    workRequire(previous.knownGaps==listOf(MATERIALIZED_CLOSURE_GAP) && c.assessment!=previous.declaredClosureAssessment,WorkProblem.INVALID_REFERENCE)
    val manifest=WorkRecord(WorkRecordIdentity(WorkFamily.INPUT_MANIFESTS,c.manifest),
        WInputManifestsPayload(previous.copy(declaredClosureAssessment=c.assessment,knownGaps=emptyList())))
    val identity=WorkRecordIdentity(WorkFamily.DISPATCH_CLOSURES,WorkKey.Id(before.projectId,c.requestKey))
    val provenance=after.records[identity] ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    workRequire((provenance.payload as? WDispatchClosurePayload)?.value==c,WorkProblem.INVALID_REFERENCE)
    val added=listOf(manifest,provenance)
    workRequire(added.none { it.identity in before.records } && after.records==before.records+added.associateBy { it.identity } &&
        after.bodies==before.bodies,WorkProblem.INVALID_REFERENCE)
}

/** Construct an unpublished immutable successor only after an independently supplied assessment
 * matches the current structural inventory. This is not a service authorization, publication,
 * verified full packet, or proof that the assessor is qualified. */
internal fun proposeClosedMaterializedManifest(state:WorkState,basis:WorkGitOid,materializationId:UUID,newManifest:WorkKey.Id,
    facts:WorkExternalFacts,assessment:PrebindingClosureAssessment):CPinInputManifest {
    workRequire(newManifest.projectId==state.projectId && WorkRecordIdentity(WorkFamily.INPUT_MANIFESTS,newManifest) !in state.records,
        WorkProblem.INVALID_REFERENCE)
    val inputs=prebindingMaterializedInputs(state,materializationId,facts)
    workRequire(assessment.basis==basis && assessment.materializationId==materializationId && assessment.inputs==inputs,WorkProblem.BLOCKED_INPUT)
    val previous=(state.resolve(inputs.manifest).payload as WInputManifestsPayload).value
    workRequire(assessment.assessment!=previous.declaredClosureAssessment,WorkProblem.BLOCKED_INPUT)
    when(val source=assessment.assessment) {
        is WSourceVariant0Value -> workRequire(source.value.reference in facts.knowledgeSources,WorkProblem.BLOCKED_INPUT)
        is WSourceVariant1Value -> workRequire(source.value.artifact in facts.retainedSnapshots,WorkProblem.BLOCKED_INPUT)
        is WSourceVariant2Value -> {
            val record=state.resolve(source.value.reference)
            workLinks(record.payload).bodies.forEach { body ->
                workRequire(state.bodies[body.digest]?.sizeBytes==body.byteLength,WorkProblem.MISSING_BODY)
            }
        }
    }
    return CPinInputManifest(newManifest,previous.copy(declaredClosureAssessment=assessment.assessment,knownGaps=emptyList()),emptyList())
}
internal fun prebindingMaterializedInputs(state:WorkState,materializationId:UUID,facts:WorkExternalFacts):PrebindingInputInventory {
    return declaredMaterializedInputs(state,materializationId,facts,null)
}

/** Deterministic successor proposal after checking declared inputs. The retained materialization
 * identifies the check's subject; it is not a reviewer report or semantic-completeness certificate.
 * Publication still requires service authority, an exact-head transaction and historical checking. */
internal fun proposeDeclaredMaterializedManifest(state:WorkState,materializationId:UUID,newManifest:WorkKey.Id,
    facts:WorkExternalFacts):CPinInputManifest {
    workRequire(newManifest.projectId==state.projectId &&
        WorkRecordIdentity(WorkFamily.INPUT_MANIFESTS,newManifest) !in state.records,WorkProblem.INVALID_REFERENCE)
    val inputs=declaredMaterializedInputs(state,materializationId,facts,null)
    val previous=(state.resolve(inputs.manifest).payload as WInputManifestsPayload).value
    val provenance=state.reference(WorkFamily.DISPATCH_MATERIALIZATIONS,WorkKey.Id(state.projectId,materializationId))
    return CPinInputManifest(newManifest,previous.copy(knownGaps=emptyList(),
        declaredClosureAssessment=WSourceVariant2Value(WSourceVariant2("work",provenance))),emptyList())
}

/** Automatic declared-input check, not independent review. With a successor reference, validate
 * the exact unchanged input set after closing the materialization marker. Real known gaps cannot
 * be cleared through this path. External source observations still have to be supplied by the
 * trusted service adapter; a body or worker report does not establish artifact retention. */
internal fun declaredMaterializedInputs(state:WorkState,materializationId:UUID,facts:WorkExternalFacts,
    successor:WorkReference?):PrebindingInputInventory {
    workRequire(state.modelVersion==4,WorkProblem.INVALID_SUBJECT)
    val cmd=(state.record(WorkFamily.DISPATCH_MATERIALIZATIONS,WorkKey.Id(state.projectId,materializationId)).payload as? WDispatchMaterializationPayload)?.value
        ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    val plan=(state.resolve(cmd.plan).payload as? WDispatchPlanPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    val entry=plan.entries.singleOrNull { it.task==cmd.task } ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    val task=state.task(cmd.newTask)
    val originalRef=state.reference(WorkFamily.INPUT_MANIFESTS,cmd.manifest)
    val original=(state.resolve(originalRef).payload as WInputManifestsPayload).value
    val manifestRef=successor ?: originalRef
    val manifest=(state.resolve(manifestRef).payload as WInputManifestsPayload).value
    workRequire(original.knownGaps==listOf(MATERIALIZED_CLOSURE_GAP),WorkProblem.BLOCKED_INPUT)
    if(successor!=null)workRequire(successor.identity.family==WorkFamily.INPUT_MANIFESTS &&
        manifest.knownGaps.isEmpty() && manifest.copy(declaredClosureAssessment=original.declaredClosureAssessment,
            knownGaps=original.knownGaps)==original,WorkProblem.INVALID_REFERENCE)
    workRequire(task.state=="executable" && task.unresolved.isEmpty() && task.executionProfile==entry.profile &&
        manifest.task==cmd.newTask && manifest.base==task.base,WorkProblem.BLOCKED_INPUT)
    workRequire(task.approvedDesign.all { it in facts.approvedDecisions },WorkProblem.BLOCKED_INPUT)
    val applicability=(state.record(WorkFamily.APPLICABILITY,manifest.applicability).payload as WApplicabilityPayload).value
    workRequire(applicability.entries.none { it.state!="applicable" },WorkProblem.BLOCKED_INPUT)
    val required=applicability.entries.flatMap { listOf(it.source)+it.dependencies }+task.criteria.flatMap { it.clauses }
    workRequire(required.all { requiredSource -> manifest.items.any { it.mandatory && it.source==requiredSource } },WorkProblem.BLOCKED_INPUT)
    fun source(s:WSource) { when(s) {
        is WSourceVariant0Value -> workRequire(s.value.reference in facts.knowledgeSources,WorkProblem.BLOCKED_INPUT)
        is WSourceVariant1Value -> workRequire(s.value.artifact in facts.retainedSnapshots,WorkProblem.BLOCKED_INPUT)
        is WSourceVariant2Value -> {
            val record=state.resolve(s.value.reference)
            (record.payload as? WResultsPayload)?.value?.let { result ->
                workRequire(result.artifacts.isNotEmpty() && result.artifacts.all { it in facts.retainedSnapshots } &&
                    WorkSubmittedDiff(result.task,result.artifacts,result.changes) in facts.submittedDiffs,WorkProblem.BLOCKED_INPUT)
            }
            workLinks(record.payload).bodies.forEach { body ->
                workRequire(state.bodies[body.digest]?.sizeBytes==body.byteLength,WorkProblem.MISSING_BODY)
            }
        }
    } }
    manifest.items.forEach { item ->
        source(item.source)
        item.exactFragments.forEach { body -> workRequire(state.bodies[body.digest]?.sizeBytes==body.byteLength,WorkProblem.MISSING_BODY) }
    }
    source(manifest.declaredClosureAssessment)
    val selected=task.dependencies.map { dependency ->
        val ref=dependency.selectedResult ?: throw WorkFault(WorkProblem.NOT_READY)
        val result=(state.resolve(ref).payload as? WResultsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
        workRequire(result.task==dependency.producer,WorkProblem.INVALID_REFERENCE)
        ref
    }
    entry.futureDependencies.forEach { future ->
        val dependency=task.dependencies.singleOrNull { it.producer==future.producer && it.readiness==future.readiness }
            ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
        val ref=dependency.selectedResult ?: throw WorkFault(WorkProblem.NOT_READY)
        workRequire(manifest.items.any { it.mandatory && (it.source as? WSourceVariant2Value)?.value?.reference==ref },WorkProblem.BLOCKED_INPUT)
    }
    return PrebindingInputInventory(state.reference(WorkFamily.TASK_REVISIONS,cmd.newTask),manifestRef,java.util.Collections.unmodifiableList(selected))
}

/** Construct exact existing-command inputs from a published plan at one H. This is not an
 * authenticated transition and does not publish, select a new code base, or validate the new
 * packet. The generated manifest deliberately carries a closure gap until independently assessed. */
internal fun materializeDispatchDependency(state:WorkState,planRef:WorkReference,originalTask:WorkKey.TaskRevision,
    newTask:WorkKey.TaskRevision,newVerificationPlan:WorkKey.Revision,newManifest:WorkKey.Id):DispatchMaterialization {
    workRequire(state.modelVersion==4 && planRef.identity.family==WorkFamily.DISPATCH_PLANS,WorkProblem.INVALID_REFERENCE)
    val plan=(state.resolve(planRef).payload as? WDispatchPlanPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    val entry=plan.entries.singleOrNull { it.task==originalTask } ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    workRequire(originalTask.projectId==state.projectId && newTask.projectId==state.projectId && newTask.taskId==originalTask.taskId &&
        originalTask.revision<Long.MAX_VALUE && newTask.revision==originalTask.revision+1 &&
        newVerificationPlan.projectId==state.projectId && newVerificationPlan.revision==0L && newManifest.projectId==state.projectId,
        WorkProblem.INVALID_SUBJECT)
    val task=state.task(originalTask)
    workRequire(task.state=="executable" && task.unresolved.isEmpty() && task.executionProfile==entry.profile && entry.futureDependencies.isNotEmpty(),WorkProblem.NOT_READY)
    val unresolved=task.dependencies.filter { it.selectedResult==null }
    workRequire(unresolved.size==entry.futureDependencies.size && unresolved.all { dep -> entry.futureDependencies.count {
        it.producer==dep.producer && it.readiness==dep.readiness }==1 },WorkProblem.INVALID_REFERENCE)
    val chosen=entry.futureDependencies.associate { rule ->
        val choice=chooseDispatchResult(state,rule)
        workRequire(choice is DispatchResultChoice.Selected,WorkProblem.NOT_READY)
        rule.producer to (choice as DispatchResultChoice.Selected).result
    }
    val template=(state.resolve(entry.manifest).payload as? WInputManifestsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    workRequire(template.task==originalTask && template.base==task.base,WorkProblem.INVALID_REFERENCE)
    val oldPlan=task.verificationPlan ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    val verification=state.governingVerificationPlan(originalTask,oldPlan)
    val dependencies=task.dependencies.map { d -> if(d.selectedResult!=null)d else d.copy(selectedResult=chosen.getValue(d.producer)) }
    val revised=task.copy(dependencies=dependencies,verificationPlan=newVerificationPlan)
    val required=chosen.values.map { WSourceVariant2Value(WSourceVariant2("work",it)) }
    val items=template.items.toMutableList()
    required.forEach { source ->
        val index=items.indexOfFirst { it.source==source }
        if(index<0)items.add(WManifestItem(source,true,"exact selected dependency result",emptyList()))
        else items[index]=items[index].copy(mandatory=true)
    }
    val manifest=template.copy(task=newTask,items=items,knownGaps=(template.knownGaps+MATERIALIZED_CLOSURE_GAP).distinct())
    return DispatchMaterialization(CCreateTaskRevision(newTask,revised,CCreateTaskRevisionNewVerificationPlan(newVerificationPlan,
        verification.copy(task=newTask,previous=null)),emptyList()),CPinInputManifest(newManifest,manifest,emptyList()))
}

/** One unpublished work-graph effect: derive the unique result and both changes from this exact
 * input state. The caller must authorize and publish the complete effect in one CAS transaction;
 * this function does not provide service admission, journal audit, or packet verification. */
internal fun reduceDispatchMaterialization(state:WorkState,actor:WorkExecutionInput,facts:WorkExternalFacts,
    planRef:WorkReference,originalTask:WorkKey.TaskRevision,newTask:WorkKey.TaskRevision,
    newVerificationPlan:WorkKey.Revision,newManifest:WorkKey.Id,requestKey:UUID):WorkEffect {
    val plan=(state.resolve(planRef).payload as? WDispatchPlanPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    workRequire(actor.controllerId==plan.controllerId && actor.assignment==null && actor.execution==null,WorkProblem.INVALID_BINDING)
    val proposal=materializeDispatchDependency(state,planRef,originalTask,newTask,newVerificationPlan,newManifest)
    val reducer=WorkReducer()
    val taskEffect=reducer.reduce(state,WorkCommand(requestKey,state.projectId,CCreateTaskRevisionOperation(proposal.task)),actor,facts)
    val manifestEffect=reducer.reduce(taskEffect.after,WorkCommand(requestKey,state.projectId,CPinInputManifestOperation(proposal.manifest)),actor,facts)
    return WorkEffect(state,manifestEffect.after,manifestEffect.result,taskEffect.changedRecords+manifestEffect.changedRecords,
        taskEffect.addedBodies+manifestEffect.addedBodies)
}
/** Exact stable inputs for the prospective atomic service operation. The chosen result is omitted
 * deliberately: it must be recomputed from the publication head, not supplied by the controller. */
internal data class DispatchMaterializationCommand(val requestKey:UUID,val plan:WorkReference,
    val task:WorkKey.TaskRevision,val newTask:WorkKey.TaskRevision,val verificationPlan:WorkKey.Revision,val manifest:WorkKey.Id)
/** Retained provenance for historical H-to-C validation, not an independent approval. */
internal data class WDispatchMaterializationPayload(val value:DispatchMaterializationCommand):WorkPayload
internal class DispatchMaterializationCommandCodec {
    private fun key(value:WorkKey)=StrictJson.decode(identityCodec.encodeKey(value))
    private fun value(v:DispatchMaterializationCommand,withKey:Boolean):JsonValue {
        val fields=linkedMapOf<String,JsonValue>("formatVersion" to num(3),"projectId" to uuid(v.task.projectId),
            "operation" to str("materialize_dispatch_dependency"),"payload" to obj(
                "plan" to StrictJson.decode(identityCodec.encodeReference(v.plan)),"task" to key(v.task),
                "newTask" to key(v.newTask),"verificationPlan" to key(v.verificationPlan),"manifest" to key(v.manifest)))
        if(withKey)fields["requestKey"]=uuid(v.requestKey)
        return JObject(fields)
    }
    fun encode(v:DispatchMaterializationCommand):ByteArray=StrictJson.encode(value(v,true),262144).also { decode(it) }
    fun decode(bytes:ByteArray):DispatchMaterializationCommand {
        val f=StrictJson.decode(bytes,JsonLimits(bytes=262144,stringBytes=65536,depth=32,collection=1024,tokens=100000))
            .fields("formatVersion","requestKey","projectId","operation","payload")
        shape(f.getValue("formatVersion").long()==3L && f.getValue("operation").string()=="materialize_dispatch_dependency")
        val project=uuid(f.getValue("projectId"));val p=f.getValue("payload").fields("plan","task","newTask","verificationPlan","manifest")
        fun key(family:WorkFamily,name:String)=identityCodec.decodeKey(family,StrictJson.encode(p.getValue(name)))
        val plan=identityCodec.decodeReference(StrictJson.encode(p.getValue("plan")))
        val task=key(WorkFamily.TASK_REVISIONS,"task") as WorkKey.TaskRevision
        val next=key(WorkFamily.TASK_REVISIONS,"newTask") as WorkKey.TaskRevision
        val verification=key(WorkFamily.VERIFICATION_PLANS,"verificationPlan") as WorkKey.Revision
        val manifest=key(WorkFamily.INPUT_MANIFESTS,"manifest") as WorkKey.Id
        shape(plan.identity.family==WorkFamily.DISPATCH_PLANS && plan.identity.key.projectId==project &&
            task.projectId==project && next.projectId==project && verification.projectId==project && manifest.projectId==project &&
            task.taskId==next.taskId && task.revision<Long.MAX_VALUE && next.revision==task.revision+1 && verification.revision==0L)
        return DispatchMaterializationCommand(uuid(f.getValue("requestKey")),plan,task,next,verification,manifest)
    }
    fun fingerprint(v:DispatchMaterializationCommand):Digest {
        encode(v);return framedDigest("writhelm.command.v3") { writeFramed(StrictJson.encode(value(v,false))) }
    }
}
/** Unpublished reducer entry for the candidate command. Result selection happens inside the
 * reduction against state, never during command encoding or from a stale proposal. */
internal fun reduceDispatchMaterialization(state:WorkState,actor:WorkExecutionInput,facts:WorkExternalFacts,
    original:DispatchMaterializationCommand):WorkEffect {
    val codec=DispatchMaterializationCommandCodec()
    val command=codec.decode(codec.encode(original))
    val effect=reduceDispatchMaterialization(state,actor,facts,command.plan,command.task,command.newTask,
        command.verificationPlan,command.manifest,command.requestKey)
    val identity=WorkRecordIdentity(WorkFamily.DISPATCH_MATERIALIZATIONS,WorkKey.Id(state.projectId,command.requestKey))
    workRequire(identity !in state.records,WorkProblem.DUPLICATE_ID)
    val row=WorkRecord(identity,WDispatchMaterializationPayload(command))
    val after=effect.after.derive(listOf(row),emptyList())
    return WorkEffect(state,after,identity,effect.changedRecords+after.records.getValue(identity),effect.addedBodies)
}
/** Validate the work-graph portion of a historical H -> C edge from retained command bytes.
 * Authorization, receipt fingerprint and the Git write set are separate publisher obligations.
 * In particular, uniqueness is checked at H; later results must not change the old decision. */
internal fun validateDispatchMaterializationEdge(before:WorkState,after:WorkState,command:DispatchMaterializationCommand) {
    workRequire(before.modelVersion==4 && after.modelVersion==4 && before.projectId==after.projectId,WorkProblem.INVALID_REFERENCE)
    val codec=DispatchMaterializationCommandCodec();val c=codec.decode(codec.encode(command))
    val proposal=materializeDispatchDependency(before,c.plan,c.task,c.newTask,c.verificationPlan,c.manifest)
    val verification=proposal.task.newVerificationPlan ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    fun row(key:WorkKey,payload:WorkPayload)=WorkRecord(WorkRecordIdentity(payloadFamily(payload),key),payload)
    val records=listOf(row(c.newTask,WTaskRevisionsPayload(proposal.task.data)),
        row(verification.key,WVerificationPlansPayload(verification.data)),row(c.manifest,WInputManifestsPayload(proposal.manifest.data)),
        row(WorkKey.Id(before.projectId,c.requestKey),WDispatchMaterializationPayload(c)))
    workRequire(records.map { it.identity }.distinct().size==records.size && records.none { it.identity in before.records },WorkProblem.INVALID_REFERENCE)
    val expected=before.records+records.associateBy { it.identity }
    workRequire(after.records==expected && after.bodies==before.bodies,WorkProblem.INVALID_REFERENCE)
}
internal data class DispatchCandidateCheck(val checkId:String,val command:List<String>,val safeRepeat:Boolean,val dependencies:List<String>)
internal data class DispatchPlanCandidate(val projectId:UUID,val planId:UUID,val controllerId:UUID,val entries:List<DispatchPlanEntry>,
    val candidateChecks:List<DispatchCandidateCheck> = emptyList())
internal data class WDispatchPlanPayload(val value:DispatchPlanCandidate):WorkPayload
/** Original reservation is part of the immutable plan-attempt identity, not a local retry hint. */
internal data class DispatchAttempt(val plan:WorkReference,val preparationId:UUID,val reservation:WorkCommand,
    val previous:WorkReference?=null,val rootPreparationId:UUID?=null,val startId:UUID?=null)
internal data class WDispatchAttemptPayload(val value:DispatchAttempt):WorkPayload
internal data class DispatchAttemptCommand(val requestKey:UUID,val attempt:DispatchAttempt)
internal class DispatchAttemptCommandCodec {
    private fun value(v:DispatchAttemptCommand,withKey:Boolean):JsonValue {
        val fields=linkedMapOf<String,JsonValue>("formatVersion" to num(3),"projectId" to uuid(v.attempt.reservation.projectId),
            "operation" to str("bind_dispatch_attempt"),"payload" to StrictJson.decode(DispatchAttemptCodec().encode(v.attempt)))
        if(withKey)fields["requestKey"]=uuid(v.requestKey)
        return JObject(fields)
    }
    fun encode(v:DispatchAttemptCommand):ByteArray=StrictJson.encode(value(v,true),262144).also { decode(it) }
    fun decode(bytes:ByteArray):DispatchAttemptCommand {
        val f=StrictJson.decode(bytes,JsonLimits(bytes=262144,stringBytes=65536,depth=32,collection=1024,tokens=100000))
            .fields("formatVersion","requestKey","projectId","operation","payload")
        shape(f.getValue("formatVersion").long()==3L && f.getValue("operation").string()=="bind_dispatch_attempt")
        val attempt=DispatchAttemptCodec().decode(StrictJson.encode(f.getValue("payload")))
        shape(uuid(f.getValue("projectId"))==attempt.reservation.projectId)
        return DispatchAttemptCommand(uuid(f.getValue("requestKey")),attempt)
    }
    fun fingerprint(v:DispatchAttemptCommand):Digest { encode(v);return framedDigest("writhelm.command.v3") { writeFramed(StrictJson.encode(value(v,false))) } }
}
internal class DispatchAttemptCodec {
    fun encode(v:DispatchAttempt):ByteArray {
        val successor=v.previous!=null || v.rootPreparationId!=null || v.startId!=null
        shape(!successor || v.previous!=null && v.rootPreparationId!=null && v.startId!=null)
        val fields=linkedMapOf<String,JsonValue>("formatVersion" to num(if(successor)2 else 1),"plan" to StrictJson.decode(identityCodec.encodeReference(v.plan)),
            "preparationId" to uuid(v.preparationId),"reservation" to StrictJson.decode(WorkCodec().encodeCommand(v.reservation)))
        if(successor) { fields["previous"]=StrictJson.decode(identityCodec.encodeReference(v.previous!!));fields["rootPreparationId"]=uuid(v.rootPreparationId!!);fields["startId"]=uuid(v.startId!!) }
        val bytes=StrictJson.encode(JObject(fields),262144)
        decode(bytes);return bytes
    }
    fun decode(bytes:ByteArray):DispatchAttempt {
        val value=StrictJson.decode(bytes,JsonLimits(bytes=262144,stringBytes=65536,depth=32,collection=1024,tokens=100000))
        val version=((value as? JObject)?.entries?.get("formatVersion") ?: refuse(CodecProblem.INVALID_SHAPE)).long()
        shape(version in setOf(1L,2L))
        val f=if(version==1L)value.fields("formatVersion","plan","preparationId","reservation")
            else value.fields("formatVersion","plan","preparationId","reservation","previous","rootPreparationId","startId")
        val plan=identityCodec.decodeReference(StrictJson.encode(f.getValue("plan")))
        val reservation=WorkCodec().decodeCommand(StrictJson.encode(f.getValue("reservation")))
        shape(plan.identity.family==WorkFamily.DISPATCH_PLANS && plan.identity.key.projectId==reservation.projectId &&
            reservation.operation is CReserveAssignmentOperation)
        val previous=if(version==1L)null else identityCodec.decodeReference(StrictJson.encode(f.getValue("previous")))
        shape(previous==null || previous.identity.family==WorkFamily.DISPATCH_ATTEMPTS && previous.identity.key.projectId==reservation.projectId)
        return DispatchAttempt(plan,uuid(f.getValue("preparationId")),reservation,previous,
            if(version==1L)null else uuid(f.getValue("rootPreparationId")),if(version==1L)null else uuid(f.getValue("startId")))
    }
}
/** Graph transition only. Publisher must additionally check original receipt absence at the
 * same H, authenticate current controller and audit this binding before any reservation effect. */
internal fun bindDispatchAttempt(state:WorkState,actor:WorkExecutionInput,original:DispatchAttempt):WorkEffect {
    workRequire(state.modelVersion==4,WorkProblem.NOT_READY)
    val attempt=DispatchAttemptCodec().decode(DispatchAttemptCodec().encode(original))
    val plan=(state.resolve(attempt.plan).payload as? WDispatchPlanPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    workRequire(actor.controllerId==plan.controllerId && actor.assignment==null && actor.execution==null,WorkProblem.INVALID_BINDING)
    val reserve=(attempt.reservation.operation as CReserveAssignmentOperation).value
    workRequire(attempt.reservation.projectId==state.projectId,WorkProblem.FOREIGN_REFERENCE)
    val identity=WorkRecordIdentity(WorkFamily.DISPATCH_ATTEMPTS,WorkKey.Id(state.projectId,attempt.preparationId))
    workRequire(identity !in state.records,WorkProblem.DUPLICATE_ID)
    workRequire(WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(state.projectId,reserve.assignmentId)) !in state.records &&
        WorkRecordIdentity(WorkFamily.NATIVE_INTENTS,WorkKey.Id(state.projectId,reserve.intentId)) !in state.records,WorkProblem.INVALID_TRANSITION)
    val row=WorkRecord(identity,WDispatchAttemptPayload(attempt))
    val after=try { state.derive(listOf(row),emptyList()) } catch(e:CodecFault) {
        if(e.problem==CodecProblem.INVALID_GRAPH)throw WorkFault(WorkProblem.INVALID_REFERENCE)
        throw e
    }
    return WorkEffect(state,after,identity,listOf(after.records.getValue(identity)),emptyList())
}
internal data class DispatchPlanCommand(val requestKey:UUID,val plan:DispatchPlanCandidate)

/** Closed command3 candidate. Request key identifies the receipt; fingerprint covers the exact
 * canonical semantics independently of that key, matching the existing command3 journal domain. */
internal class DispatchPlanCommandCodec {
    private val plans=DispatchPlanCandidateCodec()
    private fun value(v:DispatchPlanCommand,withKey:Boolean):JsonValue {
        val f=linkedMapOf<String,JsonValue>("formatVersion" to num(3),"projectId" to uuid(v.plan.projectId),
            "operation" to str("record_dispatch_plan"),"payload" to StrictJson.decode(plans.encode(v.plan)))
        if(withKey)f["requestKey"]=uuid(v.requestKey)
        return JObject(f)
    }
    fun encode(v:DispatchPlanCommand):ByteArray {
        val bytes=StrictJson.encode(value(v,true),262144)
        decode(bytes)
        return bytes
    }
    fun decode(bytes:ByteArray):DispatchPlanCommand {
        val f=StrictJson.decode(bytes,JsonLimits(bytes=262144,stringBytes=65536,depth=32,collection=1024,tokens=100000))
            .fields("formatVersion","requestKey","projectId","operation","payload")
        shape(f.getValue("formatVersion").long()==3L && f.getValue("operation").string()=="record_dispatch_plan")
        val plan=plans.decode(StrictJson.encode(f.getValue("payload")))
        shape(plan.projectId==uuid(f.getValue("projectId")))
        return DispatchPlanCommand(uuid(f.getValue("requestKey")),plan)
    }
    fun fingerprint(v:DispatchPlanCommand):Digest {
        encode(v)
        return framedDigest("writhelm.command.v3") { writeFramed(StrictJson.encode(value(v,false))) }
    }
}

/** Pure model4 transition only. APP must authenticate and authorize the actor at the current
 * store head and publish/replay the resulting effect atomically; this function does neither. */
internal fun recordDispatchPlan(state:WorkState,actor:WorkExecutionInput,plan:DispatchPlanCandidate):WorkEffect {
    workRequire(state.modelVersion==4,WorkProblem.NOT_READY)
    workRequire(plan.projectId==state.projectId,WorkProblem.FOREIGN_REFERENCE)
    workRequire(actor.controllerId==plan.controllerId && actor.assignment==null && actor.execution==null,WorkProblem.INVALID_BINDING)
    val codec=DispatchPlanCandidateCodec()
    val normalized=codec.decode(codec.encode(plan))
    val identity=WorkRecordIdentity(WorkFamily.DISPATCH_PLANS,WorkKey.Id(state.projectId,plan.planId))
    workRequire(identity !in state.records,WorkProblem.DUPLICATE_ID)
    val used=state.records.values.mapNotNull { (it.payload as? WDispatchPlanPayload)?.value }
        .flatMap { it.entries }.flatMap { listOf(it.preparationId,it.startId) }.toSet()
    workRequire(normalized.entries.none { it.preparationId in used || it.startId in used },WorkProblem.DUPLICATE_ID)
    val row=WorkRecord(identity,WDispatchPlanPayload(normalized))
    val after=try { state.derive(listOf(row),emptyList()) } catch(e:CodecFault) {
        if(e.problem==CodecProblem.INVALID_GRAPH)throw WorkFault(WorkProblem.INVALID_REFERENCE)
        throw e
    }
    return WorkEffect(state,after,identity,listOf(after.records.getValue(identity)),emptyList())
}

/** Separate format namespace: never reinterpret historical model2 or candidate model3 records. */
internal class DispatchPlanCandidateCodec {
    private fun key(v:WorkKey)=StrictJson.decode(identityCodec.encodeKey(v))
    private fun reference(v:WorkReference)=StrictJson.decode(identityCodec.encodeReference(v))
    fun encode(v:DispatchPlanCandidate):ByteArray {
        val version=if(v.candidateChecks.isNotEmpty())3 else if(v.entries.any { it.futureDependencies.isNotEmpty() })2 else 1
        val root=obj("formatVersion" to num(version),"projectId" to uuid(v.projectId),"planId" to uuid(v.planId),
            "controllerId" to uuid(v.controllerId),"entries" to workArray(v.entries,1,256,true) { e ->
                val fields=linkedMapOf<String,JsonValue>("task" to key(e.task),"manifest" to reference(e.manifest),"profile" to key(e.profile),
                    "resources" to workArray(e.resources,1,256,true) { key(it) },"preparationId" to uuid(e.preparationId),"startId" to uuid(e.startId))
                if(version>=2)fields["futureDependencies"]=workArray(e.futureDependencies,0,256,true) { d -> obj("producer" to key(d.producer),"readiness" to str(d.readiness)) }
                JObject(fields)
            })
        val bytes=StrictJson.encode(if(version<3)root else JObject(root.entries+("candidateChecks" to JArray(v.candidateChecks.map { check ->
            obj("checkId" to str(check.checkId),"command" to JArray(check.command.map(::str)),"safeRepeat" to JBoolean(check.safeRepeat),
                "dependencies" to JArray(check.dependencies.map(::str)))
        }))))
        decode(bytes)
        return bytes
    }
    fun decode(bytes:ByteArray):DispatchPlanCandidate {
        val decoded=StrictJson.decode(bytes,JsonLimits(bytes=262144,stringBytes=65536,depth=32,collection=1024,tokens=100000)) as? JObject ?: refuse(CodecProblem.INVALID_SHAPE)
        val version=decoded.entries["formatVersion"]?.long() ?: refuse(CodecProblem.INVALID_SHAPE)
        shape(version in setOf(1L,2L,3L))
        val f=if(version==3L)decoded.fields("formatVersion","projectId","planId","controllerId","entries","candidateChecks")
            else decoded.fields("formatVersion","projectId","planId","controllerId","entries")
        val project=uuid(f.getValue("projectId"))
        val entries=workList(f.getValue("entries"),1,256,true) { item ->
            val x=if(version==1L)item.fields("task","manifest","profile","resources","preparationId","startId")
                else item.fields("task","manifest","profile","resources","preparationId","startId","futureDependencies")
            val task=identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,StrictJson.encode(x.getValue("task"))) as WorkKey.TaskRevision
            val manifest=identityCodec.decodeReference(StrictJson.encode(x.getValue("manifest")))
            val profile=identityCodec.decodeKey(WorkFamily.PROFILES,StrictJson.encode(x.getValue("profile"))) as WorkKey.Profile
            val resources=workList(x.getValue("resources"),1,256,true) { r -> identityCodec.decodeKey(WorkFamily.CLAIMS,StrictJson.encode(r)) as WorkKey.Resource }
            shape(task.projectId==project && manifest.identity.family==WorkFamily.INPUT_MANIFESTS && manifest.identity.key.projectId==project &&
                profile.projectId==project && resources.all { it.projectId==project } && resources.distinct().size==resources.size)
            val future=if(version==1L)emptyList() else workList(x.getValue("futureDependencies"),0,256,true) { d ->
                val fields=d.fields("producer","readiness")
                val producer=identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,StrictJson.encode(fields.getValue("producer"))) as WorkKey.TaskRevision
                val readiness=fields.getValue("readiness").string()
                shape(producer.projectId==project && readiness=="implemented")
                DispatchFutureDependency(producer,readiness)
            }
            shape(future.map { it.producer }.distinct().size==future.size)
            DispatchPlanEntry(task,manifest,profile,resources,uuid(x.getValue("preparationId")),uuid(x.getValue("startId")),future)
        }
        shape(entries.map { it.task.taskId }.distinct().size==entries.size)
        val ids=entries.flatMap { listOf(it.preparationId,it.startId) }
        shape(ids.distinct().size==ids.size)
        if(version==2L)shape(entries.any { it.futureDependencies.isNotEmpty() })
        val checks=if(version<3)emptyList() else workList(f.getValue("candidateChecks"),1,256,true) { item ->
            val c=item.fields("checkId","command","safeRepeat","dependencies")
            DispatchCandidateCheck(workText(c.getValue("checkId"),1,128),workList(c.getValue("command"),1,64,false) { workText(it,1,8192) },
                (c.getValue("safeRepeat") as? JBoolean)?.value ?: refuse(CodecProblem.INVALID_SHAPE),
                workList(c.getValue("dependencies"),0,256,true) { workText(it,1,128) })
        }
        shape(checks.map { it.checkId }.distinct().size==checks.size)
        val byId=checks.associateBy { it.checkId };val visiting=HashSet<String>();val visited=HashSet<String>()
        fun visit(id:String) {
            if(id in visited)return
            shape(id !in visiting && id in byId);visiting.add(id)
            byId.getValue(id).dependencies.forEach(::visit);visiting.remove(id);visited.add(id)
        }
        checks.forEach { visit(it.checkId) }
        return DispatchPlanCandidate(project,uuid(f.getValue("planId")),uuid(f.getValue("controllerId")),entries,checks)
    }
}

/** Capture the first preparation selection against one work graph. This is neither authorization
 * nor readiness: the existing reserve command must recheck both and atomically acquire claims.
 * Once an attempt is retained, recovery must reuse its original selection rather than recapture. */
internal fun dispatchPreparationSelection(state:WorkState,entry:DispatchPlanEntry):ByteArray {
    workRequire(entry.task.projectId==state.projectId && entry.profile.projectId==state.projectId &&
        entry.resources.isNotEmpty() && entry.resources.distinct().size==entry.resources.size &&
        entry.resources.all { it.projectId==state.projectId },WorkProblem.INVALID_SUBJECT)
    val task=state.task(entry.task)
    workRequire(task.executionProfile==entry.profile && entry.manifest.identity.family==WorkFamily.INPUT_MANIFESTS,WorkProblem.INVALID_SUBJECT)
    val manifest=(state.resolve(entry.manifest).payload as? WInputManifestsPayload)?.value
    workRequire(manifest!=null && manifest.task==entry.task && manifest.base==task.base,WorkProblem.INVALID_SUBJECT)
    state.record(WorkFamily.PROFILES,entry.profile)
    fun key(v:WorkKey)=StrictJson.decode(identityCodec.encodeKey(v))
    val resources=entry.resources.map { resource ->
        val old=state.records[WorkRecordIdentity(WorkFamily.CLAIMS,resource)]?.payload as? WClaimsPayload
        obj("resource" to key(resource),"expectedGeneration" to (old?.value?.generation?.let { JNumber(it) } ?: JNull))
    }
    return StrictJson.encode(obj("task" to key(entry.task),"manifest" to StrictJson.decode(identityCodec.encodeReference(entry.manifest)),
        "profile" to key(entry.profile),"resources" to JArray(resources),"reviewSubjects" to JArray(emptyList())))
}

/** Read alternatives, not an enabled writer/dispatch profile. Legacy bytes keep their old codec. */
internal sealed interface ExecutionProfileDescriptor { val key:WorkKey.Profile }
internal data class NativeProfileDescriptor(override val key:WorkKey.Profile,val value:WProfile):ExecutionProfileDescriptor
internal data class MechanicalProfileDescriptor(override val key:WorkKey.Profile,val value:MechanicalProfile):ExecutionProfileDescriptor
/** Same canonical graph, only under explicit candidate model3 selection. No enabled writer. */
internal data class WMechanicalProfilesPayload(val value:MechanicalProfile):WorkPayload
internal data class MechanicalProfile(
    val nativeProfileId:String,val qualificationEvidence:List<WSource>,val checkProfileId:String,
    val maximumPatchBytes:Long,val maximumFileBytes:Long,val maximumCheckOutputBytes:Long,val maximumCheckMillis:Long,
    val executionAssurance:String,val permissions:List<WProfilePermissionsItem>,val platform:String,val qualification:String
)

internal sealed interface ExecutionProducerDescriptor { val executionId:UUID;val workspaceId:UUID }
internal data class NativeProducerDescriptor(val value:WExecution):ExecutionProducerDescriptor {
    override val executionId get()=value.executionId
    override val workspaceId get()=value.workspaceId
}
/** No principal/credential/model/chat-session is invented for a deterministic process. The actual
 * controller authenticates separately; this metadata cannot authorize an operation. */
internal data class MechanicalProducerDescriptor(
    override val executionId:UUID,val controllerId:UUID,val nativeHostId:UUID,
    override val workspaceId:UUID,val operation:WorkKey.Id
):ExecutionProducerDescriptor

/** One bounded correction has one execution/operation and no native session or delegation.
 * New work needs a new assignment, not an adoption/rebind of an uncertain original effect. */
internal data class MechanicalBinding(
    val task:WorkKey.TaskRevision,val manifest:WorkReference,val profile:WorkKey.Profile,
    val producer:MechanicalProducerDescriptor,val resources:List<WorkKey.Resource>,val authorship:DeclaredResultAuthorship
)
internal data class MechanicalAssignment(val generation:Long,val bindingData:MechanicalBinding,val binding:WorkReference?,val state:String)
internal data class MechanicalOperation(
    val generation:Long,val assignment:WAssignmentSubject,val binding:WorkReference,
    val patch:WorkBodyReference,val witnesses:WorkBodyReference,val beforeDigest:Digest,val afterDigest:Digest,
    val outcome:String,val observedDigest:Digest?,val writerCompleted:Boolean,val evidence:List<WorkReference>,val observations:List<WSource>
)
internal data class WMechanicalAssignmentsPayload(val value:MechanicalAssignment):WorkPayload
internal data class WMechanicalBindingsPayload(val value:MechanicalBinding):WorkPayload
internal data class WMechanicalOperationsPayload(val value:MechanicalOperation):WorkPayload

internal data class MechanicalResourceExpectation(val resource:WorkKey.Resource,val generation:Long?)
internal data class MechanicalAttachment(val digest:Digest,val body:String)
internal sealed interface MechanicalCommandOperation {
    data class Reserve(val assignmentId:UUID,val binding:MechanicalBinding,val resources:List<MechanicalResourceExpectation>):MechanicalCommandOperation
    data class Bind(val assignmentId:UUID,val generation:Long,val binding:WorkReference):MechanicalCommandOperation
    data class Prepare(val assignmentId:UUID,val generation:Long,val operation:WorkKey.Id,val effect:MechanicalOperation,val bodies:List<MechanicalAttachment> = emptyList()):MechanicalCommandOperation
    data class Observe(val assignmentId:UUID,val generation:Long,val operation:WorkKey.Id,val expectedOperationGeneration:Long,val effect:MechanicalOperation):MechanicalCommandOperation
    data class Retrospective(val assignmentId:UUID,val generation:Long,val key:WorkKey.Id,val value:WRetrospectives):MechanicalCommandOperation
    data class Submit(val assignmentId:UUID,val generation:Long,val operationGeneration:Long,val result:MechanicalResultDescriptor):MechanicalCommandOperation
}
internal data class MechanicalCommand(val requestKey:UUID,val projectId:UUID,val operation:MechanicalCommandOperation)

/** Staged command4 codec, NOT a store catalogue, writer, service command, authority,
 * published attachment or trusted native observation. */
internal class MechanicalCommandCodec {
    private val mechanical=MechanicalWorkCodec()
    private val results=ResultDescriptorCodec()
    private val limits=JsonLimits(bytes=2*1024*1024,stringBytes=65536,depth=32,collection=1024,tokens=100000)
    private val constructionLimits=limits.copy(stringBytes=1024*1024)
    private fun json(k:WorkKey)=StrictJson.decode(identityCodec.encodeKey(k))
    private fun json(r:WorkReference)=StrictJson.decode(identityCodec.encodeReference(r))
    private fun generation(v:JsonValue)=v.long().also { shape(it>=0) }
    fun decode(bytes:ByteArray):MechanicalCommand {
        val f=StrictJson.decodeWorkCommand(bytes,limits,1024*1024).fields("formatVersion","requestKey","projectId","operation","payload")
        if(f.getValue("formatVersion").long()!=4L)refuse(CodecProblem.UNSUPPORTED_FORMAT)
        val project=uuid(f.getValue("projectId"))
        val payload=f.getValue("payload")
        val operation=when(f.getValue("operation").string()) {
            "reserve_mechanical" -> {
                val p=payload.fields("assignmentId","binding","resources")
                val binding=mechanical.decodeBinding(p.getValue("binding"),project)
                val resources=workList(p.getValue("resources"),1,256,true) { item ->
                    val fields=item.fields("resource","expectedGeneration")
                    MechanicalResourceExpectation(identityCodec.decodeKey(WorkFamily.CLAIMS,StrictJson.encode(fields.getValue("resource"))) as WorkKey.Resource,
                        fields.getValue("expectedGeneration").let { if(it===JNull)null else generation(it) })
                }
                shape(resources.map { it.resource }.distinct().size==resources.size && resources.map { it.resource }.toSet()==binding.resources.toSet())
                MechanicalCommandOperation.Reserve(uuid(p.getValue("assignmentId")),binding,resources)
            }
            "bind_mechanical" -> {
                val p=payload.fields("assignmentId","expectedGeneration","binding")
                val assignment=uuid(p.getValue("assignmentId"))
                val binding=identityCodec.decodeReference(StrictJson.encode(p.getValue("binding")))
                shape(binding.identity.family==WorkFamily.ASSIGNMENT_BINDINGS && binding.identity.key==WorkKey.AssignmentBinding(project,assignment,0))
                MechanicalCommandOperation.Bind(assignment,generation(p.getValue("expectedGeneration")),binding)
            }
            "prepare_mechanical_effect","observe_mechanical_effect" -> {
                val observe=f.getValue("operation").string()=="observe_mechanical_effect"
                val p=if(observe)payload.fields("assignmentId","expectedGeneration","operation","expectedOperationGeneration","effect")
                    else payload.fields("assignmentId","expectedGeneration","operation","effect","bodies")
                val assignment=uuid(p.getValue("assignmentId"));val expected=generation(p.getValue("expectedGeneration"))
                val operation=identityCodec.decodeKey(WorkFamily.NATIVE_INTENTS,StrictJson.encode(p.getValue("operation"))) as WorkKey.Id
                val effect=(mechanical.decode(WorkFamily.NATIVE_INTENTS,p.getValue("effect"),project) as WMechanicalOperationsPayload).value
                shape(operation.projectId==project && effect.assignment==WAssignmentSubject(assignment,0) &&
                    effect.binding.identity.key==WorkKey.AssignmentBinding(project,assignment,0))
                if(!observe) {
                    shape(effect.generation==0L && effect.outcome=="pending" && !effect.writerCompleted && effect.evidence.isEmpty() && effect.observations.isEmpty())
                    val bodies=workList(p.getValue("bodies"),0,2,true) { item ->
                        val fields=item.fields("digest","body")
                        val declared=digest(fields.getValue("digest"));val text=fields.getValue("body").string()
                        val content=Utf8Content.fromText(text)
                        shape(content.sizeBytes<=1024*1024 && content.digest==declared &&
                            listOf(effect.patch,effect.witnesses).any { it.digest==declared && it.byteLength==content.sizeBytes })
                        MechanicalAttachment(declared,text)
                    }
                    shape(bodies.map { it.digest }.distinct().size==bodies.size && bodies.sumOf { utf8Size(it.body).toLong() }<=1024L*1024)
                    MechanicalCommandOperation.Prepare(assignment,expected,operation,effect,bodies)
                } else {
                    val previous=generation(p.getValue("expectedOperationGeneration"));shape(previous<Long.MAX_VALUE && effect.generation==previous+1 && effect.outcome!="pending")
                    MechanicalCommandOperation.Observe(assignment,expected,operation,previous,effect)
                }
            }
            "submit_mechanical_result" -> {
                val p=payload.fields("assignmentId","expectedGeneration","expectedOperationGeneration","result")
                val assignment=uuid(p.getValue("assignmentId"))
                val result=results.decode(StrictJson.encode(p.getValue("result"))) as? MechanicalResultDescriptor ?: refuse(CodecProblem.INVALID_SHAPE)
                shape(result.key.projectId==project && result.value.assignment==WAssignmentSubject(assignment,0))
                MechanicalCommandOperation.Submit(assignment,generation(p.getValue("expectedGeneration")),generation(p.getValue("expectedOperationGeneration")),result)
            }
            "record_mechanical_retrospective" -> {
                val p=payload.fields("assignmentId","expectedGeneration","key","data")
                val assignment=uuid(p.getValue("assignmentId"))
                val key=identityCodec.decodeKey(WorkFamily.RETROSPECTIVES,StrictJson.encode(p.getValue("key"))) as WorkKey.Id
                val data=decodeWRetrospectives(p.getValue("data"));validateWorkShape(WRetrospectivesPayload(data))
                shape(key.projectId==project && data.assignment==WAssignmentSubject(assignment,0) && data.result==null)
                MechanicalCommandOperation.Retrospective(assignment,generation(p.getValue("expectedGeneration")),key,data)
            }
            else -> refuse(CodecProblem.UNSUPPORTED_FORMAT)
        }
        val command=MechanicalCommand(uuid(f.getValue("requestKey")),project,operation)
        sameProject(operationJSON(command.operation).second,project)
        // The body exception must not turn command metadata into a 2 MiB channel. Measure a
        // canonical envelope with attachment bodies blanked; bodies have their own 1 MiB budget.
        val metadata=if(operation is MechanicalCommandOperation.Prepare)
            command.copy(operation=operation.copy(bodies=operation.bodies.map { it.copy(body="") })) else command
        StrictJson.encode(value(metadata,true),262144,maxDepth=limits.depth)
        return command
    }
    private fun operationJSON(v:MechanicalCommandOperation):Pair<String,JsonValue> = when(v) {
        is MechanicalCommandOperation.Reserve -> "reserve_mechanical" to obj("assignmentId" to uuid(v.assignmentId),"binding" to mechanical.encodeBinding(v.binding),
            "resources" to workArray(v.resources,1,256,true) { obj("resource" to json(it.resource),"expectedGeneration" to (it.generation?.let(::JNumber) ?: JNull)) })
        is MechanicalCommandOperation.Bind -> "bind_mechanical" to obj("assignmentId" to uuid(v.assignmentId),"expectedGeneration" to JNumber(v.generation),"binding" to json(v.binding))
        is MechanicalCommandOperation.Prepare -> "prepare_mechanical_effect" to obj("assignmentId" to uuid(v.assignmentId),"expectedGeneration" to JNumber(v.generation),
            "operation" to json(v.operation),"effect" to mechanical.encode(WMechanicalOperationsPayload(v.effect)),
            "bodies" to workArray(v.bodies,0,2,true) { obj("digest" to digest(it.digest),"body" to str(it.body)) })
        is MechanicalCommandOperation.Observe -> "observe_mechanical_effect" to obj("assignmentId" to uuid(v.assignmentId),"expectedGeneration" to JNumber(v.generation),
            "operation" to json(v.operation),"expectedOperationGeneration" to JNumber(v.expectedOperationGeneration),"effect" to mechanical.encode(WMechanicalOperationsPayload(v.effect)))
        is MechanicalCommandOperation.Retrospective -> "record_mechanical_retrospective" to obj("assignmentId" to uuid(v.assignmentId),"expectedGeneration" to JNumber(v.generation),
            "key" to json(v.key),"data" to encodeWRetrospectives(v.value))
        is MechanicalCommandOperation.Submit -> "submit_mechanical_result" to obj("assignmentId" to uuid(v.assignmentId),"expectedGeneration" to JNumber(v.generation),
            "expectedOperationGeneration" to JNumber(v.operationGeneration),"result" to StrictJson.decode(results.encode(v.result)))
    }
    private fun value(command:MechanicalCommand,includeKey:Boolean):JsonValue {
        val (name,payload)=operationJSON(command.operation)
        val fields=linkedMapOf<String,JsonValue>("formatVersion" to JNumber(4),"projectId" to uuid(command.projectId),"operation" to str(name),"payload" to payload)
        if(includeKey)fields["requestKey"]=uuid(command.requestKey)
        return JObject(fields)
    }
    fun encode(command:MechanicalCommand):ByteArray {
        val bytes=JsonConstruction.bounded(constructionLimits,{ _,_-> },false) { StrictJson.encode(value(command,true),limits.bytes,maxDepth=limits.depth) }
        decode(bytes);return bytes
    }
    fun fingerprint(command:MechanicalCommand):Digest {
        val canonical=decode(encode(command))
        return framedDigest("writhelm.command.v4") { writeFramed(StrictJson.encode(value(canonical,false),limits.bytes)) }
    }
}

/** Service-derived qualification, not a client-supplied profile label. No production provider is
 * installed by this candidate reducer. The exact profile reference and host are checked at H. */
internal data class MechanicalProfileQualification(val profile:WorkReference,val nativeHostId:UUID,val platform:String)
/** A qualified adapter's actual exclusive work-area/base observation at the same H; not a body
 * assertion, a profile label, or a substitute for current resource claims. */
internal data class MechanicalWorkspaceQualification(val workspaceId:UUID,val nativeHostId:UUID,val base:WBase,val manifest:WorkReference)
internal data class MechanicalReservationFacts(val base:WorkExternalFacts,val qualifiedProfiles:Set<MechanicalProfileQualification>,
    val qualifiedWorkspaces:Set<MechanicalWorkspaceQualification>)
/** Actual native effect observations are service-derived, never constructed from an operation body.
 * Prepared means the exact original operation and witnesses are already durably retained in qualified
 * custody, not merely captured or asserted. A production provider is not installed; independent
 * writer fencing is mandatory for terminal data. */
internal data class MechanicalPreparedObservation(val operation:WorkKey.Id,val effect:MechanicalOperation,val workspaceId:UUID,val nativeHostId:UUID)
internal data class MechanicalEffectObservation(val operation:WorkKey.Id,val previousGeneration:Long,val effect:MechanicalOperation,
    val workspaceId:UUID,val nativeHostId:UUID,val writersFenced:Boolean)
internal data class MechanicalEffectFacts(val reservation:MechanicalReservationFacts,val prepared:Set<MechanicalPreparedObservation>,
    val observations:Set<MechanicalEffectObservation>)
/** Exact retained result capture after the original operation; this is not inferred from a caller's
 * chosen snapshots or from after-bytes alone. No production capture provider is installed. */
internal data class MechanicalResultObservation(val operation:WorkKey.Id,val operationGeneration:Long,val afterDigest:Digest,
    val artifacts:List<WSnapshot>,val changes:WorkBodyReference)
internal data class MechanicalSubmissionFacts(val base:WorkExternalFacts,val results:Set<MechanicalResultObservation>)

/** Pure candidate reducer. The caller MUST authenticate the current
 * controller and TASK_DISPATCH at the same captured H, apply receipt/replay and publication rules,
 * and supply actual source/qualification facts. This class is not wired to any route or store. */
internal class MechanicalReservationReducer {
    private fun source(h:WorkState,facts:WorkExternalFacts,source:WSource) {
        when(source) {
            is WSourceVariant0Value -> workRequire(source.value.reference in facts.knowledgeSources,WorkProblem.BLOCKED_INPUT)
            is WSourceVariant1Value -> workRequire(source.value.artifact in facts.retainedSnapshots,WorkProblem.INVALID_SUBJECT)
            is WSourceVariant2Value -> h.resolve(source.value.reference)
        }
    }
    private fun ready(h:WorkState,b:MechanicalBinding,actor:WorkExecutionInput,facts:MechanicalReservationFacts) {
        val t=h.task(b.task);val external=facts.base
        workRequire(actor.assignment==null && actor.execution==null && actor.controllerId==b.producer.controllerId,WorkProblem.INVALID_BINDING)
        workRequire(t.kind=="remediation" && t.state=="executable" && t.unresolved.isEmpty() && t.executionProfile==b.profile,WorkProblem.NOT_READY)
        val selectedPlan=t.verificationPlan ?: throw WorkFault(WorkProblem.NOT_READY)
        h.governingVerificationPlan(b.task,selectedPlan)
        // Until exact pre-dispatch evidence/report mapping is implemented, never let grouping or
        // a local candidate silently postpone an earlier required gate. This is a held case.
        workRequire(h.records.values.none { r -> (r.payload as? WVerificationPlansPayload)?.value?.let { plan ->
            plan.task==b.task && plan.gates.any { it.stage=="dispatch" && !it.deferred } }==true },WorkProblem.NOT_READY)
        workRequire(t.approvedDesign.isNotEmpty(),WorkProblem.BLOCKED_INPUT)
        workRequire(t.approvedDesign.all { it in external.approvedDecisions },WorkProblem.BLOCKED_INPUT)
        val profile=(h.record(WorkFamily.PROFILES,b.profile).payload as? WMechanicalProfilesPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
        val profileRef=h.reference(WorkFamily.PROFILES,b.profile)
        workRequire(profile.qualification=="qualified" && profile.qualificationEvidence.isNotEmpty() &&
            MechanicalProfileQualification(profileRef,b.producer.nativeHostId,profile.platform) in facts.qualifiedProfiles,WorkProblem.NOT_READY)
        profile.qualificationEvidence.forEach { source(h,external,it) }
        val manifest=(h.resolve(b.manifest).payload as? WInputManifestsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
        workRequire(manifest.task==b.task && manifest.base==t.base && manifest.knownGaps.isEmpty() && b.manifest in external.verifiedPackets,WorkProblem.BLOCKED_INPUT)
        val base=t.base ?: throw WorkFault(WorkProblem.NOT_READY)
        workRequire(MechanicalWorkspaceQualification(b.producer.workspaceId,b.producer.nativeHostId,base,b.manifest) in facts.qualifiedWorkspaces,WorkProblem.NOT_READY)
        manifest.items.forEach { source(h,external,it.source) };source(h,external,manifest.declaredClosureAssessment)
        val applicability=(h.record(WorkFamily.APPLICABILITY,manifest.applicability).payload as? WApplicabilityPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
        workRequire(applicability.entries.none { it.state!="applicable" },WorkProblem.BLOCKED_INPUT)
        var readinessScans=0
        val predecessors=t.dependencies.map { dependency ->
            val selected=dependency.selectedResult ?: throw WorkFault(WorkProblem.NOT_READY)
            val row=h.resolve(selected)
            val result=when(val p=row.payload) {
                is WResultsPayload -> NativeResultDescriptor(row.identity.key as WorkKey.Id,p.value)
                is WMechanicalResultsPayload -> MechanicalResultDescriptor(row.identity.key as WorkKey.Id,p.value)
                else -> throw WorkFault(WorkProblem.INVALID_REFERENCE)
            }
            val resultTask=when(result) { is NativeResultDescriptor -> result.value.task;is MechanicalResultDescriptor -> result.value.task }
            workRequire(resultTask==dependency.producer,WorkProblem.NOT_READY)
            val evidence=when(result) { is NativeResultDescriptor -> result.value.evidence;is MechanicalResultDescriptor -> result.value.evidence }
            val artifacts=when(result) { is NativeResultDescriptor -> result.value.artifacts;is MechanicalResultDescriptor -> result.value.artifacts }
            fun has(family:WorkFamily,predicate:(WorkPayload)->Boolean)=h.records.values.any {
                if(++readinessScans>h.budget.edges)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
                it.identity.family==family && predicate(it.payload)
            }
            val isReady=when(dependency.readiness) {
                "implemented" -> true
                "cheap-checked" -> evidence.any { ref -> (h.resolve(ref).payload as? WEvidencePayload)?.value?.let { e ->
                    e.outcome=="passed" && e.boundary in setOf("pure","component","joint") && e.subjects.any { subject -> when(subject) {
                        is WSourceVariant1Value -> subject.value.artifact in artifacts
                        is WSourceVariant2Value -> subject.value.reference==selected
                        else -> false
                    } } }==true }
                "reviewed" -> has(WorkFamily.VERIFICATION_REPORTS) { p -> (p as WVerificationReportsPayload).value.let { r ->
                    r.task==dependency.producer && r.plan==h.task(dependency.producer).verificationPlan && selected in r.subjects &&
                        r.findings.none { it.blocking && (it.affectedResultIds.isEmpty() || result.key.id in it.affectedResultIds) } &&
                        nonAuthorResultCandidate(r.reviewer.principalId,r.reviewer.executionId,result,knownResultAuthors(result),false) } }
                "accepted" -> has(WorkFamily.ACCEPTANCES) { (it as WAcceptancesPayload).value.result==selected }
                "integrated" -> has(WorkFamily.INTEGRATIONS) { (it as WIntegrationsPayload).value.result==selected }
                else -> false
            }
            workRequire(isReady,WorkProblem.NOT_READY);result
        }
        workRequire(predecessors.isNotEmpty() && b.authorship.sourceResults.toSet()==predecessors.map { ResultDescriptorCodec().reference(it) }.toSet(),WorkProblem.INVALID_SUBJECT)
        requireDeclaredAuthorship(b.authorship,actor.principalId,predecessors.flatMap { knownResultAuthors(it) }.toSet())
    }
    fun reserve(h:WorkState,original:MechanicalCommand,actor:WorkExecutionInput,facts:MechanicalReservationFacts):WorkEffect {
        workRequire(h.modelVersion==3,WorkProblem.NOT_READY)
        val command=MechanicalCommandCodec().decode(MechanicalCommandCodec().encode(original))
        workRequire(command.projectId==h.projectId,WorkProblem.FOREIGN_REFERENCE)
        val v=command.operation as? MechanicalCommandOperation.Reserve ?: throw WorkFault(WorkProblem.INVALID_SUBJECT)
        val b=v.binding;ready(h,b,actor,facts)
        val assignment=WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(h.projectId,v.assignmentId))
        workRequire(assignment !in h.records,WorkProblem.DUPLICATE_ID)
        val occupied=h.records.values.mapNotNull { r -> (r.payload as? WClaimsPayload)?.value?.let { r.identity.key as WorkKey.Resource to it } }
        val holder=WClaimsHolderVariant0Value(WClaimsHolderVariant0("assignment",WAssignmentSubject(v.assignmentId,0)))
        val basis=WSourceVariant2Value(WSourceVariant2("work",b.manifest))
        val claims=v.resources.map { expectation ->
            val identity=WorkRecordIdentity(WorkFamily.CLAIMS,expectation.resource)
            val old=(h.records[identity]?.payload as? WClaimsPayload)?.value
            workRequire(old?.generation==expectation.generation,WorkProblem.STALE_GENERATION)
            workRequire(old==null || old.state=="free",WorkProblem.CLAIM_CONFLICT)
            occupied.forEach { (other,c) -> if(c.state!="free")workRequire(other!=expectation.resource && expectation.resource !in c.incompatible && other !in (old?.incompatible ?: emptyList()),WorkProblem.CLAIM_CONFLICT) }
            WorkRecord(identity,WClaimsPayload(WClaims(if(old==null)0 else nextGeneration(old.generation),holder,b.producer.workspaceId,"held",old?.incompatible ?: emptyList(),basis)))
        }
        val changed=claims+WorkRecord(assignment,WMechanicalAssignmentsPayload(MechanicalAssignment(0,b,null,"reserved")))
        val after=h.derive(changed,emptyList())
        return WorkEffect(h,after,assignment,changed.map { after.records.getValue(it.identity) },emptyList())
    }
    fun bind(h:WorkState,original:MechanicalCommand,actor:WorkExecutionInput,facts:MechanicalReservationFacts):WorkEffect {
        workRequire(h.modelVersion==3,WorkProblem.NOT_READY)
        val command=MechanicalCommandCodec().decode(MechanicalCommandCodec().encode(original))
        workRequire(command.projectId==h.projectId,WorkProblem.FOREIGN_REFERENCE)
        val v=command.operation as? MechanicalCommandOperation.Bind ?: throw WorkFault(WorkProblem.INVALID_SUBJECT)
        val key=WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(h.projectId,v.assignmentId))
        val a=(h.records[key]?.payload as? WMechanicalAssignmentsPayload)?.value ?: throw WorkFault(WorkProblem.WRONG_ASSIGNMENT)
        workRequire(a.generation==v.generation,WorkProblem.STALE_GENERATION)
        workRequire(a.state=="reserved",WorkProblem.INVALID_TRANSITION);ready(h,a.bindingData,actor,facts)
        val binding=WorkRecord(WorkRecordIdentity(WorkFamily.ASSIGNMENT_BINDINGS,WorkKey.AssignmentBinding(h.projectId,v.assignmentId,0)),WMechanicalBindingsPayload(a.bindingData))
        workRequire(binding.identity !in h.records,WorkProblem.DUPLICATE_ID)
        workRequire(v.binding==WorkReference(binding.identity,WorkCodec(modelVersion=3).digest(binding)),WorkProblem.DIGEST_MISMATCH)
        val changed=listOf(binding,WorkRecord(key,WMechanicalAssignmentsPayload(a.copy(generation=nextGeneration(a.generation),binding=v.binding,state="bound"))))
        val after=h.derive(changed,emptyList())
        return WorkEffect(h,after,key,changed.map { after.records.getValue(it.identity) },emptyList())
    }
    fun prepare(h:WorkState,original:MechanicalCommand,actor:WorkExecutionInput,facts:MechanicalEffectFacts):WorkEffect {
        workRequire(h.modelVersion==3,WorkProblem.NOT_READY)
        val command=MechanicalCommandCodec().decode(MechanicalCommandCodec().encode(original))
        workRequire(command.projectId==h.projectId,WorkProblem.FOREIGN_REFERENCE)
        val v=command.operation as? MechanicalCommandOperation.Prepare ?: throw WorkFault(WorkProblem.INVALID_SUBJECT)
        val assignment=WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(h.projectId,v.assignmentId))
        val a=(h.records[assignment]?.payload as? WMechanicalAssignmentsPayload)?.value ?: throw WorkFault(WorkProblem.WRONG_ASSIGNMENT)
        workRequire(a.generation==v.generation,WorkProblem.STALE_GENERATION)
        workRequire(a.state=="bound" && a.binding==v.effect.binding && v.operation==a.bindingData.producer.operation,WorkProblem.INVALID_BINDING)
        ready(h,a.bindingData,actor,facts.reservation)
        val observation=MechanicalPreparedObservation(v.operation,v.effect,a.bindingData.producer.workspaceId,a.bindingData.producer.nativeHostId)
        workRequire(observation in facts.prepared,WorkProblem.NATIVE_UNKNOWN)
        val op=WorkRecordIdentity(WorkFamily.NATIVE_INTENTS,v.operation)
        workRequire(op !in h.records,WorkProblem.DUPLICATE_ID)
        val bodies=v.bodies.map { Utf8Content.fromText(it.body) }
        // A redundant attachment cannot be reconstructed from the resulting H->C body diff.
        // Exact-key replay must resolve its receipt before evaluation, never reattach old bytes.
        bodies.forEach { body -> workRequire(body.digest !in h.bodies,WorkProblem.DUPLICATE_ID) }
        val added=bodies
        listOf(v.effect.patch,v.effect.witnesses).forEach { ref ->
            val body=h.bodies[ref.digest] ?: added.singleOrNull { it.digest==ref.digest }
            workRequire(body!=null && body.sizeBytes==ref.byteLength,WorkProblem.MISSING_BODY)
        }
        val changed=listOf(WorkRecord(op,WMechanicalOperationsPayload(v.effect)),
            WorkRecord(assignment,WMechanicalAssignmentsPayload(a.copy(generation=nextGeneration(a.generation),state="active"))))
        val after=h.derive(changed,added)
        return WorkEffect(h,after,op,changed.map { after.records.getValue(it.identity) },added)
    }
    fun observe(h:WorkState,original:MechanicalCommand,actor:WorkExecutionInput,facts:MechanicalEffectFacts):WorkEffect {
        workRequire(h.modelVersion==3,WorkProblem.NOT_READY)
        val command=MechanicalCommandCodec().decode(MechanicalCommandCodec().encode(original))
        workRequire(command.projectId==h.projectId,WorkProblem.FOREIGN_REFERENCE)
        val v=command.operation as? MechanicalCommandOperation.Observe ?: throw WorkFault(WorkProblem.INVALID_SUBJECT)
        val assignment=WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(h.projectId,v.assignmentId))
        val a=(h.records[assignment]?.payload as? WMechanicalAssignmentsPayload)?.value ?: throw WorkFault(WorkProblem.WRONG_ASSIGNMENT)
        workRequire(a.generation==v.generation,WorkProblem.STALE_GENERATION)
        workRequire(actor.assignment==null && actor.execution==null && actor.controllerId==a.bindingData.producer.controllerId,WorkProblem.INVALID_BINDING)
        workRequire(a.state in setOf("active","unknown") && v.operation==a.bindingData.producer.operation && a.binding==v.effect.binding,WorkProblem.INVALID_BINDING)
        val op=WorkRecordIdentity(WorkFamily.NATIVE_INTENTS,v.operation)
        val previous=h.records[op] ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
        val old=(previous.payload as? WMechanicalOperationsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
        workRequire(old.generation==v.expectedOperationGeneration,WorkProblem.STALE_GENERATION)
        val next=WorkRecord(op,WMechanicalOperationsPayload(v.effect));validateMechanicalRecordEdge(previous,next)
        val observation=MechanicalEffectObservation(v.operation,old.generation,v.effect,a.bindingData.producer.workspaceId,a.bindingData.producer.nativeHostId,v.effect.writerCompleted)
        workRequire(observation in facts.observations,WorkProblem.NATIVE_UNKNOWN)
        v.effect.observations.forEach { source(h,facts.reservation.base,it) }
        v.effect.evidence.forEach { workRequire(it.identity.family==WorkFamily.EVIDENCE,WorkProblem.INVALID_REFERENCE);h.resolve(it) }
        val changed=listOf(next,WorkRecord(assignment,WMechanicalAssignmentsPayload(a.copy(generation=nextGeneration(a.generation),
            state=if(v.effect.writerCompleted)"completed" else "unknown"))))
        val after=h.derive(changed,emptyList())
        return WorkEffect(h,after,op,changed.map { after.records.getValue(it.identity) },emptyList())
    }
    private fun completed(h:WorkState,id:UUID,generation:Long,actor:WorkExecutionInput):MechanicalAssignment {
        val a=(h.records[WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(h.projectId,id))]?.payload as? WMechanicalAssignmentsPayload)?.value
            ?: throw WorkFault(WorkProblem.WRONG_ASSIGNMENT)
        workRequire(a.generation==generation,WorkProblem.STALE_GENERATION)
        workRequire(actor.assignment==null && actor.execution==null && actor.controllerId==a.bindingData.producer.controllerId &&
            actor.principalId==a.bindingData.authorship.declaredBy,WorkProblem.INVALID_BINDING)
        workRequire(a.state=="completed",WorkProblem.INVALID_TRANSITION)
        return a
    }
    fun retrospective(h:WorkState,original:MechanicalCommand,actor:WorkExecutionInput,facts:WorkExternalFacts):WorkEffect {
        workRequire(h.modelVersion==3,WorkProblem.NOT_READY)
        val command=MechanicalCommandCodec().decode(MechanicalCommandCodec().encode(original))
        workRequire(command.projectId==h.projectId,WorkProblem.FOREIGN_REFERENCE)
        val v=command.operation as? MechanicalCommandOperation.Retrospective ?: throw WorkFault(WorkProblem.INVALID_SUBJECT)
        val a=completed(h,v.assignmentId,v.generation,actor)
        workRequire(v.value.task==a.bindingData.task && v.value.assignment==WAssignmentSubject(v.assignmentId,0) && v.value.result==null,WorkProblem.INVALID_SUBJECT)
        v.value.observations.forEach { observation ->
            source(h,facts,observation.observed);observation.expectation?.let { source(h,facts,it) }
            observation.normativeDecision?.let { workRequire(it in facts.approvedDecisions,WorkProblem.BLOCKED_INPUT) }
            observation.effectEvidence.forEach { h.resolve(it) }
        }
        val row=WorkRecord(WorkRecordIdentity(WorkFamily.RETROSPECTIVES,v.key),WRetrospectivesPayload(v.value))
        workRequire(row.identity !in h.records,WorkProblem.DUPLICATE_ID)
        val after=h.derive(listOf(row),emptyList())
        return WorkEffect(h,after,row.identity,listOf(after.records.getValue(row.identity)),emptyList())
    }
    fun submit(h:WorkState,original:MechanicalCommand,actor:WorkExecutionInput,facts:MechanicalSubmissionFacts):WorkEffect {
        workRequire(h.modelVersion==3,WorkProblem.NOT_READY)
        val command=MechanicalCommandCodec().decode(MechanicalCommandCodec().encode(original))
        workRequire(command.projectId==h.projectId,WorkProblem.FOREIGN_REFERENCE)
        val v=command.operation as? MechanicalCommandOperation.Submit ?: throw WorkFault(WorkProblem.INVALID_SUBJECT)
        val a=completed(h,v.assignmentId,v.generation,actor);val b=a.bindingData
        val operation=(h.record(WorkFamily.NATIVE_INTENTS,b.producer.operation).payload as? WMechanicalOperationsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
        workRequire(operation.generation==v.operationGeneration,WorkProblem.STALE_GENERATION)
        workRequire(operation.outcome=="applied" && operation.writerCompleted,WorkProblem.NATIVE_UNKNOWN)
        val result=v.result.value
        workRequire(result.task==b.task && result.assignment==WAssignmentSubject(v.assignmentId,0) && result.manifest==b.manifest &&
            result.producer==b.producer && result.patch==operation.patch && result.authorship==b.authorship,WorkProblem.INVALID_SUBJECT)
        val task=h.task(b.task)
        workRequire(task.kind=="remediation" && task.state=="executable" && task.unresolved.isEmpty() && task.approvedDesign.isNotEmpty(),WorkProblem.NOT_READY)
        workRequire(task.approvedDesign.all { it in facts.base.approvedDecisions } && b.manifest in facts.base.verifiedPackets,WorkProblem.BLOCKED_INPUT)
        workRequire(result.artifacts.all { it in facts.base.retainedSnapshots } && WorkSubmittedDiff(result.task,result.artifacts,result.changes) in facts.base.submittedDiffs,WorkProblem.INVALID_SUBJECT)
        workRequire(MechanicalResultObservation(b.producer.operation,operation.generation,operation.afterDigest,result.artifacts,result.changes) in facts.results,WorkProblem.NATIVE_UNKNOWN)
        val predecessors=task.dependencies.map { dependency ->
            val selected=dependency.selectedResult ?: throw WorkFault(WorkProblem.NOT_READY)
            val row=h.resolve(selected)
            when(val p=row.payload) {
                is WResultsPayload -> NativeResultDescriptor(row.identity.key as WorkKey.Id,p.value)
                is WMechanicalResultsPayload -> MechanicalResultDescriptor(row.identity.key as WorkKey.Id,p.value)
                else -> throw WorkFault(WorkProblem.INVALID_REFERENCE)
            }
        }
        requireMechanicalResultAuthorship(v.result,actor.principalId,predecessors)
        val row=WorkRecord(WorkRecordIdentity(WorkFamily.RESULTS,v.result.key),WMechanicalResultsPayload(result))
        workRequire(row.identity !in h.records,WorkProblem.DUPLICATE_ID)
        workRequire(h.records.values.none { (it.payload as? WMechanicalResultsPayload)?.value?.assignment==result.assignment },WorkProblem.INVALID_TRANSITION)
        val after=h.derive(listOf(row),emptyList())
        return WorkEffect(h,after,row.identity,listOf(after.records.getValue(row.identity)),emptyList())
    }
}

/** Candidate original-H -> C audit of the exact mechanical row/body footprint and actor binding.
 * Both snapshots must already be fully validated. This is NOT receipt authentication, original
 * authority, task-readiness proof, native capture/fencing verification or a Git history auditor.
 * Those separately required checks remain held until a selected model3 writer is integrated. */
internal fun validateMechanicalTransition(h:WorkState,c:WorkState,original:MechanicalCommand,actor:WorkExecutionInput) {
    workRequire(h.modelVersion==3 && c.modelVersion==3 && h.projectId==c.projectId,WorkProblem.INVALID_SUBJECT)
    val command=MechanicalCommandCodec().decode(MechanicalCommandCodec().encode(original))
    workRequire(command.projectId==h.projectId && actor.assignment==null && actor.execution==null,WorkProblem.INVALID_BINDING)
    val changed=(h.records.keys+c.records.keys).filter { h.records[it]!=c.records[it] }.toSet()
    val bodyChanges=(h.bodies.keys+c.bodies.keys).filter { h.bodies[it]!=c.bodies[it] }.toSet()
    val project=h.projectId
    fun assignment(id:UUID)=WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(project,id))
    fun oldAssignment(id:UUID):MechanicalAssignment=(h.records[assignment(id)]?.payload as? WMechanicalAssignmentsPayload)?.value ?: throw WorkFault(WorkProblem.WRONG_ASSIGNMENT)
    fun newAssignment(id:UUID):MechanicalAssignment=(c.records[assignment(id)]?.payload as? WMechanicalAssignmentsPayload)?.value ?: throw WorkFault(WorkProblem.WRONG_ASSIGNMENT)
    fun controller(b:MechanicalBinding,author:Boolean) {
        workRequire(actor.controllerId==b.producer.controllerId && (!author || actor.principalId==b.authorship.declaredBy),WorkProblem.INVALID_BINDING)
    }
    fun sameBodyChanges(expected:Set<Digest>) { workRequire(bodyChanges==expected && bodyChanges.all { it !in h.bodies && it in c.bodies },WorkProblem.INVALID_SUBJECT) }
    when(val v=command.operation) {
        is MechanicalCommandOperation.Reserve -> {
            controller(v.binding,true);val id=assignment(v.assignmentId);val a=newAssignment(v.assignmentId)
            workRequire(id !in h.records && a==MechanicalAssignment(0,v.binding,null,"reserved"),WorkProblem.INVALID_TRANSITION)
            validateMechanicalRecordEdge(null,c.records[id])
            val holder=WClaimsHolderVariant0Value(WClaimsHolderVariant0("assignment",WAssignmentSubject(v.assignmentId,0)))
            val basis=WSourceVariant2Value(WSourceVariant2("work",v.binding.manifest))
            val ids=v.resources.map { e ->
                val key=WorkRecordIdentity(WorkFamily.CLAIMS,e.resource)
                val old=(h.records[key]?.payload as? WClaimsPayload)?.value
                workRequire(old?.generation==e.generation && (old==null || old.state=="free"),WorkProblem.STALE_GENERATION)
                val expected=WClaims(if(old==null)0 else nextGeneration(old.generation),holder,v.binding.producer.workspaceId,"held",old?.incompatible ?: emptyList(),basis)
                workRequire((c.records[key]?.payload as? WClaimsPayload)?.value==expected,WorkProblem.INVALID_SUBJECT);key
            }.toSet()
            workRequire(changed==ids+id,WorkProblem.INVALID_SUBJECT);sameBodyChanges(emptySet())
        }
        is MechanicalCommandOperation.Bind -> {
            val id=assignment(v.assignmentId);val old=oldAssignment(v.assignmentId);val next=newAssignment(v.assignmentId)
            controller(old.bindingData,true);workRequire(old.generation==v.generation && old.state=="reserved",WorkProblem.STALE_GENERATION)
            val key=WorkRecordIdentity(WorkFamily.ASSIGNMENT_BINDINGS,WorkKey.AssignmentBinding(project,v.assignmentId,0))
            val row=c.records[key] ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
            workRequire(key !in h.records && row.payload==WMechanicalBindingsPayload(old.bindingData) &&
                v.binding==WorkReference(key,WorkCodec(modelVersion=3).digest(row)) && next.binding==v.binding && next.state=="bound",WorkProblem.INVALID_BINDING)
            validateMechanicalRecordEdge(h.records[id],c.records[id]);validateMechanicalRecordEdge(null,row)
            workRequire(changed==setOf(id,key),WorkProblem.INVALID_SUBJECT);sameBodyChanges(emptySet())
        }
        is MechanicalCommandOperation.Prepare -> {
            val id=assignment(v.assignmentId);val old=oldAssignment(v.assignmentId);val next=newAssignment(v.assignmentId)
            controller(old.bindingData,true);val key=WorkRecordIdentity(WorkFamily.NATIVE_INTENTS,v.operation)
            workRequire(old.generation==v.generation && old.state=="bound" && old.binding==v.effect.binding &&
                v.operation==old.bindingData.producer.operation && next.state=="active" && key !in h.records &&
                c.records[key]?.payload==WMechanicalOperationsPayload(v.effect),WorkProblem.INVALID_BINDING)
            validateMechanicalRecordEdge(h.records[id],c.records[id]);validateMechanicalRecordEdge(null,c.records[key])
            val supplied=v.bodies.map { Utf8Content.fromText(it.body) }
            supplied.forEach { body -> workRequire(body.digest !in h.bodies && c.bodies[body.digest]==body,WorkProblem.DIGEST_MISMATCH) }
            val expected=supplied.map { it.digest }.toSet()
            sameBodyChanges(expected);workRequire(changed==setOf(id,key),WorkProblem.INVALID_SUBJECT)
        }
        is MechanicalCommandOperation.Observe -> {
            val id=assignment(v.assignmentId);val old=oldAssignment(v.assignmentId);val next=newAssignment(v.assignmentId)
            controller(old.bindingData,false);val key=WorkRecordIdentity(WorkFamily.NATIVE_INTENTS,v.operation)
            val prior=(h.records[key]?.payload as? WMechanicalOperationsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
            workRequire(old.generation==v.generation && old.state in setOf("active","unknown") && v.operation==old.bindingData.producer.operation &&
                prior.generation==v.expectedOperationGeneration && c.records[key]?.payload==WMechanicalOperationsPayload(v.effect) &&
                next.state==(if(v.effect.writerCompleted)"completed" else "unknown"),WorkProblem.INVALID_BINDING)
            validateMechanicalRecordEdge(h.records[id],c.records[id]);validateMechanicalRecordEdge(h.records[key],c.records[key])
            workRequire(changed==setOf(id,key),WorkProblem.INVALID_SUBJECT);sameBodyChanges(emptySet())
        }
        is MechanicalCommandOperation.Retrospective -> {
            val a=oldAssignment(v.assignmentId);controller(a.bindingData,true)
            val key=WorkRecordIdentity(WorkFamily.RETROSPECTIVES,v.key)
            workRequire(a.generation==v.generation && a.state=="completed" && v.value.task==a.bindingData.task && key !in h.records &&
                c.records[key]?.payload==WRetrospectivesPayload(v.value),WorkProblem.INVALID_SUBJECT)
            workRequire(changed==setOf(key),WorkProblem.INVALID_SUBJECT);sameBodyChanges(emptySet())
        }
        is MechanicalCommandOperation.Submit -> {
            val a=oldAssignment(v.assignmentId);controller(a.bindingData,true)
            val key=WorkRecordIdentity(WorkFamily.RESULTS,v.result.key)
            val operation=(h.records[WorkRecordIdentity(WorkFamily.NATIVE_INTENTS,a.bindingData.producer.operation)]?.payload as? WMechanicalOperationsPayload)?.value
                ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
            workRequire(a.generation==v.generation && a.state=="completed" && operation.generation==v.operationGeneration && operation.outcome=="applied" && operation.writerCompleted &&
                key !in h.records && c.records[key]?.payload==WMechanicalResultsPayload(v.result.value),WorkProblem.INVALID_SUBJECT)
            validateMechanicalRecordEdge(null,c.records[key])
            workRequire(changed==setOf(key),WorkProblem.INVALID_SUBJECT);sameBodyChanges(emptySet())
        }
    }
}

/** Reconstruct the canonical command for a *successful* mechanical H->C edge from validated
 * snapshots, receipt operation/request key and exact result identity. Newly supplied prepare
 * attachments are exactly the new C bodies: redundant existing attachments are refused by the
 * reducer. Caller MUST compare the reconstructed fingerprint/result to the actual receipt and
 * separately authenticate original-H authority and native evidence. Rejections have no reconstructible
 * subject by design and are not handled here. No store or Git history integration is enabled. */
internal fun reconstructMechanicalTransition(h:WorkState,c:WorkState,operation:String,requestKey:UUID,
    result:WorkRecordIdentity,actor:WorkExecutionInput):MechanicalCommand {
    workRequire(h.modelVersion==3 && c.modelVersion==3 && h.projectId==c.projectId && result.key.projectId==h.projectId,WorkProblem.FOREIGN_REFERENCE)
    val project=h.projectId
    fun assignment(id:UUID):MechanicalAssignment=(h.records[WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(project,id))]?.payload as? WMechanicalAssignmentsPayload)?.value
        ?: throw WorkFault(WorkProblem.WRONG_ASSIGNMENT)
    val v:MechanicalCommandOperation=when(operation) {
        "reserve_mechanical" -> {
            workRequire(result.family==WorkFamily.ASSIGNMENTS,WorkProblem.INVALID_REFERENCE)
            val id=(result.key as WorkKey.Id).id
            val a=(c.records[result]?.payload as? WMechanicalAssignmentsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
            val resources=a.bindingData.resources.map { key -> MechanicalResourceExpectation(key,(h.records[WorkRecordIdentity(WorkFamily.CLAIMS,key)]?.payload as? WClaimsPayload)?.value?.generation) }
            MechanicalCommandOperation.Reserve(id,a.bindingData,resources)
        }
        "bind_mechanical" -> {
            workRequire(result.family==WorkFamily.ASSIGNMENTS,WorkProblem.INVALID_REFERENCE)
            val id=(result.key as WorkKey.Id).id;val old=assignment(id)
            val next=(c.records[result]?.payload as? WMechanicalAssignmentsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
            MechanicalCommandOperation.Bind(id,old.generation,next.binding ?: throw WorkFault(WorkProblem.INVALID_BINDING))
        }
        "prepare_mechanical_effect" -> {
            workRequire(result.family==WorkFamily.NATIVE_INTENTS,WorkProblem.INVALID_REFERENCE)
            val effect=(c.records[result]?.payload as? WMechanicalOperationsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
            val a=assignment(effect.assignment.assignmentId)
            val bodies=(c.bodies.keys-h.bodies.keys).map { digest ->
                val body=c.bodies.getValue(digest);MechanicalAttachment(digest,body.text())
            }
            MechanicalCommandOperation.Prepare(effect.assignment.assignmentId,a.generation,result.key as WorkKey.Id,effect,bodies)
        }
        "observe_mechanical_effect" -> {
            workRequire(result.family==WorkFamily.NATIVE_INTENTS,WorkProblem.INVALID_REFERENCE)
            val old=(h.records[result]?.payload as? WMechanicalOperationsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
            val next=(c.records[result]?.payload as? WMechanicalOperationsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
            val a=assignment(old.assignment.assignmentId)
            MechanicalCommandOperation.Observe(old.assignment.assignmentId,a.generation,result.key as WorkKey.Id,old.generation,next)
        }
        "record_mechanical_retrospective" -> {
            workRequire(result.family==WorkFamily.RETROSPECTIVES,WorkProblem.INVALID_REFERENCE)
            val data=(c.records[result]?.payload as? WRetrospectivesPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
            MechanicalCommandOperation.Retrospective(data.assignment.assignmentId,assignment(data.assignment.assignmentId).generation,result.key as WorkKey.Id,data)
        }
        "submit_mechanical_result" -> {
            workRequire(result.family==WorkFamily.RESULTS,WorkProblem.INVALID_REFERENCE)
            val data=(c.records[result]?.payload as? WMechanicalResultsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
            val a=assignment(data.assignment.assignmentId)
            val effect=(h.record(WorkFamily.NATIVE_INTENTS,a.bindingData.producer.operation).payload as? WMechanicalOperationsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
            MechanicalCommandOperation.Submit(data.assignment.assignmentId,a.generation,effect.generation,MechanicalResultDescriptor(result.key as WorkKey.Id,data))
        }
        else -> throw WorkFault(WorkProblem.INVALID_SUBJECT)
    }
    val codec=MechanicalCommandCodec()
    val command=codec.decode(codec.encode(MechanicalCommand(requestKey,project,v)))
    validateMechanicalTransition(h,c,command,actor)
    return command
}

/** Candidate record2 shapes only. This codec has no authenticated command, writer or native effect. */
internal class MechanicalWorkCodec {
    private val execution=ExecutionDescriptorCodec()
    private fun key(f:WorkFamily,v:JsonValue)=identityCodec.decodeKey(f,StrictJson.encode(v))
    private fun ref(v:JsonValue,f:WorkFamily)=identityCodec.decodeReference(StrictJson.encode(v)).also { shape(it.identity.family==f) }
    private fun key(v:WorkKey)=StrictJson.decode(identityCodec.encodeKey(v))
    private fun ref(v:WorkReference)=StrictJson.decode(identityCodec.encodeReference(v))
    private fun body(v:WorkBodyReference)=StrictJson.decode(identityCodec.encodeBodyReference(v))
    fun decodeBinding(v:JsonValue,project:UUID):MechanicalBinding {
        val f=v.fields("kind","task","manifest","profile","producer","resources","authorship")
        shape(f.getValue("kind").string()=="mechanical-v1")
        val producer=execution.decodeProducer(StrictJson.encode(f.getValue("producer")),project) as? MechanicalProducerDescriptor ?: refuse(CodecProblem.INVALID_SHAPE)
        val authorship=execution.decodeAuthorship(StrictJson.encode(f.getValue("authorship")),project)
        shape(authorship.sourceResults.isNotEmpty())
        return MechanicalBinding(key(WorkFamily.TASK_REVISIONS,f.getValue("task")) as WorkKey.TaskRevision,
            ref(f.getValue("manifest"),WorkFamily.INPUT_MANIFESTS),key(WorkFamily.PROFILES,f.getValue("profile")) as WorkKey.Profile,producer,
            workList(f.getValue("resources"),1,256,true) { key(WorkFamily.CLAIMS,it) as WorkKey.Resource },authorship)
    }
    fun encodeBinding(v:MechanicalBinding):JsonValue=obj("kind" to str("mechanical-v1"),"task" to key(v.task),"manifest" to ref(v.manifest),
        "profile" to key(v.profile),"producer" to StrictJson.decode(execution.encodeProducer(v.producer,v.task.projectId)),
        "resources" to workArray(v.resources,1,256,true,::key),"authorship" to StrictJson.decode(execution.encodeAuthorship(v.authorship,v.task.projectId)))
    fun decode(family:WorkFamily,v:JsonValue,project:UUID):WorkPayload=when(family) {
        WorkFamily.ASSIGNMENT_BINDINGS -> WMechanicalBindingsPayload(decodeBinding(v,project))
        WorkFamily.ASSIGNMENTS -> {
            val f=v.fields("kind","generation","bindingData","binding","state")
            shape(f.getValue("kind").string()=="mechanical-v1")
            val generation=f.getValue("generation").long().also { shape(it>=0) }
            val state=f.getValue("state").string().also { shape(it in setOf("reserved","bound","active","unknown","completed")) }
            val binding=f.getValue("binding").let { if(it===JNull)null else ref(it,WorkFamily.ASSIGNMENT_BINDINGS) }
            shape((state=="reserved")== (binding==null))
            WMechanicalAssignmentsPayload(MechanicalAssignment(generation,decodeBinding(f.getValue("bindingData"),project),binding,state))
        }
        WorkFamily.NATIVE_INTENTS -> {
            val f=v.fields("kind","generation","assignment","binding","patch","witnesses","beforeDigest","afterDigest","outcome","observedDigest","writerCompleted","evidence","observations")
            shape(f.getValue("kind").string()=="mechanical-v1")
            val generation=f.getValue("generation").long().also { shape(it>=0) }
            val outcome=f.getValue("outcome").string().also { shape(it in setOf("pending","unknown","applied","not-applied","partial")) }
            val before=digest(f.getValue("beforeDigest"));val after=digest(f.getValue("afterDigest"));shape(before!=after)
            val observed=f.getValue("observedDigest").let { if(it===JNull)null else digest(it) }
            val completed=workBoolean(f.getValue("writerCompleted"))
            shape(completed==(outcome in setOf("applied","not-applied","partial")))
            if(outcome=="pending")shape(observed==null)
            if(outcome=="applied")shape(observed==after)
            if(outcome=="not-applied")shape(observed==before)
            if(outcome=="partial")shape(observed!=null && observed!=before && observed!=after)
            val patch=identityCodec.decodeBodyReference(StrictJson.encode(f.getValue("patch")))
            val witnesses=identityCodec.decodeBodyReference(StrictJson.encode(f.getValue("witnesses")))
            shape(patch.byteLength>0 && witnesses.byteLength>0)
            val assignment=decodeWAssignmentSubject(f.getValue("assignment"));shape(assignment.generation==0L)
            WMechanicalOperationsPayload(MechanicalOperation(generation,assignment,ref(f.getValue("binding"),WorkFamily.ASSIGNMENT_BINDINGS),patch,witnesses,before,after,outcome,observed,completed,
                workList(f.getValue("evidence"),0,256,true) { ref(it,WorkFamily.EVIDENCE) },workList(f.getValue("observations"),0,256,false,::decodeWSource)))
        }
        else -> refuse(CodecProblem.INVALID_SHAPE)
    }
    fun encode(p:WorkPayload):JsonValue=when(p) {
        is WMechanicalBindingsPayload -> encodeBinding(p.value)
        is WMechanicalAssignmentsPayload -> p.value.let { obj("kind" to str("mechanical-v1"),"generation" to JNumber(it.generation),
            "bindingData" to encodeBinding(it.bindingData),"binding" to (it.binding?.let(::ref) ?: JNull),"state" to str(it.state)) }
        is WMechanicalOperationsPayload -> p.value.let { obj("kind" to str("mechanical-v1"),"generation" to JNumber(it.generation),
            "assignment" to encodeWAssignmentSubject(it.assignment),"binding" to ref(it.binding),"patch" to body(it.patch),"witnesses" to body(it.witnesses),
            "beforeDigest" to digest(it.beforeDigest),"afterDigest" to digest(it.afterDigest),"outcome" to str(it.outcome),
            "observedDigest" to (it.observedDigest?.let(::digest) ?: JNull),"writerCompleted" to JBoolean(it.writerCompleted),
            "evidence" to workArray(it.evidence,0,256,true,::ref),"observations" to workArray(it.observations,0,256,false,::encodeWSource)) }
        else -> refuse(CodecProblem.INVALID_SHAPE)
    }
}

/** Structural history invariants only. Actual effect/writer facts still require authenticated
 * command admission; a consistent uploaded record is never authority to apply/release claims. */
internal fun validateMechanicalEdge(old:WorkPayload,next:WorkPayload) {
    if(old is WMechanicalAssignmentsPayload || next is WMechanicalAssignmentsPayload) {
        workRequire(old is WMechanicalAssignmentsPayload && next is WMechanicalAssignmentsPayload,WorkProblem.INVALID_TRANSITION)
        old as WMechanicalAssignmentsPayload;next as WMechanicalAssignmentsPayload
        val a=old.value;val b=next.value
        workRequire(b.generation==nextGeneration(a.generation),WorkProblem.STALE_GENERATION)
        workRequire(a.bindingData==b.bindingData && (a.binding==null || a.binding==b.binding),WorkProblem.INVALID_BINDING)
        val allowed=when(a.state) {
            "reserved" -> setOf("bound")
            "bound" -> setOf("active")
            "active" -> setOf("unknown","completed")
            "unknown" -> setOf("unknown","completed")
            else -> emptySet()
        }
        workRequire(b.state in allowed,WorkProblem.INVALID_TRANSITION)
    }
    if(old is WMechanicalOperationsPayload || next is WMechanicalOperationsPayload) {
        workRequire(old is WMechanicalOperationsPayload && next is WMechanicalOperationsPayload,WorkProblem.INVALID_TRANSITION)
        old as WMechanicalOperationsPayload;next as WMechanicalOperationsPayload
        val a=old.value;val b=next.value
        workRequire(b.generation==nextGeneration(a.generation),WorkProblem.STALE_GENERATION)
        workRequire(a.copy(generation=b.generation,outcome=b.outcome,observedDigest=b.observedDigest,writerCompleted=b.writerCompleted,
            evidence=b.evidence,observations=b.observations)==b,WorkProblem.INVALID_SUBJECT)
        workRequire(!a.writerCompleted && b.outcome!="pending" && b.observations.containsAll(a.observations) && b.evidence.containsAll(a.evidence),WorkProblem.INVALID_TRANSITION)
    }
}

/** Candidate mechanical row edge, suitable for later historical audit integration. Callers must
 * independently validate receipt/actor/current authority, both complete snapshots, exact changed
 * paths, and actual native observations. Absence or matching snapshots alone are not that proof. */
internal fun validateMechanicalRecordEdge(before:WorkRecord?,after:WorkRecord?) {
    val next=after ?: throw WorkFault(WorkProblem.INVALID_TRANSITION)
    val p=next.payload
    workRequire(p is WMechanicalAssignmentsPayload || p is WMechanicalBindingsPayload ||
        p is WMechanicalOperationsPayload || p is WMechanicalProfilesPayload || p is WMechanicalResultsPayload,WorkProblem.INVALID_SUBJECT)
    if(before==null) {
        when(p) {
            is WMechanicalAssignmentsPayload -> workRequire(p.value.generation==0L && p.value.state=="reserved" && p.value.binding==null,WorkProblem.INVALID_TRANSITION)
            is WMechanicalOperationsPayload -> workRequire(p.value.generation==0L && p.value.outcome=="pending" && !p.value.writerCompleted,WorkProblem.INVALID_TRANSITION)
            else -> Unit
        }
        return
    }
    workRequire(before.identity==next.identity,WorkProblem.INVALID_SUBJECT)
    if(!next.identity.family.mutable)throw WorkFault(WorkProblem.INVALID_TRANSITION)
    validateMechanicalEdge(before.payload,p)
}

internal sealed interface ResultAuthorship
internal data class LegacyProducerAuthorship(val principalId:UUID):ResultAuthorship
/** Complete declaration, not mechanical proof that every semantic contributor was discovered. */
internal data class DeclaredResultAuthorship(val declaredBy:UUID,val authors:List<UUID>,val sourceResults:List<WorkReference>):ResultAuthorship

/** A new mechanical result cannot use a legacy producer-only view as complete attribution.
 * knownAuthors must come from actual selected predecessors, not an applier-supplied empty list. */
internal fun requireDeclaredAuthorship(value:DeclaredResultAuthorship,authenticatedSubmitter:UUID,knownAuthors:Set<UUID>) {
    workRequire(value.declaredBy==authenticatedSubmitter && authenticatedSubmitter in value.authors &&
        value.authors.toSet().containsAll(knownAuthors),WorkProblem.INVALID_SUBJECT)
}

/** Structural NON-author candidate only. Does not check rights, fresh context, immutable snapshot,
 * required gates or evidence applicability and therefore cannot itself grant review/acceptance. */
internal fun nonAuthorCandidate(principal:UUID,execution:UUID,producer:ExecutionProducerDescriptor,
    authorship:ResultAuthorship,requireComplete:Boolean,knownAuthors:Set<UUID>):Boolean {
    if(execution==producer.executionId)return false
    if(producer is NativeProducerDescriptor && principal==producer.value.principalId)return false
    return when(authorship) {
        is LegacyProducerAuthorship -> producer is NativeProducerDescriptor && !requireComplete && principal!=authorship.principalId && principal !in knownAuthors
        is DeclaredResultAuthorship -> authorship.authors.isNotEmpty() && authorship.authors.size<=256 &&
            authorship.authors.distinct().size==authorship.authors.size && authorship.declaredBy in authorship.authors &&
            authorship.authors.containsAll(knownAuthors) && principal !in authorship.authors
    }
}

/** Stage codec only: record2 is not silently admitted by command3/record1 or a legacy writer.
 * Selecting a new store/writer profile, reducer/auth/claim/result/history integration remains held. */
internal class ExecutionDescriptorCodec {
    private val legacy=WorkCodec()
    private fun limits(historical:Boolean)=if(historical)
        JsonLimits(bytes=4*1024*1024,stringBytes=262144,depth=128,collection=4096,tokens=500000)
        else JsonLimits(bytes=262144,stringBytes=65536,depth=32,collection=1024,tokens=100000)
    fun decodeProfile(bytes:ByteArray,historical:Boolean=false):ExecutionProfileDescriptor {
        val f=StrictJson.decode(bytes,limits(historical),unavailable=historical).fields("formatVersion","family","key","payload")
        if(f.getValue("formatVersion").long()==1L) {
            val row=legacy.decodeRecord(bytes,historical)
            shape(row.identity.family==WorkFamily.PROFILES)
            return NativeProfileDescriptor(row.identity.key as WorkKey.Profile,(row.payload as WProfilesPayload).value)
        }
        shape(f.getValue("formatVersion").long()==2L && f.getValue("family").string()=="profiles")
        val key=identityCodec.decodeKey(WorkFamily.PROFILES,StrictJson.encode(f.getValue("key"))) as WorkKey.Profile
        val p=decodeMechanicalProfile(f.getValue("payload"))
        sameProject(encodeMechanicalProfile(p),key.projectId)
        return MechanicalProfileDescriptor(key,p)
    }
    fun encodeProfile(value:ExecutionProfileDescriptor,historical:Boolean=false):ByteArray {
        if(value is NativeProfileDescriptor)return legacy.encodeRecord(WorkRecord(WorkRecordIdentity(WorkFamily.PROFILES,value.key),WProfilesPayload(value.value)),historical)
        value as MechanicalProfileDescriptor
        val bytes=StrictJson.encode(obj("formatVersion" to num(2),"family" to str("profiles"),
            "key" to StrictJson.decode(identityCodec.encodeKey(value.key)),"payload" to encodeMechanicalProfile(value.value)),
            if(historical)4*1024*1024 else 262144)
        decodeProfile(bytes,historical);return bytes
    }
    private fun decodeMechanicalProfile(v:JsonValue):MechanicalProfile {
        val f=v.fields("kind","nativeProfileId","qualificationEvidence","checkProfileId","maximumPatchBytes","maximumFileBytes",
            "maximumCheckOutputBytes","maximumCheckMillis","executionAssurance","permissions","platform","qualification")
        shape(f.getValue("kind").string()=="mechanical-v1")
        fun bounded(name:String,maximum:Long)=f.getValue(name).long().also { shape(it in 1..maximum) }
        val assurance=f.getValue("executionAssurance").string().also { shape(it=="E-COOP-1") }
        val platform=f.getValue("platform").string().also { shape(it in setOf("linux","windows","macos")) }
        val qualification=f.getValue("qualification").string().also { shape(it in setOf("unqualified","qualified")) }
        return MechanicalProfile(workText(f.getValue("nativeProfileId"),1,128),workList(f.getValue("qualificationEvidence"),0,256,false,::decodeWSource),
            workText(f.getValue("checkProfileId"),1,128),bounded("maximumPatchBytes",1024*1024),bounded("maximumFileBytes",8*1024*1024),
            bounded("maximumCheckOutputBytes",1024*1024),bounded("maximumCheckMillis",3600000),assurance,
            workList(f.getValue("permissions"),0,256,false,::decodeWProfilePermissionsItem),platform,qualification)
    }
    internal fun encodeMechanicalProfile(p:MechanicalProfile):JsonValue = obj("kind" to str("mechanical-v1"),
        "nativeProfileId" to str(p.nativeProfileId),"qualificationEvidence" to workArray(p.qualificationEvidence,0,256,false,::encodeWSource),
        "checkProfileId" to str(p.checkProfileId),"maximumPatchBytes" to JNumber(p.maximumPatchBytes),"maximumFileBytes" to JNumber(p.maximumFileBytes),
        "maximumCheckOutputBytes" to JNumber(p.maximumCheckOutputBytes),"maximumCheckMillis" to JNumber(p.maximumCheckMillis),
        "executionAssurance" to str(p.executionAssurance),"permissions" to workArray(p.permissions,0,256,false,::encodeWProfilePermissionsItem),
        "platform" to str(p.platform),"qualification" to str(p.qualification))

    fun decodeProducer(bytes:ByteArray,project:UUID):ExecutionProducerDescriptor {
        val value=StrictJson.decode(bytes,JsonLimits(bytes=16384,stringBytes=8192,depth=16,collection=64,tokens=2048))
        if(value is JObject && "formatVersion" !in value.entries)return NativeProducerDescriptor(decodeWExecution(value))
        val f=value.fields("formatVersion","kind","executionId","controllerId","nativeHostId","workspaceId","operation")
        shape(f.getValue("formatVersion").long()==2L && f.getValue("kind").string()=="mechanical-v1")
        val operation=identityCodec.decodeKey(WorkFamily.NATIVE_INTENTS,StrictJson.encode(f.getValue("operation"))) as WorkKey.Id
        shape(operation.projectId==project)
        return MechanicalProducerDescriptor(uuid(f.getValue("executionId")),uuid(f.getValue("controllerId")),uuid(f.getValue("nativeHostId")),uuid(f.getValue("workspaceId")),operation)
    }
    fun encodeProducer(value:ExecutionProducerDescriptor,project:UUID):ByteArray {
        val json=when(value) {
            is NativeProducerDescriptor -> encodeWExecution(value.value)
            is MechanicalProducerDescriptor -> obj("formatVersion" to num(2),"kind" to str("mechanical-v1"),"executionId" to uuid(value.executionId),
                "controllerId" to uuid(value.controllerId),"nativeHostId" to uuid(value.nativeHostId),"workspaceId" to uuid(value.workspaceId),
                "operation" to StrictJson.decode(identityCodec.encodeKey(value.operation)))
        }
        val bytes=StrictJson.encode(json,16384);decodeProducer(bytes,project);return bytes
    }
    fun decodeAuthorship(bytes:ByteArray,project:UUID):DeclaredResultAuthorship {
        val f=StrictJson.decode(bytes,JsonLimits(bytes=262144,stringBytes=65536,depth=32,collection=1024,tokens=100000))
            .fields("formatVersion","kind","declaredBy","authors","sourceResults")
        shape(f.getValue("formatVersion").long()==1L && f.getValue("kind").string()=="declared-authors")
        val author=uuid(f.getValue("declaredBy"))
        val authors=workList<UUID>(f.getValue("authors"),1,256,true) { uuid(it) }
        shape(author in authors)
        val sources=workList(f.getValue("sourceResults"),0,256,true) {
            identityCodec.decodeReference(StrictJson.encode(it)).also { ref ->shape(ref.identity.family==WorkFamily.RESULTS && ref.identity.key.projectId==project) }
        }
        return DeclaredResultAuthorship(author,authors,sources)
    }
    fun encodeAuthorship(value:DeclaredResultAuthorship,project:UUID):ByteArray {
        val bytes=StrictJson.encode(obj("formatVersion" to num(1),"kind" to str("declared-authors"),"declaredBy" to uuid(value.declaredBy),
            "authors" to workArray(value.authors,1,256,true,::uuid),"sourceResults" to workArray(value.sourceResults,0,256,true) {
                StrictJson.decode(identityCodec.encodeReference(it)) }),262144)
        decodeAuthorship(bytes,project);return bytes
    }
}
