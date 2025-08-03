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
import android.content.Intent
import android.content.Intent.FLAG_RECEIVER_FOREGROUND
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_INFORMATION
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetySourceIssue.Action
import android.safetycenter.SafetySourceIssue.Action.ConfirmationDialogDetails
import android.safetycenter.SafetySourceStatus
import android.safetycenter.SafetySourceStatus.IconAction
import android.safetycenter.SafetySourceStatus.IconAction.ICON_TYPE_GEAR
import android.safetycenter.SafetySourceStatus.IconAction.ICON_TYPE_INFO
import androidx.annotation.RequiresApi
import com.android.modules.utils.build.SdkLevel
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ACTION_TEST_ACTIVITY
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ACTION_TEST_ACTIVITY_EXPORTED
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_ID
import com.android.safetycenter.testing.SafetySourceIntentHandler.Companion.ACTION_DISMISS_ISSUE
import com.android.safetycenter.testing.SafetySourceIntentHandler.Companion.ACTION_RESOLVE_ACTION
import com.android.safetycenter.testing.SafetySourceIntentHandler.Companion.EXTRA_SOURCE_ID
import com.android.safetycenter.testing.SafetySourceIntentHandler.Companion.EXTRA_SOURCE_ISSUE_ACTION_ID
import com.android.safetycenter.testing.SafetySourceIntentHandler.Companion.EXTRA_SOURCE_ISSUE_ID
import kotlin.math.max

/**
 * A class that provides [SafetySourceData] objects and associated constants to facilitate setting
 * up specific states in SafetyCenter for testing.
 */
@RequiresApi(TIRAMISU)
class SafetySourceTestData(private val context: Context) {

    /**
     * A [PendingIntent] that redirects to the [TestActivity] page.
     *
     * @param explicit whether the returned [PendingIntent] should use an explicit [Intent] (default
     *   [true])
     * @param identifier the [Intent] identifier (default [null])
     */
    fun createTestActivityRedirectPendingIntent(
        explicit: Boolean = true,
        identifier: String? = null
    ) =
        createRedirectPendingIntent(
            context,
            createTestActivityIntent(context, explicit).setIdentifier(identifier)
        )

