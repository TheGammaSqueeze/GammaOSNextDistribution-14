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
package android.telephony.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.telephony.AccessNetworkConstants;
import android.telephony.PhysicalChannelConfig;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import org.junit.Before;
import org.junit.Test;

public class PhysicalChannelConfigTest {

    private static final int[] CONTEXT_IDS = new int[] {123, 555, 1, 0};
    private static final int BAND = 1;
    private static final int CONNECTION_STATUS = PhysicalChannelConfig.CONNECTION_PRIMARY_SERVING;
    private static final int CELL_BANDWIDTH = 12345;
    private static final int CHANNEL_NUMBER = 1234;
    private static final int DOWNLINK_FREQUENCY = 11100;
    private static final int UPLINK_FREQUENCY = 11100;
    private static final int FREQUENCY_RANGE = 1;
    private static final int PHYSICAL_CELL_ID = 502;
    private static final int PHYSICAL_INVALID_CELL_ID = 1008;
    private static final int NETWORK_TYPE_NR = TelephonyManager.NETWORK_TYPE_NR;
    private static final int NETWORK_TYPE_LTE = TelephonyManager.NETWORK_TYPE_LTE;
    private static final int NETWORK_TYPE_UMTS = TelephonyManager.NETWORK_TYPE_UMTS;
    private static final int NETWORK_TYPE_GSM = TelephonyManager.NETWORK_TYPE_GSM;


    private PhysicalChannelConfig mPhysicalChannelConfig;

    @Before
    public void setUp() throws Exception {
        mPhysicalChannelConfig = new PhysicalChannelConfig.Builder()
                .setPhysicalCellId(PHYSICAL_CELL_ID)
                .setNetworkType(NETWORK_TYPE_LTE)
                .setCellConnectionStatus(CONNECTION_STATUS)
                .setCellBandwidthDownlinkKhz(CELL_BANDWIDTH)
                .setCellBandwidthUplinkKhz(CELL_BANDWIDTH)
                .setContextIds(CONTEXT_IDS)
                .setFrequencyRange(FREQUENCY_RANGE)
                .setDownlinkChannelNumber(CHANNEL_NUMBER)
                .setUplinkChannelNumber(CHANNEL_NUMBER)
                .setBand(BAND)
                .build();
    }

    @Test
    public void testInvalidPhysicalChannelConfig() {
        try {
            mPhysicalChannelConfig = new PhysicalChannelConfig.Builder()
                    .setNetworkType(NETWORK_TYPE_LTE)
                    .setPhysicalCellId(PHYSICAL_INVALID_CELL_ID)
                    .setCellConnectionStatus(CONNECTION_STATUS)
                    .setCellBandwidthDownlinkKhz(CELL_BANDWIDTH)
                    .setCellBandwidthUplinkKhz(CELL_BANDWIDTH)
                    .setContextIds(CONTEXT_IDS)
                    .setFrequencyRange(FREQUENCY_RANGE)
                    .setDownlinkChannelNumber(CHANNEL_NUMBER)
                    .setUplinkChannelNumber(CHANNEL_NUMBER)
                    .setBand(BAND)
                    .build();
            fail("Physical cell Id: 1008 is over limit");
        } catch (IllegalArgumentException expected) { }
    }

    @Test
    public void testGetCellBandwidthDownlinkKhz() {
        assertEquals(CELL_BANDWIDTH, mPhysicalChannelConfig.getCellBandwidthDownlinkKhz());
    }

    @Test
    public void testGetCellBandwidthUplinkKhz() {
        assertEquals(CELL_BANDWIDTH, mPhysicalChannelConfig.getCellBandwidthUplinkKhz());
    }

    @Test
    public void testGetConnectionStatus() {
        assertEquals(CONNECTION_STATUS, mPhysicalChannelConfig.getConnectionStatus());
    }

    @Test
    public void testGetNetworkType() {
        assertEquals(NETWORK_TYPE_LTE, mPhysicalChannelConfig.getNetworkType());
    }

