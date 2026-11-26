package org.qinyan.rimoat.hardware.learning

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.UsbSessionAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Own the physical connection only while listening; release it before test transmission. */
internal class LazyUsbLearner(
    val deviceName: String,
    override val type: LearnerType,
    private val open: suspend () -> IrLearner?
) : IrLearner, AutoCloseable {
    private val _available = MutableStateFlow(true)
    override val isAvailable = _available.asStateFlow()
    private val _state = MutableStateFlow<LearningState>(LearningState.Idle)
    override val state = _state.asStateFlow()
    private val disconnected = AtomicBoolean(false)
    @Volatile private var session: UsbSessionAccess.LearningSession? = null
    @Volatile private var current: IrLearner? = null
    @Volatile private var learningJob: Job? = null

    private fun disconnectedFailure(): Result<IrSignal.Raw> {
        val err = AppStrings.get(R.string.msg_usb_disconnected, "USB 设备已断开")
        _state.value = LearningState.Error(err)
        return Result.failure(IOException(err))
    }

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
                return@withContext disconnectedFailure()
            }
            supervisorScope {
                val work = async(start = CoroutineStart.LAZY) {
                    var delegate: IrLearner? = null
                    var observer: Job? = null
                    try {
                        currentCoroutineContext().ensureActive()
                        if (disconnected.get() || !_available.value) {
                            return@async disconnectedFailure()
                        }
                        val opened = open() ?: error(AppStrings.get(R.string.msg_usb_receiver_open_failed, "无法打开 USB 接收器，请检查授权或重新插入"))
                        delegate = opened
                        current = opened
                        if (disconnected.get() || !_available.value) {
                            return@async disconnectedFailure()
                        }
                        observer = launch {
                            opened.state.collect { nextState ->
                                if (!disconnected.get()) _state.value = nextState
                            }
                        }
                        val result = opened.startLearning(timeoutSeconds)
                        observer.cancel()
                        if (disconnected.get() || !_available.value) {
                            disconnectedFailure()
                        } else {
                            _state.value = opened.state.value
                            result
                        }
                    } finally {
                        observer?.cancel()
                        current = null
                        if (delegate is AutoCloseable) {
                            try { delegate.close() } catch (_: Throwable) {}
                        }
                    }
                }
                learningJob = work
                if (disconnected.get() || !_available.value) {
                    work.cancel()
                    return@supervisorScope disconnectedFailure()
                }
                work.start()
                try {
                    work.await()
                } finally {
                    learningJob = null
                    withContext(NonCancellable) { work.cancelAndJoin() }
                }
            }
        } catch (e: CancellationException) {
            if (disconnected.get() && currentCoroutineContext().isActive) {
                disconnectedFailure()
            } else {
                throw e
            }
        } catch (e: Exception) {
            if (disconnected.get()) {
                disconnectedFailure()
            } else {
                _state.value = LearningState.Error(e.localizedMessage ?: AppStrings.get(R.string.msg_usb_learning_failed, "USB 学习失败"))
                Result.failure(e)
            }
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

    /** Marks the device detached and cancels active learning; the learning thread owns connection release/close. */
    fun markDisconnected() {
        if (disconnected.compareAndSet(false, true)) {
            _available.value = false
            session?.let(UsbSessionAccess::cancelLearning)
            current?.cancel()
            learningJob?.cancel()
            _state.value = LearningState.Error(AppStrings.get(R.string.msg_usb_disconnected, "USB 设备已断开"))
        }
    }

    override fun close() {
        markDisconnected()
    }
}
