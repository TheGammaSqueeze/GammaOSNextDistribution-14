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
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiMigration;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.util.SettingsMigrationDataHolder;
import com.android.server.wifi.util.WifiConfigStoreEncryptionUtil;
import com.android.server.wifi.util.XmlUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Store data for storing wifi settings. These are key (string) / value pairs that are stored in
 * WifiConfigStore.xml file in a separate section.
 */
public class WifiSettingsConfigStore {
    private static final String TAG = "WifiSettingsConfigStore";

    // List of all allowed keys.
    private static final ArrayList<Key> sKeys = new ArrayList<>();

    /******** Wifi shared pref keys ***************/
    /**
     * Indicate whether factory reset request is pending.
     */
    public static final Key<Boolean> WIFI_P2P_PENDING_FACTORY_RESET =
            new Key<>("wifi_p2p_pending_factory_reset", false);

    /**
     * Allow scans to be enabled even wifi is turned off.
     */
    public static final Key<Boolean> WIFI_SCAN_ALWAYS_AVAILABLE =
            new Key<>("wifi_scan_always_enabled", false);

    /**
     * Whether wifi scan throttle is enabled or not.
     */
    public static final Key<Boolean> WIFI_SCAN_THROTTLE_ENABLED =
            new Key<>("wifi_scan_throttle_enabled", true);

    /**
     * Setting to enable verbose logging in Wi-Fi.
     */
    public static final Key<Boolean> WIFI_VERBOSE_LOGGING_ENABLED =
            new Key<>("wifi_verbose_logging_enabled", false);

    /**
     * Setting to enable verbose logging in Wi-Fi Aware.
     */
    public static final Key<Boolean> WIFI_AWARE_VERBOSE_LOGGING_ENABLED =
            new Key<>("wifi_aware_verbose_logging_enabled", false);

    /**
     * The Wi-Fi peer-to-peer device name
     */
    public static final Key<String> WIFI_P2P_DEVICE_NAME =
            new Key<>("wifi_p2p_device_name", null);

    /**
     * The Wi-Fi peer-to-peer device mac address
     */
    public static final Key<String> WIFI_P2P_DEVICE_ADDRESS =
            new Key<>("wifi_p2p_device_address", null);

    /**
     * Whether Wifi scoring is enabled or not.
     */
    public static final Key<Boolean> WIFI_SCORING_ENABLED =
            new Key<>("wifi_scoring_enabled", true);

    /**
     * Whether Wifi Passpoint is enabled or not.
     */
    public static final Key<Boolean> WIFI_PASSPOINT_ENABLED =
            new Key<>("wifi_passpoint_enabled", true);

    /**
     * Whether Wifi Multi Internet is enabled for multi ap, dbs or disabled.
     */
    public static final Key<Integer> WIFI_MULTI_INTERNET_MODE =
            new Key<Integer>("wifi_multi_internet_mode",
                    WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED);

    /**
     * Store the STA factory MAC address retrieved from the driver on the first bootup.
     */
    public static final Key<String> WIFI_STA_FACTORY_MAC_ADDRESS =
            new Key<>("wifi_sta_factory_mac_address", null);

    /**
     * Store the Secondary STA factory MAC address retrieved from the driver on the first bootup.
     */
    public static final Key<String> SECONDARY_WIFI_STA_FACTORY_MAC_ADDRESS =
            new Key<>("secondary_wifi_sta_factory_mac_address", null);


    /**
     * Store the default country code updated via {@link WifiManager#setDefaultCountryCode(String)}
     */
    public static final Key<String> WIFI_DEFAULT_COUNTRY_CODE =
            new Key<>("wifi_default_country_code", WifiCountryCode.getOemDefaultCountryCode());

    /**
     * Store the supported features retrieved from WiFi HAL and Supplicant HAL
     */
    public static final Key<Long> WIFI_NATIVE_SUPPORTED_FEATURES =
            new Key<>("wifi_native_supported_features", 0L);

    /**
     * Store the supported features retrieved from WiFi HAL and Supplicant HAL
     */
    public static final Key<Integer> WIFI_NATIVE_SUPPORTED_STA_BANDS =
            new Key<>("wifi_native_supported_sta_bands", 0);

    /**
     * Store the static chip info retrieved from WiFi HAL
     */
    public static final Key<String> WIFI_STATIC_CHIP_INFO = new Key<>("wifi_static_chip_info", "");

