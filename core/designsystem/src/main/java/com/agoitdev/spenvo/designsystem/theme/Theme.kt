package com.agoitdev.spenvo.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ColorMode { BRAND, DYNAMIC }

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary, onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer, onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary, onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer, onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary, onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer, onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground, onBackground = DarkOnBackground,
    surface = DarkSurface, onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkSurfaceTint,
    inverseSurface = DarkInverseSurface, inverseOnSurface = DarkInverseOnSurface,
    error = DarkError, onError = DarkOnError,
    errorContainer = DarkErrorContainer, onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline, outlineVariant = DarkOutlineVariant, scrim = DarkScrim,
    surfaceBright = DarkSurfaceBright, surfaceDim = DarkSurfaceDim,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    primaryFixed = DarkPrimaryFixed, primaryFixedDim = DarkPrimaryFixedDim,
    onPrimaryFixed = DarkOnPrimaryFixed, onPrimaryFixedVariant = DarkOnPrimaryFixedVariant,
    secondaryFixed = DarkSecondaryFixed, secondaryFixedDim = DarkSecondaryFixedDim,
    onSecondaryFixed = DarkOnSecondaryFixed, onSecondaryFixedVariant = DarkOnSecondaryFixedVariant,
    tertiaryFixed = DarkTertiaryFixed, tertiaryFixedDim = DarkTertiaryFixedDim,
    onTertiaryFixed = DarkOnTertiaryFixed, onTertiaryFixedVariant = DarkOnTertiaryFixedVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary, onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer, onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary, onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer, onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary, onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer, onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground, onBackground = LightOnBackground,
    surface = LightSurface, onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant, onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightSurfaceTint,
    inverseSurface = LightInverseSurface, inverseOnSurface = LightInverseOnSurface,
    error = LightError, onError = LightOnError,
    errorContainer = LightErrorContainer, onErrorContainer = LightOnErrorContainer,
    outline = LightOutline, outlineVariant = LightOutlineVariant, scrim = LightScrim,
    surfaceBright = LightSurfaceBright, surfaceDim = LightSurfaceDim,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    primaryFixed = LightPrimaryFixed, primaryFixedDim = LightPrimaryFixedDim,
    onPrimaryFixed = LightOnPrimaryFixed, onPrimaryFixedVariant = LightOnPrimaryFixedVariant,
    secondaryFixed = LightSecondaryFixed, secondaryFixedDim = LightSecondaryFixedDim,
    onSecondaryFixed = LightOnSecondaryFixed, onSecondaryFixedVariant = LightOnSecondaryFixedVariant,
    tertiaryFixed = LightTertiaryFixed, tertiaryFixedDim = LightTertiaryFixedDim,
    onTertiaryFixed = LightOnTertiaryFixed, onTertiaryFixedVariant = LightOnTertiaryFixedVariant,
)

@Composable
fun SpenvoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorMode: ColorMode = ColorMode.BRAND,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (
        colorMode == ColorMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        val context = LocalContext.current
        if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (useDarkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }
    val extendedColors = if (useDarkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalSpenvoExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SpenvoTypography,
            shapes = SpenvoShapes,
            content = content,
        )
    }
}
