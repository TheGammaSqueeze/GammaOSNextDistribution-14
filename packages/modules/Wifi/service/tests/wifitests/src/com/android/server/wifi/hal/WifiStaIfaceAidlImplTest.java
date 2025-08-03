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

package com.android.server.wifi.hal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.wifi.CachedScanData;
import android.hardware.wifi.CachedScanResult;
import android.hardware.wifi.IWifiStaIface;
import android.hardware.wifi.StaLinkLayerIfaceContentionTimeStats;
import android.hardware.wifi.StaLinkLayerIfacePacketStats;
import android.hardware.wifi.StaLinkLayerIfaceStats;
import android.hardware.wifi.StaLinkLayerLinkStats;
import android.hardware.wifi.StaLinkLayerRadioStats;
import android.hardware.wifi.StaLinkLayerStats;
import android.hardware.wifi.StaPeerInfo;
import android.hardware.wifi.StaRateStat;
import android.hardware.wifi.StaRoamingCapabilities;
import android.hardware.wifi.StaRoamingState;
import android.hardware.wifi.WifiChannelInfo;
import android.hardware.wifi.WifiChannelStats;
import android.hardware.wifi.WifiChannelWidthInMhz;
import android.hardware.wifi.WifiDebugPacketFateFrameInfo;
import android.hardware.wifi.WifiDebugPacketFateFrameType;
import android.hardware.wifi.WifiDebugRxPacketFate;
import android.hardware.wifi.WifiDebugRxPacketFateReport;
import android.hardware.wifi.WifiDebugTxPacketFate;
import android.hardware.wifi.WifiDebugTxPacketFateReport;
import android.hardware.wifi.WifiRateInfo;
import android.hardware.wifi.WifiRatePreamble;
import android.hardware.wifi.WifiStatusCode;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner.ScanData;
import android.net.wifi.WifiSsid;

import com.android.server.wifi.SsidTranslator;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiLinkLayerStats;
import com.android.server.wifi.WifiLoggerHal;
import com.android.server.wifi.WifiNative;
import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

public class WifiStaIfaceAidlImplTest extends WifiBaseTest {
    private static final int[] TEST_FREQUENCIES = {2412, 2417, 2422, 2427, 2432, 2437};

