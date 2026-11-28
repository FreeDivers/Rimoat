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
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val pageViewModels by viewModels<PageViewModelStores>()
    private val refreshRateController by lazy { org.qinyan.rimoat.ui.DisplayRefreshRateController(this) }
    private var widgetDestination by mutableStateOf<AppScreen?>(null)
    private var initialColdResumeConsumed = false

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
                    AppNavHost(
                        app = app,
                        pageViewModels = pageViewModels,
                        widgetDestination = widgetDestination,
                        onWidgetDestinationConsumed = { widgetDestination = null },
                        isChangingConfigurations = { isChangingConfigurations }
                    )
                }
            }
        }
    }
}
