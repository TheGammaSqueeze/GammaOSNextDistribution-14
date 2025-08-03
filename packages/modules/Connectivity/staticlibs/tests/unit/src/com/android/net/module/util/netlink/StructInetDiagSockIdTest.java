/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.net.module.util.netlink;

import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.AF_INET6;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.net.InetAddresses;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StructInetDiagSockIdTest {
    private static final Inet4Address IPV4_SRC_ADDR =
            (Inet4Address) InetAddresses.parseNumericAddress("192.0.2.1");
    private static final Inet4Address IPV4_DST_ADDR =
            (Inet4Address) InetAddresses.parseNumericAddress("198.51.100.1");
    private static final Inet6Address IPV6_SRC_ADDR =
            (Inet6Address) InetAddresses.parseNumericAddress("2001:db8::1");
    private static final Inet6Address IPV6_DST_ADDR =
            (Inet6Address) InetAddresses.parseNumericAddress("2001:db8::2");
    private static final int SRC_PORT = 65297;
    private static final int DST_PORT = 443;
    private static final int IF_INDEX = 7;
    private static final long COOKIE = 561;

    private static final byte[] INET_DIAG_SOCKET_ID_IPV4 =
            new byte[] {
                    // src port, dst port
                    (byte) 0xff, (byte) 0x11, (byte) 0x01, (byte) 0xbb,
                    // src address
                    (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    // dst address
                    (byte) 0xc6, (byte) 0x33, (byte) 0x64, (byte) 0x01,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    // if index
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    // cookie
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff
            };

    private static final byte[] INET_DIAG_SOCKET_ID_IPV4_IF_COOKIE =
            new byte[] {
                    // src port, dst port
                    (byte) 0xff, (byte) 0x11, (byte) 0x01, (byte) 0xbb,
                    // src address
                    (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    // dst address
                    (byte) 0xc6, (byte) 0x33, (byte) 0x64, (byte) 0x01,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    // if index
                    (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    // cookie
                    (byte) 0x31, (byte) 0x02, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            };

    private static final byte[] INET_DIAG_SOCKET_ID_IPV6 =
            new byte[] {
                    // src port, dst port
                    (byte) 0xff, (byte) 0x11, (byte) 0x01, (byte) 0xbb,
                    // src address
                    (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                    // dst address
                    (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
                    // if index
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    // cookie
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff
            };

    private static final byte[] INET_DIAG_SOCKET_ID_IPV6_IF_COOKIE =
            new byte[] {
                    // src port, dst port
                    (byte) 0xff, (byte) 0x11, (byte) 0x01, (byte) 0xbb,
                    // src address
                    (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                    // dst address
                    (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
                    // if index
                    (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    // cookie
                    (byte) 0x31, (byte) 0x02, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            };

    @Test
    public void testPackStructInetDiagSockIdWithIpv4() {
        final InetSocketAddress srcAddr = new InetSocketAddress(IPV4_SRC_ADDR, SRC_PORT);
        final InetSocketAddress dstAddr = new InetSocketAddress(IPV4_DST_ADDR, DST_PORT);
        final StructInetDiagSockId sockId = new StructInetDiagSockId(srcAddr, dstAddr);
        final ByteBuffer buffer = ByteBuffer.allocate(StructInetDiagSockId.STRUCT_SIZE);
        sockId.pack(buffer);
        assertArrayEquals(INET_DIAG_SOCKET_ID_IPV4, buffer.array());
    }

    @Test
    public void testPackStructInetDiagSockIdWithIpv6() {
        final InetSocketAddress srcAddr = new InetSocketAddress(IPV6_SRC_ADDR, SRC_PORT);
        final InetSocketAddress dstAddr = new InetSocketAddress(IPV6_DST_ADDR, DST_PORT);
        final StructInetDiagSockId sockId = new StructInetDiagSockId(srcAddr, dstAddr);
        final ByteBuffer buffer = ByteBuffer.allocate(StructInetDiagSockId.STRUCT_SIZE);
        sockId.pack(buffer);
        assertArrayEquals(INET_DIAG_SOCKET_ID_IPV6, buffer.array());
    }

    @Test
    public void testPackStructInetDiagSockIdWithIpv4IfIndexCookie() {
        final InetSocketAddress srcAddr = new InetSocketAddress(IPV4_SRC_ADDR, SRC_PORT);
        final InetSocketAddress dstAddr = new InetSocketAddress(IPV4_DST_ADDR, DST_PORT);
        final StructInetDiagSockId sockId =
                new StructInetDiagSockId(srcAddr, dstAddr, IF_INDEX, COOKIE);
        final ByteBuffer buffer = ByteBuffer.allocate(StructInetDiagSockId.STRUCT_SIZE);
        sockId.pack(buffer);
        assertArrayEquals(INET_DIAG_SOCKET_ID_IPV4_IF_COOKIE, buffer.array());
    }

    @Test
    public void testPackStructInetDiagSockIdWithIpv6IfIndexCookie() {
        final InetSocketAddress srcAddr = new InetSocketAddress(IPV6_SRC_ADDR, SRC_PORT);
        final InetSocketAddress dstAddr = new InetSocketAddress(IPV6_DST_ADDR, DST_PORT);
        final StructInetDiagSockId sockId =
                new StructInetDiagSockId(srcAddr, dstAddr, IF_INDEX, COOKIE);
        final ByteBuffer buffer = ByteBuffer.allocate(StructInetDiagSockId.STRUCT_SIZE);
        sockId.pack(buffer);
        assertArrayEquals(INET_DIAG_SOCKET_ID_IPV6_IF_COOKIE, buffer.array());
    }

    @Test
    public void testParseStructInetDiagSockIdWithIpv4() {
        final ByteBuffer buffer = ByteBuffer.wrap(INET_DIAG_SOCKET_ID_IPV4_IF_COOKIE);
        final StructInetDiagSockId sockId = StructInetDiagSockId.parse(buffer, (byte) AF_INET);

        assertEquals(SRC_PORT, sockId.locSocketAddress.getPort());
        assertEquals(IPV4_SRC_ADDR, sockId.locSocketAddress.getAddress());
        assertEquals(DST_PORT, sockId.remSocketAddress.getPort());
        assertEquals(IPV4_DST_ADDR, sockId.remSocketAddress.getAddress());
        assertEquals(IF_INDEX, sockId.ifIndex);
        assertEquals(COOKIE, sockId.cookie);
    }

    @Test
    public void testParseStructInetDiagSockIdWithIpv6() {
        final ByteBuffer buffer = ByteBuffer.wrap(INET_DIAG_SOCKET_ID_IPV6_IF_COOKIE);
        final StructInetDiagSockId sockId = StructInetDiagSockId.parse(buffer, (byte) AF_INET6);

        assertEquals(SRC_PORT, sockId.locSocketAddress.getPort());
        assertEquals(IPV6_SRC_ADDR, sockId.locSocketAddress.getAddress());
        assertEquals(DST_PORT, sockId.remSocketAddress.getPort());
        assertEquals(IPV6_DST_ADDR, sockId.remSocketAddress.getAddress());
        assertEquals(IF_INDEX, sockId.ifIndex);
        assertEquals(COOKIE, sockId.cookie);
    }

    @Test
    public void testToStringStructInetDiagSockIdWithIpv4() {
        final InetSocketAddress srcAddr = new InetSocketAddress(IPV4_SRC_ADDR, SRC_PORT);
        final InetSocketAddress dstAddr = new InetSocketAddress(IPV4_DST_ADDR, DST_PORT);
        final StructInetDiagSockId sockId = new StructInetDiagSockId(srcAddr, dstAddr);
        assertEquals("StructInetDiagSockId{ idiag_sport{65297}, idiag_dport{443},"
                + " idiag_src{192.0.2.1}, idiag_dst{198.51.100.1}, idiag_if{0},"
                + " idiag_cookie{INET_DIAG_NOCOOKIE}}", sockId.toString());
    }

    @Test
    public void testToStringStructInetDiagSockIdWithIpv6() {
        final InetSocketAddress srcAddr = new InetSocketAddress(IPV6_SRC_ADDR, SRC_PORT);
        final InetSocketAddress dstAddr = new InetSocketAddress(IPV6_DST_ADDR, DST_PORT);
        final StructInetDiagSockId sockId = new StructInetDiagSockId(srcAddr, dstAddr);
        assertEquals("StructInetDiagSockId{ idiag_sport{65297}, idiag_dport{443},"
                + " idiag_src{2001:db8::1}, idiag_dst{2001:db8::2}, idiag_if{0},"
                + " idiag_cookie{INET_DIAG_NOCOOKIE}}", sockId.toString());
    }
}
