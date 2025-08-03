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

import android.annotation.Nullable;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiBlocklistMonitor.CarrierSpecificEapFailureConfig;
import com.android.wifi.resources.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import javax.annotation.concurrent.ThreadSafe;


/** Global wifi service in-memory state that is not persisted. */
@ThreadSafe
public class WifiGlobals {

    private static final String TAG = "WifiGlobals";
    private final Context mContext;

    private final AtomicInteger mPollRssiIntervalMillis = new AtomicInteger(-1);
    private final AtomicBoolean mIpReachabilityDisconnectEnabled = new AtomicBoolean(true);
    private final AtomicBoolean mIsBluetoothConnected = new AtomicBoolean(false);
    // Set default to false to check if the value will be overridden by WifiSettingConfigStore.
    private final AtomicBoolean mIsWepAllowed = new AtomicBoolean(false);
    private final AtomicBoolean mIsD2dStaConcurrencySupported = new AtomicBoolean(false);
    private final AtomicInteger mSendDhcpHostnameRestriction = new AtomicInteger();

    // These are read from the overlay, cache them after boot up.
    private boolean mIsWpa3SaeUpgradeEnabled;
    private boolean mIsWpa3SaeUpgradeOffloadEnabled;
    private final boolean mIsOweUpgradeEnabled;
    private final boolean mFlushAnqpCacheOnWifiToggleOffEvent;
    private final boolean mIsWpa3SaeH2eSupported;
    private final String mP2pDeviceNamePrefix;
    private final int mP2pDeviceNamePostfixNumDigits;
    private final int mClientModeImplNumLogRecs;
    private final boolean mSaveFactoryMacToConfigStoreEnabled;
    private final int mWifiLowConnectedScoreThresholdToTriggerScanForMbb;
    private final int mWifiLowConnectedScoreScanPeriodSeconds;
    private final boolean mWifiAllowInsecureEnterpriseConfiguration;
    private final boolean mIsP2pMacRandomizationSupported;
    private final int mPollRssiShortIntervalMillis;
    private final int mPollRssiLongIntervalMillis;
    private final int mClientRssiMonitorThresholdDbm;
    private final int mClientRssiMonitorHysteresisDb;
    private boolean mDisableFirmwareRoamingInIdleMode = false;
    private final boolean mIsSupportMultiInternetDual5G;
    private final int mRepeatedNudFailuresThreshold;
    private final int mRepeatedNudFailuresWindowMs;
    private final boolean mAdjustPollRssiIntervalEnabled;
    private final boolean mWifiInterfaceAddedSelfRecoveryEnabled;
    private final int mNetworkNotFoundEventThreshold;
    private boolean mIsBackgroundScanSupported;
    private boolean mIsSwPnoEnabled;
    private final boolean mIsWepDeprecated;
    private final boolean mIsWpaPersonalDeprecated;
    private final Map<String, List<String>> mCountryCodeToAfcServers;
    private final long mWifiConfigMaxDisableDurationMs;
    // This is set by WifiManager#setVerboseLoggingEnabled(int).
    private int mVerboseLoggingLevel = WifiManager.VERBOSE_LOGGING_LEVEL_DISABLED;
    private boolean mIsUsingExternalScorer = false;
    private boolean mDisableUnwantedNetworkOnLowRssi = false;
    private final boolean mIsAfcSupportedOnDevice;
    private boolean mDisableNudDisconnectsForWapiInSpecificCc = false;
    private boolean mD2dAllowedControlSupportedWhenInfraStaDisabled = false;
    private Set<String> mMacRandomizationUnsupportedSsidPrefixes = new ArraySet<>();
    private Map<String, BiFunction<String, Boolean, Boolean>> mOverrideMethods = new HashMap<>();

    private SparseArray<SparseArray<CarrierSpecificEapFailureConfig>>
            mCarrierSpecificEapFailureConfigMapPerCarrierId = new SparseArray<>();


