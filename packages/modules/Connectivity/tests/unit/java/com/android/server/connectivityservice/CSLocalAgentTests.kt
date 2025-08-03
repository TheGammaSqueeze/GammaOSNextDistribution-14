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
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.LocalNetworkConfig
import android.net.MulticastRoutingConfig
import android.net.MulticastRoutingConfig.CONFIG_FORWARD_NONE
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_DUN
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_THREAD
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest
import android.net.NetworkScore
import android.net.NetworkScore.KEEP_CONNECTED_FOR_TEST
import android.net.NetworkScore.KEEP_CONNECTED_LOCAL_NETWORK
import android.net.RouteInfo
import android.os.Build
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.RecorderCallback.CallbackEntry.LocalInfoChanged
import com.android.testutils.RecorderCallback.CallbackEntry.Lost
import com.android.testutils.TestableNetworkCallback
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.eq
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify

private const val TIMEOUT_MS = 200L
private const val MEDIUM_TIMEOUT_MS = 1_000L
private const val LONG_TIMEOUT_MS = 5_000

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

@DevSdkIgnoreRunner.MonitorThreadLeak
@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
class CSLocalAgentTests : CSTest() {
    val multicastRoutingConfigMinScope =
                MulticastRoutingConfig.Builder(MulticastRoutingConfig.FORWARD_WITH_MIN_SCOPE, 4)
                .build();
    val multicastRoutingConfigSelected =
                MulticastRoutingConfig.Builder(MulticastRoutingConfig.FORWARD_SELECTED)
                .build();
    val upstreamSelectorAny = NetworkRequest.Builder()
                .addForbiddenCapability(NET_CAPABILITY_LOCAL_NETWORK)
                .build()
    val upstreamSelectorWifi = NetworkRequest.Builder()
                .addForbiddenCapability(NET_CAPABILITY_LOCAL_NETWORK)
                .addTransportType(TRANSPORT_WIFI)
                .build()
    val upstreamSelectorCell = NetworkRequest.Builder()
                .addForbiddenCapability(NET_CAPABILITY_LOCAL_NETWORK)
                .addTransportType(TRANSPORT_CELLULAR)
                .build()

    @Test
    fun testBadAgents() {
        deps.setBuildSdk(VERSION_V)

        assertFailsWith<IllegalArgumentException> {
            Agent(nc = NetworkCapabilities.Builder()
                    .addCapability(NET_CAPABILITY_LOCAL_NETWORK)
                    .build(),
                    lnc = null)
        }
        assertFailsWith<IllegalArgumentException> {
            Agent(nc = NetworkCapabilities.Builder().build(),
                    lnc = FromS(LocalNetworkConfig.Builder().build()))
        }
    }

    @Test
    fun testStructuralConstraintViolation() {
        deps.setBuildSdk(VERSION_V)

        val cb = TestableNetworkCallback()
        cm.requestNetwork(NetworkRequest.Builder()
                .clearCapabilities()
                .build(),
                cb)
        val agent = Agent(nc = NetworkCapabilities.Builder()
                .addCapability(NET_CAPABILITY_LOCAL_NETWORK)
                .build(),
                lnc = FromS(LocalNetworkConfig.Builder().build()))
        agent.connect()
        cb.expectAvailableCallbacks(agent.network, validated = false)
        agent.sendNetworkCapabilities(NetworkCapabilities.Builder().build())
        cb.expect<Lost>(agent.network)

        val agent2 = Agent(nc = NetworkCapabilities.Builder()
                .build(),
                lnc = null)
        agent2.connect()
        cb.expectAvailableCallbacks(agent2.network, validated = false)
        agent2.sendNetworkCapabilities(NetworkCapabilities.Builder()
                .addCapability(NET_CAPABILITY_LOCAL_NETWORK)
                .build())
        cb.expect<Lost>(agent2.network)
    }

    @Test
    fun testUpdateLocalAgentConfig() {
        deps.setBuildSdk(VERSION_V)

        val cb = TestableNetworkCallback()
        cm.requestNetwork(NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_LOCAL_NETWORK)
                .build(),
                cb)

