// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.workflow.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest.dynamicTest
import java.util.UUID
import kotlin.test.*

internal fun id(n: Int) = UUID(0,n.toLong())
internal val store=id(1)
internal val project=id(2)
internal val owner=id(3)
internal val key=id(4)
internal val member=id(6)
internal val memberKey=id(7)
internal val secret=ByteArray(32) { it.toByte() } // Public deterministic fixture, never a live key.
internal const val token="wh1.00000000-0000-0000-0000-000000000001.00000000-0000-0000-0000-000000000004.AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
internal val verifier=Digest.parse("d7e7768bfbb2a58f9417759671900e7f9fc779700b0b00116f080d5825763cb2")
internal fun credential(k: UUID=key,p: UUID=owner,s: UUID=store,j: UUID=project,g: Long=0,status: CredentialStatus=CredentialStatus.ACTIVE,next: UUID?=null) = Credential(k,s,j,p,CredentialVerifier.digest(s,j,p,k,secret),status,g,next)
internal fun view(extra: List<Credential> = emptyList(),guard: Long=0,memberCaps: Set<Capability>?=setOf(Capability.READ,Capability.PLAN)): AuthorityView {
    val policy=AccessPolicy(listOf(Grant(owner,project,Capability.entries.toSet()))+listOfNotNull(memberCaps?.let { Grant(member,project,it) }))
    return AuthorityView.checked(store,listOf(owner,member),listOf(ProjectBinding(project,owner)),listOf(credential(),credential(memberKey,member))+extra,
        mapOf(Enrollment(owner,project) to 0L,Enrollment(member,project) to guard),policy)
}
internal fun wire(k: UUID,p: UUID=member)=CredentialVerifier.token(store,k,secret)
internal fun command(payload: AuthPayload,request: UUID=id(5),j: UUID=project)=AuthCommand(request,j,payload)
internal fun replace(v: AuthorityView,caps: Set<Capability>,generation: Long=v.guards.getValue(Enrollment(member,project)),digest: Digest=v.grantDigest(member,project)) = command(AuthPayload.ReplaceGrants(member,generation,digest,caps))
internal fun success(r: AuthTransition)=assertIs<AuthTransition.Success>(r)

class CredentialTest {
    @Test fun independentVerifierAndTokenGolden() {
        assertEquals(verifier,CredentialVerifier.digest(store,project,owner,key,secret))
        assertEquals(token,CredentialVerifier.token(store,key,secret))
        assertEquals(121,token.toByteArray().size)
        assertNotNull(view().authorize(token,project,"resolve_approval"))
    }
    @TestFactory fun malformedTokens() = listOf("",key.toString(),verifier.hex,token+"=",token+".x"," "+token,token+" ",token.replace("wh1","wh2"),token.dropLast(1)+"9",token.replace("000000000001","00000000000A"),token.replace("000000000001","000000000009"),token.replace("000000000004","000000000099"),token.dropLast(1),token+"\n").mapIndexed { i,t -> dynamicTest("AU03-token-$i") { assertNull(view().authorize(t,project,"read")) } }
    @TestFactory fun everyVerifierBindingParticipates() = (0..3).map { field -> dynamicTest("AU03-binding-$field") {
        val ids=mutableListOf(store,project,owner,key); ids[field]=id(99)
        assertNotEquals(verifier,CredentialVerifier.digest(ids[0],ids[1],ids[2],ids[3],secret))
    } }
    @Test fun noCapabilityImplicationAndNoForeignProject() {
        val v=view(memberCaps=setOf(Capability.PLAN)); val t=wire(memberKey)
        assertNotNull(v.authorize(t,project,"request_approval"))
        for(op in listOf("read","resolve_approval","revoke_access","replace_grants","ADMIN","HUMAN")) assertNull(v.authorize(t,project,op))
        assertNull(v.authorize(token,id(99),"read"))
    }
    @Test fun noSecretInRepresentationsOrFailures() {
        assertFalse(CredentialVerifier.parse(token).toString().contains(token.substringAfterLast('.')))
        assertFalse(view().toString().contains(token))
        assertFailsWith<IllegalArgumentException> { CredentialVerifier.token(store,key,ByteArray(31)) }.also { assertFalse(it.toString().contains(token)) }
    }
}

