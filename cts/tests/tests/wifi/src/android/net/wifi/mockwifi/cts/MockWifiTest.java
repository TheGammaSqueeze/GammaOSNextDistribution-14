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

package android.net.wifi.mockwifi.cts;

import static android.content.Context.RECEIVER_EXPORTED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.net.wifi.cts.WifiFeature;
import android.net.wifi.cts.WifiManagerTest.Mutable;
import android.net.wifi.nl80211.NativeScanResult;
import android.net.wifi.nl80211.RadioChainInfo;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiDevice;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.wifi.mockwifi.MockWifiModemManager;
import android.wifi.mockwifi.nl80211.IClientInterfaceImp;
import android.wifi.mockwifi.nl80211.IWifiScannerImp;
import android.wifi.mockwifi.nl80211.WifiNL80211ManagerImp;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot get WifiManager/WifiNl80211Manager in instant app mode")
public class MockWifiTest {
    private static final String TAG = "MockWifiTest";

    private static final int TEST_WAIT_DURATION_MS = 10_000;
    private static final int WAIT_MS = 60;
    private static final int WIFI_CONNECT_TIMEOUT_MS = 30_000;
    private static final int WIFI_DISCONNECT_TIMEOUT_MS = 30_000;
    private static final int WIFI_PNO_CONNECT_TIMEOUT_MILLIS = 90_000;

    private static Context sContext;
    private static boolean sShouldRunTest = false;

    private static final int STATE_NULL = 0;
    private static final int STATE_WIFI_CHANGING = 1;
    private static final int STATE_WIFI_ENABLED = 2;
    private static final int STATE_WIFI_DISABLED = 3;
    private static final int STATE_SCANNING = 4;
    private static final int STATE_SCAN_DONE = 5;

    private static class MySync {
        public int expectedState = STATE_NULL;
    }

    private static MySync sMySync;
    private static WifiManager sWifiManager;
    private static ConnectivityManager sConnectivityManager;
    private static UiDevice sUiDevice;
    private static PowerManager.WakeLock sWakeLock;
    private static boolean sWasVerboseLoggingEnabled;
    private static boolean sWasScanThrottleEnabled;
    private static List<ScanResult> sScanResults = null;
    private static MockWifiModemManager sMockModemManager;
    private static NetworkInfo sNetworkInfo =
            new NetworkInfo(ConnectivityManager.TYPE_WIFI, TelephonyManager.NETWORK_TYPE_UNKNOWN,
                    "wifi", "unknown");
    private static int sTestAccessPointFrequency = 0;

    private final Object mLock = new Object();

    private static void turnScreenOnNoDelay() throws Exception {
        if (sWakeLock.isHeld()) sWakeLock.release();
        sUiDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        sUiDevice.executeShellCommand("wm dismiss-keyguard");
    }

    private static void turnScreenOffNoDelay() throws Exception {
        sUiDevice.executeShellCommand("input keyevent KEYCODE_SLEEP");
    }

