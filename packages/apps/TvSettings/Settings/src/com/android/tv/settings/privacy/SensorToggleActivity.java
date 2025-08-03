/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tv.settings.privacy;

import android.os.Bundle;
import android.util.Log;

import androidx.fragment.app.Fragment;

import com.android.tv.settings.TvSettingsActivity;

/**
 * Activity allowing microphone/camera privacy management.
 */
public class SensorToggleActivity extends TvSettingsActivity {
    private static final String TAG = SensorToggleActivity.class.getSimpleName();
    private static final String MIC_INTENT = "android.settings.MANAGE_MICROPHONE_PRIVACY";
    private static final String CAMERA_INTENT = "android.settings.MANAGE_CAMERA_PRIVACY";

    @Override
    protected Fragment createSettingsFragment() {
        String action = getIntent().getAction();
        if (action == null) return null;

        PrivacyToggle toggle;
        switch (action) {
            case MIC_INTENT:
                toggle = PrivacyToggle.MIC_TOGGLE;
                break;
            case CAMERA_INTENT:
                toggle = PrivacyToggle.CAMERA_TOGGLE;
                break;
            default:
                Log.w(TAG, "Unrecognized action " + action);
                return null;
        }

        if (!toggle.isPresentAndEnabled(this)) {
            Log.w(TAG, "Toggle not supported or not enabled");
            return null;
        }

        Bundle args = new Bundle();
        args.putObject(SensorFragment.TOGGLE_EXTRA, toggle);
        if (toggle == PrivacyToggle.MIC_TOGGLE) {
            return com.android.tv.settings.overlay.FlavorUtils.getFeatureFactory(
                    this).getSettingsFragmentProvider()
                    .newSettingsFragment(MicrophoneFragment.class.getName(), args);
        } else {
            return com.android.tv.settings.overlay.FlavorUtils.getFeatureFactory(
                    this).getSettingsFragmentProvider()
                    .newSettingsFragment(SensorFragment.class.getName(), args);
        }
    }

}
