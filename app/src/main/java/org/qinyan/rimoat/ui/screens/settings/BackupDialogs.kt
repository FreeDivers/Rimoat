@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppError
import org.qinyan.rimoat.i18n.toAppError
import org.qinyan.rimoat.ui.motion.MotionAlertDialog

@Composable
internal fun BackupSavedDialog(
    savedUri: String,
    onDismiss: () -> Unit,
    onError: (AppError) -> Unit
) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.screen_share_backup)
    val fileName = backupDisplayName(context, savedUri)
    MotionAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.screen_backup_saved)) },
        text = { Text(stringResource(R.string.screen_backup_saved_file, fileName)) },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val uri = savedUri.toUri()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri("Rimoat backup", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, shareTitle))
                    } catch (e: ActivityNotFoundException) {
                        onError(e.toAppError(R.string.screen_share_unavailable))
                    } catch (e: Exception) {
                        onError(e.toAppError(R.string.screen_share_failed))
                    }
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.screen_share)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.screen_done))
            }
        }
    )
}

internal fun backupDisplayName(context: Context, savedUri: String): String {
    val uri = savedUri.toUri()
    if (uri.scheme == "content") {
        val queried = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()
        if (!queried.isNullOrBlank()) return queried
    }
    val segment = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    return segment ?: "rimoat_backup.json"
}
