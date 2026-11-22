package com.leonardogalvao.pontocontrole.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leonardogalvao.pontocontrole.PontoControleApplication
import com.leonardogalvao.pontocontrole.core.navigation.PontoControleNavHost
import com.leonardogalvao.pontocontrole.core.ui.theme.PontoControleTheme

@Composable
fun PontoControleApp() {
    val application = LocalContext.current.applicationContext as PontoControleApplication
    val container = application.container
    val appViewModel: AppViewModel = viewModel(factory = AppViewModel.factory(container.sessionStore))
    val uiState by appViewModel.uiState.collectAsStateWithLifecycle()

    PontoControleTheme {
        when (val state = uiState) {
            AppUiState.Loading -> AppLoadingScreen()
            is AppUiState.Ready -> PontoControleNavHost(
                startDestination = state.startDestination,
                authRepository = container.authRepository,
                sessionStore = container.sessionStore,
            )
        }
    }
}

@Composable
private fun AppLoadingScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}
