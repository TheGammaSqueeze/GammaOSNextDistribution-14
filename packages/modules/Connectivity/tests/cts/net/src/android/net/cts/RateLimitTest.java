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

package android.net.cts;

import static android.Manifest.permission.MANAGE_TEST_NETWORKS;
import static android.system.OsConstants.IPPROTO_IP;
import static android.system.OsConstants.IPPROTO_UDP;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.android.net.module.util.NetworkStackConstants.ETHER_MTU;
import static com.android.net.module.util.NetworkStackConstants.IPV4_ADDR_ANY;
import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;
import static com.android.testutils.TestPermissionUtil.runAsShell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.icu.text.MessageFormat;
import android.net.ConnectivityManager;
import android.net.ConnectivitySettingsManager;
import android.net.InetAddresses;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.RouteInfo;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.net.TestNetworkSpecifier;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.system.Os;
import android.util.Log;

import com.android.compatibility.common.util.SystemUtil;
import com.android.net.module.util.PacketBuilder;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.TestableNetworkAgent;
import com.android.testutils.TestableNetworkCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;

@AppModeFull(reason = "Instant apps cannot access /dev/tun, so createTunInterface fails")
@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
public class RateLimitTest {
    // cannot be final as it gets initialized inside ensureKernelConfigLoaded().
    private static HashSet<String> sKernelConfig;

    private static final String TAG = "RateLimitTest";
    private static final LinkAddress LOCAL_IP4_ADDR = new LinkAddress("10.0.0.1/8");
    private static final InetAddress REMOTE_IP4_ADDR = InetAddresses.parseNumericAddress("8.8.8.8");
    private static final short TEST_UDP_PORT = 1234;
    private static final byte TOS = 0;
    private static final short ID = 27149;
    private static final short DONT_FRAG_FLAG_MASK = (short) 0x4000; // flags=DF, offset=0
    private static final byte TIME_TO_LIVE = 64;
    private static final byte[] PAYLOAD = new byte[1472];

    private Handler mHandler;
    private Context mContext;
    private TestNetworkManager mNetworkManager;
    private TestNetworkInterface mTunInterface;
    private ConnectivityManager mCm;
    private TestNetworkSpecifier mNetworkSpecifier;
    private NetworkCapabilities mNetworkCapabilities;
    private TestableNetworkCallback mNetworkCallback;
    private LinkProperties mLinkProperties;
    private TestableNetworkAgent mNetworkAgent;
    private Network mNetwork;
    private DatagramSocket mSocket;

    // Note: exceptions thrown in @BeforeClass or @ClassRule methods are not reported correctly.
    // This function is called from setUp and loads the kernel config options the first time it is
    // invoked. This ensures proper error reporting.
    private static synchronized void ensureKernelConfigLoaded() {
        if (sKernelConfig != null) return;
        final String result = SystemUtil.runShellCommandOrThrow("gzip -cd /proc/config.gz");
        sKernelConfig = Arrays.stream(result.split("\\R")).collect(
                Collectors.toCollection(HashSet::new));

        // make sure that if for some reason /proc/config.gz returns an empty string, this test
        // does not silently fail.
        assertNotEquals("gzip -cd /proc/config.gz returned an empty string", 0, result.length());
    }

    private static void assumeKernelSupport() {
        assumeTrue(sKernelConfig.contains("CONFIG_NET_CLS_MATCHALL=y"));
        assumeTrue(sKernelConfig.contains("CONFIG_NET_ACT_POLICE=y"));
        assumeTrue(sKernelConfig.contains("CONFIG_NET_ACT_BPF=y"));
    }

