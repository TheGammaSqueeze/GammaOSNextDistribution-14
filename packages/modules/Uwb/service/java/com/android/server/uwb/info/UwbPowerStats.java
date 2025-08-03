/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.server.uwb.info;

/**
 * Power related status reported by the UWB subsystem.
 * All values should never decrease after the start of subsystem.
 */
public class UwbPowerStats {
    private static final String TAG = UwbPowerStats.class.getSimpleName();

    /**
     * The duration of UWB operating in the idle mode (neither Tx nor Rx).
     * For the HW with very low idle current, it may not be meaningful to maintain this
     * count and thus the value could be always zero.
     */
    private int mIdleTimeMs;

    /**
     * The duration of UWB operating in the Tx mode in millis.
     * This may include time for HW configuration, ramp up and down.
     */
    private int mTxTimeMs;

    /**
     * The duration of UWB operating in the Rx mode in millis.
     * This may include time for HW configuration and listen mode.
     */
    private int mRxTimeMs;

    /**
     * Total count of host wakeup due to UWB subsystem event.
     */
    private int mTotalWakeCount;

    public UwbPowerStats(int idleTimeMs, int txTimeMs, int rxTimeMs, int totalWakeCount) {
        mIdleTimeMs = idleTimeMs;
        mTxTimeMs = txTimeMs;
        mRxTimeMs = rxTimeMs;
        mTotalWakeCount = totalWakeCount;
    }

    /**
     * get total idle time in millis
     */
    public int getIdleTimeMs() {
        return mIdleTimeMs;
    }

    /**
     * get total Tx time in millis
     */
    public int getTxTimeMs() {
        return mTxTimeMs;
    }

    /**
     * get total Rx time in millis
     */
    public int getRxTimeMs() {
        return mRxTimeMs;
    }

    /**
     * get total wakeup count
     */
    public int getTotalWakeCount() {
        return mTotalWakeCount;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("UwbPowerStats: idle_time_ms=").append(mIdleTimeMs)
                .append(" tx_time_ms=").append(mTxTimeMs)
                .append(" rx_time_ms=").append(mRxTimeMs)
                .append(" total_wake_count=").append(mTotalWakeCount);
        return sb.toString();
    }
}
