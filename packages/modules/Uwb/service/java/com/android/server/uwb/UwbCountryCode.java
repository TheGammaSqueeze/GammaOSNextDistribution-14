/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.uwb;

import static com.android.server.uwb.data.UwbUciConstants.STATUS_CODE_OK;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextParams;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.ActiveCountryCodeChangedCallback;
import android.os.Handler;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.internal.annotations.Keep;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.HandlerExecutor;
import com.android.server.uwb.jni.NativeUwbManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

/**
 * Provide functions for making changes to UWB country code.
 * This Country Code is from MCC or phone default setting. This class sends Country Code
 * to UWB venodr via the HAL.
 */
public class UwbCountryCode {
    private static final String TAG = "UwbCountryCode";
    // To be used when there is no country code available.
    @VisibleForTesting
    public static final String DEFAULT_COUNTRY_CODE = "00";
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    /**
     * Copied from {@link TelephonyManager} because it's @hide.
     * TODO (b/242326831): Use @SystemApi.
     */
    @VisibleForTesting
    public static final String EXTRA_LAST_KNOWN_NETWORK_COUNTRY =
            "android.telephony.extra.LAST_KNOWN_NETWORK_COUNTRY";

    // Wait 1 hour between updates
    private static final long TIME_BETWEEN_UPDATES_MS = 1000L * 60 * 60 * 1;
    // Minimum distance before an update is triggered, in meters. We don't need this to be too
    // exact because all we care about is what country the user is in.
    private static final float DISTANCE_BETWEEN_UPDATES_METERS = 5_000.0f;

    // The last SIM slot index, used when the slot is not known, so that the corresponding
    // country code has the lowest priority (in the sorted mTelephonyCountryCodeInfoPerSlot map).
    private static final int LAST_SIM_SLOT_INDEX = Integer.MAX_VALUE;

    private final Context mContext;
    private final Handler mHandler;
    private final TelephonyManager mTelephonyManager;
    private final SubscriptionManager mSubscriptionManager;
    private final LocationManager mLocationManager;
    private final Geocoder mGeocoder;
    private final NativeUwbManager mNativeUwbManager;
    private final UwbInjector mUwbInjector;
    private final Set<CountryCodeChangedListener> mListeners = new ArraySet<>();

    private Map<Integer, TelephonyCountryCodeSlotInfo> mTelephonyCountryCodeInfoPerSlot =
            new ConcurrentSkipListMap();
    private String mWifiCountryCode = null;
    private String mLocationCountryCode = null;
    private String mCachedCountryCode = null;
    private String mOverrideCountryCode = null;
    private String mCountryCode = null;
    private Optional<Integer> mCountryCodeStatus = Optional.empty();
    private String mCountryCodeUpdatedTimestamp = null;
    private String mWifiCountryTimestamp = null;
    private String mLocationCountryTimestamp = null;

    /**
     * Container class to store country code per sim slot.
     */
    public static class TelephonyCountryCodeSlotInfo {
        public int slotIdx;
        public String countryCode;
        public String lastKnownCountryCode;
        public String timestamp;

        @Override
        public String toString() {
            return "TelephonyCountryCodeSlotInfo[ slotIdx: " + slotIdx
                    + ", countryCode: " + countryCode
                    + ", lastKnownCountryCode: " + lastKnownCountryCode
                    + ", timestamp: " + timestamp + "]";
        }
    }

    public interface CountryCodeChangedListener {
        /**
         * Notify listeners about a country code change.
         * @param statusCode - Status of the UWBS controller configuring the {@code newCountryCode}:
         *         - STATUS_CODE_OK: The country code was successfully configured by UWBS.
         *         - STATUS_CODE_ANDROID_REGULATION_UWB_OFF: UWB is not supported in the configured
         *                   country code.
         *         - Other status codes returned by the UWBS controller.
         * @param newCountryCode - The new UWB country code configured in the UWBS controller.
         */
        void onCountryCodeChanged(int statusCode, @Nullable String newCountryCode);
    }

    public UwbCountryCode(
            Context context, NativeUwbManager nativeUwbManager, Handler handler,
            UwbInjector uwbInjector) {
        mContext = context.createContext(
                new ContextParams.Builder().setAttributionTag(TAG).build());
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mSubscriptionManager = mContext.getSystemService(SubscriptionManager.class);
        mLocationManager = mContext.getSystemService(LocationManager.class);
        mGeocoder = uwbInjector.makeGeocoder();
        mNativeUwbManager = nativeUwbManager;
        mHandler = handler;
        mUwbInjector = uwbInjector;
    }

