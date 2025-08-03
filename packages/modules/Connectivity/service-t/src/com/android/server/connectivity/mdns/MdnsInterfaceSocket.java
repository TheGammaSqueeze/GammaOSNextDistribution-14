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

import static com.android.server.connectivity.mdns.MdnsSocket.MULTICAST_IPV4_ADDRESS;
import static com.android.server.connectivity.mdns.MdnsSocket.MULTICAST_IPV6_ADDRESS;

import android.annotation.NonNull;
import android.annotation.RequiresApi;
import android.net.LinkAddress;
import android.net.util.SocketUtils;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import com.android.net.module.util.SharedLog;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.List;

/**
 * {@link MdnsInterfaceSocket} provides a similar interface to {@link MulticastSocket} and binds to
 * an available multicast network interfaces.
 *
 * <p>This isn't thread safe and should be always called on the same thread unless specified
 * otherwise.
 *
 * @see MulticastSocket for javadoc of each public method.
 * @see MulticastSocket for javadoc of each public method.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class MdnsInterfaceSocket {
    private static final String TAG = MdnsInterfaceSocket.class.getSimpleName();
    @NonNull private final MulticastSocket mMulticastSocket;
    @NonNull private final NetworkInterface mNetworkInterface;
    @NonNull private final MulticastPacketReader mPacketReader;
    @NonNull private final ParcelFileDescriptor mFileDescriptor;
    @NonNull private final SharedLog mSharedLog;
    private boolean mJoinedIpv4 = false;
    private boolean mJoinedIpv6 = false;

    public MdnsInterfaceSocket(@NonNull NetworkInterface networkInterface, int port,
            @NonNull Looper looper, @NonNull byte[] packetReadBuffer, @NonNull SharedLog sharedLog)
            throws IOException {
        mNetworkInterface = networkInterface;
        mMulticastSocket = new MulticastSocket(port);
        // RFC Spec: https://tools.ietf.org/html/rfc6762. Time to live is set 255
        mMulticastSocket.setTimeToLive(255);
        mMulticastSocket.setNetworkInterface(networkInterface);

        // Bind socket to the interface for receiving from that interface only.
        mFileDescriptor = ParcelFileDescriptor.fromDatagramSocket(mMulticastSocket);
        try {
            final FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            final int flags = Os.fcntlInt(fd, OsConstants.F_GETFL, 0);
            Os.fcntlInt(fd, OsConstants.F_SETFL, flags | OsConstants.SOCK_NONBLOCK);
            SocketUtils.bindSocketToInterface(fd, mNetworkInterface.getName());
        } catch (ErrnoException e) {
            throw new IOException("Error setting socket options", e);
        }

        mPacketReader = new MulticastPacketReader(networkInterface.getName(), mFileDescriptor,
                new Handler(looper), packetReadBuffer);
        mPacketReader.start();

        mSharedLog = sharedLog;
    }

    /**
     * Sends a datagram packet from this socket.
     *
     * <p>This method could be used on any thread.
     */
    public void send(@NonNull DatagramPacket packet) throws IOException {
        mMulticastSocket.send(packet);
    }

    private static boolean hasIpv4Address(@NonNull List<LinkAddress> addresses) {
        for (LinkAddress address : addresses) {
            if (address.isIpv4()) return true;
        }
        return false;
    }

    private static boolean hasIpv6Address(@NonNull List<LinkAddress> addresses) {
        for (LinkAddress address : addresses) {
            if (address.isIpv6()) return true;
        }
        return false;
    }

    /*** Joins both IPv4 and IPv6 multicast groups. */
    public void joinGroup(@NonNull List<LinkAddress> addresses) {
        maybeJoinIpv4(addresses);
        maybeJoinIpv6(addresses);
    }

    private boolean joinGroup(@NonNull InetSocketAddress multicastAddress) {
        try {
            mMulticastSocket.joinGroup(multicastAddress, mNetworkInterface);
            return true;
        } catch (IOException e) {
            // The address may have just been removed
            mSharedLog.e("Error joining multicast group for " + mNetworkInterface, e);
            return false;
        }
    }

    private void maybeJoinIpv4(@NonNull List<LinkAddress> addresses) {
        final boolean hasAddr = hasIpv4Address(addresses);
        if (!mJoinedIpv4 && hasAddr) {
            mJoinedIpv4 = joinGroup(MULTICAST_IPV4_ADDRESS);
        } else if (!hasAddr) {
            // Lost IPv4 address
            mJoinedIpv4 = false;
        }
    }

    private void maybeJoinIpv6(@NonNull List<LinkAddress> addresses) {
        final boolean hasAddr = hasIpv6Address(addresses);
        if (!mJoinedIpv6 && hasAddr) {
            mJoinedIpv6 = joinGroup(MULTICAST_IPV6_ADDRESS);
        } else if (!hasAddr) {
            // Lost IPv6 address
            mJoinedIpv6 = false;
        }
    }

    /*** Destroy the socket */
    public void destroy() {
        mPacketReader.stop();
        try {
            mFileDescriptor.close();
        } catch (IOException e) {
            mSharedLog.e("Close file descriptor failed.");
        }
        mMulticastSocket.close();
    }

    /**
     * Add a handler to receive callbacks when reads the packet from socket. If the handler is
     * already set, this is a no-op.
     */
    public void addPacketHandler(@NonNull MulticastPacketReader.PacketHandler handler) {
        mPacketReader.addPacketHandler(handler);
    }

    /**
     * Remove a handler added via {@link #addPacketHandler}. If the handler is not present, this is
     * a no-op.
     */
    public void removePacketHandler(@NonNull MulticastPacketReader.PacketHandler handler) {
        mPacketReader.removePacketHandler(handler);
    }

    /**
     * Returns the network interface that this socket is bound to.
     *
     * <p>This method could be used on any thread.
     */
    public NetworkInterface getInterface() {
        return mNetworkInterface;
    }

    /*** Returns whether this socket has joined IPv4 group */
    public boolean hasJoinedIpv4() {
        return mJoinedIpv4;
    }

    /*** Returns whether this socket has joined IPv6 group */
    public boolean hasJoinedIpv6() {
        return mJoinedIpv6;
    }
}