    public WifiGlobals(Context context) {
        mContext = context;

        mIsWpa3SaeUpgradeEnabled = mContext.getResources()
                .getBoolean(R.bool.config_wifiSaeUpgradeEnabled);
        var prop = android.os.SystemProperties.get("persist.sys.phh.wifi_disable_sae", null);
        if (prop != null && "true".equals(prop))
            mIsWpa3SaeUpgradeEnabled = false;
        mIsWpa3SaeUpgradeOffloadEnabled = mContext.getResources()
                .getBoolean(R.bool.config_wifiSaeUpgradeOffloadEnabled);
        mIsOweUpgradeEnabled = mContext.getResources()
                .getBoolean(R.bool.config_wifiOweUpgradeEnabled);
        mFlushAnqpCacheOnWifiToggleOffEvent = mContext.getResources()
                .getBoolean(R.bool.config_wifiFlushAnqpCacheOnWifiToggleOffEvent);
        mIsWpa3SaeH2eSupported = mContext.getResources()
                .getBoolean(R.bool.config_wifiSaeH2eSupported);
        mP2pDeviceNamePrefix = mContext.getResources()
                .getString(R.string.config_wifiP2pDeviceNamePrefix);
        mP2pDeviceNamePostfixNumDigits = mContext.getResources()
                .getInteger(R.integer.config_wifiP2pDeviceNamePostfixNumDigits);
        mClientModeImplNumLogRecs = mContext.getResources()
                .getInteger(R.integer.config_wifiClientModeImplNumLogRecs);
        mSaveFactoryMacToConfigStoreEnabled = mContext.getResources()
                .getBoolean(R.bool.config_wifiSaveFactoryMacToWifiConfigStore);
        mWifiLowConnectedScoreThresholdToTriggerScanForMbb = mContext.getResources().getInteger(
                R.integer.config_wifiLowConnectedScoreThresholdToTriggerScanForMbb);
        mWifiLowConnectedScoreScanPeriodSeconds = mContext.getResources().getInteger(
                R.integer.config_wifiLowConnectedScoreScanPeriodSeconds);
        mWifiAllowInsecureEnterpriseConfiguration = mContext.getResources().getBoolean(
                R.bool.config_wifiAllowInsecureEnterpriseConfigurationsForSettingsAndSUW);
        mIsP2pMacRandomizationSupported = mContext.getResources().getBoolean(
                R.bool.config_wifi_p2p_mac_randomization_supported);
        mPollRssiIntervalMillis.set(mContext.getResources().getInteger(
                R.integer.config_wifiPollRssiIntervalMilliseconds));
        mPollRssiShortIntervalMillis = mContext.getResources().getInteger(
                R.integer.config_wifiPollRssiIntervalMilliseconds);
        mPollRssiLongIntervalMillis = mContext.getResources().getInteger(
                R.integer.config_wifiPollRssiLongIntervalMilliseconds);
        mClientRssiMonitorThresholdDbm = mContext.getResources().getInteger(
                R.integer.config_wifiClientRssiMonitorThresholdDbm);
        mClientRssiMonitorHysteresisDb = mContext.getResources().getInteger(
                R.integer.config_wifiClientRssiMonitorHysteresisDb);
        mAdjustPollRssiIntervalEnabled = mContext.getResources().getBoolean(
                R.bool.config_wifiAdjustPollRssiIntervalEnabled);
        mDisableFirmwareRoamingInIdleMode = mContext.getResources()
                .getBoolean(R.bool.config_wifiDisableFirmwareRoamingInIdleMode);
        mIsSupportMultiInternetDual5G = mContext.getResources().getBoolean(
                R.bool.config_wifiAllowMultiInternetConnectDual5GFrequency);
        mRepeatedNudFailuresThreshold = mContext.getResources()
                .getInteger(R.integer.config_wifiDisableReasonRepeatedNudFailuresThreshold);
        mRepeatedNudFailuresWindowMs = mContext.getResources()
                .getInteger(R.integer.config_wifiDisableReasonRepeatedNudFailuresWindowMs);
        mWifiInterfaceAddedSelfRecoveryEnabled = mContext.getResources().getBoolean(
                R.bool.config_wifiInterfaceAddedSelfRecoveryEnabled);
        mDisableUnwantedNetworkOnLowRssi = mContext.getResources().getBoolean(
                R.bool.config_wifiDisableUnwantedNetworkOnLowRssi);
        mDisableNudDisconnectsForWapiInSpecificCc = mContext.getResources().getBoolean(
                R.bool.config_wifiDisableNudDisconnectsForWapiInSpecificCc);
        mNetworkNotFoundEventThreshold = mContext.getResources().getInteger(
                R.integer.config_wifiNetworkNotFoundEventThreshold);
        mIsBackgroundScanSupported = mContext.getResources()
                .getBoolean(R.bool.config_wifi_background_scan_support);
        mIsSwPnoEnabled = mContext.getResources()
                .getBoolean(R.bool.config_wifiSwPnoEnabled);
        mIsWepDeprecated = mContext.getResources()
                .getBoolean(R.bool.config_wifiWepDeprecated);
        mIsWpaPersonalDeprecated = mContext.getResources()
                .getBoolean(R.bool.config_wifiWpaPersonalDeprecated);
        mIsAfcSupportedOnDevice = mContext.getResources().getBoolean(R.bool.config_wifiAfcSupported)
                && mContext.getResources().getBoolean(R.bool.config_wifiSoftap6ghzSupported)
                && mContext.getResources().getBoolean(R.bool.config_wifi6ghzSupport);
        mWifiConfigMaxDisableDurationMs = mContext.getResources()
                .getInteger(R.integer.config_wifiDisableTemporaryMaximumDurationMs);
        mD2dAllowedControlSupportedWhenInfraStaDisabled = mContext.getResources()
                .getBoolean(R.bool.config_wifiD2dAllowedControlSupportedWhenInfraStaDisabled);
        Set<String> unsupportedSsidPrefixes = new ArraySet<>(mContext.getResources().getStringArray(
                R.array.config_wifiForceDisableMacRandomizationSsidPrefixList));
        mCountryCodeToAfcServers = getCountryCodeToAfcServersMap();
        if (!unsupportedSsidPrefixes.isEmpty()) {
            for (String ssid : unsupportedSsidPrefixes) {
                String cleanedSsid = ssid.length() > 1 && (ssid.charAt(0) == '"')
                        && (ssid.charAt(ssid.length() - 1) == '"')
                        ? ssid.substring(0, ssid.length() - 1) : ssid;
                mMacRandomizationUnsupportedSsidPrefixes.add(cleanedSsid);
            }
        }
        loadCarrierSpecificEapFailureConfigMap();
        mOverrideMethods.put("config_wifi_background_scan_support",
                new BiFunction<String, Boolean, Boolean>() {
                @Override
                public Boolean apply(String value , Boolean isEnabled) {
                    // reset to default
                    if (!isEnabled) {
                        mIsBackgroundScanSupported = mContext.getResources()
                        .getBoolean(R.bool.config_wifi_background_scan_support);
                        return true;
                    }
                    if ("true".equals(value) || "false".equals(value)) {
                        mIsBackgroundScanSupported = Boolean.parseBoolean(value);
                        return true;
                    }
                    return false;
                }
            });
    }

