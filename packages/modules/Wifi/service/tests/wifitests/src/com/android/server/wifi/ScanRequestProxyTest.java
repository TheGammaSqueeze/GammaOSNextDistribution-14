/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;

import static com.android.server.wifi.ScanRequestProxy.SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS;
import static com.android.server.wifi.ScanRequestProxy.SCAN_REQUEST_THROTTLE_TIME_WINDOW_FG_APPS_MS;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_SCAN_THROTTLE_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.IScanResultsCallback;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.ScanSettings.HiddenNetwork;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.os.test.TestLooper;
import android.text.TextUtils;
import android.util.ArrayMap;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.scanner.WifiScannerInternal;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.wifi.ScanRequestProxy}.
 */
@SmallTest
public class ScanRequestProxyTest extends WifiBaseTest {
    private static final int TEST_UID = 5;
    private static final String TEST_PACKAGE_NAME_1 = "com.test.1";
    private static final String TEST_PACKAGE_NAME_2 = "com.test.2";
    private static final String TEST_HIDDEN_NETWORK_MAC = "10:22:34:56:78:92";
    private static final String TEST_HIDDEN_NETWORK_SSID = "HideMe";
    private static final List<HiddenNetwork> TEST_HIDDEN_NETWORKS_LIST = List.of(
            new HiddenNetwork("test_ssid_1"),
            new HiddenNetwork("test_ssid_2"));
    private static final List<HiddenNetwork> TEST_HIDDEN_NETWORKS_LIST_NS = List.of(
            new HiddenNetwork("test_ssid_3"),
            new HiddenNetwork("test_ssid_4"));

    @Mock private Context mContext;
    @Mock private AppOpsManager mAppOps;
    @Mock private ActivityManager mActivityManager;
    @Mock private WifiInjector mWifiInjector;
    @Mock private WifiConfigManager mWifiConfigManager;
    @Mock private WifiScannerInternal mWifiScanner;
    @Mock private WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock private WifiMetrics mWifiMetrics;
    @Mock private WifiMetrics.ScanMetrics mScanMetrics;
    @Mock private Clock mClock;
    @Mock private WifiSettingsConfigStore mWifiSettingsConfigStore;
    @Mock private WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    @Mock private IScanResultsCallback mScanResultsCallback;
    @Mock private IScanResultsCallback mAnotherScanResultsCallback;
    @Mock private IBinder mBinder;
    @Mock private IBinder mAnotherBinder;

    private ArgumentCaptor<WifiScanner.ScanSettings> mScanSettingsArgumentCaptor =
            ArgumentCaptor.forClass(WifiScanner.ScanSettings.class);
    private ArgumentCaptor<WifiScannerInternal.ScanListener> mScanRequestListenerArgumentCaptor =
            ArgumentCaptor.forClass(WifiScannerInternal.ScanListener.class);
    private ArgumentCaptor<WifiScannerInternal.ScanListener> mGlobalScanListenerArgumentCaptor =
            ArgumentCaptor.forClass(WifiScannerInternal.ScanListener.class);
    private WifiScanner.ScanData[] mTestScanDatas1;
    private WifiScanner.ScanData[] mTestScanDatas2;
    private WifiScanner.ScanData[] mTestHiddenNetworkScanDatas;
    private InOrder mInOrder;
    private TestLooper mLooper;
    private ScanRequestProxy mScanRequestProxy;
    private MockResources mResources;

    private MockResources getMockResources() {
        MockResources resources = new MockResources();
        return resources;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();
        mResources = getMockResources();
        when(mContext.getResources()).thenReturn(mResources);
        WifiLocalServices.removeServiceForTest(WifiScannerInternal.class);
        WifiLocalServices.addService(WifiScannerInternal.class, mWifiScanner);
        when(mWifiInjector.getWifiNetworkSuggestionsManager())
                .thenReturn(mWifiNetworkSuggestionsManager);
        when(mWifiConfigManager.retrieveHiddenNetworkList(false /* autoJoinOnly */))
                .thenReturn(TEST_HIDDEN_NETWORKS_LIST);
        when(mWifiNetworkSuggestionsManager.retrieveHiddenNetworkList(false /* autoJoinOnly */))
                .thenReturn(TEST_HIDDEN_NETWORKS_LIST_NS);
        when(mWifiMetrics.getScanMetrics()).thenReturn(mScanMetrics);
        doNothing().when(mWifiScanner).registerScanListener(
                mGlobalScanListenerArgumentCaptor.capture());
        doNothing().when(mWifiScanner).startScan(
                mScanSettingsArgumentCaptor.capture(),
                mScanRequestListenerArgumentCaptor.capture(), any());

        mInOrder = inOrder(mWifiScanner, mWifiConfigManager,
                mContext, mWifiNetworkSuggestionsManager);
        mTestScanDatas1 =
                ScanTestUtil.createScanDatas(new int[][]{{ 2417, 2427, 5180, 5170, 58320, 60480 }},
                        new int[]{0},
                        new int[]{WifiScanner.WIFI_BAND_ALL});
        mTestScanDatas2 =
                ScanTestUtil.createScanDatas(new int[][]{{ 2412, 2422, 5200, 5210 }},
                        new int[]{0},
                        new int[]{WifiScanner.WIFI_BAND_ALL});

        // Scan throttling is enabled by default.
        when(mWifiSettingsConfigStore.get(eq(WIFI_SCAN_THROTTLE_ENABLED))).thenReturn(true);
        mScanRequestProxy =
            new ScanRequestProxy(mContext, mAppOps, mActivityManager, mWifiInjector,
                    mWifiConfigManager, mWifiPermissionsUtil, mWifiMetrics, mClock,
                    new Handler(mLooper.getLooper()), mWifiSettingsConfigStore);
        mScanRequestProxy.enableVerboseLogging(true);
        when(mScanResultsCallback.asBinder()).thenReturn(mBinder);
        when(mAnotherScanResultsCallback.asBinder()).thenReturn(mAnotherBinder);
    }

