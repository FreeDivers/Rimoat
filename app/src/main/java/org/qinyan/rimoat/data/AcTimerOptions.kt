package org.qinyan.rimoat.data

/** Supported timer durations (in minutes) per AC protocol. */
object AcTimerOptions {
    private val minute = (1..1440).toList()
    private val tenMinutes = (10..1440 step 10).toList()
    private val halfHours = (30..1440 step 30).toList()
    private val mixedHours = (30..600 step 30).toList() + (660..1440 step 60).toList()
    private val rules = buildMap {
        listOf(628, 637, 642, 648, 653, 663, 664, 668, 672).forEach { put(it, minute) }
        listOf(636, 643, 644, 649, 651).forEach { put(it, tenMinutes) }
        listOf(639, 646, 652, 659, 669, 674, 675, 676, 677, 678).forEach { put(it, halfHours) }
        listOf(640, 641, 650, 656, 658).forEach { put(it, mixedHours) }
        listOf(634, 635).forEach { put(it, (10..1430 step 10).toList()) }
        put(645, (1..720).toList())
        put(647, (60..1380 step 60).toList())
        // Power-on half-hour entries alias whole-hour frames in this protocol.
        put(657, (60..1380 step 60).toList())
        put(660, (60..720 step 60).toList())
        put(667, (60..1440).toList())
        // NativeAcEncoder restores the hour field for protocol 670.
        put(670, minute)
        put(671, (15..1440 step 15).toList())
        put(673, (60..420 step 60).toList())
    }.mapValues { (_, durations) -> listOf(0) + durations }
    private val powerOnOnly = setOf(637, 660, 668, 673)

    fun forProtocol(protocol: Int, powerOn: Boolean = true): List<Int> = when {
        !powerOn && protocol in powerOnOnly -> emptyList()
        protocol == 657 && !powerOn -> listOf(0) + (30..570 step 30).toList() + (600..1380 step 60).toList()
        else -> rules[protocol].orEmpty()
    }

    fun supports(protocol: Int, settings: AcSettings): Boolean =
        settings.timerMinutes in forProtocol(protocol, settings.powerOn)
}
