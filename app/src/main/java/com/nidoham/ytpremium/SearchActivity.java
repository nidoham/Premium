package com.nidoham.ytpremium;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.nidoham.ytpremium.adapter.SearchSuggestionsAdapter;
import com.nidoham.ytpremium.databinding.ActivitySearchBinding;
import org.schabi.newpipe.extractor.ExtractorHelper;
import org.schabi.newpipe.extractor.ServiceList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class SearchActivity extends AppCompatActivity {
    
    private static final String TAG = "SearchActivity";
    private static final int SUGGESTION_DEBOUNCE_MS = 300;
    
    private ActivitySearchBinding binding;
    
    private SearchSuggestionsAdapter suggestionsAdapter;
    
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final PublishSubject<String> searchSubject = PublishSubject.create();
    private Disposable suggestionDisposable;
    
    private final List<String> suggestions = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        initViews();
        setupRecyclerView();
        setupSearchTextWatcher();
        setupDebounceSearch();
        setupClickListeners();
    }
    
    private void initViews() {
        binding.searchEditText.requestFocus();
    }
    
    private void setupRecyclerView() {
        suggestionsAdapter = new SearchSuggestionsAdapter(suggestions, this::onSuggestionClick);
        binding.searchHistoryRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.searchHistoryRecyclerView.setAdapter(suggestionsAdapter);
    }
    
    private void setupSearchTextWatcher() {
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    binding.btnClearSearch.setVisibility(View.VISIBLE);
                    searchSubject.onNext(s.toString());
                } else {
                    binding.btnClearSearch.setVisibility(View.GONE);
                    clearSuggestions();
                }
            }
            
            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }
    
    private void setupDebounceSearch() {
        Disposable disposable = searchSubject
                .debounce(SUGGESTION_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .filter(query -> !query.trim().isEmpty())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::loadSuggestions,
                        error -> Log.e(TAG, "সার্চ সাবজেক্ট এরর", error)
                );
        
        disposables.add(disposable);
    }
    
    private void loadSuggestions(String query) {
        if (suggestionDisposable != null && !suggestionDisposable.isDisposed()) {
            suggestionDisposable.dispose();
        }
        
        Log.d(TAG, "সাজেশন লোড করা হচ্ছে: " + query);
        
        suggestionDisposable = ExtractorHelper
                .suggestionsFor(ServiceList.YouTube.getServiceId(), query)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::onSuggestionsLoaded,
                        this::onSuggestionsError
                );
        
        disposables.add(suggestionDisposable);
    }
    
    private void onSuggestionsLoaded(List<String> loadedSuggestions) {
        Log.d(TAG, "সাজেশন পাওয়া গেছে: " + loadedSuggestions.size());
        
        suggestions.clear();
        
        if (loadedSuggestions != null && !loadedSuggestions.isEmpty()) {
            suggestions.addAll(loadedSuggestions);
        }
        
        suggestionsAdapter.notifyDataSetChanged();
        
        if (!suggestions.isEmpty()) {
            binding.searchHistoryRecyclerView.setVisibility(View.VISIBLE);
        }
    }
    
    private void onSuggestionsError(Throwable error) {
        Log.e(TAG, "সাজেশন লোড করতে ব্যর্থ", error);
        Toast.makeText(this, "সাজেশন লোড করতে ব্যর্থ: " + error.getMessage(), 
                Toast.LENGTH_SHORT).show();
        clearSuggestions();
    }
    
    private void onSuggestionClick(String suggestion) {
        Log.d(TAG, "সাজেশন নির্বাচিত: " + suggestion);
        
        Intent intent = new Intent(this, SearchResultActivity.class);
        intent.putExtra("SEARCH_QUERY", suggestion);
        startActivity(intent);
        finish();
    }
    
    private void clearSuggestions() {
        suggestions.clear();
        suggestionsAdapter.notifyDataSetChanged();
        binding.searchHistoryRecyclerView.setVisibility(View.GONE);
    }
    
    private void setupClickListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        
        binding.btnClearSearch.setOnClickListener(v -> {
            binding.searchEditText.setText("");
            binding.searchEditText.requestFocus();
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }
}