    @After
    public void cleanUp() throws Exception {
        verifyNoMoreInteractions(mWifiScanner, mWifiConfigManager, mWifiMetrics);
        validateMockitoUsage();
    }

    private void enableScanning() {
        // Enable scanning
        mScanRequestProxy.enableScanning(true, false);
        mInOrder.verify(mWifiScanner).registerScanListener(any());
        mInOrder.verify(mWifiScanner).setScanningEnabled(true);
        validateScanAvailableBroadcastSent(true);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(782L);
    }

    private void verifyScanMetricsDataWasSet() {
        verifyScanMetricsDataWasSet(1);
    }

    private void verifyScanMetricsDataWasSet(int times) {
        verifyScanMetricsDataWasSet(times, times);
    }

    private void verifyScanMetricsDataWasSet(int timesExternalRequest, int timesDataSet) {
        verify(mWifiMetrics,
                times(timesExternalRequest)).incrementExternalAppOneshotScanRequestsCount();
        verify(mWifiMetrics, times(timesDataSet * 2)).getScanMetrics();
        verify(mScanMetrics, times(timesDataSet)).setWorkSource(any());
        verify(mScanMetrics, times(timesDataSet)).setImportance(anyInt());
    }

    /**
     * Verify scan enable sequence.
     */
    @Test
    public void testEnableScanning() {
        mScanRequestProxy.enableScanning(true, false);
        mInOrder.verify(mWifiScanner).registerScanListener(any());
        mInOrder.verify(mWifiScanner).setScanningEnabled(true);
        validateScanAvailableBroadcastSent(true);
    }

    /**
     * Verify scan disable sequence.
     */
    @Test
    public void testDisableScanning() {
        mScanRequestProxy.enableScanning(false, false);
        mInOrder.verify(mWifiScanner).registerScanListener(any());
        mInOrder.verify(mWifiScanner).setScanningEnabled(false);
        validateScanAvailableBroadcastSent(false);
    }

    /**
     * Verify scan request will be rejected if we cannot get a handle to wifiscanner.
     */
    @Test
    public void testStartScanFailWithoutScanner() {
        when(mWifiInjector.getWifiScanner()).thenReturn(null);
        assertFalse(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        validateScanResultsFailureBroadcastSent(TEST_PACKAGE_NAME_1);
    }

    /**
     * Verify scan request will forwarded to wifiscanner if wifiscanner is present.
     */
    @Test
    public void testStartScanSuccess() {
        enableScanning();
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        validateScanSettings(mScanSettingsArgumentCaptor.getValue(), false);

        verifyScanMetricsDataWasSet();
    }

    /**
     * Verify scan request will forwarded to wifiscanner if wifiscanner is present.
     */
    @Test
    public void testStartScanSuccessFromAppWithNetworkSettings() {
        enableScanning();
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(TEST_UID)).thenReturn(true);
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        validateScanSettings(mScanSettingsArgumentCaptor.getValue(), false, true);
        verifyScanMetricsDataWasSet(0, 1);
        if (SdkLevel.isAtLeastS()) {
            assertFalse("6Ghz PSC should not enabled for scan requests from the settings app.",
                    mScanSettingsArgumentCaptor.getValue().is6GhzPscOnlyEnabled());
        }
    }

