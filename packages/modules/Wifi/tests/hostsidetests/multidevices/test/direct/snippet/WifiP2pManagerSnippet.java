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

package com.google.snippet.wifi.direct;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pManager;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcOptional;
import com.google.android.mobly.snippet.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/** Snippet class for WifiP2pManager. */
public class WifiP2pManagerSnippet implements Snippet {
    private static final String WIFI_P2P_CREATING_GROUP = "CREATING_GROUP";

    private final Context mContext;
    private final IntentFilter mIntentFilter;
    private final WifiP2pManager mWifiP2pManager;

    private WifiP2pManager.Channel mChannel = null;
    private WifiP2pStateChangedReceiver mStateChangedReceiver = null;

    private static class WifiP2pManagerException extends Exception {
        WifiP2pManagerException(String message) {
            super(message);
        }
    }

    public WifiP2pManagerSnippet() {
        mContext = ApplicationProvider.getApplicationContext();
        mWifiP2pManager = mContext.getSystemService(WifiP2pManager.class);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    @AsyncRpc(description = "Registers the application with the Wi-Fi framework.")
    public void p2pInitialize(String callbackId) {
        if (mChannel != null) {
            Log.d("Channel has already created, skip WifiP2pManager.initialize()");
            return;
        }
        mChannel = mWifiP2pManager.initialize(mContext, mContext.getMainLooper(), null);
        mStateChangedReceiver = new WifiP2pStateChangedReceiver(callbackId, mIntentFilter);
    }

    @Rpc(
            description =
                    "Close the current P2P connection and indicate to the P2P service that"
                            + " connections created by the app can be removed.")
    public void p2pClose() {
        if (mChannel == null) {
            Log.d("Channel has already closed, skip" + " WifiP2pManager.Channel.close()");
            return;
        }
        mChannel.close();
        mChannel = null;
        mStateChangedReceiver.close();
        mStateChangedReceiver = null;
    }

    @AsyncRpc(description = "Create a p2p group with the current device as the group owner.")
    public void p2pCreateGroup(String callbackId, @RpcOptional JSONObject wifiP2pConfig)
            throws JSONException, WifiP2pManagerException {
        if (mChannel == null) {
            throw new WifiP2pManagerException(
                    "Channel has not created, call p2pInitialize() first");
        }
        WifiP2pActionListener actionListener = new WifiP2pActionListener(callbackId);
        if (wifiP2pConfig == null) {
            mWifiP2pManager.createGroup(mChannel, actionListener);
        } else {
            mWifiP2pManager.createGroup(
                    mChannel, JsonDeserializer.jsonToWifiP2pConfig(wifiP2pConfig), actionListener);
        }
    }

    @AsyncRpc(description = "Start a p2p connection to a device with the specified configuration.")
    public void p2pConnect(String callbackId, JSONObject wifiP2pConfig)
            throws JSONException, WifiP2pManagerException {
        if (mChannel == null) {
            throw new WifiP2pManagerException(
                    "Channel has not created, call p2pInitialize() first");
        }
        WifiP2pActionListener actionListener = new WifiP2pActionListener(callbackId);
        mWifiP2pManager.connect(
                mChannel, JsonDeserializer.jsonToWifiP2pConfig(wifiP2pConfig), actionListener);
    }

    private class WifiP2pStateChangedReceiver extends BroadcastReceiver {
        public final String callbackId;

        private WifiP2pStateChangedReceiver(String callbackId, IntentFilter mIntentFilter) {
            this.callbackId = callbackId;
            mContext.registerReceiver(this, mIntentFilter, Context.RECEIVER_NOT_EXPORTED);
        }

        private void close() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context mContext, Intent intent) {
            String action = intent.getAction();
            SnippetEvent event = new SnippetEvent(callbackId, action);
            switch (action) {
                case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                    event
                            .getData()
                            .putInt(
                                    WifiP2pManager.EXTRA_WIFI_STATE,
                                    intent.getIntExtra(
                                            WifiP2pManager.EXTRA_WIFI_STATE, 0));
                    break;
                case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                    event
                            .getData()
                            .putParcelable(
                                    action,
                                    intent.getParcelableExtra(
                                            WifiP2pManager.EXTRA_P2P_DEVICE_LIST));
                    break;
                case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                    NetworkInfo networkInfo = intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_NETWORK_INFO);
                    if (networkInfo.isConnected()) {
                        SnippetEvent connectedEvent = new SnippetEvent(
                                callbackId, WIFI_P2P_CREATING_GROUP);
                        connectedEvent
                                .getData()
                                .putParcelable(
                                        WifiP2pManager.EXTRA_WIFI_P2P_INFO,
                                        intent.getParcelableExtra(
                                                WifiP2pManager.EXTRA_WIFI_P2P_INFO));
                        connectedEvent
                                .getData()
                                .putParcelable(
                                        WifiP2pManager.EXTRA_WIFI_P2P_GROUP,
                                        intent.getParcelableExtra(
                                                WifiP2pManager.EXTRA_WIFI_P2P_GROUP));
                        EventCache.getInstance().postEvent(connectedEvent);
                    }
                    break;
                case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION:
                    event
                            .getData()
                            .putParcelable(
                                    action,
                                    intent.getParcelableExtra(
                                            WifiP2pManager.EXTRA_WIFI_P2P_DEVICE));
                    break;
                case WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION:
                    event
                            .getData()
                            .putInt(action, intent.getIntExtra(
                                    WifiP2pManager.EXTRA_DISCOVERY_STATE, 0));
                    break;
                default:
                    break;
            }
            EventCache.getInstance().postEvent(event);
        }
    }

    private static class WifiP2pActionListener implements WifiP2pManager.ActionListener {
        private final String mCallbackId;

        WifiP2pActionListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onSuccess() {
            SnippetEvent event = new SnippetEvent(mCallbackId, "onSuccess");
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onFailure(int reason) {
            SnippetEvent event = new SnippetEvent(mCallbackId, "onFailure");
            event.getData().putInt("reason", reason);
            EventCache.getInstance().postEvent(event);
        }
    }

    @Override
    public void shutdown() {
        p2pClose();
    }
}

