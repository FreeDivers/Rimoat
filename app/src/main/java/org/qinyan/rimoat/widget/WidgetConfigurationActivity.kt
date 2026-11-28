package org.qinyan.rimoat.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.qinyan.rimoat.IrRemoteApplication
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalView

class WidgetConfigurationActivity : AppCompatActivity() {
    private val refreshRateController by lazy { org.qinyan.rimoat.ui.DisplayRefreshRateController(this) }

    override fun onResume() {
        super.onResume()
        refreshRateController.start()
        (application as IrRemoteApplication).let { app ->
            app.applicationScope.launch { app.widgets.configurationChanged() }
        }
    }
    override fun onPause() { refreshRateController.stop(); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshRateController.update()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(Activity.RESULT_CANCELED, result)
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
        val kind = WidgetPickerPreviews.kind(info?.provider) ?: run { finish(); return }
        enableEdgeToEdge()
        val app = application as IrRemoteApplication
        setContent {
            val composeView = LocalView.current
            DisposableEffect(composeView) {
                refreshRateController.bind(composeView)
                onDispose { refreshRateController.bind(null) }
            }
            val dynamic by app.themePreferences.useDynamicColor.collectAsState()
            val colorMode by app.themePreferences.colorMode.collectAsState()
            val pureBlack by app.themePreferences.pureBlackBackground.collectAsState()
            IrRemoteTheme(colorMode = colorMode, dynamicColor = dynamic, pureBlackBackground = pureBlack) {
                WidgetConfigurationScreen(app, widgetId, kind, onCancel = ::finish,
                    onSaved = { setResult(Activity.RESULT_OK, result); finish() })
            }
        }
    }
}
