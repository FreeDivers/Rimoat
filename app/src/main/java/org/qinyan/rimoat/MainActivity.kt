package org.qinyan.rimoat

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import org.qinyan.rimoat.ui.motion.MotionContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.ui.learning.CustomRemoteWizardScreen
import org.qinyan.rimoat.ui.learning.CustomRemoteWizardViewModel
import org.qinyan.rimoat.ui.learning.WizardDrafts
import org.qinyan.rimoat.ui.screens.AddRemoteScreen
import org.qinyan.rimoat.ui.screens.RemoteControlScreen
import org.qinyan.rimoat.ui.screens.SettingsScreen
import org.qinyan.rimoat.ui.screens.AboutScreen
import org.qinyan.rimoat.ui.screens.OpenSourceLicensesScreen
import org.qinyan.rimoat.ui.screens.RemoteListScreen
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.AddRemoteViewModel
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import kotlinx.coroutines.launch
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

private val ScreenSaver = listSaver<AppScreen, String>(
    save = { screen -> when (screen) {
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
    } },
    restore = { values -> when (values.firstOrNull()) {
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
    } }
)

class MainActivity : AppCompatActivity() {
    private val pageViewModels by viewModels<PageViewModelStores>()
    private val refreshRateController by lazy { org.qinyan.rimoat.ui.DisplayRefreshRateController(this) }
    private var widgetDestination by mutableStateOf<AppScreen?>(null)
    private var initialColdResumeConsumed = false

    @Composable
    private fun pageViewModelOwner(screen: AppScreen): ViewModelStoreOwner {
        val owner = remember(screen) { pageViewModels.ownerFor(screen) }
        DisposableEffect(screen) {
            onDispose {
                if (!isChangingConfigurations) pageViewModels.release(screen)
            }
        }
        return owner
    }

    private fun wizardViewModel(
        screen: AppScreen.CustomRemoteWizard,
        owner: ViewModelStoreOwner
    ): CustomRemoteWizardViewModel {
        val app = application as IrRemoteApplication
        return ViewModelProvider(owner, viewModelFactory {
            initializer {
                CustomRemoteWizardViewModel(
                    repository = app.repository,
                    transmitterManager = app.transmitterManager,
                    learnerManager = app.learnerManager,
                    initialCategory = screen.category,
                    draftFile = WizardDrafts.file(app.filesDir, screen.sessionId),
                    brandLibrary = app.irDatabaseRepository
                )
            }
        })[CustomRemoteWizardViewModel::class.java]
    }

