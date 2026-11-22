package com.leonardogalvao.pontocontrole.feature.login.domain

sealed interface ChangePasswordResult {
    data object Success : ChangePasswordResult

    data class Failure(
        val message: String,
        val code: String? = null,
        val requestId: String? = null,
        val sessionInvalidated: Boolean = false,
    ) : ChangePasswordResult
}
