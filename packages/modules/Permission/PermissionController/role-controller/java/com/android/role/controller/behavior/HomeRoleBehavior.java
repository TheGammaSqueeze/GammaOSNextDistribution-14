/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.role.controller.behavior;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.modules.utils.build.SdkLevel;
import com.android.role.controller.model.AppOpPermissions;
import com.android.role.controller.model.Permissions;
import com.android.role.controller.model.Role;
import com.android.role.controller.model.RoleBehavior;
import com.android.role.controller.model.VisibilityMixin;
import com.android.role.controller.util.UserUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Class for behavior of the home role.
 *
 * @see com.android.settings.applications.DefaultAppSettings
 * @see com.android.settings.applications.defaultapps.DefaultHomePreferenceController
 * @see com.android.settings.applications.defaultapps.DefaultHomePicker
 */
public class HomeRoleBehavior implements RoleBehavior {

    private static final List<String> AUTOMOTIVE_PERMISSIONS = Arrays.asList(
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.WRITE_CALL_LOG,
            android.Manifest.permission.READ_CONTACTS);

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static final List<String> WEAR_PERMISSIONS_T = Arrays.asList(
            android.Manifest.permission.POST_NOTIFICATIONS,
            android.Manifest.permission.SYSTEM_APPLICATION_OVERLAY);

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private static final List<String> WEAR_PERMISSIONS_V = Arrays.asList(
            android.Manifest.permission.ALWAYS_UPDATE_WALLPAPER);

    private static final List<String> WEAR_APP_OP_PERMISSIONS = Arrays.asList(
            android.Manifest.permission.SYSTEM_ALERT_WINDOW);

    @Override
    public boolean isAvailableAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return !UserUtils.isProfile(user, context);
    }

    /**
     * @see com.android.server.pm.PackageManagerService#getDefaultHomeActivity(int)
     */
    @Nullable
    @Override
    public String getFallbackHolderAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        Context userContext = UserUtils.getUserContext(context, user);
        PackageManager userPackageManager = userContext.getPackageManager();
        Intent intent = role.getRequiredComponents().get(0).getIntentFilterData().createIntent();
        List<ResolveInfo> resolveInfos = userPackageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.MATCH_DIRECT_BOOT_AWARE
                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);

        String packageName = null;
        int priority = Integer.MIN_VALUE;
        int resolveInfosSize = resolveInfos.size();
        for (int i = 0; i < resolveInfosSize; i++) {
            ResolveInfo resolveInfo = resolveInfos.get(i);

            // Leave the fallback to PackageManagerService if there is only the fallback home in
            // Settings, because if we fallback to it here, we cannot fallback to a normal home
            // later, and user cannot see the fallback home in the UI anyway.
            if (isSettingsApplicationAsUser(resolveInfo.activityInfo.applicationInfo, user,
                    context)) {
                continue;
            }
            if (resolveInfo.priority > priority) {
                packageName = resolveInfo.activityInfo.packageName;
                priority = resolveInfo.priority;
            } else if (resolveInfo.priority == priority) {
                packageName = null;
            }
        }
        return packageName;
    }

    /**
     * Check if the application is a settings application
     */
    private static boolean isSettingsApplicationAsUser(@NonNull ApplicationInfo applicationInfo,
            @NonNull UserHandle user, @NonNull Context context) {
        Context userContext = UserUtils.getUserContext(context, user);
        PackageManager userPackageManager = userContext.getPackageManager();
        ResolveInfo resolveInfo = userPackageManager.resolveActivity(
                new Intent(Settings.ACTION_SETTINGS), PackageManager.MATCH_DEFAULT_ONLY
                | PackageManager.MATCH_DIRECT_BOOT_AWARE
                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        if (resolveInfo == null || resolveInfo.activityInfo == null
                || !resolveInfo.activityInfo.exported) {
            return false;
        }
        return Objects.equals(applicationInfo.packageName, resolveInfo.activityInfo.packageName);
    }

    @Override
    public void onHolderSelectedAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        // Launch the new home app so the change is immediately visible even if the home button is
        // not pressed.
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    public void grantAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Permissions.grantAsUser(packageName, AUTOMOTIVE_PERMISSIONS,
                    true, false, true, false, false, user, context);
        }

        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            if (SdkLevel.isAtLeastT()) {
                Permissions.grantAsUser(packageName, WEAR_PERMISSIONS_T,
                        true, false, true, false, false, user, context);
                for (String permission : WEAR_APP_OP_PERMISSIONS) {
                    AppOpPermissions.grantAsUser(packageName, permission, true, user, context);
                }
            }
            if (SdkLevel.isAtLeastV()) {
                Permissions.grantAsUser(packageName, WEAR_PERMISSIONS_V,
                        true, false, true, false, false, user, context);
            }
        }
    }

    @Override
    public void revokeAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Permissions.revokeAsUser(packageName, AUTOMOTIVE_PERMISSIONS, true, false, false,
                    user, context);
        }

        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            if (SdkLevel.isAtLeastT()) {
                Permissions.revokeAsUser(packageName, WEAR_PERMISSIONS_T, true, false, false,
                        user, context);
                for (String permission : WEAR_APP_OP_PERMISSIONS) {
                    AppOpPermissions.revokeAsUser(packageName, permission, user, context);
                }
            }
            if (SdkLevel.isAtLeastV()) {
                Permissions.revokeAsUser(packageName, WEAR_PERMISSIONS_V, true, false, false,
                        user, context);
            }
        }
    }

    @Override
    public boolean isVisibleAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return VisibilityMixin.isVisible("config_showDefaultHome", false, user, context);
    }

    @Override
    public boolean isApplicationVisibleAsUser(@NonNull Role role,
            @NonNull ApplicationInfo applicationInfo, @NonNull UserHandle user,
            @NonNull Context context) {
        return !isSettingsApplicationAsUser(applicationInfo, user, context);
    }
}
