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
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.Bundle
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_INFORMATION
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetySourceStatus
import androidx.core.os.bundleOf
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.core.os.Parcelables.forceParcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import androidx.test.filters.SdkSuppress
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetySourceData]. */
@RunWith(AndroidJUnit4::class)
class SafetySourceDataTest {
    private val context: Context = getApplicationContext()

    @Test
    fun getStatus_withDefaultBuilder_returnsNull() {
        val safetySourceData =
            SafetySourceData.Builder().addIssue(createIssue(SEVERITY_LEVEL_INFORMATION)).build()

        assertThat(safetySourceData.status).isNull()
    }

    @Test
    fun getStatus_whenSetExplicitly_returnsStatus() {
        val status = createStatus(SEVERITY_LEVEL_INFORMATION)
        val safetySourceData = SafetySourceData.Builder().setStatus(status).build()

        assertThat(safetySourceData.status).isEqualTo(status)
    }

    @Test
    fun getIssues_withDefaultBuilder_returnsEmptyList() {
        val safetySourceData =
            SafetySourceData.Builder().setStatus(createStatus(SEVERITY_LEVEL_INFORMATION)).build()

        assertThat(safetySourceData.issues).isEmpty()
    }

    @Test
    fun getIssues_whenSetExplicitly_returnsIssues() {
        val firstIssue = createIssue(SEVERITY_LEVEL_INFORMATION, 1)
        val secondIssue = createIssue(SEVERITY_LEVEL_INFORMATION, 2)
        val safetySourceData =
            SafetySourceData.Builder().addIssue(firstIssue).addIssue(secondIssue).build()

        assertThat(safetySourceData.issues).containsExactly(firstIssue, secondIssue).inOrder()
    }

