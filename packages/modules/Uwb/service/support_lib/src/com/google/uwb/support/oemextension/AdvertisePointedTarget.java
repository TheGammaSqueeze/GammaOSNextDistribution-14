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
import android.uwb.UwbAddress;
import android.uwb.UwbManager;

import androidx.annotation.Nullable;

import com.google.uwb.support.base.RequiredParam;

import java.util.Objects;

/**
 * Check pointed target for oem extension callback
 *
 * <p> This is passed as a bundle to oem extension API
 * {@link UwbManager.UwbOemExtensionCallback#onCheckPointedTarget(PersistableBundle)}
 */
public class AdvertisePointedTarget {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private final UwbAddress mMacAddress;
    private final boolean mAdvertisePointingResult;
    public static final String KEY_BUNDLE_VERSION = "bundle_version";
    public static final String MAC_ADDRESS = "mac_address";
    public static final String ADVERTISE_POINTING_RESULT = "advertise_pointing_result";


    public AdvertisePointedTarget(UwbAddress macAddress, boolean advertisePointingResult) {
        mMacAddress = macAddress;
        mAdvertisePointingResult = advertisePointingResult;
    }

    public static int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public UwbAddress getMacAddress() {
        return mMacAddress;
    }

    public boolean isAdvertisePointingResult() {
        return mAdvertisePointingResult;
    }

    @Nullable
    private static int[] byteArrayToIntArray(@Nullable byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        int[] values = new int[bytes.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (bytes[i]);
        }
        return values;
    }

    @Nullable
    private static byte[] intArrayToByteArray(@Nullable int[] values) {
        if (values == null) {
            return null;
        }
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    public PersistableBundle toBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_BUNDLE_VERSION, getBundleVersion());
        bundle.putIntArray(MAC_ADDRESS, byteArrayToIntArray(mMacAddress.toBytes()));
        bundle.putBoolean(ADVERTISE_POINTING_RESULT, mAdvertisePointingResult);
        return bundle;
    }

    public static AdvertisePointedTarget fromBundle(PersistableBundle bundle) {
        switch (bundle.getInt(KEY_BUNDLE_VERSION)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);
            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static AdvertisePointedTarget parseVersion1(PersistableBundle bundle) {
        return new Builder()
                .setMacAddress(
                        Objects.requireNonNull(
                                intArrayToByteArray(bundle.getIntArray(MAC_ADDRESS))))
                .setAdvertisePointingResult(bundle.getBoolean(ADVERTISE_POINTING_RESULT))
                .build();
    }

    /** Builder */
    public static class Builder {
        private final RequiredParam<UwbAddress> mMacAddress = new RequiredParam<>();
        private final RequiredParam<Boolean> mAdvertisePointingResult = new RequiredParam<>();

        public AdvertisePointedTarget.Builder setMacAddress(byte[] macAddress) {
            mMacAddress.set(UwbAddress.fromBytes(macAddress));
            return this;
        }

        public AdvertisePointedTarget.Builder setAdvertisePointingResult(
                boolean advertisePointingResult) {
            mAdvertisePointingResult.set(advertisePointingResult);
            return this;
        }

        public AdvertisePointedTarget build() {
            return new AdvertisePointedTarget(
                    mMacAddress.get(),
                    mAdvertisePointingResult.get()
            );
        }
    }
}
