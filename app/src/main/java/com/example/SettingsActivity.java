package com.example;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    
    private Switch switchAdblock;
    private Switch switchSponsorBlock;
    private TextView txtAdsBlockedCount;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);

        switchAdblock = findViewById(R.id.switchAdblock);
        switchSponsorBlock = findViewById(R.id.switchSponsorBlock);
        txtAdsBlockedCount = findViewById(R.id.txtAdsBlockedCount);

        switchAdblock.setChecked(prefs.getBoolean("pref_adblock_enabled", true));
        switchSponsorBlock.setChecked(prefs.getBoolean("pref_sponsorblock_enabled", true));
        txtAdsBlockedCount.setText("Ads blocked: " + prefs.getInt("pref_ads_blocked_count", 0));

        switchAdblock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("pref_adblock_enabled", isChecked).apply();
        });

        switchSponsorBlock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("pref_sponsorblock_enabled", isChecked).apply();
        });
    }
}
