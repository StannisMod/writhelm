// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*

/** Independent H->C laws shared by live candidate and stored-edge audit. No native assertions. */
internal fun validateContinuationEdge(before: WContinuations,after: WContinuations) {
    workRequire(after.generation==nextGeneration(before.generation) && before.phase!="resume-confirmed",WorkProblem.INVALID_TRANSITION)
    val mutable=setOf("generation","phase","stage","observations","evidence","delivery","nativeIntent","reason","effect")
    val a=(encodeWContinuations(before) as JObject).entries.filterKeys { it !in mutable }
    val b=(encodeWContinuations(after) as JObject).entries.filterKeys { it !in mutable }
    workRequire(StrictJson.encode(JObject(a)).contentEquals(StrictJson.encode(JObject(b))),WorkProblem.INVALID_TRANSITION)
    if(after.phase=="in-flight") {
        val stage=when(before.phase) { "prepared"->"environment";"environment-confirmed"->"delivery";"delivery-confirmed"->"resume";else->before.stage }
        workRequire(after.stage==stage && after.effect.state=="armed",WorkProblem.INVALID_TRANSITION)
        if(before.phase in setOf("held","unknown"))workRequire(before.effect.state=="not-performed" && after.effect.previousAttempt==before.effect.attemptId,WorkProblem.INVALID_TRANSITION)
        else workRequire(before.phase in setOf("prepared","environment-confirmed","delivery-confirmed"),WorkProblem.INVALID_TRANSITION)
    } else {
        workRequire(after.stage==before.stage && before.effect.state in setOf("armed","outcome-unknown") &&
            after.effect.attemptId==before.effect.attemptId && after.effect.requestKey==before.effect.requestKey && after.effect.requestDigest==before.effect.requestDigest,
            WorkProblem.INVALID_TRANSITION)
        when(after.phase) {
            "environment-confirmed" -> workRequire(after.stage=="environment" && after.effect.state=="confirmed",WorkProblem.INVALID_TRANSITION)
            "delivery-confirmed" -> workRequire(after.stage=="delivery" && after.effect.state=="confirmed" && after.delivery!=null && after.nativeIntent!=null,WorkProblem.INVALID_TRANSITION)
            "resume-confirmed" -> workRequire(after.stage=="resume" && after.effect.state=="confirmed" && after.delivery==before.delivery && after.nativeIntent==before.nativeIntent,WorkProblem.INVALID_TRANSITION)
            "unknown" -> workRequire(after.effect.state=="outcome-unknown",WorkProblem.INVALID_TRANSITION)
            "held" -> workRequire(after.effect.state in setOf(before.effect.state,"not-performed") && after.reason!=null,WorkProblem.INVALID_TRANSITION)
            else -> throw WorkFault(WorkProblem.INVALID_TRANSITION)
        }
    }
}
