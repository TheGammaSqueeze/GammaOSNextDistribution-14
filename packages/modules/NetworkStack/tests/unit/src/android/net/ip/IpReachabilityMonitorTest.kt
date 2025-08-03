/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.net.ip

import android.annotation.SuppressLint
import android.content.Context
import android.net.INetd
import android.net.InetAddresses.parseNumericAddress
import android.net.IpPrefix
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.RouteInfo
import android.net.metrics.IpConnectivityLog
import android.os.Handler
import android.os.HandlerThread
import android.os.MessageQueue
import android.os.MessageQueue.OnFileDescriptorEventListener
import android.stats.connectivity.IpType
import android.stats.connectivity.IpType.IPV4
import android.stats.connectivity.IpType.IPV6
import android.stats.connectivity.NudEventType
import android.stats.connectivity.NudEventType.NUD_CONFIRM_FAILED
import android.stats.connectivity.NudEventType.NUD_CONFIRM_FAILED_CRITICAL
import android.stats.connectivity.NudEventType.NUD_ORGANIC_FAILED
import android.stats.connectivity.NudEventType.NUD_ORGANIC_FAILED_CRITICAL
import android.stats.connectivity.NudEventType.NUD_POST_ROAMING_FAILED
import android.stats.connectivity.NudEventType.NUD_POST_ROAMING_FAILED_CRITICAL
import android.stats.connectivity.NudEventType.NUD_POST_ROAMING_MAC_ADDRESS_CHANGED
import android.stats.connectivity.NudNeighborType
import android.stats.connectivity.NudNeighborType.NUD_NEIGHBOR_BOTH
import android.stats.connectivity.NudNeighborType.NUD_NEIGHBOR_DNS
import android.stats.connectivity.NudNeighborType.NUD_NEIGHBOR_GATEWAY
import android.system.ErrnoException
import android.system.OsConstants.EAGAIN
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.net.module.util.InterfaceParams
import com.android.net.module.util.SharedLog
import com.android.net.module.util.ip.IpNeighborMonitor
import com.android.net.module.util.netlink.StructNdMsg.NUD_FAILED
import com.android.net.module.util.netlink.StructNdMsg.NUD_PROBE
import com.android.net.module.util.netlink.StructNdMsg.NUD_REACHABLE
import com.android.net.module.util.netlink.StructNdMsg.NUD_STALE
import com.android.networkstack.metrics.IpReachabilityMonitorMetrics
import com.android.networkstack.util.NetworkStackUtils.IP_REACHABILITY_IGNORE_ORGANIC_NUD_FAILURE_VERSION
import com.android.networkstack.util.NetworkStackUtils.IP_REACHABILITY_MCAST_RESOLICIT_VERSION
import com.android.networkstack.util.NetworkStackUtils.IP_REACHABILITY_ROUTER_MAC_CHANGE_FAILURE_ONLY_AFTER_ROAM_VERSION
import com.android.testutils.makeNewNeighMessage
import com.android.testutils.waitForIdle
import java.io.FileDescriptor
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify

private const val TEST_TIMEOUT_MS = 10_000L

private val TEST_IPV4_GATEWAY = parseNumericAddress("192.168.222.3") as Inet4Address
private val TEST_IPV6_GATEWAY = parseNumericAddress("2001:db8::1") as Inet6Address

private val TEST_MAC_1 = "001122334455"
private val TEST_MAC_2 = "1122334455aa"

// IPv4 gateway is also DNS server.
private val TEST_IPV4_GATEWAY_DNS = parseNumericAddress("192.168.222.100") as Inet4Address

private val TEST_IPV4_LINKADDR = LinkAddress("192.168.222.123/24")
private val TEST_IPV6_LINKADDR = LinkAddress("2001:db8::123/64")

private val TEST_IPV6_LINKLOCAL_LINKADDR = LinkAddress("fe80::123/64")
private val TEST_IPV6_LINKLOCAL_GATEWAY = parseNumericAddress("fe80::1") as Inet6Address
private val TEST_IPV6_LINKLOCAL_SCOPED_GATEWAY = parseNumericAddress("fe80::1%21") as Inet6Address

// DNSes inside IP prefix
private val TEST_IPV4_DNS = parseNumericAddress("192.168.222.1") as Inet4Address
private val TEST_IPV6_DNS = parseNumericAddress("2001:db8::321") as Inet6Address
private val TEST_IPV6_DNS2 = parseNumericAddress("2001:db8::456") as Inet6Address

