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
package com.android.intentresolver.v2

import android.Manifest
import android.Manifest.permission.INTERACT_ACROSS_USERS
import android.Manifest.permission.INTERACT_ACROSS_USERS_FULL
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.PermissionChecker
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import com.android.intentresolver.v2.data.repository.DevicePolicyResources
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG: String = "IntentForwarding"

@Singleton
class IntentForwarding
@Inject
constructor(
    private val resources: DevicePolicyResources,
    private val userManager: UserManager,
    private val packageManager: PackageManager
) {

    fun forwardMessageFor(intent: Intent): String? {
        val contentUserHint = intent.contentUserHint
        if (
            contentUserHint != UserHandle.USER_CURRENT && contentUserHint != UserHandle.myUserId()
        ) {
            val originUserInfo = userManager.getUserInfo(contentUserHint)
            val originIsManaged = originUserInfo?.isManagedProfile ?: false
            val targetIsManaged = userManager.isManagedProfile
            return when {
                originIsManaged && !targetIsManaged -> resources.forwardToPersonalMessage
                !originIsManaged && targetIsManaged -> resources.forwardToWorkMessage
                else -> null
            }
        }
        return null
    }

    private fun isPermissionGranted(permission: String, uid: Int) =
        ActivityManager.checkComponentPermission(
            /* permission = */ permission,
            /* uid = */ uid,
            /* owningUid= */ -1,
            /* exported= */ true
        )

    /**
     * Returns whether the package has the necessary permissions to interact across profiles on
     * behalf of a given user.
     *
     * This means meeting the following condition:
     * * The app's [ApplicationInfo.crossProfile] flag must be true, and at least one of the
     *   following conditions must be fulfilled
     * * `Manifest.permission.INTERACT_ACROSS_USERS_FULL` granted.
     * * `Manifest.permission.INTERACT_ACROSS_USERS` granted.
     * * `Manifest.permission.INTERACT_ACROSS_PROFILES` granted, or the corresponding AppOps
     *   `android:interact_across_profiles` is set to "allow".
     */
    fun canAppInteractAcrossProfiles(context: Context, packageName: String): Boolean {
        val applicationInfo: ApplicationInfo
        try {
            applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Package $packageName does not exist on current user.")
            return false
        }
        if (!applicationInfo.crossProfile) {
            return false
        }

        val packageUid = applicationInfo.uid

        if (isPermissionGranted(INTERACT_ACROSS_USERS_FULL, packageUid) == PERMISSION_GRANTED) {
            return true
        }
        if (isPermissionGranted(INTERACT_ACROSS_USERS, packageUid) == PERMISSION_GRANTED) {
            return true
        }
        return PermissionChecker.checkPermissionForPreflight(
            context,
            Manifest.permission.INTERACT_ACROSS_PROFILES,
            PermissionChecker.PID_UNKNOWN,
            packageUid,
            packageName
        ) == PERMISSION_GRANTED
    }
}
