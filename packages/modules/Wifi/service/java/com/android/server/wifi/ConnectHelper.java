/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.android.server.wifi.util.ActionListenerWrapper;

/**
 * This class is used to hold connection logic that is shared between {@link WifiServiceImpl} and
 * other helper classes, because helper classes should not call WifiServiceImpl directly.
 */
public class ConnectHelper {
    private static final String TAG = "WifiConnectHelper";

    private final ActiveModeWarden mActiveModeWarden;
    private final WifiConfigManager mWifiConfigManager;

    public ConnectHelper(
            ActiveModeWarden activeModeWarden,
            WifiConfigManager wifiConfigManager) {
        mActiveModeWarden = activeModeWarden;
        mWifiConfigManager = wifiConfigManager;
    }

    /**
     * Trigger connection to an existing network and provide status via the provided callback.
     * This uses the primary client mode manager for making the connection.
     */
    public void connectToNetwork(
            @NonNull NetworkUpdateResult result,
            @NonNull ActionListenerWrapper wrapper,
            int callingUid, @NonNull String packageName, @Nullable String attributionTag) {
        connectToNetwork(
                mActiveModeWarden.getPrimaryClientModeManager(), result, wrapper, callingUid,
                packageName, attributionTag);
    }

    /**
     * Trigger connection to an existing network and provide status via the provided callback.
     * This uses the provided client mode manager for making the connection.
     */
    public void connectToNetwork(
            @NonNull ClientModeManager clientModeManager,
            @NonNull NetworkUpdateResult result,
            @NonNull ActionListenerWrapper wrapper,
            int callingUid, @NonNull String packageName, @Nullable String attributionTag) {
        int netId = result.getNetworkId();
        if (mWifiConfigManager.getConfiguredNetwork(netId) == null) {
            Log.e(TAG, "connectToNetwork Invalid network Id=" + netId);
            wrapper.sendFailure(WifiManager.ActionListener.FAILURE_INTERNAL_ERROR);
            return;
        }
        mWifiConfigManager.updateBeforeConnect(netId, callingUid, packageName,
                !ClientModeImpl.ATTRIBUTION_TAG_DISALLOW_CONNECT_CHOICE.equals(attributionTag));
        clientModeManager.connectNetwork(result, wrapper, callingUid, packageName, attributionTag);
    }
}
