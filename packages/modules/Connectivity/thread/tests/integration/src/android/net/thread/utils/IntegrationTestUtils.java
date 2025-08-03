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
package android.net.thread.utils;

import static android.system.OsConstants.IPPROTO_ICMPV6;

import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ND_OPTION_PIO;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ROUTER_ADVERTISEMENT;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.net.TestNetworkInterface;
import android.net.thread.ThreadNetworkController;
import android.os.Handler;
import android.os.SystemClock;
import android.os.SystemProperties;

import com.android.net.module.util.Struct;
import com.android.net.module.util.structs.Icmpv6Header;
import com.android.net.module.util.structs.Ipv6Header;
import com.android.net.module.util.structs.PrefixInformationOption;
import com.android.net.module.util.structs.RaHeader;
import com.android.testutils.HandlerUtils;
import com.android.testutils.TapPacketReader;

import com.google.common.util.concurrent.SettableFuture;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Static utility methods relating to Thread integration tests. */
public final class IntegrationTestUtils {
    // The timeout of join() after restarting ot-daemon. The device needs to send 6 Link Request
    // every 5 seconds, followed by 4 Parent Request every second. So this value needs to be 40
    // seconds to be safe
    public static final Duration RESTART_JOIN_TIMEOUT = Duration.ofSeconds(40);
    public static final Duration JOIN_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration LEAVE_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration CALLBACK_TIMEOUT = Duration.ofSeconds(1);

    private IntegrationTestUtils() {}

    /** Returns whether the device supports simulated Thread radio. */
    public static boolean isSimulatedThreadRadioSupported() {
        // The integration test uses SIMULATION Thread radio so that it only supports CuttleFish.
        return SystemProperties.get("ro.product.model").startsWith("Cuttlefish");
    }

    /**
     * Waits for the given {@link Supplier} to be true until given timeout.
     *
     * @param condition the condition to check
     * @param timeout the time to wait for the condition before throwing
     * @throws TimeoutException if the condition is still not met when the timeout expires
     */
    public static void waitFor(Supplier<Boolean> condition, Duration timeout)
            throws TimeoutException {
        final long intervalMills = 1000;
        final long timeoutMills = timeout.toMillis();

        for (long i = 0; i < timeoutMills; i += intervalMills) {
            if (condition.get()) {
                return;
            }
            SystemClock.sleep(intervalMills);
        }
        if (condition.get()) {
            return;
        }
        throw new TimeoutException("The condition failed to become true in " + timeout);
    }

    /**
     * Creates a {@link TapPacketReader} given the {@link TestNetworkInterface} and {@link Handler}.
     *
     * @param testNetworkInterface the TUN interface of the test network
     * @param handler the handler to process the packets
     * @return the {@link TapPacketReader}
     */
    public static TapPacketReader newPacketReader(
            TestNetworkInterface testNetworkInterface, Handler handler) {
        FileDescriptor fd = testNetworkInterface.getFileDescriptor().getFileDescriptor();
        final TapPacketReader reader =
                new TapPacketReader(handler, fd, testNetworkInterface.getMtu());
        handler.post(() -> reader.start());
        HandlerUtils.waitForIdle(handler, 5000 /* timeout in milliseconds */);
        return reader;
    }

