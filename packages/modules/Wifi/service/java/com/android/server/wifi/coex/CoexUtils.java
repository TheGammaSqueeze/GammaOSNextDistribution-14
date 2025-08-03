/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wifi.coex;

import static android.net.wifi.ScanResult.UNSPECIFIED;
import static android.net.wifi.WifiScanner.WIFI_BAND_24_GHZ;
import static android.net.wifi.WifiScanner.WIFI_BAND_5_GHZ;

import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiAnnotations;
import android.os.Build;
import android.telephony.Annotation;
import android.telephony.PhysicalChannelConfig;
import android.util.Log;
import android.util.SparseIntArray;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Class containing the unsafe channel algorithms and other utility methods for Wi-Fi/Cellular coex.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class CoexUtils {
    public static final String TAG = "CoexUtils";

    private static final int INVALID_CHANNEL = -1;
    @VisibleForTesting
    /* package */ static final int INVALID_BAND = -1;
    @VisibleForTesting
    /* package */ static final int INVALID_FREQ = -1;

    public static final int GPS_L1_CENTER_FREQ_KHZ = 1575_420;

    public static final int NUM_24_GHZ_CHANNELS = 14;
    public static final NavigableSet<Integer> CHANNEL_SET_5_GHZ_20_MHZ = create5g20MhzChannels();
    public static final NavigableSet<Integer> CHANNEL_SET_5_GHZ_40_MHZ = create5g40MhzChannels();
    public static final NavigableSet<Integer> CHANNEL_SET_5_GHZ_80_MHZ = create5g80MhzChannels();
    public static final NavigableSet<Integer> CHANNEL_SET_5_GHZ_160_MHZ = create5g160MhzChannels();
    public static final NavigableSet<Integer> CHANNEL_SET_5_GHZ = new TreeSet<>();
    static {
        CHANNEL_SET_5_GHZ.addAll(CHANNEL_SET_5_GHZ_20_MHZ);
        CHANNEL_SET_5_GHZ.addAll(CHANNEL_SET_5_GHZ_40_MHZ);
        CHANNEL_SET_5_GHZ.addAll(CHANNEL_SET_5_GHZ_80_MHZ);
        CHANNEL_SET_5_GHZ.addAll(CHANNEL_SET_5_GHZ_160_MHZ);
    }
    private static final SparseIntArray DEPENDENT_MAP_5_GHZ = create5gDependentChannelMap();

    private static NavigableSet<Integer> create5g20MhzChannels() {
        NavigableSet<Integer> set = new TreeSet<>();
        for (int chan = 32; chan <= 68; chan += 4) {
            set.add(chan);
        }
        for (int chan = 96; chan <= 144; chan += 4) {
            set.add(chan);
        }
        for (int chan = 149; chan <= 173; chan += 4) {
            set.add(chan);
        }
        return set;
    }

    private static NavigableSet<Integer> create5g40MhzChannels() {
        NavigableSet<Integer> set = new TreeSet<>();
        set.add(34);
        for (int chan = 38; chan <= 62; chan += 8) {
            set.add(chan);
        }
        for (int chan = 102; chan <= 142; chan += 8) {
            set.add(chan);
        }
        for (int chan = 151; chan <= 159; chan += 8) {
            set.add(chan);
        }
        return set;
    }

    private static NavigableSet<Integer> create5g80MhzChannels() {
        NavigableSet<Integer> set = new TreeSet<>();
        set.add(42);
        set.add(58);
        set.add(106);
        set.add(122);
        set.add(138);
        set.add(155);
        return set;
    }

    private static NavigableSet<Integer> create5g160MhzChannels() {
        NavigableSet<Integer> set = new TreeSet<>();
        set.add(50);
        set.add(114);
        return set;
    }

    /**
     * Creates a SparseIntArray map of 5GHz channel to the dependent channel that contains it,
     * if it exists.
     */
    private static SparseIntArray create5gDependentChannelMap() {
        SparseIntArray map = new SparseIntArray();
        // Map 160Mhz channels with their dependency 80Mhz channels.
        for (int chan : CHANNEL_SET_5_GHZ_160_MHZ) {
            map.put(chan - 8, chan);
            map.put(chan + 8, chan);
        }
        // Map 80Mhz channels with their dependency 40Mhz channels.
        for (int chan : CHANNEL_SET_5_GHZ_80_MHZ) {
            map.put(chan - 4, chan);
            map.put(chan + 4, chan);
        }
        // Map 40Mhz channels with their dependency 20Mhz channels.
        // Note channel 36 maps to both 34 and 38, but will only map to 38 in the dependent map.
        for (int chan : CHANNEL_SET_5_GHZ_40_MHZ) {
            map.put(chan - 2, chan);
            map.put(chan + 2, chan);
        }
        return map;
    }

    // Channels to frequencies

    /** Gets the upper or lower edge of a given channel */
    private static int getChannelEdgeKhz(int channel, @WifiAnnotations.WifiBandBasic int band,
            boolean lowerEdge) {
        int centerFreqMhz = ScanResult.convertChannelToFrequencyMhzIfSupported(channel, band);
        if (centerFreqMhz == UNSPECIFIED) {
            return INVALID_FREQ;
        }

        int bandwidthOffsetMhz = 0;
        if (band == WIFI_BAND_24_GHZ) {
            bandwidthOffsetMhz = 11;
        } else if (band == WIFI_BAND_5_GHZ) {
            if (CHANNEL_SET_5_GHZ_20_MHZ.contains(channel)) {
                bandwidthOffsetMhz = 10;
            } else if (CHANNEL_SET_5_GHZ_40_MHZ.contains(channel)) {
                bandwidthOffsetMhz = 20;
            } else if (CHANNEL_SET_5_GHZ_80_MHZ.contains(channel)) {
                bandwidthOffsetMhz = 40;
            } else {
                bandwidthOffsetMhz = 80;
            }
        }

        if (lowerEdge) {
            bandwidthOffsetMhz = -bandwidthOffsetMhz;
        }
        return (centerFreqMhz + bandwidthOffsetMhz) * 1_000;
    }

    /** Gets the lower frequency of a given channel */
    @VisibleForTesting
    /* package */ static int getLowerFreqKhz(int channel, @WifiAnnotations.WifiBandBasic int band) {
        return getChannelEdgeKhz(channel, band, true);
    }

    /** Gets the upper frequency of a given channel */
    @VisibleForTesting
    /* package */ static int getUpperFreqKhz(int channel, @WifiAnnotations.WifiBandBasic int band) {
        return getChannelEdgeKhz(channel, band, false);
    }

    // Frequencies to channels

    /**
     * Gets the highest 2.4GHz Wi-Fi channel overlapping the upper edge of an interference
     * frequency approaching from below the band.
     *
     * Returns INVALID_CHANNEL if there is no overlap.
     */
    private static int get2gHighestOverlapChannel(int upperEdgeKhz) {
        final int band = WIFI_BAND_24_GHZ;
        if (upperEdgeKhz > getLowerFreqKhz(14, band)) {
            return 14;
        }
        if (upperEdgeKhz > getLowerFreqKhz(13, band)) {
            return 13;
        }
        final int chan1LowerFreqKhz = getLowerFreqKhz(1, band);
        if (upperEdgeKhz > chan1LowerFreqKhz) {
            return getOffsetChannel(1, upperEdgeKhz - chan1LowerFreqKhz, 1);
        }
        // Edge does not overlap the band.
        return INVALID_CHANNEL;
    }

    /**
     * Gets the lowest 2.4GHz Wi-Fi channel overlapping the lower edge of an interference
     * frequency approaching from above the band.
     *
     * Returns INVALID_CHANNEL if there is no overlap.
     */
    private static int get2gLowestOverlapChannel(int lowerEdgeKhz) {
        final int band = WIFI_BAND_24_GHZ;
        if (lowerEdgeKhz < getUpperFreqKhz(1, band)) {
            return 1;
        }
        final int chan13UpperFreqKhz = getUpperFreqKhz(13, band);
        if (lowerEdgeKhz < chan13UpperFreqKhz) {
            return getOffsetChannel(13, lowerEdgeKhz - chan13UpperFreqKhz, 1);
        }
        if (lowerEdgeKhz < getUpperFreqKhz(14, band)) {
            return 14;
        }
        // Edge does not overlap the band.
        return INVALID_CHANNEL;
    }

    /**
     * Gets the highest 5GHz Wi-Fi channel overlapping the upper edge of an interference
     * frequency approaching from below the band.
     *
     * Returns INVALID_CHANNEL if there is no overlap.
     */
    private static int get5gHighestOverlap20MhzChannel(int upperEdgeKhz) {
        final int band = WIFI_BAND_5_GHZ;
        // 149 to 173
        if (upperEdgeKhz > getLowerFreqKhz(173, band)) {
            return 173;
        }
        final int chan149LowerFreqKhz = getLowerFreqKhz(149, band);
        if (upperEdgeKhz > chan149LowerFreqKhz) {
            return getOffsetChannel(149, upperEdgeKhz - chan149LowerFreqKhz, 4);
        }
        // 96 to 144
        if (upperEdgeKhz > getLowerFreqKhz(144, band)) {
            return 144;
        }
        final int chan96LowerFreqKhz = getLowerFreqKhz(96, band);
        if (upperEdgeKhz > chan96LowerFreqKhz) {
            return getOffsetChannel(96, upperEdgeKhz - chan96LowerFreqKhz, 4);
        }
        // 32 to 68
        if (upperEdgeKhz > getLowerFreqKhz(68, band)) {
            return 68;
        }
        final int chan32LowerFreqKhz = getLowerFreqKhz(32, band);
        if (upperEdgeKhz > chan32LowerFreqKhz) {
            return getOffsetChannel(32, upperEdgeKhz - chan32LowerFreqKhz, 4);
        }
        // Edge does not overlap the band.
        return INVALID_CHANNEL;
    }

    /**
     * Gets the lowest 5GHz Wi-Fi channel overlapping the lower edge of an interference
     * frequency approaching from above the band.
     *
     * Returns INVALID_CHANNEL if there is no overlap.
     */
    private static int get5gLowestOverlap20MhzChannel(int lowerEdgeKhz) {
        final int band = WIFI_BAND_5_GHZ;
        // 32 to 68
        if (lowerEdgeKhz < getUpperFreqKhz(32, band)) {
            return 32;
        }
        final int chan68UpperFreqKhz = getUpperFreqKhz(68, band);
        if (lowerEdgeKhz < chan68UpperFreqKhz) {
            return getOffsetChannel(68, lowerEdgeKhz - chan68UpperFreqKhz, 4);
        }
        // 96 to 144
        if (lowerEdgeKhz < getUpperFreqKhz(96, band)) {
            return 96;
        }
        final int chan144UpperFreqKhz = getUpperFreqKhz(144, band);
        if (lowerEdgeKhz < chan144UpperFreqKhz) {
            return getOffsetChannel(144, lowerEdgeKhz - chan144UpperFreqKhz, 4);
        }
        // 149 to 173
        if (lowerEdgeKhz < getUpperFreqKhz(149, band)) {
            return 149;
        }
        final int chan173UpperFreqKhz = getUpperFreqKhz(173, band);
        if (lowerEdgeKhz < chan173UpperFreqKhz) {
            return getOffsetChannel(173, lowerEdgeKhz - chan173UpperFreqKhz, 4);
        }
        // Edge does not overlap the band.
        return INVALID_CHANNEL;
    }

    /**
     * Returns the furthest channel located a given frequency offset away from a start channel
     * counting by a given channel step size. A positive frequency offset will give a channel
     * above the start, and a negative frequency offset will give a channel below the start.
     *
     * @param startChannel Channel to start from
     * @param offsetKhz Offset distance in Khz
     * @param channelStepSize Step size to count channels by
     * @return The channel that lies the given offset away from the start channel
     */
    @VisibleForTesting
    /* package */ static int getOffsetChannel(
            int startChannel, int offsetKhz, int channelStepSize) {
        // Each channel number is always separated by 5Mhz.
        int channelSpacingKhz = 5_000;
        int stepsToOffset = offsetKhz / (channelSpacingKhz * channelStepSize);
        // Offset lands directly channel edge; use previous channel based on offset direction.
        if (offsetKhz % (channelSpacingKhz * channelStepSize) == 0) {
            if (offsetKhz > 0) {
                stepsToOffset--;
            } else if (offsetKhz < 0) {
                stepsToOffset++;
            }
        }
        return startChannel + (stepsToOffset * channelStepSize);
    }

    /**
     * Returns the percent overlap (0 to 100) of an aggressor frequency range over a victim
     * frequency range,
     */
    private static int getOverlapPercent(int aggressorLowerKhz, int aggressorUpperKhz,
            int victimLowerKhz, int victimUpperKhz) {
        final int victimBandwidthKhz = victimUpperKhz - victimLowerKhz;
        int overlapWidthKhz = Math.min(aggressorUpperKhz, victimUpperKhz)
                - Math.max(aggressorLowerKhz, victimLowerKhz);
        if (overlapWidthKhz < 0) {
            overlapWidthKhz = 0;
        }
        if (victimBandwidthKhz == 0) {
            return 0;
        }
        return overlapWidthKhz * 100 / victimBandwidthKhz;
    }

    /**
     * Returns the CoexUnsafeChannels for the given cell channel and threshold.
     */
    public static List<CoexUnsafeChannel> getNeighboringCoexUnsafeChannels(
            int cellFreqKhz, int cellBandwidthKhz, int thresholdKhz, int powerCapDbm) {
        List<CoexUnsafeChannel> coexUnsafeChannels = new ArrayList<>();
        final int unsafeLowerKhz = cellFreqKhz - (cellBandwidthKhz / 2) - thresholdKhz;
        final int unsafeUpperKhz = cellFreqKhz + (cellBandwidthKhz / 2) + thresholdKhz;

        // 2.4Ghz
        final int lowest2gChannel = get2gLowestOverlapChannel(unsafeLowerKhz);
        final int highest2gChannel = get2gHighestOverlapChannel(unsafeUpperKhz);
        // If the interference has a valid overlap over the 2.4GHz band, mark every channel
        // in the inclusive range of the lowest and highest overlapped channels.
        if (lowest2gChannel != INVALID_CHANNEL && highest2gChannel != INVALID_CHANNEL
                && lowest2gChannel <= highest2gChannel) {
            for (int channel = lowest2gChannel; channel <= highest2gChannel; channel++) {
                coexUnsafeChannels.add(
                        new CoexUnsafeChannel(WIFI_BAND_24_GHZ, channel, powerCapDbm));
            }
        }

        // 5Ghz
        final int highest5gChannel = get5gHighestOverlap20MhzChannel(unsafeUpperKhz);
        final int lowest5gChannel = get5gLowestOverlap20MhzChannel(unsafeLowerKhz);
        // If the interference has a valid overlap over the 5GHz band, mark every channel
        // in the inclusive range of the lowest and highest overlapped channels.
        if (lowest5gChannel != INVALID_CHANNEL && highest5gChannel != INVALID_CHANNEL
                && lowest5gChannel <= highest5gChannel) {
            final Set<Integer> overlapped5g20MhzChannels = CHANNEL_SET_5_GHZ_20_MHZ.subSet(
                    lowest5gChannel, true,
                    highest5gChannel, true);
            final Set<Integer> seen = new HashSet<>();
            // Mark overlapped 20Mhz channels and their dependents as unsafe
            for (int channel : overlapped5g20MhzChannels) {
                while (channel != 0) {
                    if (!seen.add(channel)) {
                        // Dependent channel was already marked unsafe by another dependency channel
                        break;
                    }
                    coexUnsafeChannels.add(
                            new CoexUnsafeChannel(WIFI_BAND_5_GHZ, channel, powerCapDbm));
                    // Go to each dependent 40, 80, 160Mhz channel and mark them as unsafe.
                    // If a dependent doesn't exist, channel will be set to 0 and the loop ends.
                    channel = DEPENDENT_MAP_5_GHZ.get(channel, 0);
                }
            }
            // 36 should also map to 34, but only maps to 38 in the dependent channel map.
            if (overlapped5g20MhzChannels.contains(36) && !seen.contains(34)) {
                coexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 34, powerCapDbm));
            }
        }

        return coexUnsafeChannels;
    }

    /**
     * Returns the 2.4GHz UnsafeChannels affected by the harmonic interference from a given uplink
     * cell channel.
     */
    public static List<CoexUnsafeChannel> get2gHarmonicCoexUnsafeChannels(
            int ulFreqKhz, int ulBandwidthKhz, int harmonicDegree, int overlapPercentThreshold,
            int powerCapDbm) {
        List<CoexUnsafeChannel> coexUnsafeChannels = new ArrayList<>();
        final int unsafeLowerKhz = (ulFreqKhz - (ulBandwidthKhz / 2)) * harmonicDegree;
        final int unsafeUpperKhz = (ulFreqKhz + (ulBandwidthKhz / 2)) * harmonicDegree;

        int lowest2gChannel = get2gLowestOverlapChannel(unsafeLowerKhz);
        int highest2gChannel = get2gHighestOverlapChannel(unsafeUpperKhz);
        if (lowest2gChannel != INVALID_CHANNEL && highest2gChannel != INVALID_CHANNEL
                && lowest2gChannel <= highest2gChannel) {
            // Find lowest channel at max overlap, or invalid channel 15 if the threshold is not met
            while (lowest2gChannel <= 14 && getOverlapPercent(unsafeLowerKhz, unsafeUpperKhz,
                    getLowerFreqKhz(lowest2gChannel, WIFI_BAND_24_GHZ),
                    getUpperFreqKhz(lowest2gChannel, WIFI_BAND_24_GHZ))
                    < overlapPercentThreshold) {
                lowest2gChannel++;
            }
            // Find highest channel at max overlap, or invalid channel 0 if the threshold is not met
            while (highest2gChannel >= 1 && getOverlapPercent(unsafeLowerKhz, unsafeUpperKhz,
                    getLowerFreqKhz(highest2gChannel, WIFI_BAND_24_GHZ),
                    getUpperFreqKhz(highest2gChannel, WIFI_BAND_24_GHZ))
                    < overlapPercentThreshold) {
                highest2gChannel--;
            }
            // Mark every channel in between as unsafe
            for (int channel = lowest2gChannel; channel <= highest2gChannel; channel++) {
                coexUnsafeChannels.add(
                        new CoexUnsafeChannel(WIFI_BAND_24_GHZ, channel, powerCapDbm));
            }
        }
        return coexUnsafeChannels;
    }


    /**
     * Returns the 5GHz CoexUnsafeChannels affected by the harmonic interference from a given uplink
     * cell channel.
     */
    public static List<CoexUnsafeChannel> get5gHarmonicCoexUnsafeChannels(
            int ulFreqKhz, int ulBandwidthKhz, int harmonicDegree, int overlapPercentThreshold,
            int powerCapDbm) {
        List<CoexUnsafeChannel> coexUnsafeChannels = new ArrayList<>();
        final int unsafeLowerKhz = (ulFreqKhz - (ulBandwidthKhz / 2)) * harmonicDegree;
        final int unsafeUpperKhz = (ulFreqKhz + (ulBandwidthKhz / 2)) * harmonicDegree;

        final int lowest5gChannel = get5gLowestOverlap20MhzChannel(unsafeLowerKhz);
        final int highest5gChannel = get5gHighestOverlap20MhzChannel(unsafeUpperKhz);
        if (lowest5gChannel != INVALID_CHANNEL && highest5gChannel != INVALID_CHANNEL
                && lowest5gChannel <= highest5gChannel) {
            Map<Integer, Integer> overlapPercents = new HashMap<>();
            // Find lowest 20MHz overlap channel
            overlapPercents.put(lowest5gChannel, getOverlapPercent(unsafeLowerKhz, unsafeUpperKhz,
                    getLowerFreqKhz(lowest5gChannel, WIFI_BAND_5_GHZ),
                    getUpperFreqKhz(lowest5gChannel, WIFI_BAND_5_GHZ)));
            // Find highest 2MHz overlap channel
            overlapPercents.put(highest5gChannel, getOverlapPercent(unsafeLowerKhz, unsafeUpperKhz,
                    getLowerFreqKhz(highest5gChannel, WIFI_BAND_5_GHZ),
                    getUpperFreqKhz(highest5gChannel, WIFI_BAND_5_GHZ)));
            // Every channel in between should be at 100 percent overlap
            for (int channel : CHANNEL_SET_5_GHZ_20_MHZ.subSet(
                    lowest5gChannel, false, highest5gChannel, false)) {
                overlapPercents.put(channel, 100);
            }
            // Iterate through each group of 20Mhz, 40Mhz, 80Mhz, 160Mhz channels, and add to
            // unsafe channel set if the pre-calculated overlap percent meets the threshold.
            while (!overlapPercents.isEmpty()) {
                Map<Integer, Integer> dependentOverlaps = new HashMap<>();
                for (int channel : overlapPercents.keySet()) {
                    int overlapPercent = overlapPercents.get(channel);
                    // Add channel to unsafe channel set if overlap percent meets threshold.
                    if (overlapPercent >= overlapPercentThreshold) {
                        coexUnsafeChannels.add(
                                new CoexUnsafeChannel(WIFI_BAND_5_GHZ, channel, powerCapDbm));
                    }
                    // Pre-calculate the dependent channel overlap for the next iteration by adding
                    // half of each dependency channel percent.
                    final int dependentChannel =  DEPENDENT_MAP_5_GHZ.get(channel, 0);
                    if (dependentChannel != 0) {
                        dependentOverlaps.put(dependentChannel, overlapPercent / 2
                                + dependentOverlaps.getOrDefault(dependentChannel, 0));
                    }
                    // 36 should also map to 34, but only maps to 38 in the dependent map.
                    if (channel == 36) {
                        dependentOverlaps.put(34, overlapPercent / 2
                                + dependentOverlaps.getOrDefault(34, 0));
                    }
                }
                // Set the next dependent group to iterate over until there are no more dependents.
                overlapPercents = dependentOverlaps;
            }
        }
        return coexUnsafeChannels;
    }

    /**
     * Returns CoexUnsafeChannels of a given band affected by the intermod interference from a given
     * uplink and downlink cell channel.
     */
    public static List<CoexUnsafeChannel> getIntermodCoexUnsafeChannels(
            int ulFreqKhz, int ulBandwidthKhz, int dlFreqKhz, int dlBandwidthKhz,
            int n, int m, int overlapPercentThreshold, @WifiAnnotations.WifiBandBasic int band,
            int powerCapDbm) {
        List<CoexUnsafeChannel> coexUnsafeChannels = new ArrayList<>();
        final int ulLowerKhz = (ulFreqKhz - (ulBandwidthKhz / 2));
        final int ulUpperKhz = (ulFreqKhz + (ulBandwidthKhz / 2));
        final int dlLowerKhz = (dlFreqKhz - (dlBandwidthKhz / 2));
        final int dlUpperKhz = (dlFreqKhz + (dlBandwidthKhz / 2));

        Set<Integer> channelSet = new HashSet<>();
        if (band == WIFI_BAND_24_GHZ) {
            for (int channel = 1; channel <= 14; channel++) {
                channelSet.add(channel);
            }
        } else if (band == WIFI_BAND_5_GHZ) {
            channelSet.addAll(CHANNEL_SET_5_GHZ);
        }

        for (int channel : channelSet) {
            final int wifiLowerKhz = getLowerFreqKhz(channel, band);
            final int wifiUpperKhz = getUpperFreqKhz(channel, band);
            final int intermodLowerKhz = Math.min(n * ulLowerKhz, n * ulUpperKhz)
                    + Math.min(m * wifiLowerKhz, m * wifiUpperKhz);
            final int intermodUpperKhz = Math.max(n * ulLowerKhz, n * ulUpperKhz)
                    + Math.max(m * wifiLowerKhz, m * wifiUpperKhz);
            if (getOverlapPercent(intermodLowerKhz, intermodUpperKhz, dlLowerKhz, dlUpperKhz)
                    >= overlapPercentThreshold) {
                coexUnsafeChannels.add(new CoexUnsafeChannel(band, channel, powerCapDbm));
            }
        }

        return coexUnsafeChannels;
    }

    /**
     * Returns CoexUnsafeChannels affecting GPS L1 due to the intermod interference from a given
     * uplink and downlink cell channel and every possible Wi-Fi channel.
     */
    public static List<CoexUnsafeChannel> getCoexUnsafeChannelsForGpsL1(
            int cellUlFreqKhz, int cellUlBandwidthKhz, int thresholdKhz) {
        List<CoexUnsafeChannel> coexUnsafeChannels = new ArrayList<>();
        for (int channel = 1; channel <= 14; channel++) {
            int centerFreq2gMhz =
                    ScanResult.convertChannelToFrequencyMhzIfSupported(channel, WIFI_BAND_24_GHZ);
            if (centerFreq2gMhz == UNSPECIFIED) {
                continue;
            }
            if (isGpsL1ImpactedByCellAndWifi(cellUlFreqKhz, cellUlBandwidthKhz,
                    centerFreq2gMhz * 1000, 22_000, thresholdKhz)) {
                coexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, channel));
            }
        }
        for (int channel : CHANNEL_SET_5_GHZ_20_MHZ) {
            int centerFreq5g20Mhz =
                    ScanResult.convertChannelToFrequencyMhzIfSupported(channel, WIFI_BAND_5_GHZ);
            if (centerFreq5g20Mhz == UNSPECIFIED) {
                continue;
            }
            if (isGpsL1ImpactedByCellAndWifi(cellUlFreqKhz, cellUlBandwidthKhz,
                    centerFreq5g20Mhz * 1000, 20_000, thresholdKhz)) {
                coexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, channel));
            }
        }
        for (int channel : CHANNEL_SET_5_GHZ_40_MHZ) {
            int centerFreq5g40Mhz =
                    ScanResult.convertChannelToFrequencyMhzIfSupported(channel, WIFI_BAND_5_GHZ);
            if (centerFreq5g40Mhz == UNSPECIFIED) {
                continue;
            }
            if (isGpsL1ImpactedByCellAndWifi(cellUlFreqKhz, cellUlBandwidthKhz,
                    centerFreq5g40Mhz * 1000, 40_000, thresholdKhz)) {
                coexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, channel));
            }
        }
        for (int channel : CHANNEL_SET_5_GHZ_80_MHZ) {
            int centerFreq5g80Mhz =
                    ScanResult.convertChannelToFrequencyMhzIfSupported(channel, WIFI_BAND_5_GHZ);
            if (centerFreq5g80Mhz == UNSPECIFIED) {
                continue;
            }
            if (isGpsL1ImpactedByCellAndWifi(cellUlFreqKhz, cellUlBandwidthKhz,
                    centerFreq5g80Mhz * 1000, 80_000, thresholdKhz)) {
                coexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, channel));
            }
        }
        for (int channel : CHANNEL_SET_5_GHZ_160_MHZ) {
            int centerFreq5g160Mhz =
                    ScanResult.convertChannelToFrequencyMhzIfSupported(channel, WIFI_BAND_5_GHZ);
            if (centerFreq5g160Mhz == UNSPECIFIED) {
                continue;
            }
            if (isGpsL1ImpactedByCellAndWifi(cellUlFreqKhz, cellUlBandwidthKhz,
                    centerFreq5g160Mhz * 1000, 160_000, thresholdKhz)) {
                coexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, channel));
            }
        }
        return coexUnsafeChannels;
    }

    /**
     * Returns whether or not GPS L1 is impacted by the given cell and wifi channels.
     */
    public static boolean isGpsL1ImpactedByCellAndWifi(
            int cellCenterKhz, int cellBandwidthKhz,
            int wifiCenterKhz, int wifiBandwidthKhz,
            int thresholdKhz) {
        int cellLowerKhz = cellCenterKhz - cellBandwidthKhz / 2;
        int cellUpperKhz = cellCenterKhz + cellBandwidthKhz / 2;
        int wifiLowerKhz = wifiCenterKhz - wifiBandwidthKhz / 2;
        int wifiUpperKhz = wifiCenterKhz + wifiBandwidthKhz / 2;
        int intermodLowerKhz;
        int intermodUpperKhz;
        if (wifiCenterKhz > cellCenterKhz) {
            intermodLowerKhz = wifiLowerKhz - cellUpperKhz;
            intermodUpperKhz = wifiUpperKhz - cellLowerKhz;
        } else {
            intermodLowerKhz = cellLowerKhz - wifiUpperKhz;
            intermodUpperKhz = cellUpperKhz - wifiLowerKhz;
        }
        int gpsLowerKhz = GPS_L1_CENTER_FREQ_KHZ - thresholdKhz;
        int gpsUpperKhz = GPS_L1_CENTER_FREQ_KHZ + thresholdKhz;
        return !(intermodLowerKhz > gpsUpperKhz || intermodUpperKhz < gpsLowerKhz);
    }

    /**
     * Data structure class mirroring cell channel information from PhysicalChannelConfig used for
     * coex calculations.
     */
    public static class CoexCellChannel {
        private final @Annotation.NetworkType int mRat;
        private final int mBand;
        private final int mDownlinkFreqKhz;
        private final int mDownlinkBandwidthKhz;
        private final int mUplinkFreqKhz;
        private final int mUplinkBandwidthKhz;
        private final int mSubId;

        public CoexCellChannel(@Annotation.NetworkType int rat, int band,
                int downlinkFreqKhz, int downlinkBandwidthKhz,
                int uplinkFreqKhz, int uplinkBandwidthKhz, int subId) {
            if (band < 1 || band > 261) {
                Log.wtf(TAG, "Band is " + band + " but should be a value from 1 to 261");
            }
            if (downlinkFreqKhz < 0 && downlinkFreqKhz != PhysicalChannelConfig.FREQUENCY_UNKNOWN) {
                Log.wtf(TAG, "Downlink frequency is " + downlinkFreqKhz + " but should be >= 0"
                        + " or PhysicalChannelConfig.FREQUENCY_UNKNOWN: "
                        + PhysicalChannelConfig.FREQUENCY_UNKNOWN);
            }
            if (downlinkBandwidthKhz <= 0
                    && downlinkBandwidthKhz != PhysicalChannelConfig.CELL_BANDWIDTH_UNKNOWN) {
                Log.wtf(TAG, "Downlink bandwidth is " + downlinkBandwidthKhz + " but should be > 0"
                        + " or PhysicalChannelConfig.CELL_BANDWIDTH_UNKNOWN: "
                        + PhysicalChannelConfig.CELL_BANDWIDTH_UNKNOWN);
            }
            if (uplinkFreqKhz < 0 && uplinkFreqKhz != PhysicalChannelConfig.FREQUENCY_UNKNOWN) {
                Log.wtf(TAG, "Uplink frequency is " + uplinkFreqKhz + " but should be >= 0"
                        + " or PhysicalChannelConfig.FREQUENCY_UNKNOWN: "
                        + PhysicalChannelConfig.FREQUENCY_UNKNOWN);
            }
            if (uplinkBandwidthKhz <= 0
                    && uplinkBandwidthKhz != PhysicalChannelConfig.CELL_BANDWIDTH_UNKNOWN) {
                Log.wtf(TAG, "Uplink bandwidth is " + uplinkBandwidthKhz + " but should be > 0"
                        + " or PhysicalChannelConfig.CELL_BANDWIDTH_UNKNOWN: "
                        + PhysicalChannelConfig.CELL_BANDWIDTH_UNKNOWN);
            }
            mRat = rat;
            mBand = band;
            mDownlinkFreqKhz = downlinkFreqKhz;
            mDownlinkBandwidthKhz = downlinkBandwidthKhz;
            mUplinkFreqKhz = uplinkFreqKhz;
            mUplinkBandwidthKhz = uplinkBandwidthKhz;
            mSubId = subId;
        }

        /**
         * Constructor to extract coex cell channel info from PhysicalChannelConfig
         */
        public CoexCellChannel(
                @android.annotation.NonNull PhysicalChannelConfig config, int subId) {
            this(config.getNetworkType(),
                    config.getBand(),
                    config.getDownlinkFrequencyKhz(),
                    config.getCellBandwidthDownlinkKhz(),
                    config.getUplinkFrequencyKhz(),
                    config.getCellBandwidthUplinkKhz(),
                    subId);
        }

        public @Annotation.NetworkType int getRat() {
            return mRat;
        }

        public int getBand() {
            return mBand;
        }

        public int getDownlinkFreqKhz() {
            return mDownlinkFreqKhz;
        }

        public int getDownlinkBandwidthKhz() {
            return mDownlinkBandwidthKhz;
        }

        public int getUplinkFreqKhz() {
            return mUplinkFreqKhz;
        }

        public int getUplinkBandwidthKhz() {
            return mUplinkBandwidthKhz;
        }

        public int getSubId() {
            return mSubId;
        }

        @java.lang.Override
        public String toString() {
            return "CoexCellChannel{"
                    + "rat=" + mRat
                    + ", band=" + mBand
                    + ", dlFreqKhz=" + mDownlinkFreqKhz
                    + ", dlBandwidthKhz=" + mDownlinkBandwidthKhz
                    + ", ulFreqKhz=" + mUplinkFreqKhz
                    + ", ulBandwidthKhz=" + mUplinkBandwidthKhz
                    + ", subId=" + mSubId
                    + '}';
        }

        @java.lang.Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CoexCellChannel)) return false;
            CoexCellChannel that = (CoexCellChannel) o;
            return getRat() == that.getRat() && getBand() == that.getBand()
                    && getDownlinkFreqKhz() == that.getDownlinkFreqKhz()
                    && getDownlinkBandwidthKhz() == that.getDownlinkBandwidthKhz()
                    && getUplinkFreqKhz() == that.getUplinkFreqKhz()
                    && getUplinkBandwidthKhz() == that.getUplinkBandwidthKhz()
                    && getSubId() == that.getSubId();
        }

        @java.lang.Override
        public int hashCode() {
            return Objects.hash(getRat(), getBand(), getDownlinkFreqKhz(),
                    getDownlinkBandwidthKhz(),
                    getUplinkFreqKhz(), getUplinkBandwidthKhz(), getSubId());
        }
    }
}
