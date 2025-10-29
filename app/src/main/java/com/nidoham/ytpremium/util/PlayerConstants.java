package com.nidoham.ytpremium.util;

import com.nidoham.ytpremium.BuildConfig;
import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * PlayerConstants - Player Service এবং Activity এর মধ্যে যোগাযোগের জন্য সকল Constants
 * 
 * এই ক্লাসে player সংক্রান্ত সকল constant values রাখা হয়েছে যাতে
 * code maintenance সহজ হয় এবং একই জায়গায় সব পাওয়া যায়
 * 
 * Added @IntDef annotations for type safety and validation
 */
public final class PlayerConstants {
    
    private PlayerConstants() {
        throw new AssertionError("Cannot instantiate PlayerConstants");
    }
    
    private static final String BASE_PACKAGE = BuildConfig.APPLICATION_ID;
    
    // ═══════════════════════════════════════════════════════════════
    // Intent Actions - Service কন্ট্রোল করার জন্য
    // ═══════════════════════════════════════════════════════════════
    
    public static final String ACTION_PLAY = BASE_PACKAGE + ".ACTION_PLAY";
    public static final String ACTION_PAUSE = BASE_PACKAGE + ".ACTION_PAUSE";
    public static final String ACTION_STOP = BASE_PACKAGE + ".ACTION_STOP";
    public static final String ACTION_NEXT = BASE_PACKAGE + ".ACTION_NEXT";
    public static final String ACTION_PREVIOUS = BASE_PACKAGE + ".ACTION_PREVIOUS";
    public static final String ACTION_SEEK = BASE_PACKAGE + ".ACTION_SEEK";
    public static final String ACTION_CHANGE_QUALITY = BASE_PACKAGE + ".ACTION_CHANGE_QUALITY";
    
    // ═══════════════════════════════════════════════════════════════
    // Intent Extra Keys - Data পাঠানোর জন্য
    // ═══════════════════════════════════════════════════════════════
    
    public static final String EXTRA_PLAY_QUEUE = "queue";
    public static final String EXTRA_VIDEO_URL = "extra_video_url";
    public static final String EXTRA_AUDIO_URL = "extra_audio_url";
    public static final String EXTRA_QUALITY_ID = "extra_quality_id";
    public static final String EXTRA_SEEK_POSITION = "extra_seek_position";
    
    // ═══════════════════════════════════════════════════════════════
    // Broadcast Actions এবং Extras
    // ═══════════════════════════════════════════════════════════════
    
    public static final String BROADCAST_PLAYBACK_STATE = BASE_PACKAGE + ".PLAYBACK_STATE";
    public static final String BROADCAST_METADATA_UPDATE = BASE_PACKAGE + ".METADATA_UPDATE";
    
    public static final String EXTRA_STATE = "extra_state";
    public static final String EXTRA_POSITION = "extra_position";
    public static final String EXTRA_DURATION = "extra_duration";
    public static final String EXTRA_CURRENT_ITEM = "extra_current_item";
    public static final String EXTRA_QUEUE_INDEX = "extra_queue_index";
    public static final String EXTRA_QUEUE_SIZE = "extra_queue_size";
    
    // ═══════════════════════════════════════════════════════════════
    // Metadata Extra Keys
    // ═══════════════════════════════════════════════════════════════
    
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_UPLOADER = "extra_uploader";
    public static final String EXTRA_UPLOADER_URL = "extra_uploader_url";
    public static final String EXTRA_DESCRIPTION = "extra_description";
    public static final String EXTRA_VIEW_COUNT = "extra_view_count";
    public static final String EXTRA_LIKE_COUNT = "extra_like_count";
    public static final String EXTRA_THUMBNAIL_URL = "extra_thumbnail_url";
    public static final String EXTRA_UPLOAD_DATE = "extra_upload_date";
    public static final String EXTRA_AVAILABLE_QUALITIES = "extra_available_qualities";
    public static final String EXTRA_CURRENT_QUALITY = "extra_current_quality";
    public static final String EXTRA_LOADING_STATUS = "extra_loading_status";
    public static final String EXTRA_ERROR_MESSAGE = "extra_error_message";
    public static final String EXTRA_IS_LOADING = "extra_is_loading";
    
    // ═══════════════════════════════════════════════════════════════
    // Notification Configuration
    // ═══════════════════════════════════════════════════════════════
    
    public static final int NOTIFICATION_ID = 1001;
    public static final String CHANNEL_ID = "opentube_player_channel";
    public static final String CHANNEL_NAME = "Video Player";
    
    // ═══════════════════════════════════════════════════════════════
    // Player State Constants with @IntDef annotation
    // ═══════════════════════════════════════════════════════════════
    
    @IntDef({STATE_PLAYING, STATE_PAUSED, STATE_BUFFERING, STATE_STOPPED, STATE_ENDED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface PlayerState {}
    
    public static final int STATE_PLAYING = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_BUFFERING = 3;
    public static final int STATE_STOPPED = 4;
    public static final int STATE_ENDED = 5;
    
    /**
     * Added validation method for player states
     * Validates if a given state is a valid player state
     */
    public static boolean isValidState(@PlayerState final int state) {
        return state >= STATE_PLAYING && state <= STATE_ENDED;
    }
    
    /**
     * Added validation method for actions
     * Validates if a given action is a valid player action
     */
    public static boolean isValidAction(final String action) {
        if (action == null) return false;
        return action.equals(ACTION_PLAY) || 
               action.equals(ACTION_PAUSE) || 
               action.equals(ACTION_STOP) || 
               action.equals(ACTION_NEXT) || 
               action.equals(ACTION_PREVIOUS) || 
               action.equals(ACTION_SEEK) || 
               action.equals(ACTION_CHANGE_QUALITY);
    }
}
