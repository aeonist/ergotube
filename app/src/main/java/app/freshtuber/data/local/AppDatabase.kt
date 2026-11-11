package app.freshtuber.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import app.freshtuber.data.music.MusicDao
import app.freshtuber.data.music.MusicPlaylist
import app.freshtuber.data.music.MusicPlaylistTrack
import app.freshtuber.data.music.MusicStoredState
import app.freshtuber.data.music.MusicTrack

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

@Database(entities = [PlaybackPosition::class, app.freshtuber.data.subscriptions.LocalChannel::class, app.freshtuber.data.subscriptions.LocalVideo::class, app.freshtuber.data.downloads.Download::class, MusicTrack::class, MusicPlaylist::class, MusicPlaylistTrack::class, MusicStoredState::class], version = 7, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localSubscriptions(): app.freshtuber.data.subscriptions.LocalSubscriptionDao
    abstract fun downloads(): app.freshtuber.data.downloads.DownloadDao
    abstract fun playbackPositions(): PlaybackPositionDao
    abstract fun music(): MusicDao
}


val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS local_channels (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, avatarUrl TEXT, notifications INTEGER NOT NULL, addedAt INTEGER NOT NULL, initialized INTEGER NOT NULL, lastCheckedAt INTEGER NOT NULL, syncFailed INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS local_videos (id TEXT NOT NULL PRIMARY KEY, channelId TEXT NOT NULL, channelName TEXT NOT NULL, title TEXT NOT NULL, thumbnailUrl TEXT, publishedAt INTEGER NOT NULL, FOREIGN KEY(channelId) REFERENCES local_channels(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_local_videos_channelId ON local_videos(channelId)")
    }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE local_videos ADD COLUMN isShort INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS downloads (videoId TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, channelName TEXT, channelId TEXT, thumbnailUrl TEXT, status TEXT NOT NULL, workId TEXT NOT NULL, requestedHeight INTEGER NOT NULL, height INTEGER NOT NULL, durationSeconds INTEGER NOT NULL, bytesDone INTEGER NOT NULL, bytesTotal INTEGER NOT NULL, videoFingerprint TEXT, audioFingerprint TEXT, videoMime TEXT, audioMime TEXT, codecMime TEXT, separateAudio INTEGER NOT NULL, subtitleMime TEXT, subtitleLanguage TEXT, subtitleLabel TEXT, error TEXT, createdAt INTEGER NOT NULL)")
    }
}


val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE music_tracks (id TEXT NOT NULL PRIMARY KEY, source TEXT NOT NULL, title TEXT NOT NULL, artist TEXT, album TEXT, artwork TEXT, durationMs INTEGER NOT NULL, uri TEXT, saved INTEGER NOT NULL, lastPlayedAt INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX index_music_tracks_uri ON music_tracks (uri)")
        db.execSQL("CREATE TABLE music_playlists (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, source TEXT NOT NULL)")
        db.execSQL("CREATE TABLE music_playlist_tracks (playlistId TEXT NOT NULL, trackId TEXT NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(playlistId, trackId), FOREIGN KEY(playlistId) REFERENCES music_playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(trackId) REFERENCES music_tracks(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_music_playlist_tracks_trackId ON music_playlist_tracks (trackId)")
        db.execSQL("CREATE TABLE music_state (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
    }
}


val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE downloads_new (videoId TEXT NOT NULL, title TEXT NOT NULL, channelName TEXT, channelId TEXT, thumbnailUrl TEXT, status TEXT NOT NULL, workId TEXT NOT NULL, requestedHeight INTEGER NOT NULL, height INTEGER NOT NULL, durationSeconds INTEGER NOT NULL, bytesDone INTEGER NOT NULL, bytesTotal INTEGER NOT NULL, videoFingerprint TEXT, audioFingerprint TEXT, videoMime TEXT, audioMime TEXT, codecMime TEXT, separateAudio INTEGER NOT NULL, subtitleMime TEXT, subtitleLanguage TEXT, subtitleLabel TEXT, error TEXT, createdAt INTEGER NOT NULL, kind TEXT NOT NULL, PRIMARY KEY(videoId, kind))")
        db.execSQL("INSERT INTO downloads_new SELECT videoId, title, channelName, channelId, thumbnailUrl, status, workId, requestedHeight, height, durationSeconds, bytesDone, bytesTotal, videoFingerprint, audioFingerprint, videoMime, audioMime, codecMime, separateAudio, subtitleMime, subtitleLanguage, subtitleLabel, error, createdAt, 'Video' FROM downloads")
        db.execSQL("DROP TABLE downloads")
        db.execSQL("ALTER TABLE downloads_new RENAME TO downloads")
    }
}


/** Music v2: one library with local favourites and the date a song joined it. */
val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE music_tracks ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE music_tracks ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE music_tracks SET addedAt = lastPlayedAt WHERE saved = 1")
    }
}
