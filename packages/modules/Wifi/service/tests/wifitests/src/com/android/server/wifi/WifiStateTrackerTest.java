/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.BatteryStatsManager;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link com.android.server.wifi.WifiStateTracker}. */
@SmallTest
public class WifiStateTrackerTest extends WifiBaseTest {

    private static final String TAG = "WifiStateTrackerTest";

    private static final String WLAN0 = "wlan0";
    private static final String WLAN1 = "wlan1";

    @Mock BatteryStatsManager mBatteryStats;
    private WifiStateTracker mWifiStateTracker;

    /**
     * Setup test.
     */
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mWifiStateTracker = new WifiStateTracker(mBatteryStats);
    }

    /**
     * Ensure BatteryStats's reportWifiState() is called when the method
     * updateState() is invoked on WifiStateTracker for relevant states.
     */
    @Test
    public void testBatteryStatsUpdated() throws Exception {
        int[] relevantStates = {
                WifiStateTracker.SCAN_MODE,
                WifiStateTracker.CONNECTED,
                WifiStateTracker.DISCONNECTED,
                WifiStateTracker.SOFT_AP
        };
        for (int relevantState : relevantStates) {
            mWifiStateTracker.updateState(WLAN0, relevantState);
        }
        verify(mBatteryStats, times(relevantStates.length)).reportWifiState(anyInt(), any());
    }

    /**
     * Ensure BatteryStats's reportWifiState() is not called when the method
     * updateState() is invoked on WifiStateTracker for irrelevant states.
     */
    @Test
    public void testBatteryStatsNotUpdated() throws Exception {
        int[] irrelevantStates = {
                WifiStateTracker.SCAN_MODE - 1,
                WifiStateTracker.SOFT_AP + 1
        };
        for (int irrelevantState : irrelevantStates) {
            mWifiStateTracker.updateState(WLAN0, irrelevantState);
        }
        verify(mBatteryStats, times(0)).reportWifiState(anyInt(), any());
    }

    @Test
    public void updateOnMultipleIfaces() throws Exception {
        mWifiStateTracker.updateState(WLAN0, WifiStateTracker.INVALID);
        verify(mBatteryStats, never()).reportWifiState(anyInt(), any());

        mWifiStateTracker.updateState(WLAN0, WifiStateTracker.DISCONNECTED);
        verify(mBatteryStats).reportWifiState(
                BatteryStatsManager.WIFI_STATE_ON_DISCONNECTED, null);

        mWifiStateTracker.updateState(WLAN0, WifiStateTracker.CONNECTED);
        verify(mBatteryStats).reportWifiState(
                BatteryStatsManager.WIFI_STATE_ON_CONNECTED_STA, null);

        mWifiStateTracker.updateState(WLAN1, WifiStateTracker.DISCONNECTED);
        verify(mBatteryStats).reportWifiState(
                BatteryStatsManager.WIFI_STATE_ON_CONNECTED_STA, null);

        mWifiStateTracker.updateState(WLAN1, WifiStateTracker.CONNECTED);
        verify(mBatteryStats).reportWifiState(
                BatteryStatsManager.WIFI_STATE_ON_CONNECTED_STA, null);

        mWifiStateTracker.updateState(WLAN0, WifiStateTracker.DISCONNECTED);
        verify(mBatteryStats).reportWifiState(
                BatteryStatsManager.WIFI_STATE_ON_CONNECTED_STA, null);

        mWifiStateTracker.updateState(WLAN1, WifiStateTracker.DISCONNECTED);
        verify(mBatteryStats, times(2)).reportWifiState(
                BatteryStatsManager.WIFI_STATE_ON_DISCONNECTED, null);
        verify(mBatteryStats, times(3)).reportWifiState(anyInt(), any());

        mWifiStateTracker.updateState(WLAN1, WifiStateTracker.INVALID);
        mWifiStateTracker.updateState(WLAN0, WifiStateTracker.INVALID);
        verify(mBatteryStats, times(3)).reportWifiState(anyInt(), any());
    }
}
