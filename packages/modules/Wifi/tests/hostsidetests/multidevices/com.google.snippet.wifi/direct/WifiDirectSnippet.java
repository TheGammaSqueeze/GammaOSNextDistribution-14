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
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

import org.json.JSONException;

public class WifiDirectSnippet implements Snippet {

    private static final String TAG = "WifiDirectSnippet";
    private final Context mContext;

    private final WifiP2pManager mP2pManager;
    private WifiP2pInfo mP2pInfo;
    private WifiP2pGroup mP2pGroup;
    private WifiP2pManager.Channel mChannel;
    private WifiManager mWifiManager;

    private final Object mLock = new Object();

    private final WifiP2pManager.ChannelListener mChannelListener =
            new WifiP2pManager.ChannelListener() {
                public void onChannelDisconnected() {
                    Log.e(TAG, "P2P channel disconnected");
                }
            };

    private BroadcastReceiver mP2pStateChangedReceiver =
            new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            if (action.equals(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)) {
                Log.d(TAG, "Wifi P2p State Changed.");
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, 0);
                if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                    Log.d(TAG, "Disabled");
                } else if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    Log.d(TAG, "Enabled");
                }
            } else if (action.equals(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)) {
                Log.d(TAG, "Wifi P2p Peers Changed");
            } else if (action.equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)) {
                Log.d(TAG, "Wifi P2p Connection Changed.");
                synchronized (mLock) {
                    NetworkInfo networkInfo = intent
                            .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                    if (networkInfo.isConnected()) {
                        Log.d(TAG, "Wifi P2p Connected.");
                        mP2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                        mP2pGroup = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
                        mLock.notify();
                    } else if (networkInfo.isConnectedOrConnecting()) {
                        Log.d(TAG, "Wifi P2p is in connecting state");
                    } else {
                        Log.d(TAG, "Wifi P2p is in disconnected state");
                    }
                }
            } else if (action.equals(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)) {
                Log.d(TAG, "Wifi P2p This Device Changed.");
                WifiP2pDevice device = intent
                        .getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            } else if (action.equals(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)) {
                Log.d(TAG, "Wifi P2p Discovery Changed.");
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, 0);
                if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                    Log.d(TAG, "discovery started.");
                } else if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                    Log.d(TAG, "discovery stopped.");
                }
            }
        }
    };

    class WifiP2pActionListener implements WifiP2pManager.ActionListener {
        private final String mOperation;
        WifiP2pActionListener(String action) {
            mOperation = action;
        }

        @Override
        public void onSuccess() {
            Log.d(TAG, mOperation + " OnSuccess");
        }
        @Override
        public void onFailure(int reason) {
            Log.d(TAG, mOperation + " onFailure - reason: " + reason);
        }
    }

    private WifiP2pConfig buildWifiP2pConfig(String networkName, String passphrase,
            int frequency) {
        WifiP2pConfig.Builder builder = new WifiP2pConfig.Builder();
        builder.setNetworkName(networkName);
        builder.setPassphrase(passphrase);
        if (frequency != 0) {
            builder.setGroupOperatingFrequency(frequency);
        }
        return builder.build();
    }

    public WifiDirectSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mP2pManager = mContext.getSystemService(WifiP2pManager.class);
        mWifiManager = mContext.getSystemService(WifiManager.class);

        Log.d(TAG, "WifiDirectSnippet - init");
    }

    @Rpc(description = "Check if Aware pairing supported")
    public boolean isChannelConstrainedDiscoverySupported()
            throws InterruptedException {
        if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            return false;
        }
        return mP2pManager.isChannelConstrainedDiscoverySupported();
    }

    @Rpc(description = "Execute p2p initialize")
    public void initializeP2p() throws Exception {
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.allowAutojoinGlobal(false));
        ShellIdentityUtils.invokeWithShellPermissions(() -> mWifiManager.disconnect());
        PollingCheck.check(
                "Wifi not disconnected",
                20000,
                () -> mWifiManager.getConnectionInfo().getNetworkId() == -1);
        mChannel = mP2pManager.initialize(mContext,  mContext.getMainLooper(), mChannelListener);
        IntentFilter intentFilter = new IntentFilter(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        intentFilter.setPriority(999);
        mContext.registerReceiver(mP2pStateChangedReceiver, intentFilter);
        Log.d(TAG, "Wifi Direct initialization completed");
    }

    @Rpc(description = "Close P2P channel")
    public void closeP2p() throws JSONException {
        if (mChannel != null) {
            mChannel.close();
            mChannel = null;
            Log.d(TAG, "Wifi Direct close called");
        }
        ShellIdentityUtils.invokeWithShellPermissions(() -> mWifiManager.allowAutojoinGlobal(true));
        Log.d(TAG, "Wifi Direct close completed");
    }

    @Rpc(description = "Create a P2P group owner with config")
    public boolean p2pCreateGroup(String networkName, String passphrase,
            int frequency) throws JSONException {
        if (mChannel == null) {
            Log.d(TAG, "p2pCreateGroup failed -should call initializeP2p first ");
            return false;
        }
        WifiP2pConfig wifiP2pConfig = buildWifiP2pConfig(networkName, passphrase, frequency);
        mP2pManager.createGroup(mChannel, wifiP2pConfig,
                new WifiP2pActionListener("CreateGroup"));
        Log.d(TAG, "p2pCreateGroup succeeded");
        return true;
    }

    @Rpc(description = "Connect to a P2P group owner with config")
    public long p2pConnect(String networkName, String passphrase,
            int frequency) throws JSONException, InterruptedException {
        if (mChannel == null) {
            Log.d(TAG, "p2pConnect failed- should call initializeP2p first");
            return -1;
        }
        WifiP2pConfig wifiP2pConfig = buildWifiP2pConfig(networkName, passphrase, frequency);
        long startTime = System.currentTimeMillis();
        mP2pManager.connect(mChannel, wifiP2pConfig,
                new WifiP2pActionListener("Connect"));

        synchronized (mLock) {
            mLock.wait(5000);
        }

        if (mP2pInfo == null || !mP2pInfo.groupFormed || mP2pGroup == null) {
            Log.d(TAG, "p2pConnect failure");

            return -1;
        }

        Log.d(TAG, "p2pConnect succeeded - group not null");
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public void shutdown() {
        Log.d(TAG, "Wifi Direct shutdown called");
    }
}
