package com.leonardogalvao.pontocontrole.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leonardogalvao.pontocontrole.core.model.UserRole
import com.leonardogalvao.pontocontrole.R
import com.leonardogalvao.pontocontrole.core.session.SessionStore
import com.leonardogalvao.pontocontrole.feature.home.data.AttendanceDto
import com.leonardogalvao.pontocontrole.feature.home.data.EmployeeDto
import com.leonardogalvao.pontocontrole.feature.home.data.WorkRepository
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun HomeRoute(
    workRepository: WorkRepository,
    sessionStore: SessionStore,
    onLogout: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(workRepository, sessionStore),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gps = remember(context) { GpsLocationProvider(context.applicationContext) }
    var requestedCameraMode by remember { mutableStateOf<CameraMode?>(null) }
    var attendancePhoto by remember { mutableStateOf<File?>(null) }

    val collectLocation: (File) -> Unit = { photo ->
        scope.launch {
            when (val result = gps.current()) {
                is GpsResult.Success -> {
                    val type = nextAttendanceType(state.me?.todayStatus)
                    if (type == null) {
                        photo.delete()
                        viewModel.showError("O ponto de hoje já foi concluído.")
                    } else {
                        viewModel.prepareAttendance(photo, result.reading, type)
                    }
                }
                is GpsResult.Failure -> {
                    photo.delete()
                    viewModel.showError(result.message)
                }
            }
            attendancePhoto = null
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val hasPreciseLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        val photo = attendancePhoto
        if (hasPreciseLocation && photo != null) {
            collectLocation(photo)
        } else {
            photo?.delete()
            attendancePhoto = null
            viewModel.showError("Autorize a localização precisa para registrar o ponto.")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val mode = requestedCameraMode
        requestedCameraMode = null
        if (granted && mode != null) {
            viewModel.requestCamera(mode)
        } else {
            viewModel.showError("Autorize o uso da câmera para capturar a foto.")
        }
    }

    val openCamera: (CameraMode) -> Unit = { mode ->
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.requestCamera(mode)
        } else {
            requestedCameraMode = mode
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onLogout()
    }

    if (state.cameraMode != null) {
        CameraCaptureScreen(
            onCaptured = { file ->
                when (state.cameraMode) {
                    CameraMode.PROFILE -> viewModel.profilePhotoCaptured(file)
                    CameraMode.ATTENDANCE -> {
                        viewModel.closeCamera()
                        attendancePhoto = file
                        if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            collectLocation(file)
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        }
                    }
                    null -> file.delete()
                }
            },
            onCancel = viewModel::closeCamera,
            onError = {
                viewModel.closeCamera()
                viewModel.showError(it)
            },
        )
    } else {
        HomeScreen(
            state = state,
            onRefresh = viewModel::refresh,
            onLogout = onLogout,
            onClearMessage = viewModel::clearMessage,
            onShowEmployeeForm = viewModel::showEmployeeForm,
            onEmployeeFormChange = viewModel::updateEmployeeForm,
            onOpenCamera = openCamera,
            onSubmitEmployee = viewModel::submitEmployee,
            onRecordAttendance = { openCamera(CameraMode.ATTENDANCE) },
            onRetryAttendance = viewModel::sendAttendance,
            onLoadEvidence = viewModel::loadEvidence,
            onCloseEvidence = viewModel::closeEvidence,
        )
    }
}

@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onClearMessage: () -> Unit,
    onShowEmployeeForm: (Boolean) -> Unit,
    onEmployeeFormChange: ((EmployeeFormState) -> EmployeeFormState) -> Unit,
    onOpenCamera: (CameraMode) -> Unit,
    onSubmitEmployee: () -> Unit,
    onRecordAttendance: () -> Unit,
    onRetryAttendance: () -> Unit,
    onLoadEvidence: (String) -> Unit,
    onCloseEvidence: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                role = state.role,
                isRefreshing = state.isLoading,
                isSubmitting = state.isSubmitting,
                onRefresh = onRefresh,
                onLogout = onLogout,
            )
            if (state.isLoading && state.employees.isEmpty() && state.me == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    state.errorMessage?.let { message ->
                        item { MessageCard(message, isError = true, onDismiss = onClearMessage) }
                    }
                    state.successMessage?.let { message ->
                        item { MessageCard(message, isError = false, onDismiss = onClearMessage) }
                    }
                    if (state.isLoading || state.isSubmitting) {
                        item { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                    }
                    when (state.role) {
                        UserRole.GERENTE -> managerContent(
                            state = state,
                            onShowEmployeeForm = onShowEmployeeForm,
                            onLoadEvidence = onLoadEvidence,
                        )
                        UserRole.FUNCIONARIO -> employeeContent(
                            state = state,
                            onRecordAttendance = onRecordAttendance,
                            onRetryAttendance = onRetryAttendance,
                            onLoadEvidence = onLoadEvidence,
                        )
                        null -> item { Text("Sessão indisponível.") }
                    }
                }
            }
        }
    }

    if (state.showEmployeeForm) {
        EmployeeDialog(
            state = state.employeeForm,
            isSubmitting = state.isSubmitting,
            onChange = onEmployeeFormChange,
            onCamera = { onOpenCamera(CameraMode.PROFILE) },
            onSubmit = onSubmitEmployee,
            onDismiss = { onShowEmployeeForm(false) },
        )
    }
    state.evidenceBytes?.let { bytes ->
        EvidenceDialog(bytes = bytes, onDismiss = onCloseEvidence)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.managerContent(
    state: HomeUiState,
    onShowEmployeeForm: (Boolean) -> Unit,
    onLoadEvidence: (String) -> Unit,
) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Painel gerencial", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Acompanhe a equipe e os registros recentes.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard("Funcionários", state.totalEmployees.toString(), Modifier.weight(1f))
            SummaryCard("Entraram hoje", state.enteredToday.toString(), Modifier.weight(1f))
        }
    }
    item {
        Button(
            onClick = { onShowEmployeeForm(true) },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cadastrar funcionário")
        }
    }
    item { SectionTitle("Equipe") }
    if (state.employees.isEmpty()) {
        item { EmptyCard("Nenhum funcionário cadastrado.") }
    } else {
        items(state.employees, key = { it.id }) { employee -> EmployeeCard(employee) }
    }
    item { SectionTitle("Histórico recente") }
    if (state.attendance.isEmpty()) {
        item { EmptyCard("Ainda não existem registros de ponto.") }
    } else {
        items(state.attendance, key = { it.id }) { record ->
            AttendanceCard(record, onLoadEvidence)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.employeeContent(
    state: HomeUiState,
    onRecordAttendance: () -> Unit,
    onRetryAttendance: () -> Unit,
    onLoadEvidence: (String) -> Unit,
) {
    val me = state.me
    item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Meu expediente", style = MaterialTheme.typography.headlineSmall)
            Text(
                me?.name?.let { "Olá, $it" } ?: "Área do funcionário",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (me != null) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(me.name ?: me.email, fontWeight = FontWeight.SemiBold)
                    me.registrationNumber?.let { Text("Matrícula: $it") }
                    me.jobTitle?.let { Text("Cargo: $it") }
                    StatusLabel(me.todayStatus ?: "SEM_REGISTRO")
                }
            }
        }
        item {
            val action = attendanceAction(me.todayStatus)
            if (action != null) {
                Button(
                    onClick = onRecordAttendance,
                    enabled = !state.isSubmitting && state.pendingAttendance == null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(action, fontWeight = FontWeight.SemiBold)
                }
            } else {
                EmptyCard("Expediente de hoje concluído.")
            }
        }
    }
    state.pendingAttendance?.let {
        item {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("O envio não foi concluído. A foto e a mesma chave foram preservadas.")
                    OutlinedButton(onClick = onRetryAttendance, enabled = !state.isSubmitting) {
                        Text("Tentar enviar novamente")
                    }
                }
            }
        }
    }
    item { SectionTitle("Meu histórico") }
    if (state.attendance.isEmpty()) {
        item { EmptyCard("Você ainda não registrou pontos.") }
    } else {
        items(state.attendance, key = { it.id }) { record ->
            AttendanceCard(record, onLoadEvidence)
        }
    }
}

