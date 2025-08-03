/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.server.wifi.scanner;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.IScanDataListener;
import android.net.wifi.IWifiScanner;
import android.net.wifi.IWifiScannerListener;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiAnnotations;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.ChannelSpec;
import android.net.wifi.WifiScanner.PnoSettings;
import android.net.wifi.WifiScanner.ScanData;
import android.net.wifi.WifiScanner.ScanSettings;
import android.net.wifi.WifiScanner.WifiBand;
import android.net.wifi.util.ScanResultUtil;
import android.os.BatteryStatsManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.WorkSource;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Protocol;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.ClientModeImpl;
import com.android.server.wifi.Clock;
import com.android.server.wifi.DeviceConfigFacade;
import com.android.server.wifi.WifiGlobals;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiLocalServices;
import com.android.server.wifi.WifiLog;
import com.android.server.wifi.WifiMetrics;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WifiThreadRunner;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.proto.nano.WifiMetricsProto;
import com.android.server.wifi.scanner.ChannelHelper.ChannelCollection;
import com.android.server.wifi.util.ArrayUtils;
import com.android.server.wifi.util.LastCallerInfoManager;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.server.wifi.util.WorkSourceUtil;
import com.android.wifi.resources.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class WifiScanningServiceImpl extends IWifiScanner.Stub {

    private static final String TAG = WifiScanningService.TAG;
    private static final boolean DBG = false;

    private static final int UNKNOWN_PID = -1;

    private final LocalLog mLocalLog = new LocalLog(512);

    private WifiLog mLog;

    private void localLog(String message) {
        mLocalLog.log(message);
        if (isVerboseLoggingEnabled()) {
            Log.i(TAG, message, null);
        }
    }

    private void logw(String message) {
        Log.w(TAG, message, null);
        mLocalLog.log(message);
    }

    private void loge(String message) {
        Log.e(TAG, message, null);
        mLocalLog.log(message);
    }

    private void notifyFailure(IWifiScannerListener listener, int reasonCode, String reason) {
        try {
            listener.onFailure(reasonCode, reason);
        } catch (RemoteException e) {
            loge(e + "failed to notify listener for failure");
        }
    }

    private boolean isPlatformOrTargetSdkLessThanU(String packageName, int uid) {
        if (!SdkLevel.isAtLeastU()) {
            return true;
        }
        return mWifiPermissionsUtil.isTargetSdkLessThan(packageName,
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE, uid);
    }

    @Override
    public Bundle getAvailableChannels(@WifiBand int band, String packageName,
            @Nullable String attributionTag, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (isPlatformOrTargetSdkLessThanU(packageName, uid)) {
            long ident = Binder.clearCallingIdentity();
            try {
                enforcePermission(uid, packageName, attributionTag, false, false, false);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        } else {
            mWifiPermissionsUtil.enforceNearbyDevicesPermission(
                    extras.getParcelable(WifiManager.EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE),
                    true, TAG + " getAvailableChannels");
        }
        ChannelSpec[][] channelSpecs = mWifiThreadRunner.call(() -> {
            if (mChannelHelper == null) return new ChannelSpec[0][0];
            mChannelHelper.updateChannels();
            return mChannelHelper.getAvailableScanChannels(band);
        }, new ChannelSpec[0][0]);
        if (channelSpecs == null) {
            channelSpecs = new ChannelSpec[0][0];
        }

        ArrayList<Integer> list = new ArrayList<>();
        for (int i = 0; i < channelSpecs.length; i++) {
            for (ChannelSpec channelSpec : channelSpecs[i]) {
                list.add(channelSpec.frequency);
            }
        }
        Bundle b = new Bundle();
        b.putIntegerArrayList(WifiScanner.GET_AVAILABLE_CHANNELS_EXTRA, list);
        mLog.trace("getAvailableChannels uid=%").c(Binder.getCallingUid()).flush();
        return b;
    }

    /**
     * See {@link WifiScanner#isScanning()}
     *
     * @return true if in ScanningState.
     */
    @Override
    public boolean isScanning() {
        int uid = Binder.getCallingUid();
        if (!mWifiPermissionsUtil.checkCallersHardwareLocationPermission(uid)) {
            throw new SecurityException("UID " + uid
                    + " does not have hardware Location permission");
        }
        return mIsScanning;
    }

    @Override
    public boolean setScanningEnabled(boolean enable, int tid, String packageName) {
        int uid = Binder.getCallingUid();
        int msgWhat = enable ? WifiScanner.CMD_ENABLE : WifiScanner.CMD_DISABLE;
        try {
            enforcePermission(uid, packageName, null, isPrivilegedMessage(msgWhat),
                    false, false);
        } catch (SecurityException e) {
            localLog("setScanningEnabled: failed to authorize app: " + packageName + " uid "
                    + uid);
            return false;
        }
        localLog("enable scan: package " + packageName + " tid " + tid + " enable " + enable);
        mWifiThreadRunner.post(() -> {
            if (enable) {
                Log.i(TAG,
                        "Received a request to enable scanning, UID = " + Binder.getCallingUid());
                setupScannerImpls();
            } else {
                Log.i(TAG, "Received a request to disable scanning, UID = " + uid);
                teardownScannerImpls();
            }
            Message msg = Message.obtain();
            msg.what = msgWhat;
            mBackgroundScanStateMachine.sendMessage(Message.obtain(msg));
            mSingleScanStateMachine.sendMessage(Message.obtain(msg));
            mPnoScanStateMachine.sendMessage(Message.obtain(msg));
            mLastCallerInfoManager.put(WifiManager.API_SCANNING_ENABLED, tid,
                    Binder.getCallingUid(), Binder.getCallingPid(), packageName, enable);
        });
        return true;
    }

    @Override
    public void registerScanListener(IWifiScannerListener listener, String packageName,
            String featureId) {
        final int uid = Binder.getCallingUid();
        try {
            enforcePermission(uid, packageName, featureId,
                    isPrivilegedMessage(WifiScanner.CMD_REGISTER_SCAN_LISTENER),
                    false, false);
        } catch (SecurityException e) {
            localLog("registerScanListener: failed to authorize app: " + packageName + " uid "
                    + uid  + " AttributionTag " + featureId);
            notifyFailure(listener, WifiScanner.REASON_NOT_AUTHORIZED, "Not authorized");
            return;
        }
        mWifiThreadRunner.post(() -> {
            if (mClients.get(listener) != null) {
                logw("duplicate client connection: " + uid + ", listener=" + listener
                        + " AttributionTag " + featureId);
                return;
            }
            final ExternalClientInfo client = new ExternalClientInfo(uid, packageName,
                    listener);
            client.register();
            localLog("register scan listener: " + client + " AttributionTag " + featureId);
            logScanRequest("registerScanListener", client, null, null, null);
            mSingleScanListeners.addRequest(client, null, null);
            client.replySucceeded();
        });
    }

    @Override
    public void unregisterScanListener(IWifiScannerListener listener, String packageName,
            String featureId) {
        int uid = Binder.getCallingUid();
        try {
            enforcePermission(uid, packageName, featureId,
                    isPrivilegedMessage(WifiScanner.CMD_DEREGISTER_SCAN_LISTENER),
                    true, false);
        } catch (SecurityException e) {
            localLog("unregisterScanListener: failed to authorize app: " + packageName + " uid "
                    + uid + " AttributionTag " + featureId);
            notifyFailure(listener, WifiScanner.REASON_NOT_AUTHORIZED, "Not authorized");
            return;
        }
        ExternalClientInfo client = (ExternalClientInfo) mClients.get(listener);
        if (client == null) {
            logw("no client registered: " + uid + ", listener=" + listener
                    + " AttributionTag " + featureId);
            return;
        }
        mWifiThreadRunner.post(() -> {
            logScanRequest("deregisterScanListener", client, null, null, null);
            mSingleScanListeners.removeRequest(client);
            client.cleanup();
        });
    }

    @Override
    public void startBackgroundScan(IWifiScannerListener listener,
            WifiScanner.ScanSettings settings,
            WorkSource workSource, String packageName, String featureId) {
        final int uid = Binder.getCallingUid();
        try {
            enforcePermission(uid, packageName, featureId,
                    isPrivilegedMessage(WifiScanner.CMD_START_BACKGROUND_SCAN),
                    false, false);
        } catch (SecurityException e) {
            localLog("startBackgroundScan: failed to authorize app: " + packageName + " uid "
                    + uid);
            notifyFailure(listener, WifiScanner.REASON_NOT_AUTHORIZED, "Not authorized");
            return;
        }
        mWifiThreadRunner.post(() -> {
            ExternalClientInfo client = (ExternalClientInfo) mClients.get(listener);
            if (client == null) {
                client = new ExternalClientInfo(uid, packageName, listener);
                client.register();
            }
            localLog("start background scan: " + client + " package " + packageName);
            Message msg = Message.obtain();
            msg.what = WifiScanner.CMD_START_BACKGROUND_SCAN;
            msg.obj = new ScanParams(listener, settings, workSource);
            msg.sendingUid = uid;
            mBackgroundScanStateMachine.sendMessage(msg);
        });
    }

    @Override
    public void stopBackgroundScan(IWifiScannerListener listener, String packageName,
            String featureId) {
        final int uid = Binder.getCallingUid();
        try {
            enforcePermission(uid, packageName, featureId,
                    isPrivilegedMessage(WifiScanner.CMD_STOP_BACKGROUND_SCAN),
                    true, false);
        } catch (SecurityException e) {
            localLog("stopBackgroundScan: failed to authorize app: " + packageName + " uid "
                    + uid);
            notifyFailure(listener, WifiScanner.REASON_NOT_AUTHORIZED, "Not authorized");
            return;
        }
        ExternalClientInfo client = (ExternalClientInfo) mClients.get(listener);
        if (client == null) {
            Log.e(TAG, "listener not found " + listener);
            return;
        }
        localLog("stop background scan: " + client);
        Message msg = Message.obtain();
        msg.what = WifiScanner.CMD_STOP_BACKGROUND_SCAN;
        msg.obj = new ScanParams(listener, null, null);
        msg.sendingUid = uid;
        mBackgroundScanStateMachine.sendMessage(msg);
    }

    @Override
    public boolean getScanResults(String packageName, String featureId) {
        final int uid = Binder.getCallingUid();
        try {
            enforcePermission(uid, packageName, featureId,
                    isPrivilegedMessage(WifiScanner.CMD_GET_SCAN_RESULTS),
                    false, false);
        } catch (SecurityException e) {
            localLog("getScanResults: failed to authorize app: " + packageName + " uid "
                    + uid + " AttributionTag " + featureId);
            return false;
        }
        localLog("get scan result: " + packageName + " AttributionTag " + featureId);
        mBackgroundScanStateMachine.sendMessage(WifiScanner.CMD_GET_SCAN_RESULTS);
        return true;
    }

    private static class ScanParams {
        public IWifiScannerListener listener;
        public WifiScanner.ScanSettings settings;
        public WifiScanner.PnoSettings pnoSettings;
        public WorkSource workSource;
        public String packageName;
        public String featureId;

        ScanParams(IWifiScannerListener listener, WifiScanner.ScanSettings settings,
                WorkSource workSource) {
            this(listener, settings, null, workSource, null, null);
        }

        ScanParams(IWifiScannerListener listener, WifiScanner.ScanSettings settings,
                WifiScanner.PnoSettings pnoSettings, WorkSource workSource, String packageName,
                String featureId) {
            this.listener = listener;
            this.settings = settings;
            this.pnoSettings = pnoSettings;
            this.workSource = workSource;
            this.packageName = packageName;
            this.featureId = featureId;
        }
    }

    @Override
    public void startScan(IWifiScannerListener listener, WifiScanner.ScanSettings settings,
            WorkSource workSource, String packageName, String featureId) {
        final int uid = Binder.getCallingUid();
        try {
            enforcePermission(uid, packageName, featureId,
                    isPrivilegedMessage(WifiScanner.CMD_START_SINGLE_SCAN),
                    shouldIgnoreLocationSettingsForSingleScan(settings),
                    shouldHideFromAppsForSingleScan(settings));
        } catch (SecurityException e) {
            localLog("startScan: failed to authorize app: " + packageName + " uid "
                    + uid + " AttributionTag " + featureId);
            notifyFailure(listener, WifiScanner.REASON_NOT_AUTHORIZED, "Not authorized");
            return;
        }
        mLastCallerInfoManager.put(WifiManager.API_WIFI_SCANNER_START_SCAN, Process.myTid(),
                uid, Binder.getCallingPid(), packageName, true);
        mWifiThreadRunner.post(() -> {
            ExternalClientInfo client = (ExternalClientInfo) mClients.get(listener);
            if (client == null) {
                client = new ExternalClientInfo(uid, packageName, listener);
                client.register();
            }
            localLog("start scan: " + client + " package " + packageName + " AttributionTag "
                    + featureId);
            Message msg = Message.obtain();
            msg.what = WifiScanner.CMD_START_SINGLE_SCAN;
            msg.obj = new ScanParams(listener, settings, workSource);
            msg.sendingUid = uid;
            mSingleScanStateMachine.sendMessage(msg);
        });
    }

    @Override
    public void stopScan(IWifiScannerListener listener, String packageName, String featureId) {
        int uid = Binder.getCallingUid();
        try {
            enforcePermission(uid, packageName, featureId,
                    isPrivilegedMessage(WifiScanner.CMD_STOP_SINGLE_SCAN),
                    true, false);
        } catch (SecurityException e) {
            localLog("stopScan: failed to authorize app: " + packageName + " uid "
                    + uid + " AttributionTag " + featureId);
            notifyFailure(listener, WifiScanner.REASON_NOT_AUTHORIZED, "Not authorized");
            return;
        }
        mWifiThreadRunner.post(() -> {
            ExternalClientInfo client = (ExternalClientInfo) mClients.get(listener);
            if (client == null) {
                Log.e(TAG, "listener not found " + listener);
                return;
            }
            localLog("stop scan: " + client + " AttributionTag " + featureId);
            Message msg = Message.obtain();
            msg.what = WifiScanner.CMD_STOP_SINGLE_SCAN;
            msg.obj = new ScanParams(listener, null, null);
            msg.sendingUid = uid;
            mSingleScanStateMachine.sendMessage(msg);
        });
    }

    @Override
    public List<ScanResult> getSingleScanResults(String packageName, String featureId) {
        localLog("get single scan result: package " + packageName
                + " AttributionTag " + featureId);
        final int uid = Binder.getCallingUid();
        try {
            enforcePermission(uid, packageName, featureId,
                    isPrivilegedMessage(WifiScanner.CMD_GET_SCAN_RESULTS),
                    false, false);
        } catch (SecurityException e) {
            localLog("getSingleScanResults: failed to authorize app: " + packageName + " uid "
                    + uid + " AttributionTag " + featureId);
            return new ArrayList<>();
        }
        return mWifiThreadRunner.call(() -> mSingleScanStateMachine.filterCachedScanResultsByAge(),
                new ArrayList<ScanResult>());
    }


    /**
     * See {@link WifiScanner#getCachedScanData(Executor, Consumer)}.
     */
    @Override
    public void getCachedScanData(String packageName, String featureId,
            IScanDataListener listener) {
        localLog("get single scan result: package " + packageName
                + " AttributionTag " + featureId);
        final int uid = Binder.getCallingUid();
        Objects.requireNonNull(listener, "listener cannot be null");
        enforcePermission(uid, packageName, featureId, false, false, false);

        mWifiThreadRunner.post(() -> {
            try {
                listener.onResult(mWifiNative.getCachedScanResultsFromAllClientIfaces());
            } catch (RemoteException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        });
    }

    @Override
    public void startPnoScan(IWifiScannerListener listener, WifiScanner.ScanSettings scanSettings,
            WifiScanner.PnoSettings pnoSettings, String packageName, String featureId) {
        final int uid = Binder.getCallingUid();
        if (listener == null) {
            Log.e(TAG, "listener is null");
            return;
        }
        try {
            enforcePermission(uid, packageName, featureId,
                    isPrivilegedMessage(WifiScanner.CMD_START_PNO_SCAN),
                    false, false);
        } catch (SecurityException e) {
            localLog("startPnoScan: failed to authorize app: " + packageName + " uid "
                    + uid + " AttributionTag " + featureId);
            notifyFailure(listener, WifiScanner.REASON_NOT_AUTHORIZED, "Not authorized");
            return;
        }
        mWifiThreadRunner.post(() -> {
            String clientInfoLog = "ClientInfo[uid=" + uid + ", package=" + packageName + ", "
                    + listener + "]";
            localLog("start pno scan: " + clientInfoLog + " AttributionTag " + featureId);
            Message msg = Message.obtain();
            msg.what = WifiScanner.CMD_START_PNO_SCAN;
            msg.obj = new ScanParams(listener, scanSettings, pnoSettings, null, packageName, null);
            msg.sendingUid = uid;
            mPnoScanStateMachine.sendMessage(msg);
        });
    }

    @Override
    public void stopPnoScan(IWifiScannerListener listener, String packageName, String featureId) {
        final int uid = Binder.getCallingUid();
        if (listener == null) {
            Log.e(TAG, "listener is null");
            return;
        }
        try {
            enforcePermission(uid, packageName, featureId,
                    isPrivilegedMessage(WifiScanner.CMD_STOP_PNO_SCAN),
                    true, false);
        } catch (SecurityException e) {
            localLog("stopPnoScan: failed to authorize app: " + packageName + " uid "
                    + uid + " AttributionTag " + featureId);
            notifyFailure(listener, WifiScanner.REASON_NOT_AUTHORIZED, "Not authorized");
            return;
        }
        mWifiThreadRunner.post(() -> {
            ExternalClientInfo client = (ExternalClientInfo) mClients.get(listener);
            if (client == null) {
                Log.e(TAG, "listener not found " + listener);
                return;
            }
            localLog("stop pno scan: " + client + " AttributionTag " + featureId);
            Message msg = Message.obtain();
            msg.what = WifiScanner.CMD_STOP_PNO_SCAN;
            msg.obj = new ScanParams(listener, null, null);
            msg.sendingUid = uid;
            mPnoScanStateMachine.sendMessage(msg);
        });
    }

    @Override
    public void enableVerboseLogging(boolean enabled) {
        if (!mWifiPermissionsUtil.checkNetworkSettingsPermission(Binder.getCallingUid())) {
            return;
        }
        mVerboseLoggingEnabled.set(enabled);
        localLog("enableVerboseLogging: uid=" + Binder.getCallingUid() + " enabled=" + enabled);
    }

    private boolean isVerboseLoggingEnabled() {
        return mVerboseLoggingEnabled.get();
    }

    private void enforceNetworkStack(int uid) {
        mContext.enforcePermission(
                Manifest.permission.NETWORK_STACK,
                UNKNOWN_PID, uid,
                "NetworkStack");
    }

    // Helper method to check if the incoming message is for a privileged request.
    private boolean isPrivilegedMessage(int msgWhat) {
        boolean isPrivileged = (msgWhat == WifiScanner.CMD_ENABLE
                || msgWhat == WifiScanner.CMD_DISABLE
                || msgWhat == WifiScanner.CMD_START_PNO_SCAN
                || msgWhat == WifiScanner.CMD_STOP_PNO_SCAN);
        if (!SdkLevel.isAtLeastT()) {
            isPrivileged = isPrivileged || msgWhat == WifiScanner.CMD_REGISTER_SCAN_LISTENER;
        }
        return isPrivileged;
    }

    // Check if we should ignore location settings if this is a single scan request.
    private boolean shouldIgnoreLocationSettingsForSingleScan(ScanSettings scanSettings) {
        if (scanSettings == null) return false;
        return scanSettings.ignoreLocationSettings;
    }

    // Check if we should hide this request from app-ops if this is a single scan request.
    private boolean shouldHideFromAppsForSingleScan(ScanSettings scanSettings) {
        if (scanSettings == null) return false;
        return scanSettings.hideFromAppOps;
    }

    /**
     * Get merged vendor IE byte array from List
     */
    public static byte[] getVendorIesBytesFromVendorIesList(
            List<ScanResult.InformationElement> vendorIesList) {
        if (vendorIesList.size() == 0) return null;

        int len = 0;
        for (ScanResult.InformationElement ie : vendorIesList) {
            if ((len + WifiScanner.WIFI_IE_HEAD_LEN + ie.bytes.length)
                    > WifiScanner.WIFI_SCANNER_SETTINGS_VENDOR_ELEMENTS_MAX_LEN) {
                Log.w(TAG, "Total vendor IE len is larger than max len. Current len:" + len);
                break;
            }
            len += WifiScanner.WIFI_IE_HEAD_LEN + ie.bytes.length;
        }

        byte[] vendorIes = new byte[len];
        int index = 0;
        for (ScanResult.InformationElement ie : vendorIesList) {
            if ((index + WifiScanner.WIFI_IE_HEAD_LEN + ie.bytes.length)
                    > WifiScanner.WIFI_SCANNER_SETTINGS_VENDOR_ELEMENTS_MAX_LEN) {
                break;
            }
            vendorIes[index] = (byte) ie.id;
            vendorIes[index + 1] = (byte) ie.bytes.length;
            System.arraycopy(ie.bytes, 0, vendorIes, index + WifiScanner.WIFI_IE_HEAD_LEN,
                    ie.bytes.length);
            index += WifiScanner.WIFI_IE_HEAD_LEN + ie.bytes.length;
        }
        return vendorIes;
    }

    /**
     * Enforce the necessary client permissions for WifiScanner.
     * If the client has NETWORK_STACK permission, then it can "always" send "any" request.
     * If the client has only LOCATION_HARDWARE permission, then it can
     * a) Only make scan related requests when location is turned on.
     * b) Can never make one of the privileged requests.
     *
     * @param uid                          uid of the client
     * @param packageName                  package name of the client
     * @param attributionTag               The feature in the package of the client
     * @param isPrivilegedRequest          whether we are checking for a privileged request
     * @param shouldIgnoreLocationSettings override to ignore location settings
     * @param shouldHideFromApps           override to hide request from AppOps
     */
    private void enforcePermission(int uid, String packageName, @Nullable String attributionTag,
            boolean isPrivilegedRequest, boolean shouldIgnoreLocationSettings,
            boolean shouldHideFromApps) {
        try {
            /** Wifi stack issued requests.*/
            enforceNetworkStack(uid);
        } catch (SecurityException e) {
            // System-app issued requests
            if (isPrivilegedRequest) {
                // Privileged message, only requests from clients with NETWORK_STACK allowed!
                throw e;
            }
            mWifiPermissionsUtil.enforceCanAccessScanResultsForWifiScanner(packageName,
                    attributionTag, uid, shouldIgnoreLocationSettings, shouldHideFromApps);
        }
    }

    private static final int BASE = Protocol.BASE_WIFI_SCANNER_SERVICE;

    private static final int CMD_SCAN_RESULTS_AVAILABLE = BASE + 0;
    private static final int CMD_FULL_SCAN_RESULTS = BASE + 1;
    private static final int CMD_SCAN_PAUSED = BASE + 8;
    private static final int CMD_SCAN_RESTARTED = BASE + 9;
    private static final int CMD_SCAN_FAILED = BASE + 10;
    private static final int CMD_PNO_NETWORK_FOUND = BASE + 11;
    private static final int CMD_PNO_SCAN_FAILED = BASE + 12;
    private static final int CMD_SW_PNO_SCAN = BASE + 14;


    private final Context mContext;
    private final Looper mLooper;
    private final WifiThreadRunner mWifiThreadRunner;
    private final WifiScannerImpl.WifiScannerImplFactory mScannerImplFactory;
    private final ArrayMap<IWifiScannerListener, ClientInfo> mClients;
    private final Map<String, WifiScannerImpl> mScannerImpls;


    private final RequestList<Void> mSingleScanListeners = new RequestList<>();

    private ChannelHelper mChannelHelper;
    private BackgroundScanScheduler mBackgroundScheduler;
    private WifiNative.ScanSettings mPreviousSchedule;
    private boolean mIsScanning = false;

    private WifiBackgroundScanStateMachine mBackgroundScanStateMachine;
    private WifiSingleScanStateMachine mSingleScanStateMachine;
    private WifiPnoScanStateMachine mPnoScanStateMachine;
    private final BatteryStatsManager mBatteryStats;
    private final AlarmManager mAlarmManager;
    private final WifiMetrics mWifiMetrics;
    private final Clock mClock;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private final WifiNative mWifiNative;
    private final WifiManager mWifiManager;
    private final LastCallerInfoManager mLastCallerInfoManager;
    private final DeviceConfigFacade mDeviceConfigFacade;
    private final WifiGlobals mWifiGlobals;

    private AtomicBoolean mVerboseLoggingEnabled = new AtomicBoolean(false);

    WifiScanningServiceImpl(Context context, Looper looper,
            WifiScannerImpl.WifiScannerImplFactory scannerImplFactory,
            BatteryStatsManager batteryStats, WifiInjector wifiInjector) {
        mContext = context;
        mLooper = looper;
        mWifiThreadRunner = new WifiThreadRunner(new Handler(looper));
        mScannerImplFactory = scannerImplFactory;
        mBatteryStats = batteryStats;
        mClients = new ArrayMap<>();
        mScannerImpls = new ArrayMap<>();
        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        mWifiMetrics = wifiInjector.getWifiMetrics();
        mClock = wifiInjector.getClock();
        mLog = wifiInjector.makeLog(TAG);
        mWifiPermissionsUtil = wifiInjector.getWifiPermissionsUtil();
        mWifiNative = wifiInjector.getWifiNative();
        mDeviceConfigFacade = wifiInjector.getDeviceConfigFacade();
        mWifiGlobals = wifiInjector.getWifiGlobals();
        // Wifi service is always started before other wifi services. So, there is no problem
        // obtaining WifiManager in the constructor here.
        mWifiManager = mContext.getSystemService(WifiManager.class);
        mPreviousSchedule = null;
        mLastCallerInfoManager = wifiInjector.getLastCallerInfoManager();
    }

    public void startService() {
        mWifiThreadRunner.post(() -> {
            WifiLocalServices.addService(WifiScannerInternal.class, new LocalService());
            mBackgroundScanStateMachine = new WifiBackgroundScanStateMachine(mLooper);
            mSingleScanStateMachine = new WifiSingleScanStateMachine(mLooper);
            mPnoScanStateMachine = new WifiPnoScanStateMachine(mLooper);

            mBackgroundScanStateMachine.start();
            mSingleScanStateMachine.start();
            mPnoScanStateMachine.start();
        });
    }

    /**
     * Checks if all the channels provided by the new impl is already satisfied by an existing impl.
     *
     * Note: This only handles the cases where the 2 ifaces are on different chips with
     * distinctly different bands supported on both. If there are cases where
     * the 2 ifaces support overlapping bands, then we probably need to rework this.
     * For example: wlan0 supports 2.4G only, wlan1 supports 2.4G + 5G + DFS.
     * In the above example, we should teardown wlan0 impl when wlan1 impl is created
     * because wlan1 impl can already handle all the supported bands.
     * Ignoring this for now since we don't foresee this requirement in the near future.
     */
    private boolean doesAnyExistingImplSatisfy(WifiScannerImpl newImpl) {
        for (WifiScannerImpl existingImpl : mScannerImpls.values()) {
            if (existingImpl.getChannelHelper().satisfies(newImpl.getChannelHelper())) {
                return true;
            }
        }
        return false;
    }

    private void setupScannerImpls() {
        Set<String> ifaceNames = mWifiNative.getClientInterfaceNames();
        if (ArrayUtils.isEmpty(ifaceNames)) {
            loge("Failed to retrieve client interface names");
            return;
        }
        Set<String> ifaceNamesOfImplsAlreadySetup = mScannerImpls.keySet();
        if (ifaceNames.equals(ifaceNamesOfImplsAlreadySetup)) {
            // Scanner Impls already exist for all ifaces (back to back CMD_ENABLE sent?).
            Log.i(TAG, "scanner impls already exists");
            return;
        }
        // set of impls to teardown.
        Set<String> ifaceNamesOfImplsToTeardown = new ArraySet<>(ifaceNamesOfImplsAlreadySetup);
        ifaceNamesOfImplsToTeardown.removeAll(ifaceNames);
        // set of impls to be considered for setup.
        Set<String> ifaceNamesOfImplsToSetup = new ArraySet<>(ifaceNames);
        ifaceNamesOfImplsToSetup.removeAll(ifaceNamesOfImplsAlreadySetup);

        for (String ifaceName : ifaceNamesOfImplsToTeardown) {
            WifiScannerImpl impl = mScannerImpls.remove(ifaceName);
            if (impl == null) continue; // should never happen
            impl.cleanup();
            Log.i(TAG, "Removed an impl for " + ifaceName);
        }
        for (String ifaceName : ifaceNamesOfImplsToSetup) {
            WifiScannerImpl impl = mScannerImplFactory.create(mContext, mLooper, mClock, ifaceName);
            if (impl == null) {
                loge("Failed to create scanner impl for " + ifaceName);
                continue;
            }
            // If this new scanner impl does not offer any new bands to scan, then we should
            // ignore it.
            if (!doesAnyExistingImplSatisfy(impl)) {
                mScannerImpls.put(ifaceName, impl);
                Log.i(TAG, "Created a new impl for " + ifaceName);
            } else {
                Log.i(TAG, "All the channels on the new impl for iface " + ifaceName
                        + " are already satisfied by an existing impl. Skipping..");
                impl.cleanup(); // cleanup the impl before discarding.
            }
        }
    }

    private void teardownScannerImpls() {
        for (Map.Entry<String, WifiScannerImpl> entry : mScannerImpls.entrySet()) {
            WifiScannerImpl impl = entry.getValue();
            String ifaceName = entry.getKey();
            if (impl == null) continue; // should never happen
            impl.cleanup();
            Log.i(TAG, "Removed an impl for " + ifaceName);
        }
        mScannerImpls.clear();
    }

    private WorkSource computeWorkSource(ClientInfo ci, WorkSource requestedWorkSource) {
        if (requestedWorkSource != null && !requestedWorkSource.isEmpty()) {
            return requestedWorkSource.withoutNames();
        }

        if (ci.getUid() > 0) {
            return new WorkSource(ci.getUid());
        }

        // We can't construct a sensible WorkSource because the one supplied to us was empty and
        // we don't have a valid UID for the given client.
        loge("Unable to compute workSource for client: " + ci + ", requested: "
                + requestedWorkSource);
        return new WorkSource();
    }

    private class RequestInfo<T> {
        final ClientInfo clientInfo;
        final WorkSource workSource;
        final T settings;

        RequestInfo(ClientInfo clientInfo, WorkSource requestedWorkSource, T settings) {
            this.clientInfo = clientInfo;
            this.settings = settings;
            this.workSource = computeWorkSource(clientInfo, requestedWorkSource);
        }
    }

    private class RequestList<T> extends ArrayList<RequestInfo<T>> {
        void addRequest(ClientInfo ci, WorkSource reqworkSource, T settings) {
            add(new RequestInfo<T>(ci, reqworkSource, settings));
        }

        T removeRequest(ClientInfo ci) {
            T removed = null;
            Iterator<RequestInfo<T>> iter = iterator();
            while (iter.hasNext()) {
                RequestInfo<T> entry = iter.next();
                if (entry.clientInfo == ci) {
                    removed = entry.settings;
                    iter.remove();
                }
            }
            return removed;
        }

        Collection<T> getAllSettings() {
            ArrayList<T> settingsList = new ArrayList<>();
            Iterator<RequestInfo<T>> iter = iterator();
            while (iter.hasNext()) {
                RequestInfo<T> entry = iter.next();
                settingsList.add(entry.settings);
            }
            return settingsList;
        }

        Collection<T> getAllSettingsForClient(ClientInfo ci) {
            ArrayList<T> settingsList = new ArrayList<>();
            Iterator<RequestInfo<T>> iter = iterator();
            while (iter.hasNext()) {
                RequestInfo<T> entry = iter.next();
                if (entry.clientInfo == ci) {
                    settingsList.add(entry.settings);
                }
            }
            return settingsList;
        }

        void removeAllForClient(ClientInfo ci) {
            Iterator<RequestInfo<T>> iter = iterator();
            while (iter.hasNext()) {
                RequestInfo<T> entry = iter.next();
                if (entry.clientInfo == ci) {
                    iter.remove();
                }
            }
        }

        WorkSource createMergedWorkSource() {
            WorkSource mergedSource = new WorkSource();
            for (RequestInfo<T> entry : this) {
                mergedSource.add(entry.workSource);
            }
            return mergedSource;
        }
    }

    /**
     * State machine that holds the state of single scans. Scans should only be active in the
     * ScanningState. The pending scans and active scans maps are swapped when entering
     * ScanningState. Any requests queued while scanning will be placed in the pending queue and
     * executed after transitioning back to IdleState.
     */
    class WifiSingleScanStateMachine extends StateMachine {
        /**
         * Maximum age of results that we return from our cache via
         * {@link WifiScanner#getScanResults()}.
         * This is currently set to 3 minutes to restore parity with the wpa_supplicant's scan
         * result cache expiration policy. (See b/62253332 for details)
         */
        @VisibleForTesting
        public static final int CACHED_SCAN_RESULTS_MAX_AGE_IN_MILLIS = 180 * 1000;
        /**
         * Alarm Tag to use for the delayed indication of emergency scan end.
         */
        @VisibleForTesting
        public static final String EMERGENCY_SCAN_END_INDICATION_ALARM_TAG =
                TAG + "EmergencyScanEnd";
        /**
         * Alarm timeout to use for the delayed indication of emergency scan end.
         */
        private static final int EMERGENCY_SCAN_END_INDICATION_DELAY_MILLIS = 15_000;
        /**
         * Alarm listener to use for the delayed indication of emergency scan end.
         */
        private final AlarmManager.OnAlarmListener mEmergencyScanEndIndicationListener =
                () -> mWifiManager.setEmergencyScanRequestInProgress(false);

        private final DefaultState mDefaultState = new DefaultState();
        private final DriverStartedState mDriverStartedState = new DriverStartedState();
        private final IdleState  mIdleState  = new IdleState();
        private final ScanningState  mScanningState  = new ScanningState();

        private WifiNative.ScanSettings mActiveScanSettings = null;
        private RequestList<ScanSettings> mActiveScans = new RequestList<>();
        private RequestList<ScanSettings> mPendingScans = new RequestList<>();

        // Scan results cached from the last full single scan request.
        private final List<ScanResult> mCachedScanResults = new ArrayList<>();

        // Tracks scan requests across multiple scanner impls.
        private final ScannerImplsTracker mScannerImplsTracker;

        WifiSingleScanStateMachine(Looper looper) {
            super("WifiSingleScanStateMachine", looper);

            mScannerImplsTracker = new ScannerImplsTracker();

            setLogRecSize(128);
            setLogOnlyTransitions(false);

            // CHECKSTYLE:OFF IndentationCheck
            addState(mDefaultState);
                addState(mDriverStartedState, mDefaultState);
                    addState(mIdleState, mDriverStartedState);
                    addState(mScanningState, mDriverStartedState);
            // CHECKSTYLE:ON IndentationCheck

            setInitialState(mDefaultState);
        }

        /**
         * Tracks a single scan request across all the available scanner impls.
         *
         * a) Initiates the scan using the same ScanSettings across all the available impls.
         * b) Waits for all the impls to report the status of the scan request (success or failure).
         * c) Calculates a consolidated scan status and sends the results if successful.
         * Note: If there are failures on some of the scanner impls, we ignore them since we will
         * get some scan results from the other successful impls. We don't declare total scan
         * failures, unless all the scanner impls fail.
         */
        private final class ScannerImplsTracker {
            private final class ScanEventHandler implements WifiNative.ScanEventHandler {
                private final String mImplIfaceName;
                ScanEventHandler(@NonNull String implIfaceName) {
                    mImplIfaceName = implIfaceName;
                }

                /**
                 * Called to indicate a change in state for the current scan.
                 * Will dispatch a corresponding event to the state machine
                 */
                @Override
                public void onScanStatus(int event) {
                    if (DBG) {
                        localLog("onScanStatus event received, event=" + event
                                + ", iface=" + mImplIfaceName);
                    }
                    switch (event) {
                        case WifiNative.WIFI_SCAN_RESULTS_AVAILABLE:
                        case WifiNative.WIFI_SCAN_THRESHOLD_NUM_SCANS:
                        case WifiNative.WIFI_SCAN_THRESHOLD_PERCENT:
                            reportScanStatusForImpl(mImplIfaceName, STATUS_SUCCEEDED,
                                    WifiScanner.REASON_SUCCEEDED);
                            break;
                        case WifiNative.WIFI_SCAN_FAILED:
                            reportScanStatusForImpl(mImplIfaceName, STATUS_FAILED,
                                    WifiScanner.REASON_UNSPECIFIED);
                            break;
                        default:
                            Log.e(TAG, "Unknown scan status event: " + event);
                            break;
                    }
                }

                /**
                 * Called for each full scan result if requested
                 */
                @Override
                public void onFullScanResult(ScanResult fullScanResult, int bucketsScanned) {
                    if (DBG) localLog("onFullScanResult received on iface " + mImplIfaceName);
                    reportFullScanResultForImpl(mImplIfaceName, fullScanResult, bucketsScanned);
                }

                @Override
                public void onScanPaused(ScanData[] scanData) {
                    // should not happen for single scan
                    Log.e(TAG, "Got scan paused for single scan");
                }

                @Override
                public void onScanRestarted() {
                    // should not happen for single scan
                    Log.e(TAG, "Got scan restarted for single scan");
                }

                /**
                 * Called to indicate a scan failure
                 */
                @Override
                public void onScanRequestFailed(int errorCode) {
                    reportScanStatusForImpl(mImplIfaceName, STATUS_FAILED, errorCode);
                }
            }

            private static final int STATUS_PENDING = 0;
            private static final int STATUS_SUCCEEDED = 1;
            private static final int STATUS_FAILED = 2;

            // Tracks scan status per impl.
            Map<String, Integer> mStatusPerImpl = new ArrayMap<>();

            /**
             * Triggers a new scan on all the available scanner impls.
             * @return true if the scan succeeded on any of the impl, false otherwise.
             */
            public boolean startSingleScan(WifiNative.ScanSettings scanSettings) {
                mStatusPerImpl.clear();
                boolean anySuccess = false;
                for (Map.Entry<String, WifiScannerImpl> entry : mScannerImpls.entrySet()) {
                    String ifaceName = entry.getKey();
                    WifiScannerImpl impl = entry.getValue();
                    boolean success = impl.startSingleScan(
                            scanSettings, new ScanEventHandler(ifaceName));
                    if (!success) {
                        Log.e(TAG, "Failed to start single scan on " + ifaceName);
                        mStatusPerImpl.put(ifaceName, STATUS_FAILED);
                        continue;
                    }
                    mStatusPerImpl.put(ifaceName, STATUS_PENDING);
                    anySuccess = true;
                }
                return anySuccess;
            }

            /**
             * Returns the latest scan results from all the available scanner impls.
             * @return Consolidated list of scan results from all the impl.
             */
            public @Nullable ScanData getLatestSingleScanResults() {
                ScanData consolidatedScanData = null;
                for (WifiScannerImpl impl : mScannerImpls.values()) {
                    Integer ifaceStatus = mStatusPerImpl.get(impl.getIfaceName());
                    if (ifaceStatus == null || ifaceStatus != STATUS_SUCCEEDED) {
                        continue;
                    }
                    ScanData scanData = impl.getLatestSingleScanResults();
                    if (consolidatedScanData == null) {
                        consolidatedScanData = new ScanData(scanData);
                    } else {
                        consolidatedScanData.addResults(scanData.getResults());
                    }
                }
                return consolidatedScanData;
            }

            private void reportFullScanResultForImpl(@NonNull String implIfaceName,
                    ScanResult fullScanResult, int bucketsScanned) {
                Integer status = mStatusPerImpl.get(implIfaceName);
                if (status != null && status == STATUS_PENDING) {
                    sendMessage(CMD_FULL_SCAN_RESULTS, 0, bucketsScanned, fullScanResult);
                }
            }

            private int getConsolidatedStatus() {
                boolean anyPending = mStatusPerImpl.values().stream()
                        .anyMatch(status -> status == STATUS_PENDING);
                // at-least one impl status is still pending.
                if (anyPending) return STATUS_PENDING;

                boolean anySuccess = mStatusPerImpl.values().stream()
                        .anyMatch(status -> status == STATUS_SUCCEEDED);
                // one success is good enough to declare consolidated success.
                if (anySuccess) {
                    return STATUS_SUCCEEDED;
                } else {
                    // all failed.
                    return STATUS_FAILED;
                }
            }

            private void reportScanStatusForImpl(@NonNull String implIfaceName, int newStatus,
                    int statusCode) {
                Integer currentStatus = mStatusPerImpl.get(implIfaceName);
                if (currentStatus != null && currentStatus == STATUS_PENDING) {
                    mStatusPerImpl.put(implIfaceName, newStatus);
                }
                // Now check if all the scanner impls scan status is available.
                int consolidatedStatus = getConsolidatedStatus();
                if (consolidatedStatus == STATUS_SUCCEEDED) {
                    sendMessage(CMD_SCAN_RESULTS_AVAILABLE);
                } else if (consolidatedStatus == STATUS_FAILED) {
                    sendMessage(CMD_SCAN_FAILED, statusCode);
                }
            }
        }

        /**
         * Helper method to handle the scan start message.
         */
        private void handleScanStartMessage(ClientInfo ci, ScanParams scanParams) {
            if (ci == null) {
                logCallback("singleScanInvalidRequest", ci, "null params");
                return;
            }
            ScanSettings scanSettings = scanParams.settings;
            WorkSource workSource = scanParams.workSource;
            if (validateScanRequest(ci, scanSettings)) {
                if (getCurrentState() == mDefaultState && !scanSettings.ignoreLocationSettings) {
                    // Reject regular scan requests if scanning is disabled.
                    ci.replyFailed(WifiScanner.REASON_UNSPECIFIED, "not available");
                    ci.cleanup();
                    return;
                }
                mWifiMetrics.incrementOneshotScanCount();
                if ((scanSettings.band & WifiScanner.WIFI_BAND_5_GHZ_DFS_ONLY) != 0) {
                    mWifiMetrics.incrementOneshotScanWithDfsCount();
                }
                logScanRequest("addSingleScanRequest", ci, workSource,
                        scanSettings, null);
                ci.replySucceeded();

                if (scanSettings.ignoreLocationSettings) {
                    // Inform wifi manager that an emergency scan is in progress (regardless of
                    // whether scanning is currently enabled or not). This ensures that
                    // the wifi chip remains on for the duration of this scan.
                    mWifiManager.setEmergencyScanRequestInProgress(true);
                }

                if (getCurrentState() == mScanningState) {
                    // If there is an active scan that will fulfill the scan request then
                    // mark this request as an active scan, otherwise mark it pending.
                    if (activeScanSatisfies(scanSettings)) {
                        mActiveScans.addRequest(ci, workSource, scanSettings);
                    } else {
                        mPendingScans.addRequest(ci, workSource, scanSettings);
                    }
                } else if (getCurrentState() == mIdleState) {
                    // If were not currently scanning then try to start a scan. Otherwise
                    // this scan will be scheduled when transitioning back to IdleState
                    // after finishing the current scan.
                    mPendingScans.addRequest(ci, workSource, scanSettings);
                    tryToStartNewScan();
                } else if (getCurrentState() == mDefaultState) {
                    // If scanning is disabled and the request is for emergency purposes
                    // (checked above), add to pending list. this scan will be scheduled when
                    // transitioning to IdleState when wifi manager enables scanning as a part of
                    // processing WifiManager.setEmergencyScanRequestInProgress(true)
                    mPendingScans.addRequest(ci, workSource, scanSettings);
                }
            } else {
                logCallback("singleScanInvalidRequest", ci, "bad request");
                ci.replyFailed(WifiScanner.REASON_INVALID_REQUEST, "bad request");
                ci.cleanup();
                mWifiMetrics.incrementScanReturnEntry(
                        WifiMetricsProto.WifiLog.SCAN_FAILURE_INVALID_CONFIGURATION, 1);
            }
        }

        class DefaultState extends State {
            @Override
            public void enter() {
                mActiveScans.clear();
                mPendingScans.clear();
            }

            @Override
            public boolean processMessage(Message msg) {
                switch (msg.what) {
                    case WifiScanner.CMD_ENABLE:
                        if (mScannerImpls.isEmpty()) {
                            loge("Failed to start single scan state machine because scanner impl"
                                    + " is null");
                            return HANDLED;
                        }
                        transitionTo(mIdleState);
                        return HANDLED;
                    case WifiScanner.CMD_DISABLE:
                        transitionTo(mDefaultState);
                        return HANDLED;
                    case WifiScanner.CMD_START_SINGLE_SCAN:
                        ScanParams scanParams = (ScanParams) msg.obj;
                        if (scanParams != null) {
                            ClientInfo ci = mClients.get(scanParams.listener);
                            handleScanStartMessage(ci, scanParams);
                        }
                        return HANDLED;
                    case WifiScanner.CMD_STOP_SINGLE_SCAN:
                        scanParams = (ScanParams) msg.obj;
                        if (scanParams != null) {
                            ClientInfo ci = mClients.get(scanParams.listener);
                            removeSingleScanRequests(ci);
                        }
                        return HANDLED;
                    case CMD_SCAN_RESULTS_AVAILABLE:
                        if (DBG) localLog("ignored scan results available event");
                        return HANDLED;
                    case CMD_FULL_SCAN_RESULTS:
                        if (DBG) localLog("ignored full scan result event");
                        return HANDLED;
                    case WifiScanner.CMD_GET_SINGLE_SCAN_RESULTS:
                        // Should not handled here.
                        return HANDLED;
                    default:
                        return NOT_HANDLED;
                }
            }
        }

        /**
         * State representing when the driver is running. This state is not meant to be transitioned
         * directly, but is instead intended as a parent state of ScanningState and IdleState
         * to hold common functionality and handle cleaning up scans when the driver is shut down.
         */
        class DriverStartedState extends State {
            @Override
            public void exit() {
                // clear scan results when scan mode is not active
                mCachedScanResults.clear();

                mWifiMetrics.incrementScanReturnEntry(
                        WifiMetricsProto.WifiLog.SCAN_FAILURE_INTERRUPTED,
                        mPendingScans.size());
                sendOpFailedToAllAndClear(mPendingScans, WifiScanner.REASON_UNSPECIFIED,
                        "Scan was interrupted");
            }

            @Override
            public boolean processMessage(Message msg) {
                switch (msg.what) {
                    case WifiScanner.CMD_ENABLE:
                        // Ignore if we're already in driver loaded state.
                        return HANDLED;
                    default:
                        return NOT_HANDLED;
                }
            }
        }

        class IdleState extends State {
            @Override
            public void enter() {
                tryToStartNewScan();
            }

            @Override
            public boolean processMessage(Message msg) {
                return NOT_HANDLED;
            }
        }

        class ScanningState extends State {
            private WorkSource mScanWorkSource;

            @Override
            public void enter() {
                mScanWorkSource = mActiveScans.createMergedWorkSource();
                mBatteryStats.reportWifiScanStartedFromSource(mScanWorkSource);
                Pair<int[], String[]> uidsAndTags =
                        WorkSourceUtil.getUidsAndTagsForWs(mScanWorkSource);
                WifiStatsLog.write(WifiStatsLog.WIFI_SCAN_STATE_CHANGED,
                        uidsAndTags.first, uidsAndTags.second,
                        WifiStatsLog.WIFI_SCAN_STATE_CHANGED__STATE__ON);
                mIsScanning = true;
            }

            @Override
            public void exit() {
                mActiveScanSettings = null;
                mBatteryStats.reportWifiScanStoppedFromSource(mScanWorkSource);
                Pair<int[], String[]> uidsAndTags =
                        WorkSourceUtil.getUidsAndTagsForWs(mScanWorkSource);
                WifiStatsLog.write(WifiStatsLog.WIFI_SCAN_STATE_CHANGED,
                        uidsAndTags.first, uidsAndTags.second,
                        WifiStatsLog.WIFI_SCAN_STATE_CHANGED__STATE__OFF);
                mIsScanning = false;

                // if any scans are still active (never got results available then indicate failure)
                mWifiMetrics.incrementScanReturnEntry(
                                WifiMetricsProto.WifiLog.SCAN_UNKNOWN,
                                mActiveScans.size());
                sendOpFailedToAllAndClear(mActiveScans, WifiScanner.REASON_UNSPECIFIED,
                        "Scan was interrupted");
            }

            @Override
            public boolean processMessage(Message msg) {
                switch (msg.what) {
                    case CMD_SCAN_RESULTS_AVAILABLE:
                        ScanData latestScanResults =
                                mScannerImplsTracker.getLatestSingleScanResults();
                        if (latestScanResults != null) {
                            handleScanResults(latestScanResults);
                        } else {
                            Log.e(TAG, "latest scan results null unexpectedly");
                        }
                        transitionTo(mIdleState);
                        return HANDLED;
                    case CMD_FULL_SCAN_RESULTS:
                        reportFullScanResult((ScanResult) msg.obj, /* bucketsScanned */ msg.arg2);
                        return HANDLED;
                    case CMD_SCAN_FAILED:
                        mWifiMetrics.incrementScanReturnEntry(
                                WifiMetricsProto.WifiLog.SCAN_UNKNOWN, mActiveScans.size());
                        mWifiMetrics.getScanMetrics().logScanFailed(
                                WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE);
                        sendOpFailedToAllAndClear(mActiveScans, msg.arg1,
                                scanErrorCodeToDescriptionString(msg.arg1));
                        transitionTo(mIdleState);
                        return HANDLED;
                    default:
                        return NOT_HANDLED;
                }
            }
        }

        boolean validateScanType(@WifiAnnotations.ScanType int type) {
            return (type == WifiScanner.SCAN_TYPE_LOW_LATENCY
                    || type == WifiScanner.SCAN_TYPE_LOW_POWER
                    || type == WifiScanner.SCAN_TYPE_HIGH_ACCURACY);
        }

        boolean validateScanRequest(ClientInfo ci, ScanSettings settings) {
            if (ci == null) {
                Log.d(TAG, "Failing single scan request ClientInfo not found " + ci);
                return false;
            }
            if (settings.band == WifiScanner.WIFI_BAND_UNSPECIFIED) {
                if (settings.channels == null || settings.channels.length == 0) {
                    Log.d(TAG, "Failing single scan because channel list was empty");
                    return false;
                }
            }
            if (!validateScanType(settings.type)) {
                Log.e(TAG, "Invalid scan type " + settings.type);
                return false;
            }
            if (mContext.checkPermission(
                    Manifest.permission.NETWORK_STACK, UNKNOWN_PID, ci.getUid())
                    == PERMISSION_DENIED) {
                if (!ArrayUtils.isEmpty(settings.hiddenNetworks)) {
                    Log.e(TAG, "Failing single scan because app " + ci.getUid()
                            + " does not have permission to set hidden networks");
                    return false;
                }
                if (settings.type != WifiScanner.SCAN_TYPE_LOW_LATENCY) {
                    Log.e(TAG, "Failing single scan because app " + ci.getUid()
                            + " does not have permission to set type");
                    return false;
                }
            }
            return true;
        }

        // We can coalesce a LOW_POWER/LOW_LATENCY scan request into an ongoing HIGH_ACCURACY
        // scan request. But, we can't coalesce a HIGH_ACCURACY scan request into an ongoing
        // LOW_POWER/LOW_LATENCY scan request.
        boolean activeScanTypeSatisfies(int requestScanType) {
            switch(mActiveScanSettings.scanType) {
                case WifiScanner.SCAN_TYPE_LOW_LATENCY:
                case WifiScanner.SCAN_TYPE_LOW_POWER:
                    return requestScanType != WifiScanner.SCAN_TYPE_HIGH_ACCURACY;
                case WifiScanner.SCAN_TYPE_HIGH_ACCURACY:
                    return true;
                default:
                    // This should never happen because we've validated the incoming type in
                    // |validateScanType|.
                    throw new IllegalArgumentException("Invalid scan type "
                        + mActiveScanSettings.scanType);
            }
        }

        // If there is a HIGH_ACCURACY scan request among the requests being merged, the merged
        // scan type should be HIGH_ACCURACY.
        int mergeScanTypes(int existingScanType, int newScanType) {
            switch(existingScanType) {
                case WifiScanner.SCAN_TYPE_LOW_LATENCY:
                case WifiScanner.SCAN_TYPE_LOW_POWER:
                    return newScanType;
                case WifiScanner.SCAN_TYPE_HIGH_ACCURACY:
                    return existingScanType;
                default:
                    // This should never happen because we've validated the incoming type in
                    // |validateScanType|.
                    throw new IllegalArgumentException("Invalid scan type " + existingScanType);
            }
        }

        private boolean mergeRnrSetting(boolean enable6GhzRnr, ScanSettings scanSettings) {
            if (!SdkLevel.isAtLeastS()) {
                return false;
            }
            if (enable6GhzRnr) {
                return true;
            }
            int rnrSetting = scanSettings.getRnrSetting();
            if (rnrSetting == WifiScanner.WIFI_RNR_ENABLED) {
                return true;
            }
            if (rnrSetting == WifiScanner.WIFI_RNR_ENABLED_IF_WIFI_BAND_6_GHZ_SCANNED) {
                return ChannelHelper.is6GhzBandIncluded(scanSettings.band);
            }
            return false;
        }

        private void mergeVendorIes(List<ScanResult.InformationElement> existingVendorIes,
                ScanSettings scanSettings) {
            if (!SdkLevel.isAtLeastU()) {
                return;
            }
            for (ScanResult.InformationElement ie : scanSettings.getVendorIes()) {
                if (!existingVendorIes.contains(ie)) {
                    existingVendorIes.add(ie);
                }
            }
        }

        boolean activeScanSatisfies(ScanSettings settings) {
            if (mActiveScanSettings == null) {
                return false;
            }

            if (!activeScanTypeSatisfies(settings.type)) {
                return false;
            }

            // there is always one bucket for a single scan
            WifiNative.BucketSettings activeBucket = mActiveScanSettings.buckets[0];

            // validate that all requested channels are being scanned
            ChannelCollection activeChannels = mChannelHelper.createChannelCollection();
            activeChannels.addChannels(activeBucket);
            if (!activeChannels.containsSettings(settings)) {
                return false;
            }

            // if the request is for a full scan, but there is no ongoing full scan
            if ((settings.reportEvents & WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT) != 0
                    && (activeBucket.report_events & WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT)
                    == 0) {
                return false;
            }

            if (!ArrayUtils.isEmpty(settings.hiddenNetworks)) {
                if (ArrayUtils.isEmpty(mActiveScanSettings.hiddenNetworks)) {
                    return false;
                }
                List<WifiNative.HiddenNetwork> activeHiddenNetworks = new ArrayList<>();
                for (WifiNative.HiddenNetwork hiddenNetwork : mActiveScanSettings.hiddenNetworks) {
                    activeHiddenNetworks.add(hiddenNetwork);
                }
                for (ScanSettings.HiddenNetwork hiddenNetwork : settings.hiddenNetworks) {
                    WifiNative.HiddenNetwork nativeHiddenNetwork = new WifiNative.HiddenNetwork();
                    nativeHiddenNetwork.ssid = hiddenNetwork.ssid;
                    if (!activeHiddenNetworks.contains(nativeHiddenNetwork)) {
                        return false;
                    }
                }
            }

            return true;
        }

        void removeSingleScanRequests(ClientInfo ci) {
            if (ci != null) {
                logScanRequest("removeSingleScanRequests", ci, null, null, null);
                mPendingScans.removeAllForClient(ci);
                mActiveScans.removeAllForClient(ci);
            }
        }

        void tryToStartNewScan() {
            if (mPendingScans.size() == 0) { // no pending requests
                return;
            }
            mChannelHelper.updateChannels();
            // TODO move merging logic to a scheduler
            WifiNative.ScanSettings settings = new WifiNative.ScanSettings();
            settings.num_buckets = 1;
            WifiNative.BucketSettings bucketSettings = new WifiNative.BucketSettings();
            bucketSettings.bucket = 0;
            bucketSettings.period_ms = 0;
            bucketSettings.report_events = WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN;

            ChannelCollection channels = mChannelHelper.createChannelCollection();
            WifiScanner.ChannelSpec[][] available6GhzChannels =
                    mChannelHelper.getAvailableScanChannels(WifiScanner.WIFI_BAND_6_GHZ);
            boolean are6GhzChannelsAvailable = available6GhzChannels.length > 0
                    && available6GhzChannels[0].length > 0;
            List<WifiNative.HiddenNetwork> hiddenNetworkList = new ArrayList<>();
            List<ScanResult.InformationElement> vendorIesList = new ArrayList<>();
            for (RequestInfo<ScanSettings> entry : mPendingScans) {
                settings.scanType = mergeScanTypes(settings.scanType, entry.settings.type);
                if (are6GhzChannelsAvailable) {
                    settings.enable6GhzRnr = mergeRnrSetting(
                            settings.enable6GhzRnr, entry.settings);
                } else {
                    settings.enable6GhzRnr = false;
                }
                channels.addChannels(entry.settings);
                for (ScanSettings.HiddenNetwork srcNetwork : entry.settings.hiddenNetworks) {
                    WifiNative.HiddenNetwork hiddenNetwork = new WifiNative.HiddenNetwork();
                    hiddenNetwork.ssid = srcNetwork.ssid;
                    hiddenNetworkList.add(hiddenNetwork);
                }
                mergeVendorIes(vendorIesList, entry.settings);
                if ((entry.settings.reportEvents & WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT)
                        != 0) {
                    bucketSettings.report_events |= WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT;
                }

                if (entry.clientInfo != null) {
                    mWifiMetrics.getScanMetrics().setClientUid(entry.clientInfo.mUid);
                }
                mWifiMetrics.getScanMetrics().setWorkSource(entry.workSource);
            }

            if (hiddenNetworkList.size() > 0) {
                settings.hiddenNetworks = new WifiNative.HiddenNetwork[hiddenNetworkList.size()];
                int numHiddenNetworks = 0;
                for (WifiNative.HiddenNetwork hiddenNetwork : hiddenNetworkList) {
                    settings.hiddenNetworks[numHiddenNetworks++] = hiddenNetwork;
                }
            }
            settings.vendorIes = getVendorIesBytesFromVendorIesList(vendorIesList);

            channels.fillBucketSettings(bucketSettings, Integer.MAX_VALUE);
            settings.buckets = new WifiNative.BucketSettings[] {bucketSettings};

            if (mScannerImplsTracker.startSingleScan(settings)) {
                mWifiMetrics.getScanMetrics().logScanStarted(
                        WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE);

                // store the active scan settings
                mActiveScanSettings = settings;
                // swap pending and active scan requests
                RequestList<ScanSettings> tmp = mActiveScans;
                mActiveScans = mPendingScans;
                mPendingScans = tmp;
                // make sure that the pending list is clear
                mPendingScans.clear();
                transitionTo(mScanningState);
            } else {
                mWifiMetrics.incrementScanReturnEntry(
                        WifiMetricsProto.WifiLog.SCAN_UNKNOWN, mPendingScans.size());
                mWifiMetrics.getScanMetrics().logScanFailedToStart(
                        WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE);

                // notify and cancel failed scans
                sendOpFailedToAllAndClear(mPendingScans, WifiScanner.REASON_UNSPECIFIED,
                        "Failed to start single scan");
            }
        }

        void sendOpFailedToAllAndClear(RequestList<?> clientHandlers, int reason,
                String description) {
            for (RequestInfo<?> entry : clientHandlers) {
                logCallback("singleScanFailed", entry.clientInfo,
                        "reason=" + reason + ", " + description);
                try {
                    entry.clientInfo.mListener.onFailure(reason, description);
                } catch (Exception e) {
                    loge("Failed to call onFailure: " + entry.clientInfo);
                }
                entry.clientInfo.unregister();
            }
            clientHandlers.clear();
        }

        void reportFullScanResult(@NonNull ScanResult result, int bucketsScanned) {
            for (RequestInfo<ScanSettings> entry : mActiveScans) {
                if (ScanScheduleUtil.shouldReportFullScanResultForSettings(mChannelHelper,
                                result, bucketsScanned, entry.settings, -1)) {
                    entry.clientInfo.reportEvent((listener) -> {
                        try {
                            listener.onFullResult(result);
                        } catch (RemoteException e) {
                            loge("Failed to call onFullResult: " + entry.clientInfo);
                        }
                    });
                }
            }

            for (RequestInfo<Void> entry : mSingleScanListeners) {
                entry.clientInfo.reportEvent((listener) -> {
                    try {
                        listener.onFullResult(result);
                    } catch (RemoteException e) {
                        loge("Failed to call onFullResult: " + entry.clientInfo);
                    }
                });
            }
        }

        void reportScanResults(@NonNull ScanData results) {
            if (results != null && results.getResults() != null) {
                if (results.getResults().length > 0) {
                    mWifiMetrics.incrementNonEmptyScanResultCount();
                } else {
                    mWifiMetrics.incrementEmptyScanResultCount();
                }
            }
            ScanData[] allResults = new ScanData[] {results};
            for (RequestInfo<ScanSettings> entry : mActiveScans) {
                ScanData[] resultsToDeliver = ScanScheduleUtil.filterResultsForSettings(
                        mChannelHelper, allResults, entry.settings, -1);
                logCallback("singleScanResults", entry.clientInfo,
                        describeForLog(resultsToDeliver));
                entry.clientInfo.reportEvent((listener) -> {
                    try {
                        listener.onResults(resultsToDeliver);
                        // make sure the handler is removed
                        listener.onSingleScanCompleted();
                    } catch (RemoteException e) {
                        loge("Failed to call onResult: " + entry.clientInfo);
                    }
                });
            }

            for (RequestInfo<Void> entry : mSingleScanListeners) {
                logCallback("singleScanResults", entry.clientInfo,
                        describeForLog(allResults));
                entry.clientInfo.reportEvent((listener) -> {
                    try {
                        listener.onResults(allResults);
                    } catch (RemoteException e) {
                        loge("Failed to call onResult: " + entry.clientInfo);
                    }
                });
            }
        }

        void handleScanResults(@NonNull ScanData results) {
            mWifiMetrics.getScanMetrics().logScanSucceeded(
                    WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE, results.getResults().length);
            mWifiMetrics.incrementScanReturnEntry(
                    WifiMetricsProto.WifiLog.SCAN_SUCCESS, mActiveScans.size());
            reportScanResults(results);
            // Cache full band (with DFS or not) scan results.
            if (WifiScanner.isFullBandScan(results.getScannedBandsInternal(), true)) {
                mCachedScanResults.clear();
                mCachedScanResults.addAll(Arrays.asList(results.getResults()));
            }
            if (mActiveScans.stream().anyMatch(rI -> rI.settings.ignoreLocationSettings)) {
                // We were processing an emergency scan, post an alarm to inform WifiManager the
                // end of that scan processing. If another scan is processed before the alarm fires,
                // this timer is restarted (AlarmManager.set() using the same listener resets the
                // timer). This delayed indication of emergency scan end prevents
                // quick wifi toggle on/off if there is a burst of emergency scans when wifi is off.
                mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        mClock.getElapsedSinceBootMillis()
                                + EMERGENCY_SCAN_END_INDICATION_DELAY_MILLIS,
                        EMERGENCY_SCAN_END_INDICATION_ALARM_TAG,
                        mEmergencyScanEndIndicationListener, getHandler());
            }
            for (RequestInfo<ScanSettings> entry : mActiveScans) {
                entry.clientInfo.unregister();
            }
            mActiveScans.clear();
        }

        List<ScanResult> getCachedScanResultsAsList() {
            return mCachedScanResults;
        }

        /**
         * Filter out  any scan results that are older than
         * {@link #CACHED_SCAN_RESULTS_MAX_AGE_IN_MILLIS}.
         *
         * @return Filtered list of scan results.
         */
        public List<ScanResult> filterCachedScanResultsByAge() {
            // Using ScanResult.timestamp here to ensure that we use the same fields as
            // WificondScannerImpl for filtering stale results.
            long currentTimeInMillis = mClock.getElapsedSinceBootMillis();
            return mCachedScanResults.stream()
                    .filter(scanResult
                            -> ((currentTimeInMillis - (scanResult.timestamp / 1000))
                            < CACHED_SCAN_RESULTS_MAX_AGE_IN_MILLIS)).collect(Collectors.toList());
        }
    }

    // TODO(b/71855918): Remove this bg scan state machine and its dependencies.
    // Note: bgscan will not support multiple scanner impls (will pick any).
    class WifiBackgroundScanStateMachine extends StateMachine {

        private final DefaultState mDefaultState = new DefaultState();
        private final StartedState mStartedState = new StartedState();
        private final PausedState  mPausedState  = new PausedState();

        private final RequestList<ScanSettings> mActiveBackgroundScans = new RequestList<>();

        private WifiScannerImpl mScannerImpl;

        WifiBackgroundScanStateMachine(Looper looper) {
            super("WifiBackgroundScanStateMachine", looper);

            setLogRecSize(512);
            setLogOnlyTransitions(false);

            // CHECKSTYLE:OFF IndentationCheck
            addState(mDefaultState);
                addState(mStartedState, mDefaultState);
                addState(mPausedState, mDefaultState);
            // CHECKSTYLE:ON IndentationCheck

            setInitialState(mDefaultState);
        }

        public Collection<ScanSettings> getBackgroundScanSettings(ClientInfo ci) {
            return mActiveBackgroundScans.getAllSettingsForClient(ci);
        }

        public void removeBackgroundScanSettings(ClientInfo ci) {
            mActiveBackgroundScans.removeAllForClient(ci);
            updateSchedule();
        }

        private final class ScanEventHandler implements WifiNative.ScanEventHandler {
            private final String mImplIfaceName;

            ScanEventHandler(@NonNull String implIfaceName) {
                mImplIfaceName = implIfaceName;
            }

            @Override
            public void onScanStatus(int event) {
                if (DBG) localLog("onScanStatus event received, event=" + event);
                switch (event) {
                    case WifiNative.WIFI_SCAN_RESULTS_AVAILABLE:
                    case WifiNative.WIFI_SCAN_THRESHOLD_NUM_SCANS:
                    case WifiNative.WIFI_SCAN_THRESHOLD_PERCENT:
                        sendMessage(CMD_SCAN_RESULTS_AVAILABLE);
                        break;
                    case WifiNative.WIFI_SCAN_FAILED:
                        sendMessage(CMD_SCAN_FAILED, WifiScanner.REASON_UNSPECIFIED);
                        break;
                    default:
                        Log.e(TAG, "Unknown scan status event: " + event);
                        break;
                }
            }

            @Override
            public void onFullScanResult(ScanResult fullScanResult, int bucketsScanned) {
                if (DBG) localLog("onFullScanResult received");
                sendMessage(CMD_FULL_SCAN_RESULTS, 0, bucketsScanned, fullScanResult);
            }

            @Override
            public void onScanPaused(ScanData[] scanData) {
                if (DBG) localLog("onScanPaused received");
                sendMessage(CMD_SCAN_PAUSED, scanData);
            }

            @Override
            public void onScanRestarted() {
                if (DBG) localLog("onScanRestarted received");
                sendMessage(CMD_SCAN_RESTARTED);
            }

            /**
             * Called to indicate a scan failure
             */
            @Override
            public void onScanRequestFailed(int errorCode) {
                sendMessage(CMD_SCAN_FAILED, errorCode);
            }
        }

        class DefaultState extends State {
            @Override
            public void enter() {
                if (DBG) localLog("DefaultState");
                mActiveBackgroundScans.clear();
            }

            @Override
            public boolean processMessage(Message msg) {
                switch (msg.what) {
                    case WifiScanner.CMD_ENABLE:
                        if (mScannerImpls.isEmpty()) {
                            loge("Failed to start bgscan scan state machine because scanner impl"
                                    + " is null");
                            return HANDLED;
                        }
                        // Pick any impl available and stick to it until disable.
                        mScannerImpl = mScannerImpls.entrySet().iterator().next().getValue();
                        mChannelHelper = mScannerImpl.getChannelHelper();

                        mBackgroundScheduler = new BackgroundScanScheduler(mChannelHelper);

                        WifiNative.ScanCapabilities capabilities =
                                new WifiNative.ScanCapabilities();
                        if (!mScannerImpl.getScanCapabilities(capabilities)) {
                            loge("could not get scan capabilities");
                            return HANDLED;
                        }
                        if (capabilities.max_scan_buckets <= 0) {
                            loge("invalid max buckets in scan capabilities "
                                    + capabilities.max_scan_buckets);
                            return HANDLED;
                        }
                        mBackgroundScheduler.setMaxBuckets(capabilities.max_scan_buckets);
                        mBackgroundScheduler.setMaxApPerScan(capabilities.max_ap_cache_per_scan);

                        Log.i(TAG, "wifi driver loaded with scan capabilities: "
                                + "max buckets=" + capabilities.max_scan_buckets);

                        transitionTo(mStartedState);
                        return HANDLED;
                    case WifiScanner.CMD_DISABLE:
                        Log.i(TAG, "wifi driver unloaded");
                        transitionTo(mDefaultState);
                        break;
                    case WifiScanner.CMD_START_BACKGROUND_SCAN:
                    case WifiScanner.CMD_STOP_BACKGROUND_SCAN:
                    case WifiScanner.CMD_START_SINGLE_SCAN:
                    case WifiScanner.CMD_STOP_SINGLE_SCAN:
                    case WifiScanner.CMD_GET_SCAN_RESULTS:
                        ScanParams scanParams = (ScanParams) msg.obj;
                        ClientInfo ci = mClients.get(scanParams.listener);
                        if (ci == null) {
                            loge("ClientInfo is null");
                            break;
                        }
                        ci.replyFailed(WifiScanner.REASON_UNSPECIFIED, "not available");
                        break;

                    case CMD_SCAN_RESULTS_AVAILABLE:
                        if (DBG) localLog("ignored scan results available event");
                        break;

                    case CMD_FULL_SCAN_RESULTS:
                        if (DBG) localLog("ignored full scan result event");
                        break;

                    default:
                        break;
                }

                return HANDLED;
            }
        }

        class StartedState extends State {

            @Override
            public void enter() {
                if (DBG) localLog("StartedState");
                if (mScannerImpl == null) {
                    // should never happen
                    Log.wtf(TAG, "Scanner impl unexpectedly null");
                    transitionTo(mDefaultState);
                }
            }

            @Override
            public void exit() {
                sendBackgroundScanFailedToAllAndClear(
                        WifiScanner.REASON_UNSPECIFIED, "Scan was interrupted");
                mScannerImpl = null; // reset impl
            }

            @Override
            public boolean processMessage(Message msg) {
                switch (msg.what) {
                    case WifiScanner.CMD_ENABLE:
                        Log.e(TAG, "wifi driver loaded received while already loaded");
                        // Ignore if we're already in driver loaded state.
                        return HANDLED;
                    case WifiScanner.CMD_DISABLE:
                        return NOT_HANDLED;
                    case WifiScanner.CMD_START_BACKGROUND_SCAN: {
                        ScanParams scanParams = (ScanParams) msg.obj;
                        mWifiMetrics.incrementBackgroundScanCount();
                        ClientInfo ci = mClients.get(scanParams.listener);
                        if (ci == null) {
                            loge("ClientInfo is null");
                            return HANDLED;
                        }
                        if (scanParams.settings == null) {
                            loge("params null");
                            return HANDLED;
                        }
                        if (addBackgroundScanRequest(ci, msg.arg2, scanParams.settings,
                                scanParams.workSource)) {
                            ci.replySucceeded();
                        } else {
                            ci.replyFailed(WifiScanner.REASON_INVALID_REQUEST, "bad request");
                        }
                        break;
                    }
                    case WifiScanner.CMD_STOP_BACKGROUND_SCAN:
                        ScanParams scanParams = (ScanParams) msg.obj;
                        ClientInfo ci = mClients.get(scanParams.listener);
                        removeBackgroundScanRequest(ci);
                        break;
                    case WifiScanner.CMD_GET_SCAN_RESULTS:
                        reportScanResults(mScannerImpl.getLatestBatchedScanResults(true));
                        break;
                    case CMD_SCAN_RESULTS_AVAILABLE:
                        WifiScanner.ScanData[] results = mScannerImpl.getLatestBatchedScanResults(
                                true);
                        mWifiMetrics.getScanMetrics().logScanSucceeded(
                                WifiMetrics.ScanMetrics.SCAN_TYPE_BACKGROUND,
                                results != null ? results.length : 0);
                        reportScanResults(results);
                        break;
                    case CMD_FULL_SCAN_RESULTS:
                        reportFullScanResult((ScanResult) msg.obj, /* bucketsScanned */ msg.arg2);
                        break;
                    case CMD_SCAN_PAUSED:
                        reportScanResults((ScanData[]) msg.obj);
                        transitionTo(mPausedState);
                        break;
                    case CMD_SCAN_FAILED:
                        mWifiMetrics.getScanMetrics().logScanFailed(
                                WifiMetrics.ScanMetrics.SCAN_TYPE_BACKGROUND);
                        Log.e(TAG, "WifiScanner background scan gave CMD_SCAN_FAILED");
                        sendBackgroundScanFailedToAllAndClear(
                                WifiScanner.REASON_UNSPECIFIED, "Background Scan failed");
                        break;
                    default:
                        return NOT_HANDLED;
                }

                return HANDLED;
            }
        }

        class PausedState extends State {
            @Override
            public void enter() {
                if (DBG) localLog("PausedState");
            }

            @Override
            public boolean processMessage(Message msg) {
                switch (msg.what) {
                    case CMD_SCAN_RESTARTED:
                        transitionTo(mStartedState);
                        break;
                    default:
                        deferMessage(msg);
                        break;
                }
                return HANDLED;
            }
        }

        private boolean addBackgroundScanRequest(ClientInfo ci, int handler,
                ScanSettings settings, WorkSource workSource) {
            if (ci == null) {
                Log.d(TAG, "Failing scan request ClientInfo not found " + handler);
                return false;
            }

            if (settings.periodInMs < WifiScanner.MIN_SCAN_PERIOD_MS) {
                loge("Failing scan request because periodInMs is " + settings.periodInMs
                        + ", min scan period is: " + WifiScanner.MIN_SCAN_PERIOD_MS);
                return false;
            }

            if (settings.band == WifiScanner.WIFI_BAND_UNSPECIFIED && settings.channels == null) {
                loge("Channels was null with unspecified band");
                return false;
            }

            if (settings.band == WifiScanner.WIFI_BAND_UNSPECIFIED
                    && settings.channels.length == 0) {
                loge("No channels specified");
                return false;
            }

            int minSupportedPeriodMs = mChannelHelper.estimateScanDuration(settings);
            if (settings.periodInMs < minSupportedPeriodMs) {
                loge("Failing scan request because minSupportedPeriodMs is "
                        + minSupportedPeriodMs + " but the request wants " + settings.periodInMs);
                return false;
            }

            // check truncated binary exponential back off scan settings
            if (settings.maxPeriodInMs != 0 && settings.maxPeriodInMs != settings.periodInMs) {
                if (settings.maxPeriodInMs < settings.periodInMs) {
                    loge("Failing scan request because maxPeriodInMs is " + settings.maxPeriodInMs
                            + " but less than periodInMs " + settings.periodInMs);
                    return false;
                }
                if (settings.maxPeriodInMs > WifiScanner.MAX_SCAN_PERIOD_MS) {
                    loge("Failing scan request because maxSupportedPeriodMs is "
                            + WifiScanner.MAX_SCAN_PERIOD_MS + " but the request wants "
                            + settings.maxPeriodInMs);
                    return false;
                }
                if (settings.stepCount < 1) {
                    loge("Failing scan request because stepCount is " + settings.stepCount
                            + " which is less than 1");
                    return false;
                }
            }

            logScanRequest("addBackgroundScanRequest", ci, null, settings, null);
            mWifiMetrics.getScanMetrics().setClientUid(ci.mUid);
            mWifiMetrics.getScanMetrics().setWorkSource(workSource);
            mActiveBackgroundScans.addRequest(ci, workSource, settings);

            if (updateSchedule()) {
                return true;
            } else {
                mActiveBackgroundScans.removeRequest(ci);
                localLog("Failing scan request because failed to reset scan");
                return false;
            }
        }

        private boolean updateSchedule() {
            if (mChannelHelper == null || mBackgroundScheduler == null || mScannerImpl == null) {
                loge("Failed to update schedule because WifiScanningService is not initialized");
                return false;
            }
            mChannelHelper.updateChannels();
            Collection<ScanSettings> settings = mActiveBackgroundScans.getAllSettings();

            mBackgroundScheduler.updateSchedule(settings);
            WifiNative.ScanSettings schedule = mBackgroundScheduler.getSchedule();

            if (ScanScheduleUtil.scheduleEquals(mPreviousSchedule, schedule)) {
                if (DBG) Log.d(TAG, "schedule updated with no change");
                return true;
            }

            mPreviousSchedule = schedule;

            if (schedule.num_buckets == 0) {
                mScannerImpl.stopBatchedScan();
                if (DBG) Log.d(TAG, "scan stopped");
                return true;
            } else {
                localLog("starting scan: "
                        + "base period=" + schedule.base_period_ms
                        + ", max ap per scan=" + schedule.max_ap_per_scan
                        + ", batched scans=" + schedule.report_threshold_num_scans);
                for (int b = 0; b < schedule.num_buckets; b++) {
                    WifiNative.BucketSettings bucket = schedule.buckets[b];
                    localLog("bucket " + bucket.bucket + " (" + bucket.period_ms + "ms)"
                            + "[" + bucket.report_events + "]: "
                            + ChannelHelper.toString(bucket));
                }

                if (mScannerImpl.startBatchedScan(schedule,
                        new ScanEventHandler(mScannerImpl.getIfaceName()))) {
                    if (DBG) {
                        Log.d(TAG, "scan restarted with " + schedule.num_buckets
                                + " bucket(s) and base period: " + schedule.base_period_ms);
                    }
                    mWifiMetrics.getScanMetrics().logScanStarted(
                            WifiMetrics.ScanMetrics.SCAN_TYPE_BACKGROUND);
                    return true;
                } else {
                    mPreviousSchedule = null;
                    loge("error starting scan: "
                            + "base period=" + schedule.base_period_ms
                            + ", max ap per scan=" + schedule.max_ap_per_scan
                            + ", batched scans=" + schedule.report_threshold_num_scans);
                    for (int b = 0; b < schedule.num_buckets; b++) {
                        WifiNative.BucketSettings bucket = schedule.buckets[b];
                        loge("bucket " + bucket.bucket + " (" + bucket.period_ms + "ms)"
                                + "[" + bucket.report_events + "]: "
                                + ChannelHelper.toString(bucket));
                    }
                    mWifiMetrics.getScanMetrics().logScanFailedToStart(
                            WifiMetrics.ScanMetrics.SCAN_TYPE_BACKGROUND);
                    return false;
                }
            }
        }

        private void removeBackgroundScanRequest(ClientInfo ci) {
            if (ci != null) {
                ScanSettings settings = mActiveBackgroundScans.removeRequest(ci);
                logScanRequest("removeBackgroundScanRequest", ci, null, settings, null);
                updateSchedule();
            }
        }

        private void reportFullScanResult(ScanResult result, int bucketsScanned) {
            for (RequestInfo<ScanSettings> entry : mActiveBackgroundScans) {
                ClientInfo ci = entry.clientInfo;
                ScanSettings settings = entry.settings;
                if (mBackgroundScheduler.shouldReportFullScanResultForSettings(
                                result, bucketsScanned, settings)) {
                    ScanResult newResult = new ScanResult(result);
                    if (result.informationElements != null) {
                        newResult.informationElements = result.informationElements.clone();
                    }
                    else {
                        newResult.informationElements = null;
                    }
                    entry.clientInfo.reportEvent((listener) -> {
                        try {
                            listener.onFullResult(newResult);
                        } catch (RemoteException e) {
                            loge("Failed to call onFullResult: " + ci);
                        }
                    });
                }
            }
        }

        private void reportScanResults(ScanData[] results) {
            if (results == null) {
                Log.d(TAG,"The results is null, nothing to report.");
                return;
            }
            for (ScanData result : results) {
                if (result != null && result.getResults() != null) {
                    if (result.getResults().length > 0) {
                        mWifiMetrics.incrementNonEmptyScanResultCount();
                    } else {
                        mWifiMetrics.incrementEmptyScanResultCount();
                    }
                }
            }
            for (RequestInfo<ScanSettings> entry : mActiveBackgroundScans) {
                ClientInfo ci = entry.clientInfo;
                ScanSettings settings = entry.settings;
                ScanData[] resultsToDeliver =
                        mBackgroundScheduler.filterResultsForSettings(results, settings);
                if (resultsToDeliver != null) {
                    logCallback("backgroundScanResults", ci,
                            describeForLog(resultsToDeliver));
                    entry.clientInfo.reportEvent((listener) -> {
                        try {
                            listener.onResults(resultsToDeliver);
                        } catch (RemoteException e) {
                            loge("Failed to call onFullResult: " + ci);
                        }
                    });
                }
            }
        }

        private void sendBackgroundScanFailedToAllAndClear(int reason, String description) {
            for (RequestInfo<ScanSettings> entry : mActiveBackgroundScans) {
                ClientInfo ci = entry.clientInfo;
                entry.clientInfo.reportEvent((listener) -> {
                    try {
                        listener.onFailure(reason, description);
                    } catch (RemoteException e) {
                        loge("Failed to call onFullResult: " + ci);
                    }
                });
            }
            mActiveBackgroundScans.clear();
        }
    }

    /**
     * PNO scan state machine has 5 states:
     * -Default State
     *   -Started State
     *     -Hw Pno Scan state
     *       -Single Scan state
     *
     * These are the main state transitions:
     * 1. Start at |Default State|
     * 2. Move to |Started State| when we get the |WIFI_SCAN_AVAILABLE| broadcast from WifiManager.
     * 3. When a new PNO scan request comes in:
     *   a.1. Switch to |Hw Pno Scan state| when the device supports HW PNO
     *        (This could either be HAL based ePNO or wificond based PNO).
     *   a.2. In |Hw Pno Scan state| when PNO scan results are received, check if the result
     *        contains IE (information elements). If yes, send the results to the client, else
     *        switch to |Single Scan state| and send the result to the client when the scan result
     *        is obtained.
     *
     * Note: PNO scans only work for a single client today. We don't have support in HW to support
     * multiple requests at the same time, so will need non-trivial changes to support (if at all
     * possible) in WifiScanningService.
     */
    class WifiPnoScanStateMachine extends StateMachine {

        private final DefaultState mDefaultState = new DefaultState();
        private final StartedState mStartedState = new StartedState();
        private final HwPnoScanState mHwPnoScanState = new HwPnoScanState();
        private final SwPnoScanState mSwPnoScanState = new SwPnoScanState();
        private final SingleScanState mSingleScanState = new SingleScanState();
        private InternalClientInfo mInternalClientInfo;

        private final RequestList<Pair<PnoSettings, ScanSettings>> mActivePnoScans =
                new RequestList<>();
        // Tracks scan requests across multiple scanner impls.
        private final ScannerImplsTracker mScannerImplsTracker;

        WifiPnoScanStateMachine(Looper looper) {
            super("WifiPnoScanStateMachine", looper);

            mScannerImplsTracker = new ScannerImplsTracker();

            setLogRecSize(256);
            setLogOnlyTransitions(false);

            // CHECKSTYLE:OFF IndentationCheck
            addState(mDefaultState);
                addState(mStartedState, mDefaultState);
                    addState(mHwPnoScanState, mStartedState);
                        addState(mSingleScanState, mHwPnoScanState);
                    addState(mSwPnoScanState, mStartedState);
            // CHECKSTYLE:ON IndentationCheck

            setInitialState(mDefaultState);
        }

        public void removePnoSettings(ClientInfo ci) {
            mActivePnoScans.removeAllForClient(ci);
        }

        /**
         * Tracks a PNO scan request across all the available scanner impls.
         *
         * Note: If there are failures on some of the scanner impls, we ignore them since we can
         * get a PNO match from the other successful impls. We don't declare total scan
         * failures, unless all the scanner impls fail.
         */
        private final class ScannerImplsTracker {
            private final class PnoEventHandler implements WifiNative.PnoEventHandler {
                private final String mImplIfaceName;

                PnoEventHandler(@NonNull String implIfaceName) {
                    mImplIfaceName = implIfaceName;
                }

                @Override
                public void onPnoNetworkFound(ScanResult[] results) {
                    if (DBG) localLog("onWifiPnoNetworkFound event received");
                    reportPnoNetworkFoundForImpl(mImplIfaceName, results);
                }

                @Override
                public void onPnoScanFailed() {
                    if (DBG) localLog("onWifiPnoScanFailed event received");
                    reportPnoScanFailedForImpl(mImplIfaceName);
                }
            }

            private static final int STATUS_PENDING = 0;
            private static final int STATUS_FAILED = 2;

            // Tracks scan status per impl.
            Map<String, Integer> mStatusPerImpl = new ArrayMap<>();

            /**
             * Triggers a new PNO with the specified settings on all the available scanner impls.
             * @return true if the PNO succeeded on any of the impl, false otherwise.
             */
            public boolean setHwPnoList(WifiNative.PnoSettings pnoSettings) {
                mStatusPerImpl.clear();
                boolean anySuccess = false;
                for (Map.Entry<String, WifiScannerImpl> entry : mScannerImpls.entrySet()) {
                    String ifaceName = entry.getKey();
                    WifiScannerImpl impl = entry.getValue();
                    boolean success = impl.setHwPnoList(
                            pnoSettings, new PnoEventHandler(ifaceName));
                    if (!success) {
                        Log.e(TAG, "Failed to start pno on " + ifaceName);
                        continue;
                    }
                    mStatusPerImpl.put(ifaceName, STATUS_PENDING);
                    anySuccess = true;
                }
                return anySuccess;
            }

            /**
             * Resets any ongoing PNO on all the available scanner impls.
             * @return true if the PNO stop succeeded on all of the impl, false otherwise.
             */
            public boolean resetHwPnoList() {
                boolean allSuccess = true;
                for (String ifaceName : mStatusPerImpl.keySet()) {
                    WifiScannerImpl impl = mScannerImpls.get(ifaceName);
                    if (impl == null) continue;
                    boolean success = impl.resetHwPnoList();
                    if (!success) {
                        Log.e(TAG, "Failed to stop pno on " + ifaceName);
                        allSuccess = false;
                    }
                }
                mStatusPerImpl.clear();
                return allSuccess;
            }

            /**
             * @return true if HW PNO is supported on all the available scanner impls,
             * false otherwise.
             */
            public boolean isHwPnoSupported(boolean isConnected) {
                for (WifiScannerImpl impl : mScannerImpls.values()) {
                    if (!impl.isHwPnoSupported(isConnected)) {
                        return false;
                    }
                }
                return true;
            }

            private void reportPnoNetworkFoundForImpl(@NonNull String implIfaceName,
                                                      ScanResult[] results) {
                Integer status = mStatusPerImpl.get(implIfaceName);
                if (status != null && status == STATUS_PENDING) {
                    sendMessage(CMD_PNO_NETWORK_FOUND, 0, 0, results);
                }
            }

            private int getConsolidatedStatus() {
                boolean anyPending = mStatusPerImpl.values().stream()
                        .anyMatch(status -> status == STATUS_PENDING);
                // at-least one impl status is still pending.
                if (anyPending) {
                    return STATUS_PENDING;
                } else {
                    // all failed.
                    return STATUS_FAILED;
                }
            }

            private void reportPnoScanFailedForImpl(@NonNull String implIfaceName) {
                Integer currentStatus = mStatusPerImpl.get(implIfaceName);
                if (currentStatus != null && currentStatus == STATUS_PENDING) {
                    mStatusPerImpl.put(implIfaceName, STATUS_FAILED);
                }
                // Now check if all the scanner impls scan status is available.
                int consolidatedStatus = getConsolidatedStatus();
                if (consolidatedStatus == STATUS_FAILED) {
                    sendMessage(CMD_PNO_SCAN_FAILED);
                }
            }
        }

        class DefaultState extends State {
            @Override
            public void enter() {
                if (DBG) localLog("DefaultState");
            }

            @Override
            public boolean processMessage(Message msg) {
                switch (msg.what) {
                    case WifiScanner.CMD_ENABLE:
                        if (mScannerImpls.isEmpty()) {
                            loge("Failed to start pno scan state machine because scanner impl"
                                    + " is null");
                            return HANDLED;
                        }
                        transitionTo(mStartedState);
                        break;
                    case WifiScanner.CMD_DISABLE:
                        transitionTo(mDefaultState);
                        break;
                    case WifiScanner.CMD_START_PNO_SCAN: {
                        ScanParams scanParams = (ScanParams) msg.obj;
                        try {
                            scanParams.listener.onFailure(WifiScanner.REASON_UNSPECIFIED,
                                    "not available");
                        } catch (RemoteException e) {
                            // not much we can do if message can't be sent.
                        }
                        break;
                    }
                    case WifiScanner.CMD_STOP_PNO_SCAN: {
                        ScanParams scanParams = (ScanParams) msg.obj;
                        ClientInfo ci = mClients.get(scanParams.listener);
                        if (ci == null) {
                            localLog("Pno ClientInfo is null in DefaultState");
                            break;
                        }
                        ci.replyFailed(WifiScanner.REASON_UNSPECIFIED, "not available");
                        break;
                    }
                    case CMD_PNO_NETWORK_FOUND:
                    case CMD_PNO_SCAN_FAILED:
                    case WifiScanner.CMD_SCAN_RESULT:
                    case WifiScanner.CMD_OP_FAILED:
                        loge("Unexpected message " + msg.what);
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }
        }

        class StartedState extends State {
            @Override
            public void enter() {
                if (DBG) localLog("StartedState");
            }

            @Override
            public void exit() {
                sendPnoScanFailedToAllAndClear(
                        WifiScanner.REASON_UNSPECIFIED, "Scan was interrupted");
            }

            @Override
            public boolean processMessage(Message msg) {
                switch (msg.what) {
                    case WifiScanner.CMD_ENABLE:
                        // Ignore if we're already in driver loaded state.
                        return HANDLED;
                    case WifiScanner.CMD_START_PNO_SCAN:
                        ScanParams scanParams = (ScanParams) msg.obj;
                        if (scanParams == null) {
                            loge("scan params null");
                            return HANDLED;
                        }
                        ClientInfo ci = mClients.get(scanParams.listener);
                        if (ci == null) {
                            ci = new ExternalClientInfo(msg.sendingUid, scanParams.packageName,
                                    scanParams.listener);
                            ci.register();
                        }
                        if (scanParams.pnoSettings == null || scanParams.settings == null) {
                            Log.e(TAG, "Failed to get parcelable params");
                            ci.replyFailed(WifiScanner.REASON_INVALID_REQUEST, "bad parcel params");
                            return HANDLED;
                        }
                        if (mScannerImplsTracker.isHwPnoSupported(
                                scanParams.pnoSettings.isConnected)) {
                            deferMessage(msg);
                            transitionTo(mHwPnoScanState);
                        } else if (mWifiGlobals.isSwPnoEnabled()
                                && mDeviceConfigFacade.isSoftwarePnoEnabled()) {
                            deferMessage(msg);
                            transitionTo(mSwPnoScanState);
                        } else {
                            Log.w(TAG, "PNO is not available");
                            ci.replyFailed(WifiScanner.REASON_INVALID_REQUEST, "not supported");
                        }
                        break;
                    case WifiScanner.CMD_STOP_PNO_SCAN:
                        scanParams = (ScanParams) msg.obj;
                        ci = mClients.get(scanParams.listener);
                        if (ci != null) {
                            ci.cleanup();
                        }
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }
        }

        class HwPnoScanState extends State {
            @Override
            public void enter() {
                if (DBG) localLog("HwPnoScanState");
            }

            @Override
            public void exit() {
                // Reset PNO scan in ScannerImpl before we exit.
                mScannerImplsTracker.resetHwPnoList();
                removeInternalClient();
            }

            @Override
            public boolean processMessage(Message msg) {
                switch (msg.what) {
                    case WifiScanner.CMD_START_PNO_SCAN:
                        ScanParams scanParams = (ScanParams) msg.obj;
                        if (scanParams == null) {
                            loge("params null");
                            return HANDLED;
                        }
                        ClientInfo ci = mClients.get(scanParams.listener);
                        if (ci == null) {
                            ci = new ExternalClientInfo(msg.sendingUid, scanParams.packageName,
                                    scanParams.listener);
                            ci.register();
                        }
                        if (scanParams.pnoSettings == null || scanParams.settings == null) {
                            Log.e(TAG, "Failed to get parcelable params");
                            ci.replyFailed(WifiScanner.REASON_INVALID_REQUEST, "bad parcel params");
                            return HANDLED;
                        }

                        if (addHwPnoScanRequest(ci, scanParams.settings,
                                scanParams.pnoSettings)) {
                            mWifiMetrics.getScanMetrics().logPnoScanEvent(
                                    WifiMetrics.ScanMetrics.PNO_SCAN_STATE_STARTED);
                            ci.replySucceeded();
                        } else {
                            mWifiMetrics.getScanMetrics().logPnoScanEvent(
                                    WifiMetrics.ScanMetrics.PNO_SCAN_STATE_FAILED_TO_START);
                            ci.replyFailed(WifiScanner.REASON_INVALID_REQUEST, "bad request");
                            ci.cleanup();
                            transitionTo(mStartedState);
                        }
                        break;
                    case WifiScanner.CMD_STOP_PNO_SCAN:
                        scanParams = (ScanParams) msg.obj;
                        ci = mClients.get(scanParams.listener);
                        removeHwPnoScanRequest(ci);
                        transitionTo(mStartedState);
                        break;
                    case CMD_PNO_NETWORK_FOUND:
                        ScanResult[] scanResults = ((ScanResult[]) msg.obj);
                        mWifiMetrics.getScanMetrics().logPnoScanEvent(
                                WifiMetrics.ScanMetrics.PNO_SCAN_STATE_COMPLETED_NETWORK_FOUND);

                        if (isSingleScanNeeded(scanResults)) {
                            ScanSettings activeScanSettings = getScanSettings();
                            if (activeScanSettings == null) {
                                sendPnoScanFailedToAllAndClear(
                                        WifiScanner.REASON_UNSPECIFIED,
                                        "couldn't retrieve setting");
                                transitionTo(mStartedState);
                            } else {
                                addSingleScanRequest(activeScanSettings);
                                transitionTo(mSingleScanState);
                            }
                        } else {
                            reportPnoNetworkFound((ScanResult[]) msg.obj);
                        }
                        break;
                    case CMD_PNO_SCAN_FAILED:
                        mWifiMetrics.getScanMetrics().logPnoScanEvent(
                                WifiMetrics.ScanMetrics.PNO_SCAN_STATE_FAILED);
                        sendPnoScanFailedToAllAndClear(
                                WifiScanner.REASON_UNSPECIFIED, "pno scan failed");
                        transitionTo(mStartedState);
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }
        }

        class SwPnoScanState extends State {
            private final AlarmManager mSwPnoAlarmManager;
            private final SwPnoAlarmReceiver mSwPnoAlarmReceiver = new SwPnoAlarmReceiver();
            @VisibleForTesting
            public static final String SW_PNO_ALARM_INTENT_ACTION =
                    "com.android.server.wifi.scanner.WifiPnoScanStateMachine.SwPnoScanState"
                            + ".SW_PNO_ALARM";
            PendingIntent mPendingIntentSwPno;
            private final int mSwPnoTimerMarginMs;
            @VisibleForTesting
            public static final String SW_PNO_UPPER_BOUND_ALARM_INTENT_ACTION =
                    "com.android.server.wifi.scanner.WifiPnoScanStateMachine.SwPnoScanState"
                            + ".SW_PNO_UPPERBOUND_ALARM";
            PendingIntent mPendingIntentSwPnoUpperBound;
            private SwPnoScheduler mSwPnoScheduler = null;
            private ScanParams mScanParams = null;
            private ClientInfo mClientInfo = null;


            private final class SwPnoScheduler {

                private final class SwPnoScheduleInfo {
                    /**
                     * The timer is initially scheduled with an interval equal to mTimerBaseMs.
                     * If mBackoff is true, at each iteration the interval will increase
                     * proportionally to the elapsed iterations.
                     * The schedule is repeated up to mMaxIterations iterations.
                     */
                    private final int mMaxIterations;
                    private final int mTimerBaseMs;
                    private final boolean mBackoff;
                    /**
                     * Whether the alarm should be exact or not.
                     * Inexact alarms are delivered when the system thinks it is most efficient.
                     */
                    private final boolean mExact;


                    SwPnoScheduleInfo(int maxIterations, boolean exact, int timerBaseMs,
                            boolean backoff) {
                        if (maxIterations < 1 || timerBaseMs < 1) {
                            Log.wtf(TAG, "Invalid Sw PNO Schedule Info.");
                            throw new IllegalArgumentException("Invalid Sw PNO Schedule Info");
                        }
                        this.mMaxIterations = maxIterations;
                        this.mExact = exact;
                        this.mTimerBaseMs = timerBaseMs;
                        this.mBackoff = backoff;
                    }
                }
                private List<SwPnoScheduleInfo> mSwPnoScheduleInfos = new ArrayList<>();
                SwPnoScheduleInfo mCurrentSchedule;
                Iterator<SwPnoScheduleInfo> mScheduleIterator;
                int mIteration = 0;

                /**
                 * Append a new schedule info at the end of the schedule queue.
                 * @param maxIterations Number of times the current schedule must be repeated
                 * @param exact Whether the alarms are scheduled exactly or not
                 * @param timerBaseMs Initial alarm interval
                 * @param backoff Whether the interval should increase at each iteration or not
                 */
                void addSchedule(int maxIterations, boolean exact, int timerBaseMs,
                        boolean backoff) {
                    mSwPnoScheduleInfos.add(new SwPnoScheduleInfo(maxIterations, exact, timerBaseMs,
                            backoff));
                }

                boolean start() {
                    if (mSwPnoScheduleInfos.isEmpty()) {
                        Log.wtf(TAG, "No SwPno Schedule Found");
                        return false;
                    }
                    mScheduleIterator = mSwPnoScheduleInfos.iterator();
                    mCurrentSchedule = mScheduleIterator.next();
                    return true;
                }

                boolean next() {
                    if (mCurrentSchedule.mMaxIterations > mIteration) {
                        mIteration++;
                        return true;
                    } else if (mScheduleIterator.hasNext()) {
                        mCurrentSchedule = mScheduleIterator.next();
                        mIteration = 1;
                        return true;
                    }
                    return false;
                }

                int getInterval() {
                    int multiplier = mCurrentSchedule.mBackoff ? mIteration : 1;
                    return mCurrentSchedule.mTimerBaseMs * multiplier;
                }

                boolean isExact() {
                    return mCurrentSchedule.mExact;
                }
            }

            private class SwPnoAlarmReceiver extends BroadcastReceiver {

                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(SW_PNO_UPPER_BOUND_ALARM_INTENT_ACTION)) {
                        mSwPnoAlarmManager.cancel(mPendingIntentSwPno);
                    } else {
                        mSwPnoAlarmManager.cancel(mPendingIntentSwPnoUpperBound);
                    }
                    Message msg = obtainMessage();
                    msg.what = CMD_SW_PNO_SCAN;
                    sendMessage(msg);
                }
            }

            SwPnoScanState() {
                Intent alarmIntent = new Intent(SW_PNO_ALARM_INTENT_ACTION).setPackage(
                        mContext.getPackageName());
                Intent alarmIntentUpperBound = new Intent(
                        SW_PNO_UPPER_BOUND_ALARM_INTENT_ACTION).setPackage(
                                mContext.getPackageName());
                mSwPnoAlarmManager = mContext.getSystemService(AlarmManager.class);
                mPendingIntentSwPno = PendingIntent.getBroadcast(mContext, /* requestCode */ 0,
                        alarmIntent, PendingIntent.FLAG_IMMUTABLE);
                mPendingIntentSwPnoUpperBound = PendingIntent.getBroadcast(mContext,
                        /* requestCode */ 1, alarmIntentUpperBound, PendingIntent.FLAG_IMMUTABLE);
                mSwPnoTimerMarginMs = mContext.getResources().getInteger(
                        R.integer.config_wifiSwPnoSlowTimerMargin);
            }

            @Override
            public void enter() {
                if (DBG) localLog("SwPnoScanState");
                IntentFilter filter = new IntentFilter(SW_PNO_ALARM_INTENT_ACTION);
                filter.addAction(SW_PNO_UPPER_BOUND_ALARM_INTENT_ACTION);
                mContext.registerReceiver(mSwPnoAlarmReceiver, filter, null,
                        getHandler());
            }

            @Override
            public void exit() {
                removeInternalClient();
                mSwPnoAlarmManager.cancel(mPendingIntentSwPno);
                mSwPnoAlarmManager.cancel(mPendingIntentSwPnoUpperBound);
                mContext.unregisterReceiver(mSwPnoAlarmReceiver);
                mScanParams = null;
                mClientInfo = null;
            }

            boolean initializeSwPnoScheduleInfos(int mobilityIntervalMs) {
                final int swPnoDefaultTimerFastMs = mContext.getResources().getInteger(
                        R.integer.config_wifiSwPnoFastTimerMs);
                final int swPnoDefaultTimerSlowMs = mContext.getResources().getInteger(
                        R.integer.config_wifiSwPnoSlowTimerMs);
                final int swPnoMobilityIterations = mContext.getResources().getInteger(
                        R.integer.config_wifiSwPnoMobilityStateTimerIterations);
                final int swPnoFastIterations = mContext.getResources().getInteger(
                        R.integer.config_wifiSwPnoFastTimerIterations);
                final int swPnoSlowIterations = mContext.getResources().getInteger(
                        R.integer.config_wifiSwPnoSlowTimerIterations);

                mSwPnoScheduler = new SwPnoScheduler();
                try {
                    mSwPnoScheduler.addSchedule(swPnoMobilityIterations,
                            /* exact */ true, mobilityIntervalMs, /* backoff */ true);
                    mSwPnoScheduler.addSchedule(swPnoFastIterations,
                            /* exact */ true, swPnoDefaultTimerFastMs, /* backoff */ false);
                    mSwPnoScheduler.addSchedule(swPnoSlowIterations,
                            /* exact */ false, swPnoDefaultTimerSlowMs, /* backoff */ false);
                } catch (IllegalArgumentException e) {
                    return false;
                }
                return mSwPnoScheduler.start();
            }

            private void addSwPnoScanRequest(ClientInfo ci,
                    ScanSettings scanSettings, PnoSettings pnoSettings) {
                scanSettings.reportEvents |= WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT
                        | WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN;
                addPnoScanRequest(ci, scanSettings, pnoSettings);
            }

            private void removeSwPnoScanRequest(ClientInfo ci) {
                if (ci != null) {
                    Pair<PnoSettings, ScanSettings> settings = removePnoScanRequest(ci);
                    ci.cleanup();
                    if (settings != null) {
                        logScanRequest("removeSwPnoScanRequest", ci, null,
                                settings.second, settings.first);
                    }
                }
            }

            private void schedulePnoTimer(boolean exact, int timeoutMs) {
                Log.i(TAG, "Next SwPno scan in: " + timeoutMs);
                if (exact) {
                    mSwPnoAlarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            mClock.getElapsedSinceBootMillis() + timeoutMs,
                            mPendingIntentSwPno);
                } else {
                    mSwPnoAlarmManager.setWindow(AlarmManager.ELAPSED_REALTIME,
                            mClock.getElapsedSinceBootMillis() + timeoutMs,
                            mSwPnoTimerMarginMs,
                            mPendingIntentSwPno);

                    mSwPnoAlarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            mClock.getElapsedSinceBootMillis() + timeoutMs
                                    + mSwPnoTimerMarginMs, mPendingIntentSwPnoUpperBound);
                }
            }

            private void handleSwPnoScan() {
                if (mScanParams != null && mScanParams.settings != null && mClientInfo != null) {
                    // The Internal ClientInfo is unregistered by
                    // WifiSingleScanStateMachine#handleScanResults after each scan. We have
                    // therefore to re-create or at least re-register the client before each scan.
                    // For the first scan this is redundant.
                    removeInternalClient();
                    addInternalClient(mClientInfo);
                    addSingleScanRequest(mScanParams.settings);
                }
            }

            private void handleSwPnoSchedule() {
                if (mSwPnoScheduler.next()) {
                    schedulePnoTimer(mSwPnoScheduler.isExact(),
                            mSwPnoScheduler.getInterval());
                } else {
                    // Nothing more to schedule, stopping SW PNO
                    Message msg = obtainMessage();
                    msg.what = WifiScanner.CMD_STOP_PNO_SCAN;
                    sendMessage(msg);
                }
            }

            @Override
            public boolean processMessage(Message msg) {
                switch (msg.what) {
                    case WifiScanner.CMD_START_PNO_SCAN: {
                        Log.i(TAG, "Starting Software PNO");
                        ScanParams scanParams = (ScanParams) msg.obj;
                        if (scanParams == null) {
                            Log.wtf(TAG, "Received Start PNO request without parameters");
                            transitionTo(mStartedState);
                            return HANDLED;
                        }

                        ClientInfo clientInfo = mClients.get(scanParams.listener);
                        if (clientInfo == null) {
                            clientInfo = new ExternalClientInfo(msg.sendingUid,
                                    scanParams.packageName, scanParams.listener);
                            clientInfo.register();
                        }

                        if (!mActivePnoScans.isEmpty()) {
                            loge("Dropping scan request because there is already an active scan");
                            clientInfo.replyFailed(WifiScanner.REASON_DUPLICATE_REQEUST,
                                    "Failed to add a SW Pno Scan Request");
                            return HANDLED;
                        }

                        if (scanParams.pnoSettings == null || scanParams.settings == null) {
                            Log.e(TAG, "SwPno Invalid Scan Parameters");
                            clientInfo.replyFailed(WifiScanner.REASON_INVALID_REQUEST,
                                    "invalid settings");
                            transitionTo(mStartedState);
                            return HANDLED;
                        }

                        if (!initializeSwPnoScheduleInfos(scanParams.settings.periodInMs)) {
                            clientInfo.replyFailed(WifiScanner.REASON_INVALID_REQUEST,
                                    "Failed to initialize the Sw PNO Scheduler");
                            transitionTo(mStartedState);
                            return HANDLED;
                        }

                        addSwPnoScanRequest(clientInfo, scanParams.settings,
                                scanParams.pnoSettings);
                        clientInfo.replySucceeded();
                        mClientInfo = clientInfo;
                        mScanParams = scanParams;

                        handleSwPnoScan();
                        handleSwPnoSchedule();
                        break;
                    }
                    case CMD_SW_PNO_SCAN:
                        // The internal client is registered to mClients when the PNO scan is
                        // started, and is deregistered when the scan is over. By verifying that
                        // the internal client is not registered in mClients can be sure that no
                        // other pno scans are in progress
                        if (mClients.get(mInternalClientInfo.mListener) == null) {
                            handleSwPnoScan();
                            handleSwPnoSchedule();
                        }
                        break;
                    case WifiScanner.CMD_STOP_PNO_SCAN: {
                        Log.i(TAG, "Stopping Software PNO");
                        if (mClientInfo != null) {
                            removeSwPnoScanRequest(mClientInfo);
                            transitionTo(mStartedState);
                        }
                        break;
                    }
                    case WifiScanner.CMD_OP_FAILED:
                        sendPnoScanFailedToAllAndClear(
                                WifiScanner.REASON_UNSPECIFIED, "scan failed");
                        transitionTo(mStartedState);
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }
        }

        class SingleScanState extends State {
            @Override
            public void enter() {
                if (DBG) localLog("SingleScanState");
            }

            @Override
            public boolean processMessage(Message msg) {
                switch (msg.what) {
                    case WifiScanner.CMD_SCAN_RESULT:
                        WifiScanner.ParcelableScanData parcelableScanData =
                                (WifiScanner.ParcelableScanData) msg.obj;
                        ScanData[] scanDatas = parcelableScanData.getResults();
                        ScanData lastScanData = scanDatas[scanDatas.length - 1];
                        reportPnoNetworkFound(lastScanData.getResults());
                        transitionTo(mHwPnoScanState);
                        break;
                    case WifiScanner.CMD_OP_FAILED:
                        sendPnoScanFailedToAllAndClear(
                                WifiScanner.REASON_UNSPECIFIED, "single scan failed");
                        transitionTo(mStartedState);
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }
        }

        private WifiNative.PnoSettings convertToWifiNativePnoSettings(ScanSettings scanSettings,
                                                                  PnoSettings pnoSettings) {
            WifiNative.PnoSettings nativePnoSetting = new WifiNative.PnoSettings();
            nativePnoSetting.periodInMs = scanSettings.periodInMs;
            nativePnoSetting.min5GHzRssi = pnoSettings.min5GHzRssi;
            nativePnoSetting.min24GHzRssi = pnoSettings.min24GHzRssi;
            nativePnoSetting.min6GHzRssi = pnoSettings.min6GHzRssi;
            nativePnoSetting.scanIterations = pnoSettings.scanIterations;
            nativePnoSetting.scanIntervalMultiplier = pnoSettings.scanIntervalMultiplier;
            nativePnoSetting.isConnected = pnoSettings.isConnected;
            nativePnoSetting.networkList =
                    new WifiNative.PnoNetwork[pnoSettings.networkList.length];
            for (int i = 0; i < pnoSettings.networkList.length; i++) {
                nativePnoSetting.networkList[i] = new WifiNative.PnoNetwork();
                nativePnoSetting.networkList[i].ssid = pnoSettings.networkList[i].ssid;
                nativePnoSetting.networkList[i].flags = pnoSettings.networkList[i].flags;
                nativePnoSetting.networkList[i].auth_bit_field =
                        pnoSettings.networkList[i].authBitField;
                nativePnoSetting.networkList[i].frequencies =
                        pnoSettings.networkList[i].frequencies;
            }
            return nativePnoSetting;
        }

        // Retrieve the only active scan settings.
        private ScanSettings getScanSettings() {
            for (Pair<PnoSettings, ScanSettings> settingsPair : mActivePnoScans.getAllSettings()) {
                return settingsPair.second;
            }
            return null;
        }

        private void removeInternalClient() {
            if (mInternalClientInfo != null) {
                mInternalClientInfo.cleanup();
                mInternalClientInfo = null;
            } else {
                Log.w(TAG, "No Internal client for PNO");
            }
        }

        private void addInternalClient(ClientInfo ci) {
            if (mInternalClientInfo == null) {
                mInternalClientInfo = new InternalClientInfo(ci.getUid(), "internal",
                        new InternalListener());
                mInternalClientInfo.register();
            } else {
                Log.w(TAG, "Internal client for PNO already exists");
            }
        }

        private void addPnoScanRequest(ClientInfo ci, ScanSettings scanSettings,
                PnoSettings pnoSettings) {
            mActivePnoScans.addRequest(ci, ClientModeImpl.WIFI_WORK_SOURCE,
                    Pair.create(pnoSettings, scanSettings));
            addInternalClient(ci);
        }

        private Pair<PnoSettings, ScanSettings> removePnoScanRequest(ClientInfo ci) {
            Pair<PnoSettings, ScanSettings> settings = mActivePnoScans.removeRequest(ci);
            return settings;
        }

        private boolean addHwPnoScanRequest(ClientInfo ci, ScanSettings scanSettings,
                PnoSettings pnoSettings) {
            if (ci == null) {
                Log.d(TAG, "Failing scan request ClientInfo not found ");
                return false;
            }
            if (!mActivePnoScans.isEmpty()) {
                loge("Failing scan request because there is already an active scan");
                return false;
            }
            WifiNative.PnoSettings nativePnoSettings =
                    convertToWifiNativePnoSettings(scanSettings, pnoSettings);
            if (!mScannerImplsTracker.setHwPnoList(nativePnoSettings)) {
                return false;
            }
            logScanRequest("addHwPnoScanRequest", ci, null, scanSettings, pnoSettings);
            addPnoScanRequest(ci, scanSettings, pnoSettings);

            return true;
        }

        private void removeHwPnoScanRequest(ClientInfo ci) {
            if (ci != null) {
                Pair<PnoSettings, ScanSettings> settings = removePnoScanRequest(ci);
                ci.cleanup();
                if (settings != null) {
                    logScanRequest("removeHwPnoScanRequest", ci, null,
                            settings.second, settings.first);
                }
            }
        }

        private void reportPnoNetworkFound(ScanResult[] results) {
            for (RequestInfo<Pair<PnoSettings, ScanSettings>> entry : mActivePnoScans) {
                ClientInfo ci = entry.clientInfo;
                logCallback("pnoNetworkFound", ci, describeForLog(results));
                ci.reportEvent((listener) -> {
                    try {
                        listener.onPnoNetworkFound(results);
                    } catch (RemoteException e) {
                        loge("Failed to call onFullResult: " + ci);
                    }
                });
            }
        }

        private void sendPnoScanFailedToAllAndClear(int reason, String description) {
            for (RequestInfo<Pair<PnoSettings, ScanSettings>> entry : mActivePnoScans) {
                ClientInfo ci = entry.clientInfo;
                ci.reportEvent((listener) -> {
                    try {
                        listener.onFailure(reason, description);
                    } catch (RemoteException e) {
                        loge("Failed to call onFullResult: " + ci);
                    }
                });
            }
            mActivePnoScans.clear();
        }

        private void addSingleScanRequest(ScanSettings settings) {
            if (DBG) localLog("Starting single scan");
            if (mInternalClientInfo != null) {
                Message msg = Message.obtain();
                msg.what = WifiScanner.CMD_START_SINGLE_SCAN;
                msg.obj = new ScanParams(mInternalClientInfo.mListener, settings,
                        ClientModeImpl.WIFI_WORK_SOURCE);
                mSingleScanStateMachine.sendMessage(msg);
            }
            mWifiMetrics.getScanMetrics().setWorkSource(ClientModeImpl.WIFI_WORK_SOURCE);
        }

        /**
         * Checks if IE are present in scan data, if no single scan is needed to report event to
         * client
         */
        private boolean isSingleScanNeeded(ScanResult[] scanResults) {
            for (ScanResult scanResult : scanResults) {
                if (scanResult.informationElements != null
                        && scanResult.informationElements.length > 0) {
                    return false;
                }
            }
            return true;
        }
    }

    @FunctionalInterface
    private interface ListenerCallback {
        void callListener(IWifiScannerListener listener);
    }

    private abstract class ClientInfo {
        private final int mUid;
        private final String mPackageName;
        private final WorkSource mWorkSource;
        private boolean mScanWorkReported = false;
        protected final IWifiScannerListener mListener;
        protected DeathRecipient mDeathRecipient = new DeathRecipient() {
            @Override
            public void binderDied() {
                mWifiThreadRunner.post(() -> {
                    if (DBG) localLog("binder died: client listener: " + mListener);
                    if (isVerboseLoggingEnabled()) {
                        Log.i(TAG, "binder died: client listener: " + mListener);
                    }
                    cleanup();
                });
            }
        };

        ClientInfo(int uid, String packageName, IWifiScannerListener listener) {
            mUid = uid;
            mPackageName = packageName;
            mListener = listener;
            mWorkSource = new WorkSource(uid);
        }

        /**
         * Register this client to main client map.
         */
        public void register() {
            if (isVerboseLoggingEnabled()) {
                Log.i(TAG, "Registering listener= " + mListener + " uid=" + mUid
                        + " packageName=" + mPackageName + " workSource=" + mWorkSource);
            }
            mClients.put(mListener, this);
        }

        /**
         * Unregister this client from main client map.
         */
        private void unregister() {
            if (isVerboseLoggingEnabled()) {
                Log.i(TAG, "Unregistering listener= " + mListener + " uid=" + mUid
                        + " packageName=" + mPackageName + " workSource=" + mWorkSource);
            }
            try {
                mListener.asBinder().unlinkToDeath(mDeathRecipient, 0);
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister death recipient! " + mListener);
            }

            mClients.remove(mListener);
        }

        public void cleanup() {
            mSingleScanListeners.removeAllForClient(this);
            mSingleScanStateMachine.removeSingleScanRequests(this);
            mBackgroundScanStateMachine.removeBackgroundScanSettings(this);
            unregister();
            localLog("Successfully stopped all requests for client " + this);
        }

        public int getUid() {
            return mUid;
        }

        // This has to be implemented by subclasses to report events back to clients.
        public abstract void reportEvent(ListenerCallback cb);

        // TODO(b/27903217, 71530998): This is dead code. Should this be wired up ?
        private void reportBatchedScanStart() {
            if (mUid == 0)
                return;

            int csph = getCsph();

            mBatteryStats.reportWifiBatchedScanStartedFromSource(mWorkSource, csph);
        }

        // TODO(b/27903217, 71530998): This is dead code. Should this be wired up ?
        private void reportBatchedScanStop() {
            if (mUid == 0)
                return;

            mBatteryStats.reportWifiBatchedScanStoppedFromSource(mWorkSource);
        }

        // TODO migrate batterystats to accept scan duration per hour instead of csph
        private int getCsph() {
            int totalScanDurationPerHour = 0;
            Collection<ScanSettings> settingsList =
                    mBackgroundScanStateMachine.getBackgroundScanSettings(this);
            for (ScanSettings settings : settingsList) {
                int scanDurationMs = mChannelHelper.estimateScanDuration(settings);
                int scans_per_Hour = settings.periodInMs == 0 ? 1 : (3600 * 1000) /
                        settings.periodInMs;
                totalScanDurationPerHour += scanDurationMs * scans_per_Hour;
            }

            return totalScanDurationPerHour / ChannelHelper.SCAN_PERIOD_PER_CHANNEL_MS;
        }

        // TODO(b/27903217, 71530998): This is dead code. Should this be wired up ?
        private void reportScanWorkUpdate() {
            if (mScanWorkReported) {
                reportBatchedScanStop();
                mScanWorkReported = false;
            }
            if (mBackgroundScanStateMachine.getBackgroundScanSettings(this).isEmpty()) {
                reportBatchedScanStart();
                mScanWorkReported = true;
            }
        }

        void replySucceeded() {
            if (mListener != null) {
                try {
                    mListener.onSuccess();
                    mLog.trace("onSuccess").flush();
                } catch (Exception e) {
                    // There's not much we can do if reply can't be sent!
                }
            } else {
                // locally generated message; doesn't need a reply!
            }
        }

        void replyFailed(int reason, String description) {
            if (mListener != null) {
                try {
                    mListener.onFailure(reason, description);
                    mLog.trace("onFailure reason=% description=%")
                            .c(reason)
                            .c(description)
                            .flush();
                } catch (Exception e) {
                    // There's not much we can do if reply can't be sent!
                }
            } else {
                // locally generated message; doesn't need a reply!
            }
        }

        @Override
        public String toString() {
            return "ClientInfo[uid=" + mUid + ", package=" + mPackageName + ", " + mListener
                    + "]";
        }
    }

    /**
     * This class is used to represent external clients to the WifiScanning Service.
     */
    private class ExternalClientInfo extends ClientInfo {
        /**
         * Indicates if the client is still connected
         * If the client is no longer connected then messages to it will be silently dropped
         */
        private boolean mDisconnected = false;

        ExternalClientInfo(int uid, String packageName, IWifiScannerListener listener) {
            super(uid, packageName, listener);
            if (DBG) localLog("New client, listener: " + listener);
            try {
                listener.asBinder().linkToDeath(mDeathRecipient, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "can't register death recipient! " + listener);
            }
        }

        public void reportEvent(ListenerCallback cb) {
            if (!mDisconnected) {
                cb.callListener(mListener);
            }
        }

        @Override
        public void cleanup() {
            mDisconnected = true;
            mPnoScanStateMachine.removePnoSettings(this);
            super.cleanup();
        }
    }

    /**
     * This class is used to represent internal clients to the WifiScanning Service. This is needed
     * for communicating between State Machines.
     * This leaves the onReportEvent method unimplemented, so that the clients have the freedom
     * to handle the events as they need.
     */
    private class InternalClientInfo extends ClientInfo {
        /**
         * The UID here is used to proxy the original external requester UID.
         */
        InternalClientInfo(int requesterUid, String packageName, IWifiScannerListener listener) {
            super(requesterUid, packageName, listener);
        }

        @Override
        public void reportEvent(ListenerCallback cb) {
            cb.callListener(mListener);
        }

        @Override
        public String toString() {
            return "InternalClientInfo[]";
        }
    }

    private static class InternalListener extends IWifiScannerListener.Stub {
        InternalListener() {
        }

        @Override
        public void onSuccess() {
        }

        @Override
        public void onFailure(int reason, String description) {
        }

        @Override
        public void onResults(WifiScanner.ScanData[] results) {
        }

        @Override
        public void onFullResult(ScanResult fullScanResult) {
        }

        @Override
        public void onSingleScanCompleted() {
        }

        @Override
        public void onPnoNetworkFound(ScanResult[] results) {
        }
    }

    private class LocalService extends WifiScannerInternal {
        @Override
        public void setScanningEnabled(boolean enable) {
            WifiScanningServiceImpl.this.setScanningEnabled(enable, Process.myTid(),
                    mContext.getOpPackageName());
        }

        @Override
        public void registerScanListener(@NonNull WifiScannerInternal.ScanListener listener) {
            WifiScanningServiceImpl.this.registerScanListener(listener,
                    mContext.getOpPackageName(), mContext.getAttributionTag());
        }

        @Override
        public void startScan(WifiScanner.ScanSettings settings,
                WifiScannerInternal.ScanListener listener,
                @Nullable WorkSource workSource) {
            WifiScanningServiceImpl.this.startScan(listener, settings, workSource,
                    workSource.getPackageName(0), mContext.getAttributionTag());
        }

        @Override
        public void stopScan(WifiScannerInternal.ScanListener listener) {
            WifiScanningServiceImpl.this.stopScan(listener,
                    mContext.getOpPackageName(), mContext.getAttributionTag());
        }

        @Override
        public void startPnoScan(WifiScanner.ScanSettings scanSettings,
                WifiScanner.PnoSettings pnoSettings,
                WifiScannerInternal.ScanListener listener) {
            WifiScanningServiceImpl.this.startPnoScan(listener,
                    scanSettings, pnoSettings, mContext.getOpPackageName(),
                    mContext.getAttributionTag());
        }

        @Override
        public void stopPnoScan(WifiScannerInternal.ScanListener listener) {
            WifiScanningServiceImpl.this.stopPnoScan(listener, mContext.getOpPackageName(),
                    mContext.getAttributionTag());
        }

        @Override
        public List<ScanResult> getSingleScanResults() {
            return mSingleScanStateMachine.filterCachedScanResultsByAge();
        }
    }

    private static String toString(int uid, ScanSettings settings) {
        StringBuilder sb = new StringBuilder();
        sb.append("ScanSettings[uid=").append(uid);
        sb.append(", period=").append(settings.periodInMs);
        sb.append(", report=").append(settings.reportEvents);
        if (settings.reportEvents == WifiScanner.REPORT_EVENT_AFTER_BUFFER_FULL
                && settings.numBssidsPerScan > 0
                && settings.maxScansToCache > 1) {
            sb.append(", batch=").append(settings.maxScansToCache);
            sb.append(", numAP=").append(settings.numBssidsPerScan);
        }
        sb.append(", ").append(ChannelHelper.toString(settings));
        sb.append("]");

        return sb.toString();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump WifiScanner from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid()
                    + " without permission "
                    + android.Manifest.permission.DUMP);
            return;
        }
        pw.println("WifiScanningService - Log Begin ----");
        mLocalLog.dump(fd, pw, args);
        pw.println("WifiScanningService - Log End ----");
        pw.println();
        pw.println("clients:");
        for (ClientInfo client : mClients.values()) {
            pw.println("  " + client);
        }
        pw.println("listeners:");
        for (ClientInfo client : mClients.values()) {
            Collection<ScanSettings> settingsList =
                    mBackgroundScanStateMachine.getBackgroundScanSettings(client);
            for (ScanSettings settings : settingsList) {
                pw.println("  " + toString(client.mUid, settings));
            }
        }
        if (mBackgroundScheduler != null) {
            WifiNative.ScanSettings schedule = mBackgroundScheduler.getSchedule();
            if (schedule != null) {
                pw.println("schedule:");
                pw.println("  base period: " + schedule.base_period_ms);
                pw.println("  max ap per scan: " + schedule.max_ap_per_scan);
                pw.println("  batched scans: " + schedule.report_threshold_num_scans);
                pw.println("  buckets:");
                for (int b = 0; b < schedule.num_buckets; b++) {
                    WifiNative.BucketSettings bucket = schedule.buckets[b];
                    pw.println("    bucket " + bucket.bucket + " (" + bucket.period_ms + "ms)["
                            + bucket.report_events + "]: "
                            + ChannelHelper.toString(bucket));
                }
            }
        }
        if (mPnoScanStateMachine != null) {
            mPnoScanStateMachine.dump(fd, pw, args);
        }
        pw.println();

        if (mChannelHelper != null) {
            mChannelHelper.dump(fd, pw, args);
            pw.println();
        }

        if (mSingleScanStateMachine != null) {
            mSingleScanStateMachine.dump(fd, pw, args);
            pw.println();
            List<ScanResult> scanResults = mSingleScanStateMachine.getCachedScanResultsAsList();
            long nowMs = mClock.getElapsedSinceBootMillis();
            Log.d(TAG, "Latest scan results nowMs = " + nowMs);
            pw.println("Latest scan results:");
            ScanResultUtil.dumpScanResults(pw, scanResults, nowMs);
            pw.println();
        }
        for (WifiScannerImpl impl : mScannerImpls.values()) {
            impl.dump(fd, pw, args);
        }
    }

    void logScanRequest(String request, ClientInfo ci, WorkSource workSource,
            ScanSettings settings, PnoSettings pnoSettings) {
        StringBuilder sb = new StringBuilder();
        sb.append(request)
                .append(": ")
                .append((ci == null) ? "ClientInfo[unknown]" : ci.toString());
        if (workSource != null) {
            sb.append(",").append(workSource);
        }
        if (settings != null) {
            sb.append(", ");
            describeTo(sb, settings);
        }
        if (pnoSettings != null) {
            sb.append(", ");
            describeTo(sb, pnoSettings);
        }
        localLog(sb.toString());
    }

    void logCallback(String callback, ClientInfo ci, String extra) {
        StringBuilder sb = new StringBuilder();
        sb.append(callback)
                .append(": ")
                .append((ci == null) ? "ClientInfo[unknown]" : ci.toString());
        if (extra != null) {
            sb.append(",").append(extra);
        }
        localLog(sb.toString());
    }

    static String describeForLog(ScanData[] results) {
        StringBuilder sb = new StringBuilder();
        sb.append("results=");
        for (int i = 0; i < results.length; ++i) {
            if (i > 0) sb.append(";");
            sb.append(results[i].getResults().length);
        }
        return sb.toString();
    }

    static String describeForLog(ScanResult[] results) {
        return "results=" + results.length;
    }

    static String getScanTypeString(int type) {
        switch(type) {
            case WifiScanner.SCAN_TYPE_LOW_LATENCY:
                return "LOW LATENCY";
            case WifiScanner.SCAN_TYPE_LOW_POWER:
                return "LOW POWER";
            case WifiScanner.SCAN_TYPE_HIGH_ACCURACY:
                return "HIGH ACCURACY";
            default:
                // This should never happen because we've validated the incoming type in
                // |validateScanType|.
                throw new IllegalArgumentException("Invalid scan type " + type);
        }
    }

    /**
     * Convert Wi-Fi standard error to string
     */
    private static String scanErrorCodeToDescriptionString(int errorCode) {
        switch(errorCode) {
            case WifiScanner.REASON_BUSY:
                return "Scan failed - Device or resource busy";
            case WifiScanner.REASON_ABORT:
                return "Scan aborted";
            case WifiScanner.REASON_NO_DEVICE:
                return "Scan failed - No such device";
            case WifiScanner.REASON_INVALID_ARGS:
                return "Scan failed - invalid argument";
            case WifiScanner.REASON_TIMEOUT:
                return "Scan failed - Timeout";
            case WifiScanner.REASON_UNSPECIFIED:
            default:
                return "Scan failed - unspecified reason";
        }
    }

    static String describeTo(StringBuilder sb, ScanSettings scanSettings) {
        sb.append("ScanSettings { ")
                .append(" type:").append(getScanTypeString(scanSettings.type))
                .append(" band:").append(ChannelHelper.bandToString(scanSettings.band))
                .append(" ignoreLocationSettings:").append(scanSettings.ignoreLocationSettings)
                .append(" period:").append(scanSettings.periodInMs)
                .append(" reportEvents:").append(scanSettings.reportEvents)
                .append(" numBssidsPerScan:").append(scanSettings.numBssidsPerScan)
                .append(" maxScansToCache:").append(scanSettings.maxScansToCache)
                .append(" rnrSetting:").append(
                        SdkLevel.isAtLeastS() ? scanSettings.getRnrSetting() : "Not supported")
                .append(" 6GhzPscOnlyEnabled:").append(
                        SdkLevel.isAtLeastS() ? scanSettings.is6GhzPscOnlyEnabled()
                                : "Not supported")
                .append(" channels:[ ");
        if (scanSettings.channels != null) {
            for (int i = 0; i < scanSettings.channels.length; i++) {
                sb.append(scanSettings.channels[i].frequency).append(" ");
            }
        }
        sb.append(" ] ").append(" } ");
        return sb.toString();
    }

    static String describeTo(StringBuilder sb, PnoSettings pnoSettings) {
        sb.append("PnoSettings { ")
          .append(" min5GhzRssi:").append(pnoSettings.min5GHzRssi)
          .append(" min24GhzRssi:").append(pnoSettings.min24GHzRssi)
          .append(" min6GhzRssi:").append(pnoSettings.min6GHzRssi)
          .append(" scanIterations:").append(pnoSettings.scanIterations)
          .append(" scanIntervalMultiplier:").append(pnoSettings.scanIntervalMultiplier)
          .append(" isConnected:").append(pnoSettings.isConnected)
          .append(" networks:[ ");
        if (pnoSettings.networkList != null) {
            for (int i = 0; i < pnoSettings.networkList.length; i++) {
                sb.append(pnoSettings.networkList[i].ssid).append(",");
            }
        }
        sb.append(" ] ")
          .append(" } ");
        return sb.toString();
    }
}
