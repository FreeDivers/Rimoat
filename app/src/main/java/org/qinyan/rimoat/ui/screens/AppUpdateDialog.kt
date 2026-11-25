@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.text.NumberFormat
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.update.AppRelease
import org.qinyan.rimoat.data.update.AppUpdateRepository
import org.qinyan.rimoat.data.update.ReleaseAsset
import org.qinyan.rimoat.data.update.UpdateFailure
import org.qinyan.rimoat.data.update.UpdateInstaller
import org.qinyan.rimoat.ui.motion.AppMotion
import org.qinyan.rimoat.ui.motion.MotionAlertDialog
import org.qinyan.rimoat.ui.motion.StateFade
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.theme.Spacing
import org.qinyan.rimoat.ui.viewmodel.AppUpdateState
import org.qinyan.rimoat.ui.viewmodel.AppUpdateViewModel
import org.qinyan.rimoat.ui.viewmodel.UpdatePhase

@Composable
internal fun appUpdateViewModel(): AppUpdateViewModel {
    val app = LocalContext.current.applicationContext
    return viewModel(factory = remember(app) {
        viewModelFactory { initializer { AppUpdateViewModel(AppUpdateRepository(app), createSavedStateHandle()) } }
    })
}

@Composable
internal fun AppUpdateHost(viewModel: AppUpdateViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.permissionResult(UpdateInstaller.allowed(context))
    }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.installerReturned()
    }
    LifecycleResumeEffect(Unit) {
        viewModel.permissionResult(UpdateInstaller.allowed(context))
        onPauseOrDispose { }
    }
    LaunchedEffect(state.phase) {
        if (state.phase == UpdatePhase.READY) {
            if (!UpdateInstaller.allowed(context)) viewModel.requirePermission()
            else try {
                val intent = UpdateInstaller.installIntent(context, checkNotNull(state.apk))
                viewModel.installing()
                installer.launch(intent)
            } catch (_: Exception) { viewModel.installationFailed() }
        }
    }
    AppUpdateDialog(state, viewModel.installedVersion,
        onCancel = viewModel::cancel,
        onCheck = viewModel::check,
        onDownload = viewModel::download,
        onInstall = viewModel::installAgain,
        onPermission = {
            try {
                viewModel.awaitingPermission()
                permission.launch(UpdateInstaller.permissionIntent(context))
            } catch (_: Exception) { viewModel.permissionUnavailable() }
        }, modifier = modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppUpdateDialog(
    state: AppUpdateState,
    installedVersion: String,
    onCancel: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.phase == UpdatePhase.CLOSED) return
    val title = when (state.phase) {
        UpdatePhase.AVAILABLE -> R.string.update_title_available
        UpdatePhase.DOWNLOADING -> R.string.update_title_downloading
        UpdatePhase.PERMISSION, UpdatePhase.WAITING_PERMISSION -> R.string.update_title_permission
        UpdatePhase.READY, UpdatePhase.DOWNLOADED, UpdatePhase.INSTALLING -> R.string.update_title_ready
        else -> R.string.update_check
    }
    val action: Pair<Int, () -> Unit>? = when (state.phase) {
        UpdatePhase.AVAILABLE -> if (state.release?.asset != null) R.string.update_now to onDownload
            else R.string.update_retry to onCheck
        UpdatePhase.ERROR -> R.string.update_retry to if (state.release?.asset != null) onDownload else onCheck
        UpdatePhase.PERMISSION -> R.string.update_open_settings to onPermission
        UpdatePhase.DOWNLOADED -> R.string.update_install to onInstall
        else -> null
    }
    MotionAlertDialog(
        onDismissRequest = onCancel,
        modifier = modifier.testTag("app_update_dialog"),
        title = { Text(stringResource(title)) },
        text = {
            StateFade(state, contentKey = { it.phase }) { displayed ->
                UpdateDialogBody(displayed, installedVersion)
            }
        },
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                action?.let { (label, callback) ->
                    TextButton(onClick = callback, modifier = Modifier.padding(end = Spacing.small), shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(label))
                    }
                }
                TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateDialogBody(state: AppUpdateState, installedVersion: String, modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val notes = state.release?.notes(locale.toLanguageTag()).orEmpty()
    Column(
        modifier.fillMaxWidth().heightIn(max = (configuration.screenHeightDp * 0.55f).dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        when (state.phase) {
            UpdatePhase.CHECKING -> {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
                    LoadingIndicator(Modifier.size(48.dp).testTag("update_loading"))
                    StatusText(stringResource(R.string.update_checking))
                }
            }
            UpdatePhase.CURRENT -> StatusText(stringResource(R.string.update_current, installedVersion))
            UpdatePhase.AVAILABLE -> {
                Text(state.release?.tag.orEmpty(), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.update_installed, installedVersion), style = MaterialTheme.typography.bodySmall)
                SelectionContainer { Text(notes.ifBlank { stringResource(R.string.update_no_notes) }) }
                if (state.release?.asset == null) StatusText(stringResource(R.string.update_error_no_apk), error = true)
            }
            UpdatePhase.DOWNLOADING -> {
                val fraction = if (state.totalBytes > 0) (state.downloadedBytes.toDouble() / state.totalBytes).coerceIn(0.0, 1.0).toFloat() else 0f
                val animated by animateFloatAsState(fraction, AppMotion.current.defaultEffectsSpec(), label = "DownloadProgress")
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
                    LinearWavyProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth().testTag("update_progress"))
                    Text(NumberFormat.getPercentInstance(locale).format(fraction.toDouble()),
                        style = MaterialTheme.typography.titleMedium)
                    val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
                    Text(stringResource(R.string.update_download_size,
                        format.format(state.downloadedBytes / 1048576.0), format.format(state.totalBytes / 1048576.0)),
                        textAlign = TextAlign.Center)
                    if (fraction >= 1f) StatusText(stringResource(R.string.update_verifying))
                }
            }
            UpdatePhase.PERMISSION, UpdatePhase.WAITING_PERMISSION -> StatusText(stringResource(R.string.update_permission_message))
            UpdatePhase.READY, UpdatePhase.INSTALLING -> StatusText(stringResource(R.string.update_opening_installer))
            UpdatePhase.DOWNLOADED -> StatusText(stringResource(R.string.update_downloaded))
            else -> Unit
        }
        state.failure?.let { StatusText(stringResource(it.messageResource()), error = true) }
    }
}

