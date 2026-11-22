package com.leonardogalvao.pontocontrole.feature.passwordchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.leonardogalvao.pontocontrole.core.navigation.AppDestination
import com.leonardogalvao.pontocontrole.feature.login.domain.AuthRepository
import com.leonardogalvao.pontocontrole.feature.login.domain.ChangePasswordResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PasswordChangeViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PasswordChangeUiState())
    val uiState: StateFlow<PasswordChangeUiState> = _uiState.asStateFlow()

    private val _navigationEvents = Channel<AppDestination>(Channel.BUFFERED)
    val navigationEvents: Flow<AppDestination> = _navigationEvents.receiveAsFlow()

    fun onCurrentPasswordChanged(value: String) = updatePassword {
        copy(currentPassword = value.take(MAX_PASSWORD_CHARACTERS))
    }

    fun onNewPasswordChanged(value: String) = updatePassword {
        copy(newPassword = value.take(MAX_PASSWORD_CHARACTERS))
    }

    fun onConfirmationChanged(value: String) = updatePassword {
        copy(confirmation = value.take(MAX_PASSWORD_CHARACTERS))
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(passwordsVisible = !it.passwordsVisible) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isLoading) return

        val validationError = validate(state)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = authRepository.changePassword(state.currentPassword, state.newPassword)) {
                ChangePasswordResult.Success -> {
                    _uiState.value = PasswordChangeUiState()
                    _navigationEvents.send(AppDestination.LOGIN)
                }

                is ChangePasswordResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                    if (result.sessionInvalidated) {
                        _navigationEvents.send(AppDestination.LOGIN)
                    }
                }
            }
        }
    }

    private fun updatePassword(transform: PasswordChangeUiState.() -> PasswordChangeUiState) {
        _uiState.update { it.transform().copy(errorMessage = null) }
    }

    private fun validate(state: PasswordChangeUiState): String? = when {
        state.currentPassword.isEmpty() || state.newPassword.isEmpty() || state.confirmation.isEmpty() ->
            REQUIRED_FIELDS_MESSAGE

        state.newPassword != state.confirmation -> PASSWORDS_DO_NOT_MATCH_MESSAGE
        state.currentPassword == state.newPassword -> PASSWORD_MUST_DIFFER_MESSAGE
        state.newPassword.toByteArray(Charsets.UTF_8).size !in MIN_PASSWORD_BYTES..MAX_PASSWORD_BYTES ->
            INVALID_PASSWORD_LENGTH_MESSAGE

        else -> null
    }

    companion object {
        private const val MAX_PASSWORD_CHARACTERS = 72
        private const val MIN_PASSWORD_BYTES = 12
        private const val MAX_PASSWORD_BYTES = 72
        private const val REQUIRED_FIELDS_MESSAGE = "Preencha todos os campos de senha."
        private const val PASSWORDS_DO_NOT_MATCH_MESSAGE = "A confirmação não corresponde à nova senha."
        private const val PASSWORD_MUST_DIFFER_MESSAGE =
            "A nova senha deve ser diferente da senha temporária."
        private const val INVALID_PASSWORD_LENGTH_MESSAGE =
            "A nova senha deve ter entre 12 e 72 bytes."

        fun factory(authRepository: AuthRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(PasswordChangeViewModel::class.java))
                    return PasswordChangeViewModel(authRepository) as T
                }
            }
    }
}
