package app.freshtuber.data.subscriptions

import java.io.StringReader
import java.time.Instant
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/** Anonymous Atom feed. DTDs are rejected before parsing; external entities are never resolved. */
object RssParser {
    fun parse(xml: String, channelId: String, channelName: String): List<LocalVideo> {
        require(xml.length <= 2_000_000 && !xml.contains("<!DOCTYPE", ignoreCase = true))
        val result = mutableListOf<LocalVideo>()
        val text = StringBuilder()
        var entry = false
        var short = false
        var id = ""; var title = ""; var published = ""; var thumbnail: String? = null
        val handler = object : DefaultHandler() {
            override fun resolveEntity(publicId: String?, systemId: String?) = InputSource(StringReader(""))
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                text.setLength(0)
                if (qName == "entry") { entry = true; id = ""; title = ""; published = ""; thumbnail = null; short = false }
                if (entry && qName == "link" && attributes.getValue("href")?.contains("/shorts/") == true) short = true
                if (entry && qName == "media:thumbnail") thumbnail = attributes.getValue("url")?.takeIf { it.startsWith("https://") }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { text.append(ch, start, length) }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                if (!entry) return
                when (qName) {
                    "yt:videoId" -> id = text.toString().trim()
                    "title" -> title = text.toString().trim()
                    "published" -> published = text.toString().trim()
                    "entry" -> {
                        val timestamp = runCatching { Instant.parse(published).toEpochMilli() }.getOrNull()
                        if (id.matches(Regex("[A-Za-z0-9_-]{11}")) && title.isNotBlank() && timestamp != null)
                            result += LocalVideo(id, channelId, channelName, title, thumbnail ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg", timestamp, isShort = short)
                        entry = false
                    }
                }
            }
        }
        SAXParserFactory.newInstance().apply { isNamespaceAware = false }.newSAXParser().xmlReader.apply {
            contentHandler = handler
            entityResolver = handler
        }.parse(InputSource(StringReader(xml)))
        return result.distinctBy { it.id }.sortedByDescending { it.publishedAt }
    }
}
