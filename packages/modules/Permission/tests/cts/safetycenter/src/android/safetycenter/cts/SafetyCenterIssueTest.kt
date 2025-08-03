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

package android.safetycenter.cts

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterIssue.Action.ConfirmationDialogDetails
import androidx.annotation.RequiresApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import androidx.test.filters.SdkSuppress
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyCenterIssue]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterIssueTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val pendingIntent1 =
        PendingIntent.getActivity(context, 0, Intent("Fake Data"), PendingIntent.FLAG_IMMUTABLE)
    private val pendingIntent2 =
        PendingIntent.getActivity(
            context,
            0,
            Intent("Fake Different Data"),
            PendingIntent.FLAG_IMMUTABLE
        )

    private val action1 =
        SafetyCenterIssue.Action.Builder("action_id_1", "an action", pendingIntent1)
            .setWillResolve(true)
            .setIsInFlight(true)
            .setSuccessMessage("a success message")
            .build()
    private val action2 =
        SafetyCenterIssue.Action.Builder("action_id_2", "another action", pendingIntent2)
            .setWillResolve(false)
            .setIsInFlight(false)
            .build()

    private val issue1 =
        SafetyCenterIssue.Builder("issue_id", "Everything's good", "Please acknowledge this")
            .setSubtitle("In the neighborhood")
            .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
            .setDismissible(true)
            .setShouldConfirmDismissal(true)
            .setActions(listOf(action1))
            .build()

    private val issueWithRequiredFieldsOnly =
        SafetyCenterIssue.Builder("issue_id", "Everything's good", "Please acknowledge this")
            .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
            .build()

    @Test
    fun getId_returnsId() {
        assertThat(SafetyCenterIssue.Builder(issue1).setId("an id").build().id).isEqualTo("an id")
        assertThat(SafetyCenterIssue.Builder(issue1).setId("another id").build().id)
            .isEqualTo("another id")
    }

    @Test
    fun getTitle_returnsTitle() {
        assertThat(SafetyCenterIssue.Builder(issue1).setTitle("a title").build().title)
            .isEqualTo("a title")
        assertThat(SafetyCenterIssue.Builder(issue1).setTitle("another title").build().title)
            .isEqualTo("another title")
    }

    @Test
    fun getSubtitle_returnsSubtitle() {
        assertThat(SafetyCenterIssue.Builder(issue1).setSubtitle("a subtitle").build().subtitle)
            .isEqualTo("a subtitle")
        assertThat(
                SafetyCenterIssue.Builder(issue1).setSubtitle("another subtitle").build().subtitle
            )
            .isEqualTo("another subtitle")
    }

    @Test
    fun getSummary_returnsSummary() {
        assertThat(SafetyCenterIssue.Builder(issue1).setSummary("a summary").build().summary)
            .isEqualTo("a summary")
        assertThat(SafetyCenterIssue.Builder(issue1).setSummary("another summary").build().summary)
            .isEqualTo("another summary")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getAttributionTitle_returnsAttributionTitle() {
        assertThat(
                SafetyCenterIssue.Builder(issue1)
                    .setAttributionTitle("an attributionTitle")
                    .build()
                    .attributionTitle
            )
            .isEqualTo("an attributionTitle")
        assertThat(
                SafetyCenterIssue.Builder(issue1)
                    .setAttributionTitle("another attributionTitle")
                    .build()
                    .attributionTitle
            )
            .isEqualTo("another attributionTitle")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getAttributionTitle_withNullAttributionTitle_returnsNull() {
        val safetyCenterIssue =
            SafetyCenterIssue.Builder("issue_id", "Everything's good", "Please acknowledge this")
                .build()

        assertThat(safetyCenterIssue.attributionTitle).isNull()
    }

    @Test
    fun getSeverityLevel_returnsSeverityLevel() {
        assertThat(
                SafetyCenterIssue.Builder(issue1)
                    .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION)
                    .build()
                    .severityLevel
            )
            .isEqualTo(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION)
        assertThat(
                SafetyCenterIssue.Builder(issue1)
                    .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING)
                    .build()
                    .severityLevel
            )
            .isEqualTo(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING)
    }

    @Test
    fun isDismissible_returnsIsDismissible() {
        assertThat(SafetyCenterIssue.Builder(issue1).setDismissible(true).build().isDismissible)
            .isTrue()
        assertThat(SafetyCenterIssue.Builder(issue1).setDismissible(false).build().isDismissible)
            .isFalse()
    }

    @Test
    fun isDismissible_defaultsToTrue() {
        assertThat(issueWithRequiredFieldsOnly.isDismissible).isTrue()
    }

    @Test
    fun shouldConfirmDismissal_returnsShouldConfirmDismissal() {
        assertThat(
                SafetyCenterIssue.Builder(issue1)
                    .setShouldConfirmDismissal(true)
                    .build()
                    .shouldConfirmDismissal()
            )
            .isTrue()
        assertThat(
                SafetyCenterIssue.Builder(issue1)
                    .setShouldConfirmDismissal(false)
                    .build()
                    .shouldConfirmDismissal()
            )
            .isFalse()
    }

    @Test
    fun shouldConfirmDismissal_defaultsToTrue() {
        assertThat(issueWithRequiredFieldsOnly.shouldConfirmDismissal()).isTrue()
    }

    @Test
    fun getActions_returnsActions() {
        assertThat(
                SafetyCenterIssue.Builder(issue1)
                    .setActions(listOf(action1, action2))
                    .build()
                    .actions
            )
            .containsExactly(action1, action2)
            .inOrder()
        assertThat(SafetyCenterIssue.Builder(issue1).setActions(listOf(action2)).build().actions)
            .containsExactly(action2)
        assertThat(SafetyCenterIssue.Builder(issue1).setActions(listOf()).build().actions).isEmpty()
    }

    @Test
    fun getActions_mutationsAreNotAllowed() {
        val mutatedActions = issue1.actions

        assertFailsWith(UnsupportedOperationException::class) { mutatedActions.add(action2) }
    }

    @Test
    fun build_withInvalidIssueSeverityLevel_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetyCenterIssue.Builder(issue1).setSeverityLevel(-1)
            }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected IssueSeverityLevel for SafetyCenterIssue: -1")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getGroupId_withNonNullValue_returnsGroupId() {
        val issue = SafetyCenterIssue.Builder(issue1).setGroupId("group_id").build()

        assertThat(issue.groupId).isEqualTo("group_id")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getGroupId_withNullValue_returnsNull() {
        val issue =
            SafetyCenterIssue.Builder("issue_id", "Everything's good", "Please acknowledge this")
                .build()

        assertThat(issue.groupId).isNull()
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun getGroupId_withVersionLessThanU_throws() {
        val issue =
            SafetyCenterIssue.Builder("issue_id", "Everything's good", "Please acknowledge this")
                .build()

        assertFails { issue.groupId }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setGroupId_withNullValue_returnsNull() {
        val issue = SafetyCenterIssue.Builder(issue1).setGroupId(null).build()

        assertThat(issue.groupId).isNull()
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun setGroupId_withVersionLessThanU_throws() {
        assertFails { SafetyCenterIssue.Builder(issue1).setGroupId("group_id").build() }
    }

    @Test
    fun describeContents_returns0() {
        assertThat(issue1.describeContents()).isEqualTo(0)
        assertThat(issueWithRequiredFieldsOnly.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(issue1).recreatesEqual(SafetyCenterIssue.CREATOR)
        assertThat(issueWithRequiredFieldsOnly).recreatesEqual(SafetyCenterIssue.CREATOR)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun parcelRoundTrip_recreatesEqual_atLeastAndroidU() {
        val safetyCenterIssue =
            SafetyCenterIssue.Builder("issue_id", "Everything's good", "Please acknowledge this")
                .setSubtitle("In the neighborhood")
                .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
                .setDismissible(true)
                .setShouldConfirmDismissal(true)
                .setActions(
                    listOf(
                        SafetyCenterIssue.Action.Builder(action1)
                            .setConfirmationDialogDetails(
                                ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")
                            )
                            .build()
                    )
                )
                .setAttributionTitle("Attribution title")
                .setGroupId("group_id")
                .build()

        assertThat(safetyCenterIssue).recreatesEqual(SafetyCenterIssue.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        newTiramisuEqualsHashCodeToStringTester().test()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester_atLeastAndroidU() {
        newUpsideDownCakeEqualsHashCodeToStringTester().test()
    }

    @Test
    fun action_getId_returnsId() {
        assertThat(action1.id).isEqualTo("action_id_1")
        assertThat(action2.id).isEqualTo("action_id_2")
    }

    @Test
    fun action_getLabel_returnsLabel() {
        assertThat(action1.label).isEqualTo("an action")
        assertThat(action2.label).isEqualTo("another action")
    }

    @Test
    fun action_getPendingIntent_returnsPendingIntent() {
        assertThat(action1.pendingIntent).isEqualTo(pendingIntent1)
        assertThat(action2.pendingIntent).isEqualTo(pendingIntent2)
    }

    @Test
    fun action_willResolve_returnsWillResolve() {
        assertThat(action1.willResolve()).isTrue()
        assertThat(action2.willResolve()).isFalse()
    }

    @Test
    fun action_isInFlight_returnsIsInFlight() {
        assertThat(action1.isInFlight).isTrue()
        assertThat(action2.isInFlight).isFalse()
    }

    @Test
    fun action_getSuccessMessage_returnsSuccessMessage() {
        assertThat(action1.successMessage).isEqualTo("a success message")
        assertThat(action2.successMessage).isNull()
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun action_getConfirmationDialogDetails_withVersionLessThanU_throws() {
        assertFails { action1.confirmationDialogDetails }
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun action_setConfirmationDialogDetails_withVersionLessThanU_throws() {
        assertFails {
            SafetyCenterIssue.Action.Builder("action_id", "Action label", pendingIntent1)
                .setConfirmationDialogDetails(
                    ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")
                )
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun action_getConfirmationDialogDetails_withDefaultBuilder_returnsNull() {
        val action =
            SafetyCenterIssue.Action.Builder("action_id", "Action label", pendingIntent1).build()

        assertThat(action.confirmationDialogDetails).isNull()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun action_getConfirmationDialogDetails_whenSetExplicitly_returnsConfirmation() {
        val action =
            SafetyCenterIssue.Action.Builder("action_id", "Action label", pendingIntent1)
                .setConfirmationDialogDetails(
                    ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")
                )
                .build()

        assertThat(action.confirmationDialogDetails)
            .isEqualTo(ConfirmationDialogDetails("Title", "Text", "Accept", "Deny"))
    }

    @Test
    fun action_describeContents_returns0() {
        assertThat(action1.describeContents()).isEqualTo(0)
        assertThat(action2.describeContents()).isEqualTo(0)
    }

    @Test
    fun action_parcelRoundTrip_recreatesEqual() {
        assertThat(action1).recreatesEqual(SafetyCenterIssue.Action.CREATOR)
        assertThat(action2).recreatesEqual(SafetyCenterIssue.Action.CREATOR)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun action_parcelRoundTrip_recreatesEqual_atLeastAndroidU() {
        val action =
            SafetyCenterIssue.Action.Builder(action1)
                .setConfirmationDialogDetails(
                    ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")
                )
                .build()

        assertThat(action).recreatesEqual(SafetyCenterIssue.Action.CREATOR)
    }

    @Test
    fun action_equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        issueActionNewTiramisuEqualsHashCodeToStringTester().test()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun action_equalsHashCodeToString_usingEqualsHashCodeToStringTester_atLeastAndroidU() {
        val confirmationDialogDetails = ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")
        issueActionNewTiramisuEqualsHashCodeToStringTester(
                createCopyFromBuilder = { SafetyCenterIssue.Action.Builder(it).build() }
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder(action1)
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder(action2)
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setWillResolve(true)
                    .setIsInFlight(true)
                    .setSuccessMessage("a success message")
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build(),
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setWillResolve(true)
                    .setIsInFlight(true)
                    .setSuccessMessage("a success message")
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setSuccessMessage("a success message")
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("a_different_id", "a label", pendingIntent1)
                    .setSuccessMessage("a success message")
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a different label", pendingIntent1)
                    .setSuccessMessage("a success message")
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent2)
                    .setSuccessMessage("a success message")
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setWillResolve(true)
                    .setSuccessMessage("a success message")
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setIsInFlight(true)
                    .setSuccessMessage("a success message")
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setSuccessMessage("a different success message")
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setId("another_id")
                    .setLabel("another_label")
                    .setPendingIntent(pendingIntent2)
                    .setConfirmationDialogDetails(confirmationDialogDetails)
                    .build()
            )
            .test()
    }

    /**
     * Creates a new [EqualsHashCodeToStringTester] instance with all the equality groups in the
     * [newTiramisuEqualsHashCodeToStringTester] plus new equality groups covering all the new
     * fields added in U.
     */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    private fun newUpsideDownCakeEqualsHashCodeToStringTester():
        EqualsHashCodeToStringTester<SafetyCenterIssue> {
        val issueWithTiramisuFields =
            SafetyCenterIssue.Builder("issue_id", "Everything's good", "Please acknowledge this")
                .setSubtitle("In the neighborhood")
                .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
                .setDismissible(true)
                .setShouldConfirmDismissal(true)
                .setActions(listOf(action1))
                .build()
        val confirmationDialogDetails = ConfirmationDialogDetails("Title", "Text", "Accept", "Deny")
        return newTiramisuEqualsHashCodeToStringTester(
                createCopyFromBuilder = { SafetyCenterIssue.Builder(it).build() }
            )
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issueWithTiramisuFields)
                    .setAttributionTitle("Attribution title")
                    .build(),
                SafetyCenterIssue.Builder(issueWithTiramisuFields)
                    .setAttributionTitle("Attribution title")
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issueWithTiramisuFields)
                    .setAttributionTitle("a different attribution title")
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issueWithTiramisuFields)
                    .setAttributionTitle("Attribution title")
                    .setGroupId("group_id")
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issueWithTiramisuFields).setGroupId("group_id").build(),
                SafetyCenterIssue.Builder(issueWithTiramisuFields).setGroupId("group_id").build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issueWithTiramisuFields)
                    .setGroupId("a different group_id")
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issueWithTiramisuFields)
                    .setActions(
                        listOf(
                            SafetyCenterIssue.Action.Builder(action1)
                                .setConfirmationDialogDetails(confirmationDialogDetails)
                                .build()
                        )
                    )
                    .build()
            )
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

    /**
     * Creates a new [EqualsHashCodeToStringTester] instance which covers all the fields in the T
     * API and is safe to use on any T+ API level.
     */
    private fun newTiramisuEqualsHashCodeToStringTester(
        createCopyFromBuilder: ((SafetyCenterIssue) -> SafetyCenterIssue)? = null
    ) =
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetyCenterIssue.CREATOR,
                createCopy = createCopyFromBuilder
            )
            .addEqualityGroup(issue1, SafetyCenterIssue.Builder(issue1).build())
            .addEqualityGroup(issueWithRequiredFieldsOnly)
            .addEqualityGroup(
                SafetyCenterIssue.Builder("an id", "a title", "Please acknowledge this")
                    .setSubtitle("In the neighborhood")
                    .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
                    .setActions(listOf(action1))
                    .build(),
                SafetyCenterIssue.Builder("an id", "a title", "Please acknowledge this")
                    .setSubtitle("In the neighborhood")
                    .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
                    .setActions(listOf(action1))
                    .build()
            )
            .addEqualityGroup(SafetyCenterIssue.Builder(issue1).setId("a different id").build())
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issue1).setTitle("a different title").build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issue1).setSubtitle("a different subtitle").build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issue1).setSummary("a different summary").build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issue1)
                    .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING)
                    .build()
            )
            .addEqualityGroup(SafetyCenterIssue.Builder(issue1).setDismissible(false).build())
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issue1).setShouldConfirmDismissal(false).build()
            )
            .addEqualityGroup(SafetyCenterIssue.Builder(issue1).setActions(listOf(action2)).build())

    private fun issueActionNewTiramisuEqualsHashCodeToStringTester(
        createCopyFromBuilder: ((SafetyCenterIssue.Action) -> SafetyCenterIssue.Action)? = null
    ) =
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetyCenterIssue.Action.CREATOR,
                createCopy = createCopyFromBuilder
            )
            .addEqualityGroup(action1)
            .addEqualityGroup(action2)
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setWillResolve(true)
                    .setIsInFlight(true)
                    .setSuccessMessage("a success message")
                    .build(),
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setWillResolve(true)
                    .setIsInFlight(true)
                    .setSuccessMessage("a success message")
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setSuccessMessage("a success message")
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("a_different_id", "a label", pendingIntent1)
                    .setSuccessMessage("a success message")
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a different label", pendingIntent1)
                    .setSuccessMessage("a success message")
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent2)
                    .setSuccessMessage("a success message")
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setWillResolve(true)
                    .setSuccessMessage("a success message")
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setIsInFlight(true)
                    .setSuccessMessage("a success message")
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setSuccessMessage("a different success message")
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setId("another_id")
                    .setLabel("another_label")
                    .setPendingIntent(pendingIntent2)
                    .build()
            )
}
