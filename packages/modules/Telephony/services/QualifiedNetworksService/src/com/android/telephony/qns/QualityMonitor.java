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

import android.content.Context;
import android.util.Log;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class manages threshold information registered from AccessNetworkEvaluator It monitors
 * Cellular or WiFi qualities(WiFi RSSI, 4g/5g RSRP , 3G RSCP , 2G RSSI etc..) and report event if
 * the network quality changes over the threshold value.
 */
abstract class QualityMonitor {
    private static final int BASE = 1000;
    protected static final int EVENT_CELLULAR_QNS_TELEPHONY_INFO_CHANGED = BASE + 1;
    protected static final int EVENT_WIFI_RSSI_CHANGED = BASE + 2;
    protected static final int EVENT_WIFI_STATE_CHANGED = BASE + 3;
    protected static final int EVENT_WIFI_NOTIFY_TIMER_EXPIRED = BASE + 4;
    protected static final int EVENT_SUBSCRIPTION_ID_CHANGED = BASE + 5;
    private final String mTag;
    protected Context mContext;
    protected final HashMap<String, IThresholdListener> mThresholdCallbackMap = new HashMap<>();
    protected final ConcurrentHashMap<String, List<Threshold>> mThresholdsList =
            new ConcurrentHashMap<>();
    protected final HashMap<String, Boolean> mWaitingThresholds = new HashMap<>();

    /** To-Do: For future use. */
    protected QualityMonitor(String tag) {
        mTag = tag;
    }

    /** Get current Quality based on access network & measurement type */
    abstract int getCurrentQuality(int accessNetwork, int measurementType);

    /** This method registers the thresholds to monitor the signal strengths */
    void registerThresholdChange(
            ThresholdCallback thresholdCallback,
            int netCapability,
            Threshold[] ths,
            int slotIndex) {
        String key = getKey(netCapability, slotIndex);
        Log.d(mTag, "Registering for slotIndex=[" + slotIndex + "], key=[" + key + "]");
        mThresholdCallbackMap.put(key, thresholdCallback.mCallback);
    }

    /** Unregister the Cellular & Wifi Quality threshold */
    void unregisterThresholdChange(int netCapability, int slotIndex) {
        String key = getKey(netCapability, slotIndex);
        Log.d(mTag, "Unregister threshold change for key=[" + key + "]");
        mThresholdCallbackMap.remove(key);
        mThresholdsList.remove(key);
        mWaitingThresholds.remove(key);
    }

    /**
     * It replace/set the new threshold values to listen for the given netCapability.
     *
     * @param netCapability Network Capability for which new thresholds are updated
     * @param slotIndex slot id
     * @param ths updated thresholds array. If ths is empty; if thresholds are registered for given
     *     netCapability, it will be cleared and removed from registered list.
     */
    void updateThresholdsForNetCapability(int netCapability, int slotIndex, Threshold[] ths) {
        String key = getKey(netCapability, slotIndex);
        if (mThresholdCallbackMap.get(key) == null) {
            throw new IllegalStateException(
                    "For the netCapability = "
                            + netCapability
                            + "["
                            + slotIndex
                            + "], no callback is registered");
        }
        if (ths == null || ths.length == 0) {
            mThresholdsList.remove(key);
            mWaitingThresholds.remove(key);
        } else {
            mThresholdsList.put(key, new ArrayList<>(List.of(ths)));
        }
        Log.d(mTag, "Thresholds stored: " + mThresholdsList);
    }

    protected abstract void notifyThresholdChange(String key, Threshold[] ths);

    /**
     * This method provides unique key to store the hashmap values and need to optimize in future.
     */
    protected String getKey(int netCapability, int slotIndex) {
        return netCapability + "_" + slotIndex;
    }

    public void close() {
        mThresholdsList.clear();
        mWaitingThresholds.clear();
        mThresholdCallbackMap.clear();
    }

    /**
     * Dumps the state of {@link QualityMonitor}
     *
     * @param pw {@link PrintWriter} to write the state of the object.
     * @param prefix String to append at start of dumped log.
     */
    void dump(PrintWriter pw, String prefix) {
        pw.println(prefix + "mThresholdCallbackMap=" + mThresholdCallbackMap);
        pw.println(prefix + "mThresholdsList=" + mThresholdsList);
        pw.println(prefix + "mWaitingThresholds=" + mWaitingThresholds);
    }
}
