package org.qinyan.rimoat.ui.viewmodel

import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.data.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class RoomViewModel(
    private val repository: RemoteRepository,
    private val performStorageOperation: (suspend () -> Unit) -> Unit
) {
    private val _room = MutableStateFlow<String?>(null)
    val selectedRoom = _room.asStateFlow()
    val rooms = repository.rooms
    val allRemotes = repository.remotes
    fun editRoom(oldName: String?, newName: String?, onSaved: () -> Unit) = performStorageOperation {
        repository.editRoom(oldName, newName)
        if (_room.value == oldName && oldName != null) _room.value = newName?.trim()
        onSaved()
    }
    fun moveToRoom(id: String, room: String) = performStorageOperation { repository.moveToRoom(id, room) }
    fun selectRoom(room: String?) { _room.value = room }
}
