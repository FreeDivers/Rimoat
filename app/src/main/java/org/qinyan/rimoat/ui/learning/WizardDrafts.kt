package org.qinyan.rimoat.ui.learning

import org.qinyan.rimoat.data.AtomicLocalFile
import java.io.File
import java.util.concurrent.TimeUnit

internal object WizardDrafts {
    private const val DIRECTORY = "wizard-drafts"
    private const val SESSION_ID = "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"
    private val draftName = Regex("$SESSION_ID[.]json")
    private val temporaryName = Regex("[.]$SESSION_ID[.]json[.]-?[0-9]+[.]tmp")
    private val retentionMillis = TimeUnit.DAYS.toMillis(7)

    fun file(filesDir: File, sessionId: String): File {
        val name = "$sessionId.json"
        require(draftName.matches(name))
        return File(File(filesDir, DIRECTORY), name)
    }

    @Synchronized
    fun read(file: File): String? =
        if (!file.exists() || file.isExpired(System.currentTimeMillis())) null else file.readText()

    @Synchronized
    fun write(file: File, state: String) {
        AtomicLocalFile.write(file, state)
    }

    @Synchronized
    fun delete(file: File): Boolean {
        val staleWrites = file.parentFile?.listFiles { candidate ->
            candidate.isFile && candidate.name.startsWith(".${file.name}.") && temporaryName.matches(candidate.name)
        }.orEmpty()
        var deleted = file.deleteIfPresent()
        staleWrites.forEach { if (!it.deleteIfPresent()) deleted = false }
        return deleted
    }

    @Synchronized
    fun pruneExpired(filesDir: File, nowMillis: Long = System.currentTimeMillis()) {
        File(filesDir, DIRECTORY).listFiles()?.forEach { file ->
            if (file.isFile && (draftName.matches(file.name) || temporaryName.matches(file.name)) && file.isExpired(nowMillis)) {
                file.delete()
            }
        }
    }

    private fun File.deleteIfPresent(): Boolean = !exists() || (isFile && delete())

    private fun File.isExpired(nowMillis: Long): Boolean {
        val modified = lastModified()
        return modified > 0 && modified < nowMillis - retentionMillis
    }
}
