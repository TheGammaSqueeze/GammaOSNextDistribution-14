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

import static android.Manifest.permission.BLUETOOTH_ADVERTISE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;

import androidx.annotation.IntDef;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.nearby.util.identity.CallerIdentity;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Utilities for handling presence broadcast runtime permissions. */
public class BroadcastPermissions {

    /** Indicates no permissions are present, or no permissions are required. */
    public static final int PERMISSION_NONE = 0;

    /** Indicates only the Bluetooth advertise permission is present, or is required. */
    public static final int PERMISSION_BLUETOOTH_ADVERTISE = 1;

    /** Broadcast permission levels. */
    @IntDef({
            PERMISSION_NONE,
            PERMISSION_BLUETOOTH_ADVERTISE
    })
    @Retention(RetentionPolicy.SOURCE)
    @Target({TYPE_USE})
    public @interface BroadcastPermissionLevel {}

    /**
     * Throws a security exception if the caller does not hold the required broadcast permissions.
     */
    public static void enforceBroadcastPermission(Context context, CallerIdentity callerIdentity) {
        if (!checkCallerBroadcastPermission(context, callerIdentity)) {
            throw new SecurityException("uid " + callerIdentity.getUid()
                    + " does not have " + BLUETOOTH_ADVERTISE + ".");
        }
    }

    /**
     * Checks if the app has the permission to broadcast.
     *
     * @return true if the app does have the permission, false otherwise.
     */
    public static boolean checkCallerBroadcastPermission(Context context,
            CallerIdentity callerIdentity) {
        int uid = callerIdentity.getUid();
        int pid = callerIdentity.getPid();

        if (!checkBroadcastPermission(
                getPermissionLevel(context, uid, pid), PERMISSION_BLUETOOTH_ADVERTISE)) {
            return false;
        }

        return true;
    }

    /** Returns the permission level of the caller. */
    @VisibleForTesting
    @BroadcastPermissionLevel
    public static int getPermissionLevel(
            Context context, int uid, int pid) {
        boolean isBluetoothAdvertiseGranted =
                context.checkPermission(BLUETOOTH_ADVERTISE, pid, uid)
                        == PERMISSION_GRANTED;
        if (isBluetoothAdvertiseGranted) {
            return PERMISSION_BLUETOOTH_ADVERTISE;
        }

        return PERMISSION_NONE;
    }

    /** Returns false if the given permission level does not meet the required permission level. */
    private static boolean checkBroadcastPermission(
            @BroadcastPermissionLevel int permissionLevel,
            @BroadcastPermissionLevel int requiredPermissionLevel) {
        return permissionLevel >= requiredPermissionLevel;
    }

    private BroadcastPermissions() {}
}

