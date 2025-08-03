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
package com.android.telephony.qns;

import static android.net.NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import static com.android.telephony.qns.QnsConstants.THRESHOLD_EQUAL_OR_LARGER;

import android.annotation.NonNull;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.SignalThresholdInfo;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class manages threshold information registered from AccessNetworkEvaluator It is intended to
 * monitor Wi-Fi qualities(Wi-Fi RSSI, WIFI PER) & report event if the network quality changes over
 * the threshold value.
 */
public class WifiQualityMonitor extends QualityMonitor {
    private final String mTag;
    private final Context mContext;
    private final WifiManager mWifiManager;
    private final ConnectivityManager mConnectivityManager;
    private final WiFiThresholdCallback mWiFiThresholdCallback;
    private final NetworkRequest.Builder mBuilder;
    private final QnsTimer mQnsTimer;
    private final List<Integer> mTimerIds;

    private int mWifiRssi;
    @VisibleForTesting Handler mHandler;
    private int mRegisteredThreshold = SIGNAL_STRENGTH_UNSPECIFIED;
    private static final int BACKHAUL_TIMER_DEFAULT = 3000;
    static final int INVALID_RSSI = -127;
    private boolean mIsRegistered = false;
    private boolean mIsBackhaulRunning;

    private class WiFiThresholdCallback extends ConnectivityManager.NetworkCallback {
        /** Callback Received based on meeting Wifi RSSI Threshold Registered or Wifi Lost */
        @Override
        public void onCapabilitiesChanged(
                @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities);
            Log.d(
                    mTag,
                    "onCapabilitiesChanged wlan network="
                            + network
                            + ", capabilities="
                            + networkCapabilities);
            if (networkCapabilities != null) {
                mWifiRssi = networkCapabilities.getSignalStrength();
                Log.d(mTag, "onCapabilitiesChanged_rssi: " + mWifiRssi);
                validateWqmStatus(mWifiRssi);
            }
        }

