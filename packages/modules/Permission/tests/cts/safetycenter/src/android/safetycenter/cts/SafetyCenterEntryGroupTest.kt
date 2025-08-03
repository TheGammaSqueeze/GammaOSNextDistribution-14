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
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntryGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyCenterEntryGroup]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterEntryGroupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val pendingIntent =
        PendingIntent.getActivity(context, 0, Intent("Fake Data"), PendingIntent.FLAG_IMMUTABLE)

    private val entry1 =
        SafetyCenterEntry.Builder("eNtRy_iD_OnE", "An entry title")
            .setPendingIntent(pendingIntent)
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
            .build()
    private val entry2 =
        SafetyCenterEntry.Builder("eNtRy_iD_TwO", "Another entry title")
            .setPendingIntent(pendingIntent)
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    private val groupId1 = "gRoUp_iD_oNe"
    private val groupId2 = "gRoUp_iD_tWo"

    private val entryGroup1 =
        SafetyCenterEntryGroup.Builder(groupId1, "A group title")
            .setSummary("A group summary")
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
            .setEntries(listOf(entry1))
            .build()
    private val entryGroup2 =
        SafetyCenterEntryGroup.Builder(groupId2, "Another group title")
            .setSummary("Another group summary")
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .setEntries(listOf(entry2))
            .build()

    @Test
    fun getId_returnsId() {
        assertThat(entryGroup1.id).isEqualTo(groupId1)
        assertThat(entryGroup2.id).isEqualTo(groupId2)
    }

    @Test
    fun getTitle_returnsTitle() {
        assertThat(SafetyCenterEntryGroup.Builder(entryGroup1).setTitle("title one").build().title)
            .isEqualTo("title one")
        assertThat(SafetyCenterEntryGroup.Builder(entryGroup1).setTitle("title two").build().title)
            .isEqualTo("title two")
    }

    @Test
    fun getSummary_returnsSummary() {
        assertThat(SafetyCenterEntryGroup.Builder(entryGroup1).setSummary("one").build().summary)
            .isEqualTo("one")
        assertThat(SafetyCenterEntryGroup.Builder(entryGroup1).setSummary("two").build().summary)
            .isEqualTo("two")
        assertThat(SafetyCenterEntryGroup.Builder(entryGroup1).setSummary(null).build().summary)
            .isNull()
    }

    @Test
    fun getSeverityLevel_returnsSeverityLevel() {
        assertThat(
                SafetyCenterEntryGroup.Builder(entryGroup1)
                    .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
                    .build()
                    .severityLevel
            )
            .isEqualTo(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
        assertThat(
                SafetyCenterEntryGroup.Builder(entryGroup1)
                    .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
                    .build()
                    .severityLevel
            )
            .isEqualTo(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
    }

    @Test
    fun getSeverityUnspecifiedIconType_returnsSeverityUnspecifiedIconType() {
        assertThat(entryGroup1.severityUnspecifiedIconType)
            .isEqualTo(SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON)
        assertThat(
                SafetyCenterEntryGroup.Builder(entryGroup1)
                    .setSeverityUnspecifiedIconType(
                        SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY
                    )
                    .build()
                    .severityUnspecifiedIconType
            )
            .isEqualTo(SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
    }

    @Test
    fun getEntries_returnsEntries() {
        assertThat(entryGroup1.entries).containsExactly(entry1)
        assertThat(entryGroup2.entries).containsExactly(entry2)
    }

    @Test
    fun getEntries_mutationsAreNotAllowed() {
        val mutatedEntries = entryGroup1.entries

        assertFailsWith(UnsupportedOperationException::class) { mutatedEntries.add(entry2) }
    }

    @Test
    fun build_withInvalidEntrySeverityLevel_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetyCenterEntryGroup.Builder(entryGroup1).setSeverityLevel(-1)
            }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected EntrySeverityLevel for SafetyCenterEntryGroup: -1")
    }

    @Test
    fun build_withInvalidSeverityUnspecifiedIconType_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetyCenterEntryGroup.Builder(entryGroup1).setSeverityUnspecifiedIconType(-1)
            }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected SeverityUnspecifiedIconType for SafetyCenterEntryGroup: -1")
    }

    @Test
    fun describeContents_returns0() {
        assertThat(entryGroup1.describeContents()).isEqualTo(0)
        assertThat(entryGroup2.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(entryGroup1).recreatesEqual(SafetyCenterEntryGroup.CREATOR)
        assertThat(entryGroup2).recreatesEqual(SafetyCenterEntryGroup.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetyCenterEntryGroup.CREATOR,
                createCopy = { SafetyCenterEntryGroup.Builder(it).build() }
            )
            .addEqualityGroup(
                entryGroup1,
                SafetyCenterEntryGroup.Builder(groupId1, "A group title")
                    .setSummary("A group summary")
                    .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
                    .setEntries(listOf(entry1))
                    .build()
            )
            .addEqualityGroup(entryGroup2)
            .addEqualityGroup(
                SafetyCenterEntryGroup.Builder(entryGroup1).setId("different!").build()
            )
            .addEqualityGroup(
                SafetyCenterEntryGroup.Builder(entryGroup1).setTitle("different!").build()
            )
            .addEqualityGroup(
                SafetyCenterEntryGroup.Builder(entryGroup1).setSummary("different!").build()
            )
            .addEqualityGroup(
                SafetyCenterEntryGroup.Builder(entryGroup1)
                    .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterEntryGroup.Builder(entryGroup1)
                    .setSeverityUnspecifiedIconType(
                        SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY
                    )
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterEntryGroup.Builder(entryGroup1).setEntries(listOf(entry2)).build()
            )
            .addEqualityGroup(
                SafetyCenterEntryGroup.Builder(entryGroup1).setEntries(emptyList()).build()
            )
            .test()
    }
}
