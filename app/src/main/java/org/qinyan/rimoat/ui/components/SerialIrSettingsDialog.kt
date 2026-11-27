package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.UiTextStateSaver
import org.qinyan.rimoat.i18n.asString
import org.qinyan.rimoat.i18n.toAppError
import androidx.annotation.StringRes
import org.qinyan.rimoat.ui.motion.AppMotion
import org.qinyan.rimoat.ui.motion.ExpandSection
import org.qinyan.rimoat.ui.motion.StateFade
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.serial.SerialIrModule
import org.qinyan.rimoat.hardware.serial.SerialIrSettings
import org.qinyan.rimoat.hardware.serial.SerialDeviceOption
import kotlinx.coroutines.launch
import org.qinyan.rimoat.ui.motion.MotionAlertDialog

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SerialIrSettingsDialog(manager: IrTransmitterManager, onDismiss: () -> Unit) {
    val saved by manager.serialSettings.collectAsStateWithLifecycle()
    val devices by manager.serialDevices.collectAsStateWithLifecycle()
    val loaded by manager.serialSettingsLoaded.collectAsStateWithLifecycle()
    // Initialize the editable draft only after the persisted settings are available.
    if (!loaded) {
        MotionAlertDialog(
            onDismissRequest = onDismiss,
            containerColor = ExpressiveSurface.canvas,
            title = { Text(AppStrings.get(R.string.control_serial_config)) },
            text = { LinearWavyProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(AppStrings.get(R.string.control_cancel)) } }
        )
        return
    }
    SerialIrSettingsForm(saved, devices, manager::requestUsbPermission, manager::saveSerialSettings, onDismiss)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SerialIrSettingsForm(
    saved: SerialIrSettings,
    devices: List<SerialDeviceOption>,
    onRequestPermission: (String) -> Unit,
    onSave: suspend (SerialIrSettings, String?) -> Result<Unit>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = AppMotion.current
    var module by rememberSaveable(saved) { mutableStateOf(saved.module) }
    var baud by rememberSaveable(saved) {
        mutableStateOf(if (saved.module == SerialIrModule.IR_TOY) "9600" else saved.baudRate.toString())
    }
    var address by rememberSaveable(saved) { mutableStateOf(saved.moduleAddress.toString(16).uppercase()) }
    var port by rememberSaveable(saved) { mutableStateOf(saved.portIndex.toString()) }
    var selectedConnectionId by rememberSaveable(saved) { mutableStateOf<String?>(null) }
    var selectedLabel by rememberSaveable(saved) { mutableStateOf("") }
    val selectedDevice = devices.singleOrNull { it.connectionId == selectedConnectionId }
    val selectionMissing = selectedConnectionId != null && selectedDevice == null
    var deviceMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by rememberSaveable(stateSaver = UiTextStateSaver) { mutableStateOf<UiText?>(null) }
    val scope = rememberCoroutineScope()
    val wide = rememberWideLayout()
    val configuration = LocalConfiguration.current
    MotionAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = if (wide) modifier.fillMaxWidth(.96f).widthIn(max = 960.dp) else modifier,
        containerColor = ExpressiveSurface.canvas,
        properties = DialogProperties(usePlatformDefaultWidth = !wide),
        title = { Text(AppStrings.get(R.string.control_serial_config)) },
        text = {
            ProvideExpressiveCanvas {
                val moduleEntries = SerialIrModule.entries
                val modules: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier.selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(ExpressiveShapeTokens.SegmentedGap)
                    ) {
                        moduleEntries.forEachIndexed { index, option ->
                            SingleChoiceListItem(
                                title = option.label,
                                selected = module == option,
                                enabled = !saving,
                                index = index,
                                count = moduleEntries.size,
                                onClick = {
                                    // Reselecting the current module must not overwrite typed values.
                                    module = option
                                    error = null
                                }
                            )
                        }
                    }
                }
                val parameters: @Composable (Dp) -> Unit = { topPadding ->
                    SerialFormSection(visible = module != SerialIrModule.UNCONFIGURED, topPadding = topPadding) {
                        val hasDevices = devices.isNotEmpty()
                        val selectorInteractive = hasDevices && !saving
                        val deviceLabel = selectedDevice?.label ?: if (selectionMissing) selectedLabel
                            else if (saved.vendorId == null) AppStrings.get(R.string.control_select_serial)
                            else AppStrings.get(R.string.control_keep_serial)
                        val deviceSupport = when {
                            selectionMissing -> AppStrings.get(R.string.control_select_again)
                            !hasDevices -> AppStrings.get(R.string.control_no_serial_usb)
                            selectedDevice != null -> AppStrings.get(R.string.control_port_count, selectedDevice.portCount)
                            else -> null
                        }
                        Box(Modifier.fillMaxWidth()) {
                            Surface(
                                onClick = { deviceMenu = true },
                                enabled = selectorInteractive,
                                shape = RoundedCornerShape(16.dp),
                                color = if (hasDevices) ExpressiveSurface.card else ExpressiveSurface.card.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 56.dp)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Usb,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (hasDevices) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = deviceLabel,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (hasDevices || selectionMissing) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (deviceSupport != null) {
                                            Text(
                                                text = deviceSupport,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (selectionMissing) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (hasDevices) {
                                        Icon(
                                            imageVector = Icons.Rounded.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            ExpressiveDropdownMenu(
                                expanded = deviceMenu,
                                onDismissRequest = { deviceMenu = false },
                                selectable = true
                            ) {
                                devices.forEachIndexed { index, device ->
                                    val chosen = device.connectionId == selectedConnectionId
                                    ExpressiveSelectableMenuItem(
                                        selected = chosen,
                                        onClick = {
                                            if (!chosen) port = "0"
                                            selectedConnectionId = device.connectionId
                                            selectedLabel = device.label
                                            error = null
                                            deviceMenu = false
                                        },
                                        text = { Text(device.label) },
                                        supportingText = {
                                            Column {
                                                Text(AppStrings.get(R.string.control_port_count, device.portCount))
                                                if (devices.count { it.label == device.label } > 1) {
                                                    Text(device.deviceName)
                                                }
                                            }
                                        },
                                        index = index,
                                        count = devices.size
                                    )
                                }
                            }
                        }
                        SerialFormSection(visible = selectedDevice?.granted == false, topPadding = 0.dp) {
                            FilledTonalButton(
                                onClick = {
                                    selectedDevice?.takeUnless { it.granted }?.let { onRequestPermission(it.deviceName) }
                                },
                                enabled = !saving && selectedDevice?.granted == false,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Text(AppStrings.get(R.string.control_authorize_usb))
                            }
                        }
                        ExpressiveTextField(
                            value = port,
                            onValueChange = { port = it; error = null },
                            label = { Text(AppStrings.get(R.string.control_port_index)) },
                            supportingText = { Text(AppStrings.get(R.string.control_zero_based)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !saving,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    SerialFormSection(visible = module == SerialIrModule.YS_IRTM, topPadding = 12.dp) {
                        ExpressiveTextField(
                            value = baud,
                            onValueChange = { baud = it; error = null },
                            label = { Text(AppStrings.get(R.string.control_baud_rate)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !saving,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        ExpressiveTextField(
                            value = address,
                            onValueChange = { address = it; error = null },
                            label = { Text(AppStrings.get(R.string.control_module_address)) },
                            supportingText = { Text(AppStrings.get(R.string.control_hexadecimal)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !saving
                        )
                    }
                    SerialFormSection(visible = module == SerialIrModule.IR_TOY, topPadding = 12.dp) {
                        Text(
                            AppStrings.get(R.string.control_firmware),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    // Keep the outgoing message until its exit completes, including its spacing.
                    AnimatedContent(error, transitionSpec = {
                        (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                            .using(SizeTransform { _, _ -> motion.defaultSpatialSpec() })
                    }, label = "SerialFormError") { message ->
                        if (message != null) Text(message.asString(), modifier = Modifier.padding(top = 12.dp),
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                val maxBodyHeight = (configuration.screenHeightDp.dp - 160.dp)
                    .coerceAtMost(520.dp)
                    .coerceAtLeast(80.dp)
                if (wide) {
                    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = maxBodyHeight)) {
                        if (maxWidth >= 480.dp) {
                            TwoPaneLayout(
                                primaryFraction = .4f,
                                contentPadding = PaddingValues(0.dp),
                                primary = {
                                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { modules() }
                                },
                                secondary = {
                                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { parameters(0.dp) }
                                }
                            )
                        } else {
                            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                modules()
                                parameters(16.dp)
                            }
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth()
                            .heightIn(max = maxBodyHeight)
                            .verticalScroll(rememberScrollState())
                    ) {
                        modules()
                        parameters(16.dp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && (module == SerialIrModule.UNCONFIGURED || !selectionMissing), onClick = {
                val config = try {
                    serialSettingsFromDraft(saved, module, port, baud, address)
                } catch (invalid: SerialValidationException) {
                    error = UiText.Resource(invalid.messageRes)
                    return@TextButton
                } catch (_: IllegalArgumentException) {
                    error = UiText.Resource(R.string.control_config_save_failed)
                    return@TextButton
                }
                saving = true
                scope.launch {
                    try {
                        onSave(config, selectedConnectionId.takeUnless { module == SerialIrModule.UNCONFIGURED })
                            .onSuccess { onDismiss() }
                            .onFailure { error = it.toAppError(R.string.control_config_save_failed).message }
                    } finally { saving = false }
                }
            }, shapes = ButtonDefaults.shapes()) {
                StateFade(saving) { active ->
                    Text(if (active) AppStrings.get(R.string.control_saving) else AppStrings.get(R.string.control_save))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving, shapes = ButtonDefaults.shapes()) { Text(AppStrings.get(R.string.control_cancel)) } }
    )
}

/** Include spacing in the transition so hidden sections leave no blank rows. */
@Composable
private fun SerialFormSection(
    visible: Boolean,
    topPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    ExpandSection(visible) {
        Column(Modifier.padding(top = topPadding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

internal class SerialValidationException(@param:StringRes val messageRes: Int) : IllegalArgumentException()

/** Only validate the inputs used by the selected module; hidden drafts remain editable. */
internal fun serialSettingsFromDraft(
    saved: SerialIrSettings,
    module: SerialIrModule,
    port: String,
    baud: String,
    address: String
): SerialIrSettings {
    if (module == SerialIrModule.UNCONFIGURED) return SerialIrSettings()
    val portValue = port.toIntOrNull()
        ?: throw SerialValidationException(R.string.control_invalid_port)
    if (portValue !in 0..31) {
        throw SerialValidationException(R.string.msg_serial_port_range)
    }
    val baudValue = if (module == SerialIrModule.IR_TOY) {
        115200
    } else {
        baud.toIntOrNull() ?: throw SerialValidationException(R.string.control_invalid_baud)
    }
    if (module != SerialIrModule.IR_TOY && baudValue !in listOf(4800, 9600, 19200, 57600)) {
        throw SerialValidationException(R.string.msg_serial_baud_rates)
    }
    val addressValue = if (module == SerialIrModule.IR_TOY) {
        saved.moduleAddress
    } else {
        address.removePrefix("0x").removePrefix("0X").toIntOrNull(16)
            ?: throw SerialValidationException(R.string.control_invalid_address)
    }
    if (addressValue !in 0..255 || addressValue == 0xFA) {
        throw SerialValidationException(R.string.msg_module_address_range)
    }
    if ((saved.vendorId == null) != (saved.productId == null)) {
        throw SerialValidationException(R.string.msg_usb_ids_incomplete)
    }
    if (saved.vendorId != null && (saved.vendorId !in 0..65535 || saved.productId!! !in 0..65535)) {
        throw SerialValidationException(R.string.msg_usb_ids_invalid)
    }
    return saved.copy(module = module, portIndex = portValue, baudRate = baudValue, moduleAddress = addressValue)
        .also { it.validate() }
}
