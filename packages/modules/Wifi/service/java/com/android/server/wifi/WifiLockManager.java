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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.Context;
import android.net.wifi.IWifiLowLatencyLockListener;
import android.net.wifi.WifiManager;
import android.os.BatteryStatsManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.WorkSource;
import android.os.WorkSource.WorkChain;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.server.wifi.util.WorkSourceUtil;
import com.android.wifi.resources.R;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;

/**
 * WifiLockManager maintains the list of wake locks held by different applications.
 */
public class WifiLockManager {
    private static final String TAG = "WifiLockManager";

    private static final int LOW_LATENCY_SUPPORT_UNDEFINED = -1;
    private static final int LOW_LATENCY_NOT_SUPPORTED     =  0;
    private static final int LOW_LATENCY_SUPPORTED         =  1;

    private static final int IGNORE_SCREEN_STATE_MASK = 0x01;
    private static final int IGNORE_WIFI_STATE_MASK   = 0x02;

    private int mLatencyModeSupport = LOW_LATENCY_SUPPORT_UNDEFINED;

    private boolean mVerboseLoggingEnabled = false;

    private final Clock mClock;
    private final Context mContext;
    private final BatteryStatsManager mBatteryStats;
    private final FrameworkFacade mFrameworkFacade;
    private final ActiveModeWarden mActiveModeWarden;
    private final ActivityManager mActivityManager;
    private final Handler mHandler;
    private final WifiMetrics mWifiMetrics;

    private final List<WifiLock> mWifiLocks = new ArrayList<>();
    // map UIDs to their corresponding records (for low-latency locks)
    private final SparseArray<UidRec> mLowLatencyUidWatchList = new SparseArray<>();
    /** the current op mode of the primary ClientModeManager */
    private int mCurrentOpMode = WifiManager.WIFI_MODE_NO_LOCKS_HELD;
    private boolean mScreenOn = false;
    /** whether Wifi is connected on the primary ClientModeManager */
    private boolean mWifiConnected = false;

    // For shell command support
    private boolean mForceHiPerfMode = false;
    private boolean mForceLowLatencyMode = false;

    // some wifi lock statistics
    private int mFullHighPerfLocksAcquired;
    private int mFullHighPerfLocksReleased;
    private int mFullLowLatencyLocksAcquired;
    private int mFullLowLatencyLocksReleased;
    private long mCurrentSessionStartTimeMs;
    private final DeviceConfigFacade mDeviceConfigFacade;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private final RemoteCallbackList<IWifiLowLatencyLockListener>
            mWifiLowLatencyLockListeners = new RemoteCallbackList<>();
    private boolean mIsLowLatencyActivated = false;
    private WorkSource mLowLatencyBlamedWorkSource = new WorkSource();
    private WorkSource mHighPerfBlamedWorkSource = new WorkSource();
    private enum BlameReason {
        WIFI_CONNECTION_STATE_CHANGED,
        SCREEN_STATE_CHANGED,
    };

    WifiLockManager(
            Context context,
            BatteryStatsManager batteryStats,
            ActiveModeWarden activeModeWarden,
            FrameworkFacade frameworkFacade,
            Handler handler,
            Clock clock,
            WifiMetrics wifiMetrics,
            DeviceConfigFacade deviceConfigFacade,
            WifiPermissionsUtil wifiPermissionsUtil,
            WifiDeviceStateChangeManager wifiDeviceStateChangeManager) {
        mContext = context;
        mBatteryStats = batteryStats;
        mActiveModeWarden = activeModeWarden;
        mFrameworkFacade = frameworkFacade;
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mHandler = handler;
        mClock = clock;
        mWifiMetrics = wifiMetrics;
        mDeviceConfigFacade = deviceConfigFacade;
        mWifiPermissionsUtil = wifiPermissionsUtil;

        wifiDeviceStateChangeManager.registerStateChangeCallback(
                new WifiDeviceStateChangeManager.StateChangeCallback() {
                    @Override
                    public void onScreenStateChanged(boolean screenOn) {
                        handleScreenStateChanged(screenOn);
                    }
                });

        // Register for UID fg/bg transitions
        registerUidImportanceTransitions();
    }

    private boolean canDisableChipPowerSave() {
        return mContext.getResources().getBoolean(
                R.bool.config_wifiLowLatencyLockDisableChipPowerSave);
    }

    // Check for conditions to activate high-perf lock
    private boolean canActivateHighPerfLock(int ignoreMask) {
        boolean check = true;

        // Only condition is when Wifi is connected
        if ((ignoreMask & IGNORE_WIFI_STATE_MASK) == 0) {
            check = check && mWifiConnected;
        }

        return check;
    }

    private boolean canActivateHighPerfLock() {
        return canActivateHighPerfLock(0);
    }

    // Check for conditions to activate low-latency lock
    private boolean canActivateLowLatencyLock(int ignoreMask, UidRec uidRec) {
        boolean check = true;

        if ((ignoreMask & IGNORE_WIFI_STATE_MASK) == 0) {
            check = check && mWifiConnected;
        }
        if ((ignoreMask & IGNORE_SCREEN_STATE_MASK) == 0) {
            check = check && mScreenOn;
        }
        if (uidRec != null) {
            check = check && uidRec.mIsFg;
        }

        return check;
    }

