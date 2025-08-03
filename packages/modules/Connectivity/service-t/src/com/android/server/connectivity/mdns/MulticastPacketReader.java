/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.connectivity.mdns;

import static com.android.server.connectivity.mdns.util.MdnsUtils.ensureRunningOnHandlerThread;

import android.annotation.NonNull;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.system.Os;

import com.android.net.module.util.FdEventsReader;
import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.io.FileDescriptor;
import java.net.InetSocketAddress;
import java.util.Set;

/** Simple reader for mDNS packets. */
public class MulticastPacketReader extends FdEventsReader<MulticastPacketReader.RecvBuffer> {
    @NonNull
    private final String mLogTag;
    @NonNull
    private final ParcelFileDescriptor mSocket;
    @NonNull
    private final Handler mHandler;
    @NonNull
    private final Set<PacketHandler> mPacketHandlers = MdnsUtils.newSet();

    interface PacketHandler {
        /**
         * Handle an incoming packet.
         *
         * The recvbuf and src <b>will be reused and modified</b> after this method returns, so
         * implementers must ensure that they are not accessed after handlePacket returns.
         */
        void handlePacket(byte[] recvbuf, int length, InetSocketAddress src);
    }

    public static final class RecvBuffer {
        final byte[] data;
        final InetSocketAddress src;

        private RecvBuffer(byte[] data, InetSocketAddress src) {
            this.data = data;
            this.src = src;
        }
    }

    /**
     * Create a new {@link MulticastPacketReader}.
     * @param socket Socket to read from. This will *not* be closed when the reader terminates.
     * @param buffer Buffer to read packets into. Will only be used from the handler thread.
     * @param port the port number for the socket
     */
    protected MulticastPacketReader(@NonNull String interfaceTag,
            @NonNull ParcelFileDescriptor socket, @NonNull Handler handler,
            @NonNull byte[] buffer) {
        // Set the port to zero as placeholder as the recvfrom() call will fill the actual port
        // value later.
        super(handler, new RecvBuffer(buffer, new InetSocketAddress(0 /* port */)));
        mLogTag = MulticastPacketReader.class.getSimpleName() + "/" + interfaceTag;
        mSocket = socket;
        mHandler = handler;
    }

    @Override
    protected int recvBufSize(@NonNull RecvBuffer buffer) {
        return buffer.data.length;
    }

    @Override
    protected FileDescriptor createFd() {
        // Keep a reference to the PFD as it would close the fd in its finalizer otherwise
        return mSocket.getFileDescriptor();
    }

    @Override
    protected void onStop() {
        // Do nothing (do not close the FD)
    }

    @Override
    protected int readPacket(@NonNull FileDescriptor fd, @NonNull RecvBuffer buffer)
            throws Exception {
        return Os.recvfrom(
                fd, buffer.data, 0, buffer.data.length, 0 /* flags */, buffer.src);
    }

    @Override
    protected void handlePacket(@NonNull RecvBuffer recvbuf, int length) {
        for (PacketHandler handler : mPacketHandlers) {
            handler.handlePacket(recvbuf.data, length, recvbuf.src);
        }
    }

    /**
     * Add a packet handler to deal with received packets. If the handler is already set,
     * this is a no-op.
     */
    public void addPacketHandler(@NonNull PacketHandler handler) {
        ensureRunningOnHandlerThread(mHandler);
        mPacketHandlers.add(handler);
    }

    /**
     * Remove a packet handler added via {@link #addPacketHandler}. If the handler was not set,
     * this is a no-op.
     */
    public void removePacketHandler(@NonNull PacketHandler handler) {
        ensureRunningOnHandlerThread(mHandler);
        mPacketHandlers.remove(handler);
    }
}

