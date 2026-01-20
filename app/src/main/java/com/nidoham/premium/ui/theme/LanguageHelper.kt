package com.nidoham.premium.ui.language

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

/**
 * Extension property to create a DataStore instance for language preferences.
 * The DataStore is created lazily and cached at the application context level.
 */
private val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "language_preferences"
)

/**
 * Comprehensive language management utility for multi-language Android applications.
 *
 * This helper class provides robust language preference management with support for
 * both traditional View-based applications and modern Jetpack Compose applications.
 * The implementation handles language selection, persistence, and runtime application
 * across all Android versions from API 24 and above, utilizing the appropriate APIs
 * for each Android version to ensure consistent behavior.
 *
 * The class manages language preferences through DataStore for persistence and integrates
 * with the Android framework's locale management system to apply language changes
 * application-wide. It supports both system default language and explicit user-selected
 * languages, providing a complete solution for internationalization requirements.
 */
class LanguageHelper(private val context: Context) {

    /**
     * Represents a supported language configuration within the application.
     *
     * This data class encapsulates all necessary information about a language option,
     * including its locale identifier, display name for the user interface, and the
     * native name as it appears in the language itself. This comprehensive representation
     * enables proper language selection interfaces and locale management throughout
     * the application lifecycle.
     *
     * @property code The ISO 639-1 language code, optionally including country code.
     * @property displayName The localized display name of the language in the current locale.
     * @property nativeName The name of the language in its own writing system.
     */
    data class Language(
        val code: String,
        val displayName: String,
        val nativeName: String
    ) {
        /**
         * Converts the language code to a Locale object for Android framework usage.
         *
         * This method handles both simple language codes and language-country combinations,
         * properly constructing the appropriate Locale instance for each case.
         *
         * @return A Locale object representing this language configuration.
         */
        fun toLocale(): Locale {
            val parts = code.split("-")
            return if (parts.size == 2) {
                Locale(parts[0], parts[1])
            } else {
                Locale(parts[0])
            }
        }

        companion object {
            /**
             * Constant representing the system default language option.
             *
             * When this language is selected, the application will follow the device's
             * system language settings rather than enforcing a specific language choice.
             */
            val SYSTEM_DEFAULT = Language(
                code = "system",
                displayName = "System Default",
                nativeName = "System Default"
            )
        }
    }

    private companion object {
        val LANGUAGE_CODE_KEY = stringPreferencesKey("selected_language_code")
    }

    /**
     * Flow representing the currently selected language configuration.
     *
     * This Flow emits the persisted language preference and automatically updates
     * when the language selection changes. Composable functions can collect this
     * Flow to react to language changes and update their user interface accordingly.
     */
    val selectedLanguageFlow: Flow<Language> = context.languageDataStore.data.map { preferences ->
        val code = preferences[LANGUAGE_CODE_KEY] ?: Language.SYSTEM_DEFAULT.code
        getLanguageByCode(code)
    }

    /**
     * Retrieves a complete list of all languages supported by the application.
     *
     * This method returns a predefined list of supported languages, each with
     * localized display names and native names. Applications should customize this
     * list to include only the languages for which they have proper translations.
     * The system default option is always included as the first element.
     *
     * @return A list of all available language configurations including system default.
     */
    fun getSupportedLanguages(): List<Language> {
        return listOf(
            Language.SYSTEM_DEFAULT,
            Language(
                code = "en",
                displayName = context.getString(android.R.string.cancel).let { "English" },
                nativeName = "English"
            ),
            Language(
                code = "es",
                displayName = "Spanish",
                nativeName = "Español"
            ),
            Language(
                code = "fr",
                displayName = "French",
                nativeName = "Français"
            ),
            Language(
                code = "de",
                displayName = "German",
                nativeName = "Deutsch"
            ),
            Language(
                code = "it",
                displayName = "Italian",
                nativeName = "Italiano"
            ),
            Language(
                code = "pt",
                displayName = "Portuguese",
                nativeName = "Português"
            ),
            Language(
                code = "ru",
                displayName = "Russian",
                nativeName = "Русский"
            ),
            Language(
                code = "ja",
                displayName = "Japanese",
                nativeName = "日本語"
            ),
            Language(
                code = "ko",
                displayName = "Korean",
                nativeName = "한국어"
            ),
            Language(
                code = "zh",
                displayName = "Chinese",
                nativeName = "中文"
            ),
            Language(
                code = "ar",
                displayName = "Arabic",
                nativeName = "العربية"
            ),
            Language(
                code = "hi",
                displayName = "Hindi",
                nativeName = "हिन्दी"
            ),
            Language(
                code = "bn",
                displayName = "Bengali",
                nativeName = "বাংলা"
            )
        )
    }