    /**
     * Waits for the Thread module to enter any state of the given {@code deviceRoles}.
     *
     * @param controller the {@link ThreadNetworkController}
     * @param deviceRoles the desired device roles. See also {@link
     *     ThreadNetworkController.DeviceRole}
     * @param timeout the time to wait for the expected state before throwing
     * @return the {@link ThreadNetworkController.DeviceRole} after waiting
     * @throws TimeoutException if the device hasn't become any of expected roles until the timeout
     *     expires
     */
    public static int waitForStateAnyOf(
            ThreadNetworkController controller, List<Integer> deviceRoles, Duration timeout)
            throws TimeoutException {
        SettableFuture<Integer> future = SettableFuture.create();
        ThreadNetworkController.StateCallback callback =
                newRole -> {
                    if (deviceRoles.contains(newRole)) {
                        future.set(newRole);
                    }
                };
        controller.registerStateCallback(directExecutor(), callback);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new TimeoutException(
                    String.format(
                            "The device didn't become an expected role in %s: %s",
                            timeout, e.getMessage()));
        } finally {
            controller.unregisterStateCallback(callback);
        }
    }

    /**
     * Polls for a packet from a given {@link TapPacketReader} that satisfies the {@code filter}.
     *
     * @param packetReader a TUN packet reader
     * @param filter the filter to be applied on the packet
     * @return the first IPv6 packet that satisfies the {@code filter}. If it has waited for more
     *     than 3000ms to read the next packet, the method will return null
     */
    public static byte[] pollForPacket(TapPacketReader packetReader, Predicate<byte[]> filter) {
        byte[] packet;
        while ((packet = packetReader.poll(3000 /* timeoutMs */, filter)) != null) {
            return packet;
        }
        return null;
    }

    /** Returns {@code true} if {@code packet} is an ICMPv6 packet of given {@code type}. */
    public static boolean isExpectedIcmpv6Packet(byte[] packet, int type) {
        if (packet == null) {
            return false;
        }
        ByteBuffer buf = ByteBuffer.wrap(packet);
        try {
            if (Struct.parse(Ipv6Header.class, buf).nextHeader != (byte) IPPROTO_ICMPV6) {
                return false;
            }
            return Struct.parse(Icmpv6Header.class, buf).type == (short) type;
        } catch (IllegalArgumentException ignored) {
            // It's fine that the passed in packet is malformed because it's could be sent
            // by anybody.
        }
        return false;
    }

    public static boolean isFromIpv6Source(byte[] packet, Inet6Address src) {
        if (packet == null) {
            return false;
        }
        ByteBuffer buf = ByteBuffer.wrap(packet);
        try {
            return Struct.parse(Ipv6Header.class, buf).srcIp.equals(src);
        } catch (IllegalArgumentException ignored) {
            // It's fine that the passed in packet is malformed because it's could be sent
            // by anybody.
        }
        return false;
    }

    public static boolean isToIpv6Destination(byte[] packet, Inet6Address dest) {
        if (packet == null) {
            return false;
        }
        ByteBuffer buf = ByteBuffer.wrap(packet);
        try {
            return Struct.parse(Ipv6Header.class, buf).dstIp.equals(dest);
        } catch (IllegalArgumentException ignored) {
            // It's fine that the passed in packet is malformed because it's could be sent
            // by anybody.
        }
        return false;
    }

    /** Returns the Prefix Information Options (PIO) extracted from an ICMPv6 RA message. */
    public static List<PrefixInformationOption> getRaPios(byte[] raMsg) {
        final ArrayList<PrefixInformationOption> pioList = new ArrayList<>();

        if (raMsg == null) {
            return pioList;
        }

        final ByteBuffer buf = ByteBuffer.wrap(raMsg);
        final Ipv6Header ipv6Header = Struct.parse(Ipv6Header.class, buf);
        if (ipv6Header.nextHeader != (byte) IPPROTO_ICMPV6) {
            return pioList;
        }

        final Icmpv6Header icmpv6Header = Struct.parse(Icmpv6Header.class, buf);
        if (icmpv6Header.type != (short) ICMPV6_ROUTER_ADVERTISEMENT) {
            return pioList;
        }

        Struct.parse(RaHeader.class, buf);
        while (buf.position() < raMsg.length) {
            final int currentPos = buf.position();
            final int type = Byte.toUnsignedInt(buf.get());
            final int length = Byte.toUnsignedInt(buf.get());
            if (type == ICMPV6_ND_OPTION_PIO) {
                final ByteBuffer pioBuf =
                        ByteBuffer.wrap(
                                buf.array(),
                                currentPos,
                                Struct.getSize(PrefixInformationOption.class));
                final PrefixInformationOption pio =
                        Struct.parse(PrefixInformationOption.class, pioBuf);
                pioList.add(pio);

                // Move ByteBuffer position to the next option.
                buf.position(currentPos + Struct.getSize(PrefixInformationOption.class));
            } else {
                // The length is in units of 8 octets.
                buf.position(currentPos + (length * 8));
            }
        }
        return pioList;
    }

    /**
     * Sends a UDP message to a destination.
     *
     * @param dstAddress the IP address of the destination
     * @param dstPort the port of the destination
     * @param message the message in UDP payload
     * @throws IOException if failed to send the message
     */
    public static void sendUdpMessage(InetAddress dstAddress, int dstPort, String message)
            throws IOException {
        SocketAddress dstSockAddr = new InetSocketAddress(dstAddress, dstPort);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(dstSockAddr);

            byte[] msgBytes = message.getBytes();
            DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length);

            socket.send(packet);
        }
    }

    public static boolean isInMulticastGroup(String interfaceName, Inet6Address address) {
        final String cmd = "ip -6 maddr show dev " + interfaceName;
        final String output = runShellCommandOrThrow(cmd);
        final String addressStr = address.getHostAddress();
        for (final String line : output.split("\\n")) {
            if (line.contains(addressStr)) {
                return true;
            }
        }
        return false;
    }
}
