/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.bluetooth.le_scan;

import android.annotation.RequiresPermission;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.Display;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.BluetoothAdapterProxy;
import com.android.bluetooth.flags.Flags;
import com.android.bluetooth.gatt.FilterParams;
import com.android.bluetooth.gatt.GattObjectsFactory;
import com.android.bluetooth.gatt.GattService;
import com.android.bluetooth.gatt.GattServiceConfig;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class that handles Bluetooth LE scan related operations.
 *
 * @hide
 */
public class ScanManager {
    private static final boolean DBG = GattServiceConfig.DBG;
    private static final String TAG = GattServiceConfig.TAG_PREFIX + "ScanManager";

    /**
     * Scan params corresponding to regular scan setting
     */
    private static final int SCAN_MODE_LOW_POWER_WINDOW_MS = 140;
    private static final int SCAN_MODE_LOW_POWER_INTERVAL_MS = 1400;
    private static final int SCAN_MODE_BALANCED_WINDOW_MS = 183;
    private static final int SCAN_MODE_BALANCED_INTERVAL_MS = 730;
    private static final int SCAN_MODE_LOW_LATENCY_WINDOW_MS = 100;
    private static final int SCAN_MODE_LOW_LATENCY_INTERVAL_MS = 100;
    public static final int SCAN_MODE_SCREEN_OFF_LOW_POWER_WINDOW_MS = 512;
    public static final int SCAN_MODE_SCREEN_OFF_LOW_POWER_INTERVAL_MS = 10240;
    public static final int SCAN_MODE_SCREEN_OFF_BALANCED_WINDOW_MS = 183;
    public static final int SCAN_MODE_SCREEN_OFF_BALANCED_INTERVAL_MS = 730;

    // Result type defined in bt stack. Need to be accessed by GattService.
    public static final int SCAN_RESULT_TYPE_TRUNCATED = 1;
    public static final int SCAN_RESULT_TYPE_FULL = 2;
    static final int SCAN_RESULT_TYPE_BOTH = 3;

    // Messages for handling BLE scan operations.
    @VisibleForTesting
    static final int MSG_START_BLE_SCAN = 0;
    static final int MSG_STOP_BLE_SCAN = 1;
    static final int MSG_FLUSH_BATCH_RESULTS = 2;
    static final int MSG_SCAN_TIMEOUT = 3;
    static final int MSG_SUSPEND_SCANS = 4;
    static final int MSG_RESUME_SCANS = 5;
    static final int MSG_IMPORTANCE_CHANGE = 6;
    static final int MSG_SCREEN_ON = 7;
    static final int MSG_SCREEN_OFF = 8;
    static final int MSG_REVERT_SCAN_MODE_UPGRADE = 9;
    static final int MSG_START_CONNECTING = 10;
    static final int MSG_STOP_CONNECTING = 11;
    private static final int MSG_BT_PROFILE_CONN_STATE_CHANGED = 12;
    private static final String ACTION_REFRESH_BATCHED_SCAN =
            "com.android.bluetooth.gatt.REFRESH_BATCHED_SCAN";

    // Timeout for each controller operation.
    private static final int OPERATION_TIME_OUT_MILLIS = 500;
    private static final int MAX_IS_UID_FOREGROUND_MAP_SIZE = 500;

    private int mLastConfiguredScanSetting = Integer.MIN_VALUE;
    // Scan parameters for batch scan.
    private BatchScanParams mBatchScanParms;

    private final Object mCurUsedTrackableAdvertisementsLock = new Object();
    @GuardedBy("mCurUsedTrackableAdvertisementsLock")
    private int mCurUsedTrackableAdvertisements = 0;

    private final GattService mService;
    private final AdapterService mAdapterService;
    private BroadcastReceiver mBatchAlarmReceiver;
    private boolean mBatchAlarmReceiverRegistered;
    private ScanNative mScanNative;
    private volatile ClientHandler mHandler;
    private BluetoothAdapterProxy mBluetoothAdapterProxy;

    private Set<ScanClient> mRegularScanClients;
    private Set<ScanClient> mBatchClients;
    private Set<ScanClient> mSuspendedScanClients;
    private SparseIntArray mPriorityMap = new SparseIntArray();

    private DisplayManager mDm;

    private ActivityManager mActivityManager;
    private LocationManager mLocationManager;
    private static final int FOREGROUND_IMPORTANCE_CUTOFF =
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
    private static final boolean DEFAULT_UID_IS_FOREGROUND = true;
    private static final int SCAN_MODE_APP_IN_BACKGROUND = ScanSettings.SCAN_MODE_LOW_POWER;
    private static final int SCAN_MODE_FORCE_DOWNGRADED = ScanSettings.SCAN_MODE_LOW_POWER;
    private static final int SCAN_MODE_MAX_IN_CONCURRENCY = ScanSettings.SCAN_MODE_BALANCED;
    private final SparseBooleanArray mIsUidForegroundMap = new SparseBooleanArray();
    private boolean mScreenOn = false;
    @VisibleForTesting boolean mIsConnecting;
    @VisibleForTesting int mProfilesConnecting;
    private int mProfilesConnected, mProfilesDisconnecting;

    @VisibleForTesting
    static class UidImportance {
        public int uid;
        public int importance;

        UidImportance(int uid, int importance) {
            this.uid = uid;
            this.importance = importance;
        }
    }

    public ScanManager(
            GattService service,
            AdapterService adapterService,
            BluetoothAdapterProxy bluetoothAdapterProxy,
            Looper looper) {
        mRegularScanClients =
                Collections.newSetFromMap(new ConcurrentHashMap<ScanClient, Boolean>());
        mBatchClients = Collections.newSetFromMap(new ConcurrentHashMap<ScanClient, Boolean>());
        mSuspendedScanClients =
                Collections.newSetFromMap(new ConcurrentHashMap<ScanClient, Boolean>());
        mService = service;
        mAdapterService = adapterService;
        mScanNative = new ScanNative();
        mDm = mService.getSystemService(DisplayManager.class);
        mActivityManager = mService.getSystemService(ActivityManager.class);
        mLocationManager = mAdapterService.getSystemService(LocationManager.class);
        mBluetoothAdapterProxy = bluetoothAdapterProxy;
        mIsConnecting = false;

        mPriorityMap.put(ScanSettings.SCAN_MODE_OPPORTUNISTIC, 0);
        mPriorityMap.put(ScanSettings.SCAN_MODE_SCREEN_OFF, 1);
        mPriorityMap.put(ScanSettings.SCAN_MODE_LOW_POWER, 2);
        mPriorityMap.put(ScanSettings.SCAN_MODE_SCREEN_OFF_BALANCED, 3);
        // BALANCED and AMBIENT_DISCOVERY now have the same settings and priority.
        mPriorityMap.put(ScanSettings.SCAN_MODE_BALANCED, 4);
        mPriorityMap.put(ScanSettings.SCAN_MODE_AMBIENT_DISCOVERY, 4);
        mPriorityMap.put(ScanSettings.SCAN_MODE_LOW_LATENCY, 5);

        mHandler = new ClientHandler(looper);
        if (mDm != null) {
            mDm.registerDisplayListener(mDisplayListener, null);
        }
        mScreenOn = isScreenOn();
        AppScanStats.initScanRadioState();
        AppScanStats.setScreenState(mScreenOn);
        if (mActivityManager != null) {
            mActivityManager.addOnUidImportanceListener(mUidImportanceListener,
                    FOREGROUND_IMPORTANCE_CUTOFF);
        }
        IntentFilter locationIntentFilter = new IntentFilter(LocationManager.MODE_CHANGED_ACTION);
        locationIntentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mService.registerReceiver(mLocationReceiver, locationIntentFilter);
    }

    public void cleanup() {
        mRegularScanClients.clear();
        mBatchClients.clear();
        mSuspendedScanClients.clear();
        mScanNative.cleanup();

        if (mActivityManager != null) {
            try {
                mActivityManager.removeOnUidImportanceListener(mUidImportanceListener);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "exception when invoking removeOnUidImportanceListener", e);
            }
        }

        if (mDm != null) {
            mDm.unregisterDisplayListener(mDisplayListener);
        }

        if (mHandler != null) {
            // Shut down the thread
            mHandler.removeCallbacksAndMessages(null);
            Looper looper = mHandler.getLooper();
            if (looper != null) {
                looper.quitSafely();
            }
            mHandler = null;
        }

