package org.schabi.newpipe;

import android.content.Context;
import android.util.Log;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.util.InfoCache;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/**
 * Professional HTTP downloader implementation using OkHttp with:
 * - Intelligent user agent detection
 * - Cookie management
 * - Connection pooling and timeouts
 * - Retry mechanism
 * - Comprehensive error handling
 * - Thread-safe singleton pattern
 */
public final class DownloaderImpl extends Downloader {
    private static final String TAG = "DownloaderImpl";
    
    // Public so other classes can reference it (e.g., ReCaptchaActivity)
    public static volatile String USER_AGENT = "Mozilla/5.0 (Android)";

    // YouTube restricted mode constants
    public static final String YOUTUBE_RESTRICTED_MODE_COOKIE_KEY = "youtube_restricted_mode_key";
    public static final String YOUTUBE_RESTRICTED_MODE_COOKIE = "PREF=f2=8000000";
    public static final String YOUTUBE_DOMAIN = "youtube.com";

    // Network configuration constants
    private static final int CONNECT_TIMEOUT_SECONDS = 15;
    private static final int READ_TIMEOUT_SECONDS = 30;
    private static final int WRITE_TIMEOUT_SECONDS = 30;
    private static final int CONNECTION_POOL_SIZE = 5;
    private static final long CONNECTION_POOL_IDLE_MINUTES = 5;
    
    // HTTP constants
    private static final String COOKIE_SEPARATOR = "; ";
    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String HEADER_COOKIE = "Cookie";
    private static final String HEADER_CONTENT_LENGTH = "Content-Length";
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    
    // Retry configuration
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    // Thread-safe singleton instance
    private static volatile DownloaderImpl instance;
    private static final Object LOCK = new Object();

    private final ConcurrentHashMap<String, String> mCookies;
    private final OkHttpClient client;
    private final boolean enableDebugLogging;

