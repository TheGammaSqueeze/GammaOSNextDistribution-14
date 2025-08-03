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

package com.android.permissioncontroller.tests.mocking.safetycenter.ui.model

import android.content.Context
import android.os.Build
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterStatus
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS
import android.safetycenter.SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS
import android.safetycenter.SafetyCenterStatus.REFRESH_STATUS_NONE
import androidx.test.filters.SdkSuppress
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.ui.model.StatusUiData
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
class StatusUiDataTest {

    @Mock private lateinit var mockContext: Context

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun copyForPendingActions_setsCorrectPendingActionsValue() {
        val copiedWithPendingActions =
            StatusUiData(STATUS, hasPendingActions = false).copyForPendingActions(true)
        val copiedWithoutPendingActions =
            StatusUiData(STATUS, hasPendingActions = true).copyForPendingActions(false)

        assertThat(copiedWithPendingActions.hasPendingActions).isTrue()
        assertThat(copiedWithoutPendingActions.hasPendingActions).isFalse()
    }

    @Test
    fun getTitle_returnsTitle() {
        assertThat(StatusUiData(STATUS).title).isEqualTo(STATUS.title)
        assertThat(StatusUiData(ANOTHER_STATUS).title).isEqualTo(ANOTHER_STATUS.title)
        assertThat(StatusUiData(SafetyCenterData(STATUS, listOf(), listOf(), listOf())).title)
            .isEqualTo(STATUS.title)
    }

    @Test
    fun getOriginalSummary_returnsOriginalSummary() {
        assertThat(StatusUiData(STATUS).originalSummary).isEqualTo(STATUS.summary)
        assertThat(StatusUiData(ANOTHER_STATUS).originalSummary).isEqualTo(ANOTHER_STATUS.summary)
        assertThat(
                StatusUiData(SafetyCenterData(STATUS, listOf(), listOf(), listOf())).originalSummary
            )
            .isEqualTo(STATUS.summary)
    }

    @Test
    fun getSeverityLevel_returnsSeverityLevel() {
        assertThat(StatusUiData(STATUS).severityLevel).isEqualTo(STATUS.severityLevel)
        assertThat(StatusUiData(ANOTHER_STATUS).severityLevel)
            .isEqualTo(ANOTHER_STATUS.severityLevel)
        assertThat(
                StatusUiData(SafetyCenterData(STATUS, listOf(), listOf(), listOf())).severityLevel
            )
            .isEqualTo(STATUS.severityLevel)
    }

    @Test
    fun getSummary_withoutPendingActions_returnsOriginalSummary() {
        val dataWithoutPendingActions = StatusUiData(STATUS, hasPendingActions = false)

        val actualSummary = dataWithoutPendingActions.getSummary(mockContext)

        assertThat(actualSummary).isEqualTo(dataWithoutPendingActions.originalSummary)
    }

    @Test
    fun getSummary_withPendingActions_returnsQsSummary() {
        val expectedSummary = "a quick settings summary"
        whenever(mockContext.getString(R.string.safety_center_qs_status_summary))
            .thenReturn(expectedSummary)

        val actualSummary = StatusUiData(STATUS, hasPendingActions = true).getSummary(mockContext)

        assertThat(actualSummary).isEqualTo(expectedSummary)
    }

    @Test
    fun getContentDescription_returnsContentDescription() {
        val expectedContentDescription = "a content description"
        whenever(
                mockContext.getString(
                    R.string.safety_status_preference_title_and_summary_content_description,
                    STATUS.title,
                    STATUS.summary
                )
            )
            .thenReturn(expectedContentDescription)

        val actualContentDescription = StatusUiData(STATUS).getContentDescription(mockContext)

        assertThat(actualContentDescription).isEqualTo(expectedContentDescription)
    }

    @Test
    fun fromSafetyCenterData_withIssues_hasIssuesIsTrue() {
        assertThat(StatusUiData(DATA_WITH_ISSUES).hasIssues).isTrue()
    }

    @Test
    fun fromSafetyCenterData_withoutIssues_hasIssuesIsFalse() {
        assertThat(StatusUiData(DATA_WITHOUT_ISSUES).hasIssues).isFalse()
    }

    @Test
    fun hasPendingActions_defaultsFalse() {
        assertThat(StatusUiData(STATUS).hasPendingActions).isFalse()
        assertThat(StatusUiData(DATA_WITH_ISSUES).hasPendingActions).isFalse()
    }

    @Test
    fun getStatusImageResId_severityOk() {
        assertThat(uiDataForSeverity(OVERALL_SEVERITY_LEVEL_OK).statusImageResId)
            .isEqualTo(R.drawable.safety_status_info)
    }

