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

package com.android.server.wifi;

import android.app.StatsManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.Process;
import android.util.Log;
import android.util.StatsEvent;

import com.android.server.wifi.proto.WifiStatsLog;

import java.util.List;
import java.util.Set;

/**
 * This is used to log pulled atoms to StatsD via Callback.
 */
public class WifiPulledAtomLogger {
    public static final String WIFI_BUILD_FROM_SOURCE_PACKAGE_NAME = "com.android.wifi";
    private static final String WIFI_PACKAGE_NAME_SUFFIX = ".android.wifi";
    private int mWifiBuildType = 0;

    private static final String TAG = "WifiPulledAtomLogger";
    private final StatsManager mStatsManager;
    private final Handler mWifiHandler;
    private final Context mContext;
    private final WifiInjector mWifiInjector;
    private StatsManager.StatsPullAtomCallback mStatsPullAtomCallback;

    private int mApexVersionNumber = -1;

    public WifiPulledAtomLogger(StatsManager statsManager, Handler handler, Context context,
            WifiInjector wifiInjector) {
        mStatsManager = statsManager;
        mWifiHandler = handler;
        mStatsPullAtomCallback = new WifiPullAtomCallback();
        mContext = context;
        mWifiInjector = wifiInjector;
    }

    /**
     * Set up an atom to get pulled.
     * @param atomTag
     */
    public void setPullAtomCallback(int atomTag) {
        if (mStatsManager == null) {
            Log.e(TAG, "StatsManager is null. Failed to set wifi pull atom callback for atomTag="
                    + atomTag);
            return;
        }
        mStatsManager.setPullAtomCallback(
                atomTag,
                null, // use default meta data values
                command -> mWifiHandler.post(command), // Executor posting to wifi handler thread
                mStatsPullAtomCallback
        );
    }

    /**
     * Implementation of StatsPullAtomCallback. This will check the atom tag and log data
     * correspondingly.
     */
    public class WifiPullAtomCallback implements StatsManager.StatsPullAtomCallback {
        @Override
        public int onPullAtom(int atomTag, List<StatsEvent> data) {
            switch (atomTag) {
                case WifiStatsLog.WIFI_MODULE_INFO:
                    return handleWifiVersionPull(atomTag, data);
                case WifiStatsLog.WIFI_SETTING_INFO:
                    return handleWifiSettingsPull(atomTag, data);
                case WifiStatsLog.WIFI_COMPLEX_SETTING_INFO:
                    return handleWifiComplexSettingsPull(atomTag, data);
                case WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO:
                    return handleWifiConfiguredNetworkInfoPull(atomTag, data);
                default:
                    return StatsManager.PULL_SKIP;
            }
        }
    }

    private int handleWifiVersionPull(int atomTag, List<StatsEvent> data) {
        if (mWifiBuildType != 0) {
            // build type already cached. No need to get it again.
            data.add(WifiStatsLog.buildStatsEvent(atomTag, mApexVersionNumber, mWifiBuildType));
            return StatsManager.PULL_SUCCESS;
        }
        PackageManager pm = mContext.getPackageManager();
        if (pm == null) {
            Log.e(TAG, "Failed to get package manager");
            return StatsManager.PULL_SKIP;
        }
        updateBuildTypeAndVersionCode(pm);

        data.add(WifiStatsLog.buildStatsEvent(atomTag, mApexVersionNumber, mWifiBuildType));
        return StatsManager.PULL_SUCCESS;
    }

