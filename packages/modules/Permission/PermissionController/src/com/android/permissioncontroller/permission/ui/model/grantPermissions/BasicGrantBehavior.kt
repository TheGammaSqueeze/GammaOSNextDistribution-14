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

/** A basic group. Shows "allow" and "deny", does not allow fixed permissions to be re-requested */
object BasicGrantBehavior : GrantBehavior() {

    override fun getPrompt(
        group: LightAppPermGroup,
        requestedPerms: Set<String>,
        isSystemTriggeredPrompt: Boolean
    ): Prompt {
        return Prompt.BASIC
    }

    override fun getDenyButton(
        group: LightAppPermGroup,
        requestedPerms: Set<String>,
        prompt: Prompt
    ): DenyButton {
        if (prompt in noDenyButtonPrompts) {
            return DenyButton.NONE
        }
        if (group.isUserSet) {
            return DenyButton.DENY_DONT_ASK_AGAIN
        }
        return DenyButton.DENY
    }

    // A list of prompts without any deny behavior
    private val noDenyButtonPrompts =
        listOf(
            Prompt.NO_UI_SETTINGS_REDIRECT,
            Prompt.NO_UI_PHOTO_PICKER_REDIRECT,
            Prompt.NO_UI_HEALTH_REDIRECT,
            Prompt.NO_UI_REJECT_THIS_GROUP,
            Prompt.NO_UI_REJECT_ALL_GROUPS,
            Prompt.NO_UI_FILTER_THIS_GROUP
        )
}
