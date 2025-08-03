/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.connectivity.mdns

import android.content.Context
import android.content.res.Resources
import android.net.InetAddresses.parseNumericAddress
import android.net.LinkAddress
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.nsd.OffloadEngine
import android.net.nsd.OffloadServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.android.connectivity.resources.R
import com.android.net.module.util.SharedLog
import com.android.server.connectivity.ConnectivityResources
import com.android.server.connectivity.mdns.MdnsAdvertiser.AdvertiserCallback
import com.android.server.connectivity.mdns.MdnsInterfaceAdvertiser.CONFLICT_SERVICE
import com.android.server.connectivity.mdns.MdnsSocketProvider.SocketCallback
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.waitForIdle
import java.net.NetworkInterface
import java.time.Duration
import java.util.Objects
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.argThat
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

private const val SERVICE_ID_1 = 1
private const val SERVICE_ID_2 = 2
private const val SERVICE_ID_3 = 3
private const val LONG_SERVICE_ID_1 = 4
private const val LONG_SERVICE_ID_2 = 5
private const val CASE_INSENSITIVE_TEST_SERVICE_ID = 6
private const val TIMEOUT_MS = 10_000L
private val TEST_ADDR = parseNumericAddress("2001:db8::123")
private val TEST_ADDR2 = parseNumericAddress("2001:db8::124")
private val TEST_LINKADDR = LinkAddress(TEST_ADDR, 64 /* prefixLength */)
private val TEST_LINKADDR2 = LinkAddress(TEST_ADDR2, 64 /* prefixLength */)
private val TEST_NETWORK_1 = mock(Network::class.java)
private val TEST_SOCKETKEY_1 = SocketKey(1001 /* interfaceIndex */)
private val TEST_SOCKETKEY_2 = SocketKey(1002 /* interfaceIndex */)
private val TEST_HOSTNAME = arrayOf("Android_test", "local")
private const val TEST_SUBTYPE = "_subtype"
private const val TEST_SUBTYPE2 = "_subtype2"
private val TEST_INTERFACE1 = "test_iface1"
private val TEST_INTERFACE2 = "test_iface2"
private val TEST_CLIENT_UID_1 = 10010
private val TEST_OFFLOAD_PACKET1 = byteArrayOf(0x01, 0x02, 0x03)
private val TEST_OFFLOAD_PACKET2 = byteArrayOf(0x02, 0x03, 0x04)
private val DEFAULT_ADVERTISING_OPTION = MdnsAdvertisingOptions.getDefaultOptions()

private val SERVICE_1 = NsdServiceInfo("TestServiceName", "_advertisertest._tcp").apply {
    port = 12345
    hostAddresses = listOf(TEST_ADDR)
    network = TEST_NETWORK_1
}

private val SERVICE_1_SUBTYPE = NsdServiceInfo("TestServiceName", "_advertisertest._tcp").apply {
    subtypes = setOf(TEST_SUBTYPE)
    port = 12345
    hostAddresses = listOf(TEST_ADDR)
    network = TEST_NETWORK_1
}

private val LONG_SERVICE_1 =
    NsdServiceInfo("a".repeat(48) + "TestServiceName", "_longadvertisertest._tcp").apply {
    port = 12345
    hostAddresses = listOf(TEST_ADDR)
    network = TEST_NETWORK_1
    }

private val ALL_NETWORKS_SERVICE = NsdServiceInfo("TestServiceName", "_advertisertest._tcp").apply {
    port = 12345
    hostAddresses = listOf(TEST_ADDR)
    network = null
}

private val ALL_NETWORKS_SERVICE_SUBTYPE =
        NsdServiceInfo("TestServiceName", "_advertisertest._tcp").apply {
    subtypes = setOf(TEST_SUBTYPE)
    port = 12345
    hostAddresses = listOf(TEST_ADDR)
    network = null
}

private val ALL_NETWORKS_SERVICE_2 =
    NsdServiceInfo("TESTSERVICENAME", "_ADVERTISERTEST._tcp").apply {
        port = 12345
        hostAddresses = listOf(TEST_ADDR)
        network = null
    }

