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

package android.wifi.mockwifi;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.wifi.mockwifi.nl80211.IClientInterfaceImp;
import android.wifi.mockwifi.nl80211.IWifiScannerImp;
import android.wifi.mockwifi.nl80211.WifiNL80211ManagerImp;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MockWifiModemService extends Service {

    private static final String TAG = "MockWifiModemService";
    public static final int TEST_TIMEOUT_MS = 30000;

    public static final int LATCH_MOCK_WIFI_MODEM_SERVICE_READY = 0;
    public static final int LATCH_WIFI_INTERFACES_READY = 1;
    public static final int LATCH_MAX = 2;

    public static final String METHOD_IDENTIFIER = ",";
    public static final String CLASS_IDENTIFIER = "-";
    private static final int NUM_MOCKED_INTERFACES = 1; // The number of HAL, now only support
                                                        // nl80211 HAL
    public static final String NL80211_INTERFACE_NAME = "android.wifi.mockwifimodem.nl80211";

    private static CountDownLatch[] sLatches;
    private Object mLock = new Object();;
    private static Context sContext;
    private LocalBinder mBinder;

    private static WifiNL80211ManagerImp sWifiNL80211ManagerImp;

    // For local access to this Service.
    class LocalBinder extends Binder {
        MockWifiModemService getService() {
            return MockWifiModemService.this;
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Mock Wifi Modem Service Created");
        sContext = getBaseContext();
        sLatches = new CountDownLatch[LATCH_MAX];
        for (int i = 0; i < LATCH_MAX; i++) {
            if (i == LATCH_WIFI_INTERFACES_READY) {
                sLatches[i] = new CountDownLatch(NUM_MOCKED_INTERFACES); // make sure all HAL
                                                                         // simulation is ready
            } else {
                sLatches[i] = new CountDownLatch(1);
            }
        }
        mBinder = new LocalBinder();
        sWifiNL80211ManagerImp = new WifiNL80211ManagerImp(sContext);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (NL80211_INTERFACE_NAME.equals(intent.getAction())) {
            Log.i(TAG, "onBind-NL80211");
            countDownLatch(LATCH_WIFI_INTERFACES_READY);
            return sWifiNL80211ManagerImp;
        }
        countDownLatch(LATCH_MOCK_WIFI_MODEM_SERVICE_READY);
        Log.i(TAG, "onBind-Local");
        return mBinder;
    }

    public void countDownLatch(int latchIndex) {
        if (latchIndex < 0 || latchIndex >= sLatches.length) {
            Log.e(TAG, "invalid latch index: " + latchIndex);
            return;
        }
        synchronized (mLock) {
            sLatches[latchIndex].countDown();
        }
    }

    public boolean waitForLatchCountdown(int latchIndex) {
        return waitForLatchCountdown(latchIndex, TEST_TIMEOUT_MS);
    }

    public boolean waitForLatchCountdown(int latchIndex, long waitMs) {
        if (latchIndex < 0 || latchIndex >= sLatches.length) {
            Log.e(TAG, "invalid latch index: " + latchIndex);
            return false;
        }
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    /**
     * Configures a mock client interface.
     */
    public boolean configureClientInterfaceMock(String ifaceName,
            IClientInterfaceImp.ClientInterfaceMock clientInterfaceMock) {
        if (sWifiNL80211ManagerImp == null) {
            return false;
        }
        return sWifiNL80211ManagerImp.configureClientInterfaceMock(ifaceName, clientInterfaceMock);
    }

    /**
     * Configures a mock Wifi scanner interface.
     */
    public boolean configureWifiScannerInterfaceMock(String ifaceName,
            IWifiScannerImp.WifiScannerInterfaceMock wifiScannerInterfaceMock) {
        if (sWifiNL80211ManagerImp == null) {
            return false;
        }
        return sWifiNL80211ManagerImp.configureWifiScannerInterfaceMock(ifaceName,
                wifiScannerInterfaceMock);
    }

    /**
     * Gets all configured methods from all mocked HALs.
     */
    public String getAllConfiguredMethods() {
        // Get configured methods from all mocked HALs. (Now only supports WifiNL80211ManagerImp).
        return sWifiNL80211ManagerImp.getConfiguredMethods();
    }

    public WifiNL80211ManagerImp getWifiNL80211ManagerImp() {
        return sWifiNL80211ManagerImp;
    }
}
