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

import android.content.Intent
import android.os.Build
import android.os.UserHandle
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ID
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ISSUE_ID
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_USER_HANDLE
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.safetycenter.SafetyCenterConstants.EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY
import com.android.safetycenter.internaldata.SafetyCenterIssueKey

/** Class representing parsed intent extra values for use in [SafetyCenterDashboardFragment] */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
data class ParsedSafetyCenterIntent(
    val safetyCenterIssueKey: SafetyCenterIssueKey? = null,
    val shouldExpandIssuesGroup: Boolean
) {
    companion object {
        @JvmStatic
        fun Intent.toSafetyCenterIntent(): ParsedSafetyCenterIntent {
            val safetySourceId: String? = getStringExtra(EXTRA_SAFETY_SOURCE_ID)
            val safetySourceIssueId: String? = getStringExtra(EXTRA_SAFETY_SOURCE_ISSUE_ID)
            val safetySourceUserHandle: UserHandle? =
                getParcelableExtra(EXTRA_SAFETY_SOURCE_USER_HANDLE, UserHandle::class.java)
            val safetyCenterIssueKey: SafetyCenterIssueKey? =
                createSafetyCenterIssueKey(
                    safetySourceId,
                    safetySourceIssueId,
                    safetySourceUserHandle
                )

            // Check if we've navigated from QS or if focusing on single issue and issues should be
            // expanded
            val shouldExpandIssuesGroup: Boolean =
                getBooleanExtra(EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY, false)

            return ParsedSafetyCenterIntent(safetyCenterIssueKey, shouldExpandIssuesGroup)
        }

        /**
         * Creates [SafetyCenterIssueKey] using the provided values
         *
         * @param safetySourceId source ID for a {@link SafetySourceIssue}
         * @param safetySourceIssueId an issue ID for a {@link SafetySourceIssue}
         * @param safetySourceUserHandle the specific a {@link android.os.UserHandle} associated
         *   with issue
         */
        private fun createSafetyCenterIssueKey(
            safetySourceId: String?,
            safetySourceIssueId: String?,
            safetySourceUserHandle: UserHandle?
        ): SafetyCenterIssueKey? {
            if (safetySourceId == null || safetySourceIssueId == null) {
                return null
            }
            return SafetyCenterIssueKey.newBuilder()
                .setSafetySourceId(safetySourceId)
                .setSafetySourceIssueId(safetySourceIssueId)
                // Default to current user
                .setUserId(safetySourceUserHandle?.identifier ?: UserHandle.myUserId())
                .build()
        }
    }
}
