package org.qinyan.rimoat.data

/** Custom functions keep their button identity; standard functions use stable enum names. */
val RemoteButton.verifiedFunctionId: String
    get() = acAction?.name ?: key?.name ?: id

/** Invalidate by identity after learning or replacing a signal, even after a rename. */
fun RemoteProfile.withoutVerifiedButtons(changed: Collection<RemoteButton>): RemoteProfile {
    val normalized = LegacyRemoteMigration.normalizeKeys(this)
    val ids = changed.map { it.id }.toSet()
    val tokens = (normalized.buttons.filter { it.id in ids } + changed).map { it.verifiedFunctionId }.toSet()
    return normalized.copy(verifiedFunctions = normalized.verifiedFunctions.filterNot { it in tokens })
}

val AcAction.buttonKey: ButtonKey
    get() = when (this) {
        AcAction.POWER_ON -> ButtonKey.POWER_ON
        AcAction.POWER_OFF -> ButtonKey.POWER_OFF
        AcAction.TEMP_UP -> ButtonKey.TEMPERATURE_UP
        AcAction.TEMP_DOWN -> ButtonKey.TEMPERATURE_DOWN
        AcAction.MODE -> ButtonKey.MODE
        AcAction.FAN -> ButtonKey.FAN_SPEED
        AcAction.SWING -> ButtonKey.VERTICAL_SWING
        AcAction.HORIZONTAL_SWING -> ButtonKey.HORIZONTAL_SWING
        AcAction.WIND_DIRECTION -> ButtonKey.WIND_DIRECTION
        AcAction.LIGHT -> ButtonKey.LIGHT
        AcAction.TURBO -> ButtonKey.TURBO
        AcAction.SLEEP -> ButtonKey.SLEEP
        AcAction.VENTILATION -> ButtonKey.VENTILATION
        AcAction.AUXILIARY_HEAT -> ButtonKey.AUXILIARY_HEAT
        AcAction.TIMER -> ButtonKey.TIMER
        AcAction.DRY -> ButtonKey.DRY
        AcAction.ION -> ButtonKey.ION
        AcAction.ECO -> ButtonKey.ECO
        AcAction.COMFORT -> ButtonKey.COMFORT
        AcAction.TEMPERATURE_DISPLAY -> ButtonKey.TEMPERATURE_DISPLAY
        AcAction.QUICK_COOL -> ButtonKey.QUICK_COOL
        AcAction.QUICK_HEAT -> ButtonKey.QUICK_HEAT
        AcAction.QUIET -> ButtonKey.MUTE
        AcAction.CLEAN -> ButtonKey.CLEAN
    }
