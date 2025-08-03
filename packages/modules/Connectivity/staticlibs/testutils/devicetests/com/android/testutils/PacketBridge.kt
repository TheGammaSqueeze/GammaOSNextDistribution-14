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

package com.android.testutils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TestNetworkInterface
import android.net.TestNetworkManager
import android.net.TestNetworkSpecifier
import android.os.Binder
import com.android.testutils.RecorderCallback.CallbackEntry.Available
import java.net.InetAddress
import libcore.io.IoUtils

/**
 * A class that set up two {@link TestNetworkInterface}, and forward packets between them.
 *
 * See {@link PacketForwarder} for more detailed information.
 */
class PacketBridge(
    context: Context,
    addresses: List<LinkAddress>,
    dnsAddr: InetAddress,
    portMapping: List<Pair<Int, Int>>
) {
    private val binder = Binder()

    private val cm = context.getSystemService(ConnectivityManager::class.java)!!
    private val tnm = context.getSystemService(TestNetworkManager::class.java)!!

    // Create test networks. The needed permissions should be supplied by the callers.
    @SuppressLint("MissingPermission")
    private val internalIface = tnm.createTunInterface(addresses)
    @SuppressLint("MissingPermission")
    private val externalIface = tnm.createTunInterface(addresses)

    // Register test networks to ConnectivityService.
    private val internalNetworkCallback: TestableNetworkCallback
    private val externalNetworkCallback: TestableNetworkCallback

    private val internalForwardMap = HashMap<Int, Int>()
    private val externalForwardMap = HashMap<Int, Int>()

    val internalNetwork: Network
    val externalNetwork: Network
    init {
        val (inCb, inNet) = createTestNetwork(internalIface, addresses, dnsAddr)
        val (exCb, exNet) = createTestNetwork(externalIface, addresses, dnsAddr)
        internalNetworkCallback = inCb
        externalNetworkCallback = exCb
        internalNetwork = inNet
        externalNetwork = exNet
        for (mapping in portMapping) {
            internalForwardMap[mapping.first] = mapping.second
            externalForwardMap[mapping.second] = mapping.first
        }
    }

    // Set up the packet bridge.
    private val internalFd = internalIface.fileDescriptor.fileDescriptor
    private val externalFd = externalIface.fileDescriptor.fileDescriptor

    private val pr1 = InternalPacketForwarder(
        internalFd,
        1500,
        externalFd,
        internalForwardMap
    )
    private val pr2 = ExternalPacketForwarder(
        externalFd,
        1500,
        internalFd,
        externalForwardMap
    )

    fun start() {
        IoUtils.setBlocking(internalFd, true /* blocking */)
        IoUtils.setBlocking(externalFd, true /* blocking */)
        pr1.start()
        pr2.start()
    }

    fun stop() {
        pr1.interrupt()
        pr2.interrupt()
        cm.unregisterNetworkCallback(internalNetworkCallback)
        cm.unregisterNetworkCallback(externalNetworkCallback)
    }

    /**
     * Creates a test network with given test TUN interface and addresses.
     */
    private fun createTestNetwork(
        testIface: TestNetworkInterface,
        addresses: List<LinkAddress>,
        dnsAddr: InetAddress
    ): Pair<TestableNetworkCallback, Network> {
        // Make a network request to hold the test network
        val nr = NetworkRequest.Builder()
            .clearCapabilities()
            .addTransportType(NetworkCapabilities.TRANSPORT_TEST)
            .setNetworkSpecifier(TestNetworkSpecifier(testIface.interfaceName))
            .build()
        val testCb = TestableNetworkCallback()
        cm.requestNetwork(nr, testCb)

        val lp = LinkProperties().apply {
            setLinkAddresses(addresses)
            interfaceName = testIface.interfaceName
            addDnsServer(dnsAddr)
        }
        tnm.setupTestNetwork(lp, true /* isMetered */, binder)

        // Wait for available before return.
        val network = testCb.expect<Available>().network
        return testCb to network
    }
}
