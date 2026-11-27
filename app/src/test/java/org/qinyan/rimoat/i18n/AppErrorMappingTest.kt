package org.qinyan.rimoat.i18n

import android.content.ActivityNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qinyan.rimoat.R
import org.qinyan.rimoat.hardware.learning.LearningTimeoutException
import java.io.IOException
import java.util.concurrent.TimeoutException

class AppErrorMappingTest {

    @Test
    fun ioExceptionWithEnospcMapsToStorageFullAndSettingsAction() {
        val error = IOException("write failed: ENOSPC (No space left on device)")
            .toAppError(R.string.control_unknown_error)

        assertTrue(error is AppError.StorageFull)
        assertEquals(UiText.Resource(R.string.error_storage_full), error.message)
        assertEquals(ErrorAction.OpenStorageSettings, error.action)
    }

    @Test
    fun genericIoExceptionUsesFallbackAndOptionalDefaultAction() {
        val error = IOException("raw low level message")
            .toAppError(R.string.msg_storage_unavailable, defaultAction = ErrorAction.Retry)

        assertTrue(error is AppError.Resource)
        assertEquals(UiText.Resource(R.string.msg_storage_unavailable), error.message)
        assertEquals(ErrorAction.Retry, error.action)
    }

    @Test
    fun securityExceptionMapsToPermissionDenied() {
        val error = SecurityException("uid 1000 lacks permission")
            .toAppError(R.string.control_unknown_error, defaultAction = ErrorAction.Retry)

        assertTrue(error is AppError.PermissionDenied)
        assertEquals(UiText.Resource(R.string.error_permission_denied), error.message)
        assertEquals(ErrorAction.Retry, error.action)
    }

    @Test
    fun learningAndTimeoutExceptionsMapToLearningTimeoutWithRetry() {
        val timeout = LearningTimeoutException("Hardware did not respond")
            .toAppError(R.string.screen_learn_failed)
        assertTrue(timeout is AppError.LearningTimeout)
        assertEquals(UiText.Resource(R.string.screen_learn_timeout), timeout.message)
        assertEquals(ErrorAction.Retry, timeout.action)

        val javaTimeout = TimeoutException("timed out")
            .toAppError(R.string.screen_learn_failed)
        assertTrue(javaTimeout is AppError.LearningTimeout)

        val cancelledTimeout = runCatching {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeout(1) {
                    kotlinx.coroutines.delay(100)
                }
            }
        }.exceptionOrNull()!!
        assertTrue(cancelledTimeout is TimeoutCancellationException)
        assertTrue(cancelledTimeout.toAppError(R.string.screen_learn_failed) is AppError.LearningTimeout)
    }

    @Test
    fun activityNotFoundMapsToShareUnavailable() {
        val error = ActivityNotFoundException("No Activity found to handle Intent")
            .toAppError(R.string.screen_share_failed)

        assertTrue(error is AppError.ShareUnavailable)
        assertEquals(UiText.Resource(R.string.screen_share_unavailable), error.message)
        assertNull(error.action)
    }

    @Test
    fun uiTextExceptionPreservesMessageAndAction() {
        val error = UiTextException(
            UiText.Resource(R.string.msg_ac_state_library_unavailable),
            action = ErrorAction.Reload
        ).toAppError(R.string.control_unknown_error)

        assertTrue(error is AppError.Resource)
        assertEquals(UiText.Resource(R.string.msg_ac_state_library_unavailable), error.message)
        assertEquals(ErrorAction.Reload, error.action)
    }

    @Test
    fun uiTextDetailNeverReturnsDynamicTextForSystemExceptions() {
        val mapped = IllegalStateException("internal stack detail")
            .uiTextDetail(R.string.control_unknown_error)

        assertEquals(UiText.Resource(R.string.control_unknown_error), mapped)
        assertFalse(mapped is UiText.Dynamic)
    }

    @Test
    fun wrappedPreservesSpecificCauseAndAction() {
        val wrapped = SecurityException("denied")
            .toAppError(R.string.control_unknown_error, defaultAction = ErrorAction.Retry)
            .wrapped(R.string.msg_remote_save_failed)

        assertEquals(
            UiText.resource(R.string.msg_remote_save_failed, UiText.Resource(R.string.error_permission_denied)),
            wrapped.message
        )
        assertEquals(ErrorAction.Retry, wrapped.action)
    }
}
