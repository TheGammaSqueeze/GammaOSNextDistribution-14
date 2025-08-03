/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.net.vcn.cts;

import static android.net.cts.util.CtsNetUtils.TestNetworkCallback;

import android.annotation.NonNull;
import android.content.Context;
import android.ipsec.ike.cts.IkeTunUtils;
import android.net.ConnectivityManager;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkProvider;
import android.net.NetworkRequest;
import android.net.RouteInfo;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.net.TestNetworkSpecifier;
import android.net.vcn.VcnManager;
import android.net.vcn.VcnManager.VcnNetworkPolicyChangeListener;
import android.net.vcn.VcnNetworkPolicyResult;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.CloseGuard;
import android.util.Log;

import com.android.net.module.util.NetworkStackConstants;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Utility class for cleanly creating and tearing down Test Networks. */
// TODO(b/188462344): compine with IKEv2's TestNetworkContext
public class TestNetworkWrapper implements AutoCloseable {
    private static final String TAG = TestNetworkWrapper.class.getSimpleName();
    private static final String NETWORK_AGENT_TAG = TestNetworkAgent.class.getSimpleName();
    private static final String POLICY_LISTENER_TAG =
            TestNetworkAgent.TestVcnNetworkPolicyChangeListener.class.getSimpleName();

    private static final int POLICY_CHANGE_TIMEOUT_MS = 500;
    public static final int NETWORK_CB_TIMEOUT_MS = 5000;

    private static final int IP4_PREFIX_LEN = 32;
    private static final int IP6_PREFIX_LEN = 64;

    // This NetworkRequest is expected to only match with Test Networks. To do so, remove all
    // default Capabilities and specify TRANSPORT_TEST.
    private static final NetworkRequest TEST_NETWORK_REQUEST =
            new NetworkRequest.Builder()
                    .clearCapabilities()
                    .addTransportType(NetworkCapabilities.TRANSPORT_TEST)
                    .build();

    private static final String NETWORK_PROVIDER_NAME = "TestNetworkProvider";
    private static final int TEST_NETWORK_SCORE = 1; // Use a low, non-zero score.
    private static final Executor INLINE_EXECUTOR = Runnable::run;

    private final CloseGuard mCloseGuard = new CloseGuard();

    private final ConnectivityManager mConnectivityManager;
    private final VcnManager mVcnManager;
    private final TestNetworkManager mTestNetworkManager;

    private final TestNetworkAgent mTestNetworkAgent;

    public final VcnTestNetworkCallback vcnNetworkCallback;
    public final ParcelFileDescriptor tunFd;
    public final IkeTunUtils ikeTunUtils;
    public final Network tunNetwork;

    public TestNetworkWrapper(
            @NonNull Context context,
            int mtu,
            @NonNull Set<Integer> capabilities,
            @NonNull Set<Integer> subIds,
            @NonNull InetAddress localAddress)
            throws Exception {
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mVcnManager = context.getSystemService(VcnManager.class);
        mTestNetworkManager = context.getSystemService(TestNetworkManager.class);

        try {
            final LinkAddress linkAddress =
                    new LinkAddress(
                            localAddress,
                            localAddress instanceof Inet4Address ? IP4_PREFIX_LEN : IP6_PREFIX_LEN);
            final TestNetworkInterface tni =
                    mTestNetworkManager.createTunInterface(Arrays.asList(linkAddress));
            tunFd = tni.getFileDescriptor();
            final String iface = tni.getInterfaceName();

            final NetworkRequest nr =
                    new NetworkRequest.Builder(TEST_NETWORK_REQUEST)
                            .setNetworkSpecifier(iface)
                            .build();
            vcnNetworkCallback = new VcnTestNetworkCallback();
            mConnectivityManager.requestNetwork(nr, vcnNetworkCallback);

            // Build TestNetworkAgent
            final NetworkCapabilities nc =
                    createNetworkCapabilitiesForIface(iface, capabilities, subIds);
            final LinkProperties lp = createLinkPropertiesForIface(iface, mtu);

            final VcnNetworkPolicyResult policy = mVcnManager.applyVcnNetworkPolicy(nc, lp);
            if (policy.isTeardownRequested()) {
                throw new IllegalStateException("Restart requested in bringup");
            }

            mTestNetworkAgent =
                    new TestNetworkAgent(
                            context, Looper.getMainLooper(), policy.getNetworkCapabilities(), lp);
            mTestNetworkAgent.register();
            mTestNetworkAgent.markConnected();

            tunNetwork = vcnNetworkCallback.waitForAvailable();
            ikeTunUtils = new IkeTunUtils(tunFd);
            mCloseGuard.open(TAG);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bring up TestNetworkWrapper", e);
            close();
            throw e;
        }
    }