    private WifiStaIfaceAidlImpl mDut;
    @Mock private IWifiStaIface mIWifiStaIfaceMock;
    @Mock private Context mContextMock;
    @Mock private Resources mResourcesMock;
    @Mock private SsidTranslator mSsidTranslatorMock;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new WifiStaIfaceAidlImpl(mIWifiStaIfaceMock, mContextMock, mSsidTranslatorMock);
        when(mContextMock.getResources()).thenReturn(mResourcesMock);
    }

    /**
     * Test translation to WifiManager.WIFI_FEATURE_*
     */
    @Test
    public void testStaIfaceFeatureMaskTranslation() {
        int halFeatures = (
                IWifiStaIface.FeatureSetMask.BACKGROUND_SCAN
                        | IWifiStaIface.FeatureSetMask.LINK_LAYER_STATS
        );
        long expected = (
                WifiManager.WIFI_FEATURE_SCANNER
                        | WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        assertEquals(expected, mDut.halToFrameworkStaFeatureSet(halFeatures));
    }

    /**
     * Test that the link layer stats fields are populated correctly.
     */
    @Test
    public void testLinkLayerStatsAssignment() throws Exception {
        Random r = new Random(1775968256);
        StaLinkLayerStats stats = new StaLinkLayerStats();
        stats.iface = new StaLinkLayerIfaceStats();
        StaLinkLayerLinkStats link = new StaLinkLayerLinkStats();
        stats.iface.links = new StaLinkLayerLinkStats[]{link};
        randomizePacketStats(r, stats.iface.links[0]);
        StaLinkLayerRadioStats rstat = new StaLinkLayerRadioStats();
        randomizeRadioStats(r, rstat);
        stats.radios = new StaLinkLayerRadioStats[]{rstat};
        stats.timeStampInMs = r.nextLong() & 0xFFFFFFFFFFL;
        randomizeContentionTimeStats(r, stats.iface.links[0]);
        stats.iface.links[0].peers = randomizePeerInfoStats(r);

        WifiLinkLayerStats converted = mDut.halToFrameworkLinkLayerStats(stats);

        verifyIfaceStats(stats.iface, converted);
        verifyPerRadioStats(stats.radios, converted);
        verifyRadioStats(stats.radios[0], converted);
        assertEquals(stats.timeStampInMs, converted.timeStampInMs);
        assertEquals(WifiLinkLayerStats.V1_5, converted.version);
        assertEquals(1, converted.numRadios);
    }

    /**
     * Test that the link layer stats fields are aggregated correctly for two radios.
     */
    @Test
    public void testTwoRadioStatsAggregation() throws Exception {
        when(mResourcesMock.getBoolean(R.bool.config_wifiLinkLayerAllRadiosStatsAggregationEnabled))
                .thenReturn(true);
        Random r = new Random(245786856);
        StaLinkLayerStats stats = new StaLinkLayerStats();
        // Fill stats in two radios
        stats.radios = new StaLinkLayerRadioStats[2];
        for (int i = 0; i < 2; i++) {
            StaLinkLayerRadioStats rstat = new StaLinkLayerRadioStats();
            randomizeRadioStats(r, rstat);
            stats.radios[i] = rstat;
        }

        WifiLinkLayerStats converted = mDut.halToFrameworkLinkLayerStats(stats);
        verifyPerRadioStats(stats.radios, converted);
        verifyTwoRadioStatsAggregation(stats.radios, converted);
        assertEquals(2, converted.numRadios);
    }

    /**
     * Test that the link layer stats fields are not aggregated on setting
     * config_wifiLinkLayerAllRadiosStatsAggregationEnabled to false (default value).
     */
    @Test
    public void testRadioStatsAggregationDisabled() throws Exception {
        Random r = new Random(245786856);
        StaLinkLayerStats stats = new StaLinkLayerStats();
        // Fill stats in two radios
        for (int i = 0; i < 2; i++) {
            StaLinkLayerRadioStats rstat = new StaLinkLayerRadioStats();
            randomizeRadioStats(r, rstat);
            stats.radios = new StaLinkLayerRadioStats[]{rstat};
        }

        WifiLinkLayerStats converted = mDut.halToFrameworkLinkLayerStats(stats);
        verifyPerRadioStats(stats.radios, converted);
        verifyRadioStats(stats.radios[0], converted);
        assertEquals(1, converted.numRadios);
    }

    /**
     * Tests the retrieval of tx packet fates.
     */
    @Test
    public void testGetTxPktFates() throws Exception {
        byte[] frameContentBytes = new byte[30];
        new Random().nextBytes(frameContentBytes);
        WifiDebugTxPacketFateReport fateReport = new WifiDebugTxPacketFateReport();
        fateReport.fate = WifiDebugTxPacketFate.DRV_QUEUED;
        fateReport.frameInfo = new WifiDebugPacketFateFrameInfo();
        fateReport.frameInfo.driverTimestampUsec = new Random().nextLong();
        fateReport.frameInfo.frameType = WifiDebugPacketFateFrameType.ETHERNET_II;
        fateReport.frameInfo.frameContent = frameContentBytes;

        when(mIWifiStaIfaceMock.getDebugTxPacketFates())
                .thenReturn(new WifiDebugTxPacketFateReport[]{fateReport});

        List<WifiNative.TxFateReport> retrievedFates = mDut.getDebugTxPacketFates();
        assertEquals(1, retrievedFates.size());
        WifiNative.TxFateReport retrievedFate = retrievedFates.get(0);
        verify(mIWifiStaIfaceMock).getDebugTxPacketFates();
        assertEquals(WifiLoggerHal.TX_PKT_FATE_DRV_QUEUED, retrievedFate.mFate);
        assertEquals(fateReport.frameInfo.driverTimestampUsec, retrievedFate.mDriverTimestampUSec);
        assertEquals(WifiLoggerHal.FRAME_TYPE_ETHERNET_II, retrievedFate.mFrameType);
        assertArrayEquals(frameContentBytes, retrievedFate.mFrameBytes);
    }

    /**
     * Tests the retrieval of tx packet fates when the number of fates retrieved exceeds the
     * maximum number of packet fates fetched ({@link WifiLoggerHal#MAX_FATE_LOG_LEN}).
     */
    @Test
    public void testGetTxPktFatesExceedsInputArrayLength() throws Exception {
        byte[] frameContentBytes = new byte[30];
        new Random().nextBytes(frameContentBytes);
        WifiDebugTxPacketFateReport fateReport = new WifiDebugTxPacketFateReport();
        fateReport.fate = WifiDebugTxPacketFate.FW_DROP_OTHER;
        fateReport.frameInfo = new WifiDebugPacketFateFrameInfo();
        fateReport.frameInfo.driverTimestampUsec = new Random().nextLong();
        fateReport.frameInfo.frameType = WifiDebugPacketFateFrameType.MGMT_80211;
        fateReport.frameInfo.frameContent = frameContentBytes;

        // create twice as many as the max size
        WifiDebugTxPacketFateReport[] reports =
                new WifiDebugTxPacketFateReport[WifiLoggerHal.MAX_FATE_LOG_LEN * 2];
        for (int i = 0; i < reports.length; i++) {
            reports[i] = fateReport;
        }
        when(mIWifiStaIfaceMock.getDebugTxPacketFates()).thenReturn(reports);

        List<WifiNative.TxFateReport> retrievedFates = mDut.getDebugTxPacketFates();
        // assert that at most WifiLoggerHal.MAX_FATE_LOG_LEN is retrieved
        assertEquals(WifiLoggerHal.MAX_FATE_LOG_LEN, retrievedFates.size());
        WifiNative.TxFateReport retrievedFate = retrievedFates.get(0);
        verify(mIWifiStaIfaceMock).getDebugTxPacketFates();
        assertEquals(WifiLoggerHal.TX_PKT_FATE_FW_DROP_OTHER, retrievedFate.mFate);
        assertEquals(fateReport.frameInfo.driverTimestampUsec, retrievedFate.mDriverTimestampUSec);
        assertEquals(WifiLoggerHal.FRAME_TYPE_80211_MGMT, retrievedFate.mFrameType);
        assertArrayEquals(frameContentBytes, retrievedFate.mFrameBytes);
    }

    /**
     * Tests the retrieval of rx packet fates.
     */
    @Test
    public void testGetRxPktFates() throws Exception {
        byte[] frameContentBytes = new byte[30];
        new Random().nextBytes(frameContentBytes);
        WifiDebugRxPacketFateReport fateReport = new WifiDebugRxPacketFateReport();
        fateReport.fate = WifiDebugRxPacketFate.SUCCESS;
        fateReport.frameInfo = new WifiDebugPacketFateFrameInfo();
        fateReport.frameInfo.driverTimestampUsec = new Random().nextLong();
        fateReport.frameInfo.frameType = WifiDebugPacketFateFrameType.ETHERNET_II;
        fateReport.frameInfo.frameContent = frameContentBytes;

        when(mIWifiStaIfaceMock.getDebugRxPacketFates())
                .thenReturn(new WifiDebugRxPacketFateReport[]{fateReport});

        List<WifiNative.RxFateReport> retrievedFates = mDut.getDebugRxPacketFates();
        assertEquals(1, retrievedFates.size());
        WifiNative.RxFateReport retrievedFate = retrievedFates.get(0);
        verify(mIWifiStaIfaceMock).getDebugRxPacketFates();
        assertEquals(WifiLoggerHal.RX_PKT_FATE_SUCCESS, retrievedFate.mFate);
        assertEquals(fateReport.frameInfo.driverTimestampUsec, retrievedFate.mDriverTimestampUSec);
        assertEquals(WifiLoggerHal.FRAME_TYPE_ETHERNET_II, retrievedFate.mFrameType);
        assertArrayEquals(frameContentBytes, retrievedFate.mFrameBytes);
    }

    /**
     * Tests the retrieval of rx packet fates when the number of fates retrieved exceeds the
     * maximum number of packet fates fetched ({@link WifiLoggerHal#MAX_FATE_LOG_LEN}).
     */
    @Test
    public void testGetRxPktFatesExceedsInputArrayLength() throws Exception {
        byte[] frameContentBytes = new byte[30];
        new Random().nextBytes(frameContentBytes);
        WifiDebugRxPacketFateReport fateReport = new WifiDebugRxPacketFateReport();
        fateReport.fate = WifiDebugRxPacketFate.FW_DROP_FILTER;
        fateReport.frameInfo = new WifiDebugPacketFateFrameInfo();
        fateReport.frameInfo.driverTimestampUsec = new Random().nextLong();
        fateReport.frameInfo.frameType = WifiDebugPacketFateFrameType.MGMT_80211;
        fateReport.frameInfo.frameContent = frameContentBytes;

        // create twice as many as the max size
        WifiDebugRxPacketFateReport[] reports =
                new WifiDebugRxPacketFateReport[WifiLoggerHal.MAX_FATE_LOG_LEN * 2];
        for (int i = 0; i < reports.length; i++) {
            reports[i] = fateReport;
        }
        when(mIWifiStaIfaceMock.getDebugRxPacketFates()).thenReturn(reports);

        List<WifiNative.RxFateReport> retrievedFates = mDut.getDebugRxPacketFates();
        assertEquals(WifiLoggerHal.MAX_FATE_LOG_LEN, retrievedFates.size());
        verify(mIWifiStaIfaceMock).getDebugRxPacketFates();
        WifiNative.RxFateReport retrievedFate = retrievedFates.get(0);
        assertEquals(WifiLoggerHal.RX_PKT_FATE_FW_DROP_FILTER, retrievedFate.mFate);
        assertEquals(fateReport.frameInfo.driverTimestampUsec, retrievedFate.mDriverTimestampUSec);
        assertEquals(WifiLoggerHal.FRAME_TYPE_80211_MGMT, retrievedFate.mFrameType);
        assertArrayEquals(frameContentBytes, retrievedFate.mFrameBytes);
    }

    /**
     * Tests the retrieval of firmware roaming capabilities.
     */
    @Test
    public void testFirmwareRoamingCapabilityRetrieval() throws Exception {
        for (int i = 0; i < 4; i++) {
            int blocklistSize = i + 10;
            int allowlistSize = i * 3;
            StaRoamingCapabilities caps = new StaRoamingCapabilities();
            caps.maxBlocklistSize = blocklistSize;
            caps.maxAllowlistSize = allowlistSize;
            when(mIWifiStaIfaceMock.getRoamingCapabilities()).thenReturn(caps);
            WifiNative.RoamingCapabilities roamCap = mDut.getRoamingCapabilities();
            assertNotNull(roamCap);
            assertEquals(blocklistSize, roamCap.maxBlocklistSize);
            assertEquals(allowlistSize, roamCap.maxAllowlistSize);
        }
    }

    /**
     * Tests enableFirmwareRoaming failure case due to an invalid argument.
     */
    @Test
    public void testEnableFirmwareRoamingFailureInvalidArgument() throws Exception {
        final int badState = WifiNative.DISABLE_FIRMWARE_ROAMING
                + WifiNative.ENABLE_FIRMWARE_ROAMING + 1;
        assertEquals(WifiNative.SET_FIRMWARE_ROAMING_FAILURE, mDut.setRoamingState(badState));
    }

    /**
     * Test the retrieval of cached scan data
     */
    @Test
    public void testGetCachedScanData() throws Exception {
        CachedScanData halData = new CachedScanData();
        CachedScanResult[] halResults = new CachedScanResult[2];
        CachedScanResult halResult = new CachedScanResult();
        halData.cachedScanResults = halResults;
        halResults[0] = halResult;
        halResult.timeStampInUs = 10_000_000;
        halResult.bssid = new byte[]{0x61, 0x52, 0x43, 0x34, 0x25, 0x16};
        String test_ssid = "Test SSID";
        halResult.ssid = test_ssid.getBytes(StandardCharsets.UTF_8);
        halResult.rssiDbm = -80;
        halResult.frequencyMhz = 5260;
        halResult.channelWidthMhz = WifiChannelWidthInMhz.WIDTH_80;
        halResult.preambleType = WifiRatePreamble.HE;

        when(mIWifiStaIfaceMock.getCachedScanData())
                .thenReturn(halData);
        when(mSsidTranslatorMock.getTranslatedSsidAndRecordBssidCharset(any(), any()))
                .thenReturn(WifiSsid.fromBytes(halResult.ssid));

        ScanData scanData = mDut.getCachedScanData();
        ScanResult[] scanResults = scanData.getResults();
        assertEquals(1, scanResults.length);
        assertEquals(10_000_000, scanResults[0].timestamp);
        assertEquals(-80, scanResults[0].level);
        assertEquals(5260, scanResults[0].frequency);
        assertEquals(ScanResult.CHANNEL_WIDTH_80MHZ, scanResults[0].channelWidth);
        assertEquals(ScanResult.WIFI_STANDARD_11AX, scanResults[0].getWifiStandard());
        assertEquals("61:52:43:34:25:16", scanResults[0].BSSID);
    }

    /**
     * Tests setRoamingMode.
     */
    @Test
    public void testSetRoamingMode() throws Exception {
        final int invalidRoamingMode = -1;
        assertEquals(WifiStatusCode.ERROR_INVALID_ARGS, mDut.setRoamingMode(invalidRoamingMode));

        assertEquals(WifiStatusCode.SUCCESS, mDut.setRoamingMode(WifiManager.ROAMING_MODE_NORMAL));
        verify(mIWifiStaIfaceMock).setRoamingState(StaRoamingState.ENABLED);
    }

    // Utilities

    /**
     * Populate packet stats with non-negative random values.
     */
    private static void randomizePacketStats(Random r,  StaLinkLayerLinkStats stats) {
        stats.wmeBePktStats = new StaLinkLayerIfacePacketStats();
        stats.wmeBkPktStats = new StaLinkLayerIfacePacketStats();
        stats.wmeViPktStats = new StaLinkLayerIfacePacketStats();
        stats.wmeVoPktStats = new StaLinkLayerIfacePacketStats();
        randomizePacketStats(r, stats.wmeBePktStats);
        randomizePacketStats(r, stats.wmeBkPktStats);
        randomizePacketStats(r, stats.wmeViPktStats);
        randomizePacketStats(r, stats.wmeVoPktStats);
    }

    private static void randomizePacketStats(Random r, StaLinkLayerIfacePacketStats pstats) {
        pstats.rxMpdu = r.nextLong() & 0xFFFFFFFFFFL; // more than 32 bits
        pstats.txMpdu = r.nextLong() & 0xFFFFFFFFFFL;
        pstats.lostMpdu = r.nextLong() & 0xFFFFFFFFFFL;
        pstats.retries = r.nextLong() & 0xFFFFFFFFFFL;
    }

    /**
     * Populate radio stats with non-negative random values.
     */
    private static void randomizeRadioStats(Random r, StaLinkLayerRadioStats rstat) {
        rstat.onTimeInMs = r.nextInt() & 0xFFFFFF;
        rstat.txTimeInMs = r.nextInt() & 0xFFFFFF;
        rstat.txTimeInMsPerLevel = new int[4];
        for (int j = 0; j < 4; j++) {
            Integer v = r.nextInt() & 0xFFFFFF;
            rstat.txTimeInMsPerLevel[j] = v;
        }
        rstat.rxTimeInMs = r.nextInt() & 0xFFFFFF;
        rstat.onTimeInMsForScan = r.nextInt() & 0xFFFFFF;
        rstat.onTimeInMsForNanScan = r.nextInt() & 0xFFFFFF;
        rstat.onTimeInMsForBgScan = r.nextInt() & 0xFFFFFF;
        rstat.onTimeInMsForRoamScan = r.nextInt() & 0xFFFFFF;
        rstat.onTimeInMsForPnoScan = r.nextInt() & 0xFFFFFF;
        rstat.onTimeInMsForHs20Scan = r.nextInt() & 0xFFFFFF;
        rstat.channelStats = new WifiChannelStats[TEST_FREQUENCIES.length];
        for (int k = 0; k < TEST_FREQUENCIES.length; k++) {
            WifiChannelStats channelStats = new WifiChannelStats();
            channelStats.channel = new WifiChannelInfo();
            channelStats.channel.centerFreq = TEST_FREQUENCIES[k];
            channelStats.onTimeInMs = r.nextInt() & 0xFFFFFF;
            channelStats.ccaBusyTimeInMs = r.nextInt() & 0xFFFFFF;
            rstat.channelStats[k] = channelStats;
        }
        rstat.radioId = r.nextInt() & 0xFFFFFF;
    }

    /**
     * Populate contention time stats with non-negative random values.
     */
    private static void randomizeContentionTimeStats(Random r, StaLinkLayerLinkStats stats) {
        stats.wmeBeContentionTimeStats = new StaLinkLayerIfaceContentionTimeStats();
        stats.wmeBkContentionTimeStats = new StaLinkLayerIfaceContentionTimeStats();
        stats.wmeViContentionTimeStats = new StaLinkLayerIfaceContentionTimeStats();
        stats.wmeVoContentionTimeStats = new StaLinkLayerIfaceContentionTimeStats();
        randomizeContentionTimeStats(r, stats.wmeBeContentionTimeStats);
        randomizeContentionTimeStats(r, stats.wmeBkContentionTimeStats);
        randomizeContentionTimeStats(r, stats.wmeViContentionTimeStats);
        randomizeContentionTimeStats(r, stats.wmeVoContentionTimeStats);
    }

    private static void randomizeContentionTimeStats(Random r,
            StaLinkLayerIfaceContentionTimeStats cstats) {
        cstats.contentionTimeMinInUsec = r.nextInt() & 0x7FFFFFFF;
        cstats.contentionTimeMaxInUsec = r.nextInt() & 0x7FFFFFFF;
        cstats.contentionTimeAvgInUsec = r.nextInt() & 0x7FFFFFFF;
        cstats.contentionNumSamples = r.nextInt() & 0x7FFFFFFF;
    }

    /**
     * Populate peer info stats with non-negative random values.
     */
    private static StaPeerInfo[] randomizePeerInfoStats(Random r) {
        StaPeerInfo pstat = new StaPeerInfo();
        pstat.staCount = 2;
        pstat.chanUtil = 90;
        StaRateStat rateStat = new StaRateStat();
        rateStat.rateInfo = new WifiRateInfo();
        rateStat.rateInfo.preamble = r.nextInt() & 0x7FFFFFFF;
        rateStat.rateInfo.nss = r.nextInt() & 0x7FFFFFFF;
        rateStat.rateInfo.bw = r.nextInt() & 0x7FFFFFFF;
        rateStat.rateInfo.rateMcsIdx = 9;
        rateStat.rateInfo.bitRateInKbps = 101;
        rateStat.txMpdu = r.nextInt() & 0x7FFFFFFF;
        rateStat.rxMpdu = r.nextInt() & 0x7FFFFFFF;
        rateStat.mpduLost = r.nextInt() & 0x7FFFFFFF;
        rateStat.retries = r.nextInt() & 0x7FFFFFFF;
        pstat.rateStats = new StaRateStat[]{rateStat};
        return new StaPeerInfo[]{pstat};
    }

    private void verifyIfaceStats(StaLinkLayerIfaceStats iface,
            WifiLinkLayerStats wifiLinkLayerStats) {
        assertEquals(iface.links[0].beaconRx, wifiLinkLayerStats.links[0].beacon_rx);
        assertEquals(iface.links[0].avgRssiMgmt, wifiLinkLayerStats.links[0].rssi_mgmt);

        assertEquals(iface.links[0].wmeBePktStats.rxMpdu, wifiLinkLayerStats.links[0].rxmpdu_be);
        assertEquals(iface.links[0].wmeBePktStats.txMpdu, wifiLinkLayerStats.links[0].txmpdu_be);
        assertEquals(iface.links[0].wmeBePktStats.lostMpdu,
                wifiLinkLayerStats.links[0].lostmpdu_be);
        assertEquals(iface.links[0].wmeBePktStats.retries, wifiLinkLayerStats.links[0].retries_be);

        assertEquals(iface.links[0].wmeBkPktStats.rxMpdu, wifiLinkLayerStats.links[0].rxmpdu_bk);
        assertEquals(iface.links[0].wmeBkPktStats.txMpdu, wifiLinkLayerStats.links[0].txmpdu_bk);
        assertEquals(iface.links[0].wmeBkPktStats.lostMpdu,
                wifiLinkLayerStats.links[0].lostmpdu_bk);
        assertEquals(iface.links[0].wmeBkPktStats.retries, wifiLinkLayerStats.links[0].retries_bk);

        assertEquals(iface.links[0].wmeViPktStats.rxMpdu, wifiLinkLayerStats.links[0].rxmpdu_vi);
        assertEquals(iface.links[0].wmeViPktStats.txMpdu, wifiLinkLayerStats.links[0].txmpdu_vi);
        assertEquals(iface.links[0].wmeViPktStats.lostMpdu,
                wifiLinkLayerStats.links[0].lostmpdu_vi);
        assertEquals(iface.links[0].wmeViPktStats.retries, wifiLinkLayerStats.links[0].retries_vi);

        assertEquals(iface.links[0].wmeVoPktStats.rxMpdu, wifiLinkLayerStats.links[0].rxmpdu_vo);
        assertEquals(iface.links[0].wmeVoPktStats.txMpdu, wifiLinkLayerStats.links[0].txmpdu_vo);
        assertEquals(iface.links[0].wmeVoPktStats.lostMpdu,
                wifiLinkLayerStats.links[0].lostmpdu_vo);
        assertEquals(iface.links[0].wmeVoPktStats.retries, wifiLinkLayerStats.links[0].retries_vo);

        assertEquals(iface.links[0].wmeBeContentionTimeStats.contentionTimeMinInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMinBeInUsec);
        assertEquals(iface.links[0].wmeBeContentionTimeStats.contentionTimeMaxInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMaxBeInUsec);
        assertEquals(iface.links[0].wmeBeContentionTimeStats.contentionTimeAvgInUsec,
                wifiLinkLayerStats.links[0].contentionTimeAvgBeInUsec);
        assertEquals(iface.links[0].wmeBeContentionTimeStats.contentionNumSamples,
                wifiLinkLayerStats.links[0].contentionNumSamplesBe);

        assertEquals(iface.links[0].wmeBkContentionTimeStats.contentionTimeMinInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMinBkInUsec);
        assertEquals(iface.links[0].wmeBkContentionTimeStats.contentionTimeMaxInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMaxBkInUsec);
        assertEquals(iface.links[0].wmeBkContentionTimeStats.contentionTimeAvgInUsec,
                wifiLinkLayerStats.links[0].contentionTimeAvgBkInUsec);
        assertEquals(iface.links[0].wmeBkContentionTimeStats.contentionNumSamples,
                wifiLinkLayerStats.links[0].contentionNumSamplesBk);

        assertEquals(iface.links[0].wmeViContentionTimeStats.contentionTimeMinInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMinViInUsec);
        assertEquals(iface.links[0].wmeViContentionTimeStats.contentionTimeMaxInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMaxViInUsec);
        assertEquals(iface.links[0].wmeViContentionTimeStats.contentionTimeAvgInUsec,
                wifiLinkLayerStats.links[0].contentionTimeAvgViInUsec);
        assertEquals(iface.links[0].wmeViContentionTimeStats.contentionNumSamples,
                wifiLinkLayerStats.links[0].contentionNumSamplesVi);

        assertEquals(iface.links[0].wmeVoContentionTimeStats.contentionTimeMinInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMinVoInUsec);
        assertEquals(iface.links[0].wmeVoContentionTimeStats.contentionTimeMaxInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMaxVoInUsec);
        assertEquals(iface.links[0].wmeVoContentionTimeStats.contentionTimeAvgInUsec,
                wifiLinkLayerStats.links[0].contentionTimeAvgVoInUsec);
        assertEquals(iface.links[0].wmeVoContentionTimeStats.contentionNumSamples,
                wifiLinkLayerStats.links[0].contentionNumSamplesVo);

        for (int i = 0; i < iface.links[0].peers.length; i++) {
            assertEquals(iface.links[0].peers[i].staCount,
                    wifiLinkLayerStats.links[0].peerInfo[i].staCount);
            assertEquals(iface.links[0].peers[i].chanUtil,
                    wifiLinkLayerStats.links[0].peerInfo[i].chanUtil);
            for (int j = 0; j < iface.links[0].peers[i].rateStats.length; j++) {
                assertEquals(iface.links[0].peers[i].rateStats[j].rateInfo.preamble,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].preamble);
                assertEquals(iface.links[0].peers[i].rateStats[j].rateInfo.nss,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].nss);
                assertEquals(iface.links[0].peers[i].rateStats[j].rateInfo.bw,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].bw);
                assertEquals(iface.links[0].peers[i].rateStats[j].rateInfo.rateMcsIdx,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].rateMcsIdx);
                assertEquals(iface.links[0].peers[i].rateStats[j].rateInfo.bitRateInKbps,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].bitRateInKbps);
                assertEquals(iface.links[0].peers[i].rateStats[j].txMpdu,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].txMpdu);
                assertEquals(iface.links[0].peers[i].rateStats[j].rxMpdu,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].rxMpdu);
                assertEquals(iface.links[0].peers[i].rateStats[j].mpduLost,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].mpduLost);
                assertEquals(iface.links[0].peers[i].rateStats[j].retries,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].retries);
            }
        }
    }

    private void verifyPerRadioStats(StaLinkLayerRadioStats[] radios,
            WifiLinkLayerStats wifiLinkLayerStats) {
        assertEquals(radios.length, wifiLinkLayerStats.radioStats.length);
        for (int i = 0; i < radios.length; i++) {
            StaLinkLayerRadioStats radio = radios[i];
            WifiLinkLayerStats.RadioStat radioStat = wifiLinkLayerStats.radioStats[i];
            assertEquals(radio.radioId, radioStat.radio_id);
            assertEquals(radio.onTimeInMs, radioStat.on_time);
            assertEquals(radio.txTimeInMs, radioStat.tx_time);
            assertEquals(radio.rxTimeInMs, radioStat.rx_time);
            assertEquals(radio.onTimeInMsForScan, radioStat.on_time_scan);
            assertEquals(radio.onTimeInMsForNanScan, radioStat.on_time_nan_scan);
            assertEquals(radio.onTimeInMsForBgScan, radioStat.on_time_background_scan);
            assertEquals(radio.onTimeInMsForRoamScan, radioStat.on_time_roam_scan);
            assertEquals(radio.onTimeInMsForPnoScan, radioStat.on_time_pno_scan);
            assertEquals(radio.onTimeInMsForHs20Scan, radioStat.on_time_hs20_scan);

            assertEquals(radio.channelStats.length, radioStat.channelStatsMap.size());
            for (int j = 0; j < radio.channelStats.length; j++) {
                android.hardware.wifi.WifiChannelStats channelStats = radio.channelStats[j];
                WifiLinkLayerStats.ChannelStats retrievedChannelStats =
                        radioStat.channelStatsMap.get(channelStats.channel.centerFreq);
                assertNotNull(retrievedChannelStats);
                assertEquals(channelStats.channel.centerFreq, retrievedChannelStats.frequency);
                assertEquals(channelStats.onTimeInMs, retrievedChannelStats.radioOnTimeMs);
                assertEquals(channelStats.ccaBusyTimeInMs, retrievedChannelStats.ccaBusyTimeMs);
            }
        }
    }

    private void verifyRadioStats(StaLinkLayerRadioStats radio,
            WifiLinkLayerStats wifiLinkLayerStats) {
        assertEquals(radio.onTimeInMs, wifiLinkLayerStats.on_time);
        assertEquals(radio.txTimeInMs, wifiLinkLayerStats.tx_time);
        assertEquals(radio.rxTimeInMs, wifiLinkLayerStats.rx_time);
        assertEquals(radio.onTimeInMsForScan, wifiLinkLayerStats.on_time_scan);
        assertEquals(radio.txTimeInMsPerLevel.length, wifiLinkLayerStats.tx_time_per_level.length);
        for (int i = 0; i < radio.txTimeInMsPerLevel.length; i++) {
            assertEquals(radio.txTimeInMsPerLevel[i], wifiLinkLayerStats.tx_time_per_level[i]);
        }
        assertEquals(radio.onTimeInMsForNanScan, wifiLinkLayerStats.on_time_nan_scan);
        assertEquals(radio.onTimeInMsForBgScan, wifiLinkLayerStats.on_time_background_scan);
        assertEquals(radio.onTimeInMsForRoamScan, wifiLinkLayerStats.on_time_roam_scan);
        assertEquals(radio.onTimeInMsForPnoScan, wifiLinkLayerStats.on_time_pno_scan);
        assertEquals(radio.onTimeInMsForHs20Scan, wifiLinkLayerStats.on_time_hs20_scan);
        assertEquals(radio.channelStats.length, wifiLinkLayerStats.channelStatsMap.size());
        for (int j = 0; j < radio.channelStats.length; j++) {
            WifiChannelStats channelStats = radio.channelStats[j];
            WifiLinkLayerStats.ChannelStats retrievedChannelStats =
                    wifiLinkLayerStats.channelStatsMap.get(channelStats.channel.centerFreq);
            assertNotNull(retrievedChannelStats);
            assertEquals(channelStats.channel.centerFreq, retrievedChannelStats.frequency);
            assertEquals(channelStats.onTimeInMs, retrievedChannelStats.radioOnTimeMs);
            assertEquals(channelStats.ccaBusyTimeInMs, retrievedChannelStats.ccaBusyTimeMs);
        }
    }

    private void verifyTwoRadioStatsAggregation(StaLinkLayerRadioStats[] radios,
            WifiLinkLayerStats wifiLinkLayerStats) {
        assertEquals(2, radios.length);
        StaLinkLayerRadioStats radio0 = radios[0];
        StaLinkLayerRadioStats radio1 = radios[1];

        assertEquals(radio0.onTimeInMs + radio1.onTimeInMs, wifiLinkLayerStats.on_time);
        assertEquals(radio0.txTimeInMs + radio1.txTimeInMs, wifiLinkLayerStats.tx_time);
        assertEquals(radio0.rxTimeInMs + radio1.rxTimeInMs, wifiLinkLayerStats.rx_time);
        assertEquals(radio0.onTimeInMsForScan + radio1.onTimeInMsForScan,
                wifiLinkLayerStats.on_time_scan);
        assertEquals(radio0.txTimeInMsPerLevel.length, radio1.txTimeInMsPerLevel.length);
        assertEquals(radio0.txTimeInMsPerLevel.length, wifiLinkLayerStats.tx_time_per_level.length);
        for (int i = 0; i < radio0.txTimeInMsPerLevel.length; i++) {
            assertEquals(radio0.txTimeInMsPerLevel[i] + radio1.txTimeInMsPerLevel[i],
                    wifiLinkLayerStats.tx_time_per_level[i]);
        }
        assertEquals(radio0.onTimeInMsForNanScan + radio1.onTimeInMsForNanScan,
                wifiLinkLayerStats.on_time_nan_scan);
        assertEquals(radio0.onTimeInMsForBgScan + radio1.onTimeInMsForBgScan,
                wifiLinkLayerStats.on_time_background_scan);
        assertEquals(radio0.onTimeInMsForRoamScan + radio1.onTimeInMsForRoamScan,
                wifiLinkLayerStats.on_time_roam_scan);
        assertEquals(radio0.onTimeInMsForPnoScan + radio1.onTimeInMsForPnoScan,
                wifiLinkLayerStats.on_time_pno_scan);
        assertEquals(radio0.onTimeInMsForHs20Scan + radio1.onTimeInMsForHs20Scan,
                wifiLinkLayerStats.on_time_hs20_scan);
        assertEquals(radio0.channelStats.length, radio1.channelStats.length);
        assertEquals(radio0.channelStats.length, wifiLinkLayerStats.channelStatsMap.size());
        for (int j = 0; j < radio0.channelStats.length; j++) {
            WifiChannelStats radio0ChannelStats = radio0.channelStats[j];
            WifiChannelStats radio1ChannelStats = radio1.channelStats[j];
            WifiLinkLayerStats.ChannelStats retrievedChannelStats =
                    wifiLinkLayerStats.channelStatsMap.get(radio0ChannelStats.channel.centerFreq);
            assertNotNull(retrievedChannelStats);
            assertEquals(radio0ChannelStats.channel.centerFreq, retrievedChannelStats.frequency);
            assertEquals(radio1ChannelStats.channel.centerFreq, retrievedChannelStats.frequency);
            assertEquals(radio0ChannelStats.onTimeInMs + radio1ChannelStats.onTimeInMs,
                    retrievedChannelStats.radioOnTimeMs);
            assertEquals(radio0ChannelStats.ccaBusyTimeInMs
                    + radio1ChannelStats.ccaBusyTimeInMs, retrievedChannelStats.ccaBusyTimeMs);
        }
    }
}
