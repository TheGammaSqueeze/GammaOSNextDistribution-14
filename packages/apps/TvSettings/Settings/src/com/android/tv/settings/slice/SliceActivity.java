/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tv.settings.slice;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.android.tv.settings.TvSettingsActivity;
import com.android.tv.twopanelsettings.slices.SliceFragment;
import com.android.tv.twopanelsettings.slices.SlicesConstants;

/**
 * Extend this class to implement slice settings activity.
 */
public class SliceActivity extends TvSettingsActivity {
    private static final String EXTRA_SLICE_URI = "slice_uri";
    private static final String EXTRA_STARTUP_VERIFICATION_REQUIRED =
            "startup_verification_required";

    /** Provide slice uri */
    public String getSliceUri() {
        return getIntent().getStringExtra(EXTRA_SLICE_URI);
    }

    /** Provide slice screen title */
    public String getScreenTitle() {
        return null;
    }

    @Override
    protected Fragment createSettingsFragment() {
        Bundle bundle = new Bundle();
        String sliceUri = getSliceUri();
        if (sliceUri == null) {
            return null;
        }
        bundle.putString(SlicesConstants.TAG_TARGET_URI, sliceUri);
        bundle.putString(SlicesConstants.TAG_SCREEN_TITLE, getScreenTitle());
        return com.android.tv.settings.overlay.FlavorUtils.getFeatureFactory(
                this).getSettingsFragmentProvider()
                .newSettingsFragment(SliceFragment.class.getName(), bundle);
    }

    // The startup verification requirement is assumed unless otherwise specified in the Intent, as
    // this Activity may be started with "SLICE_SETTINGS" action combined with an URL extra
    // corresponding to a sensitive settings page such as account settings.
    @Override
    protected boolean isStartupVerificationRequired() {
        return getIntent().getBooleanExtra(EXTRA_STARTUP_VERIFICATION_REQUIRED, true);
    }
}
