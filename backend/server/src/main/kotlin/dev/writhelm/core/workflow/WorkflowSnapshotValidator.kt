// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.workflow

import dev.writhelm.core.codec.CodecProblem
import dev.writhelm.core.codec.graph
import dev.writhelm.core.codec.refuse
import dev.writhelm.core.knowledge.*
import java.util.UUID

/** Structural/bidirectional checks only. Historical grants, ordering and first trace need trusted H. */
internal object WorkflowSnapshotValidator {
    fun restore(snapshot: WorkflowSnapshot, edges: Int, depth: Int): ApprovalWorkflow {
        validate(snapshot,edges,depth)
        return ApprovalWorkflow.restoreValidated(snapshot)
    }
    fun validate(s: WorkflowSnapshot, maxEdges: Int, maxDepth: Int) {
        require(maxEdges>0 && maxDepth>0)
        var edges=0
        fun edge() { if(edges==maxEdges)refuse(CodecProblem.RESOURCE_UNAVAILABLE); edges++ }
        val revisions=s.revisions.associateBy { it.key }; graph(revisions.size==s.revisions.size)
        graph(s.grants.map { it.principalId to it.projectId }.distinct().size==s.grants.size)
        graph(revisions.keys==s.proposals.keys)
        val catalog=RevisionCatalog(s.revisions)
        fun valid(ref: KnowledgeReference,project: UUID=ref.revision.document.projectId) {
            edge(); graph(ref.revision.document.projectId==project)
            graph(when(ref) { is ClauseReference -> catalog.read(ref) is ReadResult.Found; is ContextReference -> catalog.read(ref) is ReadResult.Found })
        }
        fun contexts(refs: List<KnowledgeReference>, selected: List<ContextReference>) {
            refs.filterIsInstance<ClauseReference>().forEach { r ->
                val revision=revisions[r.revision] ?: refuse(CodecProblem.INVALID_GRAPH)
                if(revision.structure.context.isNotEmpty())graph(revision.contextReference() in selected)
            }
        }
        fun bytes(ref: KnowledgeReference): List<Utf8Content> = when(ref) {
            is ClauseReference -> listOf((catalog.read(ref) as ReadResult.Found).value.located.content)
            is ContextReference -> (catalog.read(ref) as ReadResult.Found).value.fragments.map { it.content }
        }
        fun contextBytes(ref: KnowledgeReference): List<Utf8Content> = bytes(revisions.getValue(ref.revision).contextReference())
        graph(s.resolutions.keys.all { it in s.requests })
        s.requests.forEach { (id,r) ->
            edge(); graph(id==r.id)
            r.packet.references.forEach { valid(it,r.projectId) }; contexts(r.packet.references,r.packet.contexts)
            graph(r.generations.keys==r.packet.items.map { it.target.subject() }.toSet())
            graph(r.generations.values.all { it>=0 })
        }
        val approved=LinkedHashSet<KnowledgeReference>()
        val successful=LinkedHashMap<SubjectKey,MutableList<Pair<Long,ReviewItem>>>()
        s.resolutions.forEach { (id,r) ->
            edge(); graph(id==r.requestId)
            val request=s.requests.getValue(id)
            if(r.answer is StructuredAnswer.ChangesRequired)graph(r.answer.comments.keys.all { key -> request.packet.items.any { it.id==key } })
            val targets=if(r.answer==StructuredAnswer.Approve)request.packet.items.map { it.target } else emptyList()
            graph(r.approved==targets)
            if(r.answer==StructuredAnswer.Approve) {
                graph(request.packet.informationGaps.isEmpty())
                request.packet.items.forEach { item ->
                    edge(); approved.add(item.target)
                    successful.getOrPut(item.target.subject()) { ArrayList() }.add(request.generations.getValue(item.target.subject()) to item)
                }
            }
        }
        graph(s.approvals.keys==approved)
        s.approvals.forEach { (ref,t) ->
            valid(ref); graph(t.origin==ref && t.assessmentIds.isEmpty())
            val resolution=s.resolutions[t.requestId] ?: refuse(CodecProblem.INVALID_GRAPH)
            graph(resolution.answer==StructuredAnswer.Approve && ref in resolution.approved && resolution.actor.principalId==t.approvedBy)
        }
        graph(s.heads.keys==successful.keys && s.generations.keys==successful.keys)
        val chains=LinkedHashMap<SubjectKey,List<KnowledgeReference?>>()
        successful.forEach { (subject,steps) ->
            val ordered=steps.sortedBy { it.first }; val chain=ArrayList<KnowledgeReference?>(); chain.add(null)
            ordered.forEachIndexed { index,(guard,item) ->
                edge(); graph(guard==index.toLong() && item.before==chain.last() && item.target.subject()==subject)
                chain.add(item.target)
            }
            graph(s.generations[subject]==ordered.size.toLong() && s.heads[subject]==chain.last())
            chains[subject]=chain
        }
        // Pending/rejected guards may be old, but must name a retained historical cursor, not be rebound.
        s.requests.values.forEach { request -> request.packet.items.forEach { item ->
            edge(); val g=request.generations.getValue(item.target.subject()); val chain=chains[item.target.subject()]
            if(chain==null)graph(g==0L && item.before==null)
            else { graph(g<chain.size.toLong()); graph(item.before==chain[g.toInt()]) }
        } }
        val links=LinkedHashMap<KnowledgeReference,StoredContinuity>(); val blockers=LinkedHashSet<KnowledgeReference>()
        s.assessments.forEach { (id,record) ->
            edge(); val a=record.assessment; graph(id==a.id)
            a.references.forEach { valid(it,a.projectId) }
            contexts(a.references,a.references.filterIsInstance<ContextReference>())
            graph(a.blocked.all { it in a.references }); blockers.addAll(a.blocked)
            val batchTargets=a.links.map { it.target }.toSet()
            a.links.forEach { link ->
                edge(); graph(link.source in a.references && link.target in a.references)
                graph(link.source !in batchTargets)
                graph(link.source!=link.target && link.source.subject()==link.target.subject())
                graph(bytes(link.source)==bytes(link.target))
                if(link.source is ClauseReference)graph(contextBytes(link.source)==contextBytes(link.target))
                graph(link.target !in links); links[link.target]=StoredContinuity(link.source,id)
            }
        }
        graph(s.continuity==links && s.blocked==blockers)
        // Check retained links even when later direct approval now takes precedence.
        s.continuity.keys.forEach { start ->
            val seen=HashSet<KnowledgeReference>(); var current=start; var length=0
            while(true) {
                edge()
                graph(seen.add(current))
                val link=s.continuity[current] ?: break
                if(length++>=maxDepth)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
                current=link.source
            }
            graph(current in s.approvals)
        }
        fun historicalAuthority(start: KnowledgeReference) {
            val pending=ArrayDeque<Pair<KnowledgeReference,Int>>(); pending.add(start to 0)
            val seen=HashSet<KnowledgeReference>()
            while(pending.isNotEmpty()) {
                val (ref,depth)=pending.removeLast(); edge()
                if(depth>maxDepth)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
                if(!seen.add(ref))continue
                if(ref is ClauseReference) {
                    val revision=revisions.getValue(ref.revision)
                    if(revision.structure.context.isNotEmpty())pending.add(revision.contextReference() to depth+1)
                }
                if(ref !in s.approvals) {
                    val link=s.continuity[ref] ?: refuse(CodecProblem.INVALID_GRAPH)
                    pending.add(link.source to depth+1)
                }
            }
        }
        s.continuity.values.forEach { historicalAuthority(it.source) }
        // Successful dependencies require retained authority, not today's grants or blockers.
        s.resolutions.filterValues { it.answer==StructuredAnswer.Approve }.keys.forEach { id ->
            val request=s.requests.getValue(id); val targets=request.packet.items.map { it.target }.toSet()
            (request.packet.contexts+request.packet.dependencies).filter { it !in targets }.forEach(::historicalAuthority)
        }
        fun frozenTrace(ref: KnowledgeReference,t: ApprovalTrace) {
            var current=ref
            if(t.assessmentIds.size>maxDepth)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
            for(id in t.assessmentIds.asReversed()) {
                edge(); val link=s.continuity[current] ?: refuse(CodecProblem.INVALID_GRAPH)
                graph(link.assessmentId==id); current=link.source
            }
            graph(current==t.origin)
            val original=s.approvals[t.origin] ?: refuse(CodecProblem.INVALID_GRAPH)
            graph(t.requestId==original.requestId && t.approvedBy==original.approvedBy)
        }
        s.manifests.forEach { (id,m) ->
            edge(); graph(id==m.id && m.clauses.isNotEmpty())
            listOf(m.clauses,m.contexts,m.dependencies).forEach { graph(it.distinct().size==it.size) }
            val refs=(m.clauses+m.contexts+m.dependencies).distinct(); refs.forEach { valid(it,m.projectId) }; contexts(refs,m.contexts)
            graph(refs.filterIsInstance<ClauseReference>().groupBy { it.subject() }.values.all { it.distinct().size==1 })
            graph(refs.filterIsInstance<ContextReference>().groupBy { it.revision.document }.values.all { group -> group.map(::bytes).distinct().size==1 })
            val assessment=s.assessments[m.assessmentId]?.assessment ?: refuse(CodecProblem.INVALID_GRAPH)
            graph(assessment.projectId==m.projectId && assessment.references.containsAll(refs))
            graph(m.provenance.keys==refs.toSet())
            refs.forEach { ref -> historicalAuthority(ref); frozenTrace(ref,m.provenance.getValue(ref)) }
        }
    }
}