    /**
     * Gets the CarrierSpecificEapFailureConfig applicable for the carrierId and eapFailureReason.
     * @param carrierId the carrier ID
     * @param eapFailureReason EAP failure reason
     * @return The applicable CarrierSpecificEapFailureConfig, or null if there's no data for this
     * particular combination of carrierId and eapFailureReason.
     */
    public @Nullable CarrierSpecificEapFailureConfig getCarrierSpecificEapFailureConfig(
            int carrierId, int eapFailureReason) {
        if (!mCarrierSpecificEapFailureConfigMapPerCarrierId.contains(carrierId)) {
            return null;
        }
        return mCarrierSpecificEapFailureConfigMapPerCarrierId.get(carrierId).get(eapFailureReason);
    }

    /**
     * Utility method for unit testing.
     */
    public @VisibleForTesting int getCarrierSpecificEapFailureConfigMapSize() {
        return mCarrierSpecificEapFailureConfigMapPerCarrierId.size();
    }

    private void loadCarrierSpecificEapFailureConfigMap() {
        String[] eapFailureOverrides = mContext.getResources().getStringArray(
                R.array.config_wifiEapFailureConfig);
        if (eapFailureOverrides == null) {
            return;
        }
        for (String line : eapFailureOverrides) {
            if (line == null) {
                continue;
            }
            String[] items = line.split(",");
            if (items.length != 5) {
                // error case. Should have exactly 5 items.
                Log.e(TAG, "Failed to parse eapFailureOverrides line=" + line);
                continue;
            }
            try {
                int carrierId = Integer.parseInt(items[0].trim());
                int eapFailureCode = Integer.parseInt(items[1].trim());
                int displayDialogue = Integer.parseInt(items[2].trim());
                int disableThreshold = Integer.parseInt(items[3].trim());
                int disableDurationMinutes = Integer.parseInt(items[4].trim());
                if (!mCarrierSpecificEapFailureConfigMapPerCarrierId.contains(carrierId)) {
                    mCarrierSpecificEapFailureConfigMapPerCarrierId.put(carrierId,
                            new SparseArray<>());
                }
                SparseArray<CarrierSpecificEapFailureConfig> perEapFailureMap =
                        mCarrierSpecificEapFailureConfigMapPerCarrierId.get(carrierId);
                perEapFailureMap.put(eapFailureCode, new CarrierSpecificEapFailureConfig(
                        disableThreshold, disableDurationMinutes * 60 * 1000, displayDialogue > 0));
            } catch (Exception e) {
                // failure to parse. Something is wrong with the config.
                Log.e(TAG, "Parsing eapFailureOverrides line=" + line
                        + ". Exception occurred:" + e);
            }
        }
    }

