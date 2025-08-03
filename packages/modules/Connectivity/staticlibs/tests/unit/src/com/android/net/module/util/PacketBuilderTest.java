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

package com.android.net.module.util;

import static android.system.OsConstants.IPPROTO_IP;
import static android.system.OsConstants.IPPROTO_IPV6;
import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.IPPROTO_UDP;

import static com.android.net.module.util.NetworkStackConstants.ETHER_TYPE_IPV4;
import static com.android.net.module.util.NetworkStackConstants.ETHER_TYPE_IPV6;
import static com.android.net.module.util.NetworkStackConstants.IPV4_HEADER_MIN_LEN;
import static com.android.net.module.util.NetworkStackConstants.TCPHDR_ACK;
import static com.android.net.module.util.NetworkStackConstants.TCP_HEADER_MIN_LEN;
import static com.android.net.module.util.NetworkStackConstants.UDP_HEADER_LEN;
import static com.android.testutils.MiscAsserts.assertThrows;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.net.InetAddresses;
import android.net.MacAddress;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.structs.EthernetHeader;
import com.android.net.module.util.structs.Ipv4Header;
import com.android.net.module.util.structs.Ipv6Header;
import com.android.net.module.util.structs.TcpHeader;
import com.android.net.module.util.structs.UdpHeader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.nio.ByteBuffer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class PacketBuilderTest {
    private static final MacAddress SRC_MAC = MacAddress.fromString("11:22:33:44:55:66");
    private static final MacAddress DST_MAC = MacAddress.fromString("aa:bb:cc:dd:ee:ff");
    private static final Inet4Address IPV4_SRC_ADDR = addr4("192.0.2.1");
    private static final Inet4Address IPV4_DST_ADDR = addr4("198.51.100.1");
    private static final Inet6Address IPV6_SRC_ADDR = addr6("2001:db8::1");
    private static final Inet6Address IPV6_DST_ADDR = addr6("2001:db8::2");
    private static final short SRC_PORT = 9876;
    private static final short DST_PORT = 433;
    private static final short SEQ_NO = 13579;
    private static final short ACK_NO = 24680;
    private static final byte TYPE_OF_SERVICE = 0;
    private static final short ID = 27149;
    private static final short FLAGS_AND_FRAGMENT_OFFSET = (short) 0x4000; // flags=DF, offset=0
    private static final byte TIME_TO_LIVE = (byte) 0x40;
    private static final short WINDOW = (short) 0x2000;
    private static final short URGENT_POINTER = 0;
    // version=6, traffic class=0x80, flowlabel=0x515ca;
    private static final int VERSION_TRAFFICCLASS_FLOWLABEL = 0x680515ca;
    private static final short HOP_LIMIT = 0x40;
    private static final ByteBuffer DATA = ByteBuffer.wrap(new byte[] {
            (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef
    });

    private static final byte[] TEST_PACKET_ETHERHDR_IPV4HDR_TCPHDR =
            new byte[] {
                // packet = (scapy.Ether(src="11:22:33:44:55:66", dst="aa:bb:cc:dd:ee:ff",
                //                       type='IPv4') /
                //           scapy.IP(src="192.0.2.1", dst="198.51.100.1",
                //                    tos=0, id=27149, flags='DF') /
                //           scapy.TCP(sport=9876, dport=433, seq=13579, ack=24680,
                //                     flags='A', window=8192, urgptr=0))
                // Ether header
                (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd,
                (byte) 0xee, (byte) 0xff, (byte) 0x11, (byte) 0x22,
                (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x66,
                (byte) 0x08, (byte) 0x00,
                // IPv4 header
                (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x28,
                (byte) 0x6a, (byte) 0x0d, (byte) 0x40, (byte) 0x00,
                (byte) 0x40, (byte) 0x06, (byte) 0xe4, (byte) 0x8c,
                (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
                (byte) 0xc6, (byte) 0x33, (byte) 0x64, (byte) 0x01,
                // TCP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x00, (byte) 0x35, (byte) 0x0b,
                (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x68,
                (byte) 0x50, (byte) 0x10, (byte) 0x20, (byte) 0x00,
                (byte) 0xe5, (byte) 0xe5, (byte) 0x00, (byte) 0x00
            };

    private static final byte[] TEST_PACKET_ETHERHDR_IPV4HDR_TCPHDR_DATA =
            new byte[] {
                // packet = (scapy.Ether(src="11:22:33:44:55:66", dst="aa:bb:cc:dd:ee:ff",
                //                       type='IPv4') /
                //           scapy.IP(src="192.0.2.1", dst="198.51.100.1",
                //                    tos=0, id=27149, flags='DF') /
                //           scapy.TCP(sport=9876, dport=433, seq=13579, ack=24680,
                //                     flags='A', window=8192, urgptr=0) /
                //           b'\xde\xad\xbe\xef')
                // Ether header
                (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd,
                (byte) 0xee, (byte) 0xff, (byte) 0x11, (byte) 0x22,
                (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x66,
                (byte) 0x08, (byte) 0x00,
                // IPv4 header
                (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x2c,
                (byte) 0x6a, (byte) 0x0d, (byte) 0x40, (byte) 0x00,
                (byte) 0x40, (byte) 0x06, (byte) 0xe4, (byte) 0x88,
                (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
                (byte) 0xc6, (byte) 0x33, (byte) 0x64, (byte) 0x01,
                // TCP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x00, (byte) 0x35, (byte) 0x0b,
                (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x68,
                (byte) 0x50, (byte) 0x10, (byte) 0x20, (byte) 0x00,
                (byte) 0x48, (byte) 0x44, (byte) 0x00, (byte) 0x00,
                // Data
                (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef
            };

    private static final byte[] TEST_PACKET_IPV4HDR_TCPHDR =
            new byte[] {
                // packet = (scapy.IP(src="192.0.2.1", dst="198.51.100.1",
                //                    tos=0, id=27149, flags='DF') /
                //           scapy.TCP(sport=9876, dport=433, seq=13579, ack=24680,
                //                     flags='A', window=8192, urgptr=0))
                // IPv4 header
                (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x28,
                (byte) 0x6a, (byte) 0x0d, (byte) 0x40, (byte) 0x00,
                (byte) 0x40, (byte) 0x06, (byte) 0xe4, (byte) 0x8c,
                (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
                (byte) 0xc6, (byte) 0x33, (byte) 0x64, (byte) 0x01,
                // TCP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x00, (byte) 0x35, (byte) 0x0b,
                (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x68,
                (byte) 0x50, (byte) 0x10, (byte) 0x20, (byte) 0x00,
                (byte) 0xe5, (byte) 0xe5, (byte) 0x00, (byte) 0x00
            };

    private static final byte[] TEST_PACKET_IPV4HDR_TCPHDR_DATA =
            new byte[] {
                // packet = (scapy.IP(src="192.0.2.1", dst="198.51.100.1",
                //                    tos=0, id=27149, flags='DF') /
                //           scapy.TCP(sport=9876, dport=433, seq=13579, ack=24680,
                //                     flags='A', window=8192, urgptr=0) /
                //           b'\xde\xad\xbe\xef')
                // IPv4 header
                (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x2c,
                (byte) 0x6a, (byte) 0x0d, (byte) 0x40, (byte) 0x00,
                (byte) 0x40, (byte) 0x06, (byte) 0xe4, (byte) 0x88,
                (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
                (byte) 0xc6, (byte) 0x33, (byte) 0x64, (byte) 0x01,
                // TCP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x00, (byte) 0x35, (byte) 0x0b,
                (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x68,
                (byte) 0x50, (byte) 0x10, (byte) 0x20, (byte) 0x00,
                (byte) 0x48, (byte) 0x44, (byte) 0x00, (byte) 0x00,
                // Data
                (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef
            };

    private static final byte[] TEST_PACKET_ETHERHDR_IPV4HDR_UDPHDR =
            new byte[] {
                // packet = (scapy.Ether(src="11:22:33:44:55:66", dst="aa:bb:cc:dd:ee:ff",
                //                 type='IPv4') /
                //           scapy.IP(src="192.0.2.1", dst="198.51.100.1",
                //                 tos=0, id=27149, flags='DF') /
                //           scapy.UDP(sport=9876, dport=433))
                // Ether header
                (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd,
                (byte) 0xee, (byte) 0xff, (byte) 0x11, (byte) 0x22,
                (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x66,
                (byte) 0x08, (byte) 0x00,
                // IP header
                (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x1c,
                (byte) 0x6a, (byte) 0x0d, (byte) 0x40, (byte) 0x00,
                (byte) 0x40, (byte) 0x11, (byte) 0xe4, (byte) 0x8d,
                (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
                (byte) 0xc6, (byte) 0x33, (byte) 0x64, (byte) 0x01,
                // UDP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x08, (byte) 0xeb, (byte) 0x62
            };

    private static final byte[] TEST_PACKET_ETHERHDR_IPV4HDR_UDPHDR_DATA =
            new byte[] {
                // packet = (scapy.Ether(src="11:22:33:44:55:66", dst="aa:bb:cc:dd:ee:ff",
                //                 type='IPv4') /
                //           scapy.IP(src="192.0.2.1", dst="198.51.100.1",
                //                 tos=0, id=27149, flags='DF') /
                //           scapy.UDP(sport=9876, dport=433) /
                //           b'\xde\xad\xbe\xef')
                // Ether header
                (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd,
                (byte) 0xee, (byte) 0xff, (byte) 0x11, (byte) 0x22,
                (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x66,
                (byte) 0x08, (byte) 0x00,
                // IP header
                (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x20,
                (byte) 0x6a, (byte) 0x0d, (byte) 0x40, (byte) 0x00,
                (byte) 0x40, (byte) 0x11, (byte) 0xe4, (byte) 0x89,
                (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
                (byte) 0xc6, (byte) 0x33, (byte) 0x64, (byte) 0x01,
                // UDP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x0c, (byte) 0x4d, (byte) 0xbd,
                // Data
                (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef
            };

    private static final byte[] TEST_PACKET_IPV4HDR_UDPHDR =
            new byte[] {
                // packet = (scapy.IP(src="192.0.2.1", dst="198.51.100.1",
                //                 tos=0, id=27149, flags='DF') /
                //           scapy.UDP(sport=9876, dport=433))
                // IP header
                (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x1c,
                (byte) 0x6a, (byte) 0x0d, (byte) 0x40, (byte) 0x00,
                (byte) 0x40, (byte) 0x11, (byte) 0xe4, (byte) 0x8d,
                (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
                (byte) 0xc6, (byte) 0x33, (byte) 0x64, (byte) 0x01,
                // UDP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x08, (byte) 0xeb, (byte) 0x62
            };

    private static final byte[] TEST_PACKET_IPV4HDR_UDPHDR_DATA =
            new byte[] {
                // packet = (scapy.IP(src="192.0.2.1", dst="198.51.100.1",
                //                 tos=0, id=27149, flags='DF') /
                //           scapy.UDP(sport=9876, dport=433) /
                //           b'\xde\xad\xbe\xef')
                // IP header
                (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x20,
                (byte) 0x6a, (byte) 0x0d, (byte) 0x40, (byte) 0x00,
                (byte) 0x40, (byte) 0x11, (byte) 0xe4, (byte) 0x89,
                (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
                (byte) 0xc6, (byte) 0x33, (byte) 0x64, (byte) 0x01,
                // UDP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x0c, (byte) 0x4d, (byte) 0xbd,
                // Data
                (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef
            };

    private static final byte[] TEST_PACKET_ETHERHDR_IPV6HDR_UDPHDR =
            new byte[] {
                // packet = (scapy.Ether(src="11:22:33:44:55:66", dst="aa:bb:cc:dd:ee:ff",
                //                     type='IPv6') /
                //           scapy.IPv6(src="2001:db8::1", dst="2001:db8::2", tc=0x80,
                //                      fl=0x515ca, hlim=0x40) /
                //           scapy.UDP(sport=9876, dport=433))
                // Ether header
                (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd,
                (byte) 0xee, (byte) 0xff, (byte) 0x11, (byte) 0x22,
                (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x66,
                (byte) 0x86, (byte) 0xdd,
                // IP header
                (byte) 0x68, (byte) 0x05, (byte) 0x15, (byte) 0xca,
                (byte) 0x00, (byte) 0x08, (byte) 0x11, (byte) 0x40,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
                // UDP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x08, (byte) 0x7c, (byte) 0x24
            };

    private static final byte[] TEST_PACKET_ETHERHDR_IPV6HDR_UDPHDR_DATA =
            new byte[] {
                // packet = (scapy.Ether(src="11:22:33:44:55:66", dst="aa:bb:cc:dd:ee:ff",
                //                       type='IPv6') /
                //           scapy.IPv6(src="2001:db8::1", dst="2001:db8::2", tc=0x80,
                //                      fl=0x515ca, hlim=0x40) /
                //           scapy.UDP(sport=9876, dport=433) /
                //           b'\xde\xad\xbe\xef')
                // Ether header
                (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd,
                (byte) 0xee, (byte) 0xff, (byte) 0x11, (byte) 0x22,
                (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x66,
                (byte) 0x86, (byte) 0xdd,
                // IP header
                (byte) 0x68, (byte) 0x05, (byte) 0x15, (byte) 0xca,
                (byte) 0x00, (byte) 0x0c, (byte) 0x11, (byte) 0x40,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
                // UDP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x0c, (byte) 0xde, (byte) 0x7e,
                // Data
                (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef
            };

    private static final byte[] TEST_PACKET_ETHERHDR_IPV6HDR_TCPHDR_DATA =
            new byte[] {
                // packet = (scapy.Ether(src="11:22:33:44:55:66", dst="aa:bb:cc:dd:ee:ff",
                //                       type='IPv6') /
                //           scapy.IPv6(src="2001:db8::1", dst="2001:db8::2", tc=0x80,
                //                      fl=0x515ca, hlim=0x40) /
                //           scapy.TCP(sport=9876, dport=433, seq=13579, ack=24680,
                //                     flags='A', window=8192, urgptr=0) /
                //           b'\xde\xad\xbe\xef')
                // Ether header
                (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd,
                (byte) 0xee, (byte) 0xff, (byte) 0x11, (byte) 0x22,
                (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x66,
                (byte) 0x86, (byte) 0xdd,
                // IPv6 header
                (byte) 0x68, (byte) 0x05, (byte) 0x15, (byte) 0xca,
                (byte) 0x00, (byte) 0x18, (byte) 0x06, (byte) 0x40,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
                // TCP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x00, (byte) 0x35, (byte) 0x0b,
                (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x68,
                (byte) 0x50, (byte) 0x10, (byte) 0x20, (byte) 0x00,
                (byte) 0xd9, (byte) 0x05, (byte) 0x00, (byte) 0x00,
                // Data
                (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef
            };

    private static final byte[] TEST_PACKET_ETHERHDR_IPV6HDR_TCPHDR =
            new byte[] {
                // packet = (scapy.Ether(src="11:22:33:44:55:66", dst="aa:bb:cc:dd:ee:ff",
                //                       type='IPv6') /
                //           scapy.IPv6(src="2001:db8::1", dst="2001:db8::2", tc=0x80,
                //                      fl=0x515ca, hlim=0x40) /
                //           scapy.TCP(sport=9876, dport=433, seq=13579, ack=24680,
                //                     flags='A', window=8192, urgptr=0))
                // Ether header
                (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd,
                (byte) 0xee, (byte) 0xff, (byte) 0x11, (byte) 0x22,
                (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x66,
                (byte) 0x86, (byte) 0xdd,
                // IPv6 header
                (byte) 0x68, (byte) 0x05, (byte) 0x15, (byte) 0xca,
                (byte) 0x00, (byte) 0x14, (byte) 0x06, (byte) 0x40,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
                // TCP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x00, (byte) 0x35, (byte) 0x0b,
                (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x68,
                (byte) 0x50, (byte) 0x10, (byte) 0x20, (byte) 0x00,
                (byte) 0x76, (byte) 0xa7, (byte) 0x00, (byte) 0x00
            };

    private static final byte[] TEST_PACKET_IPV6HDR_TCPHDR_DATA =
            new byte[] {
                // packet = (scapy.IPv6(src="2001:db8::1", dst="2001:db8::2", tc=0x80,
                //                      fl=0x515ca, hlim=0x40) /
                //           scapy.TCP(sport=9876, dport=433, seq=13579, ack=24680,
                //                     flags='A', window=8192, urgptr=0) /
                //           b'\xde\xad\xbe\xef')
                // IPv6 header
                (byte) 0x68, (byte) 0x05, (byte) 0x15, (byte) 0xca,
                (byte) 0x00, (byte) 0x18, (byte) 0x06, (byte) 0x40,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
                // TCP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x00, (byte) 0x35, (byte) 0x0b,
                (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x68,
                (byte) 0x50, (byte) 0x10, (byte) 0x20, (byte) 0x00,
                (byte) 0xd9, (byte) 0x05, (byte) 0x00, (byte) 0x00,
                // Data
                (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef
            };

    private static final byte[] TEST_PACKET_IPV6HDR_TCPHDR =
            new byte[] {
                // packet = (scapy.IPv6(src="2001:db8::1", dst="2001:db8::2", tc=0x80,
                //                      fl=0x515ca, hlim=0x40) /
                //           scapy.TCP(sport=9876, dport=433, seq=13579, ack=24680,
                //                     flags='A', window=8192, urgptr=0))
                // IPv6 header
                (byte) 0x68, (byte) 0x05, (byte) 0x15, (byte) 0xca,
                (byte) 0x00, (byte) 0x14, (byte) 0x06, (byte) 0x40,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
                // TCP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x00, (byte) 0x35, (byte) 0x0b,
                (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x68,
                (byte) 0x50, (byte) 0x10, (byte) 0x20, (byte) 0x00,
                (byte) 0x76, (byte) 0xa7, (byte) 0x00, (byte) 0x00
            };

    private static final byte[] TEST_PACKET_IPV6HDR_UDPHDR =
            new byte[] {
                // packet = (scapy.IPv6(src="2001:db8::1", dst="2001:db8::2", tc=0x80,
                //                      fl=0x515ca, hlim=0x40) /
                //           scapy.UDP(sport=9876, dport=433))
                // IP header
                (byte) 0x68, (byte) 0x05, (byte) 0x15, (byte) 0xca,
                (byte) 0x00, (byte) 0x08, (byte) 0x11, (byte) 0x40,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
                // UDP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x08, (byte) 0x7c, (byte) 0x24
            };

    private static final byte[] TEST_PACKET_IPV6HDR_UDPHDR_DATA =
            new byte[] {
                // packet = (scapy.IPv6(src="2001:db8::1", dst="2001:db8::2", tc=0x80,
                //                      fl=0x515ca, hlim=0x40) /
                //           scapy.UDP(sport=9876, dport=433) /
                //           b'\xde\xad\xbe\xef')
                // IP header
                (byte) 0x68, (byte) 0x05, (byte) 0x15, (byte) 0xca,
                (byte) 0x00, (byte) 0x0c, (byte) 0x11, (byte) 0x40,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
                // UDP header
                (byte) 0x26, (byte) 0x94, (byte) 0x01, (byte) 0xb1,
                (byte) 0x00, (byte) 0x0c, (byte) 0xde, (byte) 0x7e,
                // Data
                (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef
            };

    /**
     * Build a packet which has ether header, IP header, TCP/UDP header and data.
     * The ethernet header and data are optional. Note that both source mac address and
     * destination mac address are required for ethernet header.
     *
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                Layer 2 header (EthernetHeader)                | (optional)
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |           Layer 3 header (Ipv4Header, Ipv6Header)             |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |           Layer 4 header (TcpHeader, UdpHeader)               |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                          Payload                              | (optional)
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     * @param srcMac source MAC address. used by L2 ether header.
     * @param dstMac destination MAC address. used by L2 ether header.
     * @param l3proto the layer 3 protocol. Only {@code IPPROTO_IP} and {@code IPPROTO_IPV6}
     *        currently supported.
     * @param l4proto the layer 4 protocol. Only {@code IPPROTO_TCP} and {@code IPPROTO_UDP}
     *        currently supported.
     * @param payload the payload.
     */
    @NonNull
    private ByteBuffer buildPacket(@Nullable final MacAddress srcMac,
            @Nullable final MacAddress dstMac, final int l3proto, final int l4proto,
            @Nullable final ByteBuffer payload)
            throws Exception {
        if (l3proto != IPPROTO_IP && l3proto != IPPROTO_IPV6) {
            fail("Unsupported layer 3 protocol " + l3proto);
        }

        if (l4proto != IPPROTO_TCP && l4proto != IPPROTO_UDP) {
            fail("Unsupported layer 4 protocol " + l4proto);
        }

        final boolean hasEther = (srcMac != null && dstMac != null);
        final int payloadLen = (payload == null) ? 0 : payload.limit();
        final ByteBuffer buffer = PacketBuilder.allocate(hasEther, l3proto, l4proto,
                payloadLen);
        final PacketBuilder packetBuilder = new PacketBuilder(buffer);

        // [1] Build ether header.
        if (hasEther) {
            final int etherType = (l3proto == IPPROTO_IP) ? ETHER_TYPE_IPV4 : ETHER_TYPE_IPV6;
            packetBuilder.writeL2Header(srcMac, dstMac, (short) etherType);
        }

        // [2] Build IP header.
        if (l3proto == IPPROTO_IP) {
            packetBuilder.writeIpv4Header(TYPE_OF_SERVICE, ID, FLAGS_AND_FRAGMENT_OFFSET,
                    TIME_TO_LIVE, (byte) l4proto, IPV4_SRC_ADDR, IPV4_DST_ADDR);
        } else if (l3proto == IPPROTO_IPV6) {
            packetBuilder.writeIpv6Header(VERSION_TRAFFICCLASS_FLOWLABEL,
                    (byte) l4proto, HOP_LIMIT, IPV6_SRC_ADDR, IPV6_DST_ADDR);
        }

        // [3] Build TCP or UDP header.
        if (l4proto == IPPROTO_TCP) {
            packetBuilder.writeTcpHeader(SRC_PORT, DST_PORT, SEQ_NO, ACK_NO,
                    TCPHDR_ACK, WINDOW, URGENT_POINTER);
        } else if (l4proto == IPPROTO_UDP) {
            packetBuilder.writeUdpHeader(SRC_PORT, DST_PORT);
        }

        // [4] Build payload.
        if (payload != null) {
            buffer.put(payload);
            // in case data might be reused by caller, restore the position and
            // limit of bytebuffer.
            payload.clear();
        }

        return packetBuilder.finalizePacket();
    }

    /**
     * Check ethernet header.
     *
     * @param l3proto the layer 3 protocol. Only {@code IPPROTO_IP} and {@code IPPROTO_IPV6}
     *        currently supported.
     * @param actual the packet to check.
     */
    private void checkEtherHeader(final int l3proto, final ByteBuffer actual) {
        if (l3proto != IPPROTO_IP && l3proto != IPPROTO_IPV6) {
            fail("Unsupported layer 3 protocol " + l3proto);
        }

        final EthernetHeader eth = Struct.parse(EthernetHeader.class, actual);
        assertEquals(SRC_MAC, eth.srcMac);
        assertEquals(DST_MAC, eth.dstMac);
        final int expectedEtherType = (l3proto == IPPROTO_IP) ? ETHER_TYPE_IPV4 : ETHER_TYPE_IPV6;
        assertEquals(expectedEtherType, eth.etherType);
    }

    /**
     * Check IPv4 header.
     *
     * @param l4proto the layer 4 protocol. Only {@code IPPROTO_TCP} and {@code IPPROTO_UDP}
     *        currently supported.
     * @param hasData true if the packet has data payload; false otherwise.
     * @param actual the packet to check.
     */
    private void checkIpv4Header(final int l4proto, final boolean hasData,
            final ByteBuffer actual) {
        if (l4proto != IPPROTO_TCP && l4proto != IPPROTO_UDP) {
            fail("Unsupported layer 4 protocol " + l4proto);
        }

        final Ipv4Header ipv4Header = Struct.parse(Ipv4Header.class, actual);
        assertEquals(Ipv4Header.IPHDR_VERSION_IHL, ipv4Header.vi);
        assertEquals(TYPE_OF_SERVICE, ipv4Header.tos);
        assertEquals(ID, ipv4Header.id);
        assertEquals(FLAGS_AND_FRAGMENT_OFFSET, ipv4Header.flagsAndFragmentOffset);
        assertEquals(TIME_TO_LIVE, ipv4Header.ttl);
        assertEquals(IPV4_SRC_ADDR, ipv4Header.srcIp);
        assertEquals(IPV4_DST_ADDR, ipv4Header.dstIp);

        final int dataLength = hasData ? DATA.limit() : 0;
        if (l4proto == IPPROTO_TCP) {
            assertEquals(IPV4_HEADER_MIN_LEN + TCP_HEADER_MIN_LEN + dataLength,
                    ipv4Header.totalLength);
            assertEquals((byte) IPPROTO_TCP, ipv4Header.protocol);
            assertEquals(hasData ? (short) 0xe488 : (short) 0xe48c, ipv4Header.checksum);
        } else if (l4proto == IPPROTO_UDP) {
            assertEquals(IPV4_HEADER_MIN_LEN + UDP_HEADER_LEN + dataLength,
                    ipv4Header.totalLength);
            assertEquals((byte) IPPROTO_UDP, ipv4Header.protocol);
            assertEquals(hasData ? (short) 0xe489 : (short) 0xe48d, ipv4Header.checksum);
        }
    }

    /**
     * Check IPv6 header.
     *
     * @param l4proto the layer 4 protocol. Only {@code IPPROTO_TCP} and {@code IPPROTO_UDP}
     *        currently supported.
     * @param hasData true if the packet has data payload; false otherwise.
     * @param actual the packet to check.
     */
    private void checkIpv6Header(final int l4proto, final boolean hasData,
            final ByteBuffer actual) {
        if (l4proto != IPPROTO_TCP && l4proto != IPPROTO_UDP) {
            fail("Unsupported layer 4 protocol " + l4proto);
        }

        final Ipv6Header ipv6Header = Struct.parse(Ipv6Header.class, actual);

        assertEquals(VERSION_TRAFFICCLASS_FLOWLABEL, ipv6Header.vtf);
        assertEquals(HOP_LIMIT, ipv6Header.hopLimit);
        assertEquals(IPV6_SRC_ADDR, ipv6Header.srcIp);
        assertEquals(IPV6_DST_ADDR, ipv6Header.dstIp);

        final int dataLength = hasData ? DATA.limit() : 0;
        if (l4proto == IPPROTO_TCP) {
            assertEquals(TCP_HEADER_MIN_LEN + dataLength, ipv6Header.payloadLength);
            assertEquals((byte) IPPROTO_TCP, ipv6Header.nextHeader);
        } else if (l4proto == IPPROTO_UDP) {
            assertEquals(UDP_HEADER_LEN + dataLength, ipv6Header.payloadLength);
            assertEquals((byte) IPPROTO_UDP, ipv6Header.nextHeader);
        }
    }

    /**
     * Check TCP packet.
     *
     * @param hasEther true if the packet has ether header; false otherwise.
     * @param l3proto the layer 3 protocol. Only {@code IPPROTO_IP} and {@code IPPROTO_IPV6}
     *        currently supported.
     * @param hasData true if the packet has data payload; false otherwise.
     * @param actual the packet to check.
     */
    private void checkTcpPacket(final boolean hasEther, final int l3proto, final boolean hasData,
            final ByteBuffer actual) {
        if (l3proto != IPPROTO_IP && l3proto != IPPROTO_IPV6) {
            fail("Unsupported layer 3 protocol " + l3proto);
        }

        // [1] Check ether header.
        if (hasEther) {
            checkEtherHeader(l3proto, actual);
        }

        // [2] Check IP header.
        if (l3proto == IPPROTO_IP) {
            checkIpv4Header(IPPROTO_TCP, hasData, actual);
        } else if (l3proto == IPPROTO_IPV6) {
            checkIpv6Header(IPPROTO_TCP, hasData, actual);
        }

        // [3] Check TCP header.
        final TcpHeader tcpHeader = Struct.parse(TcpHeader.class, actual);
        assertEquals(SRC_PORT, tcpHeader.srcPort);
        assertEquals(DST_PORT, tcpHeader.dstPort);
        assertEquals(SEQ_NO, tcpHeader.seq);
        assertEquals(ACK_NO, tcpHeader.ack);
        assertEquals((short) 0x5010 /* offset=5(*4bytes), control bits=ACK */,
                tcpHeader.dataOffsetAndControlBits);
        assertEquals(WINDOW, tcpHeader.window);
        assertEquals(URGENT_POINTER, tcpHeader.urgentPointer);
        if (l3proto == IPPROTO_IP) {
            assertEquals(hasData ? (short) 0x4844 : (short) 0xe5e5, tcpHeader.checksum);
        } else if (l3proto == IPPROTO_IPV6) {
            assertEquals(hasData ? (short) 0xd905 : (short) 0x76a7, tcpHeader.checksum);
        }

        // [4] Check payload.
        if (hasData) {
            assertEquals(0xdeadbeef, actual.getInt());
        }
    }

    /**
     * Check UDP packet.
     *
     * @param hasEther true if the packet has ether header; false otherwise.
     * @param l3proto the layer 3 protocol. Only {@code IPPROTO_IP} and {@code IPPROTO_IPV6}
     *        currently supported.
     * @param hasData true if the packet has data payload; false otherwise.
     * @param actual the packet to check.
     */
    private void checkUdpPacket(final boolean hasEther, final int l3proto, final boolean hasData,
            final ByteBuffer actual) {
        if (l3proto != IPPROTO_IP && l3proto != IPPROTO_IPV6) {
            fail("Unsupported layer 3 protocol " + l3proto);
        }

        // [1] Check ether header.
        if (hasEther) {
            checkEtherHeader(l3proto, actual);
        }

        // [2] Check IP header.
        if (l3proto == IPPROTO_IP) {
            checkIpv4Header(IPPROTO_UDP, hasData, actual);
        } else if (l3proto == IPPROTO_IPV6) {
            checkIpv6Header(IPPROTO_UDP, hasData, actual);
        }

        // [3] Check UDP header.
        final UdpHeader udpHeader = Struct.parse(UdpHeader.class, actual);
        assertEquals(SRC_PORT, udpHeader.srcPort);
        assertEquals(DST_PORT, udpHeader.dstPort);
        final int dataLength = hasData ? DATA.limit() : 0;
        assertEquals(UDP_HEADER_LEN + dataLength, udpHeader.length);
        if (l3proto == IPPROTO_IP) {
            assertEquals(hasData ? (short) 0x4dbd : (short) 0xeb62, udpHeader.checksum);
        } else if (l3proto == IPPROTO_IPV6) {
            assertEquals(hasData ? (short) 0xde7e : (short) 0x7c24, udpHeader.checksum);
        }

        // [4] Check payload.
        if (hasData) {
            assertEquals(0xdeadbeef, actual.getInt());
        }
    }

    @Test
    public void testBuildPacketEtherIPv4Tcp() throws Exception {
        final ByteBuffer packet = buildPacket(SRC_MAC, DST_MAC, IPPROTO_IP, IPPROTO_TCP,
                null /* data */);
        checkTcpPacket(true /* hasEther */, IPPROTO_IP, false /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_ETHERHDR_IPV4HDR_TCPHDR, packet.array());
    }

    @Test
    public void testBuildPacketEtherIPv4TcpData() throws Exception {
        final ByteBuffer packet = buildPacket(SRC_MAC, DST_MAC, IPPROTO_IP, IPPROTO_TCP, DATA);
        checkTcpPacket(true /* hasEther */, IPPROTO_IP, true /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_ETHERHDR_IPV4HDR_TCPHDR_DATA,
                packet.array());
    }

    @Test
    public void testBuildPacketIPv4Tcp() throws Exception {
        final ByteBuffer packet = buildPacket(null /* srcMac */, null /* dstMac */,
                IPPROTO_IP, IPPROTO_TCP, null /* data */);
        checkTcpPacket(false /* hasEther */, IPPROTO_IP, false /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_IPV4HDR_TCPHDR, packet.array());
    }

    @Test
    public void testBuildPacketIPv4TcpData() throws Exception {
        final ByteBuffer packet = buildPacket(null /* srcMac */, null /* dstMac */,
                IPPROTO_IP, IPPROTO_TCP, DATA);
        checkTcpPacket(false /* hasEther */, IPPROTO_IP, true /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_IPV4HDR_TCPHDR_DATA, packet.array());
    }

    @Test
    public void testBuildPacketEtherIPv4Udp() throws Exception {
        final ByteBuffer packet = buildPacket(SRC_MAC, DST_MAC, IPPROTO_IP, IPPROTO_UDP,
                null /* data */);
        checkUdpPacket(true /* hasEther */, IPPROTO_IP, false /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_ETHERHDR_IPV4HDR_UDPHDR, packet.array());
    }

    @Test
    public void testBuildPacketEtherIPv4UdpData() throws Exception {
        final ByteBuffer packet = buildPacket(SRC_MAC, DST_MAC, IPPROTO_IP, IPPROTO_UDP, DATA);
        checkUdpPacket(true /* hasEther */, IPPROTO_IP, true /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_ETHERHDR_IPV4HDR_UDPHDR_DATA, packet.array());
    }

    @Test
    public void testBuildPacketIPv4Udp() throws Exception {
        final ByteBuffer packet = buildPacket(null /* srcMac */, null /* dstMac */,
                IPPROTO_IP, IPPROTO_UDP, null /*data*/);
        checkUdpPacket(false /* hasEther */, IPPROTO_IP, false /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_IPV4HDR_UDPHDR, packet.array());
    }

    @Test
    public void testBuildPacketIPv4UdpData() throws Exception {
        final ByteBuffer packet = buildPacket(null /* srcMac */, null /* dstMac */,
                IPPROTO_IP, IPPROTO_UDP, DATA);
        checkUdpPacket(false /* hasEther */, IPPROTO_IP, true /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_IPV4HDR_UDPHDR_DATA, packet.array());
    }

    @Test
    public void testBuildPacketEtherIPv6TcpData() throws Exception {
        final ByteBuffer packet = buildPacket(SRC_MAC, DST_MAC, IPPROTO_IPV6, IPPROTO_TCP, DATA);
        checkTcpPacket(true /* hasEther */, IPPROTO_IPV6, true /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_ETHERHDR_IPV6HDR_TCPHDR_DATA,
                packet.array());
    }

    @Test
    public void testBuildPacketEtherIPv6Tcp() throws Exception {
        final ByteBuffer packet = buildPacket(SRC_MAC, DST_MAC, IPPROTO_IPV6, IPPROTO_TCP,
                null /*data*/);
        checkTcpPacket(true /* hasEther */, IPPROTO_IPV6, false /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_ETHERHDR_IPV6HDR_TCPHDR,
                packet.array());
    }

    @Test
    public void testBuildPacketIPv6TcpData() throws Exception {
        final ByteBuffer packet = buildPacket(null /* srcMac */, null /* dstMac */, IPPROTO_IPV6,
                IPPROTO_TCP, DATA);
        checkTcpPacket(false /* hasEther */, IPPROTO_IPV6, true /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_IPV6HDR_TCPHDR_DATA, packet.array());
    }

    @Test
    public void testBuildPacketIPv6Tcp() throws Exception {
        final ByteBuffer packet = buildPacket(null /* srcMac */, null /* dstMac */, IPPROTO_IPV6,
                IPPROTO_TCP, null /*data*/);
        checkTcpPacket(false /* hasEther */, IPPROTO_IPV6, false /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_IPV6HDR_TCPHDR, packet.array());
    }

    @Test
    public void testBuildPacketEtherIPv6Udp() throws Exception {
        final ByteBuffer packet = buildPacket(SRC_MAC, DST_MAC, IPPROTO_IPV6, IPPROTO_UDP,
                null /* data */);
        checkUdpPacket(true /* hasEther */, IPPROTO_IPV6, false /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_ETHERHDR_IPV6HDR_UDPHDR, packet.array());
    }

    @Test
    public void testBuildPacketEtherIPv6UdpData() throws Exception {
        final ByteBuffer packet = buildPacket(SRC_MAC, DST_MAC, IPPROTO_IPV6, IPPROTO_UDP,
                DATA);
        checkUdpPacket(true /* hasEther */, IPPROTO_IPV6, true /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_ETHERHDR_IPV6HDR_UDPHDR_DATA, packet.array());
    }

    @Test
    public void testBuildPacketIPv6Udp() throws Exception {
        final ByteBuffer packet = buildPacket(null /* srcMac */, null /* dstMac */,
                IPPROTO_IPV6, IPPROTO_UDP, null /*data*/);
        checkUdpPacket(false /* hasEther */, IPPROTO_IPV6, false /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_IPV6HDR_UDPHDR, packet.array());
    }

    @Test
    public void testBuildPacketIPv6UdpData() throws Exception {
        final ByteBuffer packet = buildPacket(null /* srcMac */, null /* dstMac */,
                IPPROTO_IPV6, IPPROTO_UDP, DATA);
        checkUdpPacket(false /* hasEther */, IPPROTO_IPV6, true /* hasData */, packet);
        assertArrayEquals(TEST_PACKET_IPV6HDR_UDPHDR_DATA, packet.array());
    }

    @Test
    public void testFinalizePacketWithoutIpv4Header() throws Exception {
        final ByteBuffer buffer = PacketBuilder.allocate(false /* hasEther */, IPPROTO_IP,
                IPPROTO_TCP, 0 /* payloadLen */);
        final PacketBuilder packetBuilder = new PacketBuilder(buffer);
        packetBuilder.writeTcpHeader(SRC_PORT, DST_PORT, SEQ_NO, ACK_NO,
                TCPHDR_ACK, WINDOW, URGENT_POINTER);
        assertThrows("java.io.IOException: Packet is missing IPv4 header", IOException.class,
                () -> packetBuilder.finalizePacket());
    }

    @Test
    public void testFinalizePacketWithoutL4Header() throws Exception {
        final ByteBuffer buffer = PacketBuilder.allocate(false /* hasEther */, IPPROTO_IP,
                IPPROTO_TCP, 0 /* payloadLen */);
        final PacketBuilder packetBuilder = new PacketBuilder(buffer);
        packetBuilder.writeIpv4Header(TYPE_OF_SERVICE, ID, FLAGS_AND_FRAGMENT_OFFSET,
                TIME_TO_LIVE, (byte) IPPROTO_TCP, IPV4_SRC_ADDR, IPV4_DST_ADDR);
        assertThrows("java.io.IOException: Packet is missing neither TCP nor UDP header",
                IOException.class, () -> packetBuilder.finalizePacket());
    }

    @Test
    public void testWriteL2HeaderToInsufficientBuffer() throws Exception {
        final PacketBuilder packetBuilder = new PacketBuilder(ByteBuffer.allocate(1));
        assertThrows(IOException.class,
                () -> packetBuilder.writeL2Header(SRC_MAC, DST_MAC, (short) ETHER_TYPE_IPV4));
    }

    @Test
    public void testWriteIpv4HeaderToInsufficientBuffer() throws Exception {
        final PacketBuilder packetBuilder = new PacketBuilder(ByteBuffer.allocate(1));
        assertThrows(IOException.class,
                () -> packetBuilder.writeIpv4Header(TYPE_OF_SERVICE, ID, FLAGS_AND_FRAGMENT_OFFSET,
                        TIME_TO_LIVE, (byte) IPPROTO_TCP, IPV4_SRC_ADDR, IPV4_DST_ADDR));
    }

    @Test
    public void testWriteTcpHeaderToInsufficientBuffer() throws Exception {
        final PacketBuilder packetBuilder = new PacketBuilder(ByteBuffer.allocate(1));
        assertThrows(IOException.class,
                () -> packetBuilder.writeTcpHeader(SRC_PORT, DST_PORT, SEQ_NO, ACK_NO,
                        TCPHDR_ACK, WINDOW, URGENT_POINTER));
    }

    @Test
    public void testWriteUdpHeaderToInsufficientBuffer() throws Exception {
        final PacketBuilder packetBuilder = new PacketBuilder(ByteBuffer.allocate(1));
        assertThrows(IOException.class, () -> packetBuilder.writeUdpHeader(SRC_PORT, DST_PORT));
    }

    private static Inet4Address addr4(String addr) {
        return (Inet4Address) InetAddresses.parseNumericAddress(addr);
    }

    private static Inet6Address addr6(String addr) {
        return (Inet6Address) InetAddresses.parseNumericAddress(addr);
    }
}
