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
package com.android.car;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;

import java.util.Arrays;

/**
 * Helper for permissions checks.
 */
public final class PermissionHelper {

    /**
     * Checks if caller has the {@code android.Manifest.permission.DUMP} permission.
     *
     * @throws SecurityException if it doesn't.
     */
    public static void checkHasDumpPermissionGranted(@NonNull Context context,
            @NonNull String message) {
        checkHasAtLeastOnePermissionGranted(context, message, android.Manifest.permission.DUMP);
    }

    /**
     * Checks if caller has at least one of the give permissions.
     *
     * @throws SecurityException if it doesn't.
     */
    public static void checkHasAtLeastOnePermissionGranted(@NonNull Context context,
            @NonNull String message, @NonNull String...permissions) {
        if (!hasAtLeastOnePermissionGranted(context, permissions)) {
            if (permissions.length == 1) {
                throw new SecurityException("You need " + permissions[0] + " to: " + message);
            }
            throw new SecurityException("You need one of " + Arrays.toString(permissions)
                    + " to: " + message);
        }
    }

    /**
     * Returns whether the given {@code uids} has at least one of the give permissions.
     */
    public static boolean hasAtLeastOnePermissionGranted(@NonNull Context context,
            @NonNull String... permissions) {
        for (String permission : permissions) {
            if (context.checkCallingOrSelfPermission(permission)
                    == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    private PermissionHelper() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}
