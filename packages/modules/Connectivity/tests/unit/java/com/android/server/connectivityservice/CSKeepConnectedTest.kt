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

import android.net.LocalNetworkConfig
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest
import android.net.NetworkScore
import android.net.NetworkScore.KEEP_CONNECTED_FOR_TEST
import android.net.NetworkScore.KEEP_CONNECTED_LOCAL_NETWORK
import android.os.Build
import androidx.test.filters.SmallTest
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.RecorderCallback.CallbackEntry.Lost
import com.android.testutils.TestableNetworkCallback
import org.junit.Test
import org.junit.runner.RunWith

@DevSdkIgnoreRunner.MonitorThreadLeak
@RunWith(DevSdkIgnoreRunner::class)
@SmallTest
@IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
class CSKeepConnectedTest : CSTest() {
    @Test
    fun testKeepConnectedLocalAgent() {
        deps.setBuildSdk(VERSION_V)
        val nc = NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .addCapability(NET_CAPABILITY_LOCAL_NETWORK)
                .build()
        val keepConnectedAgent = Agent(nc = nc, score = FromS(NetworkScore.Builder()
                .setKeepConnectedReason(KEEP_CONNECTED_LOCAL_NETWORK)
                .build()),
                lnc = FromS(LocalNetworkConfig.Builder().build()))
        val dontKeepConnectedAgent = Agent(nc = nc,
                lnc = FromS(LocalNetworkConfig.Builder().build()))
        doTestKeepConnected(keepConnectedAgent, dontKeepConnectedAgent)
    }

    @Test
    fun testKeepConnectedForTest() {
        val keepAgent = Agent(score = FromS(NetworkScore.Builder()
                .setKeepConnectedReason(KEEP_CONNECTED_FOR_TEST)
                .build()))
        val dontKeepAgent = Agent()
        doTestKeepConnected(keepAgent, dontKeepAgent)
    }

    fun doTestKeepConnected(keepAgent: CSAgentWrapper, dontKeepAgent: CSAgentWrapper) {
        val cb = TestableNetworkCallback()
        cm.registerNetworkCallback(NetworkRequest.Builder().clearCapabilities().build(), cb)

        keepAgent.connect()
        dontKeepAgent.connect()

        cb.expectAvailableCallbacks(keepAgent.network, validated = false)
        cb.expectAvailableCallbacks(dontKeepAgent.network, validated = false)

        // After the nascent timer, the agent without keep connected gets lost.
        cb.expect<Lost>(dontKeepAgent.network)
        cb.assertNoCallback()
    }
}
