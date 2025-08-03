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

package com.android.adservices.common;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.modules.utils.build.SdkLevel;

import java.util.Objects;

public class DevContextUtils {

    private static final boolean IS_DEBUGGABLE_BUILD = computeIsDebuggableBuild();

    /** Method to check if Dev Options are enabled based on DevContext. */
    public static boolean isDevOptionsEnabled(Context context, String logTag) {
        String callingAppPackage = getAppPackageNameForUid(context, Process.myUid(), logTag);
        boolean isDebuggable = isDebuggable(context, callingAppPackage, logTag);
        boolean isDeveloperMode = isDeveloperMode(context);
        Log.d(
                logTag,
                String.format("Debuggable: %b\n", isDebuggable)
                        + String.format("Developer options on: %b", isDeveloperMode));
        return isDebuggable && isDeveloperMode;
    }

    /** Returns true if developer options are enabled. */
    public static boolean isDeveloperMode(@NonNull Context context) {
        return isDebuggableBuild()
                || Settings.Global.getInt(
                                context.getContentResolver(),
                                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                                0)
                        != 0;
    }

    /**
     * Returns true if the callingAppPackage is debuggable and false if it is not or if {@code
     * callingAppPackage} is null.
     *
     * @param callingAppPackage the calling app package
     */
    public static boolean isDebuggable(
            @NonNull Context context, String callingAppPackage, @NonNull String logTag) {
        if (Objects.isNull(callingAppPackage)) {
            return false;
        }
        try {
            ApplicationInfo applicationInfo = getApplicationInfo(context, callingAppPackage, 0);
            return (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(
                    logTag,
                    String.format(
                            "Unable to retrieve application info for app with resolved package "
                                    + "name '%s', considering not debuggable for safety.",
                            callingAppPackage));
            return false;
        }
    }

    /**
     * Invoke the {@code Build.isDebuggable} API on Android S or higher, and re-implement the same
     * functionality on Android R
     *
     * @return {@code true} if the device is running a debuggable build such as "userdebug" or
     *     "eng".
     */
    public static boolean isDebuggableBuild() {
        return IS_DEBUGGABLE_BUILD;
    }

    /**
     * Invokes the appropriate overload of {@code getApplicationInfo} on {@link PackageManager}
     * depending on the SDK version.
     *
     * <p>{@code ApplicationInfoFlags.of()} actually takes a {@code long} as input whereas the
     * earlier overload takes an {@code int}. For backward-compatibility, we're limited to the
     * {@code int} range, so using {@code int} as a parameter to this method.
     *
     * @param context the context
     * @param flags the flags to be used for querying package manager
     * @param packageName the name of the package for which the ApplicationInfo should be retrieved
     * @return the application info returned from the query to {@link PackageManager}
     */
    @NonNull
    public static ApplicationInfo getApplicationInfo(
            @NonNull Context context, @NonNull String packageName, int flags)
            throws PackageManager.NameNotFoundException {
        Objects.requireNonNull(context);
        Objects.requireNonNull(packageName);
        return SdkLevel.isAtLeastT()
                ? context.getPackageManager()
                        .getApplicationInfo(
                                packageName, PackageManager.ApplicationInfoFlags.of(flags))
                : context.getPackageManager().getApplicationInfo(packageName, flags);
    }

    /**
     * @param appUid The UUID of the app, for example the ID of the app calling.
     * @return the AppID (package name) for the application associated to the given UID In the rare
     *     case that there are multiple apps associated to the same UID the first one returned by
     *     the OS is returned.
     * @throws IllegalArgumentException if the system cannot find any app package for the given UID.
     */
    public static String getAppPackageNameForUid(Context context, int appUid, String logTag)
            throws IllegalArgumentException {
        // We could have more than one package name for the same UID if the UID is shared by
        // different apps. This is a rare case and we are going to use the ID of the first one.
        // See https://yaqs.corp.google.com/eng/q/4727253374861312#a5649050225344512
        String[] possibleAppPackages = context.getPackageManager().getPackagesForUid(appUid);
        if (possibleAppPackages == null || possibleAppPackages.length == 0) {
            throw new IllegalArgumentException(
                    "Unable to retrieve a package name for caller UID " + appUid);
        }
        if (possibleAppPackages.length > 1) {
            Log.d(
                    logTag,
                    String.format(
                            "More than one package name available for UID %d, returning package "
                                    + "name %s",
                            possibleAppPackages.length, possibleAppPackages[0]));
        }
        return possibleAppPackages[0];
    }

    private static boolean computeIsDebuggableBuild() {
        if (SdkLevel.isAtLeastS()) {
            return Build.isDebuggable();
        }

        // Build.isDebuggable was added in S; duplicate that functionality for R.
        return SystemProperties.getInt("ro.debuggable", 0) == 1;
    }
}
