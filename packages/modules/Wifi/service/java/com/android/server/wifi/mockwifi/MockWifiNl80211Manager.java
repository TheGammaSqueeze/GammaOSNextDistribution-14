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

package com.android.server.wifi.mockwifi;

import android.content.Context;
import android.net.wifi.WifiScanner;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.os.IBinder;
import android.util.Log;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiMonitor;

import java.util.HashSet;
import java.util.Set;

/**
 * Mocked WifiNl80211Manager
 */
public class MockWifiNl80211Manager {
    private static final String TAG = "MockWifiNl80211Manager";

    private Context mContext;
    private WifiNl80211Manager mMockWifiNl80211Manager;
    private final WifiMonitor mWifiMonitor;
    private Set<String> mConfiguredMethodSet = new HashSet<>();

    public MockWifiNl80211Manager(IBinder wificondBinder, Context context,
            WifiMonitor wifiMonitor) {
        mContext = context;
        mWifiMonitor = wifiMonitor;
        if (SdkLevel.isAtLeastU()) {
            mMockWifiNl80211Manager = new WifiNl80211Manager(mContext, wificondBinder);
            for (String ifaceName : mWifiMonitor.getMonitoredIfaceNames()) {
                Log.i(TAG, "Mock setupInterfaceForClientMode for iface: " + ifaceName);
                mMockWifiNl80211Manager.setupInterfaceForClientMode(ifaceName, Runnable::run,
                        new NormalScanEventCallback(ifaceName),
                        new PnoScanEventCallback(ifaceName));
            }
        }
    }

    public WifiNl80211Manager getWifiNl80211Manager() {
        return mMockWifiNl80211Manager;
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

    private class NormalScanEventCallback implements WifiNl80211Manager.ScanEventCallback {
        private String mIfaceName;

        NormalScanEventCallback(String ifaceName) {
            mIfaceName = ifaceName;
        }

        @Override
        public void onScanResultReady() {
            Log.d(TAG, "Scan result ready event " + mIfaceName);
            mWifiMonitor.broadcastScanResultEvent(mIfaceName);
        }

        @Override
        public void onScanFailed() {
            Log.d(TAG, "Scan failed event " + mIfaceName);
            mWifiMonitor.broadcastScanFailedEvent(mIfaceName, WifiScanner.REASON_UNSPECIFIED);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d(TAG, "Scan failed event: errorCode: " + errorCode);
            mWifiMonitor.broadcastScanFailedEvent(mIfaceName, errorCode);
        }
    }

    private class PnoScanEventCallback implements WifiNl80211Manager.ScanEventCallback {
        private String mIfaceName;

        PnoScanEventCallback(String ifaceName) {
            mIfaceName = ifaceName;
        }

        @Override
        public void onScanResultReady() {
            Log.d(TAG, "Pno scan result event");
            mWifiMonitor.broadcastPnoScanResultEvent(mIfaceName);
        }

        @Override
        public void onScanFailed() {
            Log.d(TAG, "Pno Scan failed event");
        }
    }
}
