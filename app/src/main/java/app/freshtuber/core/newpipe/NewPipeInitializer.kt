package app.freshtuber.core.newpipe

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

object NewPipeInitializer {

    fun init(downloader: Downloader, locale: Locale) {
        NewPipe.init(downloader, Localization.fromLocale(locale), contentCountryOf(locale))
    }

    /** Re-applies language and country after the system locale changes. */
    fun updateLocale(locale: Locale) {
        NewPipe.setupLocalization(Localization.fromLocale(locale), contentCountryOf(locale))
    }

    private fun contentCountryOf(locale: Locale): ContentCountry =
        locale.country
            .takeIf { it.length == 2 }
            ?.let(::ContentCountry)
            ?: ContentCountry.DEFAULT
}
