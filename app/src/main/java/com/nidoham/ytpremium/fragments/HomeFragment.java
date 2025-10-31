package com.nidoham.ytpremium.fragments;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nidoham.ytpremium.R;
import com.nidoham.ytpremium.adapter.StreamsAdapter;
import com.nidoham.ytpremium.databinding.FragmentHomeBinding;
import com.nidoham.ytpremium.player.service.PlayerService;
import com.nidoham.ytpremium.prompt.KioskList;
import com.nidoham.ytpremium.util.Kiosk;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.ExtractorHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import android.content.Intent;
import com.nidoham.ytpremium.PlayerActivity;
import com.google.android.material.snackbar.Snackbar;
import org.schabi.newpipe.player.queue.PlayQueueItem;
import org.schabi.newpipe.player.queue.PlayQueue;

/**
 * হোম ফ্র্যাগমেন্ট - ক্যাটাগরি ভিত্তিক ভিডিও সার্চ এবং প্রদর্শন করে।
 * 
 * বৈশিষ্ট্য:
 * - ক্যাটাগরি চিপ সিলেকশন (All, Gaming, Sports, Music, News)
 * - RxJava 3 ভিত্তিক অ্যাসিঙ্ক সার্চ
 * - RecyclerView এ ভিডিও প্রদর্শন
 * - GridLayoutManager for landscape mode
 * - লোডিং ইন্ডিকেটর এবং এরর হ্যান্ডলিং
 * - পেজিনেশন সাপোর্ট (পরবর্তী পৃষ্ঠা লোড করা)
 * - Multiple item types support (Stream, Channel, Playlist)
 * 
 * @author NI Doha Mondol
 * @version 2.0
 */
