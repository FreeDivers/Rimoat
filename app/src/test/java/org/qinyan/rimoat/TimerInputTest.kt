package org.qinyan.rimoat

import org.qinyan.rimoat.data.AcTimerOptions
import org.qinyan.rimoat.ui.components.timerPresets
import org.qinyan.rimoat.i18n.timerTargetDayResource
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimerInputTest {
    @Test fun presetsRespectProtocolOptions() {
        for (id in 628..678) {
            val options = AcTimerOptions.forProtocol(id)
            assertTrue(timerPresets(options).all { it > 0 && it in options })
        }
        assertEquals(listOf(30, 60, 120, 240), timerPresets(AcTimerOptions.forProtocol(670)))
        assertEquals(listOf(60, 120, 240, 480), timerPresets(AcTimerOptions.forProtocol(660)))
    }
    @Test fun expectedShutdownDistinguishesMidnightAndFullDay() {
        val zone = TimeZone.getTimeZone("Asia/Tokyo")
        val now = Calendar.getInstance(zone).apply { set(2026, 10, 8, 23, 30, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        assertEquals(R.string.control_today_time, timerTargetDayResource(now, 15, zone))
        assertEquals(R.string.control_tomorrow_time, timerTargetDayResource(now, 60, zone))
        assertEquals(R.string.control_tomorrow_time, timerTargetDayResource(now, 1440, zone))
    }
    @Test fun dayBoundaryWorksAcrossYear() {
        val zone = TimeZone.getTimeZone("UTC")
        val now = Calendar.getInstance(zone).apply { set(2026, 11, 31, 23, 50, 0) }.timeInMillis
        assertEquals(R.string.control_tomorrow_time, timerTargetDayResource(now, 30, zone))
    }
    @Test fun dayBoundaryUsesTheDisplayTimeZone() {
        val utc = TimeZone.getTimeZone("UTC")
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
        val now = Calendar.getInstance(utc).apply { set(2026, 10, 15, 23, 30, 0) }.timeInMillis
        assertEquals(R.string.control_tomorrow_time, timerTargetDayResource(now, 60, utc))
        assertEquals(R.string.control_today_time, timerTargetDayResource(now, 60, tokyo))
    }
}
