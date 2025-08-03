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

package android.wifi.mockwifi.nl80211;

import android.net.wifi.nl80211.IPnoScanEvent;
import android.net.wifi.nl80211.IScanEvent;
import android.net.wifi.nl80211.IWifiScannerImpl;
import android.net.wifi.nl80211.NativeScanResult;
import android.net.wifi.nl80211.PnoSettings;
import android.net.wifi.nl80211.SingleScanSettings;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Log;

import java.util.Collections;
import java.util.Set;

public class IWifiScannerImp extends IWifiScannerImpl.Stub {
    private static final String TAG = "IWifiScannerImp";

    private String mIfaceName;
    private WifiScannerInterfaceMock mWifiScannerInterfaceMock;
    private IPnoScanEvent mIPnoScanEvent;
    private IScanEvent mScanEventHandler;

    public interface WifiScannerInterfaceMock {
        default NativeScanResult[] getScanResults() {
            return null;
        }
        default NativeScanResult[] getPnoScanResults() {
            return null;
        }
        /**
        * Configures a start Pno scan interface.
        */
        default boolean startPnoScan(PnoSettings pnoSettings) {
            return false;
        }
    }

    public IWifiScannerImp(String ifaceName) {
        mIfaceName = ifaceName;
    }

    private boolean isMethodOverridden(WifiScannerInterfaceMock wifiScannerInterfaceMock,
            String methodName) throws NoSuchMethodException {
        if (methodName.equals("startPnoScan")) {
            return wifiScannerInterfaceMock.getClass().getMethod(
                    methodName, PnoSettings.class).getDeclaringClass().equals(
                    WifiScannerInterfaceMock.class);
        }
        if (methodName.equals("subscribePnoScanEvents")) {
            return !wifiScannerInterfaceMock.getClass().getMethod(
                    methodName, IPnoScanEvent.class).getDeclaringClass().equals(
                    WifiScannerInterfaceMock.class);
        }
        return !wifiScannerInterfaceMock.getClass().getMethod(
                methodName).getDeclaringClass().equals(WifiScannerInterfaceMock.class);
    }

    /**
    *  Overridden method check.
    */
    public Set<String> setWifiScannerInterfaceMock(
            WifiScannerInterfaceMock wifiScannerInterfaceMock) {
        if (wifiScannerInterfaceMock == null) {
            return Collections.emptySet();
        }
        Set<String> overriddenMethods = new ArraySet<>();
        try {
            if (isMethodOverridden(wifiScannerInterfaceMock, "getScanResults")) {
                overriddenMethods.add("getScanResults");
            }
            if (isMethodOverridden(wifiScannerInterfaceMock, "getPnoScanResults")) {
                overriddenMethods.add("getPnoScanResults");
            }
            if (isMethodOverridden(wifiScannerInterfaceMock, "startPnoScan")) {
                overriddenMethods.add("startPnoScan");
            }
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Reflection error: " + e);
            return Collections.emptySet();
        }
        mWifiScannerInterfaceMock = wifiScannerInterfaceMock;
        return overriddenMethods;
    }

    /**
     * Trigger the scan ready event to force frameworks to get scan result again.
     * Otherwise the mocked scan result may not work because the frameworks keep use cache data
     * since there is no scan ready event.
     */
    public void mockScanResultReadyEvent() {
        try {
            if (mScanEventHandler != null) {
                mScanEventHandler.OnScanResultReady();
            }
        } catch (RemoteException re) {
            Log.e(TAG, "RemoteException when calling OnScanResultRead" + re);
        }
    }
    // Supported methods in IWifiScannerImpl.aidl
    @Override
    public NativeScanResult[] getScanResults() {
        Log.i(TAG, "getScanResults");
        if (mWifiScannerInterfaceMock == null) {
            Log.e(TAG, "mWifiScannerInterfaceMock: null!");
            return null;
        }
        return mWifiScannerInterfaceMock.getScanResults();
    }

    @Override
    public NativeScanResult[] getPnoScanResults() {
        Log.i(TAG, "getPnoScanResults");
        if (mWifiScannerInterfaceMock == null) {
            Log.e(TAG, "mWifiScannerInterfaceMock: null!");
            return null;
        }
        return mWifiScannerInterfaceMock.getPnoScanResults();
    }

    @Override
    public boolean scan(SingleScanSettings scanSettings) {
        Log.i(TAG, "scan");
        // TODO: Mock it when we have a use (test) case.
        return true;
    }

    @Override
    public int scanRequest(SingleScanSettings scanSettings) {
        Log.i(TAG, "scanRequest");
        // TODO: Mock it when we have a use (test) case.
        return 0;
    }

    @Override
    public void subscribeScanEvents(IScanEvent handler) {
        Log.i(TAG, "subscribeScanEvents");
        mScanEventHandler = handler;
    }

    @Override
    public void unsubscribeScanEvents() {
        Log.i(TAG, "unsubscribeScanEvents");
        // TODO: Mock it when we have a use (test) case.
    }

    @Override
    public void subscribePnoScanEvents(IPnoScanEvent handler) {
        Log.i(TAG, "subscribePnoScanEvents");
        this.mIPnoScanEvent = handler;
    }

    @Override
    public void unsubscribePnoScanEvents() {
        Log.i(TAG, "unsubscribePnoScanEvents");
        // TODO: Mock it when we have a use (test) case.
    }

    @Override
    public boolean startPnoScan(PnoSettings pnoSettings) {
        Log.i(TAG, "startPnoScan");
        if (mWifiScannerInterfaceMock == null || mIPnoScanEvent == null) {
            Log.e(TAG, "startPnoScan: false mock!");
            return false;
        }
        try {
            mIPnoScanEvent.OnPnoNetworkFound();
        } catch (RemoteException e) {
            Log.d(TAG, "Failed to start pno scan due to remote exception");
        }
        return true;
    }

    @Override
    public boolean stopPnoScan() {
        Log.i(TAG, "stopPnoScan");
        // TODO: Mock it when we have a use (test) case.
        return false;
    }

    @Override
    public void abortScan() {
        Log.i(TAG, "abortScan");
        // TODO: Mock it when we have a use (test) case.
    }

    @Override
    public int getMaxSsidsPerScan() {
        Log.i(TAG, "getMaxSsidsPerScan");
        // TODO: Mock it when we have a use (test) case.
        return 0;
    }
}
