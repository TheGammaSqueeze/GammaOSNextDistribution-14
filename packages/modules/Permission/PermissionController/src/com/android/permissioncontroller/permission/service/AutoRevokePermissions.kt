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

@file:JvmName("AutoRevokePermissions")

package com.android.permissioncontroller.permission.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.FLAG_PERMISSION_AUTO_REVOKED
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET
import android.os.UserHandle
import android.os.UserManager
import android.permission.PermissionManager
import androidx.annotation.MainThread
import com.android.permissioncontroller.Constants.INVALID_SESSION_ID
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_UNUSED_APP_PERMISSION_REVOKED
import com.android.permissioncontroller.hibernation.getUnusedThresholdMs
import com.android.permissioncontroller.permission.data.AutoRevokedPackagesLiveData
import com.android.permissioncontroller.permission.data.LightAppPermGroupLiveData
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.permission.utils.application
import com.android.permissioncontroller.permission.utils.forEachInParallel
import com.android.permissioncontroller.permission.utils.updatePermissionFlags
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers.Main

private const val LOG_TAG = "AutoRevokePermissions"
const val DEBUG_AUTO_REVOKE = true

val AUTO_REVOKE_EXEMPT_PERMISSIONS =
    listOf(Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS)

private val SERVER_LOG_ID =
    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_UNUSED_APP_PERMISSION_REVOKED

/**
 * Revoke granted app permissions for apps that should be auto-revoked
 *
 * @return list of packages that successfully had their permissions revoked
 */
