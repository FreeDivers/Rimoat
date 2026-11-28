@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.material3.LinearWavyProgressIndicator
import org.qinyan.rimoat.i18n.ErrorAction
import org.qinyan.rimoat.i18n.asString
import org.qinyan.rimoat.ui.components.openStorageSettings
import org.qinyan.rimoat.ui.components.showAppError
import org.qinyan.rimoat.ui.screens.settings.BackupSavedDialog
import org.qinyan.rimoat.ui.theme.Spacing
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import org.qinyan.rimoat.ui.motion.ExpandSection
import org.qinyan.rimoat.ui.motion.FadeVisibility
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.qinyan.rimoat.ui.components.ExpressivePageTopBar
import org.qinyan.rimoat.ui.components.ExpressiveShapeTokens
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
import androidx.compose.foundation.layout.BoxWithConstraints
import org.qinyan.rimoat.ui.components.rememberWideLayout
import org.qinyan.rimoat.ui.components.TwoPaneLayout
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import org.qinyan.rimoat.ui.components.ActionIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.ui.components.HardwareStatusButton
import org.qinyan.rimoat.ui.components.TransmitterSelectionBottomSheet
import org.qinyan.rimoat.ui.components.clearSearchFocusOnTapOutside
import org.qinyan.rimoat.ui.components.rememberSearchFocusController
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
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
    val categories = remoteCategoryFilters
    val layoutDirection = LocalLayoutDirection.current
    val transmitterType by viewModel.transmitterType.collectAsStateWithLifecycle()
    val isHardwareReady by viewModel.isHardwareReady.collectAsStateWithLifecycle()
    val isForceMock by viewModel.isForceMock.collectAsStateWithLifecycle()
    var showTransmitterSheet by rememberSaveable { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val errorAppMessage by viewModel.errorAppMessage.collectAsStateWithLifecycle()
    val displayedError = errorMessage?.asString()
    val snackbar = remember { SnackbarHostState() }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportedUri by rememberSaveable { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri, exportId) { exportedUri = uri.toString() }
    }
    val activeError = errorAppMessage ?: errorMessage?.let { org.qinyan.rimoat.i18n.AppError.Resource(it) }
    LaunchedEffect(screenActive, activeError) {
        if (screenActive) activeError?.let { appError ->
            val result = snackbar.showAppError(context, appError)
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                when (appError.action) {
                    is ErrorAction.Retry -> if (!viewModel.retryLastExport()) {
                        exportLauncher.launch("Rimoat-remote.json")
                    }
                    is ErrorAction.OpenStorageSettings -> openStorageSettings(context)
                    else -> Unit
                }
            }
            viewModel.clearError()
        }
    }

    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val selectedRoom by viewModel.selectedRoom.collectAsStateWithLifecycle()
    val recentFirst by viewModel.recentFirst.collectAsStateWithLifecycle()
    val deletedIds by viewModel.deletedIds.collectAsStateWithLifecycle()
    val trashedMessage = if (deletedIds.isNotEmpty()) {
        pluralStringResource(R.plurals.screen_trashed_remotes, deletedIds.size, deletedIds.size)
    } else {
        null
    }
    val undoLabel = stringResource(R.string.screen_undo)
    val addRemoteLabel = stringResource(R.string.screen_add_remote)
    val transferBusy by viewModel.transferBusy.collectAsStateWithLifecycle()
    val transferMessage by viewModel.transferMessage.collectAsStateWithLifecycle()
    val displayedTransfer = transferMessage?.asString()
    LaunchedEffect(screenActive, deletedIds) {
        if (screenActive && deletedIds.isNotEmpty()) {
            val ids = deletedIds
            val message = trashedMessage ?: return@LaunchedEffect
            try {
                val result = snackbar.showSnackbar(
                    message, actionLabel = undoLabel, duration = SnackbarDuration.Long
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) viewModel.restoreDeleted(ids)
            } finally {
                // Do not replay a cancelled notice on return or consume a newer deletion.
                viewModel.consumeDeleted(ids)
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
                    ActionIconButton(stringResource(R.string.screen_settings), onClick = onSettingsClick) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.screen_settings))
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
                            contentDescription = addRemoteLabel
                            role = androidx.compose.ui.semantics.Role.Button
                            onClick { onAddRemoteClick(); true }
                        }
                    },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.screen_add_remote)) })
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
                    placeholder = stringResource(R.string.screen_search_remote),
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
                    onFavorite = { viewModel.toggleFavorite(remote.id) }, onRemove = { viewModel.deleteRemote(remote.id).await() }, modifier = itemModifier) {
                    RemoteCard(
                        remote = remote,
                        onClick = { onRemoteClick(remote.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(remote.id) },
                        onDelete = { viewModel.deleteRemote(remote.id) },
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
        remotes.firstOrNull { it.id == editingId }?.let { profile ->
            RemoteEditDialog(
                profile = profile,
                displayedError = displayedError,
                onDismiss = { editingId = null },
                onSave = { name, room -> viewModel.updateDetails(profile.id, name, room) { editingId = null } }
            )
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
