package com.nidoham.ytpremium.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.snackbar.Snackbar;
import com.nidoham.ytpremium.PlayerActivity;
import com.nidoham.ytpremium.R;
import com.nidoham.ytpremium.adapter.VideoAdapter;
import com.nidoham.ytpremium.database.AppDatabase;
import com.nidoham.ytpremium.database.VideoHistory;
import com.nidoham.ytpremium.database.VideoHistoryDao;
import com.nidoham.ytpremium.databinding.FragmentShortsBinding;
import com.nidoham.ytpremium.model.Video;
import com.nidoham.ytpremium.prompt.KioskList;
import com.nidoham.ytpremium.stream.StreamManager;

import org.schabi.newpipe.extractor.ExtractorHelper;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.schabi.newpipe.player.queue.PlayQueueItem;
import org.schabi.newpipe.player.queue.PlayQueue;

/**
 * শর্টস ফ্র্যাগমেন্ট - YouTube Shorts স্টাইলে ভার্টিক্যাল ভিডিও প্লেয়ার।
 * 
 * বৈশিষ্ট্য:
 * - NewPipe Extractor দিয়ে YouTube Shorts ফেচ করা
 * - ExoPlayer দিয়ে ভিডিও প্লেব্যাক
 * - ViewPager2 দিয়ে ভার্টিক্যাল স্ক্রলিং
 * - অটো-প্লে এবং লুপিং
 * - পেজিনেশন সাপোর্ট
 * - RxJava 3 ভিত্তিক অ্যাসিঙ্ক ডেটা লোডিং
 * - Room ডাটাবেস দিয়ে ভিডিও হিস্টোরি ম্যানেজমেন্ট
 * - StreamManager দিয়ে নেটওয়ার্ক/ডিভাইস অনুযায়ী অটো কোয়ালিটি সিলেকশন
 * 
 * @author NI Doha Mondol
 * @version 2.5 (Fixed stream selection and ExoPlayer setup)
 */
public class ShortsFragment extends Fragment {
    private static final String TAG = "ShortsFragment";
    private static final int SERVICE_ID = 0; // YouTube service ID
    private static final int MAX_DURATION_SECONDS = 180; // 3 minutes for shorts
    private static final int MAX_VIDEOS_TO_PROCESS = 10;
    private static final int LOAD_MORE_THRESHOLD = 5;

    private FragmentShortsBinding binding;
    private ExoPlayer exoPlayer;
    private VideoAdapter adapter;
    private List<Video> videoList;
    private List<StreamInfoItem> streamInfoItems;
    private CompositeDisposable compositeDisposable;
    private StreamManager streamManager;

    private SearchInfo currentSearchInfo;
    private boolean isLoading = false;
    private boolean isInitialLoad = true;
    private int currentVideoIndex = 0;
    private boolean isFragmentAlive = false;

    // Room Database
    private VideoHistoryDao historyDao;
    private List<String> watchedVideoIds = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentShortsBinding.inflate(inflater, container, false);
        isFragmentAlive = true;
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Log.d(TAG, "onViewCreated called");

        // Initialize StreamManager
        streamManager = new StreamManager(requireContext());

        // Initialize database
        historyDao = AppDatabase.getDatabase(requireContext()).videoHistoryDao();

