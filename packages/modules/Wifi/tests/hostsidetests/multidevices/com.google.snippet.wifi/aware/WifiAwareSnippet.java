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

import static android.net.wifi.aware.AwarePairingConfig.PAIRING_BOOTSTRAPPING_OPPORTUNISTIC;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Pair;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** An example snippet class with a simple Rpc. */
public class WifiAwareSnippet implements Snippet {

    private Object mLock;

    private static class WifiAwareSnippetException extends Exception {
        private static final long SERIAL_VERSION_UID = 1;

        WifiAwareSnippetException(String msg) {
            super(msg);
        }

        WifiAwareSnippetException(String msg, Throwable err) {
            super(msg, err);
        }
    }

    private static final String TAG = "WifiAwareSnippet";

    private static final String SERVICE_NAME = "CtsVerifierTestService";
    private static final byte[] MATCH_FILTER_BYTES = "bytes used for matching".getBytes(UTF_8);
    private static final byte[] PUB_SSI = "Extra bytes in the publisher discovery".getBytes(UTF_8);
    private static final byte[] SUB_SSI =
            "Arbitrary bytes for the subscribe discovery".getBytes(UTF_8);
    private static final int LARGE_ENOUGH_DISTANCE = 100000; // 100 meters
    private static final String PASSWORD = "Some super secret password";
    private static final String ALIAS_PUBLISH = "publisher";
    private static final String ALIAS_SUBSCRIBE = "subscriber";
    private static final int TEST_WAIT_DURATION_MS = 10000;

    private final WifiAwareManager mWifiAwareManager;

    private final Context mContext;

    private final HandlerThread mHandlerThread;

    private final Handler mHandler;

    private WifiAwareSession mWifiAwareSession;
    private DiscoverySession mDiscoverySession;
    private CallbackUtils.DiscoveryCb mDiscoveryCb;
    private PeerHandle mPeerHandle;
    private final AwarePairingConfig mPairingConfig = new AwarePairingConfig.Builder()
            .setPairingCacheEnabled(true)
            .setPairingSetupEnabled(true)
            .setPairingVerificationEnabled(true)
            .setBootstrappingMethods(PAIRING_BOOTSTRAPPING_OPPORTUNISTIC)
            .build();

    public WifiAwareSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mWifiAwareManager = mContext.getSystemService(WifiAwareManager.class);
        mHandlerThread = new HandlerThread("Snippet-Aware");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Rpc(description = "Execute attach.")
    public void attach() throws InterruptedException, WifiAwareSnippetException {
        CallbackUtils.AttachCb attachCb = new CallbackUtils.AttachCb();
        mWifiAwareManager.attach(attachCb, mHandler);
        Pair<CallbackUtils.AttachCb.CallbackCode, WifiAwareSession> results =
                attachCb.waitForAttach();
        if (results.first != CallbackUtils.AttachCb.CallbackCode.ON_ATTACHED) {
            throw new WifiAwareSnippetException(
                    String.format("executeTest: attach " + results.first));
        }
        mWifiAwareSession = results.second;
        if (mWifiAwareSession == null) {
            throw new WifiAwareSnippetException(
                    "executeTest: attach callback succeeded but null session returned!?");
        }
    }

