/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.net.wifi.WifiManager.WIFI_FEATURE_CONTROL_ROAMING;

import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * This class provides helper functions for Wifi connectivity related modules to
 * access WifiNative. It starts with firmware roaming. TODO(b/34819513): Move operations
 * such as connection to network and legacy framework roaming here.
 *
 * NOTE: This class is not thread safe and should only be used from the main Wifi thread.
 */
public class WifiConnectivityHelper {
    private static final String TAG = "WifiConnectivityHelper";
    @VisibleForTesting
    public static int INVALID_LIST_SIZE = -1;
    private final WifiInjector mWifiInjector;
    private boolean mFirmwareRoamingSupported = false;
    private int mMaxNumBlocklistBssid = INVALID_LIST_SIZE;
    private int mMaxNumAllowlistSsid = INVALID_LIST_SIZE;

    WifiConnectivityHelper(WifiInjector wifiInjector) {
        mWifiInjector = wifiInjector;
    }

    /**
     * Query firmware if it supports
     * {@link android.net.wifi.WifiManager#WIFI_FEATURE_CONTROL_ROAMING}. If yes, get the firmware
     * roaming capabilities. If firmware roaming is supported but we fail to get the roaming
     * capabilities or the returned capability values are invalid, we fall back to framework
     * roaming.
     *
     * @return true if succeed, false if firmware roaming is supported but fail to get valid
     * roaming capabilities.
     */
    public boolean getFirmwareRoamingInfo() {
        mFirmwareRoamingSupported = false;
        mMaxNumBlocklistBssid = INVALID_LIST_SIZE;
        mMaxNumAllowlistSsid = INVALID_LIST_SIZE;

        ClientModeManager primaryManager =
                mWifiInjector.getActiveModeWarden().getPrimaryClientModeManager();
        long fwFeatureSet = primaryManager.getSupportedFeatures();
        Log.d(TAG, "Firmware supported feature set: " + Long.toHexString(fwFeatureSet));

        if ((fwFeatureSet & WIFI_FEATURE_CONTROL_ROAMING) == 0) {
            Log.d(TAG, "Firmware roaming is not supported");
            return true;
        }

        WifiNative.RoamingCapabilities roamingCap = primaryManager.getRoamingCapabilities();
        if (roamingCap != null) {
            if (roamingCap.maxBlocklistSize < 0 || roamingCap.maxAllowlistSize < 0) {
                Log.e(TAG, "Invalid firmware roaming capabilities: max num blocklist bssid="
                        + roamingCap.maxBlocklistSize + " max num allowlist ssid="
                        + roamingCap.maxAllowlistSize);
            } else {
                mFirmwareRoamingSupported = true;
                mMaxNumBlocklistBssid = roamingCap.maxBlocklistSize;
                mMaxNumAllowlistSsid = roamingCap.maxAllowlistSize;
                Log.d(TAG, "Firmware roaming supported with capabilities: max num blocklist bssid="
                        + mMaxNumBlocklistBssid + " max num allowlist ssid="
                        + mMaxNumAllowlistSsid);
                return true;
            }
        } else {
            Log.e(TAG, "Failed to get firmware roaming capabilities");
        }

        return false;
    }

    /**
     * Return if firmware roaming is supported.
     */
    public boolean isFirmwareRoamingSupported() {
        return mFirmwareRoamingSupported;
    }

    /**
     * Get the maximum size of BSSID blocklist firmware supports.
     *
     * @return INVALID_LIST_SIZE if firmware roaming is not supported, or
     * maximum size of the BSSID blocklist firmware supports.
     */
    public int getMaxNumBlocklistBssid() {
        if (mFirmwareRoamingSupported) {
            return mMaxNumBlocklistBssid;
        } else {
            Log.e(TAG, "getMaxNumBlocklistBssid: Firmware roaming is not supported");
            return INVALID_LIST_SIZE;
        }
    }

    /**
     * Get the maximum size of SSID allowlist firmware supports.
     *
     * @return INVALID_LIST_SIZE if firmware roaming is not supported, or
     * maximum size of the SSID allowlist firmware supports.
     */
    public int getMaxNumAllowlistSsid() {
        if (mFirmwareRoamingSupported) {
            return mMaxNumAllowlistSsid;
        } else {
            Log.e(TAG, "getMaxNumAllowlistSsid: Firmware roaming is not supported");
            return INVALID_LIST_SIZE;
        }
    }

    /**
     * Write firmware roaming configuration to firmware.
     *
     * @param blocklistBssids BSSIDs to be blocklisted
     * @param allowlistSsids  SSIDs to be allowlisted
     * @return true if succeeded, false otherwise.
     */
    public boolean setFirmwareRoamingConfiguration(ArrayList<String> blocklistBssids,
            ArrayList<String> allowlistSsids) {
        if (!mFirmwareRoamingSupported) {
            Log.e(TAG, "Firmware roaming is not supported");
            return false;
        }

        if (blocklistBssids == null || allowlistSsids == null) {
            Log.e(TAG, "Invalid firmware roaming configuration settings");
            return false;
        }

        int blocklistSize = blocklistBssids.size();
        int allowlistSize = allowlistSsids.size();

        if (blocklistSize > mMaxNumBlocklistBssid || allowlistSize > mMaxNumAllowlistSsid) {
            Log.e(TAG, "Invalid BSSID blocklist size " + blocklistSize + " SSID allowlist size "
                    + allowlistSize + ". Max blocklist size: " + mMaxNumBlocklistBssid
                    + ", max allowlist size: " + mMaxNumAllowlistSsid);
            return false;
        }

        WifiNative.RoamingConfig roamConfig = new WifiNative.RoamingConfig();
        roamConfig.blocklistBssids = blocklistBssids;
        roamConfig.allowlistSsids = allowlistSsids;

        return mWifiInjector.getActiveModeWarden()
                .getPrimaryClientModeManager().configureRoaming(roamConfig);
    }

    /**
     * Dump debug information
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of WifiConnectivityHelper");
        pw.println("WifiConnectivityHelper - Log Begin ----");
        pw.println("mFirmwareRoamingSupported: " + mFirmwareRoamingSupported);
        pw.println("mMaxNumBlocklistBssid: " + mMaxNumBlocklistBssid);
        pw.println("mMaxNumAllowlistSsid: " + mMaxNumAllowlistSsid);
        pw.println("WifiConnectivityHelper - Log End ----");
    }
}
