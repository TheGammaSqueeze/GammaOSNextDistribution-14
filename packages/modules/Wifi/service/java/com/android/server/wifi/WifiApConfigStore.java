/*
 * Copyright (C) 2010 The Android Open Source Project
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

import static android.net.wifi.SoftApConfiguration.SECURITY_TYPE_WPA2_PSK;
import static android.net.wifi.SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION;
import static android.net.wifi.SoftApConfiguration.SECURITY_TYPE_WPA3_SAE;
import static android.net.wifi.SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION;

import android.annotation.NonNull;
import android.app.compat.CompatChanges;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApConfiguration.BandType;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.MacAddressUtils;
import com.android.server.wifi.util.ApConfigUtil;
import com.android.wifi.resources.R;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

import javax.annotation.Nullable;

/**
 * Provides API for reading/writing soft access point configuration.
 */
public class WifiApConfigStore {

    // Intent when user has interacted with the softap settings change notification
    public static final String ACTION_HOTSPOT_CONFIG_USER_TAPPED_CONTENT =
            "com.android.server.wifi.WifiApConfigStoreUtil.HOTSPOT_CONFIG_USER_TAPPED_CONTENT";

    private static final String TAG = "WifiApConfigStore";

    private static final int RAND_SSID_INT_MIN = 1000;
    private static final int RAND_SSID_INT_MAX = 9999;

    @VisibleForTesting
    static final int SAE_ASCII_MIN_LEN = 1;
    @VisibleForTesting
    static final int PSK_ASCII_MIN_LEN = 8;
    @VisibleForTesting
    static final int PSK_SAE_ASCII_MAX_LEN = 63;

    // Should only be accessed via synchronized methods.
    private SoftApConfiguration mPersistentWifiApConfig = null;
    private String mLastConfiguredPassphrase = null;

    private final Context mContext;
    private final Handler mHandler;
    private final WifiMetrics mWifiMetrics;
    private final BackupManagerProxy mBackupManagerProxy;
    private final MacAddressUtil mMacAddressUtil;
    private final WifiConfigManager mWifiConfigManager;
    private final ActiveModeWarden mActiveModeWarden;
    private final WifiNative mWifiNative;
    private boolean mHasNewDataToSerialize = false;
    private boolean mForceApChannel = false;
    private int mForcedApBand;
    private int mForcedApChannel;
    private int mForcedApMaximumChannelBandWidth;
    private final boolean mIsAutoAppendLowerBandEnabled;

    /**
     * Module to interact with the wifi config store.
     */
    private class SoftApStoreDataSource implements SoftApStoreData.DataSource {

        public SoftApConfiguration toSerialize() {
            mHasNewDataToSerialize = false;
            return mPersistentWifiApConfig;
        }

        public void fromDeserialized(SoftApConfiguration config) {
            if (config.getPersistentRandomizedMacAddress() == null) {
                config = updatePersistentRandomizedMacAddress(config);
            }
            mPersistentWifiApConfig = new SoftApConfiguration.Builder(config).build();
            if (!TextUtils.isEmpty(mPersistentWifiApConfig.getPassphrase())) {
                mLastConfiguredPassphrase = mPersistentWifiApConfig.getPassphrase();
            }
        }

        public void reset() {
            mPersistentWifiApConfig = null;
        }

        public boolean hasNewDataToSerialize() {
            return mHasNewDataToSerialize;
        }
    }

