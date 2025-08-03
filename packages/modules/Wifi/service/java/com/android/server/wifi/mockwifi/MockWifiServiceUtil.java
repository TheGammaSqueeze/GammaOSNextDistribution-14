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

import static android.os.UserHandle.CURRENT;

import android.annotation.IntDef;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.os.IBinder;
import android.util.Log;

import com.android.server.wifi.WifiMonitor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** This class provides wrapper APIs for binding interfaces to mock service. */
public class MockWifiServiceUtil {
    private static final String TAG = "MockWifiModemUtil";
    private static final String BIND_NL80211 = "android.wifi.mockwifimodem.nl80211";
    private static final String BIND_SUPPLICANT = "android.wifi.mockwifimodem.supplicant";

    @IntDef({MOCK_NL80211_SERVICE, MOCK_SUPPLICANT_SERVICE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface MockServiceType {}

    public static final int MOCK_NL80211_SERVICE = 0;
    public static final int MOCK_SUPPLICANT_SERVICE = 1;
    public static final int MIN_SERVICE_IDX = MOCK_NL80211_SERVICE;
    public static final int NUM_SERVICES = 2;
    public static final int BINDER_RETRY_MILLIS = 3 * 100;
    public static final int BINDER_MAX_RETRY = 3;

    public static final String METHOD_SEPARATOR = ",";
    public static final String CLASS_IDENTIFIER = "-";
    public static final String AIDL_METHOD_IDENTIFIER = "#";

    private static final String TAG_MOCK_NL80211 = "WifiNL80211ManagerImp";
    private static final String TAG_MOCK_SUPPLICANT = "ISupplicantImp";

    private Context mContext;
    private WifiMonitor mWifiMonitor;
    private String mServiceName;
    private String mPackageName;
    private MockWifiNl80211Manager mMockWifiNl80211Manager;
    private MockSupplicantManager mMockSupplicantManager;
    private IBinder mMockNl80211Binder;
    private ServiceConnection mMockNl80211ServiceConnection;
    private IBinder mMockSupplicantBinder;
    private ServiceConnection mMockSupplicantServiceConnection;

    public MockWifiServiceUtil(Context context, String serviceName, WifiMonitor wifiMonitor) {
        mContext = context.createContextAsUser(CURRENT, 0);
        mWifiMonitor = wifiMonitor;
        String[] componentInfo = serviceName.split("/", 2);
        mPackageName = componentInfo[0];
        mServiceName = componentInfo[1];
    }

    private class MockModemConnection implements ServiceConnection {
        private int mService;

        MockModemConnection(int module) {
            mService = module;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "Wifi mock Service " + getModuleName(mService) + "  - onServiceConnected");
            if (mService == MOCK_NL80211_SERVICE) {
                mMockNl80211Binder = binder;
                mMockWifiNl80211Manager = new MockWifiNl80211Manager(mMockNl80211Binder, mContext,
                        mWifiMonitor);
            }
            if (mService == MOCK_SUPPLICANT_SERVICE) {
                mMockSupplicantBinder = binder;
                mMockSupplicantManager = new MockSupplicantManager(mMockSupplicantBinder,
                        mWifiMonitor);
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Wifi mock Service " + getModuleName(mService)
                    + "  - onServiceDisconnected");
            if (mService == MOCK_NL80211_SERVICE) {
                mMockNl80211Binder = null;
                mMockWifiNl80211Manager = null;
            }
            if (mService == MOCK_SUPPLICANT_SERVICE) {
                mMockSupplicantBinder = null;
                mMockSupplicantManager = null;
            }
        }
    }

    private boolean bindModuleToMockModemService(
            String actionName, ServiceConnection serviceConnection) {
        boolean status = false;

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(mPackageName, mServiceName));
        intent.setAction(actionName);

        status = mContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        return status;
    }

    /**
     * Unbind of the provided service.
     */
    public void unbindMockModemService() {
        mContext.unbindService(mMockNl80211ServiceConnection);
        mContext.unbindService(mMockSupplicantServiceConnection);
    }

    /** waitForBinder */
    public IBinder getServiceBinder(@MockServiceType int service) {
        switch (service) {
            case MOCK_NL80211_SERVICE:
                return mMockNl80211Binder;
            case MOCK_SUPPLICANT_SERVICE:
                return mMockSupplicantBinder;
            default:
                return null;
        }
    }

