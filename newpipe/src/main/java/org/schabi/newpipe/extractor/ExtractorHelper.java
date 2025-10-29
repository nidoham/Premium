package org.schabi.newpipe.extractor;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;

import java.util.stream.Collectors;
import org.schabi.newpipe.R;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.util.Constant;
import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.Localization;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

/**
 * ExtractorHelper - নিউপাইপ এক্সট্র্যাক্টর অপারেশনের জন্য কেন্দ্রীয় সহায়ক ক্লাস।
 * 
 * এই ক্লাসটি সার্চ, চ্যানেল, প্লেলিস্ট, স্ট্রিম, মন্তব্য এবং কিয়স্ক সম্পর্কিত সমস্ত 
 * নেটওয়ার্ক অপারেশন পরিচালনা করে। এটি RxJava 3 ভিত্তিক অ্যাসিঙ্ক প্রোগ্রামিং ব্যবহার করে 
 * এবং স্মার্ট ক্যাশিং মেকানিজম প্রদান করে।
 * 
 * মূল বৈশিষ্ট্য:
 * - RxJava 3 ভিত্তিক অ্যাসিঙ্ক অপারেশন (Single, Maybe ব্যবহার করে)
 * - স্বয়ংক্রিয় ক্যাশিং এবং ক্যাশ ম্যানেজমেন্ট
 * - সম্পূর্ণ নাল সেফটি (@NonNull, @Nullable অ্যানোটেশন)
 * - HTML ফরম্যাটিং এবং মেটা তথ্য প্রদর্শন
 * - ব্যাপক এরর হ্যান্ডলিং এবং লগিং
 * 
 * @author NI Doha Mondol
 * @version 2.0 (Optimized with Bengali Documentation)
 */
public final class ExtractorHelper {
    
    private static final String TAG = ExtractorHelper.class.getSimpleName();
    private static final InfoCache CACHE = InfoCache.getInstance();
    
    private static final String HTML_BOLD_OPEN = "<b>";
    private static final String HTML_BOLD_CLOSE = "</b>";
    private static final String HTML_LINK_FORMAT = "<a href=\"%s\">%s</a>";
    private static final String HTML_LINE_BREAK = "<br/><br/>";
    private static final String HTML_PERIOD = ".";
    private static final int STRING_BUILDER_CAPACITY = 512;

    private ExtractorHelper() {
    }

    // ============================================================================
    // সার্চ অপারেশন (Search Operations)
    // ============================================================================

    /**
     * নির্দিষ্ট সেবায় কন্টেন্ট অনুসন্ধান করে।
     * 
     * ব্যবহার: ব্যবহারকারী যখন কোনো ভিডিও বা চ্যানেল খোঁজে তখন এই মেথড কল হয়।
     * 
     * @param serviceId সেবার আইডি (YouTube, Vimeo ইত্যাদি)
     * @param searchString অনুসন্ধান কোয়েরি (যা ব্যবহারকারী খোঁজে)
     * @param contentFilter কন্টেন্ট ফিল্টার তালিকা (ভিডিও, চ্যানেল, প্লেলিস্ট ইত্যাদি)
     * @param sortFilter সর্টিং পছন্দ (প্রাসঙ্গিকতা, তারিখ, ভিউ ইত্যাদি)
     * @return SearchInfo সহ Single (অ্যাসিঙ্ক ফলাফল)
     * @throws IllegalArgumentException যদি serviceId অবৈধ হয়
     */
    public static Single<SearchInfo> searchFor(final int serviceId, 
                                               @NonNull final String searchString,
                                               @Nullable final List<String> contentFilter,
                                               @Nullable final String sortFilter) {
        checkServiceId(serviceId);
        
        return Single.fromCallable(() -> {
            final var service = NewPipe.getService(serviceId);
            final var queryHandler = service.getSearchQHFactory()
                    .fromQuery(searchString, contentFilter, sortFilter);
            return SearchInfo.getInfo(service, queryHandler);
        })
        .onErrorResumeNext(error -> {
            Log.e(TAG, "সার্চ অপারেশন ব্যর্থ: " + searchString, error);
            return Single.error(error);
        });
    }

