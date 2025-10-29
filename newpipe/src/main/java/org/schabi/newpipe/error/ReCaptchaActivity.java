package org.schabi.newpipe.error;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.databinding.ActivityRecaptchaBinding;
import org.schabi.newpipe.extractor.utils.Utils;

import java.util.Objects;

/*
 * Created by beneth <bmauduit@beneth.fr> on 06.12.16.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * ReCaptchaActivity.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
public class ReCaptchaActivity extends AppCompatActivity {
    public static final int RECAPTCHA_REQUEST = 10;
    public static final String RECAPTCHA_URL_EXTRA = "recaptcha_url_extra";
    public static final String TAG = ReCaptchaActivity.class.getSimpleName();
    public static final String YT_URL = "https://www.youtube.com";
    public static final String RECAPTCHA_COOKIES_KEY = "recaptcha_cookies";

    private ActivityRecaptchaBinding recaptchaBinding;
    private final StringBuilder foundCookies = new StringBuilder();

    /**
     * Sanitizes reCAPTCHA URLs by removing YouTube-specific parameters that break HTML rendering.
     * Added null-coalescing and improved documentation
     */
    public static String sanitizeRecaptchaUrl(@Nullable final String url) {
        if (url == null || url.trim().isEmpty()) {
            return YT_URL;
        }
        // Remove "pbj=1" parameter from YouTube URLs, as it makes the page JSON instead of HTML
        return url.replace("&pbj=1", "").replace("pbj=1&", "").replace("?pbj=1", "");
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        recaptchaBinding = ActivityRecaptchaBinding.inflate(getLayoutInflater());
        setContentView(recaptchaBinding.getRoot());
        setSupportActionBar(recaptchaBinding.toolbar);

        final String url = sanitizeRecaptchaUrl(
                getIntent().getStringExtra(RECAPTCHA_URL_EXTRA)
        );
        setResult(RESULT_CANCELED);

        configureWebView();

        recaptchaBinding.reCaptchaWebView.setWebViewClient(new ReCaptchaWebViewClient());

        clearWebViewData();

        recaptchaBinding.reCaptchaWebView.loadUrl(url);
    }

    /**
     * Extracted WebView configuration into separate method for clarity and maintainability
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        final WebSettings webSettings = recaptchaBinding.reCaptchaWebView.getSettings();
        
        // Enable JavaScript (required for reCAPTCHA)
        webSettings.setJavaScriptEnabled(true);
        
        webSettings.setUserAgentString(DownloaderImpl.USER_AGENT);
        
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);
        
        webSettings.setGeolocationEnabled(false);
        
        webSettings.setDomStorageEnabled(true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            webSettings.setAlgorithmicDarkeningAllowed(false);
        }
    }

    /**
     * Extracted data clearing into separate method
     */
    private void clearWebViewData() {
        recaptchaBinding.reCaptchaWebView.clearCache(true);
        recaptchaBinding.reCaptchaWebView.clearHistory();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookies(null);
        } else {
            CookieManager.getInstance().removeAllCookie();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_recaptcha, menu);

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setTitle(R.string.title_activity_recaptcha);
            actionBar.setSubtitle(R.string.subtitle_activity_recaptcha);
        }

        return true;
    }

    @Override
    public void onBackPressed() {
        saveCookiesAndFinish();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.menu_item_done) {
            saveCookiesAndFinish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Improved cookie extraction and persistence
     */
    private void saveCookiesAndFinish() {
        // Try to get cookies from the current page
        handleCookiesFromUrl(recaptchaBinding.reCaptchaWebView.getUrl());
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "saveCookiesAndFinish: foundCookies=" + foundCookies);
        }

        if (foundCookies.length() > 0) {
            final String cookieString = foundCookies.toString();
            
            // Save cookies to SharedPreferences
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                    getApplicationContext()
            );
            final String key = getString(R.string.recaptcha_cookies_key);
            prefs.edit().putString(key, cookieString).apply();

            // Pass cookies to Downloader
            DownloaderImpl.getInstance().setCookie(RECAPTCHA_COOKIES_KEY, cookieString);
            setResult(RESULT_OK);
        }

        // Navigate to blank page to prevent background playback
//        recaptchaBinding.reCaptchaWebView.loadUrl("about:blank");
//
//        final Intent intent = new Intent(this, com.nidoham.ytpremiumMainActivity.class);
//        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        NavUtils.navigateUpTo(this, intent);
    }

    /**
     * Improved cookie extraction from URL and CookieManager
     */
    private void handleCookiesFromUrl(@Nullable final String url) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "handleCookiesFromUrl: url=" + (url == null ? "null" : url));
        }

        if (url == null) {
            return;
        }

        // Extract cookies from CookieManager
        final String cookies = CookieManager.getInstance().getCookie(url);
        handleCookies(cookies);

        // Extract cookies embedded in URL (google_abuse parameter)
        extractEmbeddedCookies(url);
    }

    /**
     * Extracted embedded cookie extraction into separate method
     */
    private void extractEmbeddedCookies(@NonNull final String url) {
        final int abuseStart = url.indexOf("google_abuse=");
        if (abuseStart == -1) {
            return;
        }

        final int abuseEnd = url.indexOf("+path");
        if (abuseEnd == -1) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "extractEmbeddedCookies: malformed google_abuse parameter in url");
            }
            return;
        }

        try {
            final String encodedCookie = url.substring(abuseStart + 13, abuseEnd);
            final String decodedCookie = Utils.decodeUrlUtf8(encodedCookie);
            handleCookies(decodedCookie);
        } catch (final StringIndexOutOfBoundsException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "extractEmbeddedCookies: invalid google_abuse parameter", e);
            }
        }
    }

    /**
     * Improved null safety and logging
     */
    private void handleCookies(@Nullable final String cookies) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "handleCookies: cookies=" + (cookies == null ? "null" : cookies));
        }

        if (cookies == null || cookies.isEmpty()) {
            return;
        }

        addYoutubeCookies(cookies);
    }

    /**
     * Improved cookie validation with modern patterns
     */
    private void addYoutubeCookies(@NonNull final String cookies) {
        final boolean hasRequiredCookie = cookies.contains("s_gl=")
                || cookies.contains("goojf=")
                || cookies.contains("VISITOR_INFO1_LIVE=")
                || cookies.contains("GOOGLE_ABUSE_EXEMPTION=");

        if (hasRequiredCookie) {
            addCookie(cookies);
        }
    }

    /**
     * Improved cookie concatenation using StringBuilder
     */
    private void addCookie(@NonNull final String cookie) {
        if (foundCookies.toString().contains(cookie)) {
            return;
        }

        if (foundCookies.length() == 0) {
            foundCookies.append(cookie);
        } else if (foundCookies.toString().endsWith("; ")) {
            foundCookies.append(cookie);
        } else if (foundCookies.toString().endsWith(";")) {
            foundCookies.append(" ").append(cookie);
        } else {
            foundCookies.append("; ").append(cookie);
        }
    }

    /**
     * Inner class for WebViewClient with improved cookie handling
     */
    private class ReCaptchaWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(
                @NonNull final WebView view,
                @NonNull final WebResourceRequest request
        ) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "shouldOverrideUrlLoading: url=" + request.getUrl());
            }

            handleCookiesFromUrl(request.getUrl().toString());
            return false;
        }

        @Override
        public void onPageFinished(@NonNull final WebView view, @NonNull final String url) {
            super.onPageFinished(view, url);
            handleCookiesFromUrl(url);
        }
    }
}
