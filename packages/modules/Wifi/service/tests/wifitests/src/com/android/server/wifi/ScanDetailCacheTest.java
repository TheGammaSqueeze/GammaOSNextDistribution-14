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

package com.android.server.wifi;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.net.wifi.WifiConfiguration;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.ScanDetailCache}.
 */
@SmallTest
public class ScanDetailCacheTest extends WifiBaseTest {
    private static final int TEST_MAX_SIZE = 5;
    private static final int TEST_TRIM_SIZE = 2;
    private static final String TEST_BSSID_1 = "0a:08:5c:67:89:01";
    private static final String TEST_BSSID_2 = "0a:08:5c:67:89:02";
    private static final String TEST_BSSID_3 = "0a:08:5c:67:89:03";
    private static final String TEST_BSSID_4 = "0a:08:5c:67:89:04";
    private static final int TEST_RSSI = -50;
    private static final int TEST_RSSI_2 = -60;
    private static final int TEST_FREQUENCY = 2412;
    private ScanDetailCache mScanDetailCache;
    WifiConfiguration mWifiConfiguration;

    @Mock Clock mClock;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mWifiConfiguration = WifiConfigurationTestUtil.createOpenNetwork();
        mScanDetailCache = new ScanDetailCache(mWifiConfiguration, TEST_MAX_SIZE, TEST_TRIM_SIZE);
    }

    @Test
    public void testPut() {
        ScanDetail scanDetail = createScanDetailForNetwork(mWifiConfiguration, TEST_BSSID_1,
                TEST_RSSI, TEST_FREQUENCY);
        mScanDetailCache.put(scanDetail);
        assertEquals(scanDetail, mScanDetailCache.getScanDetail(TEST_BSSID_1));
    }

    @Test
    public void testGetMostRecentScanResult() {
        setClockTime(1000);
        ScanDetail s1 = createScanDetailForNetwork(mWifiConfiguration, TEST_BSSID_1,
                TEST_RSSI, TEST_FREQUENCY);
        setClockTime(2000);
        ScanDetail s2 = createScanDetailForNetwork(mWifiConfiguration, TEST_BSSID_2,
                TEST_RSSI, TEST_FREQUENCY);
        setClockTime(3000);
        // s3 and s4 have the same timestamp but s3 has higher RSSI.
        ScanDetail s3 = createScanDetailForNetwork(mWifiConfiguration, TEST_BSSID_3,
                TEST_RSSI, TEST_FREQUENCY);
        ScanDetail s4 = createScanDetailForNetwork(mWifiConfiguration, TEST_BSSID_4,
                TEST_RSSI_2, TEST_FREQUENCY);

        mScanDetailCache.put(s1);
        mScanDetailCache.put(s2);
        mScanDetailCache.put(s3);
        mScanDetailCache.put(s4);

        // verify getMostRecentScanResult() returns s3 because it's the most recent scan result,
        // and has higher RSSI in comparison to s4.
        assertEquals(s3.getScanResult(), mScanDetailCache.getMostRecentScanResult());

        // verify that each individual ScanDetail also get retrieved correctly.
        assertEquals(s1, mScanDetailCache.getScanDetail(TEST_BSSID_1));
        assertEquals(s2, mScanDetailCache.getScanDetail(TEST_BSSID_2));
        assertEquals(s3, mScanDetailCache.getScanDetail(TEST_BSSID_3));
        assertEquals(s4, mScanDetailCache.getScanDetail(TEST_BSSID_4));
    }

    private void setClockTime(long millis) {
        when(mClock.getUptimeSinceBootMillis()).thenReturn(millis);
        when(mClock.getWallClockMillis()).thenReturn(millis);
    }

    /**
     * Creates a scan detail corresponding to the provided network and given BSSID, level &frequency
     * values.
     */
    private ScanDetail createScanDetailForNetwork(
            WifiConfiguration configuration, String bssid, int level, int frequency) {
        return WifiConfigurationTestUtil.createScanDetailForNetwork(configuration, bssid, level,
                frequency, mClock.getUptimeSinceBootMillis(), mClock.getWallClockMillis());
    }
}
