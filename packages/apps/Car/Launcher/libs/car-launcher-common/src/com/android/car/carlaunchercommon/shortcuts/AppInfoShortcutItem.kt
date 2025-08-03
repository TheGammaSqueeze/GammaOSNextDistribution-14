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

package com.android.car.carlaunchercommon.shortcuts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings
import com.android.car.carlaunchercommon.R
import com.android.car.hidden.apis.HiddenApiAccess.startActivityAsUser
import com.android.car.ui.shortcutspopup.CarUiShortcutsPopup

class AppInfoShortcutItem constructor(
    private val context: Context,
    private val packageName: String,
    private val userHandle: UserHandle
) : CarUiShortcutsPopup.ShortcutItem {
    private companion object {
        private const val PACKAGE_URI_PREFIX = "package:"
    }

    override fun data(): CarUiShortcutsPopup.ItemData {
        return CarUiShortcutsPopup.ItemData(
            R.drawable.ic_app_info,
            context.resources.getString(R.string.app_info_shortcut_label)
        )
    }

    override fun onClick(): Boolean {
        val packageURI = Uri.parse(PACKAGE_URI_PREFIX + packageName)
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            packageURI
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAsUser(context, intent, userHandle)
        return true
    }

    override fun isEnabled(): Boolean {
        return true
    }
}
