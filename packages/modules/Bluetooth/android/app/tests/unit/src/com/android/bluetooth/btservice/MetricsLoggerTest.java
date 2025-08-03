/*
 * Copyright 2018 The Android Open Source Project
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
package com.android.bluetooth.btservice;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMetricsProto.BluetoothLog;
import com.android.bluetooth.BluetoothMetricsProto.ProfileConnectionStats;
import com.android.bluetooth.BluetoothMetricsProto.ProfileId;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 * Unit tests for {@link MetricsLogger}
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class MetricsLoggerTest {
    private static final String TEST_BLOOMFILTER_NAME = "TestBloomfilter";

    private TestableMetricsLogger mTestableMetricsLogger;
    @Mock
    private AdapterService mMockAdapterService;

    public class TestableMetricsLogger extends MetricsLogger {
        public HashMap<Integer, Long> mTestableCounters = new HashMap<>();
        public HashMap<String, Integer> mTestableDeviceNames = new HashMap<>();

        @Override
        public boolean count(int key, long count) {
            mTestableCounters.put(key, count);
          return true;
        }

        @Override
        protected void scheduleDrains() {
        }

        @Override
        protected void cancelPendingDrain() {
        }

        @Override
        protected void statslogBluetoothDeviceNames(
                int metricId, String matchedString, String sha256) {
            mTestableDeviceNames.merge(matchedString, 1, Integer::sum);
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        // Dump metrics to clean up internal states
        MetricsLogger.dumpProto(BluetoothLog.newBuilder());
        mTestableMetricsLogger = new TestableMetricsLogger();
        mTestableMetricsLogger.mBloomFilterInitialized = true;
        doReturn(null)
                .when(mMockAdapterService).registerReceiver(any(), any());
    }

    @After
    public void tearDown() {
        // Dump metrics to clean up internal states
        MetricsLogger.dumpProto(BluetoothLog.newBuilder());
        mTestableMetricsLogger.close();
    }

    /**
     * Simple test to verify that profile connection event can be logged, dumped, and cleaned
     */
    @Test
    public void testLogProfileConnectionEvent() {
        MetricsLogger.logProfileConnectionEvent(ProfileId.AVRCP);
        BluetoothLog.Builder metricsBuilder = BluetoothLog.newBuilder();
        MetricsLogger.dumpProto(metricsBuilder);
        BluetoothLog metricsProto = metricsBuilder.build();
        Assert.assertEquals(1, metricsProto.getProfileConnectionStatsCount());
        ProfileConnectionStats profileUsageStatsAvrcp = metricsProto.getProfileConnectionStats(0);
        Assert.assertEquals(ProfileId.AVRCP, profileUsageStatsAvrcp.getProfileId());
        Assert.assertEquals(1, profileUsageStatsAvrcp.getNumTimesConnected());
        // Verify that MetricsLogger's internal state is cleared after a dump
        BluetoothLog.Builder metricsBuilderAfterDump = BluetoothLog.newBuilder();
        MetricsLogger.dumpProto(metricsBuilderAfterDump);
        BluetoothLog metricsProtoAfterDump = metricsBuilderAfterDump.build();
        Assert.assertEquals(0, metricsProtoAfterDump.getProfileConnectionStatsCount());
    }

    /**
     * Test whether multiple profile's connection events can be logged interleaving
     */
    @Test
    public void testLogProfileConnectionEventMultipleProfile() {
        MetricsLogger.logProfileConnectionEvent(ProfileId.AVRCP);
        MetricsLogger.logProfileConnectionEvent(ProfileId.HEADSET);
        MetricsLogger.logProfileConnectionEvent(ProfileId.AVRCP);
        BluetoothLog.Builder metricsBuilder = BluetoothLog.newBuilder();
        MetricsLogger.dumpProto(metricsBuilder);
        BluetoothLog metricsProto = metricsBuilder.build();
        Assert.assertEquals(2, metricsProto.getProfileConnectionStatsCount());
        HashMap<ProfileId, ProfileConnectionStats> profileConnectionCountMap =
                getProfileUsageStatsMap(metricsProto.getProfileConnectionStatsList());
        Assert.assertTrue(profileConnectionCountMap.containsKey(ProfileId.AVRCP));
        Assert.assertEquals(2,
                profileConnectionCountMap.get(ProfileId.AVRCP).getNumTimesConnected());
        Assert.assertTrue(profileConnectionCountMap.containsKey(ProfileId.HEADSET));
        Assert.assertEquals(1,
                profileConnectionCountMap.get(ProfileId.HEADSET).getNumTimesConnected());
        // Verify that MetricsLogger's internal state is cleared after a dump
        BluetoothLog.Builder metricsBuilderAfterDump = BluetoothLog.newBuilder();
        MetricsLogger.dumpProto(metricsBuilderAfterDump);
        BluetoothLog metricsProtoAfterDump = metricsBuilderAfterDump.build();
        Assert.assertEquals(0, metricsProtoAfterDump.getProfileConnectionStatsCount());
    }

    private static HashMap<ProfileId, ProfileConnectionStats> getProfileUsageStatsMap(
            List<ProfileConnectionStats> profileUsageStats) {
        HashMap<ProfileId, ProfileConnectionStats> profileUsageStatsMap = new HashMap<>();
        profileUsageStats.forEach(item -> profileUsageStatsMap.put(item.getProfileId(), item));
        return profileUsageStatsMap;
    }

    /**
     * Test add counters and send them to statsd
     */
    @Test
    public void testAddAndSendCountersNormalCases() {
        mTestableMetricsLogger.init(mMockAdapterService);
        mTestableMetricsLogger.cacheCount(1, 10);
        mTestableMetricsLogger.cacheCount(1, 10);
        mTestableMetricsLogger.cacheCount(2, 5);
        mTestableMetricsLogger.drainBufferedCounters();

        Assert.assertEquals(20L, mTestableMetricsLogger.mTestableCounters.get(1).longValue());
        Assert.assertEquals(5L, mTestableMetricsLogger.mTestableCounters.get(2).longValue());

        mTestableMetricsLogger.cacheCount(1, 3);
        mTestableMetricsLogger.cacheCount(2, 5);
        mTestableMetricsLogger.cacheCount(2, 5);
        mTestableMetricsLogger.cacheCount(3, 1);
        mTestableMetricsLogger.drainBufferedCounters();
        Assert.assertEquals(
                3L, mTestableMetricsLogger.mTestableCounters.get(1).longValue());
        Assert.assertEquals(
                10L, mTestableMetricsLogger.mTestableCounters.get(2).longValue());
        Assert.assertEquals(
                1L, mTestableMetricsLogger.mTestableCounters.get(3).longValue());
    }

    @Test
    public void testAddAndSendCountersCornerCases() {
        mTestableMetricsLogger.init(mMockAdapterService);
        Assert.assertTrue(mTestableMetricsLogger.isInitialized());
        mTestableMetricsLogger.cacheCount(1, -1);
        mTestableMetricsLogger.cacheCount(3, 0);
        mTestableMetricsLogger.cacheCount(2, 10);
        mTestableMetricsLogger.cacheCount(2, Long.MAX_VALUE - 8L);
        mTestableMetricsLogger.drainBufferedCounters();

        Assert.assertFalse(mTestableMetricsLogger.mTestableCounters.containsKey(1));
        Assert.assertFalse(mTestableMetricsLogger.mTestableCounters.containsKey(3));
        Assert.assertEquals(
                Long.MAX_VALUE, mTestableMetricsLogger.mTestableCounters.get(2).longValue());
    }

    @Test
    public void testMetricsLoggerClose() {
        mTestableMetricsLogger.init(mMockAdapterService);
        mTestableMetricsLogger.cacheCount(1, 1);
        mTestableMetricsLogger.cacheCount(2, 10);
        mTestableMetricsLogger.cacheCount(2, Long.MAX_VALUE);
        mTestableMetricsLogger.close();

        Assert.assertEquals(
                1, mTestableMetricsLogger.mTestableCounters.get(1).longValue());
        Assert.assertEquals(
                Long.MAX_VALUE, mTestableMetricsLogger.mTestableCounters.get(2).longValue());
    }

    @Test
    public void testMetricsLoggerNotInit() {
        Assert.assertFalse(mTestableMetricsLogger.cacheCount(1, 1));
        mTestableMetricsLogger.drainBufferedCounters();
        Assert.assertFalse(mTestableMetricsLogger.mTestableCounters.containsKey(1));
        Assert.assertFalse(mTestableMetricsLogger.close());
    }

    @Test
    public void testAddAndSendCountersDoubleInit() {
        Assert.assertTrue(mTestableMetricsLogger.init(mMockAdapterService));
        Assert.assertTrue(mTestableMetricsLogger.isInitialized());
        Assert.assertFalse(mTestableMetricsLogger.init(mMockAdapterService));
    }

    @Test
    public void testDeviceNameUploadingDeviceSet1() {
        initTestingBloomfitler();

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "a b c d e f g h pixel 7");
        Assert.assertTrue(mTestableMetricsLogger.mTestableDeviceNames.isEmpty());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "AirpoDspro");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("airpodspro").intValue());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "AirpoDs-pro");
        Assert.assertEquals(2,
                mTestableMetricsLogger.mTestableDeviceNames.get("airpodspro").intValue());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "Someone's AirpoDs");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("airpods").intValue());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "Who's Pixel 7");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("pixel7").intValue());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "陈的pixel 7手机");
        Assert.assertEquals(2,
                mTestableMetricsLogger.mTestableDeviceNames.get("pixel7").intValue());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(2, "pixel 7 pro");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("pixel7pro").intValue());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "My Pixel 7 PRO");
        Assert.assertEquals(2,
                mTestableMetricsLogger.mTestableDeviceNames.get("pixel7pro").intValue());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "My Pixel   7   PRO");
        Assert.assertEquals(3,
                mTestableMetricsLogger.mTestableDeviceNames.get("pixel7pro").intValue());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "My Pixel   7   - PRO");
        Assert.assertEquals(4,
                mTestableMetricsLogger.mTestableDeviceNames.get("pixel7pro").intValue());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "My BMW X5");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("bmwx5").intValue());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "Jane Doe's Tesla Model--X");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("teslamodelx").intValue());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "TESLA of Jane DOE");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("tesla").intValue());

        mTestableMetricsLogger
                .logSanitizedBluetoothDeviceName(1, "SONY WH-1000XM noise cancelling headsets");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("sonywh1000xm").intValue());

        mTestableMetricsLogger
                .logSanitizedBluetoothDeviceName(1, "SONY WH-1000XM4 noise cancelling headsets");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("sonywh1000xm4").intValue());

        mTestableMetricsLogger
                .logSanitizedBluetoothDeviceName(1, "Amazon Echo Dot in Kitchen");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("amazonechodot").intValue());

        mTestableMetricsLogger
                .logSanitizedBluetoothDeviceName(1, "斯巴鲁 Starlink");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("starlink").intValue());

        mTestableMetricsLogger
                .logSanitizedBluetoothDeviceName(1, "大黄蜂MyLink");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("mylink").intValue());

        mTestableMetricsLogger
                .logSanitizedBluetoothDeviceName(1, "Dad's Fitbit Charge 3");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("fitbitcharge3").intValue());

        mTestableMetricsLogger.mTestableDeviceNames.clear();
        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "");
        Assert.assertTrue(mTestableMetricsLogger.mTestableDeviceNames.isEmpty());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, " ");
        Assert.assertTrue(mTestableMetricsLogger.mTestableDeviceNames.isEmpty());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "SomeDevice1");
        Assert.assertTrue(mTestableMetricsLogger.mTestableDeviceNames.isEmpty());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "Bluetooth headset");
        Assert.assertTrue(mTestableMetricsLogger.mTestableDeviceNames.isEmpty());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(3, "Some Device-2");
        Assert.assertTrue(mTestableMetricsLogger.mTestableDeviceNames.isEmpty());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(5, "abcgfDG gdfg");
        Assert.assertTrue(mTestableMetricsLogger.mTestableDeviceNames.isEmpty());
    }

    @Test
    public void testDeviceNameUploadingDeviceSet2() {
        initTestingBloomfitler();

        mTestableMetricsLogger
                .logSanitizedBluetoothDeviceName(1, "Galaxy Buds pro");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("galaxybudspro").intValue());

        mTestableMetricsLogger
                .logSanitizedBluetoothDeviceName(1, "Mike's new Galaxy Buds 2");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("galaxybuds2").intValue());

        mTestableMetricsLogger
                .logSanitizedBluetoothDeviceName(877, "My third Ford F-150");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("fordf150").intValue());

        mTestableMetricsLogger
                .logSanitizedBluetoothDeviceName(1, "BOSE QC_35 Noise Cancelling Headsets");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("boseqc35").intValue());

        mTestableMetricsLogger
                .logSanitizedBluetoothDeviceName(1, "BOSE Quiet Comfort 35 Headsets");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("bosequietcomfort35").intValue());

        mTestableMetricsLogger
                .logSanitizedBluetoothDeviceName(1, "Fitbit versa 3 band");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("fitbitversa3").intValue());

        mTestableMetricsLogger
                .logSanitizedBluetoothDeviceName(1, "vw atlas");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("vwatlas").intValue());

        mTestableMetricsLogger
                .logSanitizedBluetoothDeviceName(1, "My volkswagen tiguan");
        Assert.assertEquals(1,
                mTestableMetricsLogger.mTestableDeviceNames.get("volkswagentiguan").intValue());

        mTestableMetricsLogger.mTestableDeviceNames.clear();
        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "");
        Assert.assertTrue(mTestableMetricsLogger.mTestableDeviceNames.isEmpty());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, " ");
        Assert.assertTrue(mTestableMetricsLogger.mTestableDeviceNames.isEmpty());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, "weirddevice");
        Assert.assertTrue(mTestableMetricsLogger.mTestableDeviceNames.isEmpty());

        mTestableMetricsLogger.logSanitizedBluetoothDeviceName(1, ""
                + "My BOSE Quiet Comfort 35 Noise Cancelling Headsets");
        // Too long, won't process
        Assert.assertTrue(mTestableMetricsLogger.mTestableDeviceNames.isEmpty());

    }
    private void initTestingBloomfitler() {
        byte[] bloomfilterData = DeviceBloomfilterGenerator.hexStringToByteArray(
                DeviceBloomfilterGenerator.BLOOM_FILTER_DEFAULT);
        try {
            mTestableMetricsLogger.setBloomfilter(
                    BloomFilter.readFrom(
                            new ByteArrayInputStream(bloomfilterData), Funnels.byteArrayFunnel()));
        } catch (IOException e) {
            Assert.assertTrue(false);
        }
    }
}
