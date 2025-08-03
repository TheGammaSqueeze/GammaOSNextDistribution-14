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
package com.android.permissioncontroller.safetycenter.ui

import android.safetycenter.SafetyCenterEntry
import android.util.Log
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.SafetyCenterConstants.PRIVACY_SOURCES_GROUP_ID

internal object SeverityIconPicker {

    private val TAG = SeverityIconPicker::class.java.simpleName

    @JvmStatic
    fun selectIconResId(id: String, severityLevel: Int, severityUnspecifiedIconType: Int): Int {
        if (id == PRIVACY_SOURCES_GROUP_ID) {
            return R.drawable.ic_privacy
        }

        when (severityLevel) {
            SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN -> return R.drawable.ic_safety_null_state
            SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED ->
                return selectSeverityUnspecifiedIconResId(severityUnspecifiedIconType)
            SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK -> return R.drawable.ic_safety_info
            SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION ->
                return R.drawable.ic_safety_recommendation
            SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING ->
                return R.drawable.ic_safety_warn
        }
        Log.e(
            TAG,
            String.format("Unexpected SafetyCenterEntry.EntrySeverityLevel: %s", severityLevel)
        )
        return R.drawable.ic_safety_null_state
    }

    private fun selectSeverityUnspecifiedIconResId(severityUnspecifiedIconType: Int): Int {
        when (severityUnspecifiedIconType) {
            SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON ->
                return R.drawable.ic_safety_empty
            SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY -> return R.drawable.ic_privacy
            SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION ->
                return R.drawable.ic_safety_null_state
        }
        Log.e(
            TAG,
            String.format(
                "Unexpected SafetyCenterEntry.SeverityNoneIconType: %s",
                severityUnspecifiedIconType
            )
        )
        return R.drawable.ic_safety_null_state
    }
}
