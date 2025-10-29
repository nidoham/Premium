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
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.queue.PlayQueueItem;
import org.schabi.newpipe.util.StreamTypeUtil;

/**
 * PlayerActivity - সম্পূর্ণ পেশাদার প্লেয়ার অ্যাক্টিভিটি
 * 
 * বৈশিষ্ট্য:
 * - PlayerService এর সাথে সম্পূর্ণ সংযোগ
 * - StreamInfoCallback ইমপ্লিমেন্টেশন
 * - DASH, HLS, MP4 স্ট্রিম সাপোর্ট
 * - সম্পূর্ণ প্লেব্যাক নিয়ন্ত্রণ
 * - রিয়েল-টাইম টাইম আপডেট
 * - ব্যাপক লগিং এবং ত্রুটি হ্যান্ডলিং
 * - লাইফসাইকেল ম্যানেজমেন্ট
 * - মেমরি ম্যানেজমেন্ট
 */
@UnstableApi
public class PlayerActivity extends AppCompatActivity implements StreamInfoCallback {
    // ===== লগিং ট্যাগ =====
    private static final String TAG = "PlayerActivity";
    private static final String TAG_BIND = "[BIND]";
    private static final String TAG_CALLBACK = "[CALLBACK]";
    private static final String TAG_STREAM = "[STREAM]";
    private static final String TAG_PLAYER = "[PLAYER]";
    private static final String TAG_TIME = "[TIME]";
    private static final String TAG_UI = "[UI]";
    private static final String TAG_LIFECYCLE = "[LIFECYCLE]";
    private static final String TAG_INTENT = "[INTENT]";
    private static final String TAG_TOAST = "[TOAST]";
    // ===== সময় আপডেট ইন্টারভাল =====
    private static final long TIME_UPDATE_INTERVAL = 500; // মিলিসেকেন্ড
    // ===== UI কম্পোনেন্ট =====
    private PlayerView playerView;
    private FrameLayout playerContainer; // <-- Added
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
    private LinearLayout bottomControls;
    // ===== সার্ভিস এবং বাঁধাই =====
    private PlayerService playerService;
    private boolean isServiceBound = false;
    // ===== প্লেব্যাক স্টেট =====
    private boolean isPlaying = false;
    private boolean isBuffering = false;
    private long currentPosition = 0;
    private long totalDuration = 0;
    // ===== হ্যান্ডলার এবং রানেবল =====
    private Handler timeUpdateHandler;
    private Runnable timeUpdateRunnable;
    // ===== সার্ভিস কানেকশন =====
    /**
     * PlayerService এর সাথে সংযোগ পরিচালনা করে
     * সার্ভিস সংযুক্ত হলে ExoPlayer UI সেটআপ করে
     */
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, TAG_BIND + " PlayerService connected successfully");
            try {
                // সার্ভিস রেফারেন্স পান
                PlayerService.LocalBinder binder = (PlayerService.LocalBinder) service;
                playerService = binder.getService();
                isServiceBound = true;
                Log.d(TAG, TAG_BIND + " Service instance obtained");
                // StreamInfoCallback রেজিস্টার করুন
                playerService.setStreamInfoCallback(PlayerActivity.this);
                Log.d(TAG, TAG_BIND + " StreamInfoCallback registered");
                // ExoPlayer কে PlayerView এর সাথে সংযুক্ত করুন
                if (playerService.getPlayer() != null) {
                    playerView.setPlayer(playerService.getPlayer());
                    Log.d(TAG, TAG_BIND + " ExoPlayer connected to PlayerView");
                    // প্লেয়ার লিসেনার সেটআপ করুন
                    setupPlayerListener();
                    Log.d(TAG, TAG_BIND + " Player listener configured");
                    // UI আপডেট করুন
                    updateUI();
                    Log.d(TAG, TAG_BIND + " UI updated");
                } else {
                    Log.e(TAG, TAG_BIND + " ExoPlayer instance is null");
                    showToast("Error: Player not available");
                }
            } catch (Exception e) {
                Log.e(TAG, TAG_BIND + " Error during service connection", e);
                showToast("Service connection error: " + e.getMessage());
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, TAG_BIND + " PlayerService disconnected");
            try {
                isServiceBound = false;
                playerService = null;
                if (playerView != null) {
                    playerView.setPlayer(null);
                }
                stopTimeUpdates();
                Log.d(TAG, TAG_BIND + " Cleanup completed");
            } catch (Exception e) {
                Log.e(TAG, TAG_BIND + " Error during disconnection", e);
            }
        }
    };
    // ===== লাইফসাইকেল মেথড =====
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        Log.d(TAG, TAG_LIFECYCLE + " onCreate called");
        try {
            // UI ইনিশিয়ালাইজ করুন
            initializeUI();
            Log.d(TAG, TAG_LIFECYCLE + " UI initialized");
            // হ্যান্ডলার ইনিশিয়ালাইজ করুন
            initializeHandlers();
            Log.d(TAG, TAG_LIFECYCLE + " Handlers initialized");
            // PlayerService এর সাথে বাঁধুন
            bindPlayerService();
            Log.d(TAG, TAG_LIFECYCLE + " Service binding initiated");
            // Intent থেকে ডেটা পরিচালনা করুন
            handleIntent(getIntent());
            Log.d(TAG, TAG_LIFECYCLE + " Intent handled");
        } catch (Exception e) {
            Log.e(TAG, TAG_LIFECYCLE + " Error in onCreate", e);
            showToast("Initialization error: " + e.getMessage());
        }
    }
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, TAG_LIFECYCLE + " onStart called");
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, TAG_LIFECYCLE + " onResume called");
        try {
            if (playerService != null && playerService.getPlayer() != null) {
                playerView.setPlayer(playerService.getPlayer());
                Log.d(TAG, TAG_LIFECYCLE + " Player reattached to view");
                if (playerService.getPlayer().isPlaying()) {
                    startTimeUpdates();
                    Log.d(TAG, TAG_LIFECYCLE + " Time updates resumed");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, TAG_LIFECYCLE + " Error in onResume", e);
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, TAG_LIFECYCLE + " onPause called");
        try {
            stopTimeUpdates();
            Log.d(TAG, TAG_LIFECYCLE + " Time updates stopped");
            if (playerService != null && playerService.getPlayer() != null) {
                playerService.getPlayer().pause();
                Log.d(TAG, TAG_LIFECYCLE + " Playback paused");
            }
        } catch (Exception e) {
            Log.e(TAG, TAG_LIFECYCLE + " Error in onPause", e);
        }
    }
    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, TAG_LIFECYCLE + " onStop called");
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, TAG_LIFECYCLE + " onDestroy called");
        try {
            // টাইম আপডেট বন্ধ করুন
            stopTimeUpdates();
            Log.d(TAG, TAG_LIFECYCLE + " Time updates stopped");
            // সার্ভিস থেকে আনবাইন্ড করুন
            if (isServiceBound) {
                playerService.setStreamInfoCallback(null);
                unbindService(serviceConnection);
                isServiceBound = false;
                Log.d(TAG, TAG_LIFECYCLE + " Service unbound");
            }
            // হ্যান্ডলার ক্লিনআপ করুন
            if (timeUpdateHandler != null) {
                timeUpdateHandler.removeCallbacksAndMessages(null);
                timeUpdateHandler = null;
                Log.d(TAG, TAG_LIFECYCLE + " Handler cleaned up");
            }
            // PlayerView ক্লিনআপ করুন
            if (playerView != null) {
                playerView.setPlayer(null);
                playerContainer.removeView(playerView);
                playerView = null;
            }
            Log.d(TAG, TAG_LIFECYCLE + " PlayerView cleaned up");
        } catch (Exception e) {
            Log.e(TAG, TAG_LIFECYCLE + " Error in onDestroy", e);
        }
    }
    // ===== ইনিশিয়ালাইজেশন মেথড =====
    /**
     * UI কম্পোনেন্ট ইনিশিয়ালাইজ করে এবং ক্লিক লিসেনার সেট করে
     */
    private void initializeUI() {
        try {
            // Find player container (from activity_player.xml)
            playerContainer = findViewById(R.id.playerContainer);
            
            // Programmatically create PlayerView
            playerView = new PlayerView(this);
            playerView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ));
            playerView.setUseController(false); // We use custom controls below
            playerContainer.addView(playerView);

            // Rest of UI components
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
            bottomControls = findViewById(R.id.bottomControls);
            Log.d(TAG, TAG_UI + " All UI components found");

            // Set click listeners
            btnPlayPause.setOnClickListener(v -> {
                Log.d(TAG, TAG_UI + " Play/Pause button clicked");
                togglePlayPause();
            });
            btnNext.setOnClickListener(v -> {
                Log.d(TAG, TAG_UI + " Next button clicked");
                playNext();
            });
            btnPrevious.setOnClickListener(v -> {
                Log.d(TAG, TAG_UI + " Previous button clicked");
                playPrevious();
            });
            btnBack.setOnClickListener(v -> {
                Log.d(TAG, TAG_UI + " Back button clicked");
                onBackPressed();
            });

            // Progress bar listener
            if (videoProgress != null) {
                videoProgress.addListener(new TimeBar.OnScrubListener() {
                    @Override
                    public void onScrubStart(TimeBar timeBar, long position) {
                        Log.d(TAG, TAG_UI + " Scrub started at: " + position);
                        stopTimeUpdates();
                    }
                    @Override
                    public void onScrubMove(TimeBar timeBar, long position) {
                        Log.d(TAG, TAG_UI + " Scrub moving at: " + position);
                        if (totalDuration > 0) {
                            updateTimeDisplay();
                        }
                    }
                    @Override
                    public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                        Log.d(TAG, TAG_UI + " Scrub stopped at: " + position + ", canceled: " + canceled);
                        if (!canceled && playerService != null && playerService.getPlayer() != null) {
                            playerService.getPlayer().seekTo(position);
                            if (playerService.getPlayer().isPlaying()) {
                                startTimeUpdates();
                            }
                        }
                    }
                });
            }
            Log.d(TAG, TAG_UI + " Click listeners configured");
        } catch (Exception e) {
            Log.e(TAG, TAG_UI + " Error initializing UI", e);
            throw new RuntimeException("UI initialization failed", e);
        }
    }

    /**
     * হ্যান্ডলার এবং রানেবল ইনিশিয়ালাইজ করে টাইম আপডেটের জন্য
     */
    private void initializeHandlers() {
        try {
            timeUpdateHandler = new Handler(Looper.getMainLooper());
            timeUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (playerService != null && playerService.getPlayer() != null && 
                            playerService.getPlayer().isPlaying()) {
                            updateTimeDisplay();
                            timeUpdateHandler.postDelayed(this, TIME_UPDATE_INTERVAL);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, TAG_TIME + " Error in time update runnable", e);
                    }
                }
            };
            Log.d(TAG, TAG_LIFECYCLE + " Handlers initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, TAG_LIFECYCLE + " Error initializing handlers", e);
            throw new RuntimeException("Handler initialization failed", e);
        }
    }

    /**
     * PlayerService এর সাথে বাঁধুন
     */
    private void bindPlayerService() {
        try {
            Intent intent = new Intent(this, PlayerService.class);
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            Log.d(TAG, TAG_BIND + " Binding request sent to PlayerService");
        } catch (Exception e) {
            Log.e(TAG, TAG_BIND + " Error binding to service", e);
            showToast("Failed to bind service: " + e.getMessage());
        }
    }

    /**
     * Intent থেকে প্লেব্যাক ডেটা পরিচালনা করে
     */
    private void handleIntent(@Nullable Intent intent) {
        try {
            if (intent == null) {
                Log.w(TAG, TAG_INTENT + " No intent provided");
                return;
            }
            Log.d(TAG, TAG_INTENT + " Intent received - PlayQueue data handled by PlayerService");
        } catch (Exception e) {
            Log.e(TAG, TAG_INTENT + " Error handling intent", e);
        }
    }

    // ===== প্লেয়ার লিসেনার সেটআপ =====
    /**
     * প্লেয়ার লিসেনার সেটআপ করে প্লেব্যাক ইভেন্ট শোনার জন্য
     */
    private void setupPlayerListener() {
        try {
            if (playerService == null || playerService.getPlayer() == null) {
                Log.w(TAG, TAG_PLAYER + " Player not available for listener setup");
                return;
            }
            playerService.getPlayer().addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    try {
                        String stateStr = getPlaybackStateString(playbackState);
                        Log.d(TAG, TAG_PLAYER + " Playback state changed: " + stateStr);
                        switch (playbackState) {
                            case Player.STATE_IDLE:
                                Log.d(TAG, TAG_PLAYER + " Player is idle");
                                loadingIndicator.setVisibility(View.GONE);
                                break;
                            case Player.STATE_BUFFERING:
                                Log.d(TAG, TAG_PLAYER + " Player is buffering");
                                loadingIndicator.setVisibility(View.VISIBLE);
                                isBuffering = true;
                                break;
                            case Player.STATE_READY:
                                Log.d(TAG, TAG_PLAYER + " Player is ready");
                                loadingIndicator.setVisibility(View.GONE);
                                isBuffering = false;
                                updateTimeDisplay();
                                startTimeUpdates();
                                break;
                            case Player.STATE_ENDED:
                                Log.d(TAG, TAG_PLAYER + " Playback ended");
                                loadingIndicator.setVisibility(View.GONE);
                                stopTimeUpdates();
                                break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, TAG_PLAYER + " Error in onPlaybackStateChanged", e);
                    }
                }
                @Override
                public void onIsPlayingChanged(boolean isPlayingNow) {
                    try {
                        Log.d(TAG, TAG_PLAYER + " Is playing changed: " + isPlayingNow);
                        isPlaying = isPlayingNow;
                        updatePlayPauseButton();
                        if (isPlayingNow) {
                            startTimeUpdates();
                        } else {
                            stopTimeUpdates();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, TAG_PLAYER + " Error in onIsPlayingChanged", e);
                    }
                }
                @Override
                public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    try {
                        Log.e(TAG, TAG_PLAYER + " Playback error: " + error.getMessage(), error);
                        loadingIndicator.setVisibility(View.GONE);
                        stopTimeUpdates();
                        showToast("Playback error: " + error.getMessage());
                    } catch (Exception e) {
                        Log.e(TAG, TAG_PLAYER + " Error in onPlayerError", e);
                    }
                }
                @Override
                public void onMediaMetadataChanged(androidx.media3.common.MediaMetadata mediaMetadata) {
                    try {
                        Log.d(TAG, TAG_PLAYER + " Media metadata changed");
                        updateUI();
                    } catch (Exception e) {
                        Log.e(TAG, TAG_PLAYER + " Error in onMediaMetadataChanged", e);
                    }
                }
                @Override
                public void onPositionDiscontinuity(Player.PositionInfo oldPosition, 
                                                   Player.PositionInfo newPosition, 
                                                   int reason) {
                    try {
                        Log.d(TAG, TAG_PLAYER + " Position discontinuity: " + reason);
                        updateTimeDisplay();
                    } catch (Exception e) {
                        Log.e(TAG, TAG_PLAYER + " Error in onPositionDiscontinuity", e);
                    }
                }
            });
            Log.d(TAG, TAG_PLAYER + " Player listener configured successfully");
        } catch (Exception e) {
            Log.e(TAG, TAG_PLAYER + " Error setting up player listener", e);
        }
    }

    /**
     * প্লেব্যাক স্টেট কোড থেকে স্ট্রিং পান
     */
    @NonNull
    private String getPlaybackStateString(int state) {
        switch (state) {
            case Player.STATE_IDLE:
                return "IDLE";
            case Player.STATE_BUFFERING:
                return "BUFFERING";
            case Player.STATE_READY:
                return "READY";
            case Player.STATE_ENDED:
                return "ENDED";
            default:
                return "UNKNOWN";
        }
    }

    // ===== StreamInfoCallback ইমপ্লিমেন্টেশন =====
    /**
     * স্ট্রিম লোডিং শুরু হলে কল করা হয়
     */
    @Override
    public void onStreamLoadingStarted(@NonNull PlayQueueItem queueItem) {
        try {
            Log.d(TAG, TAG_CALLBACK + " Stream loading started for: " + queueItem.getTitle());
            runOnUiThread(() -> {
                try {
                    loadingIndicator.setVisibility(View.VISIBLE);
                    txtTitle.setText(queueItem.getTitle());
                    txtMeta.setText(queueItem.getUploader() + " • " + 
                                  formatDuration(queueItem.getDuration()));
                    Log.d(TAG, TAG_CALLBACK + " UI updated with loading state");
                } catch (Exception e) {
                    Log.e(TAG, TAG_CALLBACK + " Error updating UI on loading started", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, TAG_CALLBACK + " Error in onStreamLoadingStarted", e);
        }
    }

    /**
     * স্ট্রিম ইনফো সফলভাবে লোড হলে কল করা হয়
     * DASH, HLS, এবং সাধারণ URL স্ট্রিম সাপোর্ট করে
     */
    @Override
    public void onStreamInfoLoaded(@NonNull PlayQueueItem queueItem, @NonNull StreamInfo streamInfo) {
        try {
            Log.d(TAG, TAG_CALLBACK + " Stream info loaded: " + streamInfo.getName());
            Log.d(TAG, TAG_CALLBACK + " Stream type: " + streamInfo.getStreamType());
            Log.d(TAG, TAG_CALLBACK + " Video streams: " + 
                (streamInfo.getVideoStreams() != null ? streamInfo.getVideoStreams().size() : 0));
            Log.d(TAG, TAG_CALLBACK + " Audio streams: " + 
                (streamInfo.getAudioStreams() != null ? streamInfo.getAudioStreams().size() : 0));
            Log.d(TAG, TAG_CALLBACK + " DASH manifest: " + streamInfo.getDashMpdUrl());
            Log.d(TAG, TAG_CALLBACK + " HLS manifest: " + streamInfo.getHlsUrl());
            runOnUiThread(() -> {
                try {
                    loadingIndicator.setVisibility(View.GONE);
                    // মেটাডেটা আপডেট করুন
                    txtTitle.setText(streamInfo.getName());
                    txtMeta.setText(streamInfo.getUploaderName() + " • " + 
                                  formatDuration(streamInfo.getDuration()));
                    Log.d(TAG, TAG_CALLBACK + " UI updated with stream info");
                    // স্ট্রিম টাইপ অনুযায়ী প্লে করুন
                    if (playerService != null) {
                        playStreamByType(streamInfo);
                    } else {
                        Log.e(TAG, TAG_CALLBACK + " PlayerService not available");
                        showToast("Player service not available");
                    }
                } catch (Exception e) {
                    Log.e(TAG, TAG_CALLBACK + " Error updating UI on stream loaded", e);
                    showToast("Error: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, TAG_CALLBACK + " Error in onStreamInfoLoaded", e);
        }
    }

    /**
     * স্ট্রিম টাইপ অনুযায়ী সেরা স্ট্রিম নির্বাচন করে প্লে করে
     * অগ্রাধিকার: DASH > HLS > ভিডিও+অডিও > ভিডিও > অডিও > ডিরেক্ট URL
     */
    private void playStreamByType(@NonNull StreamInfo streamInfo) {
        try {
            StreamType streamType = streamInfo.getStreamType();
            Log.d(TAG, TAG_STREAM + " Stream type: " + streamType);
            // ১. DASH মেনিফেস্ট চেষ্টা করুন (সর্বোচ্চ অগ্রাধিকার)
            if (streamInfo.getDashMpdUrl() != null && !streamInfo.getDashMpdUrl().isEmpty()) {
                Log.d(TAG, TAG_STREAM + " Playing DASH stream: " + streamInfo.getDashMpdUrl());
                playerService.playWithDashUrl(streamInfo.getDashMpdUrl());
                return;
            }
            // ২. HLS মেনিফেস্ট চেষ্টা করুন
            if (streamInfo.getHlsUrl() != null && !streamInfo.getHlsUrl().isEmpty()) {
                Log.d(TAG, TAG_STREAM + " Playing HLS stream: " + streamInfo.getHlsUrl());
                playerService.playWithSingleUrl(streamInfo.getHlsUrl());
                return;
            }
            // ৩. ভিডিও + অডিও স্ট্রিম চেষ্টা করুন
            if (streamInfo.getVideoStreams() != null && !streamInfo.getVideoStreams().isEmpty() &&
                streamInfo.getAudioStreams() != null && !streamInfo.getAudioStreams().isEmpty()) {
                VideoStream videoStream = streamInfo.getVideoStreams().get(0);
                AudioStream audioStream = streamInfo.getAudioStreams().get(0);
                Log.d(TAG, TAG_STREAM + " Playing merged video+audio streams");
                Log.d(TAG, TAG_STREAM + " Video: " + videoStream.getContent());
                Log.d(TAG, TAG_STREAM + " Audio: " + audioStream.getContent());
                playerService.playWithMergedUrl(videoStream.getContent(), audioStream.getContent());
                return;
            }
            // ৪. শুধু ভিডিও স্ট্রিম চেষ্টা করুন
            if (streamInfo.getVideoStreams() != null && !streamInfo.getVideoStreams().isEmpty()) {
                String videoUrl = streamInfo.getVideoStreams().get(0).getContent();
                Log.d(TAG, TAG_STREAM + " Playing video stream: " + videoUrl);
                playerService.playWithSingleUrl(videoUrl);
                return;
            }
            // ৫. শুধু অডিও স্ট্রিম চেষ্টা করুন
            if (streamInfo.getAudioStreams() != null && !streamInfo.getAudioStreams().isEmpty()) {
                String audioUrl = streamInfo.getAudioStreams().get(0).getContent();
                Log.d(TAG, TAG_STREAM + " Playing audio stream: " + audioUrl);
                playerService.playWithSingleUrl(audioUrl);
                return;
            }
            // ৬. ডিরেক্ট URL চেষ্টা করুন
            if (streamInfo.getUrl() != null && !streamInfo.getUrl().isEmpty()) {
                Log.d(TAG, TAG_STREAM + " Playing direct URL: " + streamInfo.getUrl());
                playerService.playWithSingleUrl(streamInfo.getUrl());
                return;
            }
            // কোন প্লেযোগ্য স্ট্রিম পাওয়া যায়নি
            Log.e(TAG, TAG_STREAM + " No playable stream found!");
            showToast("No playable stream found");
        } catch (Exception e) {
            Log.e(TAG, TAG_STREAM + " Error playing stream", e);
            showToast("Error: " + e.getMessage());
        }
    }

    /**
     * স্ট্রিম লোডিং ব্যর্থ হলে কল করা হয়
     */
    @Override
    public void onStreamLoadingFailed(@NonNull PlayQueueItem queueItem, @NonNull Exception error) {
        try {
            Log.e(TAG, TAG_CALLBACK + " Stream loading failed for: " + queueItem.getTitle(), error);
            runOnUiThread(() -> {
                try {
                    loadingIndicator.setVisibility(View.GONE);
                    txtTitle.setText("Error loading: " + queueItem.getTitle());
                    txtMeta.setText(error.getMessage());
                    showToast("Failed to load: " + queueItem.getTitle());
                    Log.d(TAG, TAG_CALLBACK + " Error UI updated");
                } catch (Exception e) {
                    Log.e(TAG, TAG_CALLBACK + " Error updating UI on loading failed", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, TAG_CALLBACK + " Error in onStreamLoadingFailed", e);
        }
    }

    /**
     * কিউ স্টেট পরিবর্তন হলে কল করা হয়
     */
    @Override
    public void onQueueStateChanged(int currentIndex, int totalItems) {
        try {
            Log.d(TAG, TAG_CALLBACK + " Queue state changed: " + currentIndex + "/" + totalItems);
            runOnUiThread(() -> {
                try {
                    // কিউ পজিশন আপডেট করুন
                    if (txtMeta != null) {
                        String meta = txtMeta.getText().toString();
                        if (!meta.contains("[")) {
                            txtMeta.setText(meta + " [" + (currentIndex + 1) + "/" + totalItems + "]");
                        }
                    }
                    Log.d(TAG, TAG_CALLBACK + " Queue position UI updated");
                } catch (Exception e) {
                    Log.e(TAG, TAG_CALLBACK + " Error updating queue UI", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, TAG_CALLBACK + " Error in onQueueStateChanged", e);
        }
    }

    // ===== টাইম ম্যানেজমেন্ট =====
    /**
     * টাইম ডিসপ্লে আপডেট করে (বর্তমান এবং মোট সময়)
     */
    private void updateTimeDisplay() {
        try {
            if (playerService == null || playerService.getPlayer() == null) {
                return;
            }
            currentPosition = playerService.getPlayer().getCurrentPosition();
            totalDuration = playerService.getPlayer().getDuration();
            if (txtCurrentTime != null) {
                txtCurrentTime.setText(formatTime(currentPosition));
            }
            if (txtTotalTime != null) {
                txtTotalTime.setText(formatTime(totalDuration));
            }
            // প্রগতি বার আপডেট করুন
            if (videoProgress != null && totalDuration > 0) {
                videoProgress.setPosition(currentPosition);
                videoProgress.setDuration(totalDuration);
            }
        } catch (Exception e) {
            Log.e(TAG, TAG_TIME + " Error updating time display", e);
        }
    }

    /**
     * টাইম আপডেট শুরু করে (প্রতি ৫০০ms)
     */
    private void startTimeUpdates() {
        try {
            if (timeUpdateHandler != null && timeUpdateRunnable != null) {
                timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
                timeUpdateHandler.post(timeUpdateRunnable);
                Log.d(TAG, TAG_TIME + " Time updates started");
            }
        } catch (Exception e) {
            Log.e(TAG, TAG_TIME + " Error starting time updates", e);
        }
    }

    /**
     * টাইম আপডেট বন্ধ করে
     */
    private void stopTimeUpdates() {
        try {
            if (timeUpdateHandler != null && timeUpdateRunnable != null) {
                timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
                Log.d(TAG, TAG_TIME + " Time updates stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, TAG_TIME + " Error stopping time updates", e);
        }
    }

    /**
     * মিলিসেকেন্ড থেকে সময় ফরম্যাট করে (HH:MM:SS বা MM:SS)
     */
    @NonNull
    private String formatTime(long timeMs) {
        try {
            if (timeMs <= 0 || timeMs == Long.MIN_VALUE) {
                return "0:00";
            }
            long seconds = timeMs / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            seconds = seconds % 60;
            minutes = minutes % 60;
            if (hours > 0) {
                return String.format("%d:%02d:%02d", hours, minutes, seconds);
            } else {
                return String.format("%d:%02d", minutes, seconds);
            }
        } catch (Exception e) {
            Log.e(TAG, TAG_TIME + " Error formatting time", e);
            return "0:00";
        }
    }

    /**
     * মিলিসেকেন্ড থেকে সময়কাল ফরম্যাট করে (MM:SS)
     */
    @NonNull
    private String formatDuration(long durationMs) {
        try {
            if (durationMs <= 0) {
                return "0:00";
            }
            long seconds = durationMs / 1000;
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return String.format("%d:%02d", minutes, remainingSeconds);
        } catch (Exception e) {
            Log.e(TAG, TAG_TIME + " Error formatting duration", e);
            return "0:00";
        }
    }

    // ===== প্লেব্যাক নিয়ন্ত্রণ =====
    /**
     * প্লে/পজ টগল করে
     */
    private void togglePlayPause() {
        try {
            if (playerService == null || playerService.getPlayer() == null) {
                Log.w(TAG, TAG_PLAYER + " PlayerService not available");
                showToast("Player not available");
                return;
            }
            if (playerService.getPlayer().isPlaying()) {
                playerService.getPlayer().pause();
                isPlaying = false;
                Log.d(TAG, TAG_PLAYER + " Playback paused");
            } else {
                playerService.getPlayer().play();
                isPlaying = true;
                Log.d(TAG, TAG_PLAYER + " Playback started");
            }
            updatePlayPauseButton();
        } catch (Exception e) {
            Log.e(TAG, TAG_PLAYER + " Error toggling play/pause", e);
            showToast("Error: " + e.getMessage());
        }
    }

    /**
     * পরবর্তী ভিডিও প্লে করে
     */
    private void playNext() {
        try {
            if (playerService == null) {
                Log.w(TAG, TAG_PLAYER + " PlayerService not available");
                return;
            }
            playerService.playNext();
            Log.d(TAG, TAG_PLAYER + " Playing next video");
        } catch (Exception e) {
            Log.e(TAG, TAG_PLAYER + " Error playing next", e);
            showToast("Error: " + e.getMessage());
        }
    }

    /**
     * পূর্ববর্তী ভিডিও প্লে করে
     */
    private void playPrevious() {
        try {
            if (playerService == null) {
                Log.w(TAG, TAG_PLAYER + " PlayerService not available");
                return;
            }
            playerService.playPrevious();
            Log.d(TAG, TAG_PLAYER + " Playing previous video");
        } catch (Exception e) {
            Log.e(TAG, TAG_PLAYER + " Error playing previous", e);
            showToast("Error: " + e.getMessage());
        }
    }

    /**
     * প্লে/পজ বাটন আপডেট করে
     */
    private void updatePlayPauseButton() {
        try {
            if (playerService == null || playerService.getPlayer() == null) {
                return;
            }
            if (playerService.getPlayer().isPlaying()) {
                btnPlayPause.setImageResource(R.drawable.ic_pause);
                isPlaying = true;
                Log.d(TAG, TAG_UI + " Play button updated to pause icon");
            } else {
                btnPlayPause.setImageResource(R.drawable.ic_play_arrow);
                isPlaying = false;
                Log.d(TAG, TAG_UI + " Play button updated to play icon");
            }
        } catch (Exception e) {
            Log.e(TAG, TAG_UI + " Error updating play pause button", e);
        }
    }

    // ===== UI আপডেট =====
    /**
     * সম্পূর্ণ UI আপডেট করে
     */
    private void updateUI() {
        try {
            if (playerService == null) {
                Log.w(TAG, TAG_UI + " PlayerService not available");
                return;
            }
            // বর্তমান আইটেম থেকে মেটাডেটা পান
            PlayQueueItem currentItem = playerService.getCurrentItem();
            if (currentItem != null) {
                txtTitle.setText(currentItem.getTitle());
                txtMeta.setText(currentItem.getUploader());
                Log.d(TAG, TAG_UI + " Metadata updated from current item");
            }
            // প্লেব্যাক স্টেট আপডেট করুন
            updatePlayPauseButton();
            updateTimeDisplay();
            Log.d(TAG, TAG_UI + " UI updated successfully");
        } catch (Exception e) {
            Log.e(TAG, TAG_UI + " Error updating UI", e);
        }
    }

    // ===== ইউটিলিটি মেথড =====
    /**
     * Toast বার্তা দেখায়
     */
    private void showToast(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            Log.d(TAG, TAG_TOAST + " Toast shown: " + message);
        } catch (Exception e) {
            Log.e(TAG, TAG_TOAST + " Error showing toast", e);
        }
    }
}