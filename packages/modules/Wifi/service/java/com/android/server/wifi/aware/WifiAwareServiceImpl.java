/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.aware;

import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_256;

import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_AWARE_VERBOSE_LOGGING_ENABLED;

import android.Manifest;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.IBooleanListener;
import android.net.wifi.IIntegerListener;
import android.net.wifi.IListListener;
import android.net.wifi.WifiManager;
import android.net.wifi.aware.AwareParams;
import android.net.wifi.aware.AwareResources;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.IWifiAwareDiscoverySessionCallback;
import android.net.wifi.aware.IWifiAwareEventCallback;
import android.net.wifi.aware.IWifiAwareMacAddressProvider;
import android.net.wifi.aware.IWifiAwareManager;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.BuildProperties;
import com.android.server.wifi.Clock;
import com.android.server.wifi.FrameworkFacade;
import com.android.server.wifi.InterfaceConflictManager;
import com.android.server.wifi.RunnerHandler;
import com.android.server.wifi.SystemBuildProperties;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiSettingsConfigStore;
import com.android.server.wifi.WifiThreadRunner;
import com.android.server.wifi.hal.WifiNanIface.NanStatusCode;
import com.android.server.wifi.util.NetdWrapper;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.server.wifi.util.WifiPermissionsWrapper;
import com.android.wifi.resources.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Implementation of the IWifiAwareManager AIDL interface. Performs validity
 * (permission and clientID-UID mapping) checks and delegates execution to the
 * WifiAwareStateManager singleton handler. Limited state to feedback which has to
 * be provided instantly: client and session IDs.
 */
public class WifiAwareServiceImpl extends IWifiAwareManager.Stub {
    private static final String TAG = "WifiAwareService";
    private boolean mVerboseLoggingEnabled = false;
    private boolean mVerboseHalLoggingEnabled = false;

    private WifiPermissionsUtil mWifiPermissionsUtil;
    private WifiAwareStateManager mStateManager;
    private WifiAwareNativeManager mWifiAwareNativeManager;
    private WifiAwareNativeApi mWifiAwareNativeApi;
    private WifiAwareNativeCallback mWifiAwareNativeCallback;
    private WifiAwareShellCommand mShellCommand;
    private Handler mHandler;

    private final Object mLock = new Object();
    private final SparseArray<IBinder.DeathRecipient> mDeathRecipientsByClientId =
            new SparseArray<>();
    private int mNextClientId = 1;
    private final SparseIntArray mUidByClientId = new SparseIntArray();
    private final Context mContext;
    private final BuildProperties mBuildProperties;
    private final FrameworkFacade mFrameworkFacade;

    public WifiAwareServiceImpl(Context context) {
        mContext = context;
        mBuildProperties = new SystemBuildProperties();
        mFrameworkFacade = new FrameworkFacade();
    }

    /**
     * Proxy for the final native call of the parent class. Enables mocking of
     * the function.
     */
    public int getMockableCallingUid() {
        return getCallingUid();
    }

    /**
     * Start the service: allocate a new thread (for now), start the handlers of
     * the components of the service.
     */
    public void start(HandlerThread handlerThread, WifiAwareStateManager awareStateManager,
            WifiAwareShellCommand awareShellCommand, WifiAwareMetrics awareMetrics,
            WifiPermissionsUtil wifiPermissionsUtil, WifiPermissionsWrapper permissionsWrapper,
            WifiSettingsConfigStore settingsConfigStore,
            WifiAwareNativeManager wifiAwareNativeManager, WifiAwareNativeApi wifiAwareNativeApi,
            WifiAwareNativeCallback wifiAwareNativeCallback, NetdWrapper netdWrapper,
            InterfaceConflictManager interfaceConflictManager) {
        Log.i(TAG, "Starting Wi-Fi Aware service");

        mWifiPermissionsUtil = wifiPermissionsUtil;
        mStateManager = awareStateManager;
        mShellCommand = awareShellCommand;
        mHandler = new RunnerHandler(handlerThread.getLooper(), mContext.getResources()
                .getInteger(R.integer.config_wifiConfigurationWifiRunnerThresholdInMs),
                WifiInjector.getInstance().getWifiHandlerLocalLog());
        mWifiAwareNativeManager = wifiAwareNativeManager;
        mWifiAwareNativeApi = wifiAwareNativeApi;
        mWifiAwareNativeCallback = wifiAwareNativeCallback;

        mHandler.post(() -> {
            mStateManager.start(mContext, handlerThread.getLooper(), awareMetrics,
                    wifiPermissionsUtil, permissionsWrapper, new Clock(), netdWrapper,
                    interfaceConflictManager);

            settingsConfigStore.registerChangeListener(
                    WIFI_AWARE_VERBOSE_LOGGING_ENABLED,
                    (key, newValue) -> enableVerboseLogging(newValue),
                    mHandler);
            enableVerboseLogging(settingsConfigStore.get(WIFI_AWARE_VERBOSE_LOGGING_ENABLED));
        });
    }

