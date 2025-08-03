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

package com.android.compatibility.common.util;

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.permissionToOp;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AppOpsManager;
import android.app.UiAutomation;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Utility methods for permission-related functionality.
 */
public final class PermissionUtils {
    private static final UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private static final String PLATFORM_PACKAGE_NAME = "android";

    private static final String LOG_TAG = "PermissionUtils";

    /**
     * Checks if a permission is granted for a package.
     *
     * <p>This correctly handles pre-M apps by checking the app-ops instead.</p>
     *
     * @param packageName The package that might have the permission granted
     * @param permission  The permission that might be granted
     * @return {@code true} iff the permission and app op are granted
     */
    public static boolean isPermissionAndAppOpGranted(@NonNull String packageName,
            @NonNull String permission) throws Exception {
        if (!isPermissionGranted(packageName, permission)) {
            return false;
        }
        return getAppOp(packageName, permission) != MODE_IGNORED;
    }

    /**
     * Grant a permission to an app.
     *
     * <p>This correctly handles pre-M apps by setting the app-ops.</p>
     *
     * @param packageName The app that should have the permission granted
     * @param permission  The permission to grant
     */
    @TargetApi(28)
    public static void grantPermissionAndAppOp(@NonNull String packageName,
            @NonNull String permission) {
        sUiAutomation.grantRuntimePermission(packageName, permission);

        setAppOpByPermission(packageName, permission, MODE_ALLOWED);
    }

    /**
     * Revoke a permission from an app.
     *
     * <p>This correctly handles pre-M apps by setting the app-ops.</p>
     *
     * @param packageName The app that should have the permission revoked
     * @param permission  The permission to revoke
     */
    @TargetApi(28)
    public static void revokePermissionAndAppOp(@NonNull String packageName,
            @NonNull String permission) {
        sUiAutomation.revokeRuntimePermission(packageName, permission);

        setAppOpByPermission(packageName, permission, MODE_IGNORED);
    }

    /**
     * Get all permissions an app requests. This includes the split permissions.
     *
     * @param packageName The package that requests the permissions.
     * @return The permissions requested by the app
     */
    @NonNull
    public static List<String> getPermissions(@NonNull String packageName)
            throws Exception {
        PackageInfo appInfo = getTargetContext().getPackageManager().getPackageInfo(packageName,
                GET_PERMISSIONS);

        return appInfo.requestedPermissions == null
                ? Collections.emptyList()
                : Arrays.asList(appInfo.requestedPermissions);
    }

    // Enforce non instantiability with a private constructor
    private PermissionUtils() {
    }

    /**
     * Get the state of an app-op.
     *
     * @param packageName The package for which the app-op is retrieved
     * @param permission  The permission to which the app-op is associated
     * @return the app-op mode
     */
    @TargetApi(29)
    private static int getAppOp(@NonNull String packageName, @NonNull String permission)
            throws Exception {
        return callWithShellPermissionIdentity(
                () -> getTargetContext().getSystemService(AppOpsManager.class).unsafeCheckOpRaw(
                        permissionToOp(permission),
                        getTargetContext().getPackageManager().getPackageUid(
                                packageName, 0), packageName));
    }

    /**
     * Set a new state for an app-op
     *
     * <p>Uses the permission-name for new state.</p>
     *
     * @param packageName The package for which the app-op is retrieved
     * @param permission  The permission to which the app-op is associated
     * @param mode        The new mode
     */
    @TargetApi(24)
    private static void setAppOpByPermission(@NonNull String packageName,
            @NonNull String permission, int mode) {
        setAppOpByName(packageName, permissionToOp(permission), mode);
    }

    /**
     * Set a new state for an app-op (using the app-op-name)
     *
     * @param packageName The package for which the app-op is retrieved
     * @param op          The name of the op
     * @param mode        The new mode
     */
    @TargetApi(24)
    private static void setAppOpByName(@NonNull String packageName, @NonNull String op, int mode) {
        runWithShellPermissionIdentity(
                () -> getTargetContext().getSystemService(AppOpsManager.class).setUidMode(op,
                        getTargetContext().getPackageManager()
                                .getPackageUid(packageName, 0), mode));
    }

