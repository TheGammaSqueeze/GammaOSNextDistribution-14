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

package android.net.util

import android.Manifest.permission.MANAGE_TEST_NETWORKS
import android.content.Context
import android.net.InetAddresses.parseNumericAddress
import android.net.IpPrefix
import android.net.MacAddress
import android.net.TestNetworkInterface
import android.net.TestNetworkManager
import android.net.dhcp.DhcpPacket
import android.os.HandlerThread
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.OsConstants.AF_INET
import android.system.OsConstants.AF_PACKET
import android.system.OsConstants.ETH_P_IPV6
import android.system.OsConstants.IPPROTO_UDP
import android.system.OsConstants.SOCK_CLOEXEC
import android.system.OsConstants.SOCK_DGRAM
import android.system.OsConstants.SOCK_NONBLOCK
import android.system.OsConstants.SOCK_RAW
import android.system.OsConstants.SOL_SOCKET
import android.system.OsConstants.SO_RCVTIMEO
import android.system.StructTimeval
import androidx.test.platform.app.InstrumentationRegistry
import com.android.internal.util.HexDump
import com.android.net.module.util.InterfaceParams
import com.android.net.module.util.IpUtils
import com.android.net.module.util.Ipv6Utils
import com.android.net.module.util.NetworkStackConstants.ETHER_ADDR_LEN
import com.android.net.module.util.NetworkStackConstants.ETHER_HEADER_LEN
import com.android.net.module.util.NetworkStackConstants.IPV4_ADDR_ANY
import com.android.net.module.util.NetworkStackConstants.IPV4_CHECKSUM_OFFSET
import com.android.net.module.util.NetworkStackConstants.IPV4_FLAGS_OFFSET
import com.android.net.module.util.NetworkStackConstants.IPV4_FLAG_DF
import com.android.net.module.util.NetworkStackConstants.IPV4_FLAG_MF
import com.android.net.module.util.NetworkStackConstants.IPV4_HEADER_MIN_LEN
import com.android.net.module.util.NetworkStackConstants.IPV6_ADDR_ALL_NODES_MULTICAST
import com.android.net.module.util.NetworkStackConstants.UDP_HEADER_LEN
import com.android.net.module.util.structs.PrefixInformationOption
import com.android.networkstack.util.NetworkStackUtils
import com.android.testutils.ArpRequestFilter
import com.android.testutils.IPv4UdpFilter
import com.android.testutils.TapPacketReader
import java.io.FileDescriptor
import java.net.Inet4Address
import java.net.Inet6Address
import java.nio.ByteBuffer
import java.util.Arrays
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test

class NetworkStackUtilsIntegrationTest {
    private val inst by lazy { InstrumentationRegistry.getInstrumentation() }
    private val context by lazy { inst.context }

    private val TEST_TIMEOUT_MS = 10_000L
    private val TEST_MTU = 1500
    private val TEST_TARGET_IPV4_ADDR = parseNumericAddress("192.0.2.42") as Inet4Address
    private val TEST_SRC_MAC = MacAddress.fromString("BA:98:76:54:32:10")
    private val TEST_TARGET_MAC = MacAddress.fromString("01:23:45:67:89:0A")
    private val TEST_INET6ADDR_1 = parseNumericAddress("2001:db8::1") as Inet6Address
    private val TEST_INET6ADDR_2 = parseNumericAddress("2001:db8::2") as Inet6Address
    private val TEST_INET6ADDR_3 = parseNumericAddress("fd01:db8::3") as Inet6Address

    // RFC4291 section 2.7.1
    private val SOLICITED_NODE_MULTICAST_PREFIX = "FF02:0:0:0:0:1:FF00::/104"

    private val readerHandler = HandlerThread(
            NetworkStackUtilsIntegrationTest::class.java.simpleName)
    private lateinit var iface: TestNetworkInterface
    private lateinit var reader: TapPacketReader

    @Before
    fun setUp() {
        inst.uiAutomation.adoptShellPermissionIdentity(MANAGE_TEST_NETWORKS)
        try {
            val tnm = context.assertHasService(TestNetworkManager::class)
            iface = tnm.createTapInterface()
        } finally {
            inst.uiAutomation.dropShellPermissionIdentity()
        }
        readerHandler.start()
        reader = TapPacketReader(readerHandler.threadHandler, iface.fileDescriptor.fileDescriptor,
                1500 /* maxPacketSize */)
        readerHandler.threadHandler.post { reader.start() }
    }

