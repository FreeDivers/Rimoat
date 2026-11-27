package org.qinyan.rimoat.ui.components

import androidx.annotation.StringRes
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.*

internal const val MAIN_PANEL_KEY_COUNT = 6

fun panelKey(button: RemoteButton) = "key:${button.id}"
fun panelKey(buttonId: String) = "key:$buttonId"
fun panelAction(action: AcAction) = "ac:${action.name}"

fun RemoteProfile.hasAcControlPanel(): Boolean = category == DeviceCategory.AC &&
    (useStaticAcButtons || acProtocolId != null)

private val NUMERIC_BUTTON_ID = Regex("(?:tv_)?num_(\\d)")

internal fun remoteButtonNumber(button: RemoteButton): Int? =
    NUMERIC_BUTTON_ID.matchEntire(button.id)?.groupValues?.get(1)?.toIntOrNull()
        ?: (button.builtinLabel ?: button.label).toIntOrNull()?.takeIf { it in 0..9 }

internal fun genericDirectionButtons(buttons: List<RemoteButton>): Map<DpadDirection, RemoteButton> {
    val aliases = mapOf(
        DpadDirection.UP to setOf("up"),
        DpadDirection.DOWN to setOf("down"),
        DpadDirection.LEFT to setOf("left"),
        DpadDirection.RIGHT to setOf("right"),
        DpadDirection.OK to setOf("ok", "enter", "confirm", "select")
    )
    val legacyButtons = buttons.filter { it.key == null }
    return aliases.mapNotNull { (direction, tokens) ->
        val button = buttons.firstOrNull { it.key == direction.key }
            ?: legacyButtons.firstOrNull { it.id.lowercase() in tokens }
            ?: legacyButtons.firstOrNull { button ->
                val parts = button.id.lowercase().split(Regex("[^a-z0-9]+"))
                parts.lastOrNull() in tokens && parts.dropLast(1).none {
                    it in setOf("volume", "vol", "channel", "ch", "temp", "temperature", "page")
                }
            }
        button?.let { direction to it }
    }.toMap()
}

/** Spatial combinations retain their geometry and their complete signal set. */
fun fixedPanelKeys(profile: RemoteProfile): Set<String> {
    if (profile.hasAcControlPanel()) return emptySet()
    val power = profile.buttons.firstOrNull { it.isPowerKey() }
    val fixed = mutableSetOf<String>()
    power?.let { fixed += it.id }
    if (profile.category == DeviceCategory.TV) {
        val standard = tvStandardButtons(profile)
        standard.dpadButtons.forEach { fixed += it.id }
        standard.rockers.forEach { fixed += it.id }
        profile.buttons.filter { remoteButtonNumber(it) != null }.forEach { fixed += it.id }
    } else {
        val directions = genericDirectionButtons(profile.buttons)
        if (directions.keys.containsAll(DpadDirection.cardinal)) fixed += directions.values.map { it.id }
        profile.buttons.filter { remoteButtonNumber(it) != null }.forEach { fixed += it.id }
    }
    return fixed
}

fun RemoteProfile.withPanelPresentation(): RemoteProfile {
    val fixed = fixedPanelKeys(this)
    return copy(buttons = buttons.filter { it.id in fixed || panelKey(it) !in panelLayout.hidden }
        .sortedBy { if (it.id in fixed) -1 else panelLayout.rank(panelKey(it)) })
}

/** A user's label never assigns an AC function; stable metadata survives renaming. */
private fun acButtonMatchesAction(button: RemoteButton, action: AcAction): Boolean {
    button.acAction?.let { return it == action }
    AcAction.entries.firstOrNull { it.keyType == button.sourceKeyType }?.let { return it == action }
    if (button.isLearned && button.builtinLabel == null && button.sourceKeyType == null) return false
    val keys = when (action) {
        AcAction.POWER_ON -> setOf(ButtonKey.POWER_ON, ButtonKey.POWER)
        AcAction.FAN -> setOf(ButtonKey.FAN_SPEED, ButtonKey.AIRFLOW)
        AcAction.SWING -> setOf(ButtonKey.SWING, ButtonKey.VERTICAL_SWING)
        else -> setOf(action.buttonKey)
    }
    return button.key in keys
}

/** The state control chooses the most explicit binding; every duplicate remains represented by that control. */
fun acButtonForAction(profile: RemoteProfile, action: AcAction): RemoteButton? =
    profile.buttons.filter { acButtonMatchesAction(it, action) }.minByOrNull { button ->
        when {
            button.isLearned && button.hasSignal -> -1
            button.acAction == action -> 0
            button.sourceKeyType == action.keyType -> 1
            else -> 2
        }
    }

fun acRawExtensionButtons(profile: RemoteProfile): List<RemoteButton> =
    profile.buttons.filter { button -> AcAction.entries.none { acButtonMatchesAction(button, it) } }

/** Policy for migrated buttons; a custom name alone never assigns a function. */
fun RemoteButton.isPowerKey(): Boolean = key in setOf(
    ButtonKey.POWER, ButtonKey.POWER_ON, ButtonKey.POWER_OFF, ButtonKey.SECOND_POWER)

