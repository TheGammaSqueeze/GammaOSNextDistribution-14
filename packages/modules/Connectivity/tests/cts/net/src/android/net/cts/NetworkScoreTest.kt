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
package android.net.cts

import android.Manifest.permission.MANAGE_TEST_NETWORKS
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkAgent
import android.net.NetworkAgentConfig
import android.net.NetworkCapabilities
import android.net.NetworkProvider
import android.net.NetworkRequest
import android.net.NetworkScore
import android.net.VpnManager
import android.net.VpnTransportInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.test.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.RecorderCallback.CallbackEntry
import com.android.testutils.TestableNetworkCallback
import com.android.testutils.TestableNetworkCallback.HasNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

// This test doesn't really have a constraint on how fast the methods should return. If it's
// going to fail, it will simply wait forever, so setting a high timeout lowers the flake ratio
// without affecting the run time of successful runs. Thus, set a very high timeout.
private const val TIMEOUT_MS = 30_000L
// When waiting for a NetworkCallback to determine there was no timeout, waiting is the
// only possible thing (the relevant handler is the one in the real ConnectivityService,
// and then there is the Binder call), so have a short timeout for this as it will be
// exhausted every time.
private const val NO_CALLBACK_TIMEOUT = 200L

private val testContext: Context
    get() = InstrumentationRegistry.getContext()

private fun score(exiting: Boolean = false, primary: Boolean = false) =
        NetworkScore.Builder().setExiting(exiting).setTransportPrimary(primary)
                // TODO : have a constant KEEP_CONNECTED_FOR_TEST ?
                .setKeepConnectedReason(NetworkScore.KEEP_CONNECTED_FOR_HANDOVER)
                .build()

@IgnoreUpTo(Build.VERSION_CODES.R)
@RunWith(DevSdkIgnoreRunner::class)
class NetworkScoreTest {
    private val TAG = javaClass.simpleName
    private val mCm = testContext.getSystemService(ConnectivityManager::class.java)!!
    private val mHandlerThread = HandlerThread("$TAG handler thread")
    private val mHandler by lazy { Handler(mHandlerThread.looper) }
    private val agentsToCleanUp = Collections.synchronizedList(mutableListOf<NetworkAgent>())
    private val callbacksToCleanUp = mutableListOf<TestableNetworkCallback>()

    @Before
    fun setUp() {
        mHandlerThread.start()
    }

    @After
    fun tearDown() {
        val agentCleanUpCb = TestableNetworkCallback(TIMEOUT_MS).also { cb ->
            mCm.registerNetworkCallback(
                NetworkRequest.Builder().clearCapabilities()
                    .addTransportType(NetworkCapabilities.TRANSPORT_TEST).build(), cb, mHandler
            )
        }
        Log.i(TAG, "Teardown on thread ${System.identityHashCode(Thread.currentThread())} " +
                "cleaning up ${agentsToCleanUp.size} agents")
        agentsToCleanUp.forEach {
            Log.i(TAG, "Unregister agent for net ${it.network}")
            it.unregister()
            agentCleanUpCb.eventuallyExpect<CallbackEntry.Lost> { cb -> cb.network == it.network }
        }
        mCm.unregisterNetworkCallback(agentCleanUpCb)

        callbacksToCleanUp.forEach { mCm.unregisterNetworkCallback(it) }
        mHandlerThread.quitSafely()
        mHandlerThread.join()
    }

    // Returns a networkCallback that sends onAvailable on the best network with TRANSPORT_TEST.
    private fun makeTestNetworkCallback() = TestableNetworkCallback(TIMEOUT_MS).also { cb ->
        mCm.registerBestMatchingNetworkCallback(NetworkRequest.Builder().clearCapabilities()
                .addTransportType(NetworkCapabilities.TRANSPORT_TEST).build(), cb, mHandler)
        callbacksToCleanUp.add(cb)
    }

    // TestNetworkCallback is made to interact with a wrapper of NetworkAgent, because it's
    // made for ConnectivityServiceTest.
    // TODO : have TestNetworkCallback work for NetworkAgent too and remove this class.
    private class AgentWrapper(val agent: NetworkAgent) : HasNetwork {
        override val network = checkNotNull(agent.network)
        fun sendNetworkScore(s: NetworkScore) = agent.sendNetworkScore(s)
    }

