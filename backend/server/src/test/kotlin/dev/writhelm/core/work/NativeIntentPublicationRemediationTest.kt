// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Utf8Content
import dev.writhelm.core.store.*
import kotlin.test.*
import org.junit.jupiter.api.Test

/** Actual CORE reducer, work codecs, Git store and journal. Credential admission is a deliberately
 * synthetic exact controller test port, not an implementation/qualification of APP or AUTH2. */
class NativeIntentPublicationRemediationTest {
    @Test fun `F2 real reducer native intent results match journal after bytes counters and companion effects`() {
        val f=ReviewProbeFixture();f.reserve();f.enroll();f.bind();val codec=WorkCodec()
        fun storeKey(i: WorkRecordIdentity)=RecordKey("work",i.family.tag,StrictJson.decode(identityCodec.encodeKey(i.key)))
        fun work(snapshot: StoreSnapshot): WorkState {
            val records=snapshot.entries.keys.filter { it.startsWith("work/records/") }.map { codec.decodeRecord(snapshot.file(it)!!,true) }
            val bodies=snapshot.entries.keys.filter { it.startsWith("work/bodies/") }.map { Utf8Content.fromBytes(snapshot.file(it)!!) }
            return WorkState.restore(f.project,records,bodies)
        }
        fun effect(snapshot: StoreSnapshot,command: StoreCommand)=f.reducer.reduce(work(snapshot),codec.decodeCommand(command.bytes()),f.actor,f.facts)
        fun result(effect: WorkEffect): JsonValue {
            val record=effect.after.records.getValue(effect.result)
            return obj("key" to StrictJson.decode(identityCodec.encodeKey(effect.result.key)),
                "generation" to (recordGeneration(record.payload)?.let(::JNumber) ?: JNull),"digest" to digest(codec.digest(record)))
        }
        val h1=H1PublicationBindings(
            canonicalize={ codec.encodeCommand(codec.decodeCommand(it)) },
            authorize={ _,c -> if(c.projectId==f.project)CurrentCommandScope.NewEffect(AuthenticatedActor(f.actor.principalId,f.actor.credentialId)) else CurrentCommandScope.Denied },
            allowedWrites={ h,c -> effect(h,c).changedRecords.map { storeKey(it.identity) }.toSet() },
            validateRecord={ key,bytes,historical ->
                if(key.namespace=="work") {
                    val record=codec.decodeRecord(bytes,historical)
                    assertEquals(key.path,identityCodec.recordPath(record.identity))
                }
            },
            preflight={ _,_,_-> },
            validateBoundary={ h,c,r ->
                assertEquals(h.oid,r.validatedAt)
                assertEquals(r.writes.map { it.path }.toSet()+r.path,(h.entries.keys+c.entries.keys).filter { h.entries[it]!=c.entries[it] }.toSet())
                r.writes.forEach { write ->
                    assertEquals(write.beforeDigest,h.file(write.path)?.let(::sha))
                    assertEquals(write.afterDigest,c.file(write.path)?.let(::sha))
                }
                val outcome=r.outcome as LogicalOutcome.Success
                val selector=H1Catalog.result(r.identity.operation,outcome.result)
                assertEquals("native-intents",selector.family)
                val after=work(c);val before=work(h)
                val key=identityCodec.decodeKey(WorkFamily.NATIVE_INTENTS,StrictJson.encode(selector.key))
                val intent=after.record(WorkFamily.NATIVE_INTENTS,key)
                val fields=outcome.result.fields("key","generation","digest")
                assertEquals(codec.digest(intent),digest(fields.getValue("digest")))
                assertEquals(recordGeneration(intent.payload),fields.getValue("generation").long())
                assertEquals(f.assignmentId,(intent.payload as WNativeIntentsPayload).value.assignment.assignmentId)
                val old=before.assignment(f.assignmentId);val next=after.assignment(f.assignmentId)
                assertEquals(old.generation+1,next.generation)
                assertEquals(old.task,next.task);assertEquals(old.executionGeneration,next.executionGeneration)
                assertEquals(old.execution,next.execution);assertEquals(old.intendedExecution,next.intendedExecution)
                assertEquals(setOf("assignments","native-intents"),r.writes.map { it.record.family }.toSet())
            })
        val ports=StorePorts(mapOf(
            "auth" to NamespaceValidator(obj("formatVersion" to num(2),"profileVersion" to num(2))) { h ->assertTrue(h.entries.keys.none { it.startsWith("auth/records/") }) },
            "work" to NamespaceValidator(obj("formatVersion" to num(1),"modelVersion" to num(2))) { h ->work(h) },
            "policy" to NamespaceValidator(obj("formatVersion" to num(2))) { }),
            validateTransition={ h,c,actor,evaluation ->
                assertEquals(f.actor.principalId,actor.principalId)
                val expected=effect(h,c)
                assertEquals(expected.changedRecords.map { identityCodec.recordPath(it.identity) }.toSet(),evaluation.changes.map { it.key.path }.toSet())
                expected.changedRecords.forEach { record ->assertContentEquals(codec.encodeRecord(record),evaluation.changes.single { it.key.path==identityCodec.recordPath(record.identity) }.bytes()) }
            },
            validateHistoricalReceipt={ _,r ->
                val outcome=r.outcome as LogicalOutcome.Success
                assertEquals("native-intents",H1Catalog.result(r.identity.operation,outcome.result).family)
                assertEquals(setOf("assignments","native-intents"),r.writes.map { it.record.family }.toSet())
            },h1=h1)
        val genesis=listOf(
            StoreFile("core/format.json",StrictJson.encode(CoreRecordCodec.header())),
            StoreFile("auth/format.json",StrictJson.encode(obj("formatVersion" to num(2),"profileVersion" to num(2)))),
            StoreFile("work/format.json",StrictJson.encode(obj("formatVersion" to num(1),"modelVersion" to num(2)))),
            StoreFile("policy/format.json",StrictJson.encode(obj("formatVersion" to num(2)))))+
            f.state.records.values.map { StoreFile(identityCodec.recordPath(it.identity),codec.encodeRecord(it)) }
        val directory=storeTestDirectory("core-native-intent-journal-")
        var corruptCounter=false
        val evaluator=CommandEvaluator { h,c,_ ->
            val derived=effect(h,c);var value=result(derived)
            if(corruptCounter) {
                val fields=(value as JObject).entries
                value=JObject(fields+("generation" to JNumber(derived.after.assignment(f.assignmentId).generation)))
            }
            Evaluation(LogicalOutcome.Success(c.operation,value,false),derived.changedRecords.map { RecordChange(storeKey(it.identity),codec.encodeRecord(it)) })
        }
        val legacyAccess=CommandAccess { _,_->error("H1 current-scope port must be used") }
        GitCommandStore.initialize(directory,uid(1200),genesis,ports,admission=AdmissionPolicy(formatVersion=2)).use { store ->
            val intent=uid(1201);val correlation=uid(1202)
            val request=f.command(CRequestNativeTransitionOperation(CRequestNativeTransition(f.assignmentId,2,intent,correlation,"start",null,emptyList())))
            fun execute(command: WorkCommand)=store.execute(StoreCommand.decodeH1(codec.encodeCommand(command),h1),legacyAccess,evaluator)
            val requested=assertIs<CommandResult.Settled>(execute(request))
            assertEquals(3,store.read(f.project,ReadAccess { _,_->true }) { work(it).assignment(f.assignmentId).generation })
            fun assertIntent(receipt: Receipt,generation: Long) {
                val outcome=receipt.outcome as LogicalOutcome.Success
                val fields=outcome.result.fields("key","generation","digest")
                assertEquals(intent,uuid(fields.getValue("key").fields("projectId","id").getValue("id")))
                assertEquals(generation,fields.getValue("generation").long())
                assertEquals(setOf("assignments","native-intents"),receipt.writes.map { it.record.family }.toSet())
                store.read(f.project,ReadAccess { _,_->true }) { h ->
                    val record=work(h).record(WorkFamily.NATIVE_INTENTS,WorkKey.Id(f.project,intent))
                    assertEquals(codec.digest(record),digest(fields.getValue("digest")))
                }
                val roundTrip=JournalCodec.decode(JournalCodec.encode(receipt),receipt.path)
                assertContentEquals(StrictJson.encode(outcome.json()),StrictJson.encode(roundTrip.outcome.json()))
            }
            assertIntent(requested.receipt,0)
            f.facts=f.facts.copy(native=f.facts.native+(intent to WorkNativeObservation(f.worker.assignment!!,intent,correlation,"start","confirmed",listOf(f.source),f.execution,false,"bound")))
            val observed=assertIs<CommandResult.Settled>(execute(f.command(CObserveNativeTransitionOperation(CObserveNativeTransition(f.assignmentId,3,intent,0,"confirmed",listOf(f.source),emptyList())))))
            assertIntent(observed.receipt,1)
            assertEquals(4,store.read(f.project,ReadAccess { _,_->true }) { work(it).assignment(f.assignmentId).generation })
            val before=store.read(f.project,ReadAccess { _,_->true }) { it.oid }
            corruptCounter=true
            val bad=f.command(CRequestNativeTransitionOperation(CRequestNativeTransition(f.assignmentId,4,uid(1203),uid(1204),"pause",null,emptyList()))).copy(requestKey=uid(1205))
            assertEquals(StoreProblem.INTEGRITY,assertIs<CommandResult.Refused>(execute(bad)).problem)
            assertEquals(before,store.read(f.project,ReadAccess { _,_->true }) { it.oid })
        }
    }
}
