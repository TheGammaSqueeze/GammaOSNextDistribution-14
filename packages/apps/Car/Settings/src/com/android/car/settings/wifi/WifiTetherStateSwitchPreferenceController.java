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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import com.android.car.settings.R;
import com.android.car.settings.common.ColoredSwitchPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Enables/disables tether state via SwitchPreference.
 */
public class WifiTetherStateSwitchPreferenceController extends
        PreferenceController<ColoredSwitchPreference> implements
        WifiTetheringHandler.WifiTetheringAvailabilityListener {

    private WifiTetheringHandler mWifiTetheringHandler;

    public WifiTetherStateSwitchPreferenceController(Context context,
            String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mWifiTetheringHandler = new WifiTetheringHandler(context,
                fragmentController.getSettingsLifecycle(), this);
    }

    @Override
    protected Class<ColoredSwitchPreference> getPreferenceType() {
        return ColoredSwitchPreference.class;
    }

    @Override
    protected void updateState(ColoredSwitchPreference preference) {
        updateSwitchPreference(mWifiTetheringHandler.isWifiTetheringEnabled());
    }

    @Override
    protected boolean handlePreferenceChanged(ColoredSwitchPreference preference, Object newValue) {
        boolean switchOn = (Boolean) newValue;
        updateSwitchPreference(switchOn);
        mWifiTetheringHandler.updateWifiTetheringState(switchOn);
        return true;
    }

    @Override
    protected void onCreateInternal() {
        getPreference().setContentDescription(
                getContext().getString(R.string.wifi_hotspot_state_switch_content_description));
    }

    @Override
    protected void onStartInternal() {
        mWifiTetheringHandler.onStartInternal();
    }

    @Override
    protected void onStopInternal() {
        mWifiTetheringHandler.onStopInternal();
    }

    @Override
    public void onWifiTetheringAvailable() {
        updateSwitchPreference(/* switchOn= */ true);
    }

    @Override
    public void onWifiTetheringUnavailable() {
        updateSwitchPreference(/* switchOn= */ false);
    }

    @Override
    public void enablePreference() {
        getPreference().setEnabled(getAvailabilityStatus() == AVAILABLE);
    }

    @Override
    public void disablePreference() {
        getPreference().setEnabled(false);
    }

    private void updateSwitchPreference(boolean switchOn) {
        getPreference().setChecked(switchOn);
    }
}
