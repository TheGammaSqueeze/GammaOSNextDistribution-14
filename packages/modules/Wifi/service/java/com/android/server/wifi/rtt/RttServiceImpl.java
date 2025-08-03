/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.wifi.rtt;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
import static android.net.wifi.rtt.WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_LCI;
import static android.net.wifi.rtt.WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_LCR;
import static android.net.wifi.rtt.WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_NTB_INITIATOR;
import static android.net.wifi.rtt.WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_ONE_SIDED_RTT;
import static android.net.wifi.rtt.WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_STA_RESPONDER;

import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_VERBOSE_LOGGING_ENABLED;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.MacAddress;
import android.net.wifi.WifiManager;
import android.net.wifi.aware.IWifiAwareMacAddressProvider;
import android.net.wifi.aware.MacAddrMapping;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.rtt.IRttCallback;
import android.net.wifi.rtt.IWifiRttManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.ResponderConfig;
import android.net.wifi.rtt.ResponderLocation;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.WorkSource;
import android.os.WorkSource.WorkChain;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.WakeupMessage;
import com.android.modules.utils.BasicShellCommandHandler;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.BuildProperties;
import com.android.server.wifi.Clock;
import com.android.server.wifi.FrameworkFacade;
import com.android.server.wifi.HalDeviceManager;
import com.android.server.wifi.SystemBuildProperties;
import com.android.server.wifi.WifiSettingsConfigStore;
import com.android.server.wifi.hal.WifiRttController;
import com.android.server.wifi.proto.nano.WifiMetricsProto;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.resources.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * Implementation of the IWifiRttManager AIDL interface and of the RttService state manager.
 */
public class RttServiceImpl extends IWifiRttManager.Stub {
    private static final String TAG = "RttServiceImpl";
    private static final boolean VDBG = false; // STOPSHIP if true
    private boolean mVerboseLoggingEnabled = false;
    private boolean mVerboseHalLoggingEnabled = false;

    private final Context mContext;
    private final RttShellCommand mShellCommand;
    private Clock mClock;
    private WifiAwareManager mAwareManager;
    private WifiRttController mWifiRttController;
    private HalDeviceManager mHalDeviceManager;
    private RttMetrics mRttMetrics;
    private WifiPermissionsUtil mWifiPermissionsUtil;
    private ActivityManager mActivityManager;
    private PowerManager mPowerManager;
    private long mLastRequestTimestamp;
    private final BuildProperties mBuildProperties;
    private FrameworkFacade mFrameworkFacade;
    private WifiRttController.Capabilities mCapabilities;

    private RttServiceSynchronized mRttServiceSynchronized;

    /* package */ static final String HAL_RANGING_TIMEOUT_TAG = TAG + " HAL Ranging Timeout";

    @VisibleForTesting
    public static final long HAL_RANGING_TIMEOUT_MS = 5_000; // 5 sec
    @VisibleForTesting
    public static final long HAL_AWARE_RANGING_TIMEOUT_MS = 10_000; // 10 sec

    // arbitrary, larger than anything reasonable
    /* package */ static final int MAX_QUEUED_PER_UID = 20;

    private final WifiRttController.RttControllerRangingResultsCallback mRangingResultsCallback =
            new WifiRttController.RttControllerRangingResultsCallback() {
                @Override
                public void onRangingResults(int cmdId, List<RangingResult> rangingResults) {
                    if (mVerboseLoggingEnabled) Log.d(TAG, "onRangingResults: cmdId=" + cmdId);
                    mRttServiceSynchronized.mHandler.post(() -> {
                        mRttServiceSynchronized.onRangingResults(cmdId, rangingResults);
                    });
                }
            };

    private final HalDeviceManager.InterfaceRttControllerLifecycleCallback mRttLifecycleCb =
            new HalDeviceManager.InterfaceRttControllerLifecycleCallback() {
                @Override
                public void onNewRttController(WifiRttController controller) {
                    if (mVerboseLoggingEnabled) {
                        Log.d(TAG, "onNewRttController: controller=" + controller);
                    }
                    boolean changed = mWifiRttController == null;
                    mWifiRttController = controller;
                    mWifiRttController.registerRangingResultsCallback(mRangingResultsCallback);
                    if (changed) {
                        enableIfPossible();
                    }
                }

                @Override
                public void onRttControllerDestroyed() {
                    if (mVerboseLoggingEnabled) Log.d(TAG, "onRttControllerDestroyed");
                    mWifiRttController = null;
                    disable();
                }
            };

    public RttServiceImpl(Context context) {
        mContext = context;
        mBuildProperties = new SystemBuildProperties();
        mFrameworkFacade = new FrameworkFacade();
        mShellCommand = new RttShellCommand();
        mShellCommand.reset();
    }

    private void updateVerboseLoggingEnabled() {
        final int verboseAlwaysOnLevel = mContext.getResources().getInteger(
                R.integer.config_wifiVerboseLoggingAlwaysOnLevel);
        mVerboseLoggingEnabled = mFrameworkFacade.isVerboseLoggingAlwaysOn(verboseAlwaysOnLevel,
                mBuildProperties) || mVerboseHalLoggingEnabled;
    }

    /*
     * Shell command: adb shell cmd wifirtt ...
     */

    // If set to 0: normal behavior, if set to 1: do not allow any caller (including system
    // callers) privileged API access
    private static final String CONTROL_PARAM_OVERRIDE_ASSUME_NO_PRIVILEGE_NAME =
            "override_assume_no_privilege";
    private static final int CONTROL_PARAM_OVERRIDE_ASSUME_NO_PRIVILEGE_DEFAULT = 0;

    private class RttShellCommand extends BasicShellCommandHandler {
        private Map<String, Integer> mControlParams = new HashMap<>();

