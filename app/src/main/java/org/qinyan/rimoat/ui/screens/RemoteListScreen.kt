@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.ui.components.rememberExpressiveShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.ui.semantics.Role
import org.qinyan.rimoat.ui.components.getCategoryIcon
import androidx.compose.material3.LinearWavyProgressIndicator
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.asString
import org.qinyan.rimoat.ui.theme.Spacing
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import org.qinyan.rimoat.ui.motion.ExpandSection
import org.qinyan.rimoat.ui.motion.FadeVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.qinyan.rimoat.ui.components.ExpressivePageTopBar
import org.qinyan.rimoat.ui.components.ExpressiveShapeTokens
import org.qinyan.rimoat.ui.components.rememberSegmentedExpressiveShape
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.FilterChip
import org.qinyan.rimoat.ui.components.rememberWideLayout
import org.qinyan.rimoat.ui.components.TwoPaneLayout
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.SoundEffectOnInteraction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Upload
import org.qinyan.rimoat.ui.motion.MotionAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import org.qinyan.rimoat.ui.components.ExpressiveDropdownMenu
import org.qinyan.rimoat.ui.components.ExpressiveDropdownMenuGroup
import org.qinyan.rimoat.ui.components.ExpressiveDropdownMenuPopup
import org.qinyan.rimoat.ui.components.ExpressiveMenuItem
import org.qinyan.rimoat.ui.components.ExpressiveSelectableMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import org.qinyan.rimoat.ui.components.ActionIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.SnackbarDuration
import org.qinyan.rimoat.ui.components.AppSnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.window.DialogProperties
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.ui.components.HardwareStatusButton
import org.qinyan.rimoat.ui.components.TransmitterSelectionBottomSheet
import org.qinyan.rimoat.ui.components.clearSearchFocusOnTapOutside
import org.qinyan.rimoat.ui.components.rememberSearchFocusController
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import java.util.UUID
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteListScreen(
    viewModel: RemoteListViewModel,
    onRemoteClick: (String) -> Unit,
    onAddRemoteClick: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val motion = org.qinyan.rimoat.ui.motion.AppMotion.current
    val wide = rememberWideLayout()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val filtersScroll = rememberScrollState()
    var previousWide by rememberSaveable { mutableStateOf(wide) }
    var anchorRemoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorOffset by rememberSaveable { mutableStateOf(0) }
    val screenActive = LocalMotionActive.current
    val remotes by viewModel.filteredRemotes.collectAsStateWithLifecycle()
    val currentRemotes by rememberUpdatedState(remotes)
    LaunchedEffect(wide) {
        if (previousWide != wide) {
            val loaded = snapshotFlow { currentRemotes }.first { it.isNotEmpty() }
            val index = loaded.indexOfFirst { it.id == anchorRemoteId }.coerceAtLeast(0)
            if (wide) gridState.scrollToItem(index, anchorOffset) else listState.scrollToItem(index, anchorOffset)
            previousWide = wide
        }
        snapshotFlow {
            if (wide) gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.let { it.key to gridState.firstVisibleItemScrollOffset }
            else listState.layoutInfo.visibleItemsInfo.firstOrNull()?.let { it.key to listState.firstVisibleItemScrollOffset }
        }.collect { position ->
            if (position?.first is String) {
                anchorRemoteId = position.first as String
                anchorOffset = position.second
            }
        }
    }
    val allRemotes by viewModel.allRemotes.collectAsStateWithLifecycle()
    val trulyEmpty = allRemotes.isEmpty()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val categories = categoryOptions().map { it.first }
    val layoutDirection = LocalLayoutDirection.current
    val transmitterType by viewModel.transmitterType.collectAsStateWithLifecycle()
    val isHardwareReady by viewModel.isHardwareReady.collectAsStateWithLifecycle()
    val isForceMock by viewModel.isForceMock.collectAsStateWithLifecycle()
    var showTransmitterSheet by rememberSaveable { mutableStateOf(false) }

    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val displayedError = errorMessage?.asString()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(screenActive, errorMessage, displayedError) {
        if (screenActive) errorMessage?.let {
            snackbar.showSnackbar(checkNotNull(displayedError), duration = SnackbarDuration.Long)
            viewModel.clearError()
        }
    }

    var remoteToDelete by remember { mutableStateOf<RemoteProfile?>(null) }

    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val selectedRoom by viewModel.selectedRoom.collectAsStateWithLifecycle()
    val recentFirst by viewModel.recentFirst.collectAsStateWithLifecycle()
    val deletedId by viewModel.deletedId.collectAsStateWithLifecycle()
    val transferBusy by viewModel.transferBusy.collectAsStateWithLifecycle()
    val transferMessage by viewModel.transferMessage.collectAsStateWithLifecycle()
    val displayedTransfer = transferMessage?.asString()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportedUri by rememberSaveable { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri, exportId) { exportedUri = uri.toString() }
    }
    LaunchedEffect(screenActive, deletedId) {
        if (screenActive) deletedId?.let { id ->
            try {
                val result = snackbar.showSnackbar(
                    AppStrings.get(R.string.screen_trashed, "已移入回收站"), actionLabel = AppStrings.get(R.string.screen_undo, "撤销"), duration = SnackbarDuration.Long
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) viewModel.restoreDeleted(id)
            } finally {
                // Do not replay a cancelled notice on return or consume a newer deletion.
                viewModel.consumeDeleted(id)
            }
        }
    }
    LaunchedEffect(screenActive, transferMessage, displayedTransfer) {
        if (screenActive) transferMessage?.let {
            snackbar.showSnackbar(checkNotNull(displayedTransfer), duration = SnackbarDuration.Short)
            viewModel.clearTransferMessage()
        }
    }

    val searchFocus = rememberSearchFocusController()
    val scrollBehavior = if (wide) null else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    ProvideExpressiveCanvas {
    Scaffold(
        modifier = Modifier
            .let { if (scrollBehavior != null) it.nestedScroll(scrollBehavior.nestedScrollConnection) else it }
            .clearSearchFocusOnTapOutside(searchFocus),
        containerColor = ExpressiveSurface.canvas,
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            ExpressivePageTopBar(
                title = stringResource(R.string.app_name),
                wide = wide,
                subtitle = if (!trulyEmpty) stringResource(R.string.screen_remote_count, allRemotes.size) else null,
                scrollBehavior = scrollBehavior,
                actions = {
                    HardwareStatusButton(
                        transmitterType = transmitterType,
                        isReady = isHardwareReady,
                        forceMock = isForceMock,
                        onClick = { showTransmitterSheet = true }
                    )
                    Spacer(Modifier.width(4.dp))
                    ActionIconButton(AppStrings.get(R.string.screen_settings, "设置"), onClick = onSettingsClick) {
                        Icon(Icons.Rounded.Settings, contentDescription = AppStrings.get(R.string.screen_settings, "设置"))
                    }
                }
            )
        },
        floatingActionButton = {
            FadeVisibility(visible = !trulyEmpty && !wide) {
                ExtendedFloatingActionButton(
                    onClick = { if (!trulyEmpty) onAddRemoteClick() },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                        .clearAndSetSemantics {
                        if (!trulyEmpty) {
                            contentDescription = AppStrings.get(R.string.screen_add_remote, "添加遥控器")
                            role = androidx.compose.ui.semantics.Role.Button
                            onClick { onAddRemoteClick(); true }
                        }
                    },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(AppStrings.get(R.string.screen_add_remote, "添加遥控器")) })
            }
        }
    ) { padding ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(padding)
                .pointerInput(screenActive, trulyEmpty, selectedCategory, layoutDirection) {
                    if (!screenActive || trulyEmpty) return@pointerInput
                    val threshold = 64.dp.toPx()
                    var dragDistance = 0f
                    // Child card gestures and scrolling consume their own drags first.
                    detectHorizontalDragGestures(
                        onDragStart = { dragDistance = 0f },
                        onDragCancel = { dragDistance = 0f },
                        onDragEnd = {
                            val distance = if (layoutDirection == LayoutDirection.Rtl) -dragDistance else dragDistance
                            val step = when {
                                distance <= -threshold -> 1
                                distance >= threshold -> -1
                                else -> 0
                            }
                            val currentIndex = categories.indexOf(selectedCategory)
                            val nextIndex = (currentIndex + step).coerceIn(categories.indices)
                            if (nextIndex != currentIndex) viewModel.selectCategory(categories[nextIndex])
                        }
                    ) { _, amount -> dragDistance += amount }
                }
        ) {
            val searchBar: @Composable (Modifier) -> Unit = { barModifier ->
                org.qinyan.rimoat.ui.components.RemoteSearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    placeholder = AppStrings.get(R.string.screen_search_remote, "搜索遥控器"),
                    modifier = barModifier,
                    focusController = searchFocus
                )
            }
            val roomAndSortBar: @Composable () -> Unit = {
                RoomAndSortBar(
                    rooms = rooms,
                    selectedRoom = selectedRoom,
                    recentFirst = recentFirst,
                    onSetRecentFirst = viewModel::setRecentFirst,
                    onSelectRoom = viewModel::selectRoom
                )
            }
            val remoteRow: @Composable (RemoteProfile, Int, Int, Modifier) -> Unit = { remote, index, count, itemModifier ->
                org.qinyan.rimoat.ui.components.SwipeRemoteCard(remote.isFavorite,
                    onFavorite = { viewModel.toggleFavorite(remote.id) }, onRemove = { viewModel.deleteRemote(remote.id) }, modifier = itemModifier) {
                    RemoteCard(
                        remote = remote,
                        onClick = { onRemoteClick(remote.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(remote.id) },
                        onDelete = { remoteToDelete = remote },
                        onEdit = { editingId = remote.id },
                        onExport = { exportId = remote.id; exportLauncher.launch("Rimoat-remote.json") },
                        index = index,
                        count = count
                    )
                }
            }
            val emptyState: @Composable () -> Unit = {
                FadeVisibility(visible = remotes.isEmpty()) {
                    EmptyStateView(
                        isSearch = !trulyEmpty,
                        hasSearchQuery = searchQuery.isNotBlank(),
                        showAddAction = !wide,
                        actionsEnabled = remotes.isEmpty(),
                        onResetSearch = {
                            if (searchQuery.isNotBlank()) viewModel.setSearchQuery("")
                            else {
                                viewModel.selectCategory(null)
                                viewModel.selectRoom(null)
                            }
                        },
                        onAddRemote = onAddRemoteClick
                    )
                }
            }
            if (wide) {
                TwoPaneLayout(primaryFraction = .4f, primaryMaxWidth = 320.dp, primary = {
                    Column(Modifier.fillMaxSize().testTag("home_filters").verticalScroll(filtersScroll),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!trulyEmpty) {
                                searchBar(Modifier)
                                CategoryFilterChips(
                                    selectedCategory = selectedCategory,
                                    onSelectCategory = { viewModel.selectCategory(it) }
                                )
                                roomAndSortBar()
                        }
                        Spacer(Modifier.weight(1f))
                        androidx.compose.material3.Button(onClick = onAddRemoteClick, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.screen_add_remote))
                        }
                    }
                }, secondary = {
                    Column(Modifier.fillMaxSize()) {
                        ExpandSection(visible = transferBusy) {
                            androidx.compose.material3.LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Box(Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(240.dp),
                                state = gridState,
                                modifier = Modifier.fillMaxSize().testTag("home_results"),
                                contentPadding = PaddingValues(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                verticalArrangement = Arrangement.spacedBy(Spacing.small)
                            ) {
                                gridItems(remotes, key = { it.id }) { remote ->
                                    remoteRow(remote, 0, 1, Modifier.animateItem(
                                        fadeInSpec = motion.defaultEffectsSpec(),
                                        placementSpec = motion.defaultSpatialSpec(),
                                        fadeOutSpec = motion.fastEffectsSpec()))
                                }
                            }
                            emptyState()
                        }
                    }
                })
            } else {
                Column(Modifier.fillMaxSize()) {
                    ExpandSection(visible = !trulyEmpty) {
                        Column {
                            searchBar(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            CategoryFilterBar(
                                selectedCategory = selectedCategory,
                                onSelectCategory = { viewModel.selectCategory(it) }
                            )
                            roomAndSortBar()
                        }
                    }
                    ExpandSection(visible = transferBusy) {
                        androidx.compose.material3.LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    // Remotes List
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("home_results"),
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(ExpressiveShapeTokens.SegmentedGap)
                        ) {
                            itemsIndexed(remotes, key = { _, item -> item.id }) { index, remote ->
                                remoteRow(remote, index, remotes.size, Modifier.animateItem(
                                    fadeInSpec = motion.defaultEffectsSpec(),
                                    placementSpec = motion.defaultSpatialSpec(),
                                    fadeOutSpec = motion.fastEffectsSpec()))
                            }
                            // Bottom space so FAB doesn't obscure the last card
                            item {
                                Spacer(modifier = Modifier.height(84.dp))
                            }
                        }
                        emptyState()
                    }
                }
            }
        }
    }
    }

    if (screenActive) {
        // Delete Confirmation Dialog
        if (remoteToDelete != null) {
            MotionAlertDialog(
                onDismissRequest = { remoteToDelete = null },
                title = { Text(AppStrings.get(R.string.screen_delete_remote, "删除遥控器")) },
                text = { Text(AppStrings.get(R.string.screen_trash_question, "将「%1\$s」移入回收站？", remoteToDelete?.name.orEmpty())) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            remoteToDelete?.let { viewModel.deleteRemote(it.id) }
                            remoteToDelete = null
                        },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(AppStrings.get(R.string.screen_delete, "删除"), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { remoteToDelete = null }, shapes = ButtonDefaults.shapes()) {
                        Text(AppStrings.get(R.string.screen_cancel, "取消"))
                    }
                }
            )
        }

        remotes.firstOrNull { it.id == editingId }?.let { profile ->
            var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
            var room by rememberSaveable(profile.id) { mutableStateOf(profile.room) }
            MotionAlertDialog(onDismissRequest = { editingId = null }, title = { Text(AppStrings.get(R.string.screen_edit_remote, "编辑遥控器")) },
                modifier = Modifier.safeDrawingPadding(),
                properties = DialogProperties(decorFitsSystemWindows = false),
                text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { if (it.length <= 100) name = it }, label = { Text(AppStrings.get(R.string.screen_remote_name, "遥控器名称")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = room, onValueChange = { if (it.length <= 60) room = it }, label = { Text(AppStrings.get(R.string.screen_room_optional, "房间（可选）")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    displayedError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                } },
                confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { viewModel.updateDetails(profile.id, name, room) { editingId = null } }, shapes = ButtonDefaults.shapes()) { Text(AppStrings.get(R.string.screen_save, "保存")) } },
                dismissButton = { TextButton(onClick = { editingId = null }, shapes = ButtonDefaults.shapes()) { Text(AppStrings.get(R.string.screen_cancel, "取消")) } })
        }
        exportedUri?.let { saved ->
            BackupSavedDialog(saved, onDismiss = { exportedUri = null }, onError = viewModel::reportTransferError)
        }
        if (showTransmitterSheet) {
            TransmitterSelectionBottomSheet(
                transmitterManager = viewModel.transmitterManager,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                onDismissRequest = { showTransmitterSheet = false }
            )
        }
    }
}