    @Rpc(description = "Execute subscribe.")
    public void subscribe(Boolean isUnsolicited, Boolean isRangingRequired,
            Boolean isPairingRequired) throws InterruptedException, WifiAwareSnippetException {
        mDiscoveryCb = new CallbackUtils.DiscoveryCb();

        List<byte[]> matchFilter = new ArrayList<>();
        matchFilter.add(MATCH_FILTER_BYTES);
        SubscribeConfig.Builder builder =
                new SubscribeConfig.Builder()
                        .setServiceName(SERVICE_NAME)
                        .setServiceSpecificInfo(SUB_SSI)
                        .setMatchFilter(matchFilter)
                        .setSubscribeType(
                                isUnsolicited
                                        ? SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE
                                        : SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)
                        .setTerminateNotificationEnabled(true);

        if (isRangingRequired) {
            // set up a distance that will always trigger - i.e. that we're already in that range
            builder.setMaxDistanceMm(LARGE_ENOUGH_DISTANCE);
        }
        if (isPairingRequired) {
            builder.setPairingConfig(mPairingConfig);
        }
        SubscribeConfig subscribeConfig = builder.build();
        Log.d(TAG, "executeTestSubscriber: subscribeConfig=" + subscribeConfig);
        mWifiAwareSession.subscribe(subscribeConfig, mDiscoveryCb, mHandler);

        // wait for results - subscribe session
        CallbackUtils.DiscoveryCb.CallbackData callbackData =
                mDiscoveryCb.waitForCallbacks(
                        ImmutableSet.of(
                                CallbackUtils.DiscoveryCb.CallbackCode.ON_SUBSCRIBE_STARTED,
                                CallbackUtils.DiscoveryCb.CallbackCode.ON_SESSION_CONFIG_FAILED));
        if (callbackData.callbackCode
                != CallbackUtils.DiscoveryCb.CallbackCode.ON_SUBSCRIBE_STARTED) {
            throw new WifiAwareSnippetException(
                    String.format("executeTestSubscriber: subscribe %s",
                            callbackData.callbackCode));
        }
        mDiscoverySession = callbackData.subscribeDiscoverySession;
        if (mDiscoverySession == null) {
            throw new WifiAwareSnippetException(
                    "executeTestSubscriber: subscribe succeeded but null session returned");
        }
        Log.d(TAG, "executeTestSubscriber: subscribe succeeded");

        // 3. wait for discovery
        callbackData =
                mDiscoveryCb.waitForCallbacks(ImmutableSet.of(isRangingRequired ? CallbackUtils
                        .DiscoveryCb.CallbackCode.ON_SERVICE_DISCOVERED_WITH_RANGE
                        : CallbackUtils.DiscoveryCb.CallbackCode.ON_SERVICE_DISCOVERED));

        if (callbackData.callbackCode == CallbackUtils.DiscoveryCb.CallbackCode.TIMEOUT) {
            throw new WifiAwareSnippetException(
                    "executeTestSubscriber: waiting for discovery TIMEOUT");
        }
        mPeerHandle = callbackData.peerHandle;
        if (!isRangingRequired) {
            Log.d(TAG, "executeTestSubscriber: discovery");
        } else {
            Log.d(TAG, "executeTestSubscriber: discovery with range="
                    + callbackData.distanceMm);
        }

        if (!Arrays.equals(PUB_SSI, callbackData.serviceSpecificInfo)) {
            throw new WifiAwareSnippetException(
                    "executeTestSubscriber: discovery but SSI mismatch: rx='"
                            + new String(callbackData.serviceSpecificInfo, UTF_8)
                            + "'");
        }
        if (callbackData.matchFilter.size() != 1
                || !Arrays.equals(MATCH_FILTER_BYTES, callbackData.matchFilter.get(0))) {
            StringBuilder sb = new StringBuilder();
            sb.append("size=").append(callbackData.matchFilter.size());
            for (byte[] mf : callbackData.matchFilter) {
                sb.append(", e='").append(new String(mf, UTF_8)).append("'");
            }
            throw new WifiAwareSnippetException(
                    "executeTestSubscriber: discovery but matchFilter mismatch: " + sb);
        }
        if (mPeerHandle == null) {
            throw new WifiAwareSnippetException(
                    "executeTestSubscriber: discovery but null peerHandle");
        }
    }

