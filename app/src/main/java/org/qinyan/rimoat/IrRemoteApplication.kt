package org.qinyan.rimoat

import android.app.Application
import org.qinyan.rimoat.data.RemoteRepository
import org.qinyan.rimoat.data.AcSignalStore
import org.qinyan.rimoat.data.ThemePreferences
import org.qinyan.rimoat.data.db.IrDatabaseRepository
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.ui.ComposeCompatibility
import org.qinyan.rimoat.ui.learning.WizardDrafts
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class IrRemoteApplication : Application() {

    lateinit var repository: RemoteRepository
        private set

    lateinit var transmitterManager: IrTransmitterManager
        private set

    lateinit var themePreferences: ThemePreferences
        private set

    lateinit var irDatabaseRepository: IrDatabaseRepository
        private set

    lateinit var learnerManager: IrLearnerManager
        private set

    lateinit var acSignals: AcSignalStore
        private set

    lateinit var commands: org.qinyan.rimoat.data.RemoteCommandExecutor
        private set
    lateinit var widgets: org.qinyan.rimoat.widget.WidgetRuntime
        private set
    lateinit var shortcuts: org.qinyan.rimoat.data.RemoteShortcutManager
        private set
    @Volatile internal var coldLaunchTimestampUptimeMs: Long = 0L
        private set
    private val shortcutMutex = Mutex()
    private var lastSyncedShortcutSignature: List<Triple<String, String, Boolean>>? = null
    private suspend fun syncCurrentShortcuts(forcePlatformCheck: Boolean = false) {
        repository.awaitLoaded()
        val profiles = repository.remotes.value
        val signature = profiles
            .sortedWith(compareByDescending<org.qinyan.rimoat.data.RemoteProfile> { it.isFavorite }.thenByDescending { it.lastUsedAt })
            .map { Triple(it.id, it.name, it.isFavorite) }
        shortcutMutex.withLock {
            if (!forcePlatformCheck && signature == lastSyncedShortcutSignature) return@withLock
            shortcuts.sync(profiles)
                .onSuccess { lastSyncedShortcutSignature = signature }
                .onFailure { android.util.Log.w("RimoatShortcuts", "Shortcut sync deferred", it) }
        }
    }
    fun refreshShortcuts() {
        if (::shortcuts.isInitialized) applicationScope.launch {
            syncCurrentShortcuts(forcePlatformCheck = true)
        }
    }
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshShortcuts()
        if (::widgets.isInitialized) applicationScope.launch { widgets.configurationChanged() }
    }

    override fun onCreate() {
        ComposeCompatibility.initialize()
        super.onCreate()
        instance = this
        coldLaunchTimestampUptimeMs = android.os.SystemClock.uptimeMillis()
        org.qinyan.rimoat.i18n.AppLanguage.initialize(this)
        org.qinyan.rimoat.i18n.AppStrings.initialize(this)
        themePreferences = ThemePreferences(this)
        transmitterManager = IrTransmitterManager(this)
        learnerManager = IrLearnerManager(this)
        acSignals = AcSignalStore(this)
        irDatabaseRepository = IrDatabaseRepository(this, acSignals)
        repository = RemoteRepository(this, acSignals = acSignals, upgradeCatalog = irDatabaseRepository::upgradeProfile)
        commands = org.qinyan.rimoat.data.RemoteCommandExecutor(
            readProfile = { id -> repository.awaitLoaded(); repository.getRemote(id) },
            commitSettings = repository::updateAcSettings,
            encoder = acSignals,
            route = transmitterManager::currentTransmitterTypeAfterCheck,
            transmit = { signals, owner, route, before ->
                transmitterManager.transmitWithReceipt(signals, owner, route, before).getOrThrow()
            },
            journal = org.qinyan.rimoat.data.RemoteCommandJournal(java.io.File(filesDir, "remote-commands.json"))
        )
        shortcuts = org.qinyan.rimoat.data.RemoteShortcutManager(this)
        applicationScope.launch { WizardDrafts.pruneExpired(filesDir) }
        applicationScope.launch {
            repository.awaitLoaded()
            repository.remotes.collect { syncCurrentShortcuts() }
        }
        widgets = org.qinyan.rimoat.widget.WidgetRuntime(this)
        widgets.start()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { java.io.File(filesDir, "pairing-progress.json").delete() }
            try {
                acSignals.compactExistingCache()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("RimoatCatalog", "Cache compaction deferred", e) }
        }
    }

    companion object {
        lateinit var instance: IrRemoteApplication
            private set
    }
}
