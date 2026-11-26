package org.qinyan.rimoat.widget

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.AppLanguage
import org.qinyan.rimoat.i18n.UiText

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.res.Configuration
import org.qinyan.rimoat.IrRemoteApplication
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.TransmitterType
import androidx.glance.appwidget.updateAll
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WidgetFeedback(val text: String, val error: Boolean = false, val busy: Boolean = false,
                          val shortcut: WidgetShortcut? = null)
data class WidgetSnapshot(
    val loaded: Boolean = false,
    val profiles: List<RemoteProfile> = emptyList(),
    val configurations: Map<Int, WidgetConfiguration> = emptyMap(),
    val commands: Map<String, List<WidgetCommand>> = emptyMap(),
    val records: Map<String, RemoteCommandRecord> = emptyMap(),
    val simulated: Boolean = false,
    val dynamicColor: Boolean = false,
    val colorMode: AppColorMode = AppColorMode.SYSTEM,
    val fontScale: Float = 1f,
    val isRtl: Boolean = false,
    val error: UiText? = null,
    val localeTags: String = ""
)

internal fun WidgetSnapshot.withRepositoryError(error: UiText?): WidgetSnapshot =
    copy(error = error.takeIf { profiles.isEmpty() })

internal fun widgetRelevantProfilesEqual(old: List<RemoteProfile>, new: List<RemoteProfile>): Boolean =
    old.size == new.size && old.indices.all { index ->
        old[index].copy(updatedAt = 0L, lastUsedAt = 0L, lastSentAt = 0L) ==
            new[index].copy(updatedAt = 0L, lastUsedAt = 0L, lastSentAt = 0L)
    }

/** Application-scoped observations wake widgets on changes; no periodic refresh work. */
class WidgetRuntime(private val app: IrRemoteApplication) {
    private data class CommandCacheKey(
        val id: String,
        val category: DeviceCategory,
        val buttons: List<RemoteButton>,
        val acProtocolId: Int?,
        val useStaticAcButtons: Boolean,
        val actions: Set<AcAction>
    )

    val store = WidgetStore(File(app.filesDir, "widgets.json"))
    private val _state = MutableStateFlow(WidgetSnapshot())
    private val configuration = MutableStateFlow(Configuration(AppLanguage.wrap(app).resources.configuration))
    val state = _state.asStateFlow()
    private val _feedback = MutableStateFlow<Map<Int, WidgetFeedback>>(emptyMap())
    val feedback = _feedback.asStateFlow()
    private val ready = CompletableDeferred<Unit>()
    private val actionMutex = Mutex()
    private val observationLock = Any()
    private val observationMutex = Mutex()
    private var observation: Job? = null
    private val actions = mutableMapOf<Int, Set<AcAction>>()
    private val cachedCommands = mutableMapOf<String, Pair<CommandCacheKey, List<WidgetCommand>>>()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start() {
        synchronized(observationLock) {
            observation?.cancel()
            val next = app.applicationScope.launch(start = CoroutineStart.LAZY) {
                observationMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    try {
                        WidgetPickerPreviews.synchronize(app)
                        app.repository.awaitLoaded()
                        store.load()
                        app.commands.journal.load()
                        val relevantProfiles = app.repository.remotes.distinctUntilChanged(::widgetRelevantProfilesEqual)
                        val appearance = combine(app.themePreferences.useDynamicColor, app.themePreferences.colorMode) { dynamic, mode ->
                            dynamic to mode
                        }
                        combine(relevantProfiles, store.configurations, app.commands.journal.records,
                            appearance, app.transmitterManager.transmitterType) { profiles, configurations, records, colors, type ->
                            WidgetSnapshot(true, profiles, configurations, records = records,
                                simulated = type == TransmitterType.MOCK, dynamicColor = colors.first, colorMode = colors.second,
                                error = if (profiles.isEmpty()) app.repository.errorMessage.value else null)
                        }.combine(app.repository.errorMessage) { snapshot, error -> snapshot.withRepositoryError(error) }
                        .mapLatest { snapshot ->
                            val currentIds = snapshot.profiles.mapTo(HashSet()) { it.id }
                            cachedCommands.keys.retainAll(currentIds)
                            snapshot.copy(commands = snapshot.profiles.associate { profile ->
                                currentCoroutineContext().ensureActive()
                                val availableActions = if (profile.acProtocolId != null && !profile.useStaticAcButtons) {
                                    try { actions[profile.acProtocolId] ?: app.acSignals.availableActions(profile.acProtocolId).also { actions[profile.acProtocolId] = it } }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { emptySet() }
                                } else emptySet()
                                val key = CommandCacheKey(
                                    profile.id, profile.category, profile.buttons,
                                    profile.acProtocolId, profile.useStaticAcButtons, availableActions
                                )
                                val existing = cachedCommands[profile.id]
                                val choices = if (existing != null && existing.first == key) {
                                    existing.second
                                } else {
                                    WidgetCommands.choices(profile, availableActions).also {
                                        cachedCommands[profile.id] = key to it
                                    }
                                }
                                profile.id to choices
                            })
                        }.combine(configuration) { snapshot, current ->
                            snapshot.copy(fontScale = current.fontScale,
                                isRtl = current.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL,
                                localeTags = current.locales.toLanguageTags())
                        }.collect { snapshot ->
                            _state.value = snapshot
                            ready.complete(Unit)
                            updateAll()
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        _state.value = WidgetSnapshot(loaded = true, error = UiText.Resource(R.string.widget_config_read_failed))
                        ready.complete(Unit)
                        android.util.Log.e("RimoatWidget", "Widget initialization failed", e)
                        updateAll()
                    }
                }
            }
            observation = next
            next.start()
        }
    }

