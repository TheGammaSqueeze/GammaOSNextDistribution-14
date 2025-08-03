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

package com.android.networkstack.tethering;

import static android.system.OsConstants.EAGAIN;
import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.NETLINK_NETFILTER;

import static com.android.net.module.util.netlink.NetlinkUtils.DEFAULT_RECV_BUFSIZE;
import static com.android.networkstack.tethering.OffloadHardwareInterface.IPCTNL_MSG_CT_NEW;
import static com.android.networkstack.tethering.OffloadHardwareInterface.NFNL_SUBSYS_CTNETLINK;
import static com.android.networkstack.tethering.OffloadHardwareInterface.NF_NETLINK_CONNTRACK_DESTROY;
import static com.android.networkstack.tethering.OffloadHardwareInterface.NF_NETLINK_CONNTRACK_NEW;

import static org.junit.Assert.fail;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.NativeHandle;
import android.system.ErrnoException;
import android.util.Log;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.SharedLog;
import com.android.net.module.util.netlink.ConntrackMessage;
import com.android.net.module.util.netlink.NetlinkMessage;
import com.android.net.module.util.netlink.NetlinkUtils;
import com.android.net.module.util.netlink.StructNlMsgHdr;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class ConntrackSocketTest {
    private static final long TIMEOUT = 500;
    private static final String TAG = ConntrackSocketTest.class.getSimpleName();

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private final SharedLog mLog = new SharedLog("privileged-test");

    private OffloadHardwareInterface mOffloadHw;
    private OffloadHardwareInterface.Dependencies mDeps;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mHandlerThread = new HandlerThread(getClass().getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        // Looper must be prepared here since AndroidJUnitRunner runs tests on separate threads.
        if (Looper.myLooper() == null) Looper.prepare();

        mDeps = new OffloadHardwareInterface.Dependencies(mHandler, mLog);
        mOffloadHw = new OffloadHardwareInterface(mHandler, mLog, mDeps);
    }

    @After
    public void tearDown() throws Exception {
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread.join();
        }
    }

    void findConnectionOrThrow(FileDescriptor fd, InetSocketAddress local, InetSocketAddress remote)
            throws Exception {
        Log.d(TAG, "Looking for socket " + local + " -> " + remote);

        // Loop until the socket is found (and return) or recvMessage throws an exception.
        while (true) {
            final ByteBuffer buffer = NetlinkUtils.recvMessage(fd, DEFAULT_RECV_BUFSIZE, TIMEOUT);

            // Parse all the netlink messages in the dump.
            // NetlinkMessage#parse returns null if the message is truncated or invalid.
            while (buffer.remaining() > 0) {
                NetlinkMessage nlmsg = NetlinkMessage.parse(buffer, NETLINK_NETFILTER);
                Log.d(TAG, "Got netlink message: " + nlmsg);
                if (!(nlmsg instanceof ConntrackMessage)) {
                    continue;
                }

                StructNlMsgHdr nlmsghdr = nlmsg.getHeader();
                ConntrackMessage ctmsg = (ConntrackMessage) nlmsg;
                ConntrackMessage.Tuple tuple = ctmsg.tupleOrig;

                if (nlmsghdr.nlmsg_type == (NFNL_SUBSYS_CTNETLINK << 8 | IPCTNL_MSG_CT_NEW)
                        && tuple != null
                        && tuple.protoNum == IPPROTO_TCP
                        && tuple.srcIp.equals(local.getAddress())
                        && tuple.dstIp.equals(remote.getAddress())
                        && tuple.srcPort == (short) local.getPort()
                        && tuple.dstPort == (short) remote.getPort()) {
                    return;
                }
            }
        }
    }

    @Test
    public void testIpv4ConntrackSocket() throws Exception {
        // Set up server and connect.
        final InetAddress localhost = InetAddress.getByName("127.0.0.1");
        final InetSocketAddress anyAddress = new InetSocketAddress(localhost, 0);
        final ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(anyAddress);
        final InetSocketAddress theAddress =
                (InetSocketAddress) serverSocket.getLocalSocketAddress();

        // Make a connection to the server.
        final Socket socket = new Socket();
        socket.connect(theAddress);
        final InetSocketAddress localAddress = (InetSocketAddress) socket.getLocalSocketAddress();
        final Socket acceptedSocket = serverSocket.accept();

        final NativeHandle handle = mDeps.createConntrackSocket(
                NF_NETLINK_CONNTRACK_NEW | NF_NETLINK_CONNTRACK_DESTROY);
        mOffloadHw.requestSocketDump(handle);

        try {
            findConnectionOrThrow(handle.getFileDescriptor(), localAddress, theAddress);
            // No exceptions? Socket was found, test passes.
        } catch (ErrnoException e) {
            if (e.errno == EAGAIN) {
                fail("Did not find socket " + localAddress + "->" + theAddress + " in dump");
            } else {
                throw e;
            }
        } finally {
            socket.close();
            serverSocket.close();
            acceptedSocket.close();
        }
    }
}
