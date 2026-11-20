package org.qinyan.rimoat

import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.ui.motion.AppMotion
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AcTimerOptionsTest {
    @Test fun everyBundledTimerHasCanonicalOptions() {
        val features = Json.parseToJsonElement(File("src/main/assets/ac_features.json").readText()).jsonObject
        val declared = features.filterValues { v -> v.jsonObject.getValue("keys").jsonArray.any { it.jsonPrimitive.int == 876 } }.keys.map { it.toInt() }.toSet()
        assertEquals(39, declared.size)
        val supported = (0..1000).filter { AcTimerOptions.forProtocol(it).isNotEmpty() }.toSet()
        assertEquals(declared, supported)
        supported.forEach { protocol ->
            val options = AcTimerOptions.forProtocol(protocol)
            assertEquals(0, options.first())
            assertTrue(options.zipWithNext().all { (a, b) -> a < b })
            assertTrue(options.last() <= 1440)
        }
    }

    @Test fun roundingClampingAndWrapsAreNotExposedAsDurations() {
        for (protocol in listOf(634, 635, 636, 639, 640, 646, 651, 652, 657, 658, 659, 660, 669, 671, 673, 674, 678)) {
            assertFalse("$protocol: 31", 31 in AcTimerOptions.forProtocol(protocol))
            assertFalse("$protocol: 78", 78 in AcTimerOptions.forProtocol(protocol))
        }
        assertEquals(720, AcTimerOptions.forProtocol(645).last())
        assertEquals(420, AcTimerOptions.forProtocol(673).last())
        assertEquals(1430, AcTimerOptions.forProtocol(635).last())
        assertEquals(1380, AcTimerOptions.forProtocol(647).last())
        assertEquals((0..1440).toList(), AcTimerOptions.forProtocol(670))
        assertEquals((0..1440).toList(), AcTimerOptions.forProtocol(670, powerOn = false))
        assertTrue(600 in AcTimerOptions.forProtocol(658))
        assertFalse(630 in AcTimerOptions.forProtocol(658))
        assertTrue(660 in AcTimerOptions.forProtocol(658))
        assertTrue(31 in AcTimerOptions.forProtocol(628))
        assertTrue(78 in AcTimerOptions.forProtocol(628))
    }

    @Test fun timerPreservesPowerAndUnsupportedOnTimersAreUnavailable() {
        val off = AcSettings(powerOn = false).apply(AcAction.TIMER, 60)
        assertFalse(off.powerOn)
        assertEquals(0, off.nativeState()[0].toInt())
        assertTrue(AcTimerOptions.supports(635, off))
        for (protocol in listOf(637, 660, 668, 673)) assertFalse(AcTimerOptions.supports(protocol, off))
        assertFalse(AcTimerOptions.supports(635, AcSettings(timerMinutes = 31)))
        assertTrue(AcTimerOptions.supports(635, AcSettings(timerMinutes = 30)))
        assertEquals(0, off.apply(AcAction.TIMER, 0).nativeState()[10].toInt())
    }

    @Test fun automaticMotionCurvesAreMonotonicAndNonlinear() {
        for (curve in listOf(AppMotion.Standard, AppMotion.Enter, AppMotion.Exit)) {
            val points = (0..100).map { curve.transform(it / 100f) }
            assertEquals(0f, points.first(), .001f)
            assertEquals(1f, points.last(), .001f)
            assertTrue(points.zipWithNext().all { (a, b) -> a <= b })
            assertTrue(kotlin.math.abs(curve.transform(.5f) - .5f) > .05f)
        }
    }
}
