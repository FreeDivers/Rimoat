package org.qinyan.rimoat.widget

import org.qinyan.rimoat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.glance.appwidget.compose
import org.qinyan.rimoat.ui.motion.MotionAlertDialog
import org.qinyan.rimoat.ui.components.TwoPaneLayout

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WidgetShortcutPicker(
    choosingRemote: Boolean,
    choosing: Boolean,
    currentDraft: WidgetConfiguration?,
    snapshot: WidgetSnapshot,
    kind: WidgetKind,
    wide: Boolean,
    chooseRemoteId: String,
    change: (WidgetConfiguration) -> Unit,
    onChooseRemote: (String) -> Unit,
    onDismissRemote: () -> Unit,
    onDismissShortcut: () -> Unit
) {
    val context = LocalContext.current
    if (choosingRemote && currentDraft != null) {
        MotionAlertDialog(onDismissRequest = { onDismissRemote() }, title = { Text(stringResource(R.string.widget_select_remote)) }, text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(snapshot.profiles, key = { it.id }) { profile ->
                    val selected = profile.id == currentDraft.remoteId
                    ListItem(headlineContent = { Text(profile.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        supportingContent = profile.room.takeIf(String::isNotBlank)?.let { { Text(it) } },
                        trailingContent = { RadioButton(selected, onClick = null) },
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).selectable(selected, role = Role.RadioButton) {
                            val commands = snapshot.commands[profile.id].orEmpty()
                            val primary = WidgetCommands.default(profile, commands, context)
                            val keys = if (kind == WidgetKind.SINGLE) listOfNotNull(primary) else
                                (listOfNotNull(primary) + commands.take(6).map { WidgetShortcut(profile.id, it.id) }).distinct().take(6)
                            change(currentDraft.copy(remoteId = profile.id, shortcuts = keys))
                            onDismissRemote()
                        })
                }
            }
        }, confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { onDismissRemote() }) { Text(stringResource(R.string.widget_cancel)) } })
    }
    if (choosing && currentDraft != null) {
        val draft = currentDraft
        val boundProfile = snapshot.profiles.firstOrNull { it.id == draft.remoteId }
        val lockedToRemote = kind == WidgetKind.STATUS && boundProfile != null
        val showTwoPane = wide && !lockedToRemote
        val selectedProfile = if (lockedToRemote) boundProfile
            else snapshot.profiles.firstOrNull { it.id == chooseRemoteId } ?: if (showTwoPane) snapshot.profiles.firstOrNull() else null
        MotionAlertDialog(onDismissRequest = { onDismissShortcut() }, title = { Text(if (selectedProfile == null) stringResource(R.string.widget_select_remote) else selectedProfile.name) },
            modifier = if (showTwoPane) Modifier.safeDrawingPadding().fillMaxWidth().widthIn(max = 840.dp) else Modifier,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = !showTwoPane),
            text = {
                val remotes: @Composable () -> Unit = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(snapshot.profiles, key = { it.id }) { profile ->
                        ListItem(headlineContent = { Text(profile.name) }, supportingContent = if (profile.room.isNotBlank()) ({ Text(profile.room) }) else null,
                            colors = ListItemDefaults.colors(containerColor = if (showTwoPane && selectedProfile?.id == profile.id) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.clickable { onChooseRemote(profile.id) })
                    }
                }
                }
                val buttons: @Composable () -> Unit = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    if (selectedProfile != null) {
                        val commands = snapshot.commands[selectedProfile.id].orEmpty()
                        if (commands.isEmpty()) item { Text(stringResource(R.string.widget_no_buttons)) }
                        items(commands, key = { it.id }) { command ->
                            val shortcut = WidgetShortcut(selectedProfile.id, command.id)
                            ListItem(headlineContent = { Text(command.displayLabel(selectedProfile, context)) }, trailingContent = if (shortcut in draft.shortcuts) ({ Icon(Icons.Rounded.Check, stringResource(R.string.widget_selected)) }) else null,
                                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                                modifier = Modifier.clickable {
                                    val next = when (kind) {
                                        WidgetKind.SINGLE -> listOf(shortcut)
                                        WidgetKind.PANEL -> (draft.shortcuts + shortcut).distinct()
                                        WidgetKind.STATUS -> (draft.shortcuts.filter { it.remoteId == selectedProfile.id } + shortcut).distinct()
                                    }
                                    change(draft.copy(remoteId = if (kind == WidgetKind.PANEL) draft.remoteId else selectedProfile.id, shortcuts = next))
                                    onDismissShortcut()
                                })
                        }
                    }
                }
                }
                if (showTwoPane) BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    TwoPaneLayout(Modifier.fillMaxSize(), primaryFraction = .4f, primary = remotes, secondary = buttons)
                } else if (selectedProfile == null) remotes() else buttons()
            }, confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { onDismissShortcut() }) { Text(stringResource(R.string.widget_cancel)) } },
            dismissButton = if (!showTwoPane && kind == WidgetKind.PANEL && selectedProfile != null) ({ TextButton(shapes = ButtonDefaults.shapes(), onClick = { onChooseRemote("") }) { Text(stringResource(R.string.widget_change_remote)) } }) else null)
    }
}
