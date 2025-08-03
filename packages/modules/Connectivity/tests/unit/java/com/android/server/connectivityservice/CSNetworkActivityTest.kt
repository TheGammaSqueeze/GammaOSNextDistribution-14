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

import android.net.ConnectivityManager
import android.net.ConnectivityManager.ACTION_DATA_ACTIVITY_CHANGE
import android.net.ConnectivityManager.EXTRA_DEVICE_TYPE
import android.net.ConnectivityManager.EXTRA_IS_ACTIVE
import android.net.ConnectivityManager.EXTRA_REALTIME_NS
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_IMS
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest
import android.os.Build
import android.os.ConditionVariable
import android.telephony.DataConnectionRealTimeInfo.DC_POWER_STATE_HIGH
import android.telephony.DataConnectionRealTimeInfo.DC_POWER_STATE_LOW
import androidx.test.filters.SmallTest
import com.android.net.module.util.BaseNetdUnsolicitedEventListener
import com.android.server.CSTest.CSContext
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.RecorderCallback.CallbackEntry.Lost
import com.android.testutils.TestableNetworkCallback
import kotlin.test.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify

private const val DATA_CELL_IFNAME = "rmnet_data"
private const val IMS_CELL_IFNAME = "rmnet_ims"
private const val WIFI_IFNAME = "wlan0"
private const val TIMESTAMP = 1234L
private const val NETWORK_ACTIVITY_NO_UID = -1
private const val PACKAGE_UID = 123
private const val TIMEOUT_MS = 250L

