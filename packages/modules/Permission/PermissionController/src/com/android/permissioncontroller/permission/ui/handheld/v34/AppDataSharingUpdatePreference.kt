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

package com.android.permissioncontroller.permission.ui.handheld.v34

import android.app.Application
import android.content.Context
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.UserHandle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.handheld.SmartIconLoadPackagePermissionPreference

/**
 * A preference with package label, summary, app icon and settings gear icon, to represent updates
 * in data sharing for one app.
 */
@RequiresApi(UPSIDE_DOWN_CAKE)
class AppDataSharingUpdatePreference(
    app: Application,
    packageName: String,
    user: UserHandle,
    context: Context
) : SmartIconLoadPackagePermissionPreference(app, packageName, user, context) {

    init {
        layoutResource = R.layout.app_data_sharing_settings_preference
    }

    /** [View.OnClickListener] for the preference. */
    var preferenceClick: View.OnClickListener? = null
        set(value) {
            field = value
            notifyChanged()
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.itemView.setOnClickListener(preferenceClick)
    }
}
