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

import android.safetycenter.SafetyCenterStatus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyCenterStatus]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterStatusTest {

    private val baseStatus =
        SafetyCenterStatus.Builder("This is my title", "This is my summary")
            .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
            .setRefreshStatus(SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS)
            .build()

    @Test
    fun getTitle_returnsTitle() {
        assertThat(SafetyCenterStatus.Builder(baseStatus).setTitle("title").build().title)
            .isEqualTo("title")

        assertThat(SafetyCenterStatus.Builder(baseStatus).setTitle("different title").build().title)
            .isEqualTo("different title")
    }

    @Test
    fun getSummary_returnsSummary() {
        assertThat(SafetyCenterStatus.Builder(baseStatus).setSummary("summary").build().summary)
            .isEqualTo("summary")

        assertThat(
                SafetyCenterStatus.Builder(baseStatus)
                    .setSummary("different summary")
                    .build()
                    .summary
            )
            .isEqualTo("different summary")
    }

    @Test
    fun getSeverityLevel_returnsSeverityLevel() {
        assertThat(
                SafetyCenterStatus.Builder(baseStatus)
                    .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
                    .build()
                    .severityLevel
            )
            .isEqualTo(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)

        assertThat(
                SafetyCenterStatus.Builder(baseStatus)
                    .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
                    .build()
                    .severityLevel
            )
            .isEqualTo(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
    }

    @Test
    fun getSeverityLevel_defaultUnknown() {
        assertThat(
                SafetyCenterStatus.Builder("This is my title", "This is my summary")
                    .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN)
                    .build()
                    .severityLevel
            )
            .isEqualTo(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getRefreshStatus_returnsRefreshStatus() {
        assertThat(
                SafetyCenterStatus.Builder(baseStatus)
                    .setRefreshStatus(SafetyCenterStatus.REFRESH_STATUS_NONE)
                    .build()
                    .refreshStatus
            )
            .isEqualTo(SafetyCenterStatus.REFRESH_STATUS_NONE)

        assertThat(
                SafetyCenterStatus.Builder(baseStatus)
                    .setRefreshStatus(SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS)
                    .build()
                    .refreshStatus
            )
            .isEqualTo(SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS)
    }

    @Test
    fun getRefreshStatus_defaultNone() {
        assertThat(
                SafetyCenterStatus.Builder("This is my title", "This is my summary")
                    .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN)
                    .build()
                    .refreshStatus
            )
            .isEqualTo(SafetyCenterStatus.REFRESH_STATUS_NONE)
    }

    @Test
    fun build_withInvalidOverallSeverityLevel_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetyCenterStatus.Builder(baseStatus).setSeverityLevel(-1)
            }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected OverallSeverityLevel for SafetyCenterStatus: -1")
    }

    @Test
    fun build_withInvalidRefreshStatus_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetyCenterStatus.Builder(baseStatus).setRefreshStatus(-1)
            }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected RefreshStatus for SafetyCenterStatus: -1")
    }

    @Test
    fun describeContents_returns0() {
        assertThat(baseStatus.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(baseStatus).recreatesEqual(SafetyCenterStatus.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetyCenterStatus.CREATOR,
                createCopy = { SafetyCenterStatus.Builder(it).build() }
            )
            .addEqualityGroup(
                baseStatus,
                SafetyCenterStatus.Builder("This is my title", "This is my summary")
                    .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
                    .setRefreshStatus(SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS)
                    .build(),
                SafetyCenterStatus.Builder(baseStatus).build()
            )
            .addEqualityGroup(
                SafetyCenterStatus.Builder("same title", "same summary")
                    .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
                    .build(),
                SafetyCenterStatus.Builder("same title", "same summary")
                    .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterStatus.Builder(baseStatus).setTitle("that's not it").build()
            )
            .addEqualityGroup(
                SafetyCenterStatus.Builder(baseStatus).setSummary("that's not it").build()
            )
            .addEqualityGroup(
                SafetyCenterStatus.Builder(baseStatus)
                    .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterStatus.Builder(baseStatus)
                    .setRefreshStatus(SafetyCenterStatus.REFRESH_STATUS_NONE)
                    .build()
            )
            .test()
    }
}