        @Override
        public int onCommand(String cmd) {
            final int uid = Binder.getCallingUid();
            if (uid != 0) {
                throw new SecurityException(
                        "Uid " + uid + " does not have access to wifirtt commands");
            }

            final PrintWriter pw = getErrPrintWriter();
            try {
                if ("reset".equals(cmd)) {
                    reset();
                    return 0;
                } else if ("get".equals(cmd)) {
                    String name = getNextArgRequired();
                    if (!mControlParams.containsKey(name)) {
                        pw.println("Unknown parameter name -- '" + name + "'");
                        return -1;
                    }
                    getOutPrintWriter().println(mControlParams.get(name));
                    return 0;
                } else if ("set".equals(cmd)) {
                    String name = getNextArgRequired();
                    String valueStr = getNextArgRequired();

                    if (!mControlParams.containsKey(name)) {
                        pw.println("Unknown parameter name -- '" + name + "'");
                        return -1;
                    }

                    try {
                        mControlParams.put(name, Integer.valueOf(valueStr));
                        return 0;
                    } catch (NumberFormatException e) {
                        pw.println("Can't convert value to integer -- '" + valueStr + "'");
                        return -1;
                    }
                } else if ("get_capabilities".equals(cmd)) {
                    if (mCapabilities == null && mWifiRttController != null) {
                        mCapabilities = mWifiRttController.getRttCapabilities();
                    }
                    JSONObject j = new JSONObject();
                    if (mCapabilities != null) {
                        try {
                            j.put("rttOneSidedSupported", mCapabilities.oneSidedRttSupported);
                            j.put("rttFtmSupported", mCapabilities.rttFtmSupported);
                            j.put("lciSupported", mCapabilities.lciSupported);
                            j.put("lcrSupported", mCapabilities.lcrSupported);
                            j.put("responderSupported", mCapabilities.responderSupported);
                            j.put("mcVersion", mCapabilities.mcVersion);
                            j.put("ntbInitiatorSupported", mCapabilities.ntbInitiatorSupported);
                            j.put("ntbResponderSupported", mCapabilities.ntbResponderSupported);
                        } catch (JSONException e) {
                            Log.e(TAG, "onCommand: get_capabilities e=" + e);
                        }
                    }
                    getOutPrintWriter().println(j.toString());
                    return 0;
                } else {
                    handleDefaultCommands(cmd);
                }
            } catch (Exception e) {
                pw.println("Exception: " + e);
            }
            return -1;
        }

        @Override
        public void onHelp() {
            final PrintWriter pw = getOutPrintWriter();

            pw.println("Wi-Fi RTT (wifirt) commands:");
            pw.println("  help");
            pw.println("    Print this help text.");
            pw.println("  reset");
            pw.println("    Reset parameters to default values.");
            pw.println("  get_capabilities: prints out the RTT capabilities as a JSON string");
            pw.println("  get <name>");
            pw.println("    Get the value of the control parameter.");
            pw.println("  set <name> <value>");
            pw.println("    Set the value of the control parameter.");
            pw.println("  Control parameters:");
            for (String name : mControlParams.keySet()) {
                pw.println("    " + name);
            }
            pw.println();
        }

        public int getControlParam(String name) {
            if (mControlParams.containsKey(name)) {
                return mControlParams.get(name);
            }

            Log.wtf(TAG, "getControlParam for unknown variable: " + name);
            return 0;
        }

        public void reset() {
            mControlParams.put(CONTROL_PARAM_OVERRIDE_ASSUME_NO_PRIVILEGE_NAME,
                    CONTROL_PARAM_OVERRIDE_ASSUME_NO_PRIVILEGE_DEFAULT);
        }
    }

    /*
     * INITIALIZATION
     */

    /**
     * Initializes the RTT service (usually with objects from an injector).
     *
     * @param looper The looper on which to synchronize operations.
     * @param clock A mockable clock.
     * @param awareManager The Wi-Fi Aware service (binder) if supported on the system.
     * @param rttMetrics The Wi-Fi RTT metrics object.
     * @param wifiPermissionsUtil Utility for permission checks.
     * @param settingsConfigStore Used for retrieving verbose logging level.
     * @param halDeviceManager The HAL device manager object.
     */
    public void start(Looper looper, Clock clock, WifiAwareManager awareManager,
            RttMetrics rttMetrics, WifiPermissionsUtil wifiPermissionsUtil,
            WifiSettingsConfigStore settingsConfigStore, HalDeviceManager halDeviceManager) {
        mClock = clock;
        mAwareManager = awareManager;
        mHalDeviceManager = halDeviceManager;
        mRttMetrics = rttMetrics;
        mWifiPermissionsUtil = wifiPermissionsUtil;
        mRttServiceSynchronized = new RttServiceSynchronized(looper);
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mPowerManager = mContext.getSystemService(PowerManager.class);

        mRttServiceSynchronized.mHandler.post(() -> {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
            mContext.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (mVerboseLoggingEnabled) {
                        Log.v(TAG, "BroadcastReceiver: action=" + action);
                    }
                    if (PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED.equals(action)) {
                        if (mPowerManager.isDeviceIdleMode()) {
                            disable();
                        } else {
                            enableIfPossible();
                        }
                    }
                }
            }, intentFilter);

            settingsConfigStore.registerChangeListener(
                    WIFI_VERBOSE_LOGGING_ENABLED,
                    (key, newValue) -> enableVerboseLogging(newValue),
                    mRttServiceSynchronized.mHandler);
            enableVerboseLogging(settingsConfigStore.get(WIFI_VERBOSE_LOGGING_ENABLED));

