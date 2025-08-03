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
import com.android.permissioncontroller.permission.utils.Utils

/** Health permissions always redirect to the health connect UI. */
object HealthGrantBehavior : GrantBehavior() {
    override fun getPrompt(
        group: LightAppPermGroup,
        requestedPerms: Set<String>,
        isSystemTriggeredPrompt: Boolean
    ): Prompt {
        return if (Utils.isHealthPermissionUiEnabled()) {
            Prompt.NO_UI_HEALTH_REDIRECT
        } else {
            Prompt.NO_UI_REJECT_THIS_GROUP
        }
    }

    override fun getDenyButton(
        group: LightAppPermGroup,
        requestedPerms: Set<String>,
        prompt: Prompt
    ): DenyButton {
        return DenyButton.NONE
    }

    override fun isGroupFullyGranted(
        group: LightAppPermGroup,
        requestedPerms: Set<String>
    ): Boolean {
        return requestedPerms.all { group.permissions[it]?.isGranted != false }
    }

    override fun isPermissionFixed(group: LightAppPermGroup, perm: String): Boolean {
        return group.permissions[perm]?.isUserFixed == true
    }
}