    /**
     * Store the last country code used by Soft AP.
     */
    public static final Key<String> WIFI_SOFT_AP_COUNTRY_CODE =
            new Key<>("wifi_last_country_code", "");

    /**
     * Store the available channel frequencies in a JSON array for Soft AP for the last country
     * code used.
     */
    public static final Key<String> WIFI_AVAILABLE_SOFT_AP_FREQS_MHZ =
            new Key<>("wifi_available_soft_ap_freqs_mhz", "[]");

    /**
     * Whether to show a dialog when third party apps attempt to enable wifi.
     */
    public static final Key<Boolean> SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI =
            new Key<>("show_dialog_when_third_party_apps_enable_wifi", false);

    /**
     * Whether the
     * {@link WifiManager#setThirdPartyAppEnablingWifiConfirmationDialogEnabled(boolean)} API was
     * called to set the value of {@link #SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI}.
     */
    public static final Key<Boolean> SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI_SET_BY_API =
            new Key<>("show_dialog_when_third_party_apps_enable_wifi_set_by_api", false);

    /**
     * AIDL version implemented by the Supplicant service.
     */
    public static final Key<Integer> SUPPLICANT_HAL_AIDL_SERVICE_VERSION =
            new Key<>("supplicant_hal_aidl_service_version", -1);

    /**
     * Whether the WEP network is allowed or not.
     */
    public static final Key<Boolean> WIFI_WEP_ALLOWED = new Key<>("wep_allowed", true);

    /**
     * Store wiphy capability for 11be support.
     */
    public static final Key<Boolean> WIFI_WIPHY_11BE_SUPPORTED =
            new Key<>("wifi_wiphy_11be_supported", true);

    /**
     * Whether the D2D is allowed or not when infra sta is disabled.
     */
    public static final Key<Boolean> D2D_ALLOWED_WHEN_INFRA_STA_DISABLED =
            new Key<>("d2d_allowed_when_infra_sta_disabled", false);

    // List of all keys which require to backup and restore.
    @SuppressWarnings("DoubleBraceInitialization")
    private static final ArrayList<Key> sBackupRestoreKeys = new ArrayList<>() {{
            add(WIFI_WEP_ALLOWED); }};
    /******** Wifi shared pref keys ***************/

    private final Context mContext;
    private final Handler mHandler;
    private final SettingsMigrationDataHolder mSettingsMigrationDataHolder;
    private final WifiConfigManager mWifiConfigManager;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final Map<String, Object> mSettings = new HashMap<>();
    @GuardedBy("mLock")
    private final Map<String, Map<OnSettingsChangedListener, Handler>> mListeners =
            new HashMap<>();
    private WifiMigration.SettingsMigrationData mCachedMigrationData = null;

    private boolean mHasNewDataToSerialize = false;

    /**
     * Interface for a settings change listener.
     * @param <T> Type of the value.
     */
    public interface OnSettingsChangedListener<T> {
        /**
         * Invoked when a particular key settings changes.
         *
         * @param key Key that was changed.
         * @param newValue New value that was assigned to the key.
         */
        void onSettingsChanged(@NonNull Key<T> key, @Nullable T newValue);
    }

    public WifiSettingsConfigStore(@NonNull Context context, @NonNull Handler handler,
            @NonNull SettingsMigrationDataHolder settingsMigrationDataHolder,
            @NonNull WifiConfigManager wifiConfigManager,
            @NonNull WifiConfigStore wifiConfigStore) {
        mContext = context;
        mHandler = handler;
        mSettingsMigrationDataHolder = settingsMigrationDataHolder;
        mWifiConfigManager = wifiConfigManager;

        // Register our data store.
        wifiConfigStore.registerStoreData(new StoreData());
    }

    public ArrayList<Key> getAllKeys() {
        return sKeys;
    }

    public ArrayList<Key> getAllBackupRestoreKeys() {
        return sBackupRestoreKeys;
    }

    private void invokeAllListeners() {
        synchronized (mLock) {
            for (Key key : sKeys) {
                invokeListeners(key);
            }
        }
    }

