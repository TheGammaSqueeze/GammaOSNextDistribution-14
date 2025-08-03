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

package com.android.systemui.car.users;

import android.app.ActivityManager;
import android.car.CarOccupantZoneManager;
import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.settings.UserTracker;

/**
 * Class containing utility functions relating to users in CarSystemUI.
 */
public final class CarSystemUIUserUtil {
    private CarSystemUIUserUtil() {};

    /**
     * Attempt to get the current user handle for the user associated with this SystemUI process.
     * Ideally the classes calling this will have an instance of UserTracker that can return the
     * UserHandle as the central source of truth but in the event that UserTracker is not set,
     * we can use some assumptions about the system to infer what the correct user is.
     */
    public static UserHandle getCurrentUserHandle(Context context,
            @Nullable UserTracker userTracker) {
        if (userTracker != null) {
            return userTracker.getUserHandle();
        }
        // If the UserTracker has not been set, we can try to guess the current user based on the
        // context user.
        if (context.getUserId() == UserHandle.USER_SYSTEM
                && UserManager.isHeadlessSystemUserMode()) {
            // SystemUI is running as system user (which is not the current foreground user) -
            // return the current foreground user.
            return UserHandle.of(ActivityManager.getCurrentUser());
        }
        // SystemUI is running as a non-headless system user - this is probably the correct user
        return UserHandle.of(context.getUserId());
    }

    /**
     * Helper function that returns {@code true} if the current system supports MUMD.
     */
    public static boolean isMUMDSystemUI() {
        return UserManager.isVisibleBackgroundUsersEnabled();
    }

    /**
     * Helper function that returns {@code true} if the current instance of SystemUI is running as
     * a secondary user on MUMD system.
     */
    public static boolean isSecondaryMUMDSystemUI() {
        UserHandle myUserHandle = Process.myUserHandle();
        return isMUMDSystemUI()
                && !myUserHandle.isSystem()
                && myUserHandle.getIdentifier() != ActivityManager.getCurrentUser();
    }

    /**
     * Helper function that returns {@code true} if the current instance of SystemUI is running as
     * the system user on a MUPAND system.
     */
    public static boolean isMUPANDSystemUI() {
        return UserManager.isVisibleBackgroundUsersOnDefaultDisplayEnabled()
                && Process.myUserHandle().isSystem();
    }

    /**
     * Helper function that returns {@code true} if the specified displayId is associated with the
     * current SystemUI instance.
     */
    public static boolean isCurrentSystemUIDisplay(
            @NonNull CarOccupantZoneManager carOccupantZoneManager,
            @NonNull CarOccupantZoneManager.OccupantZoneInfo occupantZone,
            int displayId) {
        if (!isMUMDSystemUI()) {
            return true;
        }
        return carOccupantZoneManager.getAllDisplaysForOccupant(occupantZone).stream().anyMatch(
                d -> d.getDisplayId() == displayId);
    }
}
