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

package com.android.permissioncontroller.tests.mocking.safetycenter.ui.model

import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.Bundle
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetyCenterStatus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterUiData
import com.android.safetycenter.internaldata.SafetyCenterBundles.ISSUES_TO_GROUPS_BUNDLE_KEY
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class SafetyCenterUiDataTest {

    @Test
    fun getMatchingGroup_validMatchingGroup_returnsExpectedEntryGroup() {
        val matchingGroup = createSafetyCenterEntryGroup(MATCHING_GROUP_ID)
        val nonMatchingGroup = createSafetyCenterEntryGroup(NON_MATCHING_GROUP_ID)
        val safetyCenterData =
            createSafetyCenterData(entryGroups = listOf(matchingGroup, nonMatchingGroup))

        val result = SafetyCenterUiData(safetyCenterData).getMatchingGroup(MATCHING_GROUP_ID)

        assertThat(result).isEqualTo(matchingGroup)
    }

    @Test
    fun getMatchingGroup_noMatchingGroup_returnsNull() {
        val nonMatchingGroup = createSafetyCenterEntryGroup(NON_MATCHING_GROUP_ID)
        val safetyCenterData = createSafetyCenterData(entryGroups = listOf(nonMatchingGroup))

        val result = SafetyCenterUiData(safetyCenterData).getMatchingGroup(MATCHING_GROUP_ID)

        assertThat(result).isNull()
    }

    @Test
    fun getMatchingIssues_defaultMatchingIssue_noExtras_returnsListOfIssues() {
        val defaultMatchingIssue = createSafetyCenterIssue("id1", MATCHING_GROUP_ID)
        val nonMatchingIssue = createSafetyCenterIssue("id2", NON_MATCHING_GROUP_ID)
        val safetyCenterData =
            createSafetyCenterData(issues = listOf(defaultMatchingIssue, nonMatchingIssue))

        val result = SafetyCenterUiData(safetyCenterData).getMatchingIssues(MATCHING_GROUP_ID)

        assertThat(result).containsExactly(defaultMatchingIssue)
    }

    @Test
    fun getMatchingIssues_defaultMatchingIssue_unrelatedExtras_returnsListOfIssues() {
        val defaultMatchingIssue = createSafetyCenterIssue("id1", MATCHING_GROUP_ID)
        val nonMatchingIssue = createSafetyCenterIssue("id2", NON_MATCHING_GROUP_ID)
        val safetyCenterData =
            createSafetyCenterData(
                issues = listOf(defaultMatchingIssue, nonMatchingIssue),
                extras =
                    createSafetyCenterExtras(
                        Bundle().apply {
                            putStringArrayList(
                                nonMatchingIssue.id,
                                arrayListOf(NON_MATCHING_GROUP_ID)
                            )
                        }
                    )
            )

        val result = SafetyCenterUiData(safetyCenterData).getMatchingIssues(MATCHING_GROUP_ID)

        assertThat(result).containsExactly(defaultMatchingIssue)
    }

    @Test
    fun getMatchingIssues_mappingMatchingIssue_returnsListOfIssues() {
        val mappingMatchingIssue = createSafetyCenterIssue("id1", NON_MATCHING_GROUP_ID)
        val nonMatchingIssue = createSafetyCenterIssue("id2", NON_MATCHING_GROUP_ID)
        val safetyCenterData =
            createSafetyCenterData(
                issues = listOf(mappingMatchingIssue, nonMatchingIssue),
                extras =
                    createSafetyCenterExtras(
                        Bundle().apply {
                            putStringArrayList(
                                mappingMatchingIssue.id,
                                arrayListOf(MATCHING_GROUP_ID)
                            )
                        }
                    )
            )

        val result = SafetyCenterUiData(safetyCenterData).getMatchingIssues(MATCHING_GROUP_ID)

        assertThat(result).containsExactly(mappingMatchingIssue)
    }

    @Test
    fun getMatchingIssues_noDefaultMatchingIssue_returnsEmptyList() {
        val nonMatchingIssue = createSafetyCenterIssue("id1", NON_MATCHING_GROUP_ID)
        val dismissedIssue = createSafetyCenterIssue("id2", MATCHING_GROUP_ID)
        val safetyCenterData =
            createSafetyCenterData(
                issues = listOf(nonMatchingIssue),
                dismissedIssues = listOf(dismissedIssue)
            )

        val result = SafetyCenterUiData(safetyCenterData).getMatchingIssues(MATCHING_GROUP_ID)

        assertThat(result).isEmpty()
    }

    @Test
    fun getMatchingDismissedIssues_defaultMatchingDismissedIssue_returnsListOfDismissedIssues() {
        val defaultMatchingDismissedIssue = createSafetyCenterIssue("id1", MATCHING_GROUP_ID)
        val nonMatchingDismissedIssue = createSafetyCenterIssue("id2", NON_MATCHING_GROUP_ID)
        val safetyCenterData =
            createSafetyCenterData(
                dismissedIssues = listOf(defaultMatchingDismissedIssue, nonMatchingDismissedIssue)
            )

        val result =
            SafetyCenterUiData(safetyCenterData).getMatchingDismissedIssues(MATCHING_GROUP_ID)

        assertThat(result).containsExactly(defaultMatchingDismissedIssue)
    }

    @Test
    fun getMatchingDismissedIssues_defaultMatchingDismissedIssue2_returnsListOfDismissedIssues() {
        val defaultMatchingDismissedIssue = createSafetyCenterIssue("id1", MATCHING_GROUP_ID)
        val nonMatchingDismissedIssue = createSafetyCenterIssue("id2", NON_MATCHING_GROUP_ID)
        val safetyCenterData =
            createSafetyCenterData(
                dismissedIssues = listOf(defaultMatchingDismissedIssue, nonMatchingDismissedIssue),
                extras =
                    createSafetyCenterExtras(
                        Bundle().apply {
                            putStringArrayList(
                                nonMatchingDismissedIssue.id,
                                arrayListOf(NON_MATCHING_GROUP_ID)
                            )
                        }
                    )
            )

        val result =
            SafetyCenterUiData(safetyCenterData).getMatchingDismissedIssues(MATCHING_GROUP_ID)

        assertThat(result).containsExactly(defaultMatchingDismissedIssue)
    }

    @Test
    fun getMatchingDismissedIssues_mappingMatchingDismissedIssue_returnsListOfDismissedIssues() {
        val mappingMatchingDismissedIssue = createSafetyCenterIssue("id1", NON_MATCHING_GROUP_ID)
        val nonMatchingDismissedIssue = createSafetyCenterIssue("id2", NON_MATCHING_GROUP_ID)
        val safetyCenterData =
            createSafetyCenterData(
                dismissedIssues = listOf(mappingMatchingDismissedIssue, nonMatchingDismissedIssue),
                extras =
                    createSafetyCenterExtras(
                        Bundle().apply {
                            putStringArrayList(
                                mappingMatchingDismissedIssue.id,
                                arrayListOf(MATCHING_GROUP_ID)
                            )
                        }
                    )
            )

        val result =
            SafetyCenterUiData(safetyCenterData).getMatchingDismissedIssues(MATCHING_GROUP_ID)

        assertThat(result).containsExactly(mappingMatchingDismissedIssue)
    }

    @Test
    fun getMatchingDismissedIssues_noDefaultMatchingDismissedIssue_returnsEmptyList() {
        val nonMatchingDismissedIssue = createSafetyCenterIssue("id1", NON_MATCHING_GROUP_ID)
        val nonDismissedIssue = createSafetyCenterIssue("id2", MATCHING_GROUP_ID)
        val safetyCenterData =
            createSafetyCenterData(
                issues = listOf(nonDismissedIssue),
                dismissedIssues = listOf(nonMatchingDismissedIssue)
            )

        val result =
            SafetyCenterUiData(safetyCenterData).getMatchingDismissedIssues(MATCHING_GROUP_ID)

        assertThat(result).isEmpty()
    }

    @Test
    fun getMatchingDismissedIssues_doesntReturnGreenIssues() {
        val greenDismissedIssue =
            createSafetyCenterIssue(
                "id1",
                MATCHING_GROUP_ID,
                severityLevel = ISSUE_SEVERITY_LEVEL_OK
            )
        val yellowDismissedIssue =
            createSafetyCenterIssue(
                "id2",
                MATCHING_GROUP_ID,
                severityLevel = ISSUE_SEVERITY_LEVEL_RECOMMENDATION
            )
        val redDismissedIssue =
            createSafetyCenterIssue(
                "id3",
                MATCHING_GROUP_ID,
                severityLevel = ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING
            )
        val nonMatchingDismissedIssue = createSafetyCenterIssue("id4", NON_MATCHING_GROUP_ID)
        val safetyCenterData =
            createSafetyCenterData(
                dismissedIssues =
                    listOf(
                        redDismissedIssue,
                        yellowDismissedIssue,
                        greenDismissedIssue,
                        nonMatchingDismissedIssue
                    ),
            )

        val result =
            SafetyCenterUiData(safetyCenterData).getMatchingDismissedIssues(MATCHING_GROUP_ID)

        assertThat(result).containsExactly(redDismissedIssue, yellowDismissedIssue).inOrder()
    }

    private companion object {
        const val MATCHING_GROUP_ID = "matching_group_id"
        const val NON_MATCHING_GROUP_ID = "non_matching_group_id"

        fun createSafetyCenterData(
            issues: List<SafetyCenterIssue> = listOf(),
            entryGroups: List<SafetyCenterEntryGroup> = listOf(),
            dismissedIssues: List<SafetyCenterIssue> = listOf(),
            extras: Bundle = Bundle()
        ): SafetyCenterData {
            val safetyCenterStatus =
                SafetyCenterStatus.Builder("status title", "status summary").build()
            val builder = SafetyCenterData.Builder(safetyCenterStatus)
            for (issue in issues) {
                builder.addIssue(issue)
            }
            for (group in entryGroups) {
                builder.addEntryOrGroup(SafetyCenterEntryOrGroup(group))
            }
            for (dismissedIssue in dismissedIssues) {
                builder.addDismissedIssue(dismissedIssue)
            }
            builder.setExtras(extras)
            return builder.build()
        }

        fun createSafetyCenterEntryGroup(groupId: String) =
            SafetyCenterEntryGroup.Builder(groupId, "group title").build()

        fun createSafetyCenterIssue(
            issueId: String,
            groupId: String,
            severityLevel: Int = ISSUE_SEVERITY_LEVEL_RECOMMENDATION
        ) =
            SafetyCenterIssue.Builder(issueId, "issue title", "issue summary")
                .setSeverityLevel(severityLevel)
                .setGroupId(groupId)
                .build()

        fun createSafetyCenterExtras(issuesToGroupsMapping: Bundle) =
            Bundle().apply { putBundle(ISSUES_TO_GROUPS_BUNDLE_KEY, issuesToGroupsMapping) }
    }
}
