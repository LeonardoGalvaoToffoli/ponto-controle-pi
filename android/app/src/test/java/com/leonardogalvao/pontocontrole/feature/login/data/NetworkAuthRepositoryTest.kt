package com.leonardogalvao.pontocontrole.feature.login.data

import com.leonardogalvao.pontocontrole.core.model.AuthSession
import com.leonardogalvao.pontocontrole.core.model.TokenPurpose
import com.leonardogalvao.pontocontrole.core.model.UserRole
import com.leonardogalvao.pontocontrole.core.network.ApiClient
import com.leonardogalvao.pontocontrole.core.session.InMemorySessionStore
import com.leonardogalvao.pontocontrole.feature.login.domain.ChangePasswordResult
import com.leonardogalvao.pontocontrole.feature.login.domain.LoginResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkAuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var sessionStore: InMemorySessionStore
    private lateinit var repository: NetworkAuthRepository

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        sessionStore = InMemorySessionStore()
        val client = ApiClient(server.url("/").toString(), sessionStore)
        repository = NetworkAuthRepository(
            authApi = client.create(AuthApi::class.java),
            gson = client.gson,
            sessionStore = sessionStore,
            nowEpochSeconds = { 10_000 },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login envia contrato e converte resposta`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "token":"jwt-token",
                      "tokenType":"Bearer",
                      "tokenPurpose":"ACCESS",
                      "expiresIn":1800,
                      "role":"GERENTE",
                      "mustChangePassword":false
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.login("gerente@empresa.com", "senha-segura-123")
        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))

        assertEquals("/api/v1/auth/login", request.path)
        assertTrue(request.body.readUtf8().contains("\"email\":\"gerente@empresa.com\""))
        assertTrue(result is LoginResult.Success)
        assertEquals(11_800L, (result as LoginResult.Success).session.expiresAtEpochSeconds)
    }

    @Test
    fun `troca de senha envia bearer e limpa token apos 204`() = runTest {
        sessionStore.save(passwordChangeSession())
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository.changePassword(
            currentPassword = "senha-temp-123",
            newPassword = "nova-senha-456",
        )
        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))

        assertEquals("/api/v1/auth/change-password", request.path)
        assertEquals("Bearer jwt-change", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"currentPassword\":\"senha-temp-123\""))
        assertTrue(body.contains("\"newPassword\":\"nova-senha-456\""))
        assertEquals(ChangePasswordResult.Success, result)
        assertNull(sessionStore.read())
    }

    @Test
    fun `resposta 401 limpa qualquer sessao em memoria`() = runTest {
        sessionStore.save(passwordChangeSession())
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"code":"INVALID_CREDENTIALS","message":"Email ou senha inválidos.","requestId":"req-1"}""",
                ),
        )

        val result = repository.login("funcionario@empresa.com", "senha-errada-123")

        assertTrue(result is LoginResult.Failure)
        assertEquals("INVALID_CREDENTIALS", (result as LoginResult.Failure).code)
        assertNull(sessionStore.read())
    }

    private fun passwordChangeSession() = AuthSession(
        token = "jwt-change",
        tokenType = "Bearer",
        tokenPurpose = TokenPurpose.PASSWORD_CHANGE,
        role = UserRole.FUNCIONARIO,
        expiresAtEpochSeconds = 20_000,
        mustChangePassword = true,
    )
}