private fun categoryOptions(): List<Pair<DeviceCategory?, String>> = listOf(
    null to AppStrings.get(R.string.screen_category_all, "全部"),
    DeviceCategory.TV to AppStrings.get(R.string.screen_category_tv, "电视"),
    DeviceCategory.AC to AppStrings.get(R.string.screen_category_ac, "空调"),
    DeviceCategory.FAN to AppStrings.get(R.string.screen_category_fan, "风扇"),
    DeviceCategory.PROJECTOR to AppStrings.get(R.string.screen_category_projector, "投影仪"),
    DeviceCategory.AUDIO to AppStrings.get(R.string.screen_category_audio, "音响"),
    DeviceCategory.CUSTOM to AppStrings.get(R.string.screen_category_custom, "自定义")
)

/** Wrapping choices keep every category reachable in a narrow landscape pane. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryFilterChips(
    selectedCategory: DeviceCategory?,
    onSelectCategory: (DeviceCategory?) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        categoryOptions().forEach { (category, label) ->
            val checked = selectedCategory == category
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelectCategory(category) },
                colors = ToggleButtonDefaults.colors(
                    containerColor = ExpressiveSurface.card,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    selected = checked
                    role = Role.RadioButton
                }
            ) { Text(label) }
        }
    }
}

private val CategoryButtonRigidHorizontalPadding = 6.dp
private val CategoryButtonCompressibleHorizontalPadding = 10.dp
private val CategoryButtonCompressionLimit = 6.dp

/**
 * Reserves resting horizontal padding for [ButtonGroup] intrinsic measurement while allowing
 * inner slack to absorb neighbor compression and spring overshoot without squeezing [content].
 */
