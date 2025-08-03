/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.server;

import static com.android.net.module.util.netlink.NetlinkUtils.SOCKET_RECV_BUFSIZE;
import static com.android.net.module.util.netlink.xfrm.XfrmNetlinkMessage.IPPROTO_ESP;
import static com.android.net.module.util.netlink.xfrm.XfrmNetlinkMessage.NETLINK_XFRM;
import static com.android.net.module.util.netlink.xfrm.XfrmNetlinkMessage.XFRM_MSG_NEWSA;

import android.annotation.TargetApi;
import android.os.Build;
import android.system.ErrnoException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.HexDump;
import com.android.net.module.util.netlink.NetlinkConstants;
import com.android.net.module.util.netlink.NetlinkErrorMessage;
import com.android.net.module.util.netlink.NetlinkMessage;
import com.android.net.module.util.netlink.NetlinkUtils;
import com.android.net.module.util.netlink.xfrm.XfrmNetlinkGetSaMessage;
import com.android.net.module.util.netlink.xfrm.XfrmNetlinkMessage;
import com.android.net.module.util.netlink.xfrm.XfrmNetlinkNewSaMessage;

import libcore.io.IoUtils;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

/**
 * This class handles IPSec XFRM commands between IpSecService and the Linux kernel
 *
 * <p>Synchronization in IpSecXfrmController is done on all entrypoints due to potential race
 * conditions at the kernel/xfrm level.
 */
public class IpSecXfrmController {
    private static final String TAG = IpSecXfrmController.class.getSimpleName();

    private static final boolean VDBG = false; // STOPSHIP: if true

    private static final int TIMEOUT_MS = 500;
    private static final int DEFAULT_RECV_BUFSIZE = 8 * 1024;

    @NonNull private final Dependencies mDependencies;
    @Nullable private FileDescriptor mNetlinkSocket;

    @VisibleForTesting
    public IpSecXfrmController(@NonNull Dependencies dependencies) {
        mDependencies = dependencies;
    }

    public IpSecXfrmController() {
        this(new Dependencies());
    }

    /**
     * Start the XfrmController
     *
     * <p>The method is idempotent
     */
    public synchronized void openNetlinkSocketIfNeeded() throws ErrnoException, SocketException {
        if (mNetlinkSocket == null) {
            mNetlinkSocket = mDependencies.newNetlinkSocket();
        }
    }

    /**
     * Stop the XfrmController
     *
     * <p>The method is idempotent
     */
    public synchronized void closeNetlinkSocketIfNeeded() {
        if (mNetlinkSocket != null) {
            mDependencies.releaseNetlinkSocket(mNetlinkSocket);
            mNetlinkSocket = null;
        }
    }

    @VisibleForTesting
    public synchronized FileDescriptor getNetlinkSocket() {
        return mNetlinkSocket;
    }

    /** Dependencies of IpSecXfrmController, for injection in tests. */
    @VisibleForTesting
    public static class Dependencies {
        /** Get a new XFRM netlink socket and connect it */
        public FileDescriptor newNetlinkSocket() throws ErrnoException, SocketException {
            final FileDescriptor fd =
                    NetlinkUtils.netlinkSocketForProto(NETLINK_XFRM, SOCKET_RECV_BUFSIZE);
            NetlinkUtils.connectToKernel(fd);
            return fd;
        }

        /** Close the netlink socket */
        // TODO: b/205923322 This annotation is to suppress the lint error complaining that
        // #closeQuietly requires Android S. It can be removed when the infra supports setting
        // service-connectivity min_sdk to 31
        @TargetApi(Build.VERSION_CODES.S)
        public void releaseNetlinkSocket(FileDescriptor fd) {
            IoUtils.closeQuietly(fd);
        }

        /** Send a netlink message to a socket */
        public void sendMessage(FileDescriptor fd, byte[] bytes)
                throws ErrnoException, InterruptedIOException {
            NetlinkUtils.sendMessage(fd, bytes, 0, bytes.length, TIMEOUT_MS);
        }

        /** Receive a netlink message from a socket */
        public ByteBuffer recvMessage(FileDescriptor fd)
                throws ErrnoException, InterruptedIOException {
            return NetlinkUtils.recvMessage(fd, DEFAULT_RECV_BUFSIZE, TIMEOUT_MS);
        }
    }

    @GuardedBy("IpSecXfrmController.this")
    private NetlinkMessage sendRequestAndGetResponse(String methodTag, byte[] req)
            throws ErrnoException, InterruptedIOException, IOException {
        openNetlinkSocketIfNeeded();

        logD(methodTag + ":  send request " + req.length + " bytes");
        logV(HexDump.dumpHexString(req));
        mDependencies.sendMessage(mNetlinkSocket, req);

        final ByteBuffer response = mDependencies.recvMessage(mNetlinkSocket);
        logD(methodTag + ": receive response " + response.limit() + " bytes");
        logV(HexDump.dumpHexString(response.array(), 0 /* offset */, response.limit()));

        final NetlinkMessage msg = XfrmNetlinkMessage.parse(response, NETLINK_XFRM);
        if (msg == null) {
            throw new IOException("Fail to parse the response message");
        }

        final int msgType = msg.getHeader().nlmsg_type;
        if (msgType == NetlinkConstants.NLMSG_ERROR) {
            final NetlinkErrorMessage errorMsg = (NetlinkErrorMessage) msg;
            final int errorCode = errorMsg.getNlMsgError().error;
            throw new ErrnoException(methodTag, errorCode);
        }

        return msg;
    }

    /** Get the state of an IPsec SA */
    @NonNull
    public synchronized XfrmNetlinkNewSaMessage ipSecGetSa(
            @NonNull final InetAddress destAddress, long spi)
            throws ErrnoException, InterruptedIOException, IOException {
        logD("ipSecGetSa: destAddress=" + destAddress + " spi=" + spi);

        final byte[] req =
                XfrmNetlinkGetSaMessage.newXfrmNetlinkGetSaMessage(
                        destAddress, spi, (short) IPPROTO_ESP);
        try {
            final NetlinkMessage msg = sendRequestAndGetResponse("ipSecGetSa", req);

            final int messageType = msg.getHeader().nlmsg_type;
            if (messageType != XFRM_MSG_NEWSA) {
                throw new IOException("unexpected response type " + messageType);
            }

            return (XfrmNetlinkNewSaMessage) msg;
        } catch (IllegalArgumentException exception) {
            // Maybe thrown from Struct.parse
            throw new IOException("Failed to parse the response " + exception);
        }
    }

    private static void logV(String details) {
        if (VDBG) {
            Log.v(TAG, details);
        }
    }

    private static void logD(String details) {
        Log.d(TAG, details);
    }
}