    private Map<String, List<String>> getCountryCodeToAfcServersMap() {
        Map<String, List<String>> countryCodeToAfcServers = new HashMap<>();
        String[] countryCodeToAfcServersFromConfig = mContext.getResources().getStringArray(
                R.array.config_wifiAfcServerUrlsForCountry);

        if (countryCodeToAfcServersFromConfig == null) {
            return countryCodeToAfcServers;
        }

        // each entry should be of the form: countryCode,url1,url2...
        for (String entry : countryCodeToAfcServersFromConfig) {
            String[] countryAndUrls = entry.split(",");

            // if no servers are specified for a country, then continue to the next entry
            if (countryAndUrls.length < 2) {
                continue;
            }
            countryCodeToAfcServers.put(countryAndUrls[0], Arrays.asList(Arrays.copyOfRange(
                    countryAndUrls, 1, countryAndUrls.length)));
        }
        return countryCodeToAfcServers;
    }

    public Set<String> getMacRandomizationUnsupportedSsidPrefixes() {
        return mMacRandomizationUnsupportedSsidPrefixes;
    }

    /** Get the interval between RSSI polls, in milliseconds. */
    public int getPollRssiIntervalMillis() {
        return mPollRssiIntervalMillis.get();
    }

    /** Set the interval between RSSI polls, in milliseconds. */
    public void setPollRssiIntervalMillis(int newPollIntervalMillis) {
        mPollRssiIntervalMillis.set(newPollIntervalMillis);
    }

    /** Returns whether CMD_IP_REACHABILITY_LOST events should trigger disconnects. */
    public boolean getIpReachabilityDisconnectEnabled() {
        return mIpReachabilityDisconnectEnabled.get();
    }

    /**
     * Returns a list of AFC server URLs for a country, or null if AFC is not available in that
     * country.
     */
    public @Nullable List<String> getAfcServerUrlsForCountry(String countryCode) {
        return mCountryCodeToAfcServers.get(countryCode);
    }

    /**
     * Returns whether this device supports AFC.
     */
    public boolean isAfcSupportedOnDevice() {
        return mIsAfcSupportedOnDevice;
    }

    /** Sets whether CMD_IP_REACHABILITY_LOST events should trigger disconnects. */
    public void setIpReachabilityDisconnectEnabled(boolean enabled) {
        mIpReachabilityDisconnectEnabled.set(enabled);
    }

    /** Set whether bluetooth is enabled. */
    public void setBluetoothEnabled(boolean isEnabled) {
        // If BT was connected and then turned off, there is no CONNECTION_STATE_CHANGE message.
        // So set mIsBluetoothConnected to false if we get a bluetooth disable while connected.
        // But otherwise, Bluetooth being turned on doesn't mean that we're connected.
        if (!isEnabled) {
            mIsBluetoothConnected.set(false);
        }
    }