class AuthCodecTest {
    private val codec=AuthCommandCodec()
    private fun bytes()=codec.encode(command(AuthPayload.RevokeCredential(key,0)))
    @Test fun independentFingerprintGoldenAndRequestKeyExclusion() {
        val c=command(AuthPayload.RevokeCredential(key,0))
        assertEquals("fe4611fbb1e3038c17d417382462d3f72e3507508d64fb4594d8163cc22465f0",codec.fingerprint(c).hex)
        assertEquals(codec.fingerprint(c),codec.fingerprint(command(c.payload,id(8))))
        assertNotEquals(codec.fingerprint(c),codec.fingerprint(command(AuthPayload.RevokeCredential(key,1))))
        assertContentEquals(bytes(),codec.encode(codec.decode(bytes())))
        assertEquals(codec.fingerprint(c),codec.fingerprint(codec.decode((" \n"+bytes().toString(Charsets.UTF_8).replace("credentialId","credential\\u0049d")+"\t").toByteArray())))
    }
    @TestFactory fun strictMutations() : List<org.junit.jupiter.api.DynamicTest> {
        val s=bytes().toString(Charsets.UTF_8)
        val bad=listOf(s.replace("\"formatVersion\":2","\"formatVersion\":1"),s.replace("\"formatVersion\":2","\"formatVersion\":3"),s.replace("\"expectedGeneration\":0","\"expectedGeneration\":-0"),s.replace("\"expectedGeneration\":0","\"expectedGeneration\":1.0"),s.replace("\"expectedGeneration\":0","\"expectedGeneration\":1e0"),s.replace("\"expectedGeneration\":0","\"expectedGeneration\":9223372036854775808"),s.replace("\"expectedGeneration\":0","\"expectedGeneration\":-1"),s.replace("\"expectedGeneration\":0","\"expectedGeneration\":null"),s.replace("\"expectedGeneration\":0","\"expectedGeneration\":\"0\""),s.replace("\"expectedGeneration\":0","\"expectedGeneration\":0,\"expected\\u0047eneration\":0"),s.replace("\"expectedGeneration\":0","\"expectedGeneration\":0,\"actor\":\"HUMAN\""),s.replace("revoke_credential","revoke_access"),s+"{}",s.replace("revoke_credential","\\ud800"),s.replace("\"requestKey\"","\"actor\""))
        return bad.mapIndexed { i,t -> dynamicTest("CD02-AU09-shape-$i") { assertFailsWith<CodecFault> { codec.decode(t.toByteArray()) } } } + listOf(dynamicTest("invalid-UTF8") { assertFailsWith<CodecFault> { codec.decode(byteArrayOf(0xc0.toByte(),0x80.toByte())) } },dynamicTest("metadata-BOM") { assertFailsWith<CodecFault> { codec.decode(byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte())+bytes()) } })
    }
    @Test fun allPayloadsAndFrozenCapabilitySets() {
        val caps=mutableSetOf(Capability.READ,Capability.PLAN)
        val c=command(AuthPayload.ReplaceGrants(member,0,view().grantDigest(member,project),caps)); val b=codec.encode(c); caps.clear()
        assertContentEquals(b,codec.encode(c))
        for(p in listOf(c.payload,AuthPayload.RotateCredential(key,0,id(8),verifier))) assertContentEquals(codec.encode(command(p)),codec.encode(codec.decode(codec.encode(command(p)))))
        assertFailsWith<CodecFault> { codec.decode(b.toString(Charsets.UTF_8).replace("\"READ\"","\"READ\",\"READ\"").toByteArray()) }
    }
    @Test fun byteAdmissionFenceposts() {
        val b=bytes()
        for(n in listOf(b.size,b.size+1)) assertContentEquals(b,AuthCommandCodec(JsonLimits(bytes=n)).encode(codec.decode(b)))
        assertEquals(CodecProblem.ADMISSION_LIMIT,assertFailsWith<CodecFault> { AuthCommandCodec(JsonLimits(bytes=b.size-1)).decode(b) }.problem)
        assertFailsWith<CodecFault> { AuthCommandCodec(JsonLimits(bytes=b.size-1)).encode(codec.decode(b)) }
        assertFailsWith<CodecFault> { AuthCommandCodec(JsonLimits(depth=1)).decode(b) }
        assertFailsWith<CodecFault> { AuthCommandCodec(JsonLimits(collection=1)).decode(b) }
        assertFailsWith<CodecFault> { AuthCommandCodec(JsonLimits(tokens=1)).decode(b) }
        assertFailsWith<CodecFault> { AuthCommandCodec(JsonLimits(stringBytes=35)).decode(b) }
    }
    @Test fun allRecordsCanonicalAndPathBound() {
        val c=AuthRecordCodec()
        val records=listOf<AuthRecord>(AuthRecord.Principal(owner),AuthRecord.Project(ProjectBinding(project,owner)),AuthRecord.CredentialRecord(credential()),AuthRecord.Guard(Enrollment(owner,project),Long.MAX_VALUE))
        for(r in records) {
            val b=c.encode(r); val path=c.path(r)
            assertTrue(path.startsWith("auth/records/")); assertContentEquals(b,c.encode(c.decode(b,path)))
            assertEquals(CodecProblem.INVALID_GRAPH,assertFailsWith<CodecFault> { c.decode(b,path+"x") }.problem)
            assertFailsWith<CodecFault> { c.decode(b.toString(Charsets.UTF_8).replace("\"formatVersion\":1","\"formatVersion\":2").toByteArray(),path) }
        }
        assertEquals("{\"formatVersion\":1,\"profileVersion\":1}",c.header().toString(Charsets.UTF_8)); c.checkHeader(c.header())
        assertFailsWith<CodecFault> { c.checkHeader("{\"formatVersion\":1,\"profileVersion\":2}".toByteArray()) }
        val r=AuthRecord.CredentialRecord(credential()); val b=c.encode(r).toString(Charsets.UTF_8)
        for(t in listOf(b.replace("\"replacedBy\":null","\"replacedBy\":\"${id(8)}\""),b.replace("\"replacedBy\":null,",""),b.replace("\"verifierVersion\":1","\"verifierVersion\":2"))) assertFailsWith<CodecFault> { c.decode(t.toByteArray(),c.path(r)) }
    }
}

