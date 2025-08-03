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

package com.android.car.settings.location;

import static android.car.hardware.power.PowerComponent.LOCATION;
import static android.os.UserManager.DISALLOW_CONFIG_LOCATION;
import static android.os.UserManager.DISALLOW_SHARE_LOCATION;

import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByDpm;

import android.car.drivingstate.CarUxRestrictions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.ColoredSwitchPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PowerPolicyListener;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.enterprise.EnterpriseUtils;
import com.android.settingslib.Utils;

/**
 * Enables/disables location state via SwitchPreference.
 */
public class LocationStateSwitchPreferenceController extends
        PreferenceController<ColoredSwitchPreference> {
    private static final Logger LOG = new Logger(
            LocationStateSwitchPreferenceController.class);
    private static final IntentFilter INTENT_FILTER_LOCATION_MODE_CHANGED =
            new IntentFilter(LocationManager.MODE_CHANGED_ACTION);

    private boolean mIsPowerPolicyOn = true;

    protected final LocationManager mLocationManager;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };

    @VisibleForTesting
    final PowerPolicyListener mPowerPolicyListener;

    public LocationStateSwitchPreferenceController(Context context,
            String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mLocationManager = context.getSystemService(LocationManager.class);
        mPowerPolicyListener = new PowerPolicyListener(context, LOCATION,
                isOn -> {
                    LOG.d("setting mIsPowerPolicyOn to " + isOn);
                    mIsPowerPolicyOn = isOn;
                    // mIsPowerPolicyOn is used in deciding the availability status.
                    // Call refreshUi() so that the UI can be updated as per
                    // getAvailabilityStatus().
                    refreshUi();
                });
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        if (hasUserRestrictionByDpm(getContext(), DISALLOW_CONFIG_LOCATION)
                || hasUserRestrictionByDpm(getContext(), DISALLOW_SHARE_LOCATION)
                || !mIsPowerPolicyOn) {
            return AVAILABLE_FOR_VIEWING;
        }
        return AVAILABLE;
    }

    @Override
    protected Class<ColoredSwitchPreference> getPreferenceType() {
        return ColoredSwitchPreference.class;
    }

    @Override
    protected void updateState(ColoredSwitchPreference preference) {
        updateSwitchPreference(preference, mLocationManager.isLocationEnabled());
    }

    @Override
    protected boolean handlePreferenceChanged(ColoredSwitchPreference preference,
            Object newValue) {
        boolean isLocationEnabled = (Boolean) newValue;
        Utils.updateLocationEnabled(
                getContext(),
                isLocationEnabled,
                UserHandle.myUserId(),
                Settings.Secure.LOCATION_CHANGER_SYSTEM_SETTINGS);
        return true;
    }

    @Override
    protected void onCreateInternal() {
        getPreference().setContentDescription(
                getContext().getString(R.string.location_state_switch_content_description));
        setClickableWhileDisabled(getPreference(), /* clickable= */ true, p -> {
            // All the cases here should coincide with the ones in getAvailabilityStatus()
            if (!mIsPowerPolicyOn) {
                Toast.makeText(getContext(), R.string.power_component_disabled, Toast.LENGTH_LONG)
                        .show();
                return;
            }
            if (hasUserRestrictionByDpm(getContext(), DISALLOW_SHARE_LOCATION)) {
                showActionDisabledByAdminDialog(DISALLOW_SHARE_LOCATION);
                return;
            }
            if (hasUserRestrictionByDpm(getContext(), DISALLOW_CONFIG_LOCATION)) {
                showActionDisabledByAdminDialog(DISALLOW_CONFIG_LOCATION);
                return;
            }
        });
    }

    @Override
    protected void onStartInternal() {
        getContext().registerReceiver(mReceiver, INTENT_FILTER_LOCATION_MODE_CHANGED);
    }

    @Override
    protected void onResumeInternal() {
        mPowerPolicyListener.handleCurrentPolicy();
    }

    @Override
    protected void onStopInternal() {
        getContext().unregisterReceiver(mReceiver);
    }

    @Override
    protected void onDestroyInternal() {
        mPowerPolicyListener.release();
    }

    private void updateSwitchPreference(ColoredSwitchPreference preference,
            boolean enabled) {
        preference.setChecked(enabled
                && !hasUserRestrictionByDpm(getContext(), DISALLOW_SHARE_LOCATION));
    }

    private void showActionDisabledByAdminDialog(String restrictionType) {
        getFragmentController().showDialog(
                EnterpriseUtils.getActionDisabledByAdminDialog(getContext(),
                        restrictionType),
                DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG);
    }
}
