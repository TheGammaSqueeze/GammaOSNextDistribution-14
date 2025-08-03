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

package com.android.car.settings.applications;

import android.app.usage.UsageStats;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.preference.PreferenceGroup;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

import java.util.List;

/**
 * Class that controls visibility based on whether there have been recently used apps.
 * Hidden if there have are no recently used apps.
 */
public class RecentAppsGroupPreferenceController extends PreferenceController<PreferenceGroup>
        implements RecentAppsItemManager.RecentAppStatsListener {

    // In most cases, device has recently opened apps. So, assume true by default.
    private boolean mAreThereRecentlyUsedApps = true;

    public RecentAppsGroupPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    public int getDefaultAvailabilityStatus() {
        return mAreThereRecentlyUsedApps ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public void onRecentAppStatsLoaded(List<UsageStats> recentAppStats) {
        mAreThereRecentlyUsedApps = !recentAppStats.isEmpty();
        refreshUi();
    }
}