private val TEST_IFACE = InterfaceParams("fake0", 21, null)

@SuppressLint("NewApi")
private val TEST_LINK_PROPERTIES = LinkProperties().apply {
    interfaceName = TEST_IFACE.name
    addLinkAddress(TEST_IPV4_LINKADDR)
    addLinkAddress(TEST_IPV6_LINKADDR)

    // Add on link routes
    addRoute(RouteInfo(TEST_IPV4_LINKADDR, null /* gateway */, TEST_IFACE.name))
    addRoute(RouteInfo(TEST_IPV6_LINKADDR, null /* gateway */, TEST_IFACE.name))

    // Add default routes
    addRoute(RouteInfo(IpPrefix(parseNumericAddress("0.0.0.0"), 0), TEST_IPV4_GATEWAY))
    addRoute(RouteInfo(IpPrefix(parseNumericAddress("::"), 0), TEST_IPV6_GATEWAY))

    addDnsServer(TEST_IPV4_DNS)
    addDnsServer(TEST_IPV6_DNS)
}

@SuppressLint("NewApi")
private val TEST_IPV4_ONLY_LINK_PROPERTIES = LinkProperties().apply {
    interfaceName = TEST_IFACE.name
    addLinkAddress(TEST_IPV4_LINKADDR)

    // Add on link routes
    addRoute(RouteInfo(TEST_IPV4_LINKADDR, null /* gateway */, TEST_IFACE.name))

    // Add default routes
    addRoute(RouteInfo(IpPrefix(parseNumericAddress("0.0.0.0"), 0), TEST_IPV4_GATEWAY_DNS))

    addDnsServer(TEST_IPV4_GATEWAY_DNS)
}

@SuppressLint("NewApi")
private val TEST_IPV6_LINKLOCAL_SCOPED_LINK_PROPERTIES = LinkProperties().apply {
    interfaceName = TEST_IFACE.name
    addLinkAddress(TEST_IPV6_LINKADDR)
    addLinkAddress(TEST_IPV6_LINKLOCAL_LINKADDR)

    // Add on link routes
    addRoute(RouteInfo(TEST_IPV6_LINKADDR, null /* gateway */, TEST_IFACE.name))
    addRoute(RouteInfo(TEST_IPV6_LINKLOCAL_LINKADDR, null /* gateway */, TEST_IFACE.name))

    // Add default routes
    addRoute(RouteInfo(IpPrefix(parseNumericAddress("::"), 0), TEST_IPV6_LINKLOCAL_SCOPED_GATEWAY))

    addDnsServer(TEST_IPV6_DNS)
}

@SuppressLint("NewApi")
private val TEST_DUAL_LINK_PROPERTIES = LinkProperties().apply {
    interfaceName = TEST_IFACE.name
    addLinkAddress(TEST_IPV4_LINKADDR)
    addLinkAddress(TEST_IPV6_LINKADDR)
    addLinkAddress(TEST_IPV6_LINKLOCAL_LINKADDR)

    // Add on link routes
    addRoute(RouteInfo(TEST_IPV4_LINKADDR, null /* gateway */, TEST_IFACE.name))
    addRoute(RouteInfo(TEST_IPV6_LINKADDR, null /* gateway */, TEST_IFACE.name))
    addRoute(RouteInfo(TEST_IPV6_LINKLOCAL_LINKADDR, null /* gateway */, TEST_IFACE.name))

    // Add default routes
    addRoute(RouteInfo(IpPrefix(parseNumericAddress("0.0.0.0"), 0), TEST_IPV4_GATEWAY))
    addRoute(RouteInfo(IpPrefix(parseNumericAddress("::"), 0), TEST_IPV6_LINKLOCAL_SCOPED_GATEWAY))

    addDnsServer(TEST_IPV4_DNS)
    addDnsServer(TEST_IPV6_DNS)
    addDnsServer(TEST_IPV6_DNS2)
}