@Composable
private fun AppHeader(
    role: UserRole?,
    isRefreshing: Boolean,
    isSubmitting: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    Surface(
        modifier = Modifier.statusBarsPadding(),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(
                    MaterialTheme.colorScheme.primaryContainer,
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_time_clock_logo),
                    contentDescription = "Logo Controle de Ponto",
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Controle de Ponto", fontWeight = FontWeight.SemiBold)
                Text(
                    if (role == UserRole.GERENTE) "Acesso gerente" else "Acesso funcionário",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onRefresh,
                enabled = !isRefreshing && !isSubmitting,
            ) {
                Text(if (isRefreshing) "Atualizando..." else "Atualizar")
            }
            TextButton(onClick = onLogout) { Text("Sair") }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmployeeCard(employee: EmployeeDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(employee.name, fontWeight = FontWeight.SemiBold)
            Text("${employee.jobTitle} · Matrícula ${employee.registrationNumber}")
            Text(employee.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusLabel(employee.todayStatus)
        }
    }
}

@Composable
private fun AttendanceCard(record: AttendanceDto, onLoadEvidence: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(attendanceTypeLabel(record.type), fontWeight = FontWeight.SemiBold)
                Text(formatInstant(record.recordedAt), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Distância da empresa: ${record.distanceMeters.toInt()} m",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { onLoadEvidence(record.photoUrl) }) {
                Text("Ver foto de evidência")
            }
        }
    }
}

