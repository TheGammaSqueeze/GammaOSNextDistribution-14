/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.net

import android.net.NetworkStats
import com.android.testutils.DevSdkIgnoreRunner
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(DevSdkIgnoreRunner::class)
class TrafficStatsRateLimitCacheTest {
    companion object {
        private const val expiryDurationMs = 1000L
    }

    private val clock = mock(Clock::class.java)
    private val entry = mock(NetworkStats.Entry::class.java)
    private val cache = TrafficStatsRateLimitCache(clock, expiryDurationMs)

    @Test
    fun testGet_returnsEntryIfNotExpired() {
        cache.put("iface", 2, entry)
        `when`(clock.millis()).thenReturn(500L) // Set clock to before expiry
        val result = cache.get("iface", 2)
        assertEquals(entry, result)
    }

    @Test
    fun testGet_returnsNullIfExpired() {
        cache.put("iface", 2, entry)
        `when`(clock.millis()).thenReturn(2000L) // Set clock to after expiry
        assertNull(cache.get("iface", 2))
    }

    @Test
    fun testGet_returnsNullForNonExistentKey() {
        val result = cache.get("otherIface", 99)
        assertNull(result)
    }

    @Test
    fun testPutAndGet_retrievesCorrectEntryForDifferentKeys() {
        val entry1 = mock(NetworkStats.Entry::class.java)
        val entry2 = mock(NetworkStats.Entry::class.java)

        cache.put("iface1", 2, entry1)
        cache.put("iface2", 4, entry2)

        assertEquals(entry1, cache.get("iface1", 2))
        assertEquals(entry2, cache.get("iface2", 4))
    }

    @Test
    fun testPut_overridesExistingEntry() {
        val entry1 = mock(NetworkStats.Entry::class.java)
        val entry2 = mock(NetworkStats.Entry::class.java)

        cache.put("iface", 2, entry1)
        cache.put("iface", 2, entry2) // Put with the same key

        assertEquals(entry2, cache.get("iface", 2))
    }

    @Test
    fun testClear() {
        cache.put("iface", 2, entry)
        cache.clear()
        assertNull(cache.get("iface", 2))
    }
}
