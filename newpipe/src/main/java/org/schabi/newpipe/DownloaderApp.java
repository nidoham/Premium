package org.schabi.newpipe;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.util.Localization;

import org.ocpsoft.prettytime.PrettyTime;

import java.util.Objects;

/**
 * Professional downloader application initializer with:
 * - NewPipe extractor configuration
 * - Localization setup
 * - Cookie management
 * - Comprehensive error handling
 * - Thread-safe initialization
 */
public class DownloaderApp {
    private static final String TAG = "DownloaderApp";
    
    private final Context appContext;
    private volatile boolean isInitialized = false;
    private final Object initLock = new Object();

    /**
     * Create a new DownloaderApp instance.
     * 
     * @param context Application context (will be converted to app context automatically)
     * @throws NullPointerException if context is null
     */
    public DownloaderApp(@NonNull final Context context) {
        Objects.requireNonNull(context, "Context cannot be null");
        
        this.appContext = context.getApplicationContext();
        
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "DownloaderApp created with context: " + appContext.getPackageName());
        }
    }

    /**
     * Initialize the downloader system. This should be called once during app startup.
     * Thread-safe - multiple calls will only initialize once.
     * 
     * @return true if initialization was successful, false if already initialized
     */
    public boolean initialize() {
        if (isInitialized) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Already initialized, skipping");
            }
            return false;
        }

        synchronized (initLock) {
            if (isInitialized) {
                return false;
            }

            try {
                // Step 1: Initialize Localization system
                initializeLocalization();

                // Step 2: Initialize Downloader
                final Downloader downloader = getDownloader();

                // Step 3: Initialize NewPipe extractor with downloader and localization
                initializeNewPipe(downloader);

                // Step 4: Initialize PrettyTime for relative time formatting
                initializePrettyTime();

                isInitialized = true;
                
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "DownloaderApp initialized successfully");
                }
                
                return true;

            } catch (final Exception e) {
                Log.e(TAG, "Failed to initialize DownloaderApp", e);
                return false;
            }
        }
    }

    /**
     * Initialize the localization system.
     * Sets up locale preferences and migrations.
     */
    private void initializeLocalization() {
        try {
            // Migrate legacy app language settings if necessary
            Localization.migrateAppLanguageSettingIfNecessary(appContext);
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Localization initialized - Locale: " + 
                        Localization.getAppLocale());
            }
        } catch (final Exception e) {
            Log.e(TAG, "Failed to initialize localization, using defaults", e);
        }
    }

    /**
     * Initialize NewPipe extractor with downloader and localization settings.
     * 
     * @param downloader the downloader instance to use
     */
    private void initializeNewPipe(@NonNull final Downloader downloader) {
        try {
            NewPipe.init(
                downloader,
                Localization.getPreferredLocalization(appContext),
                Localization.getPreferredContentCountry(appContext)
            );
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "NewPipe initialized - Localization: " + 
                        Localization.getPreferredLocalization(appContext) + 
                        ", Country: " + 
                        Localization.getPreferredContentCountry(appContext));
            }
        } catch (final Exception e) {
            Log.e(TAG, "Failed to initialize NewPipe", e);
            throw new RuntimeException("NewPipe initialization failed", e);
        }
    }

    /**
     * Initialize PrettyTime for relative time formatting.
     */
    private void initializePrettyTime() {
        try {
            final PrettyTime prettyTime = Localization.resolvePrettyTime();
            Localization.initPrettyTime(prettyTime);
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "PrettyTime initialized with locale: " + 
                        Localization.getAppLocale());
            }
        } catch (final Exception e) {
            Log.e(TAG, "Failed to initialize PrettyTime, using defaults", e);
        }
    }

    /**
     * Get or create the downloader instance with proper configuration.
     * 
     * @return configured Downloader instance
     */
    @NonNull
    protected Downloader getDownloader() {
        try {
            // Initialize DownloaderImpl with application context for proper User-Agent
            final DownloaderImpl downloader = DownloaderImpl.init(appContext, null);
            
            // Configure cookies from preferences
            setCookiesToDownloader(downloader, appContext);
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Downloader initialized with User-Agent: " + 
                        DownloaderImpl.USER_AGENT);
            }
            
            return downloader;
            
        } catch (final Exception e) {
            Log.e(TAG, "Failed to initialize downloader", e);
            throw new RuntimeException("Downloader initialization failed", e);
        }
    }

    /**
     * Configure cookies for the downloader from SharedPreferences.
     * Loads reCAPTCHA cookies and YouTube restricted mode settings.
     * 
     * @param downloader the downloader instance to configure
     * @param context    the application context
     */
    protected void setCookiesToDownloader(@NonNull final DownloaderImpl downloader, 
                                         @NonNull final Context context) {
        Objects.requireNonNull(downloader, "Downloader cannot be null");
        Objects.requireNonNull(context, "Context cannot be null");
        
        try {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            
            // Load reCAPTCHA cookies
            loadReCaptchaCookies(downloader, prefs, context);
            
            // Load YouTube restricted mode cookies
            loadYouTubeRestrictedModeCookies(downloader, context);
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Cookies configured for downloader");
            }
            
        } catch (final Exception e) {
            Log.e(TAG, "Failed to set cookies to downloader", e);
        }
    }

    /**
     * Load reCAPTCHA cookies from SharedPreferences.
     * 
     * @param downloader the downloader instance
     * @param prefs      SharedPreferences instance
     * @param context    application context
     */
    private void loadReCaptchaCookies(@NonNull final DownloaderImpl downloader,
                                     @NonNull final SharedPreferences prefs,
                                     @NonNull final Context context) {
        try {
            final String recaptchaKey = context.getString(R.string.recaptcha_cookies_key);
            final String recaptchaCookies = prefs.getString(recaptchaKey, null);
            
            if (recaptchaCookies != null && !recaptchaCookies.isEmpty()) {
                downloader.setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, recaptchaCookies);
                
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "reCAPTCHA cookies loaded");
                }
            } else if (BuildConfig.DEBUG) {
                Log.d(TAG, "No reCAPTCHA cookies found");
            }
            
        } catch (final Exception e) {
            Log.e(TAG, "Failed to load reCAPTCHA cookies", e);
        }
    }

    /**
     * Load YouTube restricted mode cookies based on user preferences.
     * 
     * @param downloader the downloader instance
     * @param context    application context
     */
    private void loadYouTubeRestrictedModeCookies(@NonNull final DownloaderImpl downloader,
                                                  @NonNull final Context context) {
        try {
            downloader.updateYoutubeRestrictedModeCookies(context);
            
            if (BuildConfig.DEBUG) {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                final String restrictedModeKey = context.getString(R.string.youtube_restricted_mode_enabled);
                final boolean enabled = prefs.getBoolean(restrictedModeKey, false);
                Log.d(TAG, "YouTube restricted mode: " + (enabled ? "enabled" : "disabled"));
            }
            
        } catch (final Exception e) {
            Log.e(TAG, "Failed to load YouTube restricted mode cookies", e);
        }
    }

    /**
     * Check if the downloader system is initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Get the application context used by this instance.
     * 
     * @return application context
     */
    @NonNull
    public Context getAppContext() {
        return appContext;
    }

    /**
     * Refresh cookies from current preferences.
     * Useful when user changes settings without restarting the app.
     */
    public void refreshCookies() {
        if (!isInitialized) {
            Log.w(TAG, "Cannot refresh cookies - not initialized");
            return;
        }

        try {
            final DownloaderImpl downloader = DownloaderImpl.getInstance();
            setCookiesToDownloader(downloader, appContext);
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Cookies refreshed from preferences");
            }
            
        } catch (final Exception e) {
            Log.e(TAG, "Failed to refresh cookies", e);
        }
    }

    /**
     * Refresh localization settings.
     * Useful when user changes language settings without restarting the app.
     */
    public void refreshLocalization() {
        if (!isInitialized) {
            Log.w(TAG, "Cannot refresh localization - not initialized");
            return;
        }

        try {
            // Reinitialize NewPipe with new localization
            final Downloader downloader = DownloaderImpl.getInstance();
            NewPipe.init(
                downloader,
                Localization.getPreferredLocalization(appContext),
                Localization.getPreferredContentCountry(appContext)
            );
            
            // Reinitialize PrettyTime
            final PrettyTime prettyTime = Localization.resolvePrettyTime();
            Localization.initPrettyTime(prettyTime);
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Localization refreshed");
            }
            
        } catch (final Exception e) {
            Log.e(TAG, "Failed to refresh localization", e);
        }
    }

    /**
     * Cleanup resources. Should be called when the app is shutting down.
     */
    public void cleanup() {
        synchronized (initLock) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Cleaning up DownloaderApp resources");
            }
            
            // Clear any cached data if needed
            isInitialized = false;
        }
    }
}