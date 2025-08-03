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

package com.android.net.module.util.ip;

import static android.system.OsConstants.AF_UNIX;
import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.SOCK_DGRAM;

import static com.android.net.module.util.netlink.ConntrackMessage.Tuple;
import static com.android.net.module.util.netlink.ConntrackMessage.TupleIpv4;
import static com.android.net.module.util.netlink.ConntrackMessage.TupleProto;
import static com.android.net.module.util.netlink.NetlinkConstants.IPCTNL_MSG_CT_DELETE;
import static com.android.net.module.util.netlink.NetlinkConstants.IPCTNL_MSG_CT_NEW;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.net.InetAddresses;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.SharedLog;
import com.android.net.module.util.ip.ConntrackMonitor.ConntrackEvent;
import com.android.net.module.util.netlink.NetlinkConstants;
import com.android.net.module.util.netlink.NetlinkUtils;

import libcore.util.HexEncoding;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;
import java.io.InterruptedIOException;
import java.net.Inet4Address;

/**
 * Tests for ConntrackMonitor.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ConntrackMonitorTest {
    private static final long TIMEOUT_MS = 10_000L;

    @Mock private SharedLog mLog;
    @Mock private ConntrackMonitor.ConntrackEventConsumer mConsumer;

    private final HandlerThread mHandlerThread = new HandlerThread(
            ConntrackMonitorTest.class.getSimpleName());

    // Late init since the handler thread has been started.
    private Handler mHandler;
    private TestConntrackMonitor mConntrackMonitor;

    // A version of [ConntrackMonitor] that reads packets from the socket pair, and instead
    // allows the test to write test packets to the socket pair via [sendMessage].
    private class TestConntrackMonitor extends ConntrackMonitor {
        TestConntrackMonitor(@NonNull Handler h, @NonNull SharedLog log,
                @NonNull ConntrackEventConsumer cb) {
            super(h, log, cb);

            mReadFd = new FileDescriptor();
            mWriteFd = new FileDescriptor();
            try {
                Os.socketpair(AF_UNIX, SOCK_DGRAM, 0, mWriteFd, mReadFd);
            } catch (ErrnoException e) {
                fail("Could not create socket pair: " + e);
            }
        }

        @Override
        protected FileDescriptor createFd() {
            return mReadFd;
        }

        private void sendMessage(byte[] msg) {
            mHandler.post(() -> {
                try {
                    NetlinkUtils.sendMessage(mWriteFd, msg, 0 /* offset */, msg.length,
                                              TIMEOUT_MS);
                } catch (ErrnoException | InterruptedIOException e) {
                    fail("Unable to send netfilter message: " + e);
                }
            });
        }

        private final FileDescriptor mReadFd;
        private final FileDescriptor mWriteFd;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        // ConntrackMonitor needs to be started from the handler thread.
        final ConditionVariable initDone = new ConditionVariable();
        mHandler.post(() -> {
            TestConntrackMonitor m = new TestConntrackMonitor(mHandler, mLog, mConsumer);
            m.start();
            mConntrackMonitor = m;

            initDone.open();
        });
        if (!initDone.block(TIMEOUT_MS)) {
            fail("... init monitor timed-out after " + TIMEOUT_MS + "ms");
        }
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quitSafely();
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
            "0000019e" +      // nla_value = 0b110011110 (big endian)
                              // IPS_SEEN_REPLY (1 << 1) | IPS_ASSURED (1 << 2) |
                              // IPS_CONFIRMED (1 << 3) | IPS_SRC_NAT (1 << 4) |
                              // IPS_SRC_NAT_DONE (1 << 7) | IPS_DST_NAT_DONE (1 << 8)
            // struct nlattr
            "0800" +          // nla_len = 8
            "0700" +          // nla_type = CTA_TIMEOUT
            "00000078";       // nla_value = 120 (big endian)
            // CHECKSTYLE:ON IndentationCheck
    public static final byte[] CT_V4NEW_TCP_BYTES =
            HexEncoding.decode(CT_V4NEW_TCP_HEX.replaceAll(" ", "").toCharArray(), false);

    @NonNull
    private ConntrackEvent makeTestConntrackEvent(short msgType, int status, int timeoutSec) {
        final Inet4Address privateIp =
                (Inet4Address) InetAddresses.parseNumericAddress("192.168.80.12");
        final Inet4Address remoteIp =
                (Inet4Address) InetAddresses.parseNumericAddress("140.112.8.116");
        final Inet4Address publicIp =
                (Inet4Address) InetAddresses.parseNumericAddress("100.81.179.1");

        return new ConntrackEvent(
                (short) (NetlinkConstants.NFNL_SUBSYS_CTNETLINK << 8 | msgType),
                new Tuple(new TupleIpv4(privateIp, remoteIp),
                        new TupleProto((byte) IPPROTO_TCP, (short) 62449, (short) 443)),
                new Tuple(new TupleIpv4(remoteIp, publicIp),
                        new TupleProto((byte) IPPROTO_TCP, (short) 443, (short) 62449)),
                status,
                timeoutSec);
    }

    @Test
    public void testConntrackEventNew() throws Exception {
        final ConntrackEvent expectedEvent = makeTestConntrackEvent(IPCTNL_MSG_CT_NEW,
                0x19e /* status */, 120 /* timeoutSec */);
        mConntrackMonitor.sendMessage(CT_V4NEW_TCP_BYTES);
        verify(mConsumer, timeout(TIMEOUT_MS)).accept(eq(expectedEvent));
    }

    @Test
    public void testConntrackEventEquals() {
        final ConntrackEvent event1 = makeTestConntrackEvent(IPCTNL_MSG_CT_NEW, 1234 /* status */,
                5678 /* timeoutSec*/);
        final ConntrackEvent event2 = makeTestConntrackEvent(IPCTNL_MSG_CT_NEW, 1234 /* status */,
                5678 /* timeoutSec*/);
        assertEquals(event1, event2);
    }

    @Test
    public void testConntrackEventNotEquals() {
        final ConntrackEvent e = makeTestConntrackEvent(IPCTNL_MSG_CT_NEW, 1234 /* status */,
                5678 /* timeoutSec*/);

        final ConntrackEvent typeNotEqual = new ConntrackEvent((short) (e.msgType + 1) /* diff */,
                e.tupleOrig, e.tupleReply, e.status, e.timeoutSec);
        assertNotEquals(e, typeNotEqual);

        final ConntrackEvent tupleOrigNotEqual = new ConntrackEvent(e.msgType,
                null /* diff */, e.tupleReply, e.status, e.timeoutSec);
        assertNotEquals(e, tupleOrigNotEqual);

        final ConntrackEvent tupleReplyNotEqual = new ConntrackEvent(e.msgType,
                e.tupleOrig, null /* diff */, e.status, e.timeoutSec);
        assertNotEquals(e, tupleReplyNotEqual);

        final ConntrackEvent statusNotEqual = new ConntrackEvent(e.msgType,
                e.tupleOrig, e.tupleReply, e.status + 1 /* diff */, e.timeoutSec);
        assertNotEquals(e, statusNotEqual);

        final ConntrackEvent timeoutSecNotEqual = new ConntrackEvent(e.msgType,
                e.tupleOrig, e.tupleReply, e.status, e.timeoutSec + 1 /* diff */);
        assertNotEquals(e, timeoutSecNotEqual);
    }

    @Test
    public void testToString() {
        final ConntrackEvent event = makeTestConntrackEvent(IPCTNL_MSG_CT_NEW,
                0x198 /* status */, 120 /* timeoutSec */);
        final String expected = ""
                + "ConntrackEvent{"
                + "msg_type{IPCTNL_MSG_CT_NEW}, "
                + "tuple_orig{Tuple{IPPROTO_TCP: 192.168.80.12:62449 -> 140.112.8.116:443}}, "
                + "tuple_reply{Tuple{IPPROTO_TCP: 140.112.8.116:443 -> 100.81.179.1:62449}}, "
                + "status{408(IPS_CONFIRMED|IPS_SRC_NAT|IPS_SRC_NAT_DONE|IPS_DST_NAT_DONE)}, "
                + "timeout_sec{120}}";
        assertEquals(expected, event.toString());
    }

    public static final String CT_V4DELETE_TCP_HEX =
            // CHECKSTYLE:OFF IndentationCheck
            // struct nlmsghdr
            "84000000" +      // length = 132
            "0201" +          // type = NFNL_SUBSYS_CTNETLINK (1) << 8 | IPCTNL_MSG_CT_DELETE (2)
            "0000" +          // flags = 0
            "00000000" +      // seqno = 0
            "00000000" +      // pid = 0
            // struct nfgenmsg
            "02" +            // nfgen_family  = AF_INET
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
                    "0600 0300 01BB 0000" +  // nla_type=CTA_PROTO_DST_PORT, port=433 (big endian)
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
                    "0600 0200 01BB 0000" +  // nla_type=CTA_PROTO_SRC_PORT, port=433 (big endian)
                    "0600 0300 F3F1 0000" +  // nla_type=CTA_PROTO_DST_PORT, port=62449 (big endian)
            // struct nlattr
            "0800" +          // nla_len = 8
            "0300" +          // nla_type = CTA_STATUS
            "0000039E";       // nla_value = 0b1110011110 (big endian)
                              // IPS_SEEN_REPLY (1 << 1) | IPS_ASSURED (1 << 2) |
                              // IPS_CONFIRMED (1 << 3) | IPS_SRC_NAT (1 << 4) |
                              // IPS_SRC_NAT_DONE (1 << 7) | IPS_DST_NAT_DONE (1 << 8) |
                              // IPS_DYING (1 << 9)
            // CHECKSTYLE:ON IndentationCheck
    public static final byte[] CT_V4DELETE_TCP_BYTES =
            HexEncoding.decode(CT_V4DELETE_TCP_HEX.replaceAll(" ", "").toCharArray(), false);

    @Test
    public void testConntrackEventDelete() throws Exception {
        final ConntrackEvent expectedEvent =
                makeTestConntrackEvent(IPCTNL_MSG_CT_DELETE, 0x39e /* status */,
                        0 /* timeoutSec (absent) */);
        mConntrackMonitor.sendMessage(CT_V4DELETE_TCP_BYTES);
        verify(mConsumer, timeout(TIMEOUT_MS)).accept(eq(expectedEvent));
    }
}
