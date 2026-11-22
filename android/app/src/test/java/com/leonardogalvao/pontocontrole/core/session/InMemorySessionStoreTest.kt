package com.leonardogalvao.pontocontrole.core.session

import com.leonardogalvao.pontocontrole.core.model.AuthSession
import com.leonardogalvao.pontocontrole.core.model.TokenPurpose
import com.leonardogalvao.pontocontrole.core.model.UserRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemorySessionStoreTest {
    @Test
    fun `mantem sessao somente na instancia em memoria`() = runTest {
        val store = InMemorySessionStore()
        val session = AuthSession(
            token = "jwt-token",
            tokenType = "Bearer",
            tokenPurpose = TokenPurpose.ACCESS,
            role = UserRole.GERENTE,
            expiresAtEpochSeconds = 2_000,
            mustChangePassword = false,
        )

        store.save(session)

        assertEquals(session, store.read())
        assertEquals(session, store.session.value)

        store.clear()

        assertNull(store.read())
        assertNull(store.session.value)
    }
}
