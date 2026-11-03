package app.freshtuber

import android.app.Application
import android.content.res.Configuration
import androidx.core.os.ConfigurationCompat
import app.freshtuber.core.network.ExtractorDownloader
import app.freshtuber.core.newpipe.NewPipeInitializer
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var extractorDownloader: ExtractorDownloader
    @Inject lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        NewPipeInitializer.init(extractorDownloader, currentLocale(resources.configuration))
        app.freshtuber.background.SubscriptionWorker.schedule(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        NewPipeInitializer.updateLocale(currentLocale(newConfig))
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }
            .crossfade(true)
            .build()

    private fun currentLocale(configuration: Configuration): Locale =
        ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
}
