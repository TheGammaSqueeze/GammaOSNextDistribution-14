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

package com.android.server.wifi.mockwifi;

import android.hardware.wifi.supplicant.ISupplicant;
import android.hardware.wifi.supplicant.ISupplicantStaIface;
import android.os.IBinder;
import android.util.Log;

import com.android.server.wifi.WifiMonitor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

 /**
 * Mock Supplicant
 */
public class MockSupplicantManager {
    private static final String TAG = "MockSupplicantManager";
    private ISupplicant mISupplicant;
    private final WifiMonitor mWifiMonitor;
    private Set<String> mConfiguredMethodSet = new HashSet<>();
    private Map<String, ISupplicantStaIface> mMockISupplicantStaIfaces = new HashMap<>();

    public MockSupplicantManager(IBinder supplicantBinder, WifiMonitor wifiMonitor) {
        mWifiMonitor = wifiMonitor;
        mISupplicant = ISupplicant.Stub.asInterface(supplicantBinder);
        for (String ifaceName : mWifiMonitor.getMonitoredIfaceNames()) {
            Log.i(TAG, "Mock setupInterfaceForSupplicant for iface: " + ifaceName);
            try {
                mMockISupplicantStaIfaces.put(ifaceName, mISupplicant.addStaInterface(ifaceName));
            } catch (Exception e) {
                Log.e(TAG, "Failed to create ISupplicantStaIface due to exception - " + e);
            }
        }
    }

    /**
     * Return mocked ISupplicantStaIface.
     */
    public ISupplicantStaIface getMockSupplicantStaIface(String ifaceName) {
        return mMockISupplicantStaIfaces.get(ifaceName);
    }

    /**
     * Reset mocked methods.
     */
    public void resetMockedMethods() {
        mConfiguredMethodSet.clear();
    }

    /**
     * Adds mocked method
     *
     * @param method the method name is updated
     */
    public void addMockedMethod(String method) {
        // TODO: b/315088283 - Need to handle same method name from different aidl.
        mConfiguredMethodSet.add(method);
    }

    /**
     * Whether or not the method is mocked. (i.e. The framework should call this mocked method)
     *
     * @param method the method name.
     */
    public boolean isMethodConfigured(String method) {
        return mConfiguredMethodSet.contains(method);
    }
}
