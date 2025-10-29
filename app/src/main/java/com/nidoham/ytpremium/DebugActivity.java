package com.nidoham.ytpremium;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.nidoham.ytpremium.databinding.ActivityDebugBinding;

public class DebugActivity extends AppCompatActivity {

    private static final String CRASH_REPORT_KEY = "CRASH_REPORT";
    private static final String SUPPORT_EMAIL = "support@bongotube.com";
    private static final String EMAIL_SUBJECT = "BongoTube Crash Report";

    private ActivityDebugBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDebugBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupViews();
        loadCrashReport();
        setupListeners();
    }

    private void setupViews() {
        binding.tvCrashReport.setMovementMethod(new ScrollingMovementMethod());
    }

    private void loadCrashReport() {
        String report = getIntent().getStringExtra(CRASH_REPORT_KEY);

        if (report != null && !report.isEmpty()) {
            binding.tvCrashReport.setText(report);
        } else {
            binding.tvCrashReport.setText("কোন ক্র্যাশ রিপোর্ট পাওয়া যায়নি");
            disableReportActions();
        }
    }

    private void disableReportActions() {
        binding.btnSendReport.setEnabled(false);
        binding.btnCopyReport.setEnabled(false);
    }

    private void setupListeners() {
        binding.btnSendReport.setOnClickListener(v -> {
            String report = binding.tvCrashReport.getText().toString();
            sendEmail(report);
        });

        binding.btnCopyReport.setOnClickListener(v -> {
            String report = binding.tvCrashReport.getText().toString();
            copyToClipboard(report);
        });

        binding.btnRestartApp.setOnClickListener(v -> restartApp());
    }

    private void sendEmail(String report) {
        if (report.trim().isEmpty()) {
            showToast("ক্র্যাশ রিপোর্ট পাঠানো সম্ভব নয়");
            return;
        }
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("message/rfc822");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {SUPPORT_EMAIL});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, EMAIL_SUBJECT);
        emailIntent.putExtra(Intent.EXTRA_TEXT, getEmailBody(report));

        try {
            startActivity(Intent.createChooser(emailIntent, "ক্র্যাশ রিপোর্ট পাঠান"));
        } catch (ActivityNotFoundException ex) {
            fallbackShare(report);
        }
    }

    private String getEmailBody(String report) {
        return "প্রিয় BongoTube দল, আপনার অ্যাপ্লিকেশনে নিচের ত্রুটি ঘটেছে:" + report;
    }

    private void fallbackShare(String report) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, report);

        try {
            startActivity(Intent.createChooser(shareIntent, "ক্র্যাশ রিপোর্ট শেয়ার করুন"));
        } catch (ActivityNotFoundException ex) {
            showToast("রিপোর্ট শেয়ার করার জন্য কোনো অ্যাপ পাওয়া যায়নি");
        }
    }

    private void copyToClipboard(String report) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText(EMAIL_SUBJECT, report);
            clipboard.setPrimaryClip(clip);
            showToast("ক্র্যাশ রিপোর্ট ক্লিপবোর্ডে কপি হয়েছে");
        } else {
            showToast("ক্লিপবোর্ড অ্যাক্সেস ব্যর্থ হয়েছে");
        }
    }

    private void restartApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            showToast("অ্যাপ্লিকেশন পুনরায় শুরু করা যাচ্ছে না");
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishAffinity();  // সম্পূর্ণ অ্যাপ বন্ধ করেই বের হওয়া
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;  // মেমরি লিক এড়ানোর জন্য
    }
}