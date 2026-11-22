package com.leonardogalvao.pontocontrole.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.leonardogalvao.pontocontrole.feature.home.HomeRoute

@Composable
fun PontoControleNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppDestination.HOME,
    ) {
        composable(AppDestination.HOME) {
            HomeRoute()
        }
    }
}
