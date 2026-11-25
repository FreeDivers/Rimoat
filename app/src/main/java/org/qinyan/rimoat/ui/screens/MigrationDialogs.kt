@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import org.qinyan.rimoat.ui.components.rememberExpressiveShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.ui.components.SimpleDropdownMenu
import org.qinyan.rimoat.ui.components.SimpleDropdownMenuItem
import org.qinyan.rimoat.ui.motion.MotionBasicAlertDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MigrationImportDialog(document: ImportDocument, existingIds: Set<String>, busy: Boolean,
    onDismiss: () -> Unit, onImport: (Map<String, ConflictChoice>, PortablePreferences?) -> Unit) {
    val choicesSaver = mapSaver<Map<String, ConflictChoice>>(
        save = { values -> values.mapValues { it.value.name } },
        restore = { values -> values.mapValues { ConflictChoice.valueOf(it.value as String) } })
    var choices by rememberSaveable(document, stateSaver = choicesSaver) { mutableStateOf(emptyMap()) }
    var dynamic by rememberSaveable(document) { mutableStateOf(false) }
    var black by rememberSaveable(document) { mutableStateOf(false) }
    val summary = importSummary(document.backup, existingIds, choices)
    MotionBasicAlertDialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = AlertDialogDefaults.shape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp),
                contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text(stringResource(R.string.migration_import_title), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }) }
                item { Text(stringResource(R.string.migration_summary, summary.added, summary.conflicts,
                    summary.skipped + document.skippedSignals.size, summary.overwritten)) }
                if (document.backup.rooms.isNotEmpty()) item { Text(stringResource(R.string.migration_rooms, document.backup.rooms.size)) }
                items(document.backup.remotes, key = { it.id }) { profile ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium)
                        if (profile.id in existingIds) {
                            var expanded by remember { mutableStateOf(false) }
                            val current = choices[profile.id] ?: ConflictChoice.KEEP
                            val currentLabel = stringResource(conflictLabel(current))
                            val description = stringResource(R.string.migration_conflict_action, profile.name, currentLabel)
                            Box {
                                OutlinedButton(onClick = { expanded = true }, enabled = !busy,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = description },
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Text(currentLabel, Modifier.weight(1f))
                                    Icon(Icons.Rounded.ExpandMore, null)
                                }
                                val conflictEntries = ConflictChoice.entries
                                SimpleDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    selectedIndex = conflictEntries.indexOf(current).takeIf { it >= 0 },
                                    itemCount = conflictEntries.size
                                ) {
                                    conflictEntries.forEach { choice ->
                                        SimpleDropdownMenuItem(
                                            text = { Text(stringResource(conflictLabel(choice))) },
                                            selected = choice == current,
                                            onClick = { choices = choices + (profile.id to choice); expanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                items(document.skippedSignals) { signal ->
                    Text(stringResource(R.string.migration_skipped_signal, signal.name, signal.protocol))
                }
                document.backup.preferences?.let { preferences ->
                    item { Text(stringResource(R.string.migration_preferences_title), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }) }
                    if (preferences.dynamicColors != null) item {
                        MigrationToggle(stringResource(R.string.migration_dynamic_colors), dynamic, !busy) { dynamic = it }
                    }
                    if (preferences.pureBlack != null) item {
                        MigrationToggle(stringResource(R.string.migration_pure_black), black, !busy) { black = it }
                    }
                }
                item { Text(stringResource(R.string.migration_snapshot_note), style = MaterialTheme.typography.bodyMedium) }
                item {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss, enabled = !busy, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) }
                        TextButton(enabled = !busy && (summary.added + summary.overwritten > 0 || document.backup.rooms.isNotEmpty() || dynamic || black),
                            onClick = {
                                val prefs = document.backup.preferences?.let {
                                    PortablePreferences(it.dynamicColors.takeIf { dynamic }, it.pureBlack.takeIf { black })
                                }?.takeIf { it.dynamicColors != null || it.pureBlack != null }
                                onImport(choices, prefs)
                            },
                            shapes = ButtonDefaults.shapes()
                        ) { Text(stringResource(R.string.migration_import_confirm)) }
                    }
                }
            }
        }
    }
}

private fun conflictLabel(choice: ConflictChoice): Int = when (choice) {
    ConflictChoice.KEEP -> R.string.migration_keep
    ConflictChoice.OVERWRITE -> R.string.migration_overwrite
    ConflictChoice.COPY -> R.string.migration_copy
}

@Composable
internal fun MigrationToggle(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberExpressiveShape(interactionSource)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape)
        .toggleable(value = checked, interactionSource = interactionSource, indication = ripple(),
            enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, Modifier.weight(1f))
    }
}
