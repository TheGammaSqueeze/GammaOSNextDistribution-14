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

package com.google.snippet.wifi.aware;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.IdentityChangedListener;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.ServiceDiscoveryInfo;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareSession;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import com.android.compatibility.common.util.ApiLevelUtil;

import com.google.common.collect.ImmutableSet;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/** Blocking callbacks for Wi-Fi Aware and Connectivity Manager. */
public final class CallbackUtils {
    private static final String TAG = "CallbackUtils";

    public static final int CALLBACK_TIMEOUT_SEC = 15;

    /**
     * Utility AttachCallback - provides mechanism to block execution with the waitForAttach method.
     */
    public static class AttachCb extends AttachCallback {

        /** Callback codes. */
        public enum CallbackCode {
            TIMEOUT,
            ON_ATTACHED,
            ON_ATTACH_FAILED
        }

        private final CountDownLatch mBlocker = new CountDownLatch(1);
        private CallbackCode mCallbackCode = CallbackCode.TIMEOUT;
        private WifiAwareSession mWifiAwareSession = null;

        @Override
        public void onAttached(WifiAwareSession session) {
            mCallbackCode = CallbackCode.ON_ATTACHED;
            mWifiAwareSession = session;
            mBlocker.countDown();
        }

        @Override
        public void onAttachFailed() {
            mCallbackCode = CallbackCode.ON_ATTACH_FAILED;
            mBlocker.countDown();
        }

        /**
         * Wait (blocks) for any AttachCallback callback or timeout.
         *
         * @return A pair of values: the callback constant (or TIMEOUT) and the WifiAwareSession
         * created
         * when attach successful - null otherwise (attach failure or timeout).
         */
        public Pair<CallbackCode, WifiAwareSession> waitForAttach() throws InterruptedException {
            if (mBlocker.await(CALLBACK_TIMEOUT_SEC, SECONDS)) {
                return new Pair<>(mCallbackCode, mWifiAwareSession);
            }

            return new Pair<>(CallbackCode.TIMEOUT, null);
        }
    }

    /**
     * Utility IdentityChangedListener - provides mechanism to block execution with the
     * waitForIdentity method. Single shot listener - only listens for the first triggered callback.
     */
    public static class IdentityListenerSingleShot extends IdentityChangedListener {
        private final CountDownLatch mBlocker = new CountDownLatch(1);
        private byte[] mMac = null;

        @Override
        public void onIdentityChanged(byte[] mac) {
            if (this.mMac != null) {
                return;
            }

            this.mMac = mac;
            mBlocker.countDown();
        }

        /**
         * Wait (blocks) for the onIdentityChanged callback or a timeout.
         *
         * @return The MAC address returned by the onIdentityChanged() callback, or null on timeout.
         */
        public byte[] waitForMac() throws InterruptedException {
            if (mBlocker.await(CALLBACK_TIMEOUT_SEC, SECONDS)) {
                return mMac;
            }

            return null;
        }
    }

    /**
     * Utility NetworkCallback - provides mechanism for blocking/serializing access with the
     * waitForNetwork method.
     */
    public static class NetworkCb extends ConnectivityManager.NetworkCallback {
        private final CountDownLatch mBlocker = new CountDownLatch(1);
        private Network mNetwork = null;
        private NetworkCapabilities mNetworkCapabilities = null;

        @Override
        public void onUnavailable() {
            mNetworkCapabilities = null;
            mBlocker.countDown();
        }

        @Override
        public void onCapabilitiesChanged(Network network,
                NetworkCapabilities networkCapabilities) {
            this.mNetwork = network;
            this.mNetworkCapabilities = networkCapabilities;
            mBlocker.countDown();
        }

        /**
         * Wait (blocks) for Capabilities Changed callback - or timesout.
         *
         * @return Network + NetworkCapabilities (pair) if occurred, null otherwise.
         */
        public Pair<Network, NetworkCapabilities> waitForNetworkCapabilities()
                throws InterruptedException {
            if (mBlocker.await(CALLBACK_TIMEOUT_SEC, SECONDS)) {
                return Pair.create(mNetwork, mNetworkCapabilities);
            }
            return null;
        }
    }

