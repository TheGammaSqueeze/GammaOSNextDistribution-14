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

import static android.system.OsConstants.ETH_P_IPV6;
import static android.system.OsConstants.IPPROTO_ICMPV6;

import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ND_OPTION_SLLA;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_NEIGHBOR_SOLICITATION;
import static com.android.testutils.MiscAsserts.assertThrows;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.net.InetAddresses;
import android.net.MacAddress;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet6Address;
import java.nio.ByteBuffer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class NeighborSolicitationTest {
    private static final Inet6Address TEST_SRC_ADDR =
            (Inet6Address) InetAddresses.parseNumericAddress("fe80::d419:d664:df38:2f65");
    private static final Inet6Address TEST_DST_ADDR =
            (Inet6Address) InetAddresses.parseNumericAddress("fe80::200:1a:1122:3344");
    private static final Inet6Address TEST_TARGET_ADDR =
            (Inet6Address) InetAddresses.parseNumericAddress("fe80::200:1a:1122:3344");
    private static final byte[] TEST_SOURCE_MAC_ADDR = new byte[] {
            (byte) 0x06, (byte) 0x5a, (byte) 0xac, (byte) 0x02, (byte) 0x61, (byte) 0x11,
    };
    private static final byte[] TEST_DST_MAC_ADDR = new byte[] {
            (byte) 0x00, (byte) 0x1a, (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44,
    };
    private static final byte[] TEST_NEIGHBOR_SOLICITATION = new byte[] {
        // dst mac address
        (byte) 0x00, (byte) 0x1a, (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44,
        // src mac address
        (byte) 0x06, (byte) 0x5a, (byte) 0xac, (byte) 0x02, (byte) 0x61, (byte) 0x11,
        // ether type
        (byte) 0x86, (byte) 0xdd,
        // version, priority and flow label
        (byte) 0x60, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        // length
        (byte) 0x00, (byte) 0x20,
        // next header
        (byte) 0x3a,
        // hop limit
        (byte) 0xff,
        // source address
        (byte) 0xfe, (byte) 0x80, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0xd4, (byte) 0x19, (byte) 0xd6, (byte) 0x64,
        (byte) 0xdf, (byte) 0x38, (byte) 0x2f, (byte) 0x65,
        // destination address
        (byte) 0xfe, (byte) 0x80, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x1a,
        (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44,
        // ICMP type, code, checksum
        (byte) 0x87, (byte) 0x00, (byte) 0x22, (byte) 0x96,
        // reserved
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        // target address
        (byte) 0xfe, (byte) 0x80, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x1a,
        (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44,
        // slla option
        (byte) 0x01, (byte) 0x01,
        // link-layer address
        (byte) 0x06, (byte) 0x5a, (byte) 0xac, (byte) 0x02,
        (byte) 0x61, (byte) 0x11,
    };
    private static final byte[] TEST_NEIGHBOR_SOLICITATION_WITHOUT_SLLA = new byte[] {
        // dst mac address
        (byte) 0x00, (byte) 0x1a, (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44,
        // src mac address
        (byte) 0x06, (byte) 0x5a, (byte) 0xac, (byte) 0x02, (byte) 0x61, (byte) 0x11,
        // ether type
        (byte) 0x86, (byte) 0xdd,
        // version, priority and flow label
        (byte) 0x60, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        // length
        (byte) 0x00, (byte) 0x20,
        // next header
        (byte) 0x3a,
        // hop limit
        (byte) 0xff,
        // source address
        (byte) 0xfe, (byte) 0x80, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0xd4, (byte) 0x19, (byte) 0xd6, (byte) 0x64,
        (byte) 0xdf, (byte) 0x38, (byte) 0x2f, (byte) 0x65,
        // destination address
        (byte) 0xfe, (byte) 0x80, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x1a,
        (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44,
        // ICMP type, code, checksum
        (byte) 0x87, (byte) 0x00, (byte) 0x22, (byte) 0x96,
        // reserved
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        // target address
        (byte) 0xfe, (byte) 0x80, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x1a,
        (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44,
    };
    private static final byte[] TEST_NEIGHBOR_SOLICITATION_LESS_LENGTH = new byte[] {
        // dst mac address
        (byte) 0x00, (byte) 0x1a, (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44,
        // src mac address
        (byte) 0x06, (byte) 0x5a, (byte) 0xac, (byte) 0x02, (byte) 0x61, (byte) 0x11,
        // ether type
        (byte) 0x86, (byte) 0xdd,
        // version, priority and flow label
        (byte) 0x60, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        // length
        (byte) 0x00, (byte) 0x20,
        // next header
        (byte) 0x3a,
        // hop limit
        (byte) 0xff,
        // source address
        (byte) 0xfe, (byte) 0x80, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0xd4, (byte) 0x19, (byte) 0xd6, (byte) 0x64,
        (byte) 0xdf, (byte) 0x38, (byte) 0x2f, (byte) 0x65,
        // destination address
        (byte) 0xfe, (byte) 0x80, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x1a,
        (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44,
        // ICMP type, code, checksum
        (byte) 0x87, (byte) 0x00, (byte) 0x22, (byte) 0x96,
        // reserved
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
    };
    private static final byte[] TEST_NEIGHBOR_SOLICITATION_TRUNCATED = new byte[] {
        // dst mac address
        (byte) 0x00, (byte) 0x1a, (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44,
        // src mac address
        (byte) 0x06, (byte) 0x5a, (byte) 0xac, (byte) 0x02, (byte) 0x61, (byte) 0x11,
        // ether type
        (byte) 0x86, (byte) 0xdd,
        // version, priority and flow label
        (byte) 0x60, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        // length
        (byte) 0x00, (byte) 0x20,
        // next header
        (byte) 0x3a,
        // hop limit
        (byte) 0xff,
        // source address
        (byte) 0xfe, (byte) 0x80, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0xd4, (byte) 0x19, (byte) 0xd6, (byte) 0x64,
        (byte) 0xdf, (byte) 0x38, (byte) 0x2f, (byte) 0x65,
        // destination address
        (byte) 0xfe, (byte) 0x80, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x1a,
        (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44,
        // ICMP type, code, checksum
        (byte) 0x87, (byte) 0x00, (byte) 0x22, (byte) 0x96,
        // reserved
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        // target address
        (byte) 0xfe, (byte) 0x80, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x1a,
        (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44,
        // slla option
        (byte) 0x01, (byte) 0x01,
        // truncatd link-layer address: 4bytes
        (byte) 0x06, (byte) 0x5a, (byte) 0xac, (byte) 0x02,
    };

    @Test
    public void testNeighborSolicitation_build() throws Exception {
        final ByteBuffer ns = NeighborSolicitation.build(
                MacAddress.fromBytes(TEST_SOURCE_MAC_ADDR),
                MacAddress.fromBytes(TEST_DST_MAC_ADDR),
                TEST_SRC_ADDR, TEST_DST_ADDR, TEST_TARGET_ADDR);
        assertArrayEquals(ns.array(), TEST_NEIGHBOR_SOLICITATION);
    }

    private void assertNeighborSolicitation(final NeighborSolicitation ns, boolean hasSllaOption) {
        assertArrayEquals(TEST_SOURCE_MAC_ADDR, ns.ethHdr.srcMac.toByteArray());
        assertArrayEquals(TEST_DST_MAC_ADDR, ns.ethHdr.dstMac.toByteArray());
        assertEquals(ETH_P_IPV6, ns.ethHdr.etherType);
        assertEquals(IPPROTO_ICMPV6, ns.ipv6Hdr.nextHeader);
        assertEquals(0xff, ns.ipv6Hdr.hopLimit);
        assertEquals(TEST_DST_ADDR, ns.ipv6Hdr.dstIp);
        assertEquals(TEST_SRC_ADDR, ns.ipv6Hdr.srcIp);
        assertEquals(ICMPV6_NEIGHBOR_SOLICITATION, ns.icmpv6Hdr.type);
        assertEquals(0, ns.icmpv6Hdr.code);
        assertEquals(TEST_TARGET_ADDR, ns.nsHdr.target);
        if (hasSllaOption) {
            assertEquals(ICMPV6_ND_OPTION_SLLA, ns.slla.type);
            assertEquals(1, ns.slla.length);
            assertEquals(MacAddress.fromBytes(TEST_SOURCE_MAC_ADDR), ns.slla.linkLayerAddress);
        }
    }

    @Test
    public void testNeighborSolicitation_parse() throws Exception {
        final NeighborSolicitation ns = NeighborSolicitation.parse(TEST_NEIGHBOR_SOLICITATION,
                TEST_NEIGHBOR_SOLICITATION.length);

        assertNeighborSolicitation(ns, true /* hasSllaOption */);
        assertArrayEquals(TEST_NEIGHBOR_SOLICITATION, ns.toByteBuffer().array());
    }

    @Test
    public void testNeighborSolicitation_parseWithoutSllaOption() throws Exception {
        final NeighborSolicitation ns =
                NeighborSolicitation.parse(TEST_NEIGHBOR_SOLICITATION_WITHOUT_SLLA,
                        TEST_NEIGHBOR_SOLICITATION_WITHOUT_SLLA.length);

        assertNeighborSolicitation(ns, false /* hasSllaOption */);
        assertArrayEquals(TEST_NEIGHBOR_SOLICITATION_WITHOUT_SLLA, ns.toByteBuffer().array());
    }

    @Test
    public void testNeighborSolicitation_invalidPacketLength() throws Exception {
        assertThrows(NeighborSolicitation.ParseException.class,
                () -> NeighborSolicitation.parse(TEST_NEIGHBOR_SOLICITATION, 0));
    }

    @Test
    public void testNeighborSolicitation_invalidByteBufferLength() throws Exception {
        assertThrows(NeighborSolicitation.ParseException.class,
                () -> NeighborSolicitation.parse(TEST_NEIGHBOR_SOLICITATION_TRUNCATED,
                                                  TEST_NEIGHBOR_SOLICITATION.length));
    }

    @Test
    public void testNeighborSolicitation_lessPacketLength() throws Exception {
        assertThrows(NeighborSolicitation.ParseException.class,
                () -> NeighborSolicitation.parse(TEST_NEIGHBOR_SOLICITATION_LESS_LENGTH,
                                                  TEST_NEIGHBOR_SOLICITATION_LESS_LENGTH.length));
    }

    @Test
    public void testNeighborSolicitation_truncatedPacket() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> NeighborSolicitation.parse(TEST_NEIGHBOR_SOLICITATION_TRUNCATED,
                                                  TEST_NEIGHBOR_SOLICITATION_TRUNCATED.length));
    }
}
