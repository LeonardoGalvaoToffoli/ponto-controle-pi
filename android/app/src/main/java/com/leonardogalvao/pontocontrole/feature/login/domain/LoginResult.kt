package com.leonardogalvao.pontocontrole.feature.login.domain

import com.leonardogalvao.pontocontrole.core.model.AuthSession

sealed interface LoginResult {
    data class Success(val session: AuthSession) : LoginResult

    data class Failure(
        val message: String,
        val code: String? = null,
        val requestId: String? = null,
    ) : LoginResult
}
