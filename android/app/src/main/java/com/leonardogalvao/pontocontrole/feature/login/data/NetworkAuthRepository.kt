package com.leonardogalvao.pontocontrole.feature.login.data

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.leonardogalvao.pontocontrole.core.model.TokenPurpose
import com.leonardogalvao.pontocontrole.core.session.SessionStore
import com.leonardogalvao.pontocontrole.feature.login.domain.AuthRepository
import com.leonardogalvao.pontocontrole.feature.login.domain.ChangePasswordResult
import com.leonardogalvao.pontocontrole.feature.login.domain.LoginResult
import java.io.IOException

class NetworkAuthRepository(
    private val authApi: AuthApi,
    private val gson: Gson,
    private val sessionStore: SessionStore,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : AuthRepository {
    override suspend fun login(email: String, password: String): LoginResult {
        return try {
            val response = authApi.login(LoginRequestDto(email = email, password = password))
            if (response.isSuccessful) {
                val session = response.body()?.toAuthSession(nowEpochSeconds())
                    ?: return LoginResult.Failure(INVALID_RESPONSE_MESSAGE)
                LoginResult.Success(session)
            } else {
                if (response.code() == HTTP_UNAUTHORIZED) sessionStore.clear()
                response.toLoginFailure()
            }
        } catch (_: IOException) {
            LoginResult.Failure(NETWORK_ERROR_MESSAGE)
        } catch (_: JsonParseException) {
            LoginResult.Failure(INVALID_RESPONSE_MESSAGE)
        }
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): ChangePasswordResult {
        val session = sessionStore.read()
        if (
            session == null ||
            session.tokenPurpose != TokenPurpose.PASSWORD_CHANGE ||
            session.isExpired(nowEpochSeconds())
        ) {
            sessionStore.clear()
            return ChangePasswordResult.Failure(
                message = SESSION_REQUIRED_MESSAGE,
                code = UNAUTHORIZED_CODE,
                sessionInvalidated = true,
            )
        }

        return try {
            val response = authApi.changePassword(
                authorization = "${session.tokenType} ${session.token}",
                request = ChangePasswordRequestDto(
                    currentPassword = currentPassword,
                    newPassword = newPassword,
                ),
            )
            if (response.isSuccessful) {
                sessionStore.clear()
                ChangePasswordResult.Success
            } else {
                val apiError = response.parseApiError()
                val invalidated = response.code() == HTTP_UNAUTHORIZED
                if (invalidated) sessionStore.clear()
                ChangePasswordResult.Failure(
                    message = response.safeErrorMessage(apiError),
                    code = apiError?.code,
                    requestId = apiError?.requestId,
                    sessionInvalidated = invalidated,
                )
            }
        } catch (_: IOException) {
            ChangePasswordResult.Failure(NETWORK_ERROR_MESSAGE)
        } catch (_: JsonParseException) {
            ChangePasswordResult.Failure(INVALID_RESPONSE_MESSAGE)
        }
    }

    private fun retrofit2.Response<*>.toLoginFailure(): LoginResult.Failure {
        val apiError = parseApiError()

        val safeMessage = when {
            code() == HTTP_UNAUTHORIZED -> INVALID_CREDENTIALS_MESSAGE
            code() >= HTTP_SERVER_ERROR -> SERVER_ERROR_MESSAGE
            else -> apiError?.message?.trim()?.takeIf(String::isNotBlank)?.take(MAX_ERROR_LENGTH)
                ?: GENERIC_ERROR_MESSAGE
        }

        return LoginResult.Failure(
            message = safeMessage,
            code = apiError?.code,
            requestId = apiError?.requestId,
        )
    }

    private fun retrofit2.Response<*>.parseApiError(): ApiErrorDto? = runCatching {
        errorBody()?.charStream()?.use { gson.fromJson(it, ApiErrorDto::class.java) }
    }.getOrNull()

    private fun retrofit2.Response<*>.safeErrorMessage(apiError: ApiErrorDto?): String = when {
        code() >= HTTP_SERVER_ERROR -> SERVER_ERROR_MESSAGE
        else -> apiError?.message?.trim()?.takeIf(String::isNotBlank)?.take(MAX_ERROR_LENGTH)
            ?: GENERIC_ERROR_MESSAGE
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_SERVER_ERROR = 500
        const val MAX_ERROR_LENGTH = 240
        const val UNAUTHORIZED_CODE = "UNAUTHORIZED"
        const val INVALID_CREDENTIALS_MESSAGE = "E-mail ou senha inválidos."
        const val SESSION_REQUIRED_MESSAGE = "Sua sessão expirou. Entre novamente."
        const val NETWORK_ERROR_MESSAGE =
            "Não foi possível conectar ao servidor local. Verifique se a API está em execução."
        const val INVALID_RESPONSE_MESSAGE = "O servidor retornou uma resposta inválida."
        const val SERVER_ERROR_MESSAGE = "O servidor está indisponível no momento. Tente novamente."
        const val GENERIC_ERROR_MESSAGE = "Não foi possível entrar. Verifique os dados e tente novamente."
    }
}
