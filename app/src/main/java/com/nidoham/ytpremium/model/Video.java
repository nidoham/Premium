package com.nidoham.ytpremium.model;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nidoham.ytpremium.extractor.image.ThumbnailExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

/**
 * Video model for storing video information
 * Used in Shorts and other video players
 * 
 * @version 3.0 (Added NewPipe integration with from() factory methods)
 */
public class Video {
    private static final String TAG = "Video";
    private String videoUrl;
    private String title;
    private String uploader;
    private String description;
    private String thumbnailUrl;
    private long duration; // in seconds
    private String videoId;

    // Constructor with essential fields (matches current usage in ShortsFragment)
    public Video(String videoUrl, String title, String uploader) {
        this.videoUrl = videoUrl;
        this.title = title;
        this.uploader = uploader;
        this.description = "";
    }

    // Full constructor
    public Video(String videoUrl, String title, String uploader, String description) {
        this.videoUrl = videoUrl;
        this.title = title;
        this.uploader = uploader;
        this.description = description;
    }

    // Extended constructor with all fields
    public Video(String videoUrl, String title, String uploader, String description, 
                 String thumbnailUrl, long duration, String videoId) {
        this.videoUrl = videoUrl;
        this.title = title;
        this.uploader = uploader;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.duration = duration;
        this.videoId = videoId;
    }

    // Getters and Setters
    public String getVideoUrl() {
        return videoUrl;
    }

    // ============= NEWPIPE INTEGRATION FACTORY METHODS =============
    
    /**
     * Create Video from StreamInfo with automatic best quality selection
     * Prefers HLS > Combined Audio+Video > Highest quality available
     * 
     * @param streamInfo NewPipe StreamInfo object
     * @return Video object with best available stream, or null if no valid stream found
     */
    @Nullable
    public static Video from(@NonNull StreamInfo streamInfo) {
        return from(streamInfo, 720); // Default max 720p
    }

    /**
     * Create Video from StreamInfo with quality preference
     * 
     * @param streamInfo NewPipe StreamInfo object
     * @param maxResolution Maximum resolution to select (e.g., 720, 1080)
     * @return Video object with best available stream, or null if no valid stream found
     */
    @Nullable
    public static Video from(@NonNull StreamInfo streamInfo, int maxResolution) {
        String videoUrl = extractBestVideoUrl(streamInfo, maxResolution);
        
        if (videoUrl == null || videoUrl.isEmpty()) {
            Log.w(TAG, "No valid video URL found for: " + streamInfo.getName());
            return null;
        }

        String videoId = extractVideoId(streamInfo.getUrl());
        ThumbnailExtractor thumb = new ThumbnailExtractor(streamInfo.getThumbnails());
        String thumbnailUrl = thumb.getThumbnail();
        
        Video video = new Video(
            videoUrl,
            streamInfo.getName(),
            streamInfo.getUploaderName(),
            streamInfo.getDescription() != null ? streamInfo.getDescription().getContent() : ""
        );
        
        video.setThumbnailUrl(thumbnailUrl);
        video.setDuration(streamInfo.getDuration());
        video.setVideoId(videoId);
        
        return video;
    }

    /**
     * Create Video from StreamInfoItem (lightweight, for search results)
     * Note: This only has basic info, use from(StreamInfo) for full details
     * 
     * @param streamInfoItem NewPipe StreamInfoItem object
     * @return Video object with basic info (URL will need to be fetched separately)
     */
    @NonNull
    public static Video from(@NonNull StreamInfoItem streamInfoItem) {
        String videoId = extractVideoId(streamInfoItem.getUrl());
        
        Video video = new Video(
            streamInfoItem.getUrl(), // This is the YouTube page URL, not stream URL
            streamInfoItem.getName(),
            streamInfoItem.getUploaderName()
        );
        ThumbnailExtractor thumb = new ThumbnailExtractor(streamInfoItem.getThumbnails());
        String thumbnailUrl = thumb.getThumbnail();
        
        video.setThumbnailUrl(thumbnailUrl);
        video.setDuration(streamInfoItem.getDuration());
        video.setVideoId(videoId);
        
        return video;
    }