    @After
    fun tearDown() {
        readerHandler.quitSafely()
        if (this::iface.isInitialized) iface.fileDescriptor.close()
    }

    @Test
    fun testAddArpEntry() {
        val socket = Os.socket(AF_INET, SOCK_DGRAM or SOCK_NONBLOCK, IPPROTO_UDP)
        SocketUtils.bindSocketToInterface(socket, iface.interfaceName)

        NetworkStackUtils.addArpEntry(TEST_TARGET_IPV4_ADDR, TEST_TARGET_MAC, iface.interfaceName,
                socket)

        // Fake DHCP packet: would not be usable as a DHCP offer (most IPv4 addresses are all-zero,
        // no gateway or DNS servers, etc).
        // Using a DHCP packet to replicate actual usage of the API: it is used in DhcpServer to
        // send packets to clients before their IP address has been assigned.
        val buffer = DhcpPacket.buildOfferPacket(DhcpPacket.ENCAP_BOOTP, 123 /* transactionId */,
                false /* broadcast */, IPV4_ADDR_ANY /* serverIpAddr */,
                IPV4_ADDR_ANY /* relayIp */, IPV4_ADDR_ANY /* yourIp */,
                TEST_TARGET_MAC.toByteArray(), 3600 /* timeout */, IPV4_ADDR_ANY /* netMask */,
                IPV4_ADDR_ANY /* bcAddr */, emptyList<Inet4Address>() /* gateways */,
                emptyList<Inet4Address>() /* dnsServers */,
                IPV4_ADDR_ANY /* dhcpServerIdentifier */, null /* domainName */,
                null /* hostname */, false /* metered */, 1500 /* mtu */,
                null /* captivePortalUrl */)
        // Not using .array as per errorprone "ByteBufferBackingArray" recommendation
        val originalPacket = buffer.readAsArray()

        Os.sendto(socket, originalPacket, 0 /* bytesOffset */, originalPacket.size /* bytesCount */,
                0 /* flags */, TEST_TARGET_IPV4_ADDR, DhcpPacket.DHCP_CLIENT.toInt() /* port */)

        // Verify the packet was sent to the mac address specified in the ARP entry
        // Also accept ARP requests, but expect that none is sent before the UDP packet
        // IPv6 NS may be sent on the interface but will be filtered out
        val sentPacket = reader.poll(TEST_TIMEOUT_MS, IPv4UdpFilter().or(ArpRequestFilter()))
                ?: fail("Packet was not sent on the interface")

        val sentTargetAddr = MacAddress.fromBytes(sentPacket.copyOfRange(0, ETHER_ADDR_LEN))
        assertEquals(TEST_TARGET_MAC, sentTargetAddr, "Destination ethernet address does not match")

        val sentDhcpPacket = sentPacket.copyOfRange(
                ETHER_HEADER_LEN + IPV4_HEADER_MIN_LEN + UDP_HEADER_LEN, sentPacket.size)

        assertArrayEquals("Sent packet != original packet", originalPacket, sentDhcpPacket)
    }

    private fun doTestAttachRaFilter(generic: Boolean) {
        val socket = Os.socket(AF_PACKET, SOCK_RAW or SOCK_CLOEXEC, 0)
        val ifParams = InterfaceParams.getByName(iface.interfaceName)
                ?: fail("Could not obtain interface params for ${iface.interfaceName}")
        val socketAddr = SocketUtils.makePacketSocketAddress(ETH_P_IPV6, ifParams.index)
        Os.bind(socket, socketAddr)
        Os.setsockoptTimeval(socket, SOL_SOCKET, SO_RCVTIMEO,
                StructTimeval.fromMillis(TEST_TIMEOUT_MS))

        // Verify that before setting any filter, the socket receives pings
        val echo = Ipv6Utils.buildEchoRequestPacket(TEST_SRC_MAC, TEST_TARGET_MAC, TEST_INET6ADDR_1,
                TEST_INET6ADDR_2)
        reader.sendResponse(echo)
        echo.rewind()
        assertNextPacketEquals(socket, echo.readAsArray(), "ICMPv6 echo")

        if (generic) {
            NetworkStackUtils.attachControlPacketFilter(socket)
        } else {
            NetworkStackUtils.attachRaFilter(socket)
        }
        // Send another echo, then an RA. After setting the filter expect only the RA.
        echo.rewind()
        reader.sendResponse(echo)
        val pio = PrefixInformationOption.build(IpPrefix("2001:db8:1::/64"),
                0.toByte() /* flags */, 3600 /* validLifetime */, 1800 /* preferredLifetime */)
        val ra = Ipv6Utils.buildRaPacket(TEST_SRC_MAC, TEST_TARGET_MAC,
                TEST_INET6ADDR_1 /* routerAddr */, IPV6_ADDR_ALL_NODES_MULTICAST,
                0.toByte() /* flags */, 1800 /* lifetime */, 0 /* reachableTime */,
                0 /* retransTimer */, pio)
        reader.sendResponse(ra)
        ra.rewind()

        assertNextPacketEquals(socket, ra.readAsArray(), "ICMPv6 RA")
    }

