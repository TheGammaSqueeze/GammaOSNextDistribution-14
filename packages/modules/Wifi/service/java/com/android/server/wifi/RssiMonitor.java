/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.DeviceMobilityState;
import android.util.Log;

import java.util.Arrays;

/**
 * Class for App and client mode RSSI monitoring. It processes the RSSI thresholds for these
 * monitors and enables/disables the monitoring accordingly. It also changes the RSSI polling
 * interval dynamically based on the client mode RSSI monitoring and device mobility state.
 */
public class RssiMonitor {
    private static final String TAG = "RssiMonitor";
    private boolean mVerboseLoggingEnabled = false;

    private final WifiGlobals mWifiGlobals;
    private final WifiThreadRunner mWifiThreadRunner;
    private final WifiInfo mWifiInfo;
    private final WifiNative mWifiNative;
    private final String mInterfaceName;
    private final Runnable mUpdateCapabilityRunnable;
    private final DeviceConfigFacade mDeviceConfigFacade;

    private boolean mEnableClientRssiMonitor = false;
    private boolean mIsPollRssiIntervalOverridden = false;
    private int[] mAppThresholds = {};
    private byte[] mRssiRanges = {};

    public RssiMonitor(WifiGlobals wifiGlobals, WifiThreadRunner wifiThreadRunner,
            WifiInfo wifiInfo, WifiNative wifiNative, String interfaceName,
            Runnable updateCapabilityRunnable, DeviceConfigFacade deviceConfigFacade) {
        mWifiGlobals = wifiGlobals;
        mWifiThreadRunner = wifiThreadRunner;
        mWifiInfo = wifiInfo;
        mWifiNative = wifiNative;
        mInterfaceName = interfaceName;
        mUpdateCapabilityRunnable = updateCapabilityRunnable;
        mDeviceConfigFacade = deviceConfigFacade;
    }

    private void logd(String string) {
        if (mVerboseLoggingEnabled) {
            Log.d(getTag(), string);
        }
    }

    private String getTag() {
        return TAG + "[" + (mInterfaceName == null ? "unknown" : mInterfaceName) + "]";
    }

    class RssiEventHandler implements WifiNative.WifiRssiEventHandler {
        @Override
        public void onRssiThresholdBreached(byte curRssi) {
            if (mVerboseLoggingEnabled) {
                logd("onRssiThresholdBreach event. Cur Rssi = " + curRssi);
            }
            // Corner case: if framework threshold is same as one of the app thresholds,
            // processClientRssiThresholdBreached(curRssi) is called. The framework monitor will
            // be disabled, and the RSSI monitor will be restarted with the app thresholds
            // by calling handleRssiBreachRestartRssiMonitor(). From the App monitor perspective
            // the actions are same as calling handleRssiBreachRestartRssiMonitor(curRssi) directly.
            if (mEnableClientRssiMonitor && curRssi
                    <= mWifiGlobals.getClientRssiMonitorThresholdDbm()) {
                mWifiThreadRunner.post(() -> processClientRssiThresholdBreached(curRssi));
            } else {
                mWifiThreadRunner.post(() -> handleRssiBreachRestartRssiMonitor(curRssi));
            }
        }
    }
    private final RssiEventHandler mRssiEventHandler = new RssiEventHandler();

    private void processClientRssiThresholdBreached(int curRssi) {
        int shortInterval = mWifiGlobals.getPollRssiShortIntervalMillis();
        logd("Client mode RSSI monitor threshold breach event. RSSI polling interval changed to "
                + shortInterval + " ms" + ", disable client mode RSSI monitor");
        mWifiGlobals.setPollRssiIntervalMillis(shortInterval);
        disableClientRssiMonitorAndUpdateThresholds(curRssi);
    }

    private void handleRssiBreachRestartRssiMonitor(byte curRssi) {
        if (curRssi == Byte.MAX_VALUE || curRssi == Byte.MIN_VALUE) {
            Log.wtf(getTag(), "Process RSSI thresholds: Invalid rssi " + curRssi);
            return;
        }
        for (int i = 1; i < mRssiRanges.length; i++) {
            if (curRssi < mRssiRanges[i]) {
                // Assume sorted values(ascending order) for rssi,
                // bounded by high(127) and low(-128) at extremeties
                byte maxRssi = mRssiRanges[i];
                byte minRssi = mRssiRanges[i - 1];
                // This value of hw has to be believed as this value is averaged and has breached
                // the rssi thresholds and raised event to host. This would be eggregious if this
                // value is invalid
                mWifiInfo.setRssi(curRssi);
                mUpdateCapabilityRunnable.run();
                int ret = startRssiMonitoringOffload(maxRssi, minRssi);
                logd("Re-program RSSI thresholds " + ": [" + minRssi + ", "
                        + maxRssi + "], curRssi=" + curRssi + " ret=" + ret);
                break;
            }
        }
    }

