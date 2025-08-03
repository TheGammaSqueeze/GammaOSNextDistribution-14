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

package com.android.testutils;

import android.content.Context
import android.net.KeepalivePacketData
import android.net.LinkProperties
import android.net.NetworkAgent
import android.net.NetworkAgentConfig
import android.net.NetworkCapabilities
import android.net.NetworkProvider
import android.net.QosFilter
import android.net.Uri
import android.os.Looper
import com.android.net.module.util.ArrayTrackRecord
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnAddKeepalivePacketFilter
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnAutomaticReconnectDisabled
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnBandwidthUpdateRequested
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnDscpPolicyStatusUpdated
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnNetworkCreated
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnNetworkDestroyed
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnNetworkUnwanted
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnRegisterQosCallback
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnRemoveKeepalivePacketFilter
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnSaveAcceptUnvalidated
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnSignalStrengthThresholdsUpdated
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnStartSocketKeepalive
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnStopSocketKeepalive
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnUnregisterQosCallback
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnValidationStatus
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Assert.assertArrayEquals

// Any legal score (0~99) for the test network would do, as it is going to be kept up by the
// requests filed by the test and should never match normal internet requests. 70 is the default
// score of Ethernet networks, it's as good a value as any other.
private const val TEST_NETWORK_SCORE = 70

private class Provider(context: Context, looper: Looper) :
            NetworkProvider(context, looper, "NetworkAgentTest NetworkProvider")

public open class TestableNetworkAgent(
    context: Context,
    looper: Looper,
    val nc: NetworkCapabilities,
    val lp: LinkProperties,
    conf: NetworkAgentConfig
) : NetworkAgent(context, looper, TestableNetworkAgent::class.java.simpleName /* tag */,
        nc, lp, TEST_NETWORK_SCORE, conf, Provider(context, looper)) {

    val DEFAULT_TIMEOUT_MS = 5000L

    val history = ArrayTrackRecord<CallbackEntry>().newReadHead()

    sealed class CallbackEntry {
        object OnBandwidthUpdateRequested : CallbackEntry()
        object OnNetworkUnwanted : CallbackEntry()
        data class OnAddKeepalivePacketFilter(
            val slot: Int,
            val packet: KeepalivePacketData
        ) : CallbackEntry()
        data class OnRemoveKeepalivePacketFilter(val slot: Int) : CallbackEntry()
        data class OnStartSocketKeepalive(
            val slot: Int,
            val interval: Int,
            val packet: KeepalivePacketData
        ) : CallbackEntry()
        data class OnStopSocketKeepalive(val slot: Int) : CallbackEntry()
        data class OnSaveAcceptUnvalidated(val accept: Boolean) : CallbackEntry()
        object OnAutomaticReconnectDisabled : CallbackEntry()
        data class OnValidationStatus(val status: Int, val uri: Uri?) : CallbackEntry()
        data class OnSignalStrengthThresholdsUpdated(val thresholds: IntArray) : CallbackEntry()
        object OnNetworkCreated : CallbackEntry()
        object OnNetworkDestroyed : CallbackEntry()
        data class OnDscpPolicyStatusUpdated(val policyId: Int, val status: Int) : CallbackEntry()
        data class OnRegisterQosCallback(
            val callbackId: Int,
            val filter: QosFilter
        ) : CallbackEntry()
        data class OnUnregisterQosCallback(val callbackId: Int) : CallbackEntry()
    }

    override fun onBandwidthUpdateRequested() {
        history.add(OnBandwidthUpdateRequested)
    }

    override fun onNetworkUnwanted() {
        history.add(OnNetworkUnwanted)
    }

    override fun onAddKeepalivePacketFilter(slot: Int, packet: KeepalivePacketData) {
        history.add(OnAddKeepalivePacketFilter(slot, packet))
    }

    override fun onRemoveKeepalivePacketFilter(slot: Int) {
        history.add(OnRemoveKeepalivePacketFilter(slot))
    }

    override fun onStartSocketKeepalive(
        slot: Int,
        interval: Duration,
        packet: KeepalivePacketData
    ) {
        history.add(OnStartSocketKeepalive(slot, interval.seconds.toInt(), packet))
    }

    override fun onStopSocketKeepalive(slot: Int) {
        history.add(OnStopSocketKeepalive(slot))
    }

    override fun onSaveAcceptUnvalidated(accept: Boolean) {
        history.add(OnSaveAcceptUnvalidated(accept))
    }

    override fun onAutomaticReconnectDisabled() {
        history.add(OnAutomaticReconnectDisabled)
    }

    override fun onSignalStrengthThresholdsUpdated(thresholds: IntArray) {
        history.add(OnSignalStrengthThresholdsUpdated(thresholds))
    }

    fun expectSignalStrengths(thresholds: IntArray? = intArrayOf()) {
        expectCallback<OnSignalStrengthThresholdsUpdated>().let {
            assertArrayEquals(thresholds, it.thresholds)
        }
    }

    override fun onQosCallbackRegistered(qosCallbackId: Int, filter: QosFilter) {
        history.add(OnRegisterQosCallback(qosCallbackId, filter))
    }

    override fun onQosCallbackUnregistered(qosCallbackId: Int) {
        history.add(OnUnregisterQosCallback(qosCallbackId))
    }

    override fun onValidationStatus(status: Int, uri: Uri?) {
        history.add(OnValidationStatus(status, uri))
    }

    override fun onNetworkCreated() {
        history.add(OnNetworkCreated)
    }

    override fun onNetworkDestroyed() {
        history.add(OnNetworkDestroyed)
    }

    override fun onDscpPolicyStatusUpdated(policyId: Int, status: Int) {
        history.add(OnDscpPolicyStatusUpdated(policyId, status))
    }

    // Expects the initial validation event that always occurs immediately after registering
    // a NetworkAgent whose network does not require validation (which test networks do
    // not, since they lack the INTERNET capability). It always contains the default argument
    // for the URI.
    fun expectValidationBypassedStatus() = expectCallback<OnValidationStatus>().let {
        assertEquals(it.status, VALID_NETWORK)
        // The returned Uri is parsed from the empty string, which means it's an
        // instance of the (private) Uri.StringUri. There are no real good ways
        // to check this, the least bad is to just convert it to a string and
        // make sure it's empty.
        assertEquals("", it.uri.toString())
    }

    inline fun <reified T : CallbackEntry> expectCallback(): T {
        val foundCallback = history.poll(DEFAULT_TIMEOUT_MS)
        assertTrue(foundCallback is T, "Expected ${T::class} but found $foundCallback")
        return foundCallback
    }

    inline fun <reified T : CallbackEntry> expectCallback(valid: (T) -> Boolean) {
        val foundCallback = history.poll(DEFAULT_TIMEOUT_MS)
        assertTrue(foundCallback is T, "Expected ${T::class} but found $foundCallback")
        assertTrue(valid(foundCallback), "Unexpected callback : $foundCallback")
    }

    inline fun <reified T : CallbackEntry> eventuallyExpect() =
            history.poll(DEFAULT_TIMEOUT_MS) { it is T }.also {
                assertNotNull(it, "Callback ${T::class} not received")
    } as T

    fun assertNoCallback() {
        assertTrue(waitForIdle(DEFAULT_TIMEOUT_MS),
                "Handler didn't became idle after ${DEFAULT_TIMEOUT_MS}ms")
        assertNull(history.peek())
    }
}
