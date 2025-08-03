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

package com.android.server.healthconnect.permission;

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthPermissions;
import android.os.Binder;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;

import java.time.Instant;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A handler for HealthConnect permission-related logic.
 *
 * @hide
 */
public final class HealthConnectPermissionHelper {
    private static final Period GRANT_TIME_TO_START_ACCESS_DATE_PERIOD = Period.ofDays(30);

    private static final int MASK_PERMISSION_FLAGS =
            PackageManager.FLAG_PERMISSION_USER_SET
                    | PackageManager.FLAG_PERMISSION_USER_FIXED
                    | PackageManager.FLAG_PERMISSION_AUTO_REVOKED;

    private final Context mContext;
    private final PackageManager mPackageManager;
    private final Set<String> mHealthPermissions;
    private final HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;
    private final FirstGrantTimeManager mFirstGrantTimeManager;

    /**
     * Constructs a {@link HealthConnectPermissionHelper}.
     *
     * @param context the service context.
     * @param packageManager a {@link PackageManager} instance.
     * @param healthPermissions a {@link Set} of permissions that are recognized as
     *     HealthConnect-defined permissions.
     * @param permissionIntentTracker a {@link
     *     com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker} instance
     *     that tracks apps allowed to request health permissions.
     */
    public HealthConnectPermissionHelper(
            Context context,
            PackageManager packageManager,
            Set<String> healthPermissions,
            HealthPermissionIntentAppsTracker permissionIntentTracker,
            FirstGrantTimeManager firstGrantTimeManager) {
        mContext = context;
        mPackageManager = packageManager;
        mHealthPermissions = healthPermissions;
        mPermissionIntentAppsTracker = permissionIntentTracker;
        mFirstGrantTimeManager = firstGrantTimeManager;
    }