    @Test
    public void testGetPhysicalCellId() {
        assertEquals(PHYSICAL_CELL_ID, mPhysicalChannelConfig.getPhysicalCellId());
    }

    @Test
    public void testGetBand() {
        assertEquals(BAND, mPhysicalChannelConfig.getBand());
    }

    @Test
    public void testGetDownlinkChannelNumber() {
        assertEquals(CHANNEL_NUMBER, mPhysicalChannelConfig.getDownlinkChannelNumber());
    }

    @Test
    public void testGetUpChannelNumber() {
        assertEquals(CHANNEL_NUMBER, mPhysicalChannelConfig.getUplinkChannelNumber());
    }

    @Test
    public void testGetContextId() {
        assertEquals(CONTEXT_IDS, mPhysicalChannelConfig.getContextIds());
    }

    @Test
    public void testFrequencyRange() {
        assertEquals(FREQUENCY_RANGE, mPhysicalChannelConfig.getFrequencyRange());
    }

    @Test
    public void testFrequencyRangeForNrArfcnFromBand() {
        mPhysicalChannelConfig = new PhysicalChannelConfig.Builder()
                .setPhysicalCellId(PHYSICAL_CELL_ID)
                .setNetworkType(NETWORK_TYPE_NR)
                .setCellConnectionStatus(CONNECTION_STATUS)
                .setCellBandwidthDownlinkKhz(CELL_BANDWIDTH)
                .setCellBandwidthUplinkKhz(CELL_BANDWIDTH)
                .setContextIds(CONTEXT_IDS)
                .setDownlinkChannelNumber(4500)
                .setUplinkChannelNumber(4500)
                .setBand(AccessNetworkConstants.NgranBands.BAND_79)
                .build();

        assertThat(mPhysicalChannelConfig.getFrequencyRange()).isEqualTo(
                ServiceState.FREQUENCY_RANGE_HIGH);
    }

    @Test
    public void testFrequencyRangeForNrArfcnFromChannelNumber() {
        mPhysicalChannelConfig = new PhysicalChannelConfig.Builder()
                .setPhysicalCellId(PHYSICAL_CELL_ID)
                .setNetworkType(NETWORK_TYPE_NR)
                .setCellConnectionStatus(CONNECTION_STATUS)
                .setCellBandwidthDownlinkKhz(CELL_BANDWIDTH)
                .setCellBandwidthUplinkKhz(CELL_BANDWIDTH)
                .setContextIds(CONTEXT_IDS)
                .setDownlinkChannelNumber(4500)
                .setUplinkChannelNumber(4500)
                .setBand(100)
                .build();

        assertThat(mPhysicalChannelConfig.getFrequencyRange()).isEqualTo(
                ServiceState.FREQUENCY_RANGE_LOW);
    }

    private void setupNrPhysicalChannelConfig() {
        mPhysicalChannelConfig = new PhysicalChannelConfig.Builder()
                .setPhysicalCellId(PHYSICAL_CELL_ID)
                .setNetworkType(NETWORK_TYPE_NR)
                .setCellConnectionStatus(CONNECTION_STATUS)
                .setCellBandwidthDownlinkKhz(CELL_BANDWIDTH)
                .setCellBandwidthUplinkKhz(CELL_BANDWIDTH)
                .setContextIds(CONTEXT_IDS)
                .setDownlinkChannelNumber(2220)
                .setUplinkChannelNumber(2220)
                .setBand(BAND)
                .build();
    }

    @Test
    public void testUplinkFrequencyKhz() {
        setupNrPhysicalChannelConfig();

        assertEquals(UPLINK_FREQUENCY, mPhysicalChannelConfig.getUplinkFrequencyKhz());
    }

    @Test
    public void testDownlinkFrequencyKhz() {
        setupNrPhysicalChannelConfig();

        assertEquals(DOWNLINK_FREQUENCY, mPhysicalChannelConfig.getDownlinkFrequencyKhz());
    }
}
