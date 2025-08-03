/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.net.cts;

import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.net.IpSecAlgorithm.AUTH_AES_CMAC;
import static android.net.IpSecAlgorithm.AUTH_AES_XCBC;
import static android.net.IpSecAlgorithm.AUTH_CRYPT_AES_GCM;
import static android.net.IpSecAlgorithm.AUTH_CRYPT_CHACHA20_POLY1305;
import static android.net.IpSecAlgorithm.AUTH_HMAC_MD5;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA1;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA256;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA384;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA512;
import static android.net.IpSecAlgorithm.CRYPT_AES_CBC;
import static android.net.IpSecAlgorithm.CRYPT_AES_CTR;
import static android.net.cts.PacketUtils.AES_CBC_BLK_SIZE;
import static android.net.cts.PacketUtils.AES_CBC_IV_LEN;
import static android.net.cts.PacketUtils.AES_CMAC_ICV_LEN;
import static android.net.cts.PacketUtils.AES_CMAC_KEY_LEN;
import static android.net.cts.PacketUtils.AES_CTR_BLK_SIZE;
import static android.net.cts.PacketUtils.AES_CTR_IV_LEN;
import static android.net.cts.PacketUtils.AES_CTR_KEY_LEN_20;
import static android.net.cts.PacketUtils.AES_GCM_BLK_SIZE;
import static android.net.cts.PacketUtils.AES_GCM_IV_LEN;
import static android.net.cts.PacketUtils.AES_XCBC_ICV_LEN;
import static android.net.cts.PacketUtils.AES_XCBC_KEY_LEN;
import static android.net.cts.PacketUtils.CHACHA20_POLY1305_BLK_SIZE;
import static android.net.cts.PacketUtils.CHACHA20_POLY1305_ICV_LEN;
import static android.net.cts.PacketUtils.CHACHA20_POLY1305_IV_LEN;
import static android.net.cts.PacketUtils.HMAC_SHA512_ICV_LEN;
import static android.net.cts.PacketUtils.HMAC_SHA512_KEY_LEN;
import static android.net.cts.PacketUtils.IP4_HDRLEN;
import static android.net.cts.PacketUtils.IP6_HDRLEN;
import static android.net.cts.PacketUtils.TCP_HDRLEN_WITH_TIMESTAMP_OPT;
import static android.net.cts.PacketUtils.UDP_HDRLEN;
import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.IPPROTO_UDP;

import static com.android.compatibility.common.util.PropertyUtil.getFirstApiLevel;
import static com.android.compatibility.common.util.PropertyUtil.getVendorApiLevel;
import static com.android.testutils.DeviceInfoUtils.isKernelVersionAtLeast;
import static com.android.testutils.MiscAsserts.assertThrows;
import static com.android.testutils.TestPermissionUtil.runAsShell;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.net.IpSecAlgorithm;
import android.net.IpSecManager;
import android.net.IpSecManager.SecurityParameterIndex;
import android.net.IpSecManager.UdpEncapsulationSocket;
import android.net.IpSecTransform;
import android.net.TrafficStats;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.build.SdkLevel;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.SkipMainlinePresubmit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Socket cannot bind in instant app mode")
public class IpSecManagerTest extends IpSecBaseTest {
    @Rule public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    private static final String TAG = IpSecManagerTest.class.getSimpleName();

    private static final InetAddress GOOGLE_DNS_4 = InetAddress.parseNumericAddress("8.8.8.8");
    private static final InetAddress GOOGLE_DNS_6 =
            InetAddress.parseNumericAddress("2001:4860:4860::8888");

    private static final InetAddress[] GOOGLE_DNS_LIST =
            new InetAddress[] {GOOGLE_DNS_4, GOOGLE_DNS_6};

    private static final int DROID_SPI = 0xD1201D;
    private static final int MAX_PORT_BIND_ATTEMPTS = 10;

    private static final byte[] AEAD_KEY = getKey(288);

    /*
     * Allocate a random SPI
     * Allocate a specific SPI using previous randomly created SPI value
     * Realloc the same SPI that was specifically created (expect SpiUnavailable)
     * Close SPIs
     */
    @Test
    public void testAllocSpi() throws Exception {
        for (InetAddress addr : GOOGLE_DNS_LIST) {
            SecurityParameterIndex randomSpi, droidSpi;
            randomSpi = mISM.allocateSecurityParameterIndex(addr);
            assertTrue(
                    "Failed to receive a valid SPI",
                    randomSpi.getSpi() != IpSecManager.INVALID_SECURITY_PARAMETER_INDEX);

            droidSpi = mISM.allocateSecurityParameterIndex(addr, DROID_SPI);
            assertTrue("Failed to allocate specified SPI, " + DROID_SPI,
                    droidSpi.getSpi() == DROID_SPI);

            IpSecManager.SpiUnavailableException expectedException =
                    assertThrows("Duplicate SPI was allowed to be created",
                            IpSecManager.SpiUnavailableException.class,
                            () -> mISM.allocateSecurityParameterIndex(addr, DROID_SPI));
            assertEquals(expectedException.getSpi(), droidSpi.getSpi());

            randomSpi.close();
            droidSpi.close();
        }
    }

    /** This function finds an available port */
    private static int findUnusedPort() throws Exception {
        // Get an available port.
        DatagramSocket s = new DatagramSocket();
        int port = s.getLocalPort();
        s.close();
        return port;
    }

    private static FileDescriptor getBoundUdpSocket(InetAddress address) throws Exception {
        FileDescriptor sock =
                Os.socket(getDomain(address), OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_UDP);

        for (int i = 0; i < MAX_PORT_BIND_ATTEMPTS; i++) {
            try {
                int port = findUnusedPort();
                Os.bind(sock, address, port);
                break;
            } catch (ErrnoException e) {
                // Someone claimed the port since we called findUnusedPort.
                if (e.errno == OsConstants.EADDRINUSE) {
                    if (i == MAX_PORT_BIND_ATTEMPTS - 1) {

                        fail("Failed " + MAX_PORT_BIND_ATTEMPTS + " attempts to bind to a port");
                    }
                    continue;
                }
                throw e.rethrowAsIOException();
            }
        }
        return sock;
    }

