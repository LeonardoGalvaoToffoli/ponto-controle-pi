package com.leonardogalvao.pontocontrole.core.di

import com.leonardogalvao.pontocontrole.BuildConfig
import com.leonardogalvao.pontocontrole.core.network.ApiClient
import com.leonardogalvao.pontocontrole.core.session.InMemorySessionStore
import com.leonardogalvao.pontocontrole.core.session.SessionStore
import com.leonardogalvao.pontocontrole.feature.login.data.AuthApi
import com.leonardogalvao.pontocontrole.feature.login.data.NetworkAuthRepository
import com.leonardogalvao.pontocontrole.feature.login.domain.AuthRepository

class AppContainer {
    private val apiClient = ApiClient(BuildConfig.API_BASE_URL)

    val sessionStore: SessionStore = InMemorySessionStore()

    val authRepository: AuthRepository = NetworkAuthRepository(
        authApi = apiClient.create(AuthApi::class.java),
        gson = apiClient.gson,
        sessionStore = sessionStore,
    )
}