    suspend fun awaitReady() { ready.await() }

    suspend fun configurationChanged() {
        WidgetPickerPreviews.synchronize(app)
        configuration.value = Configuration(AppLanguage.wrap(app).resources.configuration)
    }

    suspend fun retry() {
        app.repository.loadAllRemotes()
        start()
    }

    suspend fun save(id: Int, configuration: WidgetConfiguration) {
        store.save(id, configuration)
        _state.update { it.copy(configurations = store.configurations.value) }
        updateAll()
    }

    suspend fun updateAll() {
        val manager = runCatching { AppWidgetManager.getInstance(app) }.getOrNull()
        for (entry in WidgetPickerPreviews.entries) {
            val hasBoundInstances = if (manager != null) {
                try {
                    manager.getAppWidgetIds(ComponentName(app, entry.receiver)).isNotEmpty()
                } catch (_: RuntimeException) {
                    true
                }
            } else true
            if (!hasBoundInstances) continue
            try { entry.widget.updateAll(app) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.e("RimoatWidget", "Widget update failed", e) }
        }
    }

    suspend fun perform(widgetId: Int, shortcut: WidgetShortcut) {
        awaitReady()
        if (!actionMutex.tryLock()) {
            // Keep the in-flight disabled state; never queue an unbounded number of IR commands.
            return
        }
        try {
            val configuration = store.configurations.value[widgetId] ?: error(AppStrings.get(R.string.widget_select_remote_first, "请先选择遥控器"))
            check(shortcut in configuration.shortcuts) { AppStrings.get(R.string.widget_shortcut_changed, "快捷键已改变，请重新操作") }
            val profile = app.repository.getRemote(shortcut.remoteId) ?: error(AppStrings.get(R.string.widget_remote_removed, "遥控器已移除"))
            check(state.value.commands[profile.id].orEmpty().any { it.id == shortcut.buttonId }) { AppStrings.get(R.string.widget_button_unavailable, "按键不可用，请重新选择") }
            _feedback.update { it + (widgetId to WidgetFeedback(AppStrings.get(R.string.widget_sending, "发送中"), busy = true)) }
            updateAll()
            val validateBinding: suspend () -> Unit = {
                check(shortcut in store.configurations.value[widgetId]?.shortcuts.orEmpty()) { AppStrings.get(R.string.widget_shortcut_changed, "快捷键已改变，请重新操作") }
            }
            val result = if (shortcut.buttonId.startsWith(WidgetCommands.PREFIX)) {
                app.commands.changeAc(profile.id, owner = "widget:$widgetId", rejectIfBusy = true,
                    beforeTransmission = validateBinding) { base, protocol ->
                    if (shortcut.buttonId == WidgetCommands.POWER) {
                        val action = if (base.powerOn) AcAction.POWER_OFF else AcAction.POWER_ON
                        base.apply(action) to action
                    } else {
                        val action = AcAction.valueOf(shortcut.buttonId.removePrefix(WidgetCommands.PREFIX))
                        if ((action == AcAction.TEMP_UP && base.temperature >= 30) ||
                            (action == AcAction.TEMP_DOWN && base.temperature <= 16)) return@changeAc null
                        val next = if (action == AcAction.MODE) {
                            val modes = app.acSignals.supportedModes(protocol)
                            check(modes.isNotEmpty()) { AppStrings.get(R.string.widget_no_modes, "没有可用模式") }
                            base.copy(mode = modes[(modes.indexOf(base.mode) + 1) % modes.size], powerOn = true)
                        } else base.apply(action)
                        next to action
                    }
                }
            } else app.commands.executeButton(profile.id, shortcut.buttonId, "widget:$widgetId", rejectIfBusy = true,
                beforeTransmission = validateBinding)
            _feedback.update {
                if (result?.warning != null) it + (widgetId to WidgetFeedback(result.warning, error = true, shortcut = shortcut))
                else it - widgetId
            }
        } catch (e: CancellationException) {
            _feedback.update { it + (widgetId to WidgetFeedback(AppStrings.get(R.string.widget_interrupted, "操作已中断"), error = true, shortcut = shortcut)) }
            throw e
        }
        catch (e: Exception) {
            _feedback.update { it + (widgetId to WidgetFeedback(e.message ?: AppStrings.get(R.string.widget_send_failed, "发送失败，请重试"), error = true, shortcut = shortcut)) }
        } finally {
            actionMutex.unlock()
            updateAll()
        }
    }
}