    /**
     * Initialize the singleton with context and custom OkHttpClient builder.
     * Should be called once from Application.onCreate().
     *
     * @param context optional application Context for user agent detection
     * @param builder optional OkHttpClient.Builder for custom configuration
     * @return initialized singleton instance
     */
    public static DownloaderImpl init(@Nullable final Context context,
                                      @Nullable OkHttpClient.Builder builder) {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    if (builder == null) {
                        builder = new OkHttpClient.Builder();
                    }
                    instance = new DownloaderImpl(builder);
                    
                    // Initialize USER_AGENT with context if available
                    if (context != null) {
                        initializeUserAgent(context);
                    }
                    
                    if (BuildConfig.DEBUG) {
                        Log.i(TAG, "DownloaderImpl initialized with User-Agent: " + USER_AGENT);
                    }
                }
            }
        }
        return instance;
    }

    /**
     * Initialize user agent from Android WebView.
     * Falls back to default if WebSettings is unavailable.
     *
     * @param context application context
     */
    private static void initializeUserAgent(@NonNull final Context context) {
        try {
            final Context appContext = context.getApplicationContext();
            final String webViewUA = WebSettings.getDefaultUserAgent(appContext);
            
            if (webViewUA != null && !webViewUA.isEmpty()) {
                // Enhance user agent with app information
                USER_AGENT = webViewUA + " NewPipe";
                
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "User-Agent initialized from WebView: " + USER_AGENT);
                }
            } else {
                Log.w(TAG, "WebView returned empty User-Agent, using fallback");
            }
        } catch (final Exception e) {
            Log.e(TAG, "Failed to initialize User-Agent from WebView, using fallback", e);
            // Keep existing fallback USER_AGENT
        }
    }

    /**
     * Backward-compatible init method without context.
     *
     * @param builder optional OkHttpClient.Builder
     * @return initialized singleton instance
     */
    public static DownloaderImpl init(@Nullable final OkHttpClient.Builder builder) {
        return init(null, builder);
    }

    /**
     * Get the singleton instance. Creates default instance if not initialized.
     *
     * @return singleton instance
     */
    @NonNull
    public static DownloaderImpl getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    Log.w(TAG, "getInstance() called before init(), creating default instance");
                    init(null, null);
                }
            }
        }
        return instance;
    }

    /**
     * Private constructor - initializes OkHttpClient and cookie store.
     *
     * @param builder OkHttpClient.Builder (non-null)
     */
    private DownloaderImpl(@NonNull final OkHttpClient.Builder builder) {
        Objects.requireNonNull(builder, "OkHttpClient.Builder cannot be null");
        
        this.enableDebugLogging = BuildConfig.DEBUG;
        
        // Configure OkHttpClient with optimal settings
        this.client = builder
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(
                        CONNECTION_POOL_SIZE, 
                        CONNECTION_POOL_IDLE_MINUTES, 
                        TimeUnit.MINUTES))
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .addInterceptor(createLoggingInterceptor())
                .build();

        this.mCookies = new ConcurrentHashMap<>();
        
        if (enableDebugLogging) {
            Log.d(TAG, "OkHttpClient configured - Timeouts: " +
                    "connect=" + CONNECT_TIMEOUT_SECONDS + "s, " +
                    "read=" + READ_TIMEOUT_SECONDS + "s, " +
                    "write=" + WRITE_TIMEOUT_SECONDS + "s");
        }
    }

    /**
     * Create logging interceptor for debugging network requests.
     *
     * @return interceptor that logs requests in debug mode
     */
    private Interceptor createLoggingInterceptor() {
        return chain -> {
            final okhttp3.Request request = chain.request();
            
            if (enableDebugLogging) {
                Log.d(TAG, "→ " + request.method() + " " + request.url());
            }
            
            final long startTime = System.nanoTime();
            final okhttp3.Response response = chain.proceed(request);
            final long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            
            if (enableDebugLogging) {
                Log.d(TAG, "← " + response.code() + " " + request.url() + " (" + duration + "ms)");
            }
            
            return response;
        };
    }

    /**
     * Get cookies for a specific URL.
     * Combines YouTube restricted mode cookies and reCAPTCHA cookies.
     *
     * @param url target URL
     * @return combined cookie string or empty string
     */
    @NonNull
    public String getCookies(@Nullable final String url) {
        final Set<String> cookieParts = new LinkedHashSet<>();

        // Add YouTube restricted mode cookie if applicable
        if (url != null && url.contains(YOUTUBE_DOMAIN)) {
            final String ytCookie = getCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY);
            if (ytCookie != null && !ytCookie.isEmpty()) {
                cookieParts.addAll(splitCookieString(ytCookie));
            }
        }

        // Add reCAPTCHA cookies
        final String recaptchaCookie = getCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY);
        if (recaptchaCookie != null && !recaptchaCookie.isEmpty()) {
            cookieParts.addAll(splitCookieString(recaptchaCookie));
        }

        if (cookieParts.isEmpty()) {
            return "";
        }

        return String.join(COOKIE_SEPARATOR, cookieParts);
    }

    /**
     * Split a cookie header string into individual cookies.
     *
     * @param cookieHeader raw cookie header
     * @return list of individual cookie strings
     */
    @NonNull
    private List<String> splitCookieString(@NonNull final String cookieHeader) {
        Objects.requireNonNull(cookieHeader, "Cookie header cannot be null");
        
        final String[] parts = cookieHeader.split(";\\s*");
        final List<String> result = new ArrayList<>(parts.length);
        
        for (final String part : parts) {
            final String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        
        return result;
    }

    /**
     * Get a cookie by key.
     *
     * @param key cookie key
     * @return cookie value or null if not found
     */
    @Nullable
    public String getCookie(@NonNull final String key) {
        Objects.requireNonNull(key, "Cookie key cannot be null");
        return mCookies.get(key);
    }

    /**
     * Set or remove a cookie.
     *
     * @param key    cookie key
     * @param cookie cookie value (null to remove)
     */
    public void setCookie(@NonNull final String key, @Nullable final String cookie) {
        Objects.requireNonNull(key, "Cookie key cannot be null");
        
        if (cookie == null) {
            mCookies.remove(key);
            if (enableDebugLogging) {
                Log.d(TAG, "Cookie removed: " + key);
            }
        } else {
            mCookies.put(key, cookie);
            if (enableDebugLogging) {
                Log.d(TAG, "Cookie set: " + key);
            }
        }
    }

    /**
     * Remove a cookie by key.
     *
     * @param key cookie key
     */
    public void removeCookie(@NonNull final String key) {
        setCookie(key, null);
    }

    /**
     * Clear all stored cookies.
     */
    public void clearAllCookies() {
        mCookies.clear();
        if (enableDebugLogging) {
            Log.d(TAG, "All cookies cleared");
        }
    }

    /**
     * Update YouTube restricted mode cookies based on preferences.
     *
     * @param context Android context to read preferences
     */
    public void updateYoutubeRestrictedModeCookies(@NonNull final Context context) {
        Objects.requireNonNull(context, "Context cannot be null");
        
        try {
            final String restrictedModeKey = context.getString(R.string.youtube_restricted_mode_enabled);
            final boolean restrictedModeEnabled = PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getBoolean(restrictedModeKey, false);
            
            updateYoutubeRestrictedModeCookies(restrictedModeEnabled);
        } catch (final Exception e) {
            Log.e(TAG, "Failed to update YouTube restricted mode cookies", e);
        }
    }

    /**
     * Update YouTube restricted mode cookies.
     *
     * @param youtubeRestrictedModeEnabled whether restricted mode is enabled
     */
    public void updateYoutubeRestrictedModeCookies(final boolean youtubeRestrictedModeEnabled) {
        if (youtubeRestrictedModeEnabled) {
            setCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY, YOUTUBE_RESTRICTED_MODE_COOKIE);
            if (enableDebugLogging) {
                Log.d(TAG, "YouTube restricted mode enabled");
            }
        } else {
            removeCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY);
            if (enableDebugLogging) {
                Log.d(TAG, "YouTube restricted mode disabled");
            }
        }
        
        // Clear cache when restricted mode changes
        InfoCache.getInstance().clearCache();
    }

    /**
     * Get content length by sending a HEAD request.
     *
     * @param url target URL
     * @return content length in bytes
     * @throws IOException if request fails or Content-Length header is missing
     */
    public long getContentLength(@NonNull final String url) throws IOException {
        Objects.requireNonNull(url, "URL cannot be null");
        
        try {
            final Response response = head(url);
            final String lengthHeader = response.getHeader(HEADER_CONTENT_LENGTH);
            
            if (lengthHeader == null || lengthHeader.isEmpty()) {
                throw new IOException("Content-Length header missing for URL: " + url);
            }
            
            try {
                return Long.parseLong(lengthHeader);
            } catch (final NumberFormatException e) {
                throw new IOException("Invalid Content-Length header: " + lengthHeader, e);
            }
        } catch (final ReCaptchaException e) {
            throw new IOException("reCAPTCHA required for URL: " + url, e);
        }
    }

    /**
     * Execute an HTTP request with automatic retry on failure.
     *
     * @param request the request to execute
     * @return HTTP response
     * @throws IOException          if request fails after all retries
     * @throws ReCaptchaException if reCAPTCHA challenge is detected
     */
    @Override
    @NonNull
    public Response execute(@NonNull final Request request) 
            throws IOException, ReCaptchaException {
        Objects.requireNonNull(request, "Request cannot be null");
        
        IOException lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return executeInternal(request);
            } catch (final ReCaptchaException e) {
                // Don't retry on reCAPTCHA
                throw e;
            } catch (final InterruptedIOException e) {
                lastException = e;
                
                if (attempt < MAX_RETRIES) {
                    if (enableDebugLogging) {
                        Log.w(TAG, "Request timeout (attempt " + attempt + "/" + MAX_RETRIES + 
                                "), retrying: " + request.url());
                    }
                    
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Request interrupted", ie);
                    }
                } else {
                    Log.e(TAG, "Request failed after " + MAX_RETRIES + " attempts: " + 
                            request.url(), e);
                }
            } catch (final IOException e) {
                lastException = e;
                
                if (attempt < MAX_RETRIES && isRetryableError(e)) {
                    if (enableDebugLogging) {
                        Log.w(TAG, "Retryable error (attempt " + attempt + "/" + MAX_RETRIES + 
                                "): " + e.getMessage());
                    }
                    
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Request interrupted", ie);
                    }
                } else {
                    throw e;
                }
            }
        }
        
        // All retries exhausted
        throw new IOException("Request failed after " + MAX_RETRIES + " attempts", lastException);
    }

    /**
     * Internal method to execute the actual HTTP request.
     *
     * @param request the request to execute
     * @return HTTP response
     * @throws IOException          if request fails
     * @throws ReCaptchaException if reCAPTCHA challenge is detected
     */
    @NonNull
    private Response executeInternal(@NonNull final Request request) 
            throws IOException, ReCaptchaException {
        
        final String httpMethod = request.httpMethod();
        final String url = request.url();
        final Map<String, List<String>> headers = request.headers();
        final byte[] dataToSend = request.dataToSend();

        // Build request body
        final RequestBody requestBody = buildRequestBody(httpMethod, dataToSend);
        
        // Build OkHttp request
        final okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
                .method(httpMethod, requestBody)
                .url(url)
                .header(HEADER_USER_AGENT, USER_AGENT);

        // Add cookies
        addCookieHeader(requestBuilder, url);
        
        // Add custom headers
        addCustomHeaders(requestBuilder, headers);

        // Execute request
        try (okhttp3.Response okhttpResponse = client.newCall(requestBuilder.build()).execute()) {
            
            // Check for reCAPTCHA
            if (okhttpResponse.code() == HTTP_TOO_MANY_REQUESTS) {
                throw new ReCaptchaException("reCaptcha Challenge requested", url);
            }

            // Read response body
            String responseBodyString = null;
            try (ResponseBody body = okhttpResponse.body()) {
                if (body != null) {
                    responseBodyString = body.string();
                }
            }

            // Get final URL after redirects
            final String finalUrl = okhttpResponse.request().url().toString();
            
            return new Response(
                    okhttpResponse.code(),
                    okhttpResponse.message(),
                    okhttpResponse.headers().toMultimap(),
                    responseBodyString,
                    finalUrl);
        }
    }

    /**
     * Check if an IOException is retryable.
     *
     * @param e the exception
     * @return true if retryable
     */
    private boolean isRetryableError(@NonNull final IOException e) {
        final String message = e.getMessage();
        if (message == null) {
            return false;
        }
        
        final String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("connection reset") ||
               lowerMessage.contains("broken pipe") ||
               lowerMessage.contains("connection refused");
    }

    /**
     * Build OkHttp RequestBody from data.
     *
     * @param httpMethod HTTP method
     * @param dataToSend data to send (nullable)
     * @return RequestBody or null
     */
    @Nullable
    private RequestBody buildRequestBody(@NonNull final String httpMethod, 
                                         @Nullable final byte[] dataToSend) {
        if (dataToSend != null && dataToSend.length > 0) {
            return RequestBody.create(
                    MediaType.parse(CONTENT_TYPE_OCTET_STREAM), 
                    dataToSend);
        } else if ("POST".equalsIgnoreCase(httpMethod) || "PUT".equalsIgnoreCase(httpMethod)) {
            // Empty body for POST/PUT
            return RequestBody.create(null, new byte[0]);
        }
        return null;
    }

    /**
     * Add Cookie header to request if cookies exist for the URL.
     *
     * @param requestBuilder OkHttp request builder
     * @param url            target URL
     */
    private void addCookieHeader(@NonNull final okhttp3.Request.Builder requestBuilder, 
                                 @NonNull final String url) {
        final String cookies = getCookies(url);
        if (!cookies.isEmpty()) {
            requestBuilder.header(HEADER_COOKIE, cookies);
            
            if (enableDebugLogging) {
                Log.d(TAG, "Added cookies for: " + url);
            }
        }
    }

    /**
     * Add custom headers to request.
     *
     * @param requestBuilder OkHttp request builder
     * @param headers        custom headers (nullable)
     */
    private void addCustomHeaders(@NonNull final okhttp3.Request.Builder requestBuilder, 
                                  @Nullable final Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }

        for (final Map.Entry<String, List<String>> entry : headers.entrySet()) {
            final String headerName = entry.getKey();
            final List<String> headerValues = entry.getValue();
            
            if (headerName == null || headerValues == null) {
                continue;
            }
            
            // Remove existing header first
            requestBuilder.removeHeader(headerName);
            
            // Add all values
            for (final String value : headerValues) {
                if (value != null) {
                    requestBuilder.addHeader(headerName, value);
                }
            }
        }
    }

    /**
     * Get the underlying OkHttpClient for advanced use cases.
     *
     * @return OkHttpClient instance
     */
    @NonNull
    public OkHttpClient getClient() {
        return client;
    }
}