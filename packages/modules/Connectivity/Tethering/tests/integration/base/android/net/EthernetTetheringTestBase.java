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

package android.net;

import static android.Manifest.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS;
import static android.Manifest.permission.MANAGE_TEST_NETWORKS;
import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.Manifest.permission.TETHER_PRIVILEGED;
import static android.content.pm.PackageManager.FEATURE_WIFI;
import static android.net.InetAddresses.parseNumericAddress;
import static android.net.TetheringManager.CONNECTIVITY_SCOPE_GLOBAL;
import static android.net.TetheringManager.CONNECTIVITY_SCOPE_LOCAL;
import static android.net.TetheringManager.TETHERING_ETHERNET;
import static android.net.TetheringTester.buildTcpPacket;
import static android.net.TetheringTester.buildUdpPacket;
import static android.net.TetheringTester.isAddressIpv4;
import static android.net.TetheringTester.isExpectedIcmpPacket;
import static android.net.TetheringTester.isExpectedTcpPacket;
import static android.net.TetheringTester.isExpectedUdpPacket;

import static com.android.net.module.util.HexDump.dumpHexString;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ROUTER_ADVERTISEMENT;
import static com.android.net.module.util.NetworkStackConstants.TCPHDR_ACK;
import static com.android.net.module.util.NetworkStackConstants.TCPHDR_SYN;
import static com.android.testutils.TestNetworkTrackerKt.initTestNetwork;
import static com.android.testutils.TestPermissionUtil.runAsShell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.EthernetManager.TetheredInterfaceCallback;
import android.net.EthernetManager.TetheredInterfaceRequest;
import android.net.TetheringManager.StartTetheringCallback;
import android.net.TetheringManager.TetheringEventCallback;
import android.net.TetheringManager.TetheringRequest;
import android.net.TetheringTester.TetheredDevice;
import android.net.cts.util.CtsNetUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.Struct;
import com.android.net.module.util.structs.Ipv6Header;
import com.android.testutils.HandlerUtils;
import com.android.testutils.TapPacketReader;
import com.android.testutils.TestNetworkTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.FileDescriptor;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * TODO: Common variables or methods shared between CtsEthernetTetheringTest and
 * MtsEthernetTetheringTest.
 */
public abstract class EthernetTetheringTestBase {
    private static final String TAG = EthernetTetheringTestBase.class.getSimpleName();

    protected static final int TIMEOUT_MS = 5000;
    // Used to check if any tethering interface is available. Choose 200ms to be request timeout
    // because the average interface requested time on cuttlefish@acloud is around 10ms.
    // See TetheredInterfaceRequester.getInterface, isInterfaceForTetheringAvailable.
    private static final int AVAILABLE_TETHER_IFACE_REQUEST_TIMEOUT_MS = 200;
    private static final int TETHER_REACHABILITY_ATTEMPTS = 20;
    protected static final long WAIT_RA_TIMEOUT_MS = 2000;

    // Address and NAT prefix definition.
    protected static final MacAddress TEST_MAC = MacAddress.fromString("1:2:3:4:5:6");
    protected static final LinkAddress TEST_IP4_ADDR = new LinkAddress("10.0.0.1/24");
    protected static final LinkAddress TEST_IP6_ADDR = new LinkAddress("2001:db8:1::101/64");
    protected static final InetAddress TEST_IP4_DNS = parseNumericAddress("8.8.8.8");
    protected static final InetAddress TEST_IP6_DNS = parseNumericAddress("2001:db8:1::888");

    protected static final Inet4Address REMOTE_IP4_ADDR =
            (Inet4Address) parseNumericAddress("8.8.8.8");
    protected static final Inet6Address REMOTE_IP6_ADDR =
            (Inet6Address) parseNumericAddress("2002:db8:1::515:ca");
    // The IPv6 network address translation of REMOTE_IP4_ADDR if pref64::/n is 64:ff9b::/96.
    // For more information, see TetheringTester#PREF64_IPV4ONLY_ADDR, which assumes a prefix
    // of 64:ff9b::/96.
    protected static final Inet6Address REMOTE_NAT64_ADDR =
            (Inet6Address) parseNumericAddress("64:ff9b::808:808");

    // LOCAL_PORT is used by public port and private port. Assume port 9876 has not been used yet
    // before the testing that public port and private port are the same in the testing. Note that
    // NAT port forwarding could be different between private port and public port.
    protected static final short LOCAL_PORT = 9876;
    protected static final short REMOTE_PORT = 433;

    // Payload definition.
    protected static final ByteBuffer EMPTY_PAYLOAD = ByteBuffer.wrap(new byte[0]);
    private static final ByteBuffer TEST_REACHABILITY_PAYLOAD =
            ByteBuffer.wrap(new byte[] { (byte) 0x55, (byte) 0xaa });
    protected static final ByteBuffer RX_PAYLOAD =
            ByteBuffer.wrap(new byte[] { (byte) 0x12, (byte) 0x34 });
    protected static final ByteBuffer TX_PAYLOAD =
            ByteBuffer.wrap(new byte[] { (byte) 0x56, (byte) 0x78 });

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final EthernetManager sEm = sContext.getSystemService(EthernetManager.class);
    private static final TetheringManager sTm = sContext.getSystemService(TetheringManager.class);
    private static final PackageManager sPackageManager = sContext.getPackageManager();
    private static final CtsNetUtils sCtsNetUtils = new CtsNetUtils(sContext);

