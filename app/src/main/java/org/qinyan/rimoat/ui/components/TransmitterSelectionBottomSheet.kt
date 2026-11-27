package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.SoundEffectOnInteraction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.ui.motion.ExpandSection
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
import org.qinyan.rimoat.ui.theme.SilenceWindowClickSounds
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransmitterSelectionBottomSheet(
    transmitterManager: IrTransmitterManager,
    sheetState: SheetState,
    onDismissRequest: () -> Unit
) {
    var showSerialSettings by rememberSaveable { mutableStateOf(false) }
    var showUnavailable by rememberSaveable { mutableStateOf(false) }
    val currentActive by transmitterManager.transmitterType.collectAsState()
    val ready by transmitterManager.isReady.collectAsState()
    val manualSelection by transmitterManager.selectedTransmitterType.collectAsState()
    val forceMock by transmitterManager.forceMockMode.collectAsState()
    val available by transmitterManager.availableTransmitters.collectAsState()
    val usbDevices by transmitterManager.usbDevices.collectAsState()
    val serialSettings by transmitterManager.serialSettings.collectAsState()
    val serialSettingsLoaded by transmitterManager.serialSettingsLoaded.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val demoSelected = forceMock || manualSelection == TransmitterType.MOCK
    val automatic = manualSelection == null && !forceMock
    val channels = transmitterChannels(
        available.map { it.type }.toSet(), manualSelection.takeUnless { demoSelected },
        usbDevices, serialSettings, serialSettingsLoaded
    )
    val usable = channels.filter { it.available }
    val attention = channels.filter { it.needsAttention }
    val unavailable = channels.filter { !it.available && !it.needsAttention }
    val inlineSerialSettings = attention.any { it.type == TransmitterType.USB_SERIAL }
    val select: (TransmitterType?) -> Unit = { type ->
        org.qinyan.rimoat.ui.HapticHelper.performSelection(haptic, context)
        transmitterManager.selectTransmitterType(if (type == TransmitterType.MOCK) null else type)
        transmitterManager.setForceMockMode(type == TransmitterType.MOCK)
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { transmitterManager.refreshHardware() }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = ExpressiveSurface.canvas
    ) {
        ProvideExpressiveCanvas {
        SilenceWindowClickSounds()
        SoundEffectOnInteraction(enabled = false) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(AppStrings.get(R.string.control_ir_channel), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { scope.launch { sheetState.hide(); onDismissRequest() } }, shapes = ButtonDefaults.shapes()) { Text(AppStrings.get(R.string.control_done)) }
            }
            val choices: @Composable () -> Unit = {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChannelChoice(AppStrings.get(R.string.control_auto_select), Icons.Rounded.AutoMode, automatic, onClick = { select(null) })
                    if (usable.isNotEmpty()) {
                        ChannelSectionTitle(AppStrings.get(R.string.control_available_devices))
                        Column(verticalArrangement = Arrangement.spacedBy(ExpressiveShapeTokens.SegmentedGap)) {
                            usable.forEachIndexed { index, channel ->
                                HardwareChannelItem(channel, usbDevices.any { it.pending },
                                    index = index, count = usable.size,
                                    onSelect = { select(channel.type) },
                                    onPermission = transmitterManager::requestUsbPermission)
                            }
                        }
                    }
                    if (attention.isNotEmpty()) {
                        ChannelSectionTitle(AppStrings.get(R.string.control_pending_devices))
                        Column(verticalArrangement = Arrangement.spacedBy(ExpressiveShapeTokens.SegmentedGap)) {
                            attention.forEachIndexed { index, channel ->
                                HardwareChannelItem(channel, usbDevices.any { it.pending },
                                    index = index, count = attention.size,
                                    onSelect = { select(channel.type) },
                                    onPermission = transmitterManager::requestUsbPermission,
                                    onConfigure = if (channel.type == TransmitterType.USB_SERIAL) {
                                        { showSerialSettings = true }
                                    } else null)
                            }
                        }
                    }
                    if (unavailable.isNotEmpty()) {
                        // This row expands information; it never looks like a channel choice.
                        TextButton(
                            onClick = { showUnavailable = !showUnavailable },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).semantics {
                                stateDescription = if (showUnavailable) AppStrings.get(R.string.control_expanded) else AppStrings.get(R.string.control_collapsed)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(AppStrings.get(R.string.control_unavailable_count, unavailable.size), Modifier.weight(1f))
                            Icon(if (showUnavailable) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                        }
                        ExpandSection(visible = showUnavailable) {
                            Column(verticalArrangement = Arrangement.spacedBy(ExpressiveShapeTokens.SegmentedGap)) {
                                unavailable.forEachIndexed { index, it -> UnavailableChannel(it, index, unavailable.size) }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    ChannelChoice(AppStrings.get(R.string.control_demo_mode), Icons.Rounded.SensorsOff, demoSelected,
                        onClick = { select(TransmitterType.MOCK) })
                }
                if (!inlineSerialSettings) {
                    OutlinedButton(onClick = { showSerialSettings = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Settings, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(AppStrings.get(R.string.control_serial_config))
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                if (maxWidth >= 480.dp) {
                    TwoPaneLayout(primaryFraction = .4f, primaryMaxWidth = 320.dp,
                        primary = {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                ActiveChannelSummary(currentActive, ready)
                            }
                        }, secondary = {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)) { choices() }
                        })
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp).padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ActiveChannelSummary(currentActive, ready)
                        choices()
                    }
                }
            }
        }
        }
    }
    // Recreated windows follow composition order: keep the child dialog above its sheet.
    if (showSerialSettings) {
        SerialIrSettingsDialog(transmitterManager) { showSerialSettings = false }
    }
}