    WifiApConfigStore(Context context,
            WifiInjector wifiInjector,
            Handler handler,
            BackupManagerProxy backupManagerProxy,
            WifiConfigStore wifiConfigStore,
            WifiConfigManager wifiConfigManager,
            ActiveModeWarden activeModeWarden,
            WifiMetrics wifiMetrics) {
        mContext = context;
        mHandler = handler;
        mBackupManagerProxy = backupManagerProxy;
        mWifiConfigManager = wifiConfigManager;
        mActiveModeWarden = activeModeWarden;
        mWifiMetrics = wifiMetrics;
        mWifiNative = wifiInjector.getWifiNative();
        // Register store data listener
        wifiConfigStore.registerStoreData(
                wifiInjector.makeSoftApStoreData(new SoftApStoreDataSource()));

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_HOTSPOT_CONFIG_USER_TAPPED_CONTENT);
        mMacAddressUtil = wifiInjector.getMacAddressUtil();
        mIsAutoAppendLowerBandEnabled = mContext.getResources().getBoolean(
                R.bool.config_wifiSoftapAutoAppendLowerBandsToBandConfigurationEnabled);
    }

    /**
     * Return the current soft access point configuration.
     */
    public synchronized SoftApConfiguration getApConfiguration() {
        if (mPersistentWifiApConfig == null) {
            /* Use default configuration. */
            Log.d(TAG, "Fallback to use default AP configuration");
            persistConfigAndTriggerBackupManagerProxy(
                    updatePersistentRandomizedMacAddress(getDefaultApConfiguration()));
        }
        SoftApConfiguration sanitizedPersistentconfig =
                sanitizePersistentApConfig(mPersistentWifiApConfig);
        if (!Objects.equals(mPersistentWifiApConfig, sanitizedPersistentconfig)) {
            Log.d(TAG, "persisted config was converted, need to resave it");
            persistConfigAndTriggerBackupManagerProxy(sanitizedPersistentconfig);
        }

        if (mForceApChannel) {
            Log.d(TAG, "getApConfiguration: Band force to "
                    + mForcedApBand
                    + ", and channel force to "
                    + mForcedApChannel
                    + ", and maximum channel width limited to "
                    + mForcedApMaximumChannelBandWidth);
            if (SdkLevel.isAtLeastT()) {
                return mForcedApChannel == 0
                        ? new SoftApConfiguration.Builder(mPersistentWifiApConfig)
                                .setBand(mForcedApBand)
                                .setMaxChannelBandwidth(mForcedApMaximumChannelBandWidth)
                                .build()
                        : new SoftApConfiguration.Builder(mPersistentWifiApConfig)
                                .setChannel(mForcedApChannel, mForcedApBand)
                                .setMaxChannelBandwidth(mForcedApMaximumChannelBandWidth)
                                .build();
            } else {
                return mForcedApChannel == 0
                        ? new SoftApConfiguration.Builder(mPersistentWifiApConfig)
                                .setBand(mForcedApBand)
                                .build()
                        : new SoftApConfiguration.Builder(mPersistentWifiApConfig)
                                .setChannel(mForcedApChannel, mForcedApBand)
                                .build();
            }
        }
        return mPersistentWifiApConfig;
    }

    /**
     * Update the current soft access point configuration.
     * Restore to default AP configuration if null is provided.
     * This can be invoked under context of binder threads (WifiManager.setWifiApConfiguration)
     * and the main Wifi thread (CMD_START_AP).
     */
    public synchronized void setApConfiguration(SoftApConfiguration config) {
        SoftApConfiguration newConfig = config == null ? getDefaultApConfiguration()
                : new SoftApConfiguration.Builder(sanitizePersistentApConfig(config))
                        .setUserConfiguration(true).build();
        persistConfigAndTriggerBackupManagerProxy(
                updatePersistentRandomizedMacAddress(newConfig));
    }

    /**
     * Returns SoftApConfiguration in which some parameters might be upgrade to supported default
     * configuration.
     */
    public synchronized SoftApConfiguration upgradeSoftApConfiguration(
            @NonNull SoftApConfiguration config) {
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder(config);
        if (SdkLevel.isAtLeastS() && ApConfigUtil.isBridgedModeSupported(mContext, mWifiNative)
                && config.getBands().length == 1 && mContext.getResources().getBoolean(
                        R.bool.config_wifiSoftapAutoUpgradeToBridgedConfigWhenSupported)) {
            int[] dual_bands = new int[] {
                    SoftApConfiguration.BAND_2GHZ,
                    SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ};
            if (SdkLevel.isAtLeastS()) {
                configBuilder.setBands(dual_bands);
            }
            Log.i(TAG, "Device support bridged AP, upgrade band setting to bridged configuration");
        }
        return configBuilder.build();
    }

    /**
     * Returns SoftApConfiguration in which some parameters might be reset to supported default
     * config since it depends on UI or HW.
     *
     * MaxNumberOfClients and isClientControlByUserEnabled will need HAL support client force
     * disconnect, and Band setting (5g/6g) need HW support.
     *
     * HiddenSsid, Channel, ShutdownTimeoutMillis and AutoShutdownEnabled are features
     * which need UI(Setting) support.
     *
     * SAE/SAE-Transition need hardware support, reset to secured WPA2 security type when device
     * doesn't support it.
     *
     * Check band(s) setting to make sure all of the band(s) are supported.
     * - If previous bands configuration is bridged mode. Reset to 2.4G when device doesn't support
     *   it.
     */
    public synchronized SoftApConfiguration resetToDefaultForUnsupportedConfig(
            @NonNull SoftApConfiguration config) {
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder(config);
        if ((!ApConfigUtil.isClientForceDisconnectSupported(mContext)
                || mContext.getResources().getBoolean(
                R.bool.config_wifiSoftapResetUserControlConfig))
                && (config.isClientControlByUserEnabled()
                || config.getBlockedClientList().size() != 0)) {
            configBuilder.setClientControlByUserEnabled(false);
            configBuilder.setBlockedClientList(new ArrayList<>());
            Log.i(TAG, "Reset ClientControlByUser to false due to device doesn't support");
        }

        if ((!ApConfigUtil.isClientForceDisconnectSupported(mContext)
                || mContext.getResources().getBoolean(
                R.bool.config_wifiSoftapResetMaxClientSettingConfig))
                && config.getMaxNumberOfClients() != 0) {
            configBuilder.setMaxNumberOfClients(0);
            Log.i(TAG, "Reset MaxNumberOfClients to 0 due to device doesn't support");
        }

        if (!ApConfigUtil.isWpa3SaeSupported(mContext) && (config.getSecurityType()
                == SECURITY_TYPE_WPA3_SAE
                || config.getSecurityType()
                == SECURITY_TYPE_WPA3_SAE_TRANSITION)) {
            try {
                configBuilder.setPassphrase(generatePassword(),
                        SECURITY_TYPE_WPA2_PSK);
            } catch (IllegalArgumentException e) {
                Log.wtf(TAG, "Generated password was invalid: " + e);
            }
            Log.i(TAG, "Device doesn't support WPA3-SAE, reset config to WPA2");
        }

        if (mContext.getResources().getBoolean(R.bool.config_wifiSoftapResetChannelConfig)
                && config.getChannel() != 0) {
            // The device might not support customize channel or forced channel might not
            // work in some countries. Need to reset it.
            configBuilder.setBand(ApConfigUtil.append24GToBandIf24GSupported(
                    config.getBand(), mContext));
            Log.i(TAG, "Reset SAP channel configuration");
        }

        if (SdkLevel.isAtLeastS() && config.getBands().length > 1) {
            if (!ApConfigUtil.isBridgedModeSupported(mContext, mWifiNative)
                    || !isBandsSupported(config.getBands(), mContext)) {
                int newSingleApBand = 0;
                for (int targetBand : config.getBands()) {
                    int availableBand = ApConfigUtil.removeUnsupportedBands(
                            mContext, targetBand);
                    newSingleApBand |= availableBand;
                }
                newSingleApBand = ApConfigUtil.append24GToBandIf24GSupported(
                        newSingleApBand, mContext);
                configBuilder.setBand(newSingleApBand);
                Log.i(TAG, "An unsupported band setting for the bridged mode, force to "
                        + newSingleApBand);
            }
        } else {
            // Single band case, check and remove unsupported band.
            int newBand = ApConfigUtil.removeUnsupportedBands(mContext, config.getBand());
            if (newBand != config.getBand()) {
                newBand = ApConfigUtil.append24GToBandIf24GSupported(newBand, mContext);
                Log.i(TAG, "Reset band from " + config.getBand() + " to "
                        + newBand);
                configBuilder.setBand(newBand);
            }
        }

        if (mContext.getResources().getBoolean(R.bool.config_wifiSoftapResetHiddenConfig)
                && config.isHiddenSsid()) {
            configBuilder.setHiddenSsid(false);
            Log.i(TAG, "Reset SAP Hidden Network configuration");
        }

        if (mContext.getResources().getBoolean(
                R.bool.config_wifiSoftapResetAutoShutdownTimerConfig)
                && config.getShutdownTimeoutMillis() > 0) {
            if (CompatChanges.isChangeEnabled(
                    SoftApConfiguration.REMOVE_ZERO_FOR_TIMEOUT_SETTING)) {
                configBuilder.setShutdownTimeoutMillis(SoftApConfiguration.DEFAULT_TIMEOUT);
            } else {
                configBuilder.setShutdownTimeoutMillis(0);
            }
            Log.i(TAG, "Reset SAP auto shutdown configuration");
        }

        if (!ApConfigUtil.isApMacRandomizationSupported(mContext)) {
            if (SdkLevel.isAtLeastS()) {
                configBuilder.setMacRandomizationSetting(SoftApConfiguration.RANDOMIZATION_NONE);
                Log.i(TAG, "Force set SAP MAC randomization to NONE when not supported");
            }
        }

        mWifiMetrics.noteSoftApConfigReset(config, configBuilder.build());
        return configBuilder.build();
    }

    private SoftApConfiguration sanitizePersistentApConfig(SoftApConfiguration config) {
        SoftApConfiguration.Builder convertedConfigBuilder =
                new SoftApConfiguration.Builder(config);
        int[] bands = config.getBands();
        SparseIntArray newChannels = new SparseIntArray();
        // The bands length should always 1 in R. Adding SdkLevel.isAtLeastS for lint check only.
        for (int i = 0; i < bands.length; i++) {
            int channel = SdkLevel.isAtLeastS()
                    ? config.getChannels().valueAt(i) : config.getChannel();
            int newBand = bands[i];
            if (channel == 0 && mIsAutoAppendLowerBandEnabled
                    && ApConfigUtil.isBandSupported(newBand, mContext)) {
                // some countries are unable to support 5GHz only operation, always allow for 2GHz
                // when config doesn't force channel
                if ((newBand & SoftApConfiguration.BAND_2GHZ) == 0) {
                    newBand = ApConfigUtil.append24GToBandIf24GSupported(newBand, mContext);
                }
                // If the 6G configuration doesn't includes 5G band (2.4G have appended because
                // countries reason), it will cause that driver can't switch channel from 6G to
                // 5G/2.4G when coexistence happened (For instance: wifi connected to 2.4G or 5G
                // channel). Always append 5G into band configuration when configured band includes
                // 6G.
                if ((newBand & SoftApConfiguration.BAND_6GHZ) != 0
                        && (newBand & SoftApConfiguration.BAND_5GHZ) == 0) {
                    newBand = ApConfigUtil.append5GToBandIf5GSupported(newBand, mContext);
                }
            }
            newChannels.put(newBand, channel);
        }
        if (SdkLevel.isAtLeastS()) {
            convertedConfigBuilder.setChannels(newChannels);
        } else if (bands.length > 0 && newChannels.valueAt(0) == 0) {
            convertedConfigBuilder.setBand(newChannels.keyAt(0));
        }
        return convertedConfigBuilder.build();
    }

    private synchronized void persistConfigAndTriggerBackupManagerProxy(
            SoftApConfiguration config) {
        mPersistentWifiApConfig = config;
        if (!TextUtils.isEmpty(config.getPassphrase())) {
            mLastConfiguredPassphrase = config.getPassphrase();
        }
        mHasNewDataToSerialize = true;
        mHandler.post(() -> mWifiConfigManager.saveToStore(true));
        mBackupManagerProxy.notifyDataChanged();
    }

    /**
     * Generate a default WPA3 SAE transition (if supported) or WPA2 based
     * configuration with a random password.
     * We are changing the Wifi Ap configuration storage from secure settings to a
     * flat file accessible only by the system. A WPA2 based default configuration
     * will keep the device secure after the update.
     */
    private SoftApConfiguration getDefaultApConfiguration() {
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setBand(generateDefaultBand(mContext));
        configBuilder.setSsid(mContext.getResources().getString(
                R.string.wifi_tether_configure_ssid_default) + "_" + getRandomIntForDefaultSsid());
        try {
            if (ApConfigUtil.isWpa3SaeSupported(mContext)) {
                configBuilder.setPassphrase(generatePassword(),
                        SECURITY_TYPE_WPA3_SAE_TRANSITION);
            } else {
                configBuilder.setPassphrase(generatePassword(),
                        SECURITY_TYPE_WPA2_PSK);
            }
        } catch (IllegalArgumentException e) {
            Log.wtf(TAG, "Generated password was invalid: " + e);
        }

        // It is new overlay configuration, it should always false in R. Add SdkLevel.isAtLeastS for
        // lint check
        if (ApConfigUtil.isBridgedModeSupported(mContext, mWifiNative)) {
            if (SdkLevel.isAtLeastS()) {
                int[] dual_bands = new int[] {
                        SoftApConfiguration.BAND_2GHZ,
                        SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ};
                configBuilder.setBands(dual_bands);
            }
        }

        // Update default MAC randomization setting to NONE when feature doesn't support it.
        if (!ApConfigUtil.isApMacRandomizationSupported(mContext)) {
            if (SdkLevel.isAtLeastS()) {
                configBuilder.setMacRandomizationSetting(SoftApConfiguration.RANDOMIZATION_NONE);
            }
        }

        configBuilder.setUserConfiguration(false);
        return configBuilder.build();
    }

    private static int getRandomIntForDefaultSsid() {
        Random random = new Random();
        return random.nextInt((RAND_SSID_INT_MAX - RAND_SSID_INT_MIN) + 1) + RAND_SSID_INT_MIN;
    }

    private static String generateLohsSsid(Context context) {
        return context.getResources().getString(
                R.string.wifi_localhotspot_configure_ssid_default) + "_"
                + getRandomIntForDefaultSsid();
    }

    private static boolean hasAutomotiveFeature(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    /**
     * Generate a temporary WPA2 based configuration for use by the local only hotspot.
     * This config is not persisted and will not be stored by the WifiApConfigStore.
     */
    public SoftApConfiguration generateLocalOnlyHotspotConfig(@NonNull Context context,
            @Nullable SoftApConfiguration customConfig, @NonNull SoftApCapability capability) {
        SoftApConfiguration.Builder configBuilder;
        if (customConfig != null) {
            configBuilder = new SoftApConfiguration.Builder(customConfig);
            // Make sure that we use available band on old build.
            if (!SdkLevel.isAtLeastT()
                    && !isBandsSupported(customConfig.getBands(), context)) {
                configBuilder.setBand(generateDefaultBand(context));
            }
        } else {
            configBuilder = new SoftApConfiguration.Builder();
            // Make sure the default band configuration is supported.
            configBuilder.setBand(generateDefaultBand(context));
            // Default to disable the auto shutdown
            configBuilder.setAutoShutdownEnabled(false);
            try {
                if (ApConfigUtil.isWpa3SaeSupported(context)) {
                    configBuilder.setPassphrase(generatePassword(),
                            SECURITY_TYPE_WPA3_SAE_TRANSITION);
                } else {
                    configBuilder.setPassphrase(generatePassword(),
                            SECURITY_TYPE_WPA2_PSK);
                }
            } catch (IllegalArgumentException e) {
                Log.wtf(TAG, "Generated password was invalid: " + e);
            }
            synchronized (this) {
                // Update default MAC randomization setting to NONE when feature doesn't support
                // it, or it was disabled in tethered mode.
                if (!ApConfigUtil.isApMacRandomizationSupported(context)
                        || (mPersistentWifiApConfig != null
                        && mPersistentWifiApConfig.getMacRandomizationSettingInternal()
                        == SoftApConfiguration.RANDOMIZATION_NONE)) {
                    if (SdkLevel.isAtLeastS()) {
                        configBuilder.setMacRandomizationSetting(
                                SoftApConfiguration.RANDOMIZATION_NONE);
                    }
                }
            }
        }

        // Automotive mode can force the LOHS to specific bands
        if (hasAutomotiveFeature(context)) {
            int desiredBand = SoftApConfiguration.BAND_2GHZ;
            if (context.getResources().getBoolean(R.bool.config_wifiLocalOnlyHotspot6ghz)
                    && ApConfigUtil.isBandSupported(SoftApConfiguration.BAND_6GHZ, mContext)) {
                desiredBand |= SoftApConfiguration.BAND_6GHZ;
            }
            if (context.getResources().getBoolean(R.bool.config_wifi_local_only_hotspot_5ghz)
                    && ApConfigUtil.isBandSupported(SoftApConfiguration.BAND_5GHZ, mContext)) {
                desiredBand |= SoftApConfiguration.BAND_5GHZ;
            }
            configBuilder.setBand(desiredBand);
        }
        if (customConfig == null || customConfig.getSsid() == null) {
            configBuilder.setSsid(generateLohsSsid(context));
        }

        return updatePersistentRandomizedMacAddress(configBuilder.build());
    }

    /**
     * @return a copy of the given SoftApConfig with the BSSID randomized, unless a custom BSSID is
     * already set.
     */
    SoftApConfiguration randomizeBssidIfUnset(Context context, SoftApConfiguration config) {
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder(config);
        if (config.getBssid() == null && ApConfigUtil.isApMacRandomizationSupported(mContext)
                && config.getMacRandomizationSettingInternal()
                    != SoftApConfiguration.RANDOMIZATION_NONE) {
            MacAddress macAddress = null;
            if (config.getMacRandomizationSettingInternal()
                    == SoftApConfiguration.RANDOMIZATION_PERSISTENT) {
                macAddress = config.getPersistentRandomizedMacAddress();
                if (macAddress == null) {
                    WifiSsid ssid = config.getWifiSsid();
                    macAddress = mMacAddressUtil.calculatePersistentMacForSap(
                            ssid != null ? ssid.toString() : null, Process.WIFI_UID);
                    if (macAddress == null) {
                        Log.e(TAG, "Failed to calculate MAC from SSID. "
                                + "Generating new random MAC instead.");
                    }
                }
            }
            if (macAddress == null) {
                macAddress = MacAddressUtils.createRandomUnicastAddress();
            }
            configBuilder.setBssid(macAddress);
            if (macAddress != null && SdkLevel.isAtLeastS()) {
                configBuilder.setMacRandomizationSetting(SoftApConfiguration.RANDOMIZATION_NONE);
            }
        }
        return configBuilder.build();
    }

    /**
     * Verify provided preSharedKey in ap config for WPA2_PSK/WPA3_SAE (Transition) network
     * meets requirements.
     */
    @SuppressWarnings("ReturnValueIgnored")
    private static boolean validateApConfigAsciiPreSharedKey(
            @SoftApConfiguration.SecurityType int securityType, String preSharedKey) {
        final int sharedKeyLen = preSharedKey.length();
        final int keyMinLen = securityType == SECURITY_TYPE_WPA3_SAE
                ? SAE_ASCII_MIN_LEN : PSK_ASCII_MIN_LEN;
        if (sharedKeyLen < keyMinLen || sharedKeyLen > PSK_SAE_ASCII_MAX_LEN) {
            Log.d(TAG, "softap network password string size must be at least " + keyMinLen
                    + " and no more than " + PSK_SAE_ASCII_MAX_LEN + " when type is "
                    + securityType);
            return false;
        }

        try {
            preSharedKey.getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "softap network password verification failed: malformed string");
            return false;
        }
        return true;
    }

    /**
     * Validate a SoftApConfiguration is properly configured for use by SoftApManager.
     *
     * This method checks for consistency between security settings (if it requires a password, was
     * one provided?).
     *
     * @param apConfig {@link SoftApConfiguration} to use for softap mode
     * @param isPrivileged indicate the caller can pass some fields check or not
     * @param wifiNative to use native API to get iface combinations.
     * @return boolean true if the provided config meets the minimum set of details, false
     * otherwise.
     */
    static boolean validateApWifiConfiguration(@NonNull SoftApConfiguration apConfig,
            boolean isPrivileged, Context context, WifiNative wifiNative) {
        // first check the SSID
        WifiSsid ssid = apConfig.getWifiSsid();
        if (ssid == null || ssid.getBytes().length == 0) {
            Log.d(TAG, "SSID for softap configuration cannot be null or 0 length.");
            return false;
        }

        // BSSID can be set if caller own permission:android.Manifest.permission.NETWORK_SETTINGS.
        if (apConfig.getBssid() != null && !isPrivileged) {
            Log.e(TAG, "Config BSSID needs NETWORK_SETTINGS permission");
            return false;
        }

        String preSharedKey = apConfig.getPassphrase();
        boolean hasPreSharedKey = !TextUtils.isEmpty(preSharedKey);
        int authType;

        try {
            authType = apConfig.getSecurityType();
        } catch (IllegalStateException e) {
            Log.d(TAG, "Unable to get AuthType for softap config: " + e.getMessage());
            return false;
        }

        if (ApConfigUtil.isNonPasswordAP(authType)) {
            // open networks should not have a password
            if (hasPreSharedKey) {
                Log.d(TAG, "open softap network should not have a password");
                return false;
            }
        } else if (authType == SECURITY_TYPE_WPA2_PSK
                || authType == SECURITY_TYPE_WPA3_SAE_TRANSITION
                || authType == SECURITY_TYPE_WPA3_SAE) {
            // this is a config that should have a password - check that first
            if (!hasPreSharedKey) {
                Log.d(TAG, "softap network password must be set");
                return false;
            }

            if (context.getResources().getBoolean(
                    R.bool.config_wifiSoftapPassphraseAsciiEncodableCheck)) {
                final CharsetEncoder asciiEncoder = StandardCharsets.US_ASCII.newEncoder();
                if (!asciiEncoder.canEncode(preSharedKey)) {
                    Log.d(TAG, "passphrase not ASCII encodable");
                    return false;
                }
                if (!validateApConfigAsciiPreSharedKey(authType, preSharedKey)) {
                    // failed preSharedKey checks for WPA2 and WPA3 SAE (Transition) mode.
                    return false;
                }
            }
        } else {
            // this is not a supported security type
            Log.d(TAG, "softap configs must either be open or WPA2 PSK networks");
            return false;
        }

        if (!isBandsSupported(apConfig.getBands(), context)) {
            return false;
        }

        if (ApConfigUtil.isSecurityTypeRestrictedFor6gBand(authType)) {
            for (int band : apConfig.getBands()) {
                // Only return failure if requested band is limited to 6GHz only
                if (band == SoftApConfiguration.BAND_6GHZ
                        && !ApConfigUtil.canHALConvertRestrictedSecurityTypeFor6GHz(
                                context.getResources(), authType)) {
                    Log.d(TAG, "security type: " +  authType
                            + " is not allowed for softap in 6GHz band");
                    return false;
                }
            }
        }

        if (SdkLevel.isAtLeastT()
                && authType == SECURITY_TYPE_WPA3_OWE_TRANSITION) {
            if (!ApConfigUtil.isBridgedModeSupported(context, wifiNative)) {
                Log.d(TAG, "softap owe transition needs bridge mode support");
                return false;
            } else if (apConfig.getBands().length > 1) {
                Log.d(TAG, "softap owe transition must use single band");
                return false;
            }
        }

        return true;
    }

    private static String generatePassword() {
        // Characters that will be used for password generation. Some characters commonly known to
        // be confusing like 0 and O excluded from this list.
        final String allowed = "23456789abcdefghijkmnpqrstuvwxyz";
        final int passLength = 15;

        StringBuilder sb = new StringBuilder(passLength);
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < passLength; i++) {
            sb.append(allowed.charAt(random.nextInt(allowed.length())));
        }
        return sb.toString();
    }

    /**
     * Generate default band base on supported band configuration.
     *
     * @param context The caller context used to get value from resource file.
     * @return A band which will be used for a default band in default configuration.
     */
    public static @BandType int generateDefaultBand(Context context) {
        for (int band : SoftApConfiguration.BAND_TYPES) {
            if (ApConfigUtil.isBandSupported(band, context)) {
                return band;
            }
        }
        Log.e(TAG, "Invalid overlay configuration! No any band supported on SoftAp");
        return SoftApConfiguration.BAND_2GHZ;
    }

    private static boolean isBandsSupported(@NonNull int[] apBands, Context context) {
        for (int band : apBands) {
            if (!ApConfigUtil.isBandSupported(band, context)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Enable force-soft-AP-channel mode which takes effect when soft AP starts next time
     *
     * @param forcedApBand The forced band.
     * @param forcedApChannel The forced IEEE channel number or 0 when forced AP band only.
     * @param forcedApMaximumChannelBandWidth The forced maximum channel bandwidth.
     */
    public synchronized void enableForceSoftApBandOrChannel(
            @BandType int forcedApBand, int forcedApChannel, int forcedApMaximumChannelBandWidth) {
        mForceApChannel = true;
        mForcedApChannel = forcedApChannel;
        mForcedApBand = forcedApBand;
        mForcedApMaximumChannelBandWidth = forcedApMaximumChannelBandWidth;
    }

    /**
     * Disable force-soft-AP-channel mode which take effect when soft AP starts next time
     */
    public synchronized void disableForceSoftApBandOrChannel() {
        mForceApChannel = false;
    }

    private SoftApConfiguration updatePersistentRandomizedMacAddress(SoftApConfiguration config) {
        // Update randomized MacAddress
        WifiSsid ssid = config.getWifiSsid();
        MacAddress randomizedMacAddress = mMacAddressUtil.calculatePersistentMacForSap(
                ssid != null ? ssid.toString() : null, Process.WIFI_UID);
        if (randomizedMacAddress != null) {
            return new SoftApConfiguration.Builder(config)
                    .setRandomizedMacAddress(randomizedMacAddress).build();
        }

        if (config.getPersistentRandomizedMacAddress() != null) {
            return config;
        }

        randomizedMacAddress = MacAddressUtils.createRandomUnicastAddress();
        return new SoftApConfiguration.Builder(config)
                .setRandomizedMacAddress(randomizedMacAddress).build();
    }

    /**
     * Returns the last configured Wi-Fi tethered AP passphrase.
     */
    public synchronized String getLastConfiguredTetheredApPassphraseSinceBoot() {
        return mLastConfiguredPassphrase;
    }
}
