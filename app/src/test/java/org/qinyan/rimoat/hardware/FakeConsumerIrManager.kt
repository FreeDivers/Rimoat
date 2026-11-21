package org.qinyan.rimoat.hardware

import android.content.Context
import android.hardware.ConsumerIrManager
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@Implements(ConsumerIrManager::class)
class FakeConsumerIrManager {
    var hasEmitter: Boolean = false
    var lastFrequency: Int? = null
        private set
    var lastPattern: IntArray? = null
        private set

    @Implementation
    @Suppress("UNUSED_PARAMETER")
    protected fun __constructor__(context: Context) = Unit

    @Implementation
    protected fun hasIrEmitter(): Boolean = hasEmitter

    @Implementation
    protected fun transmit(carrierFrequency: Int, pattern: IntArray) {
        lastFrequency = carrierFrequency
        lastPattern = pattern.copyOf()
    }
}