    private static final BroadcastReceiver sReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                synchronized (sMySync) {
                    if (intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) {
                        sScanResults = sWifiManager.getScanResults();
                    } else {
                        sScanResults = null;
                    }
                    sMySync.expectedState = STATE_SCAN_DONE;
                    sMySync.notifyAll();
                }
            } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                int newState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN);
                synchronized (sMySync) {
                    if (newState == WifiManager.WIFI_STATE_ENABLED) {
                        Log.d(TAG, "*** New WiFi state is ENABLED ***");
                        sMySync.expectedState = STATE_WIFI_ENABLED;
                        sMySync.notifyAll();
                    } else if (newState == WifiManager.WIFI_STATE_DISABLED) {
                        Log.d(TAG, "*** New WiFi state is DISABLED ***");
                        sMySync.expectedState = STATE_WIFI_DISABLED;
                        sMySync.notifyAll();
                    }
                }
            } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                synchronized (sMySync) {
                    sNetworkInfo =
                            (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if (sNetworkInfo.getState() == NetworkInfo.State.CONNECTED) {
                        sMySync.notifyAll();
                    }
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
        sWifiManager = sContext.getSystemService(WifiManager.class);
        assertThat(sWifiManager).isNotNull();
        sConnectivityManager = sContext.getSystemService(ConnectivityManager.class);
        sMockModemManager = new MockWifiModemManager(sContext);
        assertNotNull(sMockModemManager);

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

        sWakeLock = sContext.getSystemService(PowerManager.class).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, TAG);
        sUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        turnScreenOnNoDelay();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.ACTION_PICK_WIFI_NETWORK);
        intentFilter.setPriority(999);

        sContext.registerReceiver(sReceiver, intentFilter, RECEIVER_EXPORTED);

        synchronized (sMySync) {
            sMySync.expectedState = STATE_NULL;
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (!sShouldRunTest) {
            return;
        }
        if (!sWifiManager.isWifiEnabled()) {
            setWifiEnabled(true);
        }
        sContext.unregisterReceiver(sReceiver);
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScanThrottleEnabled(sWasScanThrottleEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setVerboseLoggingEnabled(sWasVerboseLoggingEnabled));
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(sShouldRunTest);

        // enable Wifi
        if (!sWifiManager.isWifiEnabled()) setWifiEnabled(true);
        PollingCheck.check("Wifi not enabled", TEST_WAIT_DURATION_MS,
                () -> sWifiManager.isWifiEnabled());

        sWifiManager.startScan();
        waitForConnection(); // ensures that there is at-least 1 saved network on the device.
        if (sTestAccessPointFrequency == 0) {
            WifiInfo currentNetwork = ShellIdentityUtils.invokeWithShellPermissions(
                    sWifiManager::getConnectionInfo);
            sTestAccessPointFrequency = currentNetwork.getFrequency();
            assertNotEquals("Invalid Access-point frequency", sTestAccessPointFrequency, 0);
        }
    }

    private static void setWifiEnabled(boolean enable) throws Exception {
        synchronized (sMySync) {
            if (sWifiManager.isWifiEnabled() != enable) {
                // the new state is different, we expect it to change
                sMySync.expectedState = STATE_WIFI_CHANGING;
            } else {
                sMySync.expectedState = (enable ? STATE_WIFI_ENABLED : STATE_WIFI_DISABLED);
            }
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.setWifiEnabled(enable));
            waitForExpectedWifiState(enable);
        }
    }

    private static void waitForExpectedWifiState(boolean enabled) throws InterruptedException {
        synchronized (sMySync) {
            long timeout = System.currentTimeMillis() + TEST_WAIT_DURATION_MS;
            int expected = (enabled ? STATE_WIFI_ENABLED : STATE_WIFI_DISABLED);
            while (System.currentTimeMillis() < timeout
                    && sMySync.expectedState != expected) {
                sMySync.wait(WAIT_MS);
            }
            assertEquals(expected, sMySync.expectedState);
        }
    }

    private void waitForNetworkInfoState(NetworkInfo.State state, int timeoutMillis)
            throws Exception {
        synchronized (sMySync) {
            if (sNetworkInfo.getState() == state) return;
            long timeout = System.currentTimeMillis() + timeoutMillis;
            while (System.currentTimeMillis() < timeout
                    && sNetworkInfo.getState() != state) {
                sMySync.wait(WAIT_MS);
            }
            assertEquals(state, sNetworkInfo.getState());
        }
    }

    private void waitForConnection() throws Exception {
        waitForNetworkInfoState(NetworkInfo.State.CONNECTED, WIFI_CONNECT_TIMEOUT_MS);
    }

    private void waitForConnection(int timeoutMillis) throws Exception {
        waitForNetworkInfoState(NetworkInfo.State.CONNECTED, timeoutMillis);
    }

    private void waitForDisconnection() throws Exception {
        waitForNetworkInfoState(NetworkInfo.State.DISCONNECTED, WIFI_DISCONNECT_TIMEOUT_MS);
    }

    private String getIfaceName() {
        Network wifiCurrentNetwork = sWifiManager.getCurrentNetwork();
        assertNotNull(wifiCurrentNetwork);
        LinkProperties wifiLinkProperties = sConnectivityManager.getLinkProperties(
                wifiCurrentNetwork);
        String mIfaceName = wifiLinkProperties.getInterfaceName();
        return mIfaceName;
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void testMockSignalPollOnMockWifi() throws Exception {
        int testRssi = -30;
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            WifiInfo wifiInfo = sWifiManager.getConnectionInfo();
            assertTrue(sMockModemManager.connectMockWifiModemService(sContext));
            assertTrue(sMockModemManager.configureClientInterfaceMock(getIfaceName(),
                    new IClientInterfaceImp.ClientInterfaceMock() {
                        @Override
                        public int[] signalPoll() {
                            return new int[]{
                                    testRssi,
                                    wifiInfo.getTxLinkSpeedMbps(), wifiInfo.getRxLinkSpeedMbps(),
                                    wifiInfo.getFrequency()
                            };
                        }
                    }));
            sMockModemManager.updateConfiguredMockedMethods();
            PollingCheck.check(
                    "Rssi update fail", 30_000,
                    () -> {
                        WifiInfo newWifiInfo = sWifiManager.getConnectionInfo();
                        return newWifiInfo.getRssi() == testRssi;
                    });
        } finally {
            sMockModemManager.disconnectMockWifiModemService();
        }
    }

    private NativeScanResult[] getMockNativeResults() {
        byte[] testSsid =
                new byte[] {'M', 'o', 'c', 'k', 'T', 'e', 's', 't', 'A', 'P'};
        byte[] testBssid =
                new byte[] {(byte) 0x12, (byte) 0xef, (byte) 0xa1,
                    (byte) 0x2c, (byte) 0x97, (byte) 0x8b};
        byte[] testInfoElement =
                new byte[] {(byte) 0x01, (byte) 0x03, (byte) 0x12, (byte) 0xbe, (byte) 0xff};
        int testCapability = (0x1 << 2) | (0x1 << 5);
        int[] radioChainIds = {0, 1};
        int[] radioChainLevels = {-56, -65};

        NativeScanResult scanResult = new NativeScanResult();
        scanResult.ssid = testSsid;
        scanResult.bssid = testBssid;
        scanResult.infoElement = testInfoElement;
        scanResult.frequency = sTestAccessPointFrequency;
        // Add extra 4 seconds as the scan result timestamp to simulate the real behavior
        // like scan result will return after scan triggered 4 ~ 6 seconds.
        // It also avoid the timing issue cause scan result is filtered with old scan time.
        scanResult.tsf = (SystemClock.elapsedRealtime() + 4) * 1000;
        scanResult.capability = testCapability;
        scanResult.radioChainInfos = new ArrayList<>(Arrays.asList(
                new RadioChainInfo(radioChainIds[0], radioChainLevels[0]),
                new RadioChainInfo(radioChainIds[1], radioChainLevels[1])));

        NativeScanResult[] nativeScanResults = new NativeScanResult[1];
        nativeScanResults[0] = scanResult;
        return nativeScanResults;
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void testMockPnoScanResultsOnMockWifi() throws Exception {
        if (!sWifiManager.isPreferredNetworkOffloadSupported()) {
            return;
        }
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            WifiInfo currentNetwork = sWifiManager.getConnectionInfo();
            assertTrue(sMockModemManager.connectMockWifiModemService(sContext));
            assertTrue(sMockModemManager.configureWifiScannerInterfaceMock(getIfaceName(),
                    new IWifiScannerImp.WifiScannerInterfaceMock() {
                    @Override
                    public NativeScanResult[] getPnoScanResults() {
                        return getMockNativeResults();
                    }
                }));

            sMockModemManager.updateConfiguredMockedMethods();
            List<WifiSsid> listOfSsids = new ArrayList<WifiSsid>();
            for (NativeScanResult nativeScan : getMockNativeResults()) {
                listOfSsids.add(WifiSsid.fromBytes(nativeScan.getSsid()));
            }
            AtomicReference<List<ScanResult>> mScanResults = new AtomicReference<>();
            Mutable<Boolean> isQuerySucceeded = new Mutable<Boolean>(false);
            sWifiManager.setExternalPnoScanRequest(listOfSsids,
                    null, Executors.newSingleThreadExecutor(),
                    new WifiManager.PnoScanResultsCallback() {
                    @Override
                    public void onScanResultsAvailable(List<ScanResult> scanResults) {
                        synchronized (mLock) {
                            mScanResults.set(scanResults);
                            Log.d(TAG, "Results from callback registered : " + mScanResults);
                            isQuerySucceeded.value = true;
                            mLock.notify();
                        }
                    }
                    @Override
                    public void onRegisterSuccess() {
                        synchronized (mLock) {
                            Log.d(TAG, "onRegisterSuccess");
                            mLock.notify();
                        }
                    }
                    @Override
                    public void onRegisterFailed(int reason) {
                        synchronized (mLock) {
                            mLock.notify();
                        }
                    }
                    @Override
                    public void onRemoved(int reason) {
                        synchronized (mLock) {
                            mLock.notify();
                        }
                    }
                });
            synchronized (mLock) {
                long now = System.currentTimeMillis();
                long deadline = now + TEST_WAIT_DURATION_MS;
                while (!isQuerySucceeded.value && now < deadline) {
                    mLock.wait(deadline - now);
                    now = System.currentTimeMillis();
                }
            }
            sWifiManager.disconnect();
            waitForDisconnection();
            turnScreenOffNoDelay();
            sWifiManager.enableNetwork(currentNetwork.getNetworkId(), false);
            waitForConnection(WIFI_PNO_CONNECT_TIMEOUT_MILLIS);
            assertTrue(mScanResults.get().stream().allMatch(
                    p -> listOfSsids.contains(p.getWifiSsid())));
        } finally {
            turnScreenOnNoDelay();
            sMockModemManager.disconnectMockWifiModemService();
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private NativeScanResult[] getMockZeroLengthSubElementIe() {
        byte[] zeroSubElementIE = new byte[] {
                (byte) 0xff,  (byte) 0x10, (byte) 0x6b,
                (byte) 0x10,  (byte) 0x00,                             // Control
                (byte) 0x08,  (byte) 0x02, (byte) 0x34, (byte) 0x56,   // Common Info
                (byte) 0x78,  (byte) 0x9A, (byte) 0xBC, (byte) 0x01,
                (byte) 0x08,  (byte) 0x00, (byte) 0x02, (byte) 0x00,   // First Link Info
                (byte) 0x00,  (byte) 0x00, (byte) 0x00, (byte) 0x00,   //
                (byte) 0x00,  (byte) 0x00, (byte) 0x03, (byte) 0x00,   // Second Link Info
                (byte) 0x00,  (byte) 0x00, (byte) 0x00, (byte) 0x00    //
        };
        NativeScanResult[] zeroLengthSubElementIE = getMockNativeResults();
        zeroLengthSubElementIE[0].infoElement = zeroSubElementIE;
        return zeroLengthSubElementIE;
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void testZeroLengthSubElementIEOnMockWifi() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            final NativeScanResult[] mockScanData = getMockZeroLengthSubElementIe();
            uiAutomation.adoptShellPermissionIdentity();
            final String currentStaIfaceName = getIfaceName();
            assertTrue(sMockModemManager.connectMockWifiModemService(sContext));
            assertTrue(sMockModemManager.configureWifiScannerInterfaceMock(currentStaIfaceName,
                    new IWifiScannerImp.WifiScannerInterfaceMock() {
                    @Override
                    public NativeScanResult[] getScanResults() {
                        return mockScanData;
                    }
                }));
            sMockModemManager.updateConfiguredMockedMethods();
            WifiNL80211ManagerImp mockedWifiNL80211Manager =
                    sMockModemManager.getWifiNL80211ManagerImp();
            assertNotNull(mockedWifiNL80211Manager);
            mockedWifiNL80211Manager.mockScanResultReadyEvent(currentStaIfaceName);
            PollingCheck.check(
                    "getscanResults fail", 4_000,
                    () -> {
                        List<ScanResult> scanResults = sWifiManager.getScanResults();
                        if (scanResults.size() == 0) {
                            return false;
                        }
                        return (scanResults.get(0).getWifiSsid().equals(
                            WifiSsid.fromBytes(mockScanData[0].getSsid()))
                            && MacAddress.fromString(scanResults.get(0).BSSID).equals(
                            mockScanData[0].getBssid())
                            && scanResults.get(0).frequency == mockScanData[0].getFrequencyMhz()
                            && (scanResults.get(0).getApMloLinkId() == -1)
                            && (scanResults.get(0).getApMldMacAddress() == null));
                    });
        } finally {
            sMockModemManager.disconnectMockWifiModemService();
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
