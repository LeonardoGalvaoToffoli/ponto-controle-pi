package com.leonardogalvao.pontocontrole.feature.home.data

import com.google.gson.Gson
import com.leonardogalvao.pontocontrole.core.session.SessionStore
import com.leonardogalvao.pontocontrole.feature.login.data.ApiErrorDto
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

interface WorkRepository {
    suspend fun me(): WorkResult<MeDto>
    suspend fun employees(): WorkResult<EmployeeListDto>
    suspend fun createEmployee(metadata: CreateEmployeeDto, photo: File): WorkResult<EmployeeDto>
    suspend fun attendance(employeeId: String? = null): WorkResult<AttendanceListDto>
    suspend fun recordAttendance(
        idempotencyKey: String,
        metadata: RecordAttendanceDto,
        photo: File,
    ): WorkResult<AttendanceDto>
    suspend fun photo(url: String): WorkResult<ByteArray>
}

sealed interface WorkResult<out T> {
    data class Success<T>(val value: T) : WorkResult<T>
    data class Failure(
        val message: String,
        val code: String? = null,
        val retryable: Boolean = false,
        val sessionExpired: Boolean = false,
    ) : WorkResult<Nothing>
}

class NetworkWorkRepository(
    private val api: WorkApi,
    private val gson: Gson,
    private val sessionStore: SessionStore,
) : WorkRepository {
    override suspend fun me() = call { api.me() }

    override suspend fun employees() = call { api.employees() }

    override suspend fun createEmployee(metadata: CreateEmployeeDto, photo: File) = call {
        api.createEmployee(json(metadata), photoPart(photo, "profile.jpg"))
    }

    override suspend fun attendance(employeeId: String?) = call {
        api.attendance(employeeId = employeeId)
    }

    override suspend fun recordAttendance(
        idempotencyKey: String,
        metadata: RecordAttendanceDto,
        photo: File,
    ) = call {
        api.recordAttendance(idempotencyKey, json(metadata), photoPart(photo, "evidence.jpg"))
    }

    override suspend fun photo(url: String): WorkResult<ByteArray> = try {
        val response = api.photo(url)
        if (response.isSuccessful) {
            response.body()?.bytes()?.let { WorkResult.Success(it) }
                ?: WorkResult.Failure("A imagem recebida está vazia.")
        } else {
            failure(response)
        }
    } catch (_: IOException) {
        WorkResult.Failure(NETWORK_MESSAGE, retryable = true)
    }

    private suspend fun <T> call(block: suspend () -> Response<T>): WorkResult<T> = try {
        val response = block()
        if (response.isSuccessful) {
            response.body()?.let { WorkResult.Success(it) }
                ?: WorkResult.Failure("O servidor retornou uma resposta vazia.")
        } else {
            failure(response)
        }
    } catch (_: IOException) {
        WorkResult.Failure(NETWORK_MESSAGE, retryable = true)
    } catch (_: RuntimeException) {
        WorkResult.Failure("Não foi possível interpretar a resposta do servidor.")
    }

    private suspend fun failure(response: Response<*>): WorkResult.Failure {
        val error = runCatching {
            response.errorBody()?.charStream()?.use { gson.fromJson(it, ApiErrorDto::class.java) }
        }.getOrNull()
        val expired = response.code() == 401
        if (expired) sessionStore.clear()
        return WorkResult.Failure(
            message = when {
                response.code() >= 500 -> "O servidor está indisponível. Tente novamente."
                else -> error?.message?.takeIf(String::isNotBlank)
                    ?: "Não foi possível concluir a operação."
            },
            code = error?.code,
            retryable = response.code() >= 500,
            sessionExpired = expired,
        )
    }

    private fun json(value: Any) = gson.toJson(value)
        .toRequestBody("application/json".toMediaType())

    private fun photoPart(file: File, filename: String) = MultipartBody.Part.createFormData(
        "photo",
        filename,
        file.asRequestBody("image/jpeg".toMediaType()),
    )

    private companion object {
        const val NETWORK_MESSAGE =
            "Sem conexão com a API local. Verifique o Docker e tente novamente."
    }
}
