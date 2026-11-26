package org.qinyan.rimoat.hardware

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.qinyan.rimoat.hardware.audio.AudioIrTransmitter
import org.qinyan.rimoat.hardware.serial.UsbSerialTransmitter
import org.qinyan.rimoat.hardware.usb.ElkSmartUsbTransmitter
import org.qinyan.rimoat.hardware.usb.TiqiaaUsbTransmitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.qinyan.rimoat.ui.HapticHelper

data class TransmitLog(
    val timestamp: Long = System.currentTimeMillis(),
    val transmitterType: TransmitterType,
    val signal: IrSignal,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val ownerId: String? = null
)

data class TransmissionReceipt(val simulated: Boolean)

/**
 * Unified IR hardware manager coordinating built-in, USB dongles,
 * 3.5mm audio jack PCM, and Mock transmitters.
 * Ensures the UI layer remains decoupled from hardware details.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IrTransmitterManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val appContext: Context = try { context.applicationContext ?: context } catch (_: Throwable) { context }
    private val deferInitialHardwareCheck: Boolean = try {
        val main = android.os.Looper.getMainLooper()
        main != null && android.os.Looper.myLooper() == main
    } catch (_: Throwable) {
        false
    }
    @Volatile
    private var initialHardwareChecked: Boolean = !deferInitialHardwareCheck

    val builtInTransmitter: BuiltInIrTransmitter = BuiltInIrTransmitter(appContext, refreshImmediately = !deferInitialHardwareCheck)
    val tiqiaaTransmitter: TiqiaaUsbTransmitter = TiqiaaUsbTransmitter(appContext, refreshImmediately = !deferInitialHardwareCheck)
    val elkSmartTransmitter: ElkSmartUsbTransmitter = ElkSmartUsbTransmitter(appContext, refreshImmediately = !deferInitialHardwareCheck)
    val usbSerialTransmitter: UsbSerialTransmitter = UsbSerialTransmitter(appContext, scope)
    val audioTransmitter: AudioIrTransmitter = AudioIrTransmitter(appContext, refreshImmediately = !deferInitialHardwareCheck)
    val mockTransmitter: MockIrTransmitter = MockIrTransmitter()

    val serialSettings get() = usbSerialTransmitter.settings
    val serialDevices get() = usbSerialTransmitter.devices
    val serialStatus get() = usbSerialTransmitter.status
    val serialSettingsLoaded get() = usbSerialTransmitter.loaded
    suspend fun saveSerialSettings(settings: org.qinyan.rimoat.hardware.serial.SerialIrSettings, connectionId: String?) =
        usbSerialTransmitter.saveSettings(settings, connectionId)

    private val usbPermissionsLazy = lazy {
        UsbPermissionController(appContext) { scope.launch(Dispatchers.IO) { refreshHardware() } }
    }
    private val usbPermissions get() = usbPermissionsLazy.value
    val usbDevices get() = usbPermissions.devices
    fun requestUsbPermission(deviceName: String) = usbPermissions.request(deviceName)

    private val preferences = appContext.getSharedPreferences("rimoat_transmitter", Context.MODE_PRIVATE)
    private val _forceMockMode = MutableStateFlow(preferences?.getBoolean("force_mock", false) ?: false)
    val forceMockMode: StateFlow<Boolean> = _forceMockMode.asStateFlow()

    private val _selectedTransmitterType = MutableStateFlow(
        preferences?.getString("selected_type", null)?.let { name -> TransmitterType.entries.firstOrNull { it.name == name } })
    val selectedTransmitterType: StateFlow<TransmitterType?> = _selectedTransmitterType.asStateFlow()

    private val _lastTransmitLog = MutableStateFlow<TransmitLog?>(null)
    val lastTransmitLog: StateFlow<TransmitLog?> = _lastTransmitLog.asStateFlow()

    // Dynamic receivers
    private var usbReceiver: BroadcastReceiver? = null
    private var audioReceiver: BroadcastReceiver? = null
    private val hardwareRefreshMutex = Mutex()

    init {
        registerReceivers()
        if (deferInitialHardwareCheck) {
            scope.launch(Dispatchers.IO) { awaitInitialHardwareCheck() }
        }
    }

    private fun registerReceivers() {
        try {
            val usbFilter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            val uReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                        @Suppress("DEPRECATION")
                        val device = intent.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)
                        usbSerialTransmitter.onDeviceDetached(device?.deviceName)
                    }
                    scope.launch(Dispatchers.IO) { refreshHardware() }
                }
            }
            ContextCompat.registerReceiver(appContext, uReceiver, usbFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
            usbReceiver = uReceiver
        } catch (e: Exception) {
            Log.w("IrTransmitterManager", "Failed to register USB receiver", e)
        }

        try {
            val audioFilter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
            val aReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val state = intent?.getIntExtra("state", -1) ?: -1
                    if (state == 1) {
                        audioTransmitter.updateHeadsetPlugged(true)
                    } else if (state == 0) {
                        audioTransmitter.updateHeadsetPlugged(false)
                    }
                }
            }
            ContextCompat.registerReceiver(appContext, aReceiver, audioFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
            audioReceiver = aReceiver
        } catch (e: Exception) {
            Log.w("IrTransmitterManager", "Failed to register audio receiver", e)
        }
    }

    suspend fun refreshHardware() = withContext(Dispatchers.IO) {
        hardwareRefreshMutex.withLock {
            refreshHardwareLocked()
        }
    }

    private fun refreshHardwareLocked() {
        val sharedUsbDevices = try {
            (appContext.getSystemService(Context.USB_SERVICE) as? UsbManager)?.deviceList?.values
        } catch (_: Throwable) {
            null
        }
        try { tiqiaaTransmitter.refreshState(sharedUsbDevices) } catch (_: Throwable) {}
        try { elkSmartTransmitter.refreshState(sharedUsbDevices) } catch (_: Throwable) {}
        try { usbSerialTransmitter.refreshState() } catch (_: Throwable) {}
        try { audioTransmitter.refreshState() } catch (_: Throwable) {}
        try { builtInTransmitter.checkHardwareAvailability() } catch (_: Throwable) {}
        initialHardwareChecked = true
    }

    fun setForceMockMode(forced: Boolean) {
        preferences?.edit()?.putBoolean("force_mock", forced)?.apply()
        _forceMockMode.value = forced
    }

    fun selectTransmitterType(type: TransmitterType?) {
        preferences?.edit()?.putString("selected_type", type?.name)?.apply()
        _selectedTransmitterType.value = type
    }

    val availableTransmitters: StateFlow<List<IrTransmitter>> = combine(
        listOf(
            builtInTransmitter.isReady,
            tiqiaaTransmitter.isReady,
            elkSmartTransmitter.isReady,
            usbSerialTransmitter.isReady,
            audioTransmitter.isReady
        )
    ) {
        val list = mutableListOf<IrTransmitter>()
        if (tiqiaaTransmitter.isReady.value) list.add(tiqiaaTransmitter)
        if (elkSmartTransmitter.isReady.value) list.add(elkSmartTransmitter)
        if (usbSerialTransmitter.isReady.value) list.add(usbSerialTransmitter)
        if (audioTransmitter.isReady.value) list.add(audioTransmitter)
        if (builtInTransmitter.isReady.value) list.add(builtInTransmitter)
        list.add(mockTransmitter)
        list
    }.stateIn(scope, SharingStarted.Eagerly, listOf(mockTransmitter))

    /**
     * Active transmitter resolved dynamically:
     * 1. If forceMockMode is enabled -> MockIrTransmitter
     * 2. If user selected a specific transmitter type -> use that transmitter
     * 3. Priority Auto-resolution:
     *    - Supported USB dongles, then serial IR modules
     *    - Native BuiltIn IR
     *    - 3.5mm Audio IR
     *    - Fallback -> MockIrTransmitter
     */
    private fun resolveActiveTransmitter(): IrTransmitter {
        val forceMock = _forceMockMode.value
        val manualType = _selectedTransmitterType.value
        val tiqiaaReady = tiqiaaTransmitter.isReady.value
        val elkSmartReady = elkSmartTransmitter.isReady.value
        val serialReady = usbSerialTransmitter.isReady.value
        val audioReady = audioTransmitter.isReady.value
        val builtInReady = builtInTransmitter.isReady.value

        if (forceMock) return mockTransmitter
        if (manualType != null) return when (manualType) {
            TransmitterType.USB_TIQIAA -> tiqiaaTransmitter
            TransmitterType.USB_ELKSMART -> elkSmartTransmitter
            TransmitterType.USB_SERIAL -> usbSerialTransmitter
            TransmitterType.USB -> if (elkSmartReady) elkSmartTransmitter else tiqiaaTransmitter
            TransmitterType.AUDIO -> audioTransmitter
            TransmitterType.BUILT_IN -> builtInTransmitter
            TransmitterType.MOCK, TransmitterType.NONE -> mockTransmitter
        }
        return when {
            tiqiaaReady -> tiqiaaTransmitter
            elkSmartReady -> elkSmartTransmitter
            serialReady -> usbSerialTransmitter
            builtInReady -> builtInTransmitter
            audioReady -> audioTransmitter
            else -> mockTransmitter
        }
    }

    val activeTransmitter: StateFlow<IrTransmitter> = combine(
        combine(_forceMockMode, _selectedTransmitterType) { force, manual -> Pair(force, manual) },
        combine(
            tiqiaaTransmitter.isReady,
            elkSmartTransmitter.isReady,
            usbSerialTransmitter.isReady,
            audioTransmitter.isReady
        ) { t, e, s, a -> listOf(t, e, s, a) },
        builtInTransmitter.isReady
    ) { _, _, _ ->
        resolveActiveTransmitter()
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        if (builtInTransmitter.isReady.value) builtInTransmitter else mockTransmitter
    )

    val transmitterType: StateFlow<TransmitterType> = combine(
        activeTransmitter,
        _forceMockMode
    ) { active, _ ->
        active.type
    }.stateIn(scope, SharingStarted.Eagerly, activeTransmitter.value.type)

    val isReady: StateFlow<Boolean> = activeTransmitter.flatMapLatest { active ->
        active.isReady
    }.stateIn(scope, SharingStarted.Eagerly, activeTransmitter.value.isReady.value)

    private val transmitMutex = Mutex()

    suspend fun awaitInitialHardwareCheck() {
        if (initialHardwareChecked) return
        withContext(Dispatchers.IO) {
            hardwareRefreshMutex.withLock {
                if (!initialHardwareChecked) refreshHardwareLocked()
            }
        }
    }

    fun currentTransmitterType(): TransmitterType = resolveActiveTransmitter().type

    suspend fun currentTransmitterTypeAfterCheck(): TransmitterType {
        awaitInitialHardwareCheck()
        return resolveActiveTransmitter().type
    }

    suspend fun transmit(signal: IrSignal, ownerId: String? = null): Result<Unit> = transmitSequence(listOf(signal), ownerId)

    suspend fun transmitSequence(signals: List<IrSignal>, ownerId: String? = null): Result<Unit> =
        transmitWithReceipt(signals, ownerId).map { Unit }

    /** Receipt describes the route held for this entire transaction, including automatic fallback. */
    suspend fun transmitWithReceipt(
        signals: List<IrSignal>, ownerId: String? = null, expectedType: TransmitterType? = null,
        beforeTransmission: suspend () -> Unit = {}
    ): Result<TransmissionReceipt> = withContext(Dispatchers.IO) {
        transmitMutex.withLock {
            awaitInitialHardwareCheck()
            // Read the source settings synchronously here. The derived StateFlow
            // may still be waiting for its dispatcher after a user changes the
            // channel; a send must never use the previous route in that window.
            val transmitter = resolveActiveTransmitter()
            if (expectedType != null && transmitter.type != expectedType) {
                val error = IllegalStateException(AppStrings.get(R.string.msg_transmitter_changed, "发射通道已变化，请重新操作"))
                signals.firstOrNull()?.let { _lastTransmitLog.value = TransmitLog(
                    transmitterType = transmitter.type, signal = it, isSuccess = false,
                    errorMessage = error.message, ownerId = ownerId) }
                return@withLock Result.failure(error)
            }
            if (signals.isEmpty()) return@withLock Result.failure(IllegalArgumentException(AppStrings.get(R.string.msg_no_signals, "没有可发送的信号")))
            val usesUsb = transmitter.type in setOf(TransmitterType.USB, TransmitterType.USB_TIQIAA,
                TransmitterType.USB_ELKSMART, TransmitterType.USB_SERIAL)
            if (usesUsb) UsbSessionAccess.awaitCancelledLearning()
            if (usesUsb && !UsbSessionAccess.mutex.tryLock()) {
                val error = IllegalStateException(AppStrings.get(R.string.msg_usb_learning_active, "USB 正在学习，请完成或取消后再发射"))
                signals.firstOrNull()?.let { _lastTransmitLog.value = TransmitLog(
                    transmitterType = transmitter.type, signal = it, isSuccess = false, errorMessage = error.message, ownerId = ownerId) }
                return@withLock Result.failure(error)
            }
            try {
                // Run only after waiting for the global/USB locks, before any physical frame.
                beforeTransmission()
                for ((index, signal) in signals.withIndex()) {
                    val result = transmitOne(transmitter, signal, ownerId)
                    if (result.isFailure) return@withLock Result.failure(result.exceptionOrNull()!!)
                    if (index < signals.lastIndex) delay(100)
                }
                if (transmitter.type != TransmitterType.MOCK) {
                    // Confirm one completed hardware transaction, never simulation or appliance reception.
                    HapticHelper.performTransmissionConfirmed(appContext)
                }
                Result.success(TransmissionReceipt(transmitter.type == TransmitterType.MOCK))
            } finally {
                if (usesUsb) UsbSessionAccess.mutex.unlock()
            }
        }
    }

    private suspend fun transmitOne(transmitter: IrTransmitter, signal: IrSignal, ownerId: String?): Result<Unit> {
        try {
            val patternHash = (signal as? IrSignal.Raw)?.patternMicros?.contentHashCode()
            android.util.Log.d("IrTransmitterManager", "Routing signal to ${transmitter.type}: $signal, patternHash=$patternHash")
        } catch (_: Throwable) {}

        val result = try { transmitter.transmit(signal) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }

        try {
            android.util.Log.d("IrTransmitterManager", "Transmit completed via ${transmitter.type}: success=${result.isSuccess}, error=${result.exceptionOrNull()?.message}")
        } catch (_: Throwable) {}

        _lastTransmitLog.value = TransmitLog(
            timestamp = System.currentTimeMillis(),
            transmitterType = transmitter.type,
            signal = signal,
            isSuccess = result.isSuccess,
            errorMessage = result.exceptionOrNull()?.message,
            ownerId = ownerId
        )


        return result
    }

    fun release() {
        if (usbPermissionsLazy.isInitialized()) {
            try { usbPermissionsLazy.value.close() } catch (_: Exception) {}
        }
        try {
            usbReceiver?.let { appContext.unregisterReceiver(it) }
        } catch (_: Throwable) {}
        try {
            audioReceiver?.let { appContext.unregisterReceiver(it) }
        } catch (_: Throwable) {}
        usbReceiver = null
        audioReceiver = null
    }
}