    // Late initialization in setUp()
    private boolean mRunTests;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private TetheredInterfaceRequester mTetheredInterfaceRequester;

    // Late initialization in initTetheringTester().
    private TapPacketReader mUpstreamReader;
    private TestNetworkTracker mUpstreamTracker;
    private TestNetworkInterface mDownstreamIface;
    private TapPacketReader mDownstreamReader;
    private MyTetheringEventCallback mTetheringEventCallback;

    public Context getContext() {
        return sContext;
    }

    @BeforeClass
    public static void setUpOnce() throws Exception {
        // The first test case may experience tethering restart with IP conflict handling.
        // Tethering would cache the last upstreams so that the next enabled tethering avoids
        // picking up the address that is in conflict with the upstreams. To protect subsequent
        // tests, turn tethering on and off before running them.
        MyTetheringEventCallback callback = null;
        TestNetworkInterface testIface = null;
        try {
            // If the physical ethernet interface is available, do nothing.
            if (isInterfaceForTetheringAvailable()) return;

            testIface = createTestInterface();
            setIncludeTestInterfaces(true);

            callback = enableEthernetTethering(testIface.getInterfaceName(), null);
            callback.awaitUpstreamChanged(true /* throwTimeoutException */);
        } catch (TimeoutException e) {
            Log.d(TAG, "WARNNING " + e);
        } finally {
            maybeCloseTestInterface(testIface);
            maybeUnregisterTetheringEventCallback(callback);

            setIncludeTestInterfaces(false);
        }
    }

    @Before
    public void setUp() throws Exception {
        mHandlerThread = new HandlerThread(getClass().getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mRunTests = isEthernetTetheringSupported();
        assumeTrue(mRunTests);

        mTetheredInterfaceRequester = new TetheredInterfaceRequester();
    }

    private boolean isEthernetTetheringSupported() throws Exception {
        if (sEm == null) return false;

        return runAsShell(NETWORK_SETTINGS, TETHER_PRIVILEGED, () -> sTm.isTetheringSupported());
    }

    protected void maybeStopTapPacketReader(final TapPacketReader tapPacketReader)
            throws Exception {
        if (tapPacketReader != null) {
            TapPacketReader reader = tapPacketReader;
            mHandler.post(() -> reader.stop());
        }
    }

    protected static void maybeCloseTestInterface(final TestNetworkInterface testInterface)
            throws Exception {
        if (testInterface != null) {
            testInterface.getFileDescriptor().close();
            Log.d(TAG, "Deleted test interface " + testInterface.getInterfaceName());
        }
    }

    protected static void maybeUnregisterTetheringEventCallback(
            final MyTetheringEventCallback callback) throws Exception {
        if (callback != null) {
            callback.awaitInterfaceUntethered();
            callback.unregister();
        }
    }

    protected void stopEthernetTethering(final MyTetheringEventCallback callback) {
        runAsShell(TETHER_PRIVILEGED, () -> {
            sTm.stopTethering(TETHERING_ETHERNET);
            maybeUnregisterTetheringEventCallback(callback);
        });
    }

    protected void cleanUp() throws Exception {
        setPreferTestNetworks(false);

        if (mUpstreamTracker != null) {
            runAsShell(MANAGE_TEST_NETWORKS, () -> {
                mUpstreamTracker.teardown();
                mUpstreamTracker = null;
            });
        }
        if (mUpstreamReader != null) {
            TapPacketReader reader = mUpstreamReader;
            mHandler.post(() -> reader.stop());
            mUpstreamReader = null;
        }

        maybeStopTapPacketReader(mDownstreamReader);
        mDownstreamReader = null;
        // To avoid flaky which caused by the next test started but the previous interface is not
        // untracked from EthernetTracker yet. Just delete the test interface without explicitly
        // calling TetheringManager#stopTethering could let EthernetTracker untrack the test
        // interface from server mode before tethering stopped. Thus, awaitInterfaceUntethered
        // could not only make sure tethering is stopped but also guarantee the test interface is
        // untracked from EthernetTracker.
        maybeCloseTestInterface(mDownstreamIface);
        mDownstreamIface = null;
        maybeUnregisterTetheringEventCallback(mTetheringEventCallback);
        mTetheringEventCallback = null;

        runAsShell(NETWORK_SETTINGS, () -> mTetheredInterfaceRequester.release());
        setIncludeTestInterfaces(false);
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (mRunTests) cleanUp();
        } finally {
            mHandlerThread.quitSafely();
            mHandlerThread.join();
        }
    }

