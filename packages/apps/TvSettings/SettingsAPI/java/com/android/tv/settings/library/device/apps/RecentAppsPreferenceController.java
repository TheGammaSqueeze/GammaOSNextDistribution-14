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

package com.android.tv.settings.library.device.apps;

import android.app.Application;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.IconDrawableFactory;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This controller displays a list of recently used apps and a "See all" button.
 */
public class RecentAppsPreferenceController extends AbstractPreferenceController
        implements Comparator<UsageStats> {

    private static final String TAG = "RecentAppsPreferenceController";
    private static final String KEY_PREF_CATEGORY = "recently_used_apps_category";
    @VisibleForTesting
    static final String KEY_SEE_ALL = "see_all_apps";
    private static final int SHOW_RECENT_APP_COUNT = 5;
    private static final Set<String> SKIP_SYSTEM_PACKAGES = new ArraySet<>();

    private final PackageManager mPm;
    private final UsageStatsManager mUsageStatsManager;
    private final ApplicationsState mApplicationsState;
    private final int mUserId;
    private final IconDrawableFactory mIconDrawableFactory;

    private Calendar mCal;
    private List<UsageStats> mStats;

    static {
        SKIP_SYSTEM_PACKAGES.addAll(Arrays.asList(
                "android",
                "com.android.tv.settings",
                "com.android.systemui",
                "com.android.providers.calendar",
                "com.android.providers.media"
        ));
    }

    public RecentAppsPreferenceController(Context context, Application app,
            UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager preferenceCompatManager) {
        this(context, app == null ? null : ApplicationsState.getInstance(app), callback,
                stateIdentifier, preferenceCompatManager);
    }

    @VisibleForTesting
    RecentAppsPreferenceController(Context context, ApplicationsState appState,
            UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager preferenceCompatManager) {
        super(context, callback, stateIdentifier, preferenceCompatManager);
        mIconDrawableFactory = IconDrawableFactory.newInstance(context);
        mUserId = UserHandle.myUserId();
        mPm = context.getPackageManager();
        mUsageStatsManager = context.getSystemService(UsageStatsManager.class);
        mApplicationsState = appState;
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{KEY_PREF_CATEGORY};
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void init() {
        update();
    }

    public void update() {
        reloadData();
        final List<UsageStats> recentApps = getDisplayableRecentAppList();
        if (recentApps != null && !recentApps.isEmpty()) {
            displayRecentApps(recentApps);
        } else {
            displayOnlyAllApps();
        }
    }

    private void displayOnlyAllApps() {
        mPreferenceCompat.setVisible(false);
    }

    private void displayRecentApps(List<UsageStats> recentApps) {
        mPreferenceCompat.setVisible(true);
        final int recentAppsCount = recentApps.size();
        for (int i = 0; i < recentAppsCount; i++) {
            final UsageStats stat = recentApps.get(i);
            // Bind recent apps to existing prefs if possible, or create a new pref.
            final String pkgName = stat.getPackageName();
            final ApplicationsState.AppEntry appEntry =
                    mApplicationsState.getEntry(pkgName, mUserId);
            if (appEntry == null) {
                continue;
            }

            String[] prefKey = new String[]{KEY_PREF_CATEGORY, pkgName};
            PreferenceCompat pref = new PreferenceCompat(prefKey);
            pref.setTitle(appEntry.label);
            pref.setIcon(mIconDrawableFactory.getBadgedIcon(appEntry.info));
            pref.setSummary(StringUtil.formatRelativeTime(mContext,
                    System.currentTimeMillis() - stat.getLastTimeUsed(), false).toString());
            pref.setNextState(ManagerUtil.STATE_APP_MANAGEMENT);
            Bundle nextStateExtras = new Bundle();
            AppManagementState.prepareArgs(nextStateExtras, pkgName);
            pref.setExtras(nextStateExtras);
            mPreferenceCompat.addChildPrefCompat(pref);
        }
    }

    @Override
    public final int compare(UsageStats a, UsageStats b) {
        // return by descending order
        return Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed());
    }

    @VisibleForTesting
    void reloadData() {
        mCal = Calendar.getInstance();
        mCal.add(Calendar.DAY_OF_YEAR, -1);
        mStats = mUsageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, mCal.getTimeInMillis(),
                System.currentTimeMillis());
    }

    private List<UsageStats> getDisplayableRecentAppList() {
        final List<UsageStats> recentApps = new ArrayList<>();
        final Map<String, UsageStats> map = new ArrayMap<>();
        final int statCount = mStats.size();
        for (int i = 0; i < statCount; i++) {
            final UsageStats pkgStats = mStats.get(i);
            if (!shouldIncludePkgInRecents(pkgStats)) {
                continue;
            }
            final String pkgName = pkgStats.getPackageName();
            final UsageStats existingStats = map.get(pkgName);
            if (existingStats == null) {
                map.put(pkgName, pkgStats);
            } else {
                existingStats.add(pkgStats);
            }
        }
        final List<UsageStats> packageStats = new ArrayList<>();
        packageStats.addAll(map.values());
        Collections.sort(packageStats, this /* comparator */);
        int count = 0;
        for (UsageStats stat : packageStats) {
            final ApplicationsState.AppEntry appEntry = mApplicationsState.getEntry(
                    stat.getPackageName(), mUserId);
            if (appEntry == null) {
                continue;
            }
            recentApps.add(stat);
            count++;
            if (count >= SHOW_RECENT_APP_COUNT) {
                break;
            }
        }
        return recentApps;
    }

    /**
     * Whether or not the app should be included in recent list.
     */
    private boolean shouldIncludePkgInRecents(UsageStats stat) {
        final String pkgName = stat.getPackageName();
        if (stat.getLastTimeUsed() < mCal.getTimeInMillis()) {
            Log.d(TAG, "Invalid timestamp, skipping " + pkgName);
            return false;
        }

        if (SKIP_SYSTEM_PACKAGES.contains(pkgName)) {
            Log.d(TAG, "System package, skipping " + pkgName);
            return false;
        }
        final Intent launchIntent = new Intent().addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setPackage(pkgName);

        if (mPm.resolveActivity(launchIntent, 0) == null) {
            // Not visible on launcher -> likely not a user visible app, skip if non-instant.
            final ApplicationsState.AppEntry appEntry =
                    mApplicationsState.getEntry(pkgName, mUserId);
            if (appEntry == null || appEntry.info == null || !AppUtils.isInstant(appEntry.info)) {
                Log.d(TAG, "Not a user visible or instant app, skipping " + pkgName);
                return false;
            }
        }
        return true;
    }
}