    private fun createTestNetworkAgent(
            // The network always has TRANSPORT_TEST, plus optional transports
        optionalTransports: IntArray = IntArray(size = 0),
        everUserSelected: Boolean = false,
        acceptUnvalidated: Boolean = false,
        isExiting: Boolean = false,
        isPrimary: Boolean = false
    ): AgentWrapper {
        val nc = NetworkCapabilities.Builder().apply {
            addTransportType(NetworkCapabilities.TRANSPORT_TEST)
            optionalTransports.forEach { addTransportType(it) }
            // Add capabilities that are common, just for realism. It's not strictly necessary
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            // Remove capabilities that a test network agent shouldn't have and that are not
            // needed for the purposes of this test.
            removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
            removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            if (optionalTransports.contains(NetworkCapabilities.TRANSPORT_VPN)) {
                addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                setTransportInfo(VpnTransportInfo(VpnManager.TYPE_VPN_SERVICE, null))
            }
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
        }.build()
        val config = NetworkAgentConfig.Builder()
                .setExplicitlySelected(everUserSelected)
                .setUnvalidatedConnectivityAcceptable(acceptUnvalidated)
                .build()
        val score = score(exiting = isExiting, primary = isPrimary)
        val context = testContext
        val looper = mHandlerThread.looper
        val agent = object : NetworkAgent(context, looper, "NetworkScore test agent", nc,
                LinkProperties(), score, config, NetworkProvider(context, looper,
                "NetworkScore test provider")) {}.also {
            Log.i(TAG, "Add on thread ${System.identityHashCode(Thread.currentThread())} " +
                    "agent to clean up $it")
            agentsToCleanUp.add(it)
        }
        runWithShellPermissionIdentity({ agent.register() }, MANAGE_TEST_NETWORKS)
        agent.markConnected()
        return AgentWrapper(agent)
    }

    @Test
    fun testExitingLosesAndOldSatisfierWins() {
        val cb = makeTestNetworkCallback()
        val agent1 = createTestNetworkAgent()
        cb.expectAvailableThenValidatedCallbacks(agent1)
        val agent2 = createTestNetworkAgent()
        // Because the existing network must win, the callback stays on agent1.
        cb.assertNoCallback(NO_CALLBACK_TIMEOUT)
        agent1.sendNetworkScore(score(exiting = true))
        // Now that agent1 is exiting, the callback is satisfied by agent2.
        cb.expectAvailableCallbacks(agent2.network)
        agent1.sendNetworkScore(score(exiting = false))
        // Agent1 is no longer exiting, but agent2 is the current satisfier.
        cb.assertNoCallback(NO_CALLBACK_TIMEOUT)
    }

    @Test
    fun testVpnWins() {
        val cb = makeTestNetworkCallback()
        val agent1 = createTestNetworkAgent()
        cb.expectAvailableThenValidatedCallbacks(agent1.network)
        val agent2 = createTestNetworkAgent(intArrayOf(NetworkCapabilities.TRANSPORT_VPN))
        // VPN wins out against agent1 even before it's validated (hence the "then validated",
        // because it becomes the best network for this callback before it validates)
        cb.expectAvailableThenValidatedCallbacks(agent2.network)
    }

    @Test
    fun testEverUserSelectedAcceptUnvalidatedWins() {
        val cb = makeTestNetworkCallback()
        val agent1 = createTestNetworkAgent()
        cb.expectAvailableThenValidatedCallbacks(agent1.network)
        val agent2 = createTestNetworkAgent(everUserSelected = true, acceptUnvalidated = true)
        // agent2 wins out against agent1 even before it's validated, because user-selected and
        // accept unvalidated networks should win against even networks that are validated.
        cb.expectAvailableThenValidatedCallbacks(agent2.network)
    }

    @Test
    fun testPreferredTransportOrder() {
        val cb = makeTestNetworkCallback()
        val agentCell = createTestNetworkAgent(intArrayOf(NetworkCapabilities.TRANSPORT_CELLULAR))
        cb.expectAvailableThenValidatedCallbacks(agentCell.network)
        val agentWifi = createTestNetworkAgent(intArrayOf(NetworkCapabilities.TRANSPORT_WIFI))
        // In the absence of other discriminating factors, agentWifi wins against agentCell because
        // of its better transport, but only after it validates.
        cb.expectAvailableDoubleValidatedCallbacks(agentWifi)
        val agentEth = createTestNetworkAgent(intArrayOf(NetworkCapabilities.TRANSPORT_ETHERNET))
        // Likewise, agentEth wins against agentWifi after validation because of its better
        // transport.
        cb.expectAvailableCallbacksValidated(agentEth)
    }

    @Test
    fun testTransportPrimary() {
        val cb = makeTestNetworkCallback()
        val agent1 = createTestNetworkAgent()
        cb.expectAvailableThenValidatedCallbacks(agent1)
        val agent2 = createTestNetworkAgent()
        // Because the existing network must win, the callback stays on agent1.
        cb.assertNoCallback(NO_CALLBACK_TIMEOUT)
        agent2.sendNetworkScore(score(primary = true))
        // Now that agent2 is primary, the callback is satisfied by agent2.
        cb.expectAvailableCallbacks(agent2.network)
        agent1.sendNetworkScore(score(primary = true))
        // Agent1 is primary too, but agent2 is the current satisfier
        cb.assertNoCallback(NO_CALLBACK_TIMEOUT)
        agent2.sendNetworkScore(score(primary = false))
        // Now agent1 is primary and agent2 isn't
        cb.expectAvailableCallbacks(agent1.network)
    }

    // TODO (b/187929636) : add a test making sure that validated networks win over unvalidated
    // ones. Right now this is not possible because this CTS can't directly manipulate the
    // validation state of a network.
}
