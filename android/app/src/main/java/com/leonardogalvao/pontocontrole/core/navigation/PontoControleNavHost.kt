package com.leonardogalvao.pontocontrole.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.leonardogalvao.pontocontrole.core.session.SessionStore
import com.leonardogalvao.pontocontrole.feature.home.HomeRoute
import com.leonardogalvao.pontocontrole.feature.login.LoginRoute
import com.leonardogalvao.pontocontrole.feature.login.domain.AuthRepository
import com.leonardogalvao.pontocontrole.feature.passwordchange.PasswordChangeRoute
import kotlinx.coroutines.launch

@Composable
fun PontoControleNavHost(
    startDestination: AppDestination,
    authRepository: AuthRepository,
    sessionStore: SessionStore,
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = startDestination.route,
    ) {
        composable(AppDestination.LOGIN.route) {
            LoginRoute(
                authRepository = authRepository,
                sessionStore = sessionStore,
                onAuthenticated = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(AppDestination.LOGIN.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(AppDestination.HOME.route) {
            HomeRoute(
                onLogout = {
                    coroutineScope.launch {
                        sessionStore.clear()
                        navController.navigate(AppDestination.LOGIN.route) {
                            popUpTo(AppDestination.HOME.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        composable(AppDestination.PASSWORD_CHANGE.route) {
            PasswordChangeRoute(
                authRepository = authRepository,
                onFinished = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(AppDestination.PASSWORD_CHANGE.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
