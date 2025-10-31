package com.nidoham.ytpremium;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.nidoham.ytpremium.R;
import com.nidoham.ytpremium.adapter.StreamsAdapter;
import com.nidoham.ytpremium.databinding.ActivitySearchResultBinding;
import com.nidoham.ytpremium.player.service.PlayerService;
import com.nidoham.ytpremium.util.SearchFilter;

import java.util.ArrayList;
import org.schabi.newpipe.extractor.ExtractorHelper;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.schabi.newpipe.player.queue.PlayQueue;
import org.schabi.newpipe.player.queue.PlayQueueItem;

public class SearchResultActivity extends AppCompatActivity {

    private ActivitySearchResultBinding binding;
    private CompositeDisposable compositeDisposable;

    private String currentQuery = "";
    private SearchFilter currentFilter = SearchFilter.ALL;
    private SearchInfo lastSearchInfo;
    private int serviceId = 0;

    private StreamsAdapter streamsAdapter;

    private boolean isLoading = false;
    private boolean hasMorePages = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchResultBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        compositeDisposable = new CompositeDisposable();

        setupViews();
        setupRecyclerView();
        setupListeners();
        
        handleIncomingIntent();
    }
    
    private void handleIncomingIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("SEARCH_QUERY")) {
            String query = intent.getStringExtra("SEARCH_QUERY");
            if (query != null && !query.trim().isEmpty()) {
                binding.searchEditText.setText(query);
                binding.btnClearSearch.setVisibility(View.VISIBLE);
                currentQuery = query;
                performSearch(query);
            }
        }
    }

    private void setupViews() {
        binding.btnClearSearch.setVisibility(View.GONE);
        updateSearchHint();
    }

    private void setupRecyclerView() {
        streamsAdapter = new StreamsAdapter();
        
        streamsAdapter.setOnItemClickListener(new StreamsAdapter.OnItemClickListener() {
            @Override
            public void onStreamClick(StreamInfoItem streamInfo) {
                try {
                    PlayQueueItem item = PlayQueueItem.from(streamInfo);
                    List<PlayQueueItem> itemList = new ArrayList<>();
                    itemList.add(item);
                    PlayQueue queue = new PlayQueue(0, itemList, false);

                    Intent intent = new Intent(getApplicationContext(), PlayerActivity.class);
                    // Use the correct keys from PlayerService
                    intent.putExtra(PlayerService.EXTRA_PLAY_QUEUE, queue);
        
                    startActivity(intent);
                } catch (Exception e) {
                    Snackbar.make(binding.getRoot(), "ভিডিও চালানো যাচ্ছে না: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                }
            }

            @Override
            public void onChannelClick(ChannelInfoItem item) {
            }

            @Override
            public void onPlaylistClick(PlaylistInfoItem item) {
            }
        });

        binding.searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.searchResultsRecyclerView.setAdapter(streamsAdapter);

        binding.searchResultsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;

                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                if (!isLoading && hasMorePages) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount 
                        && firstVisibleItemPosition >= 0) {
                        loadNextPage();
                    }
                }
            }
        });
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        
        binding.btnClearSearch.setOnClickListener(v -> {
            startActivity(new Intent(this, SearchActivity.class));
            finish();
        });
        
        binding.btnMic.setOnClickListener(v -> {
            Toast.makeText(this, "Voice search not implemented", Toast.LENGTH_SHORT).show();
        });
        
        binding.btnCast.setOnClickListener(v -> {
            Toast.makeText(this, "Cast not implemented", Toast.LENGTH_SHORT).show();
        });
        
        binding.btnMoreOptions.setOnClickListener(v -> showFilterDialog());

        binding.searchEditText.setFocusable(false);
        binding.searchEditText.setClickable(true);
        binding.searchEditText.setOnClickListener(v -> {
            startActivity(new Intent(this, SearchActivity.class));
        });
    }

    private void showFilterDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_search_filter);

        RadioGroup filterGroup = dialog.findViewById(R.id.filterGroup);
        setCurrentFilterSelection(dialog);

        filterGroup.setOnCheckedChangeListener((group, checkedId) -> {
            dialog.dismiss();
            handleFilterSelection(checkedId);
        });

        configureDialogWindow(dialog);
        dialog.show();
    }

    private void setCurrentFilterSelection(Dialog dialog) {
        RadioGroup filterGroup = dialog.findViewById(R.id.filterGroup);
        filterGroup.clearCheck();

        int selectedId = switch (currentFilter) {
            case ALL -> R.id.filter_all;
            case VIDEOS -> R.id.filter_videos;
            case CHANNELS -> R.id.filter_channels;
            case PLAYLISTS -> R.id.filter_playlists;
            case SONGS -> R.id.filter_songs;
            case MUSIC_VIDEOS -> R.id.filter_music_videos;
            case ALBUMS -> R.id.filter_albums;
            case MUSIC_PLAYLISTS -> R.id.filter_music_playlists;
        };
        
        RadioButton selectedButton = dialog.findViewById(selectedId);
        if (selectedButton != null) {
            selectedButton.setChecked(true);
        }
    }

    private void handleFilterSelection(int checkedId) {
        SearchFilter newFilter;

        if (checkedId == R.id.filter_all) {
            newFilter = SearchFilter.ALL;
        } else if (checkedId == R.id.filter_videos) {
            newFilter = SearchFilter.VIDEOS;
        } else if (checkedId == R.id.filter_channels) {
            newFilter = SearchFilter.CHANNELS;
        } else if (checkedId == R.id.filter_playlists) {
            newFilter = SearchFilter.PLAYLISTS;
        } else if (checkedId == R.id.filter_songs) {
            newFilter = SearchFilter.SONGS;
        } else if (checkedId == R.id.filter_music_videos) {
            newFilter = SearchFilter.MUSIC_VIDEOS;
        } else if (checkedId == R.id.filter_albums) {
            newFilter = SearchFilter.ALBUMS;
        } else if (checkedId == R.id.filter_music_playlists) {
            newFilter = SearchFilter.MUSIC_PLAYLISTS;
        } else {
            return;
        }

        if (currentFilter != newFilter) {
            currentFilter = newFilter;
            updateSearchHint();
            if (!currentQuery.isEmpty()) {
                performSearch(currentQuery);
            }
        }
    }

    private void updateSearchHint() {
        String hint = switch (currentFilter) {
            case VIDEOS -> "Search Videos";
            case PLAYLISTS -> "Search Playlists";
            case CHANNELS -> "Search Channels";
            case SONGS -> "Search Songs";
            case MUSIC_VIDEOS -> "Search Music Videos";
            case ALBUMS -> "Search Albums";
            case MUSIC_PLAYLISTS -> "Search Music Playlists";
            default -> "Search YouTube";
        };
        binding.searchEditText.setHint(hint);
    }

    private void configureDialogWindow(Dialog dialog) {
        if (dialog.getWindow() != null) {
            Window window = dialog.getWindow();
            window.setBackgroundDrawableResource(android.R.color.transparent);
            int dialogWidth = (int) (240 * getResources().getDisplayMetrics().density);

            android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.width = dialogWidth;
            params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            params.gravity = android.view.Gravity.TOP | android.view.Gravity.END;

            int[] location = new int[2];
            binding.btnMoreOptions.getLocationOnScreen(location);

            params.x = 16;
            params.y = location[1] + binding.btnMoreOptions.getHeight() + 8;

            window.setAttributes(params);
            window.setWindowAnimations(R.style.DialogAnimation);
        }
    }

    private void performSearch(String query) {
        if (query.trim().isEmpty()) return;
        
        currentQuery = query;
        isLoading = true;
        
        hideKeyboard();
        
        clearSearchResults();

        Disposable disposable = ExtractorHelper.searchFor(
                serviceId,
                query,
                currentFilter.getContentFilters(),
                null
        )
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::handleSearchSuccess, this::handleSearchError);

        compositeDisposable.add(disposable);
    }

    private void handleSearchSuccess(SearchInfo searchInfo) {
        isLoading = false;
        lastSearchInfo = searchInfo;

        List<InfoItem> items = searchInfo.getRelatedItems();
        
        if (items == null || items.isEmpty()) {
            Toast.makeText(this, "No results found", Toast.LENGTH_SHORT).show();
        } else {
            streamsAdapter.updateItems(items);
            hasMorePages = searchInfo.hasNextPage();
        }
    }

    private void handleSearchError(Throwable error) {
        isLoading = false;
        Toast.makeText(this, "Search failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void loadNextPage() {
        if (lastSearchInfo == null || !hasMorePages || isLoading) return;
        
        isLoading = true;

        Disposable disposable = ExtractorHelper.getMoreSearchItems(
                serviceId,
                currentQuery,
                currentFilter.getContentFilters(),
                null,
                lastSearchInfo.getNextPage()
        )
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::handleNextPageSuccess, this::handleNextPageError);

        compositeDisposable.add(disposable);
    }

    private void handleNextPageSuccess(InfoItemsPage<InfoItem> itemsPage) {
        isLoading = false;
        
        List<InfoItem> newItems = itemsPage.getItems();
        if (newItems != null && !newItems.isEmpty()) {
            streamsAdapter.addItems(newItems);
        }
        
        hasMorePages = itemsPage.hasNextPage();
    }

    private void handleNextPageError(Throwable error) {
        isLoading = false;
        Toast.makeText(this, "Failed to load more: " + error.getMessage(), Toast.LENGTH_SHORT).show();
    }

    private void clearSearchResults() {
        streamsAdapter.clearItems();
        lastSearchInfo = null;
        hasMorePages = false;
    }

    private void hideKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm = 
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        } catch (Exception e) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeDisposable.clear();
    }
}