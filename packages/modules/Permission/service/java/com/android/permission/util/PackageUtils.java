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

package com.android.permission.util;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Build;
import android.os.SystemProperties;
import android.os.UserHandle;

import java.util.List;

/** Utility class for dealing with packages. */
public final class PackageUtils {
    private PackageUtils() {}

    /**
     * Returns {@code true} if the calling package is able to query for details about the package.
     *
     * @see PackageManager#canPackageQuery
     */
    public static boolean canCallingOrSelfPackageQuery(
            @NonNull String packageName, @UserIdInt int userId, @NonNull Context context) {
        final Context userContext = context.createContextAsUser(UserHandle.of(userId), 0);
        final PackageManager userPackageManager = userContext.getPackageManager();
        try {
            userPackageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    /**
     * Returns the activities {@link ResolveInfo} that match the given {@link Intent} for the given
     * {@code flags} and {@code userId}.
     */
    @NonNull
    public static List<ResolveInfo> queryUnfilteredIntentActivitiesAsUser(
            @NonNull Intent intent, int flags, @UserIdInt int userId, @NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        // This call requires the INTERACT_ACROSS_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return packageManager.queryIntentActivitiesAsUser(intent, flags, UserHandle.of(userId));
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /**
     * Returns the broadcasts {@link ResolveInfo} that match the given {@link Intent} for the given
     * {@code flags} and {@code userId}.
     */
    @NonNull
    public static List<ResolveInfo> queryUnfilteredBroadcastReceiversAsUser(
            @NonNull Intent intent, int flags, @UserIdInt int userId, @NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        // This call requires the INTERACT_ACROSS_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return packageManager.queryBroadcastReceiversAsUser(
                    intent, flags, UserHandle.of(userId));
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /** Default is to not use fs-verity since it depends on kernel support. */
    private static final int FSVERITY_DISABLED = 0;

    /** Standard fs-verity. */
    private static final int FSVERITY_ENABLED = 2;

    /** Returns true if standard APK Verity is enabled. */
    public static boolean isApkVerityEnabled() {
        return Build.VERSION.DEVICE_INITIAL_SDK_INT >= Build.VERSION_CODES.R
                || SystemProperties.getInt("ro.apk_verity.mode", FSVERITY_DISABLED)
                        == FSVERITY_ENABLED;
    }
}