    /**
     * Utility DiscoverySessionCallback - provides mechanism to block/serialize Aware discovery
     * operations using the waitForCallbacks() method.
     */
    public static class DiscoveryCb extends DiscoverySessionCallback {
        /** Callback codes. */
        public enum CallbackCode {
            TIMEOUT,
            ON_PUBLISH_STARTED,
            ON_SUBSCRIBE_STARTED,
            ON_SESSION_CONFIG_UPDATED,
            ON_SESSION_CONFIG_FAILED,
            ON_SESSION_TERMINATED,
            ON_SERVICE_DISCOVERED,
            ON_MESSAGE_SEND_SUCCEEDED,
            ON_MESSAGE_SEND_FAILED,
            ON_MESSAGE_RECEIVED,
            ON_SERVICE_DISCOVERED_WITH_RANGE, ON_PAIRING_REQUEST_RECEIVED,
            ON_PAIRING_SETUP_CONFIRMED, ON_BOOTSTRAPPING_CONFIRMED,
            ON_PAIRING_VERIFICATION_CONFIRMED,
        }


        ;

        /**
         * Data container for all parameters which can be returned by any DiscoverySessionCallback
         * callback.
         */
        public static class CallbackData {
            public CallbackData(CallbackCode callbackCode) {
                this.callbackCode = callbackCode;
            }

            public CallbackCode callbackCode;

            public PublishDiscoverySession publishDiscoverySession;
            public SubscribeDiscoverySession subscribeDiscoverySession;
            public PeerHandle peerHandle;
            public byte[] serviceSpecificInfo;
            public List<byte[]> matchFilter;
            public int messageId;
            public int distanceMm;
            public int pairingRequestId;
            public boolean pairingAccept;
            public boolean bootstrappingAccept;
            public String pairingAlias;
            public int bootstrappingMethod;
            public String pairedAlias;
            public AwarePairingConfig pairingConfig;
        }

        private CountDownLatch mBlocker = null;
        private Set<CallbackCode> mWaitForCallbackCodes = ImmutableSet.of();

        private final Object mLock = new Object();
        private final ArrayDeque<CallbackData> mCallbackQueue = new ArrayDeque<>();

        private void processCallback(CallbackData callbackData) {
            synchronized (mLock) {
                mCallbackQueue.addLast(callbackData);
                if (mBlocker != null && mWaitForCallbackCodes.contains(callbackData.callbackCode)) {
                    mBlocker.countDown();
                }
            }
        }

        private CallbackData getAndRemoveFirst(Set<CallbackCode> callbackCodes) {
            synchronized (mLock) {
                for (CallbackData cbd : mCallbackQueue) {
                    if (callbackCodes.contains(cbd.callbackCode)) {
                        mCallbackQueue.remove(cbd);
                        return cbd;
                    }
                }
            }

            return null;
        }

        private CallbackData waitForCallbacks(Set<CallbackCode> callbackCodes, boolean timeout)
                throws InterruptedException {
            synchronized (mLock) {
                CallbackData cbd = getAndRemoveFirst(callbackCodes);
                if (cbd != null) {
                    return cbd;
                }

                mWaitForCallbackCodes = callbackCodes;
                mBlocker = new CountDownLatch(1);
            }

            boolean finishedNormally = true;
            if (timeout) {
                finishedNormally = mBlocker.await(CALLBACK_TIMEOUT_SEC, SECONDS);
            } else {
                mBlocker.await();
            }
            if (finishedNormally) {
                CallbackData cbd = getAndRemoveFirst(callbackCodes);
                if (cbd != null) {
                    return cbd;
                }

                Log.wtf(
                        TAG,
                        "DiscoveryCb.waitForCallback: callbackCodes="
                                + callbackCodes
                                + ": did not time-out but doesn't have any of the requested "
                                + "callbacks in "
                                + "the stack!?");
                // falling-through to TIMEOUT
            }

            return new CallbackData(CallbackCode.TIMEOUT);
        }

