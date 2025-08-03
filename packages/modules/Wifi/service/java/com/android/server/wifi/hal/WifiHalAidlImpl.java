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
import android.hardware.wifi.IWifiChip;
import android.hardware.wifi.IWifiEventCallback;
import android.hardware.wifi.WifiStatusCode;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.SsidTranslator;

import java.util.ArrayList;
import java.util.List;

/**
 * AIDL implementation of the IWifiHal interface.
 */
public class WifiHalAidlImpl implements IWifiHal {
    private static final String TAG = "WifiHalAidlImpl";
    private static final String HAL_INSTANCE_NAME =
            android.hardware.wifi.IWifi.DESCRIPTOR + "/default";

    private android.hardware.wifi.IWifi mWifi;
    private Context mContext;
    private SsidTranslator mSsidTranslator;
    private IWifiEventCallback mHalCallback;
    private WifiHal.Callback mFrameworkCallback;
    private DeathRecipient mServiceDeathRecipient;
    private WifiHal.DeathRecipient mFrameworkDeathRecipient;
    private final Object mLock = new Object();
    private static int sServiceVersion;

    public WifiHalAidlImpl(@NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
        Log.i(TAG, "Creating the Wifi HAL using the AIDL implementation");
        mContext = context;
        mSsidTranslator = ssidTranslator;
        mServiceDeathRecipient = new WifiDeathRecipient();
        mHalCallback = new WifiEventCallback();
    }

