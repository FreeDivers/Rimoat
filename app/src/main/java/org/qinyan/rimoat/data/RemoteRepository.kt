package org.qinyan.rimoat.data

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppError
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.UiTextException
import org.qinyan.rimoat.i18n.checkUiText
import org.qinyan.rimoat.i18n.requireUiText
import org.qinyan.rimoat.i18n.toAppError
import org.qinyan.rimoat.i18n.wrapped
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.UUID

data class DeletedRemoteEntry(val entryId: String, val profile: RemoteProfile, val deletedAt: Long)

class RemoteRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val acSignals: AcSignalEncoder? = null,
    private val upgradeCatalog: (suspend (RemoteProfile) -> RemoteProfile)? = null
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    internal val appFilesDir: File
        get() = context.filesDir

    private val storageDir: File by lazy {
        File(context.filesDir, "remotes").apply {
            if (!exists()) mkdirs()
        }
    }

    private val mutex = Mutex()
    private val _remotes = MutableStateFlow<List<RemoteProfile>>(emptyList())
    val remotes: StateFlow<List<RemoteProfile>> = _remotes.asStateFlow()

    private val _errorAppMessage = MutableStateFlow<AppError?>(null)
    val errorAppMessage: StateFlow<AppError?> = _errorAppMessage.asStateFlow()
    private val _errorMessage = MutableStateFlow<UiText?>(null)
    val errorMessage: StateFlow<UiText?> = _errorMessage.asStateFlow()

    private fun setRepositoryError(error: AppError?) {
        _errorAppMessage.value = error
        _errorMessage.value = error?.message
    }

    fun clearError() { setRepositoryError(null) }

    private val _rooms = MutableStateFlow<List<String>>(emptyList())
    val rooms: StateFlow<List<String>> = _rooms.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val initialLoad = kotlinx.coroutines.CompletableDeferred<Unit>()
    suspend fun awaitLoaded() { initialLoad.await() }

    init {
        scope.launch {
            try {
                loadAllRemotes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setRepositoryError(
                    e.toAppError(R.string.msg_storage_unavailable).wrapped(R.string.msg_remote_load_failed)
                )
            } finally {
                _isLoaded.value = true
                initialLoad.complete(Unit)
            }
        }
    }

    suspend fun loadAllRemotes(): List<RemoteProfile> = withContext(Dispatchers.IO) {
        mutex.withLock {
            setRepositoryError(null)
            try {
                recoverRoomMutation()
                recoverPendingImport()
                val list = mutableListOf<RemoteProfile>()
                val files = storageDir.listFiles { _, name -> name.endsWith(".json") }

                    for (file in files.orEmpty()) {
                        try {
                            val content = file.readText()
                            val profile = json.decodeFromString(RemoteProfile.serializer(), content)
                            requireUiText(profile.id == file.nameWithoutExtension, R.string.msg_remote_file_id_mismatch)
                            var upgraded = LegacyRemoteMigration.normalizeKeys(LegacyRemoteMigration.upgradeLegacyAc(profile))
                            RemoteBackupCodec.validate(upgraded)
                            try {
                                val candidate = upgradeCatalog?.invoke(upgraded) ?: upgraded
                                if (candidate != upgraded) RemoteBackupCodec.validate(candidate)
                                upgraded = candidate
                            } catch (e: CancellationException) { throw e
                            } catch (_: Exception) {
                                setRepositoryError(AppError.Resource(UiText.resource(R.string.msg_button_upgrade_failed, profile.name)))
                            }
                            if (upgraded.category == DeviceCategory.AC && !upgraded.useStaticAcButtons && upgraded.acProtocolId == null) {
                                val source = upgraded.sourceRemoteId ?: upgraded.buttons.firstNotNullOfOrNull {
                                    Regex("^(R_air_\\d+_\\d+)_").find(it.id)?.groupValues?.get(1)
                                }
                                if (source != null && acSignals != null) upgraded = upgraded.copy(
                                    sourceRemoteId = source, acProtocolId = acSignals.protocolFor(source))
                            }
                            if (upgraded != profile) {
                                try { AtomicLocalFile.write(file, json.encodeToString(RemoteProfile.serializer(), upgraded)) }
                                catch (_: Exception) {
                                    setRepositoryError(AppError.Resource(UiText.Resource(R.string.msg_configuration_upgrade_unsaved)))
                                }
                            }
                            list.add(upgraded)
                        } catch (e: CancellationException) { throw e
                        } catch (_: Exception) {
                            setRepositoryError(AppError.Resource(UiText.resource(R.string.msg_remote_file_unreadable, file.name)))
                        }
                    }


                // Sort by favorite first, then updatedAt descending
                val sorted = list.sortedWith(
                    compareByDescending<RemoteProfile> { it.isFavorite }
                        .thenByDescending { it.updatedAt }
                )
                _remotes.value = sorted
                _isLoaded.value = true
                refreshRooms()
                sorted
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                setRepositoryError(
                    e.toAppError(R.string.msg_storage_unavailable).wrapped(R.string.msg_remote_load_failed)
                )
                throw e
            }
        }
    }

    fun getRemoteFlow(id: String) = _remotes.map { list ->
        list.find { it.id == id }
    }

    suspend fun getRemote(id: String): RemoteProfile? = _remotes.value.find { it.id == id }

    private fun saveRemoteLocked(profile: RemoteProfile) {
        RemoteBackupCodec.validate(profile)
        val names = (savedRooms() + _remotes.value.map { it.room } + profile.room)
            .filter { it.isNotBlank() }.distinct().sorted()
        AtomicLocalFile.write(roomsFile, json.encodeToString(names))
        val file = File(storageDir, "${profile.id}.json")
        val updated = LegacyRemoteMigration.normalizeKeys(profile).copy(updatedAt = System.currentTimeMillis())
        AtomicLocalFile.write(file, json.encodeToString(RemoteProfile.serializer(), updated))

        val current = _remotes.value.filter { it.id != profile.id }.toMutableList()
        current.add(updated)
        _remotes.value = current.sortedWith(
            compareByDescending<RemoteProfile> { it.isFavorite }
                .thenByDescending { it.updatedAt }
        )
        refreshRooms()
    }

    suspend fun saveRemote(profile: RemoteProfile) = withContext(Dispatchers.IO) {
        mutex.withLock {
            recoverRoomMutation()
            saveRemoteLocked(profile)
        }
    }

    suspend fun updateButton(remoteId: String, updatedButton: RemoteButton) = withContext(Dispatchers.IO) {
        mutex.withLock {
            recoverRoomMutation()
            val current = _remotes.value.find { it.id == remoteId } ?: run {
                val file = File(storageDir, "$remoteId.json")
                if (file.exists()) {
                    try {
                        json.decodeFromString(RemoteProfile.serializer(), file.readText())
                    } catch (_: Exception) {
                        null
                    }
                } else null
            } ?: throw UiTextException(UiText.Resource(R.string.msg_remote_missing_save_button))

            val updatedButtons = current.buttons.toMutableList()
            val index = updatedButtons.indexOfFirst { it.id == updatedButton.id }
            if (index >= 0) {
                updatedButtons[index] = updatedButton
            } else {
                updatedButtons.add(updatedButton)
            }
            val invalidated = current.withoutVerifiedButtons(listOf(updatedButton))
            saveRemoteLocked(invalidated.copy(buttons = updatedButtons.map { button ->
                if (button.key != null) button else button.copy(key = invalidated.buttons.firstOrNull { it.id == button.id }?.key)
            }))
        }
    }

    suspend fun deleteRemote(id: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            recoverRoomMutation()
            requireUiText(id.matches(Regex("[A-Za-z0-9_-]{1,128}")), R.string.msg_remote_id_invalid)
            val file = File(storageDir, "$id.json")
            val trash = File(storageDir, ".trash").apply { if (!exists()) mkdirs() }
            if (!file.exists()) {
                _remotes.value = _remotes.value.filter { it.id != id }
                return@withLock true
            }
            // Give each deleted version a unique filename to preserve earlier learned signals.
            val trashFile = File(trash, "$id-${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
            val deleted = file.renameTo(trashFile)
            checkUiText(deleted, R.string.msg_delete_failed)
            _remotes.value = _remotes.value.filter { it.id != id }
            deleted
        }
    }

    suspend fun updateAcSettings(id: String, settings: AcSettings) = withContext(Dispatchers.IO) {
        mutex.withLock {
            recoverRoomMutation()
            val current = _remotes.value.find { it.id == id }
                ?: throw UiTextException(UiText.Resource(R.string.msg_remote_missing))
            val updated = current.copy(acSettings = settings, lastSentAt = System.currentTimeMillis())
            RemoteBackupCodec.validate(updated)
            AtomicLocalFile.write(File(storageDir, "$id.json"), json.encodeToString(RemoteProfile.serializer(), updated))
            _remotes.value = _remotes.value.map { if (it.id == id) updated else it }
        }
    }

    suspend fun toggleFavorite(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            recoverRoomMutation()
            val current = _remotes.value.find { it.id == id } ?: run {
                val file = File(storageDir, "$id.json")
                if (file.exists()) {
                    try {
                        json.decodeFromString(RemoteProfile.serializer(), file.readText())
                    } catch (_: Exception) {
                        null
                    }
                } else null
            } ?: return@withContext

            val updated = current.copy(isFavorite = !current.isFavorite)
            saveRemoteLocked(updated)
        }
    }

    suspend fun updateDetails(id: String, name: String, room: String) = withContext(Dispatchers.IO) {
        requireUiText(name.isNotBlank() && name.length <= 100 && room.length <= 60, R.string.msg_enter_valid_name_room)
        mutex.withLock {
            recoverRoomMutation()
            val current = _remotes.value.find { it.id == id }
                ?: throw UiTextException(UiText.Resource(R.string.msg_remote_missing))
            saveRemoteLocked(current.copy(name = name.trim(), room = room.trim()))
        }
    }

    suspend fun markUsed(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            recoverRoomMutation()
            val current = _remotes.value.find { it.id == id } ?: return@withLock
            val updated = current.copy(lastUsedAt = System.currentTimeMillis())
            AtomicLocalFile.write(File(storageDir, "$id.json"), json.encodeToString(RemoteProfile.serializer(), updated))
            _remotes.value = _remotes.value.map { if (it.id == id) updated else it }
        }
    }

    private fun deletedEntriesLocked(): List<DeletedRemoteEntry> =
        File(storageDir, ".trash").listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { file ->
            runCatching {
                val profile = json.decodeFromString(RemoteProfile.serializer(), file.readText())
                val timestamp = file.name.removePrefix("${profile.id}-").substringBefore('-').toLongOrNull()
                    ?: file.lastModified()
                DeletedRemoteEntry(file.name, profile, timestamp)
            }.getOrNull()
        }.sortedByDescending { it.deletedAt }

    suspend fun deletedEntries(): List<DeletedRemoteEntry> = withContext(Dispatchers.IO) {
        mutex.withLock { deletedEntriesLocked() }
    }

    suspend fun deletedRemotes(): List<RemoteProfile> = deletedEntries().map { it.profile }

    /** Remove exactly one trash version; never interpret an external entry id as a path. */
    suspend fun permanentlyDelete(entryId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entry = deletedEntriesLocked().firstOrNull { it.entryId == entryId }
                ?: throw UiTextException(UiText.Resource(R.string.msg_trash_remote_missing))
            val trashDir = File(storageDir, ".trash")
            val file = File(trashDir, entry.entryId)
            checkUiText(file.canonicalFile.parentFile == trashDir.canonicalFile, R.string.msg_trash_path_invalid)
            checkUiText(file.delete(), R.string.msg_permanent_delete_retry)
        }
    }

    suspend fun restoreDeleted(id: String, entryId: String? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            recoverRoomMutation()
            require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")))
            val candidate = deletedEntriesLocked().firstOrNull {
                it.profile.id == id && (entryId == null || it.entryId == entryId)
            } ?: throw UiTextException(UiText.Resource(R.string.msg_trash_remote_missing))
            val file = File(File(storageDir, ".trash"), candidate.entryId)
            checkUiText(
                _remotes.value.none { it.id == id } && !File(storageDir, "$id.json").exists(),
                R.string.msg_trash_existing_id
            )
            saveRemoteLocked(upgradeCatalog?.invoke(candidate.profile) ?: candidate.profile)
            checkUiText(file.delete(), R.string.msg_restore_cleanup_failed)
        }
    }

    suspend fun exportRemotes(id: String? = null, preferences: PortablePreferences? = null): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            recoverRoomMutation()
            val profiles = _remotes.value.filter { id == null || it.id == id }
            checkUiText(id == null || profiles.isNotEmpty(), R.string.msg_no_remotes_export)
            requireUiText(
                profiles.size <= RemoteBackupCodec.MAX_PROFILES,
                R.string.msg_export_profile_limit,
                RemoteBackupCodec.MAX_PROFILES.toString()
            )
            RemoteBackupCodec.encode(
                RemoteBackup(
                    remotes = profiles,
                    rooms = if (id == null) _rooms.value else profiles.map { it.room }.filter { it.isNotBlank() }.distinct(),
                    preferences = preferences
                )
            ).also {
                checkUiText(it.toByteArray().size <= RemoteBackupCodec.MAX_BYTES, R.string.msg_backup_split_needed)
            }
        }
    }

    private fun recoverPendingImport() {
        val journal = File(storageDir, ".import-pending.json")
        if (!journal.exists()) return
        val ids = json.decodeFromString<List<String>>(journal.readText())
        ids.forEach { id ->
            require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")))
            val file = File(storageDir, "$id.json")
            checkUiText(!file.exists() || file.delete(), R.string.msg_import_rollback_failed)
        }
        checkUiText(journal.delete(), R.string.msg_import_recovery_failed)
    }

    suspend fun importRemotes(profiles: List<RemoteProfile>, mode: ImportMode): Int =
        importBackup(RemoteBackup(remotes = profiles), profiles.associate { it.id to
            if (mode == ImportMode.COPIES) ConflictChoice.COPY else ConflictChoice.KEEP },
            copyAll = mode == ImportMode.COPIES)

    private val migrationDirectory get() = File(context.filesDir, "migration").apply { mkdirs() }
    private val migrationJournal get() = File(migrationDirectory, "pending.json")
    private val recoverySnapshot get() = File(migrationDirectory, "before-import.json")
    @kotlinx.serialization.Serializable
    private data class MigrationJournal(val originals: Map<String, String?>, val preferences: PortablePreferences?)

    fun portablePreferences(): PortablePreferences = PortablePreferenceStore.read(context)
    suspend fun readRecoverySnapshot(): RemoteBackup = withContext(Dispatchers.IO) {
        mutex.withLock {
            recoverMigration()
            checkUiText(recoverySnapshot.exists(), R.string.migration_no_snapshot)
            RemoteBackupCodec.decodeRecoverySnapshot(recoverySnapshot.readText())
        }
    }

    private fun migrationTarget(relative: String): File {
        require(relative == "rooms.json" || relative.matches(Regex("remotes/[A-Za-z0-9_-]{1,128}\\.json")))
        return File(context.filesDir, relative).also { require(it.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator)) }
    }
    private fun recoverMigration() {
        if (!migrationJournal.exists()) return
        val transaction = json.decodeFromString<MigrationJournal>(migrationJournal.readText())
        transaction.originals.forEach { (relative, previous) ->
            val file = migrationTarget(relative)
            if (previous == null) check(!file.exists() || file.delete()) else AtomicLocalFile.write(file, previous)
        }
        transaction.preferences?.let { PortablePreferenceStore.write(context, it) }
        checkUiText(migrationJournal.delete(), R.string.msg_import_recovery_failed)
    }

    /** One journal covers profile replacements/additions, empty rooms and the selected appearance preferences. */
    suspend fun importBackup(backup: RemoteBackup, choices: Map<String, ConflictChoice>,
        preferences: PortablePreferences? = null, copyAll: Boolean = false): Int = withContext(Dispatchers.IO) {
        RemoteBackupCodec.validateBackup(backup)
        mutex.withLock {
            recoverRoomMutation()
            recoverPendingImport()
            val existing = storageDir.listFiles().orEmpty().filter { it.extension == "json" }.map { it.nameWithoutExtension }.toSet()
            val planned = (if (copyAll) RemoteBackupCodec.plan(backup.remotes, existing, ImportMode.COPIES)
                else RemoteBackupCodec.plan(backup, existing, choices)).map {
                val normalized = LegacyRemoteMigration.normalizeKeys(LegacyRemoteMigration.upgradeLegacyAc(it))
                upgradeCatalog?.invoke(normalized) ?: normalized
            }
            planned.forEach(RemoteBackupCodec::validate)
            val rooms = (savedRooms() + _remotes.value.map { it.room } + backup.rooms + planned.map { it.room })
                .filter { it.isNotBlank() }.distinct().sorted()
            val writes = planned.associate { "remotes/${it.id}.json" to json.encodeToString(it) } +
                ("rooms.json" to json.encodeToString(rooms))
            val originals = writes.keys.associateWith { migrationTarget(it).takeIf(File::exists)?.readText() }
            val currentPreferences = portablePreferences()
            val oldPreferences = preferences?.let { currentPreferences }
            val snapshotText = RemoteBackupCodec.encodeRecoverySnapshot(RemoteBackup(
                remotes = _remotes.value, rooms = _rooms.value, preferences = currentPreferences))
            RemoteBackupCodec.decodeRecoverySnapshot(snapshotText)
            AtomicLocalFile.write(recoverySnapshot, snapshotText)
            AtomicLocalFile.write(migrationJournal, json.encodeToString(MigrationJournal(originals, oldPreferences)))
            try {
                writes.forEach { (relative, content) -> AtomicLocalFile.write(migrationTarget(relative), content) }
                preferences?.let { PortablePreferenceStore.write(context, it) }
                checkUiText(migrationJournal.delete(), R.string.msg_import_commit_failed)
            } catch (e: Exception) {
                try { recoverMigration() } catch (rollback: Exception) { e.addSuppressed(rollback) }
                throw e
            }
            val replacements = planned.associateBy { it.id }
            _remotes.value = (_remotes.value.filter { it.id !in replacements } + planned)
                .sortedWith(compareByDescending<RemoteProfile> { it.isFavorite }.thenByDescending { it.updatedAt })
            _rooms.value = rooms
            planned.size
        }
    }

    private val roomsFile get() = File(context.filesDir, "rooms.json")
    private val roomJournal get() = File(context.filesDir, "room-mutation.json")

    @kotlinx.serialization.Serializable
    private data class RoomMutation(val rooms: List<String>, val profiles: List<RemoteProfile>)

    private fun savedRooms(): List<String> {
        if (!roomsFile.exists()) return emptyList()
        // Read errors are not corruption: leave permission/storage failures visible to the caller.
        val content = roomsFile.readText()
        return try {
            json.decodeFromString<List<String>>(content)
        } catch (_: kotlinx.serialization.SerializationException) {
            val backup = File(context.filesDir, "rooms-corrupt-${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
            // Do not replace damaged metadata until its original contents have been preserved.
            AtomicLocalFile.write(backup, content)
            val recovered = _remotes.value.map { it.room }.filter { it.isNotBlank() }.distinct().sorted()
            AtomicLocalFile.write(roomsFile, json.encodeToString(recovered))
            setRepositoryError(AppError.Resource(UiText.Resource(R.string.msg_rooms_recovered)))
            recovered
        }
    }
    private fun refreshRooms() {
        _rooms.value = (savedRooms() + _remotes.value.map { it.room }).filter { it.isNotBlank() }.distinct().sorted()
    }
    private fun recoverRoomMutation() {
        recoverMigration()
        if (!roomJournal.exists()) return
        val change = json.decodeFromString<RoomMutation>(roomJournal.readText())
        change.profiles.forEach { profile ->
            RemoteBackupCodec.validate(profile)
            AtomicLocalFile.write(File(storageDir, "${profile.id}.json"), json.encodeToString(profile))
        }
        AtomicLocalFile.write(roomsFile, json.encodeToString(change.rooms))
        checkUiText(roomJournal.delete(), R.string.msg_room_update_incomplete)
        val replacements = change.profiles.associateBy { it.id }
        _remotes.value = _remotes.value.map { replacements[it.id] ?: it }
        _rooms.value = change.rooms
    }

    suspend fun editRoom(oldName: String?, newName: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            recoverRoomMutation()
            refreshRooms()
            val name = newName?.trim()
            requireUiText(name == null || name.isNotBlank() && name.length <= 60, R.string.msg_room_name_length)
            requireUiText(oldName == null || oldName in _rooms.value, R.string.msg_room_missing)
            requireUiText(name == null || name == oldName || name !in _rooms.value, R.string.msg_room_duplicate)
            val names = (_rooms.value.filter { it != oldName } + listOfNotNull(name)).sorted()
            val changed = if (oldName == null) emptyList() else _remotes.value.filter { it.room == oldName }
                .map { it.copy(room = name.orEmpty()) }
            AtomicLocalFile.write(roomJournal, json.encodeToString(RoomMutation(names, changed)))
            recoverRoomMutation()
            val replacements = changed.associateBy { it.id }
            _remotes.value = _remotes.value.map { replacements[it.id] ?: it }
            refreshRooms()
        }
    }

    suspend fun updatePanelLayout(id: String, layout: RemotePanelLayout) = withContext(Dispatchers.IO) {
        mutex.withLock {
            recoverRoomMutation()
            val current = _remotes.value.firstOrNull { it.id == id }
                ?: throw UiTextException(UiText.Resource(R.string.msg_remote_missing))
            saveRemoteLocked(current.copy(panelLayout = layout, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun moveToRoom(remoteId: String, room: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            recoverRoomMutation()
            val current = _remotes.value.firstOrNull { it.id == remoteId }
                ?: throw UiTextException(UiText.Resource(R.string.msg_remote_missing))
            requireUiText(room.isBlank() || room in _rooms.value, R.string.msg_room_missing)
            saveRemoteLocked(current.copy(room = room))
        }
    }
}
