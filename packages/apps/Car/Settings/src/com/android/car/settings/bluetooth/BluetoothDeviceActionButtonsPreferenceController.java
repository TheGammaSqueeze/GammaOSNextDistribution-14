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

package com.android.car.settings.bluetooth;

import static com.android.car.settings.common.ActionButtonsPreference.ActionButtons;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import com.android.car.settings.R;
import com.android.car.settings.common.ActionButtonInfo;
import com.android.car.settings.common.ActionButtonsPreference;
import com.android.car.settings.common.FragmentController;

/**
 * Displays the action buttons (connect/disconnect and forget) a remote Bluetooth device.
 */
public class BluetoothDeviceActionButtonsPreferenceController extends
        BluetoothDevicePreferenceController<ActionButtonsPreference> {

    public BluetoothDeviceActionButtonsPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<ActionButtonsPreference> getPreferenceType() {
        return ActionButtonsPreference.class;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        updateConnectionButton(getPreference().getButton(ActionButtons.BUTTON1));
        updateForgetButton(getPreference().getButton(ActionButtons.BUTTON2));
    }

    @Override
    protected void updateState(ActionButtonsPreference preference) {
        updateConnectionButton(preference.getButton(ActionButtons.BUTTON1));
    }

    private void updateConnectionButton(ActionButtonInfo button) {
        button.setEnabled(!getCachedDevice().isBusy());
        if (getCachedDevice().isConnected()) {
            button.setText(R.string.disconnect).setIcon(R.drawable.ic_close).setOnClickListener(
                    v -> getCachedDevice().disconnect());
        } else {
            button.setText(R.string.connect).setIcon(R.drawable.ic_add).setOnClickListener(
                    v -> getCachedDevice().connect());
        }
    }

    private void updateForgetButton(ActionButtonInfo button) {
        button.setText(R.string.forget).setIcon(R.drawable.ic_delete).setOnClickListener(v -> {
            getCachedDevice().unpair();
            getFragmentController().goBack();
        });
    }
}
