package org.qinyan.rimoat.hardware.learning

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import android.content.Context
import android.os.Build
import android.util.Log
import org.qinyan.rimoat.hardware.IrSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Built-in IR learning backend for devices with a compatible OEM learning service.
 * Uses reflection to access the service without compile-time SDK dependencies.
 */
class HuaweiIrLearner internal constructor(
    private val manager: Any,
    private val clazz: Class<*>
) : IrLearner {

    companion object {
        private const val TAG = "HuaweiIrLearner"
        private const val CLASS_NAME = "android.irself.IrSelfLearningManager"
        private const val POLL_INTERVAL_MS = 500L
        private const val MIN_PULSES = 6

        private fun logW(tag: String, msg: String) {
            try { Log.w(tag, msg) } catch (_: Throwable) {}
        }

        private fun logE(tag: String, msg: String, t: Throwable? = null) {
            try { Log.e(tag, msg, t) } catch (_: Throwable) {}
        }

        @Volatile
        private var supportedCache: Boolean? = null

        /**
         * Checks the device manufacturer and availability of a compatible IR learning service.
         * Safe to call on any thread; cached after initial probe.
         */
        fun isSupported(context: Context): Boolean {
            supportedCache?.let { return it }
            val result = probe(context)
            supportedCache = result
            return result
        }

        private fun probe(context: Context): Boolean {
            val mfr = try {
                Build.MANUFACTURER?.lowercase() ?: ""
            } catch (_: Throwable) {
                ""
            }
            if (mfr != "huawei" && mfr != "honor") return false
            if (!readIrdaLearningSupportProp()) return false

            return try {
                val clazz = Class.forName(CLASS_NAME)
                obtainInstance(context, clazz) != null
            } catch (_: Throwable) {
                false
            }
        }

        private fun readIrdaLearningSupportProp(): Boolean {
            try {
                val spx = Class.forName("com.huawei.android.os.SystemPropertiesEx")
                val get = spx.getMethod("getBoolean", String::class.java, Boolean::class.java)
                return get.invoke(null, "hw_mc.irda_learning_support", true) as? Boolean ?: true
            } catch (_: Throwable) {
            }

            return try {
                val sp = Class.forName("android.os.SystemProperties")
                val get = sp.getMethod("getBoolean", String::class.java, Boolean::class.java)
                get.invoke(null, "hw_mc.irda_learning_support", true) as? Boolean ?: true
            } catch (_: Throwable) {
                true // default conservative fallback
            }
        }

        fun open(context: Context): HuaweiIrLearner? {
            return try {
                val clazz = Class.forName(CLASS_NAME)
                val instance = obtainInstance(context, clazz) ?: return null
                val initResult = clazz.getMethod("deviceInit").invoke(instance)
                if (initResult == false) {
                    logW(TAG, "IrSelfLearningManager.deviceInit() returned false")
                    return null
                }
                HuaweiIrLearner(instance, clazz)
            } catch (t: Throwable) {
                logW(TAG, "Failed to open the built-in IR learner: ${t.message}")
                null
            }
        }

        private fun obtainInstance(context: Context, clazz: Class<*>): Any? {
            // Strategy 1: getDefault() static factory (primary ROM path)
            try {
                val instance = clazz.getMethod("getDefault").invoke(null)
                if (instance != null) return instance
            } catch (_: Throwable) {
            }

            // Strategy 2: System service named "irself"
            try {
                val svc = context.getSystemService("irself")
                if (svc != null && clazz.isInstance(svc)) return svc
            } catch (_: Throwable) {
            }

            // Strategy 3: Direct constructor
            return try {
                clazz.getDeclaredConstructor().newInstance()
            } catch (_: Throwable) {
                null
            }
        }
    }

    override val type: LearnerType = LearnerType.HUAWEI_OEM

    private val _isAvailable = MutableStateFlow(true)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _state = MutableStateFlow<LearningState>(LearningState.Idle)
    override val state: StateFlow<LearningState> = _state.asStateFlow()

    private val cancelled = AtomicBoolean(false)

    override suspend fun startLearning(timeoutSeconds: Int): Result<IrSignal.Raw> = withContext(Dispatchers.IO) {
        cancelled.set(false)
        val totalSec = timeoutSeconds.coerceAtLeast(1)
        _state.value = LearningState.Listening(remainingSeconds = totalSec, totalSeconds = totalSec)

        val startLearning: java.lang.reflect.Method
        val stopLearning: java.lang.reflect.Method
        val getLearningStatus: java.lang.reflect.Method
        val readIRFrequency: java.lang.reflect.Method
        val readIRCode: java.lang.reflect.Method
        try {
            startLearning = clazz.getMethod("startLearning")
            stopLearning = clazz.getMethod("stopLearning")
            getLearningStatus = clazz.getMethod("getLearningStatus")
            readIRFrequency = clazz.getMethod("readIRFrequency")
            readIRCode = clazz.getMethod("readIRCode")
        } catch (t: Throwable) {
            val err = AppStrings.get(R.string.msg_huawei_learning_unavailable, "Huawei IR 学习接口不可用：%1\$s", (t.message ?: t.javaClass.simpleName).toString())
            _state.value = LearningState.Error(err, t)
            return@withContext Result.failure(IllegalStateException(err, t))
        }

        val started = try {
            startLearning.invoke(manager) as? Boolean ?: false
        } catch (t: Throwable) {
            logE(TAG, "Error invoking startLearning()", t)
            false
        }

        if (!started) {
            val err = AppStrings.get(R.string.msg_huawei_learn_start_failed, "Huawei IR hardware failed to enter learning mode")
            _state.value = LearningState.Error(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        val deadline = System.currentTimeMillis() + totalSec * 1000L
        var lastRemaining = totalSec

        try {
            while (!cancelled.get() && System.currentTimeMillis() < deadline) {
                val currentRemaining = ((deadline - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)
                if (currentRemaining != lastRemaining) {
                    lastRemaining = currentRemaining
                    _state.value = LearningState.Listening(remainingSeconds = currentRemaining, totalSeconds = totalSec)
                }

                delay(POLL_INTERVAL_MS)
                if (cancelled.get()) break

                val ready = try {
                    getLearningStatus.invoke(manager) as? Boolean ?: false
                } catch (t: Throwable) {
                    logW(TAG, "Error polling getLearningStatus(): ${t.message}")
                    false
                }

                if (!ready) continue

                // STRICT ROM ORDER REQUIREMENT: readIRFrequency MUST be called before readIRCode
                val freqAndCode = try {
                    // The OEM requires frequency to be read before the code.
                    val freq = (readIRFrequency.invoke(manager) as? Number)?.toInt() ?: 38000
                    val code = readIRCode.invoke(manager) as? IntArray
                    freq to code
                } catch (t: Throwable) {
                    val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
                    val err = AppStrings.get(R.string.msg_huawei_learning_read_failed, "读取华为红外学习结果失败：%1\$s", (cause.message ?: cause.javaClass.simpleName).toString())
                    logE(TAG, err, cause)
                    _state.value = LearningState.Error(err, cause)
                    return@withContext Result.failure(IllegalStateException(err, cause))
                }
                val freq = freqAndCode.first
                val code = freqAndCode.second

                if (code == null || code.size < MIN_PULSES) {
                    logW(TAG, "Invalid code received (size=${code?.size}); resuming poll")
                    continue
                }

                val sanitizeResult = IrSignalSanitizer.sanitize(freq, code)
                if (sanitizeResult.isSuccess) {
                    val signal = sanitizeResult.getOrThrow()
                    _state.value = LearningState.Captured(signal)
                    return@withContext Result.success(signal)
                }
            }

            if (cancelled.get()) {
                _state.value = LearningState.Idle
                return@withContext Result.failure(Exception("IR learning cancelled by user"))
            }

            _state.value = LearningState.Timeout
            return@withContext Result.failure(LearningTimeoutException("IR learning timed out after $totalSec seconds"))
        } finally {
            try {
                stopLearning.invoke(manager)
            } catch (t: Throwable) {
                logW(TAG, "Error invoking stopLearning(): ${t.message}")
            }
        }
    }

    override fun cancel() {
        cancelled.set(true)
        _state.value = LearningState.Idle
        // The IO learning coroutine owns stopLearning in its finally block.
    }
}