    private boolean canActivateLowLatencyLock(int ignoreMask) {
        return canActivateLowLatencyLock(ignoreMask, null);
    }

    private boolean canActivateLowLatencyLock() {
        return canActivateLowLatencyLock(0, null);
    }

    private void onAppForeground(final int uid, final int importance) {
        mHandler.post(() -> {
            UidRec uidRec = mLowLatencyUidWatchList.get(uid);
            if (uidRec == null) {
                // Not a uid in the watch list
                return;
            }

            boolean newModeIsFg = isAppForeground(uid, importance);
            if (uidRec.mIsFg == newModeIsFg) {
                return; // already at correct state
            }

            uidRec.mIsFg = newModeIsFg;
            updateOpMode();

            // If conditions for lock activation are met,
            // then UID either share the blame, or removed from sharing
            // whether to start or stop the blame based on UID fg/bg state
            if (canActivateLowLatencyLock(
                    uidRec.mIsScreenOnExempted ? IGNORE_SCREEN_STATE_MASK : 0)) {
                setBlameLowLatencyUid(uid, uidRec.mIsFg);
                notifyLowLatencyActiveUsersChanged();
            }
        });
    }

    // Detect UIDs going,
    //          - Foreground <-> Background
    //          - Foreground service <-> Background
    private void registerUidImportanceTransitions() {
        mActivityManager.addOnUidImportanceListener(new ActivityManager.OnUidImportanceListener() {
            @Override
            public void onUidImportance(final int uid, final int importance) {
                onAppForeground(uid, importance);
            }
        }, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
        mActivityManager.addOnUidImportanceListener(new ActivityManager.OnUidImportanceListener() {
            @Override
            public void onUidImportance(final int uid, final int importance) {
                onAppForeground(uid, importance);
            }
        }, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE);
    }

    /**
     * Method allowing a calling app to acquire a Wifi WakeLock in the supplied mode.
     *
     * This method checks that the lock mode is a valid WifiLock mode.
     * @param lockMode int representation of the Wifi WakeLock type.
     * @param tag String passed to WifiManager.WifiLock
     * @param binder IBinder for the calling app
     * @param ws WorkSource of the calling app
     *
     * @return true if the lock was successfully acquired, false if the lockMode was invalid.
     */
    public boolean acquireWifiLock(int lockMode, String tag, IBinder binder, WorkSource ws) {
        // Make a copy of the WorkSource before adding it to the WakeLock
        // This is to make sure worksource value can not be changed by caller
        // after function returns.
        WorkSource newWorkSource = new WorkSource(ws);
        // High perf lock is deprecated from Android U onwards. Acquisition of  High perf lock
        // will be treated as a call to Low Latency Lock.
        if (mDeviceConfigFacade.isHighPerfLockDeprecated() && SdkLevel.isAtLeastU()
                && lockMode == WifiManager.WIFI_MODE_FULL_HIGH_PERF) {
            lockMode = WifiManager.WIFI_MODE_FULL_LOW_LATENCY;
        }
        return addLock(new WifiLock(lockMode, tag, binder, newWorkSource));
    }

    /**
     * Method used by applications to release a WiFi Wake lock.
     *
     * @param binder IBinder for the calling app.
     * @return true if the lock was released, false if the caller did not hold any locks
     */
    public boolean releaseWifiLock(IBinder binder) {
        return releaseLock(binder);
    }

    /**
     * Method used to get the strongest lock type currently held by the WifiLockManager.
     *
     * If no locks are held, WifiManager.WIFI_MODE_NO_LOCKS_HELD is returned.
     *
     * @return int representing the currently held (highest power consumption) lock.
     */
    @VisibleForTesting
    synchronized int getStrongestLockMode() {
        // If Wifi Client is not connected, then all locks are not effective
        if (!mWifiConnected) {
            return WifiManager.WIFI_MODE_NO_LOCKS_HELD;
        }

        // Check if mode is forced to hi-perf
        if (mForceHiPerfMode) {
            return WifiManager.WIFI_MODE_FULL_HIGH_PERF;
        }

        // Check if mode is forced to low-latency
        if (mForceLowLatencyMode) {
            return WifiManager.WIFI_MODE_FULL_LOW_LATENCY;
        }

        if (mScreenOn && countFgLowLatencyUids(false) > 0) {
            return WifiManager.WIFI_MODE_FULL_LOW_LATENCY;
        }

        if (!mScreenOn && countFgLowLatencyUids(true) > 0) {
            return WifiManager.WIFI_MODE_FULL_LOW_LATENCY;
        }

        if (mFullHighPerfLocksAcquired > mFullHighPerfLocksReleased) {
            return WifiManager.WIFI_MODE_FULL_HIGH_PERF;
        }

        return WifiManager.WIFI_MODE_NO_LOCKS_HELD;
    }

    /**
     * Method to create a WorkSource containing all active WifiLock WorkSources.
     */
    public synchronized WorkSource createMergedWorkSource() {
        WorkSource mergedWS = new WorkSource();
        for (WifiLock lock : mWifiLocks) {
            mergedWS.add(lock.getWorkSource());
        }
        return mergedWS;
    }

    /**
     * Method used to update WifiLocks with a new WorkSouce.
     *
     * @param binder IBinder for the calling application.
     * @param ws WorkSource to add to the existing WifiLock(s).
     */
    public synchronized void updateWifiLockWorkSource(IBinder binder, WorkSource ws) {

        // Now check if there is an active lock
        WifiLock wl = findLockByBinder(binder);
        if (wl == null) {
            throw new IllegalArgumentException("Wifi lock not active");
        }

        // Make a copy of the WorkSource before adding it to the WakeLock
        // This is to make sure worksource value can not be changed by caller
        // after function returns.
        WorkSource newWorkSource = new WorkSource(ws);

        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "updateWifiLockWakeSource: " + wl + ", newWorkSource=" + newWorkSource);
        }