private val LONG_ALL_NETWORKS_SERVICE =
    NsdServiceInfo("a".repeat(48) + "TestServiceName", "_longadvertisertest._tcp").apply {
        port = 12345
        hostAddresses = listOf(TEST_ADDR)
        network = null
    }

private val OFFLOAD_SERVICEINFO = OffloadServiceInfo(
    OffloadServiceInfo.Key("TestServiceName", "_advertisertest._tcp"),
    listOf(TEST_SUBTYPE),
    "Android_test.local",
    TEST_OFFLOAD_PACKET1,
    0, /* priority */
    OffloadEngine.OFFLOAD_TYPE_REPLY.toLong()
)

private val OFFLOAD_SERVICEINFO_NO_SUBTYPE = OffloadServiceInfo(
    OffloadServiceInfo.Key("TestServiceName", "_advertisertest._tcp"),
    listOf(),
    "Android_test.local",
    TEST_OFFLOAD_PACKET1,
    0, /* priority */
    OffloadEngine.OFFLOAD_TYPE_REPLY.toLong()
)

private val OFFLOAD_SERVICEINFO_NO_SUBTYPE2 = OffloadServiceInfo(
    OffloadServiceInfo.Key("TestServiceName", "_advertisertest._tcp"),
    listOf(),
    "Android_test.local",
    TEST_OFFLOAD_PACKET2,
    0, /* priority */
    OffloadEngine.OFFLOAD_TYPE_REPLY.toLong()
)

private val SERVICES_PRIORITY_LIST = arrayOf(
    "0:_advertisertest._tcp",
    "5:_prioritytest._udp",
    "5:_otherprioritytest._tcp"
)

@RunWith(DevSdkIgnoreRunner::class)
@IgnoreUpTo(Build.VERSION_CODES.S_V2)
class MdnsAdvertiserTest {
    private val thread = HandlerThread(MdnsAdvertiserTest::class.simpleName)
    private val handler by lazy { Handler(thread.looper) }
    private val socketProvider = mock(MdnsSocketProvider::class.java)
    private val cb = mock(AdvertiserCallback::class.java)
    private val sharedlog = mock(SharedLog::class.java)

    private val mockSocket1 = mock(MdnsInterfaceSocket::class.java)
    private val mockSocket2 = mock(MdnsInterfaceSocket::class.java)
    private val mockInterfaceAdvertiser1 = mock(MdnsInterfaceAdvertiser::class.java)
    private val mockInterfaceAdvertiser2 = mock(MdnsInterfaceAdvertiser::class.java)
    private val mockDeps = mock(MdnsAdvertiser.Dependencies::class.java)
    private val context = mock(Context::class.java)
    private val resources = mock(Resources::class.java)
    private val flags = MdnsFeatureFlags.newBuilder().setIsMdnsOffloadFeatureEnabled(true).build()

    @Before
    fun setUp() {
        thread.start()
        doReturn(TEST_HOSTNAME).`when`(mockDeps).generateHostname()
        doReturn(mockInterfaceAdvertiser1).`when`(mockDeps).makeAdvertiser(eq(mockSocket1),
                any(), any(), any(), any(), eq(TEST_HOSTNAME), any(), any()
        )
        doReturn(mockInterfaceAdvertiser2).`when`(mockDeps).makeAdvertiser(eq(mockSocket2),
                any(), any(), any(), any(), eq(TEST_HOSTNAME), any(), any()
        )
        doReturn(true).`when`(mockInterfaceAdvertiser1).isProbing(anyInt())
        doReturn(true).`when`(mockInterfaceAdvertiser2).isProbing(anyInt())
        doReturn(createEmptyNetworkInterface()).`when`(mockSocket1).getInterface()
        doReturn(createEmptyNetworkInterface()).`when`(mockSocket2).getInterface()
        doReturn(TEST_INTERFACE1).`when`(mockInterfaceAdvertiser1).socketInterfaceName
        doReturn(TEST_INTERFACE2).`when`(mockInterfaceAdvertiser2).socketInterfaceName
        doReturn(TEST_OFFLOAD_PACKET1).`when`(mockInterfaceAdvertiser1).getRawOffloadPayload(
            SERVICE_ID_1)
        doReturn(TEST_OFFLOAD_PACKET1).`when`(mockInterfaceAdvertiser1).getRawOffloadPayload(
            SERVICE_ID_2)
        doReturn(TEST_OFFLOAD_PACKET1).`when`(mockInterfaceAdvertiser1).getRawOffloadPayload(
            SERVICE_ID_3)
        doReturn(TEST_OFFLOAD_PACKET1).`when`(mockInterfaceAdvertiser2).getRawOffloadPayload(
            SERVICE_ID_1)
        doReturn(resources).`when`(context).getResources()
        doReturn(SERVICES_PRIORITY_LIST).`when`(resources).getStringArray(
            R.array.config_nsdOffloadServicesPriority)
        ConnectivityResources.setResourcesContextForTest(context)
    }

