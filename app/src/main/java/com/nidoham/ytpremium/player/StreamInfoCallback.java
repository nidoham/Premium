package com.nidoham.ytpremium.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.queue.PlayQueueItem;

/**
 * Callback interface for StreamInfo updates from PlayerService to PlayerActivity.
 * Enables decoupled communication and proper lifecycle management.
 */
public interface StreamInfoCallback {
    
    /**
     * Called when StreamInfo is successfully loaded for the current queue item.
     * 
     * @param queueItem The PlayQueueItem being played
     * @param streamInfo The loaded StreamInfo containing video/audio streams
     */
    void onStreamInfoLoaded(@NonNull PlayQueueItem queueItem, @NonNull StreamInfo streamInfo);
    
    /**
     * Called when stream loading starts.
     * 
     * @param queueItem The PlayQueueItem being loaded
     */
    void onStreamLoadingStarted(@NonNull PlayQueueItem queueItem);
    
    /**
     * Called when stream loading fails.
     * 
     * @param queueItem The PlayQueueItem that failed to load
     * @param error The exception that occurred
     */
    void onStreamLoadingFailed(@NonNull PlayQueueItem queueItem, @NonNull Exception error);
    
    /**
     * Called when queue state changes (next, previous, shuffle, repeat).
     * 
     * @param currentIndex Current index in the queue
     * @param totalItems Total items in the queue
     */
    void onQueueStateChanged(int currentIndex, int totalItems);
}