    private int startRssiMonitoringOffload(byte maxRssi, byte minRssi) {
        return mWifiNative.startRssiMonitoring(mInterfaceName, maxRssi, minRssi, mRssiEventHandler);
    }

    private int stopRssiMonitoringOffload() {
        return mWifiNative.stopRssiMonitoring(mInterfaceName);
    }

    /**
     * Reset the RSSI monitor
     */
    public void reset() {
        mEnableClientRssiMonitor = false;
        mAppThresholds = new int[] {};
        mRssiRanges = new byte[] {};
        if (!mIsPollRssiIntervalOverridden) {
            int shortInterval = mWifiGlobals.getPollRssiShortIntervalMillis();
            mWifiGlobals.setPollRssiIntervalMillis(shortInterval);
        }
        stopRssiMonitoringOffload();
    }

    /**
     * Enable/Disable verbose logging.
     * @param verbose true to enable and false to disable.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    /**
     * Update the RSSI polling interval based on the current device mobility state and RSSI.
     * If the device is stationary and RSSI is high, change to the long interval. Otherwise,
     * change to the short interval.
     * @param state the current device mobility state
     */
    public void updatePollRssiInterval(@DeviceMobilityState int state) {
        if (!mWifiGlobals.isAdjustPollRssiIntervalEnabled()
                || !mDeviceConfigFacade.isAdjustPollRssiIntervalEnabled()
                || mIsPollRssiIntervalOverridden) {
            return;
        }
        int curRssi = mWifiInfo.getRssi();
        int rssiMonitorThreshold = mWifiGlobals.getClientRssiMonitorThresholdDbm();
        int rssiMonitorHysteresisDb = mWifiGlobals.getClientRssiMonitorHysteresisDb();
        if (state == WifiManager.DEVICE_MOBILITY_STATE_STATIONARY && curRssi
                >= rssiMonitorThreshold + rssiMonitorHysteresisDb) {
            setLongPollRssiInterval();
        } else if (state != WifiManager.DEVICE_MOBILITY_STATE_STATIONARY || curRssi
                < rssiMonitorThreshold) {
            setShortPollRssiInterval();
        }
    }

    private void setLongPollRssiInterval() {
        int longInterval = mWifiGlobals.getPollRssiLongIntervalMillis();
        if (mWifiGlobals.getPollRssiIntervalMillis() == longInterval) {
            return;
        }
        logd("RSSI polling interval changed to " + longInterval + " ms"
                + ", enable client mode RSSI monitor");
        mWifiGlobals.setPollRssiIntervalMillis(longInterval);
        enableClientRssiMonitorAndUpdateThresholds(mWifiInfo.getRssi());
    }

    /**
     * Change the RSSI polling interval to the short interval and disable client mode RSSI monitor
     */
    public void setShortPollRssiInterval() {
        if (mIsPollRssiIntervalOverridden) {
            return;
        }
        int shortInterval = mWifiGlobals.getPollRssiShortIntervalMillis();
        if (mWifiGlobals.getPollRssiIntervalMillis() == shortInterval) {
            return;
        }
        logd("RSSI polling interval changed to " + shortInterval + " ms"
                + ", disable client mode RSSI monitor");
        mWifiGlobals.setPollRssiIntervalMillis(shortInterval);
        disableClientRssiMonitorAndUpdateThresholds(mWifiInfo.getRssi());
    }

    private void enableClientRssiMonitorAndUpdateThresholds(int curRssi) {
        mEnableClientRssiMonitor = true;
        updateRssiRangesAndStartMonitor(curRssi);
    }

    private void disableClientRssiMonitorAndUpdateThresholds(int curRssi) {
        mEnableClientRssiMonitor = false;
        updateRssiRangesAndStartMonitor(curRssi);
    }

