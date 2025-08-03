/*
 * Copyright 2017 The Android Open Source Project
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
import android.net.wifi.MloLink;
import android.net.wifi.WifiInfo;

/**
 * Extends WifiInfo with the methods for computing the averaged packet rates
 */
public class ExtendedWifiInfo extends WifiInfo {
    private static final long RESET_TIME_STAMP = Long.MIN_VALUE;
    private static final double FILTER_TIME_CONSTANT = 3000.0;
    private static final int SOURCE_UNKNOWN = 0;
    private static final int SOURCE_TRAFFIC_COUNTERS = 1;
    private static final int SOURCE_LLSTATS = 2;

    private final WifiGlobals mWifiGlobals;
    private final String mIfaceName;

    private int mLastSource = SOURCE_UNKNOWN;
    private long mLastPacketCountUpdateTimeStamp = RESET_TIME_STAMP;

    ExtendedWifiInfo(WifiGlobals wifiGlobals, String ifaceName) {
        mWifiGlobals = wifiGlobals;
        mIfaceName = ifaceName;
    }

    @Override
    public void reset() {
        super.reset();
        mLastSource = SOURCE_UNKNOWN;
        mLastPacketCountUpdateTimeStamp = RESET_TIME_STAMP;
        if (mWifiGlobals.isConnectedMacRandomizationEnabled()) {
            setMacAddress(DEFAULT_MAC_ADDRESS);
        }
    }

    /**
     * Updates the packet rates using link layer stats
     *
     * @param stats WifiLinkLayerStats
     * @param timeStamp time in milliseconds
     */
    public void updatePacketRates(@NonNull WifiLinkLayerStats stats, long timeStamp) {
        long txgood = stats.txmpdu_be + stats.txmpdu_bk + stats.txmpdu_vi + stats.txmpdu_vo;
        long txretries = stats.retries_be + stats.retries_bk + stats.retries_vi + stats.retries_vo;
        long txbad = stats.lostmpdu_be + stats.lostmpdu_bk + stats.lostmpdu_vi + stats.lostmpdu_vo;
        long rxgood = stats.rxmpdu_be + stats.rxmpdu_bk + stats.rxmpdu_vi + stats.rxmpdu_vo;
        updateWifiInfoRates(SOURCE_LLSTATS, txgood, txretries, txbad, rxgood, timeStamp);
        // Process link stats if available.
        if (stats.links == null) return;
        for (WifiLinkLayerStats.LinkSpecificStats link : stats.links) {
            updateMloRates(
                    link.link_id,
                    SOURCE_LLSTATS,
                    link.txmpdu_be + link.txmpdu_bk + link.txmpdu_vi + link.txmpdu_vo,
                    link.retries_be + link.retries_bk + link.retries_vi + link.retries_vo,
                    link.lostmpdu_be + link.lostmpdu_bk + link.lostmpdu_vi + link.lostmpdu_vo,
                    link.rxmpdu_be + link.rxmpdu_bk + link.rxmpdu_vi + link.rxmpdu_vo,
                    timeStamp);
        }
    }

    /**
     * This function is less powerful and used if the WifiLinkLayerStats API is not implemented
     * at the Wifi HAL
     */
    public void updatePacketRates(long txPackets, long rxPackets, long timeStamp) {
        updateWifiInfoRates(SOURCE_TRAFFIC_COUNTERS, txPackets, 0, 0, rxPackets, timeStamp);
    }

