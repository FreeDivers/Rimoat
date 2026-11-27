package org.qinyan.rimoat.data

/** Canonical ASCII IDs for directional learning placeholders, shared across buttons, layout, and widgets. */
internal object LegacyDirectionIds {
    private val idMap = mapOf(
        "direction_上" to "direction_up",
        "direction_下" to "direction_down",
        "direction_左" to "direction_left",
        "direction_右" to "direction_right",
        "direction_OK" to "direction_ok"
    )

    private val keysByMigratedId = mapOf(
        "direction_up" to ButtonKey.UP,
        "direction_down" to ButtonKey.DOWN,
        "direction_left" to ButtonKey.LEFT,
        "direction_right" to ButtonKey.RIGHT,
        "direction_ok" to ButtonKey.OK
    )

    fun migrateButtonId(id: String): String = idMap[id] ?: id

    fun migratePanelKey(key: String): String =
        if (key.startsWith("key:")) "key:" + migrateButtonId(key.removePrefix("key:")) else key

    fun keyForMigratedId(id: String): ButtonKey? = keysByMigratedId[id]
}