        /**
         * Wait for the specified callbacks - a bitmask of any of the ON_* constants. Returns the
         * CallbackData structure whose CallbackData.callback specifies the callback which was
         * triggered. The callback may be TIMEOUT.
         *
         * <p>Note: other callbacks happening while while waiting for the specified callback(s) will
         * be
         * queued.
         */
        public CallbackData waitForCallbacks(Set<CallbackCode> callbackCodes)
                throws InterruptedException {
            return waitForCallbacks(callbackCodes, true);
        }

        /**
         * Wait for the specified callbacks - a bitmask of any of the ON_* constants. Returns the
         * CallbackData structure whose CallbackData.callback specifies the callback which was
         * triggered.
         *
         * <p>This call will not timeout - it can be interrupted though (which results in a thrown
         * exception).
         *
         * <p>Note: other callbacks happening while while waiting for the specified callback(s) will
         * be
         * queued.
         */
        public CallbackData waitForCallbacksNoTimeout(Set<CallbackCode> callbackCodes)
                throws InterruptedException {
            return waitForCallbacks(callbackCodes, false);
        }

        @Override
        public void onPublishStarted(PublishDiscoverySession session) {
            CallbackData callbackData = new CallbackData(CallbackCode.ON_PUBLISH_STARTED);
            callbackData.publishDiscoverySession = session;
            processCallback(callbackData);
        }

        @Override
        public void onSubscribeStarted(SubscribeDiscoverySession session) {
            CallbackData callbackData = new CallbackData(CallbackCode.ON_SUBSCRIBE_STARTED);
            callbackData.subscribeDiscoverySession = session;
            processCallback(callbackData);
        }

        @Override
        public void onSessionConfigUpdated() {
            CallbackData callbackData = new CallbackData(CallbackCode.ON_SESSION_CONFIG_UPDATED);
            processCallback(callbackData);
        }

        @Override
        public void onSessionConfigFailed() {
            CallbackData callbackData = new CallbackData(CallbackCode.ON_SESSION_CONFIG_FAILED);
            processCallback(callbackData);
        }

        @Override
        public void onSessionTerminated() {
            CallbackData callbackData = new CallbackData(CallbackCode.ON_SESSION_TERMINATED);
            processCallback(callbackData);
        }

        @Override
        public void onServiceDiscovered(
                PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter) {
            CallbackData callbackData = new CallbackData(CallbackCode.ON_SERVICE_DISCOVERED);
            callbackData.peerHandle = peerHandle;
            callbackData.serviceSpecificInfo = serviceSpecificInfo;
            callbackData.matchFilter = matchFilter;
            processCallback(callbackData);
        }

        @Override
        public void onServiceDiscovered(ServiceDiscoveryInfo info) {
            CallbackData callbackData = new CallbackData(CallbackCode.ON_SERVICE_DISCOVERED);
            callbackData.peerHandle = info.getPeerHandle();
            callbackData.serviceSpecificInfo = info.getServiceSpecificInfo();
            callbackData.matchFilter = info.getMatchFilters();
            if (ApiLevelUtil.isAfter(Build.VERSION_CODES.TIRAMISU)) {
                callbackData.pairedAlias = info.getPairedAlias();
                callbackData.pairingConfig = info.getPairingConfig();
            }
            processCallback(callbackData);
        }

        @Override
        public void onServiceDiscoveredWithinRange(
                PeerHandle peerHandle,
                byte[] serviceSpecificInfo,
                List<byte[]> matchFilter,
                int distanceMm) {
            CallbackData callbackData = new CallbackData(
                    CallbackCode.ON_SERVICE_DISCOVERED_WITH_RANGE);
            callbackData.peerHandle = peerHandle;
            callbackData.serviceSpecificInfo = serviceSpecificInfo;
            callbackData.matchFilter = matchFilter;
            callbackData.distanceMm = distanceMm;
            processCallback(callbackData);
        }

        @Override
        public void onMessageSendSucceeded(int messageId) {
            CallbackData callbackData = new CallbackData(CallbackCode.ON_MESSAGE_SEND_SUCCEEDED);
            callbackData.messageId = messageId;
            processCallback(callbackData);
        }

        @Override
        public void onMessageSendFailed(int messageId) {
            CallbackData callbackData = new CallbackData(CallbackCode.ON_MESSAGE_SEND_FAILED);
            callbackData.messageId = messageId;
            processCallback(callbackData);
        }

