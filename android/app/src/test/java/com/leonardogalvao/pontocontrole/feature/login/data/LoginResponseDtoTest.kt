package com.leonardogalvao.pontocontrole.feature.login.data

import com.leonardogalvao.pontocontrole.core.model.TokenPurpose
import com.leonardogalvao.pontocontrole.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoginResponseDtoTest {
    @Test
    fun `converte resposta de acesso em sessao com expiracao absoluta`() {
        val response = LoginResponseDto(
            token = "jwt-token",
            tokenType = "Bearer",
            tokenPurpose = "ACCESS",
            expiresIn = 1_800,
            role = "GERENTE",
            mustChangePassword = false,
        )

        val session = response.toAuthSession(nowEpochSeconds = 10_000)

        requireNotNull(session)
        assertEquals(TokenPurpose.ACCESS, session.tokenPurpose)
        assertEquals(UserRole.GERENTE, session.role)
        assertEquals(11_800L, session.expiresAtEpochSeconds)
    }

    @Test
    fun `rejeita resposta inconsistente de troca de senha`() {
        val response = LoginResponseDto(
            token = "jwt-token",
            tokenType = "Bearer",
            tokenPurpose = "PASSWORD_CHANGE",
            expiresIn = 600,
            role = "FUNCIONARIO",
            mustChangePassword = false,
        )

        assertNull(response.toAuthSession(nowEpochSeconds = 10_000))
    }
}
