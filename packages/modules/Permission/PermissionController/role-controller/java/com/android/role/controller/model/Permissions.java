/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.role.controller.model;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.role.controller.util.ArrayUtils;
import com.android.role.controller.util.CollectionUtils;
import com.android.role.controller.util.PackageUtils;
import com.android.role.controller.util.UserUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Runtime permissions to be granted or revoke by a {@link Role}.
 */
public class Permissions {

    private static final String LOG_TAG = Permissions.class.getSimpleName();

    private static final boolean DEBUG = false;

    private static ArrayMap<String, String> sForegroundToBackgroundPermission;
    private static ArrayMap<String, List<String>> sBackgroundToForegroundPermissions;
    private static final Object sForegroundBackgroundPermissionMappingsLock = new Object();

    private static final ArrayMap<String, Boolean> sRestrictedPermissions = new ArrayMap<>();

    /**
     * Filter a list of permissions based on their SDK versions.
     *
     * @param permissions the list of permissions
     * @param user the user to check for
     * @param context the {@code Context} to retrieve system services
     *
     * @return the filtered list of permission names.
     */
    @NonNull
    public static List<String> filterBySdkVersionAsUser(@NonNull List<Permission> permissions,
            @NonNull UserHandle user, @NonNull Context context) {
        List<String> permissionNames = new ArrayList<>();
        int permissionsSize = permissions.size();
        for (int i = 0; i < permissionsSize; i++) {
            Permission permission = permissions.get(i);
            if (!permission.isAvailableAsUser(user, context)) {
                continue;
            }
            permissionNames.add(permission.getName());
        }
        return permissionNames;
    }

