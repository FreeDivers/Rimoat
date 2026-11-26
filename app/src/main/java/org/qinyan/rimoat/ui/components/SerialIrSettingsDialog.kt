package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.ui.motion.AppMotion
import org.qinyan.rimoat.ui.motion.ExpandSection
import org.qinyan.rimoat.ui.motion.StateFade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.KeyboardType
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

@Composable
fun SerialIrSettingsDialog(manager: IrTransmitterManager, onDismiss: () -> Unit) {
    val saved by manager.serialSettings.collectAsStateWithLifecycle()
    val devices by manager.serialDevices.collectAsStateWithLifecycle()
    val loaded by manager.serialSettingsLoaded.collectAsStateWithLifecycle()
    // Initialize the editable draft only after the persisted settings are available.
    if (!loaded) {
        MotionAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(AppStrings.get(R.string.control_serial_config, "串口模块配置")) },
            text = { LinearWavyProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(AppStrings.get(R.string.control_cancel, "取消")) } }
        )
        return
    }
    SerialIrSettingsForm(saved, devices, manager::requestUsbPermission, manager::saveSerialSettings, onDismiss)
}

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
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val wide = rememberWideLayout()
    val configuration = LocalConfiguration.current
    MotionAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = if (wide) modifier.fillMaxWidth(.96f).widthIn(max = 960.dp) else modifier,
        properties = DialogProperties(usePlatformDefaultWidth = !wide),
        title = { Text(AppStrings.get(R.string.control_serial_config, "串口模块配置")) },
        text = {
            val modules: @Composable () -> Unit = {
                Column(Modifier.selectableGroup()) {
                    SerialIrModule.entries.forEach { option ->
                        SingleChoiceListItem(
                            title = option.label, selected = module == option, enabled = !saving,
                            onClick = {
                                // Reselecting the current module must not overwrite typed values.
                                module = option
                                error = null
                            }
                        )
                    }
                }
            }
            val parameters: @Composable () -> Unit = {
                SerialFormSection(visible = module != SerialIrModule.UNCONFIGURED) {
                    HorizontalDivider()
                    Column {
                        ExpandSection(visible = devices.isEmpty()) {
                            Text(AppStrings.get(R.string.control_no_serial_usb, "未连接 USB 串口"),
                                modifier = Modifier.padding(bottom = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            OutlinedButton(onClick = { deviceMenu = true }, enabled = devices.isNotEmpty() && !saving, shapes = ButtonDefaults.shapes()) {
                                Text(selectedDevice?.label ?: if (selectionMissing) selectedLabel
                                    else if (saved.vendorId == null) AppStrings.get(R.string.control_select_serial, "选择 USB 串口") else AppStrings.get(R.string.control_keep_serial, "保留已绑定的 USB 串口"))
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
                                                Text(AppStrings.get(R.string.control_port_count, "%1\$d 个串口", device.portCount))
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
                        SerialFormSection(visible = selectionMissing) {
                            Text(AppStrings.get(R.string.control_select_again, "连接已断开，请重新选择"), color = MaterialTheme.colorScheme.error)
                        }
                        SerialFormSection(visible = selectedDevice?.granted == false) {
                            TextButton(onClick = {
                                selectedDevice?.takeUnless { it.granted }?.let { onRequestPermission(it.deviceName) }
                            }, enabled = !saving && selectedDevice?.granted == false, shapes = ButtonDefaults.shapes()) {
                                Text(AppStrings.get(R.string.control_authorize_usb, "授权所选 USB 设备"))
                            }
                        }
                        OutlinedTextField(value = port, onValueChange = { port = it; error = null }, label = { Text(AppStrings.get(R.string.control_port_index, "串口序号")) },
                            supportingText = { Text(AppStrings.get(R.string.control_zero_based, "从 0 开始")) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true, enabled = !saving,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
                SerialFormSection(visible = module == SerialIrModule.YS_IRTM) {
                    OutlinedTextField(value = baud, onValueChange = { baud = it; error = null }, label = { Text(AppStrings.get(R.string.control_baud_rate, "波特率")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = address, onValueChange = { address = it; error = null }, label = { Text(AppStrings.get(R.string.control_module_address, "模块地址")) },
                        supportingText = { Text(AppStrings.get(R.string.control_hexadecimal, "十六进制")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true, enabled = !saving)
                }
                SerialFormSection(visible = module == SerialIrModule.IR_TOY) {
                    Text(AppStrings.get(R.string.control_firmware, "固件 ≥ 22\n115200 / 8N1"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Keep the outgoing message until its exit completes, including its spacing.
                AnimatedContent(error, transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                        .using(SizeTransform { _, _ -> motion.defaultSpatialSpec() })
                }, label = "SerialFormError") { message ->
                    if (message != null) Text(message, modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.error)
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().heightIn(
                max = (configuration.screenHeightDp.dp - 160.dp).coerceAtLeast(80.dp))) {
                if (maxWidth >= 480.dp) {
                    TwoPaneLayout(primaryFraction = .4f, primary = {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { modules() }
                    }, secondary = {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { parameters() }
                    })
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        modules()
                        parameters()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && (module == SerialIrModule.UNCONFIGURED || !selectionMissing), onClick = {
                val config = try {
                    serialSettingsFromDraft(saved, module, port, baud, address)
                } catch (invalid: IllegalArgumentException) {
                    error = invalid.message
                    return@TextButton
                }
                saving = true
                scope.launch {
                    try {
                        onSave(config, selectedConnectionId.takeUnless { module == SerialIrModule.UNCONFIGURED })
                            .onSuccess { onDismiss() }
                            .onFailure { error = it.message ?: AppStrings.get(R.string.control_config_save_failed, "配置保存失败") }
                    } finally { saving = false }
                }
            }, shapes = ButtonDefaults.shapes()) {
                StateFade(saving) { active ->
                    Text(if (active) AppStrings.get(R.string.control_saving, "保存中…") else AppStrings.get(R.string.control_save, "保存"))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving, shapes = ButtonDefaults.shapes()) { Text(AppStrings.get(R.string.control_cancel, "取消")) } }
    )
}

/** Include spacing in the transition so hidden sections leave no blank rows. */
@Composable
private fun SerialFormSection(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    ExpandSection(visible) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Only validate the inputs used by the selected module; hidden drafts remain editable. */
internal fun serialSettingsFromDraft(
    saved: SerialIrSettings,
    module: SerialIrModule,
    port: String,
    baud: String,
    address: String
): SerialIrSettings {
    if (module == SerialIrModule.UNCONFIGURED) return SerialIrSettings()
    val portValue = requireNotNull(port.toIntOrNull()) { AppStrings.get(R.string.control_invalid_port, "请输入有效的串口序号") }
    val baudValue = if (module == SerialIrModule.IR_TOY) 115200
        else requireNotNull(baud.toIntOrNull()) { AppStrings.get(R.string.control_invalid_baud, "请输入有效的波特率") }
    val addressValue = if (module == SerialIrModule.IR_TOY) saved.moduleAddress else
        requireNotNull(address.removePrefix("0x").removePrefix("0X").toIntOrNull(16)) { AppStrings.get(R.string.control_invalid_address, "请输入有效的十六进制地址") }
    return saved.copy(module = module, portIndex = portValue, baudRate = baudValue, moduleAddress = addressValue)
        .also { it.validate() }
}
