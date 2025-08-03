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

package com.android.net.module.util.netlink;

import static android.system.OsConstants.IFA_F_PERMANENT;
import static android.system.OsConstants.NETLINK_ROUTE;
import static android.system.OsConstants.RT_SCOPE_LINK;
import static android.system.OsConstants.RT_SCOPE_UNIVERSE;

import static com.android.testutils.MiscAsserts.assertThrows;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.InetAddresses;
import android.system.OsConstants;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.HexDump;

import libcore.util.HexEncoding;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class RtNetlinkAddressMessageTest {
    private static final Inet6Address TEST_LINK_LOCAL =
            (Inet6Address) InetAddresses.parseNumericAddress("FE80::2C41:5CFF:FE09:6665");
    private static final Inet6Address TEST_GLOBAL_ADDRESS =
            (Inet6Address) InetAddresses.parseNumericAddress("2001:DB8:1::100");

    // An example of the full RTM_NEWADDR message.
    private static final String RTM_NEWADDR_HEX =
            "48000000140000000000000000000000"            // struct nlmsghr
            + "0A4080FD1E000000"                          // struct ifaddrmsg
            + "14000100FE800000000000002C415CFFFE096665"  // IFA_ADDRESS
            + "14000600100E0000201C00002A70000045700000"  // IFA_CACHEINFO
            + "0800080080000000";                         // IFA_FLAGS

    private ByteBuffer toByteBuffer(final String hexString) {
        return ByteBuffer.wrap(HexDump.hexStringToByteArray(hexString));
    }

    @Test
    public void testParseRtmNewAddress() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWADDR_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkAddressMessage);
        final RtNetlinkAddressMessage addrMsg = (RtNetlinkAddressMessage) msg;

        final StructNlMsgHdr hdr = addrMsg.getHeader();
        assertNotNull(hdr);
        assertEquals(72, hdr.nlmsg_len);
        assertEquals(NetlinkConstants.RTM_NEWADDR, hdr.nlmsg_type);
        assertEquals(0, hdr.nlmsg_flags);
        assertEquals(0, hdr.nlmsg_seq);
        assertEquals(0, hdr.nlmsg_pid);

        final StructIfaddrMsg ifaddrMsgHdr = addrMsg.getIfaddrHeader();
        assertNotNull(ifaddrMsgHdr);
        assertEquals((byte) OsConstants.AF_INET6, ifaddrMsgHdr.family);
        assertEquals(64, ifaddrMsgHdr.prefixLen);
        assertEquals(0x80, ifaddrMsgHdr.flags);
        assertEquals(0xFD, ifaddrMsgHdr.scope);
        assertEquals(30, ifaddrMsgHdr.index);

        assertEquals((Inet6Address) addrMsg.getIpAddress(), TEST_LINK_LOCAL);
        assertEquals(3600L, addrMsg.getIfacacheInfo().preferred);
        assertEquals(7200L, addrMsg.getIfacacheInfo().valid);
        assertEquals(28714, addrMsg.getIfacacheInfo().cstamp);
        assertEquals(28741, addrMsg.getIfacacheInfo().tstamp);
        assertEquals(0x80, addrMsg.getFlags());
    }

    private static final String RTM_NEWADDR_PACK_HEX =
            "48000000140000000000000000000000"             // struct nlmsghr
            + "0A4080FD1E000000"                           // struct ifaddrmsg
            + "14000100FE800000000000002C415CFFFE096665"   // IFA_ADDRESS
            + "14000600FFFFFFFFFFFFFFFF2A7000002A700000"   // IFA_CACHEINFO
            + "0800080081000000";                          // IFA_FLAGS(override ifa_flags)

    @Test
    public void testPackRtmNewAddr() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWADDR_PACK_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkAddressMessage);
        final RtNetlinkAddressMessage addrMsg = (RtNetlinkAddressMessage) msg;

        final ByteBuffer packBuffer = ByteBuffer.allocate(72);
        packBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        addrMsg.pack(packBuffer);
        assertEquals(RTM_NEWADDR_PACK_HEX, HexDump.toHexString(packBuffer.array()));
    }

    private static final String RTM_NEWADDR_TRUNCATED_HEX =
            "44000000140000000000000000000000"            // struct nlmsghr
            + "0A4080FD1E000000"                          // struct ifaddrmsg
            + "10000100FE800000000000002C415CFF"          // IFA_ADDRESS(truncated)
            + "14000600FFFFFFFFFFFFFFFF2A7000002A700000"  // IFA_CACHEINFO
            + "0800080080000000";                         // IFA_FLAGS

    @Test
    public void testTruncatedRtmNewAddr() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWADDR_TRUNCATED_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        // Parsing RTM_NEWADDR with truncated IFA_ADDRESS attribute returns null.
        assertNull(msg);
    }

    @Test
    public void testCreateRtmNewAddressMessage() {
        // Hexadecimal representation of our created packet.
        final String expectedNewAddressHex =
                // struct nlmsghdr
                "48000000" +    // length = 72
                "1400" +        // type = 20 (RTM_NEWADDR)
                "0501" +        // flags = NLM_F_ACK | NLM_F_REQUEST | NLM_F_REPLACE
                "01000000" +    // seqno = 1
                "00000000" +    // pid = 0 (send to kernel)
                // struct IfaddrMsg
                "0A" +          // family = inet6
                "40" +          // prefix len = 64
                "00" +          // flags = 0
                "FD" +          // scope = RT_SCOPE_LINK
                "17000000" +    // ifindex = 23
                // struct nlattr: IFA_ADDRESS
                "1400" +        // len
                "0100" +        // type
                "FE800000000000002C415CFFFE096665" + // IP address = fe80::2C41:5cff:fe09:6665
                // struct nlattr: IFA_CACHEINFO
                "1400" +        // len
                "0600" +        // type
                "FFFFFFFF" +    // preferred = infinite
                "FFFFFFFF" +    // valid = infinite
                "00000000" +    // cstamp
                "00000000" +    // tstamp
                // struct nlattr: IFA_FLAGS
                "0800" +        // len
                "0800" +        // type
                "80000000";     // flags = IFA_F_PERMANENT
        final byte[] expectedNewAddress =
                HexEncoding.decode(expectedNewAddressHex.toCharArray(), false);

        final byte[] bytes = RtNetlinkAddressMessage.newRtmNewAddressMessage(1 /* seqno */,
                TEST_LINK_LOCAL, (short) 64 /* prefix len */, IFA_F_PERMANENT /* flags */,
                (byte) RT_SCOPE_LINK /* scope */, 23 /* ifindex */,
                (long) 0xFFFFFFFF /* preferred */, (long) 0xFFFFFFFF /* valid */);
        assertArrayEquals(expectedNewAddress, bytes);
    }

    @Test
    public void testCreateRtmNewAddressMessage_IPv4Address() {
        // Hexadecimal representation of our created packet.
        final String expectedNewAddressHex =
                // struct nlmsghdr
                "4c000000"      // length = 76
                + "1400"        // type = 20 (RTM_NEWADDR)
                + "0501"        // flags = NLM_F_ACK | NLM_F_REQUEST | NLM_F_REPLACE
                + "01000000"    // seqno = 1
                + "00000000"    // pid = 0 (send to kernel)
                // struct IfaddrMsg
                + "02"          // family = inet
                + "18"          // prefix len = 24
                + "00"          // flags = 0
                + "00"          // scope = RT_SCOPE_UNIVERSE
                + "14000000"    // ifindex = 20
                // struct nlattr: IFA_ADDRESS
                + "0800"        // len
                + "0100"        // type
                + "C0A80491"    // IPv4 address = 192.168.4.145
                // struct nlattr: IFA_CACHEINFO
                + "1400"        // len
                + "0600"        // type
                + "C0A80000"    // preferred = 43200s
                + "C0A80000"    // valid = 43200s
                + "00000000"    // cstamp
                + "00000000"    // tstamp
                // struct nlattr: IFA_FLAGS
                + "0800"        // len
                + "0800"        // type
                + "00000000"    // flags = 0
                // struct nlattr: IFA_LOCAL
                + "0800"        // len
                + "0200"        // type
                + "C0A80491"    // local address = 192.168.4.145
                // struct nlattr: IFA_BROADCAST
                + "0800"        // len
                + "0400"        // type
                + "C0A804FF";   // broadcast address = 192.168.4.255
        final byte[] expectedNewAddress =
                HexEncoding.decode(expectedNewAddressHex.toCharArray(), false);

        final Inet4Address ipAddress =
                (Inet4Address) InetAddresses.parseNumericAddress("192.168.4.145");
        final byte[] bytes = RtNetlinkAddressMessage.newRtmNewAddressMessage(1 /* seqno */,
                ipAddress, (short) 24 /* prefix len */, 0 /* flags */,
                (byte) RT_SCOPE_UNIVERSE /* scope */, 20 /* ifindex */,
                (long) 0xA8C0 /* preferred */, (long) 0xA8C0 /* valid */);
        assertArrayEquals(expectedNewAddress, bytes);
    }

    @Test
    public void testCreateRtmDelAddressMessage() {
        // Hexadecimal representation of our created packet.
        final String expectedDelAddressHex =
                // struct nlmsghdr
                "2C000000" + // length = 44
                "1500" +     // type = 21 (RTM_DELADDR)
                "0500" +     // flags = NLM_F_ACK | NLM_F_REQUEST
                "01000000" + // seqno = 1
                "00000000" + // pid = 0 (send to kernel)
                // struct IfaddrMsg
                "0A" +       // family = inet6
                "40" +       // prefix len = 64
                "00" +       // flags = 0
                "00" +       // scope = RT_SCOPE_UNIVERSE
                "3B000000" + // ifindex = 59
                // struct nlattr: IFA_ADDRESS
                "1400" +     // len
                "0100" +     // type
                "20010DB8000100000000000000000100"; // IP address = 2001:db8:1::100
        final byte[] expectedDelAddress =
                HexEncoding.decode(expectedDelAddressHex.toCharArray(), false);

        final byte[] bytes = RtNetlinkAddressMessage.newRtmDelAddressMessage(1 /* seqno */,
                TEST_GLOBAL_ADDRESS, (short) 64 /* prefix len */, 59 /* ifindex */);
        assertArrayEquals(expectedDelAddress, bytes);
    }

    @Test
    public void testCreateRtmNewAddressMessage_nullIpAddress() {
        assertThrows(NullPointerException.class,
                () -> RtNetlinkAddressMessage.newRtmNewAddressMessage(1 /* seqno */,
                        null /* IP address */, (short) 0 /* prefix len */,
                        IFA_F_PERMANENT /* flags */, (byte) RT_SCOPE_LINK /* scope */,
                        23 /* ifindex */, (long) 0xFFFFFFFF /* preferred */,
                        (long) 0xFFFFFFFF /* valid */));
    }

    @Test
    public void testCreateRtmDelAddressMessage_nullIpAddress() {
        assertThrows(NullPointerException.class,
                () -> RtNetlinkAddressMessage.newRtmDelAddressMessage(1 /* seqno */,
                        null /* IP address */, (short) 0 /* prefix len */, 59 /* ifindex */));
    }

    @Test
    public void testCreateRtmNewAddressMessage_u32Flags() {
        // Hexadecimal representation of our created packet.
        final String expectedNewAddressHex =
                // struct nlmsghdr
                "48000000" +    // length = 72
                "1400" +        // type = 20 (RTM_NEWADDR)
                "0501" +        // flags = NLM_F_ACK | NLM_F_REQUEST | NLM_F_REPLACE
                "01000000" +    // seqno = 1
                "00000000" +    // pid = 0 (send to kernel)
                // struct IfaddrMsg
                "0A" +          // family = inet6
                "80" +          // prefix len = 128
                "00" +          // flags = 0
                "00" +          // scope = RT_SCOPE_UNIVERSE
                "17000000" +    // ifindex = 23
                // struct nlattr: IFA_ADDRESS
                "1400" +        // len
                "0100" +        // type
                "20010DB8000100000000000000000100" + // IP address = 2001:db8:1::100
                // struct nlattr: IFA_CACHEINFO
                "1400" +        // len
                "0600" +        // type
                "FFFFFFFF" +    // preferred = infinite
                "FFFFFFFF" +    // valid = infinite
                "00000000" +    // cstamp
                "00000000" +    // tstamp
                // struct nlattr: IFA_FLAGS
                "0800" +        // len
                "0800" +        // type
                "00030000";     // flags = IFA_F_MANAGETEMPADDR | IFA_F_NOPREFIXROUTE
        final byte[] expectedNewAddress =
                HexEncoding.decode(expectedNewAddressHex.toCharArray(), false);

        final byte[] bytes = RtNetlinkAddressMessage.newRtmNewAddressMessage(1 /* seqno */,
                TEST_GLOBAL_ADDRESS, (short) 128 /* prefix len */,
                (int) 0x300 /* flags: IFA_F_MANAGETEMPADDR | IFA_F_NOPREFIXROUTE */,
                (byte) RT_SCOPE_UNIVERSE /* scope */, 23 /* ifindex */,
                (long) 0xFFFFFFFF /* preferred */, (long) 0xFFFFFFFF /* valid */);
        assertArrayEquals(expectedNewAddress, bytes);
    }

    @Test
    public void testToString() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWADDR_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkAddressMessage);
        final RtNetlinkAddressMessage addrMsg = (RtNetlinkAddressMessage) msg;
        final String expected = "RtNetlinkAddressMessage{ "
                + "nlmsghdr{"
                + "StructNlMsgHdr{ nlmsg_len{72}, nlmsg_type{20(RTM_NEWADDR)}, nlmsg_flags{0()}, "
                + "nlmsg_seq{0}, nlmsg_pid{0} }}, "
                + "Ifaddrmsg{"
                + "family: 10, prefixLen: 64, flags: 128, scope: 253, index: 30}, "
                + "IP Address{fe80::2c41:5cff:fe09:6665}, "
                + "IfacacheInfo{"
                + "preferred: 3600, valid: 7200, cstamp: 28714, tstamp: 28741}, "
                + "Address Flags{00000080} "
                + "}";
        assertEquals(expected, addrMsg.toString());
    }
}
