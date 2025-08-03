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

import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SCAN_ONLY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT;
import static com.android.server.wifi.ClientModeImpl.WIFI_WORK_SOURCE;
import static com.android.server.wifi.WifiConfigurationTestUtil.generateWifiConfig;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.AlarmManager;
import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.app.test.TestAlarmManager;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager;
import android.net.IpConfiguration;
import android.net.MacAddress;
import android.net.wifi.IPnoScanResultsCallback;
import android.net.wifi.ScanResult;
import android.net.wifi.ScanResult.InformationElement;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSelectionConfig;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.PnoSettings;
import android.net.wifi.WifiScanner.ScanData;
import android.net.wifi.WifiScanner.ScanSettings;
import android.net.wifi.WifiSsid;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.util.ScanResultUtil;
import android.os.Handler;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.IThermalService;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.WorkSource;
import android.os.test.TestLooper;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.LocalLog;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.ActiveModeWarden.ExternalClientModeManagerRequestListener;
import com.android.server.wifi.hotspot2.PasspointManager;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.proto.nano.WifiMetricsProto;
import com.android.server.wifi.scanner.WifiScannerInternal;
import com.android.server.wifi.util.LruConnectionTracker;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unit tests for {@link com.android.server.wifi.WifiConnectivityManager}.
 */
@SmallTest
public class WifiConnectivityManagerTest extends WifiBaseTest {
    /**
     * Called before each test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mResources = new MockResources();
        setUpResources(mResources);
        mAlarmManager = new TestAlarmManager();
        mContext = mockContext();
        mLocalLog = new LocalLog(512);
        setupMockForClientModeManager(mPrimaryClientModeManager);
        mWifiConfigManager = mockWifiConfigManager();
        mWifiInfo = getWifiInfo();
        mScanData = mockScanData();
        mockWifiScanner();
        mWifiConnectivityHelper = mockWifiConnectivityHelper();
        mWifiNS = mockWifiNetworkSelector();
        mLooper = new TestLooper(mClock::getElapsedSinceBootMillis);
        mTestHandler = new TestHandler(mLooper.getLooper());
        WifiLocalServices.removeServiceForTest(WifiScannerInternal.class);
        WifiLocalServices.addService(WifiScannerInternal.class, mWifiScanner);
        when(mWifiNetworkSuggestionsManager.retrieveHiddenNetworkList(anyBoolean()))
                .thenReturn(new ArrayList<>());
        when(mWifiNetworkSuggestionsManager.getAllApprovedNetworkSuggestions())
                .thenReturn(new HashSet<>());
        when(mPasspointManager.getProviderConfigs(anyInt(), anyBoolean()))
                .thenReturn(new ArrayList<>());
        mPowerManagerService = mock(IPowerManager.class);
        PowerManager powerManager =
                new PowerManager(mContext, mPowerManagerService, mock(IThermalService.class),
                        new Handler());
        when(mContext.getSystemService(PowerManager.class)).thenReturn(powerManager);
        when(powerManager.isInteractive()).thenReturn(false);
        when(mPrimaryClientModeManager.getRole()).thenReturn(ActiveModeManager.ROLE_CLIENT_PRIMARY);
        when(mPrimaryClientModeManager.getConnectionInfo()).thenReturn(mWifiInfo);
        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(mPrimaryClientModeManager);
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(mPrimaryClientModeManager);
            }
        }).when(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(), eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS), any(), any());
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(mPrimaryClientModeManager);
            }
        }).when(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());

        mWifiConnectivityManager = createConnectivityManager();
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        mWifiConnectivityManager.enableVerboseLogging(true);
        setWifiEnabled(true);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(CURRENT_SYSTEM_TIME_MS);
        when(mWifiLastResortWatchdog.shouldIgnoreBssidUpdate(anyString())).thenReturn(false);
        mLruConnectionTracker = new LruConnectionTracker(100, mContext);
        Comparator<WifiConfiguration> comparator =
                Comparator.comparingInt(mLruConnectionTracker::getAgeIndexOfNetwork);
        when(mWifiConfigManager.getScanListComparator()).thenReturn(comparator);

        // Need to mock WifiInjector since some code used in WifiConnectivityManager calls
        // WifiInjector.getInstance().
        mSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(WifiInjector.class, withSettings().lenient())
                .mockStatic(WifiStatsLog.class)
                .startMocking();
        WifiInjector wifiInjector = mock(WifiInjector.class);
        when(wifiInjector.getActiveModeWarden()).thenReturn(mActiveModeWarden);
        when(wifiInjector.getWifiGlobals()).thenReturn(mWifiGlobals);
        when(wifiInjector.getDppManager()).thenReturn(mDppManager);
        when(wifiInjector.getHalDeviceManager()).thenReturn(mHalDeviceManager);
        lenient().when(WifiInjector.getInstance()).thenReturn(wifiInjector);
        when(mSsidTranslator.getAllPossibleOriginalSsids(any())).thenAnswer(
                (Answer<List<WifiSsid>>) invocation -> Arrays.asList(invocation.getArgument(0),
                        WifiSsid.fromString(UNTRANSLATED_HEX_SSID))
        );
        when(mWifiDialogManager.createSimpleDialog(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mDialogHandle);
    }

    private void setUpResources(MockResources resources) {
        resources.setBoolean(
                R.bool.config_wifi_framework_enable_associated_network_selection, true);
        resources.setInteger(
                R.integer.config_wifi_framework_wifi_score_good_rssi_threshold_24GHz, -60);
        resources.setInteger(
                R.integer.config_wifiFrameworkMinPacketPerSecondActiveTraffic, 16);
        resources.setIntArray(
                R.array.config_wifiConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC);
        resources.setIntArray(
                R.array.config_wifiDisconnectedScanIntervalScheduleSec,
                VALID_DISCONNECTED_SINGLE_SCAN_SCHEDULE_SEC);
        resources.setIntArray(R.array.config_wifiConnectedScanType,
                VALID_CONNECTED_SINGLE_SCAN_TYPE);
        resources.setIntArray(R.array.config_wifiDisconnectedScanType,
                VALID_DISCONNECTED_SINGLE_SCAN_TYPE);
        resources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                SCHEDULE_EMPTY_SEC);
        resources.setInteger(
                R.integer.config_wifiHighMovementNetworkSelectionOptimizationScanDelayMs,
                HIGH_MVMT_SCAN_DELAY_MS);
        resources.setInteger(
                R.integer.config_wifiHighMovementNetworkSelectionOptimizationRssiDelta,
                HIGH_MVMT_RSSI_DELTA);
        resources.setInteger(R.integer.config_wifiInitialPartialScanChannelCacheAgeMins,
                CHANNEL_CACHE_AGE_MINS);
        resources.setInteger(R.integer.config_wifiMovingPnoScanIntervalMillis,
                MOVING_PNO_SCAN_INTERVAL_MILLIS);
        resources.setInteger(R.integer.config_wifiStationaryPnoScanIntervalMillis,
                STATIONARY_PNO_SCAN_INTERVAL_MILLIS);
        resources.setInteger(R.integer.config_wifiPnoScanLowRssiNetworkRetryStartDelaySec,
                LOW_RSSI_NETWORK_RETRY_START_DELAY_SEC);
        resources.setInteger(R.integer.config_wifiPnoScanLowRssiNetworkRetryMaxDelaySec,
                LOW_RSSI_NETWORK_RETRY_MAX_DELAY_SEC);
        resources.setBoolean(R.bool.config_wifiEnable6ghzPscScanning, true);
        resources.setBoolean(R.bool.config_wifiUseHalApiToDisableFwRoaming, true);
        resources.setInteger(R.integer.config_wifiPnoScanIterations, EXPECTED_PNO_ITERATIONS);
        resources.setInteger(R.integer.config_wifiPnoScanIntervalMultiplier,
                EXPECTED_PNO_MULTIPLIER);
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    private WifiContext mContext;
    private TestAlarmManager mAlarmManager;
    private TestLooper mLooper;
    private TestHandler mTestHandler;
    private WifiConnectivityManager mWifiConnectivityManager;
    private WifiNetworkSelector mWifiNS;
    private WifiConnectivityHelper mWifiConnectivityHelper;
    private ScanData mScanData;
    private WifiConfigManager mWifiConfigManager;
    private WifiInfo mWifiInfo;
    private LocalLog mLocalLog;
    private LruConnectionTracker mLruConnectionTracker;
    @Mock private WifiScannerInternal mWifiScanner;
    @Mock private Clock mClock;
    @Mock private WifiLastResortWatchdog mWifiLastResortWatchdog;
    @Mock private OpenNetworkNotifier mOpenNetworkNotifier;
    @Mock private WifiMetrics mWifiMetrics;
    @Mock private WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    @Mock private WifiBlocklistMonitor mWifiBlocklistMonitor;
    @Mock private WifiChannelUtilization mWifiChannelUtilization;
    @Mock private ScoringParams mScoringParams;
    @Mock private WifiScoreCard mWifiScoreCard;
    @Mock private PasspointManager mPasspointManager;
    @Mock private FrameworkFacade mFacade;
    @Mock private MultiInternetManager mMultiInternetManager;
    @Mock private WifiScoreCard.PerNetwork mPerNetwork;
    @Mock private WifiScoreCard.PerNetwork mPerNetwork1;
    @Mock private PasspointConfiguration mPasspointConfiguration;
    @Mock private WifiConfiguration mSuggestionConfig;
    @Mock private WifiNetworkSuggestion mWifiNetworkSuggestion;
    @Mock private IPowerManager mPowerManagerService;
    @Mock private DeviceConfigFacade mDeviceConfigFacade;
    @Mock private ActiveModeWarden mActiveModeWarden;
    @Mock private ConcreteClientModeManager mPrimaryClientModeManager;
    @Mock private ConcreteClientModeManager mSecondaryClientModeManager;
    @Mock private WifiGlobals mWifiGlobals;
    @Mock private ExternalPnoScanRequestManager mExternalPnoScanRequestManager;
    @Mock private SsidTranslator mSsidTranslator;
    @Mock private WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock private WifiCarrierInfoManager mWifiCarrierInfoManager;
    @Mock private WifiCountryCode mWifiCountryCode;
    @Mock private DppManager mDppManager;
    @Mock private WifiDialogManager mWifiDialogManager;
    @Mock private WifiDialogManager.DialogHandle mDialogHandle;
    @Mock private WifiInjector mWifiInjector;
    @Mock private HalDeviceManager mHalDeviceManager;
    @Mock WifiCandidates.Candidate mCandidate1;
    @Mock WifiCandidates.Candidate mCandidate2;
    @Mock WifiCandidates.Candidate mCandidate3;
    @Mock WifiCandidates.Candidate mCandidate4;
    @Mock WifiDeviceStateChangeManager mWifiDeviceStateChangeManager;
    private WifiConfiguration mCandidateWifiConfig1;
    private WifiConfiguration mCandidateWifiConfig2;
    private List<WifiCandidates.Candidate> mCandidateList;
    @Captor ArgumentCaptor<String> mCandidateBssidCaptor;
    @Captor ArgumentCaptor<WifiConfigManager.OnNetworkUpdateListener>
            mNetworkUpdateListenerCaptor;
    @Captor ArgumentCaptor<WifiNetworkSuggestionsManager.OnSuggestionUpdateListener>
            mSuggestionUpdateListenerCaptor;
    @Captor ArgumentCaptor<ActiveModeWarden.ModeChangeCallback> mModeChangeCallbackCaptor;
    @Captor ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor;

    @Captor
    ArgumentCaptor<WifiDeviceStateChangeManager.StateChangeCallback>
            mStateChangeCallbackArgumentCaptor;

    @Captor ArgumentCaptor<MultiInternetManager.ConnectionStatusListener>
            mMultiInternetConnectionStatusListenerCaptor;
    @Captor ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> mSimpleDialogCallbackCaptor;
    private MockitoSession mSession;
    private MockResources mResources;

    private static final int CANDIDATE_NETWORK_ID = 0;
    private static final int CANDIDATE_NETWORK_ID_2 = 2;
    private static final String CANDIDATE_SSID = "\"AnSsid\"";
    private static final String CANDIDATE_SSID_2 = "\"AnSsid2\"";
    private static final String CANDIDATE_BSSID = "6c:f3:7f:ae:8c:f3";
    private static final String CANDIDATE_BSSID_2 = "6c:f3:7f:ae:8d:f3";
    private static final String CANDIDATE_BSSID_3 = "6c:f3:7f:ae:8c:f4";
    private static final String CANDIDATE_BSSID_4 = "6c:f3:7f:ae:8c:f5";
    private static final String CANDIDATE_BSSID_5 = "6c:f3:7f:ae:8c:f6";
    private static final String INVALID_SCAN_RESULT_BSSID = "6c:f3:7f:ae:8c:f4";
    private static final int TEST_FREQUENCY = 2420;
    private static final long CURRENT_SYSTEM_TIME_MS = 1000;
    private static final int MAX_BSSID_BLOCKLIST_SIZE = 16;

    // Scan schedule and corresponding scan types
    private static final int[] VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC = {10, 30, 50};
    private static final int[] VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC = {15, 35, 55};
    private static final int[] VALID_DISCONNECTED_SINGLE_SCAN_SCHEDULE_SEC = {25, 40, 60};
    private static final int[] VALID_CONNECTED_SINGLE_SCAN_TYPE = {1, 0, 0};
    private static final int[] VALID_CONNECTED_SINGLE_SAVED_NETWORK_TYPE = {2, 0, 1};
    private static final int[] VALID_DISCONNECTED_SINGLE_SCAN_TYPE = {2, 1, 1};
    private static final int[] VALID_EXTERNAL_SINGLE_SCAN_SCHEDULE_SEC = {40, 80};
    private static final int[] VALID_EXTERNAL_SINGLE_SCAN_TYPE = {1, 0};

    private static final int[] SCHEDULE_EMPTY_SEC = {};
    private static final int[] INVALID_SCHEDULE_NEGATIVE_VALUES_SEC = {10, -10, 20};
    private static final int[] INVALID_SCHEDULE_ZERO_VALUES_SEC = {10, 0, 20};
    private static final int MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC = 60;
    private static final int[] DEFAULT_SINGLE_SCAN_SCHEDULE_SEC = {20, 40, 80, 160};
    private static final int[] DEFAULT_SINGLE_SCAN_TYPE = {2, 2, 2, 2};
    private static final int MAX_SCAN_INTERVAL_IN_DEFAULT_SCHEDULE_SEC = 160;
    private static final int TEST_FREQUENCY_1 = 2412;
    private static final int TEST_FREQUENCY_2 = 5180;
    private static final int TEST_FREQUENCY_3 = 5240;
    private static final int TEST_CURRENT_CONNECTED_FREQUENCY = 2427;
    private static final int HIGH_MVMT_SCAN_DELAY_MS = 10000;
    private static final int HIGH_MVMT_RSSI_DELTA = 10;
    private static final String TEST_FQDN = "FQDN";
    private static final String TEST_SSID = "SSID";
    private static final String UNTRANSLATED_HEX_SSID = "abcdef";
    private static final int TEMP_BSSID_BLOCK_DURATION_MS = 10 * 1000; // 10 seconds
    private static final int TEST_CONNECTED_NETWORK_ID = 55;
    private static final String TEST_CONNECTED_BSSID = "6c:f3:7f:ae:8c:f1";
    private static final int CHANNEL_CACHE_AGE_MINS = 14400;
    private static final int MOVING_PNO_SCAN_INTERVAL_MILLIS = 20_000;
    private static final int STATIONARY_PNO_SCAN_INTERVAL_MILLIS = 60_000;
    private static final int POWER_SAVE_SCAN_INTERVAL_MULTIPLIER = 2;
    private static final int LOW_RSSI_NETWORK_RETRY_START_DELAY_SEC = 20;
    private static final int LOW_RSSI_NETWORK_RETRY_MAX_DELAY_SEC = 80;
    private static final int SCAN_TRIGGER_TIMES = 7;
    private static final long NETWORK_CHANGE_TRIGGER_PNO_THROTTLE_MS = 3000; // 3 seconds
    private static final int EXPECTED_PNO_ITERATIONS = 3;
    private static final int EXPECTED_PNO_MULTIPLIER = 4;
    private static final int TEST_FREQUENCY_2G = 2412;
    private static final int TEST_FREQUENCY_5G = 5262;

    /**
    * A test Handler that stores one single incoming Message with delayed time internally, to be
    * able to manually triggered by calling {@link #timeAdvance}. Only one delayed message can be
    * scheduled at a time. The scheduled delayed message intervals are recorded and returned by
    * {@link #getIntervals}. The intervals are cleared by calling {@link #reset}.
    */
    private class TestHandler extends RunnerHandler {
        private ArrayList<Long> mIntervals = new ArrayList<>();
        private Message mMessage;

        TestHandler(Looper looper) {
            super(looper, 100, new LocalLog(128));
        }

        public List<Long> getIntervals() {
            return mIntervals;
        }

        public void reset() {
            mIntervals.clear();
        }

        public void timeAdvance() {
            if (mMessage != null) {
                // Dispatch the message without waiting.
                super.dispatchMessage(mMessage);
            }
        }

