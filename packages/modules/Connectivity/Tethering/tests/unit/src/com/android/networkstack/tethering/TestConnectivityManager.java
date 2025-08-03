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

package com.android.networkstack.tethering;

import static android.net.NetworkCapabilities.NET_CAPABILITY_DUN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;

import static com.android.networkstack.apishim.common.ShimUtils.isAtLeastS;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.IConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Simulates upstream switching and sending NetworkCallbacks and CONNECTIVITY_ACTION broadcasts.
 *
 * Unlike any real networking code, this class is single-threaded and entirely synchronous.
 * The effects of all method calls (including sending fake broadcasts, sending callbacks, etc.) are
 * performed immediately on the caller's thread before returning.
 *
 * TODO: this duplicates a fair amount of code from ConnectivityManager and ConnectivityService.
 * Consider using a ConnectivityService object instead, as used in ConnectivityServiceTest.
 *
 * Things to consider:
 * - ConnectivityService uses a real handler for realism, and these test use TestLooper (or even
 *   invoke callbacks directly inline) for determinism. Using a real ConnectivityService would
 *   require adding dispatchAll() calls and migrating to handlers.
 * - ConnectivityService does not provide a way to order CONNECTIVITY_ACTION before or after the
 *   NetworkCallbacks for the same network change. That ability is useful because the upstream
 *   selection code in Tethering is vulnerable to race conditions, due to its reliance on multiple
 *   separate NetworkCallbacks and BroadcastReceivers, each of which trigger different types of
 *   updates. If/when the upstream selection code is refactored to a more level-triggered model
 *   (e.g., with an idempotent function that takes into account all state every time any part of
 *   that state changes), this may become less important or unnecessary.
 */
public class TestConnectivityManager extends ConnectivityManager {
    public static final boolean BROADCAST_FIRST = false;
    public static final boolean CALLBACKS_FIRST = true;

    final Map<NetworkCallback, Handler> mAllCallbacks = new ArrayMap<>();
    // This contains the callbacks tracking the system default network, whether it's registered
    // with registerSystemDefaultNetworkCallback (S+) or with a custom request (R-).
    final Map<NetworkCallback, Handler> mTrackingDefault = new ArrayMap<>();
    final Map<NetworkCallback, NetworkRequestInfo> mListening = new ArrayMap<>();
    final Map<NetworkCallback, NetworkRequestInfo> mRequested = new ArrayMap<>();
    final Map<NetworkCallback, Integer> mLegacyTypeMap = new ArrayMap<>();

    private final Context mContext;

    private int mNetworkId = 100;
    private TestNetworkAgent mDefaultNetwork = null;

    /**
     * Constructs a TestConnectivityManager.
     * @param ctx the context to use. Must be a fake or a mock because otherwise the test will
     *            attempt to send real broadcasts and resulting in permission denials.
     * @param svc an IConnectivityManager. Should be a fake or a mock.
     */
    public TestConnectivityManager(Context ctx, IConnectivityManager svc) {
        super(ctx, svc);
        mContext = ctx;
    }

    static class NetworkRequestInfo {
        public final NetworkRequest request;
        public final Handler handler;
        NetworkRequestInfo(NetworkRequest r, Handler h) {
            request = r;
            handler = h;
        }
    }

    boolean hasNoCallbacks() {
        return mAllCallbacks.isEmpty()
                && mTrackingDefault.isEmpty()
                && mListening.isEmpty()
                && mRequested.isEmpty()
                && mLegacyTypeMap.isEmpty();
    }

    boolean onlyHasDefaultCallbacks() {
        return (mAllCallbacks.size() == 1)
                && (mTrackingDefault.size() == 1)
                && mListening.isEmpty()
                && mRequested.isEmpty()
                && mLegacyTypeMap.isEmpty();
    }

