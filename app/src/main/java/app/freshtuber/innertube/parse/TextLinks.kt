package app.freshtuber.innertube.parse

import app.freshtuber.domain.model.TextLink
import app.freshtuber.innertube.*
import kotlinx.serialization.json.JsonElement
import java.net.URI
import java.net.URLDecoder

/** Preserves actual endpoints behind shortened display URLs, then adds plain links/times. */
object TextLinks {
    private val web = Regex("(?i)\\b(?:https?://|www\\.)[^\\s<>]+")
    private val time = Regex("(?<![\\p{L}\\p{N}:])(?:\\d{1,3}:)?\\d{1,3}:[0-5]\\d(?![\\d:])")

    fun timestamp(value: String): Long? {
        val parts = value.split(':').map { it.toLongOrNull() ?: return null }
        if (parts.size !in 2..3 || parts.any { it < 0 } || parts.last() >= 60 || (parts.size == 3 && parts[1] >= 60)) return null
        return parts.fold(0L) { result, part -> result * 60 + part }
    }

    fun safeUrl(value: String): String? {
        val absolute = when { value.startsWith('/') -> "https://www.youtube.com$value"; value.startsWith("www.") -> "https://$value"; else -> value }
        val uri = runCatching { URI(absolute) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        if (uri.host.lowercase() in setOf("youtube.com", "www.youtube.com") && uri.path == "/redirect") {
            val target = query(uri, "q") ?: query(uri, "url") ?: return absolute
            return safeUrlWithoutRedirect(target)
        }
        return absolute
    }
    private fun safeUrlWithoutRedirect(value: String): String? = runCatching { URI(value) }.getOrNull()
        ?.takeIf { it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrBlank() }?.toString()
    private fun query(uri: URI, key: String): String? = uri.rawQuery?.split('&')?.firstOrNull { it.substringBefore('=') == key }
        ?.substringAfter('=', "")?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }

    private fun endpoint(command: JsonElement?, start: Int, end: Int, videoId: String?): TextLink? {
        val watch = command.obj("watchEndpoint")
        if (watch != null) {
            val id = watch.str("videoId") ?: videoId
            val seconds = watch.int("startTimeSeconds")?.toLong() ?: 0L
            if (id == videoId && seconds >= 0) return TextLink(start, end, seconds = seconds)
            if (id != null) return TextLink(start, end, url = "https://www.youtube.com/watch?v=$id&t=${seconds}s")
        }
        val raw = command.obj("urlEndpoint").str("url") ?: command.path("commandMetadata", "webCommandMetadata").str("url") ?: return null
        return safeUrl(raw)?.let { TextLink(start, end, url = it) }
    }

    fun attributed(source: JsonElement?, text: String, videoId: String? = null): List<TextLink> {
        val spans = mutableListOf<TextLink>()
        source.arr("commandRuns").orEmpty().forEach { run ->
            val start = run.int("startIndex") ?: return@forEach
            val length = run.int("length") ?: return@forEach
            val end = start.toLong() + length
            if (start < 0 || length <= 0 || end > text.length) return@forEach
            endpoint(run.path("onTap", "innertubeCommand"), start, end.toInt(), videoId)?.let(spans::add)
        }
        var offset = 0
        source.arr("runs").orEmpty().forEach { run ->
            val length = run.str("text").orEmpty().length
            if (length > 0 && offset + length <= text.length) endpoint(run.obj("navigationEndpoint"), offset, offset + length, videoId)?.let(spans::add)
            offset += length
        }
        return scan(text, videoId, spans)
    }

    fun scan(text: String, videoId: String? = null, explicit: List<TextLink> = emptyList()): List<TextLink> {
        val spans = explicit.filter { it.start >= 0 && it.end in (it.start + 1)..text.length }.map { span ->
            // Comment endpoints are parsed before their watch-page context is known.
            val uri = span.url?.let { runCatching { URI(it) }.getOrNull() }
            val id = if (uri?.host in setOf("youtube.com", "www.youtube.com", "m.youtube.com")) uri?.let { query(it, "v") } else null
            val seconds = uri?.let { query(it, "t") ?: query(it, "start") }?.removeSuffix("s")?.toLongOrNull()
            if (videoId != null && id == videoId && seconds != null && seconds >= 0) span.copy(url = null, seconds = seconds) else span
        }.toMutableList()
        fun free(start: Int, end: Int) = spans.none { start < it.end && end > it.start }
        web.findAll(text).forEach { match ->
            val value = match.value.trimEnd('.', ',', ';', '!', '?', ')', ']', '}', '…', '"', '\'')
            val end = match.range.first + value.length
            if (free(match.range.first, end)) safeUrl(value)?.let { url ->
                val uri = URI(url)
                val id = if (uri.host in setOf("youtu.be", "www.youtu.be")) uri.path.trim('/')
                    else if (uri.host in setOf("youtube.com", "www.youtube.com", "m.youtube.com")) query(uri, "v") else null
                val rawTime = query(uri, "t") ?: query(uri, "start")
                val seconds = rawTime?.removeSuffix("s")?.toLongOrNull()
                spans += if (id == videoId && id != null && seconds != null && seconds >= 0) TextLink(match.range.first, end, seconds = seconds)
                    else TextLink(match.range.first, end, url = url)
            }
        }
        time.findAll(text).forEach { match ->
            val end = match.range.last + 1
            if (free(match.range.first, end)) timestamp(match.value)?.let { spans += TextLink(match.range.first, end, seconds = it) }
        }
        return spans.sortedBy { it.start }
    }
}