    @Before
    public void setUp() throws IOException {
        ensureKernelConfigLoaded();

        mHandler = new Handler(Looper.getMainLooper());

        runAsShell(MANAGE_TEST_NETWORKS, () -> {
            mContext = getContext();

            mNetworkManager = mContext.getSystemService(TestNetworkManager.class);
            mTunInterface = mNetworkManager.createTunInterface(Arrays.asList(LOCAL_IP4_ADDR));
        });

        mCm = mContext.getSystemService(ConnectivityManager.class);
        mNetworkSpecifier = new TestNetworkSpecifier(mTunInterface.getInterfaceName());
        mNetworkCapabilities = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                .addTransportType(NetworkCapabilities.TRANSPORT_TEST)
                .setNetworkSpecifier(mNetworkSpecifier).build();
        mNetworkCallback = new TestableNetworkCallback();

        mCm.requestNetwork(
                new NetworkRequest.Builder()
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                        .addTransportType(NetworkCapabilities.TRANSPORT_TEST)
                        .setNetworkSpecifier(mNetworkSpecifier)
                        .build(),
                mNetworkCallback);

        mLinkProperties = new LinkProperties();
        mLinkProperties.addLinkAddress(LOCAL_IP4_ADDR);
        mLinkProperties.setInterfaceName(mTunInterface.getInterfaceName());
        mLinkProperties.addRoute(
                new RouteInfo(new IpPrefix(IPV4_ADDR_ANY, 0), null,
                        mTunInterface.getInterfaceName()));


        runAsShell(MANAGE_TEST_NETWORKS, () -> {
            mNetworkAgent = new TestableNetworkAgent(mContext, mHandler.getLooper(),
                    mNetworkCapabilities, mLinkProperties,
                    new NetworkAgentConfig.Builder().setExplicitlySelected(
                            true).setUnvalidatedConnectivityAcceptable(true).build());

            mNetworkAgent.register();
            mNetworkAgent.markConnected();
        });

        mNetwork = mNetworkAgent.getNetwork();
        mNetworkCallback.expectAvailableThenValidatedCallbacks(mNetwork, 5_000);
        mSocket = new DatagramSocket(TEST_UDP_PORT);
        mSocket.setSoTimeout(1_000);
        mNetwork.bindSocket(mSocket);
    }

    @After
    public void tearDown() throws IOException {
        if (mContext != null) {
            // whatever happens, don't leave the device in rate limited state.
            ConnectivitySettingsManager.setIngressRateLimitInBytesPerSecond(mContext, -1);
        }
        if (mSocket != null) mSocket.close();
        if (mNetworkAgent != null) mNetworkAgent.unregister();
        if (mTunInterface != null) mTunInterface.getFileDescriptor().close();
        if (mCm != null) mCm.unregisterNetworkCallback(mNetworkCallback);
    }

    private void assertGreaterThan(final String msg, long lhs, long rhs) {
        assertTrue(msg + " -- Failed comparison: " + lhs + " > " + rhs, lhs > rhs);
    }

    private void assertLessThan(final String msg, long lhs, long rhs) {
        assertTrue(msg + " -- Failed comparison: " + lhs + " < " + rhs, lhs < rhs);
    }

    private static void sendPacketsToTunInterfaceForDuration(final TestNetworkInterface iface,
            final Duration duration) throws Exception {
        final ByteBuffer buffer = PacketBuilder.allocate(false, IPPROTO_IP, IPPROTO_UDP,
                PAYLOAD.length);
        final PacketBuilder builder = new PacketBuilder(buffer);
        builder.writeIpv4Header(TOS, ID, DONT_FRAG_FLAG_MASK, TIME_TO_LIVE,
                (byte) IPPROTO_UDP, (Inet4Address) REMOTE_IP4_ADDR,
                (Inet4Address) LOCAL_IP4_ADDR.getAddress());
        builder.writeUdpHeader((short) TEST_UDP_PORT, (short) TEST_UDP_PORT);
        buffer.put(PAYLOAD);
        builder.finalizePacket();

        // write packets to the tun fd as fast as possible for duration.
        long endMillis = SystemClock.elapsedRealtime() + duration.toMillis();
        while (SystemClock.elapsedRealtime() < endMillis) {
            Os.write(iface.getFileDescriptor().getFileDescriptor(), buffer.array(), 0,
                    buffer.limit());
        }
    }

    private static class RateMeasurementSocketReader extends Thread {
        private volatile boolean mIsRunning = false;
        private DatagramSocket mSocket;
        private long mStartMillis = 0;
        private long mStopMillis = 0;
        private long mBytesReceived = 0;

        RateMeasurementSocketReader(DatagramSocket socket) throws Exception {
            mSocket = socket;
        }

        public void startTest() {
            mIsRunning = true;
            start();
        }

        public long stopAndGetResult() throws Exception {
            mIsRunning = false;
            join();

            final long durationMillis = mStopMillis - mStartMillis;
            return (long) ((double) mBytesReceived / (durationMillis / 1000.0));
        }

