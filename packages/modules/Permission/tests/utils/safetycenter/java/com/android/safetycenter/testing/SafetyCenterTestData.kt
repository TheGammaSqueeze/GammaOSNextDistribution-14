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

package com.android.safetycenter.testing

import android.app.PendingIntent
import android.content.Context
import android.icu.text.MessageFormat
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.Bundle
import android.os.UserHandle
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetyCenterStatus
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN
import android.util.ArrayMap
import androidx.annotation.RequiresApi
import com.android.modules.utils.build.SdkLevel
import com.android.safetycenter.internaldata.SafetyCenterEntryId
import com.android.safetycenter.internaldata.SafetyCenterIds
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId
import com.android.safetycenter.internaldata.SafetyCenterIssueId
import com.android.safetycenter.internaldata.SafetyCenterIssueKey
import com.android.safetycenter.resources.SafetyCenterResourcesApk
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_GROUP_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.CRITICAL_ISSUE_ACTION_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.CRITICAL_ISSUE_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.INFORMATION_ISSUE_ACTION_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.INFORMATION_ISSUE_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.ISSUE_TYPE_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.RECOMMENDATION_ISSUE_ACTION_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.RECOMMENDATION_ISSUE_ID
import java.util.Locale

/**
 * A class that provides [SafetyCenterData] objects and associated constants to facilitate asserting
 * on specific Safety Center states in SafetyCenter for testing.
 */
@RequiresApi(TIRAMISU)
class SafetyCenterTestData(context: Context) {

    private val safetyCenterResourcesApk = SafetyCenterResourcesApk.forTests(context)
    private val safetySourceTestData = SafetySourceTestData(context)

