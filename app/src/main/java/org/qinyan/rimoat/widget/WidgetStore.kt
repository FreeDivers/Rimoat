package org.qinyan.rimoat.widget

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings

import org.qinyan.rimoat.data.AtomicLocalFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

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
        val stored = if (!file.exists()) StoredWidgets() else {
            val text = file.readText()
            if ("configurations" in json.parseToJsonElement(text).jsonObject) json.decodeFromString<StoredWidgets>(text)
            else StoredWidgets(json.decodeFromString(text))
        }
        val values = stored.configurations
        values.values.forEach { it.validate() }
        _configurations.value = values
        restores = stored.completedRestores
        loaded = true
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
