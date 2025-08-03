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

package com.android.permissioncontroller.permission.ui.auto.dashboard

import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import androidx.annotation.RequiresApi
import androidx.preference.Preference.OnPreferenceClickListener
import com.android.car.ui.preference.CarUiPreference
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.legacy.PermissionUsageDetailsViewModelLegacy
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel

/** Preference that displays a permission usage for an app. */
@RequiresApi(Build.VERSION_CODES.S)
class AutoPermissionHistoryPreference(
    context: Context,
    historyPreferenceData: PermissionUsageDetailsViewModelLegacy.HistoryPreferenceData
) : CarUiPreference(context) {

    init {
        title = historyPreferenceData.preferenceTitle
        summary =
            if (historyPreferenceData.summaryText != null) {
                context.getString(
                    R.string.auto_permission_usage_timeline_summary,
                    DateFormat.getTimeFormat(context).format(historyPreferenceData.accessEndTime),
                    historyPreferenceData.summaryText
                )
            } else {
                DateFormat.getTimeFormat(context).format(historyPreferenceData.accessEndTime)
            }
        if (historyPreferenceData.appIcon != null) {
            icon = historyPreferenceData.appIcon
        }

        onPreferenceClickListener = OnPreferenceClickListener {
            // This Intent should ideally be part of the preference data, and can be consolidated
            // when the Legacy and New viewmodels are merged.
            context.startActivity(
                PermissionUsageDetailsViewModel.createHistoryPreferenceClickIntent(
                    context = context,
                    userHandle = historyPreferenceData.userHandle,
                    packageName = historyPreferenceData.pkgName,
                    permissionGroup = historyPreferenceData.permissionGroup,
                    accessEndTime = historyPreferenceData.accessEndTime,
                    accessStartTime = historyPreferenceData.accessStartTime,
                    showingAttribution = historyPreferenceData.showingAttribution,
                    attributionTags = historyPreferenceData.attributionTags
                )
            )
            true
        }
    }
}
