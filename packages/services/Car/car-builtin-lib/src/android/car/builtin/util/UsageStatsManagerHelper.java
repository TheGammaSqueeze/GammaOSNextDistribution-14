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

package android.car.builtin.util;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.annotation.UserIdInt;
import android.app.usage.UsageStatsManager;
import android.util.Log;

import java.util.Objects;

/**
 * Wrapper for UsageStatsManager.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class UsageStatsManagerHelper {

    private static final String TAG = UsageStatsManagerHelper.class.getSimpleName();
    private static final boolean DEBUG = false;

    private UsageStatsManagerHelper() {
        throw new UnsupportedOperationException();
    }

    /**
     * Reports user interaction with a given package in the given user.
     * Check {@link UsageStatsManager#reportUserInteraction(String, int)}
     *
     * @param usageStatsManager {@link UsageStatsManager} instance used to report the user
     *                          interaction
     * @param packageName       package name reported for the user interaction
     * @param userId            user id of the process
     */
    public static void reportUserInteraction(@NonNull UsageStatsManager usageStatsManager,
            @NonNull String packageName, @UserIdInt int userId) {
        if (DEBUG) {
            Log.d(TAG, "usageStatsManager#reportUserInteraction package=" + packageName
                    + " userId=" + userId);
        }
        Objects.requireNonNull(usageStatsManager);
        Objects.requireNonNull(packageName);
        usageStatsManager.reportUserInteraction(packageName, userId);
    }

}
