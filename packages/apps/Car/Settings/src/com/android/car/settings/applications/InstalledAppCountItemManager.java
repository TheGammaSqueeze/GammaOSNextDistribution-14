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

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.settingslib.utils.ThreadUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Class used to count the number of non-system apps. Largely derived from
 * {@link com.android.settings.applications.InstalledAppCounter}.
 */
public class InstalledAppCountItemManager {

    private Context mContext;
    private final List<InstalledAppCountListener> mInstalledAppCountListeners;

    public InstalledAppCountItemManager(Context context) {
        mContext = context;
        mInstalledAppCountListeners = new ArrayList<>();
    }

    /**
     * Registers a listener that will be notified once the data is loaded.
     */
    public void addListener(@NonNull InstalledAppCountListener listener) {
        mInstalledAppCountListeners.add(listener);
    }

    /**
     * Starts fetching installed apps and counting the non-system apps
     */
    public void startLoading() {
        ThreadUtils.postOnBackgroundThread(() -> {
            List<ApplicationInfo> appList = mContext.getPackageManager()
                    .getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS
                            | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS);

            int appCount = 0;
            for (ApplicationInfo applicationInfo : appList) {
                if (shouldCountApp(applicationInfo)) {
                    appCount++;
                }
            }
            int finalAppCount = appCount;
            for (InstalledAppCountListener listener : mInstalledAppCountListeners) {
                ThreadUtils.postOnMainThread(() -> listener
                        .onInstalledAppCountLoaded(finalAppCount));
            }
        });
    }

    @VisibleForTesting
    boolean shouldCountApp(ApplicationInfo applicationInfo) {
        if ((applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
            return true;
        }
        if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
            return true;
        }
        int userId = UserHandle.getUserId(applicationInfo.uid);
        Intent launchIntent = new Intent(Intent.ACTION_MAIN, null)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(applicationInfo.packageName);
        List<ResolveInfo> intents = mContext.getPackageManager().queryIntentActivitiesAsUser(
                launchIntent,
                PackageManager.MATCH_DISABLED_COMPONENTS
                        | PackageManager.MATCH_DIRECT_BOOT_AWARE
                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                userId);
        return intents != null && !intents.isEmpty();
    }

    /**
     * Callback that is called once the number of installed applications is counted.
     */
    public interface InstalledAppCountListener {
        /**
         * Called when the apps are successfully loaded from PackageManager and non-system apps are
         * counted.
         */
        void onInstalledAppCountLoaded(int appCount);
    }
}
