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

package com.android.server.thread;

import android.annotation.Nullable;
import android.annotation.StringDef;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.thread.IOperationReceiver;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.ActiveCountryCodeChangedCallback;
import android.os.Build;
import android.sysprop.ThreadNetworkProperties;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Log;

import com.android.connectivity.resources.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.connectivity.ConnectivityResources;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Provide functions for making changes to Thread Network country code. This Country Code is from
 * location, WiFi, telephony or OEM configuration. This class sends Country Code to Thread Network
 * native layer.
 *
 * <p>This class is thread-safe.
 */
@TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class ThreadNetworkCountryCode {
    private static final String TAG = "ThreadNetworkCountryCode";
    // To be used when there is no country code available.
    @VisibleForTesting public static final String DEFAULT_COUNTRY_CODE = "WW";

    // Wait 1 hour between updates.
    private static final long TIME_BETWEEN_LOCATION_UPDATES_MS = 1000L * 60 * 60 * 1;
    // Minimum distance before an update is triggered, in meters. We don't need this to be too
    // exact because all we care about is what country the user is in.
    private static final float DISTANCE_BETWEEN_LOCALTION_UPDATES_METERS = 5_000.0f;

    /** List of country code sources. */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef(
            prefix = "COUNTRY_CODE_SOURCE_",
            value = {
                COUNTRY_CODE_SOURCE_DEFAULT,
                COUNTRY_CODE_SOURCE_LOCATION,
                COUNTRY_CODE_SOURCE_OEM,
                COUNTRY_CODE_SOURCE_OVERRIDE,
                COUNTRY_CODE_SOURCE_TELEPHONY,
                COUNTRY_CODE_SOURCE_TELEPHONY_LAST,
                COUNTRY_CODE_SOURCE_WIFI,
            })
    private @interface CountryCodeSource {}

    private static final String COUNTRY_CODE_SOURCE_DEFAULT = "Default";
    private static final String COUNTRY_CODE_SOURCE_LOCATION = "Location";
    private static final String COUNTRY_CODE_SOURCE_OEM = "Oem";
    private static final String COUNTRY_CODE_SOURCE_OVERRIDE = "Override";
    private static final String COUNTRY_CODE_SOURCE_TELEPHONY = "Telephony";
    private static final String COUNTRY_CODE_SOURCE_TELEPHONY_LAST = "TelephonyLast";
    private static final String COUNTRY_CODE_SOURCE_WIFI = "Wifi";

    private static final CountryCodeInfo DEFAULT_COUNTRY_CODE_INFO =
            new CountryCodeInfo(DEFAULT_COUNTRY_CODE, COUNTRY_CODE_SOURCE_DEFAULT);

    private final ConnectivityResources mResources;
    private final Context mContext;
    private final LocationManager mLocationManager;
    @Nullable private final Geocoder mGeocoder;
    private final ThreadNetworkControllerService mThreadNetworkControllerService;
    private final WifiManager mWifiManager;
    private final TelephonyManager mTelephonyManager;
    private final SubscriptionManager mSubscriptionManager;
    private final Map<Integer, TelephonyCountryCodeSlotInfo> mTelephonyCountryCodeSlotInfoMap =
            new ArrayMap();

    @Nullable private CountryCodeInfo mCurrentCountryCodeInfo;
    @Nullable private CountryCodeInfo mLocationCountryCodeInfo;
    @Nullable private CountryCodeInfo mOverrideCountryCodeInfo;
    @Nullable private CountryCodeInfo mWifiCountryCodeInfo;
    @Nullable private CountryCodeInfo mTelephonyCountryCodeInfo;
    @Nullable private CountryCodeInfo mTelephonyLastCountryCodeInfo;
    @Nullable private CountryCodeInfo mOemCountryCodeInfo;

    /** Container class to store Thread country code information. */
    private static final class CountryCodeInfo {
        private String mCountryCode;
        @CountryCodeSource private String mSource;
        private final Instant mUpdatedTimestamp;

        /**
         * Constructs a new {@code CountryCodeInfo} from the given country code, country code source
         * and country coode created time.
         *
         * @param countryCode a String representation of the country code as defined in ISO 3166.
         * @param countryCodeSource a String representation of country code source.
         * @param instant a Instant representation of the time when the country code was created.
         * @throws IllegalArgumentException if {@code countryCode} contains invalid country code.
         */
        public CountryCodeInfo(
                String countryCode, @CountryCodeSource String countryCodeSource, Instant instant) {
            if (!isValidCountryCode(countryCode)) {
                throw new IllegalArgumentException("Country code is invalid: " + countryCode);
            }

            mCountryCode = countryCode;
            mSource = countryCodeSource;
            mUpdatedTimestamp = instant;
        }

        /**
         * Constructs a new {@code CountryCodeInfo} from the given country code, country code
         * source. The updated timestamp of the country code will be set to the time when {@code
         * CountryCodeInfo} was constructed.
         *
         * @param countryCode a String representation of the country code as defined in ISO 3166.
         * @param countryCodeSource a String representation of country code source.
         * @throws IllegalArgumentException if {@code countryCode} contains invalid country code.
         */
        public CountryCodeInfo(String countryCode, @CountryCodeSource String countryCodeSource) {
            this(countryCode, countryCodeSource, Instant.now());
        }

        public String getCountryCode() {
            return mCountryCode;
        }

        public boolean isCountryCodeMatch(CountryCodeInfo countryCodeInfo) {
            if (countryCodeInfo == null) {
                return false;
            }

            return Objects.equals(countryCodeInfo.mCountryCode, mCountryCode);
        }

        @Override
        public String toString() {
            return "CountryCodeInfo{ mCountryCode: "
                    + mCountryCode
                    + ", mSource: "
                    + mSource
                    + ", mUpdatedTimestamp: "
                    + mUpdatedTimestamp
                    + "}";
        }
    }

    /** Container class to store country code per SIM slot. */
    private static final class TelephonyCountryCodeSlotInfo {
        public int slotIndex;
        public String countryCode;
        public String lastKnownCountryCode;
        public Instant timestamp;

        @Override
        public String toString() {
            return "TelephonyCountryCodeSlotInfo{ slotIndex: "
                    + slotIndex
                    + ", countryCode: "
                    + countryCode
                    + ", lastKnownCountryCode: "
                    + lastKnownCountryCode
                    + ", timestamp: "
                    + timestamp
                    + "}";
        }
    }

    private boolean isLocationUseForCountryCodeEnabled() {
        return mResources
                .get()
                .getBoolean(R.bool.config_thread_location_use_for_country_code_enabled);
    }

    public ThreadNetworkCountryCode(
            LocationManager locationManager,
            ThreadNetworkControllerService threadNetworkControllerService,
            @Nullable Geocoder geocoder,
            ConnectivityResources resources,
            WifiManager wifiManager,
            Context context,
            TelephonyManager telephonyManager,
            SubscriptionManager subscriptionManager,
            @Nullable String oemCountryCode) {
        mLocationManager = locationManager;
        mThreadNetworkControllerService = threadNetworkControllerService;
        mGeocoder = geocoder;
        mResources = resources;
        mWifiManager = wifiManager;
        mContext = context;
        mTelephonyManager = telephonyManager;
        mSubscriptionManager = subscriptionManager;

        if (oemCountryCode != null) {
            mOemCountryCodeInfo = new CountryCodeInfo(oemCountryCode, COUNTRY_CODE_SOURCE_OEM);
        }
    }

    public static ThreadNetworkCountryCode newInstance(
            Context context, ThreadNetworkControllerService controllerService) {
        return new ThreadNetworkCountryCode(
                context.getSystemService(LocationManager.class),
                controllerService,
                Geocoder.isPresent() ? new Geocoder(context) : null,
                new ConnectivityResources(context),
                context.getSystemService(WifiManager.class),
                context,
                context.getSystemService(TelephonyManager.class),
                context.getSystemService(SubscriptionManager.class),
                ThreadNetworkProperties.country_code().orElse(null));
    }

    /** Sets up this country code module to listen to location country code changes. */
    public synchronized void initialize() {
        registerGeocoderCountryCodeCallback();
        registerWifiCountryCodeCallback();
        registerTelephonyCountryCodeCallback();
        updateTelephonyCountryCodeFromSimCard();
        updateCountryCode(false /* forceUpdate */);
    }

    private synchronized void registerGeocoderCountryCodeCallback() {
        if ((mGeocoder != null) && isLocationUseForCountryCodeEnabled()) {
            mLocationManager.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER,
                    TIME_BETWEEN_LOCATION_UPDATES_MS,
                    DISTANCE_BETWEEN_LOCALTION_UPDATES_METERS,
                    location -> setCountryCodeFromGeocodingLocation(location));
        }
    }

    private synchronized void geocodeListener(List<Address> addresses) {
        if (addresses != null && !addresses.isEmpty()) {
            String countryCode = addresses.get(0).getCountryCode();

            if (isValidCountryCode(countryCode)) {
                Log.d(TAG, "Set location country code to: " + countryCode);
                mLocationCountryCodeInfo =
                        new CountryCodeInfo(countryCode, COUNTRY_CODE_SOURCE_LOCATION);
            } else {
                Log.d(TAG, "Received invalid location country code");
                mLocationCountryCodeInfo = null;
            }

            updateCountryCode(false /* forceUpdate */);
        }
    }

    private synchronized void setCountryCodeFromGeocodingLocation(@Nullable Location location) {
        if ((location == null) || (mGeocoder == null)) return;

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            Log.wtf(
                    TAG,
                    "Unexpected call to set country code from the Geocoding location, "
                            + "Thread code never runs under T or lower.");
            return;
        }

        mGeocoder.getFromLocation(
                location.getLatitude(),
                location.getLongitude(),
                1 /* maxResults */,
                this::geocodeListener);
    }

    private synchronized void registerWifiCountryCodeCallback() {
        if (mWifiManager != null) {
            mWifiManager.registerActiveCountryCodeChangedCallback(
                    r -> r.run(), new WifiCountryCodeCallback());
        }
    }

    private class WifiCountryCodeCallback implements ActiveCountryCodeChangedCallback {
        @Override
        public void onActiveCountryCodeChanged(String countryCode) {
            Log.d(TAG, "Wifi country code is changed to " + countryCode);
            synchronized ("ThreadNetworkCountryCode.this") {
                if (isValidCountryCode(countryCode)) {
                    mWifiCountryCodeInfo =
                            new CountryCodeInfo(countryCode, COUNTRY_CODE_SOURCE_WIFI);
                } else {
                    Log.w(TAG, "WiFi country code " + countryCode + " is invalid");
                    mWifiCountryCodeInfo = null;
                }

                updateCountryCode(false /* forceUpdate */);
            }
        }

        @Override
        public void onCountryCodeInactive() {
            Log.d(TAG, "Wifi country code is inactived");
            synchronized ("ThreadNetworkCountryCode.this") {
                mWifiCountryCodeInfo = null;
                updateCountryCode(false /* forceUpdate */);
            }
        }
    }

    private synchronized void registerTelephonyCountryCodeCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.wtf(
                    TAG,
                    "Unexpected call to register the telephony country code changed callback, "
                            + "Thread code never runs under T or lower.");
            return;
        }

        BroadcastReceiver broadcastReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        int slotIndex =
                                intent.getIntExtra(
                                        SubscriptionManager.EXTRA_SLOT_INDEX,
                                        SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                        String lastKnownCountryCode = null;
                        String countryCode =
                                intent.getStringExtra(TelephonyManager.EXTRA_NETWORK_COUNTRY);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            lastKnownCountryCode =
                                    intent.getStringExtra(
                                            TelephonyManager.EXTRA_LAST_KNOWN_NETWORK_COUNTRY);
                        }

                        setTelephonyCountryCodeAndLastKnownCountryCode(
                                slotIndex, countryCode, lastKnownCountryCode);
                    }
                };

        mContext.registerReceiver(
                broadcastReceiver,
                new IntentFilter(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED),
                Context.RECEIVER_EXPORTED);
    }

    private synchronized void updateTelephonyCountryCodeFromSimCard() {
        List<SubscriptionInfo> subscriptionInfoList =
                mSubscriptionManager.getActiveSubscriptionInfoList();

        if (subscriptionInfoList == null) {
            Log.d(TAG, "No SIM card is found");
            return;
        }

        for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
            String countryCode;
            int slotIndex;

            slotIndex = subscriptionInfo.getSimSlotIndex();
            try {
                countryCode = mTelephonyManager.getNetworkCountryIso(slotIndex);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to get country code for slot index:" + slotIndex, e);
                continue;
            }

            Log.d(TAG, "Telephony slot " + slotIndex + " country code is " + countryCode);
            setTelephonyCountryCodeAndLastKnownCountryCode(
                    slotIndex, countryCode, null /* lastKnownCountryCode */);
        }
    }

    private synchronized void setTelephonyCountryCodeAndLastKnownCountryCode(
            int slotIndex, String countryCode, String lastKnownCountryCode) {
        Log.d(
                TAG,
                "Set telephony country code to: "
                        + countryCode
                        + ", last country code to: "
                        + lastKnownCountryCode
                        + " for slotIndex: "
                        + slotIndex);

        TelephonyCountryCodeSlotInfo telephonyCountryCodeInfoSlot =
                mTelephonyCountryCodeSlotInfoMap.computeIfAbsent(
                        slotIndex, k -> new TelephonyCountryCodeSlotInfo());
        telephonyCountryCodeInfoSlot.slotIndex = slotIndex;
        telephonyCountryCodeInfoSlot.timestamp = Instant.now();
        telephonyCountryCodeInfoSlot.countryCode = countryCode;
        telephonyCountryCodeInfoSlot.lastKnownCountryCode = lastKnownCountryCode;

        mTelephonyCountryCodeInfo = null;
        mTelephonyLastCountryCodeInfo = null;

        for (TelephonyCountryCodeSlotInfo slotInfo : mTelephonyCountryCodeSlotInfoMap.values()) {
            if ((mTelephonyCountryCodeInfo == null) && isValidCountryCode(slotInfo.countryCode)) {
                mTelephonyCountryCodeInfo =
                        new CountryCodeInfo(
                                slotInfo.countryCode,
                                COUNTRY_CODE_SOURCE_TELEPHONY,
                                slotInfo.timestamp);
            }

            if ((mTelephonyLastCountryCodeInfo == null)
                    && isValidCountryCode(slotInfo.lastKnownCountryCode)) {
                mTelephonyLastCountryCodeInfo =
                        new CountryCodeInfo(
                                slotInfo.lastKnownCountryCode,
                                COUNTRY_CODE_SOURCE_TELEPHONY_LAST,
                                slotInfo.timestamp);
            }
        }

        updateCountryCode(false /* forceUpdate */);
    }

    /**
     * Priority order of country code sources (we stop at the first known country code source):
     *
     * <ul>
     *   <li>1. Override country code - Country code forced via shell command (local/automated
     *       testing)
     *   <li>2. Telephony country code - Current country code retrieved via cellular. If there are
     *       multiple SIM's, the country code chosen is non-deterministic if they return different
     *       codes. The first valid country code with the lowest slot number will be used.
     *   <li>3. Wifi country code - Current country code retrieved via wifi (via 80211.ad).
     *   <li>4. Last known telephony country code - Last known country code retrieved via cellular.
     *       If there are multiple SIM's, the country code chosen is non-deterministic if they
     *       return different codes. The first valid last known country code with the lowest slot
     *       number will be used.
     *   <li>5. Location country code - Country code retrieved from LocationManager passive location
     *       provider.
     *   <li>6. OEM country code - Country code retrieved from the system property
     *       `threadnetwork.country_code`.
     *   <li>7. Default country code `WW`.
     * </ul>
     *
     * @return the selected country code information.
     */
    private CountryCodeInfo pickCountryCode() {
        if (mOverrideCountryCodeInfo != null) {
            return mOverrideCountryCodeInfo;
        }

        if (mTelephonyCountryCodeInfo != null) {
            return mTelephonyCountryCodeInfo;
        }

        if (mWifiCountryCodeInfo != null) {
            return mWifiCountryCodeInfo;
        }

        if (mTelephonyLastCountryCodeInfo != null) {
            return mTelephonyLastCountryCodeInfo;
        }

        if (mLocationCountryCodeInfo != null) {
            return mLocationCountryCodeInfo;
        }

        if (mOemCountryCodeInfo != null) {
            return mOemCountryCodeInfo;
        }

        return DEFAULT_COUNTRY_CODE_INFO;
    }

    private IOperationReceiver newOperationReceiver(CountryCodeInfo countryCodeInfo) {
        return new IOperationReceiver.Stub() {
            @Override
            public void onSuccess() {
                synchronized ("ThreadNetworkCountryCode.this") {
                    mCurrentCountryCodeInfo = countryCodeInfo;
                }
            }

            @Override
            public void onError(int otError, String message) {
                Log.e(
                        TAG,
                        "Error "
                                + otError
                                + ": "
                                + message
                                + ". Failed to set country code "
                                + countryCodeInfo);
            }
        };
    }

    /**
     * Updates country code to the Thread native layer.
     *
     * @param forceUpdate Force update the country code even if it was the same as previously cached
     *     value.
     */
    @VisibleForTesting
    public synchronized void updateCountryCode(boolean forceUpdate) {
        CountryCodeInfo countryCodeInfo = pickCountryCode();

        if (!forceUpdate && countryCodeInfo.isCountryCodeMatch(mCurrentCountryCodeInfo)) {
            Log.i(TAG, "Ignoring already set country code " + countryCodeInfo.getCountryCode());
            return;
        }

        Log.i(TAG, "Set country code: " + countryCodeInfo);
        mThreadNetworkControllerService.setCountryCode(
                countryCodeInfo.getCountryCode().toUpperCase(Locale.ROOT),
                newOperationReceiver(countryCodeInfo));
    }

    /** Returns the current country code or {@code null} if no country code is set. */
    @Nullable
    public synchronized String getCountryCode() {
        return (mCurrentCountryCodeInfo != null) ? mCurrentCountryCodeInfo.getCountryCode() : null;
    }

    /**
     * Returns {@code true} if {@code countryCode} is a valid country code.
     *
     * <p>A country code is valid if it consists of 2 alphabets.
     */
    public static boolean isValidCountryCode(String countryCode) {
        return countryCode != null
                && countryCode.length() == 2
                && countryCode.chars().allMatch(Character::isLetter);
    }

    /**
     * Overrides any existing country code.
     *
     * @param countryCode A 2-Character alphabetical country code (as defined in ISO 3166).
     * @throws IllegalArgumentException if {@code countryCode} is an invalid country code.
     */
    public synchronized void setOverrideCountryCode(String countryCode) {
        if (!isValidCountryCode(countryCode)) {
            throw new IllegalArgumentException("The override country code is invalid");
        }

        mOverrideCountryCodeInfo = new CountryCodeInfo(countryCode, COUNTRY_CODE_SOURCE_OVERRIDE);
        updateCountryCode(true /* forceUpdate */);
    }

    /** Clears the country code previously set through {@link #setOverrideCountryCode} method. */
    public synchronized void clearOverrideCountryCode() {
        mOverrideCountryCodeInfo = null;
        updateCountryCode(true /* forceUpdate */);
    }

    /** Dumps the current state of this ThreadNetworkCountryCode object. */
    public synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("---- Dump of ThreadNetworkCountryCode begin ----");
        pw.println("mOverrideCountryCodeInfo        : " + mOverrideCountryCodeInfo);
        pw.println("mTelephonyCountryCodeSlotInfoMap: " + mTelephonyCountryCodeSlotInfoMap);
        pw.println("mTelephonyCountryCodeInfo       : " + mTelephonyCountryCodeInfo);
        pw.println("mWifiCountryCodeInfo            : " + mWifiCountryCodeInfo);
        pw.println("mTelephonyLastCountryCodeInfo   : " + mTelephonyLastCountryCodeInfo);
        pw.println("mLocationCountryCodeInfo        : " + mLocationCountryCodeInfo);
        pw.println("mOemCountryCodeInfo             : " + mOemCountryCodeInfo);
        pw.println("mCurrentCountryCodeInfo         : " + mCurrentCountryCodeInfo);
        pw.println("---- Dump of ThreadNetworkCountryCode end ------");
    }
}