    /**
     * The [SafetyCenterStatus] used when the overall status is unknown and no scan is in progress.
     */
    val safetyCenterStatusUnknown: SafetyCenterStatus
        get() =
            SafetyCenterStatus.Builder(
                    safetyCenterResourcesApk.getStringByName(
                        "overall_severity_level_ok_review_title"
                    ),
                    safetyCenterResourcesApk.getStringByName(
                        "overall_severity_level_ok_review_summary"
                    )
                )
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_UNKNOWN)
                .build()

    /**
     * Returns a [SafetyCenterStatus] with one alert and the given [statusResource] and
     * [overallSeverityLevel].
     */
    fun safetyCenterStatusOneAlert(
        statusResource: String,
        overallSeverityLevel: Int
    ): SafetyCenterStatus = safetyCenterStatusNAlerts(statusResource, overallSeverityLevel, 1)

    /**
     * Returns a [SafetyCenterStatus] with [numAlerts] and the given [statusResource] and
     * [overallSeverityLevel].
     */
    fun safetyCenterStatusNAlerts(
        statusResource: String,
        overallSeverityLevel: Int,
        numAlerts: Int,
    ): SafetyCenterStatus =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesApk.getStringByName(statusResource),
                getAlertString(numAlerts)
            )
            .setSeverityLevel(overallSeverityLevel)
            .build()

    /**
     * Returns an information [SafetyCenterStatus] that has "Tip(s) available" as a summary for the
     * given [numTipIssues].
     */
    fun safetyCenterStatusTips(
        numTipIssues: Int,
    ): SafetyCenterStatus =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesApk.getStringByName("overall_severity_level_ok_title"),
                getIcuPluralsString("overall_severity_level_tip_summary", numTipIssues)
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
            .build()

    /**
     * Returns an information [SafetyCenterStatus] that has "Action(s) taken" as a summary for the
     * given [numAutomaticIssues].
     */
    fun safetyCenterStatusActionsTaken(
        numAutomaticIssues: Int,
    ): SafetyCenterStatus =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesApk.getStringByName("overall_severity_level_ok_title"),
                getIcuPluralsString(
                    "overall_severity_level_action_taken_summary",
                    numAutomaticIssues
                )
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
            .build()

    /**
     * Returns the [SafetyCenterStatus] used when the overall status is critical and no scan is in
     * progress for the given number of alerts.
     */
    fun safetyCenterStatusCritical(numAlerts: Int) =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesApk.getStringByName(
                    "overall_severity_level_critical_safety_warning_title"
                ),
                getAlertString(numAlerts)
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    /**
     * Returns a [SafetyCenterEntry] builder with a grey icon (for unknown severity), the summary
     * generally used for sources of the [SafetyCenterTestConfigs], and a pending intent that
     * redirects to [TestActivity] for the given source, user id, and title.
     */
    fun safetyCenterEntryDefaultBuilder(
        sourceId: String,
        userId: Int = UserHandle.myUserId(),
        title: CharSequence = "OK",
        pendingIntent: PendingIntent? =
            safetySourceTestData.createTestActivityRedirectPendingIntent()
    ) =
        SafetyCenterEntry.Builder(entryId(sourceId, userId), title)
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
            .setSummary("OK")
            .setPendingIntent(pendingIntent)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)

    /**
     * Returns a [SafetyCenterEntry] with a grey icon (for unknown severity), the summary generally
     * used for sources of the [SafetyCenterTestConfigs], and a pending intent that redirects to
     * Safety Center for the given source, user id, and title.
     */
    fun safetyCenterEntryDefault(
        sourceId: String,
        userId: Int = UserHandle.myUserId(),
        title: CharSequence = "OK",
        pendingIntent: PendingIntent? =
            safetySourceTestData.createTestActivityRedirectPendingIntent()
    ) = safetyCenterEntryDefaultBuilder(sourceId, userId, title, pendingIntent).build()

    /**
     * Returns a [SafetyCenterEntry] builder with no icon, the summary generally used for sources of
     * the [SafetyCenterTestConfigs], and a pending intent that redirects to [TestActivity] for the
     * given source, user id, and title.
     */
    fun safetyCenterEntryDefaultStaticBuilder(
        sourceId: String,
        userId: Int = UserHandle.myUserId(),
        title: CharSequence = "OK"
    ) =
        SafetyCenterEntry.Builder(entryId(sourceId, userId), title)
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
            .setSummary("OK")
            .setPendingIntent(
                safetySourceTestData.createTestActivityRedirectPendingIntent(explicit = false)
            )
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON)

    /**
     * Returns a [SafetyCenterEntry] with a grey icon (for unknown severity), a refresh error
     * summary, and a pending intent that redirects to [TestActivity] for the given source, user id,
     * and title.
     */
    fun safetyCenterEntryError(sourceId: String) =
        safetyCenterEntryDefaultBuilder(sourceId).setSummary(getRefreshErrorString(1)).build()

    /**
     * Returns a disabled [SafetyCenterEntry] with a grey icon (for unspecified severity), a
     * standard summary, and a standard title for the given source and pending intent.
     */
    fun safetyCenterEntryUnspecified(
        sourceId: String,
        pendingIntent: PendingIntent? =
            safetySourceTestData.createTestActivityRedirectPendingIntent()
    ) =
        SafetyCenterEntry.Builder(entryId(sourceId), "Unspecified title")
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
            .setSummary("Unspecified summary")
            .setPendingIntent(pendingIntent)
            .setEnabled(false)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
            .build()

    /**
     * Returns a [SafetyCenterEntry] builder with a green icon (for ok severity), a standard
     * summary, and a pending intent that redirects to [TestActivity] for the given source, user id,
     * and title.
     */
    fun safetyCenterEntryOkBuilder(
        sourceId: String,
        userId: Int = UserHandle.myUserId(),
        title: CharSequence = "Ok title"
    ) =
        SafetyCenterEntry.Builder(entryId(sourceId, userId), title)
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_OK)
            .setSummary("Ok summary")
            .setPendingIntent(safetySourceTestData.createTestActivityRedirectPendingIntent())
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)

    /**
     * Returns a [SafetyCenterEntry] with a green icon (for ok severity), a standard summary, and a
     * pending intent that redirects to [TestActivity] for the given source, user id, and title.
     */
    fun safetyCenterEntryOk(
        sourceId: String,
        userId: Int = UserHandle.myUserId(),
        title: CharSequence = "Ok title"
    ) = safetyCenterEntryOkBuilder(sourceId, userId, title).build()

    /**
     * Returns a [SafetyCenterEntry] with a yellow icon (for recommendation severity), a standard
     * title, and a pending intent that redirects to [TestActivity] for the given source and
     * summary.
     */
    fun safetyCenterEntryRecommendation(
        sourceId: String,
        summary: String = "Recommendation summary"
    ) =
        SafetyCenterEntry.Builder(entryId(sourceId), "Recommendation title")
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .setSummary(summary)
            .setPendingIntent(safetySourceTestData.createTestActivityRedirectPendingIntent())
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
            .build()

    /**
     * Returns a [SafetyCenterEntry] with a red icon (for critical severity), a standard title, a
     * standard summary, and a pending intent that redirects to [TestActivity] for the given source.
     */
    fun safetyCenterEntryCritical(sourceId: String) =
        SafetyCenterEntry.Builder(entryId(sourceId), "Critical title")
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
            .setSummary("Critical summary")
            .setPendingIntent(safetySourceTestData.createTestActivityRedirectPendingIntent())
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
            .build()

    /**
     * Returns an information [SafetyCenterIssue] for the given source and user id that is
     * consistent with information [SafetySourceIssue]s used in [SafetySourceTestData].
     */
    fun safetyCenterIssueInformation(
        sourceId: String,
        userId: Int = UserHandle.myUserId(),
        attributionTitle: String? = "OK",
        groupId: String? = SINGLE_SOURCE_GROUP_ID
    ) =
        SafetyCenterIssue.Builder(
                issueId(sourceId, INFORMATION_ISSUE_ID, userId = userId),
                "Information issue title",
                "Information issue summary"
            )
            .setSeverityLevel(ISSUE_SEVERITY_LEVEL_OK)
            .setShouldConfirmDismissal(false)
            .setActions(
                listOf(
                    SafetyCenterIssue.Action.Builder(
                            issueActionId(
                                sourceId,
                                INFORMATION_ISSUE_ID,
                                INFORMATION_ISSUE_ACTION_ID,
                                userId
                            ),
                            "Review",
                            safetySourceTestData.createTestActivityRedirectPendingIntent()
                        )
                        .build()
                )
            )
            .apply {
                if (SdkLevel.isAtLeastU()) {
                    setAttributionTitle(attributionTitle)
                    setGroupId(groupId)
                }
            }
            .build()

    /**
     * Returns a recommendation [SafetyCenterIssue] for the given source and user id that is
     * consistent with recommendation [SafetySourceIssue]s used in [SafetySourceTestData].
     */
    fun safetyCenterIssueRecommendation(
        sourceId: String,
        userId: Int = UserHandle.myUserId(),
        attributionTitle: String? = "OK",
        groupId: String? = SINGLE_SOURCE_GROUP_ID,
        confirmationDialog: Boolean = false
    ) =
        SafetyCenterIssue.Builder(
                issueId(sourceId, RECOMMENDATION_ISSUE_ID, userId = userId),
                "Recommendation issue title",
                "Recommendation issue summary"
            )
            .setSeverityLevel(ISSUE_SEVERITY_LEVEL_RECOMMENDATION)
            .setActions(
                listOf(
                    SafetyCenterIssue.Action.Builder(
                            issueActionId(
                                sourceId,
                                RECOMMENDATION_ISSUE_ID,
                                RECOMMENDATION_ISSUE_ACTION_ID,
                                userId
                            ),
                            "See issue",
                            safetySourceTestData.createTestActivityRedirectPendingIntent()
                        )
                        .apply {
                            if (confirmationDialog && SdkLevel.isAtLeastU()) {
                                setConfirmationDialogDetails(
                                    SafetyCenterIssue.Action.ConfirmationDialogDetails(
                                        "Confirmation title",
                                        "Confirmation text",
                                        "Confirmation yes",
                                        "Confirmation no"
                                    )
                                )
                            }
                        }
                        .build()
                )
            )
            .apply {
                if (SdkLevel.isAtLeastU()) {
                    setAttributionTitle(attributionTitle)
                    setGroupId(groupId)
                }
            }
            .build()

    /**
     * Returns a critical [SafetyCenterIssue] for the given source and user id that is consistent
     * with critical [SafetySourceIssue]s used in [SafetySourceTestData].
     */
    fun safetyCenterIssueCritical(
        sourceId: String,
        isActionInFlight: Boolean = false,
        userId: Int = UserHandle.myUserId(),
        attributionTitle: String? = "OK",
        groupId: String? = SINGLE_SOURCE_GROUP_ID
    ) =
        SafetyCenterIssue.Builder(
                issueId(sourceId, CRITICAL_ISSUE_ID, userId = userId),
                "Critical issue title",
                "Critical issue summary"
            )
            .setSeverityLevel(ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING)
            .setActions(
                listOf(
                    SafetyCenterIssue.Action.Builder(
                            issueActionId(
                                sourceId,
                                CRITICAL_ISSUE_ID,
                                CRITICAL_ISSUE_ACTION_ID,
                                userId
                            ),
                            "Solve issue",
                            safetySourceTestData.criticalIssueActionPendingIntent
                        )
                        .setWillResolve(true)
                        .setIsInFlight(isActionInFlight)
                        .build()
                )
            )
            .apply {
                if (SdkLevel.isAtLeastU()) {
                    setAttributionTitle(attributionTitle)
                    setGroupId(groupId)
                }
            }
            .build()

    /**
     * Returns the [overall_severity_n_alerts_summary] string formatted for the given number of
     * alerts.
     */
    fun getAlertString(numberOfAlerts: Int): String =
        getIcuPluralsString("overall_severity_n_alerts_summary", numberOfAlerts)

    /** Returns the [refresh_error] string formatted for the given number of error entries. */
    fun getRefreshErrorString(numberOfErrorEntries: Int): String =
        getIcuPluralsString("refresh_error", numberOfErrorEntries)

    private fun getIcuPluralsString(name: String, count: Int, vararg formatArgs: Any): String {
        val messageFormat =
            MessageFormat(
                safetyCenterResourcesApk.getStringByName(name, formatArgs),
                Locale.getDefault()
            )
        val arguments = ArrayMap<String, Any>()
        arguments["count"] = count
        return messageFormat.format(arguments)
    }

    companion object {
        /** The default [SafetyCenterData] returned by the Safety Center APIs. */
        val DEFAULT: SafetyCenterData =
            SafetyCenterData(
                SafetyCenterStatus.Builder("", "")
                    .setSeverityLevel(OVERALL_SEVERITY_LEVEL_UNKNOWN)
                    .build(),
                emptyList(),
                emptyList(),
                emptyList()
            )

        /** Creates an ID for a Safety Center entry. */
        fun entryId(sourceId: String, userId: Int = UserHandle.myUserId()) =
            SafetyCenterIds.encodeToString(
                SafetyCenterEntryId.newBuilder()
                    .setSafetySourceId(sourceId)
                    .setUserId(userId)
                    .build()
            )

        /** Creates an ID for a Safety Center issue. */
        fun issueId(
            sourceId: String,
            sourceIssueId: String,
            issueTypeId: String = ISSUE_TYPE_ID,
            userId: Int = UserHandle.myUserId()
        ) =
            SafetyCenterIds.encodeToString(
                SafetyCenterIssueId.newBuilder()
                    .setSafetyCenterIssueKey(
                        SafetyCenterIssueKey.newBuilder()
                            .setSafetySourceId(sourceId)
                            .setSafetySourceIssueId(sourceIssueId)
                            .setUserId(userId)
                            .build()
                    )
                    .setIssueTypeId(issueTypeId)
                    .build()
            )

        /** Creates an ID for a Safety Center issue action. */
        fun issueActionId(
            sourceId: String,
            sourceIssueId: String,
            sourceIssueActionId: String,
            userId: Int = UserHandle.myUserId()
        ) =
            SafetyCenterIds.encodeToString(
                SafetyCenterIssueActionId.newBuilder()
                    .setSafetyCenterIssueKey(
                        SafetyCenterIssueKey.newBuilder()
                            .setSafetySourceId(sourceId)
                            .setSafetySourceIssueId(sourceIssueId)
                            .setUserId(userId)
                            .build()
                    )
                    .setSafetySourceIssueActionId(sourceIssueActionId)
                    .build()
            )

        /**
         * On U+, returns a new [SafetyCenterData] with the dismissed issues set. Prior to U,
         * returns the passed in [SafetyCenterData].
         */
        fun SafetyCenterData.withDismissedIssuesIfAtLeastU(
            dismissedIssues: List<SafetyCenterIssue>
        ): SafetyCenterData =
            if (SdkLevel.isAtLeastU()) {
                copy(dismissedIssues = dismissedIssues)
            } else this

        /** Returns a [SafetyCenterData] without extras. */
        fun SafetyCenterData.withoutExtras() =
            if (SdkLevel.isAtLeastU()) {
                SafetyCenterData.Builder(this).clearExtras().build()
            } else this

        /**
         * On U+, returns a new [SafetyCenterData] with [SafetyCenterIssue]s having the
         * [attributionTitle]. Prior to U, returns the passed in [SafetyCenterData].
         */
        fun SafetyCenterData.withAttributionTitleInIssuesIfAtLeastU(
            attributionTitle: String?
        ): SafetyCenterData {
            return if (SdkLevel.isAtLeastU()) {
                val issuesWithAttributionTitle =
                    this.issues.map {
                        SafetyCenterIssue.Builder(it).setAttributionTitle(attributionTitle).build()
                    }
                copy(issues = issuesWithAttributionTitle)
            } else this
        }

        /**
         * On U+, returns a new [SafetyCenterData] with the extras set. Prior to U, returns the
         * passed in [SafetyCenterData].
         */
        fun SafetyCenterData.withExtrasIfAtLeastU(extras: Bundle): SafetyCenterData =
            if (SdkLevel.isAtLeastU()) {
                copy(extras = extras)
            } else this

        @RequiresApi(UPSIDE_DOWN_CAKE)
        private fun SafetyCenterData.copy(
            issues: List<SafetyCenterIssue> = this.issues,
            dismissedIssues: List<SafetyCenterIssue> = this.dismissedIssues,
            extras: Bundle = this.extras
        ): SafetyCenterData =
            SafetyCenterData.Builder(status)
                .apply {
                    issues.forEach(::addIssue)
                    entriesOrGroups.forEach(::addEntryOrGroup)
                    staticEntryGroups.forEach(::addStaticEntryGroup)
                    dismissedIssues.forEach(::addDismissedIssue)
                }
                .setExtras(extras)
                .build()
    }
}
