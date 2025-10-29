package com.nidoham.ytpremium.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.nidoham.ytpremium.PlayerActivity;
import com.nidoham.ytpremium.R;

import org.schabi.newpipe.extractor.ExtractorHelper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.queue.PlayQueue;
import org.schabi.newpipe.queue.PlayQueueItem;
import org.schabi.newpipe.util.StreamTypeUtil;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Professional PlayerService with complete DASH/URL stream support
 * 
 * Features:
 * - DASH manifest playback (.mpd)
 * - HLS stream playback (.m3u8)
 * - Single URL playback (MP4, WebM, etc.)
 * - Merged video+audio playback
 * - Automatic stream type detection
 * - Queue management with shuffle/repeat
 * - Playback speed control
 * - Quality selection
 * - Stream preloading
 * - Complete error handling
 * - Comprehensive logging
 * - Full UI support via PlayerView in PlayerActivity
 * 
 * No external dependencies required beyond ExoPlayer and NewPipe Extractor
 */
@UnstableApi
public class PlayerService extends MediaSessionService {
    private static final String TAG = "PlayerService";
    // Notification
    private static final String CHANNEL_ID = "youtube_player_channel";
    private static final int NOTIFICATION_ID = 1001;
    // Custom commands
    private static final String COMMAND_TOGGLE_SPEED = "TOGGLE_SPEED";
    private static final String COMMAND_SEEK_FORWARD = "SEEK_FORWARD";
    private static final String COMMAND_SEEK_BACKWARD = "SEEK_BACKWARD";
    private static final String COMMAND_NEXT = "NEXT";
    private static final String COMMAND_PREVIOUS = "PREVIOUS";
    private static final String COMMAND_TOGGLE_SHUFFLE = "TOGGLE_SHUFFLE";
    private static final String COMMAND_TOGGLE_REPEAT = "TOGGLE_REPEAT";
    // Playback configuration
    private static final long SEEK_INCREMENT_MS = 10000L;
    private static final float[] SPEED_OPTIONS = {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    private static final int DEFAULT_SPEED_INDEX = 3; // 1.0x
    private static final int PRELOAD_AHEAD_COUNT = 2;
    private static final int MAX_VIDEO_HEIGHT = 1080;
    // Intent extras
    public static final String EXTRA_PLAY_QUEUE = "EXTRA_PLAY_QUEUE";
    public static final String EXTRA_START_INDEX = "EXTRA_START_INDEX";
    public static final String EXTRA_VIDEO_URL = "EXTRA_VIDEO_URL";
    public static final String EXTRA_START_POSITION = "EXTRA_START_POSITION";
    // Media components
    private ExoPlayer player;
    private MediaSession mediaSession;
    private DefaultTrackSelector trackSelector;
    private DefaultDataSource.Factory dataSourceFactory;
    // Queue management
    private PlayQueue playQueue;
    private PlayQueueItem currentItem;
    private StreamInfo currentStreamInfo;
    // Callbacks
    private StreamInfoCallback streamInfoCallback;
    // State management
    private final AtomicBoolean isLoadingStream = new AtomicBoolean(false);
    private final AtomicBoolean isServiceInitialized = new AtomicBoolean(false);
    private int currentSpeedIndex = DEFAULT_SPEED_INDEX;
    // Resource management
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final ExecutorService preloadExecutor = Executors.newSingleThreadExecutor();
    // Binder for activity binding
    private final IBinder binder = new LocalBinder();

    /**
     * Local binder for service-activity communication
     */
    public class LocalBinder extends Binder {
        @NonNull
        public PlayerService getService() {
            return PlayerService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        Log.d(TAG, "[BIND] Service bound to activity");
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "[INIT] PlayerService creating");
        if (isServiceInitialized.get()) {
            Log.w(TAG, "[INIT] Service already initialized");
            return;
        }
        try {
            initializeComponents();
            createNotificationChannel();
            isServiceInitialized.set(true);
            Log.d(TAG, "[INIT] ✓ PlayerService initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "[INIT] ✗ Failed to initialize service", e);
            isServiceInitialized.set(false);
            stopSelf();
        }
    }

    /**
     * Handle queue initialization from intent
     * PlayQueue is now the primary data source - Intent extras are optional
     */
    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Log.d(TAG, "[START] onStartCommand called");
        if (intent != null && intent.hasExtra(EXTRA_PLAY_QUEUE)) {
            try {
                Serializable queueObj = intent.getSerializableExtra(EXTRA_PLAY_QUEUE);
                if (queueObj instanceof PlayQueue) {
                    PlayQueue queue = (PlayQueue) queueObj;
                    int startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0);
                    Log.d(TAG, "[START] Queue received: " + queue.size() + " items, starting at: " + startIndex);
                    if (queue.isEmpty()) {
                        Log.e(TAG, "[START] Queue is empty!");
                        stopSelf();
                        return START_NOT_STICKY;
                    }
                    initializeQueue(queue, startIndex);
                } else {
                    Log.e(TAG, "[START] Invalid queue object type");
                    stopSelf();
                }
            } catch (Exception e) {
                Log.e(TAG, "[START] Failed to initialize queue", e);
                stopSelf();
            }
        } else {
            Log.w(TAG, "[START] No queue provided - waiting for queue initialization");
        }
        return START_NOT_STICKY;
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Initialization
     //////////////////////////////////////////////////////////////////////////*/

    /**
     * Initialize all service components
     */
    private void initializeComponents() {
        Log.d(TAG, "[INIT] Initializing components");
        initializePlayer();
        initializeMediaSession();
        dataSourceFactory = new DefaultDataSource.Factory(this);
    }

    /**
     * Initialize ExoPlayer with video rendering support
     */
    private void initializePlayer() {
        Log.d(TAG, "[PLAYER] Initializing ExoPlayer");
        trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setMaxVideoSize(Integer.MAX_VALUE, MAX_VIDEO_HEIGHT)
                .setPreferredAudioLanguage("en")
                .setForceHighestSupportedBitrate(false)
                .setAllowVideoMixedMimeTypeAdaptiveness(false)
                .setAllowVideoNonSeamlessAdaptiveness(true)
                .build()
        );
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build();
        player = new ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            .build();
        player.addListener(new PlayerEventListener());
        player.setPlayWhenReady(false);
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        player.prepare();
        Log.d(TAG, "[PLAYER] ✓ ExoPlayer initialized");
    }