        // Load watched video IDs from Room
        compositeDisposable = new CompositeDisposable();
        compositeDisposable.add(
            Single.fromCallable(() -> historyDao.getAllVideoIds())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    ids -> {
                        if (!isFragmentAlive) return;
                        watchedVideoIds = ids;
                        Log.d(TAG, "Loaded " + watchedVideoIds.size() + " watched video IDs");
                        initializeAndFetch();
                    },
                    throwable -> {
                        if (!isFragmentAlive) return;
                        Log.e(TAG, "Failed to load history", throwable);
                        initializeAndFetch();
                    }
                )
        );
    }

    private void initializeAndFetch() {
        videoList = new ArrayList<>();
        streamInfoItems = new ArrayList<>();
        initializePlayer();

        adapter = new VideoAdapter(videoList, exoPlayer);
        binding.videoPager.setAdapter(adapter);
        binding.videoPager.setOffscreenPageLimit(1);

        setupPageChangeCallback();
        showLoading(true);
        fetchShortsVideos();
    }

    private void initializePlayer() {
        // Create DataSource.Factory for network requests
        DefaultHttpDataSource.Factory dataSourceFactory = 
            new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");

        exoPlayer = new ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
            .build();
            
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                Log.e(TAG, "Player error: " + error.getMessage(), error);
                if (isFragmentAlive && getContext() != null) {
                    Toast.makeText(getContext(), "প্লেয়ার এরর। পরবর্তী ভিডিওতে যান।", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                String state = playbackState == Player.STATE_IDLE ? "IDLE" :
                              playbackState == Player.STATE_BUFFERING ? "BUFFERING" :
                              playbackState == Player.STATE_READY ? "READY" : "ENDED";
                Log.d(TAG, "Playback state: " + state);
            }
        });

        Log.d(TAG, "ExoPlayer initialized");
    }

    private void setupPageChangeCallback() {
        binding.videoPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentVideoIndex = position;

                Log.d(TAG, "Page selected: " + position + " / " + videoList.size());

                binding.videoPager.post(() -> {
                    if (!isFragmentAlive) return;
                    
                    PlayerView playerView = findPlayerViewAtPosition(position);
                    if (playerView != null) {
                        Log.d(TAG, "Playing video at position: " + position);
                        adapter.playVideo(position, playerView);

                        // Save to history after play starts
                        if (position < streamInfoItems.size()) {
                            StreamInfoItem item = streamInfoItems.get(position);
                            String videoId = extractVideoIdFromUrl(item.getUrl());
                            
                            if (videoId != null && !watchedVideoIds.contains(videoId)) {
                                watchedVideoIds.add(videoId);
                                compositeDisposable.add(
                                    io.reactivex.rxjava3.core.Completable.fromAction(() ->
                                        historyDao.insert(new VideoHistory(videoId))
                                    ).subscribeOn(Schedulers.io()).subscribe()
                                );
                            }
                        }
                    } else {
                        Log.e(TAG, "PlayerView not found at position: " + position);
                    }
                });

                if (position >= videoList.size() - LOAD_MORE_THRESHOLD && !isLoading && currentSearchInfo != null) {
                    Log.d(TAG, "Near end, loading more videos...");
                    loadMoreVideos();
                }
            }
        });

        binding.videoPager.setOnLongClickListener(v -> {
            if (currentVideoIndex < streamInfoItems.size()) {
                playWithNewPipePlayer();
            }
            return true;
        });
    }

    private void fetchShortsVideos() {
        if (isLoading || !isFragmentAlive) {
            Log.d(TAG, "Already loading or fragment destroyed, skipping fetch");
            return;
        }

        isLoading = true;
        Log.d(TAG, "Fetching shorts videos...");

        String query = KioskList.BANGLADESH_SHORTS_QUERY;

        compositeDisposable.add(
            ExtractorHelper.searchFor(
                SERVICE_ID,
                query,
                Collections.singletonList("videos"),
                ""
            )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                this::handleSearchSuccess,
                this::handleSearchError
            )
        );
    }

    private void handleSearchSuccess(SearchInfo searchInfo) {
        if (!isFragmentAlive) return;
        
        Log.d(TAG, "Search successful, processing results...");
        currentSearchInfo = searchInfo;

        List<StreamInfoItem> shortVideos = filterShortVideos(searchInfo.getRelatedItems());
        Log.d(TAG, "Found " + shortVideos.size() + " short videos after history filter");

        if (shortVideos.isEmpty()) {
            isLoading = false;
            showLoading(false);
            if (getContext() != null) {
                Toast.makeText(getContext(), "কোনো নতুন শর্ট ভিডিও পাওয়া যায়নি", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        int videosToProcess = Math.min(shortVideos.size(), MAX_VIDEOS_TO_PROCESS);
        List<StreamInfoItem> videosToLoad = shortVideos.subList(0, videosToProcess);
        Log.d(TAG, "Processing " + videosToLoad.size() + " videos");
        processVideosSequentially(videosToLoad);
    }

    private List<StreamInfoItem> filterShortVideos(List<InfoItem> items) {
        List<StreamInfoItem> shortVideos = new ArrayList<>();

        for (InfoItem item : items) {
            if (item instanceof StreamInfoItem) {
                StreamInfoItem streamItem = (StreamInfoItem) item;
                long duration = streamItem.getDuration();

                if (duration == -1 || duration <= MAX_DURATION_SECONDS) {
                    String videoId = extractVideoIdFromUrl(streamItem.getUrl());
                    if (videoId != null && !watchedVideoIds.contains(videoId)) {
                        shortVideos.add(streamItem);
                    }
                }
            }
        }

        return shortVideos;
    }

    private void processVideosSequentially(List<StreamInfoItem> streamItems) {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);

        for (StreamInfoItem streamItem : streamItems) {
            compositeDisposable.add(
                ExtractorHelper.getStreamInfo(SERVICE_ID, streamItem.getUrl(), false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        streamInfo -> {
                            if (!isFragmentAlive) return;
                            
                            processedCount.incrementAndGet();

                            // Use the new factory method to create Video
                            int maxResolution = streamManager.getOptimalQualityForNetwork();
                            Video video = Video.from(streamInfo, maxResolution);

                            if (video != null && video.isValid()) {
                                videoList.add(video);
                                streamInfoItems.add(streamItem);
                                successCount.incrementAndGet();

                                adapter.notifyItemInserted(videoList.size() - 1);

                                Log.d(TAG, "Video added (#" + videoList.size() + "): " + streamInfo.getName());

                                if (successCount.get() == 1 && isInitialLoad) {
                                    isInitialLoad = false;
                                    showLoading(false);

                                    binding.videoPager.post(() -> {
                                        if (!isFragmentAlive) return;
                                        PlayerView firstPlayerView = findPlayerViewAtPosition(0);
                                        if (firstPlayerView != null) {
                                            Log.d(TAG, "Playing first video");
                                            adapter.playVideo(0, firstPlayerView);
                                        }
                                    });
                                }
                            } else {
                                Log.e(TAG, "No video URL found for: " + streamInfo.getName());
                                logAvailableStreams(streamInfo);
                            }

                            if (processedCount.get() == streamItems.size()) {
                                isLoading = false;
                                showLoading(false);
                                Log.d(TAG, "All videos processed. Success: " + successCount.get() + "/" + streamItems.size());

                                if (successCount.get() == 0 && getContext() != null) {
                                    Toast.makeText(getContext(),
                                        "ভিডিও URL এক্সট্র্যাক্ট করতে ব্যর্থ",
                                        Toast.LENGTH_SHORT).show();
                                }
                            }
                        },
                        throwable -> {
                            if (!isFragmentAlive) return;
                            
                            processedCount.incrementAndGet();
                            Log.e(TAG, "Error extracting video info for: " + streamItem.getName(), throwable);

                            if (processedCount.get() == streamItems.size()) {
                                isLoading = false;
                                showLoading(false);

                                if (successCount.get() == 0 && getContext() != null) {
                                    Toast.makeText(getContext(),
                                        "ভিডিও লোড করতে ব্যর্থ",
                                        Toast.LENGTH_LONG).show();
                                }
                            }
                        }
                    )
            );
        }
    }

    private void logAvailableStreams(StreamInfo streamInfo) {
        Log.d(TAG, "=== Available Streams for: " + streamInfo.getName() + " ===");
        Log.d(TAG, "HLS URL: " + (streamInfo.getHlsUrl() != null ? "Available" : "Not available"));

        List<VideoStream> videoStreams = streamInfo.getVideoStreams();
        Log.d(TAG, "Video streams (combined): " + (videoStreams != null ? videoStreams.size() : 0));
        if (videoStreams != null) {
            for (int i = 0; i < Math.min(5, videoStreams.size()); i++) {
                VideoStream stream = videoStreams.get(i);
                Log.d(TAG, "  - " + stream.getResolution() + " " + stream.getFormat() + 
                     " (video-only: " + stream.isVideoOnly() + ")");
            }
        }

        Log.d(TAG, "Video-only streams: " + (streamInfo.getVideoOnlyStreams() != null ? 
            streamInfo.getVideoOnlyStreams().size() : 0));
        Log.d(TAG, "Audio streams: " + (streamInfo.getAudioStreams() != null ? 
            streamInfo.getAudioStreams().size() : 0));
        Log.d(TAG, "Optimal resolution for current network: " + 
            streamManager.getOptimalQualityForNetwork() + "p");
        Log.d(TAG, "=====================================");
    }

    private void handleSearchError(Throwable throwable) {
        if (!isFragmentAlive) return;
        
        isLoading = false;
        showLoading(false);
        Log.e(TAG, "Search error", throwable);

        if (getContext() != null) {
            String errorMessage = "শর্ট ভিডিও লোড করতে ব্যর্থ: " + throwable.getMessage();
            Toast.makeText(getContext(), errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    private void loadMoreVideos() {
        if (currentSearchInfo == null || !currentSearchInfo.hasNextPage() || isLoading || !isFragmentAlive) {
            Log.d(TAG, "Cannot load more: hasNextPage=" +
                  (currentSearchInfo != null && currentSearchInfo.hasNextPage()) +
                  ", isLoading=" + isLoading);
            return;
        }

        isLoading = true;
        Log.d(TAG, "Loading more videos from page...");

        compositeDisposable.add(
            ExtractorHelper.getMoreSearchItems(
                SERVICE_ID,
                "shorts",
                Collections.singletonList("videos"),
                "",
                currentSearchInfo.getNextPage()
            )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                infoItemsPage -> {
                    if (!isFragmentAlive) return;
                    
                    Log.d(TAG, "Got more items: " + infoItemsPage.getItems().size());

                    List<StreamInfoItem> shortVideos = filterShortVideos(infoItemsPage.getItems());

                    if (!shortVideos.isEmpty()) {
                        int videosToProcess = Math.min(shortVideos.size(), LOAD_MORE_THRESHOLD);
                        List<StreamInfoItem> videosToLoad = shortVideos.subList(0, videosToProcess);
                        Log.d(TAG, "Processing " + videosToLoad.size() + " more videos");
                        processVideosSequentially(videosToLoad);
                    } else {
                        isLoading = false;
                        Log.d(TAG, "No new short videos found");
                    }
                },
                throwable -> {
                    if (!isFragmentAlive) return;
                    
                    isLoading = false;
                    Log.e(TAG, "Load more error", throwable);
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "আরো ভিডিও লোড করতে ব্যর্থ", Toast.LENGTH_SHORT).show();
                    }
                }
            )
        );
    }

    private void playWithNewPipePlayer() {
        if (currentVideoIndex >= streamInfoItems.size() || !isFragmentAlive) {
            return;
        }

        try {
            StreamInfoItem streamInfo = streamInfoItems.get(currentVideoIndex);
            PlayQueueItem item = PlayQueueItem.from(streamInfo);
            List<PlayQueueItem> itemList = new ArrayList<>();
            itemList.add(item);
            PlayQueue queue = new PlayQueue(0, itemList, false);

            Intent intent = new Intent(getContext(), PlayerActivity.class);
            intent.putExtra("PLAY_QUEUE", queue);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening PlayerActivity", e);
            if (isFragmentAlive) {
                Snackbar.make(binding.getRoot(),
                    "ভিডিও চালানো যাচ্ছে না: " + e.getMessage(),
                    Snackbar.LENGTH_LONG).show();
            }
        }
    }

    private PlayerView findPlayerViewAtPosition(int position) {
        View view = binding.videoPager.getChildAt(0);
        if (view instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) view;
            RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(position);
            if (viewHolder != null) {
                return viewHolder.itemView.findViewById(R.id.player_view);
            }
        }
        return null;
    }

    private String extractVideoIdFromUrl(String url) {
        if (url == null) return null;
        try {
            if (url.contains("youtube.com/watch")) {
                String[] parts = url.split("v=");
                if (parts.length > 1) {
                    return parts[1].split("&")[0];
                }
            } else if (url.contains("youtu.be/")) {
                String[] parts = url.split("youtu.be/");
                if (parts.length > 1) {
                    return parts[1].split("\\?")[0];
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract video ID from: " + url, e);
        }
        return null;
    }

    private void showLoading(boolean show) {
        if (binding != null && isFragmentAlive) {
            binding.videoPager.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (exoPlayer != null) {
            exoPlayer.pause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (exoPlayer != null && exoPlayer.getPlaybackState() == Player.STATE_READY) {
            exoPlayer.play();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        Log.d(TAG, "onDestroyView called");
        
        isFragmentAlive = false;

        if (compositeDisposable != null && !compositeDisposable.isDisposed()) {
            compositeDisposable.dispose();
        }

        if (adapter != null) {
            adapter.stopVideo();
        }
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }

        binding = null;
    }
}