package org.qinyan.rimoat.ui.motion

import org.qinyan.rimoat.ui.learning.CustomRemoteWizardUiState
import org.qinyan.rimoat.ui.learning.WizardStep
import org.qinyan.rimoat.ui.viewmodel.AddRemoteStep
import org.qinyan.rimoat.ui.viewmodel.AddRemoteUiState

/** Pure snapshots: creating a preview must never cancel a job or edit a learned signal. */
internal fun AddRemoteUiState.backPreview(): AddRemoteUiState? {
    if (isSaving || isSendingTest) return null
    val previous = when (step) {
        AddRemoteStep.CATEGORY -> return null
        AddRemoteStep.BRAND -> AddRemoteStep.CATEGORY
        AddRemoteStep.WIZARD -> AddRemoteStep.BRAND
        AddRemoteStep.CONFIRM -> AddRemoteStep.WIZARD
    }
    return copy(step = previous, isLoading = false, error = null)
}

internal val CustomRemoteWizardUiState.motionKey: String
    get() = step.name

internal val CustomRemoteWizardUiState.motionOrder: Int
    get() = when (step) {
        WizardStep.SETUP -> -1
        WizardStep.LEARNING -> currentIndex
        WizardStep.REVIEW -> keyQueue.size + 1
    }

internal fun CustomRemoteWizardUiState.backPreview(): CustomRemoteWizardUiState? {
    if (isSaving || isRestoring) return null
    return when (step) {
        WizardStep.SETUP -> null
        WizardStep.REVIEW -> copy(step = WizardStep.LEARNING, isTestingTransmit = false, testFeedbackMessage = null)
        WizardStep.LEARNING -> copy(step = WizardStep.SETUP, isTestingTransmit = false, testFeedbackMessage = null)
    }
}
