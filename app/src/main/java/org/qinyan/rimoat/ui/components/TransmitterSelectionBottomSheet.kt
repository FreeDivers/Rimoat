package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedLabel
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.ui.motion.ExpandSection
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
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(AppStrings.get(R.string.control_ir_channel, "红外发射通道"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { scope.launch { sheetState.hide(); onDismissRequest() } }) { Text(AppStrings.get(R.string.control_done, "完成")) }
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ActiveChannelSummary(currentActive, ready)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChannelChoice(AppStrings.get(R.string.control_auto_select, "自动选择"), Icons.Rounded.AutoMode, automatic, onClick = { select(null) })
                if (usable.isNotEmpty()) {
                    ChannelSectionTitle(AppStrings.get(R.string.control_available_devices, "可用设备"))
                    usable.forEach { channel ->
                        HardwareChannelItem(channel, usbDevices.any { it.pending },
                            onSelect = { select(channel.type) },
                            onPermission = transmitterManager::requestUsbPermission)
                    }
                }
                if (attention.isNotEmpty()) {
                    ChannelSectionTitle(AppStrings.get(R.string.control_pending_devices, "待处理设备"))
                    attention.forEach { channel ->
                        HardwareChannelItem(channel, usbDevices.any { it.pending },
                            onSelect = { select(channel.type) },
                            onPermission = transmitterManager::requestUsbPermission,
                            onConfigure = if (channel.type == TransmitterType.USB_SERIAL) {
                                { showSerialSettings = true }
                            } else null)
                    }
                }
                if (unavailable.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    // This row expands information; it never looks like a channel choice.
                    TextButton(
                        onClick = { showUnavailable = !showUnavailable },
                        modifier = Modifier.fillMaxWidth().semantics {
                            stateDescription = if (showUnavailable) AppStrings.get(R.string.control_expanded, "已展开") else AppStrings.get(R.string.control_collapsed, "已收起")
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(AppStrings.get(R.string.control_unavailable_count, "不可用通道（%1\$d）", unavailable.size), Modifier.weight(1f))
                        Icon(if (showUnavailable) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    ExpandSection(visible = showUnavailable) {
                        Column { unavailable.forEach { UnavailableChannel(it) } }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ChannelChoice(AppStrings.get(R.string.control_demo_mode, "演示模式"), Icons.Rounded.SensorsOff, demoSelected,
                    onClick = { select(TransmitterType.MOCK) })
            }
            if (!inlineSerialSettings) {
                OutlinedButton(onClick = { showSerialSettings = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Settings, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(AppStrings.get(R.string.control_serial_config, "串口模块配置"))
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
        colors = CardDefaults.cardColors(
            containerColor = if (unavailable) colors.errorContainer else colors.surfaceContainerHigh,
            contentColor = if (unavailable) colors.onErrorContainer else colors.onSurface
        ),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(when {
                unavailable -> Icons.Rounded.Info
                simulated -> Icons.Rounded.SensorsOff
                else -> Icons.Rounded.CheckCircle
            }, null, Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (unavailable) AppStrings.get(R.string.control_current_not_ready, "当前通道未就绪") else AppStrings.get(R.string.control_current_output, "当前发射"),
                    style = MaterialTheme.typography.labelMedium)
                Text(when {
                    unavailable -> channelTitle(active)
                    simulated -> AppStrings.get(R.string.control_demo_mode, "演示模式")
                    else -> channelTitle(active)
                }, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ChannelSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp))
}

@Composable
private fun HardwareChannelItem(
    channel: TransmitterChannelState,
    permissionPending: Boolean,
    onSelect: () -> Unit,
    onPermission: (String) -> Unit,
    onConfigure: (() -> Unit)? = null
) {
    Column {
        if (channel.available) {
            ChannelChoice(channelTitle(channel.type), channelIcon(channel.type), channel.selected,
                description = channel.status, onClick = onSelect)
        } else {
            UnavailableChannel(channel)
        }
        val needsPermission = channel.devices.filter { !it.granted }
        if (needsPermission.isNotEmpty() || onConfigure != null) {
            Column(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                needsPermission.forEach { device ->
                    // Distinguish devices when more than one accessory needs authorization.
                    if (channel.devices.size > 1) Text(device.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { onPermission(device.deviceName) },
                        enabled = !permissionPending, modifier = Modifier.fillMaxWidth()) {
                        Text(when {
                            device.pending -> AppStrings.get(R.string.control_waiting_permission, "等待授权")
                            device.denied -> AppStrings.get(R.string.control_reauthorize, "重新授权")
                            else -> AppStrings.get(R.string.control_authorize_device, "授权设备")
                        })
                    }
                }
                if (onConfigure != null) OutlinedButton(onClick = onConfigure, modifier = Modifier.fillMaxWidth()) {
                    Text(AppStrings.get(R.string.control_configure_module, "配置模块"))
                }
            }
        }
    }
}

@Composable
private fun UnavailableChannel(channel: TransmitterChannelState) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        modifier = Modifier.semantics(mergeDescendants = true) {
            disabled()
            stateDescription = if (channel.selected) AppStrings.get(R.string.control_selected_unavailable, "已选通道，不可用") else AppStrings.get(R.string.control_unavailable, "不可用")
        },
        headlineContent = { Text(channelTitle(channel.type)) },
        supportingContent = { Text(if (channel.selected) AppStrings.get(R.string.control_selected_status, "已选 · %1\$s", channel.status) else channel.status) },
        leadingContent = { Icon(channelIcon(channel.type), null, Modifier.size(24.dp)) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = colors.onSurfaceVariant,
            supportingColor = colors.onSurfaceVariant,
            leadingIconColor = colors.onSurface.copy(alpha = 0.38f)
        )
    )
}

@Composable
private fun ChannelChoice(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    description: String? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        modifier = Modifier.clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = { Icon(icon, null, Modifier.size(24.dp)) },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) colors.secondaryContainer else Color.Transparent,
            headlineColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
            supportingColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            leadingIconColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
        )
    )
}

private fun channelIcon(type: TransmitterType): ImageVector = when (type) {
    TransmitterType.AUDIO -> Icons.Rounded.Headphones
    TransmitterType.BUILT_IN -> Icons.Rounded.Sensors
    else -> Icons.Rounded.Usb
}
