// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

import org.junit.jupiter.api.Test
import kotlin.test.*

class WorkProtocolTest {
    @Test fun retainedWorkContractIsPackagedAndItsClosedRequiredListsAreSatisfiable() {
        val contract=WorkProtocol.exported() as JObject
        assertEquals(34,(contract.entries.getValue("commands") as JObject).entries.size)
        assertEquals(34,(contract.entries.getValue("reads") as JObject).entries.size)
        val definitions=(contract.entries.getValue("\$defs") as JObject).entries
        fun verify(v:JsonValue){
            when(v){
                is JObject->{
                    if(v.entries["type"]==str("object")&&v.entries["additionalProperties"]==JBoolean(false)){
                        val properties=(v.entries["properties"] as? JObject)?.entries.orEmpty()
                        v.entries["required"]?.list()?.forEach {assertTrue(it.string() in properties)}
                    }
                    v.entries.values.forEach(::verify)
                }
                is JArray->v.values.forEach(::verify)
                else->Unit
            }
        }
        definitions.values.forEach(::verify)
        for(name in listOf("work-model2--step","work-model2--pending")){
            val properties=((definitions.getValue(name) as JObject).entries.getValue("properties") as JObject).entries
            assertTrue("description" in properties)
        }
    }
}
