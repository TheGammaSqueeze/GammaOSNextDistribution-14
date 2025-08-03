/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static android.content.Context.RECEIVER_EXPORTED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.MloLink;
import android.net.wifi.ScanResult;
import android.net.wifi.ScanResult.InformationElement;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.os.Build;
import android.os.Parcel;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.support.test.uiautomator.UiDevice;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;


@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
public class ScanResultTest extends WifiJUnit4TestBase {
    private static Context sContext;

    private static boolean sShouldRunTest = false;

    private static class MySync {
        int expectedState = STATE_NULL;
    }

    private static WifiManager sWifiManager;
    private static WifiLock sWifiLock;
    private static TestHelper sTestHelper;
    private static MySync sMySync;
    private static boolean sWasVerboseLoggingEnabled;
    private static boolean sWasScanThrottleEnabled;

    private static final int STATE_NULL = 0;
    private static final int STATE_WIFI_CHANGING = 1;
    private static final int STATE_WIFI_CHANGED = 2;
    private static final int STATE_START_SCAN = 3;
    private static final int STATE_SCAN_RESULTS_AVAILABLE = 4;
    private static final int STATE_SCAN_FAILURE = 5;

    private static final String TAG = "WifiInfoTest";
    private static final int TIMEOUT_MSEC = 6000;
    private static final int WAIT_MSEC = 60;
    private static final int ENABLE_WAIT_MSEC = 10000;
    private static final int SCAN_WAIT_MSEC = 10000;
    private static final int SCAN_MAX_RETRY_COUNT = 6;
    private static final int SCAN_FIND_BSSID_MAX_RETRY_COUNT = 5;
    private static final long SCAN_FIND_BSSID_WAIT_MSEC = 5_000L;
    private static final int WIFI_CONNECT_TIMEOUT_MILLIS = 30_000;

    // Note: defined in ScanRequestProxy.java
    public static final int SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS = 4;

    private static final String TEST_SSID = "TEST_SSID";
    public static final String TEST_BSSID = "04:ac:fe:45:34:10";
    public static final String TEST_CAPS = "CCMP";
    public static final int TEST_LEVEL = -56;
    public static final int TEST_FREQUENCY = 2412;
    public static final long TEST_TIMESTAMP = 4660L;

