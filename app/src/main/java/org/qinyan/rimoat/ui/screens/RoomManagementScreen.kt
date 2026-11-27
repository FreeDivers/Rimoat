@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import org.qinyan.rimoat.ui.components.contentWidth
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.asString
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.qinyan.rimoat.ui.components.*
import org.qinyan.rimoat.ui.motion.*
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
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
    val wide = rememberWideLayout()
    val roomsScroll = rememberLazyListState()
    val assigningRoomsScroll = rememberLazyListState()
    val membersScroll = rememberLazyListState()
    val assignmentScroll = rememberLazyListState()
    var previousAssigning by remember { mutableStateOf(assigning) }
    val currentRoom = if (wide) selected?.takeIf { it.isBlank() || it in rooms } ?: rooms.firstOrNull().orEmpty() else selected
    LaunchedEffect(wide, rooms) {
        if (wide && selected != currentRoom) { selected = currentRoom; assigning = false }
    }
    LaunchedEffect(assigning) {
        if (previousAssigning != assigning) {
            if (assigning) assigningRoomsScroll.scrollToItem(roomsScroll.firstVisibleItemIndex, roomsScroll.firstVisibleItemScrollOffset)
            else roomsScroll.scrollToItem(assigningRoomsScroll.firstVisibleItemIndex, assigningRoomsScroll.firstVisibleItemScrollOffset)
            previousAssigning = assigning
        }
    }
    LaunchedEffect(active, error, displayedError) { if (active && displayedError != null) { snackbar.showSnackbar(displayedError, duration = SnackbarDuration.Long); viewModel.clearError() } }
    val motion = AppMotion.current
    val page = RoomPage(currentRoom, assigning)
    fun back() { if (assigning) assigning = false else if (wide) onBack() else if (selected != null) selected = null else onBack() }
    val allRooms = rooms + ""
    ProvideExpressiveCanvas {
    MotionContent(page, contentKey = { if (wide) "wide:${it.assigning}" else "${it.room != null}:${it.assigning}" },
        isBack = { from, to -> to.room == null || (from.assigning && !to.assigning) },
        backTarget = if (assigning) RoomPage(currentRoom) else if (!wide && selected != null) RoomPage(null) else null,
        onBack = ::back) { destination ->
        val roomListState = if (destination.assigning) assigningRoomsScroll else roomsScroll
        val memberListState = if (destination.assigning) assignmentScroll else membersScroll
        val room = destination.room
        val members = remotes.filter { it.room == room }
        val isRoomListRoot = !wide && room == null
        val scrollBehavior = if (isRoomListRoot) TopAppBarDefaults.exitUntilCollapsedScrollBehavior() else null
        Scaffold(
            modifier = if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier,
            containerColor = ExpressiveSurface.canvas,
            topBar = {
                val titleText = if (destination.assigning) stringResource(R.string.screen_assign_remotes)
                    else room?.ifBlank { stringResource(R.string.screen_unassigned) }
                    ?: stringResource(R.string.screen_manage_rooms)
                ExpressivePageTopBar(
                    title = titleText,
                    wide = !isRoomListRoot,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        ActionIconButton(stringResource(R.string.screen_back), ::back) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.screen_back))
                        }
                    },
                    actions = {
                        if (!wide && room == null) ActionIconButton(stringResource(R.string.screen_new_room), { oldName = null; name = ""; edit = true }) {
                            Icon(Icons.Rounded.Add, stringResource(R.string.screen_new_room))
                        }
                    }
                )
            },
            bottomBar = {
                if (!wide && room != null && room.isNotBlank()) Surface(
                    color = ExpressiveSurface.canvas,
                    tonalElevation = 2.dp
                ) {
                    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Button(
                            onClick = { assigning = !destination.assigning },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(if (destination.assigning) stringResource(R.string.screen_done) else stringResource(R.string.screen_assign_remotes))
                        }
                    }
                }
            },
            snackbarHost = { AppSnackbarHost(snackbar) }
        ) { padding ->
            val roomItems: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                    if (wide && !destination.assigning) item(key = "new_room") {
                        FilledTonalButton(
                            onClick = { oldName = null; name = ""; edit = true },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).heightIn(min = 48.dp),
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.screen_new_room))
                        }
                    }
                    itemsIndexed(allRooms, key = { _, item -> "room_$item" }) { index, item ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val count = remotes.count { it.room == item }
                        val isSelected = wide && item == room
                        val color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else ExpressiveSurface.card
                        ExpressiveSegmentedSurface(
                            index = index,
                            count = allRooms.size,
                            interactionSource = interactionSource,
                            containerColor = color,
                            modifier = Modifier.fillMaxWidth().animateItem(
                                fadeInSpec = motion.defaultEffectsSpec(), placementSpec = motion.defaultSpatialSpec(), fadeOutSpec = motion.fastEffectsSpec()
                            )
                        ) {
                            ListItem(
                                headlineContent = { Text(item.ifBlank { stringResource(R.string.screen_unassigned) }, style = MaterialTheme.typography.titleMedium) },
                                supportingContent = { Text(stringResource(R.string.screen_remote_count, count)) },
                                leadingContent = {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(if (item.isBlank()) Icons.Rounded.DevicesOther else Icons.Rounded.MeetingRoom, null)
                                        }
                                    }
                                },
                                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .semantics { this.selected = isSelected }
                                    .clickable(interactionSource = interactionSource, indication = ripple(), role = Role.Button) {
                                        selected = item; assigning = false
                                    }
                                    .padding(vertical = 6.dp)
                            )
                        }
                    }
            }
            val detailItems: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                if (room != null && destination.assigning) {
                    item {
                        Text(room, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    if (remotes.isEmpty()) item(key = "assign_empty") {
                        Text(stringResource(R.string.screen_no_remotes), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.animateItem(fadeInSpec = motion.defaultEffectsSpec(), placementSpec = null, fadeOutSpec = motion.fastEffectsSpec()))
                    }
                    itemsIndexed(remotes, key = { _, remote -> remote.id }) { index, remote ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val checked = remote.room == room
                        ExpressiveSegmentedSurface(
                            index = index,
                            count = remotes.size,
                            interactionSource = interactionSource,
                            containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f) else ExpressiveSurface.card,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = { Text(remote.name) },
                                supportingContent = { Text(remote.room.ifBlank { stringResource(R.string.screen_unassigned) }) },
                                leadingContent = {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(getCategoryIcon(remote), null)
                                        }
                                    }
                                },
                                trailingContent = { Checkbox(checked, null) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.toggleable(checked, interactionSource = interactionSource,
                                    indication = ripple(), role = Role.Checkbox) { viewModel.moveToRoom(remote.id, if (it) room else "") }
                            )
                        }
                    }
                } else if (room != null) {
                    if (room.isNotBlank()) item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = ExpressiveSurface.card,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        ) {
                            FlowRow(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(onClick = { oldName = room; name = room; edit = true }, shapes = ButtonDefaults.shapes()) {
                                    Icon(Icons.Rounded.Edit, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.screen_rename))
                                }
                                TextButton(onClick = { removing = room }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), shapes = ButtonDefaults.shapes()) {
                                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.screen_remove_room))
                                }
                            }
                        }
                    }
                    item {
                        Text(stringResource(R.string.screen_remote), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                    }
                    if (members.isEmpty()) item(key = "room_empty") {
                        Text(stringResource(R.string.screen_no_remotes),
                            modifier = Modifier.animateItem(fadeInSpec = motion.defaultEffectsSpec(), placementSpec = null, fadeOutSpec = motion.fastEffectsSpec()).padding(vertical = 20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    itemsIndexed(members, key = { _, remote -> remote.id }) { index, remote ->
                        ExpressiveSegmentedSurface(
                            index = index,
                            count = members.size,
                            modifier = Modifier.fillMaxWidth().animateItem(
                                fadeInSpec = motion.defaultEffectsSpec(), placementSpec = motion.defaultSpatialSpec(), fadeOutSpec = motion.fastEffectsSpec()
                            )
                        ) {
                            ListItem(
                                headlineContent = { Text(remote.name) },
                                leadingContent = {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(getCategoryIcon(remote), null)
                                        }
                                    }
                                },
                                trailingContent = {
                                    ActionIconButton(stringResource(R.string.screen_move_name, remote.name), { movingRemote = remote.id }) {
                                        Icon(Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.screen_move_name, remote.name))
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
                if (wide && !room.isNullOrBlank()) item(key = "assign_action") {
                    Button(onClick = { assigning = !destination.assigning }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp), shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(if (destination.assigning) R.string.screen_done else R.string.screen_assign_remotes))
                    }
                }
            }
            if (wide) TwoPaneLayout(Modifier.fillMaxSize().padding(padding), primaryFraction = .4f, primaryMaxWidth = 320.dp,
                primary = {
                    LazyColumn(Modifier.fillMaxSize(), state = roomListState, verticalArrangement = Arrangement.spacedBy(ExpressiveShapeTokens.SegmentedGap), content = roomItems)
                }, secondary = {
                    LazyColumn(Modifier.fillMaxSize(), state = memberListState, verticalArrangement = Arrangement.spacedBy(ExpressiveShapeTokens.SegmentedGap), content = detailItems)
                })
            else LazyColumn(Modifier.padding(padding).contentWidth().fillMaxHeight(), state = if (room == null) roomListState else memberListState,
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(ExpressiveShapeTokens.SegmentedGap)) {
                if (room == null) roomItems() else detailItems()
            }
        }
    }
    }
    if (active && edit) MotionAlertDialog(onDismissRequest = { edit = false }, title = { Text(if (oldName == null) stringResource(R.string.screen_add_room) else stringResource(R.string.screen_rename_room)) },
        text = { Column { ExpressiveTextField(name, { name = it.take(60) }, label = { Text(stringResource(R.string.screen_room_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true); displayedError?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton(onClick = { viewModel.editRoom(oldName, name) { if (selected == oldName && oldName != null) selected = name.trim(); edit = false } }, enabled = name.isNotBlank(), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.screen_save)) } },
        dismissButton = { TextButton(onClick = { edit = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.screen_cancel)) } })
    if (active) removing?.let { room -> MotionAlertDialog(onDismissRequest = { removing = null }, title = { Text(stringResource(R.string.screen_remove_room_question, room)) },
        text = { Text(stringResource(R.string.screen_keep_unassigned)) },
        confirmButton = { TextButton(onClick = {
            val remaining = rooms.filterNot { it == room }
            val next = remaining.getOrNull(rooms.indexOf(room).coerceAtMost((remaining.size - 1).coerceAtLeast(0))).orEmpty()
            viewModel.editRoom(room, null) { removing = null; selected = if (wide) next else null; assigning = false }
        },
            shapes = ButtonDefaults.shapes()
        ) { Text(stringResource(R.string.screen_remove), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { removing = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.screen_cancel)) } }) }
    if (active) movingRemote?.let { id -> MotionAlertDialog(onDismissRequest = { movingRemote = null }, title = { Text(stringResource(R.string.screen_move_to_room)) },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(rooms + "") { room ->
            val interactionSource = remember { MutableInteractionSource() }
            val shape = rememberExpressiveShape(interactionSource)
            ListItem(headlineContent = { Text(room.ifBlank { stringResource(R.string.screen_unassigned) }) },
                modifier = Modifier.clip(shape).clickable(interactionSource = interactionSource,
                    indication = ripple(), role = Role.Button) { viewModel.moveToRoom(id, room); movingRemote = null })
        } } }, confirmButton = {}, dismissButton = { TextButton(onClick = { movingRemote = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.screen_cancel)) } }) }
}
