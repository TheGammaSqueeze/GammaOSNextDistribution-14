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

import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ND_OPTION_TLLA;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_NEIGHBOR_ADVERTISEMENT;
import static com.android.net.module.util.NetworkStackConstants.IPV6_ADDR_ALL_ROUTERS_MULTICAST;
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
public final class NeighborAdvertisementTest {
    private static final Inet6Address TEST_SRC_ADDR =
            (Inet6Address) InetAddresses.parseNumericAddress("fe80::dfd9:50a0:cc7b:7d6d");
    private static final Inet6Address TEST_TARGET_ADDR =
            (Inet6Address) InetAddresses.parseNumericAddress("2001:db8:1:0:c928:250d:b90c:3178");
    private static final byte[] TEST_SOURCE_MAC_ADDR = new byte[] {
            (byte) 0xea, (byte) 0xbe, (byte) 0x11, (byte) 0x25, (byte) 0xc1, (byte) 0x25
    };
    private static final byte[] TEST_DST_MAC_ADDR = new byte[] {
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
    };
    private static final byte[] TEST_GRATUITOUS_NA = new byte[] {
        // dst mac address
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
        // src mac address
        (byte) 0xea, (byte) 0xbe, (byte) 0x11, (byte) 0x25, (byte) 0xc1, (byte) 0x25,
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
        (byte) 0xdf, (byte) 0xd9, (byte) 0x50, (byte) 0xa0,
        (byte) 0xcc, (byte) 0x7b, (byte) 0x7d, (byte) 0x6d,
        // destination address
        (byte) 0xff, (byte) 0x02, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
        // ICMP type, code, checksum
        (byte) 0x88, (byte) 0x00, (byte) 0x3a, (byte) 0x3c,
        // flags
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        // target address
        (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
        (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00,
        (byte) 0xc9, (byte) 0x28, (byte) 0x25, (byte) 0x0d,
        (byte) 0xb9, (byte) 0x0c, (byte) 0x31, (byte) 0x78,
        // TLLA option
        (byte) 0x02, (byte) 0x01,
        // Link-Layer address
        (byte) 0xea, (byte) 0xbe, (byte) 0x11, (byte) 0x25, (byte) 0xc1, (byte) 0x25,
    };
    private static final byte[] TEST_GRATUITOUS_NA_WITHOUT_TLLA = new byte[] {
        // dst mac address
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
        // src mac address
        (byte) 0xea, (byte) 0xbe, (byte) 0x11, (byte) 0x25, (byte) 0xc1, (byte) 0x25,
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
        (byte) 0xdf, (byte) 0xd9, (byte) 0x50, (byte) 0xa0,
        (byte) 0xcc, (byte) 0x7b, (byte) 0x7d, (byte) 0x6d,
        // destination address
        (byte) 0xff, (byte) 0x02, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
        // ICMP type, code, checksum
        (byte) 0x88, (byte) 0x00, (byte) 0x3a, (byte) 0x3c,
        // flags
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        // target address
        (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
        (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00,
        (byte) 0xc9, (byte) 0x28, (byte) 0x25, (byte) 0x0d,
        (byte) 0xb9, (byte) 0x0c, (byte) 0x31, (byte) 0x78,
    };
    private static final byte[] TEST_GRATUITOUS_NA_LESS_LENGTH = new byte[] {
        // dst mac address
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
        // src mac address
        (byte) 0xea, (byte) 0xbe, (byte) 0x11, (byte) 0x25, (byte) 0xc1, (byte) 0x25,
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
        (byte) 0xdf, (byte) 0xd9, (byte) 0x50, (byte) 0xa0,
        (byte) 0xcc, (byte) 0x7b, (byte) 0x7d, (byte) 0x6d,
        // destination address
        (byte) 0xff, (byte) 0x02, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
        // ICMP type, code, checksum
        (byte) 0x88, (byte) 0x00, (byte) 0x3a, (byte) 0x3c,
        // flags
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
    };
    private static final byte[] TEST_GRATUITOUS_NA_TRUNCATED = new byte[] {
        // dst mac address
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
        // src mac address
        (byte) 0xea, (byte) 0xbe, (byte) 0x11, (byte) 0x25, (byte) 0xc1, (byte) 0x25,
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
        (byte) 0xdf, (byte) 0xd9, (byte) 0x50, (byte) 0xa0,
        (byte) 0xcc, (byte) 0x7b, (byte) 0x7d, (byte) 0x6d,
        // destination address
        (byte) 0xff, (byte) 0x02, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
        // ICMP type, code, checksum
        (byte) 0x88, (byte) 0x00, (byte) 0x3a, (byte) 0x3c,
        // flags
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        // target address
        (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
        (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00,
        (byte) 0xc9, (byte) 0x28, (byte) 0x25, (byte) 0x0d,
        (byte) 0xb9, (byte) 0x0c, (byte) 0x31, (byte) 0x78,
        // TLLA option
        (byte) 0x02, (byte) 0x01,
        // truncatd Link-Layer address: 4bytes
        (byte) 0xea, (byte) 0xbe, (byte) 0x11, (byte) 0x25,
    };

    @Test
    public void testGratuitousNa_build() throws Exception {
        final ByteBuffer na = NeighborAdvertisement.build(
                MacAddress.fromBytes(TEST_SOURCE_MAC_ADDR),
                MacAddress.fromBytes(TEST_DST_MAC_ADDR),
                TEST_SRC_ADDR, IPV6_ADDR_ALL_ROUTERS_MULTICAST, 0 /* flags */, TEST_TARGET_ADDR);
        assertArrayEquals(na.array(), TEST_GRATUITOUS_NA);
    }

    private void assertNeighborAdvertisement(final NeighborAdvertisement na,
            boolean hasTllaOption) {
        assertArrayEquals(TEST_SOURCE_MAC_ADDR, na.ethHdr.srcMac.toByteArray());
        assertArrayEquals(TEST_DST_MAC_ADDR, na.ethHdr.dstMac.toByteArray());
        assertEquals(ETH_P_IPV6, na.ethHdr.etherType);
        assertEquals(IPPROTO_ICMPV6, na.ipv6Hdr.nextHeader);
        assertEquals(0xff, na.ipv6Hdr.hopLimit);
        assertEquals(IPV6_ADDR_ALL_ROUTERS_MULTICAST, na.ipv6Hdr.dstIp);
        assertEquals(TEST_SRC_ADDR, na.ipv6Hdr.srcIp);
        assertEquals(ICMPV6_NEIGHBOR_ADVERTISEMENT, na.icmpv6Hdr.type);
        assertEquals(0, na.icmpv6Hdr.code);
        assertEquals(0, na.naHdr.flags);
        assertEquals(TEST_TARGET_ADDR, na.naHdr.target);
        if (hasTllaOption) {
            assertEquals(ICMPV6_ND_OPTION_TLLA, na.tlla.type);
            assertEquals(1, na.tlla.length);
            assertArrayEquals(TEST_SOURCE_MAC_ADDR, na.tlla.linkLayerAddress.toByteArray());
        }
    }

    @Test
    public void testGratuitousNa_parse() throws Exception {
        final NeighborAdvertisement na = NeighborAdvertisement.parse(TEST_GRATUITOUS_NA,
                TEST_GRATUITOUS_NA.length);

        assertNeighborAdvertisement(na, true /* hasTllaOption */);
        assertArrayEquals(TEST_GRATUITOUS_NA, na.toByteBuffer().array());
    }

    @Test
    public void testGratuitousNa_parseWithoutTllaOption() throws Exception {
        final NeighborAdvertisement na =
                NeighborAdvertisement.parse(TEST_GRATUITOUS_NA_WITHOUT_TLLA,
                        TEST_GRATUITOUS_NA_WITHOUT_TLLA.length);

        assertNeighborAdvertisement(na, false /* hasTllaOption */);
        assertArrayEquals(TEST_GRATUITOUS_NA_WITHOUT_TLLA, na.toByteBuffer().array());
    }

    @Test
    public void testGratuitousNa_zeroPacketLength() throws Exception {
        assertThrows(NeighborAdvertisement.ParseException.class,
                () -> NeighborAdvertisement.parse(TEST_GRATUITOUS_NA, 0));
    }

    @Test
    public void testGratuitousNa_invalidByteBufferLength() throws Exception {
        assertThrows(NeighborAdvertisement.ParseException.class,
                () -> NeighborAdvertisement.parse(TEST_GRATUITOUS_NA_TRUNCATED,
                                                  TEST_GRATUITOUS_NA.length));
    }

    @Test
    public void testGratuitousNa_lessPacketLength() throws Exception {
        assertThrows(NeighborAdvertisement.ParseException.class,
                () -> NeighborAdvertisement.parse(TEST_GRATUITOUS_NA_LESS_LENGTH,
                                                  TEST_GRATUITOUS_NA_LESS_LENGTH.length));
    }

    @Test
    public void testGratuitousNa_truncatedPacket() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> NeighborAdvertisement.parse(TEST_GRATUITOUS_NA_TRUNCATED,
                                                  TEST_GRATUITOUS_NA_TRUNCATED.length));
    }
}
