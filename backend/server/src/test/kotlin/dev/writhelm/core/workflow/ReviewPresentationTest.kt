// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.workflow

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.app.JointFixture
import dev.writhelm.core.app.H1LegacyReads
import dev.writhelm.core.app.H1Navigation
import dev.writhelm.core.app.H1ProtectedReads
import dev.writhelm.core.app.H1BoundState
import dev.writhelm.core.auth2.*
import dev.writhelm.core.app.id as appId
import dev.writhelm.core.app.project as appProject
import dev.writhelm.core.app.planner as appPlanner
import dev.writhelm.core.app.plannerKey as appPlannerKey
import dev.writhelm.core.app.revision as appRevision
import dev.writhelm.core.app.AppFixture
import dev.writhelm.core.app.reader as appReader
import dev.writhelm.core.app.readerToken as appReaderToken
import dev.writhelm.core.app.ownerToken as appOwnerToken
import dev.writhelm.service.WrithelmService
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*

class ReviewPresentationTest {
    @Test fun substantiveAbsenceAndAnchoredExplanationPreserveOldEncodingAndBindNewMeaning() {
        val ref=ReviewItem(f.id(),f.a.x())
        fun explained(text:String?)=ReviewPacket(listOf(ref),"Question","Scope","Product reason",
            emptyList(),emptyList(),emptyList(),emptyList(),presentations=listOf(
                ReviewPresentation("ru","en",listOf(ReviewPresentationItem(ref.id,"Правило","Точное правило.",text)))))
        val plain=explained(null);val next=explained("Причина конкретного правила.")
        assertNotEquals(plain.digest,next.digest)
        assertNotEquals(next.digest,explained("Иная причина.").digest)
        val encoded=packet(next)
        assertEquals(next.digest,packet(encoded).digest)
        assertContentEquals(StrictJson.encode(encoded),StrictJson.encode(packet(packet(encoded))))
        assertFalse(StrictJson.encode(packet(plain)).decodeToString().contains("\"explanation\""))
        assertFails { explained(" ") }
    }
    private val f=WorkflowFixture(a=doc(70,context=""))
    private val item=ReviewItem(f.id(),f.a.x())
    private val base=packet(listOf(item),emptyList())
    private fun presentation(language:String="ru",text:String="Точный текст.")=
        ReviewPresentation(language,"en",listOf(ReviewPresentationItem(item.id,"Заголовок",text)))
    private fun localized(values:List<ReviewPresentation> = listOf(presentation()),link:ReviewFollowup?=null)=
        ReviewPacket(base.items,base.question,base.scope,base.rationale,base.consequences,
            base.alternatives,base.uncertainties,base.contexts,base.dependencies,base.informationGaps,link,values)

    @Test fun legacyBytesAndDigestsRemainUnchangedAndV3BindsEveryField() {
        val raw=StrictJson.encode(packet(base))
        // Independently captured from the pre-localization implementation, not this codec.
        assertEquals("a8616852d9147090fe55c3e553a5055bb691c1d5a403a9d9a27f92b5ca1c5655",base.digest.hex)
        assertEquals("d83ff7819b3f44aa2bec98ab00ccdd4d618500b1ee65d92ada332bf176d0ee77",Digest.of(raw).hex)
        assertFalse((packet(base) as JObject).entries.containsKey("presentations"))
        assertContentEquals(raw,StrictJson.encode(packet(localized(emptyList()))))
        assertEquals(base.digest,localized(emptyList()).digest)
        val p=localized()
        assertNotEquals(base.digest,p.digest)
        assertNotEquals(p.digest,localized(listOf(presentation(text="Изменение."))).digest)
        assertNotEquals(p.digest,localized(listOf(presentation("en"))).digest)
        assertNotEquals(p.digest,localized(listOf(ReviewPresentation("ru","fr",presentation().items))).digest)
        assertNotEquals(p.digest,localized(listOf(ReviewPresentation("ru","en",
            listOf(ReviewPresentationItem(item.id,"Другой заголовок","Точный текст."))))).digest)
        val decoded=packet(StrictJson.decode(StrictJson.encode(packet(p))))
        assertEquals(p.digest,decoded.digest)
        assertContentEquals(StrictJson.encode(packet(p)),StrictJson.encode(packet(decoded)))
        val link=ReviewFollowup(f.id(),base.digest,base.digest,
            listOf(ReviewFollowupItem(item.id,item.id,"unchanged","Same.")))
        val oldLinked=localized(emptyList(),link)
        assertEquals("680dc0d1ca7af8ba7e78c1b131058be04b00e1701ea470726a4a5fa4b184f1c1",oldLinked.digest.hex)
        assertEquals("cc597f44884244821ef0931c9ace533ad86835deb003e3cbf5bdbf3c21e8854e",
            Digest.of(StrictJson.encode(packet(oldLinked))).hex)
        assertEquals(oldLinked.digest,packet(packet(oldLinked)).digest)
        assertNotEquals(oldLinked.digest,localized(link=link).digest)
        assertNotEquals(p.digest,localized(link=link).digest)
    }

