package com.leonardogalvao.pontocontrole.feature.passwordchange

data class PasswordChangeUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmation: String = "",
    val passwordsVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = currentPassword.isNotEmpty() &&
            newPassword.isNotEmpty() &&
            confirmation.isNotEmpty() &&
            !isLoading
}