    private <T> void invokeListeners(@NonNull Key<T> key) {
        synchronized (mLock) {
            if (!mSettings.containsKey(key.key)) return;
            Object newValue = mSettings.get(key.key);
            Map<OnSettingsChangedListener, Handler> listeners = mListeners.get(key.key);
            if (listeners == null || listeners.isEmpty()) return;
            for (Map.Entry<OnSettingsChangedListener, Handler> listener
                    : listeners.entrySet()) {
                // Trigger the callback in the appropriate handler.
                listener.getValue().post(() ->
                        listener.getKey().onSettingsChanged(key, newValue));
            }
        }
    }

    /**
     * Trigger config store writes and invoke listeners in the main wifi service looper's handler.
     */
    private void triggerSaveToStoreAndInvokeAllListeners() {
        mHandler.post(() -> {
            mHasNewDataToSerialize = true;
            mWifiConfigManager.saveToStore(true);

            invokeAllListeners();
        });
    }

    /**
     * Trigger config store writes and invoke listeners in the main wifi service looper's handler.
     */
    private <T> void triggerSaveToStoreAndInvokeListeners(@NonNull Key<T> key) {
        mHandler.post(() -> {
            mHasNewDataToSerialize = true;
            mWifiConfigManager.saveToStore(true);

            invokeListeners(key);
        });
    }

    /**
     * Performs a one time migration from Settings.Global values to settings store. Only
     * performed one time if the settings store is empty.
     */
    private void migrateFromSettingsIfNeeded() {
        if (!mSettings.isEmpty()) return; // already migrated.

        mCachedMigrationData = mSettingsMigrationDataHolder.retrieveData();
        if (mCachedMigrationData == null) {
            Log.e(TAG, "No settings data to migrate");
            return;
        }
        Log.i(TAG, "Migrating data out of settings to shared preferences");

        mSettings.put(WIFI_P2P_DEVICE_NAME.key,
                mCachedMigrationData.getP2pDeviceName());
        mSettings.put(WIFI_P2P_PENDING_FACTORY_RESET.key,
                mCachedMigrationData.isP2pFactoryResetPending());
        mSettings.put(WIFI_SCAN_ALWAYS_AVAILABLE.key,
                mCachedMigrationData.isScanAlwaysAvailable());
        mSettings.put(WIFI_SCAN_THROTTLE_ENABLED.key,
                mCachedMigrationData.isScanThrottleEnabled());
        mSettings.put(WIFI_VERBOSE_LOGGING_ENABLED.key,
                mCachedMigrationData.isVerboseLoggingEnabled());
        triggerSaveToStoreAndInvokeAllListeners();
    }

    /**
     * Store a value to the stored settings.
     *
     * @param key One of the settings keys.
     * @param value Value to be stored.
     */
    public <T> void put(@NonNull Key<T> key, @Nullable T value) {
        synchronized (mLock) {
            mSettings.put(key.key, value);
        }
        triggerSaveToStoreAndInvokeListeners(key);
    }

    /**
     * Retrieve a value from the stored settings.
     *
     * @param key One of the settings keys.
     * @return value stored in settings, defValue if the key does not exist.
     */
    public @Nullable <T> T get(@NonNull Key<T> key) {
        synchronized (mLock) {
            return (T) mSettings.getOrDefault(key.key, key.defaultValue);
        }
    }

    /**
     * Register for settings change listener.
     *
     * @param key One of the settings keys.
     * @param listener Listener to be registered.
     * @param handler Handler to post the listener
     */
    public <T> void registerChangeListener(@NonNull Key<T> key,
            @NonNull OnSettingsChangedListener<T> listener, @NonNull Handler handler) {
        synchronized (mLock) {
            mListeners.computeIfAbsent(
                    key.key, ignore -> new HashMap<>()).put(listener, handler);
        }
    }

    /**
     * Unregister for settings change listener.
     *
     * @param key One of the settings keys.
     * @param listener Listener to be unregistered.
     */
    public <T> void unregisterChangeListener(@NonNull Key<T> key,
            @NonNull OnSettingsChangedListener<T> listener) {
        synchronized (mLock) {
            Map<OnSettingsChangedListener, Handler> listeners = mListeners.get(key.key);
            if (listeners == null || listeners.isEmpty()) {
                Log.e(TAG, "No listeners for " + key);
                return;
            }
            if (listeners.remove(listener) == null) {
                Log.e(TAG, "Unknown listener for " + key);
            }
        }
    }

