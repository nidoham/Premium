package com.nidoham.ytpremium.player.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultAllocator;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import org.schabi.newpipe.player.queue.PlayQueue;
import org.schabi.newpipe.player.queue.PlayQueueItem;
import com.nidoham.ytpremium.player.constant.PlayerConstants;
import com.nidoham.ytpremium.PlayerActivity;
import com.nidoham.ytpremium.R;

/**
 * Enhanced PlayerService with proper lifecycle management and UI-bound operation.
 * 
 * Key Features:
 * - Properly uses ExtractorHelper patterns with RxJava for async stream extraction
 * - Only runs when UI is active (bound service pattern)
 * - Efficient resource management with proper cleanup
 * - Comprehensive error handling and retry logic
 * - Smart caching with LruCache for stream data
 * - MediaSession integration for system playback controls
 * - Robust notification handling with proper foreground service lifecycle
 * 
 * Architecture:
 * - Service runs only when bound to active UI components
 * - Uses RxJava for all async operations with proper disposal
 * - Implements observer pattern for state updates to UI
 * - Follows Android best practices for media playback services
 */
public class PlayerService extends Service {

    private static final String TAG = "PlayerService";
    
    // Public constants for Intent extras
    public static final String EXTRA_PLAY_QUEUE = "EXTRA_PLAY_QUEUE";
    public static final String EXTRA_SERVICE_ID = "EXTRA_SERVICE_ID";
    
    // Configuration constants
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;
    private static final int MAX_CACHE_SIZE = 30;
    private static final int POSITION_UPDATE_INTERVAL_MS = 500;
    private static final int QUALITY_CHANGE_DELAY_MS = 300;
    private static final int BUFFER_MIN_MS = 2500;
    private static final int BUFFER_MAX_MS = 25000;
    private static final int BUFFER_PLAYBACK_MS = 1000;
    private static final int BUFFER_REBUFFER_MS = 2500;
    
    // Notification constants
    private static final String CHANNEL_ID = "player_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    // State management
    private final AtomicBoolean isServiceBound = new AtomicBoolean(false);
    private final AtomicBoolean isLoadingStream = new AtomicBoolean(false);
    private final AtomicBoolean isForegroundActive = new AtomicBoolean(false);
    
    // Core components
    private ExoPlayer exoPlayer;
    private MediaSessionCompat mediaSession;
    private DefaultTrackSelector trackSelector;
    private NotificationManager notificationManager;
    private PlayQueue playQueue;
    
    // Caching and scheduling
    private final LruCache<String, CachedStreamData> streamCache = new LruCache<>(MAX_CACHE_SIZE);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CompositeDisposable disposables = new CompositeDisposable();
    
    // Service binder
    private final PlayerServiceBinder binder = new PlayerServiceBinder();
    
    // Configuration
    private String currentQualityPreference = "720p";
    private int currentServiceId = 0;
    
    // Listener collections
    private final CopyOnWriteArrayList<PlaybackStateListener> playbackListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<MetadataListener> metadataListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<QueueListener> queueListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ErrorListener> errorListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<LoadingListener> loadingListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<QualityListener> qualityListeners = new CopyOnWriteArrayList<>();
    
