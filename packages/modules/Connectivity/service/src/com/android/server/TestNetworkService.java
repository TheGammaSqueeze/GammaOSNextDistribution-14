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

package com.android.server;

import static android.net.TestNetworkManager.CLAT_INTERFACE_PREFIX;
import static android.net.TestNetworkManager.TEST_TAP_PREFIX;
import static android.net.TestNetworkManager.TEST_TUN_PREFIX;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.INetd;
import android.net.ITestNetworkManager;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkProvider;
import android.net.RouteInfo;
import android.net.TestNetworkInterface;
import android.net.TestNetworkSpecifier;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.NetworkStackConstants;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** @hide */
class TestNetworkService extends ITestNetworkManager.Stub {
    @NonNull private static final String TEST_NETWORK_LOGTAG = "TestNetworkAgent";
    @NonNull private static final String TEST_NETWORK_PROVIDER_NAME = "TestNetworkProvider";
    @NonNull private static final AtomicInteger sTestTunIndex = new AtomicInteger();

    @NonNull private final Context mContext;
    @NonNull private final INetd mNetd;

    @NonNull private final HandlerThread mHandlerThread;
    @NonNull private final Handler mHandler;

    @NonNull private final ConnectivityManager mCm;
    @NonNull private final NetworkProvider mNetworkProvider;

    // Native method stubs
    private static native int nativeCreateTunTap(boolean isTun, boolean hasCarrier,
            boolean setIffMulticast, @NonNull String iface);

    private static native void nativeSetTunTapCarrierEnabled(@NonNull String iface, int tunFd,
            boolean enabled);

    private static native void nativeBringUpInterface(String iface);

