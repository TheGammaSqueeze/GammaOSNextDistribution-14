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

package com.android.server.wifi.rtt;

import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.ResponderConfig;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for the Rtt unit test suite.
 */
public class RttTestUtils {
    /**
     * Compare the two lists and return true for equality, false otherwise. The two lists are
     * considered identical if they have the same number of elements and contain equal elements
     * (equality of elements using the equal() operator of the component objects).
     *
     * Note: null != empty list
     */
    public static boolean compareListContentsNoOrdering(List a, List b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false; // at this point they're not both null
        }
        if (a.size() != b.size()) { // at this point neither is null
            return false;
        }
        return a.containsAll(b) && b.containsAll(a);
    }

    /**
     * Returns a placeholder ranging request with 3 requests and a non-default in-range burst size:
     * - First: 802.11mc capable
     * - Second: 802.11mc not capable
     * - Third: Aware peer
     */
    public static RangingRequest getDummyRangingRequest(byte lastMacByte) {
        RangingRequest.Builder builder = new RangingRequest.Builder();

        ScanResult scan1 = new ScanResult();
        scan1.BSSID = "00:01:02:03:04:" + String.format("%02d", lastMacByte);
        scan1.setFlag(ScanResult.FLAG_80211mc_RESPONDER);
        scan1.channelWidth = ScanResult.CHANNEL_WIDTH_40MHZ;
        ScanResult scan2 = new ScanResult();
        scan2.BSSID = "0A:0B:0C:0D:0E:" + String.format("%02d", lastMacByte);
        scan2.channelWidth = ScanResult.CHANNEL_WIDTH_20MHZ;
        MacAddress mac1 = MacAddress.fromString("08:09:08:07:06:05");

        builder.addAccessPoint(scan1);
        builder.addNon80211mcCapableAccessPoint(scan2);
        // Changing default RTT burst size to a valid, but maximum, value
        builder.setRttBurstSize(RangingRequest.getMaxRttBurstSize());
        builder.addWifiAwarePeer(mac1);
        return builder.build();
    }

    /**
     * Returns a placeholder ranging request with 11mc request with a specified burst size.
     */
    public static RangingRequest getDummyRangingRequestMcOnly(byte lastMacByte, int rttBurstSize) {
        RangingRequest.Builder builder = new RangingRequest.Builder();

        ScanResult scan1 = new ScanResult();
        scan1.BSSID = "00:01:02:03:04:" + String.format("%02d", lastMacByte);
        scan1.setFlag(ScanResult.FLAG_80211mc_RESPONDER);
        scan1.channelWidth = ScanResult.CHANNEL_WIDTH_40MHZ;

        builder.addAccessPoint(scan1);
        builder.setRttBurstSize(rttBurstSize);

        return builder.build();
    }

    /**
     * Returns a placeholder ranging request with 2 requests - neither of which support 802.11mc.
     */
    public static RangingRequest getDummyRangingRequestNo80211mcSupport(byte lastMacByte) {
        RangingRequest.Builder builder = new RangingRequest.Builder();

        ScanResult scan1 = new ScanResult();
        scan1.BSSID = "00:01:02:03:04:" + String.format("%02d", lastMacByte);
        ScanResult scan2 = new ScanResult();
        scan2.BSSID = "0A:0B:0C:0D:0E:" + String.format("%02d", lastMacByte);

        builder.addNon80211mcCapableAccessPoint(scan1);
        builder.addNon80211mcCapableAccessPoint(scan2);

        return builder.build();
    }

    /**
     * Returns a matched set of placeholder ranging results: HAL RttResult and the public API
     * RangingResult.
     *
     * @param request If non-null will be used as a template (BSSID) for the range results.
     */
    public static Pair<List<RangingResult>, List<RangingResult>> getDummyRangingResults(
            RangingRequest request) {
        int rangeCmBase = 15;
        int rangeStdDevCmBase = 3;
        int rssiBase = -20;
        long rangeTimestampBase = 666;
        List<RangingResult> halResults = new ArrayList<>();
        List<RangingResult> results = new ArrayList<>();

        if (request != null) {
            for (ResponderConfig peer : request.mRttPeers) {
                halResults.add(new RangingResult.Builder()
                        .setStatus(RangingResult.STATUS_SUCCESS)
                        .setMacAddress(peer.getMacAddress())
                        .setDistanceMm(rangeCmBase)
                        .setDistanceStdDevMm(rangeStdDevCmBase)
                        .setRssi(rssiBase)
                        .setNumAttemptedMeasurements(8)
                        .setNumSuccessfulMeasurements(5)
                        .setRangingTimestampMillis(rangeTimestampBase)
                        .set80211mcMeasurement(true)
                        .setMeasurementChannelFrequencyMHz(5180)
                        .setMeasurementBandwidth(ScanResult.CHANNEL_WIDTH_40MHZ)
                        .build());
                RangingResult.Builder builder = new RangingResult.Builder()
                        .setStatus(RangingResult.STATUS_SUCCESS)
                        .setDistanceMm(rangeCmBase++)
                        .setDistanceStdDevMm(rangeStdDevCmBase++)
                        .setRssi(rssiBase++)
                        .setNumAttemptedMeasurements(8)
                        .setNumSuccessfulMeasurements(5)
                        .setRangingTimestampMillis(rangeTimestampBase++)
                        .set80211mcMeasurement(true)
                        .setMeasurementChannelFrequencyMHz(5180)
                        .setMeasurementBandwidth(ScanResult.CHANNEL_WIDTH_40MHZ);
                if (peer.peerHandle == null) {
                    builder.setMacAddress(peer.getMacAddress());
                } else {
                    builder.setPeerHandle(peer.peerHandle);
                }
                RangingResult rangingResult = builder.build();
                results.add(rangingResult);
            }
        } else {
            results.add(new RangingResult.Builder()
                    .setStatus(RangingResult.STATUS_SUCCESS)
                    .setMacAddress(MacAddress.fromString("10:01:02:03:04:05"))
                    .setDistanceMm(rangeCmBase++)
                    .setDistanceStdDevMm(rangeStdDevCmBase++)
                    .setRssi(rssiBase++)
                    .setNumAttemptedMeasurements(8)
                    .setNumSuccessfulMeasurements(4)
                    .setRangingTimestampMillis(rangeTimestampBase++)
                    .set80211mcMeasurement(true)
                    .setMeasurementChannelFrequencyMHz(5180)
                    .setMeasurementBandwidth(ScanResult.CHANNEL_WIDTH_40MHZ)
                    .build());
            results.add(new RangingResult.Builder()
                    .setStatus(RangingResult.STATUS_SUCCESS)
                    .setMacAddress(MacAddress.fromString("1A:0B:0C:0D:0E:0F"))
                    .setDistanceMm(rangeCmBase++)
                    .setDistanceStdDevMm(rangeStdDevCmBase++)
                    .setRssi(rssiBase++)
                    .setNumAttemptedMeasurements(9)
                    .setNumSuccessfulMeasurements(3)
                    .setRangingTimestampMillis(rangeTimestampBase++)
                    .set80211mcMeasurement(true)
                    .setMeasurementChannelFrequencyMHz(5180)
                    .setMeasurementBandwidth(ScanResult.CHANNEL_WIDTH_40MHZ)
                    .build());
            results.add(new RangingResult.Builder()
                    .setStatus(RangingResult.STATUS_SUCCESS)
                    .setMacAddress(MacAddress.fromString("08:09:08:07:06:05"))
                    .setDistanceMm(rangeCmBase++)
                    .setDistanceStdDevMm(rangeStdDevCmBase++)
                    .setRssi(rssiBase++)
                    .setNumAttemptedMeasurements(10)
                    .setNumSuccessfulMeasurements(2)
                    .setRangingTimestampMillis(rangeTimestampBase++)
                    .set80211mcMeasurement(true)
                    .setMeasurementChannelFrequencyMHz(5180)
                    .setMeasurementBandwidth(ScanResult.CHANNEL_WIDTH_40MHZ)
                    .build());
            halResults.addAll(results);
        }

        return new Pair<>(halResults, results);
    }
}
