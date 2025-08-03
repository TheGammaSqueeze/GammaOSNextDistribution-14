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

package com.android.permissioncontroller.permission.ui.handheld.v34

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R

/** A preference to show messaging below the page title in the [AppDataSharingUpdatesFragment]. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AppDataSharingDetailsPreference : Preference {
    constructor(c: Context) : super(c)
    constructor(c: Context, a: AttributeSet) : super(c, a)
    constructor(c: Context, a: AttributeSet, attr: Int) : super(c, a, attr)
    constructor(c: Context, a: AttributeSet, attr: Int, res: Int) : super(c, a, attr, res)

    init {
        layoutResource = R.layout.app_data_sharing_details_preference
    }

    /** Whether to show the no updates message. */
    var showNoUpdates: Boolean = false
        set(value) {
            field = value
            notifyChanged()
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        val noUpdatesMessage = holder.findViewById(R.id.no_updates_message)!!
        noUpdatesMessage.isVisible = showNoUpdates
        super.onBindViewHolder(holder)
    }
}