    private int handleWifiSettingsPull(int atomTag, List<StatsEvent> data) {
        WifiSettingsStore settingsStore = mWifiInjector.getWifiSettingsStore();
        data.add(WifiStatsLog.buildStatsEvent(atomTag,
                WifiStatsLog.WIFI_SETTING_INFO__SETTING_NAME__WIFI_SCAN_ALWAYS_AVAILABLE,
                settingsStore.isScanAlwaysAvailable()));
        data.add(WifiStatsLog.buildStatsEvent(atomTag,
                WifiStatsLog.WIFI_SETTING_INFO__SETTING_NAME__WIFI_SCAN_THROTTLE,
                settingsStore.isWifiScanThrottleEnabled()));
        data.add(WifiStatsLog.buildStatsEvent(atomTag,
                WifiStatsLog.WIFI_SETTING_INFO__SETTING_NAME__WIFI_SCORING,
                settingsStore.isWifiScoringEnabled()));
        data.add(WifiStatsLog.buildStatsEvent(atomTag,
                WifiStatsLog.WIFI_SETTING_INFO__SETTING_NAME__WIFI_PASSPOINT,
                settingsStore.isWifiPasspointEnabled()));

        boolean nonPersistentMacRandEnabled = mWifiInjector.getFrameworkFacade().getIntegerSetting(
                mContext,
                WifiConfigManager.NON_PERSISTENT_MAC_RANDOMIZATION_FEATURE_FORCE_ENABLE_FLAG, 0)
                == 1 ? true : false;
        data.add(WifiStatsLog.buildStatsEvent(atomTag,
                WifiStatsLog.WIFI_SETTING_INFO__SETTING_NAME__WIFI_ENHANCED_MAC_RANDOMIZATION,
                nonPersistentMacRandEnabled));

        data.add(WifiStatsLog.buildStatsEvent(atomTag,
                WifiStatsLog.WIFI_SETTING_INFO__SETTING_NAME__WIFI_WAKE,
                mWifiInjector.getWakeupController().isEnabled()));
        data.add(WifiStatsLog.buildStatsEvent(atomTag,
                WifiStatsLog.WIFI_SETTING_INFO__SETTING_NAME__WIFI_NETWORKS_AVAILABLE_NOTIFICATION,
                mWifiInjector.getOpenNetworkNotifier().isSettingEnabled()));
        data.add(WifiStatsLog.buildStatsEvent(atomTag,
                WifiStatsLog.WIFI_SETTING_INFO__SETTING_NAME__LOCATION_MODE,
                mWifiInjector.getWifiPermissionsUtil().isLocationModeEnabled()));
        return StatsManager.PULL_SUCCESS;
    }

    private static int frameworkToAtomMultiInternetMode(
            @WifiManager.WifiMultiInternetMode int mode) {
        switch (mode) {
            case WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED:
                return WifiStatsLog
                        .WIFI_COMPLEX_SETTING_INFO__MULTI_INTERNET_MODE__MULTI_INTERNET_MODE_DISABLED;
            case WifiManager.WIFI_MULTI_INTERNET_MODE_DBS_AP:
                return WifiStatsLog
                        .WIFI_COMPLEX_SETTING_INFO__MULTI_INTERNET_MODE__MULTI_INTERNET_MODE_DBS_AP;
            case WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP:
                return WifiStatsLog
                        .WIFI_COMPLEX_SETTING_INFO__MULTI_INTERNET_MODE__MULTI_INTERNET_MODE_MULTI_AP;
            default:
                Log.i(TAG, "Invalid multi-internet mode: " + mode);
                return -1;
        }
    }

    private int handleWifiComplexSettingsPull(int atomTag, List<StatsEvent> data) {
        int multiInternetMode = frameworkToAtomMultiInternetMode(
                mWifiInjector.getWifiSettingsStore().getWifiMultiInternetMode());
        if (multiInternetMode == -1) {
            return StatsManager.PULL_SKIP;
        }
        data.add(WifiStatsLog.buildStatsEvent(atomTag, multiInternetMode));
        return StatsManager.PULL_SUCCESS;
    }