    protected static boolean isInterfaceForTetheringAvailable() throws Exception {
        // Before T, all ethernet interfaces could be used for server mode. Instead of
        // waiting timeout, just checking whether the system currently has any
        // ethernet interface is more reliable.
        if (!SdkLevel.isAtLeastT()) {
            return runAsShell(CONNECTIVITY_USE_RESTRICTED_NETWORKS, () -> sEm.isAvailable());
        }

        // If previous test case doesn't release tethering interface successfully, the other tests
        // after that test may be skipped as unexcepted.
        // TODO: figure out a better way to check default tethering interface existenion.
        final TetheredInterfaceRequester requester = new TetheredInterfaceRequester();
        try {
            // Use short timeout (200ms) for requesting an existing interface, if any, because
            // it should reurn faster than requesting a new tethering interface. Using default
            // timeout (5000ms, TIMEOUT_MS) may make that total testing time is over 1 minute
            // test module timeout on internal testing.
            // TODO: if this becomes flaky, consider using default timeout (5000ms) and moving
            // this check into #setUpOnce.
            return requester.getInterface(AVAILABLE_TETHER_IFACE_REQUEST_TIMEOUT_MS) != null;
        } catch (TimeoutException e) {
            return false;
        } finally {
            runAsShell(NETWORK_SETTINGS, () -> {
                requester.release();
            });
        }
    }

    protected static void setIncludeTestInterfaces(boolean include) {
        runAsShell(NETWORK_SETTINGS, () -> {
            sEm.setIncludeTestInterfaces(include);
        });
    }

    protected static void setPreferTestNetworks(boolean prefer) {
        runAsShell(NETWORK_SETTINGS, () -> {
            sTm.setPreferTestNetworks(prefer);
        });
    }

    protected String getTetheredInterface() throws Exception {
        return mTetheredInterfaceRequester.getInterface();
    }

    protected CompletableFuture<String> requestTetheredInterface() throws Exception {
        return mTetheredInterfaceRequester.requestInterface();
    }

    protected static void waitForRouterAdvertisement(TapPacketReader reader, String iface,
            long timeoutMs) {
        final long deadline = SystemClock.uptimeMillis() + timeoutMs;
        do {
            byte[] pkt = reader.popPacket(timeoutMs);
            if (isExpectedIcmpPacket(pkt, true /* hasEth */, false /* isIpv4 */,
                    ICMPV6_ROUTER_ADVERTISEMENT)) {
                return;
            }

            timeoutMs = deadline - SystemClock.uptimeMillis();
        } while (timeoutMs > 0);
        fail("Did not receive router advertisement on " + iface + " after "
                +  timeoutMs + "ms idle");
    }


    protected static final class MyTetheringEventCallback implements TetheringEventCallback {
        private final CountDownLatch mTetheringStartedLatch = new CountDownLatch(1);
        private final CountDownLatch mTetheringStoppedLatch = new CountDownLatch(1);
        private final CountDownLatch mLocalOnlyStartedLatch = new CountDownLatch(1);
        private final CountDownLatch mLocalOnlyStoppedLatch = new CountDownLatch(1);
        private final CountDownLatch mClientConnectedLatch = new CountDownLatch(1);
        private final CountDownLatch mUpstreamLatch = new CountDownLatch(1);
        private final CountDownLatch mCallbackRegisteredLatch = new CountDownLatch(1);
        private final TetheringInterface mIface;
        private final Network mExpectedUpstream;

        private final boolean mAcceptAnyUpstream;

        private volatile boolean mInterfaceWasTethered = false;
        private volatile boolean mInterfaceWasLocalOnly = false;
        private volatile boolean mUnregistered = false;
        private volatile Collection<TetheredClient> mClients = null;
        private volatile Network mUpstream = null;

        // The dnsmasq in R might block netd for 20 seconds, which can also block tethering
        // enable/disable for 20 seconds. To fix this, changing the timeouts from 5 seconds to 30
        // seconds. See b/289881008.
        private static final int EXPANDED_TIMEOUT_MS = 30000;

        MyTetheringEventCallback(String iface) {
            mIface = new TetheringInterface(TETHERING_ETHERNET, iface);
            mExpectedUpstream = null;
            mAcceptAnyUpstream = true;
        }

        MyTetheringEventCallback(String iface, @NonNull Network expectedUpstream) {
            Objects.requireNonNull(expectedUpstream);
            mIface = new TetheringInterface(TETHERING_ETHERNET, iface);
            mExpectedUpstream = expectedUpstream;
            mAcceptAnyUpstream = false;
        }

        public void unregister() {
            sTm.unregisterTetheringEventCallback(this);
            mUnregistered = true;
        }
        @Override
        public void onTetheredInterfacesChanged(List<String> interfaces) {
            fail("Should only call callback that takes a Set<TetheringInterface>");
        }

        @Override
        public void onTetheredInterfacesChanged(Set<TetheringInterface> interfaces) {
            // Ignore stale callbacks registered by previous test cases.
            if (mUnregistered) return;

            if (!mInterfaceWasTethered && interfaces.contains(mIface)) {
                // This interface is being tethered for the first time.
                Log.d(TAG, "Tethering started: " + interfaces);
                mInterfaceWasTethered = true;
                mTetheringStartedLatch.countDown();
            } else if (mInterfaceWasTethered && !interfaces.contains(mIface)) {
                Log.d(TAG, "Tethering stopped: " + interfaces);
                mTetheringStoppedLatch.countDown();
            }
        }