    /**
     * Pass the updated App RSSI thresholds to RssiMonitor and start/restart RSSI monitoring if
     * the thresholds are valid after processing.
     */
    public void updateAppThresholdsAndStartMonitor(@NonNull int[] appThresholds) {
        logd("Received app signal strength thresholds: " + Arrays.toString(appThresholds));
        mAppThresholds = appThresholds;
        updateRssiRangesAndStartMonitor(mWifiInfo.getRssi());
    }

    private void updateRssiRangesAndStartMonitor(int curRssi) {
        // 0. If there are no thresholds, or if the thresholds are invalid,
        //    stop RSSI monitoring.
        // 1. Tell the hardware to start RSSI monitoring here, possibly adding MIN_VALUE and
        //    MAX_VALUE at the start/end of the thresholds array if necessary.
        // 2. Ensure that when the hardware event fires, we fetch the RSSI from the hardware
        //    event, call mWifiInfo.setRssi() with it, and call updateCapabilities(), and then
        //    re-arm the hardware event. This needs to be done on the state machine thread to
        //    avoid race conditions. The RSSI used to re-arm the event (and perhaps also the one
        //    sent in the NetworkCapabilities) must be the one received from the hardware event
        //    received, or we might skip callbacks.
        // 3. Ensure that when we disconnect, RSSI monitoring is stopped.
        int[] mergedThresholds = mergeAppFrameworkThresholds(mAppThresholds);
        if (mergedThresholds.length == 0) {
            mRssiRanges = new byte[] {};
            stopRssiMonitoringOffload();
            return;
        }
        int [] rssiVals = Arrays.copyOf(mergedThresholds, mergedThresholds.length + 2);
        rssiVals[rssiVals.length - 2] = Byte.MIN_VALUE;
        rssiVals[rssiVals.length - 1] = Byte.MAX_VALUE;
        Arrays.sort(rssiVals);
        byte[] rssiRange = new byte[rssiVals.length];
        for (int i = 0; i < rssiVals.length; i++) {
            int val = rssiVals[i];
            if (val <= Byte.MAX_VALUE && val >= Byte.MIN_VALUE) {
                rssiRange[i] = (byte) val;
            } else {
                Log.e(getTag(), "Illegal value " + val + " for RSSI thresholds: "
                        + Arrays.toString(rssiVals));
                stopRssiMonitoringOffload();
                return;
            }
        }
        mRssiRanges = rssiRange;
        logd("Updated RSSI thresholds=" + Arrays.toString(mRssiRanges));
        handleRssiBreachRestartRssiMonitor((byte) curRssi);
    }

    private int[] mergeAppFrameworkThresholds(@NonNull int[] appThresholds) {
        if (mEnableClientRssiMonitor) {
            int[] mergedThresholds = Arrays.copyOf(appThresholds,
                    appThresholds.length + 1);
            mergedThresholds[mergedThresholds.length - 1] = mWifiGlobals
                    .getClientRssiMonitorThresholdDbm();
            return mergedThresholds;
        } else {
            return appThresholds;
        }
    }

    /**
     * When link layer stats polling interval is overridden, set the fixed interval and disable
     * client mode RSSI monitoring if it is enabled. When the polling interval is set to automatic
     * handling, set the interval to the regular (short) interval, then the RSSI monitor will
     * adjust the interval automatically
     * @param newIntervalMs a non-negative integer, for the link layer stats polling interval
     *                      in milliseconds.
     *                      To set a fixed interval, use a positive value.
     *                      For automatic handling of the interval, use value 0
     */
    public void overridePollRssiInterval(int newIntervalMs) {
        if (mIsPollRssiIntervalOverridden && newIntervalMs == 0) {
            setAutoPollRssiInterval();
            return;
        }
        if (newIntervalMs > 0) {
            setFixedPollRssiInterval(newIntervalMs);
        }
    }

    private void setAutoPollRssiInterval() {
        mIsPollRssiIntervalOverridden = false;
        int regularInterval = mWifiGlobals.getPollRssiShortIntervalMillis();
        mWifiGlobals.setPollRssiIntervalMillis(regularInterval);
    }

    private void setFixedPollRssiInterval(int newIntervalMs) {
        mIsPollRssiIntervalOverridden = true;
        mWifiGlobals.setPollRssiIntervalMillis(newIntervalMs);
        if (mEnableClientRssiMonitor) {
            disableClientRssiMonitorAndUpdateThresholds(mWifiInfo.getRssi());
        }
    }
}
