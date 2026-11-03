package app.freshtuber.core.di

import app.freshtuber.core.network.CookieProvider
import app.freshtuber.core.network.AccountCookieProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CompressionInterceptor
import okhttp3.Gzip
import okhttp3.OkHttpClient
import okhttp3.brotli.Brotli
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(CompressionInterceptor(Brotli, Gzip))
        .build()

    /** Extraction is anonymous unless a caller explicitly asks for account cookies. */
    @Provides
    @Singleton
    fun provideCookieProvider(provider: AccountCookieProvider): CookieProvider = provider
}
