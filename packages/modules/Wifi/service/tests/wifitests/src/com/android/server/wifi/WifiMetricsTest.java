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
 * limitations under the License
 */
package com.android.server.wifi;

import static android.net.wifi.WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT;
import static android.net.wifi.WifiManager.DEVICE_MOBILITY_STATE_LOW_MVMT;
import static android.net.wifi.WifiManager.DEVICE_MOBILITY_STATE_STATIONARY;
import static android.net.wifi.WifiManager.DEVICE_MOBILITY_STATE_UNKNOWN;

import static com.android.server.wifi.WifiMetrics.convertBandwidthEnumToUsabilityStatsType;
import static com.android.server.wifi.WifiMetrics.convertPreambleTypeEnumToUsabilityStatsType;
import static com.android.server.wifi.WifiMetrics.convertSpatialStreamEnumToUsabilityStatsType;
import static com.android.server.wifi.WifiMetricsTestUtil.assertDeviceMobilityStatePnoScanStatsEqual;
import static com.android.server.wifi.WifiMetricsTestUtil.assertExperimentProbeCountsEqual;
import static com.android.server.wifi.WifiMetricsTestUtil.assertHistogramBucketsEqual;
import static com.android.server.wifi.WifiMetricsTestUtil.assertKeyCountsEqual;
import static com.android.server.wifi.WifiMetricsTestUtil.assertLinkProbeFailureReasonCountsEqual;
import static com.android.server.wifi.WifiMetricsTestUtil.assertLinkProbeStaEventsEqual;
import static com.android.server.wifi.WifiMetricsTestUtil.buildDeviceMobilityStatePnoScanStats;
import static com.android.server.wifi.WifiMetricsTestUtil.buildExperimentProbeCounts;
import static com.android.server.wifi.WifiMetricsTestUtil.buildHistogramBucketInt32;
import static com.android.server.wifi.WifiMetricsTestUtil.buildInt32Count;
import static com.android.server.wifi.WifiMetricsTestUtil.buildLinkProbeFailureReasonCount;
import static com.android.server.wifi.WifiMetricsTestUtil.buildLinkProbeFailureStaEvent;
import static com.android.server.wifi.WifiMetricsTestUtil.buildLinkProbeSuccessStaEvent;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_CONFIG_SAVED;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_IS_UNUSABLE_REPORTED;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_IS_UNUSABLE_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_UNKNOWN;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_IS_UNUSABLE_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_USABLE;
import static com.android.server.wifi.proto.nano.WifiMetricsProto.StaEvent.TYPE_LINK_PROBE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.lang.StrictMath.toIntExact;

import android.app.ActivityManager;
import android.content.Context;
import android.net.MacAddress;
import android.net.wifi.EAPConstants;
import android.net.wifi.IOnWifiUsabilityStatsListener;
import android.net.wifi.MloLink;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.NetworkSelectionStatus;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.ProvisioningCallback;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.wifi.WifiLinkLayerStats.PeerInfo;
import com.android.server.wifi.WifiLinkLayerStats.RadioStat;
import com.android.server.wifi.WifiLinkLayerStats.RateStat;
import com.android.server.wifi.aware.WifiAwareMetrics;
import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.hotspot2.PasspointManager;
import com.android.server.wifi.hotspot2.PasspointMatch;
import com.android.server.wifi.hotspot2.PasspointProvider;
import com.android.server.wifi.p2p.WifiP2pMetrics;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.proto.nano.WifiMetricsProto;
import com.android.server.wifi.proto.nano.WifiMetricsProto.ConnectToNetworkNotificationAndActionCount;
import com.android.server.wifi.proto.nano.WifiMetricsProto.DeviceMobilityStatePnoScanStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.HealthMonitorFailureStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.HealthMonitorMetrics;
import com.android.server.wifi.proto.nano.WifiMetricsProto.HistogramBucketInt32;
import com.android.server.wifi.proto.nano.WifiMetricsProto.Int32Count;
import com.android.server.wifi.proto.nano.WifiMetricsProto.LinkProbeStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.LinkProbeStats.ExperimentProbeCounts;
import com.android.server.wifi.proto.nano.WifiMetricsProto.LinkProbeStats.LinkProbeFailureReasonCount;
import com.android.server.wifi.proto.nano.WifiMetricsProto.NetworkDisableReason;
import com.android.server.wifi.proto.nano.WifiMetricsProto.NetworkSelectionExperimentDecisions;
import com.android.server.wifi.proto.nano.WifiMetricsProto.PasspointProfileTypeCount;
import com.android.server.wifi.proto.nano.WifiMetricsProto.PasspointProvisionStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.PnoScanMetrics;
import com.android.server.wifi.proto.nano.WifiMetricsProto.RadioStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.RateStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.SoftApConnectedClientsEvent;
import com.android.server.wifi.proto.nano.WifiMetricsProto.StaEvent;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiIsUnusableEvent;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiRadioUsage;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiUsabilityStats;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiUsabilityStatsEntry;
import com.android.server.wifi.rtt.RttMetrics;
import com.android.server.wifi.util.InformationElementUtil;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unit tests for {@link com.android.server.wifi.WifiMetrics}.
 */
@SmallTest
public class WifiMetricsTest extends WifiBaseTest {

    WifiMetrics mWifiMetrics;
    WifiMetricsProto.WifiLog mDecodedProto;
    TestLooper mTestLooper;
    Random mRandom = new Random();
    private static final int TEST_NETWORK_ID = 42;
    public static final String TEST_IFACE_NAME = "wlan0";
    public static final String TEST_IFACE_NAME2 = "wlan1";
    private static final int TEST_UID = 52;
    private static final String TEST_TAG = "TestTag";
    private static final int TEST_CONNECTION_FAILURE_STATUS_CODE = -1;
    private static final String MLO_LINK_STA_MAC_ADDRESS = "12:34:56:78:9a:bc";
    private static final String MLO_LINK_AP_MAC_ADDRESS = "bc:9a:78:56:34:12";
    private static final int TEST_CHANNEL = 36;

    private MockitoSession mSession;
    @Mock Context mContext;
    MockResources mResources;
    @Mock FrameworkFacade mFacade;
    @Mock Clock mClock;
    @Mock ScoringParams mScoringParams;
    @Mock WifiConfigManager mWcm;
    @Mock WifiBlocklistMonitor mWifiBlocklistMonitor;
    @Mock PasspointManager mPpm;
    @Mock WifiNetworkSelector mWns;
    @Mock WifiPowerMetrics mWifiPowerMetrics;
    @Mock WifiDataStall mWifiDataStall;
    @Mock WifiChannelUtilization mWifiChannelUtilization;
    @Mock WifiSettingsStore mWifiSettingsStore;
    @Mock WifiHealthMonitor mWifiHealthMonitor;
    @Mock IBinder mAppBinder;
    @Mock IOnWifiUsabilityStatsListener mOnWifiUsabilityStatsListener;
    @Mock WifiP2pMetrics mWifiP2pMetrics;
    @Mock DppMetrics mDppMetrics;
    @Mock WifiScoreCard mWifiScoreCard;
    @Mock WifiScoreCard.PerNetwork mPerNetwork;
    @Mock WifiScoreCard.NetworkConnectionStats mNetworkConnectionStats;
    @Mock PowerManager mPowerManager;
    @Mock WifiMonitor mWifiMonitor;
    @Mock ActiveModeWarden mActiveModeWarden;
    @Mock WifiDeviceStateChangeManager mWifiDeviceStateChangeManager;
    @Captor ArgumentCaptor<ActiveModeWarden.ModeChangeCallback> mModeChangeCallbackArgumentCaptor;
    @Captor ArgumentCaptor<Handler> mHandlerCaptor;
    @Captor
    ArgumentCaptor<WifiDeviceStateChangeManager.StateChangeCallback>
            mStateChangeCallbackArgumentCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDecodedProto = null;
        when(mClock.getElapsedSinceBootMillis()).thenReturn((long) 0);
        mTestLooper = new TestLooper();
        mResources = new MockResources();
        when(mContext.getResources()).thenReturn(mResources);
        mWifiMetrics =
                new WifiMetrics(
                        mContext,
                        mFacade,
                        mClock,
                        mTestLooper.getLooper(),
                        new WifiAwareMetrics(mClock),
                        new RttMetrics(mClock),
                        mWifiPowerMetrics,
                        mWifiP2pMetrics,
                        mDppMetrics,
                        mWifiMonitor,
                        mWifiDeviceStateChangeManager);
        mWifiMetrics.setWifiConfigManager(mWcm);
        mWifiMetrics.setWifiBlocklistMonitor(mWifiBlocklistMonitor);
        mWifiMetrics.setPasspointManager(mPpm);
        mWifiMetrics.setScoringParams(mScoringParams);
        mWifiMetrics.setWifiNetworkSelector(mWns);
        mWifiMetrics.setWifiDataStall(mWifiDataStall);
        mWifiMetrics.setWifiChannelUtilization(mWifiChannelUtilization);
        mWifiMetrics.setWifiSettingsStore(mWifiSettingsStore);
        mWifiMetrics.setWifiHealthMonitor(mWifiHealthMonitor);
        mWifiMetrics.setWifiScoreCard(mWifiScoreCard);
        when(mOnWifiUsabilityStatsListener.asBinder()).thenReturn(mAppBinder);
        when(mWifiScoreCard.lookupNetwork(anyString())).thenReturn(mPerNetwork);
        when(mPerNetwork.getRecentStats()).thenReturn(mNetworkConnectionStats);
        verify(mWifiDeviceStateChangeManager)
                .registerStateChangeCallback(mStateChangeCallbackArgumentCaptor.capture());
        setScreenState(true);

        mWifiMetrics.registerForWifiMonitorEvents("wlan0");
        verify(mWifiMonitor, atLeastOnce())
                .registerHandler(eq("wlan0"), anyInt(), mHandlerCaptor.capture());

        mWifiMetrics.setActiveModeWarden(mActiveModeWarden);
        verify(mActiveModeWarden).registerModeChangeCallback(
                mModeChangeCallbackArgumentCaptor.capture());
        ActiveModeWarden.ModeChangeCallback modeChangeCallback =
                        mModeChangeCallbackArgumentCaptor.getValue();
        ConcreteClientModeManager concreteClientModeManager = mock(ConcreteClientModeManager.class);
        when(concreteClientModeManager.getInterfaceName()).thenReturn(TEST_IFACE_NAME);
        when(concreteClientModeManager.getRole()).thenReturn(ActiveModeManager.ROLE_CLIENT_PRIMARY);
        modeChangeCallback.onActiveModeManagerAdded(concreteClientModeManager);

        mSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(WifiStatsLog.class)
                .startMocking();
    }

    @After
    public void tearDown() {
        mSession.finishMocking();
    }

    /**
     * Test that startConnectionEvent and endConnectionEvent can be called repeatedly and out of
     * order. Only tests no exception occurs. Creates 3 ConnectionEvents.
     */
    @Test
    public void startAndEndConnectionEventSucceeds() throws Exception {
        //Start and end Connection event
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, null,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                WifiMetricsProto.ConnectionEvent.HLF_DHCP,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        //end Connection event without starting one
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                WifiMetricsProto.ConnectionEvent.HLF_DHCP,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        //start two ConnectionEvents in a row
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, null,
                "BLUE", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, null,
                "GREEN", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
    }

    private static final long TEST_RECORD_DURATION_SEC = 12 * 60 * 60;
    private static final long TEST_RECORD_DURATION_MILLIS = TEST_RECORD_DURATION_SEC * 1000;
    /**
     * Simulate how dumpsys gets the proto from mWifiMetrics, filter the proto bytes out and
     * deserialize them into mDecodedProto
     */
    private void dumpProtoAndDeserialize() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(stream);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(TEST_RECORD_DURATION_MILLIS);
        //Test proto dump, by passing in proto arg option
        String[] args = {WifiMetrics.PROTO_DUMP_ARG};
        mWifiMetrics.dump(null, writer, args);
        writer.flush();
        Pattern pattern = Pattern.compile(
                "(?<=WifiMetrics:\\n)([\\s\\S]*)(?=EndWifiMetrics)");
        Matcher matcher = pattern.matcher(stream.toString());
        assertTrue("Proto Byte string found in WifiMetrics.dump():\n" + stream.toString(),
                matcher.find());
        String protoByteString = matcher.group(1);
        byte[] protoBytes = Base64.decode(protoByteString, Base64.DEFAULT);
        mDecodedProto = WifiMetricsProto.WifiLog.parseFrom(protoBytes);
    }

    /*, LOCAL_GEN, DEAUTH_REASON*
     * Gets the 'clean dump' proto bytes from mWifiMetrics & deserializes it into
     * mDecodedProto
     */
    public void cleanDumpProtoAndDeserialize() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(stream);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(TEST_RECORD_DURATION_MILLIS);
        //Test proto dump, by passing in proto arg option
        String[] args = {WifiMetrics.PROTO_DUMP_ARG, WifiMetrics.CLEAN_DUMP_ARG};
        mWifiMetrics.dump(null, writer, args);
        writer.flush();
        String protoByteString = stream.toString();
        byte[] protoBytes = Base64.decode(protoByteString, Base64.DEFAULT);
        mDecodedProto = WifiMetricsProto.WifiLog.parseFrom(protoBytes);
    }

    /** Verifies that dump() includes the expected header */
    @Test
    public void stateDumpIncludesHeader() throws Exception {
        assertStringContains(getStateDump(), "WifiMetrics");
    }

    /** Verifies that dump() includes correct alert count when there are no alerts. */
    @Test
    public void stateDumpAlertCountIsCorrectWithNoAlerts() throws Exception {
        assertStringContains(getStateDump(), "mWifiLogProto.alertReasonCounts=()");
    }

    /** Verifies that dump() includes correct alert count when there is one alert. */
    @Test
    public void stateDumpAlertCountIsCorrectWithOneAlert() throws Exception {
        mWifiMetrics.logFirmwareAlert(TEST_IFACE_NAME, 1);
        assertStringContains(getStateDump(), "mWifiLogProto.alertReasonCounts=(1,1)");
    }

    /** Verifies that dump() includes correct alert count when there are multiple alerts. */
    @Test
    public void stateDumpAlertCountIsCorrectWithMultipleAlerts() throws Exception {
        mWifiMetrics.logFirmwareAlert(TEST_IFACE_NAME, 1);
        mWifiMetrics.logFirmwareAlert(TEST_IFACE_NAME, 1);
        mWifiMetrics.logFirmwareAlert(TEST_IFACE_NAME, 16);
        assertStringContains(getStateDump(), "mWifiLogProto.alertReasonCounts=(1,2),(16,1)");
    }

    @Test
    public void testDumpProtoAndDeserialize() throws Exception {
        setAndIncrementMetrics();
        dumpProtoAndDeserialize();
        verify(mWifiP2pMetrics).consolidateProto();
        assertDeserializedMetricsCorrect();
    }

    private static final int NUM_OPEN_NETWORKS = 2;
    private static final int NUM_LEGACY_PERSONAL_NETWORKS = 3;
    private static final int NUM_LEGACY_ENTERPRISE_NETWORKS = 5;
    private static final int NUM_ENHANCED_OPEN_NETWORKS = 1;
    private static final int NUM_WPA3_PERSONAL_NETWORKS = 4;
    private static final int NUM_WPA3_ENTERPRISE_NETWORKS = 6;
    private static final int NUM_WAPI_PERSONAL_NETWORKS = 4;
    private static final int NUM_WAPI_ENTERPRISE_NETWORKS = 6;
    private static final int NUM_SAVED_NETWORKS = NUM_OPEN_NETWORKS + NUM_LEGACY_PERSONAL_NETWORKS
            + NUM_LEGACY_ENTERPRISE_NETWORKS + NUM_ENHANCED_OPEN_NETWORKS
            + NUM_WPA3_PERSONAL_NETWORKS + NUM_WPA3_ENTERPRISE_NETWORKS
            + NUM_WAPI_PERSONAL_NETWORKS + NUM_WAPI_ENTERPRISE_NETWORKS;
    private static final int NUM_HIDDEN_NETWORKS = NUM_OPEN_NETWORKS;
    private static final int NUM_PASSPOINT_NETWORKS = NUM_LEGACY_ENTERPRISE_NETWORKS;
    private static final int NUM_NETWORKS_ADDED_BY_USER = 0;
    private static final int NUM_NETWORKS_ADDED_BY_APPS = NUM_SAVED_NETWORKS
            - NUM_NETWORKS_ADDED_BY_USER;
    private static final boolean TEST_VAL_IS_LOCATION_ENABLED = true;
    private static final boolean IS_SCANNING_ALWAYS_ENABLED = true;
    private static final boolean IS_VERBOSE_LOGGING_ENABLED = true;
    private static final boolean IS_NON_PERSISTENT_MAC_RANDOMIZATION_FORCE_ENABLED = true;
    private static final boolean IS_WIFI_WAKE_ENABLED = true;
    private static final int NUM_EMPTY_SCAN_RESULTS = 19;
    private static final int NUM_NON_EMPTY_SCAN_RESULTS = 23;
    private static final int NUM_SCAN_UNKNOWN = 1;
    private static final int NUM_SCAN_SUCCESS = 2;
    private static final int NUM_SCAN_FAILURE_INTERRUPTED = 3;
    private static final int NUM_SCAN_FAILURE_INVALID_CONFIGURATION = 5;
    private static final int NUM_WIFI_UNKNOWN_SCREEN_OFF = 3;
    private static final int NUM_WIFI_UNKNOWN_SCREEN_ON = 5;
    private static final int NUM_WIFI_ASSOCIATED_SCREEN_OFF = 7;
    private static final int NUM_WIFI_ASSOCIATED_SCREEN_ON = 11;
    private static final int NUM_CONNECTIVITY_WATCHDOG_PNO_GOOD = 11;
    private static final int NUM_CONNECTIVITY_WATCHDOG_PNO_BAD = 12;
    private static final int NUM_CONNECTIVITY_WATCHDOG_BACKGROUND_GOOD = 13;
    private static final int NUM_CONNECTIVITY_WATCHDOG_BACKGROUND_BAD = 14;
    private static final int NUM_LAST_RESORT_WATCHDOG_TRIGGERS = 1;
    private static final int NUM_LAST_RESORT_WATCHDOG_BAD_ASSOCIATION_NETWORKS_TOTAL = 2;
    private static final int NUM_LAST_RESORT_WATCHDOG_BAD_AUTHENTICATION_NETWORKS_TOTAL = 3;
    private static final int NUM_LAST_RESORT_WATCHDOG_BAD_DHCP_NETWORKS_TOTAL = 4;
    private static final int NUM_LAST_RESORT_WATCHDOG_BAD_OTHER_NETWORKS_TOTAL = 5;
    private static final int NUM_LAST_RESORT_WATCHDOG_AVAILABLE_NETWORKS_TOTAL = 6;
    private static final int NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_ASSOCIATION = 7;
    private static final int NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_AUTHENTICATION = 8;
    private static final int NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_DHCP = 9;
    private static final int NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_OTHER = 10;
    private static final int NUM_LAST_RESORT_WATCHDOG_SUCCESSES = 5;
    private static final int WATCHDOG_TOTAL_CONNECTION_FAILURE_COUNT_AFTER_TRIGGER = 6;
    private static final int RSSI_POLL_FREQUENCY = 5150;
    private static final int NUM_RSSI_LEVELS_TO_INCREMENT = 20;
    private static final int NUM_OPEN_NETWORK_SCAN_RESULTS = 1;
    private static final int NUM_LEGACY_PERSONAL_NETWORK_SCAN_RESULTS = 4;
    private static final int NUM_ENHANCED_OPEN_NETWORK_SCAN_RESULTS = 1;
    private static final int NUM_WPA3_PERSONAL_NETWORK_SCAN_RESULTS = 2;
    private static final int NUM_WPA3_ENTERPRISE_NETWORK_SCAN_RESULTS = 3;
    private static final int NUM_WAPI_PERSONAL_NETWORK_SCAN_RESULTS = 1;
    private static final int NUM_WAPI_ENTERPRISE_NETWORK_SCAN_RESULTS = 2;
    private static final int NUM_HIDDEN_NETWORK_SCAN_RESULTS = 1;
    private static final int NUM_HOTSPOT2_R1_NETWORK_SCAN_RESULTS = 1;
    private static final int NUM_HOTSPOT2_R2_NETWORK_SCAN_RESULTS = 2;
    private static final int NUM_HOTSPOT2_R3_NETWORK_SCAN_RESULTS = 2;
    private static final int NUM_LEGACY_ENTERPRISE_NETWORK_SCAN_RESULTS =
            NUM_HOTSPOT2_R1_NETWORK_SCAN_RESULTS + NUM_HOTSPOT2_R2_NETWORK_SCAN_RESULTS
            + NUM_HOTSPOT2_R3_NETWORK_SCAN_RESULTS;
    private static final int NUM_SCANS = 5;
    private static final int NUM_CONNECTIVITY_ONESHOT_SCAN_EVENT = 4;
    private static final int NUM_EXTERNAL_APP_ONESHOT_SCAN_REQUESTS = 15;
    private static final int NUM_EXTERNAL_FOREGROUND_APP_ONESHOT_SCAN_REQUESTS_THROTTLED = 10;
    private static final int NUM_EXTERNAL_BACKGROUND_APP_ONESHOT_SCAN_REQUESTS_THROTTLED = 16;
    // Look at buildMockScanDetailList, this number needs to match the mocked results
    private static final int NUM_TOTAL_SCAN_RESULTS = NUM_OPEN_NETWORK_SCAN_RESULTS
            + NUM_LEGACY_PERSONAL_NETWORK_SCAN_RESULTS + NUM_LEGACY_ENTERPRISE_NETWORK_SCAN_RESULTS
            + NUM_ENHANCED_OPEN_NETWORK_SCAN_RESULTS + NUM_WPA3_PERSONAL_NETWORK_SCAN_RESULTS
            + NUM_WPA3_ENTERPRISE_NETWORK_SCAN_RESULTS + NUM_WAPI_PERSONAL_NETWORK_SCAN_RESULTS
            + NUM_WAPI_ENTERPRISE_NETWORK_SCAN_RESULTS;
    private static final int MIN_RSSI_LEVEL = -127;
    private static final int MAX_RSSI_LEVEL = 0;
    private static final int WIFI_SCORE_RANGE_MIN = 0;
    private static final int NUM_WIFI_SCORES_TO_INCREMENT = 20;
    private static final int WIFI_SCORE_RANGE_MAX = 60;
    private static final int NUM_OUT_OF_BOUND_ENTRIES = 10;
    private static final int MAX_NUM_SOFTAP_RETURN_CODES = 3;
    private static final int NUM_SOFTAP_START_SUCCESS = 3;
    private static final int NUM_SOFTAP_FAILED_GENERAL_ERROR = 2;
    private static final int NUM_SOFTAP_FAILED_NO_CHANNEL = 1;
    private static final int NUM_HAL_CRASHES = 11;
    private static final int NUM_WIFICOND_CRASHES = 12;
    private static final int NUM_SUPPLICANT_CRASHES = 23;
    private static final int NUM_HOSTAPD_CRASHES = 7;
    private static final int NUM_WIFI_ON_FAILURE_DUE_TO_HAL = 13;
    private static final int NUM_WIFI_ON_FAILURE_DUE_TO_WIFICOND = 14;
    private static final int NUM_WIFI_ON_FAILURE_DUE_TO_SUPPLICANT = 20;
    private static final int NUM_SOFTAP_ON_FAILURE_DUE_TO_HAL = 23;
    private static final int NUM_SOFTAP_ON_FAILURE_DUE_TO_WIFICOND = 19;
    private static final int NUM_SOFTAP_ON_FAILURE_DUE_TO_HOSTAPD = 31;
    private static final int NUM_SOFTAP_INTERFACE_DOWN = 65;
    private static final int NUM_CLIENT_INTERFACE_DOWN = 12;
    private static final int NUM_PASSPOINT_PROVIDERS = 7;
    private static final int NUM_PASSPOINT_PROVIDER_INSTALLATION = 5;
    private static final int NUM_PASSPOINT_PROVIDER_INSTALL_SUCCESS = 4;
    private static final int NUM_PASSPOINT_PROVIDER_UNINSTALLATION = 3;
    private static final int NUM_PASSPOINT_PROVIDER_UNINSTALL_SUCCESS = 2;
    private static final int NUM_PASSPOINT_PROVIDERS_SUCCESSFULLY_CONNECTED = 1;
    private static final int NUM_PASSPOINT_PROVIDERS_WITH_NO_ROOT_CA = 2;
    private static final int NUM_PASSPOINT_PROVIDERS_WITH_SELF_SIGNED_ROOT_CA = 3;
    private static final int NUM_PASSPOINT_PROVIDERS_WITH_EXPIRATION_DATE = 4;
    private static final int NUM_EAP_SIM_TYPE = 1;
    private static final int NUM_EAP_TTLS_TYPE = 2;
    private static final int NUM_EAP_TLS_TYPE = 3;
    private static final int NUM_EAP_AKA_TYPE = 4;
    private static final int NUM_EAP_AKA_PRIME_TYPE = 5;
    private static final SparseIntArray SAVED_PASSPOINT_PROVIDERS_TYPE = new SparseIntArray();
    static {
        SAVED_PASSPOINT_PROVIDERS_TYPE.put(EAPConstants.EAP_SIM, NUM_EAP_SIM_TYPE);
        SAVED_PASSPOINT_PROVIDERS_TYPE.put(EAPConstants.EAP_TTLS, NUM_EAP_TTLS_TYPE);
        SAVED_PASSPOINT_PROVIDERS_TYPE.put(EAPConstants.EAP_TLS, NUM_EAP_TLS_TYPE);
        SAVED_PASSPOINT_PROVIDERS_TYPE.put(EAPConstants.EAP_AKA, NUM_EAP_AKA_TYPE);
        SAVED_PASSPOINT_PROVIDERS_TYPE.put(EAPConstants.EAP_AKA_PRIME, NUM_EAP_AKA_PRIME_TYPE);
    }

    private static final int NUM_PARTIAL_SCAN_RESULTS = 73;
    private static final int NUM_PNO_SCAN_ATTEMPTS = 20;
    private static final int NUM_PNO_SCAN_FAILED = 5;
    private static final int NUM_PNO_FOUND_NETWORK_EVENTS = 10;
    private static final int NUM_RADIO_MODE_CHANGE_TO_MCC = 4;
    private static final int NUM_RADIO_MODE_CHANGE_TO_SCC = 13;
    private static final int NUM_RADIO_MODE_CHANGE_TO_SBS = 19;
    private static final int NUM_RADIO_MODE_CHANGE_TO_DBS = 34;
    private static final int NUM_SOFTAP_USER_BAND_PREFERENCE_UNSATISFIED = 14;
    private static final long NUM_WATCHDOG_SUCCESS_DURATION_MS = 65;
    private static final long WIFI_POWER_METRICS_LOGGING_DURATION = 280;
    private static final long WIFI_POWER_METRICS_SCAN_TIME = 33;
    private static final boolean LINK_SPEED_COUNTS_LOGGING_SETTING = true;
    private static final int DATA_STALL_MIN_TX_BAD_SETTING = 5;
    private static final int DATA_STALL_MIN_TX_SUCCESS_WITHOUT_RX_SETTING = 75;
    private static final int NUM_ONESHOT_SCAN_REQUESTS_WITH_DFS_CHANNELS = 4;
    private static final int NUM_ADD_OR_UPDATE_NETWORK_CALLS = 5;
    private static final int NUM_ENABLE_NETWORK_CALLS = 6;
    private static final long NUM_IP_RENEWAL_FAILURE = 7;
    private static final int NUM_NETWORK_ABNORMAL_ASSOC_REJECTION = 2;
    private static final int NUM_NETWORK_ABNORMAL_CONNECTION_FAILURE_DISCONNECTION = 5;
    private static final int NUM_NETWORK_SUFFICIENT_RECENT_STATS_ONLY = 4;
    private static final int NUM_NETWORK_SUFFICIENT_RECENT_PREV_STATS = 5;
    private static final int NUM_BSSID_SELECTION_DIFFERENT_BETWEEN_FRAMEWORK_FIRMWARE = 3;
    private static final long WIFI_MAINLINE_MODULE_VERSION = 123456L;

    /** Number of notifications per "Connect to Network" notification type. */
    private static final int[] NUM_CONNECT_TO_NETWORK_NOTIFICATIONS = {0, 10, 20, 30, 40};
    /** Number of notifications per "Connect to Network notification type and action type. */
    private static final int[][] NUM_CONNECT_TO_NETWORK_NOTIFICATION_ACTIONS = {
            {0, 1, 2, 3, 4},
            {10, 11, 12, 13, 14},
            {20, 21, 22, 23, 24},
            {30, 31, 32, 33, 34},
            {40, 41, 42, 43, 44}};
    private static final int SIZE_OPEN_NETWORK_RECOMMENDER_BLOCKLIST = 10;
    private static final boolean IS_WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON = true;
    private static final int NUM_OPEN_NETWORK_CONNECT_MESSAGE_FAILED_TO_SEND = 5;
    private static final int NUM_OPEN_NETWORK_RECOMMENDATION_UPDATES = 8;
    private static final String OPEN_NET_NOTIFIER_TAG = OpenNetworkNotifier.TAG;

    private static final int NUM_SOFT_AP_EVENT_ENTRIES = 3;
    private static final int NUM_SOFT_AP_EVENT_ENTRIES_FOR_BRIDGED_AP = 4;
    private static final int NUM_SOFT_AP_ASSOCIATED_STATIONS = 3;
    private static final int SOFT_AP_CHANNEL_FREQUENCY_2G = 2437;
    private static final int SOFT_AP_CHANNEL_BANDWIDTH_2G =
            SoftApConnectedClientsEvent.BANDWIDTH_20;
    private static final int SOFT_AP_GENERATION_2G = ScanResult.WIFI_STANDARD_11N;
    private static final int SOFT_AP_CHANNEL_FREQUENCY_5G = 5180;
    private static final int SOFT_AP_CHANNEL_BANDWIDTH_5G =
            SoftApConnectedClientsEvent.BANDWIDTH_80;
    private static final int SOFT_AP_GENERATION_5G = ScanResult.WIFI_STANDARD_11AC;
    private static final int SOFT_AP_MAX_CLIENT_SETTING = 10;
    private static final int SOFT_AP_MAX_CLIENT_CAPABILITY = 16;
    private static final long SOFT_AP_SHUTDOWN_TIMEOUT_SETTING = 10_000;
    private static final long SOFT_AP_SHUTDOWN_TIMEOUT_DEFAULT_SETTING = 600_000;
    private static final boolean SOFT_AP_CLIENT_CONTROL_ENABLE = true;
    private static final boolean IS_MAC_RANDOMIZATION_ON = true;
    private static final int NUM_LINK_SPEED_LEVELS_TO_INCREMENT = 30;
    private static final int TEST_RSSI_LEVEL = -80;
    private static final int MAX_SUPPORTED_TX_LINK_SPEED_MBPS = 144;
    private static final int MAX_SUPPORTED_RX_LINK_SPEED_MBPS = 190;

    private static final long NUM_MBO_SUPPORTED_NETWORKS_SCAN_RESULTS = 4;
    private static final long NUM_MBO_CELL_DATA_AWARE_NETWORKS_SCAN_RESULTS = 2;
    private static final long NUM_OCE_SUPPORTED_NETWORKS_SCAN_RESULTS = 2;
    private static final long NUM_FILS_SUPPORTED_NETWORKS_SCAN_RESULTS = 2;
    private static final long NUM_11AX_NETWORKS_SCAN_RESULTS = 3;
    private static final long NUM_6G_NETWORKS_SCAN_RESULTS = 2;
    private static final long NUM_6G_PSC_NETWORKS_SCAN_RESULTS = 1;
    private static final long NUM_BSSID_FILTERED_DUE_TO_MBO_ASSOC_DISALLOW_IND = 3;
    private static final long NUM_CONNECT_TO_MBO_SUPPORTED_NETWORKS = 4;
    private static final long NUM_CONNECT_TO_OCE_SUPPORTED_NETWORKS = 3;
    private static final long NUM_STEERING_REQUEST = 3;
    private static final long NUM_FORCE_SCAN_DUE_TO_STEERING_REQUEST = 2;
    private static final long NUM_MBO_CELLULAR_SWITCH_REQUEST = 3;
    private static final long NUM_STEERING_REQUEST_INCLUDING_MBO_ASSOC_RETRY_DELAY = 3;
    private static final long NUM_CONNECT_REQUEST_WITH_FILS_AKM = 4;
    private static final long NUM_L2_CONNECTION_THROUGH_FILS_AUTHENTICATION = 3;

    private static final int FEATURE_MBO = 1 << 0;
    private static final int FEATURE_MBO_CELL_DATA_AWARE = 1 << 1;
    private static final int FEATURE_OCE = 1 << 2;
    private static final int FEATURE_11AX = 1 << 3;
    private static final int FEATURE_6G = 1 << 4;
    private static final int FEATURE_6G_PSC = 1 << 5;

    private ScanDetail buildMockScanDetail(boolean hidden, NetworkDetail.HSRelease hSRelease,
            String capabilities, int supportedFeatures) {
        ScanDetail mockScanDetail = mock(ScanDetail.class);
        NetworkDetail mockNetworkDetail = mock(NetworkDetail.class);
        ScanResult mockScanResult = mock(ScanResult.class);
        when(mockScanDetail.getNetworkDetail()).thenReturn(mockNetworkDetail);
        when(mockScanDetail.getScanResult()).thenReturn(mockScanResult);
        when(mockNetworkDetail.isHiddenBeaconFrame()).thenReturn(hidden);
        when(mockNetworkDetail.getHSRelease()).thenReturn(hSRelease);
        mockScanResult.capabilities = capabilities;
        if ((supportedFeatures & FEATURE_MBO) != 0) {
            when(mockNetworkDetail.isMboSupported()).thenReturn(true);
        }
        if ((supportedFeatures & FEATURE_MBO_CELL_DATA_AWARE) != 0) {
            when(mockNetworkDetail.isMboCellularDataAware()).thenReturn(true);
        }
        if ((supportedFeatures & FEATURE_OCE) != 0) {
            when(mockNetworkDetail.isOceSupported()).thenReturn(true);
        }
        if ((supportedFeatures & FEATURE_11AX) != 0) {
            when(mockNetworkDetail.getWifiMode())
                    .thenReturn(InformationElementUtil.WifiMode.MODE_11AX);
        }
        if ((supportedFeatures & FEATURE_6G) != 0) {
            when(mockScanResult.is6GHz()).thenReturn(true);
        }
        if ((supportedFeatures & FEATURE_6G_PSC) != 0) {
            when(mockScanResult.is6GhzPsc()).thenReturn(true);
        }
        return mockScanDetail;
    }

    private ScanDetail buildMockScanDetail(String ssid, String bssid, boolean isOpen,
            boolean isSaved, boolean isProvider, boolean isWeakRssi) {
        ScanDetail mockScanDetail = mock(ScanDetail.class);
        NetworkDetail mockNetworkDetail = mock(NetworkDetail.class);
        ScanResult scanResult = new ScanResult();
        scanResult.SSID = ssid;
        scanResult.setWifiSsid(WifiSsid.fromUtf8Text(ssid));
        scanResult.BSSID = bssid;
        when(mockScanDetail.getNetworkDetail()).thenReturn(mockNetworkDetail);
        when(mockScanDetail.getScanResult()).thenReturn(scanResult);
        when(mWns.isSignalTooWeak(eq(scanResult))).thenReturn(isWeakRssi);
        scanResult.capabilities = isOpen ? "" : "PSK";
        if (isSaved) {
            when(mWcm.getSavedNetworkForScanDetail(eq(mockScanDetail)))
                    .thenReturn(mock(WifiConfiguration.class));
        }
        if (isProvider) {
            PasspointProvider provider = mock(PasspointProvider.class);
            List<Pair<PasspointProvider, PasspointMatch>> matchedProviders = new ArrayList<>();
            matchedProviders.add(Pair.create(provider, null));
            when(mockNetworkDetail.isInterworking()).thenReturn(true);
            when(mPpm.matchProvider(eq(scanResult), eq(false))).thenReturn(matchedProviders);
        }
        return mockScanDetail;
    }

    private ScanDetail buildMockScanDetailPasspoint(String ssid, String bssid, long hessid,
            int anqpDomainId, NetworkDetail.HSRelease hsRelease, boolean weakSignal) {
        ScanDetail mockScanDetail = mock(ScanDetail.class);
        NetworkDetail mockNetworkDetail = mock(NetworkDetail.class);
        ScanResult scanResult = new ScanResult();
        scanResult.SSID = ssid;
        scanResult.setWifiSsid(WifiSsid.fromUtf8Text(ssid));
        scanResult.BSSID = bssid;
        scanResult.hessid = hessid;
        scanResult.capabilities = "PSK";
        when(mockScanDetail.getNetworkDetail()).thenReturn(mockNetworkDetail);
        when(mockScanDetail.getScanResult()).thenReturn(scanResult);
        when(mockNetworkDetail.getHSRelease()).thenReturn(hsRelease);
        when(mockNetworkDetail.getAnqpDomainID()).thenReturn(anqpDomainId);
        when(mockNetworkDetail.isInterworking()).thenReturn(true);
        when(mWns.isSignalTooWeak(eq(scanResult))).thenReturn(weakSignal);
        return mockScanDetail;
    }

    private List<ScanDetail> buildMockScanDetailList() {
        List<ScanDetail> mockScanDetails = new ArrayList<ScanDetail>();
        mockScanDetails.add(buildMockScanDetail(true, null, "[ESS]", 0));
        mockScanDetails.add(buildMockScanDetail(false, null, "[WPA2-PSK-CCMP][ESS]", FEATURE_11AX));
        mockScanDetails.add(buildMockScanDetail(false, null, "[WPA-PSK-CCMP]", 0));
        mockScanDetails.add(buildMockScanDetail(false, null, "[WPA2-SAE-CCMP]", FEATURE_MBO));
        mockScanDetails.add(buildMockScanDetail(false, null, "[WPA-PSK-CCMP]",
                FEATURE_11AX | FEATURE_6G));
        mockScanDetails.add(buildMockScanDetail(false, null, "[WEP]", 0));
        mockScanDetails.add(buildMockScanDetail(false, null, "[WPA2-SAE-CCMP]",
                FEATURE_MBO | FEATURE_MBO_CELL_DATA_AWARE));
        mockScanDetails.add(buildMockScanDetail(false, null, "[WPA2-OWE-CCMP]",
                FEATURE_MBO | FEATURE_MBO_CELL_DATA_AWARE | FEATURE_OCE));
        mockScanDetails.add(buildMockScanDetail(false, null, "[RSN-SUITE_B_192][MFPR]",
                FEATURE_11AX | FEATURE_6G | FEATURE_6G_PSC));
        // WPA3 Enterprise transition network
        mockScanDetails.add(buildMockScanDetail(false, null,
                "[RSN-EAP/SHA1+EAP/SHA256-CCMP][MFPC]", 0));
        // WPA3 Enterprise only network
        mockScanDetails.add(buildMockScanDetail(false, null,
                "[RSN-EAP/SHA256-CCMP][MFPR][MFPC]", 0));
        mockScanDetails.add(buildMockScanDetail(false, null, "[WAPI-WAPI-PSK-SMS4-SMS4]", 0));
        mockScanDetails.add(buildMockScanDetail(false, null, "[WAPI-WAPI-CERT-SMS4-SMS4]", 0));
        mockScanDetails.add(buildMockScanDetail(false, null, "[WAPI-WAPI-CERT-SMS4-SMS4]", 0));
        // Number of scans of R2 networks must be equal to NUM_HOTSPOT2_R2_NETWORK_SCAN_RESULTS
        mockScanDetails.add(buildMockScanDetail(false, NetworkDetail.HSRelease.R2,
                "[WPA-EAP/SHA1-CCMP+EAP-FILS-SHA256-CCMP]", FEATURE_MBO | FEATURE_OCE));
        mockScanDetails.add(buildMockScanDetail(false, NetworkDetail.HSRelease.R2,
                "[WPA2-EAP/SHA1+FT/EAP-CCMP+EAP-FILS-SHA256-CCMP]", 0));
        // Number of scans of R1 networks must be equal to NUM_HOTSPOT2_R1_NETWORK_SCAN_RESULTS
        mockScanDetails.add(buildMockScanDetail(false, NetworkDetail.HSRelease.R1,
                "[WPA-EAP/SHA1-CCMP]", 0));
        // Number of scans of R3 networks must be equal to NUM_HOTSPOT2_R3_NETWORK_SCAN_RESULTS
        mockScanDetails.add(buildMockScanDetail(false, NetworkDetail.HSRelease.R3,
                "[WPA-EAP/SHA1-CCMP]", 0));
        // WPA2 Enterprise network with MFPR and MFPC
        mockScanDetails.add(buildMockScanDetail(false, NetworkDetail.HSRelease.R3,
                "[WPA-EAP/SHA1-CCMP][MFPR][MFPC]", 0));
        return mockScanDetails;
    }

    private List<WifiConfiguration> buildSavedNetworkList() {
        List<WifiConfiguration> testSavedNetworks = new ArrayList<WifiConfiguration>();
        for (int i = 0; i < NUM_OPEN_NETWORKS; i++) {
            testSavedNetworks.add(WifiConfigurationTestUtil.createOpenHiddenNetwork());
        }
        for (int i = 0; i < NUM_LEGACY_PERSONAL_NETWORKS; i++) {
            testSavedNetworks.add(WifiConfigurationTestUtil.createPskNetwork());
        }
        for (int i = 0; i < NUM_LEGACY_ENTERPRISE_NETWORKS; i++) {
            // Passpoint networks are counted in both Passpoint and Enterprise counters
            testSavedNetworks.add(WifiConfigurationTestUtil.createPasspointNetwork());
        }
        for (int i = 0; i < NUM_ENHANCED_OPEN_NETWORKS; i++) {
            testSavedNetworks.add(WifiConfigurationTestUtil.createOweNetwork());
        }
        for (int i = 0; i < NUM_WPA3_PERSONAL_NETWORKS; i++) {
            testSavedNetworks.add(WifiConfigurationTestUtil.createSaeNetwork());
        }
        for (int i = 0; i < NUM_WPA3_ENTERPRISE_NETWORKS; i++) {
            testSavedNetworks.add(WifiConfigurationTestUtil.createEapSuiteBNetwork());
        }
        for (int i = 0; i < NUM_WAPI_PERSONAL_NETWORKS; i++) {
            testSavedNetworks.add(WifiConfigurationTestUtil.createWapiPskNetwork());
        }
        for (int i = 0; i < NUM_WAPI_ENTERPRISE_NETWORKS; i++) {
            testSavedNetworks.add(WifiConfigurationTestUtil.createWapiCertNetwork());
        }
        testSavedNetworks.get(0).macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        return testSavedNetworks;
    }

    private PasspointProvider createMockProvider(int eapType, boolean validateForR2) {
        PasspointProvider provider = mock(PasspointProvider.class);
        PasspointConfiguration config = mock(PasspointConfiguration.class);
        Credential credential = new Credential();

        switch (eapType) {
            case EAPConstants.EAP_TLS:
                credential.setCertCredential(new Credential.CertificateCredential());
                break;
            case EAPConstants.EAP_TTLS:
                credential.setUserCredential(new Credential.UserCredential());
                break;
            case EAPConstants.EAP_AKA:
            case EAPConstants.EAP_AKA_PRIME:
            case EAPConstants.EAP_SIM:
                Credential.SimCredential simCredential = new Credential.SimCredential();
                simCredential.setEapType(eapType);
                credential.setSimCredential(simCredential);
                break;
        }
        when(provider.getConfig()).thenReturn(config);
        when(config.getCredential()).thenReturn(credential);
        when(config.validateForR2()).thenReturn(validateForR2);
        return provider;
    }

    /**
     * Set simple metrics, increment others
     */
    public void setAndIncrementMetrics() throws Exception {
        Map<String, PasspointProvider> providers = new HashMap<>();
        mWifiMetrics.updateSavedNetworks(buildSavedNetworkList());
        mWifiMetrics.updateSavedPasspointProfiles(NUM_PASSPOINT_PROVIDERS,
                NUM_PASSPOINT_PROVIDERS_SUCCESSFULLY_CONNECTED);
        for (int i = 0; i < SAVED_PASSPOINT_PROVIDERS_TYPE.size(); i++) {
            int eapType = SAVED_PASSPOINT_PROVIDERS_TYPE.keyAt(i);
            int count = SAVED_PASSPOINT_PROVIDERS_TYPE.valueAt(i);
            for (int j = 0; j < count; j++) {
                providers.put(Integer.toString(eapType) + j, createMockProvider(eapType, false));
            }
            for (int j = count; j < count * 2; j++) {
                providers.put(Integer.toString(eapType) + j, createMockProvider(eapType, true));
            }
        }
        mWifiMetrics.updateSavedPasspointProfilesInfo(providers);

        mWifiMetrics.setIsLocationEnabled(TEST_VAL_IS_LOCATION_ENABLED);
        mWifiMetrics.setIsScanningAlwaysEnabled(IS_SCANNING_ALWAYS_ENABLED);
        mWifiMetrics.setVerboseLoggingEnabled(IS_VERBOSE_LOGGING_ENABLED);
        mWifiMetrics.setNonPersistentMacRandomizationForceEnabled(
                IS_NON_PERSISTENT_MAC_RANDOMIZATION_FORCE_ENABLED);
        mWifiMetrics.setWifiWakeEnabled(IS_WIFI_WAKE_ENABLED);

        for (int i = 0; i < NUM_EMPTY_SCAN_RESULTS; i++) {
            mWifiMetrics.incrementEmptyScanResultCount();
        }
        for (int i = 0; i < NUM_NON_EMPTY_SCAN_RESULTS; i++) {
            mWifiMetrics.incrementNonEmptyScanResultCount();
        }
        mWifiMetrics.incrementScanReturnEntry(WifiMetricsProto.WifiLog.SCAN_UNKNOWN,
                NUM_SCAN_UNKNOWN);
        mWifiMetrics.incrementScanReturnEntry(WifiMetricsProto.WifiLog.SCAN_SUCCESS,
                NUM_SCAN_SUCCESS);
        mWifiMetrics.incrementScanReturnEntry(
                WifiMetricsProto.WifiLog.SCAN_FAILURE_INTERRUPTED,
                NUM_SCAN_FAILURE_INTERRUPTED);
        mWifiMetrics.incrementScanReturnEntry(
                WifiMetricsProto.WifiLog.SCAN_FAILURE_INVALID_CONFIGURATION,
                NUM_SCAN_FAILURE_INVALID_CONFIGURATION);
        for (int i = 0; i < NUM_WIFI_UNKNOWN_SCREEN_OFF; i++) {
            mWifiMetrics.incrementWifiSystemScanStateCount(WifiMetricsProto.WifiLog.WIFI_UNKNOWN,
                    false);
        }
        for (int i = 0; i < NUM_WIFI_UNKNOWN_SCREEN_ON; i++) {
            mWifiMetrics.incrementWifiSystemScanStateCount(WifiMetricsProto.WifiLog.WIFI_UNKNOWN,
                    true);
        }
        for (int i = 0; i < NUM_WIFI_ASSOCIATED_SCREEN_OFF; i++) {
            mWifiMetrics.incrementWifiSystemScanStateCount(WifiMetricsProto.WifiLog.WIFI_ASSOCIATED,
                    false);
        }
        for (int i = 0; i < NUM_WIFI_ASSOCIATED_SCREEN_ON; i++) {
            mWifiMetrics.incrementWifiSystemScanStateCount(WifiMetricsProto.WifiLog.WIFI_ASSOCIATED,
                    true);
        }
        for (int i = 0; i < NUM_CONNECTIVITY_WATCHDOG_PNO_GOOD; i++) {
            mWifiMetrics.incrementNumConnectivityWatchdogPnoGood();
        }
        for (int i = 0; i < NUM_CONNECTIVITY_WATCHDOG_PNO_BAD; i++) {
            mWifiMetrics.incrementNumConnectivityWatchdogPnoBad();
        }
        for (int i = 0; i < NUM_CONNECTIVITY_WATCHDOG_BACKGROUND_GOOD; i++) {
            mWifiMetrics.incrementNumConnectivityWatchdogBackgroundGood();
        }
        for (int i = 0; i < NUM_CONNECTIVITY_WATCHDOG_BACKGROUND_BAD; i++) {
            mWifiMetrics.incrementNumConnectivityWatchdogBackgroundBad();
        }
        for (int i = 0; i < NUM_LAST_RESORT_WATCHDOG_TRIGGERS; i++) {
            mWifiMetrics.incrementNumLastResortWatchdogTriggers();
        }
        mWifiMetrics.addCountToNumLastResortWatchdogBadAssociationNetworksTotal(
                NUM_LAST_RESORT_WATCHDOG_BAD_ASSOCIATION_NETWORKS_TOTAL);
        mWifiMetrics.addCountToNumLastResortWatchdogBadAuthenticationNetworksTotal(
                NUM_LAST_RESORT_WATCHDOG_BAD_AUTHENTICATION_NETWORKS_TOTAL);
        mWifiMetrics.addCountToNumLastResortWatchdogBadDhcpNetworksTotal(
                NUM_LAST_RESORT_WATCHDOG_BAD_DHCP_NETWORKS_TOTAL);
        mWifiMetrics.addCountToNumLastResortWatchdogBadOtherNetworksTotal(
                NUM_LAST_RESORT_WATCHDOG_BAD_OTHER_NETWORKS_TOTAL);
        mWifiMetrics.addCountToNumLastResortWatchdogAvailableNetworksTotal(
                NUM_LAST_RESORT_WATCHDOG_AVAILABLE_NETWORKS_TOTAL);
        for (int i = 0; i < NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_ASSOCIATION; i++) {
            mWifiMetrics.incrementNumLastResortWatchdogTriggersWithBadAssociation();
        }
        for (int i = 0; i < NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_AUTHENTICATION; i++) {
            mWifiMetrics.incrementNumLastResortWatchdogTriggersWithBadAuthentication();
        }
        for (int i = 0; i < NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_DHCP; i++) {
            mWifiMetrics.incrementNumLastResortWatchdogTriggersWithBadDhcp();
        }
        for (int i = 0; i < NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_OTHER; i++) {
            mWifiMetrics.incrementNumLastResortWatchdogTriggersWithBadOther();
        }
        for (int i = 0; i < NUM_LAST_RESORT_WATCHDOG_SUCCESSES; i++) {
            mWifiMetrics.incrementNumLastResortWatchdogSuccesses();
        }
        for (int i = 0; i < WATCHDOG_TOTAL_CONNECTION_FAILURE_COUNT_AFTER_TRIGGER; i++) {
            mWifiMetrics.incrementWatchdogTotalConnectionFailureCountAfterTrigger();
        }
        for (int i = 0; i < NUM_RSSI_LEVELS_TO_INCREMENT; i++) {
            for (int j = 0; j <= i; j++) {
                mWifiMetrics.incrementRssiPollRssiCount(RSSI_POLL_FREQUENCY, MIN_RSSI_LEVEL + i);
            }
        }
        for (int i = 1; i < NUM_OUT_OF_BOUND_ENTRIES; i++) {
            mWifiMetrics.incrementRssiPollRssiCount(RSSI_POLL_FREQUENCY, MIN_RSSI_LEVEL - i);
        }
        for (int i = 1; i < NUM_OUT_OF_BOUND_ENTRIES; i++) {
            mWifiMetrics.incrementRssiPollRssiCount(RSSI_POLL_FREQUENCY, MAX_RSSI_LEVEL + i);
        }

        // Test alert-reason clamping.
        mWifiMetrics.logFirmwareAlert(TEST_IFACE_NAME, WifiLoggerHal.WIFI_ALERT_REASON_MIN - 1);
        mWifiMetrics.logFirmwareAlert(TEST_IFACE_NAME, WifiLoggerHal.WIFI_ALERT_REASON_MAX + 1);
        // Simple cases for alert reason.
        mWifiMetrics.logFirmwareAlert(TEST_IFACE_NAME, 1);
        mWifiMetrics.logFirmwareAlert(TEST_IFACE_NAME, 1);
        mWifiMetrics.logFirmwareAlert(TEST_IFACE_NAME, 1);
        mWifiMetrics.logFirmwareAlert(TEST_IFACE_NAME, 2);
        List<ScanDetail> mockScanDetails = buildMockScanDetailList();
        for (int i = 0; i < NUM_SCANS; i++) {
            mWifiMetrics.countScanResults(mockScanDetails);
        }
        // increment connectivity scan metrics
        for (int i = 0; i < NUM_CONNECTIVITY_ONESHOT_SCAN_EVENT; i++) {
            mWifiMetrics.incrementConnectivityOneshotScanCount();
        }
        for (int i = 0; i < NUM_EXTERNAL_APP_ONESHOT_SCAN_REQUESTS; i++) {
            mWifiMetrics.incrementExternalAppOneshotScanRequestsCount();
        }
        for (int i = 0; i < NUM_EXTERNAL_FOREGROUND_APP_ONESHOT_SCAN_REQUESTS_THROTTLED; i++) {
            mWifiMetrics.incrementExternalForegroundAppOneshotScanRequestsThrottledCount();
        }
        for (int i = 0; i < NUM_EXTERNAL_BACKGROUND_APP_ONESHOT_SCAN_REQUESTS_THROTTLED; i++) {
            mWifiMetrics.incrementExternalBackgroundAppOneshotScanRequestsThrottledCount();
        }
        for (int score = 0; score < NUM_WIFI_SCORES_TO_INCREMENT; score++) {
            for (int offset = 0; offset <= score; offset++) {
                mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, WIFI_SCORE_RANGE_MIN + score);
            }
        }
        for (int i = 1; i < NUM_OUT_OF_BOUND_ENTRIES; i++) {
            mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, WIFI_SCORE_RANGE_MIN - i);
        }
        for (int i = 1; i < NUM_OUT_OF_BOUND_ENTRIES; i++) {
            mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, WIFI_SCORE_RANGE_MAX + i);
        }
        for (int score = 0; score < NUM_WIFI_SCORES_TO_INCREMENT; score++) {
            for (int offset = 0; offset <= score; offset++) {
                mWifiMetrics.incrementWifiUsabilityScoreCount(
                        TEST_IFACE_NAME, 1, WIFI_SCORE_RANGE_MIN + score, 15);
            }
        }
        for (int i = 1; i < NUM_OUT_OF_BOUND_ENTRIES; i++) {
            mWifiMetrics.incrementWifiUsabilityScoreCount(
                    TEST_IFACE_NAME, 1, WIFI_SCORE_RANGE_MIN - i, 15);
        }
        for (int i = 1; i < NUM_OUT_OF_BOUND_ENTRIES; i++) {
            mWifiMetrics.incrementWifiUsabilityScoreCount(
                    TEST_IFACE_NAME, 1, WIFI_SCORE_RANGE_MAX + i, 15);
        }

        // increment soft ap start return codes
        for (int i = 0; i < NUM_SOFTAP_START_SUCCESS; i++) {
            mWifiMetrics.incrementSoftApStartResult(true, 0);
        }
        for (int i = 0; i < NUM_SOFTAP_FAILED_GENERAL_ERROR; i++) {
            mWifiMetrics.incrementSoftApStartResult(false, WifiManager.SAP_START_FAILURE_GENERAL);
        }
        for (int i = 0; i < NUM_SOFTAP_FAILED_NO_CHANNEL; i++) {
            mWifiMetrics.incrementSoftApStartResult(false,
                    WifiManager.SAP_START_FAILURE_NO_CHANNEL);
        }
        for (int i = 0; i < NUM_HAL_CRASHES; i++) {
            mWifiMetrics.incrementNumHalCrashes();
        }
        for (int i = 0; i < NUM_WIFICOND_CRASHES; i++) {
            mWifiMetrics.incrementNumWificondCrashes();
        }
        for (int i = 0; i < NUM_SUPPLICANT_CRASHES; i++) {
            mWifiMetrics.incrementNumSupplicantCrashes();
        }
        for (int i = 0; i < NUM_HOSTAPD_CRASHES; i++) {
            mWifiMetrics.incrementNumHostapdCrashes();
        }
        for (int i = 0; i < NUM_WIFI_ON_FAILURE_DUE_TO_HAL; i++) {
            mWifiMetrics.incrementNumSetupClientInterfaceFailureDueToHal();
        }
        for (int i = 0; i < NUM_WIFI_ON_FAILURE_DUE_TO_WIFICOND; i++) {
            mWifiMetrics.incrementNumSetupClientInterfaceFailureDueToWificond();
        }
        for (int i = 0; i < NUM_WIFI_ON_FAILURE_DUE_TO_SUPPLICANT; i++) {
            mWifiMetrics.incrementNumSetupClientInterfaceFailureDueToSupplicant();
        }
        for (int i = 0; i < NUM_SOFTAP_ON_FAILURE_DUE_TO_HAL; i++) {
            mWifiMetrics.incrementNumSetupSoftApInterfaceFailureDueToHal();
        }
        for (int i = 0; i < NUM_SOFTAP_ON_FAILURE_DUE_TO_WIFICOND; i++) {
            mWifiMetrics.incrementNumSetupSoftApInterfaceFailureDueToWificond();
        }
        for (int i = 0; i < NUM_SOFTAP_ON_FAILURE_DUE_TO_HOSTAPD; i++) {
            mWifiMetrics.incrementNumSetupSoftApInterfaceFailureDueToHostapd();
        }
        for (int i = 0; i < NUM_SOFTAP_INTERFACE_DOWN; i++) {
            mWifiMetrics.incrementNumSoftApInterfaceDown();
        }
        for (int i = 0; i < NUM_CLIENT_INTERFACE_DOWN; i++) {
            mWifiMetrics.incrementNumClientInterfaceDown();
        }
        for (int i = 0; i < NUM_PASSPOINT_PROVIDER_INSTALLATION; i++) {
            mWifiMetrics.incrementNumPasspointProviderInstallation();
        }
        for (int i = 0; i < NUM_PASSPOINT_PROVIDER_INSTALL_SUCCESS; i++) {
            mWifiMetrics.incrementNumPasspointProviderInstallSuccess();
        }
        for (int i = 0; i < NUM_PASSPOINT_PROVIDER_UNINSTALLATION; i++) {
            mWifiMetrics.incrementNumPasspointProviderUninstallation();
        }
        for (int i = 0; i < NUM_PASSPOINT_PROVIDER_UNINSTALL_SUCCESS; i++) {
            mWifiMetrics.incrementNumPasspointProviderUninstallSuccess();
        }
        for (int i = 0; i < NUM_PASSPOINT_PROVIDERS_WITH_NO_ROOT_CA; i++) {
            mWifiMetrics.incrementNumPasspointProviderWithNoRootCa();
        }
        for (int i = 0; i < NUM_PASSPOINT_PROVIDERS_WITH_SELF_SIGNED_ROOT_CA; i++) {
            mWifiMetrics.incrementNumPasspointProviderWithSelfSignedRootCa();
        }
        for (int i = 0; i < NUM_PASSPOINT_PROVIDERS_WITH_EXPIRATION_DATE; i++) {
            mWifiMetrics.incrementNumPasspointProviderWithSubscriptionExpiration();
        }
        for (int i = 0; i < NUM_RADIO_MODE_CHANGE_TO_MCC; i++) {
            mWifiMetrics.incrementNumRadioModeChangeToMcc();
        }
        for (int i = 0; i < NUM_RADIO_MODE_CHANGE_TO_SCC; i++) {
            mWifiMetrics.incrementNumRadioModeChangeToScc();
        }
        for (int i = 0; i < NUM_RADIO_MODE_CHANGE_TO_SBS; i++) {
            mWifiMetrics.incrementNumRadioModeChangeToSbs();
        }
        for (int i = 0; i < NUM_RADIO_MODE_CHANGE_TO_DBS; i++) {
            mWifiMetrics.incrementNumRadioModeChangeToDbs();
        }
        for (int i = 0; i < NUM_SOFTAP_USER_BAND_PREFERENCE_UNSATISFIED; i++) {
            mWifiMetrics.incrementNumSoftApUserBandPreferenceUnsatisfied();
        }

        // increment pno scan metrics
        for (int i = 0; i < NUM_PNO_SCAN_ATTEMPTS; i++) {
            mWifiMetrics.incrementPnoScanStartAttemptCount();
        }
        for (int i = 0; i < NUM_PNO_SCAN_FAILED; i++) {
            mWifiMetrics.incrementPnoScanFailedCount();
        }
        for (int i = 0; i < NUM_PNO_FOUND_NETWORK_EVENTS; i++) {
            mWifiMetrics.incrementPnoFoundNetworkEventCount();
        }
        for (int i = 0; i < NUM_BSSID_SELECTION_DIFFERENT_BETWEEN_FRAMEWORK_FIRMWARE; i++) {
            mWifiMetrics.incrementNumBssidDifferentSelectionBetweenFrameworkAndFirmware();
        }

        // set and increment "connect to network" notification metrics
        for (int i = 0; i < NUM_CONNECT_TO_NETWORK_NOTIFICATIONS.length; i++) {
            int count = NUM_CONNECT_TO_NETWORK_NOTIFICATIONS[i];
            for (int j = 0; j < count; j++) {
                mWifiMetrics.incrementConnectToNetworkNotification(OPEN_NET_NOTIFIER_TAG, i);
            }
        }
        for (int i = 0; i < NUM_CONNECT_TO_NETWORK_NOTIFICATION_ACTIONS.length; i++) {
            int[] actions = NUM_CONNECT_TO_NETWORK_NOTIFICATION_ACTIONS[i];
            for (int j = 0; j < actions.length; j++) {
                int count = actions[j];
                for (int k = 0; k < count; k++) {
                    mWifiMetrics.incrementConnectToNetworkNotificationAction(OPEN_NET_NOTIFIER_TAG,
                            i, j);
                }
            }
        }
        mWifiMetrics.setNetworkRecommenderBlocklistSize(OPEN_NET_NOTIFIER_TAG,
                SIZE_OPEN_NETWORK_RECOMMENDER_BLOCKLIST);
        mWifiMetrics.setIsWifiNetworksAvailableNotificationEnabled(OPEN_NET_NOTIFIER_TAG,
                IS_WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON);
        for (int i = 0; i < NUM_OPEN_NETWORK_RECOMMENDATION_UPDATES; i++) {
            mWifiMetrics.incrementNumNetworkRecommendationUpdates(OPEN_NET_NOTIFIER_TAG);
        }
        for (int i = 0; i < NUM_OPEN_NETWORK_CONNECT_MESSAGE_FAILED_TO_SEND; i++) {
            mWifiMetrics.incrementNumNetworkConnectMessageFailedToSend(OPEN_NET_NOTIFIER_TAG);
        }

        addSoftApEventsToMetrics();

        for (int i = 0; i < NUM_ONESHOT_SCAN_REQUESTS_WITH_DFS_CHANNELS; i++) {
            mWifiMetrics.incrementOneshotScanWithDfsCount();
        }
        for (int i = 0; i < NUM_ADD_OR_UPDATE_NETWORK_CALLS; i++) {
            mWifiMetrics.incrementNumAddOrUpdateNetworkCalls();
        }
        for (int i = 0; i < NUM_ENABLE_NETWORK_CALLS; i++) {
            mWifiMetrics.incrementNumEnableNetworkCalls();
        }
        for (int i = 0; i < NUM_IP_RENEWAL_FAILURE; i++) {
            mWifiMetrics.incrementIpRenewalFailure();
        }

        mWifiMetrics.setWatchdogSuccessTimeDurationMs(NUM_WATCHDOG_SUCCESS_DURATION_MS);
        mResources.setBoolean(R.bool.config_wifi_connected_mac_randomization_supported,
                IS_MAC_RANDOMIZATION_ON);

        addWifiPowerMetrics();

        addWifiHealthMetrics();

        mResources.setBoolean(R.bool.config_wifiLinkSpeedMetricsEnabled,
                LINK_SPEED_COUNTS_LOGGING_SETTING);
        mResources.setInteger(R.integer.config_wifiDataStallMinTxBad,
                DATA_STALL_MIN_TX_BAD_SETTING);
        mResources.setInteger(R.integer.config_wifiDataStallMinTxSuccessWithoutRx,
                DATA_STALL_MIN_TX_SUCCESS_WITHOUT_RX_SETTING);

        for (int i = 0; i < NUM_BSSID_FILTERED_DUE_TO_MBO_ASSOC_DISALLOW_IND; i++) {
            mWifiMetrics.incrementNetworkSelectionFilteredBssidCountDueToMboAssocDisallowInd();
        }
        for (int i = 0; i < NUM_STEERING_REQUEST; i++) {
            mWifiMetrics.incrementSteeringRequestCount();
        }
        for (int i = 0; i < NUM_FORCE_SCAN_DUE_TO_STEERING_REQUEST; i++) {
            mWifiMetrics.incrementForceScanCountDueToSteeringRequest();
        }
        for (int i = 0; i < NUM_MBO_CELLULAR_SWITCH_REQUEST; i++) {
            mWifiMetrics.incrementMboCellularSwitchRequestCount();
        }
        for (int i = 0; i < NUM_STEERING_REQUEST_INCLUDING_MBO_ASSOC_RETRY_DELAY; i++) {
            mWifiMetrics.incrementSteeringRequestCountIncludingMboAssocRetryDelay();
        }
        for (int i = 0; i < NUM_CONNECT_REQUEST_WITH_FILS_AKM; i++) {
            mWifiMetrics.incrementConnectRequestWithFilsAkmCount();
        }
        for (int i = 0; i < NUM_L2_CONNECTION_THROUGH_FILS_AUTHENTICATION; i++) {
            mWifiMetrics.incrementL2ConnectionThroughFilsAuthCount();
        }
    }

    private void addWifiPowerMetrics() {
        WifiRadioUsage wifiRadioUsage = new WifiRadioUsage();
        wifiRadioUsage.loggingDurationMs = WIFI_POWER_METRICS_LOGGING_DURATION;
        wifiRadioUsage.scanTimeMs = WIFI_POWER_METRICS_SCAN_TIME;
        when(mWifiPowerMetrics.buildWifiRadioUsageProto()).thenReturn(wifiRadioUsage);
    }

    private void addWifiHealthMetrics() {
        HealthMonitorMetrics metrics = new HealthMonitorMetrics();
        metrics.failureStatsIncrease = new HealthMonitorFailureStats();
        metrics.failureStatsDecrease = new HealthMonitorFailureStats();
        metrics.failureStatsHigh = new HealthMonitorFailureStats();
        metrics.failureStatsIncrease.cntAssocRejection = NUM_NETWORK_ABNORMAL_ASSOC_REJECTION;
        metrics.failureStatsDecrease.cntDisconnectionNonlocalConnecting =
                NUM_NETWORK_ABNORMAL_CONNECTION_FAILURE_DISCONNECTION;
        metrics.numNetworkSufficientRecentStatsOnly = NUM_NETWORK_SUFFICIENT_RECENT_STATS_ONLY;
        metrics.numNetworkSufficientRecentPrevStats = NUM_NETWORK_SUFFICIENT_RECENT_PREV_STATS;
        when(mWifiHealthMonitor.buildProto()).thenReturn(metrics);
        when(mWifiHealthMonitor.getWifiStackVersion()).thenReturn(WIFI_MAINLINE_MODULE_VERSION);
    }

    private void addSoftApEventsToMetrics() {
        SoftApInfo testSoftApInfo_2G = new SoftApInfo();
        testSoftApInfo_2G.setFrequency(SOFT_AP_CHANNEL_FREQUENCY_2G);
        testSoftApInfo_2G.setBandwidth(SOFT_AP_CHANNEL_BANDWIDTH_2G);
        testSoftApInfo_2G.setWifiStandard(SOFT_AP_GENERATION_2G);
        SoftApInfo testSoftApInfo_5G = new SoftApInfo();
        testSoftApInfo_5G.setFrequency(SOFT_AP_CHANNEL_FREQUENCY_5G);
        testSoftApInfo_5G.setBandwidth(SOFT_AP_CHANNEL_BANDWIDTH_5G);
        testSoftApInfo_5G.setWifiStandard(SOFT_AP_GENERATION_5G);

        // Total number of events recorded is NUM_SOFT_AP_EVENT_ENTRIES in both modes
        mWifiMetrics.addSoftApUpChangedEvent(true, WifiManager.IFACE_IP_MODE_TETHERED,
                SOFT_AP_SHUTDOWN_TIMEOUT_DEFAULT_SETTING, false);
        mWifiMetrics.addSoftApNumAssociatedStationsChangedEvent(NUM_SOFT_AP_ASSOCIATED_STATIONS,
                NUM_SOFT_AP_ASSOCIATED_STATIONS, WifiManager.IFACE_IP_MODE_TETHERED,
                testSoftApInfo_2G);

        // Should be dropped.
        mWifiMetrics.addSoftApNumAssociatedStationsChangedEvent(NUM_SOFT_AP_ASSOCIATED_STATIONS,
                NUM_SOFT_AP_ASSOCIATED_STATIONS, WifiManager.IFACE_IP_MODE_UNSPECIFIED,
                testSoftApInfo_2G);

        mWifiMetrics.addSoftApUpChangedEvent(false, WifiManager.IFACE_IP_MODE_TETHERED,
                SOFT_AP_SHUTDOWN_TIMEOUT_DEFAULT_SETTING, false);



        // Channel switch info should be added to the last Soft AP UP event in the list
        mWifiMetrics.addSoftApChannelSwitchedEvent(List.of(testSoftApInfo_2G),
                WifiManager.IFACE_IP_MODE_TETHERED, false);
        SoftApConfiguration testSoftApConfig = new SoftApConfiguration.Builder()
                .setSsid("Test_Metric_SSID")
                .setMaxNumberOfClients(SOFT_AP_MAX_CLIENT_SETTING)
                .setShutdownTimeoutMillis(SOFT_AP_SHUTDOWN_TIMEOUT_SETTING)
                .setClientControlByUserEnabled(SOFT_AP_CLIENT_CONTROL_ENABLE)
                .build();
        mWifiMetrics.updateSoftApConfiguration(testSoftApConfig,
                WifiManager.IFACE_IP_MODE_TETHERED, false);
        SoftApCapability testSoftApCapability = new SoftApCapability(0);
        testSoftApCapability.setMaxSupportedClients(SOFT_AP_MAX_CLIENT_CAPABILITY);
        mWifiMetrics.updateSoftApCapability(testSoftApCapability,
                WifiManager.IFACE_IP_MODE_TETHERED, false);

        mWifiMetrics.addSoftApUpChangedEvent(true, WifiManager.IFACE_IP_MODE_LOCAL_ONLY,
                SOFT_AP_SHUTDOWN_TIMEOUT_DEFAULT_SETTING, false);
        mWifiMetrics.addSoftApNumAssociatedStationsChangedEvent(NUM_SOFT_AP_ASSOCIATED_STATIONS,
                NUM_SOFT_AP_ASSOCIATED_STATIONS, WifiManager.IFACE_IP_MODE_LOCAL_ONLY,
                testSoftApInfo_2G);

        // Should be dropped.
        mWifiMetrics.addSoftApUpChangedEvent(false, WifiManager.IFACE_IP_MODE_CONFIGURATION_ERROR,
                SOFT_AP_SHUTDOWN_TIMEOUT_DEFAULT_SETTING, false);
        mWifiMetrics.addSoftApUpChangedEvent(false, WifiManager.IFACE_IP_MODE_LOCAL_ONLY,
                SOFT_AP_SHUTDOWN_TIMEOUT_DEFAULT_SETTING, false);

        // Bridged mode test, total NUM_SOFT_AP_EVENT_ENTRIES_FOR_BRIDGED_AP events for bridged mode
        mWifiMetrics.addSoftApUpChangedEvent(true, WifiManager.IFACE_IP_MODE_TETHERED,
                SOFT_AP_SHUTDOWN_TIMEOUT_DEFAULT_SETTING, true);
        mWifiMetrics.addSoftApChannelSwitchedEvent(
                List.of(testSoftApInfo_2G, testSoftApInfo_5G),
                WifiManager.IFACE_IP_MODE_TETHERED, true);

        mWifiMetrics.updateSoftApConfiguration(testSoftApConfig,
                WifiManager.IFACE_IP_MODE_TETHERED, true);
        mWifiMetrics.updateSoftApCapability(testSoftApCapability,
                WifiManager.IFACE_IP_MODE_TETHERED, true);

        mWifiMetrics.addSoftApInstanceDownEventInDualMode(WifiManager.IFACE_IP_MODE_TETHERED,
                testSoftApInfo_5G);
        mWifiMetrics.addSoftApUpChangedEvent(false, WifiManager.IFACE_IP_MODE_TETHERED,
                SOFT_AP_SHUTDOWN_TIMEOUT_DEFAULT_SETTING, true);
    }

    private void verifySoftApEventsStoredInProto() {
        // Tethered mode includes single AP and dual AP test.
        assertEquals(NUM_SOFT_AP_EVENT_ENTRIES + NUM_SOFT_AP_EVENT_ENTRIES_FOR_BRIDGED_AP,
                mDecodedProto.softApConnectedClientsEventsTethered.length);
        assertEquals(SoftApConnectedClientsEvent.SOFT_AP_UP,
                mDecodedProto.softApConnectedClientsEventsTethered[0].eventType);
        assertEquals(0, mDecodedProto.softApConnectedClientsEventsTethered[0].numConnectedClients);
        assertEquals(SOFT_AP_CHANNEL_FREQUENCY_2G,
                mDecodedProto.softApConnectedClientsEventsTethered[0].channelFrequency);
        assertEquals(SOFT_AP_CHANNEL_BANDWIDTH_2G,
                mDecodedProto.softApConnectedClientsEventsTethered[0].channelBandwidth);
        assertEquals(SOFT_AP_MAX_CLIENT_SETTING,
                mDecodedProto.softApConnectedClientsEventsTethered[0]
                .maxNumClientsSettingInSoftapConfiguration);
        assertEquals(SOFT_AP_MAX_CLIENT_CAPABILITY,
                mDecodedProto.softApConnectedClientsEventsTethered[0]
                .maxNumClientsSettingInSoftapCapability);
        assertEquals(SOFT_AP_SHUTDOWN_TIMEOUT_SETTING,
                mDecodedProto.softApConnectedClientsEventsTethered[0]
                .shutdownTimeoutSettingInSoftapConfiguration);
        assertEquals(SOFT_AP_SHUTDOWN_TIMEOUT_DEFAULT_SETTING,
                mDecodedProto.softApConnectedClientsEventsTethered[0]
                .defaultShutdownTimeoutSetting);
        assertEquals(SOFT_AP_CLIENT_CONTROL_ENABLE,
                mDecodedProto.softApConnectedClientsEventsTethered[0].clientControlIsEnabled);

        assertEquals(SoftApConnectedClientsEvent.NUM_CLIENTS_CHANGED,
                mDecodedProto.softApConnectedClientsEventsTethered[1].eventType);
        assertEquals(NUM_SOFT_AP_ASSOCIATED_STATIONS,
                mDecodedProto.softApConnectedClientsEventsTethered[1].numConnectedClients);
        assertEquals(SoftApConnectedClientsEvent.SOFT_AP_DOWN,
                mDecodedProto.softApConnectedClientsEventsTethered[2].eventType);
        assertEquals(0, mDecodedProto.softApConnectedClientsEventsTethered[2].numConnectedClients);

        // Verify the bridged AP metrics
        assertEquals(SoftApConnectedClientsEvent.DUAL_AP_BOTH_INSTANCES_UP,
                mDecodedProto.softApConnectedClientsEventsTethered[3].eventType);
        assertEquals(0, mDecodedProto.softApConnectedClientsEventsTethered[3].numConnectedClients);
        assertEquals(SOFT_AP_CHANNEL_FREQUENCY_2G,
                mDecodedProto.softApConnectedClientsEventsTethered[3].channelFrequency);
        assertEquals(SOFT_AP_CHANNEL_BANDWIDTH_2G,
                mDecodedProto.softApConnectedClientsEventsTethered[3].channelBandwidth);
        assertEquals(SOFT_AP_MAX_CLIENT_SETTING,
                mDecodedProto.softApConnectedClientsEventsTethered[3]
                .maxNumClientsSettingInSoftapConfiguration);
        assertEquals(SOFT_AP_MAX_CLIENT_CAPABILITY,
                mDecodedProto.softApConnectedClientsEventsTethered[3]
                .maxNumClientsSettingInSoftapCapability);
        assertEquals(SOFT_AP_SHUTDOWN_TIMEOUT_SETTING,
                mDecodedProto.softApConnectedClientsEventsTethered[3]
                .shutdownTimeoutSettingInSoftapConfiguration);
        assertEquals(SOFT_AP_SHUTDOWN_TIMEOUT_DEFAULT_SETTING,
                mDecodedProto.softApConnectedClientsEventsTethered[3]
                .defaultShutdownTimeoutSetting);
        assertEquals(SOFT_AP_CLIENT_CONTROL_ENABLE,
                mDecodedProto.softApConnectedClientsEventsTethered[3].clientControlIsEnabled);
        assertEquals(SoftApConnectedClientsEvent.DUAL_AP_BOTH_INSTANCES_UP,
                mDecodedProto.softApConnectedClientsEventsTethered[4].eventType);
        assertEquals(0, mDecodedProto.softApConnectedClientsEventsTethered[4].numConnectedClients);
        assertEquals(SOFT_AP_CHANNEL_FREQUENCY_5G,
                mDecodedProto.softApConnectedClientsEventsTethered[4].channelFrequency);
        assertEquals(SOFT_AP_CHANNEL_BANDWIDTH_5G,
                mDecodedProto.softApConnectedClientsEventsTethered[4].channelBandwidth);
        assertEquals(SOFT_AP_MAX_CLIENT_SETTING,
                mDecodedProto.softApConnectedClientsEventsTethered[4]
                .maxNumClientsSettingInSoftapConfiguration);
        assertEquals(SOFT_AP_MAX_CLIENT_CAPABILITY,
                mDecodedProto.softApConnectedClientsEventsTethered[4]
                .maxNumClientsSettingInSoftapCapability);
        assertEquals(SOFT_AP_SHUTDOWN_TIMEOUT_SETTING,
                mDecodedProto.softApConnectedClientsEventsTethered[4]
                .shutdownTimeoutSettingInSoftapConfiguration);
        assertEquals(SOFT_AP_SHUTDOWN_TIMEOUT_DEFAULT_SETTING,
                mDecodedProto.softApConnectedClientsEventsTethered[4]
                .defaultShutdownTimeoutSetting);
        assertEquals(SOFT_AP_CLIENT_CONTROL_ENABLE,
                mDecodedProto.softApConnectedClientsEventsTethered[4].clientControlIsEnabled);
        assertEquals(SoftApConnectedClientsEvent.DUAL_AP_ONE_INSTANCE_DOWN,
                mDecodedProto.softApConnectedClientsEventsTethered[5].eventType);
        assertEquals(0, mDecodedProto.softApConnectedClientsEventsTethered[5].numConnectedClients);
        assertEquals(SoftApConnectedClientsEvent.SOFT_AP_DOWN,
                mDecodedProto.softApConnectedClientsEventsTethered[6].eventType);
        assertEquals(0, mDecodedProto.softApConnectedClientsEventsTethered[6].numConnectedClients);

        assertEquals(SoftApConnectedClientsEvent.SOFT_AP_UP,
                mDecodedProto.softApConnectedClientsEventsLocalOnly[0].eventType);
        assertEquals(0, mDecodedProto.softApConnectedClientsEventsLocalOnly[0].numConnectedClients);
        assertEquals(SoftApConnectedClientsEvent.NUM_CLIENTS_CHANGED,
                mDecodedProto.softApConnectedClientsEventsLocalOnly[1].eventType);
        assertEquals(NUM_SOFT_AP_ASSOCIATED_STATIONS,
                mDecodedProto.softApConnectedClientsEventsLocalOnly[1].numConnectedClients);
        assertEquals(SoftApConnectedClientsEvent.SOFT_AP_DOWN,
                mDecodedProto.softApConnectedClientsEventsLocalOnly[2].eventType);
        assertEquals(0, mDecodedProto.softApConnectedClientsEventsLocalOnly[2].numConnectedClients);
    }

    /**
     * Assert that values in deserializedWifiMetrics match those set in 'setAndIncrementMetrics'
     */
    private void assertDeserializedMetricsCorrect() throws Exception {
        assertEquals("mDecodedProto.numSavedNetworks == NUM_SAVED_NETWORKS",
                NUM_SAVED_NETWORKS, mDecodedProto.numSavedNetworks);
        assertEquals("mDecodedProto.numSavedNetworksWithMacRandomization == NUM_SAVED_NETWORKS-1",
                NUM_SAVED_NETWORKS - 1, mDecodedProto.numSavedNetworksWithMacRandomization);
        assertEquals("mDecodedProto.numOpenNetworks == NUM_OPEN_NETWORKS",
                NUM_OPEN_NETWORKS, mDecodedProto.numOpenNetworks);
        assertEquals("mDecodedProto.numLegacyPersonalNetworks == NUM_LEGACY_PERSONAL_NETWORKS",
                NUM_LEGACY_PERSONAL_NETWORKS, mDecodedProto.numLegacyPersonalNetworks);
        assertEquals(
                "mDecodedProto.numLegacyEnterpriseNetworks == NUM_LEGACY_ENTERPRISE_NETWORKS",
                NUM_LEGACY_ENTERPRISE_NETWORKS, mDecodedProto.numLegacyEnterpriseNetworks);
        assertEquals("mDecodedProto.numEnhancedOpenNetworks == NUM_ENHANCED_OPEN_NETWORKS",
                NUM_ENHANCED_OPEN_NETWORKS, mDecodedProto.numEnhancedOpenNetworks);
        assertEquals("mDecodedProto.numWpa3PersonalNetworks == NUM_WPA3_PERSONAL_NETWORKS",
                NUM_WPA3_PERSONAL_NETWORKS, mDecodedProto.numWpa3PersonalNetworks);
        assertEquals("mDecodedProto.numWpa3EnterpriseNetworks == NUM_WPA3_ENTERPRISE_NETWORKS",
                NUM_WPA3_ENTERPRISE_NETWORKS, mDecodedProto.numWpa3EnterpriseNetworks);
        assertEquals("mDecodedProto.numWapiPersonalNetworks == NUM_WAPI_PERSONAL_NETWORKS",
                NUM_WAPI_PERSONAL_NETWORKS, mDecodedProto.numWapiPersonalNetworks);
        assertEquals("mDecodedProto.numWapiEnterpriseNetworks == NUM_WAPI_ENTERPRISE_NETWORKS",
                NUM_WAPI_ENTERPRISE_NETWORKS, mDecodedProto.numWapiEnterpriseNetworks);
        assertEquals("mDecodedProto.numNetworksAddedByUser == NUM_NETWORKS_ADDED_BY_USER",
                NUM_NETWORKS_ADDED_BY_USER, mDecodedProto.numNetworksAddedByUser);
        assertEquals(NUM_HIDDEN_NETWORKS, mDecodedProto.numHiddenNetworks);
        assertEquals(NUM_PASSPOINT_NETWORKS, mDecodedProto.numPasspointNetworks);
        assertEquals("mDecodedProto.numNetworksAddedByApps == NUM_NETWORKS_ADDED_BY_APPS",
                NUM_NETWORKS_ADDED_BY_APPS, mDecodedProto.numNetworksAddedByApps);
        assertEquals("mDecodedProto.isLocationEnabled == TEST_VAL_IS_LOCATION_ENABLED",
                TEST_VAL_IS_LOCATION_ENABLED, mDecodedProto.isLocationEnabled);
        assertEquals("mDecodedProto.isScanningAlwaysEnabled == IS_SCANNING_ALWAYS_ENABLED",
                IS_SCANNING_ALWAYS_ENABLED, mDecodedProto.isScanningAlwaysEnabled);
        assertEquals(IS_VERBOSE_LOGGING_ENABLED, mDecodedProto.isVerboseLoggingEnabled);
        assertEquals(IS_NON_PERSISTENT_MAC_RANDOMIZATION_FORCE_ENABLED,
                mDecodedProto.isEnhancedMacRandomizationForceEnabled);
        assertEquals(IS_WIFI_WAKE_ENABLED, mDecodedProto.isWifiWakeEnabled);
        assertEquals("mDecodedProto.numEmptyScanResults == NUM_EMPTY_SCAN_RESULTS",
                NUM_EMPTY_SCAN_RESULTS, mDecodedProto.numEmptyScanResults);
        assertEquals("mDecodedProto.numNonEmptyScanResults == NUM_NON_EMPTY_SCAN_RESULTS",
                NUM_NON_EMPTY_SCAN_RESULTS, mDecodedProto.numNonEmptyScanResults);
        assertScanReturnEntryEquals(WifiMetricsProto.WifiLog.SCAN_UNKNOWN, NUM_SCAN_UNKNOWN);
        assertScanReturnEntryEquals(WifiMetricsProto.WifiLog.SCAN_SUCCESS, NUM_SCAN_SUCCESS);
        assertScanReturnEntryEquals(WifiMetricsProto.WifiLog.SCAN_FAILURE_INTERRUPTED,
                NUM_SCAN_FAILURE_INTERRUPTED);
        assertScanReturnEntryEquals(WifiMetricsProto.WifiLog.SCAN_FAILURE_INVALID_CONFIGURATION,
                NUM_SCAN_FAILURE_INVALID_CONFIGURATION);
        assertSystemStateEntryEquals(WifiMetricsProto.WifiLog.WIFI_UNKNOWN, false,
                NUM_WIFI_UNKNOWN_SCREEN_OFF);
        assertSystemStateEntryEquals(WifiMetricsProto.WifiLog.WIFI_UNKNOWN, true,
                NUM_WIFI_UNKNOWN_SCREEN_ON);
        assertSystemStateEntryEquals(
                WifiMetricsProto.WifiLog.WIFI_ASSOCIATED, false, NUM_WIFI_ASSOCIATED_SCREEN_OFF);
        assertSystemStateEntryEquals(WifiMetricsProto.WifiLog.WIFI_ASSOCIATED, true,
                NUM_WIFI_ASSOCIATED_SCREEN_ON);
        assertEquals(NUM_CONNECTIVITY_WATCHDOG_PNO_GOOD,
                mDecodedProto.numConnectivityWatchdogPnoGood);
        assertEquals(NUM_CONNECTIVITY_WATCHDOG_PNO_BAD,
                mDecodedProto.numConnectivityWatchdogPnoBad);
        assertEquals(NUM_CONNECTIVITY_WATCHDOG_BACKGROUND_GOOD,
                mDecodedProto.numConnectivityWatchdogBackgroundGood);
        assertEquals(NUM_CONNECTIVITY_WATCHDOG_BACKGROUND_BAD,
                mDecodedProto.numConnectivityWatchdogBackgroundBad);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_TRIGGERS,
                mDecodedProto.numLastResortWatchdogTriggers);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_BAD_ASSOCIATION_NETWORKS_TOTAL,
                mDecodedProto.numLastResortWatchdogBadAssociationNetworksTotal);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_BAD_AUTHENTICATION_NETWORKS_TOTAL,
                mDecodedProto.numLastResortWatchdogBadAuthenticationNetworksTotal);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_BAD_DHCP_NETWORKS_TOTAL,
                mDecodedProto.numLastResortWatchdogBadDhcpNetworksTotal);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_BAD_OTHER_NETWORKS_TOTAL,
                mDecodedProto.numLastResortWatchdogBadOtherNetworksTotal);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_AVAILABLE_NETWORKS_TOTAL,
                mDecodedProto.numLastResortWatchdogAvailableNetworksTotal);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_ASSOCIATION,
                mDecodedProto.numLastResortWatchdogTriggersWithBadAssociation);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_AUTHENTICATION,
                mDecodedProto.numLastResortWatchdogTriggersWithBadAuthentication);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_DHCP,
                mDecodedProto.numLastResortWatchdogTriggersWithBadDhcp);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_OTHER,
                mDecodedProto.numLastResortWatchdogTriggersWithBadOther);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_SUCCESSES,
                mDecodedProto.numLastResortWatchdogSuccesses);
        assertEquals(WATCHDOG_TOTAL_CONNECTION_FAILURE_COUNT_AFTER_TRIGGER,
                mDecodedProto.watchdogTotalConnectionFailureCountAfterTrigger);
        assertEquals(TEST_RECORD_DURATION_SEC,
                mDecodedProto.recordDurationSec);
        for (int i = 0; i < NUM_RSSI_LEVELS_TO_INCREMENT; i++) {
            assertEquals(RSSI_POLL_FREQUENCY,
                    mDecodedProto.rssiPollRssiCount[i].frequency);
            assertEquals(MIN_RSSI_LEVEL + i, mDecodedProto.rssiPollRssiCount[i].rssi);
            assertEquals(i + 1, mDecodedProto.rssiPollRssiCount[i].count);
        }
        StringBuilder sb_rssi = new StringBuilder();
        sb_rssi.append("Number of RSSIs = " + mDecodedProto.rssiPollRssiCount.length);
        assertTrue(sb_rssi.toString(), (mDecodedProto.rssiPollRssiCount.length
                     <= (MAX_RSSI_LEVEL - MIN_RSSI_LEVEL + 1)));
        assertEquals(2, mDecodedProto.alertReasonCount[0].count);  // Clamped reasons.
        assertEquals(3, mDecodedProto.alertReasonCount[1].count);
        assertEquals(1, mDecodedProto.alertReasonCount[2].count);
        assertEquals(3, mDecodedProto.alertReasonCount.length);
        assertEquals(NUM_TOTAL_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numTotalScanResults);
        assertEquals(NUM_OPEN_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numOpenNetworkScanResults);
        assertEquals(NUM_LEGACY_PERSONAL_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numLegacyPersonalNetworkScanResults);
        assertEquals(NUM_LEGACY_ENTERPRISE_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numLegacyEnterpriseNetworkScanResults);
        assertEquals(NUM_ENHANCED_OPEN_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numEnhancedOpenNetworkScanResults);
        assertEquals(NUM_WPA3_PERSONAL_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numWpa3PersonalNetworkScanResults);
        assertEquals(NUM_WPA3_ENTERPRISE_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numWpa3EnterpriseNetworkScanResults);
        assertEquals(NUM_WAPI_PERSONAL_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numWapiPersonalNetworkScanResults);
        assertEquals(NUM_WAPI_ENTERPRISE_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numWapiEnterpriseNetworkScanResults);
        assertEquals(NUM_HIDDEN_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numHiddenNetworkScanResults);
        assertEquals(NUM_HOTSPOT2_R1_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numHotspot2R1NetworkScanResults);
        assertEquals(NUM_HOTSPOT2_R2_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numHotspot2R2NetworkScanResults);
        assertEquals(NUM_HOTSPOT2_R3_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numHotspot2R3NetworkScanResults);

        assertEquals(NUM_MBO_SUPPORTED_NETWORKS_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numMboSupportedNetworkScanResults);
        assertEquals(NUM_MBO_CELL_DATA_AWARE_NETWORKS_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numMboCellularDataAwareNetworkScanResults);
        assertEquals(NUM_OCE_SUPPORTED_NETWORKS_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numOceSupportedNetworkScanResults);
        assertEquals(NUM_FILS_SUPPORTED_NETWORKS_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.numFilsSupportedNetworkScanResults);
        assertEquals(NUM_11AX_NETWORKS_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.num11AxNetworkScanResults);
        assertEquals(NUM_6G_NETWORKS_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.num6GNetworkScanResults);
        assertEquals(NUM_6G_PSC_NETWORKS_SCAN_RESULTS * NUM_SCANS,
                mDecodedProto.num6GPscNetworkScanResults);
        assertEquals(NUM_SCANS,
                mDecodedProto.numScans);
        assertEquals(NUM_CONNECTIVITY_ONESHOT_SCAN_EVENT,
                mDecodedProto.numConnectivityOneshotScans);
        assertEquals(NUM_EXTERNAL_APP_ONESHOT_SCAN_REQUESTS,
                mDecodedProto.numExternalAppOneshotScanRequests);
        assertEquals(NUM_EXTERNAL_FOREGROUND_APP_ONESHOT_SCAN_REQUESTS_THROTTLED,
                mDecodedProto.numExternalForegroundAppOneshotScanRequestsThrottled);
        assertEquals(NUM_EXTERNAL_BACKGROUND_APP_ONESHOT_SCAN_REQUESTS_THROTTLED,
                mDecodedProto.numExternalBackgroundAppOneshotScanRequestsThrottled);

        for (int score_index = 0; score_index < NUM_WIFI_SCORES_TO_INCREMENT; score_index++) {
            assertEquals(WIFI_SCORE_RANGE_MIN + score_index,
                    mDecodedProto.wifiScoreCount[score_index].score);
            assertEquals(WIFI_SCORE_RANGE_MIN + score_index + 1,
                    mDecodedProto.wifiScoreCount[score_index].count);
            assertEquals(WIFI_SCORE_RANGE_MIN + score_index,
                    mDecodedProto.wifiUsabilityScoreCount[score_index].score);
            assertEquals(WIFI_SCORE_RANGE_MIN + score_index + 1,
                    mDecodedProto.wifiUsabilityScoreCount[score_index].count);
        }
        StringBuilder sb_wifi_score = new StringBuilder();
        sb_wifi_score.append("Number of wifi_scores = " + mDecodedProto.wifiScoreCount.length);
        assertTrue(sb_wifi_score.toString(), (mDecodedProto.wifiScoreCount.length
                <= (WIFI_SCORE_RANGE_MAX - WIFI_SCORE_RANGE_MIN + 1)));
        StringBuilder sb_wifi_limits = new StringBuilder();
        sb_wifi_limits.append("Wifi Score limit is " +  ConnectedScore.WIFI_MAX_SCORE
                + ">= " + WIFI_SCORE_RANGE_MAX);
        assertTrue(sb_wifi_limits.toString(),
                ConnectedScore.WIFI_MAX_SCORE <= WIFI_SCORE_RANGE_MAX);
        StringBuilder sb_wifi_usability_score = new StringBuilder();
        sb_wifi_usability_score.append("Number of wifi_usability_scores = "
                + mDecodedProto.wifiUsabilityScoreCount.length);
        assertTrue(sb_wifi_usability_score.toString(), (mDecodedProto.wifiUsabilityScoreCount.length
                <= (WIFI_SCORE_RANGE_MAX - WIFI_SCORE_RANGE_MIN + 1)));
        StringBuilder sb_wifi_usablity_limits = new StringBuilder();
        sb_wifi_limits.append("Wifi Usability Score limit is " +  ConnectedScore.WIFI_MAX_SCORE
                + ">= " + WIFI_SCORE_RANGE_MAX);
        assertTrue(sb_wifi_limits.toString(),
                ConnectedScore.WIFI_MAX_SCORE <= WIFI_SCORE_RANGE_MAX);
        assertEquals(MAX_NUM_SOFTAP_RETURN_CODES, mDecodedProto.softApReturnCode.length);
        assertEquals(WifiMetricsProto.SoftApReturnCodeCount.SOFT_AP_STARTED_SUCCESSFULLY,
                     mDecodedProto.softApReturnCode[0].startResult);
        assertEquals(NUM_SOFTAP_START_SUCCESS, mDecodedProto.softApReturnCode[0].count);
        assertEquals(WifiMetricsProto.SoftApReturnCodeCount.SOFT_AP_FAILED_GENERAL_ERROR,
                     mDecodedProto.softApReturnCode[1].startResult);
        assertEquals(NUM_SOFTAP_FAILED_GENERAL_ERROR,
                     mDecodedProto.softApReturnCode[1].count);
        assertEquals(WifiMetricsProto.SoftApReturnCodeCount.SOFT_AP_FAILED_NO_CHANNEL,
                     mDecodedProto.softApReturnCode[2].startResult);
        assertEquals(NUM_SOFTAP_FAILED_NO_CHANNEL,
                     mDecodedProto.softApReturnCode[2].count);
        assertEquals(NUM_HAL_CRASHES, mDecodedProto.numHalCrashes);
        assertEquals(NUM_WIFICOND_CRASHES, mDecodedProto.numWificondCrashes);
        assertEquals(NUM_SUPPLICANT_CRASHES, mDecodedProto.numSupplicantCrashes);
        assertEquals(NUM_HOSTAPD_CRASHES, mDecodedProto.numHostapdCrashes);
        assertEquals(NUM_WIFI_ON_FAILURE_DUE_TO_HAL,
                mDecodedProto.numSetupClientInterfaceFailureDueToHal);
        assertEquals(NUM_WIFI_ON_FAILURE_DUE_TO_WIFICOND,
                mDecodedProto.numSetupClientInterfaceFailureDueToWificond);
        assertEquals(NUM_WIFI_ON_FAILURE_DUE_TO_SUPPLICANT,
                mDecodedProto.numSetupClientInterfaceFailureDueToSupplicant);
        assertEquals(NUM_SOFTAP_ON_FAILURE_DUE_TO_HAL,
                mDecodedProto.numSetupSoftApInterfaceFailureDueToHal);
        assertEquals(NUM_SOFTAP_ON_FAILURE_DUE_TO_WIFICOND,
                mDecodedProto.numSetupSoftApInterfaceFailureDueToWificond);
        assertEquals(NUM_SOFTAP_ON_FAILURE_DUE_TO_HOSTAPD,
                mDecodedProto.numSetupSoftApInterfaceFailureDueToHostapd);
        assertEquals(NUM_CLIENT_INTERFACE_DOWN, mDecodedProto.numClientInterfaceDown);
        assertEquals(NUM_SOFTAP_INTERFACE_DOWN, mDecodedProto.numSoftApInterfaceDown);
        assertEquals(NUM_PASSPOINT_PROVIDERS, mDecodedProto.numPasspointProviders);
        assertPasspointProfileTypeCount(mDecodedProto.installedPasspointProfileTypeForR1);
        assertPasspointProfileTypeCount(mDecodedProto.installedPasspointProfileTypeForR2);
        assertEquals(NUM_PASSPOINT_PROVIDER_INSTALLATION,
                mDecodedProto.numPasspointProviderInstallation);
        assertEquals(NUM_PASSPOINT_PROVIDER_INSTALL_SUCCESS,
                mDecodedProto.numPasspointProviderInstallSuccess);
        assertEquals(NUM_PASSPOINT_PROVIDER_UNINSTALLATION,
                mDecodedProto.numPasspointProviderUninstallation);
        assertEquals(NUM_PASSPOINT_PROVIDER_UNINSTALL_SUCCESS,
                mDecodedProto.numPasspointProviderUninstallSuccess);
        assertEquals(NUM_PASSPOINT_PROVIDERS_SUCCESSFULLY_CONNECTED,
                mDecodedProto.numPasspointProvidersSuccessfullyConnected);
        assertEquals(NUM_PASSPOINT_PROVIDERS_WITH_NO_ROOT_CA,
                mDecodedProto.numPasspointProviderWithNoRootCa);
        assertEquals(NUM_PASSPOINT_PROVIDERS_WITH_SELF_SIGNED_ROOT_CA,
                mDecodedProto.numPasspointProviderWithSelfSignedRootCa);
        assertEquals(NUM_PASSPOINT_PROVIDERS_WITH_EXPIRATION_DATE,
                mDecodedProto.numPasspointProviderWithSubscriptionExpiration);
        assertEquals(NUM_BSSID_SELECTION_DIFFERENT_BETWEEN_FRAMEWORK_FIRMWARE,
                mDecodedProto.numBssidDifferentSelectionBetweenFrameworkAndFirmware);

        assertEquals(NUM_RADIO_MODE_CHANGE_TO_MCC, mDecodedProto.numRadioModeChangeToMcc);
        assertEquals(NUM_RADIO_MODE_CHANGE_TO_SCC, mDecodedProto.numRadioModeChangeToScc);
        assertEquals(NUM_RADIO_MODE_CHANGE_TO_SBS, mDecodedProto.numRadioModeChangeToSbs);
        assertEquals(NUM_RADIO_MODE_CHANGE_TO_DBS, mDecodedProto.numRadioModeChangeToDbs);
        assertEquals(NUM_SOFTAP_USER_BAND_PREFERENCE_UNSATISFIED,
                mDecodedProto.numSoftApUserBandPreferenceUnsatisfied);

        PnoScanMetrics pno_metrics = mDecodedProto.pnoScanMetrics;
        assertNotNull(pno_metrics);
        assertEquals(NUM_PNO_SCAN_ATTEMPTS, pno_metrics.numPnoScanAttempts);
        assertEquals(NUM_PNO_SCAN_FAILED, pno_metrics.numPnoScanFailed);
        assertEquals(NUM_PNO_FOUND_NETWORK_EVENTS, pno_metrics.numPnoFoundNetworkEvents);

        for (ConnectToNetworkNotificationAndActionCount notificationCount
                : mDecodedProto.connectToNetworkNotificationCount) {
            assertEquals(NUM_CONNECT_TO_NETWORK_NOTIFICATIONS[notificationCount.notification],
                    notificationCount.count);
            assertEquals(ConnectToNetworkNotificationAndActionCount.RECOMMENDER_OPEN,
                    notificationCount.recommender);
        }
        for (ConnectToNetworkNotificationAndActionCount notificationActionCount
                : mDecodedProto.connectToNetworkNotificationActionCount) {
            assertEquals(NUM_CONNECT_TO_NETWORK_NOTIFICATION_ACTIONS
                            [notificationActionCount.notification]
                            [notificationActionCount.action],
                    notificationActionCount.count);
            assertEquals(ConnectToNetworkNotificationAndActionCount.RECOMMENDER_OPEN,
                    notificationActionCount.recommender);
        }

        assertEquals(SIZE_OPEN_NETWORK_RECOMMENDER_BLOCKLIST,
                mDecodedProto.openNetworkRecommenderBlocklistSize);
        assertEquals(IS_WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                mDecodedProto.isWifiNetworksAvailableNotificationOn);
        assertEquals(NUM_OPEN_NETWORK_RECOMMENDATION_UPDATES,
                mDecodedProto.numOpenNetworkRecommendationUpdates);
        assertEquals(NUM_OPEN_NETWORK_CONNECT_MESSAGE_FAILED_TO_SEND,
                mDecodedProto.numOpenNetworkConnectMessageFailedToSend);

        verifySoftApEventsStoredInProto();

        assertEquals(NUM_WATCHDOG_SUCCESS_DURATION_MS,
                mDecodedProto.watchdogTriggerToConnectionSuccessDurationMs);
        assertEquals(IS_MAC_RANDOMIZATION_ON, mDecodedProto.isMacRandomizationOn);
        assertEquals(WIFI_POWER_METRICS_LOGGING_DURATION,
                mDecodedProto.wifiRadioUsage.loggingDurationMs);
        assertEquals(WIFI_POWER_METRICS_SCAN_TIME,
                mDecodedProto.wifiRadioUsage.scanTimeMs);
        assertEquals(LINK_SPEED_COUNTS_LOGGING_SETTING,
                mDecodedProto.experimentValues.linkSpeedCountsLoggingEnabled);
        assertEquals(DATA_STALL_MIN_TX_BAD_SETTING,
                mDecodedProto.experimentValues.wifiDataStallMinTxBad);
        assertEquals(DATA_STALL_MIN_TX_SUCCESS_WITHOUT_RX_SETTING,
                mDecodedProto.experimentValues.wifiDataStallMinTxSuccessWithoutRx);
        assertEquals(NUM_ONESHOT_SCAN_REQUESTS_WITH_DFS_CHANNELS,
                mDecodedProto.numOneshotHasDfsChannelScans);
        assertEquals(NUM_ADD_OR_UPDATE_NETWORK_CALLS, mDecodedProto.numAddOrUpdateNetworkCalls);
        assertEquals(NUM_ENABLE_NETWORK_CALLS, mDecodedProto.numEnableNetworkCalls);
        assertEquals(NUM_IP_RENEWAL_FAILURE, mDecodedProto.numIpRenewalFailure);
        assertEquals(NUM_NETWORK_ABNORMAL_ASSOC_REJECTION,
                mDecodedProto.healthMonitorMetrics.failureStatsIncrease.cntAssocRejection);
        assertEquals(NUM_NETWORK_ABNORMAL_CONNECTION_FAILURE_DISCONNECTION,
                mDecodedProto.healthMonitorMetrics.failureStatsDecrease
                        .cntDisconnectionNonlocalConnecting);
        assertEquals(0,
                mDecodedProto.healthMonitorMetrics.failureStatsIncrease.cntAssocTimeout);
        assertEquals(NUM_NETWORK_SUFFICIENT_RECENT_STATS_ONLY,
                mDecodedProto.healthMonitorMetrics.numNetworkSufficientRecentStatsOnly);
        assertEquals(NUM_NETWORK_SUFFICIENT_RECENT_PREV_STATS,
                mDecodedProto.healthMonitorMetrics.numNetworkSufficientRecentPrevStats);
        assertEquals(NUM_BSSID_FILTERED_DUE_TO_MBO_ASSOC_DISALLOW_IND,
                mDecodedProto.numBssidFilteredDueToMboAssocDisallowInd);
        assertEquals(NUM_STEERING_REQUEST,
                mDecodedProto.numSteeringRequest);
        assertEquals(NUM_FORCE_SCAN_DUE_TO_STEERING_REQUEST,
                mDecodedProto.numForceScanDueToSteeringRequest);
        assertEquals(NUM_MBO_CELLULAR_SWITCH_REQUEST,
                mDecodedProto.numMboCellularSwitchRequest);
        assertEquals(NUM_STEERING_REQUEST_INCLUDING_MBO_ASSOC_RETRY_DELAY,
                mDecodedProto.numSteeringRequestIncludingMboAssocRetryDelay);
        assertEquals(NUM_CONNECT_REQUEST_WITH_FILS_AKM,
                mDecodedProto.numConnectRequestWithFilsAkm);
        assertEquals(NUM_L2_CONNECTION_THROUGH_FILS_AUTHENTICATION,
                mDecodedProto.numL2ConnectionThroughFilsAuthentication);
        assertEquals(WIFI_MAINLINE_MODULE_VERSION, mDecodedProto.mainlineModuleVersion);

    }

    @Test
    public void testHalCrashSoftApFailureCount() throws Exception {
        mWifiMetrics.incrementNumHalCrashes();
        mWifiMetrics.incrementNumSetupSoftApInterfaceFailureDueToHostapd();
        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__SOFT_AP_FAILURE_HOSTAPD));
        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__HAL_CRASH));
    }

    @Test
    public void testSetupP2pInterfaceFailureCount() throws Exception {
        mWifiMetrics.incrementNumSetupP2pInterfaceFailureDueToHal();
        mWifiMetrics.incrementNumSetupP2pInterfaceFailureDueToSupplicant();
        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__P2P_FAILURE_HAL));
        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED,
                WifiStatsLog.WIFI_SETUP_FAILURE_CRASH_REPORTED__TYPE__P2P_FAILURE_SUPPLICANT));
    }

    /**
     *  Assert deserialized metrics Scan Return Entry equals count
     */
    public void assertScanReturnEntryEquals(int returnCode, int count) {
        for (int i = 0; i < mDecodedProto.scanReturnEntries.length; i++) {
            if (mDecodedProto.scanReturnEntries[i].scanReturnCode == returnCode) {
                assertEquals(count, mDecodedProto.scanReturnEntries[i].scanResultsCount);
                return;
            }
        }
        assertEquals(null, count);
    }

    /**
     *  Assert deserialized metrics SystemState entry equals count
     */
    public void assertSystemStateEntryEquals(int state, boolean screenOn, int count) {
        for (int i = 0; i < mDecodedProto.wifiSystemStateEntries.length; i++) {
            if (mDecodedProto.wifiSystemStateEntries[i].wifiState == state
                    && mDecodedProto.wifiSystemStateEntries[i].isScreenOn == screenOn) {
                assertEquals(count, mDecodedProto.wifiSystemStateEntries[i].wifiStateCount);
                return;
            }
        }
        assertEquals(null, count);
    }

    /**
     * Test the number of Passpoint provision with the failure code are collected correctly
     *
     * @throws Exception
     */
    @Test
    public void testPasspointProvisionMetrics() throws Exception {
        //Increment count for provisioning success.
        mWifiMetrics.incrementPasspointProvisionSuccess();

        // Increment count for provisioning unavailable
        mWifiMetrics.incrementPasspointProvisionFailure(
                ProvisioningCallback.OSU_FAILURE_PROVISIONING_NOT_AVAILABLE);
        mWifiMetrics.incrementPasspointProvisionFailure(
                ProvisioningCallback.OSU_FAILURE_PROVISIONING_NOT_AVAILABLE);

        // Increment count for server connection failure
        mWifiMetrics.incrementPasspointProvisionFailure(
                ProvisioningCallback.OSU_FAILURE_AP_CONNECTION);

        // Dump proto and deserialize
        dumpProtoAndDeserialize();

        assertEquals(mDecodedProto.passpointProvisionStats.numProvisionSuccess, 1);
        assertEquals(mDecodedProto.passpointProvisionStats.provisionFailureCount.length, 2);
        assertEquals(mDecodedProto.passpointProvisionStats.provisionFailureCount[0].failureCode,
                PasspointProvisionStats.OSU_FAILURE_AP_CONNECTION);
        assertEquals(mDecodedProto.passpointProvisionStats.provisionFailureCount[0].count, 1);
        assertEquals(mDecodedProto.passpointProvisionStats.provisionFailureCount[1].failureCode,
                PasspointProvisionStats.OSU_FAILURE_PROVISIONING_NOT_AVAILABLE);
        assertEquals(mDecodedProto.passpointProvisionStats.provisionFailureCount[1].count, 2);
    }

    /**
     * Combination of all other WifiMetrics unit tests, an internal-integration test, or functional
     * test
     */
    @Test
    public void setMetricsSerializeDeserializeAssertMetricsSame() throws Exception {
        setAndIncrementMetrics();
        startAndEndConnectionEventSucceeds();
        dumpProtoAndDeserialize();
        assertDeserializedMetricsCorrect();
        assertEquals("mDecodedProto.connectionEvent.length",
                2, mDecodedProto.connectionEvent.length);
        //<TODO> test individual connectionEvents for correctness,
        // check scanReturnEntries & wifiSystemStateEntries counts and individual elements
        // pending their implementation</TODO>
    }

    /**
     * Test that score breach events are properly generated
     */
    @Test
    public void testScoreBeachEvents() throws Exception {
        int upper = WifiMetrics.LOW_WIFI_SCORE + 7;
        int mid = WifiMetrics.LOW_WIFI_SCORE;
        int lower = WifiMetrics.LOW_WIFI_SCORE - 8;
        mWifiMetrics.setWifiState(TEST_IFACE_NAME, WifiMetricsProto.WifiLog.WIFI_ASSOCIATED);
        for (int score = upper; score >= mid; score--) {
            mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, score);
        }
        mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, mid + 1);
        mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, lower); // First breach
        for (int score = lower; score <= mid; score++) {
            mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, score);
        }
        mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, mid - 1);
        mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, upper); // Second breach

        dumpProtoAndDeserialize();

        assertEquals(2, mDecodedProto.staEventList.length);
        assertEquals(StaEvent.TYPE_SCORE_BREACH, mDecodedProto.staEventList[0].type);
        assertEquals(TEST_IFACE_NAME, mDecodedProto.staEventList[0].interfaceName);
        assertEquals(lower, mDecodedProto.staEventList[0].lastScore);
        assertEquals(StaEvent.TYPE_SCORE_BREACH, mDecodedProto.staEventList[1].type);
        assertEquals(TEST_IFACE_NAME, mDecodedProto.staEventList[1].interfaceName);
        assertEquals(upper, mDecodedProto.staEventList[1].lastScore);
    }

    /**
     * Test that Wifi usability score breach events are properly generated
     */
    @Test
    public void testWifiUsabilityScoreBreachEvents() throws Exception {
        int upper = WifiMetrics.LOW_WIFI_USABILITY_SCORE + 7;
        int mid = WifiMetrics.LOW_WIFI_USABILITY_SCORE;
        int lower = WifiMetrics.LOW_WIFI_USABILITY_SCORE - 8;
        mWifiMetrics.setWifiState(TEST_IFACE_NAME, WifiMetricsProto.WifiLog.WIFI_ASSOCIATED);
        for (int score = upper; score >= mid; score--) {
            mWifiMetrics.incrementWifiUsabilityScoreCount(TEST_IFACE_NAME, 1, score, 15);
        }
        mWifiMetrics.incrementWifiUsabilityScoreCount(TEST_IFACE_NAME, 1, mid + 1, 15);
        // First breach
        mWifiMetrics.incrementWifiUsabilityScoreCount(TEST_IFACE_NAME, 1, lower, 15);
        for (int score = lower; score <= mid; score++) {
            mWifiMetrics.incrementWifiUsabilityScoreCount(TEST_IFACE_NAME, 1, score, 15);
        }
        mWifiMetrics.incrementWifiUsabilityScoreCount(TEST_IFACE_NAME, 1, mid - 1, 15);
        // Second breach
        mWifiMetrics.incrementWifiUsabilityScoreCount(TEST_IFACE_NAME, 1, upper, 15);

        dumpProtoAndDeserialize();

        assertEquals(2, mDecodedProto.staEventList.length);
        assertEquals(StaEvent.TYPE_WIFI_USABILITY_SCORE_BREACH, mDecodedProto.staEventList[0].type);
        assertEquals(lower, mDecodedProto.staEventList[0].lastWifiUsabilityScore);
        assertEquals(StaEvent.TYPE_WIFI_USABILITY_SCORE_BREACH, mDecodedProto.staEventList[1].type);
        assertEquals(upper, mDecodedProto.staEventList[1].lastWifiUsabilityScore);
    }

    /**
     * Test that WifiMetrics is correctly getting data from ScanDetail and WifiConfiguration
     */
    @Test
    public void testScanDetailAndWifiConfigurationUsage() throws Exception {
        setupNetworkAndVerify();
    }

    /**
     * Test that WifiMetrics is correctly getting data from ScanDetail and WifiConfiguration for
     * Passpoint use cases.
     */
    @Test
    public void testScanDetailAndWifiConfigurationUsageForPasspoint() throws Exception {
        setupNetworkAndVerify(true, false);
        setupNetworkAndVerify(true, true);
    }

    private static final String SSID = "red";
    private static final int CONFIG_DTIM = 3;
    private static final int NETWORK_DETAIL_WIFIMODE = 5;
    private static final int NETWORK_DETAIL_DTIM = 7;
    private static final int SCAN_RESULT_LEVEL = -30;

    private void setupNetworkAndVerify() throws Exception {
        setupNetworkAndVerify(false, false);
    }

    private void setupNetworkAndVerify(boolean isPasspoint, boolean isPasspointHomeProvider)
            throws Exception {
        //Setup mock configs and scan details
        NetworkDetail networkDetail = mock(NetworkDetail.class);
        when(networkDetail.getWifiMode()).thenReturn(NETWORK_DETAIL_WIFIMODE);
        when(networkDetail.getSSID()).thenReturn(SSID);
        when(networkDetail.getDtimInterval()).thenReturn(NETWORK_DETAIL_DTIM);
        ScanResult scanResult = mock(ScanResult.class);
        scanResult.level = SCAN_RESULT_LEVEL;
        scanResult.capabilities = "EAP/SHA1";
        WifiConfiguration config = mock(WifiConfiguration.class);
        config.SSID = "\"" + SSID + "\"";
        config.dtimInterval = CONFIG_DTIM;
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        config.allowedKeyManagement = new BitSet();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X);
        config.enterpriseConfig = new WifiEnterpriseConfig();
        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TTLS);
        config.enterpriseConfig.setPhase2Method(WifiEnterpriseConfig.Phase2.MSCHAPV2);
        config.enterpriseConfig.setOcsp(WifiEnterpriseConfig.OCSP_REQUIRE_CERT_STATUS);
        WifiConfiguration.NetworkSelectionStatus networkSelectionStat =
                mock(WifiConfiguration.NetworkSelectionStatus.class);
        when(networkSelectionStat.getCandidate()).thenReturn(scanResult);
        when(config.getNetworkSelectionStatus()).thenReturn(networkSelectionStat);
        ScanDetail scanDetail = mock(ScanDetail.class);
        when(scanDetail.getNetworkDetail()).thenReturn(networkDetail);
        when(scanDetail.getScanResult()).thenReturn(scanResult);
        when(networkDetail.isMboSupported()).thenReturn(true);
        when(networkDetail.isOceSupported()).thenReturn(true);
        SecurityParams securityParams = mock(SecurityParams.class);
        when(config.getDefaultSecurityParams()).thenReturn(securityParams);
        when(securityParams.isEnterpriseSecurityType()).thenReturn(true);

        config.networkId = TEST_NETWORK_ID;
        mWifiMetrics.setNominatorForNetwork(TEST_NETWORK_ID,
                WifiMetricsProto.ConnectionEvent.NOMINATOR_MANUAL);

        when(config.isPasspoint()).thenReturn(isPasspoint);
        config.isHomeProviderNetwork = isPasspointHomeProvider;

        //Create a connection event using only the config
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config,
                "Red", WifiMetricsProto.ConnectionEvent.ROAM_NONE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        //Change configuration to open without randomization
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        scanResult.capabilities = "";

        //Create a connection event using the config and a scan detail
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config,
                "Green", WifiMetricsProto.ConnectionEvent.ROAM_NONE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.setConnectionScanDetail(TEST_IFACE_NAME, scanDetail);
        mWifiMetrics.logBugReport();
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        //Dump proto from mWifiMetrics and deserialize it to mDecodedProto
        dumpProtoAndDeserialize();

        //Check that the correct values are being flowed through
        assertEquals(2, mDecodedProto.connectionEvent.length);
        assertEquals(CONFIG_DTIM, mDecodedProto.connectionEvent[0].routerFingerprint.dtim);
        assertEquals(WifiMetricsProto.RouterFingerPrint.AUTH_ENTERPRISE,
                mDecodedProto.connectionEvent[0].routerFingerprint.authentication);
        assertEquals(WifiMetricsProto.RouterFingerPrint.TYPE_EAP_TTLS,
                mDecodedProto.connectionEvent[0].routerFingerprint.eapMethod);
        assertEquals(WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_MSCHAPV2,
                mDecodedProto.connectionEvent[0].routerFingerprint.authPhase2Method);
        assertEquals(WifiMetricsProto.RouterFingerPrint.TYPE_OCSP_REQUIRE_CERT_STATUS,
                mDecodedProto.connectionEvent[0].routerFingerprint.ocspType);
        assertEquals(SCAN_RESULT_LEVEL, mDecodedProto.connectionEvent[0].signalStrength);
        assertEquals(NETWORK_DETAIL_DTIM, mDecodedProto.connectionEvent[1].routerFingerprint.dtim);
        assertEquals(WifiMetricsProto.RouterFingerPrint.AUTH_OPEN,
                mDecodedProto.connectionEvent[1].routerFingerprint.authentication);
        assertEquals(WifiMetricsProto.RouterFingerPrint.TYPE_EAP_UNKNOWN,
                mDecodedProto.connectionEvent[1].routerFingerprint.eapMethod);
        assertEquals(WifiMetricsProto.RouterFingerPrint.TYPE_PHASE2_NONE,
                mDecodedProto.connectionEvent[1].routerFingerprint.authPhase2Method);
        assertEquals(WifiMetricsProto.RouterFingerPrint.TYPE_OCSP_NONE,
                mDecodedProto.connectionEvent[1].routerFingerprint.ocspType);
        assertEquals(SCAN_RESULT_LEVEL, mDecodedProto.connectionEvent[1].signalStrength);
        assertEquals(NETWORK_DETAIL_WIFIMODE,
                mDecodedProto.connectionEvent[1].routerFingerprint.routerTechnology);
        assertFalse(mDecodedProto.connectionEvent[0].automaticBugReportTaken);
        assertTrue(mDecodedProto.connectionEvent[1].automaticBugReportTaken);
        assertTrue(mDecodedProto.connectionEvent[0].useRandomizedMac);
        assertFalse(mDecodedProto.connectionEvent[1].useRandomizedMac);
        assertEquals(WifiMetricsProto.ConnectionEvent.NOMINATOR_MANUAL,
                mDecodedProto.connectionEvent[0].connectionNominator);
        assertEquals(1, mDecodedProto.numConnectToNetworkSupportingMbo);
        assertEquals(1, mDecodedProto.numConnectToNetworkSupportingOce);
        assertEquals(isPasspoint, mDecodedProto.connectionEvent[0].routerFingerprint.passpoint);
        assertEquals(isPasspointHomeProvider,
                mDecodedProto.connectionEvent[0].routerFingerprint.isPasspointHomeProvider);
    }

    /**
     * Tests that the mapping from networkId to nominatorId is not cleared.
     */
    @Test
    public void testNetworkToNominatorNotCleared() throws Exception {
        //Setup mock configs and scan details
        NetworkDetail networkDetail = mock(NetworkDetail.class);
        when(networkDetail.getWifiMode()).thenReturn(NETWORK_DETAIL_WIFIMODE);
        when(networkDetail.getSSID()).thenReturn(SSID);
        when(networkDetail.getDtimInterval()).thenReturn(NETWORK_DETAIL_DTIM);
        ScanResult scanResult = mock(ScanResult.class);
        scanResult.level = SCAN_RESULT_LEVEL;
        WifiConfiguration config = mock(WifiConfiguration.class);
        config.SSID = "\"" + SSID + "\"";
        config.dtimInterval = CONFIG_DTIM;
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        config.allowedKeyManagement = new BitSet();
        WifiConfiguration.NetworkSelectionStatus networkSelectionStat =
                mock(WifiConfiguration.NetworkSelectionStatus.class);
        when(networkSelectionStat.getCandidate()).thenReturn(scanResult);
        when(config.getNetworkSelectionStatus()).thenReturn(networkSelectionStat);
        ScanDetail scanDetail = mock(ScanDetail.class);
        when(scanDetail.getNetworkDetail()).thenReturn(networkDetail);
        when(scanDetail.getScanResult()).thenReturn(scanResult);
        SecurityParams securityParams = mock(SecurityParams.class);
        when(config.getDefaultSecurityParams()).thenReturn(securityParams);
        when(securityParams.isEnterpriseSecurityType()).thenReturn(true);

        config.networkId = TEST_NETWORK_ID;
        mWifiMetrics.setNominatorForNetwork(TEST_NETWORK_ID,
                WifiMetricsProto.ConnectionEvent.NOMINATOR_CARRIER);

        // dump() calls clear() internally
        mWifiMetrics.dump(null, new PrintWriter(new StringWriter()),
                new String[]{WifiMetrics.PROTO_DUMP_ARG});

        // Create a connection event using only the config
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config,
                "Red", WifiMetricsProto.ConnectionEvent.ROAM_NONE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        dumpProtoAndDeserialize();

        assertEquals(WifiMetricsProto.ConnectionEvent.NOMINATOR_CARRIER,
                mDecodedProto.connectionEvent[0].connectionNominator);
    }

    /**
     * Test that WifiMetrics is serializing/deserializing association time out events.
     */
    @Test
    public void testMetricsAssociationTimedOut() throws Exception {
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, null,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_NONE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        //Dump proto and deserialize
        //This should clear all the metrics in mWifiMetrics,
        dumpProtoAndDeserialize();
        //Check there is only 1 connection events
        assertEquals(1, mDecodedProto.connectionEvent.length);
        assertEquals(WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT,
                mDecodedProto.connectionEvent[0].level2FailureCode);
        assertEquals(WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN,
                mDecodedProto.connectionEvent[0].level2FailureReason);
    }

    /**
     * Verify the logging of number of blocked BSSIDs in ConnectionEvent.
     */
    @Test
    public void testMetricNumBssidInBlocklist() throws Exception {
        WifiConfiguration config = mock(WifiConfiguration.class);
        config.SSID = "\"" + SSID + "\"";
        config.allowedKeyManagement = new BitSet();
        when(config.getNetworkSelectionStatus()).thenReturn(
                mock(WifiConfiguration.NetworkSelectionStatus.class));
        when(mWifiBlocklistMonitor.updateAndGetNumBlockedBssidsForSsid(eq(config.SSID)))
                .thenReturn(3);
        SecurityParams securityParams = mock(SecurityParams.class);
        when(config.getDefaultSecurityParams()).thenReturn(securityParams);
        when(securityParams.isEnterpriseSecurityType()).thenReturn(true);

        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_NONE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        dumpProtoAndDeserialize();

        assertEquals(1, mDecodedProto.connectionEvent.length);
        assertEquals(3, mDecodedProto.connectionEvent[0].numBssidInBlocklist);
    }

    /**
     * Verify the ConnectionEvent is labeled with networkType open network correctly.
     */
    @Test
    public void testConnectionNetworkTypeOpen() throws Exception {
        WifiConfiguration config = mock(WifiConfiguration.class);
        config.SSID = "\"" + SSID + "\"";
        config.allowedKeyManagement = new BitSet();
        when(config.getNetworkSelectionStatus()).thenReturn(
                mock(WifiConfiguration.NetworkSelectionStatus.class));
        when(config.isOpenNetwork()).thenReturn(true);
        SecurityParams securityParams = mock(SecurityParams.class);
        when(config.getDefaultSecurityParams()).thenReturn(securityParams);
        when(securityParams.isEnterpriseSecurityType()).thenReturn(false);
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_NONE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        dumpProtoAndDeserialize();

        assertEquals(1, mDecodedProto.connectionEvent.length);
        assertEquals(WifiMetricsProto.ConnectionEvent.TYPE_OPEN,
                mDecodedProto.connectionEvent[0].networkType);
        assertFalse(mDecodedProto.connectionEvent[0].isOsuProvisioned);
    }

    @Test
    public void testStart2ConnectionEventsOnDifferentIfaces_endOneAndDump_endOtherAndDump()
            throws Exception {
        WifiConfiguration config1 = WifiConfigurationTestUtil.createPskNetwork();
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config1, "RED",
                WifiMetricsProto.ConnectionEvent.ROAM_DBDC, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        WifiConfiguration config2 = WifiConfigurationTestUtil.createOpenNetwork();
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME2, config2, "BLUE",
                WifiMetricsProto.ConnectionEvent.ROAM_USER_SELECTED, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        mWifiMetrics.setConnectionScanDetail(TEST_IFACE_NAME, mock(ScanDetail.class));
        mWifiMetrics.setConnectionPmkCache(TEST_IFACE_NAME, false);
        mWifiMetrics.setConnectionMaxSupportedLinkSpeedMbps(TEST_IFACE_NAME, 100, 50);

        mWifiMetrics.setConnectionScanDetail(TEST_IFACE_NAME2, mock(ScanDetail.class));
        mWifiMetrics.setConnectionPmkCache(TEST_IFACE_NAME2, true);
        mWifiMetrics.setConnectionMaxSupportedLinkSpeedMbps(TEST_IFACE_NAME2, 400, 200);

        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME2,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_TIMEOUT, 5745,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        dumpProtoAndDeserialize();

        assertEquals(1, mDecodedProto.connectionEvent.length);
        WifiMetricsProto.ConnectionEvent connectionEvent = mDecodedProto.connectionEvent[0];
        assertEquals(TEST_IFACE_NAME2, connectionEvent.interfaceName);
        assertTrue(connectionEvent.routerFingerprint.pmkCacheEnabled);
        assertEquals(400, connectionEvent.routerFingerprint.maxSupportedTxLinkSpeedMbps);
        assertEquals(200, connectionEvent.routerFingerprint.maxSupportedRxLinkSpeedMbps);
        assertEquals(WifiMetricsProto.ConnectionEvent.ROAM_USER_SELECTED, connectionEvent.roamType);
        assertEquals(WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_TIMEOUT,
                connectionEvent.level2FailureReason);

        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.HLF_DHCP,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD, 2412,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        dumpProtoAndDeserialize();

        assertEquals(1, mDecodedProto.connectionEvent.length);
        connectionEvent = mDecodedProto.connectionEvent[0];
        assertEquals(TEST_IFACE_NAME, connectionEvent.interfaceName);
        assertFalse(connectionEvent.routerFingerprint.pmkCacheEnabled);
        assertEquals(100, connectionEvent.routerFingerprint.maxSupportedTxLinkSpeedMbps);
        assertEquals(50, connectionEvent.routerFingerprint.maxSupportedRxLinkSpeedMbps);
        assertEquals(WifiMetricsProto.ConnectionEvent.ROAM_DBDC, connectionEvent.roamType);
        assertEquals(WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD,
                connectionEvent.level2FailureReason);
    }

    @Test
    public void testStart2ConnectionEventsOnDifferentIfaces_end2AndDump() throws Exception {
        WifiConfiguration config1 = WifiConfigurationTestUtil.createPskNetwork();
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config1, "RED",
                WifiMetricsProto.ConnectionEvent.ROAM_DBDC, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        WifiConfiguration config2 = WifiConfigurationTestUtil.createOpenNetwork();
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME2, config2, "BLUE",
                WifiMetricsProto.ConnectionEvent.ROAM_USER_SELECTED, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        mWifiMetrics.setConnectionScanDetail(TEST_IFACE_NAME, mock(ScanDetail.class));
        mWifiMetrics.setConnectionPmkCache(TEST_IFACE_NAME, false);
        mWifiMetrics.setConnectionMaxSupportedLinkSpeedMbps(TEST_IFACE_NAME, 100, 50);

        mWifiMetrics.setConnectionScanDetail(TEST_IFACE_NAME2, mock(ScanDetail.class));
        mWifiMetrics.setConnectionPmkCache(TEST_IFACE_NAME2, true);
        mWifiMetrics.setConnectionMaxSupportedLinkSpeedMbps(TEST_IFACE_NAME2, 400, 200);

        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.HLF_DHCP,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD, 2412,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME2,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_TIMEOUT, 5745,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        dumpProtoAndDeserialize();

        assertEquals(2, mDecodedProto.connectionEvent.length);

        WifiMetricsProto.ConnectionEvent connectionEvent = mDecodedProto.connectionEvent[0];
        assertEquals(TEST_IFACE_NAME, connectionEvent.interfaceName);
        assertFalse(connectionEvent.routerFingerprint.pmkCacheEnabled);
        assertEquals(100, connectionEvent.routerFingerprint.maxSupportedTxLinkSpeedMbps);
        assertEquals(50, connectionEvent.routerFingerprint.maxSupportedRxLinkSpeedMbps);
        assertEquals(WifiMetricsProto.ConnectionEvent.ROAM_DBDC, connectionEvent.roamType);
        assertEquals(WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD,
                connectionEvent.level2FailureReason);

        connectionEvent = mDecodedProto.connectionEvent[1];
        assertEquals(TEST_IFACE_NAME2, connectionEvent.interfaceName);
        assertTrue(connectionEvent.routerFingerprint.pmkCacheEnabled);
        assertEquals(400, connectionEvent.routerFingerprint.maxSupportedTxLinkSpeedMbps);
        assertEquals(200, connectionEvent.routerFingerprint.maxSupportedRxLinkSpeedMbps);
        assertEquals(WifiMetricsProto.ConnectionEvent.ROAM_USER_SELECTED, connectionEvent.roamType);
        assertEquals(WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_TIMEOUT,
                connectionEvent.level2FailureReason);
    }

    @Test
    public void testStartAndEnd2ConnectionEventsOnDifferentIfacesAndDump() throws Exception {
        WifiConfiguration config1 = WifiConfigurationTestUtil.createPskNetwork();
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config1, "RED",
                WifiMetricsProto.ConnectionEvent.ROAM_DBDC, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.setConnectionScanDetail(TEST_IFACE_NAME, mock(ScanDetail.class));
        mWifiMetrics.setConnectionPmkCache(TEST_IFACE_NAME, false);
        mWifiMetrics.setConnectionMaxSupportedLinkSpeedMbps(TEST_IFACE_NAME, 100, 50);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.HLF_DHCP,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD, 2412,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        WifiConfiguration config2 = WifiConfigurationTestUtil.createOpenNetwork();
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME2, config2, "BLUE",
                WifiMetricsProto.ConnectionEvent.ROAM_USER_SELECTED, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.setConnectionScanDetail(TEST_IFACE_NAME2, mock(ScanDetail.class));
        mWifiMetrics.setConnectionPmkCache(TEST_IFACE_NAME2, true);
        mWifiMetrics.setConnectionMaxSupportedLinkSpeedMbps(TEST_IFACE_NAME2, 400, 200);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME2,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_TIMEOUT, 5745,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        dumpProtoAndDeserialize();

        assertEquals(2, mDecodedProto.connectionEvent.length);

        WifiMetricsProto.ConnectionEvent connectionEvent = mDecodedProto.connectionEvent[0];
        assertEquals(TEST_IFACE_NAME, connectionEvent.interfaceName);
        assertFalse(connectionEvent.routerFingerprint.pmkCacheEnabled);
        assertEquals(100, connectionEvent.routerFingerprint.maxSupportedTxLinkSpeedMbps);
        assertEquals(50, connectionEvent.routerFingerprint.maxSupportedRxLinkSpeedMbps);
        assertEquals(WifiMetricsProto.ConnectionEvent.ROAM_DBDC, connectionEvent.roamType);
        assertEquals(WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD,
                connectionEvent.level2FailureReason);

        connectionEvent = mDecodedProto.connectionEvent[1];
        assertEquals(TEST_IFACE_NAME2, connectionEvent.interfaceName);
        assertTrue(connectionEvent.routerFingerprint.pmkCacheEnabled);
        assertEquals(400, connectionEvent.routerFingerprint.maxSupportedTxLinkSpeedMbps);
        assertEquals(200, connectionEvent.routerFingerprint.maxSupportedRxLinkSpeedMbps);
        assertEquals(WifiMetricsProto.ConnectionEvent.ROAM_USER_SELECTED, connectionEvent.roamType);
        assertEquals(WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_TIMEOUT,
                connectionEvent.level2FailureReason);
    }

    @Test
    public void testNonExistentConnectionEventIface_doesntCrash() throws Exception {
        mWifiMetrics.setConnectionScanDetail("nonexistentIface", mock(ScanDetail.class));
        mWifiMetrics.setConnectionPmkCache("nonexistentIface", false);
        mWifiMetrics.setConnectionMaxSupportedLinkSpeedMbps("nonexistentIface", 100, 50);
        mWifiMetrics.endConnectionEvent("nonexistentIface",
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.HLF_DHCP,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD, 2412,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
    }

    /**
     * Verify the ConnectionEvent is labeled with networkType Passpoint correctly.
     */
    @Test
    public void testConnectionNetworkTypePasspoint() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createPasspointNetwork();
        config.carrierMerged = true;
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_NONE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        dumpProtoAndDeserialize();

        assertEquals(1, mDecodedProto.connectionEvent.length);
        assertEquals(WifiMetricsProto.ConnectionEvent.TYPE_PASSPOINT,
                mDecodedProto.connectionEvent[0].networkType);
        assertFalse(mDecodedProto.connectionEvent[0].isOsuProvisioned);
        assertTrue(mDecodedProto.connectionEvent[0].isCarrierMerged);
    }

    /**
     * Verify the ConnectionEvent is created with correct creatorUid.
     */
    @Test
    public void testConnectionCreatorUid() throws Exception {
        WifiConfiguration config = mock(WifiConfiguration.class);
        config.SSID = "\"" + SSID + "\"";
        config.allowedKeyManagement = new BitSet();
        when(config.getNetworkSelectionStatus()).thenReturn(
                mock(WifiConfiguration.NetworkSelectionStatus.class));
        SecurityParams securityParams = mock(SecurityParams.class);
        when(config.getDefaultSecurityParams()).thenReturn(securityParams);
        when(securityParams.isEnterpriseSecurityType()).thenReturn(true);

        // First network is created by the user
        config.fromWifiNetworkSuggestion = false;
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_NONE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        // Second network is created by a carrier app
        config.fromWifiNetworkSuggestion = true;
        config.carrierId = 123;
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_NONE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        // Third network is created by an unknown app
        config.fromWifiNetworkSuggestion = true;
        config.carrierId = TelephonyManager.UNKNOWN_CARRIER_ID;
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_NONE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        dumpProtoAndDeserialize();

        assertEquals(3, mDecodedProto.connectionEvent.length);
        assertEquals(WifiMetricsProto.ConnectionEvent.CREATOR_USER,
                mDecodedProto.connectionEvent[0].networkCreator);
        assertEquals(WifiMetricsProto.ConnectionEvent.CREATOR_CARRIER,
                mDecodedProto.connectionEvent[1].networkCreator);
        assertEquals(WifiMetricsProto.ConnectionEvent.CREATOR_UNKNOWN,
                mDecodedProto.connectionEvent[2].networkCreator);
    }

    /**
     * Test that WifiMetrics is serializing/deserializing authentication failure events.
     */
    @Test
    public void testMetricsAuthenticationFailureReason() throws Exception {
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, null,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_NONE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        //Dump proto and deserialize
        //This should clear all the metrics in mWifiMetrics,
        dumpProtoAndDeserialize();
        //Check there is only 1 connection events
        assertEquals(1, mDecodedProto.connectionEvent.length);
        assertEquals(WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                mDecodedProto.connectionEvent[0].level2FailureCode);
        //Check the authentication failure reason
        assertEquals(WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD,
                mDecodedProto.connectionEvent[0].level2FailureReason);
    }

    /**
     * Test the logging of BssidBlocklistStats.
     */
    @Test
    public void testBssidBlocklistMetrics() throws Exception {
        for (int i = 0; i < 3; i++) {
            mWifiMetrics.incrementNetworkSelectionFilteredBssidCount(i);
            mWifiMetrics.incrementBssidBlocklistCount(
                    WifiBlocklistMonitor.REASON_ASSOCIATION_TIMEOUT);
            mWifiMetrics.incrementWificonfigurationBlocklistCount(
                    NetworkSelectionStatus.DISABLED_ASSOCIATION_REJECTION);
        }
        mWifiMetrics.incrementNetworkSelectionFilteredBssidCount(2);
        mWifiMetrics.incrementBssidBlocklistCount(
                WifiBlocklistMonitor.REASON_NETWORK_VALIDATION_FAILURE);
        mWifiMetrics.incrementWificonfigurationBlocklistCount(
                NetworkSelectionStatus.DISABLED_NO_INTERNET_TEMPORARY);
        mResources.setBoolean(R.bool.config_wifiHighMovementNetworkSelectionOptimizationEnabled,
                true);
        mWifiMetrics.incrementNumHighMovementConnectionStarted();
        mWifiMetrics.incrementNumHighMovementConnectionSkipped();
        mWifiMetrics.incrementNumHighMovementConnectionSkipped();
        dumpProtoAndDeserialize();

        Int32Count[] expectedFilteredBssidHistogram = {
                buildInt32Count(0, 1),
                buildInt32Count(1, 1),
                buildInt32Count(2, 2),
        };
        Int32Count[] expectedBssidBlocklistPerReasonHistogram = {
                buildInt32Count(WifiBlocklistMonitor.REASON_NETWORK_VALIDATION_FAILURE, 1),
                buildInt32Count(WifiBlocklistMonitor.REASON_ASSOCIATION_TIMEOUT, 3),
        };
        Int32Count[] expectedWificonfigBlocklistPerReasonHistogram = {
                buildInt32Count(NetworkSelectionStatus.DISABLED_ASSOCIATION_REJECTION, 3),
                buildInt32Count(NetworkSelectionStatus.DISABLED_NO_INTERNET_TEMPORARY, 1),
        };
        assertKeyCountsEqual(expectedFilteredBssidHistogram,
                mDecodedProto.bssidBlocklistStats.networkSelectionFilteredBssidCount);
        assertKeyCountsEqual(expectedBssidBlocklistPerReasonHistogram,
                mDecodedProto.bssidBlocklistStats.bssidBlocklistPerReasonCount);
        assertKeyCountsEqual(expectedWificonfigBlocklistPerReasonHistogram,
                mDecodedProto.bssidBlocklistStats.wifiConfigBlocklistPerReasonCount);
        assertEquals(true, mDecodedProto.bssidBlocklistStats
                .highMovementMultipleScansFeatureEnabled);
        assertEquals(1, mDecodedProto.bssidBlocklistStats.numHighMovementConnectionStarted);
        assertEquals(2, mDecodedProto.bssidBlocklistStats.numHighMovementConnectionSkipped);
    }

    /**
     * Test that WifiMetrics is being cleared after dumping via proto
     */
    @Test
    public void testMetricsClearedAfterProtoRequested() throws Exception {
        // Create 3 ConnectionEvents
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, null,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, null,
                "YELLOW", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, null,
                "GREEN", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, null,
                "ORANGE", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        //Dump proto and deserialize
        //This should clear all the metrics in mWifiMetrics,
        dumpProtoAndDeserialize();
        //Check there are 4 connection events
        assertEquals(4, mDecodedProto.connectionEvent.length);
        assertEquals(0, mDecodedProto.rssiPollRssiCount.length);
        assertEquals(0, mDecodedProto.alertReasonCount.length);

        // Create 2 ConnectionEvents
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, null,
                "BLUE", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, null,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        //Dump proto and deserialize
        dumpProtoAndDeserialize();
        //Check there are only 2 connection events
        assertEquals(2, mDecodedProto.connectionEvent.length);
    }

    /**
     * Test logging to statsd when a connection event finishes.
     */
    @Test
    public void testLogWifiConnectionResultStatsd() throws Exception {
        // Start and end Connection event
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, createComplexWifiConfig(),
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                WifiMetricsProto.ConnectionEvent.HLF_DHCP,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, TEST_CANDIDATE_FREQ,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED), eq(false),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_AUTHENTICATION_GENERAL),
                eq(-80), eq(0),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__BAND__BAND_2G),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__AUTH_TYPE__AUTH_TYPE_WPA2_PSK),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__TRIGGER__AUTOCONNECT_BOOT),
                eq(true),
                eq(0),
                eq(true),
                eq(false),
                eq(1), eq(TEST_CONNECTION_FAILURE_STATUS_CODE), anyInt(), anyInt(), anyInt(),
                anyInt(), anyInt(), eq(TEST_UID)));
    }

    /**
     * Test that current ongoing ConnectionEvent is not cleared and logged
     * when proto is dumped
     */
    @Test
    public void testCurrentConnectionEventNotClearedAfterProtoRequested() throws Exception {
        // Create 2 complete ConnectionEvents and 1 ongoing un-ended ConnectionEvent
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, null,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, null,
                "YELLOW", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, null,
                "GREEN", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        // Dump proto and deserialize
        // This should clear the metrics in mWifiMetrics,
        dumpProtoAndDeserialize();
        assertEquals(2, mDecodedProto.connectionEvent.length);

        // End the ongoing ConnectionEvent
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        dumpProtoAndDeserialize();
        assertEquals(1, mDecodedProto.connectionEvent.length);
    }

    /**
     * Tests that after setting metrics values they can be serialized and deserialized with the
     *   $ adb shell dumpsys wifi wifiMetricsProto clean
     */
    @Test
    public void testClearMetricsDump() throws Exception {
        setAndIncrementMetrics();
        startAndEndConnectionEventSucceeds();
        cleanDumpProtoAndDeserialize();
        assertDeserializedMetricsCorrect();
        assertEquals("mDecodedProto.connectionEvent.length",
                2, mDecodedProto.connectionEvent.length);
    }

    private static final int NUM_REPEATED_DELTAS = 7;
    private static final int REPEATED_DELTA = 0;
    private static final int SINGLE_GOOD_DELTA = 1;
    private static final int SINGLE_TIMEOUT_DELTA = 2;
    private static final int NUM_REPEATED_BOUND_DELTAS = 2;
    private static final int MAX_DELTA_LEVEL = 127;
    private static final int MIN_DELTA_LEVEL = -127;
    private static final int ARBITRARY_DELTA_LEVEL = 20;

    /**
     * Sunny day RSSI delta logging scenario.
     * Logs one rssi delta value multiple times
     * Logs a different delta value a single time
     */
    @Test
    public void testRssiDeltasSuccessfulLogging() throws Exception {
        // Generate some repeated deltas
        for (int i = 0; i < NUM_REPEATED_DELTAS; i++) {
            generateRssiDelta(MIN_RSSI_LEVEL, REPEATED_DELTA,
                    WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS);
        }
        // Generate a single delta
        generateRssiDelta(MIN_RSSI_LEVEL, SINGLE_GOOD_DELTA,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS);
        dumpProtoAndDeserialize();
        assertEquals(2, mDecodedProto.rssiPollDeltaCount.length);
        // Check the repeated deltas
        assertEquals(NUM_REPEATED_DELTAS, mDecodedProto.rssiPollDeltaCount[0].count);
        assertEquals(REPEATED_DELTA, mDecodedProto.rssiPollDeltaCount[0].rssi);
        // Check the single delta
        assertEquals(1, mDecodedProto.rssiPollDeltaCount[1].count);
        assertEquals(SINGLE_GOOD_DELTA, mDecodedProto.rssiPollDeltaCount[1].rssi);
    }

    /**
     * Tests that Rssi Delta events whose scanResult and Rssi Poll come too far apart, timeout,
     * and are not logged.
     */
    @Test
    public void testRssiDeltasTimeout() throws Exception {
        // Create timed out rssi deltas
        generateRssiDelta(MIN_RSSI_LEVEL, REPEATED_DELTA,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS + 1);
        generateRssiDelta(MIN_RSSI_LEVEL, SINGLE_TIMEOUT_DELTA,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS + 1);
        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.rssiPollDeltaCount.length);
    }

    /**
     * Tests the exact inclusive boundaries of RSSI delta logging.
     */
    @Test
    public void testRssiDeltaSuccessfulLoggingExactBounds() throws Exception {
        generateRssiDelta(MIN_RSSI_LEVEL, MAX_DELTA_LEVEL,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS);
        generateRssiDelta(MAX_RSSI_LEVEL, MIN_DELTA_LEVEL,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS);
        dumpProtoAndDeserialize();
        assertEquals(2, mDecodedProto.rssiPollDeltaCount.length);
        assertEquals(MIN_DELTA_LEVEL, mDecodedProto.rssiPollDeltaCount[0].rssi);
        assertEquals(1, mDecodedProto.rssiPollDeltaCount[0].count);
        assertEquals(MAX_DELTA_LEVEL, mDecodedProto.rssiPollDeltaCount[1].rssi);
        assertEquals(1, mDecodedProto.rssiPollDeltaCount[1].count);
    }

    /**
     * Tests the exact exclusive boundaries of RSSI delta logging.
     * This test ensures that too much data is not generated.
     */
    @Test
    public void testRssiDeltaOutOfBounds() throws Exception {
        generateRssiDelta(MIN_RSSI_LEVEL, MAX_DELTA_LEVEL + 1,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS);
        generateRssiDelta(MAX_RSSI_LEVEL, MIN_DELTA_LEVEL - 1,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS);
        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.rssiPollDeltaCount.length);
    }

    /**
     * This test ensures no rssi Delta is logged after an unsuccessful ConnectionEvent
     */
    @Test
    public void testUnsuccesfulConnectionEventRssiDeltaIsNotLogged() throws Exception {
        generateRssiDelta(MIN_RSSI_LEVEL, ARBITRARY_DELTA_LEVEL,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS,
                false, // successfulConnectionEvent
                true, // completeConnectionEvent
                true, // useValidScanResult
                true // dontDeserializeBeforePoll
        );

        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.rssiPollDeltaCount.length);
    }

    /**
     * This test ensures rssi Deltas can be logged during a ConnectionEvent
     */
    @Test
    public void testIncompleteConnectionEventRssiDeltaIsLogged() throws Exception {
        generateRssiDelta(MIN_RSSI_LEVEL, ARBITRARY_DELTA_LEVEL,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS,
                true, // successfulConnectionEvent
                false, // completeConnectionEvent
                true, // useValidScanResult
                true // dontDeserializeBeforePoll
        );
        dumpProtoAndDeserialize();
        assertEquals(1, mDecodedProto.rssiPollDeltaCount.length);
        assertEquals(ARBITRARY_DELTA_LEVEL, mDecodedProto.rssiPollDeltaCount[0].rssi);
        assertEquals(1, mDecodedProto.rssiPollDeltaCount[0].count);
    }

    /**
     * This test ensures that no delta is logged for a null ScanResult Candidate
     */
    @Test
    public void testRssiDeltaNotLoggedForNullCandidateScanResult() throws Exception {
        generateRssiDelta(MIN_RSSI_LEVEL, ARBITRARY_DELTA_LEVEL,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS,
                true, // successfulConnectionEvent
                true, // completeConnectionEvent
                false, // useValidScanResult
                true // dontDeserializeBeforePoll
        );
        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.rssiPollDeltaCount.length);
    }

    /**
     * This test ensures that Rssi Deltas are not logged over a 'clear()' call (Metrics Serialized)
     */
    @Test
    public void testMetricsSerializedDuringRssiDeltaEventLogsNothing() throws Exception {
        generateRssiDelta(MIN_RSSI_LEVEL, ARBITRARY_DELTA_LEVEL,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS,
                true, // successfulConnectionEvent
                true, // completeConnectionEvent
                true, // useValidScanResult
                false // dontDeserializeBeforePoll
        );
        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.rssiPollDeltaCount.length);
    }

    private static final int DEAUTH_REASON = 7;
    private static final int ASSOC_STATUS = 11;
    private static final boolean ASSOC_TIMEOUT = true;
    private static final boolean LOCAL_GEN = true;
    private static final int AUTH_FAILURE_REASON = WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD;
    private static final int NUM_TEST_STA_EVENTS = 19;
    private static final String   sSSID = "\"SomeTestSsid\"";
    private static final WifiSsid sWifiSsid = WifiSsid.fromUtf8Text(sSSID);
    private static final String   sBSSID = "01:02:03:04:05:06";

    private final StateChangeResult mStateDisconnected =
            new StateChangeResult(0, sWifiSsid, sBSSID, 0, SupplicantState.DISCONNECTED);
    private final StateChangeResult mStateCompleted =
            new StateChangeResult(0, sWifiSsid, sBSSID, 0, SupplicantState.COMPLETED);
    // Test bitmasks of supplicant state changes
    private final int mSupBm1 = WifiMetrics.supplicantStateToBit(mStateDisconnected.state);
    private final int mSupBm2 = WifiMetrics.supplicantStateToBit(mStateDisconnected.state)
            | WifiMetrics.supplicantStateToBit(mStateCompleted.state);
    // An invalid but interesting wifiConfiguration that exercises the StaEvent.ConfigInfo encoding
    private final WifiConfiguration mTestWifiConfig = createComplexWifiConfig();
    // <msg.what> <msg.arg1> <msg.arg2>
    private int[][] mTestStaMessageInts = {
        {WifiMonitor.ASSOCIATION_REJECTION_EVENT,   0, 0},
        {WifiMonitor.AUTHENTICATION_FAILURE_EVENT,  0, 0},
        {WifiMonitor.NETWORK_CONNECTION_EVENT,      0, 0},
        {WifiMonitor.NETWORK_DISCONNECTION_EVENT,   0, 0},
        {WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0},
        {WifiMonitor.ASSOCIATED_BSSID_EVENT,        0, 0},
        {WifiMonitor.TARGET_BSSID_EVENT,            0, 0},
        {WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0},
        {WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0}
    };
    private Object[] mTestStaMessageObjs = {
        new AssocRejectEventInfo(sSSID, sBSSID, ASSOC_STATUS, ASSOC_TIMEOUT),
        new AuthenticationFailureEventInfo(sSSID, MacAddress.fromString(sBSSID),
                AUTH_FAILURE_REASON, -1),
        null,
        new DisconnectEventInfo(sSSID, sBSSID, DEAUTH_REASON, LOCAL_GEN),
        mStateDisconnected,
        null,
        null,
        mStateDisconnected,
        mStateCompleted
    };
    // Values used to generate the StaEvent log calls from ClientModeImpl
    // <StaEvent.Type>, <StaEvent.FrameworkDisconnectReason>, <1|0>(testWifiConfiguration, null)
    private int[][] mTestStaLogInts = {
        {StaEvent.TYPE_CMD_IP_CONFIGURATION_SUCCESSFUL, 0,                          0},
        {StaEvent.TYPE_CMD_IP_CONFIGURATION_LOST,       0,                          0},
        {StaEvent.TYPE_CMD_IP_REACHABILITY_LOST,        0,                          0},
        {StaEvent.TYPE_CMD_START_CONNECT,               0,                          1},
        {StaEvent.TYPE_CMD_START_ROAM,                  0,                          1},
        {StaEvent.TYPE_CONNECT_NETWORK,                 0,                          1},
        {StaEvent.TYPE_NETWORK_AGENT_VALID_NETWORK,     0,                          0},
        {StaEvent.TYPE_FRAMEWORK_DISCONNECT,            StaEvent.DISCONNECT_API,    0},
        {StaEvent.TYPE_SCORE_BREACH,                    0,                          0},
        {StaEvent.TYPE_MAC_CHANGE,                      0,                          1},
        {StaEvent.TYPE_WIFI_ENABLED,                    0,                          0},
        {StaEvent.TYPE_WIFI_DISABLED,                   0,                          0},
        {StaEvent.TYPE_WIFI_USABILITY_SCORE_BREACH,     0,                          0}
    };
    // Values used to generate the StaEvent log calls from WifiMonitor
    // <type>, <reason>, <status>, <local_gen>,
    // <auth_fail_reason>, <assoc_timed_out> <supplicantStateChangeBitmask> <1|0>(has ConfigInfo)
    private int[][] mExpectedValues = {
        {StaEvent.TYPE_ASSOCIATION_REJECTION_EVENT,     -1,  ASSOC_STATUS,         0,
            /**/                               0, ASSOC_TIMEOUT ? 1 : 0,        0, 0},    /**/
        {StaEvent.TYPE_AUTHENTICATION_FAILURE_EVENT,    -1,            -1,         0,
            /**/StaEvent.AUTH_FAILURE_WRONG_PSWD,             0,        0, 0},    /**/
        {StaEvent.TYPE_NETWORK_CONNECTION_EVENT,        -1,            -1,         0,
            /**/                               0,             0,        0, 0},    /**/
        {StaEvent.TYPE_NETWORK_DISCONNECTION_EVENT, DEAUTH_REASON,     -1, LOCAL_GEN ? 1 : 0,
            /**/                               0,             0,        0, 0},    /**/
        {StaEvent.TYPE_CMD_ASSOCIATED_BSSID,            -1,            -1,         0,
            /**/                               0,             0,  mSupBm1, 0},    /**/
        {StaEvent.TYPE_CMD_TARGET_BSSID,                -1,            -1,         0,
            /**/                               0,             0,        0, 0},    /**/
        {StaEvent.TYPE_CMD_IP_CONFIGURATION_SUCCESSFUL, -1,            -1,         0,
            /**/                               0,             0,  mSupBm2, 0},    /**/
        {StaEvent.TYPE_CMD_IP_CONFIGURATION_LOST,       -1,            -1,         0,
            /**/                               0,             0,        0, 0},    /**/
        {StaEvent.TYPE_CMD_IP_REACHABILITY_LOST,        -1,            -1,         0,
            /**/                               0,             0,        0, 0},    /**/
        {StaEvent.TYPE_CMD_START_CONNECT,               -1,            -1,         0,
            /**/                               0,             0,        0, 1},    /**/
        {StaEvent.TYPE_CMD_START_ROAM,                  -1,            -1,         0,
            /**/                               0,             0,        0, 1},    /**/
        {StaEvent.TYPE_CONNECT_NETWORK,                 -1,            -1,         0,
            /**/                               0,             0,        0, 1},    /**/
        {StaEvent.TYPE_NETWORK_AGENT_VALID_NETWORK,     -1,            -1,         0,
            /**/                               0,             0,        0, 0},    /**/
        {StaEvent.TYPE_FRAMEWORK_DISCONNECT,            -1,            -1,         0,
            /**/                               0,             0,        0, 0},    /**/
        {StaEvent.TYPE_SCORE_BREACH,                    -1,            -1,         0,
            /**/                               0,             0,        0, 0},    /**/
        {StaEvent.TYPE_MAC_CHANGE,                      -1,            -1,         0,
            /**/                               0,             0,        0, 1},    /**/
        {StaEvent.TYPE_WIFI_ENABLED,                    -1,            -1,         0,
            /**/                               0,             0,        0, 0},    /**/
        {StaEvent.TYPE_WIFI_DISABLED,                   -1,            -1,         0,
            /**/                               0,             0,        0, 0},     /**/
        {StaEvent.TYPE_WIFI_USABILITY_SCORE_BREACH,     -1,            -1,         0,
            /**/                               0,             0,        0, 0}    /**/
    };

    /**
     * Generates events from all the rows in mTestStaMessageInts, and then mTestStaLogInts
     */
    private void generateStaEvents(WifiMetrics wifiMetrics) {
        Handler handler = mHandlerCaptor.getValue();
        for (int i = 0; i < mTestStaMessageInts.length; i++) {
            int[] mia = mTestStaMessageInts[i];
            Message message = handler.obtainMessage(mia[0], mia[1], mia[2], mTestStaMessageObjs[i]);
            message.getData().putString(WifiMonitor.KEY_IFACE, TEST_IFACE_NAME);
            handler.sendMessage(message);
        }
        mTestLooper.dispatchAll();
        setScreenState(true);
        when(mWifiDataStall.isCellularDataAvailable()).thenReturn(true);
        wifiMetrics.setAdaptiveConnectivityState(true);
        for (int i = 0; i < mTestStaLogInts.length; i++) {
            int[] lia = mTestStaLogInts[i];
            wifiMetrics.logStaEvent(
                    TEST_IFACE_NAME, lia[0], lia[1], lia[2] == 1 ? mTestWifiConfig : null);
        }
    }
    private void verifyDeserializedStaEvents(WifiMetricsProto.WifiLog wifiLog) {
        assertNotNull(mTestWifiConfig);
        assertEquals(NUM_TEST_STA_EVENTS, wifiLog.staEventList.length);
        int j = 0; // De-serialized event index
        for (int i = 0; i < mTestStaMessageInts.length; i++) {
            StaEvent event = wifiLog.staEventList[j];
            int[] mia = mTestStaMessageInts[i];
            int[] evs = mExpectedValues[j];
            if (mia[0] != WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT) {
                assertEquals(evs[0], event.type);
                assertEquals(evs[1], event.reason);
                assertEquals(evs[2], event.status);
                assertEquals(evs[3] == 1 ? true : false, event.localGen);
                assertEquals(evs[4], event.authFailureReason);
                assertEquals(evs[5] == 1 ? true : false, event.associationTimedOut);
                assertEquals(evs[6], event.supplicantStateChangesBitmask);
                assertConfigInfoEqualsWifiConfig(
                        evs[7] == 1 ? mTestWifiConfig : null, event.configInfo);
                j++;
            }
        }
        for (int i = 0; i < mTestStaLogInts.length; i++) {
            StaEvent event = wifiLog.staEventList[j];
            int[] evs = mExpectedValues[j];
            assertEquals(evs[0], event.type);
            assertEquals(evs[1], event.reason);
            assertEquals(evs[2], event.status);
            assertEquals(evs[3] == 1 ? true : false, event.localGen);
            assertEquals(evs[4], event.authFailureReason);
            assertEquals(evs[5] == 1 ? true : false, event.associationTimedOut);
            assertEquals(evs[6], event.supplicantStateChangesBitmask);
            assertConfigInfoEqualsWifiConfig(
                    evs[7] == 1 ? mTestWifiConfig : null, event.configInfo);
            assertEquals(true, event.screenOn);
            assertEquals(true, event.isCellularDataAvailable);
            assertEquals(true, event.isAdaptiveConnectivityEnabled);
            j++;
        }
        assertEquals(mExpectedValues.length, j);
    }

    /**
     * Generate StaEvents of each type, ensure all the different values are logged correctly,
     * and that they survive serialization & de-serialization
     */
    @Test
    public void testStaEventsLogSerializeDeserialize() throws Exception {
        generateStaEvents(mWifiMetrics);
        dumpProtoAndDeserialize();
        verifyDeserializedStaEvents(mDecodedProto);
    }

    /**
     * Ensure the number of StaEvents does not exceed MAX_STA_EVENTS by generating lots of events
     * and checking how many are deserialized
     */
    @Test
    public void testStaEventBounding() throws Exception {
        for (int i = 0; i < (WifiMetrics.MAX_STA_EVENTS + 10); i++) {
            mWifiMetrics.logStaEvent(TEST_IFACE_NAME, StaEvent.TYPE_CMD_START_CONNECT);
        }
        dumpProtoAndDeserialize();
        assertEquals(WifiMetrics.MAX_STA_EVENTS, mDecodedProto.staEventList.length);
    }

    /**
     * Tests that link probe StaEvents do not exceed
     * {@link WifiMetrics#MAX_LINK_PROBE_STA_EVENTS}.
     */
    @Test
    public void testLinkProbeStaEventBounding() throws Exception {
        for (int i = 0; i < WifiMetrics.MAX_LINK_PROBE_STA_EVENTS; i++) {
            mWifiMetrics.logLinkProbeSuccess(TEST_IFACE_NAME, 0, 0, 0, 0);
            mWifiMetrics.logLinkProbeFailure(TEST_IFACE_NAME, 0, 0, 0, 0);
        }
        for (int i = 0; i < 10; i++) {
            mWifiMetrics.logStaEvent(TEST_IFACE_NAME, StaEvent.TYPE_CMD_START_CONNECT);
        }

        dumpProtoAndDeserialize();

        long numLinkProbeStaEvents = Arrays.stream(mDecodedProto.staEventList)
                .filter(event -> event.type == TYPE_LINK_PROBE)
                .count();
        assertEquals(WifiMetrics.MAX_LINK_PROBE_STA_EVENTS, numLinkProbeStaEvents);
        assertEquals(WifiMetrics.MAX_LINK_PROBE_STA_EVENTS + 10, mDecodedProto.staEventList.length);
    }

    /**
     * Test the logging of UserActionEvent with a valid network ID
     */
    @Test
    public void testLogUserActionEventValidNetworkId() throws Exception {
        int testEventType = WifiMetricsProto.UserActionEvent.EVENT_FORGET_WIFI;
        int testNetworkId = 0;
        long testStartTimeMillis = 123123L;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(testStartTimeMillis);
        WifiConfiguration config = WifiConfigurationTestUtil.createPasspointNetwork();
        config.ephemeral = true;
        when(mWcm.getConfiguredNetwork(testNetworkId)).thenReturn(config);

        mWifiMetrics.logUserActionEvent(testEventType, testNetworkId);
        dumpProtoAndDeserialize();

        WifiMetricsProto.UserActionEvent[] userActionEvents = mDecodedProto.userActionEvents;
        assertEquals(1, userActionEvents.length);
        assertEquals(WifiMetricsProto.UserActionEvent.EVENT_FORGET_WIFI,
                userActionEvents[0].eventType);
        assertEquals(testStartTimeMillis, userActionEvents[0].startTimeMillis);
        assertEquals(true, userActionEvents[0].targetNetworkInfo.isEphemeral);
        assertEquals(true, userActionEvents[0].targetNetworkInfo.isPasspoint);

        // Verify that there are no disabled WifiConfiguration and BSSIDs
        NetworkDisableReason networkDisableReason = userActionEvents[0].networkDisableReason;
        assertEquals(NetworkDisableReason.REASON_UNKNOWN, networkDisableReason.disableReason);
        assertEquals(false, networkDisableReason.configTemporarilyDisabled);
        assertEquals(false, networkDisableReason.configPermanentlyDisabled);
        assertEquals(0, networkDisableReason.bssidDisableReasons.length);
    }

    /**
     * Verify the WifiStatus field in a UserActionEvent is populated correctly.
     * @throws Exception
     */
    @Test
    public void testLogWifiStatusInUserActionEvent() throws Exception {
        // setups WifiStatus for information
        int expectedRssi = -55;
        int testNetworkId = 1;
        int expectedTx = 1234;
        int expectedRx = 2345;

        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.getRssi()).thenReturn(expectedRssi);
        when(wifiInfo.getNetworkId()).thenReturn(testNetworkId);
        mWifiMetrics.handlePollResult(TEST_IFACE_NAME, wifiInfo);
        mWifiMetrics.incrementThroughputKbpsCount(expectedTx, expectedRx, RSSI_POLL_FREQUENCY);
        mWifiMetrics.setNominatorForNetwork(testNetworkId,
                WifiMetricsProto.ConnectionEvent.NOMINATOR_SAVED_USER_CONNECT_CHOICE);

        // generate a user action event and then verify fields
        int testEventType = WifiMetricsProto.UserActionEvent.EVENT_FORGET_WIFI;
        mWifiMetrics.logUserActionEvent(testEventType, testNetworkId);
        dumpProtoAndDeserialize();

        WifiMetricsProto.UserActionEvent[] userActionEvents = mDecodedProto.userActionEvents;
        assertEquals(1, userActionEvents.length);
        assertEquals(WifiMetricsProto.UserActionEvent.EVENT_FORGET_WIFI,
                userActionEvents[0].eventType);
        assertEquals(expectedRssi, userActionEvents[0].wifiStatus.lastRssi);
        assertEquals(expectedTx, userActionEvents[0].wifiStatus.estimatedTxKbps);
        assertEquals(expectedRx, userActionEvents[0].wifiStatus.estimatedRxKbps);
        assertTrue(userActionEvents[0].wifiStatus.isStuckDueToUserConnectChoice);
    }

    /**
     * verify NetworkDisableReason is populated properly when there exists a disabled
     * WifiConfiguration and BSSID.
     */
    @Test
    public void testNetworkDisableReasonInUserActionEvent() throws Exception {
        // Setup a temporarily blocked config due to DISABLED_ASSOCIATION_REJECTION
        WifiConfiguration testConfig = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkSelectionStatus status = testConfig.getNetworkSelectionStatus();
        status.setNetworkSelectionStatus(
                NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED);
        status.setNetworkSelectionDisableReason(
                NetworkSelectionStatus.DISABLED_ASSOCIATION_REJECTION);
        when(mWcm.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(testConfig);

        // Also setup the same BSSID level failure
        Set<Integer> testBssidBlocklistReasons = new HashSet<>();
        testBssidBlocklistReasons.add(WifiBlocklistMonitor.REASON_ASSOCIATION_REJECTION);
        when(mWifiBlocklistMonitor.getFailureReasonsForSsid(anyString()))
                .thenReturn(testBssidBlocklistReasons);

        // Logging the user action event
        mWifiMetrics.logUserActionEvent(WifiMetricsProto.UserActionEvent.EVENT_FORGET_WIFI,
                TEST_NETWORK_ID);
        dumpProtoAndDeserialize();

        WifiMetricsProto.UserActionEvent[] userActionEvents = mDecodedProto.userActionEvents;
        assertEquals(1, userActionEvents.length);
        assertEquals(WifiMetricsProto.UserActionEvent.EVENT_FORGET_WIFI,
                userActionEvents[0].eventType);
        NetworkDisableReason networkDisableReason = userActionEvents[0].networkDisableReason;
        assertEquals(NetworkDisableReason.REASON_ASSOCIATION_REJECTION,
                networkDisableReason.disableReason);
        assertEquals(true, networkDisableReason.configTemporarilyDisabled);
        assertEquals(false, networkDisableReason.configPermanentlyDisabled);
        assertEquals(1, networkDisableReason.bssidDisableReasons.length);
        assertEquals(NetworkDisableReason.REASON_ASSOCIATION_REJECTION,
                networkDisableReason.bssidDisableReasons[0]);
    }

    /**
     * verify that auto-join disable overrides any other disable reasons in NetworkDisableReason.
     */
    @Test
    public void testNetworkDisableReasonDisableAutojoinInUserActionEvent() throws Exception {
        // Setup a temporarily blocked config due to DISABLED_ASSOCIATION_REJECTION
        WifiConfiguration testConfig = WifiConfigurationTestUtil.createOpenNetwork();
        NetworkSelectionStatus status = testConfig.getNetworkSelectionStatus();
        status.setNetworkSelectionStatus(
                NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED);
        status.setNetworkSelectionDisableReason(
                NetworkSelectionStatus.DISABLED_ASSOCIATION_REJECTION);
        when(mWcm.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(testConfig);

        // Disable autojoin
        testConfig.allowAutojoin = false;

        // Logging the user action event
        mWifiMetrics.logUserActionEvent(WifiMetricsProto.UserActionEvent.EVENT_FORGET_WIFI,
                TEST_NETWORK_ID);
        dumpProtoAndDeserialize();

        WifiMetricsProto.UserActionEvent[] userActionEvents = mDecodedProto.userActionEvents;
        NetworkDisableReason networkDisableReason = userActionEvents[0].networkDisableReason;
        assertEquals(NetworkDisableReason.REASON_AUTO_JOIN_DISABLED,
                networkDisableReason.disableReason);
        assertEquals(false, networkDisableReason.configTemporarilyDisabled);
        assertEquals(true, networkDisableReason.configPermanentlyDisabled);
    }

    /**
     * Test the logging of UserActionEvent with invalid network ID
     */
    @Test
    public void testLogUserActionEventInvalidNetworkId() throws Exception {
        int testEventType = WifiMetricsProto.UserActionEvent.EVENT_FORGET_WIFI;
        int testNetworkId = 0;
        long testStartTimeMillis = 123123L;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(testStartTimeMillis);
        when(mWcm.getConfiguredNetwork(testNetworkId)).thenReturn(null);

        mWifiMetrics.logUserActionEvent(testEventType, testNetworkId);
        dumpProtoAndDeserialize();

        WifiMetricsProto.UserActionEvent[] userActionEvents = mDecodedProto.userActionEvents;
        assertEquals(1, userActionEvents.length);
        assertEquals(WifiMetricsProto.UserActionEvent.EVENT_FORGET_WIFI,
                userActionEvents[0].eventType);
        assertEquals(testStartTimeMillis, userActionEvents[0].startTimeMillis);
        assertNull(userActionEvents[0].targetNetworkInfo);
    }

    /**
     * Test the logging of UserActionEvent for Adaptive Connectivity toggle
     */
    @Test
    public void testLogUserActionEventForAdaptiveConnectivity() throws Exception {
        long testStartTimeMillis = 123123L;
        boolean adaptiveConnectivityEnabled = true;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(testStartTimeMillis);
        mWifiMetrics.logUserActionEvent(
                mWifiMetrics.convertAdaptiveConnectivityStateToUserActionEventType(
                        adaptiveConnectivityEnabled));
        long testStartTimeMillis2 = 200000L;
        boolean adaptiveConnectivityEnabled2 = false;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(testStartTimeMillis2);
        mWifiMetrics.logUserActionEvent(
                mWifiMetrics.convertAdaptiveConnectivityStateToUserActionEventType(
                        adaptiveConnectivityEnabled2));
        dumpProtoAndDeserialize();

        WifiMetricsProto.UserActionEvent[] userActionEvents = mDecodedProto.userActionEvents;
        assertEquals(2, userActionEvents.length);
        assertEquals(WifiMetricsProto.UserActionEvent.EVENT_CONFIGURE_ADAPTIVE_CONNECTIVITY_ON,
                userActionEvents[0].eventType);
        assertEquals(testStartTimeMillis, userActionEvents[0].startTimeMillis);
        assertEquals(WifiMetricsProto.UserActionEvent.EVENT_CONFIGURE_ADAPTIVE_CONNECTIVITY_OFF,
                userActionEvents[1].eventType);
        assertEquals(testStartTimeMillis2, userActionEvents[1].startTimeMillis);
    }

    /**
     * Verify that the max length of the UserActionEvent list is limited to MAX_USER_ACTION_EVENTS.
     */
    @Test
    public void testLogUserActionEventCapped() throws Exception {
        for (int i = 0; i < WifiMetrics.MAX_USER_ACTION_EVENTS + 1; i++) {
            mWifiMetrics.logUserActionEvent(WifiMetricsProto.UserActionEvent.EVENT_FORGET_WIFI, 0);
        }
        dumpProtoAndDeserialize();
        assertEquals(WifiMetrics.MAX_USER_ACTION_EVENTS, mDecodedProto.userActionEvents.length);
    }

    /**
     * Ensure WifiMetrics doesn't cause a null pointer exception when called with null args
     */
    @Test
    public void testDumpNullArg() {
        mWifiMetrics.dump(new FileDescriptor(), new PrintWriter(new StringWriter()), null);
    }

    /**
     * Test the generation of 'NumConnectableNetwork' histograms from two scans of different
     * ScanDetails produces the correct histogram values, and relevant bounds are observed
     */
    @MediumTest
    @Test
    public void testNumConnectableNetworksGeneration() throws Exception {
        List<ScanDetail> scan = new ArrayList<ScanDetail>();
        //                                ssid, bssid, isOpen, isSaved, isProvider, isWeakRssi)
        scan.add(buildMockScanDetail("PASSPOINT_1", "bssid0", false, false, true, false));
        scan.add(buildMockScanDetail("PASSPOINT_2", "bssid1", false, false, true, false));
        scan.add(buildMockScanDetail("SSID_B", "bssid2", true, true, false, false));
        scan.add(buildMockScanDetail("SSID_B", "bssid3", true, true, false, false));
        scan.add(buildMockScanDetail("SSID_C", "bssid4", true, false, false, false));
        scan.add(buildMockScanDetail("SSID_D", "bssid5", false, true, false, false));
        scan.add(buildMockScanDetail("SSID_E", "bssid6", false, true, false, false));
        scan.add(buildMockScanDetail("SSID_F", "bssid7", false, false, false, false));
        scan.add(buildMockScanDetail("SSID_G_WEAK", "bssid9", false, false, false, true));
        scan.add(buildMockScanDetail("SSID_H_WEAK", "bssid10", false, false, false, true));
        mWifiMetrics.incrementAvailableNetworksHistograms(scan, true);
        scan.add(buildMockScanDetail("SSID_B", "bssid8", true, true, false, false));
        mWifiMetrics.incrementAvailableNetworksHistograms(scan, true);
        for (int i = 0; i < NUM_PARTIAL_SCAN_RESULTS; i++) {
            mWifiMetrics.incrementAvailableNetworksHistograms(scan, false);
        }
        dumpProtoAndDeserialize();
        verifyHist(mDecodedProto.totalSsidsInScanHistogram, 1,                    a(7),    a(2));
        verifyHist(mDecodedProto.totalBssidsInScanHistogram, 2,                   a(8, 9), a(1, 1));
        verifyHist(mDecodedProto.availableOpenSsidsInScanHistogram, 1,            a(2),    a(2));
        verifyHist(mDecodedProto.availableOpenBssidsInScanHistogram, 2,           a(3, 4), a(1, 1));
        verifyHist(mDecodedProto.availableSavedSsidsInScanHistogram, 1,           a(3),    a(2));
        verifyHist(mDecodedProto.availableSavedBssidsInScanHistogram, 2,          a(4, 5), a(1, 1));
        verifyHist(mDecodedProto.availableOpenOrSavedSsidsInScanHistogram, 1,     a(4),    a(2));
        verifyHist(mDecodedProto.availableOpenOrSavedBssidsInScanHistogram, 2,    a(5, 6), a(1, 1));
        verifyHist(mDecodedProto.availableSavedPasspointProviderProfilesInScanHistogram, 1,
                                                                                  a(2),    a(2));
        verifyHist(mDecodedProto.availableSavedPasspointProviderBssidsInScanHistogram, 1,
                                                                                  a(2),    a(2));
        assertEquals(2, mDecodedProto.fullBandAllSingleScanListenerResults);
        assertEquals(NUM_PARTIAL_SCAN_RESULTS, mDecodedProto.partialAllSingleScanListenerResults);

        // Check Bounds
        scan.clear();
        int lotsOfSSids = Math.max(WifiMetrics.MAX_TOTAL_SCAN_RESULT_SSIDS_BUCKET,
                WifiMetrics.MAX_CONNECTABLE_SSID_NETWORK_BUCKET) + 5;
        for (int i = 0; i < lotsOfSSids; i++) {
            scan.add(buildMockScanDetail("SSID_" + i, "bssid_" + i, true, true, false, false));
        }
        mWifiMetrics.incrementAvailableNetworksHistograms(scan, true);
        dumpProtoAndDeserialize();
        verifyHist(mDecodedProto.totalSsidsInScanHistogram, 1,
                a(WifiMetrics.MAX_TOTAL_SCAN_RESULT_SSIDS_BUCKET), a(1));
        verifyHist(mDecodedProto.availableOpenSsidsInScanHistogram, 1,
                a(WifiMetrics.MAX_CONNECTABLE_SSID_NETWORK_BUCKET), a(1));
        verifyHist(mDecodedProto.availableSavedSsidsInScanHistogram, 1,
                a(WifiMetrics.MAX_CONNECTABLE_SSID_NETWORK_BUCKET), a(1));
        verifyHist(mDecodedProto.availableOpenOrSavedSsidsInScanHistogram, 1,
                a(WifiMetrics.MAX_CONNECTABLE_SSID_NETWORK_BUCKET), a(1));
        scan.clear();
        int lotsOfBssids = Math.max(WifiMetrics.MAX_TOTAL_SCAN_RESULTS_BUCKET,
                WifiMetrics.MAX_CONNECTABLE_BSSID_NETWORK_BUCKET) + 5;
        for (int i = 0; i < lotsOfBssids; i++) {
            scan.add(buildMockScanDetail("SSID", "bssid_" + i, true, true, false, false));
        }
        mWifiMetrics.incrementAvailableNetworksHistograms(scan, true);
        dumpProtoAndDeserialize();
        verifyHist(mDecodedProto.totalBssidsInScanHistogram, 1,
                a(WifiMetrics.MAX_TOTAL_SCAN_RESULTS_BUCKET), a(1));
        verifyHist(mDecodedProto.availableOpenBssidsInScanHistogram, 1,
                a(WifiMetrics.MAX_CONNECTABLE_BSSID_NETWORK_BUCKET), a(1));
        verifyHist(mDecodedProto.availableSavedBssidsInScanHistogram, 1,
                a(WifiMetrics.MAX_CONNECTABLE_BSSID_NETWORK_BUCKET), a(1));
        verifyHist(mDecodedProto.availableOpenOrSavedBssidsInScanHistogram, 1,
                a(WifiMetrics.MAX_CONNECTABLE_BSSID_NETWORK_BUCKET), a(1));
    }

    /**
     * Test that Hotspot 2.0 (Passpoint) scan results are collected correctly and that relevant
     * bounds are observed.
     */
    @Test
    public void testObservedHotspotAps() throws Exception {
        List<ScanDetail> scan = new ArrayList<ScanDetail>();
        // 2 R1 (Unknown AP isn't counted) passpoint APs belonging to a single provider: hessid1
        long hessid1 = 10;
        int anqpDomainId1 = 5;
        scan.add(buildMockScanDetailPasspoint("PASSPOINT_XX", "00:02:03:04:05:06", hessid1,
                anqpDomainId1, NetworkDetail.HSRelease.R1, true));
        scan.add(buildMockScanDetailPasspoint("PASSPOINT_XY", "01:02:03:04:05:06", hessid1,
                anqpDomainId1, NetworkDetail.HSRelease.R1, true));
        scan.add(buildMockScanDetailPasspoint("PASSPOINT_XYZ", "02:02:03:04:05:06", hessid1,
                anqpDomainId1, NetworkDetail.HSRelease.Unknown, true));
        // 2 R2 passpoint APs belonging to a single provider: hessid2
        long hessid2 = 12;
        int anqpDomainId2 = 6;
        scan.add(buildMockScanDetailPasspoint("PASSPOINT_Y", "AA:02:03:04:05:06", hessid2,
                anqpDomainId2, NetworkDetail.HSRelease.R2, true));
        scan.add(buildMockScanDetailPasspoint("PASSPOINT_Z", "AB:02:03:04:05:06", hessid2,
                anqpDomainId2, NetworkDetail.HSRelease.R2, true));
        mWifiMetrics.incrementAvailableNetworksHistograms(scan, true);
        scan = new ArrayList<ScanDetail>();
        // 3 R2 passpoint APs belonging to a single provider: hessid3 (in next scan)
        long hessid3 = 15;
        int anqpDomainId3 = 8;
        scan.add(buildMockScanDetailPasspoint("PASSPOINT_Y", "AA:02:03:04:05:06", hessid3,
                anqpDomainId3, NetworkDetail.HSRelease.R2, true));
        scan.add(buildMockScanDetailPasspoint("PASSPOINT_Y", "AA:02:03:04:05:06", hessid3,
                anqpDomainId3, NetworkDetail.HSRelease.R2, false));
        scan.add(buildMockScanDetailPasspoint("PASSPOINT_Z", "AB:02:03:04:05:06", hessid3,
                anqpDomainId3, NetworkDetail.HSRelease.R2, true));
        // 2 R3 Passpoint APs belonging to a single provider: hessid4
        long hessid4 = 17;
        int anqpDomainId4 = 2;
        scan.add(buildMockScanDetailPasspoint("PASSPOINT_R3", "0C:02:03:04:05:01", hessid4,
                anqpDomainId4, NetworkDetail.HSRelease.R3, true));
        scan.add(buildMockScanDetailPasspoint("PASSPOINT_R3_2", "0C:02:03:04:05:02", hessid4,
                anqpDomainId4, NetworkDetail.HSRelease.R3, true));
        mWifiMetrics.incrementAvailableNetworksHistograms(scan, true);
        dumpProtoAndDeserialize();

        verifyHist(mDecodedProto.observedHotspotR1ApsInScanHistogram, 2, a(0, 2), a(1, 1));
        verifyHist(mDecodedProto.observedHotspotR2ApsInScanHistogram, 2, a(2, 3), a(1, 1));
        verifyHist(mDecodedProto.observedHotspotR3ApsInScanHistogram, 2, a(0, 2), a(1, 1));
        verifyHist(mDecodedProto.observedHotspotR1EssInScanHistogram, 2, a(0, 1), a(1, 1));
        verifyHist(mDecodedProto.observedHotspotR2EssInScanHistogram, 1, a(1), a(2));
        verifyHist(mDecodedProto.observedHotspotR3EssInScanHistogram, 2, a(0, 1), a(1, 1));
        verifyHist(mDecodedProto.observedHotspotR1ApsPerEssInScanHistogram, 1, a(2), a(1));
        verifyHist(mDecodedProto.observedHotspotR2ApsPerEssInScanHistogram, 2, a(2, 3), a(1, 1));
        verifyHist(mDecodedProto.observedHotspotR3ApsPerEssInScanHistogram, 1, a(2), a(1));

        // check bounds
        scan.clear();
        int lotsOfSSids = Math.max(WifiMetrics.MAX_TOTAL_PASSPOINT_APS_BUCKET,
                WifiMetrics.MAX_TOTAL_PASSPOINT_UNIQUE_ESS_BUCKET) + 5;
        for (int i = 0; i < lotsOfSSids; i++) {
            scan.add(buildMockScanDetailPasspoint("PASSPOINT_XX" + i, "00:02:03:04:05:06", i,
                    i + 10, NetworkDetail.HSRelease.R1, true));
            scan.add(buildMockScanDetailPasspoint("PASSPOINT_XY" + i, "AA:02:03:04:05:06", 1000 * i,
                    i + 10, NetworkDetail.HSRelease.R2, false));
            scan.add(buildMockScanDetailPasspoint("PASSPOINT_XZ" + i, "0B:02:03:04:05:06", 101 * i,
                    i + 10, NetworkDetail.HSRelease.R3, false));
        }
        mWifiMetrics.incrementAvailableNetworksHistograms(scan, true);
        dumpProtoAndDeserialize();
        verifyHist(mDecodedProto.observedHotspotR1ApsInScanHistogram, 1,
                a(WifiMetrics.MAX_TOTAL_PASSPOINT_APS_BUCKET), a(1));
        verifyHist(mDecodedProto.observedHotspotR2ApsInScanHistogram, 1,
                a(WifiMetrics.MAX_TOTAL_PASSPOINT_APS_BUCKET), a(1));
        verifyHist(mDecodedProto.observedHotspotR3ApsInScanHistogram, 1,
                a(WifiMetrics.MAX_TOTAL_PASSPOINT_APS_BUCKET), a(1));
        verifyHist(mDecodedProto.observedHotspotR1EssInScanHistogram, 1,
                a(WifiMetrics.MAX_TOTAL_PASSPOINT_UNIQUE_ESS_BUCKET), a(1));
        verifyHist(mDecodedProto.observedHotspotR2EssInScanHistogram, 1,
                a(WifiMetrics.MAX_TOTAL_PASSPOINT_UNIQUE_ESS_BUCKET), a(1));
        verifyHist(mDecodedProto.observedHotspotR3EssInScanHistogram, 1,
                a(WifiMetrics.MAX_TOTAL_PASSPOINT_UNIQUE_ESS_BUCKET), a(1));
    }

    /**
     * Test that IEEE 802.11mc scan results are collected correctly and that relevant
     * bounds are observed.
     */
    @Test
    public void testObserved80211mcAps() throws Exception {
        ScanDetail mockScanDetailNon80211mc = mock(ScanDetail.class);
        ScanDetail mockScanDetail80211mc = mock(ScanDetail.class);
        NetworkDetail mockNetworkDetailNon80211mc = mock(NetworkDetail.class);
        NetworkDetail mockNetworkDetail80211mc = mock(NetworkDetail.class);
        when(mockNetworkDetail80211mc.is80211McResponderSupport()).thenReturn(true);
        ScanResult mockScanResult = mock(ScanResult.class);
        mockScanResult.capabilities = "";
        when(mockScanDetailNon80211mc.getNetworkDetail()).thenReturn(mockNetworkDetailNon80211mc);
        when(mockScanDetail80211mc.getNetworkDetail()).thenReturn(mockNetworkDetail80211mc);
        when(mockScanDetailNon80211mc.getScanResult()).thenReturn(mockScanResult);
        when(mockScanDetail80211mc.getScanResult()).thenReturn(mockScanResult);
        when(mWns.isSignalTooWeak(eq(mockScanDetail80211mc.getScanResult()))).thenReturn(true);
        List<ScanDetail> scan = new ArrayList<ScanDetail>();

        // 4 scans (a few non-802.11mc supporting APs on each)
        //  scan1: no 802.11mc supporting APs

        scan.add(mockScanDetailNon80211mc);
        scan.add(mockScanDetailNon80211mc);
        mWifiMetrics.incrementAvailableNetworksHistograms(scan, true);

        //  scan2: 2 802.11mc supporting APs
        scan.clear();
        scan.add(mockScanDetailNon80211mc);
        scan.add(mockScanDetail80211mc);
        scan.add(mockScanDetail80211mc);
        mWifiMetrics.incrementAvailableNetworksHistograms(scan, true);

        //  scan3: 100 802.11mc supporting APs (> limit)
        scan.clear();
        scan.add(mockScanDetailNon80211mc);
        scan.add(mockScanDetailNon80211mc);
        scan.add(mockScanDetailNon80211mc);
        for (int i = 0; i < 100; ++i) {
            scan.add(mockScanDetail80211mc);
        }
        mWifiMetrics.incrementAvailableNetworksHistograms(scan, true);

        //  scan4: 2 802.11mc supporting APs
        scan.clear();
        scan.add(mockScanDetailNon80211mc);
        scan.add(mockScanDetail80211mc);
        scan.add(mockScanDetail80211mc);
        scan.add(mockScanDetailNon80211mc);
        mWifiMetrics.incrementAvailableNetworksHistograms(scan, true);

        dumpProtoAndDeserialize();

        verifyHist(mDecodedProto.observed80211McSupportingApsInScanHistogram, 3,
                a(0, 2, WifiMetrics.MAX_TOTAL_80211MC_APS_BUCKET), a(1, 2, 1));
    }


    /**
     * Test Open Network Notification blocklist size and feature state are not cleared when proto
     * is dumped.
     */
    @Test
    public void testOpenNetworkNotificationBlocklistSizeAndFeatureStateNotCleared()
            throws Exception {
        mWifiMetrics.setNetworkRecommenderBlocklistSize(OPEN_NET_NOTIFIER_TAG,
                SIZE_OPEN_NETWORK_RECOMMENDER_BLOCKLIST);
        mWifiMetrics.setIsWifiNetworksAvailableNotificationEnabled(OPEN_NET_NOTIFIER_TAG,
                IS_WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON);
        for (int i = 0; i < NUM_OPEN_NETWORK_RECOMMENDATION_UPDATES; i++) {
            mWifiMetrics.incrementNumNetworkRecommendationUpdates(OPEN_NET_NOTIFIER_TAG);
        }

        // This should clear most metrics in mWifiMetrics
        dumpProtoAndDeserialize();
        assertEquals(SIZE_OPEN_NETWORK_RECOMMENDER_BLOCKLIST,
                mDecodedProto.openNetworkRecommenderBlocklistSize);
        assertEquals(IS_WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                mDecodedProto.isWifiNetworksAvailableNotificationOn);
        assertEquals(NUM_OPEN_NETWORK_RECOMMENDATION_UPDATES,
                mDecodedProto.numOpenNetworkRecommendationUpdates);

        // Check that blocklist size and feature state persist on next dump but
        // others do not.
        dumpProtoAndDeserialize();
        assertEquals(SIZE_OPEN_NETWORK_RECOMMENDER_BLOCKLIST,
                mDecodedProto.openNetworkRecommenderBlocklistSize);
        assertEquals(IS_WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                mDecodedProto.isWifiNetworksAvailableNotificationOn);
        assertEquals(0, mDecodedProto.numOpenNetworkRecommendationUpdates);
    }

    /**
     * Check network selector id
     */
    @Test
    public void testNetworkSelectorExperimentId() throws Exception {
        final int id = 42888888;
        mWifiMetrics.setNetworkSelectorExperimentId(id);
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, mTestWifiConfig,
                "TestNetwork", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        dumpProtoAndDeserialize();
        assertEquals(id, mDecodedProto.connectionEvent[0].networkSelectorExperimentId);
    }

    /**
     * Check pmk cache
     */
    @Test
    public void testConnectionWithPmkCache() throws Exception {
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, mTestWifiConfig,
                "TestNetwork", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.setConnectionPmkCache(TEST_IFACE_NAME, true);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        dumpProtoAndDeserialize();
        assertEquals(true, mDecodedProto.connectionEvent[0].routerFingerprint.pmkCacheEnabled);
    }

    /**
     * Check max supported link speed and consecutive connection failure count
     */
    @Test
    public void testConnectionMaxSupportedLinkSpeedConsecutiveFailureCnt() throws Exception {
        setScreenState(true);
        when(mNetworkConnectionStats.getCount(WifiScoreCard.CNT_CONSECUTIVE_CONNECTION_FAILURE))
                .thenReturn(2);
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, mTestWifiConfig,
                "TestNetwork", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.setConnectionMaxSupportedLinkSpeedMbps(TEST_IFACE_NAME,
                MAX_SUPPORTED_TX_LINK_SPEED_MBPS, MAX_SUPPORTED_RX_LINK_SPEED_MBPS);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        dumpProtoAndDeserialize();
        assertEquals(MAX_SUPPORTED_TX_LINK_SPEED_MBPS, mDecodedProto.connectionEvent[0]
                .routerFingerprint.maxSupportedTxLinkSpeedMbps);
        assertEquals(MAX_SUPPORTED_RX_LINK_SPEED_MBPS, mDecodedProto.connectionEvent[0]
                .routerFingerprint.maxSupportedRxLinkSpeedMbps);
        assertEquals(2, mDecodedProto.connectionEvent[0].numConsecutiveConnectionFailure);
        assertEquals(true, mDecodedProto.connectionEvent[0].screenOn);
    }

    /**
     * Check ScoringParams
     */
    @Test
    public void testExperimentId() throws Exception {
        final int id = 42;
        final String expectId = "x" + id;
        when(mScoringParams.getExperimentIdentifier()).thenReturn(id);
        dumpProtoAndDeserialize();
        assertEquals(expectId, mDecodedProto.scoreExperimentId);
    }

    /**
     * Check ScoringParams default case
     */
    @Test
    public void testDefaultExperimentId() throws Exception {
        final int id = 0;
        final String expectId = "";
        when(mScoringParams.getExperimentIdentifier()).thenReturn(id);
        dumpProtoAndDeserialize();
        assertEquals(expectId, mDecodedProto.scoreExperimentId);
    }

    /** short hand for instantiating an anonymous int array, instead of 'new int[]{a1, a2, ...}' */
    private int[] a(int... element) {
        return element;
    }

    private void verifyHist(WifiMetricsProto.NumConnectableNetworksBucket[] hist, int size,
            int[] keys, int[] counts) throws Exception {
        assertEquals(size, hist.length);
        for (int i = 0; i < keys.length; i++) {
            assertEquals(keys[i], hist[i].numConnectableNetworks);
            assertEquals(counts[i], hist[i].count);
        }
    }

    /**
     * Generate an RSSI delta event by creating a connection event and an RSSI poll within
     * 'interArrivalTime' milliseconds of each other.
     * Event will not be logged if interArrivalTime > mWifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS
     * successfulConnectionEvent, completeConnectionEvent, useValidScanResult and
     * dontDeserializeBeforePoll
     * each create an anomalous condition when set to false.
     */
    private void generateRssiDelta(int scanRssi, int rssiDelta,
            long interArrivalTime, boolean successfulConnectionEvent,
            boolean completeConnectionEvent, boolean useValidScanResult,
            boolean dontDeserializeBeforePoll) throws Exception {
        when(mClock.getElapsedSinceBootMillis()).thenReturn((long) 0);
        ScanResult scanResult = null;
        if (useValidScanResult) {
            scanResult = mock(ScanResult.class);
            scanResult.level = scanRssi;
        }
        WifiConfiguration config = mock(WifiConfiguration.class);
        WifiConfiguration.NetworkSelectionStatus networkSelectionStat =
                mock(WifiConfiguration.NetworkSelectionStatus.class);
        config.allowedKeyManagement = new BitSet();
        when(networkSelectionStat.getCandidate()).thenReturn(scanResult);
        when(config.getNetworkSelectionStatus()).thenReturn(networkSelectionStat);
        SecurityParams securityParams = mock(SecurityParams.class);
        when(config.getDefaultSecurityParams()).thenReturn(securityParams);
        when(securityParams.isEnterpriseSecurityType()).thenReturn(true);
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config,
                "TestNetwork", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        if (completeConnectionEvent) {
            if (successfulConnectionEvent) {
                mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                        WifiMetrics.ConnectionEvent.FAILURE_NONE,
                        WifiMetricsProto.ConnectionEvent.HLF_NONE,
                        WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                        TEST_CONNECTION_FAILURE_STATUS_CODE);
            } else {
                mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                        WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                        WifiMetricsProto.ConnectionEvent.HLF_NONE,
                        WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                        TEST_CONNECTION_FAILURE_STATUS_CODE);
            }
        }
        when(mClock.getElapsedSinceBootMillis()).thenReturn(interArrivalTime);
        if (!dontDeserializeBeforePoll) {
            dumpProtoAndDeserialize();
        }
        mWifiMetrics.incrementRssiPollRssiCount(RSSI_POLL_FREQUENCY, scanRssi + rssiDelta);
    }

    /**
     * Generate an RSSI delta event, with all extra conditions set to true.
     */
    private void generateRssiDelta(int scanRssi, int rssiDelta,
            long interArrivalTime) throws Exception {
        generateRssiDelta(scanRssi, rssiDelta, interArrivalTime, true, true, true, true);
    }

    private void assertStringContains(
            String actualString, String expectedSubstring) {
        assertTrue("Expected text not found in: " + actualString,
                actualString.contains(expectedSubstring));
    }

    private String getStateDump() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(stream);
        String[] args = new String[0];
        mWifiMetrics.dump(null, writer, args);
        writer.flush();
        return stream.toString();
    }

    private static final int TEST_ALLOWED_KEY_MANAGEMENT = 16;
    private static final int TEST_ALLOWED_PROTOCOLS = 22;
    private static final int TEST_ALLOWED_AUTH_ALGORITHMS = 11;
    private static final int TEST_ALLOWED_PAIRWISE_CIPHERS = 67;
    private static final int TEST_ALLOWED_GROUP_CIPHERS = 231;
    private static final int TEST_CANDIDATE_LEVEL = -80;
    private static final int TEST_CANDIDATE_FREQ = 2450;
    private static final int TEST_CARRIER_ID = 100;

    private WifiConfiguration createComplexWifiConfig() {
        WifiConfiguration config = new WifiConfiguration();
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        config.SSID = SSID;
        config.allowedKeyManagement = intToBitSet(TEST_ALLOWED_KEY_MANAGEMENT);
        config.allowedProtocols = intToBitSet(TEST_ALLOWED_PROTOCOLS);
        config.allowedAuthAlgorithms = intToBitSet(TEST_ALLOWED_AUTH_ALGORITHMS);
        config.allowedPairwiseCiphers = intToBitSet(TEST_ALLOWED_PAIRWISE_CIPHERS);
        config.allowedGroupCiphers = intToBitSet(TEST_ALLOWED_GROUP_CIPHERS);
        config.hiddenSSID = true;
        config.ephemeral = true;
        config.getNetworkSelectionStatus().setHasEverConnected(true);
        config.carrierId = TEST_CARRIER_ID;
        ScanResult candidate = new ScanResult();
        candidate.level = TEST_CANDIDATE_LEVEL;
        candidate.frequency = TEST_CANDIDATE_FREQ;
        config.getNetworkSelectionStatus().setCandidate(candidate);
        return config;
    }

    private void assertConfigInfoEqualsWifiConfig(WifiConfiguration config,
            StaEvent.ConfigInfo info) {
        if (config == null && info == null) return;
        assertEquals(config.allowedKeyManagement,   intToBitSet(info.allowedKeyManagement));
        assertEquals(config.allowedProtocols,       intToBitSet(info.allowedProtocols));
        assertEquals(config.allowedAuthAlgorithms,  intToBitSet(info.allowedAuthAlgorithms));
        assertEquals(config.allowedPairwiseCiphers, intToBitSet(info.allowedPairwiseCiphers));
        assertEquals(config.allowedGroupCiphers,    intToBitSet(info.allowedGroupCiphers));
        assertEquals(config.hiddenSSID, info.hiddenSsid);
        assertEquals(config.ephemeral, info.isEphemeral);
        assertEquals(config.getNetworkSelectionStatus().hasEverConnected(),
                info.hasEverConnected);
        assertEquals(config.getNetworkSelectionStatus().getCandidate().level, info.scanRssi);
        assertEquals(config.getNetworkSelectionStatus().getCandidate().frequency, info.scanFreq);
    }

    /**
     * Sets the values of bitSet to match an int mask
     */
    private static BitSet intToBitSet(int mask) {
        BitSet bitSet = new BitSet();
        for (int bitIndex = 0; mask > 0; mask >>>= 1, bitIndex++) {
            if ((mask & 1) != 0) bitSet.set(bitIndex);
        }
        return bitSet;
    }

    private static final int NUM_UNUSABLE_EVENT = 5;
    private static final int NUM_UNUSABLE_EVENT_TIME_THROTTLE = 3;

    /**
     * Values used to generate WifiIsUnusableEvent
     * <WifiIsUnusableEvent.TriggerType>, <last_score>, <tx_success_delta>, <tx_retries_delta>,
     * <tx_bad_delta>, <rx_success_delta>, <packet_update_time_delta>, <firmware_alert_code>,
     * <last_wifi_usability_score>, <mobile_tx_bytes>, <mobile_rx_bytes>, <total_tx_bytes>,
     * <total_rx_bytes>,
     */
    private int[][] mTestUnusableEvents = {
        {WifiIsUnusableEvent.TYPE_DATA_STALL_BAD_TX,        60,  60,  50,  40,  30,  1000,  -1, 51,
                11, 12, 13, 14},
        {WifiIsUnusableEvent.TYPE_DATA_STALL_TX_WITHOUT_RX, 55,  40,  30,  0,   0,   500,   -1, 52,
                15, 16, 17, 18},
        {WifiIsUnusableEvent.TYPE_DATA_STALL_BOTH,          60,  90,  30,  30,  0,   1000,  -1, 53,
                19, 20, 21, 22},
        {WifiIsUnusableEvent.TYPE_FIRMWARE_ALERT,           55,  55,  30,  15,  10,  1000,   4, 54,
                23, 24, 25, 26},
        {WifiIsUnusableEvent.TYPE_IP_REACHABILITY_LOST,     50,  56,  28,  17,  12,  1000,  -1, 45,
                27, 28, 29, 30}
    };

    /**
     * Generate all WifiIsUnusableEvents from mTestUnusableEvents
     */
    private void generateAllUnusableEvents(WifiMetrics wifiMetrics) {
        for (int i = 0; i < mTestUnusableEvents.length; i++) {
            generateUnusableEventAtGivenTime(i, i * (WifiMetrics.MIN_DATA_STALL_WAIT_MS + 1000));
        }
    }

    /**
     * Generate a WifiIsUnusableEvent at the given timestamp with data from
     * mTestUnusableEvents[index]
     */
    private void generateUnusableEventAtGivenTime(int index, long eventTime) {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(eventTime);
        int[] trigger = mTestUnusableEvents[index];
        when(mFacade.getMobileTxBytes()).thenReturn((long) trigger[9]);
        when(mFacade.getMobileRxBytes()).thenReturn((long) trigger[10]);
        when(mFacade.getTotalTxBytes()).thenReturn((long) trigger[11]);
        when(mFacade.getTotalRxBytes()).thenReturn((long) trigger[12]);
        mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, trigger[1]);
        mWifiMetrics.incrementWifiUsabilityScoreCount(TEST_IFACE_NAME, 1, trigger[8], 15);
        mWifiMetrics.updateWifiIsUnusableLinkLayerStats(trigger[2], trigger[3], trigger[4],
                trigger[5], trigger[6]);
        setScreenState(true);
        switch(trigger[0]) {
            case WifiIsUnusableEvent.TYPE_DATA_STALL_BAD_TX:
            case WifiIsUnusableEvent.TYPE_DATA_STALL_TX_WITHOUT_RX:
            case WifiIsUnusableEvent.TYPE_DATA_STALL_BOTH:
                mWifiMetrics.logWifiIsUnusableEvent(TEST_IFACE_NAME, trigger[0]);
                break;
            case WifiIsUnusableEvent.TYPE_FIRMWARE_ALERT:
                mWifiMetrics.logWifiIsUnusableEvent(TEST_IFACE_NAME, trigger[0], trigger[7]);
                break;
            case WifiIsUnusableEvent.TYPE_IP_REACHABILITY_LOST:
                mWifiMetrics.logWifiIsUnusableEvent(TEST_IFACE_NAME, trigger[0]);
                break;
            default:
                break;
        }
    }

    /**
     * Verify that WifiIsUnusableEvent in wifiLog matches mTestUnusableEvents
     */
    private void verifyDeserializedUnusableEvents(WifiMetricsProto.WifiLog wifiLog) {
        assertEquals(NUM_UNUSABLE_EVENT, wifiLog.wifiIsUnusableEventList.length);
        for (int i = 0; i < mTestUnusableEvents.length; i++) {
            WifiIsUnusableEvent event = wifiLog.wifiIsUnusableEventList[i];
            verifyUnusableEvent(event, i);
        }
    }

    /**
     * Verify that the given WifiIsUnusableEvent matches mTestUnusableEvents
     * at given index
     */
    private void verifyUnusableEvent(WifiIsUnusableEvent event, int index) {
        int[] expectedValues = mTestUnusableEvents[index];
        assertEquals(expectedValues[0], event.type);
        assertEquals(expectedValues[1], event.lastScore);
        assertEquals(expectedValues[2], event.txSuccessDelta);
        assertEquals(expectedValues[3], event.txRetriesDelta);
        assertEquals(expectedValues[4], event.txBadDelta);
        assertEquals(expectedValues[5], event.rxSuccessDelta);
        assertEquals(expectedValues[6], event.packetUpdateTimeDelta);
        assertEquals(expectedValues[7], event.firmwareAlertCode);
        assertEquals(expectedValues[8], event.lastWifiUsabilityScore);
        assertEquals(true, event.screenOn);
        assertEquals(expectedValues[9], event.mobileTxBytes);
        assertEquals(expectedValues[10], event.mobileRxBytes);
        assertEquals(expectedValues[11], event.totalTxBytes);
        assertEquals(expectedValues[12], event.totalRxBytes);
    }

    /**
     * Generate WifiIsUnusableEvent and verify that they are logged correctly
     */
    @Test
    public void testUnusableEventLogSerializeDeserialize() throws Exception {
        generateAllUnusableEvents(mWifiMetrics);
        dumpProtoAndDeserialize();
        verifyDeserializedUnusableEvents(mDecodedProto);
    }

    /**
     * Generate WifiIsUnUsableReported and verify that they are logged correctly when no external
     * scorer is ON.
     */
    @Test
    public void testWifiIsUnUsableReportedWithNoExternalScorer() throws Exception {
        generateAllUnusableEvents(mWifiMetrics);
        for (int i = 0; i < mTestUnusableEvents.length; i++) {
            int index = i;
            ExtendedMockito.verify(() -> WifiStatsLog.write(WIFI_IS_UNUSABLE_REPORTED,
                    mTestUnusableEvents[index][0], Process.WIFI_UID, false,
                    WIFI_IS_UNUSABLE_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_UNKNOWN));
        }
    }

    /**
     * Generate WifiIsUnUsableReported and verify that they are logged correctly when external
     * scorer is ON.
     */
    @Test
    public void testWifiIsUnUsableReportedWithExternalScorer() throws Exception {
        mWifiMetrics.setIsExternalWifiScorerOn(true, TEST_UID);
        mWifiMetrics.setScorerPredictedWifiUsabilityState(TEST_IFACE_NAME,
                WifiMetrics.WifiUsabilityState.USABLE);
        generateAllUnusableEvents(mWifiMetrics);
        for (int i = 0; i < mTestUnusableEvents.length; i++) {
            int index = i;
            ExtendedMockito.verify(() -> WifiStatsLog.write(WIFI_IS_UNUSABLE_REPORTED,
                    mTestUnusableEvents[index][0], TEST_UID, true,
                    WIFI_IS_UNUSABLE_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_USABLE));
        }
    }

    /**
     * Verify that the number of WifiIsUnusableEvents does not exceed MAX_UNUSABLE_EVENTS
     */
    @Test
    public void testUnusableEventBounding() throws Exception {
        for (int i = 0; i < (WifiMetrics.MAX_UNUSABLE_EVENTS + 2); i++) {
            generateAllUnusableEvents(mWifiMetrics);
        }
        dumpProtoAndDeserialize();
        assertEquals(WifiMetrics.MAX_UNUSABLE_EVENTS, mDecodedProto.wifiIsUnusableEventList.length);
    }

    /**
     * Verify that we don't generate new WifiIsUnusableEvent from data stalls
     * until MIN_DATA_STALL_WAIT_MS has passed since the last data stall WifiIsUnusableEvent
     */
    @Test
    public void testUnusableEventTimeThrottleForDataStall() throws Exception {
        generateUnusableEventAtGivenTime(0, 0);
        // should be time throttled
        generateUnusableEventAtGivenTime(1, 1);
        generateUnusableEventAtGivenTime(2, WifiMetrics.MIN_DATA_STALL_WAIT_MS + 1000);
        // no time throttle for firmware alert
        generateUnusableEventAtGivenTime(3, WifiMetrics.MIN_DATA_STALL_WAIT_MS + 1001);
        dumpProtoAndDeserialize();
        assertEquals(NUM_UNUSABLE_EVENT_TIME_THROTTLE,
                mDecodedProto.wifiIsUnusableEventList.length);
        verifyUnusableEvent(mDecodedProto.wifiIsUnusableEventList[0], 0);
        verifyUnusableEvent(mDecodedProto.wifiIsUnusableEventList[1], 2);
        verifyUnusableEvent(mDecodedProto.wifiIsUnusableEventList[2], 3);
    }

    /**
     * Verify that LinkSpeedCounts is correctly logged in metrics
     */
    @Test
    public void testLinkSpeedCounts() throws Exception {
        mResources.setBoolean(R.bool.config_wifiLinkSpeedMetricsEnabled, true);
        for (int i = 0; i < NUM_LINK_SPEED_LEVELS_TO_INCREMENT; i++) {
            for (int j = 0; j <= i; j++) {
                mWifiMetrics.incrementLinkSpeedCount(
                        WifiMetrics.MIN_LINK_SPEED_MBPS + i, TEST_RSSI_LEVEL);
            }
        }
        dumpProtoAndDeserialize();
        assertEquals(NUM_LINK_SPEED_LEVELS_TO_INCREMENT, mDecodedProto.linkSpeedCounts.length);
        for (int i = 0; i < NUM_LINK_SPEED_LEVELS_TO_INCREMENT; i++) {
            assertEquals("Incorrect link speed", WifiMetrics.MIN_LINK_SPEED_MBPS + i,
                    mDecodedProto.linkSpeedCounts[i].linkSpeedMbps);
            assertEquals("Incorrect count of link speed",
                    i + 1, mDecodedProto.linkSpeedCounts[i].count);
            assertEquals("Incorrect sum of absolute values of rssi values",
                    Math.abs(TEST_RSSI_LEVEL) * (i + 1),
                    mDecodedProto.linkSpeedCounts[i].rssiSumDbm);
            assertEquals("Incorrect sum of squares of rssi values",
                    TEST_RSSI_LEVEL * TEST_RSSI_LEVEL * (i + 1),
                    mDecodedProto.linkSpeedCounts[i].rssiSumOfSquaresDbmSq);
        }
    }

    /**
     * Verify that Tx and Rx per-band LinkSpeedCounts are correctly logged in metrics
     */
    @Test
    public void testTxRxLinkSpeedBandCounts() throws Exception {
        mResources.setBoolean(R.bool.config_wifiLinkSpeedMetricsEnabled, true);
        for (int i = 0; i < NUM_LINK_SPEED_LEVELS_TO_INCREMENT; i++) {
            for (int j = 0; j <= i; j++) {
                mWifiMetrics.incrementTxLinkSpeedBandCount(
                        WifiMetrics.MIN_LINK_SPEED_MBPS + i, RSSI_POLL_FREQUENCY);
                mWifiMetrics.incrementRxLinkSpeedBandCount(
                        WifiMetrics.MIN_LINK_SPEED_MBPS + i + 1, RSSI_POLL_FREQUENCY);
            }
        }
        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.txLinkSpeedCount2G.length);
        assertEquals(0, mDecodedProto.rxLinkSpeedCount2G.length);
        assertEquals(NUM_LINK_SPEED_LEVELS_TO_INCREMENT,
                mDecodedProto.txLinkSpeedCount5GLow.length);
        assertEquals(NUM_LINK_SPEED_LEVELS_TO_INCREMENT,
                mDecodedProto.rxLinkSpeedCount5GLow.length);
        assertEquals(0, mDecodedProto.txLinkSpeedCount5GMid.length);
        assertEquals(0, mDecodedProto.rxLinkSpeedCount5GMid.length);
        assertEquals(0, mDecodedProto.txLinkSpeedCount5GHigh.length);
        assertEquals(0, mDecodedProto.rxLinkSpeedCount5GHigh.length);
        for (int i = 0; i < NUM_LINK_SPEED_LEVELS_TO_INCREMENT; i++) {
            assertEquals("Incorrect Tx link speed", WifiMetrics.MIN_LINK_SPEED_MBPS + i,
                    mDecodedProto.txLinkSpeedCount5GLow[i].key);
            assertEquals("Incorrect Rx link speed", WifiMetrics.MIN_LINK_SPEED_MBPS + i + 1,
                    mDecodedProto.rxLinkSpeedCount5GLow[i].key);
            assertEquals("Incorrect count of Tx link speed",
                    i + 1, mDecodedProto.txLinkSpeedCount5GLow[i].count);
            assertEquals("Incorrect count of Rx link speed",
                    i + 1, mDecodedProto.rxLinkSpeedCount5GLow[i].count);
        }
    }

    /**
     * Verify that LinkSpeedCounts is not logged when disabled in settings
     */
    @Test
    public void testNoLinkSpeedCountsWhenDisabled() throws Exception {
        mResources.setBoolean(R.bool.config_wifiLinkSpeedMetricsEnabled, false);
        for (int i = 0; i < NUM_LINK_SPEED_LEVELS_TO_INCREMENT; i++) {
            for (int j = 0; j <= i; j++) {
                mWifiMetrics.incrementLinkSpeedCount(
                        WifiMetrics.MIN_LINK_SPEED_MBPS + i, TEST_RSSI_LEVEL);
                mWifiMetrics.incrementTxLinkSpeedBandCount(
                        WifiMetrics.MIN_LINK_SPEED_MBPS - i, RSSI_POLL_FREQUENCY);
                mWifiMetrics.incrementRxLinkSpeedBandCount(
                        WifiMetrics.MIN_LINK_SPEED_MBPS - i, RSSI_POLL_FREQUENCY);
            }
        }
        dumpProtoAndDeserialize();
        assertEquals("LinkSpeedCounts should not be logged when disabled in settings",
                0, mDecodedProto.linkSpeedCounts.length);
        assertEquals("Tx LinkSpeedCounts should not be logged when disabled in settings",
                0, mDecodedProto.txLinkSpeedCount5GLow.length);
        assertEquals("Rx LinkSpeedCounts should not be logged when disabled in settings",
                0, mDecodedProto.rxLinkSpeedCount5GLow.length);
    }

    /**
     * Verify that LinkSpeedCounts is not logged when the link speed value is lower than
     * MIN_LINK_SPEED_MBPS or when the rssi value is outside of
     * [MIN_RSSI_LEVEL, MAX_RSSI_LEVEL]
     */
    @Test
    public void testNoLinkSpeedCountsForOutOfBoundValues() throws Exception {
        mResources.setBoolean(R.bool.config_wifiLinkSpeedMetricsEnabled, true);
        for (int i = 1; i < NUM_OUT_OF_BOUND_ENTRIES; i++) {
            mWifiMetrics.incrementLinkSpeedCount(
                    WifiMetrics.MIN_LINK_SPEED_MBPS - i, MIN_RSSI_LEVEL);
            mWifiMetrics.incrementTxLinkSpeedBandCount(
                    WifiMetrics.MIN_LINK_SPEED_MBPS - i, RSSI_POLL_FREQUENCY);
            mWifiMetrics.incrementRxLinkSpeedBandCount(
                    WifiMetrics.MIN_LINK_SPEED_MBPS - i, RSSI_POLL_FREQUENCY);
        }
        for (int i = 1; i < NUM_OUT_OF_BOUND_ENTRIES; i++) {
            mWifiMetrics.incrementLinkSpeedCount(
                    WifiMetrics.MIN_LINK_SPEED_MBPS, MIN_RSSI_LEVEL - i);
        }
        for (int i = 1; i < NUM_OUT_OF_BOUND_ENTRIES; i++) {
            mWifiMetrics.incrementLinkSpeedCount(
                    WifiMetrics.MIN_LINK_SPEED_MBPS, MAX_RSSI_LEVEL + i);
        }
        dumpProtoAndDeserialize();
        assertEquals("LinkSpeedCounts should not be logged for out of bound values",
                0, mDecodedProto.linkSpeedCounts.length);
        assertEquals("Tx LinkSpeedCounts should not be logged for out of bound values",
                0, mDecodedProto.txLinkSpeedCount5GLow.length);
        assertEquals("Rx LinkSpeedCounts should not be logged for out of bound values",
                0, mDecodedProto.rxLinkSpeedCount5GLow.length);
    }

    private int nextRandInt() {
        return mRandom.nextInt(1000);
    }

    private WifiLinkLayerStats nextRandomStats(WifiLinkLayerStats current) {
        WifiLinkLayerStats out = new WifiLinkLayerStats();
        final int numLinks = 2;
        out.links = new WifiLinkLayerStats.LinkSpecificStats[numLinks];
        for (int i = 0; i < numLinks; i++) {
            out.links[i] = new WifiLinkLayerStats.LinkSpecificStats();
            out.links[i].link_id = i;
            out.links[i].txmpdu_vi = nextRandInt();
            out.links[i].txmpdu_bk = nextRandInt();
            out.links[i].radio_id = nextRandInt() % 5;
            out.links[i].rssi_mgmt = nextRandInt() % 127;
            out.links[i].beacon_rx = nextRandInt();
            out.links[i].frequencyMhz = nextRandInt();
            out.links[i].rxmpdu_be = nextRandInt();
            out.links[i].txmpdu_be = nextRandInt();
            out.links[i].lostmpdu_be = nextRandInt();
            out.links[i].retries_be = nextRandInt();
            out.links[i].contentionTimeMinBeInUsec = nextRandInt();
            out.links[i].contentionTimeMaxBeInUsec = nextRandInt();
            out.links[i].contentionTimeAvgBeInUsec = nextRandInt();
            out.links[i].contentionNumSamplesBe = nextRandInt();
            out.links[i].rxmpdu_bk = nextRandInt();
            out.links[i].txmpdu_bk = nextRandInt();
            out.links[i].lostmpdu_bk = nextRandInt();
            out.links[i].retries_bk = nextRandInt();
            out.links[i].contentionTimeMinBkInUsec = nextRandInt();
            out.links[i].contentionTimeMaxBkInUsec = nextRandInt();
            out.links[i].contentionTimeAvgBkInUsec = nextRandInt();
            out.links[i].contentionNumSamplesBk = nextRandInt();
            out.links[i].rxmpdu_vi = nextRandInt();
            out.links[i].txmpdu_vi = nextRandInt();
            out.links[i].lostmpdu_vi = nextRandInt();
            out.links[i].retries_vi = nextRandInt();
            out.links[i].contentionTimeMinViInUsec = nextRandInt();
            out.links[i].contentionTimeMaxViInUsec = nextRandInt();
            out.links[i].contentionTimeAvgViInUsec = nextRandInt();
            out.links[i].contentionNumSamplesVi = nextRandInt();
            out.links[i].rxmpdu_vo = nextRandInt();
            out.links[i].txmpdu_vo = nextRandInt();
            out.links[i].lostmpdu_vo = nextRandInt();
            out.links[i].retries_vo = nextRandInt();
            out.links[i].contentionTimeMinVoInUsec = nextRandInt();
            out.links[i].contentionTimeMaxVoInUsec = nextRandInt();
            out.links[i].contentionTimeAvgVoInUsec = nextRandInt();
            out.links[i].contentionNumSamplesVo = nextRandInt();
            out.links[i].timeSliceDutyCycleInPercent = (short) (nextRandInt() % 101);
            out.links[i].peerInfo = createNewPeerInfo(current.peerInfo);
            // Channel Stats
            WifiLinkLayerStats.ChannelStats cs = new WifiLinkLayerStats.ChannelStats();
            cs.frequency = out.links[i].frequencyMhz;
            cs.radioOnTimeMs = nextRandInt();
            cs.ccaBusyTimeMs = nextRandInt();
            out.channelStatsMap.put(out.links[i].frequencyMhz, cs);
        }

        out.timeStampInMs = current.timeStampInMs + nextRandInt();

        out.rxmpdu_be = current.rxmpdu_be + nextRandInt();
        out.txmpdu_be = current.txmpdu_be + nextRandInt();
        out.lostmpdu_be = current.lostmpdu_be + nextRandInt();
        out.retries_be = current.retries_be + nextRandInt();

        out.rxmpdu_bk = current.rxmpdu_bk + nextRandInt();
        out.txmpdu_bk = current.txmpdu_bk + nextRandInt();
        out.lostmpdu_bk = current.lostmpdu_bk + nextRandInt();
        out.retries_bk = current.retries_bk + nextRandInt();

        out.rxmpdu_vi = current.rxmpdu_vi + nextRandInt();
        out.txmpdu_vi = current.txmpdu_vi + nextRandInt();
        out.lostmpdu_vi = current.lostmpdu_vi + nextRandInt();
        out.retries_vi = current.retries_vi + nextRandInt();

        out.rxmpdu_vo = current.rxmpdu_vo + nextRandInt();
        out.txmpdu_vo = current.txmpdu_vo + nextRandInt();
        out.lostmpdu_vo = current.lostmpdu_vo + nextRandInt();
        out.retries_vo = current.retries_vo + nextRandInt();

        out.on_time = current.on_time + nextRandInt();
        out.tx_time = current.tx_time + nextRandInt();
        out.rx_time = current.rx_time + nextRandInt();
        out.on_time_scan = current.on_time_scan + nextRandInt();
        out.on_time_nan_scan = current.on_time_nan_scan + nextRandInt();
        out.on_time_background_scan = current.on_time_background_scan + nextRandInt();
        out.on_time_roam_scan = current.on_time_roam_scan + nextRandInt();
        out.on_time_pno_scan = current.on_time_pno_scan + nextRandInt();
        out.on_time_hs20_scan = current.on_time_hs20_scan + nextRandInt();
        out.timeSliceDutyCycleInPercent =
                (short) ((current.timeSliceDutyCycleInPercent + nextRandInt()) % 101);
        out.peerInfo = createNewPeerInfo(current.peerInfo);
        out.radioStats = createNewRadioStat(current.radioStats);
        return out;
    }

    private PeerInfo[] createNewPeerInfo(PeerInfo[] current) {
        if (current == null) {
            return null;
        }
        PeerInfo[] out = new PeerInfo[current.length];
        for (int i = 0; i < current.length; i++) {
            int numRates = 0;
            if (current[i].rateStats != null) {
                numRates = current[i].rateStats.length;
            }
            RateStat[] rateStats = new RateStat[numRates];
            for (int j = 0; j < numRates; j++) {
                RateStat curRate = current[i].rateStats[j];
                RateStat newRate = new RateStat();
                newRate.preamble = curRate.preamble;
                newRate.nss = curRate.nss;
                newRate.bw = curRate.bw;
                newRate.rateMcsIdx = curRate.rateMcsIdx;
                newRate.bitRateInKbps = curRate.bitRateInKbps;
                newRate.txMpdu = curRate.txMpdu + nextRandInt();
                newRate.rxMpdu = curRate.rxMpdu + nextRandInt();
                newRate.mpduLost = curRate.mpduLost + nextRandInt();
                newRate.retries = curRate.retries + nextRandInt();
                rateStats[j] = newRate;
            }
            out[i] = new PeerInfo();
            out[i].rateStats = rateStats;
            out[i].staCount = (short) (current[i].staCount + nextRandInt() % 10);
            out[i].chanUtil = (short) ((current[i].chanUtil + nextRandInt()) % 100);
        }
        return out;
    }

    private RadioStat[] createNewRadioStat(RadioStat[] current) {
        if (current == null) {
            return null;
        }
        RadioStat[] out = new RadioStat[current.length];
        for (int i = 0; i < current.length; i++) {
            RadioStat currentRadio = current[i];
            RadioStat newRadio = new RadioStat();
            newRadio.radio_id = currentRadio.radio_id;
            newRadio.on_time = currentRadio.on_time + nextRandInt();
            newRadio.tx_time = currentRadio.tx_time + nextRandInt();
            newRadio.rx_time = currentRadio.rx_time + nextRandInt();
            newRadio.on_time_scan = currentRadio.on_time_scan + nextRandInt();
            newRadio.on_time_nan_scan = currentRadio.on_time_nan_scan + nextRandInt();
            newRadio.on_time_background_scan = currentRadio.on_time_background_scan + nextRandInt();
            newRadio.on_time_roam_scan = currentRadio.on_time_roam_scan + nextRandInt();
            newRadio.on_time_pno_scan = currentRadio.on_time_pno_scan + nextRandInt();
            newRadio.on_time_hs20_scan = currentRadio.on_time_hs20_scan + nextRandInt();
            out[i] = newRadio;
        }
        return out;
    }

    private void assertWifiLinkLayerUsageHasDiff(WifiLinkLayerStats oldStats,
            WifiLinkLayerStats newStats) {
        assertEquals(newStats.timeStampInMs - oldStats.timeStampInMs,
                mDecodedProto.wifiLinkLayerUsageStats.loggingDurationMs);
        assertEquals(newStats.on_time - oldStats.on_time,
                mDecodedProto.wifiLinkLayerUsageStats.radioOnTimeMs);
        assertEquals(newStats.tx_time - oldStats.tx_time,
                mDecodedProto.wifiLinkLayerUsageStats.radioTxTimeMs);
        assertEquals(newStats.rx_time - oldStats.rx_time,
                mDecodedProto.wifiLinkLayerUsageStats.radioRxTimeMs);
        assertEquals(newStats.on_time_scan - oldStats.on_time_scan,
                mDecodedProto.wifiLinkLayerUsageStats.radioScanTimeMs);
        assertEquals(newStats.on_time_nan_scan - oldStats.on_time_nan_scan,
                mDecodedProto.wifiLinkLayerUsageStats.radioNanScanTimeMs);
        assertEquals(newStats.on_time_background_scan - oldStats.on_time_background_scan,
                mDecodedProto.wifiLinkLayerUsageStats.radioBackgroundScanTimeMs);
        assertEquals(newStats.on_time_roam_scan - oldStats.on_time_roam_scan,
                mDecodedProto.wifiLinkLayerUsageStats.radioRoamScanTimeMs);
        assertEquals(newStats.on_time_pno_scan - oldStats.on_time_pno_scan,
                mDecodedProto.wifiLinkLayerUsageStats.radioPnoScanTimeMs);
        assertEquals(newStats.on_time_hs20_scan - oldStats.on_time_hs20_scan,
                mDecodedProto.wifiLinkLayerUsageStats.radioHs20ScanTimeMs);
    }

    private void assertPerRadioStatsUsageHasDiff(WifiLinkLayerStats oldStats,
            WifiLinkLayerStats newStats) {
        assertEquals(oldStats.radioStats.length, newStats.radioStats.length);
        assertEquals(newStats.radioStats.length,
                mDecodedProto.wifiLinkLayerUsageStats.radioStats.length);
        for (int i = 0; i < oldStats.radioStats.length; i++) {
            RadioStat oldRadioStats = oldStats.radioStats[i];
            RadioStat newRadioStats = newStats.radioStats[i];
            RadioStats radioStats =
                    mDecodedProto.wifiLinkLayerUsageStats.radioStats[i];
            assertEquals(oldRadioStats.radio_id, newRadioStats.radio_id);
            assertEquals(newRadioStats.radio_id, radioStats.radioId);
            assertEquals(newRadioStats.on_time - oldRadioStats.on_time,
                    radioStats.totalRadioOnTimeMs);
            assertEquals(newRadioStats.tx_time - oldRadioStats.tx_time,
                    radioStats.totalRadioTxTimeMs);
            assertEquals(newRadioStats.rx_time - oldRadioStats.rx_time,
                    radioStats.totalRadioRxTimeMs);
            assertEquals(newRadioStats.on_time_scan - oldRadioStats.on_time_scan,
                    radioStats.totalScanTimeMs);
            assertEquals(newRadioStats.on_time_nan_scan - oldRadioStats.on_time_nan_scan,
                    radioStats.totalNanScanTimeMs);
            assertEquals(newRadioStats.on_time_background_scan
                    - oldRadioStats.on_time_background_scan,
                    radioStats.totalBackgroundScanTimeMs);
            assertEquals(newRadioStats.on_time_roam_scan - oldRadioStats.on_time_roam_scan,
                    radioStats.totalRoamScanTimeMs);
            assertEquals(newRadioStats.on_time_pno_scan - oldRadioStats.on_time_pno_scan,
                    radioStats.totalPnoScanTimeMs);
            assertEquals(newRadioStats.on_time_hs20_scan - oldRadioStats.on_time_hs20_scan,
                    radioStats.totalHotspot2ScanTimeMs);
        }
    }

    /**
     * Verify that WifiMetrics is counting link layer usage correctly when given a series of
     * valid input.
     * @throws Exception
     */
    @Test
    public void testWifiLinkLayerUsageStats() throws Exception {
        WifiLinkLayerStats stat1 = nextRandomStats(createNewWifiLinkLayerStats());
        WifiLinkLayerStats stat2 = nextRandomStats(stat1);
        WifiLinkLayerStats stat3 = nextRandomStats(stat2);
        mWifiMetrics.incrementWifiLinkLayerUsageStats(TEST_IFACE_NAME, stat1);
        mWifiMetrics.incrementWifiLinkLayerUsageStats(TEST_IFACE_NAME, stat2);
        mWifiMetrics.incrementWifiLinkLayerUsageStats(TEST_IFACE_NAME, stat3);
        dumpProtoAndDeserialize();

        // After 2 increments, the counters should have difference between |stat1| and |stat3|
        assertWifiLinkLayerUsageHasDiff(stat1, stat3);
        assertPerRadioStatsUsageHasDiff(stat1, stat3);
    }

    /**
     * Verify that null input is handled and wifi link layer usage stats are not incremented.
     * @throws Exception
     */
    @Test
    public void testWifiLinkLayerUsageStatsNullInput() throws Exception {
        WifiLinkLayerStats stat1 = nextRandomStats(createNewWifiLinkLayerStats());
        WifiLinkLayerStats stat2 = null;
        mWifiMetrics.incrementWifiLinkLayerUsageStats(TEST_IFACE_NAME, stat1);
        mWifiMetrics.incrementWifiLinkLayerUsageStats(TEST_IFACE_NAME, stat2);
        dumpProtoAndDeserialize();

        // Counter should be zero
        assertWifiLinkLayerUsageHasDiff(stat1, stat1);
        assertNotNull(mDecodedProto.wifiLinkLayerUsageStats.radioStats);
    }

    /**
     * Verify that when the new data appears to be bad link layer usage stats are not being
     * incremented and the buffered WifiLinkLayerStats get cleared.
     * @throws Exception
     */
    @Test
    public void testWifiLinkLayerUsageStatsChipReset() throws Exception {
        WifiLinkLayerStats stat1 = nextRandomStats(createNewWifiLinkLayerStats());
        WifiLinkLayerStats stat2 = nextRandomStats(stat1);
        stat2.on_time = stat1.on_time - 1;
        WifiLinkLayerStats stat3 = nextRandomStats(stat2);
        WifiLinkLayerStats stat4 = nextRandomStats(stat3);
        mWifiMetrics.incrementWifiLinkLayerUsageStats(TEST_IFACE_NAME, stat1);
        mWifiMetrics.incrementWifiLinkLayerUsageStats(TEST_IFACE_NAME, stat2);
        mWifiMetrics.incrementWifiLinkLayerUsageStats(TEST_IFACE_NAME, stat3);
        mWifiMetrics.incrementWifiLinkLayerUsageStats(TEST_IFACE_NAME, stat4);
        dumpProtoAndDeserialize();

        // Should only count the difference between |stat3| and |stat4|
        assertWifiLinkLayerUsageHasDiff(stat3, stat4);
        assertPerRadioStatsUsageHasDiff(stat3, stat4);
    }

    private void assertUsabilityStatsAssignment(WifiInfo info, WifiLinkLayerStats stats,
            WifiUsabilityStatsEntry usabilityStats) {
        assertEquals(info.getRssi(), usabilityStats.rssi);
        assertEquals(info.getLinkSpeed(), usabilityStats.linkSpeedMbps);
        assertEquals(info.getRxLinkSpeedMbps(), usabilityStats.rxLinkSpeedMbps);
        assertEquals(stats.timeStampInMs, usabilityStats.timeStampMs);
        assertEquals(stats.txmpdu_be + stats.txmpdu_bk + stats.txmpdu_vi + stats.txmpdu_vo,
                usabilityStats.totalTxSuccess);
        assertEquals(stats.retries_be + stats.retries_bk + stats.retries_vi + stats.retries_vo,
                usabilityStats.totalTxRetries);
        assertEquals(stats.lostmpdu_be + stats.lostmpdu_bk + stats.lostmpdu_vi + stats.lostmpdu_vo,
                usabilityStats.totalTxBad);
        assertEquals(stats.rxmpdu_be + stats.rxmpdu_bk + stats.rxmpdu_vi + stats.rxmpdu_vo,
                usabilityStats.totalRxSuccess);
        assertEquals(stats.radioStats.length, usabilityStats.radioStats.length);
        for (int i = 0; i < stats.radioStats.length; i++) {
            RadioStat radio = stats.radioStats[i];
            RadioStats radioStats = usabilityStats.radioStats[i];
            assertEquals(radio.radio_id, radioStats.radioId);
            assertEquals(radio.on_time, radioStats.totalRadioOnTimeMs);
            assertEquals(radio.tx_time, radioStats.totalRadioTxTimeMs);
            assertEquals(radio.rx_time, radioStats.totalRadioRxTimeMs);
            assertEquals(radio.on_time_scan, radioStats.totalScanTimeMs);
            assertEquals(radio.on_time_nan_scan, radioStats.totalNanScanTimeMs);
            assertEquals(radio.on_time_background_scan, radioStats.totalBackgroundScanTimeMs);
            assertEquals(radio.on_time_roam_scan, radioStats.totalRoamScanTimeMs);
            assertEquals(radio.on_time_pno_scan, radioStats.totalPnoScanTimeMs);
            assertEquals(radio.on_time_hs20_scan, radioStats.totalHotspot2ScanTimeMs);
        }
        assertEquals(stats.on_time, usabilityStats.totalRadioOnTimeMs);
        assertEquals(stats.tx_time, usabilityStats.totalRadioTxTimeMs);
        assertEquals(stats.rx_time, usabilityStats.totalRadioRxTimeMs);
        assertEquals(stats.on_time_scan, usabilityStats.totalScanTimeMs);
        assertEquals(stats.on_time_nan_scan, usabilityStats.totalNanScanTimeMs);
        assertEquals(stats.on_time_background_scan, usabilityStats.totalBackgroundScanTimeMs);
        assertEquals(stats.on_time_roam_scan, usabilityStats.totalRoamScanTimeMs);
        assertEquals(stats.on_time_pno_scan, usabilityStats.totalPnoScanTimeMs);
        assertEquals(stats.on_time_hs20_scan, usabilityStats.totalHotspot2ScanTimeMs);
        assertEquals(stats.beacon_rx, usabilityStats.totalBeaconRx);
        assertEquals(stats.timeSliceDutyCycleInPercent, usabilityStats.timeSliceDutyCycleInPercent);
        assertEquals(stats.contentionTimeMinBeInUsec,
                usabilityStats.contentionTimeStats[0].contentionTimeMinMicros);
        assertEquals(stats.contentionTimeMaxBeInUsec,
                usabilityStats.contentionTimeStats[0].contentionTimeMaxMicros);
        assertEquals(stats.contentionTimeAvgBeInUsec,
                usabilityStats.contentionTimeStats[0].contentionTimeAvgMicros);
        assertEquals(stats.contentionNumSamplesBe,
                usabilityStats.contentionTimeStats[0].contentionNumSamples);
        assertEquals(stats.contentionTimeMinBkInUsec,
                usabilityStats.contentionTimeStats[1].contentionTimeMinMicros);
        assertEquals(stats.contentionTimeMaxBkInUsec,
                usabilityStats.contentionTimeStats[1].contentionTimeMaxMicros);
        assertEquals(stats.contentionTimeAvgBkInUsec,
                usabilityStats.contentionTimeStats[1].contentionTimeAvgMicros);
        assertEquals(stats.contentionNumSamplesBk,
                usabilityStats.contentionTimeStats[1].contentionNumSamples);
        assertEquals(stats.contentionTimeMinViInUsec,
                usabilityStats.contentionTimeStats[2].contentionTimeMinMicros);
        assertEquals(stats.contentionTimeMaxViInUsec,
                usabilityStats.contentionTimeStats[2].contentionTimeMaxMicros);
        assertEquals(stats.contentionTimeAvgViInUsec,
                usabilityStats.contentionTimeStats[2].contentionTimeAvgMicros);
        assertEquals(stats.contentionNumSamplesVi,
                usabilityStats.contentionTimeStats[2].contentionNumSamples);
        assertEquals(stats.contentionTimeMinVoInUsec,
                usabilityStats.contentionTimeStats[3].contentionTimeMinMicros);
        assertEquals(stats.contentionTimeMaxVoInUsec,
                usabilityStats.contentionTimeStats[3].contentionTimeMaxMicros);
        assertEquals(stats.contentionTimeAvgVoInUsec,
                usabilityStats.contentionTimeStats[3].contentionTimeAvgMicros);
        assertEquals(stats.contentionNumSamplesVo,
                usabilityStats.contentionTimeStats[3].contentionNumSamples);
        for (int i = 0; i < stats.peerInfo.length; i++) {
            PeerInfo curPeer = stats.peerInfo[i];
            assertEquals(curPeer.staCount, usabilityStats.staCount);
            assertEquals(curPeer.chanUtil, usabilityStats.channelUtilization);
            for (int j = 0; j < curPeer.rateStats.length; j++) {
                RateStat rate = curPeer.rateStats[j];
                RateStats usabilityRate = usabilityStats.rateStats[j];
                assertEquals(rate.preamble, usabilityRate.preamble);
                assertEquals(rate.nss, usabilityRate.nss);
                assertEquals(rate.bw, usabilityRate.bw);
                assertEquals(rate.rateMcsIdx, usabilityRate.rateMcsIdx);
                assertEquals(rate.bitRateInKbps, usabilityRate.bitRateInKbps);
                assertEquals(rate.txMpdu, usabilityRate.txMpdu);
                assertEquals(rate.rxMpdu, usabilityRate.rxMpdu);
                assertEquals(rate.mpduLost, usabilityRate.mpduLost);
                assertEquals(rate.retries, usabilityRate.retries);
            }
        }
    }

    // Simulate adding a LABEL_GOOD WifiUsabilityStats
    private WifiLinkLayerStats addGoodWifiUsabilityStats(WifiLinkLayerStats start) {
        WifiInfo info = mock(WifiInfo.class);
        when(info.getRssi()).thenReturn(nextRandInt());
        when(info.getLinkSpeed()).thenReturn(nextRandInt());
        WifiLinkLayerStats stats = start;
        for (int i = 0; i < WifiMetrics.NUM_WIFI_USABILITY_STATS_ENTRIES_PER_WIFI_GOOD; i++) {
            mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats);
            stats = nextRandomStats(stats);
        }
        return stats;
    }

    // Simulate adding a LABEL_BAD WifiUsabilityStats
    private WifiLinkLayerStats addBadWifiUsabilityStats(WifiLinkLayerStats start) {
        WifiInfo info = mock(WifiInfo.class);
        when(info.getRssi()).thenReturn(nextRandInt());
        when(info.getLinkSpeed()).thenReturn(nextRandInt());
        WifiLinkLayerStats stats1 = start;
        WifiLinkLayerStats stats2 = nextRandomStats(stats1);
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats1);
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats2);
        mWifiMetrics.addToWifiUsabilityStatsList(TEST_IFACE_NAME, WifiUsabilityStats.LABEL_BAD,
                WifiUsabilityStats.TYPE_DATA_STALL_BAD_TX, -1);
        return nextRandomStats(stats2);
    }

    /**
     * Verify that updateWifiUsabilityStatsEntries correctly converts the inputs into
     * a WifiUsabilityStatsEntry Object and then stores it.
     *
     * Verify that the converted metrics proto contains pairs of WifiUsabilityStats with
     * LABEL_GOOD and LABEL_BAD
     * @throws Exception
     */
    @Test
    public void testUpdateWifiUsabilityStatsEntries() throws Exception {
        WifiInfo info = mock(WifiInfo.class);
        when(info.getRssi()).thenReturn(nextRandInt());
        when(info.getLinkSpeed()).thenReturn(nextRandInt());
        when(info.getRxLinkSpeedMbps()).thenReturn(nextRandInt());
        when(info.getBSSID()).thenReturn("Wifi");
        when(info.getFrequency()).thenReturn(5745);
        when(mWifiDataStall.isCellularDataAvailable()).thenReturn(true);
        when(mWifiDataStall.isThroughputSufficient()).thenReturn(false);
        when(mWifiChannelUtilization.getUtilizationRatio(anyInt())).thenReturn(150);
        when(mWifiSettingsStore.isWifiScoringEnabled()).thenReturn(true);

        WifiLinkLayerStats stats1 = nextRandomStats(createNewWifiLinkLayerStats());
        WifiLinkLayerStats stats2 = nextRandomStats(stats1);
        mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, 60);
        mWifiMetrics.incrementWifiUsabilityScoreCount(TEST_IFACE_NAME, 2, 55, 15);
        mWifiMetrics.logLinkProbeSuccess(
                TEST_IFACE_NAME, nextRandInt(), nextRandInt(), nextRandInt(), 12);
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats1);
        mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, 58);
        mWifiMetrics.incrementWifiUsabilityScoreCount(TEST_IFACE_NAME, 3, 56, 15);
        mWifiMetrics.logLinkProbeFailure(TEST_IFACE_NAME, nextRandInt(), nextRandInt(),
                nextRandInt(), nextRandInt());
        mWifiMetrics.enterDeviceMobilityState(DEVICE_MOBILITY_STATE_HIGH_MVMT);

        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats2);
        assertEquals(stats2.beacon_rx, mWifiMetrics.getTotalBeaconRxCount());
        mWifiMetrics.addToWifiUsabilityStatsList(TEST_IFACE_NAME, WifiUsabilityStats.LABEL_BAD,
                WifiUsabilityStats.TYPE_DATA_STALL_BAD_TX, -1);

        // Add 2 LABEL_GOOD but only 1 should remain in the converted proto
        WifiLinkLayerStats statsGood = addGoodWifiUsabilityStats(nextRandomStats(stats2));
        statsGood.timeStampInMs += WifiMetrics.MIN_WIFI_GOOD_USABILITY_STATS_PERIOD_MS;
        addGoodWifiUsabilityStats(statsGood);

        dumpProtoAndDeserialize();
        assertEquals(2, mDecodedProto.wifiUsabilityStatsList.length);
        assertEquals(WifiUsabilityStats.LABEL_GOOD, mDecodedProto.wifiUsabilityStatsList[0].label);
        assertEquals(WifiUsabilityStats.LABEL_BAD, mDecodedProto.wifiUsabilityStatsList[1].label);
        assertUsabilityStatsAssignment(info, stats1,
                mDecodedProto.wifiUsabilityStatsList[1].stats[0]);
        assertUsabilityStatsAssignment(info, stats2,
                mDecodedProto.wifiUsabilityStatsList[1].stats[1]);

        assertEquals(2, mDecodedProto.wifiUsabilityStatsList[1].stats[0].seqNumToFramework);
        assertEquals(3, mDecodedProto.wifiUsabilityStatsList[1].stats[1].seqNumToFramework);
        assertEquals(0, mDecodedProto.wifiUsabilityStatsList[1].stats[0].seqNumInsideFramework);
        assertEquals(1, mDecodedProto.wifiUsabilityStatsList[1].stats[1].seqNumInsideFramework);
        assertEquals(60, mDecodedProto.wifiUsabilityStatsList[1].stats[0].wifiScore);
        assertEquals(58, mDecodedProto.wifiUsabilityStatsList[1].stats[1].wifiScore);
        assertEquals(55, mDecodedProto.wifiUsabilityStatsList[1].stats[0].wifiUsabilityScore);
        assertEquals(56, mDecodedProto.wifiUsabilityStatsList[1].stats[1].wifiUsabilityScore);
        assertEquals(15, mDecodedProto.wifiUsabilityStatsList[1].stats[0].predictionHorizonSec);
        assertEquals(true, mDecodedProto.wifiUsabilityStatsList[1].stats[0].isSameBssidAndFreq);
        assertEquals(android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_SUCCESS,
                mDecodedProto.wifiUsabilityStatsList[1].stats[0].probeStatusSinceLastUpdate);
        assertEquals(android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_FAILURE,
                mDecodedProto.wifiUsabilityStatsList[1].stats[1].probeStatusSinceLastUpdate);
        assertEquals(android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_NO_PROBE,
                mDecodedProto.wifiUsabilityStatsList[0].stats[0].probeStatusSinceLastUpdate);
        assertEquals(12,
                mDecodedProto.wifiUsabilityStatsList[1].stats[0].probeElapsedTimeSinceLastUpdateMs);
        assertEquals(Integer.MAX_VALUE, mDecodedProto.wifiUsabilityStatsList[1]
                .stats[1].probeElapsedTimeSinceLastUpdateMs);
        assertEquals(-1, mDecodedProto.wifiUsabilityStatsList[0]
                .stats[0].probeElapsedTimeSinceLastUpdateMs);
        assertEquals(DEVICE_MOBILITY_STATE_HIGH_MVMT, mDecodedProto.wifiUsabilityStatsList[1]
                .stats[mDecodedProto.wifiUsabilityStatsList[1].stats.length - 1]
                .deviceMobilityState);
        assertEquals(true, mDecodedProto.wifiUsabilityStatsList[0].stats[0].isWifiScoringEnabled);
        assertEquals(true,
                mDecodedProto.wifiUsabilityStatsList[1].stats[0].isCellularDataAvailable);
        assertEquals(false,
                mDecodedProto.wifiUsabilityStatsList[1].stats[1].isThroughputSufficient);
        assertEquals(150,
                mDecodedProto.wifiUsabilityStatsList[0].stats[0].channelUtilizationRatio);
    }

    private WifiLinkLayerStats createNewWifiLinkLayerStats() {
        WifiLinkLayerStats stats = new WifiLinkLayerStats();
        RateStat[] rateStats = new RateStat[1];
        rateStats[0] = new RateStat();
        rateStats[0].preamble = 1;
        rateStats[0].nss = 1;
        rateStats[0].bw = 2;
        rateStats[0].rateMcsIdx = 5;
        rateStats[0].bitRateInKbps = 2000;
        PeerInfo[] peerInfo = new PeerInfo[1];
        peerInfo[0] = new PeerInfo();
        peerInfo[0].rateStats = rateStats;
        stats.peerInfo = peerInfo;
        RadioStat[] radioStats = new RadioStat[2];
        for (int i = 0; i < 2; i++) {
            RadioStat radio = new RadioStat();
            radio.radio_id = i;
            radioStats[i] = radio;
        }
        stats.radioStats = radioStats;
        return stats;
    }

    /**
     * Verify that when there are no WifiUsability events the generated proto also contains no
     * such information.
     * @throws Exception
     */
    @Test
    public void testWifiUsabilityStatsZeroEvents() throws Exception {
        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.wifiUsabilityStatsList.length);
    }

    /**
     * Verify that we discard a WifiUsabilityStats with LABEL_GOOD if there is no corresponding
     * LABEL_BAD
     * @throws Exception
     */
    @Test
    public void testWifiUsabilityStatsIgnoreSingleLabelGood() throws Exception {
        addGoodWifiUsabilityStats(new WifiLinkLayerStats());
        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.wifiUsabilityStatsList.length);
    }

    /**
     * Verify that we discard a WifiUsabilityStats with LABEL_BAD if there is no corresponding
     * LABEL_GOOD
     * @throws Exception
     */
    @Test
    public void testWifiUsabilityStatsIgnoreSingleLabelBad() throws Exception {
        addBadWifiUsabilityStats(new WifiLinkLayerStats());
        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.wifiUsabilityStatsList.length);
    }

    /**
     * Verify that the buffer for WifiUsabilityStats does not exceed the max length.
     * Do this by trying to add more WifiUsabilityStats than the max length and then
     * verifying that the decoded proto's length does not exceed the max length.
     *
     * Also verify that the length for the list of WifiUsabilityStatsEntry is capped.
     * @throws Exception
     */
    @Test
    public void testWifiUsabilityStatsBufferSizeIsCapped() throws Exception {
        // simulate adding LABEL_GOOD WifiUsabilityStats 1 time over the max limit
        WifiLinkLayerStats stats = new WifiLinkLayerStats();
        for (int j = 0; j < WifiMetrics.MAX_WIFI_USABILITY_STATS_LIST_SIZE_PER_TYPE + 1; j++) {
            stats = addGoodWifiUsabilityStats(stats);
            stats = addBadWifiUsabilityStats(stats);
            stats.timeStampInMs += WifiMetrics.MIN_WIFI_GOOD_USABILITY_STATS_PERIOD_MS;
        }
        dumpProtoAndDeserialize();
        assertEquals(2 * WifiMetrics.MAX_WIFI_USABILITY_STATS_PER_TYPE_TO_UPLOAD,
                mDecodedProto.wifiUsabilityStatsList.length);
        for (int i = 0; i < WifiMetrics.MAX_WIFI_USABILITY_STATS_PER_TYPE_TO_UPLOAD; i++) {
            assertEquals(WifiMetrics.MAX_WIFI_USABILITY_STATS_ENTRIES_LIST_SIZE,
                    mDecodedProto.wifiUsabilityStatsList[2 * i].stats.length);
            assertEquals(2, mDecodedProto.wifiUsabilityStatsList[2 * i + 1].stats.length);
        }
    }

    /**
     * Verify that LABEL_GOOD stats are not generated more frequently than
     * |MIN_WIFI_GOOD_USABILITY_STATS_PERIOD_MS|
     * @throws Exception
     */
    @Test
    public void testWifiUsabilityStatsLabelGoodHasMinimumPeriod() throws Exception {
        // simulate adding LABEL_GOOD WifiUsabilityStats 1 time over the max limit
        WifiLinkLayerStats stats = new WifiLinkLayerStats();
        for (int j = 0; j < 2; j++) {
            stats = addGoodWifiUsabilityStats(stats);
            stats = addBadWifiUsabilityStats(stats);
        }
        dumpProtoAndDeserialize();
        assertEquals(2, mDecodedProto.wifiUsabilityStatsList.length);
    }

    /**
     * Verify that LABEL_BAD stats are not generated more frequently than |MIN_DATA_STALL_WAIT_MS|
     * @throws Exception
     */
    @Test
    public void testWifiUsabilityStatsLabelBadNotGeneratedGapLessThanMinimum() throws Exception {
        // simulate adding two LABEL_GOOD WifiUsabilityStats
        WifiInfo info = mock(WifiInfo.class);
        when(info.getRssi()).thenReturn(nextRandInt());
        when(info.getLinkSpeed()).thenReturn(nextRandInt());
        WifiLinkLayerStats stats1 = new WifiLinkLayerStats();
        WifiLinkLayerStats stats2 = new WifiLinkLayerStats();
        stats1 = addGoodWifiUsabilityStats(stats1);
        stats2.timeStampInMs = stats1.timeStampInMs
                + WifiMetrics.MIN_WIFI_GOOD_USABILITY_STATS_PERIOD_MS;
        addGoodWifiUsabilityStats(stats2);

        WifiLinkLayerStats stats3 = new WifiLinkLayerStats();
        WifiLinkLayerStats stats4 = new WifiLinkLayerStats();
        for (int i = 0; i < WifiMetrics.MAX_WIFI_USABILITY_STATS_ENTRIES_LIST_SIZE - 1; i++) {
            mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats3);
            stats3 = nextRandomStats(stats3);
        }
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats3);
        mWifiMetrics.addToWifiUsabilityStatsList(TEST_IFACE_NAME, WifiUsabilityStats.LABEL_BAD,
                WifiUsabilityStats.TYPE_DATA_STALL_BAD_TX, -1);
        for (int i = 0; i < WifiMetrics.MAX_WIFI_USABILITY_STATS_ENTRIES_LIST_SIZE - 1; i++) {
            mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats4);
            stats4 = nextRandomStats(stats4);
        }
        stats4.timeStampInMs = stats3.timeStampInMs - 1 + WifiMetrics.MIN_DATA_STALL_WAIT_MS;
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats4);
        mWifiMetrics.addToWifiUsabilityStatsList(TEST_IFACE_NAME, WifiUsabilityStats.LABEL_BAD,
                WifiUsabilityStats.TYPE_DATA_STALL_BAD_TX, -1);
        dumpProtoAndDeserialize();
        assertEquals(2, mDecodedProto.wifiUsabilityStatsList.length);
    }

    /**
     * Verify that LABEL_BAD stats are generated if timestamp gap is larger than
     * |MIN_DATA_STALL_WAIT_MS|
     * @throws Exception
     */
    @Test
    public void testWifiUsabilityStatsLabelBadGeneratedGapLargerThanMinimum() throws Exception {
        // simulate adding two LABEL_GOOD WifiUsabilityStats
        WifiInfo info = mock(WifiInfo.class);
        when(info.getRssi()).thenReturn(nextRandInt());
        when(info.getLinkSpeed()).thenReturn(nextRandInt());
        WifiLinkLayerStats stats1 = new WifiLinkLayerStats();
        WifiLinkLayerStats stats2 = new WifiLinkLayerStats();
        stats1 = addGoodWifiUsabilityStats(stats1);
        stats2.timeStampInMs = stats1.timeStampInMs
                + WifiMetrics.MIN_WIFI_GOOD_USABILITY_STATS_PERIOD_MS;
        addGoodWifiUsabilityStats(stats2);

        WifiLinkLayerStats stats3 = new WifiLinkLayerStats();
        WifiLinkLayerStats stats4 = new WifiLinkLayerStats();
        for (int i = 0; i < WifiMetrics.MAX_WIFI_USABILITY_STATS_ENTRIES_LIST_SIZE - 1; i++) {
            mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats3);
            stats3 = nextRandomStats(stats3);
        }
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats3);
        mWifiMetrics.addToWifiUsabilityStatsList(TEST_IFACE_NAME, WifiUsabilityStats.LABEL_BAD,
                WifiUsabilityStats.TYPE_DATA_STALL_BAD_TX, -1);
        for (int i = 0; i < WifiMetrics.MAX_WIFI_USABILITY_STATS_ENTRIES_LIST_SIZE - 1; i++) {
            mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats4);
            stats4 = nextRandomStats(stats4);
        }
        stats4.timeStampInMs = stats3.timeStampInMs + 1 + WifiMetrics.MIN_DATA_STALL_WAIT_MS;
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats4);
        mWifiMetrics.addToWifiUsabilityStatsList(TEST_IFACE_NAME, WifiUsabilityStats.LABEL_BAD,
                WifiUsabilityStats.TYPE_DATA_STALL_BAD_TX, -1);
        dumpProtoAndDeserialize();
        assertEquals(4, mDecodedProto.wifiUsabilityStatsList.length);
    }

    /**
     * Tests device mobility state metrics as states are changed.
     */
    @Test
    public void testDeviceMobilityStateMetrics_changeState() throws Exception {
        // timeMs is initialized to 0 by the setUp() method
        long timeMs = 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(timeMs);
        mWifiMetrics.enterDeviceMobilityState(DEVICE_MOBILITY_STATE_STATIONARY);

        timeMs += 2000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(timeMs);
        mWifiMetrics.enterDeviceMobilityState(DEVICE_MOBILITY_STATE_LOW_MVMT);

        dumpProtoAndDeserialize();

        DeviceMobilityStatePnoScanStats[] expected = {
                buildDeviceMobilityStatePnoScanStats(DEVICE_MOBILITY_STATE_UNKNOWN, 1, 1000, 0),
                buildDeviceMobilityStatePnoScanStats(DEVICE_MOBILITY_STATE_STATIONARY, 1, 2000, 0),
                buildDeviceMobilityStatePnoScanStats(DEVICE_MOBILITY_STATE_LOW_MVMT, 1, 0, 0)
        };

        assertDeviceMobilityStatePnoScanStatsEqual(
                expected, mDecodedProto.mobilityStatePnoStatsList);
    }

    /**
     * Tests device mobility state metrics as PNO scans are started and stopped.
     */
    @Test
    public void testDeviceMobilityStateMetrics_startStopPnoScans() throws Exception {
        long timeMs = 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(timeMs);
        mWifiMetrics.logPnoScanStart();

        timeMs += 2000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(timeMs);
        mWifiMetrics.logPnoScanStop();
        mWifiMetrics.enterDeviceMobilityState(DEVICE_MOBILITY_STATE_STATIONARY);
        mWifiMetrics.logPnoScanStart();

        timeMs += 4000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(timeMs);
        mWifiMetrics.logPnoScanStop();

        timeMs += 8000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(timeMs);
        mWifiMetrics.enterDeviceMobilityState(DEVICE_MOBILITY_STATE_HIGH_MVMT);

        dumpProtoAndDeserialize();

        DeviceMobilityStatePnoScanStats[] expected = {
                buildDeviceMobilityStatePnoScanStats(DEVICE_MOBILITY_STATE_UNKNOWN,
                        1, 1000 + 2000, 2000),
                buildDeviceMobilityStatePnoScanStats(DEVICE_MOBILITY_STATE_STATIONARY,
                        1, 4000 + 8000, 4000),
                buildDeviceMobilityStatePnoScanStats(DEVICE_MOBILITY_STATE_HIGH_MVMT, 1, 0, 0)
        };

        assertDeviceMobilityStatePnoScanStatsEqual(
                expected, mDecodedProto.mobilityStatePnoStatsList);
    }

    /**
     * Tests that the initial state is set up correctly.
     */
    @Test
    public void testDeviceMobilityStateMetrics_initialState() throws Exception {
        dumpProtoAndDeserialize();

        DeviceMobilityStatePnoScanStats[] expected = {
                buildDeviceMobilityStatePnoScanStats(DEVICE_MOBILITY_STATE_UNKNOWN, 1, 0, 0)
        };

        assertDeviceMobilityStatePnoScanStatsEqual(
                expected, mDecodedProto.mobilityStatePnoStatsList);
    }

    /**
     * Tests that logPnoScanStart() updates the total duration in addition to the PNO duration.
     */
    @Test
    public void testDeviceMobilityStateMetrics_startPnoScansUpdatesTotalDuration()
            throws Exception {
        long timeMs = 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(timeMs);
        mWifiMetrics.logPnoScanStart();

        dumpProtoAndDeserialize();

        DeviceMobilityStatePnoScanStats[] expected = {
                buildDeviceMobilityStatePnoScanStats(DEVICE_MOBILITY_STATE_UNKNOWN, 1, 1000, 0)
        };

        assertDeviceMobilityStatePnoScanStatsEqual(
                expected, mDecodedProto.mobilityStatePnoStatsList);
    }

    /**
     * Tests that logPnoScanStop() updates the total duration in addition to the PNO duration.
     */
    @Test
    public void testDeviceMobilityStateMetrics_stopPnoScansUpdatesTotalDuration()
            throws Exception {
        long timeMs = 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(timeMs);
        mWifiMetrics.logPnoScanStart();

        timeMs += 2000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(timeMs);
        mWifiMetrics.logPnoScanStop();

        dumpProtoAndDeserialize();

        DeviceMobilityStatePnoScanStats[] expected = {
                buildDeviceMobilityStatePnoScanStats(DEVICE_MOBILITY_STATE_UNKNOWN,
                        1, 1000 + 2000, 2000)
        };

        assertDeviceMobilityStatePnoScanStatsEqual(
                expected, mDecodedProto.mobilityStatePnoStatsList);
    }

    /**
     * Verify that clients should be notified of activity in case Wifi stats get updated.
     */
    @Test
    public void testClientNotification() throws RemoteException {
        // Register Client for verification.
        ArgumentCaptor<android.net.wifi.WifiUsabilityStatsEntry> usabilityStats =
                ArgumentCaptor.forClass(android.net.wifi.WifiUsabilityStatsEntry.class);
        mWifiMetrics.addOnWifiUsabilityListener(mOnWifiUsabilityStatsListener);
        WifiInfo info = mock(WifiInfo.class);
        when(info.getRssi()).thenReturn(nextRandInt());
        when(info.getLinkSpeed()).thenReturn(nextRandInt());


        WifiLinkLayerStats linkLayerStats = nextRandomStats(createNewWifiLinkLayerStats());

        // Add MLO links
        List<MloLink> links = new ArrayList<>();
        MloLink link;
        for (WifiLinkLayerStats.LinkSpecificStats stat : linkLayerStats.links) {
            link = new MloLink();
            link.setStaMacAddress(MacAddress.fromString(MLO_LINK_STA_MAC_ADDRESS));
            link.setApMacAddress(MacAddress.fromString(MLO_LINK_AP_MAC_ADDRESS));
            link.setRssi(stat.rssi_mgmt);
            link.setLinkId(stat.link_id);
            link.setBand(WifiScanner.WIFI_BAND_5_GHZ);
            link.setChannel(TEST_CHANNEL);
            link.setRxLinkSpeedMbps(nextRandInt());
            link.setTxLinkSpeedMbps(nextRandInt());
            link.setState(nextRandInt() % MloLink.MLO_LINK_STATE_ACTIVE);
            links.add(link);
        }
        when(info.getAffiliatedMloLinks()).thenReturn(links);

        // verify non-primary does not send wifi usability stats
        ConcreteClientModeManager concreteClientModeManager = mock(ConcreteClientModeManager.class);
        when(concreteClientModeManager.getInterfaceName()).thenReturn(TEST_IFACE_NAME);
        when(concreteClientModeManager.getRole()).thenReturn(
                ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED);
        mModeChangeCallbackArgumentCaptor.getValue()
                .onActiveModeManagerRoleChanged(concreteClientModeManager);
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, linkLayerStats);
        verify(mOnWifiUsabilityStatsListener, never()).onWifiUsabilityStats(anyInt(), anyBoolean(),
                any());

        // verify primary sends out wifi usability stats
        concreteClientModeManager = mock(ConcreteClientModeManager.class);
        when(concreteClientModeManager.getInterfaceName()).thenReturn(TEST_IFACE_NAME);
        when(concreteClientModeManager.getRole()).thenReturn(ActiveModeManager.ROLE_CLIENT_PRIMARY);
        mModeChangeCallbackArgumentCaptor.getValue()
                .onActiveModeManagerRoleChanged(concreteClientModeManager);
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, linkLayerStats);

        // Client should get the stats.
        verify(mOnWifiUsabilityStatsListener).onWifiUsabilityStats(anyInt(), anyBoolean(),
                usabilityStats.capture());
        assertEquals(usabilityStats.getValue().getTotalRadioOnTimeMillis(), linkLayerStats.on_time);
        assertEquals(usabilityStats.getValue().getTotalTxBad(), linkLayerStats.lostmpdu_be
                + linkLayerStats.lostmpdu_bk + linkLayerStats.lostmpdu_vi
                + linkLayerStats.lostmpdu_vo);
        assertEquals(usabilityStats.getValue().getTimeStampMillis(), linkLayerStats.timeStampInMs);
        assertEquals(usabilityStats.getValue().getTotalRoamScanTimeMillis(),
                linkLayerStats.on_time_roam_scan);

        SparseArray<MloLink> mloLinks = new SparseArray<>();
        for (MloLink mloLink: info.getAffiliatedMloLinks()) {
            mloLinks.put(mloLink.getLinkId(), mloLink);
        }

        // Verify MLO stats
        for (WifiLinkLayerStats.LinkSpecificStats linkStat : linkLayerStats.links) {
            assertEquals(usabilityStats.getValue().getLinkState(linkStat.link_id), linkStat.state);
            assertEquals(usabilityStats.getValue().getRadioId(linkStat.link_id), linkStat.radio_id);
            assertEquals(usabilityStats.getValue().getRssi(linkStat.link_id), linkStat.rssi_mgmt);
            assertEquals(usabilityStats.getValue().getTotalTxSuccess(linkStat.link_id),
                    linkStat.txmpdu_be + linkStat.txmpdu_bk + linkStat.txmpdu_vi
                            + linkStat.txmpdu_vo);
            assertEquals(usabilityStats.getValue().getTxLinkSpeedMbps(linkStat.link_id),
                    mloLinks.get(linkStat.link_id).getTxLinkSpeedMbps());
            assertEquals(usabilityStats.getValue().getRxLinkSpeedMbps(linkStat.link_id),
                    mloLinks.get(linkStat.link_id).getRxLinkSpeedMbps());

            assertEquals(usabilityStats.getValue().getTotalTxRetries(linkStat.link_id),
                    linkStat.retries_be + linkStat.retries_bk + linkStat.retries_vi
                            + linkStat.retries_vo);
            assertEquals(usabilityStats.getValue().getTotalCcaBusyFreqTimeMillis(linkStat.link_id),
                    linkLayerStats.channelStatsMap.get(linkStat.frequencyMhz).ccaBusyTimeMs);
            assertEquals(usabilityStats.getValue().getTotalRadioOnFreqTimeMillis(linkStat.link_id),
                    linkLayerStats.channelStatsMap.get(linkStat.frequencyMhz).radioOnTimeMs);
            assertEquals(usabilityStats.getValue().getTotalBeaconRx(linkStat.link_id),
                    linkStat.beacon_rx);
            assertEquals(usabilityStats.getValue().getTimeSliceDutyCycleInPercent(linkStat.link_id),
                    linkStat.timeSliceDutyCycleInPercent);

            // Verify contention time stats for each AC's
            android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats contentionTimeStatsBe =
                    usabilityStats.getValue().getContentionTimeStats(linkStat.link_id,
                            android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE);
            assertEquals(contentionTimeStatsBe.getContentionTimeMinMicros(),
                    linkStat.contentionTimeMinBeInUsec);
            assertEquals(contentionTimeStatsBe.getContentionTimeAvgMicros(),
                    linkStat.contentionTimeAvgBeInUsec);
            assertEquals(contentionTimeStatsBe.getContentionTimeMaxMicros(),
                    linkStat.contentionTimeMaxBeInUsec);
            assertEquals(contentionTimeStatsBe.getContentionNumSamples(),
                    linkStat.contentionNumSamplesBe);

            android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats contentionTimeStatsBk =
                    usabilityStats.getValue().getContentionTimeStats(linkStat.link_id,
                            android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK);
            assertEquals(contentionTimeStatsBk.getContentionTimeMinMicros(),
                    linkStat.contentionTimeMinBkInUsec);
            assertEquals(contentionTimeStatsBk.getContentionTimeAvgMicros(),
                    linkStat.contentionTimeAvgBkInUsec);
            assertEquals(contentionTimeStatsBk.getContentionTimeMaxMicros(),
                    linkStat.contentionTimeMaxBkInUsec);
            assertEquals(contentionTimeStatsBk.getContentionNumSamples(),
                    linkStat.contentionNumSamplesBk);

            android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats contentionTimeStatsVo =
                    usabilityStats.getValue().getContentionTimeStats(linkStat.link_id,
                            android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO);
            assertEquals(contentionTimeStatsVo.getContentionTimeMinMicros(),
                    linkStat.contentionTimeMinVoInUsec);
            assertEquals(contentionTimeStatsVo.getContentionTimeAvgMicros(),
                    linkStat.contentionTimeAvgVoInUsec);
            assertEquals(contentionTimeStatsVo.getContentionTimeMaxMicros(),
                    linkStat.contentionTimeMaxVoInUsec);
            assertEquals(contentionTimeStatsVo.getContentionNumSamples(),
                    linkStat.contentionNumSamplesVo);

            android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats contentionTimeStatsVi =
                    usabilityStats.getValue().getContentionTimeStats(linkStat.link_id,
                            android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI);
            assertEquals(contentionTimeStatsVi.getContentionTimeMinMicros(),
                    linkStat.contentionTimeMinViInUsec);
            assertEquals(contentionTimeStatsVi.getContentionTimeAvgMicros(),
                    linkStat.contentionTimeAvgViInUsec);
            assertEquals(contentionTimeStatsVi.getContentionTimeMaxMicros(),
                    linkStat.contentionTimeMaxViInUsec);
            assertEquals(contentionTimeStatsVi.getContentionNumSamples(),
                    linkStat.contentionNumSamplesVi);

            // Verify Rate stats.
            List<android.net.wifi.WifiUsabilityStatsEntry.RateStats> usabilityRateStats =
                    usabilityStats.getValue().getRateStats(linkStat.link_id);
            int i = 0;
            for (RateStat rateStat : linkStat.peerInfo[0].rateStats) {
                assertEquals(convertPreambleTypeEnumToUsabilityStatsType(rateStat.preamble),
                        usabilityRateStats.get(i).getPreamble());
                assertEquals(rateStat.bitRateInKbps,
                        usabilityRateStats.get(i).getBitRateInKbps());
                assertEquals(convertSpatialStreamEnumToUsabilityStatsType(rateStat.nss),
                        usabilityRateStats.get(i).getNumberOfSpatialStreams());
                assertEquals(convertBandwidthEnumToUsabilityStatsType(rateStat.bw),
                        usabilityRateStats.get(i).getBandwidthInMhz());
                assertEquals(rateStat.rateMcsIdx,
                        usabilityRateStats.get(i).getRateMcsIdx());
                assertEquals(rateStat.bitRateInKbps,
                        usabilityRateStats.get(i).getBitRateInKbps());
                assertEquals(rateStat.txMpdu,
                        usabilityRateStats.get(i).getTxMpdu());
                assertEquals(rateStat.rxMpdu,
                        usabilityRateStats.get(i).getRxMpdu());
                assertEquals(rateStat.mpduLost,
                        usabilityRateStats.get(i).getMpduLost());
                assertEquals(rateStat.retries,
                        usabilityRateStats.get(i).getRetries());
                i++;
            }
        }
    }

    /**
     * Verify that remove client should be handled
     */
    @Test
    public void testRemoveClient() throws RemoteException {
        // Register Client for verification.
        mWifiMetrics.addOnWifiUsabilityListener(mOnWifiUsabilityStatsListener);
        mWifiMetrics.removeOnWifiUsabilityListener(mOnWifiUsabilityStatsListener);
        verify(mAppBinder).unlinkToDeath(any(), anyInt());

        WifiInfo info = mock(WifiInfo.class);
        when(info.getRssi()).thenReturn(nextRandInt());
        when(info.getLinkSpeed()).thenReturn(nextRandInt());
        WifiLinkLayerStats linkLayerStats = nextRandomStats(new WifiLinkLayerStats());
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, linkLayerStats);

        verify(mOnWifiUsabilityStatsListener, never()).onWifiUsabilityStats(anyInt(),
                anyBoolean(), any());
    }

    /**
     * Verify that WifiMetrics adds for death notification on adding client.
     */
    @Test
    public void testAddsForBinderDeathOnAddClient() throws Exception {
        mWifiMetrics.addOnWifiUsabilityListener(mOnWifiUsabilityStatsListener);
        verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
    }

    /**
     * Verify that client fails to get message when listener add failed.
     */
    @Test
    public void testAddsListenerFailureOnLinkToDeath() throws Exception {
        doThrow(new RemoteException())
                .when(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        mWifiMetrics.addOnWifiUsabilityListener(mOnWifiUsabilityStatsListener);
        verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        WifiInfo info = mock(WifiInfo.class);
        when(info.getRssi()).thenReturn(nextRandInt());
        when(info.getLinkSpeed()).thenReturn(nextRandInt());
        WifiLinkLayerStats linkLayerStats = nextRandomStats(new WifiLinkLayerStats());
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, linkLayerStats);

        // Client should not get any message listener add failed.
        verify(mOnWifiUsabilityStatsListener, never()).onWifiUsabilityStats(anyInt(),
                anyBoolean(), any());
    }

    /**
     * Verify that the label and the triggerType of Wifi usability stats are saved correctly
     * during firmware alert is triggered.
     * @throws Exception
     */
    @Test
    public void verifyFirmwareAlertUpdatesWifiUsabilityMetrics() throws Exception {
        WifiInfo info = mock(WifiInfo.class);
        when(info.getRssi()).thenReturn(nextRandInt());
        when(info.getLinkSpeed()).thenReturn(nextRandInt());
        long eventTimeMs = nextRandInt();
        when(mClock.getElapsedSinceBootMillis()).thenReturn(eventTimeMs);
        WifiLinkLayerStats stats1 = nextRandomStats(new WifiLinkLayerStats());
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats1);

        // Add 1 LABEL_GOOD
        WifiLinkLayerStats statsGood = addGoodWifiUsabilityStats(nextRandomStats(stats1));
        // Firmware alert occurs
        mWifiMetrics.logFirmwareAlert(TEST_IFACE_NAME, 2);

        dumpProtoAndDeserialize();
        assertEquals(2, mDecodedProto.wifiUsabilityStatsList.length);

        WifiUsabilityStats[] statsList = mDecodedProto.wifiUsabilityStatsList;
        assertEquals(WifiUsabilityStats.LABEL_GOOD, statsList[0].label);
        assertEquals(WifiUsabilityStats.LABEL_BAD, statsList[1].label);
        assertEquals(WifiIsUnusableEvent.TYPE_FIRMWARE_ALERT, statsList[1].triggerType);
        assertEquals(eventTimeMs, statsList[1].timeStampMs);
        assertEquals(2, statsList[1].firmwareAlertCode);
    }

    /**
     * Verify that the label and the triggerType of Wifi usability stats are saved correctly
     * during Wifi data stall is triggered.
     * @throws Exception
     */
    @Test
    public void verifyWifiDataStallUpdatesWifiUsabilityMetrics() throws Exception {
        WifiInfo info = mock(WifiInfo.class);
        when(info.getRssi()).thenReturn(nextRandInt());
        when(info.getLinkSpeed()).thenReturn(nextRandInt());
        long eventTimeMs = nextRandInt();
        when(mClock.getElapsedSinceBootMillis()).thenReturn(eventTimeMs);
        WifiLinkLayerStats stats1 = nextRandomStats(new WifiLinkLayerStats());
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats1);

        // Add 1 LABEL_GOOD
        WifiLinkLayerStats statsGood = addGoodWifiUsabilityStats(nextRandomStats(stats1));
        // Wifi data stall occurs
        mWifiMetrics.addToWifiUsabilityStatsList(TEST_IFACE_NAME, WifiUsabilityStats.LABEL_BAD,
                WifiIsUnusableEvent.TYPE_DATA_STALL_BAD_TX, -1);

        dumpProtoAndDeserialize();
        assertEquals(2, mDecodedProto.wifiUsabilityStatsList.length);
        WifiUsabilityStats[] statsList = mDecodedProto.wifiUsabilityStatsList;
        assertEquals(WifiUsabilityStats.LABEL_BAD, statsList[1].label);
        assertEquals(WifiIsUnusableEvent.TYPE_DATA_STALL_BAD_TX, statsList[1].triggerType);
        assertEquals(-1, statsList[1].firmwareAlertCode);
        assertEquals(eventTimeMs, statsList[1].timeStampMs);
    }

    /**
     * Test the generation of 'WifiConfigStoreIODuration' read histograms.
     */
    @Test
    public void testWifiConfigStoreReadDurationsHistogramGeneration() throws Exception {
        mWifiMetrics.noteWifiConfigStoreReadDuration(10);
        mWifiMetrics.noteWifiConfigStoreReadDuration(20);
        mWifiMetrics.noteWifiConfigStoreReadDuration(100);
        mWifiMetrics.noteWifiConfigStoreReadDuration(90);
        mWifiMetrics.noteWifiConfigStoreReadDuration(130);
        mWifiMetrics.noteWifiConfigStoreReadDuration(250);
        mWifiMetrics.noteWifiConfigStoreReadDuration(600);

        dumpProtoAndDeserialize();

        assertEquals(5, mDecodedProto.wifiConfigStoreIo.readDurations.length);
        assertEquals(0, mDecodedProto.wifiConfigStoreIo.writeDurations.length);

        assertEquals(Integer.MIN_VALUE,
                mDecodedProto.wifiConfigStoreIo.readDurations[0].rangeStartMs);
        assertEquals(50, mDecodedProto.wifiConfigStoreIo.readDurations[0].rangeEndMs);
        assertEquals(2, mDecodedProto.wifiConfigStoreIo.readDurations[0].count);

        assertEquals(50, mDecodedProto.wifiConfigStoreIo.readDurations[1].rangeStartMs);
        assertEquals(100, mDecodedProto.wifiConfigStoreIo.readDurations[1].rangeEndMs);
        assertEquals(1, mDecodedProto.wifiConfigStoreIo.readDurations[1].count);

        assertEquals(100, mDecodedProto.wifiConfigStoreIo.readDurations[2].rangeStartMs);
        assertEquals(150, mDecodedProto.wifiConfigStoreIo.readDurations[2].rangeEndMs);
        assertEquals(2, mDecodedProto.wifiConfigStoreIo.readDurations[2].count);

        assertEquals(200, mDecodedProto.wifiConfigStoreIo.readDurations[3].rangeStartMs);
        assertEquals(300, mDecodedProto.wifiConfigStoreIo.readDurations[3].rangeEndMs);
        assertEquals(1, mDecodedProto.wifiConfigStoreIo.readDurations[3].count);

        assertEquals(300, mDecodedProto.wifiConfigStoreIo.readDurations[4].rangeStartMs);
        assertEquals(Integer.MAX_VALUE,
                mDecodedProto.wifiConfigStoreIo.readDurations[4].rangeEndMs);
        assertEquals(1, mDecodedProto.wifiConfigStoreIo.readDurations[4].count);
    }

    /**
     * Test the generation of 'WifiConfigStoreIODuration' write histograms.
     */
    @Test
    public void testWifiConfigStoreWriteDurationsHistogramGeneration() throws Exception {
        mWifiMetrics.noteWifiConfigStoreWriteDuration(10);
        mWifiMetrics.noteWifiConfigStoreWriteDuration(40);
        mWifiMetrics.noteWifiConfigStoreWriteDuration(60);
        mWifiMetrics.noteWifiConfigStoreWriteDuration(90);
        mWifiMetrics.noteWifiConfigStoreWriteDuration(534);
        mWifiMetrics.noteWifiConfigStoreWriteDuration(345);

        dumpProtoAndDeserialize();

        assertEquals(0, mDecodedProto.wifiConfigStoreIo.readDurations.length);
        assertEquals(3, mDecodedProto.wifiConfigStoreIo.writeDurations.length);

        assertEquals(Integer.MIN_VALUE,
                mDecodedProto.wifiConfigStoreIo.writeDurations[0].rangeStartMs);
        assertEquals(50, mDecodedProto.wifiConfigStoreIo.writeDurations[0].rangeEndMs);
        assertEquals(2, mDecodedProto.wifiConfigStoreIo.writeDurations[0].count);

        assertEquals(50, mDecodedProto.wifiConfigStoreIo.writeDurations[1].rangeStartMs);
        assertEquals(100, mDecodedProto.wifiConfigStoreIo.writeDurations[1].rangeEndMs);
        assertEquals(2, mDecodedProto.wifiConfigStoreIo.writeDurations[1].count);

        assertEquals(300, mDecodedProto.wifiConfigStoreIo.writeDurations[2].rangeStartMs);
        assertEquals(Integer.MAX_VALUE,
                mDecodedProto.wifiConfigStoreIo.writeDurations[2].rangeEndMs);
        assertEquals(2, mDecodedProto.wifiConfigStoreIo.writeDurations[2].count);
    }

    /**
     * Test link probe metrics.
     */
    @Test
    public void testLogLinkProbeMetrics() throws Exception {
        mWifiMetrics.logLinkProbeSuccess(TEST_IFACE_NAME, 10000, -75, 50, 5);
        mWifiMetrics.logLinkProbeFailure(TEST_IFACE_NAME, 30000, -80, 10,
                WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_NO_ACK);
        mWifiMetrics.logLinkProbeSuccess(TEST_IFACE_NAME, 3000, -71, 160, 12);
        mWifiMetrics.logLinkProbeFailure(TEST_IFACE_NAME, 40000, -80, 6,
                WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_NO_ACK);
        mWifiMetrics.logLinkProbeSuccess(TEST_IFACE_NAME, 5000, -73, 160, 10);
        mWifiMetrics.logLinkProbeFailure(TEST_IFACE_NAME, 2000, -78, 6,
                WifiNl80211Manager.SEND_MGMT_FRAME_ERROR_TIMEOUT);

        dumpProtoAndDeserialize();

        StaEvent[] expected = {
                buildLinkProbeSuccessStaEvent(5),
                buildLinkProbeFailureStaEvent(LinkProbeStats.LINK_PROBE_FAILURE_REASON_NO_ACK),
                buildLinkProbeSuccessStaEvent(12),
                buildLinkProbeFailureStaEvent(LinkProbeStats.LINK_PROBE_FAILURE_REASON_NO_ACK),
                buildLinkProbeSuccessStaEvent(10),
                buildLinkProbeFailureStaEvent(LinkProbeStats.LINK_PROBE_FAILURE_REASON_TIMEOUT)
        };
        assertLinkProbeStaEventsEqual(expected, mDecodedProto.staEventList);

        LinkProbeStats linkProbeStats = mDecodedProto.linkProbeStats;

        Int32Count[] expectedSuccessRssiHistogram = {
                buildInt32Count(-75, 1),
                buildInt32Count(-73, 1),
                buildInt32Count(-71, 1),
        };
        assertKeyCountsEqual(expectedSuccessRssiHistogram,
                linkProbeStats.successRssiCounts);

        Int32Count[] expectedFailureRssiHistogram = {
                buildInt32Count(-80, 2),
                buildInt32Count(-78, 1),
        };
        assertKeyCountsEqual(expectedFailureRssiHistogram,
                linkProbeStats.failureRssiCounts);

        Int32Count[] expectedSuccessLinkSpeedHistogram = {
                buildInt32Count(50, 1),
                buildInt32Count(160, 2)
        };
        assertKeyCountsEqual(expectedSuccessLinkSpeedHistogram,
                linkProbeStats.successLinkSpeedCounts);

        Int32Count[] expectedFailureLinkSpeedHistogram = {
                buildInt32Count(6, 2),
                buildInt32Count(10, 1)
        };
        assertKeyCountsEqual(expectedFailureLinkSpeedHistogram,
                linkProbeStats.failureLinkSpeedCounts);

        HistogramBucketInt32[] expectedSuccessTimeSinceLastTxSuccessSecondsHistogram = {
                buildHistogramBucketInt32(Integer.MIN_VALUE, 5, 1),
                buildHistogramBucketInt32(5, 15, 2)
        };
        assertHistogramBucketsEqual(expectedSuccessTimeSinceLastTxSuccessSecondsHistogram,
                linkProbeStats.successSecondsSinceLastTxSuccessHistogram);

        HistogramBucketInt32[] expectedFailureTimeSinceLastTxSuccessSecondsHistogram = {
                buildHistogramBucketInt32(Integer.MIN_VALUE, 5, 1),
                buildHistogramBucketInt32(15, 45, 2)
        };
        assertHistogramBucketsEqual(expectedFailureTimeSinceLastTxSuccessSecondsHistogram,
                linkProbeStats.failureSecondsSinceLastTxSuccessHistogram);

        HistogramBucketInt32[] expectedSuccessElapsedTimeMsHistogram = {
                buildHistogramBucketInt32(5, 10, 1),
                buildHistogramBucketInt32(10, 15, 2),
        };
        assertHistogramBucketsEqual(expectedSuccessElapsedTimeMsHistogram,
                linkProbeStats.successElapsedTimeMsHistogram);

        LinkProbeFailureReasonCount[] expectedFailureReasonCount = {
                buildLinkProbeFailureReasonCount(
                        LinkProbeStats.LINK_PROBE_FAILURE_REASON_NO_ACK, 2),
                buildLinkProbeFailureReasonCount(
                        LinkProbeStats.LINK_PROBE_FAILURE_REASON_TIMEOUT, 1),
        };
        assertLinkProbeFailureReasonCountsEqual(expectedFailureReasonCount,
                linkProbeStats.failureReasonCounts);
    }

    /**
     * Tests counting the number of link probes triggered per day for each experiment.
     */
    @Test
    public void testIncrementLinkProbeExperimentProbeCount() throws Exception {
        String experimentId1 = "screenOnDelay=6000,noTxDelay=3000,delayBetweenProbes=9000,"
                + "rssiThreshold=-70,linkSpeedThreshold=15,";
        mWifiMetrics.incrementLinkProbeExperimentProbeCount(experimentId1);

        String experimentId2 = "screenOnDelay=9000,noTxDelay=12000,delayBetweenProbes=15000,"
                + "rssiThreshold=-72,linkSpeedThreshold=20,";
        mWifiMetrics.incrementLinkProbeExperimentProbeCount(experimentId2);
        mWifiMetrics.incrementLinkProbeExperimentProbeCount(experimentId2);

        dumpProtoAndDeserialize();

        ExperimentProbeCounts[] actual = mDecodedProto.linkProbeStats.experimentProbeCounts;

        ExperimentProbeCounts[] expected = {
                buildExperimentProbeCounts(experimentId1, 1),
                buildExperimentProbeCounts(experimentId2, 2)
        };

        assertExperimentProbeCountsEqual(expected, actual);
    }

    /**
     * Tests logNetworkSelectionDecision()
     */
    @Test
    public void testLogNetworkSelectionDecision() throws Exception {
        mWifiMetrics.logNetworkSelectionDecision(1, 2, true, 6);
        mWifiMetrics.logNetworkSelectionDecision(1, 2, false, 1);
        mWifiMetrics.logNetworkSelectionDecision(1, 2, true, 6);
        mWifiMetrics.logNetworkSelectionDecision(1, 2, true, 2);
        mWifiMetrics.logNetworkSelectionDecision(3, 2, false, 15);
        mWifiMetrics.logNetworkSelectionDecision(1, 2, false, 6);
        mWifiMetrics.logNetworkSelectionDecision(1, 4, true, 2);

        dumpProtoAndDeserialize();

        assertEquals(3, mDecodedProto.networkSelectionExperimentDecisionsList.length);

        NetworkSelectionExperimentDecisions exp12 =
                findUniqueNetworkSelectionExperimentDecisions(1, 2);
        Int32Count[] exp12SameExpected = {
                buildInt32Count(2, 1),
                buildInt32Count(6, 2)
        };
        assertKeyCountsEqual(exp12SameExpected, exp12.sameSelectionNumChoicesCounter);
        Int32Count[] exp12DiffExpected = {
                buildInt32Count(1, 1),
                buildInt32Count(6, 1)
        };
        assertKeyCountsEqual(exp12DiffExpected, exp12.differentSelectionNumChoicesCounter);

        NetworkSelectionExperimentDecisions exp32 =
                findUniqueNetworkSelectionExperimentDecisions(3, 2);
        Int32Count[] exp32SameExpected = {};
        assertKeyCountsEqual(exp32SameExpected, exp32.sameSelectionNumChoicesCounter);
        Int32Count[] exp32DiffExpected = {
                buildInt32Count(
                        WifiMetrics.NetworkSelectionExperimentResults.MAX_CHOICES, 1)
        };
        assertKeyCountsEqual(exp32DiffExpected, exp32.differentSelectionNumChoicesCounter);

        NetworkSelectionExperimentDecisions exp14 =
                findUniqueNetworkSelectionExperimentDecisions(1, 4);
        Int32Count[] exp14SameExpected = {
                buildInt32Count(2, 1)
        };
        assertKeyCountsEqual(exp14SameExpected, exp14.sameSelectionNumChoicesCounter);
        Int32Count[] exp14DiffExpected = {};
        assertKeyCountsEqual(exp14DiffExpected, exp14.differentSelectionNumChoicesCounter);
    }

    /**
     * Test the generation of 'WifiNetworkRequestApiLog' message.
     */
    @Test
    public void testWifiNetworkRequestApiLog() throws Exception {
        mWifiMetrics.incrementNetworkRequestApiNumRequest();
        mWifiMetrics.incrementNetworkRequestApiNumRequest();
        mWifiMetrics.incrementNetworkRequestApiNumRequest();

        mWifiMetrics.incrementNetworkRequestApiMatchSizeHistogram(7);
        mWifiMetrics.incrementNetworkRequestApiMatchSizeHistogram(0);
        mWifiMetrics.incrementNetworkRequestApiMatchSizeHistogram(1);

        mWifiMetrics.incrementNetworkRequestApiNumConnectSuccessOnPrimaryIface();
        mWifiMetrics.incrementNetworkRequestApiNumConnectSuccessOnPrimaryIface();

        mWifiMetrics.incrementNetworkRequestApiNumConnectSuccessOnSecondaryIface();

        mWifiMetrics.incrementNetworkRequestApiNumConnectOnPrimaryIface();
        mWifiMetrics.incrementNetworkRequestApiNumConnectOnPrimaryIface();

        mWifiMetrics.incrementNetworkRequestApiNumConnectOnSecondaryIface();
        mWifiMetrics.incrementNetworkRequestApiNumConnectOnSecondaryIface();
        mWifiMetrics.incrementNetworkRequestApiNumConnectOnSecondaryIface();

        mWifiMetrics.incrementNetworkRequestApiNumUserApprovalBypass();
        mWifiMetrics.incrementNetworkRequestApiNumUserApprovalBypass();

        mWifiMetrics.incrementNetworkRequestApiNumUserReject();

        mWifiMetrics.incrementNetworkRequestApiNumApps();

        mWifiMetrics.incrementNetworkRequestApiConnectionDurationSecOnPrimaryIfaceHistogram(40);
        mWifiMetrics.incrementNetworkRequestApiConnectionDurationSecOnPrimaryIfaceHistogram(670);
        mWifiMetrics.incrementNetworkRequestApiConnectionDurationSecOnPrimaryIfaceHistogram(1801);

        mWifiMetrics.incrementNetworkRequestApiConnectionDurationSecOnSecondaryIfaceHistogram(100);
        mWifiMetrics.incrementNetworkRequestApiConnectionDurationSecOnSecondaryIfaceHistogram(350);
        mWifiMetrics.incrementNetworkRequestApiConnectionDurationSecOnSecondaryIfaceHistogram(750);

        mWifiMetrics.incrementNetworkRequestApiConcurrentConnectionDurationSecHistogram(10);
        mWifiMetrics.incrementNetworkRequestApiConcurrentConnectionDurationSecHistogram(589);
        mWifiMetrics.incrementNetworkRequestApiConcurrentConnectionDurationSecHistogram(2900);
        mWifiMetrics.incrementNetworkRequestApiConcurrentConnectionDurationSecHistogram(145);

        dumpProtoAndDeserialize();

        assertEquals(3, mDecodedProto.wifiNetworkRequestApiLog.numRequest);
        assertEquals(2, mDecodedProto.wifiNetworkRequestApiLog.numConnectSuccessOnPrimaryIface);
        assertEquals(1, mDecodedProto.wifiNetworkRequestApiLog.numConnectSuccessOnSecondaryIface);
        assertEquals(2, mDecodedProto.wifiNetworkRequestApiLog.numConnectOnPrimaryIface);
        assertEquals(3, mDecodedProto.wifiNetworkRequestApiLog.numConnectOnSecondaryIface);
        assertEquals(2, mDecodedProto.wifiNetworkRequestApiLog.numUserApprovalBypass);
        assertEquals(1, mDecodedProto.wifiNetworkRequestApiLog.numUserReject);
        assertEquals(1, mDecodedProto.wifiNetworkRequestApiLog.numApps);

        HistogramBucketInt32[] expectedNetworkMatchSizeHistogram = {
                buildHistogramBucketInt32(0, 1, 1),
                buildHistogramBucketInt32(1, 5, 1),
                buildHistogramBucketInt32(5, 10, 1)
        };
        assertHistogramBucketsEqual(expectedNetworkMatchSizeHistogram,
                mDecodedProto.wifiNetworkRequestApiLog.networkMatchSizeHistogram);

        HistogramBucketInt32[] expectedConnectionDurationOnPrimarySec = {
                buildHistogramBucketInt32(0, toIntExact(Duration.ofMinutes(3).getSeconds()), 1),
                buildHistogramBucketInt32(toIntExact(Duration.ofMinutes(10).getSeconds()),
                        toIntExact(Duration.ofMinutes(30).getSeconds()), 1),
                buildHistogramBucketInt32(toIntExact(Duration.ofMinutes(30).getSeconds()),
                        toIntExact(Duration.ofHours(1).getSeconds()), 1)
        };
        assertHistogramBucketsEqual(expectedConnectionDurationOnPrimarySec,
                mDecodedProto.wifiNetworkRequestApiLog
                        .connectionDurationSecOnPrimaryIfaceHistogram);

        HistogramBucketInt32[] expectedConnectionDurationOnSecondarySec = {
                buildHistogramBucketInt32(0, toIntExact(Duration.ofMinutes(3).getSeconds()), 1),
                buildHistogramBucketInt32(toIntExact(Duration.ofMinutes(3).getSeconds()),
                        toIntExact(Duration.ofMinutes(10).getSeconds()), 1),
                buildHistogramBucketInt32(toIntExact(Duration.ofMinutes(10).getSeconds()),
                        toIntExact(Duration.ofMinutes(30).getSeconds()), 1),
        };
        assertHistogramBucketsEqual(expectedConnectionDurationOnSecondarySec,
                mDecodedProto.wifiNetworkRequestApiLog
                        .connectionDurationSecOnSecondaryIfaceHistogram);

        HistogramBucketInt32[] expectedConcurrentConnectionDuration = {
                buildHistogramBucketInt32(0, toIntExact(Duration.ofMinutes(3).getSeconds()), 2),
                buildHistogramBucketInt32(toIntExact(Duration.ofMinutes(3).getSeconds()),
                        toIntExact(Duration.ofMinutes(10).getSeconds()), 1),
                buildHistogramBucketInt32(toIntExact(Duration.ofMinutes(30).getSeconds()),
                        toIntExact(Duration.ofHours(1).getSeconds()), 1)
        };
        assertHistogramBucketsEqual(expectedConcurrentConnectionDuration,
                mDecodedProto.wifiNetworkRequestApiLog.concurrentConnectionDurationSecHistogram);
    }

    /**
     * Test the generation of 'WifiNetworkSuggestionApiLog' message.
     */
    @Test
    public void testWifiNetworkSuggestionApiLog() throws Exception {
        mWifiMetrics.incrementNetworkSuggestionApiNumModification();
        mWifiMetrics.incrementNetworkSuggestionApiNumModification();
        mWifiMetrics.incrementNetworkSuggestionApiNumModification();
        mWifiMetrics.incrementNetworkSuggestionApiNumModification();

        mWifiMetrics.incrementNetworkSuggestionApiNumConnectSuccess();
        mWifiMetrics.incrementNetworkSuggestionApiNumConnectSuccess();

        mWifiMetrics.incrementNetworkSuggestionApiNumConnectFailure();

        mWifiMetrics.incrementNetworkSuggestionApiUsageNumOfAppInType(
                WifiNetworkSuggestionsManager.APP_TYPE_NON_PRIVILEGED);
        mWifiMetrics.incrementNetworkSuggestionApiUsageNumOfAppInType(
                WifiNetworkSuggestionsManager.APP_TYPE_NON_PRIVILEGED);
        mWifiMetrics.incrementNetworkSuggestionApiUsageNumOfAppInType(
                WifiNetworkSuggestionsManager.APP_TYPE_NON_PRIVILEGED);
        mWifiMetrics.incrementNetworkSuggestionApiUsageNumOfAppInType(
                WifiNetworkSuggestionsManager.APP_TYPE_CARRIER_PRIVILEGED);
        mWifiMetrics.incrementNetworkSuggestionApiUsageNumOfAppInType(
                WifiNetworkSuggestionsManager.APP_TYPE_CARRIER_PRIVILEGED);
        mWifiMetrics.incrementNetworkSuggestionApiUsageNumOfAppInType(
                WifiNetworkSuggestionsManager.APP_TYPE_NETWORK_PROVISIONING);


        mWifiMetrics.noteNetworkSuggestionApiListSizeHistogram(List.of(
                5,
                100,
                50,
                120));
        // Second update should overwrite the prevous write.
        mWifiMetrics.noteNetworkSuggestionApiListSizeHistogram(List.of(
                7,
                110,
                40,
                60));

        mWifiMetrics.incrementNetworkSuggestionUserRevokePermission();
        mWifiMetrics.incrementNetworkSuggestionUserRevokePermission();

        mWifiMetrics.addSuggestionExistsForSavedNetwork("savedNetwork");
        mWifiMetrics.incrementNetworkSuggestionMoreThanOneSuggestionForSingleScanResult();
        mWifiMetrics.addNetworkSuggestionPriorityGroup(0);
        mWifiMetrics.addNetworkSuggestionPriorityGroup(1);
        mWifiMetrics.addNetworkSuggestionPriorityGroup(1);

        dumpProtoAndDeserialize();

        assertEquals(4, mDecodedProto.wifiNetworkSuggestionApiLog.numModification);
        assertEquals(2, mDecodedProto.wifiNetworkSuggestionApiLog.numConnectSuccess);
        assertEquals(1, mDecodedProto.wifiNetworkSuggestionApiLog.numConnectFailure);

        HistogramBucketInt32[] expectedNetworkListSizeHistogram = {
                buildHistogramBucketInt32(5, 20, 1),
                buildHistogramBucketInt32(20, 50, 1),
                buildHistogramBucketInt32(50, 100, 1),
                buildHistogramBucketInt32(100, 500, 1),
        };
        assertHistogramBucketsEqual(expectedNetworkListSizeHistogram,
                mDecodedProto.wifiNetworkSuggestionApiLog.networkListSizeHistogram);

        assertEquals(3, mDecodedProto.wifiNetworkSuggestionApiLog.appCountPerType.length);
        assertEquals(WifiMetricsProto.WifiNetworkSuggestionApiLog.TYPE_CARRIER_PRIVILEGED,
                mDecodedProto.wifiNetworkSuggestionApiLog.appCountPerType[0].appType);
        assertEquals(2, mDecodedProto.wifiNetworkSuggestionApiLog.appCountPerType[0].count);
        assertEquals(WifiMetricsProto.WifiNetworkSuggestionApiLog.TYPE_NETWORK_PROVISIONING,
                mDecodedProto.wifiNetworkSuggestionApiLog.appCountPerType[1].appType);
        assertEquals(1, mDecodedProto.wifiNetworkSuggestionApiLog.appCountPerType[1].count);
        assertEquals(WifiMetricsProto.WifiNetworkSuggestionApiLog.TYPE_NON_PRIVILEGED,
                mDecodedProto.wifiNetworkSuggestionApiLog.appCountPerType[2].appType);
        assertEquals(3, mDecodedProto.wifiNetworkSuggestionApiLog.appCountPerType[2].count);
        assertEquals(1, mDecodedProto.wifiNetworkSuggestionApiLog.numMultipleSuggestions);
        assertEquals(1, mDecodedProto.wifiNetworkSuggestionApiLog
                .numSavedNetworksWithConfiguredSuggestion);
        assertEquals(1, mDecodedProto.wifiNetworkSuggestionApiLog.numPriorityGroups);
    }

    /**
     * Test the generation of 'UserReactionToApprovalUiEvent' message.
     */
    @Test
    public void testUserReactionToApprovalUiEvent() throws Exception {
        mWifiMetrics.addUserApprovalSuggestionAppUiReaction(1,  true);
        mWifiMetrics.addUserApprovalSuggestionAppUiReaction(2,  false);

        mWifiMetrics.addUserApprovalCarrierUiReaction(
                WifiCarrierInfoManager.ACTION_USER_ALLOWED_CARRIER, true);
        mWifiMetrics.addUserApprovalCarrierUiReaction(
                WifiCarrierInfoManager.ACTION_USER_DISMISS, false);
        mWifiMetrics.addUserApprovalCarrierUiReaction(
                WifiCarrierInfoManager.ACTION_USER_DISALLOWED_CARRIER, false);

        dumpProtoAndDeserialize();

        assertEquals(2,
                mDecodedProto.userReactionToApprovalUiEvent.userApprovalAppUiReaction.length);
        assertEquals(WifiMetricsProto.UserReactionToApprovalUiEvent.ACTION_ALLOWED,
                mDecodedProto.userReactionToApprovalUiEvent.userApprovalAppUiReaction[0]
                        .userAction);
        assertEquals(true,
                mDecodedProto.userReactionToApprovalUiEvent.userApprovalAppUiReaction[0]
                        .isDialog);
        assertEquals(WifiMetricsProto.UserReactionToApprovalUiEvent.ACTION_DISALLOWED,
                mDecodedProto.userReactionToApprovalUiEvent.userApprovalAppUiReaction[1]
                        .userAction);
        assertEquals(false,
                mDecodedProto.userReactionToApprovalUiEvent.userApprovalAppUiReaction[1]
                        .isDialog);

        assertEquals(3,
                mDecodedProto.userReactionToApprovalUiEvent.userApprovalCarrierUiReaction.length);
        assertEquals(WifiMetricsProto.UserReactionToApprovalUiEvent.ACTION_ALLOWED,
                mDecodedProto.userReactionToApprovalUiEvent.userApprovalCarrierUiReaction[0]
                        .userAction);
        assertEquals(true,
                mDecodedProto.userReactionToApprovalUiEvent.userApprovalCarrierUiReaction[0]
                        .isDialog);
        assertEquals(WifiMetricsProto.UserReactionToApprovalUiEvent.ACTION_DISMISS,
                mDecodedProto.userReactionToApprovalUiEvent.userApprovalCarrierUiReaction[1]
                        .userAction);
        assertEquals(false,
                mDecodedProto.userReactionToApprovalUiEvent.userApprovalCarrierUiReaction[1]
                        .isDialog);
        assertEquals(WifiMetricsProto.UserReactionToApprovalUiEvent.ACTION_DISALLOWED,
                mDecodedProto.userReactionToApprovalUiEvent.userApprovalCarrierUiReaction[2]
                        .userAction);
        assertEquals(false,
                mDecodedProto.userReactionToApprovalUiEvent.userApprovalCarrierUiReaction[2]
                        .isDialog);
    }

    private NetworkSelectionExperimentDecisions findUniqueNetworkSelectionExperimentDecisions(
            int experiment1Id, int experiment2Id) {
        NetworkSelectionExperimentDecisions result = null;
        for (NetworkSelectionExperimentDecisions d
                : mDecodedProto.networkSelectionExperimentDecisionsList) {
            if (d.experiment1Id == experiment1Id && d.experiment2Id == experiment2Id) {
                assertNull("duplicate found!", result);
                result = d;
            }
        }
        assertNotNull("not found!", result);
        return result;
    }

    /**
     * Verify that the label and the triggerType of Wifi usability stats are saved correctly
     * during IP reachability lost message is received.
     * @throws Exception
     */
    @Test
    public void verifyIpReachabilityLostUpdatesWifiUsabilityMetrics() throws Exception {
        WifiInfo info = mock(WifiInfo.class);
        when(info.getRssi()).thenReturn(nextRandInt());
        when(info.getLinkSpeed()).thenReturn(nextRandInt());
        long eventTimeMs = nextRandInt();
        when(mClock.getElapsedSinceBootMillis()).thenReturn(eventTimeMs);
        WifiLinkLayerStats stats1 = nextRandomStats(new WifiLinkLayerStats());
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats1);

        // Add 1 LABEL_GOOD
        WifiLinkLayerStats statsGood = addGoodWifiUsabilityStats(nextRandomStats(stats1));
        // IP reachability lost occurs
        mWifiMetrics.addToWifiUsabilityStatsList(TEST_IFACE_NAME, WifiUsabilityStats.LABEL_BAD,
                WifiUsabilityStats.TYPE_IP_REACHABILITY_LOST, -1);

        dumpProtoAndDeserialize();
        assertEquals(2, mDecodedProto.wifiUsabilityStatsList.length);
        WifiUsabilityStats[] statsList = mDecodedProto.wifiUsabilityStatsList;
        assertEquals(WifiUsabilityStats.LABEL_BAD, statsList[1].label);
        assertEquals(WifiUsabilityStats.TYPE_IP_REACHABILITY_LOST, statsList[1].triggerType);
        assertEquals(eventTimeMs, statsList[1].timeStampMs);
    }

    /**
     * Test the WifiLock active session statistics
     */
    @Test
    public void testWifiLockActiveSession() throws Exception {
        mWifiMetrics.addWifiLockActiveSession(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 100000, true, false, false);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_DEACTIVATED, new int[]{TEST_UID},
                        new String[]{TEST_TAG},
                        WifiStatsLog.WIFI_LOCK_DEACTIVATED__MODE__WIFI_MODE_FULL_HIGH_PERF, 100000,
                        true, false, false));

        mWifiMetrics.addWifiLockActiveSession(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 10000, true, true, false);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_DEACTIVATED, new int[]{TEST_UID},
                        new String[]{TEST_TAG},
                        WifiStatsLog.WIFI_LOCK_DEACTIVATED__MODE__WIFI_MODE_FULL_HIGH_PERF, 10000,
                        true, true, false));

        mWifiMetrics.addWifiLockActiveSession(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 10000000, true, true, true);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_DEACTIVATED, new int[]{TEST_UID},
                        new String[]{TEST_TAG},
                        WifiStatsLog.WIFI_LOCK_DEACTIVATED__MODE__WIFI_MODE_FULL_HIGH_PERF,
                        10000000, true, true, true));

        mWifiMetrics.addWifiLockActiveSession(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 1000, false, false, false);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_DEACTIVATED, new int[]{TEST_UID},
                        new String[]{TEST_TAG},
                        WifiStatsLog.WIFI_LOCK_DEACTIVATED__MODE__WIFI_MODE_FULL_HIGH_PERF, 1000,
                        false, false, false));

        mWifiMetrics.addWifiLockActiveSession(WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 90000, false, false, false);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_DEACTIVATED, new int[]{TEST_UID},
                        new String[]{TEST_TAG},
                        WifiStatsLog.WIFI_LOCK_DEACTIVATED__MODE__WIFI_MODE_FULL_LOW_LATENCY, 90000,
                        false, false, false));

        mWifiMetrics.addWifiLockActiveSession(WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 900000, true, false, false);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_DEACTIVATED, new int[]{TEST_UID},
                        new String[]{TEST_TAG},
                        WifiStatsLog.WIFI_LOCK_DEACTIVATED__MODE__WIFI_MODE_FULL_LOW_LATENCY,
                        900000, true, false, false));

        mWifiMetrics.addWifiLockActiveSession(WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 9000, true, true, false);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_DEACTIVATED, new int[]{TEST_UID},
                        new String[]{TEST_TAG},
                        WifiStatsLog.WIFI_LOCK_DEACTIVATED__MODE__WIFI_MODE_FULL_LOW_LATENCY, 9000,
                        true, true, false));

        mWifiMetrics.addWifiLockActiveSession(WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 20000000, true, true, true);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_DEACTIVATED, new int[]{TEST_UID},
                        new String[]{TEST_TAG},
                        WifiStatsLog.WIFI_LOCK_DEACTIVATED__MODE__WIFI_MODE_FULL_LOW_LATENCY,
                        20000000, true, true, true));

        dumpProtoAndDeserialize();

        assertEquals(10111000, mDecodedProto.wifiLockStats.highPerfActiveTimeMs);
        assertEquals(20999000, mDecodedProto.wifiLockStats.lowLatencyActiveTimeMs);

        HistogramBucketInt32[] expectedHighPerfHistogram = {
                buildHistogramBucketInt32(1, 10, 1),
                buildHistogramBucketInt32(10, 60, 1),
                buildHistogramBucketInt32(60, 600, 1),
                buildHistogramBucketInt32(3600, Integer.MAX_VALUE, 1),
        };

        HistogramBucketInt32[] expectedLowLatencyHistogram = {
                buildHistogramBucketInt32(1, 10, 1),
                buildHistogramBucketInt32(60, 600, 1),
                buildHistogramBucketInt32(600, 3600, 1),
                buildHistogramBucketInt32(3600, Integer.MAX_VALUE, 1),
        };

        assertHistogramBucketsEqual(expectedHighPerfHistogram,
                mDecodedProto.wifiLockStats.highPerfActiveSessionDurationSecHistogram);

        assertHistogramBucketsEqual(expectedLowLatencyHistogram,
                mDecodedProto.wifiLockStats.lowLatencyActiveSessionDurationSecHistogram);
    }

    /**
     * Test the WifiLock acquisition session statistics
     */
    @Test
    public void testWifiLockAcqSession() throws Exception {
        mWifiMetrics.addWifiLockAcqSession(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 0, 100000, false, false, false);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_RELEASED, new int[]{TEST_UID},
                        new String[]{TEST_TAG}, 0,
                        WifiStatsLog.WIFI_LOCK_RELEASED__MODE__WIFI_MODE_FULL_HIGH_PERF, 100000,
                        false, false, false));

        mWifiMetrics.addWifiLockAcqSession(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 0, 10000, true, false, false);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_RELEASED, new int[]{TEST_UID},
                        new String[]{TEST_TAG}, 0,
                        WifiStatsLog.WIFI_LOCK_RELEASED__MODE__WIFI_MODE_FULL_HIGH_PERF, 10000,
                        true, false, false));

        mWifiMetrics.addWifiLockAcqSession(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 0, 10000000, true, true, false);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_RELEASED, new int[]{TEST_UID},
                        new String[]{TEST_TAG}, 0,
                        WifiStatsLog.WIFI_LOCK_RELEASED__MODE__WIFI_MODE_FULL_HIGH_PERF, 10000000,
                        true, true, false));

        mWifiMetrics.addWifiLockAcqSession(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 0, 1000, true, true, true);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_RELEASED, new int[]{TEST_UID},
                        new String[]{TEST_TAG}, 0,
                        WifiStatsLog.WIFI_LOCK_RELEASED__MODE__WIFI_MODE_FULL_HIGH_PERF, 1000,
                        true, true, true));


        mWifiMetrics.addWifiLockAcqSession(WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 0, 90000, false, false, false);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_RELEASED, new int[]{TEST_UID},
                        new String[]{TEST_TAG}, 0,
                        WifiStatsLog.WIFI_LOCK_RELEASED__MODE__WIFI_MODE_FULL_LOW_LATENCY, 90000,
                        false, false, false));

        mWifiMetrics.addWifiLockAcqSession(WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 0, 900000, true, false, false);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_RELEASED, new int[]{TEST_UID},
                        new String[]{TEST_TAG}, 0,
                        WifiStatsLog.WIFI_LOCK_RELEASED__MODE__WIFI_MODE_FULL_LOW_LATENCY, 900000,
                        true, false, false));

        mWifiMetrics.addWifiLockAcqSession(WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 0, 9000, true, true, false);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_RELEASED, new int[]{TEST_UID},
                        new String[]{TEST_TAG}, 0,
                        WifiStatsLog.WIFI_LOCK_RELEASED__MODE__WIFI_MODE_FULL_LOW_LATENCY, 9000,
                        true, true, false));

        mWifiMetrics.addWifiLockAcqSession(WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                new int[]{TEST_UID}, new String[]{TEST_TAG}, 0, 20000000, true, true, true);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_LOCK_RELEASED, new int[]{TEST_UID},
                        new String[]{TEST_TAG}, 0,
                        WifiStatsLog.WIFI_LOCK_RELEASED__MODE__WIFI_MODE_FULL_LOW_LATENCY, 20000000,
                        true, true, true));

        dumpProtoAndDeserialize();

        HistogramBucketInt32[] expectedHighPerfHistogram = {
                buildHistogramBucketInt32(1, 10, 1),
                buildHistogramBucketInt32(10, 60, 1),
                buildHistogramBucketInt32(60, 600, 1),
                buildHistogramBucketInt32(3600, Integer.MAX_VALUE, 1),
        };

        HistogramBucketInt32[] expectedLowLatencyHistogram = {
                buildHistogramBucketInt32(1, 10, 1),
                buildHistogramBucketInt32(60, 600, 1),
                buildHistogramBucketInt32(600, 3600, 1),
                buildHistogramBucketInt32(3600, Integer.MAX_VALUE, 1),
        };

        assertHistogramBucketsEqual(expectedHighPerfHistogram,
                mDecodedProto.wifiLockStats.highPerfLockAcqDurationSecHistogram);

        assertHistogramBucketsEqual(expectedLowLatencyHistogram,
                mDecodedProto.wifiLockStats.lowLatencyLockAcqDurationSecHistogram);
    }

    /**
     * Verify that LABEL_GOOD stats are generated if Wifi score breaches low and there
     * is no WifiIsUnusableEvent in MIN_SCORE_BREACH_TO_GOOD_STATS_WAIT_TIME_MS millis
     * @throws Exception
     */
    @Test
    public void testGoodStatsAreGeneratedByWifiScoreBreachLow() throws Exception {
        // The elapsed time falls into the interval for adding good stats
        createTestForDataCollectionByScoreBreach(
                WifiMetrics.MIN_SCORE_BREACH_TO_GOOD_STATS_WAIT_TIME_MS + 1,
                false, true);
        dumpProtoAndDeserialize();
        assertEquals(2, mDecodedProto.wifiUsabilityStatsList.length);
    }

    /**
     * Verify that LABEL_GOOD stats are not generated if Wifi score breaches low and the checking
     * time is less than MIN_SCORE_BREACH_TO_GOOD_STATS_WAIT_TIME_MS millis
     * @throws Exception
     */
    @Test
    public void testGoodStatsAreNotGeneratedByWifiScoreBreachLow() throws Exception {
        // The elapsed time is shorter than necessary to add good stats
        createTestForDataCollectionByScoreBreach(
                WifiMetrics.MIN_SCORE_BREACH_TO_GOOD_STATS_WAIT_TIME_MS - 1,
                false, true);
        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.wifiUsabilityStatsList.length);
    }

    /**
     * Verify that LABEL_GOOD stats are not generated if Wifi score breaches low and the checking
     * time is greater than VALIDITY_PERIOD_OF_SCORE_BREACH_LOW_MS
     * @throws Exception
     */
    @Test
    public void testGoodStatsAreNotGeneratedIfWifiScoreBreachExpires() throws Exception {
        // The Wifi score breaching expires for adding good stats
        createTestForDataCollectionByScoreBreach(
                WifiMetrics.VALIDITY_PERIOD_OF_SCORE_BREACH_LOW_MS + 1,
                false, true);
        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.wifiUsabilityStatsList.length);
    }

    /**
     * Verify that LABEL_GOOD stats are not generated if Wifi score breaches low and there is
     * WifiIsUnusableEvent occured within MIN_SCORE_BREACH_TO_GOOD_STATS_WAIT_TIME_MS millis
     * @throws Exception
     */
    @Test
    public void testGoodStatsAreNotGeneratedIfBadEventOccured() throws Exception {
        // The elapsed time falls into the interval for adding good stats and bad event occurs
        createTestForDataCollectionByScoreBreach(
                WifiMetrics.MIN_SCORE_BREACH_TO_GOOD_STATS_WAIT_TIME_MS + 1,
                true, true);
        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.wifiUsabilityStatsList.length);
    }

    /**
     * Verify that LABEL_GOOD stats are generated if Wifi usability score breaches low and there
     * is no WifiIsUnusableEvent in MIN_SCORE_BREACH_TO_GOOD_STATS_WAIT_TIME_MS millis
     * @throws Exception
     */
    @Test
    public void testGoodStatsAreGeneratedByWifiUsabilityScoreBreachLow() throws Exception {
        // The elapsed time falls into the interval for adding good stats
        createTestForDataCollectionByScoreBreach(
                WifiMetrics.MIN_SCORE_BREACH_TO_GOOD_STATS_WAIT_TIME_MS + 1,
                false, false);
        dumpProtoAndDeserialize();
        assertEquals(2, mDecodedProto.wifiUsabilityStatsList.length);
    }

    /**
     * Verify that LABEL_GOOD stats are not generated if Wifi usability score breaches low and
     * the checking time is less than MIN_SCORE_BREACH_TO_GOOD_STATS_WAIT_TIME_MS millis
     * @throws Exception
     */
    @Test
    public void testGoodStatsAreNotGeneratedByWifiUsabilityScoreBreachLow() throws Exception {
        // The elapsed time is shorter than necessary to add good stats
        createTestForDataCollectionByScoreBreach(
                WifiMetrics.MIN_SCORE_BREACH_TO_GOOD_STATS_WAIT_TIME_MS - 1,
                false, false);
        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.wifiUsabilityStatsList.length);
    }

    /**
     * Verify that LABEL_GOOD stats are not generated if Wifi usability score breaches low and
     * the checking time is greater than VALIDITY_PERIOD_OF_SCORE_BREACH_LOW_MS
     * @throws Exception
     */
    @Test
    public void testGoodStatsAreNotGeneratedIfWifiUsabilityScoreBreachExpires() throws Exception {
        // The Wifi usability score breaching expires for adding good stats
        createTestForDataCollectionByScoreBreach(
                WifiMetrics.VALIDITY_PERIOD_OF_SCORE_BREACH_LOW_MS + 1,
                false, false);
        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.wifiUsabilityStatsList.length);
    }

    /**
     * Verify that LABEL_GOOD stats are not generated if Wifi usability score breaches low and there
     * is WifiIsUnusableEvent occured within MIN_SCORE_BREACH_TO_GOOD_STATS_WAIT_TIME_MS millis
     * @throws Exception
     */
    @Test
    public void testGoodStatsAreNotGeneratedIfBadEventOccuredForUsabilityScore() throws Exception {
        // The elapsed time falls into the interval for adding good stats and bad event occurs
        createTestForDataCollectionByScoreBreach(
                WifiMetrics.MIN_SCORE_BREACH_TO_GOOD_STATS_WAIT_TIME_MS + 1,
                true, false);
        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.wifiUsabilityStatsList.length);
    }

    /**
     * Verify that incrementNumWifiToggles increments the corrects fields based on input.
     */
    @Test
    public void testIncrementNumWifiToggles() throws Exception {
        mWifiMetrics.incrementNumWifiToggles(true, true);
        for (int i = 0; i < 2; i++) {
            mWifiMetrics.incrementNumWifiToggles(true, false);
        }
        for (int i = 0; i < 3; i++) {
            mWifiMetrics.incrementNumWifiToggles(false, true);
        }
        for (int i = 0; i < 4; i++) {
            mWifiMetrics.incrementNumWifiToggles(false, false);
        }
        dumpProtoAndDeserialize();
        assertEquals(1, mDecodedProto.wifiToggleStats.numToggleOnPrivileged);
        assertEquals(2, mDecodedProto.wifiToggleStats.numToggleOffPrivileged);
        assertEquals(3, mDecodedProto.wifiToggleStats.numToggleOnNormal);
        assertEquals(4, mDecodedProto.wifiToggleStats.numToggleOffNormal);
    }

    /**
     * Verify metered stats are counted properly for saved and ephemeral networks.
     */
    @Test
    public void testMeteredNetworkMetrics() throws Exception {
        // Test without metered override
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration config1 = WifiConfigurationTestUtil.createPskNetwork();
        config.fromWifiNetworkSuggestion = false;
        config1.fromWifiNetworkSuggestion = true;
        mWifiMetrics.addMeteredStat(config, false);
        mWifiMetrics.addMeteredStat(config1, true);
        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.meteredNetworkStatsSaved.numMetered);
        assertEquals(1, mDecodedProto.meteredNetworkStatsSaved.numUnmetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSaved.numOverrideMetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSaved.numOverrideUnmetered);
        assertEquals(1, mDecodedProto.meteredNetworkStatsSuggestion.numMetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSuggestion.numUnmetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSuggestion.numOverrideMetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSuggestion.numOverrideUnmetered);

        // Test with metered override
        config = WifiConfigurationTestUtil.createPskNetwork();
        config1 = WifiConfigurationTestUtil.createPskNetwork();
        config.meteredOverride = WifiConfiguration.METERED_OVERRIDE_METERED;
        config1.meteredOverride = WifiConfiguration.METERED_OVERRIDE_NOT_METERED;
        mWifiMetrics.addMeteredStat(config, true);
        mWifiMetrics.addMeteredStat(config1, true);
        dumpProtoAndDeserialize();
        assertEquals(1, mDecodedProto.meteredNetworkStatsSaved.numMetered);
        assertEquals(1, mDecodedProto.meteredNetworkStatsSaved.numUnmetered);
        assertEquals(1, mDecodedProto.meteredNetworkStatsSaved.numOverrideMetered);
        assertEquals(1, mDecodedProto.meteredNetworkStatsSaved.numOverrideUnmetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSuggestion.numMetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSuggestion.numUnmetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSuggestion.numOverrideMetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSuggestion.numOverrideUnmetered);
    }

    /**
     * Verify that the same network does not get counted twice
     */
    @Test
    public void testMeteredNetworkMetricsNoDoubleCount() throws Exception {
        WifiConfiguration config = new WifiConfiguration();
        config.ephemeral = false;
        mWifiMetrics.addMeteredStat(config, false);
        mWifiMetrics.addMeteredStat(config, true);
        mWifiMetrics.addMeteredStat(config, true);
        dumpProtoAndDeserialize();
        assertEquals(1, mDecodedProto.meteredNetworkStatsSaved.numMetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSaved.numUnmetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSaved.numOverrideMetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSaved.numOverrideUnmetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSuggestion.numMetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSuggestion.numUnmetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSuggestion.numOverrideMetered);
        assertEquals(0, mDecodedProto.meteredNetworkStatsSuggestion.numOverrideUnmetered);
    }

    /**
     * Create a test to verify data collection logic triggered by score breaching low
     * @param elapsedTimeAfterBreach The elapsed time after score breaches low
     * @param isThereBadEvent Whether there is a bad event happened after score breaches low
     * @param isWifiScore Whether it is Wifi score or not that breaches the threshold
     */
    private void createTestForDataCollectionByScoreBreach(
            long elapsedTimeAfterBreach, boolean isThereBadEvent, boolean isWifiScore) {
        WifiInfo info = mock(WifiInfo.class);
        when(info.getRssi()).thenReturn(nextRandInt());
        when(info.getLinkSpeed()).thenReturn(nextRandInt());
        WifiLinkLayerStats stats2 = new WifiLinkLayerStats();
        mWifiMetrics.setWifiState(TEST_IFACE_NAME, WifiMetricsProto.WifiLog.WIFI_ASSOCIATED);

        addOneBadWifiUsabilityStats(info);
        if (isWifiScore) {
            stats2 = wifiScoreBreachesLow(info, stats2);
        } else {
            stats2 = wifiUsabilityScoreBreachesLow(info, stats2);
        }
        if (isThereBadEvent) {
            mWifiMetrics.logWifiIsUnusableEvent(TEST_IFACE_NAME,
                    WifiIsUnusableEvent.TYPE_DATA_STALL_BAD_TX, -1);
        }
        when(mClock.getElapsedSinceBootMillis()).thenReturn(elapsedTimeAfterBreach);
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats2);
    }

    // Simulate adding one LABEL_BAD WifiUsabilityStats
    private void addOneBadWifiUsabilityStats(WifiInfo info) {
        WifiLinkLayerStats stats1 = new WifiLinkLayerStats();
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats1);
        mWifiMetrics.addToWifiUsabilityStatsList(TEST_IFACE_NAME, WifiUsabilityStats.LABEL_BAD,
                WifiUsabilityStats.TYPE_DATA_STALL_BAD_TX, -1);
    }

    // Simulate that Wifi score breaches low
    private WifiLinkLayerStats wifiScoreBreachesLow(WifiInfo info, WifiLinkLayerStats stats2) {
        int upper = WifiMetrics.LOW_WIFI_SCORE + 7;
        int lower = WifiMetrics.LOW_WIFI_SCORE - 8;
        mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, upper);
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats2);
        stats2 = nextRandomStats(stats2);
        long timeMs = 0;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(timeMs);
        // Wifi score breaches low
        mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, lower);
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats2);
        stats2 = nextRandomStats(stats2);
        return stats2;
    }

    // Simulate that Wifi usability score breaches low
    private WifiLinkLayerStats wifiUsabilityScoreBreachesLow(WifiInfo info,
            WifiLinkLayerStats stats2) {
        int upper = WifiMetrics.LOW_WIFI_USABILITY_SCORE + 7;
        int lower = WifiMetrics.LOW_WIFI_USABILITY_SCORE - 8;
        mWifiMetrics.incrementWifiUsabilityScoreCount(TEST_IFACE_NAME, 1, upper, 30);
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats2);
        stats2 = nextRandomStats(stats2);
        long timeMs = 0;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(timeMs);
        // Wifi usability score breaches low
        mWifiMetrics.incrementWifiUsabilityScoreCount(TEST_IFACE_NAME, 2, lower, 30);
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats2);
        stats2 = nextRandomStats(stats2);
        return stats2;
    }

    /**
     * Verify the counts of passpoint profile type are correct.
     * @param profileTypes type and count of installed passpoint profiles
     */
    private void assertPasspointProfileTypeCount(PasspointProfileTypeCount[] profileTypes) {
        for (PasspointProfileTypeCount passpointProfileType : profileTypes) {
            switch(passpointProfileType.eapMethodType) {
                case PasspointProfileTypeCount.TYPE_EAP_AKA:
                    assertEquals(NUM_EAP_AKA_TYPE, passpointProfileType.count);
                    break;
                case PasspointProfileTypeCount.TYPE_EAP_AKA_PRIME:
                    assertEquals(NUM_EAP_AKA_PRIME_TYPE, passpointProfileType.count);
                    break;
                case PasspointProfileTypeCount.TYPE_EAP_SIM:
                    assertEquals(NUM_EAP_SIM_TYPE, passpointProfileType.count);
                    break;
                case PasspointProfileTypeCount.TYPE_EAP_TLS:
                    assertEquals(NUM_EAP_TLS_TYPE, passpointProfileType.count);
                    break;
                case PasspointProfileTypeCount.TYPE_EAP_TTLS:
                    assertEquals(NUM_EAP_TTLS_TYPE, passpointProfileType.count);
                    break;
                default:
                    fail("unknown type counted");
            }
        }
    }

    /**
     * Verify that the LABEL_BAD Wifi usability stats are not saved if screen state is off.
     * @throws Exception
     */
    @Test
    public void verifyLabelBadStatsAreNotSavedIfScreenIsOff() throws Exception {
        setScreenState(false);
        WifiInfo info = mock(WifiInfo.class);
        when(info.getRssi()).thenReturn(nextRandInt());
        when(info.getLinkSpeed()).thenReturn(nextRandInt());
        WifiLinkLayerStats stats1 = nextRandomStats(new WifiLinkLayerStats());
        mWifiMetrics.updateWifiUsabilityStatsEntries(TEST_IFACE_NAME, info, stats1);

        // Add 1 LABEL_GOOD
        WifiLinkLayerStats statsGood = addGoodWifiUsabilityStats(nextRandomStats(stats1));
        // IP reachability lost occurs
        mWifiMetrics.addToWifiUsabilityStatsList(TEST_IFACE_NAME, WifiUsabilityStats.LABEL_BAD,
                WifiUsabilityStats.TYPE_IP_REACHABILITY_LOST, -1);
        // Wifi data stall occurs
        mWifiMetrics.addToWifiUsabilityStatsList(TEST_IFACE_NAME, WifiUsabilityStats.LABEL_BAD,
                WifiIsUnusableEvent.TYPE_DATA_STALL_BAD_TX, -1);
        // Firmware alert occurs
        mWifiMetrics.logFirmwareAlert(TEST_IFACE_NAME, 2);

        dumpProtoAndDeserialize();
        assertEquals(0, mDecodedProto.wifiUsabilityStatsList.length);
    }

    /**
     * Test the logging of connection duration stats
     */
    @Test
    public void testConnectionDurationStats() throws Exception {
        for (int i = 0; i < 2; i++) {
            mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, 52);
            mWifiMetrics.incrementConnectionDuration(TEST_IFACE_NAME, 5000, false, true, -50, 10000,
                    10000);
            mWifiMetrics.incrementWifiScoreCount(TEST_IFACE_NAME, 40);
            mWifiMetrics.incrementConnectionDuration(TEST_IFACE_NAME, 5000, false, true, -50, 10000,
                    10000);
            mWifiMetrics.incrementConnectionDuration(TEST_IFACE_NAME, 3000, true, true, -50, 10000,
                    10000);
            mWifiMetrics.incrementConnectionDuration(TEST_IFACE_NAME, 1000, false, false, -50,
                    10000, 10000);
            mWifiMetrics.incrementConnectionDuration(TEST_IFACE_NAME, 500, true, false, -50, 10000,
                    10000);
        }
        dumpProtoAndDeserialize();

        assertEquals(6000,
                mDecodedProto.connectionDurationStats.totalTimeSufficientThroughputMs);
        assertEquals(20000,
                mDecodedProto.connectionDurationStats.totalTimeInsufficientThroughputMs);
        assertEquals(10000,
                mDecodedProto.connectionDurationStats.totalTimeInsufficientThroughputDefaultWifiMs);
        assertEquals(3000,
                mDecodedProto.connectionDurationStats.totalTimeCellularDataOffMs);
    }

    /**
     * Test the logging of isExternalWifiScorerOn
     */
    @Test
    public void testIsExternalWifiScorerOn() throws Exception {
        mWifiMetrics.setIsExternalWifiScorerOn(true, TEST_UID);
        dumpProtoAndDeserialize();
        assertEquals(true, mDecodedProto.isExternalWifiScorerOn);
    }

    /*
     * Test the logging of Wi-Fi off
     */
    @Test
    public void testWifiOff() throws Exception {
        // if not deferred, timeout and duration should be ignored.
        mWifiMetrics.noteWifiOff(false, false, 0);
        mWifiMetrics.noteWifiOff(false, true, 999);

        // deferred, not timed out
        mWifiMetrics.noteWifiOff(true, false, 0);
        mWifiMetrics.noteWifiOff(true, false, 1000);

        // deferred and timed out
        mWifiMetrics.noteWifiOff(true, true, 2000);
        mWifiMetrics.noteWifiOff(true, true, 2000);
        mWifiMetrics.noteWifiOff(true, true, 4000);

        dumpProtoAndDeserialize();

        assertEquals(7,
                mDecodedProto.wifiOffMetrics.numWifiOff);
        assertEquals(5,
                mDecodedProto.wifiOffMetrics.numWifiOffDeferring);
        assertEquals(3,
                mDecodedProto.wifiOffMetrics.numWifiOffDeferringTimeout);

        Int32Count[] expectedHistogram = {
                buildInt32Count(0, 1),
                buildInt32Count(1000, 1),
                buildInt32Count(2000, 2),
                buildInt32Count(4000, 1),
        };
        assertKeyCountsEqual(expectedHistogram,
                mDecodedProto.wifiOffMetrics.wifiOffDeferringTimeHistogram);
    }

    /*
     * Test the logging of Wi-Fi off
     */
    @Test
    public void testSoftApConfigLimitationMetrics() throws Exception {
        SoftApConfiguration originalConfig = new SoftApConfiguration.Builder()
                .setSsid("TestSSID").build();
        SoftApConfiguration needToResetCongig = new SoftApConfiguration.Builder(originalConfig)
                .setPassphrase("TestPassphreas", SoftApConfiguration.SECURITY_TYPE_WPA3_SAE)
                .setClientControlByUserEnabled(true)
                .setMaxNumberOfClients(10)
                .build();
        mWifiMetrics.noteSoftApConfigReset(originalConfig, needToResetCongig);

        mWifiMetrics.noteSoftApClientBlocked(5);
        mWifiMetrics.noteSoftApClientBlocked(5);
        mWifiMetrics.noteSoftApClientBlocked(5);
        mWifiMetrics.noteSoftApClientBlocked(8);

        dumpProtoAndDeserialize();

        assertEquals(1,
                mDecodedProto.softApConfigLimitationMetrics.numSecurityTypeResetToDefault);
        assertEquals(1,
                mDecodedProto.softApConfigLimitationMetrics.numMaxClientSettingResetToDefault);
        assertEquals(1,
                mDecodedProto.softApConfigLimitationMetrics.numClientControlByUserResetToDefault);

        Int32Count[] expectedHistogram = {
                buildInt32Count(5, 3),
                buildInt32Count(8, 1),
        };
        assertKeyCountsEqual(expectedHistogram,
                mDecodedProto.softApConfigLimitationMetrics.maxClientSettingWhenReachHistogram);
    }

    /**
     * Test the logging of channel utilization
     */
    @Test
    public void testChannelUtilization() throws Exception {
        mWifiMetrics.incrementChannelUtilizationCount(180, 2412);
        mWifiMetrics.incrementChannelUtilizationCount(150, 2412);
        mWifiMetrics.incrementChannelUtilizationCount(230, 2412);
        mWifiMetrics.incrementChannelUtilizationCount(20, 5510);
        mWifiMetrics.incrementChannelUtilizationCount(50, 5510);

        dumpProtoAndDeserialize();

        HistogramBucketInt32[] expected2GHistogram = {
                buildHistogramBucketInt32(150, 175, 1),
                buildHistogramBucketInt32(175, 200, 1),
                buildHistogramBucketInt32(225, Integer.MAX_VALUE, 1),
        };

        HistogramBucketInt32[] expectedAbove2GHistogram = {
                buildHistogramBucketInt32(Integer.MIN_VALUE, 25, 1),
                buildHistogramBucketInt32(50, 75, 1),
        };

        assertHistogramBucketsEqual(expected2GHistogram,
                mDecodedProto.channelUtilizationHistogram.utilization2G);
        assertHistogramBucketsEqual(expectedAbove2GHistogram,
                mDecodedProto.channelUtilizationHistogram.utilizationAbove2G);
    }

    /**
     * Test the logging of Tx and Rx throughput
     */
    @Test
    public void testThroughput() throws Exception {
        mWifiMetrics.incrementThroughputKbpsCount(500, 800, 2412);
        mWifiMetrics.incrementThroughputKbpsCount(5_000, 4_000, 2412);
        mWifiMetrics.incrementThroughputKbpsCount(54_000, 48_000, 2412);
        mWifiMetrics.incrementThroughputKbpsCount(50_000, 49_000, 5510);
        mWifiMetrics.incrementThroughputKbpsCount(801_000, 790_000, 5510);
        mWifiMetrics.incrementThroughputKbpsCount(1100_000, 1200_000, 5510);
        mWifiMetrics.incrementThroughputKbpsCount(1599_000, 1800_000, 6120);
        dumpProtoAndDeserialize();

        HistogramBucketInt32[] expectedTx2GHistogramMbps = {
                buildHistogramBucketInt32(Integer.MIN_VALUE, 1, 1),
                buildHistogramBucketInt32(5, 10, 1),
                buildHistogramBucketInt32(50, 100, 1),
        };

        HistogramBucketInt32[] expectedRx2GHistogramMbps = {
                buildHistogramBucketInt32(Integer.MIN_VALUE, 1, 1),
                buildHistogramBucketInt32(1, 5, 1),
                buildHistogramBucketInt32(25, 50, 1),
        };

        HistogramBucketInt32[] expectedTxAbove2GHistogramMbps = {
                buildHistogramBucketInt32(50, 100, 1),
                buildHistogramBucketInt32(800, 1200, 2),
                buildHistogramBucketInt32(1200, 1600, 1),
        };

        HistogramBucketInt32[] expectedRxAbove2GHistogramMbps = {
                buildHistogramBucketInt32(25, 50, 1),
                buildHistogramBucketInt32(600, 800, 1),
                buildHistogramBucketInt32(1200, 1600, 1),
                buildHistogramBucketInt32(1600, Integer.MAX_VALUE, 1),
        };

        assertHistogramBucketsEqual(expectedTx2GHistogramMbps,
                mDecodedProto.throughputMbpsHistogram.tx2G);
        assertHistogramBucketsEqual(expectedTxAbove2GHistogramMbps,
                mDecodedProto.throughputMbpsHistogram.txAbove2G);
        assertHistogramBucketsEqual(expectedRx2GHistogramMbps,
                mDecodedProto.throughputMbpsHistogram.rx2G);
        assertHistogramBucketsEqual(expectedRxAbove2GHistogramMbps,
                mDecodedProto.throughputMbpsHistogram.rxAbove2G);
    }

    /**
     * Test the Initial partial scan statistics
     */
    @Test
    public void testInitPartialScan() throws Exception {
        mWifiMetrics.incrementInitialPartialScanCount();
        mWifiMetrics.reportInitialPartialScan(4, true);
        mWifiMetrics.incrementInitialPartialScanCount();
        mWifiMetrics.reportInitialPartialScan(2, false);
        mWifiMetrics.incrementInitialPartialScanCount();
        mWifiMetrics.incrementInitialPartialScanCount();
        mWifiMetrics.reportInitialPartialScan(1, false);
        mWifiMetrics.incrementInitialPartialScanCount();
        mWifiMetrics.reportInitialPartialScan(7, true);
        mWifiMetrics.incrementInitialPartialScanCount();
        mWifiMetrics.incrementInitialPartialScanCount();
        mWifiMetrics.reportInitialPartialScan(15, false);
        mWifiMetrics.incrementInitialPartialScanCount();
        mWifiMetrics.reportInitialPartialScan(2, true);
        mWifiMetrics.incrementInitialPartialScanCount();
        mWifiMetrics.reportInitialPartialScan(10, true);

        dumpProtoAndDeserialize();

        assertEquals(9, mDecodedProto.initPartialScanStats.numScans);
        assertEquals(4, mDecodedProto.initPartialScanStats.numSuccessScans);
        assertEquals(3, mDecodedProto.initPartialScanStats.numFailureScans);

        HistogramBucketInt32[] expectedSuccessScanHistogram = {
                buildHistogramBucketInt32(1, 3, 1),
                buildHistogramBucketInt32(3, 5, 1),
                buildHistogramBucketInt32(5, 10, 1),
                buildHistogramBucketInt32(10, Integer.MAX_VALUE, 1),
        };

        HistogramBucketInt32[] expectedFailureScanHistogram = {
                buildHistogramBucketInt32(1, 3, 2),
                buildHistogramBucketInt32(10, Integer.MAX_VALUE, 1),
        };

        assertHistogramBucketsEqual(expectedSuccessScanHistogram,
                mDecodedProto.initPartialScanStats.successfulScanChannelCountHistogram);

        assertHistogramBucketsEqual(expectedFailureScanHistogram,
                mDecodedProto.initPartialScanStats.failedScanChannelCountHistogram);
    }

    /**
     * Test overlapping and non-overlapping connection events return overlapping duration correctly
     */
    @Test
    public void testOverlappingConnectionEvent() throws Exception {
        // Connection event 1
        when(mClock.getElapsedSinceBootMillis()).thenReturn((long) 0);
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, mTestWifiConfig,
                "TestNetwork", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        when(mClock.getElapsedSinceBootMillis()).thenReturn((long) 1000);
        // Connection event 2 overlaps with 1
        assertEquals(1000, mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, mTestWifiConfig,
                "TestNetwork", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID));

        // Connection event 2 ends
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        when(mClock.getElapsedSinceBootMillis()).thenReturn((long) 2000);
        // Connection event 3 doesn't overlap with 2
        assertEquals(0, mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, mTestWifiConfig,
                "TestNetwork", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID));
    }

    @Test
    public void testCarrierWifiConnectionEvent() throws Exception {
        mWifiMetrics.incrementNumOfCarrierWifiConnectionSuccess();
        for (int i = 0; i < 2; i++) {
            mWifiMetrics.incrementNumOfCarrierWifiConnectionAuthFailure();
        }
        for (int i = 0; i < 3; i++) {
            mWifiMetrics.incrementNumOfCarrierWifiConnectionNonAuthFailure();
        }

        dumpProtoAndDeserialize();

        assertEquals(1, mDecodedProto.carrierWifiMetrics.numConnectionSuccess);
        assertEquals(2, mDecodedProto.carrierWifiMetrics.numConnectionAuthFailure);
        assertEquals(3, mDecodedProto.carrierWifiMetrics.numConnectionNonAuthFailure);
    }

    /**
     * Verify the ConnectionEvent is labeled with networkType Passpoint correctly and that the OSU
     * provisioned flag is set to true.
     */
    @Test
    public void testConnectionNetworkTypePasspointFromOsu() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createPasspointNetwork();
        config.updateIdentifier = "7";
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_NONE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        dumpProtoAndDeserialize();

        assertEquals(1, mDecodedProto.connectionEvent.length);
        assertEquals(WifiMetricsProto.ConnectionEvent.TYPE_PASSPOINT,
                mDecodedProto.connectionEvent[0].networkType);
        assertTrue(mDecodedProto.connectionEvent[0].isOsuProvisioned);
    }

    @Test
    public void testFirstConnectAfterBootStats() throws Exception {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(1000L);
        mWifiMetrics.noteWifiEnabledDuringBoot(true);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(2000L);
        mWifiMetrics.noteFirstNetworkSelectionAfterBoot(true);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(3000L);
        mWifiMetrics.noteFirstL2ConnectionAfterBoot(true);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(4000L);
        mWifiMetrics.noteFirstL3ConnectionAfterBoot(true);

        dumpProtoAndDeserialize();

        assertEquals(1000, mDecodedProto
                .firstConnectAfterBootStats.wifiEnabledAtBoot.timestampSinceBootMillis);
        assertTrue(mDecodedProto.firstConnectAfterBootStats.wifiEnabledAtBoot.isSuccess);
        assertEquals(2000, mDecodedProto
                .firstConnectAfterBootStats.firstNetworkSelection.timestampSinceBootMillis);
        assertTrue(mDecodedProto.firstConnectAfterBootStats.firstNetworkSelection.isSuccess);
        assertEquals(3000, mDecodedProto
                .firstConnectAfterBootStats.firstL2Connection.timestampSinceBootMillis);
        assertTrue(mDecodedProto.firstConnectAfterBootStats.firstL2Connection.isSuccess);
        assertEquals(4000, mDecodedProto
                .firstConnectAfterBootStats.firstL3Connection.timestampSinceBootMillis);
        assertTrue(mDecodedProto.firstConnectAfterBootStats.firstL3Connection.isSuccess);
    }

    @Test
    public void testFirstConnectAfterBootStats_firstCallWins() throws Exception {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(1000L);
        mWifiMetrics.noteWifiEnabledDuringBoot(true);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(2000L);
        mWifiMetrics.noteWifiEnabledDuringBoot(false);

        dumpProtoAndDeserialize();

        assertEquals(1000, mDecodedProto
                .firstConnectAfterBootStats.wifiEnabledAtBoot.timestampSinceBootMillis);
        assertTrue(mDecodedProto.firstConnectAfterBootStats.wifiEnabledAtBoot.isSuccess);
    }

    @Test
    public void testFirstConnectAfterBootStats_secondDumpNull() throws Exception {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(1000L);
        mWifiMetrics.noteWifiEnabledDuringBoot(true);

        dumpProtoAndDeserialize();

        when(mClock.getElapsedSinceBootMillis()).thenReturn(2000L);
        mWifiMetrics.noteWifiEnabledDuringBoot(false);

        dumpProtoAndDeserialize();

        assertNull(mDecodedProto.firstConnectAfterBootStats);
    }

    @Test
    public void testFirstConnectAfterBootStats_falseInvalidatesSubsequentCalls() throws Exception {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(1000L);
        mWifiMetrics.noteWifiEnabledDuringBoot(false);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(2000L);
        mWifiMetrics.noteFirstNetworkSelectionAfterBoot(true);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(3000L);
        mWifiMetrics.noteFirstL2ConnectionAfterBoot(true);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(4000L);
        mWifiMetrics.noteFirstL3ConnectionAfterBoot(true);

        dumpProtoAndDeserialize();

        assertEquals(1000, mDecodedProto
                .firstConnectAfterBootStats.wifiEnabledAtBoot.timestampSinceBootMillis);
        assertFalse(mDecodedProto.firstConnectAfterBootStats.wifiEnabledAtBoot.isSuccess);
        assertNull(mDecodedProto.firstConnectAfterBootStats.firstNetworkSelection);
        assertNull(mDecodedProto.firstConnectAfterBootStats.firstL2Connection);
        assertNull(mDecodedProto.firstConnectAfterBootStats.firstL3Connection);
    }

    @Test
    public void testWifiConnectionResultAtomNotEmittedWithNoConnectionEndEvent() {
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, createComplexWifiConfig(),
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED), anyBoolean(), anyInt(), anyInt(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean(), anyInt(), anyBoolean(),
                anyBoolean(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                eq(TEST_UID)),
                times(0));
    }

    @Test
    public void testWifiConnectionResultAtomNotEmittedWithNoConnectionStartEvent() {
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                WifiMetricsProto.ConnectionEvent.HLF_DHCP,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, TEST_CANDIDATE_FREQ,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED), anyBoolean(), anyInt(), anyInt(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean(), anyInt(), anyBoolean(),
                anyBoolean(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                eq(TEST_UID)),
                times(0));
    }

    @Test
    public void testWifiConnectionResultAtomEmittedOnlyOnceWithMultipleConnectionEndEvents() {
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, createComplexWifiConfig(),
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        for (int i = 0; i < 5; i++) {
            mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                    WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                    WifiMetricsProto.ConnectionEvent.HLF_DHCP,
                    WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, TEST_CANDIDATE_FREQ,
                    TEST_CONNECTION_FAILURE_STATUS_CODE);
        }

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED), eq(false),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_AUTHENTICATION_GENERAL),
                eq(-80), eq(0),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__BAND__BAND_2G),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__AUTH_TYPE__AUTH_TYPE_WPA2_PSK),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__TRIGGER__AUTOCONNECT_BOOT),
                eq(true),
                eq(0), eq(true), eq(false), eq(1), eq(TEST_CONNECTION_FAILURE_STATUS_CODE),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), eq(TEST_UID)),
                times(1));
    }

    @Test
    public void testWifiConnectionResultAtomNewSessionOverwritesPreviousSession() {

        WifiConfiguration config1 = createComplexWifiConfig();
        config1.getNetworkSelectionStatus().getCandidate().level = -50;

        WifiConfiguration config2 = createComplexWifiConfig();
        config2.getNetworkSelectionStatus().getCandidate().level = -60;

        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config1,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config2,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, true,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                WifiMetricsProto.ConnectionEvent.HLF_DHCP,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, TEST_CANDIDATE_FREQ,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED), eq(false),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__FAILURE_CODE__FAILURE_AUTHENTICATION_GENERAL),
                eq(-60), eq(0),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__BAND__BAND_2G),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__AUTH_TYPE__AUTH_TYPE_WPA2_PSK),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__TRIGGER__AUTOCONNECT_BOOT),
                eq(true),
                eq(0),  eq(true), eq(true), eq(1), eq(TEST_CONNECTION_FAILURE_STATUS_CODE),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), eq(TEST_UID)),
                times(1));
    }

    @Test
    public void testWifiConnectionResultAtomHasCorrectTriggers() {
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, createComplexWifiConfig(),
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_NONE, TEST_CANDIDATE_FREQ,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        mWifiMetrics.reportNetworkDisconnect(TEST_IFACE_NAME, 0, 0, 0, 0);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED), anyBoolean(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__TRIGGER__AUTOCONNECT_BOOT),
                anyBoolean(), anyInt(), anyBoolean(), anyBoolean(), anyInt(),
                eq(TEST_CONNECTION_FAILURE_STATUS_CODE), anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), eq(TEST_UID)));

        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, createComplexWifiConfig(),
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_NONE, TEST_CANDIDATE_FREQ,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        mWifiMetrics.reportNetworkDisconnect(TEST_IFACE_NAME, 0, 0, 0, 0);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED), anyBoolean(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__TRIGGER__RECONNECT_SAME_NETWORK),
                anyBoolean(), anyInt(), anyBoolean(), anyBoolean(), anyInt(),
                eq(TEST_CONNECTION_FAILURE_STATUS_CODE), anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), eq(TEST_UID)));

        WifiConfiguration configOtherNetwork = createComplexWifiConfig();
        configOtherNetwork.networkId = 21;
        configOtherNetwork.SSID = "OtherNetwork";
        mWifiMetrics.setNominatorForNetwork(configOtherNetwork.networkId,
                WifiMetricsProto.ConnectionEvent.NOMINATOR_SAVED);

        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, configOtherNetwork,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_NONE, TEST_CANDIDATE_FREQ,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        mWifiMetrics.reportNetworkDisconnect(TEST_IFACE_NAME, 0, 0, 0, 0);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED), anyBoolean(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__TRIGGER__AUTOCONNECT_CONFIGURED_NETWORK),
                anyBoolean(), anyInt(), anyBoolean(), anyBoolean(), anyInt(),
                eq(TEST_CONNECTION_FAILURE_STATUS_CODE), anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), eq(TEST_UID)));

        WifiConfiguration config = createComplexWifiConfig();
        config.networkId = 42;
        mWifiMetrics.setNominatorForNetwork(config.networkId,
                WifiMetricsProto.ConnectionEvent.NOMINATOR_MANUAL);

        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config,
                "GREEN", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_NONE, TEST_CANDIDATE_FREQ,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED), anyBoolean(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__TRIGGER__MANUAL),
                anyBoolean(), anyInt(), anyBoolean(), anyBoolean(), anyInt(),
                eq(TEST_CONNECTION_FAILURE_STATUS_CODE), anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), eq(TEST_UID)));
    }

    @Test
    public void testWifiDisconnectAtomEmittedOnDisconnectFromSuccessfulSession() {
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, createComplexWifiConfig(),
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        long connectionEndTimeMs = 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(connectionEndTimeMs);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_NONE, TEST_CANDIDATE_FREQ,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        long wifiDisconnectTimeMs = 2000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(wifiDisconnectTimeMs);
        int linkSpeed = 100;
        int reason = 42;
        mWifiMetrics.reportNetworkDisconnect(TEST_IFACE_NAME, reason, TEST_CANDIDATE_LEVEL,
                linkSpeed, 0);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_DISCONNECT_REPORTED),
                eq((int) (wifiDisconnectTimeMs - connectionEndTimeMs) / 1000),
                eq(reason),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__BAND__BAND_2G),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__AUTH_TYPE__AUTH_TYPE_WPA2_PSK),
                eq(TEST_CANDIDATE_LEVEL),
                eq(linkSpeed),
                eq((int) wifiDisconnectTimeMs / 1000),
                eq((int) (wifiDisconnectTimeMs - connectionEndTimeMs) / 1000),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY),
                anyInt(), anyInt(), anyInt(), anyInt()));
    }

    @Test
    public void testWifiDisconnectAtomNotEmittedOnDisconnectFromNotConnectedSession() {
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, createComplexWifiConfig(),
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        long connectionEndTimeMs = 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(connectionEndTimeMs);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                WifiMetricsProto.ConnectionEvent.HLF_DHCP,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, TEST_CANDIDATE_FREQ,
                TEST_CONNECTION_FAILURE_STATUS_CODE);


        long wifiDisconnectTimeMs = 2000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(wifiDisconnectTimeMs);
        int linkSpeed = 100;
        int reason = 42;
        mWifiMetrics.reportNetworkDisconnect(TEST_IFACE_NAME, reason, TEST_CANDIDATE_LEVEL,
                linkSpeed, 0);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_DISCONNECT_REPORTED),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()),
                times(0));
    }

    @Test
    public void testWifiDisconnectAtomNotEmittedWithNoSession() {
        mWifiMetrics.reportNetworkDisconnect(TEST_IFACE_NAME, 0, TEST_CANDIDATE_LEVEL, 0, 0);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_DISCONNECT_REPORTED),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()),
                times(0));
    }

    @Test
    public void testWifiStateChangedAtomEmittedOnSuccessfulConnectAndDisconnect() {
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, createComplexWifiConfig(),
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_NONE, TEST_CANDIDATE_FREQ,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        // TRUE must be emitted
        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_CONNECTION_STATE_CHANGED,
                true,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__BAND__BAND_2G,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__AUTH_TYPE__AUTH_TYPE_WPA2_PSK));

        int linkSpeed = 100;
        int reason = 42;
        mWifiMetrics.reportNetworkDisconnect(TEST_IFACE_NAME, reason, TEST_CANDIDATE_LEVEL,
                linkSpeed, 0);

        // FALSE must be emitted
        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_CONNECTION_STATE_CHANGED,
                false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__BAND__BAND_2G,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__AUTH_TYPE__AUTH_TYPE_WPA2_PSK));
    }

    @Test
    public void testWifiStateChangedAtomNotEmittedOnNotSuccessfulConnectAndDisconnect() {
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, createComplexWifiConfig(),
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                WifiMetricsProto.ConnectionEvent.HLF_DHCP,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, TEST_CANDIDATE_FREQ,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        // TRUE must not be emitted
        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_CONNECTION_STATE_CHANGED),
                anyBoolean(), anyInt(), anyInt()),
                times(0));

        int linkSpeed = 100;
        int reason = 42;
        mWifiMetrics.reportNetworkDisconnect(TEST_IFACE_NAME, reason, TEST_CANDIDATE_LEVEL,
                linkSpeed, 0);

        // FALSE should not be emitted since wifi was never connected
        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_CONNECTION_STATE_CHANGED),
                eq(false),
                anyInt(),
                anyInt()), times(0));
    }

    @Test
    public void testWifiConnectionResultTimeSinceLastConnectionCorrect() {
        when(mClock.getElapsedSinceBootMillis()).thenReturn((long) 10 * 1000);

        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, createComplexWifiConfig(),
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_NONE, TEST_CANDIDATE_FREQ,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED), anyBoolean(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__TRIGGER__AUTOCONNECT_BOOT),
                anyBoolean(), eq(10), anyBoolean(), anyBoolean(), anyInt(),
                eq(TEST_CONNECTION_FAILURE_STATUS_CODE), anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), eq(TEST_UID)));

        mWifiMetrics.reportNetworkDisconnect(TEST_IFACE_NAME, 0, 0, 0, 0);

        when(mClock.getElapsedSinceBootMillis()).thenReturn((long) 30 * 1000);

        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, createComplexWifiConfig(),
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);

        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_NONE, TEST_CANDIDATE_FREQ,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED), anyBoolean(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                eq(WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__TRIGGER__RECONNECT_SAME_NETWORK),
                anyBoolean(), eq(20), anyBoolean(), anyBoolean(), anyInt(),
                eq(TEST_CONNECTION_FAILURE_STATUS_CODE), anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), eq(TEST_UID)));

        mWifiMetrics.reportNetworkDisconnect(TEST_IFACE_NAME, 0, 0, 0, 0);
    }

    @Test
    public void testWifiScanEmittedOnSuccess() {
        WifiMetrics.ScanMetrics scanMetrics = mWifiMetrics.getScanMetrics();

        scanMetrics.setImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
        scanMetrics.logScanStarted(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE);
        scanMetrics.logScanSucceeded(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE, 4);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_SCAN_REPORTED,
                WifiStatsLog.WIFI_SCAN_REPORTED__TYPE__TYPE_SINGLE,
                WifiStatsLog.WIFI_SCAN_REPORTED__RESULT__RESULT_SUCCESS,
                WifiStatsLog.WIFI_SCAN_REPORTED__SOURCE__SOURCE_NO_WORK_SOURCE,
                WifiStatsLog.WIFI_SCAN_REPORTED__IMPORTANCE__IMPORTANCE_FOREGROUND,
                0, 4));
    }

    @Test
    public void testWifiScanEmittedOnFailedToStart() {
        WifiMetrics.ScanMetrics scanMetrics = mWifiMetrics.getScanMetrics();

        scanMetrics.logScanFailedToStart(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_SCAN_REPORTED,
                WifiStatsLog.WIFI_SCAN_REPORTED__TYPE__TYPE_SINGLE,
                WifiStatsLog.WIFI_SCAN_REPORTED__RESULT__RESULT_FAILED_TO_START,
                WifiStatsLog.WIFI_SCAN_REPORTED__SOURCE__SOURCE_NO_WORK_SOURCE,
                WifiStatsLog.WIFI_SCAN_REPORTED__IMPORTANCE__IMPORTANCE_UNKNOWN,
                0, 0));
    }

    @Test
    public void testWifiScanEmittedOnFailure() {
        WifiMetrics.ScanMetrics scanMetrics = mWifiMetrics.getScanMetrics();

        scanMetrics.logScanStarted(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE);
        scanMetrics.logScanFailed(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_SCAN_REPORTED,
                WifiStatsLog.WIFI_SCAN_REPORTED__TYPE__TYPE_SINGLE,
                WifiStatsLog.WIFI_SCAN_REPORTED__RESULT__RESULT_FAILED_TO_SCAN,
                WifiStatsLog.WIFI_SCAN_REPORTED__SOURCE__SOURCE_NO_WORK_SOURCE,
                WifiStatsLog.WIFI_SCAN_REPORTED__IMPORTANCE__IMPORTANCE_UNKNOWN,
                0, 0));
    }

    @Test
    public void testWifiScanNotEmittedWithNoStart() {
        WifiMetrics.ScanMetrics scanMetrics = mWifiMetrics.getScanMetrics();

        scanMetrics.logScanSucceeded(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE, 4);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_SCAN_REPORTED),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()), times(0));
    }

    @Test
    public void testWifiScanEmittedOnlyOnce() {
        WifiMetrics.ScanMetrics scanMetrics = mWifiMetrics.getScanMetrics();

        scanMetrics.logScanStarted(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE);
        scanMetrics.logScanSucceeded(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE, 4);
        scanMetrics.logScanSucceeded(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE, 5);
        scanMetrics.logScanSucceeded(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE, 6);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                eq(WifiStatsLog.WIFI_SCAN_REPORTED),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), eq(4)), times(1));
    }

    @Test
    public void testWifiScanStatePreservedAfterStart() {
        WifiMetrics.ScanMetrics scanMetrics = mWifiMetrics.getScanMetrics();

        scanMetrics.setImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
        scanMetrics.logScanStarted(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE);
        scanMetrics.setImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE);
        scanMetrics.logScanSucceeded(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE, 4);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_SCAN_REPORTED,
                WifiStatsLog.WIFI_SCAN_REPORTED__TYPE__TYPE_SINGLE,
                WifiStatsLog.WIFI_SCAN_REPORTED__RESULT__RESULT_SUCCESS,
                WifiStatsLog.WIFI_SCAN_REPORTED__SOURCE__SOURCE_NO_WORK_SOURCE,
                WifiStatsLog.WIFI_SCAN_REPORTED__IMPORTANCE__IMPORTANCE_FOREGROUND,
                0, 4));
    }

    @Test
    public void testWifiScanOverlappingRequestsOverwriteStateForSameType() {
        WifiMetrics.ScanMetrics scanMetrics = mWifiMetrics.getScanMetrics();

        scanMetrics.setImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
        scanMetrics.logScanStarted(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE);

        scanMetrics.setImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE);
        scanMetrics.logScanStarted(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE);

        scanMetrics.logScanSucceeded(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE, 42);
        scanMetrics.logScanSucceeded(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE, 21);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_SCAN_REPORTED,
                WifiStatsLog.WIFI_SCAN_REPORTED__TYPE__TYPE_SINGLE,
                WifiStatsLog.WIFI_SCAN_REPORTED__RESULT__RESULT_SUCCESS,
                WifiStatsLog.WIFI_SCAN_REPORTED__SOURCE__SOURCE_NO_WORK_SOURCE,
                WifiStatsLog.WIFI_SCAN_REPORTED__IMPORTANCE__IMPORTANCE_BACKGROUND,
                0, 42));
    }

    @Test
    public void testWifiScanOverlappingRequestsSeparateStatesForDifferentTypes() {
        WifiMetrics.ScanMetrics scanMetrics = mWifiMetrics.getScanMetrics();

        scanMetrics.setImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
        scanMetrics.logScanStarted(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE);

        scanMetrics.setImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE);
        scanMetrics.logScanStarted(WifiMetrics.ScanMetrics.SCAN_TYPE_BACKGROUND);

        scanMetrics.logScanSucceeded(WifiMetrics.ScanMetrics.SCAN_TYPE_SINGLE, 42);
        scanMetrics.logScanSucceeded(WifiMetrics.ScanMetrics.SCAN_TYPE_BACKGROUND, 21);

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_SCAN_REPORTED,
                WifiStatsLog.WIFI_SCAN_REPORTED__TYPE__TYPE_SINGLE,
                WifiStatsLog.WIFI_SCAN_REPORTED__RESULT__RESULT_SUCCESS,
                WifiStatsLog.WIFI_SCAN_REPORTED__SOURCE__SOURCE_NO_WORK_SOURCE,
                WifiStatsLog.WIFI_SCAN_REPORTED__IMPORTANCE__IMPORTANCE_FOREGROUND,
                0, 42));

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_SCAN_REPORTED,
                WifiStatsLog.WIFI_SCAN_REPORTED__TYPE__TYPE_BACKGROUND,
                WifiStatsLog.WIFI_SCAN_REPORTED__RESULT__RESULT_SUCCESS,
                WifiStatsLog.WIFI_SCAN_REPORTED__SOURCE__SOURCE_NO_WORK_SOURCE,
                WifiStatsLog.WIFI_SCAN_REPORTED__IMPORTANCE__IMPORTANCE_BACKGROUND,
                0, 21));
    }

    private void setScreenState(boolean screenOn) {
        WifiDeviceStateChangeManager.StateChangeCallback callback =
                mStateChangeCallbackArgumentCaptor.getValue();
        assertNotNull(callback);
        callback.onScreenStateChanged(screenOn);
    }

    @Test
    public void testWifiToWifiSwitchMetrics() throws Exception {
        // initially all 0
        dumpProtoAndDeserialize();

        assertFalse(mDecodedProto.wifiToWifiSwitchStats.isMakeBeforeBreakSupported);
        assertEquals(0, mDecodedProto.wifiToWifiSwitchStats.wifiToWifiSwitchTriggerCount);
        assertEquals(0, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakTriggerCount);
        assertEquals(0, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakNoInternetCount);
        assertEquals(0, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakRecoverPrimaryCount);
        assertEquals(0, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakInternetValidatedCount);
        assertEquals(0, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakSuccessCount);
        assertEquals(0, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakLingerCompletedCount);
        assertEquals(0,
                mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakLingerDurationSeconds.length);

        // increment everything
        mWifiMetrics.setIsMakeBeforeBreakSupported(true);
        mWifiMetrics.incrementWifiToWifiSwitchTriggerCount();
        mWifiMetrics.incrementMakeBeforeBreakTriggerCount();
        mWifiMetrics.incrementMakeBeforeBreakNoInternetCount();
        mWifiMetrics.incrementMakeBeforeBreakRecoverPrimaryCount();
        mWifiMetrics.incrementMakeBeforeBreakInternetValidatedCount();
        mWifiMetrics.incrementMakeBeforeBreakSuccessCount();
        mWifiMetrics.incrementMakeBeforeBreakLingerCompletedCount(1000);

        dumpProtoAndDeserialize();

        // should be all 1
        assertTrue(mDecodedProto.wifiToWifiSwitchStats.isMakeBeforeBreakSupported);
        assertEquals(1, mDecodedProto.wifiToWifiSwitchStats.wifiToWifiSwitchTriggerCount);
        assertEquals(1, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakTriggerCount);
        assertEquals(1, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakNoInternetCount);
        assertEquals(1, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakRecoverPrimaryCount);
        assertEquals(1, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakInternetValidatedCount);
        assertEquals(1, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakSuccessCount);
        assertEquals(1, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakLingerCompletedCount);
        assertEquals(1,
                mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakLingerDurationSeconds.length);
        assertEquals(1,
                mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakLingerDurationSeconds[0].key);
        assertEquals(1,
                mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakLingerDurationSeconds[0].count);

        // dump again
        dumpProtoAndDeserialize();

        // everything should be reset
        assertFalse(mDecodedProto.wifiToWifiSwitchStats.isMakeBeforeBreakSupported);
        assertEquals(0, mDecodedProto.wifiToWifiSwitchStats.wifiToWifiSwitchTriggerCount);
        assertEquals(0, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakTriggerCount);
        assertEquals(0, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakNoInternetCount);
        assertEquals(0, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakRecoverPrimaryCount);
        assertEquals(0, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakInternetValidatedCount);
        assertEquals(0, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakSuccessCount);
        assertEquals(0, mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakLingerCompletedCount);
        assertEquals(0,
                mDecodedProto.wifiToWifiSwitchStats.makeBeforeBreakLingerDurationSeconds.length);
    }

    @Test
    public void testPasspointConnectionMetrics() throws Exception {
        // initially all 0
        dumpProtoAndDeserialize();

        assertEquals(0, mDecodedProto.totalNumberOfPasspointConnectionsWithVenueUrl);
        assertEquals(0, mDecodedProto.totalNumberOfPasspointConnectionsWithTermsAndConditionsUrl);
        assertEquals(0, mDecodedProto.totalNumberOfPasspointAcceptanceOfTermsAndConditions);
        assertEquals(0, mDecodedProto.totalNumberOfPasspointProfilesWithDecoratedIdentity);
        assertEquals(0, mDecodedProto.passpointDeauthImminentScope.length);

        // increment everything
        mWifiMetrics.incrementTotalNumberOfPasspointConnectionsWithVenueUrl();
        mWifiMetrics.incrementTotalNumberOfPasspointConnectionsWithTermsAndConditionsUrl();
        mWifiMetrics.incrementTotalNumberOfPasspointAcceptanceOfTermsAndConditions();
        mWifiMetrics.incrementTotalNumberOfPasspointProfilesWithDecoratedIdentity();
        mWifiMetrics.incrementPasspointDeauthImminentScope(true);
        mWifiMetrics.incrementPasspointDeauthImminentScope(false);
        mWifiMetrics.incrementPasspointDeauthImminentScope(false);

        dumpProtoAndDeserialize();

        Int32Count[] expectedDeauthImminentScope = {
                buildInt32Count(WifiMetrics.PASSPOINT_DEAUTH_IMMINENT_SCOPE_ESS, 1),
                buildInt32Count(WifiMetrics.PASSPOINT_DEAUTH_IMMINENT_SCOPE_BSS, 2),
        };

        assertEquals(1, mDecodedProto.totalNumberOfPasspointConnectionsWithVenueUrl);
        assertEquals(1, mDecodedProto.totalNumberOfPasspointConnectionsWithTermsAndConditionsUrl);
        assertEquals(1, mDecodedProto.totalNumberOfPasspointAcceptanceOfTermsAndConditions);
        assertEquals(1, mDecodedProto.totalNumberOfPasspointProfilesWithDecoratedIdentity);
        assertKeyCountsEqual(expectedDeauthImminentScope,
                mDecodedProto.passpointDeauthImminentScope);

        // dump again
        dumpProtoAndDeserialize();

        // everything should be reset
        assertEquals(0, mDecodedProto.totalNumberOfPasspointConnectionsWithVenueUrl);
        assertEquals(0, mDecodedProto.totalNumberOfPasspointConnectionsWithTermsAndConditionsUrl);
        assertEquals(0, mDecodedProto.totalNumberOfPasspointAcceptanceOfTermsAndConditions);
        assertEquals(0, mDecodedProto.totalNumberOfPasspointProfilesWithDecoratedIdentity);
        assertEquals(0, mDecodedProto.passpointDeauthImminentScope.length);
    }

    @Test
    public void testWifiStatsHealthStatWrite() throws Exception {
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.getFrequency()).thenReturn(5810);
        mWifiMetrics.incrementWifiScoreCount("",  60);
        mWifiMetrics.handlePollResult(TEST_IFACE_NAME, wifiInfo);
        mWifiMetrics.incrementConnectionDuration(TEST_IFACE_NAME, 3000, true, true, -50, 10002,
                10001);
        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_HEALTH_STAT_REPORTED, 3000, true, true,
                WifiStatsLog.WIFI_HEALTH_STAT_REPORTED__BAND__BAND_5G_HIGH, -50, 10002, 10001,
                Process.WIFI_UID,
                false,
                WIFI_IS_UNUSABLE_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_UNKNOWN));

        when(wifiInfo.getFrequency()).thenReturn(2412);
        mWifiMetrics.setIsExternalWifiScorerOn(true, TEST_UID);
        mWifiMetrics.setScorerPredictedWifiUsabilityState(TEST_IFACE_NAME,
                WifiMetrics.WifiUsabilityState.USABLE);
        mWifiMetrics.incrementWifiScoreCount("",  30);
        mWifiMetrics.handlePollResult(TEST_IFACE_NAME, wifiInfo);
        mWifiMetrics.incrementConnectionDuration(TEST_IFACE_NAME, 2000, false, true, -55, 20002,
                20001);
        ExtendedMockito.verify(
                () -> WifiStatsLog.write(WifiStatsLog.WIFI_HEALTH_STAT_REPORTED, 2000, true, true,
                        WifiStatsLog.WIFI_HEALTH_STAT_REPORTED__BAND__BAND_2G, -55, 20002, 20001,
                        TEST_UID, true,
                        WIFI_IS_UNUSABLE_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_USABLE));
    }

    /**
     * Test number of times connection failure status reported per
     * WifiConfiguration.RecentFailureReason
     */
    @Test
    public void testRecentFailureAssociationStatusCount() throws Exception {
        mWifiMetrics.incrementRecentFailureAssociationStatusCount(
                WifiConfiguration.RECENT_FAILURE_AP_UNABLE_TO_HANDLE_NEW_STA);
        mWifiMetrics.incrementRecentFailureAssociationStatusCount(
                WifiConfiguration.RECENT_FAILURE_AP_UNABLE_TO_HANDLE_NEW_STA);
        mWifiMetrics.incrementRecentFailureAssociationStatusCount(
                WifiConfiguration.RECENT_FAILURE_OCE_RSSI_BASED_ASSOCIATION_REJECTION);
        mWifiMetrics.incrementRecentFailureAssociationStatusCount(
                WifiConfiguration.RECENT_FAILURE_MBO_ASSOC_DISALLOWED_AIR_INTERFACE_OVERLOADED);
        mWifiMetrics.incrementRecentFailureAssociationStatusCount(
                WifiConfiguration.RECENT_FAILURE_MBO_ASSOC_DISALLOWED_AIR_INTERFACE_OVERLOADED);

        dumpProtoAndDeserialize();

        Int32Count[] expectedRecentFailureAssociationStatus = {
                buildInt32Count(WifiConfiguration.RECENT_FAILURE_AP_UNABLE_TO_HANDLE_NEW_STA,
                        2),
                buildInt32Count(
                        WifiConfiguration
                                .RECENT_FAILURE_MBO_ASSOC_DISALLOWED_AIR_INTERFACE_OVERLOADED, 2),
                buildInt32Count(
                        WifiConfiguration.RECENT_FAILURE_OCE_RSSI_BASED_ASSOCIATION_REJECTION, 1),
        };

        assertKeyCountsEqual(expectedRecentFailureAssociationStatus,
                mDecodedProto.recentFailureAssociationStatus);

    }

    private void testConnectionNetworkTypeByCandidateSecurityParams(
            int candidateSecurityType, int expectedType) throws Exception {
        WifiConfiguration config = null;
        switch (candidateSecurityType) {
            case WifiConfiguration.SECURITY_TYPE_OPEN:
            case WifiConfiguration.SECURITY_TYPE_OWE:
                config = WifiConfigurationTestUtil.createOpenOweNetwork();
                break;
            case WifiConfiguration.SECURITY_TYPE_PSK:
            case WifiConfiguration.SECURITY_TYPE_SAE:
                config = WifiConfigurationTestUtil.createPskSaeNetwork();
                break;
            case WifiConfiguration.SECURITY_TYPE_EAP:
            case WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE:
                config = WifiConfigurationTestUtil.createWpa2Wpa3EnterpriseNetwork();
                break;
            case WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT:
                config = WifiConfigurationTestUtil.createEapSuiteBNetwork();
                break;
            case WifiConfiguration.SECURITY_TYPE_WAPI_PSK:
                config = WifiConfigurationTestUtil.createWapiPskNetwork();
                break;
            case WifiConfiguration.SECURITY_TYPE_WAPI_CERT:
                config = WifiConfigurationTestUtil.createWapiCertNetwork();
                break;
        }
        assertNotNull(config);
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(candidateSecurityType));

        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config,
                "RED", WifiMetricsProto.ConnectionEvent.ROAM_NONE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);
        dumpProtoAndDeserialize();

        assertEquals(1, mDecodedProto.connectionEvent.length);
        assertEquals(expectedType,
                mDecodedProto.connectionEvent[0].networkType);
    }

    @Test
    public void testConnectionNetworkTypeOpenByCandidateSecurityParams() throws Exception {
        testConnectionNetworkTypeByCandidateSecurityParams(
                WifiConfiguration.SECURITY_TYPE_OPEN,
                WifiMetricsProto.ConnectionEvent.TYPE_OPEN);
    }

    @Test
    public void testConnectionNetworkTypePskByCandidateSecurityParams() throws Exception {
        testConnectionNetworkTypeByCandidateSecurityParams(
                WifiConfiguration.SECURITY_TYPE_PSK,
                WifiMetricsProto.ConnectionEvent.TYPE_WPA2);
    }

    @Test
    public void testConnectionNetworkTypeEapByCandidateSecurityParams() throws Exception {
        testConnectionNetworkTypeByCandidateSecurityParams(
                WifiConfiguration.SECURITY_TYPE_EAP,
                WifiMetricsProto.ConnectionEvent.TYPE_EAP);
    }

    @Test
    public void testConnectionNetworkTypeSaeByCandidateSecurityParams() throws Exception {
        testConnectionNetworkTypeByCandidateSecurityParams(
                WifiConfiguration.SECURITY_TYPE_SAE,
                WifiMetricsProto.ConnectionEvent.TYPE_WPA3);
    }

    @Test
    public void testConnectionNetworkTypeSuitBByCandidateSecurityParams() throws Exception {
        testConnectionNetworkTypeByCandidateSecurityParams(
                WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT,
                WifiMetricsProto.ConnectionEvent.TYPE_EAP);
    }

    @Test
    public void testConnectionNetworkTypeOweByCandidateSecurityParams() throws Exception {
        testConnectionNetworkTypeByCandidateSecurityParams(
                WifiConfiguration.SECURITY_TYPE_OWE,
                WifiMetricsProto.ConnectionEvent.TYPE_OWE);
    }

    @Test
    public void testConnectionNetworkTypeWapiPskByCandidateSecurityParams() throws Exception {
        testConnectionNetworkTypeByCandidateSecurityParams(
                WifiConfiguration.SECURITY_TYPE_WAPI_PSK,
                WifiMetricsProto.ConnectionEvent.TYPE_WAPI);
    }

    @Test
    public void testConnectionNetworkTypeWapiCertByCandidateSecurityParams() throws Exception {
        testConnectionNetworkTypeByCandidateSecurityParams(
                WifiConfiguration.SECURITY_TYPE_WAPI_CERT,
                WifiMetricsProto.ConnectionEvent.TYPE_WAPI);
    }

    @Test
    public void testConnectionNetworkTypeWpa3EntByCandidateSecurityParams() throws Exception {
        testConnectionNetworkTypeByCandidateSecurityParams(
                WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE,
                WifiMetricsProto.ConnectionEvent.TYPE_EAP);
    }

    @Test
    public void testWifiStateChanged() throws Exception {
        mWifiMetrics.reportWifiStateChanged(true, true, false);
        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_STATE_CHANGED, true, true, false));
    }

    @Test
    public void testReportAirplaneModeSession() throws Exception {
        mWifiMetrics.reportAirplaneModeSession(true, true, false, true, false, false);
        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.AIRPLANE_MODE_SESSION_REPORTED,
                WifiStatsLog.AIRPLANE_MODE_SESSION_REPORTED__PACKAGE_NAME__WIFI,
                true, true, false, true, false, false, false));
    }

    @Test
    public void testWifiConfigStored() {
        mWifiMetrics.wifiConfigStored(120);
        ExtendedMockito.verify(() -> WifiStatsLog.write(WIFI_CONFIG_SAVED, 120));
    }

    @Test
    public void testApCapabilitiesReported() throws Exception {
        //Setup mock configs and scan details
        NetworkDetail networkDetail = mock(NetworkDetail.class);
        when(networkDetail.getWifiMode()).thenReturn(NETWORK_DETAIL_WIFIMODE);
        when(networkDetail.getSSID()).thenReturn(SSID);
        when(networkDetail.getDtimInterval()).thenReturn(NETWORK_DETAIL_DTIM);

        ScanResult scanResult = mock(ScanResult.class);
        scanResult.level = SCAN_RESULT_LEVEL;
        scanResult.capabilities = "EAP/SHA1";
        scanResult.frequency = TEST_CANDIDATE_FREQ;

        WifiConfiguration config = mock(WifiConfiguration.class);
        config.SSID = "\"" + SSID + "\"";
        config.dtimInterval = CONFIG_DTIM;
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        config.allowedKeyManagement = new BitSet();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X);
        config.enterpriseConfig = new WifiEnterpriseConfig();
        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TTLS);
        config.enterpriseConfig.setPhase2Method(WifiEnterpriseConfig.Phase2.MSCHAPV2);
        config.enterpriseConfig.setOcsp(WifiEnterpriseConfig.OCSP_REQUIRE_CERT_STATUS);
        config.hiddenSSID = true;

        WifiConfiguration.NetworkSelectionStatus networkSelectionStat =
                mock(WifiConfiguration.NetworkSelectionStatus.class);
        when(networkSelectionStat.getCandidate()).thenReturn(scanResult);
        when(config.getNetworkSelectionStatus()).thenReturn(networkSelectionStat);

        ScanDetail scanDetail = mock(ScanDetail.class);
        when(scanDetail.getNetworkDetail()).thenReturn(networkDetail);
        when(scanDetail.getScanResult()).thenReturn(scanResult);
        when(networkDetail.isMboSupported()).thenReturn(true);
        when(networkDetail.isOceSupported()).thenReturn(true);
        when(networkDetail.getApType6GHz()).thenReturn(
                InformationElementUtil.ApType6GHz.AP_TYPE_6GHZ_STANDARD_POWER);
        when(networkDetail.isBroadcastTwtSupported()).thenReturn(true);
        when(networkDetail.isRestrictedTwtSupported()).thenReturn(true);
        when(networkDetail.isIndividualTwtSupported()).thenReturn(true);
        when(networkDetail.isTwtRequired()).thenReturn(true);
        when(networkDetail.isFilsCapable()).thenReturn(true);
        when(networkDetail.is11azSupported()).thenReturn(true);
        when(networkDetail.is80211McResponderSupport()).thenReturn(true);
        when(networkDetail.isEpcsPriorityAccessSupported()).thenReturn(true);
        when(networkDetail.getHSRelease()).thenReturn(NetworkDetail.HSRelease.Unknown);
        when(networkDetail.isHiddenBeaconFrame()).thenReturn(false);
        when(networkDetail.getWifiMode()).thenReturn(InformationElementUtil.WifiMode.MODE_11BE);

        SecurityParams securityParams = mock(SecurityParams.class);
        when(config.getDefaultSecurityParams()).thenReturn(securityParams);
        when(securityParams.isEnterpriseSecurityType()).thenReturn(true);
        when(config.isPasspoint()).thenReturn(false);
        config.isHomeProviderNetwork = false;

        //Create a connection event using only the config
        mWifiMetrics.startConnectionEvent(TEST_IFACE_NAME, config,
                "Red", WifiMetricsProto.ConnectionEvent.ROAM_NONE, false,
                WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY, TEST_UID);
        mWifiMetrics.setConnectionScanDetail(TEST_IFACE_NAME, scanDetail);
        mWifiMetrics.logBugReport();
        mWifiMetrics.logStaEvent(TEST_IFACE_NAME, StaEvent.TYPE_CMD_START_ROAM,
                StaEvent.DISCONNECT_UNKNOWN, null);
        mWifiMetrics.endConnectionEvent(TEST_IFACE_NAME,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0,
                TEST_CONNECTION_FAILURE_STATUS_CODE);

        ExtendedMockito.verify(
                () -> WifiStatsLog.write(eq(WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED),
                        eq(true), // mIsFrameworkInitiatedRoaming
                        eq(TEST_CANDIDATE_FREQ),
                        eq(WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__BAND_MHZ__BAND_2G),
                        eq(NETWORK_DETAIL_DTIM),
                        eq(WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__CONNECTED_SECURITY_MODE__SECURITY_MODE_NONE),
                        eq(true), // hidden
                        eq(true), // mIsIncorrectlyConfiguredAsHidden
                        eq(WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__STANDARD__WIFI_STANDARD_11BE),
                        eq(false), // mIs11bSupported
                        eq(WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_TYPE__TYPE_EAP_TTLS),
                        eq(WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__EAP_INNER_METHOD__METHOD_MSCHAP_V2),
                        eq(WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__OCSP_TYPE__TYPE_OCSP_REQUIRE_CERT_STATUS),
                        eq(false), // pmkCacheEnabled
                        eq(true), // mIsMboSupported
                        eq(true), // mIsOceSupported
                        eq(true), // mIsFilsSupported
                        eq(true), // mIsTwtRequired
                        eq(true), // mIsIndividualTwtSupported
                        eq(true), // mIsBroadcastTwtSupported
                        eq(true), // mIsRestrictedTwtSupported
                        eq(true), // mIs11McSupported
                        eq(true), // mIs11AzSupported
                        eq(WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__PASSPOINT_RELEASE__PASSPOINT_RELEASE_UNKNOWN),
                        eq(false), // isPasspointHomeProvider
                        eq(WifiStatsLog.WIFI_AP_CAPABILITIES_REPORTED__AP_TYPE_6GHZ__AP_TYPE_6GHZ_STANDARD_POWER),
                        eq(true))); // mIsEcpsPriorityAccessSupported
    }
}