    @After
    fun tearDown() {
        ConnectivityResources.setResourcesContextForTest(null)
        thread.quitSafely()
        thread.join()
    }

    private fun createEmptyNetworkInterface(): NetworkInterface {
        val constructor = NetworkInterface::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance()
    }

    @Test
    fun testAddService_OneNetwork() {
        val advertiser =
            MdnsAdvertiser(thread.looper, socketProvider, cb, mockDeps, sharedlog, flags, context)
        postSync { advertiser.addOrUpdateService(SERVICE_ID_1, SERVICE_1,
                DEFAULT_ADVERTISING_OPTION, TEST_CLIENT_UID_1) }

        val socketCbCaptor = ArgumentCaptor.forClass(SocketCallback::class.java)
        verify(socketProvider).requestSocket(eq(TEST_NETWORK_1), socketCbCaptor.capture())

        val socketCb = socketCbCaptor.value
        postSync { socketCb.onSocketCreated(TEST_SOCKETKEY_1, mockSocket1, listOf(TEST_LINKADDR)) }

        val intAdvCbCaptor = ArgumentCaptor.forClass(MdnsInterfaceAdvertiser.Callback::class.java)
        verify(mockDeps).makeAdvertiser(
            eq(mockSocket1),
            eq(listOf(TEST_LINKADDR)),
            eq(thread.looper),
            any(),
            intAdvCbCaptor.capture(),
            eq(TEST_HOSTNAME),
            any(),
            any()
        )

        doReturn(false).`when`(mockInterfaceAdvertiser1).isProbing(SERVICE_ID_1)
        postSync { intAdvCbCaptor.value.onServiceProbingSucceeded(
                mockInterfaceAdvertiser1, SERVICE_ID_1) }
        verify(cb).onRegisterServiceSucceeded(eq(SERVICE_ID_1), argThat { it.matches(SERVICE_1) })
        verify(cb).onOffloadStartOrUpdate(eq(TEST_INTERFACE1), eq(OFFLOAD_SERVICEINFO_NO_SUBTYPE))

        // Service is conflicted.
        postSync {
            intAdvCbCaptor.value
                    .onServiceConflict(mockInterfaceAdvertiser1, SERVICE_ID_1, CONFLICT_SERVICE)
        }

        // Verify the metrics data
        doReturn(25).`when`(mockInterfaceAdvertiser1).getServiceRepliedRequestsCount(SERVICE_ID_1)
        doReturn(40).`when`(mockInterfaceAdvertiser1).getSentPacketCount(SERVICE_ID_1)
        val metrics = postReturn { advertiser.getAdvertiserMetrics(SERVICE_ID_1) }
        assertEquals(25, metrics.mRepliedRequestsCount)
        assertEquals(40, metrics.mSentPacketCount)
        assertEquals(0, metrics.mConflictDuringProbingCount)
        assertEquals(1, metrics.mConflictAfterProbingCount)

        doReturn(TEST_OFFLOAD_PACKET2).`when`(mockInterfaceAdvertiser1)
            .getRawOffloadPayload(
                SERVICE_ID_1
            )
        postSync {
            socketCb.onAddressesChanged(
                TEST_SOCKETKEY_1,
                mockSocket1,
                listOf(TEST_LINKADDR2)
            )
        }
        verify(cb).onOffloadStartOrUpdate(eq(TEST_INTERFACE1), eq(OFFLOAD_SERVICEINFO_NO_SUBTYPE2))

        postSync { socketCb.onInterfaceDestroyed(TEST_SOCKETKEY_1, mockSocket1) }
        verify(mockInterfaceAdvertiser1).destroyNow()
        postSync { intAdvCbCaptor.value.onDestroyed(mockSocket1) }
        verify(cb).onOffloadStop(eq(TEST_INTERFACE1), eq(OFFLOAD_SERVICEINFO_NO_SUBTYPE2))
    }

