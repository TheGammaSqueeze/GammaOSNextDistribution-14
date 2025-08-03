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

import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_LOCALE_CHANGED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import androidx.test.filters.SdkSuppress
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyEvent]. */
@RunWith(AndroidJUnit4::class)
class SafetyEventTest {
    @Test
    fun getType_returnsType() {
        val safetyEvent = SafetyEvent.Builder(SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()

        assertThat(safetyEvent.type).isEqualTo(SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED)
    }

    @Test
    fun getRefreshBroadcastId_returnsRefreshBroadcastId() {
        val safetyEvent =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                .setRefreshBroadcastId(REFRESH_BROADCAST_ID)
                .build()

        assertThat(safetyEvent.refreshBroadcastId).isEqualTo(REFRESH_BROADCAST_ID)
    }

    @Test
    fun getSafetySourceIssueId_returnsSafetySourceIssueId() {
        val safetyEvent =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                .build()

        assertThat(safetyEvent.safetySourceIssueId).isEqualTo(SAFETY_SOURCE_ISSUE_ID)
    }

    @Test
    fun getSafetySourceIssueActionId_returnsSafetySourceIssueActionId() {
        val safetyEvent =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                .build()

        assertThat(safetyEvent.safetySourceIssueActionId).isEqualTo(SAFETY_SOURCE_ISSUE_ACTION_ID)
    }

    @Test
    fun build_actionFailed_withMissingIssueActionId_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                    .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                    .build()
            }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo(
                "Missing issue action id for resolving action safety event: " +
                    SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED
            )
    }

    @Test
    fun build_actionFailed_withMissingIssueId_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                    .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                    .build()
            }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo(
                "Missing issue id for resolving action safety event: " +
                    SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED
            )
    }

    @Test
    fun build_actionSucceeded_withMissingIssueActionId_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED)
                    .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                    .build()
            }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo(
                "Missing issue action id for resolving action safety event: " +
                    SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED
            )
    }

    @Test
    fun build_actionSucceeded_withMissingIssueId_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED)
                    .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                    .build()
            }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo(
                "Missing issue id for resolving action safety event: " +
                    SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED
            )
    }

    @Test
    fun build_refreshRequested_withMissingRefreshBroadcastId_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED).build()
            }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Missing refresh broadcast id for refresh requested safety event")
    }

    @Test
    fun build_withInvalidType_throwsIllegalArgumentException() {
        val exception = assertFailsWith(IllegalArgumentException::class) { SafetyEvent.Builder(-1) }

        assertThat(exception).hasMessageThat().isEqualTo("Unexpected Type for SafetyEvent: -1")
    }

    @Test
    fun describeContents_returns0() {
        val safetyEvent =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                .build()

        assertThat(safetyEvent.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        val safetyEvent =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                .build()

        assertThat(safetyEvent).recreatesEqual(SafetyEvent.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        newTiramisuEqualsHashCodeToStringTester().test()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester_atLeastAndroidU() {
        newTiramisuEqualsHashCodeToStringTester(
                createCopyFromBuilder = { SafetyEvent.Builder(it).build() }
            )
            .test()
    }

    private fun newTiramisuEqualsHashCodeToStringTester(
        createCopyFromBuilder: ((SafetyEvent) -> SafetyEvent)? = null
    ) =
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetyEvent.CREATOR,
                createCopy = createCopyFromBuilder
            )
            .addEqualityGroup(SafetyEvent.Builder(SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build())
            .addEqualityGroup(
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                    .setRefreshBroadcastId(REFRESH_BROADCAST_ID)
                    .build(),
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                    .setRefreshBroadcastId(REFRESH_BROADCAST_ID)
                    .build()
            )
            .addEqualityGroup(
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED)
                    .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                    .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                    .build(),
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED)
                    .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                    .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                    .build()
            )
            .addEqualityGroup(
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                    .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                    .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                    .build(),
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                    .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                    .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                    .build()
            )
            .addEqualityGroup(SafetyEvent.Builder(SAFETY_EVENT_TYPE_DEVICE_REBOOTED).build())
            .addEqualityGroup(SafetyEvent.Builder(SAFETY_EVENT_TYPE_DEVICE_LOCALE_CHANGED).build())
            .addEqualityGroup(
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                    .setRefreshBroadcastId(OTHER_REFRESH_BROADCAST_ID)
                    .build()
            )
            .addEqualityGroup(
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED)
                    .setSafetySourceIssueId(OTHER_SAFETY_SOURCE_ISSUE_ID)
                    .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                    .build()
            )
            .addEqualityGroup(
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED)
                    .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                    .setSafetySourceIssueActionId(OTHER_SAFETY_SOURCE_ISSUE_ACTION_ID)
                    .build()
            )
            .addEqualityGroup(
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                    .setSafetySourceIssueId(OTHER_SAFETY_SOURCE_ISSUE_ID)
                    .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                    .build()
            )
            .addEqualityGroup(
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                    .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                    .setSafetySourceIssueActionId(OTHER_SAFETY_SOURCE_ISSUE_ACTION_ID)
                    .build()
            )

    companion object {
        private const val REFRESH_BROADCAST_ID = "refresh_broadcast_id"
        private const val OTHER_REFRESH_BROADCAST_ID = "other_refresh_broadcast_id"
        private const val SAFETY_SOURCE_ISSUE_ID = "safety_source_issue_id"
        private const val OTHER_SAFETY_SOURCE_ISSUE_ID = "other_safety_source_issue_id"
        private const val SAFETY_SOURCE_ISSUE_ACTION_ID = "safety_source_issue_action_id"
        private const val OTHER_SAFETY_SOURCE_ISSUE_ACTION_ID =
            "other_safety_source_issue_action_id"
    }
}
