package org.qinyan.rimoat.i18n

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** Resource access for models and event handlers which do not have a Compose scope. */
object AppStrings {
    @Volatile private var application: Context? = null
    @Volatile private var cachedContext: LocalizedContext? = null
    @Volatile private var testResolver: ResourceResolver? = null

    private data class LocalizedContext(val languageTags: String, val contexts: Map<String, Context>) {
        val context: Context get() = contexts.getValue(languageTags)
    }

    internal interface ResourceResolver {
        val currentLanguage: String
        get() = application?.let { AppLanguage.wrap(it).resources.configuration.locales[0].language } ?: "zh"

    fun get(id: Int, vararg args: Any): String
        fun quantity(id: Int, quantity: Int, vararg args: Any): String
    }

    @Synchronized
    fun initialize(context: Context) {
        application = context.applicationContext
        testResolver = null
        cachedContext = null
    }

    @Synchronized
    fun invalidate() {
        cachedContext = null
    }

    internal fun setResourceResolverForTests(resolver: ResourceResolver?): ResourceResolver? {
        val previous = testResolver
        testResolver = resolver
        return previous
    }

    internal fun resourceContext(): Context {
        cachedContext?.let { return it.context }
        return synchronized(this) {
            cachedContext?.context ?: AppLanguage.wrap(checkNotNull(application) {
                "AppStrings is not initialized"
            }).also { context ->
                val languageTags = context.resources.configuration.locales.toLanguageTags()
                cachedContext = LocalizedContext(languageTags, mapOf(languageTags to context))
            }
        }
    }

    fun get(@StringRes id: Int, vararg args: Any): String =
        testResolver?.get(id, *args) ?: resourceContext().getString(id, *args)

    fun quantity(@PluralsRes id: Int, quantity: Int, vararg args: Any): String =
        testResolver?.quantity(id, quantity, *args)
            ?: resourceContext().resources.getQuantityString(id, quantity, *args)
}
