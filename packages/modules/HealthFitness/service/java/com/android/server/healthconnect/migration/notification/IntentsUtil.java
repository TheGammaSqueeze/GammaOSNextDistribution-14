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

package com.android.server.healthconnect.migration.notification;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.util.Log;

/** Utility class used to create and resolve intents. */
final class IntentsUtil {
    private static final String LOG_TAG = "AppStoreUtil";

    private IntentsUtil() {}

    /**
     * Returns the package name of the app that we consider to be the user-visible 'installer' of
     * given packageName, if one is available.
     */
    @Nullable
    private static String getInstallerPackageName(
            @NonNull Context context, @NonNull String packageName) {
        String installerPackageName;
        try {
            InstallSourceInfo source =
                    context.getPackageManager().getInstallSourceInfo(packageName);
            // By default, use the installing package name.
            installerPackageName = source.getInstallingPackageName();
            // Use the recorded originating package name only if the initiating package is a system
            // app (eg. Package Installer). The originating package is not verified by the platform,
            // so we choose to ignore this when supplied by a non-system app.
            String originatingPackageName = source.getOriginatingPackageName();
            String initiatingPackageName = source.getInitiatingPackageName();
            if (originatingPackageName != null && initiatingPackageName != null) {
                ApplicationInfo ai =
                        context.getPackageManager().getApplicationInfo(initiatingPackageName, 0);
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    installerPackageName = originatingPackageName;
                }
            }
        } catch (NameNotFoundException e) {
            Log.e(LOG_TAG, "Exception while retrieving the package installer of " + packageName, e);
            installerPackageName = null;
        }
        return installerPackageName;
    }

    /** Returns a link to the installer app store for a given package name. */
    @Nullable
    private static Intent createAppStoreIntent(
            @NonNull Context context,
            @NonNull String installerPackageName,
            @NonNull String packageName) {
        Intent intent = new Intent(Intent.ACTION_SHOW_APP_INFO).setPackage(installerPackageName);
        ResolveInfo result = context.getPackageManager().resolveActivity(intent, 0);
        if (result != null) {
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
            return intent;
        }
        return null;
    }

    /** Convenience method that looks up the installerPackageName for you. */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Nullable
    public static Intent createAppStoreIntent(
            @NonNull Context context, @NonNull String packageName) {
        String installerPackageName = getInstallerPackageName(context, packageName);
        return createAppStoreIntent(context, installerPackageName, packageName);
    }
}
