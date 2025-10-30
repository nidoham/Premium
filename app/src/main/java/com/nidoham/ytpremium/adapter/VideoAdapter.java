package com.nidoham.ytpremium.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.nidoham.ytpremium.R;
import com.nidoham.ytpremium.model.Video;

import java.util.List;

/**
 * VideoAdapter for YouTube Shorts style vertical video player
 * 
 * Features:
 * - Proper HLS and progressive stream handling
 * - Smooth video transitions
 * - Memory efficient playback
 * - Auto-play and looping support
 * 
 * @version 2.0 (Fixed HLS support and lifecycle management)
 */
public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {
    private static final String TAG = "VideoAdapter";
    
    private List<Video> videoList;
    private ExoPlayer exoPlayer;
    private int currentPlayingPosition = -1;
    private PlayerView currentPlayerView;

    public VideoAdapter(List<Video> videoList, ExoPlayer exoPlayer) {
        this.videoList = videoList;
        this.exoPlayer = exoPlayer;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        if (position < videoList.size()) {
            Video video = videoList.get(position);
            
            // Set video metadata if you have TextViews in your layout
            if (holder.titleTextView != null) {
                holder.titleTextView.setText(video.getTitle());
            }
            if (holder.uploaderTextView != null) {
                holder.uploaderTextView.setText(video.getUploader());
            }
            
            // Show info container if available
            if (holder.infoContainer != null) {
                holder.infoContainer.setVisibility(View.VISIBLE);
            }
            
            // Hide error and loading initially
            if (holder.errorTextView != null) {
                holder.errorTextView.setVisibility(View.GONE);
            }
            if (holder.loadingIndicator != null) {
                holder.loadingIndicator.setVisibility(View.GONE);
            }
            
            // Configure PlayerView
            holder.playerView.setUseController(false); // Hide controls for shorts
            holder.playerView.setKeepScreenOn(true);
        }
    }

    @Override
    public int getItemCount() {
        return videoList != null ? videoList.size() : 0;
    }

    /**
     * Play video at specified position with proper stream handling
     */
    public void playVideo(int position, PlayerView playerView) {
        if (position < 0 || position >= videoList.size()) {
            Log.e(TAG, "Invalid position: " + position);
            return;
        }

        if (exoPlayer == null) {
            Log.e(TAG, "ExoPlayer is null");
            return;
        }

        // If same video and already playing, just ensure playback
        if (currentPlayingPosition == position) {
            if (exoPlayer.getPlaybackState() == Player.STATE_READY && !exoPlayer.isPlaying()) {
                Log.d(TAG, "Resuming video at position: " + position);
                exoPlayer.play();
            } else {
                Log.d(TAG, "Already playing video at position: " + position);
            }
            return;
        }

        try {
            Video video = videoList.get(position);
            String videoUrl = video.getVideoUrl();

            Log.d(TAG, "Playing video at position " + position + ": " + video.getTitle());
            Log.d(TAG, "Video URL type: " + (isHlsUrl(videoUrl) ? "HLS" : "Progressive"));

            // Stop current playback
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
            
            // Detach from previous PlayerView
            if (currentPlayerView != null && currentPlayerView != playerView) {
                currentPlayerView.setPlayer(null);
            }

            // Update tracking
            currentPlayingPosition = position;
            currentPlayerView = playerView;

            // Create appropriate MediaItem based on stream type
            MediaItem mediaItem = createMediaItem(videoUrl);

            // Attach player to view BEFORE preparing
            playerView.setPlayer(exoPlayer);
            
            // Set media item and configure player
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
            
            // Prepare and auto-play
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);

            Log.d(TAG, "Video prepared and playing");

        } catch (Exception e) {
            Log.e(TAG, "Error playing video at position " + position, e);
            
            // Try to play next video on error
            if (position < videoList.size() - 1) {
                Log.d(TAG, "Attempting to play next video");
                // Don't recursively call playVideo here - let the page change handler do it
            }
        }
    }

    /**
     * Create MediaItem with proper MIME type for HLS or progressive streams
     */
    private MediaItem createMediaItem(String videoUrl) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(videoUrl);
        
        if (isHlsUrl(videoUrl)) {
            // Explicitly set HLS MIME type for better compatibility
            builder.setMimeType(MimeTypes.APPLICATION_M3U8);
            Log.d(TAG, "Creating HLS MediaItem");
        } else {
            // For progressive streams, let ExoPlayer auto-detect
            // Or explicitly set MP4 if needed: builder.setMimeType(MimeTypes.VIDEO_MP4);
            Log.d(TAG, "Creating progressive MediaItem");
        }
        
        return builder.build();
    }

    /**
     * Check if URL is an HLS stream
     */
    private boolean isHlsUrl(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains(".m3u8") || 
               lowerUrl.contains("/hls/") ||
               lowerUrl.contains("manifest") && lowerUrl.contains(".m3u");
    }

    /**
     * Pause current video
     */
    public void pauseVideo() {
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.pause();
            Log.d(TAG, "Video paused");
        }
    }

    /**
     * Stop current video and detach player
     */
    public void stopVideo() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
            
            if (currentPlayerView != null) {
                currentPlayerView.setPlayer(null);
                currentPlayerView = null;
            }
            
            currentPlayingPosition = -1;
            Log.d(TAG, "Video stopped");
        }
    }

    /**
     * Release player resources (call from Fragment/Activity onDestroy)
     * WARNING: After calling this, adapter cannot play videos anymore
     */
    public void releasePlayer() {
        if (exoPlayer != null) {
            stopVideo();
            // Don't release the player here - it's managed by the Fragment
            // exoPlayer.release();
            exoPlayer = null;
            Log.d(TAG, "Player reference cleared");
        }
    }

    /**
     * Get current playing position
     */
    public int getCurrentPlayingPosition() {
        return currentPlayingPosition;
    }

    /**
     * Check if a position is currently playing
     */
    public boolean isPlaying(int position) {
        return currentPlayingPosition == position && 
               exoPlayer != null && 
               exoPlayer.isPlaying();
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        PlayerView playerView;
        TextView titleTextView;
        TextView uploaderTextView;
        TextView errorTextView;
        View loadingIndicator;
        View infoContainer;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            playerView = itemView.findViewById(R.id.player_view);
            
            // Optional: Bind UI elements if they exist in your layout
            try {
                titleTextView = itemView.findViewById(R.id.video_title);
                uploaderTextView = itemView.findViewById(R.id.video_uploader);
                errorTextView = itemView.findViewById(R.id.error_message);
                loadingIndicator = itemView.findViewById(R.id.loading_indicator);
                infoContainer = itemView.findViewById(R.id.video_info_container);
            } catch (Exception e) {
                // Views don't exist in layout
                Log.d(TAG, "Optional UI elements not found in layout");
            }
        }
    }
}