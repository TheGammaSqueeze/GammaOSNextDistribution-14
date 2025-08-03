/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tv.settings.system;

import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_CLASSIC;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.tv.settings.TvSettingsActivity;

/**
 * A subset of security settings to enter a restricted profile.
 */
public class EnterRestrictedProfileActivity extends TvSettingsActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDimAmount(0f);
    }

    @Override
    protected Fragment createSettingsFragment() {
        return com.android.tv.settings.overlay.FlavorUtils.getFeatureFactory(this)
                .getSettingsFragmentProvider()
                .newSettingsFragment(EnterRestrictedProfileFragment.class.getName(), null);
    }

    @Override
    protected int getAvailableFlavors() {
        return FLAVOR_CLASSIC;
    }
}
