package org.qinyan.rimoat.data

import org.qinyan.rimoat.hardware.IrSignal
import kotlinx.serialization.Serializable

@Serializable
enum class DeviceCategory {
    TV,
    AC,
    FAN,
    AUDIO,
    PROJECTOR,
    CUSTOM
}

@Serializable
enum class SignalType {
    RAW,
    NEC
}

@Serializable
data class RemoteProfile(
    val id: String,
    val name: String,
    val brand: String = "",
    val category: DeviceCategory = DeviceCategory.CUSTOM,
    val buttons: List<RemoteButton> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val sourceRemoteId: String? = null,
    val acProtocolId: Int? = null,
    val acSettings: AcSettings = AcSettings(),
    val useStaticAcButtons: Boolean = false,
    val room: String = "",
    val lastUsedAt: Long = 0,
    val lastSentAt: Long = 0,
    val verifiedFunctions: List<String> = emptyList(),
    val catalogVersion: Int = 0,
    val panelLayout: RemotePanelLayout = RemotePanelLayout(),
    val applianceType: ApplianceType? = null,
    val keySchemaVersion: Int = 0,
    val brandIdx: Int? = null
)

@Serializable
data class CatalogLabelMetadata(
    val key: ButtonKey? = null,
    val customType: Int? = null,
    val literalOk: Boolean = false,
    val mode: AcMode? = null,
    val temperature: String? = null,
    val fallbackKey: ButtonKey? = null,
    val fallbackLabel: CatalogFallbackLabel? = null
)

@Serializable
data class RemoteButton(
    val id: String,
    val label: String,
    val iconName: String? = null,
    val row: Int = 0,          // Grid row position
    val col: Int = 0,          // Grid column position
    val spanCol: Int = 1,      // Button width span
    val signalType: SignalType = SignalType.RAW,
    val frequencyHz: Int = 38000,
    val rawPattern: List<Int> = emptyList(), // Used when signalType == RAW
    val necAddress: Int? = null,             // Used when signalType == NEC
    val necCommand: Int? = null,             // Used when signalType == NEC
    val sourceKeyType: Int? = null,
    val sourceSlot: String? = null,
    val alternateRawPattern: List<Int> = emptyList(),
    val alternateFrequencyHz: Int = 38000,
    val isLearned: Boolean = false,
    val acAction: AcAction? = null,
    /** Stable source label only for app-created default keys; user-entered labels leave this null. */
    val builtinLabel: String? = null,
    /** Missing flags preserve the address convention used by existing saved remotes. */
    val necExtended: Boolean = ((necAddress ?: 0) and 0xFF00) != 0,
    val key: ButtonKey? = null,
    /** Semantic catalog labels are resolved while loading, before presentation. */
    val catalogLabel: CatalogLabelMetadata? = null
) {
    val hasSignal: Boolean
        get() = when (signalType) {
            SignalType.RAW -> rawPattern.isNotEmpty() && rawPattern.all { it != 0 && it != Int.MIN_VALUE }
            SignalType.NEC -> necAddress != null && necCommand != null
        }

    fun toIrSignal(pressIndex: Int = 0): IrSignal {
        if (signalType == SignalType.RAW && alternateRawPattern.isNotEmpty() && pressIndex and 1 == 1) {
            return IrSignal.Raw(alternateFrequencyHz, alternateRawPattern.toIntArray())
        }
        return when (signalType) {
            SignalType.NEC -> {
                IrSignal.Nec(
                    address = necAddress ?: 0,
                    command = necCommand ?: 0,
                    extended = necExtended
                )
            }
            SignalType.RAW -> {
                IrSignal.Raw(
                    frequencyHz = frequencyHz,
                    patternMicros = rawPattern.toIntArray()
                )
            }
        }
    }

    fun withLearnedSignal(signal: IrSignal.Raw): RemoteButton {
        return copy(
            signalType = SignalType.RAW,
            frequencyHz = signal.frequencyHz,
            rawPattern = signal.patternMicros.toList(),
            necAddress = null,
            necCommand = null,
            necExtended = false,
            alternateRawPattern = emptyList(),
            isLearned = true
        )
    }
}

