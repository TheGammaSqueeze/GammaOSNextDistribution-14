/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.permissioncontroller.permission.model.livedatatypes

import android.Manifest
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.os.Build
import android.os.UserHandle
import com.android.permissioncontroller.permission.utils.Utils

/**
 * A lightweight version of the AppPermissionGroup data structure. Represents information about a
 * package, and all permissions in a particular permission group this package requests.
 *
 * @param packageInfo Information about the package
 * @param permGroupInfo Information about the permission group
 * @param allPermissions The permissions in the permission group that the package requests
 *   (including restricted ones).
 * @param hasInstallToRuntimeSplit If this group contains a permission that was previously an
 *   install permission, but is currently a runtime permission
 * @param specialLocationGrant If this package is the location provider, or the extra location
 *   package, then the grant state of the group is not determined by the grant state of individual
 *   permissions, but by other system properties
 * @param specialFixedStorageGrant If this package holds the SYSTEM_GALLERY role, and has the
 *   WRITE_MEDIA_IMAGES app op granted, then we should show the grant state of the storage
 *   permissions as system fixed and granted.
 *
 */
data class LightAppPermGroup(
    val packageInfo: LightPackageInfo,
    val permGroupInfo: LightPermGroupInfo,
    val allPermissions: Map<String, LightPermission>,
    val hasInstallToRuntimeSplit: Boolean,
    val specialLocationGrant: Boolean?,
    val specialFixedStorageGrant: Boolean,
) {
    constructor(
        pI: LightPackageInfo,
        pGI: LightPermGroupInfo,
        perms: Map<String, LightPermission>
    ) : this(pI, pGI, perms, false, null, false)

    /** All unrestricted permissions. Usually restricted permissions are ignored */
    val permissions: Map<String, LightPermission> =
        allPermissions.filter { (_, permission) -> !permission.isRestricted }

    /** The package name of this group */
    val packageName = packageInfo.packageName

    /** The permission group name of this group */
    val permGroupName = permGroupInfo.name

    /** The current userHandle of this AppPermGroup. */
    val userHandle: UserHandle = UserHandle.getUserHandleForUid(packageInfo.uid)

    /** The device ID of this group, inferred from LightPackageInfo */
    val deviceId = packageInfo.deviceId

    /**
     * The names of all background permissions in the permission group which are requested by the
     * package.
     */
    val backgroundPermNames = permissions.mapNotNull { it.value.backgroundPermission }

    /** All foreground permissions in the permission group which are requested by the package. */
    val foregroundPermNames
        get() =
            permissions.mapNotNull { (name, _) -> if (name !in backgroundPermNames) name else null }

    val isPlatformPermissionGroup = permGroupInfo.packageName == Utils.OS_PKG

    val foreground =
        AppPermSubGroup(
            permissions.filter { it.key in foregroundPermNames },
            packageInfo,
            isPlatformPermissionGroup,
            specialLocationGrant,
            specialFixedStorageGrant
        )

    val background =
        AppPermSubGroup(
            permissions.filter { it.key in backgroundPermNames },
            packageInfo,
            isPlatformPermissionGroup,
            specialLocationGrant,
            specialFixedStorageGrant
        )

    /** Whether or not this App Permission Group has a permission which has a background mode */
    val hasPermWithBackgroundMode = backgroundPermNames.isNotEmpty()

    /** Whether or not this App Permission Group requests a background permission */
    val hasBackgroundGroup = backgroundPermNames.any { permissions.contains(it) }

    /**
     * Whether this App Permission Group's background and foreground permissions are fixed by policy
     */
    val isPolicyFullyFixed =
        foreground.isPolicyFixed && (!hasBackgroundGroup || background.isPolicyFixed)

    /**
     * Whether this App Permission Group's background permissions are fixed by the system or policy
     */
    val isBackgroundFixed = background.isPolicyFixed || background.isSystemFixed

    /**
     * Whether this App Permission Group's foreground permissions are fixed by the system or policy
     */
    val isForegroundFixed = foreground.isPolicyFixed || foreground.isSystemFixed

    /** Whether or not this group supports runtime permissions */
    val supportsRuntimePerms = packageInfo.targetSdkVersion >= Build.VERSION_CODES.M

    /**
     * Whether this App Permission Group is one-time. 2 cases:
     * 1. If the perm group is not LOCATION, check if any of the permissions is one-time and none of
     *    the granted permissions are not one-time.
     * 2. If the perm group is LOCATION, check if ACCESS_COARSE_LOCATION is one-time.
     */
    val isOneTime =
        (permGroupName != Manifest.permission_group.LOCATION &&
            permissions.any { it.value.isOneTime } &&
            permissions.none { !it.value.isOneTime && it.value.isGranted }) ||
            (permGroupName == Manifest.permission_group.LOCATION &&
                permissions[ACCESS_COARSE_LOCATION]?.isOneTime == true)

    /** Whether any permissions in this group are granted by default (pregrant) */
    val isGrantedByDefault = foreground.isGrantedByDefault || background.isGrantedByDefault

    /** Whether any permissions in this group are granted by being a role holder */
    val isGrantedByRole = foreground.isGrantedByRole || background.isGrantedByRole

    /** Whether any of the permission (foreground/background) is fixed by the system */
    val isSystemFixed = foreground.isSystemFixed || background.isSystemFixed

    /** Whether any of the permission (foreground/background) in this group requires a review */
    val isReviewRequired = foreground.isReviewRequired || background.isReviewRequired

    /** Whether any of the permission (foreground/background) is granted in this permission group */
    var isGranted = foreground.isGranted || background.isGranted

    /** Whether any permissions in this group are user sensitive */
    val isUserSensitive = permissions.any { it.value.isUserSensitive }

    /** Whether any permissions in this group are revoke-when-requested */
    val isRevokeWhenRequested = permissions.any { it.value.isRevokeWhenRequested }

    /** Whether any of this App Permission Groups permissions are fixed by the user */
    val isUserFixed = foreground.isUserFixed || background.isUserFixed

    /** Whether any of this App Permission Group's permissions are set by the user */
    val isUserSet = foreground.isUserSet || background.isUserSet

    /**
     * A subset of the AppPermissionGroup, representing either the background or foreground
     * permissions of the full group.
     *
     * @param permissions The permissions contained within this subgroup, a subset of those
     *   contained in the full group
     * @param isPlatformPermissionGroup Whether this is a platform permission group
     * @param specialLocationGrant Whether this is a special location package
     * @param specialFixedStorageGrant Whether this is a special storage grant
     */
    data class AppPermSubGroup
    internal constructor(
        private val permissions: Map<String, LightPermission>,
        private val packageInfo: LightPackageInfo,
        private val isPlatformPermissionGroup: Boolean,
        private val specialLocationGrant: Boolean?,
        private val specialFixedStorageGrant: Boolean
    ) {
        /** Whether any of this App Permission SubGroup's permissions are granted */
        val isGranted =
            specialLocationGrant
                ?: specialFixedStorageGrant || permissions.any {
                    val mayGrantByPlatformOrSystem =
                        !isPlatformPermissionGroup || it.value.isPlatformOrSystem
                    it.value.isGranted && mayGrantByPlatformOrSystem
                }

        /**
         * Whether this App Permission SubGroup should be treated as granted. This means either:
         * 1) At least one permission was granted excluding auto-granted permissions (i.e., granted
         *    during install time with flag RevokeWhenRequested.) Or,
         * 2) All permissions were auto-granted (all permissions are all granted and all
         *    RevokeWhenRequested.)
         */
        val allowFullGroupGrant =
            specialLocationGrant
                ?: (permissions.any {
                    val mayGrantByPlatformOrSystem =
                        !isPlatformPermissionGroup || it.value.isPlatformOrSystem
                    it.value.allowFullGroupGrant && mayGrantByPlatformOrSystem
                } || permissions.all { it.value.isGranted && it.value.isRevokeWhenRequested })

        /** Whether any of this App Permission SubGroup's permissions are granted by default */
        val isGrantedByDefault = permissions.any { it.value.isGrantedByDefault }

        /**
         * Whether at least one of this App Permission SubGroup's permissions is one-time and none
         * of the granted permissions are not one-time.
         */
        val isOneTime =
            permissions.any { it.value.isOneTime } &&
                permissions.none { it.value.isGranted && !it.value.isOneTime }

        /**
         * Whether any of this App Permission Subgroup's foreground permissions are fixed by policy
         */
        val isPolicyFixed = permissions.any { it.value.isPolicyFixed }

        /** Whether any of this App Permission Subgroup's permissions are fixed by the system */
        val isSystemFixed = permissions.any { it.value.isSystemFixed } || specialFixedStorageGrant

        /** Whether any of this App Permission Subgroup's permissions are fixed by the user */
        val isUserFixed = permissions.any { it.value.isUserFixed }

        /** Whether any of this App Permission Subgroup's permissions are set by the user */
        val isUserSet = permissions.any { it.value.isUserSet }

        /** whether review is required or not for the permission group */
        val isReviewRequired = permissions.any { it.value.isReviewRequired }

        /**
         * Whether any of this App Permission Subgroup's permissions are set by the role of this app
         */
        val isGrantedByRole = permissions.any { it.value.isGrantedByRole }

        private val hasPreRuntimePerm = permissions.any { (_, perm) -> !perm.isRuntimeOnly }

        private val hasInstantPerm = permissions.any { (_, perm) -> perm.isInstantPerm }

        /** Whether or not any permissions in this App Permission Subgroup can be granted */
        val isGrantable =
            (!packageInfo.isInstantApp || hasInstantPerm) &&
                (packageInfo.targetSdkVersion >= Build.VERSION_CODES.M || hasPreRuntimePerm)
    }
}
