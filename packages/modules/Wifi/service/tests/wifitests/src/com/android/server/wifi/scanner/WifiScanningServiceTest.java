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

package com.android.server.wifi.scanner;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.wifi.WifiScanner.GET_AVAILABLE_CHANNELS_EXTRA;

import static com.android.server.wifi.ScanTestUtil.NativeScanSettingsBuilder;
import static com.android.server.wifi.ScanTestUtil.assertNativePnoSettingsEquals;
import static com.android.server.wifi.ScanTestUtil.assertNativeScanSettingsEquals;
import static com.android.server.wifi.ScanTestUtil.assertScanDatasEquals;
import static com.android.server.wifi.ScanTestUtil.assertScanResultsEquals;
import static com.android.server.wifi.ScanTestUtil.channelsToSpec;
import static com.android.server.wifi.ScanTestUtil.computeSingleScanNativeSettings;
import static com.android.server.wifi.ScanTestUtil.computeSingleScanNativeSettingsWithChannelHelper;
import static com.android.server.wifi.ScanTestUtil.createRequest;
import static com.android.server.wifi.ScanTestUtil.createSingleScanNativeSettingsForChannels;
import static com.android.server.wifi.scanner.WifiScanningServiceImpl.WifiPnoScanStateMachine.SwPnoScanState.SW_PNO_ALARM_INTENT_ACTION;
import static com.android.server.wifi.scanner.WifiScanningServiceImpl.WifiPnoScanStateMachine.SwPnoScanState.SW_PNO_UPPER_BOUND_ALARM_INTENT_ACTION;
import static com.android.server.wifi.scanner.WifiScanningServiceImpl.WifiSingleScanStateMachine.CACHED_SCAN_RESULTS_MAX_AGE_IN_MILLIS;
import static com.android.server.wifi.scanner.WifiScanningServiceImpl.WifiSingleScanStateMachine.EMERGENCY_SCAN_END_INDICATION_ALARM_TAG;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.app.test.TestAlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.IWifiScannerListener;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.os.BatteryStatsManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.WorkSource;
import android.os.test.TestLooper;
import android.util.ArraySet;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.Clock;
import com.android.server.wifi.DeviceConfigFacade;
import com.android.server.wifi.FakeWifiLog;
import com.android.server.wifi.FrameworkFacade;
import com.android.server.wifi.MockResources;
import com.android.server.wifi.ScanResults;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiGlobals;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiLocalServices;
import com.android.server.wifi.WifiMetrics;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.proto.nano.WifiMetricsProto;
import com.android.server.wifi.util.LastCallerInfoManager;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Unit tests for {@link com.android.server.wifi.scanner.WifiScanningServiceImpl}.
 */
@SmallTest
public class WifiScanningServiceTest extends WifiBaseTest {
    public static final String TAG = "WifiScanningServiceTest";

    private static final int TEST_MAX_SCAN_BUCKETS_IN_CAPABILITIES = 8;
    private static final String TEST_PACKAGE_NAME = "com.test.123";
    private static final String TEST_FEATURE_ID = "test.feature";
    private static final String TEST_IFACE_NAME_0 = "wlan0";
    private static final String TEST_IFACE_NAME_1 = "wlan1";
    private static final int TEST_PSC_CHANNEL = ScanResult.BAND_6_GHZ_PSC_START_MHZ;
    private static final int TEST_NON_PSC_CHANNEL = 5985;
    private static final WifiScanner.ScanData PLACEHOLDER_SCAN_DATA =
            new WifiScanner.ScanData(0, 0, new ScanResult[0]);

    private final int mSwPnoMobilityIterations = 3;
    private final int mSwPnoFastIterations = 3;
    private final int mSwPnoSlowIterations = 10;

    @Mock Context mContext;
    TestAlarmManager mAlarmManager;
    @Mock WifiScannerImpl mWifiScannerImpl0;
    @Mock WifiScannerImpl mWifiScannerImpl1;
    @Mock WifiScannerImpl.WifiScannerImplFactory mWifiScannerImplFactory;
    @Mock BatteryStatsManager mBatteryStats;
    @Mock WifiInjector mWifiInjector;
    @Mock FrameworkFacade mFrameworkFacade;
    @Mock Clock mClock;
    @Spy FakeWifiLog mLog;
    @Mock WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock WifiNative mWifiNative;
    @Mock WifiMetrics mWifiMetrics;
    @Mock WifiMetrics.ScanMetrics mScanMetrics;
    @Mock WifiManager mWifiManager;
    @Mock LastCallerInfoManager mLastCallerInfoManager;
    @Mock DeviceConfigFacade mDeviceConfigFacade;
    @Mock WifiGlobals mWifiGlobals;
    PresetKnownBandsChannelHelper mChannelHelper0;
    PresetKnownBandsChannelHelper mChannelHelper1;
    TestLooper mLooper;
    WifiScanningServiceImpl mWifiScanningServiceImpl;
    private Bundle mExtras = new Bundle();
    MockResources mResources = new MockResources();
    Context mInstContext = InstrumentationRegistry.getContext();
    BroadcastReceiver mSwPnoBroadcastReceiver = null;
    int mSwPnoIterationCount = 0;
    ArrayList<Intent> mSwPnoIntentsQueue = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mAlarmManager = new TestAlarmManager();
        when(mContext.getSystemService(Context.ALARM_SERVICE))
                .thenReturn(mAlarmManager.getAlarmManager());
        when(mContext.getSystemService(AlarmManager.class))
                .thenReturn(mAlarmManager.getAlarmManager());
        when(mContext.getSystemService(WifiManager.class)).thenReturn(mWifiManager);
        doAnswer(inv -> {
            final BroadcastReceiver br = inv.getArgument(0);
            registerSwPnoBroadcastReceiver(br);
            return null;
        }).when(mContext).registerReceiver(any(BroadcastReceiver.class), any(), any(), any());

        mResources.setInteger(R.integer.config_wifiSwPnoMobilityStateTimerIterations,
                mSwPnoMobilityIterations);
        mResources.setInteger(R.integer.config_wifiSwPnoFastTimerIterations, mSwPnoFastIterations);
        mResources.setInteger(R.integer.config_wifiSwPnoSlowTimerIterations, mSwPnoSlowIterations);
        when(mWifiInjector.getWifiGlobals()).thenReturn(mWifiGlobals);
        when(mWifiGlobals.isSwPnoEnabled()).thenReturn(true);

        when(mContext.getResources()).thenReturn(mResources);
        when(mWifiInjector.getWifiPermissionsUtil())
                .thenReturn(mWifiPermissionsUtil);
        when(mContext.getUser()).thenReturn(mInstContext.getUser());
        when(mContext.getPackageName()).thenReturn(mInstContext.getPackageName());