    private static NetworkCapabilities createNetworkCapabilitiesForIface(
            @NonNull String iface, Set<Integer> capabilities, Set<Integer> subIds) {
        NetworkCapabilities.Builder builder =
                NetworkCapabilities.Builder.withoutDefaultCapabilities()
                        .addTransportType(NetworkCapabilities.TRANSPORT_TEST)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                        .setNetworkSpecifier(new TestNetworkSpecifier(iface))
                        .setSubscriptionIds(subIds);
        for (int cap : capabilities) {
            builder.addCapability(cap);
        }

        return builder.build();
    }

    private static LinkProperties createLinkPropertiesForIface(@NonNull String iface, int mtu)
            throws Exception {
        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(iface);
        lp.setMtu(mtu);

        // Find the currently assigned addresses, and add them to LinkProperties
        boolean allowIPv4 = false;
        boolean allowIPv6 = false;
        NetworkInterface netIntf = NetworkInterface.getByName(iface);
        Objects.requireNonNull(netIntf, "No such network interface found: " + netIntf);

        for (InterfaceAddress intfAddr : netIntf.getInterfaceAddresses()) {
            lp.addLinkAddress(
                    new LinkAddress(intfAddr.getAddress(), intfAddr.getNetworkPrefixLength()));

            if (intfAddr.getAddress() instanceof Inet6Address) {
                allowIPv6 |= !intfAddr.getAddress().isLinkLocalAddress();
            } else if (intfAddr.getAddress() instanceof Inet4Address) {
                allowIPv4 = true;
            }
        }

        // Add global routes (but as non-default, non-internet providing network). Use prefix
        // lengths of 0 to match all IP addresses.
        if (allowIPv4) {
            lp.addRoute(
                    new RouteInfo(
                            new IpPrefix(NetworkStackConstants.IPV4_ADDR_ANY, 0 /* prefixLength */),
                            null /* gateway */,
                            iface,
                            RouteInfo.RTN_UNICAST));
        }
        if (allowIPv6) {
            lp.addRoute(
                    new RouteInfo(
                            new IpPrefix(NetworkStackConstants.IPV6_ADDR_ANY, 0 /* prefixLength */),
                            null /* gateway */,
                            iface,
                            RouteInfo.RTN_UNICAST));
        }

        return lp;
    }

    @Override
    public void close() {
        mCloseGuard.close();

        if (vcnNetworkCallback != null) {
            try {
                mConnectivityManager.unregisterNetworkCallback(vcnNetworkCallback);
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister Network CB", e);
            }
        }

        if (mTestNetworkAgent != null) {
            synchronized (mTestNetworkAgent) {
                try {
                    mTestNetworkAgent.teardown();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to unregister TestNetworkAgent", e);
                }
            }
        }

        if (tunNetwork != null) {
            try {
                mTestNetworkManager.teardownTestNetwork(tunNetwork);
            } catch (Exception e) {
                Log.e(TAG, "Failed to tear down Test Network", e);
            }
        }

        if (tunFd != null) {
            try {
                tunFd.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to close Test Network FD", e);
            }
        }
    }

    @Override
    public void finalize() {
        mCloseGuard.warnIfOpen();
        close();
    }

    public VcnNetworkPolicyResult awaitVcnNetworkPolicyChange() throws Exception {
        return mTestNetworkAgent.mPolicyListener.awaitPolicyChange();
    }

    /**
     * Test-only NetworkAgent to be used for instrumented TUN Networks.
     *
     * <p>TestNetworkAgent is NOT THREAD SAFE - all accesses should be synchronized.
     */
    private class TestNetworkAgent extends NetworkAgent {
        private final CloseGuard mCloseGuard = new CloseGuard();
        private final TestVcnNetworkPolicyChangeListener mPolicyListener =
                new TestVcnNetworkPolicyChangeListener();

        private final LinkProperties mLinkProperties;

        private NetworkCapabilities mNetworkCapabilities;

