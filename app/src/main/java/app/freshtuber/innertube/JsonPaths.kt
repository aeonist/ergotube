package app.freshtuber.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

// Small, forgiving accessors for InnerTube JSON. Every miss returns null instead of throwing,
// because YouTube changes response shapes often.

fun JsonElement?.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject

fun JsonElement?.arr(key: String): JsonArray? = (this as? JsonObject)?.get(key) as? JsonArray

fun JsonElement?.str(key: String): String? =
    ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

fun JsonElement?.bool(key: String): Boolean? =
    ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.booleanOrNull

/** Follows object keys; returns null on the first missing step. */
fun JsonElement?.path(vararg keys: String): JsonElement? {
    var current: JsonElement? = this
    for (key in keys) current = (current as? JsonObject)?.get(key) ?: return null
    return current
}

/** Text of a YouTube text object: `{"simpleText": …}` or `{"runs": [{"text": …}, …]}`. */
fun JsonElement?.text(): String? {
    val obj = this as? JsonObject ?: return null
    obj.str("simpleText")?.let { return it }
    obj.str("content")?.let { return it }
    val runs = obj.arr("runs") ?: return null
    return runs.mapNotNull { it.str("text") }.joinToString("").ifEmpty { null }
}

/** The widest thumbnail URL from `{"thumbnails": [...]}`; protocol-relative URLs get https. */
fun JsonElement?.bestThumbnailUrl(): String? {
    val thumbnails = arr("thumbnails") ?: return null
    val best = thumbnails.maxByOrNull { (it.path("width") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0 }
    val url = best.str("url") ?: return null
    return if (url.startsWith("//")) "https:$url" else url
}

/** The widest URL from a view-model image: `{"sources": [{"url", "width"}, …]}`. */
fun JsonElement?.bestSourceUrl(): String? {
    val sources = arr("sources") ?: return null
    val best = sources.maxByOrNull { (it.path("width") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0 }
    val url = best.str("url") ?: return null
    return if (url.startsWith("//")) "https:$url" else url
}

/** Integer field, whether YouTube sent it as a number or a string. */
fun JsonElement?.int(key: String): Int? =
    ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

/** Every object in this tree that has [key], depth-first. Used where nesting varies between clients. */
fun JsonElement?.findAll(key: String): List<JsonElement> {
    val out = mutableListOf<JsonElement>()
    fun walk(element: JsonElement?) {
        when (element) {
            is JsonObject -> {
                element[key]?.let(out::add)
                element.values.forEach(::walk)
            }
            is JsonArray -> element.forEach(::walk)
            else -> Unit
        }
    }
    walk(this)
    return out
}
