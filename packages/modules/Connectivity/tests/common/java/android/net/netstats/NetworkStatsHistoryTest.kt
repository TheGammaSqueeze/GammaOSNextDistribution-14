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

package android.net.netstats

import android.net.NetworkStatsHistory
import android.text.format.DateUtils
import androidx.test.filters.SmallTest
import com.android.testutils.ConnectivityModuleTest
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.SC_V2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals

@ConnectivityModuleTest
@RunWith(JUnit4::class)
@SmallTest
class NetworkStatsHistoryTest {
    @Rule
    @JvmField
    val ignoreRule = DevSdkIgnoreRule(ignoreClassUpTo = SC_V2)

    @Test
    fun testBuilder() {
        val entry1 = NetworkStatsHistory.Entry(10, 30, 40, 4, 50, 5, 60)
        val entry2 = NetworkStatsHistory.Entry(30, 15, 3, 41, 7, 1, 0)
        val entry3 = NetworkStatsHistory.Entry(7, 301, 11, 14, 31, 2, 80)
        val statsEmpty = NetworkStatsHistory
                .Builder(DateUtils.HOUR_IN_MILLIS, /* initialCapacity */ 10).build()
        assertEquals(0, statsEmpty.entries.size)
        assertEquals(DateUtils.HOUR_IN_MILLIS, statsEmpty.bucketDuration)
        val statsSingle = NetworkStatsHistory
                .Builder(DateUtils.HOUR_IN_MILLIS, /* initialCapacity */ 8)
                .addEntry(entry1)
                .build()
        statsSingle.assertEntriesEqual(entry1)
        assertEquals(DateUtils.HOUR_IN_MILLIS, statsSingle.bucketDuration)

        val statsMultiple = NetworkStatsHistory
                .Builder(DateUtils.SECOND_IN_MILLIS, /* initialCapacity */ 0)
                .addEntry(entry1).addEntry(entry2).addEntry(entry3)
                .build()
        assertEquals(DateUtils.SECOND_IN_MILLIS, statsMultiple.bucketDuration)
        // Verify the entries exist and sorted.
        statsMultiple.assertEntriesEqual(entry3, entry1, entry2)
    }

    @Test
    fun testBuilderSortAndDeduplicate() {
        val entry1 = NetworkStatsHistory.Entry(10, 30, 40, 4, 50, 5, 60)
        val entry2 = NetworkStatsHistory.Entry(30, 15, 3, 41, 7, 1, 0)
        val entry3 = NetworkStatsHistory.Entry(30, 999, 11, 14, 31, 2, 80)
        val entry4 = NetworkStatsHistory.Entry(10, 15, 1, 17, 5, 33, 10)
        val entry5 = NetworkStatsHistory.Entry(6, 1, 9, 11, 29, 1, 7)

        // Entries for verification.
        // Note that active time of 2 + 3 is truncated to bucket duration since the active time
        // should not go over bucket duration.
        val entry2and3 = NetworkStatsHistory.Entry(30, 1000, 14, 55, 38, 3, 80)
        val entry1and4 = NetworkStatsHistory.Entry(10, 45, 41, 21, 55, 38, 70)

        val statsMultiple = NetworkStatsHistory
                .Builder(DateUtils.SECOND_IN_MILLIS, /* initialCapacity */ 0)
                .addEntry(entry1).addEntry(entry2).addEntry(entry3)
                .addEntry(entry4).addEntry(entry5).build()
        assertEquals(DateUtils.SECOND_IN_MILLIS, statsMultiple.bucketDuration)
        // Verify the entries sorted and deduplicated.
        statsMultiple.assertEntriesEqual(entry5, entry1and4, entry2and3)
    }

    fun NetworkStatsHistory.assertEntriesEqual(vararg entries: NetworkStatsHistory.Entry) {
        assertEquals(entries.size, this.entries.size)
        entries.forEachIndexed { i, element ->
            assertEquals(element, this.entries[i])
        }
    }
}