    /**
     * Checks a permission. Does <u>not</u> check the appOp.
     *
     * <p>Users should use {@link #isPermissionAndAppOpGranted} instead.</p>
     *
     * @param packageName The package that might have the permission granted
     * @param permission  The permission that might be granted
     * @return {@code true} iff the permission is granted
     */
    private static boolean isPermissionGranted(@NonNull String packageName,
            @NonNull String permission) {
        return getTargetContext().getPackageManager().checkPermission(permission, packageName)
                == PERMISSION_GRANTED;
    }

    /**
     * Returns if the device is a handheld device.
     */
    @TargetApi(23)
    public static boolean isHandheld(Context context) {
        final PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
                && !pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                && !pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                && !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    /**
     * Get package names of the applications hlding the role.
     *
     * @param roleName the name of the role to get the role holder for
     * @return a list of package names of the role holders, or an empty list if none.
     */
    @TargetApi(29)
    @NonNull
    public static List<String> getRoleHolders(String roleName) throws Exception {
        return callWithShellPermissionIdentity(() -> getTargetContext()
                        .getSystemService(RoleManager.class).getRoleHolders(roleName),
                Manifest.permission.MANAGE_ROLE_HOLDERS);
    }

    /**
     * Returns the app holding the specified role, or null if no app has the role. If more than one
     * app holds the role, throws an {@link IllegalStateException}.
     *
     * @param roleName The target role name.
     * @return The package name of the app holding the role, or null if not app has the role.
     */
    @Nullable
    public static String getRoleHolder(String roleName) throws Exception {
        List<String> packageNames = getRoleHolders(roleName);
        if (packageNames.isEmpty()) {
            return null;
        } else if (packageNames.size() == 1) {
            return packageNames.get(0);
        } else {
            throw new IllegalStateException("Expected only 1 package to hold the role \""
                    + roleName + "\" but found " + packageNames.size() + ": " + packageNames);
        }
    }

    /**
     * Check if it is a privileged permission.
     *
     * @return {@code true} iff it is a privileged permission
     */
    @TargetApi(28)
    public static boolean isPrivilegedPerm(PermissionInfo permissionInfo) {
        return permissionInfo != null
                && ((permissionInfo.getProtection() & PermissionInfo.PROTECTION_SIGNATURE) != 0)
                && ((permissionInfo.getProtectionFlags()
                & PermissionInfo.PROTECTION_FLAG_PRIVILEGED) != 0);
    }

    /**
     * Check if a package has any privileged permission granted.
     *
     * @param packageName The name of the package.
     * @return {@code true} if the package has any privileged permission granted.
     */
    public static boolean isPkgHasPrivilegedPermGranted(@NonNull String packageName)
            throws Exception {
        final PackageManager pm = getTargetContext().getPackageManager();
        PackageInfo pkgInfo = pm.getPackageInfo(packageName, GET_PERMISSIONS);
        if (pkgInfo.requestedPermissions == null || pkgInfo.requestedPermissionsFlags == null) {
            return false;
        }
        Assert.assertEquals(
                String.format(Locale.US,
                        "[PackageInfo Error]: package(%s) requestedPermissions.length(%d) does "
                                + "not equal to "
                                + "requestedPermissionsFlags.length(%d)",
                        pkgInfo.packageName,
                        pkgInfo.requestedPermissions.length,
                        pkgInfo.requestedPermissionsFlags.length),
                pkgInfo.requestedPermissions.length,
                pkgInfo.requestedPermissionsFlags.length);
        for (int i = 0; i < pkgInfo.requestedPermissions.length; i++) {
            if ((pkgInfo.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED)
                    != 0) {
                try {
                    PermissionInfo permissionInfo = pm.getPermissionInfo(
                            pkgInfo.requestedPermissions[i], 0);
                    if (permissionInfo != null) {
                        return isPrivilegedPerm(permissionInfo);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    Log.i(LOG_TAG, String.format("Permission %s not found",
                            pkgInfo.requestedPermissions[i]), e);
                }
            }
        }
        return false;
    }

    /**
     * Check if a package is a platform signed app.
     * Compare the given app cert with the cert of "android" package on the device.
     * If both are same, then app is platform signed.
     *
     * @param packageName The name of the package.
     * @return {@code true} if the package is a platform signed app.
     */
    public static boolean isPlatformSigned(@NonNull String packageName) {
        final PackageManager pm = getTargetContext().getPackageManager();
        return pm.checkSignatures(packageName, PLATFORM_PACKAGE_NAME)
                == PackageManager.SIGNATURE_MATCH;
    }

    private static Context getTargetContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
