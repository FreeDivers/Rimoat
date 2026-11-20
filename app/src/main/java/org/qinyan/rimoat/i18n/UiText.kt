package org.qinyan.rimoat.i18n

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** Pending UI copy retains resource identity; resolve it with the displaying screen's Context. */
sealed interface UiText {
    data class Resource(@StringRes val id: Int, val arguments: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val quantity: Int, val arguments: List<Any> = emptyList()) : UiText
    /** User input or an external error, never an already formatted application sentence. */
    data class Dynamic(val value: String) : UiText
    /** Source names stay intact while the presentation language can change. */
    data class CatalogBrand(val chinese: String, val english: String) : UiText
    /** Built-in or catalog button labels resolve against the displaying Context. */
    data class ButtonLabel(val button: org.qinyan.rimoat.data.RemoteButton) : UiText

    fun resolve(context: Context): String = when (this) {
        is Resource -> context.getString(id, *arguments.resolveArguments(context))
        is Plural -> context.resources.getQuantityString(id, quantity, *arguments.resolveArguments(context))
        is Dynamic -> value
        is CatalogBrand -> if (context.resources.configuration.locales[0].language == "zh") {
            chinese.ifBlank { english }
        } else english.ifBlank { chinese }
        is ButtonLabel -> localizedButtonLabel(button, context)
    }

    companion object {
        fun resource(@StringRes id: Int, vararg arguments: Any): UiText = Resource(id, arguments.toList())
        fun plural(@PluralsRes id: Int, quantity: Int, vararg arguments: Any): UiText =
            Plural(id, quantity, arguments.toList())
    }
}

private fun List<Any>.resolveArguments(context: Context): Array<Any> =
    map { if (it is UiText) it.resolve(context) else it }.toTypedArray()

fun Throwable.uiTextDetail(@StringRes fallback: Int): UiText =
    localizedMessage?.let { UiText.Dynamic(it) } ?: UiText.Resource(fallback)

@Composable
fun UiText.asString(): String {
    // Configuration is observable even when the current Context instance stays unchanged.
    LocalConfiguration.current
    return resolve(LocalContext.current)
}
