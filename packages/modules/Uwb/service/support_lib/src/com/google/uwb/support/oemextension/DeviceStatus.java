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

package com.google.uwb.support.oemextension;

import android.os.PersistableBundle;
import android.uwb.UwbManager;

import com.google.uwb.support.base.RequiredParam;

/**
 * Device status for oem extension callback
 *
 * <p> This is passed as a bundle to oem extension API
 * {@link UwbManager.UwbOemExtensionCallback#onDeviceStatusNotificationReceived(PersistableBundle)}.
 */
public class DeviceStatus {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private final int mDeviceState;
    private final String mChipId;
    public static final String KEY_BUNDLE_VERSION = "bundle_version";
    public static final String DEVICE_STATE = "device_state";
    public static final String CHIP_ID = "chip_id";

    public static int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public long getDeviceState() {
        return mDeviceState;
    }

    public String getChipId() {
        return mChipId;
    }

    private DeviceStatus(int deviceState, String chipId) {
        mDeviceState = deviceState;
        this.mChipId = chipId;
    }

    public PersistableBundle toBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_BUNDLE_VERSION, getBundleVersion());
        bundle.putInt(DEVICE_STATE, mDeviceState);
        bundle.putString(CHIP_ID, mChipId);
        return bundle;
    }

    public static DeviceStatus fromBundle(PersistableBundle bundle) {
        switch (bundle.getInt(KEY_BUNDLE_VERSION)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);
            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static DeviceStatus parseVersion1(PersistableBundle bundle) {
        return new DeviceStatus.Builder()
                .setDeviceState(bundle.getInt(DEVICE_STATE))
                .setChipId(bundle.getString(CHIP_ID))
                .build();
    }

    /** Builder */
    public static class Builder {
        private final RequiredParam<Integer> mDeviceState = new RequiredParam<>();
        private final RequiredParam<String> mChipId = new RequiredParam<>();

        public DeviceStatus.Builder setDeviceState(int deviceState) {
            mDeviceState.set(deviceState);
            return this;
        }

        public DeviceStatus.Builder setChipId(String chipId) {
            mChipId.set(chipId);
            return this;
        }

        public DeviceStatus build() {
            return new DeviceStatus(
                    mDeviceState.get(),
                    mChipId.get());
        }
    }
}