        /** Called when current threshold goes below the threshold set. */
        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
            mWifiRssi = getCurrentQuality(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI);
            Log.d(mTag, "onLost_rssi=" + mWifiRssi);
            validateWqmStatus(mWifiRssi);
        }
    }

    @VisibleForTesting
    synchronized void validateWqmStatus(int wifiRssi) {
        if (isWifiRssiValid(wifiRssi)) {
            Log.d(mTag, "Registered Threshold @ Wqm Status check =" + mRegisteredThreshold);
            mHandler.obtainMessage(EVENT_WIFI_RSSI_CHANGED, wifiRssi, 0).sendToTarget();
        } else {
            Log.d(mTag, "Cancel backhaul if running for invalid SS received");
            clearBackHaulTimer();
        }
    }

    private boolean isWifiRssiValid(int wifiRssi) {
        if (mRegisteredThreshold != SIGNAL_STRENGTH_UNSPECIFIED
                && wifiRssi < 0
                && wifiRssi > SIGNAL_STRENGTH_UNSPECIFIED
                && wifiRssi != INVALID_RSSI) {
            Log.d(mTag, "rssi under check is valid");
            return true;
        }
        return false;
    }

    private void clearBackHaulTimer() {
        Log.d(mTag, "Stop all active backhaul timers");
        for (int timerId : mTimerIds) {
            mQnsTimer.unregisterTimer(timerId);
        }
        mTimerIds.clear();
        mWaitingThresholds.clear();
    }

    /**
     * Create WifiQualityMonitor object for accessing WifiManager, ConnectivityManager to monitor
     * RSSI, build parameters for registering threshold & callback listening.
     */
    WifiQualityMonitor(Context context, QnsTimer qnsTimer) {
        super(QualityMonitor.class.getSimpleName() + "-I");
        mTag = WifiQualityMonitor.class.getSimpleName() + "-I";
        mContext = context;
        mQnsTimer = qnsTimer;
        mTimerIds = new ArrayList<>();
        HandlerThread handlerThread = new HandlerThread(mTag);
        handlerThread.start();
        mHandler = new WiFiEventsHandler(handlerThread.getLooper());

        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mWifiManager = mContext.getSystemService(WifiManager.class);
        if (mConnectivityManager == null) {
            Log.e(mTag, "Failed to get Connectivity Service");
        }
        if (mWifiManager == null) {
            Log.e(mTag, "Failed to get WiFi Service");
        }
        /* Network Callback for Threshold Register. */
        mWiFiThresholdCallback = new WiFiThresholdCallback();
        mBuilder =
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                        .addTransportType(TRANSPORT_WIFI);
    }

    /** Returns current Wifi RSSI information */
    @Override
    synchronized int getCurrentQuality(int accessNetwork, int measurementType) {
        return getCurrentQuality(measurementType);
    }

    // To-do:  To be handled for more Measurement types(e.g. WiFi PER).
    private int getCurrentQuality(int measurementType) {
        // TODO getConnectionInfo is deprecated.
        return mWifiManager.getConnectionInfo().getRssi();
    }

    /**
     * Register for threshold to receive callback based on criteria met, using WiFiThresholdCallback
     */
    @Override
    synchronized void registerThresholdChange(
            ThresholdCallback thresholdCallback,
            int netCapability,
            Threshold[] ths,
            int slotIndex) {
        Log.d(
                mTag,
                "registerThresholds for netCapability="
                        + QnsUtils.getNameOfNetCapability(netCapability));
        super.registerThresholdChange(thresholdCallback, netCapability, ths, slotIndex);
        updateThresholdsForNetCapability(netCapability, slotIndex, ths);
    }

    // To-do:  To be handled for more Measurement types(e.g. WiFi PER)
    @Override
    synchronized void unregisterThresholdChange(int netCapability, int slotIndex) {
        super.unregisterThresholdChange(netCapability, slotIndex);
        checkForThresholdRegistration();
    }

    @Override
    synchronized void updateThresholdsForNetCapability(
            int netCapability, int slotIndex, Threshold[] ths) {
        super.updateThresholdsForNetCapability(netCapability, slotIndex, ths);
        checkForThresholdRegistration();
    }

    @Override
    protected void notifyThresholdChange(String key, Threshold[] ths) {
        IThresholdListener listener = mThresholdCallbackMap.get(key);
        Log.d(mTag, "Notify Threshold Change to listener = " + listener);
        if (listener != null) {
            listener.onWifiThresholdChanged(ths);
        }
    }

    private void checkForThresholdRegistration() {
        // Current check is on measurement type as RSSI
        // Future to be enhanced for WiFi PER.
        int newThreshold = SIGNAL_STRENGTH_UNSPECIFIED;
        for (Map.Entry<String, List<Threshold>> entry : mThresholdsList.entrySet()) {
            for (Threshold t : entry.getValue()) {
                if (t.getMeasurementType() == SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI) {
                    // check ROVE IN cases:
                    if (t.getMatchType() == THRESHOLD_EQUAL_OR_LARGER) {
                        if (newThreshold > t.getThreshold()
                                || newThreshold == SIGNAL_STRENGTH_UNSPECIFIED) {
                            newThreshold = t.getThreshold();
                        }
                        // other ROVE OUT cases:
                    } else if (newThreshold < t.getThreshold()) {
                        newThreshold = t.getThreshold();
                    }
                }
            }
        }

        Log.d(
                mTag,
                "Registered threshold = "
                        + mRegisteredThreshold
                        + ", new threshold = "
                        + newThreshold);
        if (newThreshold != mRegisteredThreshold) {
            mRegisteredThreshold = newThreshold;
            updateRequest(mRegisteredThreshold != SIGNAL_STRENGTH_UNSPECIFIED);
        }
    }

    private void validateForWifiBackhaul(int wifiRssi) {
        mIsBackhaulRunning = false;
        for (Map.Entry<String, List<Threshold>> entry : mThresholdsList.entrySet()) {
            if (mWaitingThresholds.getOrDefault(entry.getKey(), false)) {
                continue;
            }
            for (Threshold th : entry.getValue()) {
                if (th.isMatching(wifiRssi)) {
                    Log.d(mTag, "RSSI matched for threshold = " + th);
                    handleMatchingThreshold(entry.getKey(), th, wifiRssi);
                }
            }
        }
    }

    private void handleMatchingThreshold(String key, Threshold th, int wifiRssi) {
        int backhaul = th.getWaitTime();
        if (backhaul < 0 && th.getMatchType() != QnsConstants.THRESHOLD_EQUAL_OR_SMALLER) {
            backhaul = BACKHAUL_TIMER_DEFAULT;
        }
        if (backhaul > 0) {
            mWaitingThresholds.put(key, true);
            Log.d(mTag, "Starting backhaul timer = " + backhaul);
            if (!mIsBackhaulRunning) {
                mTimerIds.add(
                        mQnsTimer.registerTimer(
                                Message.obtain(mHandler, EVENT_WIFI_NOTIFY_TIMER_EXPIRED),
                                backhaul));
                mIsBackhaulRunning = true;
            }
        } else {
            Log.d(mTag, "Notify for RSSI Threshold Registered w/o Backhaul = " + backhaul);
            checkAndNotifySignalStrength(key, wifiRssi);
        }
    }

    private void validateThresholdsAfterBackHaul(int wifiRssi) {
        mWaitingThresholds.clear();
        for (Map.Entry<String, List<Threshold>> entry : mThresholdsList.entrySet()) {
            checkAndNotifySignalStrength(entry.getKey(), wifiRssi);
        }
    }

    private void checkAndNotifySignalStrength(String key, int wifiRssi) {
        List<Threshold> thresholdsList = mThresholdsList.get(key);
        if (thresholdsList == null) return;
        Log.d(mTag, "checkAndNotifySignalStrength for " + thresholdsList);
        List<Threshold> matchedThresholds = new ArrayList<>();
        Threshold threshold;
        for (Threshold th : thresholdsList) {
            if (th.isMatching(wifiRssi)) {
                threshold = th.copy();
                threshold.setThreshold(wifiRssi);
                matchedThresholds.add(threshold);
            }
        }
        if (matchedThresholds.size() > 0) {
            notifyThresholdChange(key, matchedThresholds.toArray(new Threshold[0]));
        }
    }

    private void updateRequest(boolean register) {
        if (!register) {
            unregisterCallback();
            if (mThresholdsList.isEmpty()) {
                clearBackHaulTimer();
            }
        } else {
            Log.d(mTag, "Listening to threshold = " + mRegisteredThreshold);
            mBuilder.setSignalStrength(mRegisteredThreshold);
            registerCallback();
        }
    }

    private void unregisterCallback() {
        if (mIsRegistered) {
            Log.d(mTag, "Unregister callbacks");
            mIsRegistered = false;
            mConnectivityManager.unregisterNetworkCallback(mWiFiThresholdCallback);
        }
    }

    private void registerCallback() {
        unregisterCallback();
        if (!mIsRegistered) {
            Log.d(mTag, "Register callbacks");
            mConnectivityManager.registerNetworkCallback(mBuilder.build(), mWiFiThresholdCallback);
            mIsRegistered = true;
        }
    }

    @VisibleForTesting
    int getRegisteredThreshold() {
        return mRegisteredThreshold;
    }

    private class WiFiEventsHandler extends Handler {
        WiFiEventsHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d(mTag, "handleMessage what = " + msg.what);
            switch (msg.what) {
                case EVENT_WIFI_RSSI_CHANGED:
                    Log.d(mTag, "start validating for rssi = " + msg.arg1);
                    validateForWifiBackhaul(msg.arg1);
                    break;
                case EVENT_WIFI_NOTIFY_TIMER_EXPIRED:
                    mWifiRssi = getCurrentQuality(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI);
                    Log.d(mTag, "Backhaul timer expired, wifi rssi = " + mWifiRssi);
                    if (isWifiRssiValid(mWifiRssi)) {
                        validateThresholdsAfterBackHaul(mWifiRssi);
                    }
                    break;
                default:
                    Log.d(mTag, "Not Handled !");
            }
        }
    }

    @VisibleForTesting
    @Override
    public void close() {
        unregisterCallback();
        mWifiRssi = SIGNAL_STRENGTH_UNSPECIFIED;
        mIsRegistered = false;
        mRegisteredThreshold = SIGNAL_STRENGTH_UNSPECIFIED;
        Log.d(mTag, "closed WifiQualityMonitor");
    }

    @Override
    void dump(PrintWriter pw, String prefix) {
        pw.println(prefix + "------------------------------");
        pw.println(prefix + "WifiQualityMonitor:");
        super.dump(pw, prefix);
        pw.println(
                prefix
                        + ", mIsRegistered="
                        + mIsRegistered
                        + ", mIsBackhaulRunning="
                        + mIsBackhaulRunning);
        pw.println(
                prefix
                        + "mWifiRssi="
                        + mWifiRssi
                        + ", mRegisteredThreshold="
                        + mRegisteredThreshold);
    }
}
