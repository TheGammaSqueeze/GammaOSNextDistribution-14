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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil;
import android.content.Context;
import android.content.res.Resources;
import android.hardware.wifi.V1_0.IWifiStaIface;
import android.hardware.wifi.V1_0.StaLinkLayerIfacePacketStats;
import android.hardware.wifi.V1_0.StaLinkLayerIfaceStats;
import android.hardware.wifi.V1_0.StaLinkLayerRadioStats;
import android.hardware.wifi.V1_0.StaLinkLayerStats;
import android.hardware.wifi.V1_0.StaRoamingCapabilities;
import android.hardware.wifi.V1_0.WifiDebugPacketFateFrameType;
import android.hardware.wifi.V1_0.WifiDebugRxPacketFate;
import android.hardware.wifi.V1_0.WifiDebugRxPacketFateReport;
import android.hardware.wifi.V1_0.WifiDebugTxPacketFate;
import android.hardware.wifi.V1_0.WifiDebugTxPacketFateReport;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hardware.wifi.V1_3.WifiChannelStats;
import android.hardware.wifi.V1_5.StaLinkLayerIfaceContentionTimeStats;
import android.hardware.wifi.V1_5.StaPeerInfo;
import android.hardware.wifi.V1_5.StaRateStat;
import android.net.MacAddress;
import android.net.wifi.WifiManager;
import android.os.RemoteException;

