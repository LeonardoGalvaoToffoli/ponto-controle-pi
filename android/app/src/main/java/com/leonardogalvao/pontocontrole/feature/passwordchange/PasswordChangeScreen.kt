package com.leonardogalvao.pontocontrole.feature.passwordchange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leonardogalvao.pontocontrole.core.navigation.AppDestination
import com.leonardogalvao.pontocontrole.core.ui.theme.PontoControleTheme
import com.leonardogalvao.pontocontrole.feature.login.domain.AuthRepository
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PasswordChangeRoute(
    authRepository: AuthRepository,
    onFinished: (AppDestination) -> Unit,
) {
    val viewModel: PasswordChangeViewModel = viewModel(
        factory = PasswordChangeViewModel.factory(authRepository),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collectLatest(onFinished)
    }

    PasswordChangeScreen(
        state = state,
        onCurrentPasswordChanged = viewModel::onCurrentPasswordChanged,
        onNewPasswordChanged = viewModel::onNewPasswordChanged,
        onConfirmationChanged = viewModel::onConfirmationChanged,
        onToggleVisibility = viewModel::togglePasswordVisibility,
        onDismissError = viewModel::dismissError,
        onSubmit = viewModel::submit,
    )
}

@Composable
internal fun PasswordChangeScreen(
    state: PasswordChangeUiState,
    onCurrentPasswordChanged: (String) -> Unit,
    onNewPasswordChanged: (String) -> Unit,
    onConfirmationChanged: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onDismissError: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val transformation = if (state.passwordsVisible) {
        VisualTransformation.None
    } else {
        PasswordVisualTransformation()
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Proteja seu acesso",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Antes de continuar, substitua a senha temporária por uma senha pessoal.",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.errorMessage?.let { message ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(message, style = MaterialTheme.typography.bodyMedium)
                                TextButton(
                                    onClick = onDismissError,
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text("Fechar")
                                }
                            }
                        }
                    }

                    PasswordField(
                        value = state.currentPassword,
                        onValueChanged = onCurrentPasswordChanged,
                        label = "Senha temporária atual",
                        transformation = transformation,
                        enabled = !state.isLoading,
                    )
                    PasswordField(
                        value = state.newPassword,
                        onValueChanged = onNewPasswordChanged,
                        label = "Nova senha",
                        transformation = transformation,
                        enabled = !state.isLoading,
                    )
                    PasswordField(
                        value = state.confirmation,
                        onValueChanged = onConfirmationChanged,
                        label = "Confirmar nova senha",
                        transformation = transformation,
                        enabled = !state.isLoading,
                        imeAction = ImeAction.Done,
                        onDone = {
                            focusManager.clearFocus()
                            onSubmit()
                        },
                    )

                    Text(
                        text = "Use de 12 a 72 bytes. A nova senha deve ser diferente da temporária.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = onToggleVisibility,
                        enabled = !state.isLoading,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(if (state.passwordsVisible) "Ocultar senhas" else "Mostrar senhas")
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onSubmit()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = state.canSubmit,
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Definir nova senha", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChanged: (String) -> Unit,
    label: String,
    transformation: VisualTransformation,
    enabled: Boolean,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

@Preview(showBackground = true)
@Composable
private fun PasswordChangeScreenPreview() {
    PontoControleTheme {
        PasswordChangeScreen(
            state = PasswordChangeUiState(),
            onCurrentPasswordChanged = {},
            onNewPasswordChanged = {},
            onConfirmationChanged = {},
            onToggleVisibility = {},
            onDismissError = {},
            onSubmit = {},
        )
    }
}
