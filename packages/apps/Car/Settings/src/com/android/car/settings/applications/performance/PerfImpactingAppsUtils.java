/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.settings.applications.performance;

import static android.car.settings.CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE;

import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.PackageKillableState;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Utility functions for use in Performance-impacting apps settings and Prioritize app settings.
 */
public final class PerfImpactingAppsUtils {

    private static final String PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR = ";";

    private PerfImpactingAppsUtils() {}

    /**
     * Returns the {@link android.car.watchdog.PackageKillableState.KillableState} for the package
     * and user provided.
     */
    public static int getKillableState(String packageName, UserHandle userHandle,
            CarWatchdogManager manager) {
        return Objects.requireNonNull(manager)
                .getPackageKillableStatesAsUser(userHandle).stream()
                .filter(pks -> pks.getPackageName().equals(packageName))
                .findFirst().map(PackageKillableState::getKillableState).orElse(-1);
    }

    /**
     * Shows confirmation dialog when user chooses to prioritize an app disabled because of resource
     * overuse.
     */
    public static void showPrioritizeAppConfirmationDialog(Context context,
            FragmentController fragmentController,
            ConfirmationDialogFragment.ConfirmListener listener, String dialogTag) {
        ConfirmationDialogFragment dialogFragment =
                new ConfirmationDialogFragment.Builder(context)
                        .setTitle(R.string.prioritize_app_performance_dialog_title)
                        .setMessage(R.string.prioritize_app_performance_dialog_text)
                        .setPositiveButton(R.string.prioritize_app_performance_dialog_action_on,
                                listener)
                        .setNegativeButton(R.string.prioritize_app_performance_dialog_action_off,
                                /* rejectListener= */ null)
                        .build();
        fragmentController.showDialog(dialogFragment, dialogTag);
    }

    /**
     * Returns the set of package names disabled due to resource overuse.
     */
    public static Set<String> getDisabledPackages(Context context) {
        ContentResolver contentResolverForUser = context.createContextAsUser(
                        UserHandle.getUserHandleForUid(Process.myUid()), /* flags= */ 0)
                .getContentResolver();
        return extractPackages(Settings.Secure.getString(contentResolverForUser,
                        KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE));
    }

    /**
     * Returns a list of application infos disabled due to resource overuse.
     */
    public static List<ApplicationInfo> getDisabledAppInfos(Context context) {
        Set<String> disabledPackageNames = getDisabledPackages(context);
        if (disabledPackageNames.isEmpty()) {
            return new ArrayList<>(0);
        }
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> allPackages = packageManager.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_RESOLVED_FILTER
                        | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS));
        List<ApplicationInfo> disabledAppInfos = new ArrayList<>(allPackages.size());
        for (int idx = 0; idx < allPackages.size(); idx++) {
            ApplicationInfo applicationInfo = allPackages.get(idx).activityInfo.applicationInfo;
            if (disabledPackageNames.contains(applicationInfo.packageName)) {
                disabledAppInfos.add(applicationInfo);
                // Match only the first occurrence of a package.
                // |PackageManager#queryIntentActivities| can return duplicate packages.
                disabledPackageNames.remove(applicationInfo.packageName);
            }
        }
        return disabledAppInfos;
    }

    private static ArraySet<String> extractPackages(String settingsString) {
        return TextUtils.isEmpty(settingsString) ? new ArraySet<>()
                : new ArraySet<>(Arrays.asList(settingsString.split(
                        PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR)));
    }
}