    @Test
    fun testAddService_AllNetworksWithSubType() {
        val advertiser =
            MdnsAdvertiser(thread.looper, socketProvider, cb, mockDeps, sharedlog, flags, context)
        postSync { advertiser.addOrUpdateService(SERVICE_ID_1, ALL_NETWORKS_SERVICE_SUBTYPE,
                DEFAULT_ADVERTISING_OPTION, TEST_CLIENT_UID_1) }

        val socketCbCaptor = ArgumentCaptor.forClass(SocketCallback::class.java)
        verify(socketProvider).requestSocket(eq(ALL_NETWORKS_SERVICE_SUBTYPE.network),
                socketCbCaptor.capture())

        val socketCb = socketCbCaptor.value
        postSync { socketCb.onSocketCreated(TEST_SOCKETKEY_1, mockSocket1, listOf(TEST_LINKADDR)) }
        postSync { socketCb.onSocketCreated(TEST_SOCKETKEY_2, mockSocket2, listOf(TEST_LINKADDR)) }

        val intAdvCbCaptor1 = ArgumentCaptor.forClass(MdnsInterfaceAdvertiser.Callback::class.java)
        val intAdvCbCaptor2 = ArgumentCaptor.forClass(MdnsInterfaceAdvertiser.Callback::class.java)
        verify(mockDeps).makeAdvertiser(eq(mockSocket1), eq(listOf(TEST_LINKADDR)),
                eq(thread.looper), any(), intAdvCbCaptor1.capture(), eq(TEST_HOSTNAME), any(), any()
        )
        verify(mockDeps).makeAdvertiser(eq(mockSocket2), eq(listOf(TEST_LINKADDR)),
                eq(thread.looper), any(), intAdvCbCaptor2.capture(), eq(TEST_HOSTNAME), any(), any()
        )
        verify(mockInterfaceAdvertiser1).addService(
                anyInt(), eq(ALL_NETWORKS_SERVICE_SUBTYPE), any())
        verify(mockInterfaceAdvertiser2).addService(
                anyInt(), eq(ALL_NETWORKS_SERVICE_SUBTYPE), any())

        doReturn(false).`when`(mockInterfaceAdvertiser1).isProbing(SERVICE_ID_1)
        postSync { intAdvCbCaptor1.value.onServiceProbingSucceeded(
                mockInterfaceAdvertiser1, SERVICE_ID_1) }
        verify(cb).onOffloadStartOrUpdate(eq(TEST_INTERFACE1), eq(OFFLOAD_SERVICEINFO))

        // Need both advertisers to finish probing and call onRegisterServiceSucceeded
        verify(cb, never()).onRegisterServiceSucceeded(anyInt(), any())
        doReturn(false).`when`(mockInterfaceAdvertiser2).isProbing(SERVICE_ID_1)
        postSync { intAdvCbCaptor2.value.onServiceProbingSucceeded(
                mockInterfaceAdvertiser2, SERVICE_ID_1) }
        verify(cb).onOffloadStartOrUpdate(eq(TEST_INTERFACE2), eq(OFFLOAD_SERVICEINFO))
        verify(cb).onRegisterServiceSucceeded(eq(SERVICE_ID_1),
                argThat { it.matches(ALL_NETWORKS_SERVICE_SUBTYPE) })

        // Services are conflicted.
        postSync {
            intAdvCbCaptor1.value
                    .onServiceConflict(mockInterfaceAdvertiser1, SERVICE_ID_1, CONFLICT_SERVICE)
        }
        postSync {
            intAdvCbCaptor1.value
                    .onServiceConflict(mockInterfaceAdvertiser1, SERVICE_ID_1, CONFLICT_SERVICE)
        }
        postSync {
            intAdvCbCaptor2.value
                    .onServiceConflict(mockInterfaceAdvertiser2, SERVICE_ID_1, CONFLICT_SERVICE)
        }

        // Verify the metrics data
        doReturn(10).`when`(mockInterfaceAdvertiser1).getServiceRepliedRequestsCount(SERVICE_ID_1)
        doReturn(5).`when`(mockInterfaceAdvertiser2).getServiceRepliedRequestsCount(SERVICE_ID_1)
        doReturn(22).`when`(mockInterfaceAdvertiser1).getSentPacketCount(SERVICE_ID_1)
        doReturn(12).`when`(mockInterfaceAdvertiser2).getSentPacketCount(SERVICE_ID_1)
        val metrics = postReturn { advertiser.getAdvertiserMetrics(SERVICE_ID_1) }
        assertEquals(15, metrics.mRepliedRequestsCount)
        assertEquals(34, metrics.mSentPacketCount)
        assertEquals(2, metrics.mConflictDuringProbingCount)
        assertEquals(1, metrics.mConflictAfterProbingCount)

        // Unregister the service
        postSync { advertiser.removeService(SERVICE_ID_1) }
        verify(mockInterfaceAdvertiser1).removeService(SERVICE_ID_1)
        verify(mockInterfaceAdvertiser2).removeService(SERVICE_ID_1)
        verify(cb).onOffloadStop(eq(TEST_INTERFACE1), eq(OFFLOAD_SERVICEINFO))
        verify(cb).onOffloadStop(eq(TEST_INTERFACE2), eq(OFFLOAD_SERVICEINFO))

        // Interface advertisers call onDestroyed after sending exit announcements
        postSync { intAdvCbCaptor1.value.onDestroyed(mockSocket1) }
        verify(socketProvider, never()).unrequestSocket(any())
        postSync { intAdvCbCaptor2.value.onDestroyed(mockSocket2) }
        verify(socketProvider).unrequestSocket(socketCb)
    }

