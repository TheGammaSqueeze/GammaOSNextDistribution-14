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
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.net.MacAddress;
import android.os.RemoteException;
import android.util.Log;

import com.android.server.wifi.util.GeneralUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * HIDL implementation of the IWifiApIface interface.
 */
public class WifiApIfaceHidlImpl implements IWifiApIface {
    private static final String TAG = "WifiApIfaceHidlImpl";
    private android.hardware.wifi.V1_0.IWifiApIface mWifiApIface;
    private String mIfaceName;

    public WifiApIfaceHidlImpl(@NonNull android.hardware.wifi.V1_0.IWifiApIface apIface) {
        mWifiApIface = apIface;
    }

    /**
     * See comments for {@link IWifiApIface#getName()}
     */
    public String getName() {
        final String methodStr = "getName";
        return validateAndCall(methodStr, null,
                () -> getNameInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiApIface#getBridgedInstances()}
     */
    public List<String> getBridgedInstances() {
        final String methodStr = "getBridgedInstances";
        return validateAndCall(methodStr, null,
                () -> getBridgedInstancesInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiApIface#getFactoryMacAddress()}
     */
    public MacAddress getFactoryMacAddress() {
        final String methodStr = "getFactoryMacAddress";
        return validateAndCall(methodStr, null,
                () -> getFactoryMacAddressInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiApIface#setCountryCode(byte[])}
     */
    public boolean setCountryCode(byte[] countryCode) {
        final String methodStr = "setCountryCode";
        return validateAndCall(methodStr, false,
                () -> setCountryCodeInternal(methodStr, countryCode));
    }

    /**
     * See comments for {@link IWifiApIface#resetToFactoryMacAddress()}
     *
     * Note: If HAL < 1.5, will only reset the MAC address for this interface.
     */
    public boolean resetToFactoryMacAddress() {
        final String methodStr = "resetToFactoryMacAddress";
        return validateAndCall(methodStr, false,
                () -> resetToFactoryMacAddressInternal(methodStr));
    }

    /**
     * See comments for {@link IWifiApIface#isSetMacAddressSupported()}
     */
    public boolean isSetMacAddressSupported() {
        if (mWifiApIface == null) return false;
        return getWifiApIfaceV1_4Mockable() != null;
    }

    /**
     * See comments for {@link IWifiApIface#setMacAddress(MacAddress)}
     */
    public boolean setMacAddress(MacAddress mac) {
        final String methodStr = "setMacAddress";
        return validateAndCall(methodStr, false,
                () -> setMacAddressInternal(methodStr, mac));
    }


    // Internal Implementations

    private String getNameInternal(String methodStr) {
        if (mIfaceName != null) return mIfaceName;
        GeneralUtil.Mutable<String> nameResp = new GeneralUtil.Mutable<>();
        try {
            mWifiApIface.getName((WifiStatus status, String name) -> {
                if (isOk(status, methodStr)) {
                    nameResp.value = name;
                    mIfaceName = name;
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return nameResp.value;
    }

    private List<String> getBridgedInstancesInternal(String methodStr) {
        GeneralUtil.Mutable<List<String>> instancesResp = new GeneralUtil.Mutable<>();
        try {
            android.hardware.wifi.V1_5.IWifiApIface ap15 = getWifiApIfaceV1_5Mockable();
            if (ap15 == null) return null;
            ap15.getBridgedInstances((status, instances) -> {
                if (isOk(status, methodStr)) {
                    instancesResp.value = new ArrayList<>(instances);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return instancesResp.value;
    }

    private MacAddress getFactoryMacAddressInternal(String methodStr) {
        GeneralUtil.Mutable<MacAddress> macResp = new GeneralUtil.Mutable<>();
        try {
            android.hardware.wifi.V1_4.IWifiApIface ap14 = getWifiApIfaceV1_4Mockable();
            if (ap14 == null) return null;
            ap14.getFactoryMacAddress((status, macBytes) -> {
                if (isOk(status, methodStr)) {
                    macResp.value = MacAddress.fromBytes(macBytes);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return macResp.value;
    }

    private boolean setCountryCodeInternal(String methodStr, byte[] countryCode) {
        try {
            WifiStatus status = mWifiApIface.setCountryCode(countryCode);
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean resetToFactoryMacAddressInternal(String methodStr) {
        try {
            android.hardware.wifi.V1_5.IWifiApIface ap15 = getWifiApIfaceV1_5Mockable();
            if (ap15 == null) {
                MacAddress mac = getFactoryMacAddress();
                return mac != null && setMacAddress(mac);
            }
            WifiStatus status = ap15.resetToFactoryMacAddress();
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean setMacAddressInternal(String methodStr, MacAddress mac) {
        try {
            android.hardware.wifi.V1_4.IWifiApIface ap14 = getWifiApIfaceV1_4Mockable();
            if (ap14 == null) return false;
            WifiStatus status = ap14.setMacAddress(mac.toByteArray());
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }


    // Helper Functions

    protected android.hardware.wifi.V1_4.IWifiApIface getWifiApIfaceV1_4Mockable() {
        return android.hardware.wifi.V1_4.IWifiApIface.castFrom(mWifiApIface);
    }

    protected android.hardware.wifi.V1_5.IWifiApIface getWifiApIfaceV1_5Mockable() {
        return android.hardware.wifi.V1_5.IWifiApIface.castFrom(mWifiApIface);
    }

    private boolean isOk(WifiStatus status, String methodStr) {
        if (status.code == WifiStatusCode.SUCCESS) return true;
        Log.e(TAG, methodStr + " failed with status: " + status);
        return false;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
        mWifiApIface = null;
    }

    private <T> T validateAndCall(String methodStr, T defaultVal, @NonNull Supplier<T> supplier) {
        if (mWifiApIface == null) {
            Log.wtf(TAG, "Cannot call " + methodStr + " because mWifiApIface is null");
            return defaultVal;
        }
        return supplier.get();
    }
}
