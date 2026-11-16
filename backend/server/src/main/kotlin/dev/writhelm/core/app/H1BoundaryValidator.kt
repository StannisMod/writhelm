// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.*
import dev.writhelm.core.auth2.WorkOperation as AuthorityOperation

/** Required original-H -> actual-C structural/provenance validator. Prepared for the parent's direct
 * H1 boundary callback, NOT wired to the old two-argument port through an empty validator.
 * No current/latest substitution, archived commands, raw historical secret or adapter re-execution. */
internal class H1BoundaryValidator(private val cache: H1SnapshotCache) {
    fun validate(parentH: StoreSnapshot,candidateC: StoreSnapshot,receipt: Receipt,normal: H1JointCandidate?=null) {
        integrity(receipt.validatedAt==parentH.oid && receipt.journalVersion==3)
        val before=cache.load(parentH)
        integrity(receipt.storeId==before.storeId)
        val credential=before.authority.retainedCredential(receipt.authenticatedByCredentialId,receipt.identity.projectId) ?: fail(StoreProblem.INTEGRITY)
        integrity(credential.credential.principalId==receipt.identity.principalId)
        val actual=(parentH.entries.keys+candidateC.entries.keys).filter { parentH.entries[it]!=candidateC.entries[it] }.toSet()
        integrity(actual==receipt.writes.map { it.path }.toSet()+receipt.path)
        receipt.writes.forEach { w ->
            integrity(parentH.file(w.path)?.let(::sha)==w.beforeDigest && candidateC.file(w.path)?.let(::sha)==w.afterDigest)
        }
        val after=if(normal!=null) {
            integrity(normal.before.basis==before.basis && normal.before.storeId==before.storeId && normal.evaluation.changes.map { it.key.path }.toSet()==receipt.writes.map { it.path }.toSet())
            integrity(StrictJson.encode(normal.evaluation.outcome.json()).contentEquals(StrictJson.encode(receipt.outcome.json())))
            var bytes=before.entryBytes
            normal.evaluation.changes.forEach { change ->
                val expected=change.bytes();val observed=candidateC.file(change.key.path)
                integrity(if(expected==null)observed==null else observed!=null && expected.contentEquals(observed))
                bytes-=parentH.file(change.key.path)?.size ?: 0;bytes+=expected?.size ?: 0
            }
            bytes+=JournalCodec.encode(receipt).size
            cache.candidate(normal,candidateC.oid,bytes,receipt)
        } else cache.load(candidateC) // bounded historical cold restore, never normal-body rescanning
        validateAuthPreservation(before,after,receipt)
        validateRecordedActors(after,receipt)
        if(receipt.identity.operation=="bind_dispatch_attempt" && receipt.outcome is LogicalOutcome.Success)
            validateDispatchAttempt(parentH,before,after,receipt)
        if(receipt.identity.operation in H1Catalog.workOperations)validateWork(before,after,credential,receipt)
        else validateAuthority(before,after,credential,receipt)
        cache.remember(after)
    }
    private fun validateDispatchAttempt(parent:StoreSnapshot,h:H1BoundState,c:H1BoundState,r:Receipt) {
        val result=H1Catalog.result(r.identity.operation,(r.outcome as LogicalOutcome.Success).result)
        val key=identityCodec.decodeKey(WorkFamily.DISPATCH_ATTEMPTS,StrictJson.encode(result.key)) as WorkKey.Id
        val after=c.work.getValue(r.identity.projectId)
        val attempt=(after.record(WorkFamily.DISPATCH_ATTEMPTS,key).payload as? WDispatchAttemptPayload)?.value ?: fail(StoreProblem.INTEGRITY)
        val command=DispatchAttemptCommand(r.identity.requestKey,attempt)
        integrity(DispatchAttemptCommandCodec().fingerprint(command).hex==r.fingerprint)
        val reserve=attempt.reservation
        val ctl=(reserve.operation as CReserveAssignmentOperation).value.intendedExecution.controllerId
        val originalCall=WorkInvocation(r.identity.projectId,AuthorityOperation.RESERVE_ASSIGNMENT,reserve.requestKey,null,ctl,null)
        val original=Auth2Policy(h.authority,h.projection).authorizeHistoricalWork(r.authenticatedByCredentialId,originalCall) ?: fail(StoreProblem.INTEGRITY)
        integrity(original.identity.principalId==r.identity.principalId && original.mode==AdmissionMode.EVALUATE)
        val reservationIdentity=dev.writhelm.core.store.CommandIdentity(r.identity.principalId,r.identity.projectId,"reserve_assignment",reserve.requestKey)
        integrity(parent.file(reservationIdentity.path)==null && r.writes.size==1 && r.writes.single().path==result.path)
        attempt.previous?.let { ref ->
            val previous=(h.work.getValue(r.identity.projectId).resolve(ref).payload as? WDispatchAttemptPayload)?.value ?: fail(StoreProblem.INTEGRITY)
            integrity(definitiveReservationRefusal(parent,h.storeId,r.identity.principalId,previous.reservation))
        }
        val actor=WorkExecutionInput(r.identity.principalId,r.authenticatedByCredentialId,ctl,null,null)
        val expected=try { bindDispatchAttempt(h.work.getValue(r.identity.projectId),actor,attempt) } catch(_:WorkFault) { fail(StoreProblem.INTEGRITY) }
        integrity(expected.result==WorkRecordIdentity(WorkFamily.DISPATCH_ATTEMPTS,key) && expected.after.records==after.records && expected.after.bodies==after.bodies)
    }
    private fun validateRecordedActors(c: H1BoundState,r: Receipt) {
        val actor=r.identity.principalId
        val core=c.core.snapshot(8192)
        r.writes.filter { it.afterDigest!=null }.forEach { w ->
            if(w.record.namespace=="core")when(w.record.family) {
                "proposals" -> integrity(core.proposals[revisionKey(w.record.key)]?.principalId==actor)
                "requests" -> integrity(core.requests[uuid(w.record.key)]?.proposedBy==actor)
                "resolutions" -> integrity(core.resolutions[uuid(w.record.key)]?.actor?.principalId==actor)
                "assessments" -> integrity(core.assessments[uuid(w.record.key)]?.actor?.principalId==actor)
                "manifests" -> integrity(core.manifests[uuid(w.record.key)]?.createdBy==actor)
                else -> Unit
            }
            if(w.record.namespace=="work" && w.record.family!="bodies") {
                val family=H1Catalog.family(w.record.family)
                val key=identityCodec.decodeKey(family,StrictJson.encode(w.record.key))
                val record=c.work.getValue(key.projectId).record(family,key)
                when(val p=record.payload) {
                    is WDispositionsPayload -> integrity(p.value.recordedBy==actor)
                    is WAcceptancesPayload -> integrity(p.value.acceptedBy==actor)
                    is WIntegrationsPayload -> integrity(p.value.integratedBy==actor)
                    else -> Unit
                }
            }
        }
    }
    private fun validateAuthPreservation(h: H1BoundState,c: H1BoundState,r: Receipt) {
        val a=h.authority;val b=c.authority
        if(r.identity.operation!="register_project")integrity(a.projects==b.projects)
        else integrity(b.projects.entries.containsAll(a.projects.entries))
        integrity(b.principals.containsAll(a.principals))
        if(r.identity.operation!="register_controller")integrity(a.controllers==b.controllers)
        else integrity(b.controllers.entries.containsAll(a.controllers.entries))
        if(r.identity.operation !in setOf("register_project","register_controller","register_delegation"))integrity(a.principals==b.principals && a.guards.keys==b.guards.keys && a.delegations.keys==b.delegations.keys)
        a.credentials.forEach { (id,old) ->
            val next=b.credentials[id] ?: fail(StoreProblem.INTEGRITY)
            integrity(old.scope==next.scope)
            val x=old.credential;val y=next.credential
            integrity(x.storeId==y.storeId && x.projectId==y.projectId && x.principalId==y.principalId && x.verifierDigest==y.verifierDigest)
            if(x!=y) {
                integrity(x.replacedBy==null && x.generation<Long.MAX_VALUE && y.generation==x.generation+1 && y.status==CredentialStatus.REVOKED)
                y.replacedBy?.let { successor ->
                    val key=b.credentials[successor] ?: fail(StoreProblem.INTEGRITY)
                    integrity(successor !in a.credentials && key.scope==old.scope && key.credential.principalId==x.principalId &&
                        key.credential.projectId==x.projectId && key.credential.storeId==x.storeId && key.credential.status==CredentialStatus.ACTIVE && key.credential.generation==0L)
                }
            }
        }
        a.delegations.forEach { (id,old) ->
            val next=b.delegations[id] ?: fail(StoreProblem.INTEGRITY)
            integrity(old.binding==next.binding)
            if(old!=next)integrity(old.status==CredentialStatus.ACTIVE && next.status==CredentialStatus.REVOKED && old.generation<Long.MAX_VALUE && next.generation==old.generation+1)
        }
    }
    private fun validateAuthority(h: H1BoundState,c: H1BoundState,actor: ScopedCredential,r: Receipt) {
        val project=r.identity.projectId;val op=r.identity.operation;val a=h.authority
        if(op in coreOperations || op in authOperations) {
            integrity(actor.scope !is CredentialScope.Assignment)
            val required=requiredCapability(op) ?: fail(StoreProblem.INTEGRITY)
            integrity(a.allows(actor,project,required))
            if(required in setOf(dev.writhelm.core.workflow.Capability.MANAGE,dev.writhelm.core.workflow.Capability.APPROVE))integrity(a.projects[project]?.ownerPrincipalId==actor.credential.principalId)
            return
        }
        val success=r.outcome as? LogicalOutcome.Success
        if(op=="register_project") {
            integrity(actor.scope==CredentialScope.Project && a.owner(actor,project))
            if(success!=null) {
                val target=uuid(success.result);val principal=actor.credential.principalId
                val enrollment=Enrollment(principal,target)
                integrity(target !in a.projects && c.authority.projects.keys==a.projects.keys+target && c.authority.principals==a.principals)
                integrity(c.authority.projects[target]?.ownerPrincipalId==principal && c.authority.guards.keys==a.guards.keys+enrollment && c.authority.guards[enrollment]==0L)
                val keys=c.authority.credentials.filterKeys { it !in a.credentials }
                integrity(keys.size==1)
                val key=keys.values.single()
                integrity(key.scope==CredentialScope.Project && key.credential.projectId==target && key.credential.principalId==principal &&
                    key.credential.status==CredentialStatus.ACTIVE && key.credential.generation==0L && key.credential.replacedBy==null)
                integrity(Auth3CommandCodec().fingerprint(Auth3Command(r.identity.requestKey,project,
                    Auth3Payload.RegisterProject(target,key.credential.id,key.credential.verifierDigest))).hex==r.fingerprint)
                integrity(c.authority.policy.snapshotGrants().single { it.principalId==principal && it.projectId==target }.capabilities==dev.writhelm.core.workflow.Capability.entries.toSet())
                integrity(c.authority.workGrants[enrollment]?.permissions==WorkPermission.entries.toSet() && c.authority.workGrants[enrollment]?.generation==0L)
                integrity(c.authority.controllers==a.controllers && c.authority.delegations==a.delegations)
                integrity(c.work.getValue(target).records.isEmpty())
            }
            return
        }
        if(op=="register_controller") {
            integrity(actor.scope==CredentialScope.Project && a.owner(actor,project))
            if(success!=null) {
                val binding=c.authority.controllers[uuid(success.result)] ?: fail(StoreProblem.INTEGRITY)
                val principal=binding.principalId;val enrollment=dev.writhelm.core.auth.Enrollment(principal,project)
                integrity(binding.controllerId !in a.controllers && principal !in a.principals && binding.projectId==project)
                integrity(c.authority.controllers.keys==a.controllers.keys+binding.controllerId && c.authority.principals==a.principals+principal)
                integrity(c.authority.guards.keys==a.guards.keys+enrollment && c.authority.guards[enrollment]==0L)
                val keys=c.authority.credentials.filterKeys { it !in a.credentials }
                integrity(keys.size==1 && keys.values.single().let { it.scope==CredentialScope.Controller(binding.controllerId) &&
                    it.credential.principalId==principal && it.credential.projectId==project && it.credential.status==CredentialStatus.ACTIVE && it.credential.generation==0L })
                val key=keys.values.single().credential
                integrity(Auth3CommandCodec().fingerprint(Auth3Command(r.identity.requestKey,project,
                    Auth3Payload.RegisterController(binding.controllerId,principal,key.id,key.verifierDigest))).hex==r.fingerprint)
                integrity(c.authority.policy.snapshotGrants().single { it.principalId==principal && it.projectId==project }.capabilities==
                    setOf(dev.writhelm.core.workflow.Capability.READ,dev.writhelm.core.workflow.Capability.PLAN,dev.writhelm.core.workflow.Capability.PROPOSE))
                integrity(c.authority.workGrants[enrollment]?.permissions==WorkPermission.entries.toSet() && c.authority.workGrants[enrollment]?.generation==0L)
                integrity(c.authority.delegations==a.delegations)
            }
            return
        }
        if(op=="replace_work_grants") { integrity(a.owner(actor,project));return }
        if(op=="register_delegation") {
            integrity(a.workAllows(actor,project,WorkPermission.TASK_DISPATCH))
            val ctl=a.controller(actor,project) ?: fail(StoreProblem.INTEGRITY)
            if(success!=null) {
                val d=c.authority.delegations[uuid(success.result)] ?: fail(StoreProblem.INTEGRITY)
                integrity(d.binding.controllerId==ctl && d.binding.projectId==project && d.id !in a.delegations)
                val reservation=h.projection.assignment(d.binding) ?: fail(StoreProblem.INTEGRITY)
                integrity(reservation.phase==AssignmentPhase.RESERVED && reservation.delegationId==null && reservation.independent())
                val enrolled=c.projection.assignment(d.binding) ?: fail(StoreProblem.INTEGRITY)
                integrity(enrolled.assignmentGeneration==Math.addExact(reservation.assignmentGeneration,1) && enrolled.delegationId==d.id)
            }
            return
        }
        if(success==null) { // No effect/subject archive is invented for retained domain rejection.
            integrity(a.owner(actor,project) && op=="revoke_delegation" || a.controller(actor,project)!=null && a.workAllows(actor,project,WorkPermission.TASK_DISPATCH))
            return
        }
        val key=if(op=="rotate_delegated_credential")uuid(success.result.fields("credentialId","replacedCredentialId").getValue("replacedCredentialId")) else uuid(success.result)
        val delegation=if(op=="revoke_delegation")key else (a.credentials[key]?.scope as? CredentialScope.Assignment)?.delegationId ?: fail(StoreProblem.INTEGRITY)
        integrity(op=="revoke_delegation" && a.owner(actor,project) || a.ownsDelegation(actor,project,delegation,if(op=="revoke_delegation")null else key))
    }
    private fun validateWork(h: H1BoundState,c: H1BoundState,actor: ScopedCredential,r: Receipt) {
        val operation=AuthorityOperation.entries.singleOrNull { it.tag==r.identity.operation } ?: fail(StoreProblem.INTEGRITY)
        val delegated=(actor.scope as? CredentialScope.Assignment)?.let { h.authority.delegations[it.delegationId]?.binding ?: fail(StoreProblem.INTEGRITY) }
        val success=r.outcome as? LogicalOutcome.Success
        val record=success?.let { o ->
            val result=H1Catalog.result(o.operation,o.result)
            val family=H1Catalog.family(result.family)
            val key=identityCodec.decodeKey(family,StrictJson.encode(result.key))
            c.work.getValue(r.identity.projectId).record(family,key)
        }
        if(operation==AuthorityOperation.RECORD_DISPATCH_PLAN && record!=null) {
            integrity(r.writes.size==1 && r.writes.single().path==identityCodec.recordPath(record.identity))
            H1JointReduction().validateDispatchPlanEdge(h,c,r.identity.principalId,r.identity.requestKey,r.authenticatedByCredentialId,
                Digest.parse(r.fingerprint),record.identity.key as WorkKey.Id)
        }
        if(operation==AuthorityOperation.MATERIALIZE_DISPATCH_DEPENDENCY && record!=null) {
            val command=(record.payload as? WDispatchMaterializationPayload)?.value ?: fail(StoreProblem.INTEGRITY)
            integrity(command.requestKey==r.identity.requestKey && DispatchMaterializationCommandCodec().fingerprint(command).hex==r.fingerprint)
            val paths=setOf(record.identity,WorkRecordIdentity(WorkFamily.TASK_REVISIONS,command.newTask),
                WorkRecordIdentity(WorkFamily.VERIFICATION_PLANS,command.verificationPlan),WorkRecordIdentity(WorkFamily.INPUT_MANIFESTS,command.manifest))
                .map { identityCodec.recordPath(it) }.toSet()
            integrity(r.writes.map { it.path }.toSet()==paths && r.writes.all { it.beforeDigest==null && it.afterDigest!=null })
            try { validateDispatchMaterializationEdge(h.work.getValue(r.identity.projectId),c.work.getValue(r.identity.projectId),command) }
            catch(_:WorkFault) { fail(StoreProblem.INTEGRITY) }
            val task=h.work.getValue(r.identity.projectId).task(command.task)
            integrity(task.approvedDesign.all { h.core.validationDecisionApproved(it) })
        }
        if(operation==AuthorityOperation.PIN_INPUT_MANIFEST && record!=null && h.work.getValue(r.identity.projectId).modelVersion==4) {
            val manifest=(record.payload as? WInputManifestsPayload)?.value ?: fail(StoreProblem.INTEGRITY)
            if(manifest.knownGaps.isEmpty())integrity(h.work.getValue(r.identity.projectId).records.values.none {
                (it.payload as? WDispatchMaterializationPayload)?.value?.newTask==manifest.task
            })
        }
        // Automatic declared-input checks are reconstructed from the original materialization.
        // Legacy assessor assertions do not become successful service publications.
        if(operation==AuthorityOperation.CLOSE_DISPATCH_INPUTS) {
            if(record!=null) {
                val key=record.identity.key as WorkKey.Id
                integrity(key.id==r.identity.requestKey && r.writes.map { it.path }.toSet()==setOf(
                    identityCodec.recordPath(record.identity),identityCodec.recordPath(WorkRecordIdentity(WorkFamily.INPUT_MANIFESTS,
                        (record.payload as? WDispatchClosurePayload)?.value?.manifest ?: fail(StoreProblem.INTEGRITY)))) &&
                    r.writes.all { it.beforeDigest==null && it.afterDigest!=null })
                H1JointReduction().validateDispatchClosureHistory(h,c,key,r.identity.principalId,r.authenticatedByCredentialId,
                    Digest.parse(r.fingerprint))
            }
            if(record==null)integrity(r.writes.isEmpty())
            else integrity((record.payload as? WDispatchClosurePayload)?.automatic==true)
        }
        if(record!=null && operation in setOf(AuthorityOperation.REQUEST_NATIVE_TRANSITION,AuthorityOperation.OBSERVE_NATIVE_TRANSITION)) {
            val write=r.writes.single { it.path==identityCodec.recordPath(record.identity) }
            integrity((write.beforeDigest==null)==(operation==AuthorityOperation.REQUEST_NATIVE_TRANSITION))
            validateNative(h,c,r,record)
        }
        if(record!=null && operation==AuthorityOperation.BIND_ASSIGNMENT)validateBind(h,c,r,record)
        if(record!=null && r.identity.operation in model2RefusalCodes)validateModel2Boundary(h,c,actor,r,record)
        if(record!=null) {
            val state=h.work[r.identity.projectId]
            val reused=r.writes.filter { it.record.namespace=="work" && it.record.family!="bodies" }.flatMap { w ->
                val family=H1Catalog.family(w.record.family);val key=identityCodec.decodeKey(family,StrictJson.encode(w.record.key))
                workLinks(c.work.getValue(key.projectId).record(family,key).payload).bodies
            }.filter { it.digest in state?.bodies.orEmpty() }.toSet()
            integrity(H1ProtectedReads().mayReuseBodies(h,actor,reused))
        }
        fun currentPlan(state: WorkState,start: WorkKey.Revision) {
            var key: WorkKey.Revision?=start;val seen=HashSet<WorkKey.Revision>()
            while(key!=null) {
                if(seen.size>=256)fail(StoreProblem.UNAVAILABLE)
                integrity(seen.add(key))
                val plan=(state.record(WorkFamily.VERIFICATION_PLANS,key).payload as WVerificationPlansPayload).value
                integrity(plan.authorizedChanges.all { h.core.validationDecisionApproved(it) })
                key=plan.previous
            }
        }
        when(val payload=record?.payload) {
            is WVerificationPlansPayload -> currentPlan(c.work.getValue(r.identity.projectId),record.identity.key as WorkKey.Revision)
            is WAcceptancesPayload -> currentPlan(h.work.getValue(r.identity.projectId),payload.value.verificationPlan)
            is WRecoveriesPayload -> {
                val assignment=h.work.getValue(r.identity.projectId).assignment(payload.value.assignment.assignmentId)
                val authWrites=r.writes.filter { it.record.namespace=="auth" }
                if(payload.value.decision=="revoked-isolated" && assignment.delegationId!=null) {
                    val id=assignment.delegationId
                    val old=h.authority.delegations.getValue(id)
                    integrity(c.authority.delegations[id]?.status==CredentialStatus.REVOKED)
                    val expected=if(old.status==CredentialStatus.ACTIVE)setOf(RecordKey("auth","delegations",uuid(id)).path) else emptySet()
                    integrity(authWrites.map { it.path }.toSet()==expected)
                } else integrity(authWrites.isEmpty())
            }
            else -> Unit
        }
        r.writes.filter { it.record.namespace=="work" && it.record.family=="verification-plans" }.forEach { write ->
            val key=identityCodec.decodeKey(WorkFamily.VERIFICATION_PLANS,StrictJson.encode(write.record.key)) as WorkKey.Revision
            currentPlan(c.work.getValue(key.projectId),key)
        }
        val owner=when(val p=record?.payload) {
            is WPlansPayload -> p.value.assignment
            is WLoadedViewsPayload -> p.value.assignment
            is WMemoryPayload -> p.value.assignment
            is WCheckpointsPayload -> p.value.assignment
            is WRetrospectivesPayload -> p.value.assignment
            is WResultsPayload -> p.value.assignment
            is WVerificationReportsPayload -> p.value.assignment
            is WSourceCutsPayload -> p.value.assignment
            else -> null
        }?.let { AssignmentSubject(r.identity.projectId,it.assignmentId,it.generation) }
        val subject=delegated?.let { AssignmentSubject(it.projectId,it.assignmentId,it.executionGeneration) }
        if(owner!=null)integrity(owner==subject || delegated==null && operation!=AuthorityOperation.UPDATE_PLAN)
        val controller=(record?.identity?.key as? WorkKey.ControllerContinuation)?.controllerId
            ?: if(operation in setOf(AuthorityOperation.RESERVE_ASSIGNMENT,AuthorityOperation.RECORD_CONTROLLER_CONTINUATION,AuthorityOperation.RECORD_DISPATCH_PLAN,AuthorityOperation.BIND_DISPATCH_ATTEMPT,AuthorityOperation.MATERIALIZE_DISPATCH_DEPENDENCY,AuthorityOperation.CLOSE_DISPATCH_INPUTS))
                (actor.scope as? CredentialScope.Controller)?.controllerId else null
        val call=WorkInvocation(r.identity.projectId,operation,r.identity.requestKey,subject,controller,
            if(operation==AuthorityOperation.UPDATE_PLAN)owner else null)
        val admission=Auth2Policy(h.authority,h.projection).authorizeHistoricalWork(r.authenticatedByCredentialId,call) ?: fail(StoreProblem.INTEGRITY)
        integrity(admission.mode==AdmissionMode.EVALUATE)
        if(record!=null && delegated!=null) {
            val expected=h.work.getValue(r.identity.projectId).binding(WAssignmentSubject(delegated.assignmentId,delegated.executionGeneration)).execution
            when(val p=record.payload) {
                is WResultsPayload -> integrity(p.value.producer==expected)
                is WVerificationReportsPayload -> integrity(p.value.reviewer==expected && p.value.subjects==delegated.subjectResults)
                is WSourceCutsPayload -> integrity(p.value.nativeHostId==expected.nativeHostId && p.value.nativeSessionId==expected.nativeSessionId)
                else -> Unit
            }
        }
        // Mutable assignment companions may not silently retarget executable terms. Rebind alone
        // changes pins/execution lineage; its separately held isolated-only path is not fabricated here.
        r.writes.filter { it.record.namespace=="work" && it.record.family=="assignments" }.forEach { w ->
            val key=identityCodec.decodeKey(WorkFamily.ASSIGNMENTS,StrictJson.encode(w.record.key)) as WorkKey.Id
            val b=c.work.getValue(key.projectId).assignment(key.id)
            if(operation.permission==WorkPermission.TASK_DISPATCH)integrity((actor.scope as? CredentialScope.Controller)?.controllerId==b.intendedExecution.controllerId)
            if(w.beforeDigest==null)return@forEach
            val a=h.work.getValue(key.projectId).assignment(key.id)
            when(operation) {
                AuthorityOperation.RECORD_CHECKPOINT -> {
                    integrity(a.copy(generation=b.generation,checkpoint=b.checkpoint)==b)
                    integrity(record!=null && b.checkpoint==WorkReference(record.identity,WorkCodec().digest(record)))
                }
                AuthorityOperation.COMPLETE_ASSIGNMENT -> integrity(a.copy(generation=b.generation,state="completed",currentIntent=null)==b)
                AuthorityOperation.BIND_ASSIGNMENT -> {
                    integrity(a.state=="reserved" && a.delegationId!=null && h.authority.delegations[a.delegationId]?.status==CredentialStatus.ACTIVE)
                    val credential=b.execution?.credentialId?.let { h.authority.credentials[it] } ?: fail(StoreProblem.INTEGRITY)
                    integrity(credential.scope==CredentialScope.Assignment(a.delegationId!!) && credential.credential.status==CredentialStatus.ACTIVE)
                }
                else -> Unit
            }
            if(operation!=AuthorityOperation.REBIND_ASSIGNMENT)integrity(a.task==b.task && a.manifest==b.manifest && a.profile==b.profile &&
                a.executionGeneration==b.executionGeneration && a.intendedExecution==b.intendedExecution && a.workspaceId==b.workspaceId && a.requiredResources==b.requiredResources && a.reviewSubjects==b.reviewSubjects && a.delegationId==b.delegationId)
        }
        r.writes.filter { it.record.namespace=="work" && it.beforeDigest!=null && it.record.family in setOf("plans","native-intents","deliveries") }.forEach { w ->
            val family=H1Catalog.family(w.record.family);val key=identityCodec.decodeKey(family,StrictJson.encode(w.record.key))
            val before=h.work.getValue(key.projectId).record(family,key).payload;val after=c.work.getValue(key.projectId).record(family,key).payload
            when {
                before is WPlansPayload && after is WPlansPayload -> integrity(before.value.task==after.value.task && before.value.assignment==after.value.assignment)
                before is WNativeIntentsPayload && after is WNativeIntentsPayload -> integrity(before.value.copy(generation=after.value.generation,outcome=after.value.outcome,observations=after.value.observations,reconciliation=after.value.reconciliation)==after.value)
                before is WDeliveriesPayload && after is WDeliveriesPayload -> integrity(before.value.assignment==after.value.assignment && before.value.sourceCutId==after.value.sourceCutId && before.value.destinationTask==after.value.destinationTask && before.value.content==after.value.content && before.value.state!="durable-delivered")
            }
        }
    }

