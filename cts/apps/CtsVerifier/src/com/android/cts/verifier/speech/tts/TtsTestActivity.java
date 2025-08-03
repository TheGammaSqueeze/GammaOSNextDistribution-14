package com.android.cts.verifier.speech.tts;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Guide the user to run test for the TTS API.
 */
public class TtsTestActivity extends PassFailButtons.Activity {

    private static final String TTS_INTENT = "com.android.settings.TTS_SETTINGS";

    private Button mAccessibilitySettingsButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.tts_main);
        setInfoResources(R.string.tts_test, R.string.tts_test_info, -1);
        setPassFailButtonClickListeners();

        mAccessibilitySettingsButton = findViewById(R.id.accessibility_settings_button);
        mAccessibilitySettingsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent;
                if (isAutomotive()) {
                    // Automotive doesn't have accessibility settings - open TTS settings directly
                    intent = new Intent(TTS_INTENT);
                } else {
                    intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                }
                try {
                    startActivityForResult(intent, 0);
                } catch (ActivityNotFoundException e) { }
            }
        });
    }

    private boolean isAutomotive() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }
}