    /**
     * Verify scan request will forwarded to wifiscanner if wifiscanner is present.
     */
    @Test
    public void testStartScanSuccessFromAppWithNetworkSetupWizard() {
        enableScanning();
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(TEST_UID)).thenReturn(true);
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        validateScanSettings(mScanSettingsArgumentCaptor.getValue(), false, true);
        verifyScanMetricsDataWasSet(0, 1);
    }

    /**
     * Verify that hidden network list is not retrieved when hidden network scanning is disabled.
     */
    @Test
    public void testStartScanWithHiddenNetworkScanningDisabled() {
        mScanRequestProxy.enableScanning(true, false);
        mInOrder.verify(mWifiScanner).registerScanListener(any());
        mInOrder.verify(mWifiScanner).setScanningEnabled(true);
        validateScanAvailableBroadcastSent(true);

        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiConfigManager, never())
                .retrieveHiddenNetworkList(false /* autoJoinOnly */);
        mInOrder.verify(mWifiNetworkSuggestionsManager, never())
                .retrieveHiddenNetworkList(false /* autoJoinOnly */);
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        validateScanSettings(mScanSettingsArgumentCaptor.getValue(), false);

        verifyScanMetricsDataWasSet();
    }

    /**
     * Verify that hidden network list is retrieved when hidden network scanning is enabled.
     */
    @Test
    public void testStartScanWithHiddenNetworkScanningEnabled() {
        mScanRequestProxy.enableScanning(true, true);
        mInOrder.verify(mWifiScanner).registerScanListener(any());
        mInOrder.verify(mWifiScanner).setScanningEnabled(true);
        validateScanAvailableBroadcastSent(true);

        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));

        mInOrder.verify(mWifiConfigManager)
                .retrieveHiddenNetworkList(false /* autoJoinOnly */);
        mInOrder.verify(mWifiNetworkSuggestionsManager)
                .retrieveHiddenNetworkList(false /* autoJoinOnly */);
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        validateScanSettings(mScanSettingsArgumentCaptor.getValue(), true);

        verifyScanMetricsDataWasSet();
    }

    @Test
    public void testPartialScanIsCached() throws Exception {
        mScanRequestProxy.registerScanResultsCallback(mScanResultsCallback);
        // Make a scan request.
        testStartScanSuccess();

        // Verify scan requests cache is initially empty
        assertEquals(0, mScanRequestProxy.getScanResults().size());

        // Create scan data that only for 2.4 and 5 without DFS - not a full band scan.
        WifiScanner.ScanData[] partialScanData =
                ScanTestUtil.createScanDatas(new int[][]{{ 2417, 2427, 5180}},
                        new int[]{0},
                        new int[]{WifiScanner.WIFI_BAND_BOTH});

        // Verify scan result available is not sent but cache is updated
        mGlobalScanListenerArgumentCaptor.getValue().onResults(partialScanData);
        mLooper.dispatchAll();
        verify(mScanResultsCallback, never()).onScanResultsAvailable();
        assertEquals(3, mScanRequestProxy.getScanResults().size());
    }

    /**
     * Verify a successful scan request and processing of scan results.
     */
    @Test
    public void testScanSuccess() throws Exception {
        // Make a scan request.
        testStartScanSuccess();

        // Verify the scan results processing.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas1);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);

        // Validate the scan results in the cache.
        ScanTestUtil.assertScanResultsEqualsAnyOrder(
                mTestScanDatas1[0].getResults(),
                mScanRequestProxy.getScanResults().stream().toArray(ScanResult[]::new));
        ScanTestUtil.assertScanResultEquals(mTestScanDatas1[0].getResults()[0],
                mScanRequestProxy.getScanResult(mTestScanDatas1[0].getResults()[0].BSSID));

        verifyScanMetricsDataWasSet();
    }

    /**
     * Verify a successful scan request and processing of scan failure.
     */
    @Test
    public void testScanFailure() throws Exception {
        // Make a scan request.
        testStartScanSuccess();

        // Verify the scan failure processing.
        mScanRequestListenerArgumentCaptor.getValue().onFailure(0, "failed");
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(false);

        // Ensure scan results in the cache is empty.
        assertTrue(mScanRequestProxy.getScanResults().isEmpty());
        assertNull(mScanRequestProxy.getScanResult(mTestScanDatas1[0].getResults()[0].BSSID));

        verifyScanMetricsDataWasSet();
    }

    /**
     * Verify processing of successive successful scans.
     */
    @Test
    public void testScanSuccessOverwritesPreviousResults() throws Exception {
        enableScanning();
        // Make scan request 1.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        // Verify the scan results processing for request 1.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas1);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);
        // Validate the scan results in the cache.
        ScanTestUtil.assertScanResultsEqualsAnyOrder(
                mTestScanDatas1[0].getResults(),
                mScanRequestProxy.getScanResults().stream().toArray(ScanResult[]::new));
        ScanTestUtil.assertScanResultEquals(mTestScanDatas1[0].getResults()[0],
                mScanRequestProxy.getScanResult(mTestScanDatas1[0].getResults()[0].BSSID));

        // Make scan request 2.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_2));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        // Verify the scan results processing for request 2.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas2);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);
        // Validate the scan results in the cache.
        ScanTestUtil.assertScanResultsEqualsAnyOrder(
                mTestScanDatas2[0].getResults(),
                mScanRequestProxy.getScanResults().stream().toArray(ScanResult[]::new));
        ScanTestUtil.assertScanResultEquals(mTestScanDatas2[0].getResults()[0],
                mScanRequestProxy.getScanResult(mTestScanDatas2[0].getResults()[0].BSSID));

        verifyScanMetricsDataWasSet(2);
    }

    /**
     * Verify processing of a successful scan followed by a failure.
     */
    @Test
    public void testScanFailureDoesNotOverwritePreviousResults() throws Exception {
        enableScanning();
        // Make scan request 1.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        // Verify the scan results processing for request 1.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas1);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);
        // Validate the scan results in the cache.
        ScanTestUtil.assertScanResultsEqualsAnyOrder(
                mTestScanDatas1[0].getResults(),
                mScanRequestProxy.getScanResults().stream().toArray(ScanResult[]::new));
        ScanTestUtil.assertScanResultEquals(mTestScanDatas1[0].getResults()[0],
                mScanRequestProxy.getScanResult(mTestScanDatas1[0].getResults()[0].BSSID));

        // Make scan request 2.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_2));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        // Verify the scan failure processing.
        mScanRequestListenerArgumentCaptor.getValue().onFailure(0, "failed");
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(false);
        // Validate the scan results from a previous successful scan in the cache.
        ScanTestUtil.assertScanResultsEqualsAnyOrder(
                mTestScanDatas1[0].getResults(),
                mScanRequestProxy.getScanResults().stream().toArray(ScanResult[]::new));
        ScanTestUtil.assertScanResultEquals(mTestScanDatas1[0].getResults()[0],
                mScanRequestProxy.getScanResult(mTestScanDatas1[0].getResults()[0].BSSID));

        verifyScanMetricsDataWasSet(2);
    }

    /**
     * Verify processing of a new scan request after a previous scan success.
     * Verify that we send out two broadcasts (two successes).
     */
    @Test
    public void testNewScanRequestAfterPreviousScanSucceeds() throws Exception {
        enableScanning();
        // Make scan request 1.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        // Now send the scan results for request 1.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas1);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);
        // Validate the scan results in the cache.
        ScanTestUtil.assertScanResultsEqualsAnyOrder(
                mTestScanDatas1[0].getResults(),
                mScanRequestProxy.getScanResults().stream().toArray(ScanResult[]::new));
        ScanTestUtil.assertScanResultEquals(mTestScanDatas1[0].getResults()[0],
                mScanRequestProxy.getScanResult(mTestScanDatas1[0].getResults()[0].BSSID));

        // Make scan request 2.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_2));
        // Ensure that we did send a second scan request to scanner.
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        // Now send the scan results for request 2.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas2);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);
        // Validate the scan results in the cache.
        ScanTestUtil.assertScanResultsEqualsAnyOrder(
                mTestScanDatas2[0].getResults(),
                mScanRequestProxy.getScanResults().stream().toArray(ScanResult[]::new));
        ScanTestUtil.assertScanResultEquals(mTestScanDatas2[0].getResults()[0],
                mScanRequestProxy.getScanResult(mTestScanDatas2[0].getResults()[0].BSSID));

        verifyScanMetricsDataWasSet(2);
    }

    /**
     * Verify processing of a new scan request after a previous scan success, but with bad scan
     * data.
     * Verify that we send out two broadcasts (one failure & one success).
     */
    @Test
    public void testNewScanRequestAfterPreviousScanSucceedsWithInvalidScanDatas() throws Exception {
        enableScanning();
        // Make scan request 1.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        // Now send scan success for request 1, but with invalid scan datas.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(
                new WifiScanner.ScanData[] {mTestScanDatas1[0], mTestScanDatas2[0]});
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(false);
        // Validate the scan results in the cache.
        assertTrue(mScanRequestProxy.getScanResults().isEmpty());
        assertNull(mScanRequestProxy.getScanResult(mTestScanDatas1[0].getResults()[0].BSSID));

        // Make scan request 2.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_2));
        // Ensure that we did send a second scan request to scanner.
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        // Now send the scan results for request 2.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas2);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);
        // Validate the scan results in the cache.
        ScanTestUtil.assertScanResultsEqualsAnyOrder(
                mTestScanDatas2[0].getResults(),
                mScanRequestProxy.getScanResults().stream().toArray(ScanResult[]::new));
        ScanTestUtil.assertScanResultEquals(mTestScanDatas2[0].getResults()[0],
                mScanRequestProxy.getScanResult(mTestScanDatas2[0].getResults()[0].BSSID));

        verifyScanMetricsDataWasSet(2);
    }


    /**
     * Verify processing of a new scan request after a previous scan failure.
     * Verify that we send out two broadcasts (one failure & one success).
     */
    @Test
    public void testNewScanRequestAfterPreviousScanFailure() throws Exception {
        enableScanning();
        // Make scan request 1.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        // Now send scan failure for request 1.
        mScanRequestListenerArgumentCaptor.getValue().onFailure(0, "failed");
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(false);
        // Validate the scan results in the cache.
        assertTrue(mScanRequestProxy.getScanResults().isEmpty());
        assertNull(mScanRequestProxy.getScanResult(mTestScanDatas1[0].getResults()[0].BSSID));

        // Make scan request 2.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_2));
        // Ensure that we did send a second scan request to scanner.
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        // Now send the scan results for request 2.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas2);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);
        // Validate the scan results in the cache.
        ScanTestUtil.assertScanResultsEqualsAnyOrder(
                mTestScanDatas2[0].getResults(),
                mScanRequestProxy.getScanResults().stream().toArray(ScanResult[]::new));
        ScanTestUtil.assertScanResultEquals(mTestScanDatas2[0].getResults()[0],
                mScanRequestProxy.getScanResult(mTestScanDatas2[0].getResults()[0].BSSID));

        verifyScanMetricsDataWasSet(2);
    }

    /**
     * Verify that we clear scan results when scan state is toggled.
     */
    @Test
    public void testToggleScanStateClearsScanResults() throws Exception {
        // Enable scanning
        mScanRequestProxy.enableScanning(true, false);
        mInOrder.verify(mWifiScanner).registerScanListener(any());
        mInOrder.verify(mWifiScanner).setScanningEnabled(true);
        validateScanAvailableBroadcastSent(true);

        // Make scan request 1.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        // Verify the scan results processing for request 1.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas1);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);
        // Validate the scan results in the cache.
        ScanTestUtil.assertScanResultsEqualsAnyOrder(
                mTestScanDatas1[0].getResults(),
                mScanRequestProxy.getScanResults().stream().toArray(ScanResult[]::new));
        ScanTestUtil.assertScanResultEquals(mTestScanDatas1[0].getResults()[0],
                mScanRequestProxy.getScanResult(mTestScanDatas1[0].getResults()[0].BSSID));

        // Enable scanning again (a new iface was added/removed).
        mScanRequestProxy.enableScanning(true, false);
        mInOrder.verify(mWifiScanner).setScanningEnabled(true);
        validateScanAvailableBroadcastSent(true);
        // Validate the scan results in the cache (should not be cleared).
        ScanTestUtil.assertScanResultsEqualsAnyOrder(
                mTestScanDatas1[0].getResults(),
                mScanRequestProxy.getScanResults().stream().toArray(ScanResult[]::new));
        ScanTestUtil.assertScanResultEquals(mTestScanDatas1[0].getResults()[0],
                mScanRequestProxy.getScanResult(mTestScanDatas1[0].getResults()[0].BSSID));

        // Disable scanning
        mScanRequestProxy.enableScanning(false, false);
        verify(mWifiScanner).setScanningEnabled(false);
        validateScanAvailableBroadcastSent(false);

        // Validate the scan results in the cache (should be cleared).
        assertTrue(mScanRequestProxy.getScanResults().isEmpty());
        assertNull(mScanRequestProxy.getScanResult(mTestScanDatas1[0].getResults()[0].BSSID));

        verifyScanMetricsDataWasSet();
    }

    /**
     * Verify that we don't use the same listener for multiple scan requests.
     */
    @Test
    public void testSuccessiveScanRequestsDontUseSameListener() {
        enableScanning();
        WifiScannerInternal.ScanListener listener1;
        WifiScannerInternal.ScanListener listener2;
        // Make scan request 1.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        listener1 = mScanRequestListenerArgumentCaptor.getValue();

        // Make scan request 2.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_2));
        // Ensure that we did send a second scan request to scanner.
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        listener2 = mScanRequestListenerArgumentCaptor.getValue();

        assertNotEquals(listener1, listener2);

        verifyScanMetricsDataWasSet(2);
    }

    /**
     * Verify that a scan request from a App in the foreground scanning throttle exception list
     * is not throttled.
     */
    @Test
    public void testForegroundScanForPackageInExceptionListNotThrottled() {
        enableScanning();
        long firstRequestMs = 782;
        mResources.setStringArray(R.array.config_wifiForegroundScanThrottleExceptionList,
                new String[]{TEST_PACKAGE_NAME_1});
        when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs);
        for (int i = 0; i < SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS; i++) {
            when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs + i);
            assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
            mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        }
        // Make next scan request from the same package name & ensure that it is not throttled.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        verifyScanMetricsDataWasSet(SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS + 1);
        if (SdkLevel.isAtLeastS()) {
            assertTrue("6Ghz PSC should be enabled for scan requests from normal apps.",
                    mScanSettingsArgumentCaptor.getValue().is6GhzPscOnlyEnabled());
        }
    }

    /**
     * Ensure new scan requests from the same app are rejected if there are more than
     * {@link ScanRequestProxy#SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS} requests in
     * {@link ScanRequestProxy#SCAN_REQUEST_THROTTLE_TIME_WINDOW_FG_APPS_MS}
     */
    @Test
    public void testSuccessiveScanRequestFromSameFgAppThrottled() {
        enableScanning();
        long firstRequestMs = 782;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs);
        for (int i = 0; i < SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS; i++) {
            when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs + i);
            assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
            mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        }
        // Make next scan request from the same package name & ensure that it is throttled.
        assertFalse(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        validateScanResultsFailureBroadcastSent(TEST_PACKAGE_NAME_1);

        verify(mWifiMetrics).incrementExternalForegroundAppOneshotScanRequestsThrottledCount();
        verifyScanMetricsDataWasSet(SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS + 1,
                SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS);
    }

    /**
     * Ensure new scan requests from the same app are rejected if there are more than
     * {@link ScanRequestProxy#SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS} requests after
     * {@link ScanRequestProxy#SCAN_REQUEST_THROTTLE_TIME_WINDOW_FG_APPS_MS}
     */
    @Test
    public void testSuccessiveScanRequestFromSameFgAppNotThrottled() {
        enableScanning();
        long firstRequestMs = 782;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs);
        for (int i = 0; i < SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS; i++) {
            when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs + i);
            assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
            mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        }
        long lastRequestMs = firstRequestMs + SCAN_REQUEST_THROTTLE_TIME_WINDOW_FG_APPS_MS + 1;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(lastRequestMs);
        // Make next scan request from the same package name & ensure that it is not throttled.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        verifyScanMetricsDataWasSet(SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS + 1);
    }

    /**
     * Ensure new scan requests from the same app with NETWORK_SETTINGS permission are not
     * throttled.
     */
    @Test
    public void testSuccessiveScanRequestFromSameAppWithNetworkSettingsPermissionNotThrottled() {
        enableScanning();
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(TEST_UID)).thenReturn(true);

        long firstRequestMs = 782;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs);
        for (int i = 0; i < SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS; i++) {
            when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs + i);
            assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
            mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        }
        // Make next scan request from the same package name & ensure that it is not throttled.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        verifyScanMetricsDataWasSet(0, SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS + 1);
    }

    /**
     * Ensure new scan requests from the same app with NETWORK_SETUP_WIZARD permission are not
     * throttled.
     */
    @Test
    public void testSuccessiveScanRequestFromSameAppWithNetworkSetupWizardPermissionNotThrottled() {
        enableScanning();
        when(mWifiPermissionsUtil.checkNetworkSetupWizardPermission(TEST_UID)).thenReturn(true);

        long firstRequestMs = 782;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs);
        for (int i = 0; i < SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS; i++) {
            when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs + i);
            assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
            mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        }
        // Make next scan request from the same package name & ensure that it is not throttled.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        verifyScanMetricsDataWasSet(0, SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS + 1);
    }

    /**
     * Ensure new scan requests from the same app are not throttled when the user turns
     * off scan throttling.
     */
    @Test
    public void testSuccessiveScanRequestFromSameAppWhenThrottlingIsDisabledNotThrottled() {
        // Triggers the scan throttle setting registration.
        testEnableScanning();
        mScanRequestProxy.setScanThrottleEnabled(false);
        verify(mWifiSettingsConfigStore).put(WIFI_SCAN_THROTTLE_ENABLED, false);

        long firstRequestMs = 782;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs);
        for (int i = 0; i < SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS; i++) {
            when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs + i);
            assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
            mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        }
        // Make next scan request from the same package name & ensure that it is not throttled.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        verifyScanMetricsDataWasSet(0, SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS + 1);
    }

    /**
     * Ensure new scan requests from different apps are not throttled.
     */
    @Test
    public void testSuccessiveScanRequestFromDifferentFgAppsNotThrottled() {
        enableScanning();
        long firstRequestMs = 782;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs);
        for (int i = 0; i < SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS / 2; i++) {
            when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs + i);
            assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
            mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        }
        for (int i = 0; i < SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS / 2; i++) {
            when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs + i);
            assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_2));
            mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        }
        // Make next scan request from both the package name & ensure that it is not throttled.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_2));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        verifyScanMetricsDataWasSet(SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS + 2);
    }

    /**
     * Ensure new scan requests from the same app after removal & re-install is not
     * throttled.
     * Verifies that we clear the scan timestamps for apps that were removed.
     */
    @Test
    public void testSuccessiveScanRequestFromSameAppAfterRemovalAndReinstallNotThrottled() {
        enableScanning();
        long firstRequestMs = 782;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs);
        for (int i = 0; i < SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS; i++) {
            when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs + i);
            assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
            mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        }
        // Now simulate removing the app.
        mScanRequestProxy.clearScanRequestTimestampsForApp(TEST_PACKAGE_NAME_1, TEST_UID);

        // Make next scan request from the same package name (simulating a reinstall) & ensure that
        // it is not throttled.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        verifyScanMetricsDataWasSet(SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS + 1);
    }

    /**
     * Ensure new scan requests after removal of the app from a different user is throttled.
     * The app has same the package name across users, but different UID's. Verifies that
     * the cache is cleared only for the specific app for a specific user when an app is removed.
     */
    @Test
    public void testSuccessiveScanRequestFromSameAppAfterRemovalOnAnotherUserThrottled() {
        enableScanning();
        long firstRequestMs = 782;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs);
        for (int i = 0; i < SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS; i++) {
            when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs + i);
            assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
            mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        }
        // Now simulate removing the app for another user (User 1).
        mScanRequestProxy.clearScanRequestTimestampsForApp(
                TEST_PACKAGE_NAME_1,
                UserHandle.getUid(UserHandle.USER_SYSTEM + 1, TEST_UID));

        // Make next scan request from the same package name & ensure that is throttled.
        assertFalse(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        validateScanResultsFailureBroadcastSent(TEST_PACKAGE_NAME_1);

        verify(mWifiMetrics, times(SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS + 1))
                .incrementExternalAppOneshotScanRequestsCount();
        verify(mWifiMetrics).incrementExternalForegroundAppOneshotScanRequestsThrottledCount();
        verify(mWifiMetrics,
                times(SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS * 2)).getScanMetrics();
        verify(mScanMetrics, times(SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS)).setWorkSource(
                any());
        verify(mScanMetrics, times(SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS)).setImportance(
                anyInt());
    }

    /**
     * Verify that a scan request from a App in the background scanning throttle exception list
     * is not throttled.
     */
    @Test
    public void testBackgroundScanForPackageInExceptionListNotThrottled() {
        enableScanning();
        mResources.setStringArray(R.array.config_wifiForegroundScanThrottleExceptionList,
                new String[]{TEST_PACKAGE_NAME_2});
        when(mActivityManager.getPackageImportance(TEST_PACKAGE_NAME_1))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND + 1);
        when(mActivityManager.getPackageImportance(TEST_PACKAGE_NAME_2))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND + 1);

        long firstRequestMs = 782;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs);
        // Make scan request 1.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        // Make scan request 2 from the different package name & ensure that it is not throttled.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_2));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        verifyScanMetricsDataWasSet(2);
    }

    /**
     * Ensure scan requests from different background apps are throttled if it's before
     * {@link ScanRequestProxy#SCAN_REQUEST_THROTTLE_INTERVAL_BG_APPS_MS}.
     */
    @Test
    public void testSuccessiveScanRequestFromBgAppsThrottled() {
        enableScanning();
        when(mActivityManager.getPackageImportance(TEST_PACKAGE_NAME_1))
                .thenReturn(IMPORTANCE_FOREGROUND_SERVICE + 1);
        when(mActivityManager.getPackageImportance(TEST_PACKAGE_NAME_2))
                .thenReturn(IMPORTANCE_FOREGROUND_SERVICE + 1);

        long firstRequestMs = 782;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs);
        // Make scan request 1.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        // Make scan request 2 from the different package name & ensure that it is throttled.
        assertFalse(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_2));
        validateScanResultsFailureBroadcastSent(TEST_PACKAGE_NAME_2);

        verify(mWifiMetrics).incrementExternalBackgroundAppOneshotScanRequestsThrottledCount();
        verifyScanMetricsDataWasSet(2, 1);
    }

    /**
     * Ensure scan requests from different background apps are not throttled if it's after
     * {@link ScanRequestProxy#SCAN_REQUEST_THROTTLE_INTERVAL_BG_APPS_MS}.
     */
    @Test
    public void testSuccessiveScanRequestFromBgAppsNotThrottled() {
        enableScanning();
        when(mActivityManager.getPackageImportance(TEST_PACKAGE_NAME_1))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND + 1);
        when(mActivityManager.getPackageImportance(TEST_PACKAGE_NAME_2))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND + 1);

        long firstRequestMs = 782;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(firstRequestMs);
        // Make scan request 1.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        long secondRequestMs =
                firstRequestMs + ScanRequestProxy.SCAN_REQUEST_THROTTLE_INTERVAL_BG_APPS_MS + 1;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(secondRequestMs);
        // Make scan request 2 from the different package name & ensure that it is throttled.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_2));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());

        verifyScanMetricsDataWasSet(2);
    }

    /**
     * Verify processing of a successful scan followed by full scan results from
     * internal scans.
     */
    @Test
    public void testFullInternalScanResultsOverwritesPreviousResults() throws Exception {
        enableScanning();
        // Make scan request 1.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        // Verify the scan results processing for request 1.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas1);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);
        // Validate the scan results in the cache.
        ScanTestUtil.assertScanResultsEqualsAnyOrder(
                mTestScanDatas1[0].getResults(),
                mScanRequestProxy.getScanResults().stream().toArray(ScanResult[]::new));

        // Now send results from an internal full scan request.
        // Verify the scan failure processing.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas2);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);
        // Validate the scan results in the cache.
        ScanTestUtil.assertScanResultsEqualsAnyOrder(
                mTestScanDatas2[0].getResults(),
                mScanRequestProxy.getScanResults().stream().toArray(ScanResult[]::new));

        verifyScanMetricsDataWasSet();
    }

    /**
     * Verify processing of a successful scan followed by partial scan results from
     * internal scans.
     */
    @Test
    public void testPartialInternalScanResultsAppendToPreviousResults() throws Exception {
        enableScanning();
        // Make scan request 1.
        assertTrue(mScanRequestProxy.startScan(TEST_UID, TEST_PACKAGE_NAME_1));
        mInOrder.verify(mWifiScanner).startScan(any(), any(), any());
        // Verify the scan results processing for request 1.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas1);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);
        // Validate the scan results in the cache.
        int scanResultsSize = mScanRequestProxy.getScanResults().size();
        ScanTestUtil.assertScanResultsEqualsAnyOrder(
                mTestScanDatas1[0].getResults(),
                mScanRequestProxy.getScanResults().stream().toArray(ScanResult[]::new));

        // Now send results from an internal partial scan request.
        mTestScanDatas2 = ScanTestUtil.createScanDatas(new int[][]{{ 2412, 2422 }},
                new int[]{0},
                new int[]{WifiScanner.WIFI_BAND_24_GHZ});
        // Verify the scan failure processing.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas2);
        mLooper.dispatchAll();
        // Validate the scan results get appended
        assertTrue(mScanRequestProxy.getScanResults().size() > scanResultsSize);

        verifyScanMetricsDataWasSet();
    }

    private void validateScanSettings(WifiScanner.ScanSettings scanSettings,
                                      boolean expectHiddenNetworks) {
        validateScanSettings(scanSettings, expectHiddenNetworks, false);
    }

    private void validateScanSettings(WifiScanner.ScanSettings scanSettings,
                                      boolean expectHiddenNetworks,
                                      boolean expectHighAccuracyType) {
        assertNotNull(scanSettings);
        assertEquals(WifiScanner.WIFI_BAND_ALL, scanSettings.band);
        if (expectHighAccuracyType) {
            assertEquals(WifiScanner.SCAN_TYPE_HIGH_ACCURACY, scanSettings.type);
        } else {
            assertEquals(WifiScanner.SCAN_TYPE_LOW_LATENCY, scanSettings.type);
        }
        assertEquals(WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN
                | WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT, scanSettings.reportEvents);
        List<HiddenNetwork> hiddenNetworkList =
                new ArrayList<>();
        hiddenNetworkList.addAll(TEST_HIDDEN_NETWORKS_LIST);
        hiddenNetworkList.addAll(TEST_HIDDEN_NETWORKS_LIST_NS);
        if (expectHiddenNetworks) {
            assertEquals(hiddenNetworkList.size(), scanSettings.hiddenNetworks.size());
            for (HiddenNetwork hiddenNetwork : scanSettings.hiddenNetworks) {
                validateHiddenNetworkInList(hiddenNetwork, hiddenNetworkList);
            }
        } else {
            assertEquals(Collections.emptyList(), scanSettings.hiddenNetworks);
        }
    }

    private void validateHiddenNetworkInList(
            HiddenNetwork expectedHiddenNetwork,
            List<HiddenNetwork> hiddenNetworkList) {
        for (HiddenNetwork hiddenNetwork : hiddenNetworkList) {
            if (hiddenNetwork.ssid.equals(expectedHiddenNetwork.ssid)) {
                return;
            }
        }
        fail();
    }

    private void validateScanResultsAvailableBroadcastSent(boolean expectScanSuceeded) {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<UserHandle> userHandleCaptor = ArgumentCaptor.forClass(UserHandle.class);
        ArgumentCaptor<Bundle> broadcastOptionsCaptor = ArgumentCaptor.forClass(Bundle.class);
        mInOrder.verify(mContext).sendBroadcastAsUser(
                intentCaptor.capture(), userHandleCaptor.capture(), isNull(),
                broadcastOptionsCaptor.capture());

        assertEquals(userHandleCaptor.getValue(), UserHandle.ALL);

        Intent intent = intentCaptor.getValue();
        assertEquals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION, intent.getAction());
        assertEquals(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT, intent.getFlags());
        boolean scanSucceeded = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
        assertEquals(expectScanSuceeded, scanSucceeded);

        verifyScanAvailableBroadcastOptions(broadcastOptionsCaptor.getValue(), scanSucceeded);
    }

    private void validateScanResultsFailureBroadcastSent(String expectedPackageName) {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<UserHandle> userHandleCaptor = ArgumentCaptor.forClass(UserHandle.class);
        ArgumentCaptor<Bundle> broadcastOptionsCaptor = ArgumentCaptor.forClass(Bundle.class);
        mInOrder.verify(mContext).sendBroadcastAsUser(
                intentCaptor.capture(), userHandleCaptor.capture(), isNull(),
                broadcastOptionsCaptor.capture());

        assertEquals(userHandleCaptor.getValue(), UserHandle.ALL);

        Intent intent = intentCaptor.getValue();
        assertEquals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION, intent.getAction());
        assertEquals(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT, intent.getFlags());
        boolean scanSucceeded = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
        assertFalse(scanSucceeded);
        String packageName = intent.getPackage();
        assertEquals(expectedPackageName, packageName);

        verifyScanAvailableBroadcastOptions(broadcastOptionsCaptor.getValue(), false);
    }

    private void verifyScanAvailableBroadcastOptions(Bundle actualBroadcastOptions,
            boolean actualScansucceeded) {
        if (SdkLevel.isAtLeastU()) {
            ArrayMap<String, Object> actualOptions = asMap(actualBroadcastOptions);
            ArrayMap<String, Object> expectedOptions = asMap(ScanRequestProxy
                    .createBroadcastOptionsForScanResultsAvailable(actualScansucceeded));
            assertNotNull(expectedOptions);
            assertEquals(expectedOptions, actualOptions);
        }
    }

    private static ArrayMap<String, Object> asMap(Bundle bundle) {
        final ArrayMap<String, Object> map = new ArrayMap<>();
        if (bundle != null) {
            for (String key : bundle.keySet()) {
                map.put(key, bundle.get(key));
            }
        }
        return map;
    }

    private void validateScanAvailableBroadcastSent(boolean expectedScanAvailable) {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<UserHandle> userHandleCaptor = ArgumentCaptor.forClass(UserHandle.class);
        mInOrder.verify(mContext).sendStickyBroadcastAsUser(
                intentCaptor.capture(), userHandleCaptor.capture());

        assertEquals(userHandleCaptor.getValue(), UserHandle.ALL);

        Intent intent = intentCaptor.getValue();
        assertEquals(WifiManager.ACTION_WIFI_SCAN_AVAILABILITY_CHANGED, intent.getAction());
        assertEquals(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT, intent.getFlags());
        boolean scanAvailable = intent.getBooleanExtra(WifiManager.EXTRA_SCAN_AVAILABLE, false);
        assertEquals(expectedScanAvailable, scanAvailable);
    }

    /**
     * Create a test scan data for a hidden network with two scan results(BSS), one with SSID and
     * another one without SSID (Native layer may give two scan results for the same BSS
     * when a hidden network is configured)
     * @param isHiddenFirst if true, place the scan result with empty SSID first in the list
     */
    private void setupScanDataWithHiddenNetwork(boolean isHiddenFirst) {
        mTestHiddenNetworkScanDatas = ScanTestUtil.createScanDatas(new int[][]{{2412, 2412}},
                new int[]{0},
                new int[]{WifiScanner.WIFI_BAND_ALL});
        assertEquals(1, mTestHiddenNetworkScanDatas.length);
        ScanResult[] scanResults = mTestHiddenNetworkScanDatas[0].getResults();
        assertEquals(2, scanResults.length);
        scanResults[0].BSSID = TEST_HIDDEN_NETWORK_MAC;
        scanResults[1].BSSID = TEST_HIDDEN_NETWORK_MAC;
        if (isHiddenFirst) {
            scanResults[0].SSID = "";
            scanResults[1].SSID = TEST_HIDDEN_NETWORK_SSID;
        } else {
            scanResults[0].SSID = TEST_HIDDEN_NETWORK_SSID;
            scanResults[1].SSID = "";
        }
    }

    /**
     * Test register two different scan result Callback, all of them will receive the event.
     */
    @Test
    public void testScanSuccessWithMultipleCallback() throws Exception {
        mScanRequestProxy.registerScanResultsCallback(mScanResultsCallback);
        mScanRequestProxy.registerScanResultsCallback(mAnotherScanResultsCallback);
        testStartScanSuccess();
        // Verify the scan results processing.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas1);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);

        mScanRequestProxy.unregisterScanResultsCallback(mScanResultsCallback);
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas2);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);
        verify(mScanResultsCallback).onScanResultsAvailable();
        verify(mAnotherScanResultsCallback, times(2)).onScanResultsAvailable();
    }

    /**
     * Verify that registering twice with same Callback will replace the first Callback.
     */
    @Test
    public void testReplacesOldListenerWithNewCallbackWhenRegisteringTwice() throws Exception {
        mScanRequestProxy.registerScanResultsCallback(mScanResultsCallback);
        mScanRequestProxy.registerScanResultsCallback(mScanResultsCallback);
        mLooper.dispatchAll();
        // Verify old listener is replaced.
        verify(mBinder, times(2)).linkToDeath(any(), anyInt());
        verify(mBinder).unlinkToDeath(any(), anyInt());
        testStartScanSuccess();
        // Verify the scan results processing.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas1);
        mLooper.dispatchAll();
        verify(mScanResultsCallback).onScanResultsAvailable();
        validateScanResultsAvailableBroadcastSent(true);
    }

    /**
     * Test registered scan result Callback will be unregistered when calling binder is died.
     */
    @Test
    public void testUnregisterScanResultCallbackOnBinderDied() throws Exception {
        ArgumentCaptor<IBinder.DeathRecipient> drCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        mScanRequestProxy.registerScanResultsCallback(mScanResultsCallback);
        verify(mBinder).linkToDeath(drCaptor.capture(), anyInt());
        testStartScanSuccess();
        // Verify the scan results processing.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas1);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);
        verify(mScanResultsCallback).onScanResultsAvailable();
        drCaptor.getValue().binderDied();
        mLooper.dispatchAll();
        reset(mScanResultsCallback);
        // Verify the scan results processing.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestScanDatas1);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);
        verify(mScanResultsCallback, never()).onScanResultsAvailable();
    }

    /** Test that modifying the returned scan results list does not change the original. */
    @Test
    public void testGetScanResults_modifyReturnedList_doesNotChangeOriginal() {
        // initialize scan results
        testStartScanSuccess();

        List<ScanResult> scanResults = mScanRequestProxy.getScanResults();
        int scanResultsOriginalSize = scanResults.size();

        scanResults.add(new ScanResult());

        assertThat(mScanRequestProxy.getScanResults()).hasSize(scanResultsOriginalSize);
    }

    /** Test that getScanResults() always returns the hidden network result with SSID */
    @Test
    public void testGetScanResults_HiddenNetwork_ReturnsSsidScanresult() {
        // Make a scan request.
        testStartScanSuccess();

        // Prepare scan results with empty SSID first in the list
        setupScanDataWithHiddenNetwork(true);

        // Verify the scan results processing.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestHiddenNetworkScanDatas);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);

        // Validate the scan results in the cache.
        List<ScanResult> scanResultsList = mScanRequestProxy.getScanResults();
        assertEquals(1, scanResultsList.size());
        assertTrue(TextUtils.equals(TEST_HIDDEN_NETWORK_SSID, scanResultsList.get(0).SSID));

        // Prepare scan results with SSID first in the list
        setupScanDataWithHiddenNetwork(false);

        // Verify the scan results processing.
        mGlobalScanListenerArgumentCaptor.getValue().onResults(mTestHiddenNetworkScanDatas);
        mLooper.dispatchAll();
        validateScanResultsAvailableBroadcastSent(true);

        // Validate the scan results in the cache.
        scanResultsList = mScanRequestProxy.getScanResults();
        assertEquals(1, scanResultsList.size());
        assertTrue(TextUtils.equals(TEST_HIDDEN_NETWORK_SSID, scanResultsList.get(0).SSID));

    }
}
