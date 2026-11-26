package org.qinyan.rimoat.data

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.UiTextException
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.TransmissionReceipt
import org.qinyan.rimoat.hardware.TransmitterType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RemoteCommandRecord(
    val label: String = "",
    val sentAt: Long = 0,
    val simulated: Boolean = false,
    val demoSettings: AcSettings? = null,
    val demoBase: AcSettings? = null,
    val toggles: Map<String, Int> = emptyMap(),
    val buttonId: String? = null
)

/** Local operation receipts, separate from authoritative remote profiles. No scheduled sends. */
class RemoteCommandJournal(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _records = MutableStateFlow<Map<String, RemoteCommandRecord>>(emptyMap())
    val records = _records.asStateFlow()
    @Volatile private var loaded = false
    private val mutex = Mutex()

    suspend fun load() {
        if (loaded) return
        withContext(Dispatchers.IO) { mutex.withLock {
            loadLocked()
        } }
    }

    private fun loadLocked() {
        if (!loaded) {
            _records.value = if (file.exists()) {
                val content = file.readText()
                try {
                    json.decodeFromString<Map<String, RemoteCommandRecord>>(content)
                } catch (e: IllegalArgumentException) {
                    // Invalid map entries can throw IllegalArgumentException instead of SerializationException.
                    val backup = File(file.parentFile, "remote-commands-corrupt-${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
                    AtomicLocalFile.write(backup, content)
                    val recovered = emptyMap<String, RemoteCommandRecord>()
                    AtomicLocalFile.write(file, json.encodeToString(recovered))
                    recovered
                }
            } else emptyMap()
            loaded = true
        }
    }

    suspend fun record(id: String, value: RemoteCommandRecord) = withContext(Dispatchers.IO) { mutex.withLock {
        loadLocked()
        val next = _records.value + (id to value)
        AtomicLocalFile.write(file, json.encodeToString(next))
        _records.value = next
    } }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) { mutex.withLock {
        loadLocked()
        val next = _records.value - id
        AtomicLocalFile.write(file, json.encodeToString(next))
        _records.value = next
    } }
}

data class RemoteCommandResult(
    val simulated: Boolean,
    val label: String,
    val settings: AcSettings? = null,
    val warning: UiText? = null
)