        try {
            mService.unregisterReceiver(mLocationReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "exception when invoking unregisterReceiver(mLocationReceiver)", e);
        }
    }

    public void registerScanner(UUID uuid) {
        mScanNative.registerScanner(uuid.getLeastSignificantBits(),
                uuid.getMostSignificantBits());
    }

    public void unregisterScanner(int scannerId) {
        mScanNative.unregisterScanner(scannerId);
    }

    /**
     * Returns the regular scan queue.
     */
    public Set<ScanClient> getRegularScanQueue() {
        return mRegularScanClients;
    }

    /**
     * Returns the suspended scan queue.
     */
    Set<ScanClient> getSuspendedScanQueue() {
        return mSuspendedScanClients;
    }

    /**
     * Returns batch scan queue.
     */
    public Set<ScanClient> getBatchScanQueue() {
        return mBatchClients;
    }

    /**
     * Returns a set of full batch scan clients.
     */
    public Set<ScanClient> getFullBatchScanQueue() {
        // TODO: split full batch scan clients and truncated batch clients so we don't need to
        // construct this every time.
        Set<ScanClient> fullBatchClients = new HashSet<ScanClient>();
        for (ScanClient client : mBatchClients) {
            if (client.settings.getScanResultType() == ScanSettings.SCAN_RESULT_TYPE_FULL) {
                fullBatchClients.add(client);
            }
        }
        return fullBatchClients;
    }

    public void startScan(ScanClient client) {
        if (DBG) {
            Log.d(TAG, "startScan() " + client);
        }
        sendMessage(MSG_START_BLE_SCAN, client);
    }

    public void stopScan(int scannerId) {
        ScanClient client = mScanNative.getBatchScanClient(scannerId);
        if (client == null) {
            client = mScanNative.getRegularScanClient(scannerId);
        }
        if (client == null) {
            client = mScanNative.getSuspendedScanClient(scannerId);
        }
        sendMessage(MSG_STOP_BLE_SCAN, client);
    }

    public void flushBatchScanResults(ScanClient client) {
        sendMessage(MSG_FLUSH_BATCH_RESULTS, client);
    }

    public void callbackDone(int scannerId, int status) {
        mScanNative.callbackDone(scannerId, status);
    }

    private void sendMessage(int what, ScanClient client) {
        final ClientHandler handler = mHandler;
        if (handler == null) {
            Log.d(TAG, "sendMessage: mHandler is null.");
            return;
        }
        Message message = new Message();
        message.what = what;
        message.obj = client;
        handler.sendMessage(message);
    }

    private boolean isFilteringSupported() {
        if (mBluetoothAdapterProxy == null) {
            Log.e(TAG, "mBluetoothAdapterProxy is null");
            return false;
        }
        return mBluetoothAdapterProxy.isOffloadedScanFilteringSupported();
    }

    public boolean isAutoBatchScanClientEnabled(ScanClient client) {
        return mScanNative.isAutoBatchScanClientEnabled(client);
    }

    // Handler class that handles BLE scan operations.
    private class ClientHandler extends Handler {

        ClientHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START_BLE_SCAN:
                    handleStartScan((ScanClient) msg.obj);
                    break;
                case MSG_STOP_BLE_SCAN:
                    handleStopScan((ScanClient) msg.obj);
                    break;
                case MSG_FLUSH_BATCH_RESULTS:
                    handleFlushBatchResults((ScanClient) msg.obj);
                    break;
                case MSG_SCAN_TIMEOUT:
                    mScanNative.regularScanTimeout((ScanClient) msg.obj);
                    break;
                case MSG_SUSPEND_SCANS:
                    handleSuspendScans();
                    break;
                case MSG_RESUME_SCANS:
                    handleResumeScans();
                    break;
                case MSG_SCREEN_OFF:
                    handleScreenOff();
                    break;
                case MSG_SCREEN_ON:
                    handleScreenOn();
                    break;
                case MSG_REVERT_SCAN_MODE_UPGRADE:
                    revertScanModeUpgrade((ScanClient) msg.obj);
                    break;
                case MSG_IMPORTANCE_CHANGE:
                    handleImportanceChange((UidImportance) msg.obj);
                    break;
                case MSG_START_CONNECTING:
                    handleConnectingState();
                    break;
                case MSG_STOP_CONNECTING:
                    handleClearConnectingState();
                    break;
                case MSG_BT_PROFILE_CONN_STATE_CHANGED:
                    handleProfileConnectionStateChanged(msg);
                default:
                    // Shouldn't happen.
                    Log.e(TAG, "received an unkown message : " + msg.what);
            }
        }

        void handleStartScan(ScanClient client) {
            if (DBG) {
                Log.d(TAG, "handling starting scan");
            }
            fetchAppForegroundState(client);

            if (!isScanSupported(client)) {
                Log.e(TAG, "Scan settings not supported");
                return;
            }

            if (mRegularScanClients.contains(client) || mBatchClients.contains(client)) {
                Log.e(TAG, "Scan already started");
                return;
            }

            if (requiresScreenOn(client) && !mScreenOn) {
                Log.w(TAG, "Cannot start unfiltered scan in screen-off. This scan will be resumed "
                        + "later: " + client.scannerId);
                mSuspendedScanClients.add(client);
                if (client.stats != null) {
                    client.stats.recordScanSuspend(client.scannerId);
                }
                return;
            }

            final boolean locationEnabled = mLocationManager.isLocationEnabled();
            if (requiresLocationOn(client) && !locationEnabled) {
                Log.i(TAG, "Cannot start unfiltered scan in location-off. This scan will be"
                        + " resumed when location is on: " + client.scannerId);
                mSuspendedScanClients.add(client);
                if (client.stats != null) {
                    client.stats.recordScanSuspend(client.scannerId);
                }
                return;
            }

            if (!mScanNative.isExemptFromAutoBatchScanUpdate(client)) {
                if (mScreenOn) {
                    clearAutoBatchScanClient(client);
                } else {
                    setAutoBatchScanClient(client);
                }
            }

            // Begin scan operations.
            if (isBatchClient(client) || isAutoBatchScanClientEnabled(client)) {
                mBatchClients.add(client);
                mScanNative.startBatchScan(client);
            } else {
                updateScanModeBeforeStart(client);
                updateScanModeConcurrency(client);
                mRegularScanClients.add(client);
                mScanNative.startRegularScan(client);
                if (!mScanNative.isOpportunisticScanClient(client)) {
                    mScanNative.configureRegularScanParams();

                    if (!mScanNative.isExemptFromScanTimeout(client)) {
                        Message msg = obtainMessage(MSG_SCAN_TIMEOUT);
                        msg.obj = client;
                        // Only one timeout message should exist at any time
                        if (Flags.scanTimeoutReset()) {
                            removeMessages(MSG_SCAN_TIMEOUT, client);
                        }
                        sendMessageDelayed(msg, mAdapterService.getScanTimeoutMillis());
                        if (DBG) {
                            Log.d(TAG,
                                    "apply scan timeout (" + mAdapterService.getScanTimeoutMillis()
                                            + ")" + "to scannerId " + client.scannerId);
                        }
                    }
                }
            }
            client.started = true;
        }

        private boolean requiresScreenOn(ScanClient client) {
            boolean isFiltered = isFilteredScan(client);
            return !mScanNative.isOpportunisticScanClient(client) && !isFiltered;
        }

        private boolean requiresLocationOn(ScanClient client) {
            boolean isFiltered = isFilteredScan(client);
            return !client.hasDisavowedLocation && !isFiltered;
        }

        private boolean isFilteredScan(ScanClient client) {
            if ((client.filters == null) || client.filters.isEmpty()) {
                return false;
            }

            boolean atLeastOneValidFilter = false;
            for (ScanFilter filter : client.filters) {
                // A valid filter need at least one field not empty
                if (!filter.isAllFieldsEmpty()) {
                    atLeastOneValidFilter = true;
                    break;
                }
            }
            return atLeastOneValidFilter;
        }

        @RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
        void handleStopScan(ScanClient client) {
            if (client == null) {
                return;
            }
            if (DBG) {
                Log.d(TAG, "handling stopping scan " + client);
            }

            if (mSuspendedScanClients.contains(client)) {
                mSuspendedScanClients.remove(client);
            }
            removeMessages(MSG_REVERT_SCAN_MODE_UPGRADE, client);
            removeMessages(MSG_SCAN_TIMEOUT, client);
            if (mRegularScanClients.contains(client)) {
                mScanNative.stopRegularScan(client);

                if (!mScanNative.isOpportunisticScanClient(client)) {
                    mScanNative.configureRegularScanParams();
                }
            } else {
                if (isAutoBatchScanClientEnabled(client)) {
                    handleFlushBatchResults(client);
                }
                mScanNative.stopBatchScan(client);
            }
            if (client.appDied) {
                if (DBG) {
                    Log.d(TAG, "app died, unregister scanner - " + client.scannerId);
                }
                mService.unregisterScanner(client.scannerId, mService.getAttributionSource());
            }
        }

        void handleFlushBatchResults(ScanClient client) {
            if (DBG) {
                Log.d(TAG, "handleFlushBatchResults() " + client);
            }
            if (!mBatchClients.contains(client)) {
                if (DBG) {
                    Log.d(TAG, "There is no batch scan client to flush " + client);
                }
                return;
            }
            mScanNative.flushBatchResults(client.scannerId);
        }

        private boolean isBatchClient(ScanClient client) {
            if (client == null || client.settings == null) {
                return false;
            }
            ScanSettings settings = client.settings;
            return settings.getCallbackType() == ScanSettings.CALLBACK_TYPE_ALL_MATCHES
                    && settings.getReportDelayMillis() != 0;
        }

        private boolean isScanSupported(ScanClient client) {
            if (client == null || client.settings == null) {
                return true;
            }
            ScanSettings settings = client.settings;
            if (isFilteringSupported()) {
                return true;
            }
            return settings.getCallbackType() == ScanSettings.CALLBACK_TYPE_ALL_MATCHES
                    && settings.getReportDelayMillis() == 0;
        }

        void handleScreenOff() {
            AppScanStats.setScreenState(false);
            if (!mScreenOn) {
                return;
            }
            mScreenOn = false;
            if (DBG) {
                Log.d(TAG, "handleScreenOff()");
            }
            handleSuspendScans();
            updateRegularScanClientsScreenOff();
            updateRegularScanToBatchScanClients();
        }

        void handleConnectingState() {
            if (mAdapterService.getScanDowngradeDurationMillis() == 0) {
                return;
            }
            boolean updatedScanParams = false;
            mIsConnecting = true;
            if (DBG) {
                Log.d(TAG, "handleConnectingState()");
            }
            for (ScanClient client : mRegularScanClients) {
                if (downgradeScanModeFromMaxDuty(client)) {
                    updatedScanParams = true;
                    if (DBG) {
                        Log.d(TAG, "scanMode is downgraded by connecting for " + client);
                    }
                }
            }
            if (updatedScanParams) {
                mScanNative.configureRegularScanParams();
            }
            removeMessages(MSG_STOP_CONNECTING);
            Message msg = obtainMessage(MSG_STOP_CONNECTING);
            sendMessageDelayed(msg, mAdapterService.getScanDowngradeDurationMillis());
        }

        void handleClearConnectingState() {
            if (!mIsConnecting) {
                Log.e(TAG, "handleClearConnectingState() - not connecting state");
                return;
            }
            if (DBG) {
                Log.d(TAG, "handleClearConnectingState()");
            }
            boolean updatedScanParams = false;
            for (ScanClient client : mRegularScanClients) {
                if (revertDowngradeScanModeFromMaxDuty(client)) {
                    updatedScanParams = true;
                    if (DBG) {
                        Log.d(TAG, "downgraded scanMode is reverted for " + client);
                    }
                }
            }
            if (updatedScanParams) {
                mScanNative.configureRegularScanParams();
            }
            removeMessages(MSG_STOP_CONNECTING);
            mIsConnecting = false;
        }

        @RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
        void handleSuspendScans() {
            for (ScanClient client : mRegularScanClients) {
                if ((requiresScreenOn(client) && !mScreenOn)
                        || (requiresLocationOn(client) && !mLocationManager.isLocationEnabled())) {
                    /*Suspend unfiltered scans*/
                    if (client.stats != null) {
                        client.stats.recordScanSuspend(client.scannerId);
                    }
                    if (DBG) {
                        Log.d(TAG, "suspend scan " + client);
                    }
                    handleStopScan(client);
                    mSuspendedScanClients.add(client);
                }
            }
        }

        private void updateRegularScanToBatchScanClients() {
            boolean updatedScanParams = false;
            for (ScanClient client : mRegularScanClients) {
                if (!mScanNative.isExemptFromAutoBatchScanUpdate(client)) {
                    if (DBG) {
                        Log.d(TAG, "Updating regular scan to batch scan" + client);
                    }
                    handleStopScan(client);
                    setAutoBatchScanClient(client);
                    handleStartScan(client);
                    updatedScanParams = true;
                }
            }
            if (updatedScanParams) {
                mScanNative.configureRegularScanParams();
            }
        }

        private void updateBatchScanToRegularScanClients() {
            boolean updatedScanParams = false;
            for (ScanClient client : mBatchClients) {
                if (!mScanNative.isExemptFromAutoBatchScanUpdate(client)) {
                    if (DBG) {
                        Log.d(TAG, "Updating batch scan to regular scan" + client);
                    }
                    handleStopScan(client);
                    clearAutoBatchScanClient(client);
                    handleStartScan(client);
                    updatedScanParams = true;
                }
            }
            if (updatedScanParams) {
                mScanNative.configureRegularScanParams();
            }
        }

        private void setAutoBatchScanClient(ScanClient client) {
            if (isAutoBatchScanClientEnabled(client)) {
                return;
            }
            client.updateScanMode(ScanSettings.SCAN_MODE_SCREEN_OFF);
            if (client.stats != null) {
                client.stats.setAutoBatchScan(client.scannerId, true);
            }
        }

        private void clearAutoBatchScanClient(ScanClient client) {
            if (!isAutoBatchScanClientEnabled(client)) {
                return;
            }
            client.updateScanMode(client.scanModeApp);
            if (client.stats != null) {
                client.stats.setAutoBatchScan(client.scannerId, false);
            }
        }

        private void updateRegularScanClientsScreenOff() {
            boolean updatedScanParams = false;
            for (ScanClient client : mRegularScanClients) {
                if (updateScanModeScreenOff(client)) {
                    updatedScanParams = true;
                    if (DBG) {
                        Log.d(TAG, "Scan mode update during screen off" + client);
                    }
                }
            }
            if (updatedScanParams) {
                mScanNative.configureRegularScanParams();
            }
        }

        private boolean updateScanModeScreenOff(ScanClient client) {
            if (mScanNative.isOpportunisticScanClient(client)) {
                return false;
            }
            if (!isAppForeground(client) || mScanNative.isForceDowngradedScanClient(client)) {
                return client.updateScanMode(ScanSettings.SCAN_MODE_SCREEN_OFF);
            }

            // The following codes are effectively only for services
            // Apps are either already or will be soon handled by handleImportanceChange().
            switch (client.scanModeApp) {
                case ScanSettings.SCAN_MODE_LOW_POWER:
                    return client.updateScanMode(ScanSettings.SCAN_MODE_SCREEN_OFF);
                case ScanSettings.SCAN_MODE_BALANCED:
                case ScanSettings.SCAN_MODE_AMBIENT_DISCOVERY:
                    return client.updateScanMode(ScanSettings.SCAN_MODE_SCREEN_OFF_BALANCED);
                case ScanSettings.SCAN_MODE_LOW_LATENCY:
                    return client.updateScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
                case ScanSettings.SCAN_MODE_OPPORTUNISTIC:
                default:
                    return false;
            }
        }

        /**
         * Services and Apps are assumed to be in the foreground by default unless it changes to the
         * background triggering onUidImportance().
         */
        private boolean isAppForeground(ScanClient client) {
            return mIsUidForegroundMap.get(client.appUid, DEFAULT_UID_IS_FOREGROUND);
        }

        private void fetchAppForegroundState(ScanClient client) {
            PackageManager packageManager = mAdapterService.getPackageManager();
            if (mActivityManager == null || packageManager == null) {
                return;
            }
            String[] packages = packageManager.getPackagesForUid(client.appUid);
            if (packages == null || packages.length == 0) {
                return;
            }
            int importance = mActivityManager.getPackageImportance(packages[0]);
            boolean isForeground =
                    importance
                            <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
            mIsUidForegroundMap.put(client.appUid, isForeground);
        }

        private boolean updateScanModeBeforeStart(ScanClient client) {
            if (upgradeScanModeBeforeStart(client)) {
                return true;
            }
            if (mScreenOn) {
                return updateScanModeScreenOn(client);
            } else {
                return updateScanModeScreenOff(client);
            }
        }

        private boolean updateScanModeConcurrency(ScanClient client) {
            if (mIsConnecting) {
                return downgradeScanModeFromMaxDuty(client);
            }
            return false;
        }

        private boolean upgradeScanModeBeforeStart(ScanClient client) {
            if (client.started || mAdapterService.getScanUpgradeDurationMillis() == 0) {
                return false;
            }
            if (client.stats == null || client.stats.hasRecentScan()) {
                return false;
            }
            if (!isAppForeground(client) || isBatchClient(client)) {
                return false;
            }

            if (upgradeScanModeByOneLevel(client)) {
                Message msg = obtainMessage(MSG_REVERT_SCAN_MODE_UPGRADE);
                msg.obj = client;
                if (DBG) {
                    Log.d(TAG, "scanMode is upgraded for " + client);
                }
                sendMessageDelayed(msg, mAdapterService.getScanUpgradeDurationMillis());
                return true;
            }
            return false;
        }

        private boolean upgradeScanModeByOneLevel(ScanClient client) {
            switch (client.scanModeApp) {
                case ScanSettings.SCAN_MODE_LOW_POWER:
                    return client.updateScanMode(ScanSettings.SCAN_MODE_BALANCED);
                case ScanSettings.SCAN_MODE_BALANCED:
                case ScanSettings.SCAN_MODE_AMBIENT_DISCOVERY:
                    return client.updateScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
                case ScanSettings.SCAN_MODE_OPPORTUNISTIC:
                case ScanSettings.SCAN_MODE_LOW_LATENCY:
                default:
                    return false;
            }
        }

        void revertScanModeUpgrade(ScanClient client) {
            if (mPriorityMap.get(client.settings.getScanMode())
                    <= mPriorityMap.get(client.scanModeApp)) {
                return;
            }
            if (client.updateScanMode(client.scanModeApp)) {
                if (DBG) {
                    Log.d(TAG, "scanMode upgrade is reverted for " + client);
                }
                mScanNative.configureRegularScanParams();
            }
        }

        private boolean updateScanModeScreenOn(ScanClient client) {
            if (mScanNative.isOpportunisticScanClient(client)) {
                return false;
            }
            int scanMode =
                    isAppForeground(client) ? client.scanModeApp : SCAN_MODE_APP_IN_BACKGROUND;
            int maxScanMode =
                    mScanNative.isForceDowngradedScanClient(client)
                            ? SCAN_MODE_FORCE_DOWNGRADED
                            : scanMode;
            return client.updateScanMode(getMinScanMode(scanMode, maxScanMode));
        }

        private boolean downgradeScanModeFromMaxDuty(ScanClient client) {
            if ((client.stats == null) || mAdapterService.getScanDowngradeDurationMillis() == 0) {
                return false;
            }
            int scanMode = client.settings.getScanMode();
            int maxScanMode = SCAN_MODE_MAX_IN_CONCURRENCY;
            if (client.updateScanMode(getMinScanMode(scanMode, maxScanMode))) {
                client.stats.setScanDowngrade(client.scannerId, true);
                if (DBG) {
                    Log.d(TAG, "downgradeScanModeFromMaxDuty() for " + client);
                }
                return true;
            }
            return false;
        }

        private boolean revertDowngradeScanModeFromMaxDuty(ScanClient client) {
            if (!mScanNative.isDowngradedScanClient(client)) {
                return false;
            }
            client.stats.setScanDowngrade(client.scannerId, false);
            if (DBG) {
                Log.d(TAG, "revertDowngradeScanModeFromMaxDuty() for " + client);
            }
            if (mScreenOn) {
                return updateScanModeScreenOn(client);
            } else {
                return updateScanModeScreenOff(client);
            }
        }

        void handleScreenOn() {
            AppScanStats.setScreenState(true);
            if (mScreenOn) {
                return;
            }
            mScreenOn = true;
            if (DBG) {
                Log.d(TAG, "handleScreenOn()");
            }
            updateBatchScanToRegularScanClients();
            handleResumeScans();
            updateRegularScanClientsScreenOn();
        }

        void handleResumeScans() {
            Iterator<ScanClient> iterator = mSuspendedScanClients.iterator();
            while (iterator.hasNext()) {
                ScanClient client = iterator.next();
                if ((!requiresScreenOn(client) || mScreenOn)
                        && (!requiresLocationOn(client) || mLocationManager.isLocationEnabled())) {
                    if (client.stats != null) {
                        client.stats.recordScanResume(client.scannerId);
                    }
                    if (DBG) {
                        Log.d(TAG, "resume scan " + client);
                    }
                    handleStartScan(client);
                    iterator.remove();
                }
            }
        }

        private void updateRegularScanClientsScreenOn() {
            boolean updatedScanParams = false;
            for (ScanClient client : mRegularScanClients) {
                if (updateScanModeScreenOn(client)) {
                    updatedScanParams = true;
                }
            }
            if (updatedScanParams) {
                mScanNative.configureRegularScanParams();
            }
        }

        private void handleProfileConnectionStateChanged(Message msg) {
            int fromState = msg.arg1, toState = msg.arg2;
            int profile = ((Integer) msg.obj).intValue();
            boolean updatedConnectingState =
                    updateCountersAndCheckForConnectingState(toState, fromState);
            if (DBG) {
                Log.d(
                        TAG,
                        "PROFILE_CONNECTION_STATE_CHANGE:"
                                + (" profile=" + BluetoothProfile.getProfileName(profile))
                                + (" prevState=" + fromState)
                                + (" state=" + toState)
                                + (" updatedConnectingState = " + updatedConnectingState));
            }
            if (updatedConnectingState) {
                if (!mIsConnecting) {
                    handleConnectingState();
                }
            } else {
                if (mIsConnecting) {
                    handleClearConnectingState();
                }
            }
        }
    }

    /**
     * Parameters for batch scans.
     */
    class BatchScanParams {
        public int scanMode;
        public int fullScanscannerId;
        public int truncatedScanscannerId;

        BatchScanParams() {
            scanMode = -1;
            fullScanscannerId = -1;
            truncatedScanscannerId = -1;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            BatchScanParams other = (BatchScanParams) obj;
            return scanMode == other.scanMode && fullScanscannerId == other.fullScanscannerId
                    && truncatedScanscannerId == other.truncatedScanscannerId;

        }
    }

    public int getCurrentUsedTrackingAdvertisement() {
        synchronized (mCurUsedTrackableAdvertisementsLock) {
            return mCurUsedTrackableAdvertisements;
        }
    }

    private class ScanNative {

        // Delivery mode defined in bt stack.
        private static final int DELIVERY_MODE_IMMEDIATE = 0;
        private static final int DELIVERY_MODE_ON_FOUND_LOST = 1;
        private static final int DELIVERY_MODE_BATCH = 2;

        private static final int ONFOUND_SIGHTINGS_AGGRESSIVE = 1;
        private static final int ONFOUND_SIGHTINGS_STICKY = 4;

        private static final int ALL_PASS_FILTER_INDEX_REGULAR_SCAN = 1;
        private static final int ALL_PASS_FILTER_INDEX_BATCH_SCAN = 2;
        private static final int ALL_PASS_FILTER_SELECTION = 0;

        private static final int DISCARD_OLDEST_WHEN_BUFFER_FULL = 0;


        /**
         * Onfound/onlost for scan settings
         */
        private static final int MATCH_MODE_AGGRESSIVE_TIMEOUT_FACTOR = (1);
        private static final int MATCH_MODE_STICKY_TIMEOUT_FACTOR = (3);
        private static final int ONLOST_FACTOR = 2;
        private static final int ONLOST_ONFOUND_BASE_TIMEOUT_MS = 500;

        // The logic is AND for each filter field.
        private static final int LIST_LOGIC_TYPE = 0x1111111;
        private static final int FILTER_LOGIC_TYPE = 1;
        // Filter indices that are available to user. It's sad we need to maintain filter index.
        private final Deque<Integer> mFilterIndexStack;
        // Map of scannerId and Filter indices used by client.
        private final Map<Integer, Deque<Integer>> mClientFilterIndexMap;
        // Keep track of the clients that uses ALL_PASS filters.
        private final Set<Integer> mAllPassRegularClients = new HashSet<>();
        private final Set<Integer> mAllPassBatchClients = new HashSet<>();

        private AlarmManager mAlarmManager;
        private PendingIntent mBatchScanIntervalIntent;
        private ScanNativeInterface mNativeInterface;

        ScanNative() {
            mNativeInterface = GattObjectsFactory.getInstance().getScanNativeInterface();
            mFilterIndexStack = new ArrayDeque<Integer>();
            mClientFilterIndexMap = new HashMap<Integer, Deque<Integer>>();

            mAlarmManager = mService.getSystemService(AlarmManager.class);
            Intent batchIntent = new Intent(ACTION_REFRESH_BATCHED_SCAN, null);
            mBatchScanIntervalIntent = PendingIntent.getBroadcast(mService, 0, batchIntent,
                    PendingIntent.FLAG_IMMUTABLE);
            IntentFilter filter = new IntentFilter();
            filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
            filter.addAction(ACTION_REFRESH_BATCHED_SCAN);
            mBatchAlarmReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, "awakened up at time " + SystemClock.elapsedRealtime());
                    String action = intent.getAction();

                    if (action.equals(ACTION_REFRESH_BATCHED_SCAN)) {
                        if (mBatchClients.isEmpty()) {
                            return;
                        }
                        // Note this actually flushes all pending batch data.
                        if (mBatchClients.iterator().hasNext()) {
                            flushBatchScanResults(mBatchClients.iterator().next());
                        }
                    }
                }
            };
            mService.registerReceiver(mBatchAlarmReceiver, filter);
            mBatchAlarmReceiverRegistered = true;
        }

        private void callbackDone(int scannerId, int status) {
            if (DBG) {
                Log.d(TAG, "callback done for scannerId - " + scannerId + " status - " + status);
            }
            if (status == 0) {
                mNativeInterface.callbackDone();
            }
            // TODO: add a callback for scan failure.
        }

        private void resetCountDownLatch() {
            mNativeInterface.resetCountDownLatch();
        }

        private boolean waitForCallback() {
            return mNativeInterface.waitForCallback(OPERATION_TIME_OUT_MILLIS);
        }

        void configureRegularScanParams() {
            if (DBG) {
                Log.d(TAG, "configureRegularScanParams() - queue=" + mRegularScanClients.size());
            }
            int curScanSetting = Integer.MIN_VALUE;
            ScanClient client = getAggressiveClient(mRegularScanClients);
            if (client != null) {
                curScanSetting = client.settings.getScanMode();
            }

            if (curScanSetting != Integer.MIN_VALUE
                    && curScanSetting != ScanSettings.SCAN_MODE_OPPORTUNISTIC) {
                if (curScanSetting != mLastConfiguredScanSetting) {
                    int scanWindowMs = getScanWindowMillis(client.settings);
                    int scanIntervalMs = getScanIntervalMillis(client.settings);

                    // convert scanWindow and scanInterval from ms to LE scan units(0.625ms)
                    int scanWindow = Utils.millsToUnit(scanWindowMs);
                    int scanInterval = Utils.millsToUnit(scanIntervalMs);
                    mNativeInterface.gattClientScan(false);
                    if (!AppScanStats.recordScanRadioStop()) {
                        Log.w(TAG, "There is no scan radio to stop");
                    }
                    if (DBG) {
                        Log.d(TAG, "Start gattClientScanNative with"
                                + " old scanMode " + mLastConfiguredScanSetting
                                + " new scanMode " + curScanSetting
                                + " ( in MS: " + scanIntervalMs + " / " + scanWindowMs
                                + ", in scan unit: " + scanInterval + " / " + scanWindow + " )"
                                + client);
                    }
                    mNativeInterface.gattSetScanParameters(client.scannerId, scanInterval,
                            scanWindow);
                    mNativeInterface.gattClientScan(true);
                    if (!AppScanStats.recordScanRadioStart(curScanSetting)) {
                        Log.w(TAG, "Scan radio already started");
                    }
                    mLastConfiguredScanSetting = curScanSetting;
                }
            } else {
                mLastConfiguredScanSetting = curScanSetting;
                if (DBG) {
                    Log.d(TAG, "configureRegularScanParams() - queue empty, scan stopped");
                }
            }
        }

        ScanClient getAggressiveClient(Set<ScanClient> cList) {
            ScanClient result = null;
            int currentScanModePriority = Integer.MIN_VALUE;
            for (ScanClient client : cList) {
                int priority = mPriorityMap.get(client.settings.getScanMode());
                if (priority > currentScanModePriority) {
                    result = client;
                    currentScanModePriority = priority;
                }
            }
            return result;
        }

        void startRegularScan(ScanClient client) {
            if (isFilteringSupported() && mFilterIndexStack.isEmpty()
                    && mClientFilterIndexMap.isEmpty()) {
                initFilterIndexStack();
            }
            if (isFilteringSupported()) {
                configureScanFilters(client);
            }
            // Start scan native only for the first client.
            if (numRegularScanClients() == 1
                    && client.settings != null
                    && client.settings.getScanMode() != ScanSettings.SCAN_MODE_OPPORTUNISTIC) {
                if (DBG) {
                    Log.d(TAG, "start gattClientScanNative from startRegularScan()");
                }
                mNativeInterface.gattClientScan(true);
                if (!AppScanStats.recordScanRadioStart(client.settings.getScanMode())) {
                    Log.w(TAG, "Scan radio already started");
                }
            }
        }

        private int numRegularScanClients() {
            int num = 0;
            for (ScanClient client : mRegularScanClients) {
                if (client.settings.getScanMode() != ScanSettings.SCAN_MODE_OPPORTUNISTIC) {
                    num++;
                }
            }
            return num;
        }

        void startBatchScan(ScanClient client) {
            if (mFilterIndexStack.isEmpty() && isFilteringSupported()) {
                initFilterIndexStack();
            }
            configureScanFilters(client);
            if (!isOpportunisticScanClient(client)) {
                // Reset batch scan. May need to stop the existing batch scan and update scan
                // params.
                resetBatchScan(client);
            }
        }

        private boolean isExemptFromScanTimeout(ScanClient client) {
            return isOpportunisticScanClient(client) || isFirstMatchScanClient(client);
        }

        private boolean isExemptFromAutoBatchScanUpdate(ScanClient client) {
            return isOpportunisticScanClient(client) || !isAllMatchesAutoBatchScanClient(client);
        }

        private boolean isAutoBatchScanClientEnabled(ScanClient client) {
            return client.stats != null && client.stats.isAutoBatchScan(client.scannerId);
        }

        private boolean isAllMatchesAutoBatchScanClient(ScanClient client) {
            return client.settings.getCallbackType()
                    == ScanSettings.CALLBACK_TYPE_ALL_MATCHES_AUTO_BATCH;
        }

        private boolean isOpportunisticScanClient(ScanClient client) {
            return client.settings.getScanMode() == ScanSettings.SCAN_MODE_OPPORTUNISTIC;
        }

        private boolean isTimeoutScanClient(ScanClient client) {
            return (client.stats != null) && client.stats.isScanTimeout(client.scannerId);
        }

        private boolean isDowngradedScanClient(ScanClient client) {
            return (client.stats != null) && client.stats.isScanDowngraded(client.scannerId);
        }

        private boolean isForceDowngradedScanClient(ScanClient client) {
            return isTimeoutScanClient(client) || isDowngradedScanClient(client);
        }

        private boolean isFirstMatchScanClient(ScanClient client) {
            return (client.settings.getCallbackType() & ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                    != 0;
        }

        private void resetBatchScan(ScanClient client) {
            int scannerId = client.scannerId;
            BatchScanParams batchScanParams = getBatchScanParams();
            // Stop batch if batch scan params changed and previous params is not null.
            if (mBatchScanParms != null && (!mBatchScanParms.equals(batchScanParams))) {
                if (DBG) {
                    Log.d(TAG, "stopping BLe Batch");
                }
                resetCountDownLatch();
                mNativeInterface.gattClientStopBatchScan(scannerId);
                waitForCallback();
                // Clear pending results as it's illegal to config storage if there are still
                // pending results.
                flushBatchResults(scannerId);
            }
            // Start batch if batchScanParams changed and current params is not null.
            if (batchScanParams != null && (!batchScanParams.equals(mBatchScanParms))) {
                int notifyThreshold = 95;
                if (DBG) {
                    Log.d(TAG, "Starting BLE batch scan");
                }
                int resultType = getResultType(batchScanParams);
                int fullScanPercent = getFullScanStoragePercent(resultType);
                resetCountDownLatch();
                if (DBG) {
                    Log.d(TAG, "configuring batch scan storage, appIf " + client.scannerId);
                }
                mNativeInterface.gattClientConfigBatchScanStorage(client.scannerId, fullScanPercent,
                        100 - fullScanPercent, notifyThreshold);
                waitForCallback();
                resetCountDownLatch();
                int scanInterval =
                        Utils.millsToUnit(getBatchScanIntervalMillis(batchScanParams.scanMode));
                int scanWindow =
                        Utils.millsToUnit(getBatchScanWindowMillis(batchScanParams.scanMode));
                mNativeInterface.gattClientStartBatchScan(scannerId, resultType, scanInterval,
                        scanWindow, 0, DISCARD_OLDEST_WHEN_BUFFER_FULL);
                waitForCallback();
            }
            mBatchScanParms = batchScanParams;
            setBatchAlarm();
        }

        private int getFullScanStoragePercent(int resultType) {
            switch (resultType) {
                case SCAN_RESULT_TYPE_FULL:
                    return 100;
                case SCAN_RESULT_TYPE_TRUNCATED:
                    return 0;
                case SCAN_RESULT_TYPE_BOTH:
                    return 50;
                default:
                    return 50;
            }
        }

        private BatchScanParams getBatchScanParams() {
            if (mBatchClients.isEmpty()) {
                return null;
            }
            BatchScanParams params = new BatchScanParams();
            ScanClient winner = getAggressiveClient(mBatchClients);
            if (winner != null) {
                params.scanMode = winner.settings.getScanMode();
            }
            // TODO: split full batch scan results and truncated batch scan results to different
            // collections.
            for (ScanClient client : mBatchClients) {
                if (client.settings.getScanResultType() == ScanSettings.SCAN_RESULT_TYPE_FULL) {
                    params.fullScanscannerId = client.scannerId;
                } else {
                    params.truncatedScanscannerId = client.scannerId;
                }
            }
            return params;
        }

        // Batched scan doesn't require high duty cycle scan because scan result is reported
        // infrequently anyway. To avoid redefining paramete sets, map to the low duty cycle
        // parameter set as follows.
        private int getBatchScanWindowMillis(int scanMode) {
            ContentResolver resolver = mService.getContentResolver();
            switch (scanMode) {
                case ScanSettings.SCAN_MODE_LOW_LATENCY:
                    return Settings.Global.getInt(
                        resolver,
                        Settings.Global.BLE_SCAN_BALANCED_WINDOW_MS,
                        SCAN_MODE_BALANCED_WINDOW_MS);
                case ScanSettings.SCAN_MODE_SCREEN_OFF:
                    return mAdapterService.getScreenOffLowPowerWindowMillis();
                default:
                    return Settings.Global.getInt(
                        resolver,
                        Settings.Global.BLE_SCAN_LOW_POWER_WINDOW_MS,
                        SCAN_MODE_LOW_POWER_WINDOW_MS);
            }
        }

        private int getBatchScanIntervalMillis(int scanMode) {
            ContentResolver resolver = mService.getContentResolver();
            switch (scanMode) {
                case ScanSettings.SCAN_MODE_LOW_LATENCY:
                    return Settings.Global.getInt(
                        resolver,
                        Settings.Global.BLE_SCAN_BALANCED_INTERVAL_MS,
                        SCAN_MODE_BALANCED_INTERVAL_MS);
                case ScanSettings.SCAN_MODE_SCREEN_OFF:
                    return mAdapterService.getScreenOffLowPowerIntervalMillis();
                default:
                    return Settings.Global.getInt(
                        resolver,
                        Settings.Global.BLE_SCAN_LOW_POWER_INTERVAL_MS,
                        SCAN_MODE_LOW_POWER_INTERVAL_MS);
            }
        }

        // Set the batch alarm to be triggered within a short window after batch interval. This
        // allows system to optimize wake up time while still allows a degree of precise control.
        private void setBatchAlarm() {
            // Cancel any pending alarm just in case.
            mAlarmManager.cancel(mBatchScanIntervalIntent);
            if (mBatchClients.isEmpty()) {
                return;
            }
            long batchTriggerIntervalMillis = getBatchTriggerIntervalMillis();
            // Allows the alarm to be triggered within
            // [batchTriggerIntervalMillis, 1.1 * batchTriggerIntervalMillis]
            long windowLengthMillis = batchTriggerIntervalMillis / 10;
            long windowStartMillis = SystemClock.elapsedRealtime() + batchTriggerIntervalMillis;
            mAlarmManager.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, windowStartMillis,
                    windowLengthMillis, mBatchScanIntervalIntent);
        }

        void stopRegularScan(ScanClient client) {
            // Remove scan filters and recycle filter indices.
            if (client == null) {
                return;
            }
            int deliveryMode = getDeliveryMode(client);
            if (deliveryMode == DELIVERY_MODE_ON_FOUND_LOST) {
                for (ScanFilter filter : client.filters) {
                    int entriesToFree = getNumOfTrackingAdvertisements(client.settings);
                    if (!manageAllocationOfTrackingAdvertisement(entriesToFree, false)) {
                        Log.e(TAG, "Error freeing for onfound/onlost filter resources "
                                + entriesToFree);
                        try {
                            mService.onScanManagerErrorCallback(client.scannerId,
                                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR);
                        } catch (RemoteException e) {
                            Log.e(TAG, "failed on onScanManagerCallback at freeing", e);
                        }
                    }
                }
            }
            mRegularScanClients.remove(client);
            if (numRegularScanClients() == 0) {
                if (DBG) {
                    Log.d(TAG, "stop gattClientScanNative");
                }
                mNativeInterface.gattClientScan(false);
                if (!AppScanStats.recordScanRadioStop()) {
                    Log.w(TAG, "There is no scan radio to stop");
                }
            }
            removeScanFilters(client.scannerId);
        }

        void regularScanTimeout(ScanClient client) {
            if (!isExemptFromScanTimeout(client) && client.stats.isScanningTooLong()) {
                if (DBG) {
                    Log.d(TAG, "regularScanTimeout - client scan time was too long");
                }
                if (client.filters == null || client.filters.isEmpty()) {
                    Log.w(TAG,
                            "Moving unfiltered scan client to opportunistic scan (scannerId "
                                    + client.scannerId + ")");
                    setOpportunisticScanClient(client);
                    removeScanFilters(client.scannerId);

                } else {
                    Log.w(
                            TAG,
                            "Moving filtered scan client to downgraded scan (scannerId "
                                    + client.scannerId
                                    + ")");
                    int scanMode = client.settings.getScanMode();
                    int maxScanMode = SCAN_MODE_FORCE_DOWNGRADED;
                    client.updateScanMode(getMinScanMode(scanMode, maxScanMode));
                }
                client.stats.setScanTimeout(client.scannerId);
                client.stats.recordScanTimeoutCountMetrics();
            }

            // The scan should continue for background scans
            configureRegularScanParams();
            if (numRegularScanClients() == 0) {
                if (DBG) {
                    Log.d(TAG, "stop gattClientScanNative");
                }
                mNativeInterface.gattClientScan(false);
                if (!AppScanStats.recordScanRadioStop()) {
                    Log.w(TAG, "There is no scan radio to stop");
                }
            }
        }

        void setOpportunisticScanClient(ScanClient client) {
            // TODO: Add constructor to ScanSettings.Builder
            // that can copy values from an existing ScanSettings object
            ScanSettings.Builder builder = new ScanSettings.Builder();
            ScanSettings settings = client.settings;
            builder.setScanMode(ScanSettings.SCAN_MODE_OPPORTUNISTIC);
            builder.setCallbackType(settings.getCallbackType());
            builder.setScanResultType(settings.getScanResultType());
            builder.setReportDelay(settings.getReportDelayMillis());
            builder.setNumOfMatches(settings.getNumOfMatches());
            client.settings = builder.build();
        }

        // Find the regular scan client information.
        ScanClient getRegularScanClient(int scannerId) {
            for (ScanClient client : mRegularScanClients) {
                if (client.scannerId == scannerId) {
                    return client;
                }
            }
            return null;
        }

        ScanClient getSuspendedScanClient(int scannerId) {
            for (ScanClient client : mSuspendedScanClients) {
                if (client.scannerId == scannerId) {
                    return client;
                }
            }
            return null;
        }

        void stopBatchScan(ScanClient client) {
            mBatchClients.remove(client);
            removeScanFilters(client.scannerId);
            if (!isOpportunisticScanClient(client)) {
                resetBatchScan(client);
            }
        }

        void flushBatchResults(int scannerId) {
            if (DBG) {
                Log.d(TAG, "flushPendingBatchResults - scannerId = " + scannerId);
            }
            if (mBatchScanParms.fullScanscannerId != -1) {
                resetCountDownLatch();
                mNativeInterface.gattClientReadScanReports(mBatchScanParms.fullScanscannerId,
                        SCAN_RESULT_TYPE_FULL);
                waitForCallback();
            }
            if (mBatchScanParms.truncatedScanscannerId != -1) {
                resetCountDownLatch();
                mNativeInterface.gattClientReadScanReports(mBatchScanParms.truncatedScanscannerId,
                        SCAN_RESULT_TYPE_TRUNCATED);
                waitForCallback();
            }
            setBatchAlarm();
        }

        void cleanup() {
            mAlarmManager.cancel(mBatchScanIntervalIntent);
            // Protect against multiple calls of cleanup.
            if (mBatchAlarmReceiverRegistered) {
                mService.unregisterReceiver(mBatchAlarmReceiver);
            }
            mBatchAlarmReceiverRegistered = false;
        }

        private long getBatchTriggerIntervalMillis() {
            long intervalMillis = Long.MAX_VALUE;
            for (ScanClient client : mBatchClients) {
                if (client.settings != null && client.settings.getReportDelayMillis() > 0) {
                    intervalMillis =
                            Math.min(intervalMillis, client.settings.getReportDelayMillis());
                }
            }
            return intervalMillis;
        }

        // Add scan filters. The logic is:
        // If no offload filter can/needs to be set, set ALL_PASS filter.
        // Otherwise offload all filters to hardware and enable all filters.
        private void configureScanFilters(ScanClient client) {
            int scannerId = client.scannerId;
            int deliveryMode = getDeliveryMode(client);
            int trackEntries = 0;

            // Do not add any filters set by opportunistic scan clients
            if (isOpportunisticScanClient(client)) {
                return;
            }

            if (!shouldAddAllPassFilterToController(client, deliveryMode)) {
                return;
            }

            resetCountDownLatch();
            mNativeInterface.gattClientScanFilterEnable(scannerId, true);
            waitForCallback();

            if (shouldUseAllPassFilter(client)) {
                int filterIndex =
                        (deliveryMode == DELIVERY_MODE_BATCH) ? ALL_PASS_FILTER_INDEX_BATCH_SCAN
                                : ALL_PASS_FILTER_INDEX_REGULAR_SCAN;
                resetCountDownLatch();
                // Don't allow Onfound/onlost with all pass
                configureFilterParamter(scannerId, client, ALL_PASS_FILTER_SELECTION, filterIndex,
                        0);
                waitForCallback();
            } else {
                Deque<Integer> clientFilterIndices = new ArrayDeque<Integer>();
                for (ScanFilter filter : client.filters) {
                    ScanFilterQueue queue = new ScanFilterQueue();
                    queue.addScanFilter(filter);
                    int featureSelection = queue.getFeatureSelection();
                    int filterIndex = mFilterIndexStack.pop();

                    resetCountDownLatch();
                    mNativeInterface.gattClientScanFilterAdd(scannerId, queue.toArray(),
                            filterIndex);
                    waitForCallback();

                    resetCountDownLatch();
                    if (deliveryMode == DELIVERY_MODE_ON_FOUND_LOST) {
                        trackEntries = getNumOfTrackingAdvertisements(client.settings);
                        if (!manageAllocationOfTrackingAdvertisement(trackEntries, true)) {
                            Log.e(TAG, "No hardware resources for onfound/onlost filter "
                                    + trackEntries);
                            client.stats.recordTrackingHwFilterNotAvailableCountMetrics();
                            try {
                                mService.onScanManagerErrorCallback(scannerId,
                                        ScanCallback.SCAN_FAILED_INTERNAL_ERROR);
                            } catch (RemoteException e) {
                                Log.e(TAG, "failed on onScanManagerCallback", e);
                            }
                        }
                    }
                    configureFilterParamter(scannerId, client, featureSelection, filterIndex,
                            trackEntries);
                    waitForCallback();
                    clientFilterIndices.add(filterIndex);
                }
                mClientFilterIndexMap.put(scannerId, clientFilterIndices);
            }
        }

        // Check whether the filter should be added to controller.
        // Note only on ALL_PASS filter should be added.
        private boolean shouldAddAllPassFilterToController(ScanClient client, int deliveryMode) {
            // Not an ALL_PASS client, need to add filter.
            if (!shouldUseAllPassFilter(client)) {
                return true;
            }

            if (deliveryMode == DELIVERY_MODE_BATCH) {
                mAllPassBatchClients.add(client.scannerId);
                return mAllPassBatchClients.size() == 1;
            } else {
                mAllPassRegularClients.add(client.scannerId);
                return mAllPassRegularClients.size() == 1;
            }
        }

        private void removeScanFilters(int scannerId) {
            Deque<Integer> filterIndices = mClientFilterIndexMap.remove(scannerId);
            if (filterIndices != null) {
                mFilterIndexStack.addAll(filterIndices);
                for (Integer filterIndex : filterIndices) {
                    resetCountDownLatch();
                    mNativeInterface.gattClientScanFilterParamDelete(scannerId, filterIndex);
                    waitForCallback();
                }
            }
            // Remove if ALL_PASS filters are used.
            removeFilterIfExisits(mAllPassRegularClients, scannerId,
                    ALL_PASS_FILTER_INDEX_REGULAR_SCAN);
            removeFilterIfExisits(mAllPassBatchClients, scannerId,
                    ALL_PASS_FILTER_INDEX_BATCH_SCAN);
        }

        private void removeFilterIfExisits(Set<Integer> clients, int scannerId, int filterIndex) {
            if (!clients.contains(scannerId)) {
                return;
            }
            clients.remove(scannerId);
            // Remove ALL_PASS filter iff no app is using it.
            if (clients.isEmpty()) {
                resetCountDownLatch();
                mNativeInterface.gattClientScanFilterParamDelete(scannerId, filterIndex);
                waitForCallback();
            }
        }

        private ScanClient getBatchScanClient(int scannerId) {
            for (ScanClient client : mBatchClients) {
                if (client.scannerId == scannerId) {
                    return client;
                }
            }
            return null;
        }

        /**
         * Return batch scan result type value defined in bt stack.
         */
        private int getResultType(BatchScanParams params) {
            if (params.fullScanscannerId != -1 && params.truncatedScanscannerId != -1) {
                return SCAN_RESULT_TYPE_BOTH;
            }
            if (params.truncatedScanscannerId != -1) {
                return SCAN_RESULT_TYPE_TRUNCATED;
            }
            if (params.fullScanscannerId != -1) {
                return SCAN_RESULT_TYPE_FULL;
            }
            return -1;
        }

        // Check if ALL_PASS filter should be used for the client.
        private boolean shouldUseAllPassFilter(ScanClient client) {
            if (client == null) {
                return true;
            }
            if (client.filters == null || client.filters.isEmpty()) {
                return true;
            }
            if (client.filters.size() > mFilterIndexStack.size()) {
                client.stats.recordHwFilterNotAvailableCountMetrics();
                return true;
            }
            return false;
        }

        private void initFilterIndexStack() {
            int maxFiltersSupported =
                    AdapterService.getAdapterService().getNumOfOffloadedScanFilterSupported();
            // Start from index 4 as:
            // index 0 is reserved for ALL_PASS filter in Settings app.
            // index 1 is reserved for ALL_PASS filter for regular scan apps.
            // index 2 is reserved for ALL_PASS filter for batch scan apps.
            // index 3 is reserved for BAP/CAP Announcements
            for (int i = 4; i < maxFiltersSupported; ++i) {
                mFilterIndexStack.add(i);
            }
        }

        // Configure filter parameters.
        private void configureFilterParamter(int scannerId, ScanClient client, int featureSelection,
                int filterIndex, int numOfTrackingEntries) {
            int deliveryMode = getDeliveryMode(client);
            int rssiThreshold = Byte.MIN_VALUE;
            ScanSettings settings = client.settings;
            int onFoundTimeout = getOnFoundOnLostTimeoutMillis(settings, true);
            int onLostTimeout = getOnFoundOnLostTimeoutMillis(settings, false);
            int onFoundCount = getOnFoundOnLostSightings(settings);
            onLostTimeout = 10000;
            if (DBG) {
                Log.d(TAG, "configureFilterParamter " + onFoundTimeout + " " + onLostTimeout + " "
                        + onFoundCount + " " + numOfTrackingEntries);
            }
            FilterParams filtValue =
                    new FilterParams(scannerId, filterIndex, featureSelection, LIST_LOGIC_TYPE,
                            FILTER_LOGIC_TYPE, rssiThreshold, rssiThreshold, deliveryMode,
                            onFoundTimeout, onLostTimeout, onFoundCount, numOfTrackingEntries);
            mNativeInterface.gattClientScanFilterParamAdd(filtValue);
        }

        // Get delivery mode based on scan settings.
        private int getDeliveryMode(ScanClient client) {
            if (client == null) {
                return DELIVERY_MODE_IMMEDIATE;
            }
            ScanSettings settings = client.settings;
            if (settings == null) {
                return DELIVERY_MODE_IMMEDIATE;
            }
            if ((settings.getCallbackType() & ScanSettings.CALLBACK_TYPE_FIRST_MATCH) != 0
                    || (settings.getCallbackType() & ScanSettings.CALLBACK_TYPE_MATCH_LOST) != 0) {
                return DELIVERY_MODE_ON_FOUND_LOST;
            }
            if (isAllMatchesAutoBatchScanClient(client)) {
                return isAutoBatchScanClientEnabled(client) ? DELIVERY_MODE_BATCH
                        : DELIVERY_MODE_IMMEDIATE;
            }
            return settings.getReportDelayMillis() == 0 ? DELIVERY_MODE_IMMEDIATE
                    : DELIVERY_MODE_BATCH;
        }

        private int getScanWindowMillis(ScanSettings settings) {
            ContentResolver resolver = mService.getContentResolver();
            if (settings == null) {
                return Settings.Global.getInt(
                    resolver,
                    Settings.Global.BLE_SCAN_LOW_POWER_WINDOW_MS,
                    SCAN_MODE_LOW_POWER_WINDOW_MS);
            }

            switch (settings.getScanMode()) {
                case ScanSettings.SCAN_MODE_LOW_LATENCY:
                    return Settings.Global.getInt(
                        resolver,
                        Settings.Global.BLE_SCAN_LOW_LATENCY_WINDOW_MS,
                        SCAN_MODE_LOW_LATENCY_WINDOW_MS);
                case ScanSettings.SCAN_MODE_BALANCED:
                case ScanSettings.SCAN_MODE_AMBIENT_DISCOVERY:
                    return Settings.Global.getInt(
                        resolver,
                        Settings.Global.BLE_SCAN_BALANCED_WINDOW_MS,
                        SCAN_MODE_BALANCED_WINDOW_MS);
                case ScanSettings.SCAN_MODE_LOW_POWER:
                    return Settings.Global.getInt(
                        resolver,
                        Settings.Global.BLE_SCAN_LOW_POWER_WINDOW_MS,
                        SCAN_MODE_LOW_POWER_WINDOW_MS);
                case ScanSettings.SCAN_MODE_SCREEN_OFF:
                    return mAdapterService.getScreenOffLowPowerWindowMillis();
                case ScanSettings.SCAN_MODE_SCREEN_OFF_BALANCED:
                    return mAdapterService.getScreenOffBalancedWindowMillis();
                default:
                    return Settings.Global.getInt(
                        resolver,
                        Settings.Global.BLE_SCAN_LOW_POWER_WINDOW_MS,
                        SCAN_MODE_LOW_POWER_WINDOW_MS);
            }
        }

        private int getScanIntervalMillis(ScanSettings settings) {
            ContentResolver resolver = mService.getContentResolver();
            if (settings == null) {
                return Settings.Global.getInt(
                    resolver,
                    Settings.Global.BLE_SCAN_LOW_POWER_INTERVAL_MS,
                    SCAN_MODE_LOW_POWER_INTERVAL_MS);
            }
            switch (settings.getScanMode()) {
                case ScanSettings.SCAN_MODE_LOW_LATENCY:
                    return Settings.Global.getInt(
                        resolver,
                        Settings.Global.BLE_SCAN_LOW_LATENCY_INTERVAL_MS,
                        SCAN_MODE_LOW_LATENCY_INTERVAL_MS);
                case ScanSettings.SCAN_MODE_BALANCED:
                case ScanSettings.SCAN_MODE_AMBIENT_DISCOVERY:
                    return Settings.Global.getInt(
                        resolver,
                        Settings.Global.BLE_SCAN_BALANCED_INTERVAL_MS,
                        SCAN_MODE_BALANCED_INTERVAL_MS);
                case ScanSettings.SCAN_MODE_LOW_POWER:
                    return Settings.Global.getInt(
                        resolver,
                        Settings.Global.BLE_SCAN_LOW_POWER_INTERVAL_MS,
                        SCAN_MODE_LOW_POWER_INTERVAL_MS);
                case ScanSettings.SCAN_MODE_SCREEN_OFF:
                    return mAdapterService.getScreenOffLowPowerIntervalMillis();
                case ScanSettings.SCAN_MODE_SCREEN_OFF_BALANCED:
                    return mAdapterService.getScreenOffBalancedIntervalMillis();
                default:
                    return Settings.Global.getInt(
                        resolver,
                        Settings.Global.BLE_SCAN_LOW_POWER_INTERVAL_MS,
                        SCAN_MODE_LOW_POWER_INTERVAL_MS);
            }
        }

        private int getOnFoundOnLostTimeoutMillis(ScanSettings settings, boolean onFound) {
            int factor;
            int timeout = ONLOST_ONFOUND_BASE_TIMEOUT_MS;

            if (settings.getMatchMode() == ScanSettings.MATCH_MODE_AGGRESSIVE) {
                factor = MATCH_MODE_AGGRESSIVE_TIMEOUT_FACTOR;
            } else {
                factor = MATCH_MODE_STICKY_TIMEOUT_FACTOR;
            }
            if (!onFound) {
                factor = factor * ONLOST_FACTOR;
            }
            return (timeout * factor);
        }

        private int getOnFoundOnLostSightings(ScanSettings settings) {
            if (settings == null) {
                return ONFOUND_SIGHTINGS_AGGRESSIVE;
            }
            if (settings.getMatchMode() == ScanSettings.MATCH_MODE_AGGRESSIVE) {
                return ONFOUND_SIGHTINGS_AGGRESSIVE;
            } else {
                return ONFOUND_SIGHTINGS_STICKY;
            }
        }

        private int getNumOfTrackingAdvertisements(ScanSettings settings) {
            if (settings == null) {
                return 0;
            }
            int val = 0;
            int maxTotalTrackableAdvertisements =
                    AdapterService.getAdapterService().getTotalNumOfTrackableAdvertisements();
            // controller based onfound onlost resources are scarce commodity; the
            // assignment of filters to num of beacons to track is configurable based
            // on hw capabilities. Apps give an intent and allocation of onfound
            // resources or failure there of is done based on availability - FCFS model
            switch (settings.getNumOfMatches()) {
                case ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT:
                    val = 1;
                    break;
                case ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT:
                    val = 2;
                    break;
                case ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT:
                    val = maxTotalTrackableAdvertisements / 2;
                    break;
                default:
                    val = 1;
                    if (DBG) {
                        Log.d(TAG, "Invalid setting for getNumOfMatches() "
                                + settings.getNumOfMatches());
                    }
            }
            return val;
        }

        private boolean manageAllocationOfTrackingAdvertisement(int numOfTrackableAdvertisement,
                boolean allocate) {
            int maxTotalTrackableAdvertisements =
                    AdapterService.getAdapterService().getTotalNumOfTrackableAdvertisements();
            synchronized (mCurUsedTrackableAdvertisementsLock) {
                int availableEntries =
                        maxTotalTrackableAdvertisements - mCurUsedTrackableAdvertisements;
                if (allocate) {
                    if (availableEntries >= numOfTrackableAdvertisement) {
                        mCurUsedTrackableAdvertisements += numOfTrackableAdvertisement;
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    if (numOfTrackableAdvertisement > mCurUsedTrackableAdvertisements) {
                        return false;
                    } else {
                        mCurUsedTrackableAdvertisements -= numOfTrackableAdvertisement;
                        return true;
                    }
                }
            }
        }

        private void registerScanner(long appUuidLsb, long appUuidMsb) {
            mNativeInterface.registerScanner(appUuidLsb, appUuidMsb);
        }

        private void unregisterScanner(int scannerId) {
            mNativeInterface.unregisterScanner(scannerId);
        }
    }

    @VisibleForTesting
    ClientHandler getClientHandler() {
        return mHandler;
    }

    @VisibleForTesting
    BatchScanParams getBatchScanParams() {
        return mBatchScanParms;
    }

    private boolean isScreenOn() {
        Display[] displays = mDm.getDisplays();

        if (displays == null) {
            return false;
        }

        for (Display display : displays) {
            if (display.getState() == Display.STATE_ON) {
                return true;
            }
        }

        return false;
    }

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {}

                @Override
                public void onDisplayRemoved(int displayId) {}

                @Override
                public void onDisplayChanged(int displayId) {
                    if (isScreenOn()) {
                        sendMessage(MSG_SCREEN_ON, null);
                    } else {
                        sendMessage(MSG_SCREEN_OFF, null);
                    }
                }
            };

    private ActivityManager.OnUidImportanceListener mUidImportanceListener =
            new ActivityManager.OnUidImportanceListener() {
                @Override
                public void onUidImportance(final int uid, final int importance) {
                    if (mService.mTransitionalScanHelper.getScannerMap().getAppScanStatsByUid(uid)
                            != null) {
                        Message message = new Message();
                        message.what = MSG_IMPORTANCE_CHANGE;
                        message.obj = new UidImportance(uid, importance);
                        mHandler.sendMessage(message);
                    }
                }
            };

    private BroadcastReceiver mLocationReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (LocationManager.MODE_CHANGED_ACTION.equals(action)) {
                        final boolean locationEnabled = mLocationManager.isLocationEnabled();
                        if (locationEnabled) {
                            sendMessage(MSG_RESUME_SCANS, null);
                        } else {
                            sendMessage(MSG_SUSPEND_SCANS, null);
                        }
                    }
                }
            };

    private boolean updateCountersAndCheckForConnectingState(int state, int prevState) {
        switch (prevState) {
            case BluetoothProfile.STATE_CONNECTING:
                if (mProfilesConnecting > 0) {
                    mProfilesConnecting--;
                } else {
                    Log.e(TAG, "mProfilesConnecting " + mProfilesConnecting);
                    throw new IllegalStateException(
                            "Invalid state transition, " + prevState + " -> " + state);
                }
                break;
            case BluetoothProfile.STATE_CONNECTED:
                if (mProfilesConnected > 0) {
                    mProfilesConnected--;
                } else {
                    Log.e(TAG, "mProfilesConnected " + mProfilesConnected);
                    throw new IllegalStateException(
                            "Invalid state transition, " + prevState + " -> " + state);
                }
                break;
            case BluetoothProfile.STATE_DISCONNECTING:
                if (mProfilesDisconnecting > 0) {
                    mProfilesDisconnecting--;
                } else {
                    Log.e(TAG, "mProfilesDisconnecting " + mProfilesDisconnecting);
                    throw new IllegalStateException(
                            "Invalid state transition, " + prevState + " -> " + state);
                }
                break;
        }
        switch (state) {
            case BluetoothProfile.STATE_CONNECTING:
                mProfilesConnecting++;
                break;
            case BluetoothProfile.STATE_CONNECTED:
                mProfilesConnected++;
                break;
            case BluetoothProfile.STATE_DISCONNECTING:
                mProfilesDisconnecting++;
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                break;
            default:
        }
        if (DBG) {
            Log.d(TAG, "mProfilesConnecting " + mProfilesConnecting + ", mProfilesConnected "
                    + mProfilesConnected + ", mProfilesDisconnecting " + mProfilesDisconnecting);
        }
        return (mProfilesConnecting > 0);
    }

    private void handleImportanceChange(UidImportance imp) {
        if (imp == null) {
            return;
        }
        int uid = imp.uid;
        int importance = imp.importance;
        boolean updatedScanParams = false;
        boolean isForeground = importance <= ActivityManager.RunningAppProcessInfo
                .IMPORTANCE_FOREGROUND_SERVICE;

        if (mIsUidForegroundMap.size() < MAX_IS_UID_FOREGROUND_MAP_SIZE) {
            mIsUidForegroundMap.put(uid, isForeground);
        }

        for (ScanClient client : mRegularScanClients) {
            if (client.appUid != uid || mScanNative.isOpportunisticScanClient(client)) {
                continue;
            }
            if (isForeground) {
                int scanMode = client.scanModeApp;
                int maxScanMode =
                        mScanNative.isForceDowngradedScanClient(client)
                                ? SCAN_MODE_FORCE_DOWNGRADED
                                : scanMode;
                if (client.updateScanMode(getMinScanMode(scanMode, maxScanMode))) {
                    updatedScanParams = true;
                }
            } else {
                int scanMode = client.settings.getScanMode();
                int maxScanMode =
                        mScreenOn ? SCAN_MODE_APP_IN_BACKGROUND : ScanSettings.SCAN_MODE_SCREEN_OFF;
                if (client.updateScanMode(getMinScanMode(scanMode, maxScanMode))) {
                    updatedScanParams = true;
                }
            }
            if (DBG) {
                Log.d(TAG, "uid " + uid + " isForeground " + isForeground
                        + " scanMode " + client.settings.getScanMode());
            }
        }

        if (updatedScanParams) {
            mScanNative.configureRegularScanParams();
        }
    }

    private int getMinScanMode(int oldScanMode, int newScanMode) {
        return mPriorityMap.get(oldScanMode) <= mPriorityMap.get(newScanMode)
                ? oldScanMode
                : newScanMode;
    }

    /**
     * Handle bluetooth profile connection state changes (for A2DP, HFP, HFP Client, A2DP Sink and
     * LE Audio).
     */
    public void handleBluetoothProfileConnectionStateChanged(
            int profile, int fromState, int toState) {
        if (mHandler == null) {
            Log.d(TAG, "handleBluetoothProfileConnectionStateChanged: mHandler is null.");
            return;
        }
        mHandler.obtainMessage(
                        MSG_BT_PROFILE_CONN_STATE_CHANGED,
                        fromState,
                        toState,
                        Integer.valueOf(profile))
                .sendToTarget();
        return;
    }
}
