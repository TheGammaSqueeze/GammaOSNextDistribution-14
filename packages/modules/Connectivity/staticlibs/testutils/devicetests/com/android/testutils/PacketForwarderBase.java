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

package com.android.testutils;

import static com.android.testutils.PacketReflector.IPPROTO_TCP;
import static com.android.testutils.PacketReflector.IPPROTO_UDP;
import static com.android.testutils.PacketReflector.IPV4_HEADER_LENGTH;
import static com.android.testutils.PacketReflector.IPV6_HEADER_LENGTH;
import static com.android.testutils.PacketReflector.IPV6_PROTO_OFFSET;
import static com.android.testutils.PacketReflector.TCP_HEADER_LENGTH;
import static com.android.testutils.PacketReflector.UDP_HEADER_LENGTH;

import android.annotation.NonNull;
import android.net.TestNetworkInterface;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * A class that forwards packets from a {@link TestNetworkInterface} to another
 * {@link TestNetworkInterface}.
 *
 * For testing purposes, a {@link TestNetworkInterface} provides a {@link FileDescriptor}
 * which allows content injection on the test network. However, this could be hard to use
 * because the callers need to compose IP packets in order to inject content to the
 * test network.
 *
 * In order to remove the need of composing the IP packets, this class forwards IP packets to
 * the {@link FileDescriptor} of another {@link TestNetworkInterface} instance. Thus,
 * the TCP/IP headers could be parsed/composed automatically by the protocol stack of this
 * additional {@link TestNetworkInterface}, while the payload is supplied by the
 * servers run on the interface.
 *
 * To make it work, an internal interface and an external interface are defined, where
 * the client might send packets from the internal interface which are originated from
 * multiple addresses to a server that listens on the different port.
 *
 * For the incoming packet received from external interface, for example a http response sent
 * from the http server, the same mechanism is applied but in a different direction,
 * where the source and destination will be swapped.
 */
public abstract class PacketForwarderBase extends Thread {
    private static final String TAG = "PacketForwarder";
    static final int DESTINATION_PORT_OFFSET = 2;

    // The source fd to read packets from.
    @NonNull
    final FileDescriptor mSrcFd;
    // The buffer to temporarily hold the entire packet after receiving.
    @NonNull
    final byte[] mBuf;
    // The destination fd to write packets to.
    @NonNull
    final FileDescriptor mDstFd;

    @NonNull
    final Map<Integer, Integer> mPortRemapRules;
    /**
     * Construct a {@link PacketForwarderBase}.
     *
     * This class reads packets from {@code srcFd} of a {@link TestNetworkInterface}, and
     * forwards them to the {@code dstFd} of another {@link TestNetworkInterface}.
     *
     * Note that this class is not useful if the instance is not managed by a
     * {@link PacketBridge} to set up a two-way communication.
     *
     * @param srcFd   {@link FileDescriptor} to read packets from.
     * @param mtu     MTU of the test network.
     * @param dstFd   {@link FileDescriptor} to write packets to.
     * @param portRemapRules    port remap rules
     */
    public PacketForwarderBase(@NonNull FileDescriptor srcFd, int mtu,
                           @NonNull FileDescriptor dstFd,
                           @NonNull Map<Integer, Integer> portRemapRules) {
        super(TAG);
        mSrcFd = Objects.requireNonNull(srcFd);
        mBuf = new byte[mtu];
        mDstFd = Objects.requireNonNull(dstFd);
        mPortRemapRules = Objects.requireNonNull(portRemapRules);
    }

    /**
     * A method to prepare forwarding packets between two instances of {@link TestNetworkInterface},
     * which includes ports mapping.
     * Subclasses should override this method to implement the needed port remapping.
     * For internal forwarder will remapped destination port,
     * external forwarder will remapped source port.
     * Example:
     * An outgoing packet from the internal interface with
     * source 1.2.3.4:1234 and destination 8.8.8.8:80
     * might be translated to 8.8.8.8:1234 -> 1.2.3.4:8080 before forwarding.
     * An outgoing packet from the external interface with
     * source 1.2.3.4:8080 and destination 8.8.8.8:1234
     * might be translated to 8.8.8.8:80 -> 1.2.3.4:1234 before forwarding.
     */
    abstract void remapPort(@NonNull byte[] buf, int version);

    /**
     * Retrieves a potentially remapped port number from a packet.
     *
     * @param buf            The packet data as a byte array.
     * @param transportOffset The offset within the packet where the transport layer port begins.
     * @return The remapped port if a mapping exists in the internal forwarding map,
     *         otherwise returns 0 (indicating no remapping).
     */
    int getRemappedPort(@NonNull byte[] buf, int transportOffset) {
        int port = PacketReflectorUtil.getPortAt(buf, transportOffset);
        return mPortRemapRules.getOrDefault(port, 0);
    }

    int getTransportOffset(int version) {
        return version == 4 ? IPV4_HEADER_LENGTH : IPV6_HEADER_LENGTH;
    }

    private void forwardPacket(@NonNull byte[] buf, int len) {
        try {
            Os.write(mDstFd, buf, 0, len);
        } catch (ErrnoException | IOException e) {
            Log.e(TAG, "Error writing packet: " + e.getMessage());
        }
    }

    /**
     * Reads one packet from mSrcFd, and writes the packet to the mDestFd for supported protocols.
     * This includes:
     * 1.Address Swapping: Swaps source and destination IP addresses.
     * 2.Port Remapping: Remap port if necessary.
     * 3.Checksum Recalculation: Updates IP and transport layer checksums to reflect changes.
     */
    private void processPacket() {
        final int len = PacketReflectorUtil.readPacket(mSrcFd, mBuf);
        if (len < 1) {
            // Usually happens when socket read is being interrupted, e.g. stopping PacketForwarder.
            return;
        }

        final int version = mBuf[0] >>> 4;
        final int protoPos, ipHdrLen;
        switch (version) {
            case 4:
                ipHdrLen = IPV4_HEADER_LENGTH;
                protoPos = PacketReflector.IPV4_PROTO_OFFSET;
                break;
            case 6:
                ipHdrLen = IPV6_HEADER_LENGTH;
                protoPos = IPV6_PROTO_OFFSET;
                break;
            default:
                throw new IllegalStateException("Unexpected version: " + version);
        }
        if (len < ipHdrLen) {
            throw new IllegalStateException("Unexpected buffer length: " + len);
        }

        final byte proto = mBuf[protoPos];
        final int transportHdrLen;
        switch (proto) {
            case IPPROTO_TCP:
                transportHdrLen = TCP_HEADER_LENGTH;
                break;
            case IPPROTO_UDP:
                transportHdrLen = UDP_HEADER_LENGTH;
                break;
            // TODO: Support ICMP.
            default:
                return; // Unknown protocol, ignored.
        }

        if (len < ipHdrLen + transportHdrLen) {
            throw new IllegalStateException("Unexpected buffer length: " + len);
        }

        // Swap source and destination address.
        PacketReflectorUtil.swapAddresses(mBuf, version);

        // Remapping the port.
        remapPort(mBuf, version);

        // Fix IP and Transport layer checksum.
        PacketReflectorUtil.fixPacketChecksum(mBuf, len, version, proto);

        // Send the packet to the destination fd.
        forwardPacket(mBuf, len);
    }
    @Override
    public void run() {
        Log.i(TAG, "starting fd=" + mSrcFd + " valid=" + mSrcFd.valid());
        while (!interrupted() && mSrcFd.valid()) {
            processPacket();
        }
        Log.i(TAG, "exiting fd=" + mSrcFd + " valid=" + mSrcFd.valid());
    }
}
