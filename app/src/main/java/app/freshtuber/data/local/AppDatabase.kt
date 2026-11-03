package app.freshtuber.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** Where the user stopped in a video, to resume there. Stays on the phone. */
@Entity(tableName = "playback_positions")
data class PlaybackPosition(
    @PrimaryKey val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Dao
interface PlaybackPositionDao {
    @Query("SELECT * FROM playback_positions WHERE videoId = :videoId")
    suspend fun get(videoId: String): PlaybackPosition?

    @Upsert
    suspend fun save(position: PlaybackPosition)

    @Query("DELETE FROM playback_positions WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM playback_positions WHERE updatedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Database(entities = [PlaybackPosition::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playbackPositions(): PlaybackPositionDao
}
