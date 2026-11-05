package app.freshtuber.player

import java.util.Locale

/**
 * A DASH manifest built from YouTube's own stream list, so the player can pick the quality
 * from the connection speed and switch it while playing, like YouTube's apps do. Every stream
 * is one file with a header and an index (`initRange`, `indexRange`), which DASH describes
 * as `SegmentBase`; the player then loads the file in small ranges, one fragment at a time.
 * The layout follows NewPipeExtractor's YoutubeProgressiveDashManifestCreator, with all
 * qualities of a codec in one adaptation set.
 */
object YoutubeDash {

    /** Whether these streams can go into a manifest: all of them have their byte ranges. */
    fun usable(video: List<VideoTrack>, audio: AudioTrack?): Boolean =
        audio?.ranges != null && video.isNotEmpty() && video.all { it.ranges != null && it.itag > 0 && it.codecs != null }

    fun manifest(video: List<VideoTrack>, audio: AudioTrack, durationMs: Long): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" type=\"static\"")
        append(" minBufferTime=\"PT1.500S\" mediaPresentationDuration=\"").append(duration(durationMs)).append("\">")
        append("<Period>")
        // One adaptation set per codec: the player only adapts between streams of one codec.
        video.groupBy { it.codecMime ?: it.mimeType }.values.forEachIndexed { set, tracks ->
            append("<AdaptationSet id=\"").append(set).append("\" contentType=\"video\" subsegmentAlignment=\"true\">")
            tracks.sortedBy { it.bitrate }.forEach { track ->
                append("<Representation id=\"").append(track.itag).append('"')
                append(" mimeType=\"").append(escape(track.mimeType ?: "video/mp4")).append('"')
                append(" codecs=\"").append(escape(track.codecs!!)).append('"')
                append(" bandwidth=\"").append(track.bitrate.coerceAtLeast(1)).append('"')
                if (track.width > 0) append(" width=\"").append(track.width).append('"')
                append(" height=\"").append(track.height).append('"')
                if (track.fps > 0) append(" frameRate=\"").append(track.fps).append('"')
                append('>')
                append("<BaseURL>").append(escape(track.url)).append("</BaseURL>")
                segmentBase(track.ranges!!)
                append("</Representation>")
            }
            append("</AdaptationSet>")
        }
        append("<AdaptationSet id=\"").append(video.map { it.codecMime ?: it.mimeType }.distinct().size).append("\" contentType=\"audio\">")
        append("<Representation id=\"").append(audio.itag).append('"')
        append(" mimeType=\"").append(escape(audio.mimeType ?: "audio/mp4")).append('"')
        audio.codecs?.let { append(" codecs=\"").append(escape(it)).append('"') }
        append(" bandwidth=\"").append(audio.bitrate.coerceAtLeast(1)).append('"')
        if (audio.sampleRate > 0) append(" audioSamplingRate=\"").append(audio.sampleRate).append('"')
        append('>')
        if (audio.channels > 0) append("<AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"")
            .append(audio.channels).append("\"/>")
        append("<BaseURL>").append(escape(audio.url)).append("</BaseURL>")
        segmentBase(audio.ranges!!)
        append("</Representation></AdaptationSet>")
        append("</Period></MPD>")
    }

    private fun StringBuilder.segmentBase(ranges: SegmentRanges) {
        append("<SegmentBase indexRange=\"").append(ranges.indexStart).append('-').append(ranges.indexEnd).append("\">")
        append("<Initialization range=\"").append(ranges.initStart).append('-').append(ranges.initEnd).append("\"/>")
        append("</SegmentBase>")
    }

    private fun duration(ms: Long): String = String.format(Locale.US, "PT%.3fS", ms.coerceAtLeast(1) / 1000.0)

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }
}
