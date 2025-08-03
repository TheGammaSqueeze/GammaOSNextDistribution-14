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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiSsid;
import android.net.wifi.util.ScanResultUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class to store the info needed to match a scan result to the provided network configuration.
 */
public class ScanResultMatchInfo {
    /**
     * SSID of the network.
     */
    public String networkSsid;
    /**
     * Security params list.
     */
    public List<SecurityParams> securityParamsList = new ArrayList<>();

    /**
     * True if created from a scan result
     */
    private boolean mFromScanResult = false;

    /**
     * Get the ScanResultMatchInfo for the given WifiConfiguration
     */
    public static ScanResultMatchInfo fromWifiConfiguration(WifiConfiguration config) {
        ScanResultMatchInfo info = new ScanResultMatchInfo();
        info.networkSsid = config.SSID;
        info.securityParamsList = config.getSecurityParamsList();
        return info;
    }

    /**
     * Get the ScanResultMatchInfo for the given ScanResult
     */
    public static ScanResultMatchInfo fromScanResult(ScanResult scanResult) {
        ScanResultMatchInfo info = new ScanResultMatchInfo();
        WifiSsid wifiSsid = scanResult.getWifiSsid();
        if (wifiSsid != null) {
            info.networkSsid = wifiSsid.toString();
        } else {
            info.networkSsid = "\"" + scanResult.SSID + "\"";
        }
        info.securityParamsList =
                ScanResultUtil.generateSecurityParamsListFromScanResult(scanResult);
        info.mFromScanResult = true;
        return info;
    }

    /**
     * Check if an auto-upgraded security parameters configuration is allowed by the overlay
     * configurations for WPA3-Personal (SAE) and Enhanced Open (OWE).
     *
     * @param securityParams Security parameters object
     * @return true if allowed, false if not allowed
     */
    private static boolean isAutoUpgradeSecurityParamsAllowed(SecurityParams securityParams) {
        WifiGlobals wifiGlobals = WifiInjector.getInstance().getWifiGlobals();
        // In mixed security network environments, we need to filter out APs with the stronger
        // security type when the current network supports the weaker security type, and the
        // stronger security type was added by auto-upgrade, and
        // auto-upgrade feature is disabled.
        if (securityParams.getSecurityType() == WifiConfiguration.SECURITY_TYPE_SAE
                && securityParams.isAddedByAutoUpgrade()
                && !wifiGlobals.isWpa3SaeUpgradeEnabled()) {
            return false;
        }
        if (securityParams.getSecurityType() == WifiConfiguration.SECURITY_TYPE_OWE
                && securityParams.isAddedByAutoUpgrade()
                && !wifiGlobals.isOweUpgradeEnabled()) {
            return false;
        }
        return true;
    }

    /**
     * The matching algorithm is that the type with a bigger index in the allowed
     * params list has the higher priority. We try to match each type from the end of
     * the allowed params list against the params in the scan result params list.
     *
     * There are three cases which will skip the match:
     * 1. the security type is different.
     * 2. the params is disabled, ex. disabled by Transition Disable Indication.
     * 3. The params is added by the auto-upgrade mechanism, but the corresponding
     *    feature is not enabled.
     */
    private static @Nullable SecurityParams findBestMatchingSecurityParams(
            List<SecurityParams> allowedParamsList,
            List<SecurityParams> scanResultParamsList) {
        if (null == allowedParamsList) return null;
        if (null == scanResultParamsList) return null;
        for (int i = allowedParamsList.size() - 1; i >= 0; i--) {
            SecurityParams allowedParams = allowedParamsList.get(i);
            if (!WifiConfigurationUtil.isSecurityParamsValid(allowedParams)
                    || !isAutoUpgradeSecurityParamsAllowed(allowedParams)) {
                continue;
            }
            for (SecurityParams scanResultParams: scanResultParamsList) {
                if (!allowedParams.isSecurityType(scanResultParams.getSecurityType())) {
                    continue;
                }
                return allowedParams;
            }
        }
        return null;
    }

