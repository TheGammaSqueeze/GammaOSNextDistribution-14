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
import android.safetycenter.SafetyCenterEntryOrGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyCenterEntryOrGroup]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterEntryOrGroupTest {
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

    private val entryGroup1 =
        SafetyCenterEntryGroup.Builder("gRoUp_iD_oNe", "A group title")
            .setSummary("A group summary")
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
            .setEntries(listOf(entry1))
            .build()
    private val entryGroup2 =
        SafetyCenterEntryGroup.Builder("gRoUp_iD_tWo", "Another group title")
            .setSummary("Another group summary")
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .setEntries(listOf(entry2))
            .build()

    private val entryOrGroupWithEntry = SafetyCenterEntryOrGroup(entry1)
    private val entryOrGroupWithGroup = SafetyCenterEntryOrGroup(entryGroup1)

    @Test
    fun getEntry_returnsEntry() {
        assertThat(entryOrGroupWithEntry.entry).isEqualTo(entry1)
    }

    @Test
    fun getEntry_returnsEntry_whenNull() {
        assertThat(entryOrGroupWithGroup.entry).isNull()
    }

    @Test
    fun getEntryGroup_returnsEntryGroup() {
        assertThat(entryOrGroupWithGroup.entryGroup).isEqualTo(entryGroup1)
    }

    @Test
    fun getEntryGroup_returnsEntryGroup_whenNull() {
        assertThat(entryOrGroupWithEntry.entryGroup).isNull()
    }

    @Test
    fun describeContents_returns0() {
        assertThat(entryOrGroupWithEntry.describeContents()).isEqualTo(0)
        assertThat(entryOrGroupWithGroup.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(entryOrGroupWithEntry).recreatesEqual(SafetyCenterEntryOrGroup.CREATOR)
        assertThat(entryOrGroupWithGroup).recreatesEqual(SafetyCenterEntryOrGroup.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetyCenterEntryOrGroup.CREATOR
            )
            .addEqualityGroup(entryOrGroupWithEntry, SafetyCenterEntryOrGroup(entry1))
            .addEqualityGroup(entryOrGroupWithGroup, SafetyCenterEntryOrGroup(entryGroup1))
            .addEqualityGroup(SafetyCenterEntryOrGroup(entry2))
            .addEqualityGroup(SafetyCenterEntryOrGroup(entryGroup2))
            .test()
    }
}
