package com.leonardogalvao.pontocontrole.feature.passwordchange

import com.leonardogalvao.pontocontrole.MainDispatcherRule
import com.leonardogalvao.pontocontrole.core.navigation.AppDestination
import com.leonardogalvao.pontocontrole.feature.login.domain.AuthRepository
import com.leonardogalvao.pontocontrole.feature.login.domain.ChangePasswordResult
import com.leonardogalvao.pontocontrole.feature.login.domain.LoginResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordChangeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `envia senha atual e nova e retorna ao login no sucesso`() = runTest {
        val repository = FakeAuthRepository(ChangePasswordResult.Success)
        val viewModel = PasswordChangeViewModel(repository)

        viewModel.onCurrentPasswordChanged("senha-temp-123")
        viewModel.onNewPasswordChanged("nova-senha-456")
        viewModel.onConfirmationChanged("nova-senha-456")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals("senha-temp-123", repository.currentPassword)
        assertEquals("nova-senha-456", repository.newPassword)
        assertEquals(AppDestination.LOGIN, viewModel.navigationEvents.first())
        assertEquals(PasswordChangeUiState(), viewModel.uiState.value)
    }

    @Test
    fun `bloqueia confirmacao diferente sem chamar api`() = runTest {
        val repository = FakeAuthRepository(ChangePasswordResult.Success)
        val viewModel = PasswordChangeViewModel(repository)

        viewModel.onCurrentPasswordChanged("senha-temp-123")
        viewModel.onNewPasswordChanged("nova-senha-456")
        viewModel.onConfirmationChanged("outra-senha-789")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, repository.changeCalls)
        assertEquals(
            "A confirmação não corresponde à nova senha.",
            viewModel.uiState.value.errorMessage,
        )
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private class FakeAuthRepository(
        private val changeResult: ChangePasswordResult,
    ) : AuthRepository {
        var changeCalls = 0
        var currentPassword: String? = null
        var newPassword: String? = null

        override suspend fun login(email: String, password: String): LoginResult =
            error("Não usado neste teste")

        override suspend fun changePassword(
            currentPassword: String,
            newPassword: String,
        ): ChangePasswordResult {
            changeCalls += 1
            this.currentPassword = currentPassword
            this.newPassword = newPassword
            return changeResult
        }
    }
}
