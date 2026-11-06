// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import org.junit.jupiter.api.Test

/** Parser/policy boundaries only. Fixture-specific typed admission, never a production codec stub. */
class H1AttachmentAdmissionTest {
    private val limits=JsonLimits(bytes=2*1024*1024,stringBytes=65536,depth=32,collection=1024,tokens=100000)
    private val template: JObject by lazy {
        StrictJson.decode(Files.readAllBytes(Path.of(checkNotNull(System.getenv("WRITHELM_H1_BODY_PROBE_ROOT"))).resolve("valid-checkpoint-70000.json")),limits.copy(stringBytes=1048576)) as JObject
    }
    private fun fields(v: JsonValue)=(v as JObject).entries
    private fun replace(v: JObject,key: String,value: JsonValue)=JObject(v.entries+(key to value))
    private fun payload(v: JObject)=v.entries.getValue("payload") as JObject
    private fun data(v: JObject)=payload(v).entries.getValue("data") as JObject
    private fun fixture(body: String,description: String?=null): JObject {
        val digest=sha(body.toByteArray(Charsets.UTF_8))
        val report=obj("digest" to str(digest),"byteLength" to num(body.toByteArray(Charsets.UTF_8).size))
        val steps=if(description==null)JArray(emptyList()) else JArray(listOf(obj("id" to template.entries.getValue("projectId"),"description" to str(description),"state" to str("pending"),"obligations" to JArray(emptyList()),"blockers" to JArray(emptyList()))))
        val d=replace(replace(data(template),"report",report),"planSteps",steps)
        return replace(template,"payload",replace(replace(payload(template),"data",d),"bodies",JArray(listOf(obj("digest" to str(digest),"body" to str(body))))))
    }
    private fun binding(canonicalize: (ByteArray)->ByteArray)=H1PublicationBindings(canonicalize,
        { _,_ -> error("parser only") },{ _,_ -> error("parser only") },{ _,_,_ -> error("parser only") },{ _,_,_ -> error("parser only") },{ _,_,_ -> error("parser only") })
    /** Accept only the parent's closed checkpoint vector with correctly rebound body/report and
     * optional closed step. Everything else is refused; no arbitrary-payload identity callback. */
    private fun typedFixture(raw: ByteArray): ByteArray {
        val v=StrictJson.decodeWorkCommand(raw,limits,1048576) as JObject
        val p=payload(v); val attachment=p.entries.getValue("bodies").list().single().fields("digest","body")
        val body=attachment.getValue("body").string()
        val digest=sha(body.toByteArray(Charsets.UTF_8)); assertEquals(digest,attachment.getValue("digest").string())
        val report=data(v).entries.getValue("report").fields("digest","byteLength")
        assertEquals(digest,report.getValue("digest").string()); assertEquals(body.toByteArray(Charsets.UTF_8).size,report.getValue("byteLength").int())
        val steps=data(v).entries.getValue("planSteps").list()
        val description=if(steps.isEmpty())null else steps.single().fields("id","description","state","obligations","blockers").getValue("description").string()
        val expected=fixture(body,description)
        shape(StrictJson.encode(expected).contentEquals(StrictJson.encode(v)))
        return StrictJson.encode(v)
    }
    private fun decode(v: JObject)=StoreCommand.decodeH1(StrictJson.encode(v),binding(::typedFixture))
    private fun policy()=AdmissionPolicy(formatVersion=2)
    private fun preliminaryRefuses(v: JObject) {
        var reached=false
        assertFailsWith<CodecFault> { StoreCommand.decodeH1(StrictJson.encode(v),binding { reached=true; error("must refuse before typed codec") }) }
        assertFalse(reached)
    }

    @Test fun `parent valid70k body and body N minus1 N plus1 exercise decoder canonical parse and policy`() {
        val parent=StoreCommand.decodeH1(StrictJson.encode(template),binding(::typedFixture)); policy().command(parent)
        for(n in listOf(1048575,1048576))policy().command(decode(fixture("a".repeat(n))))
        preliminaryRefuses(fixture("a".repeat(1048577)))
        val custom=policy().copy(workBodyBytes=70000)
        for(n in listOf(69999,70000))custom.command(decode(fixture("a".repeat(n))))
        assertFailsWith<CodecFault> { custom.command(decode(fixture("a".repeat(70001)))) }
        val smallerThanOrdinary=policy().copy(workBodyBytes=7,workStringBytes=1000)
        for(n in listOf(6,7))smallerThanOrdinary.command(decode(fixture("a".repeat(n),"d".repeat(1000))))
        assertFailsWith<CodecFault> { smallerThanOrdinary.command(decode(fixture("a".repeat(8)))) }
    }

    @Test fun `ordinary string exact edges retain separate default and tightened policy caps`() {
        for(n in listOf(65535,65536))policy().command(decode(fixture("a".repeat(70000),"d".repeat(n))))
        preliminaryRefuses(fixture("a".repeat(70000),"d".repeat(65537)))
        val custom=policy().copy(workStringBytes=1000,workBodyBytes=70000)
        for(n in listOf(999,1000))custom.command(decode(fixture("a".repeat(70000),"d".repeat(n))))
        assertFailsWith<CodecFault> { custom.command(decode(fixture("a".repeat(70000),"d".repeat(1001)))) }
    }

