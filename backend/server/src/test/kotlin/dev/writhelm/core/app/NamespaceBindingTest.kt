// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class NamespaceBindingTest {
    @Test fun `frozen H1 authority projection explicitly refuses candidate model3`() {
        val oid=GitOid("sha1","0".repeat(40))
        val project=id(90)
        assertEquals(headBasis(oid),h1WorkProjection(oid,mapOf(project to WorkState.empty(project))).basis)
        assertEquals(StoreProblem.INTEGRITY,assertFailsWith<StoreFault> {
            h1WorkProjection(oid,mapOf(project to WorkState.empty(project,modelVersion=3)))
        }.problem)
    }
    @Test fun `actual namespace startup refuses shape valid credential with foreign enrollment`() {
        val dir=directory(); val g=genesis(); val codec=AuthRecordCodec()
        val invalid=AuthRecord.CredentialRecord(g.authority.credentials.getValue(plannerKey).copy(principalId=id(99)))
        // Valid individual auth shape, invalid whole-store enrollment graph. Build only a NEW
        // disposable negative genesis via native plumbing, never a loader/patch client endpoint.
        assertIs<AuthRecord.CredentialRecord>(codec.decode(codec.encode(invalid),codec.path(invalid)))
        RuntimeOwner.acquire(dir).use { owner ->
            val git=NativeGit(owner); git.qualify(); git.initialize()
            val files=AppBinding(storeId,AppOptions()).genesis(g).associate { it.path to it.bytes() }.toMutableMap()
            files[codec.path(invalid)]=codec.encode(invalid)
            files["commands/format.json"]=StrictJson.encode(obj("formatVersion" to num(2)))
            files["policy/admission.json"]=AdmissionPolicy().bytes()
            files["store.json"]=StrictJson.encode(obj("formatVersion" to num(1),"storeId" to uuid(storeId),"gitObjectFormat" to str("sha1"),
                "namespaces" to obj("core" to CoreRecordCodec.header(),"auth" to StrictJson.decode(codec.header()),"commands" to obj("formatVersion" to num(2)),"policy" to obj("formatVersion" to num(1)))))
            val objects=files.mapValues { git.blob(it.value) }
            fun tree(prefix: String): GitOid {
                val names=objects.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }.distinct().sorted()
                val bytes=names.joinToString("") { name ->
                    val path=prefix+name; val oid=objects[path]
                    if(oid==null)"040000 tree ${tree("$path/").hex}\t$name\u0000" else "100644 blob ${oid.hex}\t$name\u0000"
                }.toByteArray(Charsets.US_ASCII)
                return git.oid(git.call(listOf("mktree","-z"),bytes).checked())
            }
            git.update(null,git.commit(tree(""),null)).checked()
        }
        assertEquals(CodecProblem.INVALID_GRAPH,assertFailsWith<CodecFault> { MechanismApp.open(dir,storeId) }.problem)
    }
}
