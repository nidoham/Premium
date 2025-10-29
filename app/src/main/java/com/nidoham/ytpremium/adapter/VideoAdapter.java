package com.nidoham.ytpremium.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.nidoham.ytpremium.R;
import com.nidoham.ytpremium.model.Video;

import java.util.List;

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
        // Binding handled in playVideo method
    }

    @Override
    public int getItemCount() {
        return videoList != null ? videoList.size() : 0;
    }

    public void playVideo(int position, PlayerView playerView) {
        if (position < 0 || position >= videoList.size()) {
            Log.e(TAG, "Invalid position: " + position);
            return;
        }

        // If same video, just ensure it's playing
        if (currentPlayingPosition == position && exoPlayer.isPlaying()) {
            Log.d(TAG, "Already playing video at position: " + position);
            return;
        }

        try {
            // Stop current playback
            exoPlayer.stop();
            
            // Detach from previous PlayerView
            if (currentPlayerView != null) {
                currentPlayerView.setPlayer(null);
            }

            currentPlayingPosition = position;
            currentPlayerView = playerView;
            Video video = videoList.get(position);

            Log.d(TAG, "Playing video at position " + position + ": " + video.getTitle());
            Log.d(TAG, "Video URL: " + video.getVideoUrl());

            // Create media item from URI
            MediaItem mediaItem = MediaItem.fromUri(video.getVideoUrl());

            // Set media item and prepare
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
            
            // Attach player to view BEFORE preparing
            playerView.setPlayer(exoPlayer);
            
            // Prepare and play
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);

            Log.d(TAG, "Video prepared and playing");

        } catch (Exception e) {
            Log.e(TAG, "Error playing video at position " + position, e);
        }
    }

    public void pauseVideo() {
        if (exoPlayer != null) {
            exoPlayer.pause();
        }
    }

    public void stopVideo() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            if (currentPlayerView != null) {
                currentPlayerView.setPlayer(null);
                currentPlayerView = null;
            }
            currentPlayingPosition = -1;
        }
    }

    public void releasePlayer() {
        if (exoPlayer != null) {
            exoPlayer.release();
            currentPlayerView = null;
            currentPlayingPosition = -1;
        }
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        PlayerView playerView;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            playerView = itemView.findViewById(R.id.player_view);
        }
    }
}