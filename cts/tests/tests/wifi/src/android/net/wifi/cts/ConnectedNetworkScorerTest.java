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

package android.net.wifi.cts;

import static android.Manifest.permission.CONNECTIVITY_INTERNAL;
import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.Manifest.permission.READ_WIFI_CREDENTIAL;
import static android.Manifest.permission.WIFI_UPDATE_USABILITY_STATS_SCORE;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PAID;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE;
import static android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats;
import static android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_FAILURE;
import static android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_NO_PROBE;
import static android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_SUCCESS;
import static android.net.wifi.WifiUsabilityStatsEntry.PROBE_STATUS_UNKNOWN;
import static android.net.wifi.WifiUsabilityStatsEntry.RadioStats;
import static android.net.wifi.WifiUsabilityStatsEntry.RateStats;
import static android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE;
import static android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK;
import static android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI;
import static android.net.wifi.WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO;
import static android.os.Process.myUid;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.app.UiAutomation;
import android.content.Context;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.DhcpOption;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConnectedSessionInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiSsid;
import android.net.wifi.WifiUsabilityStatsEntry;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiDevice;
import android.telephony.TelephonyManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.PropertyUtil;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.wifi.flags.FeatureFlags;

import com.google.common.collect.Range;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tests for wifi connected network scorer interface and usability stats.
 */
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ConnectedNetworkScorerTest extends WifiJUnit4TestBase {
    private static Context sContext;
    private static WifiManager sWifiManager;
    private static ConnectivityManager sConnectivityManager;
    private static UiDevice sUiDevice;
    private static TestHelper sTestHelper;
    private static TelephonyManager sTelephonyManager;

    private static boolean sWasVerboseLoggingEnabled;

    private static final int WIFI_CONNECT_TIMEOUT_MILLIS = 30_000;
    private static final int TIMEOUT = 12_000;
    private static final int WAIT_DURATION = 5_000;

    private static boolean sShouldRunTest = false;
    private static boolean sWasScanThrottleEnabled;
    private static FeatureFlags sFeatureFlags;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @BeforeClass
    public static void setUpClass() throws Exception {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();

        if (!WifiFeature.isWifiSupported(sContext)) {
            return;
        }
        sShouldRunTest = true;

        sFeatureFlags = new com.android.wifi.flags.FeatureFlagsImpl();
        sWifiManager = sContext.getSystemService(WifiManager.class);
        assertThat(sWifiManager).isNotNull();
        // Location mode must be enabled, otherwise the connection info will be redacted.
        assertThat(Objects.requireNonNull(sContext.getSystemService(LocationManager.class))
                .isLocationEnabled()).isTrue();

        sConnectivityManager = sContext.getSystemService(ConnectivityManager.class);

        // turn on verbose logging for tests
        sWasVerboseLoggingEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.isVerboseLoggingEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setVerboseLoggingEnabled(true));
        sWasScanThrottleEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.isScanThrottleEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScanThrottleEnabled(false));

        // disable then enable Wifi to clean the user selected network
        ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.setWifiEnabled(false));
        PollingCheck.check("Wifi not disabled", TIMEOUT, () -> !sWifiManager.isWifiEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.setWifiEnabled(true));
        PollingCheck.check("Wifi not enabled", TIMEOUT, () -> sWifiManager.isWifiEnabled());

        // turn screen on
        sUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        sTestHelper = new TestHelper(sContext, sUiDevice);
        sTestHelper.turnScreenOn();

        // check we have >= 1 saved network
        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.getConfiguredNetworks());
        assertWithMessage("Need at least one saved network").that(savedNetworks).isNotEmpty();
        sTelephonyManager = sContext.getSystemService(TelephonyManager.class);
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(sShouldRunTest);
        // Clear any existing app state before each test.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.removeAppState(myUid(), sContext.getPackageName()));
        // ensure Wifi is connected
        ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.reconnect());
        PollingCheck.check(
                "Wifi not connected",
                WIFI_CONNECT_TIMEOUT_MILLIS,
                () -> sWifiManager.getConnectionInfo().getNetworkId() != -1);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (!sShouldRunTest) return;
        if (!sWifiManager.isWifiEnabled()) {
            ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.setWifiEnabled(true));
        }
        sTestHelper.turnScreenOff();
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setVerboseLoggingEnabled(sWasVerboseLoggingEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScanThrottleEnabled(sWasScanThrottleEnabled));
    }

    private static class TestUsabilityStatsListener implements
            WifiManager.OnWifiUsabilityStatsListener {
        private final CountDownLatch mCountDownLatch;
        public int seqNum;
        public boolean isSameBssidAndFre;
        public WifiUsabilityStatsEntry statsEntry;

        TestUsabilityStatsListener(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onWifiUsabilityStats(int seqNum, boolean isSameBssidAndFreq,
                WifiUsabilityStatsEntry statsEntry) {
            this.seqNum = seqNum;
            this.isSameBssidAndFre = isSameBssidAndFreq;
            this.statsEntry = statsEntry;
            mCountDownLatch.countDown();
        }
    }

    /**
     * Tests the {@link android.net.wifi.WifiUsabilityStatsEntry} retrieved from
     * {@link WifiManager.OnWifiUsabilityStatsListener}.
     */
    @Test
    public void testWifiUsabilityStatsEntry() throws Exception {
        // Usability stats collection only supported by vendor version Q and above.
        if (!PropertyUtil.isVendorApiLevelAtLeast(Build.VERSION_CODES.Q)) {
            return;
        }
        CountDownLatch countDownLatch = new CountDownLatch(1);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        TestUsabilityStatsListener usabilityStatsListener =
                new TestUsabilityStatsListener(countDownLatch);
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiManager.addOnWifiUsabilityStatsListener(mExecutor, usabilityStatsListener);
            // Wait for new usability stats (while connected & screen on this is triggered
            // by platform periodically).
            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();

            assertThat(usabilityStatsListener.statsEntry).isNotNull();
            WifiUsabilityStatsEntry statsEntry = usabilityStatsListener.statsEntry;

            assertThat(statsEntry.getTimeStampMillis()).isGreaterThan(0L);
            assertThat(statsEntry.getRssi()).isLessThan(0);
            assertThat(statsEntry.getLinkSpeedMbps()).isAtLeast(0);
            assertThat(statsEntry.getTotalTxSuccess()).isAtLeast(0L);
            assertThat(statsEntry.getTotalTxRetries()).isAtLeast(0L);
            assertThat(statsEntry.getTotalTxBad()).isAtLeast(0L);
            assertThat(statsEntry.getTotalRxSuccess()).isAtLeast(0L);
            if (sWifiManager.isEnhancedPowerReportingSupported()) {
                assertThat(statsEntry.getTotalRadioOnTimeMillis()).isAtLeast(0L);
                assertThat(statsEntry.getTotalRadioTxTimeMillis()).isAtLeast(0L);
                assertThat(statsEntry.getTotalRadioRxTimeMillis()).isAtLeast(0L);
                assertThat(statsEntry.getTotalScanTimeMillis()).isAtLeast(0L);
                assertThat(statsEntry.getTotalNanScanTimeMillis()).isAtLeast(0L);
                assertThat(statsEntry.getTotalBackgroundScanTimeMillis()).isAtLeast(0L);
                assertThat(statsEntry.getTotalRoamScanTimeMillis()).isAtLeast(0L);
                assertThat(statsEntry.getTotalPnoScanTimeMillis()).isAtLeast(0L);
                assertThat(statsEntry.getTotalHotspot2ScanTimeMillis()).isAtLeast(0L);
                assertThat(statsEntry.getTotalCcaBusyFreqTimeMillis()).isAtLeast(0L);
                assertThat(statsEntry.getTotalRadioOnTimeMillis()).isAtLeast(0L);
                assertThat(statsEntry.getTotalRadioOnFreqTimeMillis()).isAtLeast(0L);
                assertThat(statsEntry.getTotalBeaconRx()).isAtLeast(0L);
                assertThat(statsEntry.getProbeStatusSinceLastUpdate())
                        .isAnyOf(PROBE_STATUS_SUCCESS,
                                PROBE_STATUS_FAILURE,
                                PROBE_STATUS_NO_PROBE,
                                PROBE_STATUS_UNKNOWN);
                // -1 is default value for some of these fields if they're not available.
                assertThat(statsEntry.getProbeElapsedTimeSinceLastUpdateMillis()).isAtLeast(-1);
                assertThat(statsEntry.getProbeMcsRateSinceLastUpdate()).isAtLeast(-1);
                assertThat(statsEntry.getRxLinkSpeedMbps()).isAtLeast(-1);
                if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
                    try {
                        assertThat(statsEntry.getTimeSliceDutyCycleInPercent())
                                .isIn(Range.closed(0, 100));
                    } catch (NoSuchElementException e) {
                        // pass - Device does not support the field.
                    }
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_BE).getContentionTimeMinMicros()).isAtLeast(0);
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_BE).getContentionTimeMaxMicros()).isAtLeast(0);
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_BE).getContentionTimeAvgMicros()).isAtLeast(0);
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_BE).getContentionNumSamples()).isAtLeast(0);
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_BK).getContentionTimeMinMicros()).isAtLeast(0);
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_BK).getContentionTimeMaxMicros()).isAtLeast(0);
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_BK).getContentionTimeAvgMicros()).isAtLeast(0);
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_BK).getContentionNumSamples()).isAtLeast(0);
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_VI).getContentionTimeMinMicros()).isAtLeast(0);
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_VI).getContentionTimeMaxMicros()).isAtLeast(0);
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_VI).getContentionTimeAvgMicros()).isAtLeast(0);
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_VI).getContentionNumSamples()).isAtLeast(0);
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_VO).getContentionTimeMinMicros()).isAtLeast(0);
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_VO).getContentionTimeMaxMicros()).isAtLeast(0);
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_VO).getContentionTimeAvgMicros()).isAtLeast(0);
                    assertThat(statsEntry.getContentionTimeStats(
                            WME_ACCESS_CATEGORY_VO).getContentionNumSamples()).isAtLeast(0);
                    // This is to add CTS test for the constructor function.
                    ContentionTimeStats contentionStats = new ContentionTimeStats(2, 1, 4, 10);
                    assertEquals(2, contentionStats.getContentionTimeMinMicros());
                    assertEquals(1, contentionStats.getContentionTimeMaxMicros());
                    assertEquals(4, contentionStats.getContentionTimeAvgMicros());
                    assertEquals(10, contentionStats.getContentionNumSamples());
                    // Note that -1 is also a possible returned value for utilization ratio.
                    assertThat(statsEntry.getChannelUtilizationRatio()).isIn(Range.closed(-1, 255));
                    if (sTelephonyManager != null) {
                        boolean isCellularDataAvailable =
                                sTelephonyManager.getDataState() == TelephonyManager.DATA_CONNECTED;
                        assertEquals(isCellularDataAvailable, statsEntry.isCellularDataAvailable());
                    } else {
                        assertFalse(statsEntry.isCellularDataAvailable());
                    }
                    statsEntry.isWifiScoringEnabled();
                    statsEntry.isThroughputSufficient();
                    RateStats rateStats = new RateStats(WifiUsabilityStatsEntry.WIFI_PREAMBLE_VHT,
                            WifiUsabilityStatsEntry.WIFI_SPATIAL_STREAMS_TWO,
                            WifiUsabilityStatsEntry.WIFI_BANDWIDTH_40_MHZ,
                            2, 20, 100, 200, 5, 10);
                    assertThat(statsEntry.getRateStats()).isNotNull();
                    if(statsEntry.getRateStats().size() > 0) {
                        assertThat(statsEntry.getRateStats().get(0).getPreamble()).isAtLeast(0);
                        assertThat(statsEntry.getRateStats().get(0).getNumberOfSpatialStreams())
                                .isAtLeast(1);
                        assertThat(statsEntry.getRateStats().get(0).getBandwidthInMhz())
                                .isAtLeast(0);
                        assertThat(statsEntry.getRateStats().get(0).getRateMcsIdx()).isAtLeast(0);
                        assertThat(statsEntry.getRateStats().get(0).getBitRateInKbps())
                                .isAtLeast(0);
                        assertThat(statsEntry.getRateStats().get(0).getTxMpdu()).isAtLeast(0);
                        assertThat(statsEntry.getRateStats().get(0).getRxMpdu()).isAtLeast(0);
                        assertThat(statsEntry.getRateStats().get(0).getMpduLost()).isAtLeast(0);
                        assertThat(statsEntry.getRateStats().get(0).getRetries()).isAtLeast(0);
                    }
                    RadioStats radioStat = new RadioStats(0, 10, 11, 12, 13, 14, 15, 16, 17, 18);
                    assertThat(statsEntry.getWifiLinkLayerRadioStats()).isNotNull();
                    int numRadios = statsEntry.getWifiLinkLayerRadioStats().size();
                    for (int i = 0; i < numRadios; i++) {
                        RadioStats radioStats = statsEntry.getWifiLinkLayerRadioStats().get(i);
                        assertThat(radioStats.getRadioId()).isAtLeast(0);
                        assertThat(radioStats.getTotalRadioOnTimeMillis()).isAtLeast(0);
                        assertThat(radioStats.getTotalRadioTxTimeMillis()).isAtLeast(0);
                        assertThat(radioStats.getTotalRadioRxTimeMillis()).isAtLeast(0);
                        assertThat(radioStats.getTotalScanTimeMillis()).isAtLeast(0);
                        assertThat(radioStats.getTotalNanScanTimeMillis()).isAtLeast(0);
                        assertThat(radioStats.getTotalBackgroundScanTimeMillis()).isAtLeast(0);
                        assertThat(radioStats.getTotalRoamScanTimeMillis()).isAtLeast(0);
                        assertThat(radioStats.getTotalPnoScanTimeMillis()).isAtLeast(0);
                        assertThat(radioStats.getTotalHotspot2ScanTimeMillis()).isAtLeast(0);
                    }
                    int[] links = statsEntry.getLinkIds();
                    if (links != null) {
                        for (int link : links) {
                            assertThat(statsEntry.getLinkState(link)).isIn(Range.closed(
                                    WifiUsabilityStatsEntry.LINK_STATE_UNKNOWN,
                                    WifiUsabilityStatsEntry.LINK_STATE_IN_USE));
                            assertThat(statsEntry.getRssi(link)).isLessThan(0);
                            assertThat(statsEntry.getRadioId(link)).isAtLeast(0);
                            assertThat(statsEntry.getTxLinkSpeedMbps(link)).isAtLeast(0);
                            // -1 is a default value for rx link speed if it is not available.
                            assertThat(statsEntry.getRxLinkSpeedMbps(link)).isAtLeast(-1);
                            assertThat(statsEntry.getTotalTxSuccess(link)).isAtLeast(0L);
                            assertThat(statsEntry.getTotalTxRetries(link)).isAtLeast(0L);
                            assertThat(statsEntry.getTotalTxBad(link)).isAtLeast(0L);
                            assertThat(statsEntry.getTotalRxSuccess(link)).isAtLeast(0L);
                            assertThat(statsEntry.getTotalBeaconRx(link)).isAtLeast(0L);
                            try {
                                assertThat(statsEntry.getTimeSliceDutyCycleInPercent(link))
                                        .isIn(Range.closed(0, 100));
                            } catch (NoSuchElementException e) {
                                // pass - Device does not support the field.
                            }
                            if (sFeatureFlags.androidVWifiApi()) {
                                assertThat(
                                        statsEntry.getTotalCcaBusyFreqTimeMillis(link)).isAtLeast(
                                        0L);
                                assertThat(
                                        statsEntry.getTotalRadioOnFreqTimeMillis(link)).isAtLeast(
                                        0L);
                            }
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_BE).getContentionTimeMinMicros()).isAtLeast(
                                    0);
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_BE).getContentionTimeMaxMicros()).isAtLeast(
                                    0);
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_BE).getContentionTimeAvgMicros()).isAtLeast(
                                    0);
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_BE).getContentionNumSamples()).isAtLeast(0);
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_BK).getContentionTimeMinMicros()).isAtLeast(
                                    0);
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_BK).getContentionTimeMaxMicros()).isAtLeast(
                                    0);
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_BK).getContentionTimeAvgMicros()).isAtLeast(
                                    0);
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_BK).getContentionNumSamples()).isAtLeast(0);
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_VI).getContentionTimeMinMicros()).isAtLeast(
                                    0);
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_VI).getContentionTimeMaxMicros()).isAtLeast(
                                    0);
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_VI).getContentionTimeAvgMicros()).isAtLeast(
                                    0);
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_VI).getContentionNumSamples()).isAtLeast(0);
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_VO).getContentionTimeMinMicros()).isAtLeast(
                                    0);
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_VO).getContentionTimeMaxMicros()).isAtLeast(
                                    0);
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_VO).getContentionTimeAvgMicros()).isAtLeast(
                                    0);
                            assertThat(statsEntry.getContentionTimeStats(link,
                                    WME_ACCESS_CATEGORY_VO).getContentionNumSamples()).isAtLeast(0);

                            assertThat(statsEntry.getRateStats(link)).isNotNull();
                            if (statsEntry.getRateStats(link).size() > 0) {
                                assertThat(statsEntry.getRateStats(link).get(
                                        0).getPreamble()).isAtLeast(
                                        0);
                                assertThat(statsEntry.getRateStats(link).get(
                                        0).getNumberOfSpatialStreams())
                                        .isAtLeast(1);
                                assertThat(statsEntry.getRateStats(link).get(0).getBandwidthInMhz())
                                        .isAtLeast(0);
                                assertThat(statsEntry.getRateStats(link).get(
                                        0).getRateMcsIdx()).isAtLeast(
                                        0);
                                assertThat(statsEntry.getRateStats(link).get(0).getBitRateInKbps())
                                        .isAtLeast(0);
                                assertThat(statsEntry.getRateStats(link).get(
                                        0).getTxMpdu()).isAtLeast(
                                        0);
                                assertThat(statsEntry.getRateStats(link).get(
                                        0).getRxMpdu()).isAtLeast(
                                        0);
                                assertThat(statsEntry.getRateStats(link).get(
                                        0).getMpduLost()).isAtLeast(
                                        0);
                                assertThat(statsEntry.getRateStats(link).get(
                                        0).getRetries()).isAtLeast(0);
                            }

                        }
                    }
                }
                // no longer populated, return default value.
                assertThat(statsEntry.getCellularDataNetworkType())
                        .isAnyOf(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                                TelephonyManager.NETWORK_TYPE_GPRS,
                                TelephonyManager.NETWORK_TYPE_EDGE,
                                TelephonyManager.NETWORK_TYPE_UMTS,
                                TelephonyManager.NETWORK_TYPE_CDMA,
                                TelephonyManager.NETWORK_TYPE_EVDO_0,
                                TelephonyManager.NETWORK_TYPE_EVDO_A,
                                TelephonyManager.NETWORK_TYPE_1xRTT,
                                TelephonyManager.NETWORK_TYPE_HSDPA,
                                TelephonyManager.NETWORK_TYPE_HSUPA,
                                TelephonyManager.NETWORK_TYPE_HSPA,
                                TelephonyManager.NETWORK_TYPE_IDEN,
                                TelephonyManager.NETWORK_TYPE_EVDO_B,
                                TelephonyManager.NETWORK_TYPE_LTE,
                                TelephonyManager.NETWORK_TYPE_EHRPD,
                                TelephonyManager.NETWORK_TYPE_HSPAP,
                                TelephonyManager.NETWORK_TYPE_GSM,
                                TelephonyManager.NETWORK_TYPE_TD_SCDMA,
                                TelephonyManager.NETWORK_TYPE_IWLAN,
                                TelephonyManager.NETWORK_TYPE_NR);
                assertThat(statsEntry.getCellularSignalStrengthDbm()).isAtMost(0);
                assertThat(statsEntry.getCellularSignalStrengthDb()).isAtMost(0);
                assertThat(statsEntry.isSameRegisteredCell()).isFalse();
            }
        } finally {
            sWifiManager.removeOnWifiUsabilityStatsListener(usabilityStatsListener);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests the {@link android.net.wifi.WifiManager#updateWifiUsabilityScore(int, int, int)}
     */
    @Test
    public void testUpdateWifiUsabilityScore() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // update scoring with dummy values.
            sWifiManager.updateWifiUsabilityScore(0, 50, 50);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests the {@link android.net.wifi.WifiManager#setWifiScoringEnabled(boolean)}
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testSetWifiScoringEnabled() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            assertTrue(sWifiManager.setWifiScoringEnabled(true));
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private abstract static class TestConnectedNetworkScorer implements
            WifiManager.WifiConnectedNetworkScorer {
        protected CountDownLatch mCountDownLatch;
        public Integer startSessionId;
        public Integer stopSessionId;
        public WifiManager.ScoreUpdateObserver scoreUpdateObserver;
        public boolean isUserSelected;

        TestConnectedNetworkScorer(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onStop(int sessionId) {
            synchronized (mCountDownLatch) {
                this.stopSessionId = sessionId;
                mCountDownLatch.countDown();
            }
        }

        @Override
        public void onSetScoreUpdateObserver(WifiManager.ScoreUpdateObserver observerImpl) {
            synchronized (mCountDownLatch) {
                this.scoreUpdateObserver = observerImpl;
            }
        }

        public void resetCountDownLatch(CountDownLatch countDownLatch) {
            synchronized (mCountDownLatch) {
                mCountDownLatch = countDownLatch;
            }
        }
    }

    private static class TestConnectedNetworkScorerWithSessionId extends
            TestConnectedNetworkScorer {
        TestConnectedNetworkScorerWithSessionId(CountDownLatch countDownLatch) {
            super(countDownLatch);
            isUserSelected = false;
        }

        @Override
        public void onStart(int sessionId) {
            super.onStart(sessionId);
            synchronized (mCountDownLatch) {
                this.startSessionId = sessionId;
                mCountDownLatch.countDown();
            }
        }
    }

    private static class TestConnectedNetworkScorerWithSessionInfo extends
            TestConnectedNetworkScorer {
        TestConnectedNetworkScorerWithSessionInfo(CountDownLatch countDownLatch) {
            super(countDownLatch);
            isUserSelected = false;
        }

        @Override
        public void onStart(WifiConnectedSessionInfo sessionInfo) {
            super.onStart(sessionInfo);
            synchronized (mCountDownLatch) {
                startSessionId = sessionInfo.getSessionId();
                isUserSelected = sessionInfo.isUserSelected();
                // Build a WifiConnectedSessionInfo object
                WifiConnectedSessionInfo.Builder sessionBuilder =
                        new WifiConnectedSessionInfo.Builder(startSessionId.intValue())
                                .setUserSelected(isUserSelected);
                sessionBuilder.build();
                mCountDownLatch.countDown();
            }
        }

        @Override
        public void onNetworkSwitchAccepted(
                int sessionId, int targetNetworkId, @NonNull String targetBssid) {
            super.onNetworkSwitchAccepted(sessionId, targetNetworkId, targetBssid);
            // Not possible to fake via CTS since it requires two networks and UI interaction.
        }

        @Override
        public void onNetworkSwitchRejected(
                int sessionId, int targetNetworkId, @NonNull String targetBssid) {
            super.onNetworkSwitchRejected(sessionId, targetNetworkId, targetBssid);
            // Not possible to fake via CTS since it requires two networks and UI interaction.
        }
    }

    /**
     * Tests the
     * {@link android.net.wifi.WifiConnectedNetworkScorer#onStart(WifiConnectedSessionInfo)}.
     */
    @Test
    public void testConnectedNetworkScorerWithSessionInfo() throws Exception {
        CountDownLatch countDownLatchScorer = new CountDownLatch(1);
        TestConnectedNetworkScorerWithSessionInfo connectedNetworkScorer =
                new TestConnectedNetworkScorerWithSessionInfo(countDownLatchScorer);
        testSetWifiConnectedNetworkScorer(connectedNetworkScorer, countDownLatchScorer);
    }

    /**
     * Tests the {@link android.net.wifi.WifiConnectedNetworkScorer#onStart(int)}.
     */
    @Test
    public void testConnectedNetworkScorerWithSessionId() throws Exception {
        CountDownLatch countDownLatchScorer = new CountDownLatch(1);
        TestConnectedNetworkScorerWithSessionId connectedNetworkScorer =
                new TestConnectedNetworkScorerWithSessionId(countDownLatchScorer);
        testSetWifiConnectedNetworkScorer(connectedNetworkScorer, countDownLatchScorer);
    }

    /**
     * Note: We could write more interesting test cases (if the device has a mobile connection), but
     * that would make the test flaky. The default network/route selection on the device is not just
     * controlled by the wifi scorer input, but also based on params which are controlled by
     * other parts of the platform (likely in connectivity service) and hence will behave
     * differently on OEM devices.
     */
    private void testSetWifiConnectedNetworkScorer(
            TestConnectedNetworkScorer connectedNetworkScorer,
                    CountDownLatch countDownLatchScorer) throws Exception {
        CountDownLatch countDownLatchUsabilityStats = new CountDownLatch(1);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        TestUsabilityStatsListener usabilityStatsListener =
                new TestUsabilityStatsListener(countDownLatchUsabilityStats);
        boolean disconnected = false;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // Clear any external scorer already active on the device.
            sWifiManager.clearWifiConnectedNetworkScorer();
            Thread.sleep(500);

            sWifiManager.setWifiConnectedNetworkScorer(mExecutor, connectedNetworkScorer);
            // Since we're already connected, wait for onStart to be invoked.
            assertThat(countDownLatchScorer.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();

            assertThat(connectedNetworkScorer.startSessionId).isAtLeast(0);
            assertThat(connectedNetworkScorer.isUserSelected).isEqualTo(false);
            assertThat(connectedNetworkScorer.scoreUpdateObserver).isNotNull();
            WifiManager.ScoreUpdateObserver scoreUpdateObserver =
                    connectedNetworkScorer.scoreUpdateObserver;

            // Now trigger a dummy score update.
            scoreUpdateObserver.notifyScoreUpdate(connectedNetworkScorer.startSessionId, 50);

            // Register the usability listener
            sWifiManager.addOnWifiUsabilityStatsListener(mExecutor, usabilityStatsListener);
            // Trigger a usability stats update.
            scoreUpdateObserver.triggerUpdateOfWifiUsabilityStats(
                    connectedNetworkScorer.startSessionId);
            // Ensure that we got the stats update callback.
            assertThat(countDownLatchUsabilityStats.await(TIMEOUT, TimeUnit.MILLISECONDS))
                    .isTrue();
            assertThat(usabilityStatsListener.seqNum).isAtLeast(0);

            // Reset the scorer countdown latch for onStop
            countDownLatchScorer = new CountDownLatch(1);
            connectedNetworkScorer.resetCountDownLatch(countDownLatchScorer);
            if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
                // Notify status change and request a NUD check
                scoreUpdateObserver.notifyStatusUpdate(
                        connectedNetworkScorer.startSessionId, false);
                scoreUpdateObserver.requestNudOperation(connectedNetworkScorer.startSessionId);
                // Blocklist current AP with invalid session Id
                scoreUpdateObserver.blocklistCurrentBssid(-1);
            }
            // Now disconnect from the network.
            sWifiManager.disconnect();
            // Wait for it to be disconnected.
            PollingCheck.check(
                    "Wifi not disconnected",
                    TIMEOUT,
                    () -> sWifiManager.getConnectionInfo().getNetworkId() == -1);
            disconnected = true;

            // Wait for stop to be invoked and ensure that the session id matches.
            assertThat(countDownLatchScorer.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(connectedNetworkScorer.stopSessionId)
                    .isEqualTo(connectedNetworkScorer.startSessionId);
            // Verify that onStart() and onStop() set internal variables correctly.
            connectedNetworkScorer.onStart(
                    new WifiConnectedSessionInfo.Builder(100)
                            .setUserSelected(false)
                            .build());
            assertEquals(100, connectedNetworkScorer.startSessionId.intValue());
            assertEquals(false, connectedNetworkScorer.isUserSelected);
            connectedNetworkScorer.onStop(200);
            assertEquals(200, connectedNetworkScorer.stopSessionId.intValue());
        } finally {
            sWifiManager.removeOnWifiUsabilityStatsListener(usabilityStatsListener);
            sWifiManager.clearWifiConnectedNetworkScorer();

            if (disconnected) {
                sWifiManager.reconnect();
                // Wait for it to be reconnected.
                PollingCheck.check(
                        "Wifi not reconnected",
                        WIFI_CONNECT_TIMEOUT_MILLIS,
                        () -> sWifiManager.getConnectionInfo().getNetworkId() != -1);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests the {@link android.net.wifi.WifiConnectedNetworkScorer} interface.
     *
     * Verifies that the external scorer works even after wifi restart.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testSetWifiConnectedNetworkScorerOnSubsystemRestart() throws Exception {
        CountDownLatch countDownLatchScorer = new CountDownLatch(1);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        TestConnectedNetworkScorerWithSessionInfo connectedNetworkScorer =
                new TestConnectedNetworkScorerWithSessionInfo(countDownLatchScorer);
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // Clear any external scorer already active on the device.
            sWifiManager.clearWifiConnectedNetworkScorer();
            Thread.sleep(500);

            sWifiManager.setWifiConnectedNetworkScorer(mExecutor, connectedNetworkScorer);
            // Since we're already connected, wait for onStart to be invoked.
            assertThat(countDownLatchScorer.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();

            int prevSessionId = connectedNetworkScorer.startSessionId;
            WifiManager.ScoreUpdateObserver prevScoreUpdateObserver =
                    connectedNetworkScorer.scoreUpdateObserver;

            // Expect one stop followed by one start after the restart

            // Ensure that we got an onStop() for the previous connection when restart is invoked.
            countDownLatchScorer = new CountDownLatch(1);
            connectedNetworkScorer.resetCountDownLatch(countDownLatchScorer);

            // Restart wifi subsystem.
            sWifiManager.restartWifiSubsystem();

            // wait for scorer to stop session due to network disconnection.
            assertThat(countDownLatchScorer.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(connectedNetworkScorer.stopSessionId).isEqualTo(prevSessionId);

            countDownLatchScorer = new CountDownLatch(1);
            connectedNetworkScorer.resetCountDownLatch(countDownLatchScorer);
            // Wait for the device to connect back.
            PollingCheck.check(
                    "Wifi not connected",
                    WIFI_CONNECT_TIMEOUT_MILLIS * 2,
                    () -> sWifiManager.getConnectionInfo().getNetworkId() != -1);

            // Followed by a new onStart() after the connection.
            // Note: There is a 5 second delay between stop/start when restartWifiSubsystem() is
            // invoked, so this should not be racy.
            assertThat(countDownLatchScorer.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(connectedNetworkScorer.startSessionId).isNotEqualTo(prevSessionId);

            // Ensure that we did not get a new score update observer.
            assertThat(connectedNetworkScorer.scoreUpdateObserver).isSameInstanceAs(
                    prevScoreUpdateObserver);
        } finally {
            sWifiManager.clearWifiConnectedNetworkScorer();
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private interface ConnectionInitiator {
        /**
         * Trigger connection (using suggestion or specifier) to the provided network.
         */
        ConnectivityManager.NetworkCallback initiateConnection(
                @NonNull WifiConfiguration testNetwork,
                @NonNull ScheduledExecutorService executorService) throws Exception;
    }

    private void setWifiConnectedNetworkScorerAndInitiateConnectToSpecifierOrRestrictedSuggestion(
            @NonNull ConnectionInitiator connectionInitiator) throws Exception {
        CountDownLatch countDownLatchScorer = new CountDownLatch(1);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        TestConnectedNetworkScorerWithSessionInfo connectedNetworkScorer =
                new TestConnectedNetworkScorerWithSessionInfo(countDownLatchScorer);
        ConnectivityManager.NetworkCallback networkCallback = null;
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        List<WifiConfiguration> savedNetworks = null;
        try {
            uiAutomation.adoptShellPermissionIdentity(
                    NETWORK_SETTINGS, WIFI_UPDATE_USABILITY_STATS_SCORE, CONNECTIVITY_INTERNAL,
                    READ_WIFI_CREDENTIAL);

            // Clear any external scorer already active on the device.
            sWifiManager.clearWifiConnectedNetworkScorer();
            Thread.sleep(500);

            savedNetworks = sWifiManager.getPrivilegedConfiguredNetworks();
            WifiConfiguration testNetwork =
                    TestHelper.findMatchingSavedNetworksWithBssid(sWifiManager, savedNetworks, 1)
                            .get(0);
            // Disconnect & disable auto-join on the saved network to prevent auto-connect from
            // interfering with the test.
            for (WifiConfiguration savedNetwork : savedNetworks) {
                sWifiManager.disableNetwork(savedNetwork.networkId);
            }
            // Wait for Wifi to be disconnected.
            PollingCheck.check(
                    "Wifi not disconnected",
                    20000,
                    () -> sWifiManager.getConnectionInfo().getNetworkId() == -1);
            assertThat(testNetwork).isNotNull();

            // Register the external scorer.
            sWifiManager.setWifiConnectedNetworkScorer(mExecutor, connectedNetworkScorer);

            // Now connect using the provided connection initiator
            networkCallback = connectionInitiator.initiateConnection(testNetwork, executorService);

            // We should not receive the start
            assertThat(countDownLatchScorer.await(WAIT_DURATION, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(connectedNetworkScorer.startSessionId).isNull();

            // Now disconnect from the network.
            sConnectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallback = null;

            // We should not receive the stop either
            countDownLatchScorer = new CountDownLatch(1);
            connectedNetworkScorer.resetCountDownLatch(countDownLatchScorer);
            assertThat(countDownLatchScorer.await(WAIT_DURATION, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(connectedNetworkScorer.stopSessionId).isNull();
        } finally {
            executorService.shutdownNow();
            sWifiManager.clearWifiConnectedNetworkScorer();
            if (networkCallback != null) {
                sConnectivityManager.unregisterNetworkCallback(networkCallback);
            }
            // Re-enable the networks after the test.
            if (savedNetworks != null) {
                for (WifiConfiguration savedNetwork : savedNetworks) {
                    sWifiManager.enableNetwork(savedNetwork.networkId, false);
                }
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }
    /**
     * Tests the {@link android.net.wifi.WifiConnectedNetworkScorer} interface.
     *
     * Verifies that the external scorer is not notified for local only connections.
     */
    @Test
    public void testSetWifiConnectedNetworkScorerForSpecifierConnection() throws Exception {
        setWifiConnectedNetworkScorerAndInitiateConnectToSpecifierOrRestrictedSuggestion(
                (testNetwork, executorService) -> {
                    // Connect using wifi network specifier.
                    WifiNetworkSpecifier specifier =
                            TestHelper.createSpecifierBuilderWithCredentialFromSavedNetwork(
                                    testNetwork, true)
                                    .build();
                    return sTestHelper.testConnectionFlowWithSpecifierWithShellIdentity(
                            testNetwork, specifier, false);
                }
        );
    }

    private void testSetWifiConnectedNetworkScorerForRestrictedSuggestionConnection(
            Set<Integer> restrictedNetworkCapabilities) throws Exception {
        setWifiConnectedNetworkScorerAndInitiateConnectToSpecifierOrRestrictedSuggestion(
                (testNetwork, executorService) -> {
                    // Connect using wifi network suggestion.
                    WifiNetworkSuggestion.Builder suggestionBuilder =
                            TestHelper
                                    .createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
                                    testNetwork);
                    if (restrictedNetworkCapabilities.contains(NET_CAPABILITY_OEM_PAID)) {
                        suggestionBuilder.setOemPaid(true);
                    }
                    if (restrictedNetworkCapabilities.contains(NET_CAPABILITY_OEM_PRIVATE)) {
                        suggestionBuilder.setOemPrivate(true);
                    }
                    return sTestHelper.testConnectionFlowWithSuggestionWithShellIdentity(
                            testNetwork, suggestionBuilder.build(), executorService,
                            restrictedNetworkCapabilities, false/* restrictedNetwork */);
                }
        );
    }

    /**
     * Tests the {@link android.net.wifi.WifiConnectedNetworkScorer} interface.
     *
     * Verifies that the external scorer is not notified for oem paid suggestion connections.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testSetWifiConnectedNetworkScorerForOemPaidSuggestionConnection() throws Exception {
        testSetWifiConnectedNetworkScorerForRestrictedSuggestionConnection(
                Set.of(NET_CAPABILITY_OEM_PAID));
    }

    /**
     * Tests the {@link android.net.wifi.WifiConnectedNetworkScorer} interface.
     *
     * Verifies that the external scorer is not notified for oem private suggestion connections.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testSetWifiConnectedNetworkScorerForOemPrivateSuggestionConnection()
            throws Exception {
        testSetWifiConnectedNetworkScorerForRestrictedSuggestionConnection(
                Set.of(NET_CAPABILITY_OEM_PRIVATE));
    }

    /**
     * Tests the
     * {@link android.net.wifi.WifiManager#addCustomDhcpOptions(Object, Object, List)} and
     * {@link android.net.wifi.WifiManager#removeCustomDhcpOptions(Object, Object)}.
     *
     * Verifies that these APIs can be invoked successfully with permissions.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testAddAndRemoveCustomDhcpOptions() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            WifiSsid ssid = WifiSsid.fromBytes(new byte[]{0x12, 0x34, 0x56});
            byte[] oui = new byte[]{0x00, 0x01, 0x02};
            List<DhcpOption> options = new ArrayList<DhcpOption>();
            sWifiManager.addCustomDhcpOptions(ssid, oui, options);
            sWifiManager.removeCustomDhcpOptions(ssid, oui);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests the
     * {@link android.net.wifi.WifiManager#addCustomDhcpOptions(Object, Object, List)}.
     *
     * Verifies that SecurityException is thrown when permissions are missing.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testAddCustomDhcpOptionsOnMissingPermissions() throws Exception {
        try {
            WifiSsid ssid = WifiSsid.fromBytes(new byte[]{0x12, 0x34, 0x56});
            byte[] oui = new byte[]{0x00, 0x01, 0x02};
            List<DhcpOption> options = new ArrayList<DhcpOption>();
            sWifiManager.addCustomDhcpOptions(ssid, oui, options);
            fail("Expected SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }
}