    /**
     * সার্চ ফলাফলের পরবর্তী পৃষ্ঠা পান (পেজিনেশন)।
     * 
     * ব্যবহার: ব্যবহারকারী যখন "আরও দেখুন" বাটন ক্লিক করে তখন এই মেথড কল হয়।
     * 
     * @param serviceId সেবার আইডি
     * @param searchString মূল অনুসন্ধান কোয়েরি
     * @param contentFilter কন্টেন্ট ফিল্টার
     * @param sortFilter সর্টিং পছন্দ
     * @param page পরবর্তী পৃষ্ঠার তথ্য (Page অবজেক্ট)
     * @return পরবর্তী পৃষ্ঠার আইটেম সহ Single
     */
    public static Single<InfoItemsPage<InfoItem>> getMoreSearchItems(
            final int serviceId,
            @NonNull final String searchString,
            @Nullable final List<String> contentFilter,
            @Nullable final String sortFilter,
            @NonNull final Page page) {
        checkServiceId(serviceId);
        
        return Single.fromCallable(() -> {
            final var service = NewPipe.getService(serviceId);
            final var queryHandler = service.getSearchQHFactory()
                    .fromQuery(searchString, contentFilter, sortFilter);
            return SearchInfo.getMoreItems(service, queryHandler, page);
        })
        .onErrorResumeNext(error -> {
            Log.e(TAG, "পরবর্তী সার্চ আইটেম পুনরুদ্ধার ব্যর্থ", error);
            return Single.error(error);
        });
    }

    /**
     * অনুসন্ধান পরামর্শ পান (অটোকমপ্লিট)।
     * 
     * ব্যবহার: ব্যবহারকারী সার্চ বক্সে টাইপ করার সময় সাজেশন দেখায়।
     * 
     * @param serviceId সেবার আইডি
     * @param query অনুসন্ধান কোয়েরি (আংশিক টেক্সট)
     * @return পরামর্শ তালিকা সহ Single
     */
    public static Single<List<String>> suggestionsFor(final int serviceId, 
                                                      @NonNull final String query) {
        checkServiceId(serviceId);
        
        return Single.fromCallable(() -> {
            final var extractor = NewPipe.getService(serviceId)
                    .getSuggestionExtractor();
            return extractor != null
                    ? extractor.suggestionList(query)
                    : Collections.<String>emptyList();
        })
        .onErrorReturnItem(Collections.<String>emptyList());
    }

    // ============================================================================
    // স্ট্রিম অপারেশন (Stream Operations)
    // ============================================================================

