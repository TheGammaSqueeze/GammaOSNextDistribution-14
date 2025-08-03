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

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Tests location sensitive APIs exposed by Wi-Fi.
 * Ensures that permissions on these APIs are properly enforced.
 */
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@LargeTest
@RunWith(AndroidJUnit4.class)
@ApiTest(apis = {"Manifest.permission#ACCESS_FINE_LOCATION"})
public class WifiLocationInfoForegroundTest extends WifiJUnit4TestBase {
    private static final String TAG = "WifiLocationInfoTest";

    private static final String WIFI_LOCATION_TEST_APP_APK_PATH =
            "/data/local/tmp/cts/wifi/CtsWifiLocationTestApp.apk";
    private static final String WIFI_LOCATION_TEST_APP_PACKAGE_NAME =
            "android.net.wifi.cts.app";
    private static final String WIFI_LOCATION_TEST_APP_TRIGGER_SCAN_ACTIVITY =
            WIFI_LOCATION_TEST_APP_PACKAGE_NAME + ".TriggerScanAndReturnStatusActivity";
    private static final String WIFI_LOCATION_TEST_APP_RETRIEVE_SCAN_RESULTS_ACTIVITY =
            WIFI_LOCATION_TEST_APP_PACKAGE_NAME + ".RetrieveScanResultsAndReturnStatusActivity";
    private static final String WIFI_LOCATION_TEST_APP_RETRIEVE_CONNECTION_INFO_ACTIVITY =
            WIFI_LOCATION_TEST_APP_PACKAGE_NAME + ".RetrieveConnectionInfoAndReturnStatusActivity";
    private static final String WIFI_LOCATION_TEST_APP_RETRIEVE_TRANSPORT_INFO_ACTIVITY =
            WIFI_LOCATION_TEST_APP_PACKAGE_NAME + ".RetrieveTransportInfoAndReturnStatusActivity";

    private static final int DURATION_MS = 10_000;
    private static final int WIFI_CONNECT_TIMEOUT_MILLIS = 30_000;

    @Rule
    public final ActivityTestRule<WaitForResultActivity> mActivityRule =
            new ActivityTestRule<>(WaitForResultActivity.class);

    private static Context sContext;
    private static WifiManager sWifiManager;
    private static boolean sWasVerboseLoggingEnabled;
    private static boolean sWasScanThrottleEnabled;
    private static PowerManager sPower;
    private static PowerManager.WakeLock sLock;

    private static boolean sShouldRunTest = false;