    /** Set whether bluetooth is connected. */
    public void setBluetoothConnected(boolean isConnected) {
        mIsBluetoothConnected.set(isConnected);
    }

    /** Get whether bluetooth is connected */
    public boolean isBluetoothConnected() {
        return mIsBluetoothConnected.get();
    }

    /**
     * Helper method to check if Connected MAC Randomization is supported - onDown events are
     * skipped if this feature is enabled (b/72459123).
     *
     * @return boolean true if Connected MAC randomization is supported, false otherwise
     */
    public boolean isConnectedMacRandomizationEnabled() {
        return mContext.getResources().getBoolean(
                R.bool.config_wifi_connected_mac_randomization_supported);
    }

    /**
     * Helper method to check if WEP networks are deprecated.
     *
     * @return boolean true if WEP networks are deprecated, false otherwise.
     */
    public boolean isWepDeprecated() {
        return mIsWepDeprecated || !mIsWepAllowed.get();
    }

    /**
     * Helper method to check if WEP networks are supported.
     *
     * @return boolean true if WEP networks are supported, false otherwise.
     */
    public boolean isWepSupported() {
        return !mIsWepDeprecated;
    }

    /**
     * Helper method to check if WPA-Personal networks are deprecated.
     *
     * @return boolean true if WPA-Personal networks are deprecated, false otherwise.
     */
    public boolean isWpaPersonalDeprecated() {
        return mIsWpaPersonalDeprecated;
    }

    /**
     * Helper method to check whether this device should disable firmware roaming in idle mode.
     * @return if the device should disable firmware roaming in idle mode.
     */
    public boolean isDisableFirmwareRoamingInIdleMode() {
        return mDisableFirmwareRoamingInIdleMode;
    }

    /**
     * Get the configuration for whether Multi-internet are allowed to
     * connect simultaneously to both 5GHz high and 5GHz low.
     */
    public boolean isSupportMultiInternetDual5G() {
        return mIsSupportMultiInternetDual5G;
    }

    /**
     * Get number of repeated NUD failures needed to disable a network.
     */
    public int getRepeatedNudFailuresThreshold() {
        return mRepeatedNudFailuresThreshold;
    }

    /**
     * Get the time window in millis to count for repeated NUD failures.
     */
    public int getRepeatedNudFailuresWindowMs() {
        return mRepeatedNudFailuresWindowMs;
    }

    /**
     * Helper method to check if the device may not connect to the configuration
     * due to deprecated security type
     */
    public boolean isDeprecatedSecurityTypeNetwork(@Nullable WifiConfiguration config) {
        if (config == null) {
            return false;
        }
        if (isWepDeprecated() && config.isSecurityType(WifiConfiguration.SECURITY_TYPE_WEP)) {
            return true;
        }
        if (isWpaPersonalDeprecated() && config.isWpaPersonalOnlyConfiguration()) {
            return true;
        }
        return false;
    }

    /**
     * Help method to check if WPA3 SAE auto-upgrade is enabled.
     *
     * @return boolean true if auto-upgrade is enabled, false otherwise.
     */
    public boolean isWpa3SaeUpgradeEnabled() {
        return mIsWpa3SaeUpgradeEnabled;
    }

    /**
     * Help method to check if WPA3 SAE auto-upgrade offload is enabled.
     *
     * @return boolean true if auto-upgrade offload is enabled, false otherwise.
     */
    public boolean isWpa3SaeUpgradeOffloadEnabled() {
        return mIsWpa3SaeUpgradeOffloadEnabled;
    }

    /**
     * Helper method to enable WPA3 SAE auto-upgrade offload based on the device capability for
     * CROSS-AKM support.
     */
    public void setWpa3SaeUpgradeOffloadEnabled() {
        Log.d(TAG, "Device supports CROSS-AKM feature - Enable WPA3 SAE auto-upgrade offload");
        mIsWpa3SaeUpgradeOffloadEnabled = true;
    }


    /**
     * Help method to check if OWE auto-upgrade is enabled.
     *
     * @return boolean true if auto-upgrade is enabled, false otherwise.
     */
    public boolean isOweUpgradeEnabled() {
        // OWE auto-upgrade is supported on S or newer releases.
        return SdkLevel.isAtLeastS() && mIsOweUpgradeEnabled;
    }

