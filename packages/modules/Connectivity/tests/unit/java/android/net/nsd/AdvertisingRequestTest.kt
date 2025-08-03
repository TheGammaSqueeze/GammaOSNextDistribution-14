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

package android.net.nsd

import android.net.nsd.AdvertisingRequest.NSD_ADVERTISING_UPDATE_ONLY
import android.net.nsd.NsdManager.PROTOCOL_DNS_SD
import android.os.Build
import androidx.test.filters.SmallTest
import com.android.testutils.ConnectivityModuleTest
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.parcelingRoundTrip
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith

// TODO: move this class to CTS tests when AdvertisingRequest is made public
/** Unit tests for {@link AdvertisingRequest}. */
@IgnoreUpTo(Build.VERSION_CODES.S_V2)
@RunWith(DevSdkIgnoreRunner::class)
@SmallTest
@ConnectivityModuleTest
class AdvertisingRequestTest {
    @Test
    fun testParcelingIsLossLess() {
        val info = NsdServiceInfo().apply {
            serviceType = "_ipp._tcp"
        }
        val beforeParcel = AdvertisingRequest.Builder(info, PROTOCOL_DNS_SD)
                .setAdvertisingConfig(NSD_ADVERTISING_UPDATE_ONLY)
                .setTtl(Duration.ofSeconds(30L))
                .build()

        val afterParcel = parcelingRoundTrip(beforeParcel)

        assertEquals(beforeParcel.serviceInfo.serviceType, afterParcel.serviceInfo.serviceType)
        assertEquals(beforeParcel.advertisingConfig, afterParcel.advertisingConfig)
    }

@Test
fun testBuilder_setNullTtl_success() {
    val info = NsdServiceInfo().apply {
        serviceType = "_ipp._tcp"
    }
    val request = AdvertisingRequest.Builder(info, PROTOCOL_DNS_SD)
            .setTtl(null)
            .build()

    assertNull(request.ttl)
}

    @Test
    fun testBuilder_setPropertiesSuccess() {
        val info = NsdServiceInfo().apply {
            serviceType = "_ipp._tcp"
        }
        val request = AdvertisingRequest.Builder(info, PROTOCOL_DNS_SD)
                .setAdvertisingConfig(NSD_ADVERTISING_UPDATE_ONLY)
                .setTtl(Duration.ofSeconds(100L))
                .build()

        assertEquals("_ipp._tcp", request.serviceInfo.serviceType)
        assertEquals(PROTOCOL_DNS_SD, request.protocolType)
        assertEquals(NSD_ADVERTISING_UPDATE_ONLY, request.advertisingConfig)
        assertEquals(Duration.ofSeconds(100L), request.ttl)
    }

    @Test
    fun testEquality() {
        val info = NsdServiceInfo().apply {
            serviceType = "_ipp._tcp"
        }
        val request1 = AdvertisingRequest.Builder(info, PROTOCOL_DNS_SD).build()
        val request2 = AdvertisingRequest.Builder(info, PROTOCOL_DNS_SD).build()
        val request3 = AdvertisingRequest.Builder(info, PROTOCOL_DNS_SD)
                .setAdvertisingConfig(NSD_ADVERTISING_UPDATE_ONLY)
                .setTtl(Duration.ofSeconds(120L))
                .build()
        val request4 = AdvertisingRequest.Builder(info, PROTOCOL_DNS_SD)
                .setAdvertisingConfig(NSD_ADVERTISING_UPDATE_ONLY)
                .setTtl(Duration.ofSeconds(120L))
                .build()

        assertEquals(request1, request2)
        assertEquals(request3, request4)
        assertNotEquals(request1, request3)
        assertNotEquals(request2, request4)
    }
}
