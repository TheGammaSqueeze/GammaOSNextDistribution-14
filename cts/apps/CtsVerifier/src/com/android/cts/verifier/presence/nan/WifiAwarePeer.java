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

package com.android.cts.verifier.presence.nan;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import com.android.cts.verifier.presence.nan.RttRanger.NanResultListener;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Publishes or subscribes to a WiFi Aware service, to enable WiFi RTT ranging capabilities. */
public class WifiAwarePeer {
    private static final String TAG = RttRanger.class.getName();
    private static final String UUID_STRING = "CDB7950D-73F1-4D4D-8E47-C090502DBD63";
    private static final int CTS_V_MESSAGE_ID = 52;
    private static final ParcelUuid PARCEL_UUID = new ParcelUuid(UUID.fromString(UUID_STRING));
    private static final String CTS_V_SERVICE_NAME = "cts-v-service";
    private static final List<byte[]> MATCH_FILTER =
            Collections.singletonList(UUID_STRING.getBytes(UTF_8));

    private final Handler handler;
    private final WifiAwareManager wifiAwareManager;
    private final RttRanger rttRanger;

    private WifiAwareSession wifiAwareSession;
    private WifiAwarePeerListener wifiAwarePeerListener;
    private NanResultListener nanResultListener;
    private PublishDiscoverySession currentPublishDiscoverySession = null;
    private SubscribeDiscoverySession currentSubscribeDiscoverySession = null;
    private String serviceNameForSession = null;

    public WifiAwarePeer(Context context, Handler handler) {
        this.handler = handler;
        this.wifiAwareManager = context.getSystemService(WifiAwareManager.class);
        this.rttRanger = new RttRanger(context, handler::post);
    }

    public void publish(byte serviceId) {
        this.serviceNameForSession = CTS_V_SERVICE_NAME + serviceId;
        attach(/*isPublisher=*/ true);
    }

    public void sendMessage(PeerHandle peerHandle) {
        if (currentPublishDiscoverySession != null) {
            currentPublishDiscoverySession.sendMessage(peerHandle, CTS_V_MESSAGE_ID,
                    Build.MODEL.getBytes(
                            UTF_8));
        } else if (currentSubscribeDiscoverySession != null) {
            currentSubscribeDiscoverySession.sendMessage(peerHandle, CTS_V_MESSAGE_ID,
                    "Request".getBytes(
                            UTF_8));
        }
    }

    public void subscribe(
            WifiAwarePeerListener wifiAwarePeerListener, NanResultListener nanResultListener,
            String serviceId) {
        this.serviceNameForSession = CTS_V_SERVICE_NAME + serviceId;
        attach(/*isPublisher=*/ false);
        this.wifiAwarePeerListener = wifiAwarePeerListener;
        this.nanResultListener = nanResultListener;
    }

    private void attach(boolean isPublisher) {
        if (!wifiAwareManager.isAvailable()) {
            Log.w(TAG, "WifiRttManager is not available");
            return;
        }

        wifiAwareManager.attach(new AwareAttachCallback(isPublisher),
                handler);
    }

    public void stop() {
        Log.i(TAG, "Closing WiFi aware session");

        if (wifiAwareSession != null) {
            wifiAwareSession.close();
            wifiAwareSession = null;
            currentPublishDiscoverySession = null;
            currentSubscribeDiscoverySession = null;
        }

        nanResultListener = null;
    }

    private class AwareAttachCallback extends AttachCallback {
        private final PublishConfig publishConfig =
                new PublishConfig.Builder()
                        .setMatchFilter(MATCH_FILTER)
                        .setServiceName(serviceNameForSession)
                        .setRangingEnabled(true)
                        .build();
        private final SubscribeConfig subscribeConfig =
                new SubscribeConfig.Builder()
                        .setMatchFilter(MATCH_FILTER)
                        .setServiceName(serviceNameForSession)
                        .setMaxDistanceMm(30 * 100 * 100)
                        .setMinDistanceMm(0)
                        .build();

        private final boolean isPublisher;

        public AwareAttachCallback(boolean isPublisher) {
            this.isPublisher = isPublisher;
        }

        @Override
        public void onAttached(WifiAwareSession session) {
            Log.i(TAG, "onAttached, session = " + session);
            wifiAwareSession = session;
            if (isPublisher) {
                session.publish(publishConfig, createPublishDiscoverySessionCallback(), handler);
            } else {
                session.subscribe(subscribeConfig, createSubscribeDiscoverySessionCallback(),
                        handler);
            }
        }

        @Override
        public void onAttachFailed() {
            Log.w(TAG, "Wifi Aware attach failed");
        }
    }

    private DiscoverySessionCallback createPublishDiscoverySessionCallback() {
        return new DiscoverySessionCallback() {
            @Override
            public void onPublishStarted(PublishDiscoverySession session) {
                Log.i(TAG, "onPublishStarted, PublishDiscoverySession= " + session);
                currentPublishDiscoverySession = session;
            }

            @Override
            public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
                Log.i(TAG, "onMessageReceived from subscriber");
                // Received message from subscriber. We automatically assume this is a reference
                // device name request
                sendMessage(peerHandle);
            }
        };
    }

    private DiscoverySessionCallback createSubscribeDiscoverySessionCallback() {
        return new DiscoverySessionCallback() {

            @Override
            public void onSubscribeStarted(SubscribeDiscoverySession session) {
                Log.i(TAG, "onSubscribeStarted, SubscribeDiscoverySession= " + session);
                currentSubscribeDiscoverySession = session;
            }

            @Override
            public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
                Log.i(TAG, "onMessageReceived from publisher");
                wifiAwarePeerListener.onReferenceDeviceNameReceived(new String(message, UTF_8));
            }

            @Override
            public void onServiceDiscoveredWithinRange(
                    PeerHandle peerHandle,
                    byte[] serviceSpecificInfo,
                    List<byte[]> matchFilter,
                    int distanceMm) {
                Log.i(TAG,
                        "onServiceDiscovered, peerHandle= " + peerHandle + ", distanceMm= "
                                + distanceMm);

                wifiAwarePeerListener.onDeviceFound(peerHandle);
                sendMessage(peerHandle);
                rttRanger.startRanging(peerHandle, nanResultListener);
            }
        };
    }

    /** Listener for range results. */
    public interface WifiAwarePeerListener {
        void onDeviceFound(PeerHandle peerHandle);
        void onReferenceDeviceNameReceived(String referenceDeviceName);
    }
}