    @Rpc(description = "Send message.")
    public void sendMessage(int messageId, String message)
            throws InterruptedException, WifiAwareSnippetException {
        // 4. send message & wait for send status
        mDiscoverySession.sendMessage(mPeerHandle, messageId, message.getBytes(UTF_8));
        CallbackUtils.DiscoveryCb.CallbackData callbackData =
                mDiscoveryCb.waitForCallbacks(
                        ImmutableSet.of(
                                CallbackUtils.DiscoveryCb.CallbackCode.ON_MESSAGE_SEND_SUCCEEDED,
                                CallbackUtils.DiscoveryCb.CallbackCode.ON_MESSAGE_SEND_FAILED));

        if (callbackData.callbackCode
                != CallbackUtils.DiscoveryCb.CallbackCode.ON_MESSAGE_SEND_SUCCEEDED) {
            throw new WifiAwareSnippetException(
                    String.format("executeTestSubscriber: sendMessage %s",
                            callbackData.callbackCode));
        }
        Log.d(TAG, "executeTestSubscriber: send message succeeded");
        if (callbackData.messageId != messageId) {
            throw new WifiAwareSnippetException(
                    "executeTestSubscriber: send message message ID mismatch: "
                            + callbackData.messageId);
        }
    }

    @Rpc(description = "Create publish session.")
    public void publish(Boolean isUnsolicited, Boolean isRangingRequired, Boolean isPairingRequired)
            throws WifiAwareSnippetException, InterruptedException {
        mDiscoveryCb = new CallbackUtils.DiscoveryCb();

        // 2. publish
        List<byte[]> matchFilter = new ArrayList<>();
        matchFilter.add(MATCH_FILTER_BYTES);
        PublishConfig.Builder builder =
                new PublishConfig.Builder()
                        .setServiceName(SERVICE_NAME)
                        .setServiceSpecificInfo(PUB_SSI)
                        .setMatchFilter(matchFilter)
                        .setPublishType(
                                isUnsolicited
                                        ? PublishConfig.PUBLISH_TYPE_UNSOLICITED
                                        : PublishConfig.PUBLISH_TYPE_SOLICITED)
                        .setTerminateNotificationEnabled(true)
                        .setRangingEnabled(isRangingRequired);
        if (isPairingRequired) {
            builder.setPairingConfig(mPairingConfig);
        }
        PublishConfig publishConfig = builder.build();
        Log.d(TAG, "executeTestPublisher: publishConfig=" + publishConfig);
        mWifiAwareSession.publish(publishConfig, mDiscoveryCb, mHandler);

        //    wait for results - publish session
        CallbackUtils.DiscoveryCb.CallbackData callbackData =
                mDiscoveryCb.waitForCallbacks(
                        ImmutableSet.of(
                                CallbackUtils.DiscoveryCb.CallbackCode.ON_PUBLISH_STARTED,
                                CallbackUtils.DiscoveryCb.CallbackCode.ON_SESSION_CONFIG_FAILED));
        if (callbackData.callbackCode
                != CallbackUtils.DiscoveryCb.CallbackCode.ON_PUBLISH_STARTED) {
            throw new WifiAwareSnippetException(
                    String.format("executeTestPublisher: publish %s", callbackData.callbackCode));
        }
        mDiscoverySession = callbackData.publishDiscoverySession;
        if (mDiscoverySession == null) {
            throw new WifiAwareSnippetException(
                    "executeTestPublisher: publish succeeded but null session returned");
        }
        Log.d(TAG, "executeTestPublisher: publish succeeded");
    }

