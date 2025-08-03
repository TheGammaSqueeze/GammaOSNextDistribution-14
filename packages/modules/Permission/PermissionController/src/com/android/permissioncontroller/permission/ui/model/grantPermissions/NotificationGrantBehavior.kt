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

import android.os.Build.VERSION_CODES.TIRAMISU
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.ui.model.DenyButton
import com.android.permissioncontroller.permission.ui.model.Prompt

/**
 * The Notification permission behavior is similar to basic, except:
 *
 * It can be triggered by the system. If it's system triggered we only show it until the user makes
 * one decision. We don't make the user show it twice.
 *
 * It can't be explicitly requested from apps that don't yet target android T. If they try, we
 * remove it entirely from the request, do not return a result, and take no action on it.
 */
object NotificationGrantBehavior : GrantBehavior() {
    override fun getPrompt(
        group: LightAppPermGroup,
        requestedPerms: Set<String>,
        isSystemTriggeredPrompt: Boolean
    ): Prompt {
        val isPreT = group.packageInfo.targetSdkVersion < TIRAMISU
        if (!isSystemTriggeredPrompt && isPreT) {
            // Apps targeting below T cannot manually request Notifications
            return Prompt.NO_UI_FILTER_THIS_GROUP
        } else if (isPreT && group.isUserSet) {
            // If the user has seen the system-triggered prompt once, don't show it again
            return Prompt.NO_UI_FILTER_THIS_GROUP
        }
        return BasicGrantBehavior.getPrompt(group, requestedPerms, isSystemTriggeredPrompt)
    }

    override fun getDenyButton(
        group: LightAppPermGroup,
        requestedPerms: Set<String>,
        prompt: Prompt
    ): DenyButton {
        return BasicGrantBehavior.getDenyButton(group, requestedPerms, prompt)
    }
}
