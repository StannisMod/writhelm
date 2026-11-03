// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth

import dev.writhelm.core.codec.*
import dev.writhelm.core.workflow.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest.dynamicTest
import kotlin.test.*

class AuthBoundaryTest {
    @Test fun headerExactGoldenAndMinimumDepth() {
        val expected="{\"formatVersion\":1,\"profileVersion\":1}".toByteArray()
        val codec=AuthRecordCodec(authLimits().copy(depth=1))
        assertContentEquals(expected,codec.header())
        codec.checkHeader(expected)
    }
    @TestFactory fun headerAdmissionFenceposts() = listOf(
        "tokens" to 6, "nameBytes" to 14, "collection" to 2, "bytes" to 38
    ).flatMap { (boundary,n) -> (-1..1).map { offset -> dynamicTest("header-$boundary-${n+offset}") {
        val limit=n+offset
        val limits=when(boundary) {
            "tokens" -> authLimits().copy(tokens=limit)
            "nameBytes" -> authLimits().copy(nameBytes=limit)
            "collection" -> authLimits().copy(collection=limit)
            else -> authLimits().copy(bytes=limit)
        }
        val codec=AuthRecordCodec(limits)
        val golden="{\"formatVersion\":1,\"profileVersion\":1}".toByteArray()
        if(offset<0) {
            assertEquals(CodecProblem.ADMISSION_LIMIT,assertFailsWith<CodecFault> { codec.checkHeader(golden) }.problem)
            assertEquals(CodecProblem.ADMISSION_LIMIT,assertFailsWith<CodecFault> { codec.header() }.problem)
        } else {
            assertContentEquals(golden,codec.header())
            codec.checkHeader(codec.header())
        }
    } } }

