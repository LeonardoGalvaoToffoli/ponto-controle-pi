package com.leonardogalvao.pontocontrole.core.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.leonardogalvao.pontocontrole.core.session.SessionStore
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiClient(
    baseUrl: String,
    sessionStore: SessionStore,
) {
    val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.STRICT)
        .create()

    private val apiOrigin = baseUrl.toHttpUrl()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val sameOrigin = request.url.scheme == apiOrigin.scheme &&
                request.url.host == apiOrigin.host &&
                request.url.port == apiOrigin.port
            val protectedCall = !request.url.encodedPath.endsWith("/auth/login")
            val session = sessionStore.session.value
            val authenticated = if (
                sameOrigin && protectedCall && session != null &&
                request.header("Authorization") == null
            ) {
                request.newBuilder()
                    .header("Authorization", "${session.tokenType} ${session.token}")
                    .build()
            } else {
                request
            }
            chain.proceed(authenticated)
        }
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    fun <T> create(service: Class<T>): T = retrofit.create(service)

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 20L
        const val WRITE_TIMEOUT_SECONDS = 20L
    }
}
