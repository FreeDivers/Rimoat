package org.qinyan.rimoat.testing

import org.junit.rules.ErrorCollector

internal fun ErrorCollector.collectScreenshotDifference(capture: () -> Unit) {
    try {
        capture()
    } catch (failure: AssertionError) {
        val screenshotDifference = generateSequence<Throwable>(failure) { it.cause }
            .any { it.message?.startsWith("Roborazzi:") == true }
        if (!screenshotDifference) throw failure
        addError(failure)
    }
}