    // Position tracking
    private final Runnable positionUpdateTask = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer != null && isServiceBound.get()) {
                long position = exoPlayer.getCurrentPosition();
                long duration = exoPlayer.getDuration();
                
                if (duration > 0 && position >= 0) {
                    notifyPositionUpdate(position, duration);
                }
                
                if (exoPlayer.isPlaying()) {
                    mainHandler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS);
                }
            }
        }
    };
    
    // ═══════════════════════════════════════════════════════════════════════
    // Listener Interfaces
    // ═══════════════════════════════════════════════════════════════════════
    
    public interface PlaybackStateListener {
        void onPlaybackStateChanged(int state, boolean isPlaying, long position, long duration);
        void onPositionUpdate(long position, long duration);
        void onPlaybackEnded();
    }
    
    public interface MetadataListener {
        void onMetadataLoaded(StreamInfo streamInfo);
        void onMetadataError(String error);
    }
    
    public interface QueueListener {
        void onQueueChanged(int currentIndex, int queueSize);
        void onCurrentItemChanged(PlayQueueItem item);
        void onQueueFinished();
    }
    
    public interface ErrorListener {
        void onPlaybackError(String error, Exception exception);
        void onStreamExtractionError(String error, Exception exception);
    }
    
    public interface LoadingListener {
        void onLoadingStarted(String message);
        void onLoadingProgress(String message);
        void onLoadingFinished();
    }
    
    public interface QualityListener {
        void onQualityChanged(String quality);
        void onAvailableQualitiesChanged(List<String> qualities);
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Cached Stream Data
    // ═══════════════════════════════════════════════════════════════════════
    
    private static class CachedStreamData {
        final StreamInfo streamInfo;
        final List<VideoStream> videoStreams;
        final List<AudioStream> audioStreams;
        final long cacheTimestamp;
        
        String selectedVideoUrl;
        String selectedAudioUrl;
        String currentQuality;

        CachedStreamData(StreamInfo info) {
            this.streamInfo = info;
            this.videoStreams = info.getVideoOnlyStreams() != null ? 
                new ArrayList<>(info.getVideoOnlyStreams()) : new ArrayList<>();
            this.audioStreams = info.getAudioStreams() != null ? 
                new ArrayList<>(info.getAudioStreams()) : new ArrayList<>();
            this.cacheTimestamp = System.currentTimeMillis();
        }

        void selectQuality(String qualityPreference) {
            this.currentQuality = qualityPreference;
            this.selectedVideoUrl = findBestVideoStream(qualityPreference);
            this.selectedAudioUrl = findBestAudioStream();
        }

        private String findBestVideoStream(String qualityPreference) {
            if (videoStreams.isEmpty()) {
                return null;
            }

            int targetHeight = parseQualityToHeight(qualityPreference);
            VideoStream bestMatch = null;
            int smallestDifference = Integer.MAX_VALUE;

            for (VideoStream stream : videoStreams) {
                if (stream == null || stream.getContent() == null) {
                    continue;
                }
                
                int streamHeight = stream.getHeight();
                int difference = Math.abs(streamHeight - targetHeight);
                
                if (difference < smallestDifference) {
                    smallestDifference = difference;
                    bestMatch = stream;
                }
            }
            
            return bestMatch != null ? bestMatch.getContent() : null;
        }

        private String findBestAudioStream() {
            if (audioStreams.isEmpty()) {
                return null;
            }

            AudioStream bestAudio = null;
            int highestBitrate = -1;

            for (AudioStream stream : audioStreams) {
                if (stream == null || stream.getContent() == null) {
                    continue;
                }
                
                int bitrate = stream.getAverageBitrate();
                if (bitrate > highestBitrate) {
                    highestBitrate = bitrate;
                    bestAudio = stream;
                }
            }
            
            return bestAudio != null ? bestAudio.getContent() : null;
        }

        private int parseQualityToHeight(String quality) {
            if (quality == null || quality.isEmpty()) {
                return 720;
            }
            
            try {
                return Integer.parseInt(quality.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse quality: " + quality, e);
                return 720;
            }
        }

        boolean hasValidStreams() {
            return selectedVideoUrl != null && !selectedVideoUrl.isEmpty() &&
                   selectedAudioUrl != null && !selectedAudioUrl.isEmpty();
        }
        
        boolean isCacheValid() {
            long cacheAgeMs = System.currentTimeMillis() - cacheTimestamp;
            return cacheAgeMs < TimeUnit.HOURS.toMillis(1);
        }
        
        List<String> getAvailableQualities() {
            List<String> qualities = new ArrayList<>();
            for (VideoStream stream : videoStreams) {
                if (stream != null) {
                    String quality = stream.getHeight() + "p";
                    if (!qualities.contains(quality)) {
                        qualities.add(quality);
                    }
                }
            }
            return qualities;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Service Binder
    // ═══════════════════════════════════════════════════════════════════════
    
    public class PlayerServiceBinder extends Binder {
        public PlayerService getService() {
            return PlayerService.this;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Service Lifecycle
    // ═══════════════════════════════════════════════════════════════════════
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        createNotificationChannel();
        initializePlayer();
        initializeMediaSession();
        setupPlayerEventListeners();
    }
    
    /**
     * Create notification channel for Android O and above.
     * Required for foreground service notifications.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager = getSystemService(NotificationManager.class);
            
            if (notificationManager != null) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Media Playback",
                    NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Controls for video and audio playback");
                channel.setShowBadge(false);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                channel.enableVibration(false);
                channel.setSound(null, null);
                
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created");
            }
        }
    }
    
    /**
     * Initialize ExoPlayer with optimized configuration.
     * Configures buffering, track selection, and playback parameters.
     */
    private void initializePlayer() {
        trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setPreferredAudioLanguage("en")
                .setForceHighestSupportedBitrate(false)
                .setAllowAudioMixedMimeTypeAdaptiveness(true)
                .setAllowVideoMixedMimeTypeAdaptiveness(false)
                .build()
        );

        LoadControl loadControl = new DefaultLoadControl.Builder()
            .setAllocator(new DefaultAllocator(true, 64 * 1024))
            .setBufferDurationsMs(
                BUFFER_MIN_MS,
                BUFFER_MAX_MS,
                BUFFER_PLAYBACK_MS,
                BUFFER_REBUFFER_MS
            )
            .setTargetBufferBytes(-1)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(10000, false)
            .build();

        exoPlayer = new ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
            .build();
        
        Log.d(TAG, "ExoPlayer initialized with optimized configuration");
    }
    
    /**
     * Initialize MediaSession for system integration.
     * Enables lock screen controls, notification media controls, and system UI integration.
     */
    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setActive(true);
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                handlePlayPauseToggle();
            }
            
            @Override
            public void onPause() {
                handlePlayPauseToggle();
            }
            
            @Override
            public void onSkipToNext() {
                handleSkipToNext();
            }
            
            @Override
            public void onSkipToPrevious() {
                handleSkipToPrevious();
            }
            
            @Override
            public void onSeekTo(long position) {
                handleSeekTo(position);
            }
            
            @Override
            public void onStop() {
                handleStop();
            }
        });
        
        Log.d(TAG, "MediaSession initialized with callbacks");
    }
    
    /**
     * Setup ExoPlayer event listeners for state management.
     * Monitors playback state changes, errors, and media transitions.
     */
    private void setupPlayerEventListeners() {
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Log.d(TAG, "Playback state: " + getStateString(playbackState));
                handlePlaybackStateChange(playbackState);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                Log.d(TAG, "Is playing: " + isPlaying);
                handleIsPlayingChange(isPlaying);
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.e(TAG, "Player error occurred", error);
                handlePlayerError(error);
            }
            
            @Override
            public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
                if (events.contains(Player.EVENT_TIMELINE_CHANGED) || 
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    updateMediaSessionMetadata();
                }
            }
        });
    }
    
    /**
     * Convert player state to readable string for logging.
     */
    private String getStateString(int state) {
        switch (state) {
            case Player.STATE_IDLE: return "IDLE";
            case Player.STATE_BUFFERING: return "BUFFERING";
            case Player.STATE_READY: return "READY";
            case Player.STATE_ENDED: return "ENDED";
            default: return "UNKNOWN";
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Service Binding (UI-bound operation)
    // ═══════════════════════════════════════════════════════════════════════
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service bound to UI component");
        isServiceBound.set(true);
        return binder;
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Service unbound from UI component");
        isServiceBound.set(false);
        
        if (exoPlayer == null || !exoPlayer.isPlaying()) {
            mainHandler.postDelayed(this::performUnbindCleanup, 1000);
        }
        
        return true;
    }
    
    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        Log.d(TAG, "Service rebound to UI component");
        isServiceBound.set(true);
        mainHandler.removeCallbacks(this::performUnbindCleanup);
    }
    
    /**
     * Cleanup when service is unbound and not playing.
     * Stops foreground service and releases resources if appropriate.
     */
    private void performUnbindCleanup() {
        if (!isServiceBound.get() && (exoPlayer == null || !exoPlayer.isPlaying())) {
            Log.d(TAG, "Performing unbind cleanup - stopping service");
            stopForegroundService();
            stopSelf();
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Command Handling
    // ═══════════════════════════════════════════════════════════════════════
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        Log.d(TAG, "Command received: " + action);

        switch (action) {
            case PlayerConstants.ACTION_PLAY:
                handlePlayCommand(intent);
                break;
            case PlayerConstants.ACTION_PAUSE:
                handlePlayPauseToggle();
                break;
            case PlayerConstants.ACTION_STOP:
                handleStop();
                break;
            case PlayerConstants.ACTION_NEXT:
                handleSkipToNext();
                break;
            case PlayerConstants.ACTION_PREVIOUS:
                handleSkipToPrevious();
                break;
            case PlayerConstants.ACTION_SEEK:
                long position = intent.getLongExtra(PlayerConstants.EXTRA_SEEK_POSITION, -1);
                if (position >= 0) {
                    handleSeekTo(position);
                }
                break;
            case PlayerConstants.ACTION_CHANGE_QUALITY:
                String quality = intent.getStringExtra(PlayerConstants.EXTRA_QUALITY_ID);
                if (quality != null) {
                    handleQualityChange(quality);
                }
                break;
            default:
                Log.w(TAG, "Unknown action: " + action);
        }

        return START_NOT_STICKY;
    }
    
    /**
     * Handle play command with queue initialization.
     * Extracts PlayQueue from intent and begins playback.
     */
    private void handlePlayCommand(Intent intent) {
        Serializable queueSerializable = intent.getSerializableExtra(EXTRA_PLAY_QUEUE);
        
        if (!(queueSerializable instanceof PlayQueue)) {
            String error = "Invalid play queue provided";
            Log.e(TAG, error);
            notifyPlaybackError(error, new IllegalArgumentException(error));
            return;
        }
        
        PlayQueue queue = (PlayQueue) queueSerializable;
        int serviceId = intent.getIntExtra(EXTRA_SERVICE_ID, 0);
        
        if (queue == null || queue.isEmpty()) {
            String error = "Empty play queue";
            Log.e(TAG, error);
            notifyPlaybackError(error, new IllegalArgumentException(error));
            return;
        }
        
        this.playQueue = queue;
        this.currentServiceId = serviceId;
        
        Log.d(TAG, "Play queue initialized: " + queue.size() + " items, service ID: " + serviceId);
        
        startForegroundService();
        notifyQueueChanged();
        playCurrentQueueItem();
    }
    
    /**
     * Start foreground service with notification.
     * Required for continuous playback when UI is in background.
     */
    private void startForegroundService() {
        if (isForegroundActive.getAndSet(true)) {
            return;
        }
        
        try {
            Notification notification = buildNotification();
            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "Foreground service started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service", e);
            isForegroundActive.set(false);
        }
    }
    
    /**
     * Stop foreground service and remove notification.
     */
    private void stopForegroundService() {
        if (!isForegroundActive.getAndSet(false)) {
            return;
        }
        
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
            mainHandler.removeCallbacks(positionUpdateTask);
            Log.d(TAG, "Foreground service stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping foreground service", e);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Playback Control
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Play current item in queue using proper stream extraction.
     * Implements async extraction with RxJava patterns.
     */
    private void playCurrentQueueItem() {
        if (playQueue == null || playQueue.getItem() == null) {
            String error = "No item available in queue";
            Log.e(TAG, error);
            notifyPlaybackError(error, new IllegalStateException(error));
            return;
        }

        PlayQueueItem item = playQueue.getItem();
        String videoUrl = item.getUrl();
        
        Log.d(TAG, "Playing: " + item.getTitle() + " [" + videoUrl + "]");
        
        notifyCurrentItemChanged(item);
        updateNotification();

        CachedStreamData cachedData = streamCache.get(videoUrl);
        if (cachedData != null && cachedData.isCacheValid()) {
            Log.d(TAG, "Using cached stream data");
            cachedData.selectQuality(currentQualityPreference);
            if (cachedData.hasValidStreams()) {
                notifyMetadataLoaded(cachedData.streamInfo);
                notifyAvailableQualitiesChanged(cachedData.getAvailableQualities());
                playStreamWithUrls(cachedData.selectedVideoUrl, cachedData.selectedAudioUrl);
                return;
            }
        }

        extractStreamAndPlay(videoUrl);
    }
    
    /**
     * Extract stream information using proper async patterns with retry logic.
     * Uses RxJava for background processing and error handling.
     */
    private void extractStreamAndPlay(String videoUrl) {
        if (isLoadingStream.getAndSet(true)) {
            Log.w(TAG, "Stream extraction already in progress");
            return;
        }

        AtomicInteger retryCount = new AtomicInteger(0);

        Single<StreamInfo> extractionSingle = Single.fromCallable(() -> {
            Log.d(TAG, "Extracting stream info for: " + videoUrl);
            return StreamInfo.getInfo(NewPipe.getService(currentServiceId), videoUrl);
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .retryWhen(errors -> errors.flatMap(error -> {
            int attempt = retryCount.incrementAndGet();
            if (attempt > MAX_RETRIES) {
                return Flowable.error(error);
            }
            Log.d(TAG, "Retry attempt " + attempt + " of " + MAX_RETRIES);
            notifyLoadingProgress("Retrying... Attempt " + attempt);
            return Flowable.timer(RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
        }));

        Disposable disposable = extractionSingle
            .doOnSubscribe(d -> {
                notifyLoadingStarted("Loading stream information...");
            })
            .doFinally(() -> {
                isLoadingStream.set(false);
                notifyLoadingFinished();
            })
            .subscribe(
                streamInfo -> handleStreamExtractionSuccess(videoUrl, streamInfo),
                error -> handleStreamExtractionError(videoUrl, error)
            );

        disposables.add(disposable);
    }
    
    /**
     * Handle successful stream extraction.
     * Caches data, selects quality, and begins playback.
     */
    private void handleStreamExtractionSuccess(String videoUrl, StreamInfo streamInfo) {
        Log.d(TAG, "Stream extraction successful: " + streamInfo.getName());

        CachedStreamData streamData = new CachedStreamData(streamInfo);
        streamData.selectQuality(currentQualityPreference);
        streamCache.put(videoUrl, streamData);

        notifyMetadataLoaded(streamInfo);
        notifyAvailableQualitiesChanged(streamData.getAvailableQualities());

        if (!streamData.hasValidStreams()) {
            String error = "No playable streams found";
            Log.e(TAG, error);
            notifyStreamExtractionError(error, new IllegalStateException(error));
            skipToNextIfAvailable();
            return;
        }

        playStreamWithUrls(streamData.selectedVideoUrl, streamData.selectedAudioUrl);
    }
    
    /**
     * Handle stream extraction failure.
     * Notifies listeners and attempts to skip to next item.
     */
    private void handleStreamExtractionError(String videoUrl, Throwable error) {
        String errorMessage = "Failed to extract stream: " + error.getMessage();
        Log.e(TAG, errorMessage, error);
        
        notifyStreamExtractionError(errorMessage, 
            error instanceof Exception ? (Exception) error : new Exception(error));
        notifyMetadataError(errorMessage);
        
        skipToNextIfAvailable();
    }
    
    /**
     * Play merged video and audio streams using ExoPlayer.
     * Creates separate media sources and merges them for synchronized playback.
     */
    private void playStreamWithUrls(String videoUrl, String audioUrl) {
        if (videoUrl == null || audioUrl == null) {
            String error = "Invalid stream URLs";
            Log.e(TAG, error);
            notifyPlaybackError(error, new IllegalArgumentException(error));
            return;
        }

        try {
            Log.d(TAG, "Preparing playback");
            Log.d(TAG, "Video URL: " + videoUrl);
            Log.d(TAG, "Audio URL: " + audioUrl);

            DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                             "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true);

            MediaItem videoMediaItem = new MediaItem.Builder()
                .setUri(videoUrl)
                .setMimeType(MimeTypes.VIDEO_MP4)
                .build();

            ProgressiveMediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .setContinueLoadingCheckIntervalBytes(1024 * 1024)
                .createMediaSource(videoMediaItem);

            MediaItem audioMediaItem = new MediaItem.Builder()
                .setUri(audioUrl)
                .setMimeType(MimeTypes.AUDIO_AAC)
                .build();

            ProgressiveMediaSource audioSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .setContinueLoadingCheckIntervalBytes(512 * 1024)
                .createMediaSource(audioMediaItem);

            MergingMediaSource mergedSource = new MergingMediaSource(
                true, false, videoSource, audioSource
            );

            exoPlayer.stop();
            exoPlayer.clearMediaItems();
            exoPlayer.setMediaSource(mergedSource);
            exoPlayer.prepare();

            mainHandler.postDelayed(() -> {
                if (exoPlayer != null && exoPlayer.getPlaybackState() != Player.STATE_IDLE) {
                    exoPlayer.setPlayWhenReady(true);
                    exoPlayer.play();
                    Log.d(TAG, "Playback started");
                }
            }, 300);

        } catch (Exception e) {
            String errorMessage = "Failed to prepare playback: " + e.getMessage();
            Log.e(TAG, errorMessage, e);
            notifyPlaybackError(errorMessage, e);
        }
    }
    
    /**
     * Handle play/pause toggle action.
     */
    private void handlePlayPauseToggle() {
        if (exoPlayer == null) {
            Log.w(TAG, "Cannot toggle play/pause - player not initialized");
            return;
        }

        if (exoPlayer.isPlaying()) {
            exoPlayer.pause();
            Log.d(TAG, "Playback paused");
        } else {
            exoPlayer.play();
            Log.d(TAG, "Playback resumed");
        }
    }
    
    /**
     * Handle skip to next item in queue.
     */
    private void handleSkipToNext() {
        if (playQueue == null) {
            Log.w(TAG, "Cannot skip to next - no queue");
            return;
        }

        if (playQueue.getIndex() < playQueue.size() - 1) {
            playQueue.next();
            Log.d(TAG, "Skipping to next item: " + playQueue.getIndex());
            notifyQueueChanged();
            playCurrentQueueItem();
        } else {
            Log.d(TAG, "Already at last item in queue");
        }
    }
    
    /**
     * Handle skip to previous item in queue.
     */
    private void handleSkipToPrevious() {
        if (playQueue == null) {
            Log.w(TAG, "Cannot skip to previous - no queue");
            return;
        }

        if (playQueue.getIndex() > 0) {
            playQueue.previous();
            Log.d(TAG, "Skipping to previous item: " + playQueue.getIndex());
            notifyQueueChanged();
            playCurrentQueueItem();
        } else {
            Log.d(TAG, "Already at first item in queue");
        }
    }
    
    /**
     * Handle seek to specific position.
     */
    private void handleSeekTo(long position) {
        if (exoPlayer == null) {
            Log.w(TAG, "Cannot seek - player not initialized");
            return;
        }

        exoPlayer.seekTo(position);
        Log.d(TAG, "Seeked to position: " + position + "ms");
        updateMediaSessionPlaybackState();
    }
    
    /**
     * Handle stop command.
     */
    private void handleStop() {
        Log.d(TAG, "Stopping playback");
        
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        }
        
        stopForegroundService();
        stopSelf();
    }
    
    /**
     * Handle quality change request.
     * Smoothly transitions to new quality while maintaining playback position.
     */
    private void handleQualityChange(String newQuality) {
        if (newQuality == null || newQuality.equals(currentQualityPreference)) {
            Log.d(TAG, "Quality unchanged: " + newQuality);
            return;
        }

        if (playQueue == null || playQueue.getItem() == null || exoPlayer == null) {
            Log.w(TAG, "Cannot change quality - invalid state");
            return;
        }

        String videoUrl = playQueue.getItem().getUrl();
        CachedStreamData streamData = streamCache.get(videoUrl);

        if (streamData == null) {
            Log.w(TAG, "No cached data for quality change");
            return;
        }

        long currentPosition = exoPlayer.getCurrentPosition();
        boolean wasPlaying = exoPlayer.isPlaying();

        streamData.selectQuality(newQuality);

        if (!streamData.hasValidStreams()) {
            String error = "Selected quality not available: " + newQuality;
            Log.e(TAG, error);
            notifyPlaybackError(error, new IllegalArgumentException(error));
            return;
        }

        Log.d(TAG, "Changing quality to: " + newQuality);
        currentQualityPreference = newQuality;

        exoPlayer.stop();
        playStreamWithUrls(streamData.selectedVideoUrl, streamData.selectedAudioUrl);

        mainHandler.postDelayed(() -> {
            if (exoPlayer != null) {
                exoPlayer.seekTo(currentPosition);
                if (wasPlaying) {
                    exoPlayer.setPlayWhenReady(true);
                }
                Log.d(TAG, "Quality change completed");
            }
        }, QUALITY_CHANGE_DELAY_MS);

        notifyQualityChanged(newQuality);
    }
    
    /**
     * Skip to next item if available, otherwise finish queue.
     */
    private void skipToNextIfAvailable() {
        if (playQueue != null && playQueue.getIndex() < playQueue.size() - 1) {
            mainHandler.postDelayed(this::handleSkipToNext, 1500);
        } else {
            Log.d(TAG, "No more items in queue");
            notifyQueueFinished();
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Player Event Handlers
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Handle playback state changes from ExoPlayer.
     */
    private void handlePlaybackStateChange(int playbackState) {
        notifyPlaybackStateChanged();
        updateMediaSessionPlaybackState();
        updateNotification();

        if (playbackState == Player.STATE_READY) {
            updateMediaSessionMetadata();
        } else if (playbackState == Player.STATE_ENDED) {
            handlePlaybackEnded();
        }
    }
    
    /**
     * Handle is playing state changes from ExoPlayer.
     */
    private void handleIsPlayingChange(boolean isPlaying) {
        notifyPlaybackStateChanged();
        updateMediaSessionPlaybackState();
        updateNotification();

        if (isPlaying) {
            startPositionUpdates();
        } else {
            stopPositionUpdates();
        }
    }
    
    /**
     * Handle player errors from ExoPlayer.
     */
    private void handlePlayerError(PlaybackException error) {
        String errorMessage = "Playback error: " + error.getMessage();
        notifyPlaybackError(errorMessage, error);
        skipToNextIfAvailable();
    }
    
    /**
     * Handle playback ended event.
     */
    private void handlePlaybackEnded() {
        Log.d(TAG, "Playback ended");
        notifyPlaybackEnded();
        skipToNextIfAvailable();
    }
    
    /**
     * Start periodic position updates.
     */
    private void startPositionUpdates() {
        mainHandler.removeCallbacks(positionUpdateTask);
        mainHandler.post(positionUpdateTask);
    }
    
    /**
     * Stop periodic position updates.
     */
    private void stopPositionUpdates() {
        mainHandler.removeCallbacks(positionUpdateTask);
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Notification Management
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Build media notification with playback controls.
     */
    private Notification buildNotification() {
        Intent contentIntent = new Intent(this, PlayerActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = "Loading...";
        String artist = "BongoTube";

        if (playQueue != null && playQueue.getItem() != null) {
            PlayQueueItem item = playQueue.getItem();
            title = item.getTitle() != null ? item.getTitle() : "Unknown Title";
            artist = item.getUploader() != null ? item.getUploader() : "BongoTube";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_play_arrow)
            .setContentIntent(contentPendingIntent)
            .setOngoing(exoPlayer != null && exoPlayer.isPlaying())
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(createStopPendingIntent())
            );

        builder.addAction(createNotificationAction(
            R.drawable.ic_skip_previous, "Previous", PlayerConstants.ACTION_PREVIOUS, 10
        ));

        if (exoPlayer != null && exoPlayer.isPlaying()) {
            builder.addAction(createNotificationAction(
                R.drawable.ic_pause, "Pause", PlayerConstants.ACTION_PAUSE, 11
            ));
        } else {
            builder.addAction(createNotificationAction(
                R.drawable.ic_play_arrow, "Play", PlayerConstants.ACTION_PAUSE, 11
            ));
        }

        builder.addAction(createNotificationAction(
            R.drawable.ic_skip_next, "Next", PlayerConstants.ACTION_NEXT, 12
        ));

        builder.addAction(createNotificationAction(
            R.drawable.ic_close, "Close", PlayerConstants.ACTION_STOP, 13
        ));

        return builder.build();
    }
    
    /**
     * Create notification action with pending intent.
     */
    private NotificationCompat.Action createNotificationAction(
        int iconResId, String title, String action, int requestCode
    ) {
        Intent intent = new Intent(this, PlayerService.class);
        intent.setAction(action);
        
        PendingIntent pendingIntent = PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Action(iconResId, title, pendingIntent);
    }
    
    /**
     * Create stop pending intent for notification.
     */
    private PendingIntent createStopPendingIntent() {
        Intent intent = new Intent(this, PlayerService.class);
        intent.setAction(PlayerConstants.ACTION_STOP);
        
        return PendingIntent.getService(
            this, 99, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
    
    /**
     * Update notification display.
     */
    private void updateNotification() {
        if (!isForegroundActive.get()) {
            return;
        }

        try {
            Notification notification = buildNotification();
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to update notification", e);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // MediaSession Management
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Update MediaSession metadata with current item information.
     */
    private void updateMediaSessionMetadata() {
        if (mediaSession == null || playQueue == null || playQueue.getItem() == null) {
            return;
        }

        PlayQueueItem item = playQueue.getItem();
        long duration = exoPlayer != null ? exoPlayer.getDuration() : -1;

        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, 
                item.getTitle() != null ? item.getTitle() : "Unknown")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, 
                item.getUploader() != null ? item.getUploader() : "BongoTube")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "BongoTube");

        if (duration > 0) {
            builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        }

        mediaSession.setMetadata(builder.build());
    }
    
    /**
     * Update MediaSession playback state.
     */
    private void updateMediaSessionPlaybackState() {
        if (mediaSession == null || exoPlayer == null) {
            return;
        }

        int state = convertToMediaSessionState(exoPlayer.getPlaybackState(), exoPlayer.isPlaying());
        long position = Math.max(0, exoPlayer.getCurrentPosition());
        float speed = exoPlayer.getPlaybackParameters().speed;

        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder()
            .setState(state, position, speed)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_SEEK_TO |
                PlaybackStateCompat.ACTION_STOP |
                PlaybackStateCompat.ACTION_PLAY_PAUSE
            );

        if (playQueue != null) {
            builder.setActiveQueueItemId(playQueue.getIndex());
        }

        mediaSession.setPlaybackState(builder.build());
    }
    
    /**
     * Convert ExoPlayer state to MediaSession state.
     */
    private int convertToMediaSessionState(int playerState, boolean isPlaying) {
        if (isLoadingStream.get()) {
            return PlaybackStateCompat.STATE_BUFFERING;
        }

        switch (playerState) {
            case Player.STATE_BUFFERING:
                return PlaybackStateCompat.STATE_BUFFERING;
            case Player.STATE_READY:
                return isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
            case Player.STATE_ENDED:
                return PlaybackStateCompat.STATE_STOPPED;
            default:
                return PlaybackStateCompat.STATE_NONE;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Public API for UI Components
    // ═══════════════════════════════════════════════════════════════════════
    
    @Nullable
    public ExoPlayer getPlayer() {
        return exoPlayer;
    }
    
    @Nullable
    public StreamInfo getCurrentStreamInfo() {
        if (playQueue == null || playQueue.getItem() == null) {
            return null;
        }
        
        CachedStreamData cached = streamCache.get(playQueue.getItem().getUrl());
        return cached != null ? cached.streamInfo : null;
    }
    
    @Nullable
    public List<String> getAvailableQualities() {
        if (playQueue == null || playQueue.getItem() == null) {
            return null;
        }
        
        CachedStreamData cached = streamCache.get(playQueue.getItem().getUrl());
        return cached != null ? cached.getAvailableQualities() : null;
    }
    
    @Nullable
    public String getCurrentQuality() {
        return currentQualityPreference;
    }
    
    public void setQuality(String quality) {
        handleQualityChange(quality);
    }
    
    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
    }
    
    public int getPlayerState() {
        if (isLoadingStream.get()) {
            return PlayerConstants.STATE_BUFFERING;
        }
        if (exoPlayer == null) {
            return PlayerConstants.STATE_STOPPED;
        }
        
        switch (exoPlayer.getPlaybackState()) {
            case Player.STATE_BUFFERING:
                return PlayerConstants.STATE_BUFFERING;
            case Player.STATE_READY:
                return exoPlayer.isPlaying() ? 
                    PlayerConstants.STATE_PLAYING : PlayerConstants.STATE_PAUSED;
            case Player.STATE_ENDED:
                return PlayerConstants.STATE_ENDED;
            default:
                return PlayerConstants.STATE_STOPPED;
        }
    }
    
    @Nullable
    public PlayQueue getPlayQueue() {
        return playQueue;
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Listener Registration
    // ═══════════════════════════════════════════════════════════════════════
    
    public void addPlaybackStateListener(PlaybackStateListener listener) {
        if (listener != null && !playbackListeners.contains(listener)) {
            playbackListeners.add(listener);
            
            if (exoPlayer != null) {
                mainHandler.post(() -> listener.onPlaybackStateChanged(
                    getPlayerState(),
                    exoPlayer.isPlaying(),
                    exoPlayer.getCurrentPosition(),
                    exoPlayer.getDuration()
                ));
            }
        }
    }
    
    public void removePlaybackStateListener(PlaybackStateListener listener) {
        playbackListeners.remove(listener);
    }
    
    public void addMetadataListener(MetadataListener listener) {
        if (listener != null && !metadataListeners.contains(listener)) {
            metadataListeners.add(listener);
            
            StreamInfo info = getCurrentStreamInfo();
            if (info != null) {
                mainHandler.post(() -> listener.onMetadataLoaded(info));
            }
        }
    }
    
    public void removeMetadataListener(MetadataListener listener) {
        metadataListeners.remove(listener);
    }
    
    public void addQueueListener(QueueListener listener) {
        if (listener != null && !queueListeners.contains(listener)) {
            queueListeners.add(listener);
            
            if (playQueue != null) {
                mainHandler.post(() -> {
                    listener.onQueueChanged(playQueue.getIndex(), playQueue.size());
                    if (playQueue.getItem() != null) {
                        listener.onCurrentItemChanged(playQueue.getItem());
                    }
                });
            }
        }
    }
    
    public void removeQueueListener(QueueListener listener) {
        queueListeners.remove(listener);
    }
    
    public void addErrorListener(ErrorListener listener) {
        if (listener != null && !errorListeners.contains(listener)) {
            errorListeners.add(listener);
        }
    }
    
    public void removeErrorListener(ErrorListener listener) {
        errorListeners.remove(listener);
    }
    
    public void addLoadingListener(LoadingListener listener) {
        if (listener != null && !loadingListeners.contains(listener)) {
            loadingListeners.add(listener);
        }
    }
    
    public void removeLoadingListener(LoadingListener listener) {
        loadingListeners.remove(listener);
    }
    
    public void addQualityListener(QualityListener listener) {
        if (listener != null && !qualityListeners.contains(listener)) {
            qualityListeners.add(listener);
            
            mainHandler.post(() -> {
                listener.onQualityChanged(currentQualityPreference);
                List<String> qualities = getAvailableQualities();
                if (qualities != null) {
                    listener.onAvailableQualitiesChanged(qualities);
                }
            });
        }
    }
    
    public void removeQualityListener(QualityListener listener) {
        qualityListeners.remove(listener);
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Listener Notification Methods
    // ═══════════════════════════════════════════════════════════════════════
    
    private void notifyPlaybackStateChanged() {
        if (exoPlayer == null) {
            return;
        }

        int state = getPlayerState();
        boolean isPlaying = exoPlayer.isPlaying();
        long position = exoPlayer.getCurrentPosition();
        long duration = exoPlayer.getDuration();

        for (PlaybackStateListener listener : playbackListeners) {
            mainHandler.post(() -> 
                listener.onPlaybackStateChanged(state, isPlaying, position, duration)
            );
        }
    }
    
    private void notifyPositionUpdate(long position, long duration) {
        for (PlaybackStateListener listener : playbackListeners) {
            mainHandler.post(() -> listener.onPositionUpdate(position, duration));
        }
    }
    
    private void notifyPlaybackEnded() {
        for (PlaybackStateListener listener : playbackListeners) {
            mainHandler.post(listener::onPlaybackEnded);
        }
    }
    
    private void notifyMetadataLoaded(StreamInfo streamInfo) {
        for (MetadataListener listener : metadataListeners) {
            mainHandler.post(() -> listener.onMetadataLoaded(streamInfo));
        }
    }
    
    private void notifyMetadataError(String error) {
        for (MetadataListener listener : metadataListeners) {
            mainHandler.post(() -> listener.onMetadataError(error));
        }
    }
    
    private void notifyQueueChanged() {
        if (playQueue == null) {
            return;
        }

        int index = playQueue.getIndex();
        int size = playQueue.size();

        for (QueueListener listener : queueListeners) {
            mainHandler.post(() -> listener.onQueueChanged(index, size));
        }
    }
    
    private void notifyCurrentItemChanged(PlayQueueItem item) {
        for (QueueListener listener : queueListeners) {
            mainHandler.post(() -> listener.onCurrentItemChanged(item));
        }
    }
    
    private void notifyQueueFinished() {
        for (QueueListener listener : queueListeners) {
            mainHandler.post(listener::onQueueFinished);
        }
    }
    
    private void notifyPlaybackError(String error, Exception exception) {
        for (ErrorListener listener : errorListeners) {
            mainHandler.post(() -> listener.onPlaybackError(error, exception));
        }
    }
    
    private void notifyStreamExtractionError(String error, Exception exception) {
        for (ErrorListener listener : errorListeners) {
            mainHandler.post(() -> listener.onStreamExtractionError(error, exception));
        }
    }
    
    private void notifyLoadingStarted(String message) {
        for (LoadingListener listener : loadingListeners) {
            mainHandler.post(() -> listener.onLoadingStarted(message));
        }
    }
    
    private void notifyLoadingProgress(String message) {
        for (LoadingListener listener : loadingListeners) {
            mainHandler.post(() -> listener.onLoadingProgress(message));
        }
    }
    
    private void notifyLoadingFinished() {
        for (LoadingListener listener : loadingListeners) {
            mainHandler.post(listener::onLoadingFinished);
        }
    }
    
    private void notifyQualityChanged(String quality) {
        for (QualityListener listener : qualityListeners) {
            mainHandler.post(() -> listener.onQualityChanged(quality));
        }
    }
    
    private void notifyAvailableQualitiesChanged(List<String> qualities) {
        for (QualityListener listener : qualityListeners) {
            mainHandler.post(() -> listener.onAvailableQualitiesChanged(qualities));
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Service Cleanup
    // ═══════════════════════════════════════════════════════════════════════
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed - performing cleanup");
        
        stopForegroundService();
        stopPositionUpdates();
        
        disposables.clear();
        mainHandler.removeCallbacksAndMessages(null);
        
        playbackListeners.clear();
        metadataListeners.clear();
        queueListeners.clear();
        errorListeners.clear();
        loadingListeners.clear();
        qualityListeners.clear();
        
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.release();
            exoPlayer = null;
        }
        
        trackSelector = null;
        streamCache.evictAll();
        playQueue = null;
        
        Log.d(TAG, "Service cleanup completed");
    }
}