    /**
     * Retrieves a Language object corresponding to the specified language code.
     *
     * This method searches the supported languages list for a matching code and
     * returns the corresponding Language object. If no match is found, the system
     * default language is returned to ensure consistent behavior.
     *
     * @param code The language code to search for.
     * @return The matching Language object or system default if not found.
     */
    private fun getLanguageByCode(code: String): Language {
        return getSupportedLanguages().find { it.code == code } ?: Language.SYSTEM_DEFAULT
    }

    /**
     * Applies the specified language configuration to the application.
     *
     * This method persists the language selection to DataStore and immediately applies
     * the language change throughout the application. On Android 13 and above, it uses
     * the modern per-app language preferences API. For older Android versions, it uses
     * AppCompatDelegate to manage locale configuration. The method handles all necessary
     * system integration to ensure the language change takes effect properly.
     *
     * Note: For a complete language change experience, activities should be recreated
     * after calling this method to ensure all UI elements reflect the new language.
     *
     * @param language The language configuration to apply.
     */
    suspend fun setLanguage(language: Language) {
        context.languageDataStore.edit { preferences ->
            preferences[LANGUAGE_CODE_KEY] = language.code
        }

        if (language.code == Language.SYSTEM_DEFAULT.code) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(android.app.LocaleManager::class.java)
                    ?.applicationLocales = LocaleList.getEmptyLocaleList()
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
        } else {
            val locale = language.toLocale()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(android.app.LocaleManager::class.java)
                    ?.applicationLocales = LocaleList(locale)
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(locale))
            }
        }
    }

    /**
     * Applies the persisted language preference during application initialization.
     *
     * This method should be called during application startup, typically in the
     * Application class onCreate method, to ensure the saved language preference
     * is applied before any activities are created. This guarantees that the entire
     * application lifecycle respects the user's language choice from the moment
     * the application launches.
     */
    suspend fun applyStoredLanguage() {
        selectedLanguageFlow.collect { language ->
            if (language.code != Language.SYSTEM_DEFAULT.code) {
                val locale = language.toLocale()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.getSystemService(android.app.LocaleManager::class.java)
                        ?.applicationLocales = LocaleList(locale)
                } else {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(locale))
                }
            }
        }
    }

    /**
     * Retrieves the current application locale configuration.
     *
     * This method examines the current locale settings and returns a Language object
     * representing the active language. It properly handles both system default and
     * explicitly set languages, providing accurate information about the current
     * language state regardless of how it was configured.
     *
     * @return The currently active language configuration.
     */
    fun getCurrentLanguage(): Language {
        val currentLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(android.app.LocaleManager::class.java)
                ?.applicationLocales?.get(0)
        } else {
            AppCompatDelegate.getApplicationLocales()[0]
        } ?: Locale.getDefault()

        val languageCode = if (currentLocale.country.isNotEmpty()) {
            "${currentLocale.language}-${currentLocale.country}"
        } else {
            currentLocale.language
        }

        return getLanguageByCode(languageCode)
    }

    /**
     * Creates a new Context with the specified locale applied.
     *
     * This method generates a Context wrapper that enforces a specific locale,
     * useful for scenarios where you need to display content in a different language
     * than the application's current language setting. This is particularly valuable
     * for preview screens or multi-language content displays.
     *
     * @param locale The locale to apply to the new context.
     * @return A new Context instance with the specified locale configuration.
     */
    fun createContextWithLocale(locale: Locale): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }
}

/**
 * Extension function to simplify LanguageHelper instantiation.
 *
 * This extension provides convenient access to a LanguageHelper instance from
 * any Context, promoting cleaner code and consistent language management
 * throughout the application architecture.
 *
 * @return A new LanguageHelper instance bound to this context.
 */
fun Context.languageHelper(): LanguageHelper = LanguageHelper(this)

/**
 * Composable function to observe the current language selection.
 *
 * This function collects the language preference from DataStore and provides
 * it as a State object that composables can observe. The UI will automatically
 * recompose when the language preference changes, ensuring the interface remains
 * synchronized with user selections.
 *
 * Usage example in composables:
 * ```
 * val currentLanguage = rememberCurrentLanguage(context)
 * Text(text = "Selected: ${currentLanguage.displayName}")
 * ```
 *
 * @param context The context used to access language preferences.
 * @return The currently selected language configuration as observable state.
 */
@Composable
fun rememberCurrentLanguage(context: Context): LanguageHelper.Language {
    val languageHelper = context.languageHelper()
    val language by languageHelper.selectedLanguageFlow.collectAsState(
        initial = LanguageHelper.Language.SYSTEM_DEFAULT
    )
    return language
}