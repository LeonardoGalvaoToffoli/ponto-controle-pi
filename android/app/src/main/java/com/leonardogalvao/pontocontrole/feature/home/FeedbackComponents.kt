package com.leonardogalvao.pontocontrole.feature.home

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class AttendanceProgressStage {
    VALIDATING_LOCATION,
    RECORDING,
}

@Composable
internal fun AttendanceProgressCard(
    stage: AttendanceProgressStage,
    modifier: Modifier = Modifier,
) {
    val title = when (stage) {
        AttendanceProgressStage.VALIDATING_LOCATION -> "Validando localização"
        AttendanceProgressStage.RECORDING -> "Registrando ponto"
    }
    val detail = when (stage) {
        AttendanceProgressStage.VALIDATING_LOCATION ->
            "Conferindo a precisão do GPS e preparando a evidência."
        AttendanceProgressStage.RECORDING ->
            "Enviando foto e localização para confirmação do servidor."
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                strokeWidth = 3.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun AttendanceSuccessCard(
    confirmation: AttendanceConfirmation,
    subtitle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val animation = remember(confirmation.id) { Animatable(0f) }
    val successColor = MaterialTheme.colorScheme.primary
    val title = if (confirmation.type == "ENTRADA") {
        "Entrada confirmada"
    } else {
        "Saída confirmada"
    }

    LaunchedEffect(confirmation.id) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            },
        )
        val tone = runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, CONFIRMATION_VOLUME)
        }.getOrNull()
        try {
            tone?.startTone(ToneGenerator.TONE_PROP_ACK, CONFIRMATION_TONE_MILLIS)
            animation.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        } finally {
            tone?.release()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animation.value.coerceAtLeast(0.15f)
                scaleX = 0.94f + (0.06f * animation.value)
                scaleY = 0.94f + (0.06f * animation.value)
            },
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(modifier = Modifier.size(48.dp)) {
                val progress = animation.value
                drawCircle(
                    color = successColor,
                    radius = (size.minDimension / 2f) * progress,
                    style = Stroke(width = 4.dp.toPx()),
                )
                if (progress > 0.45f) {
                    val checkProgress = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
                    val start = Offset(size.width * 0.25f, size.height * 0.52f)
                    val middle = Offset(size.width * 0.43f, size.height * 0.70f)
                    val end = Offset(size.width * 0.76f, size.height * 0.34f)
                    if (checkProgress <= 0.45f) {
                        drawLine(
                            color = successColor,
                            start = start,
                            end = Offset(
                                start.x + ((middle.x - start.x) * (checkProgress / 0.45f)),
                                start.y + ((middle.y - start.y) * (checkProgress / 0.45f)),
                            ),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    } else {
                        drawLine(
                            color = successColor,
                            start = start,
                            end = middle,
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        val secondProgress = (checkProgress - 0.45f) / 0.55f
                        drawLine(
                            color = successColor,
                            start = middle,
                            end = Offset(
                                middle.x + ((end.x - middle.x) * secondProgress),
                                middle.y + ((end.y - middle.y) * secondProgress),
                            ),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Confirmação recebida do servidor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    }
}

@Composable
internal fun RefreshIndicator(
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "refresh")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "refreshRotation",
    )
    Text(
        text = "↻",
        modifier = modifier.graphicsLayer { rotationZ = if (isRefreshing) rotation else 0f },
        style = MaterialTheme.typography.titleMedium,
    )
}

private const val CONFIRMATION_VOLUME = 70
private const val CONFIRMATION_TONE_MILLIS = 180
