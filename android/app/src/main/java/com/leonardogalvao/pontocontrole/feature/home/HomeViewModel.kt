package com.leonardogalvao.pontocontrole.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leonardogalvao.pontocontrole.core.model.UserRole
import com.leonardogalvao.pontocontrole.core.session.SessionStore
import com.leonardogalvao.pontocontrole.feature.home.data.CreateEmployeeDto
import com.leonardogalvao.pontocontrole.feature.home.data.RecordAttendanceDto
import com.leonardogalvao.pontocontrole.feature.home.data.WorkRepository
import com.leonardogalvao.pontocontrole.feature.home.data.WorkResult
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: WorkRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(role = sessionStore.session.value?.role),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }
            when (_uiState.value.role) {
                UserRole.GERENTE -> loadManager()
                UserRole.FUNCIONARIO -> loadEmployee()
                null -> expireSession()
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun loadManager() {
        when (val employees = repository.employees()) {
            is WorkResult.Success -> _uiState.update {
                it.copy(
                    employees = employees.value.items,
                    totalEmployees = employees.value.totalEmployees,
                    enteredToday = employees.value.enteredToday,
                )
            }
            is WorkResult.Failure -> handleFailure(employees)
        }
        if (_uiState.value.sessionExpired) return
        when (val attendance = repository.attendance()) {
            is WorkResult.Success -> _uiState.update {
                it.copy(attendance = attendance.value.items)
            }
            is WorkResult.Failure -> handleFailure(attendance)
        }
    }

    private suspend fun loadEmployee() {
        when (val me = repository.me()) {
            is WorkResult.Success -> _uiState.update { it.copy(me = me.value) }
            is WorkResult.Failure -> handleFailure(me)
        }
        if (_uiState.value.sessionExpired) return
        when (val attendance = repository.attendance()) {
            is WorkResult.Success -> _uiState.update {
                it.copy(attendance = attendance.value.items)
            }
            is WorkResult.Failure -> handleFailure(attendance)
        }
    }

    fun showEmployeeForm(show: Boolean) {
        if (_uiState.value.isSubmitting) return
        if (!show) _uiState.value.employeeForm.photo?.delete()
        _uiState.update {
            it.copy(
                showEmployeeForm = show,
                employeeForm = if (show) it.employeeForm else EmployeeFormState(),
                errorMessage = null,
            )
        }
    }

    fun updateEmployeeForm(transform: (EmployeeFormState) -> EmployeeFormState) {
        _uiState.update { it.copy(employeeForm = transform(it.employeeForm)) }
    }

    fun requestCamera(mode: CameraMode) {
        _uiState.update { it.copy(cameraMode = mode, errorMessage = null) }
    }

    fun closeCamera() {
        _uiState.update { it.copy(cameraMode = null) }
    }

    fun profilePhotoCaptured(file: File) {
        _uiState.value.employeeForm.photo?.delete()
        _uiState.update {
            it.copy(
                cameraMode = null,
                employeeForm = it.employeeForm.copy(photo = file),
            )
        }
    }

    fun submitEmployee() {
        val form = _uiState.value.employeeForm
        val validation = employeeValidation(form)
        if (validation != null) {
            showError(validation)
            return
        }
        val photo = requireNotNull(form.photo)
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = repository.createEmployee(
                metadata = CreateEmployeeDto(
                    name = form.name.trim(),
                    registrationNumber = form.registrationNumber.trim(),
                    jobTitle = form.jobTitle.trim(),
                    email = form.email.trim().lowercase(),
                    temporaryPassword = form.temporaryPassword,
                ),
                photo = photo,
            )
            when (result) {
                is WorkResult.Success -> {
                    photo.delete()
                    _uiState.update {
                        it.copy(
                            showEmployeeForm = false,
                            employeeForm = EmployeeFormState(),
                            successMessage = "Funcionário cadastrado com sucesso.",
                        )
                    }
                    loadManager()
                }
                is WorkResult.Failure -> handleFailure(result)
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun prepareAttendance(file: File, reading: GpsReading, type: String) {
        _uiState.value.pendingAttendance?.photo?.delete()
        _uiState.update {
            it.copy(
                cameraMode = null,
                pendingAttendance = PendingAttendance(
                    idempotencyKey = UUID.randomUUID().toString(),
                    metadata = RecordAttendanceDto(
                        type = type,
                        latitude = reading.latitude,
                        longitude = reading.longitude,
                        accuracyMeters = reading.accuracyMeters,
                        capturedAt = reading.capturedAt.toString(),
                    ),
                    photo = file,
                ),
                errorMessage = null,
            )
        }
        sendAttendance()
    }

    fun sendAttendance() {
        val pending = _uiState.value.pendingAttendance ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (
                val result = repository.recordAttendance(
                    pending.idempotencyKey,
                    pending.metadata,
                    pending.photo,
                )
            ) {
                is WorkResult.Success -> {
                    pending.photo.delete()
                    _uiState.update {
                        it.copy(
                            pendingAttendance = null,
                            successMessage = if (result.value.type == "ENTRADA") {
                                "Entrada registrada com sucesso."
                            } else {
                                "Saída registrada com sucesso."
                            },
                        )
                    }
                    loadEmployee()
                }
                is WorkResult.Failure -> {
                    if (!result.retryable) {
                        pending.photo.delete()
                        _uiState.update { it.copy(pendingAttendance = null) }
                    }
                    handleFailure(result)
                }
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun loadEvidence(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = repository.photo(url)) {
                is WorkResult.Success -> _uiState.update {
                    it.copy(evidenceBytes = result.value)
                }
                is WorkResult.Failure -> handleFailure(result)
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun closeEvidence() {
        _uiState.update { it.copy(evidenceBytes = null) }
    }

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message, successMessage = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private suspend fun handleFailure(failure: WorkResult.Failure) {
        if (failure.sessionExpired) {
            expireSession()
        } else {
            _uiState.update { it.copy(errorMessage = failure.message, successMessage = null) }
        }
    }

    private suspend fun expireSession() {
        sessionStore.clear()
        _uiState.update { it.copy(sessionExpired = true) }
    }

    private fun employeeValidation(form: EmployeeFormState): String? = when {
        form.name.trim().length !in 2..120 -> "O nome deve ter entre 2 e 120 caracteres."
        !REGISTRATION_REGEX.matches(form.registrationNumber.trim().uppercase()) ->
            "Use até 30 letras, números, ponto, hífen ou sublinhado na matrícula."
        form.jobTitle.trim().length !in 2..80 -> "O cargo deve ter entre 2 e 80 caracteres."
        form.email.trim().length !in 3..254 || !EMAIL_REGEX.matches(form.email.trim()) ->
            "Informe um e-mail válido."
        form.temporaryPassword.toByteArray().size !in 12..72 ->
            "A senha temporária deve ter entre 12 e 72 caracteres."
        form.photo == null -> "Capture a foto de perfil do funcionário."
        else -> null
    }

    override fun onCleared() {
        _uiState.value.employeeForm.photo?.delete()
        _uiState.value.pendingAttendance?.photo?.delete()
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private val REGISTRATION_REGEX = Regex("^[A-Z0-9._-]{1,30}$")

        fun factory(
            repository: WorkRepository,
            sessionStore: SessionStore,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repository, sessionStore) }
        }
    }
}
