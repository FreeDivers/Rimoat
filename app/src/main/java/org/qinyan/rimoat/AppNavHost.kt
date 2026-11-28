package org.qinyan.rimoat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.qinyan.rimoat.ui.motion.MotionContent
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.ui.learning.CustomRemoteWizardScreen
import org.qinyan.rimoat.ui.learning.LeaveLearningDraftDialog
import org.qinyan.rimoat.ui.screens.AddRemoteScreen
import org.qinyan.rimoat.ui.screens.RemoteControlScreen
import org.qinyan.rimoat.ui.screens.SettingsScreen
import org.qinyan.rimoat.ui.screens.AboutScreen
import org.qinyan.rimoat.ui.screens.OpenSourceLicensesScreen
import org.qinyan.rimoat.ui.screens.RemoteListScreen
import org.qinyan.rimoat.ui.viewmodel.AddRemoteViewModel
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import kotlinx.coroutines.flow.first
import java.util.UUID

sealed interface AppScreen {
    object Rooms : AppScreen
    object Trash : AppScreen
    object Settings : AppScreen
    object About : AppScreen
    object OpenSourceLicenses : AppScreen
    data class LibraryLicense(val libraryId: String) : AppScreen
    object RemoteList : AppScreen
    data class RemoteControl(val remoteId: String) : AppScreen
    object AddRemote : AppScreen
    data class CustomRemoteWizard(
        val category: DeviceCategory = DeviceCategory.CUSTOM,
        val sessionId: String = UUID.randomUUID().toString()
    ) : AppScreen
}

private fun encodeScreen(screen: AppScreen): List<String> = when (screen) {
    AppScreen.Rooms -> listOf("rooms")
    AppScreen.Trash -> listOf("trash")
    AppScreen.Settings -> listOf("settings")
    AppScreen.About -> listOf("about")
    AppScreen.OpenSourceLicenses -> listOf("licenses")
    is AppScreen.LibraryLicense -> listOf("license", screen.libraryId)
    AppScreen.RemoteList -> listOf("list")
    AppScreen.AddRemote -> listOf("add")
    is AppScreen.RemoteControl -> listOf("control", screen.remoteId)
    is AppScreen.CustomRemoteWizard -> listOf("wizard", screen.category.name, screen.sessionId)
}

private fun decodeScreen(values: List<String>): AppScreen = when (values.firstOrNull()) {
    "control" -> AppScreen.RemoteControl(values[1])
    "rooms" -> AppScreen.Rooms
    "trash" -> AppScreen.Trash
    "settings" -> AppScreen.Settings
    "about" -> AppScreen.About
    "licenses" -> AppScreen.OpenSourceLicenses
    "license" -> AppScreen.LibraryLicense(values[1])
    "add" -> AppScreen.AddRemote
    "wizard" -> AppScreen.CustomRemoteWizard(DeviceCategory.valueOf(values[1]), values[2])
    else -> AppScreen.RemoteList
}

private val ScreenSaver = listSaver<AppScreen, String>(
    save = { encodeScreen(it) },
    restore = ::decodeScreen
)

private val NullableScreenSaver = listSaver<AppScreen?, String>(
    save = { screen -> screen?.let(::encodeScreen).orEmpty() },
    restore = { values -> if (values.isEmpty()) null else decodeScreen(values) }
)

