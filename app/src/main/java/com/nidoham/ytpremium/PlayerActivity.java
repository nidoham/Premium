package com.nidoham.ytpremium;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
import androidx.media3.ui.PlayerView;

import com.nidoham.ytpremium.player.PlayerService;
import com.nidoham.ytpremium.player.StreamInfoCallback;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.queue.PlayQueueItem;

import java.util.Locale;

@UnstableApi
public class PlayerActivity extends AppCompatActivity implements StreamInfoCallback {
    // Logging Tags
    private static final String TAG = "PlayerActivity";
    private static final String TAG_BIND = "[BIND]";
    private static final String TAG_CALLBACK = "[CALLBACK]";
    private static final String TAG_PLAYER = "[PLAYER]";
    private static final String TAG_UI = "[UI]";
    private static final String TAG_LIFECYCLE = "[LIFECYCLE]";
    
    // Time update interval
    private static final long TIME_UPDATE_INTERVAL_MS = 1000;

    // UI Components
    private PlayerView playerView;
    private ProgressBar loadingIndicator;
    private TextView txtTitle;
    private TextView txtMeta;
    private ImageButton btnPlayPause;
    
    // Service and Binding
    private PlayerService playerService;
    private boolean isServiceBound = false;

    // Handler for time updates
    private final Handler timeUpdateHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeUpdateRunnable = this::updatePlayerState;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, TAG_BIND + " PlayerService connected.");
            try {
                PlayerService.LocalBinder binder = (PlayerService.LocalBinder) service;
                playerService = binder.getService();
                isServiceBound = true;

                playerService.setStreamInfoCallback(PlayerActivity.this);
                
                if (playerService.getPlayer() != null) {
                    playerView.setPlayer(playerService.getPlayer());
                    setupPlayerListener(playerService.getPlayer());
                    updateInitialUI();
                    startTimeUpdates();
                    Log.d(TAG, TAG_BIND + " Service connection setup complete.");
                } else {
                    Log.e(TAG, TAG_BIND + " ExoPlayer instance is null in the service.");
                    showToast("Error: Player not available.");
                    finish();
                }
            } catch (Exception e) {
                Log.e(TAG, TAG_BIND + " Error during service connection.", e);
                showToast("Service connection error: " + e.getMessage());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, TAG_BIND + " PlayerService disconnected.");
            isServiceBound = false;
            playerService = null;
            if (playerView != null) {
                playerView.setPlayer(null);
            }
            stopTimeUpdates();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        Log.d(TAG, TAG_LIFECYCLE + " onCreate");
        
        initializeUI();
        startAndBindPlayerService();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, TAG_LIFECYCLE + " onResume");
        if (isServiceBound && playerService != null) {
            playerView.setPlayer(playerService.getPlayer()); // Re-attach player to view
            startTimeUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, TAG_LIFECYCLE + " onPause");
        stopTimeUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, TAG_LIFECYCLE + " onDestroy");
        stopTimeUpdates();
        if (isServiceBound) {
            playerService.setStreamInfoCallback(null);
            unbindService(serviceConnection);
            isServiceBound = false;
            Log.d(TAG, TAG_LIFECYCLE + " Service unbound.");
        }
        if (playerView != null) {
            playerView.setPlayer(null);
        }
    }
    
    private void initializeUI() {
        // FIXED: Find the PlayerView from the XML layout (included via include_player.xml)
        // instead of creating it programmatically. This is the correct approach.
        playerView = findViewById(R.id.playerView);
        playerView.setUseController(false); // Using custom controls

        // Find other UI components from included layouts
        loadingIndicator = findViewById(R.id.loadingIndicator); 
        txtTitle = findViewById(R.id.txtTitle);
        txtMeta = findViewById(R.id.txtMeta);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        ImageButton btnNext = findViewById(R.id.btnNext);
        ImageButton btnPrevious = findViewById(R.id.btnPrevious);
        ImageButton btnBack = findViewById(R.id.btnBack);

        // Set listeners
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnNext.setOnClickListener(v -> playNext());
        btnPrevious.setOnClickListener(v -> playPrevious());
        btnBack.setOnClickListener(v -> finish());
        
        Log.d(TAG, TAG_UI + " UI components initialized.");
    }
    
    private void startAndBindPlayerService() {
        Intent intent = new Intent(this, PlayerService.class);
        // Pass the original intent extras to the service
        if (getIntent().hasExtra(PlayerService.EXTRA_PLAY_QUEUE)) {
            intent.putExtra(PlayerService.EXTRA_PLAY_QUEUE, getIntent().getSerializableExtra(PlayerService.EXTRA_PLAY_QUEUE));
            intent.putExtra(PlayerService.EXTRA_START_INDEX, getIntent().getIntExtra(PlayerService.EXTRA_START_INDEX, 0));
        }

        try {
            startService(intent);
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            Log.d(TAG, TAG_BIND + " Starting and binding to PlayerService.");
        } catch (Exception e) {
            Log.e(TAG, TAG_BIND + " Error starting or binding service", e);
            showToast("Failed to start player: " + e.getMessage());
        }
    }

    private void setupPlayerListener(@NonNull Player player) {
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updatePlayerState();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButton(isPlaying);
            }
        });
        Log.d(TAG, TAG_PLAYER + " Player listener configured.");
    }
    
    // ===== StreamInfoCallback Implementation =====
    
    @Override
    public void onStreamLoadingStarted(@NonNull PlayQueueItem queueItem) {
        Log.d(TAG, TAG_CALLBACK + " Stream loading started: " + queueItem.getTitle());
        runOnUiThread(() -> {
            loadingIndicator.setVisibility(View.VISIBLE);
            txtTitle.setText(queueItem.getTitle());
            txtMeta.setText(queueItem.getUploader());
        });
    }

    @Override
    public void onStreamInfoLoaded(@NonNull PlayQueueItem queueItem, @NonNull StreamInfo streamInfo) {
        Log.d(TAG, TAG_CALLBACK + " Stream info loaded: " + streamInfo.getName());
        // FIXED: Removed all playback logic from the activity.
        // The service handles playback internally. The activity's only job
        // is to update the UI with the detailed metadata now available.
        runOnUiThread(() -> {
            loadingIndicator.setVisibility(View.GONE);
            txtTitle.setText(streamInfo.getName());
            txtMeta.setText(streamInfo.getUploaderName());
        });
    }

    @Override
    public void onStreamLoadingFailed(@NonNull PlayQueueItem queueItem, @NonNull Exception error) {
        Log.e(TAG, TAG_CALLBACK + " Stream loading failed for: " + queueItem.getTitle(), error);
        runOnUiThread(() -> {
            loadingIndicator.setVisibility(View.GONE);
            showToast("Failed to load: " + error.getMessage());
        });
    }
    
    @Override
    public void onQueueStateChanged(int currentIndex, int totalItems) {
        // This can be used to update a queue position indicator, e.g., "3 / 10"
    }

    // ===== Playback Controls =====
    
    private void togglePlayPause() {
        if (!isServiceBound) return;
        Player player = playerService.getPlayer();
        if (player == null) return;

        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
    }

    private void playNext() {
        if (isServiceBound) playerService.playNext();
    }

    private void playPrevious() {
        if (isServiceBound) playerService.playPrevious();
    }

    // ===== UI Updates =====
    
    private void updateInitialUI() {
        if (!isServiceBound) return;
        PlayQueueItem item = playerService.getPlayQueue().getItem();
        if (item != null) {
            txtTitle.setText(item.getTitle());
            txtMeta.setText(item.getUploader());
        }
        updatePlayerState();
    }
    
    private void updatePlayerState() {
        if (!isServiceBound || playerService.getPlayer() == null) return;
        
        Player player = playerService.getPlayer();
        updatePlayPauseButton(player.isPlaying());
        
        boolean isBuffering = player.getPlaybackState() == Player.STATE_BUFFERING;
        loadingIndicator.setVisibility(isBuffering ? View.VISIBLE : View.GONE);
    }
    
    private void updatePlayPauseButton(boolean isPlaying) {
        btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
    }
    
    // ===== Time Management =====
    
    private void startTimeUpdates() {
        stopTimeUpdates(); // Ensure no duplicates
        timeUpdateHandler.post(timeUpdateRunnable);
        Log.d(TAG, TAG_UI + " Time updates started.");
    }
    
    private void stopTimeUpdates() {
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
        Log.d(TAG, TAG_UI + " Time updates stopped.");
    }

    // ===== Utility =====

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}