    @Test fun independentPhysicalKeyGoldens() {
        val codec=AuthRecordCodec()
        assertEquals("auth/records/principals/9e/9e2e9d1529ea6983d288bc8297e16a6a1b414d6eeb52c03e3326090e8aa88970.json",codec.path(AuthRecord.Principal(owner)))
        assertEquals("auth/records/grant-guards/6d/6d375e9dc659a6df809a319a0405a0f133dc7b4eb453b9ae20ed914350d482ca.json",codec.path(AuthRecord.Guard(Enrollment(owner,project),0)))
    }
    @Test fun wrongSecretAndVerifierAsSecretCannotAuthenticate() {
        val bad=CredentialVerifier.token(store,key,ByteArray(32) { 9 })
        assertNull(view().authorize(bad,project,"read"))
        assertNull(view().authorize(CredentialVerifier.token(store,key,verifier.bytes()),project,"read"))
        val parsed=CredentialVerifier.parse(token)!!; parsed.close()
        assertFailsWith<IllegalStateException> { parsed.verifies(credential()) }
    }
    @Test fun credentialGenerationOverflowAndIdReuseLeaveInputsUntouched() {
        val base=view()
        val cs=listOf(credential(),credential(memberKey,member,g=Long.MAX_VALUE))
        val v=AuthorityView.checked(store,base.principals,base.projects.values,cs,base.guards,base.policy)
        for(p in listOf(AuthPayload.RevokeCredential(memberKey,Long.MAX_VALUE),AuthPayload.RotateCredential(memberKey,Long.MAX_VALUE,id(8),verifier))) {
            assertEquals(AuthProblem.AUTH_GENERATION_EXHAUSTED,assertIs<AuthTransition.Rejected>(v.execute(token,command(p))).problem)
            assertEquals(CredentialStatus.ACTIVE,v.credentials.getValue(memberKey).status)
            assertEquals(Long.MAX_VALUE,v.credentials.getValue(memberKey).generation)
        }
        val revoked=success(base.execute(token,command(AuthPayload.RevokeCredential(memberKey,0)))).next
        assertEquals(AuthProblem.AUTH_DUPLICATE_CREDENTIAL,assertIs<AuthTransition.Rejected>(revoked.execute(token,command(AuthPayload.RotateCredential(key,0,memberKey,verifier)))).problem)
    }
    @Test fun foreignTargetsAreNotAvailableToProjectManager() {
        val base=view(); val foreign=id(10); val otherKey=id(11)
        val v=AuthorityView.checked(store,base.principals,base.projects.values+ProjectBinding(foreign,owner),base.credentials.values+credential(otherKey,j=foreign),base.guards+(Enrollment(owner,foreign) to 0L),
            AccessPolicy(base.policy.snapshotGrants()+Grant(owner,foreign,setOf(Capability.APPROVE,Capability.MANAGE))))
        for(p in listOf(AuthPayload.RevokeCredential(otherKey,0),AuthPayload.RotateCredential(otherKey,0,id(8),verifier))) assertEquals(AuthProblem.AUTH_TARGET_NOT_FOUND,assertIs<AuthTransition.Rejected>(v.execute(token,command(p))).problem)
        assertNull(v.authorize(token,foreign,"revoke_credential"))
        assertNull(v.authorize(wire(otherKey,owner),project,"revoke_credential"))
        assertEquals(AuthProblem.AUTH_TARGET_NOT_FOUND,assertIs<AuthTransition.Rejected>(v.execute(token,command(AuthPayload.ReplaceGrants(id(99),0,verifier,emptySet())))).problem)
    }
    @Test fun replayIsCurrentAndScopedWithoutRewritingOriginalProvenance() {
        val v=view(); val original=v.replayIdentity(wire(memberKey),project,"request_approval",id(5))!!
        val noPlan=success(v.execute(token,replace(v,setOf(Capability.READ)))).next
        assertNull(noPlan.replayIdentity(wire(memberKey),project,"request_approval",id(5)))
        val read=noPlan.replayIdentity(wire(memberKey),project,"read",id(5))!!
        assertEquals(member,read.principalId); assertNotEquals(original,read)
        assertNotEquals(original,v.replayIdentity(token,project,"request_approval",id(5)))
        assertNotEquals(original,v.replayIdentity(wire(memberKey),project,"request_approval",id(9)))
        val rotated=success(v.execute(token,command(AuthPayload.RotateCredential(memberKey,0,id(8),CredentialVerifier.digest(store,project,member,id(8),secret))))).next
        assertEquals(original,rotated.replayIdentity(wire(id(8)),project,"request_approval",id(5)))
        assertNull(rotated.replayIdentity(wire(memberKey),project,"request_approval",id(5)))
    }
    @Test fun unaffectedTargetGrantChangesDoNotInvalidateAnotherGuard() {
        val v=view(); val pending=replace(v,setOf(Capability.PLAN))
        val changed=success(v.execute(token,command(AuthPayload.ReplaceGrants(owner,0,v.grantDigest(owner,project),setOf(Capability.APPROVE,Capability.MANAGE))))).next
        val r=success(changed.execute(token,pending))
        assertEquals(setOf(Capability.PLAN),r.next.policy.snapshotGrants().first { it.principalId==member }.capabilities)
        assertEquals(1L,r.next.guards.getValue(Enrollment(owner,project)))
        assertNull(r.next.authorize(token,project,"read"))
        assertNotNull(r.next.authorize(token,project,"revoke_credential"))
    }
    @Test fun recordLimitFencepostsAndStrictNestedShapes() {
        val c=AuthRecordCodec(); val r=AuthRecord.CredentialRecord(credential()); val b=c.encode(r); val path=c.path(r)
        for(n in listOf(b.size,b.size+1)) assertContentEquals(b,AuthRecordCodec(authLimits().copy(bytes=n)).encode(r))
        assertFailsWith<CodecFault> { AuthRecordCodec(authLimits().copy(bytes=b.size-1)).encode(r) }
        assertFailsWith<CodecFault> { AuthRecordCodec(authLimits().copy(bytes=b.size-1)).decode(b,path) }
        val text=b.toString(Charsets.UTF_8)
        for(t in listOf(text.replace("\"status\":\"active\"","\"status\":\"ACTIVE\""),text.replace("\"generation\":0","\"generation\":0,\"generation\":1"),text.replace("\"generation\":0","\"generation\":-1"),text.replace("\"generation\":0","\"generation\":9223372036854775808"),text.replace("\"status\":\"active\"","\"status\":\"active\",\"role\":\"ADMIN\""),text.replace("\"verifierDigest\":\"","\"verifierDigest\":\"A"))) assertFailsWith<CodecFault> { c.decode(t.toByteArray(),path) }
    }
    @Test fun parsedResourceLimitsCountExactStringTokensDepthAndCollections() {
        val c=AuthCommandCodec(); val b=c.encode(command(AuthPayload.RevokeCredential(key,0)))
        // Largest decoded strings are UUIDs (36 UTF-8 bytes), envelope has five members,
        // two object containers, and 17 Jackson tokens including names and ends.
        for(n in listOf(36,37)) AuthCommandCodec(authLimits().copy(stringBytes=n)).decode(b)
        assertFailsWith<CodecFault> { AuthCommandCodec(authLimits().copy(stringBytes=35)).decode(b) }
        for(n in listOf(5,6)) AuthCommandCodec(authLimits().copy(collection=n)).decode(b)
        assertFailsWith<CodecFault> { AuthCommandCodec(authLimits().copy(collection=4)).decode(b) }
        for(n in listOf(2,3)) AuthCommandCodec(authLimits().copy(depth=n)).decode(b)
        assertFailsWith<CodecFault> { AuthCommandCodec(authLimits().copy(depth=1)).decode(b) }
        for(n in listOf(17,18)) AuthCommandCodec(authLimits().copy(tokens=n)).decode(b)
        assertFailsWith<CodecFault> { AuthCommandCodec(authLimits().copy(tokens=16)).decode(b) }
    }
}
