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

package com.android.net.module.util

import android.net.NetworkStats
import android.text.TextUtils
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

@RunWith(AndroidJUnit4::class)
@SmallTest
class NetworkStatsUtilsTest {
    @Test
    fun testMultiplySafeByRational() {
        // Verify basic cases that the method equals to a * b / c.
        assertEquals(3 * 5 / 2, NetworkStatsUtils.multiplySafeByRational(3, 5, 2))

        // Verify input with zeros.
        assertEquals(0 * 7 / 3, NetworkStatsUtils.multiplySafeByRational(0, 7, 3))
        assertEquals(7 * 0 / 3, NetworkStatsUtils.multiplySafeByRational(7, 0, 3))
        assertEquals(0 * 0 / 1, NetworkStatsUtils.multiplySafeByRational(0, 0, 1))
        assertEquals(0, NetworkStatsUtils.multiplySafeByRational(0, Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(0, NetworkStatsUtils.multiplySafeByRational(Long.MAX_VALUE, 0, Long.MAX_VALUE))
        assertFailsWith<ArithmeticException> {
            NetworkStatsUtils.multiplySafeByRational(7, 3, 0)
        }
        assertFailsWith<ArithmeticException> {
            NetworkStatsUtils.multiplySafeByRational(0, 0, 0)
        }

        // Verify cases where a * b overflows.
        assertEquals(101, NetworkStatsUtils.multiplySafeByRational(
                101, Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(721, NetworkStatsUtils.multiplySafeByRational(
                Long.MAX_VALUE, 721, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, NetworkStatsUtils.multiplySafeByRational(
                Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE))
        assertFailsWith<ArithmeticException> {
            NetworkStatsUtils.multiplySafeByRational(Long.MAX_VALUE, Long.MAX_VALUE, 0)
        }
    }

    @Test
    fun testConstrain() {
        assertFailsWith<IllegalArgumentException> {
            NetworkStatsUtils.constrain(5, 6, 3) // low > high
        }
        assertEquals(3, NetworkStatsUtils.constrain(5, 1, 3))
        assertEquals(3, NetworkStatsUtils.constrain(3, 1, 3))
        assertEquals(2, NetworkStatsUtils.constrain(2, 1, 3))
        assertEquals(1, NetworkStatsUtils.constrain(1, 1, 3))
        assertEquals(1, NetworkStatsUtils.constrain(0, 1, 3))

        assertEquals(11, NetworkStatsUtils.constrain(15, 11, 11))
        assertEquals(11, NetworkStatsUtils.constrain(11, 11, 11))
        assertEquals(11, NetworkStatsUtils.constrain(1, 11, 11))
    }

    @Test
    fun testBucketToEntry() {
        val bucket = makeMockBucket(android.app.usage.NetworkStats.Bucket.UID_ALL,
                android.app.usage.NetworkStats.Bucket.TAG_NONE,
                android.app.usage.NetworkStats.Bucket.STATE_DEFAULT,
                android.app.usage.NetworkStats.Bucket.METERED_YES,
                android.app.usage.NetworkStats.Bucket.ROAMING_NO,
                android.app.usage.NetworkStats.Bucket.DEFAULT_NETWORK_ALL, 1024, 8, 2048, 12)
        val entry = NetworkStatsUtils.fromBucket(bucket)
        val expectedEntry = NetworkStats.Entry(null /* IFACE_ALL */, NetworkStats.UID_ALL,
            NetworkStats.SET_DEFAULT, NetworkStats.TAG_NONE, NetworkStats.METERED_YES,
            NetworkStats.ROAMING_NO, NetworkStats.DEFAULT_NETWORK_ALL, 1024, 8, 2048, 12,
            0 /* operations */)

        // TODO: Use assertEquals once all downstreams accept null iface in
        // NetworkStats.Entry#equals.
        assertEntryEquals(expectedEntry, entry)
    }

    private fun makeMockBucket(
        uid: Int,
        tag: Int,
        state: Int,
        metered: Int,
        roaming: Int,
        defaultNetwork: Int,
        rxBytes: Long,
        rxPackets: Long,
        txBytes: Long,
        txPackets: Long
    ): android.app.usage.NetworkStats.Bucket {
        val ret: android.app.usage.NetworkStats.Bucket =
                mock(android.app.usage.NetworkStats.Bucket::class.java)
        doReturn(uid).`when`(ret).getUid()
        doReturn(tag).`when`(ret).getTag()
        doReturn(state).`when`(ret).getState()
        doReturn(metered).`when`(ret).getMetered()
        doReturn(roaming).`when`(ret).getRoaming()
        doReturn(defaultNetwork).`when`(ret).getDefaultNetworkStatus()
        doReturn(rxBytes).`when`(ret).getRxBytes()
        doReturn(rxPackets).`when`(ret).getRxPackets()
        doReturn(txBytes).`when`(ret).getTxBytes()
        doReturn(txPackets).`when`(ret).getTxPackets()
        return ret
    }

    /**
     * Assert that the two {@link NetworkStats.Entry} are equals.
     */
    private fun assertEntryEquals(left: NetworkStats.Entry, right: NetworkStats.Entry) {
        TextUtils.equals(left.iface, right.iface)
        assertEquals(left.uid, right.uid)
        assertEquals(left.set, right.set)
        assertEquals(left.tag, right.tag)
        assertEquals(left.metered, right.metered)
        assertEquals(left.roaming, right.roaming)
        assertEquals(left.defaultNetwork, right.defaultNetwork)
        assertEquals(left.rxBytes, right.rxBytes)
        assertEquals(left.rxPackets, right.rxPackets)
        assertEquals(left.txBytes, right.txBytes)
        assertEquals(left.txPackets, right.txPackets)
        assertEquals(left.operations, right.operations)
    }
}