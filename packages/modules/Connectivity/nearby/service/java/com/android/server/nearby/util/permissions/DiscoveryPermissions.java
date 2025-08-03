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

package com.android.server.nearby.util.permissions;

import static android.Manifest.permission.BLUETOOTH_SCAN;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.Context;

import androidx.annotation.IntDef;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.nearby.util.identity.CallerIdentity;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Utilities for handling presence discovery runtime permissions. */
public class DiscoveryPermissions {

    /** Indicates no permissions are present, or no permissions are required. */
    public static final int PERMISSION_NONE = 0;

    /** Indicates only the Bluetooth scan permission is present, or is required. */
    public static final int PERMISSION_BLUETOOTH_SCAN = 1;

    // String in AppOpsManager
    @VisibleForTesting
    public static final String OPSTR_BLUETOOTH_SCAN = "android:bluetooth_scan";

    /** Discovery permission levels. */
    @IntDef({
            PERMISSION_NONE,
            PERMISSION_BLUETOOTH_SCAN
    })
    @Retention(RetentionPolicy.SOURCE)
    @Target({TYPE_USE})
    public @interface DiscoveryPermissionLevel {}

    /**
     * Throws a security exception if the caller does not hold the required scan permissions.
     */
    public static void enforceDiscoveryPermission(Context context, CallerIdentity callerIdentity) {
        if (!checkCallerDiscoveryPermission(context, callerIdentity)) {
            throw new SecurityException("uid " + callerIdentity.getUid() + " does not have "
                    + BLUETOOTH_SCAN + ".");
        }
    }

    /**
     * Checks if the caller has the permission to scan.
     */
    public static boolean checkCallerDiscoveryPermission(Context context,
            CallerIdentity callerIdentity) {
        int uid = callerIdentity.getUid();
        int pid = callerIdentity.getPid();

        return checkDiscoveryPermission(
                getPermissionLevel(context, uid, pid), PERMISSION_BLUETOOTH_SCAN);
    }

    /**
     * Checks if the caller is allowed by AppOpsManager to scan.
     */
    public static boolean noteDiscoveryResultDelivery(AppOpsManager appOpsManager,
            CallerIdentity callerIdentity) {
        return noteAppOpAllowed(appOpsManager, callerIdentity, /* message= */ null);
    }

    private static boolean noteAppOpAllowed(AppOpsManager appOpsManager,
            CallerIdentity identity, @Nullable String message) {
        return appOpsManager.noteOp(asAppOp(PERMISSION_BLUETOOTH_SCAN),
                identity.getUid(), identity.getPackageName(), identity.getAttributionTag(), message)
                == AppOpsManager.MODE_ALLOWED;
    }

    /** Returns the permission level of the caller. */
    public static @DiscoveryPermissionLevel int getPermissionLevel(
            Context context, int uid, int pid) {
        boolean isBluetoothScanGranted =
                context.checkPermission(BLUETOOTH_SCAN, pid, uid) == PERMISSION_GRANTED;
        if (isBluetoothScanGranted) {
            return PERMISSION_BLUETOOTH_SCAN;
        }
        return PERMISSION_NONE;
    }

    /** Returns false if the given permission lev`el does not meet the required permission level. */
    private static boolean checkDiscoveryPermission(
            @DiscoveryPermissionLevel int permissionLevel,
            @DiscoveryPermissionLevel int requiredPermissionLevel) {
        return permissionLevel >= requiredPermissionLevel;
    }

    /** Returns the app op string according to the permission level. */
    private static String asAppOp(@DiscoveryPermissionLevel int permissionLevel) {
        if (permissionLevel == PERMISSION_BLUETOOTH_SCAN) {
            return "android:bluetooth_scan";
        }
        throw new IllegalArgumentException();
    }

    private DiscoveryPermissions() {}
}
