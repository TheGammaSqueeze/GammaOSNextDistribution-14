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

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.format.DateUtils;
import android.util.IconDrawableFactory;
import android.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Helper class for the 'Recently Accessed By' sections.
 */
public class RecentlyAccessedByUtils {

    // Get the last 24 hours of app ops access information.
    private static final long RECENT_TIME_INTERVAL_MILLIS = DateUtils.DAY_IN_MILLIS;
    private static final String ANDROID_SYSTEM_PACKAGE_NAME = "android";

    private static final String TAG = RecentlyAccessedByUtils.class.getSimpleName();
    private static final boolean DEBUG = false;

    /**
     * Gets list of apps that recently used the given AppOps sorted by the most recent access.
     */
    public static List<App> getAppList(Context context, int[] appOps) {
        PackageManager packageManager = context.getPackageManager();
        IconDrawableFactory iconDrawableFactory = IconDrawableFactory.newInstance(context);
        long currentTime = System.currentTimeMillis();

        List<AppOpsManager.PackageOps> packageOps = context.getSystemService(
                AppOpsManager.class).getPackagesForOps(appOps);
        int packageOpsCount = packageOps != null ? packageOps.size() : 0;
        ArrayList<App> recentApps = new ArrayList<>(packageOpsCount);

        List<UserHandle> currentUserProfiles = context.getSystemService(
                UserManager.class).getUserProfiles();

        for (int i = 0; i < packageOpsCount; i++) {
            AppOpsManager.PackageOps ops = packageOps.get(i);

            // Don't include app ops usages from the system or other users and restricted profiles
            boolean isSystem = UserHandle.isSameApp(ops.getUid(), Process.SYSTEM_UID)
                    && ANDROID_SYSTEM_PACKAGE_NAME.equals(ops.getPackageName());
            boolean isAppOpByCurrentUser = currentUserProfiles.contains(
                    UserHandle.getUserHandleForUid(ops.getUid()));
            if (isSystem || !isAppOpByCurrentUser) {
                continue;
            }

            App recentApp = getRecentRequestFromOps(packageManager, currentTime, ops,
                    iconDrawableFactory);
            if (recentApp != null) {
                recentApps.add(recentApp);
            }
        }

        // Sort by most recent access.
        recentApps.sort(Comparator.comparingLong(app -> ((App) app).mLastAccess).reversed());

        return recentApps;
    }

    private static App getRecentRequestFromOps(PackageManager packageManager, long currentTime,
            AppOpsManager.PackageOps ops, IconDrawableFactory iconDrawableFactory) {
        String packageName = ops.getPackageName();
        List<AppOpsManager.OpEntry> entries = ops.getOps();
        long recentAccessCutoffTime = currentTime - RECENT_TIME_INTERVAL_MILLIS;

        if (DEBUG) Log.v(TAG, "package: " + packageName + ", entries: " + entries.size());
        long mostRecentAccessEnd = 0;
        for (AppOpsManager.OpEntry entry : entries) {
            long accessEnd = entry.getLastAccessTime(AppOpsManager.OP_FLAGS_ALL)
                    + entry.getLastDuration(AppOpsManager.OP_FLAGS_ALL);

            if (entry.isRunning()) {
                mostRecentAccessEnd = currentTime;
            } else if (accessEnd >= recentAccessCutoffTime && accessEnd >= mostRecentAccessEnd) {
                mostRecentAccessEnd = accessEnd;
            }
        }

        if (mostRecentAccessEnd <= 0) {
            return null;
        }

        ApplicationInfo appInfo;
        try {
            appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "package name could not be found for " + packageName);
            return null;
        }

        Drawable icon = iconDrawableFactory.getBadgedIcon(appInfo);
        CharSequence appLabel = packageManager.getApplicationLabel(appInfo);
        return new App(packageName, icon, appLabel, mostRecentAccessEnd);
    }

    public static class App {
        public final String mPackageName;
        public final Drawable mIcon;
        public final CharSequence mLabel;
        public final long mLastAccess;

        private App(String packageName, Drawable icon, CharSequence label, long lastAccess) {
            this.mPackageName = packageName;
            this.mIcon = icon;
            this.mLabel = label;
            this.mLastAccess = lastAccess;
        }
    }
}
