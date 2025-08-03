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

import android.net.MacAddress
import android.util.Log
import com.android.net.module.util.Ipv6Utils
import com.android.net.module.util.NetworkStackConstants.ETHER_HEADER_LEN
import com.android.net.module.util.NetworkStackConstants.ICMPV6_ND_OPTION_TLLA
import com.android.net.module.util.NetworkStackConstants.NEIGHBOR_ADVERTISEMENT_FLAG_SOLICITED
import com.android.net.module.util.Struct
import com.android.net.module.util.structs.Icmpv6Header
import com.android.net.module.util.structs.Ipv6Header
import com.android.net.module.util.structs.LlaOption
import com.android.net.module.util.structs.NsHeader
import com.android.testutils.PacketReflector.IPV6_HEADER_LENGTH
import java.lang.IllegalArgumentException
import java.net.Inet6Address
import java.nio.ByteBuffer

private const val NS_TYPE = 135.toShort()

/**
 * A class that can be used to reply to Neighbor Solicitation packets on a [TapPacketReader].
 */
class NSResponder(
    reader: TapPacketReader,
    table: Map<Inet6Address, MacAddress>,
    name: String = NSResponder::class.java.simpleName
) : PacketResponder(reader, Icmpv6Filter(), name) {
    companion object {
        private val TAG = NSResponder::class.simpleName
    }

    // Copy the map if not already immutable (toMap) to make sure it is not modified
    private val table = table.toMap()

    override fun replyToPacket(packet: ByteArray, reader: TapPacketReader) {
        if (packet.size < IPV6_HEADER_LENGTH) {
            return
        }
        val buf = ByteBuffer.wrap(packet, ETHER_HEADER_LEN, packet.size - ETHER_HEADER_LEN)
        val ipv6Header = parseOrLog(Ipv6Header::class.java, buf) ?: return
        val icmpHeader = parseOrLog(Icmpv6Header::class.java, buf) ?: return
        if (icmpHeader.type != NS_TYPE) {
            return
        }
        val ns = parseOrLog(NsHeader::class.java, buf) ?: return
        val replyMacAddr = table[ns.target] ?: return
        val slla = parseOrLog(LlaOption::class.java, buf) ?: return
        val requesterMac = slla.linkLayerAddress

        val tlla = LlaOption.build(ICMPV6_ND_OPTION_TLLA.toByte(), replyMacAddr)
        reader.sendResponse(Ipv6Utils.buildNaPacket(
            replyMacAddr /* srcMac */,
            requesterMac /* dstMac */,
            ns.target /* srcIp */,
            ipv6Header.srcIp /* dstIp */,
            NEIGHBOR_ADVERTISEMENT_FLAG_SOLICITED,
            ns.target,
            tlla))
    }

    private fun <T> parseOrLog(clazz: Class<T>, buf: ByteBuffer): T? where T : Struct {
        return try {
            Struct.parse(clazz, buf)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid ${clazz.simpleName} in ICMPv6 packet", e)
            null
        }
    }
}