@DevSdkIgnoreRunner.MonitorThreadLeak
@RunWith(DevSdkIgnoreRunner::class)
@SmallTest
@IgnoreUpTo(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class CSNetworkActivityTest : CSTest() {

    private fun getRegisteredNetdUnsolicitedEventListener(): BaseNetdUnsolicitedEventListener {
        val captor = ArgumentCaptor.forClass(BaseNetdUnsolicitedEventListener::class.java)
        verify(netd).registerUnsolicitedEventListener(captor.capture())
        return captor.value
    }

    @Test
    fun testInterfaceClassActivityChanged_NonDefaultNetwork() {
        val netdUnsolicitedEventListener = getRegisteredNetdUnsolicitedEventListener()
        val batteryStatsInorder = inOrder(batteryStats)

        val cellNr = NetworkRequest.Builder()
                .clearCapabilities()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .build()
        val cellCb = TestableNetworkCallback()
        // Request cell network to keep cell network up
        cm.requestNetwork(cellNr, cellCb)

        val defaultCb = TestableNetworkCallback()
        cm.registerDefaultNetworkCallback(defaultCb)

        val cellNc = NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_SUSPENDED)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .build()
        val cellLp = LinkProperties().apply {
            interfaceName = DATA_CELL_IFNAME
        }
        // Connect Cellular network
        val cellAgent = Agent(nc = cellNc, lp = cellLp)
        cellAgent.connect()
        defaultCb.expectAvailableCallbacks(cellAgent.network, validated = false)

        val wifiNc = NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .build()
        val wifiLp = LinkProperties().apply {
            interfaceName = WIFI_IFNAME
        }
        // Connect Wi-Fi network, Wi-Fi network should be the default network.
        val wifiAgent = Agent(nc = wifiNc, lp = wifiLp)
        wifiAgent.connect()
        defaultCb.expectAvailableCallbacks(wifiAgent.network, validated = false)
        batteryStatsInorder.verify(batteryStats).noteWifiRadioPowerState(eq(DC_POWER_STATE_HIGH),
                anyLong() /* timestampNs */, eq(NETWORK_ACTIVITY_NO_UID))

        val onNetworkActiveCv = ConditionVariable()
        val listener = ConnectivityManager.OnNetworkActiveListener { onNetworkActiveCv::open }
        cm.addDefaultNetworkActiveListener(listener)

        // Cellular network (non default network) goes to inactive state.
        netdUnsolicitedEventListener.onInterfaceClassActivityChanged(false /* isActive */,
                cellAgent.network.netId, TIMESTAMP, NETWORK_ACTIVITY_NO_UID)
        // Non-default network activity change does not change default network activity
        // But cellular radio power state is updated
        assertFalse(onNetworkActiveCv.block(TIMEOUT_MS))
        context.expectNoDataActivityBroadcast(0 /* timeoutMs */)
        assertTrue(cm.isDefaultNetworkActive)
        batteryStatsInorder.verify(batteryStats).noteMobileRadioPowerState(eq(DC_POWER_STATE_LOW),
                anyLong() /* timestampNs */, eq(NETWORK_ACTIVITY_NO_UID))

        // Cellular network (non default network) goes to active state.
        netdUnsolicitedEventListener.onInterfaceClassActivityChanged(true /* isActive */,
                cellAgent.network.netId, TIMESTAMP, PACKAGE_UID)
        // Non-default network activity change does not change default network activity
        // But cellular radio power state is updated
        assertFalse(onNetworkActiveCv.block(TIMEOUT_MS))
        context.expectNoDataActivityBroadcast(0 /* timeoutMs */)
        assertTrue(cm.isDefaultNetworkActive)
        batteryStatsInorder.verify(batteryStats).noteMobileRadioPowerState(eq(DC_POWER_STATE_HIGH),
                anyLong() /* timestampNs */, eq(PACKAGE_UID))

        cm.unregisterNetworkCallback(cellCb)
        cm.unregisterNetworkCallback(defaultCb)
        cm.removeDefaultNetworkActiveListener(listener)
    }

    @Test
    fun testDataActivityTracking_MultiCellNetwork() {
        val netdUnsolicitedEventListener = getRegisteredNetdUnsolicitedEventListener()
        val batteryStatsInorder = inOrder(batteryStats)

        val dataNetworkNc = NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_SUSPENDED)
                .build()
        val dataNetworkNr = NetworkRequest.Builder()
                .clearCapabilities()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .build()
        val dataNetworkLp = LinkProperties().apply {
            interfaceName = DATA_CELL_IFNAME
        }
        val dataNetworkCb = TestableNetworkCallback()
        cm.requestNetwork(dataNetworkNr, dataNetworkCb)
        val dataNetworkAgent = Agent(nc = dataNetworkNc, lp = dataNetworkLp)
        val dataNetworkNetId = dataNetworkAgent.network.netId.toString()

        val imsNetworkNc = NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_IMS)
                .addCapability(NET_CAPABILITY_NOT_SUSPENDED)
                .build()
        val imsNetworkNr = NetworkRequest.Builder()
                .clearCapabilities()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_IMS)
                .build()
        val imsNetworkLp = LinkProperties().apply {
            interfaceName = IMS_CELL_IFNAME
        }
        val imsNetworkCb = TestableNetworkCallback()
        cm.requestNetwork(imsNetworkNr, imsNetworkCb)
        val imsNetworkAgent = Agent(nc = imsNetworkNc, lp = imsNetworkLp)
        val imsNetworkNetId = imsNetworkAgent.network.netId.toString()

        dataNetworkAgent.connect()
        dataNetworkCb.expectAvailableCallbacks(dataNetworkAgent.network, validated = false)

        imsNetworkAgent.connect()
        imsNetworkCb.expectAvailableCallbacks(imsNetworkAgent.network, validated = false)

        // Both cell networks have idleTimers
        verify(netd).idletimerAddInterface(eq(DATA_CELL_IFNAME), anyInt(), eq(dataNetworkNetId))
        verify(netd).idletimerAddInterface(eq(IMS_CELL_IFNAME), anyInt(), eq(imsNetworkNetId))
        verify(netd, never()).idletimerRemoveInterface(eq(DATA_CELL_IFNAME), anyInt(),
                eq(dataNetworkNetId))
        verify(netd, never()).idletimerRemoveInterface(eq(IMS_CELL_IFNAME), anyInt(),
                eq(imsNetworkNetId))

        // Both cell networks go to inactive state
        netdUnsolicitedEventListener.onInterfaceClassActivityChanged(false /* isActive */,
                imsNetworkAgent.network.netId, TIMESTAMP, NETWORK_ACTIVITY_NO_UID)
        netdUnsolicitedEventListener.onInterfaceClassActivityChanged(false /* isActive */,
                dataNetworkAgent.network.netId, TIMESTAMP, NETWORK_ACTIVITY_NO_UID)

        // Data cell network goes to active state. This should update the cellular radio power state
        netdUnsolicitedEventListener.onInterfaceClassActivityChanged(true /* isActive */,
                dataNetworkAgent.network.netId, TIMESTAMP, PACKAGE_UID)
        batteryStatsInorder.verify(batteryStats, timeout(TIMEOUT_MS)).noteMobileRadioPowerState(
                eq(DC_POWER_STATE_HIGH), anyLong() /* timestampNs */, eq(PACKAGE_UID))
        // Ims cell network goes to active state. But this should not update the cellular radio
        // power state since cellular radio power state is already high
        netdUnsolicitedEventListener.onInterfaceClassActivityChanged(true /* isActive */,
                imsNetworkAgent.network.netId, TIMESTAMP, PACKAGE_UID)
        waitForIdle()
        batteryStatsInorder.verify(batteryStats, never()).noteMobileRadioPowerState(anyInt(),
                anyLong() /* timestampNs */, anyInt())

        // Data cell network goes to inactive state. But this should not update the cellular radio
        // power state ims cell network is still active state
        netdUnsolicitedEventListener.onInterfaceClassActivityChanged(false /* isActive */,
                dataNetworkAgent.network.netId, TIMESTAMP, NETWORK_ACTIVITY_NO_UID)
        waitForIdle()
        batteryStatsInorder.verify(batteryStats, never()).noteMobileRadioPowerState(anyInt(),
                anyLong() /* timestampNs */, anyInt())

        // Ims cell network goes to inactive state.
        // This should update the cellular radio power state
        netdUnsolicitedEventListener.onInterfaceClassActivityChanged(false /* isActive */,
                imsNetworkAgent.network.netId, TIMESTAMP, NETWORK_ACTIVITY_NO_UID)
        batteryStatsInorder.verify(batteryStats, timeout(TIMEOUT_MS)).noteMobileRadioPowerState(
                eq(DC_POWER_STATE_LOW), anyLong() /* timestampNs */, eq(NETWORK_ACTIVITY_NO_UID))

        dataNetworkAgent.disconnect()
        dataNetworkCb.expect<Lost>(dataNetworkAgent.network)
        verify(netd).idletimerRemoveInterface(eq(DATA_CELL_IFNAME), anyInt(), eq(dataNetworkNetId))

        imsNetworkAgent.disconnect()
        imsNetworkCb.expect<Lost>(imsNetworkAgent.network)
        verify(netd).idletimerRemoveInterface(eq(IMS_CELL_IFNAME), anyInt(), eq(imsNetworkNetId))

        cm.unregisterNetworkCallback(dataNetworkCb)
        cm.unregisterNetworkCallback(imsNetworkCb)
    }
}

internal fun CSContext.expectDataActivityBroadcast(
        deviceType: Int,
        isActive: Boolean,
        tsNanos: Long
) {
    assertNotNull(orderedBroadcastAsUserHistory.poll(BROADCAST_TIMEOUT_MS) {
        intent -> intent.action.equals(ACTION_DATA_ACTIVITY_CHANGE) &&
            intent.getIntExtra(EXTRA_DEVICE_TYPE, -1) == deviceType &&
            intent.getBooleanExtra(EXTRA_IS_ACTIVE, !isActive) == isActive &&
            intent.getLongExtra(EXTRA_REALTIME_NS, -1) == tsNanos
    })
}
