package app.freshtuber.core.di

import android.content.Context
import app.freshtuber.core.security.KeystoreCipher
import app.freshtuber.data.account.SessionFileStore
import app.freshtuber.data.account.SessionStore
import app.freshtuber.data.local.AppDatabase
import app.freshtuber.data.local.PlaybackPositionDao
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** A scope that lives as long as the process, for work that must outlive a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Sessions live in noBackupFilesDir, encrypted with a Keystore key. */
    @Provides
    @Singleton
    fun provideSessionStore(@ApplicationContext context: Context): SessionStore =
        SessionStore(
            fileStore = SessionFileStore(
                file = File(context.noBackupFilesDir, "session.bin"),
                cipher = KeystoreCipher("freshtuber_session_v1"),
            ),
            io = Dispatchers.IO,
        )

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "freshtuber.db").build()

    @Provides
    fun providePlaybackPositions(database: AppDatabase): PlaybackPositionDao = database.playbackPositions()
}
