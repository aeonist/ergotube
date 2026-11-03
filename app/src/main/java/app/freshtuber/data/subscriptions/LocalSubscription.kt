package app.freshtuber.data.subscriptions

import androidx.room.*
import app.freshtuber.domain.model.VideoSummary
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_channels")
data class LocalChannel(@PrimaryKey val id: String, val name: String, val avatarUrl: String? = null,
    val notifications: Boolean = false, val addedAt: Long, val initialized: Boolean = false,
    val lastCheckedAt: Long = 0, val syncFailed: Boolean = false)

@Entity(tableName = "local_videos", foreignKeys = [ForeignKey(entity = LocalChannel::class,
    parentColumns = ["id"], childColumns = ["channelId"], onDelete = ForeignKey.CASCADE)], indices = [Index("channelId")])
data class LocalVideo(@PrimaryKey val id: String, val channelId: String, val channelName: String,
    val title: String, val thumbnailUrl: String?, val publishedAt: Long, @ColumnInfo(defaultValue = "0") val isShort: Boolean = false) {
    fun summary() = VideoSummary(id, title, channelName = channelName, channelId = channelId,
        thumbnailUrl = thumbnailUrl, uploadedAtMillis = publishedAt, isShort = isShort)
}

@Dao
interface LocalSubscriptionDao {
    @Query("SELECT * FROM local_channels ORDER BY name COLLATE NOCASE") fun channels(): Flow<List<LocalChannel>>
    @Query("SELECT * FROM local_channels ORDER BY name COLLATE NOCASE") suspend fun allChannels(): List<LocalChannel>
    @Query("SELECT * FROM local_channels WHERE id = :id") fun observe(id: String): Flow<LocalChannel?>
    @Query("SELECT * FROM local_channels WHERE id = :id") suspend fun get(id: String): LocalChannel?
    @Upsert suspend fun save(channel: LocalChannel)
    @Query("DELETE FROM local_channels WHERE id = :id") suspend fun remove(id: String)
    @Query("UPDATE local_channels SET notifications = :enabled WHERE id = :id") suspend fun notifications(id: String, enabled: Boolean)
    @Query("SELECT * FROM local_videos ORDER BY publishedAt DESC, id LIMIT 500") fun videos(): Flow<List<LocalVideo>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertVideos(videos: List<LocalVideo>): List<Long>
    @Query("DELETE FROM local_videos WHERE publishedAt < :before") suspend fun trim(before: Long)
}
