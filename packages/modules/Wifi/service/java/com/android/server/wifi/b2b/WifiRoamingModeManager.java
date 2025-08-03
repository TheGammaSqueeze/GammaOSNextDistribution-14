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

package com.android.server.wifi.b2b;

import android.hardware.wifi.WifiStatusCode;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.util.Log;

import com.android.server.wifi.ActiveModeWarden;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WifiRoamingConfigStore;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Map;

public class WifiRoamingModeManager {
    private static final String TAG = "WifiRoamingModeManager";
    private final WifiNative mWifiNative;
    private final WifiRoamingConfigStore mWifiRoamingConfigStore;
    private final ActiveModeWarden mActiveModeWarden;
    private boolean mVerboseLoggingEnabled = false;

    public WifiRoamingModeManager(WifiNative wifiNative,
                                  ActiveModeWarden activeModeWarden,
                                  WifiRoamingConfigStore wifiRoamingConfigStore) {
        this.mWifiNative = wifiNative;
        this.mWifiRoamingConfigStore = wifiRoamingConfigStore;
        this.mActiveModeWarden = activeModeWarden;
    }

    /**
     * To handle policy updates when device is already in connected state
     * and also when policy get removed in connected state.
     */
    private void checkAndUpdatePolicy(String updatedSsid) {
        String currentSsid = mActiveModeWarden.getConnectionInfo().getSSID();
        if (!updatedSsid.equals(currentSsid)) return;
        String ifaceName = mActiveModeWarden.getPrimaryClientModeManager().getInterfaceName();
        if (currentSsid != null && ifaceName != null) {
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "Re-applying roaming policy as it updated");
            }
            applyWifiRoamingMode(ifaceName, currentSsid);
        }
    }

    /**
     * Add a new network roaming policy.
     *
     * @param ssid name of the network on which policy is to be added.
     * @param roamingMode   denotes roaming mode value configured.
     * @param isDeviceOwner flag denoting whether API is called by the device owner.
     */
    public void setPerSsidRoamingMode(WifiSsid ssid, @WifiManager.RoamingMode int roamingMode,
                                      boolean isDeviceOwner) {
        mWifiRoamingConfigStore.addRoamingMode(ssid.toString(), roamingMode, isDeviceOwner);
        checkAndUpdatePolicy(ssid.toString());
    }

    /**
     * Remove the network roaming policy for the given ssid.
     *
     * @param ssid name of the network on which policy is to be removed.
     * @param isDeviceOwner flag denoting whether API is called by the device owner.
     */
    public void removePerSsidRoamingMode(WifiSsid ssid, boolean isDeviceOwner) {
        mWifiRoamingConfigStore.removeRoamingMode(ssid.toString(), isDeviceOwner);
        checkAndUpdatePolicy(ssid.toString());
    }

    /**
     * Get all the network roaming policies configured.
     *
     * @param isDeviceOwner flag denoting whether API is called by the device owner.
     * @return Map of corresponding policies for the API caller,
     *         where key is ssid and value is roaming mode/policy configured for that ssid.
     */
    public Map<String, Integer> getPerSsidRoamingModes(boolean isDeviceOwner) {
        return mWifiRoamingConfigStore.getPerSsidRoamingModes(isDeviceOwner);
    }

    /**
     * Apply roaming policy to the provided network.
     * If policy does not exist, apply normal roaming policy.
     *
     * @param iface represents the name of the wifi interface.
     * @param ssid  represents the name of the network.
     */
    public void applyWifiRoamingMode(String iface, String ssid) {
        int roamingMode = mWifiRoamingConfigStore.getRoamingMode(ssid);
        if (mVerboseLoggingEnabled) {
            Log.i(TAG, "Applying roaming policy for network "
                    + ssid + " with value " + roamingMode);
        }
        @WifiStatusCode int errorCode = mWifiNative.setRoamingMode(iface, roamingMode);
        switch (errorCode) {
            case WifiStatusCode.SUCCESS:
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Roaming mode value successfully set to: " + roamingMode);
                }
                break;
            case WifiStatusCode.ERROR_NOT_STARTED:
                Log.e(TAG, "Failed to set roaming mode as WifiStaIfaceAidlImpl"
                        + " instance is not created.");
                break;
            case WifiStatusCode.ERROR_WIFI_IFACE_INVALID:
                Log.e(TAG, "Failed to set roaming mode as interface is invalid.");
                break;
            case WifiStatusCode.ERROR_INVALID_ARGS:
                Log.e(TAG, "Failed to set roaming mode due to invalid parameter "
                        + roamingMode);
                break;
            default:
                Log.e(TAG, "Failed to set roaming mode due to unknown error.");
        }
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
    }

    /**
     * Dump roaming policies for debugging.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mWifiRoamingConfigStore.dump(fd, pw, args);
    }
}
