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

package com.android.tv.settings.system.development;

import android.content.Context;
import android.debug.PairDevice;

import androidx.preference.Preference;

import com.android.tv.settings.R;

/**
 * An AP preference for the currently connected AP
 */
public class AdbPairedDevicePreference extends Preference {
    private PairDevice mPairedDevice;

    /**
     * Constructor for creating an instance by a given PairDevice object.
     */
    public AdbPairedDevicePreference(PairDevice pairedDevice, Context context) {
        super(context);
        mPairedDevice = pairedDevice;
        refresh();
        launchAdbDeviceDetailsFragment();
    }

    /**
     * Refreshes the preference bound to the paired device previously passed in.
     */
    public void refresh() {
        setTitle();
        setIcon();
    }

    /**
     * Function for updating the newly given PairDevice object in the preference.
     */
    public void setPairedDevice(PairDevice pairedDevice) {
        mPairedDevice = pairedDevice;
    }

    private void setTitle() {
        if (mPairedDevice != null) {
            this.setTitle(mPairedDevice.name);
            this.setSummary(mPairedDevice.connected
                    ? this.getContext().getText(
                    R.string.adb_wireless_device_connected_summary)
                    : "");
        }
    }

    private void setIcon() {
        this.setIcon(R.drawable.ic_settings);
    }

    private void launchAdbDeviceDetailsFragment() {
        // For sending to the device details fragment.
        this.setFragment(AdbDeviceDetailsFragment.class.getName());
        if (mPairedDevice != null) {
            AdbDeviceDetailsFragment.prepareArgs(
                    this.getExtras(), mPairedDevice.name, mPairedDevice.guid);
        }
    }
}