enum class FunctionGroup(@StringRes val labelRes: Int) {
    SOUND(R.string.control_sound_group),
    PLAYBACK(R.string.control_play),
    CHANNELS(R.string.control_channel_group),
    PICTURE(R.string.control_picture_group),
    CLIMATE(R.string.control_climate_group),
    NAVIGATION(R.string.control_navigation_group),
    DISPLAY(R.string.control_display_group),
    OPERATING_MODE(R.string.control_mode_group),
    TIMER(R.string.control_timer),
    AIR(R.string.control_air_group),
    OTHER(R.string.control_other_group)
}

val ButtonKey.group: FunctionGroup
    get() = when (this) {
        ButtonKey.VOLUME_UP, ButtonKey.VOLUME_DOWN, ButtonKey.MUTE, ButtonKey.AUDIO_CHANNEL -> FunctionGroup.SOUND
        ButtonKey.PLAY, ButtonKey.PAUSE, ButtonKey.PLAY_PAUSE, ButtonKey.STOP, ButtonKey.FAST_FORWARD,
        ButtonKey.REWIND, ButtonKey.PREVIOUS, ButtonKey.NEXT, ButtonKey.RECORD, ButtonKey.SLOW_PLAY,
        ButtonKey.EJECT -> FunctionGroup.PLAYBACK
        ButtonKey.CHANNEL_UP, ButtonKey.CHANNEL_DOWN, ButtonKey.RECALL, ButtonKey.FAVORITE,
        ButtonKey.DIGITS, ButtonKey.NUMBERS, ButtonKey.PLUS_TEN,
        ButtonKey.DIGIT_0, ButtonKey.DIGIT_1, ButtonKey.DIGIT_2, ButtonKey.DIGIT_3, ButtonKey.DIGIT_4,
        ButtonKey.DIGIT_5, ButtonKey.DIGIT_6, ButtonKey.DIGIT_7, ButtonKey.DIGIT_8, ButtonKey.DIGIT_9 -> FunctionGroup.CHANNELS
        ButtonKey.FOCUS_PLUS, ButtonKey.FOCUS_MINUS, ButtonKey.ZOOM_IN, ButtonKey.ZOOM_OUT,
        ButtonKey.BRIGHTNESS, ButtonKey.SUBTITLES, ButtonKey.PICTURE_IN_PICTURE, ButtonKey.FREEZE,
        ButtonKey.OSD, ButtonKey.VIDEO, ButtonKey.STANDARD -> FunctionGroup.PICTURE
        ButtonKey.FAN_SPEED, ButtonKey.AIRFLOW, ButtonKey.SWING, ButtonKey.HORIZONTAL_SWING,
        ButtonKey.VERTICAL_SWING, ButtonKey.OSCILLATION, ButtonKey.WIND_TYPE, ButtonKey.WIND_DIRECTION,
        ButtonKey.MODE, ButtonKey.TEMPERATURE_UP, ButtonKey.TEMPERATURE_DOWN, ButtonKey.COOL_WIND -> FunctionGroup.CLIMATE
        ButtonKey.UP, ButtonKey.DOWN, ButtonKey.LEFT, ButtonKey.RIGHT, ButtonKey.OK, ButtonKey.BACK,
        ButtonKey.HOME, ButtonKey.MENU, ButtonKey.POPUP_MENU, ButtonKey.TOP_MENU, ButtonKey.SOURCE,
        ButtonKey.SETTINGS, ButtonKey.EXIT -> FunctionGroup.NAVIGATION
        ButtonKey.LIGHT, ButtonKey.TEMPERATURE_DISPLAY -> FunctionGroup.DISPLAY
        ButtonKey.SLEEP, ButtonKey.ECO, ButtonKey.COMFORT, ButtonKey.TURBO -> FunctionGroup.OPERATING_MODE
        ButtonKey.TIMER, ButtonKey.BATH_TIMER -> FunctionGroup.TIMER
        ButtonKey.VENTILATION, ButtonKey.ION, ButtonKey.CLEAN, ButtonKey.DRY, ButtonKey.AUXILIARY_HEAT,
        ButtonKey.QUICK_COOL, ButtonKey.QUICK_HEAT, ButtonKey.HUMIDIFY, ButtonKey.KEEP_WARM -> FunctionGroup.AIR
        else -> FunctionGroup.OTHER
    }

enum class DpadDirection(
    val key: ButtonKey,
    @StringRes val labelRes: Int,
    val placeholderId: String
) {
    UP(ButtonKey.UP, R.string.control_up, "direction_up"),
    DOWN(ButtonKey.DOWN, R.string.control_down, "direction_down"),
    LEFT(ButtonKey.LEFT, R.string.control_left, "direction_left"),
    RIGHT(ButtonKey.RIGHT, R.string.control_right, "direction_right"),
    OK(ButtonKey.OK, R.string.control_ok, "direction_ok");

    fun placeholderButton(label: String): RemoteButton = RemoteButton(
        id = placeholderId,
        label = label,
        builtinLabel = key.name,
        key = key
    )

    companion object {
        val cardinal: Set<DpadDirection> = setOf(UP, DOWN, LEFT, RIGHT)
        fun from(key: ButtonKey?): DpadDirection? = entries.firstOrNull { it.key == key }
    }
}