    boolean isListeningForAll() {
        final NetworkCapabilities empty = new NetworkCapabilities();
        empty.clearAll();

        for (NetworkRequestInfo nri : mListening.values()) {
            if (nri.request.networkCapabilities.equalRequestableCapabilities(empty)) {
                return true;
            }
        }
        return false;
    }

    int getNetworkId() {
        return ++mNetworkId;
    }

    private void sendDefaultNetworkBroadcasts(TestNetworkAgent formerDefault,
            TestNetworkAgent defaultNetwork) {
        if (formerDefault != null) {
            sendConnectivityAction(formerDefault.legacyType, false /* connected */);
        }
        if (defaultNetwork != null) {
            sendConnectivityAction(defaultNetwork.legacyType, true /* connected */);
        }
    }

    private void sendDefaultNetworkCallbacks(TestNetworkAgent formerDefault,
            TestNetworkAgent defaultNetwork) {
        for (NetworkCallback cb : mTrackingDefault.keySet()) {
            final Handler handler = mTrackingDefault.get(cb);
            if (defaultNetwork != null) {
                handler.post(() -> cb.onAvailable(defaultNetwork.networkId));
                handler.post(() -> cb.onCapabilitiesChanged(
                        defaultNetwork.networkId, defaultNetwork.networkCapabilities));
                handler.post(() -> cb.onLinkPropertiesChanged(
                        defaultNetwork.networkId, defaultNetwork.linkProperties));
            } else if (formerDefault != null) {
                handler.post(() -> cb.onLost(formerDefault.networkId));
            }
        }
    }

    void makeDefaultNetwork(TestNetworkAgent agent, boolean order, @Nullable Runnable inBetween) {
        if (Objects.equals(mDefaultNetwork, agent)) return;

        final TestNetworkAgent formerDefault = mDefaultNetwork;
        mDefaultNetwork = agent;

        if (order == CALLBACKS_FIRST) {
            sendDefaultNetworkCallbacks(formerDefault, mDefaultNetwork);
            if (inBetween != null) inBetween.run();
            sendDefaultNetworkBroadcasts(formerDefault, mDefaultNetwork);
        } else {
            sendDefaultNetworkBroadcasts(formerDefault, mDefaultNetwork);
            if (inBetween != null) inBetween.run();
            sendDefaultNetworkCallbacks(formerDefault, mDefaultNetwork);
        }
    }

    void makeDefaultNetwork(TestNetworkAgent agent, boolean order) {
        makeDefaultNetwork(agent, order, null /* inBetween */);
    }

    void makeDefaultNetwork(TestNetworkAgent agent) {
        makeDefaultNetwork(agent, BROADCAST_FIRST, null /* inBetween */);
    }

    void sendLinkProperties(TestNetworkAgent agent, boolean updateDefaultFirst) {
        if (!updateDefaultFirst) agent.sendLinkProperties();

        for (NetworkCallback cb : mTrackingDefault.keySet()) {
            cb.onLinkPropertiesChanged(agent.networkId, agent.linkProperties);
        }

        if (updateDefaultFirst) agent.sendLinkProperties();
    }

    static boolean looksLikeDefaultRequest(NetworkRequest req) {
        return req.hasCapability(NET_CAPABILITY_INTERNET)
                && !req.hasCapability(NET_CAPABILITY_DUN)
                && !req.hasTransport(TRANSPORT_CELLULAR);
    }

    @Override
    public void requestNetwork(NetworkRequest req, NetworkCallback cb, Handler h) {
        // For R- devices, Tethering will invoke this function in 2 cases, one is to request mobile
        // network, the other is to track system default network.
        if (looksLikeDefaultRequest(req)) {
            assertFalse(isAtLeastS());
            addTrackDefaultCallback(cb, h);
        } else {
            assertFalse(mAllCallbacks.containsKey(cb));
            mAllCallbacks.put(cb, h);
            assertFalse(mRequested.containsKey(cb));
            mRequested.put(cb, new NetworkRequestInfo(req, h));
        }
    }

