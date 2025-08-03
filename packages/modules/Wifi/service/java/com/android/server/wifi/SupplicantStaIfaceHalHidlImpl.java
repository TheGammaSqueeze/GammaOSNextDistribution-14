/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.server.wifi;

import static android.net.wifi.WifiManager.WIFI_FEATURE_DECORATED_IDENTITY;
import static android.net.wifi.WifiManager.WIFI_FEATURE_DPP;
import static android.net.wifi.WifiManager.WIFI_FEATURE_DPP_ENROLLEE_RESPONDER;
import static android.net.wifi.WifiManager.WIFI_FEATURE_FILS_SHA256;
import static android.net.wifi.WifiManager.WIFI_FEATURE_FILS_SHA384;
import static android.net.wifi.WifiManager.WIFI_FEATURE_MBO;
import static android.net.wifi.WifiManager.WIFI_FEATURE_OCE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_OWE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_PASSPOINT_TERMS_AND_CONDITIONS;
import static android.net.wifi.WifiManager.WIFI_FEATURE_SAE_PK;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WAPI;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WFD_R2;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WPA3_SAE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WPA3_SUITE_B;

import android.annotation.NonNull;
import android.content.Context;
import android.hardware.wifi.V1_0.WifiChannelWidthInMhz;
import android.hardware.wifi.supplicant.V1_0.ISupplicant;
import android.hardware.wifi.supplicant.V1_0.ISupplicantIface;
import android.hardware.wifi.supplicant.V1_0.ISupplicantNetwork;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIface;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIfaceCallback;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork;
import android.hardware.wifi.supplicant.V1_0.IfaceType;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatus;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatusCode;
import android.hardware.wifi.supplicant.V1_0.WpsConfigMethods;
import android.hardware.wifi.supplicant.V1_2.DppAkm;
import android.hardware.wifi.supplicant.V1_2.DppNetRole;
import android.hardware.wifi.supplicant.V1_3.ConnectionCapabilities;
import android.hardware.wifi.supplicant.V1_3.WifiTechnology;
import android.hardware.wifi.supplicant.V1_3.WpaDriverCapabilitiesMask;
import android.hardware.wifi.supplicant.V1_4.DppCurve;
import android.hardware.wifi.supplicant.V1_4.LegacyMode;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiAnnotations.WifiStandard;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.IHwBinder.DeathRecipient;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.WifiNative.DppEventCallback;
import com.android.server.wifi.WifiNative.SupplicantDeathEventHandler;
import com.android.server.wifi.util.GeneralUtil.Mutable;
import com.android.server.wifi.util.NativeUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Hal calls for bring up/shut down of the supplicant daemon and for
 * sending requests to the supplicant daemon
 * To maintain thread-safety, the locking protocol is that every non-static method (regardless of
 * access level) acquires mLock.
 */
@ThreadSafe
public class SupplicantStaIfaceHalHidlImpl implements ISupplicantStaIfaceHal {
    private static final String TAG = "SupplicantStaIfaceHalHidlImpl";
    @VisibleForTesting
    public static final String HAL_INSTANCE_NAME = "default";
    @VisibleForTesting
    public static final long WAIT_FOR_DEATH_TIMEOUT_MS = 50L;
    /**
     * Regex pattern for extracting the wps device type bytes.
     * Matches a strings like the following: "<categ>-<OUI>-<subcateg>";
     */
    private static final Pattern WPS_DEVICE_TYPE_PATTERN =
            Pattern.compile("^(\\d{1,2})-([0-9a-fA-F]{8})-(\\d{1,2})$");

    private final Object mLock = new Object();
    private boolean mVerboseLoggingEnabled = false;
    private boolean mVerboseHalLoggingEnabled = false;

    // Supplicant HAL interface objects
    private IServiceManager mIServiceManager = null;
    private ISupplicant mISupplicant;
    private Map<String, ISupplicantStaIface> mISupplicantStaIfaces = new HashMap<>();
    private Map<String, ISupplicantStaIfaceCallback> mISupplicantStaIfaceCallbacks =
            new HashMap<>();
    private Map<String, SupplicantStaNetworkHalHidlImpl> mCurrentNetworkRemoteHandles =
            new HashMap<>();
    private Map<String, WifiConfiguration> mCurrentNetworkLocalConfigs = new HashMap<>();
    private Map<String, WifiSsid> mCurrentNetworkFallbackSsids = new HashMap<>();
    private Map<String, List<Pair<SupplicantStaNetworkHalHidlImpl, WifiConfiguration>>>
            mLinkedNetworkLocalAndRemoteConfigs = new HashMap<>();
    @VisibleForTesting
    PmkCacheManager mPmkCacheManager;
    private SupplicantDeathEventHandler mDeathEventHandler;
    private ServiceManagerDeathRecipient mServiceManagerDeathRecipient;
    private SupplicantDeathRecipient mSupplicantDeathRecipient;
    // Death recipient cookie registered for current supplicant instance.
    private long mDeathRecipientCookie = 0;
    private CountDownLatch mWaitForDeathLatch;
    private final Context mContext;
    private final WifiMonitor mWifiMonitor;
    private final FrameworkFacade mFrameworkFacade;
    private final Handler mEventHandler;
    private DppEventCallback mDppCallback = null;
    private final Clock mClock;
    private final WifiMetrics mWifiMetrics;
    private final WifiGlobals mWifiGlobals;
    private final @NonNull SsidTranslator mSsidTranslator;

    private final IServiceNotification mServiceNotificationCallback =
            new IServiceNotification.Stub() {
                public void onRegistration(String fqName, String name, boolean preexisting) {
                    synchronized (mLock) {
                        if (mVerboseLoggingEnabled) {
                            Log.i(TAG, "IServiceNotification.onRegistration for: " + fqName
                                    + ", " + name + " preexisting=" + preexisting);
                        }
                        if (mISupplicant == null) {
                            Log.e(TAG, "ISupplicant interface is null!");
                            return;
                        }
                        if (!linkToSupplicantDeath(mSupplicantDeathRecipient,
                                ++mDeathRecipientCookie)) {
                            Log.e(TAG, "Registering ISupplicant death recipient failed.");
                            return;
                        }
                        Log.i(TAG, "Completed service registration of ISupplicant.");
                    }
                }
            };
    private class ServiceManagerDeathRecipient implements DeathRecipient {
        @Override
        public void serviceDied(long cookie) {
            mEventHandler.post(() -> {
                synchronized (mLock) {
                    Log.w(TAG, "IServiceManager died: cookie=" + cookie);
                    supplicantServiceDiedHandler(mDeathRecipientCookie);
                    mIServiceManager = null; // Will need to register a new ServiceNotification
                }
            });
        }
    }
    private class SupplicantDeathRecipient implements DeathRecipient {
        @Override
        public void serviceDied(long cookie) {
            synchronized (mLock) {
                if (mWaitForDeathLatch != null) {
                    mWaitForDeathLatch.countDown();
                }
            }

            mEventHandler.post(() -> {
                synchronized (mLock) {
                    Log.w(TAG, "ISupplicant died: cookie=" + cookie);
                    supplicantServiceDiedHandler(cookie);
                }
            });
        }
    }

    public SupplicantStaIfaceHalHidlImpl(Context context, WifiMonitor monitor,
            FrameworkFacade frameworkFacade, Handler handler,
            Clock clock, WifiMetrics wifiMetrics,
            WifiGlobals wifiGlobals,
            @NonNull SsidTranslator ssidTranslator) {
        mContext = context;
        mWifiMonitor = monitor;
        mFrameworkFacade = frameworkFacade;
        mEventHandler = handler;
        mClock = clock;
        mWifiMetrics = wifiMetrics;
        mWifiGlobals = wifiGlobals;
        mSsidTranslator = ssidTranslator;

        mServiceManagerDeathRecipient = new ServiceManagerDeathRecipient();
        mSupplicantDeathRecipient = new SupplicantDeathRecipient();
        mPmkCacheManager = new PmkCacheManager(mClock, mEventHandler);
    }

