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

import android.net.MacAddress
import com.android.net.module.util.NetworkStackConstants.ETHER_HEADER_LEN
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer

private const val ARP_SENDER_MAC_OFFSET = ETHER_HEADER_LEN + 8
private const val ARP_TARGET_IPADDR_OFFSET = ETHER_HEADER_LEN + 24

private val TYPE_ARP = byteArrayOf(0x08, 0x06)
// Arp reply header for IPv4 over ethernet
private val ARP_REPLY_IPV4 = byteArrayOf(0x00, 0x01, 0x08, 0x00, 0x06, 0x04, 0x00, 0x02)

/**
 * A class that can be used to reply to ARP packets on a [TapPacketReader].
 */
class ArpResponder(
    reader: TapPacketReader,
    table: Map<Inet4Address, MacAddress>,
    name: String = ArpResponder::class.java.simpleName
) : PacketResponder(reader, ArpRequestFilter(), name) {
    // Copy the map if not already immutable (toMap) to make sure it is not modified
    private val table = table.toMap()

    override fun replyToPacket(packet: ByteArray, reader: TapPacketReader) {
        val targetIp = InetAddress.getByAddress(
                packet.copyFromIndexWithLength(ARP_TARGET_IPADDR_OFFSET, 4))
                as Inet4Address

        val macAddr = table[targetIp]?.toByteArray() ?: return
        val senderMac = packet.copyFromIndexWithLength(ARP_SENDER_MAC_OFFSET, 6)
        reader.sendResponse(ByteBuffer.wrap(
                // Ethernet header
                senderMac + macAddr + TYPE_ARP +
                        // ARP message
                        ARP_REPLY_IPV4 +
                        macAddr /* sender MAC */ +
                        targetIp.address /* sender IP addr */ +
                        macAddr /* target mac */ +
                        targetIp.address /* target IP addr */
        ))
    }
}

private fun ByteArray.copyFromIndexWithLength(start: Int, len: Int) =
        copyOfRange(start, start + len)
