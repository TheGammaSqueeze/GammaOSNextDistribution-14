/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.networkstack.tethering.util;

import static android.net.TetheringManager.CONNECTIVITY_SCOPE_LOCAL;
import static android.net.TetheringManager.TETHERING_USB;
import static android.net.TetheringManager.TETHERING_WIFI;
import static android.system.OsConstants.AF_UNIX;
import static android.system.OsConstants.EAGAIN;
import static android.system.OsConstants.SOCK_CLOEXEC;
import static android.system.OsConstants.SOCK_DGRAM;
import static android.system.OsConstants.SOCK_NONBLOCK;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.net.LinkAddress;
import android.net.MacAddress;
import android.net.TetheringRequestParcel;
import android.system.ErrnoException;
import android.system.Os;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.Ipv6Utils;
import com.android.net.module.util.NetworkStackConstants;
import com.android.net.module.util.Struct;
import com.android.net.module.util.structs.EthernetHeader;
import com.android.net.module.util.structs.Icmpv6Header;
import com.android.net.module.util.structs.Ipv6Header;
import com.android.testutils.MiscAsserts;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TetheringUtilsTest {
    private static final LinkAddress TEST_SERVER_ADDR = new LinkAddress("192.168.43.1/24");
    private static final LinkAddress TEST_CLIENT_ADDR = new LinkAddress("192.168.43.5/24");
    private static final int PACKET_SIZE = 1500;

    private TetheringRequestParcel mTetheringRequest;

    @Before
    public void setUp() {
        mTetheringRequest = makeTetheringRequestParcel();
    }

    public TetheringRequestParcel makeTetheringRequestParcel() {
        final TetheringRequestParcel request = new TetheringRequestParcel();
        request.tetheringType = TETHERING_WIFI;
        request.localIPv4Address = TEST_SERVER_ADDR;
        request.staticClientAddress = TEST_CLIENT_ADDR;
        request.exemptFromEntitlementCheck = false;
        request.showProvisioningUi = true;
        return request;
    }

    @Test
    public void testIsTetheringRequestEquals() {
        TetheringRequestParcel request = makeTetheringRequestParcel();

        assertTrue(TetheringUtils.isTetheringRequestEquals(mTetheringRequest, mTetheringRequest));
        assertTrue(TetheringUtils.isTetheringRequestEquals(mTetheringRequest, request));
        assertTrue(TetheringUtils.isTetheringRequestEquals(null, null));
        assertFalse(TetheringUtils.isTetheringRequestEquals(mTetheringRequest, null));
        assertFalse(TetheringUtils.isTetheringRequestEquals(null, mTetheringRequest));

        request = makeTetheringRequestParcel();
        request.tetheringType = TETHERING_USB;
        assertFalse(TetheringUtils.isTetheringRequestEquals(mTetheringRequest, request));

        request = makeTetheringRequestParcel();
        request.localIPv4Address = null;
        request.staticClientAddress = null;
        assertFalse(TetheringUtils.isTetheringRequestEquals(mTetheringRequest, request));

        request = makeTetheringRequestParcel();
        request.exemptFromEntitlementCheck = true;
        assertFalse(TetheringUtils.isTetheringRequestEquals(mTetheringRequest, request));

        request = makeTetheringRequestParcel();
        request.showProvisioningUi = false;
        assertFalse(TetheringUtils.isTetheringRequestEquals(mTetheringRequest, request));

        request = makeTetheringRequestParcel();
        request.connectivityScope = CONNECTIVITY_SCOPE_LOCAL;
        assertFalse(TetheringUtils.isTetheringRequestEquals(mTetheringRequest, request));

        MiscAsserts.assertFieldCountEquals(6, TetheringRequestParcel.class);
    }

    // Writes the specified packet to a filedescriptor, skipping the Ethernet header.
    // Needed because the Ipv6Utils methods for building packets always include the Ethernet header,
    // but socket filters applied by TetheringUtils expect the packet to start from the IP header.
    private int writePacket(FileDescriptor fd, ByteBuffer pkt) throws Exception {
        pkt.flip();
        int offset = Struct.getSize(EthernetHeader.class);
        int len = pkt.capacity() - offset;
        return Os.write(fd, pkt.array(), offset, len);
    }

    // Reads a packet from the filedescriptor.
    private ByteBuffer readIpPacket(FileDescriptor fd) throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(PACKET_SIZE);
        Os.read(fd, buf);
        return buf;
    }

    private interface SocketFilter {
        void apply(FileDescriptor fd) throws Exception;
    }

    private ByteBuffer checkIcmpSocketFilter(ByteBuffer passed, ByteBuffer dropped,
            SocketFilter filter) throws Exception {
        FileDescriptor in = new FileDescriptor();
        FileDescriptor out = new FileDescriptor();
        Os.socketpair(AF_UNIX, SOCK_DGRAM | SOCK_NONBLOCK | SOCK_CLOEXEC, 0, in, out);

        // Before the filter is applied, it doesn't drop anything.
        int len = writePacket(out, dropped);
        ByteBuffer received = readIpPacket(in);
        assertEquals(len, received.position());

        // Install the socket filter. Then write two packets, the first expected to be dropped and
        // the second expected to be passed. Check that only the second makes it through.
        filter.apply(in);
        writePacket(out, dropped);
        len = writePacket(out, passed);
        received = readIpPacket(in);
        assertEquals(len, received.position());
        received.flip();

        // Check there are no more packets to read.
        try {
            readIpPacket(in);
        } catch (ErrnoException expected) {
            assertEquals(EAGAIN, expected.errno);
        }

        return received;
    }

    @Test
    public void testIcmpSocketFilters() throws Exception {
        MacAddress mac1 = MacAddress.fromString("11:22:33:44:55:66");
        MacAddress mac2 = MacAddress.fromString("aa:bb:cc:dd:ee:ff");
        Inet6Address ll1 = (Inet6Address) InetAddress.getByName("fe80::1");
        Inet6Address ll2 = (Inet6Address) InetAddress.getByName("fe80::abcd");
        Inet6Address allRouters = NetworkStackConstants.IPV6_ADDR_ALL_ROUTERS_MULTICAST;

        final ByteBuffer na = Ipv6Utils.buildNaPacket(mac1, mac2, ll1, ll2, 0, ll1);
        final ByteBuffer ns = Ipv6Utils.buildNsPacket(mac1, mac2, ll1, ll2, ll1);
        final ByteBuffer rs = Ipv6Utils.buildRsPacket(mac1, mac2, ll1, allRouters);

        ByteBuffer received = checkIcmpSocketFilter(na /* passed */, rs /* dropped */,
                TetheringUtils::setupNaSocket);

        Struct.parse(Ipv6Header.class, received);  // Skip IPv6 header.
        Icmpv6Header icmpv6 = Struct.parse(Icmpv6Header.class, received);
        assertEquals(NetworkStackConstants.ICMPV6_NEIGHBOR_ADVERTISEMENT, icmpv6.type);

        received = checkIcmpSocketFilter(ns /* passed */, rs /* dropped */,
                TetheringUtils::setupNsSocket);

        Struct.parse(Ipv6Header.class, received);  // Skip IPv6 header.
        icmpv6 = Struct.parse(Icmpv6Header.class, received);
        assertEquals(NetworkStackConstants.ICMPV6_NEIGHBOR_SOLICITATION, icmpv6.type);
    }
}