    /** Intrinsic original-H/C correspondence, not a native rerun or a raw-command archive.
     * Observations remain attributed adapter evidence; this does not qualify physical fencing. */
    private fun validateNative(h: H1BoundState,c: H1BoundState,r: Receipt,result: WorkRecord) {
        val old=h.work.getValue(r.identity.projectId);val next=c.work.getValue(r.identity.projectId)
        val intent=(result.payload as? WNativeIntentsPayload)?.value ?: fail(StoreProblem.INTEGRITY)
        val intentKey=result.identity.key as? WorkKey.Id ?: fail(StoreProblem.INTEGRITY)
        val assignmentKey=WorkKey.Id(r.identity.projectId,intent.assignment.assignmentId)
        val a=old.assignment(assignmentKey.id);val b=next.assignment(assignmentKey.id)
        integrity(a.binding!=null && a.execution!=null && intent.assignment==WAssignmentSubject(assignmentKey.id,a.executionGeneration))
        integrity(intent.expectedSession==a.execution!!.nativeSessionId && intent.expectedWorkspace==a.workspaceId)
        val paths=setOf(identityCodec.recordPath(result.identity),identityCodec.recordPath(WorkRecordIdentity(WorkFamily.ASSIGNMENTS,assignmentKey)))
        integrity(r.writes.map { it.path }.toSet()==paths)
        integrity(a.generation<Long.MAX_VALUE && b.generation==a.generation+1)
        val prior=when(intent.operation) {
            "start" -> setOf("bound")
            "resume" -> setOf("paused")
            "pause" -> setOf("active")
            "stop" -> setOf("bound","active","paused")
            else -> fail(StoreProblem.INTEGRITY)
        }
        a.requiredResources.forEach { key ->
            val claim=(old.record(WorkFamily.CLAIMS,key).payload as WClaimsPayload).value
            // New execution requests require held claims. An observation reconciles the original
            // intent even after recovery isolated its area; it neither acquires nor releases claims.
            integrity((claim.state=="held" || r.identity.operation=="observe_native_transition" && claim.state=="isolated") && claim.workspaceId==a.workspaceId &&
                (claim.holder as? WClaimsHolderVariant0Value)?.value?.subject==intent.assignment)
        }
        if(r.identity.operation=="request_native_transition") {
            integrity(a.currentIntent==null && a.state in prior && result.identity !in old.records)
            integrity(intent.generation==0L && intent.outcome=="pending" && intent.observations.isEmpty() && intent.reconciliation==null)
            if(intent.operation=="resume")integrity(a.checkpoint!=null)
            if(intent.operation in setOf("start","resume"))validateReady(h,a)
            val state=when(intent.operation) { "pause" -> "pausing";"stop" -> "stopping";else -> a.state }
            integrity(a.copy(generation=b.generation,currentIntent=intentKey,state=state)==b)
        } else {
            val previous=(old.record(WorkFamily.NATIVE_INTENTS,intentKey).payload as WNativeIntentsPayload).value
            integrity(a.currentIntent==intentKey && previous.outcome in setOf("pending","unknown"))
            integrity(previous.generation<Long.MAX_VALUE && intent.generation==previous.generation+1)
            integrity(previous.copy(generation=intent.generation,outcome=intent.outcome,observations=intent.observations)==intent)
            // Recovery can quarantine the assignment without replacing its unresolved intent.
            // Like CORE.observe, use that exact pending/unknown intent, not an inferred prior phase.
            val state=when(intent.outcome) {
                "unknown" -> "unknown"
                "confirmed" -> when(intent.operation) { "start","resume" -> "active";"pause" -> "paused";"stop" -> "stopped";else -> fail(StoreProblem.INTEGRITY) }
                "not-performed" -> b.state.also { integrity(it in prior) }
                else -> fail(StoreProblem.INTEGRITY)
            }
            if(intent.outcome=="confirmed" && intent.operation in setOf("start","resume")) {
                validateReady(h,a)
                h.requireCurrentContinuation(r.identity.projectId,intent.assignment)
                Model2Inspector(old).requireActivation(intent.assignment,true)
            }
            integrity(a.copy(generation=b.generation,state=state,currentIntent=if(intent.outcome=="unknown")intentKey else null)==b)
        }
    }
    private fun validateReady(h: H1BoundState,a: WAssignments) {
        val state=h.work.getValue(a.task.projectId)
        val task=state.task(a.task)
        val manifest=(state.resolve(a.manifest).payload as WInputManifestsPayload).value
        val profile=(state.record(WorkFamily.PROFILES,a.profile).payload as WProfilesPayload).value
        integrity(task.state=="executable" && task.unresolved.isEmpty() && task.executionProfile==a.profile)
        integrity(manifest.task==a.task && manifest.base==task.base && manifest.knownGaps.isEmpty())
        integrity(profile.outputReserveTokens+profile.toolReserveTokens+profile.compactionReserveTokens<profile.contextTokens)
        integrity(task.approvedDesign.all { h.core.validationDecisionApproved(it) })
        validateDelegation(h,a)
    }
    private fun validateDelegation(h: H1BoundState,a: WAssignments) {
        val delegation=a.delegationId?.let { h.authority.delegations[it] } ?: fail(StoreProblem.INTEGRITY)
        integrity(delegation.status==CredentialStatus.ACTIVE && h.authority.credentials.values.any {
            it.scope==CredentialScope.Assignment(delegation.id) && it.credential.status==CredentialStatus.ACTIVE
        })
        integrity(h.authority.policy.allows(Actor(delegation.binding.principalId),a.task.projectId,Capability.READ))
    }
    private fun validateBind(h: H1BoundState,c: H1BoundState,r: Receipt,result: WorkRecord) {
        val key=result.identity.key as? WorkKey.Id ?: fail(StoreProblem.INTEGRITY)
        val old=h.work.getValue(key.projectId);val next=c.work.getValue(key.projectId)
        val a=old.assignment(key.id);val b=next.assignment(key.id)
        val intentKey=a.currentIntent ?: fail(StoreProblem.INTEGRITY)
        val i=(old.record(WorkFamily.NATIVE_INTENTS,intentKey).payload as WNativeIntentsPayload).value
        val j=(next.record(WorkFamily.NATIVE_INTENTS,intentKey).payload as WNativeIntentsPayload).value
        val bindingKey=WorkKey.AssignmentBinding(key.projectId,key.id,a.executionGeneration)
        val binding=next.record(WorkFamily.ASSIGNMENT_BINDINGS,bindingKey)
        integrity(binding.identity !in old.records)
        integrity(a.state=="reserved" && a.execution==null && a.binding==null && a.delegationId!=null)
        integrity(a.generation<Long.MAX_VALUE && b.generation==a.generation+1)
        integrity(i.operation=="prepare" && i.outcome=="pending" && i.assignment==WAssignmentSubject(key.id,a.executionGeneration))
        integrity(i.expectedSession==a.intendedExecution.nativeSessionId && i.expectedWorkspace==a.workspaceId)
        integrity(i.generation<Long.MAX_VALUE && j.generation==i.generation+1 && j.outcome=="confirmed" && j.observations.isNotEmpty())
        integrity(i.copy(generation=j.generation,outcome="confirmed",observations=j.observations)==j)
        integrity(a.copy(generation=b.generation,execution=b.execution,binding=WorkReference(binding.identity,WorkCodec().digest(binding)),state="bound",currentIntent=null)==b)
        integrity(r.writes.map { it.path }.toSet()==setOf(identityCodec.recordPath(result.identity),identityCodec.recordPath(binding.identity),
            identityCodec.recordPath(WorkRecordIdentity(WorkFamily.NATIVE_INTENTS,intentKey))))
        validateDelegation(h,a)
    }
}
