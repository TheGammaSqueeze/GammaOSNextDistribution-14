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

import android.app.Application;
import android.app.usage.UsageStats;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserHandle;
import android.text.format.DateUtils;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.ui.preference.CarUiPreference;
import com.android.settingslib.Utils;
import com.android.settingslib.applications.ApplicationsState;

import java.util.ArrayList;
import java.util.List;

/**
 * Class responsible for displaying recently used apps.
 */
public class RecentAppsListPreferenceController extends PreferenceController<PreferenceCategory>
        implements RecentAppsItemManager.RecentAppStatsListener {

    private ApplicationsState mApplicationsState;
    private int mUserId;
    private List<UsageStats> mRecentAppStats;
    private int mMaxRecentAppsCount;

    public RecentAppsListPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions, ApplicationsState
                .getInstance((Application) context.getApplicationContext()));
    }

    @VisibleForTesting
    RecentAppsListPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            ApplicationsState applicationsState) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mApplicationsState = applicationsState;
        mUserId = UserHandle.myUserId();
        mRecentAppStats = new ArrayList<>();
        mMaxRecentAppsCount = getContext().getResources().getInteger(
                R.integer.recent_apps_max_count);
    }

    @Override
    public void onRecentAppStatsLoaded(List<UsageStats> recentAppStats) {
        mRecentAppStats = recentAppStats;
        refreshUi();
    }

    @Override
    protected Class<PreferenceCategory> getPreferenceType() {
        return PreferenceCategory.class;
    }

    @Override
    protected void updateState(PreferenceCategory preferenceCategory) {
        preferenceCategory.setVisible(!mRecentAppStats.isEmpty());
        preferenceCategory.removeAll();

        int prefCount = 0;
        for (UsageStats usageStats : mRecentAppStats) {
            Preference pref = createPreference(getContext(), usageStats);

            if (pref != null) {
                getPreference().addPreference(pref);
                prefCount++;
                if (prefCount == mMaxRecentAppsCount) {
                    break;
                }
            }
        }
    }

    private Preference createPreference(Context context, UsageStats usageStats) {
        String pkgName = usageStats.getPackageName();
        ApplicationsState.AppEntry appEntry = mApplicationsState.getEntry(pkgName, mUserId);

        if (appEntry == null) {
            return null;
        }

        Preference pref = new CarUiPreference(context);
        pref.setTitle(appEntry.label);
        if (appEntry.icon == null) {
            pref.setIcon(Utils.getBadgedIcon(context, appEntry.info));
        } else {
            pref.setIcon(appEntry.icon);
        }
        pref.setSummary(DateUtils.getRelativeTimeSpanString(usageStats.getLastTimeStamp(),
                System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS));
        pref.setOnPreferenceClickListener(p -> {
            getFragmentController().launchFragment(
                    ApplicationDetailsFragment.getInstance(pkgName));
            return true;
        });

        return pref;
    }
}
