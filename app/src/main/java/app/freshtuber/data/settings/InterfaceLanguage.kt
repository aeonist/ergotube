package app.freshtuber.data.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList

internal object InterfaceLanguage {
    private const val DEFAULT_TAG = "en"
    private const val SELECTED_TAG = "language_tag"
    private const val PLATFORM_INITIALIZED = "platform_initialized"

    private fun preferences(context: Context) =
        context.getSharedPreferences("interface_language", Context.MODE_PRIVATE)

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val preferences = preferences(context)
        if (preferences.getBoolean(PLATFORM_INITIALIZED, false)) return

        val manager = context.getSystemService(LocaleManager::class.java)
        if (manager.applicationLocales.isEmpty) {
            val tag = preferences.getString(SELECTED_TAG, DEFAULT_TAG).orEmpty()
            manager.applicationLocales = LocaleList.forLanguageTags(tag)
        }
        preferences.edit().putBoolean(PLATFORM_INITIALIZED, true).apply()
    }

    fun selectedTag(context: Context): String =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales[0]?.language.orEmpty()
        } else {
            preferences(context).getString(SELECTED_TAG, DEFAULT_TAG).orEmpty()
        }

    fun select(activity: Activity, tag: String) {
        if (tag == selectedTag(activity)) return
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            preferences(activity).edit().putString(SELECTED_TAG, tag).apply()
            activity.recreate()
        }
    }

    fun activityContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = selectedTag(base)
        if (tag.isEmpty()) return base
        val configuration = Configuration().apply { setLocales(LocaleList.forLanguageTags(tag)) }
        return base.createConfigurationContext(configuration)
    }
}
