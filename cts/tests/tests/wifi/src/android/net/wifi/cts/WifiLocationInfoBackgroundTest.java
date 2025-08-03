/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.NonMainlineTest;
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

@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@LargeTest
@RunWith(AndroidJUnit4.class)
@ApiTest(apis = {"Manifest.permission#ACCESS_BACKGROUND_LOCATION"})
@NonMainlineTest
@RequiresDevice
public class WifiLocationInfoBackgroundTest extends WifiJUnit4TestBase{
    private static final String TAG = "WifiLocationInfoTest";

    private static final String WIFI_LOCATION_TEST_APP_APK_PATH =
            "/data/local/tmp/cts/wifi/CtsWifiLocationTestApp.apk";
    private static final String WIFI_LOCATION_TEST_APP_PACKAGE_NAME =
            "android.net.wifi.cts.app";
    private static final String WIFI_LOCATION_TEST_APP_TRIGGER_SCAN_SERVICE =
            WIFI_LOCATION_TEST_APP_PACKAGE_NAME + ".TriggerScanAndReturnStatusService";
    private static final String WIFI_LOCATION_TEST_APP_RETRIEVE_SCAN_RESULTS_SERVICE =
            WIFI_LOCATION_TEST_APP_PACKAGE_NAME + ".RetrieveScanResultsAndReturnStatusService";
    private static final String WIFI_LOCATION_TEST_APP_RETRIEVE_CONNECTION_INFO_SERVICE =
            WIFI_LOCATION_TEST_APP_PACKAGE_NAME + ".RetrieveConnectionInfoAndReturnStatusService";
    private static final String WIFI_LOCATION_TEST_APP_RETRIEVE_TRANSPORT_INFO_SERVICE =
            WIFI_LOCATION_TEST_APP_PACKAGE_NAME + ".RetrieveTransportInfoAndReturnStatusService";

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
        // TODO(b/290671748): re-enable the test when we havea solution on wear devices
        if (isWearDevice()) {
            return;
        }
        sShouldRunTest = true;

        sPower = sContext.getSystemService(PowerManager.class);
        sLock = sPower.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

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
        turnScreenOff();
    }

    private static boolean isWearDevice() {
        return sContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
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
        turnScreenOn();
    }

    private static void setWifiEnabled(boolean enable) throws Exception {
        ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.setWifiEnabled(enable));
    }

    private static void turnScreenOn() throws Exception {
        if (sLock.isHeld()) sLock.release();
        SystemUtil.runShellCommand("input keyevent KEYCODE_WAKEUP");
        SystemUtil.runShellCommand("wm dismiss-keyguard");
    }

    private static void turnScreenOff() throws Exception {
        if (!sLock.isHeld()) sLock.acquire();
        SystemUtil.runShellCommand("input keyevent KEYCODE_SLEEP");
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

    private void startBgServiceAndAssertStatusIs(
            ComponentName componentName, boolean status) throws Exception {
        WaitForResultActivity activity = mActivityRule.getActivity();
        activity.startServiceToWaitForResult(componentName);
        assertThat(activity.waitForServiceResult(DURATION_MS)).isEqualTo(status);
    }

    private void triggerScanBgServiceAndAssertStatusIs(boolean status) throws Exception {
        startBgServiceAndAssertStatusIs(new ComponentName(WIFI_LOCATION_TEST_APP_PACKAGE_NAME,
                WIFI_LOCATION_TEST_APP_TRIGGER_SCAN_SERVICE), status);
    }

    private void retrieveScanResultsBgServiceAndAssertStatusIs(boolean status) throws Exception {
        startBgServiceAndAssertStatusIs(new ComponentName(WIFI_LOCATION_TEST_APP_PACKAGE_NAME,
                WIFI_LOCATION_TEST_APP_RETRIEVE_SCAN_RESULTS_SERVICE), status);
    }

    private void retrieveConnectionInfoBgServiceAndAssertStatusIs(boolean status) throws Exception {
        startBgServiceAndAssertStatusIs(new ComponentName(WIFI_LOCATION_TEST_APP_PACKAGE_NAME,
                WIFI_LOCATION_TEST_APP_RETRIEVE_CONNECTION_INFO_SERVICE), status);
    }

    private void retrieveTransportInfoBgServiceAndAssertStatusIs(boolean status) throws Exception {
        startBgServiceAndAssertStatusIs(new ComponentName(WIFI_LOCATION_TEST_APP_PACKAGE_NAME,
                WIFI_LOCATION_TEST_APP_RETRIEVE_TRANSPORT_INFO_SERVICE), status);
    }

    @Test
    public void testScanTriggerAllowedWithBackgroundLocationPermission()
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_FINE_LOCATION);
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_BACKGROUND_LOCATION);
        triggerScanBgServiceAndAssertStatusIs(true);
    }

    @Test
    public void testScanTriggerNotAllowedWithFineLocationPermission()
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_FINE_LOCATION);
        triggerScanBgServiceAndAssertStatusIs(false);
    }

    @Test
    public void testScanResultsRetrievalAllowedWithBackgroundLocationPermission()
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_FINE_LOCATION);
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_BACKGROUND_LOCATION);
        retrieveScanResultsBgServiceAndAssertStatusIs(true);
    }

    @Test
    public void testScanResultsRetrievalNotAllowedWithFineLocationPermission()
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_FINE_LOCATION);
        retrieveScanResultsBgServiceAndAssertStatusIs(false);
    }

    @Test
    public void testConnectionInfoRetrievalAllowedWithBackgroundLocationPermission()
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_FINE_LOCATION);
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_BACKGROUND_LOCATION);
        retrieveConnectionInfoBgServiceAndAssertStatusIs(true);
    }

    @Test
    public void testConnectionInfoRetrievalNotAllowedWithFineLocationPermission()
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_FINE_LOCATION);
        retrieveConnectionInfoBgServiceAndAssertStatusIs(false);
    }


    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testTransportInfoRetrievalAllowedWithBackgroundLocationPermission()
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_FINE_LOCATION);
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_BACKGROUND_LOCATION);
        retrieveTransportInfoBgServiceAndAssertStatusIs(true);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testTransportInfoRetrievalNotAllowedWithFineLocationPermission()
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                WIFI_LOCATION_TEST_APP_PACKAGE_NAME, ACCESS_FINE_LOCATION);
        retrieveTransportInfoBgServiceAndAssertStatusIs(false);
    }
}
