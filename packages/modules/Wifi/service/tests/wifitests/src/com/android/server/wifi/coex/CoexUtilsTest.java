/*
 * Copyright 2020 The Android Open Source Project
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

import static android.net.wifi.CoexUnsafeChannel.POWER_CAP_NONE;
import static android.net.wifi.WifiScanner.WIFI_BAND_24_GHZ;
import static android.net.wifi.WifiScanner.WIFI_BAND_5_GHZ;

import static com.android.server.wifi.coex.CoexUtils.INVALID_FREQ;
import static com.android.server.wifi.coex.CoexUtils.get2gHarmonicCoexUnsafeChannels;
import static com.android.server.wifi.coex.CoexUtils.get5gHarmonicCoexUnsafeChannels;
import static com.android.server.wifi.coex.CoexUtils.getIntermodCoexUnsafeChannels;
import static com.android.server.wifi.coex.CoexUtils.getLowerFreqKhz;
import static com.android.server.wifi.coex.CoexUtils.getNeighboringCoexUnsafeChannels;
import static com.android.server.wifi.coex.CoexUtils.getOffsetChannel;
import static com.android.server.wifi.coex.CoexUtils.getUpperFreqKhz;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.net.wifi.CoexUnsafeChannel;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Unit tests for {@link com.android.server.wifi.coex.CoexUtils}.
 */
@SmallTest
public class CoexUtilsTest {

    private int getHarmonicUlFreqKhz(int unsafeLowerKhz, int unsafeUpperKhz, int harmonicDeg) {
        return (unsafeLowerKhz + unsafeUpperKhz) / (harmonicDeg * 2);
    }

    private int getHarmonicUlBandwidthKhz(int unsafeLowerKhz, int unsafeUpperKhz, int harmonicDeg) {
        return (unsafeUpperKhz - unsafeLowerKhz) / harmonicDeg;
    }

    @Before
    public void setUp() {
        assumeTrue(SdkLevel.isAtLeastS());
    }

    /**
     * Verifies that getNeighboringCoexUnsafeChannels returns an empty set if there is no overlap.
     */
    @Test
    public void testGetNeighboringCoexUnsafeChannels_noOverlap_returnsEmptySet() {
        // Below/Above 2.4GHz
        assertThat(getNeighboringCoexUnsafeChannels(getLowerFreqKhz(1, WIFI_BAND_24_GHZ) - 100_000,
                50_000, 50_000, POWER_CAP_NONE)).isEmpty();
        assertThat(getNeighboringCoexUnsafeChannels(getUpperFreqKhz(14, WIFI_BAND_24_GHZ) + 100_000,
                50_000, 50_000, POWER_CAP_NONE)).isEmpty();
        assertThat(getNeighboringCoexUnsafeChannels(2595_000,
                50_000, 50_000, POWER_CAP_NONE)).isEmpty();

        // Below/Above 5GHz
        assertThat(getNeighboringCoexUnsafeChannels(getLowerFreqKhz(32, WIFI_BAND_5_GHZ) - 100_000,
                50_000, 50_000, POWER_CAP_NONE)).isEmpty();
        assertThat(getNeighboringCoexUnsafeChannels(getUpperFreqKhz(173, WIFI_BAND_5_GHZ) + 100_000,
                50_000, 50_000, POWER_CAP_NONE)).isEmpty();
    }

