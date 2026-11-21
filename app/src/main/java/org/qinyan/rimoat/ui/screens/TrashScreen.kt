package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.R

import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.asString
import org.qinyan.rimoat.i18n.rememberLocalizedDateTimeFormatter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import org.qinyan.rimoat.ui.components.contentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.qinyan.rimoat.ui.components.*
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import java.util.Date
import org.qinyan.rimoat.ui.motion.MotionAlertDialog
import org.qinyan.rimoat.ui.motion.StateFade

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(viewModel: RemoteListViewModel, onBack: () -> Unit) {
    val trash by viewModel.trash.collectAsStateWithLifecycle()
    val error by viewModel.errorMessage.collectAsStateWithLifecycle()
    val displayedError = error?.asString()
    val busy by viewModel.trashBusy.collectAsStateWithLifecycle()
    var deleteEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val wide = rememberWideLayout()
    val listState = rememberLazyListState()
    val detailScroll = rememberScrollState()
    val entries = trash.orEmpty()
    val selectedEntry = entries.firstOrNull { it.entryId == selectedEntryId } ?: entries.getOrNull(selectedIndex.coerceAtMost((entries.size - 1).coerceAtLeast(0)))
    LaunchedEffect(entries) { selectedEntryId = selectedEntry?.entryId }
    val active = LocalMotionActive.current
    val snackbar = remember { SnackbarHostState() }
    val date = rememberLocalizedDateTimeFormatter(includeDate = true)
    LaunchedEffect(active) { if (active) viewModel.loadTrash() }
    LaunchedEffect(active, error, displayedError) { if (active && displayedError != null) { snackbar.showSnackbar(displayedError, duration = SnackbarDuration.Long); viewModel.clearError() } }
    Scaffold(topBar = { TopAppBar(title = { Text(AppStrings.get(R.string.screen_trash, "回收站")) }, navigationIcon = {
        ActionIconButton(AppStrings.get(R.string.screen_back, "返回"), onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, AppStrings.get(R.string.screen_back, "返回")) }
    }) }, snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        val list: @Composable () -> Unit = {
        LazyColumn(if (wide) Modifier.fillMaxSize() else Modifier.contentWidth().fillMaxHeight(), state = listState, contentPadding = if (wide) PaddingValues(0.dp) else PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(trash.orEmpty(), key = { it.entryId }) { entry ->
                val color = if (wide && entry.entryId == selectedEntry?.entryId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                Card(modifier = Modifier.clip(MaterialTheme.shapes.medium)
                    .then(if (wide) Modifier.clickable { selectedEntryId = entry.entryId; selectedIndex = entries.indexOf(entry) } else Modifier)
                    .animateItem(fadeInSpec = org.qinyan.rimoat.ui.motion.AppMotion.tween(240), placementSpec = org.qinyan.rimoat.ui.motion.AppMotion.tween(280), fadeOutSpec = org.qinyan.rimoat.ui.motion.AppMotion.tween(220)),
                    colors = CardDefaults.cardColors(containerColor = color)) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val inlineActions = maxWidth >= 360.dp && LocalDensity.current.fontScale < 1.5f
                        val actions: @Composable () -> Unit = {
                            TrashEntryActions(entry.profile.name, !busy,
                                onRestore = { viewModel.restoreDeleted(entry.profile.id, entry.entryId) },
                                onDelete = { deleteEntryId = entry.entryId })
                        }
                        Column {
                            ListItem(headlineContent = { Text(entry.profile.name) }, supportingContent = { Text(date.format(Date(entry.deletedAt))) },
                                leadingContent = { Icon(getCategoryIcon(entry.profile.category), null) },
                                trailingContent = if (!wide && inlineActions) actions else null,
                                colors = ListItemDefaults.colors(containerColor = color))
                            if (!wide && !inlineActions) {
                                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.End) { actions() }
                            }
                        }
                    }
                }
            }
        }
        }
        if (wide && entries.isNotEmpty()) TwoPaneLayout(Modifier.fillMaxSize(), primaryFraction = .4f, primaryMaxWidth = 320.dp,
            primary = list, secondary = {
                selectedEntry?.let { entry ->
                    Column(Modifier.fillMaxSize().verticalScroll(detailScroll).padding(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(entry.profile.name, style = MaterialTheme.typography.headlineSmall)
                        Icon(getCategoryIcon(entry.profile.category), null)
                        if (entry.profile.brand.isNotBlank()) Text(entry.profile.brand, style = MaterialTheme.typography.bodyLarge)
                        if (entry.profile.room.isNotBlank()) Text(entry.profile.room)
                        Text(date.format(Date(entry.deletedAt)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TrashEntryActions(entry.profile.name, !busy,
                            onRestore = { viewModel.restoreDeleted(entry.profile.id, entry.entryId) },
                            onDelete = { deleteEntryId = entry.entryId })
                    }
                }
            }) else list()
        val status = when {
            trash == null -> R.string.screen_loading
            trash.orEmpty().isEmpty() -> R.string.screen_trash_empty
            else -> null
        }
        StateFade(status, Modifier.align(Alignment.Center)) { message ->
            if (message != null) Text(stringResource(message), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
    }
    if (active) trash?.firstOrNull { it.entryId == deleteEntryId }?.let { entry ->
        MotionAlertDialog(onDismissRequest = { if (!busy) deleteEntryId = null },
            title = { Text(AppStrings.get(R.string.screen_delete_forever_question, "彻底删除？")) },
            text = { Text(AppStrings.get(R.string.screen_delete_irreversible, "「%1\$s」删除后无法恢复", entry.profile.name), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton({ viewModel.permanentlyDelete(entry.entryId) { deleteEntryId = null } },
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(if (busy) AppStrings.get(R.string.screen_deleting, "正在删除") else AppStrings.get(R.string.screen_delete_forever, "彻底删除")) }
            },
            dismissButton = { TextButton({ deleteEntryId = null }, enabled = !busy) { Text(AppStrings.get(R.string.screen_cancel, "取消")) } })
    }
}

@Composable
private fun TrashEntryActions(name: String, enabled: Boolean, onRestore: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionIconButton(AppStrings.get(R.string.screen_restore_name, "恢复%1\$s", name), onRestore, enabled = enabled) {
            Icon(Icons.Rounded.Restore, AppStrings.get(R.string.screen_restore_name, "恢复%1\$s", name))
        }
        ActionIconButton(AppStrings.get(R.string.screen_delete_name, "彻底删除%1\$s", name), onDelete, enabled = enabled) {
            Icon(Icons.Rounded.DeleteForever, AppStrings.get(R.string.screen_delete_name, "彻底删除%1\$s", name),
                tint = if (enabled) MaterialTheme.colorScheme.error else LocalContentColor.current)
        }
    }
}