@Composable
private fun StatusText(text: String, error: Boolean = false, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun UpdateFailure.messageResource(): Int = when (this) {
    UpdateFailure.CHECK_TIMEOUT -> R.string.update_error_timeout
    UpdateFailure.NETWORK -> R.string.update_error_network
    UpdateFailure.RATE_LIMIT -> R.string.update_error_rate_limit
    UpdateFailure.NO_RELEASE -> R.string.update_error_no_release
    UpdateFailure.INVALID_RELEASE -> R.string.update_error_release
    UpdateFailure.NO_APK -> R.string.update_error_no_apk
    UpdateFailure.DOWNLOAD -> R.string.update_error_download
    UpdateFailure.STORAGE -> R.string.update_error_storage
    UpdateFailure.INVALID_APK -> R.string.update_error_apk
    UpdateFailure.SIGNATURE -> R.string.update_error_signature
    UpdateFailure.PERMISSION -> R.string.update_error_permission
    UpdateFailure.INSTALLER -> R.string.update_error_installer
}

@Preview(locale = "zh", showBackground = true)
@Preview(locale = "en", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppUpdatePreview() {
    IrRemoteTheme(dynamicColor = false) {
        AppUpdateDialog(AppUpdateState(UpdatePhase.AVAILABLE,
            AppRelease("v1.2.0", stringResource(R.string.update_check),
                ReleaseAsset("Rimoat-v1.2.0.apk", "", 32_000_000))), "1.1.0", {}, {}, {}, {}, {})
    }
}
