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

import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.ui.model.DenyButton
import com.android.permissioncontroller.permission.ui.model.Prompt

/** The base behavior all grant behavior objects inherit from. */
abstract class GrantBehavior {
    val LOG_TAG = "GrantPermissionsViewModel"

    /**
     * Get the prompt type for the given set of requested permissions
     *
     * @param group The LightAppPermGroup representing the state of the app and its permissions
     * @param requestedPerms The permissions requested by the app, after filtering
     * @param isSystemTriggeredPrompt Whether the prompt was triggered by the system, instead of by
     *   the app.
     */
    abstract fun getPrompt(
        group: LightAppPermGroup,
        requestedPerms: Set<String>,
        isSystemTriggeredPrompt: Boolean = false
    ): Prompt

    /**
     * Get the deny button type for the given set of requested permissions. This is separate from
     * the prompt, because the same prompt can have multiple deny behaviors, based on if the user
     * has seen it before.
     *
     * @param group The LightAppPermGroup representing the state of the app and its permissions
     * @param requestedPerms The permissions requested by the app, after filtering
     * @param prompt The prompt determined by the behavior.
     */
    abstract fun getDenyButton(
        group: LightAppPermGroup,
        requestedPerms: Set<String>,
        prompt: Prompt
    ): DenyButton

    /**
     * Whether the group is considered "fully granted". If it is, any remaining permissions in the
     * group not already granted will be granted.
     */
    open fun isGroupFullyGranted(group: LightAppPermGroup, requestedPerms: Set<String>): Boolean {
        return group.foreground.allowFullGroupGrant
    }

    /**
     * Whether or not all foreground permissions in the group are granted. Only different in groups
     * that respect background and foreground permissions.
     */
    open fun isForegroundFullyGranted(
        group: LightAppPermGroup,
        requestedPerms: Set<String>
    ): Boolean {
        return isGroupFullyGranted(group, requestedPerms)
    }

    /**
     * Whether or not the permission should be considered as "user fixed". If it is, it is removed
     * from the request.
     */
    open fun isPermissionFixed(group: LightAppPermGroup, perm: String): Boolean {
        return group.isUserFixed
    }
}