    private void checkUnconnectedUdp(IpSecTransform transform, InetAddress local, int sendCount,
                                     boolean useJavaSockets) throws Exception {
        GenericUdpSocket sockLeft = null, sockRight = null;
        if (useJavaSockets) {
            SocketPair<JavaUdpSocket> sockets = getJavaUdpSocketPair(local, mISM, transform, false);
            sockLeft = sockets.mLeftSock;
            sockRight = sockets.mRightSock;
        } else {
            SocketPair<NativeUdpSocket> sockets =
                    getNativeUdpSocketPair(local, mISM, transform, false);
            sockLeft = sockets.mLeftSock;
            sockRight = sockets.mRightSock;
        }

        for (int i = 0; i < sendCount; i++) {
            byte[] in;

            sockLeft.sendTo(TEST_DATA, local, sockRight.getPort());
            in = sockRight.receive();
            assertArrayEquals("Left-to-right encrypted data did not match.", TEST_DATA, in);

            sockRight.sendTo(TEST_DATA, local, sockLeft.getPort());
            in = sockLeft.receive();
            assertArrayEquals("Right-to-left encrypted data did not match.", TEST_DATA, in);
        }

        sockLeft.close();
        sockRight.close();
    }

    private void checkTcp(IpSecTransform transform, InetAddress local, int sendCount,
                          boolean useJavaSockets) throws Exception {
        GenericTcpSocket client = null, accepted = null;
        if (useJavaSockets) {
            SocketPair<JavaTcpSocket> sockets = getJavaTcpSocketPair(local, mISM, transform);
            client = sockets.mLeftSock;
            accepted = sockets.mRightSock;
        } else {
            SocketPair<NativeTcpSocket> sockets = getNativeTcpSocketPair(local, mISM, transform);
            client = sockets.mLeftSock;
            accepted = sockets.mRightSock;
        }

        // Wait for TCP handshake packets to be counted
        StatsChecker.waitForNumPackets(3); // (SYN, SYN+ACK, ACK)

        // Reset StatsChecker, to ignore negotiation overhead.
        StatsChecker.initStatsChecker();
        for (int i = 0; i < sendCount; i++) {
            byte[] in;

            client.send(TEST_DATA);
            in = accepted.receive();
            assertArrayEquals("Client-to-server encrypted data did not match.", TEST_DATA, in);

            // Allow for newest data + ack packets to be returned before sending next packet
            // Also add the number of expected packets in each of the previous runs (4 per run)
            StatsChecker.waitForNumPackets(2 + (4 * i));

            accepted.send(TEST_DATA);
            in = client.receive();
            assertArrayEquals("Server-to-client encrypted data did not match.", TEST_DATA, in);

            // Allow for all data + ack packets to be returned before sending next packet
            // Also add the number of expected packets in each of the previous runs (4 per run)
            StatsChecker.waitForNumPackets(4 * (i + 1));
        }

        // Transforms should not be removed from the sockets, otherwise FIN packets will be sent
        //     unencrypted.
        // This test also unfortunately happens to rely on a nuance of the cleanup order. By
        //     keeping the policy on the socket, but removing the SA before lingering FIN packets
        //     are sent (at an undetermined later time), the FIN packets are dropped. Without this,
        //     we run into all kinds of headaches trying to test data accounting (unsolicited
        //     packets mysteriously appearing and messing up our counters)
        // The right way to close sockets is to set SO_LINGER to ensure synchronous closure,
        //     closing the sockets, and then closing the transforms. See documentation for the
        //     Socket or FileDescriptor flavors of applyTransportModeTransform() in IpSecManager
        //     for more details.

        client.close();
        accepted.close();
    }

