package com.nidoham.ytpremium;

import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import android.os.Bundle;
import android.view.View;
import com.nidoham.ytpremium.databinding.ActivityMainBinding;
import com.nidoham.ytpremium.fragments.HomeFragment;
import com.nidoham.ytpremium.fragments.ShortsFragment;
import com.nidoham.ytpremium.fragments.SubscriptionsFragment;
import com.nidoham.ytpremium.fragments.YouFragment;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        setupToolbar();
        setupBottomNavigation();
        
        // Load home fragment by default
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
            selectNavItem(binding.navHome);
        }
    }
    
    private void setupToolbar() {
        // Cast button
        binding.btnCast.setOnClickListener(v -> {
            // Handle cast click
        });
        
        // Notification button
        binding.btnNotification.setOnClickListener(v -> {
            // Handle notification click
        });
        
        // Search button
        binding.btnSearch.setOnClickListener(v -> {
            // Handle search click
            startActivity(new Intent(getApplicationContext(), SearchActivity.class));
        });
    }
    
    private void setupBottomNavigation() {
        binding.navHome.setOnClickListener(v -> {
            loadFragment(new HomeFragment());
            selectNavItem(binding.navHome);
        });
        
        binding.navShorts.setOnClickListener(v -> {
            loadFragment(new ShortsFragment());
            selectNavItem(binding.navShorts);
        });
        
        binding.navSubscriptions.setOnClickListener(v -> {
            loadFragment(new SubscriptionsFragment());
            selectNavItem(binding.navSubscriptions);
        });
        
        binding.navYou.setOnClickListener(v -> {
            loadFragment(new YouFragment());
            selectNavItem(binding.navYou);
        });
    }
    
    private void loadFragment(Fragment fragment) {
        currentFragment = fragment;
        getSupportFragmentManager()
            .beginTransaction()
            .replace(binding.fragmentContainer.getId(), fragment)
            .commit();
    }
    
    private void selectNavItem(View selectedNav) {
        // Reset all nav items
        resetNavItem(binding.navHome, binding.navHomeIcon, binding.navHomeLabel);
        resetNavItem(binding.navShorts, binding.navShortsIcon, binding.navShortsLabel);
        resetNavItem(binding.navSubscriptions, binding.navSubscriptionsIcon, binding.navSubscriptionsLabel);
        resetNavItem(binding.navYou, binding.navYouIcon, binding.navYouLabel);
        
        // Highlight selected item
        if (selectedNav.getId() == binding.navHome.getId()) {
            highlightNavItem(binding.navHomeIcon, binding.navHomeLabel);
        } else if (selectedNav.getId() == binding.navShorts.getId()) {
            highlightNavItem(binding.navShortsIcon, binding.navShortsLabel);
        } else if (selectedNav.getId() == binding.navSubscriptions.getId()) {
            highlightNavItem(binding.navSubscriptionsIcon, binding.navSubscriptionsLabel);
        } else if (selectedNav.getId() == binding.navYou.getId()) {
            highlightNavItem(binding.navYouIcon, binding.navYouLabel);
        }
    }
    
    private void resetNavItem(View nav, View icon, View label) {
        icon.setAlpha(0.6f);
        label.setAlpha(0.6f);
    }
    
    private void highlightNavItem(View icon, View label) {
        icon.setAlpha(1.0f);
        label.setAlpha(1.0f);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}