    @Test
    fun testAttachRaFilter() {
        doTestAttachRaFilter(false)
    }

    @Test
    fun testRaViaAttachControlPacketFilter() {
        doTestAttachRaFilter(true)
    }

    private fun assertNextPacketEquals(socket: FileDescriptor, expected: ByteArray, descr: String) {
        val buffer = ByteArray(TEST_MTU)
        val readPacket = Os.read(socket, buffer, 0 /* byteOffset */, buffer.size)
        assertTrue(readPacket > 0, "$descr not received")
        assertEquals(expected.size, readPacket, "Received packet size does not match for $descr")
        assertArrayEquals("Received packet != expected $descr",
                expected, buffer.copyOfRange(0, readPacket))
    }

    private fun assertSolicitedNodeMulticastAddress(
        expected: Inet6Address?,
        unicast: Inet6Address
    ) {
        assertNotNull(expected)
        val prefix = IpPrefix(SOLICITED_NODE_MULTICAST_PREFIX)
        assertTrue(prefix.contains(expected))
        assertTrue(expected.isMulticastAddress())
        // check the last 3 bytes of address
        assertArrayEquals(Arrays.copyOfRange(expected.getAddress(), 13, 15),
                Arrays.copyOfRange(unicast.getAddress(), 13, 15))
    }

    @Test
    fun testConvertIpv6AddressToSolicitedNodeMulticast() {
        val addr1 = NetworkStackUtils.ipv6AddressToSolicitedNodeMulticast(TEST_INET6ADDR_1)
        assertSolicitedNodeMulticastAddress(addr1, TEST_INET6ADDR_1)

        val addr2 = NetworkStackUtils.ipv6AddressToSolicitedNodeMulticast(TEST_INET6ADDR_2)
        assertSolicitedNodeMulticastAddress(addr2, TEST_INET6ADDR_2)

        val addr3 = NetworkStackUtils.ipv6AddressToSolicitedNodeMulticast(TEST_INET6ADDR_3)
        assertSolicitedNodeMulticastAddress(addr3, TEST_INET6ADDR_3)
    }

    @Test
    fun testConvertMacAddressToEui64() {
        // MAC address with universal/local bit set (the first byte: 0xBA)
        var expected = byteArrayOf(
                0xB8.toByte(), 0x98.toByte(), 0x76.toByte(), 0xFF.toByte(),
                0xFE.toByte(), 0x54.toByte(), 0x32.toByte(), 0x10.toByte())
        val srcEui64 = NetworkStackUtils.macAddressToEui64(TEST_SRC_MAC)
        assertArrayEquals(expected, srcEui64)

        // MAC address with universal/local bit unset (the first byte: 0x01).
        expected = byteArrayOf(
                0x03.toByte(), 0x23.toByte(), 0x45.toByte(), 0xFF.toByte(),
                0xFE.toByte(), 0x67.toByte(), 0x89.toByte(), 0x0A.toByte())
        val targetEui64 = NetworkStackUtils.macAddressToEui64(TEST_TARGET_MAC)
        assertArrayEquals(expected, targetEui64)
    }

