package com.nidoham.premium.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Light theme color scheme following Material Design 3 guidelines.
 * Primary color: Bright Blue (#3EBBF5) - represents brand identity
 */
private val LightPrimary = Color(0xFF3EBBF5)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFD1EFFF)
private val LightOnPrimaryContainer = Color(0xFF001F2A)

private val LightSecondary = Color(0xFF4A6572)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFCDE5F9)
private val LightOnSecondaryContainer = Color(0xFF051F2C)

private val LightTertiary = Color(0xFF5B5D71)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFE0E1F9)
private val LightOnTertiaryContainer = Color(0xFF181A2B)

private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)

private val LightBackground = Color(0xFFFCFCFF)
private val LightOnBackground = Color(0xFF1A1C1E)
private val LightSurface = Color(0xFFFCFCFF)
private val LightOnSurface = Color(0xFF1A1C1E)
private val LightSurfaceVariant = Color(0xFFDDE3EA)
private val LightOnSurfaceVariant = Color(0xFF41474D)
private val LightSurfaceTint = LightPrimary

private val LightOutline = Color(0xFF71787E)
private val LightOutlineVariant = Color(0xFFC1C7CE)
private val LightScrim = Color(0xFF000000)

private val LightInverseSurface = Color(0xFF2E3135)
private val LightInverseOnSurface = Color(0xFFF0F0F3)
private val LightInversePrimary = Color(0xFF7DD0FF)

private val LightSurfaceDim = Color(0xFFD9D9DC)
private val LightSurfaceBright = Color(0xFFFCFCFF)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF3F3F6)
private val LightSurfaceContainer = Color(0xFFEDEDF0)
private val LightSurfaceContainerHigh = Color(0xFFE7E8EB)
private val LightSurfaceContainerHighest = Color(0xFFE2E2E5)

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightSurfaceTint,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest
)

/**
 * Dark theme color scheme following Material Design 3 guidelines.
 * Maintains brand consistency while optimized for reduced eye strain in low-light conditions
 */
private val DarkPrimary = Color(0xFF7DD0FF)
private val DarkOnPrimary = Color(0xFF003546)
private val DarkPrimaryContainer = Color(0xFF004D63)
private val DarkOnPrimaryContainer = Color(0xFFB8E8FF)

private val DarkSecondary = Color(0xFFB1CADC)
private val DarkOnSecondary = Color(0xFF1C3441)
private val DarkSecondaryContainer = Color(0xFF324B58)
private val DarkOnSecondaryContainer = Color(0xFFCDE5F9)

private val DarkTertiary = Color(0xFFC3C5DD)
private val DarkOnTertiary = Color(0xFF2C2F41)
private val DarkTertiaryContainer = Color(0xFF434658)
private val DarkOnTertiaryContainer = Color(0xFFE0E1F9)

private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)

private val DarkBackground = Color(0xFF1A1C1E)
private val DarkOnBackground = Color(0xFFE2E2E5)
private val DarkSurface = Color(0xFF1A1C1E)
private val DarkOnSurface = Color(0xFFE2E2E5)
private val DarkSurfaceVariant = Color(0xFF41474D)
private val DarkOnSurfaceVariant = Color(0xFFC1C7CE)
private val DarkSurfaceTint = DarkPrimary

private val DarkOutline = Color(0xFF8B9198)
private val DarkOutlineVariant = Color(0xFF41474D)
private val DarkScrim = Color(0xFF000000)

private val DarkInverseSurface = Color(0xFFE2E2E5)
private val DarkInverseOnSurface = Color(0xFF2E3135)
private val DarkInversePrimary = Color(0xFF00668A)

private val DarkSurfaceDim = Color(0xFF1A1C1E)
private val DarkSurfaceBright = Color(0xFF403F44)
private val DarkSurfaceContainerLowest = Color(0xFF0F1113)
private val DarkSurfaceContainerLow = Color(0xFF1A1C1E)
private val DarkSurfaceContainer = Color(0xFF1E2022)
private val DarkSurfaceContainerHigh = Color(0xFF282A2D)
private val DarkSurfaceContainerHighest = Color(0xFF333538)

val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkSurfaceTint,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest
)