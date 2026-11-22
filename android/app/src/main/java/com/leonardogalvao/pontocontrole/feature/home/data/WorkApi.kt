package com.leonardogalvao.pontocontrole.feature.home.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Url

interface WorkApi {
    @GET("api/v1/me")
    suspend fun me(): Response<MeDto>

    @GET("api/v1/employees")
    suspend fun employees(): Response<EmployeeListDto>

    @Multipart
    @POST("api/v1/employees")
    suspend fun createEmployee(
        @Part("metadata") metadata: RequestBody,
        @Part photo: MultipartBody.Part,
    ): Response<EmployeeDto>

    @GET("api/v1/attendance")
    suspend fun attendance(
        @Query("employeeId") employeeId: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Response<AttendanceListDto>

    @Multipart
    @POST("api/v1/attendance")
    suspend fun recordAttendance(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Part("metadata") metadata: RequestBody,
        @Part photo: MultipartBody.Part,
    ): Response<AttendanceDto>

    @GET
    suspend fun photo(@Url url: String): Response<ResponseBody>
}

data class MeDto(
    val id: String,
    val email: String,
    val role: String,
    val name: String?,
    val registrationNumber: String?,
    val jobTitle: String?,
    val profilePhotoUrl: String?,
    val todayStatus: String?,
)

data class EmployeeDto(
    val id: String,
    val email: String,
    val name: String,
    val registrationNumber: String,
    val jobTitle: String,
    val profilePhotoUrl: String,
    val todayStatus: String,
)

data class EmployeeListDto(
    val items: List<EmployeeDto>,
    val totalEmployees: Long,
    val enteredToday: Long,
)

data class AttendanceDto(
    val id: String,
    val employeeId: String,
    val type: String,
    val recordedAt: String,
    val workDate: String,
    val distanceMeters: Double,
    val photoUrl: String,
)

data class AttendanceListDto(
    val items: List<AttendanceDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
)

data class CreateEmployeeDto(
    val name: String,
    val registrationNumber: String,
    val jobTitle: String,
    val email: String,
    val temporaryPassword: String,
)

data class RecordAttendanceDto(
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAt: String,
)
