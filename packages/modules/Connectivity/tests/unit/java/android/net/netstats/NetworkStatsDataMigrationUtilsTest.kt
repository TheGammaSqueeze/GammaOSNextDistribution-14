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

import android.net.NetworkStatsCollection
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import com.android.frameworks.tests.net.R
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.SC_V2
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import java.io.DataInputStream
import java.net.ProtocolException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

private const val BUCKET_DURATION_MS = 2 * 60 * 60 * 1000L

@RunWith(DevSdkIgnoreRunner::class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2) // TODO: Use to Build.VERSION_CODES.SC_V2 when available
class NetworkStatsDataMigrationUtilsTest {
    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun testReadPlatformCollection() {
        // Verify the method throws for wrong file format.
        assertFailsWith<ProtocolException> {
            NetworkStatsDataMigrationUtils.readPlatformCollection(
                    NetworkStatsCollection.Builder(BUCKET_DURATION_MS),
                    getInputStreamForResource(R.raw.netstats_uid_v4))
        }

        val builder = NetworkStatsCollection.Builder(BUCKET_DURATION_MS)
        NetworkStatsDataMigrationUtils.readPlatformCollection(builder,
                getInputStreamForResource(R.raw.netstats_uid_v16))
        // The values are obtained by dumping from NetworkStatsCollection that
        // read by the logic inside the service.
        assertValues(builder.build(), 55, 1814302L, 21050L, 31001636L, 26152L)
    }

    private fun assertValues(
        collection: NetworkStatsCollection,
        expectedSize: Int,
        expectedTxBytes: Long,
        expectedTxPackets: Long,
        expectedRxBytes: Long,
        expectedRxPackets: Long
    ) {
        var txBytes = 0L
        var txPackets = 0L
        var rxBytes = 0L
        var rxPackets = 0L
        val entries = collection.entries

        for (history in entries.values) {
            for (historyEntry in history.entries) {
                txBytes += historyEntry.txBytes
                txPackets += historyEntry.txPackets
                rxBytes += historyEntry.rxBytes
                rxPackets += historyEntry.rxPackets
            }
        }
        if (expectedSize != entries.size ||
                expectedTxBytes != txBytes ||
                expectedTxPackets != txPackets ||
                expectedRxBytes != rxBytes ||
                expectedRxPackets != rxPackets) {
            fail("expected size=$expectedSize" +
                    "txb=$expectedTxBytes txp=$expectedTxPackets " +
                    "rxb=$expectedRxBytes rxp=$expectedRxPackets bus was " +
                    "size=${entries.size} txb=$txBytes txp=$txPackets " +
                    "rxb=$rxBytes rxp=$rxPackets")
        }
        assertEquals(txBytes + rxBytes, collection.totalBytes)
    }

    private fun getInputStreamForResource(resourceId: Int): DataInputStream {
        return DataInputStream(InstrumentationRegistry.getContext()
                .getResources().openRawResource(resourceId))
    }
}
