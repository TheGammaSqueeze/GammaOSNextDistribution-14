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
import static com.android.server.wifi.ScanTestUtil.createFreqSet;
import static com.android.server.wifi.ScanTestUtil.setupMockChannels;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.*;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.net.wifi.WifiScanner;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.ScanDetail;
import com.android.server.wifi.ScanResults;
import com.android.server.wifi.WifiMonitor;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.scanner.ChannelHelper.ChannelCollection;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Unit tests for {@link com.android.server.wifi.scanner.WificondScannerImpl}.
 */
@SmallTest
public class WificondScannerTest extends BaseWifiScannerImplTest {
    private static final String NATIVE_SCAN_TITLE = "Latest native scan results:";
    private static final String NATIVE_PNO_SCAN_TITLE = "Latest native pno scan results:";
    private static final String NATIVE_SCAN_IE_TITLE = "Latest native scan results IEs:";
    public static final int MAX_NUM_SCAN_SSIDS = 16;

    WifiMonitor mWifiMonitorSpy;
    @Before
    public void setup() throws Exception {
        setupMockChannels(mWifiNative,
                new int[]{2400, 2450},
                new int[]{5150, 5175},
                new int[]{5600, 5650},
                new int[]{5945, 5985},
                new int[]{58320, 60480});
        when(mWifiNative.getMaxSsidsPerScan(anyString())).thenReturn(MAX_NUM_SCAN_SSIDS + 1);
        mWifiMonitorSpy = spy(mWifiMonitor);
        mScanner = new WificondScannerImpl(mContext, BaseWifiScannerImplTest.IFACE_NAME,
                mWifiGlobals, mWifiNative, mWifiMonitorSpy, new WificondChannelHelper(mWifiNative),
                mLooper.getLooper(), mClock);
    }

    /**
     * Test that WificondScannerImpl will not issue a scan and report scan failure
     * when there is no channel to scan for.
     */
    @Test
    public void singleScanNotIssuedIfNoAvailableChannels() {
        // Use mocked ChannelHelper and ChannelCollection to simulate the scenario
        // that no channel is available for this request.
        ChannelHelper channelHelper = mock(ChannelHelper.class);
        ChannelCollection channelCollection = mock(ChannelCollection.class);
        when(channelHelper.createChannelCollection()).thenReturn(channelCollection);
        when(channelCollection.isEmpty()).thenReturn(true);

        mScanner = new WificondScannerImpl(mContext, BaseWifiScannerImplTest.IFACE_NAME,
                mWifiGlobals, mWifiNative, mWifiMonitor, channelHelper, mLooper.getLooper(),
                mClock);

        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000) // ms
                .withMaxApPerScan(10)
                .addBucketWithBand(10000 /* ms */, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_5_GHZ)
                .build();
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        mScanner.startSingleScan(settings, eventHandler);

        mLooper.dispatchAll();