@MainThread
suspend fun revokeAppPermissions(
    apps: Map<UserHandle, List<LightPackageInfo>>,
    context: Context,
    sessionId: Long = INVALID_SESSION_ID
): Set<Pair<String, UserHandle>> {
    val revokedApps = mutableSetOf<Pair<String, UserHandle>>()
    val userManager = context.getSystemService(UserManager::class.java)

    val permissionManager = context.getSystemService(PermissionManager::class.java)!!
    val splitPermissionIndex = SplitPermissionIndex(permissionManager.splitPermissions)

    for ((user, userApps) in apps) {
        if (userManager == null || !userManager.isUserUnlocked(user)) {
            DumpableLog.w(LOG_TAG, "Skipping $user - locked direct boot state")
            continue
        }

        val pkgPermChanges =
            PermissionChangeStorageImpl.getInstance().loadEvents().associateBy { it.packageName }
        // For each autorevoke-eligible app...
        userApps.forEachInParallel(Main) forEachInParallelOuter@{ pkg: LightPackageInfo ->
            if (pkg.grantedPermissions.isEmpty()) {
                if (DEBUG_AUTO_REVOKE) {
                    DumpableLog.i(LOG_TAG, "${pkg.packageName}: no granted permissions")
                }
                return@forEachInParallelOuter
            }
            val packageName = pkg.packageName
            val pkgPermChange = pkgPermChanges[packageName]
            val now = System.currentTimeMillis()
            if (pkgPermChange != null && now - pkgPermChange.eventTime < getUnusedThresholdMs()) {
                if (DEBUG_AUTO_REVOKE) {
                    DumpableLog.i(
                        LOG_TAG,
                        "Not revoking because permissions were changed " +
                            "recently for package $packageName"
                    )
                }
                return@forEachInParallelOuter
            }
            val targetSdk = pkg.targetSdkVersion
            val pkgPermGroups: Map<String, List<String>>? =
                PackagePermissionsLiveData[packageName, user].getInitializedValue()

            if (pkgPermGroups == null || pkgPermGroups.isEmpty()) {
                if (DEBUG_AUTO_REVOKE) {
                    DumpableLog.i(LOG_TAG, "$packageName: no permission groups found.")
                }
                return@forEachInParallelOuter
            }

            if (DEBUG_AUTO_REVOKE) {
                DumpableLog.i(LOG_TAG, "$packageName: perm groups: ${pkgPermGroups.keys}.")
            }

            // Determine which permGroups are revocable
            val revocableGroups = mutableSetOf<String>()
            for (groupName in pkgPermGroups.keys) {
                if (groupName == PackagePermissionsLiveData.NON_RUNTIME_NORMAL_PERMS) {
                    continue
                }
                if (groupName !in PermissionMapping.getPlatformPermissionGroups()) {
                    continue
                }
                val group: LightAppPermGroup =
                    LightAppPermGroupLiveData[packageName, groupName, user].getInitializedValue()
                        ?: continue
                val fixed = group.isBackgroundFixed || group.isForegroundFixed
                val granted =
                    group.permissions.any { (_, perm) ->
                        perm.isGranted && perm.name !in AUTO_REVOKE_EXEMPT_PERMISSIONS
                    }
                if (
                    !fixed &&
                        granted &&
                        !group.isGrantedByDefault &&
                        !group.isGrantedByRole &&
                        !group.isRevokeWhenRequested &&
                        group.isUserSensitive
                ) {
                    revocableGroups.add(groupName)
                }
            }

            if (DEBUG_AUTO_REVOKE) {
                DumpableLog.i(LOG_TAG, "$packageName: initial revocable groups: $revocableGroups")
            }

            // Mark any groups that split from an install-time permission as unrevocable
            for (fromPerm in
                pkgPermGroups[PackagePermissionsLiveData.NON_RUNTIME_NORMAL_PERMS] ?: emptyList()) {
                for (toGroup in
                    splitPermissionIndex.getPermToGroupSplitsFrom(fromPerm, targetSdk)) {
                    revocableGroups.remove(toGroup)
                }
            }

            // For each unrevocable group, mark all groups that it splits from and to as unrevocable
            for (groupName in pkgPermGroups.keys) {
                if (!revocableGroups.contains(groupName)) {
                    for (fromGroup in
                        splitPermissionIndex.getGroupToGroupSplitsTo(groupName, targetSdk)) {
                        revocableGroups.remove(fromGroup)
                    }
                    for (toGroup in
                        splitPermissionIndex.getGroupToGroupSplitsFrom(groupName, targetSdk)) {
                        revocableGroups.remove(toGroup)
                    }
                }
            }

            if (DEBUG_AUTO_REVOKE) {
                DumpableLog.i(LOG_TAG, "$packageName: final revocable groups: $revocableGroups")
            }
            // For each revocable group, revoke all of its permissions
            val anyPermsRevoked = AtomicBoolean(false)
            pkgPermGroups.entries
                .filter { revocableGroups.contains(it.key) }
                .forEachInParallel(Main) forEachInParallelInner@{ (groupName, _) ->
                    val group: LightAppPermGroup =
                        LightAppPermGroupLiveData[packageName, groupName, user]
                            .getInitializedValue()!!

                    val revocablePermissions = group.permissions.keys.toList()

                    if (revocablePermissions.isEmpty()) {
                        if (DEBUG_AUTO_REVOKE) {
                            DumpableLog.i(LOG_TAG, "$packageName: revocable permissions empty")
                        }
                        return@forEachInParallelInner
                    }

                    if (DEBUG_AUTO_REVOKE) {
                        DumpableLog.i(LOG_TAG, "revokeUnused $packageName - $revocablePermissions")
                    }

                    val uid = group.packageInfo.uid
                    for (permName in revocablePermissions) {
                        PermissionControllerStatsLog.write(
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED,
                            sessionId,
                            uid,
                            packageName,
                            permName,
                            false,
                            SERVER_LOG_ID,
                            /* permission_rationale_shown = */ false
                        )
                    }

                    if (DEBUG_AUTO_REVOKE) {
                        DumpableLog.i(LOG_TAG, "revoking $packageName - $revocablePermissions")
                        DumpableLog.i(LOG_TAG, "State pre revocation: ${group.allPermissions}")
                    }
                    anyPermsRevoked.compareAndSet(false, true)

                    val bgRevokedState =
                        KotlinUtils.revokeBackgroundRuntimePermissions(
                            context.application,
                            group,
                            userFixed = false,
                            oneTime = false,
                            filterPermissions = revocablePermissions
                        )
                    if (DEBUG_AUTO_REVOKE) {
                        DumpableLog.i(
                            LOG_TAG,
                            "Bg state post revocation: ${bgRevokedState.allPermissions}"
                        )
                    }
                    val fgRevokedState =
                        KotlinUtils.revokeForegroundRuntimePermissions(
                            context.application,
                            group,
                            userFixed = false,
                            oneTime = false,
                            filterPermissions = revocablePermissions
                        )
                    if (DEBUG_AUTO_REVOKE) {
                        DumpableLog.i(
                            LOG_TAG,
                            "Fg state post revocation: ${fgRevokedState.allPermissions}"
                        )
                    }

                    for (permission in revocablePermissions) {
                        context.packageManager.updatePermissionFlags(
                            permission,
                            packageName,
                            user,
                            FLAG_PERMISSION_AUTO_REVOKED to true,
                            FLAG_PERMISSION_USER_SET to false
                        )
                    }
                }

            if (anyPermsRevoked.get()) {
                synchronized(revokedApps) { revokedApps.add(packageName to user) }
            }
        }

        if (DEBUG_AUTO_REVOKE) {
            synchronized(revokedApps) {
                DumpableLog.i(
                    LOG_TAG,
                    "Done auto-revoke for user ${user.identifier} - revoked $revokedApps"
                )
            }
        }
    }
    if (revokedApps.isNotEmpty()) {
        AutoRevokedPackagesLiveData.update()
    }
    return revokedApps
}