@Composable
private fun StatusLabel(status: String) {
    val text = when (status) {
        "ENTRADA_REGISTRADA" -> "Entrada registrada"
        "SAIDA_REGISTRADA" -> "Expediente concluído"
        else -> "Sem registro hoje"
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun MessageCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        HorizontalDivider()
    }
}

@Composable
private fun EmptyCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(message, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun EmployeeDialog(
    state: EmployeeFormState,
    isSubmitting: Boolean,
    onChange: ((EmployeeFormState) -> EmployeeFormState) -> Unit,
    onCamera: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Cadastrar funcionário") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { value -> onChange { it.copy(name = value) } },
                    label = { Text("Nome completo") },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.registrationNumber,
                    onValueChange = { value -> onChange { it.copy(registrationNumber = value) } },
                    label = { Text("Matrícula") },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.jobTitle,
                    onValueChange = { value -> onChange { it.copy(jobTitle = value) } },
                    label = { Text("Cargo") },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.email,
                    onValueChange = { value -> onChange { it.copy(email = value) } },
                    label = { Text("E-mail corporativo") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.temporaryPassword,
                    onValueChange = { value -> onChange { it.copy(temporaryPassword = value) } },
                    label = { Text("Senha temporária") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = onCamera, enabled = !isSubmitting) {
                    Text(if (state.photo == null) "Capturar foto de perfil" else "Refazer foto")
                }
                if (state.photo != null) {
                    Text("Foto capturada", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = !isSubmitting) {
                Text(if (isSubmitting) "Salvando..." else "Cadastrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancelar") }
        },
    )
}

@Composable
private fun EvidenceDialog(bytes: ByteArray, onDismiss: () -> Unit) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Foto de evidência") },
        text = {
            if (bitmap == null) {
                Text("Não foi possível exibir a imagem.")
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Evidência do registro de ponto",
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

private fun nextAttendanceType(status: String?): String? = when (status) {
    null, "SEM_REGISTRO" -> "ENTRADA"
    "ENTRADA_REGISTRADA" -> "SAIDA"
    else -> null
}

private fun attendanceAction(status: String?): String? = when (nextAttendanceType(status)) {
    "ENTRADA" -> "Registrar entrada"
    "SAIDA" -> "Registrar saída"
    else -> null
}

private fun attendanceTypeLabel(type: String) = if (type == "ENTRADA") "Entrada" else "Saída"

private fun formatInstant(value: String): String = runCatching {
    Instant.parse(value).atZone(ZoneId.systemDefault()).format(DATE_TIME_FORMAT)
}.getOrElse { value }

private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
