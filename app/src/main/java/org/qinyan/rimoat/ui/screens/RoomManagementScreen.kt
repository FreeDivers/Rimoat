package org.qinyan.rimoat.ui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import org.qinyan.rimoat.ui.components.contentWidth
import org.qinyan.rimoat.R

import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.asString

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.qinyan.rimoat.ui.components.*
import org.qinyan.rimoat.ui.motion.*
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import org.qinyan.rimoat.ui.motion.MotionAlertDialog

private data class RoomPage(val room: String?, val assigning: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoomManagementScreen(viewModel: RemoteListViewModel, onBack: () -> Unit) {
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val remotes by viewModel.allRemotes.collectAsStateWithLifecycle()
    val error by viewModel.errorMessage.collectAsStateWithLifecycle()
    val displayedError = error?.asString()
    val active = LocalMotionActive.current
    val snackbar = remember { SnackbarHostState() }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var assigning by rememberSaveable { mutableStateOf(false) }
    var edit by rememberSaveable { mutableStateOf(false) }
    var oldName by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var movingRemote by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(active, error, displayedError) { if (active && displayedError != null) { snackbar.showSnackbar(displayedError, duration = SnackbarDuration.Long); viewModel.clearError() } }
    val page = RoomPage(selected, assigning)
    fun back() { if (assigning) assigning = false else if (selected != null) selected = null else onBack() }
    MotionContent(page, contentKey = { "${it.room != null}:${it.assigning}" },
        isBack = { from, to -> to.room == null || (from.assigning && !to.assigning) },
        backTarget = if (assigning) RoomPage(selected) else if (selected != null) RoomPage(null) else null,
        onBack = ::back) { destination ->
        val room = destination.room
        val members = remotes.filter { it.room == room }
        Scaffold(topBar = {
            TopAppBar(title = { Text(if (destination.assigning) AppStrings.get(R.string.screen_assign_remotes, "分配遥控器") else room?.ifBlank { AppStrings.get(R.string.screen_unassigned, "未分配") } ?: AppStrings.get(R.string.screen_manage_rooms, "房间管理"),
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = { ActionIconButton(AppStrings.get(R.string.screen_back, "返回"), ::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, AppStrings.get(R.string.screen_back, "返回")) } },
                actions = {
                    if (room == null) ActionIconButton(AppStrings.get(R.string.screen_new_room, "新建房间"), { oldName = null; name = ""; edit = true }) {
                        Icon(Icons.Rounded.Add, AppStrings.get(R.string.screen_new_room, "新建房间"))
                    }
                })
        }, bottomBar = {
            if (room != null && room.isNotBlank()) Surface {
                Button(onClick = { assigning = !destination.assigning },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 48.dp)) {
                    Text(if (destination.assigning) AppStrings.get(R.string.screen_done, "完成") else AppStrings.get(R.string.screen_assign_remotes, "分配遥控器"))
                }
            }
        }, snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
            LazyColumn(Modifier.padding(padding).contentWidth().fillMaxHeight(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (room == null) {
                    items(rooms + "", key = { "room_$it" }) { item ->
                        val count = remotes.count { it.room == item }
                        Card(onClick = { selected = item }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth().animateItem(
                            fadeInSpec = AppMotion.tween(160), placementSpec = AppMotion.tween(220), fadeOutSpec = AppMotion.tween(120))) {
                            ListItem(headlineContent = { Text(item.ifBlank { AppStrings.get(R.string.screen_unassigned, "未分配") }, style = MaterialTheme.typography.titleMedium) },
                                supportingContent = { Text(AppStrings.get(R.string.screen_remote_count, "%1\$d 个遥控器", count)) },
                                leadingContent = { Icon(if (item.isBlank()) Icons.Rounded.DevicesOther else Icons.Rounded.MeetingRoom, null) },
                                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                } else if (destination.assigning) {
                    item { Text(room, style = MaterialTheme.typography.titleMedium) }
                    if (remotes.isEmpty()) item(key = "assign_empty") {
                        Text(AppStrings.get(R.string.screen_no_remotes, "暂无遥控器"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.animateItem(fadeInSpec = AppMotion.tween(160), placementSpec = null, fadeOutSpec = AppMotion.tween(100)))
                    }
                    items(remotes, key = { it.id }) { remote ->
                        val checked = remote.room == room
                        ListItem(headlineContent = { Text(remote.name) }, supportingContent = { Text(remote.room.ifBlank { AppStrings.get(R.string.screen_unassigned, "未分配") }) },
                            leadingContent = { Icon(getCategoryIcon(remote.category), null) }, trailingContent = { Checkbox(checked, null) },
                            modifier = Modifier.clip(MaterialTheme.shapes.medium).toggleable(checked, role = Role.Checkbox) { viewModel.moveToRoom(remote.id, if (it) room else "") })
                    }
                } else {
                    if (room.isNotBlank()) item {
                        FlowRow(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ oldName = room; name = room; edit = true }) {
                                Icon(Icons.Rounded.Edit, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(AppStrings.get(R.string.screen_rename, "重命名"))
                            }
                            TextButton({ removing = room }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(AppStrings.get(R.string.screen_remove_room, "移除房间"))
                            }
                        }
                    }
                    item { Text(AppStrings.get(R.string.screen_remote, "遥控器"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (members.isEmpty()) item(key = "room_empty") {
                        Text(AppStrings.get(R.string.screen_no_remotes, "暂无遥控器"),
                            modifier = Modifier.animateItem(fadeInSpec = AppMotion.tween(160), placementSpec = null, fadeOutSpec = AppMotion.tween(100)).padding(vertical = 20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(members, key = { it.id }) { remote ->
                        ListItem(headlineContent = { Text(remote.name) }, leadingContent = { Icon(getCategoryIcon(remote.category), null) },
                            trailingContent = { ActionIconButton(AppStrings.get(R.string.screen_move_name, "移动%1\$s", remote.name), { movingRemote = remote.id }) { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, AppStrings.get(R.string.screen_move_name, "移动%1\$s", remote.name)) } },
                            modifier = Modifier.animateItem(fadeInSpec = AppMotion.tween(160), placementSpec = AppMotion.tween(220), fadeOutSpec = AppMotion.tween(120)))
                    }
                }
            }
        }
    }
    if (active && edit) MotionAlertDialog(onDismissRequest = { edit = false }, title = { Text(if (oldName == null) AppStrings.get(R.string.screen_add_room, "添加房间") else AppStrings.get(R.string.screen_rename_room, "重命名房间")) },
        text = { Column { OutlinedTextField(name, { name = it.take(60) }, label = { Text(AppStrings.get(R.string.screen_room_name, "房间名称")) }, singleLine = true); displayedError?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton({ viewModel.editRoom(oldName, name) { if (selected == oldName && oldName != null) selected = name.trim(); edit = false } }, enabled = name.isNotBlank()) { Text(AppStrings.get(R.string.screen_save, "保存")) } },
        dismissButton = { TextButton({ edit = false }) { Text(AppStrings.get(R.string.screen_cancel, "取消")) } })
    if (active) removing?.let { room -> MotionAlertDialog(onDismissRequest = { removing = null }, title = { Text(AppStrings.get(R.string.screen_remove_room_question, "移除%1\$s？", room)) },
        text = { Text(AppStrings.get(R.string.screen_keep_unassigned, "遥控器将保留在未分配中")) },
        confirmButton = { TextButton({ viewModel.editRoom(room, null) { removing = null; selected = null } }) { Text(AppStrings.get(R.string.screen_remove, "移除"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ removing = null }) { Text(AppStrings.get(R.string.screen_cancel, "取消")) } }) }
    if (active) movingRemote?.let { id -> MotionAlertDialog(onDismissRequest = { movingRemote = null }, title = { Text(AppStrings.get(R.string.screen_move_to_room, "移入房间")) },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(rooms + "") { room ->
            ListItem(headlineContent = { Text(room.ifBlank { AppStrings.get(R.string.screen_unassigned, "未分配") }) },
                modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(role = Role.Button) { viewModel.moveToRoom(id, room); movingRemote = null })
        } } }, confirmButton = {}, dismissButton = { TextButton({ movingRemote = null }) { Text(AppStrings.get(R.string.screen_cancel, "取消")) } }) }
}
