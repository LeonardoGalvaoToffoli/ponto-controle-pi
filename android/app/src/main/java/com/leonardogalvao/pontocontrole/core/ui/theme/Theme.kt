package com.leonardogalvao.pontocontrole.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = CorporateBlue,
    onPrimary = Color.White,
    primaryContainer = CorporateBlueLight,
    onPrimaryContainer = CorporateNavy,
    secondary = SlateTeal,
    onSecondary = Color.White,
    secondaryContainer = SlateTealLight,
    onSecondaryContainer = SlateTealDeep,
    tertiary = AccentAmber,
    onTertiary = Color.White,
    tertiaryContainer = AccentAmberLight,
    onTertiaryContainer = AccentAmberDeep,
    background = NeutralBackground,
    onBackground = CorporateNavy,
    surface = Color.White,
    onSurface = CorporateNavy,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = CorporateBlueDark,
    onPrimary = CorporateNavy,
    primaryContainer = CorporateBlue,
    onPrimaryContainer = CorporateBlueLight,
    secondary = SlateTealDark,
    onSecondary = SlateTealDeep,
    secondaryContainer = SlateTeal,
    onSecondaryContainer = SlateTealLight,
    tertiary = AccentAmberDark,
    onTertiary = AccentAmberDeep,
    tertiaryContainer = AccentAmber,
    onTertiaryContainer = AccentAmberLight,
    background = NeutralBackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)

@Composable
fun PontoControleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = PontoControleTypography,
        shapes = PontoControleShapes,
        content = content,
    )
}
