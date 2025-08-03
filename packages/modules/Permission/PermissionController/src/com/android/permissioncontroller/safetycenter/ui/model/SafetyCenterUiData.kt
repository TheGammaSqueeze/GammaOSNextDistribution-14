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

package com.android.permissioncontroller.safetycenter.ui.model

import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK
import androidx.annotation.RequiresApi
import com.android.safetycenter.internaldata.SafetyCenterBundles.ISSUES_TO_GROUPS_BUNDLE_KEY
import com.android.safetycenter.internaldata.SafetyCenterIds
import com.android.safetycenter.internaldata.SafetyCenterIssueKey

/** UI model representation of Safety Center Data */
data class SafetyCenterUiData(
    val safetyCenterData: SafetyCenterData,
    val resolvedIssues: Map<IssueId, ActionId> = emptyMap()
) {
    @RequiresApi(TIRAMISU)
    fun getMatchingIssue(issueKey: SafetyCenterIssueKey): SafetyCenterIssue? {
        return safetyCenterData.issues.find {
            SafetyCenterIds.issueIdFromString(it.id).safetyCenterIssueKey == issueKey
        }
    }

    /** Returns the [SafetyCenterEntryGroup] corresponding to the provided ID */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    fun getMatchingGroup(groupId: String): SafetyCenterEntryGroup? {
        val entryOrGroups: List<SafetyCenterEntryOrGroup> = safetyCenterData.entriesOrGroups
        val entryGroups = entryOrGroups.mapNotNull { it.entryGroup }
        return entryGroups.find { it.id == groupId }
    }

    /**
     * Returns a list of [SafetyCenterIssue] corresponding to the provided ID. This will be
     * displayed as warning cards on a subpage in Safety Center.
     */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    fun getMatchingIssues(groupId: String): List<SafetyCenterIssue> =
        selectMatchingIssuesForGroup(groupId, safetyCenterData.issues)

    /**
     * Returns a list of dismissed [SafetyCenterIssue] corresponding to the provided ID. This will
     * be displayed as dismissed warning cards on a subpage in Safety Center.
     */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    fun getMatchingDismissedIssues(groupId: String): List<SafetyCenterIssue> =
        selectMatchingIssuesForGroup(groupId, safetyCenterData.visibleDismissedIssues())

    @RequiresApi(UPSIDE_DOWN_CAKE)
    private fun selectMatchingIssuesForGroup(
        groupId: String,
        issues: List<SafetyCenterIssue>
    ): List<SafetyCenterIssue> {
        val issuesToGroups = safetyCenterData.extras.getBundle(ISSUES_TO_GROUPS_BUNDLE_KEY)
        return issues.filter {
            val mappingExists = issuesToGroups?.containsKey(it.id) ?: false
            val matchesInMapping =
                issuesToGroups?.getStringArrayList(it.id)?.contains(groupId) ?: false
            val matchesByDefault = it.groupId == groupId

            if (mappingExists) matchesInMapping else matchesByDefault
        }
    }

    /** Returns the [SafetyCenterData.getDismissedIssues] that are meant to be visible in the UI. */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    fun SafetyCenterData.visibleDismissedIssues() =
        dismissedIssues.filter { it.severityLevel > ISSUE_SEVERITY_LEVEL_OK }
}
