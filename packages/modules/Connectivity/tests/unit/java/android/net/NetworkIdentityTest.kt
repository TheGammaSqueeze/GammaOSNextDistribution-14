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

package android.net

import android.content.Context
import android.net.ConnectivityManager.MAX_NETWORK_TYPE
import android.net.ConnectivityManager.TYPE_ETHERNET
import android.net.ConnectivityManager.TYPE_MOBILE
import android.net.ConnectivityManager.TYPE_NONE
import android.net.ConnectivityManager.TYPE_WIFI
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkIdentity.OEM_NONE
import android.net.NetworkIdentity.OEM_PAID
import android.net.NetworkIdentity.OEM_PRIVATE
import android.net.NetworkIdentity.getOemBitfield
import android.app.usage.NetworkStatsManager
import android.telephony.TelephonyManager
import android.os.Build
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val TEST_WIFI_KEY = "testwifikey"
private const val TEST_IMSI1 = "testimsi1"
private const val TEST_IMSI2 = "testimsi2"
private const val TEST_SUBID1 = 1
private const val TEST_SUBID2 = 2

@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
class NetworkIdentityTest {
    private val mockContext = mock(Context::class.java)

    private fun buildMobileNetworkStateSnapshot(
        caps: NetworkCapabilities,
        subscriberId: String
    ): NetworkStateSnapshot {
        return NetworkStateSnapshot(mock(Network::class.java), caps,
                LinkProperties(), subscriberId, TYPE_MOBILE)
    }

