package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.R

import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.UiText

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.qinyan.rimoat.ui.motion.MotionAlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun BackupSavedDialog(savedUri: String, onDismiss: () -> Unit, onError: (UiText) -> Unit) {
    val context = LocalContext.current
    MotionAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.get(R.string.screen_backup_saved, "已保存本地备份")) },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val uri = Uri.parse(savedUri)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("Rimoat backup", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, AppStrings.get(R.string.screen_share_backup, "分享遥控器备份")))
                } catch (e: Exception) {
                    onError(UiText.resource(R.string.screen_share_failed, e.localizedMessage.orEmpty()))
                }
                onDismiss()
            }) { Text(AppStrings.get(R.string.screen_share, "分享")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.get(R.string.screen_done, "完成")) } }
    )
}
