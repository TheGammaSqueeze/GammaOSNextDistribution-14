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
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStaticEntryGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyCenterStaticEntryGroup]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterStaticEntryGroupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val pendingIntent1 =
        PendingIntent.getActivity(context, 0, Intent("Fake Data 1"), PendingIntent.FLAG_IMMUTABLE)
    private val pendingIntent2 =
        PendingIntent.getActivity(context, 0, Intent("Fake Data 2"), PendingIntent.FLAG_IMMUTABLE)
    private val pendingIntent3 =
        PendingIntent.getActivity(context, 0, Intent("Fake Data 3"), PendingIntent.FLAG_IMMUTABLE)

    private val staticEntry1 =
        SafetyCenterStaticEntry.Builder("an entry title")
            .setSummary("an entry summary")
            .setPendingIntent(pendingIntent1)
            .build()
    private val staticEntry2 =
        SafetyCenterStaticEntry.Builder("another entry title")
            .setSummary("another entry summary")
            .setPendingIntent(pendingIntent2)
            .build()
    private val staticEntry3 =
        SafetyCenterStaticEntry.Builder("yet another entry title")
            .setSummary("yet another entry summary")
            .setPendingIntent(pendingIntent3)
            .build()

    private val staticEntryGroup =
        SafetyCenterStaticEntryGroup("a title", listOf(staticEntry1, staticEntry2))

    @Test
    fun getTitle_returnsTitle() {
        assertThat(SafetyCenterStaticEntryGroup("a title", listOf()).title).isEqualTo("a title")
        assertThat(SafetyCenterStaticEntryGroup("another title", listOf()).title)
            .isEqualTo("another title")
    }

    @Test
    fun getStaticEntries_returnsStaticEntries() {
        assertThat(SafetyCenterStaticEntryGroup("", listOf(staticEntry1)).staticEntries)
            .containsExactly(staticEntry1)
        assertThat(
                SafetyCenterStaticEntryGroup("", listOf(staticEntry1, staticEntry2)).staticEntries
            )
            .containsExactly(staticEntry1, staticEntry2)
            .inOrder()
        assertThat(SafetyCenterStaticEntryGroup("", listOf()).staticEntries).isEmpty()
    }

    @Test
    fun getStaticEntries_mutationsAreNotAllowed() {
        val staticEntries = staticEntryGroup.staticEntries

        assertFailsWith(UnsupportedOperationException::class) { staticEntries.add(staticEntry3) }
    }

    @Test
    fun describeContents_returns0() {
        assertThat(staticEntryGroup.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(staticEntryGroup).recreatesEqual(SafetyCenterStaticEntryGroup.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetyCenterStaticEntryGroup.CREATOR
            )
            .addEqualityGroup(
                staticEntryGroup,
                SafetyCenterStaticEntryGroup("a title", listOf(staticEntry1, staticEntry2))
            )
            .addEqualityGroup(
                SafetyCenterStaticEntryGroup("a title", listOf(staticEntry1)),
                SafetyCenterStaticEntryGroup("a title", listOf(staticEntry1))
            )
            .addEqualityGroup(
                SafetyCenterStaticEntryGroup("a different title", listOf(staticEntry1))
            )
            .addEqualityGroup(SafetyCenterStaticEntryGroup("a title", listOf(staticEntry2)))
            .test()
    }
}
