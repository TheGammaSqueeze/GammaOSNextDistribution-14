/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_DEFAULT_COUNTRY_CODE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.os.SystemProperties;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.p2p.WifiP2pMetrics;
import com.android.server.wifi.util.ApConfigUtil;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.resources.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Provide functions for making changes to WiFi country code.
 * This Country Code is from MCC or phone default setting. This class sends Country Code
 * to driver through wpa_supplicant when ClientModeImpl marks current state as ready
 * using setReadyForChange(true).
 */
public class WifiCountryCode {
    private static final String TAG = "WifiCountryCode";
    private static final String BOOT_DEFAULT_WIFI_COUNTRY_CODE = "ro.boot.wificountrycode";
    private static final int PKT_COUNT_HIGH_PKT_PER_SEC = 16;
    private static final int DISCONNECT_WIFI_COUNT_MAX = 1;
    /* TODO: replace with PackageManager.FEATURE_TELEPHONY_CALLING once
     * wifi-module-sdk-version-defaults min_sdk_version bumps to API 33. */
    private static final String FEATURE_TELEPHONY_CALLING = "android.hardware.telephony.calling";
    static final int MIN_COUNTRY_CODE_COUNT_US = 3;
    static final int MIN_COUNTRY_CODE_COUNT_OTHER = 2;
    static final String COUNTRY_CODE_US = "US";
    static final int MAX_DURATION_SINCE_LAST_UPDATE_TIME_MS = 500_000;
    static final int MIN_SCAN_RSSI_DBM = -85;
    private final String mWorldModeCountryCode;
    private final Context mContext;
    private final TelephonyManager mTelephonyManager;
    private final ActiveModeWarden mActiveModeWarden;
    private final WifiP2pMetrics mWifiP2pMetrics;
    private final WifiNative mWifiNative;
    private final WifiSettingsConfigStore mSettingsConfigStore;
    private final Clock mClock;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private List<ChangeListener> mListeners = new ArrayList<>();
    private boolean mVerboseLoggingEnabled = false;
    private boolean mIsCountryCodePendingToUpdateToCmm = true; // default to true for first update.
    /**
     * Map of active ClientModeManager instance to whether it is ready for country code change.
     *
     * - When a new ClientModeManager instance is created, it is added to this map and starts out
     * ready for any country code changes (value = true).
     * - When the ClientModeManager instance starts a connection attempt, it is marked not ready for
     * country code changes (value = false).
     * - When the ClientModeManager instance ends the connection, it is again marked ready for
     * country code changes (value = true).
     * - When the ClientModeManager instance is destroyed, it is removed from this map.
     */
    private final Map<ActiveModeManager, Boolean> mAmmToReadyForChangeMap =
            new ArrayMap<>();
    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");

    private String mTelephonyCountryCode = null;
    private String mOverrideCountryCode = null;
    private String mDriverCountryCode = null;
    private String mFrameworkCountryCode = null;
    private String mLastReceivedActiveDriverCountryCode = null;
    private long mDriverCountryCodeUpdatedTimestamp = 0;
    private String mTelephonyCountryTimestamp = null;
    private long mFrameworkCountryCodeUpdatedTimestamp = 0;
    private String mAllCmmReadyTimestamp = null;
    private int mDisconnectWifiToForceUpdateCount = 0;

    private class ModeChangeCallbackInternal implements ActiveModeWarden.ModeChangeCallback {
        @Override
        public void onActiveModeManagerAdded(@NonNull ActiveModeManager activeModeManager) {
            if (activeModeManager.getRole() instanceof ActiveModeManager.ClientRole) {
                // Add this CMM for tracking. Interface is up and HAL is initialized at this point.
                // If this device runs the 1.5 HAL version, use the IWifiChip.setCountryCode()
                // to set the country code.
                mAmmToReadyForChangeMap.put(activeModeManager, true);
                evaluateAllCmmStateAndApplyIfAllReady();
            } else if (activeModeManager instanceof SoftApManager) {
                // Put SoftApManager ready for consistence behavior in mAmmToReadyForChangeMap.
                // No need to trigger CC change because SoftApManager takes CC when starting up.
                mAmmToReadyForChangeMap.put(activeModeManager, true);
            }
        }

