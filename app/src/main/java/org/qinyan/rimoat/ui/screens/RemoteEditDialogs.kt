@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.qinyan.rimoat.ui.motion.MotionAlertDialog
import androidx.compose.material3.ButtonDefaults
import org.qinyan.rimoat.ui.components.ExpressiveTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteProfile
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
internal fun RemoteEditDialog(
    profile: RemoteProfile,
    displayedError: String?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var room by rememberSaveable(profile.id) { mutableStateOf(profile.room) }
    MotionAlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.screen_edit_remote)) },
        modifier = Modifier.safeDrawingPadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ExpressiveTextField(value = name, onValueChange = { if (it.length <= 100) name = it }, label = { Text(stringResource(R.string.screen_remote_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            ExpressiveTextField(value = room, onValueChange = { if (it.length <= 60) room = it }, label = { Text(stringResource(R.string.screen_room_optional)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            displayedError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, room) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.screen_save)) } },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.screen_cancel)) } })
}
