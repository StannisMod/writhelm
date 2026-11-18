// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.workflow

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.util.UUID

class ReviewFollowupTest {
    private fun linked(base:ReviewPacket,link:ReviewFollowup)=ReviewPacket(base.items,base.question,base.scope,
        base.rationale,base.consequences,base.alternatives,base.uncertainties,base.contexts,
        base.dependencies,base.informationGaps,link)
    private class Scenario {
        val f=WorkflowFixture(a=doc(30,context=""))
        val oldX=ReviewItem(f.id(),f.a.x());val oldY=ReviewItem(f.id(),f.a.y())
        val old:ApprovalRequest
        val nextX:ReviewItem;val nextY:ReviewItem
        val base:ReviewPacket
        init {
            f.add(f.a);old=f.pending(packet(listOf(oldX,oldY),emptyList()))
            f.answer(old,StructuredAnswer.ChangesRequired(mapOf(oldX.id to "Уточнить ограничение X.")))
            val revision=doc(31,x="X1",context="");f.add(revision)
            nextX=ReviewItem(f.id(),revision.x());nextY=ReviewItem(f.id(),f.a.y())
            base=packet(listOf(nextX,nextY),emptyList())
        }
        fun link(items:List<ReviewFollowupItem> = listOf(
            ReviewFollowupItem(oldX.id,nextX.id,"revised","Уточнено ограничение."),
            ReviewFollowupItem(oldY.id,nextY.id,"unchanged","Смысл не менялся; пункт ещё требует решения.")
        ))=ReviewFollowup(old.id,old.digest,old.packet.digest,items)
    }
    @Test fun exactFeedbackAndEveryUnapprovedPointSurviveCanonicalRoundtripAndRestore() {
        val s=Scenario();val p=linked(s.base,s.link())
        val raw=StrictJson.encode(packet(p));val restored=packet(StrictJson.decode(raw))
        assertEquals(raw.decodeToString(),StrictJson.encode(packet(restored)).decodeToString())
        assertEquals(p.digest,restored.digest);assertNotEquals(s.base.digest,p.digest)
        assertFalse((packet(s.base) as JObject).entries.containsKey("followup"))
        assertEquals(s.base.digest,packet(packet(s.base)).digest)
        val request=s.f.pending(restored)
        val recovered=WorkflowSnapshotValidator.restore(s.f.state.snapshot(),10000,64)
        val old=good(recovered.readResolution(reader,project,s.old.id))
        assertEquals("Уточнить ограничение X.",(old.answer as StructuredAnswer.ChangesRequired).comments[s.oldX.id])
        bad(WorkflowProblem.UNAPPROVED_INPUT,recovered.approvalTrace(reader,s.nextY.target))
        val resolution=s.f.answer(request)
        assertEquals(listOf(s.nextX.target,s.nextY.target),resolution.approved)
    }
    @Test fun omissionWrongRevisionCrossPointAndFalseUnchangedRefuseBeforePublication() {
        val s=Scenario()
        val incomplete=s.link(listOf(ReviewFollowupItem(s.oldX.id,s.nextX.id,"revised","Missing Y.")))
        bad(WorkflowProblem.INVALID_REFERENCE,s.f.state.request(planner,project,s.f.id(),linked(s.base,incomplete)))
        val badDigest=ReviewFollowup(s.old.id,Digest.of("wrong".toByteArray()),s.old.packet.digest,s.link().items)
        bad(WorkflowProblem.INVALID_REFERENCE,s.f.state.request(planner,project,s.f.id(),linked(s.base,badDigest)))
        val falseUnchanged=s.link(listOf(ReviewFollowupItem(s.oldX.id,s.nextX.id,"unchanged","Wrong equality."),
            ReviewFollowupItem(s.oldY.id,s.nextY.id,"unchanged","Same.")))
        bad(WorkflowProblem.INVALID_REFERENCE,s.f.state.request(planner,project,s.f.id(),linked(s.base,falseUnchanged)))
        val swapped=s.link(listOf(ReviewFollowupItem(s.oldY.id,s.nextX.id,"revised","Wrong subject."),
            ReviewFollowupItem(s.oldX.id,s.nextY.id,"revised","Wrong subject.")))
        bad(WorkflowProblem.INVALID_REFERENCE,s.f.state.request(planner,project,s.f.id(),linked(s.base,swapped)))
    }
    @Test fun retainedCannotHideAnUnapprovedPointOrDropAnApprovedDependency() {
        val s=Scenario()
        val base=packet(listOf(s.nextX),emptyList())
        val link=s.link(listOf(ReviewFollowupItem(s.oldX.id,s.nextX.id,"revised","Changed."),
            ReviewFollowupItem(s.oldY.id,null,"retained","Not approved.")))
        bad(WorkflowProblem.INVALID_REFERENCE,s.f.state.request(planner,project,s.f.id(),linked(base,link)))
        val f=WorkflowFixture(a=doc(40,context=""));f.add(f.a)
        val x=ReviewItem(f.id(),f.a.x());val y=ReviewItem(f.id(),f.a.y())
        val approved=f.pending(packet(listOf(x,y),emptyList()));f.answer(approved)
        val changed=doc(41,x="X1",context="");f.add(changed)
        val item=ReviewItem(f.id(),changed.x(),f.a.x())
        val retained=ReviewFollowup(approved.id,approved.digest,approved.packet.digest,listOf(
            ReviewFollowupItem(x.id,item.id,"revised","Changed X."),
            ReviewFollowupItem(y.id,null,"retained","Preserve exactly approved Y.")))
        bad(WorkflowProblem.INVALID_REFERENCE,f.state.request(planner,project,f.id(),
            linked(packet(listOf(item),emptyList()),retained)))
        val valid=f.pending(linked(packet(listOf(item),emptyList(),dependencies=listOf(f.a.y())),retained))
        assertEquals(listOf(changed.x()),f.answer(valid).approved,"retention is not another approval target")
        assertEquals(approved.id,good(f.state.approvalTrace(reader,f.a.y())).requestId)
    }
    @Test fun aPendingPredecessorIsNotFeedbackAuthority() {
        val f=WorkflowFixture(a=doc(50,context=""));f.add(f.a)
        val oldItem=ReviewItem(f.id(),f.a.x());val previous=f.pending(packet(listOf(oldItem),emptyList()))
        val newItem=ReviewItem(f.id(),f.a.x())
        val link=ReviewFollowup(previous.id,previous.digest,previous.packet.digest,
            listOf(ReviewFollowupItem(oldItem.id,newItem.id,"unchanged","No decision exists yet.")))
        bad(WorkflowProblem.INVALID_REFERENCE,f.state.request(planner,project,f.id(),
            linked(packet(listOf(newItem),emptyList()),link)))
    }
    @Test fun duplicateAndUnanchoredDispositionsRefuseAtStructuralBoundary() {
        val s=Scenario();val item=s.link().items.first()
        assertFails {ReviewFollowup(s.old.id,s.old.digest,s.old.packet.digest,listOf(item,item))}
        assertFails {ReviewFollowupItem(null,null,"revised","No anchors.")}
        assertFails {ReviewFollowupItem(s.oldX.id,s.nextX.id,"revised","  ")}
        assertFails {ReviewFollowupItem(s.oldX.id,s.nextX.id,"invented","Unknown state.")}
    }
}
