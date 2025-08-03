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

package com.android.server.wifi;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * A class representing signal poll results collected over multiple links.
 */
public class WifiSignalPollResults {
    public static String TAG = "WifiSignalPollResults";
    public static final int MIN_RSSI = -127;
    /* Maximum of 15 entries possible. */
    public static final int MAX_ENTRIES = 15;

    WifiSignalPollResults() {
        mEntries = new HashMap();
        mBestLinkId = 0;
        mDefault = new SignalPollResult(0, MIN_RSSI, 0, 0, 0);
    }
    static class SignalPollResult {
        SignalPollResult(int linkId, int currentRssiDbm, int txBitrateMbps,
                int rxBitrateMbps, int frequencyMHz) {
            this.linkId = linkId;
            this.currentRssiDbm = currentRssiDbm;
            this.txBitrateMbps = txBitrateMbps;
            this.rxBitrateMbps = rxBitrateMbps;
            this.frequencyMHz = frequencyMHz;
        }

        /**
         * Link identifier.
         */
        public final int linkId;

        /**
         * RSSI value in dBM.
         */
        public final int currentRssiDbm;

        /**
         * Last transmitted bit rate in Mbps.
         */
        public final int txBitrateMbps;

        /**
         * Last received packet bit rate in Mbps.
         */
        public final int rxBitrateMbps;

        /**
         * Frequency in MHz.
         */
        public final int frequencyMHz;
    }

    /* Signal poll result entries. Maps linkId to SignalPollResult. */
    private Map<Integer, SignalPollResult> mEntries;
    /* Link id of the best link. */
    private int mBestLinkId;
    /* Default entry with default values. */
    private SignalPollResult mDefault;

    /**
     * Add a new entry of signal poll result.
     *
     * @param linkId         Identifier of the link (0 - 14).
     * @param currentRssiDbm Current RSSI in dBm.
     * @param txBitRateMbps  Transmit bit rate of the link.
     * @param rxBitRateMbps  Receive bit rate of the link.
     * @param frequencyMHz   Frequency of the link.
     */
    public void addEntry(int linkId, int currentRssiDbm, int txBitRateMbps, int rxBitRateMbps,
            int frequencyMHz) {
        if (mEntries.size() > MAX_ENTRIES) {
            Log.e(TAG, "addEntry: failed, reached maximum entries " + MAX_ENTRIES);
            return;
        }
        // Update the best link id.
        if (mEntries.size() == 0 || currentRssiDbm > mEntries.get(
                mBestLinkId).currentRssiDbm) {
            mBestLinkId = linkId;
        }
        // Add a new Entry.
        mEntries.put(linkId,
                new SignalPollResult(linkId, currentRssiDbm, txBitRateMbps, rxBitRateMbps,
                        frequencyMHz));

    }

    /**
     * Get current RSSI. In case of multi links, return the maximum RSSI (best link).
     *
     * @return rssi in dBm or {@link WifiSignalPollResults#MIN_RSSI} if no poll results.
     */
    public int getRssi() {
        return mEntries.getOrDefault(mBestLinkId, mDefault).currentRssiDbm;
    }

    /**
     * Get current RSSI of the link.
     *
     * @param linkId Identifier of the link.
     * @return rssi in dBm or {@link WifiSignalPollResults#MIN_RSSI} if link is not present.
     */
    public int getRssi(int linkId) {
        return mEntries.getOrDefault(linkId, mDefault).currentRssiDbm;
    }

    /**
     * Get transmit link speed in Mbps. In case of multi links, return the rate of the best link.
     *
     * @return tx link speed in Mpbs or 0 if no poll results.
     */
    public int getTxLinkSpeed() {
        return mEntries.getOrDefault(mBestLinkId, mDefault).txBitrateMbps;
    }

    /**
     * Get transmit link speed in Mbps of the link.
     *
     * @param linkId Identifier of the link.
     * @return tx link speed in Mpbs or 0 if no poll results.
     */
    public int getTxLinkSpeed(int linkId) {
        return mEntries.getOrDefault(linkId, mDefault).txBitrateMbps;
    }

    /**
     * Get receive link speed in Mbps. In case of multi links, return the rate of the best link.
     *
     * @return rx link speed in Mpbs or 0 if no poll results.
     */
    public int getRxLinkSpeed() {
        return mEntries.getOrDefault(mBestLinkId, mDefault).rxBitrateMbps;
    }

    /**
     * Get receive link speed in Mbps of the link.
     *
     * @param linkId Identifier of the link.
     * @return rx link speed in Mpbs or 0 if no poll results.
     */
    public int getRxLinkSpeed(int linkId) {
        return mEntries.getOrDefault(linkId, mDefault).rxBitrateMbps;
    }

    /**
     * Get frequency. In case of multi links, return frequency of the best link.
     *
     * @return frequency in MHz or 0 if no poll results.
     */
    public int getFrequency() {
        return mEntries.getOrDefault(mBestLinkId, mDefault).frequencyMHz;
    }

    /**
     * Get frequency of the link.
     *
     * @param linkId Identifier of the link.
     * @return frequency in MHz or 0 if no poll results.
     */
    public int getFrequency(int linkId) {
        return mEntries.getOrDefault(linkId, mDefault).frequencyMHz;
    }

    /**
     * Return whether the poll results available for the specific link.
     *
     * @param linkId Identifier of the link.
     * @return true if available, otherwise false
     */
    public boolean isAvailable(int linkId) {
        return mEntries.containsKey(linkId);
    }
}
