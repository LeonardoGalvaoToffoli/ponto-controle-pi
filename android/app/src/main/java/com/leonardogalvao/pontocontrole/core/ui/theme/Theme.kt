package com.leonardogalvao.pontocontrole.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = CorporateBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = CorporateBlueLight,
    onPrimaryContainer = CorporateNavy,
    background = NeutralBackground,
    surface = androidx.compose.ui.graphics.Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = CorporateBlueDark,
    primaryContainer = CorporateBlue,
    background = NeutralBackgroundDark,
)

@Composable
fun PontoControleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = PontoControleTypography,
        content = content,
    )
}