        @Override
        public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
            CallbackData callbackData = new CallbackData(CallbackCode.ON_MESSAGE_RECEIVED);
            callbackData.peerHandle = peerHandle;
            callbackData.serviceSpecificInfo = message;
            processCallback(callbackData);
        }

        @Override
        public void onPairingSetupRequestReceived(PeerHandle peerHandle, int requestId) {
            CallbackData callbackData = new CallbackData(CallbackCode.ON_PAIRING_REQUEST_RECEIVED);
            callbackData.peerHandle = peerHandle;
            callbackData.pairingRequestId = requestId;
            processCallback(callbackData);
        }

        @Override
        public void onPairingSetupSucceeded(PeerHandle peerHandle, String alias) {
            CallbackData callbackData = new CallbackData(CallbackCode.ON_PAIRING_SETUP_CONFIRMED);
            callbackData.peerHandle = peerHandle;
            callbackData.pairingAccept = true;
            callbackData.pairingAlias = alias;
            processCallback(callbackData);
        }

        @Override
        public void onPairingSetupFailed(PeerHandle peerHandle) {
            CallbackData callbackData = new CallbackData(CallbackCode.ON_PAIRING_SETUP_CONFIRMED);
            callbackData.peerHandle = peerHandle;
            callbackData.pairingAccept = false;
            processCallback(callbackData);
        }

        @Override
        public void onPairingVerificationSucceed(PeerHandle peerHandle, String alias) {
            CallbackData callbackData = new CallbackData(
                    CallbackCode.ON_PAIRING_VERIFICATION_CONFIRMED);
            callbackData.peerHandle = peerHandle;
            callbackData.pairingAccept = true;
            callbackData.pairingAlias = alias;
            processCallback(callbackData);
        }

        @Override
        public void onPairingVerificationFailed(PeerHandle peerHandle) {
            CallbackData callbackData = new CallbackData(
                    CallbackCode.ON_PAIRING_VERIFICATION_CONFIRMED);
            callbackData.peerHandle = peerHandle;
            callbackData.pairingAccept = false;
            processCallback(callbackData);
        }

        @Override
        public void onBootstrappingSucceeded(PeerHandle peerHandle, int method) {
            CallbackData callbackData = new CallbackData(CallbackCode.ON_BOOTSTRAPPING_CONFIRMED);
            callbackData.peerHandle = peerHandle;
            callbackData.bootstrappingAccept = true;
            callbackData.bootstrappingMethod = method;
            processCallback(callbackData);
        }

        @Override
        public void onBootstrappingFailed(PeerHandle peerHandle) {
            CallbackData callbackData = new CallbackData(CallbackCode.ON_BOOTSTRAPPING_CONFIRMED);
            callbackData.peerHandle = peerHandle;
            callbackData.bootstrappingAccept = false;
            processCallback(callbackData);
        }
    }

    /**
     * Utility RangingResultCallback - provides mechanism for blocking/serializing access with the
     * waitForRangingResults method.
     */
    public static class RangingCb extends RangingResultCallback {
        public static final int TIMEOUT = -1;
        public static final int ON_FAILURE = 0;
        public static final int ON_RESULTS = 1;

        private final CountDownLatch mBlocker = new CountDownLatch(1);
        private int mStatus = TIMEOUT;
        private List<RangingResult> mResults = null;

        /**
         * Wait (blocks) for Ranging results callbacks - or times-out.
         *
         * @return Pair of status & Ranging results if succeeded, null otherwise.
         */
        public Pair<Integer, List<RangingResult>> waitForRangingResults()
                throws InterruptedException {
            if (mBlocker.await(CALLBACK_TIMEOUT_SEC, SECONDS)) {
                return new Pair<>(mStatus, mResults);
            }
            return new Pair<>(TIMEOUT, null);
        }

        @Override
        public void onRangingFailure(int code) {
            mStatus = ON_FAILURE;
            mBlocker.countDown();
        }

        @Override
        public void onRangingResults(List<RangingResult> results) {
            mStatus = ON_RESULTS;
            this.mResults = results;
            mBlocker.countDown();
        }
    }

    private CallbackUtils() {
    }
}