        private TestNetworkAgent(
                @NonNull Context context,
                @NonNull Looper looper,
                @NonNull NetworkCapabilities nc,
                @NonNull LinkProperties lp) {
            super(
                    context,
                    looper,
                    NETWORK_AGENT_TAG,
                    nc,
                    lp,
                    TEST_NETWORK_SCORE,
                    new NetworkAgentConfig.Builder().build(),
                    new NetworkProvider(context, looper, NETWORK_PROVIDER_NAME));

            mNetworkCapabilities = nc;
            mLinkProperties = lp;

            mVcnManager.addVcnNetworkPolicyChangeListener(INLINE_EXECUTOR, mPolicyListener);

            mCloseGuard.open(NETWORK_AGENT_TAG);
        }

        @Override
        public void finalize() {
            mCloseGuard.warnIfOpen();
            teardown();
        }

        @Override
        public void onNetworkUnwanted() {
            // Not guaranteed to be called from the same thread, so synchronize on this.
            synchronized (this) {
                teardown();
            }
        }

        private void teardown() {
            mCloseGuard.close();
            unregister();
            mVcnManager.removeVcnNetworkPolicyChangeListener(mPolicyListener);
        }

        private NetworkCapabilities getNetworkCapabilities() {
            return mNetworkCapabilities;
        }

        private void updateNetworkCapabilities(@NonNull NetworkCapabilities nc) {
            Objects.requireNonNull(nc, "nc must be non-null");

            mNetworkCapabilities = nc;
            sendNetworkCapabilities(mNetworkCapabilities);
        }

        private LinkProperties getLinkProperties() {
            return mLinkProperties;
        }

        public class TestVcnNetworkPolicyChangeListener implements VcnNetworkPolicyChangeListener {
            private final CompletableFuture<VcnNetworkPolicyResult> mFutureOnPolicyChanged =
                    new CompletableFuture<>();

            @Override
            public void onPolicyChanged() {
                synchronized (TestNetworkAgent.this) {
                    final VcnNetworkPolicyResult policy =
                            mVcnManager.applyVcnNetworkPolicy(
                                    mTestNetworkAgent.getNetworkCapabilities(),
                                    mTestNetworkAgent.getLinkProperties());

                    mFutureOnPolicyChanged.complete(policy);
                    if (policy.isTeardownRequested()) {
                        Log.w(POLICY_LISTENER_TAG, "network teardown requested on policy change");
                        teardown();
                        return;
                    }

                    updateNetworkCapabilities(policy.getNetworkCapabilities());
                }
            }

            public VcnNetworkPolicyResult awaitPolicyChange() throws Exception {
                return mFutureOnPolicyChanged.get(POLICY_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    /** NetworkCallback to used for tracking test network events. */
    // TODO(b/187231331): remove once TestNetworkCallback supports tracking NetworkCapabilities
    public static class VcnTestNetworkCallback extends TestNetworkCallback {
        private final BlockingQueue<Network> mAvailableHistory = new LinkedBlockingQueue<>();
        private final BlockingQueue<Network> mLostHistory = new LinkedBlockingQueue<>();
        private final BlockingQueue<CapabilitiesChangedEvent> mCapabilitiesChangedHistory =
                new LinkedBlockingQueue<>();

        @Override
        public Network waitForAvailable() throws InterruptedException {
            return mAvailableHistory.poll(NETWORK_CB_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        @Override
        public Network waitForLost() throws InterruptedException {
            return mLostHistory.poll(NETWORK_CB_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        public CapabilitiesChangedEvent waitForOnCapabilitiesChanged() throws Exception {
            return waitForOnCapabilitiesChanged(NETWORK_CB_TIMEOUT_MS);
        }

        public CapabilitiesChangedEvent waitForOnCapabilitiesChanged(long timeoutMillis)
                throws Exception {
            return mCapabilitiesChangedHistory.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        public void clearLostHistory() {
            mLostHistory.clear();
        }

        @Override
        public void onAvailable(@NonNull Network network) {
            mAvailableHistory.offer(network);
        }

        @Override
        public void onLost(@NonNull Network network) {
            mLostHistory.offer(network);
        }

        @Override
        public void onCapabilitiesChanged(
                @NonNull Network network, @NonNull NetworkCapabilities nc) {
            mCapabilitiesChangedHistory.offer(new CapabilitiesChangedEvent(network, nc));
        }

        public class CapabilitiesChangedEvent {
            public final Network network;
            public final NetworkCapabilities networkCapabilities;

            public CapabilitiesChangedEvent(
                    @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                this.network = network;
                this.networkCapabilities = networkCapabilities;
            }
        }
    }
}
