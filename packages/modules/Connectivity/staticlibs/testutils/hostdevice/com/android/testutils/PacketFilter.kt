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

import java.net.Inet4Address
import java.util.function.Predicate

// Some of the below constants are duplicated with NetworkStackConstants, but this is a hostdevice
// library usable for host-side tests, so device-side utils are not usable, and there is no
// host-side non-test library to host common constants.
private const val ETHER_TYPE_OFFSET = 12
private const val ETHER_HEADER_LENGTH = 14
private const val IPV4_PROTOCOL_OFFSET = ETHER_HEADER_LENGTH + 9
private const val IPV6_PROTOCOL_OFFSET = ETHER_HEADER_LENGTH + 6
private const val IPV4_CHECKSUM_OFFSET = ETHER_HEADER_LENGTH + 10
private const val IPV4_DST_OFFSET = ETHER_HEADER_LENGTH + 16
private const val IPV4_HEADER_LENGTH = 20
private const val IPV6_HEADER_LENGTH = 40
private const val IPV4_PAYLOAD_OFFSET = ETHER_HEADER_LENGTH + IPV4_HEADER_LENGTH
private const val IPV6_PAYLOAD_OFFSET = ETHER_HEADER_LENGTH + IPV6_HEADER_LENGTH
private const val UDP_HEADER_LENGTH = 8
private const val BOOTP_OFFSET = IPV4_PAYLOAD_OFFSET + UDP_HEADER_LENGTH
private const val BOOTP_TID_OFFSET = BOOTP_OFFSET + 4
private const val BOOTP_CLIENT_MAC_OFFSET = BOOTP_OFFSET + 28
private const val DHCP_OPTIONS_OFFSET = BOOTP_OFFSET + 240
private const val ARP_OPCODE_OFFSET = ETHER_HEADER_LENGTH + 6

/**
 * A [Predicate] that matches a [ByteArray] if it contains the specified [bytes] at the specified
 * [offset].
 */
class OffsetFilter(val offset: Int, vararg val bytes: Byte) : Predicate<ByteArray> {
    override fun test(packet: ByteArray) =
            bytes.withIndex().all { it.value == packet[offset + it.index] }
}

private class UdpPortFilter(
    private val udpOffset: Int,
    private val src: Short?,
    private val dst: Short?
) : Predicate<ByteArray> {
    override fun test(t: ByteArray): Boolean {
        if (src != null && !OffsetFilter(udpOffset,
                        src.toInt().ushr(8).toByte(), src.toByte()).test(t)) {
            return false
        }

        if (dst != null && !OffsetFilter(udpOffset + 2,
                        dst.toInt().ushr(8).toByte(), dst.toByte()).test(t)) {
            return false
        }
        return true
    }
}

/**
 * A [Predicate] that matches ethernet-encapped packets that contain an UDP over IPv4 datagram.
 */
class IPv4UdpFilter @JvmOverloads constructor(
    srcPort: Short? = null,
    dstPort: Short? = null
) : Predicate<ByteArray> {
    private val impl = OffsetFilter(ETHER_TYPE_OFFSET, 0x08, 0x00 /* IPv4 */).and(
            OffsetFilter(IPV4_PROTOCOL_OFFSET, 17 /* UDP */)).and(
            UdpPortFilter(IPV4_PAYLOAD_OFFSET, srcPort, dstPort))
    override fun test(t: ByteArray) = impl.test(t)
}

/**
 * A [Predicate] that matches ethernet-encapped packets that contain an UDP over IPv6 datagram.
 */
class IPv6UdpFilter @JvmOverloads constructor(
    srcPort: Short? = null,
    dstPort: Short? = null
) : Predicate<ByteArray> {
    private val impl = OffsetFilter(ETHER_TYPE_OFFSET, 0x86.toByte(), 0xdd.toByte() /* IPv6 */).and(
            OffsetFilter(IPV6_PROTOCOL_OFFSET, 17 /* UDP */)).and(
            UdpPortFilter(IPV6_PAYLOAD_OFFSET, srcPort, dstPort))
    override fun test(t: ByteArray) = impl.test(t)
}

/**
 * A [Predicate] that matches ethernet-encapped packets sent to the specified IPv4 destination.
 */
class IPv4DstFilter(dst: Inet4Address) : Predicate<ByteArray> {
    private val impl = OffsetFilter(IPV4_DST_OFFSET, *dst.address)
    override fun test(t: ByteArray) = impl.test(t)
}

/**
 * A [Predicate] that matches ethernet-encapped ARP requests.
 */
class ArpRequestFilter : Predicate<ByteArray> {
    private val impl = OffsetFilter(ETHER_TYPE_OFFSET, 0x08, 0x06 /* ARP */)
            .and(OffsetFilter(ARP_OPCODE_OFFSET, 0x00, 0x01 /* request */))
    override fun test(t: ByteArray) = impl.test(t)
}

class Icmpv6Filter : Predicate<ByteArray> {
    private val impl = OffsetFilter(ETHER_TYPE_OFFSET, 0x86.toByte(), 0xdd.toByte() /* IPv6 */).and(
        OffsetFilter(IPV6_PROTOCOL_OFFSET, 58 /* ICMPv6 */))
    override fun test(t: ByteArray) = impl.test(t)
}

/**
 * A [Predicate] that matches ethernet-encapped DHCP packets sent from a DHCP client.
 */
class DhcpClientPacketFilter : Predicate<ByteArray> {
    private val impl = IPv4UdpFilter(srcPort = 68, dstPort = 67)
    override fun test(t: ByteArray) = impl.test(t)
}

/**
 * A [Predicate] that matches a [ByteArray] if it contains a ethernet-encapped DHCP packet that
 * contains the specified option with the specified [bytes] as value.
 */
class DhcpOptionFilter(val option: Byte, vararg val bytes: Byte) : Predicate<ByteArray> {
    override fun test(packet: ByteArray): Boolean {
        val option = findDhcpOption(packet, option) ?: return false
        return option.contentEquals(bytes)
    }
}

/**
 * Find a DHCP option in a packet and return its value, if found.
 */
fun findDhcpOption(packet: ByteArray, option: Byte): ByteArray? =
        findOptionOffset(packet, option, DHCP_OPTIONS_OFFSET)?.let {
            val optionLen = packet[it + 1]
            return packet.copyOfRange(it + 2 /* type, length bytes */, it + 2 + optionLen)
        }

private tailrec fun findOptionOffset(packet: ByteArray, option: Byte, searchOffset: Int): Int? {
    if (packet.size <= searchOffset + 2 /* type, length bytes */) return null

    return if (packet[searchOffset] == option) searchOffset else {
        val optionLen = packet[searchOffset + 1]
        findOptionOffset(packet, option, searchOffset + 2 + optionLen)
    }
}