    private void updateMloRates(
            int linkId,
            int source,
            long txgood,
            long txretries,
            long txbad,
            long rxgood,
            long timeStamp) {
        MloLink link = getAffiliatedMloLink(linkId);
        if (link == null) return;
        if (source == mLastSource
                && link.lastPacketCountUpdateTimeStamp != RESET_TIME_STAMP
                && link.lastPacketCountUpdateTimeStamp < timeStamp
                && link.txBad <= txbad
                && link.txSuccess <= txgood
                && link.rxSuccess <= rxgood
                && link.txRetries <= txretries) {
            long timeDelta = timeStamp - link.lastPacketCountUpdateTimeStamp;
            double lastSampleWeight = Math.exp(-1.0 * timeDelta / FILTER_TIME_CONSTANT);
            double currentSampleWeight = 1.0 - lastSampleWeight;

            link.setLostTxPacketsPerSecond(
                    link.getLostTxPacketsPerSecond() * lastSampleWeight
                            + (txbad - link.txBad) * 1000.0 / timeDelta * currentSampleWeight);
            link.setSuccessfulTxPacketsPerSecond(
                    link.getSuccessfulTxPacketsPerSecond() * lastSampleWeight
                            + (txgood - link.txSuccess) * 1000.0 / timeDelta * currentSampleWeight);
            link.setSuccessfulRxPacketsPerSecond(
                    link.getSuccessfulRxPacketsPerSecond() * lastSampleWeight
                            + (rxgood - link.rxSuccess) * 1000.0 / timeDelta * currentSampleWeight);
            link.setRetriedTxPacketsRate(
                    link.getRetriedTxPacketsPerSecond() * lastSampleWeight
                            + (txretries - link.txRetries)
                                    * 1000.0
                                    / timeDelta
                                    * currentSampleWeight);
        } else {
            link.setLostTxPacketsPerSecond(0);
            link.setSuccessfulTxPacketsPerSecond(0);
            link.setSuccessfulRxPacketsPerSecond(0);
            link.setRetriedTxPacketsRate(0);
            mLastSource = source;
        }
        link.txBad = txbad;
        link.txSuccess = txgood;
        link.rxSuccess = rxgood;
        link.txRetries = txretries;
        link.lastPacketCountUpdateTimeStamp = timeStamp;
    }

    private void updateWifiInfoRates(
            int source, long txgood, long txretries, long txbad, long rxgood, long timeStamp) {
        if (source == mLastSource
                && mLastPacketCountUpdateTimeStamp != RESET_TIME_STAMP
                && mLastPacketCountUpdateTimeStamp < timeStamp
                && txBad <= txbad
                && txSuccess <= txgood
                && rxSuccess <= rxgood
                && txRetries <= txretries) {
            long timeDelta = timeStamp - mLastPacketCountUpdateTimeStamp;
            double lastSampleWeight = Math.exp(-1.0 * timeDelta / FILTER_TIME_CONSTANT);
            double currentSampleWeight = 1.0 - lastSampleWeight;

            setLostTxPacketsPerSecond(getLostTxPacketsPerSecond() * lastSampleWeight
                    + (txbad - txBad) * 1000.0 / timeDelta
                    * currentSampleWeight);
            setSuccessfulTxPacketsPerSecond(getSuccessfulTxPacketsPerSecond() * lastSampleWeight
                    + (txgood - txSuccess) * 1000.0 / timeDelta
                    * currentSampleWeight);
            setSuccessfulRxPacketsPerSecond(getSuccessfulRxPacketsPerSecond() * lastSampleWeight
                    + (rxgood - rxSuccess) * 1000.0 / timeDelta
                    * currentSampleWeight);
            setRetriedTxPacketsRate(getRetriedTxPacketsPerSecond() * lastSampleWeight
                    + (txretries - txRetries) * 1000.0 / timeDelta
                    * currentSampleWeight);
        } else {
            setLostTxPacketsPerSecond(0);
            setSuccessfulTxPacketsPerSecond(0);
            setSuccessfulRxPacketsPerSecond(0);
            setRetriedTxPacketsRate(0);
            mLastSource = source;
        }
        txBad = txbad;
        txSuccess = txgood;
        rxSuccess = rxgood;
        txRetries = txretries;
        mLastPacketCountUpdateTimeStamp = timeStamp;
    }

    public String getIfaceName() {
        return mIfaceName;
    }
}