    /**
     * Dump output for debugging.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println();
        pw.println("Dump of " + TAG);
        pw.println("Settings:");
        for (Map.Entry<String, Object> entry : mSettings.entrySet()) {
            pw.print(entry.getKey());
            pw.print("=");
            pw.println(entry.getValue());
        }
        if (mCachedMigrationData == null) return;
        pw.println("Migration data:");
        pw.print(WIFI_P2P_DEVICE_NAME.key);
        pw.print("=");
        pw.println(mCachedMigrationData.getP2pDeviceName());
        pw.print(WIFI_P2P_PENDING_FACTORY_RESET.key);
        pw.print("=");
        pw.println(mCachedMigrationData.isP2pFactoryResetPending());
        pw.print(WIFI_SCAN_ALWAYS_AVAILABLE.key);
        pw.print("=");
        pw.println(mCachedMigrationData.isScanAlwaysAvailable());
        pw.print(WIFI_SCAN_THROTTLE_ENABLED.key);
        pw.print("=");
        pw.println(mCachedMigrationData.isScanThrottleEnabled());
        pw.print(WIFI_VERBOSE_LOGGING_ENABLED.key);
        pw.print("=");
        pw.println(mCachedMigrationData.isVerboseLoggingEnabled());
        pw.println();
    }

    /**
     * Base class to store string key and its default value.
     * @param <T> Type of the value.
     */
    public static class Key<T> {
        public final String key;
        public final T defaultValue;

        private Key(@NonNull String key, T defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
            sKeys.add(this);
        }

        @VisibleForTesting
        public String getKey() {
            return key;
        }

        @Override
        public String toString() {
            return "[Key " + key + ", DefaultValue: " + defaultValue + "]";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            // null instanceof [type]" also returns false
            if (!(o instanceof Key)) {
                return false;
            }

            Key anotherKey = (Key) o;
            return Objects.equals(key, anotherKey.key)
                    && Objects.equals(defaultValue, anotherKey.defaultValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, defaultValue);
        }
    }

    /**
     * Store data for persisting the settings data to config store.
     */
    public class StoreData implements WifiConfigStore.StoreData {
        public static final String XML_TAG_SECTION_HEADER = "Settings";
        public static final String XML_TAG_VALUES = "Values";

        @Override
        public void serializeData(XmlSerializer out,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            synchronized (mLock) {
                XmlUtil.writeNextValue(out, XML_TAG_VALUES, mSettings);
            }
        }

        @Override
        public void deserializeData(XmlPullParser in, int outerTagDepth,
                @WifiConfigStore.Version int version,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            if (in == null) {
                // Empty read triggers the migration since it indicates that there is no settings
                // data stored in the settings store.
                migrateFromSettingsIfNeeded();
                return;
            }
            Map<String, Object> values = deserializeSettingsData(in, outerTagDepth);
            if (values != null) {
                synchronized (mLock) {
                    mSettings.putAll(values);
                    // Invoke all the registered listeners.
                    invokeAllListeners();
                }
            }
        }

        /**
         * Parse out the wifi settings from the input xml stream.
         */
        public static Map<String, Object> deserializeSettingsData(
                XmlPullParser in, int outerTagDepth)
                throws XmlPullParserException, IOException {
            Map<String, Object> values = null;
            while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                String[] valueName = new String[1];
                Object value = XmlUtil.readCurrentValue(in, valueName);
                if (TextUtils.isEmpty(valueName[0])) {
                    throw new XmlPullParserException("Missing value name");
                }
                switch (valueName[0]) {
                    case XML_TAG_VALUES:
                        values = (Map) value;
                        break;
                    default:
                        Log.w(TAG, "Ignoring unknown tag under " + XML_TAG_SECTION_HEADER + ": "
                                + valueName[0]);
                        break;
                }
            }
            return values;
        }

        @Override
        public void resetData() {
            synchronized (mLock) {
                mSettings.clear();
            }
        }

        @Override
        public boolean hasNewDataToSerialize() {
            return mHasNewDataToSerialize;
        }

        @Override
        public String getName() {
            return XML_TAG_SECTION_HEADER;
        }

        @Override
        public @WifiConfigStore.StoreFileId int getStoreFileId() {
            // Shared general store.
            return WifiConfigStore.STORE_FILE_SHARED_GENERAL;
        }
    }
}