@Composable
internal fun AppNavHost(
    app: IrRemoteApplication,
    pageViewModels: PageViewModelStores,
    widgetDestination: AppScreen?,
    onWidgetDestinationConsumed: () -> Unit,
    isChangingConfigurations: () -> Boolean
) {
    val repository = app.repository
    val transmitterManager = app.transmitterManager
    val themePreferences = app.themePreferences
    val factories = remember(app) { AppViewModelFactories(app) }
    var currentScreen by rememberSaveable(stateSaver = ScreenSaver) {
        mutableStateOf<AppScreen>(AppScreen.RemoteList)
    }
    var addFlowInitialized by rememberSaveable { mutableStateOf(false) }
    var pendingExternalDestination by rememberSaveable(stateSaver = NullableScreenSaver) {
        mutableStateOf<AppScreen?>(null)
    }

    // A recreated Activity only restores the current page, even if it rotated mid-transition.
    LaunchedEffect(Unit) { pageViewModels.releaseExcept(currentScreen) }

    fun navigate(destination: AppScreen, discardWizardDraft: Boolean = true) {
        if (destination == currentScreen) return
        val wizard = currentScreen as? AppScreen.CustomRemoteWizard
        if (wizard != null) {
            pageViewModels.findOwner(wizard)?.let { owner ->
                val vm = factories.wizardViewModel(wizard, owner)
                if (discardWizardDraft) vm.discardDraft() else vm.onScreenStopped()
            }
        }
        pendingExternalDestination = null
        currentScreen = destination
    }

    val listViewModel = viewModel<RemoteListViewModel>(
        factory = factories.remoteList()
    )

    val addViewModel: AddRemoteViewModel? = if (addFlowInitialized || currentScreen == AppScreen.AddRemote || currentScreen is AppScreen.CustomRemoteWizard) {
        viewModel<AddRemoteViewModel>(
            factory = factories.addRemote()
        )
    } else null

    fun applyExternalDestination(destination: AppScreen, discardWizardDraft: Boolean) {
        if (destination is AppScreen.RemoteControl && repository.remotes.value.none { it.id == destination.remoteId }) {
            navigate(AppScreen.RemoteList, discardWizardDraft = discardWizardDraft)
            listViewModel.reportTransferError(org.qinyan.rimoat.i18n.UiText.resource(R.string.shortcut_unavailable))
        } else {
            if (destination == AppScreen.AddRemote) {
                addViewModel?.reset()
                addFlowInitialized = true
            }
            navigate(destination, discardWizardDraft = discardWizardDraft)
        }
    }

    LaunchedEffect(widgetDestination) {
        val destination = widgetDestination ?: return@LaunchedEffect
        repository.awaitLoaded()
        val wizard = currentScreen as? AppScreen.CustomRemoteWizard
        if (wizard != null && destination != wizard) {
            val wizardVm = pageViewModels.findOwner(wizard)?.let { owner -> factories.wizardViewModel(wizard, owner) }
            if (wizardVm != null) {
                if (wizardVm.uiState.value.isRestoring || wizardVm.uiState.value.isSaving) {
                    wizardVm.uiState.first { !it.isRestoring && !it.isSaving }
                }
                if (currentScreen == wizard && destination != currentScreen && wizardVm.uiState.value.hasDraftProgress) {
                    wizardVm.onScreenStopped()
                    pendingExternalDestination = destination
                    onWidgetDestinationConsumed()
                    return@LaunchedEffect
                }
            }
            applyExternalDestination(destination, discardWizardDraft = wizardVm != null)
        } else {
            applyExternalDestination(destination, discardWizardDraft = true)
        }
        onWidgetDestinationConsumed()
    }
    val addUiState = addViewModel?.uiState?.collectAsStateWithLifecycle()?.value
    val transmitterType by transmitterManager.transmitterType.collectAsStateWithLifecycle()
    val isTransmitterReady by transmitterManager.isReady.collectAsStateWithLifecycle()
    val isTransmitterMock by transmitterManager.forceMockMode.collectAsStateWithLifecycle()
    val learnerType by app.learnerManager.learnerType.collectAsStateWithLifecycle()
    val isLearnerAvailable by app.learnerManager.isAvailable.collectAsStateWithLifecycle()
    val isLearnerMock by app.learnerManager.forceMockMode.collectAsStateWithLifecycle()

    val isTargetRotationScreen = when (currentScreen) {
        is AppScreen.RemoteControl -> true
        is AppScreen.CustomRemoteWizard -> true
        AppScreen.AddRemote -> addUiState?.step == org.qinyan.rimoat.ui.viewmodel.AddRemoteStep.WIZARD
        else -> false
    }
    val targetScreenKey = when (val s = currentScreen) {
        is AppScreen.RemoteControl -> "control:${s.remoteId}"
        is AppScreen.CustomRemoteWizard -> "wizard:${s.sessionId}"
        AppScreen.AddRemote -> if (addUiState?.step == org.qinyan.rimoat.ui.viewmodel.AddRemoteStep.WIZARD) {
            "add_matching:${addUiState.selectedBrand?.brandIdx ?: 0}"
        } else ""
        else -> ""
    }
    val isExternalConnected = org.qinyan.rimoat.data.isExternalIrActive(
        transmitterType = transmitterType,
        isTransmitterReady = isTransmitterReady,
        isTransmitterForceMock = isTransmitterMock,
        learnerType = learnerType,
        isLearnerAvailable = isLearnerAvailable,
        isLearnerForceMock = isLearnerMock
    )
    val wide = org.qinyan.rimoat.ui.components.rememberWideLayout()

    org.qinyan.rimoat.ui.components.ScreenRotationHost(
        themePreferences = themePreferences,
        isTargetRotationScreen = isTargetRotationScreen,
        targetScreenKey = targetScreenKey,
        isExternalIrConnected = isExternalConnected
    ) {
    MotionContent(
        targetState = currentScreen,
        contentKey = AppScreen::motionKey,
        isBack = { from, to -> to == from.backDestination(wide) },
        backTarget = currentScreen.backDestination(wide),
        onBack = {
            val from = currentScreen
            currentScreen.backDestination(wide)?.let { destination ->
                if (from == AppScreen.AddRemote && destination == AppScreen.RemoteList) {
                    addViewModel?.reset()
                }
                navigate(destination)
            }
        },
        retain = { it == AppScreen.RemoteList },
        discardSavedStateOnExit = { it is AppScreen.RemoteControl || it is AppScreen.CustomRemoteWizard }
    ) { screen ->
        when (screen) {
            is AppScreen.RemoteList -> {
                RemoteListScreen(
                    viewModel = listViewModel,
                    onSettingsClick = { navigate(AppScreen.Settings) },
                    onRemoteClick = { id -> navigate(AppScreen.RemoteControl(id)) },
                    onAddRemoteClick = {
                        addViewModel?.reset()
                        addFlowInitialized = true
                        navigate(AppScreen.AddRemote)
                    }
                )
            }
            AppScreen.Rooms -> org.qinyan.rimoat.ui.screens.RoomManagementScreen(listViewModel) { navigate(AppScreen.Settings) }
            AppScreen.Trash -> org.qinyan.rimoat.ui.screens.TrashScreen(listViewModel) { navigate(AppScreen.Settings) }
            AppScreen.Settings -> {
                SettingsScreen(listViewModel, onBack = { navigate(AppScreen.RemoteList) },
                    onRoomsClick = { navigate(AppScreen.Rooms) }, onTrashClick = { navigate(AppScreen.Trash) },
                    onAboutClick = { navigate(AppScreen.About) })
            }
            AppScreen.About -> AboutScreen(onBack = { navigate(AppScreen.Settings) },
                onLicensesClick = { navigate(AppScreen.OpenSourceLicenses) })
            AppScreen.OpenSourceLicenses, is AppScreen.LibraryLicense -> OpenSourceLicensesScreen(
                libraryId = (screen as? AppScreen.LibraryLicense)?.libraryId,
                onBack = { navigate(screen.backDestination(wide) ?: AppScreen.About) },
                onLibraryClick = { navigate(AppScreen.LibraryLicense(it)) })
            is AppScreen.AddRemote -> {
                val resolvedAddVm = addViewModel ?: viewModel<AddRemoteViewModel>(
                    factory = factories.addRemote()
                )
                AddRemoteScreen(
                    viewModel = resolvedAddVm,
                    onBack = {
                        resolvedAddVm.reset()
                        navigate(AppScreen.RemoteList)
                    },
                    onRemoteCreated = { profile ->
                        navigate(AppScreen.RemoteControl(profile.id))
                    },
                    onCustomWizardClick = {
                        navigate(AppScreen.CustomRemoteWizard())
                    }
                )
            }
            is AppScreen.CustomRemoteWizard -> {
                val owner = pageViewModelOwner(screen, pageViewModels, isChangingConfigurations)
                val wizardViewModel = remember(owner) { factories.wizardViewModel(screen, owner) }

                CustomRemoteWizardScreen(
                    viewModel = wizardViewModel,
                    onBack = { navigate(AppScreen.AddRemote) },
                    onRemoteCreated = { profile ->
                        if (currentScreen == screen) navigate(AppScreen.RemoteControl(profile.id))
                    }
                )
            }
            is AppScreen.RemoteControl -> {
                val controlViewModel = viewModel<RemoteControlViewModel>(
                    viewModelStoreOwner = pageViewModelOwner(screen, pageViewModels, isChangingConfigurations),
                    factory = factories.remoteControl(screen)
                )

                RemoteControlScreen(
                    viewModel = controlViewModel,
                    onBack = { navigate(AppScreen.RemoteList) }
                )
            }
        }
    }
    }
    val pendingTarget = pendingExternalDestination
    if (pendingTarget != null && currentScreen is AppScreen.CustomRemoteWizard) {
        LeaveLearningDraftDialog(
            onConfirmDiscard = {
                pendingExternalDestination = null
                applyExternalDestination(pendingTarget, discardWizardDraft = true)
            },
            onDismiss = {
                pendingExternalDestination = null
            }
        )
    }
}

@Composable
private fun pageViewModelOwner(
    screen: AppScreen,
    pageViewModels: PageViewModelStores,
    isChangingConfigurations: () -> Boolean
): ViewModelStoreOwner {
    val owner = remember(screen) { pageViewModels.ownerFor(screen) }
    DisposableEffect(screen) {
        onDispose {
            if (!isChangingConfigurations()) pageViewModels.release(screen)
        }
    }
    return owner
}
