package org.qinyan.rimoat.i18n

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

/** Resource access for models and event handlers which do not have a Compose scope. */
object AppStrings {
    @Volatile private var application: Context? = null

    fun initialize(context: Context) {
        application = context.applicationContext
    }

    val currentLanguage: String
        get() = application?.let { AppLanguage.wrap(it).resources.configuration.locales[0].language } ?: "zh"

    fun get(@StringRes id: Int, vararg args: Any): String {
        val context = checkNotNull(application) { "AppStrings is not initialized" }
        return AppLanguage.wrap(context).getString(id, *args)
    }

    /** The source-language fallback keeps plain JVM model tests independent of Android. */
    fun get(@StringRes id: Int, fallback: String, vararg args: Any): String {
        val context = application
        return if (context == null) {
            if (args.isEmpty()) fallback else String.format(Locale.SIMPLIFIED_CHINESE, fallback, *args)
        } else AppLanguage.wrap(context).getString(id, *args)
    }

    fun quantity(@PluralsRes id: Int, quantity: Int, fallback: String, vararg args: Any): String {
        val context = application
        return if (context == null) {
            if (args.isEmpty()) fallback else String.format(Locale.SIMPLIFIED_CHINESE, fallback, *args)
        } else AppLanguage.wrap(context).resources.getQuantityString(id, quantity, *args)
    }
}
