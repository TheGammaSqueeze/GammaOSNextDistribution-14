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

import android.content.Context;
import android.net.wifi.nl80211.DeviceWiphyCapabilities;
import android.net.wifi.nl80211.IApInterface;
import android.net.wifi.nl80211.IClientInterface;
import android.net.wifi.nl80211.IInterfaceEventCallback;
import android.net.wifi.nl80211.IWificond;
import android.net.wifi.nl80211.IWificondEventCallback;
import android.os.IBinder;
import android.util.Log;
import android.wifi.mockwifi.MockWifiModemService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WifiNL80211ManagerImp extends IWificond.Stub {
    private static final String TAG = "WifiNL80211ManagerImp";

    private static Context sContext;
    Set<String> mConfiguredMethodSet;
    private HashMap<String, IClientInterfaceImp> mMockIClientInterfaces = new HashMap<>();
    private HashMap<String, IWifiScannerImp> mMockIWifiScanners = new HashMap<>();

    public WifiNL80211ManagerImp(Context context) {
        sContext = context;
        mConfiguredMethodSet = new HashSet<>();
    }

    @Override
    public IApInterface createApInterface(String ifaceName) {
        // TODO: Mock it when we have a use (test) case.
        return null;
    }

    @Override
    public IClientInterface createClientInterface(String ifaceName) {
        IClientInterfaceImp mockIClientInterface = new IClientInterfaceImp(ifaceName);
        IWifiScannerImp mockIWifiScanner = (IWifiScannerImp) mockIClientInterface
                                            .getWifiScannerImpl();
        mMockIClientInterfaces.put(ifaceName, mockIClientInterface);
        mMockIWifiScanners.put(ifaceName, mockIWifiScanner);
        return mockIClientInterface;
    }

    @Override
    public boolean tearDownApInterface(String ifaceName) {
        // TODO: Mock it when we have a use (test) case.
        return true;
    }

    @Override
    public boolean tearDownClientInterface(String ifaceName) {
        return mMockIClientInterfaces.remove(ifaceName) != null;
    }

    @Override
    public void tearDownInterfaces() {
        // TODO: Mock it when we have a use (test) case.
    }

    @Override
    public int[] getAvailable2gChannels() {
        // TODO: Mock it when we have a use (test) case.
        return null;
    }

    @Override
    public int[] getAvailable5gNonDFSChannels() {
        // TODO: Mock it when we have a use (test) case.
        return null;
    }

    @Override
    public int[] getAvailableDFSChannels() {
        // TODO: Mock it when we have a use (test) case.
        return null;
    }

    @Override
    public int[] getAvailable6gChannels() {
        // TODO: Mock it when we have a use (test) case.
        return null;
    }

    @Override
    public int[] getAvailable60gChannels() {
        // TODO: Mock it when we have a use (test) case.
        return null;
    }

    @Override
    public void registerWificondEventCallback(IWificondEventCallback callback) {
        // TODO: Mock it when we have a use (test) case.
    }

    @Override
    public void unregisterWificondEventCallback(IWificondEventCallback callback) {
        // TODO: Mock it when we have a use (test) case.
    }

    @Override
    public DeviceWiphyCapabilities getDeviceWiphyCapabilities(String ifaceName) {
        // TODO: Mock it when we have a use (test) case.
        return null;
    }

    @Override
    public void notifyCountryCodeChanged() {
        // TODO: Mock it when we have a use (test) case.
    }

    // CHECKSTYLE:OFF Generated code
    @Override
    public void UnregisterCallback(IInterfaceEventCallback callback) {
        // TODO: Mock it when we have a use (test) case.
    }

    @Override
    public void RegisterCallback(IInterfaceEventCallback callback) {
        // TODO: Mock it when we have a use (test) case.
    }

    @Override
    public List<IBinder> GetClientInterfaces() {
        // TODO: Mock it when we have a use (test) case.
        return null;
    }

    @Override
    public List<IBinder> GetApInterfaces() {
        // TODO: Mock it when we have a use (test) case.
        return null;
    }
    // CHECKSTYLE:ON Generated code

    public boolean configureClientInterfaceMock(String ifaceName,
            IClientInterfaceImp.ClientInterfaceMock clientInterfaceMock) {
        if (mMockIClientInterfaces ==  null) return false;
        IClientInterfaceImp clientInterface = mMockIClientInterfaces.get(ifaceName);
        if (clientInterface == null) return false;
        Set<String> configuredMethods = clientInterface.setClientInterfaceMock(clientInterfaceMock);
        if (configuredMethods.isEmpty()) {
            Log.e(TAG, "No methods overridden in the mock ClientInterface!?");
            return false;
        }
        mConfiguredMethodSet.addAll(configuredMethods);
        return true;
    }

    /**
     * Configures a mock Wifi scanner interface.
     */
    public boolean configureWifiScannerInterfaceMock(String ifaceName,
            IWifiScannerImp.WifiScannerInterfaceMock wifiScannerInterfaceMock) {
        if (mMockIWifiScanners == null) {
            return false;
        }
        IWifiScannerImp wifiscanner = mMockIWifiScanners.get(ifaceName);
        if (wifiscanner == null || wifiScannerInterfaceMock == null) {
            Log.e(TAG, "WifiScanner interface mock: Null!");
            return false;
        }
        Set<String> configuredMethods = wifiscanner.setWifiScannerInterfaceMock(
                                        wifiScannerInterfaceMock);
        if (configuredMethods.isEmpty()) {
            Log.e(TAG, "No methods overridden in the mock WifiScanner!?");
            return false;
        }
        mConfiguredMethodSet.addAll(configuredMethods);
        return true;
    }

    public String getConfiguredMethods() {
        StringBuilder sbuf = new StringBuilder();
        for (String methodName : mConfiguredMethodSet) {
            sbuf.append(TAG + MockWifiModemService.CLASS_IDENTIFIER + methodName
                    + MockWifiModemService.METHOD_IDENTIFIER);
        }
        return sbuf.toString();
    }

    /**
     * Trigger the scan ready event to force frameworks to get scan result again.
     * Otherwise the mocked scan result may not work because the frameworks keep use cache data
     * since there is no scan ready event.
     */
    public void mockScanResultReadyEvent(String ifaceName) {
        if (mMockIWifiScanners == null || ifaceName == null) {
            return;
        }
        IWifiScannerImp wifiscanner = mMockIWifiScanners.get(ifaceName);
        if (wifiscanner == null) {
            Log.e(TAG, "WifiScanner interface mock: Null!");
            return;
        }
        wifiscanner.mockScanResultReadyEvent();
    }
}