        @Override
        public void run() {
            // Since the actual data is not used, the buffer can just be recycled.
            final byte[] recvBuf = new byte[ETHER_MTU];
            final DatagramPacket receivedPacket = new DatagramPacket(recvBuf, recvBuf.length);
            while (mIsRunning) {
                try {
                    mSocket.receive(receivedPacket);

                    // don't start the test until after the first packet is received and increment
                    // mBytesReceived starting with the second packet.
                    long time = SystemClock.elapsedRealtime();
                    if (mStartMillis == 0) {
                        mStartMillis = time;
                    } else {
                        mBytesReceived += receivedPacket.getLength();
                    }
                    // there may not be another packet, update the stop time on every iteration.
                    mStopMillis = time;
                } catch (SocketTimeoutException e) {
                    // sender has stopped sending data, do nothing and return.
                } catch (IOException e) {
                    Log.e(TAG, "socket receive failed", e);
                }
            }
        }
    }

    private long runIngressDataRateMeasurement(final Duration testDuration) throws Exception {
        final RateMeasurementSocketReader reader = new RateMeasurementSocketReader(mSocket);
        reader.startTest();
        sendPacketsToTunInterfaceForDuration(mTunInterface, testDuration);
        return reader.stopAndGetResult();
    }

    void waitForTcPoliceFilterInstalled(Duration timeout) throws IOException {
        final String command = MessageFormat.format("tc filter show ingress dev {0}",
                mTunInterface.getInterfaceName());
        // wait for tc police to show up
        final long startTime = SystemClock.elapsedRealtime();
        final long timeoutTime = startTime + timeout.toMillis();
        while (!SystemUtil.runShellCommand(command).contains("police")) {
            assertLessThan("timed out waiting for tc police filter",
                    SystemClock.elapsedRealtime(), timeoutTime);
            SystemClock.sleep(10);
        }
        Log.v(TAG, "waited " + (SystemClock.elapsedRealtime() - startTime)
                + "ms for tc police filter to appear");
    }

    @Test
    public void testIngressRateLimit_testLimit() throws Exception {
        assumeKernelSupport();

        // These tests are not very precise, especially on lower-end devices.
        // Add 30% tolerance to reduce test flakiness. Burst size is constant at 128KiB.
        final double toleranceFactor = 1.3;

        // If this value is too low, this test might become flaky because of the burst value that
        // allows to send at a higher data rate for a short period of time. The faster the data rate
        // and the longer the test, the less this test will be affected.
        final long dataLimitInBytesPerSecond = 2_000_000; // 2MB/s
        long resultInBytesPerSecond = runIngressDataRateMeasurement(Duration.ofSeconds(1));
        assertGreaterThan("Failed initial test with rate limit disabled", resultInBytesPerSecond,
                (long) (dataLimitInBytesPerSecond * toleranceFactor));

        // enable rate limit and wait until the tc filter is installed before starting the test.
        ConnectivitySettingsManager.setIngressRateLimitInBytesPerSecond(mContext,
                dataLimitInBytesPerSecond);
        waitForTcPoliceFilterInstalled(Duration.ofSeconds(1));

        resultInBytesPerSecond = runIngressDataRateMeasurement(Duration.ofSeconds(15));
        assertLessThan("Failed test with rate limit enabled", resultInBytesPerSecond,
                (long) (dataLimitInBytesPerSecond * toleranceFactor));

        ConnectivitySettingsManager.setIngressRateLimitInBytesPerSecond(mContext, -1);

        resultInBytesPerSecond = runIngressDataRateMeasurement(Duration.ofSeconds(1));
        assertGreaterThan("Failed test with rate limit disabled", resultInBytesPerSecond,
                (long) (dataLimitInBytesPerSecond * toleranceFactor));
    }

    @Test
    public void testIngressRateLimit_testSetting() {
        int dataLimitInBytesPerSecond = 1_000_000;
        ConnectivitySettingsManager.setIngressRateLimitInBytesPerSecond(mContext,
                dataLimitInBytesPerSecond);
        assertEquals(dataLimitInBytesPerSecond,
                ConnectivitySettingsManager.getIngressRateLimitInBytesPerSecond(mContext));
        ConnectivitySettingsManager.setIngressRateLimitInBytesPerSecond(mContext, -1);
        assertEquals(-1,
                ConnectivitySettingsManager.getIngressRateLimitInBytesPerSecond(mContext));
    }
}