        mChannelHelper0 = new PresetKnownBandsChannelHelper(
                new int[]{2412, 2450},
                new int[]{5160, 5175},
                new int[]{5600, 5650, 5660},
                new int[]{TEST_PSC_CHANNEL, TEST_NON_PSC_CHANNEL},
                new int[]{58320, 60480});
        mChannelHelper1 = new PresetKnownBandsChannelHelper(
                new int[]{2412, 2450},
                new int[]{5160, 5175},
                new int[]{5600, 5660, 5680}, // 5650 is missing from channelHelper0
                new int[]{5945, 5985},
                new int[]{58320, 60480});
        mLooper = new TestLooper();
        when(mWifiScannerImplFactory
                .create(any(), any(), any(), eq(TEST_IFACE_NAME_0)))
                .thenReturn(mWifiScannerImpl0);
        when(mWifiScannerImpl0.getChannelHelper()).thenReturn(mChannelHelper0);
        when(mWifiScannerImpl0.getIfaceName()).thenReturn(TEST_IFACE_NAME_0);
        when(mWifiScannerImplFactory
                .create(any(), any(), any(), eq(TEST_IFACE_NAME_1)))
                .thenReturn(mWifiScannerImpl1);
        when(mWifiScannerImpl1.getChannelHelper()).thenReturn(mChannelHelper1);
        when(mWifiScannerImpl1.getIfaceName()).thenReturn(TEST_IFACE_NAME_1);
        when(mWifiInjector.getWifiMetrics()).thenReturn(mWifiMetrics);
        when(mWifiMetrics.getScanMetrics()).thenReturn(mScanMetrics);
        when(mWifiInjector.makeLog(anyString())).thenReturn(mLog);
        when(mWifiInjector.getFrameworkFacade()).thenReturn(mFrameworkFacade);
        when(mWifiInjector.getClock()).thenReturn(mClock);
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0)));
        when(mWifiInjector.getWifiNative()).thenReturn(mWifiNative);
        when(mContext.checkPermission(eq(Manifest.permission.NETWORK_STACK),
                anyInt(), eq(Binder.getCallingUid())))
                .thenReturn(PERMISSION_GRANTED);
        when(mWifiInjector.getLastCallerInfoManager()).thenReturn(mLastCallerInfoManager);
        // Defaulting apps to target SDK level that's prior to U. This is needed to test for
        // backward compatibility of API changes.
        when(mWifiPermissionsUtil.isTargetSdkLessThan(any(),
                eq(Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
                anyInt())).thenReturn(true);
        WifiLocalServices.removeServiceForTest(WifiScannerInternal.class);
        when(mWifiInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        mWifiScanningServiceImpl = new WifiScanningServiceImpl(mContext, mLooper.getLooper(),
                mWifiScannerImplFactory, mBatteryStats, mWifiInjector);
    }

    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    /**
     * Internal BroadcastReceiver that WifiScanningServiceImpl uses to listen for broadcasts
     * this is initialized by calling startServiceAndLoadDriver
     */
    BroadcastReceiver mBroadcastReceiver;

    private WifiScanner.ScanSettings generateValidScanSettings() {
        return createRequest(WifiScanner.WIFI_BAND_BOTH, 30000, 0, 20,
                WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
    }

    private class TestClient {
        public IWifiScannerListener.Stub listener;
        public InOrder order;
        private IBinder mIBinder;
        private ArgumentCaptor<IBinder.DeathRecipient> mDeathRecipientArgumentCaptor =
                ArgumentCaptor.forClass(
                        IBinder.DeathRecipient.class);
        TestClient() {
            listener = mock(IWifiScannerListener.Stub.class);
            mIBinder = mock(IBinder.class);
            order = inOrder(listener, mIBinder);
            when(listener.asBinder()).thenReturn(mIBinder);
        }

        private void verifyLinkedToDeath() throws Exception {
            verify(listener, atLeastOnce()).asBinder();
            verify(mIBinder, atLeastOnce()).linkToDeath(mDeathRecipientArgumentCaptor.capture(),
                    anyInt());
        }

        private void verifyUnlinkedToDeath() throws Exception {
            verify(listener, atLeastOnce()).asBinder();
            verify(mIBinder, atLeastOnce()).unlinkToDeath(any(), anyInt());
        }

        private void registerScanListener() throws Exception {
            mWifiScanningServiceImpl.registerScanListener(listener, TEST_PACKAGE_NAME,
                    TEST_FEATURE_ID);
            mLooper.dispatchAll();
            if (listener != null) verifyLinkedToDeath();
        }

        private void binderDied() throws Exception {
            if (mDeathRecipientArgumentCaptor.getValue() != null) {
                mDeathRecipientArgumentCaptor.getValue().binderDied();
            }
        }

        private void deregisterScanListener() throws Exception {
            mWifiScanningServiceImpl.unregisterScanListener(listener, TEST_PACKAGE_NAME,
                    TEST_FEATURE_ID);
        }

        private void sendBackgroundScanRequest(WifiScanner.ScanSettings settings,
                WorkSource workSource) throws Exception {
            mWifiScanningServiceImpl.startBackgroundScan(listener, settings, workSource,
                    TEST_PACKAGE_NAME, TEST_FEATURE_ID);
            mLooper.dispatchAll();
            if (settings != null) verifyLinkedToDeath();
        }

        private void sendSingleScanRequest(WifiScanner.ScanSettings settings, WorkSource workSource)
                throws Exception {
            mWifiScanningServiceImpl.startScan(listener, settings, workSource, TEST_PACKAGE_NAME,
                    TEST_FEATURE_ID);
            mLooper.dispatchAll();
            if (settings != null) verifyLinkedToDeath();
        }

        private void verifyScanResultsReceived(WifiScanner.ScanData... expected) throws Exception {
            ArgumentCaptor<WifiScanner.ScanData[]> scanDataCaptor =
                    ArgumentCaptor.forClass(WifiScanner.ScanData[].class);
            order.verify(listener).onResults(scanDataCaptor.capture());
            assertScanDatasEquals(expected, scanDataCaptor.getValue());
        }

        private void verifySingleScanCompletedReceived()
                throws Exception {
            order.verify(listener).onSingleScanCompleted();
        }

        private void verifySuccessfulResponse() throws Exception {
            order.verify(listener).onSuccess();
        }

        private void verifyFailedResponse(
                int expectedErrorReason, String expectedErrorDescription) throws Exception {
            order.verify(listener, never()).onSuccess();
            order.verify(listener).onFailure(expectedErrorReason, expectedErrorDescription);
        }

        private void verifyFailedResponse(int nTimes,
                int expectedErrorReason, String expectedErrorDescription) throws Exception {
            order.verify(listener, never()).onSuccess();
            order.verify(listener, times(nTimes)).onFailure(expectedErrorReason,
                    expectedErrorDescription);
        }

        private void sendPnoScanRequest(WifiScanner.ScanSettings scanSettings,
                WifiScanner.PnoSettings pnoSettings) {
            Bundle pnoParams = new Bundle();
            scanSettings.isPnoScan = true;
            pnoParams.putParcelable(WifiScanner.PNO_PARAMS_SCAN_SETTINGS_KEY, scanSettings);
            pnoParams.putParcelable(WifiScanner.PNO_PARAMS_PNO_SETTINGS_KEY, pnoSettings);
            mWifiScanningServiceImpl.startPnoScan(listener, scanSettings, pnoSettings,
                    TEST_PACKAGE_NAME, null);
        }

        private void verifyPnoNetworkFoundReceived(ScanResult[] expected) throws Exception {
            order.verify(listener).onPnoNetworkFound(eq(expected));
        }
    }

    /**
     * If multiple results are expected for a single hardware scan then the order that they are
     * dispatched depends on the order which they are iterated through internally. This
     * function validates that the order is either one way or the other. A scan listener can
     * optionally be provided as well and will be checked after the single scan requests.
     */
    private static void verifyMultipleSingleScanResults(
            TestClient client1, ScanResults results1,
            TestClient client2, ScanResults results2)
            throws Exception {
        client1.verifyScanResultsReceived(new WifiScanner.ScanData[]{results1.getScanData()});
        client1.verifySingleScanCompletedReceived();

        client2.verifyScanResultsReceived(new WifiScanner.ScanData[]{results2.getScanData()});
        client2.verifySingleScanCompletedReceived();
    }

    private WifiNative.ScanEventHandler verifyStartSingleScan(InOrder order,
            WifiNative.ScanSettings expected) {
        return verifyStartSingleScanForImpl(mWifiScannerImpl0, order, expected);
    }

    private WifiNative.ScanEventHandler verifyStartSingleScanForImpl(
            WifiScannerImpl wifiScannerImpl, InOrder order, WifiNative.ScanSettings expected) {
        ArgumentCaptor<WifiNative.ScanSettings> scanSettingsCaptor =
                ArgumentCaptor.forClass(WifiNative.ScanSettings.class);
        ArgumentCaptor<WifiNative.ScanEventHandler> scanEventHandlerCaptor =
                ArgumentCaptor.forClass(WifiNative.ScanEventHandler.class);
        order.verify(wifiScannerImpl).startSingleScan(scanSettingsCaptor.capture(),
                scanEventHandlerCaptor.capture());
        assertNativeScanSettingsEquals(expected, scanSettingsCaptor.getValue());
        return scanEventHandlerCaptor.getValue();
    }

    private WifiNative.ScanEventHandler verifyStartSwPnoForImpl(
            WifiScannerImpl wifiScannerImpl, InOrder order) {
        ArgumentCaptor<WifiNative.ScanSettings> scanSettingsCaptor =
                ArgumentCaptor.forClass(WifiNative.ScanSettings.class);
        ArgumentCaptor<WifiNative.ScanEventHandler> scanEventHandlerCaptor =
                ArgumentCaptor.forClass(WifiNative.ScanEventHandler.class);
        order.verify(wifiScannerImpl).startSingleScan(scanSettingsCaptor.capture(),
                scanEventHandlerCaptor.capture());
        return scanEventHandlerCaptor.getValue();
    }

    private WifiNative.ScanEventHandler verifyStartBackgroundScan(InOrder order,
            WifiNative.ScanSettings expected) {
        ArgumentCaptor<WifiNative.ScanSettings> scanSettingsCaptor =
                ArgumentCaptor.forClass(WifiNative.ScanSettings.class);
        ArgumentCaptor<WifiNative.ScanEventHandler> scanEventHandlerCaptor =
                ArgumentCaptor.forClass(WifiNative.ScanEventHandler.class);
        order.verify(mWifiScannerImpl0).startBatchedScan(scanSettingsCaptor.capture(),
                scanEventHandlerCaptor.capture());
        assertNativeScanSettingsEquals(expected, scanSettingsCaptor.getValue());
        return scanEventHandlerCaptor.getValue();
    }

    private static final int MAX_AP_PER_SCAN = 16;
    private void startServiceAndLoadDriver() {
        mWifiScanningServiceImpl.startService();
        mLooper.dispatchAll();
        setupAndLoadDriver(TEST_MAX_SCAN_BUCKETS_IN_CAPABILITIES);
    }

    private void setupAndLoadDriver(int maxScanBuckets) {
        when(mWifiScannerImpl0.getScanCapabilities(any(WifiNative.ScanCapabilities.class)))
                .thenAnswer(new AnswerWithArguments() {
                    public boolean answer(WifiNative.ScanCapabilities capabilities) {
                        capabilities.max_scan_cache_size = Integer.MAX_VALUE;
                        capabilities.max_scan_buckets = maxScanBuckets;
                        capabilities.max_ap_cache_per_scan = MAX_AP_PER_SCAN;
                        capabilities.max_rssi_sample_size = 8;
                        capabilities.max_scan_reporting_threshold = 10;
                        return true;
                    }
                });
        mWifiScanningServiceImpl.setScanningEnabled(true, 0, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
    }

    private String dumpService() {
        StringWriter stringWriter = new StringWriter();
        mWifiScanningServiceImpl.dump(new FileDescriptor(), new PrintWriter(stringWriter),
                new String[0]);
        return stringWriter.toString();
    }

    private void assertDumpContainsRequestLog(String type) {
        String serviceDump = dumpService();
        Pattern logLineRegex = Pattern.compile("^.+" + type
                        + ": ClientInfo\\[uid=\\d+, package=" + TEST_PACKAGE_NAME
                        + ", Mock for Stub, hashCode: \\d+\\]",
                Pattern.MULTILINE);
        assertTrue("dump did not contain log with {" + logLineRegex
                        + "}\n" + serviceDump + "\n",
                logLineRegex.matcher(serviceDump).find());
    }

    private void assertDumpContainsCallbackLog(String callback, String extra) {
        String serviceDump = dumpService();
        String extraPattern = extra == null ? "" : "," + extra;
        Pattern logLineRegex = Pattern.compile("^.+" + callback
                + ": ClientInfo\\[uid=\\d+, package=" + TEST_PACKAGE_NAME
                + ", Mock for Stub, hashCode: \\d+\\]"
                + extraPattern + "$", Pattern.MULTILINE);
        assertTrue("dump did not contain callback log with callback {" + logLineRegex
                        + "]\n" + serviceDump + "\n",
                logLineRegex.matcher(serviceDump).find());
    }

    @Test
    public void construct() throws Exception {
        verifyNoMoreInteractions(mWifiScannerImpl0, mWifiScannerImpl0,
                mWifiScannerImplFactory, mBatteryStats);
        dumpService(); // make sure this succeeds
    }

    @Test
    public void startServiceAndTriggerSingleScanWithoutDriverLoaded() throws Exception {
        mWifiScanningServiceImpl.startService();
        mLooper.dispatchAll();
        verifyNoMoreInteractions(mWifiScannerImplFactory);

        TestClient client = new TestClient();
        client.sendSingleScanRequest(createRequest(WifiScanner.WIFI_BAND_ALL,
                0, 0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN), new WorkSource(2292));
        mLooper.dispatchAll();
        verify(client.listener).onFailure(WifiScanner.REASON_UNSPECIFIED, "not available");
    }

    @Test
    public void loadDriver() throws Exception {
        startServiceAndLoadDriver();
        verify(mWifiScannerImplFactory, times(1))
                .create(any(), any(), any(), eq(TEST_IFACE_NAME_0));

        TestClient client = new TestClient();
        when(mWifiScannerImpl0.startBatchedScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);
        client.sendBackgroundScanRequest(generateValidScanSettings(), null);
        mLooper.dispatchAll();
        verify(client.listener).onSuccess();
        assertDumpContainsRequestLog("addBackgroundScanRequest");
    }

    /**
     * Verifies that duplicate scan enable is ignored.
     */
    @Test
    public void duplicateScanEnableIsIgnored() throws Exception {
        startServiceAndLoadDriver();

        // Send scan enable again.
        mWifiScanningServiceImpl.setScanningEnabled(true, 0, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();

        // Ensure we didn't create scanner instance twice.
        verify(mWifiScannerImplFactory, times(1))
                .create(any(), any(), any(), any());
    }

    @Test
    public void rejectBackgroundScanRequestWhenHalReturnsInvalidCapabilities() throws Exception {
        mWifiScanningServiceImpl.startService();
        mLooper.dispatchAll();

        setupAndLoadDriver(0);
        TestClient client = new TestClient();
        client.sendBackgroundScanRequest(generateValidScanSettings(), null);
        InOrder order = inOrder(client.listener);
        client.sendBackgroundScanRequest(generateValidScanSettings(), null);
        mLooper.dispatchAll();
        order.verify(client.listener, times(2)).onFailure(WifiScanner.REASON_UNSPECIFIED,
                "not available");
    }

    @Test
    public void rejectBackgroundScanRequestWhenScannerImplCreateFails() throws Exception {
        // Fail scanner impl creation.
        when(mWifiScannerImplFactory.create(any(), any(), any(), any())).thenReturn(null);

        startServiceAndLoadDriver();

        TestClient client = new TestClient();
        client.sendBackgroundScanRequest(generateValidScanSettings(), null);
        mLooper.dispatchAll();
        verify(client.listener).onFailure(WifiScanner.REASON_UNSPECIFIED, "not available");
    }

    private void doSuccessfulSingleScan(WifiScanner.ScanSettings requestSettings,
            WifiNative.ScanSettings nativeSettings, @NonNull ScanResults resultsForImpl0)
            throws Exception {
        doSuccessfulSingleScanOnImpls(requestSettings, nativeSettings, resultsForImpl0, null);
    }

    private void doSuccessfulSingleScanOnImpls(WifiScanner.ScanSettings requestSettings,
            WifiNative.ScanSettings nativeSettings, @NonNull ScanResults resultsForImpl0,
            @Nullable ScanResults resultsForImpl1) throws Exception {
        WorkSource workSource = new WorkSource(2292);
        startServiceAndLoadDriver();

        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0, mWifiScannerImpl1);

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);
        if (resultsForImpl1 != null) {
            when(mWifiScannerImpl1.startSingleScan(any(WifiNative.ScanSettings.class),
                    any(WifiNative.ScanEventHandler.class))).thenReturn(true);
        }
        client.sendSingleScanRequest(requestSettings, workSource);
        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler0 =
                verifyStartSingleScanForImpl(mWifiScannerImpl0, order, nativeSettings);
        WifiNative.ScanEventHandler eventHandler1 = null;
        if (resultsForImpl1 != null) {
            eventHandler1 = verifyStartSingleScanForImpl(mWifiScannerImpl1, order, nativeSettings);
        }

        verify(mBatteryStats).reportWifiScanStartedFromSource(eq(workSource));

        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(resultsForImpl0.getScanData());
        eventHandler0.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
        if (resultsForImpl1 != null) {
            when(mWifiScannerImpl1.getLatestSingleScanResults())
                    .thenReturn(resultsForImpl1.getScanData());
            eventHandler1.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
        }

        mLooper.dispatchAll();
        ScanResults expectedResults = resultsForImpl0;
        if (resultsForImpl1 != null) {
            expectedResults = ScanResults.merge(
                    resultsForImpl0.getScanData().getScannedBandsInternal(),
                    resultsForImpl0, resultsForImpl1);
        }
        client.verifyLinkedToDeath();
        verify(client.listener).onSuccess();
        client.verifyScanResultsReceived(expectedResults.getScanData());
        verify(client.listener).onSingleScanCompleted();
        verifyNoMoreInteractions(client.listener);
        verify(mBatteryStats).reportWifiScanStoppedFromSource(eq(workSource));
        assertDumpContainsRequestLog("addSingleScanRequest");
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + expectedResults.getScanData().getResults().length);
    }

    /**
     * Do a single scan for a band and verify that it is successful.
     */
    @Test
    public void sendSingleScanBandRequest() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_ALL,
                0, 0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        doSuccessfulSingleScan(requestSettings, computeSingleScanNativeSettings(requestSettings),
                ScanResults.create(0, WifiScanner.WIFI_BAND_ALL, 2412, 5160, 5175));
    }

    /**
     * Do a single scan for a list of channels and verify that it is successful.
     */
    @Test
    public void sendSingleScanChannelsRequest() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(channelsToSpec(2412, 5160, 5175),
                0, 0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        doSuccessfulSingleScan(requestSettings, computeSingleScanNativeSettings(requestSettings),
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412, 5160, 5175));
    }

    /**
     * Do a single scan for a list of all channels and verify that it is successful.
     */
    @Test
    public void sendSingleScanAllChannelsRequest() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(
                channelsToSpec(2412, 2450, 5160, 5175, 5600, 5650, 5660),
                0, 0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        doSuccessfulSingleScan(requestSettings, computeSingleScanNativeSettings(requestSettings),
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412, 5160, 5175));
    }

    /**
     * Do a single scan with no results and verify that it is successful.
     */
    @Test
    public void sendSingleScanRequestWithNoResults() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        doSuccessfulSingleScan(requestSettings, computeSingleScanNativeSettings(requestSettings),
                ScanResults.create(0, WifiScanner.WIFI_BAND_BOTH, new int[0]));
        verify(mLastCallerInfoManager).put(eq(WifiManager.API_WIFI_SCANNER_START_SCAN), anyInt(),
                anyInt(), anyInt(), anyString(), eq(true));
    }

    /**
     * Verify that PSC channels not added when a channel list is explicitly specified for scanning.
     */
    @Test
    public void testPscIsIgnoredForPartialScan() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiScanner.ScanSettings requestSettings = createRequest(
                channelsToSpec(2412, 5160, 5955),
                0, 0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        int expectedChannelListSize = requestSettings.channels.length;
        requestSettings.set6GhzPscOnlyEnabled(true);
        WifiNative.ScanSettings fromChannelList = computeSingleScanNativeSettings(requestSettings);
        assertEquals(expectedChannelListSize, fromChannelList.buckets[0].channels.length);
        WifiNative.ScanSettings fromChannelHelper =
                computeSingleScanNativeSettingsWithChannelHelper(requestSettings, mChannelHelper0);
        assertNativeScanSettingsEquals(fromChannelList, fromChannelHelper);
        doSuccessfulSingleScan(requestSettings, fromChannelList,
                ScanResults.create(0, WifiScanner.WIFI_BAND_BOTH, new int[0]));
    }

    /**
     * Verify that when set6GhzPscOnlyEnabled(true) is used, only 6Ghz PSC channels get added
     * when the 6Ghz band is being scanned.
     */
    @Test
    public void testPscChannelAddedWhenScanning6GhzBand() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_ALL, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings.set6GhzPscOnlyEnabled(true);

        // The expectedChannels should match with all supported channels configured for
        // mChannelHelper0, less the TEST_NON_PSC_CHANNEL
        Set<Integer> expectedChannels = new ArraySet<Integer>(
                new Integer[]{2412, 2450, 5160, 5175, 5600, 5650, 5660, 5600, 5650, 5660,
                        TEST_PSC_CHANNEL, 58320, 60480});

        // Compute the expected nativeSettings
        WifiNative.ScanSettings nativeSettings =
                computeSingleScanNativeSettingsWithChannelHelper(requestSettings, mChannelHelper0);
        assertEquals("The scan band should be WIFI_BAND_UNSPECIFIED since only a subset of 6Ghz "
                        + "channels need to be scanned", WifiScanner.WIFI_BAND_UNSPECIFIED,
                nativeSettings.buckets[0].band);
        Set<Integer> scanTestUtilcomputedExpectedChannels = new ArraySet<>();
        for (WifiNative.ChannelSettings channelSettings : nativeSettings.buckets[0].channels) {
            scanTestUtilcomputedExpectedChannels.add(channelSettings.frequency);
        }

        assertEquals("Computed native settings does not match with expected value",
                expectedChannels, scanTestUtilcomputedExpectedChannels);
        doSuccessfulSingleScan(requestSettings, nativeSettings,
                ScanResults.create(0, WifiScanner.WIFI_BAND_BOTH, new int[0]));
    }

    /**
     * Verify that when set6GhzPscOnlyEnabled(true) is used but the 6Ghz band not being scanned,
     * we ignore the flag.
     */
    @Test
    public void testPscChannelNotAddedWhenNotScanning6GhzBand() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH,
                0, 0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings.set6GhzPscOnlyEnabled(true);
        WifiNative.ScanSettings fromChannelList = computeSingleScanNativeSettings(requestSettings);
        assertNull("channel list should be null", fromChannelList.buckets[0].channels);
        WifiNative.ScanSettings fromChannelHelper =
                computeSingleScanNativeSettingsWithChannelHelper(requestSettings, mChannelHelper0);
        assertNativeScanSettingsEquals(fromChannelList, fromChannelHelper);
        doSuccessfulSingleScan(requestSettings, fromChannelList,
                ScanResults.create(0, WifiScanner.WIFI_BAND_BOTH, new int[0]));
    }

    /**
     * Verify WifiNative.ScanSettings#enable6GhzRnr is set appropriatly according to
     * WifiScanner.ScanSettings#band and WifiScanner.ScanSettings#getRnrSetting().
     */
    @Test
    public void testRnrIsDisabledIf6GhzBandIsNotScanned() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // Verify RNR is disabled by default since WIFI_BAND_BOTH doesn't include the 6Ghz band.
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        WifiNative.ScanSettings nativeSettings = computeSingleScanNativeSettings(requestSettings);
        assertEquals(WifiScanner.WIFI_RNR_ENABLED_IF_WIFI_BAND_6_GHZ_SCANNED,
                requestSettings.getRnrSetting());
        assertEquals(false, nativeSettings.enable6GhzRnr);
        doSuccessfulSingleScan(requestSettings, nativeSettings,
                ScanResults.create(0, WifiScanner.WIFI_BAND_BOTH, new int[0]));
    }

    /**
     * Verify that when WIFI_BAND_ALL is scanned, RNR is automatically enabled when
     * getRnrSetting() returns WIFI_RNR_ENABLED_IF_WIFI_BAND_6_GHZ_SCANNED.
     */
    @Test
    public void testRnrIsEnabledIf6GhzBandIsScanned() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_ALL, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        WifiNative.ScanSettings nativeSettings = computeSingleScanNativeSettings(requestSettings);
        assertEquals(WifiScanner.WIFI_RNR_ENABLED_IF_WIFI_BAND_6_GHZ_SCANNED,
                requestSettings.getRnrSetting());
        assertEquals(true, nativeSettings.enable6GhzRnr);
        doSuccessfulSingleScan(requestSettings, nativeSettings,
                ScanResults.create(0, WifiScanner.WIFI_BAND_ALL, new int[0]));
    }

    /**
     * Verify RNR is enabled even though only 2.4 and 5Ghz channels are being scanned because of
     * getRnrSetting() returns WIFI_RNR_ENABLED.
     */
    @Test
    public void testForceEnableRnr() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings.setRnrSetting(WifiScanner.WIFI_RNR_ENABLED);
        WifiNative.ScanSettings nativeSettings = computeSingleScanNativeSettings(requestSettings);
        assertEquals(WifiScanner.WIFI_RNR_ENABLED,
                requestSettings.getRnrSetting());
        assertEquals(true, nativeSettings.enable6GhzRnr);
        doSuccessfulSingleScan(requestSettings, nativeSettings,
                ScanResults.create(0, WifiScanner.WIFI_BAND_BOTH, new int[0]));
    }

    /**
     * Verify that when 6Ghz scanning is not supported, RNR will not get enabled even if RNR
     * setting is WIFI_RNR_ENABLED.
     */
    @Test
    public void testRnrIsDisabledWhen6GhzChannelsNotAvailable() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mChannelHelper0 = new PresetKnownBandsChannelHelper(
                new int[]{2412, 2450},
                new int[]{5160, 5175},
                new int[]{5600, 5650, 5660},
                new int[0], // 6Ghz scanning unavailable
                new int[]{58320, 60480});
        when(mWifiScannerImpl0.getChannelHelper()).thenReturn(mChannelHelper0);
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings.setRnrSetting(WifiScanner.WIFI_RNR_ENABLED);
        WifiNative.ScanSettings nativeSettings = computeSingleScanNativeSettings(requestSettings);
        // RNR should not be enabled in the native settings
        nativeSettings.enable6GhzRnr = false;
        assertEquals(WifiScanner.WIFI_RNR_ENABLED,
                requestSettings.getRnrSetting());
        doSuccessfulSingleScan(requestSettings, nativeSettings,
                ScanResults.create(0, WifiScanner.WIFI_BAND_BOTH, new int[0]));
    }

    /**
     * Verify that when WIFI_BAND_ALL is scanned, RNR is disabled when
     * getRnrSetting() returns WIFI_RNR_NOT_NEEDED.
     */
    @Test
    public void testRnrIsExplicitlyDisabled() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_ALL, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings.setRnrSetting(WifiScanner.WIFI_RNR_NOT_NEEDED);
        WifiNative.ScanSettings nativeSettings = computeSingleScanNativeSettings(requestSettings);
        assertEquals(WifiScanner.WIFI_RNR_NOT_NEEDED,
                requestSettings.getRnrSetting());
        assertEquals(false, nativeSettings.enable6GhzRnr);
        doSuccessfulSingleScan(requestSettings, nativeSettings,
                ScanResults.create(0, WifiScanner.WIFI_BAND_ALL, new int[0]));
    }

    /**
     * Do a single scan with null vendor IEs, and verify getVendorIes() and nativeSettings.vendorIes
     * returning correct values.
     */
    @Test
    public void sendSingleScanRequestWithNullVendorIes() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_ALL, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        WifiNative.ScanSettings nativeSettings = computeSingleScanNativeSettings(requestSettings);
        assertEquals(0, requestSettings.getVendorIes().size());
        assertEquals(null, nativeSettings.vendorIes);
        doSuccessfulSingleScan(requestSettings, nativeSettings,
                ScanResults.create(0, WifiScanner.WIFI_BAND_ALL, new int[0]));
    }

    /**
     * Do a single scan with nonnull vendor IEs, and verify getVendorIes() and
     * nativeSettings.vendorIes returning same values.
     */
    @Test
    public void sendSingleScanRequestWithNonNullVendorIes() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_ALL, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        List<ScanResult.InformationElement> vendorIesList = new ArrayList<>();
        ScanResult.InformationElement vendorIe1 = new ScanResult.InformationElement(221, 0,
                new byte[]{0x00, 0x50, (byte) 0xf2, 0x08, 0x11, 0x22, 0x33});
        ScanResult.InformationElement vendorIe2 = new ScanResult.InformationElement(221, 0,
                new byte[]{0x00, 0x50, (byte) 0xf2, 0x08, (byte) 0xaa, (byte) 0xbb, (byte) 0xcc});
        vendorIesList.add(vendorIe1);
        vendorIesList.add(vendorIe2);
        requestSettings.setVendorIes(vendorIesList);

        WifiNative.ScanSettings nativeSettings = computeSingleScanNativeSettings(requestSettings);
        byte[] nativeSettingsVendorIes =
                new byte[WifiScanner.WIFI_IE_HEAD_LEN + vendorIe1.bytes.length
                        + WifiScanner.WIFI_IE_HEAD_LEN + vendorIe2.bytes.length];
        int index = 0;
        nativeSettingsVendorIes[index] = (byte) vendorIe1.id;
        nativeSettingsVendorIes[index + 1] = (byte) vendorIe1.bytes.length;
        System.arraycopy(vendorIe1.bytes, 0, nativeSettingsVendorIes,
                index + WifiScanner.WIFI_IE_HEAD_LEN,
                vendorIe1.bytes.length);
        index += WifiScanner.WIFI_IE_HEAD_LEN + vendorIe1.bytes.length;
        nativeSettingsVendorIes[index] = (byte) vendorIe2.id;
        nativeSettingsVendorIes[index + 1] = (byte) vendorIe2.bytes.length;
        System.arraycopy(vendorIe2.bytes, 0, nativeSettingsVendorIes,
                index + WifiScanner.WIFI_IE_HEAD_LEN,
                vendorIe2.bytes.length);
        assertArrayEquals(nativeSettingsVendorIes, nativeSettings.vendorIes);
        doSuccessfulSingleScan(requestSettings, nativeSettings,
                ScanResults.create(0, WifiScanner.WIFI_BAND_ALL, new int[0]));
    }

    /**
     * Do a single scan with results that do not match the requested scan and verify that it is
     * still successful (and returns no results).
     */
    @Test
    public void sendSingleScanRequestWithBadRawResults() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_24_GHZ, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        // Create a set of scan results that has results not matching the request settings, but is
        // limited to zero results for the expected results.
        ScanResults results = ScanResults.createOverflowing(0, WifiScanner.WIFI_BAND_24_GHZ, 0,
                ScanResults.generateNativeResults(0, 5160, 5171));
        doSuccessfulSingleScan(requestSettings, computeSingleScanNativeSettings(requestSettings),
                results);
    }

    /**
     * Do a single scan from a non-privileged app with some privileged params set.
     * Expect a scan failure.
     */
    @Test
    public void sendSingleScanRequestWithPrivilegedTypeParamsSetFromNonPrivilegedApp()
            throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        requestSettings.type = WifiScanner.SCAN_TYPE_HIGH_ACCURACY;
        WorkSource workSource = new WorkSource(Binder.getCallingUid()); // don't explicitly set

        when(mContext.checkPermission(
                Manifest.permission.NETWORK_STACK, -1, Binder.getCallingUid()))
                .thenReturn(PERMISSION_DENIED);

        startServiceAndLoadDriver();

        TestClient client = new TestClient();

        // successful start
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        client.sendSingleScanRequest(requestSettings, null);

        // Scan is successfully queued
        mLooper.dispatchAll();

        // but then fails to execute
        client.verifyFailedResponse(
                WifiScanner.REASON_INVALID_REQUEST, "bad request");
        assertDumpContainsCallbackLog("singleScanInvalidRequest",
                "bad request");

        verify(mWifiMetrics, never()).incrementOneshotScanCount();
        verify(mWifiMetrics).incrementScanReturnEntry(
                WifiMetricsProto.WifiLog.SCAN_FAILURE_INVALID_CONFIGURATION, 1);

        // Ensure that no scan was triggered to the lower layers.
        verify(mBatteryStats, never()).reportWifiScanStoppedFromSource(eq(workSource));
        verify(mWifiScannerImpl0, never()).startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class));
    }

    /**
     * Do a single scan from a non-privileged app with some privileged params set.
     * Expect a scan failure.
     */
    @Test
    public void sendSingleScanRequestWithPrivilegedHiddenNetworkParamsSetFromNonPrivilegedApp()
            throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        requestSettings.hiddenNetworks.clear();
        requestSettings.hiddenNetworks.add(
                new WifiScanner.ScanSettings.HiddenNetwork("Test1"));
        requestSettings.hiddenNetworks.add(
                new WifiScanner.ScanSettings.HiddenNetwork("Test2"));
        WorkSource workSource = new WorkSource(Binder.getCallingUid()); // don't explicitly set

        when(mContext.checkPermission(
                Manifest.permission.NETWORK_STACK, -1, Binder.getCallingUid()))
                .thenReturn(PERMISSION_DENIED);

        startServiceAndLoadDriver();

        TestClient client = new TestClient();

        // successful start
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        client.sendSingleScanRequest(requestSettings, null);

        // Scan is successfully queued
        mLooper.dispatchAll();

        // but then fails to execute
        client.verifyFailedResponse(
                WifiScanner.REASON_INVALID_REQUEST, "bad request");
        assertDumpContainsCallbackLog("singleScanInvalidRequest",
                "bad request");

        verify(mWifiMetrics, never()).incrementOneshotScanCount();
        verify(mWifiMetrics).incrementScanReturnEntry(
                WifiMetricsProto.WifiLog.SCAN_FAILURE_INVALID_CONFIGURATION, 1);

        // Ensure that no scan was triggered to the lower layers.
        verify(mBatteryStats, never()).reportWifiScanStoppedFromSource(eq(workSource));
        verify(mWifiScannerImpl0, never()).startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class));
    }

    /**
     * Do a single scan with invalid scan type set.
     * Expect a scan failure.
     */
    @Test
    public void sendSingleScanRequestWithInvalidScanType()
            throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        requestSettings.type = 100; // invalid  scan type
        WorkSource workSource = new WorkSource(Binder.getCallingUid()); // don't explicitly set

        startServiceAndLoadDriver();

        TestClient client = new TestClient();

        // successful start
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        client.sendSingleScanRequest(requestSettings, null);

        // Scan is successfully queued
        mLooper.dispatchAll();

        // but then fails to execute
        client.verifyFailedResponse(
                WifiScanner.REASON_INVALID_REQUEST, "bad request");
        assertDumpContainsCallbackLog("singleScanInvalidRequest",
                "bad request");

        verify(mWifiMetrics, never()).incrementOneshotScanCount();
        verify(mWifiMetrics).incrementScanReturnEntry(
                WifiMetricsProto.WifiLog.SCAN_FAILURE_INVALID_CONFIGURATION, 1);

        // Ensure that no scan was triggered to the lower layers.
        verify(mBatteryStats, never()).reportWifiScanStoppedFromSource(eq(workSource));
        verify(mWifiScannerImpl0, never()).startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class));
    }

    /**
     * Do a single scan from a non-privileged app with no privileged params set.
     */
    @Test
    public void sendSingleScanRequestWithNoPrivilegedParamsSetFromNonPrivilegedApp()
            throws Exception {
        when(mContext.checkPermission(
                Manifest.permission.NETWORK_STACK, -1, Binder.getCallingUid()))
                .thenReturn(PERMISSION_DENIED);
        WifiScanner.ScanSettings requestSettings = createRequest(channelsToSpec(2412, 5160, 5175),
                0, 0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        doSuccessfulSingleScan(requestSettings, computeSingleScanNativeSettings(requestSettings),
                ScanResults.create(0, 2412, 5160, 5175));
    }

    /**
     * Do a single scan, which the hardware fails to start, and verify that a failure response is
     * delivered.
     */
    @Test
    public void sendSingleScanRequestWhichFailsToStart() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        startServiceAndLoadDriver();

        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0);

        // scan fails
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(false);

        client.sendSingleScanRequest(requestSettings, null);
        mLooper.dispatchAll();
        // Scan is successfully queue, but then fails to execute
        order.verify(client.listener, never()).onResults(any());
        client.verifySuccessfulResponse();
        client.verifyFailedResponse(WifiScanner.REASON_UNSPECIFIED,
                "Failed to start single scan");
        verifyNoMoreInteractions(mBatteryStats);

        verify(mWifiMetrics).incrementOneshotScanCount();
        verify(mWifiMetrics).incrementScanReturnEntry(WifiMetricsProto.WifiLog.SCAN_UNKNOWN, 1);
        assertDumpContainsRequestLog("addSingleScanRequest");
    }

    /**
     * Do a single scan, which successfully starts, but fails partway through and verify that a
     * failure response is delivered.
     */
    @Test
    public void sendSingleScanRequestWhichFailsAfterStart() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        WorkSource workSource = new WorkSource(Binder.getCallingUid()); // don't explicitly set

        startServiceAndLoadDriver();

        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0);

        // successful start
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        client.sendSingleScanRequest(requestSettings, null);

        // Scan is successfully queue
        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler =
                verifyStartSingleScan(order, computeSingleScanNativeSettings(requestSettings));
        client.verifySuccessfulResponse();
        verify(mBatteryStats).reportWifiScanStartedFromSource(eq(workSource));

        // but then fails to execute
        eventHandler.onScanRequestFailed(WifiScanner.REASON_UNSPECIFIED);
        mLooper.dispatchAll();
        client.verifyFailedResponse(
                WifiScanner.REASON_UNSPECIFIED,
                "Scan failed - unspecified reason");
        assertDumpContainsCallbackLog("singleScanFailed",
                "reason=" + WifiScanner.REASON_UNSPECIFIED + ", Scan failed - unspecified reason");
        verify(mWifiMetrics).incrementOneshotScanCount();
        verify(mWifiMetrics).incrementScanReturnEntry(WifiMetricsProto.WifiLog.SCAN_UNKNOWN, 1);
        verify(mBatteryStats).reportWifiScanStoppedFromSource(eq(workSource));
    }

    /**
     * Do a single scan that includes DFS channels and verify that both oneshot scan count and
     * oneshot scan count with dfs are incremented.
     */
    @Test
    public void testMetricsForOneshotScanWithDFSIsIncremented() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(
                WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_60_GHZ, 0, 0, 20,
                WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        WorkSource workSource = new WorkSource(Binder.getCallingUid()); // don't explicitly set

        startServiceAndLoadDriver();

        TestClient client = new TestClient();

        // successful start
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        verify(mWifiMetrics, never()).incrementOneshotScanCount();
        verify(mWifiMetrics, never()).incrementOneshotScanWithDfsCount();

        client.sendSingleScanRequest(requestSettings, null);
        // Scan is successfully queue
        mLooper.dispatchAll();
        verify(mWifiMetrics).incrementOneshotScanCount();
        verify(mWifiMetrics).incrementOneshotScanWithDfsCount();
    }

    /**
     * Do a single scan that excludes DFS channels and verify that only oneshot scan count is
     * incremented.
     */
    @Test
    public void testMetricsForOneshotScanWithDFSIsNotIncremented() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(
                WifiScanner.WIFI_BAND_5_GHZ, 0, 0, 20,
                WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        WorkSource workSource = new WorkSource(Binder.getCallingUid()); // don't explicitly set

        startServiceAndLoadDriver();

        TestClient client = new TestClient();
        // successful start
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);
        verify(mWifiMetrics, never()).incrementOneshotScanCount();
        verify(mWifiMetrics, never()).incrementOneshotScanWithDfsCount();
        client.sendSingleScanRequest(requestSettings, workSource);
        // Scan is successfully queue
        mLooper.dispatchAll();
        verify(mWifiMetrics).incrementOneshotScanCount();
        verify(mWifiMetrics, never()).incrementOneshotScanWithDfsCount();
    }

    /**
     * Send a single scan request and then disable Wi-Fi before it completes
     */
    @Test
    public void sendSingleScanRequestThenDisableWifi() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(channelsToSpec(2412), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        startServiceAndLoadDriver();

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        TestClient client = new TestClient();

        // Run scan 1
        client.sendSingleScanRequest(requestSettings, null);
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();

        // disable wifi
        mWifiScanningServiceImpl.setScanningEnabled(false, 0, TEST_PACKAGE_NAME);

        // validate failed response
        mLooper.dispatchAll();
        client.verifyFailedResponse(WifiScanner.REASON_UNSPECIFIED,
                "Scan was interrupted");
        client.verifyUnlinkedToDeath();
        verifyNoMoreInteractions(client.listener);
    }

    /**
     * Send a single scan request and then disable Wi-Fi before it completes
     */
    @Test
    public void sendSingleScanRequestThenDisableWifiAfterScanCompleteButBeforeReportingResults()
            throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(channelsToSpec(2412), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        ScanResults results = ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412);

        startServiceAndLoadDriver();

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0);

        // Run scan 1
        client.sendSingleScanRequest(requestSettings, null);
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();
        WifiNative.ScanEventHandler eventHandler = verifyStartSingleScan(order,
                computeSingleScanNativeSettings(requestSettings));

        // disable wifi
        mWifiScanningServiceImpl.setScanningEnabled(false, 0, TEST_PACKAGE_NAME);
        // scan results complete event
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results.getScanData());
        eventHandler.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        client.verifyFailedResponse(WifiScanner.REASON_UNSPECIFIED,
                "Scan was interrupted");
        client.verifyUnlinkedToDeath();
        verifyNoMoreInteractions(client.listener);
    }

    /**
     * Send a single scan request, schedule a second pending scan and disable Wi-Fi before the first
     * scan completes.
     */
    @Test
    public void sendSingleScanAndPendingScanAndListenerThenDisableWifi() throws Exception  {
        WifiScanner.ScanSettings requestSettings1 = createRequest(channelsToSpec(2412), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        WifiScanner.ScanSettings requestSettings2 = createRequest(channelsToSpec(2450), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        startServiceAndLoadDriver();

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        TestClient client = new TestClient();
        TestClient client1 = new TestClient();
        TestClient client2 = new TestClient();

        // Request scan 1
        client1.sendSingleScanRequest(requestSettings1, null);
        mLooper.dispatchAll();
        client1.verifySuccessfulResponse();

        // Request scan 2
        client2.sendSingleScanRequest(requestSettings2, null);
        mLooper.dispatchAll();
        client2.verifySuccessfulResponse();

        // Setup scan listener
        client.registerScanListener();
        mLooper.dispatchAll();
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();

        // disable wifi
        mWifiScanningServiceImpl.setScanningEnabled(false, 0, TEST_PACKAGE_NAME);

        // validate failed response
        mLooper.dispatchAll();
        client1.verifyFailedResponse(WifiScanner.REASON_UNSPECIFIED, "Scan was interrupted");
        client2.verifyFailedResponse(WifiScanner.REASON_UNSPECIFIED, "Scan was interrupted");
        // No additional callbacks for scan listener
        verifyNoMoreInteractions(client.listener);
    }

    /**
     * Send a single scan request and then a second one after the first completes.
     */
    @Test
    public void sendSingleScanRequestAfterPreviousCompletes() throws Exception {
        WifiScanner.ScanSettings requestSettings1 = createRequest(channelsToSpec(2412), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        ScanResults results1 = ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412);

        WifiScanner.ScanSettings requestSettings2 = createRequest(channelsToSpec(2450, 5175), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        ScanResults results2 = ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2450);

        startServiceAndLoadDriver();

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0, mContext);

        // Run scan 1
        client.sendSingleScanRequest(requestSettings1, null);

        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler1 = verifyStartSingleScan(order,
                computeSingleScanNativeSettings(requestSettings1));
        client.verifySuccessfulResponse();

        // dispatch scan 1 results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results1.getScanData());
        eventHandler1.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        // Note: The order of the following verification calls looks out of order if you compare to
        // the source code of WifiScanningServiceImpl WifiSingleScanStateMachine.reportScanResults.
        // This is due to the fact that verifyScanResultsReceived and
        // verifySingleScanCompletedReceived require an additional call to handle the message that
        // is created in reportScanResults.  This handling is done in the two verify*Received calls
        // that is run AFTER the reportScanResults method in WifiScanningServiceImpl completes.
        client.verifyScanResultsReceived(results1.getScanData());
        client.verifySingleScanCompletedReceived();

        // Run scan 2
        client.sendSingleScanRequest(requestSettings2, null);

        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler2 = verifyStartSingleScan(order,
                computeSingleScanNativeSettings(requestSettings2));
        client.verifySuccessfulResponse();

        // dispatch scan 2 results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results2.getScanData());
        eventHandler2.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        client.verifyScanResultsReceived(results2.getScanData());
        client.verifySingleScanCompletedReceived();
    }

    /**
     * Send a single scan request and then a second one not satisfied by the first before the first
     * completes. Verify that both are scheduled and succeed.
     */
    @Test
    public void sendSingleScanRequestWhilePreviousScanRunning() throws Exception {
        WifiScanner.ScanSettings requestSettings1 = createRequest(channelsToSpec(2412), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        ScanResults results1 = ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412);

        WifiScanner.ScanSettings requestSettings2 = createRequest(channelsToSpec(2450, 5175), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        ScanResults results2 = ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2450);


        startServiceAndLoadDriver();

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        TestClient client = new TestClient();
        InOrder nativeOrder = inOrder(mWifiScannerImpl0);

        // Run scan 1
        client.sendSingleScanRequest(requestSettings1, null);

        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler1 = verifyStartSingleScan(nativeOrder,
                computeSingleScanNativeSettings(requestSettings1));
        client.verifySuccessfulResponse();

        // Queue scan 2 (will not run because previous is in progress)
        client.sendSingleScanRequest(requestSettings2, null);
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();

        // dispatch scan 1 results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results1.getScanData());
        eventHandler1.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        client.verifyScanResultsReceived(results1.getScanData());
        client.verifySingleScanCompletedReceived();

        // now that the first scan completed we expect the second one to start
        WifiNative.ScanEventHandler eventHandler2 = verifyStartSingleScan(nativeOrder,
                computeSingleScanNativeSettings(requestSettings2));

        // dispatch scan 2 results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results2.getScanData());
        eventHandler2.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        client.verifyScanResultsReceived(results2.getScanData());
        client.verifySingleScanCompletedReceived();
        verify(mWifiMetrics, times(2)).incrementOneshotScanCount();
        verify(mWifiMetrics, times(2)).incrementScanReturnEntry(
                WifiMetricsProto.WifiLog.SCAN_SUCCESS, 1);
    }

    /**
     * Send a single scan request and then a second one not satisfied by the first before the first
     * completes. Verify that both are scheduled and succeed.
     * Validates that a high accuracy scan request is not satisfied by an ongoing low latency scan,
     * while any other low latency/low power scan request is satisfied.
     */
    @Test
    public void sendSingleScanRequestWhilePreviousScanRunningWithTypesThatDoesNotSatisfy()
            throws Exception {
        // Create identical scan requests other than the types being different.
        WifiScanner.ScanSettings requestSettings1 = createRequest(channelsToSpec(2412), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings1.type = WifiScanner.SCAN_TYPE_LOW_LATENCY;

        ScanResults results1 = ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412);

        WifiScanner.ScanSettings requestSettings2 = createRequest(channelsToSpec(2412), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings2.type = WifiScanner.SCAN_TYPE_HIGH_ACCURACY;

        ScanResults results2 = ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412);

        startServiceAndLoadDriver();

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        TestClient client = new TestClient();
        InOrder nativeOrder = inOrder(mWifiScannerImpl0);

        // Run scan 1
        client.sendSingleScanRequest(requestSettings1, null);

        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler1 = verifyStartSingleScan(nativeOrder,
                computeSingleScanNativeSettings(requestSettings1));
        client.verifySuccessfulResponse();

        // Queue scan 2 (will not run because previous is in progress)
        client.sendSingleScanRequest(requestSettings2, null);
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();

        // dispatch scan 1 results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results1.getScanData());
        eventHandler1.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        client.verifyScanResultsReceived(results1.getScanData());
        client.verifySingleScanCompletedReceived();

        // now that the first scan completed we expect the second one to start
        WifiNative.ScanEventHandler eventHandler2 = verifyStartSingleScan(nativeOrder,
                computeSingleScanNativeSettings(requestSettings2));

        // dispatch scan 2 results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results2.getScanData());
        eventHandler2.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        client.verifyScanResultsReceived(results2.getScanData());
        client.verifySingleScanCompletedReceived();
        verify(mWifiMetrics, times(2)).incrementOneshotScanCount();
        verify(mWifiMetrics, times(2)).incrementScanReturnEntry(
                WifiMetricsProto.WifiLog.SCAN_SUCCESS, 1);
    }


    /**
     * Send a single scan request and then two more before the first completes. Neither are
     * satisfied by the first scan. Verify that the first completes and the second two are merged.
     * Validates that a high accuracy scan is always preferred over the other types while merging.
     */
    @Test
    public void sendMultipleSingleScanRequestWhilePreviousScanRunning() throws Exception {
        WifiScanner.ScanSettings requestSettings1 = createRequest(channelsToSpec(2412), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings1.type = WifiScanner.SCAN_TYPE_LOW_LATENCY;

        WorkSource workSource1 = new WorkSource(1121);
        ScanResults results1 = ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412);

        WifiScanner.ScanSettings requestSettings2 = createRequest(channelsToSpec(2450, 5175), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings2.type = WifiScanner.SCAN_TYPE_HIGH_ACCURACY;

        WorkSource workSource2 = new WorkSource(Binder.getCallingUid()); // don't explicitly set
        ScanResults results2 =
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2450, 5175, 2450);

        WifiScanner.ScanSettings requestSettings3 = createRequest(channelsToSpec(5160), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings3.type = WifiScanner.SCAN_TYPE_LOW_POWER;

        // Let one of the WorkSources be a chained workSource.
        WorkSource workSource3 = new WorkSource();
        workSource3.createWorkChain()
                .addNode(2292, "tag1");
        ScanResults results3 =
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 5160, 5160, 5160, 5160);

        WifiNative.ScanSettings nativeSettings2and3 = createSingleScanNativeSettingsForChannels(
                WifiScanner.SCAN_TYPE_HIGH_ACCURACY, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                channelsToSpec(2450, 5175, 5160));
        ScanResults results2and3 =
                ScanResults.merge(WifiScanner.WIFI_BAND_UNSPECIFIED, results2, results3);
        WorkSource workSource2and3 = new WorkSource();
        workSource2and3.add(workSource2);
        workSource2and3.add(workSource3);


        startServiceAndLoadDriver();

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        TestClient client1 = new TestClient();
        TestClient client2 = new TestClient();
        TestClient client3 = new TestClient();
        InOrder nativeOrder = inOrder(mWifiScannerImpl0);
        // Run scan 1
        client1.sendSingleScanRequest(requestSettings1, workSource1);

        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler1 = verifyStartSingleScan(nativeOrder,
                computeSingleScanNativeSettings(requestSettings1));
        client1.verifySuccessfulResponse();
        verify(mBatteryStats).reportWifiScanStartedFromSource(eq(workSource1));

        // Queue scan 2 (will not run because previous is in progress)
        // uses uid of calling process
        client2.sendSingleScanRequest(requestSettings2, null);
        mLooper.dispatchAll();
        client2.verifySuccessfulResponse();

        // Queue scan 3 (will not run because previous is in progress)
        client3.sendSingleScanRequest(requestSettings3, workSource3);
        mLooper.dispatchAll();
        client3.verifySuccessfulResponse();

        // dispatch scan 1 results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results1.getScanData());
        eventHandler1.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        client1.verifyScanResultsReceived(results1.getScanData());
        client1.verifySingleScanCompletedReceived();
        verify(mBatteryStats).reportWifiScanStoppedFromSource(eq(workSource1));
        verify(mBatteryStats).reportWifiScanStartedFromSource(eq(workSource2and3));

        // now that the first scan completed we expect the second and third ones to start
        WifiNative.ScanEventHandler eventHandler2and3 = verifyStartSingleScan(nativeOrder,
                nativeSettings2and3);

        // dispatch scan 2 and 3 results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results2and3.getScanData());
        eventHandler2and3.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        verifyMultipleSingleScanResults(client2, results2, client3, results3);
        verify(mWifiMetrics, times(3)).incrementOneshotScanCount();
        verify(mWifiMetrics).incrementScanReturnEntry(
                WifiMetricsProto.WifiLog.SCAN_SUCCESS, 1);
        verify(mWifiMetrics).incrementScanReturnEntry(
                WifiMetricsProto.WifiLog.SCAN_SUCCESS, 2);

        verify(mBatteryStats).reportWifiScanStoppedFromSource(eq(workSource2and3));

        assertDumpContainsRequestLog("addSingleScanRequest");
        assertDumpContainsRequestLog("addSingleScanRequest");
        assertDumpContainsRequestLog("addSingleScanRequest");
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results1.getRawScanResults().length);
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results2.getRawScanResults().length);
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results3.getRawScanResults().length);
    }

    /**
     * Send a single scan request and then a second one satisfied by the first before the first
     * completes. Verify that only one scan is scheduled.
     * Validates that a low latency scan type request is satisfied by an ongoing high accuracy
     * scan.
     */
    @Test
    public void sendSingleScanRequestWhilePreviousScanRunningAndMergeIntoFirstScan()
            throws Exception {
        // Split by frequency to make it easier to determine which results each request is expecting
        ScanResults results24GHz =
                ScanResults.create(0, WifiScanner.WIFI_BAND_BOTH, 2412, 2412, 2412, 2450);
        ScanResults results5GHz =
                ScanResults.create(0, WifiScanner.WIFI_BAND_BOTH, 5160, 5160, 5175);
        ScanResults resultsBoth =
                ScanResults.merge(WifiScanner.WIFI_BAND_BOTH, results24GHz, results5GHz);

        WifiScanner.ScanSettings requestSettings1 = createRequest(
                WifiScanner.SCAN_TYPE_HIGH_ACCURACY, WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        ScanResults results1 = resultsBoth;

        WifiScanner.ScanSettings requestSettings2 = createRequest(
                WifiScanner.SCAN_TYPE_LOW_LATENCY, WifiScanner.WIFI_BAND_24_GHZ, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        ScanResults results2 = results24GHz;


        startServiceAndLoadDriver();

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        TestClient client = new TestClient();
        InOrder nativeOrder = inOrder(mWifiScannerImpl0);

        // Run scan 1
        client.sendSingleScanRequest(requestSettings1, null);

        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler = verifyStartSingleScan(nativeOrder,
                computeSingleScanNativeSettings(requestSettings1));
        client.verifySuccessfulResponse();

        // Queue scan 2 (will be folded into ongoing scan)
        client.sendSingleScanRequest(requestSettings2, null);
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();

        // dispatch scan 1 results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(resultsBoth.getScanData());
        eventHandler.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        verifyMultipleSingleScanResults(client, results1, client,
                results2);

        verify(mWifiMetrics, times(2)).incrementOneshotScanCount();
        verify(mWifiMetrics).incrementScanReturnEntry(
                WifiMetricsProto.WifiLog.SCAN_SUCCESS, 2);
    }

    /**
     * Send a single scan request and then two more before the first completes, one of which is
     * satisfied by the first scan. Verify that the first two complete together the second scan is
     * just for the other scan.
     * Validates that a high accuracy scan request is not satisfied by an ongoing low latency scan,
     * while any other low latency/low power scan request is satisfied.
     */
    @Test
    public void sendMultipleSingleScanRequestWhilePreviousScanRunningAndMergeOneIntoFirstScan()
            throws Exception {
        // Split by frequency to make it easier to determine which results each request is expecting
        ScanResults results2412 =
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412, 2412, 2412);
        ScanResults results2450 = ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2450);
        ScanResults results1and3 =
                ScanResults.merge(WifiScanner.WIFI_BAND_UNSPECIFIED, results2412, results2450);

        WifiScanner.ScanSettings requestSettings1 = createRequest(channelsToSpec(2412, 2450), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings1.type = WifiScanner.SCAN_TYPE_LOW_LATENCY;

        WorkSource workSource1 = new WorkSource(1121);
        ScanResults results1 = results1and3;

        WifiScanner.ScanSettings requestSettings2 = createRequest(channelsToSpec(2450, 5175), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings2.type = WifiScanner.SCAN_TYPE_HIGH_ACCURACY;

        WorkSource workSource2 = new WorkSource(Binder.getCallingUid()); // don't explicitly set
        ScanResults results2 = ScanResults.create(0, 2450, 5175, 2450);

        WifiScanner.ScanSettings requestSettings3 = createRequest(channelsToSpec(2412), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings3.type = WifiScanner.SCAN_TYPE_LOW_POWER;

        WorkSource workSource3 = new WorkSource(2292);
        ScanResults results3 = results2412;

        startServiceAndLoadDriver();

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        TestClient client = new TestClient();
        InOrder nativeOrder = inOrder(mWifiScannerImpl0);

        // Run scan 1
        client.sendSingleScanRequest(requestSettings1, workSource1);

        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler1 = verifyStartSingleScan(nativeOrder,
                computeSingleScanNativeSettings(requestSettings1));
        client.verifySuccessfulResponse();
        verify(mBatteryStats).reportWifiScanStartedFromSource(eq(workSource1));

        // Queue scan 2 (will not run because previous is in progress)
        // uses uid of calling process
        client.sendSingleScanRequest(requestSettings2, null);
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();

        // Queue scan 3 (will be merged into the active scan)
        client.sendSingleScanRequest(requestSettings3, workSource3);
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();

        // dispatch scan 1 results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results1and3.getScanData());
        eventHandler1.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        verifyMultipleSingleScanResults(client, results1, client,
                results3);
        // only the requests know at the beginning of the scan get blamed
        verify(mBatteryStats).reportWifiScanStoppedFromSource(eq(workSource1));
        verify(mBatteryStats).reportWifiScanStartedFromSource(eq(workSource2));

        // now that the first scan completed we expect the second and third ones to start
        WifiNative.ScanEventHandler eventHandler2 = verifyStartSingleScan(nativeOrder,
                computeSingleScanNativeSettings(requestSettings2));

        // dispatch scan 2 and 3 results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results2.getScanData());
        eventHandler2.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();

        client.verifyScanResultsReceived(results2.getScanData());
        client.verifySingleScanCompletedReceived();
        verify(mWifiMetrics, times(3)).incrementOneshotScanCount();
        verify(mWifiMetrics).incrementScanReturnEntry(
                WifiMetricsProto.WifiLog.SCAN_SUCCESS, 2);
        verify(mWifiMetrics).incrementScanReturnEntry(
                WifiMetricsProto.WifiLog.SCAN_SUCCESS, 1);

        verify(mBatteryStats).reportWifiScanStoppedFromSource(eq(workSource2));

        assertDumpContainsRequestLog("addSingleScanRequest");
        assertDumpContainsRequestLog("addSingleScanRequest");
        assertDumpContainsRequestLog("addSingleScanRequest");
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results1.getRawScanResults().length);
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results2.getRawScanResults().length);
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results3.getRawScanResults().length);
    }

    @Test
    public void sendMultipleSingleScanRequestWithVendorIesWhilePreviousScanRunningAndMerge()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        ScanResults results2412 =
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412, 2412, 2412);
        ScanResults results2450 = ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2450);
        ScanResults results5175 = ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 5175);
        ScanResults results1and3 =
                ScanResults.merge(WifiScanner.WIFI_BAND_UNSPECIFIED, results2412, results2450);
        ScanResults results2and4 =
                ScanResults.merge(WifiScanner.WIFI_BAND_UNSPECIFIED, results2450, results5175);

        WifiScanner.ScanSettings requestSettings1 = createRequest(channelsToSpec(2412, 2450), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings1.type = WifiScanner.SCAN_TYPE_LOW_LATENCY;
        WorkSource workSource1 = new WorkSource(1121);
        ScanResults results1 = results1and3;

        WifiScanner.ScanSettings requestSettings2 = createRequest(channelsToSpec(2450, 5175), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings2.type = WifiScanner.SCAN_TYPE_HIGH_ACCURACY;
        List<ScanResult.InformationElement> vendorIesList2 = new ArrayList<>();
        ScanResult.InformationElement vendorIe21 = new ScanResult.InformationElement(221, 0,
                new byte[]{0x00, 0x50, (byte) 0xf2, 0x08, 0x11, 0x22, 0x33});
        ScanResult.InformationElement vendorIe22 = new ScanResult.InformationElement(221, 0,
                new byte[255]);
        vendorIesList2.add(vendorIe21);
        vendorIesList2.add(vendorIe22);
        requestSettings2.setVendorIes(vendorIesList2);
        WorkSource workSource2 = new WorkSource(Binder.getCallingUid());
        ScanResults results2 = results2and4;

        WifiScanner.ScanSettings requestSettings3 = createRequest(channelsToSpec(2412), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings3.type = WifiScanner.SCAN_TYPE_LOW_POWER;
        WorkSource workSource3 = new WorkSource(2292);
        ScanResults results3 = results2412;

        WifiScanner.ScanSettings requestSettings4 = createRequest(channelsToSpec(5175), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings4.type = WifiScanner.SCAN_TYPE_HIGH_ACCURACY;
        List<ScanResult.InformationElement> vendorIesList4 = new ArrayList<>();
        ScanResult.InformationElement vendorIe41 = new ScanResult.InformationElement(221, 0,
                new byte[]{0x00, 0x50, (byte) 0xf2, 0x08, (byte) 0xaa, (byte) 0xbb, (byte) 0xcc});
        ScanResult.InformationElement vendorIe42 = new ScanResult.InformationElement(221, 0,
                new byte[238]);
        vendorIesList4.add(vendorIe41);
        vendorIesList4.add(vendorIe42);
        requestSettings4.setVendorIes(vendorIesList4);
        WorkSource workSource4 = new WorkSource(Binder.getCallingUid());
        ScanResults results4 = results5175;

        startServiceAndLoadDriver();
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        TestClient client = new TestClient();
        InOrder nativeOrder = inOrder(mWifiScannerImpl0);
        client.sendSingleScanRequest(requestSettings1, workSource1);
        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler1 = verifyStartSingleScan(nativeOrder,
                computeSingleScanNativeSettings(requestSettings1));
        client.verifySuccessfulResponse();
        verify(mBatteryStats).reportWifiScanStartedFromSource(eq(workSource1));

        // Queue scan 2 (will not run because previous is in progress)
        client.sendSingleScanRequest(requestSettings2, null);
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();

        // Queue scan 3 (will be merged into the active scan)
        client.sendSingleScanRequest(requestSettings3, workSource3);
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();

        // Queue scan 4 (will be merged into the pending scan)
        client.sendSingleScanRequest(requestSettings4, null);
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();

        // dispatch scan 1 and 3 results
        when(mWifiScannerImpl0.getLatestSingleScanResults()).thenReturn(results1and3.getScanData());
        eventHandler1.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        verifyMultipleSingleScanResults(client, results1, client, results3);
        // only the requests know at the beginning of the scan get blamed
        verify(mBatteryStats).reportWifiScanStoppedFromSource(eq(workSource1));
        verify(mBatteryStats).reportWifiScanStartedFromSource(eq(workSource2));

        // now that the first scan completed we expect the second and fourth ones to start
        // vendorIes had been merged
        vendorIesList2.add(vendorIe41);
        requestSettings2.setVendorIes(vendorIesList2);
        WifiNative.ScanEventHandler eventHandler2 = verifyStartSingleScan(nativeOrder,
                computeSingleScanNativeSettings(requestSettings2));

        // dispatch scan 2 and 4 results
        when(mWifiScannerImpl0.getLatestSingleScanResults()).thenReturn(results2and4.getScanData());
        eventHandler2.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        verifyMultipleSingleScanResults(client, results2, client, results4);
        verify(mWifiMetrics, times(4)).incrementOneshotScanCount();
        verify(mWifiMetrics, times(2)).incrementScanReturnEntry(
                WifiMetricsProto.WifiLog.SCAN_SUCCESS, 2);
        verify(mBatteryStats).reportWifiScanStoppedFromSource(eq(workSource2));

        assertDumpContainsRequestLog("addSingleScanRequest");
        assertDumpContainsRequestLog("addSingleScanRequest");
        assertDumpContainsRequestLog("addSingleScanRequest");
        assertDumpContainsRequestLog("addSingleScanRequest");
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results1.getRawScanResults().length);
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results2.getRawScanResults().length);
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results3.getRawScanResults().length);
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results4.getRawScanResults().length);
    }

    /**
     * Verify that WifiService provides a way to get the most recent SingleScan results.
     */
    @Test
    public void retrieveSingleScanResults() throws Exception {
        WifiScanner.ScanSettings requestSettings =
                createRequest(WifiScanner.WIFI_BAND_ALL, 0, 0, 20,
                        WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        ScanResults expectedResults =
                ScanResults.create(0, WifiScanner.WIFI_BAND_ALL, 2412, 5160, 5175);
        doSuccessfulSingleScan(requestSettings,
                               computeSingleScanNativeSettings(requestSettings),
                               expectedResults);

        mLooper.startAutoDispatch();
        List<ScanResult> results = mWifiScanningServiceImpl.getSingleScanResults(TEST_PACKAGE_NAME,
                null);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertEquals(results.size(), expectedResults.getRawScanResults().length);

        // Make sure that we logged the scan results in the dump method.
        String serviceDump = dumpService();
        Pattern logLineRegex = Pattern.compile("Latest scan results:");
        assertTrue("dump did not contain Latest scan results: " + serviceDump + "\n",
                logLineRegex.matcher(serviceDump).find());
    }

    /**
     * Verify that WifiService provides a way to get the most recent SingleScan results even when
     * they are empty.
     */
    @Test
    public void retrieveSingleScanResultsEmpty() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        doSuccessfulSingleScan(requestSettings, computeSingleScanNativeSettings(requestSettings),
                ScanResults.create(0, WifiScanner.WIFI_BAND_BOTH, new int[0]));

        mLooper.startAutoDispatch();
        List<ScanResult> results = mWifiScanningServiceImpl.getSingleScanResults(TEST_PACKAGE_NAME,
                null);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertEquals(results.size(), 0);
    }

    /**
     * Verify that WifiService will return empty SingleScan results if a scan has not been
     * performed.
     */
    @Test
    public void retrieveSingleScanResultsBeforeAnySingleScans() throws Exception {
        startServiceAndLoadDriver();

        mLooper.startAutoDispatch();
        List<ScanResult> results = mWifiScanningServiceImpl.getSingleScanResults(TEST_PACKAGE_NAME,
                null);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertEquals(results.size(), 0);
    }

    /**
     * Verify that the newest full scan results are returned by WifiService.getSingleScanResults.
     */
    @Test
    public void retrieveMostRecentFullSingleScanResults() throws Exception {
        int scanBand = WifiScanner.WIFI_BAND_ALL & ~WifiScanner.WIFI_BAND_5_GHZ_DFS_ONLY;
        WifiScanner.ScanSettings requestSettings = createRequest(scanBand, 0, 0, 20,
                WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        ScanResults expectedResults = ScanResults.create(0, scanBand, 2412, 5160, 5175);
        doSuccessfulSingleScan(requestSettings,
                               computeSingleScanNativeSettings(requestSettings),
                               expectedResults);

        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0);
        mLooper.startAutoDispatch();
        List<ScanResult> results = mWifiScanningServiceImpl.getSingleScanResults(TEST_PACKAGE_NAME,
                null);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertEquals(results.size(), expectedResults.getRawScanResults().length);

        // now update with a new scan that only has one result
        ScanResults expectedSingleResult = ScanResults.create(0, scanBand, 5160);
        client.sendSingleScanRequest(requestSettings, null);

        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler = verifyStartSingleScan(order,
                computeSingleScanNativeSettings(requestSettings));
        client.verifySuccessfulResponse();

        // dispatch scan 2 results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(expectedSingleResult.getScanData());
        eventHandler.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        client.verifyScanResultsReceived(expectedSingleResult.getScanData());
        client.verifySingleScanCompletedReceived();

        mLooper.startAutoDispatch();
        List<ScanResult> results2 = mWifiScanningServiceImpl.getSingleScanResults(TEST_PACKAGE_NAME,
                null);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertEquals(results2.size(), expectedSingleResult.getRawScanResults().length);
    }

    /**
     * Verify that the newest partial scan results are not returned by
     * WifiService.getSingleScanResults.
     */
    @Test
    public void doesNotRetrieveMostRecentPartialSingleScanResults() throws Exception {
        int scanBand = WifiScanner.WIFI_BAND_ALL & ~WifiScanner.WIFI_BAND_5_GHZ_DFS_ONLY;
        WifiScanner.ScanSettings fullRequestSettings = createRequest(scanBand, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        ScanResults expectedFullResults =
                ScanResults.create(0, scanBand, 2412, 5160, 5175);
        doSuccessfulSingleScan(fullRequestSettings,
                computeSingleScanNativeSettings(fullRequestSettings),
                expectedFullResults);

        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0);
        verifyStartSingleScan(order, computeSingleScanNativeSettings(fullRequestSettings));
        mLooper.startAutoDispatch();
        List<ScanResult> results = mWifiScanningServiceImpl.getSingleScanResults(TEST_PACKAGE_NAME,
                TEST_FEATURE_ID);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertEquals(results.size(), expectedFullResults.getRawScanResults().length);


        // now update with a new scan that only has one result
        WifiScanner.ScanSettings partialRequestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH,
                0, 0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        ScanResults expectedPartialResults =
                ScanResults.create(0, WifiScanner.WIFI_BAND_5_GHZ, 5160);
        client.sendSingleScanRequest(partialRequestSettings, null);

        mLooper.dispatchAll();
        client.verifySuccessfulResponse();
        WifiNative.ScanEventHandler eventHandler = verifyStartSingleScan(order,
                computeSingleScanNativeSettings(partialRequestSettings));

        // dispatch scan 2 results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(expectedPartialResults.getScanData());
        eventHandler.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        client.verifyScanResultsReceived(expectedPartialResults.getScanData());
        client.verifySingleScanCompletedReceived();
        mLooper.startAutoDispatch();
        List<ScanResult> results2 = mWifiScanningServiceImpl.getSingleScanResults(TEST_PACKAGE_NAME,
                null);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertEquals(results2.size(), expectedFullResults.getRawScanResults().length);
    }

    /**
     * Verify that the scan results returned by WifiService.getSingleScanResults are not older
     * than {@link com.android.server.wifi.scanner.WifiScanningServiceImpl
     * .WifiSingleScanStateMachine#CACHED_SCAN_RESULTS_MAX_AGE_IN_MILLIS}.
     */
    @Test
    public void doesNotRetrieveStaleScanResultsFromLastFullSingleScan() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_ALL, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        ScanResults scanResults =
                ScanResults.create(0, WifiScanner.WIFI_BAND_ALL, 2412, 5160, 5175);

        // Out of the 3 scan results, modify the timestamp of 2 of them to be within the expiration
        // age and 1 out of it.
        long currentTimeInMillis = CACHED_SCAN_RESULTS_MAX_AGE_IN_MILLIS * 2;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeInMillis);
        scanResults.getRawScanResults()[0].timestamp = (currentTimeInMillis - 1) * 1000;
        scanResults.getRawScanResults()[1].timestamp = (currentTimeInMillis - 2) * 1000;
        scanResults.getRawScanResults()[2].timestamp =
                (currentTimeInMillis - CACHED_SCAN_RESULTS_MAX_AGE_IN_MILLIS) * 1000;
        List<ScanResult> expectedResults = List.of(
                scanResults.getRawScanResults()[0],
                scanResults.getRawScanResults()[1]);

        doSuccessfulSingleScan(requestSettings,
                computeSingleScanNativeSettings(requestSettings), scanResults);

        mLooper.startAutoDispatch();
        List<ScanResult> results = mWifiScanningServiceImpl.getSingleScanResults(TEST_PACKAGE_NAME,
                null);
        mLooper.stopAutoDispatchAndIgnoreExceptions();

        assertScanResultsEquals(expectedResults.toArray(new ScanResult[expectedResults.size()]),
                results.toArray(new ScanResult[results.size()]));
    }

    /**
     * Cached scan results should be cleared after the driver is unloaded.
     */
    @Test
    public void validateScanResultsClearedAfterDriverUnloaded() throws Exception {
        WifiScanner.ScanSettings requestSettings =
                createRequest(WifiScanner.WIFI_BAND_ALL,
                              0, 0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        ScanResults expectedResults = ScanResults.create(
                0, WifiScanner.WIFI_BAND_ALL, 2412, 5160, 5175);
        doSuccessfulSingleScan(requestSettings,
                               computeSingleScanNativeSettings(requestSettings),
                               expectedResults);
        mLooper.startAutoDispatch();
        List<ScanResult> results = mWifiScanningServiceImpl.getSingleScanResults(TEST_PACKAGE_NAME,
                null);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertEquals(results.size(), expectedResults.getRawScanResults().length);

        // disable wifi
        mWifiScanningServiceImpl.setScanningEnabled(false, 0, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();
        mLooper.dispatchAll();
        // Now get scan results again. The returned list should be empty since we
        // clear the cache when exiting the DriverLoaded state.
        mLooper.startAutoDispatch();
        List<ScanResult> results2 = mWifiScanningServiceImpl.getSingleScanResults(TEST_PACKAGE_NAME,
                TEST_FEATURE_ID);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        assertEquals(0, results2.size());
    }

    /**
     * Register a single scan listener and do a single scan
     */
    @Test
    public void registerScanListener() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        WifiNative.ScanSettings nativeSettings = computeSingleScanNativeSettings(requestSettings);
        ScanResults results = ScanResults.create(0, 2412, 5160, 5175);

        startServiceAndLoadDriver();

        TestClient client = new TestClient();
        TestClient client1 = new TestClient();
        InOrder order = inOrder(client.listener, client1.listener, mWifiScannerImpl0);

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        client.registerScanListener();
        client.verifySuccessfulResponse();

        client1.sendSingleScanRequest(requestSettings, null);

        mLooper.dispatchAll();
        client1.verifySuccessfulResponse();
        WifiNative.ScanEventHandler eventHandler = verifyStartSingleScan(order, nativeSettings);

        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results.getRawScanData());
        eventHandler.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        client1.verifyScanResultsReceived(results.getScanData());
        client1.verifySingleScanCompletedReceived();
        client.verifyScanResultsReceived(results.getScanData());
        client1.verifyUnlinkedToDeath();
        verifyNoMoreInteractions(client.listener);
        verifyNoMoreInteractions(client1.listener);
        assertDumpContainsRequestLog("registerScanListener");
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results.getScanData().getResults().length);
    }

    /**
     * Register a single scan listener and do a single scan
     */
    @Test
    public void deregisterScanListener() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        WifiNative.ScanSettings nativeSettings = computeSingleScanNativeSettings(requestSettings);
        ScanResults results = ScanResults.create(0, 2412, 5160, 5175);


        startServiceAndLoadDriver();

        TestClient client = new TestClient();
        TestClient client1 = new TestClient();
        InOrder order = inOrder(client.listener, client1.listener, mWifiScannerImpl0);

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        client.registerScanListener();
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();

        client1.sendSingleScanRequest(requestSettings, null);

        mLooper.dispatchAll();
        client1.verifySuccessfulResponse();
        WifiNative.ScanEventHandler eventHandler = verifyStartSingleScan(order, nativeSettings);

        client.deregisterScanListener();
        mLooper.dispatchAll();

        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results.getRawScanData());
        eventHandler.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        client1.verifyScanResultsReceived(results.getScanData());
        client1.verifySingleScanCompletedReceived();
        client.verifyUnlinkedToDeath();
        client1.verifyUnlinkedToDeath();
        verifyNoMoreInteractions(client.listener);
        verifyNoMoreInteractions(client1.listener);

        assertDumpContainsRequestLog("registerScanListener");
        assertDumpContainsRequestLog("deregisterScanListener");
    }

    /**
     * Send a single scan request and then two more before the first completes. Neither are
     * satisfied by the first scan. Verify that the first completes and the second two are merged.
     */
    @Test
    public void scanListenerReceivesAllResults() throws Exception {
        WifiScanner.ScanSettings requestSettings1 = createRequest(channelsToSpec(2412), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        ScanResults results1 = ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412);

        WifiScanner.ScanSettings requestSettings2 = createRequest(channelsToSpec(2450, 5175), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        ScanResults results2 =
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2450, 5175, 2450);

        WifiScanner.ScanSettings requestSettings3 = createRequest(channelsToSpec(5160), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        ScanResults results3 =
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 5160, 5160, 5160, 5160);

        WifiNative.ScanSettings nativeSettings2and3 = createSingleScanNativeSettingsForChannels(
                WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN, channelsToSpec(2450, 5175, 5160));
        ScanResults results2and3 =
                ScanResults.merge(WifiScanner.WIFI_BAND_UNSPECIFIED, results2, results3);

        startServiceAndLoadDriver();

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        TestClient client = new TestClient();
        TestClient client1 = new TestClient();
        TestClient client2 = new TestClient();
        TestClient client3 = new TestClient();
        InOrder nativeOrder = inOrder(mWifiScannerImpl0);

        // Run scan 1
        client1.sendSingleScanRequest(requestSettings1, null);

        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler1 = verifyStartSingleScan(nativeOrder,
                computeSingleScanNativeSettings(requestSettings1));
        client1.verifySuccessfulResponse();

        // Queue scan 2 (will not run because previous is in progress)
        client2.sendSingleScanRequest(requestSettings2, null);
        mLooper.dispatchAll();
        client2.verifySuccessfulResponse();

        // Queue scan 3 (will not run because previous is in progress)
        client3.sendSingleScanRequest(requestSettings3, null);
        mLooper.dispatchAll();
        client3.verifySuccessfulResponse();

        // Register scan listener
        client.registerScanListener();
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();

        // dispatch scan 1 results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results1.getScanData());
        eventHandler1.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        client1.verifyScanResultsReceived(results1.getScanData());
        client1.verifySingleScanCompletedReceived();
        client.verifyScanResultsReceived(results1.getScanData());

        // now that the first scan completed we expect the second and third ones to start
        WifiNative.ScanEventHandler eventHandler2and3 = verifyStartSingleScan(nativeOrder,
                nativeSettings2and3);

        // dispatch scan 2 and 3 results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results2and3.getScanData());
        eventHandler2and3.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        verifyMultipleSingleScanResults(client2, results2, client3, results3);
        client.verifyScanResultsReceived(results2and3.getScanData());

        assertDumpContainsRequestLog("registerScanListener");
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results1.getRawScanResults().length);
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results2and3.getRawScanResults().length);
    }

    @Test
    public void rejectSingleScanRequestWhenScannerGetIfaceNameFails() throws Exception {
        // Failed to get client interface name.
        when(mWifiNative.getClientInterfaceNames()).thenReturn(new ArraySet<>());

        startServiceAndLoadDriver();

        TestClient client = new TestClient();

        client.sendSingleScanRequest(generateValidScanSettings(), null);
        mLooper.dispatchAll();
        client.verifyFailedResponse(WifiScanner.REASON_UNSPECIFIED,
                "not available");
    }

    @Test
    public void rejectSingleScanRequestWhenScannerImplCreateFails() throws Exception {
        // Fail scanner impl creation.
        when(mWifiScannerImplFactory.create(any(), any(), any(), any())).thenReturn(null);

        startServiceAndLoadDriver();

        TestClient client = new TestClient();
        client.sendSingleScanRequest(generateValidScanSettings(), null);
        mLooper.dispatchAll();
        client.verifyFailedResponse(WifiScanner.REASON_UNSPECIFIED,
                "not available");
    }

    private void doSuccessfulBackgroundScan(WifiScanner.ScanSettings requestSettings,
            WifiNative.ScanSettings nativeSettings) throws Exception  {
        startServiceAndLoadDriver();

        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0);

        when(mWifiScannerImpl0.startBatchedScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        client.sendBackgroundScanRequest(requestSettings, null);
        client.verifyLinkedToDeath();
        mLooper.dispatchAll();
        verifyStartBackgroundScan(order, nativeSettings);
        client.verifySuccessfulResponse();
        verifyNoMoreInteractions(client.listener);
        assertDumpContainsRequestLog("addBackgroundScanRequest");
    }

    /**
     * Do a background scan for a band and verify that it is successful.
     */
    @Test
    public void sendBackgroundScanBandRequest() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 30000,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        WifiNative.ScanSettings nativeSettings = new NativeScanSettingsBuilder()
                .withBasePeriod(30000)
                .withMaxApPerScan(MAX_AP_PER_SCAN)
                .withMaxScansToCache(BackgroundScanScheduler.DEFAULT_MAX_SCANS_TO_BATCH)
                .addBucketWithBand(30000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_BOTH)
                .build();
        doSuccessfulBackgroundScan(requestSettings, nativeSettings);
        verify(mWifiMetrics).incrementBackgroundScanCount();
    }

    /**
     * Do a background scan for a list of channels and verify that it is successful.
     */
    @Test
    public void sendBackgroundScanChannelsRequest() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(channelsToSpec(5160), 30000,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        WifiNative.ScanSettings nativeSettings = new NativeScanSettingsBuilder()
                .withBasePeriod(30000)
                .withMaxApPerScan(MAX_AP_PER_SCAN)
                .withMaxScansToCache(BackgroundScanScheduler.DEFAULT_MAX_SCANS_TO_BATCH)
                .addBucketWithChannels(30000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN, 5160)
                .build();
        doSuccessfulBackgroundScan(requestSettings, nativeSettings);
    }

    private Pair<WifiScanner.ScanSettings, WifiNative.ScanSettings> createScanSettingsForHwPno()
            throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(
                channelsToSpec(0, 2412, 5160, 5175), 30000, 0, 20,
                WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        WifiNative.ScanSettings nativeSettings = new NativeScanSettingsBuilder()
                .withBasePeriod(30000)
                .withMaxApPerScan(MAX_AP_PER_SCAN)
                .withMaxScansToCache(BackgroundScanScheduler.DEFAULT_MAX_SCANS_TO_BATCH)
                .addBucketWithChannels(30000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        0, 2412, 5160, 5175)
                .build();
        return Pair.create(requestSettings, nativeSettings);
    }

    private Pair<WifiScanner.PnoSettings, WifiNative.PnoSettings> createPnoSettings(
            ScanResults results)
            throws Exception {
        WifiScanner.PnoSettings requestPnoSettings = new WifiScanner.PnoSettings();
        requestPnoSettings.networkList =
                new WifiScanner.PnoSettings.PnoNetwork[results.getRawScanResults().length];
        int i = 0;
        for (ScanResult scanResult : results.getRawScanResults()) {
            requestPnoSettings.networkList[i++] =
                    new WifiScanner.PnoSettings.PnoNetwork(scanResult.SSID);
        }

        WifiNative.PnoSettings nativePnoSettings = new WifiNative.PnoSettings();
        nativePnoSettings.min5GHzRssi = requestPnoSettings.min5GHzRssi;
        nativePnoSettings.min24GHzRssi = requestPnoSettings.min24GHzRssi;
        nativePnoSettings.min6GHzRssi = requestPnoSettings.min6GHzRssi;
        nativePnoSettings.isConnected = requestPnoSettings.isConnected;
        nativePnoSettings.networkList =
                new WifiNative.PnoNetwork[requestPnoSettings.networkList.length];
        for (i = 0; i < requestPnoSettings.networkList.length; i++) {
            nativePnoSettings.networkList[i] = new WifiNative.PnoNetwork();
            nativePnoSettings.networkList[i].ssid = requestPnoSettings.networkList[i].ssid;
            nativePnoSettings.networkList[i].flags = requestPnoSettings.networkList[i].flags;
            nativePnoSettings.networkList[i].auth_bit_field =
                    requestPnoSettings.networkList[i].authBitField;
        }
        return Pair.create(requestPnoSettings, nativePnoSettings);
    }

    private ScanResults createScanResultsForPno() {
        return ScanResults.create(0, 2412, 5160, 5175);
    }

    private WifiNative.PnoEventHandler verifyHwPnoForImpl(WifiScannerImpl impl, InOrder order,
            WifiNative.PnoSettings expected) {
        ArgumentCaptor<WifiNative.PnoSettings> pnoSettingsCaptor =
                ArgumentCaptor.forClass(WifiNative.PnoSettings.class);
        ArgumentCaptor<WifiNative.PnoEventHandler> pnoEventHandlerCaptor =
                ArgumentCaptor.forClass(WifiNative.PnoEventHandler.class);
        order.verify(impl).setHwPnoList(pnoSettingsCaptor.capture(),
                pnoEventHandlerCaptor.capture());
        assertNativePnoSettingsEquals(expected, pnoSettingsCaptor.getValue());
        return pnoEventHandlerCaptor.getValue();
    }

    private void expectSuccessfulBackgroundScan(InOrder order,
            WifiNative.ScanSettings nativeSettings, ScanResults results) {
        when(mWifiScannerImpl0.startBatchedScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);
        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler = verifyStartBackgroundScan(order, nativeSettings);
        WifiScanner.ScanData[] scanDatas = new WifiScanner.ScanData[1];
        scanDatas[0] = results.getScanData();
        for (ScanResult fullScanResult : results.getRawScanResults()) {
            eventHandler.onFullScanResult(fullScanResult, 0);
        }
        when(mWifiScannerImpl0.getLatestBatchedScanResults(anyBoolean())).thenReturn(scanDatas);
        eventHandler.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
        mLooper.dispatchAll();
    }

    private void expectHwPnoScan(InOrder order, IWifiScannerListener.Stub listener,
            WifiNative.PnoSettings nativeSettings, ScanResults results) throws Exception {
        when(mWifiScannerImpl0.isHwPnoSupported(anyBoolean())).thenReturn(true);

        when(mWifiScannerImpl0.setHwPnoList(any(WifiNative.PnoSettings.class),
                any(WifiNative.PnoEventHandler.class))).thenReturn(true);
        mLooper.dispatchAll();
        WifiNative.PnoEventHandler eventHandler =
                verifyHwPnoForImpl(mWifiScannerImpl0, order, nativeSettings);
        verify(listener).onSuccess();
        eventHandler.onPnoNetworkFound(results.getRawScanResults());
        mLooper.dispatchAll();
    }

    private void expectHwPnoScanOnImpls(InOrder order, IWifiScannerListener.Stub listener,
            WifiNative.PnoSettings nativeSettings,
            @Nullable ScanResults resultsForImpl0, @Nullable ScanResults resultsForImpl1)
            throws Exception {
        when(mWifiScannerImpl0.isHwPnoSupported(anyBoolean())).thenReturn(true);
        when(mWifiScannerImpl1.isHwPnoSupported(anyBoolean())).thenReturn(true);

        when(mWifiScannerImpl0.setHwPnoList(any(WifiNative.PnoSettings.class),
                any(WifiNative.PnoEventHandler.class))).thenReturn(true);
        when(mWifiScannerImpl1.setHwPnoList(any(WifiNative.PnoSettings.class),
                any(WifiNative.PnoEventHandler.class))).thenReturn(true);
        mLooper.dispatchAll();
        WifiNative.PnoEventHandler eventHandler0 =
                verifyHwPnoForImpl(mWifiScannerImpl0, order, nativeSettings);
        WifiNative.PnoEventHandler eventHandler1 =
                verifyHwPnoForImpl(mWifiScannerImpl1, order, nativeSettings);
        verify(listener).onSuccess();
        if (resultsForImpl0 != null) {
            eventHandler0.onPnoNetworkFound(resultsForImpl0.getRawScanResults());
        } else if (resultsForImpl1 != null) {
            eventHandler1.onPnoNetworkFound(resultsForImpl1.getRawScanResults());
        }
        mLooper.dispatchAll();
    }

    private void expectSwPnoScan(InOrder order, WifiScannerImpl wifiScannerImpl) {
        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler = verifyStartSwPnoForImpl(wifiScannerImpl, order);
        eventHandler.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
        mLooper.dispatchAll();
    }

    /**
     * Tests wificond SW PNO scan. This ensures that the PNO scan results are plumbed back to the
     * client as a PNO network found event. Furthermore, verify that a second PNO request is
     * neglected while the previous one is being processed.
     */
    @Test
    public void testSuccessfulSwPnoScan() throws Exception {
        startServiceAndLoadDriver();
        mLooper.dispatchAll();

        mResources.setInteger(R.integer.config_wifiSwPnoFastTimerMs, 10);
        mResources.setInteger(R.integer.config_wifiSwPnoSlowTimerMs, 60);

        when(mWifiScannerImpl0.isHwPnoSupported(anyBoolean())).thenReturn(false);
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);
        when(mDeviceConfigFacade.isSoftwarePnoEnabled()).thenReturn(true);

        TestClient client = new TestClient();

        InOrder order = inOrder(client.listener, mWifiScannerImpl0);

        ScanResults scanResults = createScanResultsForPno();
        Pair<WifiScanner.ScanSettings, WifiNative.ScanSettings> scanSettings =
                createScanSettingsForHwPno();
        Pair<WifiScanner.PnoSettings, WifiNative.PnoSettings> pnoSettings =
                createPnoSettings(scanResults);

        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(scanResults.getScanData());

        client.sendPnoScanRequest(scanSettings.first, pnoSettings.first);
        mLooper.dispatchAll();
        order.verify(client.listener).onSuccess();
        expectSwPnoScan(order, mWifiScannerImpl0);

        //Verify that a second PNO request is neglected while a previous one is being processed
        client.sendPnoScanRequest(scanSettings.first, pnoSettings.first);
        mLooper.dispatchAll();
        order.verify(client.listener).onFailure(eq(WifiScanner.REASON_DUPLICATE_REQEUST),
                anyString());
    }

    void mockBroadcastReceived(Intent intent) {
        mSwPnoIterationCount++;
        mSwPnoBroadcastReceiver.onReceive(mContext, intent);
        mLooper.dispatchAll();
    }

    void registerSwPnoBroadcastReceiver(BroadcastReceiver br) {
        mSwPnoBroadcastReceiver = br;
    }

    /**
     * Tests that the Sw PNO state machine correctly iterates through all the scheduled PNO scans.
     */
    @Test
    public void testSwPnoScanIterations() throws Exception {
        mResources.setInteger(R.integer.config_wifiSwPnoFastTimerMs, 3000);
        mResources.setInteger(R.integer.config_wifiSwPnoSlowTimerMs, 2000);
        mSwPnoIterationCount = 0;

        startServiceAndLoadDriver();
        mLooper.dispatchAll();

        // During the first mSwPnoMobilityIterations + mSwPnoFastIterations, the only timer
        // scheduled is the exact one, that broadcasts a SW_PNO_ALARM_INTENT_ACTION when it fires
        // Afterwards, both exact and inexact timers can fire for the remaining mSwPnoSlowIterations
        // iterations. We simulate the randomness of the order in which the timers are fired.
        doAnswer(inv -> {
            if (mSwPnoIterationCount < (mSwPnoMobilityIterations + mSwPnoFastIterations)) {
                mSwPnoIntentsQueue.add(new Intent(SW_PNO_ALARM_INTENT_ACTION));
            } else {
                mSwPnoIntentsQueue.add(new Intent(SW_PNO_UPPER_BOUND_ALARM_INTENT_ACTION));
            }
            return null;
        }).when(mAlarmManager.getAlarmManager()).setExactAndAllowWhileIdle(
                eq(AlarmManager.ELAPSED_REALTIME_WAKEUP), anyLong(),
                any(PendingIntent.class));

        doAnswer(inv -> {
            mSwPnoIntentsQueue.add(new Intent(SW_PNO_ALARM_INTENT_ACTION));
            return null;
        }).when(mAlarmManager.getAlarmManager()).setWindow(
                eq(AlarmManager.ELAPSED_REALTIME), anyLong(), anyLong(),
                any(PendingIntent.class));

        when(mWifiScannerImpl0.isHwPnoSupported(anyBoolean())).thenReturn(false);
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);
        when(mDeviceConfigFacade.isSoftwarePnoEnabled()).thenReturn(true);

        TestClient client = new TestClient();

        InOrder order = inOrder(client.listener, mWifiScannerImpl0);

        ScanResults scanResults = createScanResultsForPno();
        Pair<WifiScanner.ScanSettings, WifiNative.ScanSettings> scanSettings =
                createScanSettingsForHwPno();
        Pair<WifiScanner.PnoSettings, WifiNative.PnoSettings> pnoSettings =
                createPnoSettings(scanResults);

        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(scanResults.getScanData());

        client.sendPnoScanRequest(scanSettings.first, pnoSettings.first);
        mLooper.dispatchAll();
        order.verify(client.listener).onSuccess();
        expectSwPnoScan(order, mWifiScannerImpl0);

        for (int iteration = 0; iteration < mSwPnoFastIterations + mSwPnoSlowIterations
                + mSwPnoMobilityIterations; iteration++) {
            ArrayList<Intent> tempList = mSwPnoIntentsQueue;
            mSwPnoIntentsQueue = new ArrayList<>();
            Collections.shuffle(tempList);
            for (int intentIdx = 0; intentIdx < tempList.size(); intentIdx++) {
                mockBroadcastReceived(tempList.get(intentIdx));
            }
            expectSwPnoScan(order, mWifiScannerImpl0);
        }

        // Finally, after the last iteration, no more scans should be performed
        if (!mSwPnoIntentsQueue.isEmpty()) {
            for (int j = 0; j < mSwPnoIntentsQueue.size(); j++) {
                mockBroadcastReceived(mSwPnoIntentsQueue.get(j));
            }
        }
        order.verify(mWifiScannerImpl0, never()).startSingleScan(any(), any());
    }

    /**
     * verify that SW PNO scan fails if invilid configs are provided
     */
    @Test
    public void testFailedSwPnoScanInvalidConfigs() throws Exception {
        startServiceAndLoadDriver();
        mLooper.dispatchAll();

        mResources.setInteger(R.integer.config_wifiSwPnoFastTimerMs, 0);
        mResources.setInteger(R.integer.config_wifiSwPnoFastTimerIterations, 0);
        when(mWifiScannerImpl0.isHwPnoSupported(anyBoolean())).thenReturn(false);
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);
        when(mDeviceConfigFacade.isSoftwarePnoEnabled()).thenReturn(true);

        TestClient client = new TestClient();

        InOrder order = inOrder(client.listener, mWifiScannerImpl0);

        ScanResults scanResults = createScanResultsForPno();
        Pair<WifiScanner.ScanSettings, WifiNative.ScanSettings> scanSettings =
                createScanSettingsForHwPno();
        Pair<WifiScanner.PnoSettings, WifiNative.PnoSettings> pnoSettings =
                createPnoSettings(scanResults);

        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(scanResults.getScanData());

        client.sendPnoScanRequest(scanSettings.first, pnoSettings.first);
        mLooper.dispatchAll();
        order.verify(client.listener).onFailure(eq(WifiScanner.REASON_INVALID_REQUEST),
                anyString());
    }


    /**
     * Tests wificond PNO scan. This ensures that the PNO scan results are plumbed back to the
     * client as a PNO network found event.
     */
    @Test
    public void testSuccessfulHwPnoScanWithNoBackgroundScan() throws Exception {
        startServiceAndLoadDriver();
        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0);

        ScanResults scanResults = createScanResultsForPno();
        Pair<WifiScanner.ScanSettings, WifiNative.ScanSettings> scanSettings =
                createScanSettingsForHwPno();
        Pair<WifiScanner.PnoSettings, WifiNative.PnoSettings> pnoSettings =
                createPnoSettings(scanResults);

        client.sendPnoScanRequest(scanSettings.first, pnoSettings.first);
        expectHwPnoScan(order, client.listener, pnoSettings.second, scanResults);
        client.verifyPnoNetworkFoundReceived(scanResults.getRawScanResults());
    }

    @Test
    public void rejectHwPnoScanRequestWhenScannerImplCreateFails() throws Exception {
        // Fail scanner impl creation.
        when(mWifiScannerImplFactory.create(any(), any(), any(), any())).thenReturn(null);

        startServiceAndLoadDriver();

        TestClient client = new TestClient();

        ScanResults scanResults = createScanResultsForPno();
        Pair<WifiScanner.ScanSettings, WifiNative.ScanSettings> scanSettings =
                createScanSettingsForHwPno();
        Pair<WifiScanner.PnoSettings, WifiNative.PnoSettings> pnoSettings =
                createPnoSettings(scanResults);

        client.sendPnoScanRequest(scanSettings.first, pnoSettings.first);
        mLooper.dispatchAll();
        client.verifyFailedResponse(WifiScanner.REASON_UNSPECIFIED,
                "not available");
    }

    /**
     * Tries to simulate the race scenario where a client is disconnected immediately after single
     * scan request is sent to |SingleScanStateMachine|.
     */
    @Test
    public void processSingleScanRequestAfterDisconnect() throws Exception {
        startServiceAndLoadDriver();

        // Send the single scan request and then send the disconnect immediately after.
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        TestClient client = new TestClient();
        client.sendSingleScanRequest(requestSettings, null);
        client.binderDied();
        // Now process the above 2 actions. This should result in first processing the single scan
        // request (which forwards the request to SingleScanStateMachine) and then processing the
        // disconnect after.
        mLooper.dispatchAll();
        // Now check that we logged the invalid request.
        String serviceDump = dumpService();
        Pattern logLineRegex = Pattern.compile(
                "^.+" + "Successfully stopped all requests for client "
                        + "ClientInfo\\[uid=\\d+, package=" + TEST_PACKAGE_NAME
                        + ", Mock for Stub, hashCode: \\d+\\]",
                Pattern.MULTILINE);
        assertTrue("dump did not contain log with [" + logLineRegex + "]\n" + serviceDump + "\n",
                logLineRegex.matcher(serviceDump).find());
    }

    /**
     * Tries to simulate the race scenario where a client is disconnected immediately after single
     * scan request is sent to |SingleScanStateMachine|.
     */
    @Test
    public void sendScanRequestAfterUnsuccessfulSend() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        startServiceAndLoadDriver();
        mLooper.dispatchAll();

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                        any(WifiNative.ScanEventHandler.class))).thenReturn(true);
        ScanResults results = ScanResults.create(0, WifiScanner.WIFI_BAND_BOTH, 2412);
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results.getRawScanData());

        TestClient client = new TestClient();
        InOrder order = inOrder(mWifiScannerImpl0, client.listener);

        client.sendSingleScanRequest(requestSettings, null);
        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler1 = verifyStartSingleScan(order,
                computeSingleScanNativeSettings(requestSettings));
        client.verifySuccessfulResponse();

        eventHandler1.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
        mLooper.dispatchAll();
        client.verifyScanResultsReceived(results.getScanData());
        client.verifySingleScanCompletedReceived();
        client.verifyUnlinkedToDeath();
        verifyNoMoreInteractions(client.listener);

        mLooper.dispatchAll();

        client.sendSingleScanRequest(requestSettings, null);
        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler2 = verifyStartSingleScan(order,
                computeSingleScanNativeSettings(requestSettings));
        client.verifySuccessfulResponse();

        eventHandler2.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
        mLooper.dispatchAll();
        client.verifyScanResultsReceived(results.getScanData());
        client.verifySingleScanCompletedReceived();
        client.verifyUnlinkedToDeath();
        verifyNoMoreInteractions(client.listener);
    }

    /**
     * Verifies that background scan works after duplicate scan enable.
     */
    @Test
    public void backgroundScanAfterDuplicateScanEnable() throws Exception {
        startServiceAndLoadDriver();

        // Send scan enable again.
        TestClient client = new TestClient();

        mWifiScanningServiceImpl.setScanningEnabled(true, 0, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();

        // Ensure we didn't create scanner instance twice.
        verify(mWifiScannerImplFactory, times(1))
                .create(any(), any(), any(), any());

        when(mWifiScannerImpl0.startBatchedScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);
        client.sendBackgroundScanRequest(generateValidScanSettings(), null);
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();
        assertDumpContainsRequestLog("addBackgroundScanRequest");
        verify(mLastCallerInfoManager, atLeastOnce()).put(
                eq(WifiManager.API_SCANNING_ENABLED), anyInt(), anyInt(), anyInt(), any(),
                eq(true));
    }

    /**
     * Verifies that single scan works after duplicate scan enable.
     */
    @Test
    public void singleScanScanAfterDuplicateScanEnable() throws Exception {
        startServiceAndLoadDriver();

        // Send scan enable again.
        TestClient client = new TestClient();

        mWifiScanningServiceImpl.setScanningEnabled(true, 0, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();

        // Ensure we didn't create scanner instance twice.
        verify(mWifiScannerImplFactory, times(1))
                .create(any(), any(), any(), any());

        InOrder order = inOrder(client.listener, mWifiScannerImpl0);

        WorkSource workSource = new WorkSource(2292);
        WifiScanner.ScanSettings requestSettings = createRequest(channelsToSpec(2412, 5160, 5175),
                0, 0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        ScanResults results =
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412, 5160, 5175);

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        client.sendSingleScanRequest(requestSettings, workSource);

        mLooper.dispatchAll();
        client.verifySuccessfulResponse();
        WifiNative.ScanEventHandler eventHandler =
                verifyStartSingleScan(order, computeSingleScanNativeSettings(requestSettings));
        verify(mBatteryStats).reportWifiScanStartedFromSource(eq(workSource));

        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results.getRawScanData());
        eventHandler.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        client.verifyScanResultsReceived(results.getScanData());
        client.verifySingleScanCompletedReceived();
        client.verifyUnlinkedToDeath();
        verifyNoMoreInteractions(client.listener);
        verify(mBatteryStats).reportWifiScanStoppedFromSource(eq(workSource));
        assertDumpContainsRequestLog("addSingleScanRequest");
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results.getScanData().getResults().length);
        verify(mLastCallerInfoManager, atLeastOnce()).put(
                eq(WifiManager.API_SCANNING_ENABLED), anyInt(), anyInt(), anyInt(), any(),
                eq(true));
    }

    /**
     * Verifies that pno scan works after duplicate scan enable.
     */
    @Test
    public void hwPnoScanScanAfterDuplicateScanEnable() throws Exception {
        startServiceAndLoadDriver();

        // Send scan enable again.
        TestClient client = new TestClient();

        mWifiScanningServiceImpl.setScanningEnabled(true, 0, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();

        // Ensure we didn't create scanner instance twice.
        verify(mWifiScannerImplFactory, times(1))
                .create(any(), any(), any(), any());

        InOrder order = inOrder(client.listener, mWifiScannerImpl0);

        ScanResults scanResults = createScanResultsForPno();
        Pair<WifiScanner.ScanSettings, WifiNative.ScanSettings> scanSettings =
                createScanSettingsForHwPno();
        Pair<WifiScanner.PnoSettings, WifiNative.PnoSettings> pnoSettings =
                createPnoSettings(scanResults);

        client.sendPnoScanRequest(scanSettings.first, pnoSettings.first);
        expectHwPnoScan(order, client.listener, pnoSettings.second, scanResults);
        client.verifyPnoNetworkFoundReceived(scanResults.getRawScanResults());
    }

    /**
     * Verifies that only clients with NETWORK_STACK permission can call restricted APIs.
     *
     * Also verifies that starting in Android T CMD_REGISTER_SCAN_LISTENER is callable without
     * NEWORK_STACK permission.
     */
    @Test
    public void rejectRestrictedMessagesFromNonPrivilegedApps() throws Exception {
        mWifiScanningServiceImpl.startService();
        mLooper.dispatchAll();
        TestClient client = new TestClient();
        TestClient client1 = new TestClient();

        // Client doesn't have NETWORK_STACK permission.
        doThrow(new SecurityException()).when(mContext).enforcePermission(
                eq(Manifest.permission.NETWORK_STACK), anyInt(), eq(Binder.getCallingUid()), any());

        assertFalse(mWifiScanningServiceImpl.setScanningEnabled(true, 0, TEST_PACKAGE_NAME));
        mLooper.dispatchAll();

        assertFalse(mWifiScanningServiceImpl.setScanningEnabled(false, 0, TEST_PACKAGE_NAME));
        mLooper.dispatchAll();

        mWifiScanningServiceImpl.startPnoScan(client1.listener, new WifiScanner.ScanSettings(),
                new WifiScanner.PnoSettings(), TEST_PACKAGE_NAME, null);
        mLooper.dispatchAll();

        mWifiScanningServiceImpl.stopPnoScan(client1.listener, TEST_PACKAGE_NAME, null);
        mLooper.dispatchAll();

        mWifiScanningServiceImpl.registerScanListener(client.listener, TEST_PACKAGE_NAME, null);
        mLooper.dispatchAll();

        // All 4 of the above messages should have been rejected because the app doesn't have
        // the required permissions.
        client1.verifyFailedResponse(2, WifiScanner.REASON_NOT_AUTHORIZED,
                "Not authorized");
        if (SdkLevel.isAtLeastT()) {
            client.verifySuccessfulResponse();
            verify(mWifiPermissionsUtil).enforceCanAccessScanResultsForWifiScanner(
                    any(), any(), eq(Binder.getCallingUid()),
                    eq(false), eq(false));
        } else {
            client.verifyFailedResponse(WifiScanner.REASON_NOT_AUTHORIZED,
                    "Not authorized");
            verify(mWifiPermissionsUtil, never()).enforceCanAccessScanResultsForWifiScanner(
                    any(), any(), anyInt(), anyBoolean(), anyBoolean());
        }

        // Ensure we didn't create scanner instance.
        verify(mWifiScannerImplFactory, never()).create(any(), any(), any(), any());

    }

    /**
     * Verifies that clients without NETWORK_STACK permission cannot issue any messages when they
     * don't have the necessary location permissions & location is enabled.
     */
    @Test
    public void rejectAllMessagesFromNonPrivilegedAppsWithoutLocationPermission() throws Exception {
        // Start service & initialize it.
        startServiceAndLoadDriver();

        // Location permission or mode check fail.
        doThrow(new SecurityException()).when(mWifiPermissionsUtil)
                .enforceCanAccessScanResultsForWifiScanner(any(), any(), eq(Binder.getCallingUid()),
                        eq(false), eq(false));

        TestClient client = new TestClient();

        // Client doesn't have NETWORK_STACK permission.
        doThrow(new SecurityException()).when(mContext).enforcePermission(
                eq(Manifest.permission.NETWORK_STACK), anyInt(), eq(Binder.getCallingUid()), any());

        // All the above messages should have been rejected because the app doesn't have
        // the privileged permissions & location is turned off.
        client.sendSingleScanRequest(null, null);
        mLooper.dispatchAll();
        client.verifyFailedResponse(WifiScanner.REASON_NOT_AUTHORIZED,
                "Not authorized");

        mWifiScanningServiceImpl.getScanResults(TEST_PACKAGE_NAME, null);
        mLooper.dispatchAll();

        mWifiScanningServiceImpl.startBackgroundScan(client.listener, null, null, TEST_PACKAGE_NAME,
                null);
        mLooper.dispatchAll();
        client.verifyFailedResponse(WifiScanner.REASON_NOT_AUTHORIZED,
                "Not authorized");

        // Validate the initialization sequence.
        verify(mWifiScannerImpl0).getChannelHelper();
        verify(mWifiScannerImpl0).getScanCapabilities(any());

        // Ensure we didn't start any scans after.
        verifyNoMoreInteractions(mWifiScannerImpl0);
    }

    /**
     * Verifies that we ignore location settings when the single scan request settings sets
     * {@link WifiScanner.ScanSettings#ignoreLocationSettings}
     */
    @Test
    public void verifyIgnoreLocationSettingsFromNonPrivilegedAppsForSingleScan() throws Exception {
        // Start service & initialize it.
        startServiceAndLoadDriver();

        TestClient client1 = new TestClient();
        TestClient client2 = new TestClient();
        TestClient client3 = new TestClient();

        // Client doesn't have NETWORK_STACK permission.
        doThrow(new SecurityException()).when(mContext).enforcePermission(
                eq(Manifest.permission.NETWORK_STACK), anyInt(), eq(Binder.getCallingUid()), any());

        // successful start
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        WifiScanner.ScanSettings scanSettings =
                createRequest(WifiScanner.WIFI_BAND_ALL, 0, 0, 20,
                        WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        // send single scan request (ignoreLocationSettings == true).
        scanSettings.ignoreLocationSettings = true;
        client1.sendSingleScanRequest(scanSettings, null);
        mLooper.dispatchAll();

        // Verify the permission check params (ignoreLocationSettings == true).
        verify(mWifiPermissionsUtil).enforceCanAccessScanResultsForWifiScanner(
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID), eq(Binder.getCallingUid()), eq(true),
                eq(false));
        client1.verifySuccessfulResponse();
        verify(mWifiManager).setEmergencyScanRequestInProgress(true);

        // send single scan request (ignoreLocationSettings == false).
        scanSettings.ignoreLocationSettings = false;
        client2.sendSingleScanRequest(scanSettings, null);

        // Verify the permission check params (ignoreLocationSettings == true).
        verify(mWifiPermissionsUtil).enforceCanAccessScanResultsForWifiScanner(
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID), eq(Binder.getCallingUid()), eq(false),
                eq(false));
        client2.verifySuccessfulResponse();
        verify(mWifiManager, times(1)).setEmergencyScanRequestInProgress(true);

        // send background scan request (ignoreLocationSettings == true).
        scanSettings.ignoreLocationSettings = true;
        client3.sendBackgroundScanRequest(scanSettings, null);
        mLooper.dispatchAll();

        // Verify the permission check params (ignoreLocationSettings == false), the field
        // is ignored for any requests other than single scan.
        verify(mWifiPermissionsUtil, times(2)).enforceCanAccessScanResultsForWifiScanner(
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID), eq(Binder.getCallingUid()), eq(false),
                eq(false));
    }

    /**
     * Verifies that we hide from app-ops when the single scan request settings sets
     * {@link WifiScanner.ScanSettings#hideFromAppOps}
     */
    @Test
    public void verifyHideFromAppOpsFromNonPrivilegedAppsForSingleScan() throws Exception {
        // Start service & initialize it.
        startServiceAndLoadDriver();

        TestClient client = new TestClient();

        // Client doesn't have NETWORK_STACK permission.
        doThrow(new SecurityException()).when(mContext).enforcePermission(
                eq(Manifest.permission.NETWORK_STACK), anyInt(), eq(Binder.getCallingUid()), any());

        Bundle bundle = new Bundle();
        bundle.putString(WifiScanner.REQUEST_PACKAGE_NAME_KEY, TEST_PACKAGE_NAME);
        bundle.putString(WifiScanner.REQUEST_FEATURE_ID_KEY, TEST_FEATURE_ID);
        WifiScanner.ScanSettings scanSettings = new WifiScanner.ScanSettings();

        // send single scan request (hideFromAppOps == true).
        scanSettings.hideFromAppOps = true;
        bundle.putParcelable(WifiScanner.SCAN_PARAMS_SCAN_SETTINGS_KEY, scanSettings);
        client.sendSingleScanRequest(scanSettings, null);
        mLooper.dispatchAll();

        // Verify the permission check params (hideFromAppOps == true).
        verify(mWifiPermissionsUtil).enforceCanAccessScanResultsForWifiScanner(
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID), eq(Binder.getCallingUid()), eq(false),
                eq(true));

        // send single scan request (hideFromAppOps == false).
        scanSettings.hideFromAppOps = false;
        bundle.putParcelable(WifiScanner.SCAN_PARAMS_SCAN_SETTINGS_KEY, scanSettings);
        client.sendSingleScanRequest(scanSettings, null);
        mLooper.dispatchAll();

        // Verify the permission check params (hideFromAppOps == false).
        verify(mWifiPermissionsUtil).enforceCanAccessScanResultsForWifiScanner(
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID), eq(Binder.getCallingUid()), eq(false),
                eq(false));

        // send background scan request (hideFromAppOps == true).
        scanSettings.hideFromAppOps = true;
        bundle.putParcelable(WifiScanner.SCAN_PARAMS_SCAN_SETTINGS_KEY, scanSettings);
        client.sendBackgroundScanRequest(scanSettings, null);
        mLooper.dispatchAll();

        // Verify the permission check params (hideFromAppOps == false), the field
        // is ignored for any requests other than single scan.
        verify(mWifiPermissionsUtil, times(2)).enforceCanAccessScanResultsForWifiScanner(
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID), eq(Binder.getCallingUid()), eq(false),
                eq(false));
    }

    /**
     * Verifies that we don't invoke {@link WifiPermissionsUtil#
     * enforceCanAccessScanResultsForWifiScanner(String, int, boolean, boolean)} for requests
     * from privileged clients (i.e wifi service).
     */
    @Test
    public void verifyLocationPermissionCheckIsSkippedFromPrivilegedClientsForSingleScan()
            throws Exception {
        // Start service & initialize it.
        startServiceAndLoadDriver();

        TestClient client = new TestClient();

        // Client does have NETWORK_STACK permission.
        doNothing().when(mContext).enforcePermission(
                eq(Manifest.permission.NETWORK_STACK), anyInt(), eq(Binder.getCallingUid()), any());

        Bundle bundle = new Bundle();
        bundle.putString(WifiScanner.REQUEST_PACKAGE_NAME_KEY, TEST_PACKAGE_NAME);
        bundle.putString(WifiScanner.REQUEST_FEATURE_ID_KEY, TEST_FEATURE_ID);
        WifiScanner.ScanSettings scanSettings = new WifiScanner.ScanSettings();

        // send single scan request (hideFromAppOps == true, ignoreLocationSettings = true).
        scanSettings.hideFromAppOps = true;
        scanSettings.ignoreLocationSettings = true;
        bundle.putParcelable(WifiScanner.SCAN_PARAMS_SCAN_SETTINGS_KEY, scanSettings);
        client.sendSingleScanRequest(scanSettings, null);
        mLooper.dispatchAll();

        // Verify that we didn't invoke the location permission check.
        verify(mWifiPermissionsUtil, never()).enforceCanAccessScanResultsForWifiScanner(
                eq(TEST_PACKAGE_NAME), eq(TEST_FEATURE_ID), eq(Binder.getCallingUid()),
                anyBoolean(), anyBoolean());
    }

    /**
     * Setup/teardown a second scanner impl dynamically.
     */
    @Test
    public void setupAndTeardownSecondImpl() throws Exception {
        // start up service with a single impl.
        startServiceAndLoadDriver();
        verify(mWifiScannerImplFactory, times(1))
                .create(any(), any(), any(), eq(TEST_IFACE_NAME_0));

        // Now setup an impl for second iface.
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0, TEST_IFACE_NAME_1)));
        mWifiScanningServiceImpl.setScanningEnabled(true, 0, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();

        verify(mWifiScannerImplFactory, times(1))
                .create(any(), any(), any(), eq(TEST_IFACE_NAME_1));

        // Now teardown the impl for second iface.
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0)));
        mWifiScanningServiceImpl.setScanningEnabled(true, 0, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();

        verify(mWifiScannerImpl1).cleanup();

        // Now teardown everything.
        mWifiScanningServiceImpl.setScanningEnabled(false, 0, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();

        verify(mLastCallerInfoManager).put(eq(WifiManager.API_SCANNING_ENABLED), anyInt(),
                anyInt(), anyInt(), any(), eq(false));
        verify(mWifiScannerImpl0).cleanup();
    }

    /**
     * Setup/teardown a second scanner impl dynamically which satisfies the same set of channels
     * as the existing one.
     */
    @Test
    public void setupAndTeardownSecondImplWhichSatisfiesExistingImpl() throws Exception {
        // start up service with a single impl.
        startServiceAndLoadDriver();
        verify(mWifiScannerImplFactory, times(1))
                .create(any(), any(), any(), eq(TEST_IFACE_NAME_0));

        // Now setup an impl for second iface.
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0, TEST_IFACE_NAME_1)));
        // Setup the second impl to contain the same set of channels as the first one.
        when(mWifiScannerImpl1.getChannelHelper()).thenReturn(mChannelHelper0);
        mWifiScanningServiceImpl.setScanningEnabled(true, 0, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();

        // Verify that we created the new impl and immediately tore it down because it was
        // satisfied by an existing impl.
        verify(mWifiScannerImplFactory, times(1))
                .create(any(), any(), any(), eq(TEST_IFACE_NAME_1));
        verify(mWifiScannerImpl1, times(1)).getChannelHelper();
        verify(mWifiScannerImpl1, times(1)).cleanup();

        // Now teardown the second iface.
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0)));
        mWifiScanningServiceImpl.setScanningEnabled(true, 0, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();

        // do nothing, since impl1 was never added to the active impl list.
        verifyNoMoreInteractions(mWifiScannerImpl1);

        // Now teardown everything.
        mWifiScanningServiceImpl.setScanningEnabled(false, 0, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();

        verify(mWifiScannerImpl0).cleanup();
    }

    /**
     * Setup a second scanner impl and tearddown a existing scanning impl dynamically which
     * satisfies the same set of channels as the existing one.
     */
    @Test
    public void setupSecondImplAndTeardownFirstImplWhichSatisfiesExistingImpl() throws Exception {
        // start up service with a single impl.
        startServiceAndLoadDriver();
        verify(mWifiScannerImplFactory, times(1))
                .create(any(), any(), any(), eq(TEST_IFACE_NAME_0));

        // Now setup an impl for second iface and teardown the first one.
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_1)));
        // Setup the second impl to contain the same set of channels as the first one.
        when(mWifiScannerImpl1.getChannelHelper()).thenReturn(mChannelHelper0);
        mWifiScanningServiceImpl.setScanningEnabled(true, 0, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();

        // tear down the first one because corresponding iface was brought down.
        verify(mWifiScannerImpl0).cleanup();

        // Verify that we created the new impl.
        verify(mWifiScannerImplFactory, times(1))
                .create(any(), any(), any(), eq(TEST_IFACE_NAME_1));
        verify(mWifiScannerImpl1, never()).getChannelHelper();

        // Now teardown everything.
        mWifiScanningServiceImpl.setScanningEnabled(false, 0, TEST_PACKAGE_NAME);
        mLooper.dispatchAll();

        verify(mWifiScannerImpl1).cleanup();
    }

    /**
     * Do a single scan for a band and verify that it is successful across multiple impls.
     */
    @Test
    public void sendSingleScanBandRequestOnMultipleImpls() throws Exception {
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0, TEST_IFACE_NAME_1)));
        WifiScanner.ScanSettings requestSettings = createRequest(
                WifiScanner.WIFI_BAND_ALL, 0, 0, 20,
                WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        doSuccessfulSingleScanOnImpls(requestSettings,
                computeSingleScanNativeSettings(requestSettings),
                ScanResults.create(0, WifiScanner.WIFI_BAND_ALL, 2412),
                ScanResults.create(0, WifiScanner.WIFI_BAND_ALL, 5160));
    }

    /**
     * Do a single scan for a list of channels and verify that it is successful across multiple
     * impls.
     */
    @Test
    public void sendSingleScanChannelsRequestOnMultipleImpls() throws Exception {
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0, TEST_IFACE_NAME_1)));
        WifiScanner.ScanSettings requestSettings = createRequest(channelsToSpec(2412, 5160, 5175),
                0, 0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        doSuccessfulSingleScanOnImpls(requestSettings,
                computeSingleScanNativeSettings(requestSettings),
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412),
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 5175));
    }

    /**
     * Do a single scan with no results and verify that it is successful across multiple
     * impls.
     */
    @Test
    public void sendSingleScanRequestWithNoResultsOnMultipleImpls() throws Exception {
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0, TEST_IFACE_NAME_1)));
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        doSuccessfulSingleScanOnImpls(requestSettings,
                computeSingleScanNativeSettings(requestSettings),
                ScanResults.create(0, WifiScanner.WIFI_BAND_BOTH, new int[0]),
                ScanResults.create(0, WifiScanner.WIFI_BAND_BOTH, new int[0]));
    }

    /**
     * Do a single scan, which the hardware fails to start across multiple impls, and verify that a
     * failure response is delivered.
     */
    @Test
    public void sendSingleScanRequestWhichFailsToStartOnMultipleImpls() throws Exception {
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0, TEST_IFACE_NAME_1)));

        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        startServiceAndLoadDriver();

        TestClient client = new TestClient();

        // scan fails
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(false);
        when(mWifiScannerImpl1.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(false);

        client.sendSingleScanRequest(requestSettings, null);

        mLooper.dispatchAll();
        // Scan is successfully queue, but then fails to execute
        client.verifySuccessfulResponse();
        client.verifyFailedResponse(WifiScanner.REASON_UNSPECIFIED,
                "Failed to start single scan");
        verifyNoMoreInteractions(mBatteryStats);

        verify(mWifiMetrics).incrementOneshotScanCount();
        verify(mWifiMetrics).incrementScanReturnEntry(WifiMetricsProto.WifiLog.SCAN_UNKNOWN, 1);
        assertDumpContainsRequestLog("addSingleScanRequest");
    }

    /**
     * Do a single scan, which the hardware fails to start on one of the impl, and verify that a
     * successful response is delivered when other impls succeed.
     */
    @Test
    public void sendSingleScanRequestWhichFailsToStartOnOneImpl() throws Exception {
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0, TEST_IFACE_NAME_1)));

        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        WifiNative.ScanSettings nativeSettings = computeSingleScanNativeSettings(requestSettings);
        ScanResults results =
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412, 5160, 5175);
        WorkSource workSource = new WorkSource(2292);

        startServiceAndLoadDriver();
        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0, mWifiScannerImpl1);

        // scan fails on impl0
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(false);
        // scan succeeds on impl1
        when(mWifiScannerImpl1.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        client.sendSingleScanRequest(requestSettings, workSource);

        mLooper.dispatchAll();

        client.verifySuccessfulResponse();
        WifiNative.ScanEventHandler eventHandler0 =
                verifyStartSingleScanForImpl(mWifiScannerImpl0, order, nativeSettings);
        WifiNative.ScanEventHandler eventHandler1 =
                verifyStartSingleScanForImpl(mWifiScannerImpl1, order, nativeSettings);

        verify(mBatteryStats).reportWifiScanStartedFromSource(eq(workSource));

        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(new WifiScanner.ScanData(PLACEHOLDER_SCAN_DATA));
        // Send scan success on impl1
        when(mWifiScannerImpl1.getLatestSingleScanResults())
                .thenReturn(results.getRawScanData());
        eventHandler1.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        client.verifyScanResultsReceived(results.getScanData());
        client.verifySingleScanCompletedReceived();
        client.verifyUnlinkedToDeath();
        verifyNoMoreInteractions(client.listener);
        verify(mBatteryStats).reportWifiScanStoppedFromSource(eq(workSource));
        assertDumpContainsRequestLog("addSingleScanRequest");
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results.getScanData().getResults().length);
    }

    /**
     * Do a single scan, which successfully starts, but fails across multiple impls partway through
     * and verify that a failure response is delivered.
     */
    @Test
    public void sendSingleScanRequestWhichFailsAfterStartOnMultipleImpls() throws Exception {
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0, TEST_IFACE_NAME_1)));

        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        WorkSource workSource = new WorkSource(Binder.getCallingUid()); // don't explicitly set

        startServiceAndLoadDriver();
        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0, mWifiScannerImpl1);

        // successful start
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);
        when(mWifiScannerImpl1.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        client.sendSingleScanRequest(requestSettings, null);

        // Scan is successfully queue
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();
        WifiNative.ScanEventHandler eventHandler0 =
                verifyStartSingleScanForImpl(mWifiScannerImpl0, order,
                        computeSingleScanNativeSettings(requestSettings));
        WifiNative.ScanEventHandler eventHandler1 =
                verifyStartSingleScanForImpl(mWifiScannerImpl1, order,
                        computeSingleScanNativeSettings(requestSettings));
        verify(mBatteryStats).reportWifiScanStartedFromSource(eq(workSource));

        // but then fails to execute
        eventHandler0.onScanRequestFailed(WifiScanner.REASON_UNSPECIFIED);
        eventHandler1.onScanRequestFailed(WifiScanner.REASON_UNSPECIFIED);
        mLooper.dispatchAll();
        client.verifyFailedResponse(
                WifiScanner.REASON_UNSPECIFIED, "Scan failed - unspecified reason");
        assertDumpContainsCallbackLog("singleScanFailed",
                "reason=" + WifiScanner.REASON_UNSPECIFIED + ", Scan failed - unspecified reason");
        verify(mWifiMetrics).incrementOneshotScanCount();
        verify(mWifiMetrics).incrementScanReturnEntry(WifiMetricsProto.WifiLog.SCAN_UNKNOWN, 1);
        verify(mBatteryStats).reportWifiScanStoppedFromSource(eq(workSource));
    }

    /**
     * Do a single scan, which successfully starts, but fails partway through on one of the impls
     * and verify that a successful response is delivered.
     */
    @Test
    public void sendSingleScanRequestWhichFailsAfterStartOnOneImpl() throws Exception {
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0, TEST_IFACE_NAME_1)));

        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        WifiNative.ScanSettings nativeSettings = computeSingleScanNativeSettings(requestSettings);
        ScanResults results =
                ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412, 5160, 5175);
        WorkSource workSource = new WorkSource(Binder.getCallingUid()); // don't explicitly set

        startServiceAndLoadDriver();
        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0, mWifiScannerImpl1);

        // successful start
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);
        when(mWifiScannerImpl1.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        client.sendSingleScanRequest(requestSettings, null);

        // Scan is successfully queued
        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler0 =
                verifyStartSingleScanForImpl(mWifiScannerImpl0, order, nativeSettings);
        WifiNative.ScanEventHandler eventHandler1 =
                verifyStartSingleScanForImpl(mWifiScannerImpl1, order, nativeSettings);
        client.verifySuccessfulResponse();
        verify(mBatteryStats).reportWifiScanStartedFromSource(eq(workSource));

        // then fails to execute on impl0
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(new WifiScanner.ScanData(PLACEHOLDER_SCAN_DATA));
        eventHandler0.onScanStatus(WifiNative.WIFI_SCAN_FAILED);
        // but succeeds on impl1
        when(mWifiScannerImpl1.getLatestSingleScanResults())
                .thenReturn(results.getRawScanData());
        eventHandler1.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        mLooper.dispatchAll();
        client.verifyScanResultsReceived(results.getScanData());
        client.verifySingleScanCompletedReceived();
        client.verifyUnlinkedToDeath();
        verifyNoMoreInteractions(client.listener);
        verify(mBatteryStats).reportWifiScanStoppedFromSource(eq(workSource));
        assertDumpContainsRequestLog("addSingleScanRequest");
        assertDumpContainsCallbackLog("singleScanResults",
                "results=" + results.getScanData().getResults().length);
    }

    /**
     * Tests wificond PNO scan across multiple impls. This ensures that the
     * PNO scan results are plumbed back to the client as a PNO network found event.
     */
    @Test
    public void testSuccessfulHwPnoScanOnMultipleImpls() throws Exception {
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0, TEST_IFACE_NAME_1)));

        startServiceAndLoadDriver();
        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0, mWifiScannerImpl1);

        ScanResults scanResults = createScanResultsForPno();
        Pair<WifiScanner.ScanSettings, WifiNative.ScanSettings> scanSettings =
                createScanSettingsForHwPno();
        Pair<WifiScanner.PnoSettings, WifiNative.PnoSettings> pnoSettings =
                createPnoSettings(scanResults);

        // Results received on impl 0
        client.sendPnoScanRequest(scanSettings.first, pnoSettings.first);
        expectHwPnoScanOnImpls(order, client.listener, pnoSettings.second, scanResults, null);
        client.verifyPnoNetworkFoundReceived(scanResults.getRawScanResults());
    }

    /**
     * Tests wificond PNO scan that fails to start on all impls.
     */
    @Test
    public void testFailedHwPnoScanWhichFailsToStartOnMultipleImpls() throws Exception {
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0, TEST_IFACE_NAME_1)));

        startServiceAndLoadDriver();
        TestClient client = new TestClient();

        ScanResults scanResults = createScanResultsForPno();
        Pair<WifiScanner.ScanSettings, WifiNative.ScanSettings> scanSettings =
                createScanSettingsForHwPno();
        Pair<WifiScanner.PnoSettings, WifiNative.PnoSettings> pnoSettings =
                createPnoSettings(scanResults);

        when(mWifiScannerImpl0.isHwPnoSupported(anyBoolean())).thenReturn(true);
        when(mWifiScannerImpl1.isHwPnoSupported(anyBoolean())).thenReturn(true);
        // pno scan fails on both impls
        when(mWifiScannerImpl0.setHwPnoList(any(WifiNative.PnoSettings.class),
                any(WifiNative.PnoEventHandler.class))).thenReturn(false);
        when(mWifiScannerImpl1.setHwPnoList(any(WifiNative.PnoSettings.class),
                any(WifiNative.PnoEventHandler.class))).thenReturn(false);

        client.sendPnoScanRequest(scanSettings.first, pnoSettings.first);
        mLooper.dispatchAll();

        client.verifyFailedResponse(WifiScanner.REASON_INVALID_REQUEST,
                "bad request");
    }

    /**
     * Tests wificond PNO scan that fails to start on one of the impls.
     */
    @Test
    public void testSuccessfulHwPnoScanWhichFailsToStartOnOneImpl() throws Exception {
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0, TEST_IFACE_NAME_1)));

        startServiceAndLoadDriver();
        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0, mWifiScannerImpl1);

        ScanResults scanResults = createScanResultsForPno();
        Pair<WifiScanner.ScanSettings, WifiNative.ScanSettings> scanSettings =
                createScanSettingsForHwPno();
        Pair<WifiScanner.PnoSettings, WifiNative.PnoSettings> pnoSettings =
                createPnoSettings(scanResults);

        when(mWifiScannerImpl0.isHwPnoSupported(anyBoolean())).thenReturn(true);
        when(mWifiScannerImpl1.isHwPnoSupported(anyBoolean())).thenReturn(true);
        // pno scan fails on impl0
        when(mWifiScannerImpl0.setHwPnoList(any(WifiNative.PnoSettings.class),
                any(WifiNative.PnoEventHandler.class))).thenReturn(false);
        // pno scan succeeds on impl1
        when(mWifiScannerImpl1.setHwPnoList(any(WifiNative.PnoSettings.class),
                any(WifiNative.PnoEventHandler.class))).thenReturn(true);

        client.sendPnoScanRequest(scanSettings.first, pnoSettings.first);
        mLooper.dispatchAll();

        WifiNative.PnoEventHandler eventHandler0 =
                verifyHwPnoForImpl(mWifiScannerImpl0, order, pnoSettings.second);
        WifiNative.PnoEventHandler eventHandler1 =
                verifyHwPnoForImpl(mWifiScannerImpl1, order, pnoSettings.second);

        client.verifySuccessfulResponse();

        eventHandler1.onPnoNetworkFound(scanResults.getRawScanResults());
        mLooper.dispatchAll();

        client.verifyPnoNetworkFoundReceived(scanResults.getRawScanResults());
    }

    /**
     * Tests wificond PNO scan that fails after start on all impls.
     */
    @Test
    public void testFailedHwPnoScanWhichFailsAfterStartOnMultipleImpls() throws Exception {
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0, TEST_IFACE_NAME_1)));

        startServiceAndLoadDriver();
        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0, mWifiScannerImpl1);

        ScanResults scanResults = createScanResultsForPno();
        Pair<WifiScanner.ScanSettings, WifiNative.ScanSettings> scanSettings =
                createScanSettingsForHwPno();
        Pair<WifiScanner.PnoSettings, WifiNative.PnoSettings> pnoSettings =
                createPnoSettings(scanResults);

        when(mWifiScannerImpl0.isHwPnoSupported(anyBoolean())).thenReturn(true);
        when(mWifiScannerImpl1.isHwPnoSupported(anyBoolean())).thenReturn(true);
        // pno scan succeeds
        when(mWifiScannerImpl0.setHwPnoList(any(WifiNative.PnoSettings.class),
                any(WifiNative.PnoEventHandler.class))).thenReturn(true);
        when(mWifiScannerImpl1.setHwPnoList(any(WifiNative.PnoSettings.class),
                any(WifiNative.PnoEventHandler.class))).thenReturn(true);

        client.sendPnoScanRequest(scanSettings.first, pnoSettings.first);
        mLooper.dispatchAll();

        WifiNative.PnoEventHandler eventHandler0 =
                verifyHwPnoForImpl(mWifiScannerImpl0, order, pnoSettings.second);
        WifiNative.PnoEventHandler eventHandler1 =
                verifyHwPnoForImpl(mWifiScannerImpl1, order, pnoSettings.second);

        client.verifySuccessfulResponse();

        // fails afterwards.
        eventHandler0.onPnoScanFailed();
        eventHandler1.onPnoScanFailed();
        mLooper.dispatchAll();

        // Scan is successfully queue, but then fails to execute
        client.verifyFailedResponse(WifiScanner.REASON_UNSPECIFIED,
                "pno scan failed");
    }

    /**
     * Tests wificond PNO scan that fails after start on one impls.
     */
    @Test
    public void testSuccessfulHwPnoScanWhichFailsAfterStartOnOneImpl() throws Exception {
        when(mWifiNative.getClientInterfaceNames())
                .thenReturn(new ArraySet<>(Arrays.asList(TEST_IFACE_NAME_0, TEST_IFACE_NAME_1)));

        startServiceAndLoadDriver();
        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0, mWifiScannerImpl1);

        ScanResults scanResults = createScanResultsForPno();
        Pair<WifiScanner.ScanSettings, WifiNative.ScanSettings> scanSettings =
                createScanSettingsForHwPno();
        Pair<WifiScanner.PnoSettings, WifiNative.PnoSettings> pnoSettings =
                createPnoSettings(scanResults);

        when(mWifiScannerImpl0.isHwPnoSupported(anyBoolean())).thenReturn(true);
        when(mWifiScannerImpl1.isHwPnoSupported(anyBoolean())).thenReturn(true);
        // pno scan succeeds
        when(mWifiScannerImpl0.setHwPnoList(any(WifiNative.PnoSettings.class),
                any(WifiNative.PnoEventHandler.class))).thenReturn(true);
        when(mWifiScannerImpl1.setHwPnoList(any(WifiNative.PnoSettings.class),
                any(WifiNative.PnoEventHandler.class))).thenReturn(true);

        client.sendPnoScanRequest(scanSettings.first, pnoSettings.first);
        mLooper.dispatchAll();

        WifiNative.PnoEventHandler eventHandler0 =
                verifyHwPnoForImpl(mWifiScannerImpl0, order, pnoSettings.second);
        WifiNative.PnoEventHandler eventHandler1 =
                verifyHwPnoForImpl(mWifiScannerImpl1, order, pnoSettings.second);

        client.verifySuccessfulResponse();

        // fails afterwards on impl0.
        eventHandler0.onPnoScanFailed();
        // pno match on impl1.
        eventHandler1.onPnoNetworkFound(scanResults.getRawScanResults());
        mLooper.dispatchAll();

        client.verifyPnoNetworkFoundReceived(scanResults.getRawScanResults());
    }

    /**
     * Verify that isScanning throws a security exception if the calliing app has no
     * permission.
     */
    @Test(expected = SecurityException.class)
    public void testIsScanningThrowsException() throws Exception {
        startServiceAndLoadDriver();

        // Client doesn't have LOCATION_HARDWARE permission.
        when(mWifiPermissionsUtil.checkCallersHardwareLocationPermission(anyInt()))
                .thenReturn(false);
        mWifiScanningServiceImpl.isScanning();
    }

    /**
     * Test isScanning returns the proper value.
     */
    @Test
    public void testIsScanning() throws Exception {
        when(mWifiPermissionsUtil.checkCallersHardwareLocationPermission(anyInt()))
                .thenReturn(true);
        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        startServiceAndLoadDriver();

        // Verify that now isScanning = false
        assertFalse("isScanning should be false before scan starts",
                mWifiScanningServiceImpl.isScanning());

        TestClient client = new TestClient();
        mLooper.dispatchAll();

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);
        ScanResults results = ScanResults.create(0, WifiScanner.WIFI_BAND_BOTH, 2412);
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results.getRawScanData());

        InOrder order = inOrder(mWifiScannerImpl0, client.listener);

        client.sendSingleScanRequest(requestSettings, null);
        mLooper.dispatchAll();

        // Verify that now isScanning = true
        assertTrue("isScanning should be true during scanning",
                mWifiScanningServiceImpl.isScanning());

        WifiNative.ScanEventHandler eventHandler1 = verifyStartSingleScan(order,
                computeSingleScanNativeSettings(requestSettings));
        client.verifySuccessfulResponse();

        eventHandler1.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
        mLooper.dispatchAll();
        client.verifyScanResultsReceived(results.getScanData());
        client.verifySingleScanCompletedReceived();
        client.verifyUnlinkedToDeath();
        verifyNoMoreInteractions(client.listener);

        // Verify that now isScanning = false
        assertFalse("isScanning should be false since scanning is complete",
                mWifiScanningServiceImpl.isScanning());
    }

    @Test
    public void getAvailableChannels_noPermission_throwsException_After_U() {
        assumeTrue(SdkLevel.isAtLeastU());
        startServiceAndLoadDriver();

        // verify app targeting prior to Android U can call API with location permission
        when(mWifiPermissionsUtil.isTargetSdkLessThan(any(),
                eq(Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
                anyInt())).thenReturn(true);
        mWifiScanningServiceImpl.getAvailableChannels(WifiScanner.WIFI_BAND_24_GHZ,
                TEST_PACKAGE_NAME, TEST_FEATURE_ID, mExtras);

        // Verify app targeting prior to Android U fails to call API without location permission
        doThrow(new SecurityException()).when(mContext).enforcePermission(
                eq(Manifest.permission.NETWORK_STACK), anyInt(), eq(Binder.getCallingUid()), any());
        doThrow(new SecurityException())
                .when(mWifiPermissionsUtil).enforceCanAccessScanResultsForWifiScanner(
                        TEST_PACKAGE_NAME, TEST_FEATURE_ID, Binder.getCallingUid(), false, false);
        assertThrows(SecurityException.class,
                () -> mWifiScanningServiceImpl.getAvailableChannels(WifiScanner.WIFI_BAND_24_GHZ,
                        TEST_PACKAGE_NAME, TEST_FEATURE_ID, mExtras));

        // Verify app targeting Android U no longer need location.
        when(mWifiPermissionsUtil.isTargetSdkLessThan(any(),
                eq(Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
                anyInt())).thenReturn(false);
        mWifiScanningServiceImpl.getAvailableChannels(WifiScanner.WIFI_BAND_24_GHZ,
                TEST_PACKAGE_NAME, TEST_FEATURE_ID, mExtras);

        // Verify app targeting Android U will fail without nearby permission
        doThrow(new SecurityException())
                .when(mWifiPermissionsUtil).enforceNearbyDevicesPermission(
                        any(), anyBoolean(), any());
        assertThrows(SecurityException.class,
                () -> mWifiScanningServiceImpl.getAvailableChannels(WifiScanner.WIFI_BAND_24_GHZ,
                        TEST_PACKAGE_NAME, TEST_FEATURE_ID, mExtras));
    }

    /**
     * Tests that {@link WifiScanningServiceImpl#getAvailableChannels(int, String)} throws a
     * {@link SecurityException} if the caller doesn't hold the required permissions.
     */
    @Test(expected = SecurityException.class)
    public void getAvailableChannels_noPermission_throwsException() throws Exception {
        startServiceAndLoadDriver();

        // Client doesn't have NETWORK_STACK permission.
        doThrow(new SecurityException()).when(mContext).enforcePermission(
                eq(Manifest.permission.NETWORK_STACK), anyInt(), eq(Binder.getCallingUid()), any());

        // Location permission or mode check fail.
        doThrow(new SecurityException())
                .when(mWifiPermissionsUtil).enforceCanAccessScanResultsForWifiScanner(
                TEST_PACKAGE_NAME, TEST_FEATURE_ID, Binder.getCallingUid(), false, false);

        mWifiScanningServiceImpl.getAvailableChannels(WifiScanner.WIFI_BAND_24_GHZ,
                TEST_PACKAGE_NAME, TEST_FEATURE_ID, mExtras);
    }

    /**
     * Tests that {@link WifiScanningServiceImpl#getAvailableChannels(int, String)} returns
     * the expected result if the caller does hold the required permissions.
     */
    @Test
    public void getAvailableChannels_hasPermission_returnsSuccessfully() throws Exception {
        startServiceAndLoadDriver();

        // Client doesn't have NETWORK_STACK permission.
        doThrow(new SecurityException()).when(mContext).enforcePermission(
                eq(Manifest.permission.NETWORK_STACK), anyInt(), eq(Binder.getCallingUid()), any());

        // has access scan results permission
        doNothing().when(mWifiPermissionsUtil).enforceCanAccessScanResultsForWifiScanner(
                TEST_PACKAGE_NAME, TEST_FEATURE_ID, Binder.getCallingUid(), false, false);

        mLooper.startAutoDispatch();
        Bundle bundle = mWifiScanningServiceImpl.getAvailableChannels(
                WifiScanner.WIFI_BAND_24_GHZ, TEST_PACKAGE_NAME, TEST_FEATURE_ID, mExtras);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        List<Integer> actual = bundle.getIntegerArrayList(GET_AVAILABLE_CHANNELS_EXTRA);

        List<Integer> expected = Arrays.asList(2412, 2450);
        assertEquals(expected, actual);
    }

    /**
     * Tests that {@link WifiScanningServiceImpl#getAvailableChannels(int, String)} returns
     * an empty array when wifi is off.
     */
    @Test
    public void getAvailableChannels_DoesNotCrashWhenWifiDisabled() throws Exception {
        // Don't enable wifi.

        // Client doesn't have NETWORK_STACK permission.
        doThrow(new SecurityException()).when(mContext).enforcePermission(
                eq(Manifest.permission.NETWORK_STACK), anyInt(), eq(Binder.getCallingUid()), any());

        // has access scan results permission
        doNothing().when(mWifiPermissionsUtil).enforceCanAccessScanResultsForWifiScanner(
                TEST_PACKAGE_NAME, TEST_FEATURE_ID, Binder.getCallingUid(), false, false);

        mLooper.startAutoDispatch();
        Bundle bundle = mWifiScanningServiceImpl.getAvailableChannels(
                WifiScanner.WIFI_BAND_24_GHZ, TEST_PACKAGE_NAME, TEST_FEATURE_ID, mExtras);
        mLooper.stopAutoDispatchAndIgnoreExceptions();
        List<Integer> actual = bundle.getIntegerArrayList(GET_AVAILABLE_CHANNELS_EXTRA);

        assertTrue(actual.isEmpty());
    }

    private WifiScanner.ScanSettings triggerEmergencySingleScanAndVerify(WorkSource ws,
            TestClient client)
            throws Exception {
        WifiScanner.ScanSettings requestSettings =
                createRequest(WifiScanner.WIFI_BAND_ALL, 0, 0, 20,
                        WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        requestSettings.ignoreLocationSettings = true; // set emergency scan flag.
        client.sendSingleScanRequest(requestSettings, ws);

        // Scan is successfully queued
        client.verifySuccessfulResponse();
        return requestSettings;
    }

    private void sendEmergencySingleScanResultsAndVerify(
            WorkSource ws, WifiScanner.ScanSettings requestSettings,
            ScanResults results, InOrder order, TestClient client)
            throws Exception {
        // verify scan start
        WifiNative.ScanEventHandler eventHandler =
                verifyStartSingleScan(order, computeSingleScanNativeSettings(requestSettings));
        verify(mBatteryStats).reportWifiScanStartedFromSource(eq(ws));

        // dispatch scan results
        when(mWifiScannerImpl0.getLatestSingleScanResults()).thenReturn(results.getScanData());
        eventHandler.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
        mLooper.dispatchAll();

        client.verifyScanResultsReceived(results.getScanData());
        client.verifySingleScanCompletedReceived();
        verify(mBatteryStats).reportWifiScanStoppedFromSource(eq(ws));
    }

    @Test
    public void startServiceAndTriggerEmergencySingleScanWithoutDriverLoaded() throws Exception {
        mWifiScanningServiceImpl.startService();
        mLooper.dispatchAll();
        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0);
        WorkSource ws = new WorkSource(2292);
        ScanResults results = ScanResults.create(0, WifiScanner.WIFI_BAND_ALL, 2412);

        // successful start
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        // emergency scan request
        WifiScanner.ScanSettings requestSettings =
                triggerEmergencySingleScanAndVerify(ws, client);

        // Indicate start of emergency scan.
        verify(mWifiManager).setEmergencyScanRequestInProgress(true);

        // Now simulate WifiManager enabling scanning.
        setupAndLoadDriver(TEST_MAX_SCAN_BUCKETS_IN_CAPABILITIES);

        // Send native results & verify
        sendEmergencySingleScanResultsAndVerify(
                ws, requestSettings, results, order, client);

        // Ensure that we indicate the end of emergency scan processing after the timeout.
        mAlarmManager.dispatch(EMERGENCY_SCAN_END_INDICATION_ALARM_TAG);
        mLooper.dispatchAll();
        verify(mWifiManager).setEmergencyScanRequestInProgress(false);
    }

    @Test
    public void startServiceAndTriggerEmergencySuccessiveSingleScanWithoutDriverLoaded()
            throws Exception {
        mWifiScanningServiceImpl.startService();
        mLooper.dispatchAll();
        TestClient client1 = new TestClient();
        TestClient client2 = new TestClient();
        InOrder order = inOrder(client1.listener, client2.listener, mWifiScannerImpl0);
        WorkSource ws = new WorkSource(2292);
        ScanResults results = ScanResults.create(0, WifiScanner.WIFI_BAND_ALL, 2412);

        // successful start
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        // emergency scan request 1
        WifiScanner.ScanSettings requestSettings1 =
                triggerEmergencySingleScanAndVerify(ws, client1);

        // Indicate start of emergency scan.
        verify(mWifiManager).setEmergencyScanRequestInProgress(true);

        // Now simulate WifiManager enabling scanning.
        setupAndLoadDriver(TEST_MAX_SCAN_BUCKETS_IN_CAPABILITIES);

        // Send native results & verify
        sendEmergencySingleScanResultsAndVerify(
                ws, requestSettings1, results, order, client1);

        // emergency scan request 2
        clearInvocations(mBatteryStats);

        WifiScanner.ScanSettings requestSettings2 = triggerEmergencySingleScanAndVerify(ws,
                client2);

        // Indicate start of emergency scan.
        verify(mWifiManager, times(2)).setEmergencyScanRequestInProgress(true);

        // Send native results 2 & verify
        sendEmergencySingleScanResultsAndVerify(
                ws, requestSettings2, results, order, client2);

        // Ensure that we indicate only 1 end of emergency scan processing after the timeout.
        mAlarmManager.dispatch(EMERGENCY_SCAN_END_INDICATION_ALARM_TAG);
        mLooper.dispatchAll();
        verify(mWifiManager, times(1)).setEmergencyScanRequestInProgress(false);
    }

    @Test
    public void testStopPnoScanNullSetting() throws Exception {
        startServiceAndLoadDriver();
        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0);

        ScanResults scanResults = createScanResultsForPno();
        Pair<WifiScanner.ScanSettings, WifiNative.ScanSettings> scanSettings =
                createScanSettingsForHwPno();
        Pair<WifiScanner.PnoSettings, WifiNative.PnoSettings> pnoSettings =
                createPnoSettings(scanResults);

        client.sendPnoScanRequest(scanSettings.first, pnoSettings.first);
        expectHwPnoScan(order, client.listener, pnoSettings.second, scanResults);

        mWifiScanningServiceImpl.stopPnoScan(client.listener, TEST_PACKAGE_NAME, null);
        mLooper.dispatchAll();
    }

    /**
     * Do a single scan and then stop.
     * Expect scan canceled.
     */
    @Test
    public void sendSingleScanRequestAndThenStop()
            throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(channelsToSpec(2412), 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        ScanResults results = ScanResults.create(0, WifiScanner.WIFI_BAND_UNSPECIFIED, 2412);

        startServiceAndLoadDriver();

        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0);

        // Run scan
        client.sendSingleScanRequest(requestSettings, null);
        mLooper.dispatchAll();
        client.verifySuccessfulResponse();
        WifiNative.ScanEventHandler eventHandler = verifyStartSingleScan(order,
                computeSingleScanNativeSettings(requestSettings));
        //Stop scan
        mWifiScanningServiceImpl.stopScan(client.listener, TEST_PACKAGE_NAME, TEST_FEATURE_ID);
        mLooper.dispatchAll();
        // dispatch scan results
        when(mWifiScannerImpl0.getLatestSingleScanResults())
                .thenReturn(results.getScanData());
        eventHandler.onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);

        // Stopped scan should not receive results
        mLooper.dispatchAll();
        order.verify(client.listener, never()).onResults(any());
        order.verify(client.listener, never()).onSingleScanCompleted();
    }

    /**
     * Verify that scan abort failure message is received by the listener.
     */
    @Test
    public void sendSingleScanRequestWhichGetsAbortedAfterStart() throws Exception {

        WifiScanner.ScanSettings requestSettings = createRequest(WifiScanner.WIFI_BAND_BOTH, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);

        startServiceAndLoadDriver();

        TestClient client = new TestClient();
        InOrder order = inOrder(client.listener, mWifiScannerImpl0);

        // successful start
        when(mWifiScannerImpl0.startSingleScan(any(WifiNative.ScanSettings.class),
                any(WifiNative.ScanEventHandler.class))).thenReturn(true);

        client.sendSingleScanRequest(requestSettings, null);

        // Scan is successfully queue
        mLooper.dispatchAll();
        WifiNative.ScanEventHandler eventHandler =
                verifyStartSingleScan(order, computeSingleScanNativeSettings(requestSettings));
        client.verifySuccessfulResponse();

        // scan is aborted
        eventHandler.onScanRequestFailed(WifiScanner.REASON_ABORT);
        mLooper.dispatchAll();
        client.verifyFailedResponse(
                WifiScanner.REASON_ABORT,
                "Scan aborted");
    }
}
