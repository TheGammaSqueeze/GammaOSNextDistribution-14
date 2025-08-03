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

package android.net.wifi.rtt.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.cts.TestHelper;
import android.net.wifi.cts.WifiFeature;
import android.net.wifi.cts.WifiJUnit4TestBase;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Base class for Wi-Fi RTT CTS test cases. Provides a uniform configuration and event management
 * facility.
 */
public class TestBase extends WifiJUnit4TestBase {
    protected static final String TAG = "WifiRttCtsTests";

    // wait for Wi-Fi RTT to become available
    private static final int WAIT_FOR_RTT_CHANGE_SECS = 10;

    // wait for Wi-Fi scan results to become available
    private static final int WAIT_FOR_SCAN_RESULTS_SECS = 20;

    // wait for network selection and connection finish
    private static final int WAIT_FOR_CONNECTION_FINISH_MS = 30_000;

    // Interval between failure scans
    private static final int INTERVAL_BETWEEN_FAILURE_SCAN_MILLIS = 5_000;

    private static final int DURATION_MILLIS = 10_000;

    // Number of scans to do while searching for APs supporting IEEE 802.11mc
    private static final int NUM_SCANS_SEARCHING_FOR_IEEE80211MC_AP = 5;

    // 5GHz Frequency band
    private static final int FREQUENCY_OF_5GHZ_BAND_IN_MHZ = 5_000;
    private static Context sContext;
    private static boolean sShouldRunTest;
    private static UiDevice sUiDevice;
    private static TestHelper sTestHelper;
    private static boolean sWasVerboseLoggingEnabled;
    private static WifiManager sWifiManager;
    private static Boolean sWasScanThrottleEnabled;
    private static boolean sWasWifiEnabled;
    private static ScanResult s11McScanResult;
    private static ScanResult s11AzScanResult;
    private static ScanResult sLegacyScanResult;

    protected WifiRttManager mWifiRttManager;
    protected Bundle mCharacteristics;

    private final HandlerThread mHandlerThread = new HandlerThread("SingleDeviceTest");
    protected final Executor mExecutor;

    {
        mHandlerThread.start();
        mExecutor = new HandlerExecutor(new Handler(mHandlerThread.getLooper()));
    }

    @BeforeClass
    public static void setupClass() throws Exception {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        // skip the test if WiFi is not supported
        // Don't use assumeTrue in @BeforeClass
        if (!WifiFeature.isWifiSupported(sContext)) {
            Log.w(TAG, "Wifi not supported. Test wouldn't run");
            return;
        }
        if (!WifiFeature.isRttSupported(sContext)) {
            Log.w(TAG, "Wifi RTT not supported. Test wouldn't run");
            return;
        }
        // skip the test if location is not supported
        if (!sContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION)) {
            Log.w(TAG, "Location not supported. Test wouldn't run");
            return;
        }
        // skip if the location is disabled
        if (!sContext.getSystemService(LocationManager.class).isLocationEnabled()) {
            Log.w(TAG, "Location is turned off. Test wouldn't run");
            return;
        }


