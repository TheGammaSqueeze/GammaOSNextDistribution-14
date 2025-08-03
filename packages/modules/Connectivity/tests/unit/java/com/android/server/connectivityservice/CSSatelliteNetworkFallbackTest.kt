/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server

import android.net.IpPrefix
import android.net.INetd
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NativeNetworkConfig
import android.net.NativeNetworkType
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED
import android.net.NetworkScore
import android.net.NetworkCapabilities.TRANSPORT_SATELLITE
import android.net.NetworkScore.KEEP_CONNECTED_FOR_TEST
import android.net.RouteInfo
import android.net.UidRange
import android.net.UidRangeParcel
import android.net.VpnManager
import android.net.netd.aidl.NativeUidRangeConfig
import android.os.Build
import android.os.UserHandle
import android.util.ArraySet
import com.android.net.module.util.CollectionUtils
import com.android.server.ConnectivityService.PREFERENCE_ORDER_SATELLITE_FALLBACK
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.visibleOnHandlerThread
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SECONDARY_USER = 10
private val SECONDARY_USER_HANDLE = UserHandle(SECONDARY_USER)
private const val TEST_PACKAGE_UID = 123
private const val TEST_PACKAGE_UID2 = 321

@DevSdkIgnoreRunner.MonitorThreadLeak
@RunWith(DevSdkIgnoreRunner::class)
@IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
class CSSatelliteNetworkPreferredTest : CSTest() {
    /**
     * Test createMultiLayerNrisFromSatelliteNetworkPreferredUids returns correct
     * NetworkRequestInfo.
     */
    @Test
    fun testCreateMultiLayerNrisFromSatelliteNetworkPreferredUids() {
        // Verify that empty uid set should not create any NRI for it.
        val nrisNoUid = service.createMultiLayerNrisFromSatelliteNetworkFallbackUids(emptySet())
        Assert.assertEquals(0, nrisNoUid.size.toLong())
        val uid1 = PRIMARY_USER_HANDLE.getUid(TEST_PACKAGE_UID)
        val uid2 = PRIMARY_USER_HANDLE.getUid(TEST_PACKAGE_UID2)
        val uid3 = SECONDARY_USER_HANDLE.getUid(TEST_PACKAGE_UID)
        assertCreateMultiLayerNrisFromSatelliteNetworkPreferredUids(mutableSetOf(uid1))
        assertCreateMultiLayerNrisFromSatelliteNetworkPreferredUids(mutableSetOf(uid1, uid3))
        assertCreateMultiLayerNrisFromSatelliteNetworkPreferredUids(mutableSetOf(uid1, uid2))
    }

    /**
     * Test that SATELLITE_NETWORK_PREFERENCE_UIDS changes will send correct net id and uid ranges
     * to netd.
     */
    @Test
    fun testSatelliteNetworkPreferredUidsChanged() {
        val netdInOrder = inOrder(netd)

        val satelliteAgent = createSatelliteAgent("satellite0")
        satelliteAgent.connect()

        val satelliteNetId = satelliteAgent.network.netId
        netdInOrder.verify(netd).networkCreate(
            nativeNetworkConfigPhysical(satelliteNetId, INetd.PERMISSION_NONE))

        val uid1 = PRIMARY_USER_HANDLE.getUid(TEST_PACKAGE_UID)
        val uid2 = PRIMARY_USER_HANDLE.getUid(TEST_PACKAGE_UID2)
        val uid3 = SECONDARY_USER_HANDLE.getUid(TEST_PACKAGE_UID)

        // Initial satellite network preferred uids status.
        setAndUpdateSatelliteNetworkPreferredUids(setOf())
        netdInOrder.verify(netd, never()).networkAddUidRangesParcel(any())
        netdInOrder.verify(netd, never()).networkRemoveUidRangesParcel(any())

        // Set SATELLITE_NETWORK_PREFERENCE_UIDS setting and verify that net id and uid ranges
        // send to netd
        var uids = mutableSetOf(uid1, uid2, uid3)
        val uidRanges1 = toUidRangeStableParcels(uidRangesForUids(uids))
        val config1 = NativeUidRangeConfig(
            satelliteNetId, uidRanges1,
            PREFERENCE_ORDER_SATELLITE_FALLBACK
        )
        setAndUpdateSatelliteNetworkPreferredUids(uids)
        netdInOrder.verify(netd).networkAddUidRangesParcel(config1)
        netdInOrder.verify(netd, never()).networkRemoveUidRangesParcel(any())

        // Set SATELLITE_NETWORK_PREFERENCE_UIDS setting again and verify that old rules are removed
        // and new rules are added.
        uids = mutableSetOf(uid1)
        val uidRanges2: Array<UidRangeParcel?> = toUidRangeStableParcels(uidRangesForUids(uids))
        val config2 = NativeUidRangeConfig(
            satelliteNetId, uidRanges2,
            PREFERENCE_ORDER_SATELLITE_FALLBACK
        )
        setAndUpdateSatelliteNetworkPreferredUids(uids)
        netdInOrder.verify(netd).networkRemoveUidRangesParcel(config1)
        netdInOrder.verify(netd).networkAddUidRangesParcel(config2)
    }

