package org.qinyan.rimoat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

internal class PageViewModelStores : ViewModel() {
    private val owners = mutableMapOf<AppScreen, ViewModelStoreOwner>()

    fun ownerFor(screen: AppScreen): ViewModelStoreOwner = owners.getOrPut(screen) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    fun findOwner(screen: AppScreen): ViewModelStoreOwner? = owners[screen]

    fun release(screen: AppScreen) {
        owners.remove(screen)?.viewModelStore?.clear()
    }

    fun releaseExcept(screen: AppScreen) {
        owners.keys.filter { it != screen }.forEach(::release)
    }

    override fun onCleared() {
        owners.keys.toList().forEach(::release)
    }
}