    @Rpc(description = "Initiate pairing setup, should be on subscriber")
    public void initiatePairingSetup(Boolean withPassword, Boolean accept)
            throws InterruptedException, WifiAwareSnippetException {
        mDiscoverySession.initiateBootstrappingRequest(mPeerHandle,
                PAIRING_BOOTSTRAPPING_OPPORTUNISTIC);
        CallbackUtils.DiscoveryCb.CallbackData callbackData =
                mDiscoveryCb.waitForCallbacks(Set.of(
                        CallbackUtils.DiscoveryCb.CallbackCode.ON_BOOTSTRAPPING_CONFIRMED));
        if (callbackData.callbackCode
                != CallbackUtils.DiscoveryCb.CallbackCode.ON_BOOTSTRAPPING_CONFIRMED) {
            throw new WifiAwareSnippetException(
                    String.format("initiatePairingSetup: bootstrapping confirm missing %s",
                            callbackData.callbackCode));
        }
        if (!callbackData.bootstrappingAccept
                || callbackData.bootstrappingMethod != PAIRING_BOOTSTRAPPING_OPPORTUNISTIC) {
            throw new WifiAwareSnippetException("initiatePairingSetup: bootstrapping failed");
        }
        mDiscoverySession.initiatePairingRequest(mPeerHandle, ALIAS_PUBLISH,
                Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128,
                withPassword ? PASSWORD : null);
        callbackData =
                mDiscoveryCb.waitForCallbacks(Set.of(
                        CallbackUtils.DiscoveryCb.CallbackCode.ON_PAIRING_SETUP_CONFIRMED));
        if (callbackData.callbackCode
                != CallbackUtils.DiscoveryCb.CallbackCode.ON_PAIRING_SETUP_CONFIRMED) {
            throw new WifiAwareSnippetException(
                    String.format("initiatePairingSetup: pairing confirm missing %s",
                            callbackData.callbackCode));
        }
        if (!accept) {
            if (callbackData.pairingAccept) {
                throw new WifiAwareSnippetException("initiatePairingSetup: pairing should be "
                        + "rejected");
            }
            return;
        }
        if (!callbackData.pairingAccept) {
            throw new WifiAwareSnippetException("initiatePairingSetup: pairing reject");
        }
        mWifiAwareManager.removePairedDevice(ALIAS_PUBLISH);
        AtomicReference<List<String>> aliasList = new AtomicReference<>();
        Consumer<List<String>> consumer = value -> {
            synchronized (mLock) {
                aliasList.set(value);
                mLock.notify();
            }
        };
        mWifiAwareManager.getPairedDevices(Executors.newSingleThreadScheduledExecutor(), consumer);
        synchronized (mLock) {
            mLock.wait(TEST_WAIT_DURATION_MS);
        }
        if (aliasList.get().size() != 1 || !ALIAS_PUBLISH.equals(aliasList.get().get(0))) {
            throw new WifiAwareSnippetException("initiatePairingSetup: pairing alias mismatch");
        }
        mWifiAwareManager.removePairedDevice(ALIAS_SUBSCRIBE);
        mWifiAwareManager.getPairedDevices(Executors.newSingleThreadScheduledExecutor(), consumer);
        synchronized (mLock) {
            mLock.wait(TEST_WAIT_DURATION_MS);
        }
        if (!aliasList.get().isEmpty()) {
            throw new WifiAwareSnippetException(
                    "initiatePairingSetup: pairing alias is not empty after "
                            + "removal");
        }
    }

