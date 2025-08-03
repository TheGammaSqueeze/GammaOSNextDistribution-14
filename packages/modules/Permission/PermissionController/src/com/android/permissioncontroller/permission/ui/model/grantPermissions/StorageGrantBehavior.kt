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

import android.Manifest.permission.ACCESS_MEDIA_LOCATION
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.Manifest.permission_group.READ_MEDIA_VISUAL
import android.Manifest.permission_group.STORAGE
import android.os.Build
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.ui.model.DenyButton
import com.android.permissioncontroller.permission.ui.model.Prompt
import com.android.permissioncontroller.permission.utils.KotlinUtils.isPhotoPickerPromptEnabled
import com.android.permissioncontroller.permission.utils.KotlinUtils.isPhotoPickerPromptSupported

/**
 * Storage split from one group (STORAGE) into two (READ_MEDIA_VISUAL and READ_MEDIA_AURAL) in T.
 * There are special dialogs to deal with a pre-T app requesting STORAGE on a post-T device. In U,
 * the READ_MEDIA_VISUAL group was augmented with the option to select photos, which led to several
 * new dialogs to handle that.
 */
object StorageGrantBehavior : GrantBehavior() {
    override fun getPrompt(
        group: LightAppPermGroup,
        requestedPerms: Set<String>,
        isSystemTriggeredPrompt: Boolean
    ): Prompt {
        val appSupportsSplitStoragePermissions = appSupportsSplitStoragePermissions(group)
        if (!SdkLevel.isAtLeastT()) {
            return Prompt.BASIC
        } else if (appSupportsSplitStoragePermissions && group.permGroupName == STORAGE) {
            return Prompt.NO_UI_REJECT_THIS_GROUP
        }

        if (appSupportsSplitStoragePermissions && !shouldShowPhotoPickerPromptForApp(group)) {
            return Prompt.BASIC
        }

        if (!appSupportsSplitStoragePermissions) {
            if (group.packageInfo.targetSdkVersion < Build.VERSION_CODES.Q) {
                return Prompt.STORAGE_SUPERGROUP_PRE_Q
            } else {
                return Prompt.STORAGE_SUPERGROUP_Q_TO_S
            }
        }

        // Else, app supports the new photo picker dialog
        if (requestedPerms.all { it in getPartialGrantPermissions(group) }) {
            // Do not allow apps to request only READ_MEDIA_VISUAL_USER_SELECTED
            return Prompt.NO_UI_REJECT_THIS_GROUP
        }

        val userSelectedPerm = group.permissions[READ_MEDIA_VISUAL_USER_SELECTED]
        if (userSelectedPerm?.isUserFixed == true && userSelectedPerm.isGranted) {
            return Prompt.NO_UI_PHOTO_PICKER_REDIRECT
        }

        if (userSelectedPerm?.isGranted == true) {
            return Prompt.SELECT_MORE_PHOTOS
        } else {
            return Prompt.SELECT_PHOTOS
        }
    }

    override fun getDenyButton(
        group: LightAppPermGroup,
        requestedPerms: Set<String>,
        prompt: Prompt
    ): DenyButton {
        if (prompt == Prompt.SELECT_MORE_PHOTOS) {
            return DenyButton.DONT_SELECT_MORE
        }
        return BasicGrantBehavior.getDenyButton(group, requestedPerms, prompt)
    }

    override fun isGroupFullyGranted(
        group: LightAppPermGroup,
        requestedPerms: Set<String>
    ): Boolean {
        if (!isPhotoPickerPromptSupported() || group.permGroupName != READ_MEDIA_VISUAL) {
            return super.isGroupFullyGranted(group, requestedPerms)
        }

        return group.permissions.values.any {
            it.name !in getPartialGrantPermissions(group) && it.isGranted
        }
    }

    override fun isPermissionFixed(group: LightAppPermGroup, perm: String): Boolean {
        val userSelectedPerm = group.permissions[READ_MEDIA_VISUAL_USER_SELECTED]
        if (
            userSelectedPerm != null && userSelectedPerm.isGranted && userSelectedPerm.isUserFixed
        ) {
            // If the user selected permission is fixed and granted, we immediately show the
            // photo picker, rather than filtering
            return false
        }
        return super.isPermissionFixed(group, perm)
    }

    private fun appSupportsSplitStoragePermissions(group: LightAppPermGroup) =
        SdkLevel.isAtLeastT() && group.packageInfo.targetSdkVersion >= Build.VERSION_CODES.TIRAMISU

    private fun shouldShowPhotoPickerPromptForApp(group: LightAppPermGroup) =
        isPhotoPickerPromptEnabled() &&
            group.permGroupName == READ_MEDIA_VISUAL &&
            (group.packageInfo.targetSdkVersion >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                appSupportsPhotoPicker(group))

    private fun appSupportsPhotoPicker(group: LightAppPermGroup) =
        group.packageInfo.targetSdkVersion >= Build.VERSION_CODES.TIRAMISU &&
            group.permissions[READ_MEDIA_VISUAL_USER_SELECTED]?.isImplicit == false

    private fun getPartialGrantPermissions(group: LightAppPermGroup): Set<String> {
        return if (appSupportsPhotoPicker(group) && shouldShowPhotoPickerPromptForApp(group)) {
            setOf(READ_MEDIA_VISUAL_USER_SELECTED, ACCESS_MEDIA_LOCATION)
        } else {
            setOf(READ_MEDIA_VISUAL_USER_SELECTED)
        }
    }
}
