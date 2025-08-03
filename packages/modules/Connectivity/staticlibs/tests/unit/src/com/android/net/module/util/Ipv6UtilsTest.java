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

package com.android.net.module.util;

import static org.junit.Assert.assertEquals;

import android.net.InetAddresses;
import android.net.IpPrefix;
import android.net.MacAddress;
import android.system.OsConstants;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.structs.EthernetHeader;
import com.android.net.module.util.structs.Icmpv6Header;
import com.android.net.module.util.structs.Ipv6Header;
import com.android.net.module.util.structs.PrefixInformationOption;
import com.android.net.module.util.structs.RaHeader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet6Address;
import java.nio.ByteBuffer;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class Ipv6UtilsTest {

    private static final MacAddress MAC1 = MacAddress.fromString("11:22:33:44:55:66");
    private static final MacAddress MAC2 = MacAddress.fromString("aa:bb:cc:dd:ee:ff");
    private static final Inet6Address LINK_LOCAL = addr("fe80::1");
    private static final Inet6Address ROUTER_LINK_LOCAL = addr("fe80::cafe:d00d");
    private static final Inet6Address ALL_ROUTERS =
            NetworkStackConstants.IPV6_ADDR_ALL_ROUTERS_MULTICAST;
    private static final Inet6Address ALL_NODES =
            NetworkStackConstants.IPV6_ADDR_ALL_NODES_MULTICAST;

    @Test
    public void testBuildRsPacket() {
        ByteBuffer b = Ipv6Utils.buildRsPacket(MAC1, MAC2, LINK_LOCAL, ALL_ROUTERS /* no opts */);

        EthernetHeader eth = Struct.parse(EthernetHeader.class, b);
        assertEquals(MAC1, eth.srcMac);
        assertEquals(MAC2, eth.dstMac);

        Ipv6Header ipv6 = Struct.parse(Ipv6Header.class, b);
        assertEquals(255, ipv6.hopLimit);
        assertEquals(OsConstants.IPPROTO_ICMPV6, ipv6.nextHeader);
        assertEquals(LINK_LOCAL, ipv6.srcIp);
        assertEquals(ALL_ROUTERS, ipv6.dstIp);

        Icmpv6Header icmpv6 = Struct.parse(Icmpv6Header.class, b);
        assertEquals(NetworkStackConstants.ICMPV6_ROUTER_SOLICITATION, icmpv6.type);
        assertEquals(0, icmpv6.code);
    }

    @Test
    public void testBuildRaPacket() {
        final byte pioFlags =
                NetworkStackConstants.PIO_FLAG_AUTONOMOUS | NetworkStackConstants.PIO_FLAG_ON_LINK;
        ByteBuffer pio1 = PrefixInformationOption.build(new IpPrefix("2001:db8:1::/64"),
                pioFlags, 3600 /* validLifetime */, 1800 /* preferredLifetime */);
        ByteBuffer pio2 = PrefixInformationOption.build(new IpPrefix("fdcd:a17f:6502:1::/64"),
                pioFlags, 86400 /* validLifetime */, 86400 /* preferredLifetime */);

        ByteBuffer b = Ipv6Utils.buildRaPacket(MAC2, MAC1, ROUTER_LINK_LOCAL, ALL_NODES,
                (byte) 0 /* flags */, 7200 /* lifetime */,
                30_000 /* reachableTime */, 750 /* retransTimer */,
                pio1, pio2);

        EthernetHeader eth = Struct.parse(EthernetHeader.class, b);
        assertEquals(MAC2, eth.srcMac);
        assertEquals(MAC1, eth.dstMac);

        Ipv6Header ipv6 = Struct.parse(Ipv6Header.class, b);
        assertEquals(255, ipv6.hopLimit);
        assertEquals(OsConstants.IPPROTO_ICMPV6, ipv6.nextHeader);
        assertEquals(ROUTER_LINK_LOCAL, ipv6.srcIp);
        assertEquals(ALL_NODES, ipv6.dstIp);

        Icmpv6Header icmpv6 = Struct.parse(Icmpv6Header.class, b);
        assertEquals(NetworkStackConstants.ICMPV6_ROUTER_ADVERTISEMENT, icmpv6.type);
        assertEquals(0, icmpv6.code);

        RaHeader ra = Struct.parse(RaHeader.class, b);
        assertEquals(0, ra.hopLimit);  // Hop limit: unspecified.
        assertEquals(0, ra.flags);
        assertEquals(7200, ra.lifetime);
        assertEquals(30_000, ra.reachableTime);
        assertEquals(750, ra.retransTimer);

        PrefixInformationOption pio = Struct.parse(PrefixInformationOption.class, b);
        assertPioEquals(pio, "2001:db8:1::/64", pioFlags, 3600, 1800);
        pio = Struct.parse(PrefixInformationOption.class, b);
        assertPioEquals(pio, "fdcd:a17f:6502:1::/64", pioFlags, 86400, 86400);
    }

    @Test
    public void testBuildEchoRequestPacket() {
        final ByteBuffer b = Ipv6Utils.buildEchoRequestPacket(MAC2, MAC1, LINK_LOCAL, ALL_NODES);

        EthernetHeader eth = Struct.parse(EthernetHeader.class, b);
        assertEquals(MAC2, eth.srcMac);
        assertEquals(MAC1, eth.dstMac);

        Ipv6Header ipv6 = Struct.parse(Ipv6Header.class, b);
        assertEquals(255, ipv6.hopLimit);
        assertEquals(OsConstants.IPPROTO_ICMPV6, ipv6.nextHeader);
        assertEquals(LINK_LOCAL, ipv6.srcIp);
        assertEquals(ALL_NODES, ipv6.dstIp);

        Icmpv6Header icmpv6 = Struct.parse(Icmpv6Header.class, b);
        assertEquals(NetworkStackConstants.ICMPV6_ECHO_REQUEST_TYPE, icmpv6.type);
        assertEquals(0, icmpv6.code);
    }

    @Test
    public void testBuildEchoReplyPacket() {
        final ByteBuffer b = Ipv6Utils.buildEchoReplyPacket(LINK_LOCAL, ALL_NODES);

        Ipv6Header ipv6 = Struct.parse(Ipv6Header.class, b);
        assertEquals(255, ipv6.hopLimit);
        assertEquals(OsConstants.IPPROTO_ICMPV6, ipv6.nextHeader);
        assertEquals(LINK_LOCAL, ipv6.srcIp);
        assertEquals(ALL_NODES, ipv6.dstIp);

        Icmpv6Header icmpv6 = Struct.parse(Icmpv6Header.class, b);
        assertEquals(NetworkStackConstants.ICMPV6_ECHO_REPLY_TYPE, icmpv6.type);
        assertEquals(0, icmpv6.code);
    }

    private void assertPioEquals(PrefixInformationOption pio, String prefix, byte flags,
            long valid, long preferred) {
        assertEquals(NetworkStackConstants.ICMPV6_ND_OPTION_PIO, pio.type);
        assertEquals(4, pio.length);
        assertEquals(flags, pio.flags);
        assertEquals(valid, pio.validLifetime);
        assertEquals(preferred, pio.preferredLifetime);
        IpPrefix expected = new IpPrefix(prefix);
        IpPrefix actual = new IpPrefix(pio.prefix, pio.prefixLen);
        assertEquals(expected, actual);
    }

    private static Inet6Address addr(String addr) {
        return (Inet6Address) InetAddresses.parseNumericAddress(addr);
    }

    private byte[] slice(byte[] array, int length) {
        return Arrays.copyOf(array, length);
    }
}
