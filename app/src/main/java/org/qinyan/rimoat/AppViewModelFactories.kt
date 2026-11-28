package org.qinyan.rimoat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.qinyan.rimoat.ui.learning.CustomRemoteWizardViewModel
import org.qinyan.rimoat.ui.learning.WizardDrafts
import org.qinyan.rimoat.ui.viewmodel.AddRemoteViewModel
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel

internal class AppViewModelFactories(private val app: IrRemoteApplication) {
    fun remoteList(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RemoteListViewModel(app.repository, app.transmitterManager, app.themePreferences, org.qinyan.rimoat.data.BackupFileStore(app)) as T
        }
    }

    fun addRemote(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return AddRemoteViewModel(
                irDbRepository = app.irDatabaseRepository,
                remoteRepository = app.repository,
                transmitterManager = app.transmitterManager,
                savedStateHandle = extras.createSavedStateHandle()
            ) as T
        }
    }

    fun remoteControl(screen: AppScreen.RemoteControl): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RemoteControlViewModel(
                remoteId = screen.remoteId,
                initialProfile = app.repository.remotes.value.firstOrNull { it.id == screen.remoteId },
                repository = app.repository,
                transmitterManager = app.transmitterManager,
                learnerManager = app.learnerManager,
                acSignalEncoder = app.acSignals,
                commandExecutor = app.commands
            ) as T
        }
    }

    fun wizardViewModel(
        screen: AppScreen.CustomRemoteWizard,
        owner: ViewModelStoreOwner
    ): CustomRemoteWizardViewModel {
        return ViewModelProvider(owner, viewModelFactory {
            initializer {
                CustomRemoteWizardViewModel(
                    repository = app.repository,
                    transmitterManager = app.transmitterManager,
                    learnerManager = app.learnerManager,
                    initialCategory = screen.category,
                    draftFile = WizardDrafts.file(app.filesDir, screen.sessionId),
                    brandLibrary = app.irDatabaseRepository
                )
            }
        })[CustomRemoteWizardViewModel::class.java]
    }

}