    @VisibleForTesting
    protected TestNetworkService(@NonNull Context context) {
        mHandlerThread = new HandlerThread("TestNetworkServiceThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mContext = Objects.requireNonNull(context, "missing Context");
        mNetd = Objects.requireNonNull(
                INetd.Stub.asInterface((IBinder) context.getSystemService(Context.NETD_SERVICE)),
                "could not get netd instance");
        mCm = mContext.getSystemService(ConnectivityManager.class);
        mNetworkProvider = new NetworkProvider(mContext, mHandler.getLooper(),
                TEST_NETWORK_PROVIDER_NAME);
        final long token = Binder.clearCallingIdentity();
        try {
            mCm.registerNetworkProvider(mNetworkProvider);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    // TODO: find a way to allow the caller to pass in non-clat interface names, ensuring that
    // those names do not conflict with names created by callers that do not pass in an interface
    // name.
    private static boolean isValidInterfaceName(@NonNull final String iface) {
        return iface.startsWith(CLAT_INTERFACE_PREFIX + TEST_TUN_PREFIX)
                || iface.startsWith(CLAT_INTERFACE_PREFIX + TEST_TAP_PREFIX);
    }

    /**
     * Create a TUN or TAP interface with the specified parameters.
     *
     * <p>This method will return the FileDescriptor to the interface. Close it to tear down the
     * interface.
     */
    @Override
    public TestNetworkInterface createInterface(boolean isTun, boolean hasCarrier, boolean bringUp,
            boolean disableIpv6ProvisioningDelay, LinkAddress[] linkAddrs, @Nullable String iface) {
        enforceTestNetworkPermissions(mContext);

        Objects.requireNonNull(linkAddrs, "missing linkAddrs");

        String interfaceName = iface;
        if (iface == null) {
            String ifacePrefix = isTun ? TEST_TUN_PREFIX : TEST_TAP_PREFIX;
            interfaceName = ifacePrefix + sTestTunIndex.getAndIncrement();
        } else if (!isValidInterfaceName(iface)) {
            throw new IllegalArgumentException("invalid interface name requested: " + iface);
        }

        final long token = Binder.clearCallingIdentity();
        try {
            // Note: if the interface is brought up by ethernet, setting IFF_MULTICAST
            // races NetUtils#setInterfaceUp(). This flag is not necessary for ethernet
            // tests, so let's not set it when bringUp is false. See also b/242343156.
            // In the future, we could use RTM_SETLINK with ifi_change set to set the
            // flags atomically.
            final boolean setIffMulticast = bringUp;
            ParcelFileDescriptor tunIntf = ParcelFileDescriptor.adoptFd(
                    nativeCreateTunTap(isTun, hasCarrier, setIffMulticast, interfaceName));

            // Disable DAD and remove router_solicitation_delay before assigning link addresses.
            if (disableIpv6ProvisioningDelay) {
                mNetd.setProcSysNet(
                        INetd.IPV6, INetd.CONF, interfaceName, "router_solicitation_delay", "0");
                mNetd.setProcSysNet(INetd.IPV6, INetd.CONF, interfaceName, "dad_transmits", "0");
            }

            for (LinkAddress addr : linkAddrs) {
                mNetd.interfaceAddAddress(
                        interfaceName,
                        addr.getAddress().getHostAddress(),
                        addr.getPrefixLength());
            }

            if (bringUp) {
                nativeBringUpInterface(interfaceName);
            }

            return new TestNetworkInterface(tunIntf, interfaceName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    // Tracker for TestNetworkAgents
    @GuardedBy("mTestNetworkTracker")
    @NonNull
    private final SparseArray<TestNetworkAgent> mTestNetworkTracker = new SparseArray<>();

    public class TestNetworkAgent extends NetworkAgent implements IBinder.DeathRecipient {
        private static final int NETWORK_SCORE = 1; // Use a low, non-zero score.

        private final int mUid;

        @GuardedBy("mBinderLock")
        @NonNull
        private IBinder mBinder;

        @NonNull private final Object mBinderLock = new Object();

        private TestNetworkAgent(
                @NonNull Context context,
                @NonNull Looper looper,
                @NonNull NetworkCapabilities nc,
                @NonNull LinkProperties lp,
                @NonNull NetworkAgentConfig config,
                int uid,
                @NonNull IBinder binder,
                @NonNull NetworkProvider np)
                throws RemoteException {
            super(context, looper, TEST_NETWORK_LOGTAG, nc, lp, NETWORK_SCORE, config, np);
            mUid = uid;
            synchronized (mBinderLock) {
                mBinder = binder; // Binder null-checks in create()

                try {
                    mBinder.linkToDeath(this, 0);
                } catch (RemoteException e) {
                    binderDied();
                    throw e; // Abort, signal failure up the stack.
                }
            }
        }

        /**
         * If the Binder object dies, this function is called to free the resources of this
         * TestNetworkAgent
         */
        @Override
        public void binderDied() {
            teardown();
        }

        @Override
        protected void unwanted() {
            teardown();
        }

        private void teardown() {
            unregister();

            // Synchronize on mBinderLock to ensure that unlinkToDeath is never called more than
            // once (otherwise it could throw an exception)
            synchronized (mBinderLock) {
                // If mBinder is null, this Test Network has already been cleaned up.
                if (mBinder == null) return;
                mBinder.unlinkToDeath(this, 0);
                mBinder = null;
            }

            // Has to be in TestNetworkAgent to ensure all teardown codepaths properly clean up
            // resources, even for binder death or unwanted calls.
            synchronized (mTestNetworkTracker) {
                mTestNetworkTracker.remove(getNetwork().getNetId());
            }
        }
    }

    private TestNetworkAgent registerTestNetworkAgent(
            @NonNull Looper looper,
            @NonNull Context context,
            @NonNull String iface,
            @Nullable LinkProperties lp,
            boolean isMetered,
            int callingUid,
            @NonNull int[] administratorUids,
            @NonNull IBinder binder)
            throws RemoteException, SocketException {
        Objects.requireNonNull(looper, "missing Looper");
        Objects.requireNonNull(context, "missing Context");
        // iface and binder validity checked by caller

        // Build narrow set of NetworkCapabilities, useful only for testing
        NetworkCapabilities nc = new NetworkCapabilities();
        nc.clearAll(); // Remove default capabilities.
        nc.addTransportType(NetworkCapabilities.TRANSPORT_TEST);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
        nc.setNetworkSpecifier(new TestNetworkSpecifier(iface));
        nc.setAdministratorUids(administratorUids);
        if (!isMetered) {
            nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        }

        // Build LinkProperties
        if (lp == null) {
            lp = new LinkProperties();
        } else {
            lp = new LinkProperties(lp);
            // Use LinkAddress(es) from the interface itself to minimize how much the caller
            // is trusted.
            lp.setLinkAddresses(new ArrayList<>());
        }
        lp.setInterfaceName(iface);

        // Find the currently assigned addresses, and add them to LinkProperties
        boolean allowIPv4 = false, allowIPv6 = false;
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

        // Add global routes (but as non-default, non-internet providing network)
        if (allowIPv4) {
            lp.addRoute(new RouteInfo(new IpPrefix(
                    NetworkStackConstants.IPV4_ADDR_ANY, 0), null, iface));
        }
        if (allowIPv6) {
            lp.addRoute(new RouteInfo(new IpPrefix(
                    NetworkStackConstants.IPV6_ADDR_ANY, 0), null, iface));
        }

        // For testing purpose, fill legacy type for NetworkStatsService since it does not
        // support transport types.
        final TestNetworkAgent agent = new TestNetworkAgent(context, looper, nc, lp,
                new NetworkAgentConfig.Builder().setLegacyType(ConnectivityManager.TYPE_TEST)
                        .build(), callingUid, binder, mNetworkProvider);
        agent.register();
        agent.markConnected();
        return agent;
    }

    /**
     * Sets up a Network with extremely limited privileges, guarded by the MANAGE_TEST_NETWORKS
     * permission.
     *
     * <p>This method provides a Network that is useful only for testing.
     */
    @Override
    public void setupTestNetwork(
            @NonNull String iface,
            @Nullable LinkProperties lp,
            boolean isMetered,
            @NonNull int[] administratorUids,
            @NonNull IBinder binder) {
        enforceTestNetworkPermissions(mContext);

        Objects.requireNonNull(iface, "missing Iface");
        Objects.requireNonNull(binder, "missing IBinder");

        if (!(iface.startsWith(INetd.IPSEC_INTERFACE_PREFIX)
                || iface.startsWith(TEST_TUN_PREFIX))) {
            throw new IllegalArgumentException(
                    "Cannot create network for non ipsec, non-testtun interface");
        }

        try {
            // Synchronize all accesses to mTestNetworkTracker to prevent the case where:
            // 1. TestNetworkAgent successfully binds to death of binder
            // 2. Before it is added to the mTestNetworkTracker, binder dies, binderDied() is called
            // (on a different thread)
            // 3. This thread is pre-empted, put() is called after remove()
            synchronized (mTestNetworkTracker) {
                TestNetworkAgent agent =
                        registerTestNetworkAgent(
                                mHandler.getLooper(),
                                mContext,
                                iface,
                                lp,
                                isMetered,
                                Binder.getCallingUid(),
                                administratorUids,
                                binder);

                mTestNetworkTracker.put(agent.getNetwork().getNetId(), agent);
            }
        } catch (SocketException e) {
            throw new UncheckedIOException(e);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Teardown a test network */
    @Override
    public void teardownTestNetwork(int netId) {
        enforceTestNetworkPermissions(mContext);

        final TestNetworkAgent agent;
        synchronized (mTestNetworkTracker) {
            agent = mTestNetworkTracker.get(netId);
        }

        if (agent == null) {
            return; // Already torn down
        } else if (agent.mUid != Binder.getCallingUid()) {
            throw new SecurityException("Attempted to modify other user's test networks");
        }

        // Safe to be called multiple times.
        agent.teardown();
    }

    private static final String PERMISSION_NAME =
            android.Manifest.permission.MANAGE_TEST_NETWORKS;

    public static void enforceTestNetworkPermissions(@NonNull Context context) {
        context.enforceCallingOrSelfPermission(PERMISSION_NAME, "TestNetworkService");
    }

    /** Enable / disable TestNetworkInterface carrier */
    @Override
    public void setCarrierEnabled(@NonNull TestNetworkInterface iface, boolean enabled) {
        enforceTestNetworkPermissions(mContext);
        nativeSetTunTapCarrierEnabled(iface.getInterfaceName(), iface.getFileDescriptor().getFd(),
                enabled);
        // Explicitly close fd after use to prevent StrictMode from complaining.
        // Also, explicitly referencing iface guarantees that the object is not garbage collected
        // before nativeSetTunTapCarrierEnabled() executes.
        try {
            iface.getFileDescriptor().close();
        } catch (IOException e) {
            // if the close fails, there is not much that can be done -- move on.
        }
    }
}
