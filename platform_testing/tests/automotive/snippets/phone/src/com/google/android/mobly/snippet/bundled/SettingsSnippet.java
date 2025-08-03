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

package com.google.android.mobly.snippet.bundled;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoBluetoothSettingsHelper;
import android.platform.helpers.IAutoDateTimeSettingsHelper;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.SettingsConstants;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

/** Snippet class for exposing Settings APIs. */
public class SettingsSnippet implements Snippet {

    private HelperAccessor<IAutoSettingHelper> mSettingsHelper;
    private HelperAccessor<IAutoBluetoothSettingsHelper> mBluetoothSettingsHelper;

    private static String sBluetoothSettings = "OPEN_BLUETOOTH_SETTINGS_WORKFLOW";
    private HelperAccessor<IAutoDateTimeSettingsHelper> mDateTimeSettingsHelper;

    public SettingsSnippet() {
        mSettingsHelper = new HelperAccessor<>(IAutoSettingHelper.class);
        mBluetoothSettingsHelper = new HelperAccessor<>(IAutoBluetoothSettingsHelper.class);
        mDateTimeSettingsHelper = new HelperAccessor<>(IAutoDateTimeSettingsHelper.class);
    }

    @Rpc(description = "Return whether the bluetooth preference button is checked")
    public boolean isBluetoothPreferenceChecked() {
        return mBluetoothSettingsHelper.get().isBluetoothPreferenceChecked();
    }

    @Rpc(description = "Return whether the media preference button is checked")
    public boolean isMediaPreferenceChecked() {
        return mBluetoothSettingsHelper.get().isMediaPreferenceChecked();
    }

    @Rpc(description = "Return whether the media preference button is enabled")
    public boolean isMediaPreferenceEnabled() {
        return mBluetoothSettingsHelper.get().isMediaPreferenceEnabled();
    }

    @Rpc(description = "Return whether the phone preference button is checked.")
    public boolean isPhonePreferenceChecked() {
        return mBluetoothSettingsHelper.get().isPhonePreferenceChecked();
    }

    @Rpc(description = "Return whether the phone preference button is enabled")
    public boolean isPhonePreferenceEnabled() {
        return mBluetoothSettingsHelper.get().isPhonePreferenceEnabled();
    }

    @Rpc(
            description =
                    "Get the device summary of a device "
                            + "whose level two connection screen is currently open.")
    public String getDeviceSummary() {
        return mBluetoothSettingsHelper.get().getDeviceSummary();
    }

    @Rpc(description = "Open system settings.")
    public void openSystemSettings() {
        mSettingsHelper.get().openFullSettings();
    }

    @Rpc(description = "Open the bluetooth settings (from home screen)")
    public void openBluetoothSettings() {
        mSettingsHelper.get().openSetting(sBluetoothSettings);
    }

    /** Press the phone preference toggle button */
    @Rpc(description = "Press the phone toggle on a entry under 'Paired Devices'")
    public void pressPhoneToggleOnDevice(String deviceName) {
        mBluetoothSettingsHelper.get().pressPhoneToggleOnDevice(deviceName);
    }

    @Rpc(description = "Press the bluetooth toggle on a entry under 'Paired Devices'")
    public void pressBluetoothToggleOnDevice(String deviceName) {
        mBluetoothSettingsHelper.get().pressBluetoothToggleOnDevice(deviceName);
    }

    /**
     * RPC to press the bluetooth toggle on a entry under 'Paired Devices'"
     *
     * @param deviceName - The listed device whose bluetooth toggle should be switched.
     */
    @Rpc(description = "Press the bluetooth toggle on a entry under 'Paired Devices'")
    public void pressMediaToggleOnDevice(String deviceName) {
        mBluetoothSettingsHelper.get().pressMediaToggleOnDevice(deviceName);
    }

    @Rpc(description = "Press device on device list.")
    public void pressDeviceInBluetoothSettings(String deviceName) {
        mBluetoothSettingsHelper.get().pressDevice(deviceName);
    }

    @Rpc(description = "Press an entry listed under 'Paired Devices'")
    public void pressDeviceName(String deviceName) {
        mBluetoothSettingsHelper.get().pressDevice(deviceName);
    }

    @Rpc(
            description =
                    "Get the connection status of a device "
                            + "whose level two connection screen is currently open.")
    public void deviceIsConnected() {
        mBluetoothSettingsHelper.get().isConnected();
    }

    @Rpc(description = "Press the Forget button on a currently open Level Two connection screen")
    public void pressForget() {
        mBluetoothSettingsHelper.get().pressForget();
    }

    @Rpc(description = "Get the device timezone")
    public String getTimeZone() {
        return mDateTimeSettingsHelper.get().getTimeZone();
    }

    @Rpc(description = "Open Date time Setting")
    public void setTimeZone(String timezone) {
        mSettingsHelper.get().openSetting(SettingsConstants.DATE_AND_TIME_SETTINGS);
        mDateTimeSettingsHelper.get().setTimeZone(timezone);
    }

    @Override
    public void shutdown() {}
}