        // Set up a local agent that should forward its traffic to the best DUN upstream.
        val localAgent = Agent(
                nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_LOCAL_NETWORK),
                lp = lp("local0"),
                lnc = FromS(LocalNetworkConfig.Builder().build()),
        )
        localAgent.connect()

        cb.expectAvailableCallbacks(localAgent.network, validated = false)

        val wifiAgent = Agent(score = keepScore(), lp = lp("wifi0"),
                nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_INTERNET))
        wifiAgent.connect()

        val newLnc = LocalNetworkConfig.Builder()
                .setUpstreamSelector(NetworkRequest.Builder()
                        .addTransportType(TRANSPORT_WIFI)
                        .addForbiddenCapability(NET_CAPABILITY_LOCAL_NETWORK)
                        .build())
                .build()
        localAgent.sendLocalNetworkConfig(newLnc)

        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == wifiAgent.network
        }

        localAgent.sendLocalNetworkConfig(LocalNetworkConfig.Builder().build())
        cb.expect<LocalInfoChanged>(localAgent.network) { it.info.upstreamNetwork == null }

        localAgent.sendLocalNetworkConfig(newLnc)
        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == wifiAgent.network
        }

        wifiAgent.disconnect()
        cb.expect<LocalInfoChanged>(localAgent.network) { it.info.upstreamNetwork == null }

        localAgent.disconnect()
    }

    private fun createLocalAgent(name: String, localNetworkConfig: FromS<LocalNetworkConfig>):
                CSAgentWrapper {
        val localAgent = Agent(
                nc = nc(TRANSPORT_THREAD, NET_CAPABILITY_LOCAL_NETWORK),
                lp = lp(name),
                lnc = localNetworkConfig,
        )
        return localAgent
    }

    private fun createWifiAgent(name: String): CSAgentWrapper {
        return Agent(score = keepScore(), lp = lp(name),
                nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_INTERNET))
    }

    private fun createCellAgent(name: String): CSAgentWrapper {
        return Agent(score = keepScore(), lp = lp(name),
                nc = nc(TRANSPORT_CELLULAR, NET_CAPABILITY_INTERNET))
    }

    private fun sendLocalNetworkConfig(localAgent: CSAgentWrapper,
                upstreamSelector: NetworkRequest?, upstreamConfig: MulticastRoutingConfig,
                downstreamConfig: MulticastRoutingConfig) {
        val newLnc = LocalNetworkConfig.Builder()
                .setUpstreamSelector(upstreamSelector)
                .setUpstreamMulticastRoutingConfig(upstreamConfig)
                .setDownstreamMulticastRoutingConfig(downstreamConfig)
                .build()
        localAgent.sendLocalNetworkConfig(newLnc)
    }

    @Test
    fun testMulticastRoutingConfig() {
        deps.setBuildSdk(VERSION_V)
        val cb = TestableNetworkCallback()
        cm.registerNetworkCallback(NetworkRequest.Builder().clearCapabilities().build(), cb)
        val inOrder = inOrder(multicastRoutingCoordinatorService)

        val lnc = FromS(LocalNetworkConfig.Builder()
                .setUpstreamSelector(upstreamSelectorWifi)
                .setUpstreamMulticastRoutingConfig(multicastRoutingConfigMinScope)
                .setDownstreamMulticastRoutingConfig(multicastRoutingConfigSelected)
                .build()
        )
        val localAgent = createLocalAgent("local0", lnc)
        localAgent.connect()

        cb.expectAvailableCallbacks(localAgent.network, validated = false)

        val wifiAgent = createWifiAgent("wifi0")
        wifiAgent.connect()
        cb.expectAvailableCallbacks(wifiAgent.network, validated = false)
        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == wifiAgent.network
        }

        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "local0", "wifi0", multicastRoutingConfigMinScope)
        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "wifi0", "local0", multicastRoutingConfigSelected)

        wifiAgent.disconnect()

        inOrder.verify(multicastRoutingCoordinatorService)
                .applyMulticastRoutingConfig("local0", "wifi0", CONFIG_FORWARD_NONE)
        inOrder.verify(multicastRoutingCoordinatorService)
                .applyMulticastRoutingConfig("wifi0", "local0", CONFIG_FORWARD_NONE)

        localAgent.disconnect()
    }

    @Test
    fun testMulticastRoutingConfig_2LocalNetworks() {
        deps.setBuildSdk(VERSION_V)
        val inOrder = inOrder(multicastRoutingCoordinatorService)
        val lnc = FromS(LocalNetworkConfig.Builder()
                .setUpstreamSelector(upstreamSelectorWifi)
                .setUpstreamMulticastRoutingConfig(multicastRoutingConfigMinScope)
                .setDownstreamMulticastRoutingConfig(multicastRoutingConfigSelected)
                .build()
        )
        val localAgent0 = createLocalAgent("local0", lnc)
        localAgent0.connect()

        val wifiAgent = createWifiAgent("wifi0")
        wifiAgent.connect()
        waitForIdle()

        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "local0", "wifi0", multicastRoutingConfigMinScope)
        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "wifi0", "local0", multicastRoutingConfigSelected)

        val localAgent1 = createLocalAgent("local1", lnc)
        localAgent1.connect()
        waitForIdle()

        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "local1", "wifi0", multicastRoutingConfigMinScope)
        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "wifi0", "local1", multicastRoutingConfigSelected)

        localAgent0.disconnect()
        localAgent1.disconnect()
        wifiAgent.disconnect()
    }

    @Test
    fun testMulticastRoutingConfig_UpstreamNetworkCellToWifi() {
        deps.setBuildSdk(VERSION_V)
        val cb = TestableNetworkCallback()
        cm.registerNetworkCallback(NetworkRequest.Builder().clearCapabilities()
                        .addCapability(NET_CAPABILITY_LOCAL_NETWORK)
                        .build(), cb)
        val inOrder = inOrder(multicastRoutingCoordinatorService)
        val lnc = FromS(LocalNetworkConfig.Builder()
                .setUpstreamSelector(upstreamSelectorAny)
                .setUpstreamMulticastRoutingConfig(multicastRoutingConfigMinScope)
                .setDownstreamMulticastRoutingConfig(multicastRoutingConfigSelected)
                .build()
        )
        val localAgent = createLocalAgent("local0", lnc)
        val wifiAgent = createWifiAgent("wifi0")
        val cellAgent = createCellAgent("cell0")

        localAgent.connect()
        cb.expectAvailableCallbacks(localAgent.network, validated = false)

        cellAgent.connect()
        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == cellAgent.network
        }

        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "local0", "cell0", multicastRoutingConfigMinScope)
        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "cell0", "local0", multicastRoutingConfigSelected)

        wifiAgent.connect()

        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == wifiAgent.network
        }

        // upstream should have been switched to wifi
        inOrder.verify(multicastRoutingCoordinatorService)
                .applyMulticastRoutingConfig("local0", "cell0", CONFIG_FORWARD_NONE)
        inOrder.verify(multicastRoutingCoordinatorService)
                .applyMulticastRoutingConfig("cell0", "local0", CONFIG_FORWARD_NONE)
        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "local0", "wifi0", multicastRoutingConfigMinScope)
        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "wifi0", "local0", multicastRoutingConfigSelected)

        localAgent.disconnect()
        cellAgent.disconnect()
        wifiAgent.disconnect()
    }

    @Test
    fun testMulticastRoutingConfig_UpstreamSelectorCellToWifi() {
        deps.setBuildSdk(VERSION_V)
        val cb = TestableNetworkCallback()
        cm.registerNetworkCallback(NetworkRequest.Builder().clearCapabilities()
                        .addCapability(NET_CAPABILITY_LOCAL_NETWORK)
                        .build(), cb)
        val inOrder = inOrder(multicastRoutingCoordinatorService)
        val lnc = FromS(LocalNetworkConfig.Builder()
                .setUpstreamSelector(upstreamSelectorCell)
                .setUpstreamMulticastRoutingConfig(multicastRoutingConfigMinScope)
                .setDownstreamMulticastRoutingConfig(multicastRoutingConfigSelected)
                .build()
        )
        val localAgent = createLocalAgent("local0", lnc)
        val wifiAgent = createWifiAgent("wifi0")
        val cellAgent = createCellAgent("cell0")

        localAgent.connect()
        cellAgent.connect()
        wifiAgent.connect()
        cb.expectAvailableCallbacks(localAgent.network, validated = false)
        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == cellAgent.network
        }

        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "local0", "cell0", multicastRoutingConfigMinScope)
        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "cell0", "local0", multicastRoutingConfigSelected)

        sendLocalNetworkConfig(localAgent, upstreamSelectorWifi, multicastRoutingConfigMinScope,
                multicastRoutingConfigSelected)
        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == wifiAgent.network
        }

        // upstream should have been switched to wifi
        inOrder.verify(multicastRoutingCoordinatorService)
                .applyMulticastRoutingConfig("local0", "cell0", CONFIG_FORWARD_NONE)
        inOrder.verify(multicastRoutingCoordinatorService)
                .applyMulticastRoutingConfig("cell0", "local0", CONFIG_FORWARD_NONE)
        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "local0", "wifi0", multicastRoutingConfigMinScope)
        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "wifi0", "local0", multicastRoutingConfigSelected)

        localAgent.disconnect()
        cellAgent.disconnect()
        wifiAgent.disconnect()
    }

    @Test
    fun testMulticastRoutingConfig_UpstreamSelectorWifiToNull() {
        deps.setBuildSdk(VERSION_V)
        val cb = TestableNetworkCallback()
        cm.registerNetworkCallback(NetworkRequest.Builder().clearCapabilities()
                        .addCapability(NET_CAPABILITY_LOCAL_NETWORK)
                        .build(), cb)
        val inOrder = inOrder(multicastRoutingCoordinatorService)
        val lnc = FromS(LocalNetworkConfig.Builder()
                .setUpstreamSelector(upstreamSelectorWifi)
                .setUpstreamMulticastRoutingConfig(multicastRoutingConfigMinScope)
                .setDownstreamMulticastRoutingConfig(multicastRoutingConfigSelected)
                .build()
        )
        val localAgent = createLocalAgent("local0", lnc)
        localAgent.connect()
        val wifiAgent = createWifiAgent("wifi0")
        wifiAgent.connect()
        cb.expectAvailableCallbacks(localAgent.network, validated = false)
        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == wifiAgent.network
        }

        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "local0", "wifi0", multicastRoutingConfigMinScope)
        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "wifi0", "local0", multicastRoutingConfigSelected)

        sendLocalNetworkConfig(localAgent, null, multicastRoutingConfigMinScope,
                multicastRoutingConfigSelected)
        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == null
        }

        // upstream should have been switched to null
        inOrder.verify(multicastRoutingCoordinatorService)
                .applyMulticastRoutingConfig("local0", "wifi0", CONFIG_FORWARD_NONE)
        inOrder.verify(multicastRoutingCoordinatorService)
                .applyMulticastRoutingConfig("wifi0", "local0", CONFIG_FORWARD_NONE)
        inOrder.verify(multicastRoutingCoordinatorService, never()).applyMulticastRoutingConfig(
                eq("local0"), any(), eq(multicastRoutingConfigMinScope))
        inOrder.verify(multicastRoutingCoordinatorService, never()).applyMulticastRoutingConfig(
                any(), eq("local0"), eq(multicastRoutingConfigSelected))

        localAgent.disconnect()
        wifiAgent.disconnect()
    }


    @Test
    fun testUnregisterUpstreamAfterReplacement_SameIfaceName() {
        doTestUnregisterUpstreamAfterReplacement(true)
    }

    @Test
    fun testUnregisterUpstreamAfterReplacement_DifferentIfaceName() {
        doTestUnregisterUpstreamAfterReplacement(false)
    }

    fun doTestUnregisterUpstreamAfterReplacement(sameIfaceName: Boolean) {
        deps.setBuildSdk(VERSION_V)
        val cb = TestableNetworkCallback()
        cm.registerNetworkCallback(NetworkRequest.Builder().clearCapabilities().build(), cb)

        // Set up a local agent that should forward its traffic to the best wifi upstream.
        val localAgent = Agent(nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_LOCAL_NETWORK),
                lp = lp("local0"),
                lnc = FromS(LocalNetworkConfig.Builder()
                        .setUpstreamSelector(upstreamSelectorWifi)
                        .setUpstreamMulticastRoutingConfig(multicastRoutingConfigMinScope)
                        .setDownstreamMulticastRoutingConfig(multicastRoutingConfigSelected)
                        .build()),
                score = FromS(NetworkScore.Builder()
                        .setKeepConnectedReason(KEEP_CONNECTED_LOCAL_NETWORK)
                        .build())
        )
        localAgent.connect()

        cb.expectAvailableCallbacks(localAgent.network, validated = false)

        val wifiAgent = Agent(lp = lp("wifi0"),
                nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_INTERNET))
        wifiAgent.connect()

        cb.expectAvailableCallbacks(wifiAgent.network, validated = false)
        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == wifiAgent.network
        }

        clearInvocations(netd)
        clearInvocations(multicastRoutingCoordinatorService)
        val inOrder = inOrder(netd, multicastRoutingCoordinatorService)
        wifiAgent.unregisterAfterReplacement(LONG_TIMEOUT_MS)
        waitForIdle()
        inOrder.verify(netd).ipfwdRemoveInterfaceForward("local0", "wifi0")
        inOrder.verify(multicastRoutingCoordinatorService)
                .applyMulticastRoutingConfig("local0", "wifi0", CONFIG_FORWARD_NONE)
        inOrder.verify(multicastRoutingCoordinatorService)
                .applyMulticastRoutingConfig("wifi0", "local0", CONFIG_FORWARD_NONE)
        inOrder.verify(netd).networkDestroy(wifiAgent.network.netId)

        val wifiIface2 = if (sameIfaceName) "wifi0" else "wifi1"
        val wifiAgent2 = Agent(lp = lp(wifiIface2),
                nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_INTERNET))
        wifiAgent2.connect()

        cb.expectAvailableCallbacks(wifiAgent2.network, validated = false)
        cb.expect<LocalInfoChanged> { it.info.upstreamNetwork == wifiAgent2.network }
        cb.expect<Lost> { it.network == wifiAgent.network }

        inOrder.verify(netd).ipfwdAddInterfaceForward("local0", wifiIface2)
        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                "local0", wifiIface2, multicastRoutingConfigMinScope)
        inOrder.verify(multicastRoutingCoordinatorService).applyMulticastRoutingConfig(
                wifiIface2, "local0", multicastRoutingConfigSelected)

        inOrder.verify(netd, never()).ipfwdRemoveInterfaceForward(any(), any())
        inOrder.verify(multicastRoutingCoordinatorService, never())
                .applyMulticastRoutingConfig("local0", "wifi0", CONFIG_FORWARD_NONE)
        inOrder.verify(multicastRoutingCoordinatorService, never())
                .applyMulticastRoutingConfig("wifi0", "local0", CONFIG_FORWARD_NONE)
    }

    @Test
    fun testUnregisterUpstreamAfterReplacement_neverReplaced() {
        deps.setBuildSdk(VERSION_V)
        val cb = TestableNetworkCallback()
        cm.registerNetworkCallback(NetworkRequest.Builder().clearCapabilities().build(), cb)

        // Set up a local agent that should forward its traffic to the best wifi upstream.
        val localAgent = Agent(nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_LOCAL_NETWORK),
                lp = lp("local0"),
                lnc = FromS(LocalNetworkConfig.Builder()
                        .setUpstreamSelector(NetworkRequest.Builder()
                                .addTransportType(TRANSPORT_WIFI)
                                .addForbiddenCapability(NET_CAPABILITY_LOCAL_NETWORK)
                                .build())
                        .build()),
                score = FromS(NetworkScore.Builder()
                        .setKeepConnectedReason(KEEP_CONNECTED_LOCAL_NETWORK)
                        .build())
        )
        localAgent.connect()

        cb.expectAvailableCallbacks(localAgent.network, validated = false)

        val wifiAgent = Agent(lp = lp("wifi0"),
                nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_INTERNET))
        wifiAgent.connect()

        cb.expectAvailableCallbacks(wifiAgent.network, validated = false)
        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == wifiAgent.network
        }

        clearInvocations(netd)
        wifiAgent.unregisterAfterReplacement(TIMEOUT_MS.toInt())
        waitForIdle()
        verify(netd).networkDestroy(wifiAgent.network.netId)
        verify(netd).ipfwdRemoveInterfaceForward("local0", "wifi0")

        cb.expect<LocalInfoChanged>(localAgent.network) { it.info.upstreamNetwork == null }
        cb.expect<Lost> { it.network == wifiAgent.network }
    }

    @Test
    fun testUnregisterLocalAgentAfterReplacement() {
        deps.setBuildSdk(VERSION_V)

        val localCb = TestableNetworkCallback()
        cm.requestNetwork(NetworkRequest.Builder().clearCapabilities()
                .addCapability(NET_CAPABILITY_LOCAL_NETWORK)
                .build(),
                localCb)

        val cb = TestableNetworkCallback()
        cm.registerNetworkCallback(NetworkRequest.Builder().clearCapabilities().build(), cb)

        val localNc = nc(TRANSPORT_WIFI, NET_CAPABILITY_LOCAL_NETWORK)
        val lnc = FromS(LocalNetworkConfig.Builder()
                .setUpstreamSelector(NetworkRequest.Builder()
                        .addTransportType(TRANSPORT_WIFI)
                        .addForbiddenCapability(NET_CAPABILITY_LOCAL_NETWORK)
                        .build())
                .build())
        val localScore = FromS(NetworkScore.Builder().build())

        // Set up a local agent that should forward its traffic to the best wifi upstream.
        val localAgent = Agent(nc = localNc, lp = lp("local0"), lnc = lnc, score = localScore)
        localAgent.connect()

        localCb.expectAvailableCallbacks(localAgent.network, validated = false)
        cb.expectAvailableCallbacks(localAgent.network, validated = false)

        val wifiAgent = Agent(lp = lp("wifi0"), nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_INTERNET))
        wifiAgent.connect()

        cb.expectAvailableCallbacks(wifiAgent.network, validated = false)
        listOf(cb, localCb).forEach {
            it.expect<LocalInfoChanged>(localAgent.network) {
                it.info.upstreamNetwork == wifiAgent.network
            }
        }

        verify(netd).ipfwdAddInterfaceForward("local0", "wifi0")

        localAgent.unregisterAfterReplacement(LONG_TIMEOUT_MS)

        val localAgent2 = Agent(nc = localNc, lp = lp("local0"), lnc = lnc, score = localScore)
        localAgent2.connect()

        localCb.expectAvailableCallbacks(localAgent2.network,
                validated = false, upstream = wifiAgent.network)
        cb.expectAvailableCallbacks(localAgent2.network,
                validated = false, upstream = wifiAgent.network)
        cb.expect<Lost> { it.network == localAgent.network }
    }

    @Test
    fun testDestroyedNetworkAsSelectedUpstream() {
        deps.setBuildSdk(VERSION_V)
        val cb = TestableNetworkCallback()
        cm.registerNetworkCallback(NetworkRequest.Builder().clearCapabilities().build(), cb)

        val wifiAgent = Agent(lp = lp("wifi0"), nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_INTERNET))
        wifiAgent.connect()
        cb.expectAvailableCallbacks(wifiAgent.network, validated = false)

        // Unregister wifi pending replacement, then set up a local agent that would have
        // this network as its upstream.
        wifiAgent.unregisterAfterReplacement(LONG_TIMEOUT_MS)
        val localAgent = Agent(nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_LOCAL_NETWORK),
                lp = lp("local0"),
                lnc = FromS(LocalNetworkConfig.Builder()
                        .setUpstreamSelector(NetworkRequest.Builder()
                                .addForbiddenCapability(NET_CAPABILITY_LOCAL_NETWORK)
                                .addTransportType(TRANSPORT_WIFI)
                                .build())
                        .build()),
                score = FromS(NetworkScore.Builder()
                        .setKeepConnectedReason(KEEP_CONNECTED_LOCAL_NETWORK)
                        .build())
        )

        // Connect the local agent. The zombie wifi is its upstream, but the stack doesn't
        // tell netd to add the forward since the wifi0 interface has gone.
        localAgent.connect()
        cb.expectAvailableCallbacks(localAgent.network,
                validated = false, upstream = wifiAgent.network)

        verify(netd, never()).ipfwdAddInterfaceForward("local0", "wifi0")

        // Disconnect wifi without a replacement. Expect an update with upstream null.
        wifiAgent.disconnect()
        verify(netd, never()).ipfwdAddInterfaceForward("local0", "wifi0")
        cb.expect<LocalInfoChanged> { it.info.upstreamNetwork == null }
    }

    @Test
    fun testForwardingRules() {
        deps.setBuildSdk(VERSION_V)
        // Set up a local agent that should forward its traffic to the best DUN upstream.
        val lnc = FromS(LocalNetworkConfig.Builder()
                .setUpstreamSelector(NetworkRequest.Builder()
                        .addCapability(NET_CAPABILITY_DUN)
                        .addForbiddenCapability(NET_CAPABILITY_LOCAL_NETWORK)
                        .build())
                .build())
        val localAgent = Agent(nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_LOCAL_NETWORK),
                lp = lp("local0"),
                lnc = lnc,
                score = FromS(NetworkScore.Builder()
                        .setKeepConnectedReason(KEEP_CONNECTED_LOCAL_NETWORK)
                        .build())
        )
        localAgent.connect()

        val wifiAgent = Agent(score = keepScore(), lp = lp("wifi0"),
                nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_INTERNET))
        val cellAgentDun = Agent(score = keepScore(), lp = lp("cell0"),
                nc = nc(TRANSPORT_CELLULAR, NET_CAPABILITY_INTERNET, NET_CAPABILITY_DUN))
        val wifiAgentDun = Agent(score = keepScore(), lp = lp("wifi1"),
                nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_INTERNET, NET_CAPABILITY_DUN))

        val cb = TestableNetworkCallback()
        cm.registerNetworkCallback(NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_LOCAL_NETWORK)
                .build(),
                cb)
        cb.expectAvailableCallbacks(localAgent.network, validated = false)

        val inOrder = inOrder(netd)
        inOrder.verify(netd, never()).ipfwdAddInterfaceForward(any(), any())
        cb.assertNoCallback()

        wifiAgent.connect()
        inOrder.verify(netd, never()).ipfwdAddInterfaceForward(any(), any())
        cb.assertNoCallback()

        cellAgentDun.connect()
        inOrder.verify(netd).ipfwdEnableForwarding(any())
        inOrder.verify(netd).ipfwdAddInterfaceForward("local0", "cell0")
        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == cellAgentDun.network
        }

        wifiAgentDun.connect()
        inOrder.verify(netd).ipfwdRemoveInterfaceForward("local0", "cell0")
        inOrder.verify(netd).ipfwdAddInterfaceForward("local0", "wifi1")
        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == wifiAgentDun.network
        }

        // Make sure sending the same config again doesn't do anything
        repeat(5) {
            localAgent.sendLocalNetworkConfig(lnc.value)
        }
        inOrder.verifyNoMoreInteractions()

        wifiAgentDun.disconnect()
        cb.expect<LocalInfoChanged>(localAgent.network) { it.info.upstreamNetwork == null }
        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == cellAgentDun.network
        }
        inOrder.verify(netd).ipfwdRemoveInterfaceForward("local0", "wifi1")
        // This can take a little bit of time because it needs to wait for the rematch
        inOrder.verify(netd, timeout(MEDIUM_TIMEOUT_MS)).ipfwdAddInterfaceForward("local0", "cell0")

        cellAgentDun.disconnect()
        inOrder.verify(netd).ipfwdRemoveInterfaceForward("local0", "cell0")
        inOrder.verify(netd).ipfwdDisableForwarding(any())
        cb.expect<LocalInfoChanged>(localAgent.network) { it.info.upstreamNetwork == null }

        val wifiAgentDun2 = Agent(score = keepScore(), lp = lp("wifi2"),
                nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_INTERNET, NET_CAPABILITY_DUN))
        wifiAgentDun2.connect()
        inOrder.verify(netd).ipfwdEnableForwarding(any())
        inOrder.verify(netd).ipfwdAddInterfaceForward("local0", "wifi2")
        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == wifiAgentDun2.network
        }

        wifiAgentDun2.disconnect()
        inOrder.verify(netd).ipfwdRemoveInterfaceForward("local0", "wifi2")
        inOrder.verify(netd).ipfwdDisableForwarding(any())
        cb.expect<LocalInfoChanged>(localAgent.network) { it.info.upstreamNetwork == null }

        val wifiAgentDun3 = Agent(score = keepScore(), lp = lp("wifi3"),
                nc = nc(TRANSPORT_WIFI, NET_CAPABILITY_INTERNET, NET_CAPABILITY_DUN))
        wifiAgentDun3.connect()
        inOrder.verify(netd).ipfwdEnableForwarding(any())
        inOrder.verify(netd).ipfwdAddInterfaceForward("local0", "wifi3")
        cb.expect<LocalInfoChanged>(localAgent.network) {
            it.info.upstreamNetwork == wifiAgentDun3.network
        }

        localAgent.disconnect()
        inOrder.verify(netd).ipfwdRemoveInterfaceForward("local0", "wifi3")
        inOrder.verify(netd).ipfwdDisableForwarding(any())
        cb.expect<Lost>(localAgent.network)
        cb.assertNoCallback()
    }

    @Test
    fun testLocalNetworkUnwanted_withUpstream() {
        doTestLocalNetworkUnwanted(true)
    }

    @Test
    fun testLocalNetworkUnwanted_withoutUpstream() {
        doTestLocalNetworkUnwanted(false)
    }

    fun doTestLocalNetworkUnwanted(haveUpstream: Boolean) {
        deps.setBuildSdk(VERSION_V)

        val nr = NetworkRequest.Builder().addCapability(NET_CAPABILITY_LOCAL_NETWORK).build()
        val requestCb = TestableNetworkCallback()
        cm.requestNetwork(nr, requestCb)
        val listenCb = TestableNetworkCallback()
        cm.registerNetworkCallback(nr, listenCb)

        val upstream = if (haveUpstream) {
            Agent(score = keepScore(), lp = lp("wifi0"),
                    nc = nc(TRANSPORT_WIFI)).also { it.connect() }
        } else {
            null
        }

        // Set up a local agent.
        val lnc = FromS(LocalNetworkConfig.Builder().apply {
            if (haveUpstream) {
                setUpstreamSelector(NetworkRequest.Builder()
                        .addForbiddenCapability(NET_CAPABILITY_LOCAL_NETWORK)
                        .addTransportType(TRANSPORT_WIFI)
                        .build())
            }
        }.build())
        val localAgent = Agent(nc = nc(TRANSPORT_THREAD, NET_CAPABILITY_LOCAL_NETWORK),
                lp = lp("local0"),
                lnc = lnc,
                score = FromS(NetworkScore.Builder().build())
        )
        localAgent.connect()

        requestCb.expectAvailableCallbacks(localAgent.network,
                validated = false, upstream = upstream?.network)
        listenCb.expectAvailableCallbacks(localAgent.network,
                validated = false, upstream = upstream?.network)

        cm.unregisterNetworkCallback(requestCb)

        listenCb.expect<Lost>()
    }
}
