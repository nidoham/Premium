package com.nidoham.premium.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Primary application theme composable implementing Material Design 3 theming system.
 *
 * Provides comprehensive theme configuration with support for dark mode, dynamic colors
 * (Android 12+), and system UI customization compatible with Android 8.0 (API 26) and above.
 *
 * @param darkTheme Whether to apply dark theme colors. Defaults to system preference.
 * @param dynamicColor Whether to use Material You dynamic colors from wallpaper (Android 12+).
 *                     Defaults to false to maintain consistent brand identity.
 * @param content The composable content to be themed.
 */
@Composable
fun PremiumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)

            // Configure status bar appearance
            window.statusBarColor = colorScheme.primary.toArgb()

            // Configure navigation bar (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.navigationBarColor = colorScheme.surface.toArgb()

                // Android 8.1+ supports light navigation bar
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    insetsController.isAppearanceLightNavigationBars = !darkTheme
                }
            }

            // Configure status bar icon colors (Android 6.0+ but verified for 8.0+)
            insetsController.isAppearanceLightStatusBars = !darkTheme

            // Android 10+ edge-to-edge with gesture navigation support
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }

            // Android 11+ enhanced window insets for proper keyboard handling
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Alternative theme configuration with explicit edge-to-edge support.
 *
 * This variant is optimized for modern Android applications requiring full-screen
 * immersive experiences with proper system bar handling across different Android versions.
 *
 * @param darkTheme Whether to apply dark theme colors. Defaults to system preference.
 * @param dynamicColor Whether to use Material You dynamic colors (Android 12+).
 * @param transparentSystemBars Whether to render transparent system bars for edge-to-edge layout.
 * @param content The composable content to be themed.
 */
@Composable
fun PremiumThemeEdgeToEdge(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    transparentSystemBars: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)

            if (transparentSystemBars) {
                window.statusBarColor = Color.Transparent.toArgb()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    window.navigationBarColor = Color.Transparent.toArgb()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
            } else {
                window.statusBarColor = colorScheme.primary.toArgb()

                window.navigationBarColor = colorScheme.surface.toArgb()
            }

            insetsController.isAppearanceLightStatusBars = !darkTheme

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}