    @Test
    fun testAddService_OffloadPriority() {
        val advertiser =
            MdnsAdvertiser(thread.looper, socketProvider, cb, mockDeps, sharedlog, flags, context)
        postSync {
            advertiser.addOrUpdateService(SERVICE_ID_1, SERVICE_1, DEFAULT_ADVERTISING_OPTION,
                    TEST_CLIENT_UID_1)
            advertiser.addOrUpdateService(SERVICE_ID_2,
                NsdServiceInfo("TestService2", "_PRIORITYTEST._udp").apply {
                    port = 12345
                    hostAddresses = listOf(TEST_ADDR)
                }, DEFAULT_ADVERTISING_OPTION, TEST_CLIENT_UID_1)
            advertiser.addOrUpdateService(
                SERVICE_ID_3,
                NsdServiceInfo("TestService3", "_notprioritized._tcp").apply {
                    port = 12345
                    hostAddresses = listOf(TEST_ADDR)
                }, DEFAULT_ADVERTISING_OPTION, TEST_CLIENT_UID_1)
        }

        val socketCbCaptor = ArgumentCaptor.forClass(SocketCallback::class.java)
        verify(socketProvider).requestSocket(eq(SERVICE_1.network), socketCbCaptor.capture())

        val socketCb = socketCbCaptor.value
        postSync { socketCb.onSocketCreated(TEST_SOCKETKEY_1, mockSocket1, listOf(TEST_LINKADDR)) }

        val intAdvCbCaptor1 = ArgumentCaptor.forClass(MdnsInterfaceAdvertiser.Callback::class.java)
        verify(mockDeps).makeAdvertiser(eq(mockSocket1), eq(listOf(TEST_LINKADDR)),
            eq(thread.looper), any(), intAdvCbCaptor1.capture(), eq(TEST_HOSTNAME), any(), any()
        )

        doReturn(false).`when`(mockInterfaceAdvertiser1).isProbing(SERVICE_ID_1)
        doReturn(false).`when`(mockInterfaceAdvertiser1).isProbing(SERVICE_ID_2)
        doReturn(false).`when`(mockInterfaceAdvertiser1).isProbing(SERVICE_ID_3)
        postSync {
            intAdvCbCaptor1.value.onServiceProbingSucceeded(mockInterfaceAdvertiser1, SERVICE_ID_1)
            intAdvCbCaptor1.value.onServiceProbingSucceeded(mockInterfaceAdvertiser1, SERVICE_ID_2)
            intAdvCbCaptor1.value.onServiceProbingSucceeded(mockInterfaceAdvertiser1, SERVICE_ID_3)
        }

        verify(cb).onOffloadStartOrUpdate(eq(TEST_INTERFACE1), eq(OFFLOAD_SERVICEINFO_NO_SUBTYPE))
        verify(cb).onOffloadStartOrUpdate(eq(TEST_INTERFACE1), eq(OffloadServiceInfo(
            OffloadServiceInfo.Key("TestService2", "_PRIORITYTEST._udp"),
            emptyList() /* subtypes */,
            "Android_test.local",
            TEST_OFFLOAD_PACKET1,
            5, /* priority */
            OffloadEngine.OFFLOAD_TYPE_REPLY.toLong()
        )))
        verify(cb).onOffloadStartOrUpdate(eq(TEST_INTERFACE1), eq(OffloadServiceInfo(
            OffloadServiceInfo.Key("TestService3", "_notprioritized._tcp"),
            emptyList() /* subtypes */,
            "Android_test.local",
            TEST_OFFLOAD_PACKET1,
            Integer.MAX_VALUE, /* priority */
            OffloadEngine.OFFLOAD_TYPE_REPLY.toLong()
        )))
    }