    /**
     * স্ট্রিম তথ্য পান (ভিডিও বিস্তারিত)।
     * 
     * ব্যবহার: ব্যবহারকারী যখন কোনো ভিডিও খোলে তখন এই মেথড ভিডিওর সমস্ত তথ্য লোড করে।
     * 
     * @param serviceId সেবার আইডি
     * @param url স্ট্রিম URL (ভিডিও লিংক)
     * @param forceLoad true হলে ক্যাশ বাইপাস করে নেটওয়ার্ক থেকে লোড করে
     * @return StreamInfo সহ Single (ভিডিও শিরোনাম, বর্ণনা, ডিউরেশন ইত্যাদি)
     */
    public static Single<StreamInfo> getStreamInfo(final int serviceId, 
                                                   @NonNull final String url,
                                                   final boolean forceLoad) {
        checkServiceId(serviceId);
        
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.STREAM,
                Single.fromCallable(() -> 
                        StreamInfo.getInfo(NewPipe.getService(serviceId), url))
                .onErrorResumeNext(error -> {
                    Log.e(TAG, "স্ট্রিম তথ্য পুনরুদ্ধার ব্যর্থ: " + url, error);
                    return Single.error(error);
                }));
    }

    // ============================================================================
    // চ্যানেল অপারেশন (Channel Operations)
    // ============================================================================

    /**
     * চ্যানেল তথ্য পান (চ্যানেল বিস্তারিত)।
     * 
     * ব্যবহার: ব্যবহারকারী যখন কোনো চ্যানেল খোলে তখন চ্যানেলের সমস্ত তথ্য লোড করে।
     * 
     * @param serviceId সেবার আইডি
     * @param url চ্যানেল URL
     * @param forceLoad true হলে ক্যাশ বাইপাস করে নেটওয়ার্ক থেকে লোড করে
     * @return ChannelInfo সহ Single (চ্যানেল নাম, সাবস্ক্রাইবার, বর্ণনা ইত্যাদি)
     */
    public static Single<ChannelInfo> getChannelInfo(final int serviceId, 
                                                     @NonNull final String url,
                                                     final boolean forceLoad) {
        checkServiceId(serviceId);
        
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.CHANNEL,
                Single.fromCallable(() ->
                        ChannelInfo.getInfo(NewPipe.getService(serviceId), url))
                .onErrorResumeNext(error -> {
                    Log.e(TAG, "চ্যানেল তথ্য পুনরুদ্ধার ব্যর্থ: " + url, error);
                    return Single.error(error);
                }));
    }

    /**
     * চ্যানেল ট্যাব তথ্য পান (ভিডিও, প্লেলিস্ট, লাইভ ইত্যাদি ট্যাব)।
     * 
     * ব্যবহার: চ্যানেলের বিভিন্ন ট্যাব (ভিডিও, প্লেলিস্ট) থেকে কন্টেন্ট লোড করে।
     * 
     * @param serviceId সেবার আইডি
     * @param listLinkHandler ট্যাব লিংক হ্যান্ডলার (কোন ট্যাব লোড করতে হবে)
     * @param forceLoad true হলে ক্যাশ বাইপাস করে নেটওয়ার্ক থেকে লোড করে
     * @return ChannelTabInfo সহ Single
     */
    public static Single<ChannelTabInfo> getChannelTab(final int serviceId,
                                                       @NonNull final ListLinkHandler listLinkHandler,
                                                       final boolean forceLoad) {
        checkServiceId(serviceId);
        
        return checkCache(forceLoad, serviceId,
                listLinkHandler.getUrl(), InfoCache.Type.CHANNEL_TAB,
                Single.fromCallable(() ->
                        ChannelTabInfo.getInfo(NewPipe.getService(serviceId), listLinkHandler))
                .onErrorResumeNext(error -> {
                    Log.e(TAG, "চ্যানেল ট্যাব তথ্য পুনরুদ্ধার ব্যর্থ", error);
                    return Single.error(error);
                }));
    }

    /**
     * চ্যানেল ট্যাবের পরবর্তী আইটেম পান (পেজিনেশন)।
     * 
     * ব্যবহার: চ্যানেল ট্যাবে "আরও দেখুন" ক্লিক করলে পরবর্তী ভিডিও লোড করে।
     * 
     * @param serviceId সেবার আইডি
     * @param listLinkHandler ট্যাব লিংক হ্যান্ডলার
     * @param nextPage পরবর্তী পৃষ্ঠার তথ্য
     * @return পরবর্তী আইটেম সহ Single
     */
    public static Single<InfoItemsPage<InfoItem>> getMoreChannelTabItems(
            final int serviceId,
            @NonNull final ListLinkHandler listLinkHandler,
            @NonNull final Page nextPage) {
        checkServiceId(serviceId);
        
        return Single.fromCallable(() ->
                ChannelTabInfo.getMoreItems(NewPipe.getService(serviceId),
                        listLinkHandler, nextPage))
        .onErrorResumeNext(error -> {
            Log.e(TAG, "চ্যানেল ট্যাব আইটেম পুনরুদ্ধার ব্যর্থ", error);
            return Single.error(error);
        });
    }

    // ============================================================================
    // মন্তব্য অপারেশন (Comments Operations)
    // ============================================================================

    /**
     * মন্তব্য তথ্য পান (ভিডিওর সমস্ত মন্তব্য)।
     * 
     * ব্যবহার: ভিডিওর মন্তব্য সেকশন খোলার সময় সমস্ত মন্তব্য লোড করে।
     * 
     * @param serviceId সেবার আইডি
     * @param url কন্টেন্ট URL (ভিডিও লিংক)
     * @param forceLoad true হলে ক্যাশ বাইপাস করে নেটওয়ার্ক থেকে লোড করে
     * @return CommentsInfo সহ Single (সমস্ত মন্তব্য এবং মেটাডেটা)
     */
    public static Single<CommentsInfo> getCommentsInfo(final int serviceId,
                                                       @NonNull final String url,
                                                       final boolean forceLoad) {
        checkServiceId(serviceId);
        
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.COMMENTS,
                Single.fromCallable(() ->
                        CommentsInfo.getInfo(NewPipe.getService(serviceId), url))
                .onErrorResumeNext(error -> {
                    Log.e(TAG, "মন্তব্য তথ্য পুনরুদ্ধার ব্যর্থ: " + url, error);
                    return Single.error(error);
                }));
    }

    /**
     * পরবর্তী মন্তব্য পান (CommentsInfo থেকে পেজিনেশন)।
     * 
     * ব্যবহার: মন্তব্য সেকশনে "আরও মন্তব্য" লোড করার সময়।
     * 
     * @param serviceId সেবার আইডি
     * @param info বর্তমান মন্তব্য তথ্য (CommentsInfo অবজেক্ট)
     * @param nextPage পরবর্তী পৃষ্ঠার তথ্য
     * @return পরবর্তী মন্তব্য সহ Single
     */
    public static Single<InfoItemsPage<CommentsInfoItem>> getMoreCommentItems(
            final int serviceId,
            @NonNull final CommentsInfo info,
            @NonNull final Page nextPage) {
        checkServiceId(serviceId);
        
        return Single.fromCallable(() ->
                CommentsInfo.getMoreItems(NewPipe.getService(serviceId), info, nextPage))
        .onErrorResumeNext(error -> {
            Log.e(TAG, "পরবর্তী মন্তব্য পুনরুদ্ধার ব্যর্থ", error);
            return Single.error(error);
        });
    }

    /**
     * পরবর্তী মন্তব্য পান (URL থেকে পেজিনেশন)।
     * 
     * ব্যবহার: URL ব্যবহার করে সরাসরি পরবর্তী মন্তব্য লোড করার সময়।
     * 
     * @param serviceId সেবার আইডি
     * @param url কন্টেন্ট URL
     * @param nextPage পরবর্তী পৃষ্ঠার তথ্য
     * @return পরবর্তী মন্তব্য সহ Single
     */
    public static Single<InfoItemsPage<CommentsInfoItem>> getMoreCommentItems(
            final int serviceId,
            @NonNull final String url,
            @NonNull final Page nextPage) {
        checkServiceId(serviceId);
        
        return Single.fromCallable(() ->
                CommentsInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage))
        .onErrorResumeNext(error -> {
            Log.e(TAG, "পরবর্তী মন্তব্য পুনরুদ্ধার ব্যর্থ", error);
            return Single.error(error);
        });
    }

    // ============================================================================
    // প্লেলিস্ট অপারেশন (Playlist Operations)
    // ============================================================================

    /**
     * প্লেলিস্ট তথ্য পান (প্লেলিস্ট বিস্তারিত এবং ভিডিও)।
     * 
     * ব্যবহার: ব্যবহারকারী যখন কোনো প্লেলিস্ট খোলে তখন সমস্ত ভিডিও লোড করে।
     * 
     * @param serviceId সেবার আইডি
     * @param url প্লেলিস্ট URL
     * @param forceLoad true হলে ক্যাশ বাইপাস করে নেটওয়ার্ক থেকে লোড করে
     * @return PlaylistInfo সহ Single (প্লেলিস্ট নাম, ভিডিও তালিকা ইত্যাদি)
     */
    public static Single<PlaylistInfo> getPlaylistInfo(final int serviceId,
                                                       @NonNull final String url,
                                                       final boolean forceLoad) {
        checkServiceId(serviceId);
        
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.PLAYLIST,
                Single.fromCallable(() ->
                        PlaylistInfo.getInfo(NewPipe.getService(serviceId), url))
                .onErrorResumeNext(error -> {
                    Log.e(TAG, "প্লেলিস্ট তথ্য পুনরুদ্ধার ব্যর্থ: " + url, error);
                    return Single.error(error);
                }));
    }

    /**
     * প্লেলিস্টের পরবর্তী আইটেম পান (পেজিনেশন)।
     * 
     * ব্যবহার: প্লেলিস্টে "আরও ভিডিও" লোড করার সময়।
     * 
     * @param serviceId সেবার আইডি
     * @param url প্লেলিস্ট URL
     * @param nextPage পরবর্তী পৃষ্ঠার তথ্য
     * @return পরবর্তী স্ট্রিম আইটেম সহ Single
     */
    public static Single<InfoItemsPage<StreamInfoItem>> getMorePlaylistItems(
            final int serviceId,
            @NonNull final String url,
            @NonNull final Page nextPage) {
        checkServiceId(serviceId);
        
        return Single.fromCallable(() ->
                PlaylistInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage))
        .onErrorResumeNext(error -> {
            Log.e(TAG, "প্লেলিস্ট আইটেম পুনরুদ্ধার ব্যর্থ", error);
            return Single.error(error);
        });
    }

    // ============================================================================
    // কিয়স্ক অপারেশন (Kiosk Operations)
    // ============================================================================

    /**
     * কিয়স্ক তথ্য পান (ট্রেন্ডিং, জনপ্রিয় ভিডিও ইত্যাদি)।
     * 
     * ব্যবহার: হোম পেজে ট্রেন্ডিং বা জনপ্রিয় ভিডিও দেখানোর সময়।
     * 
     * @param serviceId সেবার আইডি
     * @param url কিয়স্ক URL (ট্রেন্ডিং, জনপ্রিয় ইত্যাদি)
     * @param forceLoad true হলে ক্যাশ বাইপাস করে নেটওয়ার্ক থেকে লোড করে
     * @return KioskInfo সহ Single (ভিডিও তালিকা)
     */
    public static Single<KioskInfo> getKioskInfo(final int serviceId,
                                                 @NonNull final String url,
                                                 final boolean forceLoad) {
        checkServiceId(serviceId);
        
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.KIOSK,
                Single.fromCallable(() -> 
                        KioskInfo.getInfo(NewPipe.getService(serviceId), url))
                .onErrorResumeNext(error -> {
                    Log.e(TAG, "কিয়স্ক তথ্য পুনরুদ্ধার ব্যর্থ: " + url, error);
                    return Single.error(error);
                }));
    }

    /**
     * কিয়স্কের পরবর্তী আইটেম পান (পেজিনেশন)।
     * 
     * ব্যবহার: ট্রেন্ডিং পেজে "আরও ভিডিও" লোড করার সময়।
     * 
     * @param serviceId সেবার আইডি
     * @param url কিয়স্ক URL
     * @param nextPage পরবর্তী পৃষ্ঠার তথ্য
     * @return পরবর্তী স্ট্রিম আইটেম সহ Single
     */
    public static Single<InfoItemsPage<StreamInfoItem>> getMoreKioskItems(
            final int serviceId,
            @NonNull final String url,
            @NonNull final Page nextPage) {
        checkServiceId(serviceId);
        
        return Single.fromCallable(() ->
                KioskInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage))
        .onErrorResumeNext(error -> {
            Log.e(TAG, "কিয়স্ক আইটেম পুনরুদ্ধার ব্যর্থ", error);
            return Single.error(error);
        });
    }

    // ============================================================================
    // ক্যাশ অপারেশন (Cache Operations)
    // ============================================================================

    /**
     * স্মার্ট ক্যাশিং লজিক - ক্যাশ থেকে লোড করুন অথবা নেটওয়ার্ক থেকে লোড করুন।
     * 
     * কাজের প্রক্রিয়া:
     * 1. forceLoad = true: ক্যাশ সম্পূর্ণ বাইপাস করে নেটওয়ার্ক থেকে লোড করে
     * 2. forceLoad = false: প্রথমে ক্যাশ চেক করে, পেলে ক্যাশ থেকে রিটার্ন করে, না পেলে নেটওয়ার্ক থেকে লোড করে
     * 3. নেটওয়ার্ক থেকে লোড করা ডেটা স্বয়ংক্রিয়ভাবে ক্যাশে সংরক্ষিত হয়
     * 
     * ভেরিয়েবল:
     * - forceLoad: ক্যাশ বাইপাস করতে হবে কিনা (boolean)
     * - serviceId: সেবার আইডি (int)
     * - url: কন্টেন্ট URL (String)
     * - cacheType: ক্যাশ টাইপ (STREAM, CHANNEL, PLAYLIST ইত্যাদি)
     * - loadFromNetwork: নেটওয়ার্ক থেকে লোড করার Single
     * 
     * @param <I> Info এর সাবক্লাস (StreamInfo, ChannelInfo ইত্যাদি)
     * @param forceLoad নেটওয়ার্ক থেকে জোর করে লোড করতে হবে কিনা
     * @param serviceId সেবার আইডি
     * @param url কন্টেন্ট URL
     * @param cacheType ক্যাশ টাইপ
     * @param loadFromNetwork নেটওয়ার্ক থেকে লোড করার Single
     * @return লোড করা তথ্য সহ Single
     */
    private static <I extends Info> Single<I> checkCache(
            final boolean forceLoad,
            final int serviceId,
            @NonNull final String url,
            @NonNull final InfoCache.Type cacheType,
            @NonNull final Single<I> loadFromNetwork) {
        
        checkServiceId(serviceId);
        
        final Single<I> actualLoadFromNetwork = loadFromNetwork
                .doOnSuccess(info -> {
                    if (info != null) {
                        CACHE.putInfo(serviceId, url, info, cacheType);
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "ক্যাশে সংরক্ষিত: " + cacheType);
                        }
                    }
                });

        final Single<I> load;
        if (forceLoad) {
            CACHE.removeInfo(serviceId, url, cacheType);
            load = actualLoadFromNetwork;
        } else {
            load = Maybe.concat(
                    loadFromCache(serviceId, url, cacheType),
                    actualLoadFromNetwork.toMaybe()
            )
            .firstElement()
            .toSingle();
        }

        return load;
    }

    /**
     * ক্যাশ থেকে তথ্য লোড করুন।
     * 
     * কাজের প্রক্রিয়া:
     * 1. ক্যাশ থেকে ডেটা খোঁজে
     * 2. পেলে Maybe.just() রিটার্ন করে
     * 3. না পেলে Maybe.empty() রিটার্ন করে
     * 
     * ভেরিয়েবল:
     * - serviceId: সেবার আইডি
     * - url: কন্টেন্ট URL
     * - cacheType: ক্যাশ টাইপ
     * - info: ক্যাশ থেকে পাওয়া তথ্য
     * 
     * @param <I> Info এর সাবক্লাস
     * @param serviceId সেবার আইডি
     * @param url কন্টেন্ট URL
     * @param cacheType ক্যাশ টাইপ
     * @return ক্যাশ করা তথ্য সহ Maybe (খালি যদি ক্যাশে না থাকে)
     */
    private static <I extends Info> Maybe<I> loadFromCache(
            final int serviceId,
            @NonNull final String url,
            @NonNull final InfoCache.Type cacheType) {
        
        checkServiceId(serviceId);
        
        return Maybe.defer(() -> {
            @SuppressWarnings("unchecked")
            final I info = (I) CACHE.getFromKey(serviceId, url, cacheType);
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "ক্যাশ থেকে লোড করা হয়েছে: " + (info != null ? "পাওয়া গেছে" : "পাওয়া যায়নি"));
            }

            return info != null ? Maybe.just(info) : Maybe.empty();
        });
    }

    /**
     * কোনো তথ্য ক্যাশে আছে কিনা তা পরীক্ষা করুন।
     * 
     * ব্যবহার: UI তে ক্যাশ স্ট্যাটাস দেখানোর সময় বা রিফ্রেশ বাটন সক্ষম/অক্ষম করার সময়।
     * 
     * @param serviceId সেবার আইডি
     * @param url কন্টেন্ট URL
     * @param cacheType ক্যাশ টাইপ
     * @return true যদি ক্যাশে থাকে, অন্যথায় false
     */
    public static boolean isCached(final int serviceId,
                                   @NonNull final String url,
                                   @NonNull final InfoCache.Type cacheType) {
        return loadFromCache(serviceId, url, cacheType).blockingGet() != null;
    }

    // ============================================================================
    // ইউটিলিটি মেথড (Utility Methods)
    // ============================================================================

    /**
     * মেটা তথ্য টেক্সট ভিউতে প্রদর্শন করুন।
     * 
     * কাজের প্রক্রিয়া:
     * 1. মেটা তথ্য HTML ফরম্যাটে রূপান্তরিত করে
     * 2. ব্যবহারকারীর পছন্দ অনুযায়ী মেটা তথ্য দেখায় বা লুকায়
     * 3. টেক্সট ভিউতে HTML কন্টেন্ট প্রদর্শন করে
     * 4. লিংক ক্লিকযোগ্য করে তোলে
     * 
     * ভেরিয়েবল:
     * - metaInfos: মেটা তথ্যের তালিকা (শিরোনাম, বর্ণনা, লিংক ইত্যাদি)
     * - metaInfoTextView: মেটা তথ্য প্রদর্শনের জন্য টেক্সট ভিউ
     * - metaInfoSeparator: মেটা তথ্যের উপরে বিভাজক লাইন
     * - disposables: RxJava disposables (ভবিষ্যতের ব্যবহারের জন্য)
     * 
     * @param metaInfos মেটা তথ্যের তালিকা (null বা খালি হতে পারে)
     * @param metaInfoTextView মেটা তথ্য প্রদর্শনের জন্য টেক্সট ভিউ
     * @param metaInfoSeparator বিভাজক ভিউ
     * @param disposables RxJava disposables
     */
    public static void showMetaInfoInTextView(
            @Nullable final List<MetaInfo> metaInfos,
            @NonNull final TextView metaInfoTextView,
            @NonNull final View metaInfoSeparator,
            @NonNull final CompositeDisposable disposables) {
        
        if (metaInfos == null || metaInfos.isEmpty()) {
            hideMetaInfo(metaInfoTextView, metaInfoSeparator);
            return;
        }

        final Context context = metaInfoTextView.getContext();
        final boolean showMetaInfo = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.show_meta_info_key), true);

        if (!showMetaInfo) {
            hideMetaInfo(metaInfoTextView, metaInfoSeparator);
            return;
        }

        final String htmlContent = buildMetaInfoHtml(metaInfos);
        
        metaInfoTextView.setText(HtmlCompat.fromHtml(htmlContent, 
                HtmlCompat.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING));
        metaInfoTextView.setMovementMethod(LinkMovementMethod.getInstance());
        metaInfoTextView.setVisibility(View.VISIBLE);
        metaInfoSeparator.setVisibility(View.VISIBLE);
    }

    /**
     * মেটা তথ্য লুকান।
     * 
     * @param metaInfoTextView মেটা তথ্য টেক্সট ভিউ
     * @param metaInfoSeparator বিভাজক ভিউ
     */
    private static void hideMetaInfo(@NonNull final TextView metaInfoTextView,
                                     @NonNull final View metaInfoSeparator) {
        metaInfoTextView.setVisibility(View.GONE);
        metaInfoSeparator.setVisibility(View.GONE);
    }

    /**
     * মেটা তথ্য থেকে HTML কন্টেন্ট তৈরি করুন।
     * 
     * কাজের প্রক্রিয়া:
     * 1. প্রতিটি মেটা তথ্যের জন্য লুপ করে
     * 2. শিরোনাম বোল্ড করে যোগ করে
     * 3. কন্টেন্ট যোগ করে
     * 4. লিংক যোগ করে
     * 
     * @param metaInfos মেটা তথ্যের তালিকা
     * @return HTML ফরম্যাটে কন্টেন্ট
     */
    private static String buildMetaInfoHtml(@NonNull final List<MetaInfo> metaInfos) {
        final StringBuilder htmlBuilder = new StringBuilder(STRING_BUILDER_CAPACITY);

        for (final MetaInfo metaInfo : metaInfos) {
            if (!isNullOrEmpty(metaInfo.getTitle())) {
                htmlBuilder.append(HTML_BOLD_OPEN)
                        .append(escapeHtml(metaInfo.getTitle()))
                        .append(HTML_BOLD_CLOSE)
                        .append(Localization.DOT_SEPARATOR);
            }

            String content = metaInfo.getContent().getContent().trim();
            content = removePeriodAtEnd(content);
            htmlBuilder.append(escapeHtml(content));

            appendLinksToHtml(htmlBuilder, metaInfo);
        }

        return htmlBuilder.toString();
    }

    /**
     * HTML কন্টেন্টে লিংক যোগ করুন।
     * 
     * কাজের প্রক্রিয়া:
     * 1. মেটা তথ্য থেকে URL এবং URL টেক্সট বের করে
     * 2. প্রতিটি URL এর জন্য HTML লিংক তৈরি করে
     * 3. লিংক টেক্সট নর্মালাইজ করে (সব বড় অক্ষর হলে প্রথম বড় বাকি ছোট করে)
     * 
     * @param htmlBuilder HTML বিল্ডার
     * @param metaInfo মেটা তথ্য
     */
    private static void appendLinksToHtml(@NonNull final StringBuilder htmlBuilder,
                                          @NonNull final MetaInfo metaInfo) {
        final List<String> urls = metaInfo.getUrls() != null
                ? metaInfo.getUrls().stream()
                    .filter(url -> url != null)
                    .map(Object::toString)
                    .collect(Collectors.toList())
                : Collections.emptyList();
        
        final List<String> urlTexts = metaInfo.getUrlTexts() != null
                ? metaInfo.getUrlTexts()
                : Collections.emptyList();

        if (urls.isEmpty() || urlTexts.isEmpty() || urls.size() != urlTexts.size()) {
            return;
        }

        for (int i = 0; i < urls.size(); i++) {
            final String url = urls.get(i);
            final String urlText = urlTexts.get(i);
            
            if (urlText == null || urlText.trim().isEmpty()) {
                continue;
            }

            if (isValidUrl(url)) {
                if (i == 0) {
                    htmlBuilder.append(Localization.DOT_SEPARATOR);
                } else {
                    htmlBuilder.append(HTML_LINE_BREAK);
                }

                htmlBuilder.append(String.format(HTML_LINK_FORMAT,
                        escapeHtml(url),
                        escapeHtml(normalizeAllUppercaseText(urlText.trim()))));
            }
        }
    }

    /**
     * স্ট্রিং এর শেষ থেকে পিরিয়ড সরান।
     * 
     * @param text ইনপুট টেক্সট
     * @return পিরিয়ড ছাড়া টেক্সট
     */
    private static String removePeriodAtEnd(@NonNull final String text) {
        return text.endsWith(HTML_PERIOD) 
                ? text.substring(0, text.length() - 1) 
                : text;
    }

    /**
     * সব বড় অক্ষরের টেক্সট নর্মালাইজ করুন।
     * 
     * কাজের প্রক্রিয়া:
     * 1. সম্পূর্ণ টেক্সট বড় অক্ষরে আছে কিনা চেক করে
     * 2. যদি সব বড় হয়, তাহলে প্রথম অক্ষর বড় এবং বাকি ছোট করে
     * 3. অন্যথায় টেক্সট যেমন আছে তেমন রিটার্ন করে
     * 
     * উদাহরণ: "YOUTUBE" → "Youtube", "YouTube" → "YouTube"
     * 
     * @param text ইনপুট টেক্সট
     * @return নর্মালাইজড টেক্সট
     */
    private static String normalizeAllUppercaseText(@NonNull final String text) {
        if (text.isEmpty()) {
            return text;
        }

        boolean isAllUppercase = true;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLowerCase(text.charAt(i))) {
                isAllUppercase = false;
                break;
            }
        }

        if (!isAllUppercase) {
            return text;
        }

        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }

    /**
     * HTML ইনজেকশন থেকে রক্ষা করতে HTML এস্কেপ করুন।
     * 
     * কাজের প্রক্রিয়া:
     * 1. বিশেষ HTML অক্ষর (&, <, >, ", ') কে এস্কেপ করে
     * 2. এটি XSS (Cross-Site Scripting) আক্রমণ থেকে রক্ষা করে
     * 
     * উদাহরণ: "<script>" → "&lt;script&gt;"
     * 
     * @param text ইনপুট টেক্সট
     * @return এস্কেপড টেক্সট
     */
    private static String escapeHtml(@NonNull final String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * URL ভ্যালিড কিনা তা পরীক্ষা করুন।
     * 
     * কাজের প্রক্রিয়া:
     * 1. URL null বা খালি কিনা চেক করে
     * 2. URL http:// বা https:// দিয়ে শুরু হয় কিনা চেক করে
     * 
     * @param url পরীক্ষা করার URL
     * @return true যদি URL ভ্যালিড হয়
     */
    private static boolean isValidUrl(@Nullable final String url) {
        return url != null && !url.isEmpty() && 
               (url.startsWith("http://") || url.startsWith("https://"));
    }

    // ============================================================================
    // ভ্যালিডেশন মেথড (Validation Methods)
    // ============================================================================

    /**
     * সেবা আইডি ভ্যালিড কিনা তা পরীক্ষা করুন।
     * 
     * কাজের প্রক্রিয়া:
     * 1. serviceId এর মান Constant.NO_SERVICE_ID এর সাথে তুলনা করে
     * 2. যদি সমান হয়, তাহলে IllegalArgumentException ছুড়ে দেয়
     * 
     * @param serviceId পরীক্ষা করার সেবা আইডি
     * @throws IllegalArgumentException যদি serviceId অবৈধ হয়
     */
    private static void checkServiceId(final int serviceId) {
        if (serviceId == Constant.NO_SERVICE_ID) {
            throw new IllegalArgumentException("সেবা আইডি অবৈধ: NO_SERVICE_ID");
        }
    }
}