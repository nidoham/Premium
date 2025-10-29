package com.nidoham.ytpremium.extractor.image;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.schabi.newpipe.extractor.Image;

/**
 * Utility class for extracting optimal thumbnails from a collection of images.
 * Provides intelligent selection based on resolution, quality, and aspect ratio.
 */
public final class ThumbnailExtractor {
    
    private static final double MIN_ASPECT_RATIO = 0.5;
    private static final double MAX_ASPECT_RATIO = 2.0;
    
    private static final int HIGH_LEVEL_WEIGHT = 10_000;
    private static final int MEDIUM_LEVEL_WEIGHT = 5_000;
    private static final int LOW_LEVEL_WEIGHT = 1_000;
    private static final int ASPECT_RATIO_BONUS = 1_000;
    
    private final List<Image> thumbnails;
    private final Comparator<Image> qualityComparator;
    
    public ThumbnailExtractor(List<Image> thumbnails) {
        this.thumbnails = thumbnails != null ? List.copyOf(thumbnails) : List.of();
        this.qualityComparator = Comparator
                .comparingInt(this::calculateScore)
                .thenComparingInt(img -> getResolutionLevelPriority(img.getEstimatedResolutionLevel()))
                .reversed();
    }

    /**
     * Gets the highest quality thumbnail URL based on resolution and quality estimation.
     * 
     * @return URL of the best thumbnail, or empty string if no thumbnails available
     */
    public String getThumbnail() {
        return thumbnails.stream()
                .max(qualityComparator)
                .map(Image::getUrl)
                .orElse("");
    }
    
    /**
     * Gets thumbnail by specific resolution level preference.
     * Falls back to best available thumbnail if preferred level is not found.
     * 
     * @param preferredLevel The preferred resolution level
     * @return URL of the best matching thumbnail
     * @throws IllegalArgumentException if preferredLevel is null
     */
    public String getThumbnailByLevel(Image.ResolutionLevel preferredLevel) {
        if (preferredLevel == null) {
            throw new IllegalArgumentException("Preferred level cannot be null");
        }
        
        return thumbnails.stream()
                .filter(image -> image.getEstimatedResolutionLevel() == preferredLevel)
                .max(Comparator.comparingInt(this::calculateScore))
                .map(Image::getUrl)
                .orElseGet(this::getThumbnail);
    }
    
    /**
     * Gets thumbnail with progressive fallback strategy.
     * Attempts HIGH → MEDIUM → any available thumbnail.
     * 
     * @return URL of the best available thumbnail, or empty string if none available
     */
    public String getThumbnailWithFallback() {
        return Optional.ofNullable(tryGetByLevel(Image.ResolutionLevel.HIGH))
                .or(() -> Optional.ofNullable(tryGetByLevel(Image.ResolutionLevel.MEDIUM)))
                .orElseGet(this::getThumbnail);
    }
    
    /**
     * Gets the Image object (not just URL) of the best quality thumbnail.
     * Useful when you need access to dimensions or other metadata.
     * 
     * @return Optional containing the best thumbnail, or empty if none available
     */
    public Optional<Image> getBestThumbnailImage() {
        return thumbnails.stream().max(qualityComparator);
    }
    
    /**
     * Checks if thumbnails are available.
     * 
     * @return true if at least one thumbnail is available
     */
    public boolean hasThumbnails() {
        return !thumbnails.isEmpty();
    }
    
    /**
     * Gets the total count of available thumbnails.
     * 
     * @return number of thumbnails
     */
    public int getThumbnailCount() {
        return thumbnails.size();
    }
    
    /**
     * Gets all available thumbnails as an immutable list.
     * 
     * @return immutable list of thumbnails
     */
    public List<Image> getAllThumbnails() {
        return thumbnails;
    }
    
    // ========== Private Helper Methods ==========
    
    /**
     * Attempts to get thumbnail by level, returns null if not found.
     */
    private String tryGetByLevel(Image.ResolutionLevel level) {
        String result = getThumbnailByLevel(level);
        return result.isEmpty() ? null : result;
    }
    
    /**
     * Calculates a weighted quality score for image selection.
     * Combines area, resolution level, and aspect ratio considerations.
     */
    private int calculateScore(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        if (width <= 0 || height <= 0) {
            return 0;
        }
        
        int areaScore = width * height;
        int levelWeight = getResolutionLevelWeight(image.getEstimatedResolutionLevel());
        int aspectBonus = hasReasonableAspectRatio(width, height) ? ASPECT_RATIO_BONUS : 0;
        
        return areaScore + levelWeight + aspectBonus;
    }
    
    /**
     * Checks if aspect ratio is within reasonable bounds.
     */
    private boolean hasReasonableAspectRatio(int width, int height) {
        double aspectRatio = (double) width / height;
        return aspectRatio >= MIN_ASPECT_RATIO && aspectRatio <= MAX_ASPECT_RATIO;
    }
    
    /**
     * Gets weight value for resolution level.
     */
    private int getResolutionLevelWeight(Image.ResolutionLevel level) {
        return switch (level) {
            case HIGH -> HIGH_LEVEL_WEIGHT;
            case MEDIUM -> MEDIUM_LEVEL_WEIGHT;
            case LOW -> LOW_LEVEL_WEIGHT;
            default -> 0;
        };
    }
    
    /**
     * Gets priority value for resolution level ordering.
     */
    private int getResolutionLevelPriority(Image.ResolutionLevel level) {
        return switch (level) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
            default -> 0;
        };
    }
}