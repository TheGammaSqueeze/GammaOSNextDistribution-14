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

import static android.system.OsConstants.NETLINK_ROUTE;

import static com.android.net.module.util.NetworkStackConstants.ETHER_MTU;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.MacAddress;
import android.system.OsConstants;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.HexDump;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class RtNetlinkLinkMessageTest {

    // An example of the full RTM_NEWLINK message.
    private static final String RTM_NEWLINK_HEX =
            "64000000100000000000000000000000"   // struct nlmsghr
            + "000001001E0000000210000000000000" // struct ifinfo
            + "0A000300776C616E30000000"         // IFLA_IFNAME(wlan0)
            + "08000D00B80B0000"                 // IFLA_PROTINFO
            + "0500100002000000"                 // IFLA_OPERSTATE
            + "0500110001000000"                 // IFLA_LINKMODE
            + "08000400DC050000"                 // IFLA_MTU
            + "0A00010092C3E3C9374E0000"         // IFLA_ADDRESS
            + "0A000200FFFFFFFFFFFF0000";        // IFLA_BROADCAST

    private ByteBuffer toByteBuffer(final String hexString) {
        return ByteBuffer.wrap(HexDump.hexStringToByteArray(hexString));
    }

    @Test
    public void testParseRtmNewLink() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWLINK_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkLinkMessage);
        final RtNetlinkLinkMessage linkMsg = (RtNetlinkLinkMessage) msg;

        final StructNlMsgHdr hdr = linkMsg.getHeader();
        assertNotNull(hdr);
        assertEquals(100, hdr.nlmsg_len);
        assertEquals(NetlinkConstants.RTM_NEWLINK, hdr.nlmsg_type);
        assertEquals(0, hdr.nlmsg_flags);
        assertEquals(0, hdr.nlmsg_seq);
        assertEquals(0, hdr.nlmsg_pid);

        final StructIfinfoMsg ifinfomsgHdr = linkMsg.getIfinfoHeader();
        assertNotNull(ifinfomsgHdr);
        assertEquals((byte) OsConstants.AF_UNSPEC, ifinfomsgHdr.family);
        assertEquals(OsConstants.ARPHRD_ETHER, ifinfomsgHdr.type);
        assertEquals(30, ifinfomsgHdr.index);
        assertEquals(0, ifinfomsgHdr.change);

        assertEquals(ETHER_MTU, linkMsg.getMtu());
        assertEquals(MacAddress.fromString("92:C3:E3:C9:37:4E"), linkMsg.getHardwareAddress());
        assertTrue(linkMsg.getInterfaceName().equals("wlan0"));
    }

    /**
     * Example:
     * # adb shell ip tunnel add トン0 mode sit local any remote 8.8.8.8
     * # adb shell ip link show | grep トン
     * 33: トン0@NONE: <POINTOPOINT,NOARP> mtu 1480 qdisc noop state DOWN mode DEFAULT group
     *     default qlen 1000
     *
     * IFLA_IFNAME attribute: \x0c\x00\x03\x00\xe3\x83\x88\xe3\x83\xb3\x30\x00
     *     length: 0x000c
     *     type: 0x0003
     *     value: \xe3\x83\x88\xe3\x83\xb3\x30\x00
     *            ト (\xe3\x83\x88)
     *            ン (\xe3\x83\xb3)
     *            0  (\x30)
     *            null terminated (\x00)
     */
    private static final String RTM_NEWLINK_UTF8_HEX =
            "34000000100000000000000000000000"   // struct nlmsghr
            + "000001001E0000000210000000000000" // struct ifinfo
            + "08000400DC050000"                 // IFLA_MTU
            + "0A00010092C3E3C9374E0000"         // IFLA_ADDRESS
            + "0C000300E38388E383B33000";        // IFLA_IFNAME(トン0)

    @Test
    public void testParseRtmNewLink_utf8Ifname() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWLINK_UTF8_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkLinkMessage);
        final RtNetlinkLinkMessage linkMsg = (RtNetlinkLinkMessage) msg;

        assertTrue(linkMsg.getInterfaceName().equals("トン0"));
    }

    private static final String RTM_NEWLINK_PACK_HEX =
            "34000000100000000000000000000000"   // struct nlmsghr
            + "000001001E0000000210000000000000" // struct ifinfo
            + "08000400DC050000"                 // IFLA_MTU
            + "0A00010092C3E3C9374E0000"         // IFLA_ADDRESS
            + "0A000300776C616E30000000";        // IFLA_IFNAME(wlan0)

    @Test
    public void testPackRtmNewLink() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWLINK_PACK_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkLinkMessage);
        final RtNetlinkLinkMessage linkMsg = (RtNetlinkLinkMessage) msg;

        final ByteBuffer packBuffer = ByteBuffer.allocate(64);
        packBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        linkMsg.pack(packBuffer);
        assertEquals(RTM_NEWLINK_PACK_HEX, HexDump.toHexString(packBuffer.array()));
    }

    private static final String RTM_NEWLINK_TRUNCATED_HEX =
            "54000000100000000000000000000000"   // struct nlmsghr
            + "000001001E0000000210000000000000" // struct ifinfo
            + "08000D00B80B0000"                 // IFLA_PROTINFO
            + "0500100002000000"                 // IFLA_OPERSTATE
            + "0800010092C3E3C9"                 // IFLA_ADDRESS(truncated)
            + "0500110001000000"                 // IFLA_LINKMODE
            + "0A000300776C616E30000000"         // IFLA_IFNAME(wlan0)
            + "08000400DC050000";                // IFLA_MTU

    @Test
    public void testTruncatedRtmNewLink() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWLINK_TRUNCATED_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkLinkMessage);
        final RtNetlinkLinkMessage linkMsg = (RtNetlinkLinkMessage) msg;

        // Truncated IFLA_ADDRESS attribute doesn't affect parsing other attrs.
        assertNull(linkMsg.getHardwareAddress());
        assertEquals(ETHER_MTU, linkMsg.getMtu());
        assertTrue(linkMsg.getInterfaceName().equals("wlan0"));
    }

    @Test
    public void testToString() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWLINK_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkLinkMessage);
        final RtNetlinkLinkMessage linkMsg = (RtNetlinkLinkMessage) msg;
        final String expected = "RtNetlinkLinkMessage{ "
                + "nlmsghdr{"
                + "StructNlMsgHdr{ nlmsg_len{100}, nlmsg_type{16(RTM_NEWLINK)}, nlmsg_flags{0()}, "
                + "nlmsg_seq{0}, nlmsg_pid{0} }}, "
                + "Ifinfomsg{"
                + "family: 0, type: 1, index: 30, flags: 4098, change: 0}, "
                + "Hardware Address{92:c3:e3:c9:37:4e}, " + "MTU{1500}, "
                + "Ifname{wlan0} "
                + "}";
        assertEquals(expected, linkMsg.toString());
    }
}
