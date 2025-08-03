/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.net.module.util.netlink.NetlinkConstants.IPCTNL_MSG_CT_NEW;
import static com.android.net.module.util.netlink.NetlinkConstants.NFNL_SUBSYS_CTNETLINK;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.system.OsConstants;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import libcore.util.HexEncoding;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class ConntrackMessageTest {
    private static final boolean USING_LE = (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN);

    private short makeCtType(short msgType) {
        return (short) (NFNL_SUBSYS_CTNETLINK << 8 | (byte) msgType);
    }

    // Example 1: TCP (192.168.43.209, 44333) -> (23.211.13.26, 443)
    public static final String CT_V4UPDATE_TCP_HEX =
            // struct nlmsghdr
            "50000000" +      // length = 80
            "0001" +          // type = (1 << 8) | 0
            "0501" +          // flags
            "01000000" +      // seqno = 1
            "00000000" +      // pid = 0
            // struct nfgenmsg
            "02" +            // nfgen_family  = AF_INET
            "00" +            // version = NFNETLINK_V0
            "0000" +          // res_id
            // struct nlattr
            "3400" +          // nla_len = 52
            "0180" +          // nla_type = nested CTA_TUPLE_ORIG
                // struct nlattr
                "1400" +      // nla_len = 20
                "0180" +      // nla_type = nested CTA_TUPLE_IP
                    "0800 0100 C0A82BD1" +  // nla_type=CTA_IP_V4_SRC, ip=192.168.43.209
                    "0800 0200 17D30D1A" +  // nla_type=CTA_IP_V4_DST, ip=23.211.13.26
                // struct nlattr
                "1C00" +      // nla_len = 28
                "0280" +      // nla_type = nested CTA_TUPLE_PROTO
                    "0500 0100 06 000000" +  // nla_type=CTA_PROTO_NUM, proto=6
                    "0600 0200 AD2D 0000" +  // nla_type=CTA_PROTO_SRC_PORT, port=44333 (big endian)
                    "0600 0300 01BB 0000" +  // nla_type=CTA_PROTO_DST_PORT, port=443 (big endian)
            // struct nlattr
            "0800" +          // nla_len = 8
            "0700" +          // nla_type = CTA_TIMEOUT
            "00069780";       // nla_value = 432000 (big endian)
    public static final byte[] CT_V4UPDATE_TCP_BYTES =
            HexEncoding.decode(CT_V4UPDATE_TCP_HEX.replaceAll(" ", "").toCharArray(), false);

    private byte[] makeIPv4TimeoutUpdateRequestTcp() throws Exception {
        return ConntrackMessage.newIPv4TimeoutUpdateRequest(
                OsConstants.IPPROTO_TCP,
                (Inet4Address) InetAddress.getByName("192.168.43.209"), 44333,
                (Inet4Address) InetAddress.getByName("23.211.13.26"), 443,
                432000);
    }

    // Example 2: UDP (100.96.167.146, 37069) -> (216.58.197.10, 443)
    public static final String CT_V4UPDATE_UDP_HEX =
            // struct nlmsghdr
            "50000000" +      // length = 80
            "0001" +          // type = (1 << 8) | 0
            "0501" +          // flags
            "01000000" +      // seqno = 1
            "00000000" +      // pid = 0
            // struct nfgenmsg
            "02" +            // nfgen_family  = AF_INET
            "00" +            // version = NFNETLINK_V0
            "0000" +          // res_id
            // struct nlattr
            "3400" +          // nla_len = 52
            "0180" +          // nla_type = nested CTA_TUPLE_ORIG
                // struct nlattr
                "1400" +      // nla_len = 20
                "0180" +      // nla_type = nested CTA_TUPLE_IP
                    "0800 0100 6460A792" +  // nla_type=CTA_IP_V4_SRC, ip=100.96.167.146
                    "0800 0200 D83AC50A" +  // nla_type=CTA_IP_V4_DST, ip=216.58.197.10
                // struct nlattr
                "1C00" +      // nla_len = 28
                "0280" +      // nla_type = nested CTA_TUPLE_PROTO
                    "0500 0100 11 000000" +  // nla_type=CTA_PROTO_NUM, proto=17
                    "0600 0200 90CD 0000" +  // nla_type=CTA_PROTO_SRC_PORT, port=37069 (big endian)
                    "0600 0300 01BB 0000" +  // nla_type=CTA_PROTO_DST_PORT, port=443 (big endian)
            // struct nlattr
            "0800" +          // nla_len = 8
            "0700" +          // nla_type = CTA_TIMEOUT
            "000000B4";       // nla_value = 180 (big endian)
    public static final byte[] CT_V4UPDATE_UDP_BYTES =
            HexEncoding.decode(CT_V4UPDATE_UDP_HEX.replaceAll(" ", "").toCharArray(), false);

    private byte[] makeIPv4TimeoutUpdateRequestUdp() throws Exception {
        return ConntrackMessage.newIPv4TimeoutUpdateRequest(
                OsConstants.IPPROTO_UDP,
                (Inet4Address) InetAddress.getByName("100.96.167.146"), 37069,
                (Inet4Address) InetAddress.getByName("216.58.197.10"), 443,
                180);
    }

    @Test
    public void testConntrackMakeIPv4TcpTimeoutUpdate() throws Exception {
        assumeTrue(USING_LE);

        final byte[] tcp = makeIPv4TimeoutUpdateRequestTcp();
        assertArrayEquals(CT_V4UPDATE_TCP_BYTES, tcp);
    }

    @Test
    public void testConntrackParseIPv4TcpTimeoutUpdate() throws Exception {
        assumeTrue(USING_LE);

        final byte[] tcp = makeIPv4TimeoutUpdateRequestTcp();
        final ByteBuffer byteBuffer = ByteBuffer.wrap(tcp);
        byteBuffer.order(ByteOrder.nativeOrder());
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, OsConstants.NETLINK_NETFILTER);
        assertNotNull(msg);
        assertTrue(msg instanceof ConntrackMessage);
        final ConntrackMessage conntrackMessage = (ConntrackMessage) msg;

        final StructNlMsgHdr hdr = conntrackMessage.getHeader();
        assertNotNull(hdr);
        assertEquals(80, hdr.nlmsg_len);
        assertEquals(makeCtType(IPCTNL_MSG_CT_NEW), hdr.nlmsg_type);
        assertEquals((short) (StructNlMsgHdr.NLM_F_REPLACE | StructNlMsgHdr.NLM_F_REQUEST
                | StructNlMsgHdr.NLM_F_ACK), hdr.nlmsg_flags);
        assertEquals(1, hdr.nlmsg_seq);
        assertEquals(0, hdr.nlmsg_pid);

        final StructNfGenMsg nfmsgHdr = conntrackMessage.nfGenMsg;
        assertNotNull(nfmsgHdr);
        assertEquals((byte) OsConstants.AF_INET, nfmsgHdr.nfgen_family);
        assertEquals((byte) StructNfGenMsg.NFNETLINK_V0, nfmsgHdr.version);
        assertEquals((short) 0, nfmsgHdr.res_id);

        assertEquals(InetAddress.parseNumericAddress("192.168.43.209"),
                conntrackMessage.tupleOrig.srcIp);
        assertEquals(InetAddress.parseNumericAddress("23.211.13.26"),
                conntrackMessage.tupleOrig.dstIp);
        assertEquals((byte) OsConstants.IPPROTO_TCP, conntrackMessage.tupleOrig.protoNum);
        assertEquals((short) 44333, conntrackMessage.tupleOrig.srcPort);
        assertEquals((short) 443, conntrackMessage.tupleOrig.dstPort);

        assertNull(conntrackMessage.tupleReply);

        assertEquals(0 /* absent */, conntrackMessage.status);
        assertEquals(432000, conntrackMessage.timeoutSec);
    }

    @Test
    public void testConntrackMakeIPv4UdpTimeoutUpdate() throws Exception {
        assumeTrue(USING_LE);

        final byte[] udp = makeIPv4TimeoutUpdateRequestUdp();
        assertArrayEquals(CT_V4UPDATE_UDP_BYTES, udp);
    }

    @Test
    public void testConntrackParseIPv4UdpTimeoutUpdate() throws Exception {
        assumeTrue(USING_LE);

        final byte[] udp = makeIPv4TimeoutUpdateRequestUdp();
        final ByteBuffer byteBuffer = ByteBuffer.wrap(udp);
        byteBuffer.order(ByteOrder.nativeOrder());
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, OsConstants.NETLINK_NETFILTER);
        assertNotNull(msg);
        assertTrue(msg instanceof ConntrackMessage);
        final ConntrackMessage conntrackMessage = (ConntrackMessage) msg;

        final StructNlMsgHdr hdr = conntrackMessage.getHeader();
        assertNotNull(hdr);
        assertEquals(80, hdr.nlmsg_len);
        assertEquals(makeCtType(IPCTNL_MSG_CT_NEW), hdr.nlmsg_type);
        assertEquals((short) (StructNlMsgHdr.NLM_F_REPLACE | StructNlMsgHdr.NLM_F_REQUEST
                | StructNlMsgHdr.NLM_F_ACK), hdr.nlmsg_flags);
        assertEquals(1, hdr.nlmsg_seq);
        assertEquals(0, hdr.nlmsg_pid);

        final StructNfGenMsg nfmsgHdr = conntrackMessage.nfGenMsg;
        assertNotNull(nfmsgHdr);
        assertEquals((byte) OsConstants.AF_INET, nfmsgHdr.nfgen_family);
        assertEquals((byte) StructNfGenMsg.NFNETLINK_V0, nfmsgHdr.version);
        assertEquals((short) 0, nfmsgHdr.res_id);

        assertEquals(InetAddress.parseNumericAddress("100.96.167.146"),
                conntrackMessage.tupleOrig.srcIp);
        assertEquals(InetAddress.parseNumericAddress("216.58.197.10"),
                conntrackMessage.tupleOrig.dstIp);
        assertEquals((byte) OsConstants.IPPROTO_UDP, conntrackMessage.tupleOrig.protoNum);
        assertEquals((short) 37069, conntrackMessage.tupleOrig.srcPort);
        assertEquals((short) 443, conntrackMessage.tupleOrig.dstPort);

        assertNull(conntrackMessage.tupleReply);

        assertEquals(0 /* absent */, conntrackMessage.status);
        assertEquals(180, conntrackMessage.timeoutSec);
    }

    public static final String CT_V4NEW_TCP_HEX =
            // CHECKSTYLE:OFF IndentationCheck
            // struct nlmsghdr
            "8C000000" +      // length = 140
            "0001" +          // type = NFNL_SUBSYS_CTNETLINK (1) << 8 | IPCTNL_MSG_CT_NEW (0)
            "0006" +          // flags = NLM_F_CREATE (1 << 10) | NLM_F_EXCL (1 << 9)
            "00000000" +      // seqno = 0
            "00000000" +      // pid = 0
            // struct nfgenmsg
            "02" +            // nfgen_family = AF_INET
            "00" +            // version = NFNETLINK_V0
            "1234" +          // res_id = 0x1234 (big endian)
             // struct nlattr
            "3400" +          // nla_len = 52
            "0180" +          // nla_type = nested CTA_TUPLE_ORIG
                // struct nlattr
                "1400" +      // nla_len = 20
                "0180" +      // nla_type = nested CTA_TUPLE_IP
                    "0800 0100 C0A8500C" +  // nla_type=CTA_IP_V4_SRC, ip=192.168.80.12
                    "0800 0200 8C700874" +  // nla_type=CTA_IP_V4_DST, ip=140.112.8.116
                // struct nlattr
                "1C00" +      // nla_len = 28
                "0280" +      // nla_type = nested CTA_TUPLE_PROTO
                    "0500 0100 06 000000" +  // nla_type=CTA_PROTO_NUM, proto=IPPROTO_TCP (6)
                    "0600 0200 F3F1 0000" +  // nla_type=CTA_PROTO_SRC_PORT, port=62449 (big endian)
                    "0600 0300 01BB 0000" +  // nla_type=CTA_PROTO_DST_PORT, port=443 (big endian)
            // struct nlattr
            "3400" +          // nla_len = 52
            "0280" +          // nla_type = nested CTA_TUPLE_REPLY
                // struct nlattr
                "1400" +      // nla_len = 20
                "0180" +      // nla_type = nested CTA_TUPLE_IP
                    "0800 0100 8C700874" +  // nla_type=CTA_IP_V4_SRC, ip=140.112.8.116
                    "0800 0200 6451B301" +  // nla_type=CTA_IP_V4_DST, ip=100.81.179.1
                // struct nlattr
                "1C00" +      // nla_len = 28
                "0280" +      // nla_type = nested CTA_TUPLE_PROTO
                    "0500 0100 06 000000" +  // nla_type=CTA_PROTO_NUM, proto=IPPROTO_TCP (6)
                    "0600 0200 01BB 0000" +  // nla_type=CTA_PROTO_SRC_PORT, port=443 (big endian)
                    "0600 0300 F3F1 0000" +  // nla_type=CTA_PROTO_DST_PORT, port=62449 (big endian)
            // struct nlattr
            "0800" +          // nla_len = 8
            "0300" +          // nla_type = CTA_STATUS
            "00000198" +      // nla_value = 0b110011000 (big endian)
                              // IPS_CONFIRMED (1 << 3) | IPS_SRC_NAT (1 << 4) |
                              // IPS_SRC_NAT_DONE (1 << 7) | IPS_DST_NAT_DONE (1 << 8)
            // struct nlattr
            "0800" +          // nla_len = 8
            "0700" +          // nla_type = CTA_TIMEOUT
            "00000078";       // nla_value = 120 (big endian)
            // CHECKSTYLE:ON IndentationCheck
    public static final byte[] CT_V4NEW_TCP_BYTES =
            HexEncoding.decode(CT_V4NEW_TCP_HEX.replaceAll(" ", "").toCharArray(), false);

    @Test
    public void testParseCtNew() {
        assumeTrue(USING_LE);

        final ByteBuffer byteBuffer = ByteBuffer.wrap(CT_V4NEW_TCP_BYTES);
        byteBuffer.order(ByteOrder.nativeOrder());
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, OsConstants.NETLINK_NETFILTER);
        assertNotNull(msg);
        assertTrue(msg instanceof ConntrackMessage);
        final ConntrackMessage conntrackMessage = (ConntrackMessage) msg;

        final StructNlMsgHdr hdr = conntrackMessage.getHeader();
        assertNotNull(hdr);
        assertEquals(140, hdr.nlmsg_len);
        assertEquals(makeCtType(IPCTNL_MSG_CT_NEW), hdr.nlmsg_type);
        assertEquals((short) (StructNlMsgHdr.NLM_F_CREATE | StructNlMsgHdr.NLM_F_EXCL),
                hdr.nlmsg_flags);
        assertEquals(0, hdr.nlmsg_seq);
        assertEquals(0, hdr.nlmsg_pid);

        final StructNfGenMsg nfmsgHdr = conntrackMessage.nfGenMsg;
        assertNotNull(nfmsgHdr);
        assertEquals((byte) OsConstants.AF_INET, nfmsgHdr.nfgen_family);
        assertEquals((byte) StructNfGenMsg.NFNETLINK_V0, nfmsgHdr.version);
        assertEquals((short) 0x1234, nfmsgHdr.res_id);

        assertEquals(InetAddress.parseNumericAddress("192.168.80.12"),
                conntrackMessage.tupleOrig.srcIp);
        assertEquals(InetAddress.parseNumericAddress("140.112.8.116"),
                conntrackMessage.tupleOrig.dstIp);
        assertEquals((byte) OsConstants.IPPROTO_TCP, conntrackMessage.tupleOrig.protoNum);
        assertEquals((short) 62449, conntrackMessage.tupleOrig.srcPort);
        assertEquals((short) 443, conntrackMessage.tupleOrig.dstPort);

        assertEquals(InetAddress.parseNumericAddress("140.112.8.116"),
                conntrackMessage.tupleReply.srcIp);
        assertEquals(InetAddress.parseNumericAddress("100.81.179.1"),
                conntrackMessage.tupleReply.dstIp);
        assertEquals((byte) OsConstants.IPPROTO_TCP, conntrackMessage.tupleReply.protoNum);
        assertEquals((short) 443, conntrackMessage.tupleReply.srcPort);
        assertEquals((short) 62449, conntrackMessage.tupleReply.dstPort);

        assertEquals(0x198, conntrackMessage.status);
        assertEquals(120, conntrackMessage.timeoutSec);
    }

    @Test
    public void testParseTruncation() {
        assumeTrue(USING_LE);

        // Expect no crash while parsing the truncated message which has been truncated to every
        // length between 0 and its full length - 1.
        for (int len = 0; len < CT_V4NEW_TCP_BYTES.length; len++) {
            final byte[] truncated = Arrays.copyOfRange(CT_V4NEW_TCP_BYTES, 0, len);

            final ByteBuffer byteBuffer = ByteBuffer.wrap(truncated);
            byteBuffer.order(ByteOrder.nativeOrder());
            final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer,
                    OsConstants.NETLINK_NETFILTER);
        }
    }

    @Test
    public void testParseTruncationWithInvalidByte() {
        assumeTrue(USING_LE);

        // Expect no crash while parsing the message which is truncated by invalid bytes. The
        // message has been truncated to every length between 0 and its full length - 1.
        for (byte invalid : new byte[]{(byte) 0x00, (byte) 0xff}) {
            for (int len = 0; len < CT_V4NEW_TCP_BYTES.length; len++) {
                final byte[] truncated = new byte[CT_V4NEW_TCP_BYTES.length];
                Arrays.fill(truncated, (byte) invalid);
                System.arraycopy(CT_V4NEW_TCP_BYTES, 0, truncated, 0, len);

                final ByteBuffer byteBuffer = ByteBuffer.wrap(truncated);
                byteBuffer.order(ByteOrder.nativeOrder());
                final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer,
                        OsConstants.NETLINK_NETFILTER);
            }
        }
    }

    // Malformed conntrack messages.
    public static final String CT_MALFORMED_HEX =
            // CHECKSTYLE:OFF IndentationCheck
            // <--           nlmsghr           -->|<-nfgenmsg->|<--    CTA_TUPLE_ORIG     -->|
            // CTA_TUPLE_ORIG has no nla_value.
            "18000000 0001 0006 00000000 00000000   02 00 0000 0400 0180"
            // nested CTA_TUPLE_IP has no nla_value.
            + "1C000000 0001 0006 00000000 00000000 02 00 0000 0800 0180 0400 0180"
            // nested CTA_IP_V4_SRC has no nla_value.
            + "20000000 0001 0006 00000000 00000000 02 00 0000 0C00 0180 0800 0180 0400 0100"
            // nested CTA_TUPLE_PROTO has no nla_value.
            // <--           nlmsghr           -->|<-nfgenmsg->|<--    CTA_TUPLE_ORIG
            + "30000000 0001 0006 00000000 00000000 02 00 0000 1C00 0180 1400 0180 0800 0100"
            //                                  -->|
            + "C0A8500C 0800 0200 8C700874 0400 0280";
            // CHECKSTYLE:ON IndentationCheck
    public static final byte[] CT_MALFORMED_BYTES =
            HexEncoding.decode(CT_MALFORMED_HEX.replaceAll(" ", "").toCharArray(), false);

    @Test
    public void testParseMalformation() {
        assumeTrue(USING_LE);

        final ByteBuffer byteBuffer = ByteBuffer.wrap(CT_MALFORMED_BYTES);
        byteBuffer.order(ByteOrder.nativeOrder());

        // Expect no crash while parsing the malformed message.
        int messageCount = 0;
        while (byteBuffer.remaining() > 0) {
            final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer,
                    OsConstants.NETLINK_NETFILTER);
            messageCount++;
        }
        assertEquals(4, messageCount);
    }

    @Test
    public void testToString() {
        assumeTrue(USING_LE);

        final ByteBuffer byteBuffer = ByteBuffer.wrap(CT_V4NEW_TCP_BYTES);
        byteBuffer.order(ByteOrder.nativeOrder());
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, OsConstants.NETLINK_NETFILTER);
        assertNotNull(msg);
        assertTrue(msg instanceof ConntrackMessage);
        final ConntrackMessage conntrackMessage = (ConntrackMessage) msg;

        // Bug: "nlmsg_flags{1536(NLM_F_MATCH))" is not correct because StructNlMsgHdr
        // #stringForNlMsgFlags can't convert all flags (ex: NLM_F_CREATE) and can't distinguish
        // the flags which have the same value (ex: NLM_F_MATCH <0x200> and NLM_F_EXCL <0x200>).
        // The flags output string should be "NLM_F_CREATE|NLM_F_EXCL" in this case.
        // TODO: correct the flag converted string once #stringForNlMsgFlags does.
        final String expected = ""
                + "ConntrackMessage{"
                + "nlmsghdr{StructNlMsgHdr{ nlmsg_len{140}, nlmsg_type{256(IPCTNL_MSG_CT_NEW)}, "
                + "nlmsg_flags{1536(NLM_F_MATCH)}, nlmsg_seq{0}, nlmsg_pid{0} }}, "
                + "nfgenmsg{NfGenMsg{ nfgen_family{AF_INET}, version{0}, res_id{4660} }}, "
                + "tuple_orig{Tuple{IPPROTO_TCP: 192.168.80.12:62449 -> 140.112.8.116:443}}, "
                + "tuple_reply{Tuple{IPPROTO_TCP: 140.112.8.116:443 -> 100.81.179.1:62449}}, "
                + "status{408(IPS_CONFIRMED|IPS_SRC_NAT|IPS_SRC_NAT_DONE|IPS_DST_NAT_DONE)}, "
                + "timeout_sec{120}}";
        assertEquals(expected, conntrackMessage.toString());
    }
}