        // Note:
        // Log the acquire before the release to avoid "holes" in the collected data due to
        // an acquire event immediately after a release in the case where newWorkSource and
        // wl.mWorkSource share one or more attribution UIDs. Both batteryStats and statsd
        // can correctly match "nested" acquire / release pairs.
        switch(wl.mMode) {
            case WifiManager.WIFI_MODE_FULL_HIGH_PERF:
                // Shift blame to new worksource if needed
                if (canActivateHighPerfLock()) {
                    setBlameHiPerfWs(newWorkSource, true);
                    setBlameHiPerfWs(wl.mWorkSource, false);
                }
                break;
            case WifiManager.WIFI_MODE_FULL_LOW_LATENCY:
                addWsToLlWatchList(newWorkSource);
                removeWsFromLlWatchList(wl.mWorkSource);
                updateOpMode();
                break;
            default:
                // Do nothing
                break;
        }

        wl.mWorkSource = newWorkSource;
    }

    /**
     * Method Used for shell command support
     *
     * @param isEnabled True to force hi-perf mode, false to leave it up to acquired wifiLocks.
     * @return True for success, false for failure (failure turns forcing mode off)
     */
    public boolean forceHiPerfMode(boolean isEnabled) {
        mForceHiPerfMode = isEnabled;
        mForceLowLatencyMode = false;
        if (!updateOpMode()) {
            Log.e(TAG, "Failed to force hi-perf mode, returning to normal mode");
            mForceHiPerfMode = false;
            return false;
        }
        return true;
    }

    /**
     * Method Used for shell command support
     *
     * @param isEnabled True to force low-latency mode, false to leave it up to acquired wifiLocks.
     * @return True for success, false for failure (failure turns forcing mode off)
     */
    public boolean forceLowLatencyMode(boolean isEnabled) {
        mForceLowLatencyMode = isEnabled;
        mForceHiPerfMode = false;
        if (!updateOpMode()) {
            Log.e(TAG, "Failed to force low-latency mode, returning to normal mode");
            mForceLowLatencyMode = false;
            return false;
        }
        return true;
    }

    /**
     * Handler for screen state (on/off) changes
     */
    private void handleScreenStateChanged(boolean screenOn) {
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "handleScreenStateChanged: screenOn = " + screenOn);
        }

        mScreenOn = screenOn;

        if (canActivateLowLatencyLock(IGNORE_SCREEN_STATE_MASK)) {
            // Update the running mode
            updateOpMode();
            // Adjust blaming for UIDs in foreground
            setBlameLowLatencyWatchList(BlameReason.SCREEN_STATE_CHANGED, screenOn);
        }
    }

    /**
     * Handler for Wifi Client mode state changes
     */
    public void updateWifiClientConnected(
            ClientModeManager clientModeManager, boolean isConnected) {
        boolean hasAtLeastOneConnection = isConnected
                || mActiveModeWarden.getClientModeManagers().stream().anyMatch(
                        cmm -> cmm.isConnected());
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "updateWifiClientConnected hasAtLeastOneConnection="
                    + hasAtLeastOneConnection);
        }
        if (mWifiConnected == hasAtLeastOneConnection) {
            // No need to take action
            return;
        }
        mWifiConnected = hasAtLeastOneConnection;

        // Adjust blaming for UIDs in foreground carrying low latency locks
        if (canActivateLowLatencyLock(countFgLowLatencyUids(/*isScreenOnExempted*/ true) > 0
                ? IGNORE_SCREEN_STATE_MASK | IGNORE_WIFI_STATE_MASK
                : IGNORE_WIFI_STATE_MASK)) {
            setBlameLowLatencyWatchList(BlameReason.WIFI_CONNECTION_STATE_CHANGED, mWifiConnected);
        }

        // Adjust blaming for UIDs carrying high perf locks
        // Note that blaming is adjusted only if needed,
        // since calling this API is reference counted
        if (canActivateHighPerfLock(IGNORE_WIFI_STATE_MASK)) {
            setBlameHiPerfLocks(mWifiConnected);
        }

        updateOpMode();
    }

    private synchronized void setBlameHiPerfLocks(boolean shouldBlame) {
        for (WifiLock lock : mWifiLocks) {
            if (lock.mMode == WifiManager.WIFI_MODE_FULL_HIGH_PERF) {
                setBlameHiPerfWs(lock.getWorkSource(), shouldBlame);
            }
        }
    }

    /**
     * Validate that the lock mode is valid - i.e. one of the supported enumerations.
     *
     * @param lockMode The lock mode to verify.
     * @return true for valid lock modes, false otherwise.
     */
    public static boolean isValidLockMode(int lockMode) {
        if (lockMode != WifiManager.WIFI_MODE_FULL
                && lockMode != WifiManager.WIFI_MODE_SCAN_ONLY
                && lockMode != WifiManager.WIFI_MODE_FULL_HIGH_PERF
                && lockMode != WifiManager.WIFI_MODE_FULL_LOW_LATENCY) {
            return false;
        }
        return true;
    }

    private boolean isAnyLowLatencyAppExemptedFromForeground(int[] uids) {
        if (uids == null) return false;
        for (int uid : uids) {
            UidRec uidRec = mLowLatencyUidWatchList.get(uid);
            if (uidRec != null && uidRec.mIsFgExempted) {
                return true;
            }
        }
        return false;
    }

    private boolean isAppExemptedFromImportance(int uid, int importance) {
        // Exemption for applications running with CAR Mode permissions.
        if (mWifiPermissionsUtil.checkRequestCompanionProfileAutomotiveProjectionPermission(uid)
                && (importance
                <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE)) {
            return true;
        }
        // Add any exemption cases for applications regarding restricting Low latency locks to
        // running in the foreground.
        return false;
    }

    private boolean isAppForeground(final int uid, final int importance) {
        if ((importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)) {
            return true;
        }
        return isAppExemptedFromImportance(uid, importance);
    }

    private boolean isAnyLowLatencyAppExemptedFromScreenOn(int[] uids) {
        if (uids == null) return false;
        for (int uid : uids) {
            UidRec uidRec = mLowLatencyUidWatchList.get(uid);
            if (uidRec != null && uidRec.mIsScreenOnExempted) {
                return true;
            }
        }
        return false;
    }

    private boolean isAppExemptedFromScreenOn(int uid) {
        // Exemption for applications running with CAR Mode permissions.
        if (mWifiPermissionsUtil.checkRequestCompanionProfileAutomotiveProjectionPermission(uid)) {
            return true;
        }
        // Add more exemptions here
        return false;
    }

    private void addUidToLlWatchList(int uid) {
        UidRec uidRec = mLowLatencyUidWatchList.get(uid);
        if (uidRec != null) {
            uidRec.mLockCount++;
        } else {
            uidRec = new UidRec(uid);
            uidRec.mLockCount = 1;
            mLowLatencyUidWatchList.put(uid, uidRec);
            notifyLowLatencyOwnershipChanged();

            uidRec.mIsFg = isAppForeground(uid,
                    mContext.getSystemService(ActivityManager.class).getUidImportance(uid));
            // Save the current permission of foreground & 'screen on' exemption.
            uidRec.mIsFgExempted = isAppExemptedFromImportance(uid,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
            uidRec.mIsScreenOnExempted = isAppExemptedFromScreenOn(uid);

            if (canActivateLowLatencyLock(
                    uidRec.mIsScreenOnExempted ? IGNORE_SCREEN_STATE_MASK : 0,
                    uidRec)) {
                // Share the blame for this uid
                setBlameLowLatencyUid(uid, true);
                notifyLowLatencyActiveUsersChanged();
            }
        }
    }

    private void removeUidFromLlWatchList(int uid) {
        UidRec uidRec = mLowLatencyUidWatchList.get(uid);
        if (uidRec == null) {
            Log.e(TAG, "Failed to find uid in low-latency watch list");
            return;
        }

        if (uidRec.mLockCount > 0) {
            uidRec.mLockCount--;
        } else {
            Log.e(TAG, "Error, uid record contains no locks");
        }
        if (uidRec.mLockCount == 0) {
            mLowLatencyUidWatchList.remove(uid);
            notifyLowLatencyOwnershipChanged();

            // Remove blame for this UID if it was already set
            // Note that blame needs to be stopped only if it was started before
            // to avoid calling the API unnecessarily, since it is reference counted
            if (canActivateLowLatencyLock(uidRec.mIsScreenOnExempted ? IGNORE_SCREEN_STATE_MASK : 0,
                    uidRec)) {
                setBlameLowLatencyUid(uid, false);
                notifyLowLatencyActiveUsersChanged();
            }
        }
    }

    private void addWsToLlWatchList(WorkSource ws) {
        int wsSize = ws.size();
        for (int i = 0; i < wsSize; i++) {
            final int uid = ws.getUid(i);
            addUidToLlWatchList(uid);
        }

        final List<WorkChain> workChains = ws.getWorkChains();
        if (workChains != null) {
            for (int i = 0; i < workChains.size(); ++i) {
                final WorkChain workChain = workChains.get(i);
                final int uid = workChain.getAttributionUid();
                addUidToLlWatchList(uid);
            }
        }
    }

    private void removeWsFromLlWatchList(WorkSource ws) {
        int wsSize = ws.size();
        for (int i = 0; i < wsSize; i++) {
            final int uid = ws.getUid(i);
            removeUidFromLlWatchList(uid);
        }

        final List<WorkChain> workChains = ws.getWorkChains();
        if (workChains != null) {
            for (int i = 0; i < workChains.size(); ++i) {
                final WorkChain workChain = workChains.get(i);
                final int uid = workChain.getAttributionUid();
                removeUidFromLlWatchList(uid);
            }
        }
    }

    private synchronized boolean addLock(WifiLock lock) {
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "addLock: " + lock);
        }

        if (findLockByBinder(lock.getBinder()) != null) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "attempted to add a lock when already holding one");
            }
            return false;
        }

        mWifiLocks.add(lock);

        switch(lock.mMode) {
            case WifiManager.WIFI_MODE_FULL_HIGH_PERF:
                ++mFullHighPerfLocksAcquired;
                // Start blaming this worksource if conditions are met
                if (canActivateHighPerfLock()) {
                    setBlameHiPerfWs(lock.mWorkSource, true);
                }
                break;
            case WifiManager.WIFI_MODE_FULL_LOW_LATENCY:
                addWsToLlWatchList(lock.getWorkSource());
                ++mFullLowLatencyLocksAcquired;
                break;
            default:
                // Do nothing
                break;
        }

        // Recalculate the operating mode
        updateOpMode();

        return true;
    }

    private synchronized WifiLock removeLock(IBinder binder) {
        WifiLock lock = findLockByBinder(binder);
        if (lock != null) {
            mWifiLocks.remove(lock);
            lock.unlinkDeathRecipient();
        }
        return lock;
    }

    private synchronized boolean releaseLock(IBinder binder) {
        WifiLock wifiLock = removeLock(binder);
        if (wifiLock == null) {
            // attempting to release a lock that does not exist.
            return false;
        }

        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "releaseLock: " + wifiLock);
        }

        WorkSource ws = wifiLock.getWorkSource();
        Pair<int[], String[]> uidsAndTags = WorkSourceUtil.getUidsAndTagsForWs(ws);

        switch(wifiLock.mMode) {
            case WifiManager.WIFI_MODE_FULL_HIGH_PERF:
                mWifiMetrics.addWifiLockAcqSession(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                        uidsAndTags.first,
                        uidsAndTags.second,
                        mWifiPermissionsUtil.getWifiCallerType(wifiLock.getUid(),
                                ws.getPackageName(0)),
                        mClock.getElapsedSinceBootMillis() - wifiLock.getAcqTimestamp(),
                        canDisableChipPowerSave(),
                        false,
                        false);
                ++mFullHighPerfLocksReleased;
                // Stop blaming only if blaming was set before (conditions are met).
                // This is to avoid calling the api unncessarily, since this API is
                // reference counted in batteryStats and statsd
                if (canActivateHighPerfLock()) {
                    setBlameHiPerfWs(wifiLock.mWorkSource, false);
                }
                break;
            case WifiManager.WIFI_MODE_FULL_LOW_LATENCY:
                mWifiMetrics.addWifiLockAcqSession(WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                        uidsAndTags.first,
                        uidsAndTags.second,
                        mWifiPermissionsUtil.getWifiCallerType(wifiLock.getUid(),
                                ws.getPackageName(0)),
                        mClock.getElapsedSinceBootMillis() - wifiLock.getAcqTimestamp(),
                        canDisableChipPowerSave(),
                        isAnyLowLatencyAppExemptedFromScreenOn(uidsAndTags.first),
                        isAnyLowLatencyAppExemptedFromForeground(uidsAndTags.first));
                removeWsFromLlWatchList(wifiLock.getWorkSource());
                ++mFullLowLatencyLocksReleased;
                break;
            default:
                // Do nothing
                break;
        }

        // Recalculate the operating mode
        updateOpMode();

        return true;
    }

    /**
     * Reset the given ClientModeManager's power save/low latency mode to the default.
     * The method calls needed to reset is the reverse of the method calls used to set.
     * @return true if the operation succeeded, false otherwise
     */
    private boolean resetCurrentMode(@NonNull ClientModeManager clientModeManager) {
        Pair<int[], String[]> uidsAndTags = null;
        switch (mCurrentOpMode) {
            case WifiManager.WIFI_MODE_FULL_HIGH_PERF:
                if (!setPowerSave(clientModeManager, ClientMode.POWER_SAVE_CLIENT_WIFI_LOCK,
                        true)) {
                    Log.e(TAG, "Failed to reset the OpMode from hi-perf to Normal");
                    return false;
                }
                uidsAndTags = WorkSourceUtil.getUidsAndTagsForWs(mHighPerfBlamedWorkSource);
                mWifiMetrics.addWifiLockActiveSession(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                        uidsAndTags.first,
                        uidsAndTags.second,
                        mClock.getElapsedSinceBootMillis() - mCurrentSessionStartTimeMs,
                        canDisableChipPowerSave(),
                        false,
                        false);
                mHighPerfBlamedWorkSource.clear();
                break;

            case WifiManager.WIFI_MODE_FULL_LOW_LATENCY:
                if (!setLowLatencyMode(clientModeManager, false)) {
                    Log.e(TAG, "Failed to reset the OpMode from low-latency to Normal");
                    return false;
                }
                uidsAndTags = WorkSourceUtil.getUidsAndTagsForWs(mLowLatencyBlamedWorkSource);
                mWifiMetrics.addWifiLockActiveSession(WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                        uidsAndTags.first,
                        uidsAndTags.second,
                        mClock.getElapsedSinceBootMillis() - mCurrentSessionStartTimeMs,
                        canDisableChipPowerSave(),
                        isAnyLowLatencyAppExemptedFromScreenOn(uidsAndTags.first),
                        isAnyLowLatencyAppExemptedFromForeground(uidsAndTags.first));
                mLowLatencyBlamedWorkSource.clear();
                break;

            case WifiManager.WIFI_MODE_NO_LOCKS_HELD:
            default:
                // No action
                break;
        }

        // reset the current mode
        mCurrentOpMode = WifiManager.WIFI_MODE_NO_LOCKS_HELD;
        return true;
    }

    /**
     * Set power save mode with an overlay check. It's a wrapper for
     * {@link ClientModeImpl#setPowerSave(int, boolean)}.
     */
    private boolean setPowerSave(@NonNull ClientModeManager clientModeManager,
            @ClientMode.PowerSaveClientType int client, boolean ps) {
        // Check the overlay allows lock to control chip power save.
        if (canDisableChipPowerSave()) {
            return clientModeManager.setPowerSave(client, ps);
        }
        // Otherwise, pretend the call is a success.
        return true;
    }

    /**
     * Set the new lock mode on the given ClientModeManager
     * @return true if the operation succeeded, false otherwise
     */
    private boolean setNewMode(@NonNull ClientModeManager clientModeManager, int newLockMode) {
        switch (newLockMode) {
            case WifiManager.WIFI_MODE_FULL_HIGH_PERF:
                if (!setPowerSave(clientModeManager, ClientMode.POWER_SAVE_CLIENT_WIFI_LOCK,
                        false)) {
                    Log.e(TAG, "Failed to set the OpMode to hi-perf");
                    return false;
                }
                mCurrentSessionStartTimeMs = mClock.getElapsedSinceBootMillis();
                break;

            case WifiManager.WIFI_MODE_FULL_LOW_LATENCY:
                if (!setLowLatencyMode(clientModeManager, true)) {
                    Log.e(TAG, "Failed to set the OpMode to low-latency");
                    return false;
                }
                mCurrentSessionStartTimeMs = mClock.getElapsedSinceBootMillis();
                break;

            case WifiManager.WIFI_MODE_NO_LOCKS_HELD:
                // No action
                break;

            default:
                // Invalid mode, don't change currentOpMode, and exit with error
                Log.e(TAG, "Invalid new opMode: " + newLockMode);
                return false;
        }

        // Now set the mode to the new value
        mCurrentOpMode = newLockMode;
        return true;
    }

    private synchronized boolean updateOpMode() {
        final int newLockMode = getStrongestLockMode();

        if (newLockMode == mCurrentOpMode) {
            // No action is needed
            return true;
        }

        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "Current opMode: " + mCurrentOpMode
                    + " New LockMode: " + newLockMode);
        }

        ClientModeManager primaryManager = mActiveModeWarden.getPrimaryClientModeManager();

        // Otherwise, we need to change current mode, first reset it to normal
        if (!resetCurrentMode(primaryManager)) {
            return false;
        }

        // Now switch to the new opMode
        return setNewMode(primaryManager, newLockMode);
    }

    /** Returns the cached low latency mode support value, or tries to fetch it if not yet known. */
    private int getLowLatencyModeSupport() {
        if (mLatencyModeSupport != LOW_LATENCY_SUPPORT_UNDEFINED) {
            return mLatencyModeSupport;
        }

        long supportedFeatures =
                mActiveModeWarden.getPrimaryClientModeManager().getSupportedFeatures();
        if (supportedFeatures == 0L) {
            return LOW_LATENCY_SUPPORT_UNDEFINED;
        }

        if ((supportedFeatures & WifiManager.WIFI_FEATURE_LOW_LATENCY) != 0) {
            mLatencyModeSupport = LOW_LATENCY_SUPPORTED;
        } else {
            mLatencyModeSupport = LOW_LATENCY_NOT_SUPPORTED;
        }
        return mLatencyModeSupport;
    }

    private boolean setLowLatencyMode(ClientModeManager clientModeManager, boolean enabled) {
        int lowLatencySupport = getLowLatencyModeSupport();

        if (lowLatencySupport == LOW_LATENCY_SUPPORT_UNDEFINED) {
            // Support undefined, no action is taken
            return false;
        }

        if (lowLatencySupport == LOW_LATENCY_SUPPORTED) {
            if (!clientModeManager.setLowLatencyMode(enabled)) {
                Log.e(TAG, "Failed to set low latency mode");
                return false;
            }

            if (!setPowerSave(clientModeManager, ClientMode.POWER_SAVE_CLIENT_WIFI_LOCK,
                    !enabled)) {
                Log.e(TAG, "Failed to set power save mode");
                // Revert the low latency mode
                clientModeManager.setLowLatencyMode(!enabled);
                return false;
            }
        } else if (lowLatencySupport == LOW_LATENCY_NOT_SUPPORTED) {
            // Only set power save mode
            if (!setPowerSave(clientModeManager, ClientMode.POWER_SAVE_CLIENT_WIFI_LOCK,
                    !enabled)) {
                Log.e(TAG, "Failed to set power save mode");
                return false;
            }
        }

        mIsLowLatencyActivated = enabled;
        notifyLowLatencyActivated();
        notifyLowLatencyActiveUsersChanged();
        return true;
    }

    private int[] getLowLatencyLockOwners() {
        int[] owners = new int[mLowLatencyUidWatchList.size()];
        for (int idx = 0; idx < mLowLatencyUidWatchList.size(); idx++) {
            owners[idx] = mLowLatencyUidWatchList.valueAt(idx).mUid;
        }
        return owners;
    }

    private int[] getLowLatencyActiveUsers() {
        // Return empty array if low latency mode is not activated. Otherwise, return UIDs which are
        // in foreground or exempted.
        if (!mIsLowLatencyActivated) return new int[0];
        ArrayList<Integer> activeUsers = new ArrayList<>();
        for (int idx = 0; idx < mLowLatencyUidWatchList.size(); idx++) {
            if (mLowLatencyUidWatchList.valueAt(idx).mIsFg) {
                activeUsers.add(mLowLatencyUidWatchList.valueAt(idx).mUid);
            }
        }
        return activeUsers.stream().mapToInt(i->i).toArray();
    }

    /**
     * See {@link WifiManager#addWifiLowLatencyLockListener(Executor,
     * WifiManager.WifiLowLatencyLockListener)}
     */
    public boolean addWifiLowLatencyLockListener(@NonNull IWifiLowLatencyLockListener listener) {
        if (!mWifiLowLatencyLockListeners.register(listener)) {
            return false;
        }
        // Notify the new listener about the current enablement of low latency mode.
        try {
            listener.onActivatedStateChanged(mIsLowLatencyActivated);
            listener.onOwnershipChanged(getLowLatencyLockOwners());
            if (mIsLowLatencyActivated) {
                listener.onActiveUsersChanged(getLowLatencyActiveUsers());
            }
        } catch (RemoteException e) {
            Log.e(TAG, "addWifiLowLatencyLockListener: Failure notifying listener" + e);
        }
        return true;
    }

    /**
     * See
     * {@link WifiManager#removeWifiLowLatencyLockListener(WifiManager.WifiLowLatencyLockListener)}
     */
    public boolean removeWifiLowLatencyLockListener(@NonNull IWifiLowLatencyLockListener listener) {
        return mWifiLowLatencyLockListeners.unregister(listener);
    }

    private void notifyLowLatencyActivated() {
        int numCallbacks = mWifiLowLatencyLockListeners.beginBroadcast();
        if (mVerboseLoggingEnabled) {
            Log.i(TAG, "Broadcasting IWifiLowLatencyLockListener#onActivatedStateChanged activated="
                    + mIsLowLatencyActivated);
        }
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiLowLatencyLockListeners.getBroadcastItem(i).onActivatedStateChanged(
                        mIsLowLatencyActivated);
            } catch (RemoteException e) {
                Log.e(TAG,
                        "Failure broadcasting IWifiLowLatencyLockListener#onActivatedStateChanged"
                                + e);
            }
        }
        mWifiLowLatencyLockListeners.finishBroadcast();
    }

    private void notifyLowLatencyOwnershipChanged() {
        int numCallbacks = mWifiLowLatencyLockListeners.beginBroadcast();
        int[] owners = getLowLatencyLockOwners();
        if (mVerboseLoggingEnabled) {
            Log.i(TAG, "Broadcasting IWifiLowLatencyLockListener#onOwnershipChanged: UIDs "
                    + Arrays.toString(owners));
        }
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiLowLatencyLockListeners.getBroadcastItem(i).onOwnershipChanged(owners);
            } catch (RemoteException e) {
                Log.e(TAG,
                        "Failure broadcasting IWifiLowLatencyLockListener#onOwnershipChanged" + e);
            }
        }
        mWifiLowLatencyLockListeners.finishBroadcast();
    }

    private void notifyLowLatencyActiveUsersChanged() {
        if (!mIsLowLatencyActivated) return;
        int numCallbacks = mWifiLowLatencyLockListeners.beginBroadcast();
        int[] activeUsers = getLowLatencyActiveUsers();
        if (mVerboseLoggingEnabled) {
            Log.i(TAG, "Broadcasting IWifiLowLatencyLockListener#onActiveUsersChanged: UIDs "
                    + Arrays.toString(activeUsers));
        }
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiLowLatencyLockListeners.getBroadcastItem(i).onActiveUsersChanged(activeUsers);
            } catch (RemoteException e) {
                Log.e(TAG, "Failure broadcasting IWifiLowLatencyLockListener#onActiveUsersChanged"
                        + e);
            }
        }
        mWifiLowLatencyLockListeners.finishBroadcast();
    }

    private synchronized WifiLock findLockByBinder(IBinder binder) {
        for (WifiLock lock : mWifiLocks) {
            if (lock.getBinder() == binder) {
                return lock;
            }
        }
        return null;
    }

    private int countFgLowLatencyUids(boolean isScreenOnExempted) {
        int uidCount = 0;
        int listSize = mLowLatencyUidWatchList.size();
        for (int idx = 0; idx < listSize; idx++) {
            UidRec uidRec = mLowLatencyUidWatchList.valueAt(idx);
            if (uidRec.mIsFg) {
                if (isScreenOnExempted) {
                    if (uidRec.mIsScreenOnExempted) uidCount++;
                } else {
                    uidCount++;
                }
            }
        }
        return uidCount;
    }

    private void setBlameHiPerfWs(WorkSource ws, boolean shouldBlame) {
        long ident = Binder.clearCallingIdentity();
        Pair<int[], String[]> uidsAndTags = WorkSourceUtil.getUidsAndTagsForWs(ws);
        try {
            if (shouldBlame) {
                mHighPerfBlamedWorkSource.add(ws);
                mBatteryStats.reportFullWifiLockAcquiredFromSource(ws);
                WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_STATE_CHANGED,
                        uidsAndTags.first, uidsAndTags.second,
                        WifiStatsLog.WIFI_LOCK_STATE_CHANGED__STATE__ON,
                        WifiStatsLog.WIFI_LOCK_STATE_CHANGED__MODE__WIFI_MODE_FULL_HIGH_PERF);
            } else {
                mBatteryStats.reportFullWifiLockReleasedFromSource(ws);
                WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_STATE_CHANGED,
                        uidsAndTags.first, uidsAndTags.second,
                        WifiStatsLog.WIFI_LOCK_STATE_CHANGED__STATE__OFF,
                        WifiStatsLog.WIFI_LOCK_STATE_CHANGED__MODE__WIFI_MODE_FULL_HIGH_PERF);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void setBlameLowLatencyUid(int uid, boolean shouldBlame) {
        long ident = Binder.clearCallingIdentity();
        try {
            if (shouldBlame) {
                mLowLatencyBlamedWorkSource.add(new WorkSource(uid));
                mBatteryStats.reportFullWifiLockAcquiredFromSource(new WorkSource(uid));
                WifiStatsLog.write_non_chained(WifiStatsLog.WIFI_LOCK_STATE_CHANGED, uid, null,
                        WifiStatsLog.WIFI_LOCK_STATE_CHANGED__STATE__ON,
                        WifiStatsLog.WIFI_LOCK_STATE_CHANGED__MODE__WIFI_MODE_FULL_LOW_LATENCY);
            } else {
                mBatteryStats.reportFullWifiLockReleasedFromSource(new WorkSource(uid));
                WifiStatsLog.write_non_chained(WifiStatsLog.WIFI_LOCK_STATE_CHANGED, uid, null,
                        WifiStatsLog.WIFI_LOCK_STATE_CHANGED__STATE__OFF,
                        WifiStatsLog.WIFI_LOCK_STATE_CHANGED__MODE__WIFI_MODE_FULL_LOW_LATENCY);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void setBlameLowLatencyWatchList(BlameReason reason, boolean shouldBlame) {
        boolean notify = false;
        for (int idx = 0; idx < mLowLatencyUidWatchList.size(); idx++) {
            UidRec uidRec = mLowLatencyUidWatchList.valueAt(idx);
            // The blame state of the UIDs should not be changed if the app is exempted from
            // screen-on and the reason for blaming is screen state change.
            if (uidRec.mIsScreenOnExempted && reason == BlameReason.SCREEN_STATE_CHANGED) {
                continue;
            }
            // Affect the blame for only UIDs running in foreground
            // UIDs running in the background are already not blamed,
            // and they should remain in that state.
            if (uidRec.mIsFg) {
                setBlameLowLatencyUid(uidRec.mUid, shouldBlame);
                notify = true;
            }
        }
        if (notify) notifyLowLatencyActiveUsersChanged();
    }

    protected synchronized void dump(PrintWriter pw) {
        pw.println("Locks acquired: "
                + mFullHighPerfLocksAcquired + " full high perf, "
                + mFullLowLatencyLocksAcquired + " full low latency");
        pw.println("Locks released: "
                + mFullHighPerfLocksReleased + " full high perf, "
                + mFullLowLatencyLocksReleased + " full low latency");

        pw.println();
        pw.println("Locks held:");
        for (WifiLock lock : mWifiLocks) {
            pw.print("    ");
            pw.println(lock);
        }
    }

    protected void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
    }

    private class WifiLock implements IBinder.DeathRecipient {
        String mTag;
        int mUid;
        IBinder mBinder;
        int mMode;
        WorkSource mWorkSource;
        long mAcqTimestamp;

        WifiLock(int lockMode, String tag, IBinder binder, WorkSource ws) {
            mTag = tag;
            mBinder = binder;
            mUid = Binder.getCallingUid();
            mMode = lockMode;
            mWorkSource = ws;
            mAcqTimestamp = mClock.getElapsedSinceBootMillis();
            try {
                mBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "mBinder.linkToDeath failed: " + e.getMessage());
                binderDied();
            }
        }

        protected WorkSource getWorkSource() {
            return mWorkSource;
        }

        protected int getUid() {
            return mUid;
        }

        protected IBinder getBinder() {
            return mBinder;
        }

        protected long getAcqTimestamp() {
            return mAcqTimestamp;
        }

        public void binderDied() {
            mHandler.post(() -> releaseLock(mBinder));
        }

        public void unlinkDeathRecipient() {
            try {
                mBinder.unlinkToDeath(this, 0);
            } catch (NoSuchElementException e) {
                Log.e(TAG, "mBinder.unlinkToDeath failed: " + e.getMessage());
            }
        }

        public String toString() {
            return "WifiLock{" + this.mTag + " type=" + this.mMode + " uid=" + mUid
                    + " workSource=" + mWorkSource + "}";
        }
    }

    private class UidRec {
        final int mUid;
        // Count of locks owned or co-owned by this UID
        int mLockCount;
        // Is this UID running in foreground or in exempted state (e.g. foreground-service)
        boolean mIsFg;
        boolean mIsFgExempted = false;
        boolean mIsScreenOnExempted = false;

        UidRec(int uid) {
            mUid = uid;
        }
    }
}