        @Override
        public void onActiveModeManagerRemoved(@NonNull ActiveModeManager activeModeManager) {
            if (mAmmToReadyForChangeMap.remove(activeModeManager) != null) {
                if (activeModeManager instanceof ActiveModeManager.ClientRole) {
                    // Remove this CMM from tracking.
                    evaluateAllCmmStateAndApplyIfAllReady();
                }
            }
            if (mAmmToReadyForChangeMap.size() == 0) {
                handleCountryCodeChanged(null);
                Log.i(TAG, "No active mode, call onDriverCountryCodeChanged with Null");
            }
        }

        @Override
        public void onActiveModeManagerRoleChanged(@NonNull ActiveModeManager activeModeManager) {
            if (activeModeManager.getRole() == ActiveModeManager.ROLE_CLIENT_PRIMARY) {
                // Set this CMM ready for change. This is needed to handle the transition from
                // ROLE_CLIENT_SCAN_ONLY to ROLE_CLIENT_PRIMARY on devices running older HAL
                // versions (since the IWifiChip.setCountryCode() was only added in the 1.5 HAL
                // version, before that we need to wait till supplicant is up for country code
                // change.
                mAmmToReadyForChangeMap.put(activeModeManager, true);
                evaluateAllCmmStateAndApplyIfAllReady();
            }
        }
    }

    private class ClientModeListenerInternal implements ClientModeImplListener {
        @Override
        public void onConnectionStart(@NonNull ConcreteClientModeManager clientModeManager) {
            if (mAmmToReadyForChangeMap.get(clientModeManager) == null) {
                Log.wtf(TAG, "Connection start received from unknown client mode manager");
            }
            // connection start. CMM not ready for country code change.
            mAmmToReadyForChangeMap.put(clientModeManager, false);
            evaluateAllCmmStateAndApplyIfAllReady();
        }

        @Override
        public void onConnectionEnd(@NonNull ConcreteClientModeManager clientModeManager) {
            if (mAmmToReadyForChangeMap.get(clientModeManager) == null) {
                Log.wtf(TAG, "Connection end received from unknown client mode manager");
            }
            // connection end. CMM ready for country code change.
            mAmmToReadyForChangeMap.put(clientModeManager, true);
            evaluateAllCmmStateAndApplyIfAllReady();
        }

    }

    private class CountryChangeListenerInternal implements ChangeListener {
        @Override
        public void onDriverCountryCodeChanged(String country) {
            Log.i(TAG, "Receive onDriverCountryCodeChanged " + country);
            mLastReceivedActiveDriverCountryCode = country;
            // Before T build, always handle country code changed.
            if (!SdkLevel.isAtLeastT() || isDriverSupportedRegChangedEvent()) {
                // CC doesn't notify listener after sending to the driver, notify the listener
                // after we received CC changed event.
                handleCountryCodeChanged(country);
            }
        }

        @Override
        public void onSetCountryCodeSucceeded(String country) {
            Log.i(TAG, "Receive onSetCountryCodeSucceeded " + country);
            // The country code callback might not be triggered even if the driver supports reg
            // changed event when the maintained country code in the driver is same as last one.
            // So notify the country code changed event to listener when the set one is same as
            // last received one.
            if (!SdkLevel.isAtLeastT() || !isDriverSupportedRegChangedEvent()
                    || TextUtils.equals(country, mLastReceivedActiveDriverCountryCode)) {
                mWifiNative.countryCodeChanged(country);
                handleCountryCodeChanged(country);
            }
        }
    }

