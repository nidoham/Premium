package com.nidoham.ytpremium;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.nidoham.ytpremium.databinding.ActivitySplashBinding;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MILLIS = 500; // Delay duration for splash screen
    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize view binding
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Delay and navigate to MainActivity
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);

            // ✅ Modern transition (instead of deprecated overridePendingTransition)
            Bundle options = ActivityOptions.makeCustomAnimation(
                    SplashActivity.this,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            ).toBundle();

            startActivity(intent, options);
            finish();
        }, SPLASH_DELAY_MILLIS);
    }
}