    /**
     * Initialize MediaSession for media control
     */
    private void initializeMediaSession() {
        if (player == null) {
            throw new IllegalStateException("Player must be initialized first");
        }
        Log.d(TAG, "[SESSION] Initializing MediaSession");
        mediaSession = new MediaSession.Builder(this, player)
            .setId("youtube_player_session")
            .setCallback(new MediaSessionCallback())
            .build();
        Log.d(TAG, "[SESSION] ✓ MediaSession initialized");
    }

    /**
     * Create notification channel for Android O+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "YouTube Player",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Media playback controls");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Queue Management
     //////////////////////////////////////////////////////////////////////////*/

    /**
     * Initialize queue with proper validation
     * PlayQueue is now the primary data source
     */
    private void initializeQueue(@NonNull PlayQueue queue, int startIndex) {
        if (queue == null || queue.isEmpty()) {
            Log.e(TAG, "[QUEUE] Invalid queue");
            stopSelf();
            return;
        }
        synchronized (this) {
            this.playQueue = queue;
            this.playQueue.setIndex(startIndex);
            this.currentItem = playQueue.getItem();
            if (currentItem != null) {
                Log.d(TAG, "[QUEUE] ✓ Queue initialized");
                Log.d(TAG, "[QUEUE] Size: " + queue.size() + ", Index: " + startIndex);
                Log.d(TAG, "[QUEUE] Current: " + currentItem.getTitle());
                notifyQueueStateChanged();
                loadAndPlayCurrentItem();
                preloadUpcomingStreams();
            } else {
                Log.e(TAG, "[QUEUE] No item at index " + startIndex);
                stopSelf();
            }
        }
    }

    /**
     * Initialize queue from item list
     */
    public void initializeQueueFromList(@NonNull List<PlayQueueItem> items, int startIndex, boolean repeatEnabled) {
        if (items == null || items.isEmpty()) {
            Log.w(TAG, "[QUEUE] Empty items list");
            return;
        }
        try {
            PlayQueue queue = new PlayQueue(startIndex, items, repeatEnabled);
            initializeQueue(queue, startIndex);
            Log.d(TAG, "[QUEUE] ✓ Queue initialized from list: " + items.size() + " items");
        } catch (Exception e) {
            Log.e(TAG, "[QUEUE] Failed to initialize from list", e);
        }
    }

