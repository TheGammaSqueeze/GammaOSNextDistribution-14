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

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceFragmentCompat
import com.android.permissioncontroller.permission.ui.handheld.PermissionsCollapsingToolbarBaseFragment

/** Wrapper class over [AppDataSharingUpdatesFragment]. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AppDataSharingUpdatesWrapperFragment : PermissionsCollapsingToolbarBaseFragment() {
    override fun createPreferenceFragment(): PreferenceFragmentCompat {
        return AppDataSharingUpdatesFragment()
    }

    companion object {
        /** Returns a new instance of [AppDataSharingUpdatesWrapperFragment] with arguments. */
        fun newInstance(sessionId: Long): AppDataSharingUpdatesWrapperFragment {
            val instance = AppDataSharingUpdatesWrapperFragment()
            instance.arguments = AppDataSharingUpdatesFragment.createArgs(sessionId)
            return instance
        }
    }
}