    @Test
    fun testAddService_Conflicts() {
        val advertiser =
            MdnsAdvertiser(thread.looper, socketProvider, cb, mockDeps, sharedlog, flags, context)
        postSync { advertiser.addOrUpdateService(SERVICE_ID_1, SERVICE_1,
                DEFAULT_ADVERTISING_OPTION, TEST_CLIENT_UID_1) }

        val oneNetSocketCbCaptor = ArgumentCaptor.forClass(SocketCallback::class.java)
        verify(socketProvider).requestSocket(eq(TEST_NETWORK_1), oneNetSocketCbCaptor.capture())
        val oneNetSocketCb = oneNetSocketCbCaptor.value

        // Register a service with the same name on all networks (name conflict)
        postSync { advertiser.addOrUpdateService(SERVICE_ID_2, ALL_NETWORKS_SERVICE,
                DEFAULT_ADVERTISING_OPTION, TEST_CLIENT_UID_1) }
        val allNetSocketCbCaptor = ArgumentCaptor.forClass(SocketCallback::class.java)
        verify(socketProvider).requestSocket(eq(null), allNetSocketCbCaptor.capture())
        val allNetSocketCb = allNetSocketCbCaptor.value

        postSync { advertiser.addOrUpdateService(LONG_SERVICE_ID_1, LONG_SERVICE_1,
                DEFAULT_ADVERTISING_OPTION, TEST_CLIENT_UID_1) }
        postSync { advertiser.addOrUpdateService(LONG_SERVICE_ID_2, LONG_ALL_NETWORKS_SERVICE,
                DEFAULT_ADVERTISING_OPTION, TEST_CLIENT_UID_1) }

        postSync { advertiser.addOrUpdateService(CASE_INSENSITIVE_TEST_SERVICE_ID,
                ALL_NETWORKS_SERVICE_2, DEFAULT_ADVERTISING_OPTION, TEST_CLIENT_UID_1) }

        // Callbacks for matching network and all networks both get the socket
        postSync {
            oneNetSocketCb.onSocketCreated(TEST_SOCKETKEY_1, mockSocket1, listOf(TEST_LINKADDR))
            allNetSocketCb.onSocketCreated(TEST_SOCKETKEY_1, mockSocket1, listOf(TEST_LINKADDR))
        }

        val expectedRenamed = NsdServiceInfo(
                "${ALL_NETWORKS_SERVICE.serviceName} (2)", ALL_NETWORKS_SERVICE.serviceType).apply {
            port = ALL_NETWORKS_SERVICE.port
            hostAddresses = ALL_NETWORKS_SERVICE.hostAddresses
            network = ALL_NETWORKS_SERVICE.network
        }

        val expectedLongRenamed = NsdServiceInfo(
            "${LONG_ALL_NETWORKS_SERVICE.serviceName.dropLast(4)} (2)",
            LONG_ALL_NETWORKS_SERVICE.serviceType).apply {
            port = LONG_ALL_NETWORKS_SERVICE.port
            hostAddresses = LONG_ALL_NETWORKS_SERVICE.hostAddresses
            network = LONG_ALL_NETWORKS_SERVICE.network
        }

        val expectedCaseInsensitiveRenamed = NsdServiceInfo(
            "${ALL_NETWORKS_SERVICE_2.serviceName} (3)", ALL_NETWORKS_SERVICE_2.serviceType
        ).apply {
            port = ALL_NETWORKS_SERVICE_2.port
            hostAddresses = ALL_NETWORKS_SERVICE_2.hostAddresses
            network = ALL_NETWORKS_SERVICE_2.network
        }

        val intAdvCbCaptor = ArgumentCaptor.forClass(MdnsInterfaceAdvertiser.Callback::class.java)
        verify(mockDeps).makeAdvertiser(eq(mockSocket1), eq(listOf(TEST_LINKADDR)),
                eq(thread.looper), any(), intAdvCbCaptor.capture(), eq(TEST_HOSTNAME), any(), any()
        )
        verify(mockInterfaceAdvertiser1).addService(eq(SERVICE_ID_1),
                argThat { it.matches(SERVICE_1) }, any())
        verify(mockInterfaceAdvertiser1).addService(eq(SERVICE_ID_2),
                argThat { it.matches(expectedRenamed) }, any())
        verify(mockInterfaceAdvertiser1).addService(eq(LONG_SERVICE_ID_1),
                argThat { it.matches(LONG_SERVICE_1) }, any())
        verify(mockInterfaceAdvertiser1).addService(eq(LONG_SERVICE_ID_2),
            argThat { it.matches(expectedLongRenamed) }, any())
        verify(mockInterfaceAdvertiser1).addService(eq(CASE_INSENSITIVE_TEST_SERVICE_ID),
            argThat { it.matches(expectedCaseInsensitiveRenamed) }, any())

        doReturn(false).`when`(mockInterfaceAdvertiser1).isProbing(SERVICE_ID_1)
        postSync { intAdvCbCaptor.value.onServiceProbingSucceeded(
                mockInterfaceAdvertiser1, SERVICE_ID_1) }
        verify(cb).onRegisterServiceSucceeded(eq(SERVICE_ID_1), argThat { it.matches(SERVICE_1) })

        doReturn(false).`when`(mockInterfaceAdvertiser1).isProbing(SERVICE_ID_2)
        postSync { intAdvCbCaptor.value.onServiceProbingSucceeded(
                mockInterfaceAdvertiser1, SERVICE_ID_2) }
        verify(cb).onRegisterServiceSucceeded(eq(SERVICE_ID_2),
                argThat { it.matches(expectedRenamed) })

        postSync { oneNetSocketCb.onInterfaceDestroyed(TEST_SOCKETKEY_1, mockSocket1) }
        postSync { allNetSocketCb.onInterfaceDestroyed(TEST_SOCKETKEY_1, mockSocket1) }

        // destroyNow can be called multiple times
        verify(mockInterfaceAdvertiser1, atLeastOnce()).destroyNow()
    }

