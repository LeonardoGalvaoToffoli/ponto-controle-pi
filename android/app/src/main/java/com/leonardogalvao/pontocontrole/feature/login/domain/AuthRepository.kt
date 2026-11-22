package com.leonardogalvao.pontocontrole.feature.login.domain

interface AuthRepository {
    suspend fun login(email: String, password: String): LoginResult

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): ChangePasswordResult
}
