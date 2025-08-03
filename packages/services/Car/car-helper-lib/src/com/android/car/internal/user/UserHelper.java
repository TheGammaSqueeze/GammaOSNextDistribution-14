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
package com.android.car.internal.user;

import android.annotation.ColorInt;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Provides utility methods for generic user-related functionalities that don't require a manager.
 *
 * @hide
 */
public final class UserHelper {
    @VisibleForTesting
    static final String TAG = UserHelper.class.getSimpleName();

    /**
     * Default set of restrictions for Non-Admin users.
     */
    @VisibleForTesting
    public static final Set<String> DEFAULT_NON_ADMIN_RESTRICTIONS = new HashSet<>(Arrays.asList(
            UserManager.DISALLOW_FACTORY_RESET
    ));
    /**
     * Additional optional set of restrictions for Non-Admin users. These are the restrictions
     * configurable via Settings.
     */
    @VisibleForTesting
    public static final Set<String> OPTIONAL_NON_ADMIN_RESTRICTIONS = new HashSet<>(Arrays.asList(
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_OUTGOING_CALLS,
            UserManager.DISALLOW_SMS,
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_UNINSTALL_APPS
    ));
    private UserHelper() {
        throw new UnsupportedOperationException("contains only static methods");
    }
    /**
     * Grants admin permissions to the user.
     *
     * @hide
     */
    @RequiresPermission(allOf = {
            android.Manifest.permission.INTERACT_ACROSS_USERS_FULL,
            android.Manifest.permission.MANAGE_USERS
    })
    public static void grantAdminPermissions(@NonNull Context context, @NonNull UserHandle user) {
        Preconditions.checkArgument(context != null, "Context cannot be null");
        Preconditions.checkArgument(user != null, "User cannot be null");
        UserManager userManager = context.getSystemService(UserManager.class);
        if (!userManager.isAdminUser()) {
            Log.w(TAG, "Only admin users can assign admin permissions.");
            return;
        }
        userManager.setUserAdmin(user.getIdentifier());
        // Remove restrictions imposed on non-admins.
        for (String restriction : DEFAULT_NON_ADMIN_RESTRICTIONS) {
            userManager.setUserRestriction(restriction, /* enable= */ false, user);
        }
        for (String restriction : OPTIONAL_NON_ADMIN_RESTRICTIONS) {
            userManager.setUserRestriction(restriction, /* enable= */ false, user);
        }
    }
    /**
     * Sets the values of default Non-Admin restrictions to the passed in value.
     *
     * @param context Current application context
     * @param user User to set restrictions on.
     * @param enable If true, restriction is ON, If false, restriction is OFF.
     *
     * @hide
     */
    public static void setDefaultNonAdminRestrictions(@NonNull Context context,
            @NonNull UserHandle user, boolean enable) {
        Preconditions.checkArgument(context != null, "Context cannot be null");
        Preconditions.checkArgument(user != null, "User cannot be null");
        UserManager userManager = context.getSystemService(UserManager.class);
        for (String restriction : DEFAULT_NON_ADMIN_RESTRICTIONS) {
            userManager.setUserRestriction(restriction, enable, user);
        }
    }
    /**
     * Assigns a default icon to a user according to the user's id.
     *
     * @param context Current application context
     * @param user User whose avatar is set to default icon.
     * @return Bitmap of the user icon.
     *
     * @hide
     */
    @Nullable
    public static Bitmap assignDefaultIcon(@NonNull Context context, @NonNull UserHandle user) {
        Preconditions.checkArgument(context != null, "Context cannot be null");
        Preconditions.checkArgument(user != null, "User cannot be null");
        UserManager userManager = context.getSystemService(UserManager.class);
        UserInfo userInfo = userManager.getUserInfo(user.getIdentifier());
        if (userInfo == null) {
            return null;
        }
        Bitmap bitmap = CarUserIconProvider.getDefaultUserIcon(context, userInfo);
        userManager.setUserIcon(user.getIdentifier(), bitmap);
        return bitmap;
    }
    /**
     * Returns the default user icon for guest users.
     *
     * @param context Current application context
     * @return Bitmap of the user icon.
     *
     * @hide
     */
    @NonNull
    public static Bitmap getGuestDefaultIcon(@NonNull Context context) {
        Preconditions.checkArgument(context != null, "Context cannot be null");
        return CarUserIconProvider.getGuestDefaultUserIcon(context.getResources());
    }
    /**
     * Get the user icon color for a given user id. This should not be the guest user.
     *
     * @param context Current application context
     * @param user Non-guest user to get the user icon color
     * @return ColorInt of the icon.
     *
     * @hide
     */
    @ColorInt
    public static int getUserNameIconColor(@NonNull Context context, @NonNull UserHandle user) {
        Preconditions.checkArgument(context != null, "Context cannot be null");
        Preconditions.checkArgument(user != null, "User cannot be null");
        UserManager userManager = context.getSystemService(UserManager.class);
        UserInfo userInfo = userManager.getUserInfo(user.getIdentifier());
        if (userInfo == null) {
            throw new IllegalArgumentException("Invalid UserHandle - could not get UserInfo");
        }
        return CarUserIconProvider.getUserNameIconColor(context, userInfo);
    }
}