    private static final BroadcastReceiver RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                synchronized (sMySync) {
                    sMySync.expectedState = STATE_WIFI_CHANGED;
                    sMySync.notify();
                }
            } else if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                synchronized (sMySync) {
                    if (intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) {
                        sMySync.expectedState = STATE_SCAN_RESULTS_AVAILABLE;
                    } else {
                        sMySync.expectedState = STATE_SCAN_FAILURE;
                    }
                    sMySync.notify();
                }
            }
        }
    };

    @BeforeClass
    public static void setUpClass() throws Exception {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        if (!WifiFeature.isWifiSupported(sContext)) {
            // skip the test if WiFi is not supported
            return;
        }
        sShouldRunTest = true;
        sMySync = new MySync();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.ACTION_PICK_WIFI_NETWORK);

        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            sContext.registerReceiver(RECEIVER, intentFilter, RECEIVER_EXPORTED);
        } else {
            sContext.registerReceiver(RECEIVER, intentFilter);
        }
        sWifiManager = sContext.getSystemService(WifiManager.class);
        assertThat(sWifiManager).isNotNull();

        sTestHelper = new TestHelper(InstrumentationRegistry.getInstrumentation().getContext(),
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()));

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

        sWifiLock = sWifiManager.createWifiLock(TAG);
        sWifiLock.acquire();

        // enable Wifi
        if (!sWifiManager.isWifiEnabled()) setWifiEnabled(true);
        PollingCheck.check("Wifi not enabled", ENABLE_WAIT_MSEC,
                () -> sWifiManager.isWifiEnabled());

        sMySync.expectedState = STATE_NULL;
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (!sShouldRunTest) {
            return;
        }
        sWifiLock.release();
        sContext.unregisterReceiver(RECEIVER);
        if (!sWifiManager.isWifiEnabled()) setWifiEnabled(true);
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScanThrottleEnabled(sWasScanThrottleEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setVerboseLoggingEnabled(sWasVerboseLoggingEnabled));
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(sShouldRunTest);
    }

    private static void setWifiEnabled(boolean enable) throws Exception {
        synchronized (sMySync) {
            sMySync.expectedState = STATE_WIFI_CHANGING;
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.setWifiEnabled(enable));
            waitForBroadcast(TIMEOUT_MSEC, STATE_WIFI_CHANGED);
       }
    }

    private static boolean waitForBroadcast(long timeout, int expectedState) throws Exception {
        long waitTime = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < waitTime
                && sMySync.expectedState != expectedState)
            sMySync.wait(WAIT_MSEC);
        return sMySync.expectedState == expectedState;
    }

    @Test
    public void testScanResultProperties() {
        // this test case should in Wifi environment
        for (ScanResult scanResult : sWifiManager.getScanResults()) {
            assertThat(scanResult.toString()).isNotNull();

            for (InformationElement ie : scanResult.getInformationElements()) {
                testInformationElementCopyConstructor(ie);
                testInformationElementFields(ie);
            }

            assertThat(scanResult.getWifiStandard()).isAnyOf(
                    ScanResult.WIFI_STANDARD_UNKNOWN,
                    ScanResult.WIFI_STANDARD_LEGACY,
                    ScanResult.WIFI_STANDARD_11N,
                    ScanResult.WIFI_STANDARD_11AC,
                    ScanResult.WIFI_STANDARD_11AX,
                    ScanResult.WIFI_STANDARD_11AD,
                    ScanResult.WIFI_STANDARD_11BE
            );

            scanResult.isPasspointNetwork();
        }
    }

    private void testInformationElementCopyConstructor(InformationElement ie) {
        InformationElement copy = new InformationElement(ie);

        assertThat(copy.getId()).isEqualTo(ie.getId());
        assertThat(copy.getIdExt()).isEqualTo(ie.getIdExt());
        assertThat(copy.getBytes()).isEqualTo(ie.getBytes());
    }

    private void testInformationElementFields(InformationElement ie) {
        // id is 1 octet
        int id = ie.getId();
        assertThat(id).isAtLeast(0);
        assertThat(id).isAtMost(255);

        // idExt is 0 or 1 octet
        int idExt = ie.getIdExt();
        assertThat(idExt).isAtLeast(0);
        assertThat(idExt).isAtMost(255);

        ByteBuffer bytes = ie.getBytes();
        assertThat(bytes).isNotNull();
    }

    /* Multiple scans to ensure bssid is updated */
    private void scanAndWait() throws Exception {
        synchronized (sMySync) {
            for (int retry  = 0; retry < SCAN_MAX_RETRY_COUNT; retry++) {
                sMySync.expectedState = STATE_START_SCAN;
                sWifiManager.startScan();
                if (waitForBroadcast(SCAN_WAIT_MSEC, STATE_SCAN_RESULTS_AVAILABLE)) {
                    break;
                }
            }
        }
   }

    @RequiresDevice
    @Test
    public void testScanResultTimeStamp() throws Exception {
        long timestamp = 0;
        String BSSID = null;

        scanAndWait();

        List<ScanResult> scanResults = sWifiManager.getScanResults();
        for (ScanResult result : scanResults) {
            BSSID = result.BSSID;
            timestamp = result.timestamp;
            assertThat(timestamp).isNotEqualTo(0);
            break;
        }

        scanAndWait();

        scanResults = sWifiManager.getScanResults();
        for (ScanResult result : scanResults) {
            if (result.BSSID.equals(BSSID)) {
                long timeDiff = (result.timestamp - timestamp) / 1000;
                assertThat(timeDiff).isGreaterThan(0L);
                assertThat(timeDiff).isLessThan(6L * SCAN_WAIT_MSEC);
            }
        }
    }

    /** Test that the copy constructor copies fields correctly. */
    @Test
    public void testScanResultConstructors() {
        ScanResult scanResult = new ScanResult();
        scanResult.SSID = TEST_SSID;
        scanResult.setWifiSsid(WifiSsid.fromBytes(TEST_SSID.getBytes(StandardCharsets.UTF_8)));
        scanResult.BSSID = TEST_BSSID;
        scanResult.capabilities = TEST_CAPS;
        scanResult.level = TEST_LEVEL;
        scanResult.frequency = TEST_FREQUENCY;
        scanResult.timestamp = TEST_TIMESTAMP;

        ScanResult scanResult2 = new ScanResult(scanResult);
        assertThat(scanResult2.SSID).isEqualTo(TEST_SSID);
        assertThat(scanResult2.getWifiSsid()).isEqualTo(scanResult.getWifiSsid());
        assertThat(scanResult2.BSSID).isEqualTo(TEST_BSSID);
        assertThat(scanResult2.capabilities).isEqualTo(TEST_CAPS);
        assertThat(scanResult2.level).isEqualTo(TEST_LEVEL);
        assertThat(scanResult2.frequency).isEqualTo(TEST_FREQUENCY);
        assertThat(scanResult2.timestamp).isEqualTo(TEST_TIMESTAMP);
    }

    @Test
    public void testScanResultMatchesWifiInfo() throws Exception {
        // ensure Wifi is connected
        ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.reconnect());
        PollingCheck.check(
                "Wifi not connected",
                WIFI_CONNECT_TIMEOUT_MILLIS,
                () -> sWifiManager.getConnectionInfo().getNetworkId() != -1);

        final WifiInfo wifiInfo = sWifiManager.getConnectionInfo();
        assertThat(wifiInfo).isNotNull();

        ScanResult currentNetwork = null;
        for (int i = 0; i < SCAN_FIND_BSSID_MAX_RETRY_COUNT; i++) {
            scanAndWait();
            final List<ScanResult> scanResults = sWifiManager.getScanResults();
            currentNetwork = scanResults.stream().filter(r -> r.BSSID.equals(wifiInfo.getBSSID()))
                    .findAny().orElse(null);

            if (currentNetwork != null) {
                break;
            }
            Thread.sleep(SCAN_FIND_BSSID_WAIT_MSEC);
        }
        assertWithMessage("Current network not found in scan results")
                .that(currentNetwork).isNotNull();

        String wifiInfoSsidQuoted = wifiInfo.getSSID();
        String scanResultSsidUnquoted = currentNetwork.SSID;

        assertWithMessage(
                "SSID mismatch: make sure this isn't a hidden network or an SSID containing "
                        + "non-UTF-8 characters - neither is supported by this CTS test.")
                .that("\"" + scanResultSsidUnquoted + "\"")
                .isEqualTo(wifiInfoSsidQuoted);
        assertThat(currentNetwork.frequency).isEqualTo(wifiInfo.getFrequency());
        assertThat(currentNetwork.getSecurityTypes())
                .asList().contains(wifiInfo.getCurrentSecurityType());
    }

    /**
     * Verify that scan throttling is enforced.
     */
    @Test
    public void testScanThrottling() throws Exception {
        // re-enable scan throttling
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScanThrottleEnabled(true));
        sTestHelper.turnScreenOn();

        try {
            synchronized (sMySync) {
                for (int i = 0; i < SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS; ++i) {
                    sWifiManager.startScan();
                    // TODO(b/277663385): Increased timeout until cuttlefish's mac80211_hwsim uses
                    //  more than 1 channel.
                    assertTrue("Iteration #" + i,
                            waitForBroadcast(SCAN_WAIT_MSEC * 2, STATE_SCAN_RESULTS_AVAILABLE));
                }

                sWifiManager.startScan();
                assertTrue("Should be throttled",
                        waitForBroadcast(SCAN_WAIT_MSEC, STATE_SCAN_FAILURE));
            }
        } finally {
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.setScanThrottleEnabled(false));
        }
    }

    /**
     * Test MLO Attributes in ScanResult Constructor (WiFi-7)
     */
    @Test
    public void testScanResultMloAttributes() {
        ScanResult scanResult = new ScanResult();
        assertNull(scanResult.getApMldMacAddress());
        assertEquals(MloLink.INVALID_MLO_LINK_ID, scanResult.getApMloLinkId());
        assertNotNull(scanResult.getAffiliatedMloLinks());
        assertTrue(scanResult.getAffiliatedMloLinks().isEmpty());
    }

    /**
     * Test MLO Link Constructor (WiFi-7)
     */
    @Test
    public void testMloLinkConstructor() {
        MloLink mloLink = new MloLink();
        assertEquals(WifiScanner.WIFI_BAND_UNSPECIFIED, mloLink.getBand());
        assertEquals(0, mloLink.getChannel());
        assertEquals(MloLink.INVALID_MLO_LINK_ID, mloLink.getLinkId());
        assertNull(mloLink.getStaMacAddress());
        assertNull(mloLink.getApMacAddress());
        assertEquals(MloLink.MLO_LINK_STATE_UNASSOCIATED, mloLink.getState());
        assertEquals(WifiInfo.INVALID_RSSI, mloLink.getRssi());
        assertEquals(WifiInfo.LINK_SPEED_UNKNOWN, mloLink.getRxLinkSpeedMbps());
        assertEquals(WifiInfo.LINK_SPEED_UNKNOWN, mloLink.getTxLinkSpeedMbps());
    }

    /**
     * Test MLO Link parcelable APIs
     */
    @Test
    public void testMloLinkParcelable() {
        Parcel p = Parcel.obtain();
        MloLink mloLink = new MloLink();

        mloLink.writeToParcel(p, 0);
        p.setDataPosition(0);
        MloLink newMloLink = MloLink.CREATOR.createFromParcel(p);
        assertEquals(mloLink, newMloLink);
        assertEquals("hashCode() did not get right hashCode",
                mloLink.hashCode(), newMloLink.hashCode());
    }
}
