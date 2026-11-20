package org.qinyan.rimoat.data

import android.content.pm.ActivityInfo
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.hardware.learning.LearnerType

/** Target orientation applied when screen rotation is triggered manually or automatically. */
enum class ScreenRotationDirection(val activityOrientation: Int) {
    REVERSE_PORTRAIT(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
    SENSOR_PORTRAIT(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
    LANDSCAPE(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    REVERSE_LANDSCAPE(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);

    companion object {
        fun fromPreference(value: String?): ScreenRotationDirection =
            entries.firstOrNull { it.name == value } ?: REVERSE_PORTRAIT
    }
}

/** Condition required for automatic screen rotation to activate. */
enum class ScreenRotationCondition {
    EXTERNAL_ONLY,
    ALWAYS;

    companion object {
        fun fromPreference(value: String?): ScreenRotationCondition =
            entries.firstOrNull { it.name == value } ?: EXTERNAL_ONLY
    }
}

/** Pages where automatic screen rotation applies. */
enum class ScreenRotationScope {
    REMOTE_AND_MATCHING,
    ALL_SCREENS;

    companion object {
        fun fromPreference(value: String?): ScreenRotationScope =
            entries.firstOrNull { it.name == value } ?: REMOTE_AND_MATCHING
    }
}

val TransmitterType.isExternalIr: Boolean
    get() = when (this) {
        TransmitterType.USB,
        TransmitterType.USB_TIQIAA,
        TransmitterType.USB_ELKSMART,
        TransmitterType.USB_SERIAL,
        TransmitterType.AUDIO -> true
        TransmitterType.BUILT_IN,
        TransmitterType.MOCK,
        TransmitterType.NONE -> false
    }

val LearnerType.isExternalIr: Boolean
    get() = when (this) {
        LearnerType.USB_TIQIAA,
        LearnerType.USB_ELKSMART -> true
        LearnerType.HUAWEI_OEM,
        LearnerType.MOCK,
        LearnerType.NONE -> false
    }

fun isExternalIrActive(
    transmitterType: TransmitterType,
    isTransmitterReady: Boolean,
    isTransmitterForceMock: Boolean,
    learnerType: LearnerType = LearnerType.NONE,
    isLearnerAvailable: Boolean = false,
    isLearnerForceMock: Boolean = false
): Boolean {
    val externalTransmitterReady = !isTransmitterForceMock && isTransmitterReady && transmitterType.isExternalIr
    val externalLearnerReady = !isLearnerForceMock && isLearnerAvailable && learnerType.isExternalIr
    return externalTransmitterReady || externalLearnerReady
}