    /**
     * See comments for {@link IWifiHal#getChip(int)}
     */
    @Override
    @Nullable
    public WifiChip getChip(int chipId) {
        final String methodStr = "getChip";
        synchronized (mLock) {
            try {
                if (!checkWifiAndLogFailure(methodStr)) return null;
                IWifiChip chip = mWifi.getChip(chipId);
                return new WifiChip(chip, mContext, mSsidTranslator);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiHal#getChipIds()}
     */
    @Override
    @Nullable
    public List<Integer> getChipIds() {
        final String methodStr = "getChipIds";
        synchronized (mLock) {
            try {
                if (!checkWifiAndLogFailure(methodStr)) return null;
                int[] chipIdArray = mWifi.getChipIds();
                List<Integer> chipIdList = new ArrayList<>();
                for (int id : chipIdArray) {
                    chipIdList.add(id);
                }
                return chipIdList;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiHal#registerEventCallback(WifiHal.Callback)}
     */
    @Override
    public boolean registerEventCallback(WifiHal.Callback callback) {
        synchronized (mLock) {
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
    }

    private boolean registerHalCallback() {
        final String methodStr = "registerHalCallback";
        try {
            if (!checkWifiAndLogFailure(methodStr)) return false;
            mWifi.registerEventCallback(mHalCallback);
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
        }
        return false;
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
        return serviceDeclared();
    }

    /**
     * Indicates whether the AIDL service is declared.
     */
    protected static boolean serviceDeclared() {
        // Service Manager API ServiceManager#isDeclared is supported after U.
        return SdkLevel.isAtLeastU() ? ServiceManager.isDeclared(HAL_INSTANCE_NAME) : false;
    }

    /**
     * See comments for {@link IWifiHal#start()}
     */
    @Override
    public @WifiHal.WifiStatusCode int start() {
        final String methodStr = "start";
        synchronized (mLock) {
            try {
                if (!checkWifiAndLogFailure(methodStr)) return WifiHal.WIFI_STATUS_ERROR_UNKNOWN;
                mWifi.start();
                return WifiHal.WIFI_STATUS_SUCCESS;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return WifiHal.WIFI_STATUS_ERROR_REMOTE_EXCEPTION;
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
                return halToFrameworkWifiStatusCode(e.errorCode);
            }
        }
    }

    /**
     * See comments for {@link IWifiHal#isStarted()}
     */
    @Override
    public boolean isStarted() {
        final String methodStr = "isStarted";
        synchronized (mLock) {
            try {
                if (!checkWifiAndLogFailure(methodStr)) return false;
                return mWifi.isStarted();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiHal#stop()}
     */
    @Override
    public boolean stop() {
        synchronized (mLock) {
            if (!checkWifiAndLogFailure("stop")) return false;
            boolean result = stopInternal();
            return result;
        }
    }

    private boolean stopInternal() {
        final String methodStr = "stopInternal";
        try {
            mWifi.stop();
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
        }
        return false;
    }

    /**
     * See comments for {@link IWifiHal#initialize(WifiHal.DeathRecipient)}
     */
    @Override
    public void initialize(WifiHal.DeathRecipient deathRecipient) {
        final String methodStr = "initialize";
        synchronized (mLock) {
            if (mWifi != null) {
                Log.i(TAG, "Service is already initialized");
                return;
            }

            mWifi = getWifiServiceMockable();
            if (mWifi == null) {
                Log.e(TAG, "Unable to obtain the IWifi binder");
                return;
            }
            Log.i(TAG, "Obtained the IWifi binder. Local Version: "
                    + android.hardware.wifi.IWifi.VERSION);

            try {
                sServiceVersion = mWifi.getInterfaceVersion();
                Log.i(TAG, "Remote Version: " + sServiceVersion);
                IBinder serviceBinder = getServiceBinderMockable();
                if (serviceBinder == null) {
                    Log.e(TAG, "Unable to obtain the service binder");
                    return;
                }
                serviceBinder.linkToDeath(mServiceDeathRecipient, /* flags= */  0);
                mFrameworkDeathRecipient = deathRecipient;

                // Stop wifi just in case. Stop will invalidate the callbacks, so re-register them.
                stopInternal();
                registerHalCallback();
                Log.i(TAG, "Initialization is complete");
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
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

    private class WifiEventCallback extends IWifiEventCallback.Stub {
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
        public void onFailure(int statusCode) throws RemoteException {
            synchronized (mLock) {
                mWifi = null;
            }
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onFailure(halToFrameworkWifiStatusCode(statusCode));
        }

        @Override
        public void onSubsystemRestart(int statusCode) throws RemoteException {
            if (mFrameworkCallback == null) return;
            mFrameworkCallback.onSubsystemRestart(halToFrameworkWifiStatusCode(statusCode));
        }

        @Override
        public String getInterfaceHash() {
            return IWifiEventCallback.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return IWifiEventCallback.VERSION;
        }
    }

    private class WifiDeathRecipient implements DeathRecipient {
        @Override
        public void binderDied() {
            synchronized (mLock) {
                Log.w(TAG, "IWifi binder died.");
                mWifi = null;
                if (mFrameworkDeathRecipient != null) {
                    mFrameworkDeathRecipient.onDeath();
                }
            }
        }
    }


    // Utilities

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

    @VisibleForTesting
    protected android.hardware.wifi.IWifi getWifiServiceMockable() {
        try {
            if (SdkLevel.isAtLeastU()) {
                return android.hardware.wifi.IWifi.Stub.asInterface(
                        ServiceManager.waitForDeclaredService(HAL_INSTANCE_NAME));
            } else {
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to get IWifi service, " + e);
            return null;
        }
    }

    @VisibleForTesting
    protected IBinder getServiceBinderMockable() {
        if (mWifi == null) return null;
        return mWifi.asBinder();
    }

    /**
     * Check that the service is running at least the expected version. Method is protected
     * in order to allow calls from the WifiXxxIface classes.
     */
    protected static boolean isServiceVersionAtLeast(int expectedVersion) {
        return expectedVersion <= sServiceVersion;
    }

    private boolean checkWifiAndLogFailure(String methodStr) {
        if (mWifi == null) {
            Log.e(TAG, "Unable to call " + methodStr + " because IWifi is null.");
            return false;
        }
        return true;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        mWifi = null;
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with service-specific exception: " + e);
    }
}
