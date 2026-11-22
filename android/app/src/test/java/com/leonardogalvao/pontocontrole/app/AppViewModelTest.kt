package com.leonardogalvao.pontocontrole.app

import com.leonardogalvao.pontocontrole.MainDispatcherRule
import com.leonardogalvao.pontocontrole.core.model.AuthSession
import com.leonardogalvao.pontocontrole.core.model.TokenPurpose
import com.leonardogalvao.pontocontrole.core.model.UserRole
import com.leonardogalvao.pontocontrole.core.navigation.AppDestination
import com.leonardogalvao.pontocontrole.core.session.SessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `recupera sessao em memoria na inicializacao`() = runTest {
        val store = FakeSessionStore(session(TokenPurpose.PASSWORD_CHANGE, expiration = 2_000))

        val viewModel = AppViewModel(store, nowEpochSeconds = { 1_000 })
        advanceUntilIdle()

        assertEquals(
            AppUiState.Ready(AppDestination.PASSWORD_CHANGE),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `limpa sessao expirada e inicia no login`() = runTest {
        val store = FakeSessionStore(session(TokenPurpose.ACCESS, expiration = 999))

        val viewModel = AppViewModel(store, nowEpochSeconds = { 1_000 })
        advanceUntilIdle()

        assertEquals(AppUiState.Ready(AppDestination.LOGIN), viewModel.uiState.value)
        assertTrue(store.wasCleared)
    }

    private fun session(
        purpose: TokenPurpose,
        expiration: Long,
    ) = AuthSession(
        token = "jwt-token",
        tokenType = "Bearer",
        tokenPurpose = purpose,
        role = UserRole.FUNCIONARIO,
        expiresAtEpochSeconds = expiration,
        mustChangePassword = purpose == TokenPurpose.PASSWORD_CHANGE,
    )

    private class FakeSessionStore(
        initialSession: AuthSession?,
    ) : SessionStore {
        var wasCleared = false
        private val mutableSession = MutableStateFlow(initialSession)
        override val session: StateFlow<AuthSession?> = mutableSession

        override suspend fun save(session: AuthSession) {
            mutableSession.value = session
        }

        override suspend fun read(): AuthSession? = mutableSession.value

        override suspend fun clear() {
            wasCleared = true
            mutableSession.value = null
        }
    }
}
