package com.nidoham.ytpremium.stream;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;
import java.util.Locale;

/**
 * স্ট্রিম ম্যানেজমেন্ট সিস্টেম - অডিও/ভিডিও ট্র্যাক স্বয়ংক্রিয় নির্বাচন
 * Stream Management System - Automatic audio/video track selection
 */
public class StreamManager {
    private static final String TAG = "StreamManager";
    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final TelephonyManager telephonyManager;

    public StreamManager(Context context) {
        this.context = context;
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    /**
     * ভাষা পছন্দ অনুযায়ী অডিও ট্র্যাক নির্বাচন করুন
     * Select audio track based on language preference
     */
    public AudioStream selectAudioTrack(List<AudioStream> audioStreams, String preferredLanguage) {
        if (audioStreams == null || audioStreams.isEmpty()) {
            return null;
        }

        // NewPipe Extractor uses different method names
        AudioStream bestAudio = null;
        int maxBitrate = 0;

        for (AudioStream stream : audioStreams) {
            // Try to match preferred language if available
            String audioLanguage = getAudioLanguage(stream);
            
            if (audioLanguage != null && audioLanguage.startsWith(preferredLanguage)) {
                Log.d(TAG, "Selected audio track: " + audioLanguage);
                return stream;
            }

            // Keep track of highest bitrate as fallback
            int bitrate = stream.getAverageBitrate();
            if (bitrate > maxBitrate) {
                maxBitrate = bitrate;
                bestAudio = stream;
            }
        }

        // Return highest bitrate audio if no language match found
        if (bestAudio != null) {
            Log.d(TAG, "Selected audio track (highest bitrate): " + maxBitrate + " bps");
        }
        return bestAudio;
    }

    /**
     * অডিও স্ট্রিম থেকে ভাষা তথ্য পান
     * Get language information from audio stream
     */
    private String getAudioLanguage(AudioStream stream) {
        try {
            // NewPipe Extractor stores language in the format string or metadata
            String format = stream.getFormat() != null ? stream.getFormat().getName() : "";
            
            // Try to extract language from format or use default
            if (format.contains("audio")) {
                // Default to system language
                return Locale.getDefault().getLanguage();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting audio language", e);
        }
        return Locale.getDefault().getLanguage();
    }

    /**
     * ডিভাইস পারফরম্যান্স অনুযায়ী ভিডিও কোয়ালিটি নির্বাচন করুন
     * Select video quality based on device performance
     */
    public VideoStream selectVideoQuality(List<VideoStream> videoStreams, int maxResolution) {
        if (videoStreams == null || videoStreams.isEmpty()) {
            return null;
        }

        VideoStream bestVideo = null;
        int closestResolution = 0;

        for (VideoStream stream : videoStreams) {
            int height = stream.getHeight();
            
            // Find the highest resolution that doesn't exceed maxResolution
            if (height <= maxResolution && height > closestResolution) {
                closestResolution = height;
                bestVideo = stream;
            }
        }

        if (bestVideo != null) {
            Log.d(TAG, "Selected video quality: " + closestResolution + "p");
        }
        return bestVideo;
    }

    /**
     * নেটওয়ার্ক টাইপ অনুযায়ী সর্বোত্তম কোয়ালিটি নির্ধারণ করুন
     * Determine optimal quality based on network type
     */
    public int getOptimalQualityForNetwork() {
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        
        if (networkInfo == null || !networkInfo.isConnected()) {
            return 360; // Fallback to lowest quality
        }

        int networkType = networkInfo.getType();
        
        if (networkType == ConnectivityManager.TYPE_WIFI) {
            return 1080; // WiFi: highest quality
        } else if (networkType == ConnectivityManager.TYPE_MOBILE) {
            return getOptimalMobileQuality();
        }
        
        return 720; // Default
    }

    /**
     * মোবাইল নেটওয়ার্ক টাইপ অনুযায়ী কোয়ালিটি নির্ধারণ করুন
     * Determine quality based on mobile network type
     */
    private int getOptimalMobileQuality() {
        int subtype = telephonyManager.getNetworkType();
        
        switch (subtype) {
            case TelephonyManager.NETWORK_TYPE_LTE:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
                return 720; // 4G/3G+: good quality
                
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return 480; // 3G/2G: medium quality
                
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return 360; // GPRS: lowest quality
                
            default:
                // For API 29+ network types, check using reflection
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return getOptimalQualityForModernNetworks(subtype);
                }
                return 480; // Default fallback
        }
    }

    /**
     * আধুনিক নেটওয়ার্ক টাইপের জন্য কোয়ালিটি নির্ধারণ করুন (API 29+)
     * Determine quality for modern network types (API 29+)
     */
    private int getOptimalQualityForModernNetworks(int networkType) {
        // API 29+ network types
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // NETWORK_TYPE_NR (5G) = 20
            if (networkType == 20) {
                return 1080; // 5G: highest quality
            }
        }
        
        // API 30+ network types
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // NETWORK_TYPE_IWLAN = 18
            if (networkType == 18) {
                return 1080; // WiFi calling: high quality
            }
        }
        
        return 720; // Default for modern networks
    }

    /**
     * ব্যাটারি স্ট্যাটাস অনুযায়ী পাওয়ার সেভিং মোড সক্ষম করুন
     * Enable power saving mode based on battery status
     */
    public boolean shouldEnablePowerSavingMode(int batteryLevel, boolean isLowBatteryMode) {
        return batteryLevel < 20 || isLowBatteryMode;
    }

    /**
     * পাওয়ার সেভিং মোডে সর্বোত্তম কোয়ালিটি পান
     * Get optimal quality in power saving mode
     */
    public int getQualityForPowerSavingMode() {
        return 360; // Lowest quality to save battery
    }
}