        sWifiManager = sContext.getSystemService(WifiManager.class);
        assertThat(sWifiManager).isNotNull();
        sShouldRunTest = true;
        sUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        sTestHelper = new TestHelper(sContext, sUiDevice);

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
        // Disable auto join
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.allowAutojoinGlobal(false));

        // turn screen on
        sTestHelper.turnScreenOn();
        // enable Wifi
        sWasWifiEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.isWifiEnabled());
        if (!sWifiManager.isWifiEnabled()) {
            ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.setWifiEnabled(true));
        }
        PollingCheck.check("Wifi not enabled", DURATION_MILLIS, () -> sWifiManager.isWifiEnabled());
        scanForTestAp();
        Thread.sleep(DURATION_MILLIS);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (!sShouldRunTest) return;

        // turn screen off
        sTestHelper.turnScreenOff();
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScanThrottleEnabled(sWasScanThrottleEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setVerboseLoggingEnabled(sWasVerboseLoggingEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setWifiEnabled(sWasWifiEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.allowAutojoinGlobal(true));
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(sShouldRunTest);
        mWifiRttManager = sContext.getSystemService(WifiRttManager.class);
        assertNotNull("Wi-Fi RTT Manager", mWifiRttManager);
        if (!mWifiRttManager.isAvailable()) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiRttManager.ACTION_WIFI_RTT_STATE_CHANGED);
            WifiRttBroadcastReceiver receiver = new WifiRttBroadcastReceiver();
            sContext.registerReceiver(receiver, intentFilter);
            assertTrue("Timeout waiting for Wi-Fi RTT to change status",
                    receiver.waitForStateChange());
            assertTrue("Wi-Fi RTT is not available (should be)", mWifiRttManager.isAvailable());
        }
        mCharacteristics = mWifiRttManager.getRttCharacteristics();
    }

    static class WifiRttBroadcastReceiver extends BroadcastReceiver {
        private final CountDownLatch mBlocker = new CountDownLatch(1);

        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiRttManager.ACTION_WIFI_RTT_STATE_CHANGED.equals(intent.getAction())) {
                mBlocker.countDown();
            }
        }

        boolean waitForStateChange() throws InterruptedException {
            return mBlocker.await(WAIT_FOR_RTT_CHANGE_SECS, TimeUnit.SECONDS);
        }
    }

    static class WifiScansBroadcastReceiver extends BroadcastReceiver {
        private final CountDownLatch mBlocker = new CountDownLatch(1);

        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                mBlocker.countDown();
            }
        }

        boolean waitForStateChange() throws InterruptedException {
            return mBlocker.await(WAIT_FOR_SCAN_RESULTS_SECS, TimeUnit.SECONDS);
        }
    }

    static class ResultCallback extends RangingResultCallback {
        private final CountDownLatch mBlocker = new CountDownLatch(1);
        private int mCode; // 0: success, otherwise RangingResultCallback STATUS_CODE_*.
        private List<RangingResult> mResults;

        @Override
        public void onRangingFailure(int code) {
            mCode = code;
            mResults = null; // not necessary since intialized to null - but for completeness
            mBlocker.countDown();
        }

        @Override
        public void onRangingResults(List<RangingResult> results) {
            mCode = 0; // not necessary since initialized to 0 - but for completeness
            mResults = results;
            mBlocker.countDown();
        }

        /**
         * Waits for the listener callback to be called - or an error (timeout, interruption).
         * Returns true on callback called, false on error (timeout, interruption).
         */
        boolean waitForCallback() throws InterruptedException {
            return mBlocker.await(WAIT_FOR_RTT_CHANGE_SECS, TimeUnit.SECONDS);
        }

        /**
         * Returns the code of the callback operation. Will be 0 for success (onRangingResults
         * called), else (if onRangingFailure called) will be one of the STATUS_CODE_* values.
         */
        int getCode() {
            return mCode;
        }

        /**
         * Returns the list of ranging results. In cases of error (getCode() != 0) will return null.
         */
        List<RangingResult> getResults() {
            return mResults;
        }
    }

    /**
     * Start a scan and return a list of observed ScanResults (APs).
     */
    private static List<ScanResult> scanAps() throws InterruptedException {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        WifiScansBroadcastReceiver receiver = new WifiScansBroadcastReceiver();
        sContext.registerReceiver(receiver, intentFilter);

        sWifiManager.startScan();
        receiver.waitForStateChange();
        sContext.unregisterReceiver(receiver);
        return sWifiManager.getScanResults();
    }

    private static void scanForTestAp()
            throws InterruptedException {
        int scanCount = 0;

        Map<String, ScanResult> ap24Ghz11Mc = new HashMap<>();
        Map<String, ScanResult> ap5Ghz11Mc = new HashMap<>();
        Map<String, ScanResult> ap24Ghz11Az = new HashMap<>();
        Map<String, ScanResult> ap5Ghz11Az = new HashMap<>();

        while (scanCount <= NUM_SCANS_SEARCHING_FOR_IEEE80211MC_AP) {
            for (ScanResult scanResult : scanAps()) {
                if (!scanResult.is80211mcResponder() && !scanResult.is80211azNtbResponder()) {
                    if (scanResult.centerFreq0 < FREQUENCY_OF_5GHZ_BAND_IN_MHZ) {
                        continue;
                    }
                    if (sLegacyScanResult == null
                            || scanResult.level > sLegacyScanResult.level) {
                        sLegacyScanResult = scanResult;
                    }
                    continue;
                }
                if (scanResult.level < -70) {
                    continue;
                }
                if (is24Ghz(scanResult.frequency)) {
                    if (scanResult.is80211azNtbResponder()) {
                        ap24Ghz11Az.put(scanResult.BSSID, scanResult);
                    } else {
                        ap24Ghz11Mc.put(scanResult.BSSID, scanResult);
                    }
                } else if (is5Ghz(scanResult.frequency)) {
                    if (scanResult.is80211azNtbResponder()) {
                        ap5Ghz11Az.put(scanResult.BSSID, scanResult);
                    } else {
                        ap5Ghz11Mc.put(scanResult.BSSID, scanResult);
                    }
                }
            }
            if (sLegacyScanResult == null) {
                // Ongoing connection may cause scan failure, wait for a while before next scan.
                Thread.sleep(INTERVAL_BETWEEN_FAILURE_SCAN_MILLIS);
            }
            scanCount++;
        }

        if (!ap5Ghz11Mc.isEmpty()) {
            s11McScanResult = getRandomScanResult(ap5Ghz11Mc.values());
        } else {
            s11McScanResult = getRandomScanResult(ap24Ghz11Mc.values());
        }

        if (!ap5Ghz11Az.isEmpty()) {
            s11AzScanResult = getRandomScanResult(ap5Ghz11Az.values());
        } else {
            s11AzScanResult = getRandomScanResult(ap24Ghz11Az.values());
        }

    }

    static Context getContext() {
        return sContext;
    }

    static ScanResult getS11AzScanResult() {
        return s11AzScanResult;
    }

    static ScanResult getS11McScanResult() {
        return s11McScanResult;
    }

    static ScanResult getLegacyScanResult() {
        return sLegacyScanResult;
    }

    private static boolean is24Ghz(int freq) {
        return freq >= 2142 && freq <= 2484;
    }

    private static boolean is5Ghz(int freq) {
        return freq >= 5160 && freq <= 5885;
    }

    private static ScanResult getRandomScanResult(Collection<ScanResult> scanResults) {
        if (scanResults.isEmpty()) {
            return null;
        }
        int index = new Random().nextInt(scanResults.size());
        return new ArrayList<>(scanResults).get(index);
    }
    private static ScanResult getHighestRssiScanResult(Collection<ScanResult> scanResults) {
        if (scanResults.isEmpty()) {
            return null;
        }
        return scanResults.stream().max(Comparator.comparingInt(a -> a.level)).get();
    }
}
