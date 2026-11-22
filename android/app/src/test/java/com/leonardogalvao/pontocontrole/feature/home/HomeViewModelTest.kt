package com.leonardogalvao.pontocontrole.feature.home

import com.leonardogalvao.pontocontrole.MainDispatcherRule
import com.leonardogalvao.pontocontrole.core.model.AuthSession
import com.leonardogalvao.pontocontrole.core.model.TokenPurpose
import com.leonardogalvao.pontocontrole.core.model.UserRole
import com.leonardogalvao.pontocontrole.core.session.InMemorySessionStore
import com.leonardogalvao.pontocontrole.feature.home.data.AttendanceDto
import com.leonardogalvao.pontocontrole.feature.home.data.AttendanceListDto
import com.leonardogalvao.pontocontrole.feature.home.data.CreateEmployeeDto
import com.leonardogalvao.pontocontrole.feature.home.data.EmployeeDto
import com.leonardogalvao.pontocontrole.feature.home.data.EmployeeListDto
import com.leonardogalvao.pontocontrole.feature.home.data.MeDto
import com.leonardogalvao.pontocontrole.feature.home.data.RecordAttendanceDto
import com.leonardogalvao.pontocontrole.feature.home.data.WorkRepository
import com.leonardogalvao.pontocontrole.feature.home.data.WorkResult
import java.io.File
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `gerente carrega indicadores equipe e historico`() = runTest {
        val store = sessionStore(UserRole.GERENTE)
        val repository = FakeWorkRepository()
        val viewModel = HomeViewModel(repository, store)

        advanceUntilIdle()

        assertEquals(2L, viewModel.uiState.value.totalEmployees)
        assertEquals(1L, viewModel.uiState.value.enteredToday)
        assertEquals("Funcionária Demo", viewModel.uiState.value.employees.single().name)
        assertEquals(1, viewModel.uiState.value.attendance.size)
    }

    @Test
    fun `reenvio de ponto preserva chave foto e metadados`() = runTest {
        val store = sessionStore(UserRole.FUNCIONARIO)
        val repository = FakeWorkRepository(failFirstAttendance = true)
        val viewModel = HomeViewModel(repository, store)
        advanceUntilIdle()
        val photo = File.createTempFile("attendance-test", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }

        viewModel.prepareAttendance(
            file = photo,
            reading = GpsReading(-23.5, -46.6, 8f, Instant.parse("2026-11-22T20:00:00Z")),
            type = "ENTRADA",
        )
        advanceUntilIdle()

        val pending = requireNotNull(viewModel.uiState.value.pendingAttendance)
        assertEquals(1, repository.attendanceAttempts.size)
        assertTrue(photo.exists())

        viewModel.sendAttendance()
        advanceUntilIdle()

        assertEquals(2, repository.attendanceAttempts.size)
        assertEquals(
            repository.attendanceAttempts[0].idempotencyKey,
            repository.attendanceAttempts[1].idempotencyKey,
        )
        assertSame(repository.attendanceAttempts[0].photo, repository.attendanceAttempts[1].photo)
        assertEquals(pending.metadata, repository.attendanceAttempts[1].metadata)
        assertNull(viewModel.uiState.value.pendingAttendance)
        assertEquals("Entrada registrada com sucesso.", viewModel.uiState.value.successMessage)
        assertTrue(!photo.exists())
    }

    private suspend fun sessionStore(role: UserRole) = InMemorySessionStore().also {
        it.save(
            AuthSession(
                token = "jwt-test",
                tokenType = "Bearer",
                tokenPurpose = TokenPurpose.ACCESS,
                role = role,
                expiresAtEpochSeconds = Long.MAX_VALUE,
                mustChangePassword = false,
            ),
        )
    }

    private data class AttendanceAttempt(
        val idempotencyKey: String,
        val metadata: RecordAttendanceDto,
        val photo: File,
    )

    private class FakeWorkRepository(
        private val failFirstAttendance: Boolean = false,
    ) : WorkRepository {
        val attendanceAttempts = mutableListOf<AttendanceAttempt>()

        override suspend fun me(): WorkResult<MeDto> = WorkResult.Success(
            MeDto(
                id = "employee-1",
                email = "funcionario@empresa.local",
                role = "FUNCIONARIO",
                name = "Funcionária Demo",
                registrationNumber = "MAT-001",
                jobTitle = "Analista",
                profilePhotoUrl = "/photo",
                todayStatus = "SEM_REGISTRO",
            ),
        )

        override suspend fun employees(): WorkResult<EmployeeListDto> = WorkResult.Success(
            EmployeeListDto(
                items = listOf(
                    EmployeeDto(
                        id = "employee-1",
                        email = "funcionario@empresa.local",
                        name = "Funcionária Demo",
                        registrationNumber = "MAT-001",
                        jobTitle = "Analista",
                        profilePhotoUrl = "/photo",
                        todayStatus = "ENTRADA_REGISTRADA",
                    ),
                ),
                totalEmployees = 2,
                enteredToday = 1,
            ),
        )

        override suspend fun createEmployee(
            metadata: CreateEmployeeDto,
            photo: File,
        ): WorkResult<EmployeeDto> = error("Não usado neste teste")

        override suspend fun attendance(employeeId: String?): WorkResult<AttendanceListDto> =
            WorkResult.Success(
                AttendanceListDto(
                    items = listOf(attendance()),
                    page = 0,
                    size = 20,
                    totalItems = 1,
                ),
            )

        override suspend fun recordAttendance(
            idempotencyKey: String,
            metadata: RecordAttendanceDto,
            photo: File,
        ): WorkResult<AttendanceDto> {
            attendanceAttempts += AttendanceAttempt(idempotencyKey, metadata, photo)
            return if (failFirstAttendance && attendanceAttempts.size == 1) {
                WorkResult.Failure("Sem conexão", retryable = true)
            } else {
                WorkResult.Success(attendance())
            }
        }

        override suspend fun photo(url: String): WorkResult<ByteArray> =
            WorkResult.Success(byteArrayOf(1))

        private fun attendance() = AttendanceDto(
            id = "attendance-1",
            employeeId = "employee-1",
            type = "ENTRADA",
            recordedAt = "2026-11-22T20:00:00Z",
            workDate = "2026-11-22",
            distanceMeters = 4.0,
            photoUrl = "/evidence",
        )
    }
}
