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

package com.android.server.healthconnect.migration;

import android.Manifest;
import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.health.connect.Constants;
import android.health.connect.HealthConnectManager;
import android.util.Slog;

import libcore.util.HexEncoding;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** @hide */
public class MigrationUtils {
    private static final String TAG = "HealthConnectMigrationUtils";

    /**
     * Filters and returns the package names of applications which hold permission {@link
     * android.Manifest.permission#MIGRATE_HEALTH_CONNECT_DATA}.
     *
     * @return List of filtered app package names which hold the specified permission
     */
    @NonNull
    public static List<String> filterPermissions(@NonNull Context context) {
        if (android.health.connect.Constants.DEBUG) {
            Slog.d(TAG, "Calling filterPermissions()");
        }

        String[] permissions = new String[] {Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA};

        List<PackageInfo> packageInfos =
                context.getPackageManager()
                        .getPackagesHoldingPermissions(
                                permissions, PackageManager.PackageInfoFlags.of(0));

        List<String> permissionFilteredPackages =
                packageInfos.stream().map(info -> info.packageName).collect(Collectors.toList());

        if (android.health.connect.Constants.DEBUG) {
            Slog.d(TAG, "permissionFilteredPackages : " + permissionFilteredPackages);
        }
        return permissionFilteredPackages;
    }

    /**
     * Filters and returns the package names of applications which handle intent {@link
     * android.health.connect.HealthConnectManager#ACTION_SHOW_MIGRATION_INFO}.
     *
     * @param permissionFilteredPackages List of app package names holding permission {@link
     *     android.Manifest.permission#MIGRATE_HEALTH_CONNECT_DATA}
     * @return List of filtered app package names which handle the specified intent action
     */
    @NonNull
    public static List<String> filterIntent(
            @NonNull Context context, @NonNull List<String> permissionFilteredPackages) {
        return filterIntent(context, permissionFilteredPackages, PackageManager.MATCH_ALL);
    }

    /**
     * Filters and returns the package names of applications which handle intent {@link
     * android.health.connect.HealthConnectManager#ACTION_SHOW_MIGRATION_INFO}.
     *
     * @param permissionFilteredPackages List of app package names holding permission {@link
     *     android.Manifest.permission#MIGRATE_HEALTH_CONNECT_DATA}
     * @param flags Additional option flags to modify the data returned.
     * @return List of filtered app package names which handle the specified intent action
     */
    @NonNull
    public static List<String> filterIntent(
            @NonNull Context context, @NonNull List<String> permissionFilteredPackages, int flags) {
        if (android.health.connect.Constants.DEBUG) {
            Slog.d(TAG, "Calling filterIntents()");
        }

        List<String> filteredPackages = new ArrayList<String>(permissionFilteredPackages.size());

        for (String packageName : permissionFilteredPackages) {

            if (android.health.connect.Constants.DEBUG) {
                Slog.d(TAG, "Checking intent for package : " + packageName);
            }

            Intent intentToCheck =
                    new Intent(HealthConnectManager.ACTION_SHOW_MIGRATION_INFO)
                            .setPackage(packageName);

            ResolveInfo resolveResult =
                    context.getPackageManager()
                            .resolveActivity(
                                    intentToCheck, PackageManager.ResolveInfoFlags.of(flags));

            if (resolveResult != null) {
                filteredPackages.add(packageName);
            }
        }
        if (Constants.DEBUG) {
            Slog.d(TAG, "filteredPackages : " + filteredPackages);
        }
        return filteredPackages;
    }

    /** Computes the SHA256 digest of the input data. */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public static String computeSha256DigestBytes(@NonNull byte[] data) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA256");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        messageDigest.update(data);

        return HexEncoding.encodeToString(messageDigest.digest(), /* uppercase= */ true);
    }

    /** Checks if the package is stub by checking if its installer source is not set. */
    public static boolean isPackageStub(Context context, String packageName) {
        try {
            return context.getPackageManager()
                            .getInstallSourceInfo(packageName)
                            .getInstallingPackageName()
                    == null;
        } catch (PackageManager.NameNotFoundException e) {
            Slog.w(TAG, "Package not found " + packageName);
        }
        return false;
    }
}
