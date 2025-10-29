package org.schabi.newpipe.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.icu.text.CompactDecimalFormat;
import android.os.Build;
import android.text.BidiFormatter;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.math.MathUtils;
import androidx.core.os.LocaleListCompat;
import androidx.preference.PreferenceManager;

import org.ocpsoft.prettytime.PrettyTime;
import org.ocpsoft.prettytime.units.Decade;
import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Localization {
    private static final String TAG = Localization.class.getSimpleName();
    public static final String DOT_SEPARATOR = " • ";
    private static PrettyTime prettyTime;
    
    private static final boolean DEBUG = BuildConfig.DEBUG;
    
    private static final int BILLION_THRESHOLD = 1_000_000_000;
    private static final int MILLION_THRESHOLD = 1_000_000;
    private static final int THOUSAND_THRESHOLD = 1_000;
    private static final int SCALE_THRESHOLD = 100;
    private static final int SCALE_FULL = 0;
    private static final int SCALE_DECIMAL = 1;

    private Localization() { }

    @NonNull
    public static String concatenateStrings(final String... strings) {
        return concatenateStrings(DOT_SEPARATOR, Arrays.asList(strings));
    }

    @NonNull
    public static String concatenateStrings(@NonNull final String delimiter, 
                                           @NonNull final List<String> strings) {
        Objects.requireNonNull(delimiter, "delimiter cannot be null");
        Objects.requireNonNull(strings, "strings cannot be null");
        return strings.stream()
                .filter(string -> !TextUtils.isEmpty(string))
                .collect(Collectors.joining(delimiter));
    }

    @NonNull
    public static String localizeUserName(@NonNull final String plainName) {
        Objects.requireNonNull(plainName, "plainName cannot be null");
        return BidiFormatter.getInstance().unicodeWrap(plainName);
    }

    public static org.schabi.newpipe.extractor.localization.Localization getPreferredLocalization(
            @NonNull final Context context) {
        Objects.requireNonNull(context, "context cannot be null");
        // সিস্টেম ডিফল্ট লোকেল ব্যবহার করুন
        return org.schabi.newpipe.extractor.localization.Localization
                .fromLocale(getSystemLocale());
    }

    public static ContentCountry getPreferredContentCountry(@NonNull final Context context) {
        Objects.requireNonNull(context, "context cannot be null");
        // সিস্টেম ডিফল্ট কান্ট্রি ব্যবহার করুন
        return new ContentCountry(Locale.getDefault().getCountry());
    }

    public static Locale getPreferredLocale(@NonNull final Context context) {
        Objects.requireNonNull(context, "context cannot be null");
        // সিস্টেম ডিফল্ট লোকেল রিটার্ন করুন
        return getSystemLocale();
    }

    /**
     * সিস্টেম ডিফল্ট লোকেল রিটার্ন করে (NewPipe কাস্টম সেটিংস উপেক্ষা করে)
     */
    public static Locale getAppLocale() {
        // সবসময় সিস্টেম ডিফল্ট লোকেল ব্যবহার করুন
        return getSystemLocale();
    }

    /**
     * সিস্টেম ডিফল্ট লোকেল পায়
     */
    private static Locale getSystemLocale() {
        return Locale.getDefault();
    }

    public static String localizeNumber(final long number) {
        return localizeNumber((double) number);
    }

    public static String localizeNumber(final double number) {
        // সিস্টেম লোকেল ব্যবহার করুন
        return NumberFormat.getInstance(getSystemLocale()).format(number);
    }

    public static String formatDate(@NonNull final OffsetDateTime offsetDateTime) {
        Objects.requireNonNull(offsetDateTime, "offsetDateTime cannot be null");
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(getSystemLocale())
            .format(offsetDateTime.atZoneSameInstant(ZoneId.systemDefault()));
    }

    @SuppressLint("StringFormatInvalid")
    public static String localizeUploadDate(@NonNull final Context context,
                                            @NonNull final OffsetDateTime offsetDateTime) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(offsetDateTime, "offsetDateTime cannot be null");
        return context.getString(R.string.upload_date_text, formatDate(offsetDateTime));
    }

    public static String localizeViewCount(@NonNull final Context context, final long viewCount) {
        Objects.requireNonNull(context, "context cannot be null");
        return getQuantity(context, R.plurals.views, R.string.no_views, viewCount,
                localizeNumber(viewCount));
    }

    public static String localizeStreamCount(@NonNull final Context context,
                                             final long streamCount) {
        Objects.requireNonNull(context, "context cannot be null");
        return switch ((int) streamCount) {
            case (int) ListExtractor.ITEM_COUNT_UNKNOWN -> "";
            case (int) ListExtractor.ITEM_COUNT_INFINITE -> 
                context.getResources().getString(R.string.infinite_videos);
            case (int) ListExtractor.ITEM_COUNT_MORE_THAN_100 -> 
                context.getResources().getString(R.string.more_than_100_videos);
            default -> getQuantity(context, R.plurals.videos, R.string.no_videos, streamCount,
                    localizeNumber(streamCount));
        };
    }

    public static String localizeStreamCountMini(@NonNull final Context context,
                                                 final long streamCount) {
        Objects.requireNonNull(context, "context cannot be null");
        return switch ((int) streamCount) {
            case (int) ListExtractor.ITEM_COUNT_UNKNOWN -> "";
            case (int) ListExtractor.ITEM_COUNT_INFINITE -> 
                context.getResources().getString(R.string.infinite_videos_mini);
            case (int) ListExtractor.ITEM_COUNT_MORE_THAN_100 -> 
                context.getResources().getString(R.string.more_than_100_videos_mini);
            default -> String.valueOf(streamCount);
        };
    }

    public static String localizeWatchingCount(@NonNull final Context context,
                                               final long watchingCount) {
        Objects.requireNonNull(context, "context cannot be null");
        return getQuantity(context, R.plurals.watching, R.string.no_one_watching, watchingCount,
                localizeNumber(watchingCount));
    }

    public static String shortCount(@NonNull final Context context, final long count) {
        Objects.requireNonNull(context, "context cannot be null");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return CompactDecimalFormat.getInstance(getSystemLocale(),
                    CompactDecimalFormat.CompactStyle.SHORT).format(count);
        }

        final double value = (double) count;
        if (count >= BILLION_THRESHOLD) {
            final double shortenedValue = value / BILLION_THRESHOLD;
            final int scale = shortenedValue >= SCALE_THRESHOLD ? SCALE_FULL : SCALE_DECIMAL;
            return context.getString(R.string.short_billion,
                    localizeNumber(round(shortenedValue, scale)));
        } else if (count >= MILLION_THRESHOLD) {
            final double shortenedValue = value / MILLION_THRESHOLD;
            final int scale = shortenedValue >= SCALE_THRESHOLD ? SCALE_FULL : SCALE_DECIMAL;
            return context.getString(R.string.short_million,
                    localizeNumber(round(shortenedValue, scale)));
        } else if (count >= THOUSAND_THRESHOLD) {
            final double shortenedValue = value / THOUSAND_THRESHOLD;
            final int scale = shortenedValue >= SCALE_THRESHOLD ? SCALE_FULL : SCALE_DECIMAL;
            return context.getString(R.string.short_thousand,
                    localizeNumber(round(shortenedValue, scale)));
        } else {
            return localizeNumber(value);
        }
    }

    public static String listeningCount(@NonNull final Context context, final long listeningCount) {
        Objects.requireNonNull(context, "context cannot be null");
        return getQuantity(context, R.plurals.listening, R.string.no_one_listening, listeningCount,
                shortCount(context, listeningCount));
    }

    public static String shortWatchingCount(@NonNull final Context context,
                                            final long watchingCount) {
        Objects.requireNonNull(context, "context cannot be null");
        return getQuantity(context, R.plurals.watching, R.string.no_one_watching, watchingCount,
                shortCount(context, watchingCount));
    }

    public static String shortViewCount(@NonNull final Context context, final long viewCount) {
        Objects.requireNonNull(context, "context cannot be null");
        return getQuantity(context, R.plurals.views, R.string.no_views, viewCount,
                shortCount(context, viewCount));
    }

    public static String shortSubscriberCount(@NonNull final Context context,
                                              final long subscriberCount) {
        Objects.requireNonNull(context, "context cannot be null");
        return getQuantity(context, R.plurals.subscribers, R.string.no_subscribers, subscriberCount,
                shortCount(context, subscriberCount));
    }

    public static String downloadCount(@NonNull final Context context, final int downloadCount) {
        Objects.requireNonNull(context, "context cannot be null");
        return getQuantity(context, R.plurals.download_finished_notification, 0,
                downloadCount, shortCount(context, downloadCount));
    }

    public static String deletedDownloadCount(@NonNull final Context context,
                                              final int deletedCount) {
        Objects.requireNonNull(context, "context cannot be null");
        return getQuantity(context, R.plurals.deleted_downloads_toast, 0,
                deletedCount, shortCount(context, deletedCount));
    }

    public static String replyCount(@NonNull final Context context, final int replyCount) {
        Objects.requireNonNull(context, "context cannot be null");
        return getQuantity(context, R.plurals.replies, 0, replyCount,
                String.valueOf(replyCount));
    }

    public static String likeCount(@NonNull final Context context, final int likeCount) {
        Objects.requireNonNull(context, "context cannot be null");
        return likeCount < 0 ? "-" : shortCount(context, likeCount);
    }

    public static String getDurationString(final long duration) {
        return DateUtils.formatElapsedTime(Math.max(duration, 0));
    }

    public static String getDurationString(final long duration, final boolean isDurationComplete,
                                           final boolean showDurationPrefix) {
        final String output = getDurationString(duration);
        final String durationPrefix = showDurationPrefix ? "⏱ " : "";
        final String durationPostfix = isDurationComplete ? "" : "+";
        return durationPrefix + output + durationPostfix;
    }

    @NonNull
    public static String localizeDuration(@NonNull final Context context,
                                          final int durationInSecs) {
        Objects.requireNonNull(context, "context cannot be null");
        if (durationInSecs < 0) {
            throw new IllegalArgumentException("duration can not be negative");
        }

        final int days = (int) (durationInSecs / (24 * 60 * 60L));
        final int hours = (int) (durationInSecs % (24 * 60 * 60L) / (60 * 60L));
        final int minutes = (int) (durationInSecs % (24 * 60 * 60L) % (60 * 60L) / 60L);
        final int seconds = (int) (durationInSecs % (24 * 60 * 60L) % (60 * 60L) % 60L);

        final Resources resources = context.getResources();
        
        if (days > 0) {
            return resources.getQuantityString(R.plurals.days, days, days);
        } else if (hours > 0) {
            return resources.getQuantityString(R.plurals.hours, hours, hours);
        } else if (minutes > 0) {
            return resources.getQuantityString(R.plurals.minutes, minutes, minutes);
        } else {
            return resources.getQuantityString(R.plurals.seconds, seconds, seconds);
        }
    }

    public static String audioTrackName(@NonNull final Context context, 
                                       @NonNull final AudioStream track) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(track, "track cannot be null");
        
        final String name;
        if (track.getAudioLocale() != null) {
            name = track.getAudioLocale().getDisplayName();
        } else if (track.getAudioTrackName() != null) {
            name = track.getAudioTrackName();
        } else {
            name = context.getString(R.string.unknown_audio_track);
        }

        if (track.getAudioTrackType() != null) {
            final String trackType = audioTrackType(context, track.getAudioTrackType());
            return context.getString(R.string.audio_track_name, name, trackType);
        }
        return name;
    }

    @NonNull
    private static String audioTrackType(@NonNull final Context context,
                                         @NonNull final AudioTrackType trackType) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(trackType, "trackType cannot be null");
        return switch (trackType) {
            case ORIGINAL -> context.getString(R.string.audio_track_type_original);
            case DUBBED -> context.getString(R.string.audio_track_type_dubbed);
            case DESCRIPTIVE -> context.getString(R.string.audio_track_type_descriptive);
            case SECONDARY -> context.getString(R.string.audio_track_type_secondary);
        };
    }

    public static void initPrettyTime(@NonNull final PrettyTime time) {
        Objects.requireNonNull(time, "time cannot be null");
        prettyTime = time;
        prettyTime.removeUnit(Decade.class);
    }

    public static PrettyTime resolvePrettyTime() {
        // সিস্টেম লোকেল ব্যবহার করুন
        return new PrettyTime(getSystemLocale());
    }

    public static String relativeTime(@NonNull final OffsetDateTime offsetDateTime) {
        Objects.requireNonNull(offsetDateTime, "offsetDateTime cannot be null");
        return prettyTime.formatUnrounded(offsetDateTime);
    }

    @Nullable
    public static String relativeTimeOrTextual(@Nullable final Context context,
                                               @Nullable final DateWrapper parsed,
                                               @Nullable final String textual) {
        if (parsed == null) {
            return textual;
        } else if (DEBUG && context != null && PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.show_original_time_ago_key), false)) {
            return relativeTime(parsed.offsetDateTime()) + " (" + textual + ")";
        } else {
            return relativeTime(parsed.offsetDateTime());
        }
    }

    /**
     * এই মেথডটি এখন আর ব্যবহার হবে না কারণ আমরা সবসময় সিস্টেম ডিফল্ট ব্যবহার করছি
     * @deprecated Use getSystemLocale() instead
     */
    @Deprecated
    private static Locale getLocaleFromPrefs(@NonNull final Context context,
                                             @StringRes final int prefKey) {
        Objects.requireNonNull(context, "context cannot be null");
        // সিস্টেম ডিফল্ট রিটার্ন করুন
        return getSystemLocale();
    }

    private static double round(final double value, final int scale) {
        return new BigDecimal(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static String getQuantity(@NonNull final Context context,
                                      @PluralsRes final int pluralId,
                                      @StringRes final int zeroCaseStringId,
                                      final long count,
                                      @NonNull final String formattedCount) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(formattedCount, "formattedCount cannot be null");
        if (count == 0) {
            return context.getString(zeroCaseStringId);
        }

        final int safeCount = (int) MathUtils.clamp(count, Integer.MIN_VALUE, Integer.MAX_VALUE);
        return context.getResources().getQuantityString(pluralId, safeCount, formattedCount);
    }

    /**
     * এই মেথড আর দরকার নেই কারণ আমরা কাস্টম ল্যাঙ্গুয়েজ সেটিংস ব্যবহার করছি না
     * @deprecated No longer needed as we always use system default
     */
    @Deprecated
    public static void migrateAppLanguageSettingIfNecessary(@NonNull final Context context) {
        // কিছু করার দরকার নেই - সিস্টেম ডিফল্ট ব্যবহার করছি
        Log.i(TAG, "Using system default localization - no migration needed");
    }
}