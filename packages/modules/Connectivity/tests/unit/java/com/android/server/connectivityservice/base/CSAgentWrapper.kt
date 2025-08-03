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

import android.content.Context
import android.net.ConnectivityManager
import android.net.INetworkMonitor
import android.net.INetworkMonitor.NETWORK_VALIDATION_PROBE_DNS
import android.net.INetworkMonitor.NETWORK_VALIDATION_PROBE_HTTP
import android.net.INetworkMonitorCallbacks
import android.net.LinkProperties
import android.net.LocalNetworkConfig
import android.net.Network
import android.net.NetworkAgent
import android.net.NetworkAgentConfig
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkProvider
import android.net.NetworkRequest
import android.net.NetworkScore
import android.net.NetworkTestResultParcelable
import android.net.networkstack.NetworkStackClientBase
import android.os.HandlerThread
import com.android.testutils.RecorderCallback.CallbackEntry.Available
import com.android.testutils.RecorderCallback.CallbackEntry.Lost
import com.android.testutils.TestableNetworkCallback
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.verify
import org.mockito.stubbing.Answer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.fail

const val SHORT_TIMEOUT_MS = 200L

private inline fun <reified T> ArgumentCaptor() = ArgumentCaptor.forClass(T::class.java)

private val agentCounter = AtomicInteger(1)
private fun nextAgentId() = agentCounter.getAndIncrement()

/**
 * A wrapper for network agents, for use with CSTest.
 *
 * This class knows how to interact with CSTest and has helpful methods to make fake agents
 * that can be manipulated directly from a test.
 */
