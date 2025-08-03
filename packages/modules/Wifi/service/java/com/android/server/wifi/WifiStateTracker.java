/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.os.BatteryStatsManager;
import android.util.ArrayMap;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * This class is used to track WifiState to update BatteryStats
 */
public class WifiStateTracker {
    private static final String TAG = "WifiStateTracker";

    @IntDef(value = {
            INVALID,
            SCAN_MODE,
            DISCONNECTED,
            CONNECTED,
            SOFT_AP,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface WifiStateTrackerState {}

    public static final int INVALID = -1;
    public static final int SCAN_MODE = 1; // unused
    public static final int DISCONNECTED = 2;
    public static final int CONNECTED = 3;
    public static final int SOFT_AP = 4; // unused

    /**
     * String key: interface name
     * int value: last wifi state, one of the {@link WifiStateTrackerState} values.
     */
    private final Map<String, Integer> mIfaceToWifiState = new ArrayMap<>();
    private final BatteryStatsManager mBatteryStatsManager;

    public WifiStateTracker(BatteryStatsManager batteryStatsManager) {
        mBatteryStatsManager = batteryStatsManager;
    }

    @WifiStateTrackerState
    private int mLastReportedState = INVALID;

    private void informWifiStateBatteryStats(@WifiStateTrackerState int state) {
        if (state == mLastReportedState) {
            return;
        }
        mLastReportedState = state;

        int batteryStatsState = internalToBatteryStatsWifiState(state);
        if (batteryStatsState == INVALID) {
            return;
        }

        try {
            mBatteryStatsManager.reportWifiState(batteryStatsState, null);
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Battery stats executor is being shutdown " + e.getMessage());
        }
    }

    /**
     * Reports the most active state among Wifi ifaces. e.g. Connected is more active than
     * disconnected.
     */
    @WifiStateTrackerState
    private int getHighestPriorityState() {
        int highest = INVALID;
        for (int i : mIfaceToWifiState.values()) {
            highest = Math.max(highest, i);
        }
        return highest;
    }

    /**
     * Inform the WifiState to this tracker to translate into the
     * WifiState corresponding to BatteryStatsManager.
     * @param ifaceName iface name whose state has changed
     * @param state state corresponding to the ClientModeImpl state, one of the
     *        {@link WifiStateTrackerState} values.
     */
    public void updateState(String ifaceName, @WifiStateTrackerState int state) {
        mIfaceToWifiState.put(ifaceName, state);
        int highestPriorityState = getHighestPriorityState();
        informWifiStateBatteryStats(highestPriorityState);
    }

    private static int internalToBatteryStatsWifiState(@WifiStateTrackerState int internal) {
        switch (internal) {
            case SCAN_MODE:
                return BatteryStatsManager.WIFI_STATE_OFF_SCANNING;
            case DISCONNECTED:
                return BatteryStatsManager.WIFI_STATE_ON_DISCONNECTED;
            case CONNECTED:
                return BatteryStatsManager.WIFI_STATE_ON_CONNECTED_STA;
            case SOFT_AP:
                return BatteryStatsManager.WIFI_STATE_SOFT_AP;
            case INVALID:
            default:
                return INVALID;
        }
    }
}