class AuthorityTest {
    @Test fun independentGrantDigestsAndPresence() {
        assertEquals("2dae767505deeeed7470cfbaef15782e20c4d6f6c0c7e3b75da826dc1847930b",grantDigest(project,owner,null).hex)
        assertEquals("b0773415aab6318b2b02718412840ee862c6eb1cc4d72fe8ce420cdc0912a502",grantDigest(project,owner,emptySet()).hex)
        assertEquals("92bfc4a10b3e99ba7fd841420d8128c6f5294c00f7c00b9913f4561a96129de8",grantDigest(project,owner,setOf(Capability.READ,Capability.PLAN)).hex)
    }
    @Test fun replaceRevokeRegrantABAAndImmutableOriginal() {
        val v=view(); val original=v.grantDigest(member,project); val stale=replace(v,setOf(Capability.PLAN))
        val a=success(v.revokeAccess(token,project,member)); assertEquals(1,a.next.guards.getValue(Enrollment(member,project)))
        assertNull(a.next.authorize(wire(memberKey),project,"read")); assertNotNull(v.authorize(wire(memberKey),project,"read"))
        val b=success(a.next.execute(token,replace(a.next,setOf(Capability.READ,Capability.PLAN))))
        assertEquals(original,b.next.grantDigest(member,project)); assertEquals(2,b.next.guards.getValue(Enrollment(member,project)))
        assertEquals(AuthProblem.AUTH_STALE_GRANT,assertIs<AuthTransition.Rejected>(b.next.execute(token,stale)).problem)
        assertNotNull(a.grantPatch); assertEquals(null,a.grantPatch!!.capabilities)
        val empty=success(b.next.execute(token,replace(b.next,emptySet()))).next
        assertNotEquals(a.next.grantDigest(member,project),empty.grantDigest(member,project))
    }
    @Test fun targetedGenerationAndDigestBothChecked() {
        val v=view()
        for(c in listOf(replace(v,emptySet(),1),replace(v,emptySet(),digest=verifier))) assertEquals(AuthProblem.AUTH_STALE_GRANT,assertIs<AuthTransition.Rejected>(v.execute(token,c)).problem)
        assertEquals(AuthProblem.AUTH_GENERATION_EXHAUSTED,assertIs<AuthTransition.Rejected>(view(guard=Long.MAX_VALUE).execute(token,replace(view(guard=Long.MAX_VALUE),emptySet()))).problem)
    }
    @Test fun fixedOwnerAndLastUsableKey() {
        val v=view()
        for(c in listOf(replace(v,setOf(Capability.APPROVE)),replace(v,setOf(Capability.MANAGE)),command(AuthPayload.ReplaceGrants(owner,0,v.grantDigest(owner,project),setOf(Capability.READ))))) assertEquals(AuthProblem.AUTH_FIXED_OWNER_POLICY,assertIs<AuthTransition.Rejected>(v.execute(token,c)).problem)
        assertEquals(AuthProblem.AUTH_OWNER_LOCKOUT,assertIs<AuthTransition.Rejected>(v.execute(token,command(AuthPayload.RevokeCredential(key,0)))).problem)
        assertEquals(WorkflowProblem.LAST_AUTHORITY,assertIs<AuthTransition.CoreRejected>(v.revokeAccess(token,project,owner)).problem)
        val two=view(listOf(credential(id(8))))
        val r=success(two.execute(token,command(AuthPayload.RevokeCredential(key,0))))
        assertNull(r.next.authorize(token,project,"read")); assertNotNull(r.next.authorize(wire(id(8),owner),project,"read"))
    }
    @Test fun atomicRotationOneSuccessorAndCurrentReplayKeySpace() {
        val v=view(); val c=command(AuthPayload.RotateCredential(key,0,id(8),CredentialVerifier.digest(store,project,owner,id(8),secret)))
        val r=success(v.execute(token,c)); val t=wire(id(8),owner)
        assertNull(r.next.authorize(token,project,"read")); assertNotNull(r.next.authorize(t,project,"read")); assertNotNull(v.authorize(token,project,"read"))
        assertEquals(id(8),r.next.credentials.getValue(key).replacedBy); assertEquals(1,r.next.credentials.getValue(key).generation)
        assertEquals(0,r.next.credentials.getValue(id(8)).generation)
        assertEquals(AuthProblem.AUTH_ALREADY_REPLACED,assertIs<AuthTransition.Rejected>(r.next.execute(t,command(AuthPayload.RotateCredential(key,1,id(9),verifier)))).problem)
        val replay=r.next.replayIdentity(t,project,"rotate_credential",id(5))!!
        assertEquals(owner,replay.principalId); assertEquals(id(8),r.authenticatedByCredentialId.let { r.next.authorize(t,project,"read")!!.credentialId }); assertEquals(key,r.authenticatedByCredentialId)
        assertNull(r.next.replayIdentity(token,project,"rotate_credential",id(5)))
    }
    @Test fun managerRotatingMemberDoesNotGiveManagerReceiptSpace() {
        val r=success(view().execute(token,command(AuthPayload.RotateCredential(memberKey,0,id(8),CredentialVerifier.digest(store,project,member,id(8),secret)))))
        assertNotNull(r.next.replayIdentity(token,project,"rotate_credential",id(5)))
        assertNull(r.next.replayIdentity(wire(id(8)),project,"rotate_credential",id(5)))
        assertEquals(member,r.next.authorize(wire(id(8)),project,"read")!!.actor.principalId)
    }
    @Test fun revokedCredentialCanRotateButCannotReactivateOrBranch() {
        val v=success(view().execute(token,command(AuthPayload.RevokeCredential(memberKey,0)))).next
        assertEquals(AuthProblem.AUTH_ALREADY_REVOKED,assertIs<AuthTransition.Rejected>(v.execute(token,command(AuthPayload.RevokeCredential(memberKey,1)))).problem)
        val r=success(v.execute(token,command(AuthPayload.RotateCredential(memberKey,1,id(8),verifier))))
        assertEquals(CredentialStatus.REVOKED,r.next.credentials.getValue(memberKey).status)
        assertEquals(CredentialStatus.ACTIVE,r.next.credentials.getValue(id(8)).status)
    }
    @Test fun staleMissingDuplicateForeignAndForbiddenAreAtomic() {
        val v=view()
        val cases=listOf(AuthPayload.RevokeCredential(memberKey,1) to AuthProblem.AUTH_STALE_CREDENTIAL,AuthPayload.RevokeCredential(id(99),0) to AuthProblem.AUTH_TARGET_NOT_FOUND,AuthPayload.RotateCredential(memberKey,0,key,verifier) to AuthProblem.AUTH_DUPLICATE_CREDENTIAL)
        for((p,e) in cases) assertEquals(e,assertIs<AuthTransition.Rejected>(v.execute(token,command(p))).problem)
        assertIs<AuthTransition.Denied>(v.execute(wire(memberKey),command(AuthPayload.RevokeCredential(id(99),0))))
        assertIs<AuthTransition.Denied>(v.execute(token,command(AuthPayload.RevokeCredential(memberKey,0),j=id(99))))
        assertEquals(0,v.credentials.getValue(memberKey).generation)
    }
    @Test fun corruptStartupGraphsAndNoImplicitEnrollment() {
        val base=view()
        fun check(cs: List<Credential> = base.credentials.values.toList(), ps: List<ProjectBinding> = base.projects.values.toList(), es: Map<Enrollment,Long> = base.guards, policy: AccessPolicy=base.policy) { assertFailsWith<CodecFault> { AuthorityView.checked(store,listOf(owner,member),ps,cs,es,policy) } }
        check(cs=listOf(credential(status=CredentialStatus.REVOKED),credential(memberKey,member)))
        check(cs=listOf(credential(s=id(99)),credential(memberKey,member)))
        check(cs=base.credentials.values.toList()+credential(id(9),id(99)))
        check(es=base.guards-Enrollment(member,project)); check(ps=listOf(ProjectBinding(project,id(99))))
        check(cs=listOf(credential(status=CredentialStatus.REVOKED,next=memberKey),credential(memberKey,member)))
        check(cs=listOf(credential(status=CredentialStatus.REVOKED,next=id(8)),credential(id(8),status=CredentialStatus.REVOKED,next=key),credential(memberKey,member)))
        check(cs=base.credentials.values.toList()+credential())
        check(policy=AccessPolicy(listOf(Grant(owner,project,Capability.entries.toSet()),Grant(member,project,setOf(Capability.MANAGE)))))
    }
    @Test fun stateCollectionsAreFrozen() {
        val v=view(); assertFails { (v.credentials as MutableMap).clear() }; assertFails { (v.guards as MutableMap).clear() }
        val caps=mutableSetOf(Capability.READ); val c=AuthPayload.ReplaceGrants(member,0,v.grantDigest(member,project),caps); caps.add(Capability.MANAGE)
        assertEquals(setOf(Capability.READ),c.capabilities); assertFails { (c.capabilities as MutableSet).clear() }
    }
}
