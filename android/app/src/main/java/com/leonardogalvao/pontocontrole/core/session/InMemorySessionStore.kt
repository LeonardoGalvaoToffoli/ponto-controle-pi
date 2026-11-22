package com.leonardogalvao.pontocontrole.core.session

import com.leonardogalvao.pontocontrole.core.model.AuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemorySessionStore : SessionStore {
    private val mutex = Mutex()
    private val _session = MutableStateFlow<AuthSession?>(null)
    override val session: StateFlow<AuthSession?> = _session.asStateFlow()

    override suspend fun save(session: AuthSession) {
        mutex.withLock {
            _session.value = session
        }
    }

    override suspend fun read(): AuthSession? = mutex.withLock {
        _session.value
    }

    override suspend fun clear() {
        mutex.withLock {
            _session.value = null
        }
    }
}
