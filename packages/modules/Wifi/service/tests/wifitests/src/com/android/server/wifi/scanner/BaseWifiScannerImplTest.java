/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.wifi.scanner;

import static com.android.server.wifi.ScanTestUtil.NativeScanSettingsBuilder;
import static com.android.server.wifi.ScanTestUtil.assertScanDataEquals;
import static com.android.server.wifi.ScanTestUtil.createFreqSet;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.test.TestAlarmManager;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.ScanData;
import android.net.wifi.WifiSsid;
import android.os.SystemClock;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.Clock;
import com.android.server.wifi.MockResources;
import com.android.server.wifi.MockWifiMonitor;
import com.android.server.wifi.ScanDetail;
import com.android.server.wifi.ScanResults;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiGlobals;
import com.android.server.wifi.WifiMonitor;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.scanner.ChannelHelper.ChannelCollection;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Base unit tests that should pass for all implementations of
 * {@link com.android.server.wifi.scanner.WifiScannerImpl}.
 */
@SmallTest
public abstract class BaseWifiScannerImplTest extends WifiBaseTest {
    protected static final String IFACE_NAME = "a_test_interface_name";
    @Mock Context mContext;
    TestAlarmManager mAlarmManager;
    MockWifiMonitor mWifiMonitor;
    TestLooper mLooper;
    @Mock WifiNative mWifiNative;
    MockResources mResources;
    @Mock Clock mClock;
    @Mock WifiGlobals mWifiGlobals;

    /**
     * mScanner implementation should be filled in by derived test class
     */
    WifiScannerImpl mScanner;

    @Before
    public void setUpBase() throws Exception {
        MockitoAnnotations.initMocks(this);

        mLooper = new TestLooper();
        mAlarmManager = new TestAlarmManager();
        mWifiMonitor = new MockWifiMonitor();
        mResources = new MockResources();

        when(mContext.getSystemService(Context.ALARM_SERVICE))
                .thenReturn(mAlarmManager.getAlarmManager());

        when(mContext.getResources()).thenReturn(mResources);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(SystemClock.elapsedRealtime());
        when(mClock.getElapsedSinceBootNanos()).thenReturn(SystemClock.elapsedRealtimeNanos());
    }

    protected Set<Integer> expectedBandScanFreqs(int band) {
        ChannelCollection collection = mScanner.getChannelHelper().createChannelCollection();
        collection.addBand(band);
        return collection.getScanFreqs();
    }

    protected Set<Integer> expectedBandAndChannelScanFreqs(int band, int... channels) {
        ChannelCollection collection = mScanner.getChannelHelper().createChannelCollection();
        collection.addBand(band);
        for (int channel : channels) {
            collection.addChannel(channel);
        }
        return collection.getScanFreqs();
    }

