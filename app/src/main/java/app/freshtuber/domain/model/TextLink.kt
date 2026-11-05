package app.freshtuber.domain.model

/** UTF-16 offsets, matching Kotlin strings and YouTube attributed-text command runs. */
@kotlinx.serialization.Serializable
data class TextLink(val start: Int, val end: Int, val url: String? = null, val seconds: Long? = null)