import com.android.server.wifi.SsidTranslator;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiLinkLayerStats;
import com.android.server.wifi.WifiLoggerHal;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.util.NativeUtil;
import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class WifiStaIfaceHidlImplTest extends WifiBaseTest {
    private static final int[] TEST_FREQUENCIES = {2412, 2417, 2422, 2427, 2432, 2437};
    private static final MacAddress TEST_MAC_ADDRESS = MacAddress.fromString("ee:33:a2:94:10:92");

    private WifiStaIfaceHidlImpl mDut;
    private WifiStatus mWifiStatusSuccess;
    private WifiStatus mWifiStatusFailure;
    private WifiStatus mWifiStatusBusy;

    @Mock private IWifiStaIface mIWifiStaIfaceMock;
    @Mock private android.hardware.wifi.V1_2.IWifiStaIface mIWifiStaIfaceMockV12;
    @Mock private android.hardware.wifi.V1_3.IWifiStaIface mIWifiStaIfaceMockV13;
    @Mock private android.hardware.wifi.V1_5.IWifiStaIface mIWifiStaIfaceMockV15;
    @Mock private Context mContextMock;
    @Mock private Resources mResourcesMock;
    @Mock private SsidTranslator mSsidTranslatorMock;

    private class WifiStaIfaceHidlImplSpy extends WifiStaIfaceHidlImpl {
        private int mVersion;

        WifiStaIfaceHidlImplSpy(int hidlVersion) {
            super(mIWifiStaIfaceMock, mContextMock, mSsidTranslatorMock);
            mVersion = hidlVersion;
        }

        @Override
        protected android.hardware.wifi.V1_2.IWifiStaIface getWifiStaIfaceV1_2Mockable() {
            return mVersion >= 2 ? mIWifiStaIfaceMockV12 : null;
        }

        @Override
        protected android.hardware.wifi.V1_3.IWifiStaIface getWifiStaIfaceV1_3Mockable() {
            return mVersion >= 3 ? mIWifiStaIfaceMockV13 : null;
        }

        @Override
        protected android.hardware.wifi.V1_5.IWifiStaIface getWifiStaIfaceV1_5Mockable() {
            return mVersion >= 5 ? mIWifiStaIfaceMockV15 : null;
        }

        @Override
        protected android.hardware.wifi.V1_6.IWifiStaIface getWifiStaIfaceV1_6Mockable() {
            return null;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new WifiStaIfaceHidlImplSpy(0);

        mWifiStatusSuccess = new WifiStatus();
        mWifiStatusSuccess.code = WifiStatusCode.SUCCESS;
        mWifiStatusFailure = new WifiStatus();
        mWifiStatusFailure.code = WifiStatusCode.ERROR_UNKNOWN;
        mWifiStatusFailure.description = "I don't even know what a Mock Turtle is.";
        mWifiStatusBusy = new WifiStatus();
        mWifiStatusBusy.code = WifiStatusCode.ERROR_BUSY;
        mWifiStatusBusy.description = "Don't bother me, kid";

        when(mContextMock.getResources()).thenReturn(mResourcesMock);
    }

    /**
     * Test translation to WifiManager.WIFI_FEATURE_*
     */
    @Test
    public void testStaIfaceFeatureMaskTranslation() {
        int caps = (
                IWifiStaIface.StaIfaceCapabilityMask.BACKGROUND_SCAN
                        | IWifiStaIface.StaIfaceCapabilityMask.LINK_LAYER_STATS
        );
        long expected = (
                WifiManager.WIFI_FEATURE_SCANNER
                        | WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        assertEquals(expected, mDut.halToFrameworkStaIfaceCapability(caps));
    }

    /**
     * Test that getFactoryMacAddress gets called when the HAL version is V1_3.
     */
    @Test
    public void testGetStaFactoryMacWithHalV1_3() throws Exception {
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(
                    android.hardware.wifi.V1_3.IWifiStaIface.getFactoryMacAddressCallback cb)
                    throws RemoteException {
                cb.onValues(mWifiStatusSuccess, MacAddress.BROADCAST_ADDRESS.toByteArray());
            }
        }).when(mIWifiStaIfaceMockV13).getFactoryMacAddress(any(
                android.hardware.wifi.V1_3.IWifiStaIface.getFactoryMacAddressCallback.class));
        mDut = new WifiStaIfaceHidlImplSpy(3);
        assertEquals(MacAddress.BROADCAST_ADDRESS, mDut.getFactoryMacAddress());
    }

    /**
     * Test that getLinkLayerStats_1_3 gets called when the HAL version is V1_3.
     */
    @Test
    public void testLinkLayerStatsCorrectVersionWithHalV1_3() throws Exception {
        mDut = new WifiStaIfaceHidlImplSpy(3);
        mDut.getLinkLayerStats();
        verify(mIWifiStaIfaceMockV13).getLinkLayerStats_1_3(any());
    }

    /**
     * Test getLinkLayerStats_1_5 gets called when the hal version is V1_5.
     */
    @Test
    public void testLinkLayerStatsCorrectVersionWithHalV1_5() throws Exception {
        mDut = new WifiStaIfaceHidlImplSpy(5);
        mDut.getLinkLayerStats();
        verify(mIWifiStaIfaceMockV15).getLinkLayerStats_1_5(any());
    }

    /**
     * Populate packet stats with non-negative random values.
     */
    private static void randomizePacketStats(Random r, StaLinkLayerIfacePacketStats pstats) {
        pstats.rxMpdu = r.nextLong() & 0xFFFFFFFFFFL; // more than 32 bits
        pstats.txMpdu = r.nextLong() & 0xFFFFFFFFFFL;
        pstats.lostMpdu = r.nextLong() & 0xFFFFFFFFFFL;
        pstats.retries = r.nextLong() & 0xFFFFFFFFFFL;
    }

    /**
     * Populate contention time stats with non-negative random values.
     */
    private static void randomizeContentionTimeStats(Random r,
            StaLinkLayerIfaceContentionTimeStats cstats) {
        cstats.contentionTimeMinInUsec = r.nextInt() & 0x7FFFFFFF;
        cstats.contentionTimeMaxInUsec = r.nextInt() & 0x7FFFFFFF;
        cstats.contentionTimeAvgInUsec = r.nextInt() & 0x7FFFFFFF;
        cstats.contentionNumSamples = r.nextInt() & 0x7FFFFFFF;
    }

    /**
     * Populate radio stats with non-negative random values.
     */
    private static void randomizeRadioStats(Random r, ArrayList<StaLinkLayerRadioStats> rstats) {
        StaLinkLayerRadioStats rstat = new StaLinkLayerRadioStats();
        rstat.onTimeInMs = r.nextInt() & 0xFFFFFF;
        rstat.txTimeInMs = r.nextInt() & 0xFFFFFF;
        for (int i = 0; i < 4; i++) {
            Integer v = r.nextInt() & 0xFFFFFF;
            rstat.txTimeInMsPerLevel.add(v);
        }
        rstat.rxTimeInMs = r.nextInt() & 0xFFFFFF;
        rstat.onTimeInMsForScan = r.nextInt() & 0xFFFFFF;
        rstats.add(rstat);
    }

    /**
     * Populate radio stats V1_3 with non-negative random values.
     */
    private static void randomizeRadioStats_1_3(Random r,
            android.hardware.wifi.V1_3.StaLinkLayerRadioStats rstat) {
        rstat.V1_0.onTimeInMs = r.nextInt() & 0xFFFFFF;
        rstat.V1_0.txTimeInMs = r.nextInt() & 0xFFFFFF;
        for (int j = 0; j < 4; j++) {
            Integer v = r.nextInt() & 0xFFFFFF;
            rstat.V1_0.txTimeInMsPerLevel.add(v);
        }
        rstat.V1_0.rxTimeInMs = r.nextInt() & 0xFFFFFF;
        rstat.V1_0.onTimeInMsForScan = r.nextInt() & 0xFFFFFF;
        rstat.onTimeInMsForNanScan = r.nextInt() & 0xFFFFFF;
        rstat.onTimeInMsForBgScan = r.nextInt() & 0xFFFFFF;
        rstat.onTimeInMsForRoamScan = r.nextInt() & 0xFFFFFF;
        rstat.onTimeInMsForPnoScan = r.nextInt() & 0xFFFFFF;
        rstat.onTimeInMsForHs20Scan = r.nextInt() & 0xFFFFFF;
        for (int k = 0; k < TEST_FREQUENCIES.length; k++) {
            WifiChannelStats channelStats = new WifiChannelStats();
            channelStats.channel.centerFreq = TEST_FREQUENCIES[k];
            channelStats.onTimeInMs = r.nextInt() & 0xFFFFFF;
            channelStats.ccaBusyTimeInMs = r.nextInt() & 0xFFFFFF;
            rstat.channelStats.add(channelStats);
        }
    }

    /**
     * Populate radio stats V1_5 with non-negative random values.
     */
    private static void randomizeRadioStats_1_5(Random r,
            android.hardware.wifi.V1_5.StaLinkLayerRadioStats rstat) {
        rstat.radioId = r.nextInt() & 0xFFFFFF;
        randomizeRadioStats_1_3(r, rstat.V1_3);
    }

    /**
     * Populate peer info stats with non-negative random values.
     */
    private static void randomizePeerInfoStats(Random r, ArrayList<StaPeerInfo> pstats) {
        StaPeerInfo pstat = new StaPeerInfo();
        pstat.staCount = 2;
        pstat.chanUtil = 90;
        pstat.rateStats = new ArrayList<StaRateStat>();
        StaRateStat rateStat = new StaRateStat();
        rateStat.rateInfo.preamble = r.nextInt() & 0x7FFFFFFF;
        rateStat.rateInfo.nss = r.nextInt() & 0x7FFFFFFF;
        rateStat.rateInfo.bw = r.nextInt() & 0x7FFFFFFF;
        rateStat.rateInfo.rateMcsIdx = 9;
        rateStat.rateInfo.bitRateInKbps = 101;
        rateStat.txMpdu = r.nextInt() & 0x7FFFFFFF;
        rateStat.rxMpdu = r.nextInt() & 0x7FFFFFFF;
        rateStat.mpduLost = r.nextInt() & 0x7FFFFFFF;
        rateStat.retries = r.nextInt() & 0x7FFFFFFF;
        pstat.rateStats.add(rateStat);
        pstats.add(pstat);
    }

    private void verifyIfaceStats(StaLinkLayerIfaceStats iface,
            WifiLinkLayerStats wifiLinkLayerStats) {
        assertEquals(iface.beaconRx, wifiLinkLayerStats.links[0].beacon_rx);
        assertEquals(iface.avgRssiMgmt, wifiLinkLayerStats.links[0].rssi_mgmt);

        assertEquals(iface.wmeBePktStats.rxMpdu, wifiLinkLayerStats.links[0].rxmpdu_be);
        assertEquals(iface.wmeBePktStats.txMpdu, wifiLinkLayerStats.links[0].txmpdu_be);
        assertEquals(iface.wmeBePktStats.lostMpdu, wifiLinkLayerStats.links[0].lostmpdu_be);
        assertEquals(iface.wmeBePktStats.retries, wifiLinkLayerStats.links[0].retries_be);

        assertEquals(iface.wmeBkPktStats.rxMpdu, wifiLinkLayerStats.links[0].rxmpdu_bk);
        assertEquals(iface.wmeBkPktStats.txMpdu, wifiLinkLayerStats.links[0].txmpdu_bk);
        assertEquals(iface.wmeBkPktStats.lostMpdu, wifiLinkLayerStats.links[0].lostmpdu_bk);
        assertEquals(iface.wmeBkPktStats.retries, wifiLinkLayerStats.links[0].retries_bk);

        assertEquals(iface.wmeViPktStats.rxMpdu, wifiLinkLayerStats.links[0].rxmpdu_vi);
        assertEquals(iface.wmeViPktStats.txMpdu, wifiLinkLayerStats.links[0].txmpdu_vi);
        assertEquals(iface.wmeViPktStats.lostMpdu, wifiLinkLayerStats.links[0].lostmpdu_vi);
        assertEquals(iface.wmeViPktStats.retries, wifiLinkLayerStats.links[0].retries_vi);

        assertEquals(iface.wmeVoPktStats.rxMpdu, wifiLinkLayerStats.links[0].rxmpdu_vo);
        assertEquals(iface.wmeVoPktStats.txMpdu, wifiLinkLayerStats.links[0].txmpdu_vo);
        assertEquals(iface.wmeVoPktStats.lostMpdu, wifiLinkLayerStats.links[0].lostmpdu_vo);
        assertEquals(iface.wmeVoPktStats.retries, wifiLinkLayerStats.links[0].retries_vo);
    }

    private void verifyIfaceStats_1_5(android.hardware.wifi.V1_5.StaLinkLayerIfaceStats iface,
            WifiLinkLayerStats wifiLinkLayerStats) {
        assertEquals(iface.wmeBeContentionTimeStats.contentionTimeMinInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMinBeInUsec);
        assertEquals(iface.wmeBeContentionTimeStats.contentionTimeMaxInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMaxBeInUsec);
        assertEquals(iface.wmeBeContentionTimeStats.contentionTimeAvgInUsec,
                wifiLinkLayerStats.links[0].contentionTimeAvgBeInUsec);
        assertEquals(iface.wmeBeContentionTimeStats.contentionNumSamples,
                wifiLinkLayerStats.links[0].contentionNumSamplesBe);

        assertEquals(iface.wmeBkContentionTimeStats.contentionTimeMinInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMinBkInUsec);
        assertEquals(iface.wmeBkContentionTimeStats.contentionTimeMaxInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMaxBkInUsec);
        assertEquals(iface.wmeBkContentionTimeStats.contentionTimeAvgInUsec,
                wifiLinkLayerStats.links[0].contentionTimeAvgBkInUsec);
        assertEquals(iface.wmeBkContentionTimeStats.contentionNumSamples,
                wifiLinkLayerStats.links[0].contentionNumSamplesBk);

        assertEquals(iface.wmeViContentionTimeStats.contentionTimeMinInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMinViInUsec);
        assertEquals(iface.wmeViContentionTimeStats.contentionTimeMaxInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMaxViInUsec);
        assertEquals(iface.wmeViContentionTimeStats.contentionTimeAvgInUsec,
                wifiLinkLayerStats.links[0].contentionTimeAvgViInUsec);
        assertEquals(iface.wmeViContentionTimeStats.contentionNumSamples,
                wifiLinkLayerStats.links[0].contentionNumSamplesVi);

        assertEquals(iface.wmeVoContentionTimeStats.contentionTimeMinInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMinVoInUsec);
        assertEquals(iface.wmeVoContentionTimeStats.contentionTimeMaxInUsec,
                wifiLinkLayerStats.links[0].contentionTimeMaxVoInUsec);
        assertEquals(iface.wmeVoContentionTimeStats.contentionTimeAvgInUsec,
                wifiLinkLayerStats.links[0].contentionTimeAvgVoInUsec);
        assertEquals(iface.wmeVoContentionTimeStats.contentionNumSamples,
                wifiLinkLayerStats.links[0].contentionNumSamplesVo);

        for (int i = 0; i < iface.peers.size(); i++) {
            assertEquals(iface.peers.get(i).staCount, wifiLinkLayerStats.links[0]
                    .peerInfo[i].staCount);
            assertEquals(iface.peers.get(i).chanUtil, wifiLinkLayerStats.links[0]
                    .peerInfo[i].chanUtil);
            for (int j = 0; j < iface.peers.get(i).rateStats.size(); j++) {
                assertEquals(iface.peers.get(i).rateStats.get(j).rateInfo.preamble,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].preamble);
                assertEquals(iface.peers.get(i).rateStats.get(j).rateInfo.nss,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].nss);
                assertEquals(iface.peers.get(i).rateStats.get(j).rateInfo.bw,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].bw);
                assertEquals(iface.peers.get(i).rateStats.get(j).rateInfo.rateMcsIdx,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].rateMcsIdx);
                assertEquals(iface.peers.get(i).rateStats.get(j).rateInfo.bitRateInKbps,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].bitRateInKbps);
                assertEquals(iface.peers.get(i).rateStats.get(j).txMpdu,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].txMpdu);
                assertEquals(iface.peers.get(i).rateStats.get(j).rxMpdu,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].rxMpdu);
                assertEquals(iface.peers.get(i).rateStats.get(j).mpduLost,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].mpduLost);
                assertEquals(iface.peers.get(i).rateStats.get(j).retries,
                        wifiLinkLayerStats.links[0].peerInfo[i].rateStats[j].retries);
            }
        }
    }

    private void verifyRadioStats(List<StaLinkLayerRadioStats> radios,
            WifiLinkLayerStats wifiLinkLayerStats) {
        StaLinkLayerRadioStats radio = radios.get(0);
        assertEquals(radio.onTimeInMs, wifiLinkLayerStats.on_time);
        assertEquals(radio.txTimeInMs, wifiLinkLayerStats.tx_time);
        assertEquals(radio.rxTimeInMs, wifiLinkLayerStats.rx_time);
        assertEquals(radio.onTimeInMsForScan, wifiLinkLayerStats.on_time_scan);
        assertEquals(radio.txTimeInMsPerLevel.size(),
                wifiLinkLayerStats.tx_time_per_level.length);
        for (int i = 0; i < radio.txTimeInMsPerLevel.size(); i++) {
            assertEquals((int) radio.txTimeInMsPerLevel.get(i),
                    wifiLinkLayerStats.tx_time_per_level[i]);
        }
    }

    private void verifyRadioStats_1_3(
            android.hardware.wifi.V1_3.StaLinkLayerRadioStats radio,
            WifiLinkLayerStats wifiLinkLayerStats) {
        assertEquals(radio.V1_0.onTimeInMs, wifiLinkLayerStats.on_time);
        assertEquals(radio.V1_0.txTimeInMs, wifiLinkLayerStats.tx_time);
        assertEquals(radio.V1_0.rxTimeInMs, wifiLinkLayerStats.rx_time);
        assertEquals(radio.V1_0.onTimeInMsForScan, wifiLinkLayerStats.on_time_scan);
        assertEquals(radio.V1_0.txTimeInMsPerLevel.size(),
                wifiLinkLayerStats.tx_time_per_level.length);
        for (int i = 0; i < radio.V1_0.txTimeInMsPerLevel.size(); i++) {
            assertEquals((int) radio.V1_0.txTimeInMsPerLevel.get(i),
                    wifiLinkLayerStats.tx_time_per_level[i]);
        }
        assertEquals(radio.onTimeInMsForNanScan, wifiLinkLayerStats.on_time_nan_scan);
        assertEquals(radio.onTimeInMsForBgScan, wifiLinkLayerStats.on_time_background_scan);
        assertEquals(radio.onTimeInMsForRoamScan, wifiLinkLayerStats.on_time_roam_scan);
        assertEquals(radio.onTimeInMsForPnoScan, wifiLinkLayerStats.on_time_pno_scan);
        assertEquals(radio.onTimeInMsForHs20Scan, wifiLinkLayerStats.on_time_hs20_scan);
        assertEquals(radio.channelStats.size(),
                wifiLinkLayerStats.channelStatsMap.size());
        for (int j = 0; j < radio.channelStats.size(); j++) {
            WifiChannelStats channelStats = radio.channelStats.get(j);
            WifiLinkLayerStats.ChannelStats retrievedChannelStats =
                    wifiLinkLayerStats.channelStatsMap.get(channelStats.channel.centerFreq);
            assertNotNull(retrievedChannelStats);
            assertEquals(channelStats.channel.centerFreq, retrievedChannelStats.frequency);
            assertEquals(channelStats.onTimeInMs, retrievedChannelStats.radioOnTimeMs);
            assertEquals(channelStats.ccaBusyTimeInMs, retrievedChannelStats.ccaBusyTimeMs);
        }
    }

    private void verifyPerRadioStats(List<android.hardware.wifi.V1_5.StaLinkLayerRadioStats> radios,
            WifiLinkLayerStats wifiLinkLayerStats) {
        assertEquals(radios.size(),
                wifiLinkLayerStats.radioStats.length);
        for (int i = 0; i < radios.size(); i++) {
            android.hardware.wifi.V1_5.StaLinkLayerRadioStats radio = radios.get(i);
            WifiLinkLayerStats.RadioStat radioStat = wifiLinkLayerStats.radioStats[i];
            assertEquals(radio.radioId, radioStat.radio_id);
            assertEquals(radio.V1_3.V1_0.onTimeInMs, radioStat.on_time);
            assertEquals(radio.V1_3.V1_0.txTimeInMs, radioStat.tx_time);
            assertEquals(radio.V1_3.V1_0.rxTimeInMs, radioStat.rx_time);
            assertEquals(radio.V1_3.V1_0.onTimeInMsForScan, radioStat.on_time_scan);
            assertEquals(radio.V1_3.onTimeInMsForNanScan, radioStat.on_time_nan_scan);
            assertEquals(radio.V1_3.onTimeInMsForBgScan, radioStat.on_time_background_scan);
            assertEquals(radio.V1_3.onTimeInMsForRoamScan, radioStat.on_time_roam_scan);
            assertEquals(radio.V1_3.onTimeInMsForPnoScan, radioStat.on_time_pno_scan);
            assertEquals(radio.V1_3.onTimeInMsForHs20Scan, radioStat.on_time_hs20_scan);

            assertEquals(radio.V1_3.channelStats.size(),
                    radioStat.channelStatsMap.size());
            for (int j = 0; j < radio.V1_3.channelStats.size(); j++) {
                WifiChannelStats channelStats = radio.V1_3.channelStats.get(j);
                WifiLinkLayerStats.ChannelStats retrievedChannelStats =
                        radioStat.channelStatsMap.get(channelStats.channel.centerFreq);
                assertNotNull(retrievedChannelStats);
                assertEquals(channelStats.channel.centerFreq, retrievedChannelStats.frequency);
                assertEquals(channelStats.onTimeInMs, retrievedChannelStats.radioOnTimeMs);
                assertEquals(channelStats.ccaBusyTimeInMs, retrievedChannelStats.ccaBusyTimeMs);
            }
        }

    }

    private void verifyRadioStats_1_5(
            android.hardware.wifi.V1_5.StaLinkLayerRadioStats radio,
            WifiLinkLayerStats wifiLinkLayerStats) {
        verifyRadioStats_1_3(radio.V1_3, wifiLinkLayerStats);
    }

    private void verifyTwoRadioStatsAggregation(
            android.hardware.wifi.V1_3.StaLinkLayerRadioStats radio0,
            android.hardware.wifi.V1_3.StaLinkLayerRadioStats radio1,
            WifiLinkLayerStats wifiLinkLayerStats) {
        assertEquals(radio0.V1_0.onTimeInMs + radio1.V1_0.onTimeInMs,
                wifiLinkLayerStats.on_time);
        assertEquals(radio0.V1_0.txTimeInMs + radio1.V1_0.txTimeInMs,
                wifiLinkLayerStats.tx_time);
        assertEquals(radio0.V1_0.rxTimeInMs + radio1.V1_0.rxTimeInMs,
                wifiLinkLayerStats.rx_time);
        assertEquals(radio0.V1_0.onTimeInMsForScan + radio1.V1_0.onTimeInMsForScan,
                wifiLinkLayerStats.on_time_scan);
        assertEquals(radio0.V1_0.txTimeInMsPerLevel.size(),
                radio1.V1_0.txTimeInMsPerLevel.size());
        assertEquals(radio0.V1_0.txTimeInMsPerLevel.size(),
                wifiLinkLayerStats.tx_time_per_level.length);
        for (int i = 0; i < radio0.V1_0.txTimeInMsPerLevel.size(); i++) {
            assertEquals((int) radio0.V1_0.txTimeInMsPerLevel.get(i)
                            + (int) radio1.V1_0.txTimeInMsPerLevel.get(i),
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
        assertEquals(radio0.channelStats.size(), radio1.channelStats.size());
        assertEquals(radio0.channelStats.size(),
                wifiLinkLayerStats.channelStatsMap.size());
        for (int j = 0; j < radio0.channelStats.size(); j++) {
            WifiChannelStats radio0ChannelStats = radio0.channelStats.get(j);
            WifiChannelStats radio1ChannelStats = radio1.channelStats.get(j);
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

    private void verifyTwoRadioStatsAggregation_1_3(
            List<android.hardware.wifi.V1_3.StaLinkLayerRadioStats> radios,
            WifiLinkLayerStats wifiLinkLayerStats) {
        assertEquals(2, radios.size());
        android.hardware.wifi.V1_3.StaLinkLayerRadioStats radio0 = radios.get(0);
        android.hardware.wifi.V1_3.StaLinkLayerRadioStats radio1 = radios.get(1);
        verifyTwoRadioStatsAggregation(radio0, radio1, wifiLinkLayerStats);
    }

    private void verifyTwoRadioStatsAggregation_1_5(
            List<android.hardware.wifi.V1_5.StaLinkLayerRadioStats> radios,
            WifiLinkLayerStats wifiLinkLayerStats) {
        assertEquals(2, radios.size());
        android.hardware.wifi.V1_5.StaLinkLayerRadioStats radio0 = radios.get(0);
        android.hardware.wifi.V1_5.StaLinkLayerRadioStats radio1 = radios.get(1);
        verifyTwoRadioStatsAggregation(radio0.V1_3, radio1.V1_3, wifiLinkLayerStats);
    }

    /**
     * Test that the link layer stats fields are populated correctly.
     */
    @Test
    public void testLinkLayerStatsAssignment() throws Exception {
        Random r = new Random(1775968256);
        StaLinkLayerStats stats = new StaLinkLayerStats();
        randomizePacketStats(r, stats.iface.wmeBePktStats);
        randomizePacketStats(r, stats.iface.wmeBkPktStats);
        randomizePacketStats(r, stats.iface.wmeViPktStats);
        randomizePacketStats(r, stats.iface.wmeVoPktStats);
        randomizeRadioStats(r, stats.radios);
        stats.timeStampInMs = r.nextLong() & 0xFFFFFFFFFFL;

        WifiLinkLayerStats converted = mDut.frameworkFromHalLinkLayerStats(stats);

        verifyIfaceStats(stats.iface, converted);
        verifyRadioStats(stats.radios, converted);
        assertEquals(stats.timeStampInMs, converted.timeStampInMs);
        assertEquals(WifiLinkLayerStats.V1_0, converted.version);
    }

    /**
     * Test that the link layer stats V1_3 fields are populated correctly.
     */
    @Test
    public void testLinkLayerStatsAssignment_1_3() throws Exception {
        Random r = new Random(1775968256);
        android.hardware.wifi.V1_3.StaLinkLayerStats stats =
                new android.hardware.wifi.V1_3.StaLinkLayerStats();
        randomizePacketStats(r, stats.iface.wmeBePktStats);
        randomizePacketStats(r, stats.iface.wmeBkPktStats);
        randomizePacketStats(r, stats.iface.wmeViPktStats);
        randomizePacketStats(r, stats.iface.wmeVoPktStats);
        android.hardware.wifi.V1_3.StaLinkLayerRadioStats rstat =
                new android.hardware.wifi.V1_3.StaLinkLayerRadioStats();
        randomizeRadioStats_1_3(r, rstat);
        stats.radios.add(rstat);
        stats.timeStampInMs = r.nextLong() & 0xFFFFFFFFFFL;

        WifiLinkLayerStats converted = mDut.frameworkFromHalLinkLayerStats_1_3(stats);

        verifyIfaceStats(stats.iface, converted);
        verifyRadioStats_1_3(stats.radios.get(0), converted);
        assertEquals(stats.timeStampInMs, converted.timeStampInMs);
        assertEquals(WifiLinkLayerStats.V1_3, converted.version);
        assertEquals(1, converted.numRadios);
    }

    /**
     * Test that the link layer stats V1_5 fields are populated correctly.
     */
    @Test
    public void testLinkLayerStatsAssignment_1_5() throws Exception {
        Random r = new Random(1775968256);
        android.hardware.wifi.V1_5.StaLinkLayerStats stats =
                new android.hardware.wifi.V1_5.StaLinkLayerStats();
        randomizePacketStats(r, stats.iface.V1_0.wmeBePktStats);
        randomizePacketStats(r, stats.iface.V1_0.wmeBkPktStats);
        randomizePacketStats(r, stats.iface.V1_0.wmeViPktStats);
        randomizePacketStats(r, stats.iface.V1_0.wmeVoPktStats);
        android.hardware.wifi.V1_5.StaLinkLayerRadioStats rstat =
                new android.hardware.wifi.V1_5.StaLinkLayerRadioStats();
        randomizeRadioStats_1_5(r, rstat);
        stats.radios.add(rstat);
        stats.timeStampInMs = r.nextLong() & 0xFFFFFFFFFFL;
        randomizeContentionTimeStats(r, stats.iface.wmeBeContentionTimeStats);
        randomizeContentionTimeStats(r, stats.iface.wmeBkContentionTimeStats);
        randomizeContentionTimeStats(r, stats.iface.wmeViContentionTimeStats);
        randomizeContentionTimeStats(r, stats.iface.wmeVoContentionTimeStats);
        randomizePeerInfoStats(r, stats.iface.peers);

        WifiLinkLayerStats converted = mDut.frameworkFromHalLinkLayerStats_1_5(stats);

        verifyIfaceStats(stats.iface.V1_0, converted);
        verifyIfaceStats_1_5(stats.iface, converted);
        verifyPerRadioStats(stats.radios, converted);
        verifyRadioStats_1_5(stats.radios.get(0), converted);
        assertEquals(stats.timeStampInMs, converted.timeStampInMs);
        assertEquals(WifiLinkLayerStats.V1_5, converted.version);
        assertEquals(1, converted.numRadios);
    }

    /**
     * Test that the link layer stats V1_3 fields are aggregated correctly for two radios.
     */
    @Test
    public void testTwoRadioStatsAggregation_1_3() throws Exception {
        when(mResourcesMock.getBoolean(R.bool.config_wifiLinkLayerAllRadiosStatsAggregationEnabled))
                .thenReturn(true);
        Random r = new Random(245786856);
        android.hardware.wifi.V1_3.StaLinkLayerStats stats =
                new android.hardware.wifi.V1_3.StaLinkLayerStats();
        // Fill stats in two radios
        for (int i = 0; i < 2; i++) {
            android.hardware.wifi.V1_3.StaLinkLayerRadioStats rstat =
                    new android.hardware.wifi.V1_3.StaLinkLayerRadioStats();
            randomizeRadioStats_1_3(r, rstat);
            stats.radios.add(rstat);
        }

        WifiLinkLayerStats converted = mDut.frameworkFromHalLinkLayerStats_1_3(stats);
        verifyTwoRadioStatsAggregation_1_3(stats.radios, converted);
        assertEquals(2, converted.numRadios);
    }

    /**
     * Test that the link layer stats V1_3 fields are not aggregated on setting
     * config_wifiLinkLayerAllRadiosStatsAggregationEnabled to false (default value).
     */
    @Test
    public void testRadioStatsAggregationDisabled_1_3() throws Exception {
        Random r = new Random(245786856);
        android.hardware.wifi.V1_3.StaLinkLayerStats stats =
                new android.hardware.wifi.V1_3.StaLinkLayerStats();
        // Fill stats in two radios
        for (int i = 0; i < 2; i++) {
            android.hardware.wifi.V1_3.StaLinkLayerRadioStats rstat =
                    new android.hardware.wifi.V1_3.StaLinkLayerRadioStats();
            randomizeRadioStats_1_3(r, rstat);
            stats.radios.add(rstat);
        }

        WifiLinkLayerStats converted = mDut.frameworkFromHalLinkLayerStats_1_3(stats);
        verifyRadioStats_1_3(stats.radios.get(0), converted);
        assertEquals(1, converted.numRadios);
    }

    /**
     * Test that the link layer stats V1_5 fields are aggregated correctly for two radios.
     */
    @Test
    public void testTwoRadioStatsAggregation_1_5() throws Exception {
        when(mResourcesMock.getBoolean(R.bool.config_wifiLinkLayerAllRadiosStatsAggregationEnabled))
                .thenReturn(true);
        Random r = new Random(245786856);
        android.hardware.wifi.V1_5.StaLinkLayerStats stats =
                new android.hardware.wifi.V1_5.StaLinkLayerStats();
        // Fill stats in two radios
        for (int i = 0; i < 2; i++) {
            android.hardware.wifi.V1_5.StaLinkLayerRadioStats rstat =
                    new android.hardware.wifi.V1_5.StaLinkLayerRadioStats();
            randomizeRadioStats_1_5(r, rstat);
            stats.radios.add(rstat);
        }

        WifiLinkLayerStats converted = mDut.frameworkFromHalLinkLayerStats_1_5(stats);
        verifyPerRadioStats(stats.radios, converted);
        verifyTwoRadioStatsAggregation_1_5(stats.radios, converted);
        assertEquals(2, converted.numRadios);
    }

    /**
     * Test that the link layer stats V1_5 fields are not aggregated on setting
     * config_wifiLinkLayerAllRadiosStatsAggregationEnabled to false (default value).
     */
    @Test
    public void testRadioStatsAggregationDisabled_1_5() throws Exception {
        Random r = new Random(245786856);
        android.hardware.wifi.V1_5.StaLinkLayerStats stats =
                new android.hardware.wifi.V1_5.StaLinkLayerStats();
        // Fill stats in two radios
        for (int i = 0; i < 2; i++) {
            android.hardware.wifi.V1_5.StaLinkLayerRadioStats rstat =
                    new android.hardware.wifi.V1_5.StaLinkLayerRadioStats();
            randomizeRadioStats_1_5(r, rstat);
            stats.radios.add(rstat);
        }

        WifiLinkLayerStats converted = mDut.frameworkFromHalLinkLayerStats_1_5(stats);
        verifyPerRadioStats(stats.radios, converted);
        verifyRadioStats_1_5(stats.radios.get(0), converted);
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
        fateReport.frameInfo.driverTimestampUsec = new Random().nextLong();
        fateReport.frameInfo.frameType = WifiDebugPacketFateFrameType.ETHERNET_II;
        fateReport.frameInfo.frameContent.addAll(
                NativeUtil.byteArrayToArrayList(frameContentBytes));

        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(IWifiStaIface.getDebugTxPacketFatesCallback cb) {
                cb.onValues(mWifiStatusSuccess, new ArrayList<>(Arrays.asList(fateReport)));
            }
        }).when(mIWifiStaIfaceMock)
                .getDebugTxPacketFates(any(IWifiStaIface.getDebugTxPacketFatesCallback.class));

        List<WifiNative.TxFateReport> retrievedFates = mDut.getDebugTxPacketFates();
        assertEquals(1, retrievedFates.size());
        WifiNative.TxFateReport retrievedFate = retrievedFates.get(0);
        verify(mIWifiStaIfaceMock)
                .getDebugTxPacketFates(any(IWifiStaIface.getDebugTxPacketFatesCallback.class));
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
        fateReport.frameInfo.driverTimestampUsec = new Random().nextLong();
        fateReport.frameInfo.frameType = WifiDebugPacketFateFrameType.MGMT_80211;
        fateReport.frameInfo.frameContent.addAll(
                NativeUtil.byteArrayToArrayList(frameContentBytes));

        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(IWifiStaIface.getDebugTxPacketFatesCallback cb) {
                cb.onValues(mWifiStatusSuccess, new ArrayList<>(
                        // create twice as many as the max size
                        Collections.nCopies(WifiLoggerHal.MAX_FATE_LOG_LEN * 2, fateReport)));
            }
        }).when(mIWifiStaIfaceMock)
                .getDebugTxPacketFates(any(IWifiStaIface.getDebugTxPacketFatesCallback.class));

        List<WifiNative.TxFateReport> retrievedFates = mDut.getDebugTxPacketFates();
        // assert that at most WifiLoggerHal.MAX_FATE_LOG_LEN is retrieved
        assertEquals(WifiLoggerHal.MAX_FATE_LOG_LEN, retrievedFates.size());
        WifiNative.TxFateReport retrievedFate = retrievedFates.get(0);
        verify(mIWifiStaIfaceMock)
                .getDebugTxPacketFates(any(IWifiStaIface.getDebugTxPacketFatesCallback.class));
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
        fateReport.frameInfo.driverTimestampUsec = new Random().nextLong();
        fateReport.frameInfo.frameType = WifiDebugPacketFateFrameType.ETHERNET_II;
        fateReport.frameInfo.frameContent.addAll(
                NativeUtil.byteArrayToArrayList(frameContentBytes));

        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(IWifiStaIface.getDebugRxPacketFatesCallback cb) {
                cb.onValues(mWifiStatusSuccess, new ArrayList<>(Arrays.asList(fateReport)));
            }
        }).when(mIWifiStaIfaceMock)
                .getDebugRxPacketFates(any(IWifiStaIface.getDebugRxPacketFatesCallback.class));

        List<WifiNative.RxFateReport> retrievedFates = mDut.getDebugRxPacketFates();
        assertEquals(1, retrievedFates.size());
        WifiNative.RxFateReport retrievedFate = retrievedFates.get(0);
        verify(mIWifiStaIfaceMock)
                .getDebugRxPacketFates(any(IWifiStaIface.getDebugRxPacketFatesCallback.class));
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
        fateReport.frameInfo.driverTimestampUsec = new Random().nextLong();
        fateReport.frameInfo.frameType = WifiDebugPacketFateFrameType.MGMT_80211;
        fateReport.frameInfo.frameContent.addAll(
                NativeUtil.byteArrayToArrayList(frameContentBytes));

        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(IWifiStaIface.getDebugRxPacketFatesCallback cb) {
                cb.onValues(mWifiStatusSuccess, new ArrayList<>(
                        // create twice as many as the max size
                        Collections.nCopies(WifiLoggerHal.MAX_FATE_LOG_LEN * 2, fateReport)));
            }
        }).when(mIWifiStaIfaceMock)
                .getDebugRxPacketFates(any(IWifiStaIface.getDebugRxPacketFatesCallback.class));

        List<WifiNative.RxFateReport> retrievedFates = mDut.getDebugRxPacketFates();
        assertEquals(WifiLoggerHal.MAX_FATE_LOG_LEN, retrievedFates.size());
        verify(mIWifiStaIfaceMock)
                .getDebugRxPacketFates(any(IWifiStaIface.getDebugRxPacketFatesCallback.class));
        WifiNative.RxFateReport retrievedFate = retrievedFates.get(0);
        assertEquals(WifiLoggerHal.RX_PKT_FATE_FW_DROP_FILTER, retrievedFate.mFate);
        assertEquals(fateReport.frameInfo.driverTimestampUsec, retrievedFate.mDriverTimestampUSec);
        assertEquals(WifiLoggerHal.FRAME_TYPE_80211_MGMT, retrievedFate.mFrameType);
        assertArrayEquals(frameContentBytes, retrievedFate.mFrameBytes);
    }

    /**
     * Helper class for mocking the getRoamingCapabilities callback.
     */
    private class GetRoamingCapabilitiesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private final WifiStatus mStatus;
        private final StaRoamingCapabilities mCaps;

        GetRoamingCapabilitiesAnswer(WifiStatus status, StaRoamingCapabilities caps) {
            mStatus = status;
            mCaps = caps;
        }

        public void answer(IWifiStaIface.getRoamingCapabilitiesCallback cb) {
            cb.onValues(mStatus, mCaps);
        }
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
            caps.maxBlacklistSize = blocklistSize;
            caps.maxWhitelistSize = allowlistSize;
            doAnswer(new GetRoamingCapabilitiesAnswer(mWifiStatusSuccess, caps))
                    .when(mIWifiStaIfaceMock).getRoamingCapabilities(
                            any(IWifiStaIface.getRoamingCapabilitiesCallback.class));
            WifiNative.RoamingCapabilities roamCap = mDut.getRoamingCapabilities();
            assertNotNull(roamCap);
            assertEquals(blocklistSize, roamCap.maxBlocklistSize);
            assertEquals(allowlistSize, roamCap.maxAllowlistSize);
        }
    }

    /**
     * Tests the unsuccessful retrieval of firmware roaming capabilities.
     */
    @Test
    public void testUnsuccessfulFirmwareRoamingCapabilityRetrieval() throws Exception {
        StaRoamingCapabilities caps = new StaRoamingCapabilities();
        caps.maxBlacklistSize = 43;
        caps.maxWhitelistSize = 18;

        // HAL returns a failure status
        doAnswer(new GetRoamingCapabilitiesAnswer(mWifiStatusFailure, null))
                .when(mIWifiStaIfaceMock).getRoamingCapabilities(
                        any(IWifiStaIface.getRoamingCapabilitiesCallback.class));
        assertNull(mDut.getRoamingCapabilities());

        // HAL returns failure status, but supplies caps anyway
        doAnswer(new GetRoamingCapabilitiesAnswer(mWifiStatusFailure, caps))
                .when(mIWifiStaIfaceMock).getRoamingCapabilities(
                        any(IWifiStaIface.getRoamingCapabilitiesCallback.class));
        assertNull(mDut.getRoamingCapabilities());

        // lost connection
        doThrow(new RemoteException())
                .when(mIWifiStaIfaceMock).getRoamingCapabilities(
                        any(IWifiStaIface.getRoamingCapabilitiesCallback.class));
        assertNull(mDut.getRoamingCapabilities());
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
     * Tests that setRoamingState can handle a remote exception.
     */
    @Test
    public void testEnableFirmwareRoamingException() throws Exception {
        doThrow(new RemoteException()).when(mIWifiStaIfaceMock).setRoamingState(anyByte());
        assertEquals(WifiNative.SET_FIRMWARE_ROAMING_FAILURE,
                mDut.setRoamingState(WifiNative.ENABLE_FIRMWARE_ROAMING));
    }

    /**
     * Verifies setMacAddress() success.
     */
    @Test
    public void testSetMacAddressSuccess() throws Exception {
        // Expose the 1.2 IWifiStaIface.
        mDut = new WifiStaIfaceHidlImplSpy(2);
        byte[] macByteArray = TEST_MAC_ADDRESS.toByteArray();
        when(mIWifiStaIfaceMockV12.setMacAddress(macByteArray)).thenReturn(mWifiStatusSuccess);

        assertTrue(mDut.setMacAddress(TEST_MAC_ADDRESS));
        verify(mIWifiStaIfaceMockV12).setMacAddress(macByteArray);
    }

    /**
     * Verifies that setMacAddress() can handle a failure status.
     */
    @Test
    public void testSetMacAddressFailDueToStatusFailure() throws Exception {
        // Expose the 1.2 IWifiStaIface.
        mDut = new WifiStaIfaceHidlImplSpy(2);
        byte[] macByteArray = TEST_MAC_ADDRESS.toByteArray();
        when(mIWifiStaIfaceMockV12.setMacAddress(macByteArray)).thenReturn(mWifiStatusFailure);

        assertFalse(mDut.setMacAddress(TEST_MAC_ADDRESS));
        verify(mIWifiStaIfaceMockV12).setMacAddress(macByteArray);
    }

    /**
     * Verifies that setMacAddress() can handle a RemoteException.
     */
    @Test
    public void testSetMacAddressFailDueToRemoteException() throws Exception {
        // Expose the 1.2 IWifiStaIface.
        mDut = new WifiStaIfaceHidlImplSpy(2);
        byte[] macByteArray = TEST_MAC_ADDRESS.toByteArray();
        doThrow(new RemoteException()).when(mIWifiStaIfaceMockV12).setMacAddress(macByteArray);

        assertFalse(mDut.setMacAddress(TEST_MAC_ADDRESS));
        verify(mIWifiStaIfaceMockV12).setMacAddress(macByteArray);
    }
}