/**
 * Tests for IpReachabilityMonitor.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class IpReachabilityMonitorTest {
    @get:Rule val mTestName = TestName()
    private val callback = mock(IpReachabilityMonitor.Callback::class.java)
    private val dependencies = mock(IpReachabilityMonitor.Dependencies::class.java)
    private val log = mock(SharedLog::class.java)
    private val context = mock(Context::class.java)
    private val netd = mock(INetd::class.java)
    private val fd = mock(FileDescriptor::class.java)
    private val metricsLog = mock(IpConnectivityLog::class.java)
    private val mIpReachabilityMonitorMetrics = mock(IpReachabilityMonitorMetrics::class.java)

    private val handlerThread = HandlerThread(IpReachabilityMonitorTest::class.simpleName)
    private val handler by lazy { Handler(handlerThread.looper) }

    private lateinit var reachabilityMonitor: IpReachabilityMonitor
    private lateinit var neighborMonitor: TestIpNeighborMonitor

    /**
     * A version of [IpNeighborMonitor] that overrides packet reading from a socket, and instead
     * allows the test to enqueue test packets via [enqueuePacket].
     */
    private class TestIpNeighborMonitor(
        handler: Handler,
        log: SharedLog,
        cb: NeighborEventConsumer,
        private val fd: FileDescriptor
    ) : IpNeighborMonitor(handler, log, cb) {

        private val pendingPackets = ConcurrentLinkedQueue<ByteArray>()
        val msgQueue = mock(MessageQueue::class.java)

        private var eventListener: OnFileDescriptorEventListener? = null

        override fun createFd() = fd
        override fun getMessageQueue() = msgQueue

        fun enqueuePacket(packet: ByteArray) {
            val listener = eventListener ?: fail("IpNeighborMonitor was not yet started")
            pendingPackets.add(packet)
            handler.post {
                listener.onFileDescriptorEvents(fd, OnFileDescriptorEventListener.EVENT_INPUT)
            }
        }

        override fun readPacket(fd: FileDescriptor, packetBuffer: ByteArray): Int {
            val packet = pendingPackets.poll() ?: throw ErrnoException("No pending packet", EAGAIN)
            if (packet.size > packetBuffer.size) {
                fail("Buffer (${packetBuffer.size}) is too small for packet (${packet.size})")
            }
            System.arraycopy(packet, 0, packetBuffer, 0, packet.size)
            return packet.size
        }

        override fun onStart() {
            super.onStart()

            // Find the file descriptor listener that was registered on the instrumented queue
            val captor = ArgumentCaptor.forClass(OnFileDescriptorEventListener::class.java)
            verify(msgQueue).addOnFileDescriptorEventListener(
                    eq(fd), anyInt(), captor.capture())
            eventListener = captor.value
        }
    }

    @Before
    fun setUp() {
        doReturn(log).`when`(log).forSubComponent(anyString())
        doReturn(true).`when`(fd).valid()
        handlerThread.start()

        doAnswer { inv ->
            val handler = inv.getArgument<Handler>(0)
            val log = inv.getArgument<SharedLog>(1)
            val cb = inv.getArgument<IpNeighborMonitor.NeighborEventConsumer>(2)
            neighborMonitor = TestIpNeighborMonitor(handler, log, cb, fd)
            neighborMonitor
        }.`when`(dependencies).makeIpNeighborMonitor(any(), any(), any())
        doReturn(mIpReachabilityMonitorMetrics)
                .`when`(dependencies).getIpReachabilityMonitorMetrics()
        doReturn(true).`when`(dependencies).isFeatureNotChickenedOut(any(),
                eq(IP_REACHABILITY_MCAST_RESOLICIT_VERSION))

        // TODO: test with non-default flag combinations.
        // Note: because dependencies is a mock, all features that are not specified here are
        // neither enabled nor chickened out.
        doReturn(true).`when`(dependencies).isFeatureNotChickenedOut(any(),
            eq(IP_REACHABILITY_ROUTER_MAC_CHANGE_FAILURE_ONLY_AFTER_ROAM_VERSION))

        val ignoreOrganicNudFailureTestList = listOf(
                "testLoseProvisioning_ignoreOrganicIpv4DnsLost",
                "testLoseProvisioning_ignoreOrganicIpv6DnsLost",
                "testLoseProvisioning_ignoreOrganicIpv4GatewayLost",
                "testLoseProvisioning_ignoreOrganicIpv6GatewayLost")
        // The experiment flag: IP_REACHABILITY_IGNORE_ORGANIC_NUD_FAILURE_VERSION is read at
        // the IpReachabilityMonitor constructor, so we have to set the value before initializing
        // an IpReachabilityMonitor instance.
        if (ignoreOrganicNudFailureTestList.contains(mTestName.methodName)) {
            doReturn(true).`when`(dependencies).isFeatureEnabled(any(),
                    eq(IP_REACHABILITY_IGNORE_ORGANIC_NUD_FAILURE_VERSION))
        }

        val monitorFuture = CompletableFuture<IpReachabilityMonitor>()
        // IpReachabilityMonitor needs to be started from the handler thread
        handler.post {
            monitorFuture.complete(IpReachabilityMonitor(
                    context,
                    TEST_IFACE,
                    handler,
                    log,
                    callback,
                    false /* useMultinetworkPolicyTracker */,
                    dependencies,
                    metricsLog,
                    netd))
        }
        reachabilityMonitor = monitorFuture.get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        assertTrue(::neighborMonitor.isInitialized,
                "IpReachabilityMonitor did not call makeIpNeighborMonitor")
    }

    @After
    fun tearDown() {
        // Ensure the handler thread is not accessing the fd while changing its mock
        handlerThread.waitForIdle(TEST_TIMEOUT_MS)
        doReturn(false).`when`(fd).valid()
        handlerThread.quitSafely()
    }

    @Test
    fun testLoseProvisioning_FirstProbeIsFailed() {
        reachabilityMonitor.updateLinkProperties(TEST_LINK_PROPERTIES)

        neighborMonitor.enqueuePacket(makeNewNeighMessage(TEST_IPV4_DNS, NUD_FAILED))
        verify(callback, timeout(TEST_TIMEOUT_MS)).notifyLost(eq(TEST_IPV4_DNS), anyString(),
                eq(NUD_ORGANIC_FAILED_CRITICAL))
    }

    private fun runLoseProvisioningTest(
        newLp: LinkProperties,
        lostNeighbor: InetAddress,
        eventType: NudEventType
    ) {
        runLoseProvisioningTest(newLp, lostNeighbor, eventType, true /* expectedNotifyLost */)
    }

    private fun runLoseProvisioningTest(
        newLp: LinkProperties,
        lostNeighbor: InetAddress,
        eventType: NudEventType,
        expectedNotifyLost: Boolean
    ) {
        reachabilityMonitor.updateLinkProperties(newLp)

        neighborMonitor.enqueuePacket(makeNewNeighMessage(TEST_IPV4_GATEWAY, NUD_STALE))
        neighborMonitor.enqueuePacket(makeNewNeighMessage(TEST_IPV6_GATEWAY, NUD_STALE))
        neighborMonitor.enqueuePacket(makeNewNeighMessage(TEST_IPV4_DNS, NUD_STALE))
        neighborMonitor.enqueuePacket(makeNewNeighMessage(TEST_IPV6_DNS, NUD_STALE))

        neighborMonitor.enqueuePacket(makeNewNeighMessage(lostNeighbor, NUD_FAILED))
        handlerThread.waitForIdle(TEST_TIMEOUT_MS)

        if (expectedNotifyLost) {
            verify(callback, timeout(TEST_TIMEOUT_MS)).notifyLost(eq(lostNeighbor), anyString(),
                    eq(eventType))
        } else {
             verify(callback, never()).notifyLost(eq(lostNeighbor), anyString(), any())
        }
    }

    private fun verifyNudFailureMetrics(
        eventType: NudEventType,
        ipType: IpType,
        lostNeighborType: NudNeighborType
    ) {
        verify(mIpReachabilityMonitorMetrics, timeout(TEST_TIMEOUT_MS)).setNudIpType(eq(ipType))
        verify(mIpReachabilityMonitorMetrics, timeout(TEST_TIMEOUT_MS))
                .setNudEventType(eq(eventType))
        verify(mIpReachabilityMonitorMetrics, timeout(TEST_TIMEOUT_MS))
                .setNudNeighborType(eq(lostNeighborType))
    }

    private fun verifyNudFailureMetricsNotReported(
    ) {
        verify(mIpReachabilityMonitorMetrics, never()).setNudIpType(any())
        verify(mIpReachabilityMonitorMetrics, never()).setNudEventType(any())
        verify(mIpReachabilityMonitorMetrics, never()).setNudNeighborType(any())
    }

    // Verify if the notifyLost will be called when one neighbor has lost but it's still
    // provisioned.
    private fun runLoseNeighborStillProvisionedTest(
        newLp: LinkProperties,
        lostNeighbor: InetAddress,
        eventType: NudEventType,
        ipType: IpType,
        lostNeighborType: NudNeighborType
    ) {
        reachabilityMonitor.updateLinkProperties(newLp)

        neighborMonitor.enqueuePacket(makeNewNeighMessage(lostNeighbor, NUD_FAILED))
        handlerThread.waitForIdle(TEST_TIMEOUT_MS)
        verify(callback, never()).notifyLost(any(), anyString(), any(NudEventType::class.java))
        verifyNudFailureMetrics(eventType, ipType, lostNeighborType)
    }

    private fun prepareNeighborReachableButMacAddrChangedTest(
        newLp: LinkProperties,
        neighbor: InetAddress,
        macaddr: String
    ) {
        reachabilityMonitor.updateLinkProperties(newLp)

        neighborMonitor.enqueuePacket(makeNewNeighMessage(neighbor, NUD_REACHABLE, macaddr))
        handlerThread.waitForIdle(TEST_TIMEOUT_MS)
        verify(callback, never()).notifyLost(eq(neighbor), anyString(),
                any(NudEventType::class.java))
    }

    @Test
    fun testLoseProvisioning_Ipv4DnsLost() {
        runLoseProvisioningTest(TEST_LINK_PROPERTIES, TEST_IPV4_DNS, NUD_ORGANIC_FAILED_CRITICAL)
    }

    @Test
    fun testLoseProvisioning_Ipv6DnsLost() {
        runLoseProvisioningTest(TEST_LINK_PROPERTIES, TEST_IPV6_DNS, NUD_ORGANIC_FAILED_CRITICAL)
    }

    @Test
    fun testLoseProvisioning_Ipv4GatewayLost() {
        runLoseProvisioningTest(TEST_LINK_PROPERTIES, TEST_IPV4_GATEWAY,
                NUD_ORGANIC_FAILED_CRITICAL)
    }

    @Test
    fun testLoseProvisioning_Ipv6GatewayLost() {
        runLoseProvisioningTest(TEST_LINK_PROPERTIES, TEST_IPV6_GATEWAY,
                NUD_ORGANIC_FAILED_CRITICAL)
    }

    @Test
    fun testLoseProvisioning_ignoreOrganicIpv4DnsLost() {
        runLoseProvisioningTest(TEST_LINK_PROPERTIES, TEST_IPV4_DNS, NUD_ORGANIC_FAILED_CRITICAL,
                false /* expectedNotifyLost */)
    }

    @Test
    fun testLoseProvisioning_ignoreOrganicIpv6DnsLost() {
        runLoseProvisioningTest(TEST_LINK_PROPERTIES, TEST_IPV6_DNS, NUD_ORGANIC_FAILED_CRITICAL,
                false /* expectedNotifyLost */)
    }

    @Test
    fun testLoseProvisioning_ignoreOrganicIpv4GatewayLost() {
        runLoseProvisioningTest(TEST_LINK_PROPERTIES, TEST_IPV4_GATEWAY,
                NUD_ORGANIC_FAILED_CRITICAL, false /* expectedNotifyLost */)
    }

    @Test
    fun testLoseProvisioning_ignoreOrganicIpv6GatewayLost() {
        runLoseProvisioningTest(TEST_LINK_PROPERTIES, TEST_IPV6_GATEWAY,
                NUD_ORGANIC_FAILED_CRITICAL, false /* expectedNotifyLost */)
    }

    private fun runNudProbeFailureMetricsTest(
        lp: LinkProperties,
        lostNeighbor: InetAddress,
        eventType: NudEventType,
        ipType: IpType,
        lostNeighborType: NudNeighborType
    ) {
        runLoseProvisioningTest(lp, lostNeighbor, eventType)
        verifyNudFailureMetrics(eventType, ipType, lostNeighborType)
    }

    @Test
    fun testNudProbeFailedMetrics_Ipv6GatewayLostPostRoaming() {
        reachabilityMonitor.probeAll(true /* dueToRoam */)
        runNudProbeFailureMetricsTest(TEST_LINK_PROPERTIES, TEST_IPV6_GATEWAY,
                NUD_POST_ROAMING_FAILED_CRITICAL, IPV6, NUD_NEIGHBOR_GATEWAY)
    }

    @Test
    fun testNudProbeFailedMetrics_Ipv4GatewayLostPostRoaming() {
        reachabilityMonitor.probeAll(true /* dueToRoam */)
        runNudProbeFailureMetricsTest(TEST_LINK_PROPERTIES, TEST_IPV4_GATEWAY,
                NUD_POST_ROAMING_FAILED_CRITICAL, IPV4, NUD_NEIGHBOR_GATEWAY)
    }

    @Test
    fun testNudProbeFailedMetrics_Ipv6DnsLostPostRoaming() {
        reachabilityMonitor.probeAll(true /* dueToRoam */)
        runNudProbeFailureMetricsTest(TEST_LINK_PROPERTIES, TEST_IPV6_DNS,
                NUD_POST_ROAMING_FAILED_CRITICAL, IPV6, NUD_NEIGHBOR_DNS)
    }

    @Test
    fun testNudProbeFailedMetrics_Ipv4DnsLostPostRoaming() {
        reachabilityMonitor.probeAll(true /* dueToRoam */)
        runNudProbeFailureMetricsTest(TEST_LINK_PROPERTIES, TEST_IPV4_DNS,
                NUD_POST_ROAMING_FAILED_CRITICAL, IPV4, NUD_NEIGHBOR_DNS)
    }

    @Test
    fun testNudProbeFailedMetrics_IPv4BothGatewayAndDnsLostPostRoaming() {
        reachabilityMonitor.probeAll(true /* dueToRoam */)
        runNudProbeFailureMetricsTest(TEST_IPV4_ONLY_LINK_PROPERTIES, TEST_IPV4_GATEWAY_DNS,
                NUD_POST_ROAMING_FAILED_CRITICAL, IPV4, NUD_NEIGHBOR_BOTH)
    }

    @Test
    fun testNudProbeFailedMetrics_IPv6LinklocalScopedGatewayLostPostRoaming() {
        reachabilityMonitor.probeAll(true /* dueToRoam */)
        runNudProbeFailureMetricsTest(TEST_IPV6_LINKLOCAL_SCOPED_LINK_PROPERTIES,
                TEST_IPV6_LINKLOCAL_SCOPED_GATEWAY, NUD_POST_ROAMING_FAILED_CRITICAL, IPV6,
                NUD_NEIGHBOR_GATEWAY)
    }

    @Test
    fun testNudProbeFailedMetrics_Ipv6GatewayLostAfterConfirm() {
        reachabilityMonitor.probeAll(false /* dueToRoam */)
        runNudProbeFailureMetricsTest(TEST_LINK_PROPERTIES, TEST_IPV6_GATEWAY,
                NUD_CONFIRM_FAILED_CRITICAL, IPV6, NUD_NEIGHBOR_GATEWAY)
    }

    @Test
    fun testNudProbeFailedMetrics_Ipv4GatewayLostAfterConfirm() {
        reachabilityMonitor.probeAll(false /* dueToRoam */)
        runNudProbeFailureMetricsTest(TEST_LINK_PROPERTIES, TEST_IPV4_GATEWAY,
                NUD_CONFIRM_FAILED_CRITICAL, IPV4, NUD_NEIGHBOR_GATEWAY)
    }

    @Test
    fun testNudProbeFailedMetrics_Ipv6DnsLostAfterConfirm() {
        reachabilityMonitor.probeAll(false /* dueToRoam */)
        runNudProbeFailureMetricsTest(TEST_LINK_PROPERTIES, TEST_IPV6_DNS,
                NUD_CONFIRM_FAILED_CRITICAL, IPV6, NUD_NEIGHBOR_DNS)
    }

    @Test
    fun testNudProbeFailedMetrics_Ipv4DnsLostAfterConfirm() {
        reachabilityMonitor.probeAll(false /* dueToRoam */)
        runNudProbeFailureMetricsTest(TEST_LINK_PROPERTIES, TEST_IPV4_DNS,
                NUD_CONFIRM_FAILED_CRITICAL, IPV4, NUD_NEIGHBOR_DNS)
    }

    @Test
    fun testNudProbeFailedMetrics_IPv4BothGatewayAndDnsLostAfterConfirm() {
        reachabilityMonitor.probeAll(false /* dueToRoam */)
        runNudProbeFailureMetricsTest(TEST_IPV4_ONLY_LINK_PROPERTIES, TEST_IPV4_GATEWAY_DNS,
                NUD_CONFIRM_FAILED_CRITICAL, IPV4, NUD_NEIGHBOR_BOTH)
    }

    @Test
    fun testNudProbeFailedMetrics_IPv6LinklocalScopedGatewayLostAfterConfirm() {
        reachabilityMonitor.probeAll(false /* dueToRoam */)
        runNudProbeFailureMetricsTest(TEST_IPV6_LINKLOCAL_SCOPED_LINK_PROPERTIES,
                TEST_IPV6_LINKLOCAL_SCOPED_GATEWAY, NUD_CONFIRM_FAILED_CRITICAL, IPV6,
                NUD_NEIGHBOR_GATEWAY)
    }

    @Test
    fun testNudProbeFailedMetrics_IPv6GatewayLostOrganic() {
        runNudProbeFailureMetricsTest(TEST_LINK_PROPERTIES, TEST_IPV6_GATEWAY,
                NUD_ORGANIC_FAILED_CRITICAL, IPV6, NUD_NEIGHBOR_GATEWAY)
    }

    @Test
    fun testNudProbeFailedMetrics_IPv4GatewayLostOrganic() {
        runNudProbeFailureMetricsTest(TEST_LINK_PROPERTIES, TEST_IPV4_GATEWAY,
                NUD_ORGANIC_FAILED_CRITICAL, IPV4, NUD_NEIGHBOR_GATEWAY)
    }

    @Test
    fun testNudProbeFailedMetrics_IPv6DnsLostOrganic() {
        runNudProbeFailureMetricsTest(TEST_LINK_PROPERTIES, TEST_IPV6_DNS,
                NUD_ORGANIC_FAILED_CRITICAL, IPV6, NUD_NEIGHBOR_DNS)
    }

    @Test
    fun testNudProbeFailedMetrics_IPv4DnsLostOrganic() {
        runNudProbeFailureMetricsTest(TEST_LINK_PROPERTIES, TEST_IPV4_DNS,
                NUD_ORGANIC_FAILED_CRITICAL, IPV4, NUD_NEIGHBOR_DNS)
    }

    @Test
    fun testNudProbeFailedMetrics_IPv4BothGatewayAndDnsLostOrganic() {
        runNudProbeFailureMetricsTest(TEST_IPV4_ONLY_LINK_PROPERTIES, TEST_IPV4_GATEWAY_DNS,
                NUD_ORGANIC_FAILED_CRITICAL, IPV4, NUD_NEIGHBOR_BOTH)
    }

    @Test
    fun testNudProbeFailedMetrics_IPv6LinklocalScopedGatewayLostOrganic() {
        runNudProbeFailureMetricsTest(TEST_IPV6_LINKLOCAL_SCOPED_LINK_PROPERTIES,
                TEST_IPV6_LINKLOCAL_SCOPED_GATEWAY, NUD_ORGANIC_FAILED_CRITICAL, IPV6,
                NUD_NEIGHBOR_GATEWAY)
    }

    @Test
    fun testNudProbeFailedMetrics_IPv6OneDnsNeighborLostPostRoaming() {
        reachabilityMonitor.probeAll(true /* dueToRoam */)
        runLoseNeighborStillProvisionedTest(TEST_DUAL_LINK_PROPERTIES, TEST_IPV6_DNS,
                NUD_POST_ROAMING_FAILED, IPV6, NUD_NEIGHBOR_DNS)
    }

    @Test
    fun testNudProbeFailedMetrics_IPv6OneDnsNeighborLostAfterConfirm() {
        reachabilityMonitor.probeAll(false /* dueToRoam */)
        runLoseNeighborStillProvisionedTest(TEST_DUAL_LINK_PROPERTIES, TEST_IPV6_DNS,
                NUD_CONFIRM_FAILED, IPV6, NUD_NEIGHBOR_DNS)
    }

    @Test
    fun testNudProbeFailedMetrics_IPv6OneDnsNeighborLostOrganic() {
        runLoseNeighborStillProvisionedTest(TEST_DUAL_LINK_PROPERTIES, TEST_IPV6_DNS,
                NUD_ORGANIC_FAILED, IPV6, NUD_NEIGHBOR_DNS)
    }

    @Test
    fun testNudProbeFailedMetrics_multipleProbesFromRoamFirst() {
        reachabilityMonitor.probeAll(true /* dueToRoam */)
        handlerThread.waitForIdle(TEST_TIMEOUT_MS)
        Thread.sleep(2)
        reachabilityMonitor.probeAll(false /* dueToRoam */)
        runLoseProvisioningTest(TEST_LINK_PROPERTIES, TEST_IPV6_GATEWAY,
                NUD_POST_ROAMING_FAILED_CRITICAL)

        verifyNudFailureMetrics(NUD_POST_ROAMING_FAILED_CRITICAL, IPV6, NUD_NEIGHBOR_GATEWAY)
    }

    @Test
    fun testNudProbeFailedMetrics_multipleProbesFromConfirmFirst() {
        reachabilityMonitor.probeAll(false /* dueToRoam */)
        handlerThread.waitForIdle(TEST_TIMEOUT_MS)
        Thread.sleep(2)
        reachabilityMonitor.probeAll(true /* dueToRoam */)
        runLoseProvisioningTest(TEST_LINK_PROPERTIES, TEST_IPV6_GATEWAY,
                NUD_CONFIRM_FAILED_CRITICAL)

        verifyNudFailureMetrics(NUD_CONFIRM_FAILED_CRITICAL, IPV6, NUD_NEIGHBOR_GATEWAY)
    }

    private fun probeWithNeighborEvent(dueToRoam: Boolean, neighbor: InetAddress, macaddr: String) {
        reachabilityMonitor.probeAll(dueToRoam)
        neighborMonitor.enqueuePacket(makeNewNeighMessage(neighbor, NUD_PROBE, macaddr))
    }

    private fun verifyNudMacAddrChanged(
        neighbor: InetAddress,
        eventType: NudEventType,
        ipType: IpType
    ) {
        neighborMonitor.enqueuePacket(makeNewNeighMessage(neighbor, NUD_REACHABLE, TEST_MAC_2))
        verify(callback, timeout(TEST_TIMEOUT_MS)).notifyLost(eq(neighbor), anyString(),
                eq(eventType))
        verifyNudFailureMetrics(eventType, ipType, NUD_NEIGHBOR_GATEWAY)
    }

    private fun verifyNudMacAddrChangeNotReported(
        neighbor: InetAddress,
    ) {
        neighborMonitor.enqueuePacket(makeNewNeighMessage(neighbor, NUD_REACHABLE, TEST_MAC_2))
        verify(callback, never()).notifyLost(eq(neighbor), anyString(), any())
        verifyNudFailureMetricsNotReported()
    }

    @Test
    fun testNudProbeFailedMetrics_defaultIPv6GatewayMacAddrChangedAfterRoaming() {
        prepareNeighborReachableButMacAddrChangedTest(TEST_LINK_PROPERTIES, TEST_IPV6_GATEWAY,
            TEST_MAC_1)
        probeWithNeighborEvent(true /* dueToRoam */, TEST_IPV6_GATEWAY, TEST_MAC_1)
        verifyNudMacAddrChanged(TEST_IPV6_GATEWAY, NUD_POST_ROAMING_MAC_ADDRESS_CHANGED, IPV6)
    }

    @Test
    fun testNudProbeFailedMetrics_defaultIPv4GatewayMacAddrChangedAfterRoaming() {
        prepareNeighborReachableButMacAddrChangedTest(TEST_LINK_PROPERTIES, TEST_IPV4_GATEWAY,
            TEST_MAC_1)

        probeWithNeighborEvent(true /* dueToRoam */, TEST_IPV4_GATEWAY, TEST_MAC_1)
        verifyNudMacAddrChanged(TEST_IPV4_GATEWAY, NUD_POST_ROAMING_MAC_ADDRESS_CHANGED, IPV4)
    }

    @Test
    fun testNudProbeFailedMetrics_defaultIPv6GatewayMacAddrChangedAfterConfirm() {
        prepareNeighborReachableButMacAddrChangedTest(TEST_LINK_PROPERTIES, TEST_IPV6_GATEWAY,
            TEST_MAC_1)

        reachabilityMonitor.probeAll(false /* dueToRoam */)
        verifyNudMacAddrChangeNotReported(TEST_IPV6_GATEWAY)
    }

    @Test
    fun testNudProbeFailedMetrics_defaultIPv6GatewayMacAddrChangedAfterOrganic() {
        prepareNeighborReachableButMacAddrChangedTest(TEST_LINK_PROPERTIES, TEST_IPV6_GATEWAY,
            TEST_MAC_1)

        verifyNudMacAddrChangeNotReported(TEST_IPV6_GATEWAY)
    }

    @SuppressLint("NewApi")
    @Test
    fun testIsOnLink() {
        val routes: List<RouteInfo> = listOf(
                RouteInfo(
                        IpPrefix(parseNumericAddress("192.168.0.0"), 16),
                        null /* gateway */,
                        null /* iface */,
                        RouteInfo.RTN_THROW),
                RouteInfo(IpPrefix(parseNumericAddress("0.0.0.0"), 0), null /* gateway */)
        )

        assertTrue(IpReachabilityMonitor.isOnLink(routes, parseNumericAddress("192.168.0.1")))
    }

    @SuppressLint("NewApi")
    @Test
    fun testIsOnLink_withThrowRoutes() {
        val routes: List<RouteInfo> = listOf(
                RouteInfo(
                        IpPrefix(parseNumericAddress("192.168.0.0"), 16),
                        null /* gateway */,
                        null /* iface */,
                        RouteInfo.RTN_THROW)
        )

        assertFalse(IpReachabilityMonitor.isOnLink(routes, parseNumericAddress("192.168.0.1")))
    }
}
