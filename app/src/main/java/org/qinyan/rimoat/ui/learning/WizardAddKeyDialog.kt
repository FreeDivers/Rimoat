package org.qinyan.rimoat.ui.learning

import org.qinyan.rimoat.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.components.*
import org.qinyan.rimoat.ui.motion.*
import org.qinyan.rimoat.ui.motion.MotionAlertDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WizardAddKeyDialog(
    keyName: String,
    onNameChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val dialogKeyboard = LocalSoftwareKeyboardController.current
    MotionAlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(stringResource(R.string.screen_add_button), modifier = Modifier.semantics { heading() }) },
        text = {
            ExpressiveTextField(
                value = keyName,
                onValueChange = { onNameChange(it.take(100)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.screen_button_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    dialogKeyboard?.hide()
                    if (keyName.isNotBlank()) {
                        HapticHelper.performSelection(haptic, context)
                        onAdd(keyName)
                        onDismiss()
                    }
                })
            )
        },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    dialogKeyboard?.hide()
                    HapticHelper.performSelection(haptic, context)
                    onAdd(keyName)
                    onDismiss()
                },
                enabled = keyName.isNotBlank(),

                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.screen_add)) }
        },
        dismissButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    dialogKeyboard?.hide()
                    HapticHelper.performSelection(haptic, context)
                    onDismiss()
                },

                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.screen_cancel)) }
        }
    )
}