    @Test fun everyLanguageCoversExactlyTheCurrentItems() {
        assertFails { localized(listOf(presentation(),presentation())) }
        assertFails { localized((0..8).map { presentation("en-"+('A'+it)+"A") }) }
        assertFails { ReviewPresentation("ru","en",emptyList()) }
        assertFails { ReviewPresentation("ru","en",presentation().items+presentation().items) }
        assertFails { localized(listOf(ReviewPresentation("ru","en",
            listOf(ReviewPresentationItem(UUID.randomUUID(),"Title","Text"))))) }
        val other=ReviewItem(f.id(),f.a.y())
        assertFails { ReviewPacket(listOf(item,other),base.question,base.scope,base.rationale,
            base.consequences,base.alternatives,base.uncertainties,base.contexts,
            presentations=listOf(presentation())) }
    }

    @Test fun languageUnicodeAndByteBoundsAreStrict() {
        listOf("RU","ru-ru","en_US","en-","x","en-u-ca-gregory","").forEach { tag ->
            assertFails { presentation(tag) }
            assertFails { ReviewPresentation("ru",tag,presentation().items) }
        }
        presentation("ru-RU");presentation("zh-Hant-TW")
        listOf("","  ","\uD800").forEach { text ->
            assertFails { ReviewPresentationItem(item.id,"Title",text) }
            assertFails { ReviewPresentationItem(item.id,text,"Text") }
        }
        assertFails { ReviewPresentationItem(item.id,"X".repeat(201),"Text") }
        ReviewPresentationItem(item.id,"😀".repeat(200),"я".repeat(32768))
        assertFails { ReviewPresentationItem(item.id,"Title","я".repeat(32769)) }
        val items=(0..16).map {
            val ref=doc(100L+it,context="").x()
            ReviewItem(f.id(),ref.copy(revision=ref.revision.copy(document=DocumentKey(project,f.id()))))
        }
        // These items are valid by themselves: the failure below is the aggregate byte bound.
        ReviewPacket(items,base.question,base.scope,base.rationale,base.consequences,
            base.alternatives,base.uncertainties,emptyList())
        assertFails { ReviewPacket(items,base.question,base.scope,base.rationale,base.consequences,
            base.alternatives,base.uncertainties,emptyList(),presentations=listOf(
                ReviewPresentation("ru","en",items.map { ReviewPresentationItem(it.id,"Title","x".repeat(65536)) }))) }
    }

    @Test fun codecRefusesUnknownFieldsAndInvalidItemCoverageAndProtocolDeclaresBothIdentities() {
        val encoded=packet(localized()) as JObject
        val presentations=encoded.entries.getValue("presentations") as JArray
        val p=presentations.values.single() as JObject
        fun withPresentation(value:JsonValue)=JObject(encoded.entries+("presentations" to JArray(listOf(value))))
        fun withItems(values:List<JsonValue>)=withPresentation(JObject(p.entries+("items" to JArray(values))))
        assertFails { packet(withPresentation(JObject(p.entries+("unknown" to str("field"))))) }
        val i=(p.entries.getValue("items") as JArray).values.single() as JObject
        val wrong=JObject(i.entries+("itemId" to uuid(UUID.randomUUID())))
        assertFails { packet(withItems(listOf(wrong))) }
        assertFails { packet(withItems(listOf(i,i))) }
        assertFails { packet(withItems(emptyList())) }
        assertTrue(KnowledgeProtocol.definitions.getValue("packet").accepts(encoded))
        val wrongLanguage=withPresentation(JObject(p.entries+("language" to str("RU"))))
        assertFalse(KnowledgeProtocol.definitions.getValue("packet").accepts(wrongLanguage))
        val exported=StrictJson.encode(KnowledgeProtocol.exported(),1024*1024).decodeToString()
        assertTrue(exported.contains("\"itemKey\""));assertTrue(exported.contains("\"presentations\""))
        val two=localized(listOf(presentation("ru"),presentation("en")))
        assertNotEquals(two.digest,localized(two.presentations.reversed()).digest)
    }

