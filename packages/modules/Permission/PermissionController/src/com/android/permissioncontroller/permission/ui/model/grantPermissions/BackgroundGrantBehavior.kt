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

import android.os.Build
import android.permission.PermissionManager
import android.util.Log
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.ui.model.DenyButton
import com.android.permissioncontroller.permission.ui.model.Prompt
import com.android.permissioncontroller.permission.utils.PermissionMapping

/**
 * This behavior handles groups that respect the difference between foreground and background
 * access. At present, this includes all one-time permissions, in addition to those with explicit
 * background permissions.
 *
 * The behavior is split based on the target SDK when the app split into foreground and background
 * (or android R, whichever is newer). If the app targets prior to the split, we show a dialog with
 * a link to permission settings. Once the app targets after the split, we no longer allow the app
 * to request background and foreground at the same time. If they try, we reject it. An app has to
 * request foreground, get it granted, then request background only, we send the user to settings.
 */
object BackgroundGrantBehavior : GrantBehavior() {

    private val splitPermissionSdkMap = buildMap {
        for (splitPerm in
            PermissionControllerApplication.get()
                .getSystemService(PermissionManager::class.java)!!
                .splitPermissions) {
            put(splitPerm.splitPermission, splitPerm.targetSdk)
        }
    }

    // Redundant "if" conditions are suppressed, because the conditions are clearer
    @Suppress("KotlinConstantConditions")
    override fun getPrompt(
        group: LightAppPermGroup,
        requestedPerms: Set<String>,
        isSystemTriggeredPrompt: Boolean
    ): Prompt {
        val requestsBg = hasBgPerms(group, requestedPerms)
        val requestsFg = requestedPerms.any { it !in group.backgroundPermNames }
        val isOneTimeGroup = PermissionMapping.supportsOneTimeGrant(group.permGroupName)
        val isFgGranted = group.foreground.allowFullGroupGrant
        val isFgOneTime = group.foreground.isOneTime
        val splitSdk = getSdkGroupWasSplitToBg(requestedPerms)
        val isAppIsOlderThanSplitToBg = group.packageInfo.targetSdkVersion < splitSdk

        if (!requestsBg && !isOneTimeGroup) {
            return Prompt.FG_ONLY
        } else if (!requestsBg) {
            return Prompt.ONE_TIME_FG
        }

        if (requestsBg && !requestsFg && !isFgGranted) {
            Log.w(
                LOG_TAG,
                "Cannot grant ${group.permGroupName} as the foreground permissions" +
                    " are not requested or already granted."
            )
            return Prompt.NO_UI_REJECT_THIS_GROUP
        }

        return if (isAppIsOlderThanSplitToBg) {
            getPromptForOlderApp(isOneTimeGroup, requestsFg, isFgGranted, isFgOneTime)
        } else {
            getPromptForNewerApp(group.permGroupName, splitSdk, requestsFg, isFgGranted)
        }
    }

    /**
     * Get the prompt for an app that targets before the sdk level where the permission group was
     * split into foreground and background.
     */
    private fun getPromptForOlderApp(
        isOneTimeGroup: Boolean,
        requestsFg: Boolean,
        isFgGranted: Boolean,
        isFgOneTime: Boolean
    ): Prompt {
        if (requestsFg && !isFgGranted) {
            if (isOneTimeGroup) {
                return Prompt.SETTINGS_LINK_WITH_OT
            }
            return Prompt.SETTINGS_LINK_FOR_BG
        }

        if (isFgGranted) {
            if (isFgOneTime) {
                return Prompt.OT_UPGRADE_SETTINGS_LINK
            }
            return Prompt.UPGRADE_SETTINGS_LINK
        }
        return Prompt.NO_UI_REJECT_ALL_GROUPS
    }

    /**
     * Get the prompt for an app that targets at least the sdk level where the permission group was
     * split into foreground and background.
     */
    private fun getPromptForNewerApp(
        groupName: String,
        splitSdk: Int,
        requestsFg: Boolean,
        isFgGranted: Boolean
    ): Prompt {
        if (!requestsFg && isFgGranted) {
            return Prompt.NO_UI_SETTINGS_REDIRECT
        }

        if (requestsFg) {
            Log.e(
                LOG_TAG,
                "For SDK $splitSdk+ apps requesting $groupName, " +
                    "background permissions must be requested alone after foreground permissions " +
                    "are already granted"
            )
            return Prompt.NO_UI_REJECT_ALL_GROUPS
        } else if (!isFgGranted) {
            Log.e(
                LOG_TAG,
                "For SDK $splitSdk+ apps requesting, $groupName, " +
                    "background permissions must be requested after foreground permissions are " +
                    "already granted"
            )
            Prompt.NO_UI_REJECT_THIS_GROUP
        }
        return Prompt.NO_UI_REJECT_ALL_GROUPS
    }

    override fun getDenyButton(
        group: LightAppPermGroup,
        requestedPerms: Set<String>,
        prompt: Prompt
    ): DenyButton {
        val basicDenyBehavior = BasicGrantBehavior.getDenyButton(group, requestedPerms, prompt)
        if (prompt == Prompt.UPGRADE_SETTINGS_LINK || prompt == Prompt.OT_UPGRADE_SETTINGS_LINK) {
            if (basicDenyBehavior == DenyButton.DENY) {
                return if (prompt == Prompt.UPGRADE_SETTINGS_LINK) {
                    DenyButton.NO_UPGRADE
                } else {
                    DenyButton.NO_UPGRADE_OT
                }
            }
            return if (prompt == Prompt.UPGRADE_SETTINGS_LINK) {
                DenyButton.NO_UPGRADE_AND_DONT_ASK_AGAIN
            } else {
                DenyButton.NO_UPGRADE_AND_DONT_ASK_AGAIN_OT
            }
        }
        return basicDenyBehavior
    }

    override fun isGroupFullyGranted(
        group: LightAppPermGroup,
        requestedPerms: Set<String>
    ): Boolean {
        return (!hasBgPerms(group, requestedPerms) || group.background.allowFullGroupGrant) &&
            group.foreground.allowFullGroupGrant
    }

    override fun isForegroundFullyGranted(
        group: LightAppPermGroup,
        requestedPerms: Set<String>
    ): Boolean {
        return group.foreground.allowFullGroupGrant
    }

    override fun isPermissionFixed(group: LightAppPermGroup, perm: String): Boolean {
        if (perm in group.backgroundPermNames) {
            return group.background.isUserFixed
        }
        return group.foreground.isUserFixed
    }

    private fun hasBgPerms(group: LightAppPermGroup, requestedPerms: Set<String>): Boolean {
        return requestedPerms.any { it in group.backgroundPermNames }
    }

    private fun getSdkGroupWasSplitToBg(requestedPerms: Set<String>): Int {
        val splitSdks = requestedPerms.mapNotNull { perm -> splitPermissionSdkMap[perm] }

        if (splitSdks.isEmpty()) {
            // If there was no split found, assume the split happened in R. This applies to
            // Mic and Camera, which technically split in S, but were treated as foreground-only
            // in R
            return Build.VERSION_CODES.R
        }
        // The background permission request UI changed in R, so if a split happened before R,
        // treat it as if it happened in R
        return maxOf(splitSdks.min(), Build.VERSION_CODES.R)
    }
}
