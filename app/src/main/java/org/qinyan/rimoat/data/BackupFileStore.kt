package org.qinyan.rimoat.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import java.io.IOException

internal class BackupWriteException(cause: Exception) : IOException(cause)

class BackupFileStore(private val context: Context) {
    suspend fun read(uri: Uri): List<RemoteProfile> = readDocument(uri).backup.remotes
    suspend fun readDocument(uri: Uri, infrared: Boolean = false): ImportDocument = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(output.size() + n <= RemoteBackupCodec.MAX_BYTES) { AppStrings.get(R.string.msg_backup_too_large, "备份超过 8 MB") }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        } ?: error(AppStrings.get(R.string.msg_file_read_failed, "无法读取所选文件"))
        val text = bytes.toString(Charsets.UTF_8)
        if (infrared) {
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "IR.ir"
            FlipperIrImporter.decode(text, name)
        } else ImportDocument(RemoteBackupCodec.decodeBackup(text))
    }
    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        try {
            // Opening a document for truncation can change it before the first write.
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("No output stream for the selected document")
            output.use {
                it.write(bytes)
                it.flush()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw BackupWriteException(e)
        }
    }
}