    /**
     * Grant permissions and associated app ops to an application.
     *
     * @param packageName the package name of the application to be granted permissions to
     * @param permissions the list of permissions to be granted
     * @param overrideDisabledSystemPackage whether to ignore the permissions of a disabled system
     *                                      package (if this package is an updated system package)
     * @param overrideUserSetAndFixed whether to override user set and fixed flags on the permission
     * @param setGrantedByRole whether the permissions will be granted as granted-by-role
     * @param setGrantedByDefault whether the permissions will be granted as granted-by-default
     * @param setSystemFixed whether the permissions will be granted as system-fixed
     * @param user the user of the application
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether any permission or app op changed
     *
     * @see com.android.server.pm.permission.DefaultPermissionGrantPolicy#grantRuntimePermissions(
     *      PackageInfo, java.util.Set, boolean, boolean, int)
     */
    public static boolean grantAsUser(@NonNull String packageName,
            @NonNull List<String> permissions, boolean overrideDisabledSystemPackage,
            boolean overrideUserSetAndFixed, boolean setGrantedByRole, boolean setGrantedByDefault,
            boolean setSystemFixed, @NonNull UserHandle user, @NonNull Context context) {
        if (setGrantedByRole == setGrantedByDefault) {
            throw new IllegalArgumentException("Permission must be either granted by role, or"
                    + " granted by default, but not both");
        }

        PackageInfo packageInfo = getPackageInfoAsUser(packageName, user, context);
        if (packageInfo == null) {
            return false;
        }

        if (ArrayUtils.isEmpty(packageInfo.requestedPermissions)) {
            return false;
        }

        // Automatically attempt to grant split permissions to older APKs
        PermissionManager permissionManager = context.getSystemService(PermissionManager.class);
        List<PermissionManager.SplitPermissionInfo> splitPermissions =
                permissionManager.getSplitPermissions();
        ArraySet<String> permissionsWithoutSplits = new ArraySet<>(permissions);
        ArraySet<String> permissionsToGrant = new ArraySet<>(permissionsWithoutSplits);
        int splitPermissionsSize = splitPermissions.size();
        for (int i = 0; i < splitPermissionsSize; i++) {
            PermissionManager.SplitPermissionInfo splitPermission = splitPermissions.get(i);

            if (packageInfo.applicationInfo.targetSdkVersion < splitPermission.getTargetSdk()
                    && permissionsWithoutSplits.contains(splitPermission.getSplitPermission())) {
                permissionsToGrant.addAll(splitPermission.getNewPermissions());
            }
        }

        CollectionUtils.retainAll(permissionsToGrant, packageInfo.requestedPermissions);
        if (permissionsToGrant.isEmpty()) {
            return false;
        }

        // In some cases, like for the Phone or SMS app, we grant permissions regardless
        // of if the version on the system image declares the permission as used since
        // selecting the app as the default for that function the user makes a deliberate
        // choice to grant this app the permissions needed to function. For all other
        // apps, (default grants on first boot and user creation) we don't grant default
        // permissions if the version on the system image does not declare them.
        if (!overrideDisabledSystemPackage && isUpdatedSystemApp(packageInfo)) {
            PackageInfo disabledSystemPackageInfo = getFactoryPackageInfoAsUser(packageName, user,
                    context);
            if (disabledSystemPackageInfo != null) {
                if (ArrayUtils.isEmpty(disabledSystemPackageInfo.requestedPermissions)) {
                    return false;
                }
                CollectionUtils.retainAll(permissionsToGrant,
                        disabledSystemPackageInfo.requestedPermissions);
                if (permissionsToGrant.isEmpty()) {
                    return false;
                }
            }
        }

        // Sort foreground permissions first so that we can grant a background permission based on
        // whether any of its foreground permissions are granted.
        int permissionsToGrantSize = permissionsToGrant.size();
        String[] sortedPermissionsToGrant = new String[permissionsToGrantSize];
        int foregroundPermissionCount = 0;
        int nonForegroundPermissionCount = 0;
        for (int i = 0; i < permissionsToGrantSize; i++) {
            String permission = permissionsToGrant.valueAt(i);

            if (isForegroundPermission(permission, context)) {
                sortedPermissionsToGrant[foregroundPermissionCount] = permission;
                foregroundPermissionCount++;
            } else {
                int index = permissionsToGrantSize - 1 - nonForegroundPermissionCount;
                sortedPermissionsToGrant[index] = permission;
                nonForegroundPermissionCount++;
            }
        }

        boolean permissionOrAppOpChanged = false;

        Context userContext = UserUtils.getUserContext(context, user);
        PackageManager userPackageManager = userContext.getPackageManager();
        Set<String> whitelistedRestrictedPermissions = new ArraySet<>(
                userPackageManager.getWhitelistedRestrictedPermissions(packageName,
                        PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM));

        int sortedPermissionsToGrantLength = sortedPermissionsToGrant.length;
        for (int i = 0; i < sortedPermissionsToGrantLength; i++) {
            String permission = sortedPermissionsToGrant[i];

            if (isRestrictedPermission(permission, context)
                    && whitelistedRestrictedPermissions.add(permission)) {
                userPackageManager.addWhitelistedRestrictedPermission(packageName, permission,
                        PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM);
            }

            permissionOrAppOpChanged |= grantSingleAsUser(packageName, permission,
                    overrideUserSetAndFixed, setGrantedByRole, setGrantedByDefault, setSystemFixed,
                    user, context);
        }

        return permissionOrAppOpChanged;
    }

    private static boolean grantSingleAsUser(@NonNull String packageName,
            @NonNull String permission, boolean overrideUserSetAndFixed, boolean setGrantedByRole,
            boolean setGrantedByDefault, boolean setSystemFixed, @NonNull UserHandle user,
            @NonNull Context context) {
        boolean wasPermissionOrAppOpGranted = isPermissionAndAppOpGrantedAsUser(packageName,
                permission, user, context);
        if (isPermissionFixedAsUser(packageName, permission, false,
                overrideUserSetAndFixed, user, context)
                && !wasPermissionOrAppOpGranted) {
            // Stop granting if this permission is fixed to revoked.
            return false;
        }

        if (isBackgroundPermission(permission, context)) {
            List<String> foregroundPermissions = getForegroundPermissions(permission, context);
            boolean isAnyForegroundPermissionGranted = false;
            int foregroundPermissionsSize = foregroundPermissions.size();
            for (int i = 0; i < foregroundPermissionsSize; i++) {
                String foregroundPermission = foregroundPermissions.get(i);

                if (isPermissionAndAppOpGrantedAsUser(packageName, foregroundPermission, user,
                        context)) {
                    isAnyForegroundPermissionGranted = true;
                    break;
                }
            }

            if (!isAnyForegroundPermissionGranted) {
                // Stop granting if this background permission doesn't have a granted foreground
                // permission.
                return false;
            }
        }

        boolean permissionOrAppOpChanged = grantPermissionAndAppOpAsUser(packageName, permission,
                user, context);

        // Update permission flags.
        int newFlags = 0;
        if (!wasPermissionOrAppOpGranted && setGrantedByRole) {
            newFlags |= PackageManager.FLAG_PERMISSION_GRANTED_BY_ROLE;
        }
        if (setGrantedByDefault) {
            newFlags |= PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT;
        }
        if (setSystemFixed) {
            newFlags |= PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
        }
        int newMask = newFlags;
        newMask |= PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED;
        if (!wasPermissionOrAppOpGranted) {
            // If we've granted a permission which wasn't granted, it's no longer user set or fixed.
            newMask |= PackageManager.FLAG_PERMISSION_USER_FIXED
                    | PackageManager.FLAG_PERMISSION_USER_SET
                    | PackageManager.FLAG_PERMISSION_ONE_TIME;
        }
        // If a component gets a permission for being the default handler A and also default handler
        // B, we grant the weaker grant form. This only applies to default permission grant.
        if (setGrantedByDefault && !setSystemFixed) {
            int oldFlags = getPermissionFlagsAsUser(packageName, permission, user, context);
            if ((oldFlags & PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT) != 0
                    && (oldFlags & PackageManager.FLAG_PERMISSION_SYSTEM_FIXED) != 0) {
                if (DEBUG) {
                    Log.i(LOG_TAG, "Granted not fixed " + permission + " to default handler "
                            + packageName);
                }
                newMask |= PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
            }
        }

        setPermissionFlagsAsUser(packageName, permission, newFlags, newMask,
                user, context);

        return permissionOrAppOpChanged;
    }