    @Test fun defensiveCopiesAndFullSnapshotRoundtrip() {
        f.add(f.a)
        val input=presentation().items.toMutableList()
        val p=ReviewPresentation("ru","en",input)
        val languages=mutableListOf(p);val packet=localized(languages)
        val digest=packet.digest
        input.clear();languages.clear()
        assertEquals(1,packet.presentations.size);assertEquals(1,p.items.size)
        assertEquals(digest,packet.digest)
        assertFails { (packet.presentations as MutableList).clear() }
        assertFails { (p.items as MutableList).clear() }
        val request=f.pending(packet)
        val codec=CoreSnapshotCodec();val entries=mutableListOf<CoreEntry>()
        codec.export(f.state,entries::add)
        val restored=codec.restore(entries.asSequence())
        assertEquals(request.packet.digest,restored.snapshot().requests.getValue(request.id).packet.digest)
        assertEquals("Точный текст.",restored.snapshot().requests.getValue(request.id).packet.presentations.single().items.single().text)
    }

    @Test fun localizedRecordCannotTurnMetadataScopeIntoSourceAccess() {
        val fixture=JointFixture();fixture.activateWithAttributedFixtureObservations()
        val source=appRevision(90).clauseReference(ClauseId("Y"))
        val reviewItem=ReviewItem(appId(80001),source)
        val packet=ReviewPacket(listOf(reviewItem),"Question","Scope","Rationale",
            listOf("Consequence"),listOf("Alternative"),emptyList(),listOf(appRevision(90).contextReference()),
            presentations=listOf(ReviewPresentation("ru","en",listOf(
                ReviewPresentationItem(reviewItem.id,"Название","Защищённый источник.")))))
        val transition=good(fixture.base.core.request(Actor(appPlanner),appProject,appId(80002),packet))
        val state=H1BoundState.checked(fixture.base.storeId,fixture.base.basis,transition.state,
            fixture.base.authority.records(),fixture.base.work)
        val request=transition.value
        val read=StrictJson.encode(obj("formatVersion" to num(1),"projectId" to uuid(appProject),
            "operation" to str("read_request"),"subject" to obj("id" to uuid(request.id))))
        val reader=H1LegacyReads(H1Navigation())
        fixture.key(appPlannerKey) { assertNotNull(reader.read(state,it,read)) }
        fixture.key(fixture.workerKey) { key ->
            assertFails { reader.read(state,key,read) }
            val c=state.authority.authenticate(key,appProject)!!
            val original=H1ProtectedReads().closure(state,c)
            val metadata=ProtectedResource.LegacyRecord(appProject,LegacyReadFamily.REQUEST,request.id,request.digest)
            // Synthetic record-only scope: metadata authorization is deliberately not source access.
            val proof=DerivedDisclosureClosure(original.basis,appProject,original.binding,
                original.exactTask,original.taskAndBase+metadata,original.ownContinuation,
                original.reviewGroup,original.ownedResults,original.activeOwnRecords)
            val policy=Auth2Policy(state.authority,state.projection)
            val unit=DisclosureUnit(metadata,ContentApplicability.CURRENT)
            assertTrue(policy.authorizeDisclosure(key,DisclosureRequest(appProject,"read_request",listOf(unit)),proof))
            assertFalse(policy.authorizeDisclosure(key,DisclosureRequest(appProject,"read_request",listOf(unit,
                DisclosureUnit(ProtectedResource.Knowledge(source),ContentApplicability.CURRENT))),proof))
        }
    }

    @Test fun legacyServiceLocalizedRequestAndBundleRecheckCurrentReadAuthority() {
        AppFixture().use { fixture ->
            val revision=appRevision(80010);fixture.propose(revision)
            val reviewItem=ReviewItem(appId(80011),revision.clauseReference(ClauseId("X")))
            val localized=ReviewPacket(listOf(reviewItem),"Question","Scope","Rationale",
                listOf("Consequence"),listOf("Alternative"),emptyList(),listOf(revision.contextReference()),
                presentations=listOf(ReviewPresentation("ru","en",listOf(
                    ReviewPresentationItem(reviewItem.id,"Название","Защищённый источник.")))))
            val request=fixture.request(localized);val service=WrithelmService(fixture.app)
            fun read(operation:String):ByteArray {
                val subject=if(operation=="read_request")obj("id" to uuid(request.id)) else
                    obj("id" to uuid(request.id),"requestDigest" to digest(request.digest),"packetDigest" to digest(localized.digest))
                return StrictJson.encode(obj("formatVersion" to num(1),"projectId" to uuid(appProject),
                    "operation" to str(operation),"subject" to subject))
            }
            listOf("read_request","read_review_bundle").forEach { operation ->
                val reply=service.read(appReaderToken,read(operation))
                assertEquals(200,reply.status)
                assertTrue(reply.bytes(4*1024*1024).decodeToString().contains("Защищённый источник."))
            }
            fixture.core(CommandPayload.Revoke(appReader),appOwnerToken)
            listOf("read_request","read_review_bundle").forEach { operation ->
                assertEquals(403,service.read(appReaderToken,read(operation)).status)
            }
        }
    }
}
