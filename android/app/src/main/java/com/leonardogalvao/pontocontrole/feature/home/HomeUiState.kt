package com.leonardogalvao.pontocontrole.feature.home

import com.leonardogalvao.pontocontrole.core.model.UserRole
import com.leonardogalvao.pontocontrole.feature.home.data.AttendanceDto
import com.leonardogalvao.pontocontrole.feature.home.data.EmployeeDto
import com.leonardogalvao.pontocontrole.feature.home.data.MeDto
import java.io.File

data class EmployeeFormState(
    val name: String = "",
    val registrationNumber: String = "",
    val jobTitle: String = "",
    val email: String = "",
    val temporaryPassword: String = "",
    val photo: File? = null,
)

data class PendingAttendance(
    val idempotencyKey: String,
    val metadata: com.leonardogalvao.pontocontrole.feature.home.data.RecordAttendanceDto,
    val photo: File,
)

data class AttendanceConfirmation(
    val id: String,
    val type: String,
    val recordedAt: String,
)

data class HomeUiState(
    val title: String = "Controle de Ponto",
    val environment: String = "Ambiente local",
    val role: UserRole? = null,
    val me: MeDto? = null,
    val employees: List<EmployeeDto> = emptyList(),
    val attendance: List<AttendanceDto> = emptyList(),
    val totalEmployees: Long = 0,
    val enteredToday: Long = 0,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val showEmployeeForm: Boolean = false,
    val employeeForm: EmployeeFormState = EmployeeFormState(),
    val cameraMode: CameraMode? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val attendanceConfirmation: AttendanceConfirmation? = null,
    val pendingAttendance: PendingAttendance? = null,
    val evidenceBytes: ByteArray? = null,
    val sessionExpired: Boolean = false,
)

enum class CameraMode {
    PROFILE,
    ATTENDANCE,
}
