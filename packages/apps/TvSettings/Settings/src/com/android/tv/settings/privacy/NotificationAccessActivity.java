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

import static com.android.tv.settings.overlay.FlavorUtils.ALL_FLAVORS_MASK;

import androidx.fragment.app.Fragment;

import com.android.tv.settings.TvSettingsActivity;
import com.android.tv.settings.device.apps.specialaccess.NotificationAccess;

/** The Activity that shows notification access settings UI. */
public class NotificationAccessActivity extends TvSettingsActivity {

    @Override
    protected Fragment createSettingsFragment() {
        return com.android.tv.settings.overlay.FlavorUtils.getFeatureFactory(
                this).getSettingsFragmentProvider()
                .newSettingsFragment(NotificationAccess.class.getName(), null);
    }

    @Override
    protected int getAvailableFlavors() {
        return ALL_FLAVORS_MASK;
    }
}
