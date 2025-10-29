package com.nidoham.ytpremium.util;

import androidx.annotation.NonNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * SearchFilter - Search filter options for YouTube content
 * 
 * This enum provides filter options for different content types:
 * - Content filters: ALL, VIDEOS, CHANNELS, PLAYLISTS
 * - Music filters: SONGS, MUSIC_VIDEOS, ALBUMS, MUSIC_PLAYLISTS
 */
public enum SearchFilter {
    // Content Filters
    ALL("all", Collections.emptyList(), FilterCategory.CONTENT),
    VIDEOS("videos", Arrays.asList("videos"), FilterCategory.CONTENT),
    CHANNELS("channels", Arrays.asList("channels"), FilterCategory.CONTENT),
    PLAYLISTS("playlists", Arrays.asList("playlists"), FilterCategory.CONTENT),
    
    // Music Filters
    SONGS("songs", Arrays.asList("music_songs"), FilterCategory.MUSIC),
    MUSIC_VIDEOS("music_videos", Arrays.asList("music_videos"), FilterCategory.MUSIC),
    ALBUMS("albums", Arrays.asList("music_albums"), FilterCategory.MUSIC),
    MUSIC_PLAYLISTS("music_playlists", Arrays.asList("music_playlists"), FilterCategory.MUSIC);

    private final String name;
    private final List<String> contentFilters;
    private final FilterCategory category;

    SearchFilter(String name, List<String> contentFilters, FilterCategory category) {
        this.name = name;
        this.contentFilters = contentFilters;
        this.category = category;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public List<String> getContentFilters() {
        return contentFilters;
    }

    @NonNull
    public FilterCategory getCategory() {
        return category;
    }

    public boolean isContentFilter() {
        return category == FilterCategory.CONTENT;
    }

    public boolean isMusicFilter() {
        return category == FilterCategory.MUSIC;
    }

    public enum FilterCategory {
        CONTENT,
        MUSIC
    }
}