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

package com.android.server.wifi.hal;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.MacAddress;
import android.util.Log;

import com.android.server.wifi.util.NativeUtil;

import java.util.List;
import java.util.function.Supplier;

/**
 * Wrapper around a WifiApIface.
 * May be initialized using a HIDL or AIDL WifiApIface.
 */
public class WifiApIface implements WifiHal.WifiInterface {
    private static final String TAG = "WifiApIface";
    private final IWifiApIface mWifiApIface;

    public WifiApIface(@NonNull android.hardware.wifi.V1_0.IWifiApIface apIface) {
        mWifiApIface = createWifiApIfaceHidlImplMockable(apIface);
    }

    public WifiApIface(@NonNull android.hardware.wifi.IWifiApIface apIface) {
        mWifiApIface = createWifiApIfaceAidlImplMockable(apIface);
    }

    protected WifiApIfaceHidlImpl createWifiApIfaceHidlImplMockable(
            android.hardware.wifi.V1_0.IWifiApIface apIface) {
        return new WifiApIfaceHidlImpl(apIface);
    }

    protected WifiApIfaceAidlImpl createWifiApIfaceAidlImplMockable(
            android.hardware.wifi.IWifiApIface apIface) {
        return new WifiApIfaceAidlImpl(apIface);
    }

    private <T> T validateAndCall(String methodStr, T defaultVal, @NonNull Supplier<T> supplier) {
        if (mWifiApIface == null) {
            Log.wtf(TAG, "Cannot call " + methodStr + " because mWifiApIface is null");
            return defaultVal;
        }
        return supplier.get();
    }

    /**
     * See comments for {@link IWifiApIface#getName()}
     */
    @Override
    @Nullable
    public String getName() {
        return validateAndCall("getName", null,
                () -> mWifiApIface.getName());
    }

    /**
     * See comments for {@link IWifiApIface#getBridgedInstances()}
     */
    public List<String> getBridgedInstances() {
        return validateAndCall("getBridgedInstances", null,
                () -> mWifiApIface.getBridgedInstances());
    }

    /**
     * See comments for {@link IWifiApIface#getFactoryMacAddress()}
     */
    public MacAddress getFactoryMacAddress() {
        return validateAndCall("getFactoryMacAddress", null,
                () -> mWifiApIface.getFactoryMacAddress());
    }

    /**
     * See comments for {@link IWifiApIface#setCountryCode(byte[])}
     */
    public boolean setCountryCode(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) {
            Log.e(TAG, "Invalid country code " + countryCode);
            return false;
        }
        try {
            final byte[] code = NativeUtil.stringToByteArray(countryCode);
            return validateAndCall("setCountryCode", false,
                    () -> mWifiApIface.setCountryCode(code));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid country code " + countryCode + ", error: " + e);
            return false;
        }

    }

    /**
     * See comments for {@link IWifiApIface#resetToFactoryMacAddress()}
     */
    public boolean resetToFactoryMacAddress() {
        return validateAndCall("resetToFactoryMacAddress", false,
                () -> mWifiApIface.resetToFactoryMacAddress());
    }

    /**
     * See comments for {@link IWifiApIface#isSetMacAddressSupported()}
     */
    public boolean isSetMacAddressSupported() {
        return validateAndCall("isSetMacAddressSupported", false,
                () -> mWifiApIface.isSetMacAddressSupported());
    }

    /**
     * See comments for {@link IWifiApIface#setMacAddress(MacAddress)}
     */
    public boolean setMacAddress(MacAddress mac) {
        if (mac == null) {
            Log.e(TAG, "setMacAddress received a null MAC address");
            return false;
        }
        return validateAndCall("setMacAddress", false,
                () -> mWifiApIface.setMacAddress(mac));
    }
}