    @Test
    fun testAddOrUpdateService_Updates() {
        val advertiser =
                MdnsAdvertiser(thread.looper, socketProvider, cb, mockDeps, sharedlog, flags,
                    context)
        postSync { advertiser.addOrUpdateService(SERVICE_ID_1, ALL_NETWORKS_SERVICE,
                DEFAULT_ADVERTISING_OPTION, TEST_CLIENT_UID_1) }

        val socketCbCaptor = ArgumentCaptor.forClass(SocketCallback::class.java)
        verify(socketProvider).requestSocket(eq(null), socketCbCaptor.capture())

        val socketCb = socketCbCaptor.value
        postSync { socketCb.onSocketCreated(TEST_SOCKETKEY_1, mockSocket1, listOf(TEST_LINKADDR)) }

        verify(mockInterfaceAdvertiser1).addService(eq(SERVICE_ID_1),
                argThat { it.matches(ALL_NETWORKS_SERVICE) }, any())

        val updateOptions = MdnsAdvertisingOptions.newBuilder().setIsOnlyUpdate(true).build()

        // Update with serviceId that is not registered yet should fail
        postSync { advertiser.addOrUpdateService(SERVICE_ID_2, ALL_NETWORKS_SERVICE_SUBTYPE,
                updateOptions, TEST_CLIENT_UID_1) }
        verify(cb).onRegisterServiceFailed(SERVICE_ID_2, NsdManager.FAILURE_INTERNAL_ERROR)

        // Update service with different NsdServiceInfo should fail
        postSync { advertiser.addOrUpdateService(SERVICE_ID_1, SERVICE_1_SUBTYPE, updateOptions,
                TEST_CLIENT_UID_1) }
        verify(cb).onRegisterServiceFailed(SERVICE_ID_1, NsdManager.FAILURE_INTERNAL_ERROR)

        // Update service with same NsdServiceInfo but different subType should succeed
        postSync { advertiser.addOrUpdateService(SERVICE_ID_1, ALL_NETWORKS_SERVICE_SUBTYPE,
                updateOptions, TEST_CLIENT_UID_1) }
        verify(mockInterfaceAdvertiser1).updateService(eq(SERVICE_ID_1), eq(setOf(TEST_SUBTYPE)))

        // Newly created MdnsInterfaceAdvertiser will get addService() call.
        postSync { socketCb.onSocketCreated(TEST_SOCKETKEY_2, mockSocket2, listOf(TEST_LINKADDR2)) }
        verify(mockInterfaceAdvertiser2).addService(eq(SERVICE_ID_1),
                argThat { it.matches(ALL_NETWORKS_SERVICE_SUBTYPE) }, any())
    }

