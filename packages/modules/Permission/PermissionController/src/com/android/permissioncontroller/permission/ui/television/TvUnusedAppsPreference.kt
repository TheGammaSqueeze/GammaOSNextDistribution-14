/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.television

import android.app.Application
import android.content.Context
import android.os.UserHandle
import androidx.preference.Preference
import com.android.permissioncontroller.permission.ui.RemovablePref
import com.android.permissioncontroller.permission.utils.KotlinUtils

/**
 * A TV-styled preference which represents an app that has been auto revoked. Has the app icon and
 * label, as well as a button to open the app.
 *
 * @param app The current application
 * @param packageName The name of the package whose icon this preference will retrieve
 * @param user The user whose package icon will be retrieved
 * @param context The current context
 */
class TvUnusedAppsPreference(
    app: Application,
    packageName: String,
    user: UserHandle,
    context: Context
) : Preference(context), RemovablePref {

    init {
        icon = KotlinUtils.getBadgedPackageIcon(app, packageName, user)
    }

    override fun setRemoveClickRunnable(runnable: Runnable) {
        // TV Settings don't have secondary icons and actions
    }

    override fun setRemoveComponentEnabled(enabled: Boolean) {
        // TV Settings doesn't have a remove component
    }
}