/** Shared by foreground controls and widgets. The lock covers read, encode, send AND commit. */
class RemoteCommandExecutor(
    private val readProfile: suspend (String) -> RemoteProfile?,
    private val commitSettings: suspend (String, AcSettings) -> Unit,
    private val encoder: AcSignalEncoder,
    private val route: () -> TransmitterType,
    private val transmit: suspend (List<IrSignal>, String?, TransmitterType, suspend () -> Unit) -> TransmissionReceipt,
    val journal: RemoteCommandJournal
) {
    private val mutex = Mutex()

    suspend fun executeButton(
        remoteId: String,
        buttonId: String,
        owner: String? = null,
        rejectIfBusy: Boolean = false,
        beforeTransmission: suspend () -> Unit = {}
    ): RemoteCommandResult? = locked(rejectIfBusy) {
        journal.load()
        val profile = readProfile(remoteId) ?: failWith(R.string.msg_remote_removed)
        val button = profile.buttons.firstOrNull { it.id == buttonId } ?: failWith(R.string.msg_button_removed)
        if (button.acAction != null && !button.isLearned && !profile.useStaticAcButtons) {
            changeAcLocked(profile, button.label, owner, {
                requireState(readProfile(remoteId)?.buttons?.firstOrNull { it.id == buttonId } == button, R.string.msg_button_changed)
                beforeTransmission()
            }) { state, protocol ->
                val action = button.acAction
                val next = if (action == AcAction.MODE) {
                    val modes = encoder.supportedModes(protocol)
                    requireState(modes.isNotEmpty(), R.string.msg_no_modes)
                    state.copy(mode = modes[(modes.indexOf(state.mode) + 1) % modes.size], powerOn = true)
                } else state.apply(action)
                next to action
            }
        } else {
            requireState(button.hasSignal, R.string.msg_button_no_signal)
            val selectedRoute = route()
            val old = journal.records.value[remoteId] ?: RemoteCommandRecord()
            val key = "${selectedRoute == TransmitterType.MOCK}:${button.id}"
            val index = old.toggles[key] ?: 0
            val receipt = transmit(listOf(button.toIrSignal(index)), owner, selectedRoute) {
                val latest = readProfile(remoteId)
                requireState(
                    latest != null && latest.buttons.firstOrNull { it.id == buttonId } == button &&
                        latest.useStaticAcButtons == profile.useStaticAcButtons,
                    R.string.msg_remote_changed
                )
                beforeTransmission()
            }
            var warning: UiText? = null
            withContext(NonCancellable) {
                try {
                    journal.record(remoteId, old.copy(label = button.label, sentAt = System.currentTimeMillis(),
                        simulated = receipt.simulated, buttonId = button.id,
                        toggles = old.toggles + (key to ((index + 1) and 1))))
                } catch (e: Exception) { warning = UiText.Resource(R.string.msg_sent_record_failed) }
            }
            RemoteCommandResult(receipt.simulated, button.label, warning = warning)
        }
    }

    suspend fun changeAc(
        remoteId: String,
        label: String = "状态",
        owner: String? = null,
        rejectIfBusy: Boolean = false,
        beforeTransmission: suspend () -> Unit = {},
        transform: suspend (AcSettings, Int) -> Pair<AcSettings, AcAction>?
    ): RemoteCommandResult? = locked(rejectIfBusy) {
        journal.load()
        val profile = readProfile(remoteId) ?: failWith(R.string.msg_remote_removed)
        changeAcLocked(profile, label, owner, beforeTransmission, transform)
    }

    private suspend fun changeAcLocked(
        profile: RemoteProfile,
        label: String,
        owner: String?,
        beforeTransmission: suspend () -> Unit,
        transform: suspend (AcSettings, Int) -> Pair<AcSettings, AcAction>?
    ): RemoteCommandResult? {
        requireState(!profile.useStaticAcButtons, R.string.msg_use_learned_buttons)
        val protocol = profile.acProtocolId ?: failWith(R.string.msg_remote_no_state_control)
        val selectedRoute = route()
        val old = journal.records.value[profile.id] ?: RemoteCommandRecord()
        val base = if (selectedRoute == TransmitterType.MOCK && old.demoBase == profile.acSettings)
            old.demoSettings ?: profile.acSettings else profile.acSettings
        val (transformed, action) = transform(base, protocol) ?: return null
        val next = if (action == AcAction.TIMER) transformed else transformed.copy(timerMinutes = 0)
        requireState(action != AcAction.TIMER || AcTimerOptions.supports(protocol, next), R.string.msg_timer_unsupported)
        if (next.powerOn) requireState(next.mode in encoder.supportedModes(protocol), R.string.msg_mode_unsupported)
        requireState(encoder.supportsAction(protocol, next, action), R.string.msg_action_unsupported)
        val signals = encoder.encode(protocol, next, action)
        requireState(signals.isNotEmpty(), R.string.msg_ac_no_signals)
        val receipt = transmit(signals, owner, selectedRoute) {
            val latest = readProfile(profile.id)
            requireState(
                latest != null && latest.acProtocolId == protocol && !latest.useStaticAcButtons &&
                    latest.acSettings == profile.acSettings,
                R.string.msg_remote_changed
            )
            beforeTransmission()
        }
        val committed = next.copy(counter = (next.counter + signals.size) and 1)
        var warning: UiText? = null
        withContext(NonCancellable) {
            if (!receipt.simulated) {
                try { commitSettings(profile.id, committed) }
                catch (e: Exception) { warning = UiText.Resource(R.string.msg_sent_settings_failed) }
            }
            try {
                journal.record(profile.id, old.copy(label = if (label == "状态") action.label else label,
                    sentAt = System.currentTimeMillis(), simulated = receipt.simulated, buttonId = null,
                    demoSettings = if (receipt.simulated) committed else null,
                    demoBase = if (receipt.simulated) profile.acSettings else null))
            } catch (e: Exception) { warning = warning ?: UiText.Resource(R.string.msg_sent_record_failed) }
        }
        return RemoteCommandResult(receipt.simulated, action.label, committed, warning)
    }

    private suspend fun <T> locked(rejectIfBusy: Boolean, block: suspend () -> T): T {
        if (rejectIfBusy) requireState(mutex.tryLock(), R.string.msg_sending_busy) else mutex.lock()
        return try { block() } finally { mutex.unlock() }
    }

    private fun failWith(@androidx.annotation.StringRes messageRes: Int): Nothing =
        throw UiTextException(UiText.Resource(messageRes))

    private fun requireState(condition: Boolean, @androidx.annotation.StringRes messageRes: Int) {
        if (!condition) failWith(messageRes)
    }
}