    public WifiCountryCode(
            Context context,
            ActiveModeWarden activeModeWarden,
            WifiP2pMetrics wifiP2pMetrics,
            ClientModeImplMonitor clientModeImplMonitor,
            WifiNative wifiNative,
            @NonNull WifiSettingsConfigStore settingsConfigStore,
            Clock clock,
            WifiPermissionsUtil wifiPermissionsUtil) {
        mContext = context;
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mActiveModeWarden = activeModeWarden;
        mWifiP2pMetrics = wifiP2pMetrics;
        mWifiNative = wifiNative;
        mSettingsConfigStore = settingsConfigStore;
        mClock = clock;
        mWifiPermissionsUtil = wifiPermissionsUtil;

        mActiveModeWarden.registerModeChangeCallback(new ModeChangeCallbackInternal());
        clientModeImplMonitor.registerListener(new ClientModeListenerInternal());
        mWifiNative.registerCountryCodeEventListener(new CountryChangeListenerInternal());

        mWorldModeCountryCode = mContext.getResources()
                .getString(R.string.config_wifiDriverWorldModeCountryCode);

        Log.d(TAG, "Default country code from system property "
                + BOOT_DEFAULT_WIFI_COUNTRY_CODE + " is " + getOemDefaultCountryCode());
    }

    /**
     * Default country code stored in system property
     * @return Country code if available, null otherwise.
     */
    public static String getOemDefaultCountryCode() {
        String country = SystemProperties.get(BOOT_DEFAULT_WIFI_COUNTRY_CODE);
        return WifiCountryCode.isValid(country) ? country.toUpperCase(Locale.US) : null;
    }

    /**
     * Is this a valid country code
     * @param countryCode A 2-Character alphanumeric country code.
     * @return true if the countryCode is valid, false otherwise.
     */
    public static boolean isValid(String countryCode) {
        return countryCode != null && countryCode.length() == 2
                && countryCode.chars().allMatch(Character::isLetterOrDigit);
    }

    /**
     * The class for country code related change listener
     */
    public interface ChangeListener {
        /**
         * Called when receiving new country code change pending.
         */
        default void onCountryCodeChangePending(@NonNull String countryCode) {};

        /**
         * Called when receiving country code changed from driver.
         */
        void onDriverCountryCodeChanged(String countryCode);

        /**
         * Called when country code set to native layer successful, framework sends event to
         * force country code changed.
         *
         * Reason: The country code change listener from wificond rely on driver supported
         * NL80211_CMD_REG_CHANGE/NL80211_CMD_WIPHY_REG_CHANGE. Trigger update country code
         * to listener here for non-supported platform.
         */
        default void onSetCountryCodeSucceeded(String country) {}
    }


    /**
     * Register Country code changed listener.
     */
    public void registerListener(@NonNull ChangeListener listener) {
        mListeners.add(listener);
        /**
         * Always called with mDriverCountryCode even if the SDK version is lower than T.
         * Reason: Before android S, the purpose of the internal listener is updating the supported
         * channels, it always depends on mDriverCountryCode.
         */
        if (mDriverCountryCode != null) {
            listener.onDriverCountryCodeChanged(mDriverCountryCode);
        }
    }

