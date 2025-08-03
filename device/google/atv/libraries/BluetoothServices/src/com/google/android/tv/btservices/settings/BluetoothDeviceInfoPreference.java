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

package com.google.android.tv.btservices.settings;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import com.google.android.tv.btservices.R;
import com.google.android.tv.btservices.remote.Version;

public class BluetoothDeviceInfoPreference extends Preference {

    private static final String TAG = "Atv.BtDeviceInfoPref";

    private BluetoothDevice mDevice;
    private BluetoothDeviceProvider mProvider;
    private TextView mBatteryView;
    private TextView mFirmwareView;
    private View mBatteryContainer;
    private View mFirmwareContainer;

    public BluetoothDeviceInfoPreference(Context context, BluetoothDeviceProvider provider,
            BluetoothDevice device) {
        super(context);
        mDevice = device;
        mProvider = provider;
        setLayoutResource(R.layout.bluetooth_info_preference);
        setEnabled(false);
    }

    @Override
    public void onBindViewHolder(final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        if (mDevice == null) {
            return;
        }

        View serialNumberContainer = holder.itemView.findViewById(R.id.serial_number_container);
        TextView serialNumberView = holder.itemView.findViewById(R.id.serial_number_text);
        if (serialNumberContainer != null && serialNumberView != null) {
            serialNumberContainer.setVisibility(View.VISIBLE);
            serialNumberView.setText(mDevice.getAddress());
        } else {
            Log.e(TAG, "device: " + mDevice + " " + serialNumberView);
        }

        mBatteryView = holder.itemView.findViewById(R.id.battery_level_text);
        mBatteryContainer = holder.itemView.findViewById(R.id.battery_container);
        mFirmwareView = holder.itemView.findViewById(R.id.firmware_text);
        mFirmwareContainer = holder.itemView.findViewById(R.id.firmware_container);
        update();
    }

    public void update() {
        if (mFirmwareContainer != null && mFirmwareView != null && mProvider != null) {
            Version version = mProvider.getVersion(mDevice);
            if (!Version.BAD_VERSION.equals(version)) {
                mFirmwareContainer.setVisibility(View.VISIBLE);
                mFirmwareView.setText(version.toVersionString());
            } else {
                mFirmwareContainer.setVisibility(View.GONE);
            }
        }

        if (mBatteryContainer != null && mBatteryView != null && mProvider != null) {
            int battery = mProvider.getBatteryLevel(mDevice);
            if (battery != BluetoothDevice.BATTERY_LEVEL_UNKNOWN) {
                mBatteryContainer.setVisibility(View.VISIBLE);

                final String warning =
                    getContext().getString(R.string.settings_bt_battery_low);
                // If there's an update, the warning string will appear in the option.
                if (mProvider.isBatteryLow(mDevice) && !mProvider.hasUpgrade(mDevice)) {
                    mBatteryView.setText(battery + "% (" + warning + ")");
                } else {
                    mBatteryView.setText(battery + "%");
                }
            } else {
                mBatteryContainer.setVisibility(View.GONE);
            }
        }
    }
}