class CSAgentWrapper(
        val context: Context,
        val deps: ConnectivityService.Dependencies,
        csHandlerThread: HandlerThread,
        networkStack: NetworkStackClientBase,
        nac: NetworkAgentConfig,
        val nc: NetworkCapabilities,
        val lp: LinkProperties,
        val lnc: FromS<LocalNetworkConfig>?,
        val score: FromS<NetworkScore>,
        val provider: NetworkProvider?
) : TestableNetworkCallback.HasNetwork {
    private val TAG = "CSAgent${nextAgentId()}"
    private val VALIDATION_RESULT_INVALID = 0
    private val NO_PROBE_RESULT = 0
    private val VALIDATION_TIMESTAMP = 1234L
    private val agent: NetworkAgent
    private val nmCallbacks: INetworkMonitorCallbacks
    val networkMonitor = mock<INetworkMonitor>()
    private var nmValidationRedirectUrl: String? = null
    private var nmValidationResult = NO_PROBE_RESULT
    private var nmProbesCompleted = NO_PROBE_RESULT
    private var nmProbesSucceeded = NO_PROBE_RESULT

    override val network: Network get() = agent.network!!

    init {
        // Capture network monitor callbacks and simulate network monitor
        val validateAnswer = Answer {
            CSTest.CSTestExecutor.execute { onValidationRequested() }
            null
        }
        doAnswer(validateAnswer).`when`(networkMonitor).notifyNetworkConnected(any(), any())
        doAnswer(validateAnswer).`when`(networkMonitor).notifyNetworkConnectedParcel(any())
        doAnswer(validateAnswer).`when`(networkMonitor).forceReevaluation(anyInt())
        val nmNetworkCaptor = ArgumentCaptor<Network>()
        val nmCbCaptor = ArgumentCaptor<INetworkMonitorCallbacks>()
        doNothing().`when`(networkStack).makeNetworkMonitor(
                nmNetworkCaptor.capture(),
                any() /* name */,
                nmCbCaptor.capture())

        // Create the actual agent. NetworkAgent is abstract, so make an anonymous subclass.
        if (deps.isAtLeastS()) {
            agent = object : NetworkAgent(context, csHandlerThread.looper, TAG,
                    nc, lp, lnc?.value, score.value, nac, provider) {}
        } else {
            agent = object : NetworkAgent(context, csHandlerThread.looper, TAG,
                    nc, lp, 50 /* score */, nac, provider) {}
        }
        agent.register()
        assertEquals(agent.network!!.netId, nmNetworkCaptor.value.netId)
        nmCallbacks = nmCbCaptor.value
        nmCallbacks.onNetworkMonitorCreated(networkMonitor)
    }

    private fun onValidationRequested() {
        if (deps.isAtLeastT()) {
            verify(networkMonitor).notifyNetworkConnectedParcel(any())
        } else {
            verify(networkMonitor).notifyNetworkConnected(any(), any())
        }
        nmCallbacks.notifyProbeStatusChanged(0 /* completed */, 0 /* succeeded */)
        val p = NetworkTestResultParcelable()
        p.result = nmValidationResult
        p.probesAttempted = nmProbesCompleted
        p.probesSucceeded = nmProbesSucceeded
        p.redirectUrl = nmValidationRedirectUrl
        p.timestampMillis = VALIDATION_TIMESTAMP
        nmCallbacks.notifyNetworkTestedWithExtras(p)
    }

    fun connect() {
        val mgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder().apply {
            clearCapabilities()
            if (nc.transportTypes.isNotEmpty()) addTransportType(nc.transportTypes[0])
        }.build()
        val cb = TestableNetworkCallback()
        mgr.registerNetworkCallback(request, cb)
        agent.markConnected()
        if (null == cb.poll { it is Available && agent.network == it.network }) {
            if (!nc.hasCapability(NET_CAPABILITY_NOT_SUSPENDED) &&
                    nc.hasTransport(TRANSPORT_CELLULAR)) {
                // ConnectivityService adds NOT_SUSPENDED by default to all non-cell agents. An
                // agent without NOT_SUSPENDED will not connect, instead going into the SUSPENDED
                // state, so this call will not terminate.
                // Instead of forcefully adding NOT_SUSPENDED to all agents like older tools did,
                // it's better to let the developer manage it as they see fit but help them
                // debug if they forget.
                fail("Could not connect the agent. Did you forget to add " +
                        "NET_CAPABILITY_NOT_SUSPENDED ?")
            }
            fail("Could not connect the agent. Instrumentation failure ?")
        }
        mgr.unregisterNetworkCallback(cb)
    }

    fun disconnect() {
        val mgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder().apply {
            clearCapabilities()
            if (nc.transportTypes.isNotEmpty()) addTransportType(nc.transportTypes[0])
        }.build()
        val cb = TestableNetworkCallback(timeoutMs = SHORT_TIMEOUT_MS)
        mgr.registerNetworkCallback(request, cb)
        cb.eventuallyExpect<Available> { it.network == agent.network }
        agent.unregister()
        cb.eventuallyExpect<Lost> { it.network == agent.network }
    }

    fun unregisterAfterReplacement(timeoutMs: Int) = agent.unregisterAfterReplacement(timeoutMs)

    fun sendLocalNetworkConfig(lnc: LocalNetworkConfig) = agent.sendLocalNetworkConfig(lnc)
    fun sendNetworkCapabilities(nc: NetworkCapabilities) = agent.sendNetworkCapabilities(nc)

    fun connectWithCaptivePortal(redirectUrl: String) {
        setCaptivePortal(redirectUrl)
        connect()
    }

    fun setProbesStatus(probesCompleted: Int, probesSucceeded: Int) {
        nmProbesCompleted = probesCompleted
        nmProbesSucceeded = probesSucceeded
    }

    fun setCaptivePortal(redirectUrl: String) {
        nmValidationResult = VALIDATION_RESULT_INVALID
        nmValidationRedirectUrl = redirectUrl
        // Suppose the portal is found when NetworkMonitor probes NETWORK_VALIDATION_PROBE_HTTP
        // in the beginning. Because NETWORK_VALIDATION_PROBE_HTTP is the decisive probe for captive
        // portal, considering the NETWORK_VALIDATION_PROBE_HTTPS hasn't probed yet and set only
        // DNS and HTTP probes completed.
        setProbesStatus(
            NETWORK_VALIDATION_PROBE_DNS or NETWORK_VALIDATION_PROBE_HTTP /* probesCompleted */,
            VALIDATION_RESULT_INVALID /* probesSucceeded */)
    }
}
