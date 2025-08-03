/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.app.AppOpsManager.OP_MANAGE_IPSEC_TUNNELS;
import static android.net.IpSecManager.UdpEncapsulationSocket;
import static android.net.cts.IpSecManagerTest.assumeExperimentalIpv6UdpEncapSupported;
import static android.net.cts.IpSecManagerTest.isIpv6UdpEncapSupported;
import static android.net.cts.PacketUtils.AES_CBC_BLK_SIZE;
import static android.net.cts.PacketUtils.AES_CBC_IV_LEN;
import static android.net.cts.PacketUtils.BytePayload;
import static android.net.cts.PacketUtils.EspHeader;
import static android.net.cts.PacketUtils.IP4_HDRLEN;
import static android.net.cts.PacketUtils.IP6_HDRLEN;
import static android.net.cts.PacketUtils.IpHeader;
import static android.net.cts.PacketUtils.UDP_HDRLEN;
import static android.net.cts.PacketUtils.UdpHeader;
import static android.net.cts.PacketUtils.getIpHeader;
import static android.net.cts.util.CtsNetUtils.TestNetworkCallback;
import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.AF_INET6;

import static com.android.compatibility.common.util.PropertyUtil.getVsrApiLevel;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.IpSecAlgorithm;
import android.net.IpSecManager;
import android.net.IpSecManager.IpSecTunnelInterface;
import android.net.IpSecTransform;
import android.net.LinkAddress;
import android.net.Network;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.net.cts.PacketUtils.Payload;
import android.net.cts.util.CtsNetUtils;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;

// TODO: b/268552823 Improve the readability of IpSecManagerTunnelTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "MANAGE_TEST_NETWORKS permission can't be granted to instant apps")
public class IpSecManagerTunnelTest extends IpSecBaseTest {
    @Rule public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    private static final String TAG = IpSecManagerTunnelTest.class.getSimpleName();

    private static final InetAddress LOCAL_OUTER_4 = InetAddress.parseNumericAddress("192.0.2.1");
    private static final InetAddress REMOTE_OUTER_4 = InetAddress.parseNumericAddress("192.0.2.2");
    private static final InetAddress LOCAL_OUTER_6 =
            InetAddress.parseNumericAddress("2001:db8:1::1");
    private static final InetAddress REMOTE_OUTER_6 =
            InetAddress.parseNumericAddress("2001:db8:1::2");

    private static final InetAddress LOCAL_OUTER_4_NEW =
            InetAddress.parseNumericAddress("192.0.2.101");
    private static final InetAddress REMOTE_OUTER_4_NEW =
            InetAddress.parseNumericAddress("192.0.2.102");
    private static final InetAddress LOCAL_OUTER_6_NEW =
            InetAddress.parseNumericAddress("2001:db8:1::101");
    private static final InetAddress REMOTE_OUTER_6_NEW =
            InetAddress.parseNumericAddress("2001:db8:1::102");

    private static final InetAddress LOCAL_INNER_4 =
            InetAddress.parseNumericAddress("198.51.100.1");
    private static final InetAddress REMOTE_INNER_4 =
            InetAddress.parseNumericAddress("198.51.100.2");
    private static final InetAddress LOCAL_INNER_6 =
            InetAddress.parseNumericAddress("2001:db8:2::1");
    private static final InetAddress REMOTE_INNER_6 =
            InetAddress.parseNumericAddress("2001:db8:2::2");

    private static final int IP4_PREFIX_LEN = 32;
    private static final int IP6_PREFIX_LEN = 128;
    private static final int IP6_UDP_ENCAP_SOCKET_PORT_ANY = 65536;

    private static final int TIMEOUT_MS = 500;

    // Static state to reduce setup/teardown
    private static ConnectivityManager sCM;
    private static TestNetworkManager sTNM;

    private static TunNetworkWrapper sTunWrapper;
    private static TunNetworkWrapper sTunWrapperNew;

    private static Context sContext = InstrumentationRegistry.getContext();
    private static final CtsNetUtils mCtsNetUtils = new CtsNetUtils(sContext);

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();
        sCM = (ConnectivityManager) sContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        sTNM = (TestNetworkManager) sContext.getSystemService(Context.TEST_NETWORK_SERVICE);

        // Under normal circumstances, the MANAGE_IPSEC_TUNNELS appop would be auto-granted, and
        // a standard permission is insufficient. So we shell out the appop, to give us the
        // right appop permissions.
        mCtsNetUtils.setAppopPrivileged(OP_MANAGE_IPSEC_TUNNELS, true);

        sTunWrapper = new TunNetworkWrapper(LOCAL_OUTER_4, LOCAL_OUTER_6);
        sTunWrapperNew = new TunNetworkWrapper(LOCAL_OUTER_4_NEW, LOCAL_OUTER_6_NEW);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Set to true before every run; some tests flip this.
        mCtsNetUtils.setAppopPrivileged(OP_MANAGE_IPSEC_TUNNELS, true);