    /**
     * Verifies that getNeighboringCoexUnsafeChannels returns the correct subset of 2.4GHz channels
     * from interference above and below the band.
     */
    @Test
    public void testGetNeighboringCoexUnsafeChannels_2g_returnsCorrectOverlap() {
        // Test channel 7 from below
        HashSet<CoexUnsafeChannel> lowerCoexUnsafeChannels = new HashSet<>();
        for (int i = 1; i <= 7; i++) {
            lowerCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, i));
        }
        assertThat(getNeighboringCoexUnsafeChannels(2401_000,
                0, 2431_000 - 2401_000 + 1, POWER_CAP_NONE))
                .containsExactlyElementsIn(lowerCoexUnsafeChannels);

        // Test channel 7 from above
        HashSet<CoexUnsafeChannel> upperCoexUnsafeChannels = new HashSet<>();
        for (int i = 7; i <= 14; i++) {
            upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, i));
        }
        assertThat(getNeighboringCoexUnsafeChannels(2495_000,
                0, 2495_000 - 2453_000 + 1, POWER_CAP_NONE))
                .containsExactlyElementsIn(upperCoexUnsafeChannels);
    }

    /**
     * Verifies that getNeighboringCoexUnsafeChannels returns the correct subset of 5GHz channels
     * from interference above and below the band.
     */
    @Test
    public void testGetNeighboringCoexUnsafeChannels_5g_returnsCorrectOverlap() {
        // Test channel 100 from below
        HashSet<CoexUnsafeChannel> lowerCoexUnsafeChannels = new HashSet<>();
        for (int i = 32; i <= 64; i += 2) {
            lowerCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, i));
        }
        lowerCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 68));
        lowerCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 96));
        lowerCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 100));
        // Verify that parent channels above channel 100 are included
        lowerCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 102));
        lowerCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 106));
        lowerCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 114));

        assertThat(getNeighboringCoexUnsafeChannels(5150_000,
                0, 5490_000 - 5150_000 + 1, POWER_CAP_NONE))
                .containsExactlyElementsIn(lowerCoexUnsafeChannels);

        // Test channel 64 from above
        HashSet<CoexUnsafeChannel> upperCoexUnsafeChannels = new HashSet<>();
        upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 64));
        upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 68));
        upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 96));
        for (int i = 100; i <= 128; i += 2) {
            upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, i));
        }
        for (int i = 132; i <= 144; i += 2) {
            upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, i));
        }
        for (int i = 149; i <= 161; i += 2) {
            upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, i));
        }
        upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 165));
        upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 169));
        upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 173));
        // Verify that parent channels below channel 64 are included
        upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 50));
        upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 58));
        upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 62));

        assertThat(getNeighboringCoexUnsafeChannels(5875_000,
                0, 5875_000 - 5330_000 + 1, POWER_CAP_NONE))
                .containsExactlyElementsIn(upperCoexUnsafeChannels);
    }

    /**
     * Verifies that getLowerFreqKhz() returns the correct values for an example set of inputs.
     */
    @Test
    public void testGetLowerFreqKhz_returnsCorrectValues() {
        assertThat(getLowerFreqKhz(1, WIFI_BAND_24_GHZ)).isEqualTo(2401_000);
        assertThat(getLowerFreqKhz(4, WIFI_BAND_24_GHZ)).isEqualTo(2416_000);
        assertThat(getLowerFreqKhz(6, WIFI_BAND_24_GHZ)).isEqualTo(2426_000);
        assertThat(getLowerFreqKhz(9, WIFI_BAND_24_GHZ)).isEqualTo(2441_000);
        assertThat(getLowerFreqKhz(0, WIFI_BAND_24_GHZ)).isEqualTo(INVALID_FREQ);
        assertThat(getLowerFreqKhz(14, WIFI_BAND_24_GHZ)).isEqualTo(2473_000);
        assertThat(getLowerFreqKhz(32, WIFI_BAND_5_GHZ)).isEqualTo(5150_000);
        assertThat(getLowerFreqKhz(50, WIFI_BAND_5_GHZ)).isEqualTo(5170_000);
        assertThat(getLowerFreqKhz(64, WIFI_BAND_5_GHZ)).isEqualTo(5310_000);
        assertThat(getLowerFreqKhz(96, WIFI_BAND_5_GHZ)).isEqualTo(5470_000);
        assertThat(getLowerFreqKhz(120, WIFI_BAND_5_GHZ)).isEqualTo(5590_000);
        assertThat(getLowerFreqKhz(0, WIFI_BAND_5_GHZ)).isEqualTo(INVALID_FREQ);
    }

    /**
     * Verifies that getUpperFreqKhz() returns the correct values for an example set of inputs.
     */
    @Test
    public void testGetUpperFreqKhz_returnsCorrectValues() {
        assertThat(getUpperFreqKhz(1, WIFI_BAND_24_GHZ)).isEqualTo(2423_000);
        assertThat(getUpperFreqKhz(4, WIFI_BAND_24_GHZ)).isEqualTo(2438_000);
        assertThat(getUpperFreqKhz(6, WIFI_BAND_24_GHZ)).isEqualTo(2448_000);
        assertThat(getUpperFreqKhz(9, WIFI_BAND_24_GHZ)).isEqualTo(2463_000);
        assertThat(getUpperFreqKhz(14, WIFI_BAND_24_GHZ)).isEqualTo(2495_000);
        assertThat(getUpperFreqKhz(32, WIFI_BAND_5_GHZ)).isEqualTo(5170_000);
        assertThat(getUpperFreqKhz(50, WIFI_BAND_5_GHZ)).isEqualTo(5330_000);
        assertThat(getUpperFreqKhz(64, WIFI_BAND_5_GHZ)).isEqualTo(5330_000);
        assertThat(getUpperFreqKhz(96, WIFI_BAND_5_GHZ)).isEqualTo(5490_000);
        assertThat(getUpperFreqKhz(120, WIFI_BAND_5_GHZ)).isEqualTo(5610_000);
    }

    /**
     * Verifies that get2gHarmonicUnsafeChannels returns the correct subset of 2.4GHz channels
     * from interference above, below, and in the middle of the band.
     */
    @Test
    public void testGet2gHarmonicUnsafeChannels_exampleInputs_returnsCorrectOverlap() {
        final int harmonicDeg = 2;
        final int maxOverlap = 50;
        // Test lower channels channels 1 to 7 with an overlap of 50%.
        // Channels 6, 7 should not meet the overlap.
        HashSet<CoexUnsafeChannel> lowerCoexUnsafeChannels = new HashSet<>();
        for (int i = 1; i <= 5; i += 1) {
            lowerCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, i));
        }
        int unsafeLowerKhz = getLowerFreqKhz(1, WIFI_BAND_24_GHZ) - 5_000;
        int unsafeUpperKhz = getLowerFreqKhz(7, WIFI_BAND_24_GHZ) + 5_000;
        assertThat(get2gHarmonicCoexUnsafeChannels(
                getHarmonicUlFreqKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                getHarmonicUlBandwidthKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                harmonicDeg, maxOverlap, POWER_CAP_NONE))
                .containsExactlyElementsIn(lowerCoexUnsafeChannels);

        // Test upper channels 7 to 14 with an overlap of 50%.
        // Channels 7, 8 should not meet the overlap.
        HashSet<CoexUnsafeChannel> upperCoexUnsafeChannels = new HashSet<>();
        for (int i = 9; i <= 14; i += 1) {
            upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, i));
        }
        unsafeLowerKhz = getUpperFreqKhz(7, WIFI_BAND_24_GHZ) - 5_000;
        unsafeUpperKhz = getUpperFreqKhz(14, WIFI_BAND_24_GHZ) + 5_000;
        assertThat(get2gHarmonicCoexUnsafeChannels(
                getHarmonicUlFreqKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                getHarmonicUlBandwidthKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                harmonicDeg, maxOverlap, POWER_CAP_NONE))
                .containsExactlyElementsIn(upperCoexUnsafeChannels);

        // Test middle channels 3 to 10 with an overlap of 50%.
        // Channels 3, 4, 9, 10 should not meet the overlap.
        HashSet<CoexUnsafeChannel> middleCoexUnsafeChannels = new HashSet<>();
        for (int i = 5; i <= 8; i += 1) {
            middleCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, i));
        }
        unsafeLowerKhz = getUpperFreqKhz(3, WIFI_BAND_24_GHZ) - 5_000;
        unsafeUpperKhz = getLowerFreqKhz(10, WIFI_BAND_24_GHZ) + 5_000;
        assertThat(get2gHarmonicCoexUnsafeChannels(
                getHarmonicUlFreqKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                getHarmonicUlBandwidthKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                harmonicDeg, maxOverlap, POWER_CAP_NONE))
                .containsExactlyElementsIn(middleCoexUnsafeChannels);
    }

    @Test
    public void testGet2gHarmonicUnsafeChannels_overlapDoesNotMeetThreshold_returnsNoChannels() {
        final int harmonicDeg = 3;
        final int maxOverlap = 100;
        int unsafeUpperKhz = getUpperFreqKhz(14, WIFI_BAND_24_GHZ);
        int unsafeLowerKhz = unsafeUpperKhz - 5_000;
        assertThat(get2gHarmonicCoexUnsafeChannels(
                getHarmonicUlFreqKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                getHarmonicUlBandwidthKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                harmonicDeg, maxOverlap, POWER_CAP_NONE))
                .isEmpty();
    }

    /**
     * Verifies that get5gHarmonicCoexUnsafeChannels returns the correct subset of 5GHz channels
     * from interference above, below, and in the middle of the band.
     */
    @Test
    public void testGet5gHarmonicCoexUnsafeChannels_exampleInputs_returnsCorrectOverlap() {
        final int harmonicDeg = 2;
        final int maxOverlap = 50;
        // Test lower channels 32 to 44 with an overlap of 50%.
        // Parent channel 50 should not meet the overlap.
        int unsafeLowerKhz = getLowerFreqKhz(32, WIFI_BAND_5_GHZ);
        int unsafeUpperKhz = getUpperFreqKhz(44, WIFI_BAND_5_GHZ);

        HashSet<CoexUnsafeChannel> lowerCoexUnsafeChannels = new HashSet<>();
        for (int i = 32; i <= 46; i += 2) {
            lowerCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, i));
        }
        assertThat(get5gHarmonicCoexUnsafeChannels(
                getHarmonicUlFreqKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                getHarmonicUlBandwidthKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                harmonicDeg, maxOverlap, POWER_CAP_NONE))
                .containsExactlyElementsIn(lowerCoexUnsafeChannels);

        // Test upper channels 120 to 173 with an overlap of 50%.
        // Parent channel 114 should not meet the overlap.
        unsafeLowerKhz = getLowerFreqKhz(120, WIFI_BAND_5_GHZ);
        unsafeUpperKhz = getUpperFreqKhz(173, WIFI_BAND_5_GHZ);

        HashSet<CoexUnsafeChannel> upperCoexUnsafeChannels = new HashSet<>();
        for (int i = 118; i <= 128; i += 2) {
            upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, i));
        }
        for (int i = 132; i <= 144; i += 2) {
            upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, i));
        }
        for (int i = 149; i <= 161; i += 2) {
            upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, i));
        }
        upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 165));
        upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 169));
        upperCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 173));
        assertThat(get5gHarmonicCoexUnsafeChannels(
                getHarmonicUlFreqKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                getHarmonicUlBandwidthKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                harmonicDeg, maxOverlap, POWER_CAP_NONE))
                .containsExactlyElementsIn(upperCoexUnsafeChannels);

        // Test middle channels 64 to 100 with an overlap of 50%.
        // Parent channels 50, 58, 106, 114 should not meet the overlap.
        unsafeLowerKhz = getLowerFreqKhz(64, WIFI_BAND_5_GHZ);
        unsafeUpperKhz = getUpperFreqKhz(100, WIFI_BAND_5_GHZ);

        HashSet<CoexUnsafeChannel> middleCoexUnsafeChannels = new HashSet<>();
        middleCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 62));
        middleCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 64));
        middleCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 68));
        middleCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 96));
        middleCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 100));
        middleCoexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 102));
        assertThat(get5gHarmonicCoexUnsafeChannels(
                getHarmonicUlFreqKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                getHarmonicUlBandwidthKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                harmonicDeg, maxOverlap, POWER_CAP_NONE))
                .containsExactlyElementsIn(middleCoexUnsafeChannels);
    }

    /**
     * Verifies that get5gHarmonicCoexUnsafeChannels returns no channels if the interference lands
     * in between channel 68 and 96, even though it is in the middle of the band.
     */
    @Test
    public void testGet5gHarmonicCoexUnsafeChannels_betweenChan68andChan96_returnsNoChannels() {
        final int unsafeLowerKhz = getUpperFreqKhz(68, WIFI_BAND_5_GHZ);
        final int unsafeUpperKhz = getLowerFreqKhz(96, WIFI_BAND_5_GHZ);
        final int harmonicDeg = 2;
        final int maxOverlap = 50;

        assertThat(get5gHarmonicCoexUnsafeChannels(
                getHarmonicUlFreqKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                getHarmonicUlBandwidthKhz(unsafeLowerKhz, unsafeUpperKhz, harmonicDeg),
                harmonicDeg, maxOverlap, POWER_CAP_NONE))
                .isEmpty();
    }

    /**
     * Verifies that getIntermodCoexUnsafeChannels returns the correct subset of 2.4GHz channels
     * for the example channel 3350 of LTE Band 7.
     */
    @Test
    public void testGet2gIntermodUnsafeChannels_channel3350Example_returnsCorrectWifiChannels() {
        int dlFreqKhz = 2680_000;
        int ulFreqKhz = 2560_000;
        int bandwidthKhz = 10_000;
        int maxOverlap = 100;

        Set<CoexUnsafeChannel> coexUnsafeChannels = new HashSet<>();
        for (int channel = 4; channel <= 9; channel += 1) {
            coexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, channel));
        }
        // Includes channel 6 but not channel 11
        assertThat(getIntermodCoexUnsafeChannels(ulFreqKhz, bandwidthKhz, dlFreqKhz, bandwidthKhz,
                2, -1, maxOverlap, WIFI_BAND_24_GHZ, POWER_CAP_NONE))
                .containsExactlyElementsIn(coexUnsafeChannels);
    }

    /**
     * Verifies that getIntermodCoexUnsafeChannels returns the correct subset of 5GHz channels
     * for cell channels below the wifi band.
     */
    @Test
    public void testGet5gIntermodUnsafeChannels_cellBelowWifiBand_returnsCorrectWifiChannels() {
        int dlFreqKhz = 3280_000;
        int ulFreqKhz = 2000_000;
        int bandwidthKhz = 10_000;
        int maxOverlap = 100;

        Set<CoexUnsafeChannel> coexUnsafeChannels = new HashSet<>();
        coexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 54));
        coexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 56));
        coexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 58));
        coexUnsafeChannels.add(new CoexUnsafeChannel(WIFI_BAND_5_GHZ, 50));
        assertThat(getIntermodCoexUnsafeChannels(ulFreqKhz, bandwidthKhz, dlFreqKhz, bandwidthKhz,
                -1, 1, maxOverlap, WIFI_BAND_5_GHZ, POWER_CAP_NONE))
                .containsExactlyElementsIn(coexUnsafeChannels);
    }

    /**
     * Verifies the behavior of getOffsetChannel from multiple sample inputs.
     */
    @Test
    public void testGetOffsetChannel_differentChannelStepSize_returnsChannels() {
        // Positive direction
        assertThat(getOffsetChannel(10, 1_000, 1))
                .isEqualTo(10);
        assertThat(getOffsetChannel(10, 5_000, 1))
                .isEqualTo(10);
        assertThat(getOffsetChannel(10, 5_001, 1))
                .isEqualTo(11);
        assertThat(getOffsetChannel(10, 12_000, 1))
                .isEqualTo(12);
        // Positive direction different step sizes
        assertThat(getOffsetChannel(10, 21_000, 2))
                .isEqualTo(14);
        assertThat(getOffsetChannel(10, 21_000, 3))
                .isEqualTo(13);
        assertThat(getOffsetChannel(10, 21_000, 4))
                .isEqualTo(14);

        // Negative direction
        assertThat(getOffsetChannel(10, -1_000, 1))
                .isEqualTo(10);
        assertThat(getOffsetChannel(10, -5_000, 1))
                .isEqualTo(10);
        assertThat(getOffsetChannel(10, -5_001, 1))
                .isEqualTo(9);
        assertThat(getOffsetChannel(10, -12_000, 1))
                .isEqualTo(8);
        // Negative direction different step sizes
        assertThat(getOffsetChannel(10, -21_000, 2))
                .isEqualTo(6);
        assertThat(getOffsetChannel(10, -21_000, 3))
                .isEqualTo(7);
        assertThat(getOffsetChannel(10, -21_000, 4))
                .isEqualTo(6);
    }
}
