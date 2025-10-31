package com.nidoham.ytpremium.player.manager;

import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

public class VideoScaleManager {

    public static final int SCALE_MODE_FIT = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    public static final int SCALE_MODE_FILL = AspectRatioFrameLayout.RESIZE_MODE_FILL;
    public static final int SCALE_MODE_ZOOM = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;

    private int currentScaleMode = SCALE_MODE_FIT;
    private final PlayerView playerView;

    public VideoScaleManager(PlayerView playerView) {
        this.playerView = playerView;
        applyScaleMode(SCALE_MODE_FIT);
    }

    public void setScaleMode(int mode) {
        if (mode < SCALE_MODE_FIT || mode > SCALE_MODE_ZOOM) {
            mode = SCALE_MODE_FIT;
        }
        currentScaleMode = mode;
        applyScaleMode(mode);
    }

    private void applyScaleMode(int mode) {
        if (playerView != null) {
            playerView.setResizeMode(mode);
            playerView.requestLayout();
        }
    }

    public int getCurrentScaleMode() {
        return currentScaleMode;
    }

    public void toggleNextScaleMode() {
        int nextMode = (currentScaleMode + 1);
        if (nextMode > SCALE_MODE_ZOOM) {
            nextMode = SCALE_MODE_FIT;
        }
        setScaleMode(nextMode);
    }

    public String getScaleModeName() {
        switch (currentScaleMode) {
            case SCALE_MODE_FILL: return "Fill";
            case SCALE_MODE_ZOOM: return "Zoom";
            default: return "Fit";
        }
    }

    // 🆕 নতুন যুক্ত মেথড: বর্তমান স্কেল মোড পুনরায় প্রয়োগ করবে
    public void reapplyScaleMode() {
        applyScaleMode(currentScaleMode);
    }
}