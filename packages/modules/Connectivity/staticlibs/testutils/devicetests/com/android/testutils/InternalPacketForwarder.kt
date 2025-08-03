/*
 * Copyright (C) 2024 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.testutils

import java.io.FileDescriptor

class InternalPacketForwarder(
    srcFd: FileDescriptor,
    mtu: Int,
    dstFd: FileDescriptor,
    forwardMap: Map<Int, Int>
) : PacketForwarderBase(srcFd, mtu, dstFd, forwardMap) {
    /**
     * Prepares a packet for forwarding by potentially updating the
     * destination port based on the specified port remapping rules.
     *
     * @param buf The packet data as a byte array.
     * @param version The IP version of the packet (e.g., 4 for IPv4).
     */
    override fun remapPort(buf: ByteArray, version: Int) {
        val transportOffset = getTransportOffset(version) + DESTINATION_PORT_OFFSET
        val extPort = getRemappedPort(buf, transportOffset)

        // Copy remapped destination port.
        if (extPort != 0) {
            setPortAt(extPort, buf, transportOffset)
        }
    }
}
