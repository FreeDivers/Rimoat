package org.qinyan.rimoat

import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.MockIrTransmitter
import org.qinyan.rimoat.hardware.TransmitterType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockIrTransmitterTest {

    @Test
    fun testMockTransmitterSuccess() = runBlocking {
        val mock = MockIrTransmitter()
        mock.simulateDelayMs = 0L

        assertEquals(TransmitterType.MOCK, mock.type)
        assertTrue(mock.isReady.value)

        val signal = IrSignal.Nec(0x04FB, 0x08)
        val result = mock.transmit(signal)

        assertTrue(result.isSuccess)
        assertEquals(signal, mock.lastSignal.value)
        assertEquals(1, mock.transmitCount.value)
    }

    @Test
    fun testMockTransmitterFailureSimulation() = runBlocking {
        val mock = MockIrTransmitter()
        mock.simulateDelayMs = 0L
        mock.shouldFail = true
        mock.failureReason = "Hardware timeout"

        val signal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560))
        val result = mock.transmit(signal)

        assertFalse(result.isSuccess)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertEquals("Hardware timeout", ex?.message)
        assertEquals(0, mock.transmitCount.value)
    }

    @Test
    fun testMockTransmitterNotReady() = runBlocking {
        val mock = MockIrTransmitter(initialReady = false)
        assertFalse(mock.isReady.value)

        val result = mock.transmit(IrSignal.Nec(1, 2))
        assertFalse(result.isSuccess)
    }

    @Test
    fun testMockTransmitterConcurrentThreadSafety() = runBlocking {
        val mock = MockIrTransmitter()
        mock.simulateDelayMs = 0L

        val signal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560))
        val jobs = (1..50).map {
            async {
                mock.transmit(signal)
            }
        }
        val results = jobs.awaitAll()
        assertTrue(results.all { it.isSuccess })
        assertEquals(50, mock.transmitCount.value)

        mock.reset()
        assertEquals(0, mock.transmitCount.value)
    }
}

