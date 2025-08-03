/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.settingslib.applications.AppUtils;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.utils.ThreadUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Class for fetching and returning recently used apps. Largely derived from
 * {@link com.android.settings.applications.RecentAppStatsMixin}.
 */
public class RecentAppsItemManager implements Comparator<UsageStats> {

    private static final Logger LOG = new Logger(RecentAppsItemManager.class);

    @VisibleForTesting
    final List<UsageStats> mRecentApps;
    private final int mUserId;
    private final int mMaximumApps;
    private final Context mContext;
    private final PackageManager mPm;
    private final UsageStatsManager mUsageStatsManager;
    private final ApplicationsState mApplicationsState;
    private final SparseArray<RecentAppStatsListener> mAppStatsListeners;
    private final int mDaysThreshold;
    private final List<String> mIgnoredPackages;
    private Calendar mCalendar;

    public RecentAppsItemManager(Context context, int maximumApps) {
        this(context, maximumApps, ApplicationsState.getInstance(
                (Application) context.getApplicationContext()));
    }

    @VisibleForTesting
    RecentAppsItemManager(Context context, int maximumApps, ApplicationsState applicationsState) {
        mContext = context;
        mMaximumApps = maximumApps;
        mUserId = UserHandle.myUserId();
        mPm = mContext.getPackageManager();
        mUsageStatsManager = mContext.getSystemService(UsageStatsManager.class);
        mApplicationsState = applicationsState;
        mRecentApps = new ArrayList<>();
        mAppStatsListeners = new SparseArray<>();
        mDaysThreshold = mContext.getResources()
                .getInteger(R.integer.recent_apps_days_threshold);
        mIgnoredPackages = Arrays.asList(mContext.getResources()
                .getStringArray(R.array.recent_apps_ignored_packages));
    }

    /**
     * Starts fetching recently used apps
     */
    public void startLoading() {
        ThreadUtils.postOnBackgroundThread(() -> {
            loadDisplayableRecentApps(mMaximumApps);
            for (int i = 0; i < mAppStatsListeners.size(); i++) {
                int finalIndex = i;
                ThreadUtils.postOnMainThread(() -> mAppStatsListeners.valueAt(finalIndex)
                        .onRecentAppStatsLoaded(mRecentApps));
            }
        });
    }

    @Override
    public final int compare(UsageStats a, UsageStats b) {
        // return by descending order
        return Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed());
    }

    /**
     * Registers a listener that will be notified once the data is loaded.
     */
    public void addListener(@NonNull RecentAppStatsListener listener) {
        mAppStatsListeners.append(mAppStatsListeners.size(), listener);
    }

    @VisibleForTesting
    void loadDisplayableRecentApps(int number) {
        mRecentApps.clear();
        mCalendar = Calendar.getInstance();
        mCalendar.add(Calendar.DAY_OF_YEAR, -mDaysThreshold);
        List<UsageStats> mStats = mUsageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_BEST, mCalendar.getTimeInMillis(),
                        System.currentTimeMillis());

        Map<String, UsageStats> map = new ArrayMap<>();
        for (UsageStats pkgStats : mStats) {
            if (!shouldIncludePkgInRecents(pkgStats)) {
                continue;
            }
            String pkgName = pkgStats.getPackageName();
            UsageStats existingStats = map.get(pkgName);
            if (existingStats == null) {
                map.put(pkgName, pkgStats);
            } else {
                existingStats.add(pkgStats);
            }
        }
        List<UsageStats> packageStats = new ArrayList<>();
        packageStats.addAll(map.values());
        Collections.sort(packageStats, /* comparator= */ this);
        int count = 0;
        for (UsageStats stat : packageStats) {
            ApplicationsState.AppEntry appEntry = mApplicationsState.getEntry(
                    stat.getPackageName(), mUserId);
            if (appEntry == null) {
                continue;
            }
            mRecentApps.add(stat);
            count++;
            if (count >= number) {
                break;
            }
        }
    }

    /**
     * Whether or not the app should be included in recent list.
     */
    private boolean shouldIncludePkgInRecents(UsageStats stat) {
        String pkgName = stat.getPackageName();
        if (stat.getLastTimeUsed() < mCalendar.getTimeInMillis()) {
            LOG.d("Invalid timestamp (usage time is more than 24 hours ago), skipping "
                    + pkgName);
            return false;
        }

        if (mIgnoredPackages.contains(pkgName)) {
            LOG.d("System package, skipping " + pkgName);
            return false;
        }

        if (AppUtils.isHiddenSystemModule(mContext, pkgName)) {
            return false;
        }

        Intent launchIntent = new Intent().addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(pkgName);

        if (mPm.resolveActivity(launchIntent, 0) == null) {
            // Not visible on launcher -> likely not a user visible app, skip if non-instant.
            ApplicationsState.AppEntry appEntry =
                    mApplicationsState.getEntry(pkgName, mUserId);
            if (appEntry == null || appEntry.info == null || !AppUtils.isInstant(appEntry.info)) {
                LOG.d("Not a user visible or instant app, skipping " + pkgName);
                return false;
            }
        }
        return true;
    }

    /**
     * Callback that is called once the recently used apps have been fetched.
     */
    public interface RecentAppStatsListener {
        /**
         * Called when the recently used apps are successfully loaded
         */
        void onRecentAppStatsLoaded(List<UsageStats> recentAppStats);
    }
}
