package org.qinyan.rimoat.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.update

internal data class ProgressCheckpointKey(
    val categoryId: Int?,
    val brandId: Int?,
    val step: AddRemoteStep,
    val query: String,
    val remoteId: String?,
    val customName: String,
    val observations: Map<String, PairingObservation>
)

internal class PairingProgressStore(private val savedStateHandle: SavedStateHandle) {
    private val progressJson = Json { ignoreUnknownKeys = true }
    private val savedProgressJson: String? = savedStateHandle["pairing_progress"]
    var progress = decodeProgress(savedProgressJson)
    private var lastPersistedProgress: PairingProgress? = savedProgressJson?.let { progress }
    private fun decodeProgress(json: String?): PairingProgress = runCatching {
        json?.let { progressJson.decodeFromString<PairingProgress>(it) }?.takeIf { it.version in 1..2 }
    }.getOrNull() ?: PairingProgress()
    fun brandKey(category: Int, brand: Int) = "$category:$brand"
    fun updateBrandProgress(key: String, update: (BrandProgress) -> BrandProgress) {
        val updated = update(progress.brands[key] ?: BrandProgress())
        val reordered = (progress.brands - key) + (key to updated)
        val bounded = if (reordered.size <= MAX_SAVED_BRANDS) {
            reordered
        } else {
            reordered.entries.drop(reordered.size - MAX_SAVED_BRANDS).associate { it.key to it.value }
        }
        progress = progress.copy(brands = bounded)
    }
    fun persistProgress() {
        if (progress == lastPersistedProgress) return
        if (progress == PairingProgress()) {
            savedStateHandle.remove<String>("pairing_progress")
            lastPersistedProgress = progress
            return
        }
        val serialized = progressJson.encodeToString(progress)
        if (savedStateHandle.get<String>("pairing_progress") != serialized) {
            savedStateHandle["pairing_progress"] = serialized
        }
        lastPersistedProgress = progress
    }
    fun clear() {
        progress = PairingProgress()
        lastPersistedProgress = null
        savedStateHandle.remove<String>("pairing_progress")
    }

    private companion object {
        private const val MAX_SAVED_BRANDS = 5
    }
}