    /**
     * Help method to check if the setting to flush ANQP cache when Wi-Fi is toggled off.
     *
     * @return boolean true to flush ANQP cache on Wi-Fi toggle off event, false otherwise.
     */
    public boolean flushAnqpCacheOnWifiToggleOffEvent() {
        return mFlushAnqpCacheOnWifiToggleOffEvent;
    }

    /*
     * Help method to check if WPA3 SAE Hash-to-Element is supported on this device.
     *
     * @return boolean true if supported;otherwise false.
     */
    public boolean isWpa3SaeH2eSupported() {
        return mIsWpa3SaeH2eSupported;
    }

    /**
     * Record the verbose logging level
     */
    public void setVerboseLoggingLevel(int level) {
        mVerboseLoggingLevel = level;
    }

    /** Return the currently set verbose logging level. */
    public int getVerboseLoggingLevel() {
        return mVerboseLoggingLevel;
    }

    /** Check if show key verbose logging mode is enabled. */
    public boolean getShowKeyVerboseLoggingModeEnabled() {
        return mVerboseLoggingLevel == WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED_SHOW_KEY;
    }

    /** Set whether the external scorer is being used **/
    public void setUsingExternalScorer(boolean isUsingExternalScorer) {
        mIsUsingExternalScorer = isUsingExternalScorer;
    }

    /** Get whether the external scorer is being used **/
    public boolean isUsingExternalScorer() {
        return mIsUsingExternalScorer;
    }

    /** Get the prefix of the default wifi p2p device name. */
    public String getWifiP2pDeviceNamePrefix() {
        return mP2pDeviceNamePrefix;
    }

    /** Get the number of the default wifi p2p device name postfix digit. */
    public int getWifiP2pDeviceNamePostfixNumDigits() {
        return mP2pDeviceNamePostfixNumDigits;
    }

    /** Get the number of log records to maintain. */
    public int getClientModeImplNumLogRecs() {
        return mClientModeImplNumLogRecs;
    }

    /** Get whether to use the saved factory MAC address when available **/
    public boolean isSaveFactoryMacToConfigStoreEnabled() {
        return mSaveFactoryMacToConfigStoreEnabled;
    }

    /** Get the low score threshold to do scan for MBB when external scorer is not used. **/
    public int getWifiLowConnectedScoreThresholdToTriggerScanForMbb() {
        return mWifiLowConnectedScoreThresholdToTriggerScanForMbb;
    }

    /** Get the minimum period between the extra scans triggered for MBB when score is low **/
    public int getWifiLowConnectedScoreScanPeriodSeconds() {
        return mWifiLowConnectedScoreScanPeriodSeconds;
    }

    /** Get whether or not insecure enterprise configuration is allowed. */
    public boolean isInsecureEnterpriseConfigurationAllowed() {
        return mWifiAllowInsecureEnterpriseConfiguration;
    }

    /** Get whether or not P2P MAC randomization is supported */
    public boolean isP2pMacRandomizationSupported() {
        return mIsP2pMacRandomizationSupported;
    }

    /** Get the regular (short) interval between RSSI polls, in milliseconds. */
    public int getPollRssiShortIntervalMillis() {
        return mPollRssiShortIntervalMillis;
    }

    /**
     * Get the long interval between RSSI polls, in milliseconds. The long interval is to
     * reduce power consumption of the polls. This value should be greater than the regular
     * interval.
     */
    public int getPollRssiLongIntervalMillis() {
        return mPollRssiLongIntervalMillis;
    }

    /**
     * Get the RSSI threshold for client mode RSSI monitor, in dBm. If the device is stationary
     * and current RSSI >= Threshold + Hysteresis value, set long interval and enable RSSI
     * monitoring using the RSSI threshold. If device is non-stationary or current RSSI <=
     * Threshold, set regular interval and disable RSSI monitoring.
     */
    public int getClientRssiMonitorThresholdDbm() {
        return mClientRssiMonitorThresholdDbm;
    }

