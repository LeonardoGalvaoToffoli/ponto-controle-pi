package com.leonardogalvao.pontocontrole.feature.login.data

import com.google.gson.annotations.SerializedName
import com.leonardogalvao.pontocontrole.core.model.AuthSession
import com.leonardogalvao.pontocontrole.core.model.TokenPurpose
import com.leonardogalvao.pontocontrole.core.model.UserRole

data class LoginRequestDto(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
) {
    override fun toString(): String = "LoginRequestDto(email=$email, password=<redacted>)"
}

data class ChangePasswordRequestDto(
    @SerializedName("currentPassword") val currentPassword: String,
    @SerializedName("newPassword") val newPassword: String,
) {
    override fun toString(): String =
        "ChangePasswordRequestDto(currentPassword=<redacted>, newPassword=<redacted>)"
}

data class LoginResponseDto(
    @SerializedName("token") val token: String?,
    @SerializedName("tokenType") val tokenType: String?,
    @SerializedName("tokenPurpose") val tokenPurpose: String?,
    @SerializedName("expiresIn") val expiresIn: Long?,
    @SerializedName("role") val role: String?,
    @SerializedName("mustChangePassword") val mustChangePassword: Boolean?,
) {
    fun toAuthSession(nowEpochSeconds: Long): AuthSession? {
        val validToken = token?.takeIf(String::isNotBlank) ?: return null
        val validTokenType = tokenType?.takeIf { it.equals(BEARER, ignoreCase = true) }
            ?: return null
        val validPurpose = tokenPurpose?.toTokenPurposeOrNull() ?: return null
        val validRole = role?.toUserRoleOrNull() ?: return null
        val validLifetime = expiresIn?.takeIf { it > 0 } ?: return null
        val passwordChangeRequired = mustChangePassword ?: return null

        if ((validPurpose == TokenPurpose.PASSWORD_CHANGE) != passwordChangeRequired) return null

        return AuthSession(
            token = validToken,
            tokenType = validTokenType,
            tokenPurpose = validPurpose,
            role = validRole,
            expiresAtEpochSeconds = nowEpochSeconds + validLifetime,
            mustChangePassword = passwordChangeRequired,
        )
    }

    private companion object {
        const val BEARER = "Bearer"
    }

    override fun toString(): String =
        "LoginResponseDto(token=<redacted>, tokenType=$tokenType, tokenPurpose=$tokenPurpose, " +
            "expiresIn=$expiresIn, role=$role, mustChangePassword=$mustChangePassword)"
}

data class ApiErrorDto(
    @SerializedName("code") val code: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("requestId") val requestId: String?,
)

private fun String.toTokenPurposeOrNull(): TokenPurpose? =
    TokenPurpose.entries.firstOrNull { it.name == this }

private fun String.toUserRoleOrNull(): UserRole? =
    UserRole.entries.firstOrNull { it.name == this }