    @BeforeClass
    public static void setUpClass() throws Exception {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        if (!WifiFeature.isWifiSupported(sContext)) {
            return;
        }
        sShouldRunTest = true;

        sWifiManager = sContext.getSystemService(WifiManager.class);
        assertThat(sWifiManager).isNotNull();

        // turn on verbose logging for tests
        sWasVerboseLoggingEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.isVerboseLoggingEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setVerboseLoggingEnabled(true));
        // Disable scan throttling for tests.
        sWasScanThrottleEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.isScanThrottleEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScanThrottleEnabled(false));

        // enable Wifi
        if (!sWifiManager.isWifiEnabled()) setWifiEnabled(true);
        PollingCheck.check("Wifi not enabled", DURATION_MS, () -> sWifiManager.isWifiEnabled());

        // check we have >= 1 saved network
        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.getConfiguredNetworks());
        assertWithMessage("Need at least one saved network").that(savedNetworks).isNotEmpty();

        // ensure Wifi is connected
        ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.reconnect());
        PollingCheck.check(
                "Wifi not connected",
                WIFI_CONNECT_TIMEOUT_MILLIS,
                () -> sWifiManager.getConnectionInfo().getNetworkId() != -1);
        sPower = sContext.getSystemService(PowerManager.class);
        sLock = sPower.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        turnScreenOn();
    }

    @Before
    public void setUp() throws InterruptedException {
        // skip the test if WiFi is not supported
        assumeTrue(sShouldRunTest);
        installApp(WIFI_LOCATION_TEST_APP_APK_PATH);

    }

    @After
    public void teardown() throws InterruptedException {
        if (!sShouldRunTest) {
            return;
        }
        uninstallApp(WIFI_LOCATION_TEST_APP_PACKAGE_NAME);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (!sShouldRunTest) return;
        if (!sWifiManager.isWifiEnabled()) setWifiEnabled(true);
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScanThrottleEnabled(sWasScanThrottleEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setVerboseLoggingEnabled(sWasVerboseLoggingEnabled));
    }

    private static void setWifiEnabled(boolean enable) throws Exception {
        ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.setWifiEnabled(enable));
    }

    private static void turnScreenOn() throws Exception {
        if (sLock.isHeld()) sLock.release();
        SystemUtil.runShellCommand("input keyevent KEYCODE_WAKEUP");
        SystemUtil.runShellCommand("wm dismiss-keyguard");
    }

    private static void installApp(String apk) throws InterruptedException {
        String installResult = SystemUtil.runShellCommand("pm install -r -d " + apk);
        assertThat(installResult.trim()).isEqualTo("Success");
    }

    private static void uninstallApp(String pkg) throws InterruptedException {
        String uninstallResult = SystemUtil.runShellCommand(
                "pm uninstall " + pkg);
        assertThat(uninstallResult.trim()).isEqualTo("Success");
    }

    private void startFgActivityAndAssertStatusIs(
            ComponentName componentName, boolean status) throws Exception {
        WaitForResultActivity activity = mActivityRule.getActivity();
        activity.startActivityToWaitForResult(componentName);
        assertThat(activity.waitForActivityResult(DURATION_MS)).isEqualTo(status);
    }

    private void triggerScanFgActivityAndAssertStatusIs(boolean status) throws Exception {
        startFgActivityAndAssertStatusIs(new ComponentName(WIFI_LOCATION_TEST_APP_PACKAGE_NAME,
                WIFI_LOCATION_TEST_APP_TRIGGER_SCAN_ACTIVITY), status);
    }

    private void retrieveScanResultsFgActivityAndAssertStatusIs(boolean status) throws Exception {
        startFgActivityAndAssertStatusIs(new ComponentName(WIFI_LOCATION_TEST_APP_PACKAGE_NAME,
                WIFI_LOCATION_TEST_APP_RETRIEVE_SCAN_RESULTS_ACTIVITY), status);
    }

    private void retrieveConnectionInfoFgActivityAndAssertStatusIs(boolean status)
            throws Exception {
        startFgActivityAndAssertStatusIs(new ComponentName(WIFI_LOCATION_TEST_APP_PACKAGE_NAME,
                WIFI_LOCATION_TEST_APP_RETRIEVE_CONNECTION_INFO_ACTIVITY), status);
    }

    private void retrieveTransportInfoFgActivityAndAssertStatusIs(boolean status)
            throws Exception {
        startFgActivityAndAssertStatusIs(new ComponentName(WIFI_LOCATION_TEST_APP_PACKAGE_NAME,
                WIFI_LOCATION_TEST_APP_RETRIEVE_TRANSPORT_INFO_ACTIVITY), status);
    }

    @Test
    public void testScanTriggerNotAllowedForForegroundActivityWithNoLocationPermission()
            throws Exception {
        triggerScanFgActivityAndAssertStatusIs(false);
    }

    @Test
    public void testScanTriggerAllowedForForegroundActivityWithFineLocationPermission()
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_FINE_LOCATION);
        triggerScanFgActivityAndAssertStatusIs(true);
    }

    @Test
    public void testScanResultsRetrievalNotAllowedForForegroundActivityWithNoLocationPermission()
            throws Exception {
        retrieveScanResultsFgActivityAndAssertStatusIs(false);
    }

    @Test
    public void testScanResultsRetrievalAllowedForForegroundActivityWithFineLocationPermission()
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_FINE_LOCATION);
        retrieveScanResultsFgActivityAndAssertStatusIs(true);
    }

    @Test
    public void testConnectionInfoRetrievalNotAllowedForForegroundActivityWithNoLocationPermission()
            throws Exception {
        retrieveConnectionInfoFgActivityAndAssertStatusIs(false);
    }

    @Test
    public void testConnectionInfoRetrievalAllowedForForegroundActivityWithFineLocationPermission()
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_FINE_LOCATION);
        retrieveConnectionInfoFgActivityAndAssertStatusIs(true);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testTransportInfoRetrievalNotAllowedForForegroundActivityWithNoLocationPermission()
            throws Exception {
        retrieveTransportInfoFgActivityAndAssertStatusIs(false);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testTransportInfoRetrievalAllowedForForegroundActivityWithFineLocationPermission()
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_FINE_LOCATION);
        retrieveTransportInfoFgActivityAndAssertStatusIs(true);
    }
}