    private fun readWidgetDestination(intent: android.content.Intent?) {
        org.qinyan.rimoat.data.RemoteShortcutManager.remoteIdFromIntent(intent)?.let {
            widgetDestination = AppScreen.RemoteControl(it)
            return
        }
        if (intent?.action != "org.qinyan.rimoat.OPEN_WIDGET") return
        widgetDestination = when (intent.getStringExtra("widget_destination")) {
            "add" -> AppScreen.AddRemote
            "list" -> AppScreen.RemoteList
            else -> intent.getStringExtra("widget_remote_id")?.takeIf { it.isNotBlank() }
                ?.let { AppScreen.RemoteControl(it) } ?: AppScreen.RemoteList
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readWidgetDestination(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshRateController.start()
        (application as IrRemoteApplication).let { app ->
            val justCreatedProcess = !initialColdResumeConsumed &&
                (android.os.SystemClock.uptimeMillis() - app.coldLaunchTimestampUptimeMs) in 0L..1500L
            initialColdResumeConsumed = true
            app.learnerManager.noHardwareLearner.refreshLanguage()
            app.applicationScope.launch { app.widgets.configurationChanged() }
            if (!justCreatedProcess) {
                app.applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    app.transmitterManager.refreshHardware()
                }
                app.refreshShortcuts()
            }
        }
    }

    override fun onPause() {
        refreshRateController.stop()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshRateController.update()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readWidgetDestination(intent)

        val app = application as IrRemoteApplication
        val repository = app.repository
        val transmitterManager = app.transmitterManager
        val themePreferences = app.themePreferences

        setContent {
            val composeView = androidx.compose.ui.platform.LocalView.current
            DisposableEffect(composeView) {
                refreshRateController.bind(composeView)
                onDispose { refreshRateController.bind(null) }
            }
            val useDynamicColor by themePreferences.useDynamicColor.collectAsStateWithLifecycle()
            val colorMode by themePreferences.colorMode.collectAsStateWithLifecycle()
            val pureBlackBackground by themePreferences.pureBlackBackground.collectAsStateWithLifecycle()

            IrRemoteTheme(colorMode = colorMode, dynamicColor = useDynamicColor, pureBlackBackground = pureBlackBackground) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by rememberSaveable(stateSaver = ScreenSaver) {
                        mutableStateOf<AppScreen>(AppScreen.RemoteList)
                    }
                    var addFlowInitialized by rememberSaveable { mutableStateOf(false) }

                    // A recreated Activity only restores the current page, even if it rotated mid-transition.
                    LaunchedEffect(Unit) { pageViewModels.releaseExcept(currentScreen) }

                    fun navigate(destination: AppScreen) {
                        if (destination == currentScreen) return
                        val wizard = currentScreen as? AppScreen.CustomRemoteWizard
                        if (wizard != null) {
                            pageViewModels.findOwner(wizard)?.let { owner ->
                                wizardViewModel(wizard, owner).discardDraft()
                            }
                        }
                        currentScreen = destination
                    }

                    val listViewModel = viewModel<RemoteListViewModel>(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return RemoteListViewModel(repository, transmitterManager, themePreferences, org.qinyan.rimoat.data.BackupFileStore(app)) as T
                            }
                        }
                    )

                    val addViewModel: AddRemoteViewModel? = if (addFlowInitialized || currentScreen == AppScreen.AddRemote || currentScreen is AppScreen.CustomRemoteWizard) {
                        viewModel<AddRemoteViewModel>(
                            factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                                    return AddRemoteViewModel(
                                        irDbRepository = app.irDatabaseRepository,
                                        remoteRepository = repository,
                                        transmitterManager = transmitterManager,
                                        savedStateHandle = extras.createSavedStateHandle()
                                    ) as T
                                }
                            }
                        )
                    } else null

                    LaunchedEffect(widgetDestination) {
                        val destination = widgetDestination ?: return@LaunchedEffect
                        repository.awaitLoaded()
                        if (destination is AppScreen.RemoteControl && repository.getRemote(destination.remoteId) == null) {
                            navigate(AppScreen.RemoteList)
                            listViewModel.reportTransferError(org.qinyan.rimoat.i18n.UiText.resource(R.string.shortcut_unavailable))
                        } else {
                            if (destination == AppScreen.AddRemote) {
                                addViewModel?.reset()
                                addFlowInitialized = true
                            }
                            navigate(destination)
                        }
                        widgetDestination = null
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
                                    factory = object : ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                                            return AddRemoteViewModel(
                                                irDbRepository = app.irDatabaseRepository,
                                                remoteRepository = repository,
                                                transmitterManager = transmitterManager,
                                                savedStateHandle = extras.createSavedStateHandle()
                                            ) as T
                                        }
                                    }
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
                                val owner = pageViewModelOwner(screen)
                                val wizardViewModel = remember(owner) { wizardViewModel(screen, owner) }

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
                                    viewModelStoreOwner = pageViewModelOwner(screen),
                                    factory = object : ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                            return RemoteControlViewModel(
                                                remoteId = screen.remoteId,
                                                initialProfile = repository.remotes.value.firstOrNull { it.id == screen.remoteId },
                                                repository = repository,
                                                transmitterManager = transmitterManager,
                                                learnerManager = app.learnerManager,
                                                acSignalEncoder = app.acSignals,
                                                commandExecutor = app.commands
                                            ) as T
                                        }
                                    }
                                )

                                RemoteControlScreen(
                                    viewModel = controlViewModel,
                                    onBack = { navigate(AppScreen.RemoteList) }
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}
