/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.server.bluetooth;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.content.pm.PackageManager.SIGNATURE_MATCH;
import static android.os.Process.SYSTEM_UID;
import static android.permission.PermissionManager.PERMISSION_GRANTED;
import static android.permission.PermissionManager.PERMISSION_HARD_DENIED;

import static com.android.server.bluetooth.ChangeIds.RESTRICT_ENABLE_DISABLE;

import android.annotation.RequiresPermission;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.app.compat.CompatChanges;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.PermissionManager;

import java.util.Objects;

class BtPermissionUtils {
    private static final String TAG = BtPermissionUtils.class.getSimpleName();

    private static final int FLAGS_SYSTEM_APP =
            ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;

    private final int mSystemUiUid;

    BtPermissionUtils(Context ctx) {
        // Check if device is configured with no home screen, which implies no SystemUI.
        int systemUiUid = -1;
        try {
            systemUiUid =
                    ctx.createContextAsUser(UserHandle.SYSTEM, 0)
                            .getPackageManager()
                            .getPackageUid(
                                    "com.android.systemui",
                                    PackageManager.PackageInfoFlags.of(
                                            PackageManager.MATCH_SYSTEM_ONLY));
            Log.d(TAG, "Detected SystemUiUid: " + systemUiUid);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Unable to resolve SystemUI's UID.");
        }
        mSystemUiUid = systemUiUid;
    }

    /**
     * Returns true if the BLUETOOTH_CONNECT permission is granted for the calling app. Returns
     * false if the result is a soft denial. Throws SecurityException if the result is a hard
     * denial.
     *
     * <p>Should be used in situations where the app op should not be noted.
     */
    @SuppressLint("AndroidFrameworkRequiresPermission")
    @RequiresPermission(BLUETOOTH_CONNECT)
    static boolean checkConnectPermissionForDataDelivery(
            Context ctx,
            PermissionManager permissionManager,
            AttributionSource source,
            String message) {
        final String permission = BLUETOOTH_CONNECT;
        AttributionSource currentSource =
                new AttributionSource.Builder(ctx.getAttributionSource())
                        .setNext(Objects.requireNonNull(source))
                        .build();
        final int result =
                permissionManager.checkPermissionForDataDeliveryFromDataSource(
                        permission, currentSource, message);
        if (result == PERMISSION_GRANTED) {
            return true;
        }

        final String msg = "Need " + permission + " permission for " + source + ": " + message;
        if (result == PERMISSION_HARD_DENIED) {
            throw new SecurityException(msg);
        }
        Log.w(TAG, msg);
        return false;
    }

    /**
     * Return an empty string if the current call is allowed to toggle bluetooth state
     *
     * <p>Return the error description if this caller is not allowed to toggle Bluetooth
     */
    String callerCanToggle(
            Context ctx,
            AttributionSource source,
            UserManager userManager,
            AppOpsManager appOpsManager,
            PermissionManager permissionManager,
            String message,
            boolean requireForeground) {
        if (isBluetoothDisallowed(userManager)) {
            return "Bluetooth is not allowed";
        }

        if (!checkBluetoothPermissions(
                ctx,
                source,
                userManager,
                appOpsManager,
                permissionManager,
                message,
                requireForeground)) {
            return "Missing Bluetooth permission";
        }

        if (requireForeground && !checkCompatChangeRestriction(source, ctx)) {
            return "Caller does not match restriction criteria";
        }

        return "";
    }

    static void enforcePrivileged(Context ctx) {
        ctx.enforceCallingOrSelfPermission(
                BLUETOOTH_PRIVILEGED, "Need BLUETOOTH_PRIVILEGED permission");
    }

    static int getCallingAppId() {
        return UserHandle.getAppId(Binder.getCallingUid());
    }

    static boolean isCallerSystem(int callingAppId) {
        return callingAppId == Process.SYSTEM_UID;
    }

    static boolean isCallerNfc(int callingAppId) {
        return callingAppId == Process.NFC_UID;
    }

    private static boolean isCallerShell(int callingAppId) {
        return callingAppId == Process.SHELL_UID;
    }

    private static boolean isCallerRoot(int callingAppId) {
        return callingAppId == Process.ROOT_UID;
    }

    private boolean isCallerSystemUi(int callingAppId) {
        return callingAppId == mSystemUiUid;
    }

    private static boolean isPrivileged(Context ctx, int pid, int uid) {
        return (ctx.checkPermission(BLUETOOTH_PRIVILEGED, pid, uid) == PERMISSION_GRANTED)
                || (ctx.getPackageManager().checkSignatures(uid, SYSTEM_UID) == SIGNATURE_MATCH);
    }

    private static boolean isProfileOwner(Context ctx, int uid, String packageName) {
        Context userContext;
        try {
            userContext =
                    ctx.createPackageContextAsUser(
                            ctx.getPackageName(), 0, UserHandle.getUserHandleForUid(uid));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unknown package name");
            return false;
        }
        if (userContext == null) {
            Log.e(TAG, "Unable to retrieve user context for " + uid);
            return false;
        }
        DevicePolicyManager devicePolicyManager =
                userContext.getSystemService(DevicePolicyManager.class);
        if (devicePolicyManager == null) {
            Log.w(TAG, "isProfileOwner: Error retrieving DevicePolicyManager service");
            return false;
        }
        return devicePolicyManager.isProfileOwnerApp(packageName);
    }

