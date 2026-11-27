package org.qinyan.rimoat.hardware.serial

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import android.content.Context
import android.hardware.usb.UsbManager
import org.qinyan.rimoat.data.SerialIrSettingsStore
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitter
import org.qinyan.rimoat.hardware.TransmitterType
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** UART initialization is delegated to the bridge driver; IR framing is explicitly configured. */
class UsbSerialTransmitter(context: Context, private val scope: CoroutineScope) : IrTransmitter {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val store by lazy { SerialIrSettingsStore(File(context.filesDir, "serial_ir.json")) }
    private val mutex = Mutex()
    private val connections = SerialConnections()
    private var selectedPath: String? = null
    private var initialized = false
    private var loadFailed = false
    override val type = TransmitterType.USB_SERIAL
    private val _isReady = MutableStateFlow(false)
    override val isReady = _isReady.asStateFlow()
    private val _settings = MutableStateFlow(SerialIrSettings())
    val settings = _settings.asStateFlow()
    private val _devices = MutableStateFlow<List<SerialDeviceOption>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _status = MutableStateFlow(AppStrings.get(R.string.msg_serial_settings_loading))
    val status = _status.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                try { _settings.value = store.read() }
                catch (_: Exception) { loadFailed = true }
                initialized = true
                _loaded.value = true
                refreshLocked()
            }
        }
    }

    fun refreshState() { scope.launch(Dispatchers.IO) { mutex.withLock { refreshLocked() } } }

    fun onDeviceDetached(deviceName: String?) { connections.detached(deviceName) }

    private fun connectedDevices(): List<SerialDeviceOption> = connections.snapshot(
        usbManager?.deviceList?.values.orEmpty().mapNotNull { device ->
            val driver = UsbUartProtocol.probe(device) ?: return@mapNotNull null
            val granted = usbManager?.hasPermission(device) == true
            val serial = if (granted) runCatching { device.serialNumber }.getOrNull() else null
            SerialDeviceOption(device.deviceName,
                "${device.productName ?: driver.javaClass.simpleName} (${device.vendorId.toString(16).padStart(4, '0')}:${device.productId.toString(16).padStart(4, '0')})",
                device.vendorId, device.productId, driver.ports.size, granted, serial)
        }.sortedBy { it.deviceName }
    )

    private fun refreshLocked() {
        try {
            val connected = connectedDevices()
            if (connected.none { it.deviceName == selectedPath }) selectedPath = null
            _devices.value = connected
            val config = _settings.value
            val selected = selectSerialDevice(config, connected, selectedPath)
            _isReady.value = initialized && !loadFailed && selected?.granted == true
            _status.value = when {
                !initialized -> AppStrings.get(R.string.msg_serial_settings_loading)
                loadFailed -> AppStrings.get(R.string.msg_serial_settings_load_failed)
                config.module == SerialIrModule.UNCONFIGURED -> AppStrings.get(R.string.msg_serial_select_module)
                config.vendorId == null -> AppStrings.get(R.string.msg_serial_connect_bind, (config.module.label).toString())
                selected == null && serialPermissionCandidates(config, connected).isNotEmpty() ->
                    AppStrings.get(R.string.msg_serial_permission_needed)
                selected == null -> AppStrings.get(R.string.msg_serial_match_missing)
                !selected.granted -> AppStrings.get(R.string.msg_serial_permission_needed)
                else -> AppStrings.get(R.string.msg_serial_status, (config.module.label).toString(), (config.baudRate).toString(), (config.portIndex).toString())
            }
        } catch (e: Exception) {
            _devices.value = emptyList()
            _isReady.value = false
            _status.value = AppStrings.get(R.string.msg_serial_status_unavailable, (e.message ?: e.javaClass.simpleName))
        }
    }

    suspend fun saveSettings(config: SerialIrSettings, connectionId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                config.validate()
                val device = connectionId?.let { selectedSerialConnection(connectedDevices(), it) }
                require(device == null || config.portIndex < device.portCount) { AppStrings.get(R.string.msg_serial_port_unavailable) }
                val bound = when {
                    config.module == SerialIrModule.UNCONFIGURED -> config.copy(vendorId = null, productId = null, serialNumber = null)
                    device != null -> config.copy(vendorId = device.vendorId, productId = device.productId, serialNumber = device.serialNumber)
                    else -> config
                }
                store.write(bound)
                selectedPath = device?.deviceName
                _settings.value = bound
                loadFailed = false
                refreshLocked()
                Result.success(Unit)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
        }
    }

    override suspend fun transmit(signal: IrSignal): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                refreshLocked()
                check(_isReady.value) { _status.value }
                val config = _settings.value
                val selected = selectSerialDevice(config, _devices.value, selectedPath) ?: error(AppStrings.get(R.string.msg_serial_disconnected))
                val usb = usbManager ?: error(AppStrings.get(R.string.msg_usb_manager_unavailable))
                val device = usb.deviceList[selected.deviceName] ?: error(AppStrings.get(R.string.msg_usb_serial_unplugged))
                check(usb.hasPermission(device)) { AppStrings.get(R.string.msg_usb_serial_permission_missing) }
                val driver = UsbUartProtocol.probe(device) ?: error(AppStrings.get(R.string.msg_usb_serial_chip_unsupported))
                val port = driver.ports.getOrNull(config.portIndex) ?: error(AppStrings.get(R.string.msg_usb_serial_missing))
                val connection = usb.openDevice(device) ?: error(AppStrings.get(R.string.msg_usb_serial_open_failed))
                try {
                    port.open(connection)
                    port.setParameters(config.baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    port.flowControl = UsbSerialPort.FlowControl.NONE
                    SerialIrSession(object : SerialPortIo {
                        override fun write(data: ByteArray, timeoutMs: Int) = port.write(data, timeoutMs)
                        override fun read(buffer: ByteArray, timeoutMs: Int) = port.read(buffer, timeoutMs)
                    }).transmit(config, signal)
                } finally {
                    try { port.close() } catch (_: Exception) {}
                    connection.close()
                }
                Result.success(Unit)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
            finally { refreshLocked() }
        }
    }
}
