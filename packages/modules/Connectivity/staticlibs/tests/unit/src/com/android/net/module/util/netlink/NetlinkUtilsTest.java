/*
 * Copyright (C) 2015 The Android Open Source Project
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
import static android.system.OsConstants.AF_UNSPEC;
import static android.system.OsConstants.EACCES;
import static android.system.OsConstants.NETLINK_ROUTE;
import static android.system.OsConstants.SOL_SOCKET;
import static android.system.OsConstants.SO_RCVBUF;
import static com.android.net.module.util.netlink.NetlinkConstants.RTNL_FAMILY_IP6MR;
import static com.android.net.module.util.netlink.NetlinkUtils.DEFAULT_RECV_BUFSIZE;
import static com.android.net.module.util.netlink.StructNlMsgHdr.NLM_F_DUMP;
import static com.android.net.module.util.netlink.StructNlMsgHdr.NLM_F_REQUEST;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.content.Context;
import android.net.util.SocketUtils;
import android.os.Build;
import android.system.ErrnoException;
import android.system.NetlinkSocketAddress;
import android.system.Os;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.Struct;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import libcore.io.IoUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class NetlinkUtilsTest {
    private static final String TAG = "NetlinkUtilsTest";
    private static final int TEST_SEQNO = 5;
    private static final int TEST_TIMEOUT_MS = 500;

    @Test
    public void testGetNeighborsQuery() throws Exception {
        final byte[] req = RtNetlinkNeighborMessage.newGetNeighborsRequest(TEST_SEQNO);
        assertNotNull(req);

        List<RtNetlinkNeighborMessage> msgs = new ArrayList<>();
        Consumer<RtNetlinkNeighborMessage> handleNlDumpMsg = (msg) -> {
            msgs.add(msg);
        };

        final Context ctx = InstrumentationRegistry.getInstrumentation().getContext();
        final int targetSdk =
                ctx.getPackageManager()
                        .getApplicationInfo(ctx.getPackageName(), 0)
                        .targetSdkVersion;

        // Apps targeting an SDK version > S are not allowed to send RTM_GETNEIGH{TBL} messages
        if (SdkLevel.isAtLeastT() && targetSdk > 31) {
            var ctxt = new String(Files.readAllBytes(Paths.get("/proc/thread-self/attr/current")));
            assumeFalse("must not be platform app", ctxt.startsWith("u:r:platform_app:s0:"));
            // NetworkStackCoverageTests uses the same UID with NetworkStack module, which
            // still has the permission to send RTM_GETNEIGH message (sepolicy just blocks the
            // access from untrusted_apps), also exclude the NetworkStackCoverageTests.
            assumeFalse("network_stack context is expected to have permission to send RTM_GETNEIGH",
                    ctxt.startsWith("u:r:network_stack:s0"));
            try {
                NetlinkUtils.<RtNetlinkNeighborMessage>getAndProcessNetlinkDumpMessages(req,
                        NETLINK_ROUTE, RtNetlinkNeighborMessage.class, handleNlDumpMsg);
                fail("RTM_GETNEIGH is not allowed for apps targeting SDK > 31 on T+ platforms,"
                        + " target SDK version: " + targetSdk);
            } catch (ErrnoException e) {
                // Expected
                assertEquals(e.errno, EACCES);
                return;
            }
        }

        // Check that apps targeting lower API levels / running on older platforms succeed
        NetlinkUtils.<RtNetlinkNeighborMessage>getAndProcessNetlinkDumpMessages(req,
                NETLINK_ROUTE, RtNetlinkNeighborMessage.class, handleNlDumpMsg);

        for (var msg : msgs) {
            assertNotNull(msg);
            final StructNlMsgHdr hdr = msg.getHeader();
            assertNotNull(hdr);
            assertEquals(NetlinkConstants.RTM_NEWNEIGH, hdr.nlmsg_type);
            assertTrue((hdr.nlmsg_flags & StructNlMsgHdr.NLM_F_MULTI) != 0);
            assertEquals(TEST_SEQNO, hdr.nlmsg_seq);
        }

        // TODO: make sure this test passes sanely in airplane mode.
        assertTrue(msgs.size() > 0);
    }

    @Test
    public void testBasicWorkingGetAddrQuery() throws Exception {
        final int testSeqno = 8;
        final byte[] req = newGetAddrRequest(testSeqno);
        assertNotNull(req);

        List<RtNetlinkAddressMessage> msgs = new ArrayList<>();
        Consumer<RtNetlinkAddressMessage> handleNlDumpMsg = (msg) -> {
            msgs.add(msg);
        };
        NetlinkUtils.<RtNetlinkAddressMessage>getAndProcessNetlinkDumpMessages(req, NETLINK_ROUTE,
                RtNetlinkAddressMessage.class, handleNlDumpMsg);

        boolean ipv4LoopbackAddressFound = false;
        boolean ipv6LoopbackAddressFound = false;
        final InetAddress loopbackIpv4 = InetAddress.getByName("127.0.0.1");
        final InetAddress loopbackIpv6 = InetAddress.getByName("::1");

        for (var msg : msgs) {
            assertNotNull(msg);
            final StructNlMsgHdr nlmsghdr = msg.getHeader();
            assertNotNull(nlmsghdr);
            assertEquals(NetlinkConstants.RTM_NEWADDR, nlmsghdr.nlmsg_type);
            assertTrue((nlmsghdr.nlmsg_flags & StructNlMsgHdr.NLM_F_MULTI) != 0);
            assertEquals(testSeqno, nlmsghdr.nlmsg_seq);
            assertTrue(msg instanceof RtNetlinkAddressMessage);
            // When parsing the full response we can see the RTM_NEWADDR messages representing for
            // IPv4 and IPv6 loopback address: 127.0.0.1 and ::1 and non-loopback addresses.
            final StructIfaddrMsg ifaMsg = ((RtNetlinkAddressMessage) msg).getIfaddrHeader();
            final InetAddress ipAddress = ((RtNetlinkAddressMessage) msg).getIpAddress();
            assertTrue(
                    "Non-IP address family: " + ifaMsg.family,
                    ifaMsg.family == AF_INET || ifaMsg.family == AF_INET6);
            assertNotNull(ipAddress);

            if (ipAddress.equals(loopbackIpv4)) {
                ipv4LoopbackAddressFound = true;
                assertTrue(ipAddress.isLoopbackAddress());
            }
            if (ipAddress.equals(loopbackIpv6)) {
                ipv6LoopbackAddressFound = true;
                assertTrue(ipAddress.isLoopbackAddress());
            }
        }

        assertTrue(msgs.size() > 0);
        // Check ipv4 and ipv6 loopback addresses are in the output
        assertTrue(ipv4LoopbackAddressFound && ipv6LoopbackAddressFound);
    }

    /** A convenience method to create an RTM_GETADDR request message. */
    private static byte[] newGetAddrRequest(int seqNo) {
        final int length = StructNlMsgHdr.STRUCT_SIZE + Struct.getSize(StructIfaddrMsg.class);
        final byte[] bytes = new byte[length];
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.nativeOrder());

        final StructNlMsgHdr nlmsghdr = new StructNlMsgHdr();
        nlmsghdr.nlmsg_len = length;
        nlmsghdr.nlmsg_type = NetlinkConstants.RTM_GETADDR;
        nlmsghdr.nlmsg_flags = NLM_F_REQUEST | NLM_F_DUMP;
        nlmsghdr.nlmsg_seq = seqNo;
        nlmsghdr.pack(byteBuffer);

        final StructIfaddrMsg addrMsg = new StructIfaddrMsg((byte) AF_UNSPEC /* family */,
                (short) 0 /* prefixLen */, (short) 0 /* flags */, (short) 0 /* scope */,
                0 /* index */);
        addrMsg.pack(byteBuffer);

        return bytes;
    }

    @Test
    public void testGetIpv6MulticastRoutes_doesNotThrow() {
        var multicastRoutes = NetlinkUtils.getIpv6MulticastRoutes();

        for (var route : multicastRoutes) {
            assertNotNull(route);
            assertEquals("Route is not IP6MR: " + route,
                    RTNL_FAMILY_IP6MR, route.getRtmFamily());
            assertNotNull("Route doesn't contain source: " + route, route.getSource());
            assertNotNull("Route doesn't contain destination: " + route, route.getDestination());
        }
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.R) // getsockoptInt requires > R
    public void testNetlinkSocketForProto_defaultBufferSize() throws Exception {
        final FileDescriptor fd = NetlinkUtils.netlinkSocketForProto(NETLINK_ROUTE);
        final int bufferSize = Os.getsockoptInt(fd, SOL_SOCKET, SO_RCVBUF) / 2;

        assertTrue("bufferSize: " + bufferSize, bufferSize > 0); // whatever the default value is
        SocketUtils.closeSocket(fd);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.R) // getsockoptInt requires > R
    public void testNetlinkSocketForProto_setBufferSize() throws Exception {
        final FileDescriptor fd = NetlinkUtils.netlinkSocketForProto(NETLINK_ROUTE,
                8000);
        final int bufferSize = Os.getsockoptInt(fd, SOL_SOCKET, SO_RCVBUF) / 2;

        assertEquals(8000, bufferSize);
        SocketUtils.closeSocket(fd);
    }
}