        // No scan is issued to WifiNative.
        verify(mWifiNative, never()).scan(any(), anyInt(), any(), any(List.class), anyBoolean(),
                any());
        // A scan failed event must be reported.
        verify(eventHandler).onScanRequestFailed(eq(WifiScanner.REASON_UNSPECIFIED));
    }

    @Test
    public void cleanupReportsFailureIfScanInProgress() {
        when(mWifiNative.scan(eq(IFACE_NAME), anyInt(), any(), any(List.class), anyBoolean(),
                any())).thenReturn(WifiScanner.REASON_SUCCEEDED);

        // setup ongoing scan
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000) // ms
                .withMaxApPerScan(10)
                .addBucketWithBand(10000 /* ms */, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_5_GHZ)
                .build();
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        mScanner.startSingleScan(settings, eventHandler);
        mLooper.dispatchAll();

        // no scan failure message
        verify(eventHandler, never()).onScanRequestFailed(eq(WifiScanner.REASON_UNSPECIFIED));

        // tear down the iface
        mScanner.cleanup();

        // verify received scan failure callback
        verify(eventHandler).onScanRequestFailed(eq(WifiScanner.REASON_UNSPECIFIED));
    }

    @Test
    public void externalScanResultsDoNotCauseSpuriousTimerCancellationOrCrash() {
        mWifiMonitor.sendMessage(IFACE_NAME, WifiMonitor.SCAN_RESULTS_EVENT);
        mLooper.dispatchAll();
        verify(mAlarmManager.getAlarmManager(), never()).cancel(any(PendingIntent.class));
        verify(mAlarmManager.getAlarmManager(), never())
                .cancel(any(AlarmManager.OnAlarmListener.class));
        verify(mAlarmManager.getAlarmManager(), never()).cancel(isNull(PendingIntent.class));
        verify(mAlarmManager.getAlarmManager(), never())
                .cancel(isNull(AlarmManager.OnAlarmListener.class));
    }

    @Test
    public void externalScanResultsAfterOurScanDoNotCauseSpuriousTimerCancellationOrCrash() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000) // ms
                .withMaxApPerScan(10)
                .addBucketWithBand(10000 /* ms */, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        doSuccessfulSingleScanTest(settings,
                expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ),
                new ArrayList<String>(),
                ScanResults.create(0, WifiScanner.WIFI_BAND_24_GHZ,
                        2400, 2450, 2450, 2400, 2450, 2450, 2400, 2450, 2450), false, false, null);

        mWifiMonitor.sendMessage(IFACE_NAME, WifiMonitor.SCAN_RESULTS_EVENT);
        mLooper.dispatchAll();
        verify(mAlarmManager.getAlarmManager(), never()).cancel(any(PendingIntent.class));
        verify(mAlarmManager.getAlarmManager(), times(1))
                .cancel(any(AlarmManager.OnAlarmListener.class));
        verify(mAlarmManager.getAlarmManager(), never()).cancel(isNull(PendingIntent.class));
        verify(mAlarmManager.getAlarmManager(), never())
                .cancel(isNull(AlarmManager.OnAlarmListener.class));
    }

    @Test
    public void lateScanResultsDoNotCauseSpuriousTimerCancellationOrCrash() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000) // ms
                .withMaxApPerScan(10)
                .addBucketWithBand(10000 /* ms */, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        // Kick off a scan
        when(mWifiNative.scan(eq(IFACE_NAME), anyInt(), any(), any(List.class), anyBoolean(),
                any())).thenReturn(WifiScanner.REASON_SUCCEEDED);
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        assertTrue(mScanner.startSingleScan(settings, eventHandler));
        mLooper.dispatchAll();

        // Report a timeout
        mAlarmManager.dispatch(WificondScannerImpl.TIMEOUT_ALARM_TAG);
        mLooper.dispatchAll();

        // Now report scan results (results lost the race with timeout)
        mWifiMonitor.sendMessage(IFACE_NAME, WifiMonitor.SCAN_RESULTS_EVENT);
        mLooper.dispatchAll();

        verify(mAlarmManager.getAlarmManager(), never()).cancel(any(PendingIntent.class));
        verify(mAlarmManager.getAlarmManager(), never())
                .cancel(any(AlarmManager.OnAlarmListener.class));
        verify(mAlarmManager.getAlarmManager(), never()).cancel(isNull(PendingIntent.class));
        verify(mAlarmManager.getAlarmManager(), never())
                .cancel(isNull(AlarmManager.OnAlarmListener.class));
    }

    /**
     * Test that dump() of WificondScannerImpl dumps native scan results with correct format when
     * scan result is empty.
     */
    @Test
    public void testDumpWithCorrectFormatWithEmptyScanResult() {
        // The format of scan dump when zero Ap in scan result.
        // ---------------------------------------------
        // "Latest native scan results:\n"    : Key word can't modify.
        // "Latest native pno scan results:\n": Key word can't modify.
        // " ...                           \n": Any String and any line. No limited.
        // "Latest native scan results IEs:\n": Key word can't modify.
        // "\n"                               : Should be stop with "\n"
        //---------------------------------------------
        Pattern zeroScanResultRegex = Pattern.compile(
                "" + NATIVE_SCAN_TITLE + "\n"
                + "" + NATIVE_PNO_SCAN_TITLE + "\n"
                + "(.*\n)*"
                + "" + NATIVE_SCAN_IE_TITLE + "\n"
                + "\n");

        String dump = dumpObject();

        assertLogContainsRequestPattern(zeroScanResultRegex, dump);

    }

    /**
     * Test that dump() of WificondScannerImpl dumps native scan results with correct format when
     * the number of AP in the scan result is not zero.
     */
    @Test
    public void testDumpWithCorrectFormatWithScanResult() {
        // Prepare the setting to trigger a scan.
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(2)
                .addBucketWithBand(10000,
                        WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN
                        | WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();
        long approxScanStartUs = mClock.getElapsedSinceBootMillis() * 1000;
        ArrayList<ScanDetail> rawScanResults = new ArrayList<>(Arrays.asList(
                ScanResults.generateNativeResults(0, 5150, 5171)));
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        InOrder order = inOrder(eventHandler, mWifiNative);
        // scan succeeds
        when(mWifiNative.scan(eq(IFACE_NAME), anyInt(), any(), any(List.class), anyBoolean(),
                any())).thenReturn(WifiScanner.REASON_SUCCEEDED);
        when(mWifiNative.getScanResults(eq(IFACE_NAME))).thenReturn(rawScanResults);

        int ap_count = rawScanResults.size();
        // The format of scan dump when the number of AP in the scan result is not zero.
        // ---------------------------------------------
        // "Latest native scan results:\n"    : Key word can't modify.
        // "    BSSID ...              \n"    : Must start with 4 spaces but only show when
        //                                      the number of AP in the scan result is not zero.
        // "  The APs information      \n"    : Must start with 2 spaces and show bssid first.
        // "  ...                      \n"    : Continues to print AP information and
        //                                      total AP information line should be
        //                                      same as the number of AP in the scan result.
        // "Latest native pno scan results:\n": Key word can't modify.
        // " ...                           \n": Any String and any line. No limited.
        // "Latest native scan results IEs:\n": Key word can't modify.
        // ie raw data                        : loop to start print ie raw data, finish with "\n"
        //   ...                              : Continues to print ie raw data and
        //                                      total ie raw data line should be same as
        //                                      the number of AP in the scan result.
        // "\n"                               : Should be stop with "\n"
        //---------------------------------------------
        Pattern scanResultRegex = Pattern.compile(
                "" + NATIVE_SCAN_TITLE + "\n"
                + "    .*\n"
                + "(  .{2}:.{2}:.{2}:.{2}:.{2}:.{2}.*\n){" + ap_count + "}"
                + "" + NATIVE_PNO_SCAN_TITLE + "\n"
                + "(.*\n)*"
                + "" + NATIVE_SCAN_IE_TITLE + "\n"
                + "(.*\n){" + ap_count + "}"
                + "\n");

        // Trigger a scan to update mNativeScanResults in WificondScannerImpl.
        assertTrue(mScanner.startSingleScan(settings, eventHandler));
        Set<Integer> expectedScan = expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ);
        order.verify(mWifiNative).scan(eq(IFACE_NAME), anyInt(), eq(expectedScan), any(List.class),
                anyBoolean(), any());

        // Notify scan has finished
        mWifiMonitor.sendMessage(eq(IFACE_NAME), WifiMonitor.SCAN_RESULTS_EVENT);
        mLooper.dispatchAll();
        // Get the dump string.
        String dump = dumpObject();

        assertLogContainsRequestPattern(scanResultRegex, dump);
    }

    /**
     * Verify that scan results for channels not being explicitly requested get filtered out, with
     * the exception of 6Ghz channels since they could be found via 6Ghz RNR.
     */
    @Test
    public void testUnwantedScanResultsExcept6GhzGetFiltered() {
        // Prepare the setting to trigger a scan that only explicitly target 2.4Ghz channels.
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(2)
                .addBucketWithBand(10000,
                        WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN
                                | WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();
        // Mock native scan results including 6155, which is a 6Ghz channel not explicitly being
        // scanned but found via RNR
        ArrayList<ScanDetail> rawScanResults = new ArrayList<>(Arrays.asList(
                ScanResults.generateNativeResults(0, 5150, 5171, 6155)));
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        InOrder order = inOrder(eventHandler, mWifiNative);
        // scan succeeds
        when(mWifiNative.scan(eq(IFACE_NAME), anyInt(), any(), any(List.class), anyBoolean(),
                any())).thenReturn(WifiScanner.REASON_SUCCEEDED);
        when(mWifiNative.getScanResults(eq(IFACE_NAME))).thenReturn(rawScanResults);

        // Trigger a scan to update mNativeScanResults in WificondScannerImpl.
        assertTrue(mScanner.startSingleScan(settings, eventHandler));
        Set<Integer> expectedScan = expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ);
        order.verify(mWifiNative).scan(eq(IFACE_NAME), anyInt(), eq(expectedScan), any(List.class),
                anyBoolean(), any());

        // Notify scan has finished
        mWifiMonitor.sendMessage(eq(IFACE_NAME), WifiMonitor.SCAN_RESULTS_EVENT);
        mLooper.dispatchAll();

        WifiScanner.ScanData scanData = mScanner.getLatestSingleScanResults();
        // Only the 6Ghz scan result should remain, since only the 2.4Ghz band is requested but the
        // other scan results are 5Ghz, so they get filtered out.
        assertEquals(1, scanData.getResults().length);
        assertEquals(6155, scanData.getResults()[0].frequency);
    }

    @Test
    public void cleanupDeregistersHandlers() {
        mScanner.cleanup();
        verify(mWifiMonitorSpy, times(1)).deregisterHandler(anyString(),
                eq(WifiMonitor.SCAN_FAILED_EVENT), any());
        verify(mWifiMonitorSpy, times(1)).deregisterHandler(anyString(),
                eq(WifiMonitor.PNO_SCAN_RESULTS_EVENT), any());
        verify(mWifiMonitorSpy, times(1)).deregisterHandler(anyString(),
                eq(WifiMonitor.SCAN_RESULTS_EVENT), any());
    }

    /**
     * Tests that for a large number of hidden networks, repeated scans cover the entire range of
     * hidden networks in round-robin order.
     */
    @Test
    public void testManyHiddenNetworksAcrossMultipleScans() {
        assumeTrue(SdkLevel.isAtLeastT());
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

        // First scan should cover the first MAX_NUM_SCAN_SSIDS hidden networks.
        ArrayList<String> hiddenNetworkSSIDSet = new ArrayList<>();
        for (int i = 0; i < MAX_NUM_SCAN_SSIDS; i++) {
            hiddenNetworkSSIDSet.add(hiddenNetworkSSIDs[i]);
        }
        doSuccessfulSingleScanTest(settings, createFreqSet(5650),
                hiddenNetworkSSIDSet,
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED,
                        5650, 5650, 5650, 5650, 5650, 5650, 5650), false, false, null);

        // The next scan should start from where we left off and wrap around to the
        // beginning of hiddenNetworkSSIDs.
        hiddenNetworkSSIDSet.clear();
        for (int i = MAX_NUM_SCAN_SSIDS; i < hiddenNetworkSSIDs.length; i++) {
            hiddenNetworkSSIDSet.add(hiddenNetworkSSIDs[i]);
        }
        int scanSetSize = hiddenNetworkSSIDSet.size();
        for (int i = 0; scanSetSize < MAX_NUM_SCAN_SSIDS; i++, scanSetSize++) {
            hiddenNetworkSSIDSet.add(hiddenNetworkSSIDs[i]);
        }
        doSuccessfulSingleScanTest(settings, createFreqSet(5650),
                hiddenNetworkSSIDSet,
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED,
                        5650, 5650, 5650, 5650, 5650, 5650, 5650), false, false, null);
    }

    /**
     * Tests that if the call to getMaxSsidsPerScan() fails, repeated scans contain the default
     * number of SSIDs per scan and do not cycle through the SSIDs in round-robin order.
     */
    @Test
    public void testFailureInGetMaxSsidsPerScan() {
        when(mWifiNative.getMaxSsidsPerScan(anyString())).thenReturn(-1);
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

        // If two successive scans occur, both should cover the first MAX_NUM_SCAN_SSIDS
        // hidden networks.
        ArrayList<String> hiddenNetworkSSIDSet = new ArrayList<>();
        for (int i = 0; i < MAX_NUM_SCAN_SSIDS; i++) {
            hiddenNetworkSSIDSet.add(hiddenNetworkSSIDs[i]);
        }
        doSuccessfulSingleScanTest(settings, createFreqSet(5650),
                hiddenNetworkSSIDSet,
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED,
                        5650, 5650, 5650, 5650, 5650, 5650, 5650), false, false, null);
        doSuccessfulSingleScanTest(settings, createFreqSet(5650),
                hiddenNetworkSSIDSet,
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED,
                        5650, 5650, 5650, 5650, 5650, 5650, 5650), false, false, null);
    }

    private void assertLogContainsRequestPattern(Pattern logLineRegex, String log) {
        assertTrue("dump did not contain log = " + logLineRegex.toString() + "\n" + log + "\n",
                logLineRegex.matcher(log).find());
    }

    private String dumpObject() {
        StringWriter stringWriter = new StringWriter();
        mScanner.dump(new FileDescriptor(), new PrintWriter(stringWriter),
                new String[0]);
        return stringWriter.toString();
    }
}