        @Override
        public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
            // uptimeMillis is an absolute time obtained as SystemClock.uptimeMillis() + delay
            // in Handler and can't be replaced with customized clock.
            // if custom clock is given, recalculate the time with regards to it
            long delayMs = uptimeMillis - SystemClock.uptimeMillis();
            if (delayMs > 0) {
                mIntervals.add(delayMs);
                mMessage = msg;
            }
            uptimeMillis = delayMs + mClock.getElapsedSinceBootMillis();
            // Message is still queued to super, so it doesn't get filtered out and rely on the
            // timeAdvance() to dispatch. timeAdvance() can force time to advance and send the
            // message immediately. If it is not called not the message can still be dispatched
            // at the time the message is scheduled.
            return super.sendMessageAtTime(msg, uptimeMillis);
        }
    }

    WifiContext mockContext() {
        WifiContext context = mock(WifiContext.class);

        when(context.getResources()).thenReturn(mResources);
        when(context.getSystemService(AlarmManager.class)).thenReturn(
                mAlarmManager.getAlarmManager());
        when(context.getPackageManager()).thenReturn(mock(PackageManager.class));

        return context;
    }

    ScanData mockScanData() {
        ScanData scanData = mock(ScanData.class);

        when(scanData.getScannedBandsInternal()).thenReturn(WifiScanner.WIFI_BAND_ALL);

        return scanData;
    }

    void mockWifiScanner() {
        ArgumentCaptor<WifiScannerInternal.ScanListener> allSingleScanListenerCaptor =
                ArgumentCaptor.forClass(WifiScannerInternal.ScanListener.class);

        doNothing().when(mWifiScanner).registerScanListener(allSingleScanListenerCaptor.capture());

        ScanData[] scanDatas = new ScanData[1];
        scanDatas[0] = mScanData;

        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, WifiScannerInternal.ScanListener listener)
                    throws Exception {
                listener.getWifiScannerListener().onResults(scanDatas);
                // WCM processes scan results received via onFullResult (even though they're the
                // same as onResult for single scans).
                if (mScanData != null && mScanData.getResults() != null) {
                    for (int i = 0; i < mScanData.getResults().length; i++) {
                        allSingleScanListenerCaptor.getValue().getWifiScannerListener()
                                .onFullResult(mScanData.getResults()[i]);
                    }
                }
                allSingleScanListenerCaptor.getValue().getWifiScannerListener().onResults(
                        scanDatas);
            }}).when(mWifiScanner).startScan(anyObject(), anyObject());

        // This unfortunately needs to be a somewhat valid scan result, otherwise
        // |ScanDetailUtil.toScanDetail| raises exceptions.
        final ScanResult[] scanResults = new ScanResult[1];
        scanResults[0] = new ScanResult.Builder(
                WifiSsid.fromUtf8Text(CANDIDATE_SSID), CANDIDATE_BSSID)
                .setHessid(1245)
                .setCaps("some caps")
                .setRssi(-78)
                .setFrequency(2450)
                .setTsf(1025)
                .setDistanceCm(22)
                .setDistanceSdCm(33)
                .setIs80211McRTTResponder(true)
                .build();
        scanResults[0].informationElements = new InformationElement[1];
        scanResults[0].informationElements[0] = new InformationElement();
        scanResults[0].informationElements[0].id = InformationElement.EID_SSID;
        scanResults[0].informationElements[0].bytes =
            CANDIDATE_SSID.getBytes(StandardCharsets.UTF_8);

        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, PnoSettings pnoSettings,
                    WifiScannerInternal.ScanListener listener) throws Exception {
                WifiScanner.PnoScanListener l =
                        (WifiScanner.PnoScanListener) listener.getWifiScannerListener();
                l.onPnoNetworkFound(scanResults);
            }}).when(mWifiScanner).startPnoScan(
                    anyObject(), anyObject(), anyObject());

    }

    WifiConnectivityHelper mockWifiConnectivityHelper() {
        WifiConnectivityHelper connectivityHelper = mock(WifiConnectivityHelper.class);

        when(connectivityHelper.isFirmwareRoamingSupported()).thenReturn(false);
        when(connectivityHelper.getMaxNumBlocklistBssid()).thenReturn(MAX_BSSID_BLOCKLIST_SIZE);

        return connectivityHelper;
    }

    private void setupMockForClientModeManager(ConcreteClientModeManager cmm) {
        when(cmm.getRole()).thenReturn(ActiveModeManager.ROLE_CLIENT_PRIMARY);
        when(cmm.isConnected()).thenReturn(false);
        when(cmm.isDisconnected()).thenReturn(true);
        when(cmm.isSupplicantTransientState()).thenReturn(false);
        when(cmm.enableRoaming(anyBoolean())).thenReturn(true);
    }

    WifiNetworkSelector mockWifiNetworkSelector() {
        WifiNetworkSelector ns = mock(WifiNetworkSelector.class);

        WifiConfiguration candidate = generateWifiConfig(
                0, CANDIDATE_NETWORK_ID, CANDIDATE_SSID, false, true, null, null,
                WifiConfigurationTestUtil.SECURITY_NONE);
        candidate.BSSID = ClientModeImpl.SUPPLICANT_BSSID_ANY;
        ScanResult candidateScanResult = new ScanResult();
        candidateScanResult.SSID = CANDIDATE_SSID;
        candidateScanResult.BSSID = CANDIDATE_BSSID;
        candidate.getNetworkSelectionStatus().setCandidate(candidateScanResult);
        mCandidateWifiConfig1 = candidate;
        mCandidateWifiConfig2 = new WifiConfiguration(candidate);
        mCandidateWifiConfig2.networkId = CANDIDATE_NETWORK_ID_2;

        when(mWifiConfigManager.getConfiguredNetwork(CANDIDATE_NETWORK_ID)).thenReturn(candidate);
        MacAddress macAddress = MacAddress.fromString(CANDIDATE_BSSID);
        ScanResultMatchInfo matchInfo = mock(ScanResultMatchInfo.class);
        // Assume that this test use the default security params.
        when(matchInfo.getDefaultSecurityParams()).thenReturn(candidate.getDefaultSecurityParams());
        WifiCandidates.Key key = new WifiCandidates.Key(matchInfo,
                macAddress, 0);
        when(mCandidate1.getKey()).thenReturn(key);
        when(mCandidate1.getScanRssi()).thenReturn(-40);
        when(mCandidate1.getFrequency()).thenReturn(TEST_FREQUENCY);
        when(mCandidate2.getKey()).thenReturn(key);
        when(mCandidate2.getScanRssi()).thenReturn(-60);
        mCandidateList = new ArrayList<WifiCandidates.Candidate>();
        mCandidateList.add(mCandidate1);
        when(ns.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(mCandidateList);
        when(ns.selectNetwork(any()))
                .then(new AnswerWithArguments() {
                    public WifiConfiguration answer(List<WifiCandidates.Candidate> candidateList) {
                        if (candidateList == null || candidateList.size() == 0) {
                            return null;
                        }
                        return candidate;
                    }
                });
        when(ns.isSufficiencyCheckEnabled()).thenReturn(true);
        when(ns.isAssociatedNetworkSelectionEnabled()).thenReturn(true);
        return ns;
    }

    WifiInfo getWifiInfo() {
        WifiInfo wifiInfo = new WifiInfo();

        wifiInfo.setNetworkId(WifiConfiguration.INVALID_NETWORK_ID);
        wifiInfo.setBSSID(null);
        wifiInfo.setSupplicantState(SupplicantState.DISCONNECTED);

        return wifiInfo;
    }

    WifiConfigManager mockWifiConfigManager() {
        WifiConfigManager wifiConfigManager = mock(WifiConfigManager.class);
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.getNetworkSelectionStatus().setHasEverConnected(true);
        List<WifiConfiguration> networkList = new ArrayList<>();
        networkList.add(config);
        when(wifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(null);
        when(wifiConfigManager.getSavedNetworks(anyInt())).thenReturn(networkList);

        return wifiConfigManager;
    }

    WifiConnectivityManager createConnectivityManager() {
        WifiConnectivityManager wCm =
                new WifiConnectivityManager(
                        mContext,
                        mScoringParams,
                        mWifiConfigManager,
                        mWifiNetworkSuggestionsManager,
                        mWifiNS,
                        mWifiConnectivityHelper,
                        mWifiLastResortWatchdog,
                        mOpenNetworkNotifier,
                        mWifiMetrics,
                        mTestHandler,
                        mClock,
                        mLocalLog,
                        mWifiScoreCard,
                        mWifiBlocklistMonitor,
                        mWifiChannelUtilization,
                        mPasspointManager,
                        mMultiInternetManager,
                        mDeviceConfigFacade,
                        mActiveModeWarden,
                        mFacade,
                        mWifiGlobals,
                        mExternalPnoScanRequestManager,
                        mSsidTranslator,
                        mWifiPermissionsUtil,
                        mWifiCarrierInfoManager,
                        mWifiCountryCode,
                        mWifiDialogManager,
                        mWifiDeviceStateChangeManager);
        mLooper.dispatchAll();
        verify(mActiveModeWarden, atLeastOnce()).registerModeChangeCallback(
                mModeChangeCallbackCaptor.capture());
        verify(mWifiDeviceStateChangeManager, atLeastOnce())
                .registerStateChangeCallback(mStateChangeCallbackArgumentCaptor.capture());
        setScreenState(false);
        verify(mWifiConfigManager, atLeastOnce()).addOnNetworkUpdateListener(
                mNetworkUpdateListenerCaptor.capture());
        verify(mWifiNetworkSuggestionsManager, atLeastOnce()).addOnSuggestionUpdateListener(
                mSuggestionUpdateListenerCaptor.capture());
        verify(mMultiInternetManager, atLeastOnce()).setConnectionStatusListener(
                mMultiInternetConnectionStatusListenerCaptor.capture());
        return wCm;
    }

    void setWifiStateConnected() {
        setWifiStateConnected(TEST_CONNECTED_NETWORK_ID, TEST_CONNECTED_BSSID);
    }

    void setWifiStateConnected(int networkId, String bssid) {
        // Prep for setting WiFi to connected state
        WifiConfiguration connectedWifiConfiguration = new WifiConfiguration();
        connectedWifiConfiguration.networkId = networkId;
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(connectedWifiConfiguration);
        when(mPrimaryClientModeManager.getConnectedBssid())
                .thenReturn(bssid);

        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);
        mLooper.dispatchAll();
    }

    /**
     * Verify that a primary CMM changing role to secondary transient (MBB) will not trigger cleanup
     * that's meant to be done when wifi is disabled.
     */
    @Test
    public void testPrimaryToSecondaryTransientDoesNotDisableWifi() {
        ConcreteClientModeManager cmm = mock(ConcreteClientModeManager.class);
        when(cmm.getPreviousRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(cmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers()).thenReturn(
                Collections.EMPTY_LIST);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(cmm);
        verify(mWifiConfigManager, never()).removeAllEphemeralOrPasspointConfiguredNetworks();
    }

    /**
     * Verify that the primary CMM switching to scan only mode will trigger cleanup code.
     */
    @Test
    public void testPrimaryToScanOnlyWillDisableWifi() {
        ConcreteClientModeManager cmm = mock(ConcreteClientModeManager.class);
        when(cmm.getPreviousRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(cmm.getRole()).thenReturn(ROLE_CLIENT_SCAN_ONLY);
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers()).thenReturn(
                Collections.EMPTY_LIST);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(cmm);
        verify(mWifiConfigManager).removeAllEphemeralOrPasspointConfiguredNetworks();
    }

    /**
     * Don't connect to the candidate network if we're already connected to that network on the
     * primary ClientModeManager.
     */
    @Test
    public void alreadyConnectedOnPrimaryCmm_dontConnectAgain() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        // Set screen to on
        setScreenState(true);

        WifiConfiguration config = new WifiConfiguration();
        config.networkId = CANDIDATE_NETWORK_ID;
        when(mPrimaryClientModeManager.getConnectingWifiConfiguration()).thenReturn(config);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        verify(mActiveModeWarden, never()).requestSecondaryTransientClientModeManager(
                any(), any(), any(), any());
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
    }

    /** Connect using the primary ClientModeManager if it's not connected to anything */
    @Test
    public void disconnectedOnPrimaryCmm_connectUsingPrimaryCmm() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        // Set screen to on
        setScreenState(true);

        when(mPrimaryClientModeManager.getConnectedWifiConfiguration()).thenReturn(null);
        when(mPrimaryClientModeManager.getConnectingWifiConfiguration()).thenReturn(null);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        assertEquals(WifiConnectivityManager.WIFI_STATE_DISCONNECTED,
                mWifiConnectivityManager.getWifiState());
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mPrimaryClientModeManager).enableRoaming(true);
        verify(mActiveModeWarden).stopAllClientModeManagersInRole(ROLE_CLIENT_SECONDARY_TRANSIENT);
        verify(mActiveModeWarden, never()).requestSecondaryTransientClientModeManager(
                any(), any(), any(), any());

        // Verify a state change from secondaryCmm will get ignored and not change wifi state
        ConcreteClientModeManager secondaryCmm = mock(ConcreteClientModeManager.class);
        when(secondaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        mWifiConnectivityManager.handleConnectionStateChanged(
                secondaryCmm,
                WifiConnectivityManager.WIFI_STATE_TRANSITIONING);
        assertEquals(WifiConnectivityManager.WIFI_STATE_DISCONNECTED,
                mWifiConnectivityManager.getWifiState());

        // Verify state change from primary updates the state correctly
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);
        assertEquals(WifiConnectivityManager.WIFI_STATE_CONNECTED,
                mWifiConnectivityManager.getWifiState());
    }

    /** Don't crash if allocated a null ClientModeManager. */
    @Test
    public void requestSecondaryTransientCmm_gotNullCmm() {
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(null);
            }
        }).when(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(), eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS), any(), any());

        // primary CMM already connected
        WifiConfiguration config2 = new WifiConfiguration();
        config2.networkId = CANDIDATE_NETWORK_ID_2;
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(config2);

        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        verify(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(),
                eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS),
                eq(CANDIDATE_SSID),
                eq(CANDIDATE_BSSID));
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
    }

    /**
     * Don't attempt to connect again if the allocated ClientModeManager is already connected to
     * the desired network.
     */
    @Test
    public void requestSecondaryTransientCmm_gotAlreadyConnectedCmm() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        WifiConfiguration config = new WifiConfiguration();
        config.networkId = CANDIDATE_NETWORK_ID;
        ClientModeManager alreadyConnectedCmm = mock(ClientModeManager.class);
        when(alreadyConnectedCmm.getConnectingWifiConfiguration()).thenReturn(config);

        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(alreadyConnectedCmm);
            }
        }).when(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(), eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS), any(), any());

        // primary CMM already connected
        WifiConfiguration config2 = new WifiConfiguration();
        config2.networkId = CANDIDATE_NETWORK_ID_2;
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(config2);

        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        verify(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(),
                eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS),
                eq(CANDIDATE_SSID),
                eq(null));

        // already connected, don't connect again
        verify(alreadyConnectedCmm, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
    }

    /**
     * Verify MBB full flow.
     */
    @Test
    public void connectWhenConnected_UsingMbb() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        ClientModeManager mbbCmm = mock(ClientModeManager.class);
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(mbbCmm);
            }
        }).when(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(), eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS), any(), any());

        // primary CMM already connected
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(mCandidateWifiConfig2);

        // Set screen to on
        setScreenState(true);

        // Set WiFi to connected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);
        mLooper.dispatchAll();
        // Request secondary STA and connect using it.
        verify(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(),
                eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS),
                eq(CANDIDATE_SSID),
                eq(null));
        verify(mbbCmm).startConnectToNetwork(eq(CANDIDATE_NETWORK_ID), anyInt(), any());
    }

    /**
     * Fallback to single STA behavior when both networks have MAC randomization disabled.
     */
    @Test
    public void connectWhenConnected_UsingBbmIfBothNetworksHaveMacRandomizationDisabled() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        ClientModeManager mbbCmm = mock(ClientModeManager.class);
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(mbbCmm);
            }
        }).when(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(), eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS), any(), any());

        // Turn off MAC randomization on both networks.
        mCandidateWifiConfig1.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        mCandidateWifiConfig2.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;

        // primary CMM already connected
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(mCandidateWifiConfig2);

        // Set screen to on
        setScreenState(true);

        // Set WiFi to connected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);
        mLooper.dispatchAll();
        // Don't request secondary STA, fallback to primary STA.
        verify(mActiveModeWarden, never()).requestSecondaryTransientClientModeManager(
                any(), any(), any(), any());
        verify(mbbCmm, never()).startConnectToNetwork(anyInt(), anyInt(), any());
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                eq(CANDIDATE_NETWORK_ID), anyInt(), any());
        verify(mPrimaryClientModeManager).enableRoaming(true);
    }

    /**
     * Setup all the mocks for the positive case, individual negative test cases below override
     * specific params.
     */
    private void setupMocksForSecondaryLongLivedTests() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        when(mCandidate1.isOemPaid()).thenReturn(true);
        when(mCandidate1.isOemPrivate()).thenReturn(true);
        mCandidateWifiConfig1.oemPaid = true;
        mCandidateWifiConfig1.oemPrivate = true;
        when(mWifiNS.selectNetwork(argThat(
                candidates -> (candidates != null && candidates.size() == 1
                        && (candidates.get(0).isOemPaid() || candidates.get(0).isOemPrivate()))
        ))).thenReturn(mCandidateWifiConfig1);
        when(mActiveModeWarden.isStaStaConcurrencySupportedForRestrictedConnections())
                .thenReturn(true);
        when(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                any(), eq(ROLE_CLIENT_SECONDARY_LONG_LIVED), eq(false))).thenReturn(true);
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(mSecondaryClientModeManager);
            }
        }).when(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
        when(mSecondaryClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
    }

    @Test
    public void secondaryLongLived_noOemPaidOrOemPrivateConnectionAllowed() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid/OEM private connection disallowed.
        mWifiConnectivityManager.setOemPaidConnectionAllowed(false, null);
        mWifiConnectivityManager.setOemPrivateConnectionAllowed(false, null);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden, never()).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_oemPaidConnectionAllowedWithOemPrivateCandidate() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, new WorkSource());

        // Mark the candidate oem private only
        when(mCandidate1.isOemPaid()).thenReturn(false);
        when(mCandidate1.isOemPrivate()).thenReturn(true);
        mCandidateWifiConfig1.oemPaid = false;
        mCandidateWifiConfig1.oemPrivate = true;

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden, never()).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_oemPrivateConnectionAllowedWithOemPaidCandidate() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM private connection allowed.
        mWifiConnectivityManager.setOemPrivateConnectionAllowed(true, new WorkSource());

        // Mark the candidate oem paid only
        when(mCandidate1.isOemPaid()).thenReturn(true);
        when(mCandidate1.isOemPrivate()).thenReturn(false);
        mCandidateWifiConfig1.oemPaid = true;
        mCandidateWifiConfig1.oemPrivate = false;

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden, never()).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_noSecondaryStaSupport() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, new WorkSource());

        // STA + STA is not supported.
        when(mActiveModeWarden.isStaStaConcurrencySupportedForRestrictedConnections())
                .thenReturn(false);
        when(mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet())
                .thenReturn(false);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden, never()).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_noSecondaryCandidateSelected() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, new WorkSource());

        // Network selection does not select a secondary candidate.
        when(mWifiNS.selectNetwork(argThat(
                candidates -> (candidates != null && candidates.size() == 1
                        && (candidates.get(0).isOemPaid() || candidates.get(0).isOemPrivate()))
        ))).thenReturn(null) // first for secondary returns null.
                .thenReturn(mCandidateWifiConfig1); // second for primary returns something.

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden, never()).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_secondaryStaRequestReturnsNull() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, new WorkSource());

        // STA + STA is supported, but secondary STA request returns null
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(null);
            }
        }).when(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // No connection triggered (even on primary since wifi is off).
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_secondaryStaRequestReturnsPrimary() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, new WorkSource());

        // STA + STA is supported, but secondary STA request returns the primary
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(mPrimaryClientModeManager);
            }
        }).when(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // connection triggered on primary
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_secondaryStaRequestSucceedsWithOemPaidConnectionAllowed() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, new WorkSource());

        // Mark the candidate oem paid only
        when(mCandidate1.isOemPaid()).thenReturn(true);
        when(mCandidate1.isOemPrivate()).thenReturn(false);
        mCandidateWifiConfig1.oemPaid = true;
        mCandidateWifiConfig1.oemPrivate = false;

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // connection triggered on secondary
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mSecondaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());

        // Simulate connection failing on the secondary
        clearInvocations(mSecondaryClientModeManager, mPrimaryClientModeManager, mWifiNS);
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mSecondaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                config);
        // verify connection is never restarted when a connection on the secondary STA fails.
        verify(mWifiNS, never()).selectNetwork(any());
        verify(mSecondaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
    }

    @Test
    public void secondaryLongLived_secondaryStaRequestSucceedsWithOemPrivateConnectionAllowed() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPrivateConnectionAllowed(true, new WorkSource());

        // Mark the candidate oem private only
        when(mCandidate1.isOemPaid()).thenReturn(false);
        when(mCandidate1.isOemPrivate()).thenReturn(true);
        mCandidateWifiConfig1.oemPaid = false;
        mCandidateWifiConfig1.oemPrivate = true;

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // connection triggered on secondary
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mSecondaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_secondaryStaRequestSucceedsAlongWithPrimary() {
        setupMocksForSecondaryLongLivedTests();

        // 2 candidates - 1 oem paid, other regular.
        // Mark the first candidate oem private only
        when(mCandidate1.isOemPaid()).thenReturn(false);
        when(mCandidate1.isOemPrivate()).thenReturn(true);
        mCandidateWifiConfig1.oemPaid = false;
        mCandidateWifiConfig1.oemPrivate = true;

        // Add the second regular candidate.
        mCandidateList.add(mCandidate2);

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPrivateConnectionAllowed(true, new WorkSource());

        // Network selection setup for primary.
        when(mWifiNS.selectNetwork(argThat(
                candidates -> (candidates != null && candidates.size() == 1
                        // not oem paid or oem private.
                        && !(candidates.get(0).isOemPaid() || candidates.get(0).isOemPrivate()))
        ))).thenReturn(mCandidateWifiConfig2);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // connection triggered on primary & secondary
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID_2, Process.WIFI_UID, "any");
        verify(mSecondaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    /**
     * Verify that when the secondary is already connecting to the selected secondary network,
     * we only connect the primary STA.
     */
    @Test
    public void secondaryLongLived_secondaryStaRequestSucceedsWhenSecondaryAlreadyConnecting() {
        setupMocksForSecondaryLongLivedTests();

        // 2 candidates - 1 oem paid, other regular.
        // Mark the first candidate oem private only
        when(mCandidate1.isOemPaid()).thenReturn(false);
        when(mCandidate1.isOemPrivate()).thenReturn(true);
        mCandidateWifiConfig1.oemPaid = false;
        mCandidateWifiConfig1.oemPrivate = true;

        // mock secondary STA to already connecting to the target OEM private network
        when(mSecondaryClientModeManager.getConnectingWifiConfiguration()).thenReturn(
                mCandidateWifiConfig1);

        // Add the second regular candidate.
        mCandidateList.add(mCandidate2);

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPrivateConnectionAllowed(true, new WorkSource());

        // Network selection setup for primary.
        when(mWifiNS.selectNetwork(argThat(
                candidates -> (candidates != null && candidates.size() == 1
                        // not oem paid or oem private.
                        && !(candidates.get(0).isOemPaid() || candidates.get(0).isOemPrivate()))
        ))).thenReturn(mCandidateWifiConfig2);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // connection triggered on only on primary to CANDIDATE_NETWORK_ID_2.
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID_2, Process.WIFI_UID, "any");
        verify(mSecondaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), eq(CANDIDATE_SSID), any());
    }

    /**
     * Create scan data with different bands of 2G and 5G.
     */
    private ScanData createScanDataWithDifferentBands() {
        // Create 4 scan results.
        ScanData[] scanDatas =
                ScanTestUtil.createScanDatas(new int[][]{{5150, 5175, 2412, 2400}}, new int[]{0});
        // WCM barfs if the scan result does not have an IE.
        return scanDatas[0];
    }

    private WifiConfiguration getTestWifiConfig(int networkId, String ssid) {
        WifiConfiguration config = generateWifiConfig(
                networkId, 0, ssid, false, true, null, null,
                WifiConfigurationTestUtil.SECURITY_PSK);
        config.BSSID = ClientModeImpl.SUPPLICANT_BSSID_ANY;
        config.oemPaid = false;
        config.oemPrivate = false;
        config.ephemeral = true;
        when(mWifiConfigManager.getConfiguredNetwork(networkId)).thenReturn(config);
        return config;
    }

    private WifiCandidates.Candidate getTestWifiCandidate(int networkId, String ssid, String bssid,
            int rssi, int frequency) {
        WifiCandidates.Candidate candidate = mock(WifiCandidates.Candidate.class);
        when(candidate.isOemPaid()).thenReturn(false);
        when(candidate.isOemPrivate()).thenReturn(false);

        // Set up the scan candidates
        ScanResult result = new ScanResult.Builder(WifiSsid.fromString(ssid), bssid)
                .setHessid(1245)
                .setCaps("some caps")
                .setRssi(rssi)
                .setFrequency(frequency)
                .setTsf(1025)
                .setDistanceCm(22)
                .setDistanceSdCm(33)
                .setIs80211McRTTResponder(true)
                .build();
        ScanResultMatchInfo matchInfo = ScanResultMatchInfo.fromScanResult(result);
        WifiCandidates.Key key = new WifiCandidates.Key(matchInfo, MacAddress.fromString(bssid),
                networkId, WifiConfiguration.SECURITY_TYPE_PSK);
        when(candidate.getKey()).thenReturn(key);
        when(candidate.getScanRssi()).thenReturn(rssi);
        when(candidate.getFrequency()).thenReturn(frequency);
        return candidate;
    }

    /**
     * Set up the mocks for the multi internet use case unit tests.
     */
    private void setupMocksForMultiInternetTests(boolean isDbs) {
        mCandidateWifiConfig1 = getTestWifiConfig(CANDIDATE_NETWORK_ID, CANDIDATE_SSID);
        mCandidateWifiConfig2 = getTestWifiConfig(CANDIDATE_NETWORK_ID_2, CANDIDATE_SSID_2);

        mScanData = createScanDataWithDifferentBands();
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        when(mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet()).thenReturn(true);
        when(mActiveModeWarden.getPrimaryClientModeManagerNullable())
                .thenReturn(mPrimaryClientModeManager);
        when(mActiveModeWarden.isStaStaConcurrencySupportedForRestrictedConnections())
                .thenReturn(true);
        when(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                any(), eq(ROLE_CLIENT_SECONDARY_LONG_LIVED), eq(false))).thenReturn(true);
        mCandidate1 = getTestWifiCandidate(CANDIDATE_NETWORK_ID, CANDIDATE_SSID, CANDIDATE_BSSID,
                -40,
                TEST_FREQUENCY);
        mCandidate2 = getTestWifiCandidate(CANDIDATE_NETWORK_ID_2, CANDIDATE_SSID_2,
                CANDIDATE_BSSID_2, -60,
                TEST_FREQUENCY_2);
        mCandidate4 = getTestWifiCandidate(CANDIDATE_NETWORK_ID_2,
                CANDIDATE_SSID_2, CANDIDATE_BSSID_4,
                -40,
                TEST_FREQUENCY_3);

        // A DBS candidate with same SSID as mCandidate1
        mCandidate3 = getTestWifiCandidate(CANDIDATE_NETWORK_ID, CANDIDATE_SSID, CANDIDATE_BSSID_3,
                -40,
                TEST_FREQUENCY_3);
        mCandidateList = new ArrayList<WifiCandidates.Candidate>(
                Arrays.asList(mCandidate1, mCandidate2, mCandidate4));
        if (isDbs) {
            mCandidateList.add(mCandidate3);
        }
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(mCandidateList);

        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(mSecondaryClientModeManager);
            }
        }).when(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
        when(mSecondaryClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
    }

    /**
     * Set up the primary network selection mocks for the multi internet use case unit tests.
     */
    private void setupMockPrimaryNetworkSelect(int networkId, String bssid, int rssi,
            int frequency) {
        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(networkId);
        config.getNetworkSelectionStatus().setCandidate(new ScanResult.Builder(
                WifiSsid.fromUtf8Text(config.SSID), bssid)
                .setHessid(1245)
                .setCaps("some caps")
                .setRssi(rssi)
                .setFrequency(frequency)
                .setTsf(1025)
                .setDistanceCm(22)
                .setDistanceSdCm(33)
                .setIs80211McRTTResponder(true)
                .build());
        // Selection for primary
        when(mWifiNS.selectNetwork(any()))
                .then(new AnswerWithArguments() {
                    public WifiConfiguration answer(List<WifiCandidates.Candidate> candidateList) {
                        if (candidateList != null) {
                            for (WifiCandidates.Candidate candidate : candidateList) {
                                if (networkId == candidate.getKey().networkId) {
                                    return config;
                                }
                            }
                        }
                        return null;
                    }
                });
    }

    /**
     * Set up the secondary network selection mocks for the multi internet use case unit tests.
     */
    private void setupMockSecondaryNetworkSelect(int networkId, int rssi) {
        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(networkId);
        // Selection for secondary
        when(mWifiNS.selectNetwork(any(), anyBoolean()))
                .then(new AnswerWithArguments() {
                    public WifiConfiguration answer(List<WifiCandidates.Candidate> candidateList,
                            boolean override) {
                        if (candidateList != null) {
                            for (WifiCandidates.Candidate candidate : candidateList) {
                                // Will return the first candidate matching networkId
                                if (networkId == candidate.getKey().networkId) {
                                    config.getNetworkSelectionStatus().setCandidate(
                                            new ScanResult.Builder(
                                                    WifiSsid.fromUtf8Text(config.SSID),
                                                    candidate.getKey().bssid.toString())
                                                    .setHessid(1245)
                                                    .setCaps("some caps")
                                                    .setRssi(rssi)
                                                    .setFrequency(candidate.getFrequency())
                                                    .setTsf(1025)
                                                    .setDistanceCm(22)
                                                    .setDistanceSdCm(33)
                                                    .setIs80211McRTTResponder(true)
                                                    .build());
                                    return config;
                                }
                            }
                        }
                        return null;
                    }
                });
    }

    /**
     * Set up the client manager wifi info mocks for the multi internet use case unit tests.
     */
    private void mockClientManagerInfo(ConcreteClientModeManager clientManager,
            WifiConfiguration configuration) {
        WifiInfo wifiInfo = getWifiInfo();
        wifiInfo.setNetworkId(configuration.networkId);
        wifiInfo.setSSID(WifiSsid.fromString(configuration.SSID));
        wifiInfo.setBSSID(configuration.BSSID);
        wifiInfo.setFrequency(
                configuration.getNetworkSelectionStatus().getCandidate().frequency);
        wifiInfo.setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_PSK);
        when(clientManager.isConnected()).thenReturn(true);
        when(clientManager.isDisconnected()).thenReturn(false);
        when(clientManager.getConnectionInfo()).thenReturn(wifiInfo);
    }

    private void testMultiInternetSecondaryConnectionRequest(boolean isDbsOnly, boolean isDhcp,
            boolean success, String expectedBssidToConnect) {
        when(mMultiInternetManager.isStaConcurrencyForMultiInternetMultiApAllowed())
                .thenReturn(!isDbsOnly);
        setupMockPrimaryNetworkSelect(CANDIDATE_NETWORK_ID, CANDIDATE_BSSID, -50, TEST_FREQUENCY);
        WifiConfiguration targetConfig = isDbsOnly ? mCandidateWifiConfig1 : mCandidateWifiConfig2;
        String targetBssid = expectedBssidToConnect;
        targetConfig.setIpAssignment(
                isDhcp ? IpConfiguration.IpAssignment.DHCP : IpConfiguration.IpAssignment.STATIC);
        setupMockSecondaryNetworkSelect(targetConfig.networkId, -50);
        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(mCandidateList.size())));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        // mCandidateWifiConfig1 is connected as primary
        mockClientManagerInfo(mPrimaryClientModeManager, mCandidateWifiConfig1);

        when(mMultiInternetManager.hasPendingConnectionRequests()).thenReturn(true);
        // Set the connection pending status
        mMultiInternetConnectionStatusListenerCaptor.getValue().onStatusChange(
                MultiInternetManager.MULTI_INTERNET_STATE_CONNECTION_REQUESTED,
                new WorkSource());
        mMultiInternetConnectionStatusListenerCaptor.getValue().onStartScan(new WorkSource());
        mLooper.dispatchAll();
        if (!success) {
            verify(mSecondaryClientModeManager, never()).startConnectToNetwork(
                    targetConfig.networkId, Process.WIFI_UID, targetBssid);
            verify(mSecondaryClientModeManager, never()).enableRoaming(false);
            verify(mActiveModeWarden, never()).requestSecondaryLongLivedClientModeManager(
                    any(), any(), any(), any());
            return;
        }

        verify(mSecondaryClientModeManager, times(2)).startConnectToNetwork(
                targetConfig.networkId, Process.WIFI_UID, targetBssid);
        verify(mSecondaryClientModeManager, times(2)).enableRoaming(false);
        verify(mActiveModeWarden, times(2)).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());

        // Simulate connection failing on the secondary
        clearInvocations(mSecondaryClientModeManager, mPrimaryClientModeManager, mWifiNS);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mSecondaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID));
        // verify connection is never restarted when a connection on the secondary STA fails.
        verify(mWifiNS, never()).selectNetwork(any());
        verify(mSecondaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
    }

    @Test
    public void multiInternetSecondaryConnectionRequestSucceedsWithDbsApOnly() {
        setupMocksForMultiInternetTests(true);
        testMultiInternetSecondaryConnectionRequest(true, true, true, CANDIDATE_BSSID_3);
    }

    @Test
    public void multiInternetSecondaryConnectionRequestSucceedsWithDbsApOnlyFailOnStaticIp() {
        setupMocksForMultiInternetTests(true);
        // mock network selection not needed for primary
        when(mWifiNS.isNetworkSelectionNeededForCmm(any())).thenReturn(false);
        testMultiInternetSecondaryConnectionRequest(true, false, false, CANDIDATE_BSSID_3);

        // network selection should be skipped on the primary
        verify(mWifiNS).selectNetwork(any());
    }

    /**
     * Verify that when no valid secondary internet candidate is found network selection fallback
     * onto the primary when needed.
     */
    @Test
    public void multiInternetSecondaryConnectionRequestFallbackOnPrimary() {
        setupMocksForMultiInternetTests(true);
        // mock network selection not needed for primary
        when(mWifiNS.isNetworkSelectionNeededForCmm(any())).thenReturn(true);
        testMultiInternetSecondaryConnectionRequest(true, false, false, CANDIDATE_BSSID_3);

        // network selection happens on the primary
        verify(mWifiNS, atLeastOnce()).selectNetwork(any());
    }

    @Test
    public void multiInternetSecondaryConnectionRequestSucceedsWithMultiApAllowed() {
        setupMocksForMultiInternetTests(false);
        testMultiInternetSecondaryConnectionRequest(false, true, true, CANDIDATE_BSSID_2);
    }

    @Test
    public void multiInternetSecondaryConnectionRequestSucceedsWithMultiApAllowedAndPrimaryMlo() {
        setupMocksForMultiInternetTests(false);
        // Add a new candidate (CANDIDATE_BSSID_5) in same band as primary candidate
        // (CANDIDATE_BSSID). Add at the index 0, as setupMockSecondaryNetworkSelect() returns the
        // first network match.
        mCandidateList.add(0,
                getTestWifiCandidate(CANDIDATE_NETWORK_ID_2, CANDIDATE_SSID_2, CANDIDATE_BSSID_5,
                        -40, TEST_FREQUENCY));
        // Enable Multi-Link operation (MLO) for primary.
        when(mPrimaryClientModeManager.isMlo()).thenReturn(true);
        when(mPrimaryClientModeManager.isAffiliatedLinkBssid(
                MacAddress.fromString(CANDIDATE_BSSID))).thenReturn(true);
        // Test secondary STA selects candidate in the same band.
        testMultiInternetSecondaryConnectionRequest(false, true, true, CANDIDATE_BSSID_5);
    }

    @Test
    public void multiInternetSecondaryConnectionDisconnectedBeforeNetworkSelection() {
        setupMocksForMultiInternetTests(false);
        testMultiInternetSecondaryConnectionRequest(false, true, true, CANDIDATE_BSSID_2);
        setupMockPrimaryNetworkSelect(CANDIDATE_NETWORK_ID_2, CANDIDATE_BSSID_2, -50,
                TEST_FREQUENCY);
        when(mMultiInternetManager.hasPendingConnectionRequests()).thenReturn(false);
        when(mSecondaryClientModeManager.isConnected()).thenReturn(true);
        when(mSecondaryClientModeManager.getConnectedWifiConfiguration()).thenReturn(
                mCandidateWifiConfig2);
        when(mActiveModeWarden.getClientModeManagerInRole(
                ROLE_CLIENT_SECONDARY_LONG_LIVED)).thenReturn(mSecondaryClientModeManager);
        mMultiInternetConnectionStatusListenerCaptor.getValue().onStartScan(new WorkSource());
        verify(mSecondaryClientModeManager).disconnect();
    }

    @Test
    public void multiInternetSecondaryConnectionRequest_filteredBssidExists() {
        setupMocksForMultiInternetTests(false);
        Map<Integer, String> specifiedBssids = new ArrayMap<>();
        // Verify connect to the filtered BSSID
        specifiedBssids.put(ScanResult.toBand(mCandidate4.getFrequency()),
                mCandidate4.getKey().bssid.toString());
        when(mMultiInternetManager.getSpecifiedBssids()).thenReturn(specifiedBssids);
        testMultiInternetSecondaryConnectionRequest(false, true, true,
                mCandidate4.getKey().bssid.toString());
    }

    @Test
    public void multiInternetSecondaryConnectionRequest_filterBssidNotExist() {
        setupMocksForMultiInternetTests(false);
        Map<Integer, String> specifiedBssids = new ArrayMap<>();
        // Verify filtering by a BSSID that does not exist in the candidate list will not result
        // in a connection.
        specifiedBssids.put(ScanResult.toBand(mCandidate2.getFrequency()),
                mCandidate3.getKey().bssid.toString());
        specifiedBssids.put(ScanResult.toBand(mCandidate4.getFrequency()),
                mCandidate3.getKey().bssid.toString());
        when(mMultiInternetManager.getSpecifiedBssids()).thenReturn(specifiedBssids);
        testMultiInternetSecondaryConnectionRequest(false, true, false,
                mCandidate2.getKey().bssid.toString());
    }

    @Test
    public void multiInternetSecondaryConnectionRequestFailsIfWouldDeletePrivilegedIface() {
        setupMocksForMultiInternetTests(false);
        when(mHalDeviceManager.creatingIfaceWillDeletePrivilegedIface(anyInt(), any()))
                .thenReturn(true);
        testMultiInternetSecondaryConnectionRequest(true, true, false, CANDIDATE_BSSID_3);
    }

    /**
     *  Wifi enters disconnected state while screen is on.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the
     * expected candidate network ID and BSSID.
     */
    @Test
    public void enterWifiDisconnectedStateWhenScreenOn() {
        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     *  Wifi enters connected state while screen is on.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the
     * expected candidate network ID and BSSID.
     */
    @Test
    public void enterWifiConnectedStateWhenScreenOn() {
        // Set screen to on
        setScreenState(true);

        // Set WiFi to connected state
        setWifiStateConnected();
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     *  Screen turned on while WiFi in disconnected state.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the
     * expected candidate network ID and BSSID.
     */
    @Test
    public void turnScreenOnWhenWifiInDisconnectedState() {
        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Set screen to on
        setScreenState(true);

        verify(mPrimaryClientModeManager, atLeastOnce()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     *  Screen turned on while WiFi in connected state.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the
     * expected candidate network ID and BSSID.
     */
    @Test
    public void turnScreenOnWhenWifiInConnectedState() {
        // Set WiFi to connected state
        setWifiStateConnected();

        // Set screen to on
        setScreenState(true);
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager, atLeastOnce()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     *  Screen turned on while WiFi in connected state but
     *  auto roaming is disabled.
     *
     * Expected behavior: WifiConnectivityManager doesn't invoke
     * ClientModeManager.startConnectToNetwork() because roaming
     * is turned off.
     */
    @Test
    public void turnScreenOnWhenWifiInConnectedStateRoamingDisabled() {
        // Turn off auto roaming
        mResources.setBoolean(
                R.bool.config_wifi_framework_enable_associated_network_selection, false);
        mWifiConnectivityManager = createConnectivityManager();
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        // Set WiFi to connected state
        setWifiStateConnected();

        // Set screen to on
        setScreenState(true);

        verify(mPrimaryClientModeManager, times(0)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Multiple back to back connection attempts within the rate interval should be rate limited.
     *
     * Expected behavior: WifiConnectivityManager calls ClientModeManager.startConnectToNetwork()
     * with the expected candidate network ID and BSSID for only the expected number of times within
     * the given interval.
     */
    @Test
    public void connectionAttemptRateLimitedWhenScreenOff() {
        int maxAttemptRate = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_RATE;
        int timeInterval = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_TIME_INTERVAL_MS;
        int numAttempts = 0;
        int connectionAttemptIntervals = timeInterval / maxAttemptRate;

        setScreenState(false);

        // First attempt the max rate number of connections within the rate interval.
        long currentTimeStamp = 0;
        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            mLooper.dispatchAll();
            numAttempts++;
        }
        // Now trigger another connection attempt before the rate interval, this should be
        // skipped because we've crossed rate limit.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify that we attempt to connect upto the rate.
        verify(mPrimaryClientModeManager, times(numAttempts)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Multiple back to back connection attempts outside the rate interval should not be rate
     * limited.
     *
     * Expected behavior: WifiConnectivityManager calls ClientModeManager.startConnectToNetwork()
     * with the expected candidate network ID and BSSID for only the expected number of times within
     * the given interval.
     */
    @Test
    public void connectionAttemptNotRateLimitedWhenScreenOff() {
        int maxAttemptRate = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_RATE;
        int timeInterval = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_TIME_INTERVAL_MS;
        int numAttempts = 0;
        int connectionAttemptIntervals = timeInterval / maxAttemptRate;

        setScreenState(false);

        // First attempt the max rate number of connections within the rate interval.
        long currentTimeStamp = 0;
        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            mLooper.dispatchAll();
            numAttempts++;
        }
        // Now trigger another connection attempt after the rate interval, this should not be
        // skipped because we should've evicted the older attempt.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(
                currentTimeStamp + connectionAttemptIntervals * 2);
        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        numAttempts++;

        // Verify that all the connection attempts went through
        verify(mPrimaryClientModeManager, times(numAttempts)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Multiple back to back connection attempts after a force connectivity scan should not be rate
     * limited.
     *
     * Expected behavior: WifiConnectivityManager calls ClientModeManager.startConnectToNetwork()
     * with the expected candidate network ID and BSSID for only the expected number of times within
     * the given interval.
     */
    @Test
    public void connectionAttemptNotRateLimitedWhenScreenOffForceConnectivityScan() {
        int maxAttemptRate = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_RATE;
        int timeInterval = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_TIME_INTERVAL_MS;
        int numAttempts = 0;
        int connectionAttemptIntervals = timeInterval / maxAttemptRate;

        setScreenState(false);

        // First attempt the max rate number of connections within the rate interval.
        long currentTimeStamp = 0;
        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            mLooper.dispatchAll();
            numAttempts++;
        }

        mWifiConnectivityManager.forceConnectivityScan(new WorkSource());

        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            mLooper.dispatchAll();
            numAttempts++;
        }

        // Verify that all the connection attempts went through
        verify(mPrimaryClientModeManager, times(numAttempts)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Multiple back to back connection attempts after a user selection should not be rate limited.
     *
     * Expected behavior: WifiConnectivityManager calls ClientModeManager.startConnectToNetwork()
     * with the expected candidate network ID and BSSID for only the expected number of times within
     * the given interval.
     */
    @Test
    public void connectionAttemptNotRateLimitedWhenScreenOffAfterUserSelection() {
        int maxAttemptRate = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_RATE;
        int timeInterval = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_TIME_INTERVAL_MS;
        int numAttempts = 0;
        int connectionAttemptIntervals = timeInterval / maxAttemptRate;

        setScreenState(false);

        // First attempt the max rate number of connections within the rate interval.
        long currentTimeStamp = 0;
        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            mLooper.dispatchAll();
            numAttempts++;
        }

        mWifiConnectivityManager.prepareForForcedConnection(CANDIDATE_NETWORK_ID);

        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            mLooper.dispatchAll();
            numAttempts++;
        }

        // Verify that all the connection attempts went through
        verify(mPrimaryClientModeManager, times(numAttempts)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Multiple back to back connection attempts after a wifi toggle should not be rate limited.
     *
     * Expected behavior: WifiConnectivityManager calls ClientModeManager.startConnectToNetwork()
     * with the expected candidate network ID and BSSID for only the expected number of times within
     * the given interval.
     */
    @Test
    public void connectionAttemptNotRateLimitedWhenScreenOffAfterWifiToggle() {
        int maxAttemptRate = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_RATE;
        int timeInterval = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_TIME_INTERVAL_MS;
        int numAttempts = 0;
        int connectionAttemptIntervals = timeInterval / maxAttemptRate;

        setScreenState(false);

        // First attempt the max rate number of connections within the rate interval.
        long currentTimeStamp = 0;
        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            mLooper.dispatchAll();
            numAttempts++;
        }

        setWifiEnabled(false);
        setWifiEnabled(true);

        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            mLooper.dispatchAll();
            numAttempts++;
        }

        // Verify that all the connection attempts went through
        verify(mPrimaryClientModeManager, times(numAttempts)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     *  PNO retry for low RSSI networks.
     *
     * Expected behavior: WifiConnectivityManager doubles the low RSSI
     * network retry delay value after QNS skips the PNO scan results
     * because of their low RSSI values and reaches max after three scans
     */
    @Test
    public void pnoRetryForLowRssiNetwork() {
        when(mWifiNS.selectNetwork(any())).thenReturn(null);

        // Set screen to off
        setScreenState(false);

        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        verify(mWifiMetrics).noteFirstNetworkSelectionAfterBoot(false);

        // Get the retry delay value after QNS didn't select a
        // network candidate from the PNO scan results.
        int lowRssiNetworkRetryDelayAfterOnePnoMs = mWifiConnectivityManager
                .getLowRssiNetworkRetryDelay();

        assertEquals(LOW_RSSI_NETWORK_RETRY_START_DELAY_SEC * 2000,
                lowRssiNetworkRetryDelayAfterOnePnoMs);

        // Set WiFi to disconnected state to trigger two more PNO scans
        for (int i = 0; i < 2; i++) {
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            mLooper.dispatchAll();
        }
        int lowRssiNetworkRetryDelayAfterThreePnoMs = mWifiConnectivityManager
                .getLowRssiNetworkRetryDelay();
        assertEquals(LOW_RSSI_NETWORK_RETRY_MAX_DELAY_SEC * 1000,
                lowRssiNetworkRetryDelayAfterThreePnoMs);
    }

    /**
     * Ensure that the watchdog bite increments the "Pno bad" metric.
     *
     * Expected behavior: WifiConnectivityManager detects that the PNO scan failed to find
     * a candidate while watchdog single scan did.
     */
    @Test
    public void watchdogBitePnoBadIncrementsMetrics() {
        assumeFalse(SdkLevel.isAtLeastU());
        // Set screen to off
        setScreenState(false);

        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Now fire the watchdog alarm and verify the metrics were incremented.
        mAlarmManager.dispatch(WifiConnectivityManager.WATCHDOG_TIMER_TAG);
        mLooper.dispatchAll();

        verify(mWifiMetrics).incrementNumConnectivityWatchdogPnoBad();
        verify(mWifiMetrics, never()).incrementNumConnectivityWatchdogPnoGood();
    }

    /**
     * Ensure that the watchdog bite increments the "Pno good" metric.
     *
     * Expected behavior: WifiConnectivityManager detects that the PNO scan failed to find
     * a candidate which was the same with watchdog single scan.
     */
    @Test
    public void watchdogBitePnoGoodIncrementsMetrics() {
        assumeFalse(SdkLevel.isAtLeastU());

        // Qns returns no candidate after watchdog single scan.
        when(mWifiNS.selectNetwork(any())).thenReturn(null);

        // Set screen to off
        setScreenState(false);

        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Now fire the watchdog alarm and verify the metrics were incremented.
        mAlarmManager.dispatch(WifiConnectivityManager.WATCHDOG_TIMER_TAG);
        mLooper.dispatchAll();

        verify(mWifiMetrics).incrementNumConnectivityWatchdogPnoGood();
        verify(mWifiMetrics, never()).incrementNumConnectivityWatchdogPnoBad();
    }

    @Test
    public void testNetworkConnectionCancelWatchdogTimer() {
        assumeFalse(SdkLevel.isAtLeastU());

        // Set screen to off
        setScreenState(false);

        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify the watchdog alarm has been set
        assertTrue(mAlarmManager.isPending(WifiConnectivityManager.WATCHDOG_TIMER_TAG));

        // Set WiFi to connected
        setWifiStateConnected();

        // Verify the watchdog alarm has been canceled
        assertFalse(mAlarmManager.isPending(WifiConnectivityManager.WATCHDOG_TIMER_TAG));
    }

    /**
     * Verify that the PNO Watchdog timer is not started in U+
     */
    @Test
    public void testWatchdogTimerNotStartedInUPlus() {
        assumeTrue(SdkLevel.isAtLeastU());

        // Set screen to off
        setScreenState(false);

        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify the watchdog alarm has been set
        assertFalse(mAlarmManager.isPending(WifiConnectivityManager.WATCHDOG_TIMER_TAG));
    }

    /**
     * Verify whether the PNO Watchdog timer can wake the system up according to the config flag
     */
    @Test
    public void testWatchdogTimerCanWakeUp() {
        assumeFalse(SdkLevel.isAtLeastU());
        mResources.setBoolean(R.bool.config_wifiPnoWatchdogCanWakeUp,
                true);

        // Set screen to off
        setScreenState(false);

        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify the watchdog alarm has been set
        verify(mAlarmManager.getAlarmManager()).set(eq(AlarmManager.ELAPSED_REALTIME_WAKEUP),
                anyLong(), anyString(), any(), any());

        mResources.setBoolean(R.bool.config_wifiPnoWatchdogCanWakeUp,
                false);

        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify the watchdog alarm has been set
        verify(mAlarmManager.getAlarmManager()).set(eq(AlarmManager.ELAPSED_REALTIME),
                anyLong(), anyString(), any(), any());
    }

    /**
     * Verify that 2 scans that are sufficiently far apart are required to initiate a connection
     * when the high mobility scanning optimization is enabled.
     */
    @Test
    public void testHighMovementNetworkSelection() {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        // Enable high movement optimization
        mResources.setBoolean(R.bool.config_wifiHighMovementNetworkSelectionOptimizationEnabled,
                true);
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();

        // Verify there is no connection due to currently having no cached candidates.
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Move time forward but do not cross HIGH_MVMT_SCAN_DELAY_MS yet.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(HIGH_MVMT_SCAN_DELAY_MS - 1L);
        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();

        // Verify we still don't connect because not enough time have passed since the candidates
        // were cached.
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Move time past HIGH_MVMT_SCAN_DELAY_MS.
        when(mClock.getElapsedSinceBootMillis()).thenReturn((long) HIGH_MVMT_SCAN_DELAY_MS);
        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();

        // Verify a candidate if found this time.
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
        verify(mWifiMetrics, times(2)).incrementNumHighMovementConnectionSkipped();
        verify(mWifiMetrics).incrementNumHighMovementConnectionStarted();
    }

    /**
     * Verify that the device is initiating partial scans to verify AP stability in the high
     * movement mobility state.
     */
    @Test
    public void testHighMovementTriggerPartialScan() {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        // Enable high movement optimization
        mResources.setBoolean(R.bool.config_wifiHighMovementNetworkSelectionOptimizationEnabled,
                true);
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify there is no connection due to currently having no cached candidates.
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Move time forward and verify that a delayed partial scan is scheduled.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(HIGH_MVMT_SCAN_DELAY_MS + 1L);
        mAlarmManager.dispatch(WifiConnectivityManager.DELAYED_PARTIAL_SCAN_TIMER_TAG);
        mLooper.dispatchAll();

        verify(mWifiScanner).startScan((ScanSettings) argThat(new WifiPartialScanSettingMatcher()),
                any());
    }

    private class WifiPartialScanSettingMatcher implements ArgumentMatcher<ScanSettings> {
        @Override
        public boolean matches(ScanSettings scanSettings) {
            return scanSettings.band == WifiScanner.WIFI_BAND_UNSPECIFIED
                    && scanSettings.channels[0].frequency == TEST_FREQUENCY;
        }
    }

    /**
     * Verify that when there are we obtain more than one valid candidates from scan results and
     * network connection fails, connection is immediately retried on the remaining candidates.
     */
    @Test
    public void testRetryConnectionOnFailure() {
        // Setup WifiNetworkSelector to return 2 valid candidates from scan results
        MacAddress macAddress = MacAddress.fromString(CANDIDATE_BSSID_2);
        WifiCandidates.Key key = new WifiCandidates.Key(mock(ScanResultMatchInfo.class),
                macAddress, 0, WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiCandidates.Candidate otherCandidate = mock(WifiCandidates.Candidate.class);
        when(otherCandidate.getKey()).thenReturn(key);
        List<WifiCandidates.Candidate> candidateList = new ArrayList<>();
        candidateList.add(mCandidate1);
        candidateList.add(otherCandidate);
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(candidateList);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(2)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        // Simulate the connection failing
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                config);
        // Verify the failed BSSID is added to blocklist
        verify(mWifiBlocklistMonitor).blockBssidForDurationMs(eq(CANDIDATE_BSSID),
                eq(config), anyLong(), anyInt(), anyInt());
        // Verify another connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(1)));
        verify(mPrimaryClientModeManager, times(2)).startConnectToNetwork(
                anyInt(), anyInt(), any());

        // Simulate the second connection also failing
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID_2,
                config);
        // Verify there are no more connections
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(0)));
        verify(mPrimaryClientModeManager, times(2)).startConnectToNetwork(
                anyInt(), anyInt(), any());
    }

    @Test
    public void testRetryConnectionEapFailureIgnoreSameNetwork() {
        // Setup WifiNetworkSelector to return 2 valid candidates with the same
        // ScanResultMatchInfo so they are the same network, but different BSSID.
        ScanResultMatchInfo matchInfo = ScanResultMatchInfo.fromWifiConfiguration(
                mCandidateWifiConfig1);
        WifiCandidates.Key key = new WifiCandidates.Key(matchInfo,
                MacAddress.fromString(CANDIDATE_BSSID), 0);
        WifiCandidates.Key key2 = new WifiCandidates.Key(matchInfo,
                MacAddress.fromString(CANDIDATE_BSSID_2), 0);
        WifiCandidates.Candidate candidate1 = mock(WifiCandidates.Candidate.class);
        when(candidate1.getKey()).thenReturn(key);
        WifiCandidates.Candidate candidate2 = mock(WifiCandidates.Candidate.class);
        when(candidate2.getKey()).thenReturn(key2);
        List<WifiCandidates.Candidate> candidateList = new ArrayList<>();
        candidateList.add(candidate1);
        candidateList.add(candidate2);
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(candidateList);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(2)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        // Simulate the connection failing
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_EAP_FAILURE, CANDIDATE_BSSID,
                mCandidateWifiConfig1);
        mLooper.dispatchAll();
        // verify no there is no retry.
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());
    }

    @Test
    public void testRetryConnectionIgnoreNetworkWithAutojoinDisabled() {
        // Setup WifiNetworkSelector to return 2 valid candidates from scan results
        MacAddress macAddress = MacAddress.fromString(CANDIDATE_BSSID_2);
        WifiCandidates.Key key = new WifiCandidates.Key(mock(ScanResultMatchInfo.class),
                macAddress, 0, WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiCandidates.Candidate otherCandidate = mock(WifiCandidates.Candidate.class);
        when(otherCandidate.getKey()).thenReturn(key);
        List<WifiCandidates.Candidate> candidateList = new ArrayList<>();
        candidateList.add(mCandidate1);
        candidateList.add(otherCandidate);
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(candidateList);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(2)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        // mock the WifiConfiguration to have allowAutoJoin = false
        mCandidateWifiConfig1.allowAutojoin = false;

        // Simulate the connection failing
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                config);

        // Verify another connection do not start.
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());
    }

    /**
     * Verify that we do not try to reconnect to an admin restricted network
     */
    @Test
    public void testRetryConnectionIgnoreNetworkWithAdminRestriction() {
        // Setup WifiNetworkSelector to return 2 valid candidates from scan results
        MacAddress macAddress = MacAddress.fromString(CANDIDATE_BSSID_2);
        WifiCandidates.Key key = new WifiCandidates.Key(mock(ScanResultMatchInfo.class),
                macAddress, 0, WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiCandidates.Candidate otherCandidate = mock(WifiCandidates.Candidate.class);
        when(otherCandidate.getKey()).thenReturn(key);
        List<WifiCandidates.Candidate> candidateList = new ArrayList<>();
        candidateList.add(mCandidate1);
        candidateList.add(otherCandidate);
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(candidateList);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(2)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        // mock the WifiConfiguration to have an admin restriction
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        when(mWifiPermissionsUtil.isAdminRestrictedNetwork(config)).thenReturn(true);

        // Simulate the connection failing
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                config);

        // Verify another connection do not start.
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());
    }

    private class WifiCandidatesListSizeMatcher implements
            ArgumentMatcher<List<WifiCandidates.Candidate>> {
        int mSize;
        WifiCandidatesListSizeMatcher(int size) {
            mSize = size;
        }
        @Override
        public boolean matches(List<WifiCandidates.Candidate> candidateList) {
            return candidateList.size() == mSize;
        }
    }

    /**
     * Verify that the cached candidates become cleared after a period of time.
     */
    @Test
    public void testRetryConnectionOnFailureCacheTimeout() {
        // Setup WifiNetworkSelector to return 2 valid candidates from scan results
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        MacAddress macAddress = MacAddress.fromString(CANDIDATE_BSSID_2);
        WifiCandidates.Key key = new WifiCandidates.Key(mock(ScanResultMatchInfo.class),
                macAddress, 0, WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiCandidates.Candidate otherCandidate = mock(WifiCandidates.Candidate.class);
        when(otherCandidate.getKey()).thenReturn(key);
        List<WifiCandidates.Candidate> candidateList = new ArrayList<>();
        candidateList.add(mCandidate1);
        candidateList.add(otherCandidate);
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(candidateList);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(2)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        // Simulate the connection failing after the cache timeout period.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(TEMP_BSSID_BLOCK_DURATION_MS + 1L);
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                config);
        // verify there are no additional connections.
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());
    }

    /**
     * Verify that when cached candidates get cleared there will no longer be retries after a
     * connection failure.
     */
    @Test
    public void testNoRetryConnectionOnUserDisconnectedNetwork() {
        // Setup WifiNetworkSelector to return 2 valid candidates from scan results
        MacAddress macAddress = MacAddress.fromString(CANDIDATE_BSSID_2);
        WifiCandidates.Key key = new WifiCandidates.Key(mock(ScanResultMatchInfo.class),
                macAddress, 0, WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiCandidates.Candidate otherCandidate = mock(WifiCandidates.Candidate.class);
        when(otherCandidate.getKey()).thenReturn(key);
        List<WifiCandidates.Candidate> candidateList = new ArrayList<>();
        candidateList.add(mCandidate1);
        candidateList.add(otherCandidate);
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(candidateList);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(2)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        // now mark the network as disabled by the user the cached candidates
        when(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(CANDIDATE_SSID))
                .thenReturn(true);

        // Simulate the connection failing
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                config);

        // Verify there no re-attempt to connect
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());
    }

    /**
     * Verify that when cached candidates get cleared there will no longer be retries after a
     * connection failure.
     */
    @Test
    public void testNoRetryConnectionOnFailureAfterCacheCleared() {
        // Setup WifiNetworkSelector to return 2 valid candidates from scan results
        MacAddress macAddress = MacAddress.fromString(CANDIDATE_BSSID_2);
        WifiCandidates.Key key = new WifiCandidates.Key(mock(ScanResultMatchInfo.class),
                macAddress, 0, WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiCandidates.Candidate otherCandidate = mock(WifiCandidates.Candidate.class);
        when(otherCandidate.getKey()).thenReturn(key);
        List<WifiCandidates.Candidate> candidateList = new ArrayList<>();
        candidateList.add(mCandidate1);
        candidateList.add(otherCandidate);
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(candidateList);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(2)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        // now clear the cached candidates
        mWifiConnectivityManager.clearCachedCandidates();

        // Simulate the connection failing
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                config);

        // Verify there no re-attempt to connect
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());
    }

    /**
     * Verify that the cached candidates that become disabled are not selected for connection.
     */
    @Test
    public void testRetryConnectionIgnoresDisabledNetworks() {
        // Setup WifiNetworkSelector to return 2 valid candidates from scan results
        int testOtherNetworkNetworkId = 123;
        MacAddress macAddress = MacAddress.fromString(CANDIDATE_BSSID_2);
        WifiCandidates.Key key = new WifiCandidates.Key(mock(ScanResultMatchInfo.class),
                macAddress, testOtherNetworkNetworkId, WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiCandidates.Candidate otherCandidate = mock(WifiCandidates.Candidate.class);
        when(otherCandidate.getKey()).thenReturn(key);
        List<WifiCandidates.Candidate> candidateList = new ArrayList<>();
        candidateList.add(mCandidate1);
        candidateList.add(otherCandidate);
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(candidateList);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(2)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        // make sure the configuration for otherCandidate is disabled, and verify there is no
        // connection attempt after the disconnect happens.
        when(otherCandidate.getNetworkConfigId()).thenReturn(testOtherNetworkNetworkId);
        WifiConfiguration candidateOtherConfig = WifiConfigurationTestUtil.createOpenNetwork();
        candidateOtherConfig.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED);
        when(mWifiConfigManager.getConfiguredNetwork(testOtherNetworkNetworkId))
                .thenReturn(candidateOtherConfig);

        // Simulate the connection failing
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                config);

        // Verify no more connections since there are 0 valid candidates remaining.
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(0)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());
    }

    /**
     * Verify that in the high movement mobility state, when the RSSI delta of a BSSID from
     * 2 consecutive scans becomes greater than a threshold, the candidate get ignored from
     * network selection.
     */
    @Test
    public void testHighMovementRssiFilter() {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        // Enable high movement optimization
        mResources.setBoolean(R.bool.config_wifiHighMovementNetworkSelectionOptimizationEnabled,
                true);
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();

        // Verify there is no connection due to currently having no cached candidates.
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Move time past HIGH_MVMT_SCAN_DELAY_MS.
        when(mClock.getElapsedSinceBootMillis()).thenReturn((long) HIGH_MVMT_SCAN_DELAY_MS);

        // Mock the current Candidate to have RSSI over the filter threshold
        mCandidateList.clear();
        mCandidateList.add(mCandidate2);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();

        // Verify connect is not started.
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
        verify(mWifiMetrics, times(2)).incrementNumHighMovementConnectionSkipped();
    }

    /**
     * {@link OpenNetworkNotifier} handles scan results on network selection.
     *
     * Expected behavior: ONA handles scan results
     */
    @Test
    public void wifiDisconnected_noCandidateInSelect_openNetworkNotifierScanResultsHandled() {
        // no connection candidate selected
        when(mWifiNS.selectNetwork(any())).thenReturn(null);

        List<ScanDetail> expectedOpenNetworks = new ArrayList<>();
        expectedOpenNetworks.add(
                new ScanDetail(new ScanResult.Builder(
                        WifiSsid.fromUtf8Text(CANDIDATE_SSID),
                        CANDIDATE_BSSID)
                        .setHessid(1245)
                        .setCaps("some caps")
                        .setRssi(-78)
                        .setFrequency(2450)
                        .setTsf(1025)
                        .setDistanceCm(22)
                        .setDistanceSdCm(33)
                        .setIs80211McRTTResponder(true)
                        .build()));

        when(mWifiNS.getFilteredScanDetailsForOpenUnsavedNetworks())
                .thenReturn(expectedOpenNetworks);

        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mOpenNetworkNotifier).handleScanResults(expectedOpenNetworks);
    }

    /**
     * {@link OpenNetworkNotifier} handles scan results on network selection.
     *
     * Expected behavior: ONA handles scan results
     */
    @Test
    public void wifiDisconnected_noCandidatesInScan_openNetworkNotifierScanResultsHandled() {
        // no connection candidates from scan.
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(null);

        List<ScanDetail> expectedOpenNetworks = new ArrayList<>();
        expectedOpenNetworks.add(
                new ScanDetail(
                        new ScanResult.Builder(
                                WifiSsid.fromUtf8Text(CANDIDATE_SSID),
                                CANDIDATE_BSSID)
                                .setHessid(1245)
                                .setCaps("some caps")
                                .setRssi(-78)
                                .setFrequency(2450)
                                .setTsf(1025)
                                .setDistanceCm(22)
                                .setDistanceSdCm(33)
                                .setIs80211McRTTResponder(true)
                                .build()));

        when(mWifiNS.getFilteredScanDetailsForOpenUnsavedNetworks())
                .thenReturn(expectedOpenNetworks);

        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mOpenNetworkNotifier).handleScanResults(expectedOpenNetworks);
    }

    /**
     * When wifi is connected, {@link OpenNetworkNotifier} handles the Wi-Fi connected behavior.
     *
     * Expected behavior: ONA handles connected behavior
     */
    @Test
    public void wifiConnected_openNetworkNotifierHandlesConnection() {
        // Set WiFi to connected state
        mWifiInfo.setSSID(WifiSsid.fromUtf8Text(CANDIDATE_SSID));
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID, config);
        verify(mOpenNetworkNotifier).handleWifiConnected(CANDIDATE_SSID);
    }

    /**
     * When wifi is connected, {@link OpenNetworkNotifier} handles connection state
     * change.
     *
     * Expected behavior: ONA does not clear pending notification.
     */
    @Test
    public void wifiDisconnected_openNetworkNotifierDoesNotClearPendingNotification() {
        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mOpenNetworkNotifier, never()).clearPendingNotification(anyBoolean());
    }

    /**
     * When a Wi-Fi connection attempt ends, {@link OpenNetworkNotifier} handles the connection
     * failure. A failure code that is not {@link WifiMetrics.ConnectionEvent#FAILURE_NONE}
     * represents a connection failure.
     *
     * Expected behavior: ONA handles connection failure.
     */
    @Test
    public void wifiConnectionEndsWithFailure_openNetworkNotifierHandlesConnectionFailure() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_CONNECT_NETWORK_FAILED,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                config);

        verify(mOpenNetworkNotifier).handleConnectionFailure();
    }

    /**
     * When a Wi-Fi connection attempt ends, {@link OpenNetworkNotifier} does not handle connection
     * failure after a successful connection. {@link WifiMetrics.ConnectionEvent#FAILURE_NONE}
     * represents a successful connection.
     *
     * Expected behavior: ONA does nothing.
     */
    @Test
    public void wifiConnectionEndsWithSuccess_openNetworkNotifierDoesNotHandleConnectionFailure() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID, config);

        verify(mOpenNetworkNotifier, never()).handleConnectionFailure();
    }

    /**
     * When Wi-Fi is disabled, clear the pending notification and reset notification repeat delay.
     *
     * Expected behavior: clear pending notification and reset notification repeat delay
     * */
    @Test
    public void openNetworkNotifierClearsPendingNotificationOnWifiDisabled() {
        setWifiEnabled(false);

        verify(mOpenNetworkNotifier).clearPendingNotification(true /* resetRepeatDelay */);
    }

    /**
     * Verify that the ONA controller tracks screen state changes.
     */
    @Test
    public void openNetworkNotifierTracksScreenStateChanges() {
        // Screen state change at bootup.
        verify(mOpenNetworkNotifier).handleScreenStateChanged(false);

        setScreenState(false);

        verify(mOpenNetworkNotifier, times(2)).handleScreenStateChanged(false);

        setScreenState(true);

        verify(mOpenNetworkNotifier).handleScreenStateChanged(true);
    }

    /**
     * Verify that the initial fast scan schedules the scan timer just like regular scans.
     */
    @Test
    public void testInitialFastScanSchedulesMoreScans() {
        // Enable the fast initial scan feature
        mResources.setBoolean(R.bool.config_wifiEnablePartialInitialScan, true);
        // return 2 available frequencies
        when(mWifiScoreCard.lookupNetwork(anyString())).thenReturn(mPerNetwork);
        when(mPerNetwork.getFrequencies(anyLong())).thenReturn(new ArrayList<>(
                Arrays.asList(TEST_FREQUENCY_1, TEST_FREQUENCY_2)));

        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        // set screen off and wifi disconnected
        setScreenState(false);
        mWifiConnectivityManager.handleConnectionStateChanged(mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(0 /* triggerTimes */,
                () -> {
                    // Set screen to ON to start a fast initial scan
                    setScreenState(true);
                }, currentTimeStamp);
        verifyScanTimesAndIntervals(1 /* scanTimes */, intervals,
                VALID_DISCONNECTED_SINGLE_SCAN_SCHEDULE_SEC,
                VALID_DISCONNECTED_SINGLE_SCAN_TYPE);

        // Verify the initial scan state is awaiting for response
        assertEquals(WifiConnectivityManager.INITIAL_SCAN_STATE_AWAITING_RESPONSE,
                mWifiConnectivityManager.getInitialScanState());
        verify(mWifiMetrics).incrementInitialPartialScanCount();
    }

    /**
     * Verify that if configuration for single scan schedule is empty, default
     * schedule is being used.
     */
    @Test
    public void checkPeriodicScanIntervalWhenDisconnectedWithEmptySchedule() throws Exception {
        mResources.setIntArray(R.array.config_wifiDisconnectedScanIntervalScheduleSec,
                SCHEDULE_EMPTY_SEC);
        mResources.setIntArray(R.array.config_wifiDisconnectedScanType, SCHEDULE_EMPTY_SEC);

        checkWorkingWithDefaultSchedule();
    }

    /**
     * Verify that if configuration for single scan schedule has zero values, default
     * schedule is being used.
     */
    @Test
    public void checkPeriodicScanIntervalWhenDisconnectedWithZeroValuesSchedule() {
        mResources.setIntArray(
                R.array.config_wifiDisconnectedScanIntervalScheduleSec,
                INVALID_SCHEDULE_ZERO_VALUES_SEC);
        mResources.setIntArray(R.array.config_wifiDisconnectedScanType,
                INVALID_SCHEDULE_NEGATIVE_VALUES_SEC);

        checkWorkingWithDefaultSchedule();
    }

    /**
     * Verify that if configuration for single scan schedule has negative values, default
     * schedule is being used.
     */
    @Test
    public void checkPeriodicScanIntervalWhenDisconnectedWithNegativeValuesSchedule() {
        mResources.setIntArray(
                R.array.config_wifiDisconnectedScanIntervalScheduleSec,
                INVALID_SCHEDULE_NEGATIVE_VALUES_SEC);
        mResources.setIntArray(R.array.config_wifiDisconnectedScanType,
                INVALID_SCHEDULE_NEGATIVE_VALUES_SEC);

        checkWorkingWithDefaultSchedule();
    }

    /**
     * Verify that when power save mode in on, the periodic scan interval is increased.
     */
    @Test
    public void checkPeriodicScanIntervalWhenDisconnectAndPowerSaveModeOn() throws Exception {
        mResources.setIntArray(
                R.array.config_wifiDisconnectedScanIntervalScheduleSec,
                INVALID_SCHEDULE_ZERO_VALUES_SEC);
        mResources.setIntArray(R.array.config_wifiDisconnectedScanType, SCHEDULE_EMPTY_SEC);

        when(mDeviceConfigFacade.isWifiBatterySaverEnabled()).thenReturn(true);
        when(mPowerManagerService.isPowerSaveMode()).thenReturn(true);
        checkWorkingWithDefaultScheduleWithMultiplier(POWER_SAVE_SCAN_INTERVAL_MULTIPLIER);
    }

    private void checkWorkingWithDefaultSchedule() {
        checkWorkingWithDefaultScheduleWithMultiplier(1 /* multiplier */);
    }

    /**
     * Get the value at index, or the last value if index is out of bound.
     * @param schedule Int array of schedule.
     * @param index Array index.
     */
    private int getByIndexOrLast(int[] schedule, int index) {
        return index < schedule.length ? schedule[index] : schedule[schedule.length - 1];
    }

    private void verifyScanTimesAndIntervals(int scanTimes, List<Long> intervals,
            int[] intervalSchedule, int[] scheduleScanType) {
        // Verify the scans actually happened for expected times, one scan for state change and
        // each for scan timer triggered.
        verify(mWifiScanner, times(scanTimes)).startScan(anyObject(), anyObject());

        // Verify scans are happening using the expected scan type.
        Map<Integer, Integer> scanTypeToTimesMap = new HashMap<>();
        for (int i = 0; i < scanTimes; i++) {
            int expected = getByIndexOrLast(scheduleScanType, i);
            scanTypeToTimesMap.put(expected, 1 + scanTypeToTimesMap.getOrDefault(expected, 0));
        }
        for (Map.Entry<Integer, Integer> entry : scanTypeToTimesMap.entrySet()) {
            verify(mWifiScanner, times(entry.getValue())).startScan(
                    argThat(new ArgumentMatcher<ScanSettings>() {
                        @Override
                        public boolean matches(ScanSettings scanSettings) {
                            return scanSettings.type == entry.getKey();
                        }
                    }), any());
        }

        // Verify the scan intervals are same as expected interval schedule.
        for (int i = 0; i < intervals.size(); i++) {
            long expected = (long) (getByIndexOrLast(intervalSchedule, i) * 1000);
            // TestHandler#sendMessageAtTime is not perfectly mocked and uses
            // SystemClock.uptimeMillis() to generate |intervals|. This sometimes results in error
            // margins of ~1ms and cause flaky test failures.
            final long delta = Math.abs(expected - intervals.get(i).longValue());
            assertTrue("Interval " + i + " (" + delta + ") not in 1ms error margin",
                    delta < 2);
        }
    }

    private void verifyScanTimesAndFirstInterval(int scanTimes, List<Long> intervals,
            int expectedInterval) {
        // Verify the scans actually happened for expected times, one scan for state change and
        // each for scan timer triggered.
        verify(mWifiScanner, times(scanTimes)).startScan(any(), any());

        // The actual interval should be same as scheduled.
        final long delta = Math.abs(expectedInterval * 1000L - intervals.get(0));
        assertTrue("Interval " + " (" + delta + ") not in 2ms error margin",
                delta <= 2);
    }

    /**
     * Trigger the Wifi periodic scan and get scan intervals, after setting the Wifi state.
     * @param triggerTimes The times to trigger the scheduled periodic scan. If it's 0 then don't
     *              trigger the scheduled periodic scan just return the interval.
     * @param setStateCallback The callback function to be called to set the Wifi state.
     * @param startTime The simulated scan start time.
     */
    private List<Long> triggerPeriodicScansAndGetIntervals(int triggerTimes,
            Runnable setStateCallback, long startTime) {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(startTime);
        // Call the Wifi state callback to set the specified Wifi State to test the scan intervals.
        setStateCallback.run();

        for (int i = 0; i < triggerTimes; i++) {
            // Mock the advanced time as when the scan timer supposed to fire
            when(mClock.getElapsedSinceBootMillis()).thenReturn(startTime
                    + mTestHandler.getIntervals().stream().mapToLong(Long::longValue).sum() + 10);
            // Now advance the test handler and fire the periodic scan timer
            mTestHandler.timeAdvance();
        }

        /* Verify the number of intervals recorded for periodic scans is (times + 1):
         * One initial interval by scan scheduled in setStateCallback.
         * One interval by each scan triggered.
         */
        assertEquals(triggerTimes + 1, mTestHandler.getIntervals().size());

        return mTestHandler.getIntervals();
    }

    private void checkWorkingWithDefaultScheduleWithMultiplier(float multiplier) {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mWifiConnectivityManager = createConnectivityManager();
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        setWifiEnabled(true);

        // Set screen to ON
        setScreenState(true);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(SCAN_TRIGGER_TIMES,
                () -> {
                    mWifiConnectivityManager.handleConnectionStateChanged(
                            mPrimaryClientModeManager,
                            WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
                }, currentTimeStamp);

        verifyScanTimesAndIntervals(SCAN_TRIGGER_TIMES + 1, intervals,
                Arrays.stream(DEFAULT_SINGLE_SCAN_SCHEDULE_SEC).map(i -> (int) (i * multiplier))
                .toArray(), DEFAULT_SINGLE_SCAN_TYPE);
    }

    /**
     *  Verify that scan interval for screen on and wifi disconnected scenario
     *  is in the exponential backoff fashion.
     *
     * Expected behavior: WifiConnectivityManager doubles periodic
     * scan interval.
     */
    @Test
    public void checkPeriodicScanIntervalWhenDisconnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max periodic scan interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(SCAN_TRIGGER_TIMES,
                () -> {
                    mWifiConnectivityManager.handleConnectionStateChanged(
                            mPrimaryClientModeManager,
                            WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
                }, currentTimeStamp);

        verifyScanTimesAndIntervals(SCAN_TRIGGER_TIMES + 1, intervals,
                VALID_DISCONNECTED_SINGLE_SCAN_SCHEDULE_SEC, VALID_DISCONNECTED_SINGLE_SCAN_TYPE);
    }

    @Test
    public void checkSetExternalPeriodicScanInterval() {
        assumeTrue(SdkLevel.isAtLeastT());
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mWifiConnectivityManager.setExternalScreenOnScanSchedule(
                VALID_EXTERNAL_SINGLE_SCAN_SCHEDULE_SEC, VALID_EXTERNAL_SINGLE_SCAN_TYPE);
        // Set screen to ON
        setScreenState(true);

        // Wait for max periodic scan interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(SCAN_TRIGGER_TIMES,
                () -> {
                    mWifiConnectivityManager.handleConnectionStateChanged(
                            mPrimaryClientModeManager,
                            WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
                }, currentTimeStamp);

        verifyScanTimesAndIntervals(SCAN_TRIGGER_TIMES + 1, intervals,
                VALID_EXTERNAL_SINGLE_SCAN_SCHEDULE_SEC, VALID_EXTERNAL_SINGLE_SCAN_TYPE);
    }

    @Test
    public void testSetOneShotScreenOnConnectivityScanDelayMillis() {
        assumeTrue(SdkLevel.isAtLeastT());
        int scanDelayMs = 12345;
        mWifiConnectivityManager.setOneShotScreenOnConnectivityScanDelayMillis(scanDelayMs);

        // Toggle screen to ON
        assertEquals(0, mTestHandler.getIntervals().size());
        setScreenState(false);
        setScreenState(true);
        assertEquals(1, mTestHandler.getIntervals().size());
        assertTrue("Delay is not in 1ms error margin",
                Math.abs(scanDelayMs - mTestHandler.getIntervals().get(0).longValue()) < 2);

        // Toggle again and there should be no more delayed scan
        setScreenState(false);
        setScreenState(true);
        assertEquals(1, mTestHandler.getIntervals().size());

        // set the scan delay and verify again
        scanDelayMs = 23455;
        mWifiConnectivityManager.setOneShotScreenOnConnectivityScanDelayMillis(scanDelayMs);
        setScreenState(false);
        setScreenState(true);
        assertEquals(2, mTestHandler.getIntervals().size());
        assertTrue("Delay is not in 1ms error margin",
                Math.abs(scanDelayMs - mTestHandler.getIntervals().get(1).longValue()) < 2);
    }

    /**
     *  Verify that scan interval for screen on and wifi connected scenario
     *  is in the exponential backoff fashion.
     *
     * Expected behavior: WifiConnectivityManager doubles periodic
     * scan interval.
     */
    @Test
    public void checkPeriodicScanIntervalWhenConnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        if (SdkLevel.isAtLeastT()) {
            // verify that setting the external scan schedule and then setting to null again should
            // result in no-op, and not affect the scan schedule at all.
            mWifiConnectivityManager.setExternalScreenOnScanSchedule(
                    VALID_EXTERNAL_SINGLE_SCAN_SCHEDULE_SEC, VALID_EXTERNAL_SINGLE_SCAN_TYPE);
            mWifiConnectivityManager.setExternalScreenOnScanSchedule(
                    null, null);
        }

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        List<Long> intervals = triggerPeriodicScansAndGetIntervals(SCAN_TRIGGER_TIMES,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        verifyScanTimesAndIntervals(SCAN_TRIGGER_TIMES + 1, intervals,
                VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC, VALID_CONNECTED_SINGLE_SCAN_TYPE);
    }

    /**
     *  Verify that scan interval for screen on and wifi is connected to the only network known to
     *  the device.
     */
    @Test
    public void checkPeriodicScanIntervalWhenConnectedAndOnlySingleNetwork() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);
        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanType,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_TYPE);
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.networkId = TEST_CONNECTED_NETWORK_ID;
        List<WifiConfiguration> wifiConfigurationList = new ArrayList<WifiConfiguration>();
        wifiConfigurationList.add(wifiConfiguration);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(wifiConfigurationList);

        // Set screen to ON
        setScreenState(true);
        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        List<Long> intervals = triggerPeriodicScansAndGetIntervals(SCAN_TRIGGER_TIMES,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        verifyScanTimesAndIntervals(SCAN_TRIGGER_TIMES + 1, intervals,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_TYPE);
    }

    /**
     * When screen on and single saved network schedule is set
     * If we have multiple saved networks, the regular connected state scan schedule is used
     */
    @Test
    public void checkScanScheduleForMultipleSavedNetwork() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);

        WifiConfiguration wifiConfiguration1 = new WifiConfiguration();
        WifiConfiguration wifiConfiguration2 = new WifiConfiguration();
        wifiConfiguration1.status = WifiConfiguration.Status.CURRENT;
        List<WifiConfiguration> wifiConfigurationList = new ArrayList<WifiConfiguration>();
        wifiConfigurationList.add(wifiConfiguration1);
        wifiConfigurationList.add(wifiConfiguration2);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(wifiConfigurationList);

        // Set firmware roaming to enabled
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(0 /* triggerTimes */,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    mWifiConnectivityManager.handleConnectionStateChanged(
                            mPrimaryClientModeManager,
                            WifiConnectivityManager.WIFI_STATE_CONNECTED);
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(1 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC[0]);
    }

    /**
     * When screen on and single saved network schedule is set
     * If we have a single saved network (connected network),
     * no passpoint or suggestion networks.
     * the single-saved-network connected state scan schedule is used
     */
    @Test
    public void checkScanScheduleForSingleSavedNetworkConnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);

        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.networkId = TEST_CONNECTED_NETWORK_ID;
        List<WifiConfiguration> wifiConfigurationList = new ArrayList<WifiConfiguration>();
        wifiConfigurationList.add(wifiConfiguration);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(wifiConfigurationList);

        // Set firmware roaming to enabled
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(1 /* triggerTimes */,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC[0]);
    }

    /**
     * When screen on and single saved network schedule is set
     * If we have a single saved network (not connected network),
     * no passpoint or suggestion networks.
     * the regular connected state scan schedule is used
     */
    @Test
    public void checkScanScheduleForSingleSavedNetwork() {
        int testSavedNetworkId = TEST_CONNECTED_NETWORK_ID + 1;
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);

        // Set firmware roaming to enabled
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.status = WifiConfiguration.Status.ENABLED;
        wifiConfiguration.networkId = testSavedNetworkId;
        List<WifiConfiguration> wifiConfigurationList = new ArrayList<WifiConfiguration>();
        wifiConfigurationList.add(wifiConfiguration);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(wifiConfigurationList);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(1 /* triggerTimes */,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC[0]);
    }

    /**
     * When screen on and single saved network schedule is set
     * If we have a single passpoint network (connected network),
     * and no saved or suggestion networks the single-saved-network
     * connected state scan schedule is used.
     */
    @Test
    public void checkScanScheduleForSinglePasspointNetworkConnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);

        // Prepare for a single passpoint network
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_CONNECTED_NETWORK_ID;
        String passpointKey = "PASSPOINT_KEY";
        when(mWifiConfigManager.getConfiguredNetwork(passpointKey)).thenReturn(config);
        List<PasspointConfiguration> passpointNetworks = new ArrayList<PasspointConfiguration>();
        passpointNetworks.add(mPasspointConfiguration);
        when(mPasspointConfiguration.getUniqueId()).thenReturn(passpointKey);
        when(mPasspointManager.getProviderConfigs(anyInt(), anyBoolean()))
                .thenReturn(passpointNetworks);

        // Prepare for no saved networks
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(new ArrayList<>());

        // Set firmware roaming to enabled
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(1 /* triggerTimes */,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC[0]);
    }

    /**
     * When screen on and single saved network schedule is set
     * If we have a single suggestion network (connected network),
     * and no saved network or passpoint networks the single-saved-network
     * connected state scan schedule is used
     */
    @Test
    public void checkScanScheduleForSingleSuggestionsNetworkConnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);

        // Prepare for a single suggestions network
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_CONNECTED_NETWORK_ID;
        String networkKey = "NETWORK_KEY";
        when(mWifiConfigManager.getConfiguredNetwork(networkKey)).thenReturn(config);
        when(mSuggestionConfig.getProfileKey()).thenReturn(networkKey);
        when(mWifiNetworkSuggestion.getWifiConfiguration()).thenReturn(mSuggestionConfig);
        Set<WifiNetworkSuggestion> suggestionNetworks = new HashSet<WifiNetworkSuggestion>();
        suggestionNetworks.add(mWifiNetworkSuggestion);
        when(mWifiNetworkSuggestionsManager.getAllApprovedNetworkSuggestions())
                .thenReturn(suggestionNetworks);

        // Prepare for no saved networks
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(new ArrayList<>());

        // Set firmware roaming to enabled
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(1 /* triggerTimes */,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC[0]);
    }

    /**
     * When screen on and single saved network schedule is set
     * If we have a single suggestion network (connected network),
     * and saved network/passpoint networks the regular
     * connected state scan schedule is used
     */
    @Test
    public void checkScanScheduleForSavedPasspointSuggestionNetworkConnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);

        // Prepare for a single suggestions network
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_CONNECTED_NETWORK_ID;
        String networkKey = "NETWORK_KEY";
        when(mWifiConfigManager.getConfiguredNetwork(networkKey)).thenReturn(config);
        when(mSuggestionConfig.getProfileKey()).thenReturn(networkKey);
        when(mWifiNetworkSuggestion.getWifiConfiguration()).thenReturn(mSuggestionConfig);
        Set<WifiNetworkSuggestion> suggestionNetworks = new HashSet<WifiNetworkSuggestion>();
        suggestionNetworks.add(mWifiNetworkSuggestion);
        when(mWifiNetworkSuggestionsManager.getAllApprovedNetworkSuggestions())
                .thenReturn(suggestionNetworks);

        // Prepare for a single passpoint network
        WifiConfiguration passpointConfig = new WifiConfiguration();
        String passpointKey = "PASSPOINT_KEY";
        when(mWifiConfigManager.getConfiguredNetwork(passpointKey)).thenReturn(passpointConfig);
        List<PasspointConfiguration> passpointNetworks = new ArrayList<PasspointConfiguration>();
        passpointNetworks.add(mPasspointConfiguration);
        when(mPasspointConfiguration.getUniqueId()).thenReturn(passpointKey);
        when(mPasspointManager.getProviderConfigs(anyInt(), anyBoolean()))
                .thenReturn(passpointNetworks);

        // Set firmware roaming to enabled
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(1 /* triggerTimes */,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC[0]);
    }

    /**
     * Remove network will trigger update scan and meet single network requirement.
     * Verify before disconnect finished, will not trigger single network scan schedule.
     */
    @Test
    public void checkScanScheduleForCurrentConnectedNetworkIsNull() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);

        // Set firmware roaming to enabled
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        // Set up single saved network
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.networkId = TEST_CONNECTED_NETWORK_ID;
        List<WifiConfiguration> wifiConfigurationList = new ArrayList<WifiConfiguration>();
        wifiConfigurationList.add(wifiConfiguration);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(wifiConfigurationList);

        // Set WiFi to connected state to trigger periodic scan
        setWifiStateConnected();
        mTestHandler.reset();

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(1 /* triggerTimes */,
                () -> {
                    // Simulate remove network, disconnect not finished.
                    when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                            .thenReturn(null);
                    mNetworkUpdateListenerCaptor.getValue().onNetworkRemoved(null);
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC[0]);
    }

    /**
     *  When screen on trigger a disconnected state change event then a connected state
     *  change event back to back to verify that the minium scan interval is enforced.
     *
     * Expected behavior: WifiConnectivityManager start the second periodic single
     * scan after the first one by first interval in connected scanning schedule.
     */
    @Test
    public void checkMinimumPeriodicScanIntervalWhenScreenOnAndConnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval in schedule so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        long scanForDisconnectedTimeStamp = currentTimeStamp;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set WiFi to disconnected state which triggers a scan immediately
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        verify(mWifiScanner, times(1)).startScan(anyObject(), anyObject());

        // Set up time stamp for when entering CONNECTED state
        currentTimeStamp += 2000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mTestHandler.reset();

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(1 /* triggerTimes */,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        intervals.set(0, intervals.get(0) + 2000);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC[0]);
    }

    /**
     * Check that the device does not trigger any periodic scans when it doesn't have any
     * saved, passpoint, or suggestion network and open network notifier is disabled
     */
    @Test
    public void checkNoScanWhenNoPotentialNetwork() {
        // Disable open network notifier
        when(mOpenNetworkNotifier.isSettingEnabled()).thenReturn(false);
        // Return no saved networks
        when(mWifiConfigManager.getSavedNetworks(anyInt()))
                .thenReturn(new ArrayList<WifiConfiguration>());
        // Return no suggestion networks
        when(mWifiNetworkSuggestionsManager.getAllApprovedNetworkSuggestions())
                .thenReturn(new HashSet<>());
        // Return no passpoint networks
        when(mPasspointManager.getProviderConfigs(anyInt(), anyBoolean()))
                .thenReturn(new ArrayList<>());

        // Set screen to ON
        setScreenState(true);

        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mWifiScanner, never()).startScan(anyObject(), anyObject());
    }

    /**
     *  When screen on trigger a connected state change event then a disconnected state
     *  change event back to back to verify that a scan is fired immediately for the
     *  disconnected state change event.
     *
     * Expected behavior: WifiConnectivityManager directly starts the periodic immediately
     * for the disconnected state change event. The second scan for disconnected state is
     * via alarm timer.
     */
    @Test
    public void scanImmediatelyWhenScreenOnAndDisconnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for maximum scanning interval in schedule so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        long scanForConnectedTimeStamp = currentTimeStamp;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set WiFi to connected state to trigger the periodic scan
        setWifiStateConnected();

        verify(mWifiScanner, times(1)).startScan(anyObject(), anyObject());

        // Set up the time stamp for when entering DISCONNECTED state
        currentTimeStamp += 2000;
        long enteringDisconnectedStateTimeStamp = currentTimeStamp;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mTestHandler.reset();

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(0 /* triggerTimes */,
                () -> {
                    // Set WiFi to disconnected state to trigger its periodic scan
                    mWifiConnectivityManager.handleConnectionStateChanged(
                            mPrimaryClientModeManager,
                            WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_DISCONNECTED_SINGLE_SCAN_SCHEDULE_SEC[0]);
    }

    /**
     *  When screen on trigger a connection state change event and a forced connectivity
     *  scan event back to back to verify that the minimum scan interval is not applied
     *  in this scenario.
     *
     * Expected behavior: WifiConnectivityManager starts the second periodic single
     * scan immediately.
     */
    @Test
    public void checkMinimumPeriodicScanIntervalNotEnforced() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for maximum interval in scanning schedule so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        long firstScanTimeStamp = currentTimeStamp;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set WiFi to connected state to trigger the periodic scan
        setWifiStateConnected();

        // Set the second scan attempt time stamp
        currentTimeStamp += 2000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Allow untrusted networks so WifiConnectivityManager starts a periodic scan
        // immediately.
        mWifiConnectivityManager.setUntrustedConnectionAllowed(true);

        // Get the second periodic scan actual time stamp. Note, this scan is not
        // started from the AlarmManager.
        long secondScanTimeStamp = mWifiConnectivityManager.getLastPeriodicSingleScanTimeStamp();

        // Verify that the second scan is fired immediately
        assertEquals(secondScanTimeStamp, currentTimeStamp);
    }

    /**
     * Verify that we perform full band scan in the following two cases
     * 1) Current RSSI is low, no active stream, network is insufficient
     * 2) Current RSSI is high, no active stream, and a long time since last network selection
     * 3) Current RSSI is high, no active stream, and a short time since last network selection,
     *  internet status is not acceptable
     *
     * Expected behavior: WifiConnectivityManager does full band scan in both cases
     */
    @Test
    public void verifyFullBandScanWhenConnected() {
        mResources.setInteger(
                R.integer.config_wifiConnectedHighRssiScanMinimumWindowSizeSec, 600);

        // Verify case 1
        when(mWifiNS.isNetworkSufficient(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasActiveStream(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasSufficientLinkQuality(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasInternetOrExpectNoInternet(eq(mWifiInfo))).thenReturn(true);

        final List<Integer> channelList = new ArrayList<>();
        channelList.add(TEST_FREQUENCY_1);
        channelList.add(TEST_FREQUENCY_2);
        channelList.add(TEST_FREQUENCY_3);
        WifiConfiguration configuration = WifiConfigurationTestUtil.createOpenNetwork();
        configuration.networkId = TEST_CONNECTED_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_CONNECTED_NETWORK_ID))
                .thenReturn(configuration);
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(configuration);
        when(mWifiScoreCard.lookupNetwork(configuration.SSID)).thenReturn(mPerNetwork);
        when(mPerNetwork.getFrequencies(anyLong())).thenReturn(new ArrayList<>());

        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, WifiScannerInternal.ScanListener listener)
                    throws Exception {
                if (SdkLevel.isAtLeastS()) {
                    assertEquals(WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_GHZ, settings.band);
                    assertEquals("RNR should be enabled for full scans",
                            WifiScanner.WIFI_RNR_ENABLED, settings.getRnrSetting());
                    assertTrue("PSC should be enabled for full scans",
                            settings.is6GhzPscOnlyEnabled());
                } else {
                    assertEquals(WifiScanner.WIFI_BAND_ALL, settings.band);
                }
                assertNull(settings.channels);
            }}).when(mWifiScanner).startScan(anyObject(), anyObject());

        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        // Set screen to ON
        setScreenState(true);

        // Set WiFi to connected state to trigger periodic scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);
        mLooper.dispatchAll();
        verify(mWifiScanner).startScan(anyObject(), anyObject());

        // Verify case 2
        when(mWifiNS.isNetworkSufficient(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasActiveStream(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasSufficientLinkQuality(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasInternetOrExpectNoInternet(eq(mWifiInfo))).thenReturn(true);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(600_000L + 1L);
        setScreenState(true);
        // Set WiFi to connected state to trigger periodic scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);
        verify(mWifiScanner, times(2)).startScan(anyObject(), anyObject());

        // Verify case 3
        when(mWifiNS.isNetworkSufficient(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasActiveStream(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasSufficientLinkQuality(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasInternetOrExpectNoInternet(eq(mWifiInfo))).thenReturn(false);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        setScreenState(true);
        // Set WiFi to connected state to trigger periodic scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);
        verify(mWifiScanner, times(2)).startScan(anyObject(), anyObject());
    }

    /**
     * Verify that we perform partial scan when the current RSSI is low,
     * Tx/Rx success rates are high, and when the currently connected network is present
     * in scan cache in WifiConfigManager.
     * WifiConnectivityManager does partial scan only when firmware roaming is not supported.
     *
     * Expected behavior: WifiConnectivityManager does partial scan.
     */
    @Test
    public void checkPartialScanRequestedWithLowRssiAndActiveStreamWithoutFwRoaming() {
        when(mWifiNS.isNetworkSufficient(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasActiveStream(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasSufficientLinkQuality(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasInternetOrExpectNoInternet(eq(mWifiInfo))).thenReturn(true);

        mResources.setInteger(
                R.integer.config_wifi_framework_associated_partial_scan_max_num_active_channels,
                10);

        WifiConfiguration configuration = WifiConfigurationTestUtil.createOpenNetwork();
        configuration.networkId = TEST_CONNECTED_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_CONNECTED_NETWORK_ID))
                .thenReturn(configuration);
        List<Integer> channelList = linkScoreCardFreqsToNetwork(configuration).get(0);
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(configuration);

        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(false);

        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, WifiScannerInternal.ScanListener listener)
                    throws Exception {
                assertEquals(settings.band, WifiScanner.WIFI_BAND_UNSPECIFIED);
                assertEquals(settings.channels.length, channelList.size());
                if (SdkLevel.isAtLeastS()) {
                    assertEquals("Should never force enable RNR for partial scans",
                            WifiScanner.WIFI_RNR_NOT_NEEDED, settings.getRnrSetting());
                    assertFalse("PSC should be disabled for partial scans",
                            settings.is6GhzPscOnlyEnabled());
                }
                for (int chanIdx = 0; chanIdx < settings.channels.length; chanIdx++) {
                    assertTrue(channelList.contains(settings.channels[chanIdx].frequency));
                }
                mLooper.dispatchAll();
            }}).when(mWifiScanner).startScan(anyObject(), anyObject());

        // Set screen to ON
        setScreenState(true);

        // Set WiFi to connected state to trigger periodic scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);

        verify(mWifiScanner).startScan(anyObject(), anyObject());
    }

    /**
     * Verify that we perform partial scan when the current RSSI is high,
     * Tx/Rx success rates are low, and when the currently connected network is present
     * in scan cache in WifiConfigManager.
     * WifiConnectivityManager does partial scan only when firmware roaming is not supported.
     *
     * Expected behavior: WifiConnectivityManager does partial scan.
     */
    @Test
    public void checkPartialSCanRequestedWithHighRssiNoActiveStreamWithoutFwRoaming() {
        when(mWifiNS.isNetworkSufficient(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasActiveStream(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasSufficientLinkQuality(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasInternetOrExpectNoInternet(eq(mWifiInfo))).thenReturn(true);

        mResources.setInteger(
                R.integer.config_wifi_framework_associated_partial_scan_max_num_active_channels,
                10);

        WifiConfiguration configuration = WifiConfigurationTestUtil.createOpenNetwork();
        configuration.networkId = TEST_CONNECTED_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_CONNECTED_NETWORK_ID))
                .thenReturn(configuration);
        List<Integer> channelList = linkScoreCardFreqsToNetwork(configuration).get(0);

        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(configuration);
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(false);

        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, WifiScannerInternal.ScanListener listener)
                    throws Exception {
                assertEquals(settings.band, WifiScanner.WIFI_BAND_UNSPECIFIED);
                assertEquals(settings.channels.length, channelList.size());
                for (int chanIdx = 0; chanIdx < settings.channels.length; chanIdx++) {
                    assertTrue(channelList.contains(settings.channels[chanIdx].frequency));
                }
                mLooper.dispatchAll();
            }}).when(mWifiScanner).startScan(anyObject(), anyObject());

        // Set screen to ON
        setScreenState(true);

        // Set WiFi to connected state to trigger periodic scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);

        verify(mWifiScanner).startScan(anyObject(), anyObject());
    }


    /**
     * Verify that we fall back to full band scan when the currently connected network's tx/rx
     * success rate is high, RSSI is also high but the currently connected network
     * is not present in scan cache in WifiConfigManager.
     * This is simulated by returning an empty hashset in |makeChannelList|.
     *
     * Expected behavior: WifiConnectivityManager does full band scan.
     */
    @Test
    public void checkSingleScanSettingsWhenConnectedWithHighDataRateNotInCache() {
        when(mWifiNS.isNetworkSufficient(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasActiveStream(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasSufficientLinkQuality(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasInternetOrExpectNoInternet(eq(mWifiInfo))).thenReturn(true);

        WifiConfiguration configuration = WifiConfigurationTestUtil.createOpenNetwork();
        configuration.networkId = TEST_CONNECTED_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_CONNECTED_NETWORK_ID))
                .thenReturn(configuration);
        List<Integer> channelList = linkScoreCardFreqsToNetwork(configuration).get(0);

        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(new WifiConfiguration());

        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, WifiScannerInternal.ScanListener listener)
                    throws Exception {
                assertNull(settings.channels);
                if (SdkLevel.isAtLeastS()) {
                    assertEquals(WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_GHZ, settings.band);
                    assertEquals("RNR should be enabled for full scans",
                            WifiScanner.WIFI_RNR_ENABLED, settings.getRnrSetting());
                    assertTrue("PSC should be enabled for full scans",
                            settings.is6GhzPscOnlyEnabled());
                } else {
                    assertEquals(WifiScanner.WIFI_BAND_ALL, settings.band);
                }
                mLooper.dispatchAll();
            }}).when(mWifiScanner).startScan(anyObject(), anyObject());

        // Set screen to ON
        setScreenState(true);

        // Set WiFi to connected state to trigger periodic scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);

        verify(mWifiScanner).startScan(anyObject(), anyObject());
    }

    /**
     *  Verify that we retry connectivity scan up to MAX_SCAN_RESTART_ALLOWED times
     *  when Wifi somehow gets into a bad state and fails to scan.
     *
     * Expected behavior: WifiConnectivityManager schedules connectivity scan
     * MAX_SCAN_RESTART_ALLOWED times.
     */
    @Test
    public void checkMaximumScanRetry() {
        // Set screen to ON
        setScreenState(true);

        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, WifiScannerInternal.ScanListener listener)
                    throws Exception {
                listener.onFailure(-1, "ScanFailure");
                mLooper.dispatchAll();
            }}).when(mWifiScanner).startScan(anyObject(), anyObject());

        // Set WiFi to disconnected state to trigger the single scan based periodic scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // Fire the alarm timer 2x timers
        for (int i = 0; i < (WifiConnectivityManager.MAX_SCAN_RESTART_ALLOWED * 2); i++) {
            mAlarmManager.dispatch(WifiConnectivityManager.RESTART_SINGLE_SCAN_TIMER_TAG);
            mLooper.dispatchAll();
        }

        // Verify that the connectivity scan has been retried for MAX_SCAN_RESTART_ALLOWED
        // times. Note, WifiScanner.startScan() is invoked MAX_SCAN_RESTART_ALLOWED + 1 times.
        // The very first scan is the initial one, and the other MAX_SCAN_RESTART_ALLOWED
        // are the retrial ones.
        verify(mWifiScanner, times(WifiConnectivityManager.MAX_SCAN_RESTART_ALLOWED + 1)).startScan(
                anyObject(), anyObject());
    }

    @Test
    public void testNoRetryScanWhenScreenOff() {
        // Set screen to ON
        setScreenState(true);

        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, WifiScannerInternal.ScanListener listener)
                    throws Exception {
                listener.onFailure(-1, "ScanFailure");
                mLooper.dispatchAll();
            }}).when(mWifiScanner).startScan(anyObject(), anyObject());

        // Set WiFi to disconnected state to trigger the single scan based periodic scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);


        // turn the screen off
        setScreenState(false);
        // Fire the alarm timer 2x timers
        for (int i = 0; i < (WifiConnectivityManager.MAX_SCAN_RESTART_ALLOWED * 2); i++) {
            mAlarmManager.dispatch(WifiConnectivityManager.RESTART_SINGLE_SCAN_TIMER_TAG);
            mLooper.dispatchAll();
        }

        // Verify that the connectivity scan has happened 2 times. Note, the first scan is due
        // to the initial request, and the second scan is the first retry after failure.
        // There are no more retries afterwards because the screen is off.
        verify(mWifiScanner, times(2)).startScan(anyObject(), anyObject());
    }

    /**
     * Verify that a successful scan result resets scan retry counter
     *
     * Steps
     * 1. Trigger a scan that fails
     * 2. Let the retry succeed
     * 3. Trigger a scan again and have it and all subsequent retries fail
     * 4. Verify that there are MAX_SCAN_RESTART_ALLOWED + 3 startScan calls. (2 are from the
     * original scans, and MAX_SCAN_RESTART_ALLOWED + 1 from retries)
     */
    @Test
    public void verifyScanFailureCountIsResetAfterOnResult() {
        setScreenState(true);
        // Setup WifiScanner to fail
        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, WifiScannerInternal.ScanListener listener)
                    throws Exception {
                listener.onFailure(-1, "ScanFailure");
                mLooper.dispatchAll();
            }}).when(mWifiScanner).startScan(anyObject(), anyObject());

        mWifiConnectivityManager.forceConnectivityScan(null);
        // make the retry succeed
        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, WifiScannerInternal.ScanListener listener)
                    throws Exception {
                listener.onResults(null);
                mLooper.dispatchAll();
            }}).when(mWifiScanner).startScan(anyObject(), anyObject());
        mAlarmManager.dispatch(WifiConnectivityManager.RESTART_SINGLE_SCAN_TIMER_TAG);
        mLooper.dispatchAll();

        // Verify that startScan is called once for the original scan, plus once for the retry.
        // The successful retry should have now cleared the restart count
        verify(mWifiScanner, times(2)).startScan(
                anyObject(), anyObject());

        // Now force a new scan and verify we retry MAX_SCAN_RESTART_ALLOWED times
        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, WifiScannerInternal.ScanListener listener)
                    throws Exception {
                listener.onFailure(-1, "ScanFailure");
                mLooper.dispatchAll();
            }}).when(mWifiScanner).startScan(anyObject(), anyObject());
        mWifiConnectivityManager.forceConnectivityScan(null);
        // Fire the alarm timer 2x timers
        for (int i = 0; i < (WifiConnectivityManager.MAX_SCAN_RESTART_ALLOWED * 2); i++) {
            mAlarmManager.dispatch(WifiConnectivityManager.RESTART_SINGLE_SCAN_TIMER_TAG);
            mLooper.dispatchAll();
        }

        // Verify that the connectivity scan has been retried for MAX_SCAN_RESTART_ALLOWED + 3
        // times. Note, WifiScanner.startScan() is invoked 2 times by the first part of this test,
        // and additionally MAX_SCAN_RESTART_ALLOWED + 1 times from forceConnectivityScan and
        // subsequent retries.
        verify(mWifiScanner, times(WifiConnectivityManager.MAX_SCAN_RESTART_ALLOWED + 3)).startScan(
                anyObject(), anyObject());
    }

    /**
     * Listen to scan results not requested by WifiConnectivityManager and
     * act on them.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the
     * expected candidate network ID and BSSID.
     */
    @Test
    public void listenToAllSingleScanResults() {
        ScanSettings settings = new ScanSettings();
        WifiScannerInternal.ScanListener scanListener = new WifiScannerInternal.ScanListener(mock(
                WifiScanner.ScanListener.class), mTestHandler);

        // Request a single scan outside of WifiConnectivityManager.
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();
        // Verify that WCM receives the scan results and initiates a connection
        // to the network.
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Verifies that if R.bool.config_wifiAskUserBeforeSwitchingFromUserSelectedNetwork is true,
     * then we don't show the network switch dialog if the sufficiency check is disabled.
     */
    @Test
    public void testSufficiencyCheckDisabledDoesNotShowNetworkSwitchDialog() {
        mResources.setBoolean(
                R.bool.config_wifiAskUserBeforeSwitchingFromUserSelectedNetwork, true);

        // Start off connected to a user-selected network.
        setWifiStateConnected();
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_CONNECTED_NETWORK_ID;
        config.setIsUserSelected(true);
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration()).thenReturn(config);
        when(mWifiNS.isSufficiencyCheckEnabled()).thenReturn(false);

        // Request a single scan to trigger network selection.
        ScanSettings settings = new ScanSettings();
        WifiScannerInternal.ScanListener scanListener = new WifiScannerInternal.ScanListener(mock(
                WifiScanner.ScanListener.class), mTestHandler);
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();

        // Verify we started the connection without the dialog.
        verify(mWifiDialogManager, never()).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle, never()).launchDialog();
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Verifies that if R.bool.config_wifiAskUserBeforeSwitchingFromUserSelectedNetwork is true,
     * then we switch away from a user-connected network only after the user accepts the dialog.
     */
    @Test
    public void testUserApprovedNetworkSwitch() {
        mResources.setBoolean(
                R.bool.config_wifiAskUserBeforeSwitchingFromUserSelectedNetwork, true);

        // Start off connected to a user-selected network.
        setWifiStateConnected();
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_CONNECTED_NETWORK_ID;
        config.setIsUserSelected(true);
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration()).thenReturn(config);

        // Request a single scan to trigger network selection.
        ScanSettings settings = new ScanSettings();
        WifiScannerInternal.ScanListener scanListener = new WifiScannerInternal.ScanListener(mock(
                WifiScanner.ScanListener.class), mTestHandler);
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();

        // Verify dialog was launched and we haven't started the connection.
        verify(mWifiDialogManager).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle).launchDialog();
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Accept the dialog.
        mSimpleDialogCallbackCaptor.getValue().onPositiveButtonClicked();

        // Now we connect.
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Verifies that if R.bool.config_wifiAskUserBeforeSwitchingFromUserSelectedNetwork is true,
     * then we don't switch away from a user-connected network if the user rejects the dialog.
     */
    @Test
    public void testUserRejectedNetworkSwitch() {
        mResources.setBoolean(
                R.bool.config_wifiAskUserBeforeSwitchingFromUserSelectedNetwork, true);

        // Start off connected to a user-selected network.
        setWifiStateConnected();
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_CONNECTED_NETWORK_ID;
        config.setIsUserSelected(true);
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration()).thenReturn(config);

        // Request a single scan to trigger network selection.
        ScanSettings settings = new ScanSettings();
        WifiScannerInternal.ScanListener scanListener = new WifiScannerInternal.ScanListener(mock(
                WifiScanner.ScanListener.class), mTestHandler);
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();

        // Verify dialog was launched, and we haven't started the connection.
        verify(mWifiDialogManager).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle).launchDialog();
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Reject the dialog.
        mSimpleDialogCallbackCaptor.getValue().onNegativeButtonClicked();

        // No connection.
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Trigger another scan and verify we don't show any more dialogs.
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();
        verify(mWifiDialogManager, times(1)).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle, times(1)).launchDialog();
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Disconnect and reconnect. Now we should show the dialog again.
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        setWifiStateConnected();
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration()).thenReturn(config);
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();
        verify(mWifiDialogManager, times(2)).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle, times(2)).launchDialog();
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Verifies that the network switch dialog is updated when the candidate changes or the dialog
     * is requested to be dismissed.
     */
    @Test
    public void testNetworkSwitchDialogUpdated() {
        mResources.setBoolean(
                R.bool.config_wifiAskUserBeforeSwitchingFromUserSelectedNetwork, true);

        // Start off connected to a user-selected network.
        setWifiStateConnected();
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_CONNECTED_NETWORK_ID;
        config.setIsUserSelected(true);
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration()).thenReturn(config);

        // Request a single scan to trigger network selection.
        ScanSettings settings = new ScanSettings();
        WifiScannerInternal.ScanListener scanListener = new WifiScannerInternal.ScanListener(mock(
                WifiScanner.ScanListener.class), mTestHandler);
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();

        // Verify dialog was launched, and we haven't started the connection.
        verify(mWifiDialogManager).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle).launchDialog();
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Same candidate should not refresh the dialog
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();
        verify(mWifiDialogManager).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle).launchDialog();
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Different candidate should refresh the dialog
        mCandidateWifiConfig1.networkId++;
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();
        verify(mDialogHandle).dismissDialog();
        verify(mWifiDialogManager, times(2)).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle, times(2)).launchDialog();
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Disconnect should dismiss the dialog
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        verify(mDialogHandle, times(2)).dismissDialog();
    }

    /**
     * Verifies that the network switch dialog can be disabled for a specified duration.
     */
    @Test
    public void testNetworkSwitchDialogDisabled() {
        mResources.setBoolean(
                R.bool.config_wifiAskUserBeforeSwitchingFromUserSelectedNetwork, true);

        // Start off connected to a user-selected network.
        setWifiStateConnected();
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_CONNECTED_NETWORK_ID;
        config.setIsUserSelected(true);
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration()).thenReturn(config);

        // Request a single scan to trigger network selection.
        ScanSettings settings = new ScanSettings();
        WifiScannerInternal.ScanListener scanListener = new WifiScannerInternal.ScanListener(mock(
                WifiScanner.ScanListener.class), mTestHandler);
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();

        // Verify dialog was launched, and we haven't started the connection.
        verify(mWifiDialogManager).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle).launchDialog();
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Disable the dialog for a while
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        mWifiConnectivityManager.disableNetworkSwitchDialog(1000);
        verify(mDialogHandle).dismissDialog();

        // Dialog should not come up again while it's disabled.
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();
        verify(mWifiDialogManager).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle).launchDialog();
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Dialog should come up after the dialog is reenabled
        when(mClock.getElapsedSinceBootMillis()).thenReturn(1000L);
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();
        verify(mDialogHandle).dismissDialog();
        verify(mWifiDialogManager, times(2)).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle, times(2)).launchDialog();
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Verifies that a network switch dialog will be dismissed if associated network selection is
     * set from enabled -> disabled.
     */
    @Test
    public void testAssociatedNetworkSelectionDisabledDismissesNetworkSwitchDialog() {
        mResources.setBoolean(
                R.bool.config_wifiAskUserBeforeSwitchingFromUserSelectedNetwork, true);

        // Start off connected to a user-selected network.
        setWifiStateConnected();
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_CONNECTED_NETWORK_ID;
        config.setIsUserSelected(true);
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration()).thenReturn(config);

        // Request a single scan to trigger network selection.
        ScanSettings settings = new ScanSettings();
        WifiScannerInternal.ScanListener scanListener = new WifiScannerInternal.ScanListener(mock(
                WifiScanner.ScanListener.class), mTestHandler);
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();

        // Verify dialog was launched, and we haven't started the connection.
        verify(mWifiDialogManager).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle).launchDialog();
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Setting associated network selection to disabled should dismiss the dialog
        when(mWifiNS.isAssociatedNetworkSelectionEnabled())
                .thenReturn(true, false);
        mWifiConnectivityManager.setNetworkSelectionConfig(
                new WifiNetworkSelectionConfig.Builder().build());
        verify(mDialogHandle).dismissDialog();
    }

    /**
     * Verifies that the user's choice for network switch dialogs will be reset if associated
     * network selection is set from disabled -> enabled.
     */
    @Test
    public void testAssociatedNetworkSelectionEnabledResetsNetworkSwitchDialog() {
        mResources.setBoolean(
                R.bool.config_wifiAskUserBeforeSwitchingFromUserSelectedNetwork, true);

        // Start off connected to a user-selected network.
        setWifiStateConnected();
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_CONNECTED_NETWORK_ID;
        config.setIsUserSelected(true);
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration()).thenReturn(config);

        // Request a single scan to trigger network selection.
        ScanSettings settings = new ScanSettings();
        WifiScannerInternal.ScanListener scanListener = new WifiScannerInternal.ScanListener(mock(
                WifiScanner.ScanListener.class), mTestHandler);
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();

        // Verify dialog was launched, and we haven't started the connection.
        verify(mWifiDialogManager).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle).launchDialog();
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Reject the dialog.
        mSimpleDialogCallbackCaptor.getValue().onNegativeButtonClicked();

        // No connection.
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Trigger another scan and verify we don't show any more dialogs.
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();
        verify(mWifiDialogManager, times(1)).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle, times(1)).launchDialog();
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Setting associated network selection from disabled to enabled should reset the
        // user's choice and show a dialog again on the next scan.
        // Setting associated network selection to disabled should dismiss the dialog
        when(mWifiNS.isAssociatedNetworkSelectionEnabled())
                .thenReturn(false, true);
        mWifiConnectivityManager.setNetworkSelectionConfig(
                new WifiNetworkSelectionConfig.Builder().build());
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();
        verify(mWifiDialogManager, times(2)).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle, times(2)).launchDialog();
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }


    /**
     * Verifies that the network switch dialog is dismissed when the global autojoin is disabled.
     */
    @Test
    public void testDisableAutojoinGlobalDismissesNetworkSwitchDialog() {
        mResources.setBoolean(
                R.bool.config_wifiAskUserBeforeSwitchingFromUserSelectedNetwork, true);

        // Start off connected to a user-selected network.
        setWifiStateConnected();
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_CONNECTED_NETWORK_ID;
        config.setIsUserSelected(true);
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration()).thenReturn(config);

        // Request a single scan to trigger network selection.
        ScanSettings settings = new ScanSettings();
        WifiScannerInternal.ScanListener scanListener = new WifiScannerInternal.ScanListener(mock(
                WifiScanner.ScanListener.class), mTestHandler);
        mWifiScanner.startScan(settings, scanListener);
        mLooper.dispatchAll();

        // Verify dialog was launched, and we haven't started the connection.
        verify(mWifiDialogManager).createSimpleDialog(any(), any(), any(), any(), any(),
                mSimpleDialogCallbackCaptor.capture(), any());
        verify(mDialogHandle).launchDialog();
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Setting global autojoin to disabled should dismiss the dialog.
        mWifiConnectivityManager.setAutoJoinEnabledExternal(false, false);
        verify(mDialogHandle).dismissDialog();
    }

    /**
     *  Verify that a forced connectivity scan waits for full band scan
     *  results.
     *
     * Expected behavior: WifiConnectivityManager doesn't invoke
     * ClientModeManager.startConnectToNetwork() when full band scan
     * results are not available.
     */
    @Test
    public void waitForFullBandScanResults() {
        // Set WiFi to connected state.
        setWifiStateConnected();

        // Set up as partial scan results.
        when(mScanData.getScannedBandsInternal()).thenReturn(WifiScanner.WIFI_BAND_5_GHZ);

        // Force a connectivity scan which enables WifiConnectivityManager
        // to wait for full band scan results.
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        mLooper.dispatchAll();
        // No roaming because no full band scan results.
        verify(mPrimaryClientModeManager, times(0)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Set up as full band scan results.
        when(mScanData.getScannedBandsInternal()).thenReturn(WifiScanner.WIFI_BAND_ALL);

        // Force a connectivity scan which enables WifiConnectivityManager
        // to wait for full band scan results.
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        mLooper.dispatchAll();
        // Roaming attempt because full band scan results are available.
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Verify when new scanResults are available, UserDisabledList will be updated.
     */
    @Test
    public void verifyUserDisabledListUpdated() {
        mResources.setBoolean(
                R.bool.config_wifi_framework_use_single_radio_chain_scan_results_network_selection,
                true);
        verify(mWifiConfigManager, never()).updateUserDisabledList(anyList());
        Set<String> updateNetworks = new HashSet<>();
        mScanData = createScanDataWithDifferentRadioChainInfos();
        int i = 0;
        for (ScanResult scanResult : mScanData.getResults()) {
            scanResult.SSID = TEST_SSID + i;
            updateNetworks.add(ScanResultUtil.createQuotedSsid(scanResult.SSID));
            i++;
        }
        updateNetworks.add(TEST_FQDN);
        mScanData.getResults()[0].setFlag(ScanResult.FLAG_PASSPOINT_NETWORK);
        HashMap<String, Map<Integer, List<ScanResult>>> passpointNetworks = new HashMap<>();
        passpointNetworks.put(TEST_FQDN, new HashMap<>());
        when(mPasspointManager.getAllMatchingPasspointProfilesForScanResults(any()))
                .thenReturn(passpointNetworks);

        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        mLooper.dispatchAll();
        ArgumentCaptor<ArrayList<String>> listArgumentCaptor =
                ArgumentCaptor.forClass(ArrayList.class);
        verify(mWifiConfigManager).updateUserDisabledList(listArgumentCaptor.capture());
        assertEquals(updateNetworks, new HashSet<>(listArgumentCaptor.getValue()));
    }

    /**
     * Verify that after receiving scan results, we attempt to clear expired recent failure reasons.
     */
    @Test
    public void verifyClearExpiredRecentFailureStatusAfterScan() {
        // mWifiScanner is mocked to directly return scan results when a scan is triggered.
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).cleanupExpiredRecentFailureReasons();
    }

    /**
     *  Verify that a blocklisted BSSID becomes available only after
     *  BSSID_BLOCKLIST_EXPIRE_TIME_MS.
     */
    @Test
    public void verifyBlocklistRefreshedAfterScanResults() {
        WifiConfiguration disabledConfig = WifiConfigurationTestUtil.createPskNetwork();
        disabledConfig.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED);
        List<ScanDetail> mockScanDetails = new ArrayList<>();
        mockScanDetails.add(mock(ScanDetail.class));
        when(mWifiBlocklistMonitor.tryEnablingBlockedBssids(any())).thenReturn(mockScanDetails);
        when(mWifiConfigManager.getSavedNetworkForScanDetail(any())).thenReturn(
                disabledConfig);

        InOrder inOrder = inOrder(mWifiBlocklistMonitor, mWifiConfigManager);
        // Force a connectivity scan
        inOrder.verify(mWifiBlocklistMonitor, never())
                .updateAndGetBssidBlocklistForSsids(anySet());
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        mLooper.dispatchAll();
        inOrder.verify(mWifiBlocklistMonitor).tryEnablingBlockedBssids(any());
        inOrder.verify(mWifiConfigManager).updateNetworkSelectionStatus(disabledConfig.networkId,
                WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE);
        inOrder.verify(mWifiBlocklistMonitor).updateAndGetBssidBlocklistForSsids(anySet());
    }

    /**
     *  Verify that a blocklisted BSSID becomes available only after
     *  BSSID_BLOCKLIST_EXPIRE_TIME_MS, but will not re-enable a permanently disabled
     *  WifiConfiguration.
     */
    @Test
    public void verifyBlocklistRefreshedAfterScanResultsButIgnorePermanentlyDisabledConfigs() {
        WifiConfiguration disabledConfig = WifiConfigurationTestUtil.createPskNetwork();
        disabledConfig.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED);
        List<ScanDetail> mockScanDetails = new ArrayList<>();
        mockScanDetails.add(mock(ScanDetail.class));
        when(mWifiBlocklistMonitor.tryEnablingBlockedBssids(any())).thenReturn(mockScanDetails);
        when(mWifiConfigManager.getSavedNetworkForScanDetail(any())).thenReturn(
                disabledConfig);

        InOrder inOrder = inOrder(mWifiBlocklistMonitor, mWifiConfigManager);
        // Force a connectivity scan
        inOrder.verify(mWifiBlocklistMonitor, never())
                .updateAndGetBssidBlocklistForSsids(anySet());
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        mLooper.dispatchAll();
        inOrder.verify(mWifiBlocklistMonitor).tryEnablingBlockedBssids(any());
        inOrder.verify(mWifiConfigManager, never()).updateNetworkSelectionStatus(
                disabledConfig.networkId,
                WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE);
        inOrder.verify(mWifiBlocklistMonitor).updateAndGetBssidBlocklistForSsids(anySet());
    }

    /**
     *  Verify blocklists and ephemeral networks are cleared from WifiConfigManager when exiting
     *  Wifi client mode. And if requires, ANQP cache is also flushed.
     */
    @Test
    public void clearEnableTemporarilyDisabledNetworksWhenExitingWifiClientMode() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        when(mWifiGlobals.flushAnqpCacheOnWifiToggleOffEvent()).thenReturn(true);
        // Exit Wifi client mode.
        setWifiEnabled(false);

        // Verify the blocklists is cleared again.
        verify(mWifiConfigManager).enableTemporaryDisabledNetworks();
        verify(mWifiConfigManager).stopRestrictingAutoJoinToSubscriptionId();
        verify(mWifiConfigManager).removeAllEphemeralOrPasspointConfiguredNetworks();
        verify(mWifiConfigManager).clearUserTemporarilyDisabledList();

        // Verify ANQP cache is flushed.
        verify(mPasspointManager).clearAnqpRequestsAndFlushCache();
        // Verify WifiNetworkSelector is informed of the disable.
        verify(mWifiNS).resetOnDisable();
    }

    /**
     * Verifies that the ANQP cache is not flushed when the configuration does not permit it.
     */
    @Test
    public void testAnqpFlushCacheSkippedIfNotConfigured() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        when(mWifiGlobals.flushAnqpCacheOnWifiToggleOffEvent()).thenReturn(false);
        // Exit Wifi client mode.
        setWifiEnabled(false);

        // Verify ANQP cache is not flushed.
        verify(mPasspointManager, never()).clearAnqpRequestsAndFlushCache();
    }

    /**
     *  Verify that BSSID blocklist gets cleared when preparing for a forced connection
     *  initiated by user/app.
     */
    @Test
    public void clearBssidBlocklistWhenPreparingForForcedConnection() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        // Prepare for a forced connection attempt.
        WifiConfiguration currentNetwork = generateWifiConfig(
                0, CANDIDATE_NETWORK_ID, CANDIDATE_SSID, false, true, null, null,
                WifiConfigurationTestUtil.SECURITY_NONE);
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(currentNetwork);
        mWifiConnectivityManager.prepareForForcedConnection(1);
        verify(mWifiBlocklistMonitor).clearBssidBlocklistForSsid(CANDIDATE_SSID);
    }

    /**
     * When WifiConnectivityManager is on and Wifi client mode is enabled, framework
     * queries firmware via WifiConnectivityHelper to check if firmware roaming is
     * supported and its capability.
     *
     * Expected behavior: WifiConnectivityManager#setWifiEnabled calls into
     * WifiConnectivityHelper#getFirmwareRoamingInfo
     */
    @Test
    public void verifyGetFirmwareRoamingInfoIsCalledWhenEnableWiFiAndWcmOn() {
        // WifiConnectivityManager is on by default
        setWifiEnabled(true);
        verify(mWifiConnectivityHelper).getFirmwareRoamingInfo();
    }

    /**
     * When WifiConnectivityManager is off,  verify that framework does not
     * query firmware via WifiConnectivityHelper to check if firmware roaming is
     * supported and its capability when enabling Wifi client mode.
     *
     * Expected behavior: WifiConnectivityManager#setWifiEnabled does not call into
     * WifiConnectivityHelper#getFirmwareRoamingInfo
     */
    @Test
    public void verifyGetFirmwareRoamingInfoIsNotCalledWhenEnableWiFiAndWcmOff() {
        reset(mWifiConnectivityHelper);
        mWifiConnectivityManager.setAutoJoinEnabledExternal(false, false);
        setWifiEnabled(true);
        verify(mWifiConnectivityHelper, times(0)).getFirmwareRoamingInfo();
    }

    /**
     * Verify if setAutoJoinEnabledExternal is disabled by a device admin, it cannot be re-enabled
     * by a non device admin caller.
     */
    @Test
    public void testSetAutoJoinEnabledExternalDeviceOwnerPrivileged() {
        assertTrue(mWifiConnectivityManager.getAutoJoinEnabledExternal());

        // test disable/enable by non device admin
        mWifiConnectivityManager.setAutoJoinEnabledExternal(false, false);
        assertFalse(mWifiConnectivityManager.getAutoJoinEnabledExternal());
        mWifiConnectivityManager.setAutoJoinEnabledExternal(true, false);
        assertTrue(mWifiConnectivityManager.getAutoJoinEnabledExternal());

        // test disable by device admin
        mWifiConnectivityManager.setAutoJoinEnabledExternal(false, true);
        assertFalse(mWifiConnectivityManager.getAutoJoinEnabledExternal());

        // verify that a non device admin cannot re-enable autoJoin
        mWifiConnectivityManager.setAutoJoinEnabledExternal(true, false);
        assertFalse(mWifiConnectivityManager.getAutoJoinEnabledExternal());

        // verify device admin setting autojoin back to true
        mWifiConnectivityManager.setAutoJoinEnabledExternal(true, true);
        assertTrue(mWifiConnectivityManager.getAutoJoinEnabledExternal());

        // verify that a non device admin can now modify autoJoin
        mWifiConnectivityManager.setAutoJoinEnabledExternal(false, false);
        assertFalse(mWifiConnectivityManager.getAutoJoinEnabledExternal());
    }

    /*
     * Firmware supports controlled roaming.
     * Connect to a network which doesn't have a config specified BSSID.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the
     * expected candidate network ID, and the BSSID value should be
     * 'any' since firmware controls the roaming.
     */
    @Test
    public void useAnyBssidToConnectWhenFirmwareRoamingOnAndConfigHasNoBssidSpecified() {
        // Firmware controls roaming
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, ClientModeImpl.SUPPLICANT_BSSID_ANY);
    }

    /*
     * Firmware supports controlled roaming.
     * Connect to a network which has a config specified BSSID.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the
     * expected candidate network ID, and the BSSID value should be
     * the config specified one.
     */
    @Test
    public void useConfigSpecifiedBssidToConnectWhenFirmwareRoamingOn() {
        // Firmware controls roaming
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        // Set up the candidate configuration such that it has a BSSID specified.
        WifiConfiguration candidate = generateWifiConfig(
                0, CANDIDATE_NETWORK_ID, CANDIDATE_SSID, false, true, null, null,
                WifiConfigurationTestUtil.SECURITY_NONE);
        candidate.BSSID = CANDIDATE_BSSID; // config specified
        ScanResult candidateScanResult = new ScanResult.Builder(
                WifiSsid.fromUtf8Text(CANDIDATE_SSID), CANDIDATE_BSSID).build();
        candidate.getNetworkSelectionStatus().setCandidate(candidateScanResult);
        when(mWifiNS.selectNetwork(any())).thenReturn(candidate);

        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
        verify(mPrimaryClientModeManager).enableRoaming(false);

        verify(mWifiMetrics).noteFirstNetworkSelectionAfterBoot(true);
    }

    /*
     * Firmware does not support controlled roaming.
     * Connect to a network which doesn't have a config specified BSSID.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the expected candidate network ID,
     * and the BSSID value should be the candidate scan result specified.
     */
    @Test
    public void useScanResultBssidToConnectWhenFirmwareRoamingOffAndConfigHasNoBssidSpecified() {
        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /*
     * Firmware does not support controlled roaming.
     * Connect to a network which has a config specified BSSID.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the expected candidate network ID,
     * and the BSSID value should be the config specified one.
     */
    @Test
    public void useConfigSpecifiedBssidToConnectionWhenFirmwareRoamingOff() {
        // Set up the candidate configuration such that it has a BSSID specified.
        WifiConfiguration candidate = generateWifiConfig(
                0, CANDIDATE_NETWORK_ID, CANDIDATE_SSID, false, true, null, null,
                WifiConfigurationTestUtil.SECURITY_NONE);
        candidate.BSSID = CANDIDATE_BSSID; // config specified
        ScanResult candidateScanResult = new ScanResult.Builder(
                WifiSsid.fromUtf8Text(CANDIDATE_SSID), CANDIDATE_BSSID).build();
        candidate.getNetworkSelectionStatus().setCandidate(candidateScanResult);
        when(mWifiNS.selectNetwork(any())).thenReturn(candidate);

        // Set screen to on
        setScreenState(true);
        mLooper.dispatchAll();
        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Firmware does not support controlled roaming.
     * WiFi in connected state, framework triggers roaming.
     *
     * Expected behavior: WifiConnectivityManager invokes
     * ClientModeManager.startRoamToNetwork().
     */
    @Test
    public void frameworkInitiatedRoaming() {
        // Set WiFi to connected state
        setWifiStateConnected(CANDIDATE_NETWORK_ID, CANDIDATE_BSSID_2);

        // Set screen to on
        setScreenState(true);
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager).startRoamToNetwork(eq(CANDIDATE_NETWORK_ID),
                mCandidateBssidCaptor.capture());
        assertEquals(mCandidateBssidCaptor.getValue(), CANDIDATE_BSSID);
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), anyObject());
    }

    /**
     * Firmware supports controlled roaming.
     * WiFi in connected state, framework does not trigger roaming
     * as it's handed off to the firmware.
     *
     * Expected behavior: WifiConnectivityManager doesn't invoke
     * ClientModeManager.startRoamToNetwork().
     */
    @Test
    public void noFrameworkRoamingIfConnectedAndFirmwareRoamingSupported() {
        // Set WiFi to connected state
        setWifiStateConnected(CANDIDATE_NETWORK_ID, CANDIDATE_BSSID_2);

        // Firmware controls roaming
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        // Set screen to on
        setScreenState(true);
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager, never()).startRoamToNetwork(anyInt(), anyObject());
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), anyObject());
    }

    /*
     * Wifi in disconnected state. Drop the connection attempt if the recommended
     * network configuration has a BSSID specified but the scan result BSSID doesn't
     * match it.
     *
     * Expected behavior: WifiConnectivityManager doesn't invoke
     * ClientModeManager.startConnectToNetwork().
     */
    @Test
    public void dropConnectAttemptIfConfigSpecifiedBssidDifferentFromScanResultBssid() {
        // Set up the candidate configuration such that it has a BSSID specified.
        WifiConfiguration candidate = generateWifiConfig(
                0, CANDIDATE_NETWORK_ID, CANDIDATE_SSID, false, true, null, null,
                WifiConfigurationTestUtil.SECURITY_NONE);
        candidate.BSSID = CANDIDATE_BSSID; // config specified
        // Set up the scan result BSSID to be different from the config specified one.
        ScanResult candidateScanResult = new ScanResult.Builder(
                WifiSsid.fromUtf8Text(CANDIDATE_SSID), INVALID_SCAN_RESULT_BSSID).build();
        candidate.getNetworkSelectionStatus().setCandidate(candidateScanResult);
        when(mWifiNS.selectNetwork(any())).thenReturn(candidate);

        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        verify(mPrimaryClientModeManager, times(0)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /*
     * Wifi in connected state. Drop the roaming attempt if the recommended
     * network configuration has a BSSID specified but the scan result BSSID doesn't
     * match it.
     *
     * Expected behavior: WifiConnectivityManager doesn't invoke
     * ClientModeManager.startRoamToNetwork().
     */
    @Test
    public void dropRoamingAttemptIfConfigSpecifiedBssidDifferentFromScanResultBssid() {
        // Mock the currently connected network which has the same networkID and
        // SSID as the one to be selected.
        WifiConfiguration currentNetwork = generateWifiConfig(
                TEST_CONNECTED_NETWORK_ID, 0, CANDIDATE_SSID, false, true, null, null,
                WifiConfigurationTestUtil.SECURITY_NONE);
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(currentNetwork);

        // Set up the candidate configuration such that it has a BSSID specified.
        WifiConfiguration candidate = generateWifiConfig(
                TEST_CONNECTED_NETWORK_ID, 0, CANDIDATE_SSID, false, true, null, null,
                WifiConfigurationTestUtil.SECURITY_NONE);
        candidate.BSSID = CANDIDATE_BSSID; // config specified
        // Set up the scan result BSSID to be different from the config specified one.
        ScanResult candidateScanResult = new ScanResult.Builder(
                WifiSsid.fromUtf8Text(CANDIDATE_SSID), INVALID_SCAN_RESULT_BSSID).build();
        candidate.getNetworkSelectionStatus().setCandidate(candidateScanResult);
        when(mWifiNS.selectNetwork(any())).thenReturn(candidate);

        // Set WiFi to connected state
        setWifiStateConnected();

        // Set screen to on
        setScreenState(true);

        verify(mPrimaryClientModeManager, times(0)).startRoamToNetwork(anyInt(), anyObject());
    }

    @Test
    public void testMultiInternetSimultaneous5GHz() {
        // Enable dual 5GHz multi-internet mode
        when(mWifiGlobals.isSupportMultiInternetDual5G()).thenReturn(true);

        // 2.4GHz + 5GHz should be allowed
        assertTrue(mWifiConnectivityManager.filterMultiInternetFrequency(TEST_FREQUENCY,
                TEST_FREQUENCY_5G));

        // 5GHz low + 5GHz high should be allowed
        assertTrue(mWifiConnectivityManager.filterMultiInternetFrequency(
                ScanResult.BAND_5_GHZ_LOW_HIGHEST_FREQ_MHZ,
                ScanResult.BAND_5_GHZ_HIGH_LOWEST_FREQ_MHZ));

        // 5GHz low + other 5GHz (that's neither low nor high) should not be allowed
        assertFalse(mWifiConnectivityManager.filterMultiInternetFrequency(
                ScanResult.BAND_5_GHZ_LOW_HIGHEST_FREQ_MHZ,
                ScanResult.BAND_5_GHZ_HIGH_LOWEST_FREQ_MHZ - 1));

        // 2 frequencies in 5GHz low band should not be allowed
        assertFalse(mWifiConnectivityManager.filterMultiInternetFrequency(
                ScanResult.BAND_5_GHZ_LOW_HIGHEST_FREQ_MHZ,
                ScanResult.BAND_5_GHZ_LOW_HIGHEST_FREQ_MHZ - 1));

        // 2 frequencies in 5GHz high band should not be allowed
        assertFalse(mWifiConnectivityManager.filterMultiInternetFrequency(
                ScanResult.BAND_5_GHZ_HIGH_LOWEST_FREQ_MHZ,
                ScanResult.BAND_5_GHZ_HIGH_LOWEST_FREQ_MHZ + 1));

        // Disable dual 5GHz multi-internet mode
        when(mWifiGlobals.isSupportMultiInternetDual5G()).thenReturn(false);

        // 5GHz low + 5GHz high should no longer be allowed
        assertFalse(mWifiConnectivityManager.filterMultiInternetFrequency(
                ScanResult.BAND_5_GHZ_LOW_HIGHEST_FREQ_MHZ,
                ScanResult.BAND_5_GHZ_HIGH_LOWEST_FREQ_MHZ));
    }

    /**
     *  Dump local log buffer.
     *
     * Expected behavior: Logs dumped from WifiConnectivityManager.dump()
     * contain the message we put in mLocalLog.
     */
    @Test
    public void dumpLocalLog() {
        final String localLogMessage = "This is a message from the test";
        mLocalLog.log(localLogMessage);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiConnectivityManager.dump(new FileDescriptor(), pw, new String[]{});
        assertTrue(sw.toString().contains(localLogMessage));
    }

    /**
     *  Dump ONA controller.
     *
     * Expected behavior: {@link OpenNetworkNotifier#dump(FileDescriptor, PrintWriter,
     * String[])} is invoked.
     */
    @Test
    public void dumpNotificationController() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiConnectivityManager.dump(new FileDescriptor(), pw, new String[]{});

        verify(mOpenNetworkNotifier).dump(any(), any(), any());
    }

    /**
     * Create scan data with different radio chain infos:
     * First scan result has null radio chain info (No DBS support).
     * Second scan result has empty radio chain info (No DBS support).
     * Third scan result has 1 radio chain info (DBS scan).
     * Fourth scan result has 2 radio chain info (non-DBS scan).
     */
    private ScanData createScanDataWithDifferentRadioChainInfos() {
        // Create 4 scan results.
        ScanData[] scanDatas =
                ScanTestUtil.createScanDatas(new int[][]{{5150, 5175, 2412, 2400}}, new int[]{0});
        // WCM barfs if the scan result does not have an IE.
        scanDatas[0].getResults()[0].informationElements = new InformationElement[0];
        scanDatas[0].getResults()[1].informationElements = new InformationElement[0];
        scanDatas[0].getResults()[2].informationElements = new InformationElement[0];
        scanDatas[0].getResults()[3].informationElements = new InformationElement[0];
        scanDatas[0].getResults()[0].radioChainInfos = null;
        scanDatas[0].getResults()[1].radioChainInfos = new ScanResult.RadioChainInfo[0];
        scanDatas[0].getResults()[2].radioChainInfos = new ScanResult.RadioChainInfo[1];
        scanDatas[0].getResults()[3].radioChainInfos = new ScanResult.RadioChainInfo[2];

        return scanDatas[0];
    }

    /**
     * If |config_wifi_framework_use_single_radio_chain_scan_results_network_selection| flag is
     * false, WifiConnectivityManager should filter scan results which contain scans from a single
     * radio chain (i.e DBS scan).
     * Note:
     * a) ScanResult with no radio chain indicates a lack of DBS support on the device.
     * b) ScanResult with 2 radio chain info indicates a scan done using both the radio chains
     * on a DBS supported device.
     *
     * Expected behavior: WifiConnectivityManager invokes
     * {@link WifiNetworkSelector#getCandidatesFromScan(List, Set, List, boolean, boolean, Set, boolean)}
     * boolean, boolean, boolean)} after filtering out the scan results obtained via DBS scan.
     */
    @Test
    public void filterScanResultsWithOneRadioChainInfoForNetworkSelectionIfConfigDisabled() {
        mResources.setBoolean(
                R.bool.config_wifi_framework_use_single_radio_chain_scan_results_network_selection,
                false);
        when(mWifiNS.selectNetwork(any())).thenReturn(null);
        mWifiConnectivityManager = createConnectivityManager();

        mScanData = createScanDataWithDifferentRadioChainInfos();

        // Capture scan details which were sent to network selector.
        final List<ScanDetail> capturedScanDetails = new ArrayList<>();
        doAnswer(new AnswerWithArguments() {
            public List<WifiCandidates.Candidate> answer(
                    List<ScanDetail> scanDetails, Set<String> bssidBlocklist,
                    List<WifiNetworkSelector.ClientModeManagerState> cmmStates,
                    boolean untrustedNetworkAllowed,
                    boolean oemPaidNetworkAllowed, boolean oemPrivateNetworkAllowed,
                    Set<Integer> restrictedNetworkAllowedUids, boolean multiInternetNetworkAllowed)
                    throws Exception {
                capturedScanDetails.addAll(scanDetails);
                return null;
            }}).when(mWifiNS).getCandidatesFromScan(
                    any(), any(), any(), anyBoolean(), eq(true), eq(false), any(), eq(false));

        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, new WorkSource());
        // Set WiFi to disconnected state with screen on which triggers a scan immediately.
        setWifiEnabled(true);
        setScreenState(true);
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        // We should have filtered out the 3rd scan result.
        assertEquals(3, capturedScanDetails.size());
        List<ScanResult> capturedScanResults =
                capturedScanDetails.stream().map(ScanDetail::getScanResult)
                        .collect(Collectors.toList());

        assertEquals(3, capturedScanResults.size());
        assertTrue(capturedScanResults.contains(mScanData.getResults()[0]));
        assertTrue(capturedScanResults.contains(mScanData.getResults()[1]));
        assertFalse(capturedScanResults.contains(mScanData.getResults()[2]));
        assertTrue(capturedScanResults.contains(mScanData.getResults()[3]));
    }

    /**
     * If |config_wifi_framework_use_single_radio_chain_scan_results_network_selection| flag is
     * true, WifiConnectivityManager should not filter scan results which contain scans from a
     * single radio chain (i.e DBS scan).
     * Note:
     * a) ScanResult with no radio chain indicates a lack of DBS support on the device.
     * b) ScanResult with 2 radio chain info indicates a scan done using both the radio chains
     * on a DBS supported device.
     *
     * Expected behavior: WifiConnectivityManager invokes
     * {@link WifiNetworkSelector#selectNetwork(List)}
     * after filtering out the scan results obtained via DBS scan.
     */
    @Test
    public void dontFilterScanResultsWithOneRadioChainInfoForNetworkSelectionIfConfigEnabled() {
        mResources.setBoolean(
                R.bool.config_wifi_framework_use_single_radio_chain_scan_results_network_selection,
                true);
        when(mWifiNS.selectNetwork(any())).thenReturn(null);
        mWifiConnectivityManager = createConnectivityManager();

        mScanData = createScanDataWithDifferentRadioChainInfos();

        // Capture scan details which were sent to network selector.
        final List<ScanDetail> capturedScanDetails = new ArrayList<>();
        doAnswer(new AnswerWithArguments() {
            public List<WifiCandidates.Candidate> answer(
                    List<ScanDetail> scanDetails, Set<String> bssidBlocklist,
                    List<WifiNetworkSelector.ClientModeManagerState> cmmStates,
                    boolean untrustedNetworkAllowed,
                    boolean oemPaidNetworkAllowed, boolean oemPrivateNetworkAllowed,
                    Set<Integer> restrictedNetworkAllowedUids, boolean multiInternetNetworkAllowed)
                    throws Exception {
                capturedScanDetails.addAll(scanDetails);
                return null;
            }}).when(mWifiNS).getCandidatesFromScan(
                any(), any(), any(), anyBoolean(), eq(false), eq(true), any(), eq(false));

        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        mWifiConnectivityManager.setOemPrivateConnectionAllowed(true, new WorkSource());
        // Set WiFi to disconnected state with screen on which triggers a scan immediately.
        setWifiEnabled(true);
        setScreenState(true);
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // We should not filter any of the scan results.
        assertEquals(4, capturedScanDetails.size());
        List<ScanResult> capturedScanResults =
                capturedScanDetails.stream().map(ScanDetail::getScanResult)
                        .collect(Collectors.toList());

        assertEquals(4, capturedScanResults.size());
        assertTrue(capturedScanResults.contains(mScanData.getResults()[0]));
        assertTrue(capturedScanResults.contains(mScanData.getResults()[1]));
        assertTrue(capturedScanResults.contains(mScanData.getResults()[2]));
        assertTrue(capturedScanResults.contains(mScanData.getResults()[3]));
    }

    /**
     * Verify the various auto join enable/disable sequences when auto join is disabled externally.
     *
     * Expected behavior: Autojoin is turned on as a long as there is
     *  - Auto join is enabled externally
     *    And
     *  - No specific network request being processed.
     *    And
     *    - Pending generic Network request for trusted wifi connection.
     *      OR
     *    - Pending generic Network request for untrused wifi connection.
     */
    @Test
    public void verifyEnableAndDisableAutoJoinWhenExternalAutoJoinIsDisabled() {
        mWifiConnectivityManager = createConnectivityManager();

        // set wifi on & disconnected to trigger pno scans when auto-join is enabled.
        setWifiEnabled(true);
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // Disable externally.
        mWifiConnectivityManager.setAutoJoinEnabledExternal(false, false);

        // Enable trusted connection. This should NOT trigger a pno scan for auto-join.
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        verify(mWifiScanner, never()).startPnoScan(any(), any(), any());

        // End of processing a specific request. This should NOT trigger a new pno scan for
        // auto-join.
        mWifiConnectivityManager.setSpecificNetworkRequestInProgress(false);
        verify(mWifiScanner, never()).startPnoScan(any(), any(), any());

        // Enable untrusted connection. This should NOT trigger a pno scan for auto-join.
        mWifiConnectivityManager.setUntrustedConnectionAllowed(true);
        verify(mWifiScanner, never()).startPnoScan(any(), any(), any());
    }

    /**
     * Verify the various auto join enable/disable sequences when auto join is enabled externally.
     *
     * Expected behavior: Autojoin is turned on as a long as there is
     *  - Auto join is enabled externally
     *    And
     *  - No specific network request being processed.
     *    And
     *    - Pending generic Network request for trusted wifi connection.
     *      OR
     *    - Pending generic Network request for untrused wifi connection.
     */
    @Test
    public void verifyEnableAndDisableAutoJoin() {
        mWifiConnectivityManager = createConnectivityManager();

        // set wifi on & disconnected to trigger pno scans when auto-join is enabled.
        setWifiEnabled(true);
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // Enable trusted connection. This should trigger a pno scan for auto-join.
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        verify(mWifiScanner).startPnoScan(any(), any(), any());

        // Start of processing a specific request. This should stop any pno scan for auto-join.
        mWifiConnectivityManager.setSpecificNetworkRequestInProgress(true);
        verify(mWifiScanner).stopPnoScan(any());

        // End of processing a specific request. This should now trigger a new pno scan for
        // auto-join.
        mWifiConnectivityManager.setSpecificNetworkRequestInProgress(false);
        verify(mWifiScanner, times(2)).startPnoScan(any(), any(), any());

        // Disable trusted connection. This should stop any pno scan for auto-join.
        mWifiConnectivityManager.setTrustedConnectionAllowed(false);
        verify(mWifiScanner, times(2)).stopPnoScan(any());

        // Enable untrusted connection. This should trigger a pno scan for auto-join.
        mWifiConnectivityManager.setUntrustedConnectionAllowed(true);
        verify(mWifiScanner, times(3)).startPnoScan(any(), any(), any());
    }

    @Test
    public void verifySetPnoScanEnabledByFramework() {
        mWifiConnectivityManager = createConnectivityManager();

        // set wifi on & disconnected to trigger pno scans when PNO scan is enabled.
        setWifiEnabled(true);
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // Enable trusted connection. This should trigger a pno scan for auto-join.
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        verify(mWifiScanner).startPnoScan(any(), any(), any());

        // Verify disabling PNO scan stops the on-going PNO scan
        mWifiConnectivityManager.setPnoScanEnabledByFramework(false, false);
        verify(mWifiScanner).stopPnoScan(any());

        // Verify that PNO scan is no longer triggered
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        verify(mWifiScanner).startPnoScan(any(), any(), any());

        // Verify that PNO scan is not triggered after wifi toggle, since it's not configured to do
        // so.
        setWifiEnabled(false);
        setWifiEnabled(true);
        verify(mWifiScanner).startPnoScan(any(), any(), any());

        // Verify that PNO scan is triggered again after being enabled explicitly
        mWifiConnectivityManager.setPnoScanEnabledByFramework(true, false);
        verify(mWifiScanner, times(2)).startPnoScan(any(), any(), any());
    }

    @Test
    public void verifySetPnoScanEnabledAfterWifiToggle() {
        mWifiConnectivityManager = createConnectivityManager();

        // set wifi on & disconnected to trigger pno scans when PNO scan is enabled.
        setWifiEnabled(true);
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // Enable trusted connection. This should trigger a pno scan for auto-join.
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        verify(mWifiScanner).startPnoScan(any(), any(), any());

        // Verify disabling PNO scan stops the on-going PNO scan
        mWifiConnectivityManager.setPnoScanEnabledByFramework(false, true);
        verify(mWifiScanner).stopPnoScan(any());

        // Verify that PNO scan is no longer triggered
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        verify(mWifiScanner).startPnoScan(any(), any(), any());

        // Verify that PNO scan is triggered again after wifi toggle
        setWifiEnabled(false);
        setWifiEnabled(true);
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        verify(mWifiScanner, times(2)).startPnoScan(any(), any(), any());
    }

    /**
     * Verify that the increased PNO interval is used when power save is on.
     */
    @Test
    public void testPnoIntervalPowerSaveEnabled() throws Exception {
        when(mDeviceConfigFacade.isWifiBatterySaverEnabled()).thenReturn(true);
        when(mPowerManagerService.isPowerSaveMode()).thenReturn(true);
        verifyPnoScanWithInterval(
                MOVING_PNO_SCAN_INTERVAL_MILLIS * POWER_SAVE_SCAN_INTERVAL_MULTIPLIER);
    }

    /**
     * Verify that the normal PNO interval is used when power save is off.
     */
    @Test
    public void testPnoIntervalPowerSaveDisabled() throws Exception {
        when(mDeviceConfigFacade.isWifiBatterySaverEnabled()).thenReturn(true);
        when(mPowerManagerService.isPowerSaveMode()).thenReturn(false);
        verifyPnoScanWithInterval(MOVING_PNO_SCAN_INTERVAL_MILLIS);
    }

    /**
     * Verify that the normal PNO interval is used when the power save feature is disabled.
     */
    @Test
    public void testPnoIntervalPowerSaveEnabled_FeatureDisabled() throws Exception {
        when(mDeviceConfigFacade.isWifiBatterySaverEnabled()).thenReturn(false);
        when(mPowerManagerService.isPowerSaveMode()).thenReturn(true);
        verifyPnoScanWithInterval(MOVING_PNO_SCAN_INTERVAL_MILLIS);
    }


    /**
     * Verify PNO scan is started with the given scan interval.
     */
    private void verifyPnoScanWithInterval(int interval) throws Exception {
        setWifiEnabled(true);
        // starts a PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        ArgumentCaptor<ScanSettings> scanSettingsCaptor = ArgumentCaptor.forClass(
                ScanSettings.class);
        ArgumentCaptor<PnoSettings> pnoSettingsCaptor = ArgumentCaptor.forClass(
                PnoSettings.class);
        InOrder inOrder = inOrder(mWifiScanner);

        inOrder.verify(mWifiScanner).startPnoScan(scanSettingsCaptor.capture(),
                pnoSettingsCaptor.capture(), any());
        assertEquals(interval, scanSettingsCaptor.getValue().periodInMs);
        assertEquals(EXPECTED_PNO_ITERATIONS, pnoSettingsCaptor.getValue().scanIterations);
        assertEquals(EXPECTED_PNO_MULTIPLIER, pnoSettingsCaptor.getValue().scanIntervalMultiplier);
    }

    /**
     * Change device mobility state in the middle of a PNO scan. PNO scan should stop, then restart
     * with the updated scan period.
     */
    @Test
    public void changeDeviceMobilityStateDuringScan() {
        setWifiEnabled(true);

        // starts a PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        ArgumentCaptor<ScanSettings> scanSettingsCaptor = ArgumentCaptor.forClass(
                ScanSettings.class);
        InOrder inOrder = inOrder(mWifiScanner);

        inOrder.verify(mWifiScanner).startPnoScan(scanSettingsCaptor.capture(), any(), any());
        assertEquals(scanSettingsCaptor.getValue().periodInMs, MOVING_PNO_SCAN_INTERVAL_MILLIS);

        // initial connectivity state uses moving PNO scan interval, now set it to stationary
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);

        inOrder.verify(mWifiScanner).stopPnoScan(any());
        inOrder.verify(mWifiScanner).startPnoScan(scanSettingsCaptor.capture(), any(), any());
        assertEquals(scanSettingsCaptor.getValue().periodInMs, STATIONARY_PNO_SCAN_INTERVAL_MILLIS);
        verify(mScoringParams, times(2)).getEntryRssi(ScanResult.BAND_6_GHZ_START_FREQ_MHZ);
        verify(mScoringParams, times(2)).getEntryRssi(ScanResult.BAND_5_GHZ_START_FREQ_MHZ);
        verify(mScoringParams, times(2)).getEntryRssi(ScanResult.BAND_24_GHZ_START_FREQ_MHZ);
    }

    /**
     * Change device mobility state in the middle of a PNO scan, but it is changed to another
     * mobility state with the same scan period. Original PNO scan should continue.
     */
    @Test
    public void changeDeviceMobilityStateDuringScanWithSameScanPeriod() {
        setWifiEnabled(true);

        // starts a PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        ArgumentCaptor<ScanSettings> scanSettingsCaptor = ArgumentCaptor.forClass(
                ScanSettings.class);
        InOrder inOrder = inOrder(mWifiScanner);
        inOrder.verify(mWifiScanner, never()).stopPnoScan(any());
        inOrder.verify(mWifiScanner).startPnoScan(scanSettingsCaptor.capture(), any(), any());
        assertEquals(scanSettingsCaptor.getValue().periodInMs, MOVING_PNO_SCAN_INTERVAL_MILLIS);

        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_LOW_MVMT);

        inOrder.verifyNoMoreInteractions();
    }

    /**
     * Device is already connected, setting device mobility state should do nothing since no PNO
     * scans are running. Then, when PNO scan is started afterwards, should use the new scan period.
     */
    @Test
    public void setDeviceMobilityStateBeforePnoScan() {
        // ensure no PNO scan running
        setWifiEnabled(true);
        setWifiStateConnected();

        // initial connectivity state uses moving PNO scan interval, now set it to stationary
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);

        // no scans should start or stop because no PNO scan is running
        verify(mWifiScanner, never()).startPnoScan(any(), any(), any());
        verify(mWifiScanner, never()).stopPnoScan(any());

        // starts a PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        ArgumentCaptor<ScanSettings> scanSettingsCaptor = ArgumentCaptor.forClass(
                ScanSettings.class);

        verify(mWifiScanner).startPnoScan(scanSettingsCaptor.capture(), any(), any());
        // check that now the PNO scan uses the stationary interval, even though it was set before
        // the PNO scan started
        assertEquals(scanSettingsCaptor.getValue().periodInMs, STATIONARY_PNO_SCAN_INTERVAL_MILLIS);
    }

    /**
     * Tests the metrics collection of PNO scans through changes to device mobility state and
     * starting and stopping of PNO scans.
     */
    @Test
    public void deviceMobilityStateMetricsChangeStateAndStopStart() {
        InOrder inOrder = inOrder(mWifiMetrics);

        mWifiConnectivityManager = createConnectivityManager();
        setWifiEnabled(true);

        // change mobility state while no PNO scans running
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_LOW_MVMT);
        inOrder.verify(mWifiMetrics).enterDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_LOW_MVMT);

        // starts a PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.PNO_SCAN_STARTED), anyBoolean()));

        // change to High Movement, which has the same scan interval as Low Movement
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);
        inOrder.verify(mWifiMetrics).logPnoScanStop();
        inOrder.verify(mWifiMetrics).enterDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);
        inOrder.verify(mWifiMetrics).logPnoScanStart();

        // change to Stationary, which has a different scan interval from High Movement
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);
        inOrder.verify(mWifiMetrics).logPnoScanStop();
        inOrder.verify(mWifiMetrics).enterDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);
        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.PNO_SCAN_STARTED), anyBoolean()), times(2));

        // stops PNO scan
        mWifiConnectivityManager.setTrustedConnectionAllowed(false);
        inOrder.verify(mWifiMetrics).logPnoScanStop();

        // change mobility state while no PNO scans running
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);
        inOrder.verify(mWifiMetrics).enterDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);

        inOrder.verifyNoMoreInteractions();
    }

    /**
     *  Verify that WifiChannelUtilization is updated
     */
    @Test
    public void verifyWifiChannelUtilizationRefreshedAfterScanResults() {
        WifiLinkLayerStats llstats = new WifiLinkLayerStats();
        when(mPrimaryClientModeManager.getWifiLinkLayerStats()).thenReturn(llstats);

        // Force a connectivity scan
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        mLooper.dispatchAll();
        verify(mWifiChannelUtilization).refreshChannelStatsAndChannelUtilization(
                llstats, WifiChannelUtilization.UNKNOWN_FREQ);
    }

    /**
     *  Verify that WifiChannelUtilization is initialized properly
     */
    @Test
    public void verifyWifiChannelUtilizationInitAfterWifiToggle() {
        verify(mWifiChannelUtilization, times(1)).init(null);
        WifiLinkLayerStats llstats = new WifiLinkLayerStats();
        when(mPrimaryClientModeManager.getWifiLinkLayerStats()).thenReturn(llstats);

        setWifiEnabled(false);
        setWifiEnabled(true);
        verify(mWifiChannelUtilization, times(1)).init(llstats);
    }

    /**
     *  Verify that WifiChannelUtilization sets mobility state correctly
     */
    @Test
    public void verifyWifiChannelUtilizationSetMobilityState() {
        WifiLinkLayerStats llstats = new WifiLinkLayerStats();
        when(mPrimaryClientModeManager.getWifiLinkLayerStats()).thenReturn(llstats);

        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);
        verify(mWifiChannelUtilization).setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);
        verify(mWifiChannelUtilization).setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);
    }

    /**
     *  Verify that WifiChannelUtilization is updated
     */
    @Test
    public void verifyForceConnectivityScan() {
        // Auto-join enabled
        mWifiConnectivityManager.setAutoJoinEnabledExternal(true, false);
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        verify(mWifiScanner).startScan(any(), any());

        // Auto-join disabled, no new scans
        mWifiConnectivityManager.setAutoJoinEnabledExternal(false, false);
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        verify(mWifiScanner, times(1)).startScan(any(), any());

        // Wifi disabled, no new scans
        setWifiEnabled(false);
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        verify(mWifiScanner, times(1)).startScan(any(), any());
    }

    @Test
    public void testSetAndClearExternalPnoScanRequest() {
        int testUid = 123;
        String testPackage = "TestPackage";
        IBinder binder = mock(IBinder.class);
        IPnoScanResultsCallback callback = mock(IPnoScanResultsCallback.class);
        List<WifiSsid> requestedSsids = Arrays.asList(
                WifiSsid.fromString("\"TEST_SSID_1\""),
                WifiSsid.fromString("\"TEST_SSID_2\""));
        int[] frequencies = new int[] {TEST_FREQUENCY};
        mWifiConnectivityManager.setExternalPnoScanRequest(testUid, testPackage, binder, callback,
                requestedSsids, frequencies);
        verify(mExternalPnoScanRequestManager).setRequest(testUid, testPackage, binder, callback,
                requestedSsids, frequencies);
        mWifiConnectivityManager.clearExternalPnoScanRequest(testUid);
        verify(mExternalPnoScanRequestManager).removeRequest(testUid);
    }

    /**
     * When location is disabled external PNO SSIDs should not get scanned.
     */
    @Test
    public void testExternalPnoScanRequest_gatedBylocationMode() {
        when(mWifiScoreCard.lookupNetwork(any())).thenReturn(mock(WifiScoreCard.PerNetwork.class));
        mResources.setBoolean(R.bool.config_wifiPnoFrequencyCullingEnabled, true);
        mWifiConnectivityManager.setLocationModeEnabled(false);
        // mock saved networks list to be empty
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(Collections.EMPTY_LIST);


        // Mock a couple external requested PNO SSIDs
        Set<String> requestedSsids = new ArraySet<>();
        requestedSsids.add("\"Test_SSID_1\"");
        requestedSsids.add("\"Test_SSID_2\"");
        when(mExternalPnoScanRequestManager.getExternalPnoScanSsids()).thenReturn(requestedSsids);
        Set<Integer> frequencies = new ArraySet<>();
        frequencies.add(TEST_FREQUENCY);
        when(mExternalPnoScanRequestManager.getExternalPnoScanFrequencies())
                .thenReturn(frequencies);

        assertEquals(Collections.EMPTY_LIST, mWifiConnectivityManager.retrievePnoNetworkList());

        // turn location mode on and now PNO scan should include the requested SSIDs
        mWifiConnectivityManager.setLocationModeEnabled(true);
        List<WifiScanner.PnoSettings.PnoNetwork> pnoNetworks =
                mWifiConnectivityManager.retrievePnoNetworkList();
        assertEquals(2, pnoNetworks.size());
        assertEquals("\"Test_SSID_1\"", pnoNetworks.get(0).ssid);
        assertEquals("\"Test_SSID_2\"", pnoNetworks.get(1).ssid);
        assertArrayEquals(new int[] {TEST_FREQUENCY}, pnoNetworks.get(0).frequencies);
        assertArrayEquals(new int[] {TEST_FREQUENCY}, pnoNetworks.get(1).frequencies);
    }

    /**
     * Test external requested PNO SSIDs get handled properly when there are existing saved networks
     * with same SSID.
     */
    @Test
    public void testExternalPnoScanRequest_withSavedNetworks() {
        mWifiConnectivityManager.setLocationModeEnabled(true);
        // Create and add 3 networks.
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPasspointNetwork();
        network1.ephemeral = true;
        network1.getNetworkSelectionStatus().setHasEverConnected(false);
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        network2.getNetworkSelectionStatus().setHasEverConnected(true);
        WifiConfiguration network3 = WifiConfigurationTestUtil.createPskNetwork();
        List<WifiConfiguration> networkList = new ArrayList<>();
        networkList.add(network1);
        networkList.add(network2);
        networkList.add(network3);
        mLruConnectionTracker.addNetwork(network3);
        mLruConnectionTracker.addNetwork(network2);
        mLruConnectionTracker.addNetwork(network1);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(networkList);

        // Mock a couple external requested PNO SSIDs. network3.SSID is in both saved networks
        // and external requested networks.
        Set<String> requestedSsids = new ArraySet<>();
        requestedSsids.add("\"Test_SSID_1\"");
        requestedSsids.add(network2.SSID);
        when(mExternalPnoScanRequestManager.getExternalPnoScanSsids()).thenReturn(requestedSsids);

        List<WifiScanner.PnoSettings.PnoNetwork> pnoNetworks =
                mWifiConnectivityManager.retrievePnoNetworkList();
        // There should be 4 SSIDs in total: network1, an extra original (untranslated) SSID of
        // network1, network2, and Test_SSID_1.
        // network1 should be included in PNO even if it's never connected because it's ephemeral.
        // network3 should not get included because it's saved and never connected before.
        assertEquals(4, pnoNetworks.size());
        // Verify the order. Test_SSID_1 and network2 should be in the front because they are
        // requested by an external app. Verify network2.SSID only appears once.
        assertEquals("\"Test_SSID_1\"", pnoNetworks.get(0).ssid);
        assertEquals(network2.SSID, pnoNetworks.get(1).ssid);
        assertEquals(network1.SSID, pnoNetworks.get(2).ssid);
        assertEquals(UNTRANSLATED_HEX_SSID, pnoNetworks.get(3).ssid); // Possible untranslated SSID
    }

    @Test
    public void testExternalPnoScanRequest_reportResults() {
        setWifiEnabled(true);
        mWifiConnectivityManager.setLocationModeEnabled(true);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        // starts a PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        InOrder inOrder = inOrder(mWifiScanner, mExternalPnoScanRequestManager);

        inOrder.verify(mWifiScanner).startPnoScan(any(), any(), any());
        inOrder.verify(mExternalPnoScanRequestManager).onScanResultsAvailable(any());

        // mock connectivity scan
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        mLooper.dispatchAll();
        // verify mExternalPnoScanRequestManager is notified again
        inOrder.verify(mExternalPnoScanRequestManager).onScanResultsAvailable(any());
    }

    /**
     * Verify no network is network selection disabled, auto-join disabled using.
     * {@link WifiConnectivityManager#retrievePnoNetworkList()}.
     */
    @Test
    public void testRetrievePnoList() {
        // Create and add 3 networks.
        WifiConfiguration network1 = WifiConfigurationTestUtil.createEapNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network3 = WifiConfigurationTestUtil.createOpenHiddenNetwork();
        WifiConfiguration network4 = WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
        network4.carrierId = 123; // Assign a valid carrier ID
        network1.getNetworkSelectionStatus().setHasEverConnected(true);
        network2.getNetworkSelectionStatus().setHasEverConnected(true);
        network3.getNetworkSelectionStatus().setHasEverConnected(true);
        network4.getNetworkSelectionStatus().setHasEverConnected(true);
        when(mWifiCarrierInfoManager.isSimReady(anyInt())).thenReturn(true);

        List<WifiConfiguration> networkList = new ArrayList<>();
        networkList.add(network1);
        networkList.add(network2);
        networkList.add(network3);
        networkList.add(network4);
        mLruConnectionTracker.addNetwork(network4);
        mLruConnectionTracker.addNetwork(network3);
        mLruConnectionTracker.addNetwork(network2);
        mLruConnectionTracker.addNetwork(network1);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(networkList);
        // Retrieve the Pno network list & verify.
        List<WifiScanner.PnoSettings.PnoNetwork> pnoNetworks =
                mWifiConnectivityManager.retrievePnoNetworkList();
        verify(mWifiNetworkSuggestionsManager).getAllScanOptimizationSuggestionNetworks();
        assertEquals(5, pnoNetworks.size());
        assertEquals(network1.SSID, pnoNetworks.get(0).ssid);
        assertEquals(UNTRANSLATED_HEX_SSID, pnoNetworks.get(1).ssid); // Possible untranslated SSID
        assertEquals(network2.SSID, pnoNetworks.get(2).ssid);
        assertEquals(network3.SSID, pnoNetworks.get(3).ssid);
        assertEquals(network4.SSID, pnoNetworks.get(4).ssid);

        // Now permanently disable |network3|. This should remove network 3 from the list.
        network3.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED);
        // Mock the SIM card to be not ready. This should remove network 4 from the list.
        when(mWifiCarrierInfoManager.isSimReady(anyInt())).thenReturn(false);

        // Retrieve the Pno network list & verify.
        pnoNetworks = mWifiConnectivityManager.retrievePnoNetworkList();
        assertEquals(3, pnoNetworks.size());
        assertEquals(network1.SSID, pnoNetworks.get(0).ssid);
        assertEquals(UNTRANSLATED_HEX_SSID, pnoNetworks.get(1).ssid); // Possible untranslated SSID
        assertEquals(network2.SSID, pnoNetworks.get(2).ssid);

        // Now set network1 autojoin disabled. This should remove network 1 from the list.
        network1.allowAutojoin = false;
        // Retrieve the Pno network list & verify.
        pnoNetworks = mWifiConnectivityManager.retrievePnoNetworkList();
        assertEquals(2, pnoNetworks.size());
        assertEquals(network2.SSID, pnoNetworks.get(0).ssid);
        assertEquals(UNTRANSLATED_HEX_SSID, pnoNetworks.get(1).ssid); // Possible untranslated SSID

        // Now set network2 to be temporarily disabled by the user. This should remove network 2
        // from the list.
        when(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(network2.SSID)).thenReturn(true);
        pnoNetworks = mWifiConnectivityManager.retrievePnoNetworkList();
        assertEquals(0, pnoNetworks.size());
    }

    /**
     * Verifies frequencies are populated correctly for pno networks.
     * {@link WifiConnectivityManager#retrievePnoNetworkList()}.
     */
    @Test
    public void testRetrievePnoListFrequencies() {
        // Create 2 networks.
        WifiConfiguration network1 = WifiConfigurationTestUtil.createEapNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        network1.getNetworkSelectionStatus().setHasEverConnected(true);
        network2.getNetworkSelectionStatus().setHasEverConnected(true);
        List<WifiConfiguration> networkList = new ArrayList<>();
        networkList.add(network1);
        networkList.add(network2);
        mLruConnectionTracker.addNetwork(network2);
        mLruConnectionTracker.addNetwork(network1);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(networkList);
        // Retrieve the Pno network list and verify.
        // Frequencies should be empty since no scan results have been received yet.
        List<WifiScanner.PnoSettings.PnoNetwork> pnoNetworks =
                mWifiConnectivityManager.retrievePnoNetworkList();
        assertEquals(3, pnoNetworks.size());
        assertEquals(network1.SSID, pnoNetworks.get(0).ssid);
        assertEquals(UNTRANSLATED_HEX_SSID, pnoNetworks.get(1).ssid); // Possible untranslated SSID
        assertEquals(network2.SSID, pnoNetworks.get(2).ssid);
        assertEquals("frequencies should be empty", 0, pnoNetworks.get(0).frequencies.length);
        assertEquals("frequencies should be empty", 0, pnoNetworks.get(1).frequencies.length);
        assertEquals("frequencies should be empty", 0, pnoNetworks.get(2).frequencies.length);

        //Set up wifiScoreCard to get frequency.
        List<Integer> channelList = Arrays
                .asList(TEST_FREQUENCY_1, TEST_FREQUENCY_2, TEST_FREQUENCY_3);
        when(mWifiScoreCard.lookupNetwork(network1.SSID)).thenReturn(mPerNetwork);
        when(mWifiScoreCard.lookupNetwork(network2.SSID)).thenReturn(mPerNetwork1);
        when(mPerNetwork.getFrequencies(anyLong())).thenReturn(channelList);
        when(mPerNetwork1.getFrequencies(anyLong())).thenReturn(new ArrayList<>());

        //Set config_wifiPnoFrequencyCullingEnabled false, should ignore get frequency.
        mResources.setBoolean(R.bool.config_wifiPnoFrequencyCullingEnabled, false);
        pnoNetworks = mWifiConnectivityManager.retrievePnoNetworkList();
        assertEquals(3, pnoNetworks.size());
        assertEquals(network1.SSID, pnoNetworks.get(0).ssid);
        assertEquals(UNTRANSLATED_HEX_SSID, pnoNetworks.get(1).ssid); // Possible untranslated SSID
        assertEquals(network2.SSID, pnoNetworks.get(2).ssid);
        assertEquals("frequencies should be empty", 0, pnoNetworks.get(0).frequencies.length);
        assertEquals("frequencies should be empty", 0, pnoNetworks.get(1).frequencies.length);
        assertEquals("frequencies should be empty", 0, pnoNetworks.get(2).frequencies.length);

        // Set config_wifiPnoFrequencyCullingEnabled false, should get the right frequency.
        mResources.setBoolean(R.bool.config_wifiPnoFrequencyCullingEnabled, true);
        pnoNetworks = mWifiConnectivityManager.retrievePnoNetworkList();
        assertEquals(3, pnoNetworks.size());
        assertEquals(network1.SSID, pnoNetworks.get(0).ssid);
        assertEquals(UNTRANSLATED_HEX_SSID, pnoNetworks.get(1).ssid); // Possible untranslated SSID
        assertEquals(network2.SSID, pnoNetworks.get(2).ssid);
        assertEquals(3, pnoNetworks.get(0).frequencies.length);
        Arrays.sort(pnoNetworks.get(0).frequencies);
        assertEquals(TEST_FREQUENCY_1, pnoNetworks.get(0).frequencies[0]);
        assertEquals(TEST_FREQUENCY_2, pnoNetworks.get(0).frequencies[1]);
        assertEquals(TEST_FREQUENCY_3, pnoNetworks.get(0).frequencies[2]);
        assertEquals("frequencies should be empty", 0, pnoNetworks.get(2).frequencies.length);
    }


    /**
     * Verifies the ordering of network list generated using
     * {@link WifiConnectivityManager#retrievePnoNetworkList()}.
     */
    @Test
    public void testRetrievePnoListOrder() {
        // Create 4 non-Passpoint and 3 Passpoint networks.
        WifiConfiguration network1 = WifiConfigurationTestUtil.createEapNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network3 = WifiConfigurationTestUtil.createOpenHiddenNetwork();
        WifiConfiguration network4 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration passpointNetwork1 = WifiConfigurationTestUtil.createPasspointNetwork();
        WifiConfiguration passpointNetwork2 = WifiConfigurationTestUtil.createPasspointNetwork();
        WifiConfiguration passpointNetwork3 = WifiConfigurationTestUtil.createPasspointNetwork();

        // Mark all non-Passpoint networks except network4 as connected before.
        network1.getNetworkSelectionStatus().setHasEverConnected(true);
        network2.getNetworkSelectionStatus().setHasEverConnected(true);
        network3.getNetworkSelectionStatus().setHasEverConnected(true);

        mLruConnectionTracker.addNetwork(network1);
        mLruConnectionTracker.addNetwork(network2);
        mLruConnectionTracker.addNetwork(network3);
        mLruConnectionTracker.addNetwork(network4);
        List<WifiConfiguration> networkList = new ArrayList<>();
        networkList.add(network1);
        networkList.add(network2);
        networkList.add(network3);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(networkList);

        List<WifiConfiguration> passpointNetworkList = new ArrayList<>();
        passpointNetworkList.add(passpointNetwork1);
        passpointNetworkList.add(passpointNetwork2);
        passpointNetworkList.add(passpointNetwork3);
        when(mDeviceConfigFacade.includePasspointSsidsInPnoScans()).thenReturn(true);
        when(mPasspointManager.getWifiConfigsForPasspointProfiles(anyBoolean()))
                .thenReturn(passpointNetworkList);
        List<WifiScanner.PnoSettings.PnoNetwork> pnoNetworks =
                mWifiConnectivityManager.retrievePnoNetworkList();

        // Verify correct order of networks. Note that network4 should not appear for PNO scan
        // since it had not been connected before.
        assertEquals(7, pnoNetworks.size());
        assertEquals(passpointNetwork1.SSID, pnoNetworks.get(0).ssid);
        assertEquals(UNTRANSLATED_HEX_SSID, pnoNetworks.get(1).ssid); // Possible untranslated SSID
        assertEquals(passpointNetwork2.SSID, pnoNetworks.get(2).ssid);
        assertEquals(network3.SSID, pnoNetworks.get(3).ssid);
        assertEquals(network2.SSID, pnoNetworks.get(4).ssid);
        assertEquals(network1.SSID, pnoNetworks.get(5).ssid);
        assertEquals(passpointNetwork3.SSID, pnoNetworks.get(6).ssid);
    }

    private List<List<Integer>> linkScoreCardFreqsToNetwork(WifiConfiguration... configs) {
        List<List<Integer>> results = new ArrayList<>();
        int i = 0;
        for (WifiConfiguration config : configs) {
            List<Integer> channelList = Arrays.asList(TEST_FREQUENCY_1 + i, TEST_FREQUENCY_2 + i,
                    TEST_FREQUENCY_3 + i);
            WifiScoreCard.PerNetwork perNetwork = mock(WifiScoreCard.PerNetwork.class);
            when(mWifiScoreCard.lookupNetwork(config.SSID)).thenReturn(perNetwork);
            when(perNetwork.getFrequencies(anyLong())).thenReturn(channelList);
            results.add(channelList);
            i++;
        }
        return results;
    }

    /**
     * Verify that the length of frequency set will not exceed the provided max value
     */
    @Test
    public void testFetchChannelSetForPartialScanMaxCount() {
        WifiConfiguration configuration1 = WifiConfigurationTestUtil.createOpenNetwork();
        WifiConfiguration configuration2 = WifiConfigurationTestUtil.createOpenNetwork();
        configuration1.getNetworkSelectionStatus().setHasEverConnected(true);
        configuration2.getNetworkSelectionStatus().setHasEverConnected(true);
        when(mWifiConfigManager.getSavedNetworks(anyInt()))
                .thenReturn(Arrays.asList(configuration1, configuration2));

        // linkScoreCardFreqsToNetwork creates 2 Lists of size 3 each.
        List<List<Integer>> freqs = linkScoreCardFreqsToNetwork(configuration1, configuration2);

        mLruConnectionTracker.addNetwork(configuration2);
        mLruConnectionTracker.addNetwork(configuration1);

        // Max count is 3 with no per-network max count - should match the first freq List.
        assertEquals(
                new HashSet<>(freqs.get(0)),
                mWifiConnectivityManager.fetchChannelSetForPartialScan(
                        3, 0, CHANNEL_CACHE_AGE_MINS));

        // Max count is 3 with per-network max count as 1 - should get the first freq from each
        // network.
        Set<Integer> channelSet =
                mWifiConnectivityManager.fetchChannelSetForPartialScan(
                        3, 1, CHANNEL_CACHE_AGE_MINS);
        assertEquals(2, channelSet.size());
        assertTrue(channelSet.contains(freqs.get(0).get(0)));
        assertTrue(channelSet.contains(freqs.get(1).get(0)));

        // Max count is 3 with per-network max count as 2 - should get the 2 freqs from
        // network 1, and 1 freq from network 2.
        channelSet =
                mWifiConnectivityManager.fetchChannelSetForPartialScan(
                        3, 2, CHANNEL_CACHE_AGE_MINS);
        assertEquals(3, channelSet.size());
        assertTrue(channelSet.contains(freqs.get(0).get(0)));
        assertTrue(channelSet.contains(freqs.get(0).get(1)));
        assertTrue(channelSet.contains(freqs.get(1).get(0)));
    }

    /**
     * Verifies the creation of channel list using
     * {@link WifiConnectivityManager#fetchChannelSetForNetworkForPartialScan(int)}.
     */
    @Test
    public void testFetchChannelSetForNetwork() {
        WifiConfiguration configuration = WifiConfigurationTestUtil.createOpenNetwork();
        configuration.networkId = TEST_CONNECTED_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_CONNECTED_NETWORK_ID))
                .thenReturn(configuration);
        List<List<Integer>> freqs = linkScoreCardFreqsToNetwork(configuration);

        assertEquals(new HashSet<>(freqs.get(0)), mWifiConnectivityManager
                .fetchChannelSetForNetworkForPartialScan(configuration.networkId));
    }

    /**
     * Verifies the creation of channel list using
     * {@link WifiConnectivityManager#fetchChannelSetForNetworkForPartialScan(int)} and
     * ensures that the frequenecy of the currently connected network is in the returned
     * channel set.
     */
    @Test
    public void testFetchChannelSetForNetworkIncludeCurrentNetwork() {
        WifiConfiguration configuration = WifiConfigurationTestUtil.createOpenNetwork();
        configuration.networkId = TEST_CONNECTED_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_CONNECTED_NETWORK_ID))
                .thenReturn(configuration);
        linkScoreCardFreqsToNetwork(configuration);

        mWifiInfo.setFrequency(TEST_CURRENT_CONNECTED_FREQUENCY);

        // Currently connected network frequency 2427 is not in the TEST_FREQ_LIST
        Set<Integer> freqs = mWifiConnectivityManager.fetchChannelSetForNetworkForPartialScan(
                configuration.networkId);

        assertTrue(freqs.contains(2427));
    }

    /**
     * Verifies the creation of channel list using
     * {@link WifiConnectivityManager#fetchChannelSetForNetworkForPartialScan(int)} and
     * ensures that the list size does not exceed the max configured for the device.
     */
    @Test
    public void testFetchChannelSetForNetworkIsLimitedToConfiguredSize() {
        // Need to recreate the WifiConfigManager instance for this test to modify the config
        // value which is read only in the constructor.
        int maxListSize = 2;
        mResources.setInteger(
                R.integer.config_wifi_framework_associated_partial_scan_max_num_active_channels,
                maxListSize);

        WifiConfiguration configuration = WifiConfigurationTestUtil.createOpenNetwork();
        configuration.networkId = TEST_CONNECTED_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_CONNECTED_NETWORK_ID))
                .thenReturn(configuration);
        List<List<Integer>> freqs = linkScoreCardFreqsToNetwork(configuration);
        // Ensure that the fetched list size is limited.
        Set<Integer> results = mWifiConnectivityManager.fetchChannelSetForNetworkForPartialScan(
                configuration.networkId);
        assertEquals(maxListSize, results.size());
        assertFalse(results.contains(freqs.get(0).get(2)));
    }

    @Test
    public void restartPnoScanForNetworkChanges() {
        setWifiEnabled(true);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        // starts a PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        InOrder inOrder = inOrder(mWifiScanner);

        inOrder.verify(mWifiScanner).startPnoScan(any(), any(), any());

        // Add or update suggestions.
        mSuggestionUpdateListenerCaptor.getValue().onSuggestionsAddedOrUpdated(
                Arrays.asList(mWifiNetworkSuggestion));
        // Add saved network
        mNetworkUpdateListenerCaptor.getValue().onNetworkAdded(new WifiConfiguration());
        // Ensure that we don't immediately restarted PNO.
        inOrder.verify(mWifiScanner, never()).stopPnoScan(any());
        inOrder.verify(mWifiScanner, never()).startPnoScan(any(), any(), any());

        // Verify there is only 1 delayed scan scheduled
        assertEquals(1, mTestHandler.getIntervals().size());
        final long delta = Math.abs(NETWORK_CHANGE_TRIGGER_PNO_THROTTLE_MS
                - mTestHandler.getIntervals().get(0));
        assertTrue("Interval " + " (" + delta + ") not in 1ms error margin",
                delta < 2);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(mTestHandler.getIntervals().get(0));
        // Now advance the test handler and fire the periodic scan timer
        mTestHandler.timeAdvance();

        // Ensure that we restarted PNO.
        inOrder.verify(mWifiScanner).stopPnoScan(any());
        inOrder.verify(mWifiScanner).startPnoScan(any(), any(), any());
    }

    @Test
    public void includeSecondaryStaWhenPresentInGetCandidatesFromScan() {
        // Set screen to on
        setScreenState(true);

        ConcreteClientModeManager primaryCmm = mock(ConcreteClientModeManager.class);
        WifiInfo wifiInfo1 = mock(WifiInfo.class);
        when(primaryCmm.getInterfaceName()).thenReturn("wlan0");
        when(primaryCmm.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(primaryCmm.isConnected()).thenReturn(false);
        when(primaryCmm.isDisconnected()).thenReturn(true);
        when(primaryCmm.getConnectionInfo()).thenReturn(wifiInfo1);

        ConcreteClientModeManager secondaryCmm = mock(ConcreteClientModeManager.class);
        WifiInfo wifiInfo2 = mock(WifiInfo.class);
        when(secondaryCmm.getInterfaceName()).thenReturn("wlan1");
        when(secondaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        when(secondaryCmm.isConnected()).thenReturn(false);
        when(secondaryCmm.isDisconnected()).thenReturn(true);
        when(secondaryCmm.getConnectionInfo()).thenReturn(wifiInfo2);

        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(primaryCmm, secondaryCmm));

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                primaryCmm,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        List<WifiNetworkSelector.ClientModeManagerState> expectedCmmStates =
                Arrays.asList(new WifiNetworkSelector.ClientModeManagerState(
                                "wlan0", false, true, wifiInfo1, false),
                        new WifiNetworkSelector.ClientModeManagerState(
                                "wlan1", false, true, wifiInfo2, false));
        verify(mWifiNS).getCandidatesFromScan(any(), any(),
                eq(expectedCmmStates), anyBoolean(), anyBoolean(), anyBoolean(), any(),
                anyBoolean());
    }

    @Test
    public void includeSecondaryStaWhenNotPresentButAvailableInGetCandidatesFromScan() {
        // Set screen to on
        setScreenState(true);
        // set OEM paid connection allowed.
        WorkSource oemPaidWs = new WorkSource();
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, oemPaidWs);

        ConcreteClientModeManager primaryCmm = mock(ConcreteClientModeManager.class);
        WifiInfo wifiInfo1 = mock(WifiInfo.class);
        when(primaryCmm.getInterfaceName()).thenReturn("wlan0");
        when(primaryCmm.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(primaryCmm.isConnected()).thenReturn(false);
        when(primaryCmm.isDisconnected()).thenReturn(true);
        when(primaryCmm.getConnectionInfo()).thenReturn(wifiInfo1);

        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(primaryCmm));
        // Second STA creation is allowed.
        when(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                eq(oemPaidWs), eq(ROLE_CLIENT_SECONDARY_LONG_LIVED), eq(false))).thenReturn(true);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                primaryCmm,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        List<WifiNetworkSelector.ClientModeManagerState> expectedCmmStates =
                Arrays.asList(new WifiNetworkSelector.ClientModeManagerState(
                        "wlan0", false, true, wifiInfo1, false),
                new WifiNetworkSelector.ClientModeManagerState(
                        "unknown", false, true, new WifiInfo(), false));
        verify(mWifiNS).getCandidatesFromScan(any(), any(),
                eq(expectedCmmStates), anyBoolean(), anyBoolean(), anyBoolean(), any(),
                anyBoolean());
    }

    /**
     * Verify that scan results are ignored when DPP is in progress and
     * Connected MAC Randomization enabled.
     */
    @Test
    public void testIgnoreScanResultWhenDppInProgress() {
        // Enable MAC randomization and set DPP session in progress
        when(mWifiGlobals.isConnectedMacRandomizationEnabled()).thenReturn(true);
        when(mDppManager.isSessionInProgress()).thenReturn(true);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();

        // Verify there is no connection due to scan result being ignored
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Set DPP session to no longer in progress
        when(mDppManager.isSessionInProgress()).thenReturn(false);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();

        // Verify a candidate is found this time.
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    private void setWifiEnabled(boolean enable) {
        ActiveModeWarden.ModeChangeCallback modeChangeCallback =
                mModeChangeCallbackCaptor.getValue();
        assertNotNull(modeChangeCallback);
        if (enable) {
            when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                    .thenReturn(Arrays.asList(mPrimaryClientModeManager));
            modeChangeCallback.onActiveModeManagerAdded(mPrimaryClientModeManager);
        } else {
            when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                    .thenReturn(Arrays.asList());
            modeChangeCallback.onActiveModeManagerRemoved(mPrimaryClientModeManager);
        }
    }

    private void setScreenState(boolean screenOn) {
        InOrder inOrder = inOrder(mWifiNS);
        WifiDeviceStateChangeManager.StateChangeCallback callback =
                mStateChangeCallbackArgumentCaptor.getValue();
        assertNotNull(callback);
        callback.onScreenStateChanged(screenOn);
        inOrder.verify(mWifiNS).setScreenState(screenOn);
    }
}
