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

package com.android.server.wifi;

import android.annotation.IntDef;
import android.content.Context;
import android.util.Log;

import com.android.server.wifi.proto.WifiStatsLog;
import com.android.wifi.resources.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * This class is used to recover the wifi stack from a fatal failure. The recovery mechanism
 * involves triggering a stack restart (essentially simulating an airplane mode toggle) using
 * {@link ActiveModeWarden}.
 * The current triggers for:
 * 1. Last resort watchdog bite.
 * 2. HAL/wificond crashes during normal operation.
 * 3. TBD: supplicant crashes during normal operation.
 */
public class SelfRecovery {
    private static final String TAG = "WifiSelfRecovery";

    /**
     * Reason codes for the various recovery triggers.
     */
    public static final int REASON_LAST_RESORT_WATCHDOG = 0;
    public static final int REASON_WIFINATIVE_FAILURE = 1;
    public static final int REASON_STA_IFACE_DOWN = 2;
    public static final int REASON_API_CALL = 3;
    public static final int REASON_SUBSYSTEM_RESTART = 4;
    public static final int REASON_IFACE_ADDED = 5;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"REASON_"}, value = {
            REASON_LAST_RESORT_WATCHDOG,
            REASON_WIFINATIVE_FAILURE,
            REASON_STA_IFACE_DOWN,
            REASON_API_CALL,
            REASON_SUBSYSTEM_RESTART,
            REASON_IFACE_ADDED})
    public @interface RecoveryReason {}

    /**
     * State for self recovery.
     */
    private static final int STATE_NO_RECOVERY = 0;
    private static final int STATE_DISABLE_WIFI = 1;
    private static final int STATE_RESTART_WIFI = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"STATE_"}, value = {
            STATE_NO_RECOVERY,
            STATE_DISABLE_WIFI,
            STATE_RESTART_WIFI})
    private @interface RecoveryState {}

    private final Context mContext;
    private final ActiveModeWarden mActiveModeWarden;
    private final Clock mClock;
    // Time since boot (in millis) that restart occurred
    private final LinkedList<Long> mPastRestartTimes;
    private final WifiNative mWifiNative;
    private final WifiGlobals mWifiGlobals;
    private int mSelfRecoveryReason;
    private long mLastSelfRecoveryTimeStampMillis = -1L;
    // Self recovery state
    private @RecoveryState int mRecoveryState;
    private SubsystemRestartListenerInternal mSubsystemRestartListener;

    /**
     * Return the recovery reason code as string.
     * @param reason the reason code
     * @return the recovery reason as string
     */
    public static String getRecoveryReasonAsString(@RecoveryReason int reason) {
        switch (reason) {
            case REASON_LAST_RESORT_WATCHDOG:
                return "Last Resort Watchdog";
            case REASON_WIFINATIVE_FAILURE:
                return "WifiNative Failure";
            case REASON_STA_IFACE_DOWN:
                return "Sta Interface Down";
            case REASON_API_CALL:
                return "API call (e.g. user)";
            case REASON_SUBSYSTEM_RESTART:
                return "Subsystem Restart";
            case REASON_IFACE_ADDED:
                return "Interface Added";
            default:
                return "Unknown " + reason;
        }
    }

    /**
     * Invoked when self recovery completed.
     */
    public void onRecoveryCompleted() {
        mRecoveryState = STATE_NO_RECOVERY;
    }

    /**
     * Invoked when Wifi is stopped with all client mode managers removed.
     */
    public void onWifiStopped() {
        if (mRecoveryState == STATE_DISABLE_WIFI) {
            onRecoveryCompleted();
        }
    }

    /**
     * Returns true if recovery is currently in progress.
     */
    public boolean isRecoveryInProgress() {
        // return true if in recovery progress
        return mRecoveryState != STATE_NO_RECOVERY;
    }

    private class SubsystemRestartListenerInternal
            implements HalDeviceManager.SubsystemRestartListener{
        private void onSubsystemRestart(@RecoveryReason int reason, long timeElapsedFromLastTrigger,
                int resultForLogging) {
            Log.e(TAG, "Restarting wifi for reason: " + getRecoveryReasonAsString(reason));
            WifiStatsLog.write(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED,
                    convertSelfRecoveryReason(reason),
                    resultForLogging,
                    timeElapsedFromLastTrigger);
            mActiveModeWarden.recoveryRestartWifi(reason,
                    reason != REASON_LAST_RESORT_WATCHDOG && reason != REASON_API_CALL);
        }

        @Override
        public void onSubsystemRestart() {
            long timeElapsedFromLastTrigger = getTimeElapsedFromLastTrigger();
            mLastSelfRecoveryTimeStampMillis = mClock.getWallClockMillis();
            if (mRecoveryState == STATE_RESTART_WIFI) {
                // If the wifi restart recovery is triggered then proceed
                onSubsystemRestart(mSelfRecoveryReason, timeElapsedFromLastTrigger,
                        WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_RESTART_STARTED_INTERNAL_RECOVERY_BY_NATIVE_CALLBACK);
            } else {
                // We did not trigger recovery, but looks like the firmware crashed?
                mRecoveryState = STATE_RESTART_WIFI;
                onSubsystemRestart(REASON_SUBSYSTEM_RESTART, timeElapsedFromLastTrigger,
                        WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_RESTART_STARTED_INTERNAL_RECOVERY_BY_NATIVE_CALLBACK);
            }
        }
    }

    public SelfRecovery(Context context, ActiveModeWarden activeModeWarden,
            Clock clock, WifiNative wifiNative, WifiGlobals wifiGlobals) {
        mContext = context;
        mActiveModeWarden = activeModeWarden;
        mClock = clock;
        mPastRestartTimes = new LinkedList<>();
        mWifiNative = wifiNative;
        mSubsystemRestartListener = new SubsystemRestartListenerInternal();
        mWifiNative.registerSubsystemRestartListener(mSubsystemRestartListener);
        mWifiGlobals = wifiGlobals;
        mRecoveryState = STATE_NO_RECOVERY;
    }

    /**
     * Trigger recovery.
     *
     * This method does the following:
     * 1. Checks reason code used to trigger recovery
     * 2. Checks for sta iface down triggers and disables wifi by sending {@link
     * ActiveModeWarden#recoveryDisableWifi()} to {@link ActiveModeWarden} to disable wifi.
     * 3. Throttles restart calls for underlying native failures
     * 4. Sends {@link ActiveModeWarden#recoveryRestartWifi(int)} to {@link ActiveModeWarden} to
     * initiate the stack restart.
     * @param reason One of the above |REASON_*| codes.
     */
    public void trigger(@RecoveryReason int reason) {
        long timeElapsedFromLastTrigger = getTimeElapsedFromLastTrigger();
        mLastSelfRecoveryTimeStampMillis = mClock.getWallClockMillis();
        if (!(reason == REASON_LAST_RESORT_WATCHDOG || reason == REASON_WIFINATIVE_FAILURE
                  || reason == REASON_STA_IFACE_DOWN || reason == REASON_API_CALL
                  || reason == REASON_IFACE_ADDED)) {
            Log.e(TAG, "Invalid trigger reason. Ignoring...");
            WifiStatsLog.write(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED,
                    convertSelfRecoveryReason(reason),
                    WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_INVALID_REASON,
                    timeElapsedFromLastTrigger);
            return;
        }
        if (reason == REASON_STA_IFACE_DOWN) {
            Log.e(TAG, "STA interface down, disable wifi");
            mActiveModeWarden.recoveryDisableWifi();
            mRecoveryState = STATE_DISABLE_WIFI;
            WifiStatsLog.write(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED,
                    convertSelfRecoveryReason(reason),
                    WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_IFACE_DOWN,
                    timeElapsedFromLastTrigger);
            return;
        }

        Log.e(TAG, "Triggering recovery for reason: " + getRecoveryReasonAsString(reason));
        if (reason == REASON_IFACE_ADDED
                && !mWifiGlobals.isWifiInterfaceAddedSelfRecoveryEnabled()) {
            Log.w(TAG, "Recovery on added interface is disabled. Ignoring...");
            WifiStatsLog.write(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED,
                    convertSelfRecoveryReason(reason),
                    WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_IFACE_ADD_DISABLED,
                    timeElapsedFromLastTrigger);
            return;
        }
        if (reason == REASON_WIFINATIVE_FAILURE) {
            int maxRecoveriesPerHour = mContext.getResources().getInteger(
                    R.integer.config_wifiMaxNativeFailureSelfRecoveryPerHour);
            if (maxRecoveriesPerHour == 0) {
                Log.e(TAG, "Recovery disabled. Disabling wifi");
                mActiveModeWarden.recoveryDisableWifi();
                mRecoveryState = STATE_DISABLE_WIFI;
                WifiStatsLog.write(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED,
                        convertSelfRecoveryReason(reason),
                        WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_RETRY_DISABLED,
                        timeElapsedFromLastTrigger);
                return;
            }
            trimPastRestartTimes();
            if (mPastRestartTimes.size() >= maxRecoveriesPerHour) {
                Log.e(TAG, "Already restarted wifi " + maxRecoveriesPerHour + " times in"
                        + " last 1 hour. Disabling wifi");
                mActiveModeWarden.recoveryDisableWifi();
                mRecoveryState = STATE_DISABLE_WIFI;
                WifiStatsLog.write(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED,
                        convertSelfRecoveryReason(reason),
                        WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_ABOVE_MAX_RETRY,
                        timeElapsedFromLastTrigger);
                return;
            }
            mPastRestartTimes.add(mClock.getElapsedSinceBootMillis());
        }

        mSelfRecoveryReason = reason;
        mRecoveryState = STATE_RESTART_WIFI;
        if (!mWifiNative.startSubsystemRestart()) {
            // HAL call failed, fallback to internal flow.
            mSubsystemRestartListener.onSubsystemRestart(reason, timeElapsedFromLastTrigger,
                    WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_RESTART_STARTED_INTERNAL_RECOVERY);
            return;
        }
        WifiStatsLog.write(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED,
                convertSelfRecoveryReason(reason),
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_RESTART_SUCCESS,
                timeElapsedFromLastTrigger);
    }

    /**
     * Process the mPastRestartTimes list, removing elements outside the max restarts time window
     */
    private void trimPastRestartTimes() {
        Iterator<Long> iter = mPastRestartTimes.iterator();
        long now = mClock.getElapsedSinceBootMillis();
        while (iter.hasNext()) {
            Long restartTimeMillis = iter.next();
            if (now - restartTimeMillis > TimeUnit.HOURS.toMillis(1)) {
                iter.remove();
            } else {
                break;
            }
        }
    }

    private int convertSelfRecoveryReason(int reason) {
        switch (reason) {
            case SelfRecovery.REASON_LAST_RESORT_WATCHDOG:
                return WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_LAST_RESORT_WDOG;
            case SelfRecovery.REASON_WIFINATIVE_FAILURE:
                return WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_WIFINATIVE_FAILURE;
            case SelfRecovery.REASON_STA_IFACE_DOWN:
                return WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_STA_IFACE_DOWN;
            case SelfRecovery.REASON_API_CALL:
                return WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_API_CALL;
            case SelfRecovery.REASON_SUBSYSTEM_RESTART:
                return WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_SUBSYSTEM_RESTART;
            case SelfRecovery.REASON_IFACE_ADDED:
                return WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_IFACE_ADDED;
            default:
                return WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_UNKNOWN;
        }
    }

    private long getTimeElapsedFromLastTrigger() {
        if (mLastSelfRecoveryTimeStampMillis < 0) {
            return -1L;
        } else {
            return (mClock.getWallClockMillis() - mLastSelfRecoveryTimeStampMillis);
        }
    }
}