    /**
     * Create Video with custom stream URL from StreamInfo
     * Useful when you want to manually select a specific stream
     * 
     * @param streamInfo NewPipe StreamInfo object
     * @param customStreamUrl Custom stream URL to use
     * @return Video object with custom stream URL
     */
    @NonNull
    public static Video fromWithCustomUrl(@NonNull StreamInfo streamInfo, @NonNull String customStreamUrl) {
        String videoId = extractVideoId(streamInfo.getUrl());
        
        Video video = new Video(
            customStreamUrl,
            streamInfo.getName(),
            streamInfo.getUploaderName(),
            streamInfo.getDescription() != null ? streamInfo.getDescription().getContent() : ""
        );
        
        ThumbnailExtractor thumb = new ThumbnailExtractor(streamInfo.getThumbnails());
        String thumbnailUrl = thumb.getThumbnail();
        
        video.setThumbnailUrl(thumbnailUrl);
        video.setDuration(streamInfo.getDuration());
        video.setVideoId(videoId);
        
        return video;
    }

    // ============= PRIVATE HELPER METHODS FOR NEWPIPE =============
    
    /**
     * Extract best video URL from StreamInfo based on quality preference
     */
    private static String extractBestVideoUrl(StreamInfo streamInfo, int maxResolution) {
        // 1. Try HLS first (best option - adaptive streaming with audio)
        String hlsUrl = streamInfo.getHlsUrl();
        if (hlsUrl != null && !hlsUrl.isEmpty()) {
            Log.d(TAG, "Using HLS stream");
            return hlsUrl;
        }

        // 2. Try combined video+audio streams
        List<VideoStream> videoStreams = streamInfo.getVideoStreams();
        if (videoStreams != null && !videoStreams.isEmpty()) {
            VideoStream bestCombined = selectBestCombinedStream(videoStreams, maxResolution);
            if (bestCombined != null) {
                Log.d(TAG, "Using combined stream: " + bestCombined.getResolution());
                return bestCombined.getUrl();
            }
        }

        // 3. Fallback: any available combined stream (ignore quality)
        if (videoStreams != null) {
            for (VideoStream stream : videoStreams) {
                if (!stream.isVideoOnly()) {
                    Log.d(TAG, "Using fallback combined stream: " + stream.getResolution());
                    return stream.getUrl();
                }
            }
        }

        Log.w(TAG, "No usable video+audio stream found");
        return null;
    }

    /**
     * Select best combined (audio+video) stream within quality limit
     */
    private static VideoStream selectBestCombinedStream(List<VideoStream> streams, int maxResolution) {
        VideoStream best = null;
        int bestHeight = 0;

        for (VideoStream stream : streams) {
            // Skip video-only streams
            if (stream.isVideoOnly()) {
                continue;
            }

            int height = parseResolution(stream.getResolution());
            
            // Select best quality within limit
            if (height <= maxResolution && height > bestHeight) {
                best = stream;
                bestHeight = height;
            }
        }

        return best;
    }

    /**
     * Parse resolution string to integer (e.g., "720p" -> 720)
     */
    private static int parseResolution(String resolution) {
        if (resolution == null) return 0;
        
        try {
            String numbers = resolution.replaceAll("[^0-9]", "");
            if (!numbers.isEmpty()) {
                return Integer.parseInt(numbers);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse resolution: " + resolution, e);
        }
        
        return 0;
    }

    /**
     * Extract video ID from YouTube URL
     */
    private static String extractVideoId(String url) {
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

    // ============= ORIGINAL GETTERS AND SETTERS =============
    
    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getTitle() {
        return title != null ? title : "";
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUploader() {
        return uploader != null ? uploader : "";
    }

    public void setUploader(String uploader) {
        this.uploader = uploader;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    // Utility methods
    public boolean isHlsStream() {
        if (videoUrl == null) return false;
        String lowerUrl = videoUrl.toLowerCase();
        return lowerUrl.contains(".m3u8") || 
               lowerUrl.contains("/hls/") ||
               lowerUrl.contains("manifest");
    }

    public String getFormattedDuration() {
        if (duration <= 0) return "00:00";
        
        long minutes = duration / 60;
        long seconds = duration % 60;
        
        if (minutes >= 60) {
            long hours = minutes / 60;
            minutes = minutes % 60;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        
        return String.format("%d:%02d", minutes, seconds);
    }

    public boolean isValid() {
        return videoUrl != null && !videoUrl.isEmpty() && 
               title != null && !title.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Video video = (Video) o;
        return videoUrl != null && videoUrl.equals(video.videoUrl);
    }

    @Override
    public int hashCode() {
        return videoUrl != null ? videoUrl.hashCode() : 0;
    }
}