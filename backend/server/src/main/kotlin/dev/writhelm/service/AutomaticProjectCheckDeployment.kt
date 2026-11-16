// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import java.nio.file.*
import java.util.UUID

internal fun projectCheckExecutableDigest(file:Path):Digest {
    val md=java.security.MessageDigest.getInstance("SHA-256");var size=0L
    Files.newInputStream(file).use {input->val buffer=ByteArray(65536)
        while(true){val n=input.read(buffer);if(n<0)break;size+=n;require(size<=128L*1024*1024);md.update(buffer,0,n)}}
    return Digest.parse(md.digest().joinToString(""){"%02x".format(it)})
}

/** Trusted installer policy, never repository/model configuration. Enrollment binds only observed
 * paths; native runtime supplies its actual native project identity. No execution qualification. */
internal class AutomaticProjectCheckDeployment(private val installation:Path) {
    private val policies=installation.resolve("project-check-policies")
    private fun id(source:Path)=UUID.nameUUIDFromBytes("writhelm.project-check-policy.v1\u0000$source".toByteArray(Charsets.UTF_8))
    private fun templateId()=UUID.nameUUIDFromBytes("writhelm.project-check-template.v1".toByteArray())
    private fun validate(value:JsonValue):Pair<Path,ByteArray> {
        require(System.getProperty("os.name")=="Linux")
        val f=value.fields("formatVersion","platform","sourceRoot","envExecutable","checks")
        require(f.getValue("formatVersion").long()==1L && f.getValue("platform").string()=="linux")
        val source=Path.of(f.getValue("sourceRoot").string())
        require(source.isAbsolute && source.toRealPath()==source && Files.isDirectory(source,LinkOption.NOFOLLOW_LINKS) &&
            !source.startsWith(installation.parent) && !installation.parent.startsWith(source))
        fun pin(raw:JsonValue){val p=raw.fields("path","sha256");val file=Path.of(p.getValue("path").string())
            require(file.isAbsolute && file.toRealPath()==file && Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && Files.isExecutable(file) &&
                Files.size(file) in 1..128L*1024*1024 && (Files.getAttribute(file,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int) and 0x12==0 &&
                projectCheckExecutableDigest(file)==digest(p.getValue("sha256")))}
        pin(f.getValue("envExecutable"))
        val checks=f.getValue("checks").list();require(checks.size in 1..128)
        val ids=checks.map {raw->val c=raw.fields("id","executable","args","environment","timeoutMillis","outputBytes")
            pin(c.getValue("executable"));val key=c.getValue("id").string();require(key.isNotBlank() && key.length<=128)
            require(c.getValue("args").list().size<=128 && c.getValue("args").list().all {it.string().length<=16384 && '\u0000' !in it.string()})
            val env=(c.getValue("environment") as JObject).entries
            require(env.size<=64 && env.all {(name,v)->name.matches(Regex("[A-Z][A-Z0-9_]*")) &&
                !Regex("TOKEN|SECRET|AUTH|PASSWORD|CREDENTIAL|API_KEY|^LD_|^DYLD_|BASH_ENV|ENV$").containsMatchIn(name) && v.string().length<=16384 && '\u0000' !in v.string()})
            require(c.getValue("timeoutMillis").long() in 1..120000 && c.getValue("outputBytes").long() in 1..524288);key}
        require(ids.distinct().size==ids.size)
        return source to StrictJson.encode(value,1024*1024)
    }
    fun approve(values:List<JsonValue>){
        require(values.size<=128)
        val entries=values.map(::validate);require(entries.map {it.first}.distinct().size==entries.size)
        val custody=LinuxConnectedCustody(policies)
        entries.forEach {(source,bytes)->custody.retain(id(source),"command",bytes)}
    }
    fun provision(source:Path,controllerRoot:Path,parent:Path){
        val root=controllerRoot.resolve("bootstrap");LinuxConnectedCustody(root)
        val target=root.resolve("project-check-template-${Digest.of("current".toByteArray()).hex}.json")
        val policy=policies.resolve("${id(source)}.command")
        if(!Files.exists(policy,LinkOption.NOFOLLOW_LINKS)){
            require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)){"Approved project-check policy missing; previous deployment held"};return
        }
        val raw=protectedRuntimeBytes(policies,policy,1024*1024)
        val value=StrictJson.decode(raw,JsonLimits(bytes=1024*1024,stringBytes=262144,tokens=100000))
        require(validate(value).first==source && parent.toRealPath()==parent)
        val template=StrictJson.encode(JObject((value as JObject).entries+("worktreeParent" to str(parent.toString()))),1024*1024)
        val custody=LinuxConnectedCustody(controllerRoot.resolve("project-check-deployment"))
        custody.retain(templateId(),"command",template)
        // Same immutable publication primitive as passive deployment, without overwriting partial files.
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))require(protectedRuntimeBytes(root,target,1024*1024).contentEquals(template))
        else {
            java.nio.channels.FileChannel.open(target,setOf(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))).use {channel ->
                val buffer=java.nio.ByteBuffer.wrap(template);while(buffer.hasRemaining())channel.write(buffer);channel.force(true)}
            java.nio.channels.FileChannel.open(root,StandardOpenOption.READ).use {it.force(true)}
        }
        require(protectedRuntimeBytes(policies,policy,1024*1024).contentEquals(raw))
    }
    /** Pure current observation for qualification; never repairs or republishes missing policy. */
    fun observe(source:Path,controllerRoot:Path,parent:Path):Digest? {
        val root=controllerRoot.resolve("bootstrap")
        val target=root.resolve("project-check-template-${Digest.of("current".toByteArray()).hex}.json")
        val policy=policies.resolve("${id(source)}.command")
        if(!Files.exists(policy,LinkOption.NOFOLLOW_LINKS)&&!Files.exists(target,LinkOption.NOFOLLOW_LINKS))return null
        val bytes=protectedRuntimeBytes(policies,policy,1024*1024)
        val value=StrictJson.decode(bytes,JsonLimits(bytes=1024*1024,stringBytes=262144,tokens=100000))
        require(validate(value).first==source)
        val expected=StrictJson.encode(JObject((value as JObject).entries+("worktreeParent" to str(parent.toString()))),1024*1024)
        require(protectedRuntimeBytes(root,target,1024*1024).contentEquals(expected))
        require(protectedRuntimeBytes(policies,policy,1024*1024).contentEquals(bytes))
        return Digest.of(expected)
    }
}