    @Override
    public void registerSystemDefaultNetworkCallback(
            @NonNull NetworkCallback cb, @NonNull Handler h) {
        addTrackDefaultCallback(cb, h);
    }

    private void addTrackDefaultCallback(@NonNull NetworkCallback cb, @NonNull Handler h) {
        assertFalse(mAllCallbacks.containsKey(cb));
        mAllCallbacks.put(cb, h);
        assertFalse(mTrackingDefault.containsKey(cb));
        mTrackingDefault.put(cb, h);
    }

    @Override
    public void requestNetwork(NetworkRequest req, NetworkCallback cb) {
        fail("Should never be called.");
    }

    @Override
    public void requestNetwork(NetworkRequest req,
            int timeoutMs, int legacyType, Handler h, NetworkCallback cb) {
        assertFalse(mAllCallbacks.containsKey(cb));
        NetworkRequest newReq = new NetworkRequest(req.networkCapabilities, legacyType,
                -1 /** testId */, req.type);
        mAllCallbacks.put(cb, h);
        assertFalse(mRequested.containsKey(cb));
        mRequested.put(cb, new NetworkRequestInfo(newReq, h));
        assertFalse(mLegacyTypeMap.containsKey(cb));
        if (legacyType != ConnectivityManager.TYPE_NONE) {
            mLegacyTypeMap.put(cb, legacyType);
        }
    }

    @Override
    public void registerNetworkCallback(NetworkRequest req, NetworkCallback cb, Handler h) {
        assertFalse(mAllCallbacks.containsKey(cb));
        mAllCallbacks.put(cb, h);
        assertFalse(mListening.containsKey(cb));
        mListening.put(cb, new NetworkRequestInfo(req, h));
    }

    @Override
    public void registerNetworkCallback(NetworkRequest req, NetworkCallback cb) {
        fail("Should never be called.");
    }

    @Override
    public void registerDefaultNetworkCallback(NetworkCallback cb, Handler h) {
        fail("Should never be called.");
    }

    @Override
    public void registerDefaultNetworkCallback(NetworkCallback cb) {
        fail("Should never be called.");
    }

    @Override
    public void unregisterNetworkCallback(NetworkCallback cb) {
        if (mTrackingDefault.containsKey(cb)) {
            mTrackingDefault.remove(cb);
        } else if (mListening.containsKey(cb)) {
            mListening.remove(cb);
        } else if (mRequested.containsKey(cb)) {
            mRequested.remove(cb);
            mLegacyTypeMap.remove(cb);
        } else {
            fail("Unexpected callback removed");
        }
        mAllCallbacks.remove(cb);

        assertFalse(mAllCallbacks.containsKey(cb));
        assertFalse(mTrackingDefault.containsKey(cb));
        assertFalse(mListening.containsKey(cb));
        assertFalse(mRequested.containsKey(cb));
    }

