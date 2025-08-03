/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.settings.wifi;

import android.annotation.NonNull;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiManager;

import androidx.preference.ListPreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controls WiFi Hotspot Security Type configuration.
 */
public class WifiTetherSecurityPreferenceController extends
        WifiTetherBasePreferenceController<ListPreference> implements WifiManager.SoftApCallback {

    protected static final String KEY_SECURITY_TYPE =
            "com.android.car.settings.wifi.KEY_SECURITY_TYPE";

    private static final Logger LOG = new Logger(WifiTetherSecurityPreferenceController.class);

    private int mSecurityType;

    private boolean mIsWpa3Supported = true;

    private final SharedPreferences mSharedPreferences = getContext().getSharedPreferences(
            WifiTetherPasswordPreferenceController.SHARED_PREFERENCE_PATH,
            Context.MODE_PRIVATE);

    private final Map<Integer, String> mSecurityMap = new LinkedHashMap<>();

    public WifiTetherSecurityPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        String[] securityNames = getContext().getResources().getStringArray(
                R.array.wifi_tether_security);
        String[] securityValues = getContext().getResources().getStringArray(
                R.array.wifi_tether_security_values);
        for (int i = 0; i < securityNames.length; i++) {
            mSecurityMap.put(Integer.parseInt(securityValues[i]), securityNames[i]);
        }
    }

    @Override
    protected Class<ListPreference> getPreferenceType() {
        return ListPreference.class;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        mSecurityType = getCarSoftApConfig().getSecurityType();
        getCarWifiManager().registerSoftApCallback(getContext().getMainExecutor(), this);
        updatePreferenceOptions();
    }

    private void updatePreferenceOptions() {
        if (!mIsWpa3Supported) {
            mSecurityMap.keySet()
                    .removeIf(key -> key > SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        }

        getPreference().setEntries(mSecurityMap.values().toArray(new CharSequence[0]));
        getPreference().setEntryValues(
                mSecurityMap.keySet().stream().map(Object::toString).toArray(CharSequence[]::new));
        // Need to update the security type in case current mSecurityType
        // is incompatible with the device
        updateSecurityType(mSecurityType);
        getPreference().setValue(String.valueOf(mSecurityType));
    }

    @Override
    protected boolean handlePreferenceChanged(ListPreference preference,
            Object newValue) {
        updateSecurityType(Integer.parseInt(newValue.toString()));
        // Rather than updating the ap config here, we will only update the security type shared
        // preference. When the user confirms their selection by going back, the config will be
        // updated by the WifiTetherPasswordPreferenceController. By updating the config in that
        // controller, we avoid running into a transient state where the (securityType, passphrase)
        // pair is invalid due to not being updated simultaneously.
        mSharedPreferences.edit().putInt(KEY_SECURITY_TYPE, mSecurityType).commit();
        refreshUi();
        return true;
    }

    private void updateSecurityType(int newValue) {
        mSecurityType = mSecurityMap.containsKey(newValue)
                ? newValue : SoftApConfiguration.SECURITY_TYPE_WPA2_PSK;
    }

    @Override
    protected void updateState(ListPreference preference) {
        super.updateState(preference);
        preference.setValue(Integer.toString(mSecurityType));
    }

    @Override
    protected String getSummary() {
        return mSecurityMap.containsKey(mSecurityType) ? mSecurityMap.get(mSecurityType)
                : getContext().getString(R.string.wifi_hotspot_security_none);
    }

    @Override
    protected String getDefaultSummary() {
        return null;
    }

    @Override
    public void onCapabilityChanged(@NonNull SoftApCapability softApCapability) {
        boolean isWpa3Supported =
                softApCapability.areFeaturesSupported(SoftApCapability.SOFTAP_FEATURE_WPA3_SAE);
        if (!isWpa3Supported) {
            LOG.i("WPA3 SAE is not supported on this device");
        }
        if (mIsWpa3Supported != isWpa3Supported) {
            mIsWpa3Supported = isWpa3Supported;
            updatePreferenceOptions();
        }
        getCarWifiManager().unregisterSoftApCallback(this);
    }
}