    @Test
    fun testAddOrUpdateService_customTtl_registeredSuccess() {
        val advertiser = MdnsAdvertiser(
                thread.looper, socketProvider, cb, mockDeps, sharedlog, flags, context)
        val updateOptions =
                MdnsAdvertisingOptions.newBuilder().setTtl(Duration.ofSeconds(30)).build()

        postSync { advertiser.addOrUpdateService(SERVICE_ID_1, ALL_NETWORKS_SERVICE,
                updateOptions, TEST_CLIENT_UID_1) }

        val socketCbCaptor = ArgumentCaptor.forClass(SocketCallback::class.java)
        verify(socketProvider).requestSocket(eq(null), socketCbCaptor.capture())
        val socketCb = socketCbCaptor.value
        postSync { socketCb.onSocketCreated(TEST_SOCKETKEY_1, mockSocket1, listOf(TEST_LINKADDR)) }
        verify(mockInterfaceAdvertiser1).addService(eq(SERVICE_ID_1), any(), eq(updateOptions))
    }

    @Test
    fun testRemoveService_whenAllServiceRemoved_thenUpdateHostName() {
        val advertiser =
            MdnsAdvertiser(thread.looper, socketProvider, cb, mockDeps, sharedlog, flags, context)
        verify(mockDeps, times(1)).generateHostname()
        postSync { advertiser.addOrUpdateService(SERVICE_ID_1, SERVICE_1,
                DEFAULT_ADVERTISING_OPTION, TEST_CLIENT_UID_1) }
        postSync { advertiser.removeService(SERVICE_ID_1) }
        verify(mockDeps, times(2)).generateHostname()
    }

    private fun postSync(r: () -> Unit) {
        handler.post(r)
        handler.waitForIdle(TIMEOUT_MS)
    }

    private fun <T> postReturn(r: (() -> T)): T {
        val future = CompletableFuture<T>()
        handler.post {
            future.complete(r())
        }
        return future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }
}

// NsdServiceInfo does not implement equals; this is useful to use in argument matchers
private fun NsdServiceInfo.matches(other: NsdServiceInfo): Boolean {
    return Objects.equals(serviceName, other.serviceName) &&
            Objects.equals(serviceType, other.serviceType) &&
            Objects.equals(attributes, other.attributes) &&
            Objects.equals(hostAddresses, other.hostAddresses) &&
            port == other.port &&
            Objects.equals(network, other.network)
}