    /**
     * Get the hysteresis value in dB for the client mode RSSI monitor threshold. It can avoid
     * frequent switch between regular and long polling intervals.
     */
    public int getClientRssiMonitorHysteresisDb() {
        return mClientRssiMonitorHysteresisDb;
    }

    /**
     * Get whether adjusting the RSSI polling interval between regular and long intervals
     * is enabled.
     */
    public boolean isAdjustPollRssiIntervalEnabled() {
        return mAdjustPollRssiIntervalEnabled;
    }

    /**
     * Get whether hot-plugging an interface will trigger a restart of the wifi stack.
     */
    public boolean isWifiInterfaceAddedSelfRecoveryEnabled() {
        return mWifiInterfaceAddedSelfRecoveryEnabled;
    }

    /**
     * Get whether background scan is supported.
     */
    public boolean isBackgroundScanSupported() {
        return mIsBackgroundScanSupported;
    };

    /**
     * Get whether software pno is enabled.
     */
    public boolean isSwPnoEnabled() {
        return mIsSwPnoEnabled;
    };

    /**
     * Get whether to temporarily disable a unwanted network that has low RSSI.
     */
    public boolean disableUnwantedNetworkOnLowRssi() {
        return mDisableUnwantedNetworkOnLowRssi;
    }

    /**
     * Get whether to disable NUD disconnects for WAPI configurations in a specific CC.
     */
    public boolean disableNudDisconnectsForWapiInSpecificCc() {
        return mDisableNudDisconnectsForWapiInSpecificCc;
    }

    /**
     * Get the threshold to use for blocking a network due to NETWORK_NOT_FOUND_EVENT failure.
     */
    public int getNetworkNotFoundEventThreshold() {
        return mNetworkNotFoundEventThreshold;
    }

    /**
     * Set whether wep network is allowed by user.
     */
    public void setWepAllowed(boolean isAllowed) {
        mIsWepAllowed.set(isAllowed);
    }

    /**
     * Get whether or not wep network is allowed by user.
     */
    public boolean isWepAllowed() {
        return mIsWepAllowed.get();
    }

    /**
     * Set whether the device supports device-to-device + STA concurrency.
     */
    public void setD2dStaConcurrencySupported(boolean isSupported) {
        mIsD2dStaConcurrencySupported.set(isSupported);
    }

    /**
     * Returns whether the device supports device-to-device when infra STA is disabled.
     */
    public boolean isD2dSupportedWhenInfraStaDisabled() {
        return mD2dAllowedControlSupportedWhenInfraStaDisabled
                && !mIsD2dStaConcurrencySupported.get();
    }

    /**
     * Set the global dhcp hostname restriction.
     */
    public void setSendDhcpHostnameRestriction(
            @WifiManager.SendDhcpHostnameRestriction int restriction) {
        mSendDhcpHostnameRestriction.set(restriction);
    }

    /**
     * Get the global dhcp hostname restriction.
     */
    @WifiManager.SendDhcpHostnameRestriction
    public int getSendDhcpHostnameRestriction() {
        return mSendDhcpHostnameRestriction.get();
    }

    /**
     * Get the maximum Wifi temporary disable duration.
     */
    public long getWifiConfigMaxDisableDurationMs() {
        return mWifiConfigMaxDisableDurationMs;
    }

    /**
     * Force Overlay Config Value for background scan.
     */
    public boolean forceOverlayConfigValue(String configString, String value,
            boolean isEnabled) {
        if (!mOverrideMethods.containsKey(configString)) {
            return false;
        }
        return mOverrideMethods.get(configString).apply(value, isEnabled);
    }

