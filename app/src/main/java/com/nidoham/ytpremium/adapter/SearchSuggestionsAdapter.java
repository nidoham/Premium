package com.nidoham.ytpremium.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nidoham.ytpremium.R;

import java.util.List;

/**
 * SearchSuggestionsAdapter - সার্চ সাজেশন প্রদর্শনের জন্য অ্যাডাপ্টার।
 * 
 * বৈশিষ্ট্য:
 * - সাজেশন তালিকা প্রদর্শন
 * - ক্লিক হ্যান্ডলিং
 * - সার্চ আইকন প্রদর্শন
 * 
 * @author NI Doha Mondol
 */
public class SearchSuggestionsAdapter extends RecyclerView.Adapter<SearchSuggestionsAdapter.SuggestionViewHolder> {
    
    private final List<String> suggestions;
    private final OnSuggestionClickListener clickListener;
    
    /**
     * সাজেশন ক্লিক লিসেনার ইন্টারফেস।
     */
    public interface OnSuggestionClickListener {
        void onSuggestionClick(String suggestion);
    }
    
    /**
     * Constructor.
     * 
     * @param suggestions সাজেশন তালিকা
     * @param clickListener ক্লিক লিসেনার
     */
    public SearchSuggestionsAdapter(List<String> suggestions, OnSuggestionClickListener clickListener) {
        this.suggestions = suggestions;
        this.clickListener = clickListener;
    }
    
    @NonNull
    @Override
    public SuggestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_suggestion, parent, false);
        return new SuggestionViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull SuggestionViewHolder holder, int position) {
        String suggestion = suggestions.get(position);
        holder.bind(suggestion);
    }
    
    @Override
    public int getItemCount() {
        return suggestions.size();
    }
    
    /**
     * ViewHolder ক্লাস।
     */
    class SuggestionViewHolder extends RecyclerView.ViewHolder {
        
        private final ImageView iconSearch;
        private final TextView textSuggestion;
        
        public SuggestionViewHolder(@NonNull View itemView) {
            super(itemView);
            iconSearch = itemView.findViewById(R.id.iconSearch);
            textSuggestion = itemView.findViewById(R.id.textSuggestion);
            
            // আইটেম ক্লিক লিসেনার
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && clickListener != null) {
                    clickListener.onSuggestionClick(suggestions.get(position));
                }
            });
        }
        
        /**
         * সাজেশন ডেটা bind করুন।
         * 
         * @param suggestion সাজেশন টেক্সট
         */
        public void bind(String suggestion) {
            textSuggestion.setText(suggestion);
        }
    }
}