@Composable
private fun ActiveChannelSummary(active: TransmitterType, ready: Boolean) {
    val simulated = !active.isPhysical
    val unavailable = !simulated && !ready
    val colors = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unavailable) colors.errorContainer else ExpressiveSurface.card,
            contentColor = if (unavailable) colors.onErrorContainer else colors.onSurface
        ),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (unavailable) colors.error else if (simulated) colors.surfaceContainerHigh else colors.primaryContainer,
                contentColor = if (unavailable) colors.onError else if (simulated) colors.onSurfaceVariant else colors.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(when {
                        unavailable -> Icons.Rounded.Info
                        simulated -> Icons.Rounded.SensorsOff
                        else -> Icons.Rounded.CheckCircle
                    }, null, Modifier.size(22.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(if (unavailable) R.string.control_current_not_ready else R.string.control_current_output),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unavailable) colors.onErrorContainer else colors.onSurfaceVariant)
                Text(when {
                    unavailable -> stringResource(channelTitleRes(active))
                    simulated -> stringResource(R.string.control_demo_mode)
                    else -> stringResource(channelTitleRes(active))
                }, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ChannelSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun HardwareChannelItem(
    channel: TransmitterChannelState,
    permissionPending: Boolean,
    index: Int = 0,
    count: Int = 1,
    onSelect: () -> Unit,
    onPermission: (String) -> Unit,
    onConfigure: (() -> Unit)? = null
) {
    Column {
        if (channel.available) {
            ChannelChoice(stringResource(channelTitleRes(channel.type)), channelIcon(channel.type), channel.selected,
                description = stringResource(channel.status.labelRes), index = index, count = count, onClick = onSelect)
        } else {
            UnavailableChannel(channel, index, count)
        }
        val needsPermission = channel.devices.filter { !it.granted }
        if (needsPermission.isNotEmpty() || onConfigure != null) {
            Column(Modifier.padding(start = 56.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                needsPermission.forEach { device ->
                    // Distinguish devices when more than one accessory needs authorization.
                    if (channel.devices.size > 1) Text(device.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { onPermission(device.deviceName) },
                        enabled = !permissionPending, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                        Text(when {
                            device.pending -> stringResource(R.string.control_waiting_permission)
                            device.denied -> stringResource(R.string.control_reauthorize)
                            else -> stringResource(R.string.control_authorize_device)
                        })
                    }
                }
                if (onConfigure != null) OutlinedButton(onClick = onConfigure, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.control_configure_module))
                }
            }
        }
    }
}

@Composable
private fun UnavailableChannel(channel: TransmitterChannelState, index: Int = 0, count: Int = 1) {
    val colors = MaterialTheme.colorScheme
    val statusText = stringResource(channel.status.labelRes)
    val unavailableStateText = stringResource(if (channel.selected) R.string.control_selected_unavailable else R.string.control_unavailable)
    ExpressiveSegmentedSurface(
        index = index,
        count = count,
        containerColor = ExpressiveSurface.card.copy(alpha = 0.65f)
    ) {
        ListItem(
            modifier = Modifier.semantics(mergeDescendants = true) {
                disabled()
                stateDescription = unavailableStateText
            },
            headlineContent = { Text(stringResource(channelTitleRes(channel.type))) },
            supportingContent = { Text(if (channel.selected) stringResource(R.string.control_selected_status, statusText) else statusText) },
            leadingContent = { Icon(channelIcon(channel.type), null, Modifier.size(24.dp)) },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = colors.onSurfaceVariant,
                supportingColor = colors.onSurfaceVariant,
                leadingIconColor = colors.onSurface.copy(alpha = 0.38f)
            )
        )
    }
}

@Composable
private fun ChannelChoice(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    description: String? = null,
    index: Int = 0,
    count: Int = 1,
    onClick: () -> Unit
) {
    SingleChoiceListItem(
        title = title,
        selected = selected,
        onClick = onClick,
        description = description,
        icon = icon,
        index = index,
        count = count
    )
}

private fun channelIcon(type: TransmitterType): ImageVector = when (type) {
    TransmitterType.AUDIO -> Icons.Rounded.Headphones
    TransmitterType.BUILT_IN -> Icons.Rounded.Sensors
    else -> Icons.Rounded.Usb
}
