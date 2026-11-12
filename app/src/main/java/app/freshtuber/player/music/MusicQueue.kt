package app.freshtuber.player.music

import app.freshtuber.data.music.MusicTrack
import kotlinx.serialization.Serializable

/** Where a queued song came from; the queue shows and shuffles them separately. */
@Serializable
enum class QueueLayer { Added, Collection, Radio }

@Serializable
enum class RepeatMode { Off, All, One;
    fun next(): RepeatMode = entries[(ordinal + 1) % entries.size]
}

@Serializable
data class QueueEntry(val track: MusicTrack, val layer: QueueLayer)

/**
 * The music queue in three layers: songs the listener sent with «Играть следующей» play
 * first and are never shuffled, then the collection they started from (album, playlist, jam,
 * daily mix; «Перемешать» only reorders this one), then pale radio suggestions while
 * «Бесконечно» is on. Pure and serializable, so it survives restarts and is easy to test.
 */
@Serializable
data class MusicQueue(
    val current: QueueEntry? = null,
    val added: List<MusicTrack> = emptyList(),
    val collection: List<MusicTrack> = emptyList(),
    val radio: List<MusicTrack> = emptyList(),
    /** Played before [current], oldest first; Previous goes back here. */
    val history: List<QueueEntry> = emptyList(),
    /** The whole collection in its own order, for Repeat all and switching shuffle off. */
    val collectionAll: List<MusicTrack> = emptyList(),
    /** Name of the collection («Микс дня», a playlist), for «Из … · 2 из 12». */
    val title: String? = null,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val endless: Boolean = true,
    val positionMs: Long = 0,
) {
    val track: MusicTrack? get() = current?.track
    val upcoming: List<QueueEntry>
        get() = added.map { QueueEntry(it, QueueLayer.Added) } + collection.map { QueueEntry(it, QueueLayer.Collection) } +
            radio.map { QueueEntry(it, QueueLayer.Radio) }

    /** The song that plays after this one, respecting Repeat. */
    val next: MusicTrack?
        get() = when {
            current == null -> null
            repeat == RepeatMode.One -> current.track
            else -> advance(explicit = false)?.track
        }

    /** 1-based place of [current] in the collection, or null when it is not from it. */
    val collectionPosition: Pair<Int, Int>?
        get() {
            val entry = current ?: return null
            if (entry.layer != QueueLayer.Collection || collectionAll.isEmpty()) return null
            val index = collectionAll.indexOfFirst { it.id == entry.track.id }
            return if (index < 0) null else (index + 1) to collectionAll.size
        }

    /**
     * Moves to the next song. [explicit] is a press of Next: it leaves a single-song repeat.
     * Returns null at the end of everything.
     */
    fun advance(explicit: Boolean = true): MusicQueue? {
        val entry = current ?: return null
        if (!explicit && repeat == RepeatMode.One) return copy(positionMs = 0)
        val played = (history + entry).takeLast(MAX_HISTORY)
        var state = copy(history = played, positionMs = 0)
        // The same song never plays twice in a row.
        fun List<MusicTrack>.fresh() = dropWhile { it.id == entry.track.id }
        state = state.copy(added = state.added.fresh(), collection = state.collection.fresh())
        if (state.added.isNotEmpty()) return state.copy(current = QueueEntry(state.added.first(), QueueLayer.Added), added = state.added.drop(1))
        if (state.collection.isEmpty() && repeat == RepeatMode.All && collectionAll.isNotEmpty()) {
            // Seeded, so the «Дальше» preview and the real step agree.
            val again = if (shuffle) collectionAll.shuffled(kotlin.random.Random(collectionAll.hashCode() + history.size)) else collectionAll
            state = state.copy(collection = again.fresh())
        }
        if (state.collection.isNotEmpty()) {
            return state.copy(current = QueueEntry(state.collection.first(), QueueLayer.Collection), collection = state.collection.drop(1))
        }
        val radio = state.radio.fresh()
        if (radio.isNotEmpty() && endless) return state.copy(current = QueueEntry(radio.first(), QueueLayer.Radio), radio = radio.drop(1))
        return null
    }

    /** Back to the song before; the current one returns to the front of its layer. */
    fun back(): MusicQueue? {
        val previous = history.lastOrNull() ?: return null
        val entry = current ?: return copy(current = previous, history = history.dropLast(1), positionMs = 0)
        val requeued = when (entry.layer) {
            QueueLayer.Added -> copy(added = listOf(entry.track) + added)
            QueueLayer.Collection -> copy(collection = listOf(entry.track) + collection)
            QueueLayer.Radio -> copy(radio = listOf(entry.track) + radio)
        }
        return requeued.copy(current = previous, history = history.dropLast(1), positionMs = 0)
    }

    /**
     * Back to the [index]th played song (0 is the oldest in [history]). The songs after it,
     * the current one included, return to the front of their layers in their order, so going
     * back is the same as pressing Previous that many times.
     */
    fun backTo(index: Int): MusicQueue? {
        if (index !in history.indices) return null
        var state: MusicQueue = this
        repeat(history.size - index) { state = state.back() ?: return null }
        return state
    }

    /** Shuffle reorders only the collection's songs still to come. */
    fun withShuffle(on: Boolean): MusicQueue {
        if (on == shuffle) return this
        val ids = collection.map { it.id }.toSet()
        val reordered = if (on) collection.shuffled() else collectionAll.filter { it.id in ids }.ifEmpty { collection }
        return copy(shuffle = on, collection = reordered)
    }

    fun withEndless(on: Boolean): MusicQueue = copy(endless = on, radio = if (on) radio else emptyList())

    fun playNext(track: MusicTrack): MusicQueue = copy(added = listOf(track) + added.filterNot { it.id == track.id })

    /** «В конец очереди»: after the collection, before the radio. */
    fun playLast(track: MusicTrack): MusicQueue =
        copy(collection = collection.filterNot { it.id == track.id } + track, collectionAll = collectionAll.filterNot { it.id == track.id } + track)

    fun remove(layer: QueueLayer, index: Int): MusicQueue = when (layer) {
        QueueLayer.Added -> copy(added = added.without(index))
        QueueLayer.Collection -> copy(collection = collection.without(index))
        QueueLayer.Radio -> copy(radio = radio.without(index))
    }

    /** Puts a removed song back where it was. */
    fun restore(layer: QueueLayer, index: Int, track: MusicTrack): MusicQueue = when (layer) {
        QueueLayer.Added -> copy(added = added.inserted(index, track))
        QueueLayer.Collection -> copy(collection = collection.inserted(index, track))
        QueueLayer.Radio -> copy(radio = radio.inserted(index, track))
    }

    /** Reorders within one layer; songs never cross layers by dragging. */
    fun move(layer: QueueLayer, from: Int, to: Int): MusicQueue = when (layer) {
        QueueLayer.Added -> copy(added = added.moved(from, to))
        QueueLayer.Collection -> copy(collection = collection.moved(from, to))
        QueueLayer.Radio -> copy(radio = radio.moved(from, to))
    }

    /** Plays the [index]th upcoming song now; the ones before it in the list are skipped. */
    fun jump(index: Int): MusicQueue? {
        val list = upcoming
        val target = list.getOrNull(index) ?: return null
        val skipped = list.take(index)
        val played = current?.let { (history + it).takeLast(MAX_HISTORY) } ?: history
        fun List<MusicTrack>.after(layer: QueueLayer): List<MusicTrack> {
            val gone = skipped.count { it.layer == layer } + if (target.layer == layer) 1 else 0
            return drop(gone)
        }
        return copy(current = target, history = played, positionMs = 0,
            added = added.after(QueueLayer.Added), collection = collection.after(QueueLayer.Collection), radio = radio.after(QueueLayer.Radio))
    }

    /** Radio songs to add from the last song, without the ones already queued or just played. */
    fun withRadio(tracks: List<MusicTrack>): MusicQueue {
        val known = (upcoming.map { it.track.id } + history.takeLast(30).map { it.track.id } + listOfNotNull(current?.track?.id)).toSet()
        return copy(radio = radio + tracks.filterNot { it.id in known }.distinctBy { it.id })
    }

    /** True when «Бесконечно» should fetch more: the listener's own songs are about to run out. */
    val needsRadio: Boolean
        get() = endless && current != null && added.isEmpty() && collection.size <= 1 && radio.size < 3 &&
            !(repeat == RepeatMode.All && collectionAll.isNotEmpty())

    /** Replaces a song everywhere (a favourite flag or metadata changed). */
    fun updated(track: MusicTrack): MusicQueue {
        fun List<MusicTrack>.swap() = map { if (it.id == track.id) track else it }
        return copy(current = current?.let { if (it.track.id == track.id) it.copy(track = track) else it },
            added = added.swap(), collection = collection.swap(), radio = radio.swap(), collectionAll = collectionAll.swap(),
            history = history.map { if (it.track.id == track.id) it.copy(track = track) else it })
    }

    /** Skips the given songs (unavailable) from the upcoming layers. */
    fun withoutUpcoming(ids: Set<String>): MusicQueue =
        copy(added = added.filterNot { it.id in ids }, collection = collection.filterNot { it.id in ids }, radio = radio.filterNot { it.id in ids })

    val upcomingDurationMs: Long get() = upcoming.sumOf { it.track.durationMs }

    companion object {
        const val MAX_HISTORY = 100

        /** A collection started at [start]; the songs before it count as already played. */
        fun start(tracks: List<MusicTrack>, start: Int, title: String?, previous: MusicQueue?, shuffle: Boolean = previous?.shuffle ?: false): MusicQueue {
            val list = tracks.distinctBy { it.id }
            val index = start.coerceIn(0, (list.size - 1).coerceAtLeast(0))
            val first = list.getOrNull(index) ?: return previous ?: MusicQueue()
            val rest = list.drop(index + 1)
            return MusicQueue(
                current = QueueEntry(first, QueueLayer.Collection),
                added = previous?.added.orEmpty().filterNot { it.id == first.id },
                collection = if (shuffle) (list.take(index) + rest).shuffled() else rest,
                history = if (shuffle) previous?.history.orEmpty() else (previous?.history.orEmpty() + list.take(index).map { QueueEntry(it, QueueLayer.Collection) }).takeLast(MAX_HISTORY),
                collectionAll = list,
                title = title,
                shuffle = shuffle,
                repeat = previous?.repeat ?: RepeatMode.Off,
                endless = previous?.endless ?: true,
            )
        }

        /** One song as radio: «Как музыку» and «Радио по песне». */
        fun radio(track: MusicTrack, title: String?, previous: MusicQueue?): MusicQueue = MusicQueue(
            current = QueueEntry(track, QueueLayer.Collection),
            added = previous?.added.orEmpty().filterNot { it.id == track.id },
            history = previous?.let { p -> (p.history + listOfNotNull(p.current)).takeLast(MAX_HISTORY) }.orEmpty(),
            collectionAll = listOf(track),
            title = title,
            shuffle = previous?.shuffle ?: false,
            repeat = previous?.repeat?.takeIf { it != RepeatMode.All } ?: RepeatMode.Off,
            endless = true,
        )
    }
}

private fun <T> List<T>.without(index: Int): List<T> = if (index in indices) toMutableList().also { it.removeAt(index) } else this
private fun <T> List<T>.inserted(index: Int, item: T): List<T> = toMutableList().also { it.add(index.coerceIn(0, size), item) }
private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().also { val item = it.removeAt(from); it.add(to, item) }
}
