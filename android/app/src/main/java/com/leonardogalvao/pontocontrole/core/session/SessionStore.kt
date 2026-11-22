package com.leonardogalvao.pontocontrole.core.session

import com.leonardogalvao.pontocontrole.core.model.AuthSession
import kotlinx.coroutines.flow.StateFlow

interface SessionStore {
    val session: StateFlow<AuthSession?>

    suspend fun save(session: AuthSession)

    suspend fun read(): AuthSession?

    suspend fun clear()
}
