/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.healthconnect.cts.utils;

import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.healthconnect.cts.utils.TestUtils.getHealthConnectManager;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.base.Preconditions.checkArgument;

import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthPermissions;
import android.os.UserHandle;

import androidx.annotation.Nullable;

import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.ThrowingRunnable;
import com.android.compatibility.common.util.ThrowingSupplier;

import com.google.common.collect.Sets;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class PermissionHelper {

    public static final String MANAGE_HEALTH_DATA = HealthPermissions.MANAGE_HEALTH_DATA_PERMISSION;
    public static final String READ_EXERCISE_ROUTE_PERMISSION =
            "android.permission.health.READ_EXERCISE_ROUTE";

    public static final String READ_HEALTH_DATA_IN_BACKGROUND =
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND";
    public static final String READ_EXERCISE_ROUTES =
            "android.permission.health.READ_EXERCISE_ROUTES";
    private static final String MANAGE_HEALTH_PERMISSIONS =
            HealthPermissions.MANAGE_HEALTH_PERMISSIONS;
    private static final String HEALTH_PERMISSION_PREFIX = "android.permission.health.";

    /** Returns permissions declared in the Manifest of the given package. */
    public static List<String> getDeclaredHealthPermissions(String pkgName) {
        final PackageInfo pi = getAppPackageInfo(pkgName);
        final String[] requestedPermissions = pi.requestedPermissions;

        if (requestedPermissions == null) {
            return List.of();
        }

        return Arrays.stream(requestedPermissions)
                .filter(permission -> permission.startsWith(HEALTH_PERMISSION_PREFIX))
                .toList();
    }

    public static List<String> getGrantedHealthPermissions(String pkgName) {
        final PackageInfo pi = getAppPackageInfo(pkgName);
        final String[] requestedPermissions = pi.requestedPermissions;
        final int[] requestedPermissionsFlags = pi.requestedPermissionsFlags;

        if (requestedPermissions == null) {
            return List.of();
        }

        final List<String> permissions = new ArrayList<>();

        for (int i = 0; i < requestedPermissions.length; i++) {
            if ((requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                if (requestedPermissions[i].startsWith(HEALTH_PERMISSION_PREFIX)) {
                    permissions.add(requestedPermissions[i]);
                }
            }
        }

        return permissions;
    }

    private static PackageInfo getAppPackageInfo(String pkgName) {
        final Context targetContext = androidx.test.InstrumentationRegistry.getTargetContext();
        return runWithShellPermissionIdentity(
                () ->
                        targetContext
                                .getPackageManager()
                                .getPackageInfo(
                                        pkgName,
                                        PackageManager.PackageInfoFlags.of(GET_PERMISSIONS)));
    }

    public static void grantPermission(String pkgName, String permission) {
        HealthConnectManager service = getHealthConnectManager();
        runWithShellPermissionIdentity(
                () ->
                        service.getClass()
                                .getMethod("grantHealthPermission", String.class, String.class)
                                .invoke(service, pkgName, permission),
                MANAGE_HEALTH_PERMISSIONS);
    }

    public static void revokePermission(String pkgName, String permission) {
        HealthConnectManager service = getHealthConnectManager();
        runWithShellPermissionIdentity(
                () ->
                        service.getClass()
                                .getMethod(
                                        "revokeHealthPermission",
                                        String.class,
                                        String.class,
                                        String.class)
                                .invoke(service, pkgName, permission, null),
                MANAGE_HEALTH_PERMISSIONS);
    }

    /**
     * Utility method to call {@link HealthConnectManager#revokeAllHealthPermissions(String,
     * String)}.
     */
    public static void revokeAllPermissions(String packageName, @Nullable String reason) {
        HealthConnectManager service = getHealthConnectManager();
        runWithShellPermissionIdentity(
                () ->
                        service.getClass()
                                .getMethod("revokeAllHealthPermissions", String.class, String.class)
                                .invoke(service, packageName, reason),
                MANAGE_HEALTH_PERMISSIONS);
    }

    /**
     * Same as {@link #revokeAllPermissions(String, String)} but with a delay to wait for grant time
     * to be updated.
     */
    public static void revokeAllPermissionsWithDelay(String packageName, @Nullable String reason)
            throws InterruptedException {
        revokeAllPermissions(packageName, reason);
        Thread.sleep(500);
    }

    /** Revokes all granted Health permissions and re-grants them back. */
    public static void revokeAndThenGrantHealthPermissions(String packageName) {
        List<String> healthPerms = getGrantedHealthPermissions(packageName);

        revokeHealthPermissions(packageName);

        for (String perm : healthPerms) {
            grantPermission(packageName, perm);
        }
    }

    public static void revokeHealthPermissions(String packageName) {
        runWithShellPermissionIdentity(() -> revokeHealthPermissionsPrivileged(packageName));
    }

    private static void revokeHealthPermissionsPrivileged(String packageName)
            throws PackageManager.NameNotFoundException {
        final Context targetContext = androidx.test.InstrumentationRegistry.getTargetContext();
        final PackageManager packageManager = targetContext.getPackageManager();
        final UserHandle user = targetContext.getUser();

        final PackageInfo packageInfo =
                packageManager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));

        final String[] permissions = packageInfo.requestedPermissions;
        if (permissions == null) {
            return;
        }

        for (String permission : permissions) {
            if (permission.startsWith(HEALTH_PERMISSION_PREFIX)) {
                packageManager.revokeRuntimePermission(packageName, permission, user);
            }
        }
    }

    /**
     * Utility method to call {@link
     * HealthConnectManager#getHealthDataHistoricalAccessStartDate(String)}.
     */
    public static Instant getHealthDataHistoricalAccessStartDate(String packageName) {
        HealthConnectManager service = getHealthConnectManager();
        return (Instant)
                runWithShellPermissionIdentity(
                        () ->
                                service.getClass()
                                        .getMethod(
                                                "getHealthDataHistoricalAccessStartDate",
                                                String.class)
                                        .invoke(service, packageName),
                        MANAGE_HEALTH_PERMISSIONS);
    }

    /** Revokes permission for the package for the duration of the runnable. */
    public static void runWithRevokedPermissions(
            String packageName, String permission, ThrowingRunnable runnable) throws Exception {
        runWithRevokedPermissions(
                (ThrowingSupplier<Void>)
                        () -> {
                            runnable.run();
                            return null;
                        },
                packageName,
                permission);
    }

    /** Revokes permission for the package for the duration of the supplier. */
    public static <T> T runWithRevokedPermission(
            String packageName, String permission, ThrowingSupplier<T> supplier) throws Exception {
        return runWithRevokedPermissions(supplier, packageName, permission);
    }

    /** Revokes permission for the package for the duration of the supplier. */
    public static <T> T runWithRevokedPermissions(
            ThrowingSupplier<T> supplier, String packageName, String... permissions)
            throws Exception {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        checkArgument(
                !context.getPackageName().equals(packageName),
                "Can not be called on self, only on other apps");

        UiAutomation uiAutomation =
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        .getUiAutomation();

        var grantedPermissions =
                Sets.intersection(
                        Set.copyOf(getGrantedHealthPermissions(packageName)), Set.of(permissions));

        try {
            grantedPermissions.forEach(
                    permission -> uiAutomation.revokeRuntimePermission(packageName, permission));
            return supplier.get();
        } finally {
            grantedPermissions.forEach(
                    permission -> uiAutomation.grantRuntimePermission(packageName, permission));
        }
    }

    /** Flags the permission as USER_FIXED for the duration of the supplier. */
    public static <T> T runWithUserFixedPermission(
            String packageName, String permission, ThrowingSupplier<T> supplier) throws Exception {
        SystemUtil.runShellCommand(
                String.format("pm set-permission-flags %s %s user-fixed", packageName, permission));
        try {
            return supplier.get();
        } finally {
            SystemUtil.runShellCommand(
                    String.format(
                            "pm clear-permission-flags %s %s user-fixed", packageName, permission));
        }
    }

    /**
     * Sets the device config value for the duration of the supplier.
     *
     * <p>Kills the HC controller after each device config update as the most reliable way of making
     * sure the controller picks up the updated value. Otherwise the callback which the controller
     * uses to listen to device config changes might arrive late (and usually does).
     */
    public static <T> T runWithDeviceConfigForController(
            String key, String value, ThrowingSupplier<T> supplier) throws Exception {
        DeviceConfigRule rule = new DeviceConfigRule(key, value);
        try {
            rule.before();
            killHealthConnectController();
            return supplier.get();
        } catch (Throwable e) {
            throw new Exception(e);
        } finally {
            rule.after();
            killHealthConnectController();
        }
    }

    /** Kills Health Connect controller. */
    private static void killHealthConnectController() {
        SystemUtil.runShellCommandOrThrow(
                "am force-stop com.google.android.healthconnect.controller");
    }
}
