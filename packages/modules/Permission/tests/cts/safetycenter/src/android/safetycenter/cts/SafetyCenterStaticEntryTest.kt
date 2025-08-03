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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyCenterStaticEntry]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterStaticEntryTest {
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

    private val title1 = "a title"
    private val title2 = "another title"

    private val summary1 = "a summary"
    private val summary2 = "another summary"

    private val staticEntry1 =
        SafetyCenterStaticEntry.Builder(title1)
            .setSummary(summary1)
            .setPendingIntent(pendingIntent1)
            .build()
    private val staticEntry2 =
        SafetyCenterStaticEntry.Builder(title2)
            .setSummary(summary2)
            .setPendingIntent(pendingIntent2)
            .build()
    private val staticEntryMinimal = SafetyCenterStaticEntry.Builder("").build()

    @Test
    fun getTitle_returnsTitle() {
        assertThat(staticEntry1.title).isEqualTo(title1)
        assertThat(staticEntry2.title).isEqualTo(title2)
        assertThat(SafetyCenterStaticEntry.Builder(staticEntry1).setTitle(title2).build().title)
            .isEqualTo(title2)
    }

    @Test
    fun getSummary_returnsSummary() {
        assertThat(staticEntry1.summary).isEqualTo(summary1)
        assertThat(staticEntry2.summary).isEqualTo(summary2)
        assertThat(staticEntryMinimal.summary).isNull()
    }

    @Test
    fun getPendingIntent_returnsPendingIntent() {
        assertThat(staticEntry1.pendingIntent).isEqualTo(pendingIntent1)
        assertThat(staticEntry2.pendingIntent).isEqualTo(pendingIntent2)
        assertThat(staticEntryMinimal.pendingIntent).isNull()
    }

    @Test
    fun describeContents_returns0() {
        assertThat(staticEntry1.describeContents()).isEqualTo(0)
        assertThat(staticEntry2.describeContents()).isEqualTo(0)
        assertThat(staticEntryMinimal.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(staticEntry1).recreatesEqual(SafetyCenterStaticEntry.CREATOR)
        assertThat(staticEntry2).recreatesEqual(SafetyCenterStaticEntry.CREATOR)
        assertThat(staticEntryMinimal).recreatesEqual(SafetyCenterStaticEntry.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetyCenterStaticEntry.CREATOR,
                createCopy = { SafetyCenterStaticEntry.Builder(it).build() }
            )
            .addEqualityGroup(
                staticEntry1,
                SafetyCenterStaticEntry.Builder("a title")
                    .setSummary("a summary")
                    .setPendingIntent(pendingIntent1)
                    .build()
            )
            .addEqualityGroup(staticEntry2)
            .addEqualityGroup(staticEntryMinimal, SafetyCenterStaticEntry.Builder("").build())
            .addEqualityGroup(
                SafetyCenterStaticEntry.Builder("titlee")
                    .setSummary("sumaree")
                    .setPendingIntent(pendingIntent1)
                    .build(),
                SafetyCenterStaticEntry.Builder("titlee")
                    .setSummary("sumaree")
                    .setPendingIntent(pendingIntent1)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterStaticEntry.Builder(staticEntry1).setTitle("a different title").build()
            )
            .addEqualityGroup(
                SafetyCenterStaticEntry.Builder(staticEntry1)
                    .setSummary("a different summary")
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterStaticEntry.Builder(staticEntry1)
                    .setPendingIntent(pendingIntent2)
                    .build()
            )
            .test()
    }
}
