package org.schabi.newpipe.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LruCache;

import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.BuildConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Professional LRU cache implementation for storing extracted Info objects.
 * Features:
 * - Thread-safe operations
 * - Automatic expiration handling
 * - Cache statistics tracking
 * - Memory-efficient storage
 * - Stale data cleanup
 * 
 * This cache helps reduce network requests and improve app performance.
 */
public final class InfoCache {
    private static final String TAG = "InfoCache";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final InfoCache INSTANCE = new InfoCache();
    
    // Cache configuration
    private static final int MAX_ITEMS_ON_CACHE = 60;
    private static final int TRIM_CACHE_TO = 30;
    private static final long DEFAULT_EXPIRATION_MS = 4 * 60 * 60 * 1000L; // 4 hours

    private final LruCache<String, CacheData> lruCache;
    
    // Statistics tracking
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong putCount = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);
    
    // Type-based statistics
    private final ConcurrentHashMap<Type, TypeStats> typeStatsMap = new ConcurrentHashMap<>();

    private InfoCache() {
        // Initialize LRU cache with size tracking
        this.lruCache = new LruCache<String, CacheData>(MAX_ITEMS_ON_CACHE) {
            @Override
            protected void entryRemoved(boolean evicted, String key, 
                                       CacheData oldValue, CacheData newValue) {
                if (evicted) {
                    evictionCount.incrementAndGet();
                    if (DEBUG) {
                        Log.d(TAG, "Cache entry evicted: " + key);
                    }
                }
            }
        };
        
        if (DEBUG) {
            Log.d(TAG, "InfoCache initialized - Max size: " + MAX_ITEMS_ON_CACHE);
        }
    }

    /**
     * Identifies the type of {@link Info} to put into the cache.
     */
    public enum Type {
        STREAM(0),
        CHANNEL(1),
        CHANNEL_TAB(2),
        COMMENTS(3),
        PLAYLIST(4),
        KIOSK(5);
        
        private final int ordinal;
        
        Type(int ordinal) {
            this.ordinal = ordinal;
        }
        
        public int getOrdinal() {
            return ordinal;
        }
    }

    /**
     * Get the singleton instance of InfoCache.
     * 
     * @return InfoCache instance
     */
    @NonNull
    public static InfoCache getInstance() {
        return INSTANCE;
    }

    /**
     * Generate a unique cache key for the given parameters.
     * Format: "serviceId:typeOrdinal:url"
     * 
     * @param serviceId the service ID
     * @param url the content URL
     * @param cacheType the cache type
     * @return unique cache key
     */
    @NonNull
    private static String keyOf(final int serviceId,
                                @NonNull final String url,
                                @NonNull final Type cacheType) {
        Objects.requireNonNull(url, "url cannot be null");
        Objects.requireNonNull(cacheType, "cacheType cannot be null");
        return serviceId + ":" + cacheType.getOrdinal() + ":" + url;
    }

    /**
     * Remove all expired entries from the cache.
     * This helps free up memory and maintain cache accuracy.
     * 
     * @return number of entries removed
     */
    private int removeStaleCache() {
        int removedCount = 0;
        
        synchronized (lruCache) {
            final Map<String, CacheData> snapshot = lruCache.snapshot();
            
            for (final Map.Entry<String, CacheData> entry : snapshot.entrySet()) {
                final CacheData data = entry.getValue();
                if (data != null && data.isExpired()) {
                    lruCache.remove(entry.getKey());
                    removedCount++;
                }
            }
        }
        
        if (DEBUG && removedCount > 0) {
            Log.d(TAG, "Removed " + removedCount + " stale cache entries");
        }
        
        return removedCount;
    }

    /**
     * Get info from cache by key, checking expiration.
     * 
     * @param key the cache key
     * @return Info object or null if not found/expired
     */
    @Nullable
    private Info getInfo(@NonNull final String key) {
        Objects.requireNonNull(key, "key cannot be null");
        
        final CacheData data = lruCache.get(key);
        if (data == null) {
            return null;
        }

        if (data.isExpired()) {
            lruCache.remove(key);
            if (DEBUG) {
                Log.d(TAG, "Cache entry expired and removed: " + key);
            }
            return null;
        }

        return data.info;
    }

    /**
     * Get cached Info object by service ID, URL, and type.
     * 
     * @param serviceId the service ID
     * @param url the content URL
     * @param cacheType the cache type
     * @return cached Info or null if not found/expired
     */
    @Nullable
    public Info getFromKey(final int serviceId,
                           @NonNull final String url,
                           @NonNull final Type cacheType) {
        Objects.requireNonNull(url, "url cannot be null");
        Objects.requireNonNull(cacheType, "cacheType cannot be null");
        
        if (DEBUG) {
            Log.d(TAG, "getFromKey() called with: serviceId = [" + serviceId + 
                    "], url = [" + url + "], type = [" + cacheType + "]");
        }

        final Info info;
        synchronized (lruCache) {
            info = getInfo(keyOf(serviceId, url, cacheType));
        }
        
        // Update statistics
        if (info != null) {
            hitCount.incrementAndGet();
            updateTypeStats(cacheType, true);
            if (DEBUG) {
                Log.d(TAG, "Cache HIT for: " + url);
            }
        } else {
            missCount.incrementAndGet();
            updateTypeStats(cacheType, false);
            if (DEBUG) {
                Log.d(TAG, "Cache MISS for: " + url);
            }
        }
        
        return info;
    }

    /**
     * Put Info object into cache with automatic expiration.
     * 
     * @param serviceId the service ID
     * @param url the content URL
     * @param info the Info object to cache
     * @param cacheType the cache type
     */
    public void putInfo(final int serviceId,
                        @NonNull final String url,
                        @NonNull final Info info,
                        @NonNull final Type cacheType) {
        Objects.requireNonNull(url, "url cannot be null");
        Objects.requireNonNull(info, "info cannot be null");
        Objects.requireNonNull(cacheType, "cacheType cannot be null");
        
        if (DEBUG) {
            Log.d(TAG, "putInfo() called with: serviceId = [" + serviceId + 
                    "], url = [" + url + "], type = [" + cacheType + "]");
        }

        try {
            final long expirationMillis = ServiceHelper.getCacheExpirationMillis(info.getServiceId());
            
            synchronized (lruCache) {
                final CacheData data = new CacheData(info, expirationMillis);
                lruCache.put(keyOf(serviceId, url, cacheType), data);
            }
            
            putCount.incrementAndGet();
            updateTypePutStats(cacheType);
            
        } catch (final Exception e) {
            Log.e(TAG, "Failed to put info in cache", e);
        }
    }

    /**
     * Put Info object with custom expiration time.
     * 
     * @param serviceId the service ID
     * @param url the content URL
     * @param info the Info object to cache
     * @param cacheType the cache type
     * @param expirationMillis custom expiration time in milliseconds
     */
    public void putInfo(final int serviceId,
                        @NonNull final String url,
                        @NonNull final Info info,
                        @NonNull final Type cacheType,
                        final long expirationMillis) {
        Objects.requireNonNull(url, "url cannot be null");
        Objects.requireNonNull(info, "info cannot be null");
        Objects.requireNonNull(cacheType, "cacheType cannot be null");
        
        if (DEBUG) {
            Log.d(TAG, "putInfo() with custom expiration: " + expirationMillis + "ms");
        }

        try {
            synchronized (lruCache) {
                final CacheData data = new CacheData(info, expirationMillis);
                lruCache.put(keyOf(serviceId, url, cacheType), data);
            }
            
            putCount.incrementAndGet();
            updateTypePutStats(cacheType);
            
        } catch (final Exception e) {
            Log.e(TAG, "Failed to put info in cache with custom expiration", e);
        }
    }

    /**
     * Remove specific Info from cache.
     * 
     * @param serviceId the service ID
     * @param url the content URL
     * @param cacheType the cache type
     */
    public void removeInfo(final int serviceId,
                           @NonNull final String url,
                           @NonNull final Type cacheType) {
        Objects.requireNonNull(url, "url cannot be null");
        Objects.requireNonNull(cacheType, "cacheType cannot be null");
        
        if (DEBUG) {
            Log.d(TAG, "removeInfo() called with: serviceId = [" + serviceId + 
                    "], url = [" + url + "], type = [" + cacheType + "]");
        }
        
        synchronized (lruCache) {
            lruCache.remove(keyOf(serviceId, url, cacheType));
        }
    }

    /**
     * Remove all cached entries of a specific type.
     * 
     * @param cacheType the cache type to remove
     * @return number of entries removed
     */
    public int removeByType(@NonNull final Type cacheType) {
        Objects.requireNonNull(cacheType, "cacheType cannot be null");
        
        int removedCount = 0;
        final String typePrefix = ":" + cacheType.getOrdinal() + ":";
        
        synchronized (lruCache) {
            final Map<String, CacheData> snapshot = lruCache.snapshot();
            
            for (final String key : snapshot.keySet()) {
                if (key.contains(typePrefix)) {
                    lruCache.remove(key);
                    removedCount++;
                }
            }
        }
        
        if (DEBUG) {
            Log.d(TAG, "Removed " + removedCount + " entries of type: " + cacheType);
        }
        
        return removedCount;
    }

    /**
     * Clear all cached entries.
     */
    public void clearCache() {
        if (DEBUG) {
            Log.d(TAG, "clearCache() called - clearing " + getSize() + " entries");
        }
        
        synchronized (lruCache) {
            lruCache.evictAll();
        }
        
        // Reset type statistics
        typeStatsMap.clear();
    }

    /**
     * Trim cache to target size and remove stale entries.
     * This helps manage memory usage.
     */
    public void trimCache() {
        if (DEBUG) {
            Log.d(TAG, "trimCache() called - current size: " + getSize());
        }
        
        synchronized (lruCache) {
            final int staleRemoved = removeStaleCache();
            lruCache.trimToSize(TRIM_CACHE_TO);
            
            if (DEBUG) {
                Log.d(TAG, "Trim complete - removed " + staleRemoved + 
                        " stale entries, new size: " + getSize());
            }
        }
    }

    /**
     * Get current cache size.
     * 
     * @return number of cached entries
     */
    public int getSize() {
        synchronized (lruCache) {
            return lruCache.size();
        }
    }

    /**
     * Check if cache contains a specific entry.
     * 
     * @param serviceId the service ID
     * @param url the content URL
     * @param cacheType the cache type
     * @return true if entry exists and is not expired
     */
    public boolean contains(final int serviceId,
                           @NonNull final String url,
                           @NonNull final Type cacheType) {
        Objects.requireNonNull(url, "url cannot be null");
        Objects.requireNonNull(cacheType, "cacheType cannot be null");
        
        return getFromKey(serviceId, url, cacheType) != null;
    }

    /**
     * Get cache statistics.
     * 
     * @return CacheStats object with current statistics
     */
    @NonNull
    public CacheStats getStats() {
        return new CacheStats(
            getSize(),
            hitCount.get(),
            missCount.get(),
            putCount.get(),
            evictionCount.get()
        );
    }

    /**
     * Get statistics for a specific cache type.
     * 
     * @param type the cache type
     * @return TypeStats object or null if no stats available
     */
    @Nullable
    public TypeStats getTypeStats(@NonNull final Type type) {
        Objects.requireNonNull(type, "type cannot be null");
        return typeStatsMap.get(type);
    }

    /**
     * Update statistics for cache type on hit/miss.
     */
    private void updateTypeStats(@NonNull final Type type, final boolean hit) {
        typeStatsMap.compute(type, (k, stats) -> {
            if (stats == null) {
                stats = new TypeStats();
            }
            if (hit) {
                stats.hits++;
            } else {
                stats.misses++;
            }
            return stats;
        });
    }

    /**
     * Update statistics for cache type on put.
     */
    private void updateTypePutStats(@NonNull final Type type) {
        typeStatsMap.compute(type, (k, stats) -> {
            if (stats == null) {
                stats = new TypeStats();
            }
            stats.puts++;
            return stats;
        });
    }

    /**
     * Log current cache statistics (debug only).
     */
    public void logStats() {
        if (!DEBUG) {
            return;
        }
        
        final CacheStats stats = getStats();
        final long totalRequests = stats.hits + stats.misses;
        final double hitRate = totalRequests > 0 ? 
                (stats.hits * 100.0 / totalRequests) : 0.0;
        
        Log.d(TAG, "=== Cache Statistics ===");
        Log.d(TAG, "Size: " + stats.size + "/" + MAX_ITEMS_ON_CACHE);
        Log.d(TAG, "Hits: " + stats.hits);
        Log.d(TAG, "Misses: " + stats.misses);
        Log.d(TAG, "Hit Rate: " + String.format("%.2f%%", hitRate));
        Log.d(TAG, "Puts: " + stats.puts);
        Log.d(TAG, "Evictions: " + stats.evictions);
        
        // Log type-specific stats
        for (final Type type : Type.values()) {
            final TypeStats typeStats = typeStatsMap.get(type);
            if (typeStats != null && typeStats.getTotal() > 0) {
                Log.d(TAG, type + " - Hits: " + typeStats.hits + 
                        ", Misses: " + typeStats.misses + 
                        ", Puts: " + typeStats.puts);
            }
        }
    }

    /**
     * Container for cached data with expiration tracking.
     */
    private static final class CacheData {
        private final long expireTimestamp;
        private final Info info;
        private final long createdTimestamp;

        private CacheData(@NonNull final Info info, final long timeoutMillis) {
            Objects.requireNonNull(info, "info cannot be null");
            this.createdTimestamp = System.currentTimeMillis();
            this.expireTimestamp = createdTimestamp + timeoutMillis;
            this.info = info;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expireTimestamp;
        }
        
        private long getAge() {
            return System.currentTimeMillis() - createdTimestamp;
        }
    }

    /**
     * Cache statistics container.
     */
    public static final class CacheStats {
        public final int size;
        public final long hits;
        public final long misses;
        public final long puts;
        public final long evictions;

        private CacheStats(int size, long hits, long misses, long puts, long evictions) {
            this.size = size;
            this.hits = hits;
            this.misses = misses;
            this.puts = puts;
            this.evictions = evictions;
        }

        public double getHitRate() {
            final long total = hits + misses;
            return total > 0 ? (hits * 100.0 / total) : 0.0;
        }

        @NonNull
        @Override
        public String toString() {
            return "CacheStats{" +
                    "size=" + size +
                    ", hits=" + hits +
                    ", misses=" + misses +
                    ", hitRate=" + String.format("%.2f%%", getHitRate()) +
                    ", puts=" + puts +
                    ", evictions=" + evictions +
                    '}';
        }
    }

    /**
     * Type-specific statistics container.
     */
    public static final class TypeStats {
        public long hits = 0;
        public long misses = 0;
        public long puts = 0;

        public long getTotal() {
            return hits + misses;
        }

        public double getHitRate() {
            final long total = getTotal();
            return total > 0 ? (hits * 100.0 / total) : 0.0;
        }

        @NonNull
        @Override
        public String toString() {
            return "TypeStats{" +
                    "hits=" + hits +
                    ", misses=" + misses +
                    ", puts=" + puts +
                    ", hitRate=" + String.format("%.2f%%", getHitRate()) +
                    '}';
        }
    }
}