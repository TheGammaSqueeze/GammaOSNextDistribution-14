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

package com.android.role.controller.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class LegacyRoleFallbackEnabledUtils {
    /**
     * Name of generic shared preferences file.
     */
    private static final String PREFERENCES_FILE = "preferences";

    /**
     * Key in the generic shared preferences that stores if the user manually selected the "none"
     * role holder for a role.
     */
    private static final String IS_NONE_ROLE_HOLDER_SELECTED_KEY = "is_none_role_holder_selected:";

    /**
     * Get a device protected storage based shared preferences. Avoid storing sensitive data in it.
     *
     * @param context the context to get the shared preferences
     * @return a device protected storage based shared preferences
     */
    @NonNull
    @TargetApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private static SharedPreferences getSharedPreferences(@NonNull UserHandle user,
            @NonNull Context context) {
        String packageName = context.getPackageManager().getPermissionControllerPackageName();
        try {
            context = context.createPackageContextAsUser(packageName, 0, user);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        if (!context.isDeviceProtectedStorage()) {
            context = context.createDeviceProtectedStorageContext();
        }
        return context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
    }

    /**
     * Get all role names with fallback disabled, which means their "none" are set to true.
     *
     * @return A list of role names with fallback disabled.
     */
    public static List<String> getFallbackDisabledRoles(@NonNull UserHandle user,
            @NonNull Context context) {
        List<String> fallbackDisabledRoles = new ArrayList<>();
        SharedPreferences sharedPreferences = getSharedPreferences(user, context);
        for (String key : sharedPreferences.getAll().keySet()) {
            if (key.startsWith(IS_NONE_ROLE_HOLDER_SELECTED_KEY)
                    && sharedPreferences.getBoolean(key, false)) {
                String roleName = key.substring(IS_NONE_ROLE_HOLDER_SELECTED_KEY.length());
                fallbackDisabledRoles.add(roleName);
            }
        }
        return fallbackDisabledRoles;
    }

    /**
     * Check whether the role has the fallback holder enabled.
     *
     * @return whether the "none" role holder is not selected
     */
    public static boolean isRoleFallbackEnabledAsUser(@NonNull String roleName,
            @NonNull UserHandle user, @NonNull Context context) {
        return !getSharedPreferences(user, context)
                .getBoolean(IS_NONE_ROLE_HOLDER_SELECTED_KEY + roleName, false);
    }

    /**
     * Set whether the role has fallback holder enabled.
     */
    public static void setRoleFallbackEnabledAsUser(@NonNull String roleName,
            boolean fallbackEnabled, @NonNull UserHandle user, @NonNull Context context) {
        String key = IS_NONE_ROLE_HOLDER_SELECTED_KEY + roleName;
        if (fallbackEnabled) {
            getSharedPreferences(user, context).edit().remove(key).apply();
        } else {
            getSharedPreferences(user, context).edit().putBoolean(key, true).apply();
        }
    }
}
