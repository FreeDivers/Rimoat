package org.qinyan.rimoat.hardware.learning

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.UsbSessionAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Own the physical connection only while listening; release it before test transmission. */
internal class LazyUsbLearner(
    val deviceName: String,
    override val type: LearnerType,
    private val open: () -> IrLearner?
) : IrLearner, AutoCloseable {
    private val _available = MutableStateFlow(true)
    override val isAvailable = _available.asStateFlow()
    private val _state = MutableStateFlow<LearningState>(LearningState.Idle)
    override val state = _state.asStateFlow()
    private val disconnected = AtomicBoolean(false)
    @Volatile private var session: UsbSessionAccess.LearningSession? = null
    @Volatile private var current: IrLearner? = null

    override suspend fun startLearning(timeoutSeconds: Int): Result<IrSignal.Raw> = withContext(Dispatchers.IO) {
        if (!UsbSessionAccess.mutex.tryLock()) {
            _state.value = LearningState.Error(AppStrings.get(R.string.msg_usb_busy_retry, "USB 正在发射或学习，请稍后重试"))
            return@withContext Result.failure(IllegalStateException(AppStrings.get(R.string.msg_usb_busy, "USB 设备忙")))
        }
        val lease = UsbSessionAccess.beginLearning()
        session = lease
        try {
            currentCoroutineContext().ensureActive()
            if (disconnected.get() || !_available.value) {
                val err = AppStrings.get(R.string.msg_usb_disconnected, "USB 设备已断开")
                _state.value = LearningState.Error(err)
                return@withContext Result.failure(IOException(err))
            }
            val delegate = open() ?: error(AppStrings.get(R.string.msg_usb_receiver_open_failed, "无法打开 USB 接收器，请检查授权或重新插入"))
            current = delegate
            if (disconnected.get() || !_available.value) {
                (delegate as? AutoCloseable)?.close()
                current = null
                val err = AppStrings.get(R.string.msg_usb_disconnected, "USB 设备已断开")
                _state.value = LearningState.Error(err)
                return@withContext Result.failure(IOException(err))
            }
            val observer = launch {
                delegate.state.collect { nextState ->
                    if (!disconnected.get()) _state.value = nextState
                }
            }
            try {
                val result = delegate.startLearning(timeoutSeconds)
                observer.cancel()
                if (disconnected.get() || !_available.value) {
                    val err = AppStrings.get(R.string.msg_usb_disconnected, "USB 设备已断开")
                    _state.value = LearningState.Error(err)
                    Result.failure(IOException(err))
                } else {
                    _state.value = delegate.state.value
                    result
                }
            } finally {
                observer.cancel()
                (delegate as? AutoCloseable)?.close()
                current = null
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            _state.value = LearningState.Error(e.localizedMessage ?: AppStrings.get(R.string.msg_usb_learning_failed, "USB 学习失败"))
            Result.failure(e)
        } finally {
            session = null
            UsbSessionAccess.mutex.unlock()
            UsbSessionAccess.finishLearning(lease)
        }
    }

    override fun cancel() {
        if (disconnected.get()) return
        session?.let(UsbSessionAccess::cancelLearning)
        current?.cancel()
        _state.value = LearningState.Idle
    }

    override fun close() {
        if (disconnected.compareAndSet(false, true)) {
            _available.value = false
            val active = current
            if (active is AutoCloseable) {
                try { active.close() } catch (_: Throwable) {}
            } else {
                active?.cancel()
            }
            _state.value = LearningState.Error(AppStrings.get(R.string.msg_usb_disconnected, "USB 设备已断开"))
        }
    }
}
