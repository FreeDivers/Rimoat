package org.qinyan.rimoat.data

import org.qinyan.rimoat.hardware.IrSignal
import kotlinx.serialization.Serializable

@Serializable
enum class AcMode(val labelCn: String, val nativeValue: Int) {
    COOL("制冷", 4), HEAT("制热", 3), DRY("除湿", 2), FAN("送风", 1), AUTO("自动", 0)
}

@Serializable
enum class AcFanSpeed(val labelCn: String) {
    AUTO("自动"), LOW("低速"), MEDIUM("中速"), HIGH("高速")
}

@Serializable
enum class AcWindDirection(val labelCn: String) { AUTO("自动"), UP("向上"), MIDDLE("居中"), DOWN("向下") }

@Serializable
enum class AcTemperatureDisplay(val labelCn: String) { OUTDOOR("室外温度"), INDOOR("室内温度"), TARGET("设定温度"), OFF("关闭显示") }

@Serializable
data class AcSettings(
    val powerOn: Boolean = true,
    val temperature: Int = 26,
    val mode: AcMode = AcMode.COOL,
    val fanSpeed: AcFanSpeed = AcFanSpeed.AUTO,
    val isSwingOn: Boolean = false,
    val counter: Int = 0,
    val horizontalSwingOn: Boolean = false,
    val windDirection: AcWindDirection = AcWindDirection.AUTO,
    val turboOn: Boolean = false,
    val sleepOn: Boolean = false,
    val auxiliaryHeatOn: Boolean = false,
    val timerMinutes: Int = 0,
    val temperatureDisplay: AcTemperatureDisplay = AcTemperatureDisplay.OFF,
    val ecoOn: Boolean = false,
    val ionOn: Boolean = false,
    val comfortOn: Boolean = false,
    val ventilationOn: Boolean = false,
    val lightOn: Boolean = true,
    val dryOn: Boolean = false,
    val quietOn: Boolean = false
) {
    /** Encodes the 20-byte AC state payload in encoder field order. */
    fun nativeState(): ByteArray {
        require(temperature in 16..31 && timerMinutes in 0..1440)
        fun Boolean.bit() = if (this) 1 else 0
        return listOf(powerOn.bit(), mode.nativeValue, temperature,
            when (fanSpeed) { AcFanSpeed.AUTO -> 4; AcFanSpeed.LOW -> 0; AcFanSpeed.MEDIUM -> 1; AcFanSpeed.HIGH -> 2 },
            windDirection.ordinal, horizontalSwingOn.bit(), isSwingOn.bit(), turboOn.bit(), sleepOn.bit(),
            auxiliaryHeatOn.bit(), (timerMinutes > 0).bit(), temperatureDisplay.ordinal, ecoOn.bit(),
            ionOn.bit(), comfortOn.bit(), ventilationOn.bit(), lightOn.bit(), dryOn.bit(), quietOn.bit(), 0)
            .map { it.toByte() }.toByteArray()
    }

    fun isLegacyState(): Boolean = !horizontalSwingOn && windDirection == AcWindDirection.AUTO &&
        !turboOn && !sleepOn && !auxiliaryHeatOn && timerMinutes == 0 &&
        temperatureDisplay == AcTemperatureDisplay.OFF && !ecoOn && !ionOn && !comfortOn &&
        !ventilationOn && lightOn && !dryOn && !quietOn && temperature <= 30

    /** The stateful panel deliberately exposes the same 16–30 °C range as apply(). */
    fun temperatureBoundaryReason(action: AcAction): AcUnavailableReason? = when {
        action == AcAction.TEMP_DOWN && temperature <= 16 -> AcUnavailableReason.TEMPERATURE_MINIMUM
        action == AcAction.TEMP_UP && temperature >= 30 -> AcUnavailableReason.TEMPERATURE_MAXIMUM
        else -> null
    }

    /** Apply one official key while preserving all unrelated full-state fields. */
    fun apply(action: AcAction, minutes: Int = timerMinutes): AcSettings {
        val next = when (action) {
            AcAction.POWER_ON -> copy(powerOn = true)
            AcAction.POWER_OFF -> copy(powerOn = false)
            AcAction.TEMP_UP -> copy(temperature = (temperature + 1).coerceAtMost(30))
            AcAction.TEMP_DOWN -> copy(temperature = (temperature - 1).coerceAtLeast(16))
            AcAction.MODE -> copy(mode = AcMode.entries[(mode.ordinal + 1) % AcMode.entries.size])
            AcAction.FAN -> copy(fanSpeed = AcFanSpeed.entries[(fanSpeed.ordinal + 1) % AcFanSpeed.entries.size])
            AcAction.SWING -> copy(isSwingOn = !isSwingOn)
            AcAction.HORIZONTAL_SWING -> copy(horizontalSwingOn = !horizontalSwingOn)
            AcAction.WIND_DIRECTION -> copy(windDirection = AcWindDirection.entries[(windDirection.ordinal + 1) % 4])
            AcAction.LIGHT -> copy(lightOn = !lightOn)
            AcAction.TURBO -> copy(turboOn = !turboOn, ecoOn = if (!turboOn) false else ecoOn)
            AcAction.SLEEP -> copy(sleepOn = !sleepOn)
            AcAction.VENTILATION -> copy(ventilationOn = !ventilationOn)
            AcAction.AUXILIARY_HEAT -> copy(auxiliaryHeatOn = !auxiliaryHeatOn)
            AcAction.TIMER -> { require(minutes in 0..1440); copy(timerMinutes = minutes) }
            AcAction.DRY -> copy(dryOn = !dryOn)
            AcAction.ION -> copy(ionOn = !ionOn)
            AcAction.ECO -> copy(ecoOn = !ecoOn, turboOn = if (!ecoOn) false else turboOn)
            AcAction.COMFORT -> copy(comfortOn = !comfortOn)
            AcAction.TEMPERATURE_DISPLAY -> copy(temperatureDisplay = when (temperatureDisplay) {
                AcTemperatureDisplay.OFF -> AcTemperatureDisplay.INDOOR
                AcTemperatureDisplay.INDOOR -> AcTemperatureDisplay.OUTDOOR
                AcTemperatureDisplay.OUTDOOR -> AcTemperatureDisplay.TARGET
                AcTemperatureDisplay.TARGET -> AcTemperatureDisplay.OFF
            })
            AcAction.QUICK_COOL, AcAction.QUICK_HEAT -> AcSettings(counter = counter,
                temperature = if (action == AcAction.QUICK_COOL) 26 else 22,
                mode = if (action == AcAction.QUICK_COOL) AcMode.COOL else AcMode.HEAT,
                windDirection = AcWindDirection.MIDDLE, temperatureDisplay = AcTemperatureDisplay.INDOOR)
            AcAction.QUIET -> copy(quietOn = !quietOn)
            AcAction.CLEAN -> this
        }
        return next.copy(powerOn = if (action == AcAction.TIMER) powerOn else action != AcAction.POWER_OFF,
            timerMinutes = if (action == AcAction.TIMER) next.timerMinutes else 0)
    }

    fun actionLabel(action: AcAction): String {
        val enabled = when (action) {
            AcAction.SWING -> isSwingOn; AcAction.HORIZONTAL_SWING -> horizontalSwingOn
            AcAction.LIGHT -> lightOn; AcAction.TURBO -> turboOn; AcAction.SLEEP -> sleepOn
            AcAction.VENTILATION -> ventilationOn; AcAction.AUXILIARY_HEAT -> auxiliaryHeatOn
            AcAction.DRY -> dryOn; AcAction.ION -> ionOn; AcAction.ECO -> ecoOn
            AcAction.COMFORT -> comfortOn; AcAction.QUIET -> quietOn
            else -> null
        }
        val value = when (action) {
            AcAction.WIND_DIRECTION -> windDirection.labelCn
            AcAction.TEMPERATURE_DISPLAY -> temperatureDisplay.labelCn
            AcAction.TIMER -> if (timerMinutes > 0) "${timerMinutes}分钟" else "关"
            else -> enabled?.let { if (it) "开" else "关" }
        }
        return action.label + (value?.let { " · $it" } ?: "")
    }

    // Matches the offline AC state index layout; native encoders use the counter's toggle bit.
    fun stateKey(action: AcAction, sequenceCounter: Int = counter): Int {
        require(temperature in 16..30)
        return (temperature - 16) or (mode.nativeValue shl 4) or (fanSpeed.ordinal shl 7) or
            ((if (isSwingOn) 1 else 0) shl 9) or (action.ordinal shl 10) or ((sequenceCounter and 1) shl 13)
    }
}