    private void updateBuildTypeAndVersionCode(PackageManager pm) {
        // Query build type and cache if not already cached.
        List<PackageInfo> packageInfos = pm.getInstalledPackages(PackageManager.MATCH_APEX);
        boolean found = false;
        String wifiPackageName = null;
        for (PackageInfo packageInfo : packageInfos) {
            if (packageInfo.packageName.endsWith(WIFI_PACKAGE_NAME_SUFFIX)) {
                mApexVersionNumber = (int) packageInfo.getLongVersionCode();
                wifiPackageName = packageInfo.packageName;
                if (packageInfo.packageName.equals(WIFI_BUILD_FROM_SOURCE_PACKAGE_NAME)) {
                    found = true;
                }
                break;
            }
        }
        mWifiBuildType = found
                ? WifiStatsLog.WIFI_MODULE_INFO__BUILD_TYPE__TYPE_BUILT_FROM_SOURCE
                : WifiStatsLog.WIFI_MODULE_INFO__BUILD_TYPE__TYPE_PREBUILT;
        Log.i(TAG, "Wifi Module package name is " + wifiPackageName
                + ", version is " + mApexVersionNumber);
    }

    private static boolean configHasUtf8Ssid(WifiConfiguration config) {
        return WifiSsid.fromString(config.SSID).getUtf8Text() != null;
    }

    private StatsEvent wifiConfigToStatsEvent(
            int atomTag, WifiConfiguration config, boolean isSuggestion) {
        return WifiStatsLog.buildStatsEvent(
                atomTag,
                0,  // deprecated network ID field
                config.isEnterprise(),
                config.hiddenSSID,
                config.isPasspoint(),
                isSuggestion,
                configHasUtf8Ssid(config),
                mWifiInjector.getSsidTranslator().isSsidTranslationEnabled(),
                false, // legacy TOFU field
                !config.getNetworkSelectionStatus().hasNeverDetectedCaptivePortal(),
                config.allowAutojoin,
                WifiMetrics.convertSecurityModeToProto(config),
                WifiMetrics.convertMacRandomizationToProto(config.getMacRandomizationSetting()),
                WifiMetrics.convertMeteredOverrideToProto(config.meteredOverride),
                WifiMetrics.convertEapMethodToProto(config),
                WifiMetrics.convertEapInnerMethodToProto(config),
                WifiMetrics.isFreeOpenRoaming(config),
                WifiMetrics.isSettledOpenRoaming(config),
                WifiMetrics.convertTofuConnectionStateToProto(config),
                WifiMetrics.convertTofuDialogStateToProto(config));
    }

    private int handleWifiConfiguredNetworkInfoPull(int atomTag, List<StatsEvent> data) {
        List<WifiConfiguration> savedConfigs =
                mWifiInjector.getWifiConfigManager().getSavedNetworks(Process.WIFI_UID);
        for (WifiConfiguration config : savedConfigs) {
            if (!config.isPasspoint()) {
                data.add(wifiConfigToStatsEvent(atomTag, config, false));
            }
        }

        Set<WifiNetworkSuggestion> approvedSuggestions =
                mWifiInjector.getWifiNetworkSuggestionsManager().getAllApprovedNetworkSuggestions();
        for (WifiNetworkSuggestion suggestion : approvedSuggestions) {
            WifiConfiguration config = suggestion.getWifiConfiguration();
            if (!config.isPasspoint()) {
                data.add(wifiConfigToStatsEvent(atomTag, config, true));
            }
        }

        List<WifiConfiguration> passpointConfigs =
                mWifiInjector.getPasspointManager().getWifiConfigsForPasspointProfiles(false);
        for (WifiConfiguration config : passpointConfigs) {
            data.add(wifiConfigToStatsEvent(atomTag, config, false));
        }

        List<WifiConfiguration> passpointSuggestions =
                mWifiInjector.getWifiNetworkSuggestionsManager()
                        .getAllPasspointScanOptimizationSuggestionNetworks(false);
        for (WifiConfiguration config : passpointSuggestions) {
            data.add(wifiConfigToStatsEvent(atomTag, config, true));
        }

        return StatsManager.PULL_SUCCESS;
    }
}
