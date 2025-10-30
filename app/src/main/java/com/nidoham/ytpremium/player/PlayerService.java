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
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
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
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "[DESTROY] Service destroying");
        try {
            streamInfoCallback = null;
            if (!disposables.isDisposed()) {
                disposables.clear();
                disposables.dispose();
            }
            if (!preloadExecutor.isShutdown()) {
                preloadExecutor.shutdownNow();
            }
            if (mediaSession != null) {
                mediaSession.release();
                mediaSession = null;
            }
            if (player != null) {
                player.release();
                player = null;
            }
            trackSelector = null;
            dataSourceFactory = null;
            isServiceInitialized.set(false);
            Log.d(TAG, "[DESTROY] ✓ Service destroyed");
        } catch (Exception e) {
            Log.e(TAG, "[DESTROY] Error during cleanup", e);
        }
        super.onDestroy();
    }
    
    private void initializeComponents() {
        Log.d(TAG, "[INIT] Initializing components");
        dataSourceFactory = new DefaultDataSource.Factory(this);
        initializePlayer();
        initializeMediaSession();
    }
    
    private void initializePlayer() {
        Log.d(TAG, "[PLAYER] Initializing ExoPlayer");
        trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setMaxVideoSize(Integer.MAX_VALUE, MAX_VIDEO_HEIGHT)
                .setForceHighestSupportedBitrate(false)
                .build()
        );

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build();
            
        player = new ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build();
            
        player.addListener(new PlayerEventListener());
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        Log.d(TAG, "[PLAYER] ✓ ExoPlayer initialized");
    }

    private void initializeMediaSession() {
        if (player == null) throw new IllegalStateException("Player must be initialized first");
        Log.d(TAG, "[SESSION] Initializing MediaSession");
        mediaSession = new MediaSession.Builder(this, player)
            .setId("youtube_player_session")
            .setCallback(new MediaSessionCallback())
            .build();
        // FIXED: Removed the erroneous call to setMediaSession(mediaSession);
        Log.d(TAG, "[SESSION] ✓ MediaSession initialized");
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "YouTube Player", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Media playback controls");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    public void initializeQueue(@NonNull PlayQueue queue, int startIndex) {
        if (queue.isEmpty()) {
            Log.e(TAG, "[QUEUE] Cannot initialize with an empty queue.");
            stopSelf();
            return;
        }
        synchronized (this) {
            this.playQueue = queue;
            this.playQueue.setIndex(startIndex);
            this.currentItem = playQueue.getItem();
            if (currentItem != null) {
                Log.d(TAG, "[QUEUE] ✓ Queue initialized. Size: " + queue.size() + ", Index: " + startIndex);
                Log.d(TAG, "[QUEUE] Current item: " + currentItem.getTitle());
                notifyQueueStateChanged();
                loadAndPlayCurrentItem();
                preloadUpcomingStreams();
            } else {
                Log.e(TAG, "[QUEUE] Could not get item at start index " + startIndex);
                stopSelf();
            }
        }
    }

    private void loadAndPlayCurrentItem() {
        if (currentItem == null) {
            Log.e(TAG, "[LOAD] Cannot load, current item is null.");
            handleStreamLoadError();
            return;
        }
        if (!isLoadingStream.compareAndSet(false, true)) {
            Log.w(TAG, "[LOAD] Already loading a stream, request ignored.");
            return;
        }
        
        Log.d(TAG, "[LOAD] Loading: " + currentItem.getTitle());
        notifyStreamLoadingStarted(currentItem);
        
        disposables.add(
            ExtractorHelper.getStreamInfo(currentItem.getServiceId(), currentItem.getUrl(), false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    this::onStreamInfoLoaded,
                    this::onStreamLoadingError
                )
        );
    }
    
    private void onStreamInfoLoaded(@NonNull StreamInfo streamInfo) {
        Log.d(TAG, "[LOAD] ✓ Stream info loaded for: " + streamInfo.getName());
        isLoadingStream.set(false);
        currentStreamInfo = streamInfo;
        notifyStreamInfoLoaded(currentItem, streamInfo);
        prepareAndPlayMediaSource(streamInfo);
    }

    private void onStreamLoadingError(@NonNull Throwable error) {
        Log.e(TAG, "[LOAD] ✗ Stream loading failed for: " + (currentItem != null ? currentItem.getTitle() : "unknown item"), error);
        isLoadingStream.set(false);
        if (currentItem != null) {
            notifyStreamLoadingFailed(currentItem, (Exception) error);
        }
        handleStreamLoadError();
    }
    
    private void prepareAndPlayMediaSource(@NonNull StreamInfo streamInfo) {
        try {
            MediaItem mediaItem = buildMediaItemFrom(streamInfo);
            if (mediaItem == null) {
                throw new Exception("No playable URL found in StreamInfo.");
            }
            
            Log.d(TAG, "[PLAYER] Preparing playback for URL: " + mediaItem.localConfiguration.uri);
            player.stop();
            player.clearMediaItems();
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(true);
            updateNotification();
            Log.d(TAG, "[PLAYER] ✓ Playback started for: " + streamInfo.getName());
        } catch (Exception e) {
            Log.e(TAG, "[PLAYER] Failed to prepare media source", e);
            handleStreamLoadError();
        }
    }

    @Nullable
    private MediaItem buildMediaItemFrom(@NonNull StreamInfo streamInfo) {
        String streamUrl = selectBestStreamUrl(streamInfo);
        if (streamUrl == null || streamUrl.isEmpty()) {
            return null;
        }

        MediaMetadata metadata = new MediaMetadata.Builder()
            .setTitle(streamInfo.getName())
            .setArtist(streamInfo.getUploaderName())
            .build();
        
        MediaItem.Builder builder = new MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaMetadata(metadata);

        if (streamUrl.endsWith(".mpd")) {
            builder.setMimeType("application/dash+xml");
        }

        return builder.build();
    }

    @Nullable
    private String selectBestStreamUrl(@NonNull StreamInfo streamInfo) {
        if (streamInfo.getDashMpdUrl() != null && !streamInfo.getDashMpdUrl().isEmpty()) {
            Log.d(TAG, "[SELECT] Selected DASH stream.");
            return streamInfo.getDashMpdUrl();
        }
        if (streamInfo.getHlsUrl() != null && !streamInfo.getHlsUrl().isEmpty()) {
            Log.d(TAG, "[SELECT] Selected HLS stream.");
            return streamInfo.getHlsUrl();
        }
        
        String videoUrl = selectBestVideoStream(streamInfo.getVideoStreams());
        if (videoUrl != null) {
            Log.d(TAG, "[SELECT] Selected video stream.");
            return videoUrl;
        }
        
        String audioUrl = selectBestAudioStream(streamInfo.getAudioStreams());
        if (audioUrl != null) {
            Log.d(TAG, "[SELECT] Selected audio stream.");
            return audioUrl;
        }
        
        Log.w(TAG, "[SELECT] No specific stream found, falling back to original URL.");
        return streamInfo.getUrl();
    }

    @Nullable
    private String selectBestVideoStream(@Nullable List<VideoStream> videoStreams) {
        if (videoStreams == null || videoStreams.isEmpty()) return null;
        
        return videoStreams.stream()
            .filter(s -> s.getContent() != null && !s.getContent().isEmpty() && s.getHeight() <= MAX_VIDEO_HEIGHT)
            .max((s1, s2) -> Integer.compare(s1.getHeight(), s2.getHeight()))
            .map(VideoStream::getContent)
            .orElse(null);
    }

    @Nullable
    private String selectBestAudioStream(@Nullable List<AudioStream> audioStreams) {
        if (audioStreams == null || audioStreams.isEmpty()) return null;

        return audioStreams.stream()
            .filter(s -> s.getContent() != null && !s.getContent().isEmpty())
            .max((s1, s2) -> Long.compare(s1.getAverageBitrate(), s2.getAverageBitrate()))
            .map(AudioStream::getContent)
            .orElse(null);
    }
    
    private void handleStreamLoadError() {
        if (playQueue != null && (playQueue.size() > 1 || playQueue.isRepeatEnabled())) {
            Log.d(TAG, "[ERROR] Loading failed, trying next item in queue.");
            playNext();
        } else {
            Log.e(TAG, "[ERROR] Loading failed, no more items to play. Stopping service.");
            stopSelf();
        }
    }
    
    private void updateNotification() {
        if (currentItem == null) return;
        
        Intent intent = new Intent(this, PlayerActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentItem.getTitle())
            .setContentText(currentItem.getUploader())
            .setSmallIcon(R.drawable.ic_play_arrow)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build();
            
        startForeground(NOTIFICATION_ID, notification);
    }

    private void preloadUpcomingStreams() {
        if (playQueue == null || playQueue.isEmpty()) return;
        
        preloadExecutor.execute(() -> {
            int currentIndex = playQueue.getIndex();
            for (int i = 1; i <= PRELOAD_AHEAD_COUNT; i++) {
                int nextIndex = currentIndex + i;
                if (nextIndex < playQueue.size()) {
                    PlayQueueItem upcomingItem = playQueue.getItem(nextIndex);
                    if (upcomingItem != null) {
                        ExtractorHelper.getStreamInfo(upcomingItem.getServiceId(), upcomingItem.getUrl(), false)
                            .subscribeOn(Schedulers.io())
                            .subscribe(
                                info -> Log.d(TAG, "[PRELOAD] ✓ Preloaded: " + info.getName()),
                                err -> Log.w(TAG, "[PRELOAD] ✗ Failed to preload: " + upcomingItem.getTitle())
                            );
                    }
                }
            }
        });
    }

    public void playNext() {
        if (playQueue == null) return;
        playQueue.next();
        currentItem = playQueue.getItem();
        notifyQueueStateChanged();
        if (currentItem != null) {
            loadAndPlayCurrentItem();
            preloadUpcomingStreams();
        } else if (!playQueue.isRepeatEnabled()) {
            Log.d(TAG, "[NAV] End of queue.");
            stopSelf();
        }
    }

    public void playPrevious() {
        if (playQueue == null) return;
        playQueue.previous();
        currentItem = playQueue.getItem();
        notifyQueueStateChanged();
        if (currentItem != null) {
            loadAndPlayCurrentItem();
        }
    }
    
    private void notifyQueueStateChanged() {
        if (streamInfoCallback != null && playQueue != null) {
            streamInfoCallback.onQueueStateChanged(playQueue.getIndex(), playQueue.size());
        }
    }

    private void notifyStreamLoadingStarted(@NonNull PlayQueueItem item) {
        if (streamInfoCallback != null) streamInfoCallback.onStreamLoadingStarted(item);
    }

    private void notifyStreamInfoLoaded(@NonNull PlayQueueItem item, @NonNull StreamInfo info) {
        if (streamInfoCallback != null) streamInfoCallback.onStreamInfoLoaded(item, info);
    }

    private void notifyStreamLoadingFailed(@NonNull PlayQueueItem item, @NonNull Exception error) {
        if (streamInfoCallback != null) streamInfoCallback.onStreamLoadingFailed(item, error);
    }
    
    public void setStreamInfoCallback(@Nullable StreamInfoCallback callback) {
        this.streamInfoCallback = callback;
    }

    @Nullable
    public ExoPlayer getPlayer() {
        return player;
    }

    @Nullable
    public PlayQueue getPlayQueue() {
        return playQueue;
    }
    
    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }
    
    private class PlayerEventListener implements Player.Listener {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_ENDED) {
                Log.d(TAG, "[EVENT] Playback ended.");
                playNext();
            }
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            Log.e(TAG, "[EVENT] ✗ Player error", error);
            handleStreamLoadError();
        }
    }

    private class MediaSessionCallback implements MediaSession.Callback {
        @NonNull
        @Override
        public MediaSession.ConnectionResult onConnect(@NonNull MediaSession session, @NonNull MediaSession.ControllerInfo controller) {
            return MediaSession.Callback.super.onConnect(session, controller);
        }
    }
}