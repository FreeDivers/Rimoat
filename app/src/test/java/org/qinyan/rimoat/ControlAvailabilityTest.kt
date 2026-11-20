package org.qinyan.rimoat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.ui.components.canInlineExtensions

class ControlAvailabilityTest {
    @Test fun missingSignalAndStateRestrictionAreDistinctAndDoNotEncode() = runBlocking {
        var encoded = 0
        val encoder = object : AcSignalEncoder {
            override suspend fun protocolFor(sourceRemoteId: String) = 184
            override suspend fun supportedModes(protocol: Int) = listOf(AcMode.COOL, AcMode.HEAT)
            override suspend fun availableActions(protocol: Int) = setOf(AcAction.MODE, AcAction.FAN)
            override suspend fun supportsAction(protocol: Int, settings: AcSettings, action: AcAction) = settings.mode != AcMode.DRY
            override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction): List<IrSignal.Raw> { encoded++; return emptyList() }
        }
        assertEquals(AcUnavailableReason.MISSING_SIGNAL,encoder.unavailableReason(184,AcSettings(),AcAction.SWING))
        assertEquals(AcUnavailableReason.STATE_RESTRICTED,encoder.unavailableReason(184,AcSettings(mode=AcMode.DRY),AcAction.FAN))
        assertNull(encoder.unavailableReason(184,AcSettings(),AcAction.FAN))
        assertEquals(0,encoded)
    }
    @Test fun smallExtensionCountStillRequiresSpaceForFullContent() {
        assertTrue(canInlineExtensions(3,411f,790f,500f,150f))
        assertFalse(canInlineExtensions(3,320f,700f,600f,190f))
        assertFalse(canInlineExtensions(3,411f,790f,500f,300f))
        assertFalse(canInlineExtensions(9,600f,1400f,500f,150f))
    }
    @Test fun temperatureLimitsAreNotMissingSignalsAndDoNotEncode() = runBlocking {
        var encoded = 0
        var available = setOf(AcAction.TEMP_UP, AcAction.TEMP_DOWN)
        val encoder = object : AcSignalEncoder {
            override suspend fun protocolFor(sourceRemoteId: String) = 184
            override suspend fun supportedModes(protocol: Int) = listOf(AcMode.COOL)
            override suspend fun availableActions(protocol: Int) = available
            override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction): List<IrSignal.Raw> {
                encoded++
                return emptyList()
            }
        }
        assertEquals(AcUnavailableReason.TEMPERATURE_MINIMUM,
            encoder.unavailableReason(184, AcSettings(temperature = 16), AcAction.TEMP_DOWN))
        assertEquals(AcUnavailableReason.TEMPERATURE_MAXIMUM,
            encoder.unavailableReason(184, AcSettings(temperature = 30), AcAction.TEMP_UP))
        assertNull(encoder.unavailableReason(184, AcSettings(temperature = 16), AcAction.TEMP_UP))
        assertNull(encoder.unavailableReason(184, AcSettings(temperature = 30), AcAction.TEMP_DOWN))
        assertEquals(AcUnavailableReason.MISSING_SIGNAL,
            encoder.unavailableReason(184, AcSettings(temperature = 16), AcAction.FAN))
        available = setOf(AcAction.TEMP_UP)
        assertEquals(AcUnavailableReason.MISSING_SIGNAL,
            encoder.unavailableReason(184, AcSettings(temperature = 16), AcAction.TEMP_DOWN))
        assertEquals(0, encoded)
    }
}
