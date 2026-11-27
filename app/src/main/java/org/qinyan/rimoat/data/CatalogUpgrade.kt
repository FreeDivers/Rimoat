package org.qinyan.rimoat.data

/** Only replace a generated button when it still equals the shipped v5 definition. */
object CatalogUpgrade {
    const val VERSION = 6

    fun merge(profile: RemoteProfile, source: String, legacy: List<RemoteButton>, current: List<RemoteButton>): RemoteProfile {
        if (profile.catalogVersion >= VERSION || current.isEmpty()) return profile
        val normalized = LegacyRemoteMigration.normalizeKeys(profile.copy(sourceRemoteId = profile.sourceRemoteId ?: source))
        val remaining = normalized.buttons.toMutableList()
        val currentWithKeys = LegacyRemoteMigration.normalizeKeys(RemoteProfile(
            "catalog", "Catalog", category = profile.category, buttons = current)).buttons
        val changedButtons = mutableListOf<RemoteButton>()
        val result = currentWithKeys.map { fresh ->
            val candidates = legacy.filter { it.sourceKeyType == fresh.sourceKeyType }
            val old = candidates.firstOrNull { it.label == fresh.label && remaining.any { b -> b.id == it.id } }
                ?: candidates.firstOrNull { remaining.any { b -> b.id == it.id } }
            val index = old?.let { definition -> remaining.indexOfFirst { it.id == definition.id } } ?: -1
            if (index < 0) fresh else {
                val saved = remaining.removeAt(index)
                val unchanged = !saved.isLearned && saved.copy(sourceKeyType = old!!.sourceKeyType, key = old.key) == old
                if (unchanged) {
                    if (saved.label != fresh.label || saved.toIrSignal() != fresh.toIrSignal()) changedButtons += saved
                    fresh.copy(id = saved.id, label = saved.label, builtinLabel = saved.builtinLabel, iconName = saved.iconName,
                        row = saved.row, col = saved.col, spanCol = saved.spanCol, key = saved.key ?: fresh.key)
                } else saved.copy(sourceKeyType = fresh.sourceKeyType, sourceSlot = fresh.sourceSlot)
            }
        }.toMutableList()
        // Old invented keys can be removed only if they are byte-for-byte stock.
        remaining.forEach { saved ->
            val old = legacy.firstOrNull { it.id == saved.id }
            if (old == null || saved.isLearned || saved.copy(sourceKeyType = old.sourceKeyType, key = old.key) != old) result += saved
            else changedButtons += saved
        }
        val ids = mutableSetOf<String>()
        val unique = result.mapIndexed { index, button ->
            if (ids.add(button.id)) button else {
                var id = "${button.id}_slot_$index"
                while (!ids.add(id)) id += "_"
                button.copy(id = id)
            }
        }
        return normalized.withoutVerifiedButtons(changedButtons).copy(
            buttons = unique, sourceRemoteId = source, catalogVersion = VERSION)
    }
}