    private static boolean isDeviceOwner(Context ctx, int uid, String packageName) {
        if (packageName == null) {
            Log.e(TAG, "isDeviceOwner: packageName is null, returning false");
            return false;
        }

        DevicePolicyManager devicePolicyManager = ctx.getSystemService(DevicePolicyManager.class);
        if (devicePolicyManager == null) {
            Log.w(TAG, "isDeviceOwner: Error retrieving DevicePolicyManager service");
            return false;
        }
        UserHandle deviceOwnerUser = null;
        ComponentName deviceOwnerComponent = null;
        long ident = Binder.clearCallingIdentity();
        try {
            deviceOwnerUser = devicePolicyManager.getDeviceOwnerUser();
            deviceOwnerComponent = devicePolicyManager.getDeviceOwnerComponentOnAnyUser();
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
        if (deviceOwnerUser == null
                || deviceOwnerComponent == null
                || deviceOwnerComponent.getPackageName() == null) {
            return false;
        }

        return deviceOwnerUser.equals(UserHandle.getUserHandleForUid(uid))
                && deviceOwnerComponent.getPackageName().equals(packageName);
    }

    private static boolean isSystem(Context ctx, String packageName, int uid) {
        long ident = Binder.clearCallingIdentity();
        try {
            ApplicationInfo info =
                    ctx.getPackageManager()
                            .getApplicationInfoAsUser(
                                    packageName, 0, UserHandle.getUserHandleForUid(uid));
            return (info.flags & FLAGS_SYSTEM_APP) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private static boolean isBluetoothDisallowed(UserManager userManager) {
        final long callingIdentity = Binder.clearCallingIdentity();
        try {
            return userManager.hasUserRestrictionForUser(
                    UserManager.DISALLOW_BLUETOOTH, UserHandle.SYSTEM);
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    /**
     * Check ifthe packageName belongs to calling uid
     *
     * <p>A null package belongs to any uid
     */
    private static void checkPackage(AppOpsManager appOpsManager, String packageName) {
        final int callingUid = Binder.getCallingUid();
        if (packageName == null) {
            Log.w(TAG, "checkPackage(): called with null packageName from " + callingUid);
            return;
        }

        try {
            appOpsManager.checkPackage(callingUid, packageName);
        } catch (SecurityException e) {
            Log.w(TAG, "checkPackage(): " + packageName + " does not belong to uid " + callingUid);
            throw new SecurityException(e.getMessage());
        }
    }

    boolean checkIfCallerIsForegroundUser(UserManager userManager) {
        final int callingUid = Binder.getCallingUid();
        final UserHandle callingUser = UserHandle.getUserHandleForUid(callingUid);
        final UserHandle foregroundUser;
        final UserHandle parentUser;
        final long callingIdentity = Binder.clearCallingIdentity();
        try {
            // `getCurrentUser` need to be call by system server because it require one of
            //       INTERACT_ACROSS_USERS | INTERACT_ACROSS_USERS_FULL
            foregroundUser = UserHandle.of(ActivityManager.getCurrentUser());
            // `getProfileParent` need to be call by system server because it require one of
            //       MANAGE_USERS | INTERACT_ACROSS_USER and
            parentUser = userManager.getProfileParent(callingUser);
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
        final int callingAppId = UserHandle.getAppId(callingUid);

        // TODO(b/280890575): Remove isCallerX() to only check isForegroundUser

        final boolean valid =
                Objects.equals(callingUser, foregroundUser)
                        || Objects.equals(parentUser, foregroundUser)
                        || isCallerNfc(callingAppId)
                        || isCallerSystemUi(callingAppId)
                        || isCallerShell(callingAppId);

        if (!valid) {
            Log.d(
                    TAG,
                    "checkIfCallerIsForegroundUser: REJECTED:"
                            + " callingUser="
                            + callingUser
                            + " parentUser="
                            + parentUser
                            + " foregroundUser="
                            + foregroundUser
                            + " callingAppId="
                            + callingAppId);
        }
        return valid;
    }

    @RequiresPermission(BLUETOOTH_CONNECT)
    private boolean checkBluetoothPermissions(
            Context ctx,
            AttributionSource source,
            UserManager userManager,
            AppOpsManager appOpsManager,
            PermissionManager permissionManager,
            String message,
            boolean requireForeground) {
        final int callingAppId = getCallingAppId();
        if (isCallerSystem(callingAppId)) return true;
        if (isCallerShell(callingAppId)) return true;
        if (isCallerRoot(callingAppId)) return true;
        checkPackage(appOpsManager, source.getPackageName());

        if (requireForeground && !checkIfCallerIsForegroundUser(userManager)) {
            Log.w(TAG, "Not allowed for non-active and non system user");
            return false;
        }

        if (!checkConnectPermissionForDataDelivery(ctx, permissionManager, source, message)) {
            return false;
        }
        return true;
    }

    /** Starting from T, enable/disable APIs are limited to system apps or device owners. */
    private static boolean checkCompatChangeRestriction(AttributionSource source, Context ctx) {
        final String packageName = source.getPackageName();

        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();
        if (CompatChanges.isChangeEnabled(RESTRICT_ENABLE_DISABLE, callingUid)
                && !isPrivileged(ctx, callingPid, callingUid)
                && !isSystem(ctx, packageName, callingUid)
                && !isDeviceOwner(ctx, callingUid, packageName)
                && !isProfileOwner(ctx, callingUid, packageName)) {
            Log.e(TAG, "Caller is not one of: privileged | system | deviceOwner | profileOwner");
            return false;
        }
        return true;
    }
}
