package com.leonardogalvao.pontocontrole.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.leonardogalvao.pontocontrole.core.model.TokenPurpose
import com.leonardogalvao.pontocontrole.core.navigation.AppDestination
import com.leonardogalvao.pontocontrole.core.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(
    private val sessionStore: SessionStore,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Loading)
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val session = sessionStore.read()
            val destination = when {
                session == null -> AppDestination.LOGIN
                session.isExpired(nowEpochSeconds()) -> {
                    sessionStore.clear()
                    AppDestination.LOGIN
                }
                session.tokenPurpose == TokenPurpose.PASSWORD_CHANGE -> AppDestination.PASSWORD_CHANGE
                else -> AppDestination.HOME
            }
            _uiState.value = AppUiState.Ready(destination)
        }
    }

    companion object {
        fun factory(sessionStore: SessionStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(AppViewModel::class.java))
                    return AppViewModel(sessionStore) as T
                }
            }
    }
}

sealed interface AppUiState {
    data object Loading : AppUiState

    data class Ready(val startDestination: AppDestination) : AppUiState
}