    private void sendConnectivityAction(int type, boolean connected) {
        NetworkInfo ni = new NetworkInfo(type, 0 /* subtype */,  getNetworkTypeName(type),
                "" /* subtypeName */);
        NetworkInfo.DetailedState state = connected
                ? NetworkInfo.DetailedState.CONNECTED
                : NetworkInfo.DetailedState.DISCONNECTED;
        ni.setDetailedState(state, "" /* reason */, "" /* extraInfo */);
        Intent intent = new Intent(CONNECTIVITY_ACTION);
        intent.putExtra(EXTRA_NETWORK_INFO, ni);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    public static class TestNetworkAgent {
        public final TestConnectivityManager cm;
        public final Network networkId;
        public final NetworkCapabilities networkCapabilities;
        public final LinkProperties linkProperties;
        // TODO: delete when tethering no longer uses CONNECTIVITY_ACTION.
        public final int legacyType;

        public TestNetworkAgent(TestConnectivityManager cm, NetworkCapabilities nc) {
            this.cm = cm;
            this.networkId = new Network(cm.getNetworkId());
            networkCapabilities = copy(nc);
            linkProperties = new LinkProperties();
            legacyType = toLegacyType(nc);
        }

        public TestNetworkAgent(TestConnectivityManager cm, UpstreamNetworkState state) {
            this.cm = cm;
            networkId = state.network;
            networkCapabilities = state.networkCapabilities;
            linkProperties = state.linkProperties;
            this.legacyType = toLegacyType(networkCapabilities);
        }

        private static int toLegacyType(NetworkCapabilities nc) {
            for (int type = 0; type < ConnectivityManager.TYPE_TEST; type++) {
                if (matchesLegacyType(nc, type)) return type;
            }
            throw new IllegalArgumentException(("Can't determine legacy type for: ") + nc);
        }

        private static boolean matchesLegacyType(NetworkCapabilities nc, int legacyType) {
            final NetworkCapabilities typeNc;
            try {
                typeNc = ConnectivityManager.networkCapabilitiesForType(legacyType);
            } catch (IllegalArgumentException e) {
                // networkCapabilitiesForType does not support all legacy types.
                return false;
            }
            return typeNc.satisfiedByNetworkCapabilities(nc);
        }

        private boolean matchesLegacyType(int legacyType) {
            return matchesLegacyType(networkCapabilities, legacyType);
        }

        private void maybeSendConnectivityBroadcast(boolean connected) {
            for (Integer requestedLegacyType : cm.mLegacyTypeMap.values()) {
                if (requestedLegacyType.intValue() == legacyType) {
                    cm.sendConnectivityAction(legacyType, connected /* connected */);
                    // In practice, a given network can match only one legacy type.
                    break;
                }
            }
        }

        public void fakeConnect() {
            fakeConnect(BROADCAST_FIRST, null);
        }

        public void fakeConnect(boolean order, @Nullable Runnable inBetween) {
            if (order == BROADCAST_FIRST) {
                maybeSendConnectivityBroadcast(true /* connected */);
                if (inBetween != null) inBetween.run();
            }

            for (NetworkCallback cb : cm.mListening.keySet()) {
                final NetworkRequestInfo nri = cm.mListening.get(cb);
                nri.handler.post(() -> cb.onAvailable(networkId));
                nri.handler.post(() -> cb.onCapabilitiesChanged(
                        networkId, copy(networkCapabilities)));
                nri.handler.post(() -> cb.onLinkPropertiesChanged(networkId, copy(linkProperties)));
            }

            if (order == CALLBACKS_FIRST) {
                if (inBetween != null) inBetween.run();
                maybeSendConnectivityBroadcast(true /* connected */);
            }
            // mTrackingDefault will be updated if/when the caller calls makeDefaultNetwork
        }

        public void fakeDisconnect() {
            fakeDisconnect(BROADCAST_FIRST, null);
        }

        public void fakeDisconnect(boolean order, @Nullable Runnable inBetween) {
            if (order == BROADCAST_FIRST) {
                maybeSendConnectivityBroadcast(false /* connected */);
                if (inBetween != null) inBetween.run();
            }

            for (NetworkCallback cb : cm.mListening.keySet()) {
                cb.onLost(networkId);
            }

            if (order == CALLBACKS_FIRST) {
                if (inBetween != null) inBetween.run();
                maybeSendConnectivityBroadcast(false /* connected */);
            }
            // mTrackingDefault will be updated if/when the caller calls makeDefaultNetwork
        }

        public void sendLinkProperties() {
            for (NetworkCallback cb : cm.mListening.keySet()) {
                cb.onLinkPropertiesChanged(networkId, copy(linkProperties));
            }
        }

        @Override
        public String toString() {
            return String.format("TestNetworkAgent: %s %s", networkId, networkCapabilities);
        }
    }

    static NetworkCapabilities copy(NetworkCapabilities nc) {
        return new NetworkCapabilities(nc);
    }

    static LinkProperties copy(LinkProperties lp) {
        return new LinkProperties(lp);
    }
}