    /**
     * See {@link HealthConnectManager#grantHealthPermission}.
     *
     * <p>NOTE: Once permission grant is successful, the package name will also be appended to the
     * end of the priority list corresponding to {@code permissionName}'s health permission
     * category.
     */
    public void grantHealthPermission(
            @NonNull String packageName, @NonNull String permissionName, @NonNull UserHandle user) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(permissionName);
        enforceManageHealthPermissions(/* message= */ "grantHealthPermission");
        enforceValidHealthPermission(permissionName);
        UserHandle checkedUser = UserHandle.of(handleIncomingUser(user.getIdentifier()));
        enforceValidPackage(packageName, checkedUser);
        enforceSupportPermissionsUsageIntent(packageName, checkedUser);
        final long token = Binder.clearCallingIdentity();
        try {
            mPackageManager.grantRuntimePermission(packageName, permissionName, checkedUser);
            mPackageManager.updatePermissionFlags(
                    permissionName,
                    packageName,
                    MASK_PERMISSION_FLAGS,
                    PackageManager.FLAG_PERMISSION_USER_SET,
                    checkedUser);
            addToPriorityListIfRequired(packageName, permissionName);

        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** See {@link HealthConnectManager#revokeHealthPermission}. */
    public void revokeHealthPermission(
            @NonNull String packageName,
            @NonNull String permissionName,
            @Nullable String reason,
            @NonNull UserHandle user) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(permissionName);
        enforceManageHealthPermissions(/* message= */ "revokeHealthPermission");
        enforceValidHealthPermission(permissionName);
        UserHandle checkedUser = UserHandle.of(handleIncomingUser(user.getIdentifier()));
        enforceValidPackage(packageName, checkedUser);
        final long token = Binder.clearCallingIdentity();
        try {
            boolean isAlreadyDenied =
                    mPackageManager.checkPermission(permissionName, packageName)
                            == PackageManager.PERMISSION_DENIED;
            int permissionFlags =
                    mPackageManager.getPermissionFlags(permissionName, packageName, checkedUser);
            if (!isAlreadyDenied) {
                mPackageManager.revokeRuntimePermission(
                        packageName, permissionName, checkedUser, reason);
            }
            if (isAlreadyDenied
                    && (permissionFlags & PackageManager.FLAG_PERMISSION_USER_SET) != 0) {
                permissionFlags = permissionFlags | PackageManager.FLAG_PERMISSION_USER_FIXED;
            } else {
                permissionFlags = permissionFlags | PackageManager.FLAG_PERMISSION_USER_SET;
            }
            permissionFlags = permissionFlags & ~PackageManager.FLAG_PERMISSION_AUTO_REVOKED;
            mPackageManager.updatePermissionFlags(
                    permissionName,
                    packageName,
                    MASK_PERMISSION_FLAGS,
                    permissionFlags,
                    checkedUser);

            removeFromPriorityListIfRequired(packageName, permissionName);

        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** See {@link HealthConnectManager#revokeAllHealthPermissions}. */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public void revokeAllHealthPermissions(
            @NonNull String packageName, @Nullable String reason, @NonNull UserHandle user) {
        Objects.requireNonNull(packageName);
        enforceManageHealthPermissions(/* message= */ "revokeAllHealthPermissions");
        UserHandle checkedUser = UserHandle.of(handleIncomingUser(user.getIdentifier()));
        enforceValidPackage(packageName, checkedUser);
        final long token = Binder.clearCallingIdentity();
        try {
            revokeAllHealthPermissionsUnchecked(packageName, checkedUser, reason);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** See {@link HealthConnectManager#getGrantedHealthPermissions}. */
    @NonNull
    public List<String> getGrantedHealthPermissions(
            @NonNull String packageName, @NonNull UserHandle user) {
        Objects.requireNonNull(packageName);
        enforceManageHealthPermissions(/* message= */ "getGrantedHealthPermissions");
        UserHandle checkedUser = UserHandle.of(handleIncomingUser(user.getIdentifier()));
        enforceValidPackage(packageName, checkedUser);
        final long token = Binder.clearCallingIdentity();
        try {
            return getGrantedHealthPermissionsUnchecked(packageName, checkedUser);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** See {@link HealthConnectManager#getHealthPermissionsFlags(String, List)}. */
    @NonNull
    public Map<String, Integer> getHealthPermissionsFlags(
            @NonNull String packageName,
            @NonNull UserHandle user,
            @NonNull List<String> permissions) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(user);
        Objects.requireNonNull(permissions);

        enforceManageHealthPermissions(/* message= */ "getHealthPermissionsFlags");
        UserHandle checkedUser = UserHandle.of(handleIncomingUser(user.getIdentifier()));
        enforceValidPackage(packageName, checkedUser);
        final long token = Binder.clearCallingIdentity();
        try {
            return getHealthPermissionsFlagsUnchecked(packageName, checkedUser, permissions);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** See {@link HealthConnectManager#setHealthPermissionsUserFixedFlagValue(String, List)}. */
    public void setHealthPermissionsUserFixedFlagValue(
            @NonNull String packageName,
            @NonNull UserHandle user,
            @NonNull List<String> permissions,
            boolean value) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(user);
        Objects.requireNonNull(permissions);

        enforceManageHealthPermissions(/* message= */ "setHealthPermissionsUserFixedFlagValue");
        UserHandle checkedUser = UserHandle.of(handleIncomingUser(user.getIdentifier()));
        enforceValidPackage(packageName, checkedUser);
        final long token = Binder.clearCallingIdentity();
        try {
            setHealthPermissionsUserFixedFlagValueUnchecked(
                    packageName, checkedUser, permissions, value);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Returns {@code true} if there is at least one granted permission for the provided {@code
     * packageName}, {@code false} otherwise.
     */
    public boolean hasGrantedHealthPermissions(
            @NonNull String packageName, @NonNull UserHandle user) {
        return !getGrantedHealthPermissions(packageName, user).isEmpty();
    }

    /**
     * Returns the date from which an app can read / write health data. See {@link
     * HealthConnectManager#getHealthDataHistoricalAccessStartDate}
     */
    @Nullable
    public Instant getHealthDataStartDateAccess(String packageName, UserHandle user)
            throws IllegalArgumentException {
        Objects.requireNonNull(packageName);
        enforceManageHealthPermissions(/* message= */ "getHealthDataStartDateAccess");
        UserHandle checkedUser = UserHandle.of(handleIncomingUser(user.getIdentifier()));
        enforceValidPackage(packageName, checkedUser);

        Instant grantTimeDate = mFirstGrantTimeManager.getFirstGrantTime(packageName, checkedUser);
        if (grantTimeDate == null) {
            return null;
        }

        return grantTimeDate.minus(GRANT_TIME_TO_START_ACCESS_DATE_PERIOD);
    }

    /**
     * Same as {@link #getHealthDataStartDateAccess(String, UserHandle)} except this method also
     * throws {@link IllegalAccessException} if health permission is in an incorrect state where
     * first grant time can't be fetched.
     */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @NonNull
    public Instant getHealthDataStartDateAccessOrThrow(String packageName, UserHandle user) {
        Instant startDateAccess = getHealthDataStartDateAccess(packageName, user);
        if (startDateAccess == null) {
            throwExceptionIncorrectPermissionState();
        }
        return startDateAccess;
    }

    private void throwExceptionIncorrectPermissionState() {
        throw new IllegalStateException(
                "Incorrect health permission state, likely"
                        + " because the calling application's manifest does not specify handling "
                        + Intent.ACTION_VIEW_PERMISSION_USAGE
                        + " with "
                        + HealthConnectManager.CATEGORY_HEALTH_PERMISSIONS);
    }

    private void addToPriorityListIfRequired(String packageName, String permissionName) {
        if (HealthPermissions.isWritePermission(permissionName)) {
            HealthDataCategoryPriorityHelper.getInstance()
                    .appendToPriorityList(
                            packageName,
                            HealthPermissions.getHealthDataCategoryForWritePermission(
                                    permissionName),
                            mContext,
                            /* isInactiveApp= */ false);
        }
    }

    private void removeFromPriorityListIfRequired(String packageName, String permissionName) {
        if (HealthPermissions.isWritePermission(permissionName)) {
            HealthDataCategoryPriorityHelper.getInstance()
                    .maybeRemoveAppFromPriorityList(
                            packageName,
                            HealthPermissions.getHealthDataCategoryForWritePermission(
                                    permissionName),
                            this,
                            mContext.getUser());
        }
    }

    @NonNull
    private List<String> getGrantedHealthPermissionsUnchecked(
            @NonNull String packageName, @NonNull UserHandle user) {
        PackageInfo packageInfo =
                getPackageInfoUnchecked(
                        packageName,
                        user,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));

        if (packageInfo.requestedPermissions == null) {
            return List.of();
        }

        List<String> grantedHealthPerms = new ArrayList<>(packageInfo.requestedPermissions.length);
        for (int i = 0; i < packageInfo.requestedPermissions.length; i++) {
            String currPerm = packageInfo.requestedPermissions[i];
            if (mHealthPermissions.contains(currPerm)
                    && ((packageInfo.requestedPermissionsFlags[i]
                                    & PackageInfo.REQUESTED_PERMISSION_GRANTED)
                            != 0)) {
                grantedHealthPerms.add(currPerm);
            }
        }
        return grantedHealthPerms;
    }

    @NonNull
    private Map<String, Integer> getHealthPermissionsFlagsUnchecked(
            @NonNull String packageName,
            @NonNull UserHandle user,
            @NonNull List<String> permissions) {
        enforceValidHealthPermissions(packageName, user, permissions);

        Map<String, Integer> result = new ArrayMap<>();

        for (String permission : permissions) {
            result.put(
                    permission, mPackageManager.getPermissionFlags(permission, packageName, user));
        }

        return result;
    }

    private void setHealthPermissionsUserFixedFlagValueUnchecked(
            @NonNull String packageName,
            @NonNull UserHandle user,
            @NonNull List<String> permissions,
            boolean value) {
        enforceValidHealthPermissions(packageName, user, permissions);

        int flagMask = PackageManager.FLAG_PERMISSION_USER_FIXED;
        int flagValues = value ? PackageManager.FLAG_PERMISSION_USER_FIXED : 0;

        for (String permission : permissions) {
            mPackageManager.updatePermissionFlags(
                    permission, packageName, flagMask, flagValues, user);
        }
    }

    private void revokeAllHealthPermissionsUnchecked(
            String packageName, UserHandle user, String reason) {
        List<String> grantedHealthPermissions =
                getGrantedHealthPermissionsUnchecked(packageName, user);
        for (String perm : grantedHealthPermissions) {
            mPackageManager.revokeRuntimePermission(packageName, perm, user, reason);
            mPackageManager.updatePermissionFlags(
                    perm,
                    packageName,
                    MASK_PERMISSION_FLAGS,
                    PackageManager.FLAG_PERMISSION_USER_SET,
                    user);
            removeFromPriorityListIfRequired(packageName, perm);
        }
    }

    private void enforceValidHealthPermission(String permissionName) {
        if (!mHealthPermissions.contains(permissionName)) {
            throw new IllegalArgumentException("invalid health permission");
        }
    }

    private PackageInfo getPackageInfoUnchecked(
            String packageName, UserHandle user, PackageManager.PackageInfoFlags flags) {
        try {
            PackageManager packageManager =
                    mContext.createContextAsUser(user, /* flags= */ 0).getPackageManager();

            return packageManager.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalArgumentException("invalid package", e);
        }
    }

    private void enforceValidPackage(String packageName, UserHandle user) {
        getPackageInfoUnchecked(packageName, user, PackageManager.PackageInfoFlags.of(0));
    }

    private void enforceManageHealthPermissions(String message) {
        mContext.enforceCallingOrSelfPermission(
                HealthPermissions.MANAGE_HEALTH_PERMISSIONS, message);
    }

    private void enforceSupportPermissionsUsageIntent(String packageName, UserHandle userHandle) {
        if (!mPermissionIntentAppsTracker.supportsPermissionUsageIntent(packageName, userHandle)) {
            throw new SecurityException(
                    "Package "
                            + packageName
                            + " for "
                            + userHandle.toString()
                            + " doesn't support health permissions usage intent.");
        }
    }

    /**
     * Checks input user id and converts it to positive id if needed, returns converted user id.
     *
     * @throws java.lang.SecurityException if the caller is affecting different users without
     *     holding the {@link INTERACT_ACROSS_USERS_FULL} permission.
     */
    private int handleIncomingUser(int userId) {
        int callingUserId = UserHandle.getUserHandleForUid(Binder.getCallingUid()).getIdentifier();
        if (userId == callingUserId) {
            return userId;
        }

        boolean canInteractAcrossUsersFull =
                mContext.checkCallingOrSelfPermission(INTERACT_ACROSS_USERS_FULL)
                        == PERMISSION_GRANTED;
        if (canInteractAcrossUsersFull) {
            // If the UserHandle.CURRENT has been passed (negative value),
            // convert it to positive userId.
            if (userId == UserHandle.CURRENT.getIdentifier()) {
                return ActivityManager.getCurrentUser();
            }
            return userId;
        }

        throw new SecurityException(
                "Permission denied. Need to run as either the calling user id ("
                        + callingUserId
                        + "), or with "
                        + INTERACT_ACROSS_USERS_FULL
                        + " permission");
    }

    private void enforceValidHealthPermissions(
            String packageName, UserHandle user, List<String> permissions) {
        PackageInfo packageInfo =
                getPackageInfoUnchecked(
                        packageName,
                        user,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));

        Set<String> requestedPermissions = new ArraySet<>(packageInfo.requestedPermissions);

        for (String permission : permissions) {
            if (!requestedPermissions.contains(permission)) {
                throw new IllegalArgumentException(
                        "undeclared permission " + permission + " for package " + packageName);
            }

            enforceValidHealthPermission(permission);
        }
    }
}
