package org.qinyan.rimoat.i18n

import android.content.ActivityNotFoundException
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import androidx.annotation.StringRes
import kotlinx.coroutines.TimeoutCancellationException
import org.qinyan.rimoat.R
import org.qinyan.rimoat.hardware.learning.LearningTimeoutException
import java.io.IOException
import java.util.concurrent.TimeoutException

/** Next-step user actions attached to surfaced errors. */
sealed interface ErrorAction {
    data object Retry : ErrorAction
    data object Reload : ErrorAction
    data class Learn(val buttonId: String) : ErrorAction
    data object OpenTransmitter : ErrorAction
    data object OpenStorageSettings : ErrorAction
}

@get:StringRes
val ErrorAction.labelRes: Int
    get() = when (this) {
        ErrorAction.Retry -> R.string.screen_retry
        ErrorAction.Reload -> R.string.error_action_reload
        is ErrorAction.Learn -> R.string.error_action_learn
        ErrorAction.OpenTransmitter -> R.string.error_action_open_transmitter
        ErrorAction.OpenStorageSettings -> R.string.error_action_storage_settings
    }

/** Typed presentation error mapped from exceptions without exposing raw exception text. */
sealed interface AppError {
    val message: UiText
    val action: ErrorAction?

    data class Resource(
        override val message: UiText,
        override val action: ErrorAction? = null
    ) : AppError

    data class StorageFull(
        override val message: UiText = UiText.Resource(R.string.error_storage_full),
        override val action: ErrorAction? = ErrorAction.OpenStorageSettings
    ) : AppError

    data class PermissionDenied(
        override val message: UiText = UiText.Resource(R.string.error_permission_denied),
        override val action: ErrorAction? = null
    ) : AppError

    data class LearningTimeout(
        override val message: UiText = UiText.Resource(R.string.screen_learn_timeout),
        override val action: ErrorAction? = ErrorAction.Retry
    ) : AppError

    data class ShareUnavailable(
        override val message: UiText = UiText.Resource(R.string.screen_share_unavailable),
        override val action: ErrorAction? = null
    ) : AppError
}

/** Map a [Throwable] to a localized [AppError] while logging raw technical details. */
fun Throwable.toAppError(
    @StringRes fallback: Int,
    defaultAction: ErrorAction? = null
): AppError {
    if (this !is UiTextCarryable) {
        try {
            Log.w("AppError", "${this::class.java.simpleName}: ${message.orEmpty()}", this)
        } catch (_: Throwable) {
            // Plain JVM unit tests do not mock android.util.Log.
        }
    }
    return when {
        this is UiTextCarryable -> AppError.Resource(uiText, action ?: defaultAction)
        this is LearningTimeoutException || this is TimeoutException || this is TimeoutCancellationException ->
            AppError.LearningTimeout(action = defaultAction ?: ErrorAction.Retry)
        this is ActivityNotFoundException -> AppError.ShareUnavailable(action = defaultAction)
        this is SecurityException -> AppError.PermissionDenied(action = defaultAction)
        this is IOException && isNoSpaceLeft() ->
            AppError.StorageFull(action = defaultAction ?: ErrorAction.OpenStorageSettings)
        else -> AppError.Resource(UiText.Resource(fallback), defaultAction)
    }
}

/** Wrap an [AppError] message with a contextual sentence resource while keeping its action. */
fun AppError.wrapped(
    @StringRes wrapper: Int,
    fallbackAction: ErrorAction? = null
): AppError = AppError.Resource(
    message = UiText.resource(wrapper, message),
    action = action ?: fallbackAction
)

private fun Throwable.isNoSpaceLeft(): Boolean {
    var current: Throwable? = this
    val visited = HashSet<Throwable>()
    while (current != null && visited.add(current)) {
        if (current.isErrnoEnospc()) return true
        val msg = current.message
        if (current is IOException && msg != null &&
            (msg.contains("ENOSPC", ignoreCase = true) ||
                msg.contains("No space left on device", ignoreCase = true))
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

private fun Throwable.isErrnoEnospc(): Boolean = try {
    this is ErrnoException && errno == OsConstants.ENOSPC
} catch (_: Throwable) {
    false
}