@Composable
private fun CompressibleHorizontalSlot(
    horizontalSlack: Dp,
    content: @Composable () -> Unit
) {
    val measurePolicy = remember(horizontalSlack) {
        object : MeasurePolicy {
            override fun MeasureScope.measure(
                measurables: List<Measurable>,
                constraints: Constraints
            ): MeasureResult {
                val slackPx = horizontalSlack.roundToPx()
                val placeable = measurables.single().measure(constraints.copy(minWidth = 0))
                val width = constraints.constrainWidth(placeable.width + slackPx * 2)
                val height = constraints.constrainHeight(placeable.height)
                return layout(width, height) {
                    placeable.placeRelative(
                        x = (width - placeable.width) / 2,
                        y = (height - placeable.height) / 2
                    )
                }
            }

            override fun IntrinsicMeasureScope.minIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int
            ): Int = measurables.single().minIntrinsicWidth(height)

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int
            ): Int = measurables.single().maxIntrinsicWidth(height) + horizontalSlack.roundToPx() * 2

            override fun IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int
            ): Int {
                val innerWidth = if (width == Constraints.Infinity) {
                    width
                } else {
                    (width - horizontalSlack.roundToPx() * 2).coerceAtLeast(0)
                }
                return measurables.single().minIntrinsicHeight(innerWidth)
            }

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int
            ): Int {
                val innerWidth = if (width == Constraints.Infinity) {
                    width
                } else {
                    (width - horizontalSlack.roundToPx() * 2).coerceAtLeast(0)
                }
                return measurables.single().maxIntrinsicHeight(innerWidth)
            }
        }
    }
    Layout(content = content, measurePolicy = measurePolicy)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterBar(
    selectedCategory: DeviceCategory?,
    onSelectCategory: (DeviceCategory?) -> Unit
) {
    val categories = categoryOptions()
    val buttonPadding = PaddingValues(
        horizontal = CategoryButtonRigidHorizontalPadding,
        vertical = 8.dp
    )

    ButtonGroup(
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp).selectableGroup(),
        expandedRatio = .08f,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { (category, label) ->
            customItem(
                buttonGroupContent = {
                    val checked = selectedCategory == category
                    val interactionSource = remember { MutableInteractionSource() }
                    val bringIntoView = remember { BringIntoViewRequester() }
                    LaunchedEffect(checked) {
                        if (checked) {
                            withFrameNanos { }
                            bringIntoView.bringIntoView()
                        }
                    }
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { onSelectCategory(category) },
                        interactionSource = interactionSource,
                        colors = ToggleButtonDefaults.colors(
                            containerColor = ExpressiveSurface.card,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = buttonPadding,
                        modifier = Modifier.heightIn(min = 48.dp)
                            .animateWidth(interactionSource, compressionLimit = CategoryButtonCompressionLimit)
                            .bringIntoViewRequester(bringIntoView).semantics {
                                selected = checked
                                role = Role.RadioButton
                            }
                    ) {
                        CompressibleHorizontalSlot(CategoryButtonCompressibleHorizontalPadding) {
                            Text(label, maxLines = 1, softWrap = false)
                        }
                    }
                },
                menuContent = { menu ->
                    SoundEffectOnInteraction(enabled = false) {
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { menu.dismiss(); onSelectCategory(category) }
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun RoomAndSortBar(
    rooms: List<String>,
    selectedRoom: String?,
    recentFirst: Boolean,
    onSelectRoom: (String?) -> Unit,
    onSetRecentFirst: (Boolean) -> Unit
) {
    val screenActive = LocalMotionActive.current
    var showRooms by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    LaunchedEffect(screenActive) {
        if (!screenActive) {
            showRooms = false
            showSort = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            val roomActive = selectedRoom != null
            FilledTonalButton(
                onClick = { showRooms = true },
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = AppStrings.get(R.string.screen_room, "房间")
                    stateDescription = AppStrings.get(R.string.screen_room_expansion, "%1\$s，%2\$s", selectedRoom ?: AppStrings.get(R.string.screen_all_rooms, "全部房间"), if (showRooms) AppStrings.get(R.string.screen_expanded, "已展开") else AppStrings.get(R.string.screen_collapsed, "已收起"))
                },
                contentPadding = PaddingValues(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (roomActive) MaterialTheme.colorScheme.secondaryContainer else ExpressiveSurface.card,
                    contentColor = if (roomActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                ),
                shapes = ButtonDefaults.shapes()
            ) {
                Icon(
                    Icons.Rounded.MeetingRoom,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (roomActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = selectedRoom ?: AppStrings.get(R.string.screen_all_rooms, "全部房间"),
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
            val roomOptions = buildList {
                add(null to AppStrings.get(R.string.screen_all_rooms, "全部房间"))
                rooms.forEach { add(it to it) }
            }
            ExpressiveDropdownMenu(
                expanded = screenActive && showRooms,
                onDismissRequest = { showRooms = false },
                selectable = true
            ) {
                roomOptions.forEachIndexed { index, (room, label) ->
                    ExpressiveSelectableMenuItem(
                        selected = selectedRoom == room,
                        onClick = {
                            showRooms = false
                            onSelectRoom(room)
                        },
                        text = { Text(label) },
                        index = index,
                        count = roomOptions.size
                    )
                }
            }
        }
        Box {
            Surface(
                shape = CircleShape,
                color = if (recentFirst) MaterialTheme.colorScheme.secondaryContainer else ExpressiveSurface.card,
                contentColor = if (recentFirst) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
            ) {
                ActionIconButton(AppStrings.get(R.string.screen_sort, "排序"),
                    onClick = { showSort = true },
                    modifier = Modifier.semantics {
                        stateDescription = if (recentFirst) AppStrings.get(R.string.screen_recent, "最近使用") else AppStrings.get(R.string.screen_default_sort, "默认排序")
                    }
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = AppStrings.get(R.string.screen_sort, "排序"))
                }
            }
            val sortOptions = listOf(
                false to AppStrings.get(R.string.screen_default_sort, "默认排序"),
                true to AppStrings.get(R.string.screen_recent, "最近使用")
            )
            ExpressiveDropdownMenu(
                expanded = screenActive && showSort,
                onDismissRequest = { showSort = false },
                selectable = true
            ) {
                sortOptions.forEachIndexed { index, (recent, label) ->
                    ExpressiveSelectableMenuItem(
                        selected = recentFirst == recent,
                        onClick = {
                            showSort = false
                            onSetRecentFirst(recent)
                        },
                        text = { Text(label) },
                        index = index,
                        count = sortOptions.size
                    )
                }
            }
        }
    }
}

@Composable
fun RemoteCard(
    remote: RemoteProfile,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    onExport: () -> Unit = {},
    index: Int = 0,
    count: Int = 1
) {
    val interactionSource = remember { MutableInteractionSource() }
    val cardShape = rememberSegmentedExpressiveShape(interactionSource, index, count)
    var showActions by remember { mutableStateOf(false) }
    var actionAfterDismiss by remember { mutableStateOf<(() -> Unit)?>(null) }
    val closeWithAction: (() -> Unit) -> Unit = { action ->
        if (showActions) {
            actionAfterDismiss = action
            showActions = false
        }
    }
    val subtitle = remember(remote.room, remote.brand, remote.name) {
        buildList {
            if (remote.room.isNotBlank()) add(remote.room)
            if (remote.brand.isNotBlank() && !remote.name.contains(remote.brand, ignoreCase = true)) add(remote.brand)
        }.joinToString("  ")
    }
    Card(
        onClick = onClick,
        shape = cardShape,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(containerColor = ExpressiveSurface.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp,
            focusedElevation = 0.dp, hoveredElevation = 0.dp, draggedElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (remote.isFavorite) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = getCategoryIcon(remote),
                        contentDescription = null,
                        tint = if (remote.isFavorite) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.large))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = remote.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    softWrap = true
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = true
                    )
                }
            }

            // Keep the title column stable when the favorite state changes.
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                if (remote.isFavorite) Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = AppStrings.get(R.string.screen_favorited, "已收藏"),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Box {
                ActionIconButton(AppStrings.get(R.string.screen_manage_name, "管理%1\$s", remote.name), onClick = { showActions = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = AppStrings.get(R.string.screen_manage_name, "管理%1\$s", remote.name))
                }
                ExpressiveDropdownMenuPopup(
                    expanded = LocalMotionActive.current && showActions,
                    onDismissRequest = { showActions = false }
                ) {
                    // DropdownMenuPopup keeps its content alive for the exit transition.
                    // Apply mutations only after it leaves composition: reordering a
                    // favorite must not move the anchor or change a fading menu's text.
                    DisposableEffect(Unit) {
                        onDispose {
                            val action = actionAfterDismiss
                            actionAfterDismiss = null
                            action?.invoke()
                        }
                    }
                    ExpressiveDropdownMenuGroup(index = 0, count = 2) {
                        ExpressiveMenuItem(
                            onClick = { closeWithAction(onToggleFavorite) },
                            text = { Text(if (remote.isFavorite) AppStrings.get(R.string.screen_unfavorite, "取消收藏") else AppStrings.get(R.string.screen_favorite, "收藏")) },
                            index = 0,
                            count = 3,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (remote.isFavorite) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                                    contentDescription = null
                                )
                            }
                        )
                        ExpressiveMenuItem(
                            onClick = { closeWithAction(onEdit) },
                            text = { Text(AppStrings.get(R.string.screen_edit_details, "编辑名称和房间")) },
                            index = 1,
                            count = 3,
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) }
                        )
                        ExpressiveMenuItem(
                            onClick = { closeWithAction(onExport) },
                            text = { Text(AppStrings.get(R.string.screen_export_remote, "导出此遥控器")) },
                            index = 2,
                            count = 3,
                            leadingIcon = { Icon(Icons.Rounded.Upload, contentDescription = null) }
                        )
                    }
                    ExpressiveDropdownMenuGroup(index = 1, count = 2) {
                        ExpressiveMenuItem(
                            onClick = { closeWithAction(onDelete) },
                            text = { Text(AppStrings.get(R.string.screen_trash_remote, "移入回收站")) },
                            index = 0,
                            count = 1,
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    isSearch: Boolean,
    onResetSearch: () -> Unit,
    onAddRemote: () -> Unit,
    hasSearchQuery: Boolean,
    actionsEnabled: Boolean,
    showAddAction: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.DevicesOther,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.large))
        Text(
            text = if (isSearch) AppStrings.get(if (hasSearchQuery) R.string.screen_search_empty else R.string.screen_filter_empty) else AppStrings.get(R.string.screen_no_saved_remotes, "暂无保存的遥控器"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Spacing.large))
        if (isSearch) {
            TextButton(onClick = onResetSearch, enabled = actionsEnabled, shapes = ButtonDefaults.shapes()) {
                Text(AppStrings.get(if (hasSearchQuery) R.string.screen_clear_search else R.string.screen_reset_filters))
            }
        } else if (showAddAction) {
            androidx.compose.material3.Button(onClick = onAddRemote, enabled = actionsEnabled, shapes = ButtonDefaults.shapes()) {
                Text(AppStrings.get(R.string.screen_add_remote, "添加遥控器"))
            }
        }
    }
}
