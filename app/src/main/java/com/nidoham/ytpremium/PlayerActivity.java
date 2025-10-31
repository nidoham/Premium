package com.nidoham.ytpremium;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TimeBar;

import com.nidoham.ytpremium.player.PlayerService;
import com.nidoham.ytpremium.player.StreamInfoCallback;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.queue.PlayQueueItem;

/**
 * অপটিমাইজড PlayerActivity
 * 
 * উন্নতি:
 * - কম try-catch ব্লক, শুধু প্রয়োজনীয় জায়গায়
 * - Handler এর পরিবর্তে ExoPlayer listener
 * - State-based UI updates
 * - Memory efficient
 * - Cleaner code structure
 */
@UnstableApi
public class PlayerActivity extends AppCompatActivity implements StreamInfoCallback {
    
    private static final String TAG = "PlayerActivity";
    
    // === UI Components ===
    private PlayerView playerView;
    private ProgressBar loadingIndicator;
    private TextView txtCurrentTime;
    private TextView txtTotalTime;
    private TextView txtTitle;
    private TextView txtMeta;
    private ImageButton btnPlayPause;
    private ImageButton btnNext;
    private ImageButton btnPrevious;
    private ImageButton btnBack;
    private DefaultTimeBar videoProgress;
    
    // === Service ===
    private PlayerService playerService;
    private boolean isServiceBound = false;
    
    // === Player State ===
    private PlayerState currentState = new PlayerState();
    
    // === Service Connection ===
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service connected");
            
            PlayerService.LocalBinder binder = (PlayerService.LocalBinder) service;
            playerService = binder.getService();
            isServiceBound = true;
            
            playerService.setStreamInfoCallback(PlayerActivity.this);
            
