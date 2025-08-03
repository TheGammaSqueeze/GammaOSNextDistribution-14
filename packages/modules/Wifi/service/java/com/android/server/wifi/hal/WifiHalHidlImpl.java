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
import android.content.Context;
import android.hardware.wifi.V1_0.IWifi;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hidl.manager.V1_0.IServiceNotification;
import android.hidl.manager.V1_2.IServiceManager;
import android.os.IHwBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.SsidTranslator;
import com.android.server.wifi.util.GeneralUtil.Mutable;

import java.util.List;
import java.util.function.Supplier;

/**
 * HIDL implementation of the IWifiHal interface.
 */
public class WifiHalHidlImpl implements IWifiHal {
    private static final String TAG = "WifiHalHidlImpl";
    private IServiceManager mServiceManager;
    private android.hardware.wifi.V1_0.IWifi mWifi;
    private Context mContext;
    private SsidTranslator mSsidTranslator;
    private android.hardware.wifi.V1_0.IWifiEventCallback mHalCallback10;
    private android.hardware.wifi.V1_5.IWifiEventCallback mHalCallback15;
    private WifiHal.Callback mFrameworkCallback;
    private ServiceManagerDeathRecipient mServiceManagerDeathRecipient;
    private WifiDeathRecipient mWifiDeathRecipient;
    private WifiHal.DeathRecipient mFrameworkDeathRecipient;
    private boolean mIsVendorHalSupported;

    private final Object mLock = new Object();

    public WifiHalHidlImpl(@NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
        Log.i(TAG, "Creating the Wifi HAL using the HIDL implementation");
        mContext = context;
        mSsidTranslator = ssidTranslator;
        mServiceManagerDeathRecipient = new ServiceManagerDeathRecipient();
        mWifiDeathRecipient = new WifiDeathRecipient();
        mHalCallback10 = new WifiEventCallback();
        mHalCallback15 = new WifiEventCallbackV15();
    }

    /**
     * See comments for {@link IWifiHal#getChip(int)}
     */
    @Override
    @Nullable
    public WifiChip getChip(int chipId) {
        synchronized (mLock) {
            final String methodStr = "getChip";
            return validateAndCall(methodStr, null,
                    () -> getChipInternal(methodStr, chipId));
        }
    }

    /**
     * See comments for {@link IWifiHal#getChipIds()}
     */
    @Override
    @Nullable
    public List<Integer> getChipIds() {
        synchronized (mLock) {
            final String methodStr = "getChipIds";
            return validateAndCall(methodStr, null,
                    () -> getChipIdsInternal(methodStr));
        }
    }

    /**
     * See comments for {@link IWifiHal#registerEventCallback(WifiHal.Callback)}
     */
    @Override
    public boolean registerEventCallback(WifiHal.Callback callback) {
        synchronized (mLock) {
            final String methodStr = "registerEventCallback";
            return validateAndCall(methodStr, false,
                    () -> registerEventCallbackInternal(callback));
        }
    }

    /**
     * See comments for {@link IWifiHal#isInitializationComplete()}
     */
    @Override
    public boolean isInitializationComplete() {
        synchronized (mLock) {
            return mWifi != null;
        }
    }

    /**
     * See comments for {@link IWifiHal#isSupported()}
     */
    @Override
    public boolean isSupported() {
        synchronized (mLock) {
            return mIsVendorHalSupported;
        }
    }

    /**
     * See comments for {@link IWifiHal#start()}
     */
    @Override
    public @WifiHal.WifiStatusCode int start() {
        synchronized (mLock) {
            final String methodStr = "start";
            return validateAndCall(methodStr, WifiHal.WIFI_STATUS_ERROR_UNKNOWN,
                    () -> startInternal(methodStr));
        }
    }

    /**
     * See comments for {@link IWifiHal#isStarted()}
     */
    @Override
    public boolean isStarted() {
        synchronized (mLock) {
            final String methodStr = "isStarted";
            return validateAndCall(methodStr, false,
                    () -> isStartedInternal(methodStr));
        }
    }

    /**
     * See comments for {@link IWifiHal#stop()}
     */
    @Override
    public boolean stop() {
        synchronized (mLock) {
            final String methodStr = "stop";
            boolean result = validateAndCall(methodStr, false,
                    () -> stopInternal(methodStr));
            return result;
        }
    }

