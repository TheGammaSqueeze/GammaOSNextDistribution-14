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

package com.android.adservices.service.common.compat;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import com.android.adservices.LogUtil;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Utility class for compatibility of PackageManager APIs with Android S and earlier. */
public final class PackageManagerCompatUtils {

    private PackageManagerCompatUtils() {
        // Prevent instantiation
    }

    // This list is the same as the list declared in the AdExtServicesManifest, where the
    // activities are disabled so that there are no dups on T+ devices.
    // TODO(b/263904312): Remove after max_sdk_version is implemented.
    // TODO(b/272737642) scan activities instead of hardcode
    public static final ImmutableList<String> CONSENT_ACTIVITIES_CLASSES =
            ImmutableList.copyOf(
                    Arrays.asList(
                            "com.android.adservices.ui.settings.activities."
                                    + "AdServicesSettingsMainActivity",
                            "com.android.adservices.ui.settings.activities.TopicsActivity",
                            "com.android.adservices.ui.settings.activities.BlockedTopicsActivity",
                            "com.android.adservices.ui.settings.activities.AppsActivity",
                            "com.android.adservices.ui.settings.activities.BlockedAppsActivity",
                            "com.android.adservices.ui.settings.activities.MeasurementActivity",
                            "com.android.adservices.ui.notifications.ConsentNotificationActivity"));

    // This list is the same as the list declared in the AdExtServicesManifest, where the
    // services with intent filters need to be disabled so that there are no dups on T+ devices.
    // TODO(b/263904312): Remove after max_sdk_version is implemented.
    // TODO(b/272737642) scan services instead of hardcode
    public static final ImmutableList<String> SERVICE_CLASSES =
            ImmutableList.copyOf(
                    Arrays.asList(
                            "com.android.adservices.adselection.AdSelectionService",
                            "com.android.adservices.customaudience.CustomAudienceService",
                            "com.android.adservices.topics.TopicsService",
                            "com.android.adservices.adid.AdIdService",
                            "com.android.adservices.appsetid.AppSetIdService",
                            "com.android.adservices.measurement.MeasurementService",
                            "com.android.adservices.common.AdServicesCommonService"));

    /**
     * Invokes the appropriate overload of {@code getInstalledPackages} on {@link PackageManager}
     * depending on the SDK version.
     *
     * <p>{@code PackageInfoFlags.of()} actually takes a {@code long} as input whereas the earlier
     * overload takes an {@code int}. For backward-compatibility, we're limited to the {@code int}
     * range, so using {@code int} as a parameter to this method.
     *
     * @param packageManager the package manager instance to query
     * @param flags the flags to be used for querying package manager
     * @return the list of installed packages returned from the query to {@link PackageManager}
     */
    @NonNull
    public static List<PackageInfo> getInstalledPackages(
            @NonNull PackageManager packageManager, int flags) {
        Objects.requireNonNull(packageManager);
        return SdkLevel.isAtLeastT()
                ? packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags))
                : packageManager.getInstalledPackages(flags);
    }

    /**
     * Invokes the appropriate overload of {@code getInstalledApplications} on {@link
     * PackageManager} depending on the SDK version.
     *
     * <p>{@code ApplicationInfoFlags.of()} actually takes a {@code long} as input whereas the
     * earlier overload takes an {@code int}. For backward-compatibility, we're limited to the
     * {@code int} range, so using {@code int} as a parameter to this method.
     *
     * @param packageManager the package manager instance to query
     * @param flags the flags to be used for querying package manager
     * @return the list of installed applications returned from the query to {@link PackageManager}
     */
    @NonNull
    public static List<ApplicationInfo> getInstalledApplications(
            @NonNull PackageManager packageManager, int flags) {
        Objects.requireNonNull(packageManager);
        return SdkLevel.isAtLeastT()
                ? packageManager.getInstalledApplications(
                        PackageManager.ApplicationInfoFlags.of(flags))
                : packageManager.getInstalledApplications(flags);
    }

    /**
     * Invokes the appropriate overload of {@code getApplicationInfo} on {@link PackageManager}
     * depending on the SDK version.
     *
     * <p>{@code ApplicationInfoFlags.of()} actually takes a {@code long} as input whereas the
     * earlier overload takes an {@code int}. For backward-compatibility, we're limited to the
     * {@code int} range, so using {@code int} as a parameter to this method.
     *
     * @param packageManager the package manager instance to query
     * @param flags the flags to be used for querying package manager
     * @param packageName the name of the package for which the ApplicationInfo should be retrieved
     * @return the application info returned from the query to {@link PackageManager}
     */
    @NonNull
    public static ApplicationInfo getApplicationInfo(
            @NonNull PackageManager packageManager, @NonNull String packageName, int flags)
            throws PackageManager.NameNotFoundException {
        Objects.requireNonNull(packageManager);
        Objects.requireNonNull(packageName);
        return SdkLevel.isAtLeastT()
                ? packageManager.getApplicationInfo(
                        packageName, PackageManager.ApplicationInfoFlags.of(flags))
                : packageManager.getApplicationInfo(packageName, flags);
    }

    /**
     * Invokes the appropriate overload of {@code getPackageUid} on {@link PackageManager} depending
     * on the SDK version.
     *
     * <p>{@code PackageInfoFlags.of()} actually takes a {@code long} as input whereas the earlier
     * overload takes an {@code int}. For backward-compatibility, we're limited to the {@code int}
     * range, so using {@code int} as a parameter to this method.
     *
     * @param packageManager the packageManager instance to query
     * @param packageName the name of the package for which the uid needs to be returned
     * @param flags the flags to be used for querying the packageManager
     * @return the uid of the package with the specified name
     * @throws PackageManager.NameNotFoundException if the package was not found
     */
    public static int getPackageUid(
            @NonNull PackageManager packageManager, @NonNull String packageName, int flags)
            throws PackageManager.NameNotFoundException {
        Objects.requireNonNull(packageManager);
        Objects.requireNonNull(packageName);
        return SdkLevel.isAtLeastT()
                ? packageManager.getPackageUid(
                        packageName, PackageManager.PackageInfoFlags.of(flags))
                : packageManager.getPackageUid(packageName, flags);
    }

    /**
     * Activities for user consent and control are disabled by default. Check whether the activities
     * are enabled
     *
     * @param context the context
     * @return true if AdServices activities are enabled, otherwise false
     */
    @NonNull
    public static boolean isAdServicesActivityEnabled(@NonNull Context context) {
        Objects.requireNonNull(context);
        PackageManager packageManager = context.getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            for (String activity : CONSENT_ACTIVITIES_CLASSES) {
                int componentEnabledState =
                        packageManager.getComponentEnabledSetting(
                                new ComponentName(packageInfo.packageName, activity));
                if (componentEnabledState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    return false;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e("Error when checking if activities are enabled: " + e.getMessage());
            return false;
        }
        return true;
    }
}