    /** A [SafetySourceData] with a [SEVERITY_LEVEL_UNSPECIFIED] [SafetySourceStatus]. */
    val unspecified =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Unspecified title",
                        "Unspecified summary",
                        SEVERITY_LEVEL_UNSPECIFIED
                    )
                    .setEnabled(false)
                    .build()
            )
            .build()

    /**
     * A disabled [SafetySourceData] with a [SEVERITY_LEVEL_UNSPECIFIED] [SafetySourceStatus], and a
     * [PendingIntent] that redirects to [TestActivity].
     */
    val unspecifiedDisabledWithTestActivityRedirect =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Clickable disabled title",
                        "Clickable disabled summary",
                        SEVERITY_LEVEL_UNSPECIFIED
                    )
                    .setEnabled(false)
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .build()
            )
            .build()

    /** A [SafetySourceIssue] with a [SEVERITY_LEVEL_INFORMATION] and a redirecting [Action]. */
    val informationIssue = defaultInformationIssueBuilder().build()

    /**
     * A [SafetySourceIssue.Builder] with a [SEVERITY_LEVEL_INFORMATION] and a redirecting [Action].
     */
    fun defaultInformationIssueBuilder(
        id: String = INFORMATION_ISSUE_ID,
        title: String = "Information issue title",
        summary: String = "Information issue summary"
    ) =
        SafetySourceIssue.Builder(id, title, summary, SEVERITY_LEVEL_INFORMATION, ISSUE_TYPE_ID)
            .addAction(action())

    /** Creates an action with some defaults set. */
    fun action(
        id: String = INFORMATION_ISSUE_ACTION_ID,
        label: String = "Review",
        pendingIntent: PendingIntent = createTestActivityRedirectPendingIntent()
    ) = Action.Builder(id, label, pendingIntent).build()

    /**
     * A [SafetySourceIssue] with a [SEVERITY_LEVEL_INFORMATION] and a redirecting [Action]. With
     * subtitle provided.
     */
    val informationIssueWithSubtitle =
        SafetySourceIssue.Builder(
                INFORMATION_ISSUE_ID,
                "Information issue title",
                "Information issue summary",
                SEVERITY_LEVEL_INFORMATION,
                ISSUE_TYPE_ID
            )
            .setSubtitle("Information issue subtitle")
            .addAction(action())
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting [SafetySourceIssue] and
     * a [SEVERITY_LEVEL_UNSPECIFIED] [SafetySourceStatus].
     */
    val unspecifiedWithIssue =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Unspecified title",
                        "Unspecified summary",
                        SEVERITY_LEVEL_UNSPECIFIED
                    )
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .build()
            )
            .addIssue(informationIssue)
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting [SafetySourceIssue] and
     * a [SEVERITY_LEVEL_UNSPECIFIED] [SafetySourceStatus], to be used for a managed profile entry.
     */
    val unspecifiedWithIssueForWork =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Unspecified title for Work",
                        "Unspecified summary",
                        SEVERITY_LEVEL_UNSPECIFIED
                    )
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .build()
            )
            .addIssue(informationIssue)
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting [SafetySourceIssue] and
     * a [SEVERITY_LEVEL_UNSPECIFIED] [SafetySourceStatus], to be used for a private profile entry.
     */
    val unspecifiedWithIssueForPrivate =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Unspecified title for Private",
                        "Unspecified summary",
                        SEVERITY_LEVEL_UNSPECIFIED
                    )
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .build()
            )
            .addIssue(informationIssue)
            .build()

    /** A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] [SafetySourceStatus]. */
    val information =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder("Ok title", "Ok summary", SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .build()
            )
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] [SafetySourceStatus] and null
     * pending intent.
     */
    val informationWithNullIntent =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder("Ok title", "Ok summary", SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(null)
                    .build()
            )
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] [SafetySourceStatus] and an
     * [IconAction] defined.
     */
    val informationWithIconAction =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder("Ok title", "Ok summary", SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .setIconAction(
                        IconAction(ICON_TYPE_INFO, createTestActivityRedirectPendingIntent())
                    )
                    .build()
            )
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] [SafetySourceStatus] and an
     * [IconAction] having a [ICON_TYPE_GEAR].
     */
    val informationWithGearIconAction =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder("Ok title", "Ok summary", SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .setIconAction(
                        IconAction(ICON_TYPE_GEAR, createTestActivityRedirectPendingIntent())
                    )
                    .build()
            )
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting [SafetySourceIssue] and
     * [SafetySourceStatus].
     */
    val informationWithIssue =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder("Ok title", "Ok summary", SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .build()
            )
            .addIssue(informationIssue)
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting a [SafetySourceIssue]
     * having a [SafetySourceIssue.attributionTitle] and [SafetySourceStatus].
     */
    val informationWithIssueWithAttributionTitle: SafetySourceData
        @RequiresApi(UPSIDE_DOWN_CAKE)
        get() =
            SafetySourceData.Builder()
                .setStatus(
                    SafetySourceStatus.Builder("Ok title", "Ok summary", SEVERITY_LEVEL_INFORMATION)
                        .setPendingIntent(createTestActivityRedirectPendingIntent())
                        .build()
                )
                .addIssue(
                    defaultInformationIssueBuilder()
                        .setAttributionTitle("Attribution Title")
                        .build()
                )
                .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting [SafetySourceIssue] and
     * [SafetySourceStatus], to be used for a managed profile entry.
     */
    val informationWithIssueForWork =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Ok title for Work",
                        "Ok summary",
                        SEVERITY_LEVEL_INFORMATION
                    )
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .build()
            )
            .addIssue(informationIssue)
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting [SafetySourceIssue] and
     * [SafetySourceStatus], to be used for a private profile entry.
     */
    val informationWithIssueForPrivate =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Ok title for Private",
                        "Ok summary",
                        SEVERITY_LEVEL_INFORMATION
                    )
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .build()
            )
            .addIssue(informationIssue)
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting [SafetySourceIssue] and
     * [SafetySourceStatus].
     */
    val informationWithSubtitleIssue =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder("Ok title", "Ok summary", SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .build()
            )
            .addIssue(informationIssueWithSubtitle)
            .build()

    /**
     * A [SafetySourceIssue.Builder] with a [SEVERITY_LEVEL_RECOMMENDATION] and a redirecting
     * [Action].
     */
    fun defaultRecommendationIssueBuilder(
        title: String = "Recommendation issue title",
        summary: String = "Recommendation issue summary",
        confirmationDialog: Boolean = false
    ) =
        SafetySourceIssue.Builder(
                RECOMMENDATION_ISSUE_ID,
                title,
                summary,
                SEVERITY_LEVEL_RECOMMENDATION,
                ISSUE_TYPE_ID
            )
            .addAction(
                Action.Builder(
                        RECOMMENDATION_ISSUE_ACTION_ID,
                        "See issue",
                        createTestActivityRedirectPendingIntent()
                    )
                    .apply {
                        if (confirmationDialog && SdkLevel.isAtLeastU()) {
                            setConfirmationDialogDetails(CONFIRMATION_DETAILS)
                        }
                    }
                    .build()
            )

    /**
     * A [SafetySourceIssue] with a [SEVERITY_LEVEL_RECOMMENDATION], general category and a
     * redirecting [Action].
     */
    val recommendationGeneralIssue = defaultRecommendationIssueBuilder().build()

    /**
     * A [SafetySourceIssue] with a [SEVERITY_LEVEL_RECOMMENDATION], general category, redirecting
     * [Action] and with deduplication id.
     */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    fun recommendationIssueWithDeduplicationId(deduplicationId: String) =
        defaultRecommendationIssueBuilder().setDeduplicationId(deduplicationId).build()

    val recommendationIssueWithActionConfirmation: SafetySourceIssue
        @RequiresApi(UPSIDE_DOWN_CAKE)
        get() = defaultRecommendationIssueBuilder(confirmationDialog = true).build()

    /**
     * A [SafetySourceIssue] with a [SEVERITY_LEVEL_RECOMMENDATION], account category and a
     * redirecting [Action].
     */
    val recommendationAccountIssue =
        defaultRecommendationIssueBuilder()
            .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_ACCOUNT)
            .build()

    /**
     * A [SafetySourceIssue] with a [SEVERITY_LEVEL_RECOMMENDATION], device category and a
     * redirecting [Action].
     */
    val recommendationDeviceIssue =
        defaultRecommendationIssueBuilder()
            .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DEVICE)
            .build()

    private val dismissIssuePendingIntent =
        broadcastPendingIntent(
            Intent(ACTION_DISMISS_ISSUE).putExtra(EXTRA_SOURCE_ID, SINGLE_SOURCE_ID)
        )

    /**
     * A [SafetySourceIssue] with a [SEVERITY_LEVEL_RECOMMENDATION] and a dismiss [PendingIntent].
     */
    val recommendationIssueWithDismissPendingIntent =
        defaultRecommendationIssueBuilder()
            .setOnDismissPendingIntent(dismissIssuePendingIntent)
            .build()

    /** A [SafetySourceData.Builder] with a [SEVERITY_LEVEL_RECOMMENDATION] status. */
    fun defaultRecommendationDataBuilder() =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Recommendation title",
                        "Recommendation summary",
                        SEVERITY_LEVEL_RECOMMENDATION
                    )
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .build()
            )

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_RECOMMENDATION] redirecting [SafetySourceIssue]
     * and [SafetySourceStatus], only containing a general issue.
     */
    val recommendationWithGeneralIssue =
        defaultRecommendationDataBuilder().addIssue(recommendationGeneralIssue).build()

    val recommendationWithIssueWithActionConfirmation: SafetySourceData
        @RequiresApi(UPSIDE_DOWN_CAKE)
        get() =
            defaultRecommendationDataBuilder()
                .addIssue(recommendationIssueWithActionConfirmation)
                .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_RECOMMENDATION] redirecting [SafetySourceIssue]
     * and [SafetySourceStatus], only containing an account issue.
     */
    val recommendationWithAccountIssue =
        defaultRecommendationDataBuilder().addIssue(recommendationAccountIssue).build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_RECOMMENDATION] redirecting [SafetySourceIssue]
     * and [SafetySourceStatus], only containing a device issue.
     */
    val recommendationWithDeviceIssue =
        defaultRecommendationDataBuilder().addIssue(recommendationDeviceIssue).build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_RECOMMENDATION] [SafetySourceIssue] that has a
     * dismiss [PendingIntent], and [SafetySourceStatus].
     */
    val recommendationDismissPendingIntentIssue =
        defaultRecommendationDataBuilder()
            .addIssue(recommendationIssueWithDismissPendingIntent)
            .build()

    /** A [PendingIntent] used by the resolving [Action] in [criticalResolvingGeneralIssue]. */
    val criticalIssueActionPendingIntent = resolvingActionPendingIntent()

    /**
     * Returns a [PendingIntent] for a resolving [Action] with the given [sourceId], [sourceIssueId]
     * and [sourceIssueActionId]. Default values are the same as those used by
     * [criticalIssueActionPendingIntent]. *
     */
    fun resolvingActionPendingIntent(
        sourceId: String = SINGLE_SOURCE_ID,
        sourceIssueId: String = CRITICAL_ISSUE_ID,
        sourceIssueActionId: String = CRITICAL_ISSUE_ACTION_ID
    ) =
        broadcastPendingIntent(
            Intent(ACTION_RESOLVE_ACTION)
                .putExtra(EXTRA_SOURCE_ID, sourceId)
                .putExtra(EXTRA_SOURCE_ISSUE_ID, sourceIssueId)
                .putExtra(EXTRA_SOURCE_ISSUE_ACTION_ID, sourceIssueActionId)
                // Identifier is set because intent extras do not disambiguate PendingIntents
                .setIdentifier(sourceId + sourceIssueId + sourceIssueActionId)
        )

    /** A resolving Critical [Action] */
    val criticalResolvingAction =
        Action.Builder(CRITICAL_ISSUE_ACTION_ID, "Solve issue", criticalIssueActionPendingIntent)
            .setWillResolve(true)
            .build()

    /** A resolving Critical [Action] with confirmation */
    val criticalResolvingActionWithConfirmation: SafetySourceIssue.Action
        @RequiresApi(UPSIDE_DOWN_CAKE)
        get() =
            Action.Builder(
                    CRITICAL_ISSUE_ACTION_ID,
                    "Solve issue",
                    criticalIssueActionPendingIntent
                )
                .setWillResolve(true)
                .setConfirmationDialogDetails(CONFIRMATION_DETAILS)
                .build()

    /** An action that redirects to [TestActivity] */
    val testActivityRedirectAction =
        Action.Builder(
                CRITICAL_ISSUE_ACTION_ID,
                "Redirect",
                createTestActivityRedirectPendingIntent()
            )
            .build()

    /** A resolving Critical [Action] that declares a success message */
    val criticalResolvingActionWithSuccessMessage =
        Action.Builder(CRITICAL_ISSUE_ACTION_ID, "Solve issue", criticalIssueActionPendingIntent)
            .setWillResolve(true)
            .setSuccessMessage("Issue solved")
            .build()

    /** A [SafetySourceIssue] with a [SEVERITY_LEVEL_CRITICAL_WARNING] and a resolving [Action]. */
    val criticalResolvingIssueWithSuccessMessage =
        SafetySourceIssue.Builder(
                CRITICAL_ISSUE_ID,
                "Critical issue title",
                "Critical issue summary",
                SEVERITY_LEVEL_CRITICAL_WARNING,
                ISSUE_TYPE_ID
            )
            .addAction(criticalResolvingActionWithSuccessMessage)
            .build()

    /**
     * Another [SafetySourceIssue] with a [SEVERITY_LEVEL_CRITICAL_WARNING] and a redirecting
     * [Action].
     */
    val criticalRedirectingIssue =
        SafetySourceIssue.Builder(
                CRITICAL_ISSUE_ID,
                "Critical issue title 2",
                "Critical issue summary 2",
                SEVERITY_LEVEL_CRITICAL_WARNING,
                ISSUE_TYPE_ID
            )
            .addAction(
                Action.Builder(
                        CRITICAL_ISSUE_ACTION_ID,
                        "Go solve issue",
                        createTestActivityRedirectPendingIntent()
                    )
                    .build()
            )
            .build()

    /**
     * Another [SafetySourceIssue] with a [SEVERITY_LEVEL_CRITICAL_WARNING] an [Action] that
     * redirects to [TestActivity].
     */
    private val criticalIssueWithTestActivityRedirectAction =
        defaultCriticalResolvingIssueBuilder()
            .clearActions()
            .addAction(testActivityRedirectAction)
            .build()

    val criticalResolvingIssueWithConfirmation: SafetySourceIssue
        @RequiresApi(UPSIDE_DOWN_CAKE)
        get() =
            defaultCriticalResolvingIssueBuilder()
                .clearActions()
                .addAction(criticalResolvingActionWithConfirmation)
                .build()

    /**
     * [SafetySourceIssue.Builder] with a [SEVERITY_LEVEL_CRITICAL_WARNING] and a resolving [Action]
     * .
     */
    fun defaultCriticalResolvingIssueBuilder(issueId: String = CRITICAL_ISSUE_ID) =
        SafetySourceIssue.Builder(
                issueId,
                "Critical issue title",
                "Critical issue summary",
                SEVERITY_LEVEL_CRITICAL_WARNING,
                ISSUE_TYPE_ID
            )
            .addAction(criticalResolvingAction)

    /**
     * General [SafetySourceIssue] with a [SEVERITY_LEVEL_CRITICAL_WARNING] and a resolving [Action]
     * .
     */
    val criticalResolvingGeneralIssue = defaultCriticalResolvingIssueBuilder().build()

    /**
     * General [SafetySourceIssue] with a [SEVERITY_LEVEL_CRITICAL_WARNING] and with deduplication
     * info and a resolving [Action].
     */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    fun criticalIssueWithDeduplicationId(deduplicationId: String) =
        defaultCriticalResolvingIssueBuilder().setDeduplicationId(deduplicationId).build()

    /**
     * Account related [SafetySourceIssue] with a [SEVERITY_LEVEL_CRITICAL_WARNING] and a resolving
     * [Action].
     */
    val criticalResolvingAccountIssue =
        defaultCriticalResolvingIssueBuilder()
            .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_ACCOUNT)
            .build()

    /**
     * Device related [SafetySourceIssue] with a [SEVERITY_LEVEL_CRITICAL_WARNING] and a resolving
     * [Action].
     */
    val criticalResolvingDeviceIssue =
        defaultCriticalResolvingIssueBuilder()
            .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DEVICE)
            .build()

    /** A [SafetySourceData.Builder] with a [SEVERITY_LEVEL_CRITICAL_WARNING] status. */
    fun defaultCriticalDataBuilder() =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Critical title",
                        "Critical summary",
                        SEVERITY_LEVEL_CRITICAL_WARNING
                    )
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .build()
            )

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] having a resolving
     * [SafetySourceIssue] with a [SafetySourceIssue.attributionTitle] and success message.
     */
    val criticalWithIssueWithAttributionTitle: SafetySourceData
        @RequiresApi(UPSIDE_DOWN_CAKE)
        get() =
            defaultCriticalDataBuilder()
                .addIssue(
                    defaultCriticalResolvingIssueBuilder()
                        .setAttributionTitle("Attribution Title")
                        .clearActions()
                        .addAction(criticalResolvingActionWithSuccessMessage)
                        .build()
                )
                .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] having a redirecting
     * [SafetySourceIssue] with a [SafetySourceIssue.attributionTitle] and confirmation.
     */
    val criticalWithIssueWithConfirmationWithAttributionTitle: SafetySourceData
        @RequiresApi(UPSIDE_DOWN_CAKE)
        get() =
            defaultCriticalDataBuilder()
                .addIssue(
                    defaultCriticalResolvingIssueBuilder()
                        .setAttributionTitle("Attribution Title")
                        .clearActions()
                        .addAction(criticalResolvingActionWithConfirmation)
                        .build()
                )
                .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] having a redirecting
     * [SafetySourceIssue] with a [SafetySourceIssue.attributionTitle].
     */
    val criticalWithTestActivityRedirectWithAttributionTitle: SafetySourceData
        @RequiresApi(UPSIDE_DOWN_CAKE)
        get() =
            defaultCriticalDataBuilder()
                .addIssue(
                    defaultCriticalResolvingIssueBuilder()
                        .setAttributionTitle("Attribution Title")
                        .clearActions()
                        .addAction(testActivityRedirectAction)
                        .build()
                )
                .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] resolving general
     * [SafetySourceIssue] and [SafetySourceStatus].
     */
    val criticalWithResolvingGeneralIssue =
        defaultCriticalDataBuilder().addIssue(criticalResolvingGeneralIssue).build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] resolving general
     * [SafetySourceIssue] and [SafetySourceStatus], with confirmation dialog.
     */
    val criticalWithResolvingGeneralIssueWithConfirmation: SafetySourceData
        @RequiresApi(UPSIDE_DOWN_CAKE)
        get() =
            defaultCriticalDataBuilder().addIssue(criticalResolvingIssueWithConfirmation).build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] with a [SafetySourceIssue] that
     * redirects to the [TestActivity].
     */
    val criticalWithTestActivityRedirectIssue =
        defaultCriticalDataBuilder().addIssue(criticalIssueWithTestActivityRedirectAction).build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] resolving account related
     * [SafetySourceIssue] and [SafetySourceStatus].
     */
    val criticalWithResolvingAccountIssue =
        defaultCriticalDataBuilder().addIssue(criticalResolvingAccountIssue).build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] resolving device related
     * [SafetySourceIssue] and [SafetySourceStatus].
     */
    val criticalWithResolvingDeviceIssue =
        defaultCriticalDataBuilder().addIssue(criticalResolvingDeviceIssue).build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] resolving device related
     * [SafetySourceIssue] and [SafetySourceStatus] and a recommendation issue.
     */
    val criticalWithResolvingDeviceIssueAndRecommendationIssue =
        defaultCriticalDataBuilder()
            .addIssue(criticalResolvingDeviceIssue)
            .addIssue(recommendationAccountIssue)
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] resolving [SafetySourceIssue]
     * and [SafetySourceStatus].
     */
    val criticalWithResolvingIssueWithSuccessMessage =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Critical title",
                        "Critical summary",
                        SEVERITY_LEVEL_CRITICAL_WARNING
                    )
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .build()
            )
            .addIssue(criticalResolvingIssueWithSuccessMessage)
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting [SafetySourceIssue] and
     * [SEVERITY_LEVEL_CRITICAL_WARNING] [SafetySourceStatus].
     */
    val criticalWithInformationIssue =
        defaultCriticalDataBuilder().addIssue(informationIssue).build()

    /**
     * Another [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] redirecting
     * [SafetySourceIssue] and [SafetySourceStatus].
     */
    val criticalWithRedirectingIssue =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Critical title 2",
                        "Critical summary 2",
                        SEVERITY_LEVEL_CRITICAL_WARNING
                    )
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .build()
            )
            .addIssue(criticalRedirectingIssue)
            .build()

    /**
     * A function to generate simple [SafetySourceData] with the given entry [severityLevel] and
     * [entrySummary], and an optional issue with the same [severityLevel].
     */
    fun buildSafetySourceDataWithSummary(
        severityLevel: Int,
        entrySummary: String,
        withIssue: Boolean = false,
        entryTitle: String = "Entry title"
    ) =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(entryTitle, entrySummary, severityLevel)
                    .setPendingIntent(createTestActivityRedirectPendingIntent())
                    .build()
            )
            .apply {
                if (withIssue) {
                    addIssue(
                        SafetySourceIssue.Builder(
                                "issue_id",
                                "Issue title",
                                "Issue summary",
                                max(severityLevel, SEVERITY_LEVEL_INFORMATION),
                                ISSUE_TYPE_ID
                            )
                            .addAction(
                                Action.Builder(
                                        "action_id",
                                        "Action",
                                        createTestActivityRedirectPendingIntent()
                                    )
                                    .build()
                            )
                            .build()
                    )
                }
            }
            .build()

    private fun broadcastPendingIntent(intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            intent.addFlags(FLAG_RECEIVER_FOREGROUND).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        /** Issue ID for [informationIssue]. */
        const val INFORMATION_ISSUE_ID = "information_issue_id"

        /** Action ID for the redirecting action in [informationIssue]. */
        const val INFORMATION_ISSUE_ACTION_ID = "information_issue_action_id"

        /** Issue ID for a recommendation issue */
        const val RECOMMENDATION_ISSUE_ID = "recommendation_issue_id"

        /** Action ID for the redirecting action in recommendation issue. */
        const val RECOMMENDATION_ISSUE_ACTION_ID = "recommendation_issue_action_id"

        /** Issue ID for the critical issues in this file. */
        const val CRITICAL_ISSUE_ID = "critical_issue_id"

        /** Action ID for the critical actions in this file. */
        const val CRITICAL_ISSUE_ACTION_ID = "critical_issue_action_id"

        /** Issue type ID for all the issues in this file */
        // LINT.IfChange(issue_type_id)
        const val ISSUE_TYPE_ID = "issue_type_id"
        // LINT.ThenChange(/tests/hostside/safetycenter/src/android/safetycenter/hostside/SafetyCenterInteractionLoggingHostTest.kt:issue_type_id)

        const val CONFIRMATION_TITLE = "Confirmation title"
        const val CONFIRMATION_TEXT = "Confirmation text"
        const val CONFIRMATION_YES = "Confirmation yes"
        const val CONFIRMATION_NO = "Confirmation no"
        val CONFIRMATION_DETAILS: ConfirmationDialogDetails
            @RequiresApi(UPSIDE_DOWN_CAKE)
            get() =
                ConfirmationDialogDetails(
                    CONFIRMATION_TITLE,
                    CONFIRMATION_TEXT,
                    CONFIRMATION_YES,
                    CONFIRMATION_NO
                )

        /** A [SafetyEvent] to push arbitrary changes to Safety Center. */
        val EVENT_SOURCE_STATE_CHANGED =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()

        /** Returns a [SafetySourceData] object containing only the given [issues]. */
        fun issuesOnly(vararg issues: SafetySourceIssue): SafetySourceData {
            val builder = SafetySourceData.Builder()
            issues.forEach { builder.addIssue(it) }
            return builder.build()
        }

        /** Returns an [Intent] that redirects to the [TestActivity] page. */
        fun createTestActivityIntent(context: Context, explicit: Boolean = true): Intent =
            if (explicit) {
                Intent(ACTION_TEST_ACTIVITY).setPackage(context.packageName)
            } else {
                val intent = Intent(ACTION_TEST_ACTIVITY_EXPORTED)
                // We have seen some flakiness where implicit intents find multiple receivers
                // and the ResolveActivity pops up.  A test cannot handle this, so crash.  Most
                // likely the cause is other test's APKs being left hanging around by flaky
                // test infrastructure.
                intent.flags = intent.flags or Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT
                intent
            }

        /** Returns a [PendingIntent] that redirects to the given [Intent]. */
        fun createRedirectPendingIntent(context: Context, intent: Intent): PendingIntent {
            return PendingIntent.getActivity(
                context,
                0 /* requestCode */,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
