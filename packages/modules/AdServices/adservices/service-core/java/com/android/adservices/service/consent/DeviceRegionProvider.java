/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.consent;

import android.annotation.NonNull;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.adservices.LogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Provides information about the region of the device which is used. Currently it's used only to
 * determine whether the device should be treated as EU or non-EU.
 */
public class DeviceRegionProvider {
    private static final String FEATURE_TELEPHONY = "android.hardware.telephony";

    /**
     * @return true if the device should be treated as EU device, otherwise false.
     * @param context {@link Context} of the caller.
     */
    public static boolean isEuDevice(@NonNull Context context) {
        Objects.requireNonNull(context);
        if (context.getPackageManager().hasSystemFeature(FEATURE_TELEPHONY)) {
            TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
            // if there is no telephony manager accessible, we fall back to EU device
            if (telephonyManager == null) return true;

            // we use PH to determine whether device is in the EEA region.
            if (FlagsFactory.getFlags().isEeaDeviceFeatureEnabled()) {
                LogUtil.d("Server side region detection enabled.");
                return FlagsFactory.getFlags().isEeaDevice();
            }

            // and fall back to sim card locations if the feature is not yet enabled.
            // if there is no sim card installed, we fall back to EU device
            String deviceCountryIso = telephonyManager.getSimCountryIso();
            if (deviceCountryIso.isEmpty()) {
                return true;
            }

            // if simCountryIso detects the user's country as one of EEA countries
            // we treat this device as EU device, otherwise ROW device
            if (getUiEeaCountriesSet().contains(deviceCountryIso.toUpperCase(Locale.ENGLISH))) {
                return true;
            }

            return false;
        }
        // if there is no telephony feature, we fall back to EU device
        return true;
    }

    /**
     * @return true if the device should be treated as EU device, otherwise false.
     * @param context {@link Context} of the caller.
     */
    public static boolean isEuDevice(@NonNull Context context, @NonNull Flags flags) {
        Objects.requireNonNull(context);
        if (context.getPackageManager().hasSystemFeature(FEATURE_TELEPHONY)) {
            TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
            // if there is no telephony manager accessible, we fall back to EU device
            if (telephonyManager == null) return true;

            // we use PH to determine whether device is in the EEA region.
            if (flags.isEeaDeviceFeatureEnabled()) {
                return flags.isEeaDevice();
            }

            // and fall back to sim card locations if the feature is not yet enabled.
            // if there is no sim card installed, we fall back to EU device
            String deviceCountryIso = telephonyManager.getSimCountryIso();
            if (deviceCountryIso.isEmpty()) {
                return true;
            }

            // if simCountryIso detects the user's country as one of EEA countries
            // we treat this device as EU device, otherwise ROW device
            if (getUiEeaCountriesSet(flags)
                    .contains(deviceCountryIso.toUpperCase(Locale.ENGLISH))) {
                return true;
            }

            return false;
        }
        // if there is no telephony feature, we fall back to EU device
        return true;
    }

    private static Set<String> getUiEeaCountriesSet(Flags flags) {
        String uiEeaCountries = flags.getUiEeaCountries();
        if (!isValidEeaCountriesString(uiEeaCountries)) {
            LogUtil.e("Invalid EEA countries string.");
            return Set.of();
        } else {
            return Set.of(uiEeaCountries.split(","));
        }
    }

    private static Set<String> getUiEeaCountriesSet() {
        String uiEeaCountries = FlagsFactory.getFlags().getUiEeaCountries();
        if (!isValidEeaCountriesString(uiEeaCountries)) {
            LogUtil.e("Invalid EEA countries string.");
            return Set.of();
        } else {
            return Set.of(uiEeaCountries.split(","));
        }
    }

    /** Checks whether an EEA countries string is valid. */
    public static boolean isValidEeaCountriesString(String str) {
        if (str == null || TextUtils.isEmpty(str)) {
            return false;
        }
        return (str + ",")
                .matches(String.format(Locale.US, "^([A-Z]{2},){%d}$", (str.length() + 1) / 3));
    }
}
