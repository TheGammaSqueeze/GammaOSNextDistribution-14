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

@file:JvmName("PacketReflectorUtil")

package com.android.testutils

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.android.net.module.util.IpUtils
import com.android.testutils.PacketReflector.IPV4_HEADER_LENGTH
import com.android.testutils.PacketReflector.IPV6_HEADER_LENGTH
import java.io.FileDescriptor
import java.io.InterruptedIOException
import java.net.InetAddress
import java.nio.ByteBuffer

fun readPacket(fd: FileDescriptor, buf: ByteArray): Int {
    return try {
        Os.read(fd, buf, 0, buf.size)
    } catch (e: ErrnoException) {
        // Ignore normal use cases such as the EAGAIN error indicates that the read operation
        // cannot be completed immediately, or the EINTR error indicates that the read
        // operation was interrupted by a signal.
        if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) {
            -1
        } else {
            throw e
        }
    } catch (e: InterruptedIOException) {
        -1
    }
}

fun getInetAddressAt(buf: ByteArray, pos: Int, len: Int): InetAddress =
    InetAddress.getByAddress(buf.copyOfRange(pos, pos + len))

/**
 * Reads a 16-bit unsigned int at pos in big endian, with no alignment requirements.
 */
fun getPortAt(buf: ByteArray, pos: Int): Int {
    return (buf[pos].toInt() and 0xff shl 8) + (buf[pos + 1].toInt() and 0xff)
}

fun setPortAt(port: Int, buf: ByteArray, pos: Int) {
    buf[pos] = (port ushr 8).toByte()
    buf[pos + 1] = (port and 0xff).toByte()
}

fun getAddressPositionAndLength(version: Int) = when (version) {
    4 -> PacketReflector.IPV4_ADDR_OFFSET to PacketReflector.IPV4_ADDR_LENGTH
    6 -> PacketReflector.IPV6_ADDR_OFFSET to PacketReflector.IPV6_ADDR_LENGTH
    else -> throw IllegalArgumentException("Unknown IP version $version")
}

private const val IPV4_CHKSUM_OFFSET = 10
private const val UDP_CHECKSUM_OFFSET = 6
private const val TCP_CHECKSUM_OFFSET = 16

fun fixPacketChecksum(buf: ByteArray, len: Int, version: Int, protocol: Byte) {
    // Fill Ip checksum for IPv4. IPv6 header doesn't have a checksum field.
    if (version == 4) {
        val checksum = IpUtils.ipChecksum(ByteBuffer.wrap(buf), 0)
        // Place checksum in Big-endian order.
        buf[IPV4_CHKSUM_OFFSET] = (checksum.toInt() ushr 8).toByte()
        buf[IPV4_CHKSUM_OFFSET + 1] = (checksum.toInt() and 0xff).toByte()
    }

    // Fill transport layer checksum.
    val transportOffset = if (version == 4) IPV4_HEADER_LENGTH else IPV6_HEADER_LENGTH
    when (protocol) {
        PacketReflector.IPPROTO_UDP -> {
            val checksumPos = transportOffset + UDP_CHECKSUM_OFFSET
            // Clear before calculate.
            buf[checksumPos + 1] = 0x00
            buf[checksumPos] = buf[checksumPos + 1]
            val checksum = IpUtils.udpChecksum(
                ByteBuffer.wrap(buf), 0,
                transportOffset
            )
            buf[checksumPos] = (checksum.toInt() ushr 8).toByte()
            buf[checksumPos + 1] = (checksum.toInt() and 0xff).toByte()
        }
        PacketReflector.IPPROTO_TCP -> {
            val checksumPos = transportOffset + TCP_CHECKSUM_OFFSET
            // Clear before calculate.
            buf[checksumPos + 1] = 0x00
            buf[checksumPos] = buf[checksumPos + 1]
            val transportLen: Int = len - transportOffset
            val checksum = IpUtils.tcpChecksum(
                ByteBuffer.wrap(buf), 0, transportOffset,
                transportLen
            )
            buf[checksumPos] = (checksum.toInt() ushr 8).toByte()
            buf[checksumPos + 1] = (checksum.toInt() and 0xff).toByte()
        }
        // TODO: Support ICMP.
        else -> throw IllegalArgumentException("Unsupported protocol: $protocol")
    }
}

fun swapBytes(buf: ByteArray, pos1: Int, pos2: Int, len: Int) {
    for (i in 0 until len) {
        val b = buf[pos1 + i]
        buf[pos1 + i] = buf[pos2 + i]
        buf[pos2 + i] = b
    }
}

fun swapAddresses(buf: ByteArray, version: Int) {
    val addrPos: Int
    val addrLen: Int
    when (version) {
        4 -> {
            addrPos = PacketReflector.IPV4_ADDR_OFFSET
            addrLen = PacketReflector.IPV4_ADDR_LENGTH
        }
        6 -> {
            addrPos = PacketReflector.IPV6_ADDR_OFFSET
            addrLen = PacketReflector.IPV6_ADDR_LENGTH
        }
        else -> throw java.lang.IllegalArgumentException()
    }
    swapBytes(buf, addrPos, addrPos + addrLen, addrLen)
}
