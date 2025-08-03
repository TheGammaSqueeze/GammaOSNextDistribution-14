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
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_NA_HEADER_LEN;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ND_OPTION_TLLA;
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
import com.android.net.module.util.structs.NaHeader;

import java.net.Inet6Address;
import java.nio.ByteBuffer;

/**
 * Defines basic data and operations needed to build and parse Neighbor Advertisement packet.
 *
 * @hide
 */
public class NeighborAdvertisement {
    @NonNull
    public final EthernetHeader ethHdr;
    @NonNull
    public final Ipv6Header ipv6Hdr;
    @NonNull
    public final Icmpv6Header icmpv6Hdr;
    @NonNull
    public final NaHeader naHdr;
    @Nullable
    public final LlaOption tlla;

    public NeighborAdvertisement(@NonNull final EthernetHeader ethHdr,
            @NonNull final Ipv6Header ipv6Hdr, @NonNull final Icmpv6Header icmpv6Hdr,
            @NonNull final NaHeader naHdr, @Nullable final LlaOption tlla) {
        this.ethHdr = ethHdr;
        this.ipv6Hdr = ipv6Hdr;
        this.icmpv6Hdr = icmpv6Hdr;
        this.naHdr = naHdr;
        this.tlla = tlla;
    }

    /**
     * Convert a Neighbor Advertisement instance to ByteBuffer.
     */
    public ByteBuffer toByteBuffer() {
        final int etherHeaderLen = Struct.getSize(EthernetHeader.class);
        final int ipv6HeaderLen = Struct.getSize(Ipv6Header.class);
        final int icmpv6HeaderLen = Struct.getSize(Icmpv6Header.class);
        final int naHeaderLen = Struct.getSize(NaHeader.class);
        final int tllaOptionLen = (tlla == null) ? 0 : Struct.getSize(LlaOption.class);
        final ByteBuffer packet = ByteBuffer.allocate(etherHeaderLen + ipv6HeaderLen
                + icmpv6HeaderLen + naHeaderLen + tllaOptionLen);

        ethHdr.writeToByteBuffer(packet);
        ipv6Hdr.writeToByteBuffer(packet);
        icmpv6Hdr.writeToByteBuffer(packet);
        naHdr.writeToByteBuffer(packet);
        if (tlla != null) {
            tlla.writeToByteBuffer(packet);
        }
        packet.flip();

        return packet;
    }

    /**
     * Build a Neighbor Advertisement packet from the required specified parameters.
     */
    public static ByteBuffer build(@NonNull final MacAddress srcMac,
            @NonNull final MacAddress dstMac, @NonNull final Inet6Address srcIp,
            @NonNull final Inet6Address dstIp, int flags, @NonNull final Inet6Address target) {
        final ByteBuffer tlla = LlaOption.build((byte) ICMPV6_ND_OPTION_TLLA, srcMac);
        return Ipv6Utils.buildNaPacket(srcMac, dstMac, srcIp, dstIp, flags, target, tlla);
    }

    /**
     * Parse a Neighbor Advertisement packet from ByteBuffer.
     */
    public static NeighborAdvertisement parse(@NonNull final byte[] recvbuf, final int length)
            throws ParseException {
        if (length < ETHER_HEADER_LEN + IPV6_HEADER_LEN + ICMPV6_NA_HEADER_LEN
                || recvbuf.length < length) {
            throw new ParseException("Invalid packet length: " + length);
        }
        final ByteBuffer packet = ByteBuffer.wrap(recvbuf, 0, length);

        // Parse each header and option in Neighbor Advertisement packet in order.
        final EthernetHeader ethHdr = Struct.parse(EthernetHeader.class, packet);
        final Ipv6Header ipv6Hdr = Struct.parse(Ipv6Header.class, packet);
        final Icmpv6Header icmpv6Hdr = Struct.parse(Icmpv6Header.class, packet);
        final NaHeader naHdr = Struct.parse(NaHeader.class, packet);
        final LlaOption tlla = (packet.remaining() == 0)
                ? null
                : Struct.parse(LlaOption.class, packet);

        return new NeighborAdvertisement(ethHdr, ipv6Hdr, icmpv6Hdr, naHdr, tlla);
    }

    /**
     * Thrown when parsing Neighbor Advertisement packet failed.
     */
    public static class ParseException extends Exception {
        ParseException(String message) {
            super(message);
        }
    }
}
