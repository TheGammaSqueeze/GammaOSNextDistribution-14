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
import android.os.RemoteException;
import android.util.Log;

import com.android.server.wifi.util.GeneralUtil.Mutable;

/**
 * HIDL implementation of the IWifiP2pIface interface.
 */
public class WifiP2pIfaceHidlImpl implements IWifiP2pIface {
    private static final String TAG = "WifiP2pIfaceHidlImpl";
    private android.hardware.wifi.V1_0.IWifiP2pIface mWifiP2pIface;
    private String mIfaceName;

    public WifiP2pIfaceHidlImpl(@NonNull android.hardware.wifi.V1_0.IWifiP2pIface p2pIface) {
        mWifiP2pIface = p2pIface;
    }

    /**
     * See comments for {@link com.android.server.wifi.hal.IWifiP2pIface#getName()}
     */
    public String getName() {
        final String methodStr = "getName";
        if (mWifiP2pIface == null) {
            handleNullIface(methodStr);
            return null;
        }
        if (mIfaceName != null) return mIfaceName;
        Mutable<String> nameResp = new Mutable<>();
        try {
            mWifiP2pIface.getName((WifiStatus status, String name) -> {
                if (isOk(status, methodStr)) {
                    nameResp.value = name;
                    mIfaceName = name;
                }
            });
        } catch (RemoteException e) {
            Log.e(TAG, "Exception in " + methodStr + ": " + e);
        }
        return nameResp.value;
    }

    private boolean isOk(WifiStatus status, String methodStr) {
        if (status.code == WifiStatusCode.SUCCESS) return true;
        Log.e(TAG, methodStr + " failed with status: " + status);
        return false;
    }

    private void handleNullIface(String methodStr) {
        Log.e(TAG, "Cannot call " + methodStr + " because mWifiP2pIface is null");
    }
}