    @Test
    fun getIssues_mutationsAreNotAllowed() {
        val data =
            SafetySourceData.Builder()
                .setStatus(createStatus(SEVERITY_LEVEL_CRITICAL_WARNING))
                .addIssue(createIssue(SEVERITY_LEVEL_CRITICAL_WARNING, 0))
                .addIssue(createIssue(SEVERITY_LEVEL_RECOMMENDATION, 1))
                .build()
        val mutatedIssues = data.issues

        assertFailsWith(UnsupportedOperationException::class) {
            mutatedIssues.add(createIssue(SEVERITY_LEVEL_INFORMATION, 2))
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getExtras_withDefaultBuilder_returnsEmptyBundle() {
        val safetySourceData =
            SafetySourceData.Builder().setStatus(createStatus(SEVERITY_LEVEL_INFORMATION)).build()

        assertThat(safetySourceData.extras.keySet()).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getExtras_whenSetExplicitly_returnsExtras() {
        val safetySourceData =
            SafetySourceData.Builder()
                .setStatus(createStatus(SEVERITY_LEVEL_INFORMATION))
                .setExtras(bundleOf(EXTRA_KEY to EXTRA_VALUE))
                .build()

        assertThat(safetySourceData.extras.keySet()).containsExactly(EXTRA_KEY)
        assertThat(safetySourceData.extras.getString(EXTRA_KEY, "")).isEqualTo(EXTRA_VALUE)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getExtras_whenCleared_returnsEmptyBundle() {
        val safetySourceData =
            SafetySourceData.Builder()
                .setStatus(createStatus(SEVERITY_LEVEL_INFORMATION))
                .setExtras(bundleOf(EXTRA_KEY to EXTRA_VALUE))
                .clearExtras()
                .build()

        assertThat(safetySourceData.extras.keySet()).isEmpty()
    }

    @Test
    fun builder_addIssue_doesNotMutatePreviouslyBuiltInstance() {
        val firstIssue = createIssue(SEVERITY_LEVEL_INFORMATION, 1)
        val secondIssue = createIssue(SEVERITY_LEVEL_INFORMATION, 2)
        val safetySourceDataBuilder =
            SafetySourceData.Builder()
                .setStatus(createStatus(SEVERITY_LEVEL_INFORMATION))
                .addIssue(firstIssue)
        val issues = safetySourceDataBuilder.build().issues

        safetySourceDataBuilder.addIssue(secondIssue)

        assertThat(issues).containsExactly(firstIssue)
    }

    @Test
    fun clearIssues_removesAllIssues() {
        val firstIssue = createIssue(SEVERITY_LEVEL_INFORMATION, 1)
        val secondIssue = createIssue(SEVERITY_LEVEL_INFORMATION, 2)
        val safetySourceData =
            SafetySourceData.Builder()
                .setStatus(createStatus(SEVERITY_LEVEL_INFORMATION))
                .addIssue(firstIssue)
                .addIssue(secondIssue)
                .clearIssues()
                .build()

        assertThat(safetySourceData.issues).isEmpty()
    }

    @Test
    fun build_withNoStatusAndNoIssues_doesNotThrow() {
        val builder = SafetySourceData.Builder()

        assertThat(builder.build()).isNotNull()
    }

    @Test
    fun build_withNoStatusAndInfoIssues_doesNotThrow() {
        val builder = SafetySourceData.Builder().addIssue(createIssue(SEVERITY_LEVEL_INFORMATION))

        assertThat(builder.build()).isNotNull()
    }

    @Test
    fun build_withNoStatusAndRecommendationIssues_doesNotThrow() {
        val builder =
            SafetySourceData.Builder().addIssue(createIssue(SEVERITY_LEVEL_RECOMMENDATION))

        assertThat(builder.build()).isNotNull()
    }

    @Test
    fun build_withNoStatusAndCriticalIssues_doesNotThrow() {
        val builder =
            SafetySourceData.Builder().addIssue(createIssue(SEVERITY_LEVEL_CRITICAL_WARNING))

        assertThat(builder.build()).isNotNull()
    }

    @Test
    fun build_withUnspecifiedStatusAndNoIssues_doesNotThrow() {
        val builder = SafetySourceData.Builder().setStatus(createStatus(SEVERITY_LEVEL_UNSPECIFIED))

        assertThat(builder.build()).isNotNull()
    }

    @Test
    fun build_withUnspecifiedStatusAndInformationIssues_doesNotThrow() {
        val builder =
            SafetySourceData.Builder()
                .setStatus(createStatus(SEVERITY_LEVEL_UNSPECIFIED))
                .addIssue(createIssue(SEVERITY_LEVEL_INFORMATION))

        assertThat(builder.build()).isNotNull()
    }

    @Test
    fun build_withInformationStatusAndNoIssues_doesNotThrow() {
        val builder = SafetySourceData.Builder().setStatus(createStatus(SEVERITY_LEVEL_INFORMATION))

        assertThat(builder.build()).isNotNull()
    }

    @Test
    fun build_withInformationStatusAndInformationIssues_doesNotThrow() {
        val builder =
            SafetySourceData.Builder()
                .setStatus(createStatus(SEVERITY_LEVEL_INFORMATION))
                .addIssue(createIssue(SEVERITY_LEVEL_INFORMATION))

        assertThat(builder.build()).isNotNull()
    }

    @Test
    fun build_withRecommendationStatusAndNoIssues_doesNotThrow() {
        val builder =
            SafetySourceData.Builder().setStatus(createStatus(SEVERITY_LEVEL_RECOMMENDATION))

        assertThat(builder.build()).isNotNull()
    }

    @Test
    fun build_withRecommendationStatusAndInformationIssues_doesNotThrow() {
        val builder =
            SafetySourceData.Builder()
                .setStatus(createStatus(SEVERITY_LEVEL_RECOMMENDATION))
                .addIssue(createIssue(SEVERITY_LEVEL_INFORMATION))

        assertThat(builder.build()).isNotNull()
    }

    @Test
    fun build_withRecommendationStatusAndRecommendationIssues_doesNotThrow() {
        val builder =
            SafetySourceData.Builder()
                .setStatus(createStatus(SEVERITY_LEVEL_RECOMMENDATION))
                .addIssue(createIssue(SEVERITY_LEVEL_RECOMMENDATION))

        assertThat(builder.build()).isNotNull()
    }

    @Test
    fun build_withUnspecifiedStatusAndRecommendationIssues_throwsIllegalArgumentException() {
        val builder =
            SafetySourceData.Builder()
                .setStatus(createStatus(SEVERITY_LEVEL_UNSPECIFIED))
                .addIssue(createIssue(SEVERITY_LEVEL_RECOMMENDATION))

        val exception = assertFailsWith(IllegalArgumentException::class) { builder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Safety source data cannot have issues that are more severe than its status")
    }

    @Test
    fun build_withInformationStatusAndRecommendationIssues_throwsIllegalArgumentException() {
        val builder =
            SafetySourceData.Builder()
                .setStatus(createStatus(SEVERITY_LEVEL_INFORMATION))
                .addIssue(createIssue(SEVERITY_LEVEL_RECOMMENDATION))

        val exception = assertFailsWith(IllegalArgumentException::class) { builder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Safety source data cannot have issues that are more severe than its status")
    }

    @Test
    fun build_withRecommendationStatusAndCriticalIssues_throwsIllegalArgumentException() {
        val builder =
            SafetySourceData.Builder()
                .setStatus(createStatus(SEVERITY_LEVEL_RECOMMENDATION))
                .addIssue(createIssue(SEVERITY_LEVEL_CRITICAL_WARNING))

        val exception = assertFailsWith(IllegalArgumentException::class) { builder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Safety source data cannot have issues that are more severe than its status")
    }

    @Test
    fun build_duplicateIssueIds_throwsIllegalArgumentException() {
        val builder =
            SafetySourceData.Builder()
                .addIssue(createIssue(SEVERITY_LEVEL_RECOMMENDATION, id = 0))
                .addIssue(createIssue(SEVERITY_LEVEL_CRITICAL_WARNING, id = 0))

        val exception = assertFailsWith(IllegalArgumentException::class) { builder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Safety source data cannot have duplicate issue ids")
    }

    @Test
    fun describeContents_returns0() {
        val safetySourceData =
            SafetySourceData.Builder().setStatus(createStatus(SEVERITY_LEVEL_INFORMATION)).build()

        assertThat(safetySourceData.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        val safetySourceData =
            SafetySourceData.Builder()
                .setStatus(createStatus(SEVERITY_LEVEL_RECOMMENDATION))
                .addIssue(createIssue(SEVERITY_LEVEL_RECOMMENDATION, 1))
                .addIssue(createIssue(SEVERITY_LEVEL_INFORMATION, 2))
                .build()

        assertThat(safetySourceData).recreatesEqual(SafetySourceData.CREATOR)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun parcelRoundTrip_withExtras_recreatesEqual() {
        val safetySourceData =
            SafetySourceData.Builder()
                .setStatus(createStatus(SEVERITY_LEVEL_RECOMMENDATION))
                .addIssue(createIssue(SEVERITY_LEVEL_RECOMMENDATION, 1))
                .addIssue(createIssue(SEVERITY_LEVEL_INFORMATION, 2))
                .setExtras(bundleOf(EXTRA_KEY to EXTRA_VALUE))
                .build()
        val recreatedSafetySourceData = forceParcel(safetySourceData, SafetySourceData.CREATOR)

        assertThat(recreatedSafetySourceData).isEqualTo(safetySourceData)
        assertThat(recreatedSafetySourceData.extras.keySet()).containsExactly(EXTRA_KEY)
        assertThat(recreatedSafetySourceData.extras.getString(EXTRA_KEY, "")).isEqualTo(EXTRA_VALUE)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        val firstStatus = createStatus(SEVERITY_LEVEL_INFORMATION, 1)
        val secondStatus = createStatus(SEVERITY_LEVEL_INFORMATION, 2)
        val firstIssue = createIssue(SEVERITY_LEVEL_INFORMATION, 1)
        val secondIssue = createIssue(SEVERITY_LEVEL_INFORMATION, 2)
        EqualsHashCodeToStringTester.ofParcelable(parcelableCreator = SafetySourceData.CREATOR)
            .addEqualityGroup(
                SafetySourceData.Builder().setStatus(firstStatus).build(),
                SafetySourceData.Builder().setStatus(firstStatus).build()
            )
            .addEqualityGroup(
                SafetySourceData.Builder().addIssue(firstIssue).addIssue(secondIssue).build(),
                SafetySourceData.Builder().addIssue(firstIssue).addIssue(secondIssue).build()
            )
            .addEqualityGroup(
                SafetySourceData.Builder()
                    .setStatus(firstStatus)
                    .addIssue(firstIssue)
                    .addIssue(secondIssue)
                    .build(),
                SafetySourceData.Builder()
                    .setStatus(firstStatus)
                    .addIssue(firstIssue)
                    .addIssue(secondIssue)
                    .build()
            )
            .addEqualityGroup(SafetySourceData.Builder().setStatus(secondStatus).build())
            .addEqualityGroup(
                SafetySourceData.Builder().addIssue(secondIssue).addIssue(firstIssue).build()
            )
            .addEqualityGroup(SafetySourceData.Builder().addIssue(firstIssue).build())
            .addEqualityGroup(
                SafetySourceData.Builder()
                    .setStatus(secondStatus)
                    .addIssue(firstIssue)
                    .addIssue(secondIssue)
                    .build()
            )
            .test()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun equalsHashCode_atLeastU_usingEqualsHashCodeToStringTester() {
        val firstStatus = createStatus(SEVERITY_LEVEL_INFORMATION, 1)
        val secondStatus = createStatus(SEVERITY_LEVEL_INFORMATION, 2)
        val firstIssue = createIssue(SEVERITY_LEVEL_INFORMATION, 1)
        val secondIssue = createIssue(SEVERITY_LEVEL_INFORMATION, 2)
        val filledExtras = bundleOf(EXTRA_KEY to EXTRA_VALUE)
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetySourceData.CREATOR,
                ignoreToString = true,
                createCopy = { SafetySourceData.Builder(it).build() }
            )
            .addEqualityGroup(
                SafetySourceData.Builder().setStatus(firstStatus).build(),
                SafetySourceData.Builder().setStatus(firstStatus).setExtras(filledExtras).build()
            )
            .addEqualityGroup(
                SafetySourceData.Builder().addIssue(firstIssue).addIssue(secondIssue).build(),
                SafetySourceData.Builder()
                    .addIssue(firstIssue)
                    .addIssue(secondIssue)
                    .setExtras(filledExtras)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceData.Builder()
                    .setStatus(firstStatus)
                    .addIssue(firstIssue)
                    .addIssue(secondIssue)
                    .build(),
                SafetySourceData.Builder()
                    .setStatus(firstStatus)
                    .addIssue(firstIssue)
                    .addIssue(secondIssue)
                    .setExtras(filledExtras)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceData.Builder().setStatus(secondStatus).build(),
                SafetySourceData.Builder().setStatus(secondStatus).setExtras(filledExtras).build()
            )
            .addEqualityGroup(
                SafetySourceData.Builder().addIssue(secondIssue).addIssue(firstIssue).build(),
                SafetySourceData.Builder()
                    .addIssue(secondIssue)
                    .addIssue(firstIssue)
                    .setExtras(filledExtras)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceData.Builder().addIssue(firstIssue).build(),
                SafetySourceData.Builder().addIssue(firstIssue).setExtras(filledExtras).build()
            )
            .addEqualityGroup(
                SafetySourceData.Builder()
                    .setStatus(secondStatus)
                    .addIssue(firstIssue)
                    .addIssue(secondIssue)
                    .build(),
                SafetySourceData.Builder()
                    .setStatus(secondStatus)
                    .addIssue(firstIssue)
                    .addIssue(secondIssue)
                    .setExtras(filledExtras)
                    .build()
            )
            .test()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun toString_withExtras_containsHasExtras() {
        val safetySourceDataWithExtras =
            SafetySourceData.Builder()
                .setStatus(createStatus(SEVERITY_LEVEL_INFORMATION))
                .setExtras(bundleOf(EXTRA_KEY to EXTRA_VALUE))
                .build()

        val stringRepresentation = safetySourceDataWithExtras.toString()

        assertThat(stringRepresentation).contains("(has extras)")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun toString_withoutExtras_doesNotContainHasExtras() {
        val safetySourceDataWithoutExtras =
            SafetySourceData.Builder().setStatus(createStatus(SEVERITY_LEVEL_INFORMATION)).build()

        val stringRepresentation = safetySourceDataWithoutExtras.toString()

        assertThat(stringRepresentation).doesNotContain("(has extras)")
    }

    private fun createStatus(severityLevel: Int, id: Int = 0) =
        SafetySourceStatus.Builder("Status title $id", "Status summary $id", severityLevel)
            .setPendingIntent(
                PendingIntent.getActivity(
                    context,
                    /* requestCode = */ 0,
                    Intent("Status PendingIntent $id"),
                    FLAG_IMMUTABLE
                )
            )
            .build()

    private fun createIssue(severityLevel: Int, id: Int = 0) =
        SafetySourceIssue.Builder(
                "Issue id $id",
                "Issue summary $id",
                "Issue summary $id",
                severityLevel,
                "Issue type id $id"
            )
            .addAction(
                SafetySourceIssue.Action.Builder(
                        "Action id $id",
                        "Action label $id",
                        PendingIntent.getActivity(
                            context,
                            /* requestCode = */ 0,
                            Intent("Issue PendingIntent $id"),
                            FLAG_IMMUTABLE
                        )
                    )
                    .build()
            )
            .build()

    private companion object {
        /** Key of extra data in [Bundle]. */
        const val EXTRA_KEY = "extra_key"

        /** Value of extra data in [Bundle]. */
        const val EXTRA_VALUE = "extra_value"
    }
}