            if (playerService.getPlayer() != null) {
                playerView.setPlayer(playerService.getPlayer());
                playerService.getPlayer().addListener(playerListener);
                updateUIFromState();
            }
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Service disconnected");
            cleanup();
        }
    };
    
    // === Player Listener (Handler এর পরিবর্তে) ===
    private final Player.Listener playerListener = new Player.Listener() {
        
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            currentState.playbackState = playbackState;
            updateUIForPlaybackState(playbackState);
        }
        
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            currentState.isPlaying = isPlaying;
            updatePlayPauseButton(isPlaying);
        }
        
        @Override
        public void onPositionDiscontinuity(
            Player.PositionInfo oldPosition,
            Player.PositionInfo newPosition,
            int reason
        ) {
            updateTimeDisplay();
        }
        
        @Override
        public void onPlayerError(androidx.media3.common.PlaybackException error) {
            Log.e(TAG, "Player error", error);
            showError("Playback error: " + error.getMessage());
        }
    };
    
    // ===== Lifecycle Methods =====
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        
        initializeViews();
        setupClickListeners();
        bindPlayerService();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (isServiceBound && playerService != null && playerService.getPlayer() != null) {
            playerView.setPlayer(playerService.getPlayer());
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (isServiceBound && playerService != null) {
            playerService.pause();
        }
    }
    
    @Override
    protected void onDestroy() {
        cleanup();
        super.onDestroy();
    }
    
    // ===== Initialization =====
    
    private void initializeViews() {
        playerView = findViewById(R.id.playerView);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        txtCurrentTime = findViewById(R.id.txtCurrentTime);
        txtTotalTime = findViewById(R.id.txtTotalTime);
        txtTitle = findViewById(R.id.txtTitle);
        txtMeta = findViewById(R.id.txtMeta);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnBack = findViewById(R.id.btnBack);
        videoProgress = findViewById(R.id.videoProgress);
    }
    
    private void setupClickListeners() {
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnNext.setOnClickListener(v -> playNext());
        btnPrevious.setOnClickListener(v -> playPrevious());
        btnBack.setOnClickListener(v -> onBackPressed());
        
        // Progress bar seek listener
        videoProgress.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(TimeBar timeBar, long position) {
                // Seeking শুরু
            }
            
            @Override
            public void onScrubMove(TimeBar timeBar, long position) {
                txtCurrentTime.setText(formatTime(position));
            }
            
            @Override
            public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                if (!canceled && isServiceBound) {
                    playerService.seekTo(position);
                }
            }
        });
    }
    
    private void bindPlayerService() {
        Intent intent = new Intent(this, PlayerService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }
    
    // ===== StreamInfoCallback Implementation =====
    
    @Override
    public void onStreamLoadingStarted(@NonNull PlayQueueItem queueItem) {
        runOnUiThread(() -> {
            loadingIndicator.setVisibility(View.VISIBLE);
            currentState.title = queueItem.getTitle();
            currentState.uploader = queueItem.getUploader();
            updateMetadataDisplay();
        });
    }
    
    @Override
    public void onStreamInfoLoaded(@NonNull PlayQueueItem queueItem, @NonNull StreamInfo streamInfo) {
        runOnUiThread(() -> {
            loadingIndicator.setVisibility(View.GONE);
            currentState.updateFromStreamInfo(streamInfo);
            updateMetadataDisplay();
            
            // PlayerService automatically handles stream playback
            // No need to call any play method here
            Log.d(TAG, "StreamInfo loaded, playback will start automatically");
        });
    }
    
    @Override
    public void onStreamLoadingFailed(@NonNull PlayQueueItem queueItem, @NonNull Exception error) {
        runOnUiThread(() -> {
            loadingIndicator.setVisibility(View.GONE);
            showError("Failed to load: " + queueItem.getTitle());
        });
    }
    
    @Override
    public void onQueueStateChanged(int currentIndex, int totalItems) {
        runOnUiThread(() -> {
            currentState.queuePosition = currentIndex;
            currentState.queueSize = totalItems;
            updateMetadataDisplay();
        });
    }
    
    // ===== Stream Playback (সরলীকৃত) =====
    
    private void playOptimalStream(@NonNull StreamInfo streamInfo) {
        // PlayerService automatically handles stream selection
        // Just let it know that StreamInfo is loaded
        // The service will handle DASH/HLS/Video+Audio selection internally
        Log.d(TAG, "Stream info loaded, PlayerService will handle playback");
    }
    
    // ===== UI Updates (State-based) =====
    
    private void updateUIFromState() {
        updateMetadataDisplay();
        updatePlayPauseButton(currentState.isPlaying);
        updateTimeDisplay();
    }
    
    private void updateMetadataDisplay() {
        if (txtTitle != null && currentState.title != null) {
            txtTitle.setText(currentState.title);
        }
        
        if (txtMeta != null) {
            StringBuilder meta = new StringBuilder();
            if (currentState.uploader != null) {
                meta.append(currentState.uploader);
            }
            if (currentState.queueSize > 0) {
                meta.append(" [")
                    .append(currentState.queuePosition + 1)
                    .append("/")
                    .append(currentState.queueSize)
                    .append("]");
            }
            txtMeta.setText(meta.toString());
        }
    }
    
    private void updatePlayPauseButton(boolean isPlaying) {
        if (btnPlayPause == null) return;
        
        int iconRes = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow;
        btnPlayPause.setImageResource(iconRes);
    }
    
    private void updateUIForPlaybackState(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                loadingIndicator.setVisibility(View.VISIBLE);
                break;
                
            case Player.STATE_READY:
                loadingIndicator.setVisibility(View.GONE);
                updateTimeDisplay();
                break;
                
            case Player.STATE_ENDED:
                loadingIndicator.setVisibility(View.GONE);
                break;
                
            case Player.STATE_IDLE:
                loadingIndicator.setVisibility(View.GONE);
                break;
        }
    }
    
    private void updateTimeDisplay() {
        if (!isServiceBound || playerService == null || playerService.getPlayer() == null) {
            return;
        }
        
        long currentPos = playerService.getCurrentPosition();
        long duration = playerService.getDuration();
        
        if (txtCurrentTime != null) {
            txtCurrentTime.setText(formatTime(currentPos));
        }
        
        if (txtTotalTime != null && duration > 0) {
            txtTotalTime.setText(formatTime(duration));
        }
        
        if (videoProgress != null && duration > 0) {
            videoProgress.setPosition(currentPos);
            videoProgress.setDuration(duration);
        }
    }
    
    // ===== Playback Controls =====
    
    private void togglePlayPause() {
        if (!isServiceBound || playerService == null) {
            showError("Player not available");
            return;
        }
        
        if (playerService.isPlaying()) {
            playerService.pause();
        } else {
            playerService.play();
        }
    }
    
    private void playNext() {
        if (isServiceBound && playerService != null) {
            playerService.playNext();
        }
    }
    
    private void playPrevious() {
        if (isServiceBound && playerService != null) {
            playerService.playPrevious();
        }
    }
    
    // ===== Utility Methods =====
    
    @NonNull
    private String formatTime(long timeMs) {
        if (timeMs <= 0) return "0:00";
        
        long seconds = timeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        seconds %= 60;
        minutes %= 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.e(TAG, message);
    }
    
    private void cleanup() {
        if (isServiceBound) {
            if (playerService != null) {
                playerService.setStreamInfoCallback(null);
                if (playerService.getPlayer() != null) {
                    playerService.getPlayer().removeListener(playerListener);
                }
            }
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        
        if (playerView != null) {
            playerView.setPlayer(null);
        }
        
        playerService = null;
    }
    
    // ===== Player State Class =====
    
    private static class PlayerState {
        String title;
        String uploader;
        boolean isPlaying = false;
        int playbackState = Player.STATE_IDLE;
        int queuePosition = -1;
        int queueSize = 0;
        
        void updateFromStreamInfo(StreamInfo info) {
            this.title = info.getName();
            this.uploader = info.getUploaderName();
        }
    }
}