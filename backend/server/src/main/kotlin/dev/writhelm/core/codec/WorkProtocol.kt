// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

/**
 * Interpreter for the finite retained structural JSON Schema vocabulary. No filesystem/network ref
 * resolution and no executable source. WorkCodec still owns exact numeric/token/byte limits, domain
 * construction, same-project and semantic laws. Every exported model shape is this same resource.
 */
internal object WorkProtocol {
    private val contract:JObject by lazy {
        val raw=WorkProtocol::class.java.getResourceAsStream("/writhelm/model-work-v1.json")
            ?.use {it.readBytes()} ?: error("Missing packaged work protocol")
        StrictJson.decode(raw,JsonLimits(bytes=2*1024*1024,collection=8192,tokens=200000)) as JObject
    }
    private val definitions get()=(contract.entries.getValue("\$defs") as JObject).entries
    private fun table(kind:String)=(contract.entries.getValue(kind) as JObject).entries
    fun exported():JsonValue=contract
    fun validate(kind:String,operation:String,value:JsonValue) {
        val schema=table(kind)[operation] ?: return // Internal/non-model operations retain their separate codecs.
        validateSchema(schema,value)
    }
    internal fun validateSchema(schema:JsonValue,value:JsonValue) {
        var nodes=0
        fun equal(a:JsonValue,b:JsonValue):Boolean=when(a) {
            is JObject -> b is JObject&&a.entries.keys==b.entries.keys&&a.entries.all {(key,x)->equal(x,b.entries.getValue(key))}
            is JArray -> b is JArray&&a.values.size==b.values.size&&a.values.indices.all {equal(a.values[it],b.values[it])}
            else -> a==b
        }
        fun check(schema:JsonValue,v:JsonValue,depth:Int=0):Boolean {
            if(++nodes>200000||depth>64)return false
            if(schema is JBoolean)return schema.value
            val s=(schema as? JObject)?.entries ?: return false
            if(s.keys.any {it !in setOf("\$ref","additionalProperties","allOf","anyOf","const","else","enum","if","items",
                "maxItems","maxLength","maximum","minItems","minLength","minimum","oneOf","pattern",
                "properties","required","then","type","uniqueItems")})return false
            s["\$ref"]?.let {
                val name=it.string().removePrefix("#/\$defs/")
                if(!check(definitions[name] ?: return false,v,depth+1))return false
            }
            s["const"]?.let {if(!equal(it,v))return false}
            s["enum"]?.let {if(it.list().none {x->equal(x,v)})return false}
            s["allOf"]?.let {if(!it.list().all {x->check(x,v,depth+1)})return false}
            s["anyOf"]?.let {if(!it.list().any {x->check(x,v,depth+1)})return false}
            s["oneOf"]?.let {if(it.list().count {x->check(x,v,depth+1)}!=1)return false}
            s["if"]?.let {condition->
                val selected=if(check(condition,v,depth+1))s["then"] else s["else"]
                if(selected!=null&&!check(selected,v,depth+1))return false
            }
            val type=s["type"]?.string()
            when(type) {
                "null" -> if(v!==JNull)return false
                "boolean" -> if(v !is JBoolean)return false
                "integer" -> if(v !is JNumber)return false
                "string" -> if(v !is JString)return false
                "array" -> if(v !is JArray)return false
                "object" -> if(v !is JObject)return false
                null -> Unit
                else -> return false
            }
            // Type-specific keywords apply to matching instances even without a local type.
            when(v) {
                is JNumber -> {
                    if(s["minimum"]?.let {v.value<it.long()}==true||s["maximum"]?.let {v.value>it.long()}==true)return false
                }
                is JString -> {
                    val size=v.value.codePointCount(0,v.value.length)
                    if(s["minLength"]?.let {size<it.long()}==true||s["maxLength"]?.let {size>it.long()}==true)return false
                    if(s["pattern"]?.let {!Regex(it.string()).containsMatchIn(v.value)}==true)return false
                }
                is JArray -> {
                    if(s["minItems"]?.let {v.values.size<it.long()}==true||s["maxItems"]?.let {v.values.size>it.long()}==true)return false
                    if(s["uniqueItems"]==JBoolean(true)&&v.values.map {StrictJson.encode(it).toList()}.distinct().size!=v.values.size)return false
                    if(s["items"]?.let {item->v.values.any {!check(item,it,depth+1)}}==true)return false
                }
                else -> Unit
            }
            if(v is JObject) {
                if(s["required"]?.list()?.any {it.string() !in v.entries}==true)return false
                val properties=(s["properties"] as? JObject)?.entries ?: emptyMap()
                for((key,x)in v.entries){
                    val rule=properties[key]
                    if(rule!=null){if(!check(rule,x,depth+1))return false}
                    else if(s["additionalProperties"]==JBoolean(false))return false
                }
            }
            return true
        }
        shape(check(schema,value))
    }
}