        @Override
        public void onLocalOnlyInterfacesChanged(List<String> interfaces) {
            fail("Should only call callback that takes a Set<TetheringInterface>");
        }

        @Override
        public void onLocalOnlyInterfacesChanged(Set<TetheringInterface> interfaces) {
            // Ignore stale callbacks registered by previous test cases.
            if (mUnregistered) return;

            if (!mInterfaceWasLocalOnly && interfaces.contains(mIface)) {
                // This interface is being put into local-only mode for the first time.
                Log.d(TAG, "Local-only started: " + interfaces);
                mInterfaceWasLocalOnly = true;
                mLocalOnlyStartedLatch.countDown();
            } else if (mInterfaceWasLocalOnly && !interfaces.contains(mIface)) {
                Log.d(TAG, "Local-only stopped: " + interfaces);
                mLocalOnlyStoppedLatch.countDown();
            }
        }

        public void awaitInterfaceTethered() throws Exception {
            assertTrue("Ethernet not tethered after " + EXPANDED_TIMEOUT_MS + "ms",
                    mTetheringStartedLatch.await(EXPANDED_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }

        public void awaitInterfaceLocalOnly() throws Exception {
            assertTrue("Ethernet not local-only after " + EXPANDED_TIMEOUT_MS + "ms",
                    mLocalOnlyStartedLatch.await(EXPANDED_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }

        // Used to check if the callback has registered. When the callback is registered,
        // onSupportedTetheringTypes is celled in onCallbackStarted(). After
        // onSupportedTetheringTypes called, drop the permission for registering callback.
        // See MyTetheringEventCallback#register, TetheringManager#onCallbackStarted.
        @Override
        public void onSupportedTetheringTypes(Set<Integer> supportedTypes) {
            // Used to check callback registered.
            mCallbackRegisteredLatch.countDown();
        }

        public void awaitCallbackRegistered() throws Exception {
            if (!mCallbackRegisteredLatch.await(EXPANDED_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                fail("Did not receive callback registered signal after " + EXPANDED_TIMEOUT_MS
                        + "ms");
            }
        }

        public void awaitInterfaceUntethered() throws Exception {
            // Don't block teardown if the interface was never tethered.
            // This is racy because the interface might become tethered right after this check, but
            // that can only happen in tearDown if startTethering timed out, which likely means
            // the test has already failed.
            if (!mInterfaceWasTethered && !mInterfaceWasLocalOnly) return;

            if (mInterfaceWasTethered) {
                assertTrue(mIface + " not untethered after " + EXPANDED_TIMEOUT_MS + "ms",
                        mTetheringStoppedLatch.await(EXPANDED_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            } else if (mInterfaceWasLocalOnly) {
                assertTrue(mIface + " not untethered after " + EXPANDED_TIMEOUT_MS + "ms",
                        mLocalOnlyStoppedLatch.await(EXPANDED_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            } else {
                fail(mIface + " cannot be both tethered and local-only. Update this test class.");
            }
        }

        @Override
        public void onError(String ifName, int error) {
            // Ignore stale callbacks registered by previous test cases.
            if (mUnregistered) return;

            fail("TetheringEventCallback got error:" + error + " on iface " + ifName);
        }

        @Override
        public void onClientsChanged(Collection<TetheredClient> clients) {
            // Ignore stale callbacks registered by previous test cases.
            if (mUnregistered) return;

            Log.d(TAG, "Got clients changed: " + clients);
            mClients = clients;
            if (clients.size() > 0) {
                mClientConnectedLatch.countDown();
            }
        }

        public Collection<TetheredClient> awaitClientConnected() throws Exception {
            assertTrue("Did not receive client connected callback after "
                    + EXPANDED_TIMEOUT_MS + "ms",
                    mClientConnectedLatch.await(EXPANDED_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            return mClients;
        }

        @Override
        public void onUpstreamChanged(Network network) {
            // Ignore stale callbacks registered by previous test cases.
            if (mUnregistered) return;

            Log.d(TAG, "Got upstream changed: " + network);
            mUpstream = network;
            // The callback always updates the current tethering status when it's first registered.
            // If the caller registers the callback before tethering starts, the null upstream
            // would be updated. Filtering out the null case because it's not a valid upstream that
            // we care about.
            if (mUpstream == null) return;
            if (mAcceptAnyUpstream || Objects.equals(mUpstream, mExpectedUpstream)) {
                mUpstreamLatch.countDown();
            }
        }

        public Network awaitUpstreamChanged(boolean throwTimeoutException) throws Exception {
            if (!mUpstreamLatch.await(EXPANDED_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                final String errorMessage = "Did not receive upstream "
                            + (mAcceptAnyUpstream ? "any" : mExpectedUpstream)
                            + " callback after " + EXPANDED_TIMEOUT_MS + "ms";

                if (throwTimeoutException) {
                    throw new TimeoutException(errorMessage);
                } else {
                    fail(errorMessage);
                }
            }
            return mUpstream;
        }
    }

    protected static MyTetheringEventCallback enableEthernetTethering(String iface,
            TetheringRequest request, Network expectedUpstream) throws Exception {
        // Enable ethernet tethering with null expectedUpstream means the test accept any upstream
        // after etherent tethering started.
        final MyTetheringEventCallback callback;
        if (expectedUpstream != null) {
            callback = new MyTetheringEventCallback(iface, expectedUpstream);
        } else {
            callback = new MyTetheringEventCallback(iface);
        }
        runAsShell(NETWORK_SETTINGS, () -> {
            sTm.registerTetheringEventCallback(c -> c.run() /* executor */, callback);
            // Need to hold the shell permission until callback is registered. This helps to avoid
            // the test become flaky.
            callback.awaitCallbackRegistered();
        });
        final CountDownLatch tetheringStartedLatch = new CountDownLatch(1);
        StartTetheringCallback startTetheringCallback = new StartTetheringCallback() {
            @Override
            public void onTetheringStarted() {
                Log.d(TAG, "Ethernet tethering started");
                tetheringStartedLatch.countDown();
            }

            @Override
            public void onTetheringFailed(int resultCode) {
                fail("Unexpectedly got onTetheringFailed");
            }
        };
        Log.d(TAG, "Starting Ethernet tethering");
        runAsShell(TETHER_PRIVILEGED, () -> {
            sTm.startTethering(request, c -> c.run() /* executor */, startTetheringCallback);
            // Binder call is an async call. Need to hold the shell permission until tethering
            // started. This helps to avoid the test become flaky.
            if (!tetheringStartedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                fail("Did not receive tethering started callback after " + TIMEOUT_MS + "ms");
            }
        });

        final int connectivityType = request.getConnectivityScope();
        switch (connectivityType) {
            case CONNECTIVITY_SCOPE_GLOBAL:
                callback.awaitInterfaceTethered();
                break;
            case CONNECTIVITY_SCOPE_LOCAL:
                callback.awaitInterfaceLocalOnly();
                break;
            default:
                fail("Unexpected connectivity type requested: " + connectivityType);
        }

        return callback;
    }

    protected static MyTetheringEventCallback enableEthernetTethering(String iface,
            Network expectedUpstream) throws Exception {
        return enableEthernetTethering(iface,
                new TetheringRequest.Builder(TETHERING_ETHERNET)
                .setShouldShowEntitlementUi(false).build(), expectedUpstream);
    }

    protected int getMTU(TestNetworkInterface iface) throws SocketException {
        NetworkInterface nif = NetworkInterface.getByName(iface.getInterfaceName());
        assertNotNull("Can't get NetworkInterface object for " + iface.getInterfaceName(), nif);
        return nif.getMTU();
    }

    protected TapPacketReader makePacketReader(final TestNetworkInterface iface) throws Exception {
        FileDescriptor fd = iface.getFileDescriptor().getFileDescriptor();
        return makePacketReader(fd, getMTU(iface));
    }

    protected TapPacketReader makePacketReader(FileDescriptor fd, int mtu) {
        final TapPacketReader reader = new TapPacketReader(mHandler, fd, mtu);
        mHandler.post(() -> reader.start());
        HandlerUtils.waitForIdle(mHandler, TIMEOUT_MS);
        return reader;
    }

    protected static final class TetheredInterfaceRequester implements TetheredInterfaceCallback {
        private TetheredInterfaceRequest mRequest;
        private final CompletableFuture<String> mFuture = new CompletableFuture<>();

        @Override
        public void onAvailable(String iface) {
            Log.d(TAG, "Ethernet interface available: " + iface);
            mFuture.complete(iface);
        }

        @Override
        public void onUnavailable() {
            mFuture.completeExceptionally(new IllegalStateException("onUnavailable received"));
        }

        public CompletableFuture<String> requestInterface() {
            assertNull("BUG: more than one tethered interface request", mRequest);
            Log.d(TAG, "Requesting tethered interface");
            mRequest = runAsShell(NETWORK_SETTINGS, () ->
                    sEm.requestTetheredInterface(c -> c.run() /* executor */, this));
            return mFuture;
        }

        public String getInterface(int timeout) throws Exception {
            return requestInterface().get(timeout, TimeUnit.MILLISECONDS);
        }

        public String getInterface() throws Exception {
            return getInterface(TIMEOUT_MS);
        }

        public void release() {
            if (mRequest != null) {
                mFuture.obtrudeException(new IllegalStateException("Request already released"));
                mRequest.release();
                mRequest = null;
            }
        }
    }

    protected static TestNetworkInterface createTestInterface() throws Exception {
        TestNetworkManager tnm = runAsShell(MANAGE_TEST_NETWORKS, () ->
                sContext.getSystemService(TestNetworkManager.class));
        TestNetworkInterface iface = runAsShell(MANAGE_TEST_NETWORKS, () ->
                tnm.createTapInterface());
        Log.d(TAG, "Created test interface " + iface.getInterfaceName());
        return iface;
    }

    protected TestNetworkTracker createTestUpstream(final List<LinkAddress> addresses,
            final List<InetAddress> dnses) throws Exception {
        setPreferTestNetworks(true);

        final LinkProperties lp = new LinkProperties();
        lp.setLinkAddresses(addresses);
        lp.setDnsServers(dnses);

        return runAsShell(MANAGE_TEST_NETWORKS, () -> initTestNetwork(sContext, lp, TIMEOUT_MS));
    }

    protected void sendDownloadPacketUdp(@NonNull final InetAddress srcIp,
            @NonNull final InetAddress dstIp, @NonNull final TetheringTester tester,
            boolean is6To4) throws Exception {
        if (is6To4) {
            assertFalse("CLAT download test must sends IPv6 packet", isAddressIpv4(srcIp, dstIp));
        }

        // Expected received UDP packet IP protocol. While testing CLAT (is6To4 = true), the packet
        // on downstream must be IPv4. Otherwise, the IP protocol of test packet is the same on
        // both downstream and upstream.
        final boolean isIpv4 = is6To4 ? true : isAddressIpv4(srcIp, dstIp);

        final ByteBuffer testPacket = buildUdpPacket(srcIp, dstIp, REMOTE_PORT /* srcPort */,
                LOCAL_PORT /* dstPort */, RX_PAYLOAD);
        tester.verifyDownload(testPacket, p -> {
            Log.d(TAG, "Packet in downstream: " + dumpHexString(p));
            return isExpectedUdpPacket(p, true /* hasEther */, isIpv4, RX_PAYLOAD);
        });
    }

    protected void sendUploadPacketUdp(@NonNull final MacAddress srcMac,
            @NonNull final MacAddress dstMac, @NonNull final InetAddress srcIp,
            @NonNull final InetAddress dstIp, @NonNull final TetheringTester tester,
            boolean is4To6) throws Exception {
        if (is4To6) {
            assertTrue("CLAT upload test must sends IPv4 packet", isAddressIpv4(srcIp, dstIp));
        }

        // Expected received UDP packet IP protocol. While testing CLAT (is4To6 = true), the packet
        // on upstream must be IPv6. Otherwise, the IP protocol of test packet is the same on
        // both downstream and upstream.
        final boolean isIpv4 = is4To6 ? false : isAddressIpv4(srcIp, dstIp);

        final ByteBuffer testPacket = buildUdpPacket(srcMac, dstMac, srcIp, dstIp,
                LOCAL_PORT /* srcPort */, REMOTE_PORT /* dstPort */, TX_PAYLOAD);
        tester.verifyUpload(testPacket, p -> {
            Log.d(TAG, "Packet in upstream: " + dumpHexString(p));
            return isExpectedUdpPacket(p, false /* hasEther */, isIpv4, TX_PAYLOAD);
        });
    }

    protected void sendDownloadPacketTcp(@NonNull final InetAddress srcIp,
            @NonNull final InetAddress dstIp, short seq, short ack, byte tcpFlags,
            @NonNull final ByteBuffer payload, @NonNull final TetheringTester tester,
            boolean is6To4) throws Exception {
        if (is6To4) {
            assertFalse("CLAT download test must sends IPv6 packet", isAddressIpv4(srcIp, dstIp));
        }

        // Expected received TCP packet IP protocol. While testing CLAT (is6To4 = true), the packet
        // on downstream must be IPv4. Otherwise, the IP protocol of test packet is the same on
        // both downstream and upstream.
        final boolean isIpv4 = is6To4 ? true : isAddressIpv4(srcIp, dstIp);

        final ByteBuffer testPacket = buildTcpPacket(null /* srcMac */, null /* dstMac */,
                srcIp, dstIp, REMOTE_PORT /* srcPort */, LOCAL_PORT /* dstPort */, seq, ack,
                tcpFlags, payload);
        tester.verifyDownload(testPacket, p -> {
            Log.d(TAG, "Packet in downstream: " + dumpHexString(p));

            return isExpectedTcpPacket(p, true /* hasEther */, isIpv4, seq, payload);
        });
    }

    protected void sendUploadPacketTcp(@NonNull final MacAddress srcMac,
            @NonNull final MacAddress dstMac, @NonNull final InetAddress srcIp,
            @NonNull final InetAddress dstIp, short seq, short ack, byte tcpFlags,
            @NonNull final ByteBuffer payload, @NonNull final TetheringTester tester,
            boolean is4To6) throws Exception {
        if (is4To6) {
            assertTrue("CLAT upload test must sends IPv4 packet", isAddressIpv4(srcIp, dstIp));
        }

        // Expected received TCP packet IP protocol. While testing CLAT (is4To6 = true), the packet
        // on upstream must be IPv6. Otherwise, the IP protocol of test packet is the same on
        // both downstream and upstream.
        final boolean isIpv4 = is4To6 ? false : isAddressIpv4(srcIp, dstIp);

        final ByteBuffer testPacket = buildTcpPacket(srcMac, dstMac, srcIp, dstIp,
                LOCAL_PORT /* srcPort */, REMOTE_PORT /* dstPort */, seq, ack, tcpFlags,
                payload);
        tester.verifyUpload(testPacket, p -> {
            Log.d(TAG, "Packet in upstream: " + dumpHexString(p));

            return isExpectedTcpPacket(p, false /* hasEther */, isIpv4, seq, payload);
        });
    }

    protected void runTcpTest(
            @NonNull final MacAddress uploadSrcMac, @NonNull final MacAddress uploadDstMac,
            @NonNull final InetAddress uploadSrcIp, @NonNull final InetAddress uploadDstIp,
            @NonNull final InetAddress downloadSrcIp, @NonNull final InetAddress downloadDstIp,
            @NonNull final TetheringTester tester, boolean isClat) throws Exception {
        // Three way handshake and data transfer.
        //
        // Server (base seq = 2000)                                  Client (base seq = 1000)
        //   |                                                          |
        //   |    [1] [SYN] SEQ = 1000                                  |
        //   |<---------------------------------------------------------|  -
        //   |                                                          |  ^
        //   |    [2] [SYN + ACK] SEQ = 2000, ACK = 1000+1              |  |
        //   |--------------------------------------------------------->|  three way handshake
        //   |                                                          |  |
        //   |    [3] [ACK] SEQ = 1001, ACK = 2000+1                    |  v
        //   |<---------------------------------------------------------|  -
        //   |                                                          |  ^
        //   |    [4] [ACK] SEQ = 1001, ACK = 2001, 2 byte payload      |  |
        //   |<---------------------------------------------------------|  data transfer
        //   |                                                          |  |
        //   |    [5] [ACK] SEQ = 2001, ACK = 1001+2, 2 byte payload    |  v
        //   |--------------------------------------------------------->|  -
        //   |                                                          |
        //

        // This test can only verify the packets are transferred end to end but TCP state.
        // TODO: verify TCP state change via /proc/net/nf_conntrack or netlink conntrack event.
        // [1] [UPLOAD] [SYN]: SEQ = 1000
        sendUploadPacketTcp(uploadSrcMac, uploadDstMac, uploadSrcIp, uploadDstIp,
                (short) 1000 /* seq */, (short) 0 /* ack */, TCPHDR_SYN, EMPTY_PAYLOAD,
                tester, isClat /* is4To6 */);

        // [2] [DONWLOAD] [SYN + ACK]: SEQ = 2000, ACK = 1001
        sendDownloadPacketTcp(downloadSrcIp, downloadDstIp, (short) 2000 /* seq */,
                (short) 1001 /* ack */, (byte) ((TCPHDR_SYN | TCPHDR_ACK) & 0xff), EMPTY_PAYLOAD,
                tester, isClat /* is6To4 */);

        // [3] [UPLOAD] [ACK]: SEQ = 1001, ACK = 2001
        sendUploadPacketTcp(uploadSrcMac, uploadDstMac, uploadSrcIp, uploadDstIp,
                (short) 1001 /* seq */, (short) 2001 /* ack */, TCPHDR_ACK, EMPTY_PAYLOAD, tester,
                isClat /* is4To6 */);

        // [4] [UPLOAD] [ACK]: SEQ = 1001, ACK = 2001, 2 byte payload
        sendUploadPacketTcp(uploadSrcMac, uploadDstMac, uploadSrcIp, uploadDstIp,
                (short) 1001 /* seq */, (short) 2001 /* ack */, TCPHDR_ACK, TX_PAYLOAD,
                tester, isClat /* is4To6 */);

        // [5] [DONWLOAD] [ACK]: SEQ = 2001, ACK = 1003, 2 byte payload
        sendDownloadPacketTcp(downloadSrcIp, downloadDstIp, (short) 2001 /* seq */,
                (short) 1003 /* ack */, TCPHDR_ACK, RX_PAYLOAD, tester, isClat /* is6To4 */);

        // TODO: test BPF offload maps.
    }

    // TODO: remove ipv4 verification (is4To6 = false) once upstream connected notification race is
    // fixed. See #runUdp4Test.
    //
    // This function sends a probe packet to downstream interface and exam the result from upstream
    // interface to make sure ipv4 tethering is ready. Return the entire packet which received from
    // upstream interface.
    @NonNull
    protected byte[] probeV4TetheringConnectivity(TetheringTester tester, TetheredDevice tethered,
            boolean is4To6) throws Exception {
        final ByteBuffer probePacket = buildUdpPacket(tethered.macAddr,
                tethered.routerMacAddr, tethered.ipv4Addr /* srcIp */,
                REMOTE_IP4_ADDR /* dstIp */, LOCAL_PORT /* srcPort */, REMOTE_PORT /* dstPort */,
                TEST_REACHABILITY_PAYLOAD);

        // Send a UDP packet from client and check the packet can be found on upstream interface.
        for (int i = 0; i < TETHER_REACHABILITY_ATTEMPTS; i++) {
            byte[] expectedPacket = tester.testUpload(probePacket, p -> {
                Log.d(TAG, "Packet in upstream: " + dumpHexString(p));
                // If is4To6 is true, the ipv4 probe packet would be translated to ipv6 by Clat and
                // would see this translated ipv6 packet in upstream interface.
                return isExpectedUdpPacket(p, false /* hasEther */, !is4To6 /* isIpv4 */,
                        TEST_REACHABILITY_PAYLOAD);
            });
            if (expectedPacket != null) return expectedPacket;
        }

        fail("Can't verify " + (is4To6 ? "ipv4 to ipv6" : "ipv4") + " tethering connectivity after "
                + TETHER_REACHABILITY_ATTEMPTS + " attempts");
        return null;
    }

    // TODO: remove triggering upstream reselection once test network can replace selected upstream
    // network in Tethering module.
    private void maybeRetryTestedUpstreamChanged(final Network expectedUpstream,
            final TimeoutException fallbackException) throws Exception {
        // Fall back original exception because no way to reselect if there is no WIFI feature.
        assertTrue(fallbackException.toString(), sPackageManager.hasSystemFeature(FEATURE_WIFI));

        // Try to toggle wifi network, if any, to reselect upstream network via default network
        // switching. Because test network has higher priority than internet network, this can
        // help selecting test network to be upstream network for testing. This tries to avoid
        // the flaky upstream selection under multinetwork environment. Internet and test network
        // upstream changed event order is not guaranteed. Once tethering selects non-test
        // upstream {wifi, ..}, test network won't be selected anymore. If too many test cases
        // trigger the reselection, the total test time may over test suite 1 minmute timeout.
        // Probably need to disable/restore all internet networks in a common place of test
        // process. Currently, EthernetTetheringTest is part of CTS test which needs wifi network
        // connection if device has wifi feature.
        // See Tethering#chooseUpstreamType
        // TODO: toggle cellular network if the device has no WIFI feature.
        Log.d(TAG, "Toggle WIFI to retry upstream selection");
        sCtsNetUtils.disableWifi();
        sCtsNetUtils.ensureWifiConnected();

        // Wait for expected upstream.
        final CompletableFuture<Network> future = new CompletableFuture<>();
        final TetheringEventCallback callback = new TetheringEventCallback() {
            @Override
            public void onUpstreamChanged(Network network) {
                Log.d(TAG, "Got upstream changed: " + network);
                if (Objects.equals(expectedUpstream, network)) {
                    future.complete(network);
                }
            }
        };
        try {
            sTm.registerTetheringEventCallback(mHandler::post, callback);
            assertEquals("onUpstreamChanged for unexpected network", expectedUpstream,
                    future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            throw new AssertionError("Did not receive upstream " + expectedUpstream
                    + " callback after " + TIMEOUT_MS + "ms");
        } finally {
            sTm.unregisterTetheringEventCallback(callback);
        }
    }

    protected TetheringTester initTetheringTester(List<LinkAddress> upstreamAddresses,
            List<InetAddress> upstreamDnses) throws Exception {
        assumeFalse(isInterfaceForTetheringAvailable());

        // MyTetheringEventCallback currently only support await first available upstream. Tethering
        // may select internet network as upstream if test network is not available and not be
        // preferred yet. Create test upstream network before enable tethering.
        mUpstreamTracker = createTestUpstream(upstreamAddresses, upstreamDnses);

        mDownstreamIface = createTestInterface();
        setIncludeTestInterfaces(true);

        // Make sure EtherentTracker use "mDownstreamIface" as server mode interface.
        assertEquals("TetheredInterfaceCallback for unexpected interface",
                mDownstreamIface.getInterfaceName(), mTetheredInterfaceRequester.getInterface());

        mTetheringEventCallback = enableEthernetTethering(mDownstreamIface.getInterfaceName(),
                mUpstreamTracker.getNetwork());

        try {
            assertEquals("onUpstreamChanged for test network", mUpstreamTracker.getNetwork(),
                    mTetheringEventCallback.awaitUpstreamChanged(
                            true /* throwTimeoutException */));
        } catch (TimeoutException e) {
            // Due to race condition inside tethering module, test network may not be selected as
            // tethering upstream. Force tethering retry upstream if possible. If it is not
            // possible to retry, fail the test with the original timeout exception.
            maybeRetryTestedUpstreamChanged(mUpstreamTracker.getNetwork(), e);
        }

        mDownstreamReader = makePacketReader(mDownstreamIface);
        mUpstreamReader = makePacketReader(mUpstreamTracker.getTestIface());

        final ConnectivityManager cm = sContext.getSystemService(ConnectivityManager.class);
        // Currently tethering don't have API to tell when ipv6 tethering is available. Thus, make
        // sure tethering already have ipv6 connectivity before testing.
        if (cm.getLinkProperties(mUpstreamTracker.getNetwork()).hasGlobalIpv6Address()) {
            waitForRouterAdvertisement(mDownstreamReader, mDownstreamIface.getInterfaceName(),
                    WAIT_RA_TIMEOUT_MS);
        }

        return new TetheringTester(mDownstreamReader, mUpstreamReader);
    }

    @NonNull
    protected Inet6Address getClatIpv6Address(TetheringTester tester, TetheredDevice tethered)
            throws Exception {
        // Send an IPv4 UDP packet from client and check that a CLAT translated IPv6 UDP packet can
        // be found on upstream interface. Get CLAT IPv6 address from the CLAT translated IPv6 UDP
        // packet.
        byte[] expectedPacket = probeV4TetheringConnectivity(tester, tethered, true /* is4To6 */);

        // Above has guaranteed that the found packet is an IPv6 packet without ether header.
        return Struct.parse(Ipv6Header.class, ByteBuffer.wrap(expectedPacket)).srcIp;
    }

    protected <T> List<T> toList(T... array) {
        return Arrays.asList(array);
    }
}