    private void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseHalLoggingEnabled = verboseEnabled;
        updateVerboseLoggingEnabled();
        boolean vDbg = verboseEnabled || mContext.getResources()
                .getBoolean(R.bool.config_aware_vdbg_enable_on_verbose_logging);
        mStateManager.enableVerboseLogging(mVerboseLoggingEnabled, mVerboseLoggingEnabled, vDbg);
        mWifiAwareNativeCallback.enableVerboseLogging(mVerboseLoggingEnabled);
        mWifiAwareNativeManager.enableVerboseLogging(mVerboseLoggingEnabled,
                mVerboseLoggingEnabled);
        mWifiAwareNativeApi.enableVerboseLogging(mVerboseLoggingEnabled, vDbg);
    }

    /**
     * Start/initialize portions of the service which require the boot stage to be complete.
     */
    public void startLate() {
        Log.i(TAG, "Late initialization of Wi-Fi Aware service");
        updateVerboseLoggingEnabled();
        mHandler.post(() -> mStateManager.startLate());
    }

    private void updateVerboseLoggingEnabled() {
        final int verboseAlwaysOnLevel = mContext.getResources().getInteger(
                R.integer.config_wifiVerboseLoggingAlwaysOnLevel);
        mVerboseLoggingEnabled = mFrameworkFacade.isVerboseLoggingAlwaysOn(verboseAlwaysOnLevel,
                mBuildProperties) || mVerboseHalLoggingEnabled;
    }

    @Override
    public boolean isUsageEnabled() {
        enforceAccessPermission();

        return mStateManager.isUsageEnabled();
    }

    @Override
    public Characteristics getCharacteristics() {
        enforceAccessPermission();

        return mStateManager.getCapabilities() == null ? null
                : mStateManager.getCapabilities().toPublicCharacteristics(
                        WifiInjector.getInstance().getDeviceConfigFacade());
    }

    @Override
    public AwareResources getAvailableAwareResources() {
        enforceAccessPermission();
        return new WifiThreadRunner(mHandler)
                .call(() -> mStateManager.getAvailableAwareResources(), null);
    }

    @Override
    public boolean isDeviceAttached() {
        enforceAccessPermission();
        return mStateManager.isDeviceAttached();
    }

    @Override
    public void enableInstantCommunicationMode(String callingPackage, boolean enable) {
        enforceChangePermission();
        int uid = getMockableCallingUid();
        if (uid != Process.SHELL_UID && uid != Process.ROOT_UID) {
            mWifiPermissionsUtil.checkPackage(uid, callingPackage);
            if (!mWifiPermissionsUtil.isSystem(callingPackage, uid)
                    && !mWifiPermissionsUtil.checkConfigOverridePermission(uid)) {
                Log.i(TAG, "enableInstantCommunicationMode not allowed for uid=" + uid);
                return;
            }
        }
        mStateManager.enableInstantCommunicationMode(enable);
    }

    @Override
    public boolean isInstantCommunicationModeEnabled() {
        enforceAccessPermission();
        return mStateManager.isInstantCommModeGlobalEnable();
    }

    @Override
    public boolean isSetChannelOnDataPathSupported() {
        enforceAccessPermission();
        return mStateManager.isSetChannelOnDataPathSupported();
    }

    @Override
    public void setAwareParams(AwareParams params) {
        enforceChangePermission();
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkConfigOverridePermission(uid)) {
            throw new SecurityException("App not allowed to update Aware parameters "
                    + "(uid = " + uid + ")");
        }
        mStateManager.setAwareParams(params);
    }

    @Override
    public void resetPairedDevices(String callingPackage) {
        int uid = getMockableCallingUid();
        mWifiPermissionsUtil.checkPackage(uid, callingPackage);
        enforceChangePermission();
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "resetPairedDevices: callingPackage=" + callingPackage);
        }
        mStateManager.resetPairedDevices(callingPackage);
    }

    @Override
    public void removePairedDevice(String callingPackage, String alias) {
        int uid = getMockableCallingUid();
        mWifiPermissionsUtil.checkPackage(uid, callingPackage);
        enforceChangePermission();
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "removePairedDevice: callingPackage=" + callingPackage + ", alias=" + alias);
        }
        mStateManager.removePairedDevice(callingPackage, alias);
    }

    @Override
    public void getPairedDevices(String callingPackage, @NonNull IListListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener should not be null");
        }
        int uid = getMockableCallingUid();
        mWifiPermissionsUtil.checkPackage(uid, callingPackage);
        enforceAccessPermission();
        mStateManager.getPairedDevices(callingPackage, listener);
    }

    @Override
    public void setOpportunisticModeEnabled(String callingPackage, boolean enabled) {
        int uid = getMockableCallingUid();
        mWifiPermissionsUtil.checkPackage(uid, callingPackage);
        enforceChangePermission();
        if (mVerboseLoggingEnabled) {
            Log.v(
                    TAG,
                    "setOpportunisticModeEnabled: callingPackage="
                            + callingPackage
                            + ", enabled="
                            + enabled);
        }
        mStateManager.setOpportunisticPackage(callingPackage, enabled);
    }

    @Override
    public void isOpportunisticModeEnabled(String callingPackage,
            @NonNull IBooleanListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener should not be null");
        }
        int uid = getMockableCallingUid();
        mWifiPermissionsUtil.checkPackage(uid, callingPackage);
        enforceAccessPermission();
        mStateManager.isOpportunistic(callingPackage, listener);
    }

    @Override
    public void connect(final IBinder binder, String callingPackage, String callingFeatureId,
            IWifiAwareEventCallback callback, ConfigRequest configRequest,
            boolean notifyOnIdentityChanged, Bundle extras, boolean forOffloading) {
        enforceAccessPermission();
        enforceChangePermission();

        final int uid = getMockableCallingUid();
        mWifiPermissionsUtil.checkPackage(uid, callingPackage);

        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        }
        if (binder == null) {
            throw new IllegalArgumentException("Binder must not be null");
        }

        if (extras == null) {
            throw new IllegalArgumentException("extras bundle must not be null");
        }

        if (notifyOnIdentityChanged) {
            enforceNearbyOrLocationPermission(callingPackage, callingFeatureId,
                    getMockableCallingUid(), extras, "Wifi Aware attach");
        }
        if (forOffloading && !mWifiPermissionsUtil.checkConfigOverridePermission(uid)) {
            throw new SecurityException("Enable Wifi Aware for offloading require"
                    + "OVERRIDE_WIFI_CONFIG permission");
        }

        if (configRequest != null) {
            boolean networkStackPermission = checkNetworkStackPermission();
            boolean manageNetworkSelectionPermission =
                    mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid);
            if (!(networkStackPermission || manageNetworkSelectionPermission)) {
                throw new SecurityException("Insufficient permission to include a ConfigRequest");
            }

            if (!networkStackPermission) {
                // OEM apps with only the network selection permission can provide a config request,
                // but they should only modify the vendor data field.
                ConfigRequest.Builder builder = new ConfigRequest.Builder();
                if (SdkLevel.isAtLeastV()) {
                    builder.setVendorData(configRequest.getVendorData());
                }
                configRequest = builder.build();
            }
        } else {
            configRequest = new ConfigRequest.Builder().build();
        }
        configRequest.validate();


        int pid = getCallingPid();

        final int clientId;
        synchronized (mLock) {
            clientId = mNextClientId++;
        }

        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "connect: uid=" + uid + ", clientId=" + clientId + ", configRequest="
                    + configRequest + ", notifyOnIdentityChanged=" + notifyOnIdentityChanged);
        }

        IBinder.DeathRecipient dr = new IBinder.DeathRecipient() {
            @Override
            public void binderDied() {
                if (mVerboseLoggingEnabled) Log.v(TAG, "binderDied: clientId=" + clientId);
                binder.unlinkToDeath(this, 0);

                synchronized (mLock) {
                    mDeathRecipientsByClientId.delete(clientId);
                    mUidByClientId.delete(clientId);
                }

                mStateManager.disconnect(clientId);
            }
        };

        try {
            binder.linkToDeath(dr, 0);
        } catch (RemoteException e) {
            Log.e(TAG, "Error on linkToDeath - " + e);
            try {
                callback.onConnectFail(NanStatusCode.INTERNAL_FAILURE);
            } catch (RemoteException e1) {
                Log.e(TAG, "Error on onConnectFail()");
            }
            return;
        }

        synchronized (mLock) {
            mDeathRecipientsByClientId.put(clientId, dr);
            mUidByClientId.put(clientId, uid);
        }

        mStateManager.connect(clientId, uid, pid, callingPackage, callingFeatureId, callback,
                configRequest, notifyOnIdentityChanged, extras, forOffloading);
    }

    @Override
    public void disconnect(int clientId, IBinder binder) {
        enforceAccessPermission();
        enforceChangePermission();

        int uid = getMockableCallingUid();
        enforceClientValidity(uid, clientId);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "disconnect: uid=" + uid + ", clientId=" + clientId);
        }

        if (binder == null) {
            throw new IllegalArgumentException("Binder must not be null");
        }

        synchronized (mLock) {
            IBinder.DeathRecipient dr = mDeathRecipientsByClientId.get(clientId);
            if (dr != null) {
                binder.unlinkToDeath(dr, 0);
                mDeathRecipientsByClientId.delete(clientId);
            }
            mUidByClientId.delete(clientId);
        }

        mStateManager.disconnect(clientId);
    }

    @Override
    public void setMasterPreference(int clientId, IBinder binder, int mp) {
        int uid = getMockableCallingUid();
        enforceClientValidity(uid, clientId);
        if (binder == null) {
            throw new IllegalArgumentException("Binder must not be null");
        }
        if (!mWifiPermissionsUtil.checkConfigOverridePermission(uid)) {
            throw new SecurityException("setMasterPreference requires "
                    + "OVERRIDE_WIFI_CONFIG permission");
        }

        if (mp < 0) {
            throw new IllegalArgumentException(
                    "Master Preference specification must be non-negative");
        }
        if (mp == 1 || mp == 255 || mp > 255) {
            throw new IllegalArgumentException("Master Preference specification must not "
                    + "exceed 255 or use 1 or 255 (reserved values)");
        }

        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "setMasterPreference: uid=" + uid + ", clientId=" + clientId);
        }

        mStateManager.setMasterPreference(clientId, mp);
    }

    @Override
    public void getMasterPreference(int clientId, IBinder binder, IIntegerListener listener) {
        int uid = getMockableCallingUid();
        enforceClientValidity(uid, clientId);
        if (binder == null) {
            throw new IllegalArgumentException("Binder must not be null");
        }
        if (!mWifiPermissionsUtil.checkConfigOverridePermission(uid)) {
            throw new SecurityException("getMasterPreference requires "
                    + "OVERRIDE_WIFI_CONFIG permission");
        }

        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "getMasterPreference: uid=" + uid + ", clientId=" + clientId);
        }
        mStateManager.getMasterPreference(clientId, listener);
    }


    @Override
    public void terminateSession(int clientId, int sessionId) {
        enforceAccessPermission();
        enforceChangePermission();

        int uid = getMockableCallingUid();
        enforceClientValidity(uid, clientId);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "terminateSession: sessionId=" + sessionId + ", uid=" + uid + ", clientId="
                    + clientId);
        }

        mStateManager.terminateSession(clientId, sessionId);
    }

    @Override
    public void publish(String callingPackage, String callingFeatureId, int clientId,
            PublishConfig publishConfig, IWifiAwareDiscoverySessionCallback callback,
            Bundle extras) {
        enforceAccessPermission();
        enforceChangePermission();

        int uid = getMockableCallingUid();
        mWifiPermissionsUtil.checkPackage(uid, callingPackage);

        enforceNearbyOrLocationPermission(callingPackage, callingFeatureId,
                getMockableCallingUid(), extras, "Wifi Aware publish");

        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        }
        if (publishConfig == null) {
            throw new IllegalArgumentException("PublishConfig must not be null");
        }
        publishConfig.assertValid(mStateManager.getCharacteristics(),
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT));

        if (SdkLevel.isAtLeastU() && publishConfig.isSuspendable()
                && !mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use Aware suspension"
                    + "(uid = " + uid + ")");
        }

        enforceClientValidity(uid, clientId);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "publish: uid=" + uid + ", clientId=" + clientId + ", publishConfig="
                    + publishConfig + ", callback=" + callback);
        }

        mStateManager.publish(clientId, publishConfig, callback);
    }

    @Override
    public void updatePublish(int clientId, int sessionId, PublishConfig publishConfig) {
        enforceAccessPermission();
        enforceChangePermission();

        if (publishConfig == null) {
            throw new IllegalArgumentException("PublishConfig must not be null");
        }
        publishConfig.assertValid(mStateManager.getCharacteristics(),
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT));

        int uid = getMockableCallingUid();
        if (SdkLevel.isAtLeastU() && publishConfig.isSuspendable()
                && !mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use Aware suspension"
                    + "(uid = " + uid + ")");
        }

        enforceClientValidity(uid, clientId);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "updatePublish: uid=" + uid + ", clientId=" + clientId + ", sessionId="
                    + sessionId + ", config=" + publishConfig);
        }

        mStateManager.updatePublish(clientId, sessionId, publishConfig);
    }

    @Override
    public void subscribe(String callingPackage, String callingFeatureId, int clientId,
            SubscribeConfig subscribeConfig, IWifiAwareDiscoverySessionCallback callback,
            Bundle extras) {
        enforceAccessPermission();
        enforceChangePermission();

        int uid = getMockableCallingUid();
        mWifiPermissionsUtil.checkPackage(uid, callingPackage);

        enforceNearbyOrLocationPermission(callingPackage, callingFeatureId,
                getMockableCallingUid(), extras, "Wifi Aware subscribe");

        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        }
        if (subscribeConfig == null) {
            throw new IllegalArgumentException("SubscribeConfig must not be null");
        }
        subscribeConfig.assertValid(mStateManager.getCharacteristics(),
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT));

        if (SdkLevel.isAtLeastU() && subscribeConfig.isSuspendable()
                && !mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use Aware suspension"
                    + "(uid = " + uid + ")");
        }

        enforceClientValidity(uid, clientId);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "subscribe: uid=" + uid + ", clientId=" + clientId + ", config="
                    + subscribeConfig + ", callback=" + callback);
        }

        mStateManager.subscribe(clientId, subscribeConfig, callback);
    }

    @Override
    public void updateSubscribe(int clientId, int sessionId, SubscribeConfig subscribeConfig) {
        enforceAccessPermission();
        enforceChangePermission();

        if (subscribeConfig == null) {
            throw new IllegalArgumentException("SubscribeConfig must not be null");
        }
        subscribeConfig.assertValid(mStateManager.getCharacteristics(),
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT));

        int uid = getMockableCallingUid();
        if (SdkLevel.isAtLeastU() && subscribeConfig.isSuspendable()
                && !mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use Aware suspension"
                    + "(uid = " + uid + ")");
        }

        enforceClientValidity(uid, clientId);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "updateSubscribe: uid=" + uid + ", clientId=" + clientId + ", sessionId="
                    + sessionId + ", config=" + subscribeConfig);
        }

        mStateManager.updateSubscribe(clientId, sessionId, subscribeConfig);
    }

    @Override
    public void sendMessage(int clientId, int sessionId, int peerId, byte[] message, int messageId,
            int retryCount) {
        enforceAccessPermission();
        enforceChangePermission();

        if (retryCount != 0) {
            enforceNetworkStackPermission();
        }

        if (message != null && message.length
                > mStateManager.getCharacteristics().getMaxServiceSpecificInfoLength()) {
            throw new IllegalArgumentException(
                    "Message length longer than supported by device characteristics");
        }
        if (retryCount < 0 || retryCount > DiscoverySession.getMaxSendRetryCount()) {
            throw new IllegalArgumentException("Invalid 'retryCount' must be non-negative "
                    + "and <= DiscoverySession.MAX_SEND_RETRY_COUNT");
        }

        int uid = getMockableCallingUid();
        enforceClientValidity(uid, clientId);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG,
                    "sendMessage: sessionId=" + sessionId + ", uid=" + uid + ", clientId="
                            + clientId + ", peerId=" + peerId + ", messageId=" + messageId
                            + ", retryCount=" + retryCount);
        }

        mStateManager.sendMessage(uid, clientId, sessionId, peerId, message, messageId, retryCount);
    }

    @Override
    public void requestMacAddresses(int uid, int[] peerIds, IWifiAwareMacAddressProvider callback) {
        enforceNetworkStackPermission();

        mStateManager.requestMacAddresses(uid, peerIds, callback);
    }

    @Override
    public void initiateNanPairingSetupRequest(int clientId, int sessionId, int peerId,
            String password, String pairingDeviceAlias, int cipherSuite) {
        enforceAccessPermission();
        enforceChangePermission();
        if (!mStateManager.getCharacteristics().isAwarePairingSupported()) {
            throw new IllegalArgumentException(
                    "NAN pairing is not supported");
        }
        if (pairingDeviceAlias == null) {
            throw new IllegalArgumentException(
                    "initiateNanPairingRequest: invalid pairingDeviceAlias - must be non-null");
        }
        if (cipherSuite != WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128
                && cipherSuite != WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_256) {
            throw new IllegalArgumentException(
                    "initiateNanPairingRequest: cipher suite is invalid");
        }
        int uid = getMockableCallingUid();
        enforceClientValidity(uid, clientId);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG,
                    "initiateNanPairingRequest: sessionId=" + sessionId + ", uid=" + uid
                            + ", clientId=" + clientId + ", peerId=" + peerId);
        }
        mStateManager.initiateNanPairingSetupRequest(clientId, sessionId, peerId, password,
                pairingDeviceAlias, cipherSuite);
    }

    @Override
    public void responseNanPairingSetupRequest(int clientId, int sessionId, int peerId,
            int requestId, String password, String pairingDeviceAlias, boolean accept,
            int cipherSuite) {
        enforceAccessPermission();
        enforceChangePermission();
        if (!mStateManager.getCharacteristics().isAwarePairingSupported()) {
            throw new IllegalArgumentException(
                    "NAN pairing is not supported");
        }
        if (accept) {
            if (pairingDeviceAlias == null) {
                throw new IllegalArgumentException(
                        "responseNanPairingSetupRequest: invalid pairingDeviceAlias - "
                                + "must be non-null");
            }
            if (cipherSuite != WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128
                    && cipherSuite != WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_256) {
                throw new IllegalArgumentException(
                        "responseNanPairingSetupRequest: cipher suite is invalid");
            }
        }
        int uid = getMockableCallingUid();
        enforceClientValidity(uid, clientId);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG,
                    "responsePairingRequest: sessionId=" + sessionId + ", uid=" + uid
                            + ", clientId=" + clientId + ", peerId=" + peerId);
        }
        mStateManager.responseNanPairingSetupRequest(clientId, sessionId, peerId, requestId,
                password, pairingDeviceAlias, accept, cipherSuite);
    }

    @Override
    public void initiateBootStrappingSetupRequest(int clientId, int sessionId, int peerId,
            int method) {
        enforceAccessPermission();
        enforceChangePermission();
        if (!mStateManager.getCharacteristics().isAwarePairingSupported()) {
            throw new IllegalArgumentException(
                    "NAN pairing is not supported");
        }
        int uid = getMockableCallingUid();
        enforceClientValidity(uid, clientId);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG,
                    "initiateBootStrappingSetupRequest: sessionId=" + sessionId
                            + ", uid=" + uid + ", clientId=" + clientId + ", peerId=" + peerId);
        }
        mStateManager.initiateBootStrappingSetupRequest(clientId, sessionId, peerId, method, 0,
                null);
    }

    @Override
    public void suspend(int clientId, int sessionId) {
        enforceAccessPermission();
        enforceChangePermission();

        int uid = getMockableCallingUid();
        enforceClientValidity(uid, clientId);
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use Aware suspension"
                    + "(uid = " + uid + ")");
        }

        if (!mStateManager.getCharacteristics().isSuspensionSupported()) {
            throw new UnsupportedOperationException("NAN suspension is not supported.");
        }

        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "suspend: clientId=" + clientId + ", sessionId=" + sessionId);
        }

        mStateManager.suspend(clientId, sessionId);
    }

    @Override
    public void resume(int clientId, int sessionId) {
        enforceAccessPermission();
        enforceChangePermission();

        int uid = getMockableCallingUid();
        enforceClientValidity(uid, clientId);
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use Aware suspension"
                    + "(uid = " + uid + ")");
        }

        if (!mStateManager.getCharacteristics().isSuspensionSupported()) {
            throw new UnsupportedOperationException("NAN suspension is not supported.");
        }

        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "resume: clientId=" + clientId + ", sessionId=" + sessionId);
        }

        mStateManager.resume(clientId, sessionId);
    }

    @Override
    public int handleShellCommand(@NonNull ParcelFileDescriptor in,
            @NonNull ParcelFileDescriptor out, @NonNull ParcelFileDescriptor err,
            @NonNull String[] args) {
        return mShellCommand.exec(
                this, in.getFileDescriptor(), out.getFileDescriptor(), err.getFileDescriptor(),
                args);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.DUMP) != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump WifiAwareService from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        pw.println("Wi-Fi Aware Service");
        synchronized (mLock) {
            pw.println("  mNextClientId: " + mNextClientId);
            pw.println("  mDeathRecipientsByClientId: " + mDeathRecipientsByClientId);
            pw.println("  mUidByClientId: " + mUidByClientId);
        }
        mStateManager.dump(fd, pw, args);
    }

    private void enforceClientValidity(int uid, int clientId) {
        synchronized (mLock) {
            int uidIndex = mUidByClientId.indexOfKey(clientId);
            if (uidIndex < 0 || mUidByClientId.valueAt(uidIndex) != uid) {
                throw new SecurityException("Attempting to use invalid uid+clientId mapping: uid="
                        + uid + ", clientId=" + clientId);
            }
        }
    }

    private void enforceAccessPermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE, TAG);
    }

    private void enforceChangePermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE, TAG);
    }

    private void enforceNearbyOrLocationPermission(String callingPackage, String callingFeatureId,
            int uid, Bundle extras, String message) {
        if (!SdkLevel.isAtLeastT() || mWifiPermissionsUtil.isTargetSdkLessThan(callingPackage,
                Build.VERSION_CODES.TIRAMISU,
                uid)) {
            mWifiPermissionsUtil.enforceLocationPermission(callingPackage, callingFeatureId, uid);
        } else {
            mWifiPermissionsUtil.enforceNearbyDevicesPermission(extras.getParcelable(
                    WifiManager.EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE), true, message);
        }
    }

    private void enforceNetworkStackPermission() {
        mContext.enforceCallingOrSelfPermission(Manifest.permission.NETWORK_STACK, TAG);
    }

    private boolean checkNetworkStackPermission() {
        return mContext.checkCallingOrSelfPermission(Manifest.permission.NETWORK_STACK)
                == PackageManager.PERMISSION_GRANTED;
    }
}