    /**
     * Unregister Country code changed listener.
     */
    public void unregisterListener(@NonNull ChangeListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Enable verbose logging for WifiCountryCode.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    private boolean hasCalling() {
        return mContext.getPackageManager().hasSystemFeature(FEATURE_TELEPHONY_CALLING);
    }

    /**
     * Check if Wi-Fi calling is available.
     *
     * This method can only be called if device has calling feature (see hasCalling()).
     */
    private boolean isWifiCallingAvailable() {
        SubscriptionManager subscriptionManager =
                mContext.getSystemService(SubscriptionManager.class);
        if (subscriptionManager == null) {
            Log.d(TAG, "SubscriptionManager not found");
            return false;
        }

        List<SubscriptionInfo> subInfoList = subscriptionManager
                .getCompleteActiveSubscriptionInfoList();
        if (subInfoList == null) {
            Log.d(TAG, "Active SubscriptionInfo list not found");
            return false;
        }
        for (SubscriptionInfo subInfo : subInfoList) {
            int subscriptionId = subInfo.getSubscriptionId();
            try {
                if (ImsMmTelManager.createForSubscriptionId(subscriptionId).isAvailable(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                        ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN)) {
                    Log.d(TAG, "WifiCalling is available on subId " + subscriptionId);
                    return true;
                }
            } catch (RuntimeException e) {
                Log.d(TAG, "RuntimeException while checking if wifi calling is available: " + e);
            }
        }
        return false;
    }

    private void initializeTelephonyCountryCodeIfNeeded() {
        // If we don't have telephony country code set yet, poll it.
        if (mTelephonyCountryCode == null) {
            Log.d(TAG, "Reading country code from telephony");
            setTelephonyCountryCode(mTelephonyManager.getNetworkCountryIso());
        }
    }

    /**
     * We call native code to request country code changes only if all {@link ClientModeManager}
     * instances are ready for country code change. Country code is a chip level configuration and
     * results in all the connections on the chip being disrupted.
     *
     * @return true if there are active CMM's and all are ready for country code change.
     */
    private boolean isAllCmmReady() {
        boolean isAnyCmmExist = false;
        for (ActiveModeManager am : mAmmToReadyForChangeMap.keySet()) {
            if (am instanceof ConcreteClientModeManager) {
                isAnyCmmExist = true;
                if (!mAmmToReadyForChangeMap.get(am)) {
                    return false;
                }
            }
        }
        return isAnyCmmExist;
    }

    /**
     * Check all active CMM instances and apply country code change if ready.
     */
    private void evaluateAllCmmStateAndApplyIfAllReady() {
        Log.d(TAG, "evaluateAllCmmStateAndApplyIfAllReady: " + mAmmToReadyForChangeMap);
        if (isAllCmmReady() && mIsCountryCodePendingToUpdateToCmm) {
            mAllCmmReadyTimestamp = FORMATTER.format(new Date(mClock.getWallClockMillis()));
            // We are ready to set country code now.
            // We need to post pending country code request.
            initializeTelephonyCountryCodeIfNeeded();
            updateCountryCode(true);
        }
    }

    /**
     * This call will override any existing country code.
     * This is for test purpose only and we should disallow any update from
     * telephony in this mode.
     * @param countryCode A 2-Character alphanumeric country code.
     */
    public synchronized void setOverrideCountryCode(String countryCode) {
        if (TextUtils.isEmpty(countryCode)) {
            Log.d(TAG, "Fail to override country code because"
                    + "the received country code is empty");
            return;
        }
        // Support 00 map to device world mode country code
        if (TextUtils.equals("00", countryCode)) {
            countryCode = mWorldModeCountryCode;
        }
        mOverrideCountryCode = countryCode.toUpperCase(Locale.US);
        updateCountryCode(false);
    }

    /**
     * This is for clearing the country code previously set through #setOverrideCountryCode() method
     */
    public synchronized void clearOverrideCountryCode() {
        mOverrideCountryCode = null;
        updateCountryCode(false);
    }

    private void setTelephonyCountryCode(String countryCode) {
        Log.d(TAG, "Set telephony country code to: " + countryCode);
        mTelephonyCountryTimestamp = FORMATTER.format(new Date(mClock.getWallClockMillis()));

        // Empty country code.
        if (TextUtils.isEmpty(countryCode)) {
            if (mContext.getResources()
                    .getBoolean(R.bool.config_wifi_revert_country_code_on_cellular_loss)) {
                Log.d(TAG, "Received empty country code, reset to default country code");
                mTelephonyCountryCode = null;
            }
        } else {
            mTelephonyCountryCode = countryCode.toUpperCase(Locale.US);
        }
    }

    /**
     * Handle telephony country code change request.
     * @param countryCode The country code intended to set.
     * This is supposed to be from Telephony service.
     * otherwise we think it is from other applications.
     * @return Returns true if the country code passed in is acceptable and passed to the driver.
     */
    public boolean setTelephonyCountryCodeAndUpdate(String countryCode) {
        if (TextUtils.isEmpty(countryCode)
                && !TextUtils.isEmpty(mTelephonyManager.getNetworkCountryIso())) {
            Log.i(TAG, "Skip Telephony CC update to empty because there is "
                    + "an available CC from default active SIM");
            return false;
        }
        // We do not check if the country code (CC) equals the current one because
        // 1. Wpa supplicant may silently modify the country code.
        // 2. If Wifi restarted therefore wpa_supplicant also restarted,
        setTelephonyCountryCode(countryCode);
        if (mOverrideCountryCode != null) {
            Log.d(TAG, "Skip Telephony CC update due to override country code set");
            return false;
        }

        updateCountryCode(false);
        return true;
    }

    /**
     * Update country code from scan results
     * Note the derived country code is used only if all following conditions are met
     * 1) There is no telephony country code
     * 2) The current driver country code is empty or equal to the worldwide code
     * 3) Currently the device is disconnected
     * @param scanDetails Wifi scan results
     */
    public void updateCountryCodeFromScanResults(@NonNull List<ScanDetail> scanDetails) {
        if (mTelephonyCountryCode != null) {
            return;
        }

        if (!isCcUpdateGenericEnabled()) {
            return;
        }

        String countryCode = findCountryCodeFromScanResults(scanDetails);
        if (countryCode == null) {
            Log.i(TAG, "Skip framework CC update because it is empty");
            return;
        }
        if (countryCode.equalsIgnoreCase(mFrameworkCountryCode)) {
            return;
        }

        mFrameworkCountryCodeUpdatedTimestamp = mClock.getWallClockMillis();
        mFrameworkCountryCode = countryCode;
        if (mOverrideCountryCode != null) {
            Log.d(TAG, "Skip framework CC update due to override country code set");
            return;
        }

        updateCountryCode(false);
    }

    private boolean isCcUpdateGenericEnabled() {
        return mContext.getResources().getBoolean(
                R.bool.config_wifiUpdateCountryCodeFromScanResultGeneric);
    }

    private String findCountryCodeFromScanResults(List<ScanDetail> scanDetails) {
        String selectedCountryCode = null;
        int count = 0;
        for (ScanDetail scanDetail : scanDetails) {
            NetworkDetail networkDetail = scanDetail.getNetworkDetail();
            String countryCode = networkDetail.getCountryCode();
            if (scanDetail.getScanResult().level < MIN_SCAN_RSSI_DBM) {
                continue;
            }
            if (countryCode == null || TextUtils.isEmpty(countryCode)) {
                continue;
            }
            if (selectedCountryCode == null) {
                selectedCountryCode = countryCode;
            }
            if (!selectedCountryCode.equalsIgnoreCase(countryCode)) {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "CC doesn't match");
                }
                return null;
            }
            count++;
        }
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, selectedCountryCode + " " + count);
        }
        if (count == 0) {
            return null;
        }
        int min_count = selectedCountryCode.equalsIgnoreCase(COUNTRY_CODE_US)
                ? MIN_COUNTRY_CODE_COUNT_US : MIN_COUNTRY_CODE_COUNT_OTHER;
        return (count >= min_count) ? selectedCountryCode : null;
    }

    private void disconnectWifiToForceUpdateIfNeeded() {
        if (shouldDisconnectWifiToForceUpdate()) {
            Log.d(TAG, "Disconnect wifi to force update");
            for (ClientModeManager cmm :
                    mActiveModeWarden.getInternetConnectivityClientModeManagers()) {
                if (!cmm.isConnected()) {
                    continue;
                }
                cmm.disconnect();
            }
            mDisconnectWifiToForceUpdateCount++;
        }
    }

    private boolean shouldDisconnectWifiToForceUpdate() {
        if (!hasCalling() || isWifiCallingAvailable()) {
            return false;
        }

        if (mTelephonyCountryCode == null
                || mTelephonyCountryCode.equals(mDriverCountryCode)) {
            return false;
        }

        if (mDisconnectWifiToForceUpdateCount >= DISCONNECT_WIFI_COUNT_MAX) {
            return false;
        }

        if (mDriverCountryCode != null
                && !mDriverCountryCode.equalsIgnoreCase(mWorldModeCountryCode)) {
            return false;
        }

        for (ClientModeManager cmm :
                mActiveModeWarden.getInternetConnectivityClientModeManagers()) {
            if (!cmm.isConnected()) {
                continue;
            }
            WifiInfo wifiInfo = cmm.getConnectionInfo();
            if (wifiInfo.getSuccessfulTxPacketsPerSecond() < PKT_COUNT_HIGH_PKT_PER_SEC
                    && wifiInfo.getSuccessfulRxPacketsPerSecond() < PKT_COUNT_HIGH_PKT_PER_SEC) {
                return true;
            }
        }
        return false;
    }

    /**
     * Method to get the received driver Country Code that being used in driver.
     *
     * @return Returns the local copy of the received driver Country Code or null if
     * there is no Country Code was received from driver or no any active mode.
     */
    @Nullable
    public synchronized String getCurrentDriverCountryCode() {
        return mDriverCountryCode;
    }

    /**
     * Method to return the currently reported Country Code resolved from various sources:
     * e.g. default country code, cellular network country code, country code override, etc.
     *
     * @return The current Wifi Country Code resolved from various sources. Returns null when there
     * is no Country Code available.
     */
    @Nullable
    public synchronized String getCountryCode() {
        initializeTelephonyCountryCodeIfNeeded();
        return pickCountryCode(true);
    }

    /**
     * set default country code
     * @param countryCode A 2-Character alphanumeric country code.
     */
    public synchronized void setDefaultCountryCode(String countryCode) {
        if (TextUtils.isEmpty(countryCode)) {
            Log.d(TAG, "Fail to set default country code because the country code is empty");
            return;
        }

        mSettingsConfigStore.put(WIFI_DEFAULT_COUNTRY_CODE,
                countryCode.toUpperCase(Locale.US));
        Log.i(TAG, "Default country code updated in config store: " + countryCode);
        updateCountryCode(false);
    }

    /**
     * Method to dump the current state of this WifiCountryCode object.
     */
    public synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("mRevertCountryCodeOnCellularLoss: "
                + mContext.getResources().getBoolean(
                R.bool.config_wifi_revert_country_code_on_cellular_loss));
        pw.println("DefaultCountryCode(system property): " + getOemDefaultCountryCode());
        pw.println("DefaultCountryCode(config store): "
                + mSettingsConfigStore.get(WIFI_DEFAULT_COUNTRY_CODE));
        pw.println("mTelephonyCountryCode: " + mTelephonyCountryCode);
        pw.println("mTelephonyCountryTimestamp: " + mTelephonyCountryTimestamp);
        pw.println("mOverrideCountryCode: " + mOverrideCountryCode);
        pw.println("mAllCmmReadyTimestamp: " + mAllCmmReadyTimestamp);
        pw.println("isAllCmmReady: " + isAllCmmReady());
        pw.println("mAmmToReadyForChangeMap: " + mAmmToReadyForChangeMap);
        pw.println("mDisconnectWifiToForceUpdateCount: " + mDisconnectWifiToForceUpdateCount);
        pw.println("mDriverCountryCode: " + mDriverCountryCode);
        pw.println("mDriverCountryCodeUpdatedTimestamp: "
                + (mDriverCountryCodeUpdatedTimestamp != 0
                ? FORMATTER.format(new Date(mDriverCountryCodeUpdatedTimestamp)) : "N/A"));
        pw.println("mFrameworkCountryCode: " + mFrameworkCountryCode);
        pw.println("mFrameworkCountryCodeUpdatedTimestamp: "
                + (mFrameworkCountryCodeUpdatedTimestamp != 0
                ? FORMATTER.format(new Date(mFrameworkCountryCodeUpdatedTimestamp)) : "N/A"));
        pw.println("isDriverSupportedRegChangedEvent: "
                + isDriverSupportedRegChangedEvent());
    }

    private boolean isDriverSupportedRegChangedEvent() {
        return mContext.getResources().getBoolean(
                R.bool.config_wifiDriverSupportedNl80211RegChangedEvent);
    }

    private void updateCountryCode(boolean isClientModeOnly) {
        // The mDriverCountryCode is the country code which is being used by driver now.
        // It should not be a candidate for writing use case.
        String country = pickCountryCode(false);
        Log.d(TAG, "updateCountryCode to " + country);

        // We do not check if the country code equals the current one.
        // There are two reasons:
        // 1. Wpa supplicant may silently modify the country code.
        // 2. If Wifi restarted therefore wpa_supplicant also restarted,
        // the country code could be reset to '00' by wpa_supplicant.
        if (country != null) {
            setCountryCodeNative(country, isClientModeOnly);
        }
        // We do not set country code if there is no candidate. This is reasonable
        // because wpa_supplicant usually starts with an international safe country
        // code setting: '00'.
    }

    /**
     * Pick up country code base on country code we have.
     *
     * @param useDriverCountryCodeIfAvailable whether or not to use driver country code
     *                                        if available, and it is only for reporting purpose.
     * @return country code base on the use case and current country code we have.
     */
    private String pickCountryCode(boolean useDriverCountryCodeIfAvailable) {
        if (mOverrideCountryCode != null) {
            return mOverrideCountryCode;
        }
        if (mTelephonyCountryCode != null) {
            return mTelephonyCountryCode;
        }
        if (useDriverCountryCodeIfAvailable && mDriverCountryCode != null) {
            // Returns driver country code since it may be different to WIFI_DEFAULT_COUNTRY_CODE
            // when driver supported 802.11d.
            return mDriverCountryCode;
        }
        if (mFrameworkCountryCode != null && isCcUpdateGenericEnabled()) {
            return mFrameworkCountryCode;
        }
        return mSettingsConfigStore.get(WIFI_DEFAULT_COUNTRY_CODE);
    }

    private boolean setCountryCodeNative(String country, boolean isClientModeOnly) {
        Set<ActiveModeManager> amms = mAmmToReadyForChangeMap.keySet();
        boolean isConcreteClientModeManagerUpdated = false;
        boolean anyAmmConfigured = false;
        final boolean isNeedToUpdateCCToSta = mContext.getResources()
                .getBoolean(R.bool.config_wifiStaDynamicCountryCodeUpdateSupported)
                || isAllCmmReady();
        if (!isNeedToUpdateCCToSta) {
            Log.d(TAG, "skip update supplicant not ready yet");
            disconnectWifiToForceUpdateIfNeeded();
        }
        boolean isCountryCodeChanged = !TextUtils.equals(mDriverCountryCode, country);
        Log.d(TAG, "setCountryCodeNative: " + country + ", isClientModeOnly: " + isClientModeOnly
                + " mDriverCountryCode: " + mDriverCountryCode);
        for (ActiveModeManager am : amms) {
            if (!isConcreteClientModeManagerUpdated
                    && am instanceof ConcreteClientModeManager) {
                mIsCountryCodePendingToUpdateToCmm = !isNeedToUpdateCCToSta;
                if (!isNeedToUpdateCCToSta) {
                    continue;
                }
                // Set the country code using one of the active mode managers. Since
                // country code is a chip level global setting, it can be set as long
                // as there is at least one active interface to communicate to Wifi chip
                ConcreteClientModeManager cm = (ConcreteClientModeManager) am;
                if (!cm.setCountryCode(country)) {
                    Log.d(TAG, "Failed to set country code (ConcreteClientModeManager) to "
                            + country);
                } else {
                    isConcreteClientModeManagerUpdated = true;
                    anyAmmConfigured = true;
                    // Start from S, frameworks support country code callback from wificond,
                    // move "notify the lister" to CountryChangeListenerInternal.
                    if (!SdkLevel.isAtLeastS() && !isDriverSupportedRegChangedEvent()) {
                        handleCountryCodeChanged(country);
                    }
                }
            } else if (!isClientModeOnly && am instanceof SoftApManager) {
                SoftApManager sm = (SoftApManager) am;
                if (mDriverCountryCode == null || !isCountryCodeChanged) {
                    // Ignore SoftApManager init country code case or country code didn't be
                    // changed case.
                    continue;
                }
                // Restart SAP if the overlay is enabled.
                if (ApConfigUtil.isSoftApRestartRequiredWhenCountryCodeChanged(mContext)) {
                    Log.i(TAG, "restart SoftAp required because country code changed to "
                            + country);
                    SoftApModeConfiguration modeConfig = sm.getSoftApModeConfiguration();
                    SoftApModeConfiguration newModeConfig = new SoftApModeConfiguration(
                            modeConfig.getTargetMode(), modeConfig.getSoftApConfiguration(),
                            modeConfig.getCapability(), country, modeConfig.getTetheringRequest());
                    mActiveModeWarden.stopSoftAp(modeConfig.getTargetMode());
                    mActiveModeWarden.startSoftAp(newModeConfig, sm.getRequestorWs());
                } else {
                    // The API:updateCountryCode in SoftApManager is asynchronous, it requires a
                    // new callback support in S to trigger "notifyListener" for
                    // the new S API: SoftApCapability#getSupportedChannelList(band).
                    // It requires:
                    // 1. a new overlay configuration which is introduced from S.
                    // 2. wificond support in S for S API: SoftApCapability#getSupportedChannelList
                    // Any case if device supported to set country code in R,
                    // the new S API: SoftApCapability#getSupportedChannelList(band) still doesn't
                    // work normally in R build when wifi disabled.
                    if (!sm.updateCountryCode(country)) {
                        Log.d(TAG, "Can't set country code (SoftApManager) to "
                                + country + " when SAP on (Device doesn't support runtime update)");
                    } else {
                        anyAmmConfigured = true;
                    }
                }
            }
        }
        if (!anyAmmConfigured) {
            for (ChangeListener listener : mListeners) {
                if (country != null) {
                    listener.onCountryCodeChangePending(country);
                }
            }
        }
        return anyAmmConfigured;
    }

    private void handleCountryCodeChanged(String country) {
        mDriverCountryCodeUpdatedTimestamp = mClock.getWallClockMillis();
        mDriverCountryCode = country;
        mWifiP2pMetrics.setIsCountryCodeWorldMode(isDriverCountryCodeWorldMode());
        notifyListener(country);
        if (country == null) {
            mIsCountryCodePendingToUpdateToCmm = true;
        }
    }

    /**
     * Method to check if current driver Country Code is in the world mode
     */
    private boolean isDriverCountryCodeWorldMode() {
        if (mDriverCountryCode == null) {
            return true;
        }
        return mDriverCountryCode.equalsIgnoreCase(mWorldModeCountryCode);
    }

    /**
     * Notify the listeners. There are two kind of listeners
     * 1. external listener, they only care what is country code which driver is using now.
     * 2. internal listener, frameworks also only care what is country code which driver is using
     * now because it requires to update supported channels with new country code.
     *
     * Note: Call this API only after confirming the CC is used in driver.
     *
     * @param country the country code is used in driver or null when driver is non-active.
     */
    private void notifyListener(@Nullable String country) {
        mActiveModeWarden.updateClientScanModeAfterCountryCodeUpdate(country);
        for (ChangeListener listener : mListeners) {
            listener.onDriverCountryCodeChanged(country);
        }
    }
}
