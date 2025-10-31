package com.nidoham.ytpremium.player.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.Player;
import androidx.media3.ui.AspectRatioFrameLayout;
import com.nidoham.ytpremium.player.dialog.CustomOptionsDialog;

/**
 * Manages player controls, dialogs, and user interactions.
 * Features: Quality selection, playback speed, scale mode, and state persistence.
 */
public class PlayerControlsManager {
    
    private static final String PREFS_NAME = "PlayerControlsPrefs";
    private static final String PREF_DEFAULT_QUALITY = "default_quality";
    private static final String PREF_SUPPORTS_HIGH_QUALITY = "supports_high_quality";
    private static final String DEFAULT_QUALITY = "720p";
    
    private static final int SCALE_MODE_FIT = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private static final int SCALE_MODE_FILL = AspectRatioFrameLayout.RESIZE_MODE_FILL;
    private static final int SCALE_MODE_ZOOM = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
    private static final String[] SCALE_MODE_NAMES = {"Fit", "Fill", "Zoom"};
    
    private static final String[] QUALITY_STANDARD = {"144p", "240p", "360p", "480p", "720p", "1080p"};
    private static final String[] QUALITY_LABELS_STANDARD = {
        "144p (Low)", "240p", "360p (SD)", "480p", "720p (HD)", "1080p (Full HD)"
    };
    
    private static final String[] QUALITY_HIGH = {"144p", "240p", "360p", "480p", "720p", "1080p", "1440p", "2160p"};
    private static final String[] QUALITY_LABELS_HIGH = {
        "144p (Low)", "240p", "360p (SD)", "480p", "720p (HD)", 
        "1080p (Full HD)", "1440p (2K)", "2160p (4K)"
    };
    
