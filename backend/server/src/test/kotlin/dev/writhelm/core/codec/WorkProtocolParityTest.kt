// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

import kotlin.test.*
import org.junit.jupiter.api.Test

class WorkProtocolParityTest {
    private val defs get()=(WorkProtocol.exported() as JObject).entries.getValue("\$defs") as JObject
    private fun definition(name:String)=defs.entries.getValue(name)
    private fun json(text:String)=StrictJson.decode(text.toByteArray())
    private fun valid(schema:JsonValue,value:JsonValue)=WorkProtocol.validateSchema(schema,value)
    private fun invalid(schema:JsonValue,value:JsonValue) {
        assertEquals(CodecProblem.INVALID_SHAPE,assertFailsWith<CodecFault> { valid(schema,value) }.problem)
    }
    // Produce structurally valid counterparts directly from retained declarations, not domain checks.
    private fun example(schema:JsonValue):JsonValue {
        val s=(schema as JObject).entries
        s["\$ref"]?.let { return example(definition(it.string().removePrefix("#/\$defs/"))) }
        s["const"]?.let { return it }
        s["enum"]?.let { return it.list().first() }
        s["oneOf"]?.let { return example(it.list().first()) }
        return when(s["type"]?.string()) {
            "null" -> JNull
            "boolean" -> JBoolean(false)
            "integer" -> s["minimum"] ?: num(0)
            "string" -> {
                val pattern=s["pattern"]?.string()
                str(when {
                    pattern == "^[0-9a-f]{64}$" -> "0".repeat(64)
                    pattern!=null -> "00000000-0000-4000-8000-000000000001"
                    else -> "x".repeat((s["minLength"]?.long() ?: 1).toInt())
                })
            }
            "array" -> JArray(List((s["minItems"]?.long() ?: 0).toInt()) { example(s.getValue("items")) })
            "object" -> {
                val properties=(s.getValue("properties") as JObject).entries
                JObject(s["required"]!!.list().associate { it.string() to example(properties.getValue(it.string())) })
            }
            else -> error("No example for schema")
        }
    }
    private fun changed(value:JsonValue,vararg fields:Pair<String,JsonValue>)=
        JObject((value as JObject).entries+fields)

    @Test fun retainedExecutableTaskCardinality() {
        val schema=definition("work-model2--task-revisions")
        val draft=example(schema)
        valid(schema,draft)
        valid(schema,changed(draft,"unresolved" to JArray(listOf(str("open")))))
        val executable=changed(draft,"state" to str("executable"),
            "base" to example(definition("work-model2--base")),
            "executionProfile" to example(definition("work-model2--profileKey")),
            "verificationPlan" to example(definition("work-model2--revisionKey")))
        valid(schema,executable)
        invalid(schema,changed(executable,"unresolved" to JArray(listOf(str("open")))))
    }

    @Test fun retainedRetrospectiveBothBranches() {
        val schema=definition("work-commands-v3--record_retrospective")
        val payload=example(schema)
        val data=(payload as JObject).entries.getValue("data")
        val dataSchema=(((schema as JObject).entries.getValue("properties") as JObject).entries.getValue("data") as JObject)
        val observations=(((dataSchema.entries.getValue("properties") as JObject).entries.getValue("observations") as JObject))
        val one=JArray(listOf(example(observations.entries.getValue("items"))))
        valid(schema,payload)
        invalid(schema,changed(payload,"data" to changed(data,"observations" to one)))
        invalid(schema,changed(payload,"data" to changed(data,"outcome" to str("findings"))))
        valid(schema,changed(payload,"data" to changed(data,"outcome" to str("findings"),"observations" to one)))
    }

    @Test fun constraintOnlyKeywordsAndTypeApplicability() {
        for((schema,bad,good) in listOf(
            Triple("""{"maxItems":0}""","[1]","[]"),
            Triple("""{"minItems":1,"items":{"type":"integer"}}""","[]","[1]"),
            Triple("""{"minLength":2,"maxLength":2,"pattern":"^ab"}""","\"a\"","\"ab\""),
            Triple("""{"minimum":2,"maximum":3}""","1","2"))) {
            invalid(json(schema),json(bad)); valid(json(schema),json(good))
        }
        valid(json("""{"maxItems":0}"""),str("not an array"))
        valid(json("""{"minimum":2}"""),JNull)
        invalid(json("""{"type":"array"}"""),str("not an array"))
    }

    @Test fun uniquenessIsStructuralAndArraysRemainOrdered() {
        val schema=json("""{"uniqueItems":true}""")
        invalid(schema,json("""[{"a":1,"b":[2]},{"b":[2],"a":1}]"""))
        invalid(schema,json("""[[1,2],[1,2]]"""))
        invalid(schema,json("""[1,1]"""))
        valid(schema,json("""[[1,2],[2,1],1,"1",true,null]"""))
        valid(schema,str("scalar"))
    }

    @Test fun supportedCombinatorsReferencesAndUnsupportedVocabulary() {
        val any=json("""{"anyOf":[{"type":"null"},{"type":"integer"}]}""")
        valid(any,JNull); valid(any,num(1)); invalid(any,str("x"))
        invalid(json("""{"oneOf":[{},{}]}"""),num(1))
        valid(json("""{"allOf":[{"minimum":1},{"maximum":2}]}"""),num(1))
        invalid(json("""{"allOf":[{"minimum":1},{"maximum":2}]}"""),num(3))
        invalid(json("""{"not":{}}"""),num(1))
        invalid(json("""{"type":"number"}"""),num(1))
        invalid(json("""{"${'$'}ref":"#/${'$'}defs/missing"}"""),num(1))
        invalid(json("""{"${'$'}ref":"#/${'$'}defs/core-codec-v1--generation","maximum":0}"""),num(1))
        valid(JBoolean(true),JNull); invalid(JBoolean(false),JNull)
    }
}