        // Clear TunUtils state
        sTunWrapper.utils.reset();
        sTunWrapperNew.utils.reset();
    }

    private static void tearDownTunWrapperIfNotNull(TunNetworkWrapper tunWrapper) throws Exception {
        if (tunWrapper != null) {
            tunWrapper.tearDown();
        }
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        mCtsNetUtils.setAppopPrivileged(OP_MANAGE_IPSEC_TUNNELS, false);

        tearDownTunWrapperIfNotNull(sTunWrapper);
        tearDownTunWrapperIfNotNull(sTunWrapperNew);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private static class TunNetworkWrapper {
        public final ParcelFileDescriptor fd;
        public final TestNetworkCallback networkCallback;
        public final Network network;
        public final TunUtils utils;

        TunNetworkWrapper(InetAddress... addresses) throws Exception {
            final LinkAddress[] linkAddresses = new LinkAddress[addresses.length];
            for (int i = 0; i < linkAddresses.length; i++) {
                InetAddress addr = addresses[i];
                if (addr instanceof Inet4Address) {
                    linkAddresses[i] = new LinkAddress(addr, IP4_PREFIX_LEN);
                } else {
                    linkAddresses[i] = new LinkAddress(addr, IP6_PREFIX_LEN);
                }
            }

            try {
                final TestNetworkInterface testIface = sTNM.createTunInterface(linkAddresses);

                fd = testIface.getFileDescriptor();
                networkCallback = mCtsNetUtils.setupAndGetTestNetwork(testIface.getInterfaceName());
                networkCallback.waitForAvailable();
                network = networkCallback.currentNetwork;
            } catch (Exception e) {
                tearDown();
                throw e;
            }

            utils = new TunUtils(fd);
        }

        public void tearDown() throws Exception {
            if (networkCallback != null) {
                sCM.unregisterNetworkCallback(networkCallback);
            }

            if (network != null) {
                sTNM.teardownTestNetwork(network);
            }

            if (fd != null) {
                fd.close();
            }
        }
    }

    @Test
    public void testSecurityExceptionCreateTunnelInterfaceWithoutAppop() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());

        // Ensure we don't have the appop. Permission is not requested in the Manifest
        mCtsNetUtils.setAppopPrivileged(OP_MANAGE_IPSEC_TUNNELS, false);

        // Security exceptions are thrown regardless of IPv4/IPv6. Just test one
        try {
            mISM.createIpSecTunnelInterface(LOCAL_INNER_6, REMOTE_INNER_6, sTunWrapper.network);
            fail("Did not throw SecurityException for Tunnel creation without appop");
        } catch (SecurityException expected) {
        }
    }

    @Test
    public void testSecurityExceptionBuildTunnelTransformWithoutAppop() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());

        // Ensure we don't have the appop. Permission is not requested in the Manifest
        mCtsNetUtils.setAppopPrivileged(OP_MANAGE_IPSEC_TUNNELS, false);

        // Security exceptions are thrown regardless of IPv4/IPv6. Just test one
        try (IpSecManager.SecurityParameterIndex spi =
                        mISM.allocateSecurityParameterIndex(LOCAL_INNER_4);
                IpSecTransform transform =
                        new IpSecTransform.Builder(sContext)
                                .buildTunnelModeTransform(REMOTE_INNER_4, spi)) {
            fail("Did not throw SecurityException for Transform creation without appop");
        } catch (SecurityException expected) {
        }
    }

    /* Test runnables for callbacks after IPsec tunnels are set up. */
    private abstract class IpSecTunnelTestRunnable {
        /**
         * Runs the test code, and returns the inner socket port, if any.
         *
         * @param ipsecNetwork The IPsec Interface based Network for binding sockets on
         * @param tunnelIface The IPsec tunnel interface that will be tested
         * @param tunUtils The utility of the IPsec tunnel interface's underlying TUN network
         * @param inTunnelTransform The inbound tunnel mode transform
         * @param outTunnelTransform The outbound tunnel mode transform
         * @param localOuter The local address of the outer IP packet
         * @param remoteOuter The remote address of the outer IP packet
         * @param seqNum The expected sequence number of the inbound packet
         * @throws Exception if any part of the test failed.
         */
        public abstract int run(
                Network ipsecNetwork,
                IpSecTunnelInterface tunnelIface,
                TunUtils tunUtils,
                IpSecTransform inTunnelTransform,
                IpSecTransform outTunnelTransform,
                InetAddress localOuter,
                InetAddress remoteOuter,
                int seqNum)
                throws Exception;
    }

    private int getPacketSize(
            int innerFamily, int outerFamily, boolean useEncap, boolean transportInTunnelMode) {
        int expectedPacketSize = TEST_DATA.length + UDP_HDRLEN;

        // Inner Transport mode packet size
        if (transportInTunnelMode) {
            expectedPacketSize =
                    PacketUtils.calculateEspPacketSize(
                            expectedPacketSize,
                            AES_CBC_IV_LEN,
                            AES_CBC_BLK_SIZE,
                            AUTH_KEY.length * 4);
        }

        // Inner IP Header
        expectedPacketSize += innerFamily == AF_INET ? IP4_HDRLEN : IP6_HDRLEN;

        // Tunnel mode transform size
        expectedPacketSize =
                PacketUtils.calculateEspPacketSize(
                        expectedPacketSize, AES_CBC_IV_LEN, AES_CBC_BLK_SIZE, AUTH_KEY.length * 4);

        // UDP encap size
        expectedPacketSize += useEncap ? UDP_HDRLEN : 0;

        // Outer IP Header
        expectedPacketSize += outerFamily == AF_INET ? IP4_HDRLEN : IP6_HDRLEN;

        return expectedPacketSize;
    }

    private UdpEncapsulationSocket openUdpEncapsulationSocket(int ipVersion) throws Exception {
        if (ipVersion == AF_INET) {
            return mISM.openUdpEncapsulationSocket();
        }

        if (!isIpv6UdpEncapSupported()) {
            throw new UnsupportedOperationException("IPv6 UDP encapsulation unsupported");
        }

        return mISM.openUdpEncapsulationSocket(IP6_UDP_ENCAP_SOCKET_PORT_ANY);
    }

    private interface IpSecTunnelTestRunnableFactory {
        /**
         * Build a IpSecTunnelTestRunnable.
         *
         * @param transportInTunnelMode indicate if there needs to be a transport mode transform
         *     inside the tunnel mode transform
         * @param spi The IPsec SPI
         * @param localInner The local address of the inner IP packet
         * @param remoteInner The remote address of the inner IP packet
         * @param inTransportTransform The inbound transport mode transform
         * @param outTransportTransform The outbound transport mode transform
         * @param encapSocket The UDP encapsulation socket or null
         * @param innerSocketPort The inner socket port
         */
        IpSecTunnelTestRunnable getIpSecTunnelTestRunnable(
                boolean transportInTunnelMode,
                int spi,
                InetAddress localInner,
                InetAddress remoteInner,
                IpSecTransform inTransportTransform,
                IpSecTransform outTransportTransform,
                UdpEncapsulationSocket encapSocket,
                int innerSocketPort)
                throws Exception;
    }

    private class OutputIpSecTunnelTestRunnableFactory implements IpSecTunnelTestRunnableFactory {
        public IpSecTunnelTestRunnable getIpSecTunnelTestRunnable(
                boolean transportInTunnelMode,
                int spi,
                InetAddress localInner,
                InetAddress remoteInner,
                IpSecTransform inTransportTransform,
                IpSecTransform outTransportTransform,
                UdpEncapsulationSocket encapSocket,
                int unusedInnerSocketPort) {
            return new IpSecTunnelTestRunnable() {
                @Override
                public int run(
                        Network ipsecNetwork,
                        IpSecTunnelInterface tunnelIface,
                        TunUtils tunUtils,
                        IpSecTransform inTunnelTransform,
                        IpSecTransform outTunnelTransform,
                        InetAddress localOuter,
                        InetAddress remoteOuter,
                        int seqNum)
                        throws Exception {
                    // Build a socket and send traffic
                    JavaUdpSocket socket = new JavaUdpSocket(localInner);
                    ipsecNetwork.bindSocket(socket.mSocket);
                    int innerSocketPort = socket.getPort();

                    // For Transport-In-Tunnel mode, apply transform to socket
                    if (transportInTunnelMode) {
                        mISM.applyTransportModeTransform(
                                socket.mSocket, IpSecManager.DIRECTION_IN, inTransportTransform);
                        mISM.applyTransportModeTransform(
                                socket.mSocket, IpSecManager.DIRECTION_OUT, outTransportTransform);
                    }

                    socket.sendTo(TEST_DATA, remoteInner, socket.getPort());

                    // Verify that an encrypted packet is sent. As of right now, checking encrypted
                    // body is not possible, due to the test not knowing some of the fields of the
                    // inner IP header (flow label, flags, etc)
                    int innerFamily = localInner instanceof Inet4Address ? AF_INET : AF_INET6;
                    int outerFamily = localOuter instanceof Inet4Address ? AF_INET : AF_INET6;
                    boolean useEncap = encapSocket != null;
                    int expectedPacketSize =
                            getPacketSize(
                                    innerFamily, outerFamily, useEncap, transportInTunnelMode);
                    tunUtils.awaitEspPacketNoPlaintext(
                            spi, TEST_DATA, useEncap, expectedPacketSize);
                    socket.close();

                    return innerSocketPort;
                }
            };
        }
    }

    private class InputReflectedIpSecTunnelTestRunnableFactory
            implements IpSecTunnelTestRunnableFactory {
        public IpSecTunnelTestRunnable getIpSecTunnelTestRunnable(
                boolean transportInTunnelMode,
                int spi,
                InetAddress localInner,
                InetAddress remoteInner,
                IpSecTransform inTransportTransform,
                IpSecTransform outTransportTransform,
                UdpEncapsulationSocket encapSocket,
                int innerSocketPort)
                throws Exception {
            return new IpSecTunnelTestRunnable() {
                @Override
                public int run(
                        Network ipsecNetwork,
                        IpSecTunnelInterface tunnelIface,
                        TunUtils tunUtils,
                        IpSecTransform inTunnelTransform,
                        IpSecTransform outTunnelTransform,
                        InetAddress localOuter,
                        InetAddress remoteOuter,
                        int seqNum)
                        throws Exception {
                    // Build a socket and receive traffic
                    JavaUdpSocket socket = new JavaUdpSocket(localInner, innerSocketPort);
                    ipsecNetwork.bindSocket(socket.mSocket);

                    // For Transport-In-Tunnel mode, apply transform to socket
                    if (transportInTunnelMode) {
                        mISM.applyTransportModeTransform(
                                socket.mSocket, IpSecManager.DIRECTION_IN, outTransportTransform);
                        mISM.applyTransportModeTransform(
                                socket.mSocket, IpSecManager.DIRECTION_OUT, inTransportTransform);
                    }

                    tunUtils.reflectPackets();

                    // Receive packet from socket, and validate that the payload is correct
                    receiveAndValidatePacket(socket);

                    socket.close();

                    return 0;
                }
            };
        }
    }

    private class InputPacketGeneratorIpSecTunnelTestRunnableFactory
            implements IpSecTunnelTestRunnableFactory {
        public IpSecTunnelTestRunnable getIpSecTunnelTestRunnable(
                boolean transportInTunnelMode,
                int spi,
                InetAddress localInner,
                InetAddress remoteInner,
                IpSecTransform inTransportTransform,
                IpSecTransform outTransportTransform,
                UdpEncapsulationSocket encapSocket,
                int innerSocketPort)
                throws Exception {
            return new IpSecTunnelTestRunnable() {
                @Override
                public int run(
                        Network ipsecNetwork,
                        IpSecTunnelInterface tunnelIface,
                        TunUtils tunUtils,
                        IpSecTransform inTunnelTransform,
                        IpSecTransform outTunnelTransform,
                        InetAddress localOuter,
                        InetAddress remoteOuter,
                        int seqNum)
                        throws Exception {
                    // Build a socket and receive traffic
                    JavaUdpSocket socket = new JavaUdpSocket(localInner);
                    ipsecNetwork.bindSocket(socket.mSocket);

                    // For Transport-In-Tunnel mode, apply transform to socket
                    if (transportInTunnelMode) {
                        mISM.applyTransportModeTransform(
                                socket.mSocket, IpSecManager.DIRECTION_IN, outTransportTransform);
                        mISM.applyTransportModeTransform(
                                socket.mSocket, IpSecManager.DIRECTION_OUT, inTransportTransform);
                    }

                    byte[] pkt;
                    if (transportInTunnelMode) {
                        pkt =
                                getTransportInTunnelModePacket(
                                        spi,
                                        spi,
                                        remoteInner,
                                        localInner,
                                        remoteOuter,
                                        localOuter,
                                        socket.getPort(),
                                        encapSocket != null ? encapSocket.getPort() : 0,
                                        seqNum);
                    } else {
                        pkt =
                                getTunnelModePacket(
                                        spi,
                                        remoteInner,
                                        localInner,
                                        remoteOuter,
                                        localOuter,
                                        socket.getPort(),
                                        encapSocket != null ? encapSocket.getPort() : 0,
                                        seqNum);
                    }
                    tunUtils.injectPacket(pkt);

                    // Receive packet from socket, and validate
                    receiveAndValidatePacket(socket);

                    socket.close();

                    return 0;
                }
            };
        }
    }

    private class MigrateIpSecTunnelTestRunnableFactory implements IpSecTunnelTestRunnableFactory {
        private final IpSecTunnelTestRunnableFactory mTestRunnableFactory;
        private final boolean mTestEncapTypeChange;

        MigrateIpSecTunnelTestRunnableFactory(boolean isOutputTest, boolean testEncapTypeChange) {
            if (isOutputTest) {
                mTestRunnableFactory = new OutputIpSecTunnelTestRunnableFactory();
            } else {
                mTestRunnableFactory = new InputPacketGeneratorIpSecTunnelTestRunnableFactory();
            }

            mTestEncapTypeChange = testEncapTypeChange;
        }

        @Override
        public IpSecTunnelTestRunnable getIpSecTunnelTestRunnable(
                boolean transportInTunnelMode,
                int spi,
                InetAddress localInner,
                InetAddress remoteInner,
                IpSecTransform inTransportTransform,
                IpSecTransform outTransportTransform,
                UdpEncapsulationSocket encapSocket,
                int unusedInnerSocketPort) {
            return new IpSecTunnelTestRunnable() {
                @Override
                public int run(
                        Network ipsecNetwork,
                        IpSecTunnelInterface tunnelIface,
                        TunUtils tunUtils,
                        IpSecTransform inTunnelTransform,
                        IpSecTransform outTunnelTransform,
                        InetAddress localOuter,
                        InetAddress remoteOuter,
                        int seqNum)
                        throws Exception {
                    mTestRunnableFactory
                            .getIpSecTunnelTestRunnable(
                                    transportInTunnelMode,
                                    spi,
                                    localInner,
                                    remoteInner,
                                    inTransportTransform,
                                    outTransportTransform,
                                    encapSocket,
                                    unusedInnerSocketPort)
                            .run(
                                    ipsecNetwork,
                                    tunnelIface,
                                    tunUtils,
                                    inTunnelTransform,
                                    outTunnelTransform,
                                    localOuter,
                                    remoteOuter,
                                    seqNum);
                    tunnelIface.setUnderlyingNetwork(sTunWrapperNew.network);

                    final boolean useEncapBeforeMigrate = encapSocket != null;
                    final boolean useEncapAfterMigrate =
                            mTestEncapTypeChange ? !useEncapBeforeMigrate : useEncapBeforeMigrate;

                    // Verify migrating to IPv4 and IPv6 addresses. It ensures that not only
                    // can IPsec tunnel migrate across interfaces, IPsec tunnel can also migrate to
                    // a different address on the same interface.
                    checkMigratedTunnel(
                            localInner,
                            remoteInner,
                            LOCAL_OUTER_4_NEW,
                            REMOTE_OUTER_4_NEW,
                            useEncapAfterMigrate,
                            transportInTunnelMode,
                            sTunWrapperNew.utils,
                            tunnelIface,
                            ipsecNetwork);

                    if (!useEncapAfterMigrate || isIpv6UdpEncapSupported()) {
                        checkMigratedTunnel(
                                localInner,
                                remoteInner,
                                LOCAL_OUTER_6_NEW,
                                REMOTE_OUTER_6_NEW,
                                useEncapAfterMigrate,
                                transportInTunnelMode,
                                sTunWrapperNew.utils,
                                tunnelIface,
                                ipsecNetwork);
                    }

                    return 0;
                }
            };
        }

        private void checkMigratedTunnel(
                InetAddress localInner,
                InetAddress remoteInner,
                InetAddress localOuter,
                InetAddress remoteOuter,
                boolean useEncap,
                boolean transportInTunnelMode,
                TunUtils tunUtils,
                IpSecTunnelInterface tunnelIface,
                Network ipsecNetwork)
                throws Exception {

            // Preselect both SPI and encap port, to be used for both inbound and outbound tunnels.
            // Re-uses the same SPI to ensure that even in cases of symmetric SPIs shared across
            // tunnel and transport mode, packets are encrypted/decrypted properly based on the
            // src/dst.
            int spi = getRandomSpi(localOuter, remoteOuter);

            int innerFamily = localInner instanceof Inet4Address ? AF_INET : AF_INET6;
            int outerFamily = localOuter instanceof Inet4Address ? AF_INET : AF_INET6;
            int expectedPacketSize =
                    getPacketSize(innerFamily, outerFamily, useEncap, transportInTunnelMode);

            // Build transport mode transforms and encapsulation socket for verifying
            // transport-in-tunnel case and encapsulation case.
            try (IpSecManager.SecurityParameterIndex inTransportSpi =
                            mISM.allocateSecurityParameterIndex(localInner, spi);
                    IpSecManager.SecurityParameterIndex outTransportSpi =
                            mISM.allocateSecurityParameterIndex(remoteInner, spi);
                    IpSecTransform inTransportTransform =
                            buildIpSecTransform(sContext, inTransportSpi, null, remoteInner);
                    IpSecTransform outTransportTransform =
                            buildIpSecTransform(sContext, outTransportSpi, null, localInner);
                    UdpEncapsulationSocket encapSocket =
                            useEncap ? openUdpEncapsulationSocket(outerFamily) : null) {

                // Configure tunnel mode Transform parameters
                IpSecTransform.Builder transformBuilder = new IpSecTransform.Builder(sContext);
                transformBuilder.setEncryption(
                        new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY));
                transformBuilder.setAuthentication(
                        new IpSecAlgorithm(
                                IpSecAlgorithm.AUTH_HMAC_SHA256, AUTH_KEY, AUTH_KEY.length * 4));

                if (encapSocket != null) {
                    transformBuilder.setIpv4Encapsulation(encapSocket, encapSocket.getPort());
                }

                // Apply transform and check that traffic is properly encrypted
                try (IpSecManager.SecurityParameterIndex inSpi =
                                mISM.allocateSecurityParameterIndex(localOuter, spi);
                        IpSecManager.SecurityParameterIndex outSpi =
                                mISM.allocateSecurityParameterIndex(remoteOuter, spi);
                        IpSecTransform inTransform =
                                transformBuilder.buildTunnelModeTransform(remoteOuter, inSpi);
                        IpSecTransform outTransform =
                                transformBuilder.buildTunnelModeTransform(localOuter, outSpi)) {
                    mISM.applyTunnelModeTransform(
                            tunnelIface, IpSecManager.DIRECTION_IN, inTransform);
                    mISM.applyTunnelModeTransform(
                            tunnelIface, IpSecManager.DIRECTION_OUT, outTransform);

                    mTestRunnableFactory
                            .getIpSecTunnelTestRunnable(
                                    transportInTunnelMode,
                                    spi,
                                    localInner,
                                    remoteInner,
                                    inTransportTransform,
                                    outTransportTransform,
                                    encapSocket,
                                    0)
                            .run(
                                    ipsecNetwork,
                                    tunnelIface,
                                    tunUtils,
                                    inTransform,
                                    outTransform,
                                    localOuter,
                                    remoteOuter,
                                    1 /* seqNum */);
                }
            }
        }
    }

    private class MigrateTunnelModeIpSecTransformTestRunnableFactory
            implements IpSecTunnelTestRunnableFactory {
        private final IpSecTunnelTestRunnableFactory mTestRunnableFactory;

        MigrateTunnelModeIpSecTransformTestRunnableFactory(boolean isOutputTest) {
            if (isOutputTest) {
                mTestRunnableFactory = new OutputIpSecTunnelTestRunnableFactory();
            } else {
                mTestRunnableFactory = new InputPacketGeneratorIpSecTunnelTestRunnableFactory();
            }
        }

        @Override
        public IpSecTunnelTestRunnable getIpSecTunnelTestRunnable(
                boolean transportInTunnelMode,
                int spi,
                InetAddress localInner,
                InetAddress remoteInner,
                IpSecTransform inTransportTransform,
                IpSecTransform outTransportTransform,
                UdpEncapsulationSocket encapSocket,
                int unusedInnerSocketPort) {
            return new IpSecTunnelTestRunnable() {
                @Override
                public int run(
                        Network ipsecNetwork,
                        IpSecTunnelInterface tunnelIface,
                        TunUtils tunUtils,
                        IpSecTransform inTunnelTransform,
                        IpSecTransform outTunnelTransform,
                        InetAddress localOuter,
                        InetAddress remoteOuter,
                        int seqNum)
                        throws Exception {
                    final IpSecTunnelTestRunnable testRunnable =
                            mTestRunnableFactory.getIpSecTunnelTestRunnable(
                                    transportInTunnelMode,
                                    spi,
                                    localInner,
                                    remoteInner,
                                    inTransportTransform,
                                    outTransportTransform,
                                    encapSocket,
                                    unusedInnerSocketPort);
                    testRunnable.run(
                            ipsecNetwork,
                            tunnelIface,
                            tunUtils,
                            inTunnelTransform,
                            outTunnelTransform,
                            localOuter,
                            remoteOuter,
                            seqNum++);

                    tunnelIface.setUnderlyingNetwork(sTunWrapperNew.network);

                    final boolean useEncap = encapSocket != null;
                    if (useEncap) {
                        sTunWrapperNew.network.bindSocket(encapSocket.getFileDescriptor());
                    }

                    // Updating UDP encapsulation socket is not supported. Thus this runnable will
                    // only cover 1) migration from non-encap to non-encap and 2) migration from
                    // encap to encap with the same family
                    if (!useEncap || localOuter instanceof Inet4Address) {
                        checkMigrateTunnelModeTransform(
                                testRunnable,
                                inTunnelTransform,
                                outTunnelTransform,
                                tunnelIface,
                                ipsecNetwork,
                                sTunWrapperNew.utils,
                                LOCAL_OUTER_4_NEW,
                                REMOTE_OUTER_4_NEW,
                                seqNum++);
                    }
                    if (!useEncap || localOuter instanceof Inet6Address) {
                        checkMigrateTunnelModeTransform(
                                testRunnable,
                                inTunnelTransform,
                                outTunnelTransform,
                                tunnelIface,
                                ipsecNetwork,
                                sTunWrapperNew.utils,
                                LOCAL_OUTER_6_NEW,
                                REMOTE_OUTER_6_NEW,
                                seqNum++);
                    }

                    // Unused return value for MigrateTunnelModeIpSecTransformTest
                    return 0;
                }
            };
        }

        private void checkMigrateTunnelModeTransform(
                IpSecTunnelTestRunnable testRunnable,
                IpSecTransform inTunnelTransform,
                IpSecTransform outTunnelTransform,
                IpSecTunnelInterface tunnelIface,
                Network ipsecNetwork,
                TunUtils tunUtils,
                InetAddress newLocalOuter,
                InetAddress newRemoteOuter,
                int seqNum)
                throws Exception {
            mISM.startTunnelModeTransformMigration(
                    inTunnelTransform, newRemoteOuter, newLocalOuter);
            mISM.startTunnelModeTransformMigration(
                    outTunnelTransform, newLocalOuter, newRemoteOuter);

            mISM.applyTunnelModeTransform(
                    tunnelIface, IpSecManager.DIRECTION_IN, inTunnelTransform);
            mISM.applyTunnelModeTransform(
                    tunnelIface, IpSecManager.DIRECTION_OUT, outTunnelTransform);

            testRunnable.run(
                    ipsecNetwork,
                    tunnelIface,
                    tunUtils,
                    inTunnelTransform,
                    outTunnelTransform,
                    newLocalOuter,
                    newRemoteOuter,
                    seqNum);
        }
    }

    private void checkTunnelOutput(
            int innerFamily, int outerFamily, boolean useEncap, boolean transportInTunnelMode)
            throws Exception {
        checkTunnel(
                innerFamily,
                outerFamily,
                useEncap,
                transportInTunnelMode,
                new OutputIpSecTunnelTestRunnableFactory());
    }

    private void checkTunnelInput(
            int innerFamily, int outerFamily, boolean useEncap, boolean transportInTunnelMode)
            throws Exception {
        checkTunnel(
                innerFamily,
                outerFamily,
                useEncap,
                transportInTunnelMode,
                new InputPacketGeneratorIpSecTunnelTestRunnableFactory());
    }

    private void checkMigrateTunnelOutput(
            int innerFamily,
            int outerFamily,
            boolean useEncap,
            boolean transportInTunnelMode,
            boolean isEncapTypeChanged)
            throws Exception {
        checkTunnel(
                innerFamily,
                outerFamily,
                useEncap,
                transportInTunnelMode,
                new MigrateIpSecTunnelTestRunnableFactory(true, isEncapTypeChanged));
    }

    private void checkMigrateTunnelInput(
            int innerFamily,
            int outerFamily,
            boolean useEncap,
            boolean transportInTunnelMode,
            boolean isEncapTypeChanged)
            throws Exception {
        checkTunnel(
                innerFamily,
                outerFamily,
                useEncap,
                transportInTunnelMode,
                new MigrateIpSecTunnelTestRunnableFactory(false, isEncapTypeChanged));
    }

    private void checkMigrateTunnelModeTransformOutput(
            int innerFamily, int outerFamily, boolean useEncap, boolean transportInTunnelMode)
            throws Exception {
        checkTunnel(
                innerFamily,
                outerFamily,
                useEncap,
                transportInTunnelMode,
                new MigrateTunnelModeIpSecTransformTestRunnableFactory(true /* isOutputTest */));
    }

    private void checkMigrateTunnelModeTransformInput(
            int innerFamily, int outerFamily, boolean useEncap, boolean transportInTunnelMode)
            throws Exception {
        checkTunnel(
                innerFamily,
                outerFamily,
                useEncap,
                transportInTunnelMode,
                new MigrateTunnelModeIpSecTransformTestRunnableFactory(false /* isOutputTest */));
    }

    /**
     * Validates that the kernel can talk to itself.
     *
     * <p>This test takes an outbound IPsec packet, reflects it (by flipping IP src/dst), and
     * injects it back into the TUN. This test then verifies that a packet with the correct payload
     * is found on the specified socket/port.
     */
    public void checkTunnelReflected(
            int innerFamily, int outerFamily, boolean useEncap, boolean transportInTunnelMode)
            throws Exception {
        InetAddress localInner = innerFamily == AF_INET ? LOCAL_INNER_4 : LOCAL_INNER_6;
        InetAddress remoteInner = innerFamily == AF_INET ? REMOTE_INNER_4 : REMOTE_INNER_6;

        InetAddress localOuter = outerFamily == AF_INET ? LOCAL_OUTER_4 : LOCAL_OUTER_6;
        InetAddress remoteOuter = outerFamily == AF_INET ? REMOTE_OUTER_4 : REMOTE_OUTER_6;

        // Preselect both SPI and encap port, to be used for both inbound and outbound tunnels.
        int spi = getRandomSpi(localOuter, remoteOuter);
        int expectedPacketSize =
                getPacketSize(innerFamily, outerFamily, useEncap, transportInTunnelMode);

        try (IpSecManager.SecurityParameterIndex inTransportSpi =
                        mISM.allocateSecurityParameterIndex(localInner, spi);
                IpSecManager.SecurityParameterIndex outTransportSpi =
                        mISM.allocateSecurityParameterIndex(remoteInner, spi);
                IpSecTransform inTransportTransform =
                        buildIpSecTransform(sContext, inTransportSpi, null, remoteInner);
                IpSecTransform outTransportTransform =
                        buildIpSecTransform(sContext, outTransportSpi, null, localInner);
                UdpEncapsulationSocket encapSocket =
                        useEncap ? openUdpEncapsulationSocket(outerFamily) : null) {

            // Run output direction tests
            IpSecTunnelTestRunnable outputIpSecTunnelTestRunnable =
                    new OutputIpSecTunnelTestRunnableFactory()
                            .getIpSecTunnelTestRunnable(
                                    transportInTunnelMode,
                                    spi,
                                    localInner,
                                    remoteInner,
                                    inTransportTransform,
                                    outTransportTransform,
                                    encapSocket,
                                    0);
            int innerSocketPort =
                    buildTunnelNetworkAndRunTests(
                            localInner,
                            remoteInner,
                            localOuter,
                            remoteOuter,
                            spi,
                            encapSocket,
                            outputIpSecTunnelTestRunnable);

            // Input direction tests, with matching inner socket ports.
            IpSecTunnelTestRunnable inputIpSecTunnelTestRunnable =
                    new InputReflectedIpSecTunnelTestRunnableFactory()
                            .getIpSecTunnelTestRunnable(
                                    transportInTunnelMode,
                                    spi,
                                    remoteInner,
                                    localInner,
                                    inTransportTransform,
                                    outTransportTransform,
                                    encapSocket,
                                    innerSocketPort);
            buildTunnelNetworkAndRunTests(
                    remoteInner,
                    localInner,
                    localOuter,
                    remoteOuter,
                    spi,
                    encapSocket,
                    inputIpSecTunnelTestRunnable);
        }
    }

    public void checkTunnel(
            int innerFamily,
            int outerFamily,
            boolean useEncap,
            boolean transportInTunnelMode,
            IpSecTunnelTestRunnableFactory factory)
            throws Exception {

        InetAddress localInner = innerFamily == AF_INET ? LOCAL_INNER_4 : LOCAL_INNER_6;
        InetAddress remoteInner = innerFamily == AF_INET ? REMOTE_INNER_4 : REMOTE_INNER_6;

        InetAddress localOuter = outerFamily == AF_INET ? LOCAL_OUTER_4 : LOCAL_OUTER_6;
        InetAddress remoteOuter = outerFamily == AF_INET ? REMOTE_OUTER_4 : REMOTE_OUTER_6;

        // Preselect both SPI and encap port, to be used for both inbound and outbound tunnels.
        // Re-uses the same SPI to ensure that even in cases of symmetric SPIs shared across tunnel
        // and transport mode, packets are encrypted/decrypted properly based on the src/dst.
        int spi = getRandomSpi(localOuter, remoteOuter);
        int expectedPacketSize =
                getPacketSize(innerFamily, outerFamily, useEncap, transportInTunnelMode);

        try (IpSecManager.SecurityParameterIndex inTransportSpi =
                        mISM.allocateSecurityParameterIndex(localInner, spi);
                IpSecManager.SecurityParameterIndex outTransportSpi =
                        mISM.allocateSecurityParameterIndex(remoteInner, spi);
                IpSecTransform inTransportTransform =
                        buildIpSecTransform(sContext, inTransportSpi, null, remoteInner);
                IpSecTransform outTransportTransform =
                        buildIpSecTransform(sContext, outTransportSpi, null, localInner);
                UdpEncapsulationSocket encapSocket =
                        useEncap ? openUdpEncapsulationSocket(outerFamily) : null) {

            buildTunnelNetworkAndRunTests(
                    localInner,
                    remoteInner,
                    localOuter,
                    remoteOuter,
                    spi,
                    encapSocket,
                    factory.getIpSecTunnelTestRunnable(
                            transportInTunnelMode,
                            spi,
                            localInner,
                            remoteInner,
                            inTransportTransform,
                            outTransportTransform,
                            encapSocket,
                            0));
        }
    }

    private int buildTunnelNetworkAndRunTests(
            InetAddress localInner,
            InetAddress remoteInner,
            InetAddress localOuter,
            InetAddress remoteOuter,
            int spi,
            UdpEncapsulationSocket encapSocket,
            IpSecTunnelTestRunnable test)
            throws Exception {
        int innerPrefixLen = localInner instanceof Inet6Address ? IP6_PREFIX_LEN : IP4_PREFIX_LEN;
        TestNetworkCallback testNetworkCb = null;
        int innerSocketPort;

        try (IpSecManager.SecurityParameterIndex inSpi =
                        mISM.allocateSecurityParameterIndex(localOuter, spi);
                IpSecManager.SecurityParameterIndex outSpi =
                        mISM.allocateSecurityParameterIndex(remoteOuter, spi);
                IpSecManager.IpSecTunnelInterface tunnelIface =
                        mISM.createIpSecTunnelInterface(
                                localOuter, remoteOuter, sTunWrapper.network)) {
            // Build the test network
            tunnelIface.addAddress(localInner, innerPrefixLen);
            testNetworkCb = mCtsNetUtils.setupAndGetTestNetwork(tunnelIface.getInterfaceName());
            testNetworkCb.waitForAvailable();
            Network testNetwork = testNetworkCb.currentNetwork;

            // Check interface was created
            assertNotNull(NetworkInterface.getByName(tunnelIface.getInterfaceName()));

            // Verify address was added
            final NetworkInterface netIface = NetworkInterface.getByInetAddress(localInner);
            assertNotNull(netIface);
            assertEquals(tunnelIface.getInterfaceName(), netIface.getDisplayName());

            // Configure Transform parameters
            IpSecTransform.Builder transformBuilder = new IpSecTransform.Builder(sContext);
            transformBuilder.setEncryption(
                    new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY));
            transformBuilder.setAuthentication(
                    new IpSecAlgorithm(
                            IpSecAlgorithm.AUTH_HMAC_SHA256, AUTH_KEY, AUTH_KEY.length * 4));

            if (encapSocket != null) {
                transformBuilder.setIpv4Encapsulation(encapSocket, encapSocket.getPort());
                sTunWrapper.network.bindSocket(encapSocket.getFileDescriptor());
            }

            // Apply transform and check that traffic is properly encrypted
            try (IpSecTransform inTransform =
                            transformBuilder.buildTunnelModeTransform(remoteOuter, inSpi);
                    IpSecTransform outTransform =
                            transformBuilder.buildTunnelModeTransform(localOuter, outSpi)) {
                mISM.applyTunnelModeTransform(tunnelIface, IpSecManager.DIRECTION_IN, inTransform);
                mISM.applyTunnelModeTransform(
                        tunnelIface, IpSecManager.DIRECTION_OUT, outTransform);

                innerSocketPort =
                        test.run(
                                testNetwork,
                                tunnelIface,
                                sTunWrapper.utils,
                                inTransform,
                                outTransform,
                                localOuter,
                                remoteOuter,
                                1 /* seqNum */);
            }

            // Teardown the test network
            sTNM.teardownTestNetwork(testNetwork);

            // Remove addresses and check that interface is still present, but fails lookup-by-addr
            tunnelIface.removeAddress(localInner, innerPrefixLen);
            assertNotNull(NetworkInterface.getByName(tunnelIface.getInterfaceName()));
            assertNull(NetworkInterface.getByInetAddress(localInner));

            // Check interface was cleaned up
            tunnelIface.close();
            assertNull(NetworkInterface.getByName(tunnelIface.getInterfaceName()));
        } finally {
            if (testNetworkCb != null) {
                sCM.unregisterNetworkCallback(testNetworkCb);
            }
        }

        return innerSocketPort;
    }

    private static void receiveAndValidatePacket(JavaUdpSocket socket) throws Exception {
        byte[] socketResponseBytes = socket.receive();
        assertArrayEquals(TEST_DATA, socketResponseBytes);
    }

    private int getRandomSpi(InetAddress localOuter, InetAddress remoteOuter) throws Exception {
        // Try to allocate both in and out SPIs using the same requested SPI value.
        try (IpSecManager.SecurityParameterIndex inSpi =
                        mISM.allocateSecurityParameterIndex(localOuter);
                IpSecManager.SecurityParameterIndex outSpi =
                        mISM.allocateSecurityParameterIndex(remoteOuter, inSpi.getSpi()); ) {
            return inSpi.getSpi();
        }
    }

    private EspHeader buildTransportModeEspPacket(
            int spi, int seqNum, InetAddress src, InetAddress dst, Payload payload)
            throws Exception {
        IpHeader preEspIpHeader = getIpHeader(payload.getProtocolId(), src, dst, payload);

        return new EspHeader(
                payload.getProtocolId(),
                spi,
                seqNum,
                CRYPT_KEY, // Same key for auth and crypt
                payload.getPacketBytes(preEspIpHeader));
    }

    private EspHeader buildTunnelModeEspPacket(
            int spi,
            InetAddress srcInner,
            InetAddress dstInner,
            InetAddress srcOuter,
            InetAddress dstOuter,
            int port,
            int encapPort,
            int seqNum,
            Payload payload)
            throws Exception {
        IpHeader innerIp = getIpHeader(payload.getProtocolId(), srcInner, dstInner, payload);
        return new EspHeader(
                innerIp.getProtocolId(),
                spi,
                seqNum, // sequence number
                CRYPT_KEY, // Same key for auth and crypt
                innerIp.getPacketBytes());
    }

    private IpHeader maybeEncapPacket(
            InetAddress src, InetAddress dst, int encapPort, EspHeader espPayload)
            throws Exception {

        Payload payload = espPayload;
        if (encapPort != 0) {
            payload = new UdpHeader(encapPort, encapPort, espPayload);
        }

        return getIpHeader(payload.getProtocolId(), src, dst, payload);
    }

    private byte[] getTunnelModePacket(
            int spi,
            InetAddress srcInner,
            InetAddress dstInner,
            InetAddress srcOuter,
            InetAddress dstOuter,
            int port,
            int encapPort,
            int seqNum)
            throws Exception {
        UdpHeader udp = new UdpHeader(port, port, new BytePayload(TEST_DATA));

        EspHeader espPayload =
                buildTunnelModeEspPacket(
                        spi, srcInner, dstInner, srcOuter, dstOuter, port, encapPort, seqNum, udp);
        return maybeEncapPacket(srcOuter, dstOuter, encapPort, espPayload).getPacketBytes();
    }

    private byte[] getTransportInTunnelModePacket(
            int spiInner,
            int spiOuter,
            InetAddress srcInner,
            InetAddress dstInner,
            InetAddress srcOuter,
            InetAddress dstOuter,
            int port,
            int encapPort,
            int seqNum)
            throws Exception {
        UdpHeader udp = new UdpHeader(port, port, new BytePayload(TEST_DATA));

        EspHeader espPayload =
                buildTransportModeEspPacket(spiInner, seqNum, srcInner, dstInner, udp);
        espPayload =
                buildTunnelModeEspPacket(
                        spiOuter,
                        srcInner,
                        dstInner,
                        srcOuter,
                        dstOuter,
                        port,
                        encapPort,
                        seqNum,
                        espPayload);
        return maybeEncapPacket(srcOuter, dstOuter, encapPort, espPayload).getPacketBytes();
    }

    private void doTestMigrateTunnel(
            int innerFamily,
            int outerFamily,
            boolean useEncap,
            boolean transportInTunnelMode,
            boolean testEncapTypeChange)
            throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkMigrateTunnelOutput(
                innerFamily, outerFamily, useEncap, transportInTunnelMode, testEncapTypeChange);
        checkMigrateTunnelInput(
                innerFamily, outerFamily, useEncap, transportInTunnelMode, testEncapTypeChange);
    }

    private void doTestMigrateTunnel(
            int innerFamily, int outerFamily, boolean useEncap, boolean transportInTunnelMode)
            throws Exception {
        doTestMigrateTunnel(
                innerFamily,
                outerFamily,
                useEncap,
                transportInTunnelMode,
                false /* testEncapTypeChange */);
    }

    private void doTestMigrateTunnelWithEncapTypeChange(
            int innerFamily, int outerFamily, boolean useEncap, boolean transportInTunnelMode)
            throws Exception {
        doTestMigrateTunnel(
                innerFamily,
                outerFamily,
                useEncap,
                transportInTunnelMode,
                true /* testEncapTypeChange */);
    }

    private void doTestMigrateTunnelModeTransform(
            int innerFamily, int outerFamily, boolean useEncap, boolean transportInTunnelMode)
            throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        assumeTrue(mCtsNetUtils.hasIpsecTunnelMigrateFeature());
        checkMigrateTunnelModeTransformOutput(
                innerFamily, outerFamily, useEncap, transportInTunnelMode);
        checkMigrateTunnelModeTransformInput(
                innerFamily, outerFamily, useEncap, transportInTunnelMode);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testHasIpSecTunnelMigrateFeature() throws Exception {
        // FEATURE_IPSEC_TUNNEL_MIGRATION is required when VSR API is U/U+
        if (getVsrApiLevel() > Build.VERSION_CODES.TIRAMISU) {
            assertTrue(mCtsNetUtils.hasIpsecTunnelMigrateFeature());
        }
    }

    // Transport-in-Tunnel mode tests
    @Test
    public void testTransportInTunnelModeV4InV4() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelOutput(AF_INET, AF_INET, false, true);
        checkTunnelInput(AF_INET, AF_INET, false, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTransportInTunnelModeV4InV4() throws Exception {
        doTestMigrateTunnel(AF_INET, AF_INET, false, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTransportInTunnelModeV4InV4_EncapTypeChange() throws Exception {
        doTestMigrateTunnelWithEncapTypeChange(AF_INET, AF_INET, false, true);
    }

    @Test
    public void testTransportInTunnelModeV4InV4Reflected() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelReflected(AF_INET, AF_INET, false, true);
    }

    @Test
    public void testTransportInTunnelModeV4InV4UdpEncap() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelOutput(AF_INET, AF_INET, true, true);
        checkTunnelInput(AF_INET, AF_INET, true, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTransportInTunnelModeV4InV4UdpEncap() throws Exception {
        doTestMigrateTunnel(AF_INET, AF_INET, true, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTransportInTunnelModeV4InV4UdpEncap_EncapTypeChange() throws Exception {
        doTestMigrateTunnelWithEncapTypeChange(AF_INET, AF_INET, true, true);
    }

    @Test
    public void testTransportInTunnelModeV4InV4UdpEncapReflected() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelReflected(AF_INET, AF_INET, false, true);
    }

    @Test
    public void testTransportInTunnelModeV4InV6() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelOutput(AF_INET, AF_INET6, false, true);
        checkTunnelInput(AF_INET, AF_INET6, false, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTransportInTunnelModeV4InV6() throws Exception {
        doTestMigrateTunnel(AF_INET, AF_INET6, false, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTransportInTunnelModeV4InV6_EncapTypeChange() throws Exception {
        doTestMigrateTunnelWithEncapTypeChange(AF_INET, AF_INET6, false, true);
    }

    @Test
    public void testTransportInTunnelModeV4InV6Reflected() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelReflected(AF_INET, AF_INET, false, true);
    }

    @Test
    public void testTransportInTunnelModeV6InV4() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelOutput(AF_INET6, AF_INET, false, true);
        checkTunnelInput(AF_INET6, AF_INET, false, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTransportInTunnelModeV6InV4() throws Exception {
        doTestMigrateTunnel(AF_INET6, AF_INET, false, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTransportInTunnelModeV6InV4_EncapTypeChange() throws Exception {
        doTestMigrateTunnelWithEncapTypeChange(AF_INET6, AF_INET, false, true);
    }

    @Test
    public void testTransportInTunnelModeV6InV4Reflected() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelReflected(AF_INET, AF_INET, false, true);
    }

    @Test
    public void testTransportInTunnelModeV6InV4UdpEncap() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelOutput(AF_INET6, AF_INET, true, true);
        checkTunnelInput(AF_INET6, AF_INET, true, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTransportInTunnelModeV6InV4UdpEncap() throws Exception {
        doTestMigrateTunnel(AF_INET6, AF_INET, true, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTransportInTunnelModeV6InV4UdpEncap_EncapTypeChange() throws Exception {
        doTestMigrateTunnelWithEncapTypeChange(AF_INET6, AF_INET, true, true);
    }

    @Test
    public void testTransportInTunnelModeV6InV4UdpEncapReflected() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelReflected(AF_INET, AF_INET, false, true);
    }

    @Test
    public void testTransportInTunnelModeV6InV6() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelOutput(AF_INET, AF_INET6, false, true);
        checkTunnelInput(AF_INET, AF_INET6, false, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTransportInTunnelModeV6InV6() throws Exception {
        doTestMigrateTunnel(AF_INET, AF_INET6, false, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTransportInTunnelModeV6InV6_EncapTypeChange() throws Exception {
        doTestMigrateTunnelWithEncapTypeChange(AF_INET, AF_INET6, false, true);
    }

    @Test
    public void testTransportInTunnelModeV6InV6Reflected() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelReflected(AF_INET, AF_INET, false, true);
    }

    // Tunnel mode tests
    @Test
    public void testTunnelV4InV4() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelOutput(AF_INET, AF_INET, false, false);
        checkTunnelInput(AF_INET, AF_INET, false, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTunnelV4InV4() throws Exception {
        doTestMigrateTunnel(AF_INET, AF_INET, false, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTunnelV4InV4_EncapTypeChange() throws Exception {
        doTestMigrateTunnelWithEncapTypeChange(AF_INET, AF_INET, false, false);
    }

    @Test
    public void testTunnelV4InV4Reflected() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelReflected(AF_INET, AF_INET, false, false);
    }

    @Test
    public void testTunnelV4InV4UdpEncap() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelOutput(AF_INET, AF_INET, true, false);
        checkTunnelInput(AF_INET, AF_INET, true, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTunnelV4InV4UdpEncap() throws Exception {
        doTestMigrateTunnel(AF_INET, AF_INET, true, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTunnelV4InV4UdpEncap_EncapTypeChange() throws Exception {
        doTestMigrateTunnelWithEncapTypeChange(AF_INET, AF_INET, true, false);
    }

    @Test
    public void testTunnelV4InV4UdpEncapReflected() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelReflected(AF_INET, AF_INET, true, false);
    }

    @Test
    public void testTunnelV4InV6() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelOutput(AF_INET, AF_INET6, false, false);
        checkTunnelInput(AF_INET, AF_INET6, false, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTunnelV4InV6() throws Exception {
        doTestMigrateTunnel(AF_INET, AF_INET6, false, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTunnelV4InV6_EncapTypeChange() throws Exception {
        doTestMigrateTunnelWithEncapTypeChange(AF_INET, AF_INET6, false, false);
    }

    @Test
    public void testTunnelV4InV6Reflected() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelReflected(AF_INET, AF_INET6, false, false);
    }

    @Test
    public void testTunnelV6InV4() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelOutput(AF_INET6, AF_INET, false, false);
        checkTunnelInput(AF_INET6, AF_INET, false, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTunnelV6InV4() throws Exception {
        doTestMigrateTunnel(AF_INET6, AF_INET, false, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTunnelV6InV4_EncapTypeChange() throws Exception {
        doTestMigrateTunnelWithEncapTypeChange(AF_INET6, AF_INET, false, false);
    }

    @Test
    public void testTunnelV6InV4Reflected() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelReflected(AF_INET6, AF_INET, false, false);
    }

    @Test
    public void testTunnelV6InV4UdpEncap() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelOutput(AF_INET6, AF_INET, true, false);
        checkTunnelInput(AF_INET6, AF_INET, true, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTunnelV6InV4UdpEncap() throws Exception {
        doTestMigrateTunnel(AF_INET6, AF_INET, true, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTunnelV6InV4UdpEncap_EncapTypeChange() throws Exception {
        doTestMigrateTunnelWithEncapTypeChange(AF_INET6, AF_INET, true, false);
    }

    @Test
    public void testTunnelV6InV4UdpEncapReflected() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelReflected(AF_INET6, AF_INET, true, false);
    }

    @Test
    public void testTunnelV6InV6() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelOutput(AF_INET6, AF_INET6, false, false);
        checkTunnelInput(AF_INET6, AF_INET6, false, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTunnelV6InV6() throws Exception {
        doTestMigrateTunnel(AF_INET6, AF_INET6, false, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testMigrateTunnelV6InV6_EncapTypeChange() throws Exception {
        doTestMigrateTunnelWithEncapTypeChange(AF_INET6, AF_INET6, false, false);
    }

    @Test
    public void testTunnelV6InV6Reflected() throws Exception {
        assumeTrue(mCtsNetUtils.hasIpsecTunnelsFeature());
        checkTunnelReflected(AF_INET6, AF_INET6, false, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTransportInTunnelModeV4InV4() throws Exception {
        doTestMigrateTunnelModeTransform(AF_INET, AF_INET, false, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTransportInTunnelModeV6InV4() throws Exception {
        doTestMigrateTunnelModeTransform(AF_INET6, AF_INET, false, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTransportInTunnelModeV4InV6() throws Exception {
        doTestMigrateTunnelModeTransform(AF_INET, AF_INET6, false, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTransportInTunnelModeV6InV6() throws Exception {
        doTestMigrateTunnelModeTransform(AF_INET, AF_INET6, false, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTransportInTunnelModeV4InV4UdpEncap() throws Exception {
        doTestMigrateTunnelModeTransform(AF_INET, AF_INET, true, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTransportInTunnelModeV6InV4UdpEncap() throws Exception {
        doTestMigrateTunnelModeTransform(AF_INET6, AF_INET, true, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTransportInTunnelModeV4InV6UdpEncap() throws Exception {
        assumeExperimentalIpv6UdpEncapSupported();
        doTestMigrateTunnelModeTransform(AF_INET, AF_INET6, true, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTransportInTunnelModeV6InV6UdpEncap() throws Exception {
        assumeExperimentalIpv6UdpEncapSupported();
        doTestMigrateTunnelModeTransform(AF_INET6, AF_INET6, true, true);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTunnelV4InV4() throws Exception {
        doTestMigrateTunnelModeTransform(AF_INET, AF_INET, false, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTunnelV6InV4() throws Exception {
        doTestMigrateTunnelModeTransform(AF_INET6, AF_INET, false, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTunnelV4InV6() throws Exception {
        doTestMigrateTunnelModeTransform(AF_INET, AF_INET6, false, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTunnelV6InV6() throws Exception {
        doTestMigrateTunnelModeTransform(AF_INET6, AF_INET6, false, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTunnelV4InV4UdpEncap() throws Exception {
        doTestMigrateTunnelModeTransform(AF_INET, AF_INET, true, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTunnelV6InV4UdpEncap() throws Exception {
        doTestMigrateTunnelModeTransform(AF_INET6, AF_INET, true, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTunnelV4InV6UdpEncap() throws Exception {
        assumeExperimentalIpv6UdpEncapSupported();
        doTestMigrateTunnelModeTransform(AF_INET, AF_INET6, true, false);
    }

    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testMigrateTransformTunnelV6InV6UdpEncap() throws Exception {
        assumeExperimentalIpv6UdpEncapSupported();
        doTestMigrateTunnelModeTransform(AF_INET6, AF_INET6, true, false);
    }
}
