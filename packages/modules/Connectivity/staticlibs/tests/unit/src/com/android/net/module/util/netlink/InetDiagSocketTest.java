/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.os.Process.ROOT_UID;
import static android.os.Process.SHELL_UID;
import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.IPPROTO_UDP;
import static android.system.OsConstants.NETLINK_INET_DIAG;

import static com.android.net.module.util.netlink.NetlinkConstants.SOCK_DESTROY;
import static com.android.net.module.util.netlink.StructNlMsgHdr.NLM_F_ACK;
import static com.android.net.module.util.netlink.StructNlMsgHdr.NLM_F_DUMP;
import static com.android.net.module.util.netlink.StructNlMsgHdr.NLM_F_REQUEST;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.InetAddresses;
import android.util.ArraySet;
import android.util.Range;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import libcore.util.HexEncoding;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class InetDiagSocketTest {
    // ::FFFF:192.0.2.1
    private static final byte[] SRC_V4_MAPPED_V6_ADDRESS_BYTES = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0xff,
            (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
    };
    // ::FFFF:192.0.2.2
    private static final byte[] DST_V4_MAPPED_V6_ADDRESS_BYTES = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0xff,
            (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x02,
    };

    // Hexadecimal representation of InetDiagReqV2 request.
    private static final String INET_DIAG_REQ_V2_UDP_INET4_HEX =
            // struct nlmsghdr
            "48000000" +     // length = 72
            "1400" +         // type = SOCK_DIAG_BY_FAMILY
            "0103" +         // flags = NLM_F_REQUEST | NLM_F_DUMP
            "00000000" +     // seqno
            "00000000" +     // pid (0 == kernel)
            // struct inet_diag_req_v2
            "02" +           // family = AF_INET
            "11" +           // protcol = IPPROTO_UDP
            "00" +           // idiag_ext
            "00" +           // pad
            "ffffffff" +     // idiag_states
            // inet_diag_sockid
            "a5de" +         // idiag_sport = 42462
            "b971" +         // idiag_dport = 47473
            "0a006402000000000000000000000000" + // idiag_src = 10.0.100.2
            "08080808000000000000000000000000" + // idiag_dst = 8.8.8.8
            "00000000" +     // idiag_if
            "ffffffffffffffff"; // idiag_cookie = INET_DIAG_NOCOOKIE
    private static final byte[] INET_DIAG_REQ_V2_UDP_INET4_BYTES =
            HexEncoding.decode(INET_DIAG_REQ_V2_UDP_INET4_HEX.toCharArray(), false);

    @Test
    public void testInetDiagReqV2UdpInet4() throws Exception {
        InetSocketAddress local = new InetSocketAddress(InetAddress.getByName("10.0.100.2"),
                42462);
        InetSocketAddress remote = new InetSocketAddress(InetAddress.getByName("8.8.8.8"),
                47473);
        final byte[] msg = InetDiagMessage.inetDiagReqV2(IPPROTO_UDP, local, remote, AF_INET,
                (short) (NLM_F_REQUEST | NLM_F_DUMP));
        assertArrayEquals(INET_DIAG_REQ_V2_UDP_INET4_BYTES, msg);
    }

    // Hexadecimal representation of InetDiagReqV2 request.
    private static final String INET_DIAG_REQ_V2_TCP_INET6_HEX =
            // struct nlmsghdr
            "48000000" +     // length = 72
            "1400" +         // type = SOCK_DIAG_BY_FAMILY
            "0100" +         // flags = NLM_F_REQUEST
            "00000000" +     // seqno
            "00000000" +     // pid (0 == kernel)
            // struct inet_diag_req_v2
            "0a" +           // family = AF_INET6
            "06" +           // protcol = IPPROTO_TCP
            "00" +           // idiag_ext
            "00" +           // pad
            "ffffffff" +     // idiag_states
                // inet_diag_sockid
                "a5de" +         // idiag_sport = 42462
                "b971" +         // idiag_dport = 47473
                "fe8000000000000086c9b2fffe6aed4b" + // idiag_src = fe80::86c9:b2ff:fe6a:ed4b
                "08080808000000000000000000000000" + // idiag_dst = 8.8.8.8
                "00000000" +     // idiag_if
                "ffffffffffffffff"; // idiag_cookie = INET_DIAG_NOCOOKIE
    private static final byte[] INET_DIAG_REQ_V2_TCP_INET6_BYTES =
            HexEncoding.decode(INET_DIAG_REQ_V2_TCP_INET6_HEX.toCharArray(), false);

    @Test
    public void testInetDiagReqV2TcpInet6() throws Exception {
        InetSocketAddress local = new InetSocketAddress(
                InetAddress.getByName("fe80::86c9:b2ff:fe6a:ed4b"), 42462);
        InetSocketAddress remote = new InetSocketAddress(InetAddress.getByName("8.8.8.8"),
                47473);
        byte[] msg = InetDiagMessage.inetDiagReqV2(IPPROTO_TCP, local, remote, AF_INET6,
                NLM_F_REQUEST);

        assertArrayEquals(INET_DIAG_REQ_V2_TCP_INET6_BYTES, msg);
    }

    // Hexadecimal representation of InetDiagReqV2 request with extension, INET_DIAG_INFO.
    private static final String INET_DIAG_REQ_V2_TCP_INET_INET_DIAG_HEX =
            // struct nlmsghdr
            "48000000" +     // length = 72
            "1400" +         // type = SOCK_DIAG_BY_FAMILY
            "0100" +         // flags = NLM_F_REQUEST
            "00000000" +     // seqno
            "00000000" +     // pid (0 == kernel)
            // struct inet_diag_req_v2
            "02" +           // family = AF_INET
            "06" +           // protcol = IPPROTO_TCP
            "02" +           // idiag_ext = INET_DIAG_INFO
            "00" +           // pad
            "ffffffff" +   // idiag_states
            // inet_diag_sockid
            "3039" +         // idiag_sport = 12345
            "d431" +         // idiag_dport = 54321
            "01020304000000000000000000000000" + // idiag_src = 1.2.3.4
            "08080404000000000000000000000000" + // idiag_dst = 8.8.4.4
            "00000000" +     // idiag_if
            "ffffffffffffffff"; // idiag_cookie = INET_DIAG_NOCOOKIE

    private static final byte[] INET_DIAG_REQ_V2_TCP_INET_INET_DIAG_BYTES =
            HexEncoding.decode(INET_DIAG_REQ_V2_TCP_INET_INET_DIAG_HEX.toCharArray(), false);
    private static final int TCP_ALL_STATES = 0xffffffff;
    @Test
    public void testInetDiagReqV2TcpInetWithExt() throws Exception {
        InetSocketAddress local = new InetSocketAddress(
                InetAddress.getByName("1.2.3.4"), 12345);
        InetSocketAddress remote = new InetSocketAddress(InetAddress.getByName("8.8.4.4"),
                54321);
        byte[] msg = InetDiagMessage.inetDiagReqV2(IPPROTO_TCP, local, remote, AF_INET,
                NLM_F_REQUEST, 0 /* pad */, 2 /* idiagExt */, TCP_ALL_STATES);

        assertArrayEquals(INET_DIAG_REQ_V2_TCP_INET_INET_DIAG_BYTES, msg);

        local = new InetSocketAddress(
                InetAddress.getByName("fe80::86c9:b2ff:fe6a:ed4b"), 42462);
        remote = new InetSocketAddress(InetAddress.getByName("8.8.8.8"),
                47473);
        msg = InetDiagMessage.inetDiagReqV2(IPPROTO_TCP, local, remote, AF_INET6,
                NLM_F_REQUEST, 0 /* pad */, 0 /* idiagExt */, TCP_ALL_STATES);

        assertArrayEquals(INET_DIAG_REQ_V2_TCP_INET6_BYTES, msg);
    }

    // Hexadecimal representation of InetDiagReqV2 request with no socket specified.
    private static final String INET_DIAG_REQ_V2_TCP_INET6_NO_ID_SPECIFIED_HEX =
            // struct nlmsghdr
            "48000000" +     // length = 72
            "1400" +         // type = SOCK_DIAG_BY_FAMILY
            "0100" +         // flags = NLM_F_REQUEST
            "00000000" +     // seqno
            "00000000" +     // pid (0 == kernel)
            // struct inet_diag_req_v2
            "0a" +           // family = AF_INET6
            "06" +           // protcol = IPPROTO_TCP
            "00" +           // idiag_ext
            "00" +           // pad
            "ffffffff" +     // idiag_states
            // inet_diag_sockid
            "0000" +         // idiag_sport
            "0000" +         // idiag_dport
            "00000000000000000000000000000000" + // idiag_src
            "00000000000000000000000000000000" + // idiag_dst
            "00000000" +     // idiag_if
            "0000000000000000"; // idiag_cookie

    private static final byte[] INET_DIAG_REQ_V2_TCP_INET6_NO_ID_SPECIFIED_BYTES =
            HexEncoding.decode(INET_DIAG_REQ_V2_TCP_INET6_NO_ID_SPECIFIED_HEX.toCharArray(), false);

    @Test
    public void testInetDiagReqV2TcpInet6NoIdSpecified() throws Exception {
        InetSocketAddress local = new InetSocketAddress(
                InetAddress.getByName("fe80::fe6a:ed4b"), 12345);
        InetSocketAddress remote = new InetSocketAddress(InetAddress.getByName("8.8.4.4"),
                54321);
        // Verify no socket specified if either local or remote socket address is null.
        byte[] msgExt = InetDiagMessage.inetDiagReqV2(IPPROTO_TCP, null, null, AF_INET6,
                NLM_F_REQUEST, 0 /* pad */, 0 /* idiagExt */, TCP_ALL_STATES);
        byte[] msg;
        try {
            msg = InetDiagMessage.inetDiagReqV2(IPPROTO_TCP, null, remote, AF_INET6,
                    NLM_F_REQUEST);
            fail("Both remote and local should be null, expected UnknownHostException");
        } catch (IllegalArgumentException e) {
        }

        try {
            msg = InetDiagMessage.inetDiagReqV2(IPPROTO_TCP, local, null, AF_INET6,
                    NLM_F_REQUEST, 0 /* pad */, 0 /* idiagExt */, TCP_ALL_STATES);
            fail("Both remote and local should be null, expected UnknownHostException");
        } catch (IllegalArgumentException e) {
        }

        msg = InetDiagMessage.inetDiagReqV2(IPPROTO_TCP, null, null, AF_INET6,
                NLM_F_REQUEST, 0 /* pad */, 0 /* idiagExt */, TCP_ALL_STATES);
        assertArrayEquals(INET_DIAG_REQ_V2_TCP_INET6_NO_ID_SPECIFIED_BYTES, msg);
        assertArrayEquals(INET_DIAG_REQ_V2_TCP_INET6_NO_ID_SPECIFIED_BYTES, msgExt);
    }

    // Hexadecimal representation of InetDiagReqV2 request with v4-mapped v6 address
    private static final String INET_DIAG_REQ_V2_TCP_INET6_V4_MAPPED_HEX =
            // struct nlmsghdr
            "48000000" +     // length = 72
            "1400" +         // type = SOCK_DIAG_BY_FAMILY
            "0100" +         // flags = NLM_F_REQUEST
            "00000000" +     // seqno
            "00000000" +     // pid (0 == kernel)
            // struct inet_diag_req_v2
            "0a" +           // family = AF_INET6
            "06" +           // protcol = IPPROTO_TCP
            "00" +           // idiag_ext
            "00" +           // pad
            "ffffffff" +     // idiag_states
            // inet_diag_sockid
            "a817" +     // idiag_sport = 43031
            "960f" +     // idiag_dport = 38415
            "00000000000000000000ffffc0000201" + // idiag_src = ::FFFF:192.0.2.1
            "00000000000000000000ffffc0000202" + // idiag_dst = ::FFFF:192.0.2.2
            "00000000" +     // idiag_if
            "ffffffffffffffff"; // idiag_cookie = INET_DIAG_NOCOOKIE

    private static final byte[] INET_DIAG_REQ_V2_TCP_INET6_V4_MAPPED_BYTES =
            HexEncoding.decode(INET_DIAG_REQ_V2_TCP_INET6_V4_MAPPED_HEX.toCharArray(), false);

    @Test
    public void testInetDiagReqV2TcpInet6V4Mapped() throws Exception {
        final Inet6Address srcAddr = Inet6Address.getByAddress(
                null /* host */, SRC_V4_MAPPED_V6_ADDRESS_BYTES, -1 /* scope_id */);
        final Inet6Address dstAddr = Inet6Address.getByAddress(
                null /* host */, DST_V4_MAPPED_V6_ADDRESS_BYTES, -1 /* scope_id */);
        final byte[] msg = InetDiagMessage.inetDiagReqV2(
                IPPROTO_TCP,
                new InetSocketAddress(srcAddr, 43031),
                new InetSocketAddress(dstAddr, 38415),
                AF_INET6,
                NLM_F_REQUEST);
        assertArrayEquals(INET_DIAG_REQ_V2_TCP_INET6_V4_MAPPED_BYTES, msg);
    }

    // Hexadecimal representation of InetDiagReqV2 request with SOCK_DESTROY
    private static final String INET_DIAG_REQ_V2_TCP_INET6_DESTROY_HEX =
            // struct nlmsghdr
            "48000000" +     // length = 72
            "1500" +         // type = SOCK_DESTROY
            "0500" +         // flags = NLM_F_REQUEST | NLM_F_ACK
            "00000000" +     // seqno
            "00000000" +     // pid (0 == kernel)
            // struct inet_diag_req_v2
            "0a" +           // family = AF_INET6
            "06" +           // protcol = IPPROTO_TCP
            "00" +           // idiag_ext
            "00" +           // pad
            "ffffffff" +     // idiag_states = TCP_ALL_STATES
            // inet_diag_sockid
            "a817" +     // idiag_sport = 43031
            "960f" +     // idiag_dport = 38415
            "20010db8000000000000000000000001" + // idiag_src = 2001:db8::1
            "20010db8000000000000000000000002" + // idiag_dst = 2001:db8::2
            "07000000" + // idiag_if = 7
            "5800000000000000"; // idiag_cookie = 88

    private static final byte[] INET_DIAG_REQ_V2_TCP_INET6_DESTROY_BYTES =
            HexEncoding.decode(INET_DIAG_REQ_V2_TCP_INET6_DESTROY_HEX.toCharArray(), false);

    @Test
    public void testInetDiagReqV2TcpInet6Destroy() throws Exception {
        final StructInetDiagSockId sockId = new StructInetDiagSockId(
                new InetSocketAddress(InetAddresses.parseNumericAddress("2001:db8::1"), 43031),
                new InetSocketAddress(InetAddresses.parseNumericAddress("2001:db8::2"), 38415),
                7  /* ifIndex */,
                88 /* cookie */);
        final byte[] msg = InetDiagMessage.inetDiagReqV2(IPPROTO_TCP, sockId, AF_INET6,
                SOCK_DESTROY, (short) (NLM_F_REQUEST | NLM_F_ACK), 0 /* pad */, 0 /* idiagExt */,
                TCP_ALL_STATES);

        assertArrayEquals(INET_DIAG_REQ_V2_TCP_INET6_DESTROY_BYTES, msg);
    }

    private void assertNlMsgHdr(StructNlMsgHdr hdr, short type, short flags, int seq, int pid) {
        assertNotNull(hdr);
        assertEquals(type, hdr.nlmsg_type);
        assertEquals(flags, hdr.nlmsg_flags);
        assertEquals(seq, hdr.nlmsg_seq);
        assertEquals(pid, hdr.nlmsg_pid);
    }

    private void assertInetDiagSockId(StructInetDiagSockId sockId,
            InetSocketAddress locSocketAddress, InetSocketAddress remSocketAddress,
            int ifIndex, long cookie) {
        assertEquals(locSocketAddress, sockId.locSocketAddress);
        assertEquals(remSocketAddress, sockId.remSocketAddress);
        assertEquals(ifIndex, sockId.ifIndex);
        assertEquals(cookie, sockId.cookie);
    }

    // Hexadecimal representation of InetDiagMessage
    private static final String INET_DIAG_MSG_HEX1 =
            // struct nlmsghdr
            "58000000"     // length = 88
            + "1400"         // type = SOCK_DIAG_BY_FAMILY
            + "0200"         // flags = NLM_F_MULTI
            + "00000000"     // seqno
            + "f5220000"     // pid
            // struct inet_diag_msg
            + "0a"           // family = AF_INET6
            + "01"           // idiag_state = 1
            + "02"           // idiag_timer = 2
            + "ff"           // idiag_retrans = 255
                // inet_diag_sockid
                + "a817"     // idiag_sport = 43031
                + "960f"     // idiag_dport = 38415
                + "20010db8000000000000000000000001" // idiag_src = 2001:db8::1
                + "20010db8000000000000000000000002" // idiag_dst = 2001:db8::2
                + "07000000" // idiag_if = 7
                + "5800000000000000" // idiag_cookie = 88
            + "04000000"     // idiag_expires = 4
            + "05000000"     // idiag_rqueue = 5
            + "06000000"     // idiag_wqueue = 6
            + "a3270000"     // idiag_uid = 10147
            + "a57e19f0";    // idiag_inode = 4028202661

    private void assertInetDiagMsg1(final NetlinkMessage msg) {
        assertNotNull(msg);

        assertTrue(msg instanceof InetDiagMessage);
        final InetDiagMessage inetDiagMsg = (InetDiagMessage) msg;

        assertNlMsgHdr(inetDiagMsg.getHeader(),
                NetlinkConstants.SOCK_DIAG_BY_FAMILY,
                StructNlMsgHdr.NLM_F_MULTI,
                0    /* seq */,
                8949 /* pid */);

        assertEquals(AF_INET6, inetDiagMsg.inetDiagMsg.idiag_family);
        assertEquals(1, inetDiagMsg.inetDiagMsg.idiag_state);
        assertEquals(2, inetDiagMsg.inetDiagMsg.idiag_timer);
        assertEquals(255, inetDiagMsg.inetDiagMsg.idiag_retrans);
        assertInetDiagSockId(inetDiagMsg.inetDiagMsg.id,
                new InetSocketAddress(InetAddresses.parseNumericAddress("2001:db8::1"), 43031),
                new InetSocketAddress(InetAddresses.parseNumericAddress("2001:db8::2"), 38415),
                7  /* ifIndex */,
                88 /* cookie */);
        assertEquals(4, inetDiagMsg.inetDiagMsg.idiag_expires);
        assertEquals(5, inetDiagMsg.inetDiagMsg.idiag_rqueue);
        assertEquals(6, inetDiagMsg.inetDiagMsg.idiag_wqueue);
        assertEquals(10147, inetDiagMsg.inetDiagMsg.idiag_uid);
        assertEquals(4028202661L, inetDiagMsg.inetDiagMsg.idiag_inode);

        // Verify the length of attribute list is 0 as expected since message doesn't
        // take any attributes
        assertEquals(0, inetDiagMsg.nlAttrs.size());
    }

    // Hexadecimal representation of InetDiagMessage
    private static final String INET_DIAG_MSG_HEX2 =
            // struct nlmsghdr
            "6C000000"       // length = 108
            + "1400"         // type = SOCK_DIAG_BY_FAMILY
            + "0200"         // flags = NLM_F_MULTI
            + "00000000"     // seqno
            + "f5220000"     // pid
            // struct inet_diag_msg
            + "0a"           // family = AF_INET6
            + "02"           // idiag_state = 2
            + "10"           // idiag_timer = 16
            + "20"           // idiag_retrans = 32
                // inet_diag_sockid
                + "a845"     // idiag_sport = 43077
                + "01bb"     // idiag_dport = 443
                + "20010db8000000000000000000000003" // idiag_src = 2001:db8::3
                + "20010db8000000000000000000000004" // idiag_dst = 2001:db8::4
                + "08000000" // idiag_if = 8
                + "6300000000000000" // idiag_cookie = 99
            + "30000000"     // idiag_expires = 48
            + "40000000"     // idiag_rqueue = 64
            + "50000000"     // idiag_wqueue = 80
            + "39300000"     // idiag_uid = 12345
            + "851a0000"     // idiag_inode = 6789
            + "0500"           // len = 5
            + "0800"         // type = 8
            + "00000000"     // data
            + "0800"         // len = 8
            + "0F00"         // type = 15(INET_DIAG_MARK)
            + "850A0C00"     // data, socket mark=789125
            + "0400"         // len = 4
            + "0200";        // type = 2

    private void assertInetDiagMsg2(final NetlinkMessage msg) {
        assertNotNull(msg);

        assertTrue(msg instanceof InetDiagMessage);
        final InetDiagMessage inetDiagMsg = (InetDiagMessage) msg;

        assertNlMsgHdr(inetDiagMsg.getHeader(),
                NetlinkConstants.SOCK_DIAG_BY_FAMILY,
                StructNlMsgHdr.NLM_F_MULTI,
                0    /* seq */,
                8949 /* pid */);

        assertEquals(AF_INET6, inetDiagMsg.inetDiagMsg.idiag_family);
        assertEquals(2, inetDiagMsg.inetDiagMsg.idiag_state);
        assertEquals(16, inetDiagMsg.inetDiagMsg.idiag_timer);
        assertEquals(32, inetDiagMsg.inetDiagMsg.idiag_retrans);
        assertInetDiagSockId(inetDiagMsg.inetDiagMsg.id,
                new InetSocketAddress(InetAddresses.parseNumericAddress("2001:db8::3"), 43077),
                new InetSocketAddress(InetAddresses.parseNumericAddress("2001:db8::4"), 443),
                8  /* ifIndex */,
                99 /* cookie */);
        assertEquals(48, inetDiagMsg.inetDiagMsg.idiag_expires);
        assertEquals(64, inetDiagMsg.inetDiagMsg.idiag_rqueue);
        assertEquals(80, inetDiagMsg.inetDiagMsg.idiag_wqueue);
        assertEquals(12345, inetDiagMsg.inetDiagMsg.idiag_uid);
        assertEquals(6789, inetDiagMsg.inetDiagMsg.idiag_inode);

        // Verify the number of nlAttr and their content.
        assertEquals(3, inetDiagMsg.nlAttrs.size());

        assertEquals(5, inetDiagMsg.nlAttrs.get(0).nla_len);
        assertEquals(8, inetDiagMsg.nlAttrs.get(0).nla_type);
        assertArrayEquals(
                HexEncoding.decode("00".toCharArray(), false),
                inetDiagMsg.nlAttrs.get(0).nla_value);
        assertEquals(8, inetDiagMsg.nlAttrs.get(1).nla_len);
        assertEquals(15, inetDiagMsg.nlAttrs.get(1).nla_type);
        assertArrayEquals(
                HexEncoding.decode("850A0C00".toCharArray(), false),
                inetDiagMsg.nlAttrs.get(1).nla_value);
        assertEquals(4, inetDiagMsg.nlAttrs.get(2).nla_len);
        assertEquals(2, inetDiagMsg.nlAttrs.get(2).nla_type);
        assertNull(inetDiagMsg.nlAttrs.get(2).nla_value);
    }

    // Hexadecimal representation of InetDiagMessage
    private static final String INET_DIAG_MSG_HEX_MALFORMED =
            // struct nlmsghdr
            "6E000000"       // length = 110
            + "1400"         // type = SOCK_DIAG_BY_FAMILY
            + "0200"         // flags = NLM_F_MULTI
            + "00000000"     // seqno
            + "f5220000"     // pid
            // struct inet_diag_msg
            + "0a"           // family = AF_INET6
            + "02"           // idiag_state = 2
            + "10"           // idiag_timer = 16
            + "20"           // idiag_retrans = 32
            // inet_diag_sockid
            + "a845"     // idiag_sport = 43077
            + "01bb"     // idiag_dport = 443
            + "20010db8000000000000000000000005" // idiag_src = 2001:db8::5
            + "20010db8000000000000000000000006" // idiag_dst = 2001:db8::6
            + "08000000" // idiag_if = 8
            + "6300000000000000" // idiag_cookie = 99
            + "30000000"     // idiag_expires = 48
            + "40000000"     // idiag_rqueue = 64
            + "50000000"     // idiag_wqueue = 80
            + "39300000"     // idiag_uid = 12345
            + "851a0000"     // idiag_inode = 6789
            + "0500"           // len = 5
            + "0800"         // type = 8
            + "00000000"     // data
            + "0800"         // len = 8
            + "0F00"         // type = 15(INET_DIAG_MARK)
            + "850A0C00"     // data, socket mark=789125
            + "0400"         // len = 4
            + "0200"         // type = 2
            + "0100"         // len = 1, malformed value
            + "0100";        // type = 1

    @Test
    public void testParseInetDiagResponseMalformedNlAttr() throws Exception {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(
                HexEncoding.decode((INET_DIAG_MSG_HEX_MALFORMED).toCharArray(), false));
        byteBuffer.order(ByteOrder.nativeOrder());
        assertNull(NetlinkMessage.parse(byteBuffer, NETLINK_INET_DIAG));
    }

    // Hexadecimal representation of InetDiagMessage
    private static final String INET_DIAG_MSG_HEX_TRUNCATED =
            // struct nlmsghdr
            "5E000000"       // length = 96
            + "1400"         // type = SOCK_DIAG_BY_FAMILY
            + "0200"         // flags = NLM_F_MULTI
            + "00000000"     // seqno
            + "f5220000"     // pid
            // struct inet_diag_msg
            + "0a"           // family = AF_INET6
            + "02"           // idiag_state = 2
            + "10"           // idiag_timer = 16
            + "20"           // idiag_retrans = 32
            // inet_diag_sockid
            + "a845"     // idiag_sport = 43077
            + "01bb"     // idiag_dport = 443
            + "20010db8000000000000000000000005" // idiag_src = 2001:db8::5
            + "20010db8000000000000000000000006" // idiag_dst = 2001:db8::6
            + "08000000" // idiag_if = 8
            + "6300000000000000" // idiag_cookie = 99
            + "30000000"     // idiag_expires = 48
            + "40000000"     // idiag_rqueue = 64
            + "50000000"     // idiag_wqueue = 80
            + "39300000"     // idiag_uid = 12345
            + "851a0000"     // idiag_inode = 6789
            + "0800"         // len = 8
            + "0100"         // type = 1
            + "000000";      // data, less than the expected length

    @Test
    public void testParseInetDiagResponseTruncatedNlAttr() throws Exception {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(
                HexEncoding.decode((INET_DIAG_MSG_HEX_TRUNCATED).toCharArray(), false));
        byteBuffer.order(ByteOrder.nativeOrder());
        assertNull(NetlinkMessage.parse(byteBuffer, NETLINK_INET_DIAG));
    }

    private static final byte[] INET_DIAG_MSG_BYTES =
            HexEncoding.decode(INET_DIAG_MSG_HEX1.toCharArray(), false);

    @Test
    public void testParseInetDiagResponse() throws Exception {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(INET_DIAG_MSG_BYTES);
        byteBuffer.order(ByteOrder.nativeOrder());
        assertInetDiagMsg1(NetlinkMessage.parse(byteBuffer, NETLINK_INET_DIAG));
    }


    private static final byte[] INET_DIAG_MSG_BYTES_MULTIPLE =
            HexEncoding.decode((INET_DIAG_MSG_HEX1 + INET_DIAG_MSG_HEX2).toCharArray(), false);

    @Test
    public void testParseInetDiagResponseMultiple() {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(INET_DIAG_MSG_BYTES_MULTIPLE);
        byteBuffer.order(ByteOrder.nativeOrder());
        assertInetDiagMsg1(NetlinkMessage.parse(byteBuffer, NETLINK_INET_DIAG));
        assertInetDiagMsg2(NetlinkMessage.parse(byteBuffer, NETLINK_INET_DIAG));
    }

    private static final String INET_DIAG_SOCK_ID_V4_MAPPED_V6_HEX =
            "a845" +     // idiag_sport = 43077
            "01bb" +     // idiag_dport = 443
            "00000000000000000000ffffc0000201" + // idiag_src = ::FFFF:192.0.2.1
            "00000000000000000000ffffc0000202" + // idiag_dst = ::FFFF:192.0.2.2
            "08000000" + // idiag_if = 8
            "6300000000000000"; // idiag_cookie = 99

    private static final byte[] INET_DIAG_SOCK_ID_V4_MAPPED_V6_BYTES =
            HexEncoding.decode(INET_DIAG_SOCK_ID_V4_MAPPED_V6_HEX.toCharArray(), false);

    @Test
    public void testParseAndPackInetDiagSockIdV4MappedV6() {
        final ByteBuffer parseByteBuffer = ByteBuffer.wrap(INET_DIAG_SOCK_ID_V4_MAPPED_V6_BYTES);
        parseByteBuffer.order(ByteOrder.nativeOrder());
        final StructInetDiagSockId diagSockId =
                StructInetDiagSockId.parse(parseByteBuffer, (short) AF_INET6);
        assertNotNull(diagSockId);

        final ByteBuffer packByteBuffer =
                ByteBuffer.allocate(INET_DIAG_SOCK_ID_V4_MAPPED_V6_BYTES.length);
        diagSockId.pack(packByteBuffer);

        // Move position to the head since ByteBuffer#equals compares the values from the current
        // position.
        parseByteBuffer.position(0);
        packByteBuffer.position(0);
        assertEquals(parseByteBuffer, packByteBuffer);
    }

    // Hexadecimal representation of InetDiagMessage with v4-mapped v6 address
    private static final String INET_DIAG_MSG_V4_MAPPED_V6_HEX =
            // struct nlmsghdr
            "58000000" +     // length = 88
            "1400" +         // type = SOCK_DIAG_BY_FAMILY
            "0200" +         // flags = NLM_F_MULTI
            "00000000" +     // seqno
            "f5220000" +     // pid
            // struct inet_diag_msg
            "0a" +           // family = AF_INET6
            "01" +           // idiag_state = 1
            "02" +           // idiag_timer = 2
            "03" +           // idiag_retrans = 3
                // inet_diag_sockid
                "a817" +     // idiag_sport = 43031
                "960f" +     // idiag_dport = 38415
                "00000000000000000000ffffc0000201" + // idiag_src = ::FFFF:192.0.2.1
                "00000000000000000000ffffc0000202" + // idiag_dst = ::FFFF:192.0.2.2
                "07000000" + // idiag_if = 7
                "5800000000000000" + // idiag_cookie = 88
            "04000000" +     // idiag_expires = 4
            "05000000" +     // idiag_rqueue = 5
            "06000000" +     // idiag_wqueue = 6
            "a3270000" +     // idiag_uid = 10147
            "A57E1900";      // idiag_inode = 1670821

    private static final byte[] INET_DIAG_MSG_V4_MAPPED_V6_BYTES =
            HexEncoding.decode(INET_DIAG_MSG_V4_MAPPED_V6_HEX.toCharArray(), false);

    @Test
    public void testParseInetDiagResponseV4MappedV6() throws Exception {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(INET_DIAG_MSG_V4_MAPPED_V6_BYTES);
        byteBuffer.order(ByteOrder.nativeOrder());
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_INET_DIAG);

        assertNotNull(msg);
        assertTrue(msg instanceof InetDiagMessage);
        final InetDiagMessage inetDiagMsg = (InetDiagMessage) msg;
        final Inet6Address srcAddr = Inet6Address.getByAddress(
                null /* host */, SRC_V4_MAPPED_V6_ADDRESS_BYTES, -1 /* scope_id */);
        final Inet6Address dstAddr = Inet6Address.getByAddress(
                null /* host */, DST_V4_MAPPED_V6_ADDRESS_BYTES, -1 /* scope_id */);
        assertInetDiagSockId(inetDiagMsg.inetDiagMsg.id,
                new InetSocketAddress(srcAddr, 43031),
                new InetSocketAddress(dstAddr, 38415),
                7  /* ifIndex */,
                88 /* cookie */);
    }

    private void doTestIsLoopback(InetAddress srcAddr, InetAddress dstAddr, boolean expected) {
        final InetDiagMessage inetDiagMsg = new InetDiagMessage(new StructNlMsgHdr());
        inetDiagMsg.inetDiagMsg.id = new StructInetDiagSockId(
                new InetSocketAddress(srcAddr, 43031),
                new InetSocketAddress(dstAddr, 38415)
        );

        assertEquals(expected, InetDiagMessage.isLoopback(inetDiagMsg));
    }

    @Test
    public void testIsLoopback() {
        doTestIsLoopback(
                InetAddresses.parseNumericAddress("127.0.0.1"),
                InetAddresses.parseNumericAddress("192.0.2.1"),
                true
        );
        doTestIsLoopback(
                InetAddresses.parseNumericAddress("192.0.2.1"),
                InetAddresses.parseNumericAddress("127.7.7.7"),
                true
        );
        doTestIsLoopback(
                InetAddresses.parseNumericAddress("::1"),
                InetAddresses.parseNumericAddress("::1"),
                true
        );
        doTestIsLoopback(
                InetAddresses.parseNumericAddress("::1"),
                InetAddresses.parseNumericAddress("2001:db8::1"),
                true
        );
    }

    @Test
    public void testIsLoopbackSameSrcDstAddress()  {
        doTestIsLoopback(
                InetAddresses.parseNumericAddress("192.0.2.1"),
                InetAddresses.parseNumericAddress("192.0.2.1"),
                true
        );
        doTestIsLoopback(
                InetAddresses.parseNumericAddress("2001:db8::1"),
                InetAddresses.parseNumericAddress("2001:db8::1"),
                true
        );
    }

    @Test
    public void testIsLoopbackNonLoopbackSocket()  {
        doTestIsLoopback(
                InetAddresses.parseNumericAddress("192.0.2.1"),
                InetAddresses.parseNumericAddress("192.0.2.2"),
                false
        );
        doTestIsLoopback(
                InetAddresses.parseNumericAddress("2001:db8::1"),
                InetAddresses.parseNumericAddress("2001:db8::2"),
                false
        );
    }

    @Test
    public void testIsLoopbackV4MappedV6() throws UnknownHostException {
        // ::FFFF:127.1.2.3
        final byte[] addrLoopbackByte = {
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0xff,
                (byte) 0x7f, (byte) 0x01, (byte) 0x02, (byte) 0x03,
        };
        // ::FFFF:192.0.2.1
        final byte[] addrNonLoopbackByte1 = {
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0xff,
                (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x01,
        };
        // ::FFFF:192.0.2.2
        final byte[] addrNonLoopbackByte2 = {
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0xff,
                (byte) 0xc0, (byte) 0x00, (byte) 0x02, (byte) 0x02,
        };

        final Inet6Address addrLoopback = Inet6Address.getByAddress(null, addrLoopbackByte, -1);
        final Inet6Address addrNonLoopback1 =
                Inet6Address.getByAddress(null, addrNonLoopbackByte1, -1);
        final Inet6Address addrNonLoopback2 =
                Inet6Address.getByAddress(null, addrNonLoopbackByte2, -1);

        doTestIsLoopback(addrLoopback, addrNonLoopback1, true);
        doTestIsLoopback(addrNonLoopback1, addrNonLoopback2, false);
        doTestIsLoopback(addrNonLoopback1, addrNonLoopback1, true);
    }

    private void doTestContainsUid(final int uid, final Set<Range<Integer>> ranges,
            final boolean expected) {
        final InetDiagMessage inetDiagMsg = new InetDiagMessage(new StructNlMsgHdr());
        inetDiagMsg.inetDiagMsg.idiag_uid = uid;
        assertEquals(expected, InetDiagMessage.containsUid(inetDiagMsg, ranges));
    }

    @Test
    public void testContainsUid() {
        doTestContainsUid(77 /* uid */,
                new ArraySet<>(List.of(new Range<>(0, 100))),
                true /* expected */);
        doTestContainsUid(77 /* uid */,
                new ArraySet<>(List.of(new Range<>(77, 77), new Range<>(100, 200))),
                true /* expected */);

        doTestContainsUid(77 /* uid */,
                new ArraySet<>(List.of(new Range<>(100, 200))),
                false /* expected */);
        doTestContainsUid(77 /* uid */,
                new ArraySet<>(List.of(new Range<>(0, 76), new Range<>(78, 100))),
                false /* expected */);
    }

    private void doTestIsAdbSocket(final int uid, final boolean expected) {
        final InetDiagMessage inetDiagMsg = new InetDiagMessage(new StructNlMsgHdr());
        inetDiagMsg.inetDiagMsg.idiag_uid = uid;
        inetDiagMsg.inetDiagMsg.id = new StructInetDiagSockId(
                new InetSocketAddress(InetAddresses.parseNumericAddress("2001:db8::1"), 38417),
                new InetSocketAddress(InetAddresses.parseNumericAddress("2001:db8::2"), 38415)
        );
        assertEquals(expected, InetDiagMessage.isAdbSocket(inetDiagMsg));
    }

    @Test
    public void testIsAdbSocket() {
        final int appUid = 10108;
        doTestIsAdbSocket(SHELL_UID,  true /* expected */);
        doTestIsAdbSocket(ROOT_UID, false /* expected */);
        doTestIsAdbSocket(appUid, false /* expected */);
    }
}
