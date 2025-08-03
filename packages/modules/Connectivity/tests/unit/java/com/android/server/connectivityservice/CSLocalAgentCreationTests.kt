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

import android.content.pm.PackageManager.FEATURE_LEANBACK
import android.net.INetd
import android.net.LocalNetworkConfig
import android.net.NativeNetworkConfig
import android.net.NativeNetworkType
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK
import android.net.NetworkRequest
import android.net.NetworkScore
import android.net.NetworkScore.KEEP_CONNECTED_FOR_TEST
import android.net.VpnManager
import android.os.Build
import androidx.test.filters.SmallTest
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.RecorderCallback.CallbackEntry.Available
import com.android.testutils.TestableNetworkCallback
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout

private const val TIMEOUT_MS = 2_000L
private const val NO_CALLBACK_TIMEOUT_MS = 200L

private fun keepConnectedScore() =
        FromS(NetworkScore.Builder().setKeepConnectedReason(KEEP_CONNECTED_FOR_TEST).build())

private fun defaultLnc() = FromS(LocalNetworkConfig.Builder().build())

@DevSdkIgnoreRunner.MonitorThreadLeak
@RunWith(DevSdkIgnoreRunner::class)
@SmallTest
@IgnoreUpTo(Build.VERSION_CODES.R)
class CSLocalAgentCreationTests(
        private val sdkLevel: Int,
        private val isTv: Boolean,
        private val addLocalNetCapToRequest: Boolean
) : CSTest() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun arguments() = listOf(
                arrayOf(VERSION_V, false /* isTv */, true /* addLocalNetCapToRequest */),
                arrayOf(VERSION_V, false /* isTv */, false /* addLocalNetCapToRequest */),
                arrayOf(VERSION_V, true /* isTv */, true /* addLocalNetCapToRequest */),
                arrayOf(VERSION_V, true /* isTv */, false /* addLocalNetCapToRequest */),
                arrayOf(VERSION_U, false /* isTv */, true /* addLocalNetCapToRequest */),
                arrayOf(VERSION_U, false /* isTv */, false /* addLocalNetCapToRequest */),
                arrayOf(VERSION_U, true /* isTv */, true /* addLocalNetCapToRequest */),
                arrayOf(VERSION_U, true /* isTv */, false /* addLocalNetCapToRequest */),
                arrayOf(VERSION_T, false /* isTv */, false /* addLocalNetCapToRequest */),
                arrayOf(VERSION_T, true /* isTv */, false /* addLocalNetCapToRequest */),
        )
    }

    private fun makeNativeNetworkConfigLocal(netId: Int, permission: Int) =
            NativeNetworkConfig(netId, NativeNetworkType.PHYSICAL_LOCAL, permission,
                    false /* secure */, VpnManager.TYPE_VPN_NONE, false /* excludeLocalRoutes */)

    @Test
    fun testLocalAgents() {
        val netdInOrder = inOrder(netd)
        deps.setBuildSdk(sdkLevel)
        doReturn(isTv).`when`(packageManager).hasSystemFeature(FEATURE_LEANBACK)
        val allNetworksCb = TestableNetworkCallback()
        val request = NetworkRequest.Builder()
        if (addLocalNetCapToRequest) {
            request.addCapability(NET_CAPABILITY_LOCAL_NETWORK)
        }
        cm.registerNetworkCallback(request.build(), allNetworksCb)
        val ncTemplate = NetworkCapabilities.Builder().run {
            addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            addCapability(NET_CAPABILITY_LOCAL_NETWORK)
        }.build()
        val localAgent = if (sdkLevel >= VERSION_V || sdkLevel == VERSION_U && isTv) {
            Agent(nc = ncTemplate, score = keepConnectedScore(), lnc = defaultLnc())
        } else {
            assertFailsWith<IllegalArgumentException> { Agent(nc = ncTemplate, lnc = defaultLnc()) }
            netdInOrder.verify(netd, never()).networkCreate(any())
            return
        }
        localAgent.connect()
        netdInOrder.verify(netd).networkCreate(
                makeNativeNetworkConfigLocal(localAgent.network.netId, INetd.PERMISSION_NONE))
        if (addLocalNetCapToRequest) {
            assertEquals(localAgent.network, allNetworksCb.expect<Available>().network)
        } else {
            allNetworksCb.assertNoCallback(NO_CALLBACK_TIMEOUT_MS)
        }
        cm.unregisterNetworkCallback(allNetworksCb)
        localAgent.disconnect()
        netdInOrder.verify(netd, timeout(TIMEOUT_MS)).networkDestroy(localAgent.network.netId)
    }

    @Test
    fun testBadAgents() {
        assertFailsWith<IllegalArgumentException> {
            Agent(nc = NetworkCapabilities.Builder()
                    .addCapability(NET_CAPABILITY_LOCAL_NETWORK)
                    .build(),
                    lnc = null)
        }
        assertFailsWith<IllegalArgumentException> {
            Agent(nc = NetworkCapabilities.Builder().build(), lnc = defaultLnc())
        }
    }
}
