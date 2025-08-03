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

package com.android.car.settings.wifi;

import static android.car.hardware.power.PowerComponent.WIFI;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.ColoredSwitchPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PowerPolicyListener;

/**
 * Enables/disables Wifi state via ColoredSwitchPreference.
 */
public class WifiStateSwitchPreferenceController extends
        WifiBasePreferenceController<ColoredSwitchPreference> {

    @VisibleForTesting
    final PowerPolicyListener mPowerPolicyListener;

    private boolean mIsPowerPolicyOn = true;
    private boolean mIsWifiEnabled;

    public WifiStateSwitchPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mPowerPolicyListener = new PowerPolicyListener(context, WIFI, isOn -> {
            // refresh power state
            mIsPowerPolicyOn = isOn;
            refreshUi();
        });
    }

    @Override
    protected Class<ColoredSwitchPreference> getPreferenceType() {
        return ColoredSwitchPreference.class;
    }

    @Override
    protected void updateState(ColoredSwitchPreference preference) {
        updateSwitchPreference();
    }

    @Override
    protected boolean handlePreferenceChanged(ColoredSwitchPreference preference,
            Object newValue) {
        boolean wifiEnabled = (Boolean) newValue;
        getCarWifiManager().setWifiEnabled(wifiEnabled);
        return true;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        getPreference().setContentDescription(
                getContext().getString(R.string.wifi_state_switch_content_description));
        setClickableWhileDisabled(getPreference(), /* clickable= */ true, p -> {
            if (mIsPowerPolicyOn) {
                // ActionDisabledByAdminDialog will be shown if DISALLOW_CONFIG_WIFI
                // is set by a device admin; otherwise, a default Toast will be shown
                WifiUtil.runClickableWhileDisabled(getContext(), getFragmentController());
            } else {
                Toast.makeText(getContext(),
                        getContext().getString(R.string.power_component_disabled),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onStartInternal() {
        super.onStartInternal();
        onWifiStateChanged(getCarWifiManager().getWifiState());
    }

    @Override
    protected void onResumeInternal() {
        mPowerPolicyListener.handleCurrentPolicy();
    }

    @Override
    protected void onDestroyInternal() {
        mPowerPolicyListener.release();
    }

    @Override
    public void onWifiEntriesChanged() {
        // intentional no-op
    }

    @Override
    public void onWifiStateChanged(int state) {
        mIsWifiEnabled = (state == WifiManager.WIFI_STATE_ENABLED
                || state == WifiManager.WIFI_STATE_ENABLING);
        refreshUi();
    }

    private void updateSwitchPreference() {
        getPreference().setChecked(mIsWifiEnabled);
    }

    @Override
    public int getDefaultAvailabilityStatus() {
        if (mIsPowerPolicyOn) {
            return WifiUtil.getAvailabilityStatus(getContext());
        }
        return AVAILABLE_FOR_VIEWING;
    }
}