    /**
     * Enable/Disable verbose logging.
     */
    public void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled) {
        synchronized (mLock) {
            mVerboseLoggingEnabled = verboseEnabled;
            mVerboseHalLoggingEnabled = halVerboseEnabled;
            setLogLevel(mVerboseHalLoggingEnabled);
        }
    }

    protected boolean isVerboseLoggingEnabled() {
        return mVerboseLoggingEnabled;
    }

    private boolean linkToServiceManagerDeath() {
        synchronized (mLock) {
            if (mIServiceManager == null) return false;
            try {
                if (!mIServiceManager.linkToDeath(mServiceManagerDeathRecipient, 0)) {
                    Log.wtf(TAG, "Error on linkToDeath on IServiceManager");
                    supplicantServiceDiedHandler(mDeathRecipientCookie);
                    mIServiceManager = null; // Will need to register a new ServiceNotification
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "IServiceManager.linkToDeath exception", e);
                return false;
            }
            return true;
        }
    }

    /**
     * Registers a service notification for the ISupplicant service, which triggers initialization
     * of the ISupplicantStaIface
     * @return true if the service notification was successfully registered
     */
    public boolean initialize() {
        synchronized (mLock) {
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "Registering ISupplicant service ready callback.");
            }
            mISupplicant = null;
            mISupplicantStaIfaces.clear();
            if (mIServiceManager != null) {
                // Already have an IServiceManager and serviceNotification registered, don't
                // don't register another.
                return true;
            }
            try {
                mIServiceManager = getServiceManagerMockable();
                if (mIServiceManager == null) {
                    Log.e(TAG, "Failed to get HIDL Service Manager");
                    return false;
                }
                if (!linkToServiceManagerDeath()) {
                    return false;
                }
                /* TODO(b/33639391) : Use the new ISupplicant.registerForNotifications() once it
                   exists */
                if (!mIServiceManager.registerForNotifications(
                        ISupplicant.kInterfaceName, "", mServiceNotificationCallback)) {
                    Log.e(TAG, "Failed to register for notifications to "
                            + ISupplicant.kInterfaceName);
                    mIServiceManager = null; // Will need to register a new ServiceNotification
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while trying to register a listener for "
                        + "ISupplicant service: " + e);
                supplicantServiceDiedHandler(mDeathRecipientCookie);
            }
            return true;
        }
    }

    private boolean linkToSupplicantDeath(
            DeathRecipient deathRecipient, long cookie) {
        synchronized (mLock) {
            if (mISupplicant == null) return false;
            try {
                if (!mISupplicant.linkToDeath(deathRecipient, cookie)) {
                    Log.wtf(TAG, "Error on linkToDeath on ISupplicant");
                    supplicantServiceDiedHandler(mDeathRecipientCookie);
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicant.linkToDeath exception", e);
                return false;
            }
            return true;
        }
    }

    protected int getCurrentNetworkId(@NonNull String ifaceName) {
        synchronized (mLock) {
            WifiConfiguration currentConfig = getCurrentNetworkLocalConfig(ifaceName);
            if (currentConfig == null) {
                return WifiConfiguration.INVALID_NETWORK_ID;
            }
            return currentConfig.networkId;
        }
    }

    private boolean trySetupStaIfaceV1_4(@NonNull String ifaceName,
            @NonNull ISupplicantStaIface iface)  throws RemoteException {
        if (!isV1_4()) return false;

        SupplicantStaIfaceHalCallbackV1_4 callbackV14 =
                new SupplicantStaIfaceHalCallbackV1_4(ifaceName);
        if (!registerCallbackV1_4(getStaIfaceMockableV1_4(iface), callbackV14)) {
            throw new RemoteException("Init StaIface V1_4 failed.");
        }
        /* keep this in a store to avoid recycling by garbage collector. */
        mISupplicantStaIfaceCallbacks.put(ifaceName, callbackV14);
        return true;
    }

    private boolean trySetupStaIfaceV1_3(@NonNull String ifaceName,
            @NonNull ISupplicantStaIface iface)  throws RemoteException {
        if (!isV1_3()) return false;

        /* try newer version first. */
        if (trySetupStaIfaceV1_4(ifaceName, iface)) {
            logd("Newer HAL is found, skip V1_3 remaining init flow.");
            return true;
        }
        SupplicantStaIfaceHalCallbackV1_3 callbackV13 =
                new SupplicantStaIfaceHalCallbackV1_3(ifaceName);
        if (!registerCallbackV1_3(getStaIfaceMockableV1_3(iface), callbackV13)) {
            throw new RemoteException("Init StaIface V1_3 failed.");
        }
        /* keep this in a store to avoid recycling by garbage collector. */
        mISupplicantStaIfaceCallbacks.put(ifaceName, callbackV13);
        return true;
    }

    private boolean trySetupStaIfaceV1_2(@NonNull String ifaceName,
            @NonNull ISupplicantStaIface iface) throws RemoteException {
        if (!isV1_2()) return false;

        /* try newer version first. */
        if (trySetupStaIfaceV1_3(ifaceName, iface)) {
            logd("Newer HAL is found, skip V1_2 remaining init flow.");
            return true;
        }

        SupplicantStaIfaceHalCallbackV1_2 callbackV12 =
                new SupplicantStaIfaceHalCallbackV1_2(ifaceName);
        if (!registerCallbackV1_2(getStaIfaceMockableV1_2(iface), callbackV12)) {
            throw new RemoteException("Init StaIface V1_2 failed.");
        }
        /* keep this in a store to avoid recycling by garbage collector. */
        mISupplicantStaIfaceCallbacks.put(ifaceName, callbackV12);
        return true;
    }

    private boolean trySetupStaIfaceV1_1(@NonNull String ifaceName,
            @NonNull ISupplicantStaIface iface) throws RemoteException {
        if (!isV1_1()) return false;

        /* try newer version first. */
        if (trySetupStaIfaceV1_2(ifaceName, iface)) {
            logd("Newer HAL is found, skip V1_1 remaining init flow.");
            return true;
        }

        SupplicantStaIfaceHalCallbackV1_1 callbackV11 =
                new SupplicantStaIfaceHalCallbackV1_1(ifaceName);
        if (!registerCallbackV1_1(getStaIfaceMockableV1_1(iface), callbackV11)) {
            throw new RemoteException("Init StaIface V1_1 failed.");
        }
        /* keep this in a store to avoid recycling by garbage collector. */
        mISupplicantStaIfaceCallbacks.put(ifaceName, callbackV11);
        return true;
    }

    /**
     * Helper function to set up StaIface with different HAL version.
     *
     * This helper function would try newer version recursively.
     * Once the latest version is found, it would register the callback
     * of the latest version and skip unnecessary older HAL init flow.
     *
     * New version callback will be extended from the older one, as a result,
     * older callback is always created regardless of the latest version.
     *
     * Uprev steps:
     * 1. add new helper function trySetupStaIfaceV1_Y.
     * 2. call newly added function in trySetupStaIfaceV1_X (X should be Y-1).
     */
    private ISupplicantStaIface setupStaIface(@NonNull String ifaceName,
            @NonNull ISupplicantIface ifaceHwBinder) throws RemoteException {
        /* Prepare base type for later cast. */
        ISupplicantStaIface iface = getStaIfaceMockable(ifaceHwBinder);

        /* try newer version first. */
        if (trySetupStaIfaceV1_1(ifaceName, iface)) {
            logd("Newer HAL is found, skip V1_0 remaining init flow.");
            return iface;
        }

        SupplicantStaIfaceHalCallback callback = new SupplicantStaIfaceHalCallback(ifaceName);
        if (!registerCallback(iface, callback)) {
            throw new RemoteException("Init StaIface V1_0 failed.");
        }
        /* keep this in a store to avoid recycling by garbage collector. */
        mISupplicantStaIfaceCallbacks.put(ifaceName, callback);
        return iface;
    }

    /**
     * Setup a STA interface for the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    public boolean setupIface(@NonNull String ifaceName) {
        final String methodStr = "setupIface";
        if (checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr) != null) return false;
        ISupplicantIface ifaceHwBinder;

        if (isV1_1()) {
            ifaceHwBinder = addIfaceV1_1(ifaceName);
        } else {
            ifaceHwBinder = getIfaceV1_0(ifaceName);
        }
        if (ifaceHwBinder == null) {
            Log.e(TAG, "setupIface got null iface");
            return false;
        }

        try {
            ISupplicantStaIface iface = setupStaIface(ifaceName, ifaceHwBinder);
            mISupplicantStaIfaces.put(ifaceName, iface);
        } catch (RemoteException e) {
            loge("setup StaIface failed: " + e.toString());
            return false;
        }

        return true;
    }

    /**
     * Get a STA interface for the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    private ISupplicantIface getIfaceV1_0(@NonNull String ifaceName) {
        synchronized (mLock) {
            if (mISupplicant == null) {
                return null;
            }

            /** List all supplicant Ifaces */
            final ArrayList<ISupplicant.IfaceInfo> supplicantIfaces = new ArrayList<>();
            try {
                mISupplicant.listInterfaces((SupplicantStatus status,
                        ArrayList<ISupplicant.IfaceInfo> ifaces) -> {
                    if (status.code != SupplicantStatusCode.SUCCESS) {
                        Log.e(TAG, "Getting Supplicant Interfaces failed: " + status.code);
                        return;
                    }
                    supplicantIfaces.addAll(ifaces);
                });
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicant.listInterfaces exception: " + e);
                handleRemoteException(e, "listInterfaces");
                return null;
            }
            if (supplicantIfaces.size() == 0) {
                Log.e(TAG, "Got zero HIDL supplicant ifaces. Stopping supplicant HIDL startup.");
                return null;
            }
            Mutable<ISupplicantIface> supplicantIface = new Mutable<>();
            for (ISupplicant.IfaceInfo ifaceInfo : supplicantIfaces) {
                if (ifaceInfo.type == IfaceType.STA
                        && TextUtils.equals(ifaceName, ifaceInfo.name)) {
                    try {
                        mISupplicant.getInterface(ifaceInfo,
                                (SupplicantStatus status, ISupplicantIface iface) -> {
                                    if (status.code != SupplicantStatusCode.SUCCESS) {
                                        Log.e(TAG, "Fail to get ISupplicantIface " + status.code);
                                        return;
                                    }
                                    supplicantIface.value = iface;
                                });
                    } catch (RemoteException e) {
                        Log.e(TAG, "ISupplicant.getInterface exception: " + e);
                        handleRemoteException(e, "getInterface");
                        return null;
                    }
                    break;
                }
            }
            return supplicantIface.value;
        }
    }

    /**
     * Create a STA interface for the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    private ISupplicantIface addIfaceV1_1(@NonNull String ifaceName) {
        synchronized (mLock) {
            ISupplicant.IfaceInfo ifaceInfo = new ISupplicant.IfaceInfo();
            ifaceInfo.name = ifaceName;
            ifaceInfo.type = IfaceType.STA;
            Mutable<ISupplicantIface> supplicantIface = new Mutable<>();
            try {
                getSupplicantMockableV1_1().addInterface(ifaceInfo,
                        (SupplicantStatus status, ISupplicantIface iface) -> {
                            if (status.code != SupplicantStatusCode.SUCCESS
                                    && status.code != SupplicantStatusCode.FAILURE_IFACE_EXISTS) {
                                Log.e(TAG, "Failed to create ISupplicantIface " + status.code);
                                return;
                            }
                            supplicantIface.value = iface;
                        });
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicant.addInterface exception: " + e);
                handleRemoteException(e, "addInterface");
                return null;
            } catch (NoSuchElementException e) {
                Log.e(TAG, "ISupplicant.addInterface exception: " + e);
                handleNoSuchElementException(e, "addInterface");
                return null;
            } catch (IllegalArgumentException e) {
                handleIllegalArgumentException(e, "addInterface");
                Log.e(TAG, "ISupplicant.addInterface exception: " + e);
                return null;
            }
            return supplicantIface.value;
        }
    }

    /**
     * Teardown a STA interface for the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    public boolean teardownIface(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "teardownIface";
            if (checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr) == null) return false;
            if (isV1_1()) {
                if (!removeIfaceV1_1(ifaceName)) {
                    Log.e(TAG, "Failed to remove iface = " + ifaceName);
                    return false;
                }
            }
            if (mISupplicantStaIfaces.remove(ifaceName) == null) {
                Log.e(TAG, "Trying to teardown unknown interface");
                return false;
            }
            mISupplicantStaIfaceCallbacks.remove(ifaceName);
            return true;
        }
    }

    /**
     * Remove a STA interface for the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    private boolean removeIfaceV1_1(@NonNull String ifaceName) {
        synchronized (mLock) {
            try {
                ISupplicant.IfaceInfo ifaceInfo = new ISupplicant.IfaceInfo();
                ifaceInfo.name = ifaceName;
                ifaceInfo.type = IfaceType.STA;
                SupplicantStatus status = getSupplicantMockableV1_1().removeInterface(ifaceInfo);
                if (status.code != SupplicantStatusCode.SUCCESS) {
                    Log.e(TAG, "Failed to remove iface " + status.code);
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicant.removeInterface exception: " + e);
                handleRemoteException(e, "removeInterface");
                return false;
            } catch (NoSuchElementException e) {
                Log.e(TAG, "ISupplicant.removeInterface exception: " + e);
                handleNoSuchElementException(e, "removeInterface");
                return false;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "ISupplicant.removeInterface exception: " + e);
                handleIllegalArgumentException(e, "removeInterface");
                return false;
            }
            return true;
        }
    }

    /**
     * Registers a death notification for supplicant.
     * @return Returns true on success.
     */
    public boolean registerDeathHandler(@NonNull SupplicantDeathEventHandler handler) {
        if (mDeathEventHandler != null) {
            Log.e(TAG, "Death handler already present");
        }
        mDeathEventHandler = handler;
        return true;
    }

    /**
     * Deregisters a death notification for supplicant.
     * @return Returns true on success.
     */
    public boolean deregisterDeathHandler() {
        if (mDeathEventHandler == null) {
            Log.e(TAG, "No Death handler present");
        }
        mDeathEventHandler = null;
        return true;
    }


    private void clearState() {
        synchronized (mLock) {
            mISupplicant = null;
            mISupplicantStaIfaces.clear();
            mCurrentNetworkLocalConfigs.clear();
            mCurrentNetworkRemoteHandles.clear();
            mLinkedNetworkLocalAndRemoteConfigs.clear();
        }
    }

    private void supplicantServiceDiedHandler(long cookie) {
        synchronized (mLock) {
            if (mDeathRecipientCookie != cookie) {
                Log.i(TAG, "Ignoring stale death recipient notification");
                return;
            }
            Log.i(TAG, "Handling service death");
            clearState();
            if (mDeathEventHandler != null) {
                mDeathEventHandler.onDeath();
            }
        }
    }

    /**
     * Signals whether Initialization completed successfully.
     */
    public boolean isInitializationStarted() {
        synchronized (mLock) {
            return mIServiceManager != null;
        }
    }

    /**
     * Signals whether Initialization completed successfully.
     */
    public boolean isInitializationComplete() {
        synchronized (mLock) {
            return mISupplicant != null;
        }
    }

    /**
     * Start the supplicant daemon.
     *
     * @return true on success, false otherwise.
     */
    public boolean startDaemon() {
        synchronized (mLock) {
            try {
                if (isV1_1()) {
                    Log.i(TAG, "Starting supplicant using HIDL 1.1");
                    mISupplicant = getSupplicantMockableV1_1();
                } else {
                    Log.i(TAG, "Starting supplicant using init");
                    if (!mFrameworkFacade.startSupplicant()) {
                        return false;
                    }
                    mISupplicant = getSupplicantMockable();
                }
                setLogLevel(mVerboseHalLoggingEnabled);
            } catch (RemoteException | NoSuchElementException e) {
                Log.e(TAG, "Exception while trying to start supplicant: " + e);
                supplicantServiceDiedHandler(mDeathRecipientCookie);
                return false;
            }
        }
        return true;
    }

    /**
     * Terminate the supplicant daemon for V1_1 service.
     */
    private void terminate_V1_1() {
        synchronized (mLock) {
            final String methodStr = "terminate";
            if (!checkSupplicantAndLogFailure(methodStr)) return;
            try {
                getSupplicantMockableV1_1().terminate();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (NoSuchElementException e) {
                handleNoSuchElementException(e, methodStr);
            }
        }
    }

    /**
     * Terminate the supplicant daemon & wait for it's death.
     */
    public void terminate() {
        synchronized (mLock) {
            mWaitForDeathLatch = new CountDownLatch(1);
            if (isV1_1()) {
                Log.i(TAG, "Terminating supplicant using HIDL");
                terminate_V1_1();
            } else {
                Log.i(TAG, "Terminating supplicant using init");
                mFrameworkFacade.stopSupplicant();
            }
        }

        // Now wait for death listener callback to confirm that it's dead.
        try {
            if (!mWaitForDeathLatch.await(WAIT_FOR_DEATH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Timed out waiting for confirmation of supplicant death");
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Failed to wait for supplicant death");
        }
    }

    /**
     * Indicates whether the HIDL service is declared. Uses the IServiceManager to check
     * if the device is running a version >= V1_0 of the HAL from the VINTF for the device.
     */
    public static boolean serviceDeclared() {
        try {
            IServiceManager serviceManager = IServiceManager.getService();
            String interfaceName = android.hardware.wifi.supplicant.V1_0.ISupplicant.kInterfaceName;
            if (serviceManager.getTransport(interfaceName, HAL_INSTANCE_NAME)
                    != IServiceManager.Transport.EMPTY) {
                return true;
            }
            return false;
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to check for existence of HIDL service.");
            return false;
        }
    }

    /**
     * Wrapper functions to access static HAL methods, created to be mockable in unit tests
     */
    protected IServiceManager getServiceManagerMockable() throws RemoteException {
        synchronized (mLock) {
            return IServiceManager.getService();
        }
    }

    protected ISupplicant getSupplicantMockable() throws RemoteException, NoSuchElementException {
        synchronized (mLock) {
            ISupplicant iSupplicant = ISupplicant.getService(true);
            if (iSupplicant == null) {
                throw new NoSuchElementException("Cannot get ISupplicant default service.");
            }
            return iSupplicant;
        }
    }

    protected android.hardware.wifi.supplicant.V1_1.ISupplicant getSupplicantMockableV1_1()
            throws RemoteException, NoSuchElementException {
        synchronized (mLock) {
            android.hardware.wifi.supplicant.V1_1.ISupplicant iSupplicantDerived =
                    android.hardware.wifi.supplicant.V1_1.ISupplicant.castFrom(
                            getSupplicantMockable());
            if (iSupplicantDerived == null) {
                throw new NoSuchElementException("Cannot cast to V1.1 service.");
            }
            return iSupplicantDerived;
        }
    }

    protected ISupplicantStaIface getStaIfaceMockable(ISupplicantIface iface) {
        synchronized (mLock) {
            return ISupplicantStaIface.asInterface(iface.asBinder());
        }
    }

    protected android.hardware.wifi.supplicant.V1_1.ISupplicantStaIface
            getStaIfaceMockableV1_1(ISupplicantIface iface) {
        synchronized (mLock) {
            return android.hardware.wifi.supplicant.V1_1.ISupplicantStaIface
                    .asInterface(iface.asBinder());
        }
    }

    protected android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface
            getStaIfaceMockableV1_2(ISupplicantIface iface) {
        synchronized (mLock) {
            return android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface
                    .asInterface(iface.asBinder());
        }
    }

    protected android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface
            getStaIfaceMockableV1_3(ISupplicantIface iface) {
        synchronized (mLock) {
            return android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface
                    .asInterface(iface.asBinder());
        }
    }

    protected android.hardware.wifi.supplicant.V1_4.ISupplicantStaIface
            getStaIfaceMockableV1_4(ISupplicantIface iface) {
        synchronized (mLock) {
            return android.hardware.wifi.supplicant.V1_4.ISupplicantStaIface
                    .asInterface(iface.asBinder());
        }
    }

    /**
     * Uses the IServiceManager to check if the device is running V1_1 of the HAL from the VINTF for
     * the device.
     * @return true if supported, false otherwise.
     */
    private boolean isV1_1() {
        return checkHalVersionByInterfaceName(
                android.hardware.wifi.supplicant.V1_1.ISupplicant.kInterfaceName);
    }

    /**
     * Uses the IServiceManager to check if the device is running V1_2 of the HAL from the VINTF for
     * the device.
     * @return true if supported, false otherwise.
     */
    private boolean isV1_2() {
        return checkHalVersionByInterfaceName(
                android.hardware.wifi.supplicant.V1_2.ISupplicant.kInterfaceName);
    }

    /**
     * Uses the IServiceManager to check if the device is running V1_3 of the HAL from the VINTF for
     * the device.
     * @return true if supported, false otherwise.
     */
    private boolean isV1_3() {
        return checkHalVersionByInterfaceName(
                android.hardware.wifi.supplicant.V1_3.ISupplicant.kInterfaceName);
    }

    /**
     * Uses the IServiceManager to check if the device is running V1_4 of the HAL from the VINTF for
     * the device.
     * @return true if supported, false otherwise.
     */
    private boolean isV1_4() {
        return checkHalVersionByInterfaceName(
                android.hardware.wifi.supplicant.V1_4.ISupplicant.kInterfaceName);
    }

    private boolean checkHalVersionByInterfaceName(String interfaceName) {
        if (interfaceName == null) {
            return false;
        }
        synchronized (mLock) {
            if (mIServiceManager == null) {
                Log.e(TAG, "checkHalVersionByInterfaceName: called but "
                        + "mServiceManager is null");
                return false;
            }
            try {
                return (mIServiceManager.getTransport(
                        interfaceName,
                        HAL_INSTANCE_NAME)
                        != IServiceManager.Transport.EMPTY);
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while operating on IServiceManager: " + e);
                handleRemoteException(e, "getTransport");
                return false;
            }
        }
    }

    /**
     * Helper method to look up the network object for the specified iface.
     */
    private ISupplicantStaIface getStaIface(@NonNull String ifaceName) {
        return mISupplicantStaIfaces.get(ifaceName);
    }

    /**
     * Helper method to look up the network object for the specified iface.
     */
    private SupplicantStaNetworkHalHidlImpl getCurrentNetworkRemoteHandle(
            @NonNull String ifaceName) {
        return mCurrentNetworkRemoteHandles.get(ifaceName);
    }

    /**
     * Helper method to look up the network config for the specified iface.
     */
    protected WifiConfiguration getCurrentNetworkLocalConfig(@NonNull String ifaceName) {
        return mCurrentNetworkLocalConfigs.get(ifaceName);
    }

    /**
     * Add a network configuration to wpa_supplicant.
     *
     * @param config Config corresponding to the network.
     * @return a Pair object including SupplicantStaNetworkHal and WifiConfiguration objects
     * for the current network.
     */
    private Pair<SupplicantStaNetworkHalHidlImpl, WifiConfiguration>
            addNetworkAndSaveConfig(@NonNull String ifaceName, WifiConfiguration config) {
        synchronized (mLock) {
            logi("addSupplicantStaNetwork via HIDL");
            if (config == null) {
                loge("Cannot add NULL network!");
                return null;
            }
            SupplicantStaNetworkHalHidlImpl network = addNetwork(ifaceName);
            if (network == null) {
                loge("Failed to add a network!");
                return null;
            }
            boolean saveSuccess = false;
            try {
                saveSuccess = network.saveWifiConfiguration(config);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Exception while saving config params: " + config, e);
            }
            if (!saveSuccess) {
                loge("Failed to save variables for: " + config.getProfileKey());
                if (!removeAllNetworks(ifaceName)) {
                    loge("Failed to remove all networks on failure.");
                }
                return null;
            }
            return new Pair(network, new WifiConfiguration(config));
        }
    }

    /**
     * Add the provided network configuration to wpa_supplicant and initiate connection to it.
     * This method does the following:
     * 1. If |config| is different to the current supplicant network, removes all supplicant
     * networks and saves |config|.
     * 2. Select the new network in wpa_supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param config WifiConfiguration parameters for the provided network.
     * @return true if it succeeds, false otherwise
     */
    public boolean connectToNetwork(@NonNull String ifaceName, @NonNull WifiConfiguration config) {
        return connectToNetwork(ifaceName, config, null);
    }

    /**
     * Connects to the fallback SSID (if any) of the current network upon a network not found
     * notification.
     */
    public boolean connectToFallbackSsid(@NonNull String ifaceName) {
        synchronized (mLock) {
            WifiSsid fallbackSsid = mCurrentNetworkFallbackSsids.remove(ifaceName);
            if (fallbackSsid == null) {
                return false;
            }
            Log.d(TAG, "connectToFallbackSsid " + fallbackSsid);
            return connectToNetwork(
                    ifaceName, getCurrentNetworkLocalConfig(ifaceName), fallbackSsid);
        }
    }

    /**
     * Add the provided network configuration to wpa_supplicant and initiate connection to it.
     * This method does the following:
     * 1. If |config| is different to the current supplicant network, removes all supplicant
     * networks and saves |config|.
     * 2. Selects an SSID from the 2 possible original SSIDs derived from config.SSID to pass to
     *    wpa_supplicant, and stores the unused one as fallback if the first one is not found.
     * 3. Select the new network in wpa_supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param config WifiConfiguration parameters for the provided network.
     * @param actualSsid The actual, untranslated SSID to send to supplicant. If this is null, then
     *                   we will connect to either of the 2 possible original SSIDs based on the
     *                   config's network selection BSSID or network selection candidate. If that
     *                   SSID is not found, then we will immediately connect to the other one.
     * @return true if it succeeds, false otherwise
     */
    private boolean connectToNetwork(@NonNull String ifaceName, @NonNull WifiConfiguration config,
            WifiSsid actualSsid) {
        synchronized (mLock) {
            logd("connectToNetwork " + config.getProfileKey());
            WifiConfiguration currentConfig = getCurrentNetworkLocalConfig(ifaceName);
            if (actualSsid == null && WifiConfigurationUtil.isSameNetwork(config, currentConfig)) {
                String networkSelectionBSSID = config.getNetworkSelectionStatus()
                        .getNetworkSelectionBSSID();
                String networkSelectionBSSIDCurrent =
                        currentConfig.getNetworkSelectionStatus().getNetworkSelectionBSSID();
                if (Objects.equals(networkSelectionBSSID, networkSelectionBSSIDCurrent)) {
                    logd("Network is already saved, will not trigger remove and add operation.");
                } else {
                    logd("Network is already saved, but need to update BSSID.");
                    if (!setCurrentNetworkBssid(
                            ifaceName,
                            config.getNetworkSelectionStatus().getNetworkSelectionBSSID())) {
                        loge("Failed to set current network BSSID.");
                        return false;
                    }
                    mCurrentNetworkLocalConfigs.put(ifaceName, new WifiConfiguration(config));
                }
            } else {
                mCurrentNetworkRemoteHandles.remove(ifaceName);
                mCurrentNetworkLocalConfigs.remove(ifaceName);
                mLinkedNetworkLocalAndRemoteConfigs.remove(ifaceName);
                mCurrentNetworkFallbackSsids.remove(ifaceName);
                if (!removeAllNetworks(ifaceName)) {
                    loge("Failed to remove existing networks");
                    return false;
                }
                WifiConfiguration supplicantConfig = new WifiConfiguration(config);
                if (actualSsid != null) {
                    supplicantConfig.SSID = actualSsid.toString();
                } else {
                    if (config.SSID != null) {
                        // No actual SSID supplied, so select from the network selection BSSID
                        // or the latest candidate BSSID.
                        WifiSsid configSsid = WifiSsid.fromString(config.SSID);
                        WifiSsid supplicantSsid = mSsidTranslator.getOriginalSsid(config);
                        if (supplicantSsid != null) {
                            supplicantConfig.SSID = supplicantSsid.toString();
                            List<WifiSsid> allPossibleSsids = mSsidTranslator
                                    .getAllPossibleOriginalSsids(configSsid);
                            WifiSsid selectedSsid = mSsidTranslator.getOriginalSsid(config);
                            allPossibleSsids.remove(selectedSsid);
                            if (!allPossibleSsids.isEmpty()) {
                                // Store the unused SSID to fallback on in
                                // connectToFallbackSsid(String) if the chosen SSID isn't found.
                                mCurrentNetworkFallbackSsids.put(
                                        ifaceName, allPossibleSsids.get(0));
                            }
                            Log.d(TAG, "Selecting supplicant SSID " + supplicantSsid);
                            supplicantConfig.SSID = supplicantSsid.toString();
                        }
                        // Set the actual translation of the original SSID in case the untranslated
                        // SSID has an ambiguous encoding.
                        mSsidTranslator.setTranslatedSsidForStaIface(configSsid, ifaceName);
                    }
                }
                Pair<SupplicantStaNetworkHalHidlImpl, WifiConfiguration> pair =
                        addNetworkAndSaveConfig(ifaceName, supplicantConfig);
                if (pair == null) {
                    loge("Failed to add/save network configuration: " + config
                            .getProfileKey());
                    return false;
                }
                mCurrentNetworkRemoteHandles.put(ifaceName, pair.first);
                mCurrentNetworkLocalConfigs.put(ifaceName, pair.second);
            }
            SupplicantStaNetworkHalHidlImpl networkHandle =
                    checkSupplicantStaNetworkAndLogFailure(ifaceName, "connectToNetwork");
            if (networkHandle == null) {
                loge("No valid remote network handle for network configuration: "
                        + config.getProfileKey());
                return false;
            }

            SecurityParams params = config.getNetworkSelectionStatus()
                    .getCandidateSecurityParams();
            if (params != null && !params.isSecurityType(WifiConfiguration.SECURITY_TYPE_PSK)) {
                List<ArrayList<Byte>> pmkDataList = mPmkCacheManager.get(config.networkId);
                if (pmkDataList != null) {
                    logi("Set PMK cache for config id " + config.networkId);
                    pmkDataList.forEach(pmkData -> {
                        if (networkHandle.setPmkCache(pmkData)) {
                            mWifiMetrics.setConnectionPmkCache(ifaceName, true);
                        }
                    });
                }
            }

            if (!networkHandle.select()) {
                loge("Failed to select network configuration: " + config.getProfileKey());
                return false;
            }
            return true;
        }
    }

    /**
     * Initiates roaming to the already configured network in wpa_supplicant. If the network
     * configuration provided does not match the already configured network, then this triggers
     * a new connection attempt (instead of roam).
     * 1. First check if we're attempting to connect to a linked network, and select the existing
     *    supplicant network if there is one.
     * 2. Set the new bssid for the network in wpa_supplicant.
     * 3. Trigger reassociate command to wpa_supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param config WifiConfiguration parameters for the provided network.
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    public boolean roamToNetwork(@NonNull String ifaceName, WifiConfiguration config) {
        synchronized (mLock) {
            if (updateOnLinkedNetworkRoaming(ifaceName, config.networkId, true)) {
                SupplicantStaNetworkHalHidlImpl networkHandle =
                        getCurrentNetworkRemoteHandle(ifaceName);
                if (networkHandle == null) {
                    loge("Roaming config matches a linked config, but a linked network handle was"
                            + " not found.");
                    return false;
                }
                return networkHandle.select();
            }
            if (getCurrentNetworkId(ifaceName) != config.networkId) {
                Log.w(TAG, "Cannot roam to a different network, initiate new connection. "
                        + "Current network ID: " + getCurrentNetworkId(ifaceName));
                return connectToNetwork(ifaceName, config);
            }
            String bssid = config.getNetworkSelectionStatus().getNetworkSelectionBSSID();
            logd("roamToNetwork" + config.getProfileKey() + " (bssid " + bssid + ")");

            SupplicantStaNetworkHalHidlImpl networkHandle =
                    checkSupplicantStaNetworkAndLogFailure(ifaceName, "roamToNetwork");
            if (networkHandle == null || !networkHandle.setBssid(bssid)) {
                loge("Failed to set new bssid on network: " + config.getProfileKey());
                return false;
            }
            if (!reassociate(ifaceName)) {
                loge("Failed to trigger reassociate");
                return false;
            }
            return true;
        }
    }

    /**
     * Clean HAL cached data for |networkId| in the framework.
     *
     * @param networkId network id of the network to be removed from supplicant.
     */
    public void removeNetworkCachedData(int networkId) {
        synchronized (mLock) {
            logd("Remove cached HAL data for config id " + networkId);
            removePmkCacheEntry(networkId);
        }
    }

    /**
     * Clear HAL cached data if MAC address is changed.
     *
     * @param networkId network id of the network to be checked.
     * @param curMacAddress current MAC address
     */
    public void removeNetworkCachedDataIfNeeded(int networkId, MacAddress curMacAddress) {
        synchronized (mLock) {
            mPmkCacheManager.remove(networkId, curMacAddress);
        }
    }

    /**
     * Remove all networks from supplicant
     *
     * @param ifaceName Name of the interface.
     */
    public boolean removeAllNetworks(@NonNull String ifaceName) {
        synchronized (mLock) {
            ArrayList<Integer> networks = listNetworks(ifaceName);
            if (networks == null) {
                Log.e(TAG, "removeAllNetworks failed, got null networks");
                return false;
            }
            for (int id : networks) {
                if (!removeNetwork(ifaceName, id)) {
                    Log.e(TAG, "removeAllNetworks failed to remove network: " + id);
                    return false;
                }
            }
            // Reset current network info.
            mCurrentNetworkRemoteHandles.remove(ifaceName);
            mCurrentNetworkLocalConfigs.remove(ifaceName);
            mLinkedNetworkLocalAndRemoteConfigs.remove(ifaceName);
            return true;
        }
    }

    /**
     * Disable the current network in supplicant
     *
     * @param ifaceName Name of the interface.
     */
    public boolean disableCurrentNetwork(@NonNull String ifaceName) {
        synchronized (mLock) {
            SupplicantStaNetworkHalHidlImpl networkHandle =
                    checkSupplicantStaNetworkAndLogFailure(ifaceName, "disableCurrentNetwork");
            if (networkHandle == null) return false;
            return networkHandle.disable();
        }
    }

    /**
     * Set the currently configured network's bssid.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr Bssid to set in the form of "XX:XX:XX:XX:XX:XX"
     * @return true if succeeds, false otherwise.
     */
    public boolean setCurrentNetworkBssid(@NonNull String ifaceName, String bssidStr) {
        synchronized (mLock) {
            SupplicantStaNetworkHalHidlImpl networkHandle =
                    checkSupplicantStaNetworkAndLogFailure(ifaceName, "setCurrentNetworkBssid");
            if (networkHandle == null) return false;
            return networkHandle.setBssid(bssidStr);
        }
    }

    /**
     * Get the currently configured network's WPS NFC token.
     *
     * @param ifaceName Name of the interface.
     * @return Hex string corresponding to the WPS NFC token.
     */
    public String getCurrentNetworkWpsNfcConfigurationToken(@NonNull String ifaceName) {
        synchronized (mLock) {
            SupplicantStaNetworkHalHidlImpl networkHandle =
                    checkSupplicantStaNetworkAndLogFailure(
                            ifaceName, "getCurrentNetworkWpsNfcConfigurationToken");
            if (networkHandle == null) return null;
            return networkHandle.getWpsNfcConfigurationToken();
        }
    }

    /**
     * Get the eap anonymous identity for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @return anonymous identity string if succeeds, null otherwise.
     */
    public String getCurrentNetworkEapAnonymousIdentity(@NonNull String ifaceName) {
        synchronized (mLock) {
            SupplicantStaNetworkHalHidlImpl networkHandle =
                    checkSupplicantStaNetworkAndLogFailure(
                            ifaceName, "getCurrentNetworkEapAnonymousIdentity");
            if (networkHandle == null) return null;
            return networkHandle.fetchEapAnonymousIdentity();
        }
    }

    /**
     * Send the eap identity response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param identity identity used for EAP-Identity
     * @param encryptedIdentity encrypted identity used for EAP-AKA/EAP-SIM
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapIdentityResponse(
            @NonNull String ifaceName, @NonNull String identity, String encryptedIdentity) {
        synchronized (mLock) {
            SupplicantStaNetworkHalHidlImpl networkHandle =
                    checkSupplicantStaNetworkAndLogFailure(
                            ifaceName, "sendCurrentNetworkEapIdentityResponse");
            if (networkHandle == null) return false;
            return networkHandle.sendNetworkEapIdentityResponse(identity, encryptedIdentity);
        }
    }

    /**
     * Send the eap sim gsm auth response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param paramsStr String to send.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapSimGsmAuthResponse(
            @NonNull String ifaceName, String paramsStr) {
        synchronized (mLock) {
            SupplicantStaNetworkHalHidlImpl networkHandle =
                    checkSupplicantStaNetworkAndLogFailure(
                            ifaceName, "sendCurrentNetworkEapSimGsmAuthResponse");
            if (networkHandle == null) return false;
            return networkHandle.sendNetworkEapSimGsmAuthResponse(paramsStr);
        }
    }

    /**
     * Send the eap sim gsm auth failure for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapSimGsmAuthFailure(@NonNull String ifaceName) {
        synchronized (mLock) {
            SupplicantStaNetworkHalHidlImpl networkHandle =
                    checkSupplicantStaNetworkAndLogFailure(
                            ifaceName, "sendCurrentNetworkEapSimGsmAuthFailure");
            if (networkHandle == null) return false;
            return networkHandle.sendNetworkEapSimGsmAuthFailure();
        }
    }

    /**
     * Send the eap sim umts auth response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param paramsStr String to send.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapSimUmtsAuthResponse(
            @NonNull String ifaceName, String paramsStr) {
        synchronized (mLock) {
            SupplicantStaNetworkHalHidlImpl networkHandle =
                    checkSupplicantStaNetworkAndLogFailure(
                            ifaceName, "sendCurrentNetworkEapSimUmtsAuthResponse");
            if (networkHandle == null) return false;
            return networkHandle.sendNetworkEapSimUmtsAuthResponse(paramsStr);
        }
    }

    /**
     * Send the eap sim umts auts response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param paramsStr String to send.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapSimUmtsAutsResponse(
            @NonNull String ifaceName, String paramsStr) {
        synchronized (mLock) {
            SupplicantStaNetworkHalHidlImpl networkHandle =
                    checkSupplicantStaNetworkAndLogFailure(
                            ifaceName, "sendCurrentNetworkEapSimUmtsAutsResponse");
            if (networkHandle == null) return false;
            return networkHandle.sendNetworkEapSimUmtsAutsResponse(paramsStr);
        }
    }

    /**
     * Send the eap sim umts auth failure for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapSimUmtsAuthFailure(@NonNull String ifaceName) {
        synchronized (mLock) {
            SupplicantStaNetworkHalHidlImpl networkHandle =
                    checkSupplicantStaNetworkAndLogFailure(
                            ifaceName, "sendCurrentNetworkEapSimUmtsAuthFailure");
            if (networkHandle == null) return false;
            return networkHandle.sendNetworkEapSimUmtsAuthFailure();
        }
    }

    /**
     * Adds a new network.
     *
     * @return The ISupplicantNetwork object for the new network, or null if the call fails
     */
    private SupplicantStaNetworkHalHidlImpl addNetwork(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "addNetwork";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return null;
            Mutable<ISupplicantNetwork> newNetwork = new Mutable<>();
            try {
                iface.addNetwork((SupplicantStatus status,
                        ISupplicantNetwork network) -> {
                    if (checkStatusAndLogFailure(status, methodStr)) {
                        newNetwork.value = network;
                    }
                });
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            }
            if (newNetwork.value != null) {
                return getStaNetworkMockable(
                        ifaceName,
                        ISupplicantStaNetwork.asInterface(newNetwork.value.asBinder()));
            } else {
                return null;
            }
        }
    }

    /**
     * Remove network from supplicant with network Id
     *
     * @return true if request is sent successfully, false otherwise.
     */
    private boolean removeNetwork(@NonNull String ifaceName, int id) {
        synchronized (mLock) {
            final String methodStr = "removeNetwork";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.removeNetwork(id);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Use this to mock the creation of SupplicantStaNetworkHal instance.
     *
     * @param ifaceName Name of the interface.
     * @param iSupplicantStaNetwork ISupplicantStaNetwork instance retrieved from HIDL.
     * @return The ISupplicantNetwork object for the given SupplicantNetworkId int, returns null if
     * the call fails
     */
    protected SupplicantStaNetworkHalHidlImpl getStaNetworkMockable(
            @NonNull String ifaceName, ISupplicantStaNetwork iSupplicantStaNetwork) {
        synchronized (mLock) {
            SupplicantStaNetworkHalHidlImpl network =
                    new SupplicantStaNetworkHalHidlImpl(iSupplicantStaNetwork, ifaceName, mContext,
                            mWifiMonitor, mWifiGlobals,
                            getAdvancedCapabilities(ifaceName));
            if (network != null) {
                network.enableVerboseLogging(mVerboseLoggingEnabled, mVerboseHalLoggingEnabled);
            }
            return network;
        }
    }

    /**
     * @return The ISupplicantNetwork object for the given SupplicantNetworkId int, returns null if
     * the call fails
     */
    private SupplicantStaNetworkHalHidlImpl getNetwork(@NonNull String ifaceName, int id) {
        synchronized (mLock) {
            final String methodStr = "getNetwork";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return null;
            Mutable<ISupplicantNetwork> gotNetwork = new Mutable<>();
            try {
                iface.getNetwork(id, (SupplicantStatus status, ISupplicantNetwork network) -> {
                    if (checkStatusAndLogFailure(status, methodStr)) {
                        gotNetwork.value = network;
                    }
                });
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            }
            if (gotNetwork.value != null) {
                return getStaNetworkMockable(
                        ifaceName,
                        ISupplicantStaNetwork.asInterface(gotNetwork.value.asBinder()));
            } else {
                return null;
            }
        }
    }

    /** See ISupplicantStaNetwork.hal for documentation */
    private boolean registerCallback(
            ISupplicantStaIface iface, ISupplicantStaIfaceCallback callback) {
        synchronized (mLock) {
            final String methodStr = "registerCallback";
            if (iface == null) return false;
            try {
                SupplicantStatus status =  iface.registerCallback(callback);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    private boolean registerCallbackV1_1(
            android.hardware.wifi.supplicant.V1_1.ISupplicantStaIface iface,
            android.hardware.wifi.supplicant.V1_1.ISupplicantStaIfaceCallback callback) {
        synchronized (mLock) {
            String methodStr = "registerCallback_1_1";

            if (iface == null) return false;
            try {
                SupplicantStatus status =  iface.registerCallback_1_1(callback);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    private boolean registerCallbackV1_2(
            android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface iface,
            android.hardware.wifi.supplicant.V1_2.ISupplicantStaIfaceCallback callback) {
        synchronized (mLock) {
            String methodStr = "registerCallback_1_2";

            if (iface == null) return false;
            try {
                SupplicantStatus status =  iface.registerCallback_1_2(callback);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    private boolean registerCallbackV1_3(
            android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface iface,
            android.hardware.wifi.supplicant.V1_3.ISupplicantStaIfaceCallback callback) {
        synchronized (mLock) {
            String methodStr = "registerCallback_1_3";

            if (iface == null) return false;
            try {
                SupplicantStatus status =  iface.registerCallback_1_3(callback);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    private boolean registerCallbackV1_4(
            android.hardware.wifi.supplicant.V1_4.ISupplicantStaIface iface,
            android.hardware.wifi.supplicant.V1_4.ISupplicantStaIfaceCallback callback) {
        synchronized (mLock) {
            String methodStr = "registerCallback_1_4";

            if (iface == null) return false;
            try {
                android.hardware.wifi.supplicant.V1_4.SupplicantStatus status =
                        iface.registerCallback_1_4(callback);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * @return a list of SupplicantNetworkID ints for all networks controlled by supplicant, returns
     * null if the call fails
     */
    private java.util.ArrayList<Integer> listNetworks(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "listNetworks";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return null;
            Mutable<ArrayList<Integer>> networkIdList = new Mutable<>();
            try {
                iface.listNetworks((SupplicantStatus status, ArrayList<Integer> networkIds) -> {
                    if (checkStatusAndLogFailure(status, methodStr)) {
                        networkIdList.value = networkIds;
                    }
                });
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            }
            return networkIdList.value;
        }
    }

    /**
     * Set WPS device name.
     *
     * @param ifaceName Name of the interface.
     * @param name String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsDeviceName(@NonNull String ifaceName, String name) {
        synchronized (mLock) {
            final String methodStr = "setWpsDeviceName";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.setWpsDeviceName(name);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Set WPS device type.
     *
     * @param ifaceName Name of the interface.
     * @param typeStr Type specified as a string. Used format: <categ>-<OUI>-<subcateg>
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsDeviceType(@NonNull String ifaceName, String typeStr) {
        synchronized (mLock) {
            try {
                Matcher match = WPS_DEVICE_TYPE_PATTERN.matcher(typeStr);
                if (!match.find() || match.groupCount() != 3) {
                    Log.e(TAG, "Malformed WPS device type " + typeStr);
                    return false;
                }
                short categ = Short.parseShort(match.group(1));
                byte[] oui = NativeUtil.hexStringToByteArray(match.group(2));
                short subCateg = Short.parseShort(match.group(3));

                byte[] bytes = new byte[8];
                ByteBuffer byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
                byteBuffer.putShort(categ);
                byteBuffer.put(oui);
                byteBuffer.putShort(subCateg);
                return setWpsDeviceType(ifaceName, bytes);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + typeStr, e);
                return false;
            }
        }
    }

    private boolean setWpsDeviceType(@NonNull String ifaceName, byte[/* 8 */] type) {
        synchronized (mLock) {
            final String methodStr = "setWpsDeviceType";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.setWpsDeviceType(type);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Set WPS manufacturer.
     *
     * @param ifaceName Name of the interface.
     * @param manufacturer String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsManufacturer(@NonNull String ifaceName, String manufacturer) {
        synchronized (mLock) {
            final String methodStr = "setWpsManufacturer";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.setWpsManufacturer(manufacturer);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Set WPS model name.
     *
     * @param ifaceName Name of the interface.
     * @param modelName String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsModelName(@NonNull String ifaceName, String modelName) {
        synchronized (mLock) {
            final String methodStr = "setWpsModelName";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.setWpsModelName(modelName);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Set WPS model number.
     *
     * @param ifaceName Name of the interface.
     * @param modelNumber String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsModelNumber(@NonNull String ifaceName, String modelNumber) {
        synchronized (mLock) {
            final String methodStr = "setWpsModelNumber";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.setWpsModelNumber(modelNumber);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Set WPS serial number.
     *
     * @param ifaceName Name of the interface.
     * @param serialNumber String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsSerialNumber(@NonNull String ifaceName, String serialNumber) {
        synchronized (mLock) {
            final String methodStr = "setWpsSerialNumber";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.setWpsSerialNumber(serialNumber);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Set WPS config methods
     *
     * @param ifaceName Name of the interface.
     * @param configMethodsStr List of config methods.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsConfigMethods(@NonNull String ifaceName, String configMethodsStr) {
        synchronized (mLock) {
            short configMethodsMask = 0;
            String[] configMethodsStrArr = configMethodsStr.split("\\s+");
            for (int i = 0; i < configMethodsStrArr.length; i++) {
                configMethodsMask |= stringToWpsConfigMethod(configMethodsStrArr[i]);
            }
            return setWpsConfigMethods(ifaceName, configMethodsMask);
        }
    }

    private boolean setWpsConfigMethods(@NonNull String ifaceName, short configMethods) {
        synchronized (mLock) {
            final String methodStr = "setWpsConfigMethods";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.setWpsConfigMethods(configMethods);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Trigger a reassociation even if the iface is currently connected.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean reassociate(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "reassociate";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.reassociate();
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Trigger a reconnection if the iface is disconnected.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean reconnect(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "reconnect";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.reconnect();
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Trigger a disconnection from the currently connected network.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean disconnect(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "disconnect";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.disconnect();
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Enable or disable power save mode.
     *
     * @param ifaceName Name of the interface.
     * @param enable true to enable, false to disable.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setPowerSave(@NonNull String ifaceName, boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setPowerSave";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.setPowerSave(enable);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Initiate TDLS discover with the specified AP.
     *
     * @param ifaceName Name of the interface.
     * @param macAddress MAC Address of the AP.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateTdlsDiscover(@NonNull String ifaceName, String macAddress) {
        synchronized (mLock) {
            try {
                return initiateTdlsDiscover(
                        ifaceName, NativeUtil.macAddressToByteArray(macAddress));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + macAddress, e);
                return false;
            }
        }
    }
    /** See ISupplicantStaIface.hal for documentation */
    private boolean initiateTdlsDiscover(@NonNull String ifaceName, byte[/* 6 */] macAddress) {
        synchronized (mLock) {
            final String methodStr = "initiateTdlsDiscover";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.initiateTdlsDiscover(macAddress);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Initiate TDLS setup with the specified AP.
     *
     * @param ifaceName Name of the interface.
     * @param macAddress MAC Address of the AP.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateTdlsSetup(@NonNull String ifaceName, String macAddress) {
        synchronized (mLock) {
            try {
                return initiateTdlsSetup(ifaceName, NativeUtil.macAddressToByteArray(macAddress));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + macAddress, e);
                return false;
            }
        }
    }
    /** See ISupplicantStaIface.hal for documentation */
    private boolean initiateTdlsSetup(@NonNull String ifaceName, byte[/* 6 */] macAddress) {
        synchronized (mLock) {
            final String methodStr = "initiateTdlsSetup";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.initiateTdlsSetup(macAddress);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Initiate TDLS teardown with the specified AP.
     * @param ifaceName Name of the interface.
     * @param macAddress MAC Address of the AP.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateTdlsTeardown(@NonNull String ifaceName, String macAddress) {
        synchronized (mLock) {
            try {
                return initiateTdlsTeardown(
                        ifaceName, NativeUtil.macAddressToByteArray(macAddress));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + macAddress, e);
                return false;
            }
        }
    }

    /** See ISupplicantStaIface.hal for documentation */
    private boolean initiateTdlsTeardown(@NonNull String ifaceName, byte[/* 6 */] macAddress) {
        synchronized (mLock) {
            final String methodStr = "initiateTdlsTeardown";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.initiateTdlsTeardown(macAddress);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Request the specified ANQP elements |elements| from the specified AP |bssid|.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the AP
     * @param infoElements ANQP elements to be queried. Refer to ISupplicantStaIface.AnqpInfoId.
     * @param hs20SubTypes HS subtypes to be queried. Refer to ISupplicantStaIface.Hs20AnqpSubTypes.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateAnqpQuery(@NonNull String ifaceName, String bssid,
            ArrayList<Short> infoElements,
            ArrayList<Integer> hs20SubTypes) {
        synchronized (mLock) {
            try {
                return initiateAnqpQuery(
                        ifaceName,
                        NativeUtil.macAddressToByteArray(bssid), infoElements, hs20SubTypes);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + bssid, e);
                return false;
            }
        }
    }

    /** See ISupplicantStaIface.hal for documentation */
    private boolean initiateAnqpQuery(@NonNull String ifaceName, byte[/* 6 */] macAddress,
            java.util.ArrayList<Short> infoElements, java.util.ArrayList<Integer> subTypes) {
        synchronized (mLock) {
            final String methodStr = "initiateAnqpQuery";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.initiateAnqpQuery(
                        macAddress, infoElements, subTypes);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Request Venue URL ANQP element from the specified AP |bssid|.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the AP
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateVenueUrlAnqpQuery(@NonNull String ifaceName, String bssid) {
        synchronized (mLock) {
            try {
                return initiateVenueUrlAnqpQuery(
                        ifaceName, NativeUtil.macAddressToByteArray(bssid));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + bssid, e);
                return false;
            }
        }
    }

    /** See ISupplicantStaIface.hal for documentation */
    private boolean initiateVenueUrlAnqpQuery(@NonNull String ifaceName, byte[/* 6 */] macAddress) {
        synchronized (mLock) {
            final String methodStr = "initiateVenueUrlAnqpQuery";
            if (!isV1_4()) {
                Log.e(TAG, "Method " + methodStr + " is not supported in existing HAL");
                return false;
            }
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            // Get a v1.4 supplicant STA Interface
            android.hardware.wifi.supplicant.V1_4.ISupplicantStaIface staIfaceV14 =
                    getStaIfaceMockableV1_4(iface);

            if (staIfaceV14 == null) {
                Log.e(TAG, methodStr
                        + ": SupplicantStaIface is null, cannot initiate Venue URL ANQP request");
                return false;
            }
            try {
                android.hardware.wifi.supplicant.V1_4.SupplicantStatus status =
                        staIfaceV14.initiateVenueUrlAnqpQuery(macAddress);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Request the specified ANQP ICON from the specified AP |bssid|.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the AP
     * @param fileName Name of the file to request.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateHs20IconQuery(@NonNull String ifaceName, String bssid, String fileName) {
        synchronized (mLock) {
            try {
                return initiateHs20IconQuery(
                        ifaceName, NativeUtil.macAddressToByteArray(bssid), fileName);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + bssid, e);
                return false;
            }
        }
    }

    /** See ISupplicantStaIface.hal for documentation */
    private boolean initiateHs20IconQuery(@NonNull String ifaceName,
            byte[/* 6 */] macAddress, String fileName) {
        synchronized (mLock) {
            final String methodStr = "initiateHs20IconQuery";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.initiateHs20IconQuery(macAddress, fileName);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Makes a callback to HIDL to getMacAddress from supplicant
     *
     * @param ifaceName Name of the interface.
     * @return string containing the MAC address, or null on a failed call
     */
    public String getMacAddress(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "getMacAddress";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return null;
            Mutable<String> gotMac = new Mutable<>();
            try {
                iface.getMacAddress((SupplicantStatus status,
                        byte[/* 6 */] macAddr) -> {
                    if (checkStatusAndLogFailure(status, methodStr)) {
                        gotMac.value = NativeUtil.macAddressFromByteArray(macAddr);
                    }
                });
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            }
            return gotMac.value;
        }
    }

    /**
     * Start using the added RX filters.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startRxFilter(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "startRxFilter";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.startRxFilter();
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Stop using the added RX filters.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean stopRxFilter(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "stopRxFilter";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.stopRxFilter();
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Add an RX filter.
     *
     * @param ifaceName Name of the interface.
     * @param type one of {@link WifiNative#RX_FILTER_TYPE_V4_MULTICAST}
     *        {@link WifiNative#RX_FILTER_TYPE_V6_MULTICAST} values.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean addRxFilter(@NonNull String ifaceName, int type) {
        synchronized (mLock) {
            byte halType;
            switch (type) {
                case WifiNative.RX_FILTER_TYPE_V4_MULTICAST:
                    halType = ISupplicantStaIface.RxFilterType.V4_MULTICAST;
                    break;
                case WifiNative.RX_FILTER_TYPE_V6_MULTICAST:
                    halType = ISupplicantStaIface.RxFilterType.V6_MULTICAST;
                    break;
                default:
                    Log.e(TAG, "Invalid Rx Filter type: " + type);
                    return false;
            }
            return addRxFilter(ifaceName, halType);
        }
    }

    private boolean addRxFilter(@NonNull String ifaceName, byte type) {
        synchronized (mLock) {
            final String methodStr = "addRxFilter";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.addRxFilter(type);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Remove an RX filter.
     *
     * @param ifaceName Name of the interface.
     * @param type one of {@link WifiNative#RX_FILTER_TYPE_V4_MULTICAST}
     *        {@link WifiNative#RX_FILTER_TYPE_V6_MULTICAST} values.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean removeRxFilter(@NonNull String ifaceName, int type) {
        synchronized (mLock) {
            byte halType;
            switch (type) {
                case WifiNative.RX_FILTER_TYPE_V4_MULTICAST:
                    halType = ISupplicantStaIface.RxFilterType.V4_MULTICAST;
                    break;
                case WifiNative.RX_FILTER_TYPE_V6_MULTICAST:
                    halType = ISupplicantStaIface.RxFilterType.V6_MULTICAST;
                    break;
                default:
                    Log.e(TAG, "Invalid Rx Filter type: " + type);
                    return false;
            }
            return removeRxFilter(ifaceName, halType);
        }
    }

    private boolean removeRxFilter(@NonNull String ifaceName, byte type) {
        synchronized (mLock) {
            final String methodStr = "removeRxFilter";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.removeRxFilter(type);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Set Bt co existense mode.
     *
     * @param ifaceName Name of the interface.
     * @param mode one of the above {@link WifiNative#BLUETOOTH_COEXISTENCE_MODE_DISABLED},
     *             {@link WifiNative#BLUETOOTH_COEXISTENCE_MODE_ENABLED} or
     *             {@link WifiNative#BLUETOOTH_COEXISTENCE_MODE_SENSE}.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setBtCoexistenceMode(@NonNull String ifaceName, int mode) {
        synchronized (mLock) {
            byte halMode;
            switch (mode) {
                case WifiNative.BLUETOOTH_COEXISTENCE_MODE_ENABLED:
                    halMode = ISupplicantStaIface.BtCoexistenceMode.ENABLED;
                    break;
                case WifiNative.BLUETOOTH_COEXISTENCE_MODE_DISABLED:
                    halMode = ISupplicantStaIface.BtCoexistenceMode.DISABLED;
                    break;
                case WifiNative.BLUETOOTH_COEXISTENCE_MODE_SENSE:
                    halMode = ISupplicantStaIface.BtCoexistenceMode.SENSE;
                    break;
                default:
                    Log.e(TAG, "Invalid Bt Coex mode: " + mode);
                    return false;
            }
            return setBtCoexistenceMode(ifaceName, halMode);
        }
    }

    private boolean setBtCoexistenceMode(@NonNull String ifaceName, byte mode) {
        synchronized (mLock) {
            final String methodStr = "setBtCoexistenceMode";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.setBtCoexistenceMode(mode);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /** Enable or disable BT coexistence mode.
     *
     * @param ifaceName Name of the interface.
     * @param enable true to enable, false to disable.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setBtCoexistenceScanModeEnabled(@NonNull String ifaceName, boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setBtCoexistenceScanModeEnabled";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status =
                        iface.setBtCoexistenceScanModeEnabled(enable);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Enable or disable suspend mode optimizations.
     *
     * @param ifaceName Name of the interface.
     * @param enable true to enable, false otherwise.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setSuspendModeEnabled(@NonNull String ifaceName, boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setSuspendModeEnabled";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.setSuspendModeEnabled(enable);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Set country code.
     *
     * @param ifaceName Name of the interface.
     * @param codeStr 2 byte ASCII string. For ex: US, CA.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setCountryCode(@NonNull String ifaceName, String codeStr) {
        synchronized (mLock) {
            if (TextUtils.isEmpty(codeStr)) return false;
            byte[] countryCodeBytes = NativeUtil.stringToByteArray(codeStr);
            if (countryCodeBytes.length != 2) return false;
            return setCountryCode(ifaceName, countryCodeBytes);
        }
    }

    /** See ISupplicantStaIface.hal for documentation */
    private boolean setCountryCode(@NonNull String ifaceName, byte[/* 2 */] code) {
        synchronized (mLock) {
            final String methodStr = "setCountryCode";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.setCountryCode(code);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Flush all previously configured HLPs.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean flushAllHlp(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "filsHlpFlushRequest";
            if (isV1_3()) {
                ISupplicantStaIface iface =
                        checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
                if (iface == null) {
                    return false;
                }

                // Get a v1.3 supplicant STA Interface
                android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface staIfaceV13 =
                        getStaIfaceMockableV1_3(iface);

                if (staIfaceV13 == null) {
                    Log.e(TAG, methodStr
                            + ": ISupplicantStaIface is null, cannot flushAllHlp");
                    return false;
                }
                try {
                    SupplicantStatus status = staIfaceV13.filsHlpFlushRequest();
                    return checkStatusAndLogFailure(status, methodStr);
                } catch (RemoteException e) {
                    handleRemoteException(e, methodStr);
                    return false;
                }
            } else {
                Log.e(TAG, "Method " + methodStr + " is not supported in existing HAL");
                return false;
            }
        }
    }

    /**
     * Set FILS HLP packet.
     *
     * @param ifaceName Name of the interface.
     * @param dst Destination MAC address.
     * @param hlpPacket Hlp Packet data in hex.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean addHlpReq(@NonNull String ifaceName, byte [] dst, byte [] hlpPacket) {
        synchronized (mLock) {
            final String methodStr = "filsHlpAddRequest";
            if (isV1_3()) {
                ISupplicantStaIface iface =
                        checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
                if (iface == null) {
                    return false;
                }

                // Get a v1.3 supplicant STA Interface
                android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface staIfaceV13 =
                        getStaIfaceMockableV1_3(iface);

                if (staIfaceV13 == null) {
                    Log.e(TAG, methodStr
                            + ": ISupplicantStaIface is null, cannot addHlpReq");
                    return false;
                }
                try {
                    ArrayList<Byte> payload = NativeUtil.byteArrayToArrayList(hlpPacket);
                    SupplicantStatus status = staIfaceV13.filsHlpAddRequest(dst, payload);
                    return checkStatusAndLogFailure(status, methodStr);
                } catch (RemoteException e) {
                    handleRemoteException(e, methodStr);
                    return false;
                }
            } else {
                Log.e(TAG, "Method " + methodStr + " is not supported in existing HAL");
                return false;
            }
        }
    }


    /**
     * Start WPS pin registrar operation with the specified peer and pin.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr BSSID of the peer.
     * @param pin Pin to be used.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startWpsRegistrar(@NonNull String ifaceName, String bssidStr, String pin) {
        synchronized (mLock) {
            if (TextUtils.isEmpty(bssidStr) || TextUtils.isEmpty(pin)) return false;
            try {
                return startWpsRegistrar(
                        ifaceName, NativeUtil.macAddressToByteArray(bssidStr), pin);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + bssidStr, e);
                return false;
            }
        }
    }

    /** See ISupplicantStaIface.hal for documentation */
    private boolean startWpsRegistrar(@NonNull String ifaceName, byte[/* 6 */] bssid, String pin) {
        synchronized (mLock) {
            final String methodStr = "startWpsRegistrar";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.startWpsRegistrar(bssid, pin);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Start WPS pin display operation with the specified peer.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr BSSID of the peer. Use empty bssid to indicate wildcard.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startWpsPbc(@NonNull String ifaceName, String bssidStr) {
        synchronized (mLock) {
            try {
                return startWpsPbc(ifaceName, NativeUtil.macAddressToByteArray(bssidStr));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + bssidStr, e);
                return false;
            }
        }
    }

    /** See ISupplicantStaIface.hal for documentation */
    private boolean startWpsPbc(@NonNull String ifaceName, byte[/* 6 */] bssid) {
        synchronized (mLock) {
            final String methodStr = "startWpsPbc";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.startWpsPbc(bssid);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Start WPS pin keypad operation with the specified pin.
     *
     * @param ifaceName Name of the interface.
     * @param pin Pin to be used.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startWpsPinKeypad(@NonNull String ifaceName, String pin) {
        if (TextUtils.isEmpty(pin)) return false;
        synchronized (mLock) {
            final String methodStr = "startWpsPinKeypad";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.startWpsPinKeypad(pin);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Start WPS pin display operation with the specified peer.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr BSSID of the peer. Use empty bssid to indicate wildcard.
     * @return new pin generated on success, null otherwise.
     */
    public String startWpsPinDisplay(@NonNull String ifaceName, String bssidStr) {
        synchronized (mLock) {
            try {
                return startWpsPinDisplay(ifaceName, NativeUtil.macAddressToByteArray(bssidStr));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + bssidStr, e);
                return null;
            }
        }
    }

    /** See ISupplicantStaIface.hal for documentation */
    private String startWpsPinDisplay(@NonNull String ifaceName, byte[/* 6 */] bssid) {
        synchronized (mLock) {
            final String methodStr = "startWpsPinDisplay";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return null;
            final Mutable<String> gotPin = new Mutable<>();
            try {
                iface.startWpsPinDisplay(bssid,
                        (SupplicantStatus status, String pin) -> {
                            if (checkStatusAndLogFailure(status, methodStr)) {
                                gotPin.value = pin;
                            }
                        });
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            }
            return gotPin.value;
        }
    }

    /**
     * Cancels any ongoing WPS requests.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean cancelWps(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "cancelWps";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.cancelWps();
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Sets whether to use external sim for SIM/USIM processing.
     *
     * @param ifaceName Name of the interface.
     * @param useExternalSim true to enable, false otherwise.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setExternalSim(@NonNull String ifaceName, boolean useExternalSim) {
        synchronized (mLock) {
            final String methodStr = "setExternalSim";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.setExternalSim(useExternalSim);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /** See ISupplicant.hal for documentation */
    public boolean enableAutoReconnect(@NonNull String ifaceName, boolean enable) {
        synchronized (mLock) {
            final String methodStr = "enableAutoReconnect";
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) return false;
            try {
                SupplicantStatus status = iface.enableAutoReconnect(enable);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Set the debug log level for wpa_supplicant
     *
     * @param turnOnVerbose Whether to turn on verbose logging or not.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setLogLevel(boolean turnOnVerbose) {
        synchronized (mLock) {
            int logLevel = turnOnVerbose
                    ? ISupplicant.DebugLevel.DEBUG
                    : ISupplicant.DebugLevel.INFO;
            return setDebugParams(logLevel, false,
                    turnOnVerbose && mWifiGlobals.getShowKeyVerboseLoggingModeEnabled());
        }
    }

    /** See ISupplicant.hal for documentation */
    private boolean setDebugParams(int level, boolean showTimestamp, boolean showKeys) {
        synchronized (mLock) {
            final String methodStr = "setDebugParams";
            if (!checkSupplicantAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status =
                        mISupplicant.setDebugParams(level, showTimestamp, showKeys);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Set concurrency priority between P2P & STA operations.
     *
     * @param isStaHigherPriority Set to true to prefer STA over P2P during concurrency operations,
     *                            false otherwise.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setConcurrencyPriority(boolean isStaHigherPriority) {
        synchronized (mLock) {
            if (isStaHigherPriority) {
                return setConcurrencyPriority(IfaceType.STA);
            } else {
                return setConcurrencyPriority(IfaceType.P2P);
            }
        }
    }

    /** See ISupplicant.hal for documentation */
    private boolean setConcurrencyPriority(int type) {
        synchronized (mLock) {
            final String methodStr = "setConcurrencyPriority";
            if (!checkSupplicantAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mISupplicant.setConcurrencyPriority(type);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Returns false if Supplicant is null, and logs failure to call methodStr
     */
    private boolean checkSupplicantAndLogFailure(final String methodStr) {
        synchronized (mLock) {
            if (mISupplicant == null) {
                Log.e(TAG, "Can't call " + methodStr + ", ISupplicant is null");
                return false;
            }
            return true;
        }
    }

    /**
     * Returns false if SupplicantStaIface is null, and logs failure to call methodStr
     */
    private ISupplicantStaIface checkSupplicantStaIfaceAndLogFailure(
            @NonNull String ifaceName, final String methodStr) {
        synchronized (mLock) {
            ISupplicantStaIface iface = getStaIface(ifaceName);
            if (iface == null) {
                Log.e(TAG, "Can't call " + methodStr + ", ISupplicantStaIface is null for iface="
                        + ifaceName);
                return null;
            }
            return iface;
        }
    }

    /**
     * Returns false if SupplicantStaNetwork is null, and logs failure to call methodStr
     */
    private SupplicantStaNetworkHalHidlImpl checkSupplicantStaNetworkAndLogFailure(
            @NonNull String ifaceName, final String methodStr) {
        synchronized (mLock) {
            SupplicantStaNetworkHalHidlImpl networkHal = getCurrentNetworkRemoteHandle(ifaceName);
            if (networkHal == null) {
                Log.e(TAG, "Can't call " + methodStr + ", SupplicantStaNetwork is null");
                return null;
            }
            return networkHal;
        }
    }

    /**
     * Returns true if provided status code is SUCCESS, logs debug message and returns false
     * otherwise
     */
    private boolean checkStatusAndLogFailure(SupplicantStatus status,
            final String methodStr) {
        synchronized (mLock) {
            if (status == null || status.code != SupplicantStatusCode.SUCCESS) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + " failed: " + status);
                return false;
            } else {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "ISupplicantStaIface." + methodStr + " succeeded");
                }
                return true;
            }
        }
    }

    /**
     * Returns true if provided status code is SUCCESS, logs debug message and returns false
     * otherwise
     */
    private boolean checkStatusAndLogFailure(
            android.hardware.wifi.supplicant.V1_4.SupplicantStatus status,
            final String methodStr) {
        synchronized (mLock) {
            if (status == null
                    || status.code
                    != android.hardware.wifi.supplicant.V1_4.SupplicantStatusCode.SUCCESS) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + " failed: " + status);
                return false;
            } else {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "ISupplicantStaIface." + methodStr + " succeeded");
                }
                return true;
            }
        }
    }

    /**
     * Helper function to log callbacks.
     */
    protected void logCallback(final String methodStr) {
        synchronized (mLock) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "ISupplicantStaIfaceCallback." + methodStr + " received");
            }
        }
    }

    private void handleNoSuchElementException(NoSuchElementException e, String methodStr) {
        synchronized (mLock) {
            clearState();
            Log.e(TAG, "ISupplicantStaIface." + methodStr + " failed with exception", e);
        }
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        synchronized (mLock) {
            clearState();
            Log.e(TAG, "ISupplicantStaIface." + methodStr + " failed with exception", e);
        }
    }

    private void handleIllegalArgumentException(IllegalArgumentException e, String methodStr) {
        synchronized (mLock) {
            clearState();
            Log.e(TAG, "ISupplicantStaIface." + methodStr + " failed with exception", e);
        }
    }

    /**
     * Converts the Wps config method string to the equivalent enum value.
     */
    private static short stringToWpsConfigMethod(String configMethod) {
        switch (configMethod) {
            case "usba":
                return WpsConfigMethods.USBA;
            case "ethernet":
                return WpsConfigMethods.ETHERNET;
            case "label":
                return WpsConfigMethods.LABEL;
            case "display":
                return WpsConfigMethods.DISPLAY;
            case "int_nfc_token":
                return WpsConfigMethods.INT_NFC_TOKEN;
            case "ext_nfc_token":
                return WpsConfigMethods.EXT_NFC_TOKEN;
            case "nfc_interface":
                return WpsConfigMethods.NFC_INTERFACE;
            case "push_button":
                return WpsConfigMethods.PUSHBUTTON;
            case "keypad":
                return WpsConfigMethods.KEYPAD;
            case "virtual_push_button":
                return WpsConfigMethods.VIRT_PUSHBUTTON;
            case "physical_push_button":
                return WpsConfigMethods.PHY_PUSHBUTTON;
            case "p2ps":
                return WpsConfigMethods.P2PS;
            case "virtual_display":
                return WpsConfigMethods.VIRT_DISPLAY;
            case "physical_display":
                return WpsConfigMethods.PHY_DISPLAY;
            default:
                throw new IllegalArgumentException(
                        "Invalid WPS config method: " + configMethod);
        }
    }

    protected class SupplicantStaIfaceHalCallback extends SupplicantStaIfaceCallbackHidlImpl {
        SupplicantStaIfaceHalCallback(@NonNull String ifaceName) {
            super(SupplicantStaIfaceHalHidlImpl.this, ifaceName, new Object(), mWifiMonitor,
                    mSsidTranslator);
        }
    }

    protected class SupplicantStaIfaceHalCallbackV1_1 extends
            SupplicantStaIfaceCallbackHidlV1_1Impl {
        SupplicantStaIfaceHalCallbackV1_1(@NonNull String ifaceName) {
            super(SupplicantStaIfaceHalHidlImpl.this, ifaceName, new Object(), mWifiMonitor);
        }
    }

    protected class SupplicantStaIfaceHalCallbackV1_2 extends
            SupplicantStaIfaceCallbackHidlV1_2Impl {
        SupplicantStaIfaceHalCallbackV1_2(@NonNull String ifaceName) {
            super(SupplicantStaIfaceHalHidlImpl.this, ifaceName, mContext, mSsidTranslator);
        }
    }

    protected class SupplicantStaIfaceHalCallbackV1_3 extends
            SupplicantStaIfaceCallbackHidlV1_3Impl {
        SupplicantStaIfaceHalCallbackV1_3(@NonNull String ifaceName) {
            super(SupplicantStaIfaceHalHidlImpl.this, ifaceName, mWifiMonitor);
        }
    }

    protected class SupplicantStaIfaceHalCallbackV1_4 extends
            SupplicantStaIfaceCallbackHidlV1_4Impl {
        SupplicantStaIfaceHalCallbackV1_4(@NonNull String ifaceName) {
            super(SupplicantStaIfaceHalHidlImpl.this, ifaceName, new Object(), mWifiMonitor,
                    mSsidTranslator);
        }
    }

    protected void addPmkCacheEntry(
            String ifaceName,
            int networkId, long expirationTimeInSec, ArrayList<Byte> serializedEntry) {
        String macAddressStr = getMacAddress(ifaceName);
        try {
            if (!mPmkCacheManager.add(MacAddress.fromString(macAddressStr),
                    networkId, null, expirationTimeInSec, serializedEntry)) {
                Log.w(TAG, "Cannot add PMK cache for " + ifaceName);
            }
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Cannot add PMK cache: " + ex);
        }
    }

    protected void removePmkCacheEntry(int networkId) {
        mPmkCacheManager.remove(networkId);
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }

    private static void logi(String s) {
        Log.i(TAG, s);
    }

    private static void loge(String s) {
        Log.e(TAG, s);
    }

    /**
     * Returns a bitmask of advanced capabilities: WPA3 SAE/SUITE B and OWE
     * Bitmask used is:
     * - WIFI_FEATURE_WPA3_SAE
     * - WIFI_FEATURE_WPA3_SUITE_B
     * - WIFI_FEATURE_OWE
     *
     *  This is a v1.2+ HAL feature.
     *  On error, or if these features are not supported, 0 is returned.
     */
    public long getAdvancedCapabilities(@NonNull String ifaceName) {
        final String methodStr = "getAdvancedCapabilities";

        long advancedCapabilities = 0;
        int keyMgmtCapabilities = getKeyMgmtCapabilities(ifaceName);

        if ((keyMgmtCapabilities & android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork
                .KeyMgmtMask.SAE) != 0) {
            advancedCapabilities |= WIFI_FEATURE_WPA3_SAE;

            if (mVerboseLoggingEnabled) {
                Log.v(TAG, methodStr + ": SAE supported");
            }
        }

        if ((keyMgmtCapabilities & android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork
                .KeyMgmtMask.SUITE_B_192) != 0) {
            advancedCapabilities |= WIFI_FEATURE_WPA3_SUITE_B;

            if (mVerboseLoggingEnabled) {
                Log.v(TAG, methodStr + ": SUITE_B supported");
            }
        }

        if ((keyMgmtCapabilities & android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork
                .KeyMgmtMask.OWE) != 0) {
            advancedCapabilities |= WIFI_FEATURE_OWE;

            if (mVerboseLoggingEnabled) {
                Log.v(TAG, methodStr + ": OWE supported");
            }
        }

        if ((keyMgmtCapabilities & android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork
                .KeyMgmtMask.DPP) != 0) {
            advancedCapabilities |= WIFI_FEATURE_DPP;

            if (mVerboseLoggingEnabled) {
                Log.v(TAG, methodStr + ": DPP supported");
            }
            if (isV1_4()) {
                advancedCapabilities |= WIFI_FEATURE_DPP_ENROLLEE_RESPONDER;
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, methodStr + ": DPP ENROLLEE RESPONDER supported");
                }
            }
        }

        if (isV1_4()) {
            advancedCapabilities |= WIFI_FEATURE_PASSPOINT_TERMS_AND_CONDITIONS
                    | WIFI_FEATURE_DECORATED_IDENTITY;
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, methodStr + ": Passpoint T&C supported");
                Log.v(TAG, methodStr + ": RFC 7542 decorated identity supported");
            }
        }

        if ((keyMgmtCapabilities & android.hardware.wifi.supplicant.V1_3.ISupplicantStaNetwork
                .KeyMgmtMask.WAPI_PSK) != 0) {
            advancedCapabilities |= WIFI_FEATURE_WAPI;

            if (mVerboseLoggingEnabled) {
                Log.v(TAG, methodStr + ": WAPI supported");
            }
        }

        if ((keyMgmtCapabilities & android.hardware.wifi.supplicant.V1_3.ISupplicantStaNetwork
                .KeyMgmtMask.FILS_SHA256) != 0) {
            advancedCapabilities |= WIFI_FEATURE_FILS_SHA256;

            if (mVerboseLoggingEnabled) {
                Log.v(TAG, methodStr + ": FILS_SHA256 supported");
            }
        }
        if ((keyMgmtCapabilities & android.hardware.wifi.supplicant.V1_3.ISupplicantStaNetwork
                .KeyMgmtMask.FILS_SHA384) != 0) {
            advancedCapabilities |= WIFI_FEATURE_FILS_SHA384;

            if (mVerboseLoggingEnabled) {
                Log.v(TAG, methodStr + ": FILS_SHA384 supported");
            }
        }

        if (mVerboseLoggingEnabled) {
            Log.v(TAG, methodStr + ": Capability flags = " + keyMgmtCapabilities);
        }

        return advancedCapabilities;
    }

    private int getKeyMgmtCapabilities_1_3(@NonNull String ifaceName) {
        final String methodStr = "getKeyMgmtCapabilities_1_3";
        Mutable<Integer> keyMgmtMask = new Mutable<>(0);
        ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
        if (iface == null) {
            return 0;
        }

        // Get a v1.3 supplicant STA Interface
        android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface staIfaceV13 =
                getStaIfaceMockableV1_3(iface);
        if (staIfaceV13 == null) {
            Log.e(TAG, methodStr
                    + ": ISupplicantStaIface V1.3 is null, cannot get advanced capabilities");
            return 0;
        }

        try {
            // Support for new key management types; WAPI_PSK, WAPI_CERT
            // Requires HAL v1.3 or higher
            staIfaceV13.getKeyMgmtCapabilities_1_3(
                    (SupplicantStatus statusInternal, int keyMgmtMaskInternal) -> {
                        if (statusInternal.code == SupplicantStatusCode.SUCCESS) {
                            keyMgmtMask.value = keyMgmtMaskInternal;
                        }
                        checkStatusAndLogFailure(statusInternal, methodStr);
                    });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return keyMgmtMask.value;
    }

    private int getKeyMgmtCapabilities(@NonNull String ifaceName) {
        final String methodStr = "getKeyMgmtCapabilities";
        Mutable<Boolean> status = new Mutable<>(false);
        Mutable<Integer> keyMgmtMask = new Mutable<>(0);

        if (isV1_3()) {
            keyMgmtMask.value = getKeyMgmtCapabilities_1_3(ifaceName);
        } else if (isV1_2()) {
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return 0;
            }

            // Get a v1.2 supplicant STA Interface
            android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface staIfaceV12 =
                    getStaIfaceMockableV1_2(iface);

            if (staIfaceV12 == null) {
                Log.e(TAG, methodStr
                        + ": ISupplicantStaIface is null, cannot get advanced capabilities");
                return 0;
            }

            try {
                // Support for new key management types; SAE, SUITE_B, OWE
                // Requires HAL v1.2 or higher
                staIfaceV12.getKeyMgmtCapabilities(
                        (SupplicantStatus statusInternal, int keyMgmtMaskInternal) -> {
                            status.value = statusInternal.code == SupplicantStatusCode.SUCCESS;
                            if (status.value) {
                                keyMgmtMask.value = keyMgmtMaskInternal;
                            }
                            checkStatusAndLogFailure(statusInternal, methodStr);
                        });
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            }
        } else {
            Log.e(TAG, "Method " + methodStr + " is not supported in existing HAL");
        }

        // 0 is returned in case of an error
        return keyMgmtMask.value;
    }

    private Mutable<Integer> getWpaDriverCapabilities_1_4(@NonNull String ifaceName) {
        final String methodStr = "getWpaDriverCapabilities_1_4";
        Mutable<Integer> drvCapabilitiesMask = new Mutable<>(0);
        ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);

        if (null == iface) return drvCapabilitiesMask;

        android.hardware.wifi.supplicant.V1_4.ISupplicantStaIface staIfaceV14 =
                getStaIfaceMockableV1_4(iface);
        if (null == staIfaceV14) {
            Log.e(TAG, methodStr
                    + ": SupplicantStaIface is null, cannot get wpa driver features");
            return drvCapabilitiesMask;
        }

        try {
            staIfaceV14.getWpaDriverCapabilities_1_4(
                    (android.hardware.wifi.supplicant.V1_4.SupplicantStatus statusInternal,
                            int drvCapabilities) -> {
                        if (statusInternal.code
                                == android.hardware.wifi.supplicant.V1_4
                                .SupplicantStatusCode.SUCCESS) {
                            drvCapabilitiesMask.value = drvCapabilities;
                        }
                        checkStatusAndLogFailure(statusInternal, methodStr);
                    });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return drvCapabilitiesMask;
    }

    private Mutable<Integer> getWpaDriverCapabilities_1_3(@NonNull String ifaceName) {
        final String methodStr = "getWpaDriverCapabilities_1_3";
        Mutable<Integer> drvCapabilitiesMask = new Mutable<>(0);
        ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);

        if (null == iface) return drvCapabilitiesMask;

        android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface staIfaceV13 =
                getStaIfaceMockableV1_3(iface);
        if (null == staIfaceV13) {
            Log.e(TAG, methodStr
                    + ": SupplicantStaIface is null, cannot get wpa driver features");
            return drvCapabilitiesMask;
        }

        try {
            staIfaceV13.getWpaDriverCapabilities(
                    (SupplicantStatus statusInternal, int drvCapabilities) -> {
                        if (statusInternal.code == SupplicantStatusCode.SUCCESS) {
                            drvCapabilitiesMask.value = drvCapabilities;
                        }
                        checkStatusAndLogFailure(statusInternal, methodStr);
                    });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return drvCapabilitiesMask;
    }

    /**
     * Get the driver supported features through supplicant.
     *
     * @param ifaceName Name of the interface.
     * @return bitmask defined by WifiManager.WIFI_FEATURE_*.
     */
    public long getWpaDriverFeatureSet(@NonNull String ifaceName) {
        final String methodStr = "getWpaDriverFeatureSet";
        Mutable<Integer> drvCapabilitiesMask = new Mutable<>(0);
        long featureSet = 0;

        if (isV1_4()) {
            drvCapabilitiesMask = getWpaDriverCapabilities_1_4(ifaceName);
        } else if (isV1_3()) {
            drvCapabilitiesMask = getWpaDriverCapabilities_1_3(ifaceName);
        } else {
            Log.i(TAG, "Method " + methodStr + " is not supported in existing HAL");
            return 0;
        }

        if ((drvCapabilitiesMask.value & WpaDriverCapabilitiesMask.MBO) != 0) {
            featureSet |= WIFI_FEATURE_MBO;
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, methodStr + ": MBO supported");
            }
            if ((drvCapabilitiesMask.value
                    & WpaDriverCapabilitiesMask.OCE) != 0) {
                featureSet |= WIFI_FEATURE_OCE;
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, methodStr + ": OCE supported");
                }
            }
        }

        if ((drvCapabilitiesMask.value
                & android.hardware.wifi.supplicant.V1_4.WpaDriverCapabilitiesMask.SAE_PK) != 0) {
            featureSet |= WIFI_FEATURE_SAE_PK;
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, methodStr + ": SAE-PK supported");
            }
        }

        if ((drvCapabilitiesMask.value
                & android.hardware.wifi.supplicant.V1_4.WpaDriverCapabilitiesMask.WFD_R2) != 0) {
            featureSet |= WIFI_FEATURE_WFD_R2;
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, methodStr + ": WFD-R2 supported");
            }
        }

        return featureSet;
    }

    private @WifiStandard int getWifiStandard(int technology) {
        switch(technology) {
            case WifiTechnology.HE:
                return ScanResult.WIFI_STANDARD_11AX;
            case WifiTechnology.VHT:
                return ScanResult.WIFI_STANDARD_11AC;
            case WifiTechnology.HT:
                return ScanResult.WIFI_STANDARD_11N;
            case WifiTechnology.LEGACY:
                return ScanResult.WIFI_STANDARD_LEGACY;
            default:
                return ScanResult.WIFI_STANDARD_UNKNOWN;
        }
    }

    private int getChannelBandwidth(int channelBandwidth) {
        switch(channelBandwidth) {
            case WifiChannelWidthInMhz.WIDTH_20:
                return ScanResult.CHANNEL_WIDTH_20MHZ;
            case WifiChannelWidthInMhz.WIDTH_40:
                return ScanResult.CHANNEL_WIDTH_40MHZ;
            case WifiChannelWidthInMhz.WIDTH_80:
                return ScanResult.CHANNEL_WIDTH_80MHZ;
            case WifiChannelWidthInMhz.WIDTH_160:
                return ScanResult.CHANNEL_WIDTH_160MHZ;
            case WifiChannelWidthInMhz.WIDTH_80P80:
                return ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ;
            default:
                return ScanResult.CHANNEL_WIDTH_20MHZ;
        }
    }

    private int frameworkToHidlDppAkm(int dppAkm) {
        switch(dppAkm) {
            case SupplicantStaIfaceHal.DppAkm.PSK:
                return DppAkm.PSK;
            case SupplicantStaIfaceHal.DppAkm.PSK_SAE:
                return DppAkm.PSK_SAE;
            case SupplicantStaIfaceHal.DppAkm.SAE:
                return DppAkm.SAE;
            case SupplicantStaIfaceHal.DppAkm.DPP:
                return DppAkm.DPP;
            default:
                Log.e(TAG, "Invalid DppAkm received");
                return -1;
        }
    }

    private int frameworkToHidlDppCurve(int dppCurve) {
        switch(dppCurve) {
            case SupplicantStaIfaceHal.DppCurve.PRIME256V1:
                return DppCurve.PRIME256V1;
            case SupplicantStaIfaceHal.DppCurve.SECP384R1:
                return DppCurve.SECP384R1;
            case SupplicantStaIfaceHal.DppCurve.SECP521R1:
                return DppCurve.SECP521R1;
            case SupplicantStaIfaceHal.DppCurve.BRAINPOOLP256R1:
                return DppCurve.BRAINPOOLP256R1;
            case SupplicantStaIfaceHal.DppCurve.BRAINPOOLP384R1:
                return DppCurve.BRAINPOOLP384R1;
            case SupplicantStaIfaceHal.DppCurve.BRAINPOOLP512R1:
                return DppCurve.BRAINPOOLP512R1;
            default:
                Log.e(TAG, "Invalid DppAkm received");
                return -1;
        }
    }

    private int frameworkToHidlDppNetRole(int dppNetRole) {
        switch(dppNetRole) {
            case SupplicantStaIfaceHal.DppNetRole.STA:
                return DppNetRole.STA;
            case SupplicantStaIfaceHal.DppNetRole.AP:
                return DppNetRole.AP;
            default:
                Log.e(TAG, "Invalid DppNetRole received");
                return -1;
        }
    }

    /**
     * Returns signal poll results for all Wi-Fi links of the interface.
     *
     * @param ifaceName Name of the interface.
     * @return Signal poll results.
     */
    public WifiSignalPollResults getSignalPollResults(@NonNull String ifaceName) {
        /* Signal polling is not implemented for HIDL. */
        return null;
    }


    /**
     * Returns connection capabilities of the current network
     *
     *  This is a v1.3+ HAL feature.
     * @param ifaceName Name of the interface.
     * @return connection capabilities of the current network
     */
    public WifiNative.ConnectionCapabilities getConnectionCapabilities(@NonNull String ifaceName) {
        final String methodStr = "getConnectionCapabilities";
        WifiNative.ConnectionCapabilities capOut = new WifiNative.ConnectionCapabilities();
        ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
        if (iface == null) {
            return capOut;
        }
        if (isV1_4()) {
            return getConnectionCapabilities_1_4(iface);
        } else if (isV1_3()) {
            return getConnectionCapabilities_1_3(iface);
        } else {
            Log.e(TAG, "Method " + methodStr + " is not supported in existing HAL");
        }
        return capOut;
    }

    /**
     * Returns connection MLO links info
     *
     * @param ifaceName Name of the interface.
     * @return null since this method is not supported on Hidl
     */
    public WifiNative.ConnectionMloLinksInfo getConnectionMloLinksInfo(@NonNull String ifaceName) {
        return null;
    }

    private WifiNative.ConnectionCapabilities getConnectionCapabilities_1_3(
            @NonNull ISupplicantStaIface iface) {
        final String methodStr = "getConnectionCapabilities_1_3";
        WifiNative.ConnectionCapabilities capOut = new WifiNative.ConnectionCapabilities();

        // Get a v1.3 supplicant STA Interface
        android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface staIfaceV13 =
                getStaIfaceMockableV1_3(iface);

        if (staIfaceV13 == null) {
            Log.e(TAG, methodStr
                    + ": SupplicantStaIface is null, cannot get Connection Capabilities");
            return capOut;
        }

        try {
            staIfaceV13.getConnectionCapabilities(
                    (SupplicantStatus statusInternal, ConnectionCapabilities cap) -> {
                        if (statusInternal.code == SupplicantStatusCode.SUCCESS) {
                            capOut.wifiStandard = getWifiStandard(cap.technology);
                            capOut.channelBandwidth = getChannelBandwidth(
                                    cap.channelBandwidth);
                            capOut.maxNumberTxSpatialStreams = cap.maxNumberTxSpatialStreams;
                            capOut.maxNumberRxSpatialStreams = cap.maxNumberRxSpatialStreams;
                        }
                        checkStatusAndLogFailure(statusInternal, methodStr);
                    });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return capOut;
    }

    private WifiNative.ConnectionCapabilities getConnectionCapabilities_1_4(
            @NonNull ISupplicantStaIface iface) {
        final String methodStr = "getConnectionCapabilities_1_4";
        WifiNative.ConnectionCapabilities capOut = new WifiNative.ConnectionCapabilities();
        // Get a v1.4 supplicant STA Interface
        android.hardware.wifi.supplicant.V1_4.ISupplicantStaIface staIfaceV14 =
                getStaIfaceMockableV1_4(iface);

        if (staIfaceV14 == null) {
            Log.e(TAG, methodStr
                    + ": SupplicantStaIface is null, cannot get Connection Capabilities");
            return capOut;
        }

        try {
            staIfaceV14.getConnectionCapabilities_1_4(
                    (android.hardware.wifi.supplicant.V1_4.SupplicantStatus statusInternal,
                            android.hardware.wifi.supplicant.V1_4.ConnectionCapabilities cap)
                            -> {
                        if (statusInternal.code == SupplicantStatusCode.SUCCESS) {
                            capOut.wifiStandard = getWifiStandard(cap.V1_3.technology);
                            capOut.channelBandwidth = getChannelBandwidth(
                                    cap.V1_3.channelBandwidth);
                            capOut.is11bMode = (cap.legacyMode == LegacyMode.B_MODE);
                            capOut.maxNumberTxSpatialStreams = cap.V1_3.maxNumberTxSpatialStreams;
                            capOut.maxNumberRxSpatialStreams = cap.V1_3.maxNumberRxSpatialStreams;
                        }
                        checkStatusAndLogFailure(statusInternal, methodStr);
                    });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }
        return capOut;
    }
    /**
     * Adds a DPP peer URI to the URI list.
     *
     *  This is a v1.2+ HAL feature.
     *  Returns an ID to be used later to refer to this URI (>0).
     *  On error, or if these features are not supported, -1 is returned.
     */
    public int addDppPeerUri(@NonNull String ifaceName, @NonNull String uri) {
        final String methodStr = "addDppPeerUri";
        Mutable<Boolean> status = new Mutable<>(false);
        Mutable<Integer> bootstrapId = new Mutable<>(-1);

        if (!isV1_2()) {
            Log.e(TAG, "Method " + methodStr + " is not supported in existing HAL");
            return -1;
        }

        ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
        if (iface == null) {
            return -1;
        }

        // Get a v1.2 supplicant STA Interface
        android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface staIfaceV12 =
                getStaIfaceMockableV1_2(iface);

        if (staIfaceV12 == null) {
            Log.e(TAG, methodStr + ": ISupplicantStaIface is null");
            return -1;
        }

        try {
            // Support for DPP (Easy connect)
            // Requires HAL v1.2 or higher
            staIfaceV12.addDppPeerUri(uri,
                    (SupplicantStatus statusInternal, int bootstrapIdInternal) -> {
                        status.value = statusInternal.code == SupplicantStatusCode.SUCCESS;
                        if (status.value) {
                            bootstrapId.value = bootstrapIdInternal;
                        }
                        checkStatusAndLogFailure(statusInternal, methodStr);
                    });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return -1;
        }

        return bootstrapId.value;
    }

    /**
     * Removes a DPP URI to the URI list given an ID.
     *
     *  This is a v1.2+ HAL feature.
     *  Returns true when operation is successful
     *  On error, or if these features are not supported, false is returned.
     */
    public boolean removeDppUri(@NonNull String ifaceName, int bootstrapId)  {
        final String methodStr = "removeDppUri";

        if (!isV1_2()) {
            Log.e(TAG, "Method " + methodStr + " is not supported in existing HAL");
            return false;
        }

        ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
        if (iface == null) {
            return false;
        }

        // Get a v1.2 supplicant STA Interface
        android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface staIfaceV12 =
                getStaIfaceMockableV1_2(iface);

        if (staIfaceV12 == null) {
            Log.e(TAG, methodStr + ": ISupplicantStaIface is null");
            return false;
        }

        try {
            // Support for DPP (Easy connect)
            // Requires HAL v1.2 or higher
            SupplicantStatus status = staIfaceV12.removeDppUri(bootstrapId);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }

        return false;
    }

    /**
     * Stops/aborts DPP Initiator request
     *
     *  This is a v1.2+ HAL feature.
     *  Returns true when operation is successful
     *  On error, or if these features are not supported, false is returned.
     */
    public boolean stopDppInitiator(@NonNull String ifaceName)  {
        final String methodStr = "stopDppInitiator";

        if (!isV1_2()) {
            return false;
        }

        ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
        if (iface == null) {
            return false;
        }

        // Get a v1.2 supplicant STA Interface
        android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface staIfaceV12 =
                getStaIfaceMockableV1_2(iface);

        if (staIfaceV12 == null) {
            Log.e(TAG, methodStr + ": ISupplicantStaIface is null");
            return false;
        }

        try {
            // Support for DPP (Easy connect)
            // Requires HAL v1.2 or higher
            SupplicantStatus status = staIfaceV12.stopDppInitiator();
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }

        return false;
    }

    /**
     * Starts DPP Configurator-Initiator request
     *
     *  This is a v1.2+ HAL feature.
     *  Returns true when operation is successful
     *  On error, or if these features are not supported, false is returned.
     */
    public boolean startDppConfiguratorInitiator(@NonNull String ifaceName, int peerBootstrapId,
            int ownBootstrapId, @NonNull String ssid, String password, String psk,
            int netRole, int securityAkm, byte[] privEckey)  {
        final String methodStr = "startDppConfiguratorInitiator";

        if (!isV1_2()) {
            Log.e(TAG, "Method " + methodStr + " is not supported in existing HAL");
            return false;
        }

        ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
        if (iface == null) {
            return false;
        }

        // Get a v1.2 supplicant STA Interface
        android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface staIfaceV12 =
                getStaIfaceMockableV1_2(iface);

        if (staIfaceV12 == null) {
            Log.e(TAG, methodStr + ": ISupplicantStaIface is null");
            return false;
        }

        try {
            // Support for DPP (Easy connect)
            // Requires HAL v1.2 or higher
            SupplicantStatus status = staIfaceV12.startDppConfiguratorInitiator(peerBootstrapId,
                    ownBootstrapId, ssid, password != null ? password : "", psk != null ? psk : "",
                    frameworkToHidlDppNetRole(netRole), frameworkToHidlDppAkm(securityAkm));
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }

        return false;
    }

    /**
     * Starts DPP Enrollee-Initiator request
     *
     *  This is a v1.2+ HAL feature.
     *  Returns true when operation is successful
     *  On error, or if these features are not supported, false is returned.
     */
    public boolean startDppEnrolleeInitiator(@NonNull String ifaceName, int peerBootstrapId,
            int ownBootstrapId)  {
        final String methodStr = "startDppEnrolleeInitiator";

        if (!isV1_2()) {
            Log.e(TAG, "Method " + methodStr + " is not supported in existing HAL");
            return false;
        }

        ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
        if (iface == null) {
            return false;
        }

        // Get a v1.2 supplicant STA Interface
        android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface staIfaceV12 =
                getStaIfaceMockableV1_2(iface);

        if (staIfaceV12 == null) {
            Log.e(TAG, methodStr + ": ISupplicantStaIface is null");
            return false;
        }

        try {
            // Support for DPP (Easy connect)
            // Requires HAL v1.2 or higher
            SupplicantStatus status = staIfaceV12.startDppEnrolleeInitiator(peerBootstrapId,
                    ownBootstrapId);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }

        return false;
    }

    /**
     * Generate a DPP QR code based boot strap info
     *
     *  This is a v1.4+ HAL feature.
     *  Returns DppResponderBootstrapInfo;
     */
    public WifiNative.DppBootstrapQrCodeInfo generateDppBootstrapInfoForResponder(
            @NonNull String ifaceName, String macAddress, @NonNull String deviceInfo,
            int dppCurve) {
        final String methodStr = "generateDppBootstrapInfoForResponder";
        Mutable<Boolean> status = new Mutable<>(false);
        WifiNative.DppBootstrapQrCodeInfo bootstrapInfoOut =
                new WifiNative.DppBootstrapQrCodeInfo();

        ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
        if (iface == null) {
            return bootstrapInfoOut;
        }

        // Get a v1.4 supplicant STA Interface
        android.hardware.wifi.supplicant.V1_4.ISupplicantStaIface staIfaceV14 =
                getStaIfaceMockableV1_4(iface);

        if (staIfaceV14 == null) {
            Log.e(TAG, methodStr + ": SupplicantStaIface V1.4 is null");
            return bootstrapInfoOut;
        }

        try {
            // Support for DPP Responder
            // Requires HAL v1.4 or higher
            staIfaceV14.generateDppBootstrapInfoForResponder(
                    NativeUtil.macAddressToByteArray(macAddress), deviceInfo,
                    frameworkToHidlDppCurve(dppCurve),
                    (android.hardware.wifi.supplicant.V1_4.SupplicantStatus statusInternal,
                            android.hardware.wifi.supplicant.V1_4
                                    .DppResponderBootstrapInfo info) -> {
                        if (statusInternal.code == SupplicantStatusCode.SUCCESS) {
                            bootstrapInfoOut.bootstrapId = info.bootstrapId;
                            bootstrapInfoOut.listenChannel = info.listenChannel;
                            bootstrapInfoOut.uri = info.uri;
                        }
                        checkStatusAndLogFailure(statusInternal, methodStr);
                    });
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return bootstrapInfoOut;
        }

        return bootstrapInfoOut;
    }

    /**
     * Starts DPP Enrollee-Responder request
     *
     *  This is a v1.4+ HAL feature.
     *  Returns true when operation is successful
     *  On error, or if these features are not supported, false is returned.
     */
    public boolean startDppEnrolleeResponder(@NonNull String ifaceName, int listenChannel) {
        final String methodStr = "startDppEnrolleeResponder";

        ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
        if (iface == null) {
            return false;
        }

        // Get a v1.4 supplicant STA Interface
        android.hardware.wifi.supplicant.V1_4.ISupplicantStaIface staIfaceV14 =
                getStaIfaceMockableV1_4(iface);

        if (staIfaceV14 == null) {
            Log.e(TAG, methodStr + ": ISupplicantStaIface V1.4 is null");
            return false;
        }

        try {
            // Support for DPP Responder
            // Requires HAL v1.4 or higher
            android.hardware.wifi.supplicant.V1_4.SupplicantStatus status =
                    staIfaceV14.startDppEnrolleeResponder(listenChannel);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }

        return false;
    }

    /**
     * Stops/aborts DPP Responder request.
     *
     *  This is a v1.4+ HAL feature.
     *  Returns true when operation is successful
     *  On error, or if these features are not supported, false is returned.
     */
    public boolean stopDppResponder(@NonNull String ifaceName, int ownBootstrapId)  {
        final String methodStr = "stopDppResponder";

        ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
        if (iface == null) {
            return false;
        }

        // Get a v1.4 supplicant STA Interface
        android.hardware.wifi.supplicant.V1_4.ISupplicantStaIface staIfaceV14 =
                getStaIfaceMockableV1_4(iface);

        if (staIfaceV14 == null) {
            Log.e(TAG, methodStr + ": ISupplicantStaIface V1.4 is null");
            return false;
        }

        try {
            // Support for DPP Responder
            // Requires HAL v1.4 or higher
            android.hardware.wifi.supplicant.V1_4.SupplicantStatus status =
                    staIfaceV14.stopDppResponder(ownBootstrapId);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        }

        return false;
    }


    /**
     * Register callbacks for DPP events.
     *
     * @param dppCallback DPP callback object.
     */
    public void registerDppCallback(DppEventCallback dppCallback) {
        mDppCallback = dppCallback;
    }

    protected DppEventCallback getDppCallback() {
        return mDppCallback;
    }

    /**
     * Set MBO cellular data availability.
     *
     * @param ifaceName Name of the interface.
     * @param available true means cellular data available, false otherwise.
     * @return None.
     */
    public boolean setMboCellularDataStatus(@NonNull String ifaceName, boolean available) {
        final String methodStr = "setMboCellularDataStatus";

        if (isV1_3()) {
            ISupplicantStaIface iface = checkSupplicantStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }

            // Get a v1.3 supplicant STA Interface
            android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface staIfaceV13 =
                    getStaIfaceMockableV1_3(iface);
            if (staIfaceV13 == null) {
                Log.e(TAG, methodStr
                        + ": SupplicantStaIface is null, cannot update cell status");
                return false;
            }

            try {
                SupplicantStatus status = staIfaceV13.setMboCellularDataStatus(available);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            }
        } else {
            Log.e(TAG, "Method " + methodStr + " is not supported in existing HAL");
            return false;
        }

        return false;
    }

    /**
     * Check if we've roamed to a linked network and make the linked network the current network
     * if we have.
     *
     * @param ifaceName Name of the interface.
     * @param newNetworkId Network id of the new network we've roamed to. If fromFramework is
     *                     {@code true}, this will be a framework network id. Otherwise, this will
     *                     be a remote network id.
     * @param fromFramework {@code true} if the network id is a framework network id, {@code false}
                            if the network id is a remote network id.
     * @return true if we've roamed to a linked network, false if not.
     */
    public boolean updateOnLinkedNetworkRoaming(
            @NonNull String ifaceName, int newNetworkId, boolean fromFramework) {
        synchronized (mLock) {
            List<Pair<SupplicantStaNetworkHalHidlImpl, WifiConfiguration>> linkedNetworkHandles =
                    mLinkedNetworkLocalAndRemoteConfigs.get(ifaceName);
            SupplicantStaNetworkHalHidlImpl currentHandle =
                    getCurrentNetworkRemoteHandle(ifaceName);
            WifiConfiguration currentConfig = getCurrentNetworkLocalConfig(ifaceName);
            if (linkedNetworkHandles == null || currentHandle == null || currentConfig == null) {
                return false;
            }
            if (fromFramework ? currentConfig.networkId == newNetworkId
                    : currentHandle.getNetworkId() == newNetworkId) {
                return false;
            }
            for (Pair<SupplicantStaNetworkHalHidlImpl, WifiConfiguration> pair
                    : linkedNetworkHandles) {
                if (fromFramework ? pair.second.networkId == newNetworkId
                        : pair.first.getNetworkId() == newNetworkId) {
                    Log.i(TAG, "Roamed to linked network, "
                            + "make linked network as current network");
                    mCurrentNetworkRemoteHandles.put(ifaceName, pair.first);
                    mCurrentNetworkLocalConfigs.put(ifaceName, pair.second);
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Updates the linked networks for the current network and sends them to the supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param networkId network id of the network to link the configurations to.
     * @param linkedConfigurations Map of config profile key to config for linking.
     * @return true if networks were successfully linked, false otherwise.
     */
    public boolean updateLinkedNetworks(@NonNull String ifaceName, int networkId,
            Map<String, WifiConfiguration> linkedConfigurations) {
        synchronized (mLock) {
            WifiConfiguration currentConfig = getCurrentNetworkLocalConfig(ifaceName);
            SupplicantStaNetworkHalHidlImpl currentHandle =
                    getCurrentNetworkRemoteHandle(ifaceName);

            if (currentConfig == null || currentHandle == null) {
                Log.e(TAG, "current network not configured yet.");
                return false;
            }

            if (networkId != currentConfig.networkId) {
                Log.e(TAG, "current config network id is not matching");
                return false;
            }

            final int remoteNetworkId = currentHandle.getNetworkId();
            if (remoteNetworkId == -1) {
                Log.e(TAG, "current handle getNetworkId failed");
                return false;
            }

            if (!removeAllNetworksExcept(ifaceName, remoteNetworkId)) {
                Log.e(TAG, "couldn't remove non-current supplicant networks");
                return false;
            }

            mLinkedNetworkLocalAndRemoteConfigs.remove(ifaceName);

            if (linkedConfigurations == null || linkedConfigurations.size() == 0) {
                Log.i(TAG, "cleared linked networks");
                return true;
            }

            List<Pair<SupplicantStaNetworkHalHidlImpl, WifiConfiguration>> linkedNetworkHandles =
                    new ArrayList<>();
            linkedNetworkHandles.add(new Pair(currentHandle, currentConfig));
            for (String linkedNetwork : linkedConfigurations.keySet()) {
                Log.i(TAG, "add linked network: " + linkedNetwork);
                Pair<SupplicantStaNetworkHalHidlImpl, WifiConfiguration> pair =
                        addNetworkAndSaveConfig(ifaceName, linkedConfigurations.get(linkedNetwork));
                if (pair == null) {
                    Log.e(TAG, "failed to add/save linked network: " + linkedNetwork);
                    return false;
                }
                pair.first.enable(true);
                linkedNetworkHandles.add(pair);
            }

            mLinkedNetworkLocalAndRemoteConfigs.put(ifaceName, linkedNetworkHandles);

            return true;
        }
    }

    /**
     * Remove all networks except the supplied network ID from supplicant
     *
     * @param ifaceName Name of the interface
     * @param networkId network id to keep
     */
    private boolean removeAllNetworksExcept(@NonNull String ifaceName, int networkId) {
        synchronized (mLock) {
            List<Integer> networks = listNetworks(ifaceName);
            if (networks == null) {
                Log.e(TAG, "removeAllNetworksExcept failed, got null networks");
                return false;
            }
            for (int id : networks) {
                if (networkId == id) {
                    continue;
                }
                if (!removeNetwork(ifaceName, id)) {
                    Log.e(TAG, "removeAllNetworksExcept failed to remove network: " + id);
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Gets the security params of the current network associated with this interface
     *
     * @param ifaceName Name of the interface
     * @return Security params of the current network associated with the interface
     */
    public SecurityParams getCurrentNetworkSecurityParams(@NonNull String ifaceName) {
        WifiConfiguration currentConfig = getCurrentNetworkLocalConfig(ifaceName);

        if (currentConfig == null) {
            return null;
        }

        return currentConfig.getNetworkSelectionStatus().getCandidateSecurityParams();
    }
}
