/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.networkstack.packets;

import static com.android.net.module.util.NetworkStackConstants.ETHER_HEADER_LEN;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ND_OPTION_SLLA;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_NS_HEADER_LEN;
import static com.android.net.module.util.NetworkStackConstants.IPV6_HEADER_LEN;

import android.net.MacAddress;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.net.module.util.Ipv6Utils;
import com.android.net.module.util.Struct;
import com.android.net.module.util.structs.EthernetHeader;
import com.android.net.module.util.structs.Icmpv6Header;
import com.android.net.module.util.structs.Ipv6Header;
import com.android.net.module.util.structs.LlaOption;
import com.android.net.module.util.structs.NsHeader;

import java.net.Inet6Address;
import java.nio.ByteBuffer;

/**
 * Defines basic data and operations needed to build and parse Neighbor Solicitation packet.
 *
 * @hide
 */
public class NeighborSolicitation {
    @NonNull
    public final EthernetHeader ethHdr;
    @NonNull
    public final Ipv6Header ipv6Hdr;
    @NonNull
    public final Icmpv6Header icmpv6Hdr;
    @NonNull
    public final NsHeader nsHdr;
    @Nullable
    public final LlaOption slla;

    public NeighborSolicitation(@NonNull final EthernetHeader ethHdr,
            @NonNull final Ipv6Header ipv6Hdr, @NonNull final Icmpv6Header icmpv6Hdr,
            @NonNull final NsHeader nsHdr, @Nullable final LlaOption slla) {
        this.ethHdr = ethHdr;
        this.ipv6Hdr = ipv6Hdr;
        this.icmpv6Hdr = icmpv6Hdr;
        this.nsHdr = nsHdr;
        this.slla = slla;
    }

    /**
     * Convert a Neighbor Solicitation instance to ByteBuffer.
     */
    public ByteBuffer toByteBuffer() {
        final int etherHeaderLen = Struct.getSize(EthernetHeader.class);
        final int ipv6HeaderLen = Struct.getSize(Ipv6Header.class);
        final int icmpv6HeaderLen = Struct.getSize(Icmpv6Header.class);
        final int nsHeaderLen = Struct.getSize(NsHeader.class);
        final int sllaOptionLen = (slla == null) ? 0 : Struct.getSize(LlaOption.class);
        final ByteBuffer packet = ByteBuffer.allocate(etherHeaderLen + ipv6HeaderLen
                + icmpv6HeaderLen + nsHeaderLen + sllaOptionLen);

        ethHdr.writeToByteBuffer(packet);
        ipv6Hdr.writeToByteBuffer(packet);
        icmpv6Hdr.writeToByteBuffer(packet);
        nsHdr.writeToByteBuffer(packet);
        if (slla != null) {
            slla.writeToByteBuffer(packet);
        }
        packet.flip();

        return packet;
    }

    /**
     * Build a Neighbor Solicitation packet from the required specified parameters.
     */
    public static ByteBuffer build(@NonNull final MacAddress srcMac,
            @NonNull final MacAddress dstMac, @NonNull final Inet6Address srcIp,
            @NonNull final Inet6Address dstIp, @NonNull final Inet6Address target) {
        final ByteBuffer slla = LlaOption.build((byte) ICMPV6_ND_OPTION_SLLA, srcMac);
        return Ipv6Utils.buildNsPacket(srcMac, dstMac, srcIp, dstIp, target, slla);
    }

    /**
     * Parse a Neighbor Solicitation packet from ByteBuffer.
     */
    public static NeighborSolicitation parse(@NonNull final byte[] recvbuf, final int length)
            throws ParseException {
        if (length < ETHER_HEADER_LEN + IPV6_HEADER_LEN + ICMPV6_NS_HEADER_LEN
                || recvbuf.length < length) {
            throw new ParseException("Invalid packet length: " + length);
        }
        final ByteBuffer packet = ByteBuffer.wrap(recvbuf, 0, length);

        // Parse each header and option in Neighbor Solicitation packet in order.
        final EthernetHeader ethHdr = Struct.parse(EthernetHeader.class, packet);
        final Ipv6Header ipv6Hdr = Struct.parse(Ipv6Header.class, packet);
        final Icmpv6Header icmpv6Hdr = Struct.parse(Icmpv6Header.class, packet);
        final NsHeader nsHdr = Struct.parse(NsHeader.class, packet);
        final LlaOption slla = (packet.remaining() == 0)
                ? null
                : Struct.parse(LlaOption.class, packet);

        return new NeighborSolicitation(ethHdr, ipv6Hdr, icmpv6Hdr, nsHdr, slla);
    }

    /**
     * Thrown when parsing Neighbor Solicitation packet failed.
     */
    public static class ParseException extends Exception {
        ParseException(String message) {
            super(message);
        }
    }
}
