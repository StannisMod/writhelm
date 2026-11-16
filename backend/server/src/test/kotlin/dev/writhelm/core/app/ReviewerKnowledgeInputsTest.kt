// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.work.*
import dev.writhelm.service.retainedReviewerKnowledgeInputs
import org.junit.jupiter.api.Test
import kotlin.test.*

class ReviewerKnowledgeInputsTest {
    @Test fun onlyExactAvailablePinnedCanonicalSourcesBecomeKnowledgeFacts() {
        val f=JointFixture(includeKnowledgeInput=true)
        val task=f.base.work.getValue(project).task(f.task);val base=requireNotNull(task.base)
        val sources=task.criteria.flatMap {it.clauses}
        val expected=sources.map {(it as WSourceVariant0Value).value.reference}.toSet()
        assertEquals(expected,retainedReviewerKnowledgeInputs(f.base,project,base,sources))
        assertFails {retainedReviewerKnowledgeInputs(f.base,project,base.copy(knowledgeManifestDigest=Digest.of("changed manifest".toByteArray())),sources)}
        assertFails {retainedReviewerKnowledgeInputs(f.base,project,base.copy(knowledgeManifestId=java.util.UUID.randomUUID()),sources)}
        assertFails {retainedReviewerKnowledgeInputs(f.base,java.util.UUID.randomUUID(),base,sources)}
        val unavailable=WSourceVariant0Value(WSourceVariant0("knowledge",revision(99880).x()))
        assertFails {retainedReviewerKnowledgeInputs(f.base,project,base,sources+unavailable)}
    }
}
