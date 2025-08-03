/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.role.controller.model;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.role.controller.compat.AppOpsManagerCompat;
import com.android.role.controller.util.ArrayUtils;
import com.android.role.controller.util.PackageUtils;

/**
 * App op permissions to be granted or revoke by a {@link Role}.
 */
public class AppOpPermissions {

    private AppOpPermissions() {}

    /**
     * Grant the app op of an app op permission to an application.
     *
     * @param packageName the package name of the application
     * @param appOpPermission the name of the app op permission
     * @param overrideNonDefaultMode whether to override the app opp mode if it isn't in the default
     *        mode
     * @param user the user of the application
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether any app op mode has changed
     */
    public static boolean grantAsUser(@NonNull String packageName, @NonNull String appOpPermission,
            boolean overrideNonDefaultMode, @NonNull UserHandle user, @NonNull Context context) {
        PackageInfo packageInfo = PackageUtils.getPackageInfoAsUser(packageName,
                PackageManager.GET_PERMISSIONS, user, context);
        if (packageInfo == null) {
            return false;
        }
        if (!ArrayUtils.contains(packageInfo.requestedPermissions, appOpPermission)) {
            return false;
        }
        String appOp = AppOpsManagerCompat.permissionToOp(appOpPermission);
        if (!overrideNonDefaultMode) {
            Integer currentMode = Permissions.getAppOpModeAsUser(packageName, appOp, user, context);
            if (currentMode != null && currentMode != Permissions.getDefaultAppOpMode(appOp)) {
                return false;
            }
        }
        boolean changed = setAppOpModeAsUser(packageName, appOp, AppOpsManager.MODE_ALLOWED, user,
                context);
        if (changed) {
            Permissions.setPermissionGrantedByRoleAsUser(packageName, appOpPermission, true,
                    user, context);
        }
        return changed;
    }

    /**
     * Revoke the app op of an app op permission from an application.
     *
     * @param packageName the package name of the application
     * @param appOpPermission the name of the app op permission
     * @param user the user of the application
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether any app op mode has changed
     */
    public static boolean revokeAsUser(@NonNull String packageName, @NonNull String appOpPermission,
            @NonNull UserHandle user, @NonNull Context context) {
        if (!Permissions.isPermissionGrantedByRoleAsUser(packageName, appOpPermission, user,
                context)) {
            return false;
        }
        String appOp = AppOpsManager.permissionToOp(appOpPermission);
        int defaultMode = Permissions.getDefaultAppOpMode(appOp);
        boolean changed = setAppOpModeAsUser(packageName, appOp, defaultMode, user, context);
        Permissions.setPermissionGrantedByRoleAsUser(packageName, appOpPermission, false,
                user, context);
        return changed;
    }

    private static boolean setAppOpModeAsUser(@NonNull String packageName, @NonNull String appOp,
            int mode, @NonNull UserHandle user, @NonNull Context context) {
        switch (appOp) {
            case AppOpsManager.OPSTR_ACCESS_NOTIFICATIONS:
            case AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW:
            case AppOpsManager.OPSTR_WRITE_SETTINGS:
            case AppOpsManager.OPSTR_GET_USAGE_STATS:
            case AppOpsManager.OPSTR_REQUEST_INSTALL_PACKAGES:
            case AppOpsManager.OPSTR_START_FOREGROUND:
            // This isn't an API but we are deprecating it soon anyway.
            //case AppOpsManager.OPSTR_SMS_FINANCIAL_TRANSACTIONS:
            case AppOpsManager.OPSTR_MANAGE_IPSEC_TUNNELS:
            case AppOpsManager.OPSTR_INSTANT_APP_START_FOREGROUND:
            case AppOpsManager.OPSTR_LOADER_USAGE_STATS:
                return Permissions.setAppOpPackageModeAsUser(packageName, appOp, mode, user,
                        context);
            case AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // We fixed OP_INTERACT_ACROSS_PROFILES to use UID mode on S and backported it
                    // to R, but still, we might have an out-of-date platform or an upgraded
                    // platform with old state.
                    boolean changed = false;
                    changed |= Permissions.setAppOpUidModeAsUser(packageName, appOp, mode, user,
                            context);
                    changed |= Permissions.setAppOpPackageModeAsUser(packageName, appOp,
                            Permissions.getDefaultAppOpMode(appOp), user, context);
                    return changed;
                } else {
                    return Permissions.setAppOpPackageModeAsUser(packageName, appOp, mode, user,
                            context);
                }
            default:
                return Permissions.setAppOpUidModeAsUser(packageName, appOp, mode, user, context);
        }
    }
}