    /**
     * Get the best-matching security type between ScanResult and WifiConifiguration.
     */
    public static @Nullable SecurityParams getBestMatchingSecurityParams(
            WifiConfiguration config,
            ScanResult scanResult) {
        if (null == config || null == scanResult) return null;

        return findBestMatchingSecurityParams(
                config.getSecurityParamsList(),
                ScanResultUtil.generateSecurityParamsListFromScanResult(scanResult));
    }

    /**
     * Get the best-matching security type between ScanResult and WifiConifiguration.
     */
    public static @Nullable SecurityParams getBestMatchingSecurityParams(
            WifiConfiguration config,
            List<SecurityParams> scanResultParamsList) {
        if (null == config || null == scanResultParamsList) return null;

        return findBestMatchingSecurityParams(
                config.getSecurityParamsList(),
                scanResultParamsList);
    }

    public @Nullable SecurityParams getDefaultSecurityParams() {
        return securityParamsList.isEmpty() ? null : securityParamsList.get(0);
    }

    public @Nullable SecurityParams getFirstAvailableSecurityParams() {
        return securityParamsList.stream()
                .filter(WifiConfigurationUtil::isSecurityParamsValid)
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks for equality of network type.
     */
    public boolean networkTypeEquals(@NonNull ScanResultMatchInfo other) {
        if (null == securityParamsList || null == other.securityParamsList) return false;

        // If both are from the same sources, do normal comparison.
        if (mFromScanResult == other.mFromScanResult) {
            return securityParamsList.equals(other.securityParamsList);
        }

        final List<SecurityParams> allowedParamsList = mFromScanResult
                ? other.securityParamsList : securityParamsList;
        final List<SecurityParams> scanResultParamsList = mFromScanResult
                ? securityParamsList : other.securityParamsList;

        return null != findBestMatchingSecurityParams(
                allowedParamsList,
                scanResultParamsList);
    }

    @Override
    public boolean equals(Object otherObj) {
        if (this == otherObj) {
            return true;
        } else if (!(otherObj instanceof ScanResultMatchInfo)) {
            return false;
        }
        ScanResultMatchInfo other = (ScanResultMatchInfo) otherObj;
        if (mFromScanResult == other.mFromScanResult) {
            return Objects.equals(networkSsid, other.networkSsid)
                    && securityParamsList.equals(other.securityParamsList);
        }
        return null != matchForNetworkSelection(other);
    }

    /**
     * Match two ScanResultMatchInfo objects while considering configuration in overlays
     *
     * @param other Other object to compare against
     * @return return best matching security params, null if no matching one.
     */
    public SecurityParams matchForNetworkSelection(ScanResultMatchInfo other) {
        if (!Objects.equals(networkSsid, other.networkSsid)) return null;
        if (null == securityParamsList) return null;
        if (null == other.securityParamsList) return null;

        final List<SecurityParams> allowedParamsList = mFromScanResult
                ? other.securityParamsList : securityParamsList;
        final List<SecurityParams> scanResultParamsList = mFromScanResult
                ? securityParamsList : other.securityParamsList;

        return findBestMatchingSecurityParams(
                allowedParamsList,
                scanResultParamsList);
    }

    /** Check whether this matchinfo contains the type or not. */
    public boolean isSecurityType(@WifiConfiguration.SecurityType int securityType) {
        return securityParamsList.stream().anyMatch(p -> p.isSecurityType(securityType));
    }

    @Override
    public int hashCode() {
        return Objects.hash(networkSsid);
    }

    @Override
    public String toString() {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append("ScanResultMatchInfo: SSID: ").append(networkSsid);
        sbuf.append(", from scan result: ").append(mFromScanResult);
        sbuf.append(", SecurityParams List:");
        securityParamsList.stream()
                .forEach(params -> sbuf.append(params.toString()));
        return sbuf.toString();
    }
}
