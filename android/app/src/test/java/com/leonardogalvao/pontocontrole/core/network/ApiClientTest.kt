package com.leonardogalvao.pontocontrole.core.network

import com.leonardogalvao.pontocontrole.core.model.AuthSession
import com.leonardogalvao.pontocontrole.core.model.TokenPurpose
import com.leonardogalvao.pontocontrole.core.model.UserRole
import com.leonardogalvao.pontocontrole.core.session.InMemorySessionStore
import com.leonardogalvao.pontocontrole.feature.home.data.WorkApi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiClientTest {
    @Test
    fun `envia token somente para a mesma origem da api`() = runTest {
        val apiServer = MockWebServer()
        val externalServer = MockWebServer()
        apiServer.start()
        externalServer.start()
        try {
            val sessionStore = InMemorySessionStore().also { it.save(session()) }
            val api = ApiClient(apiServer.url("/").toString(), sessionStore)
                .create(WorkApi::class.java)
            apiServer.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"id":"1","email":"e@empresa.local","role":"FUNCIONARIO","name":"E","registrationNumber":"1","jobTitle":"A","profilePhotoUrl":"/photo","todayStatus":"SEM_REGISTRO"}""",
                    ),
            )
            externalServer.enqueue(MockResponse().setResponseCode(200).setBody("image"))

            api.me()
            api.photo(externalServer.url("/image").toString())

            val protectedRequest = apiServer.takeRequest(1, TimeUnit.SECONDS)
            val externalRequest = externalServer.takeRequest(1, TimeUnit.SECONDS)
            assertEquals("Bearer jwt-test", protectedRequest?.getHeader("Authorization"))
            assertNull(externalRequest?.getHeader("Authorization"))
        } finally {
            apiServer.shutdown()
            externalServer.shutdown()
        }
    }

    private fun session() = AuthSession(
        token = "jwt-test",
        tokenType = "Bearer",
        tokenPurpose = TokenPurpose.ACCESS,
        role = UserRole.FUNCIONARIO,
        expiresAtEpochSeconds = Long.MAX_VALUE,
        mustChangePassword = false,
    )
}