    private fun assertCreateMultiLayerNrisFromSatelliteNetworkPreferredUids(uids: Set<Int>) {
        val nris: Set<ConnectivityService.NetworkRequestInfo> =
            service.createMultiLayerNrisFromSatelliteNetworkFallbackUids(uids)
        val nri = nris.iterator().next()
        // Verify that one NRI is created with multilayer requests. Because one NRI can contain
        // multiple uid ranges, so it only need create one NRI here.
        assertEquals(1, nris.size.toLong())
        assertTrue(nri.isMultilayerRequest)
        assertEquals(nri.uids, uidRangesForUids(uids))
        assertEquals(PREFERENCE_ORDER_SATELLITE_FALLBACK, nri.mPreferenceOrder)
    }

    private fun setAndUpdateSatelliteNetworkPreferredUids(uids: Set<Int>) {
        visibleOnHandlerThread(csHandler) {
            deps.satelliteNetworkFallbackUidUpdate!!.accept(uids)
        }
    }

    private fun nativeNetworkConfigPhysical(netId: Int, permission: Int) =
        NativeNetworkConfig(netId, NativeNetworkType.PHYSICAL, permission,
            false /* secure */, VpnManager.TYPE_VPN_NONE, false /* excludeLocalRoutes */)

    private fun createSatelliteAgent(name: String): CSAgentWrapper {
        return Agent(score = keepScore(), lp = lp(name),
            nc = nc(TRANSPORT_SATELLITE, NET_CAPABILITY_INTERNET)
        )
    }

    private fun toUidRangeStableParcels(ranges: Set<UidRange>): Array<UidRangeParcel?> {
        val stableRanges = arrayOfNulls<UidRangeParcel>(ranges.size)
        for ((index, range) in ranges.withIndex()) {
            stableRanges[index] = UidRangeParcel(range.start, range.stop)
        }
        return stableRanges
    }

    private fun uidRangesForUids(vararg uids: Int): Set<UidRange> {
        val ranges = ArraySet<UidRange>()
        for (uid in uids) {
            ranges.add(UidRange(uid, uid))
        }
        return ranges
    }

    private fun uidRangesForUids(uids: Collection<Int>): Set<UidRange> {
        return uidRangesForUids(*CollectionUtils.toIntArray(uids))
    }

    private fun nc(transport: Int, vararg caps: Int) = NetworkCapabilities.Builder().apply {
        addTransportType(transport)
        caps.forEach {
            addCapability(it)
        }
        // Useful capabilities for everybody
        addCapability(NET_CAPABILITY_NOT_RESTRICTED)
        addCapability(NET_CAPABILITY_NOT_SUSPENDED)
        addCapability(NET_CAPABILITY_NOT_ROAMING)
        addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
    }.build()

    private fun lp(iface: String) = LinkProperties().apply {
        interfaceName = iface
        addLinkAddress(LinkAddress(LOCAL_IPV4_ADDRESS, 32))
        addRoute(RouteInfo(IpPrefix("0.0.0.0/0"), null, null))
    }

    // This allows keeping all the networks connected without having to file individual requests
    // for them.
    private fun keepScore() = FromS(
        NetworkScore.Builder().setKeepConnectedReason(KEEP_CONNECTED_FOR_TEST).build()
    )
}
