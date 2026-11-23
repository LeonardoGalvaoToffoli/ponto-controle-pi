package com.leonardogalvao.pontocontrole.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leonardogalvao.pontocontrole.PontoControleApplication
import com.leonardogalvao.pontocontrole.R
import com.leonardogalvao.pontocontrole.core.navigation.PontoControleNavHost
import com.leonardogalvao.pontocontrole.core.ui.theme.PontoControleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PontoControleApp() {
    val application = LocalContext.current.applicationContext as PontoControleApplication
    val container = application.container
    val appViewModel: AppViewModel = viewModel(factory = AppViewModel.factory(container.sessionStore))
    val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
    var splashComplete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MILLIS)
        splashComplete = true
    }

    PontoControleTheme {
        val state = uiState
        if (!splashComplete || state is AppUiState.Loading) {
            AppLoadingScreen()
        } else {
            state as AppUiState.Ready
            PontoControleNavHost(
                startDestination = state.startDestination,
                authRepository = container.authRepository,
                workRepository = container.workRepository,
                sessionStore = container.sessionStore,
            )
        }
    }
}

@Composable
private fun AppLoadingScreen() {
    val opacity = remember { Animatable(0f) }
    val scale = remember { Animatable(0.78f) }
    val verticalOffset = remember { Animatable(18f) }

    LaunchedEffect(Unit) {
        launch { opacity.animateTo(1f, tween(360)) }
        launch { verticalOffset.animateTo(0f, tween(620, easing = FastOutSlowInEasing)) }
        scale.animateTo(1.06f, tween(480, easing = FastOutSlowInEasing))
        scale.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .alpha(opacity.value)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        translationY = verticalOffset.value
                    }
                    .size(104.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_time_clock_logo),
                    contentDescription = "Logo Controle de Ponto",
                    modifier = Modifier.size(70.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Controle de Ponto",
                modifier = Modifier.alpha(opacity.value),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private const val SPLASH_DURATION_MILLIS = 1_050L
