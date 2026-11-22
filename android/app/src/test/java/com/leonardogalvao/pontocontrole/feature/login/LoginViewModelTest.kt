package com.leonardogalvao.pontocontrole.feature.login

import com.leonardogalvao.pontocontrole.MainDispatcherRule
import com.leonardogalvao.pontocontrole.core.model.AuthSession
import com.leonardogalvao.pontocontrole.core.model.TokenPurpose
import com.leonardogalvao.pontocontrole.core.model.UserRole
import com.leonardogalvao.pontocontrole.core.navigation.AppDestination
import com.leonardogalvao.pontocontrole.core.session.SessionStore
import com.leonardogalvao.pontocontrole.feature.login.domain.AuthRepository
import com.leonardogalvao.pontocontrole.feature.login.domain.ChangePasswordResult
import com.leonardogalvao.pontocontrole.feature.login.domain.LoginResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `salva sessao e navega para troca de senha`() = runTest {
        val session = session(TokenPurpose.PASSWORD_CHANGE, mustChangePassword = true)
        val store = FakeSessionStore()
        val viewModel = LoginViewModel(
            authRepository = FakeAuthRepository(LoginResult.Success(session)),
            sessionStore = store,
        )

        viewModel.onEmailChanged(" funcionario@empresa.com ")
        viewModel.onPasswordChanged("senha-temporaria")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(session, store.savedSession)
        assertEquals(AppDestination.PASSWORD_CHANGE, viewModel.navigationEvents.first())
        assertEquals("", viewModel.uiState.value.password)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `exibe falha devolvida pelo repositorio`() = runTest {
        val viewModel = LoginViewModel(
            authRepository = FakeAuthRepository(LoginResult.Failure("E-mail ou senha inválidos.")),
            sessionStore = FakeSessionStore(),
        )

        viewModel.onEmailChanged("gerente@empresa.com")
        viewModel.onPasswordChanged("senha-incorreta")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals("E-mail ou senha inválidos.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `nao envia formulario sem credenciais`() = runTest {
        val repository = FakeAuthRepository(LoginResult.Success(session(TokenPurpose.ACCESS, false)))
        val viewModel = LoginViewModel(repository, FakeSessionStore())

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, repository.calls)
        assertEquals("Informe o e-mail e a senha.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `orienta quando acesso a rede local e negado`() = runTest {
        val viewModel = LoginViewModel(
            FakeAuthRepository(LoginResult.Success(session(TokenPurpose.ACCESS, false))),
            FakeSessionStore(),
        )

        viewModel.localNetworkPermissionDenied()

        assertEquals(
            "Autorize o acesso à rede local para conectar à API do Docker.",
            viewModel.uiState.value.errorMessage,
        )
    }

    private fun session(
        purpose: TokenPurpose,
        mustChangePassword: Boolean,
    ) = AuthSession(
        token = "jwt-token",
        tokenType = "Bearer",
        tokenPurpose = purpose,
        role = UserRole.FUNCIONARIO,
        expiresAtEpochSeconds = 20_000,
        mustChangePassword = mustChangePassword,
    )

    private class FakeAuthRepository(
        private val result: LoginResult,
    ) : AuthRepository {
        var calls = 0

        override suspend fun login(email: String, password: String): LoginResult {
            calls += 1
            return result
        }

        override suspend fun changePassword(
            currentPassword: String,
            newPassword: String,
        ): ChangePasswordResult = error("Não usado neste teste")
    }

    private class FakeSessionStore : SessionStore {
        var savedSession: AuthSession? = null
        private val mutableSession = MutableStateFlow<AuthSession?>(null)
        override val session: StateFlow<AuthSession?> = mutableSession

        override suspend fun save(session: AuthSession) {
            savedSession = session
            mutableSession.value = session
        }

        override suspend fun read(): AuthSession? = savedSession

        override suspend fun clear() {
            savedSession = null
            mutableSession.value = null
        }
    }
}