    @Test
    fun testGenerateIpv6AddressFromEui64() {
        val eui64 = NetworkStackUtils.macAddressToEui64(TEST_SRC_MAC)
        var prefix = IpPrefix("2001:db8:1::/80")
        // Don't accept the prefix length larger than 64.
        assertNull(NetworkStackUtils.createInet6AddressFromEui64(prefix, eui64))

        // prefix length equals to or less than 64 is acceptable.
        prefix = IpPrefix("2001:db8:1::/48")
        // IPv6 address string is formed by combining the IPv6 prefix("2001:db8:1::") and
        // EUI64 converted from TEST_SRC_MAC, see above test for the output EUI64 example.
        var expected = parseNumericAddress("2001:db8:1::b898:76ff:fe54:3210") as Inet6Address
        assertEquals(expected, NetworkStackUtils.createInet6AddressFromEui64(prefix, eui64))

        prefix = IpPrefix("2001:db8:1:2::/64")
        expected = parseNumericAddress("2001:db8:1:2:b898:76ff:fe54:3210") as Inet6Address
        assertEquals(expected, NetworkStackUtils.createInet6AddressFromEui64(prefix, eui64))
    }

    private fun assertSocketReadErrno(msg: String, fd: FileDescriptor, errno: Int) {
        val received = ByteBuffer.allocate(TEST_MTU)
        try {
            val len = Os.read(fd, received)
            fail(msg + ": " + toHexString(received, len))
        } catch (expected: ErrnoException) {
            assertEquals(errno.toLong(), expected.errno.toLong())
        }
    }

    private fun assertNextPacketOnSocket(fd: FileDescriptor, expectedPacket: ByteBuffer) {
        val received = ByteBuffer.allocate(TEST_MTU)
        val len = Os.read(fd, received)
        assertEquals(toHexString(expectedPacket, expectedPacket.limit()),
            toHexString(received, len))
    }

    private fun setMfBit(packet: ByteBuffer, set: Boolean) {
        val offset = ETHER_HEADER_LEN + IPV4_FLAGS_OFFSET
        var flagOff: Int = packet.getShort(offset).toInt()
        if (set) {
            flagOff = (flagOff or IPV4_FLAG_MF) and IPV4_FLAG_DF.inv()
        } else {
            flagOff = (flagOff or IPV4_FLAG_DF) and IPV4_FLAG_MF.inv()
        }
        packet.putShort(offset, flagOff.toShort())
        // Recalculate the checksum, which requires first clearing the checksum field.
        val checksumOffset = ETHER_HEADER_LEN + IPV4_CHECKSUM_OFFSET
        packet.putShort(checksumOffset, 0)
        packet.putShort(checksumOffset, IpUtils.ipChecksum(packet, ETHER_HEADER_LEN))
    }

    private fun doTestDhcpResponseWithMfBitDropped(generic: Boolean) {
        val ifindex = InterfaceParams.getByName(iface.interfaceName).index
        val packetSock = Os.socket(AF_PACKET, SOCK_RAW or SOCK_NONBLOCK, /*protocol=*/0)
        try {
            if (generic) {
                NetworkStackUtils.attachControlPacketFilter(packetSock)
            } else {
                NetworkStackUtils.attachDhcpFilter(packetSock)
            }
            val addr = SocketUtils.makePacketSocketAddress(OsConstants.ETH_P_IP, ifindex)
            Os.bind(packetSock, addr)
            val packet = DhcpPacket.buildNakPacket(DhcpPacket.ENCAP_L2, 42,
                TEST_TARGET_IPV4_ADDR, /*relayIp=*/ IPV4_ADDR_ANY, TEST_TARGET_MAC.toByteArray(),
                /*broadcast=*/ false, "NAK")
            setMfBit(packet, true)
            reader.sendResponse(packet)

            // Packet with MF bit set is not received.
            assertSocketReadErrno("Packet with MF bit should have been dropped",
                packetSock, OsConstants.EAGAIN)

            // Identical packet, except with MF bit cleared, should be received.
            setMfBit(packet, false)
            reader.sendResponse(packet)
            assertNextPacketOnSocket(packetSock, packet)
        } finally {
            Os.close(packetSock)
        }
    }

    @Test
    fun testDhcpResponseWithMfBitDropped() {
        doTestDhcpResponseWithMfBitDropped(false)
    }

    @Test
    fun testGenericDhcpResponseWithMfBitDropped() {
        doTestDhcpResponseWithMfBitDropped(true)
    }
}

private fun ByteBuffer.readAsArray(): ByteArray {
    val out = ByteArray(remaining())
    get(out)
    return out
}

private fun toHexString(b: ByteBuffer, len: Int): String {
    return HexDump.toHexString(Arrays.copyOf(b.array(), len))
}

private fun <T : Any> Context.assertHasService(manager: KClass<T>) = getSystemService(manager.java)
        ?: fail("Could not find service $manager")
