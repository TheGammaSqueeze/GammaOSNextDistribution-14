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

import android.net.NetworkIdentity
import android.net.NetworkStatsCollection
import android.net.NetworkStatsHistory
import androidx.test.filters.SmallTest
import com.android.testutils.ConnectivityModuleTest
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.SC_V2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals
import kotlin.test.fail

@ConnectivityModuleTest
@RunWith(JUnit4::class)
@SmallTest
class NetworkStatsCollectionTest {
    @Rule
    @JvmField
    val ignoreRule = DevSdkIgnoreRule(ignoreClassUpTo = SC_V2)

    @Test
    fun testBuilder() {
        val ident = setOf<NetworkIdentity>()
        val key1 = NetworkStatsCollection.Key(ident, /* uid */ 0, /* set */ 0, /* tag */ 0)
        val key2 = NetworkStatsCollection.Key(ident, /* uid */ 1, /* set */ 0, /* tag */ 0)
        val bucketDuration = 10L
        val entry1 = NetworkStatsHistory.Entry(10, 10, 40, 4, 50, 5, 60)
        val entry2 = NetworkStatsHistory.Entry(30, 10, 3, 41, 7, 1, 0)
        val history1 = NetworkStatsHistory.Builder(10, 5)
                .addEntry(entry1)
                .addEntry(entry2)
                .build()
        val history2 = NetworkStatsHistory(10, 5)
        val actualCollection = NetworkStatsCollection.Builder(bucketDuration)
                .addEntry(key1, history1)
                .addEntry(key2, history2)
                .build()

        // The builder will omit any entry with empty history. Thus, only history1
        // is expected in the result collection.
        val actualEntries = actualCollection.entries
        assertEquals(1, actualEntries.size)
        val actualHistory = actualEntries[key1] ?: fail("There should be an entry for $key1")
        assertEquals(history1.entries, actualHistory.entries)
    }
}
