package org.qinyan.rimoat.hardware.learning

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import org.qinyan.rimoat.hardware.HardwareEnvironment
import org.qinyan.rimoat.hardware.IrSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Unified IR learning scheduler and façade.
 *
 * Coordinates dynamic backend selection across supported USB receivers,
 * built-in OEM IR learning hardware, and Mock simulation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IrLearnerManager(
    private val context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    companion object {
        private const val TAG = "IrLearnerManager"
        const val ACTION_USB_PERMISSION = "org.qinyan.rimoat.USB_PERMISSION"

        private fun logI(tag: String, msg: String) {
            try { Log.i(tag, msg) } catch (_: Throwable) {}
        }
        private fun logD(tag: String, msg: String) {
            try { Log.d(tag, msg) } catch (_: Throwable) {}
        }
        private fun logW(tag: String, msg: String) {
            try { Log.w(tag, msg) } catch (_: Throwable) {}
        }
    }

    private val appContext: Context? = try { context?.applicationContext } catch (_: Throwable) { null }
    private val usbManager: UsbManager? by lazy {
        try {
            appContext?.getSystemService(Context.USB_SERVICE) as? UsbManager
        } catch (e: Exception) {
            logW(TAG, "UsbManager service acquisition failed: ${e.message}")
            null
        }
    }

    private val _receiveConfirmed = MutableStateFlow(false)
    val receiveConfirmed = _receiveConfirmed.asStateFlow()

    val mockLearner = MockIrLearner()
    val noHardwareLearner = NoHardwareLearner()
    private val huaweiLearner: HuaweiIrLearner? by lazy {
        val app = appContext ?: return@lazy null
        if (HuaweiIrLearner.isSupported(app)) {
            HuaweiIrLearner.open(app)
        } else {
            null
        }
    }

    private val _usbLearner = MutableStateFlow<IrLearner?>(null)
    private val _forceMockMode = MutableStateFlow(false)
    val forceMockMode: StateFlow<Boolean> = _forceMockMode.asStateFlow()

    private val learningMutex = Mutex()
    @Volatile private var learningJob: kotlinx.coroutines.Job? = null
    private val _runningLearner = MutableStateFlow<IrLearner?>(null)
    private val deferInitialUsbScan: Boolean = try {
        val main = android.os.Looper.getMainLooper()
        main != null && android.os.Looper.myLooper() == main
    } catch (_: Throwable) {
        false
    }
    @Volatile
    private var initialUsbScanDone: Boolean = !deferInitialUsbScan

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            logD(TAG, "USB broadcast received: $action")
            when (action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED,
                ACTION_USB_PERMISSION -> {
                    scanUsbDevices()
                }
            }
        }
    }

    init {
        UsbDeviceReceiver.onDeviceEvent = { scanUsbDevices() }
        registerUsbReceiver()
        if (deferInitialUsbScan) {
            scope.launch(Dispatchers.IO) { scanUsbDevices() }
        } else {
            scanUsbDevices()
        }
    }

    private fun registerUsbReceiver() {
        val app = appContext ?: return
        try {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(ACTION_USB_PERMISSION)
            }
            ContextCompat.registerReceiver(app, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (t: Throwable) {
            logW(TAG, "Failed to register USB receiver: ${t.message}")
        }
    }

    fun unregisterReceiver() {
        try {
            appContext?.unregisterReceiver(usbReceiver)
        } catch (_: Throwable) {
        }
    }

    /**
     * Scans attached USB devices and attaches compatible IR learners.
     */
    fun scanUsbDevices() {
        val manager = usbManager
        if (manager == null) {
            initialUsbScanDone = true
            return
        }
        try {
            val devices = manager.deviceList.values
            var matchedLearner: IrLearner? = null

            for (dev in devices) {
                if (!manager.hasPermission(dev) || !UsbDeviceFilter.isSupported(dev)) continue
                val existing = _usbLearner.value as? LazyUsbLearner
                matchedLearner = if (existing?.deviceName == dev.deviceName) existing else LazyUsbLearner(
                    dev.deviceName,
                    if (UsbDeviceFilter.isTiqiaa(dev)) LearnerType.USB_TIQIAA else LearnerType.USB_ELKSMART
                ) {
                    if (UsbDeviceFilter.isTiqiaa(dev)) TiqiaaUsbLearner.open(manager, dev)
                    else ElkSmartUsbLearner.open(manager, dev)
                }
                break
            }

            val oldLearner = _usbLearner.value
            if (oldLearner != matchedLearner) {
                if (oldLearner is AutoCloseable) {
                    try {
                        oldLearner.close()
                    } catch (_: Throwable) {
                    }
                }
                _receiveConfirmed.value = false
                _usbLearner.value = matchedLearner
            }
        } catch (t: Throwable) {
            logW(TAG, "Error scanning USB devices: ${t.message}")
        } finally {
            initialUsbScanDone = true
        }
    }

    fun setForceMockMode(forced: Boolean) {
        if (_forceMockMode.value != forced) cancel()
        _forceMockMode.value = forced
    }

    private fun resolveLearner(): IrLearner = when {
        _forceMockMode.value -> mockLearner
        _usbLearner.value?.isAvailable?.value == true -> _usbLearner.value!!
        huaweiLearner?.isAvailable?.value == true -> huaweiLearner!!
        isEmulatorOrTestEnvironment() -> mockLearner
        else -> noHardwareLearner
    }

    private fun isEmulatorOrTestEnvironment(): Boolean {
        if (context == null) return true // TestScope or JVM tests without Context
        return HardwareEnvironment.isEmulator()
    }

    /**
     * Dynamically resolved active IR learner:
     * 1. If forceMockMode is enabled -> MockIrLearner
     * 2. If a supported USB IR learner is attached -> use that learner
     * 3. If a compatible built-in IR learning service is available -> use that service
     * 4. If running in Emulator or test environment -> MockIrLearner
     * 5. Physical device with no hardware attached -> NoHardwareLearner
     */
    val activeLearner: StateFlow<IrLearner> = combine(
        _forceMockMode,
        _usbLearner
    ) { _, _ ->
        withContext(Dispatchers.IO) { resolveLearner() }
    }.stateIn(scope, SharingStarted.Eagerly, if (isEmulatorOrTestEnvironment()) mockLearner else noHardwareLearner)

    val learnerType: StateFlow<LearnerType> = activeLearner.flatMapLatest { learner ->
        MutableStateFlow(learner.type)
    }.stateIn(scope, SharingStarted.Eagerly, activeLearner.value.type)

    val isAvailable: StateFlow<Boolean> = activeLearner.flatMapLatest { learner ->
        learner.isAvailable
    }.stateIn(scope, SharingStarted.Eagerly, activeLearner.value.isAvailable.value)

    val state: StateFlow<LearningState> = combine(
        activeLearner,
        _runningLearner
    ) { active, running ->
        running ?: active
    }.distinctUntilChanged().flatMapLatest { learner ->
        learner.state
    }.stateIn(scope, SharingStarted.Eagerly, activeLearner.value.state.value)

    init {
        scope.launch {
            activeLearner.collect { newActive ->
                val running = _runningLearner.value
                if (running != null && running !== withContext(Dispatchers.IO) { resolveLearner() }) {
                    logI(TAG, "Active learner changed from ${running.type} to ${newActive.type} during active learning; cancelling running session")
                    running.cancel()
                }
            }
        }
    }

    /**
     * Initiates learning using the active learner backend under mutex mutual exclusion.
     */
    suspend fun startLearning(timeoutSeconds: Int = 30): Result<IrSignal.Raw> = withContext(Dispatchers.IO) {
        learningMutex.withLock {
            if (!initialUsbScanDone) scanUsbDevices()
            val learner = resolveLearner()
            _runningLearner.value = learner
            learningJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
            try {
                logI(TAG, "Starting learning session via backend: ${learner.type}")
                learner.startLearning(timeoutSeconds).also { result ->
                    if (result.isSuccess && learner.type in setOf(LearnerType.USB_TIQIAA, LearnerType.USB_ELKSMART) && _usbLearner.value === learner) {
                        _receiveConfirmed.value = true
                    }
                }
            } finally {
                learningJob = null
                _runningLearner.value = null
            }
        }
    }

    /**
     * Cancels any active learning session on the current learner backend.
     */
    fun cancel() {
        val running = _runningLearner.value
        running?.cancel()
        learningJob?.cancel()
        if (activeLearner.value !== running) activeLearner.value.cancel()
    }
}