    /**
     * See comments for {@link IWifiHal#initialize(WifiHal.DeathRecipient)}
     */
    @Override
    public void initialize(WifiHal.DeathRecipient deathRecipient) {
        synchronized (mLock) {
            Log.i(TAG, "Initializing the WiFi HAL");
            mFrameworkDeathRecipient = deathRecipient;
            initServiceManagerIfNecessaryLocked();
            if (mIsVendorHalSupported) {
                initWifiIfNecessaryLocked();
            }
        }
    }

    /**
     * See comments for {@link IWifiHal#invalidate()}
     */
    public void invalidate() {
        synchronized (mLock) {
            mWifi = null;
        }
    }


    // Internal Implementations

    private WifiChip getChipInternal(String methodStr, int chipId) {
        Mutable<WifiChip> chipResp = new Mutable<>();
        try {
            mWifi.getChip(chipId, (status, chip) -> {
                if (isOk(status, methodStr)) {
                    chipResp.value = new WifiChip(chip, mContext, mSsidTranslator);
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return chipResp.value;
    }

    private List<Integer> getChipIdsInternal(String methodStr) {
        Mutable<List<Integer>> chipIdResp = new Mutable<>();
        try {
            mWifi.getChipIds((status, chipIds) -> {
                if (isOk(status, methodStr)) {
                    chipIdResp.value = chipIds;
                }
            });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return chipIdResp.value;
    }

    private boolean registerEventCallbackInternal(WifiHal.Callback callback) {
        if (mFrameworkCallback != null) {
            Log.e(TAG, "Framework callback is already registered");
            return false;
        } else if (callback == null) {
            Log.e(TAG, "Cannot register a null callback");
            return false;
        }

        if (!registerHalCallback()) return false;
        mFrameworkCallback = callback;
        return true;
    }

    private boolean registerHalCallback() {
        final String methodStr = "registerHalCallback";
        try {
            android.hardware.wifi.V1_5.IWifi wifi15 = getWifiV1_5Mockable();
            WifiStatus status;
            if (wifi15 != null) {
                status = wifi15.registerEventCallback_1_5(mHalCallback15);
            } else {
                status = mWifi.registerEventCallback(mHalCallback10);
            }

            if (!isOk(status, methodStr)) {
                Log.e(TAG, "Unable to register HAL callback");
                mWifi = null;
                return false;
            }
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private @WifiHal.WifiStatusCode int startInternal(String methodStr) {
        try {
            WifiStatus status = mWifi.start();
            return halToFrameworkWifiStatusCode(status.code);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return WifiHal.WIFI_STATUS_ERROR_UNKNOWN;
        }
    }

    private boolean isStartedInternal(String methodStr) {
        try {
            return mWifi.isStarted();
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private boolean stopInternal(String methodStr) {
        try {
            WifiStatus status = mWifi.stop();
            return isOk(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    private class WifiEventCallback extends android.hardware.wifi.V1_0.IWifiEventCallback.Stub {
        @Override
        public void onStart() throws RemoteException {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onStart();
        }

        @Override
        public void onStop() throws RemoteException {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onStop();
        }

        @Override
        public void onFailure(WifiStatus status) throws RemoteException {
            synchronized (mLock) {
                mWifi = null;
                if (mFrameworkCallback == null) return;
                mFrameworkCallback.onFailure(halToFrameworkWifiStatusCode(status.code));
            }
        }
    }

    private class WifiEventCallbackV15 extends android.hardware.wifi.V1_5.IWifiEventCallback.Stub {
        @Override
        public void onStart() throws RemoteException {
            if (mFrameworkCallback == null) return;
            mHalCallback10.onStart();
        }

        @Override
        public void onStop() throws RemoteException {
            if (mFrameworkCallback == null) return;
            mHalCallback10.onStop();
        }

        @Override
        public void onFailure(WifiStatus status) throws RemoteException {
            if (mFrameworkCallback == null) return;
            mHalCallback10.onFailure(status);
        }

        @Override
        public void onSubsystemRestart(WifiStatus status) throws RemoteException {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onSubsystemRestart(halToFrameworkWifiStatusCode(status.code));
        }
    }

    private class ServiceManagerDeathRecipient implements IHwBinder.DeathRecipient {
        @Override
        public void serviceDied(long cookie) {
            Log.wtf(TAG, "IServiceManager died: cookie=" + cookie);
            mServiceManager = null;
            // theoretically can call initServiceManager again here - but
            // there's no point since most likely system is going to reboot
        }
    }

    private class WifiDeathRecipient implements IHwBinder.DeathRecipient {
        @Override
        public void serviceDied(long cookie) {
            synchronized (mLock) {
                Log.e(TAG, "IWifi HAL service died! Have a listener for it ... cookie="
                        + cookie);
                mWifi = null;
                if (mFrameworkDeathRecipient != null) {
                    mFrameworkDeathRecipient.onDeath();
                }
                // don't restart: wait for registration notification
            }
        }
    }

    private final IServiceNotification mServiceNotificationCallback =
            new IServiceNotification.Stub() {
                @Override
                public void onRegistration(String fqName, String name, boolean preexisting) {
                    synchronized (mLock) {
                        Log.d(TAG, "IWifi registration notification: fqName=" + fqName
                                + ", name=" + name + ", preexisting=" + preexisting);
                        initWifiIfNecessaryLocked();
                    }
                }
            };


    // Helper Functions

    private void initServiceManagerIfNecessaryLocked() {
        if (mServiceManager != null) {
            Log.i(TAG, "mServiceManager already exists");
            return;
        }
        Log.i(TAG, "initServiceManagerIfNecessaryLocked");

        // Failures of IServiceManager are most likely system breaking.
        // Behavior here will be to WTF and continue.
        mServiceManager = getServiceManagerMockable();
        if (mServiceManager == null) {
            Log.wtf(TAG, "Failed to get IServiceManager instance");
            return;
        }

        try {
            if (!mServiceManager.linkToDeath(mServiceManagerDeathRecipient, 0)) {
                Log.wtf(TAG, "Error on linkToDeath on IServiceManager");
                mServiceManager = null;
                return;
            }
            if (!mServiceManager.registerForNotifications(IWifi.kInterfaceName, "",
                    mServiceNotificationCallback)) {
                Log.wtf(TAG, "Failed to register a listener for IWifi service");
                mServiceManager = null;
            }
        } catch (RemoteException e) {
            Log.wtf(TAG, "Exception while operating on IServiceManager: " + e);
            mServiceManager = null;
            return;
        }
        mIsVendorHalSupported = isSupportedInternal();
    }

    private void initWifiIfNecessaryLocked() {
        if (mWifi != null) {
            Log.i(TAG, "mWifi already exists");
            return;
        }
        Log.i(TAG, "initWifiIfNecessaryLocked");

        try {
            mWifi = getWifiServiceMockable();
            if (mWifi == null) {
                Log.e(TAG, "IWifi not (yet) available - but have a listener for it ...");
                return;
            }

            if (!mWifi.linkToDeath(mWifiDeathRecipient, /* don't care */ 0)) {
                Log.e(TAG, "Error on linkToDeath on IWifi - will retry later");
                return;
            }

            // Stop wifi just in case. Stop will invalidate the callbacks, so re-register them.
            stopInternal("stop");
            registerHalCallback();
            Log.i(TAG, "mWifi was retrieved. HAL is running version " + getVersion());
        } catch (RemoteException e) {
            Log.e(TAG, "Exception while operating on IWifi: " + e);
        }
    }

    /**
     * Uses the IServiceManager to query if the vendor HAL is present in the VINTF for the device
     * or not.
     * @return true if supported, false otherwise.
     */
    private boolean isSupportedInternal() {
        if (mServiceManager == null) {
            Log.e(TAG, "isSupported: called but mServiceManager is null!?");
            return false;
        }
        try {
            List<String> wifiServices =
                    mServiceManager.listManifestByInterface(IWifi.kInterfaceName);
            return !wifiServices.isEmpty();
        } catch (RemoteException e) {
            Log.wtf(TAG, "Exception while operating on IServiceManager: " + e);
            return false;
        }
    }

    /**
     * Indicates whether the HIDL service is declared. Uses the IServiceManager to check
     * if the device is running a version >= V1_0 of the HAL from the VINTF for the device.
     */
    protected static boolean serviceDeclared() {
        try {
            IServiceManager serviceManager = getServiceManager();
            if (serviceManager == null) {
                Log.e(TAG, "Unable to get service manager to check for service.");
                return false;
            }
            String interfaceName = android.hardware.wifi.V1_0.IWifi.kInterfaceName;
            if (serviceManager.getTransport(interfaceName, "default")
                    != IServiceManager.Transport.EMPTY) {
                return true;
            }
            return false;
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to check for existence of HIDL service.");
            return false;
        }
    }

    private String getVersion() {
        if (checkHalVersionByInterfaceName(
                android.hardware.wifi.V1_6.IWifi.kInterfaceName)) {
            return "1.6";
        } else if (checkHalVersionByInterfaceName(
                android.hardware.wifi.V1_5.IWifi.kInterfaceName)) {
            return "1.5";
        } else if (checkHalVersionByInterfaceName(
                android.hardware.wifi.V1_4.IWifi.kInterfaceName)) {
            return "1.4";
        } else if (checkHalVersionByInterfaceName(
                android.hardware.wifi.V1_3.IWifi.kInterfaceName)) {
            return "1.3";
        } else if (checkHalVersionByInterfaceName(
                android.hardware.wifi.V1_2.IWifi.kInterfaceName)) {
            return "1.2";
        } else if (checkHalVersionByInterfaceName(
                android.hardware.wifi.V1_1.IWifi.kInterfaceName)) {
            return "1.1";
        } else {
            // Service exists, so at least V1_0 is supported
            return "1.0";
        }
    }

    /**
     * Use the IServiceManager to check if the device is running V1_X of the HAL
     * from the VINTF for the device.
     *
     * @return true if HAL version is V1_X, false otherwise.
     */
    private boolean checkHalVersionByInterfaceName(String interfaceName) {
        if (interfaceName == null) return false;
        if (mServiceManager == null) {
            Log.e(TAG, "checkHalVersionByInterfaceName called but mServiceManager is null!?");
            return false;
        }
        try {
            return (mServiceManager.getTransport(interfaceName, "default")
                    != IServiceManager.Transport.EMPTY);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception while operating on IServiceManager: " + e);
            handleRemoteException(e, "getTransport");
            return false;
        }
    }

    protected static @WifiHal.WifiStatusCode int halToFrameworkWifiStatusCode(int code) {
        switch (code) {
            case WifiStatusCode.SUCCESS:
                return WifiHal.WIFI_STATUS_SUCCESS;
            case WifiStatusCode.ERROR_WIFI_CHIP_INVALID:
                return WifiHal.WIFI_STATUS_ERROR_WIFI_CHIP_INVALID;
            case WifiStatusCode.ERROR_WIFI_IFACE_INVALID:
                return WifiHal.WIFI_STATUS_ERROR_WIFI_IFACE_INVALID;
            case WifiStatusCode.ERROR_WIFI_RTT_CONTROLLER_INVALID:
                return WifiHal.WIFI_STATUS_ERROR_WIFI_RTT_CONTROLLER_INVALID;
            case WifiStatusCode.ERROR_NOT_SUPPORTED:
                return WifiHal.WIFI_STATUS_ERROR_NOT_SUPPORTED;
            case WifiStatusCode.ERROR_NOT_AVAILABLE:
                return WifiHal.WIFI_STATUS_ERROR_NOT_AVAILABLE;
            case WifiStatusCode.ERROR_NOT_STARTED:
                return WifiHal.WIFI_STATUS_ERROR_NOT_STARTED;
            case WifiStatusCode.ERROR_INVALID_ARGS:
                return WifiHal.WIFI_STATUS_ERROR_INVALID_ARGS;
            case WifiStatusCode.ERROR_BUSY:
                return WifiHal.WIFI_STATUS_ERROR_BUSY;
            case WifiStatusCode.ERROR_UNKNOWN:
                return WifiHal.WIFI_STATUS_ERROR_UNKNOWN;
            default:
                Log.e(TAG, "Invalid WifiStatusCode received: " + code);
                return WifiHal.WIFI_STATUS_ERROR_UNKNOWN;
        }
    }

    protected android.hardware.wifi.V1_5.IWifi getWifiV1_5Mockable() {
        return android.hardware.wifi.V1_5.IWifi.castFrom(mWifi);
    }

    private static IServiceManager getServiceManager() {
        try {
            return IServiceManager.getService();
        } catch (RemoteException e) {
            Log.e(TAG, "Exception getting IServiceManager: " + e);
            return null;
        }
    }

    // Non-static wrapper to allow mocking in the unit tests.
    @VisibleForTesting
    protected IServiceManager getServiceManagerMockable() {
        return getServiceManager();
    }

    protected IWifi getWifiServiceMockable() {
        try {
            return IWifi.getService(true /* retry */);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception getting IWifi service: " + e);
            return null;
        }
    }

    private boolean isOk(WifiStatus status, String methodStr) {
        if (status.code == WifiStatusCode.SUCCESS) return true;
        Log.e(TAG, methodStr + " failed with status: " + status);
        return false;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
        mWifi = null;
    }

    private <T> T validateAndCall(String methodStr, T defaultVal, @NonNull Supplier<T> supplier) {
        if (mWifi == null) {
            Log.e(TAG, "Cannot call " + methodStr + " because mWifi is null");
            return defaultVal;
        }
        return supplier.get();
    }
}