    @Rpc(description = "respond to a pairing request, should be on publisher")
    public void respondToPairingSetup(Boolean withPassword, Boolean accept)
            throws InterruptedException, WifiAwareSnippetException {
        CallbackUtils.DiscoveryCb.CallbackData callbackData = mDiscoveryCb.waitForCallbacks(Set.of(
                CallbackUtils.DiscoveryCb.CallbackCode.ON_BOOTSTRAPPING_CONFIRMED));
        if (callbackData.callbackCode
                != CallbackUtils.DiscoveryCb.CallbackCode.ON_BOOTSTRAPPING_CONFIRMED) {
            throw new WifiAwareSnippetException(
                    String.format("respondToPairingSetup: bootstrapping confirm missing %s",
                            callbackData.callbackCode));
        }
        if (!callbackData.bootstrappingAccept
                || callbackData.bootstrappingMethod != PAIRING_BOOTSTRAPPING_OPPORTUNISTIC) {
            throw new WifiAwareSnippetException("respondToPairingSetup: bootstrapping failed");
        }
        callbackData =
                mDiscoveryCb.waitForCallbacks(Set.of(
                        CallbackUtils.DiscoveryCb.CallbackCode.ON_PAIRING_REQUEST_RECEIVED));
        if (callbackData.callbackCode
                != CallbackUtils.DiscoveryCb.CallbackCode.ON_PAIRING_REQUEST_RECEIVED) {
            throw new WifiAwareSnippetException(
                    String.format("respondToPairingSetup: pairing request missing %s",
                            callbackData.callbackCode));
        }
        mPeerHandle = callbackData.peerHandle;
        if (mPeerHandle == null) {
            throw new WifiAwareSnippetException("respondToPairingSetup: peerHandle null");
        }
        if (accept) {
            mDiscoverySession.acceptPairingRequest(callbackData.pairingRequestId, mPeerHandle,
                    ALIAS_SUBSCRIBE, Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128,
                    withPassword ? PASSWORD : null);
        } else {
            mDiscoverySession.rejectPairingRequest(callbackData.pairingRequestId, mPeerHandle);
            return;
        }
        callbackData =
                mDiscoveryCb.waitForCallbacks(Set.of(
                        CallbackUtils.DiscoveryCb.CallbackCode.ON_PAIRING_SETUP_CONFIRMED));
        if (callbackData.callbackCode
                != CallbackUtils.DiscoveryCb.CallbackCode.ON_PAIRING_SETUP_CONFIRMED) {
            throw new WifiAwareSnippetException(
                    String.format("respondToPairingSetup: pairing confirm missing %s",
                            callbackData.callbackCode));
        }
        if (!callbackData.pairingAccept) {
            throw new WifiAwareSnippetException("respondToPairingSetup: pairing reject");
        }
        mWifiAwareManager.removePairedDevice(ALIAS_PUBLISH);
        AtomicReference<List<String>> aliasList = new AtomicReference<>();
        Consumer<List<String>> consumer = value -> {
            synchronized (mLock) {
                aliasList.set(value);
                mLock.notify();
            }
        };
        mWifiAwareManager.getPairedDevices(Executors.newSingleThreadScheduledExecutor(), consumer);
        synchronized (mLock) {
            mLock.wait(TEST_WAIT_DURATION_MS);
        }
        if (aliasList.get().size() != 1 || !ALIAS_PUBLISH.equals(aliasList.get().get(0))) {
            throw new WifiAwareSnippetException("respondToPairingSetup: pairing alias mismatch");
        }
        mWifiAwareManager.removePairedDevice(ALIAS_SUBSCRIBE);
        mWifiAwareManager.getPairedDevices(Executors.newSingleThreadScheduledExecutor(), consumer);
        synchronized (mLock) {
            mLock.wait(TEST_WAIT_DURATION_MS);
        }
        if (!aliasList.get().isEmpty()) {
            throw new WifiAwareSnippetException(
                    "respondToPairingSetup: pairing alias is not empty after "
                            + "removal");
        }
    }

    @Rpc(description = "Check if Aware pairing supported")
    public Boolean checkIfPairingSupported()
            throws WifiAwareSnippetException, InterruptedException {
        if (!ApiLevelUtil.isAfter(Build.VERSION_CODES.TIRAMISU)) {
            return false;
        }
        return mWifiAwareManager.getCharacteristics().isAwarePairingSupported();
    }

    @Rpc(description = "Receive message.")
    public String receiveMessage() throws WifiAwareSnippetException, InterruptedException {
        // 3. wait to receive message.
        CallbackUtils.DiscoveryCb.CallbackData callbackData =
                mDiscoveryCb.waitForCallbacks(
                        ImmutableSet.of(
                                CallbackUtils.DiscoveryCb.CallbackCode.ON_MESSAGE_RECEIVED));
        mPeerHandle = callbackData.peerHandle;
        Log.d(TAG, "executeTestPublisher: received message");

        if (mPeerHandle == null) {
            throw new WifiAwareSnippetException(
                    "executeTestPublisher: received message but peerHandle is null!?");
        }
        return new String(callbackData.serviceSpecificInfo, UTF_8);
    }

    @Override
    public void shutdown() {
        if (mDiscoverySession != null) {
            mDiscoverySession.close();
            mDiscoverySession = null;
        }
        if (mWifiAwareSession != null) {
            mWifiAwareSession.close();
            mWifiAwareSession = null;
        }
        mWifiAwareManager.resetPairedDevices();
    }
}