            intentFilter = new IntentFilter();
            intentFilter.addAction(LocationManager.MODE_CHANGED_ACTION);
            mContext.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (mVerboseLoggingEnabled) {
                        Log.v(TAG, "onReceive: MODE_CHANGED_ACTION: intent=" + intent);
                    }
                    if (mWifiPermissionsUtil.isLocationModeEnabled()) {
                        enableIfPossible();
                    } else {
                        disable();
                    }
                }
            }, intentFilter);

            mHalDeviceManager.initialize();
            mHalDeviceManager.registerStatusListener(() -> {
                if (VDBG) Log.d(TAG, "hdm.onStatusChanged");
                if (mHalDeviceManager.isStarted()) {
                    mHalDeviceManager.registerRttControllerLifecycleCallback(mRttLifecycleCb,
                            mRttServiceSynchronized.mHandler);
                }
            }, mRttServiceSynchronized.mHandler);
            if (mHalDeviceManager.isStarted()) {
                mHalDeviceManager.registerRttControllerLifecycleCallback(
                        mRttLifecycleCb, mRttServiceSynchronized.mHandler);
            }
        });
    }

    private void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseHalLoggingEnabled = verboseEnabled || VDBG;
        updateVerboseLoggingEnabled();
        mRttMetrics.enableVerboseLogging(mVerboseLoggingEnabled);
        if (mWifiRttController != null) {
            mWifiRttController.enableVerboseLogging(mVerboseLoggingEnabled);
        }
    }

    /**
     * Handles the transition to boot completed phase
     */
    public void handleBootCompleted() {
        updateVerboseLoggingEnabled();
    }

    /*
     * ASYNCHRONOUS DOMAIN - can be called from different threads!
     */

    /**
     * Proxy for the final native call of the parent class. Enables mocking of
     * the function.
     */
    public int getMockableCallingUid() {
        return getCallingUid();
    }

    /**
     * Enable the API if possible: broadcast notification & start launching any queued requests
     *
     * If possible:
     * - RTT HAL is available
     * - Not in Idle mode
     * - Location Mode allows Wi-Fi based locationing
     */
    public void enableIfPossible() {
        boolean isAvailable = isAvailable();
        if (VDBG) Log.v(TAG, "enableIfPossible: isAvailable=" + isAvailable);
        if (!isAvailable) {
            return;
        }
        sendRttStateChangedBroadcast(true);
        mRttServiceSynchronized.mHandler.post(() -> {
            // queue should be empty at this point (but this call allows validation)
            mRttServiceSynchronized.executeNextRangingRequestIfPossible(false);
        });
    }

    /**
     * Disable the API:
     * - Clean-up (fail) pending requests
     * - Broadcast notification
     */
    public void disable() {
        if (VDBG) Log.v(TAG, "disable");
        sendRttStateChangedBroadcast(false);
        mRttServiceSynchronized.mHandler.post(() -> {
            mRttServiceSynchronized.cleanUpOnDisable();
        });
    }

    @Override
    public int handleShellCommand(@NonNull ParcelFileDescriptor in,
            @NonNull ParcelFileDescriptor out, @NonNull ParcelFileDescriptor err,
            @NonNull String[] args) {
        return mShellCommand.exec(
                this, in.getFileDescriptor(), out.getFileDescriptor(), err.getFileDescriptor(),
                args);
    }

    /**
     * Binder interface API to indicate whether the API is currently available. This requires an
     * immediate asynchronous response.
     */
    @Override
    public boolean isAvailable() {
        long ident = Binder.clearCallingIdentity();
        try {
            return mWifiRttController != null && !mPowerManager.isDeviceIdleMode()
                    && mWifiPermissionsUtil.isLocationModeEnabled();
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public Bundle getRttCharacteristics() {
        enforceAccessPermission();
        if (mCapabilities == null && mWifiRttController != null) {
            mCapabilities = mWifiRttController.getRttCapabilities();
        }
        return covertCapabilitiesToBundle(mCapabilities);
    }

    private Bundle covertCapabilitiesToBundle(WifiRttController.Capabilities capabilities) {
        Bundle characteristics = new Bundle();
        if (capabilities == null) {
            return characteristics;
        }
        characteristics.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_ONE_SIDED_RTT,
                capabilities.oneSidedRttSupported);
        characteristics.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_LCI, capabilities.lciSupported);
        characteristics.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_LCR, capabilities.lcrSupported);
        characteristics.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_STA_RESPONDER,
                capabilities.responderSupported);
        characteristics.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_NTB_INITIATOR,
                capabilities.ntbInitiatorSupported);
        return characteristics;
    }

    /**
     * Override IEEE 802.11az parameters with overlay values.
     */
    private void override11azOverlays(RangingRequest rangingRequest) {
        int minNtbTime = mContext.getResources().getInteger(
                R.integer.config_wifi80211azMinTimeBetweenNtbMeasurementsMicros);
        int maxNtbTime = mContext.getResources().getInteger(
                R.integer.config_wifi80211azMaxTimeBetweenNtbMeasurementsMicros);
        if (minNtbTime > 0 || maxNtbTime > 0) {
            for (ResponderConfig peer : rangingRequest.mRttPeers) {
                if (peer.is80211azNtbSupported()) {
                    if (maxNtbTime > 0) peer.setNtbMaxTimeBetweenMeasurementsMicros(maxNtbTime);
                    if (minNtbTime > 0) peer.setNtbMinTimeBetweenMeasurementsMicros(minNtbTime);
                }
            }
        }
    }

    /**
     * Binder interface API to start a ranging operation. Called on binder thread, operations needs
     * to be posted to handler thread.
     */
    @Override
    public void startRanging(IBinder binder, String callingPackage, String callingFeatureId,
            WorkSource workSource, RangingRequest request, IRttCallback callback, Bundle extras)
            throws RemoteException {
        if (VDBG) {
            Log.v(TAG, "startRanging: binder=" + binder + ", callingPackage=" + callingPackage
                    + ", workSource=" + workSource + ", request=" + request + ", callback="
                    + callback);
        }
        // verify arguments
        if (binder == null) {
            throw new IllegalArgumentException("Binder must not be null");
        }
        if (request == null || request.mRttPeers == null || request.mRttPeers.size() == 0) {
            throw new IllegalArgumentException("Request must not be null or empty");
        }
        for (ResponderConfig responder : request.mRttPeers) {
            if (responder == null) {
                throw new IllegalArgumentException("Request must not contain null Responders");
            }
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        }
        request.enforceValidity(mAwareManager != null);

        if (!isAvailable()) {
            try {
                mRttMetrics.recordOverallStatus(
                        WifiMetricsProto.WifiRttLog.OVERALL_RTT_NOT_AVAILABLE);
                callback.onRangingFailure(RangingResultCallback.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE);
            } catch (RemoteException e) {
                Log.e(TAG, "startRanging: disabled, callback failed -- " + e);
            }
            return;
        }

        final int uid = getMockableCallingUid();

        // permission checks
        enforceAccessPermission();
        enforceChangePermission();
        mWifiPermissionsUtil.checkPackage(uid, callingPackage);
        // check if only Aware APs are ranged.
        boolean onlyAwareApRanged = request.mRttPeers.stream().allMatch(
                config -> config.responderType == ResponderConfig.RESPONDER_AWARE);
        final Object attributionSource;
        if (onlyAwareApRanged && SdkLevel.isAtLeastT()) {
            // Special case: if only aware APs are ranged, then allow this request if the caller
            // has nearby permission.
            attributionSource = extras.getParcelable(
                    WifiManager.EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE);
            if (!mWifiPermissionsUtil.checkNearbyDevicesPermission(
                    (AttributionSource) attributionSource, true,
                    "wifi aware ranging")) {
                // No nearby permission. Still check for location permission.
                mWifiPermissionsUtil.enforceFineLocationPermission(
                        callingPackage, callingFeatureId, uid);
            }
        } else {
            attributionSource = null;
            mWifiPermissionsUtil.enforceFineLocationPermission(
                    callingPackage, callingFeatureId, uid);
        }

        final WorkSource ws;
        if (workSource != null) {
            enforceLocationHardware();
            // We only care about UIDs in the incoming worksources and not their associated
            // tags. Clear names so that other operations involving wakesources become simpler.
            ws = workSource.withoutNames();
        } else {
            ws = null;
        }

        boolean isCalledFromPrivilegedContext =
                checkLocationHardware() && mShellCommand.getControlParam(
                        CONTROL_PARAM_OVERRIDE_ASSUME_NO_PRIVILEGE_NAME) == 0;

        // register for binder death
        IBinder.DeathRecipient dr = new IBinder.DeathRecipient() {
            @Override
            public void binderDied() {
                if (mVerboseLoggingEnabled) Log.v(TAG, "binderDied: uid=" + uid);
                binder.unlinkToDeath(this, 0);

                mRttServiceSynchronized.mHandler.post(() -> {
                    mRttServiceSynchronized.cleanUpClientRequests(uid, null);
                });
            }
        };

        try {
            binder.linkToDeath(dr, 0);
        } catch (RemoteException e) {
            Log.e(TAG, "Error on linkToDeath - " + e);
            return;
        }

        override11azOverlays(request);

        mRttServiceSynchronized.mHandler.post(() -> {
            WorkSource sourceToUse = ws;
            if (ws == null || ws.isEmpty()) {
                sourceToUse = new WorkSource(uid);
            }
            mRttServiceSynchronized.queueRangingRequest(uid, sourceToUse, binder, dr,
                    callingPackage, callingFeatureId, request, callback,
                    isCalledFromPrivilegedContext, attributionSource);
        });
    }

    @Override
    public void cancelRanging(WorkSource workSource) throws RemoteException {
        if (VDBG) Log.v(TAG, "cancelRanging: workSource=" + workSource);
        enforceLocationHardware();
        // We only care about UIDs in the incoming worksources and not their associated
        // tags. Clear names so that other operations involving wakesources become simpler.
        final WorkSource ws = (workSource != null) ? workSource.withoutNames() : null;

        if (ws == null || ws.isEmpty()) {
            Log.e(TAG, "cancelRanging: invalid work-source -- " + ws);
            return;
        }

        mRttServiceSynchronized.mHandler.post(() -> {
            mRttServiceSynchronized.cleanUpClientRequests(0, ws);
        });
    }

    private void enforceAccessPermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE, TAG);
    }

    private void enforceChangePermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE, TAG);
    }

    private void enforceLocationHardware() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.LOCATION_HARDWARE,
                TAG);
    }

    private boolean checkLocationHardware() {
        return mContext.checkCallingOrSelfPermission(android.Manifest.permission.LOCATION_HARDWARE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void sendRttStateChangedBroadcast(boolean enabled) {
        if (VDBG) Log.v(TAG, "sendRttStateChangedBroadcast: enabled=" + enabled);
        final Intent intent = new Intent(WifiRttManager.ACTION_WIFI_RTT_STATE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.DUMP) != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump RttService from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        pw.println("Wi-Fi RTT Service");
        mRttServiceSynchronized.dump(fd, pw, args);
        pw.println("  mWifiRttController: " + mWifiRttController);
        if (mWifiRttController != null) {
            mWifiRttController.dump(pw);
        }
    }

    /*
     * SYNCHRONIZED DOMAIN
     */

    /**
     * RTT service implementation - synchronized on a single thread. All commands should be posted
     * to the exposed handler.
     */
    private class RttServiceSynchronized {
        public Handler mHandler;

        private int mNextCommandId = 1000;
        private Map<Integer, RttRequesterInfo> mRttRequesterInfo = new HashMap<>();
        private List<RttRequestInfo> mRttRequestQueue = new LinkedList<>();
        private WakeupMessage mRangingTimeoutMessage = null;

        RttServiceSynchronized(Looper looper) {
            mHandler = new Handler(looper);
            mRangingTimeoutMessage = new WakeupMessage(mContext, mHandler,
                    HAL_RANGING_TIMEOUT_TAG, () -> {
                timeoutRangingRequest();
            });
        }

        private void cancelRanging(RttRequestInfo rri) {
            ArrayList<MacAddress> macAddresses = new ArrayList<>();
            for (ResponderConfig peer : rri.request.mRttPeers) {
                macAddresses.add(peer.macAddress);
            }

            if (mWifiRttController != null) {
                mWifiRttController.rangeCancel(rri.cmdId, macAddresses);
            } else {
                Log.e(TAG, "Could not call cancelRanging, rttControllerHal is null");
            }
        }

        private void cleanUpOnDisable() {
            if (VDBG) Log.v(TAG, "RttServiceSynchronized.cleanUpOnDisable");
            for (RttRequestInfo rri : mRttRequestQueue) {
                try {
                    if (rri.dispatchedToNative) {
                        // may not be necessary in some cases (e.g. Wi-Fi disable may already clear
                        // up active RTT), but in other cases will be needed (doze disabling RTT
                        // but Wi-Fi still up). Doesn't hurt - worst case will fail.
                        cancelRanging(rri);
                    }
                    mRttMetrics.recordOverallStatus(
                            WifiMetricsProto.WifiRttLog.OVERALL_RTT_NOT_AVAILABLE);
                    rri.callback.onRangingFailure(
                            RangingResultCallback.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE);
                } catch (RemoteException e) {
                    Log.e(TAG, "RttServiceSynchronized.startRanging: disabled, callback failed -- "
                            + e);
                }
                rri.binder.unlinkToDeath(rri.dr, 0);
            }
            mRttRequestQueue.clear();
            mRangingTimeoutMessage.cancel();
        }

        /**
         * Remove entries related to the specified client and cancel any dispatched to HAL
         * requests. Expected to provide either the UID or the WorkSource (the other will be 0 or
         * null respectively).
         *
         * A workSource specification will be cleared from the requested workSource and the request
         * cancelled only if there are no remaining uids in the work-source.
         */
        private void cleanUpClientRequests(int uid, WorkSource workSource) {
            if (VDBG) {
                Log.v(TAG, "RttServiceSynchronized.cleanUpOnClientDeath: uid=" + uid
                        + ", workSource=" + workSource + ", mRttRequestQueue=" + mRttRequestQueue);
            }
            boolean dispatchedRequestAborted = false;
            ListIterator<RttRequestInfo> it = mRttRequestQueue.listIterator();
            while (it.hasNext()) {
                RttRequestInfo rri = it.next();

                boolean match = rri.uid == uid; // original UID will never be 0
                if (rri.workSource != null && workSource != null) {
                    rri.workSource.remove(workSource);
                    if (rri.workSource.isEmpty()) {
                        match = true;
                    }
                }

                if (match) {
                    if (!rri.dispatchedToNative) {
                        it.remove();
                        rri.binder.unlinkToDeath(rri.dr, 0);
                    } else {
                        dispatchedRequestAborted = true;
                        Log.d(TAG, "Client death - cancelling RTT operation in progress: cmdId="
                                + rri.cmdId);
                        mRangingTimeoutMessage.cancel();
                        cancelRanging(rri);
                    }
                }
            }

            if (VDBG) {
                Log.v(TAG, "RttServiceSynchronized.cleanUpOnClientDeath: uid=" + uid
                        + ", dispatchedRequestAborted=" + dispatchedRequestAborted
                        + ", after cleanup - mRttRequestQueue=" + mRttRequestQueue);
            }

            if (dispatchedRequestAborted) {
                executeNextRangingRequestIfPossible(true);
            }
        }

        private void timeoutRangingRequest() {
            if (VDBG) {
                Log.v(TAG, "RttServiceSynchronized.timeoutRangingRequest mRttRequestQueue="
                        + mRttRequestQueue);
            }
            if (mRttRequestQueue.size() == 0) {
                Log.w(TAG, "RttServiceSynchronized.timeoutRangingRequest: but nothing in queue!?");
                return;
            }
            RttRequestInfo rri = mRttRequestQueue.get(0);
            if (!rri.dispatchedToNative) {
                Log.w(TAG, "RttServiceSynchronized.timeoutRangingRequest: command not dispatched "
                        + "to native!?");
                return;
            }
            cancelRanging(rri);
            try {
                mRttMetrics.recordOverallStatus(WifiMetricsProto.WifiRttLog.OVERALL_TIMEOUT);
                rri.callback.onRangingFailure(RangingResultCallback.STATUS_CODE_FAIL);
            } catch (RemoteException e) {
                Log.e(TAG, "RttServiceSynchronized.timeoutRangingRequest: callback failed: " + e);
            }
            executeNextRangingRequestIfPossible(true);
        }

        private void queueRangingRequest(int uid, WorkSource workSource, IBinder binder,
                IBinder.DeathRecipient dr, String callingPackage, String callingFeatureId,
                RangingRequest request, IRttCallback callback,
                boolean isCalledFromPrivilegedContext, Object attributionSource) {
            mRttMetrics.recordRequest(workSource, request);

            if (isRequestorSpamming(workSource)) {
                Log.w(TAG,
                        "Work source " + workSource + " is spamming, dropping request: " + request);
                binder.unlinkToDeath(dr, 0);
                try {
                    mRttMetrics.recordOverallStatus(WifiMetricsProto.WifiRttLog.OVERALL_THROTTLE);
                    callback.onRangingFailure(RangingResultCallback.STATUS_CODE_FAIL);
                } catch (RemoteException e) {
                    Log.e(TAG, "RttServiceSynchronized.queueRangingRequest: spamming, callback "
                            + "failed -- " + e);
                }
                return;
            }

            RttRequestInfo newRequest = new RttRequestInfo();
            newRequest.uid = uid;
            newRequest.workSource = workSource;
            newRequest.binder = binder;
            newRequest.dr = dr;
            newRequest.callingPackage = callingPackage;
            newRequest.callingFeatureId = callingFeatureId;
            newRequest.request = request;
            newRequest.callback = callback;
            newRequest.isCalledFromPrivilegedContext = isCalledFromPrivilegedContext;
            newRequest.attributionSource = attributionSource;
            mRttRequestQueue.add(newRequest);

            if (VDBG) {
                Log.v(TAG, "RttServiceSynchronized.queueRangingRequest: newRequest=" + newRequest);
            }

            executeNextRangingRequestIfPossible(false);
        }

        private boolean isRequestorSpamming(WorkSource ws) {
            if (VDBG) Log.v(TAG, "isRequestorSpamming: ws" + ws);

            SparseIntArray counts = new SparseIntArray();

            for (RttRequestInfo rri : mRttRequestQueue) {
                for (int i = 0; i < rri.workSource.size(); ++i) {
                    int uid = rri.workSource.getUid(i);
                    counts.put(uid, counts.get(uid) + 1);
                }

                final List<WorkChain> workChains = rri.workSource.getWorkChains();
                if (workChains != null) {
                    for (int i = 0; i < workChains.size(); ++i) {
                        final int uid = workChains.get(i).getAttributionUid();
                        counts.put(uid, counts.get(uid) + 1);
                    }
                }
            }

            for (int i = 0; i < ws.size(); ++i) {
                if (counts.get(ws.getUid(i)) < MAX_QUEUED_PER_UID) {
                    return false;
                }
            }

            final List<WorkChain> workChains = ws.getWorkChains();
            if (workChains != null) {
                for (int i = 0; i < workChains.size(); ++i) {
                    final int uid = workChains.get(i).getAttributionUid();
                    if (counts.get(uid) < MAX_QUEUED_PER_UID) {
                        return false;
                    }
                }
            }

            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "isRequestorSpamming: ws=" + ws + ", someone is spamming: " + counts);
            }
            return true;
        }

        private void executeNextRangingRequestIfPossible(boolean popFirst) {
            if (VDBG) Log.v(TAG, "executeNextRangingRequestIfPossible: popFirst=" + popFirst);

            if (popFirst) {
                if (mRttRequestQueue.size() == 0) {
                    Log.w(TAG, "executeNextRangingRequestIfPossible: pop requested - but empty "
                            + "queue!? Ignoring pop.");
                } else {
                    RttRequestInfo topOfQueueRequest = mRttRequestQueue.remove(0);
                    topOfQueueRequest.binder.unlinkToDeath(topOfQueueRequest.dr, 0);
                }
            }

            if (mRttRequestQueue.size() == 0) {
                if (VDBG) Log.v(TAG, "executeNextRangingRequestIfPossible: no requests pending");
                return;
            }

            // if top of list is in progress then do nothing
            RttRequestInfo nextRequest = mRttRequestQueue.get(0);
            if (nextRequest.peerHandlesTranslated || nextRequest.dispatchedToNative) {
                if (VDBG) {
                    Log.v(TAG, "executeNextRangingRequestIfPossible: called but a command is "
                            + "executing. topOfQueue=" + nextRequest);
                }
                return;
            }

            startRanging(nextRequest);
        }

        private void startRanging(RttRequestInfo nextRequest) {
            if (VDBG) {
                Log.v(TAG, "RttServiceSynchronized.startRanging: nextRequest=" + nextRequest);
            }

            if (!isAvailable()) {
                Log.d(TAG, "RttServiceSynchronized.startRanging: disabled");
                try {
                    mRttMetrics.recordOverallStatus(
                            WifiMetricsProto.WifiRttLog.OVERALL_RTT_NOT_AVAILABLE);
                    nextRequest.callback.onRangingFailure(
                            RangingResultCallback.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE);
                } catch (RemoteException e) {
                    Log.e(TAG, "RttServiceSynchronized.startRanging: disabled, callback failed -- "
                            + e);
                    executeNextRangingRequestIfPossible(true);
                    return;
                }
            }

            if (processAwarePeerHandles(nextRequest)) {
                if (VDBG) {
                    Log.v(TAG, "RttServiceSynchronized.startRanging: deferring due to PeerHandle "
                            + "Aware requests");
                }
                return;
            }

            if (!preExecThrottleCheck(nextRequest.workSource, nextRequest.callingPackage)) {
                Log.w(TAG, "RttServiceSynchronized.startRanging: execution throttled - nextRequest="
                        + nextRequest + ", mRttRequesterInfo=" + mRttRequesterInfo);
                try {
                    mRttMetrics.recordOverallStatus(WifiMetricsProto.WifiRttLog.OVERALL_THROTTLE);
                    nextRequest.callback.onRangingFailure(RangingResultCallback.STATUS_CODE_FAIL);
                } catch (RemoteException e) {
                    Log.e(TAG, "RttServiceSynchronized.startRanging: throttled, callback failed -- "
                            + e);
                }
                executeNextRangingRequestIfPossible(true);
                return;
            }

            nextRequest.cmdId = mNextCommandId++;
            mLastRequestTimestamp = mClock.getWallClockMillis();
            if (mWifiRttController != null
                    && mWifiRttController.rangeRequest(nextRequest.cmdId, nextRequest.request)) {
                long timeout = HAL_RANGING_TIMEOUT_MS;
                for (ResponderConfig responderConfig : nextRequest.request.mRttPeers) {
                    if (responderConfig.responderType == ResponderConfig.RESPONDER_AWARE) {
                        timeout = HAL_AWARE_RANGING_TIMEOUT_MS;
                        break;
                    }
                }
                mRangingTimeoutMessage.schedule(mClock.getElapsedSinceBootMillis() + timeout);
            } else {
                Log.w(TAG, "RttServiceSynchronized.startRanging: native rangeRequest call failed");
                if (mWifiRttController == null) {
                    Log.e(TAG, "mWifiRttController is null");
                }
                try {
                    mRttMetrics.recordOverallStatus(
                            WifiMetricsProto.WifiRttLog.OVERALL_HAL_FAILURE);
                    nextRequest.callback.onRangingFailure(RangingResultCallback.STATUS_CODE_FAIL);
                } catch (RemoteException e) {
                    Log.e(TAG, "RttServiceSynchronized.startRanging: HAL request failed, callback "
                            + "failed -- " + e);
                }
                executeNextRangingRequestIfPossible(true);
            }
            nextRequest.dispatchedToNative = true;
        }

        /**
         * Perform pre-execution throttling checks:
         * - If all uids in ws are in background then check last execution and block if request is
         * more frequent than permitted
         * - If executing (i.e. permitted) then update execution time
         *
         * Returns true to permit execution, false to abort it.
         */
        private boolean preExecThrottleCheck(WorkSource ws, String callingPackage) {
            if (VDBG) Log.v(TAG, "preExecThrottleCheck: ws=" + ws);

            // are all UIDs running in the background or is at least 1 in the foreground?
            boolean allUidsInBackground = true;
            for (int i = 0; i < ws.size(); ++i) {
                int uidImportance = mActivityManager.getUidImportance(ws.getUid(i));
                if (VDBG) {
                    Log.v(TAG, "preExecThrottleCheck: uid=" + ws.getUid(i) + " -> importance="
                            + uidImportance);
                }
                if (uidImportance <= IMPORTANCE_FOREGROUND_SERVICE) {
                    allUidsInBackground = false;
                    break;
                }
            }

            final List<WorkChain> workChains = ws.getWorkChains();
            if (allUidsInBackground && workChains != null) {
                for (int i = 0; i < workChains.size(); ++i) {
                    final WorkChain wc = workChains.get(i);
                    int uidImportance = mActivityManager.getUidImportance(wc.getAttributionUid());
                    if (VDBG) {
                        Log.v(TAG, "preExecThrottleCheck: workChain=" + wc + " -> importance="
                                + uidImportance);
                    }

                    if (uidImportance <= IMPORTANCE_FOREGROUND_SERVICE) {
                        allUidsInBackground = false;
                        break;
                    }
                }
            }
            if (allUidsInBackground) {
                String[] exceptionList = mContext.getResources().getStringArray(
                        R.array.config_wifiBackgroundRttThrottleExceptionList);
                for (String packageName : exceptionList) {
                    if (TextUtils.equals(packageName, callingPackage)) {
                        allUidsInBackground = false;
                        break;
                    }
                }
            }

            // if all UIDs are in background then check timestamp since last execution and see if
            // any is permitted (infrequent enough)
            boolean allowExecution = false;
            int backgroundProcessExecGapMs = mContext.getResources().getInteger(
                    R.integer.config_wifiRttBackgroundExecGapMs);
            long mostRecentExecutionPermitted =
                    mClock.getElapsedSinceBootMillis() - backgroundProcessExecGapMs;
            if (allUidsInBackground) {
                for (int i = 0; i < ws.size(); ++i) {
                    RttRequesterInfo info = mRttRequesterInfo.get(ws.getUid(i));
                    if (info == null || info.lastRangingExecuted < mostRecentExecutionPermitted) {
                        allowExecution = true;
                        break;
                    }
                }

                if (workChains != null & !allowExecution) {
                    for (int i = 0; i < workChains.size(); ++i) {
                        final WorkChain wc = workChains.get(i);
                        RttRequesterInfo info = mRttRequesterInfo.get(wc.getAttributionUid());
                        if (info == null
                                || info.lastRangingExecuted < mostRecentExecutionPermitted) {
                            allowExecution = true;
                            break;
                        }
                    }
                }
            } else {
                allowExecution = true;
            }

            // update exec time
            if (allowExecution) {
                for (int i = 0; i < ws.size(); ++i) {
                    RttRequesterInfo info = mRttRequesterInfo.get(ws.getUid(i));
                    if (info == null) {
                        info = new RttRequesterInfo();
                        mRttRequesterInfo.put(ws.getUid(i), info);
                    }
                    info.lastRangingExecuted = mClock.getElapsedSinceBootMillis();
                }

                if (workChains != null) {
                    for (int i = 0; i < workChains.size(); ++i) {
                        final WorkChain wc = workChains.get(i);
                        RttRequesterInfo info = mRttRequesterInfo.get(wc.getAttributionUid());
                        if (info == null) {
                            info = new RttRequesterInfo();
                            mRttRequesterInfo.put(wc.getAttributionUid(), info);
                        }
                        info.lastRangingExecuted = mClock.getElapsedSinceBootMillis();
                    }
                }
            }

            return allowExecution;
        }

        /**
         * Check request for any PeerHandle Aware requests. If there are any: issue requests to
         * translate the peer ID to a MAC address and abort current execution of the range request.
         * The request will be re-attempted when response is received.
         *
         * In cases of failure: pop the current request and execute the next one. Failures:
         * - Not able to connect to remote service (unlikely)
         * - Request already processed: but we're missing information
         *
         * @return true if need to abort execution, false otherwise.
         */
        private boolean processAwarePeerHandles(RttRequestInfo request) {
            List<Integer> peerIdsNeedingTranslation = new ArrayList<>();
            for (ResponderConfig rttPeer : request.request.mRttPeers) {
                if (rttPeer.peerHandle != null && rttPeer.macAddress == null) {
                    peerIdsNeedingTranslation.add(rttPeer.peerHandle.peerId);
                }
            }

            if (peerIdsNeedingTranslation.size() == 0) {
                return false;
            }

            if (request.peerHandlesTranslated) {
                Log.w(TAG, "processAwarePeerHandles: request=" + request
                        + ": PeerHandles translated - but information still missing!?");
                try {
                    mRttMetrics.recordOverallStatus(
                            WifiMetricsProto.WifiRttLog.OVERALL_AWARE_TRANSLATION_FAILURE);
                    request.callback.onRangingFailure(RangingResultCallback.STATUS_CODE_FAIL);
                } catch (RemoteException e) {
                    Log.e(TAG, "processAwarePeerHandles: onRangingResults failure -- " + e);
                }
                executeNextRangingRequestIfPossible(true);
                return true; // an abort because we removed request and are executing next one
            }

            request.peerHandlesTranslated = true;
            int[] peerIdsArray = peerIdsNeedingTranslation.stream().mapToInt(i -> i).toArray();
            mAwareManager.requestMacAddresses(request.uid, peerIdsArray,
                    new IWifiAwareMacAddressProvider.Stub() {
                        @Override
                        public void macAddress(MacAddrMapping[] peerIdToMacList) {
                            // ASYNC DOMAIN
                            mHandler.post(() -> {
                                // BACK TO SYNC DOMAIN
                                processReceivedAwarePeerMacAddresses(request, peerIdToMacList);
                            });
                        }
                    });
            return true; // a deferral
        }

        private void processReceivedAwarePeerMacAddresses(RttRequestInfo request,
                MacAddrMapping[] peerIdToMacList) {
            if (VDBG) {
                Log.v(TAG, "processReceivedAwarePeerMacAddresses: request=" + request);
                Log.v(TAG, "processReceivedAwarePeerMacAddresses: peerIdToMacList begin");
                for (MacAddrMapping mapping : peerIdToMacList) {
                    Log.v(TAG, "    " + mapping.peerId + ": "
                            + MacAddress.fromBytes(mapping.macAddress));
                }
                Log.v(TAG, "processReceivedAwarePeerMacAddresses: peerIdToMacList end");
            }

            RangingRequest.Builder newRequestBuilder = new RangingRequest.Builder();
            for (ResponderConfig rttPeer : request.request.mRttPeers) {
                if (rttPeer.peerHandle != null && rttPeer.macAddress == null) {
                    byte[] mac = null;
                    for (MacAddrMapping mapping : peerIdToMacList) {
                        if (mapping.peerId == rttPeer.peerHandle.peerId) {
                            mac = mapping.macAddress;
                            break;
                        }
                    }
                    if (mac == null || mac.length != 6) {
                        Log.e(TAG, "processReceivedAwarePeerMacAddresses: received an invalid MAC "
                                + "address for peerId=" + rttPeer.peerHandle.peerId);
                        continue;
                    }
                    // To create a ResponderConfig object with both a MAC address and peer
                    // handler, we're bypassing the standard Builder pattern and directly using
                    // the constructor. This is because the SDK's Builder.build() method has a
                    // built-in restriction that prevents setting both properties simultaneously.
                    // To avoid triggering this exception, we're directly invoking the
                    // constructor to accommodate both values.
                    ResponderConfig.Builder responderConfigBuilder = new ResponderConfig.Builder()
                            .setMacAddress(MacAddress.fromBytes(mac))
                            .setPeerHandle(rttPeer.peerHandle)
                            .setResponderType(rttPeer.getResponderType())
                            .set80211mcSupported(rttPeer.is80211mcSupported())
                            .set80211azNtbSupported(rttPeer.is80211azNtbSupported())
                            .setChannelWidth(rttPeer.getChannelWidth())
                            .setFrequencyMhz(rttPeer.getFrequencyMhz())
                            .setCenterFreq1Mhz(rttPeer.getCenterFreq1Mhz())
                            .setCenterFreq0Mhz(rttPeer.getCenterFreq0Mhz())
                            .setPreamble(rttPeer.getPreamble());
                    newRequestBuilder.addResponder(new ResponderConfig(responderConfigBuilder));
                } else {
                    newRequestBuilder.addResponder(rttPeer);
                }
            }
            newRequestBuilder.setRttBurstSize(request.request.getRttBurstSize());
            request.request = newRequestBuilder.build();

            // run request again
            startRanging(request);
        }

        private void onRangingResults(int cmdId, List<RangingResult> results) {
            if (mRttRequestQueue.size() == 0) {
                Log.e(TAG, "RttServiceSynchronized.onRangingResults: no current RTT request "
                        + "pending!?");
                return;
            }
            mRangingTimeoutMessage.cancel();
            RttRequestInfo topOfQueueRequest = mRttRequestQueue.get(0);

            if (VDBG) {
                Log.v(TAG, "RttServiceSynchronized.onRangingResults: cmdId=" + cmdId
                        + ", topOfQueueRequest=" + topOfQueueRequest + ", results="
                        + Arrays.toString(results.toArray()));
            }

            if (topOfQueueRequest.cmdId != cmdId) {
                Log.e(TAG, "RttServiceSynchronized.onRangingResults: cmdId=" + cmdId
                        + ", does not match pending RTT request cmdId=" + topOfQueueRequest.cmdId);
                return;
            }

            boolean onlyAwareApRanged = topOfQueueRequest.request.mRttPeers.stream().allMatch(
                    config -> config.responderType == ResponderConfig.RESPONDER_AWARE);
            boolean permissionGranted = false;
            if (onlyAwareApRanged && SdkLevel.isAtLeastT()) {
                // Special case: if only aware APs are ranged, then allow this request if the caller
                // has nearby permission.
                permissionGranted = mWifiPermissionsUtil.checkNearbyDevicesPermission(
                        (AttributionSource) topOfQueueRequest.attributionSource, true,
                        "wifi aware on ranging result");
            }
            if (!permissionGranted) {
                permissionGranted =
                        mWifiPermissionsUtil.checkCallersLocationPermission(
                                topOfQueueRequest.callingPackage,
                                topOfQueueRequest.callingFeatureId,
                                topOfQueueRequest.uid, /* coarseForTargetSdkLessThanQ */ false,
                                null) && mWifiPermissionsUtil.isLocationModeEnabled();
            }
            try {
                if (permissionGranted) {
                    List<RangingResult> finalResults = postProcessResults(topOfQueueRequest.request,
                            results, topOfQueueRequest.isCalledFromPrivilegedContext);
                    mRttMetrics.recordOverallStatus(WifiMetricsProto.WifiRttLog.OVERALL_SUCCESS);
                    mRttMetrics.recordResult(topOfQueueRequest.request, results,
                            (int) (mClock.getWallClockMillis() - mLastRequestTimestamp));
                    if (VDBG) {
                        Log.v(TAG, "RttServiceSynchronized.onRangingResults: finalResults="
                                + finalResults);
                    }
                    topOfQueueRequest.callback.onRangingResults(finalResults);
                } else {
                    Log.w(TAG, "RttServiceSynchronized.onRangingResults: location permission "
                            + "revoked - not forwarding results");
                    mRttMetrics.recordOverallStatus(
                            WifiMetricsProto.WifiRttLog.OVERALL_LOCATION_PERMISSION_MISSING);
                    topOfQueueRequest.callback.onRangingFailure(
                            RangingResultCallback.STATUS_CODE_FAIL);
                }
            } catch (RemoteException e) {
                Log.e(TAG,
                        "RttServiceSynchronized.onRangingResults: callback exception -- " + e);
            }

            executeNextRangingRequestIfPossible(true);
        }

        /*
         * Post process the results:
         * - For requests without results: add FAILED results
         * - For Aware requests using PeerHandle: replace MAC address with PeerHandle
         * - Effectively: throws away results which don't match requests
         */
        private List<RangingResult> postProcessResults(RangingRequest request,
                List<RangingResult> results, boolean isCalledFromPrivilegedContext) {
            Map<MacAddress, RangingResult> resultEntries = new HashMap<>();
            for (RangingResult result : results) {
                resultEntries.put(result.getMacAddress(), result);
            }

            List<RangingResult> finalResults = new ArrayList<>(request.mRttPeers.size());

            for (ResponderConfig peer : request.mRttPeers) {
                RangingResult resultForRequest = resultEntries.get(peer.macAddress);
                if (resultForRequest == null || resultForRequest.getStatus()
                        != WifiRttController.FRAMEWORK_RTT_STATUS_SUCCESS) {
                    if (mVerboseLoggingEnabled) {
                        Log.v(TAG, "postProcessResults: missing=" + peer.macAddress);
                    }
                    RangingResult.Builder builder = new RangingResult.Builder()
                            .setStatus(RangingResult.STATUS_FAIL);
                    if (peer.peerHandle == null) {
                        builder.setMacAddress(peer.getMacAddress());
                    } else {
                        builder.setPeerHandle(peer.peerHandle);
                    }
                    finalResults.add(builder.build());
                } else {

                    // Clear LCI and LCR data if the location data should not be retransmitted,
                    // has a retention expiration time, contains no useful data, or did not parse,
                    // or the caller is not in a privileged context.
                    byte[] lci = resultForRequest.getLci();
                    byte[] lcr = resultForRequest.getLcr();
                    ResponderLocation responderLocation =
                            resultForRequest.getUnverifiedResponderLocation();
                    if (responderLocation == null || !isCalledFromPrivilegedContext) {
                        lci = null;
                        lcr = null;
                    }
                    RangingResult.Builder builder = new RangingResult.Builder();
                    builder.setStatus(RangingResult.STATUS_SUCCESS)
                            .setDistanceMm(resultForRequest.getDistanceMm())
                            .setDistanceStdDevMm(resultForRequest.getDistanceStdDevMm())
                            .setRssi(resultForRequest.getRssi())
                            .setNumAttemptedMeasurements(
                                    resultForRequest.getNumAttemptedMeasurements())
                            .setNumSuccessfulMeasurements(
                                    resultForRequest.getNumSuccessfulMeasurements())
                            .setLci(lci)
                            .setLcr(lcr)
                            .setUnverifiedResponderLocation(responderLocation)
                            .setRangingTimestampMillis(resultForRequest.getRangingTimestampMillis())
                            .set80211mcMeasurement(resultForRequest.is80211mcMeasurement())
                            .setMeasurementChannelFrequencyMHz(
                                    resultForRequest.getMeasurementChannelFrequencyMHz())
                            .setMeasurementBandwidth(resultForRequest.getMeasurementBandwidth())
                            .set80211azNtbMeasurement(resultForRequest.is80211azNtbMeasurement())
                            .setMinTimeBetweenNtbMeasurementsMicros(
                                    resultForRequest.getMinTimeBetweenNtbMeasurementsMicros())
                            .setMaxTimeBetweenNtbMeasurementsMicros(
                                    resultForRequest.getMaxTimeBetweenNtbMeasurementsMicros())
                            .set80211azInitiatorTxLtfRepetitionsCount(
                                    resultForRequest.get80211azInitiatorTxLtfRepetitionsCount())
                            .set80211azResponderTxLtfRepetitionsCount(
                                    resultForRequest.get80211azResponderTxLtfRepetitionsCount())
                            .set80211azNumberOfTxSpatialStreams(
                                    resultForRequest.get80211azNumberOfTxSpatialStreams())
                            .set80211azNumberOfRxSpatialStreams(
                                    resultForRequest.get80211azNumberOfRxSpatialStreams());
                    if (peer.peerHandle == null) {
                        builder.setMacAddress(peer.getMacAddress());
                    } else {
                        builder.setPeerHandle(peer.peerHandle);
                    }
                    if (SdkLevel.isAtLeastV() && resultForRequest.getVendorData() != null
                            && !resultForRequest.getVendorData().isEmpty()) {
                        builder.setVendorData(resultForRequest.getVendorData());
                    }
                    finalResults.add(builder.build());
                }
            }
            return finalResults;
        }

        // dump call (asynchronous most likely)
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            pw.println("  mNextCommandId: " + mNextCommandId);
            pw.println("  mRttRequesterInfo: " + mRttRequesterInfo);
            pw.println("  mRttRequestQueue: " + mRttRequestQueue);
            pw.println("  mRangingTimeoutMessage: " + mRangingTimeoutMessage);
            pw.println("  mWifiRttController: " + mWifiRttController);
            pw.println("  mHalDeviceManager: " + mHalDeviceManager);
            mRttMetrics.dump(fd, pw, args);
        }
    }

    private static class RttRequestInfo {
        public int uid;
        public WorkSource workSource;
        public IBinder binder;
        public IBinder.DeathRecipient dr;
        public String callingPackage;
        public String callingFeatureId;
        public RangingRequest request;
        public IRttCallback callback;
        public boolean isCalledFromPrivilegedContext;
        // This should be of Class AttributionSource, not is declared as Object for mainline
        // backward compatibility.
        public Object attributionSource;

        public int cmdId = 0; // uninitialized cmdId value
        public boolean dispatchedToNative = false;
        public boolean peerHandlesTranslated = false;

        @Override
        public String toString() {
            return new StringBuilder("RttRequestInfo: uid=").append(uid).append(
                    ", workSource=").append(workSource).append(", binder=").append(binder).append(
                    ", dr=").append(dr).append(", callingPackage=").append(callingPackage).append(
                    ", callingFeatureId=").append(callingFeatureId).append(", request=").append(
                    request.toString()).append(", callback=").append(callback).append(
                    ", cmdId=").append(cmdId).append(", peerHandlesTranslated=").append(
                    peerHandlesTranslated).append(", isCalledFromPrivilegedContext=").append(
                    isCalledFromPrivilegedContext).toString();
        }
    }

    private static class RttRequesterInfo {
        public long lastRangingExecuted;

        @Override
        public String toString() {
            return new StringBuilder("RttRequesterInfo: lastRangingExecuted=").append(
                    lastRangingExecuted).toString();
        }
    }
}