    private static boolean isPermissionAndAppOpGrantedAsUser(@NonNull String packageName,
            @NonNull String permission, @NonNull UserHandle user, @NonNull Context context) {
        // Check this permission.
        if (!isPermissionGrantedWithoutCheckingAppOpAsUser(packageName, permission, user,
                context)) {
            return false;
        }

        // Check if the permission is review required.
        if (isPermissionReviewRequiredAsUser(packageName, permission, user, context)) {
            return false;
        }

        if (!isBackgroundPermission(permission, context)) {
            // This permission is not a background permission, check its app op.
            String appOp = getPermissionAppOp(permission);
            if (appOp == null) {
                return true;
            }
            Integer appOpMode = getAppOpModeAsUser(packageName, appOp, user, context);
            if (appOpMode == null) {
                return false;
            }
            if (!isForegroundPermission(permission, context)) {
                // This permission is an ordinary permission, return true if its app op mode is
                // MODE_ALLOWED.
                return appOpMode == AppOpsManager.MODE_ALLOWED;
            } else {
                // This permission is a foreground permission, return true if its app op mode is
                // MODE_FOREGROUND or MODE_ALLOWED.
                return appOpMode == AppOpsManager.MODE_FOREGROUND
                        || appOpMode == AppOpsManager.MODE_ALLOWED;
            }
        } else {
            // This permission is a background permission, return true if any of its foreground
            // permissions' app op modes are MODE_ALLOWED.
            List<String> foregroundPermissions = getForegroundPermissions(permission, context);
            int foregroundPermissionsSize = foregroundPermissions.size();
            for (int i = 0; i < foregroundPermissionsSize; i++) {
                String foregroundPermission = foregroundPermissions.get(i);

                String foregroundAppOp = getPermissionAppOp(foregroundPermission);
                if (foregroundAppOp == null) {
                    continue;
                }
                Integer foregroundAppOpMode = getAppOpModeAsUser(packageName, foregroundAppOp,
                        user, context);
                if (foregroundAppOpMode == null) {
                    continue;
                }
                if (foregroundAppOpMode == AppOpsManager.MODE_ALLOWED) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean grantPermissionAndAppOpAsUser(@NonNull String packageName,
            @NonNull String permission, @NonNull UserHandle user, @NonNull Context context) {
        // Grant the permission.
        boolean permissionOrAppOpChanged = grantPermissionWithoutAppOpAsUser(packageName,
                permission, user, context);

        // Grant the app op.
        if (!isBackgroundPermission(permission, context)) {
            String appOp = getPermissionAppOp(permission);
            if (appOp != null) {
                int appOpMode;
                if (!isForegroundPermission(permission, context)) {
                    // This permission is an ordinary permission, set its app op mode to
                    // MODE_ALLOWED.
                    appOpMode = AppOpsManager.MODE_ALLOWED;
                } else {
                    // This permission is a foreground permission, set its app op mode according to
                    // whether its background permission is granted.
                    String backgroundPermission = getBackgroundPermission(permission, context);
                    if (!isPermissionAndAppOpGrantedAsUser(packageName, backgroundPermission,
                            user, context)) {
                        appOpMode = AppOpsManager.MODE_FOREGROUND;
                    } else {
                        appOpMode = AppOpsManager.MODE_ALLOWED;
                    }
                }
                permissionOrAppOpChanged |= setAppOpUidModeAsUser(packageName, appOp, appOpMode,
                        user, context);
            }
        } else {
            // This permission is a background permission, set all its foreground permissions' app
            // op modes to MODE_ALLOWED.
            List<String> foregroundPermissions = getForegroundPermissions(permission, context);
            int foregroundPermissionsSize = foregroundPermissions.size();
            for (int i = 0; i < foregroundPermissionsSize; i++) {
                String foregroundPermission = foregroundPermissions.get(i);

                String foregroundAppOp = getPermissionAppOp(foregroundPermission);
                if (foregroundAppOp == null) {
                    continue;
                }
                permissionOrAppOpChanged |= setAppOpUidModeAsUser(packageName, foregroundAppOp,
                        AppOpsManager.MODE_ALLOWED, user, context);
            }
        }

        return permissionOrAppOpChanged;
    }

    /**
     * Revoke permissions and associated app ops from an application.
     *
     * @param packageName the package name of the application to be revoke permissions from
     * @param permissions the list of permissions to be revoked
     * @param onlyIfGrantedByRole revoke the permission only if it is granted by role
     * @param onlyIfGrantedByDefault revoke the permission only if it is granted by default
     * @param overrideSystemFixed whether system-fixed permissions can be revoked
     * @param context the {@code Context} to retrieve system services
     * @param user the user of the application
     *
     * @return whether any permission or app op changed
     *
     * @see com.android.server.pm.permission.DefaultPermissionGrantPolicy#revokeRuntimePermissions(
     *      String, java.util.Set, boolean, int)
     */
    public static boolean revokeAsUser(@NonNull String packageName,
            @NonNull List<String> permissions, boolean onlyIfGrantedByRole,
            boolean onlyIfGrantedByDefault, boolean overrideSystemFixed, @NonNull UserHandle user,
            @NonNull Context context) {
        PackageInfo packageInfo = getPackageInfoAsUser(packageName, user, context);
        if (packageInfo == null) {
            return false;
        }

        if (ArrayUtils.isEmpty(packageInfo.requestedPermissions)) {
            return false;
        }

        ArraySet<String> permissionsToRevoke = new ArraySet<>(permissions);
        CollectionUtils.retainAll(permissionsToRevoke, packageInfo.requestedPermissions);
        if (permissionsToRevoke.isEmpty()) {
            return false;
        }

        // Sort background permissions first so that we can revoke a foreground permission based on
        // whether its background permission is revoked.
        int permissionsToRevokeSize = permissionsToRevoke.size();
        String[] sortedPermissionsToRevoke = new String[permissionsToRevokeSize];
        int backgroundPermissionCount = 0;
        int nonBackgroundPermissionCount = 0;
        for (int i = 0; i < permissionsToRevokeSize; i++) {
            String permission = permissionsToRevoke.valueAt(i);

            if (isBackgroundPermission(permission, context)) {
                sortedPermissionsToRevoke[backgroundPermissionCount] = permission;
                backgroundPermissionCount++;
            } else {
                int index = permissionsToRevokeSize - 1 - nonBackgroundPermissionCount;
                sortedPermissionsToRevoke[index] = permission;
                nonBackgroundPermissionCount++;
            }
        }

        Context userContext = UserUtils.getUserContext(context, user);
        PackageManager userPackageManager = userContext.getPackageManager();
        Set<String> whitelistedRestrictedPermissions =
                userPackageManager.getWhitelistedRestrictedPermissions(packageName,
                    PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM
                    | PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
                    | PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER);

        boolean permissionOrAppOpChanged = false;

        int sortedPermissionsToRevokeLength = sortedPermissionsToRevoke.length;
        for (int i = 0; i < sortedPermissionsToRevokeLength; i++) {
            String permission = sortedPermissionsToRevoke[i];

            permissionOrAppOpChanged |= revokeSingleAsUser(packageName, permission,
                    onlyIfGrantedByRole, onlyIfGrantedByDefault, overrideSystemFixed, user,
                    context);

            // Remove from the system whitelist only if not granted by default.
            if (!isPermissionGrantedByDefaultAsUser(packageName, permission, user, context)
                    && whitelistedRestrictedPermissions.remove(permission)) {
                userPackageManager.removeWhitelistedRestrictedPermission(packageName, permission,
                        PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM);
            }
        }

        return permissionOrAppOpChanged;
    }

    private static boolean revokeSingleAsUser(@NonNull String packageName,
            @NonNull String permission, boolean onlyIfGrantedByRole, boolean onlyIfGrantedByDefault,
            boolean overrideSystemFixed, @NonNull UserHandle user, @NonNull Context context) {
        if (onlyIfGrantedByRole == onlyIfGrantedByDefault) {
            throw new IllegalArgumentException("Permission can be revoked only if either granted by"
                    + " role, or granted by default, but not both");
        }

        if (onlyIfGrantedByRole) {
            if (!isPermissionGrantedByRoleAsUser(packageName, permission, user, context)) {
                return false;
            }
            setPermissionFlagsAsUser(packageName, permission, 0,
                    PackageManager.FLAG_PERMISSION_GRANTED_BY_ROLE, user, context);
        }

        if (onlyIfGrantedByDefault) {
            if (!isPermissionGrantedByDefaultAsUser(packageName, permission, user, context)) {
                return false;
            }
            // Remove the granted-by-default permission flag.
            setPermissionFlagsAsUser(packageName, permission, 0,
                    PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT, user, context);
            // Note that we do not revoke FLAG_PERMISSION_SYSTEM_FIXED. That bit remains sticky once
            // set.
        }

        if (isPermissionFixedAsUser(packageName, permission, overrideSystemFixed, false,
                user, context)
                && isPermissionAndAppOpGrantedAsUser(packageName, permission, user, context)) {
            // Stop revoking if this permission is fixed to granted.
            return false;
        }

        if (isForegroundPermission(permission, context)) {
            String backgroundPermission = getBackgroundPermission(permission, context);
            if (isPermissionAndAppOpGrantedAsUser(packageName, backgroundPermission, user,
                    context)) {
                // Stop revoking if this foreground permission has a granted background permission.
                return false;
            }
        }

        return revokePermissionAndAppOpAsUser(packageName, permission, user, context);
    }

    private static boolean revokePermissionAndAppOpAsUser(@NonNull String packageName,
            @NonNull String permission, @NonNull UserHandle user, @NonNull Context context) {
        boolean permissionOrAppOpChanged = false;

        boolean isRuntimePermissionsSupported = isRuntimePermissionsSupportedAsUser(packageName,
                user, context);
        if (isRuntimePermissionsSupported) {
            // Revoke the permission.
            permissionOrAppOpChanged |= revokePermissionWithoutAppOpAsUser(packageName, permission,
                    user, context);
        }

        // Revoke the app op.
        if (!isBackgroundPermission(permission, context)) {
            String appOp = getPermissionAppOp(permission);
            if (appOp != null) {
                // This permission is an ordinary or foreground permission, reset its app op mode to
                // default.
                int appOpMode = getDefaultAppOpMode(appOp);
                boolean appOpModeChanged = setAppOpUidModeAsUser(packageName, appOp, appOpMode,
                        user, context);
                permissionOrAppOpChanged |= appOpModeChanged;

                if (appOpModeChanged) {
                    if (!isRuntimePermissionsSupported
                            && (appOpMode == AppOpsManager.MODE_FOREGROUND
                                    || appOpMode == AppOpsManager.MODE_ALLOWED)) {
                        // We've reset this permission's app op mode to be permissive, so we'll need
                        // the user to review it again.
                        setPermissionFlagsAsUser(packageName, permission,
                                PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED,
                                PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED, user, context);
                    }
                }
            }
        } else {
            // This permission is a background permission, set all its granted foreground
            // permissions' app op modes to MODE_FOREGROUND.
            List<String> foregroundPermissions = getForegroundPermissions(permission, context);
            int foregroundPermissionsSize = foregroundPermissions.size();
            for (int i = 0; i < foregroundPermissionsSize; i++) {
                String foregroundPermission = foregroundPermissions.get(i);

                if (!isPermissionAndAppOpGrantedAsUser(packageName, foregroundPermission, user,
                        context)) {
                    continue;
                }

                String foregroundAppOp = getPermissionAppOp(foregroundPermission);
                if (foregroundAppOp == null) {
                    continue;
                }
                permissionOrAppOpChanged |= setAppOpUidModeAsUser(packageName, foregroundAppOp,
                        AppOpsManager.MODE_FOREGROUND, user, context);
            }
        }

        return permissionOrAppOpChanged;
    }

    @Nullable
    private static PackageInfo getPackageInfoAsUser(@NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        return getPackageInfoAsUser(packageName, 0, user, context);
    }

    @Nullable
    private static PackageInfo getFactoryPackageInfoAsUser(@NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        return getPackageInfoAsUser(packageName, PackageManager.MATCH_FACTORY_ONLY,
                user, context);
    }

    @Nullable
    private static PackageInfo getPackageInfoAsUser(@NonNull String packageName, int extraFlags,
            @NonNull UserHandle user, @NonNull Context context) {
        return PackageUtils.getPackageInfoAsUser(packageName, extraFlags
                // TODO: Why MATCH_UNINSTALLED_PACKAGES?
                | PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.GET_PERMISSIONS,
                user, context);
    }

    private static boolean isUpdatedSystemApp(@NonNull PackageInfo packageInfo) {
        return packageInfo.applicationInfo != null && (packageInfo.applicationInfo.flags
                & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    static boolean isRuntimePermissionsSupportedAsUser(@NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfoAsUser(packageName, user,
                context);
        if (applicationInfo == null) {
            return false;
        }
        return applicationInfo.targetSdkVersion >= Build.VERSION_CODES.M;
    }

    private static int getPermissionFlagsAsUser(@NonNull String packageName,
            @NonNull String permission, @NonNull UserHandle user, @NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        return packageManager.getPermissionFlags(permission, packageName, user);
    }

    private static boolean isPermissionFixedAsUser(@NonNull String packageName,
            @NonNull String permission, boolean overrideSystemFixed,
            boolean overrideUserSetAndFixed, @NonNull UserHandle user, @NonNull Context context) {
        int flags = getPermissionFlagsAsUser(packageName, permission, user, context);
        int fixedFlags = PackageManager.FLAG_PERMISSION_POLICY_FIXED;
        if (!overrideSystemFixed) {
            fixedFlags |= PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
        }
        if (!overrideUserSetAndFixed) {
            fixedFlags |= PackageManager.FLAG_PERMISSION_USER_FIXED
                    | PackageManager.FLAG_PERMISSION_USER_SET
                    | PackageManager.FLAG_PERMISSION_ONE_TIME;
        }
        return (flags & fixedFlags) != 0;
    }

    private static boolean isPermissionGrantedByDefaultAsUser(@NonNull String packageName,
            @NonNull String permission, @NonNull UserHandle user, @NonNull Context context) {
        int flags = getPermissionFlagsAsUser(packageName, permission, user, context);
        return (flags & PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT) != 0;
    }

    static boolean isPermissionGrantedByRoleAsUser(@NonNull String packageName,
            @NonNull String permission, @NonNull UserHandle user, @NonNull Context context) {
        int flags = getPermissionFlagsAsUser(packageName, permission, user, context);
        return (flags & PackageManager.FLAG_PERMISSION_GRANTED_BY_ROLE) != 0;
    }

    private static boolean isPermissionReviewRequiredAsUser(@NonNull String packageName,
            @NonNull String permission, @NonNull UserHandle user, @NonNull Context context) {
        int flags = getPermissionFlagsAsUser(packageName, permission, user, context);
        return (flags & PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED) != 0;
    }

    private static void setPermissionFlagsAsUser(@NonNull String packageName,
            @NonNull String permission, int flags, int mask, @NonNull UserHandle user,
            @NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        packageManager.updatePermissionFlags(permission, packageName, mask, flags, user);
    }

    static void setPermissionGrantedByRoleAsUser(@NonNull String packageName,
            @NonNull String permission, boolean grantedByRole, @NonNull UserHandle user,
            @NonNull Context context) {
        setPermissionFlagsAsUser(packageName, permission,
                grantedByRole ? PackageManager.FLAG_PERMISSION_GRANTED_BY_ROLE : 0,
                PackageManager.FLAG_PERMISSION_GRANTED_BY_ROLE, user, context);
    }

    /**
     * Most of the time {@link #isPermissionAndAppOpGranted(String, String, Context)} should be used
     * instead.
     */
    private static boolean isPermissionGrantedWithoutCheckingAppOpAsUser(
            @NonNull String packageName, @NonNull String permission, @NonNull UserHandle user,
            @NonNull Context context) {
        Context userContext = UserUtils.getUserContext(context, user);
        PackageManager userPackageManager = userContext.getPackageManager();
        return userPackageManager.checkPermission(permission, packageName)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean grantPermissionWithoutAppOpAsUser(@NonNull String packageName,
            @NonNull String permission, @NonNull UserHandle user, @NonNull Context context) {
        if (isPermissionGrantedWithoutCheckingAppOpAsUser(packageName, permission, user, context)) {
            return false;
        }
        PackageManager packageManager = context.getPackageManager();
        packageManager.grantRuntimePermission(packageName, permission, user);
        return true;
    }

    private static boolean revokePermissionWithoutAppOpAsUser(@NonNull String packageName,
            @NonNull String permission, @NonNull UserHandle user, @NonNull Context context) {
        if (!isPermissionGrantedWithoutCheckingAppOpAsUser(packageName, permission, user,
                context)) {
            return false;
        }
        PackageManager packageManager = context.getPackageManager();
        packageManager.revokeRuntimePermission(packageName, permission, user);
        return true;
    }

    private static boolean isForegroundPermission(@NonNull String permission,
            @NonNull Context context) {
        ensureForegroundBackgroundPermissionMappings(context);
        return sForegroundToBackgroundPermission.containsKey(permission);
    }

    @Nullable
    private static String getBackgroundPermission(@NonNull String foregroundPermission,
            @NonNull Context context) {
        ensureForegroundBackgroundPermissionMappings(context);
        return sForegroundToBackgroundPermission.get(foregroundPermission);
    }

    private static boolean isBackgroundPermission(@NonNull String permission,
            @NonNull Context context) {
        ensureForegroundBackgroundPermissionMappings(context);
        return sBackgroundToForegroundPermissions.containsKey(permission);
    }

    @Nullable
    private static List<String> getForegroundPermissions(@NonNull String backgroundPermission,
            @NonNull Context context) {
        ensureForegroundBackgroundPermissionMappings(context);
        return sBackgroundToForegroundPermissions.get(backgroundPermission);
    }

    private static void ensureForegroundBackgroundPermissionMappings(@NonNull Context context) {
        synchronized (sForegroundBackgroundPermissionMappingsLock) {
            if (sForegroundToBackgroundPermission == null
                    && sBackgroundToForegroundPermissions == null) {
                createForegroundBackgroundPermissionMappings(context);
            }
        }
    }

    private static boolean isRestrictedPermission(@NonNull String permission,
            @NonNull Context context) {
        synchronized (sRestrictedPermissions) {
            if (sRestrictedPermissions.containsKey(permission)) {
                return sRestrictedPermissions.get(permission);
            }
        }

        PackageManager packageManager = context.getPackageManager();
        PermissionInfo permissionInfo = null;
        try {
            permissionInfo = packageManager.getPermissionInfo(permission, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "Cannot get PermissionInfo for permission: " + permission);
        }

        // Don't expect that to be a transient error, so we can still cache the failed information.
        boolean isRestrictedPermission = permissionInfo != null
                && (permissionInfo.flags & (PermissionInfo.FLAG_SOFT_RESTRICTED
                | PermissionInfo.FLAG_HARD_RESTRICTED)) != 0;

        synchronized (sRestrictedPermissions) {
            sRestrictedPermissions.put(permission, isRestrictedPermission);
        }

        return isRestrictedPermission;
    }

    private static void createForegroundBackgroundPermissionMappings(@NonNull Context context) {
        List<String> permissions = new ArrayList<>();
        sBackgroundToForegroundPermissions = new ArrayMap<>();

        PackageManager packageManager = context.getPackageManager();
        List<PermissionGroupInfo> permissionGroupInfos = packageManager.getAllPermissionGroups(0);

        int permissionGroupInfosSize = permissionGroupInfos.size();
        for (int permissionGroupInfosIndex = 0;
                permissionGroupInfosIndex < permissionGroupInfosSize; permissionGroupInfosIndex++) {
            PermissionGroupInfo permissionGroupInfo = permissionGroupInfos.get(
                    permissionGroupInfosIndex);

            List<PermissionInfo> permissionInfos;
            try {
                permissionInfos = packageManager.queryPermissionsByGroup(
                    permissionGroupInfo.name, 0);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(LOG_TAG, "Cannot get permissions for group: " + permissionGroupInfo.name);
                continue;
            }

            int permissionInfosSize = permissionInfos.size();
            for (int permissionInfosIndex = 0; permissionInfosIndex < permissionInfosSize;
                    permissionInfosIndex++) {
                PermissionInfo permissionInfo = permissionInfos.get(permissionInfosIndex);

                String permission = permissionInfo.name;
                permissions.add(permission);

                String backgroundPermission = permissionInfo.backgroundPermission;
                if (backgroundPermission != null) {
                    List<String> foregroundPermissions = sBackgroundToForegroundPermissions.get(
                            backgroundPermission);
                    if (foregroundPermissions == null) {
                        foregroundPermissions = new ArrayList<>();
                        sBackgroundToForegroundPermissions.put(backgroundPermission,
                                foregroundPermissions);
                    }
                    foregroundPermissions.add(permission);
                }
            }
        }

        // Remove background permissions declared by foreground permissions but don't actually
        // exist.
        sBackgroundToForegroundPermissions.retainAll(permissions);

        // Collect foreground permissions that have existent background permissions.
        sForegroundToBackgroundPermission = new ArrayMap<>();

        int backgroundToForegroundPermissionsSize = sBackgroundToForegroundPermissions.size();
        for (int backgroundToForegroundPermissionsIndex = 0;
                backgroundToForegroundPermissionsIndex < backgroundToForegroundPermissionsSize;
                backgroundToForegroundPermissionsIndex++) {
            String backgroundPerimssion = sBackgroundToForegroundPermissions.keyAt(
                    backgroundToForegroundPermissionsIndex);
            List<String> foregroundPermissions = sBackgroundToForegroundPermissions.valueAt(
                    backgroundToForegroundPermissionsIndex);

            int foregroundPermissionsSize = foregroundPermissions.size();
            for (int foregroundPermissionsIndex = 0;
                    foregroundPermissionsIndex < foregroundPermissionsSize;
                    foregroundPermissionsIndex++) {
                String foregroundPermission = foregroundPermissions.get(foregroundPermissionsIndex);

                sForegroundToBackgroundPermission.put(foregroundPermission, backgroundPerimssion);
            }
        }
    }

    @Nullable
    private static String getPermissionAppOp(@NonNull String permission) {
        return AppOpsManager.permissionToOp(permission);
    }

    @Nullable
    static Integer getAppOpModeAsUser(@NonNull String packageName, @NonNull String appOp,
            @NonNull UserHandle user, @NonNull Context context) {
        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfoAsUser(packageName,
                user, context);
        if (applicationInfo == null) {
            return null;
        }
        AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
        return appOpsManager.unsafeCheckOpRaw(appOp, applicationInfo.uid, packageName);
    }

    static int getDefaultAppOpMode(@NonNull String appOp) {
        return AppOpsManager.opToDefaultMode(appOp);
    }

    static boolean setAppOpUidModeAsUser(@NonNull String packageName, @NonNull String appOp,
            int mode, @NonNull UserHandle user, @NonNull Context context) {
        return setAppOpModeAsUser(packageName, appOp, mode, true, user, context);
    }

    static boolean setAppOpPackageModeAsUser(@NonNull String packageName, @NonNull String appOp,
            int mode, @NonNull UserHandle user, @NonNull Context context) {
        return setAppOpModeAsUser(packageName, appOp, mode, false, user, context);
    }

    private static boolean setAppOpModeAsUser(@NonNull String packageName, @NonNull String appOp,
            int mode, boolean setUidMode, @NonNull UserHandle user, @NonNull Context context) {
        Integer currentMode = getAppOpModeAsUser(packageName, appOp, user, context);
        if (currentMode != null && currentMode == mode) {
            return false;
        }
        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfoAsUser(packageName, user,
                context);
        if (applicationInfo == null) {
            Log.e(LOG_TAG, "Cannot get ApplicationInfo for package to set app op mode: "
                    + packageName);
            return false;
        }
        AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
        if (setUidMode) {
            appOpsManager.setUidMode(appOp, applicationInfo.uid, mode);
        } else {
            appOpsManager.setMode(appOp, applicationInfo.uid, packageName, mode);
        }
        return true;
    }
}
