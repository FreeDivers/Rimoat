package org.qinyan.rimoat.data

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.hardware.serial.SerialIrSettings
import java.io.File
import java.io.IOException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class SerialIrSettingsStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun read(): SerialIrSettings = if (!file.exists()) SerialIrSettings() else
        json.decodeFromString<SerialIrSettings>(file.readText()).also { it.validate() }
    fun write(settings: SerialIrSettings) {
        settings.validate()
        try {
            AtomicLocalFile.write(file, json.encodeToString(settings))
        } catch (e: IOException) {
            throw IOException(AppStrings.get(R.string.msg_serial_settings_save_failed, "串口配置保存失败，请检查存储空间和权限后重试"), e)
        }
    }
}
