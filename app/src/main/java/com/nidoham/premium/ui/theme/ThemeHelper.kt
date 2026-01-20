package com.nidoham.premium.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Extension property to create a DataStore instance for theme preferences.
 * The DataStore is created lazily and cached at the application context level.
 */
private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "theme_preferences"
)

/**
 * Centralized theme management utility for controlling application theme modes in Jetpack Compose.
 *
 * This helper class provides a comprehensive solution for managing theme preferences
 * in Material3 Compose applications, supporting day mode, night mode, and automatic
 * system-based theme selection. The implementation uses DataStore for persistent storage
 * and provides reactive Flow-based access to theme preferences optimized for composables.
 *
 * Unlike traditional View-based theme management, this implementation is designed
 * specifically for Compose applications and does not require AppCompat dependencies.
 */
class ThemeHelper(private val context: Context) {

    /**
     * Enumeration of available theme modes for the application.
     *
     * Each mode provides a clear semantic meaning for theme preferences and can be
     * easily serialized to DataStore for persistent storage.
     */
    enum class ThemeMode(val value: String) {
        /**
         * Forces the application to use light theme regardless of system settings.
         */
        DAY("day"),

        /**
         * Forces the application to use dark theme regardless of system settings.
         */
        NIGHT("night"),

        /**
         * Automatically follows the system theme preference.
         * This mode respects the system-wide dark mode setting configured by the user.
         */
        AUTO("auto");

        companion object {
            /**
             * Converts a string value to its corresponding ThemeMode.
             *
             * @param value The string representation of the theme mode.
             * @return The matching ThemeMode, defaulting to AUTO if no match is found.
             */
            fun fromValue(value: String): ThemeMode {
                return values().find { it.value == value } ?: AUTO
            }
        }
    }

    private companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }

    /**
     * Flow representing the current theme mode selection.
     *
     * This Flow emits the persisted theme mode preference and can be collected
     * by composable functions to react to theme changes. The Flow automatically
     * updates when the theme preference is modified through the setThemeMode method.
     */
    val themeModeFlow: Flow<ThemeMode> = context.themeDataStore.data.map { preferences ->
        val modeValue = preferences[THEME_MODE_KEY] ?: ThemeMode.AUTO.value
        ThemeMode.fromValue(modeValue)
    }

    /**
     * Updates the application theme mode preference.
     *
     * This method persists the selected theme mode to DataStore. The change will
     * be reflected immediately in any composables collecting the themeModeFlow.
     *
     * @param mode The desired theme mode to apply.
     */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.value
        }
    }

    /**
     * Retrieves the current theme mode as a Flow for use in composables.
     *
     * This method provides a convenient way to observe theme changes within
     * composable functions, ensuring the UI remains synchronized with user preferences.
     *
     * @return Flow emitting the current theme mode.
     */
    fun observeThemeMode(): Flow<ThemeMode> = themeModeFlow

    /**
     * Toggles between day and night modes.
     *
     * This convenience method switches the theme between DAY and NIGHT modes,
     * useful for implementing quick theme toggle buttons. If the current mode
     * is AUTO, it will switch to DAY mode by default.
     */
    suspend fun toggleTheme() {
        themeModeFlow.map { currentMode ->
            when (currentMode) {
                ThemeMode.DAY -> ThemeMode.NIGHT
                ThemeMode.NIGHT -> ThemeMode.DAY
                ThemeMode.AUTO -> ThemeMode.DAY
            }
        }.collect { newMode ->
            setThemeMode(newMode)
        }
    }

    /**
     * Cycles through all available theme modes in sequence.
     *
     * This method rotates through AUTO, DAY, and NIGHT modes, providing
     * a convenient way to implement multi-state theme selection buttons.
     */
    suspend fun cycleThemeMode() {
        themeModeFlow.map { currentMode ->
            when (currentMode) {
                ThemeMode.AUTO -> ThemeMode.DAY
                ThemeMode.DAY -> ThemeMode.NIGHT
                ThemeMode.NIGHT -> ThemeMode.AUTO
            }
        }.collect { newMode ->
            setThemeMode(newMode)
        }
    }
}

/**
 * Extension function to simplify ThemeHelper instantiation.
 *
 * This extension provides convenient access to a ThemeHelper instance from
 * any Context, promoting cleaner code and easier integration throughout the application.
 *
 * @return A new ThemeHelper instance bound to this context.
 */
fun Context.themeHelper(): ThemeHelper = ThemeHelper(this)

/**
 * Composable function to observe and apply the current theme mode.
 *
 * This function collects the theme mode from DataStore and determines whether
 * dark theme should be active based on the user's preference and system settings.
 *
 * Usage example:
 * ```
 * val shouldUseDarkTheme = rememberThemeMode(context)
 * PremiumTheme(darkTheme = shouldUseDarkTheme) {
 *     // Your app content
 * }
 * ```
 *
 * @param context The context used to access theme preferences.
 * @param systemInDarkTheme The current system dark theme state, typically from isSystemInDarkTheme().
 * @return True if dark theme should be active based on preferences and system state.
 */
@Composable
fun rememberThemeMode(
    context: Context,
    systemInDarkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme()
): Boolean {
    val themeHelper = context.themeHelper()
    val themeMode by themeHelper.themeModeFlow.collectAsState(initial = ThemeHelper.ThemeMode.AUTO)

    return when (themeMode) {
        ThemeHelper.ThemeMode.DAY -> false
        ThemeHelper.ThemeMode.NIGHT -> true
        ThemeHelper.ThemeMode.AUTO -> systemInDarkTheme
    }
}