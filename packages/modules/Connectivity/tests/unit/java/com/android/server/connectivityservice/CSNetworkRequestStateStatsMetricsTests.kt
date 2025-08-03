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

import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Process
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.TestableNetworkCallback
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.argThat
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
class CSNetworkRequestStateStatsMetricsTests : CSTest() {
    private val CELL_INTERNET_NOT_METERED_NC = NetworkCapabilities.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            .build().setRequestorUidAndPackageName(Process.myUid(), context.getPackageName())

    private val CELL_INTERNET_NOT_METERED_NR = NetworkRequest.Builder()
            .setCapabilities(CELL_INTERNET_NOT_METERED_NC).build()

    @Before
    fun setup() {
        waitForIdle()
        clearInvocations(networkRequestStateStatsMetrics)
    }

    @Test
    fun testRequestTypeNRProduceMetrics() {
        cm.requestNetwork(CELL_INTERNET_NOT_METERED_NR, TestableNetworkCallback())
        waitForIdle()

        verify(networkRequestStateStatsMetrics).onNetworkRequestReceived(
                argThat{req -> req.networkCapabilities.equals(
                        CELL_INTERNET_NOT_METERED_NR.networkCapabilities)})
    }

    @Test
    fun testListenTypeNRProduceNoMetrics() {
        cm.registerNetworkCallback(CELL_INTERNET_NOT_METERED_NR, TestableNetworkCallback())
        waitForIdle()
        verify(networkRequestStateStatsMetrics, never()).onNetworkRequestReceived(any())
    }

    @Test
    fun testRemoveRequestTypeNRProduceMetrics() {
        val cb = TestableNetworkCallback()
        cm.requestNetwork(CELL_INTERNET_NOT_METERED_NR, cb)

        waitForIdle()
        clearInvocations(networkRequestStateStatsMetrics)

        cm.unregisterNetworkCallback(cb)
        waitForIdle()
        verify(networkRequestStateStatsMetrics).onNetworkRequestRemoved(
                argThat{req -> req.networkCapabilities.equals(
                        CELL_INTERNET_NOT_METERED_NR.networkCapabilities)})
    }
}
