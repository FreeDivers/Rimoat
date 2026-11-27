package org.qinyan.rimoat.hardware.learning

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.hardware.IrSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fallback learner representing an environment with no physical or simulated IR hardware.
 */
class NoHardwareLearner(
    private val customErrorMessage: String? = null
) : IrLearner {
    val errorMessage: String
        get() = customErrorMessage ?: AppStrings.get(R.string.msg_learning_hardware_missing)

    fun refreshLanguage() { _state.value = LearningState.Error(errorMessage) }

    override val type: LearnerType = LearnerType.NONE

    private val _isAvailable = MutableStateFlow(false)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _state = MutableStateFlow<LearningState>(LearningState.Error(errorMessage))
    override val state: StateFlow<LearningState> = _state.asStateFlow()

    override suspend fun startLearning(timeoutSeconds: Int): Result<IrSignal.Raw> {
        _state.value = LearningState.Error(errorMessage)
        return Result.failure(IllegalStateException(errorMessage))
    }

    override fun cancel() {
        _state.value = LearningState.Error(errorMessage)
    }
}

