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

package android.net

import android.content.Context
import android.net.ConnectivityManager.TYPE_MOBILE
import android.os.Build
import android.telephony.TelephonyManager
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import kotlin.test.assertEquals

private const val TEST_IMSI1 = "testimsi1"

@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
@RunWith(DevSdkIgnoreRunner::class)
class NetworkIdentitySetTest {
    private val mockContext = mock(Context::class.java)

    private fun buildMobileNetworkStateSnapshot(
        caps: NetworkCapabilities,
        subscriberId: String
    ): NetworkStateSnapshot {
        return NetworkStateSnapshot(mock(Network::class.java), caps,
                LinkProperties(), subscriberId, TYPE_MOBILE)
    }

    @Test
    fun testCompare() {
        val ident1 = NetworkIdentity.buildNetworkIdentity(mockContext,
            buildMobileNetworkStateSnapshot(NetworkCapabilities(), TEST_IMSI1),
            false /* defaultNetwork */, TelephonyManager.NETWORK_TYPE_UMTS)
        val ident2 = NetworkIdentity.buildNetworkIdentity(mockContext,
            buildMobileNetworkStateSnapshot(NetworkCapabilities(), TEST_IMSI1),
            true /* defaultNetwork */, TelephonyManager.NETWORK_TYPE_UMTS)

        // Verify that the results of comparing two empty sets are equal
        assertEquals(0, NetworkIdentitySet.compare(NetworkIdentitySet(), NetworkIdentitySet()))

        val identSet1 = NetworkIdentitySet()
        val identSet2 = NetworkIdentitySet()
        identSet1.add(ident1)
        identSet2.add(ident2)
        assertEquals(-1, NetworkIdentitySet.compare(NetworkIdentitySet(), identSet1))
        assertEquals(1, NetworkIdentitySet.compare(identSet1, NetworkIdentitySet()))
        assertEquals(0, NetworkIdentitySet.compare(identSet1, identSet1))
        assertEquals(-1, NetworkIdentitySet.compare(identSet1, identSet2))
    }
}
