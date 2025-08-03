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

package android.net.cts

import android.net.nsd.OffloadEngine.OFFLOAD_TYPE_FILTER_QUERIES
import android.net.nsd.OffloadServiceInfo
import android.os.Build
import androidx.test.filters.SmallTest
import com.android.testutils.ConnectivityModuleTest
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for {@link OffloadServiceInfo}. */
@IgnoreUpTo(Build.VERSION_CODES.S_V2)
@RunWith(DevSdkIgnoreRunner::class)
@SmallTest
@ConnectivityModuleTest
class OffloadServiceInfoTest {
    @Test
    fun testCreateOffloadServiceInfo() {
        val offloadServiceInfo = OffloadServiceInfo(
            OffloadServiceInfo.Key("_testService", "_testType"),
            listOf("_sub1", "_sub2"),
            "Android.local",
            byteArrayOf(0x1, 0x2, 0x3),
            1 /* priority */,
            OFFLOAD_TYPE_FILTER_QUERIES.toLong()
        )

        assertEquals(OffloadServiceInfo.Key("_testService", "_testType"), offloadServiceInfo.key)
        assertEquals(listOf("_sub1", "_sub2"), offloadServiceInfo.subtypes)
        assertEquals("Android.local", offloadServiceInfo.hostname)
        assertContentEquals(byteArrayOf(0x1, 0x2, 0x3), offloadServiceInfo.offloadPayload)
        assertEquals(1, offloadServiceInfo.priority)
        assertEquals(OFFLOAD_TYPE_FILTER_QUERIES.toLong(), offloadServiceInfo.offloadType)
    }
}