    @Test
    fun testGetOemBitfield() {
        val oemNone = NetworkCapabilities().apply {
            setCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID, false)
            setCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE, false)
        }
        val oemPaid = NetworkCapabilities().apply {
            setCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID, true)
            setCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE, false)
        }
        val oemPrivate = NetworkCapabilities().apply {
            setCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID, false)
            setCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE, true)
        }
        val oemAll = NetworkCapabilities().apply {
            setCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID, true)
            setCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE, true)
        }

        assertEquals(getOemBitfield(oemNone), OEM_NONE)
        assertEquals(getOemBitfield(oemPaid), OEM_PAID)
        assertEquals(getOemBitfield(oemPrivate), OEM_PRIVATE)
        assertEquals(getOemBitfield(oemAll), OEM_PAID or OEM_PRIVATE)
    }

    @Test
    fun testIsMetered() {
        // Verify network is metered.
        val netIdent1 = NetworkIdentity.buildNetworkIdentity(mockContext,
                buildMobileNetworkStateSnapshot(NetworkCapabilities(), TEST_IMSI1),
                false /* defaultNetwork */, TelephonyManager.NETWORK_TYPE_UMTS)
        assertTrue(netIdent1.isMetered())

        // Verify network is not metered because it has NET_CAPABILITY_NOT_METERED capability.
        val capsNotMetered = NetworkCapabilities.Builder().apply {
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }.build()
        val netIdent2 = NetworkIdentity.buildNetworkIdentity(mockContext,
                buildMobileNetworkStateSnapshot(capsNotMetered, TEST_IMSI1),
                false /* defaultNetwork */, TelephonyManager.NETWORK_TYPE_UMTS)
        assertFalse(netIdent2.isMetered())

        // In current design, a network that has NET_CAPABILITY_TEMPORARILY_NOT_METERED
        // capability will be treated as metered.
        val capsTempNotMetered = NetworkCapabilities().apply {
            setCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED, true)
        }
        val netIdent3 = NetworkIdentity.buildNetworkIdentity(mockContext,
                buildMobileNetworkStateSnapshot(capsTempNotMetered, TEST_IMSI1),
                false /* defaultNetwork */, TelephonyManager.NETWORK_TYPE_UMTS)
        assertTrue(netIdent3.isMetered())
    }

    @Test
    fun testBuilder() {
        val specifier1 = TelephonyNetworkSpecifier(TEST_SUBID1)
        val oemPrivateRoamingNotMeteredCap = NetworkCapabilities().apply {
            addCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE)
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            addTransportType(TRANSPORT_CELLULAR)
            setNetworkSpecifier(specifier1)
        }
        val identFromSnapshot = NetworkIdentity.Builder().setNetworkStateSnapshot(
                buildMobileNetworkStateSnapshot(oemPrivateRoamingNotMeteredCap, TEST_IMSI1))
                .setDefaultNetwork(true)
                .setRatType(TelephonyManager.NETWORK_TYPE_UMTS)
                .setSubId(TEST_SUBID1)
                .build()
        val identFromLegacyBuild = NetworkIdentity.buildNetworkIdentity(mockContext,
                buildMobileNetworkStateSnapshot(oemPrivateRoamingNotMeteredCap, TEST_IMSI1),
                true /* defaultNetwork */, TelephonyManager.NETWORK_TYPE_UMTS)
        val identFromConstructor = NetworkIdentity(TYPE_MOBILE,
                TelephonyManager.NETWORK_TYPE_UMTS,
                TEST_IMSI1,
                null /* wifiNetworkKey */,
                true /* roaming */,
                false /* metered */,
                true /* defaultNetwork */,
                NetworkTemplate.OEM_MANAGED_PRIVATE,
                TEST_SUBID1)
        assertEquals(identFromLegacyBuild, identFromSnapshot)
        assertEquals(identFromConstructor, identFromSnapshot)

        // Assert non-wifi can't have wifi network key.
        assertFailsWith<IllegalArgumentException> {
            NetworkIdentity.Builder()
                    .setType(TYPE_ETHERNET)
                    .setWifiNetworkKey(TEST_WIFI_KEY)
                    .build()
        }

        // Assert non-mobile can't have ratType.
        assertFailsWith<IllegalArgumentException> {
            NetworkIdentity.Builder()
                    .setType(TYPE_WIFI)
                    .setRatType(TelephonyManager.NETWORK_TYPE_LTE)
                    .build()
        }
    }

    @Test
    fun testBuilder_type() {
        // Assert illegal type values cannot make an identity.
        listOf(Integer.MIN_VALUE, TYPE_NONE - 1, MAX_NETWORK_TYPE + 1, Integer.MAX_VALUE)
                .forEach { type ->
                    assertFailsWith<IllegalArgumentException> {
                        NetworkIdentity.Builder().setType(type).build()
                    }
                }

        // Verify legitimate type values can make an identity.
        for (type in TYPE_NONE..MAX_NETWORK_TYPE) {
            NetworkIdentity.Builder().setType(type).build().also {
                assertEquals(it.type, type)
            }
        }
    }

    @Test
    fun testBuilder_ratType() {
        // Assert illegal ratTypes cannot make an identity.
        listOf(Integer.MIN_VALUE, NetworkTemplate.NETWORK_TYPE_ALL,
                NetworkStatsManager.NETWORK_TYPE_5G_NSA - 1, Integer.MAX_VALUE)
                .forEach {
                    assertFailsWith<IllegalArgumentException> {
                        NetworkIdentity.Builder()
                                .setType(TYPE_MOBILE)
                                .setRatType(it)
                                .build()
                    }
                }

        // Verify legitimate ratTypes can make an identity.
        TelephonyManager.getAllNetworkTypes().toMutableList().also {
            it.add(TelephonyManager.NETWORK_TYPE_UNKNOWN)
            it.add(NetworkStatsManager.NETWORK_TYPE_5G_NSA)
        }.forEach { rat ->
            NetworkIdentity.Builder()
                    .setType(TYPE_MOBILE)
                    .setRatType(rat)
                    .build().also {
                        assertEquals(it.ratType, rat)
                    }
        }
    }

    @Test
    fun testBuilder_oemManaged() {
        // Assert illegal oemManage values cannot make an identity.
        listOf(Integer.MIN_VALUE, NetworkTemplate.OEM_MANAGED_ALL, NetworkTemplate.OEM_MANAGED_YES,
                Integer.MAX_VALUE)
                .forEach { oemManaged ->
                    assertFailsWith<IllegalArgumentException> {
                        NetworkIdentity.Builder()
                                .setType(TYPE_MOBILE)
                                .setOemManaged(oemManaged)
                                .build()
                    }
                }

        // Verify legitimate oem managed values can make an identity.
        listOf(NetworkTemplate.OEM_MANAGED_NO, NetworkTemplate.OEM_MANAGED_PAID,
                NetworkTemplate.OEM_MANAGED_PRIVATE, NetworkTemplate.OEM_MANAGED_PAID or
                NetworkTemplate.OEM_MANAGED_PRIVATE)
                .forEach { oemManaged ->
                    NetworkIdentity.Builder()
                            .setOemManaged(oemManaged)
                            .build().also {
                                assertEquals(it.oemManaged, oemManaged)
                            }
                }
    }

    @Test
    fun testGetSubId() {
        val specifier1 = TelephonyNetworkSpecifier(TEST_SUBID1)
        val specifier2 = TelephonyNetworkSpecifier(TEST_SUBID2)
        val capSUBID1 = NetworkCapabilities().apply {
            addTransportType(TRANSPORT_CELLULAR)
            setNetworkSpecifier(specifier1)
        }
        val capSUBID2 = NetworkCapabilities().apply {
            addTransportType(TRANSPORT_CELLULAR)
            setNetworkSpecifier(specifier2)
        }

        val netIdent1 = NetworkIdentity.buildNetworkIdentity(mockContext,
                buildMobileNetworkStateSnapshot(capSUBID1, TEST_IMSI1),
                false /* defaultNetwork */, TelephonyManager.NETWORK_TYPE_UMTS)
        assertEquals(TEST_SUBID1, netIdent1.getSubId())

        val netIdent2 = NetworkIdentity.buildNetworkIdentity(mockContext,
                buildMobileNetworkStateSnapshot(capSUBID2, TEST_IMSI2),
                false /* defaultNetwork */, TelephonyManager.NETWORK_TYPE_UMTS)
        assertEquals(TEST_SUBID2, netIdent2.getSubId())
    }
}
