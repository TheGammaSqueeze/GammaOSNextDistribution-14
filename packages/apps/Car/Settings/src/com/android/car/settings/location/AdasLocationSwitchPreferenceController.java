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

package com.android.car.settings.location;

import static android.car.hardware.power.PowerComponent.LOCATION;

import android.car.drivingstate.CarUxRestrictions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PowerPolicyListener;
import com.android.car.settings.common.PreferenceController;
import com.android.car.ui.preference.CarUiSwitchPreference;

/**
 * Enables/disables ADAS (Advanced Driver-assistance systems) GNSS bypass via SwitchPreference.
 *
 * <p>This switch is not affected by {@link android.os.UserManager#DISALLOW_CONFIG_LOCATION} or
 * {@link android.os.UserManager#DISALLOW_SHARE_LOCATION} to prevent a device policy manager from
 * changing settings that can negatively impact the safety of the driver.
 */
public class AdasLocationSwitchPreferenceController extends
        PreferenceController<CarUiSwitchPreference> {
    private final LocationManager mLocationManager;

    private final BroadcastReceiver mAdasReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };

    private final BroadcastReceiver mLocationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Turns Driver assistance on when main location switch is on. Location service don't
            // support the case where main location switch on and Driver assistance off
            if (mLocationManager.isLocationEnabled()) {
                mLocationManager.setAdasGnssLocationEnabled(true);
            }
            refreshUi();
        }
    };

    private static final IntentFilter INTENT_FILTER_ADAS_GNSS_ENABLED_CHANGED =
            new IntentFilter(LocationManager.ACTION_ADAS_GNSS_ENABLED_CHANGED);

    private static final IntentFilter INTENT_FILTER_LOCATION_MODE_CHANGED =
            new IntentFilter(LocationManager.MODE_CHANGED_ACTION);
    @VisibleForTesting
    final PowerPolicyListener mPowerPolicyListener;
    @VisibleForTesting
    boolean mIsClickable;
    private boolean mIsPowerPolicyOn = true;

    public AdasLocationSwitchPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mLocationManager = context.getSystemService(LocationManager.class);
        mPowerPolicyListener = new PowerPolicyListener(context, LOCATION,
                isOn -> {
                    mIsPowerPolicyOn = isOn;
                    refreshUi();
                });
    }

    @Override
    protected Class<CarUiSwitchPreference> getPreferenceType() {
        return CarUiSwitchPreference.class;
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        return mIsClickable && mIsPowerPolicyOn && !mLocationManager.isLocationEnabled()
                ? AVAILABLE
                : AVAILABLE_FOR_VIEWING;
    }

    @Override
    protected void updateState(CarUiSwitchPreference preference) {
        preference.setChecked(mLocationManager.isAdasGnssLocationEnabled());
    }

    @Override
    protected void onCreateInternal() {
        mIsClickable = getContext().getResources()
                .getBoolean(R.bool.config_allow_adas_location_switch_clickable);

        getPreference().setOnPreferenceChangeListener((pref, val) -> {
            if (mLocationManager.isAdasGnssLocationEnabled()) {
                getFragmentController().showDialog(getConfirmationDialog(),
                        ConfirmationDialogFragment.TAG);
                return false;
            } else {
                mLocationManager.setAdasGnssLocationEnabled(true);
                return true;
            }
        });

        setClickableWhileDisabled(getPreference(), /* clickable= */true, preference -> {
            if (!mIsClickable) {
                getFragmentController().showDialog(getToggleDisabledDialog(),
                        ConfirmationDialogFragment.TAG);
                return;
            }
            if (!mIsPowerPolicyOn) {
                Toast.makeText(getContext(), R.string.power_component_disabled, Toast.LENGTH_LONG)
                        .show();
            }
        });
    }

    @Override
    protected void onStartInternal() {
        getContext().registerReceiver(mAdasReceiver, INTENT_FILTER_ADAS_GNSS_ENABLED_CHANGED,
                Context.RECEIVER_NOT_EXPORTED);
        getContext().registerReceiver(mLocationReceiver, INTENT_FILTER_LOCATION_MODE_CHANGED,
                Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onResumeInternal() {
        mPowerPolicyListener.handleCurrentPolicy();
    }

    @Override
    protected void onStopInternal() {
        getContext().unregisterReceiver(mAdasReceiver);
        getContext().unregisterReceiver(mLocationReceiver);
    }

    @Override
    protected void onDestroyInternal() {
        mPowerPolicyListener.release();
    }

    /**
     * Assigns confirm action as negative button listener and cancel action as positive button
     * listener, because the UX design requires the cancel button has to be on right and the confirm
     * button on left.
     */
    private ConfirmationDialogFragment getConfirmationDialog() {
        return new ConfirmationDialogFragment.Builder(getContext())
                .setMessage(R.string.adas_location_toggle_off_warning)
                .setNegativeButton(
                        R.string.adas_location_toggle_confirm_label,
                        arguments -> {
                            // This if statement is included because the power policy handler runs
                            // slightly after the UI is initialized. Therefore, there's a small
                            // timeframe for the user to toggle the switch before the UI updates
                            // and disables the switch because the power policy is off. This if
                            // statement mitigates this issue by reverifying the power policy
                            // status.
                            if (mIsPowerPolicyOn) {
                                mLocationManager.setAdasGnssLocationEnabled(false);
                            }
                        })
                .setPositiveButton(android.R.string.cancel, /* confirmListener= */ null)
                .build();
    }

    private ConfirmationDialogFragment getToggleDisabledDialog() {
        return new ConfirmationDialogFragment.Builder(getContext())
                .setMessage(R.string.adas_location_toggle_popup_summary)
                .setPositiveButton(android.R.string.ok, /* confirmListener= */ null)
                .build();
    }
}
