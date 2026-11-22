package com.leonardogalvao.pontocontrole.app

import androidx.compose.runtime.Composable
import com.leonardogalvao.pontocontrole.core.navigation.PontoControleNavHost
import com.leonardogalvao.pontocontrole.core.ui.theme.PontoControleTheme

@Composable
fun PontoControleApp() {
    PontoControleTheme {
        PontoControleNavHost()
    }
}
