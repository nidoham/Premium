package com.nidoham.ytpremium;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.DownloaderApp;
import org.schabi.newpipe.util.ServiceHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;

/**
 * Main application class for BongoTube.
 * Handles app-level initialization including:
 * - Downloader setup with proper initialization
 * - Comprehensive crash handling and reporting
 * - RxJava error handling
 * - First run detection
 * - Service initialization
 * 
 * Licensed under GNU General Public License (GPL) version 3 or later.
 */
public class BongoTubeApplication extends Application {
    private static final String TAG = "BongoTubeApplication";
    private static final String PREFS_NAME = "app_preferences";
    private static final String KEY_FIRST_RUN = "key_first_run";
    private static final int MAX_CRASH_REPORTS = 5;
    
    private static volatile BongoTubeApplication instance;
    
    private DownloaderApp downloaderApp;
    private File crashDirectory;
    private Thread.UncaughtExceptionHandler defaultExceptionHandler;
    private boolean isInitialized = false;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Set instance first
        instance = this;
        
        Log.i(TAG, "BongoTube Application starting...");
        
        try {
            // Step 1: Initialize crash directory
            initializeCrashDirectory();
            
            // Step 2: Setup crash handler early
            setupCrashHandler();
            
            // Step 3: Setup RxJava error handler
            setupRxJavaErrorHandler();
            
            // Step 4: Initialize DownloaderApp
            initializeDownloader();
            
            // Step 5: Initialize services
            initializeServices();
            
            isInitialized = true;
            Log.i(TAG, "BongoTube Application initialized successfully");
            
            // Log first run status
            if (isFirstRun()) {
                Log.i(TAG, "First run detected - showing welcome screen");
            }
            
        } catch (final Exception e) {
            Log.e(TAG, "Failed to initialize BongoTube Application", e);
            // Don't crash during initialization - let the app try to run
        }
    }

    /**
     * Initialize crash directory for storing crash reports.
     */
    private void initializeCrashDirectory() {
        try {
            crashDirectory = new File(getFilesDir(), "crashes");
            if (!crashDirectory.exists()) {
                if (crashDirectory.mkdirs()) {
                    Log.d(TAG, "Crash directory created: " + crashDirectory.getAbsolutePath());
                } else {
                    Log.w(TAG, "Failed to create crash directory");
                }
            }
        } catch (final Exception e) {
            Log.e(TAG, "Error creating crash directory", e);
        }
    }

    /**
     * Initialize the downloader system with proper error handling.
     */
    private void initializeDownloader() {
        try {
            Log.d(TAG, "Initializing DownloaderApp...");
            downloaderApp = new DownloaderApp(this);
            
            // IMPORTANT: Call initialize() to actually set up the downloader
            final boolean success = downloaderApp.initialize();
            
            if (success) {
                Log.i(TAG, "DownloaderApp initialized successfully");
            } else {
                Log.w(TAG, "DownloaderApp was already initialized");
            }
            
        } catch (final Exception e) {
            Log.e(TAG, "Failed to initialize DownloaderApp", e);
            throw new RuntimeException("Critical: DownloaderApp initialization failed", e);
        }
    }

    /**
     * Initialize application services.
     */
    private void initializeServices() {
        try {
            Log.d(TAG, "Initializing services...");
            ServiceHelper.initServices(this);
            Log.d(TAG, "Services initialized successfully");
        } catch (final Exception e) {
            Log.e(TAG, "Failed to initialize services", e);
        }
    }

    /**
     * Sets up the custom crash handler for the application.
     * Captures uncaught exceptions and saves crash reports.
     */
    private void setupCrashHandler() {
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                handleCrash(thread, throwable);
            } catch (final Throwable t) {
                Log.e(TAG, "Error in crash handler", t);
            } finally {
                // Always call default handler to ensure proper cleanup
                if (defaultExceptionHandler != null) {
                    defaultExceptionHandler.uncaughtException(thread, throwable);
                } else {
                    // Force exit if no default handler
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
            }
        });
        
        Log.d(TAG, "Crash handler initialized");
    }

    /**
     * Handles crash by saving report and launching debug activity.
     * 
     * @param thread the thread where crash occurred
     * @param throwable the exception that caused the crash
     */
    private void handleCrash(@NonNull final Thread thread, @NonNull final Throwable throwable) {
        Log.e(TAG, "App crashed on thread: " + thread.getName(), throwable);
        
        try {
            final String crashReport = buildCrashReport(thread, throwable);
            saveCrashReport(crashReport);
            launchDebugActivity(crashReport);
        } catch (final Exception e) {
            Log.e(TAG, "Failed to handle crash properly", e);
        }
    }

    /**
     * Builds a comprehensive crash report with device and app information.
     * 
     * @param thread the thread where crash occurred
     * @param throwable the exception that caused the crash
     * @return formatted crash report string
     */
    @NonNull
    private String buildCrashReport(@NonNull final Thread thread, @NonNull final Throwable throwable) {
        final StringBuilder report = new StringBuilder();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        
        report.append("=".repeat(50)).append("\n");
        report.append("       BongoTube Crash Report\n");
        report.append("=".repeat(50)).append("\n\n");
        
        // Timestamp
        report.append("Time: ").append(dateFormat.format(new Date())).append("\n");
        
        // Thread information
        report.append("Thread: ").append(thread.getName())
                .append(" (ID: ").append(thread.getId()).append(")\n");
        
        // App information
        report.append("App Version: ").append(getVersionName()).append("\n");
        report.append("Package: ").append(getPackageName()).append("\n");
        
        // Android information
        report.append("Android: ").append(android.os.Build.VERSION.RELEASE)
                .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        
        // Device information
        report.append("Manufacturer: ").append(android.os.Build.MANUFACTURER).append("\n");
        report.append("Model: ").append(android.os.Build.MODEL).append("\n");
        report.append("Device: ").append(android.os.Build.DEVICE).append("\n");
        
        // Memory information
        final Runtime runtime = Runtime.getRuntime();
        final long maxMemory = runtime.maxMemory() / 1024 / 1024;
        final long totalMemory = runtime.totalMemory() / 1024 / 1024;
        final long freeMemory = runtime.freeMemory() / 1024 / 1024;
        report.append("Memory: ").append(totalMemory - freeMemory).append("/")
                .append(maxMemory).append(" MB\n\n");
        
        // Exception information
        report.append("Exception Type: ").append(throwable.getClass().getName()).append("\n");
        report.append("Message: ").append(
                throwable.getMessage() != null ? throwable.getMessage() : "No message"
        ).append("\n\n");
        
        // Stack trace
        report.append("Stack Trace:\n");
        report.append("-".repeat(50)).append("\n");
        
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        report.append(sw.toString());
        
        // Caused by section
        Throwable cause = throwable.getCause();
        while (cause != null) {
            report.append("\nCaused by: ").append(cause.getClass().getName()).append("\n");
            report.append("Message: ").append(
                    cause.getMessage() != null ? cause.getMessage() : "No message"
            ).append("\n");
            
            final StringWriter causeSw = new StringWriter();
            final PrintWriter causePw = new PrintWriter(causeSw);
            cause.printStackTrace(causePw);
            report.append(causeSw.toString());
            
            cause = cause.getCause();
        }
        
        report.append("\n").append("=".repeat(50)).append("\n");
        
        return report.toString();
    }

    /**
     * Saves crash report to file in the crash directory.
     * 
     * @param crashReport the formatted crash report
     */
    private void saveCrashReport(@NonNull final String crashReport) {
        if (crashDirectory == null || !crashDirectory.exists()) {
            Log.w(TAG, "Crash directory not available");
            return;
        }
        
        try {
            final String fileName = "crash_" + System.currentTimeMillis() + ".txt";
            final File crashFile = new File(crashDirectory, fileName);
            
            try (FileWriter writer = new FileWriter(crashFile)) {
                writer.write(crashReport);
                writer.flush();
            }
            
            Log.i(TAG, "Crash report saved: " + crashFile.getName());
            cleanupOldReports();
            
        } catch (final IOException e) {
            Log.e(TAG, "Failed to save crash report", e);
        }
    }

    /**
     * Removes old crash reports, keeping only the newest MAX_CRASH_REPORTS files.
     */
    private void cleanupOldReports() {
        try {
            if (crashDirectory == null || !crashDirectory.exists()) {
                return;
            }
            
            final File[] files = crashDirectory.listFiles();
            if (files == null || files.length <= MAX_CRASH_REPORTS) {
                return;
            }
            
            // Sort by last modified (oldest first)
            Arrays.sort(files, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
            
            // Delete oldest files
            final int toDelete = files.length - MAX_CRASH_REPORTS;
            for (int i = 0; i < toDelete; i++) {
                if (files[i].delete()) {
                    Log.d(TAG, "Deleted old crash report: " + files[i].getName());
                }
            }
        } catch (final Exception e) {
            Log.e(TAG, "Failed to cleanup crash reports", e);
        }
    }

    /**
     * Launches the debug activity to display crash details to the user.
     * 
     * @param crashReport the crash report to display
     */
    private void launchDebugActivity(@NonNull final String crashReport) {
        try {
            final Intent intent = new Intent(this, DebugActivity.class);
            intent.putExtra("CRASH_REPORT", crashReport);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                    Intent.FLAG_ACTIVITY_CLEAR_TASK |
                    Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        } catch (final Exception e) {
            Log.e(TAG, "Cannot launch DebugActivity", e);
        }
    }

    /**
     * Gets the app version name from package info.
     * 
     * @return version name or "Unknown" if not available
     */
    @NonNull
    private String getVersionName() {
        try {
            return getPackageManager()
                    .getPackageInfo(getPackageName(), 0)
                    .versionName;
        } catch (final Exception e) {
            Log.e(TAG, "Failed to get version name", e);
            return "Unknown";
        }
    }

    /**
     * Handles undeliverable RxJava exceptions globally.
     * Ignores network-related exceptions to prevent unnecessary crashes.
     */
    private void setupRxJavaErrorHandler() {
        RxJavaPlugins.setErrorHandler(e -> {
            Throwable error = e;
            
            // Unwrap UndeliverableException
            if (error instanceof UndeliverableException) {
                error = error.getCause();
            }
            
            // Ignore network and interrupt exceptions
            if (error instanceof IOException || 
                error instanceof SocketException || 
                error instanceof InterruptedException ||
                error instanceof InterruptedIOException) {
                Log.d(TAG, "Ignored RxJava network exception: " + error.getClass().getSimpleName());
                return;
            }
            
            // Ignore exceptions after unsubscribe
            if (error instanceof IllegalStateException && 
                error.getMessage() != null &&
                error.getMessage().contains("disposed")) {
                Log.d(TAG, "Ignored RxJava disposed exception");
                return;
            }
            
            // Log other undeliverable exceptions
            Log.e(TAG, "RxJava undeliverable exception", error);
        });
        
        Log.d(TAG, "RxJava error handler initialized");
    }

    /**
     * Checks if this is the first app run.
     * Sets the flag to false after first check.
     * 
     * @return true if first run, false otherwise
     */
    public boolean isFirstRun() {
        final SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final boolean isFirst = prefs.getBoolean(KEY_FIRST_RUN, true);
        
        if (isFirst) {
            prefs.edit().putBoolean(KEY_FIRST_RUN, false).apply();
        }
        
        return isFirst;
    }

    /**
     * Gets all saved crash report files sorted by date (newest first).
     * 
     * @return array of crash report files, empty array if none exist
     */
    @NonNull
    public File[] getCrashReports() {
        if (crashDirectory != null && crashDirectory.exists()) {
            final File[] files = crashDirectory.listFiles();
            if (files != null && files.length > 0) {
                // Sort by last modified (newest first)
                Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                return files;
            }
        }
        return new File[0];
    }

    /**
     * Clears all saved crash reports from the crash directory.
     * 
     * @return number of reports deleted
     */
    public int clearCrashReports() {
        int deletedCount = 0;
        
        try {
            if (crashDirectory == null || !crashDirectory.exists()) {
                return 0;
            }
            
            final File[] files = crashDirectory.listFiles();
            if (files != null) {
                for (final File file : files) {
                    if (file.delete()) {
                        deletedCount++;
                    }
                }
                Log.i(TAG, "Cleared " + deletedCount + " crash reports");
            }
        } catch (final Exception e) {
            Log.e(TAG, "Failed to clear crash reports", e);
        }
        
        return deletedCount;
    }

    /**
     * Gets the DownloaderApp instance.
     * 
     * @return DownloaderApp instance or null if not initialized
     */
    @Nullable
    public DownloaderApp getDownloaderApp() {
        return downloaderApp;
    }

    /**
     * Checks if the application is fully initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Gets the application singleton instance.
     * 
     * @return BongoTubeApplication instance
     * @throws IllegalStateException if application not initialized
     */
    @NonNull
    public static BongoTubeApplication getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BongoTubeApplication not initialized");
        }
        return instance;
    }

    /**
     * Gets the application context safely.
     * 
     * @return application context
     * @throws IllegalStateException if application not initialized
     */
    @NonNull
    public static Context getAppContext() {
        return getInstance().getApplicationContext();
    }

    /**
     * Refresh downloader settings when user changes preferences.
     */
    public void refreshDownloaderSettings() {
        if (downloaderApp != null) {
            try {
                downloaderApp.refreshCookies();
                downloaderApp.refreshLocalization();
                Log.i(TAG, "Downloader settings refreshed");
            } catch (final Exception e) {
                Log.e(TAG, "Failed to refresh downloader settings", e);
            }
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.i(TAG, "BongoTube Application terminating...");
        
        // Cleanup resources
        if (downloaderApp != null) {
            try {
                downloaderApp.cleanup();
            } catch (final Exception e) {
                Log.e(TAG, "Error during cleanup", e);
            }
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "Low memory warning received");
        // Could implement memory cleanup here if needed
    }

    @Override
    public void onTrimMemory(final int level) {
        super.onTrimMemory(level);
        Log.d(TAG, "Trim memory level: " + level);
        // Could implement memory trimming based on level
    }
}