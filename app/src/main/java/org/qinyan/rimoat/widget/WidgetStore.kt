package org.qinyan.rimoat.widget

import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.AtomicLocalFile
import org.qinyan.rimoat.data.LegacyDirectionIds
import org.qinyan.rimoat.i18n.AppStrings

@Serializable
private data class StoredWidgets(val configurations: Map<Int, WidgetConfiguration> = emptyMap(),
                                 val completedRestores: Set<String> = emptySet())

class WidgetStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private var loaded = false
    private var restores = emptySet<String>()
    private val _configurations = MutableStateFlow<Map<Int, WidgetConfiguration>>(emptyMap())
    val configurations = _configurations.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock { readLocked() } }
    private fun readLocked() {
        if (loaded) return
        val stored = if (file.exists()) {
            val text = file.readText()
            try {
                val (decoded, changed) = decodeStoredWidgetsWithMigration(text)
                if (changed) {
                    AtomicLocalFile.write(file, json.encodeToString(decoded))
                }
                decoded
            } catch (e: IllegalArgumentException) {
                val backup = File(file.parentFile, "${file.nameWithoutExtension}-corrupt-${UUID.randomUUID()}.json")
                if (!file.renameTo(backup)) throw IOException("Unable to back up widget storage", e)
                StoredWidgets()
            }
        } else StoredWidgets()
        _configurations.value = stored.configurations
        restores = stored.completedRestores
        loaded = true
    }

    private fun decodeStoredWidgets(text: String): StoredWidgets =
        decodeStoredWidgetsWithMigration(text).first

    private fun decodeStoredWidgetsWithMigration(text: String): Pair<StoredWidgets, Boolean> {
        val root = json.parseToJsonElement(text) as? JsonObject
            ?: throw SerializationException("Expected a widget storage object")
        // Older files store the configuration map directly at the root.
        val wrappedConfigurations = root["configurations"]
        val entries = if (wrappedConfigurations == null) root else {
            wrappedConfigurations as? JsonObject ?: throw SerializationException("Expected a widget configuration map")
        }
        val completedRestores = if (wrappedConfigurations == null) emptySet() else {
            root["completedRestores"]?.let { json.decodeFromJsonElement<Set<String>>(it) }.orEmpty()
        }
        var changed = false
        val configurations = buildMap {
            for ((key, entry) in entries) {
                val id = key.toIntOrNull()?.takeIf { it > 0 } ?: continue
                val configuration = try {
                    val decoded = json.decodeFromJsonElement<WidgetConfiguration>(entry)
                    val migratedShortcuts = decoded.shortcuts
                        .map { it.copy(buttonId = LegacyDirectionIds.migrateButtonId(it.buttonId)) }
                        .distinct()
                    val migrated = if (migratedShortcuts != decoded.shortcuts) {
                        changed = true
                        decoded.copy(shortcuts = migratedShortcuts)
                    } else decoded
                    migrated.also { it.validate() }
                } catch (_: IllegalArgumentException) {
                    continue
                }
                put(id, configuration)
            }
        }
        return StoredWidgets(configurations, completedRestores) to changed
    }

    suspend fun save(id: Int, configuration: WidgetConfiguration) = mutate {
        require(id > 0) { AppStrings.get(R.string.widget_invalid_id, "小组件标识无效") }
        configuration.validate()
        it + (id to configuration)
    }
    suspend fun remove(id: Int) = mutate { it - id }
    suspend fun remap(oldIds: IntArray, newIds: IntArray, restoreId: String = oldIds.joinToString() + ":" + newIds.joinToString()) = withContext(Dispatchers.IO) {
        mutex.withLock {
            readLocked()
            require(oldIds.size == newIds.size && newIds.all { it > 0 })
            if (restoreId in restores) return@withLock
            val previous = _configurations.value
            val migrated = oldIds.zip(newIds.toList()).mapNotNull { (old, new) -> previous[old]?.let { new to it } }.toMap()
            val next = (previous - oldIds.toSet()) + migrated
            val nextRestores = (restores + restoreId).toList().takeLast(64).toSet()
            AtomicLocalFile.write(file, json.encodeToString(StoredWidgets(next, nextRestores)))
            restores = nextRestores
            _configurations.value = next
        }
    }
    private suspend fun mutate(transform: (Map<Int, WidgetConfiguration>) -> Map<Int, WidgetConfiguration>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            readLocked()
            val next = transform(_configurations.value)
            AtomicLocalFile.write(file, json.encodeToString(StoredWidgets(next, restores)))
            _configurations.value = next
        }
    }
}
