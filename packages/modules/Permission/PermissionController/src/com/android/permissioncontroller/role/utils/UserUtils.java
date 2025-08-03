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

package com.android.permissioncontroller.role.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Utility methods about user.
 */
public class UserUtils {

    private UserUtils() {}

    /**
     * Check whether a user is a profile.
     *
     * @param user    the user to check
     * @param context the {@code Context} to retrieve system services
     * @return whether the user is a profile
     */
    public static boolean isProfile(@NonNull UserHandle user, @NonNull Context context) {
        return isManagedProfile(user, context) || isCloneProfile(user, context);
    }

    /**
     * Check whether a user is a managed profile.
     *
     * @param user    the user to check
     * @param context the {@code Context} to retrieve system services
     * @return whether the user is a managed profile
     */
    public static boolean isManagedProfile(@NonNull UserHandle user, @NonNull Context context) {
        Context userContext = getUserContext(context, user);
        UserManager userUserManager = userContext.getSystemService(UserManager.class);
        return userUserManager.isManagedProfile(user.getIdentifier());
    }

    /**
     * Check whether a user is a clone profile.
     *
     * @param user    the user to check
     * @param context the {@code Context} to retrieve system services
     * @return whether the user is a clone profile
     */
    public static boolean isCloneProfile(@NonNull UserHandle user, @NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false;
        }
        Context userContext = getUserContext(context, user);
        UserManager userUserManager = userContext.getSystemService(UserManager.class);
        return userUserManager.isCloneProfile();
    }

    /**
     * Get the work profile of current user, if any.
     *
     * @param context the {@code Context} to retrieve system services
     *
     * @return the work profile of current user, or {@code null} if none
     */
    @Nullable
    public static UserHandle getWorkProfile(@NonNull Context context) {
        UserManager userManager = context.getSystemService(UserManager.class);
        List<UserHandle> profiles = userManager.getUserProfiles();
        UserHandle user = Process.myUserHandle();

        int profilesSize = profiles.size();
        for (int i = 0; i < profilesSize; i++) {
            UserHandle profile = profiles.get(i);

            if (Objects.equals(profile, user)) {
                continue;
            }
            if (!userManager.isManagedProfile(profile.getIdentifier())) {
                continue;
            }
            return profile;
        }
        return null;
    }

    /**
     * Create a context for a user.
     *
     * @param context The context to clone
     * @param user The user the new context should be for
     *
     * @return The context for the new user
     */
    @NonNull
    public static Context getUserContext(@NonNull Context context, @NonNull UserHandle user) {
        if (Process.myUserHandle().equals(user)) {
            return context;
        } else {
            return context.createContextAsUser(user, 0);
        }
    }
}
