/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.safetycenter.cts

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_INFORMATION
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetySourceIssue.Action
import android.safetycenter.SafetySourceIssue.Action.ConfirmationDialogDetails
import android.safetycenter.SafetySourceIssue.ISSUE_CATEGORY_ACCOUNT
import android.safetycenter.SafetySourceIssue.ISSUE_CATEGORY_DEVICE
import android.safetycenter.SafetySourceIssue.ISSUE_CATEGORY_GENERAL
import android.safetycenter.SafetySourceIssue.Notification
import android.safetycenter.cts.testing.Generic
import androidx.annotation.RequiresApi
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import androidx.test.filters.SdkSuppress
import com.android.modules.utils.build.SdkLevel
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetySourceIssue]. */
@RunWith(AndroidJUnit4::class)
class SafetySourceIssueTest {
    private val context: Context = getApplicationContext()

    private val pendingIntent1: PendingIntent =
        PendingIntent.getActivity(context, 0, Intent("PendingIntent 1"), FLAG_IMMUTABLE)
    private val action1 = Action.Builder("action_id_1", "Action label 1", pendingIntent1).build()
    private val pendingIntent2: PendingIntent =
        PendingIntent.getActivity(context, 0, Intent("PendingIntent 2"), FLAG_IMMUTABLE)
    private val action2 = Action.Builder("action_id_2", "Action label 2", pendingIntent2).build()
    private val action3 = Action.Builder("action_id_3", "Action label 3", pendingIntent1).build()
    private val pendingIntentService =
        PendingIntent.getService(context, 0, Intent("PendingIntent service"), FLAG_IMMUTABLE)

    @Test
    fun action_getId_returnsId() {
        val action = Action.Builder("action_id", "Action label", pendingIntent1).build()

        assertThat(action.id).isEqualTo("action_id")
    }

    @Test
    fun action_getLabel_returnsLabel() {
        val action = Action.Builder("action_id", "Action label", pendingIntent1).build()

        assertThat(action.label).isEqualTo("Action label")
    }

    @Test
    fun action_willResolve_withDefaultBuilder_returnsFalse() {
        val action = Action.Builder("action_id", "Action label", pendingIntent1).build()

        assertThat(action.willResolve()).isFalse()
    }

    @Test
    fun action_willResolve_whenSetExplicitly_returnsWillResolve() {
        val action =
            Action.Builder("action_id", "Action label", pendingIntentService)
                .setWillResolve(true)
                .build()

        assertThat(action.willResolve()).isTrue()
    }

    @Test
    fun action_getPendingIntent_returnsPendingIntent() {
        val action = Action.Builder("action_id", "Action label", pendingIntent1).build()

        assertThat(action.pendingIntent).isEqualTo(pendingIntent1)
    }

    @Test
    fun action_getSuccessMessage_withDefaultBuilder_returnsNull() {
        val action = Action.Builder("action_id", "Action label", pendingIntent1).build()

        assertThat(action.successMessage).isNull()
    }

