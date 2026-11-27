package org.qinyan.rimoat.i18n

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** Resource access for models and event handlers which do not have a Compose scope. */
object AppStrings {
    @Volatile private var application: Context? = null
    @Volatile private var testResolver: ResourceResolver? = null

    internal interface ResourceResolver {
        fun get(id: Int, vararg args: Any): String
        fun quantity(id: Int, quantity: Int, vararg args: Any): String
    }

    fun initialize(context: Context) {
        application = context.applicationContext
        testResolver = null
    }

    internal fun setResourceResolverForTests(resolver: ResourceResolver?): ResourceResolver? {
        val previous = testResolver
        testResolver = resolver
        return previous
    }

    val currentLanguage: String
        get() = application?.let { AppLanguage.wrap(it).resources.configuration.locales[0].language } ?: "zh"

    internal fun resourceContext(): Context = AppLanguage.wrap(checkNotNull(application) {
        "AppStrings is not initialized"
    })

    fun get(@StringRes id: Int, vararg args: Any): String =
        testResolver?.get(id, *args) ?: resourceContext().getString(id, *args)

    fun quantity(@PluralsRes id: Int, quantity: Int, vararg args: Any): String =
        testResolver?.quantity(id, quantity, *args)
            ?: resourceContext().resources.getQuantityString(id, quantity, *args)
}
