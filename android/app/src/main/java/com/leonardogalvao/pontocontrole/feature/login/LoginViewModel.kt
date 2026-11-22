package com.leonardogalvao.pontocontrole.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.leonardogalvao.pontocontrole.core.model.TokenPurpose
import com.leonardogalvao.pontocontrole.core.navigation.AppDestination
import com.leonardogalvao.pontocontrole.core.session.SessionStore
import com.leonardogalvao.pontocontrole.feature.login.domain.AuthRepository
import com.leonardogalvao.pontocontrole.feature.login.domain.LoginResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _navigationEvents = Channel<AppDestination>(capacity = Channel.BUFFERED)
    val navigationEvents: Flow<AppDestination> = _navigationEvents.receiveAsFlow()

    fun onEmailChanged(value: String) {
        _uiState.update {
            it.copy(email = value.take(MAX_EMAIL_LENGTH), errorMessage = null)
        }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update {
            it.copy(password = value.take(MAX_PASSWORD_LENGTH), errorMessage = null)
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun localNetworkPermissionDenied() {
        _uiState.update {
            it.copy(
                errorMessage = "Autorize o acesso à rede local para conectar à API do Docker.",
            )
        }
    }

    fun submit() {
        val currentState = _uiState.value
        if (currentState.isLoading) return

        val email = currentState.email.trim()
        if (email.isEmpty() || currentState.password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = REQUIRED_FIELDS_MESSAGE) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = authRepository.login(email, currentState.password)) {
                is LoginResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }

                is LoginResult.Success -> startSessionAndNavigate(result)
            }
        }
    }

    private suspend fun startSessionAndNavigate(result: LoginResult.Success) {
        try {
            sessionStore.save(result.session)
            _uiState.update {
                it.copy(password = "", isLoading = false, errorMessage = null)
            }
            val destination = when (result.session.tokenPurpose) {
                TokenPurpose.ACCESS -> AppDestination.HOME
                TokenPurpose.PASSWORD_CHANGE -> AppDestination.PASSWORD_CHANGE
            }
            _navigationEvents.send(destination)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = SESSION_START_ERROR_MESSAGE)
            }
        }
    }

    companion object {
        private const val MAX_EMAIL_LENGTH = 254
        private const val MAX_PASSWORD_LENGTH = 128
        private const val REQUIRED_FIELDS_MESSAGE = "Informe o e-mail e a senha."
        private const val SESSION_START_ERROR_MESSAGE =
            "Não foi possível iniciar a sessão. Tente novamente."

        fun factory(
            authRepository: AuthRepository,
            sessionStore: SessionStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(LoginViewModel::class.java))
                return LoginViewModel(authRepository, sessionStore) as T
            }
        }
    }
}
