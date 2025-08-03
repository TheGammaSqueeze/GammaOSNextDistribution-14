/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.server.wifi.util.InformationElementUtil.BssLoad.INVALID;
import static com.android.server.wifi.util.InformationElementUtil.BssLoad.MAX_CHANNEL_UTILIZATION;
import static com.android.server.wifi.util.InformationElementUtil.BssLoad.MIN_CHANNEL_UTILIZATION;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.nl80211.DeviceWiphyCapabilities;

import androidx.test.filters.SmallTest;

import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

/**
 * Unit tests for {@link com.android.server.wifi.ThroughputPredictor}.
 */
@SmallTest
public class ThroughputPredictorTest extends WifiBaseTest {
    @Mock private DeviceWiphyCapabilities mDeviceCapabilities;
    @Mock private Context mContext;
    // For simulating the resources, we use a Spy on a MockResource
    // (which is really more of a stub than a mock, in spite if its name).
    // This is so that we get errors on any calls that we have not explicitly set up.
    @Spy
    private MockResources mResource = new MockResources();
    ThroughputPredictor mThroughputPredictor;
    WifiNative.ConnectionCapabilities mConnectionCap = new WifiNative.ConnectionCapabilities();

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mDeviceCapabilities.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11N))
                .thenReturn(true);
        when(mDeviceCapabilities.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AC))
                .thenReturn(true);
        when(mDeviceCapabilities.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AX))
                .thenReturn(false);
        when(mDeviceCapabilities.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE))
                .thenReturn(false);
        when(mDeviceCapabilities.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_40MHZ))
                .thenReturn(true);
        when(mDeviceCapabilities.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_80MHZ))
                .thenReturn(true);
        when(mDeviceCapabilities.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_160MHZ))
                .thenReturn(false);
        when(mDeviceCapabilities.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_320MHZ))
                .thenReturn(false);
        when(mDeviceCapabilities.getMaxNumberTxSpatialStreams()).thenReturn(2);
        when(mDeviceCapabilities.getMaxNumberRxSpatialStreams()).thenReturn(2);

        when(mResource.getBoolean(
                R.bool.config_wifiFrameworkMaxNumSpatialStreamDeviceOverrideEnable))
                .thenReturn(false);
        when(mResource.getInteger(
                R.integer.config_wifiFrameworkMaxNumSpatialStreamDeviceOverrideValue))
                .thenReturn(2);
        when(mContext.getResources()).thenReturn(mResource);
        mThroughputPredictor = new ThroughputPredictor(mContext);
        mThroughputPredictor.enableVerboseLogging(true);
    }

    /** Cleans up test. */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    @Test
    public void verify6GhzRssiBoost() {
        // First make sure the boost is disabled
        when(mResource.getBoolean(R.bool.config_wifiEnable6GhzBeaconRssiBoost)).thenReturn(false);
        int predicted_5Ghz_80MHz = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AX, ScanResult.CHANNEL_WIDTH_80MHZ, -70, 5160, 1,
                0, 0, false, null);
        int predicted_6Ghz_20MHz = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AX, ScanResult.CHANNEL_WIDTH_20MHZ, -70, 5975, 1,
                0, 0, false, null);
        int predicted_6Ghz_80MHz = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AX, ScanResult.CHANNEL_WIDTH_80MHZ, -70, 5975, 1,
                0, 0, false, null);

        // verify that after the boost is enabled, only the 6Ghz 80MHz bandwidth score is increased.
        when(mResource.getBoolean(R.bool.config_wifiEnable6GhzBeaconRssiBoost)).thenReturn(true);
        int newPredicted_5Ghz_80MHz = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AX, ScanResult.CHANNEL_WIDTH_80MHZ, -70, 5160, 1,
                0, 0, false, null);
        int newPredicted_6Ghz_20MHz = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AX, ScanResult.CHANNEL_WIDTH_20MHZ, -70, 5975, 1,
                0, 0, false, null);
        int newPredicted_6Ghz_80MHz = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AX, ScanResult.CHANNEL_WIDTH_80MHZ, -70, 5975, 1,
                0, 0, false, null);

        assertEquals("5Ghz AP should not get RSSI boost",
                predicted_5Ghz_80MHz, newPredicted_5Ghz_80MHz);
        assertEquals("6Ghz AP with 20MHz bandwidth should not get RSSI boost",
                predicted_6Ghz_20MHz, newPredicted_6Ghz_20MHz);
        assertTrue("6Ghz AP with 80MHz bandwidth should get RSSI boost",
                predicted_6Ghz_80MHz < newPredicted_6Ghz_80MHz);
    }

    @Test
    public void verifyVeryLowRssi() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AC, ScanResult.CHANNEL_WIDTH_20MHZ, -200, 2412, 1,
                0, 0, false, null);

        assertEquals(0, predictedThroughputMbps);
    }

    @Test
    public void verifyMaxChannelUtilizationBssLoad() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AC, ScanResult.CHANNEL_WIDTH_20MHZ, 0, 2412, 1,
                MAX_CHANNEL_UTILIZATION, 0, false, null);

        assertEquals(0, predictedThroughputMbps);
    }

    @Test
    public void verifyMaxChannelUtilizationLinkLayerStats() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AC, ScanResult.CHANNEL_WIDTH_20MHZ, 0, 5210, 1,
                INVALID, MAX_CHANNEL_UTILIZATION, false, null);

        assertEquals(0, predictedThroughputMbps);
    }

    @Test
    public void verifyHighRssiMinChannelUtilizationAc5g80Mhz2ss() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AC, ScanResult.CHANNEL_WIDTH_80MHZ, 0, 5180, 2,
                MIN_CHANNEL_UTILIZATION, 50, false, null);

        assertEquals(866, predictedThroughputMbps);
    }

    @Test
    public void verifyHighRssiMinChannelUtilizationAc5g80Mhz2ssOverriddenTo1ss() {
        when(mResource.getBoolean(
                R.bool.config_wifiFrameworkMaxNumSpatialStreamDeviceOverrideEnable))
                .thenReturn(true);
        when(mResource.getInteger(
                R.integer.config_wifiFrameworkMaxNumSpatialStreamDeviceOverrideValue))
                .thenReturn(1);
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AC, ScanResult.CHANNEL_WIDTH_80MHZ, 0, 5180, 2,
                MIN_CHANNEL_UTILIZATION, 50, false, null);

        assertEquals(433, predictedThroughputMbps);
    }

    @Test
    public void verifyHighRssiMinChannelUtilizationAx6g320Mhz4ss() {
        when(mDeviceCapabilities.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE))
                .thenReturn(true);
        when(mDeviceCapabilities.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_320MHZ))
                .thenReturn(true);
        when(mDeviceCapabilities.getMaxNumberTxSpatialStreams()).thenReturn(4);
        when(mDeviceCapabilities.getMaxNumberRxSpatialStreams()).thenReturn(4);

        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11BE, ScanResult.CHANNEL_WIDTH_320MHZ, 0, 6180, 4,
                MIN_CHANNEL_UTILIZATION, INVALID, false, null);

        assertEquals(11529, predictedThroughputMbps);
    }

    @Test
    public void verifyHighRssiMinChannelUtilizationAx5g160Mhz4ssOverriddenTo2ss() {
        when(mResource.getBoolean(
                R.bool.config_wifiFrameworkMaxNumSpatialStreamDeviceOverrideEnable))
                .thenReturn(true);
        when(mDeviceCapabilities.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AX))
                .thenReturn(true);
        when(mDeviceCapabilities.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_160MHZ))
                .thenReturn(true);
        when(mDeviceCapabilities.getMaxNumberTxSpatialStreams()).thenReturn(4);
        when(mDeviceCapabilities.getMaxNumberRxSpatialStreams()).thenReturn(4);

        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AX, ScanResult.CHANNEL_WIDTH_160MHZ, 0, 5180, 4,
                MIN_CHANNEL_UTILIZATION, INVALID, false, null);

        assertEquals(2401, predictedThroughputMbps);
    }


    @Test
    public void verifyMidRssiMinChannelUtilizationAc5g80Mhz2ss() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AC, ScanResult.CHANNEL_WIDTH_80MHZ, -50, 5180, 2,
                MIN_CHANNEL_UTILIZATION, INVALID, false, null);

        assertEquals(866, predictedThroughputMbps);
    }

    @Test
    public void verifyLowRssiMinChannelUtilizationAc5g80Mhz2ss() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AC, ScanResult.CHANNEL_WIDTH_80MHZ, -80, 5180, 2,
                MIN_CHANNEL_UTILIZATION, INVALID, false, null);

        assertEquals(41, predictedThroughputMbps);
    }

    @Test
    public void verifyLowRssiDefaultChannelUtilizationAc5g80Mhz2ss() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AC, ScanResult.CHANNEL_WIDTH_80MHZ, -80, 5180, 2,
                INVALID, INVALID, false, null);

        assertEquals(31, predictedThroughputMbps);
    }

    @Test
    public void verifyHighRssiMinChannelUtilizationAc2g20Mhz2ss() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AC, ScanResult.CHANNEL_WIDTH_20MHZ, -20, 2437, 2,
                MIN_CHANNEL_UTILIZATION, INVALID, false, null);

        assertEquals(192, predictedThroughputMbps);
    }

    @Test
    public void verifyHighRssiMinChannelUtilizationAc2g20Mhz2ssBluetoothConnected() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AC, ScanResult.CHANNEL_WIDTH_20MHZ, -20, 2437, 2,
                MIN_CHANNEL_UTILIZATION, INVALID, true, null);

        assertEquals(144, predictedThroughputMbps);
    }

    @Test
    public void verifyHighRssiMinChannelUtilizationLegacy5g20Mhz() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_LEGACY, ScanResult.CHANNEL_WIDTH_20MHZ, -50, 5180,
                1, MIN_CHANNEL_UTILIZATION, INVALID, false, null);

        assertEquals(54, predictedThroughputMbps);
    }

    @Test
    public void verifyLowRssiDefaultChannelUtilizationLegacy5g20Mhz() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_LEGACY, ScanResult.CHANNEL_WIDTH_20MHZ, -80, 5180,
                2, INVALID, INVALID, false, null);

        assertEquals(11, predictedThroughputMbps);
    }

    @Test
    public void verifyHighRssiMinChannelUtilizationHt2g20Mhz2ss() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11N, ScanResult.CHANNEL_WIDTH_20MHZ, -50, 2437, 2,
                MIN_CHANNEL_UTILIZATION, INVALID, false, null);

        assertEquals(144, predictedThroughputMbps);
    }

    @Test
    public void verifyHighRssiMinChannelUtilizationHt2g20MhzIncorrectNss() {
        when(mDeviceCapabilities.getMaxNumberTxSpatialStreams()).thenReturn(0);
        when(mDeviceCapabilities.getMaxNumberRxSpatialStreams()).thenReturn(0);
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11N, ScanResult.CHANNEL_WIDTH_20MHZ, -50, 2437, 2,
                MIN_CHANNEL_UTILIZATION, INVALID, false, null);
        // Expect to 1SS peak rate because maxNumberSpatialStream is overridden to 1.
        assertEquals(72, predictedThroughputMbps);
    }

    @Test
    public void verifyLowRssiDefaultChannelUtilizationHt2g20Mhz1ss() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11N, ScanResult.CHANNEL_WIDTH_20MHZ, -80, 2437, 1,
                INVALID, INVALID, true, null);

        assertEquals(5, predictedThroughputMbps);
    }

    @Test
    public void verifyHighRssiHighChannelUtilizationAc2g20Mhz2ss() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AC, ScanResult.CHANNEL_WIDTH_20MHZ, -50, 2437, 2,
                INVALID, 80, true, null);

        assertEquals(84, predictedThroughputMbps);
    }

    @Test
    public void verifyRssiBoundaryHighChannelUtilizationAc2g20Mhz2ss() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AC, ScanResult.CHANNEL_WIDTH_20MHZ, -69, 2437, 2,
                INVALID, 80, true, null);

        assertEquals(46, predictedThroughputMbps);
    }

    @Test
    public void verifyRssiBoundaryHighChannelUtilizationAc5g40Mhz2ss() {
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11AC, ScanResult.CHANNEL_WIDTH_40MHZ, -66, 5180, 2,
                INVALID, 80, false, null);

        assertEquals(103, predictedThroughputMbps);
    }

    @Test
    public void verifyMaxThroughput11BMode() {
        mConnectionCap.wifiStandard = ScanResult.WIFI_STANDARD_LEGACY;
        mConnectionCap.is11bMode = true;
        mConnectionCap.channelBandwidth = ScanResult.CHANNEL_WIDTH_20MHZ;
        mConnectionCap.maxNumberTxSpatialStreams = 2;
        assertEquals(11, mThroughputPredictor.predictMaxTxThroughput(mConnectionCap));
    }

    @Test
    public void verifyMaxThroughputAc40Mhz2ss() {
        mConnectionCap.wifiStandard = ScanResult.WIFI_STANDARD_11AC;
        mConnectionCap.channelBandwidth = ScanResult.CHANNEL_WIDTH_40MHZ;
        mConnectionCap.maxNumberTxSpatialStreams = 2;
        assertEquals(400, mThroughputPredictor.predictMaxTxThroughput(mConnectionCap));
    }

    @Test
    public void verifyMaxThroughputAc80Mhz2ss() {
        mConnectionCap.wifiStandard = ScanResult.WIFI_STANDARD_11AC;
        mConnectionCap.channelBandwidth = ScanResult.CHANNEL_WIDTH_80MHZ;
        mConnectionCap.maxNumberRxSpatialStreams = 2;
        assertEquals(866, mThroughputPredictor.predictMaxRxThroughput(mConnectionCap));
    }

    @Test
    public void verifyMaxThroughputAc80MhzIncorrectNss() {
        mConnectionCap.wifiStandard = ScanResult.WIFI_STANDARD_11AC;
        mConnectionCap.channelBandwidth = ScanResult.CHANNEL_WIDTH_80MHZ;
        mConnectionCap.maxNumberRxSpatialStreams = -5;
        // Expect to 1SS peak rate because maxNumberSpatialStream is overridden to 1.
        assertEquals(433, mThroughputPredictor.predictMaxRxThroughput(mConnectionCap));
    }

    @Test
    public void verifyMaxThroughputN20Mhz1ss() {
        mConnectionCap.wifiStandard = ScanResult.WIFI_STANDARD_11N;
        mConnectionCap.channelBandwidth = ScanResult.CHANNEL_WIDTH_20MHZ;
        mConnectionCap.maxNumberRxSpatialStreams = 1;
        assertEquals(72, mThroughputPredictor.predictMaxRxThroughput(mConnectionCap));
    }

    @Test
    public void verifyMaxThroughputLegacy20Mhz1ss() {
        mConnectionCap.wifiStandard = ScanResult.WIFI_STANDARD_LEGACY;
        mConnectionCap.channelBandwidth = ScanResult.CHANNEL_WIDTH_80MHZ;
        mConnectionCap.maxNumberRxSpatialStreams = 1;
        assertEquals(54, mThroughputPredictor.predictMaxRxThroughput(mConnectionCap));
    }

    @Test
    public void verifyMaxThroughputAx80Mhz2ss() {
        mConnectionCap.wifiStandard = ScanResult.WIFI_STANDARD_11AX;
        mConnectionCap.channelBandwidth = ScanResult.CHANNEL_WIDTH_80MHZ;
        mConnectionCap.maxNumberRxSpatialStreams = 2;
        assertEquals(1200, mThroughputPredictor.predictMaxRxThroughput(mConnectionCap));
    }

    @Test
    public void verifyMaxThroughputBe320Mhz2ss() {
        mConnectionCap.wifiStandard = ScanResult.WIFI_STANDARD_11BE;
        mConnectionCap.channelBandwidth = ScanResult.CHANNEL_WIDTH_320MHZ;
        mConnectionCap.maxNumberRxSpatialStreams = 2;
        assertEquals(5764, mThroughputPredictor.predictMaxRxThroughput(mConnectionCap));
    }

    @Test
    public void verifyMaxThroughputUnknownStandard() {
        mConnectionCap.wifiStandard = ScanResult.WIFI_STANDARD_UNKNOWN;
        mConnectionCap.channelBandwidth = ScanResult.CHANNEL_WIDTH_80MHZ;
        mConnectionCap.maxNumberTxSpatialStreams = 2;
        assertEquals(-1, mThroughputPredictor.predictMaxTxThroughput(mConnectionCap));
    }

    @Test
    public void verifyTxThroughput11BModeLowSnr() {
        mConnectionCap.wifiStandard = ScanResult.WIFI_STANDARD_LEGACY;
        mConnectionCap.is11bMode = true;
        mConnectionCap.channelBandwidth = ScanResult.CHANNEL_WIDTH_20MHZ;
        mConnectionCap.maxNumberTxSpatialStreams = 1;
        assertEquals(8, mThroughputPredictor.predictTxThroughput(mConnectionCap,
                -80, 2437, 80));
    }

    @Test
    public void verifyTxThroughputAc20Mhz2ssMiddleSnr() {
        mConnectionCap.wifiStandard = ScanResult.WIFI_STANDARD_11AC;
        mConnectionCap.channelBandwidth = ScanResult.CHANNEL_WIDTH_20MHZ;
        mConnectionCap.maxNumberTxSpatialStreams = 2;
        assertEquals(131, mThroughputPredictor.predictTxThroughput(mConnectionCap,
                -50, 2437, 80));
    }

    @Test
    public void verifyRxThroughputAx160Mhz4ssHighSnrInvalidUtilization() {
        mConnectionCap.wifiStandard = ScanResult.WIFI_STANDARD_11AX;
        mConnectionCap.channelBandwidth = ScanResult.CHANNEL_WIDTH_160MHZ;
        mConnectionCap.maxNumberRxSpatialStreams = 4;
        //mConnectionCap.maxNumberTxSpatialStreams = 4;
        assertEquals(2881, mThroughputPredictor.predictRxThroughput(mConnectionCap,
                -10, 5180, INVALID));
    }

    @Test
    public void verifyThroughputBe5g160Mhz4ssDisabledSubchannelBitmap() {
        when(mDeviceCapabilities.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE)).thenReturn(
                true);
        when(mDeviceCapabilities.isChannelWidthSupported(
                ScanResult.CHANNEL_WIDTH_160MHZ)).thenReturn(true);
        when(mDeviceCapabilities.getMaxNumberTxSpatialStreams()).thenReturn(4);
        when(mDeviceCapabilities.getMaxNumberRxSpatialStreams()).thenReturn(4);
        int predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11BE, ScanResult.CHANNEL_WIDTH_160MHZ, 0, 5240, 4,
                MIN_CHANNEL_UTILIZATION, 50, false, null);
        assertEquals(5764, predictedThroughputMbps);

        predictedThroughputMbps = mThroughputPredictor.predictThroughput(mDeviceCapabilities,
                ScanResult.WIFI_STANDARD_11BE, ScanResult.CHANNEL_WIDTH_160MHZ, 0, 5240, 4,
                MIN_CHANNEL_UTILIZATION, 50, false, new byte[]{(byte) 0x3, (byte) 0x0});
        assertEquals(4388, predictedThroughputMbps);
    }
}
