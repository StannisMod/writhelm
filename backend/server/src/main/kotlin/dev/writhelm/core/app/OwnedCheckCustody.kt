// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import java.nio.file.*
import java.util.UUID

internal data class CapturedFileCheck(val runId: UUID,val subject: WorkReference,val actualDigest: Digest,
    val outcome: String,val report: Utf8Content,val environment: Utf8Content,val significantInputs: List<WSignificantInput>)

/** A real small mechanical check, not a producer 'passed' assertion. Explicit owned areas/retained
 * reports are configured by the service; there is no raw path or evidence upload route. Semantic
 * resolution still belongs to the designated assessor. Other check methods need their own adapters. */
internal class OwnedCheckCustody(private val reportRoot: Path,areas: Map<UUID,Path>) {
    private val roots=areas.toMap()
    private data class Run(val capture: CapturedFileCheck,val file: Path,val report: Path)
    private val runs=linkedMapOf<UUID,Run>()
    private val bound=linkedMapOf<WorkReference,WEvidence>()
    init { require(reportRoot.isAbsolute && !Files.isSymbolicLink(reportRoot));Files.createDirectories(reportRoot) }
    @Synchronized fun compare(area: UUID,relative: String,expected: Utf8Content,subject: WorkReference,runId: UUID,
        significantInputs: List<WSignificantInput>): CapturedFileCheck {
        require(subject.identity.family==WorkFamily.RESULTS && runId !in runs)
        require(relative.isNotEmpty() && !relative.startsWith('/') && '\\' !in relative && relative.split('/').none { it in setOf(".","..","") })
        val root=roots[area] ?: throw WorkFault(WorkProblem.NOT_READY)
        var file=root
        relative.split('/').forEach { part ->file=file.resolve(part);require(!Files.isSymbolicLink(file)) }
        require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && Files.size(file)<=4*1024*1024)
        val actual=Utf8Content.fromBytes(Files.readAllBytes(file))
        val outcome=if(actual==expected)"passed" else "failed"
        val report=Utf8Content.fromBytes(StrictJson.encode(obj("runId" to uuid(runId),"method" to str("owned-file-byte-comparison-v1"),
            "subject" to StrictJson.decode(identityCodec.encodeReference(subject)),"actualDigest" to digest(actual.digest),
            "expectedDigest" to digest(expected.digest),"outcome" to str(outcome),"areaId" to uuid(area),"relative" to str(relative),
            "significantInputs" to JArray(significantInputs.map(::encodeWSignificantInput)))))
        val environment=Utf8Content.fromText("Portable direct file-byte comparison; actual ${System.getProperty("os.name")}; no native/model execution")
        val path=reportRoot.resolve("$runId.json")
        Files.write(path,report.bytes(),StandardOpenOption.CREATE_NEW)
        val capture=CapturedFileCheck(runId,subject,actual.digest,outcome,report,environment,significantInputs.toList())
        runs[runId]=Run(capture,file,path);return capture
    }
    @Synchronized fun bind(reference: WorkReference,evidence: WEvidence) {
        require(reference.identity.family==WorkFamily.EVIDENCE)
        val run=runs[evidence.runId] ?: throw WorkFault(WorkProblem.NOT_READY)
        val c=run.capture
        workRequire(evidence.outcome==c.outcome && evidence.boundary=="pure" && evidence.report==WorkBodyReference(c.report.digest,c.report.sizeBytes) &&
            evidence.environment==WorkBodyReference(c.environment.digest,c.environment.sizeBytes) &&
            evidence.subjects==listOf(WSourceVariant2Value(WSourceVariant2("work",c.subject))) &&
            evidence.significantInputs==c.significantInputs,WorkProblem.INVALID_SUBJECT)
        bound[reference]=decodeWEvidence(encodeWEvidence(evidence))
    }
    @Synchronized fun observed(): Set<WorkReference> = bound.filter { (_,evidence) ->
        val run=runs[evidence.runId] ?: return@filter false
        try {
            !Files.isSymbolicLink(run.file) && !Files.isSymbolicLink(run.report) && Files.size(run.file)<=4*1024*1024 && Files.size(run.report)<=1048576 &&
                Digest.of(Files.readAllBytes(run.file))==run.capture.actualDigest && Files.readAllBytes(run.report).contentEquals(run.capture.report.bytes())
        } catch(_: Exception) { false }
    }.keys.toSet()
    /** Rebuild disposable run/ref indexes from canonical exact evidence + protected retained bytes.
     * No unique authority or source locator is left solely in a process cache. */
    @Synchronized fun observed(work: WorkState): Set<WorkReference> {
        var bytes=0L
        work.records.values.forEach { record ->
            val evidence=(record.payload as? WEvidencePayload)?.value ?: return@forEach
            if(evidence.method!="owned-file-byte-comparison-v1" || evidence.boundary!="pure")return@forEach
            val reference=WorkReference(record.identity,WorkCodec().digest(record))
            if(reference in bound)return@forEach
            try {
                val reportFile=reportRoot.resolve("${evidence.runId}.json")
                if(Files.isSymbolicLink(reportFile) || Files.size(reportFile)>1048576)return@forEach
                val raw=Files.readAllBytes(reportFile)
                if(raw.size>64L*1024*1024-bytes)return@forEach
                bytes+=raw.size
                val report=Utf8Content.fromBytes(raw)
                if(report.digest!=evidence.report.digest || report.sizeBytes!=evidence.report.byteLength)return@forEach
                val f=StrictJson.decode(raw).fields("runId","method","subject","actualDigest","expectedDigest","outcome","areaId","relative","significantInputs")
                if(uuid(f.getValue("runId"))!=evidence.runId || f.getValue("method").string()!=evidence.method)return@forEach
                val area=roots[uuid(f.getValue("areaId"))] ?: return@forEach
                val relative=f.getValue("relative").string()
                if(relative.isEmpty() || relative.startsWith('/') || '\\' in relative || relative.split('/').any { it in setOf(".","..","") })return@forEach
                var file=area
                relative.split('/').forEach { part ->file=file.resolve(part);require(!Files.isSymbolicLink(file)) }
                val environment=work.bodies[evidence.environment.digest] ?: return@forEach
                val subject=identityCodec.decodeReference(StrictJson.encode(f.getValue("subject")))
                val capture=CapturedFileCheck(evidence.runId,subject,digest(f.getValue("actualDigest")),f.getValue("outcome").string(),report,environment,
                    f.getValue("significantInputs").list().map(::decodeWSignificantInput))
                runs[evidence.runId]=Run(capture,file,reportFile)
                bind(reference,evidence)
            } catch(_: Exception) { /* Missing/changed custody is not observed evidence. No fabricated pass. */ }
        }
        return observed()
    }
}