    @Test
    fun action_getSuccessMessage_whenSetExplicitly_returnsSuccessMessage() {
        val action =
            Action.Builder("action_id", "Action label", pendingIntent1)
                .setSuccessMessage("Action successfully completed")
                .build()

        assertThat(action.successMessage).isEqualTo("Action successfully completed")
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun action_getConfirmationDialogDetails_withVersionLessThanU_throws() {
        val action = Action.Builder("action_id", "Action label", pendingIntent1).build()

        assertFails { action.confirmationDialogDetails }
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun action_setConfirmationDialogDetails_withVersionLessThanU_throws() {
        assertFails {
            Action.Builder("action_id", "Action label", pendingIntent1)
                .setConfirmationDialogDetails(
                    ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")
                )
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun action_getConfirmationDialogDetails_withDefaultBuilder_returnsNull() {
        val action = Action.Builder("action_id", "Action label", pendingIntent1).build()

        assertThat(action.confirmationDialogDetails).isNull()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun action_getConfirmationDialogDetails_whenSetExplicitly_returnsConfirmation() {
        val action =
            Action.Builder("action_id", "Action label", pendingIntent1)
                .setConfirmationDialogDetails(
                    ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")
                )
                .build()

        assertThat(action.confirmationDialogDetails)
            .isEqualTo(ConfirmationDialogDetails("Title", "Text", "Accept", "Deny"))
    }

    @Test
    fun action_build_withNullId_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            Action.Builder(Generic.asNull(), "Action label", pendingIntent1)
        }
    }

    @Test
    fun action_build_withNullLabel_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            Action.Builder("action_id", Generic.asNull(), pendingIntent1)
        }
    }

    @Test
    fun action_build_withNullPendingIntent_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            Action.Builder("action_id", "Action label", Generic.asNull())
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun action_build_withActivityPendingIntentAndWillResolve_throwsIllegalArgumentException() {
        assertFailsWith(IllegalArgumentException::class) {
            Action.Builder("action_id", "Action label", pendingIntent1).setWillResolve(true).build()
        }
    }

    @Test
    fun action_describeContents_returns0() {
        val action = Action.Builder("action_id", "Action label", pendingIntent1).build()

        assertThat(action.describeContents()).isEqualTo(0)
    }

    @Test
    fun action_parcelRoundTrip_recreatesEqual() {
        val action =
            Action.Builder("action_id", "Action label", pendingIntent1)
                .setSuccessMessage("Action successfully completed")
                .build()

        assertThat(action).recreatesEqual(Action.CREATOR)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun action_parcelRoundTrip_recreatesEqual_atLeastAndroidU() {
        val action =
            Action.Builder("action_id", "Action label", pendingIntent1)
                .setConfirmationDialogDetails(
                    ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")
                )
                .build()

        assertThat(action).recreatesEqual(Action.CREATOR)
    }

    @Test
    fun action_equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        actionNewTiramisuEqualsHashCodeToStringTester().test()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun action_equalsHashCodeToString_usingEqualsHashCodeToStringTester_atLeastAndroidU() {
        val confirmationDialogDetails = ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")
        actionNewTiramisuEqualsHashCodeToStringTester(
                createCopyFromBuilder = { Action.Builder(it).build() }
            )
            .addEqualityGroup(
                Action.Builder("action_id", "Action label", pendingIntent1)
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build(),
                Action.Builder("action_id", "Action label", pendingIntent1)
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build(),
                Action.Builder("action_id", "Action label", pendingIntent1)
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .setWillResolve(false)
                    .build()
            )
            .addEqualityGroup(
                Action.Builder("action_id", "Action label", pendingIntent1)
                    .setSuccessMessage("Action successfully completed")
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .addEqualityGroup(
                Action.Builder("action_id", "Other action label", pendingIntent1)
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .addEqualityGroup(
                Action.Builder("other_action_id", "Action label", pendingIntent1)
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .addEqualityGroup(
                Action.Builder("action_id", "Action label", pendingIntentService)
                    .setWillResolve(true)
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .addEqualityGroup(
                Action.Builder(
                        "action_id",
                        "Action label",
                        PendingIntent.getActivity(
                            context,
                            0,
                            Intent("Other action PendingIntent"),
                            FLAG_IMMUTABLE
                        )
                    )
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .addEqualityGroup(
                Action.Builder("action_id", "Action label", pendingIntent1)
                    .setSuccessMessage("Other action successfully completed")
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .test()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_getTitle_returnsTitle() {
        val notification = Notification.Builder("Notification title", "Notification text").build()

        assertThat(notification.title).isEqualTo("Notification title")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_getText_returnsText() {
        val notification = Notification.Builder("Notification title", "Notification text").build()

        assertThat(notification.text).isEqualTo("Notification text")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_getActions_withDefaultBuilder_returnsEmptyList() {
        val notification = Notification.Builder("", "").build()

        assertThat(notification.actions).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_getActions_returnsActions() {
        val notification =
            Notification.Builder("", "").addAction(action1).addAction(action2).build()

        assertThat(notification.actions).containsExactly(action1, action2).inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_getActions_mutationsAreNotAllowed() {
        val notification =
            Notification.Builder("", "").addAction(action1).addAction(action2).build()

        assertFailsWith(UnsupportedOperationException::class) { notification.actions.add(action3) }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_describeContents_returns0() {
        val notification =
            Notification.Builder("Notification title", "Notification text")
                .addAction(action1)
                .addAction(action2)
                .build()

        assertThat(notification.describeContents()).isEqualTo(0)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_parcelRoundTrip_recreatesEqual() {
        val notification =
            Notification.Builder("Notification title", "Notification text")
                .addAction(action1)
                .addAction(action2)
                .build()

        assertThat(notification).recreatesEqual(Notification.CREATOR)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_builder_withNullTitle_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            Notification.Builder(Generic.asNull(), "Notification text")
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_builder_withNullText_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            Notification.Builder("Notification title", Generic.asNull())
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_builder_addAction_doesNotMutatePreviouslyBuiltInstance() {
        val notificationBuilder = Notification.Builder("", "").addAction(action1)
        val actions = notificationBuilder.build().actions

        notificationBuilder.addAction(action2)

        assertThat(actions).containsExactly(action1)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_builder_addAction_withNull_throwsIllegalArgumentException() {
        assertFailsWith(NullPointerException::class) {
            Notification.Builder("", "").addAction(Generic.asNull())
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_builder_addActions_keepsPreviouslyAddedActions() {
        val notificationBuilder = Notification.Builder("", "").addAction(action1)

        notificationBuilder.addActions(listOf(action2))

        assertThat(notificationBuilder.build().actions).containsExactly(action1, action2).inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_builder_addActions_doesNotMutatePreviouslyBuiltInstance() {
        val notificationBuilder = Notification.Builder("", "").addActions(listOf(action1))
        val actions = notificationBuilder.build().actions

        notificationBuilder.addActions(listOf(action2, action3))

        assertThat(actions).containsExactly(action1)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_builder_addActions_withNull_throwsIllegalArgumentException() {
        assertFailsWith(NullPointerException::class) {
            Notification.Builder("", "").addActions(Generic.asNull())
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_builder_clearActions_removesAllActions() {
        val notification =
            Notification.Builder("", "")
                .addAction(action1)
                .addAction(action2)
                .clearActions()
                .addAction(action3)
                .build()

        assertThat(notification.actions).containsExactly(action3)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_build_withDuplicateActionIds_throwsIllegalArgumentException() {
        val notificationBuilder =
            Notification.Builder("Notification title", "Notification text")
                .addAction(action1)
                .addAction(action1)

        val exception =
            assertFailsWith(IllegalArgumentException::class) { notificationBuilder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Custom notification cannot have duplicate action ids")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_build_withMoreThanTwoActions_throwsIllegalArgumentException() {
        val notificationBuilder =
            Notification.Builder("Notification title", "Notification text")
                .addAction(action1)
                .addAction(action2)
                .addAction(action3)

        val exception =
            assertFailsWith(IllegalArgumentException::class) { notificationBuilder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Custom notification must not contain more than 2 actions")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun notification_equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = Notification.CREATOR,
                createCopy = { Notification.Builder(it).build() }
            )
            .addEqualityGroup(
                Notification.Builder("Title", "Text").build(),
                Notification.Builder("Title", "Text").build(),
            )
            .addEqualityGroup(Notification.Builder("Other title", "Text").build())
            .addEqualityGroup(Notification.Builder("Title", "Other text").build())
            .addEqualityGroup(Notification.Builder("Title", "Text").addAction(action1).build())
            .addEqualityGroup(Notification.Builder("Title", "Text").addAction(action2).build())
            .addEqualityGroup(
                Notification.Builder("Title", "Text").addAction(action1).addAction(action2).build(),
                Notification.Builder("Title", "Text").addActions(listOf(action1, action2)).build()
            )
            .test()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun actionConfirmation_getTitle_returnsTitle() {
        val confirmationDialogDetails = ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")

        assertThat(confirmationDialogDetails.title).isEqualTo("Title")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun actionConfirmation_getText_returnsText() {
        val confirmationDialogDetails = ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")

        assertThat(confirmationDialogDetails.text).isEqualTo("Text")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun actionConfirmation_getAcceptButtonText_returnsAcceptButtonText() {
        val confirmationDialogDetails = ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")

        assertThat(confirmationDialogDetails.acceptButtonText).isEqualTo("Accept")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun actionConfirmation_getDenyButtonText_returnsDenyButtonText() {
        val confirmationDialogDetails = ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")

        assertThat(confirmationDialogDetails.denyButtonText).isEqualTo("Deny")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun actionConfirmation_describeContents_returns0() {
        val confirmationDialogDetails = ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")

        assertThat(confirmationDialogDetails.describeContents()).isEqualTo(0)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun actionConfirmation_parcelRoundTrip_recreatesEqual() {
        val confirmationDialogDetails = ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")

        assertThat(confirmationDialogDetails).recreatesEqual(ConfirmationDialogDetails.CREATOR)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun actionConfirmation_equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = ConfirmationDialogDetails.CREATOR
            )
            .addEqualityGroup(
                ConfirmationDialogDetails("Title", "Text", "Accept", "Deny"),
                ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")
            )
            .addEqualityGroup(ConfirmationDialogDetails("Other title", "Text", "Accept", "Deny"))
            .addEqualityGroup(ConfirmationDialogDetails("Title", "Other text", "Accept", "Deny"))
            .addEqualityGroup(ConfirmationDialogDetails("Title", "Text", "Other accept", "Deny"))
            .addEqualityGroup(ConfirmationDialogDetails("Title", "Text", "Accept", "Other deny"))
            .test()
    }

    @Test
    fun getId_returnsId() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.id).isEqualTo("Issue id")
    }

    @Test
    fun getTitle_returnsTitle() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.title).isEqualTo("Issue title")
    }

    @Test
    fun getSubtitle_withDefaultBuilder_returnsNull() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.subtitle).isNull()
    }

    @Test
    fun getSubtitle_whenSetExplicitly_returnsSubtitle() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .setSubtitle("Issue subtitle")
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.subtitle).isEqualTo("Issue subtitle")
    }

    @Test
    fun getSummary_returnsSummary() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.summary).isEqualTo("Issue summary")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getAttributionTitle_withNullAttributionTitle_returnsNull() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.attributionTitle).isNull()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getAttributionTitle_returnsAttributionTitle() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .setAttributionTitle("attribution title")
                .build()

        assertThat(safetySourceIssue.attributionTitle).isEqualTo("attribution title")
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun getAttributionTitle_withVersionLessThanU_throws() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertFails { safetySourceIssue.attributionTitle }
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun setAttributionTitle_withVersionLessThanU_throws() {
        val safetySourceIssueBuilder =
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id"
            )

        assertFails { safetySourceIssueBuilder.setAttributionTitle("title") }
    }

    @Test
    fun getSeverityLevel_returnsSeverityLevel() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.severityLevel).isEqualTo(SEVERITY_LEVEL_INFORMATION)
    }

    @Test
    fun getIssueCategory_withDefaultBuilder_returnsGeneralCategory() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.issueCategory).isEqualTo(ISSUE_CATEGORY_GENERAL)
    }

    @Test
    fun getIssueCategory_whenSetExplicitly_returnsIssueCategory() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .setIssueCategory(ISSUE_CATEGORY_DEVICE)
                .build()

        assertThat(safetySourceIssue.issueCategory).isEqualTo(ISSUE_CATEGORY_DEVICE)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getIssueCategory_whenSetExplicitlyWithUValueOnU_returnsIssueCategory() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_PASSWORDS)
                .build()

        assertThat(safetySourceIssue.issueCategory)
            .isEqualTo(SafetySourceIssue.ISSUE_CATEGORY_PASSWORDS)
    }

    @Test
    fun getActions_returnsActions() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .addAction(action2)
                .build()

        assertThat(safetySourceIssue.actions).containsExactly(action1, action2).inOrder()
    }

    @Test
    fun getActions_mutationsAreNotAllowed() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .addAction(action2)
                .build()
        val mutatedActions = safetySourceIssue.actions

        assertFailsWith(UnsupportedOperationException::class) { mutatedActions.add(action3) }
    }

    @Test
    fun builder_addAction_doesNotMutatePreviouslyBuiltInstance() {
        val safetySourceIssueBuilder =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
        val actions = safetySourceIssueBuilder.build().actions

        safetySourceIssueBuilder.addAction(action2)

        assertThat(actions).containsExactly(action1)
    }

    @Test
    fun clearActions_removesAllActions() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .addAction(action2)
                .clearActions()
                .addAction(action3)
                .build()

        assertThat(safetySourceIssue.actions).containsExactly(action3)
    }

    @Test
    fun getOnDismissPendingIntent_withDefaultBuilder_returnsNull() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.onDismissPendingIntent).isNull()
    }

    @Test
    fun getOnDismissPendingIntent_whenSetExplicitly_returnsOnDismissPendingIntent() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .setOnDismissPendingIntent(pendingIntentService)
                .build()

        assertThat(safetySourceIssue.onDismissPendingIntent).isEqualTo(pendingIntentService)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getDeduplicationId_withDefaultBuilder_returnsNull() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.deduplicationId).isNull()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getDeduplicationId_whenSetExplicitly_returnsDeduplicationId() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .setDeduplicationId("deduplication_id")
                .build()

        assertThat(safetySourceIssue.deduplicationId).isEqualTo("deduplication_id")
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun getDeduplicationId_withVersionLessThanU_throws() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertFails { safetySourceIssue.deduplicationId }
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun setDeduplicationId_withVersionLessThanU_throws() {
        val safetySourceIssueBuilder =
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id"
            )

        assertFails { safetySourceIssueBuilder.setDeduplicationId("id") }
    }

    @Test
    fun getIssueTypeId_returnsIssueTypeId() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.issueTypeId).isEqualTo("issue_type_id")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getCustomNotification_withDefaultBuilder_returnsNull() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.customNotification).isNull()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getCustomNotification_whenSetExplicitly_returnsCustomNotification() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .setCustomNotification(
                    Notification.Builder("Notification title", "Notification text")
                        .addAction(action2)
                        .build()
                )
                .build()

        assertThat(safetySourceIssue.customNotification)
            .isEqualTo(
                Notification.Builder("Notification title", "Notification text")
                    .addAction(action2)
                    .build()
            )
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun getCustomNotification_withVersionLessThanU_throws() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertFails { safetySourceIssue.customNotification }
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun setCustomNotification_withVersionLessThanU_throws() {
        val safetySourceIssueBuilder =
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id"
            )

        assertFails { safetySourceIssueBuilder.setCustomNotification(null) }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getNotificationBehavior_withDefaultBuilder_returnsUnspecified() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.notificationBehavior)
            .isEqualTo(SafetySourceIssue.NOTIFICATION_BEHAVIOR_UNSPECIFIED)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getNotificationBehavior_whenSetExplicitly_returnsSpecifiedBehavior() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .setNotificationBehavior(SafetySourceIssue.NOTIFICATION_BEHAVIOR_NEVER)
                .build()

        assertThat(safetySourceIssue.notificationBehavior)
            .isEqualTo(SafetySourceIssue.NOTIFICATION_BEHAVIOR_NEVER)
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun getNotificationBehavior_withVersionLessThanU_throws() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertFails { safetySourceIssue.notificationBehavior }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setNotificationBehavior_withInvalidNotificationBehavior_throwsIllegalArgumentException() {
        val builder =
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id"
            )

        val exception =
            assertFailsWith(IllegalArgumentException::class) { builder.setNotificationBehavior(-1) }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected NotificationBehavior for SafetySourceIssue: -1")
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun setNotificationBehavior_withVersionLessThanU_throws() {
        val safetySourceIssueBuilder =
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id"
            )

        assertFails { safetySourceIssueBuilder.setNotificationBehavior(0) }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getIssueActionability_withDefaultBuilder_returnsManual() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.issueActionability)
            .isEqualTo(SafetySourceIssue.ISSUE_ACTIONABILITY_MANUAL)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getIssueActionability_whenSetExplicitly_returnsValueSet() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_AUTOMATIC)
                .build()

        assertThat(safetySourceIssue.issueActionability)
            .isEqualTo(SafetySourceIssue.ISSUE_ACTIONABILITY_AUTOMATIC)
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun getIssueActionability_withVersionLessThanU_throws() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .build()

        assertFails { safetySourceIssue.issueActionability }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setIssueActionability_withInvalidIssueActionability_throwsIllegalArgumentException() {
        val builder =
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id"
            )

        val exception =
            assertFailsWith(IllegalArgumentException::class) { builder.setIssueActionability(-1) }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected IssueActionability for SafetySourceIssue: -1")
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun setIssueActionability_withVersionLessThanU_throws() {
        val safetySourceIssueBuilder =
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id"
            )

        assertFails { safetySourceIssueBuilder.setIssueActionability(0) }
    }