    /** Binding interfaces with mock modem service */
    public void bindAllMockModemService() {
        for (int service = MIN_SERVICE_IDX; service < NUM_SERVICES; service++) {
            bindToMockModemService(service);
        }
    }

    /** bindToMockModemService */
    public void bindToMockModemService(@MockServiceType int service) {
        // Based on {@code service} to get each mocked HAL binder
        if (service == MOCK_NL80211_SERVICE) {
            mMockNl80211ServiceConnection = new MockModemConnection(MOCK_NL80211_SERVICE);

            boolean status =
                    bindModuleToMockModemService(BIND_NL80211, mMockNl80211ServiceConnection);
            if (!status) {
                Log.d(TAG, getModuleName(service) + " bind fail");
                mMockNl80211ServiceConnection = null;
            }
        }
        if (service == MOCK_SUPPLICANT_SERVICE) {
            mMockSupplicantServiceConnection = new MockModemConnection(MOCK_SUPPLICANT_SERVICE);
            boolean status =
                    bindModuleToMockModemService(BIND_SUPPLICANT, mMockSupplicantServiceConnection);
            if (!status) {
                Log.d(TAG, getModuleName(service) + " bind fail");
                mMockSupplicantServiceConnection = null;
            }
        }
    }

    public String getServiceName() {
        return mServiceName;
    }

    private ServiceConnection getServiceConnection(@MockServiceType int service) {
        switch (service) {
            case MOCK_NL80211_SERVICE:
                return mMockNl80211ServiceConnection;
            case MOCK_SUPPLICANT_SERVICE:
                return mMockSupplicantServiceConnection;
            default:
                return null;
        }
    }

    /**
     * Returns name of the provided service.
     */
    public String getModuleName(@MockServiceType int service) {
        switch (service) {
            case MOCK_NL80211_SERVICE:
                return "nl80211";
            case MOCK_SUPPLICANT_SERVICE:
                return "supplicant";
            default:
                return "none";
        }
    }

    /**
     * set mocked methods.
     *
     * @param methods mocked methods with format HAL-method, ...
     */
    public boolean setMockedMethods(String methods) {
        Log.i(TAG, "setMockedMethods - " + methods);
        if (methods == null) {
            return false;
        }
        if (mMockWifiNl80211Manager != null) {
            mMockWifiNl80211Manager.resetMockedMethods();
        }
        if (mMockSupplicantManager != null) {
            mMockSupplicantManager.resetMockedMethods();
        }
        String[] mockedMethods = methods.split(METHOD_SEPARATOR);
        for (String mockedMethod : mockedMethods) {
            String[] mockedMethodInfo = mockedMethod.split(CLASS_IDENTIFIER);
            if (mockedMethodInfo.length != 2) {
                return false;
            }
            String mockedClassName = mockedMethodInfo[0];
            String mockedMethodName = mockedMethodInfo[1];
            if (TAG_MOCK_NL80211.equals(mockedClassName) && mMockWifiNl80211Manager != null) {
                mMockWifiNl80211Manager.addMockedMethod(mockedMethodName);
            }
            if (TAG_MOCK_SUPPLICANT.equals(mockedClassName) && mMockSupplicantManager != null) {
                mMockSupplicantManager.addMockedMethod(mockedMethodName);
            }
        }
        return true;
    }

    public MockSupplicantManager getMockSupplicantManager() {
        return mMockSupplicantManager;
    }

    public MockWifiNl80211Manager getMockWifiNl80211Manager() {
        return mMockWifiNl80211Manager;
    }

    public WifiNl80211Manager getWifiNl80211Manager() {
        return mMockWifiNl80211Manager == null
                ? null : mMockWifiNl80211Manager.getWifiNl80211Manager();
    }

    /**
     * Get method configured status based on service.
     */
    public boolean isMethodConfigured(@MockServiceType int service, String methodName) {
        switch (service) {
            case MOCK_NL80211_SERVICE:
                return getWifiNl80211Manager() != null
                        && getMockWifiNl80211Manager().isMethodConfigured(methodName);
            case MOCK_SUPPLICANT_SERVICE:
                return mMockSupplicantManager != null
                        && getMockSupplicantManager().isMethodConfigured(methodName);
            default:
                return false;
        }
    }
}