    @Keep
    private class WifiCountryCodeCallback implements ActiveCountryCodeChangedCallback {
        public void onActiveCountryCodeChanged(@NonNull String countryCode) {
            setWifiCountryCode(countryCode);
        }

        public void onCountryCodeInactive() {
            setWifiCountryCode("");
        }
    }

    private void setCountryCodeFromGeocodingLocation(@Nullable Location location) {
        if (location == null) return;
        Geocoder.GeocodeListener geocodeListener = (List<Address> addresses) -> {
            if (addresses != null && !addresses.isEmpty()) {
                String countryCode = addresses.get(0).getCountryCode();
                mHandler.post(() -> setLocationCountryCode(countryCode));
            }
        };
        mGeocoder.getFromLocation(
                location.getLatitude(), location.getLongitude(), 1, geocodeListener);
    }

    /**
     * Initialize the module.
     */
    public void initialize() {
        // Read the cached country code first (if caching is enabled on the device)
        if (mUwbInjector.getDeviceConfigFacade().isPersistentCacheUseForCountryCodeEnabled()) {
            String cachedCountryCode = mUwbInjector.getUwbSettingsStore().get(
                    UwbSettingsStore.SETTINGS_CACHED_COUNTRY_CODE);
            if (isValid(cachedCountryCode)) mCachedCountryCode = cachedCountryCode;
        }
        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        int slotIdx = intent.getIntExtra(
                                SubscriptionManager.EXTRA_SLOT_INDEX,
                                LAST_SIM_SLOT_INDEX);
                        String countryCode = intent.getStringExtra(
                                TelephonyManager.EXTRA_NETWORK_COUNTRY);
                        String lastKnownCountryCode = intent.getStringExtra(
                                EXTRA_LAST_KNOWN_NETWORK_COUNTRY);
                        Log.d(TAG, "Telephony Country code changed to: " + countryCode);
                        setTelephonyCountryCodeAndLastKnownCountryCode(
                                slotIdx, countryCode, lastKnownCountryCode);
                    }
                },
                new IntentFilter(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED),
                null, mHandler);
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            mContext.getSystemService(WifiManager.class).registerActiveCountryCodeChangedCallback(
                    new HandlerExecutor(mHandler), new WifiCountryCodeCallback());
        }
        if (mUwbInjector.getDeviceConfigFacade().isLocationUseForCountryCodeEnabled() &&
                mUwbInjector.isGeocoderPresent()) {
            mLocationManager.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER,
                    TIME_BETWEEN_UPDATES_MS,
                    DISTANCE_BETWEEN_UPDATES_METERS,
                    location -> setCountryCodeFromGeocodingLocation(location));

        }
        Log.d(TAG, "Default country code from system property is "
                + mUwbInjector.getOemDefaultCountryCode());
        List<SubscriptionInfo> subscriptionInfoList =
                mSubscriptionManager.getActiveSubscriptionInfoList();
        if (subscriptionInfoList != null && !subscriptionInfoList.isEmpty()) {
            Set<Integer> slotIdxs = subscriptionInfoList
                    .stream()
                    .map(SubscriptionInfo::getSimSlotIndex)
                    .collect(Collectors.toSet());
            for (Integer slotIdx : slotIdxs) {
                String countryCode;
                try {
                    countryCode = mTelephonyManager.getNetworkCountryIso(slotIdx);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Failed to get country code for slot id:" + slotIdx, e);
                    continue;
                }
                setTelephonyCountryCodeAndLastKnownCountryCode(slotIdx, countryCode, null);
            }
        } else {
            // Fetch and configure the networkCountryIso() when the subscriptionInfoList is either
            // null or empty. This is done only when the country code is valid.
            if (mUwbInjector.getFeatureFlags().useNetworkCountryIso()) {
                String countryCode = mTelephonyManager.getNetworkCountryIso();
                if (isValid(countryCode)) {
                    setTelephonyCountryCodeAndLastKnownCountryCode(
                            LAST_SIM_SLOT_INDEX, countryCode, null);
                }
            }
        }

        if (mUwbInjector.getDeviceConfigFacade().isLocationUseForCountryCodeEnabled() &&
                mUwbInjector.isGeocoderPresent()) {
            setCountryCodeFromGeocodingLocation(
                    mLocationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER));
        }
        // Current Wifi country code update is sent immediately on registration.
    }

    public void addListener(@NonNull CountryCodeChangedListener listener) {
        mListeners.add(listener);
    }

    private void setTelephonyCountryCodeAndLastKnownCountryCode(int slotIdx, String countryCode,
            String lastKnownCountryCode) {
        Log.d(TAG, "Set telephony country code to: " + countryCode
                + ", last country code to: " + lastKnownCountryCode + " for slotIdx: " + slotIdx);
        TelephonyCountryCodeSlotInfo telephonyCountryCodeInfoSlot =
                mTelephonyCountryCodeInfoPerSlot.computeIfAbsent(
                        slotIdx, k -> new TelephonyCountryCodeSlotInfo());
        telephonyCountryCodeInfoSlot.slotIdx = slotIdx;
        telephonyCountryCodeInfoSlot.timestamp = LocalDateTime.now().format(FORMATTER);
        // Empty country code.
        if (!isValid(countryCode)) {
            Log.d(TAG, "Received empty telephony country code");
            telephonyCountryCodeInfoSlot.countryCode = null;
        } else {
            telephonyCountryCodeInfoSlot.countryCode = countryCode.toUpperCase(Locale.US);
        }
        if (!isValid(lastKnownCountryCode)) {
            Log.d(TAG, "Received empty telephony last known country code");
            telephonyCountryCodeInfoSlot.lastKnownCountryCode = null;
        } else {
            telephonyCountryCodeInfoSlot.lastKnownCountryCode =
                    lastKnownCountryCode.toUpperCase(Locale.US);
        }
        setCountryCode(false);
    }

    private void setWifiCountryCode(String countryCode) {
        Log.d(TAG, "Set wifi country code to: " + countryCode);
        mWifiCountryTimestamp = LocalDateTime.now().format(FORMATTER);
        // Empty country code.
        if (!isValid(countryCode)) {
            Log.d(TAG, "Received empty wifi country code");
            mWifiCountryCode = null;
        } else {
            mWifiCountryCode = countryCode.toUpperCase(Locale.US);
        }
        setCountryCode(false);
    }

    private void setLocationCountryCode(String countryCode) {
        Log.d(TAG, "Set location country code to: " + countryCode);
        mLocationCountryTimestamp = LocalDateTime.now().format(FORMATTER);
        // Empty country code.
        if (!isValid(countryCode)) {
            Log.d(TAG, "Received empty location country code");
            mLocationCountryCode = null;
        } else {
            mLocationCountryCode = countryCode.toUpperCase(Locale.US);
        }
        setCountryCode(false);
    }

    /**
     * Priority order of country code sources (we stop at the first known country code source):
     * 1. Override country code - Country code forced via shell command (local/automated testing)
     * 2. Telephony country code - Current country code retrieved via cellular. If there are
     * multiple SIM's, the country code chosen is non-deterministic if they return different codes.
     * 3. Wifi country code - Current country code retrieved via wifi (via 80211.ad).
     * 4. Last known telephony country code - Last known country code retrieved via cellular. If
     * there are multiple SIM's, the country code chosen is non-deterministic if they return
     * different codes.
     * 5. Location Country code - If enabled, country code retrieved from LocationManager Fused
     * location provider.
     * 6. Cached Country code - If enabled (only enabled on non-phone form factors), uses last
     * valid country code retrieved from any of the sources above (cache cleared on APM mode
     * toggle).
     * 7. OEM default country code - If set by the OEM, then we default to this country code.
     * @return
     */
    private String pickCountryCode() {
        if (mOverrideCountryCode != null) {
            return mOverrideCountryCode;
        }
        for (TelephonyCountryCodeSlotInfo telephonyCountryCodeInfoSlot :
                mTelephonyCountryCodeInfoPerSlot.values()) {
            if (telephonyCountryCodeInfoSlot.countryCode != null) {
                return telephonyCountryCodeInfoSlot.countryCode;
            }
        }
        if (mWifiCountryCode != null) {
            return mWifiCountryCode;
        }
        for (TelephonyCountryCodeSlotInfo telephonyCountryCodeInfoSlot :
                mTelephonyCountryCodeInfoPerSlot.values()) {
            if (telephonyCountryCodeInfoSlot.lastKnownCountryCode != null) {
                return telephonyCountryCodeInfoSlot.lastKnownCountryCode;
            }
        }
        if (mLocationCountryCode != null) {
            return mLocationCountryCode;
        }
        if (mUwbInjector.getDeviceConfigFacade().isPersistentCacheUseForCountryCodeEnabled()
                && mCachedCountryCode != null) {
            Log.d(TAG, "Using cached country code");
            return mCachedCountryCode;
        }
        return mUwbInjector.getOemDefaultCountryCode();
    }

    /**
     * Set country code
     *
     * @param forceUpdate Force update the country code even if it was the same as previously cached
     *                    value.
     * @return Pair<UWBS StatusCode from setting the country code,
     *              Country code that was attempted to be set in UWBS>
     */
    public Pair<Integer, String> setCountryCode(boolean forceUpdate) {
        String country = pickCountryCode();
        if (country == null) {
            Log.i(TAG, "No valid country code, reset to " + DEFAULT_COUNTRY_CODE);
            country = DEFAULT_COUNTRY_CODE;
        }
        if (!forceUpdate && Objects.equals(country, mCountryCode)) {
            Log.i(TAG, "Ignoring already set country code: " + country);
            return new Pair<>(STATUS_CODE_OK, mCountryCode);
        }
        Log.d(TAG, "setCountryCode to " + country);
        int status = mNativeUwbManager.setCountryCode(country.getBytes(StandardCharsets.UTF_8));
        if (status != STATUS_CODE_OK) {
            Log.i(TAG, "Failed to set country code, with status code: " + status);
        }
        mCountryCode = country;
        mCountryCodeUpdatedTimestamp = LocalDateTime.now().format(FORMATTER);
        mCountryCodeStatus = Optional.of(status);
        // Cache the country code (if caching is enabled on the device)
        if (mUwbInjector.getDeviceConfigFacade().isPersistentCacheUseForCountryCodeEnabled()
                && isValid(country)) {
            mCachedCountryCode = country;
            mUwbInjector.getUwbSettingsStore().put(
                    UwbSettingsStore.SETTINGS_CACHED_COUNTRY_CODE, country);
        }

        for (CountryCodeChangedListener listener : mListeners) {
            listener.onCountryCodeChanged(status, country);
        }
        return new Pair<>(status, country);
    }

    /**
     * Get country code.
     *
     * @return the country code that was last configured in the UWBS.
     */
    public String getCountryCode() {
        return mCountryCode;
    }

    /**
     * Get country code configuration status.
     *
     * @return Status of the last attempt to configure a country code in the UWBS.
     */
    public Optional<Integer> getCountryCodeStatus() {
        return mCountryCodeStatus;
    }

    /**
     * Is this a valid country code
     * @param countryCode A 2-Character alphanumeric country code.
     * @return true if the countryCode is valid, false otherwise.
     */
    public static boolean isValid(String countryCode) {
        return countryCode != null && countryCode.length() == 2
                && countryCode.chars().allMatch(Character::isLetterOrDigit)
                && !countryCode.equals(DEFAULT_COUNTRY_CODE);
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
        mOverrideCountryCode = countryCode.toUpperCase(Locale.US);
        setCountryCode(true);
    }

    /**
     * This is for clearing the country code previously set through #setOverrideCountryCode() method
     */
    public synchronized void clearOverrideCountryCode() {
        mOverrideCountryCode = null;
        setCountryCode(true);
    }

    /**
     * This is for clearing the cached country code when Airplane mode is toggled
     */
    public synchronized void clearCachedCountryCode() {
        if (mUwbInjector.getDeviceConfigFacade().isPersistentCacheUseForCountryCodeEnabled()) {
            Log.d(TAG, "Clearing cached country code");
            mCachedCountryCode = null;
            mUwbInjector.getUwbSettingsStore().put(
                    UwbSettingsStore.SETTINGS_CACHED_COUNTRY_CODE, "");
        }
    }

    /**
     * Method to dump the current state of this UwbCountryCode object.
     */
    public synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("---- Dump of UwbCountryCode ----");
        pw.println("DefaultCountryCode(system property): "
                + mUwbInjector.getOemDefaultCountryCode());
        pw.println("mOverrideCountryCode: " + mOverrideCountryCode);
        pw.println("mTelephonyCountryCodeInfoSlot: " + mTelephonyCountryCodeInfoPerSlot);
        pw.println("mWifiCountryCode: " + mWifiCountryCode);
        pw.println("mWifiCountryTimestamp: " + mWifiCountryTimestamp);
        pw.println("mLocationCountryCode: " + mLocationCountryCode);
        pw.println("mLocationCountryTimestamp: " + mLocationCountryTimestamp);
        pw.println("mCountryCode: " + mCountryCode);
        pw.println("mCountryCodeStatus: "
                + (mCountryCodeStatus.isEmpty() ? "none" : mCountryCodeStatus.get()));
        pw.println("mCountryCodeUpdatedTimestamp: " + mCountryCodeUpdatedTimestamp);
        pw.println("mCachedCountryCode: " + mCachedCountryCode);
        pw.println("---- Dump of UwbCountryCode ----");
    }
}
