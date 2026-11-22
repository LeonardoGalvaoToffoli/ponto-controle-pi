package com.leonardogalvao.pontocontrole.core.model

data class AuthSession(
    val token: String,
    val tokenType: String,
    val tokenPurpose: TokenPurpose,
    val role: UserRole,
    val expiresAtEpochSeconds: Long,
    val mustChangePassword: Boolean,
) {
    fun isExpired(nowEpochSeconds: Long): Boolean = expiresAtEpochSeconds <= nowEpochSeconds

    override fun toString(): String =
        "AuthSession(token=<redacted>, tokenType=$tokenType, tokenPurpose=$tokenPurpose, " +
            "role=$role, expiresAtEpochSeconds=$expiresAtEpochSeconds, " +
            "mustChangePassword=$mustChangePassword)"
}

enum class TokenPurpose {
    ACCESS,
    PASSWORD_CHANGE,
}

enum class UserRole {
    GERENTE,
    FUNCIONARIO,
}