    private static final String[] SPEED_LABELS = {"0.25x", "0.5x", "0.75x", "Normal", "1.25x", "1.5x", "1.75x", "2.0x"};
    private static final float[] SPEED_VALUES = {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    
    private final Context context;
    private final PlayerControlsCallback callback;
    private final SharedPreferences prefs;
    
    private boolean supportsHighQuality;
    private String currentQuality;
    private int currentScaleMode = SCALE_MODE_FIT;
    private float currentPlaybackSpeed = 1.0f;
    private CustomOptionsDialog customOptionsDialog;
    
    public interface PlayerControlsCallback {
        void onQualityChanged(String quality);
        void onScaleModeChanged(int scaleMode);
        void onPlaybackSpeedChanged(float speed);
        Player getPlayer();
    }
    
    public PlayerControlsManager(Context context, PlayerControlsCallback callback) {
        this.context = context;
        this.callback = callback;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        this.supportsHighQuality = prefs.getBoolean(PREF_SUPPORTS_HIGH_QUALITY, false);
        this.currentQuality = validateQuality(prefs.getString(PREF_DEFAULT_QUALITY, DEFAULT_QUALITY));
    }
    
    public void showSettingsDialog() {
        String qualityLabel = getQualityLabel(currentQuality);
        
        customOptionsDialog = new CustomOptionsDialog(
            context,
            qualityLabel,
            SCALE_MODE_NAMES[currentScaleMode],
            currentPlaybackSpeed
        );
        
        customOptionsDialog.setOnOptionClickListener(new CustomOptionsDialog.OnOptionClickListener() {
            @Override
            public void onScreenScaleClicked(String currentValue) {
                showScaleDialog();
            }
            
            @Override
            public void onPlaySpeedClicked(String currentValue) {
                showSpeedDialog();
            }
            
            @Override
            public void onQualityClicked(String currentValue) {
                showQualityDialog();
            }
        });
        
        customOptionsDialog.show();
    }
    
    private void showQualityDialog() {
        String[] options = getQualityOptions();
        String[] labels = getQualityLabels();
        int selected = findIndex(options, currentQuality);
        
        new AlertDialog.Builder(context)
            .setTitle("Video Quality")
            .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                String quality = options[which];
                if (!quality.equals(currentQuality)) {
                    currentQuality = quality;
                    prefs.edit().putString(PREF_DEFAULT_QUALITY, quality).apply();
                    callback.onQualityChanged(quality);
                    Toast.makeText(context, "Quality: " + quality, Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
                refreshDialog();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void showSpeedDialog() {
        int selected = findSpeedIndex(currentPlaybackSpeed);
        
        new AlertDialog.Builder(context)
            .setTitle("Playback Speed")
            .setSingleChoiceItems(SPEED_LABELS, selected, (dialog, which) -> {
                currentPlaybackSpeed = SPEED_VALUES[which];
                Player player = callback.getPlayer();
                if (player != null) {
                    player.setPlaybackSpeed(currentPlaybackSpeed);
                }
                callback.onPlaybackSpeedChanged(currentPlaybackSpeed);
                Toast.makeText(context, "Speed: " + SPEED_LABELS[which], Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                refreshDialog();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void showScaleDialog() {
        new AlertDialog.Builder(context)
            .setTitle("Scale Mode")
            .setSingleChoiceItems(SCALE_MODE_NAMES, currentScaleMode, (dialog, which) -> {
                currentScaleMode = which;
                callback.onScaleModeChanged(currentScaleMode);
                Toast.makeText(context, "Scale: " + SCALE_MODE_NAMES[which], Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                refreshDialog();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void refreshDialog() {
        if (customOptionsDialog != null && customOptionsDialog.isShowing()) {
            customOptionsDialog.dismiss();
            showSettingsDialog();
        }
    }
    
    private String[] getQualityOptions() {
        return supportsHighQuality ? QUALITY_HIGH : QUALITY_STANDARD;
    }
    
    private String[] getQualityLabels() {
        return supportsHighQuality ? QUALITY_LABELS_HIGH : QUALITY_LABELS_STANDARD;
    }
    
    private String getQualityLabel(String quality) {
        int index = findIndex(getQualityOptions(), quality);
        return getQualityLabels()[index];
    }
    
    private int findIndex(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) return i;
        }
        return 0;
    }
    
    private int findSpeedIndex(float speed) {
        for (int i = 0; i < SPEED_VALUES.length; i++) {
            if (Math.abs(SPEED_VALUES[i] - speed) < 0.01f) return i;
        }
        return 3;
    }
    
    private String validateQuality(String quality) {
        if (quality == null || quality.isEmpty()) return DEFAULT_QUALITY;
        
        for (String valid : getQualityOptions()) {
            if (valid.equals(quality)) return quality;
        }
        return DEFAULT_QUALITY;
    }
    
    public void dismissDialogs() {
        if (customOptionsDialog != null && customOptionsDialog.isShowing()) {
            customOptionsDialog.dismiss();
        }
    }
    
    // Getters and Setters
    
    public String getCurrentQuality() {
        return currentQuality;
    }
    
    public void setCurrentQuality(String quality) {
        this.currentQuality = validateQuality(quality);
        prefs.edit().putString(PREF_DEFAULT_QUALITY, this.currentQuality).apply();
    }
    
    public int getCurrentScaleMode() {
        return currentScaleMode;
    }
    
    public void setCurrentScaleMode(int scaleMode) {
        this.currentScaleMode = (scaleMode >= 0 && scaleMode < SCALE_MODE_NAMES.length) 
            ? scaleMode : SCALE_MODE_FIT;
    }
    
    public float getCurrentPlaybackSpeed() {
        return currentPlaybackSpeed;
    }
    
    public void setCurrentPlaybackSpeed(float speed) {
        this.currentPlaybackSpeed = (speed > 0 && speed <= 2.0f) ? speed : 1.0f;
    }
    
    public boolean isSupportsHighQuality() {
        return supportsHighQuality;
    }
    
    public void setSupportsHighQuality(boolean supports) {
        this.supportsHighQuality = supports;
        prefs.edit().putBoolean(PREF_SUPPORTS_HIGH_QUALITY, supports).apply();
        
        String validated = validateQuality(this.currentQuality);
        if (!validated.equals(this.currentQuality)) {
            this.currentQuality = validated;
            prefs.edit().putString(PREF_DEFAULT_QUALITY, validated).apply();
        }
    }
    
    // Static helpers
    
    public static int getScaleModeFit() {
        return SCALE_MODE_FIT;
    }
    
    public static int getScaleModeFill() {
        return SCALE_MODE_FILL;
    }
    
    public static int getScaleModeZoom() {
        return SCALE_MODE_ZOOM;
    }
    
    public static String getDefaultQuality(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_DEFAULT_QUALITY, DEFAULT_QUALITY);
    }
    
    public static void setDefaultQuality(Context context, String quality) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_DEFAULT_QUALITY, quality).apply();
    }
}