    /** Dump method for debugging */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of WifiGlobals");
        pw.println("mPollRssiIntervalMillis=" + mPollRssiIntervalMillis.get());
        pw.println("mIpReachabilityDisconnectEnabled=" + mIpReachabilityDisconnectEnabled.get());
        pw.println("mIsBluetoothConnected=" + mIsBluetoothConnected.get());
        pw.println("mIsWpa3SaeUpgradeEnabled=" + mIsWpa3SaeUpgradeEnabled);
        pw.println("mIsWpa3SaeUpgradeOffloadEnabled=" + mIsWpa3SaeUpgradeOffloadEnabled);
        pw.println("mIsOweUpgradeEnabled=" + mIsOweUpgradeEnabled);
        pw.println("mFlushAnqpCacheOnWifiToggleOffEvent=" + mFlushAnqpCacheOnWifiToggleOffEvent);
        pw.println("mIsWpa3SaeH2eSupported=" + mIsWpa3SaeH2eSupported);
        pw.println("mP2pDeviceNamePrefix=" + mP2pDeviceNamePrefix);
        pw.println("mP2pDeviceNamePostfixNumDigits=" + mP2pDeviceNamePostfixNumDigits);
        pw.println("mClientModeImplNumLogRecs=" + mClientModeImplNumLogRecs);
        pw.println("mSaveFactoryMacToConfigStoreEnabled=" + mSaveFactoryMacToConfigStoreEnabled);
        pw.println("mWifiLowConnectedScoreThresholdToTriggerScanForMbb="
                + mWifiLowConnectedScoreThresholdToTriggerScanForMbb);
        pw.println("mWifiLowConnectedScoreScanPeriodSeconds="
                + mWifiLowConnectedScoreScanPeriodSeconds);
        pw.println("mIsUsingExternalScorer="
                + mIsUsingExternalScorer);
        pw.println("mWifiAllowInsecureEnterpriseConfiguration="
                + mWifiAllowInsecureEnterpriseConfiguration);
        pw.println("mIsP2pMacRandomizationSupported" + mIsP2pMacRandomizationSupported);
        pw.println("mWifiInterfaceAddedSelfRecoveryEnabled="
                + mWifiInterfaceAddedSelfRecoveryEnabled);
        pw.println("mDisableUnwantedNetworkOnLowRssi=" + mDisableUnwantedNetworkOnLowRssi);
        pw.println("mNetworkNotFoundEventThreshold=" + mNetworkNotFoundEventThreshold);
        pw.println("mIsBackgroundScanSupported=" + mIsBackgroundScanSupported);
        pw.println("mIsSwPnoEnabled=" + mIsSwPnoEnabled);
        pw.println("mIsWepDeprecated=" + mIsWepDeprecated);
        pw.println("mIsWpaPersonalDeprecated=" + mIsWpaPersonalDeprecated);
        pw.println("mIsWepAllowed=" + mIsWepAllowed.get());
        pw.println("mDisableFirmwareRoamingInIdleMode=" + mDisableFirmwareRoamingInIdleMode);
        pw.println("mRepeatedNudFailuresThreshold=" + mRepeatedNudFailuresThreshold);
        pw.println("mRepeatedNudFailuresWindowMs=" + mRepeatedNudFailuresWindowMs);
        pw.println("mCarrierSpecificEapFailureConfigMapPerCarrierId mapping below:");
        pw.println("mWifiConfigMaxDisableDurationMs=" + mWifiConfigMaxDisableDurationMs);
        pw.println("mD2dAllowedControlSupportedWhenInfraStaDisabled="
                + mD2dAllowedControlSupportedWhenInfraStaDisabled);
        pw.println("IsD2dSupportedWhenInfraStaDisabled="
                + isD2dSupportedWhenInfraStaDisabled());
        for (int i = 0; i < mCarrierSpecificEapFailureConfigMapPerCarrierId.size(); i++) {
            int carrierId = mCarrierSpecificEapFailureConfigMapPerCarrierId.keyAt(i);
            SparseArray<CarrierSpecificEapFailureConfig> perFailureMap =
                    mCarrierSpecificEapFailureConfigMapPerCarrierId.valueAt(i);
            for (int j = 0; j < perFailureMap.size(); j++) {
                int eapFailureCode = perFailureMap.keyAt(j);
                pw.println("carrierId=" + carrierId
                        + ", eapFailureCode=" + eapFailureCode
                        + ", displayNotification=" + perFailureMap.valueAt(j).displayNotification
                        + ", threshold=" + perFailureMap.valueAt(j).threshold
                        + ", durationMs=" + perFailureMap.valueAt(j).durationMs);
            }
        }
        pw.println("mIsSupportMultiInternetDual5G=" + mIsSupportMultiInternetDual5G);
        pw.println("mSendDhcpHostnameRestriction=" + mSendDhcpHostnameRestriction.get());
    }
}
