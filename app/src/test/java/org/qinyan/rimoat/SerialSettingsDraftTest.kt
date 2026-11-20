package org.qinyan.rimoat

import org.qinyan.rimoat.hardware.serial.SerialIrModule
import org.qinyan.rimoat.hardware.serial.SerialIrSettings
import org.qinyan.rimoat.ui.components.serialSettingsFromDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SerialSettingsDraftTest {
    @Test fun unconfiguredDiscardsHiddenInvalidInputsAndBinding() {
        val saved = SerialIrSettings(module = SerialIrModule.IR_TOY, baudRate = 115200,
            vendorId = 123, productId = 456)
        assertEquals(SerialIrSettings(), serialSettingsFromDraft(saved, SerialIrModule.UNCONFIGURED, "", "bad", "FA"))
    }

    @Test fun irToyIgnoresHiddenYsInputs() {
        val settings = serialSettingsFromDraft(SerialIrSettings(), SerialIrModule.IR_TOY, "1", "", "invalid")
        assertEquals(1, settings.portIndex)
        assertEquals(115200, settings.baudRate)
        assertEquals(0xA1, settings.moduleAddress)
    }

    @Test fun ysUsesTypedSettingsAndChecksRealModuleLimits() {
        val saved = SerialIrSettings()
        val settings = serialSettingsFromDraft(saved, SerialIrModule.YS_IRTM, "2", "19200", "0XAF")
        assertEquals(2, settings.portIndex)
        assertEquals(19200, settings.baudRate)
        assertEquals(0xAF, settings.moduleAddress)
        assertThrows(IllegalArgumentException::class.java) {
            serialSettingsFromDraft(saved, SerialIrModule.YS_IRTM, "32", "19200", "AF")
        }
        assertThrows(IllegalArgumentException::class.java) {
            serialSettingsFromDraft(saved, SerialIrModule.YS_IRTM, "2", "115200", "AF")
        }
        assertThrows(IllegalArgumentException::class.java) {
            serialSettingsFromDraft(saved, SerialIrModule.YS_IRTM, "2", "19200", "FA")
        }
    }
}