@Serializable
enum class AcAction(val keyType: Int, val label: String) {
    POWER_ON(800,"开机"), POWER_OFF(800,"关机"), TEMP_UP(811,"升温"), TEMP_DOWN(812,"降温"),
    MODE(832,"模式"), FAN(833,"风速"), SWING(835,"上下扫风"),
    HORIZONTAL_SWING(834,"左右扫风"), WIND_DIRECTION(870,"风向"), LIGHT(871,"灯光"),
    TURBO(872,"超强"), SLEEP(873,"睡眠"), VENTILATION(874,"换气"), AUXILIARY_HEAT(875,"辅热"),
    TIMER(876,"定时"), DRY(877,"干燥"), ION(878,"负离子"), ECO(879,"节能"),
    COMFORT(880,"舒适"), TEMPERATURE_DISPLAY(881,"温度显示"), QUICK_COOL(882,"一键制冷"),
    QUICK_HEAT(883,"一键制热"), QUIET(804,"静音"), CLEAN(884,"自清洁")
}

interface AcSignalEncoder {
    suspend fun protocolFor(sourceRemoteId: String): Int?
    suspend fun supportedModes(protocol: Int): List<AcMode>
    suspend fun availableActions(protocol: Int): Set<AcAction> = AcAction.entries.toSet()
    suspend fun supportsAction(protocol: Int, settings: AcSettings, action: AcAction): Boolean = true
    suspend fun unavailableReason(protocol: Int, settings: AcSettings, action: AcAction): AcUnavailableReason? {
        if (action !in availableActions(protocol)) return AcUnavailableReason.MISSING_SIGNAL
        settings.temperatureBoundaryReason(action)?.let { return it }
        val target = if (action == AcAction.MODE) {
            val modes = supportedModes(protocol)
            if (modes.isEmpty()) return AcUnavailableReason.MISSING_SIGNAL
            settings.copy(mode = modes[(modes.indexOf(settings.mode) + 1) % modes.size], powerOn = true)
        } else settings.apply(action, if (action == AcAction.TIMER) AcTimerOptions.forProtocol(protocol, settings.powerOn).firstOrNull() ?: 0 else 0)
        return if (supportsAction(protocol, target, action)) null else AcUnavailableReason.STATE_RESTRICTED
    }
    suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction): List<IrSignal.Raw>
}

enum class AcUnavailableReason {
    MISSING_SIGNAL, ENCODER_UNAVAILABLE, STATE_RESTRICTED, HARDWARE_UNAVAILABLE, CAPABILITY_UNAVAILABLE,
    TEMPERATURE_MINIMUM, TEMPERATURE_MAXIMUM
}