public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final int SERVICE_ID = 0; // YouTube service ID
    
    private FragmentHomeBinding binding;
    private CompositeDisposable compositeDisposable;
    private StreamsAdapter streamsAdapter;
    
    private int currentCategory = 0;
    private SearchInfo currentSearchInfo;
    private boolean isLoading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, 
                             @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize CompositeDisposable
        compositeDisposable = new CompositeDisposable();
        
        // Setup RecyclerView
        setupRecyclerView();
        
        // Setup ChipGroup for category selection
        setupChipGroup();
        
        // Load initial category
        search(currentCategory);
    }

    /**
     * RecyclerView সেটআপ করে এবং StreamsAdapter ইনিশিয়ালাইজ করে।
     */
    private void setupRecyclerView() {
        // Initialize adapter with click listener
        streamsAdapter = new StreamsAdapter();
        streamsAdapter.setOnItemClickListener(new StreamsAdapter.OnItemClickListener() {
            @Override
            public void onStreamClick(StreamInfoItem item) {
                onVideoItemClick(item);
            }

            @Override
            public void onChannelClick(ChannelInfoItem item) {
                onChannelItemClick(item);
            }

            @Override
            public void onPlaylistClick(PlaylistInfoItem item) {
                onPlaylistItemClick(item);
            }
        });
        
        // Setup LayoutManager based on orientation
        RecyclerView.LayoutManager layoutManager = createLayoutManager();
        binding.recyclerView.setLayoutManager(layoutManager);
        binding.recyclerView.setAdapter(streamsAdapter);
        binding.recyclerView.setHasFixedSize(true);
        
        // Setup pagination
        setupPagination();
    }

    /**
     * ওরিয়েন্টেশনের উপর ভিত্তি করে LayoutManager তৈরি করে।
     * Portrait: LinearLayoutManager
     * Landscape: GridLayoutManager (2 columns)
     * 
     * @return RecyclerView.LayoutManager
     */
    private RecyclerView.LayoutManager createLayoutManager() {
        int orientation = getResources().getConfiguration().orientation;
        
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Grid layout for landscape with 2 columns
            return new GridLayoutManager(requireContext(), 2);
        } else {
            // Linear layout for portrait
            return new LinearLayoutManager(requireContext());
        }
    }

    /**
     * পেজিনেশন সেটআপ করে - স্ক্রল করার সময় আরও ভিডিও লোড করে।
     */
    private void setupPagination() {
        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                if (layoutManager != null && !isLoading) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition;
                    
                    // Get first visible item position based on LayoutManager type
                    if (layoutManager instanceof LinearLayoutManager) {
                        firstVisibleItemPosition = ((LinearLayoutManager) layoutManager)
                                .findFirstVisibleItemPosition();
                    } else if (layoutManager instanceof GridLayoutManager) {
                        firstVisibleItemPosition = ((GridLayoutManager) layoutManager)
                                .findFirstVisibleItemPosition();
                    } else {
                        return;
                    }
                    
                    // Load more when scrolled to bottom
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0
                            && totalItemCount >= 10 // Minimum items before loading more
                            && currentSearchInfo != null
                            && currentSearchInfo.hasNextPage()) {
                        loadMoreVideos();
                    }
                }
            }
        });
    }

    /**
     * চিপ গ্রুপ সেটআপ করে - ক্যাটাগরি সিলেকশন হ্যান্ডল করে।
     */
    private void setupChipGroup() {
        binding.cgCategories.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                int checkedId = checkedIds.get(0);
                currentCategory = getCategoryPosition(checkedId);
                search(currentCategory);
            }
        });
        
        // Set initial selection
        binding.chipAll.setChecked(true);
    }

    /**
     * চিপ আইডি থেকে ক্যাটাগরি পজিশন পায়।
     * 
     * @param chipId চিপের আইডি
     * @return ক্যাটাগরি পজিশন (0-4)
     */
    private int getCategoryPosition(int chipId) {
        if (chipId == binding.chipExplore.getId()) {
            return Kiosk.getCategoryPosition("all");
        } else if (chipId == binding.chipAll.getId()) {
            return Kiosk.getCategoryPosition("all");
        } else if (chipId == binding.chipGaming.getId()) {
            return Kiosk.getCategoryPosition("gaming");
        } else if (chipId == binding.chipSports.getId()) {
            return Kiosk.getCategoryPosition("sports");
        } else if (chipId == binding.chipMusic.getId()) {
            return Kiosk.getCategoryPosition("musics");
        } else if (chipId == binding.chipNews.getId()) {
            return Kiosk.getCategoryPosition("news");
        }
        return 0; // Default to "All"
    }

    /**
     * নির্দিষ্ট ক্যাটাগরির জন্য সার্চ করে।
     * 
     * প্রক্রিয়া:
     * 1. পজিশন থেকে সার্চ কোয়েরি তৈরি করে
     * 2. ExtractorHelper.searchFor() কল করে
     * 3. ফলাফল RecyclerView এ প্রদর্শন করে
     * 
     * @param position ক্যাটাগরি পজিশন (0-4)
     */
    private void search(final int position) {
        // Determine search query based on category
        String query = getSearchQuery(position);
        
        Log.d(TAG, "সার্চ শুরু: " + query);
        
        // Show loading
        showLoading(true);
        
        // Clear previous results
        streamsAdapter.clearItems();
        currentSearchInfo = null;
        
        // Perform search using ExtractorHelper
        compositeDisposable.add(
            ExtractorHelper.searchFor(
                SERVICE_ID,
                query,
                Collections.emptyList(), // Content filter: all types (videos, channels, playlists)
                "" // Sort filter: default (relevance)
            )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                this::handleSearchSuccess,
                this::handleSearchError
            )
        );
    }

    /**
     * পজিশন থেকে সার্চ কোয়েরি পায়।
     * 
     * @param position ক্যাটাগরি পজিশন
     * @return সার্চ কোয়েরি স্ট্রিং
     */
    private String getSearchQuery(int position) {
        if (position == Kiosk.getCategoryPosition("all")) {
            return KioskList.BANGLADESH_ALL_VIDEOS_QUERY;
        } else if (position == Kiosk.getCategoryPosition("gaming")) {
            return KioskList.BANGLADESH_GAMING_VIDEOS_QUERY;
        } else if (position == Kiosk.getCategoryPosition("sports")) {
            return KioskList.BANGLADESH_SHORTS_QUERY;
        } else if (position == Kiosk.getCategoryPosition("musics")) {
            return KioskList.BANGLADESH_MUSIC_VIDEOS_QUERY;
        } else if (position == Kiosk.getCategoryPosition("news")) {
            return KioskList.BANGLADESH_NEWS_VIDEOS_QUERY;
        }
        return "Bangladeshi trending videos"; // Default
    }

    /**
     * সার্চ সফল হলে কল হয়।
     * 
     * @param searchInfo সার্চ ফলাফল
     */
    private void handleSearchSuccess(SearchInfo searchInfo) {
        showLoading(false);
        currentSearchInfo = searchInfo;
        
        // Get all items (streams, channels, playlists)
        List<InfoItem> allItems = searchInfo.getRelatedItems();
        
        if (allItems.isEmpty()) {
            showEmptyState();
            Log.d(TAG, "কোনো আইটেম পাওয়া যায়নি");
        } else {
            // Update adapter with all items
            streamsAdapter.updateItems(allItems);
            binding.recyclerView.setVisibility(View.VISIBLE);
            
            // Log item types
            int streams = 0, channels = 0, playlists = 0;
            for (InfoItem item : allItems) {
                if (item instanceof StreamInfoItem) streams++;
                else if (item instanceof ChannelInfoItem) channels++;
                else if (item instanceof PlaylistInfoItem) playlists++;
            }
            
            Log.d(TAG, String.format("সার্চ সফল: %d টি আইটেম (ভিডিও: %d, চ্যানেল: %d, প্লেলিস্ট: %d)", 
                    allItems.size(), streams, channels, playlists));
        }
    }

    /**
     * সার্চ এরর হলে কল হয়।
     * 
     * @param throwable এরর অবজেক্ট
     */
    private void handleSearchError(Throwable throwable) {
        showLoading(false);
        Log.e(TAG, "সার্চ এরর", throwable);
        
        String errorMessage = "কন্টেন্ট লোড করতে ব্যর্থ";
        if (throwable.getMessage() != null) {
            errorMessage += ": " + throwable.getMessage();
        }
        
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
        showErrorState();
    }

    /**
     * পরবর্তী পৃষ্ঠার কন্টেন্ট লোড করে (পেজিনেশন)।
     */
    private void loadMoreVideos() {
        if (currentSearchInfo == null || !currentSearchInfo.hasNextPage() || isLoading) {
            return;
        }
        
        isLoading = true;
        Log.d(TAG, "পরবর্তী পৃষ্ঠা লোড করা হচ্ছে...");
        
        compositeDisposable.add(
            ExtractorHelper.getMoreSearchItems(
                SERVICE_ID,
                getSearchQuery(currentCategory),
                Collections.emptyList(),
                "",
                currentSearchInfo.getNextPage()
            )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                infoItemsPage -> {
                    isLoading = false;
                    
                    List<InfoItem> items = infoItemsPage.getItems();
                    
                    // Add to adapter
                    if (!items.isEmpty()) {
                        streamsAdapter.addItems(items);
                        Log.d(TAG, "পরবর্তী পৃষ্ঠা লোড: " + items.size() + " টি আইটেম");
                    }
                    
                    // Update next page info
                    if (infoItemsPage.hasNextPage()) {
                        // Update currentSearchInfo with new next page
                        // Note: This is a simplified approach
                    }
                },
                throwable -> {
                    isLoading = false;
                    Log.e(TAG, "পরবর্তী পৃষ্ঠা লোড এরর", throwable);
                    Toast.makeText(requireContext(), "আরও কন্টেন্ট লোড করতে ব্যর্থ", Toast.LENGTH_SHORT).show();
                }
            )
        );
    }

    /**
     * ভিডিও আইটেম ক্লিক হ্যান্ডল করে।
     * 
     * @param item ক্লিক করা StreamInfoItem
     */
    private void onVideoItemClick(StreamInfoItem streamInfo) {
        try {
            PlayQueueItem item = PlayQueueItem.from(streamInfo);
            List<PlayQueueItem> itemList = new ArrayList<>();
            itemList.add(item);
            PlayQueue queue = new PlayQueue(0, itemList, false);

            Intent intent = new Intent(getContext(), PlayerActivity.class);
            // Use the correct keys from PlayerService
            intent.putExtra(PlayerService.EXTRA_PLAY_QUEUE, queue);
        
        startActivity(intent);
        } catch (Exception e) {
            Snackbar.make(binding.getRoot(), "ভিডিও চালানো যাচ্ছে না: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * চ্যানেল আইটেম ক্লিক হ্যান্ডল করে।
     * 
     * @param item ক্লিক করা ChannelInfoItem
     */
    private void onChannelItemClick(ChannelInfoItem item) {
        Log.d(TAG, "চ্যানেল ক্লিক: " + item.getName());
        Toast.makeText(requireContext(), "Opening Channel: " + item.getName(), Toast.LENGTH_SHORT).show();
        
        // TODO: Navigate to channel page
        // Example:
        // Intent intent = new Intent(requireContext(), ChannelActivity.class);
        // intent.putExtra("CHANNEL_URL", item.getUrl());
        // startActivity(intent);
    }

    /**
     * প্লেলিস্ট আইটেম ক্লিক হ্যান্ডল করে।
     * 
     * @param item ক্লিক করা PlaylistInfoItem
     */
    private void onPlaylistItemClick(PlaylistInfoItem item) {
        Log.d(TAG, "প্লেলিস্ট ক্লিক: " + item.getName());
        Toast.makeText(requireContext(), "Opening Playlist: " + item.getName(), Toast.LENGTH_SHORT).show();
        
        // TODO: Navigate to playlist page
        // Example:
        // Intent intent = new Intent(requireContext(), PlaylistActivity.class);
        // intent.putExtra("PLAYLIST_URL", item.getUrl());
        // startActivity(intent);
    }

    /**
     * লোডিং স্টেট দেখায় বা লুকায়।
     * 
     * @param show true হলে দেখায়, false হলে লুকায়
     */
    private void showLoading(boolean show) {
        if (binding != null) {
            // If you have a ProgressBar in your layout, show/hide it
            // binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            
            // For now, just hide/show RecyclerView
            if (show) {
                binding.recyclerView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * খালি স্টেট দেখায় (কোনো কন্টেন্ট পাওয়া যায়নি)।
     */
    private void showEmptyState() {
        if (binding != null) {
            binding.recyclerView.setVisibility(View.VISIBLE);
            Toast.makeText(requireContext(), "কোনো কন্টেন্ট পাওয়া যায়নি", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * এরর স্টেট দেখায় (লোড করতে ব্যর্থ)।
     */
    private void showErrorState() {
        if (binding != null) {
            binding.recyclerView.setVisibility(View.VISIBLE);
            // TODO: Add error state view with retry button
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Update LayoutManager when orientation changes
        RecyclerView.LayoutManager layoutManager = createLayoutManager();
        binding.recyclerView.setLayoutManager(layoutManager);
        
        // Notify adapter to refresh
        if (streamsAdapter != null) {
            streamsAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Dispose all RxJava subscriptions
        if (compositeDisposable != null && !compositeDisposable.isDisposed()) {
            compositeDisposable.dispose();
        }
        
        binding = null;
    }
}