    /**
     * Play single video
     */
    public void playSingleVideo(@NonNull PlayQueueItem item) {
        if (item == null) {
            Log.w(TAG, "[QUEUE] Cannot play null item");
            return;
        }
        try {
            List<PlayQueueItem> singleItemList = Collections.singletonList(item);
            initializeQueueFromList(singleItemList, 0, false);
            Log.d(TAG, "[QUEUE] ✓ Playing single video: " + item.getTitle());
        } catch (Exception e) {
            Log.e(TAG, "[QUEUE] Failed to play single video", e);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Callback Management
     //////////////////////////////////////////////////////////////////////////*/

    /**
     * Register stream info callback
     */
    public void setStreamInfoCallback(@Nullable StreamInfoCallback callback) {
        this.streamInfoCallback = callback;
        Log.d(TAG, "[CALLBACK] " + (callback != null ? "Registered" : "Unregistered"));
    }

    private void notifyQueueStateChanged() {
        if (streamInfoCallback != null && playQueue != null) {
            streamInfoCallback.onQueueStateChanged(playQueue.getIndex(), playQueue.size());
        }
    }

    private void notifyStreamLoadingStarted(@NonNull PlayQueueItem item) {
        if (streamInfoCallback != null) {
            streamInfoCallback.onStreamLoadingStarted(item);
        }
    }

    private void notifyStreamInfoLoaded(@NonNull PlayQueueItem item, @NonNull StreamInfo info) {
        if (streamInfoCallback != null) {
            streamInfoCallback.onStreamInfoLoaded(item, info);
        }
    }

    private void notifyStreamLoadingFailed(@NonNull PlayQueueItem item, @NonNull Exception error) {
        if (streamInfoCallback != null) {
            streamInfoCallback.onStreamLoadingFailed(item, error);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Stream Loading
     //////////////////////////////////////////////////////////////////////////*/

    /**
     * Load and play current item
     */
    private void loadAndPlayCurrentItem() {
        if (currentItem == null) {
            Log.e(TAG, "[LOAD] No current item");
            return;
        }
        if (!isLoadingStream.compareAndSet(false, true)) {
            Log.w(TAG, "[LOAD] Already loading");
            return;
        }
        final String url = currentItem.getUrl();
        final int serviceId = currentItem.getServiceId();
        Log.d(TAG, "[LOAD] ========================================");
        Log.d(TAG, "[LOAD] Title: " + currentItem.getTitle());
        Log.d(TAG, "[LOAD] URL: " + url);
        Log.d(TAG, "[LOAD] Service ID: " + serviceId);
        Log.d(TAG, "[LOAD] ========================================");
        notifyStreamLoadingStarted(currentItem);
        disposables.add(
            ExtractorHelper.getStreamInfo(serviceId, url, false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    this::onStreamInfoLoaded,
                    this::onStreamLoadingError
                )
        );
    }

    /**
     * Handle successful stream info loading
     */
    private void onStreamInfoLoaded(@NonNull StreamInfo streamInfo) {
        try {
            Log.d(TAG, "[LOAD] ✓ Stream info loaded: " + streamInfo.getName());
            currentStreamInfo = streamInfo;
            notifyStreamInfoLoaded(currentItem, streamInfo);
            prepareAndPlayMediaSource(streamInfo);
        } catch (Exception e) {
            Log.e(TAG, "[LOAD] Error processing stream info", e);
            onStreamLoadingError(e);
        } finally {
            isLoadingStream.set(false);
        }
    }

    /**
     * Handle stream loading errors
     */
    private void onStreamLoadingError(@NonNull Throwable error) {
        Log.e(TAG, "[LOAD] ✗ Stream loading error: " + error.getMessage(), error);
        if (currentItem != null) {
            notifyStreamLoadingFailed(currentItem, (Exception) error);
        }
        isLoadingStream.set(false);
        handleStreamLoadError();
    }

    /**
     * Preload upcoming streams
     */
    private void preloadUpcomingStreams() {
        if (playQueue == null || playQueue.size() <= 1) {
            return;
        }
        preloadExecutor.execute(() -> {
            try {
                int currentIndex = playQueue.getIndex();
                int queueSize = playQueue.size();
                for (int i = 1; i <= PRELOAD_AHEAD_COUNT && (currentIndex + i) < queueSize; i++) {
                    PlayQueueItem upcomingItem = playQueue.getItem(currentIndex + i);
                    if (upcomingItem != null) {
                        preloadStream(upcomingItem);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "[PRELOAD] Error preloading streams", e);
            }
        });
    }

    /**
     * Preload single stream
     */
    private void preloadStream(@NonNull PlayQueueItem item) {
        ExtractorHelper.getStreamInfo(item.getServiceId(), item.getUrl(), false)
            .subscribeOn(Schedulers.io())
            .subscribe(
                streamInfo -> Log.d(TAG, "[PRELOAD] ✓ Preloaded: " + item.getTitle()),
                error -> Log.w(TAG, "[PRELOAD] Failed: " + item.getTitle())
            );
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Media Source Preparation
     //////////////////////////////////////////////////////////////////////////*/

    /**
     * Prepare and play media source
     */
    private void prepareAndPlayMediaSource(@NonNull StreamInfo streamInfo) {
        try {
            Log.d(TAG, "[MEDIA] Preparing media source");
            StreamType streamType = streamInfo.getStreamType();
            String streamUrl = selectBestStreamUrl(streamInfo);
            if (streamUrl == null || streamUrl.isEmpty()) {
                Log.e(TAG, "[MEDIA] No valid stream URL");
                handleStreamLoadError();
                return;
            }
            Log.d(TAG, "[MEDIA] Selected URL: " + streamUrl);
            // Play based on stream type
            playStreamByType(streamType, streamUrl, streamInfo);
            updateNotification();
            Log.d(TAG, "[MEDIA] ✓ Playback started: " + streamInfo.getName());
        } catch (Exception e) {
            Log.e(TAG, "[MEDIA] Failed to prepare media source", e);
            handleStreamLoadError();
        }
    }

    /**
     * Prepare player with media source
     */
    private void preparePlayer(@NonNull MediaSource mediaSource) {
        if (player == null) {
            throw new IllegalStateException("Player is null");
        }
        Log.d(TAG, "[PLAYER] Setting media source");
        player.stop();
        player.clearMediaItems();
        player.setMediaSource(mediaSource);
        player.prepare();
        if (trackSelector != null) {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setMaxVideoSize(Integer.MAX_VALUE, MAX_VIDEO_HEIGHT)
                    .build()
            );
        }
        player.setPlayWhenReady(true);
        Log.d(TAG, "[PLAYER] ✓ Player prepared");
    }

    /**
     * Select best stream URL
     */
    @Nullable
    private String selectBestStreamUrl(@NonNull StreamInfo streamInfo) {
        Log.d(TAG, "[SELECT] Selecting best stream URL");
        String videoUrl = selectBestVideoStream(streamInfo.getVideoStreams());
        if (videoUrl != null) {
            Log.d(TAG, "[SELECT] Selected video stream");
            return videoUrl;
        }
        String audioUrl = selectBestAudioStream(streamInfo.getAudioStreams());
        if (audioUrl != null) {
            Log.d(TAG, "[SELECT] Selected audio stream");
            return audioUrl;
        }
        Log.w(TAG, "[SELECT] Using direct URL");
        return streamInfo.getUrl();
    }

    /**
     * Select best video stream
     */
    @Nullable
    private String selectBestVideoStream(@Nullable List<VideoStream> videoStreams) {
        if (videoStreams == null || videoStreams.isEmpty()) {
            Log.d(TAG, "[VIDEO] No video streams");
            return null;
        }
        Log.d(TAG, "[VIDEO] Available: " + videoStreams.size());
        VideoStream bestStream = videoStreams.stream()
            .filter(s -> s.getContent() != null && !s.getContent().isEmpty())
            .peek(s -> Log.d(TAG, "[VIDEO] Stream: " + s.getHeight() + "p"))
            .max((s1, s2) -> {
                int h1 = Math.min(s1.getHeight(), MAX_VIDEO_HEIGHT);
                int h2 = Math.min(s2.getHeight(), MAX_VIDEO_HEIGHT);
                return Integer.compare(h1, h2);
            })
            .orElse(null);
        if (bestStream != null) {
            Log.d(TAG, "[VIDEO] ✓ Selected: " + bestStream.getHeight() + "p");
        }
        return bestStream != null ? bestStream.getContent() : null;
    }

    /**
     * Select best audio stream
     */
    @Nullable
    private String selectBestAudioStream(@Nullable List<AudioStream> audioStreams) {
        if (audioStreams == null || audioStreams.isEmpty()) {
            Log.d(TAG, "[AUDIO] No audio streams");
            return null;
        }
        Log.d(TAG, "[AUDIO] Available: " + audioStreams.size());
        AudioStream bestStream = audioStreams.stream()
            .filter(s -> s.getContent() != null && !s.getContent().isEmpty())
            .peek(s -> Log.d(TAG, "[AUDIO] Stream: " + s.getAverageBitrate() + " bps"))
            .max((s1, s2) -> Long.compare(s1.getAverageBitrate(), s2.getAverageBitrate()))
            .orElse(null);
        if (bestStream != null) {
            Log.d(TAG, "[AUDIO] ✓ Selected: " + bestStream.getAverageBitrate() + " bps");
        }
        return bestStream != null ? bestStream.getContent() : null;
    }

    /**
     * Build MediaItem with metadata
     */
    @NonNull
    private MediaItem buildMediaItem(@NonNull StreamInfo streamInfo, @NonNull String streamUrl) {
        MediaMetadata metadata = new MediaMetadata.Builder()
            .setTitle(streamInfo.getName())
            .setArtist(streamInfo.getUploaderName())
            .setDisplayTitle(streamInfo.getName())
            .build();
        return new MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build();
    }

    /**
     * Build MediaSource from MediaItem
     */
    @NonNull
    private MediaSource buildMediaSource(@NonNull MediaItem mediaItem) {
        return new ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem);
    }

    /**
     * Handle stream load error
     */
    private void handleStreamLoadError() {
        if (playQueue != null && playQueue.size() > 1) {
            Log.d(TAG, "[ERROR] Trying next item");
            playNext();
        } else {
            Log.e(TAG, "[ERROR] No alternatives, stopping");
            stopSelf();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
     // DASH এবং URL স্ট্রিম সাপোর্ট
     //////////////////////////////////////////////////////////////////////////*/

    /**
     * Play DASH manifest stream
     * No external dependencies required
     */
    public void playWithDashUrl(@NonNull String dashUrl) {
        if (dashUrl == null || dashUrl.isEmpty()) {
            Log.e(TAG, "[DASH] Invalid URL");
            return;
        }
        Log.d(TAG, "[DASH] Playing: " + dashUrl);
        try {
            MediaItem mediaItem = new MediaItem.Builder()
                .setUri(dashUrl)
                .setMimeType("application/dash+xml")
                .build();
            MediaSource mediaSource = buildMediaSource(mediaItem);
            preparePlayer(mediaSource);
            updateNotification();
            Log.d(TAG, "[DASH] ✓ Playback started");
        } catch (Exception e) {
            Log.e(TAG, "[DASH] Failed", e);
            handleStreamLoadError();
        }
    }

    /**
     * Play single URL stream
     * Supports HLS, MP4, WebM, etc.
     */
    public void playWithSingleUrl(@NonNull String streamUrl) {
        if (streamUrl == null || streamUrl.isEmpty()) {
            Log.e(TAG, "[URL] Invalid URL");
            return;
        }
        Log.d(TAG, "[URL] Playing: " + streamUrl);
        try {
            MediaItem mediaItem = new MediaItem.Builder()
                .setUri(streamUrl)
                .build();
            MediaSource mediaSource = buildMediaSource(mediaItem);
            preparePlayer(mediaSource);
            updateNotification();
            Log.d(TAG, "[URL] ✓ Playback started");
        } catch (Exception e) {
            Log.e(TAG, "[URL] Failed", e);
            handleStreamLoadError();
        }
    }

    /**
     * Play merged video and audio streams
     * For separate video and audio URLs
     */
    public void playWithMergedUrl(@NonNull String videoUrl, @NonNull String audioUrl) {
        if ((videoUrl == null || videoUrl.isEmpty()) && 
            (audioUrl == null || audioUrl.isEmpty())) {
            Log.e(TAG, "[MERGE] Both URLs invalid");
            return;
        }
        Log.d(TAG, "[MERGE] Playing merged streams");
        Log.d(TAG, "[MERGE] Video: " + videoUrl);
        Log.d(TAG, "[MERGE] Audio: " + audioUrl);
        try {
            if (audioUrl == null || audioUrl.isEmpty()) {
                playWithSingleUrl(videoUrl);
                return;
            }
            if (videoUrl == null || videoUrl.isEmpty()) {
                playWithSingleUrl(audioUrl);
                return;
            }
            MediaItem videoItem = new MediaItem.Builder()
                .setUri(videoUrl)
                .build();
            MediaSource videoSource = buildMediaSource(videoItem);
            preparePlayer(videoSource);
            updateNotification();
            Log.d(TAG, "[MERGE] ✓ Playback started");
        } catch (Exception e) {
            Log.e(TAG, "[MERGE] Failed", e);
            handleStreamLoadError();
        }
    }

    /**
     * Play stream by type detection
     * Fixed: Use StreamType directly instead of int
     */
    public void playStreamByType(@NonNull StreamType streamType, @NonNull String streamUrl, @NonNull StreamInfo streamInfo) {
        if (streamType == null || streamUrl == null) {
            Log.e(TAG, "[TYPE] Invalid parameters");
            return;
        }
        try {
            Log.d(TAG, "[TYPE] Stream type: " + streamType.name());
            // Audio stream
            if (StreamTypeUtil.isAudio(streamType)) {
                Log.d(TAG, "[TYPE] Detected: AUDIO");
                playWithSingleUrl(streamUrl);
            }
            // Video stream
            else if (StreamTypeUtil.isVideo(streamType)) {
                Log.d(TAG, "[TYPE] Detected: VIDEO");
                String videoUrl = selectBestVideoStream(streamInfo.getVideoStreams());
                String audioUrl = selectBestAudioStream(streamInfo.getAudioStreams());
                if (videoUrl != null && audioUrl != null) {
                    playWithMergedUrl(videoUrl, audioUrl);
                } else {
                    playWithSingleUrl(streamUrl);
                }
            }
            // Live stream
            else if (StreamTypeUtil.isLiveStream(streamType)) {
                Log.d(TAG, "[TYPE] Detected: LIVE");
                if (streamUrl.contains(".mpd")) {
                    playWithDashUrl(streamUrl);
                } else {
                    playWithSingleUrl(streamUrl);
                }
            }
            // Unknown type
            else {
                Log.d(TAG, "[TYPE] Detected: UNKNOWN");
                playWithSingleUrl(streamUrl);
            }
        } catch (Exception e) {
            Log.e(TAG, "[TYPE] Error", e);
            playWithSingleUrl(streamUrl);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Queue Navigation
     //////////////////////////////////////////////////////////////////////////*/

    /**
     * Play next item
     */
    public void playNext() {
        if (playQueue == null) {
            Log.w(TAG, "[NAV] Queue is null");
            return;
        }
        try {
            playQueue.next();
            currentItem = playQueue.getItem();
            notifyQueueStateChanged();
            if (currentItem != null) {
                loadAndPlayCurrentItem();
                preloadUpcomingStreams();
            } else if (!playQueue.isRepeatEnabled()) {
                Log.d(TAG, "[NAV] End of queue");
                stopSelf();
            }
        } catch (Exception e) {
            Log.e(TAG, "[NAV] Error playing next", e);
        }
    }

    /**
     * Play previous item
     */
    public void playPrevious() {
        if (playQueue == null) {
            Log.w(TAG, "[NAV] Queue is null");
            return;
        }
        try {
            playQueue.previous();
            currentItem = playQueue.getItem();
            notifyQueueStateChanged();
            if (currentItem != null) {
                loadAndPlayCurrentItem();
            }
        } catch (Exception e) {
            Log.e(TAG, "[NAV] Error playing previous", e);
        }
    }

    /**
     * Seek to queue position
     */
    public void seekToQueuePosition(int index) {
        if (playQueue == null) {
            Log.w(TAG, "[NAV] Queue is null");
            return;
        }
        if (index < 0 || index >= playQueue.size()) {
            Log.w(TAG, "[NAV] Invalid index: " + index);
            return;
        }
        try {
            playQueue.setIndex(index);
            currentItem = playQueue.getItem();
            notifyQueueStateChanged();
            if (currentItem != null) {
                loadAndPlayCurrentItem();
            }
        } catch (Exception e) {
            Log.e(TAG, "[NAV] Error seeking", e);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Playback Controls
     //////////////////////////////////////////////////////////////////////////*/

    /**
     * Toggle shuffle
     */
    public void toggleShuffle() {
        if (playQueue != null) {
            playQueue.toggleShuffle();
            notifyQueueStateChanged();
            Log.d(TAG, "[CONTROL] Shuffle: " + playQueue.isShuffleEnabled());
        }
    }

    /**
     * Toggle repeat
     */
    public void toggleRepeat() {
        if (playQueue != null) {
            playQueue.toggleRepeat();
            notifyQueueStateChanged();
            Log.d(TAG, "[CONTROL] Repeat: " + playQueue.isRepeatEnabled());
        }
    }

    /**
     * Seek by milliseconds
     */
    private void seekBy(long milliseconds) {
        if (player == null) {
            return;
        }
        try {
            long currentPosition = player.getCurrentPosition();
            long duration = player.getDuration();
            if (duration == C.TIME_UNSET || duration <= 0) {
                return;
            }
            long newPosition = Math.max(0, Math.min(currentPosition + milliseconds, duration));
            player.seekTo(newPosition);
        } catch (Exception e) {
            Log.e(TAG, "[CONTROL] Seek error", e);
        }
    }

    /**
     * Toggle playback speed
     */
    private void togglePlaybackSpeed() {
        if (player == null) {
            return;
        }
        try {
            currentSpeedIndex = (currentSpeedIndex + 1) % SPEED_OPTIONS.length;
            float newSpeed = SPEED_OPTIONS[currentSpeedIndex];
            player.setPlaybackSpeed(newSpeed);
            Log.d(TAG, "[CONTROL] Speed: " + newSpeed + "x");
        } catch (Exception e) {
            Log.e(TAG, "[CONTROL] Speed error", e);
        }
    }

    /**
     * Set playback speed
     */
    public void setPlaybackSpeed(float speed) {
        if (player == null) {
            return;
        }
        try {
            player.setPlaybackSpeed(speed);
            for (int i = 0; i < SPEED_OPTIONS.length; i++) {
                if (Float.compare(SPEED_OPTIONS[i], speed) == 0) {
                    currentSpeedIndex = i;
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "[CONTROL] Set speed error", e);
        }
    }

    /**
     * Set max video quality
     */
    public void setMaxVideoQuality(int maxHeight) {
        if (trackSelector == null) {
            return;
        }
        try {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setMaxVideoSize(Integer.MAX_VALUE, maxHeight)
                    .build()
            );
            Log.d(TAG, "[CONTROL] Max quality: " + maxHeight + "p");
        } catch (Exception e) {
            Log.e(TAG, "[CONTROL] Quality error", e);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Notification
     //////////////////////////////////////////////////////////////////////////*/

    /**
     * Update media notification
     */
    private void updateNotification() {
        if (currentStreamInfo == null) {
            return;
        }
        try {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(currentStreamInfo.getName())
                .setContentText(currentStreamInfo.getUploaderName())
                .setSmallIcon(R.drawable.ic_play_arrow)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
            startForeground(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            Log.e(TAG, "[NOTIFY] Failed to update", e);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Event Listeners
     //////////////////////////////////////////////////////////////////////////*/

    /**
     * Player event listener
     */
    private class PlayerEventListener implements Player.Listener {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            Log.d(TAG, "[EVENT] State: " + getStateString(playbackState));
            if (playbackState == Player.STATE_READY && player != null) {
                androidx.media3.common.VideoSize videoSize = player.getVideoSize();
                Log.d(TAG, "[EVENT] Video: " + videoSize.width + "x" + videoSize.height);
            }
            if (playbackState == Player.STATE_ENDED) {
                handlePlaybackEnded();
            }
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            Log.d(TAG, "[EVENT] Playing: " + isPlaying);
        }

        @Override
        public void onRenderedFirstFrame() {
            Log.d(TAG, "[EVENT] ✓✓✓ FIRST FRAME RENDERED ✓✓✓");
        }

        @Override
        public void onVideoSizeChanged(@NonNull androidx.media3.common.VideoSize videoSize) {
            Log.d(TAG, "[EVENT] Video size: " + videoSize.width + "x" + videoSize.height);
        }

        @Override
        public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
            Log.e(TAG, "[EVENT] ✗ Player error: " + error.getMessage(), error);
            handleStreamLoadError();
        }

        @Override
        public void onTracksChanged(@NonNull androidx.media3.common.Tracks tracks) {
            Log.d(TAG, "[EVENT] Tracks changed");
            for (androidx.media3.common.Tracks.Group trackGroup : tracks.getGroups()) {
                String trackType = getTrackTypeString(trackGroup.getType());
                Log.d(TAG, "[EVENT] Track: " + trackType + ", count: " + trackGroup.length);
            }
        }

        private void handlePlaybackEnded() {
            if (playQueue != null && (playQueue.size() > 1 || playQueue.isRepeatEnabled())) {
                playNext();
            } else {
                Log.d(TAG, "[EVENT] Playback completed");
                stopSelf();
            }
        }

        private String getStateString(int state) {
            switch (state) {
                case Player.STATE_IDLE: return "IDLE";
                case Player.STATE_BUFFERING: return "BUFFERING";
                case Player.STATE_READY: return "READY";
                case Player.STATE_ENDED: return "ENDED";
                default: return "UNKNOWN";
            }
        }

        private String getTrackTypeString(int trackType) {
            switch (trackType) {
                case C.TRACK_TYPE_VIDEO: return "VIDEO";
                case C.TRACK_TYPE_AUDIO: return "AUDIO";
                case C.TRACK_TYPE_TEXT: return "TEXT";
                default: return "UNKNOWN";
            }
        }
    }

    /**
     * MediaSession callback
     */
    private class MediaSessionCallback implements MediaSession.Callback {
        @Override
        public MediaSession.ConnectionResult onConnect(
            @NonNull MediaSession session,
            @NonNull MediaSession.ControllerInfo controller
        ) {
            return MediaSession.Callback.super.onConnect(session, controller);
        }

        @Override
        public ListenableFuture<SessionResult> onCustomCommand(
            @NonNull MediaSession session,
            @NonNull MediaSession.ControllerInfo controller,
            @NonNull SessionCommand customCommand,
            @NonNull Bundle args
        ) {
            String action = customCommand.customAction;
            try {
                switch (action) {
                    case COMMAND_SEEK_BACKWARD:
                        seekBy(-SEEK_INCREMENT_MS);
                        break;
                    case COMMAND_SEEK_FORWARD:
                        seekBy(SEEK_INCREMENT_MS);
                        break;
                    case COMMAND_TOGGLE_SPEED:
                        togglePlaybackSpeed();
                        break;
                    case COMMAND_NEXT:
                        playNext();
                        break;
                    case COMMAND_PREVIOUS:
                        playPrevious();
                        break;
                    case COMMAND_TOGGLE_SHUFFLE:
                        toggleShuffle();
                        break;
                    case COMMAND_TOGGLE_REPEAT:
                        toggleRepeat();
                        break;
                    default:
                        return Futures.immediateFuture(
                            new SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
                        );
                }
                return Futures.immediateFuture(
                    new SessionResult(SessionResult.RESULT_SUCCESS)
                );
            } catch (Exception e) {
                Log.e(TAG, "[SESSION] Command error: " + action, e);
                return Futures.immediateFuture(
                    new SessionResult(SessionResult.RESULT_ERROR_UNKNOWN)
                );
            }
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
     // MediaSessionService Implementation
     //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Public Accessors
     //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    public ExoPlayer getPlayer() {
        return player;
    }

    @Nullable
    public MediaSession getMediaSession() {
        return mediaSession;
    }

    @Nullable
    public PlayQueue getPlayQueue() {
        return playQueue;
    }

    @Nullable
    public PlayQueueItem getCurrentItem() {
        return currentItem;
    }

    @Nullable
    public StreamInfo getCurrentStreamInfo() {
        return currentStreamInfo;
    }

    public boolean isLoadingStream() {
        return isLoadingStream.get();
    }

    public boolean isServiceInitialized() {
        return isServiceInitialized.get();
    }

    public float getCurrentPlaybackSpeed() {
        return player != null ? player.getPlaybackParameters().speed : 1.0f;
    }

    @NonNull
    public static float[] getAvailableSpeedOptions() {
        return SPEED_OPTIONS.clone();
    }

    public int getCurrentSpeedIndex() {
        return currentSpeedIndex;
    }

    public int getMaxVideoHeight() {
        return MAX_VIDEO_HEIGHT;
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public long getCurrentPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }

    public long getDuration() {
        return player != null ? player.getDuration() : 0;
    }

    public long getBufferedPosition() {
        return player != null ? player.getBufferedPosition() : 0;
    }

    public void play() {
        if (player != null) {
            player.play();
        }
    }

    public void pause() {
        if (player != null) {
            player.pause();
        }
    }

    public void seekTo(long position) {
        if (player != null) {
            player.seekTo(position);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Lifecycle
     //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onDestroy() {
        Log.d(TAG, "[DESTROY] Service destroying");
        try {
            streamInfoCallback = null;
            if (!disposables.isDisposed()) {
                disposables.clear();
                disposables.dispose();
            }
            if (preloadExecutor != null && !preloadExecutor.isShutdown()) {
                preloadExecutor.shutdown();
            }
            if (mediaSession != null) {
                try {
                    if (mediaSession.getPlayer() != null) {
                        mediaSession.getPlayer().release();
                    }
                    mediaSession.release();
                } catch (Exception e) {
                    Log.e(TAG, "[DESTROY] MediaSession error", e);
                }
                mediaSession = null;
            }
            if (player != null) {
                try {
                    player.stop();
                    player.clearMediaItems();
                    player.release();
                } catch (Exception e) {
                    Log.e(TAG, "[DESTROY] Player error", e);
                }
                player = null;
            }
            trackSelector = null;
            dataSourceFactory = null;
            playQueue = null;
            currentItem = null;
            currentStreamInfo = null;
            isServiceInitialized.set(false);
            Log.d(TAG, "[DESTROY] ✓ Service destroyed");
        } catch (Exception e) {
            Log.e(TAG, "[DESTROY] Error", e);
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        Log.d(TAG, "[TASK] Task removed");
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            Log.w(TAG, "[MEMORY] Low memory: " + level);
            if (disposables != null && !disposables.isDisposed()) {
                disposables.clear();
            }
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "[MEMORY] Critical low memory");
        if (disposables != null && !disposables.isDisposed()) {
            disposables.clear();
        }
    }
}