    @Test
    fun getStatusImageResId_severityUnknown() {
        assertThat(uiDataForSeverity(OVERALL_SEVERITY_LEVEL_UNKNOWN).statusImageResId)
            .isEqualTo(R.drawable.safety_status_info)
    }
    @Test
    fun getStatusImageResId_severityRecommendation() {
        assertThat(uiDataForSeverity(OVERALL_SEVERITY_LEVEL_RECOMMENDATION).statusImageResId)
            .isEqualTo(R.drawable.safety_status_recommendation)
    }
    @Test
    fun getStatusImageResId_severityWarning() {
        assertThat(uiDataForSeverity(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING).statusImageResId)
            .isEqualTo(R.drawable.safety_status_warn)
    }

    @Test
    fun isRefreshInProgress_dataFetch_isTrue() {
        assertThat(
                uiDataForRefreshStatus(REFRESH_STATUS_DATA_FETCH_IN_PROGRESS).isRefreshInProgress
            )
            .isTrue()
    }

    @Test
    fun isRefreshInProgress_fullRescan_isTrue() {
        assertThat(
                uiDataForRefreshStatus(REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS).isRefreshInProgress
            )
            .isTrue()
    }

    @Test
    fun isRefreshInProgress_none_isFalse() {
        assertThat(uiDataForRefreshStatus(REFRESH_STATUS_NONE).isRefreshInProgress).isFalse()
    }

    @Test
    fun shouldShowRescanButton_severityOk_noIssues_noPendingActions_isTrue() {
        assertThat(
                StatusUiData(
                        statusForSeverity(OVERALL_SEVERITY_LEVEL_OK),
                        hasIssues = false,
                        hasPendingActions = false
                    )
                    .shouldShowRescanButton()
            )
            .isTrue()
    }

    @Test
    fun shouldShowRescanButton_severityUnknown_noIssues_noPendingActions_isTrue() {
        assertThat(
                StatusUiData(
                        statusForSeverity(OVERALL_SEVERITY_LEVEL_UNKNOWN),
                        hasIssues = false,
                        hasPendingActions = false
                    )
                    .shouldShowRescanButton()
            )
            .isTrue()
    }

    @Test
    fun shouldShowRescanButton_hasIssues_isFalse() {
        assertThat(
                StatusUiData(
                        statusForSeverity(OVERALL_SEVERITY_LEVEL_OK),
                        hasIssues = true,
                        hasPendingActions = false
                    )
                    .shouldShowRescanButton()
            )
            .isFalse()
    }

    @Test
    fun shouldShowRescanButton_hasPendingActions_isFalse() {
        assertThat(
                StatusUiData(
                        statusForSeverity(OVERALL_SEVERITY_LEVEL_OK),
                        hasIssues = false,
                        hasPendingActions = true
                    )
                    .shouldShowRescanButton()
            )
            .isFalse()
    }

    @Test
    fun shouldShowRescanButton_severityNotOkOrUnknown_isFalse() {
        for (severity in
            listOf(
                OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING,
                OVERALL_SEVERITY_LEVEL_RECOMMENDATION
            )) {
            assertThat(
                    StatusUiData(
                            statusForSeverity(severity),
                            hasIssues = false,
                            hasPendingActions = false
                        )
                        .shouldShowRescanButton()
                )
                .isFalse()
        }
    }

    private companion object {
        val STATUS =
            SafetyCenterStatus.Builder("a title", "a summary")
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
                .setRefreshStatus(REFRESH_STATUS_NONE)
                .build()

        val ANOTHER_STATUS =
            SafetyCenterStatus.Builder("another title", "another summary")
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
                .setRefreshStatus(REFRESH_STATUS_DATA_FETCH_IN_PROGRESS)
                .build()

        val ISSUE = SafetyCenterIssue.Builder("iSsUe_Id", "issue title", "issue summary").build()

        val DATA_WITH_ISSUES = SafetyCenterData(STATUS, listOf(ISSUE), listOf(), listOf())
        val DATA_WITHOUT_ISSUES = SafetyCenterData(STATUS, listOf(), listOf(), listOf())

        fun statusForSeverity(severityLevel: Int) =
            SafetyCenterStatus.Builder(STATUS).setSeverityLevel(severityLevel).build()

        fun uiDataForSeverity(severityLevel: Int) = StatusUiData(statusForSeverity(severityLevel))

        fun uiDataForRefreshStatus(refreshStatus: Int) =
            StatusUiData(SafetyCenterStatus.Builder(STATUS).setRefreshStatus(refreshStatus).build())
    }
}
