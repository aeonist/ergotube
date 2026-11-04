package app.freshtuber.player

/** A named part of a video, from YouTube's chapters. */
data class Chapter(val startSeconds: Long, val title: String) {
    companion object {
        /** Sorted, inside the video, without duplicates; the first one starts at 0. */
        fun normalized(chapters: List<Chapter>, durationSeconds: Long): List<Chapter> {
            val inside = chapters.filter { it.startSeconds >= 0 && (durationSeconds <= 0 || it.startSeconds < durationSeconds) && it.title.isNotBlank() }
                .sortedBy { it.startSeconds }.distinctBy { it.startSeconds }
            if (inside.size < 2) return emptyList()
            return if (inside.first().startSeconds == 0L) inside else listOf(inside.first().copy(startSeconds = 0)) + inside.drop(1)
        }

        fun at(chapters: List<Chapter>, seconds: Long): Chapter? = chapters.lastOrNull { it.startSeconds <= seconds }
    }
}

/** YouTube's preview frames: sprite sheets of small pictures, one every [frameMs]. */
data class Storyboard(
    val urls: List<String>,
    val total: Int,
    val perRow: Int,
    val perColumn: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val frameMs: Int,
) {
    data class Frame(val url: String, val x: Int, val y: Int, val width: Int, val height: Int)

    /** The picture to show for [positionMs]. */
    fun frameAt(positionMs: Long): Frame? {
        if (urls.isEmpty() || total <= 0 || perRow <= 0 || perColumn <= 0) return null
        val index = (if (frameMs > 0) (positionMs / frameMs).toInt() else 0).coerceIn(0, total - 1)
        val perPage = perRow * perColumn
        val page = (index / perPage).coerceAtMost(urls.lastIndex)
        val inPage = index % perPage
        return Frame(urls[page], (inPage % perRow) * frameWidth, (inPage / perRow) * frameHeight, frameWidth, frameHeight)
    }

    companion object {
        /** The sharpest set that is still small enough for a preview. */
        fun pick(sets: List<Storyboard>): Storyboard? {
            val usable = sets.filter { it.urls.isNotEmpty() && it.frameWidth > 0 && it.frameHeight > 0 }
            return usable.filter { it.frameWidth <= 320 }.maxByOrNull { it.frameWidth } ?: usable.minByOrNull { it.frameWidth }
        }
    }
}