    @Test
    public void singleScanSuccess() {
        byte[] testVendorIes =
                new byte[]{(byte) 0xdd, 0x7, 0x00, 0x50, (byte) 0xf2, 0x08, 0x11, 0x22, 0x33};
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000) // ms
                .withMaxApPerScan(10)
                .addBucketWithBand(10000 /* ms */, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .withVendorIes(testVendorIes)
                .build();

        doSuccessfulSingleScanTest(settings,
                expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ),
                new ArrayList<String>(),
                ScanResults.create(0, WifiScanner.WIFI_BAND_24_GHZ,
                        2400, 2450, 2450, 2400, 2450, 2450, 2400, 2450, 2450), false, false,
                testVendorIes);
    }

    @Test
    public void singleScanSuccessWithChannels() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithChannels(20000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN, 5650)
                .withEnable6GhzRnr(true)
                .build();

        doSuccessfulSingleScanTest(settings, createFreqSet(5650),
                new ArrayList<String>(),
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED,
                        5650, 5650, 5650, 5650, 5650, 5650, 5650, 5650), false, true, null);
    }

    @Test
    public void singleScanSuccessWithChannelsAndHighAccuracyType() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withType(WifiScanner.SCAN_TYPE_HIGH_ACCURACY)
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithChannels(20000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN, 5650)
                .build();

        doSuccessfulSingleScanTest(settings, createFreqSet(5650),
                new ArrayList<String>(),
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED,
                        5650, 5650, 5650, 5650, 5650, 5650, 5650, 5650), false, false, null);
    }

    @Test
    public void singleScanSuccessWithFullResults() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000,
                        WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN
                        | WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        doSuccessfulSingleScanTest(settings, expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ),
                new ArrayList<String>(),
                ScanResults.create(0, WifiScanner.WIFI_BAND_24_GHZ,
                        2400, 2450, 2450, 2400, 2450, 2450, 2400, 2450, 2450), true, false, null);
    }

    /**
     * Tests whether the provided hidden networkId's in scan settings is correctly passed along
     * when invoking native scan.
     */
    @Test
    public void singleScanSuccessWithHiddenNetworkIds() {
        String[] hiddenNetworkSSIDs = {"test_ssid_1", "test_ssid_2"};
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .withHiddenNetworkSSIDs(hiddenNetworkSSIDs)
                .addBucketWithChannels(20000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN, 5650)
                .build();

        List<String> hiddenNetworkSSIDSet = new ArrayList<>();
        for (int i = 0; i < hiddenNetworkSSIDs.length; i++) {
            hiddenNetworkSSIDSet.add(hiddenNetworkSSIDs[i]);
        }
        doSuccessfulSingleScanTest(settings, createFreqSet(5650),
                hiddenNetworkSSIDSet,
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED,
                        5650, 5650, 5650, 5650, 5650, 5650, 5650, 5650), false, false, null);
    }

    /**
     * Tests whether the provided hidden networkId's in scan settings is truncated to max size
     * supported by wificond when invoking native scan.
     */
    @Test
    public void singleScanSuccessWithTruncatedHiddenNetworkIds() {
        String[] hiddenNetworkSSIDs = {
                "test_ssid_0", "test_ssid_1", "test_ssid_2", "test_ssid_3", "test_ssid_4",
                "test_ssid_5", "test_ssid_6", "test_ssid_7", "test_ssid_8", "test_ssid_9",
                "test_ssid_10", "test_ssid_11", "test_ssid_12", "test_ssid_13", "test_ssid_14",
                "test_ssid_15", "test_ssid_16", "test_ssid_17", "test_ssid_18", "test_ssid_19"
        };
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .withHiddenNetworkSSIDs(hiddenNetworkSSIDs)
                .addBucketWithChannels(20000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN, 5650)
                .build();

        List<String> hiddenNetworkSSIDSet = new ArrayList<>();
        for (int i = 0; i < WificondScannerTest.MAX_NUM_SCAN_SSIDS; i++) {
            hiddenNetworkSSIDSet.add(hiddenNetworkSSIDs[i]);
        }
        doSuccessfulSingleScanTest(settings, createFreqSet(5650),
                hiddenNetworkSSIDSet,
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED,
                        5650, 5650, 5650, 5650, 5650, 5650, 5650), false, false, null);
    }

    @Test
    public void overlappingSingleScanFails() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000) // ms
                .withMaxApPerScan(10)
                .addBucketWithBand(10000 /* ms */, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        WifiNative.ScanSettings settings2 = new NativeScanSettingsBuilder()
                .withBasePeriod(10000) // ms
                .withMaxApPerScan(10)
                .addBucketWithBand(10000 /* ms */, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_5_GHZ)
                .build();
        WifiNative.ScanEventHandler eventHandler2 = mock(WifiNative.ScanEventHandler.class);

        // scan start succeeds
        when(mWifiNative.scan(eq(IFACE_NAME), anyInt(), any(), any(List.class), anyBoolean(),
                any())).thenReturn(WifiScanner.REASON_SUCCEEDED);

        assertTrue(mScanner.startSingleScan(settings, eventHandler));
        assertFalse("second scan while first scan running should fail immediately",
                mScanner.startSingleScan(settings2, eventHandler2));
    }

    @Test
    public void singleScanFailOnExecute() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        InOrder order = inOrder(eventHandler, mWifiNative);

        // scan fails
        when(mWifiNative.scan(eq(IFACE_NAME), anyInt(), any(), any(List.class), anyBoolean(),
                any())).thenReturn(WifiScanner.REASON_UNSPECIFIED);

        // start scan
        assertTrue(mScanner.startSingleScan(settings, eventHandler));

        mLooper.dispatchAll();
        order.verify(eventHandler).onScanRequestFailed(eq(WifiScanner.REASON_UNSPECIFIED));

        verifyNoMoreInteractions(eventHandler);
    }

    /**
     * Test that a scan failure is reported if a scan times out
     */
    @Test
    public void singleScanFailOnTimeout() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        InOrder order = inOrder(eventHandler, mWifiNative);

        // scan succeeds
        when(mWifiNative.scan(eq(IFACE_NAME), anyInt(), any(), any(List.class), anyBoolean(),
                any())).thenReturn(WifiScanner.REASON_SUCCEEDED);

        // start scan
        assertTrue(mScanner.startSingleScan(settings, eventHandler));
        mLooper.dispatchAll();

        // Fire timeout
        mAlarmManager.dispatch(WificondScannerImpl.TIMEOUT_ALARM_TAG);
        mLooper.dispatchAll();

        order.verify(eventHandler).onScanRequestFailed(eq(WifiScanner.REASON_TIMEOUT));

        verifyNoMoreInteractions(eventHandler);
    }

    /**
     * Test that a scan failure is reported if wificond sends a scan failed event
     */
    @Test
    public void singleScanFailOnFailedEvent() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        InOrder order = inOrder(eventHandler, mWifiNative);

        // scan succeeds
        when(mWifiNative.scan(eq(IFACE_NAME), anyInt(), any(), any(List.class), anyBoolean(),
                any())).thenReturn(WifiScanner.REASON_SUCCEEDED);

        // start scan
        assertTrue(mScanner.startSingleScan(settings, eventHandler));
        mLooper.dispatchAll();

        // Fire failed event
        mWifiMonitor.sendMessage(eq(IFACE_NAME), WifiMonitor.SCAN_FAILED_EVENT,
                WifiScanner.REASON_UNSPECIFIED);
        mLooper.dispatchAll();

        order.verify(eventHandler).onScanRequestFailed(eq(WifiScanner.REASON_UNSPECIFIED));

        verifyNoMoreInteractions(eventHandler);
    }

    @Test
    public void singleScanNullEventHandler() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();
        assertFalse(mScanner.startSingleScan(settings, null));
    }

    @Test
    public void singleScanNullSettings() {
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        assertFalse(mScanner.startSingleScan(null, eventHandler));

        verifyNoMoreInteractions(eventHandler);
    }

    @Test
    public void multipleSingleScanSuccess() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();
        WifiNative.ScanSettings settings2 = new NativeScanSettingsBuilder()
                .withType(WifiScanner.SCAN_TYPE_LOW_POWER)
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_60_GHZ)
                .build();

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        InOrder order = inOrder(eventHandler, mWifiNative);

        // scans succeed
        when(mWifiNative.scan(eq(IFACE_NAME), anyInt(), any(), any(List.class), anyBoolean(),
                any())).thenReturn(WifiScanner.REASON_SUCCEEDED);

        // start first scan
        assertTrue(mScanner.startSingleScan(settings, eventHandler));

        expectSuccessfulSingleScan(order, WifiScanner.SCAN_TYPE_LOW_LATENCY, eventHandler,
                expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ),
                new ArrayList<String>(),
                ScanResults.create(0, WifiScanner.WIFI_BAND_24_GHZ,
                        2400, 2450, 2450), false, false, null);

        // start second scan
        assertTrue(mScanner.startSingleScan(settings2, eventHandler));

        expectSuccessfulSingleScan(order, WifiScanner.SCAN_TYPE_LOW_POWER, eventHandler,
                expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_60_GHZ),
                new ArrayList<String>(),
                ScanResults.create(0, WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_60_GHZ,
                        5150, 5175), false, false, null);

        verifyNoMoreInteractions(eventHandler);
    }

    /**
     * Validate that scan results that are returned from wificond, which are timestamped prior to
     * the start of the scan, are ignored.
     */
    @Test
    public void singleScanWhereSupplicantReturnsSomeOldResults() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(2)
                .addBucketWithBand(10000,
                        WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN
                        | WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        long approxScanStartNanos = mClock.getElapsedSinceBootNanos();
        ArrayList<ScanDetail> rawResults = new ArrayList<>(Arrays.asList(
                new ScanDetail(WifiSsid.fromUtf8Text("TEST AP 1"),
                        "00:00:00:00:00:00", "", -70, 2450,
                        approxScanStartNanos / 1_000 + 2000 * 1000, 0),
                new ScanDetail(WifiSsid.fromUtf8Text("TEST AP 2"),
                        "AA:BB:CC:DD:EE:FF", "", -66, 2400,
                        approxScanStartNanos / 1_000 + 2500 * 1000, 0),
                // old result will be filtered
                new ScanDetail(WifiSsid.fromUtf8Text("TEST AP 3"),
                        "00:00:00:00:00:00", "", -80, 2450,
                        approxScanStartNanos / 1_0000 - 2000 * 1000, 0),
                new ScanDetail(WifiSsid.fromUtf8Text("TEST AP 4"),
                        "00:00:00:00:00:00", "", -80, 2450,
                        approxScanStartNanos / 1_000 + 200 , 0),
                new ScanDetail(WifiSsid.fromUtf8Text("TEST AP 5"),
                        "AA:BB:CC:11:22:33", "", -65, 2450,
                        approxScanStartNanos / 1_000 + 4000 * 1000, 0)));

        ArrayList<ScanResult> fullResults = new ArrayList<>();
        for (ScanDetail detail : rawResults) {
            if (detail.getScanResult().timestamp * 1_0000 > approxScanStartNanos) {
                fullResults.add(detail.getScanResult());
            }
        }
        ArrayList<ScanResult> scanDataResults = new ArrayList<>(fullResults);
        Collections.sort(scanDataResults, ScanResults.SCAN_RESULT_RSSI_COMPARATOR);
        ScanData scanData = new ScanData(0, 0, 0,
                WifiScanner.WIFI_BAND_24_GHZ,
                scanDataResults.toArray(new ScanResult[scanDataResults.size()]));
        Set<Integer> expectedScan = expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ);

        // Actual test

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        InOrder order = inOrder(eventHandler, mWifiNative);

        // scan succeeds
        when(mWifiNative.scan(eq(IFACE_NAME), anyInt(), any(), any(List.class), anyBoolean(),
                any())).thenReturn(WifiScanner.REASON_SUCCEEDED);

        // start scan
        assertTrue(mScanner.startSingleScan(settings, eventHandler));

        order.verify(mWifiNative).scan(eq(IFACE_NAME), anyInt(), eq(expectedScan), any(List.class),
                anyBoolean(), any());

        when(mWifiNative.getScanResults(eq(IFACE_NAME))).thenReturn(rawResults);

        // Notify scan has finished
        mWifiMonitor.sendMessage(eq(IFACE_NAME), WifiMonitor.SCAN_RESULTS_EVENT);

        mLooper.dispatchAll();

        for (ScanResult result : fullResults) {
            order.verify(eventHandler).onFullScanResult(eq(result), eq(0));
        }

        order.verify(eventHandler).onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
        assertScanDataEquals(scanData, mScanner.getLatestSingleScanResults());

        verifyNoMoreInteractions(eventHandler);
    }

    @Test
    public void backgroundScanNullEventHandler() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();
        assertFalse(mScanner.startBatchedScan(settings, null));
    }

    @Test
    public void backgroundScanNullSettings() {
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        assertFalse(mScanner.startBatchedScan(null, eventHandler));

        verifyNoMoreInteractions(eventHandler);
    }

    protected void doSuccessfulSingleScanTest(WifiNative.ScanSettings settings,
            Set<Integer> expectedScan, List<String> expectedHiddenNetSSIDs, ScanResults results,
            boolean expectFullResults, boolean expectRnr, byte[] expectVendorIes) {
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        InOrder order = inOrder(eventHandler, mWifiNative);

        // scan succeeds
        when(mWifiNative.scan(eq(IFACE_NAME), anyInt(), any(), any(List.class), anyBoolean(),
                any())).thenReturn(WifiScanner.REASON_SUCCEEDED);

        // start scan
        assertTrue(mScanner.startSingleScan(settings, eventHandler));

        expectSuccessfulSingleScan(order, settings.scanType, eventHandler, expectedScan,
                expectedHiddenNetSSIDs, results, expectFullResults, expectRnr, expectVendorIes);

        verifyNoMoreInteractions(eventHandler);
    }

    protected void expectSuccessfulSingleScan(InOrder order,
            int scanType, WifiNative.ScanEventHandler eventHandler, Set<Integer> expectedScan,
            List<String> expectedHiddenNetSSIDs, ScanResults results, boolean expectFullResults,
            boolean expectRnr, byte[] expectVendorIes) {
        order.verify(mWifiNative).scan(
                eq(IFACE_NAME), eq(scanType), eq(expectedScan), eq(expectedHiddenNetSSIDs),
                eq(expectRnr), eq(expectVendorIes));

        when(mWifiNative.getScanResults(
                eq(IFACE_NAME))).thenReturn(results.getScanDetailArrayList());

        // Notify scan has finished
        mWifiMonitor.sendMessage(eq(IFACE_NAME), WifiMonitor.SCAN_RESULTS_EVENT);

        mLooper.dispatchAll();

        if (expectFullResults) {
            for (ScanResult result : results.getRawScanResults()) {
                order.verify(eventHandler).onFullScanResult(eq(result), eq(0));
            }
        }

        order.verify(eventHandler).onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
        assertScanDataEquals(results.getScanData(), mScanner.getLatestSingleScanResults());
    }
}
