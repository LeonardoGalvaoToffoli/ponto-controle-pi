package com.leonardogalvao.pontocontrole.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
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
    var isLocatingAttendance by remember { mutableStateOf(false) }

    val collectLocation: (File) -> Unit = { photo ->
        scope.launch {
            isLocatingAttendance = true
            try {
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
            } finally {
                isLocatingAttendance = false
                attendancePhoto = null
            }
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
            isLocatingAttendance = isLocatingAttendance,
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
    isLocatingAttendance: Boolean = false,
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
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (isLocatingAttendance) {
                        item {
                            AttendanceProgressCard(AttendanceProgressStage.VALIDATING_LOCATION)
                        }
                    } else if (state.isSubmitting && state.pendingAttendance != null) {
                        item {
                            AttendanceProgressCard(AttendanceProgressStage.RECORDING)
                        }
                    }
                    state.errorMessage?.let { message ->
                        item { MessageCard(message, isError = true, onDismiss = onClearMessage) }
                    }
                    state.attendanceConfirmation?.let { confirmation ->
                        item {
                            AttendanceSuccessCard(
                                confirmation = confirmation,
                                subtitle = "Registro efetuado às ${formatTime(confirmation.recordedAt)}.",
                                onDismiss = onClearMessage,
                            )
                        }
                    }
                    state.successMessage?.takeIf { state.attendanceConfirmation == null }?.let { message ->
                        item { MessageCard(message, isError = false, onDismiss = onClearMessage) }
                    }
                    if (state.isLoading || (state.isSubmitting && state.pendingAttendance == null)) {
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
                            isLocatingAttendance = isLocatingAttendance,
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
            SummaryCard(
                label = "Funcionários",
                value = state.totalEmployees.toString(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = "Entraram hoje",
                value = state.enteredToday.toString(),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
    item {
        Button(
            onClick = { onShowEmployeeForm(true) },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_person_add),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("Cadastrar funcionário")
        }
    }
    item { SectionTitle("Equipe", count = state.employees.size) }
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
    isLocatingAttendance: Boolean,
    onRecordAttendance: () -> Unit,
    onRetryAttendance: () -> Unit,
    onLoadEvidence: (String) -> Unit,
) {
    val me = state.me
    item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                me?.name?.let { "${greeting()}, ${it.substringBefore(' ')}" } ?: "Meu expediente",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                listOfNotNull(me?.jobTitle, me?.registrationNumber?.let { "Matrícula $it" })
                    .joinToString(" · ")
                    .ifEmpty { "Área do funcionário" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (me != null) {
        item {
            ShiftClockCard(
                status = me.todayStatus ?: "SEM_REGISTRO",
                nextType = nextAttendanceType(me.todayStatus),
                actionEnabled = !state.isSubmitting &&
                    !isLocatingAttendance &&
                    state.pendingAttendance == null,
                onRecordAttendance = onRecordAttendance,
            )
        }
    }
    state.pendingAttendance?.let {
        item {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Envio pendente", fontWeight = FontWeight.SemiBold)
                    Text(
                        "O registro não chegou ao servidor. A foto e a mesma chave foram preservadas.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = onRetryAttendance,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
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
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing && !isSubmitting,
            ) {
                RefreshIndicator(isRefreshing = isRefreshing)
            }
            IconButton(onClick = onLogout) {
                Icon(
                    painter = painterResource(R.drawable.ic_exit),
                    contentDescription = "Sair",
                )
            }
        }
    }
}

@Composable
private fun ShiftClockCard(
    status: String,
    nextType: String?,
    actionEnabled: Boolean,
    onRecordAttendance: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000L - (System.currentTimeMillis() % 1_000L))
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            now.format(TIME_FORMAT),
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text(
                            now.format(SECONDS_FORMAT),
                            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp).alpha(0.7f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        now.format(LONG_DATE_FORMAT).replaceFirstChar { it.uppercase() },
                        modifier = Modifier.alpha(0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                StatusLabel(status, onAccent = true)
            }

            if (nextType != null) {
                Button(
                    onClick = onRecordAttendance,
                    enabled = actionEnabled,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimary,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        painter = painterResource(
                            if (nextType == "ENTRADA") R.drawable.ic_entry else R.drawable.ic_exit,
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (nextType == "ENTRADA") "Registrar entrada" else "Registrar saída",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(0.8f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_photo),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Text("Foto", style = MaterialTheme.typography.labelSmall)
                    Icon(
                        painter = painterResource(R.drawable.ic_location),
                        contentDescription = null,
                        modifier = Modifier.padding(start = 6.dp).size(14.dp),
                    )
                    Text("Localização precisa", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_circle),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Expediente de hoje concluído. Até amanhã!",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alpha(0.8f))
        }
    }
}

@Composable
private fun EmployeeCard(employee: EmployeeDto) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InitialsAvatar(employee.name)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(employee.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${employee.jobTitle} · Matrícula ${employee.registrationNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    employee.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusLabel(employee.todayStatus, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun InitialsAvatar(name: String) {
    val initials = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .let { parts -> listOfNotNull(parts.firstOrNull(), parts.drop(1).lastOrNull()) }
        .joinToString("") { it.first().uppercase() }
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun AttendanceCard(record: AttendanceDto, onLoadEvidence: (String) -> Unit) {
    val isEntry = record.type == "ENTRADA"
    val badgeColor = if (isEntry) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val badgeContent = if (isEntry) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(badgeColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (isEntry) R.drawable.ic_entry else R.drawable.ic_exit),
                    contentDescription = null,
                    tint = badgeContent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(attendanceTypeLabel(record.type), fontWeight = FontWeight.SemiBold)
                    Text(
                        formatTime(record.recordedAt),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    "${formatDate(record.recordedAt)} · ${formatDistance(record.distanceMeters)} da empresa",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { onLoadEvidence(record.photoUrl) },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_photo),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Ver foto de evidência")
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(
    status: String,
    modifier: Modifier = Modifier,
    onAccent: Boolean = false,
) {
    val text = when (status) {
        "ENTRADA_REGISTRADA" -> "Em expediente"
        "SAIDA_REGISTRADA" -> "Expediente concluído"
        else -> "Sem registro hoje"
    }
    val dotColor = when (status) {
        "ENTRADA_REGISTRADA" -> StatusActive
        "SAIDA_REGISTRADA" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = modifier,
        color = if (onAccent) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (onAccent) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (onAccent && status == "SAIDA_REGISTRADA") {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            dotColor
                        },
                        CircleShape,
                    ),
            )
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MessageCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val view = LocalView.current
    val horizontalOffset = remember(message, isError) { Animatable(0f) }

    LaunchedEffect(message, isError) {
        if (isError) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                },
            )
            listOf(-7f, 7f, -5f, 5f, 0f).forEach { target ->
                horizontalOffset.animateTo(target, tween(55))
            }
        }
    }

    Surface(
        modifier = Modifier.offset(x = horizontalOffset.value.dp),
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
private fun SectionTitle(text: String, count: Int? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        if (count != null && count > 0) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CircleShape,
            ) {
                Text(
                    count.toString(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
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
                OutlinedButton(
                    onClick = onCamera,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_photo),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.photo == null) "Capturar foto de perfil" else "Refazer foto")
                }
                if (state.photo != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check_circle),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "Foto capturada",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
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

private fun attendanceTypeLabel(type: String) = if (type == "ENTRADA") "Entrada" else "Saída"

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}

private fun formatDate(value: String): String = runCatching {
    Instant.parse(value).atZone(ZoneId.systemDefault()).format(DATE_FORMAT)
}.getOrElse { value }

private fun formatTime(value: String): String = runCatching {
    Instant.parse(value).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)
}.getOrElse { value }

private fun formatDistance(meters: Double): String = if (meters >= 1_000) {
    String.format(PT_BR, "%.1f km", meters / 1_000)
} else {
    "${meters.toInt()} m"
}

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val StatusActive = Color(0xFF3FA66B)
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val SECONDS_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern(":ss")
private val LONG_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR)
