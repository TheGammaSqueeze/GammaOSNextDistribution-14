/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.annotation.TargetApi
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_BIP
import android.net.NetworkCapabilities.NET_CAPABILITY_CBS
import android.net.NetworkCapabilities.NET_CAPABILITY_EIMS
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PAID
import android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_TEST
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkCapabilities.TRANSPORT_WIFI_AWARE
import android.os.Build
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.modules.utils.build.SdkLevel
import com.android.net.module.util.NetworkCapabilitiesUtils.RESTRICTED_CAPABILITIES
import com.android.net.module.util.NetworkCapabilitiesUtils.UNRESTRICTED_CAPABILITIES
import com.android.net.module.util.NetworkCapabilitiesUtils.getDisplayTransport
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.IllegalArgumentException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@SmallTest
class NetworkCapabilitiesUtilsTest {

    @Test
    fun testGetAccountingTransport() {
        assertEquals(TRANSPORT_WIFI, getDisplayTransport(intArrayOf(TRANSPORT_WIFI)))
        assertEquals(TRANSPORT_CELLULAR, getDisplayTransport(intArrayOf(TRANSPORT_CELLULAR)))
        assertEquals(TRANSPORT_BLUETOOTH, getDisplayTransport(intArrayOf(TRANSPORT_BLUETOOTH)))
        assertEquals(TRANSPORT_ETHERNET, getDisplayTransport(intArrayOf(TRANSPORT_ETHERNET)))
        assertEquals(TRANSPORT_WIFI_AWARE, getDisplayTransport(intArrayOf(TRANSPORT_WIFI_AWARE)))

        assertEquals(TRANSPORT_VPN, getDisplayTransport(
                intArrayOf(TRANSPORT_VPN, TRANSPORT_WIFI)))
        assertEquals(TRANSPORT_VPN, getDisplayTransport(
                intArrayOf(TRANSPORT_CELLULAR, TRANSPORT_VPN)))

        assertEquals(TRANSPORT_WIFI, getDisplayTransport(
                intArrayOf(TRANSPORT_ETHERNET, TRANSPORT_WIFI)))
        assertEquals(TRANSPORT_ETHERNET, getDisplayTransport(
                intArrayOf(TRANSPORT_ETHERNET, TRANSPORT_TEST)))

        assertFailsWith(IllegalArgumentException::class) {
            getDisplayTransport(intArrayOf())
        }
    }

    // NetworkCapabilities constructor and Builder are not available until R. Mark TargetApi to
    // ignore the linter error since it's used in only unit test.
    @Test @TargetApi(Build.VERSION_CODES.R)
    fun testInferRestrictedCapability() {
        val nc = NetworkCapabilities()
        // Default capabilities don't have restricted capability.
        assertFalse(NetworkCapabilitiesUtils.inferRestrictedCapability(nc))
        // If there is a force restricted capability, then the network capabilities is restricted.
        nc.addCapability(NET_CAPABILITY_OEM_PAID)
        nc.addCapability(NET_CAPABILITY_INTERNET)
        assertTrue(NetworkCapabilitiesUtils.inferRestrictedCapability(nc))
        // Except for the force restricted capability, if there is any unrestricted capability in
        // capabilities, then the network capabilities is not restricted.
        nc.removeCapability(NET_CAPABILITY_OEM_PAID)
        nc.addCapability(NET_CAPABILITY_CBS)
        assertFalse(NetworkCapabilitiesUtils.inferRestrictedCapability(nc))
        // Except for the force restricted capability, the network capabilities will only be treated
        // as restricted when there is no any unrestricted capability.
        nc.removeCapability(NET_CAPABILITY_INTERNET)
        assertTrue(NetworkCapabilitiesUtils.inferRestrictedCapability(nc))
        if (!SdkLevel.isAtLeastS()) return
        // BIP deserves its specific test because it's the first capability over 30, meaning the
        // shift will overflow
        nc.removeCapability(NET_CAPABILITY_CBS)
        nc.addCapability(NET_CAPABILITY_BIP)
        assertTrue(NetworkCapabilitiesUtils.inferRestrictedCapability(nc))
    }

    @Test
    fun testRestrictedUnrestrictedCapabilities() {
        // verify EIMS is restricted
        assertEquals((1 shl NET_CAPABILITY_EIMS).toLong() and RESTRICTED_CAPABILITIES,
                (1 shl NET_CAPABILITY_EIMS).toLong())

        // verify CBS is also restricted
        assertEquals((1 shl NET_CAPABILITY_CBS).toLong() and RESTRICTED_CAPABILITIES,
                (1 shl NET_CAPABILITY_CBS).toLong())

        // verify BIP is also restricted
        // BIP is not available in R and before, but the BIP constant is inlined so
        // this test can still run on R.
        assertEquals((1L shl NET_CAPABILITY_BIP) and RESTRICTED_CAPABILITIES,
                (1L shl NET_CAPABILITY_BIP))

        // verify default is not restricted
        assertEquals((1 shl NET_CAPABILITY_INTERNET).toLong() and RESTRICTED_CAPABILITIES, 0)

        assertTrue(RESTRICTED_CAPABILITIES > 0)

        // just to see
        assertEquals(RESTRICTED_CAPABILITIES and UNRESTRICTED_CAPABILITIES, 0)
    }
}
