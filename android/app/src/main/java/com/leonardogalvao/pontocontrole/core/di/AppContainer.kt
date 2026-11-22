package com.leonardogalvao.pontocontrole.core.di

import com.leonardogalvao.pontocontrole.BuildConfig
import com.leonardogalvao.pontocontrole.core.network.ApiClient
import com.leonardogalvao.pontocontrole.core.session.InMemorySessionStore
import com.leonardogalvao.pontocontrole.core.session.SessionStore
import com.leonardogalvao.pontocontrole.feature.login.data.AuthApi
import com.leonardogalvao.pontocontrole.feature.login.data.NetworkAuthRepository
import com.leonardogalvao.pontocontrole.feature.login.domain.AuthRepository
import com.leonardogalvao.pontocontrole.feature.home.data.NetworkWorkRepository
import com.leonardogalvao.pontocontrole.feature.home.data.WorkApi
import com.leonardogalvao.pontocontrole.feature.home.data.WorkRepository

class AppContainer {
    val sessionStore: SessionStore = InMemorySessionStore()
    private val apiClient = ApiClient(BuildConfig.API_BASE_URL, sessionStore)

    val authRepository: AuthRepository = NetworkAuthRepository(
        authApi = apiClient.create(AuthApi::class.java),
        gson = apiClient.gson,
        sessionStore = sessionStore,
    )

    val workRepository: WorkRepository = NetworkWorkRepository(
        api = apiClient.create(WorkApi::class.java),
        gson = apiClient.gson,
        sessionStore = sessionStore,
    )
}