    @Test
    fun build_withNullId_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            SafetySourceIssue.Builder(
                Generic.asNull(),
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id"
            )
        }
    }

    @Test
    fun build_withNullTitle_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            SafetySourceIssue.Builder(
                "Issue id",
                Generic.asNull(),
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id"
            )
        }
    }

    @Test
    fun build_withNullSummary_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                Generic.asNull(),
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id"
            )
        }
    }

    @Test
    fun build_withUnspecifiedSeverityLevel_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_UNSPECIFIED,
                    "issue_type_id"
                )
            }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("SeverityLevel for SafetySourceIssue must not be SEVERITY_LEVEL_UNSPECIFIED")
    }

    @Test
    fun build_withInvalidSeverityLevel_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    -1,
                    "issue_type_id"
                )
            }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected SeverityLevel for SafetySourceIssue: -1")
    }

    @Test
    fun build_withNullIssueTypeId_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                Generic.asNull()
            )
        }
    }

    @Test
    fun build_withInvalidIssueCategory_throwsIllegalArgumentException() {
        val builder =
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id"
            )

        val exception =
            assertFailsWith(IllegalArgumentException::class) { builder.setIssueCategory(-1) }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected IssueCategory for SafetySourceIssue: -1")
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun build_withUIssueCategoryValueOnT_throwsIllegalArgumentException() {
        val builder =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)

        val exception =
            assertFailsWith(IllegalArgumentException::class) { builder.setIssueCategory(600) }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected IssueCategory for SafetySourceIssue: 600")
    }

    @Test
    fun build_withInvalidOnDismissPendingIntent_throwsIllegalArgumentException() {
        val builder =
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id"
            )
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                builder.setOnDismissPendingIntent(
                    PendingIntent.getActivity(
                        context,
                        /* requestCode = */ 0,
                        Intent("PendingIntent activity"),
                        FLAG_IMMUTABLE
                    )
                )
            }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Safety source issue on dismiss pending intent must not start an activity")
    }

    @Test
    fun build_withDuplicateActionIds_throwsIllegalArgumentException() {
        val safetySourceIssueBuilder =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .addAction(action1)

        val exception =
            assertFailsWith(IllegalArgumentException::class) { safetySourceIssueBuilder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Safety source issue cannot have duplicate action ids")
    }

    @Test
    fun build_withNoActions_throwsIllegalArgumentException() {
        val safetySourceIssueBuilder =
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id"
            )

        val exception =
            assertFailsWith(IllegalArgumentException::class) { safetySourceIssueBuilder.build() }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo(
                if (SdkLevel.isAtLeastU()) {
                    "Actionable safety source issue must contain at least 1 action"
                } else {
                    "Safety source issue must contain at least 1 action"
                }
            )
    }

    @Test
    fun build_withMoreThanTwoActions_throwsIllegalArgumentException() {
        val safetySourceIssueBuilder =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .addAction(action1)
                .addAction(action2)
                .addAction(action3)

        val exception =
            assertFailsWith(IllegalArgumentException::class) { safetySourceIssueBuilder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Safety source issue must not contain more than 2 actions")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun build_withNoActionsAndManualActionabilityOnU_throwsIllegalArgumentException() {
        val safetySourceIssueBuilder =
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id"
            )

        val exception =
            assertFailsWith(IllegalArgumentException::class) { safetySourceIssueBuilder.build() }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Actionable safety source issue must contain at least 1 action")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun build_withNoActionsAndTipActionabilityOnU_success() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_TIP)
                .build()

        assertThat(safetySourceIssue.actions).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun build_withNoActionsAndAutomaticActionabilityOnU_success() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_AUTOMATIC)
                .build()

        assertThat(safetySourceIssue.actions).isEmpty()
    }

    @Test
    fun describeContents_returns0() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .setSubtitle("Issue subtitle")
                .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                .addAction(action1)
                .addAction(action2)
                .setOnDismissPendingIntent(pendingIntentService)
                .build()

        assertThat(safetySourceIssue.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .setSubtitle("Issue subtitle")
                .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                .addAction(action1)
                .addAction(action2)
                .setOnDismissPendingIntent(pendingIntentService)
                .build()

        assertThat(safetySourceIssue).recreatesEqual(SafetySourceIssue.CREATOR)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun parcelRoundTrip_recreatesEqual_atLeastAndroidU() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .setSubtitle("Issue subtitle")
                .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                .addAction(
                    Action.Builder(action1)
                        .setConfirmationDialogDetails(
                            ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")
                        )
                        .build()
                )
                .addAction(action2)
                .setOnDismissPendingIntent(pendingIntentService)
                .build()

        assertThat(safetySourceIssue).recreatesEqual(SafetySourceIssue.CREATOR)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun parcelRoundTrip_recreatesEqual_atLeastUpsideDownCake() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id"
                )
                .setSubtitle("Issue subtitle")
                .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DATA)
                .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_TIP)
                .addAction(action1)
                .addAction(action2)
                .setOnDismissPendingIntent(pendingIntentService)
                .setCustomNotification(
                    Notification.Builder("Notification title", "Notification text")
                        .addAction(action2)
                        .build()
                )
                .setNotificationBehavior(SafetySourceIssue.NOTIFICATION_BEHAVIOR_DELAYED)
                .setAttributionTitle("attribution title")
                .setDeduplicationId("deduplication_id")
                .build()

        assertThat(safetySourceIssue).recreatesEqual(SafetySourceIssue.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        newTiramisuEqualsHashCodeToStringTester().test()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester_atLeastUpsideDownCake() {
        newUpsideDownCakeEqualsHashCodeToStringTester().test()
    }

    /**
     * Creates a new [EqualsHashCodeToStringTester] instance with all the equality groups in the
     * [newTiramisuEqualsHashCodeToStringTester] plus new equality groups covering all the new
     * fields added in U.
     */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    private fun newUpsideDownCakeEqualsHashCodeToStringTester() =
        newTiramisuEqualsHashCodeToStringTester(
                createCopyFromBuilder = { SafetySourceIssue.Builder(it).build() }
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                    .addAction(action1)
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .setNotificationBehavior(SafetySourceIssue.NOTIFICATION_BEHAVIOR_DELAYED)
                    .setCustomNotification(
                        Notification.Builder("Notification title", "Notification text")
                            .addAction(action2)
                            .build()
                    )
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                    .addAction(action1)
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .setNotificationBehavior(SafetySourceIssue.NOTIFICATION_BEHAVIOR_IMMEDIATELY)
                    .setCustomNotification(
                        Notification.Builder("Other title", "Other text").addAction(action2).build()
                    )
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                    .addAction(action1)
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .setAttributionTitle("attribution title")
                    .build(),
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                    .addAction(action1)
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .setAttributionTitle("attribution title")
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_CRITICAL_WARNING,
                        "issue_type_id"
                    )
                    .setAttributionTitle("Other issue attribution title")
                    .addAction(action1)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                    .addAction(action1)
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .setAttributionTitle("attribution title")
                    .setDeduplicationId("deduplication_id")
                    .build(),
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                    .addAction(action1)
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .setAttributionTitle("attribution title")
                    .setDeduplicationId("deduplication_id")
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                    .addAction(action1)
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .setAttributionTitle("attribution title")
                    .setDeduplicationId("other_deduplication_id")
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Other issue subtitle")
                    .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DATA)
                    .addAction(action1)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Other issue subtitle")
                    .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_PASSWORDS)
                    .addAction(action1)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Other issue subtitle")
                    .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_PERSONAL_SAFETY)
                    .addAction(action1)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_MANUAL)
                    .addAction(action1)
                    .setAttributionTitle("Attribution title")
                    .setDeduplicationId("dedup_id")
                    .setNotificationBehavior(SafetySourceIssue.NOTIFICATION_BEHAVIOR_NEVER)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_TIP)
                    .addAction(action1)
                    .build(),
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_TIP)
                    .addAction(action1)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_AUTOMATIC)
                    .addAction(action1)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_AUTOMATIC)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setNotificationBehavior(SafetySourceIssue.NOTIFICATION_BEHAVIOR_DELAYED)
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_AUTOMATIC)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                    .addAction(
                        Action.Builder(action1)
                            .setConfirmationDialogDetails(
                                ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")
                            )
                            .build()
                    )
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                    .addAction(action1)
                    .addAction(
                        Action.Builder(action2)
                            .setConfirmationDialogDetails(
                                ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")
                            )
                            .build()
                    )
                    .setOnDismissPendingIntent(pendingIntentService)
                    .build()
            )

    /**
     * Creates a new [EqualsHashCodeToStringTester] instance which covers all the fields in the T
     * API and is safe to use on any T+ API level.
     */
    private fun newTiramisuEqualsHashCodeToStringTester(
        createCopyFromBuilder: ((SafetySourceIssue) -> SafetySourceIssue)? = null
    ) =
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetySourceIssue.CREATOR,
                createCopy = createCopyFromBuilder
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                    .addAction(action1)
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .build(),
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                    .addAction(action1)
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .addAction(action1)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Other issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .addAction(action1)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Other issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .addAction(action1)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Different issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .addAction(action1)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_CRITICAL_WARNING,
                        "issue_type_id"
                    )
                    .addAction(action1)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "other_issue_type_id"
                    )
                    .addAction(action1)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .addAction(action2)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Other issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                    .addAction(action1)
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Other issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_DEVICE)
                    .addAction(action1)
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Other issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_DEVICE)
                    .addAction(action2)
                    .addAction(action1)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id"
                    )
                    .setSubtitle("Other issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_DEVICE)
                    .addAction(action2)
                    .addAction(action1)
                    .setOnDismissPendingIntent(
                        PendingIntent.getService(
                            context,
                            0,
                            Intent("Other PendingIntent service"),
                            FLAG_IMMUTABLE
                        )
                    )
                    .build()
            )

    private fun actionNewTiramisuEqualsHashCodeToStringTester(
        createCopyFromBuilder: ((Action) -> Action)? = null
    ) =
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = Action.CREATOR,
                createCopy = createCopyFromBuilder
            )
            .addEqualityGroup(
                Action.Builder("action_id", "Action label", pendingIntent1).build(),
                Action.Builder("action_id", "Action label", pendingIntent1).build(),
                Action.Builder("action_id", "Action label", pendingIntent1)
                    .setWillResolve(false)
                    .build()
            )
            .addEqualityGroup(
                Action.Builder("action_id", "Action label", pendingIntent1)
                    .setSuccessMessage("Action successfully completed")
                    .build()
            )
            .addEqualityGroup(
                Action.Builder("action_id", "Other action label", pendingIntent1).build()
            )
            .addEqualityGroup(
                Action.Builder("other_action_id", "Action label", pendingIntent1).build()
            )
            .addEqualityGroup(
                Action.Builder("action_id", "Action label", pendingIntentService)
                    .setWillResolve(true)
                    .build()
            )
            .addEqualityGroup(
                Action.Builder(
                        "action_id",
                        "Action label",
                        PendingIntent.getActivity(
                            context,
                            0,
                            Intent("Other action PendingIntent"),
                            FLAG_IMMUTABLE
                        )
                    )
                    .build()
            )
            .addEqualityGroup(
                Action.Builder("action_id", "Action label", pendingIntent1)
                    .setSuccessMessage("Other action successfully completed")
                    .build()
            )
}