    private IpSecTransform buildTransportModeTransform(
            SecurityParameterIndex spi, InetAddress localAddr,
            UdpEncapsulationSocket encapSocket)
            throws Exception {
        final IpSecTransform.Builder builder =
                new IpSecTransform.Builder(InstrumentationRegistry.getContext())
                        .setEncryption(new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY))
                        .setAuthentication(
                                new IpSecAlgorithm(
                                        IpSecAlgorithm.AUTH_HMAC_SHA256,
                                        AUTH_KEY,
                                        AUTH_KEY.length * 8));
        if (encapSocket != null) {
            builder.setIpv4Encapsulation(encapSocket, encapSocket.getPort());
        }
        return builder.buildTransportModeTransform(localAddr, spi);
    }

    /*
     * Alloc outbound SPI
     * Alloc inbound SPI
     * Create transport mode transform
     * open socket
     * apply transform to socket
     * send data on socket
     * release transform
     * send data (expect exception)
     */
    private void doTestCreateTransform(String loopbackAddrString, boolean encap) throws Exception {
        InetAddress localAddr = InetAddress.getByName(loopbackAddrString);

        final boolean [][] applyInApplyOut = {
                {false, false}, {false, true}, {true, false}, {true,true}};
        final byte[] data = new String("Best test data ever!").getBytes("UTF-8");
        final DatagramPacket outPacket = new DatagramPacket(data, 0, data.length, localAddr, 0);

        byte[] in = new byte[data.length];
        DatagramPacket inPacket = new DatagramPacket(in, in.length);
        int localPort;

        for(boolean[] io : applyInApplyOut) {
            boolean applyIn = io[0];
            boolean applyOut = io[1];
            try (
                SecurityParameterIndex spi = mISM.allocateSecurityParameterIndex(localAddr);
                UdpEncapsulationSocket encapSocket = encap
                        ? getPrivilegedUdpEncapSocket(/*ipv6=*/ localAddr instanceof Inet6Address)
                        : null;
                IpSecTransform transform = buildTransportModeTransform(spi, localAddr,
                        encapSocket);
                // Bind localSocket to a random available port.
                DatagramSocket localSocket = new DatagramSocket(0);
            ) {
                localPort = localSocket.getLocalPort();
                localSocket.setSoTimeout(200);
                outPacket.setPort(localPort);
                if (applyIn) {
                    mISM.applyTransportModeTransform(
                            localSocket, IpSecManager.DIRECTION_IN, transform);
                }
                if (applyOut) {
                    mISM.applyTransportModeTransform(
                            localSocket, IpSecManager.DIRECTION_OUT, transform);
                }
                if (applyIn == applyOut) {
                    localSocket.send(outPacket);
                    localSocket.receive(inPacket);
                    assertTrue("Encrypted data did not match.",
                            Arrays.equals(outPacket.getData(), inPacket.getData()));
                    mISM.removeTransportModeTransforms(localSocket);
                } else {
                    try {
                        localSocket.send(outPacket);
                        localSocket.receive(inPacket);
                    } catch (IOException e) {
                        continue;
                    } finally {
                        mISM.removeTransportModeTransforms(localSocket);
                    }
                    // FIXME: This check is disabled because sockets currently receive data
                    // if there is a valid SA for decryption, even when the input policy is
                    // not applied to a socket.
                    //  fail("Data IO should fail on asymmetrical transforms! + Input="
                    //          + applyIn + " Output=" + applyOut);
                }
            }
        }
    }

    private UdpEncapsulationSocket getPrivilegedUdpEncapSocket(boolean ipv6) throws Exception {
        return runAsShell(NETWORK_SETTINGS, () -> {
            if (ipv6) {
                return mISM.openUdpEncapsulationSocket(65536);
            } else {
                // Can't pass 0 to IpSecManager#openUdpEncapsulationSocket(int).
                return mISM.openUdpEncapsulationSocket();
            }
        });
    }

    private static boolean isIpv6UdpEncapSupportedByKernel() {
        return isKernelVersionAtLeast("5.15.31")
                || (isKernelVersionAtLeast("5.10.108") && !isKernelVersionAtLeast("5.15.0"));
    }

    // Packet private for use in IpSecManagerTunnelTest
    static boolean isIpv6UdpEncapSupported() {
        return SdkLevel.isAtLeastU() && isIpv6UdpEncapSupportedByKernel();
    }

    // Packet private for use in IpSecManagerTunnelTest
    static void assumeExperimentalIpv6UdpEncapSupported() throws Exception {
        assumeTrue("Not supported before U", SdkLevel.isAtLeastU());
        assumeTrue("Not supported by kernel", isIpv6UdpEncapSupportedByKernel());
    }

    @Test
    public void testCreateTransformIpv4() throws Exception {
        doTestCreateTransform(IPV4_LOOPBACK, false);
    }

    @Test
    public void testCreateTransformIpv6() throws Exception {
        doTestCreateTransform(IPV6_LOOPBACK, false);
    }

    @Test
    public void testCreateTransformIpv4Encap() throws Exception {
        doTestCreateTransform(IPV4_LOOPBACK, true);
    }

    @Test
    public void testCreateTransformIpv6Encap() throws Exception {
        assumeExperimentalIpv6UdpEncapSupported();
        doTestCreateTransform(IPV6_LOOPBACK, true);
    }

    /** Snapshot of TrafficStats as of initStatsChecker call for later comparisons */
    private static class StatsChecker {
        private static final double ERROR_MARGIN_BYTES = 1.05;
        private static final double ERROR_MARGIN_PKTS = 1.05;
        private static final int MAX_WAIT_TIME_MILLIS = 3000;

        private static long uidTxBytes;
        private static long uidRxBytes;
        private static long uidTxPackets;
        private static long uidRxPackets;

        private static long ifaceTxBytes;
        private static long ifaceRxBytes;
        private static long ifaceTxPackets;
        private static long ifaceRxPackets;

        /**
         * This method counts the number of incoming packets, polling intermittently up to
         * MAX_WAIT_TIME_MILLIS.
         */
        private static void waitForNumPackets(int numPackets) throws Exception {
            long uidTxDelta = 0;
            long uidRxDelta = 0;
            for (int i = 0; i < 100; i++) {
                uidTxDelta = TrafficStats.getUidTxPackets(Os.getuid()) - uidTxPackets;
                uidRxDelta = TrafficStats.getUidRxPackets(Os.getuid()) - uidRxPackets;

                // TODO: Check Rx packets as well once kernel security policy bug is fixed.
                // (b/70635417)
                if (uidTxDelta >= numPackets) {
                    return;
                }
                Thread.sleep(MAX_WAIT_TIME_MILLIS / 100);
            }
            fail(
                    "Not enough traffic was recorded to satisfy the provided conditions: wanted "
                            + numPackets
                            + ", got "
                            + uidTxDelta
                            + " tx and "
                            + uidRxDelta
                            + " rx packets");
        }

        private static void assertUidStatsDelta(
                int expectedTxByteDelta,
                int expectedTxPacketDelta,
                int minRxByteDelta,
                int maxRxByteDelta,
                int expectedRxPacketDelta) {
            long newUidTxBytes = TrafficStats.getUidTxBytes(Os.getuid());
            long newUidRxBytes = TrafficStats.getUidRxBytes(Os.getuid());
            long newUidTxPackets = TrafficStats.getUidTxPackets(Os.getuid());
            long newUidRxPackets = TrafficStats.getUidRxPackets(Os.getuid());

            assertEquals(expectedTxByteDelta, newUidTxBytes - uidTxBytes);
            assertTrue("Not enough bytes", newUidRxBytes - uidRxBytes >= minRxByteDelta);
            assertTrue("Too many bytes", newUidRxBytes - uidRxBytes <= maxRxByteDelta);
            assertEquals(expectedTxPacketDelta, newUidTxPackets - uidTxPackets);
            assertEquals(expectedRxPacketDelta, newUidRxPackets - uidRxPackets);
        }

        private static void assertIfaceStatsDelta(
                int expectedTxByteDelta,
                int expectedTxPacketDelta,
                int expectedRxByteDelta,
                int expectedRxPacketDelta)
                throws IOException {
            long newIfaceTxBytes = TrafficStats.getLoopbackTxBytes();
            long newIfaceRxBytes = TrafficStats.getLoopbackRxBytes();
            long newIfaceTxPackets = TrafficStats.getLoopbackTxPackets();
            long newIfaceRxPackets = TrafficStats.getLoopbackRxPackets();

            // Check that iface stats are within an acceptable range; data might be sent
            // on the local interface by other apps.
            assertApproxEquals("TX bytes", ifaceTxBytes, newIfaceTxBytes, expectedTxByteDelta,
                    ERROR_MARGIN_BYTES);
            assertApproxEquals("RX bytes", ifaceRxBytes, newIfaceRxBytes, expectedRxByteDelta,
                    ERROR_MARGIN_BYTES);
            assertApproxEquals("TX packets", ifaceTxPackets, newIfaceTxPackets,
                    expectedTxPacketDelta, ERROR_MARGIN_PKTS);
            assertApproxEquals("RX packets",  ifaceRxPackets, newIfaceRxPackets,
                    expectedRxPacketDelta, ERROR_MARGIN_PKTS);
        }

        private static void assertApproxEquals(
                String what, long oldStats, long newStats, int expectedDelta, double errorMargin) {
            assertTrue(
                    "Expected at least " + expectedDelta + " " + what
                            + ", got "  + (newStats - oldStats),
                    newStats - oldStats >= expectedDelta);
            assertTrue(
                    "Expected at most " + errorMargin + " * " + expectedDelta + " " + what
                            + ", got " + (newStats - oldStats),
                    newStats - oldStats < (expectedDelta * errorMargin));
        }

        private static void initStatsChecker() throws Exception {
            uidTxBytes = TrafficStats.getUidTxBytes(Os.getuid());
            uidRxBytes = TrafficStats.getUidRxBytes(Os.getuid());
            uidTxPackets = TrafficStats.getUidTxPackets(Os.getuid());
            uidRxPackets = TrafficStats.getUidRxPackets(Os.getuid());

            ifaceTxBytes = TrafficStats.getLoopbackTxBytes();
            ifaceRxBytes = TrafficStats.getLoopbackRxBytes();
            ifaceTxPackets = TrafficStats.getLoopbackTxPackets();
            ifaceRxPackets = TrafficStats.getLoopbackRxPackets();
        }
    }

    private int getTruncLenBits(IpSecAlgorithm authOrAead) {
        return authOrAead == null ? 0 : authOrAead.getTruncationLengthBits();
    }

    private int getIvLen(IpSecAlgorithm cryptOrAead) {
        if (cryptOrAead == null) { return 0; }

        switch (cryptOrAead.getName()) {
            case IpSecAlgorithm.CRYPT_AES_CBC:
                return AES_CBC_IV_LEN;
            case IpSecAlgorithm.CRYPT_AES_CTR:
                return AES_CTR_IV_LEN;
            case IpSecAlgorithm.AUTH_CRYPT_AES_GCM:
                return AES_GCM_IV_LEN;
            case IpSecAlgorithm.AUTH_CRYPT_CHACHA20_POLY1305:
                return CHACHA20_POLY1305_IV_LEN;
            default:
                throw new IllegalArgumentException(
                        "IV length unknown for algorithm" + cryptOrAead.getName());
        }
    }

    private int getBlkSize(IpSecAlgorithm cryptOrAead) {
        // RFC 4303, section 2.4 states that ciphertext plus pad_len, next_header fields must
        //     terminate on a 4-byte boundary. Thus, the minimum ciphertext block size is 4 bytes.
        if (cryptOrAead == null) { return 4; }

        switch (cryptOrAead.getName()) {
            case IpSecAlgorithm.CRYPT_AES_CBC:
                return AES_CBC_BLK_SIZE;
            case IpSecAlgorithm.CRYPT_AES_CTR:
                return AES_CTR_BLK_SIZE;
            case IpSecAlgorithm.AUTH_CRYPT_AES_GCM:
                return AES_GCM_BLK_SIZE;
            case IpSecAlgorithm.AUTH_CRYPT_CHACHA20_POLY1305:
                return CHACHA20_POLY1305_BLK_SIZE;
            default:
                throw new IllegalArgumentException(
                        "Blk size unknown for algorithm" + cryptOrAead.getName());
        }
    }

    public void checkTransform(
            int protocol,
            String localAddress,
            IpSecAlgorithm crypt,
            IpSecAlgorithm auth,
            IpSecAlgorithm aead,
            boolean doUdpEncap,
            int sendCount,
            boolean useJavaSockets)
            throws Exception {
        StatsChecker.initStatsChecker();
        InetAddress local = InetAddress.getByName(localAddress);

        try (UdpEncapsulationSocket encapSocket = mISM.openUdpEncapsulationSocket();
                SecurityParameterIndex spi =
                        mISM.allocateSecurityParameterIndex(local)) {

            IpSecTransform.Builder transformBuilder =
                    new IpSecTransform.Builder(InstrumentationRegistry.getContext());
            if (crypt != null) {
                transformBuilder.setEncryption(crypt);
            }
            if (auth != null) {
                transformBuilder.setAuthentication(auth);
            }
            if (aead != null) {
                transformBuilder.setAuthenticatedEncryption(aead);
            }

            if (doUdpEncap) {
                transformBuilder =
                        transformBuilder.setIpv4Encapsulation(encapSocket, encapSocket.getPort());
            }

            int ipHdrLen = local instanceof Inet6Address ? IP6_HDRLEN : IP4_HDRLEN;
            int transportHdrLen = 0;
            int udpEncapLen = doUdpEncap ? UDP_HDRLEN : 0;

            try (IpSecTransform transform =
                        transformBuilder.buildTransportModeTransform(local, spi)) {
                if (protocol == IPPROTO_TCP) {
                    transportHdrLen = TCP_HDRLEN_WITH_TIMESTAMP_OPT;
                    checkTcp(transform, local, sendCount, useJavaSockets);
                } else if (protocol == IPPROTO_UDP) {
                    transportHdrLen = UDP_HDRLEN;

                    // TODO: Also check connected udp.
                    checkUnconnectedUdp(transform, local, sendCount, useJavaSockets);
                } else {
                    throw new IllegalArgumentException("Invalid protocol");
                }
            }

            checkStatsChecker(
                    protocol,
                    ipHdrLen,
                    transportHdrLen,
                    udpEncapLen,
                    sendCount,
                    getIvLen(crypt != null ? crypt : aead),
                    getBlkSize(crypt != null ? crypt : aead),
                    getTruncLenBits(auth != null ? auth : aead));
        }
    }

    private void checkStatsChecker(
            int protocol,
            int ipHdrLen,
            int transportHdrLen,
            int udpEncapLen,
            int sendCount,
            int ivLen,
            int blkSize,
            int truncLenBits)
            throws Exception {
        int innerPacketSize = TEST_DATA.length + transportHdrLen + ipHdrLen;
        int outerPacketSize =
                PacketUtils.calculateEspPacketSize(
                                TEST_DATA.length + transportHdrLen, ivLen, blkSize, truncLenBits)
                        + udpEncapLen
                        + ipHdrLen;

        int expectedOuterBytes = outerPacketSize * sendCount;
        int expectedInnerBytes = innerPacketSize * sendCount;
        int expectedPackets = sendCount;

        // Each run sends two packets, one in each direction.
        sendCount *= 2;
        expectedOuterBytes *= 2;
        expectedInnerBytes *= 2;
        expectedPackets *= 2;

        // Add TCP ACKs for data packets
        if (protocol == IPPROTO_TCP) {
            int encryptedTcpPktSize =
                    PacketUtils.calculateEspPacketSize(
                            TCP_HDRLEN_WITH_TIMESTAMP_OPT, ivLen, blkSize, truncLenBits);

            // Add data packet ACKs
            expectedOuterBytes += (encryptedTcpPktSize + udpEncapLen + ipHdrLen) * (sendCount);
            expectedInnerBytes += (TCP_HDRLEN_WITH_TIMESTAMP_OPT + ipHdrLen) * (sendCount);
            expectedPackets += sendCount;
        }

        StatsChecker.waitForNumPackets(expectedPackets);

        // eBPF only counts inner packets, whereas xt_qtaguid counts outer packets. Allow both
        StatsChecker.assertUidStatsDelta(
                expectedOuterBytes,
                expectedPackets,
                expectedInnerBytes,
                expectedOuterBytes,
                expectedPackets);

        // Unreliable at low numbers due to potential interference from other processes.
        if (sendCount >= 1000) {
            StatsChecker.assertIfaceStatsDelta(
                    expectedOuterBytes, expectedPackets, expectedOuterBytes, expectedPackets);
        }
    }

    private void checkIkePacket(
            NativeUdpSocket wrappedEncapSocket, InetAddress localAddr) throws Exception {
        StatsChecker.initStatsChecker();

        try (NativeUdpSocket remoteSocket = new NativeUdpSocket(getBoundUdpSocket(localAddr))) {

            // Append IKE/ESP header - 4 bytes of SPI, 4 bytes of seq number, all zeroed out
            // If the first four bytes are zero, assume non-ESP (IKE traffic)
            byte[] dataWithEspHeader = new byte[TEST_DATA.length + 8];
            System.arraycopy(TEST_DATA, 0, dataWithEspHeader, 8, TEST_DATA.length);

            // Send the IKE packet from remoteSocket to wrappedEncapSocket. Since IKE packets
            // are multiplexed over the socket, we expect them to appear on the encap socket
            // (as opposed to being decrypted and received on the non-encap socket)
            remoteSocket.sendTo(dataWithEspHeader, localAddr, wrappedEncapSocket.getPort());
            byte[] in = wrappedEncapSocket.receive();
            assertArrayEquals("Encapsulated data did not match.", dataWithEspHeader, in);

            // Also test that the IKE socket can send data out.
            wrappedEncapSocket.sendTo(dataWithEspHeader, localAddr, remoteSocket.getPort());
            in = remoteSocket.receive();
            assertArrayEquals("Encapsulated data did not match.", dataWithEspHeader, in);

            // Calculate expected packet sizes. Always use IPv4 header, since our kernels only
            // guarantee support of UDP encap on IPv4.
            int expectedNumPkts = 2;
            int expectedPacketSize =
                    expectedNumPkts * (dataWithEspHeader.length + UDP_HDRLEN + IP4_HDRLEN);

            StatsChecker.waitForNumPackets(expectedNumPkts);
            StatsChecker.assertUidStatsDelta(
                    expectedPacketSize,
                    expectedNumPkts,
                    expectedPacketSize,
                    expectedPacketSize,
                    expectedNumPkts);
            StatsChecker.assertIfaceStatsDelta(
                    expectedPacketSize, expectedNumPkts, expectedPacketSize, expectedNumPkts);
        }
    }

    @Test
    @SkipMainlinePresubmit(reason = "Out of SLO flakiness")
    public void testIkeOverUdpEncapSocket() throws Exception {
        // IPv6 not supported for UDP-encap-ESP
        InetAddress local = InetAddress.getByName(IPV4_LOOPBACK);
        try (UdpEncapsulationSocket encapSocket = mISM.openUdpEncapsulationSocket()) {
            NativeUdpSocket wrappedEncapSocket =
                    new NativeUdpSocket(encapSocket.getFileDescriptor());
            checkIkePacket(wrappedEncapSocket, local);

            // Now try with a transform applied to a socket using this Encap socket
            IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
            IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_MD5, getKey(128), 96);

            try (SecurityParameterIndex spi =
                            mISM.allocateSecurityParameterIndex(local);
                    IpSecTransform transform =
                            new IpSecTransform.Builder(InstrumentationRegistry.getContext())
                                    .setEncryption(crypt)
                                    .setAuthentication(auth)
                                    .setIpv4Encapsulation(encapSocket, encapSocket.getPort())
                                    .buildTransportModeTransform(local, spi);
                    JavaUdpSocket localSocket = new JavaUdpSocket(local)) {
                applyTransformBidirectionally(mISM, transform, localSocket);

                checkIkePacket(wrappedEncapSocket, local);
            }
        }
    }

    // TODO: Check IKE over ESP sockets (IPv4, IPv6) - does this need SOCK_RAW?

    /* TODO: Re-enable these when policy matcher works for reflected packets
     *
     * The issue here is that A sends to B, and everything is new; therefore PREROUTING counts
     * correctly. But it appears that the security path is not cleared afterwards, thus when A
     * sends an ACK back to B, the policy matcher flags it as a "IPSec" packet. See b/70635417
     */

    // public void testInterfaceCountersTcp4() throws Exception {
    //     IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
    //     IpSecAlgorithm auth = new IpSecAlgorithm(
    //             IpSecAlgorithm.AUTH_HMAC_MD5, getKey(128), 96);
    //     checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, false, 1000);
    // }

    // public void testInterfaceCountersTcp6() throws Exception {
    //     IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
    //     IpSecAlgorithm auth = new IpSecAlgorithm(
    //             IpSecAlgorithm.AUTH_HMAC_MD5, getKey(128), 96);
    //     checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, false, 1000);
    // }

    // public void testInterfaceCountersTcp4UdpEncap() throws Exception {
    //     IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
    //     IpSecAlgorithm auth =
    //             new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_MD5, getKey(128), 96);
    //     checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, true, 1000);
    // }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testGetSupportedAlgorithms() throws Exception {
        final Map<String, Integer> algoToRequiredMinSdk = new HashMap<>();
        algoToRequiredMinSdk.put(CRYPT_AES_CBC, Build.VERSION_CODES.P);
        algoToRequiredMinSdk.put(AUTH_HMAC_MD5, Build.VERSION_CODES.P);
        algoToRequiredMinSdk.put(AUTH_HMAC_SHA1, Build.VERSION_CODES.P);
        algoToRequiredMinSdk.put(AUTH_HMAC_SHA256, Build.VERSION_CODES.P);
        algoToRequiredMinSdk.put(AUTH_HMAC_SHA384, Build.VERSION_CODES.P);
        algoToRequiredMinSdk.put(AUTH_HMAC_SHA512, Build.VERSION_CODES.P);
        algoToRequiredMinSdk.put(AUTH_CRYPT_AES_GCM, Build.VERSION_CODES.P);

        algoToRequiredMinSdk.put(CRYPT_AES_CTR, Build.VERSION_CODES.S);
        algoToRequiredMinSdk.put(AUTH_AES_CMAC, Build.VERSION_CODES.S);
        algoToRequiredMinSdk.put(AUTH_AES_XCBC, Build.VERSION_CODES.S);
        algoToRequiredMinSdk.put(AUTH_CRYPT_CHACHA20_POLY1305, Build.VERSION_CODES.S);

        final Set<String> supportedAlgos = IpSecAlgorithm.getSupportedAlgorithms();

        // Verify all supported algorithms are valid
        for (String algo : supportedAlgos) {
            assertTrue("Found invalid algo " + algo, algoToRequiredMinSdk.keySet().contains(algo));
        }

        // Verify all mandatory algorithms are supported
        for (Entry<String, Integer> entry : algoToRequiredMinSdk.entrySet()) {
            if (Math.min(getFirstApiLevel(), getVendorApiLevel()) >= entry.getValue()) {
                assertTrue(
                        "Fail to support " + entry.getKey(),
                        supportedAlgos.contains(entry.getKey()));
            }
        }
    }

    @Test
    public void testInterfaceCountersUdp4() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_MD5, getKey(128), 96);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1000, false);
    }

    @Test
    public void testInterfaceCountersUdp6() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_MD5, getKey(128), 96);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1000, false);
    }

    @Test
    public void testInterfaceCountersUdp4UdpEncap() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_MD5, getKey(128), 96);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1000, false);
    }

    @Test
    public void testAesCbcHmacMd5Tcp4() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_MD5, getKey(128), 96);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacMd5Tcp6() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_MD5, getKey(128), 96);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacMd5Udp4() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_MD5, getKey(128), 96);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacMd5Udp6() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_MD5, getKey(128), 96);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha1Tcp4() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA1, getKey(160), 96);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha1Tcp6() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA1, getKey(160), 96);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha1Udp4() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA1, getKey(160), 96);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha1Udp6() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA1, getKey(160), 96);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha256Tcp4() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, getKey(256), 128);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha256Tcp6() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, getKey(256), 128);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha256Udp4() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, getKey(256), 128);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha256Udp6() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, getKey(256), 128);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha384Tcp4() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA384, getKey(384), 192);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha384Tcp6() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA384, getKey(384), 192);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha384Udp4() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA384, getKey(384), 192);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha384Udp6() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA384, getKey(384), 192);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha512Tcp4() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA512, getKey(512), 256);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha512Tcp6() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA512, getKey(512), 256);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha512Udp4() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA512, getKey(512), 256);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcHmacSha512Udp6() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA512, getKey(512), 256);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    private static IpSecAlgorithm buildCryptAesCtr() throws Exception {
        return new IpSecAlgorithm(CRYPT_AES_CTR, getKeyBytes(AES_CTR_KEY_LEN_20));
    }

    private static IpSecAlgorithm buildAuthHmacSha512() throws Exception {
        return new IpSecAlgorithm(
                AUTH_HMAC_SHA512, getKeyBytes(HMAC_SHA512_KEY_LEN), HMAC_SHA512_ICV_LEN * 8);
    }

    @Test
    public void testAesCtrHmacSha512Tcp4() throws Exception {
        assumeTrue(hasIpSecAlgorithm(CRYPT_AES_CTR));

        final IpSecAlgorithm crypt = buildCryptAesCtr();
        final IpSecAlgorithm auth = buildAuthHmacSha512();
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCtrHmacSha512Tcp6() throws Exception {
        assumeTrue(hasIpSecAlgorithm(CRYPT_AES_CTR));

        final IpSecAlgorithm crypt = buildCryptAesCtr();
        final IpSecAlgorithm auth = buildAuthHmacSha512();
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCtrHmacSha512Udp4() throws Exception {
        assumeTrue(hasIpSecAlgorithm(CRYPT_AES_CTR));

        final IpSecAlgorithm crypt = buildCryptAesCtr();
        final IpSecAlgorithm auth = buildAuthHmacSha512();
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCtrHmacSha512Udp6() throws Exception {
        assumeTrue(hasIpSecAlgorithm(CRYPT_AES_CTR));

        final IpSecAlgorithm crypt = buildCryptAesCtr();
        final IpSecAlgorithm auth = buildAuthHmacSha512();
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    private static IpSecAlgorithm buildCryptAesCbc() throws Exception {
        return new IpSecAlgorithm(CRYPT_AES_CBC, CRYPT_KEY);
    }

    private static IpSecAlgorithm buildAuthAesXcbc() throws Exception {
        return new IpSecAlgorithm(
                AUTH_AES_XCBC, getKeyBytes(AES_XCBC_KEY_LEN), AES_XCBC_ICV_LEN * 8);
    }

    private static IpSecAlgorithm buildAuthAesCmac() throws Exception {
        return new IpSecAlgorithm(
                AUTH_AES_CMAC, getKeyBytes(AES_CMAC_KEY_LEN), AES_CMAC_ICV_LEN * 8);
    }

    @Test
    public void testAesCbcAesXCbcTcp4() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_AES_XCBC));

        final IpSecAlgorithm crypt = buildCryptAesCbc();
        final IpSecAlgorithm auth = buildAuthAesXcbc();
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcAesXCbcTcp6() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_AES_XCBC));

        final IpSecAlgorithm crypt = buildCryptAesCbc();
        final IpSecAlgorithm auth = buildAuthAesXcbc();
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcAesXCbcUdp4() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_AES_XCBC));

        final IpSecAlgorithm crypt = buildCryptAesCbc();
        final IpSecAlgorithm auth = buildAuthAesXcbc();
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcAesXCbcUdp6() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_AES_XCBC));

        final IpSecAlgorithm crypt = buildCryptAesCbc();
        final IpSecAlgorithm auth = buildAuthAesXcbc();
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcAesCmacTcp4() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_AES_CMAC));

        final IpSecAlgorithm crypt = buildCryptAesCbc();
        final IpSecAlgorithm auth = buildAuthAesCmac();
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcAesCmacTcp6() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_AES_CMAC));

        final IpSecAlgorithm crypt = buildCryptAesCbc();
        final IpSecAlgorithm auth = buildAuthAesCmac();
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcAesCmacUdp4() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_AES_CMAC));

        final IpSecAlgorithm crypt = buildCryptAesCbc();
        final IpSecAlgorithm auth = buildAuthAesCmac();
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesCbcAesCmacUdp6() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_AES_CMAC));

        final IpSecAlgorithm crypt = buildCryptAesCbc();
        final IpSecAlgorithm auth = buildAuthAesCmac();
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, auth, null, false, 1, true);
    }

    @Test
    public void testAesGcm64Tcp4() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 64);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    @Test
    public void testAesGcm64Tcp6() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 64);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    @Test
    public void testAesGcm64Udp4() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 64);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    @Test
    public void testAesGcm64Udp6() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 64);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    @Test
    public void testAesGcm96Tcp4() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 96);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    @Test
    public void testAesGcm96Tcp6() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 96);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    @Test
    public void testAesGcm96Udp4() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 96);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    @Test
    public void testAesGcm96Udp6() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 96);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    @Test
    public void testAesGcm128Tcp4() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 128);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    @Test
    public void testAesGcm128Tcp6() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 128);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    @Test
    public void testAesGcm128Udp4() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 128);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    @Test
    public void testAesGcm128Udp6() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 128);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    private static IpSecAlgorithm buildAuthCryptChaCha20Poly1305() throws Exception {
        return new IpSecAlgorithm(
                AUTH_CRYPT_CHACHA20_POLY1305, AEAD_KEY, CHACHA20_POLY1305_ICV_LEN * 8);
    }

    @Test
    public void testChaCha20Poly1305Tcp4() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_CRYPT_CHACHA20_POLY1305));

        final IpSecAlgorithm authCrypt = buildAuthCryptChaCha20Poly1305();
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    @Test
    public void testChaCha20Poly1305Tcp6() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_CRYPT_CHACHA20_POLY1305));

        final IpSecAlgorithm authCrypt = buildAuthCryptChaCha20Poly1305();
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    @Test
    public void testChaCha20Poly1305Udp4() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_CRYPT_CHACHA20_POLY1305));

        final IpSecAlgorithm authCrypt = buildAuthCryptChaCha20Poly1305();
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    @Test
    public void testChaCha20Poly1305Udp6() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_CRYPT_CHACHA20_POLY1305));

        final IpSecAlgorithm authCrypt = buildAuthCryptChaCha20Poly1305();
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, null, null, authCrypt, false, 1, true);
    }

    @Test
    public void testAesCbcHmacMd5Tcp4UdpEncap() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_MD5, getKey(128), 96);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesCbcHmacMd5Udp4UdpEncap() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_MD5, getKey(128), 96);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesCbcHmacSha1Tcp4UdpEncap() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA1, getKey(160), 96);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesCbcHmacSha1Udp4UdpEncap() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA1, getKey(160), 96);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesCbcHmacSha256Tcp4UdpEncap() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, getKey(256), 128);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesCbcHmacSha256Udp4UdpEncap() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, getKey(256), 128);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesCbcHmacSha384Tcp4UdpEncap() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA384, getKey(384), 192);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesCbcHmacSha384Udp4UdpEncap() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA384, getKey(384), 192);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesCbcHmacSha512Tcp4UdpEncap() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA512, getKey(512), 256);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesCbcHmacSha512Udp4UdpEncap() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA512, getKey(512), 256);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesCtrHmacSha512Tcp4UdpEncap() throws Exception {
        assumeTrue(hasIpSecAlgorithm(CRYPT_AES_CTR));

        final IpSecAlgorithm crypt = buildCryptAesCtr();
        final IpSecAlgorithm auth = buildAuthHmacSha512();
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesCtrHmacSha512Udp4UdpEncap() throws Exception {
        assumeTrue(hasIpSecAlgorithm(CRYPT_AES_CTR));

        final IpSecAlgorithm crypt = buildCryptAesCtr();
        final IpSecAlgorithm auth = buildAuthHmacSha512();
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesCbcAesXCbcTcp4UdpEncap() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_AES_XCBC));

        final IpSecAlgorithm crypt = new IpSecAlgorithm(CRYPT_AES_CBC, CRYPT_KEY);
        final IpSecAlgorithm auth = new IpSecAlgorithm(AUTH_AES_XCBC, getKey(128), 96);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesCbcAesXCbcUdp4UdpEncap() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_AES_XCBC));

        final IpSecAlgorithm crypt = new IpSecAlgorithm(CRYPT_AES_CBC, CRYPT_KEY);
        final IpSecAlgorithm auth = new IpSecAlgorithm(AUTH_AES_XCBC, getKey(128), 96);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesCbcAesCmacTcp4UdpEncap() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_AES_CMAC));

        final IpSecAlgorithm crypt = new IpSecAlgorithm(CRYPT_AES_CBC, CRYPT_KEY);
        final IpSecAlgorithm auth = new IpSecAlgorithm(AUTH_AES_CMAC, getKey(128), 96);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesCbcAesCmacUdp4UdpEncap() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_AES_CMAC));

        final IpSecAlgorithm crypt = new IpSecAlgorithm(CRYPT_AES_CBC, CRYPT_KEY);
        final IpSecAlgorithm auth = new IpSecAlgorithm(AUTH_AES_CMAC, getKey(128), 96);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, auth, null, true, 1, true);
    }

    @Test
    public void testAesGcm64Tcp4UdpEncap() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 64);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, true);
    }

    @Test
    public void testAesGcm64Udp4UdpEncap() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 64);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, true);
    }

    @Test
    public void testAesGcm96Tcp4UdpEncap() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 96);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, true);
    }

    @Test
    public void testAesGcm96Udp4UdpEncap() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 96);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, true);
    }

    @Test
    public void testAesGcm128Tcp4UdpEncap() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 128);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, true);
    }

    @Test
    public void testAesGcm128Udp4UdpEncap() throws Exception {
        IpSecAlgorithm authCrypt =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AEAD_KEY, 128);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, true);
    }

    @Test
    public void testChaCha20Poly1305Tcp4UdpEncap() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_CRYPT_CHACHA20_POLY1305));

        final IpSecAlgorithm authCrypt = buildAuthCryptChaCha20Poly1305();
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, true);
    }

    @Test
    public void testChaCha20Poly1305Udp4UdpEncap() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_CRYPT_CHACHA20_POLY1305));

        final IpSecAlgorithm authCrypt = buildAuthCryptChaCha20Poly1305();
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, null, authCrypt, true, 1, true);
    }

    @Test
    public void testCryptUdp4() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, null, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, null, null, false, 1, true);
    }

    @Test
    public void testAuthUdp4() throws Exception {
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, getKey(256), 128);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, auth, null, false, 1, true);
    }

    @Test
    public void testCryptUdp6() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, null, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, crypt, null, null, false, 1, true);
    }

    @Test
    public void testAuthUdp6() throws Exception {
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, getKey(256), 128);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, null, auth, null, false, 1, false);
        checkTransform(IPPROTO_UDP, IPV6_LOOPBACK, null, auth, null, false, 1, true);
    }

    @Test
    public void testCryptTcp4() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, null, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, null, null, false, 1, true);
    }

    @Test
    public void testAuthTcp4() throws Exception {
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, getKey(256), 128);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, auth, null, false, 1, true);
    }

    @Test
    public void testCryptTcp6() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, null, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, crypt, null, null, false, 1, true);
    }

    @Test
    public void testAuthTcp6() throws Exception {
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, getKey(256), 128);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, null, auth, null, false, 1, false);
        checkTransform(IPPROTO_TCP, IPV6_LOOPBACK, null, auth, null, false, 1, true);
    }

    @Test
    public void testCryptUdp4UdpEncap() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, null, null, true, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, crypt, null, null, true, 1, true);
    }

    @Test
    public void testAuthUdp4UdpEncap() throws Exception {
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, getKey(256), 128);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, auth, null, true, 1, false);
        checkTransform(IPPROTO_UDP, IPV4_LOOPBACK, null, auth, null, true, 1, true);
    }

    @Test
    public void testCryptTcp4UdpEncap() throws Exception {
        IpSecAlgorithm crypt = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, null, null, true, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, crypt, null, null, true, 1, true);
    }

    @Test
    public void testAuthTcp4UdpEncap() throws Exception {
        IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, getKey(256), 128);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, auth, null, true, 1, false);
        checkTransform(IPPROTO_TCP, IPV4_LOOPBACK, null, auth, null, true, 1, true);
    }

    @Test
    public void testOpenUdpEncapSocketSpecificPort() throws Exception {
        UdpEncapsulationSocket encapSocket = null;
        int port = -1;
        for (int i = 0; i < MAX_PORT_BIND_ATTEMPTS; i++) {
            try {
                port = findUnusedPort();
                encapSocket = mISM.openUdpEncapsulationSocket(port);
                break;
            } catch (ErrnoException e) {
                if (e.errno == OsConstants.EADDRINUSE) {
                    // Someone claimed the port since we called findUnusedPort.
                    continue;
                }
                throw e;
            } finally {
                if (encapSocket != null) {
                    encapSocket.close();
                }
            }
        }

        if (encapSocket == null) {
            fail("Failed " + MAX_PORT_BIND_ATTEMPTS + " attempts to bind to a port");
        }

        assertTrue("Returned invalid port", encapSocket.getPort() == port);
    }

    @Test
    public void testOpenUdpEncapSocketRandomPort() throws Exception {
        try (UdpEncapsulationSocket encapSocket = mISM.openUdpEncapsulationSocket()) {
            assertTrue("Returned invalid port", encapSocket.getPort() != 0);
        }
    }
}
