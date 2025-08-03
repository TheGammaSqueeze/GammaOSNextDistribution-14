/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.testutils

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.LinkProperties
import android.net.TestNetworkInterface
import android.net.TestNetworkManager
import android.os.Binder
import android.os.Build
import androidx.annotation.RequiresApi
import com.android.modules.utils.build.SdkLevel.isAtLeastR
import com.android.modules.utils.build.SdkLevel.isAtLeastS
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/**
 * Create a test network based on a TUN interface with a LinkAddress.
 *
 * TODO: remove this function after fixing all the callers to use a list of LinkAddresses.
 * This method will block until the test network is available. Requires
 * [android.Manifest.permission.CHANGE_NETWORK_STATE] and
 * [android.Manifest.permission.MANAGE_TEST_NETWORKS].
 */
fun initTestNetwork(
    context: Context,
    interfaceAddr: LinkAddress,
    setupTimeoutMs: Long = 10_000L
): TestNetworkTracker {
    return initTestNetwork(context, listOf(interfaceAddr), setupTimeoutMs)
}

/**
 * Create a test network based on a TUN interface with a LinkAddress list.
 *
 * This method will block until the test network is available. Requires
 * [android.Manifest.permission.CHANGE_NETWORK_STATE] and
 * [android.Manifest.permission.MANAGE_TEST_NETWORKS].
 */
fun initTestNetwork(
    context: Context,
    linkAddrs: List<LinkAddress>,
    setupTimeoutMs: Long = 10_000L
): TestNetworkTracker {
    return initTestNetwork(context, linkAddrs, lp = null, setupTimeoutMs = setupTimeoutMs)
}

/**
 * Create a test network based on a TUN interface
 *
 * This method will block until the test network is available. Requires
 * [android.Manifest.permission.CHANGE_NETWORK_STATE] and
 * [android.Manifest.permission.MANAGE_TEST_NETWORKS].
 *
 * This is only usable starting from R as [TestNetworkManager] has no support for specifying
 * LinkProperties on Q.
 */
@RequiresApi(Build.VERSION_CODES.R)
fun initTestNetwork(
    context: Context,
    lp: LinkProperties,
    setupTimeoutMs: Long = 10_000L
): TestNetworkTracker {
    return initTestNetwork(context, lp.linkAddresses, lp, setupTimeoutMs)
}

private fun initTestNetwork(
    context: Context,
    linkAddrs: List<LinkAddress>,
    lp: LinkProperties?,
    setupTimeoutMs: Long = 10_000L
): TestNetworkTracker {
    val tnm = context.getSystemService(TestNetworkManager::class.java)!!
    val iface = if (isAtLeastS()) tnm.createTunInterface(linkAddrs)
    else tnm.createTunInterface(linkAddrs.toTypedArray())
    val lpWithIface = if (lp == null) null else LinkProperties(lp).apply {
        interfaceName = iface.interfaceName
    }
    return TestNetworkTracker(context, iface, tnm, lpWithIface, setupTimeoutMs)
}

/**
 * Utility class to create and track test networks.
 *
 * This class is not thread-safe.
 */
class TestNetworkTracker internal constructor(
    val context: Context,
    val iface: TestNetworkInterface,
    val tnm: TestNetworkManager,
    val lp: LinkProperties?,
    setupTimeoutMs: Long
) : TestableNetworkCallback.HasNetwork {
    private val cm = context.getSystemService(ConnectivityManager::class.java)!!
    private val binder = Binder()

    private val networkCallback: NetworkCallback
    override val network: Network
    val testIface: TestNetworkInterface

    init {
        val networkFuture = CompletableFuture<Network>()
        val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_TEST)
                // Test networks do not have NOT_VPN or TRUSTED capabilities by default
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .setNetworkSpecifier(CompatUtil.makeTestNetworkSpecifier(iface.interfaceName))
                .build()
        networkCallback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkFuture.complete(network)
            }
        }
        cm.requestNetwork(networkRequest, networkCallback)

        network = try {
            if (lp != null) {
                assertTrue(isAtLeastR(), "Cannot specify TestNetwork LinkProperties before R")
                tnm.setupTestNetwork(lp, true /* isMetered */, binder)
            } else {
                tnm.setupTestNetwork(iface.interfaceName, binder)
            }
            networkFuture.get(setupTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Throwable) {
            cm.unregisterNetworkCallback(networkCallback)
            throw e
        }

        testIface = iface
    }

    fun teardown() {
        cm.unregisterNetworkCallback(networkCallback)
        tnm.teardownTestNetwork(network)
    }
}
