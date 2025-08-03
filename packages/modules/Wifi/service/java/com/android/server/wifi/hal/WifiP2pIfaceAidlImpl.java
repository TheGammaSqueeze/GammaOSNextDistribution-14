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
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

/**
 * AIDL implementation of the IWifiP2pIface interface.
 */
public class WifiP2pIfaceAidlImpl implements IWifiP2pIface {
    private static final String TAG = "WifiP2pIfaceAidlImpl";
    private android.hardware.wifi.IWifiP2pIface mWifiP2pIface;
    private final Object mLock = new Object();
    private String mIfaceName;

    public WifiP2pIfaceAidlImpl(@NonNull android.hardware.wifi.IWifiP2pIface p2pIface) {
        mWifiP2pIface = p2pIface;
    }

    /**
     * See comments for {@link com.android.server.wifi.hal.IWifiP2pIface#getName()}
     */
    @Override
    @Nullable
    public String getName() {
        final String methodStr = "getName";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return null;
            if (mIfaceName != null) return mIfaceName;
            try {
                String ifaceName = mWifiP2pIface.getName();
                mIfaceName = ifaceName;
                return mIfaceName;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    private boolean checkIfaceAndLogFailure(String methodStr) {
        if (mWifiP2pIface == null) {
            Log.e(TAG, "Unable to call " + methodStr + " because iface is null.");
            return false;
        }
        return true;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        mWifiP2pIface = null;
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with service-specific exception: " + e);
    }
}
