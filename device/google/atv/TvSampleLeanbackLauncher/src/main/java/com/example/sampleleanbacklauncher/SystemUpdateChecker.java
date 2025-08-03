/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.example.sampleleanbacklauncher;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.preference.PreferenceManager;
import android.provider.Settings;

import java.util.List;

/**
 * This is an optional component whose job is to ensure that the system is up-to-date
 * at boot time.  It delegates this operation to a separate application on the device.
 */
@TargetApi(24)
public class SystemUpdateChecker {

    /**
     * The application that handles the system update check must be a system application that
     * has an activity found by this action.
     */
    private static final String ACTION_TV_BOOT_COMPLETED =
            "android.intent.action.TV_BOOT_COMPLETED";

    private static final String PREF_SYSTEM_CHECKED_BOOT_COUNT = "system.checked.boot.count";

    private final Context mContext;

    public SystemUpdateChecker(Context context) {
        mContext = context;
    }

    Intent getSystemUpdateCheckerIntent() {
        // See if we have already performed a system update check since the latest boot
        // of the device
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        int checkedBootCount = prefs.getInt(PREF_SYSTEM_CHECKED_BOOT_COUNT, -1);
        if (checkedBootCount >= getBootCount()) {
            return null;
        }

        PackageManager pm = mContext.getPackageManager();
        List<ResolveInfo> infoList = pm.queryIntentActivities(
                new Intent(ACTION_TV_BOOT_COMPLETED), 0);
        if (infoList != null) {
            for (ResolveInfo resolveInfo : infoList) {
                if (isSystemApp(resolveInfo)) {
                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName(resolveInfo.activityInfo.packageName,
                            resolveInfo.activityInfo.name));
                    return intent;
                }
            }
        }

        onSystemUpdateCheckerComplete();
        return null;
    }

    void onSystemUpdateCheckerComplete() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        prefs.edit().putInt(PREF_SYSTEM_CHECKED_BOOT_COUNT, getBootCount()).apply();
    }

    private int getBootCount() {
        // This API is available on Android N+
        return Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BOOT_COUNT, 0);
    }

    private static boolean isSystemApp(ResolveInfo info) {
        return (info.activityInfo != null && info.activityInfo.applicationInfo != null &&
                (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
    }
}
