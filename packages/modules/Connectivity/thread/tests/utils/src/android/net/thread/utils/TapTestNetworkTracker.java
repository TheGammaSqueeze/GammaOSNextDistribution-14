/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.Manifest.permission.MANAGE_TEST_NETWORKS;
import static android.net.InetAddresses.parseNumericAddress;
import static android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED;
import static android.net.NetworkCapabilities.TRANSPORT_TEST;
import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.IPPROTO_UDP;
import static android.system.OsConstants.SOCK_DGRAM;

import static com.android.testutils.RecorderCallback.CallbackEntry.LINK_PROPERTIES_CHANGED;
import static com.android.testutils.TestPermissionUtil.runAsShell;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.net.TestNetworkSpecifier;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;

import com.android.compatibility.common.util.PollingCheck;
import com.android.testutils.TestableNetworkAgent;
import com.android.testutils.TestableNetworkCallback;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A class that can create/destroy a test network based on TAP interface. */
public final class TapTestNetworkTracker {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private final Context mContext;
    private final Looper mLooper;
    private TestNetworkInterface mInterface;
    private TestableNetworkAgent mAgent;
    private final TestableNetworkCallback mNetworkCallback;
    private final ConnectivityManager mConnectivityManager;

    /**
     * Constructs a {@link TapTestNetworkTracker}.
     *
     * <p>It creates a TAP interface (e.g. testtap0) and registers a test network using that
     * interface. It also requests the test network by {@link ConnectivityManager#requestNetwork} so
     * the test network won't be automatically turned down by {@link
     * com.android.server.ConnectivityService}.
     */
    public TapTestNetworkTracker(Context context, Looper looper) {
        mContext = context;
        mLooper = looper;
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mNetworkCallback = new TestableNetworkCallback();
        runAsShell(MANAGE_TEST_NETWORKS, this::setUpTestNetwork);
    }

    /** Tears down the test network. */
    public void tearDown() {
        runAsShell(MANAGE_TEST_NETWORKS, this::tearDownTestNetwork);
    }

    /** Returns the interface name of the test network. */
    public String getInterfaceName() {
        return mInterface.getInterfaceName();
    }

    private void setUpTestNetwork() throws Exception {
        mInterface = mContext.getSystemService(TestNetworkManager.class).createTapInterface();

        mConnectivityManager.requestNetwork(newNetworkRequest(), mNetworkCallback);

        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(getInterfaceName());
        mAgent =
                new TestableNetworkAgent(
                        mContext,
                        mLooper,
                        newNetworkCapabilities(),
                        lp,
                        new NetworkAgentConfig.Builder().build());
        final Network network = mAgent.register();
        mAgent.markConnected();

        PollingCheck.check(
                "No usable address on interface",
                TIMEOUT.toMillis(),
                () -> hasUsableAddress(network, getInterfaceName()));

        lp.setLinkAddresses(makeLinkAddresses());
        mAgent.sendLinkProperties(lp);
        mNetworkCallback.eventuallyExpect(
                LINK_PROPERTIES_CHANGED,
                TIMEOUT.toMillis(),
                l -> !l.getLp().getAddresses().isEmpty());
    }

    private void tearDownTestNetwork() throws IOException {
        mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
        mAgent.unregister();
        mInterface.getFileDescriptor().close();
        mAgent.waitForIdle(TIMEOUT.toMillis());
    }

    private NetworkRequest newNetworkRequest() {
        return new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_TRUSTED)
                .addTransportType(TRANSPORT_TEST)
                .setNetworkSpecifier(new TestNetworkSpecifier(getInterfaceName()))
                .build();
    }

    private NetworkCapabilities newNetworkCapabilities() {
        return new NetworkCapabilities()
                .removeCapability(NET_CAPABILITY_TRUSTED)
                .addTransportType(TRANSPORT_TEST)
                .setNetworkSpecifier(new TestNetworkSpecifier(getInterfaceName()));
    }

    private List<LinkAddress> makeLinkAddresses() {
        List<LinkAddress> linkAddresses = new ArrayList<>();
        List<InterfaceAddress> interfaceAddresses = Collections.emptyList();

        try {
            interfaceAddresses =
                    NetworkInterface.getByName(getInterfaceName()).getInterfaceAddresses();
        } catch (SocketException ignored) {
            // Ignore failures when getting the addresses.
        }

        for (InterfaceAddress address : interfaceAddresses) {
            linkAddresses.add(
                    new LinkAddress(address.getAddress(), address.getNetworkPrefixLength()));
        }

        return linkAddresses;
    }

    private static boolean hasUsableAddress(Network network, String interfaceName) {
        try {
            if (NetworkInterface.getByName(interfaceName).getInterfaceAddresses().isEmpty()) {
                return false;
            }
        } catch (SocketException e) {
            return false;
        }
        // Check if the link-local address can be used. Address flags are not available without
        // elevated permissions, so check that bindSocket works.
        try {
            FileDescriptor sock = Os.socket(AF_INET6, SOCK_DGRAM, IPPROTO_UDP);
            network.bindSocket(sock);
            Os.connect(sock, parseNumericAddress("ff02::fb%" + interfaceName), 12345);
            Os.close(sock);
        } catch (ErrnoException | IOException e) {
            return false;
        }
        return true;
    }
}