    @Test fun `UTF8 bytes not scalar UTF16 or escaped length select ordinary and body caps`() {
        val custom=policy().copy(workStringBytes=1000,workBodyBytes=70000)
        for(body in listOf("é".repeat(35000),"😀".repeat(17500),"\u0001".repeat(70000)))custom.command(decode(fixture(body,"😀".repeat(250))))
        for(body in listOf("é".repeat(35000)+"a","😀".repeat(17500)+"a","\u0001".repeat(70001)))assertFailsWith<CodecFault> { custom.command(decode(fixture(body))) }
        assertFailsWith<CodecFault> { custom.command(decode(fixture("a".repeat(70000),"😀".repeat(250)+"a"))) }
        // Six-byte JSON control escaping remains charged to the encoded command, not body bytes.
        preliminaryRefuses(fixture("\u0001".repeat(400000)))
    }

    @Test fun `only exact attachment path is exempt never another body member or wrong container`() {
        val v=fixture("a"); val huge=str("x".repeat(65537))
        val bad=listOf(
            replace(v,"body",huge),
            replace(v,"payload",replace(payload(v),"body",huge)),
            replace(v,"payload",replace(payload(v),"data",replace(data(v),"body",huge))),
            replace(v,"payload",replace(payload(v),"bodies",obj("body" to huge))),
            replace(v,"payload",replace(payload(v),"bodies",JArray(listOf(JArray(listOf(obj("body" to huge))))))),
            replace(v,"payload",replace(payload(v),"bodies",JArray(listOf(obj("nested" to obj("body" to huge),"body" to str("a"),"digest" to str(sha("a".toByteArray())))))))
        )
        bad.forEach(::preliminaryRefuses)
        // A small unknown member passes preliminary resource parsing, but mandatory typed admission
        // still refuses it. A body exception is not a closed-schema or reference-validation bypass.
        assertFails { decode(replace(v,"payload",replace(payload(v),"body",str("small")))) }
        val p=payload(v); val d=replace(data(v),"report",obj("digest" to str("0".repeat(64)),"byteLength" to num(1)))
        assertFails { decode(replace(v,"payload",replace(p,"data",d))) }
    }

    @Test fun `policy applies exact path exception and all encoded collection depth token caps`() {
        val v=fixture("a".repeat(70000)); val c=decode(v)
        val encoded=c.bytes().size
        policy().copy(workCommandBytes=encoded).command(c)
        assertFails { policy().copy(workCommandBytes=encoded-1).command(c) }
        for(tight in listOf(policy().copy(workCollectionItems=1),policy().copy(workDepth=3),policy().copy(workTokens=10)))assertFailsWith<CodecFault> { tight.command(c) }
        // A trusted codec returning an oversized or misplaced ordinary string cannot bypass the
        // SECOND bounded parse either; this deliberately faulty test codec must be rejected.
        for(value in listOf(fixture("a".repeat(1048577)),fixture("a","x".repeat(65537)))) {
            assertFailsWith<CodecFault> { StoreCommand.decodeH1(StrictJson.encode(fixture("a")),binding { StrictJson.encode(value) }) }
        }
        // Policy also rejects nonbody > custom ordinary cap even though initial decoder permits it.
        assertFails { policy().copy(workStringBytes=128).command(decode(fixture("a".repeat(70000),"x".repeat(129)))) }
    }

    @Test fun `AUTH3 has no attachment exception in presented or canonical command`() {
        val work=fixture("a")
        fun auth(body: String)=replace(replace(work,"operation",str("register_delegation")),"payload",obj("bodies" to JArray(listOf(obj("body" to str(body))))))
        var reached=false
        assertFailsWith<CodecFault> { StoreCommand.decodeH1(StrictJson.encode(auth("a".repeat(1025))),binding { reached=true; it }) }
        assertFalse(reached)
        assertFailsWith<CodecFault> { StoreCommand.decodeH1(StrictJson.encode(auth("a")),binding { StrictJson.encode(auth("a".repeat(1025))) }) }
    }

    @Test fun `body consumes unchanged aggregate tokens and UTF8 bytes and escaped field aliases stay strict`() {
        val raw=StrictJson.encode(fixture("é".repeat(35000)))
        var genericTokens=0L; var genericBytes=0L
        StrictJson.decode(raw,limits.copy(stringBytes=1048576),consume={ t,b -> genericTokens+=t; genericBytes+=b })
        var tokens=0L; var bytes=0L
        StrictJson.decodeWorkCommand(raw,limits,1048576,consume={ t,b -> tokens+=t; bytes+=b })
        assertEquals(genericTokens,tokens); assertEquals(genericBytes,bytes); assertTrue(bytes>70000)
        var consumed=0L
        assertFailsWith<CodecFault> { StrictJson.decodeWorkCommand(raw,limits,1048576,consume={ _,b ->
            if(b>genericBytes-1-consumed)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
            consumed+=b
        }) }
        val original=StoreCommand.decodeH1(raw,binding(::typedFixture))
        val escaped=raw.toString(Charsets.UTF_8).replace("\"body\":","\"b\\u006fdy\":").toByteArray()
        assertEquals(original.fingerprint,StoreCommand.decodeH1(escaped,binding(::typedFixture)).fingerprint)
        val duplicate=raw.toString(Charsets.UTF_8).replace("\"body\":","\"b\\u006fdy\":\"duplicate\",\"body\":").toByteArray()
        assertFailsWith<CodecFault> { StoreCommand.decodeH1(duplicate,binding(::typedFixture)) }
    }
}
