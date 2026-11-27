package org.qinyan.rimoat.i18n

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** AppCompat applies UI locales; Android 13 owns storage, older devices keep the existing preference. */
object AppLanguage {
    val supportedTags = listOf("", "zh-CN", "zh-Hant", "en", "ja")
    private const val PREFERENCES = "rimoat_language"
    private const val LANGUAGE = "language_tag"
    private const val MIGRATED = "migrated_to_platform"

    fun initialize(context: Context) {
        invalidate()
        if (Build.VERSION.SDK_INT >= 33) {
            migrateToPlatform(context)
        } else {
            // Restore before either Activity is created, including a cold widget configuration launch.
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selectedTag(context)))
        }
    }

    /** Match the settings choice without collapsing Simplified and Traditional Chinese. */
    fun choiceTag(tag: String): String {
        if (tag.isEmpty()) return ""
        val locale = Locale.forLanguageTag(tag)
        return when (locale.language) {
            "zh" -> when (locale.script) {
                "Hant" -> "zh-Hant"
                "Hans" -> "zh-CN"
                else -> if (locale.country in setOf("TW", "HK", "MO")) "zh-Hant" else "zh-CN"
            }
            "en", "ja" -> locale.language
            else -> ""
        }
    }

    /** Preserve an Android 12 selection after an OS upgrade without replacing a platform choice. */
    fun migrateToPlatform(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (prefs.getBoolean(MIGRATED, false)) return
        val manager = context.getSystemService(LocaleManager::class.java)
        val legacy = prefs.getString(LANGUAGE, "").orEmpty()
        if (manager.applicationLocales.isEmpty && legacy in supportedTags && legacy.isNotEmpty()) {
            manager.applicationLocales = LocaleList.forLanguageTags(legacy)
        }
        prefs.edit().putBoolean(MIGRATED, true).apply()
    }

    fun selectedTag(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java).applicationLocales
            .takeUnless { it.isEmpty }?.get(0)?.toLanguageTag().orEmpty()
    } else {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LANGUAGE, "").orEmpty()
    }

    @Volatile
    private var cachedAppLocaleWrap: Triple<Context, Configuration, Context>? = null

    fun invalidate() {
        cachedAppLocaleWrap = null
        AppStrings.invalidate()
    }

    /** Background and widget contexts can still carry the previous Activity's locale. */
    fun wrap(context: Context): Context {
        val requested: LocaleList
        val system: LocaleList
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(LocaleManager::class.java)
            requested = manager.applicationLocales
            system = manager.systemLocales
        } else {
            requested = LocaleList.forLanguageTags(selectedTag(context))
            system = Resources.getSystem().configuration.locales
        }
        val locales = LocaleList.forLanguageTags(
            listOf(requested.toLanguageTags(), system.toLanguageTags()).filter(String::isNotEmpty).joinToString(",")
        )
        val configuration = Configuration(context.resources.configuration).apply { setLocales(locales) }
        if (configuration == context.resources.configuration) return context
        cachedAppLocaleWrap?.let { (source, cachedConfiguration, wrapped) ->
            if (source === context && cachedConfiguration == configuration) return wrapped
        }
        return context.createConfigurationContext(configuration).also {
            // Only retain the Application context, never an Activity or a themed wrapper.
            if (context === context.applicationContext) cachedAppLocaleWrap = Triple(context, configuration, it)
        }
    }

    fun set(activity: Activity, tag: String) {
        require(tag in supportedTags)
        if (selectedTag(activity) == tag) return
        if (Build.VERSION.SDK_INT < 33) {
            activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(LANGUAGE, tag).apply()
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        invalidate()
    }
}
