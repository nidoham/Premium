package com.nidoham.ytpremium.adapter;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.nidoham.ytpremium.databinding.ItemSearchResultBinding;
import com.nidoham.ytpremium.databinding.ItemChannelResultBinding;
import com.nidoham.ytpremium.databinding.ItemPlaylistResultBinding;

import com.nidoham.ytpremium.extractor.image.ThumbnailExtractor;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * StreamsAdapter - সব ধরনের সার্চ রেজাল্ট প্রদর্শন করার জন্য RecyclerView Adapter।
 * 
 * সাপোর্ট করে:
 * - Stream/Video items (item_search_result.xml)
 * - Playlist items (item_playlist_result.xml)
 * - Channel items (item_channel_result.xml)
 * 
 * বৈশিষ্ট্য:
 * - View Binding দিয়ে type-safe view access
 * - DiffUtil দিয়ে efficient আপডেট
 * - Multiple view types সাপোর্ট
 * - Glide দিয়ে image loading
 * - Placeholder avatars with consistent colors
 * - Formatted durations, view counts, subscriber counts
 * 
 * @author NI Doha Mondol
 * @version 2.0
 */
public class StreamsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_STREAM = 0;
    private static final int VIEW_TYPE_CHANNEL = 1;
    private static final int VIEW_TYPE_PLAYLIST = 2;

    private final List<InfoItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    /**
     * আইটেম ক্লিক লিসেনার ইন্টারফেস।
     */
    public interface OnItemClickListener {
        void onStreamClick(StreamInfoItem item);
        void onChannelClick(ChannelInfoItem item);
        void onPlaylistClick(PlaylistInfoItem item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    /**
     * আইটেম তালিকা আপডেট করে DiffUtil ব্যবহার করে।
     * 
     * @param newItems নতুন আইটেম তালিকা
     */
    public void updateItems(List<InfoItem> newItems) {
        if (newItems == null || newItems.isEmpty()) {
            clearItems();
            return;
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
            new SearchDiffCallback(items, newItems)
        );
        items.clear();
        items.addAll(newItems);
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * আইটেম তালিকায় নতুন আইটেম যোগ করে (পেজিনেশনের জন্য)।
     * 
     * @param newItems যোগ করার আইটেম তালিকা
     */
    public void addItems(List<InfoItem> newItems) {
        if (newItems != null && !newItems.isEmpty()) {
            int startPosition = items.size();
            items.addAll(newItems);
            notifyItemRangeInserted(startPosition, newItems.size());
        }
    }

    /**
     * সমস্ত আইটেম সরিয়ে দেয়।
     */
    public void clearItems() {
        int size = items.size();
        items.clear();
        notifyItemRangeRemoved(0, size);
    }

    @Override
    public int getItemViewType(int position) {
        InfoItem item = items.get(position);
        if (item instanceof StreamInfoItem) {
            return VIEW_TYPE_STREAM;
        } else if (item instanceof ChannelInfoItem) {
            return VIEW_TYPE_CHANNEL;
        } else if (item instanceof PlaylistInfoItem) {
            return VIEW_TYPE_PLAYLIST;
        }
        return VIEW_TYPE_STREAM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        switch (viewType) {
            case VIEW_TYPE_STREAM:
                ItemSearchResultBinding streamBinding = 
                    ItemSearchResultBinding.inflate(inflater, parent, false);
                return new StreamViewHolder(streamBinding);

            case VIEW_TYPE_CHANNEL:
                ItemChannelResultBinding channelBinding = 
                    ItemChannelResultBinding.inflate(inflater, parent, false);
                return new ChannelViewHolder(channelBinding);

            case VIEW_TYPE_PLAYLIST:
                ItemPlaylistResultBinding playlistBinding = 
                    ItemPlaylistResultBinding.inflate(inflater, parent, false);
                return new PlaylistViewHolder(playlistBinding);

            default:
                ItemSearchResultBinding defaultBinding = 
                    ItemSearchResultBinding.inflate(inflater, parent, false);
                return new StreamViewHolder(defaultBinding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        InfoItem item = items.get(position);
        
        if (holder instanceof StreamViewHolder) {
            ((StreamViewHolder) holder).bind((StreamInfoItem) item, listener);
        } else if (holder instanceof ChannelViewHolder) {
            ((ChannelViewHolder) holder).bind((ChannelInfoItem) item, listener);
        } else if (holder instanceof PlaylistViewHolder) {
            ((PlaylistViewHolder) holder).bind((PlaylistInfoItem) item, listener);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ==================== ViewHolder: Stream/Video ====================

    /**
     * StreamViewHolder - ভিডিও আইটেম প্রদর্শন করে।
     */
    static class StreamViewHolder extends RecyclerView.ViewHolder {
        
        private final ItemSearchResultBinding binding;

        public StreamViewHolder(@NonNull ItemSearchResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(StreamInfoItem item, OnItemClickListener listener) {
            // Set video title
            binding.videoTitle.setText(item.getName());
            
            // Set uploader name
            binding.videoUploader.setText(item.getUploaderName());
            
            // Set video stats
            binding.videoStats.setText(formatVideoStats(item));
            
            // Set duration
            if (item.getDuration() > 0) {
                binding.itemDuration.setText(formatDuration(item.getDuration()));
                binding.itemDuration.setVisibility(View.VISIBLE);
            } else {
                binding.itemDuration.setVisibility(View.GONE);
            }
            
            // Load video thumbnail
            String thumbnailUrl = getImageUrl(item.getThumbnails());
            loadImage(binding.videoThumbnail, thumbnailUrl, false);
            
            // Load channel avatar with placeholder
            String avatarUrl = getImageUrl(item.getUploaderAvatars());
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                loadImage(binding.channelAvatar, avatarUrl, true);
            } else {
                loadPlaceholderAvatar(binding.channelAvatar, item.getUploaderName());
            }
            
            // Click listeners
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onStreamClick(item);
                }
            });
            
            binding.menuButton.setOnClickListener(v -> {
                // TODO: Show bottom sheet menu
            });
        }

        private String formatVideoStats(StreamInfoItem item) {
            StringBuilder stats = new StringBuilder();
            
            if (item.getViewCount() >= 0) {
                stats.append(formatViewCount(item.getViewCount()));
            }
            
            if (item.getUploadDate() != null) {
                if (stats.length() > 0) stats.append(" • ");
                stats.append(formatUploadDate(item.getUploadDate().offsetDateTime().toString()));
            } else if (item.getTextualUploadDate() != null && !item.getTextualUploadDate().isEmpty()) {
                if (stats.length() > 0) stats.append(" • ");
                stats.append(item.getTextualUploadDate());
            }
            
            return stats.toString();
        }

        private String formatViewCount(long viewCount) {
            if (viewCount < 1000) {
                return viewCount + " views";
            } else if (viewCount < 1000000) {
                return String.format(Locale.US, "%.1fK views", viewCount / 1000.0);
            } else if (viewCount < 1000000000) {
                return String.format(Locale.US, "%.1fM views", viewCount / 1000000.0);
            } else {
                return String.format(Locale.US, "%.1fB views", viewCount / 1000000000.0);
            }
        }

        private String formatUploadDate(String uploadDate) {
            try {
                if (uploadDate.length() >= 10) {
                    return uploadDate.substring(0, 10);
                }
            } catch (Exception e) {
                // Ignore
            }
            return uploadDate;
        }

        private String formatDuration(long seconds) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long secs = seconds % 60;
            
            if (hours > 0) {
                return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs);
            } else {
                return String.format(Locale.US, "%d:%02d", minutes, secs);
            }
        }

        private void loadImage(android.widget.ImageView imageView, String url, boolean circle) {
            if (url != null && !url.isEmpty()) {
                if (circle) {
                    Glide.with(imageView.getContext())
                            .load(url)
                            .circleCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_report_image)
                            .into(imageView);
                } else {
                    Glide.with(imageView.getContext())
                            .load(url)
                            .centerCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_report_image)
                            .into(imageView);
                }
            } else {
                imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        }
        
        private String getImageUrl(List<Image> img) {
        	ThumbnailExtractor thumb = new ThumbnailExtractor(img);
            return thumb.getThumbnail();
        }

        private void loadPlaceholderAvatar(android.widget.ImageView imageView, String name) {
            Drawable placeholder = createAvatarPlaceholder(name);
            Glide.with(imageView.getContext())
                    .load(placeholder)
                    .apply(RequestOptions.circleCropTransform())
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(imageView);
        }

        private Drawable createAvatarPlaceholder(String text) {
            int color = generateColorFromString(text);
            return new ColorDrawable(color);
        }

        private int generateColorFromString(String text) {
            if (text == null || text.isEmpty()) {
                return Color.parseColor("#757575");
            }

            int[] colors = {
                Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
                Color.parseColor("#45B7D1"), Color.parseColor("#FFA07A"),
                Color.parseColor("#98D8C8"), Color.parseColor("#F7DC6F"),
                Color.parseColor("#BB8FCE"), Color.parseColor("#85C1E2"),
                Color.parseColor("#F8B739"), Color.parseColor("#52B788"),
                Color.parseColor("#E76F51"), Color.parseColor("#2A9D8F")
            };

            int hash = Math.abs(text.hashCode());
            return colors[hash % colors.length];
        }
    }

    // ==================== ViewHolder: Channel ====================

    /**
     * ChannelViewHolder - চ্যানেল আইটেম প্রদর্শন করে।
     */
    static class ChannelViewHolder extends RecyclerView.ViewHolder {
        
        private final ItemChannelResultBinding binding;

        public ChannelViewHolder(@NonNull ItemChannelResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ChannelInfoItem item, OnItemClickListener listener) {
            // Set channel name
            binding.channelName.setText(item.getName());
            
            // Set channel handle (if available)
            String handle = item.getName().replaceAll("\\s+", "");
            binding.channelHandle.setText("@" + handle);
            
            // Set subscriber count
            binding.subscriberCount.setText(formatSubscriberCount(item.getSubscriberCount()));
            
            // Set video count (if available)
            long count = item.getStreamCount();
            if (count >= 0) {
                binding.videoCount.setText(count + " videos");
            } else {
                binding.videoCount.setText("Videos");
            }
            
            // Load channel avatar
            String avatarUrl = getImageUrl(item.getThumbnails());
            
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                Glide.with(binding.getRoot().getContext())
                        .load(avatarUrl)
                        .circleCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(binding.channelAvatar);
            } else {
                loadPlaceholderAvatar(binding.channelAvatar, item.getName());
            }
            
            // Click listeners
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onChannelClick(item);
                }
            });
            
            binding.menuButton.setOnClickListener(v -> {
                // TODO: Show bottom sheet menu
            });
        }
        
        private String getImageUrl(List<Image> img) {
        	ThumbnailExtractor thumb = new ThumbnailExtractor(img);
            return thumb.getThumbnail();
        }

        private String formatSubscriberCount(long count) {
            if (count < 0) return "Unknown subscribers";
            if (count >= 1_000_000) {
                return String.format(Locale.US, "%.1fM subscribers", count / 1_000_000.0);
            }
            if (count >= 1_000) {
                return String.format(Locale.US, "%.1fK subscribers", count / 1_000.0);
            }
            return count + " subscribers";
        }

        private void loadPlaceholderAvatar(android.widget.ImageView imageView, String name) {
            Drawable placeholder = createAvatarPlaceholder(name);
            Glide.with(imageView.getContext())
                    .load(placeholder)
                    .apply(RequestOptions.circleCropTransform())
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(imageView);
        }

        private Drawable createAvatarPlaceholder(String text) {
            int color = generateColorFromString(text);
            return new ColorDrawable(color);
        }

        private int generateColorFromString(String text) {
            if (text == null || text.isEmpty()) {
                return Color.parseColor("#757575");
            }

            int[] colors = {
                Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
                Color.parseColor("#45B7D1"), Color.parseColor("#FFA07A"),
                Color.parseColor("#98D8C8"), Color.parseColor("#F7DC6F"),
                Color.parseColor("#BB8FCE"), Color.parseColor("#85C1E2"),
                Color.parseColor("#F8B739"), Color.parseColor("#52B788"),
                Color.parseColor("#E76F51"), Color.parseColor("#2A9D8F")
            };

            int hash = Math.abs(text.hashCode());
            return colors[hash % colors.length];
        }
    }

    // ==================== ViewHolder: Playlist ====================

    /**
     * PlaylistViewHolder - প্লেলিস্ট আইটেম প্রদর্শন করে।
     */
    static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        
        private final ItemPlaylistResultBinding binding;

        public PlaylistViewHolder(@NonNull ItemPlaylistResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(PlaylistInfoItem item, OnItemClickListener listener) {
            // Set playlist name
            binding.playlistName.setText(item.getName());
            
            // Set video count
            binding.itemCount.setText(String.format(Locale.US, "%d videos", item.getStreamCount()));
            
            // Set channel name
            String uploader = item.getUploaderName();
            if (uploader != null && !uploader.isEmpty()) {
                binding.channelName.setText(uploader);
            } else {
                binding.channelName.setText("Unknown Channel");
            }
            
            // Load playlist thumbnail
            String thumbnailUrl = getImageUrl(item.getThumbnails());
            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                Glide.with(binding.getRoot().getContext())
                        .load(thumbnailUrl)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(binding.playlistThumbnail);
            }
            
            // Load channel avatar placeholder
            loadPlaceholderAvatar(binding.channelAvatar, uploader);
            
            // Click listeners
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlaylistClick(item);
                }
            });
            
            binding.menuButton.setOnClickListener(v -> {
                // TODO: Show bottom sheet menu
            });
        }
        
        private String getImageUrl(List<Image> img) {
        	ThumbnailExtractor thumb = new ThumbnailExtractor(img);
            return thumb.getThumbnail();
        }

        private void loadPlaceholderAvatar(android.widget.ImageView imageView, String name) {
            Drawable placeholder = createAvatarPlaceholder(name);
            Glide.with(imageView.getContext())
                    .load(placeholder)
                    .apply(RequestOptions.circleCropTransform())
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(imageView);
        }

        private Drawable createAvatarPlaceholder(String text) {
            int color = generateColorFromString(text);
            return new ColorDrawable(color);
        }

        private int generateColorFromString(String text) {
            if (text == null || text.isEmpty()) {
                return Color.parseColor("#757575");
            }

            int[] colors = {
                Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
                Color.parseColor("#45B7D1"), Color.parseColor("#FFA07A"),
                Color.parseColor("#98D8C8"), Color.parseColor("#F7DC6F"),
                Color.parseColor("#BB8FCE"), Color.parseColor("#85C1E2"),
                Color.parseColor("#F8B739"), Color.parseColor("#52B788"),
                Color.parseColor("#E76F51"), Color.parseColor("#2A9D8F")
            };

            int hash = Math.abs(text.hashCode());
            return colors[hash % colors.length];
        }
    }

    // ==================== DiffUtil Callback ====================

    private static class SearchDiffCallback extends DiffUtil.Callback {
        private final List<InfoItem> oldList;
        private final List<InfoItem> newList;

        public SearchDiffCallback(List<InfoItem> oldList, List<InfoItem> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            InfoItem oldItem = oldList.get(oldPos);
            InfoItem newItem = newList.get(newPos);

            if (oldItem.getClass() != newItem.getClass()) return false;

            return oldItem.getUrl().equals(newItem.getUrl());
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            InfoItem oldItem = oldList.get(oldPos);
            InfoItem newItem = newList.get(newPos);

            if (oldItem instanceof StreamInfoItem) {
                StreamInfoItem old = (StreamInfoItem) oldItem;
                StreamInfoItem newStream = (StreamInfoItem) newItem;
                return old.getName().equals(newStream.getName()) &&
                       old.getUploaderName().equals(newStream.getUploaderName()) &&
                       old.getDuration() == newStream.getDuration();
            } else if (oldItem instanceof ChannelInfoItem) {
                ChannelInfoItem old = (ChannelInfoItem) oldItem;
                ChannelInfoItem newChannel = (ChannelInfoItem) newItem;
                return old.getName().equals(newChannel.getName()) &&
                       old.getSubscriberCount() == newChannel.getSubscriberCount();
            } else if (oldItem instanceof PlaylistInfoItem) {
                PlaylistInfoItem old = (PlaylistInfoItem) oldItem;
                PlaylistInfoItem newPlaylist = (PlaylistInfoItem) newItem;
                return old.getName().equals(newPlaylist.getName()) &&
                       old.getStreamCount() == newPlaylist.getStreamCount();
            }

            return false;
        }
    }
}