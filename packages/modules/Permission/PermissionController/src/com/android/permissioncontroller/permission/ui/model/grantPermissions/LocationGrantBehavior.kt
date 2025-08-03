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

package com.android.permissioncontroller.permission.ui.model.grantPermissions

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.os.Build
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.ui.model.DenyButton
import com.android.permissioncontroller.permission.ui.model.Prompt
import com.android.permissioncontroller.permission.utils.KotlinUtils

/**
 * The Location Grant behavior is the same as the Background group behavior, up until S. After S,
 * the fine and coarse location permissions were allowed to be granted separately, and this created
 * a new set of grant dialogs.
 */
object LocationGrantBehavior : GrantBehavior() {
    override fun getPrompt(
        group: LightAppPermGroup,
        requestedPerms: Set<String>,
        isSystemTriggeredPrompt: Boolean
    ): Prompt {
        val backgroundPrompt = BackgroundGrantBehavior.getPrompt(group, requestedPerms)
        val requestsBackground = requestedPerms.any { it in group.backgroundPermNames }
        val coarseGranted = group.permissions[ACCESS_COARSE_LOCATION]?.isGranted == true
        return if (!supportsLocationAccuracy(group) || requestsBackground) {
            backgroundPrompt
        } else if (requestedPerms.contains(ACCESS_FINE_LOCATION)) {
            if (coarseGranted) {
                Prompt.LOCATION_FINE_UPGRADE
            } else if (isFineLocationHighlighted(group)) {
                Prompt.LOCATION_TWO_BUTTON_FINE_HIGHLIGHT
            } else {
                Prompt.LOCATION_TWO_BUTTON_COARSE_HIGHLIGHT
            }
        } else if (requestedPerms.contains(ACCESS_COARSE_LOCATION) && !coarseGranted) {
            Prompt.LOCATION_COARSE_ONLY
        } else {
            backgroundPrompt
        }
    }

    override fun getDenyButton(
        group: LightAppPermGroup,
        requestedPerms: Set<String>,
        prompt: Prompt
    ): DenyButton {
        return BackgroundGrantBehavior.getDenyButton(group, requestedPerms, prompt)
    }

    override fun isGroupFullyGranted(
        group: LightAppPermGroup,
        requestedPerms: Set<String>
    ): Boolean {
        val requestsBackground = requestedPerms.any { it in group.backgroundPermNames }
        if (!supportsLocationAccuracy(group) || requestsBackground) {
            return BackgroundGrantBehavior.isGroupFullyGranted(group, requestedPerms)
        }
        return isForegroundFullyGranted(group, requestedPerms)
    }

    override fun isForegroundFullyGranted(
        group: LightAppPermGroup,
        requestedPerms: Set<String>
    ): Boolean {
        if (!supportsLocationAccuracy(group)) {
            return BackgroundGrantBehavior.isForegroundFullyGranted(group, requestedPerms)
        }

        if (requestedPerms.contains(ACCESS_FINE_LOCATION)) {
            return group.permissions[ACCESS_FINE_LOCATION]?.isGranted == true
        }

        return group.foreground.allowFullGroupGrant
    }

    override fun isPermissionFixed(group: LightAppPermGroup, perm: String): Boolean {
        if (!supportsLocationAccuracy(group) || perm != ACCESS_COARSE_LOCATION) {
            return BackgroundGrantBehavior.isPermissionFixed(group, perm)
        }

        // If the location group is user fixed but ACCESS_COARSE_LOCATION is not, then
        // ACCESS_FINE_LOCATION must be user fixed. In this case ACCESS_COARSE_LOCATION
        // is still grantable.
        return group.foreground.isUserFixed && group.permissions[perm]?.isUserFixed == true
    }

    private fun supportsLocationAccuracy(group: LightAppPermGroup): Boolean {
        return KotlinUtils.isLocationAccuracyEnabled() &&
            group.packageInfo.targetSdkVersion >= Build.VERSION_CODES.S
    }

    private fun isFineLocationHighlighted(group: LightAppPermGroup): Boolean {
        // Steps to decide location accuracy default state
        // 1. If none of the FINE and COARSE isSelectedLocationAccuracy
        //    flags is set, then use default precision from device config.
        // 2. Otherwise set to whichever isSelectedLocationAccuracy is true.
        val coarseLocationPerm = group.allPermissions[ACCESS_COARSE_LOCATION]
        val fineLocationPerm = group.allPermissions[ACCESS_FINE_LOCATION]
        return if (
            coarseLocationPerm?.isSelectedLocationAccuracy == false &&
                fineLocationPerm?.isSelectedLocationAccuracy == false
        ) {
            // default location precision is true, indicates FINE
            return true
        } else {
            fineLocationPerm?.isSelectedLocationAccuracy == true
        }
    }
}
