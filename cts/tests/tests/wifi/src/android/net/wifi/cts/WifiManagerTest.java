/*
 * Copyright (C) 2009 The Android Open Source Project
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
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.wifi.SoftApCapability.SOFTAP_FEATURE_ACS_OFFLOAD;
import static android.net.wifi.WifiAvailableChannel.OP_MODE_SAP;
import static android.net.wifi.WifiAvailableChannel.OP_MODE_STA;
import static android.net.wifi.WifiAvailableChannel.OP_MODE_WIFI_DIRECT_GO;
import static android.net.wifi.WifiConfiguration.INVALID_NETWORK_ID;
import static android.net.wifi.WifiManager.COEX_RESTRICTION_SOFTAP;
import static android.net.wifi.WifiManager.COEX_RESTRICTION_WIFI_AWARE;
import static android.net.wifi.WifiManager.COEX_RESTRICTION_WIFI_DIRECT;
import static android.net.wifi.WifiScanner.WIFI_BAND_24_GHZ;
import static android.os.Process.myUid;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.app.UiAutomation;
import android.app.admin.DevicePolicyManager;
import android.app.admin.WifiSsidPolicy;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.TetheringManager;
import android.net.Uri;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.MloLink;
import android.net.wifi.MscsParams;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.QosCharacteristics;
import android.net.wifi.QosPolicyParams;
import android.net.wifi.ScanResult;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApInfo;
import android.net.wifi.SoftApState;
import android.net.wifi.UriParserResults;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.SubsystemRestartTrackingCallback;
import android.net.wifi.WifiManager.WifiLock;
import android.net.wifi.WifiNetworkConnectionStatistics;
import android.net.wifi.WifiNetworkSelectionConfig;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.net.wifi.WifiUriParser;
import android.net.wifi.hotspot2.ConfigParser;
import android.net.wifi.hotspot2.OsuProvider;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.ProvisioningCallback;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.net.wifi.twt.TwtRequest;
import android.net.wifi.twt.TwtSession;
import android.net.wifi.twt.TwtSessionCallback;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.RequiresDevice;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.support.test.uiautomator.UiDevice;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.PropertyUtil;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.ThrowingRunnable;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.MacAddressUtils;
import com.android.wifi.flags.Flags;

import com.google.common.collect.Range;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
public class WifiManagerTest extends WifiJUnit4TestBase {
    private static Context sContext;
    private static boolean sShouldRunTest = false;

    private static class MySync {
        int expectedState = STATE_NULL;
    }

    private static WifiManager sWifiManager;
    private static ConnectivityManager sConnectivityManager;
    private static TetheringManager sTetheringManager;
    private static MySync sMySync;
    private static List<ScanResult> sScanResults = null;
    private static NetworkInfo sNetworkInfo =
            new NetworkInfo(ConnectivityManager.TYPE_WIFI, TelephonyManager.NETWORK_TYPE_UNKNOWN,
                    "wifi", "unknown");
    private final Object mLock = new Object();
    private static UiDevice sUiDevice;
    private static boolean sWasVerboseLoggingEnabled;
    private static boolean sWasScanThrottleEnabled;
    private static SoftApConfiguration sOriginalSoftApConfig = null;
    private static PowerManager sPowerManager;
    private static PowerManager.WakeLock sWakeLock;
    // Please refer to WifiManager
    private static final int MIN_RSSI = -100;
    private static final int MAX_RSSI = -55;

    private static final int STATE_NULL = 0;
    private static final int STATE_WIFI_CHANGING = 1;
    private static final int STATE_WIFI_ENABLED = 2;
    private static final int STATE_WIFI_DISABLED = 3;
    private static final int STATE_SCANNING = 4;
    private static final int STATE_SCAN_DONE = 5;

    private static final String TAG = "WifiManagerTest";
    private static final String SSID1 = "\"WifiManagerTest\"";
    private static final String BOOT_DEFAULT_WIFI_COUNTRY_CODE = "ro.boot.wificountrycode";
    // A full single scan duration is typically about 6-7 seconds, but
    // depending on devices it takes more time (9-11 seconds). For a
    // safety margin, the test waits for 15 seconds.
    private static final int SCAN_TEST_WAIT_DURATION_MS = 15_000;
    private static final int TEST_WAIT_DURATION_MS = 10_000;
    private static final int WIFI_CONNECT_TIMEOUT_MILLIS = 30_000;
    private static final int WIFI_OFF_ON_TIMEOUT_MILLIS = 5_000;
    private static final int WIFI_PNO_CONNECT_TIMEOUT_MILLIS = 90_000;
    private static final int WAIT_MSEC = 60;
    private static final int DURATION_SCREEN_TOGGLE = 2000;
    private static final int DURATION_SETTINGS_TOGGLE = 1_000;
    private static final int DURATION_SOFTAP_START_MS = 6_000;
    private static final int WIFI_SCAN_TEST_CACHE_DELAY_MILLIS = 3 * 60 * 1000;
    private static final String DEVICE_CONFIG_NAMESPACE = "wifi";

    private static final int ENFORCED_NUM_NETWORK_SUGGESTIONS_PER_APP = 50;

    private static final String TEST_PAC_URL = "http://www.example.com/proxy.pac";
    private static final String MANAGED_PROVISIONING_PACKAGE_NAME
            = "com.android.managedprovisioning";

    private static final String TEST_SSID_UNQUOTED = "testSsid1";
    private static final String TEST_IP_ADDRESS = "192.168.5.5";
    private static final String TEST_MAC_ADDRESS = "aa:bb:cc:dd:ee:ff";
    private static final MacAddress TEST_MAC = MacAddress.fromString(TEST_MAC_ADDRESS);
    private static final String TEST_PASSPHRASE = "passphrase";
    private static final String PASSPOINT_INSTALLATION_FILE_WITH_CA_CERT =
            "assets/ValidPasspointProfile.base64";
    private static final String TYPE_WIFI_CONFIG = "application/x-wifi-config";
    private static final String TEST_PSK_CAP = "[RSN-PSK-CCMP]";
    private static final String TEST_BSSID = "00:01:02:03:04:05";
    private static final String TEST_COUNTRY_CODE = "JP";
    private static final String TEST_DOM_SUBJECT_MATCH = "domSubjectMatch";
    private static final int TEST_SUB_ID = 2;
    private static final int EID_VSA = 221; // Copied from ScanResult.InformationElement

    private static final int TEST_LINK_LAYER_STATS_POLLING_INTERVAL_MS = 1000;
    private static final List<ScanResult.InformationElement> TEST_VENDOR_ELEMENTS =
            new ArrayList<>(Arrays.asList(
                    new ScanResult.InformationElement(221, 0, new byte[]{ 1, 2, 3, 4 }),
                    new ScanResult.InformationElement(
                            221,
                            0,
                            new byte[]{ (byte) 170, (byte) 187, (byte) 204, (byte) 221 })
            ));
    private static final int[] TEST_RSSI2_THRESHOLDS = {-81, -79, -73, -60};
    private static final int[] TEST_RSSI5_THRESHOLDS = {-80, -77, -71, -55};
    private static final int[] TEST_RSSI6_THRESHOLDS = {-79, -72, -65, -55};
    private static final SparseArray<Integer> TEST_FREQUENCY_WEIGHTS = new SparseArray<>();

    private IntentFilter mIntentFilter;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
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

    private static class TestProvisioningCallback extends ProvisioningCallback {
        private final Object mObject;
        // Initialize with an invalid status value (0)
        public int mProvisioningStatus = 0;
        // Initialize with an invalid status value (0)
        public int mProvisioningFailureStatus = 0;
        public boolean mProvisioningComplete = false;


        TestProvisioningCallback(Object lock) {
            mObject = lock;
        }

        @Override
        public void onProvisioningFailure(int status) {
            synchronized (mObject) {
                mProvisioningFailureStatus = status;
                mObject.notify();
            }
        }

        @Override
        public void onProvisioningStatus(int status) {
            synchronized (mObject) {
                mProvisioningStatus = status;
                mObject.notify();
            }
        }

        @Override
        public void onProvisioningComplete() {
            mProvisioningComplete = true;
        }
    }

    private static class TestSubsystemRestartTrackingCallback
            extends SubsystemRestartTrackingCallback {
        private final Object mObject;

        public int mSubsystemRestartStatus = 0; // 0: nada, 1: restarting, 2: restarted

        TestSubsystemRestartTrackingCallback(Object lock) {
            mObject = lock;
        }
        @Override
        public void onSubsystemRestarting() {
            synchronized (mObject) {
                mSubsystemRestartStatus = 1;
                mObject.notify();
            }
        }

        @Override
        public void onSubsystemRestarted() {
            synchronized (mObject) {
                mSubsystemRestartStatus = 2;
                mObject.notify();

            }
        }
    }

    private static final String TEST_SSID = "TEST SSID";
    private static final String TEST_FRIENDLY_NAME = "Friendly Name";
    private static final Map<String, String> TEST_FRIENDLY_NAMES = new HashMap<>();
    static {
        TEST_FRIENDLY_NAMES.put("en", TEST_FRIENDLY_NAME);
        TEST_FRIENDLY_NAMES.put("kr", TEST_FRIENDLY_NAME + 2);
        TEST_FRIENDLY_NAMES.put("jp", TEST_FRIENDLY_NAME + 3);
    }

    private static final String TEST_SERVICE_DESCRIPTION = "Dummy Service";
    private static final Uri TEST_SERVER_URI = Uri.parse("https://test.com");
    private static final String TEST_NAI = "test.access.com";
    private static final List<Integer> TEST_METHOD_LIST =
            Arrays.asList(1 /* METHOD_SOAP_XML_SPP */);
    private final HandlerThread mHandlerThread = new HandlerThread("WifiManagerTest");
    protected final Executor mExecutor;
    {
        mHandlerThread.start();
        mExecutor = new HandlerExecutor(new Handler(mHandlerThread.getLooper()));
    }

    /**
     * Class which can be used to fetch an object out of a lambda. Fetching an object
     * out of a local scope with HIDL is a common operation (although usually it can
     * and should be avoided).
     *
     * @param <E> Inner object type.
     */
    public static final class Mutable<E> {
        public E value;

        public Mutable() {
            value = null;
        }

        public Mutable(E value) {
            this.value = value;
        }
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        if (!WifiFeature.isWifiSupported(sContext)) {
            // skip the test if WiFi is not supported
            return;
        }
        sShouldRunTest = true;
        sPowerManager = sContext.getSystemService(PowerManager.class);
        sWakeLock = sPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
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
        intentFilter.setPriority(999);

        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            sContext.registerReceiver(sReceiver, intentFilter, RECEIVER_EXPORTED);
        } else {
            sContext.registerReceiver(sReceiver, intentFilter);
        }
        sWifiManager =  sContext.getSystemService(WifiManager.class);
        sConnectivityManager = sContext.getSystemService(ConnectivityManager.class);
        sTetheringManager = sContext.getSystemService(TetheringManager.class);
        assertThat(sWifiManager).isNotNull();
        assertThat(sTetheringManager).isNotNull();

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

        sUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        turnScreenOnNoDelay();

        synchronized (sMySync) {
            sMySync.expectedState = STATE_NULL;
        }

        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                sWifiManager::getConfiguredNetworks);
        assertThat(savedNetworks.isEmpty()).isFalse();

        // Get original config for restore
        sOriginalSoftApConfig = ShellIdentityUtils.invokeWithShellPermissions(
                sWifiManager::getSoftApConfiguration);
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
        // restore original softap config
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setSoftApConfiguration(sOriginalSoftApConfig));
        Thread.sleep(TEST_WAIT_DURATION_MS);
        if (sWakeLock.isHeld()) {
            sWakeLock.release();
        }
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(sShouldRunTest);
        // enable Wifi
        if (!sWifiManager.isWifiEnabled()) {
            setWifiEnabled(true);
            startScan();
        }
        PollingCheck.check("Wifi not enabled", TEST_WAIT_DURATION_MS,
                () -> sWifiManager.isWifiEnabled());

        waitForConnection();
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
                sMySync.wait(WAIT_MSEC);
            }
        }
    }

    // Get the current scan status from sticky broadcast.
    private boolean isScanCurrentlyAvailable() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.ACTION_WIFI_SCAN_AVAILABILITY_CHANGED);
        Intent intent = sContext.registerReceiver(null, intentFilter);
        assertNotNull(intent);
        if (intent.getAction().equals(WifiManager.ACTION_WIFI_SCAN_AVAILABILITY_CHANGED)) {
            return intent.getBooleanExtra(WifiManager.EXTRA_SCAN_AVAILABLE, false);
        }
        return false;
    }

    private void startScan() throws Exception {
        synchronized (sMySync) {
            sMySync.expectedState = STATE_SCANNING;
            sScanResults = null;
            assertTrue(sWifiManager.startScan());
            long timeout = System.currentTimeMillis() + SCAN_TEST_WAIT_DURATION_MS;
            while (System.currentTimeMillis() < timeout
                    && sMySync.expectedState == STATE_SCANNING) {
                sMySync.wait(WAIT_MSEC);
            }
        }
    }

    private void waitForNetworkInfoState(NetworkInfo.State state, int timeoutMillis)
            throws Exception {
        synchronized (sMySync) {
            if (sNetworkInfo.getState() == state) return;
            long timeout = System.currentTimeMillis() + timeoutMillis;
            while (System.currentTimeMillis() < timeout
                    && sNetworkInfo.getState() != state)
                sMySync.wait(WAIT_MSEC);
            assertEquals(state, sNetworkInfo.getState());
        }
    }

    private void waitForConnection(int timeoutMillis) throws Exception {
        waitForNetworkInfoState(NetworkInfo.State.CONNECTED, timeoutMillis);
    }

    private void waitForConnection() throws Exception {
        waitForNetworkInfoState(NetworkInfo.State.CONNECTED, WIFI_CONNECT_TIMEOUT_MILLIS);
    }

    private void waitForDisconnection() throws Exception {
        waitForNetworkInfoState(NetworkInfo.State.DISCONNECTED, TEST_WAIT_DURATION_MS);
    }

    private void ensureNotNetworkInfoState(NetworkInfo.State state) throws Exception {
        synchronized (sMySync) {
            long timeout = System.currentTimeMillis() + TEST_WAIT_DURATION_MS + WAIT_MSEC;
            while (System.currentTimeMillis() < timeout) {
                assertNotEquals(state, sNetworkInfo.getState());
                sMySync.wait(WAIT_MSEC);
            }
        }
    }

    private void ensureNotConnected() throws Exception {
        ensureNotNetworkInfoState(NetworkInfo.State.CONNECTED);
    }

    private void ensureNotDisconnected() throws Exception {
        ensureNotNetworkInfoState(NetworkInfo.State.DISCONNECTED);
    }

    private boolean existSSID(String ssid) {
        for (final WifiConfiguration w : sWifiManager.getConfiguredNetworks()) {
            if (w.SSID.equals(ssid))
                return true;
        }
        return false;
    }

    private int findConfiguredNetworks(String SSID, List<WifiConfiguration> networks) {
        for (final WifiConfiguration w : networks) {
            if (w.SSID.equals(SSID))
                return networks.indexOf(w);
        }
        return -1;
    }

    /**
     * Test creation of WifiManager Lock.
     */
    @Test
    public void testWifiManagerLock() throws Exception {
        final String TAG = "Test";
        assertNotNull(sWifiManager.createWifiLock(TAG));
        assertNotNull(sWifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG));
    }

    /**
     * Test wifi scanning when Wifi is off and location scanning is turned on.
     */
    @Test
    public void testWifiManagerScanWhenWifiOffLocationTurnedOn() throws Exception {
        if (!hasLocationFeature()) {
            Log.d(TAG, "Skipping test as location is not supported");
            return;
        }
        if (!isLocationEnabled()) {
            fail("Please enable location for this test - since Marshmallow WiFi scan results are"
                    + " empty when location is disabled!");
        }
        runWithScanning(() -> {
            setWifiEnabled(false);
            Thread.sleep(TEST_WAIT_DURATION_MS);
            startScan();
            if (sWifiManager.isScanAlwaysAvailable() && isScanCurrentlyAvailable()) {
                // Make sure at least one AP is found.
                assertNotNull("mScanResult should not be null!", sScanResults);
                assertFalse("empty scan results!", sScanResults.isEmpty());
            } else {
                // Make sure no scan results are available.
                assertNull("mScanResult should be null!", sScanResults);
            }
            final String TAG = "Test";
            assertNotNull(sWifiManager.createWifiLock(TAG));
            assertNotNull(sWifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG));
        }, true /* run with enabled*/);
    }

    /**
     * Restart WiFi subsystem - verify that privileged call fails.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testRestartWifiSubsystemShouldFailNoPermission() throws Exception {
        try {
            sWifiManager.restartWifiSubsystem();
            fail("The restartWifiSubsystem should not succeed - privileged call");
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Restart WiFi subsystem and verify transition through states.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testRestartWifiSubsystem() throws Exception {
        TestSubsystemRestartTrackingCallback callback =
                new TestSubsystemRestartTrackingCallback(mLock);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiManager.registerSubsystemRestartTrackingCallback(mExecutor, callback);
            synchronized (mLock) {
                sWifiManager.restartWifiSubsystem();
                mLock.wait(TEST_WAIT_DURATION_MS);
            }
            assertEquals(callback.mSubsystemRestartStatus, 1); // 1: restarting
            waitForExpectedWifiState(false);
            assertFalse(sWifiManager.isWifiEnabled());
            synchronized (mLock) {
                mLock.wait(TEST_WAIT_DURATION_MS);
                assertEquals(callback.mSubsystemRestartStatus, 2); // 2: restarted
            }
            waitForExpectedWifiState(true);
            assertTrue(sWifiManager.isWifiEnabled());
        } finally {
            // cleanup
            sWifiManager.unregisterSubsystemRestartTrackingCallback(callback);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * test point of wifiManager properties:
     * 1.enable properties
     * 2.DhcpInfo properties
     * 3.wifi state
     * 4.ConnectionInfo
     */
    @Test
    public void testWifiManagerProperties() throws Exception {
        setWifiEnabled(true);
        assertTrue(sWifiManager.isWifiEnabled());
        assertNotNull(sWifiManager.getDhcpInfo());
        assertEquals(WifiManager.WIFI_STATE_ENABLED, sWifiManager.getWifiState());
        sWifiManager.getConnectionInfo();
        setWifiEnabled(false);
        assertFalse(sWifiManager.isWifiEnabled());
    }

    /**
     * Test WiFi scan timestamp - fails when WiFi scan timestamps are inconsistent with
     * {@link SystemClock#elapsedRealtime()} on device.<p>
     * To run this test in cts-tradefed:
     * run cts --class android.net.wifi.cts.WifiManagerTest --method testWifiScanTimestamp
     */
    @Test
    @RequiresDevice
    public void testWifiScanTimestamp() throws Exception {
        if (!hasLocationFeature()) {
            Log.d(TAG, "Skipping test as location is not supported");
            return;
        }
        if (!isLocationEnabled()) {
            fail("Please enable location for this test - since Marshmallow WiFi scan results are"
                    + " empty when location is disabled!");
        }
        if (!sWifiManager.isWifiEnabled()) {
            setWifiEnabled(true);
        }
        // Make sure the scan timestamps are consistent with the device timestamp within the range
        // of WIFI_SCAN_TEST_CACHE_DELAY_MILLIS.
        startScan();
        // Make sure at least one AP is found.
        assertTrue("mScanResult should not be null. This may be due to a scan timeout",
                   sScanResults != null);
        assertFalse("empty scan results!", sScanResults.isEmpty());
        long nowMillis = SystemClock.elapsedRealtime();
        // Keep track of how many APs are fresh in one scan.
        int numFreshAps = 0;
        for (ScanResult result : sScanResults) {
            long scanTimeMillis = TimeUnit.MICROSECONDS.toMillis(result.timestamp);
            if (Math.abs(nowMillis - scanTimeMillis)  < WIFI_SCAN_TEST_CACHE_DELAY_MILLIS) {
                numFreshAps++;
            }
        }
        // At least half of the APs in the scan should be fresh.
        int numTotalAps = sScanResults.size();
        String msg = "Stale AP count: " + (numTotalAps - numFreshAps) + ", fresh AP count: "
                + numFreshAps;
        assertTrue(msg, numFreshAps * 2 >= sScanResults.size());
    }

    @Test
    public void testConvertBetweenChannelFrequencyMhz() throws Exception {
        int[] testFrequency_2G = {2412, 2437, 2462, 2484};
        int[] testFrequency_5G = {5180, 5220, 5540, 5745};
        int[] testFrequency_6G = {5955, 6435, 6535, 7115};
        int[] testFrequency_60G = {58320, 64800};
        SparseArray<int[]> testData = new SparseArray<>() {{
            put(ScanResult.WIFI_BAND_24_GHZ, testFrequency_2G);
            put(ScanResult.WIFI_BAND_5_GHZ, testFrequency_5G);
            put(ScanResult.WIFI_BAND_6_GHZ, testFrequency_6G);
            put(ScanResult.WIFI_BAND_60_GHZ, testFrequency_60G);
        }};

        for (int i = 0; i < testData.size(); i++) {
            for (int frequency : testData.valueAt(i)) {
                assertEquals(frequency, ScanResult.convertChannelToFrequencyMhzIfSupported(
                      ScanResult.convertFrequencyMhzToChannelIfSupported(frequency), testData.keyAt(i)));
            }
        }
    }

    // Return true if location is enabled.
    private boolean isLocationEnabled() {
        return Settings.Secure.getInt(sContext.getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) !=
                Settings.Secure.LOCATION_MODE_OFF;
    }

    // Returns true if the device has location feature.
    private boolean hasLocationFeature() {
        return sContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION);
    }

    private boolean hasAutomotiveFeature() {
        return sContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private boolean hasWifiDirect() {
        return sContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_WIFI_DIRECT);
    }

    private boolean hasWifiAware() {
        return sContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
    }

    @Test
    public void testSignal() {
        final int numLevels = 9;
        int expectLevel = 0;
        assertEquals(expectLevel, WifiManager.calculateSignalLevel(MIN_RSSI, numLevels));
        assertEquals(numLevels - 1, WifiManager.calculateSignalLevel(MAX_RSSI, numLevels));
        expectLevel = 4;
        assertEquals(expectLevel, WifiManager.calculateSignalLevel((MIN_RSSI + MAX_RSSI) / 2,
                numLevels));
        int rssiA = 4;
        int rssiB = 5;
        assertTrue(WifiManager.compareSignalLevel(rssiA, rssiB) < 0);
        rssiB = 4;
        assertTrue(WifiManager.compareSignalLevel(rssiA, rssiB) == 0);
        rssiA = 5;
        rssiB = 4;
        assertTrue(WifiManager.compareSignalLevel(rssiA, rssiB) > 0);
    }

    /**
     * Test that {@link WifiManager#calculateSignalLevel(int)} returns a value in the range
     * [0, {@link WifiManager#getMaxSignalLevel()}], and its value is monotonically increasing as
     * the RSSI increases.
     */
    @Test
    public void testCalculateSignalLevel() {
        int maxSignalLevel = sWifiManager.getMaxSignalLevel();

        int prevSignalLevel = 0;
        for (int rssi = -150; rssi <= 50; rssi++) {
            int signalLevel = sWifiManager.calculateSignalLevel(rssi);

            // between [0, maxSignalLevel]
            assertWithMessage("For RSSI=%s", rssi).that(signalLevel).isAtLeast(0);
            assertWithMessage("For RSSI=%s", rssi).that(signalLevel).isAtMost(maxSignalLevel);

            // calculateSignalLevel(rssi) <= calculateSignalLevel(rssi + 1)
            assertWithMessage("For RSSI=%s", rssi).that(signalLevel).isAtLeast(prevSignalLevel);
            prevSignalLevel = signalLevel;
        }
    }

    public class TestWifiVerboseLoggingStatusChangedListener implements
            WifiManager.WifiVerboseLoggingStatusChangedListener {
        public int numCalls;
        public boolean status;

        @Override
        public void onWifiVerboseLoggingStatusChanged(boolean enabled) {
            numCalls++;
            status = enabled;
        }
    }

    public class TestSoftApCallback implements WifiManager.SoftApCallback {
        final Object mSoftApLock;
        SoftApState mCurrentSoftApState;
        int currentState;
        int currentFailureReason;
        List<SoftApInfo> apInfoList = new ArrayList<>();
        SoftApInfo apInfoOnSingleApMode;
        Map<SoftApInfo, List<WifiClient>> apInfoClients = new HashMap<>();
        List<WifiClient> currentClientList;
        SoftApCapability currentSoftApCapability;
        MacAddress lastBlockedClientMacAddress;
        int lastBlockedClientReason;
        boolean onStateChangedCalled = false;
        boolean mOnSoftApStateChangedCalled = false;
        boolean onSoftApCapabilityChangedCalled = false;
        boolean onConnectedClientCalled = false;
        boolean onConnectedClientChangedWithInfoCalled = false;
        boolean onBlockedClientConnectingCalled = false;
        int onSoftapInfoChangedCalledCount = 0;
        int onSoftapInfoChangedWithListCalledCount = 0;

        TestSoftApCallback(Object lock) {
            mSoftApLock = lock;
        }

        public boolean getOnStateChangedCalled() {
            synchronized (mSoftApLock) {
                return onStateChangedCalled;
            }
        }

        /**
         * Returns {@code true} if #onStateChanged(SoftApstate) was called, else {@code false}.
         */
        public boolean getOnSoftApStateChangedCalled() {
            synchronized (mSoftApLock) {
                return mOnSoftApStateChangedCalled;
            }
        }

        public int getOnSoftapInfoChangedCalledCount() {
            synchronized (mSoftApLock) {
                return onSoftapInfoChangedCalledCount;
            }
        }

        public int getOnSoftApInfoChangedWithListCalledCount() {
            synchronized (mSoftApLock) {
                return onSoftapInfoChangedWithListCalledCount;
            }
        }

        public boolean getOnSoftApCapabilityChangedCalled() {
            synchronized (mSoftApLock) {
                return onSoftApCapabilityChangedCalled;
            }
        }

        public boolean getOnConnectedClientChangedWithInfoCalled() {
            synchronized (mSoftApLock) {
                return onConnectedClientChangedWithInfoCalled;
            }
        }

        public boolean getOnConnectedClientCalled() {
            synchronized (mSoftApLock) {
                return onConnectedClientCalled;
            }
        }

        public boolean getOnBlockedClientConnectingCalled() {
            synchronized (mSoftApLock) {
                return onBlockedClientConnectingCalled;
            }
        }

        /**
         * Returns the latest SoftApState passed into #onStateChanged(SoftApState).
         */
        public SoftApState getCurrentSoftApState() {
            synchronized (mSoftApLock) {
                return mCurrentSoftApState;
            }
        }

        public int getCurrentState() {
            synchronized (mSoftApLock) {
                return currentState;
            }
        }

        public int getCurrentStateFailureReason() {
            synchronized (mSoftApLock) {
                return currentFailureReason;
            }
        }

        public List<WifiClient> getCurrentClientList() {
            synchronized (mSoftApLock) {
                return new ArrayList<>(currentClientList);
            }
        }

        public SoftApInfo getCurrentSoftApInfo() {
            synchronized (mSoftApLock) {
                return apInfoOnSingleApMode;
            }
        }

        public List<SoftApInfo> getCurrentSoftApInfoList() {
            synchronized (mSoftApLock) {
                return new ArrayList<>(apInfoList);
            }
        }

        public SoftApCapability getCurrentSoftApCapability() {
            synchronized (mSoftApLock) {
                return currentSoftApCapability;
            }
        }

        public MacAddress getLastBlockedClientMacAddress() {
            synchronized (mSoftApLock) {
                return lastBlockedClientMacAddress;
            }
        }

        public int getLastBlockedClientReason() {
            synchronized (mSoftApLock) {
                return lastBlockedClientReason;
            }
        }

        @Override
        public void onStateChanged(int state, int failureReason) {
            synchronized (mSoftApLock) {
                currentState = state;
                currentFailureReason = failureReason;
                onStateChangedCalled = true;
            }
        }

        @Override
        public void onStateChanged(SoftApState state) {
            synchronized (mSoftApLock) {
                mCurrentSoftApState = state;
                mOnSoftApStateChangedCalled = true;
            }
        }

        @Override
        public void onConnectedClientsChanged(List<WifiClient> clients) {
            synchronized (mSoftApLock) {
                currentClientList = new ArrayList<>(clients);
                onConnectedClientCalled = true;
            }
        }

        @Override
        public void onConnectedClientsChanged(SoftApInfo info, List<WifiClient> clients) {
            synchronized (mSoftApLock) {
                apInfoClients.put(info, clients);
                onConnectedClientChangedWithInfoCalled = true;
            }
        }

        @Override
        public void onInfoChanged(List<SoftApInfo> infoList) {
            synchronized (mSoftApLock) {
                apInfoList = new ArrayList<>(infoList);
                onSoftapInfoChangedWithListCalledCount++;
            }
        }

        @Override
        public void onInfoChanged(SoftApInfo softApInfo) {
            synchronized (mSoftApLock) {
                apInfoOnSingleApMode = softApInfo;
                onSoftapInfoChangedCalledCount++;
            }
        }

        @Override
        public void onCapabilityChanged(SoftApCapability softApCapability) {
            synchronized (mSoftApLock) {
                currentSoftApCapability = softApCapability;
                onSoftApCapabilityChangedCalled = true;
            }
        }

        @Override
        public void onBlockedClientConnecting(WifiClient client, int blockedReason) {
            synchronized (mSoftApLock) {
                lastBlockedClientMacAddress = client.getMacAddress();
                lastBlockedClientReason = blockedReason;
                onBlockedClientConnectingCalled = true;
            }
        }
    }

    private static class TestLocalOnlyHotspotCallback extends WifiManager.LocalOnlyHotspotCallback {
        Object hotspotLock;
        WifiManager.LocalOnlyHotspotReservation reservation = null;
        boolean onStartedCalled = false;
        boolean onStoppedCalled = false;
        boolean onFailedCalled = false;
        int failureReason = -1;

        TestLocalOnlyHotspotCallback(Object lock) {
            hotspotLock = lock;
        }

        @Override
        public void onStarted(WifiManager.LocalOnlyHotspotReservation r) {
            synchronized (hotspotLock) {
                reservation = r;
                onStartedCalled = true;
                hotspotLock.notify();
            }
        }

        @Override
        public void onStopped() {
            synchronized (hotspotLock) {
                onStoppedCalled = true;
                hotspotLock.notify();
            }
        }

        @Override
        public void onFailed(int reason) {
            synchronized (hotspotLock) {
                onFailedCalled = true;
                failureReason = reason;
                hotspotLock.notify();
            }
        }
    }

    private List<Integer> getSupportedSoftApBand(SoftApCapability capability) {
        List<Integer> supportedApBands = new ArrayList<>();
        if (sWifiManager.is24GHzBandSupported() && capability.areFeaturesSupported(
                SoftApCapability.SOFTAP_FEATURE_BAND_24G_SUPPORTED)) {
            supportedApBands.add(SoftApConfiguration.BAND_2GHZ);
        }
        if (sWifiManager.is5GHzBandSupported() && capability.areFeaturesSupported(
                SoftApCapability.SOFTAP_FEATURE_BAND_5G_SUPPORTED)) {
            supportedApBands.add(SoftApConfiguration.BAND_5GHZ);
        }
        if (sWifiManager.is6GHzBandSupported() && capability.areFeaturesSupported(
                SoftApCapability.SOFTAP_FEATURE_BAND_6G_SUPPORTED)) {
            supportedApBands.add(SoftApConfiguration.BAND_6GHZ);
        }
        if (sWifiManager.is60GHzBandSupported() && capability.areFeaturesSupported(
                SoftApCapability.SOFTAP_FEATURE_BAND_60G_SUPPORTED)) {
            supportedApBands.add(SoftApConfiguration.BAND_60GHZ);
        }
        return supportedApBands;
    }

    private TestLocalOnlyHotspotCallback startLocalOnlyHotspot() {
        // Location mode must be enabled for this test
        if (!isLocationEnabled()) {
            fail("Please enable location for this test");
        }

        TestExecutor executor = new TestExecutor();
        TestSoftApCallback lohsSoftApCallback = new TestSoftApCallback(mLock);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        List<Integer> supportedSoftApBands = new ArrayList<>();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            verifyLohsRegisterSoftApCallback(executor, lohsSoftApCallback);
            supportedSoftApBands = getSupportedSoftApBand(
                    lohsSoftApCallback.getCurrentSoftApCapability());
        } catch (Exception ex) {
        } finally {
            // clean up
            unregisterLocalOnlyHotspotSoftApCallback(lohsSoftApCallback);
            uiAutomation.dropShellPermissionIdentity();
        }
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback(mLock);
        synchronized (mLock) {
            try {
                sWifiManager.startLocalOnlyHotspot(callback, null);
                // now wait for callback
                mLock.wait(TEST_WAIT_DURATION_MS);
            } catch (InterruptedException e) {
            }
            // check if we got the callback
            assertTrue(callback.onStartedCalled);

            SoftApConfiguration softApConfig = callback.reservation.getSoftApConfiguration();
            assertNotNull(softApConfig);
            int securityType = softApConfig.getSecurityType();
            if (securityType == SoftApConfiguration.SECURITY_TYPE_OPEN
                    || securityType == SoftApConfiguration.SECURITY_TYPE_WPA2_PSK
                    || securityType == SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION) {
                assertNotNull(softApConfig.toWifiConfiguration());
            } else {
                assertNull(softApConfig.toWifiConfiguration());
            }
            if (!hasAutomotiveFeature()) {
                assertEquals(supportedSoftApBands.size() > 0 ? supportedSoftApBands.get(0)
                        : SoftApConfiguration.BAND_2GHZ,
                        callback.reservation.getSoftApConfiguration().getBand());
            }
            assertFalse(callback.onFailedCalled);
            assertFalse(callback.onStoppedCalled);
        }
        return callback;
    }

    private void stopLocalOnlyHotspot(TestLocalOnlyHotspotCallback callback, boolean wifiEnabled) {
        synchronized (sMySync) {
            // we are expecting a new state
            sMySync.expectedState = STATE_WIFI_CHANGING;

            // now shut down LocalOnlyHotspot
            if (callback.reservation != null) {
                callback.reservation.close();
            }

            try {
                waitForExpectedWifiState(wifiEnabled);
            } catch (InterruptedException e) { }
        }
    }

    /**
     * Verify that calls to startLocalOnlyHotspot succeed with proper permissions.
     *
     * Note: Location mode must be enabled for this test.
     */
    @Test
    public void testStartLocalOnlyHotspotSuccess() throws Exception {
        // check that softap mode is supported by the device
        if (!sWifiManager.isPortableHotspotSupported()) {
            return;
        }
        runWithScanning(() -> {
            boolean wifiEnabled = sWifiManager.isWifiEnabled();
            if (wifiEnabled) {
                // Re-enabled Wi-Fi as shell for HalDeviceManager legacy LOHS behavior when there's
                // no STA+AP concurrency.
                ShellIdentityUtils.invokeWithShellPermissions(() ->
                        sWifiManager.setWifiEnabled(false));
                PollingCheck.check("Wifi turn off failed!", WIFI_OFF_ON_TIMEOUT_MILLIS,
                        () -> !sWifiManager.isWifiEnabled());
                SystemUtil.runShellCommand("cmd wifi set-wifi-enabled enabled");
                PollingCheck.check("Wifi turn on failed!", WIFI_OFF_ON_TIMEOUT_MILLIS,
                        () -> sWifiManager.isWifiEnabled());
            }
            TestLocalOnlyHotspotCallback callback = startLocalOnlyHotspot();

            // add sleep to avoid calling stopLocalOnlyHotspot before TetherController
            // initialization.
            // TODO: remove this sleep as soon as b/124330089 is fixed.
            Log.d(TAG, "Sleeping for 2 seconds");
            Thread.sleep(2000);

            stopLocalOnlyHotspot(callback, wifiEnabled);

            // wifi should either stay on, or come back on
            assertEquals(wifiEnabled, sWifiManager.isWifiEnabled());
        }, false);
    }

    /**
     * Verify calls to deprecated API's all fail for non-settings apps targeting >= Q SDK.
     */
    @Test
    public void testDeprecatedApis() throws Exception {
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.SSID = SSID1;
        wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);

        assertEquals(INVALID_NETWORK_ID,
                sWifiManager.addNetwork(wifiConfiguration));
        assertEquals(INVALID_NETWORK_ID,
                sWifiManager.updateNetwork(wifiConfiguration));
        assertFalse(sWifiManager.enableNetwork(0, true));
        assertFalse(sWifiManager.disableNetwork(0));
        assertFalse(sWifiManager.removeNetwork(0));
        assertFalse(sWifiManager.disconnect());
        assertFalse(sWifiManager.reconnect());
        assertFalse(sWifiManager.reassociate());
        assertTrue(sWifiManager.getConfiguredNetworks().isEmpty());

        boolean wifiEnabled = sWifiManager.isWifiEnabled();
        // now we should fail to toggle wifi state.
        assertFalse(sWifiManager.setWifiEnabled(!wifiEnabled));
        Thread.sleep(TEST_WAIT_DURATION_MS);
        assertEquals(wifiEnabled, sWifiManager.isWifiEnabled());
    }

    /**
     * Test the WifiManager APIs that return whether a feature is supported.
     */
    @Test
    public void testGetSupportedFeatures() {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            // Skip the test if wifi module version is older than S.
            return;
        }
        sWifiManager.isMakeBeforeBreakWifiSwitchingSupported();
        sWifiManager.isStaBridgedApConcurrencySupported();
        sWifiManager.isDualBandSimultaneousSupported();
        sWifiManager.isTidToLinkMappingNegotiationSupported();
    }

    /**
     * Verify non DO apps cannot call removeNonCallerConfiguredNetworks.
     */
    @Test
    public void testRemoveNonCallerConfiguredNetworksNotAllowed() {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            // Skip the test if wifi module version is older than S.
            return;
        }
        try {
            sWifiManager.removeNonCallerConfiguredNetworks();
            fail("Expected security exception for non DO app");
        } catch (SecurityException e) {
        }
    }

    private WifiNetworkSelectionConfig buildTestNetworkSelectionConfig() {
        TEST_FREQUENCY_WEIGHTS.put(2400, WifiNetworkSelectionConfig.FREQUENCY_WEIGHT_LOW);
        TEST_FREQUENCY_WEIGHTS.put(6000, WifiNetworkSelectionConfig.FREQUENCY_WEIGHT_HIGH);

        return new WifiNetworkSelectionConfig.Builder()
                .setAssociatedNetworkSelectionOverride(
                        WifiNetworkSelectionConfig.ASSOCIATED_NETWORK_SELECTION_OVERRIDE_ENABLED)
                .setSufficiencyCheckEnabledWhenScreenOff(false)
                .setSufficiencyCheckEnabledWhenScreenOn(false)
                .setUserConnectChoiceOverrideEnabled(false)
                .setLastSelectionWeightEnabled(false)
                .setRssiThresholds(ScanResult.WIFI_BAND_24_GHZ, TEST_RSSI2_THRESHOLDS)
                .setRssiThresholds(ScanResult.WIFI_BAND_5_GHZ, TEST_RSSI5_THRESHOLDS)
                .setRssiThresholds(ScanResult.WIFI_BAND_6_GHZ, TEST_RSSI6_THRESHOLDS)
                .setFrequencyWeights(TEST_FREQUENCY_WEIGHTS)
                .build();
    }

    /**
     * Verify the invalid and valid usages of {@code WifiManager#setNetworkSelectionConfig}.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testSetNetworkSelectionConfig() throws Exception {
        AtomicReference<WifiNetworkSelectionConfig> config = new AtomicReference<>();
        Consumer<WifiNetworkSelectionConfig> listener = new Consumer<WifiNetworkSelectionConfig>() {
            @Override
            public void accept(WifiNetworkSelectionConfig value) {
                synchronized (mLock) {
                    config.set(value);
                    mLock.notify();
                }
            }
        };

        // cache current WifiNetworkSelectionConfig
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.getNetworkSelectionConfig(mExecutor, listener));
        synchronized (mLock) {
            mLock.wait(TEST_WAIT_DURATION_MS);
        }
        WifiNetworkSelectionConfig currentConfig = config.get();

        try {
            WifiNetworkSelectionConfig nsConfig = buildTestNetworkSelectionConfig();
            assertTrue(nsConfig.getAssociatedNetworkSelectionOverride()
                    == WifiNetworkSelectionConfig.ASSOCIATED_NETWORK_SELECTION_OVERRIDE_ENABLED);
            assertFalse(nsConfig.isSufficiencyCheckEnabledWhenScreenOff());
            assertFalse(nsConfig.isSufficiencyCheckEnabledWhenScreenOn());
            assertFalse(nsConfig.isUserConnectChoiceOverrideEnabled());
            assertFalse(nsConfig.isLastSelectionWeightEnabled());
            assertArrayEquals(TEST_RSSI2_THRESHOLDS,
                    nsConfig.getRssiThresholds(ScanResult.WIFI_BAND_24_GHZ));
            assertArrayEquals(TEST_RSSI5_THRESHOLDS,
                    nsConfig.getRssiThresholds(ScanResult.WIFI_BAND_5_GHZ));
            assertArrayEquals(TEST_RSSI6_THRESHOLDS,
                    nsConfig.getRssiThresholds(ScanResult.WIFI_BAND_6_GHZ));
            assertTrue(TEST_FREQUENCY_WEIGHTS.contentEquals(nsConfig.getFrequencyWeights()));
            assertThrows(SecurityException.class,
                    () -> sWifiManager.setNetworkSelectionConfig(nsConfig));
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.setNetworkSelectionConfig(nsConfig));
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.setNetworkSelectionConfig(
                            new WifiNetworkSelectionConfig.Builder().build()));
        } finally {
            // restore WifiNetworkSelectionConfig
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.setNetworkSelectionConfig(currentConfig));
        }
    }

    /**
     * Verify the invalid and valid usages of {@code WifiManager#getNetworkSelectionConfig}.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
    @Test
    public void testGetNetworkSelectionConfig() throws Exception {
        AtomicReference<WifiNetworkSelectionConfig> config = new AtomicReference<>();
        Consumer<WifiNetworkSelectionConfig> listener = new Consumer<WifiNetworkSelectionConfig>() {
            @Override
            public void accept(WifiNetworkSelectionConfig value) {
                synchronized (mLock) {
                    config.set(value);
                    mLock.notify();
                }
            }
        };

        // cache current WifiNetworkSelectionConfig
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.getNetworkSelectionConfig(mExecutor, listener));
        synchronized (mLock) {
            mLock.wait(TEST_WAIT_DURATION_MS);
        }
        WifiNetworkSelectionConfig currentConfig = config.get();

        try {
            // Test invalid inputs trigger IllegalArgumentException
            assertThrows("null executor should trigger exception", NullPointerException.class,
                    () -> sWifiManager.getNetworkSelectionConfig(null, listener));
            assertThrows("null listener should trigger exception", NullPointerException.class,
                    () -> sWifiManager.getNetworkSelectionConfig(mExecutor, null));

            // Test caller with no permission triggers SecurityException.
            assertThrows("No permission should trigger SecurityException", SecurityException.class,
                    () -> sWifiManager.getNetworkSelectionConfig(mExecutor, listener));

            // Test get/set WifiNetworkSelectionConfig
            WifiNetworkSelectionConfig nsConfig = buildTestNetworkSelectionConfig();
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.setNetworkSelectionConfig(nsConfig));
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.getNetworkSelectionConfig(mExecutor, listener));
            synchronized (mLock) {
                mLock.wait(TEST_WAIT_DURATION_MS);
            }
            assertTrue(config.get().equals(nsConfig));
        } finally {
            // restore WifiNetworkSelectionConfig
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.setNetworkSelectionConfig(currentConfig));
        }
    }

    /**
     * Verify setting the screen-on connectivity scan delay.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testSetOneShotScreenOnConnectivityScanDelayMillis() {
        assertThrows(SecurityException.class,
                () -> sWifiManager.setOneShotScreenOnConnectivityScanDelayMillis(100));
        assertThrows(IllegalArgumentException.class, () -> {
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.setOneShotScreenOnConnectivityScanDelayMillis(-1));
        });
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setOneShotScreenOnConnectivityScanDelayMillis(10000));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setOneShotScreenOnConnectivityScanDelayMillis(0));
    }

    /**
     * Verify setting the scan schedule.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testSetScreenOnScanSchedule() {
        List<WifiManager.ScreenOnScanSchedule> schedules = new ArrayList<>();
        schedules.add(new WifiManager.ScreenOnScanSchedule(Duration.ofSeconds(20),
                WifiScanner.SCAN_TYPE_HIGH_ACCURACY));
        schedules.add(new WifiManager.ScreenOnScanSchedule(Duration.ofSeconds(40),
                WifiScanner.SCAN_TYPE_LOW_LATENCY));
        assertEquals(20, schedules.get(0).getScanInterval().toSeconds());
        assertEquals(40, schedules.get(1).getScanInterval().toSeconds());
        assertEquals(WifiScanner.SCAN_TYPE_HIGH_ACCURACY, schedules.get(0).getScanType());
        assertEquals(WifiScanner.SCAN_TYPE_LOW_LATENCY, schedules.get(1).getScanType());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScreenOnScanSchedule(schedules));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScreenOnScanSchedule(null));

        // Creating an invalid ScanSchedule should throw an exception
        assertThrows(IllegalArgumentException.class, () -> new WifiManager.ScreenOnScanSchedule(
                null, WifiScanner.SCAN_TYPE_HIGH_ACCURACY));
    }

    /**
     * Verify a normal app cannot set the scan schedule.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testSetScreenOnScanScheduleNoPermission() {
        assertThrows(SecurityException.class, () -> sWifiManager.setScreenOnScanSchedule(null));
    }

    /**
     * Test coverage for the constructor of AddNetworkResult.
     */
    @Test
    public void testAddNetworkResultCreation() {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            // Skip the test if wifi module version is older than S.
            return;
        }
        int statusCode = WifiManager.AddNetworkResult.STATUS_NO_PERMISSION;
        int networkId = 5;
        WifiManager.AddNetworkResult result = new WifiManager.AddNetworkResult(
            statusCode, networkId);
        assertEquals("statusCode should match", statusCode, result.statusCode);
        assertEquals("networkId should match", networkId, result.networkId);
    }

    /**
     * Verify {@link WifiManager#setSsidsAllowlist(Set)} can be called with sufficient
     * privilege.
     */
    @Test
    public void testGetAndSetSsidsAllowlist() {
        Set<WifiSsid> ssids = new ArraySet<>();
        ssids.add(WifiSsid.fromBytes("TEST_SSID_1".getBytes(StandardCharsets.UTF_8)));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setSsidsAllowlist(ssids));

        ShellIdentityUtils.invokeWithShellPermissions(
                () -> assertEquals("Ssids should match", ssids,
                        sWifiManager.getSsidsAllowlist()));

        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setSsidsAllowlist(Collections.EMPTY_SET));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> assertEquals("Should equal to empty set",
                        Collections.EMPTY_SET,
                        sWifiManager.getSsidsAllowlist()));

        try {
            sWifiManager.setSsidsAllowlist(Collections.EMPTY_SET);
            fail("Expected SecurityException when called without permission");
        } catch (SecurityException e) {
            // expect the exception
        }
    }

    class TestPnoScanResultsCallback implements WifiManager.PnoScanResultsCallback {
        public CountDownLatch latch = new CountDownLatch(1);
        private boolean mRegisterSuccess;
        private int mRegisterFailedReason = -1;
        private int mRemovedReason = -1;
        private List<ScanResult> mScanResults;

        @Override
        public void onScanResultsAvailable(List<ScanResult> scanResults) {
            mScanResults = scanResults;
            latch.countDown();
        }

        @Override
        public void onRegisterSuccess() {
            mRegisterSuccess = true;
            latch.countDown();
        }

        @Override
        public void onRegisterFailed(int reason) {
            mRegisterFailedReason = reason;
            latch.countDown();
        }

        @Override
        public void onRemoved(int reason) {
            mRemovedReason = reason;
            latch.countDown();
        }

        public boolean isRegisterSuccess() {
            return mRegisterSuccess;
        }

        public int getRemovedReason() {
            return mRemovedReason;
        }

        public int getRegisterFailedReason() {
            return mRegisterFailedReason;
        }

        public List<ScanResult> getScanResults() {
            return mScanResults;
        }
    }

    /**
     * Verify {@link WifiManager#setExternalPnoScanRequest(List, int[], Executor,
     * WifiManager.PnoScanResultsCallback)} can be called with proper permissions.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testSetExternalPnoScanRequestSuccess() throws Exception {
        TestPnoScanResultsCallback callback = new TestPnoScanResultsCallback();
        List<WifiSsid> ssids = new ArrayList<>();
        ssids.add(WifiSsid.fromBytes("TEST_SSID_1".getBytes(StandardCharsets.UTF_8)));
        int[] frequencies = new int[] {2412, 5180, 5805};

        assertFalse("Callback should be initialized unregistered", callback.isRegisterSuccess());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setExternalPnoScanRequest(
                        ssids, frequencies, Executors.newSingleThreadExecutor(), callback));

        callback.latch.await(TEST_WAIT_DURATION_MS, TimeUnit.MILLISECONDS);
        if (sWifiManager.isPreferredNetworkOffloadSupported()) {
            assertTrue("Expect register success or failed due to resource busy",
                    callback.isRegisterSuccess()
                    || callback.getRegisterFailedReason() == WifiManager.PnoScanResultsCallback
                            .REGISTER_PNO_CALLBACK_RESOURCE_BUSY);
        } else {
            assertEquals("Expect register fail due to not supported.",
                    WifiManager.PnoScanResultsCallback.REGISTER_PNO_CALLBACK_PNO_NOT_SUPPORTED,
                    callback.getRegisterFailedReason());
        }
        sWifiManager.clearExternalPnoScanRequest();
    }

    /**
     * Verify {@link WifiManager#setExternalPnoScanRequest(List, int[], Executor,
     * WifiManager.PnoScanResultsCallback)} can be called with null frequency.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testSetExternalPnoScanRequestSuccessNullFrequency() throws Exception {
        TestPnoScanResultsCallback callback = new TestPnoScanResultsCallback();
        List<WifiSsid> ssids = new ArrayList<>();
        ssids.add(WifiSsid.fromBytes("TEST_SSID_1".getBytes(StandardCharsets.UTF_8)));

        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setExternalPnoScanRequest(
                        ssids, null, Executors.newSingleThreadExecutor(), callback));
        sWifiManager.clearExternalPnoScanRequest();
    }

    /**
     * Verify {@link WifiManager#setExternalPnoScanRequest(List, int[], Executor,
     * WifiManager.PnoScanResultsCallback)} throws an Exception if called with too many SSIDs.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testSetExternalPnoScanRequestTooManySsidsException() throws Exception {
        TestPnoScanResultsCallback callback = new TestPnoScanResultsCallback();
        List<WifiSsid> ssids = new ArrayList<>();
        ssids.add(WifiSsid.fromBytes("TEST_SSID_1".getBytes(StandardCharsets.UTF_8)));
        ssids.add(WifiSsid.fromBytes("TEST_SSID_2".getBytes(StandardCharsets.UTF_8)));
        ssids.add(WifiSsid.fromBytes("TEST_SSID_3".getBytes(StandardCharsets.UTF_8)));

        assertFalse("Callback should be initialized unregistered", callback.isRegisterSuccess());
        assertThrows(IllegalArgumentException.class, () -> {
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.setExternalPnoScanRequest(
                            ssids, null, Executors.newSingleThreadExecutor(), callback));
        });
    }

    /**
     * Verify {@link WifiManager#setExternalPnoScanRequest(List, int[], Executor,
     * WifiManager.PnoScanResultsCallback)} throws an Exception if called with too many frequencies.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testSetExternalPnoScanRequestTooManyFrequenciesException() throws Exception {
        TestPnoScanResultsCallback callback = new TestPnoScanResultsCallback();
        List<WifiSsid> ssids = new ArrayList<>();
        ssids.add(WifiSsid.fromBytes("TEST_SSID_1".getBytes(StandardCharsets.UTF_8)));
        int[] frequencies = new int[] {2412, 2417, 2422, 2427, 2432, 2437, 2447, 2452, 2457, 2462,
                5180, 5200, 5220, 5240, 5745, 5765, 5785, 5805};

        assertFalse("Callback should be initialized unregistered", callback.isRegisterSuccess());
        assertThrows(IllegalArgumentException.class, () -> {
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.setExternalPnoScanRequest(
                            ssids, frequencies, Executors.newSingleThreadExecutor(), callback));
        });
    }

    /**
     * Verify {@link WifiManager#setExternalPnoScanRequest(List, int[], Executor,
     * WifiManager.PnoScanResultsCallback)} cannot be called without permission.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testSetExternalPnoScanRequestNoPermission() throws Exception {
        TestExecutor executor = new TestExecutor();
        TestPnoScanResultsCallback callback = new TestPnoScanResultsCallback();
        List<WifiSsid> ssids = new ArrayList<>();
        ssids.add(WifiSsid.fromBytes("TEST_SSID_1".getBytes(StandardCharsets.UTF_8)));

        assertFalse("Callback should be initialized unregistered", callback.isRegisterSuccess());
        assertThrows(SecurityException.class,
                () -> sWifiManager.setExternalPnoScanRequest(ssids, null, executor, callback));
    }

    /**
     * Verify the invalid and valid usages of {@code WifiManager#getLastCallerInfoForApi}.
     */
    @Test
    public void testGetLastCallerInfoForApi() throws Exception {
        AtomicReference<String> packageName = new AtomicReference<>();
        AtomicBoolean enabled = new AtomicBoolean(false);
        BiConsumer<String, Boolean> listener = new BiConsumer<String, Boolean>() {
            @Override
            public void accept(String caller, Boolean value) {
                synchronized (mLock) {
                    packageName.set(caller);
                    enabled.set(value);
                    mLock.notify();
                }
            }
        };
        // Test invalid inputs trigger IllegalArgumentException
        assertThrows("Invalid apiType should trigger exception", IllegalArgumentException.class,
                () -> sWifiManager.getLastCallerInfoForApi(-1, mExecutor, listener));
        assertThrows("null executor should trigger exception", IllegalArgumentException.class,
                () -> sWifiManager.getLastCallerInfoForApi(WifiManager.API_SOFT_AP, null,
                        listener));
        assertThrows("null listener should trigger exception", IllegalArgumentException.class,
                () -> sWifiManager.getLastCallerInfoForApi(WifiManager.API_SOFT_AP, mExecutor,
                        null));

        // Test caller with no permission triggers SecurityException.
        assertThrows("No permission should trigger SecurityException", SecurityException.class,
                () -> sWifiManager.getLastCallerInfoForApi(WifiManager.API_SOFT_AP,
                        mExecutor, listener));

        String expectedPackage = "android.net.wifi.cts";
        boolean isEnabledBefore = sWifiManager.isWifiEnabled();
        // toggle wifi and verify getting last caller
        setWifiEnabled(!isEnabledBefore);
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.getLastCallerInfoForApi(WifiManager.API_WIFI_ENABLED, mExecutor,
                        listener));
        synchronized (mLock) {
            mLock.wait(TEST_WAIT_DURATION_MS);
        }

        assertEquals("package does not match", expectedPackage, packageName.get());
        assertEquals("enabled does not match", !isEnabledBefore, enabled.get());

        // toggle wifi again and verify last caller
        packageName.set(null);
        setWifiEnabled(isEnabledBefore);
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.getLastCallerInfoForApi(WifiManager.API_WIFI_ENABLED, mExecutor,
                        listener));
        synchronized (mLock) {
            mLock.wait(TEST_WAIT_DURATION_MS);
        }
        assertEquals("package does not match", expectedPackage, packageName.get());
        assertEquals("enabled does not match", isEnabledBefore, enabled.get());
    }

    /**
     * Verify that {@link WifiManager#addNetworkPrivileged(WifiConfiguration)} throws a
     * SecurityException when called by a normal app.
     */
    @Test
    public void testAddNetworkPrivilegedNotAllowedForNormalApps() {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            // Skip the test if wifi module version is older than S.
            return;
        }
        try {
            WifiConfiguration newOpenNetwork = new WifiConfiguration();
            newOpenNetwork.SSID = "\"" + TEST_SSID_UNQUOTED + "\"";
            sWifiManager.addNetworkPrivileged(newOpenNetwork);
            fail("A normal app should not be able to call this API.");
        } catch (SecurityException e) {
        }
    }

    /**
     * Verify {@link WifiManager#addNetworkPrivileged(WifiConfiguration)} throws an exception when
     * null is the input.
     */
    @Test
    public void testAddNetworkPrivilegedBadInput() {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            // Skip the test if wifi module version is older than S.
            return;
        }
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiManager.addNetworkPrivileged(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Verify {@link WifiManager#getPrivilegedConnectedNetwork()} returns the currently
     * connected WifiConfiguration with randomized MAC address filtered out.
     */
    @Test
    public void testGetPrivilegedConnectedNetworkSuccess() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiManager.startScan();

            WifiInfo wifiInfo = sWifiManager.getConnectionInfo();
            int curNetworkId = wifiInfo.getNetworkId();
            assertNotEquals("Should be connected to valid networkId", INVALID_NETWORK_ID,
                    curNetworkId);
            WifiConfiguration curConfig = sWifiManager.getPrivilegedConnectedNetwork();
            assertEquals("NetworkId should match", curNetworkId, curConfig.networkId);
            assertEquals("SSID should match", wifiInfo.getSSID(), curConfig.SSID);
            assertEquals("Randomized MAC should be filtered out", WifiInfo.DEFAULT_MAC_ADDRESS,
                    curConfig.getRandomizedMacAddress().toString());
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Verify {@link WifiManager#addNetworkPrivileged(WifiConfiguration)} works properly when the
     * calling app has permissions.
     */
    @Test
    public void testAddNetworkPrivilegedSuccess() {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            // Skip the test if wifi module version is older than S.
            return;
        }
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        WifiManager.AddNetworkResult result = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            WifiConfiguration newOpenNetwork = new WifiConfiguration();
            newOpenNetwork.SSID = "\"" + TEST_SSID_UNQUOTED + "\"";
            result = sWifiManager.addNetworkPrivileged(newOpenNetwork);
            assertEquals(WifiManager.AddNetworkResult.STATUS_SUCCESS, result.statusCode);
            assertTrue(result.networkId >= 0);
            List<WifiConfiguration> configuredNetworks = sWifiManager.getConfiguredNetworks();
            boolean found = false;
            for (WifiConfiguration config : configuredNetworks) {
                if (config.networkId == result.networkId
                        && config.SSID.equals(newOpenNetwork.SSID)) {
                    found = true;
                    break;
                }
            }
            assertTrue("addNetworkPrivileged returns success"
                    + "but the network is not found in getConfiguredNetworks", found);

            List<WifiConfiguration> privilegedConfiguredNetworks =
                    sWifiManager.getPrivilegedConfiguredNetworks();
            found = false;
            for (WifiConfiguration config : privilegedConfiguredNetworks) {
                if (config.networkId == result.networkId
                        && config.SSID.equals(newOpenNetwork.SSID)) {
                    found = true;
                    break;
                }
            }
            assertTrue("addNetworkPrivileged returns success"
                    + "but the network is not found in getPrivilegedConfiguredNetworks", found);

            List<WifiConfiguration> callerConfiguredNetworks =
                    sWifiManager.getCallerConfiguredNetworks();
            found = false;
            for (WifiConfiguration config : callerConfiguredNetworks) {
                if (config.networkId == result.networkId
                        && config.SSID.equals(newOpenNetwork.SSID)) {
                    found = true;
                    break;
                }
            }
            assertTrue("addNetworkPrivileged returns success"
                    + "but the network is not found in getCallerConfiguredNetworks", found);
        } finally {
            if (null != result) {
                sWifiManager.removeNetwork(result.networkId);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private WifiConfiguration createConfig(
            String ssid, int type) {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + ssid + "\"";
        config.setSecurityParams(type);
        // set necessary fields for different types.
        switch (type) {
            case WifiConfiguration.SECURITY_TYPE_OPEN:
            case WifiConfiguration.SECURITY_TYPE_OWE:
                break;
            case WifiConfiguration.SECURITY_TYPE_PSK:
            case WifiConfiguration.SECURITY_TYPE_SAE:
                config.preSharedKey = "\"1qaz@WSX\"";
                break;
            case WifiConfiguration.SECURITY_TYPE_EAP:
            case WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE:
            case WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT:
                config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
                break;
        }
        return config;
    }

    private void assertConfigsAreFound(
            List<WifiConfiguration> expectedConfigs,
            List<WifiConfiguration> configs) {
        for (WifiConfiguration expectedConfig: expectedConfigs) {
            boolean found = false;
            for (WifiConfiguration config : configs) {
                if (config.networkId == expectedConfig.networkId
                        && config.getKey().equals(expectedConfig.getKey())) {
                    found = true;
                    break;
                }
            }
            assertTrue("the network " + expectedConfig.getKey() + " is not found", found);
        }
    }

    /**
     * Verify {@link WifiManager#addNetworkPrivileged(WifiConfiguration)} works
     * with merging types properly when the calling app has permissions.
     */
    @Test
    public void testAddNetworkPrivilegedMergingTypeSuccess() {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            // Skip the test if wifi module version is older than S.
            return;
        }
        List<WifiConfiguration> baseConfigs = new ArrayList<>();
        baseConfigs.add(createConfig("test-open-owe-jdur", WifiConfiguration.SECURITY_TYPE_OPEN));
        baseConfigs.add(createConfig("test-psk-sae-ijfe", WifiConfiguration.SECURITY_TYPE_PSK));
        baseConfigs.add(createConfig("test-wpa2e-wpa3e-plki",
                WifiConfiguration.SECURITY_TYPE_EAP));
        List<WifiConfiguration> upgradeConfigs = new ArrayList<>();
        upgradeConfigs.add(createConfig("test-open-owe-jdur", WifiConfiguration.SECURITY_TYPE_OWE));
        upgradeConfigs.add(createConfig("test-psk-sae-ijfe", WifiConfiguration.SECURITY_TYPE_SAE));
        upgradeConfigs.add(createConfig("test-wpa2e-wpa3e-plki",
                WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE));
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            final int originalConfiguredNetworksNumber =
                    sWifiManager.getConfiguredNetworks().size();
            final int originalPrivilegedConfiguredNetworksNumber =
                    sWifiManager.getPrivilegedConfiguredNetworks().size();
            final int originalCallerConfiguredNetworksNumber =
                    sWifiManager.getCallerConfiguredNetworks().size();
            for (WifiConfiguration c: baseConfigs) {
                WifiManager.AddNetworkResult result = sWifiManager.addNetworkPrivileged(c);
                assertEquals(WifiManager.AddNetworkResult.STATUS_SUCCESS, result.statusCode);
                assertTrue(result.networkId >= 0);
                c.networkId = result.networkId;
            }
            for (WifiConfiguration c: upgradeConfigs) {
                WifiManager.AddNetworkResult result = sWifiManager.addNetworkPrivileged(c);
                assertEquals(WifiManager.AddNetworkResult.STATUS_SUCCESS, result.statusCode);
                assertTrue(result.networkId >= 0);
                c.networkId = result.networkId;
            }
            // open/owe, psk/sae, and wpa2e/wpa3e should be merged
            // so they should have the same network ID.
            for (int i = 0; i < baseConfigs.size(); i++) {
                assertEquals(baseConfigs.get(i).networkId, upgradeConfigs.get(i).networkId);
            }

            int numAddedConfigs = baseConfigs.size();
            List<WifiConfiguration> expectedConfigs = new ArrayList<>(baseConfigs);
            if (SdkLevel.isAtLeastS()) {
                // S devices and above will return one additional config per each security type
                // added, so we include the number of both base and upgrade configs.
                numAddedConfigs += upgradeConfigs.size();
                expectedConfigs.addAll(upgradeConfigs);
            }
            List<WifiConfiguration> configuredNetworks = sWifiManager.getConfiguredNetworks();
            assertEquals(originalConfiguredNetworksNumber + numAddedConfigs,
                    configuredNetworks.size());
            assertConfigsAreFound(expectedConfigs, configuredNetworks);

            List<WifiConfiguration> privilegedConfiguredNetworks =
                    sWifiManager.getPrivilegedConfiguredNetworks();
            assertEquals(originalPrivilegedConfiguredNetworksNumber + numAddedConfigs,
                    privilegedConfiguredNetworks.size());
            assertConfigsAreFound(expectedConfigs, privilegedConfiguredNetworks);

            List<WifiConfiguration> callerConfiguredNetworks =
                    sWifiManager.getCallerConfiguredNetworks();
            assertEquals(originalCallerConfiguredNetworksNumber + numAddedConfigs,
                    callerConfiguredNetworks.size());
            assertConfigsAreFound(expectedConfigs, callerConfiguredNetworks);

        } finally {
            for (WifiConfiguration c: baseConfigs) {
                if (c.networkId >= 0) {
                    sWifiManager.removeNetwork(c.networkId);
                }
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Verify that applications can only have one registered LocalOnlyHotspot request at a time.
     *
     * Note: Location mode must be enabled for this test.
     */
    @Test
    public void testStartLocalOnlyHotspotSingleRequestByApps() throws Exception {
        // check that softap mode is supported by the device
        assumeTrue(sWifiManager.isPortableHotspotSupported());


        runWithScanning(() -> {
            boolean caughtException = false;
            boolean wifiEnabled = sWifiManager.isWifiEnabled();
            if (wifiEnabled) {
                // Re-enabled Wi-Fi as shell for HalDeviceManager legacy LOHS behavior when there's
                // no STA+AP concurrency.
                ShellIdentityUtils.invokeWithShellPermissions(() ->
                        sWifiManager.setWifiEnabled(false));
                PollingCheck.check("Wifi turn off failed!", WIFI_OFF_ON_TIMEOUT_MILLIS,
                        () -> !sWifiManager.isWifiEnabled());
                SystemUtil.runShellCommand("cmd wifi set-wifi-enabled enabled");
                PollingCheck.check("Wifi turn on failed!", WIFI_OFF_ON_TIMEOUT_MILLIS,
                        () -> sWifiManager.isWifiEnabled());
            }

            TestLocalOnlyHotspotCallback callback = startLocalOnlyHotspot();

            // now make a second request - this should fail.
            TestLocalOnlyHotspotCallback callback2 = new TestLocalOnlyHotspotCallback(mLock);
            try {
                sWifiManager.startLocalOnlyHotspot(callback2, null);
            } catch (IllegalStateException e) {
                Log.d(TAG, "Caught the IllegalStateException we expected: called startLOHS twice");
                caughtException = true;
            }
            if (!caughtException) {
                // second start did not fail, should clean up the hotspot.

                // add sleep to avoid calling stopLocalOnlyHotspot before TetherController
                // initialization.
                // TODO: remove this sleep as soon as b/124330089 is fixed.
                Log.d(TAG, "Sleeping for 2 seconds");
                Thread.sleep(2000);

                stopLocalOnlyHotspot(callback2, wifiEnabled);
            }
            assertTrue(caughtException);

            // add sleep to avoid calling stopLocalOnlyHotspot before TetherController
            // initialization.
            // TODO: remove this sleep as soon as b/124330089 is fixed.
            Log.d(TAG, "Sleeping for 2 seconds");
            Thread.sleep(2000);

            stopLocalOnlyHotspot(callback, wifiEnabled);
        }, false);
    }

    private static class TestExecutor implements Executor {
        private ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        private void runAll() {
            Runnable task = tasks.poll();
            while (task != null) {
                task.run();
                task = tasks.poll();
            }
        }
    }

    private SoftApConfiguration.Builder generateSoftApConfigBuilderWithSsid(String ssid) {
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            return new SoftApConfiguration.Builder().setWifiSsid(
                    WifiSsid.fromBytes(ssid.getBytes(StandardCharsets.UTF_8)));
        }
        return new SoftApConfiguration.Builder().setSsid(ssid);
    }

    private void assertSsidEquals(SoftApConfiguration config, String expectedSsid) {
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            assertEquals(WifiSsid.fromBytes(expectedSsid.getBytes(StandardCharsets.UTF_8)),
                    config.getWifiSsid());
        } else {
            assertEquals(expectedSsid, config.getSsid());
        }
    }

    private void unregisterLocalOnlyHotspotSoftApCallback(TestSoftApCallback lohsSoftApCallback) {
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            sWifiManager.unregisterLocalOnlyHotspotSoftApCallback(lohsSoftApCallback);
        } else {
            sWifiManager.unregisterSoftApCallback(lohsSoftApCallback);
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testStartLocalOnlyHotspotWithSupportedBand() throws Exception {
        // check that softap mode is supported by the device
        if (!sWifiManager.isPortableHotspotSupported()) {
            return;
        }

        TestExecutor executor = new TestExecutor();
        TestSoftApCallback lohsSoftApCallback = new TestSoftApCallback(mLock);
        setWifiEnabled(false);
        Thread.sleep(TEST_WAIT_DURATION_MS);
        boolean wifiEnabled = sWifiManager.isWifiEnabled();
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            verifyLohsRegisterSoftApCallback(executor, lohsSoftApCallback);
            SoftApConfiguration.Builder customConfigBuilder =
                    generateSoftApConfigBuilderWithSsid(TEST_SSID_UNQUOTED)
                    .setPassphrase(TEST_PASSPHRASE, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);

            SparseIntArray testBandsAndChannels = getAvailableBandAndChannelForTesting(
                    lohsSoftApCallback.getCurrentSoftApCapability());
            // The devices which doesn't have SIM and default country code in system property
            // (ro.boot.wificountrycodeCountry) will return a null country code. Since country code
            // is mandatory for 5GHz/6GHz band, skip the softap operation on 5GHz & 6GHz only band.
            boolean skip5g6gBand = false;
            String wifiCountryCode = ShellIdentityUtils.invokeWithShellPermissions(
                    sWifiManager::getCountryCode);
            if (wifiCountryCode == null) {
                skip5g6gBand = true;
                Log.e(TAG, "Country Code is not available - Skip 5GHz and 6GHz test");
            }
            for (int i = 0; i < testBandsAndChannels.size(); i++) {
                TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback(mLock);
                int testBand = testBandsAndChannels.keyAt(i);
                if (skip5g6gBand && (testBand == SoftApConfiguration.BAND_6GHZ
                        || testBand == SoftApConfiguration.BAND_5GHZ)) {
                    continue;
                }
                // WPA2_PSK is not allowed in 6GHz band. So test with WPA3_SAE which is
                // mandatory to support in 6GHz band.
                if (testBand == SoftApConfiguration.BAND_6GHZ) {
                    if (lohsSoftApCallback.getCurrentSoftApCapability()
                            .areFeaturesSupported(SoftApCapability.SOFTAP_FEATURE_WPA3_SAE)) {
                        customConfigBuilder.setPassphrase(TEST_PASSPHRASE,
                            SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
                    } else {
                        Log.e(TAG, "SoftAp 6GHz capability is advertized without WPA3 support");
                        continue;
                    }
                }
                customConfigBuilder.setBand(testBand);
                sWifiManager.startLocalOnlyHotspot(customConfigBuilder.build(), executor, callback);
                // now wait for callback
                Thread.sleep(DURATION_SOFTAP_START_MS);

                // Verify callback is run on the supplied executor
                assertFalse(callback.onStartedCalled);
                executor.runAll();
                assertTrue(callback.onStartedCalled);
                assertNotNull(callback.reservation);
                SoftApConfiguration softApConfig = callback.reservation.getSoftApConfiguration();
                assertEquals(
                        WifiSsid.fromBytes(TEST_SSID_UNQUOTED.getBytes(StandardCharsets.UTF_8)),
                        softApConfig.getWifiSsid());
                assertEquals(TEST_PASSPHRASE, softApConfig.getPassphrase());
                // Automotive mode can force the LOHS to specific bands
                if (!hasAutomotiveFeature()) {
                    assertEquals(testBand, softApConfig.getBand());
                }
                if (lohsSoftApCallback.getOnSoftapInfoChangedCalledCount() > 1) {
                    assertTrue(lohsSoftApCallback.getCurrentSoftApInfo().getFrequency() > 0);
                }
                stopLocalOnlyHotspot(callback, wifiEnabled);
            }
        } finally {
            // clean up
            sWifiManager.unregisterSoftApCallback(lohsSoftApCallback);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testStartLocalOnlyHotspotWithConfigBssid() throws Exception {
        // check that softap mode is supported by the device
        if (!sWifiManager.isPortableHotspotSupported()) {
            return;
        }

        TestExecutor executor = new TestExecutor();
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback(mLock);
        TestSoftApCallback lohsSoftApCallback = new TestSoftApCallback(mLock);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        boolean wifiEnabled = sWifiManager.isWifiEnabled();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            verifyLohsRegisterSoftApCallback(executor, lohsSoftApCallback);
            SoftApConfiguration.Builder customConfigBuilder =
                    generateSoftApConfigBuilderWithSsid(TEST_SSID_UNQUOTED)
                    .setPassphrase(TEST_PASSPHRASE, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);

            boolean isSupportCustomizedMac = lohsSoftApCallback.getCurrentSoftApCapability()
                        .areFeaturesSupported(
                        SoftApCapability.SOFTAP_FEATURE_MAC_ADDRESS_CUSTOMIZATION)
                    && PropertyUtil.isVndkApiLevelNewerThan(Build.VERSION_CODES.S);
            if (isSupportCustomizedMac) {
                customConfigBuilder.setBssid(TEST_MAC).setMacRandomizationSetting(
                            SoftApConfiguration.RANDOMIZATION_NONE);
            }
            SoftApConfiguration customConfig = customConfigBuilder.build();

            sWifiManager.startLocalOnlyHotspot(customConfig, executor, callback);
            // now wait for callback
            Thread.sleep(TEST_WAIT_DURATION_MS);

            // Verify callback is run on the supplied executor
            assertFalse(callback.onStartedCalled);
            executor.runAll();
            assertTrue(callback.onStartedCalled);

            assertNotNull(callback.reservation);
            SoftApConfiguration softApConfig = callback.reservation.getSoftApConfiguration();
            assertNotNull(softApConfig);
            if (isSupportCustomizedMac) {
                assertEquals(TEST_MAC, softApConfig.getBssid());
            }
            assertSsidEquals(softApConfig, TEST_SSID_UNQUOTED);
            assertEquals(TEST_PASSPHRASE, softApConfig.getPassphrase());
        } finally {
            // clean up
            stopLocalOnlyHotspot(callback, wifiEnabled);
            unregisterLocalOnlyHotspotSoftApCallback(lohsSoftApCallback);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testStartLocalOnlyHotspotWithNullBssidConfig() throws Exception {
        // check that softap mode is supported by the device
        if (!sWifiManager.isPortableHotspotSupported()) {
            return;
        }
        SoftApConfiguration customConfig =
                generateSoftApConfigBuilderWithSsid(TEST_SSID_UNQUOTED)
                .setPassphrase(TEST_PASSPHRASE, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .build();

        TestExecutor executor = new TestExecutor();
        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback(mLock);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        boolean wifiEnabled = sWifiManager.isWifiEnabled();
        try {
            uiAutomation.adoptShellPermissionIdentity();

            sWifiManager.startLocalOnlyHotspot(customConfig, executor, callback);
            // now wait for callback
            Thread.sleep(TEST_WAIT_DURATION_MS);

            // Verify callback is run on the supplied executor
            assertFalse(callback.onStartedCalled);
            executor.runAll();
            assertTrue(callback.onStartedCalled);

            assertNotNull(callback.reservation);
            SoftApConfiguration softApConfig = callback.reservation.getSoftApConfiguration();
            assertNotNull(softApConfig);
            assertSsidEquals(softApConfig, TEST_SSID_UNQUOTED);
            assertEquals(TEST_PASSPHRASE, softApConfig.getPassphrase());
        } finally {
            // clean up
            stopLocalOnlyHotspot(callback, wifiEnabled);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Read the content of the given resource file into a String.
     *
     * @param filename String name of the file
     * @return String
     * @throws IOException
     */
    private String loadResourceFile(String filename) throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream(filename);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append("\n");
        }
        return builder.toString();
    }

    /**
     * Verify that changing the mac randomization setting of a Passpoint configuration.
     */
    @Test
    public void testMacRandomizationSettingPasspoint() throws Exception {
        String configStr = loadResourceFile(PASSPOINT_INSTALLATION_FILE_WITH_CA_CERT);
        PasspointConfiguration config =
                ConfigParser.parsePasspointConfig(TYPE_WIFI_CONFIG, configStr.getBytes());
        String fqdn = config.getHomeSp().getFqdn();
        String uniqueId = config.getUniqueId();
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();

            sWifiManager.addOrUpdatePasspointConfiguration(config);
            PasspointConfiguration passpointConfig = getTargetPasspointConfiguration(
                    sWifiManager.getPasspointConfigurations(), uniqueId);
            assertNotNull("The installed passpoint profile is missing", passpointConfig);
            assertTrue("Mac randomization should be enabled for passpoint networks by default.",
                    passpointConfig.isMacRandomizationEnabled());

            sWifiManager.setMacRandomizationSettingPasspointEnabled(fqdn, false);
            passpointConfig = getTargetPasspointConfiguration(
                    sWifiManager.getPasspointConfigurations(), uniqueId);
            assertNotNull("The installed passpoint profile is missing", passpointConfig);
            assertFalse("Mac randomization should be disabled by the API call.",
                    passpointConfig.isMacRandomizationEnabled());
        } finally {
            // Clean up
            sWifiManager.removePasspointConfiguration(fqdn);
            uiAutomation.dropShellPermissionIdentity();
        }
    }
    /**
     * Verify that the {@link android.Manifest.permission#NETWORK_STACK} permission is never held by
     * any package.
     * <p>
     * No apps should <em>ever</em> attempt to acquire this permission, since it would give those
     * apps extremely broad access to connectivity functionality.
     */
    @Test
    public void testNetworkStackPermission() {
        final PackageManager pm = sContext.getPackageManager();

        final List<PackageInfo> holding = pm.getPackagesHoldingPermissions(new String[] {
                android.Manifest.permission.NETWORK_STACK
        }, PackageManager.MATCH_UNINSTALLED_PACKAGES);
        for (PackageInfo pi : holding) {
            fail("The NETWORK_STACK permission must not be held by " + pi.packageName
                    + " and must be revoked for security reasons");
        }
    }

    /**
     * Verify that the {@link android.Manifest.permission#NETWORK_SETTINGS} permission is
     * never held by any package.
     * <p>
     * Only Settings, SysUi, NetworkStack and shell apps should <em>ever</em> attempt to acquire
     * this permission, since it would give those apps extremely broad access to connectivity
     * functionality.  The permission is intended to be granted to only those apps with direct user
     * access and no others.
     */
    @Test
    public void testNetworkSettingsPermission() {
        final PackageManager pm = sContext.getPackageManager();

        final ArraySet<String> allowedPackages = new ArraySet();
        final ArraySet<Integer> allowedUIDs = new ArraySet();
        // explicitly add allowed UIDs
        allowedUIDs.add(Process.SYSTEM_UID);
        allowedUIDs.add(Process.SHELL_UID);
        allowedUIDs.add(Process.PHONE_UID);
        allowedUIDs.add(Process.NETWORK_STACK_UID);
        allowedUIDs.add(Process.NFC_UID);

        // only quick settings is allowed to bind to the BIND_QUICK_SETTINGS_TILE permission, using
        // this fact to determined allowed package name for sysui. This is a signature permission,
        // so allow any package with this permission.
        final List<PackageInfo> sysuiPackages = pm.getPackagesHoldingPermissions(new String[] {
                android.Manifest.permission.BIND_QUICK_SETTINGS_TILE
        }, PackageManager.MATCH_UNINSTALLED_PACKAGES);
        for (PackageInfo info : sysuiPackages) {
            allowedPackages.add(info.packageName);
        }

        // the captive portal flow also currently holds the NETWORK_SETTINGS permission
        final Intent intent = new Intent(ConnectivityManager.ACTION_CAPTIVE_PORTAL_SIGN_IN);
        final ResolveInfo ri = pm.resolveActivity(intent, PackageManager.MATCH_DISABLED_COMPONENTS);
        if (ri != null) {
            allowedPackages.add(ri.activityInfo.packageName);
        }

        final List<PackageInfo> holding = pm.getPackagesHoldingPermissions(new String[] {
                android.Manifest.permission.NETWORK_SETTINGS
        }, PackageManager.MATCH_UNINSTALLED_PACKAGES);
        StringBuilder stringBuilder = new StringBuilder();
        for (PackageInfo pi : holding) {
            String packageName = pi.packageName;

            // this is an explicitly allowed package
            if (allowedPackages.contains(packageName)) continue;

            // now check if the packages are from allowed UIDs
            int uid = -1;
            try {
                uid = pm.getPackageUidAsUser(packageName, UserHandle.USER_SYSTEM);
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            if (!allowedUIDs.contains(uid)) {
                stringBuilder.append("The NETWORK_SETTINGS permission must not be held by "
                    + packageName + ":" + uid + " and must be revoked for security reasons\n");
            }
        }
        if (stringBuilder.length() > 0) {
            fail(stringBuilder.toString());
        }
    }

    /**
     * Verify that the {@link android.Manifest.permission#NETWORK_SETUP_WIZARD} permission is
     * only held by the device setup wizard application.
     * <p>
     * Only the SetupWizard app should <em>ever</em> attempt to acquire this
     * permission, since it would give those apps extremely broad access to connectivity
     * functionality.  The permission is intended to be granted to only the device setup wizard.
     */
    @Test
    public void testNetworkSetupWizardPermission() {
        final ArraySet<String> allowedPackages = new ArraySet();

        final PackageManager pm = sContext.getPackageManager();

        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_SETUP_WIZARD);
        final ResolveInfo ri = pm.resolveActivity(intent, PackageManager.MATCH_DISABLED_COMPONENTS);
        String validPkg = "";
        if (ri != null) {
            allowedPackages.add(ri.activityInfo.packageName);
            validPkg = ri.activityInfo.packageName;
        }

        final Intent preIntent = new Intent("com.android.setupwizard.OEM_PRE_SETUP");
        preIntent.addCategory(Intent.CATEGORY_DEFAULT);
        final ResolveInfo preRi = pm
            .resolveActivity(preIntent, PackageManager.MATCH_DISABLED_COMPONENTS);
        String prePackageName = "";
        if (null != preRi) {
            prePackageName = preRi.activityInfo.packageName;
        }

        final Intent postIntent = new Intent("com.android.setupwizard.OEM_POST_SETUP");
        postIntent.addCategory(Intent.CATEGORY_DEFAULT);
        final ResolveInfo postRi = pm
            .resolveActivity(postIntent, PackageManager.MATCH_DISABLED_COMPONENTS);
        String postPackageName = "";
        if (null != postRi) {
            postPackageName = postRi.activityInfo.packageName;
        }
        if (!TextUtils.isEmpty(prePackageName) && !TextUtils.isEmpty(postPackageName)
            && prePackageName.equals(postPackageName)) {
            allowedPackages.add(prePackageName);
        }

        final List<PackageInfo> holding = pm.getPackagesHoldingPermissions(new String[]{
            android.Manifest.permission.NETWORK_SETUP_WIZARD
        }, PackageManager.MATCH_UNINSTALLED_PACKAGES);
        for (PackageInfo pi : holding) {
            if (!allowedPackages.contains(pi.packageName)) {
                fail("The NETWORK_SETUP_WIZARD permission must not be held by " + pi.packageName
                    + " and must be revoked for security reasons"
                    + " | validPkg=" + validPkg);
            }
        }
    }

    /**
     * Verify that the {@link android.Manifest.permission#NETWORK_MANAGED_PROVISIONING} permission
     * is only held by the device managed provisioning application.
     * <p>
     * Only the ManagedProvisioning app should <em>ever</em> attempt to acquire this
     * permission, since it would give those apps extremely broad access to connectivity
     * functionality.  The permission is intended to be granted to only the device managed
     * provisioning.
     */
    @Test
    public void testNetworkManagedProvisioningPermission() {
        final PackageManager pm = sContext.getPackageManager();

        // TODO(b/115980767): Using hardcoded package name. Need a better mechanism to find the
        // managed provisioning app.
        // Ensure that the package exists.
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setPackage(MANAGED_PROVISIONING_PACKAGE_NAME);
        final ResolveInfo ri = pm.resolveActivity(intent, PackageManager.MATCH_DISABLED_COMPONENTS);
        String validPkg = "";
        if (ri != null) {
            validPkg = ri.activityInfo.packageName;
        }
        String dpmHolderName = null;
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            DevicePolicyManager dpm = sContext.getSystemService(DevicePolicyManager.class);
            if (dpm != null) {
                dpmHolderName = dpm.getDevicePolicyManagementRoleHolderPackage();
            }
        }

        final List<PackageInfo> holding = pm.getPackagesHoldingPermissions(new String[] {
                android.Manifest.permission.NETWORK_MANAGED_PROVISIONING
        }, PackageManager.MATCH_UNINSTALLED_PACKAGES);
        for (PackageInfo pi : holding) {
            if (!Objects.equals(pi.packageName, validPkg)
                    && !Objects.equals(pi.packageName, dpmHolderName)) {
                fail("The NETWORK_MANAGED_PROVISIONING permission must not be held by "
                        + pi.packageName + " and must be revoked for security reasons ["
                        + validPkg + ", " + dpmHolderName + "]");
            }
        }
    }

    /**
     * Verify that the {@link android.Manifest.permission#WIFI_SET_DEVICE_MOBILITY_STATE} permission
     * is held by at most one application.
     */
    @Test
    public void testWifiSetDeviceMobilityStatePermission() {
        final PackageManager pm = sContext.getPackageManager();

        final List<PackageInfo> holding = pm.getPackagesHoldingPermissions(new String[] {
                android.Manifest.permission.WIFI_SET_DEVICE_MOBILITY_STATE
        }, PackageManager.MATCH_UNINSTALLED_PACKAGES);

        List<String> uniquePackageNames = holding
                .stream()
                .map(pi -> pi.packageName)
                .distinct()
                .collect(Collectors.toList());

        if (uniquePackageNames.size() > 1) {
            fail("The WIFI_SET_DEVICE_MOBILITY_STATE permission must not be held by more than one "
                    + "application, but is held by " + uniquePackageNames.size() + " applications: "
                    + String.join(", ", uniquePackageNames));
        }
    }

    /**
     * Verify that the {@link android.Manifest.permission#NETWORK_CARRIER_PROVISIONING} permission
     * is held by at most one application.
     */
    @Test
    public void testNetworkCarrierProvisioningPermission() {
        final PackageManager pm = sContext.getPackageManager();

        final List<PackageInfo> holding = pm.getPackagesHoldingPermissions(new String[] {
                android.Manifest.permission.NETWORK_CARRIER_PROVISIONING
        }, PackageManager.MATCH_UNINSTALLED_PACKAGES);

        List<String> uniquePackageNames = holding
                .stream()
                .map(pi -> pi.packageName)
                .distinct()
                .collect(Collectors.toList());

        if (uniquePackageNames.size() > 2) {
            fail("The NETWORK_CARRIER_PROVISIONING permission must not be held by more than two "
                    + "applications, but is held by " + uniquePackageNames.size() + " applications: "
                    + String.join(", ", uniquePackageNames));
        }
    }

    /**
     * Verify that the {@link android.Manifest.permission#WIFI_UPDATE_USABILITY_STATS_SCORE}
     * permission is held by at most two applications.
     */
    @Test
    public void testUpdateWifiUsabilityStatsScorePermission() {
        final PackageManager pm = sContext.getPackageManager();

        final List<PackageInfo> holding = pm.getPackagesHoldingPermissions(new String[] {
                android.Manifest.permission.WIFI_UPDATE_USABILITY_STATS_SCORE
        }, PackageManager.MATCH_UNINSTALLED_PACKAGES);

        Set<String> uniqueNonSystemPackageNames = new HashSet<>();
        for (PackageInfo pi : holding) {
            String packageName = pi.packageName;
            // Shell is allowed to hold this permission for testing.
            int uid = -1;
            try {
                uid = pm.getPackageUidAsUser(packageName, UserHandle.USER_SYSTEM);
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            if (uid == Process.SHELL_UID) continue;

            uniqueNonSystemPackageNames.add(packageName);
        }

        if (uniqueNonSystemPackageNames.size() > 2) {
            fail("The WIFI_UPDATE_USABILITY_STATS_SCORE permission must not be held by more than "
                    + "two applications, but is held by " + uniqueNonSystemPackageNames.size()
                    + " applications: " + String.join(", ", uniqueNonSystemPackageNames));
        }
    }

    private static void turnScreenOnNoDelay() throws Exception {
        if (sWakeLock.isHeld()) sWakeLock.release();
        sUiDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        sUiDevice.executeShellCommand("wm dismiss-keyguard");
    }

    private void turnScreenOn() throws Exception {
        turnScreenOnNoDelay();
        // Since the screen on/off intent is ordered, they will not be sent right now.
        Thread.sleep(DURATION_SCREEN_TOGGLE);
    }

    private void turnScreenOffNoDelay() throws Exception {
        sUiDevice.executeShellCommand("input keyevent KEYCODE_SLEEP");
    }

    private void turnScreenOff() throws Exception {
        if (!sWakeLock.isHeld()) sWakeLock.acquire();
        turnScreenOffNoDelay();
        // Since the screen on/off intent is ordered, they will not be sent right now.
        Thread.sleep(DURATION_SCREEN_TOGGLE);
    }

    private void assertWifiScanningIsOn() {
        if (!sWifiManager.isScanAlwaysAvailable()) {
            fail("Wi-Fi scanning should be on.");
        }
    }

    private void runWithScanning(ThrowingRunnable r, boolean isEnabled) throws Exception {
        boolean scanModeChangedForTest = false;
        if (sWifiManager.isScanAlwaysAvailable() != isEnabled) {
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.setScanAlwaysAvailable(isEnabled));
            scanModeChangedForTest = true;
        }
        try {
            r.run();
        } finally {
            if (scanModeChangedForTest) {
                ShellIdentityUtils.invokeWithShellPermissions(
                        () -> sWifiManager.setScanAlwaysAvailable(!isEnabled));
            }
        }
    }

    /**
     * Verify that Wi-Fi scanning is not turned off when the screen turns off while wifi is disabled
     * but location is on.
     * @throws Exception
     */
    @Test
    public void testScreenOffDoesNotTurnOffWifiScanningWhenWifiDisabled() throws Exception {
        if (FeatureUtil.isTV() || FeatureUtil.isAutomotive()) {
            // TV and auto do not support the setting options of WIFI scanning and Bluetooth
            // scanning
            return;
        }

        if (!hasLocationFeature()) {
            // skip the test if location is not supported
            return;
        }
        if (!isLocationEnabled()) {
            fail("Please enable location for this test - since Marshmallow WiFi scan results are"
                    + " empty when location is disabled!");
        }
        runWithScanning(() -> {
            setWifiEnabled(false);
            turnScreenOn();
            assertWifiScanningIsOn();
            // Toggle screen and verify Wi-Fi scanning is still on.
            turnScreenOff();
            assertWifiScanningIsOn();
            turnScreenOn();
            assertWifiScanningIsOn();
        }, true /* run with enabled*/);
    }

    /**
     * Verify that Wi-Fi scanning is not turned off when the screen turns off while wifi is enabled.
     * @throws Exception
     */
    @Test
    public void testScreenOffDoesNotTurnOffWifiScanningWhenWifiEnabled() throws Exception {
        if (FeatureUtil.isTV() || FeatureUtil.isAutomotive()) {
            // TV and auto do not support the setting options of WIFI scanning and Bluetooth
            // scanning
            return;
        }

        if (!hasLocationFeature()) {
            // skip the test if location is not supported
            return;
        }
        if (!isLocationEnabled()) {
            fail("Please enable location for this test - since Marshmallow WiFi scan results are"
                    + " empty when location is disabled!");
        }
        runWithScanning(() -> {
            setWifiEnabled(true);
            turnScreenOn();
            assertWifiScanningIsOn();
            // Toggle screen and verify Wi-Fi scanning is still on.
            turnScreenOff();
            assertWifiScanningIsOn();
            turnScreenOn();
            assertWifiScanningIsOn();
        }, true /* run with enabled*/);
    }

    /**
     * Verify that the platform supports a reasonable number of suggestions per app.
     * @throws Exception
     */
    @Test
    public void testMaxNumberOfNetworkSuggestionsPerApp() throws Exception {
        assertTrue(sWifiManager.getMaxNumberOfNetworkSuggestionsPerApp()
                > ENFORCED_NUM_NETWORK_SUGGESTIONS_PER_APP);
    }

    private void verifyRegisterSoftApCallback(TestExecutor executor, TestSoftApCallback callback)
            throws Exception {
        // Register callback to get SoftApCapability
        sWifiManager.registerSoftApCallback(executor, callback);
        PollingCheck.check(
                "SoftAp register failed!", 5_000,
                () -> {
                    executor.runAll();
                    // Verify callback is run on the supplied executor and called
                    return callback.getOnStateChangedCalled()
                            && callback.getOnSoftapInfoChangedCalledCount() > 0
                            && callback.getOnSoftApCapabilityChangedCalled()
                            && callback.getOnConnectedClientCalled();
                });
    }

    private void verifyLohsRegisterSoftApCallback(TestExecutor executor,
            TestSoftApCallback callback) throws Exception {
        // Register callback to get SoftApCapability
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            sWifiManager.registerLocalOnlyHotspotSoftApCallback(executor, callback);
        } else {
            sWifiManager.registerSoftApCallback(executor, callback);
        }
        PollingCheck.check(
                "SoftAp register failed!", 5_000,
                () -> {
                    executor.runAll();
                    // Verify callback is run on the supplied executor and called
                    return callback.getOnStateChangedCalled() &&
                            callback.getOnSoftapInfoChangedCalledCount() > 0 &&
                            callback.getOnSoftApCapabilityChangedCalled() &&
                            callback.getOnConnectedClientCalled();
                });
    }

    private void verifySetGetSoftApConfig(SoftApConfiguration targetConfig) {
        assertTrue(sWifiManager.validateSoftApConfiguration(targetConfig));
        sWifiManager.setSoftApConfiguration(targetConfig);
        // Bssid set dodesn't support for tethered hotspot
        SoftApConfiguration currentConfig = sWifiManager.getSoftApConfiguration();
        compareSoftApConfiguration(targetConfig, currentConfig);
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
            assertTrue(currentConfig.isUserConfiguration());
        }
        assertNotNull(currentConfig.getPersistentRandomizedMacAddress());

        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            // Verify set/get with the deprecated set/getSsid()
            SoftApConfiguration oldSsidConfig = new SoftApConfiguration.Builder(targetConfig)
                    .setWifiSsid(null)
                    .setSsid(targetConfig.getSsid()).build();
            sWifiManager.setSoftApConfiguration(oldSsidConfig);
            currentConfig = sWifiManager.getSoftApConfiguration();
            compareSoftApConfiguration(oldSsidConfig, currentConfig);
        }
    }

    private void compareSoftApConfiguration(SoftApConfiguration currentConfig,
        SoftApConfiguration testSoftApConfig) {
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            assertEquals(currentConfig.getWifiSsid(), testSoftApConfig.getWifiSsid());
        }
        assertEquals(currentConfig.getSsid(), testSoftApConfig.getSsid());
        assertEquals(currentConfig.getBssid(), testSoftApConfig.getBssid());
        assertEquals(currentConfig.getSecurityType(), testSoftApConfig.getSecurityType());
        assertEquals(currentConfig.getPassphrase(), testSoftApConfig.getPassphrase());
        assertEquals(currentConfig.isHiddenSsid(), testSoftApConfig.isHiddenSsid());
        assertEquals(currentConfig.getBand(), testSoftApConfig.getBand());
        assertEquals(currentConfig.getChannel(), testSoftApConfig.getChannel());
        assertEquals(currentConfig.getMaxNumberOfClients(),
                testSoftApConfig.getMaxNumberOfClients());
        assertEquals(currentConfig.isAutoShutdownEnabled(),
                testSoftApConfig.isAutoShutdownEnabled());
        assertEquals(currentConfig.getShutdownTimeoutMillis(),
                testSoftApConfig.getShutdownTimeoutMillis());
        assertEquals(currentConfig.isClientControlByUserEnabled(),
                testSoftApConfig.isClientControlByUserEnabled());
        assertEquals(currentConfig.getAllowedClientList(),
                testSoftApConfig.getAllowedClientList());
        assertEquals(currentConfig.getBlockedClientList(),
                testSoftApConfig.getBlockedClientList());
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
            assertEquals(currentConfig.getMacRandomizationSetting(),
                    testSoftApConfig.getMacRandomizationSetting());
            assertEquals(currentConfig.getChannels().toString(),
                    testSoftApConfig.getChannels().toString());
            assertEquals(currentConfig.isBridgedModeOpportunisticShutdownEnabled(),
                    testSoftApConfig.isBridgedModeOpportunisticShutdownEnabled());
            assertEquals(currentConfig.isIeee80211axEnabled(),
                    testSoftApConfig.isIeee80211axEnabled());
            if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
                assertEquals(currentConfig.getBridgedModeOpportunisticShutdownTimeoutMillis(),
                        testSoftApConfig.getBridgedModeOpportunisticShutdownTimeoutMillis());
                assertEquals(currentConfig.isIeee80211beEnabled(),
                        testSoftApConfig.isIeee80211beEnabled());
                assertEquals(currentConfig.getVendorElements(),
                        testSoftApConfig.getVendorElements());
                assertArrayEquals(
                        currentConfig.getAllowedAcsChannels(SoftApConfiguration.BAND_2GHZ),
                        testSoftApConfig.getAllowedAcsChannels(SoftApConfiguration.BAND_2GHZ));
                assertArrayEquals(
                        currentConfig.getAllowedAcsChannels(SoftApConfiguration.BAND_5GHZ),
                        testSoftApConfig.getAllowedAcsChannels(SoftApConfiguration.BAND_5GHZ));
                assertArrayEquals(
                        currentConfig.getAllowedAcsChannels(SoftApConfiguration.BAND_6GHZ),
                        testSoftApConfig.getAllowedAcsChannels(SoftApConfiguration.BAND_6GHZ));
                assertEquals(currentConfig.getMaxChannelBandwidth(),
                        testSoftApConfig.getMaxChannelBandwidth());
            }
            if (Flags.androidVWifiApi()
                    && (ApiLevelUtil.codenameEquals("VanillaIceCream")
                    || ApiLevelUtil.isAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM))) {
                assertTrue(Objects.equals(
                        currentConfig.getVendorData(), testSoftApConfig.getVendorData()));
            }
        }
    }

    private void turnOffWifiAndTetheredHotspotIfEnabled() throws Exception {
        if (sWifiManager.isWifiEnabled()) {
            Log.d(TAG, "Turn off WiFi");
            sWifiManager.setWifiEnabled(false);
            PollingCheck.check("Wifi turn off failed!", WIFI_OFF_ON_TIMEOUT_MILLIS,
                    () -> !sWifiManager.isWifiEnabled());
        }
        if (sWifiManager.isWifiApEnabled()) {
            sTetheringManager.stopTethering(ConnectivityManager.TETHERING_WIFI);
            Log.d(TAG, "Turn off tethered Hotspot");
            PollingCheck.check("SoftAp turn off failed!", WIFI_OFF_ON_TIMEOUT_MILLIS,
                    () -> !sWifiManager.isWifiApEnabled());
        }
    }

    private void verifyBridgedModeSoftApCallback(TestExecutor executor,
            TestSoftApCallback callback, boolean shouldFallbackSingleApMode, boolean isEnabled)
            throws Exception {
            // Verify state and info callback value as expected
            PollingCheck.check(
                    "SoftAp state and info on bridged AP mode are mismatch!!!"
                    + " shouldFallbackSingleApMode = " + shouldFallbackSingleApMode
                    + ", isEnabled = "  + isEnabled, 10_000,
                    () -> {
                        executor.runAll();
                        int expectedState = isEnabled ? WifiManager.WIFI_AP_STATE_ENABLED
                                : WifiManager.WIFI_AP_STATE_DISABLED;
                        int expectedInfoSize = isEnabled
                                ? (shouldFallbackSingleApMode ? 1 : 2) : 0;
                        return expectedState == callback.getCurrentState()
                                && callback.getCurrentSoftApInfoList().size() == expectedInfoSize;
                    });
    }

    private boolean shouldFallbackToSingleAp(int[] bands, SoftApCapability capability) {
        for (int band : bands) {
            if (capability.getSupportedChannelList(band).length == 0) {
                return true;
            }
        }
        return false;
    }

    private SparseIntArray getAvailableBandAndChannelForTesting(SoftApCapability capability) {
        final int[] bands = {SoftApConfiguration.BAND_2GHZ, SoftApConfiguration.BAND_5GHZ,
              SoftApConfiguration.BAND_6GHZ, SoftApConfiguration.BAND_60GHZ};
        SparseIntArray testBandsAndChannels = new SparseIntArray();
        if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
            testBandsAndChannels.put(SoftApConfiguration.BAND_2GHZ, 1);
            return testBandsAndChannels;
        }
        for (int band : bands) {
            int[] supportedList = capability.getSupportedChannelList(band);
            if (supportedList.length != 0) {
                testBandsAndChannels.put(band, supportedList[0]);
            }
        }
        return testBandsAndChannels;
    }

    @Test
    public void testLastConfiguredPassphraseIsKeepInSoftApConfigurationWhenChangingToNone()
            throws Exception {
        final SoftApConfiguration currentConfig = ShellIdentityUtils.invokeWithShellPermissions(
                sWifiManager::getSoftApConfiguration);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            Mutable<String> lastPassphrase = new Mutable<>();
            final String testPassphrase = "testPassphrase";
            sWifiManager.setSoftApConfiguration(
                    new SoftApConfiguration.Builder(currentConfig)
                            .setPassphrase(testPassphrase,
                                    SoftApConfiguration.SECURITY_TYPE_WPA2_PSK).build());
            sWifiManager.queryLastConfiguredTetheredApPassphraseSinceBoot(mExecutor,
                    new Consumer<String>() {
                    @Override
                    public void accept(String value) {
                        synchronized (mLock) {
                            lastPassphrase.value = value;
                            mLock.notify();
                        }
                    }
                });
            synchronized (mLock) {
                mLock.wait(TEST_WAIT_DURATION_MS);
            }
            assertEquals(lastPassphrase.value, testPassphrase);

            sWifiManager.setSoftApConfiguration(
                    new SoftApConfiguration.Builder(currentConfig)
                            .setPassphrase(null,
                                    SoftApConfiguration.SECURITY_TYPE_OPEN).build());
            sWifiManager.queryLastConfiguredTetheredApPassphraseSinceBoot(mExecutor,
                    new Consumer<String>() {
                    @Override
                    public void accept(String value) {
                        synchronized (mLock) {
                            lastPassphrase.value = value;
                            mLock.notify();
                        }
                    }
                });
            synchronized (mLock) {
                mLock.wait(TEST_WAIT_DURATION_MS);
            }
            assertEquals(lastPassphrase.value, testPassphrase);
        } finally {
            // Restore SoftApConfiguration
            sWifiManager.setSoftApConfiguration(currentConfig);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Skip the test if telephony is not supported and default country code
     * is not stored in system property.
     */
    private boolean shouldSkipCountryCodeDependentTest() {
        String countryCode = SystemProperties.get(BOOT_DEFAULT_WIFI_COUNTRY_CODE);
        return TextUtils.isEmpty(countryCode) && !WifiFeature.isTelephonySupported(sContext);
    }

    /**
     * Test SoftApConfiguration#getPersistentRandomizedMacAddress(). There are two test cases in
     * this test.
     * 1. configure two different SoftApConfigurations (different SSID) and verify that randomized
     * MAC address is different.
     * 2. configure A then B then A (SSIDs) and verify that the 1st and 3rd MAC addresses are the
     * same.
     */
    @Test
    public void testSoftApConfigurationGetPersistentRandomizedMacAddress() throws Exception {
        SoftApConfiguration currentConfig = ShellIdentityUtils.invokeWithShellPermissions(
                sWifiManager::getSoftApConfiguration);
        final String ssid = currentConfig.getSsid().length() <= 28
                ? currentConfig.getSsid() + "test"
                : "AndroidTest";
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setSoftApConfiguration(new SoftApConfiguration.Builder()
                .setSsid(ssid).build()));
        SoftApConfiguration changedSsidConfig = ShellIdentityUtils.invokeWithShellPermissions(
                sWifiManager::getSoftApConfiguration);
        assertNotEquals(currentConfig.getPersistentRandomizedMacAddress(),
                changedSsidConfig.getPersistentRandomizedMacAddress());

        // set currentConfig
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setSoftApConfiguration(currentConfig));

        SoftApConfiguration changedSsidBackConfig = ShellIdentityUtils.invokeWithShellPermissions(
                sWifiManager::getSoftApConfiguration);

        assertEquals(currentConfig.getPersistentRandomizedMacAddress(),
                changedSsidBackConfig.getPersistentRandomizedMacAddress());
    }

    /**
     * Test bridged AP enable succeeful when device supports it.
     * Also verify the callback info update correctly.
     * @throws Exception
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testTetheredBridgedAp() throws Exception {
        // check that softap bridged mode is supported by the device
        if (!sWifiManager.isBridgedApConcurrencySupported()) {
            return;
        }
        runWithScanning(() -> {
            UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation();
            TestExecutor executor = new TestExecutor();
            TestSoftApCallback callback = new TestSoftApCallback(mLock);
            try {
                uiAutomation.adoptShellPermissionIdentity();
                // Off/On Wifi to make sure that we get the supported channel
                turnOffWifiAndTetheredHotspotIfEnabled();
                sWifiManager.setWifiEnabled(true);
                PollingCheck.check("Wifi turn on failed!", WIFI_OFF_ON_TIMEOUT_MILLIS,
                        () -> sWifiManager.isWifiEnabled());
                turnOffWifiAndTetheredHotspotIfEnabled();
                verifyRegisterSoftApCallback(executor, callback);
                if (!callback.getCurrentSoftApCapability()
                        .areFeaturesSupported(SOFTAP_FEATURE_ACS_OFFLOAD)) {
                    return;
                }
                int[] testBands = {SoftApConfiguration.BAND_2GHZ,
                        SoftApConfiguration.BAND_5GHZ};
                int[] expectedBands = {SoftApConfiguration.BAND_2GHZ,
                        SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ};
                // Test bridged SoftApConfiguration set and get (setBands)
                SoftApConfiguration testSoftApConfig =
                        generateSoftApConfigBuilderWithSsid(TEST_SSID_UNQUOTED)
                        .setPassphrase(TEST_PASSPHRASE, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                        .setBands(expectedBands)
                        .build();

                boolean shouldFallbackToSingleAp = shouldFallbackToSingleAp(testBands,
                        callback.getCurrentSoftApCapability());
                verifySetGetSoftApConfig(testSoftApConfig);

                // start tethering which used to verify startTetheredHotspot
                sTetheringManager.startTethering(ConnectivityManager.TETHERING_WIFI, executor,
                    new TetheringManager.StartTetheringCallback() {
                        @Override
                        public void onTetheringFailed(final int result) {
                        }
                    });
                verifyBridgedModeSoftApCallback(executor, callback,
                        shouldFallbackToSingleAp, true /* enabled */);
                // stop tethering which used to verify stopSoftAp
                sTetheringManager.stopTethering(ConnectivityManager.TETHERING_WIFI);
                verifyBridgedModeSoftApCallback(executor, callback,
                        shouldFallbackToSingleAp, false /* disabled */);
            } finally {
                sWifiManager.unregisterSoftApCallback(callback);
                uiAutomation.dropShellPermissionIdentity();
            }
        }, false /* run with disabled */);
    }

    /**
     * Test bridged AP with forced channel config enable succeeful when device supports it.
     * Also verify the callback info update correctly.
     * @throws Exception
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testTetheredBridgedApWifiForcedChannel() throws Exception {
        // check that softap bridged mode is supported by the device
        if (!sWifiManager.isBridgedApConcurrencySupported()) {
            return;
        }
        runWithScanning(() -> {
            UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation();
            TestExecutor executor = new TestExecutor();
            TestSoftApCallback callback = new TestSoftApCallback(mLock);
            try {
                uiAutomation.adoptShellPermissionIdentity();
                // Off/On Wifi to make sure that we get the supported channel
                turnOffWifiAndTetheredHotspotIfEnabled();
                sWifiManager.setWifiEnabled(true);
                PollingCheck.check("Wifi turn on failed!", WIFI_OFF_ON_TIMEOUT_MILLIS,
                        () -> sWifiManager.isWifiEnabled());
                turnOffWifiAndTetheredHotspotIfEnabled();
                verifyRegisterSoftApCallback(executor, callback);

                boolean shouldFallbackToSingleAp = shouldFallbackToSingleAp(
                        new int[] {SoftApConfiguration.BAND_2GHZ, SoftApConfiguration.BAND_5GHZ},
                        callback.getCurrentSoftApCapability());

                // Test when there are supported channels in both of the bands.
                if (!shouldFallbackToSingleAp) {
                    // Test bridged SoftApConfiguration set and get (setChannels)
                    SparseIntArray dual_channels = new SparseIntArray(2);
                    dual_channels.put(SoftApConfiguration.BAND_2GHZ,
                            callback.getCurrentSoftApCapability()
                            .getSupportedChannelList(SoftApConfiguration.BAND_2GHZ)[0]);
                    dual_channels.put(SoftApConfiguration.BAND_5GHZ,
                            callback.getCurrentSoftApCapability()
                            .getSupportedChannelList(SoftApConfiguration.BAND_5GHZ)[0]);
                    SoftApConfiguration testSoftApConfig =
                            generateSoftApConfigBuilderWithSsid(TEST_SSID_UNQUOTED)
                            .setPassphrase(TEST_PASSPHRASE,
                                    SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                            .setChannels(dual_channels)
                            .build();

                    verifySetGetSoftApConfig(testSoftApConfig);

                    // start tethering which used to verify startTetheredHotspot
                    sTetheringManager.startTethering(ConnectivityManager.TETHERING_WIFI, executor,
                        new TetheringManager.StartTetheringCallback() {
                            @Override
                            public void onTetheringFailed(final int result) {
                            }
                        });
                    verifyBridgedModeSoftApCallback(executor, callback,
                            shouldFallbackToSingleAp, true /* enabled */);
                    // stop tethering which used to verify stopSoftAp
                    sTetheringManager.stopTethering(ConnectivityManager.TETHERING_WIFI);
                    verifyBridgedModeSoftApCallback(executor, callback,
                            shouldFallbackToSingleAp, false /* disabled */);
                }
            } finally {
                sWifiManager.unregisterSoftApCallback(callback);
                uiAutomation.dropShellPermissionIdentity();
            }
        }, false /* run with disabled */);
    }

    /**
     * Test startTetheringRequest() starts a soft AP and relays the TetheringRequest object back via
     * SoftApCallback.
     * @throws Exception
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @Test
    public void testStartTetheredHotspotWithTetheringRequest() throws Exception {
        // check that softap  is supported by the device
        if (!sWifiManager.isPortableHotspotSupported()) {
            return;
        }
        runWithScanning(() -> {
            UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation();
            TestExecutor executor = new TestExecutor();
            TestSoftApCallback callback = new TestSoftApCallback(mLock);
            try {
                uiAutomation.adoptShellPermissionIdentity();

                // start tethering which used to verify startTetheredHotspot
                TetheringManager.TetheringRequest request =
                        new TetheringManager.TetheringRequest.Builder(
                                TetheringManager.TETHERING_WIFI).build();
                sWifiManager.startTetheredHotspotRequest(request);
                PollingCheck.check("startTetheredHotspot turn on failed!", TEST_WAIT_DURATION_MS,
                        () -> {
                            executor.runAll();
                            return callback.getOnSoftApStateChangedCalled()
                                    && callback.getCurrentSoftApState().getState()
                                    == WifiManager.WIFI_AP_STATE_ENABLED;
                        });
                if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM)) {
                    assertThat(callback.getCurrentSoftApState().getTetheringRequest())
                            .isEqualTo(request);
                } else {
                    assertThat(callback.getCurrentSoftApState().getTetheringRequest()).isNull();
                }

                // stop tethering which used to verify stopSoftAp
                sTetheringManager.stopTethering(ConnectivityManager.TETHERING_WIFI);
                PollingCheck.check("startTetheredHotspot turn on failed!", TEST_WAIT_DURATION_MS,
                        () -> {
                            executor.runAll();
                            return callback.getOnSoftApStateChangedCalled()
                                    && callback.getCurrentSoftApState().getState()
                                    == WifiManager.WIFI_AP_STATE_DISABLED;
                        });
                if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM)) {
                    assertThat(callback.getCurrentSoftApState().getTetheringRequest())
                            .isEqualTo(request);
                } else {
                    assertThat(callback.getCurrentSoftApState().getTetheringRequest()).isNull();
                }
            } finally {
                sWifiManager.unregisterSoftApCallback(callback);
                uiAutomation.dropShellPermissionIdentity();
            }
        }, false /* run with disabled */);
    }

    /**
     * Verify that the configuration from getSoftApConfiguration is same as the configuration which
     * set by setSoftApConfiguration. And depends softap capability callback to test different
     * configuration.
     * @throws Exception
     */
    @RequiresDevice
    @Test
    public void testSetGetSoftApConfigurationAndSoftApCapabilityCallback() throws Exception {
        // check that softap mode is supported by the device
        if (!sWifiManager.isPortableHotspotSupported()) {
            return;
        }
        if (shouldSkipCountryCodeDependentTest()) {
            // skip the test  when there is no Country Code available
            return;
        }
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        TestExecutor executor = new TestExecutor();
        TestSoftApCallback callback = new TestSoftApCallback(mLock);
        try {
            uiAutomation.adoptShellPermissionIdentity();
            turnOffWifiAndTetheredHotspotIfEnabled();
            verifyRegisterSoftApCallback(executor, callback);

            SoftApConfiguration.Builder softApConfigBuilder =
                     generateSoftApConfigBuilderWithSsid(TEST_SSID_UNQUOTED)
                    .setPassphrase(TEST_PASSPHRASE, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                    .setAutoShutdownEnabled(true)
                    .setShutdownTimeoutMillis(100000)
                    .setBand(getAvailableBandAndChannelForTesting(
                            callback.getCurrentSoftApCapability()).keyAt(0))
                    .setHiddenSsid(false);

            if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
                softApConfigBuilder.setBridgedModeOpportunisticShutdownTimeoutMillis(30_000);
                softApConfigBuilder.setVendorElements(TEST_VENDOR_ELEMENTS);
                softApConfigBuilder.setAllowedAcsChannels(
                        SoftApConfiguration.BAND_2GHZ, new int[] {1, 6, 11});
                softApConfigBuilder.setAllowedAcsChannels(
                        SoftApConfiguration.BAND_5GHZ, new int[] {149});
                softApConfigBuilder.setAllowedAcsChannels(
                        SoftApConfiguration.BAND_6GHZ, new int[] {});
                softApConfigBuilder.setMaxChannelBandwidth(SoftApInfo.CHANNEL_WIDTH_80MHZ);
            }

            // Test SoftApConfiguration set and get
            verifySetGetSoftApConfig(softApConfigBuilder.build());

            boolean isSupportCustomizedMac = callback.getCurrentSoftApCapability()
                        .areFeaturesSupported(
                        SoftApCapability.SOFTAP_FEATURE_MAC_ADDRESS_CUSTOMIZATION)
                    && PropertyUtil.isVndkApiLevelNewerThan(Build.VERSION_CODES.S);

            //Test MAC_ADDRESS_CUSTOMIZATION supported config
            if (isSupportCustomizedMac) {
                softApConfigBuilder.setBssid(TEST_MAC)
                        .setMacRandomizationSetting(SoftApConfiguration.RANDOMIZATION_NONE);

                // Test SoftApConfiguration set and get
                verifySetGetSoftApConfig(softApConfigBuilder.build());
            }

            assertThat(callback.getCurrentSoftApCapability().getMaxSupportedClients())
                    .isGreaterThan(0);
            // Test CLIENT_FORCE_DISCONNECT supported config.
            if (callback.getCurrentSoftApCapability()
                    .areFeaturesSupported(
                    SoftApCapability.SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT)) {
                softApConfigBuilder.setMaxNumberOfClients(10);
                softApConfigBuilder.setClientControlByUserEnabled(true);
                softApConfigBuilder.setBlockedClientList(new ArrayList<>());
                softApConfigBuilder.setAllowedClientList(new ArrayList<>());
                verifySetGetSoftApConfig(softApConfigBuilder.build());
            }

            // Test SAE config
            if (callback.getCurrentSoftApCapability()
                    .areFeaturesSupported(SoftApCapability.SOFTAP_FEATURE_WPA3_SAE)) {
                softApConfigBuilder
                        .setPassphrase(TEST_PASSPHRASE,
                          SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION);
                verifySetGetSoftApConfig(softApConfigBuilder.build());
                softApConfigBuilder
                        .setPassphrase(TEST_PASSPHRASE,
                        SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
                verifySetGetSoftApConfig(softApConfigBuilder.build());
            }

            // Test 11 BE control config
            if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
                if (callback.getCurrentSoftApCapability()
                        .areFeaturesSupported(SoftApCapability.SOFTAP_FEATURE_IEEE80211_BE)) {
                    softApConfigBuilder.setIeee80211beEnabled(true);
                    verifySetGetSoftApConfig(softApConfigBuilder.build());
                }
            }

            if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
                // Test 11 AX control config.
                if (callback.getCurrentSoftApCapability()
                        .areFeaturesSupported(SoftApCapability.SOFTAP_FEATURE_IEEE80211_AX)) {
                    softApConfigBuilder.setIeee80211axEnabled(true);
                    verifySetGetSoftApConfig(softApConfigBuilder.build());
                }
                softApConfigBuilder.setBridgedModeOpportunisticShutdownEnabled(false);
                verifySetGetSoftApConfig(softApConfigBuilder.build());
            }

        } finally {
            sWifiManager.unregisterSoftApCallback(callback);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Verify that startTetheredHotspot with specific channel config.
     * @throws Exception
     */
    @RequiresDevice
    @Test
    public void testStartTetheredHotspotWithChannelConfigAndSoftApStateAndInfoCallback()
            throws Exception {
        // check that softap mode is supported by the device
        if (!sWifiManager.isPortableHotspotSupported()) {
            return;
        }
        if (shouldSkipCountryCodeDependentTest()) {
            // skip the test  when there is no Country Code available
            return;
        }
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        TestExecutor executor = new TestExecutor();
        TestSoftApCallback callback = new TestSoftApCallback(mLock);
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // check that tethering is supported by the device
            if (!sTetheringManager.isTetheringSupported()) {
                return;
            }
            turnOffWifiAndTetheredHotspotIfEnabled();
            verifyRegisterSoftApCallback(executor, callback);

            SparseIntArray testBandsAndChannels = getAvailableBandAndChannelForTesting(
                    callback.getCurrentSoftApCapability());

            if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
                assertNotEquals(0, testBandsAndChannels.size());
            }
            boolean isSupportCustomizedMac = callback.getCurrentSoftApCapability()
                    .areFeaturesSupported(
                    SoftApCapability.SOFTAP_FEATURE_MAC_ADDRESS_CUSTOMIZATION)
                    && PropertyUtil.isVndkApiLevelNewerThan(Build.VERSION_CODES.S);

            SoftApConfiguration.Builder testSoftApConfigBuilder =
                     generateSoftApConfigBuilderWithSsid(TEST_SSID_UNQUOTED)
                    .setPassphrase(TEST_PASSPHRASE, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                    .setChannel(testBandsAndChannels.valueAt(0), testBandsAndChannels.keyAt(0));

            if (isSupportCustomizedMac) {
                testSoftApConfigBuilder.setBssid(TEST_MAC)
                        .setMacRandomizationSetting(SoftApConfiguration.RANDOMIZATION_NONE);
            }

            SoftApConfiguration testSoftApConfig = testSoftApConfigBuilder.build();

            sWifiManager.setSoftApConfiguration(testSoftApConfig);

            // start tethering which used to verify startTetheredHotspot
            sTetheringManager.startTethering(ConnectivityManager.TETHERING_WIFI, executor,
                new TetheringManager.StartTetheringCallback() {
                    @Override
                    public void onTetheringFailed(final int result) {
                    }
                });

            // Verify state and info callback value as expected
            PollingCheck.check(
                    "SoftAp channel and state mismatch!!!", 10_000,
                    () -> {
                        executor.runAll();
                        int sapChannel = ScanResult.convertFrequencyMhzToChannelIfSupported(
                                callback.getCurrentSoftApInfo().getFrequency());
                        boolean isInfoCallbackSupported =
                                callback.getOnSoftapInfoChangedCalledCount() > 1;
                        if (isInfoCallbackSupported) {
                            return WifiManager.WIFI_AP_STATE_ENABLED == callback.getCurrentState()
                                && testBandsAndChannels.valueAt(0) == sapChannel;
                        }
                        return WifiManager.WIFI_AP_STATE_ENABLED == callback.getCurrentState();
                    });
            // After Soft Ap enabled, check SoftAp info if it supported
            if (isSupportCustomizedMac && callback.getOnSoftapInfoChangedCalledCount() > 1) {
                assertEquals(callback.getCurrentSoftApInfo().getBssid(), TEST_MAC);
            }
            if (PropertyUtil.isVndkApiLevelNewerThan(Build.VERSION_CODES.S)
                    && callback.getOnSoftapInfoChangedCalledCount() > 1) {
                assertNotEquals(callback.getCurrentSoftApInfo().getWifiStandard(),
                        ScanResult.WIFI_STANDARD_UNKNOWN);
            }
            if (Flags.androidVWifiApi()
                    && (ApiLevelUtil.codenameEquals("VanillaIceCream")
                    || ApiLevelUtil.isAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM))
                    && callback.getOnSoftapInfoChangedCalledCount() > 1) {
                assertNotNull(callback.getCurrentSoftApInfo().getVendorData());
            }

            if (callback.getOnSoftapInfoChangedCalledCount() > 1) {
                assertTrue(callback.getCurrentSoftApInfo().getAutoShutdownTimeoutMillis() > 0);
            }
        } finally {
            // stop tethering which used to verify stopSoftAp
            sTetheringManager.stopTethering(ConnectivityManager.TETHERING_WIFI);

            // Verify clean up
            PollingCheck.check(
                    "Stop Softap failed", 3_000,
                    () -> {
                        executor.runAll();
                        return WifiManager.WIFI_AP_STATE_DISABLED == callback.getCurrentState() &&
                                0 == callback.getCurrentSoftApInfo().getBandwidth() &&
                                0 == callback.getCurrentSoftApInfo().getFrequency();
                    });
            if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
                assertEquals(callback.getCurrentSoftApInfo().getBssid(), null);
                assertEquals(ScanResult.WIFI_STANDARD_UNKNOWN,
                        callback.getCurrentSoftApInfo().getWifiStandard());
            }
            sWifiManager.unregisterSoftApCallback(callback);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private static class TestActionListener implements WifiManager.ActionListener {
        private final Object mLock;
        public boolean onSuccessCalled = false;
        public boolean onFailedCalled = false;
        public int failureReason = -1;

        TestActionListener(Object lock) {
            mLock = lock;
        }

        @Override
        public void onSuccess() {
            synchronized (mLock) {
                onSuccessCalled = true;
                mLock.notify();
            }
        }

        @Override
        public void onFailure(int reason) {
            synchronized (mLock) {
                onFailedCalled = true;
                failureReason = reason;
                mLock.notify();
            }
        }
    }

    /**
     * Triggers connection to one of the saved networks using {@link WifiManager#connect(
     * int, WifiManager.ActionListener)} or {@link WifiManager#connect(WifiConfiguration,
     * WifiManager.ActionListener)}
     *
     * @param withNetworkId Use networkId for triggering connection, false for using
     *                      WifiConfiguration.
     * @throws Exception
     */
    private void testConnect(boolean withNetworkId) throws Exception {
        TestActionListener actionListener = new TestActionListener(mLock);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        List<WifiConfiguration> savedNetworks = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // These below API's only work with privileged permissions (obtained via shell identity
            // for test)
            savedNetworks = sWifiManager.getConfiguredNetworks();

            // Disable all the saved networks to trigger disconnect & disable autojoin.
            for (WifiConfiguration network : savedNetworks) {
                assertTrue(sWifiManager.disableNetwork(network.networkId));
            }
            waitForDisconnection();

            // Now trigger connection to the last saved network.
            WifiConfiguration savedNetworkToConnect =
                    savedNetworks.get(savedNetworks.size() - 1);
            synchronized (mLock) {
                try {
                    if (withNetworkId) {
                        sWifiManager.connect(savedNetworkToConnect.networkId, actionListener);
                    } else {
                        sWifiManager.connect(savedNetworkToConnect, actionListener);
                    }
                    // now wait for callback
                    mLock.wait(TEST_WAIT_DURATION_MS);
                } catch (InterruptedException e) {
                }
            }
            // check if we got the success callback
            assertTrue(actionListener.onSuccessCalled);
            // Wait for connection to complete & ensure we are connected to the saved network.
            waitForConnection();
            assertEquals(savedNetworkToConnect.networkId,
                    sWifiManager.getConnectionInfo().getNetworkId());
        } finally {
            // Re-enable all saved networks before exiting.
            if (savedNetworks != null) {
                for (WifiConfiguration network : savedNetworks) {
                    sWifiManager.enableNetwork(network.networkId, true);
                }
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#connect(int, WifiManager.ActionListener)} to an existing saved
     * network.
     */
    @Test
    public void testConnectWithNetworkId() throws Exception {
        testConnect(true);
    }

    /**
     * Tests {@link WifiManager#connect(WifiConfiguration, WifiManager.ActionListener)} to an
     * existing saved network.
     */
    @Test
    public void testConnectWithWifiConfiguration() throws Exception {
        testConnect(false);

    }

    private static class TestNetworkCallback extends ConnectivityManager.NetworkCallback {
        private final Object mLock;
        public boolean onAvailableCalled = false;
        public Network network;
        public NetworkCapabilities networkCapabilities;

        TestNetworkCallback(Object lock) {
            mLock = lock;
        }

        @Override
        public void onAvailable(Network network) {
            synchronized (mLock) {
                onAvailableCalled = true;
                this.network = network;
            }
        }

        @Override
        public void onCapabilitiesChanged(Network network,
                NetworkCapabilities networkCapabilities) {
            synchronized (mLock) {
                this.networkCapabilities = networkCapabilities;
                mLock.notify();
            }
        }
    }

    private void waitForNetworkCallbackAndCheckForMeteredness(boolean expectMetered) {
        Object lock = new Object();
        TestNetworkCallback networkCallbackListener = new TestNetworkCallback(lock);
        synchronized (lock) {
            try {
                NetworkRequest.Builder networkRequestBuilder = new NetworkRequest.Builder()
                        .addTransportType(TRANSPORT_WIFI);
                if (expectMetered) {
                    networkRequestBuilder.removeCapability(NET_CAPABILITY_NOT_METERED);
                } else {
                    networkRequestBuilder.addCapability(NET_CAPABILITY_NOT_METERED);
                }
                // File a request for wifi network.
                sConnectivityManager.registerNetworkCallback(
                        networkRequestBuilder.build(), networkCallbackListener);
                // now wait for callback
                lock.wait(TEST_WAIT_DURATION_MS);
            } catch (InterruptedException e) {
            }
        }
        assertTrue(networkCallbackListener.onAvailableCalled);
    }

    /**
     * Tests {@link WifiManager#save(WifiConfiguration, WifiManager.ActionListener)} by marking
     * an existing saved network metered.
     */
    @Test
    public void testSave() throws Exception {
        Object lock = new Object();
        TestActionListener actionListener = new TestActionListener(lock);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        List<WifiConfiguration> savedNetworks = null;
        WifiConfiguration currentConfig = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // These below API's only work with privileged permissions (obtained via shell identity
            // for test)

            WifiInfo wifiInfo = sWifiManager.getConnectionInfo();
            savedNetworks = sWifiManager.getConfiguredNetworks();

            // find the current network's WifiConfiguration
            currentConfig = savedNetworks
                    .stream()
                    .filter(config -> config.networkId == wifiInfo.getNetworkId())
                    .findAny()
                    .get();

            // Ensure that the current network is not metered.
            assertNotEquals("Ensure that the saved network is configured as unmetered",
                    currentConfig.meteredOverride,
                    WifiConfiguration.METERED_OVERRIDE_METERED);

            // Disable all except the currently connected networks to avoid reconnecting to the
            // wrong network after later setting the current network as metered.
            for (WifiConfiguration network : savedNetworks) {
                if (network.networkId != currentConfig.networkId) {
                    assertTrue(sWifiManager.disableNetwork(network.networkId));
                }
            }

            // Check the network capabilities to ensure that the network is marked not metered.
            waitForNetworkCallbackAndCheckForMeteredness(false);

            // Now mark the network metered and save.
            synchronized (lock) {
                try {
                    WifiConfiguration modSavedNetwork = new WifiConfiguration(currentConfig);
                    modSavedNetwork.meteredOverride = WifiConfiguration.METERED_OVERRIDE_METERED;
                    sWifiManager.save(modSavedNetwork, actionListener);
                    // now wait for callback
                    lock.wait(TEST_WAIT_DURATION_MS);
                } catch (InterruptedException e) {
                }
            }
            // check if we got the success callback
            assertTrue(actionListener.onSuccessCalled);
            // Ensure we disconnected on marking the network metered & connect back.
            waitForDisconnection();
            waitForConnection();
            // Check the network capabilities to ensure that the network is marked metered now.
            waitForNetworkCallbackAndCheckForMeteredness(true);

        } finally {
            // Restore original network config (restore the meteredness back);
            if (currentConfig != null) {
                sWifiManager.updateNetwork(currentConfig);
            }
            // re-enable all networks
            if (savedNetworks != null) {
                for (WifiConfiguration network : savedNetworks) {
                    sWifiManager.enableNetwork(network.networkId, true);
                }
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#forget(int, WifiManager.ActionListener)} by adding/removing a new
     * network.
     */
    @AsbSecurityTest(cveBugId = 159373687)
    @Test
    public void testForget() throws Exception {
        TestActionListener actionListener = new TestActionListener(mLock);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        int newNetworkId = INVALID_NETWORK_ID;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // These below API's only work with privileged permissions (obtained via shell identity
            // for test)
            List<WifiConfiguration> savedNetworks = sWifiManager.getConfiguredNetworks();

            WifiConfiguration newOpenNetwork = new WifiConfiguration();
            newOpenNetwork.SSID = "\"" + TEST_SSID_UNQUOTED + "\"";
            newNetworkId = sWifiManager.addNetwork(newOpenNetwork);
            assertNotEquals(INVALID_NETWORK_ID, newNetworkId);

            // Multi-type configurations might be converted to more than 1 configuration.
            assertThat(savedNetworks.size() < sWifiManager.getConfiguredNetworks().size()).isTrue();

            // Need an effectively-final holder because we need to modify inner Intent in callback.
            class IntentHolder {
                Intent intent;
            }
            IntentHolder intentHolder = new IntentHolder();
            sContext.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.i(TAG, "Received CONFIGURED_NETWORKS_CHANGED_ACTION broadcast: " + intent);
                    intentHolder.intent = intent;
                }
            }, new IntentFilter(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));

            // Now remove the network
            synchronized (mLock) {
                try {
                    sWifiManager.forget(newNetworkId, actionListener);
                    // now wait for callback
                    mLock.wait(TEST_WAIT_DURATION_MS);
                } catch (InterruptedException e) {
                }
            }
            // check if we got the success callback
            assertTrue(actionListener.onSuccessCalled);

            PollingCheck.check(
                    "Didn't receive CONFIGURED_NETWORKS_CHANGED_ACTION broadcast!",
                    TEST_WAIT_DURATION_MS,
                    () -> intentHolder.intent != null);
            Intent intent = intentHolder.intent;
            assertEquals(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION, intent.getAction());
            assertTrue(intent.getBooleanExtra(WifiManager.EXTRA_MULTIPLE_NETWORKS_CHANGED, false));
            assertEquals(WifiManager.CHANGE_REASON_REMOVED,
                    intent.getIntExtra(WifiManager.EXTRA_CHANGE_REASON, -1));
            assertNull(intent.getParcelableExtra(WifiManager.EXTRA_WIFI_CONFIGURATION));

            // Ensure that the new network has been successfully removed.
            assertEquals(savedNetworks.size(), sWifiManager.getConfiguredNetworks().size());
        } finally {
            // For whatever reason, if the forget fails, try removing using the public remove API.
            if (newNetworkId != INVALID_NETWORK_ID) sWifiManager.removeNetwork(newNetworkId);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#getFactoryMacAddresses()} returns at least one valid MAC address.
     */
    @RequiresDevice
    @Test
    public void testGetFactoryMacAddresses() throws Exception {
        TestActionListener actionListener = new TestActionListener(mLock);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        int newNetworkId = INVALID_NETWORK_ID;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // Obtain the factory MAC address
            String[] macAddresses = sWifiManager.getFactoryMacAddresses();
            assertTrue("At list one MAC address should be returned.", macAddresses.length > 0);
            try {
                MacAddress mac = MacAddress.fromString(macAddresses[0]);
                assertNotEquals(WifiInfo.DEFAULT_MAC_ADDRESS, mac);
                assertFalse(MacAddressUtils.isMulticastAddress(mac));
            } catch (IllegalArgumentException e) {
                fail("Factory MAC address is invalid");
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#isApMacRandomizationSupported()} does not crash.
     */
    @Test
    public void testIsApMacRandomizationSupported() throws Exception {
        sWifiManager.isApMacRandomizationSupported();
    }

    /**
     * Tests {@link WifiManager#isConnectedMacRandomizationSupported()} does not crash.
     */
    @Test
    public void testIsConnectedMacRandomizationSupported() throws Exception {
        sWifiManager.isConnectedMacRandomizationSupported();
    }

    /**
     * Tests {@link WifiManager#isPreferredNetworkOffloadSupported()} does not crash.
     */
    @Test
    public void testIsPreferredNetworkOffloadSupported() throws Exception {
        sWifiManager.isPreferredNetworkOffloadSupported();
    }

    /** Test that PNO scans reconnects us when the device is disconnected and the screen is off. */
    @Test
    public void testPnoScan() throws Exception {
        if (!sWifiManager.isPreferredNetworkOffloadSupported()) {
            // skip the test if PNO scanning is not supported
            return;
        }

        WifiInfo currentNetwork = ShellIdentityUtils.invokeWithShellPermissions(
                sWifiManager::getConnectionInfo);

        // disable all networks that aren't already disabled
        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                sWifiManager::getConfiguredNetworks);
        Set<Integer> disabledNetworkIds = new HashSet<>();
        for (WifiConfiguration config : savedNetworks) {
            if (config.getNetworkSelectionStatus().getNetworkSelectionDisableReason()
                    == WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE) {
                ShellIdentityUtils.invokeWithShellPermissions(
                        () -> sWifiManager.disableNetwork(config.networkId));
                disabledNetworkIds.add(config.networkId);
            }
        }

        try {
            // wait for disconnection from current network
            waitForDisconnection();

            // turn screen off
            turnScreenOffNoDelay();

            // re-enable the current network - this will trigger PNO
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.enableNetwork(currentNetwork.getNetworkId(), false));
            disabledNetworkIds.remove(currentNetwork.getNetworkId());

            // PNO should reconnect us back to the network we disconnected from
            waitForConnection(WIFI_PNO_CONNECT_TIMEOUT_MILLIS);
        } finally {
            // re-enable disabled networks
            for (int disabledNetworkId : disabledNetworkIds) {
                ShellIdentityUtils.invokeWithShellPermissions(
                        () -> sWifiManager.enableNetwork(disabledNetworkId, true));
            }
        }
    }

    /**
     * Tests {@link WifiManager#isStaApConcurrencySupported()}.
     */
    @Test
    public void testIsStaApConcurrencySupported() throws Exception {
        // check that softap mode is supported by the device
        assumeTrue(sWifiManager.isPortableHotspotSupported());

        runWithScanning(() -> {
            // Re-enabled Wi-Fi as shell for HalDeviceManager legacy LOHS behavior when there's no
            // STA+AP concurrency.
            ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.setWifiEnabled(false));
            PollingCheck.check("Wifi turn off failed!", 2_000, () -> !sWifiManager.isWifiEnabled());
            SystemUtil.runShellCommand("cmd wifi set-wifi-enabled enabled");
            PollingCheck.check("Wifi turn on failed!", WIFI_OFF_ON_TIMEOUT_MILLIS,
                    () -> sWifiManager.isWifiEnabled());

            boolean isStaApConcurrencySupported = sWifiManager.isStaApConcurrencySupported();
            // start local only hotspot.
            TestLocalOnlyHotspotCallback callback = startLocalOnlyHotspot();
            try {
                if (isStaApConcurrencySupported) {
                    assertTrue(sWifiManager.isWifiEnabled());
                } else {
                    // no concurrency, wifi should be disabled.
                    assertFalse(sWifiManager.isWifiEnabled());
                }
            } finally {
                // clean up local only hotspot no matter if assertion passed or failed
                stopLocalOnlyHotspot(callback, true);
            }

            assertTrue(sWifiManager.isWifiEnabled());
        }, false);
    }

    /**
     * state is a bitset, where bit 0 indicates whether there was data in, and bit 1 indicates
     * whether there was data out. Only count down on the latch once there was both data in and out.
     */
    private static class TestTrafficStateCallback implements WifiManager.TrafficStateCallback {
        public final CountDownLatch latch = new CountDownLatch(1);
        private int mAccumulator = 0;

        @Override
        public void onStateChanged(int state) {
            mAccumulator |= state;
            if (mAccumulator == DATA_ACTIVITY_INOUT) {
                latch.countDown();
            }
        }
    }

    private void sendTraffic() {
        boolean didAnyConnectionSucceed = false;
        for (int i = 0; i < 10; i++) {
            // Do some network operations
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://www.google.com/");
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(TEST_WAIT_DURATION_MS);
                connection.setReadTimeout(TEST_WAIT_DURATION_MS);
                connection.setUseCaches(false);
                InputStream stream = connection.getInputStream();
                byte[] bytes = new byte[100];
                int receivedBytes = stream.read(bytes);
                if (receivedBytes > 0) {
                    didAnyConnectionSucceed = true;
                }
            } catch (Exception e) {
                // ignore
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        assertTrue("All connections failed!", didAnyConnectionSucceed);
    }

    /**
     * Tests {@link WifiManager#registerTrafficStateCallback(Executor,
     * WifiManager.TrafficStateCallback)} by sending some traffic.
     */
    @Test
    public void testTrafficStateCallback() throws Exception {
        TestTrafficStateCallback callback = new TestTrafficStateCallback();
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();

            // Turn screen on for wifi traffic polling.
            turnScreenOn();
            sWifiManager.registerTrafficStateCallback(
                    Executors.newSingleThreadExecutor(), callback);
            // Send some traffic to trigger the traffic state change callbacks.
            sendTraffic();
            // now wait for callback
            boolean success = callback.latch.await(TEST_WAIT_DURATION_MS, TimeUnit.MILLISECONDS);
            // check if we got the state changed callback with both data in and out
            assertTrue(success);
        } finally {
            turnScreenOff();
            sWifiManager.unregisterTrafficStateCallback(callback);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#setScanAlwaysAvailable(boolean)} &
     * {@link WifiManager#isScanAlwaysAvailable()}.
     */
    @Test
    public void testScanAlwaysAvailable() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        Boolean currState = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            currState = sWifiManager.isScanAlwaysAvailable();
            boolean newState = !currState;
            sWifiManager.setScanAlwaysAvailable(newState);
            PollingCheck.check(
                    "Wifi scanning toggle failed!",
                    DURATION_SETTINGS_TOGGLE,
                    () -> sWifiManager.isScanAlwaysAvailable() == newState);
            assertEquals(newState, sWifiManager.isScanAlwaysAvailable());
        } finally {
            if (currState != null) sWifiManager.setScanAlwaysAvailable(currState);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#setScanThrottleEnabled(boolean)} &
     * {@link WifiManager#isScanThrottleEnabled()}.
     */
    @Test
    public void testScanThrottleEnabled() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        Boolean currState = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            currState = sWifiManager.isScanThrottleEnabled();
            boolean newState = !currState;
            sWifiManager.setScanThrottleEnabled(newState);
            PollingCheck.check(
                    "Wifi settings toggle failed!",
                    DURATION_SETTINGS_TOGGLE,
                    () -> sWifiManager.isScanThrottleEnabled() == newState);
            assertEquals(newState, sWifiManager.isScanThrottleEnabled());
        } finally {
            if (currState != null) sWifiManager.setScanThrottleEnabled(currState);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#setAutoWakeupEnabled(boolean)} &
     * {@link WifiManager#isAutoWakeupEnabled()}.
     */
    @Test
    public void testAutoWakeUpEnabled() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        Boolean currState = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            currState = sWifiManager.isAutoWakeupEnabled();
            boolean newState = !currState;
            sWifiManager.setAutoWakeupEnabled(newState);
            PollingCheck.check(
                    "Wifi settings toggle failed!",
                    DURATION_SETTINGS_TOGGLE,
                    () -> sWifiManager.isAutoWakeupEnabled() == newState);
            assertEquals(newState, sWifiManager.isAutoWakeupEnabled());
        } finally {
            if (currState != null) sWifiManager.setAutoWakeupEnabled(currState);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#setVerboseLoggingEnabled(boolean)} &
     * {@link WifiManager#isVerboseLoggingEnabled()}.
     */
    @Test
    public void testVerboseLoggingEnabled() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        Boolean currState = null;
        TestWifiVerboseLoggingStatusChangedListener listener =
                WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)
                        ? new TestWifiVerboseLoggingStatusChangedListener() : null;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            if (listener != null) {
                sWifiManager.addWifiVerboseLoggingStatusChangedListener(mExecutor, listener);
            }
            currState = sWifiManager.isVerboseLoggingEnabled();
            boolean newState = !currState;
            if (listener != null) {
                assertEquals(0, listener.numCalls);
            }
            sWifiManager.setVerboseLoggingEnabled(newState);
            PollingCheck.check(
                    "Wifi verbose logging toggle failed!",
                    DURATION_SETTINGS_TOGGLE,
                    () -> sWifiManager.isVerboseLoggingEnabled() == newState);
            if (listener != null) {
                PollingCheck.check(
                        "Verbose logging listener timeout",
                        DURATION_SETTINGS_TOGGLE,
                        () -> listener.status == newState && listener.numCalls == 1);
            }
        } finally {
            if (currState != null) sWifiManager.setVerboseLoggingEnabled(currState);
            if (listener != null) {
                sWifiManager.removeWifiVerboseLoggingStatusChangedListener(listener);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#setVerboseLoggingLevel(int)}.
     */
    @Test
    public void testSetVerboseLogging() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        Boolean currState = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            currState = sWifiManager.isVerboseLoggingEnabled();

            sWifiManager.setVerboseLoggingLevel(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED);
            assertTrue(sWifiManager.isVerboseLoggingEnabled());
            assertEquals(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED,
                    sWifiManager.getVerboseLoggingLevel());

            sWifiManager.setVerboseLoggingLevel(WifiManager.VERBOSE_LOGGING_LEVEL_DISABLED);
            assertFalse(sWifiManager.isVerboseLoggingEnabled());
            assertEquals(WifiManager.VERBOSE_LOGGING_LEVEL_DISABLED,
                    sWifiManager.getVerboseLoggingLevel());
        } finally {
            if (currState != null) sWifiManager.setVerboseLoggingEnabled(currState);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Test {@link WifiManager#setVerboseLoggingLevel(int)} for show key mode.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testSetVerboseLoggingShowKeyModeNonUserBuild() throws Exception {
        if (Build.TYPE.equals("user")) return;
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        Boolean currState = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            currState = sWifiManager.isVerboseLoggingEnabled();

            sWifiManager.setVerboseLoggingLevel(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED_SHOW_KEY);
            assertTrue(sWifiManager.isVerboseLoggingEnabled());
            assertEquals(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED_SHOW_KEY,
                    sWifiManager.getVerboseLoggingLevel());
        } finally {
            if (currState != null) sWifiManager.setVerboseLoggingEnabled(currState);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Test {@link WifiManager#setVerboseLoggingLevel(int)} for show key mode.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testSetVerboseLoggingShowKeyModeUserBuild() throws Exception {
        if (!Build.TYPE.equals("user")) return;
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        Boolean currState = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            currState = sWifiManager.isVerboseLoggingEnabled();

            sWifiManager.setVerboseLoggingLevel(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED_SHOW_KEY);
            assertTrue(sWifiManager.isVerboseLoggingEnabled());
            assertEquals(WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED_SHOW_KEY,
                    sWifiManager.getVerboseLoggingLevel());
            fail("Verbosing logging show key mode should not be allowed for user build.");
        } catch (SecurityException e) {
            // expected
        } finally {
            if (currState != null) sWifiManager.setVerboseLoggingEnabled(currState);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#factoryReset()} cannot be invoked from a non-privileged app.
     *
     * Note: This intentionally does not test the full reset functionality because it causes
     * the existing saved networks on the device to be lost after the test. If you add the
     * networks back after reset, the ownership of saved networks will change.
     */
    @Test
    public void testFactoryReset() throws Exception {
        List<WifiConfiguration> beforeSavedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                sWifiManager::getConfiguredNetworks);
        try {
            sWifiManager.factoryReset();
            fail("Factory reset should not be allowed for non-privileged apps");
        } catch (SecurityException e) {
            // expected
        }
        List<WifiConfiguration> afterSavedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                sWifiManager::getConfiguredNetworks);
        assertEquals(beforeSavedNetworks.size(), afterSavedNetworks.size());
    }

    /**
     * Test {@link WifiNetworkConnectionStatistics} does not crash.
     * TODO(b/150891569): deprecate it in Android S, this API is not used anywhere.
     */
    @Test
    public void testWifiNetworkConnectionStatistics() {
        new WifiNetworkConnectionStatistics();
        WifiNetworkConnectionStatistics stats = new WifiNetworkConnectionStatistics(0, 0);
        new WifiNetworkConnectionStatistics(stats);
    }

    /**
     * Verify that startRestrictingAutoJoinToSubscriptionId disconnects wifi and disables
     * auto-connect to non-carrier-merged networks. Then verify that
     * stopRestrictingAutoJoinToSubscriptionId makes the disabled networks clear to connect
     * again.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testStartAndStopRestrictingAutoJoinToSubscriptionId() throws Exception {
        int fakeSubscriptionId = 5;
        ShellIdentityUtils.invokeWithShellPermissions(() ->
                sWifiManager.startRestrictingAutoJoinToSubscriptionId(fakeSubscriptionId));
        waitForDisconnection();
        startScan();
        ensureNotConnected();
        ShellIdentityUtils.invokeWithShellPermissions(() ->
                sWifiManager.stopRestrictingAutoJoinToSubscriptionId());
        startScan();
        waitForConnection();
    }

    private class TestActiveCountryCodeChangedCallback implements
            WifiManager.ActiveCountryCodeChangedCallback  {
        private String mCurrentCountryCode;
        private boolean mIsOnActiveCountryCodeChangedCalled = false;
        private boolean mIsOnCountryCodeInactiveCalled = false;

        public boolean isOnActiveCountryCodeChangedCalled() {
            return mIsOnActiveCountryCodeChangedCalled;
        }

        public boolean isOnCountryCodeInactiveCalled() {
            return mIsOnCountryCodeInactiveCalled;
        }
        public void resetCallbackCallededHistory() {
            mIsOnActiveCountryCodeChangedCalled = false;
            mIsOnCountryCodeInactiveCalled = false;
        }

        public String getCurrentDriverCountryCode() {
            return mCurrentCountryCode;
        }

        @Override
        public void onActiveCountryCodeChanged(String country) {
            Log.d(TAG, "Receive DriverCountryCodeChanged to " + country);
            mCurrentCountryCode = country;
            mIsOnActiveCountryCodeChangedCalled = true;
        }

        @Override
        public void onCountryCodeInactive() {
            Log.d(TAG, "Receive onCountryCodeInactive");
            mCurrentCountryCode = null;
            mIsOnCountryCodeInactiveCalled = true;
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testActiveCountryCodeChangedCallback() throws Exception {
        if (!hasLocationFeature()) {
            // skip the test if location is not supported
            return;
        }
        if (!isLocationEnabled()) {
            fail("Please enable location for this test - since country code is not available"
                    + " when location is disabled!");
        }
        if (shouldSkipCountryCodeDependentTest()) {
            // skip the test when there is no Country Code available
            return;
        }
        TestActiveCountryCodeChangedCallback testCountryCodeChangedCallback =
                new TestActiveCountryCodeChangedCallback();
        TestExecutor executor = new TestExecutor();
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            // Run with scanning disable to make sure there is no active mode.
            runWithScanning(() -> {
                uiAutomation.adoptShellPermissionIdentity();
                turnOffWifiAndTetheredHotspotIfEnabled();
                sWifiManager.registerActiveCountryCodeChangedCallback(
                        executor, testCountryCodeChangedCallback);


                PollingCheck.check(
                        "DriverCountryCode is non-null when wifi off",
                        5000,
                        () -> {
                            executor.runAll();
                            return testCountryCodeChangedCallback
                                        .isOnCountryCodeInactiveCalled()
                                    && testCountryCodeChangedCallback.getCurrentDriverCountryCode()
                                            == null;
                        });
                // Enable wifi to make sure country code has been updated.
                sWifiManager.setWifiEnabled(true);
                PollingCheck.check(
                        "DriverCountryCode is null when wifi on",
                        5000,
                        () -> {
                            executor.runAll();
                            return testCountryCodeChangedCallback
                                        .isOnActiveCountryCodeChangedCalled()
                                    && testCountryCodeChangedCallback.getCurrentDriverCountryCode()
                                            != null;
                        });
                // Disable wifi to trigger country code change
                sWifiManager.setWifiEnabled(false);
                PollingCheck.check(
                        "DriverCountryCode should be null when wifi off",
                        5000,
                        () -> {
                            executor.runAll();
                            return testCountryCodeChangedCallback.isOnCountryCodeInactiveCalled()
                                    && testCountryCodeChangedCallback
                                            .getCurrentDriverCountryCode() == null;
                        });
                sWifiManager.unregisterActiveCountryCodeChangedCallback(
                            testCountryCodeChangedCallback);
                testCountryCodeChangedCallback.resetCallbackCallededHistory();
                sWifiManager.setWifiEnabled(true);
                // Check there is no callback has been called.
                PollingCheck.check(
                        "Callback is called after unregister",
                        5000,
                        () -> {
                            executor.runAll();
                            return !testCountryCodeChangedCallback.isOnCountryCodeInactiveCalled()
                                    && !testCountryCodeChangedCallback
                                            .isOnActiveCountryCodeChangedCalled();
                        });
            }, false /* Run with disabled */);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Test that the wifi country code is either null, or a length-2 string.
     */
    @Test
    public void testGetCountryCode() throws Exception {
        String wifiCountryCode = ShellIdentityUtils.invokeWithShellPermissions(
                sWifiManager::getCountryCode);

        if (wifiCountryCode == null) {
            return;
        }
        assertEquals(2, wifiCountryCode.length());

        // assert that the country code is all uppercase
        assertEquals(wifiCountryCode.toUpperCase(Locale.US), wifiCountryCode);

        // skip if Telephony is unsupported
        if (!WifiFeature.isTelephonySupported(sContext)) {
            return;
        }

        String telephonyCountryCode = sContext.getSystemService(TelephonyManager.class)
                .getNetworkCountryIso();

        // skip if Telephony country code is unavailable
        if (telephonyCountryCode == null || telephonyCountryCode.isEmpty()) {
            return;
        }

        assertEquals(telephonyCountryCode, wifiCountryCode.toLowerCase(Locale.US));
    }

    /**
     * Helper function to test getCurrentNetwork
     * @param shouldDisableWifi true to disable wifi, false to disconnect
     * @throws Exception
     */
    private void testGetCurrentNetwork(boolean shouldDisableWifi) throws Exception {
        // ensure Wifi is connected
        ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.reconnect());
        PollingCheck.check(
                "Connection info network id is invalid - Please ensure there is a " +
                " saved network in range of this device",
                WIFI_CONNECT_TIMEOUT_MILLIS,
                () -> sWifiManager.getConnectionInfo().getNetworkId() != -1);
        PollingCheck.check(
                "Wifi current network is null - Please ensure there is a saved network " +
                        " in range of this device",
                WIFI_CONNECT_TIMEOUT_MILLIS,
                () -> ShellIdentityUtils.invokeWithShellPermissions(sWifiManager::getCurrentNetwork)
                        != null);

        String networkKey = sWifiManager.getConnectionInfo().getNetworkKey();
        assertNotNull(networkKey);

        Network wifiCurrentNetwork = ShellIdentityUtils.invokeWithShellPermissions(
                sWifiManager::getCurrentNetwork);
        assertNotNull(wifiCurrentNetwork);

        List<WifiConfiguration> configuredNetwork = ShellIdentityUtils.invokeWithShellPermissions(
                sWifiManager::getConfiguredNetworks);

        boolean isNetworkKeyExist = false;
        for (WifiConfiguration config : configuredNetwork) {
            if (config.getAllNetworkKeys().contains(networkKey)) {
                isNetworkKeyExist = true;
                break;
            }
        }

        assertTrue(isNetworkKeyExist);

        TestNetworkCallback networkCallbackListener = new TestNetworkCallback(mLock);
        synchronized (mLock) {
            try {
                // File a request for wifi network.
                sConnectivityManager.registerNetworkCallback(
                        new NetworkRequest.Builder()
                                .addTransportType(TRANSPORT_WIFI)
                                .build(),
                        networkCallbackListener);
                // now wait for callback
                mLock.wait(TEST_WAIT_DURATION_MS);
            } catch (InterruptedException e) {
            }
        }
        assertTrue(networkCallbackListener.onAvailableCalled);
        Network connectivityCurrentNetwork = networkCallbackListener.network;
        assertEquals(connectivityCurrentNetwork, wifiCurrentNetwork);

        if (shouldDisableWifi) {
            setWifiEnabled(false);
            PollingCheck.check(
                    "Wifi not disabled!",
                    20000,
                    () -> !sWifiManager.isWifiEnabled());
        } else {
            ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.disconnect());
        }
        PollingCheck.check(
                "Wifi not disconnected! Connection info network id still valid",
                20000,
                () -> sWifiManager.getConnectionInfo().getNetworkId() == -1);

        PollingCheck.check(
                "Wifi not disconnected! Current network is not null",
                WIFI_CONNECT_TIMEOUT_MILLIS,
                () -> ShellIdentityUtils.invokeWithShellPermissions(sWifiManager::getCurrentNetwork)
                        == null);
    }

    /**
     * Test that {@link WifiManager#getCurrentNetwork()} returns a Network object consistent
     * with {@link ConnectivityManager#registerNetworkCallback} when connected to a Wifi network,
     * and returns null when disconnected.
     */
    @Test
    public void testGetCurrentNetworkWifiDisconnected() throws Exception {
        testGetCurrentNetwork(false);
    }

    /**
     * Test that {@link WifiManager#getCurrentNetwork()} returns a Network object consistent
     * with {@link ConnectivityManager#registerNetworkCallback} when connected to a Wifi network,
     * and returns null when wifi disabled.
     */
    @Test
    public void testGetCurrentNetworkWifiDisabled() throws Exception {
        testGetCurrentNetwork(true);
    }

    /**
     * Tests {@link WifiManager#isWpa3SaeSupported()} does not crash.
     */
    @Test
    public void testIsWpa3SaeSupported() throws Exception {
        sWifiManager.isWpa3SaeSupported();
    }

    /**
     * Tests {@link WifiManager#isWpa3SuiteBSupported()} does not crash.
     */
    @Test
    public void testIsWpa3SuiteBSupported() throws Exception {
        sWifiManager.isWpa3SuiteBSupported();
    }

    /**
     * Tests {@link WifiManager#isEnhancedOpenSupported()} does not crash.
     */
    @Test
    public void testIsEnhancedOpenSupported() throws Exception {
        sWifiManager.isEnhancedOpenSupported();
    }

    /**
     * Test that {@link WifiManager#is5GHzBandSupported()} returns successfully in
     * both WiFi enabled/disabled states.
     * Note that the response depends on device support and hence both true/false
     * are valid responses.
     */
    @Test
    public void testIs5GhzBandSupported() throws Exception {
        // Check for 5GHz support with wifi enabled
        setWifiEnabled(true);
        PollingCheck.check(
                "Wifi not enabled!",
                20000,
                () -> sWifiManager.isWifiEnabled());
        boolean isSupportedEnabled = sWifiManager.is5GHzBandSupported();

        // Check for 5GHz support with wifi disabled
        setWifiEnabled(false);
        PollingCheck.check(
                "Wifi not disabled!",
                20000,
                () -> !sWifiManager.isWifiEnabled());
        boolean isSupportedDisabled = sWifiManager.is5GHzBandSupported();

        // If Support is true when WiFi is disable, then it has to be true when it is enabled.
        // Note, the reverse is a valid case.
        if (isSupportedDisabled) {
            assertTrue(isSupportedEnabled);
        }
    }

    /**
     * Test that {@link WifiManager#is6GHzBandSupported()} returns successfully in
     * both Wifi enabled/disabled states.
     * Note that the response depends on device support and hence both true/false
     * are valid responses.
     */
    @Test
    public void testIs6GhzBandSupported() throws Exception {
        // Check for 6GHz support with wifi enabled
        setWifiEnabled(true);
        PollingCheck.check(
                "Wifi not enabled!",
                20000,
                () -> sWifiManager.isWifiEnabled());
        boolean isSupportedEnabled = sWifiManager.is6GHzBandSupported();

        // Check for 6GHz support with wifi disabled
        setWifiEnabled(false);
        PollingCheck.check(
                "Wifi not disabled!",
                20000,
                () -> !sWifiManager.isWifiEnabled());
        boolean isSupportedDisabled = sWifiManager.is6GHzBandSupported();

        // If Support is true when WiFi is disable, then it has to be true when it is enabled.
        // Note, the reverse is a valid case.
        if (isSupportedDisabled) {
            assertTrue(isSupportedEnabled);
        }
    }

    /**
     * Test that {@link WifiManager#is60GHzBandSupported()} returns successfully in
     * both Wifi enabled/disabled states.
     * Note that the response depends on device support and hence both true/false
     * are valid responses.
     */
    @Test
    public void testIs60GhzBandSupported() throws Exception {
        if (!(WifiFeature.isWifiSupported(sContext)
                && ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S))) {
            // skip the test if WiFi is not supported
            return;
        }

        // Check for 60GHz support with wifi enabled
        setWifiEnabled(true);
        PollingCheck.check(
                "Wifi not enabled!",
                20000,
                () -> sWifiManager.isWifiEnabled());
        boolean isSupportedEnabled = sWifiManager.is60GHzBandSupported();

        // Check for 60GHz support with wifi disabled
        setWifiEnabled(false);
        PollingCheck.check(
                "Wifi not disabled!",
                20000,
                () -> !sWifiManager.isWifiEnabled());
        boolean isSupportedDisabled = sWifiManager.is60GHzBandSupported();

        // If Support is true when WiFi is disable, then it has to be true when it is enabled.
        // Note, the reverse is a valid case.
        if (isSupportedDisabled) {
            assertTrue(isSupportedEnabled);
        }
    }

    /**
     * Test that {@link WifiManager#isWifiStandardSupported()} returns successfully in
     * both Wifi enabled/disabled states. The test is to be performed on
     * {@link WifiAnnotations}'s {@code WIFI_STANDARD_}
     * Note that the response depends on device support and hence both true/false
     * are valid responses.
     */
    @Test
    public void testIsWifiStandardsSupported() throws Exception {
        // Check for WiFi standards support with wifi enabled
        setWifiEnabled(true);
        PollingCheck.check(
                "Wifi not enabled!",
                20000,
                () -> sWifiManager.isWifiEnabled());
        boolean isLegacySupportedEnabled =
                sWifiManager.isWifiStandardSupported(ScanResult.WIFI_STANDARD_LEGACY);
        boolean is11nSupporedEnabled =
                sWifiManager.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11N);
        boolean is11acSupportedEnabled =
                sWifiManager.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AC);
        boolean is11axSupportedEnabled =
                sWifiManager.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AX);
        boolean is11beSupportedEnabled =
                sWifiManager.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE);

        // Check for WiFi standards support with wifi disabled
        setWifiEnabled(false);
        PollingCheck.check(
                "Wifi not disabled!",
                20000,
                () -> !sWifiManager.isWifiEnabled());

        boolean isLegacySupportedDisabled =
                sWifiManager.isWifiStandardSupported(ScanResult.WIFI_STANDARD_LEGACY);
        boolean is11nSupportedDisabled =
                sWifiManager.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11N);
        boolean is11acSupportedDisabled =
                sWifiManager.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AC);
        boolean is11axSupportedDisabled =
                sWifiManager.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AX);
        boolean is11beSupportedDisabled =
                sWifiManager.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE);

        if (isLegacySupportedDisabled) {
            assertTrue(isLegacySupportedEnabled);
        }

        if (is11nSupportedDisabled) {
            assertTrue(is11nSupporedEnabled);
        }

        if (is11acSupportedDisabled) {
            assertTrue(is11acSupportedEnabled);
        }

        if (is11axSupportedDisabled) {
            assertTrue(is11axSupportedEnabled);
        }

        if (is11beSupportedDisabled) {
            assertTrue(is11beSupportedEnabled);
        }
    }

    private static PasspointConfiguration createPasspointConfiguration() {
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("test.com");
        homeSp.setFriendlyName("friendly name");
        homeSp.setRoamingConsortiumOis(new long[]{0x55, 0x66});
        config.setHomeSp(homeSp);
        Credential.SimCredential simCred = new Credential.SimCredential();
        simCred.setImsi("123456*");
        simCred.setEapType(23 /* EAP_AKA */);
        Credential cred = new Credential();
        cred.setRealm("realm");
        cred.setSimCredential(simCred);
        config.setCredential(cred);

        return config;
    }

    /**
     * Tests {@link WifiManager#addOrUpdatePasspointConfiguration(PasspointConfiguration)}
     * adds a Passpoint configuration correctly by getting it once it is added, and comparing it
     * to the local copy of the configuration.
     */
    @Test
    public void testAddOrUpdatePasspointConfiguration() throws Exception {
        // Create and install a Passpoint configuration
        PasspointConfiguration passpointConfiguration = createPasspointConfiguration();
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiManager.addOrUpdatePasspointConfiguration(passpointConfiguration);

            // Compare configurations
            List<PasspointConfiguration> configurations = sWifiManager.getPasspointConfigurations();
            assertNotNull("The installed passpoint profile is missing", configurations);
            assertEquals(passpointConfiguration, getTargetPasspointConfiguration(configurations,
                    passpointConfiguration.getUniqueId()));
        } finally {
            // Clean up
            sWifiManager.removePasspointConfiguration(passpointConfiguration.getHomeSp().getFqdn());
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#setPasspointMeteredOverride(String, int)}
     * adds a Passpoint configuration correctly, check the default metered setting. Use API change
     * metered override, verify Passpoint configuration changes with it.
     */
    @Test
    public void testSetPasspointMeteredOverride() throws Exception {
        // Create and install a Passpoint configuration
        PasspointConfiguration passpointConfiguration = createPasspointConfiguration();
        String fqdn = passpointConfiguration.getHomeSp().getFqdn();
        String uniqueId = passpointConfiguration.getUniqueId();
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiManager.addOrUpdatePasspointConfiguration(passpointConfiguration);
            PasspointConfiguration saved = getTargetPasspointConfiguration(
                    sWifiManager.getPasspointConfigurations(), uniqueId);
            assertNotNull("The installed passpoint profile is missing", saved);
            // Verify meter override setting.
            assertEquals("Metered overrider default should be none",
                    WifiConfiguration.METERED_OVERRIDE_NONE, saved.getMeteredOverride());
            // Change the meter override setting.
            sWifiManager.setPasspointMeteredOverride(fqdn,
                    WifiConfiguration.METERED_OVERRIDE_METERED);
            // Verify passpoint config change with the new setting.
            saved = getTargetPasspointConfiguration(
                    sWifiManager.getPasspointConfigurations(), uniqueId);
            assertNotNull("The installed passpoint profile is missing", saved);
            assertEquals("Metered override should be metered",
                    WifiConfiguration.METERED_OVERRIDE_METERED, saved.getMeteredOverride());
        } finally {
            // Clean up
            sWifiManager.removePasspointConfiguration(fqdn);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests that
     * {@link WifiManager#startSubscriptionProvisioning(OsuProvider, Executor, ProvisioningCallback)}
     * starts a subscription provisioning, and confirm a status callback invoked once.
     */
    @Test
    public void testStartSubscriptionProvisioning() throws Exception {
        // Using Java reflection to construct an OsuProvider instance because its constructor is
        // hidden and not available to apps.
        Class<?> osuProviderClass = Class.forName("android.net.wifi.hotspot2.OsuProvider");
        Constructor<?> osuProviderClassConstructor = osuProviderClass.getConstructor(String.class,
                Map.class, String.class, Uri.class, String.class, List.class);

        OsuProvider osuProvider = (OsuProvider) osuProviderClassConstructor.newInstance(TEST_SSID,
                TEST_FRIENDLY_NAMES, TEST_SERVICE_DESCRIPTION, TEST_SERVER_URI, TEST_NAI,
                TEST_METHOD_LIST);
        TestProvisioningCallback callback = new TestProvisioningCallback(mLock);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            synchronized (mLock) {
                // Start a subscription provisioning for a non-existent Passpoint R2 AP
                sWifiManager.startSubscriptionProvisioning(osuProvider, mExecutor, callback);
                mLock.wait(TEST_WAIT_DURATION_MS);
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        waitForDisconnection();
        // Expect only a single callback event, connecting. Since AP doesn't exist, it ends here
        assertEquals(ProvisioningCallback.OSU_STATUS_AP_CONNECTING, callback.mProvisioningStatus);
        // No failure callbacks expected
        assertEquals(0, callback.mProvisioningFailureStatus);
        // No completion callback expected
        assertFalse(callback.mProvisioningComplete);
        ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.setWifiEnabled(false));
        PollingCheck.check("Wifi not disabled!", 20000,
                () -> !sWifiManager.isWifiEnabled());
    }

    /**
     * Tests {@link WifiManager#setTdlsEnabled(InetAddress, boolean)} does not crash.
     */
    @Test
    public void testSetTdlsEnabled() throws Exception {
        InetAddress inetAddress = InetAddress.getByName(TEST_IP_ADDRESS);

        sWifiManager.setTdlsEnabled(inetAddress, true);
        Thread.sleep(50);
        sWifiManager.setTdlsEnabled(inetAddress, false);
    }

    /**
     * Tests {@link WifiManager#setTdlsEnabledWithMacAddress(String, boolean)} does not crash.
     */
    @Test
    public void testSetTdlsEnabledWithMacAddress() throws Exception {
        sWifiManager.setTdlsEnabledWithMacAddress(TEST_MAC_ADDRESS, true);
        Thread.sleep(50);
        sWifiManager.setTdlsEnabledWithMacAddress(TEST_MAC_ADDRESS, false);
    }

    /**
     * Verify the usage of {@code WifiManager#isTdlsOperationCurrentlyAvailable}.
     */
    @Test
    public void testIsTdlsOperationCurrentlyAvailable() throws Exception {
        boolean expectedResult = sWifiManager.isTdlsSupported();
        AtomicBoolean enabled = new AtomicBoolean(false);
        sWifiManager.isTdlsOperationCurrentlyAvailable(mExecutor,
                (enabledLocal) -> {
                    synchronized (mLock) {
                        enabled.set(enabledLocal);
                        mLock.notify();
                    }
                });
        synchronized (mLock) {
            mLock.wait(TEST_WAIT_DURATION_MS);
        }
        assertEquals(expectedResult, enabled.get());
    }

    /**
     * Verify the usage of {@code WifiManager#getMaxSupportedConcurrentTdlsSessions}.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void testGetMaxSupportedConcurrentTdlsSessions() throws Exception {
        if (!sWifiManager.isTdlsSupported()) {
            // skip the test if TDLS is not supported
            return;
        }

        AtomicInteger maxNumOfTdlsSessions = new AtomicInteger(0);
        sWifiManager.getMaxSupportedConcurrentTdlsSessions(mExecutor,
                (maxNumOfTdlsSessionsLocal) -> {
                    synchronized (mLock) {
                        maxNumOfTdlsSessions.set(maxNumOfTdlsSessionsLocal);
                        mLock.notify();
                    }
                });
        synchronized (mLock) {
            mLock.wait(TEST_WAIT_DURATION_MS);
        }
        // {@code WifiManager#getMaxSupportedConcurrentTdlsSessions} returns -1
        // if HAL doesn't provide this TDLS capability.
        assertTrue(maxNumOfTdlsSessions.get() == -1 || maxNumOfTdlsSessions.get() > 0);
    }

    /**
     * Verify the usage of
     * {@link WifiManager#setTdlsEnabled(InetAddress, boolean, Executor, Consumer)}.
     */
    @Test
    public void testSetTdlsEnabledWithIpAddressConsumerModel() throws Exception {
        if (!sWifiManager.isTdlsSupported()) {
            // skip the test if TDLS is not supported
            return;
        }

        InetAddress inetAddress = InetAddress.getByName(TEST_IP_ADDRESS);
        sWifiManager.setTdlsEnabled(inetAddress, true, mExecutor, (e) -> {});
        sWifiManager.setTdlsEnabled(inetAddress, false, mExecutor, (e) -> {});
    }

    /**
     * Verify the usage of
     * {@link WifiManager#setTdlsEnabledWithMacAddress(String, boolean, Executor, Consumer)}
     * and {@link WifiManager#getNumberOfEnabledTdlsSessions(Executor, Consumer)}.
     */
    @Test
    public void testSetTdlsEnabledWithMacAddressConsumerModel() throws Exception {
        if (!sWifiManager.isTdlsSupported()) {
            // skip the test if TDLS is not supported
            return;
        }

        AtomicBoolean enabled = new AtomicBoolean(false);
        AtomicInteger numOfTdlsSessions = new AtomicInteger(0);
        Consumer<Integer> listener2 = new Consumer<Integer>() {
            @Override
            public void accept(Integer value) {
                synchronized (mLock) {
                    numOfTdlsSessions.set(value);
                    mLock.notify();
                }
            }
        };

        sWifiManager.setTdlsEnabledWithMacAddress(TEST_MAC_ADDRESS, true, mExecutor,
                (enabledLocal) -> {
                    synchronized (mLock) {
                        enabled.set(enabledLocal);
                        mLock.notify();
                    }
                });
        synchronized (mLock) {
            mLock.wait(TEST_WAIT_DURATION_MS);
        }
        assertTrue(enabled.get());
        sWifiManager.getNumberOfEnabledTdlsSessions(mExecutor, listener2);
        synchronized (mLock) {
            mLock.wait(TEST_WAIT_DURATION_MS);
        }
        assertEquals(1, numOfTdlsSessions.get());

        sWifiManager.setTdlsEnabledWithMacAddress(TEST_MAC_ADDRESS, false, mExecutor, (e) -> {});
        sWifiManager.getNumberOfEnabledTdlsSessions(mExecutor, listener2);
        synchronized (mLock) {
            mLock.wait(TEST_WAIT_DURATION_MS);
        }
        assertEquals(0, numOfTdlsSessions.get());
    }

    /**
     * Verify WifiNetworkSuggestion.Builder.setMacRandomizationSetting(WifiNetworkSuggestion
     * .RANDOMIZATION_NON_PERSISTENT) creates a
     * WifiConfiguration with macRandomizationSetting == RANDOMIZATION_NON_PERSISTENT.
     * Then verify by default, a WifiConfiguration created by suggestions should have
     * macRandomizationSetting == RANDOMIZATION_PERSISTENT.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testSuggestionBuilderNonPersistentRandomization() throws Exception {
        WifiNetworkSuggestion suggestion = new WifiNetworkSuggestion.Builder()
                .setSsid(TEST_SSID).setWpa2Passphrase(TEST_PASSPHRASE)
                .setMacRandomizationSetting(WifiNetworkSuggestion.RANDOMIZATION_NON_PERSISTENT)
                .build();
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                sWifiManager.addNetworkSuggestions(Arrays.asList(suggestion)));
        verifySuggestionFoundWithMacRandomizationSetting(TEST_SSID,
                WifiNetworkSuggestion.RANDOMIZATION_NON_PERSISTENT);

        suggestion = new WifiNetworkSuggestion.Builder()
                .setSsid(TEST_SSID).setWpa2Passphrase(TEST_PASSPHRASE)
                .build();
        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                sWifiManager.addNetworkSuggestions(Arrays.asList(suggestion)));
        verifySuggestionFoundWithMacRandomizationSetting(TEST_SSID,
                WifiNetworkSuggestion.RANDOMIZATION_PERSISTENT);
    }

    private void verifySuggestionFoundWithMacRandomizationSetting(String ssid,
            int macRandomizationSetting) {
        List<WifiNetworkSuggestion> retrievedSuggestions = sWifiManager.getNetworkSuggestions();
        for (WifiNetworkSuggestion entry : retrievedSuggestions) {
            if (entry.getSsid().equals(ssid)) {
                assertEquals(macRandomizationSetting, entry.getMacRandomizationSetting());
                return; // pass test after the MAC randomization setting is verified.
            }
        }
        fail("WifiNetworkSuggestion not found for SSID=" + ssid + ", macRandomizationSetting="
                + macRandomizationSetting);
    }

    /**
     * Tests {@link WifiManager#getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(List)}
     */
    @Test
    public void testGetAllWifiConfigForMatchedNetworkSuggestion() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        ScanResult scanResult = new ScanResult();
        scanResult.SSID = TEST_SSID;
        scanResult.capabilities = TEST_PSK_CAP;
        scanResult.BSSID = TEST_BSSID;
        List<ScanResult> testList = Arrays.asList(scanResult);
        WifiNetworkSuggestion suggestion = new WifiNetworkSuggestion.Builder()
                .setSsid(TEST_SSID).setWpa2Passphrase(TEST_PASSPHRASE).build();

        assertEquals(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
                sWifiManager.addNetworkSuggestions(Arrays.asList(suggestion)));
        List<WifiConfiguration> matchedResult;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            matchedResult = sWifiManager
                    .getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(testList);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        // As suggestion is not approved, will return empty list.
        assertTrue(matchedResult.isEmpty());
    }

    /**
     * Tests {@link WifiManager#getMatchingScanResults(List, List)}
     */
    @Test
    public void testGetMatchingScanResults() {
        // Create pair of ScanResult and WifiNetworkSuggestion
        ScanResult scanResult = new ScanResult();
        scanResult.SSID = TEST_SSID;
        scanResult.capabilities = TEST_PSK_CAP;
        scanResult.BSSID = TEST_BSSID;

        WifiNetworkSuggestion suggestion = new WifiNetworkSuggestion.Builder()
                .setSsid(TEST_SSID).setWpa2Passphrase(TEST_PASSPHRASE).build();

        Map<WifiNetworkSuggestion, List<ScanResult>> matchedResults = sWifiManager
                .getMatchingScanResults(Arrays.asList(suggestion), Arrays.asList(scanResult));
        // Verify result is matched pair of ScanResult and WifiNetworkSuggestion
        assertEquals(scanResult.SSID, matchedResults.get(suggestion).get(0).SSID);

        // Change ScanResult to unmatched should return empty result.
        scanResult.SSID = TEST_SSID_UNQUOTED;
        matchedResults = sWifiManager
                .getMatchingScanResults(Arrays.asList(suggestion), Arrays.asList(scanResult));
        assertTrue(matchedResults.get(suggestion).isEmpty());
    }

    /**
     * Tests {@link WifiManager#disableEphemeralNetwork(String)}.
     */
    @Test
    public void testDisableEphemeralNetwork() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        List<WifiConfiguration> savedNetworks = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // Temporarily disable on all networks.
            savedNetworks = sWifiManager.getConfiguredNetworks();
            for (WifiConfiguration network : savedNetworks) {
                sWifiManager.disableEphemeralNetwork(network.SSID);
            }
            // trigger a disconnect and wait for disconnect.
            sWifiManager.disconnect();
            waitForDisconnection();

            // Now trigger scan and ensure that the device does not connect to any networks.
            sWifiManager.startScan();
            ensureNotConnected();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
            setWifiEnabled(false);
        }
    }

    /**
     * Tests {@link WifiManager#allowAutojoin(int, boolean)}.
     */
    @Test
    public void testAllowAutojoin() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        List<WifiConfiguration> savedNetworks = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // disable autojoin on all networks.
            savedNetworks = sWifiManager.getConfiguredNetworks();
            for (WifiConfiguration network : savedNetworks) {
                sWifiManager.allowAutojoin(network.networkId, false);
            }
            // trigger a disconnect and wait for disconnect.
            sWifiManager.disconnect();
            waitForDisconnection();

            // Now trigger scan and ensure that the device does not connect to any networks.
            sWifiManager.startScan();
            ensureNotConnected();

            // Now enable autojoin on all networks.
            for (WifiConfiguration network : savedNetworks) {
                sWifiManager.allowAutojoin(network.networkId, true);
            }

            // Trigger a scan & wait for connection to one of the saved networks.
            sWifiManager.startScan();
            waitForConnection();
        } finally {
            // Restore auto join state.
            if (savedNetworks != null) {
                for (WifiConfiguration network : savedNetworks) {
                    sWifiManager.allowAutojoin(network.networkId, network.allowAutojoin);
                }
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#allowAutojoinPasspoint(String, boolean)}.
     */
    @Test
    public void testAllowAutojoinPasspoint() throws Exception {
        PasspointConfiguration passpointConfiguration = createPasspointConfiguration();
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiManager.addOrUpdatePasspointConfiguration(passpointConfiguration);
            // Turn off auto-join
            sWifiManager.allowAutojoinPasspoint(
                    passpointConfiguration.getHomeSp().getFqdn(), false);
            // Turn on auto-join
            sWifiManager.allowAutojoinPasspoint(
                    passpointConfiguration.getHomeSp().getFqdn(), true);
        } finally {
            sWifiManager.removePasspointConfiguration(passpointConfiguration.getHomeSp().getFqdn());
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#allowAutojoinGlobal(boolean)}.
     */
    @Test
    public void testAllowAutojoinGlobal() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // disable autojoin on all networks.
            sWifiManager.allowAutojoinGlobal(false);

            // trigger a disconnect and wait for disconnect.
            sWifiManager.disconnect();
            waitForDisconnection();

            // Now trigger scan and ensure that the device does not connect to any networks.
            sWifiManager.startScan();
            ensureNotConnected();

            // verify null is returned when attempting to get current configured network.
            WifiConfiguration config = sWifiManager.getPrivilegedConnectedNetwork();
            assertNull("config should be null because wifi is not connected", config);

            // Now enable autojoin on all networks.
            sWifiManager.allowAutojoinGlobal(true);

            // Trigger a scan & wait for connection to one of the saved networks.
            sWifiManager.startScan();
            waitForConnection();
        } finally {
            // Re-enable auto join if the test fails for some reason.
            sWifiManager.allowAutojoinGlobal(true);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Verify the invalid and valid usages of {@code WifiManager#queryAutojoinGlobal}.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testQueryAutojoinGlobal() throws Exception {
        AtomicBoolean enabled = new AtomicBoolean(false);
        Consumer<Boolean> listener = new Consumer<Boolean>() {
            @Override
            public void accept(Boolean value) {
                synchronized (mLock) {
                    enabled.set(value);
                    mLock.notify();
                }
            }
        };
        // Test invalid inputs trigger IllegalArgumentException
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> sWifiManager.queryAutojoinGlobal(null, listener));
        assertThrows("null listener should trigger exception", NullPointerException.class,
                () -> sWifiManager.queryAutojoinGlobal(mExecutor, null));

        // Test caller with no permission triggers SecurityException.
        assertThrows("No permission should trigger SecurityException", SecurityException.class,
                () -> sWifiManager.queryAutojoinGlobal(mExecutor, listener));

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // Test get/set autojoin global enabled
            sWifiManager.allowAutojoinGlobal(true);
            sWifiManager.queryAutojoinGlobal(mExecutor, listener);
            synchronized (mLock) {
                mLock.wait(TEST_WAIT_DURATION_MS);
            }
            assertTrue(enabled.get());

            // Test get/set autojoin global disabled
            sWifiManager.allowAutojoinGlobal(false);
            sWifiManager.queryAutojoinGlobal(mExecutor, listener);
            synchronized (mLock) {
                mLock.wait(TEST_WAIT_DURATION_MS);
            }
            assertFalse(enabled.get());
        } finally {
            // Re-enable auto join if the test fails for some reason.
            sWifiManager.allowAutojoinGlobal(true);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#setPerSsidRoamingMode(WifiSsid, int)},
     * {@link WifiManager#getPerSsidRoamingModes(Executor, Consumer)},
     * and {@link WifiManager#removePerSsidRoamingMode(WifiSsid)}.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    public void testPerSsidRoamingMode() throws Exception {
        if (!sWifiManager.isAggressiveRoamingModeSupported()) {
            // skip the test if aggressive roaming mode is not supported
            return;
        }
        WifiSsid testSsid = WifiSsid.fromBytes("TEST_SSID_1".getBytes(StandardCharsets.UTF_8));
        Map<String, Integer> roamingModes = new HashMap<>();
        Consumer<Map<String, Integer>> listener = new Consumer<Map<String, Integer>>() {
            @Override
            public void accept(Map<String, Integer> value) {
                synchronized (mLock) {
                    roamingModes.clear();
                    roamingModes.putAll(value);
                    mLock.notify();
                }
            }
        };

        // Test caller with no permission triggers SecurityException.
        assertThrows("No permission should trigger SecurityException", SecurityException.class,
                () -> sWifiManager.setPerSsidRoamingMode(testSsid,
                        WifiManager.ROAMING_MODE_AGGRESSIVE));
        assertThrows("No permission should trigger SecurityException", SecurityException.class,
                () -> sWifiManager.removePerSsidRoamingMode(testSsid));
        assertThrows("No permission should trigger SecurityException", SecurityException.class,
                () -> sWifiManager.getPerSsidRoamingModes(mExecutor, listener));

        // Test that invalid inputs trigger an Exception.
        assertThrows("null WifiSsid should trigger exception", NullPointerException.class,
                () -> sWifiManager.setPerSsidRoamingMode(null,
                        WifiManager.ROAMING_MODE_AGGRESSIVE));
        assertThrows("null WifiSsid should trigger exception", NullPointerException.class,
                () -> sWifiManager.removePerSsidRoamingMode(null));
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> sWifiManager.getPerSsidRoamingModes(null, listener));
        assertThrows("null listener should trigger exception", NullPointerException.class,
                () -> sWifiManager.getPerSsidRoamingModes(mExecutor, null));

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiManager.setPerSsidRoamingMode(testSsid, WifiManager.ROAMING_MODE_AGGRESSIVE);
            sWifiManager.getPerSsidRoamingModes(mExecutor, listener);
            Thread.sleep(TEST_WAIT_DURATION_MS);
            assertTrue(
                    roamingModes.get(testSsid.toString()) == WifiManager.ROAMING_MODE_AGGRESSIVE);
            sWifiManager.removePerSsidRoamingMode(testSsid);
            sWifiManager.getPerSsidRoamingModes(mExecutor, listener);
            Thread.sleep(TEST_WAIT_DURATION_MS);
            assertNull(roamingModes.get(testSsid.toString()));
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Verify the invalid and valid usages of {@code WifiManager#setPnoScanState}.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    public void testSetPnoScanState() throws Exception {
        // Test caller with no permission triggers SecurityException.
        assertThrows("No permission should trigger SecurityException", SecurityException.class,
                () -> sWifiManager.setPnoScanState(WifiManager.PNO_SCAN_STATE_ENABLED));

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiManager.setPnoScanState(WifiManager.PNO_SCAN_STATE_ENABLED);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#isWapiSupported()} does not crash.
     */
    @Test
    public void testIsWapiSupported() throws Exception {
        sWifiManager.isWapiSupported();
    }

    /**
     * Tests {@link WifiManager#isWpa3SaePublicKeySupported()} does not crash.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testIsWpa3SaePublicKeySupported() throws Exception {
        sWifiManager.isWpa3SaePublicKeySupported();
    }

    /**
     * Tests {@link WifiManager#isWpa3SaeH2eSupported()} does not crash.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testIsWpa3SaeH2eSupported() throws Exception {
        sWifiManager.isWpa3SaeH2eSupported();
    }

    /**
     * Tests {@link WifiManager#isWifiDisplayR2Supported()} does not crash.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testIsWifiDisplayR2Supported() throws Exception {
        sWifiManager.isWifiDisplayR2Supported();
    }

    /**
     * Tests {@link WifiManager#isP2pSupported()} returns true
     * if this device supports it, otherwise, ensure no crash.
     */
    @Test
    public void testIsP2pSupported() throws Exception {
        if (WifiFeature.isP2pSupported(sContext)) {
            // if this device supports P2P, ensure hw capability is correct.
            assertTrue(sWifiManager.isP2pSupported());
        } else {
            // ensure no crash.
            sWifiManager.isP2pSupported();
        }

    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testIsMultiStaConcurrencySupported() throws Exception {
        // ensure no crash.
        sWifiManager.isStaApConcurrencySupported();
    }

    private PasspointConfiguration getTargetPasspointConfiguration(
            List<PasspointConfiguration> configurationList, String uniqueId) {
        if (configurationList == null || configurationList.isEmpty()) {
            return null;
        }
        for (PasspointConfiguration config : configurationList) {
            if (TextUtils.equals(config.getUniqueId(), uniqueId)) {
                return config;
            }
        }
        return null;
    }

    /**
     * Test that {@link WifiManager#is60GHzBandSupported()} throws UnsupportedOperationException
     * if the release is older than S.
     */
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.R)
    @Test
    public void testIs60GhzBandSupportedOnROrOlder() throws Exception {
        // check for 60ghz support with wifi enabled
        try {
            sWifiManager.is60GHzBandSupported();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ex) {
        }
    }

    /**
     * Test that {@link WifiManager#is60GHzBandSupported()} returns successfully in
     * both Wifi enabled/disabled states for release newer than R.
     * Note that the response depends on device support and hence both true/false
     * are valid responses.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testIs60GhzBandSupportedOnSOrNewer() throws Exception {
        // check for 60ghz support with wifi enabled
        boolean isSupportedWhenWifiEnabled = sWifiManager.is60GHzBandSupported();

        // Check for 60GHz support with wifi disabled
        setWifiEnabled(false);
        PollingCheck.check(
                "Wifi not disabled!",
                20000,
                () -> !sWifiManager.isWifiEnabled());
        boolean isSupportedWhenWifiDisabled = sWifiManager.is60GHzBandSupported();

        // If Support is true when WiFi is disable, then it has to be true when it is enabled.
        // Note, the reverse is a valid case.
        if (isSupportedWhenWifiDisabled) {
            assertTrue(isSupportedWhenWifiEnabled);
        }
    }

    /**
     * Tests {@link WifiManager#isTrustOnFirstUseSupported()} does not crash.
     */
    // TODO(b/196180536): Wait for T SDK finalization before changing
    // to `@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)`
    @SdkSuppress(minSdkVersion = 31)
    @Test
    public void testIsTrustOnFirstUseSupported() throws Exception {
        sWifiManager.isTrustOnFirstUseSupported();
    }

    public class TestCoexCallback extends WifiManager.CoexCallback {
        private Object mCoexLock;
        private int mOnCoexUnsafeChannelChangedCount;
        private List<CoexUnsafeChannel> mCoexUnsafeChannels;
        private int mCoexRestrictions;

        TestCoexCallback(Object lock) {
            mCoexLock = lock;
        }

        @Override
        public void onCoexUnsafeChannelsChanged(
                    @NonNull List<CoexUnsafeChannel> unsafeChannels, int restrictions) {
            synchronized (mCoexLock) {
                mCoexUnsafeChannels = unsafeChannels;
                mCoexRestrictions = restrictions;
                mOnCoexUnsafeChannelChangedCount++;
                mCoexLock.notify();
            }
        }

        public int getOnCoexUnsafeChannelChangedCount() {
            synchronized (mCoexLock) {
                return mOnCoexUnsafeChannelChangedCount;
            }
        }

        public List<CoexUnsafeChannel> getCoexUnsafeChannels() {
            return mCoexUnsafeChannels;
        }

        public int getCoexRestrictions() {
            return mCoexRestrictions;
        }
    }

    /**
     * Test that coex-related methods fail without the needed privileged permissions
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testCoexMethodsShouldFailNoPermission() {
        try {
            sWifiManager.setCoexUnsafeChannels(Collections.emptyList(), 0);
            fail("setCoexUnsafeChannels should not succeed - privileged call");
        } catch (SecurityException e) {
            // expected
        }
        final TestCoexCallback callback = new TestCoexCallback(mLock);
        try {
            sWifiManager.registerCoexCallback(mExecutor, callback);
            fail("registerCoexCallback should not succeed - privileged call");
        } catch (SecurityException e) {
            // expected
        }
        try {
            sWifiManager.unregisterCoexCallback(callback);
            fail("unregisterCoexCallback should not succeed - privileged call");
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Test that coex-related methods succeed in setting the current unsafe channels and notifying
     * the listener. Since the default coex algorithm may be enabled, no-op is also valid behavior.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testListenOnCoexUnsafeChannels() {
        // These below API's only work with privileged permissions (obtained via shell identity
        // for test)
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        List<CoexUnsafeChannel> prevUnsafeChannels = new ArrayList<>();
        int prevRestrictions = -1;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            synchronized (mLock) {
                try {
                    boolean defaultAlgoEnabled = false;
                    final TestCoexCallback callback = new TestCoexCallback(mLock);
                    sWifiManager.registerCoexCallback(mExecutor, callback);

                    // Callback should be called after registering
                    mLock.wait(TEST_WAIT_DURATION_MS);
                    assertEquals(1, callback.getOnCoexUnsafeChannelChangedCount());

                    // Store the previous coex channels and try setting new coex channels 5 times.
                    //
                    // If the default algorithm is disabled, we'll get exactly 5 callbacks, and we
                    // can verify that the update channels match what we inputted.
                    //
                    // If the default algorithm is enabled, then the callbacks will trigger
                    // according to the algorithm, which may or may not trigger during the test.
                    // Thus we try 5 times and see if the callbacks match the number of tries, since
                    // it's highly unlikely that the default algorithm will update the channels
                    // exactly 5 times during the test.
                    prevUnsafeChannels = callback.getCoexUnsafeChannels();
                    prevRestrictions = callback.getCoexRestrictions();
                    List<CoexUnsafeChannel> testChannels = null;
                    final int testRestrictions = COEX_RESTRICTION_WIFI_DIRECT
                            | COEX_RESTRICTION_SOFTAP | COEX_RESTRICTION_WIFI_AWARE;
                    for (int i = 0; i < 5; i++) {
                        testChannels = List.of(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 1 + i));
                        sWifiManager.setCoexUnsafeChannels(testChannels, testRestrictions);
                        mLock.wait(TEST_WAIT_DURATION_MS);
                        if (callback.getOnCoexUnsafeChannelChangedCount() != i + 2) {
                            defaultAlgoEnabled = true;
                            break;
                        }
                    }

                    if (!defaultAlgoEnabled) {
                        int currentCallbackCount = callback.getOnCoexUnsafeChannelChangedCount();
                        assertEquals(testChannels, callback.getCoexUnsafeChannels());
                        assertEquals(testRestrictions, callback.getCoexRestrictions());
                        // Unregister callback and try setting again
                        sWifiManager.unregisterCoexCallback(callback);
                        sWifiManager.setCoexUnsafeChannels(
                                List.of(new CoexUnsafeChannel(WIFI_BAND_24_GHZ, 11)),
                                testRestrictions);
                        mLock.wait(TEST_WAIT_DURATION_MS);
                        // Callback should not be called here since it was unregistered.
                        assertThat(callback.getOnCoexUnsafeChannelChangedCount())
                                .isEqualTo(currentCallbackCount);
                    }
                } catch (InterruptedException e) {
                    fail("Thread interrupted unexpectedly while waiting on mLock");
                }
            }
        } finally {
            // Reset the previous unsafe channels if we overrode them.
            if (prevRestrictions != -1) {
                sWifiManager.setCoexUnsafeChannels(prevUnsafeChannels, prevRestrictions);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Verify that secure WPA-Enterprise network configurations can be added and updated.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testSecureEnterpriseConfigurationsAccepted() throws Exception {
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.SSID = SSID1;
        wifiConfiguration.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE);
        wifiConfiguration.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TTLS);
        int networkId = INVALID_NETWORK_ID;

        // These below API's only work with privileged permissions (obtained via shell identity
        // for test)
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();

            // Now configure it correctly with a Root CA cert and domain name
            wifiConfiguration.enterpriseConfig.setCaCertificate(FakeKeys.CA_CERT0);
            wifiConfiguration.enterpriseConfig.setAltSubjectMatch(TEST_DOM_SUBJECT_MATCH);

            // Verify that the network is added
            networkId = sWifiManager.addNetwork(wifiConfiguration);
            assertNotEquals(INVALID_NETWORK_ID, networkId);

            // Verify that the update API accepts configurations configured securely
            wifiConfiguration.networkId = networkId;
            assertEquals(networkId, sWifiManager.updateNetwork(wifiConfiguration));
        } finally {
            if (networkId != INVALID_NETWORK_ID) {
                // Clean up the previously added network
                sWifiManager.removeNetwork(networkId);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#isPasspointTermsAndConditionsSupported)} does not crash.
     */
    @Test
    public void testIsPasspointTermsAndConditionsSupported() throws Exception {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            // Skip the test if wifi module version is older than S.
            return;
        }
        sWifiManager.isPasspointTermsAndConditionsSupported();
    }

    /**
     * Test that call to {@link WifiManager#setOverrideCountryCode()},
     * {@link WifiManager#clearOverrideCountryCode()} and
     * {@link WifiManager#setDefaultCountryCode()} need privileged permission
     * and the permission is not even given to shell user.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testManageCountryCodeMethodsFailWithoutPermissions() throws Exception {
        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            try {
                sWifiManager.setOverrideCountryCode(TEST_COUNTRY_CODE);
                fail("setOverrideCountryCode() expected to fail - privileged call");
            } catch (SecurityException e) {
                // expected
            }

            try {
                sWifiManager.clearOverrideCountryCode();
                fail("clearOverrideCountryCode() expected to fail - privileged call");
            } catch (SecurityException e) {
                // expected
            }

            try {
                sWifiManager.setDefaultCountryCode(TEST_COUNTRY_CODE);
                fail("setDefaultCountryCode() expected to fail - privileged call");
            } catch (SecurityException e) {
                // expected
            }
        });
    }

    /**
     * Tests {@link WifiManager#flushPasspointAnqpCache)} does not crash.
     */
    @Test
    public void testFlushPasspointAnqpCache() throws Exception {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            // Skip the test if wifi module version is older than S.
            return;
        }
        // The below API only works with privileged permissions (obtained via shell identity
        // for test)
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiManager.flushPasspointAnqpCache();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#setWifiPasspointEnabled)} raise security exception without
     * permission.
     */
    // TODO(b/139192273): Wait for T SDK finalization before changing
    // to `@SdkSuppress(minSdkVersion = Build.VERSION_CODES.T)`
    @SdkSuppress(minSdkVersion = 31)
    @Test
    public void testEnablePasspointWithoutPermission() throws Exception {
        try {
            sWifiManager.setWifiPasspointEnabled(true);
            fail("setWifiPasspointEnabled() expected to fail - privileged call");
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Tests {@link WifiManager#setWifiPasspointEnabled)} does not crash and returns success.
     */
    // TODO(b/139192273): Wait for T SDK finalization before changing
    // to `@SdkSuppress(minSdkVersion = Build.VERSION_CODES.T)`
    @SdkSuppress(minSdkVersion = 31)
    @Test
    public void testEnablePasspoint() throws Exception {
        // The below API only works with privileged permissions (obtained via shell identity
        // for test)
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // Check if passpoint is enabled by default.
            assertTrue(sWifiManager.isWifiPasspointEnabled());
            // Try to disable passpoint
            sWifiManager.setWifiPasspointEnabled(false);
            PollingCheck.check("Wifi passpoint turn off failed!", 2_000,
                    () -> !sWifiManager.isWifiPasspointEnabled());
            // Try to enable passpoint
            sWifiManager.setWifiPasspointEnabled(true);
            PollingCheck.check("Wifi passpoint turn on failed!", 2_000,
                    () -> sWifiManager.isWifiPasspointEnabled());
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#isDecoratedIdentitySupported)} does not crash.
     */
    @Test
    public void testIsDecoratedIdentitySupported() throws Exception {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            // Skip the test if wifi module version is older than S.
            return;
        }
        sWifiManager.isDecoratedIdentitySupported();
    }

    /**
     * Tests {@link WifiManager#setCarrierNetworkOffloadEnabled)} and
     * {@link WifiManager#isCarrierNetworkOffloadEnabled} work as expected.
     */
    @Test
    public void testSetCarrierNetworkOffloadEnabled() {
        if (!WifiFeature.isWifiSupported(sContext)
                || !WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            // skip the test if WiFi is not supported
            return;
        }
        assertTrue(sWifiManager.isCarrierNetworkOffloadEnabled(TEST_SUB_ID, false));
        // The below API only works with privileged permissions (obtained via shell identity
        // for test)
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiManager.setCarrierNetworkOffloadEnabled(TEST_SUB_ID, false, false);
            assertFalse(sWifiManager.isCarrierNetworkOffloadEnabled(TEST_SUB_ID, false));
        } finally {
            sWifiManager.setCarrierNetworkOffloadEnabled(TEST_SUB_ID, false, true);
            uiAutomation.dropShellPermissionIdentity();
        }
        assertTrue(sWifiManager.isCarrierNetworkOffloadEnabled(TEST_SUB_ID, false));
    }

    /**
     * Test that {@link WifiManager#getUsableChannels(int, int)},
     * {@link WifiManager#getAllowedChannels(int, int)}
     * throws UnsupportedOperationException if the release is older than S.
     */
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.R)
    @Test
    public void testGetAllowedUsableChannelsOnROrOlder() throws Exception {
        try {
            sWifiManager.getAllowedChannels(WIFI_BAND_24_GHZ, OP_MODE_STA);
            fail("getAllowedChannels Expected to fail - UnsupportedOperationException");
        } catch (UnsupportedOperationException ex) {}

        try {
            sWifiManager.getUsableChannels(WIFI_BAND_24_GHZ, OP_MODE_STA);
            fail("getUsableChannels Expected to fail - UnsupportedOperationException");
        } catch (UnsupportedOperationException ex) {}
    }

    /**
     * Tests {@link WifiManager#getAllowedChannels(int, int))} does not crash
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testGetAllowedChannels() throws Exception {
        // The below API only works with privileged permissions (obtained via shell identity
        // for test)
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            WifiAvailableChannel channel = new WifiAvailableChannel(2412, OP_MODE_SAP);
            assertEquals(channel.getFrequencyMhz(), 2412);
            assertEquals(channel.getOperationalModes(), OP_MODE_SAP);
            final List<Integer> valid24GhzFreqs = Arrays.asList(
                2412, 2417, 2422, 2427, 2432, 2437, 2442,
                2447, 2452, 2457, 2462, 2467, 2472, 2484);
            Set<Integer> supported24GhzFreqs = new HashSet<Integer>();
            uiAutomation.adoptShellPermissionIdentity();
            List<WifiAvailableChannel> allowedChannels =
                    sWifiManager.getAllowedChannels(WIFI_BAND_24_GHZ, OP_MODE_STA);
            assertNotNull(allowedChannels);
            for (WifiAvailableChannel ch : allowedChannels) {
                //Must contain a valid 2.4GHz frequency
                assertTrue(valid24GhzFreqs.contains(ch.getFrequencyMhz()));
                if(ch.getFrequencyMhz() <= 2462) {
                    //Channels 1-11 are supported for STA in all countries
                    assertEquals(ch.getOperationalModes() & OP_MODE_STA, OP_MODE_STA);
                    supported24GhzFreqs.add(ch.getFrequencyMhz());
                }
            }
            //Channels 1-11 are supported for STA in all countries
            assertEquals(supported24GhzFreqs.size(), 11);
        } catch (UnsupportedOperationException ex) {
            //expected if the device does not support this API
        } catch (Exception ex) {
            fail("getAllowedChannels unexpected Exception " + ex);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#getUsableChannels(int, int))} does not crash
     * and returns at least one 2G channel in STA and WFD GO modes (if WFD is supported)
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testGetUsableChannelsStaWfdMode() throws Exception {
        // The below API only works with privileged permissions (obtained via shell identity
        // for test)
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            List<WifiAvailableChannel> usableStaChannels =
                    sWifiManager.getUsableChannels(WIFI_BAND_24_GHZ, OP_MODE_STA);
            //There must be at least one usable STA channel in 2.4GHz band
            assertFalse(usableStaChannels.isEmpty());
            if (sWifiManager.isP2pSupported()) {
                List<WifiAvailableChannel> usableGoChannels =
                        sWifiManager.getUsableChannels(WIFI_BAND_24_GHZ, OP_MODE_WIFI_DIRECT_GO);
                //There must be at least one usable P2P channel in 2.4GHz band
                assertFalse(usableGoChannels.isEmpty());
            }

        } catch (UnsupportedOperationException ex) {
            //expected if the device does not support this API
        } catch (Exception ex) {
            fail("getUsableChannels unexpected Exception " + ex);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#getChannelData(Executor, Consumer<List<Bundle>>)}
     * does not crash.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testGetChannelData() throws Exception {
        List<Bundle> dataList = new ArrayList<>();
        Consumer<List<Bundle>> listener = new Consumer<List<Bundle>>() {
            @Override
            public void accept(List<Bundle> value) {
                synchronized (mLock) {
                    dataList.addAll(value);
                    mLock.notify();
                }
            }
        };
        // Test invalid inputs trigger IllegalArgumentException
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> sWifiManager.getChannelData(null, listener));
        assertThrows("null listener should trigger exception", NullPointerException.class,
                () -> sWifiManager.getChannelData(mExecutor, null));

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // Start scan and wait for scan results
            startScan();
            sWifiManager.getChannelData(mExecutor, listener);
            synchronized (mLock) {
                mLock.wait(TEST_WAIT_DURATION_MS);
            }
            if (sWifiManager.isScanAlwaysAvailable() && isScanCurrentlyAvailable()) {
                assertFalse(dataList.isEmpty());
            }
        } catch (UnsupportedOperationException ex) {
            //expected if the device does not support this API
        } catch (Exception ex) {
            fail("getChannelData unexpected Exception " + ex);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Validate that the Passpoint feature is enabled on the device.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testPasspointCapability() {
        if (PropertyUtil.getVsrApiLevel() < Build.VERSION_CODES.S) {
            return;
        }
        PackageManager packageManager = sContext.getPackageManager();
        assertTrue("Passpoint must be supported",
                packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_PASSPOINT));
    }

    /**
     * Validate add and remove SuggestionUserApprovalStatusListener. And verify the listener's
     * stickiness.
     */
    @Test
    public void testAddRemoveSuggestionUserApprovalStatusListener() throws Exception {
        if (!WifiFeature.isWifiSupported(sContext)
                || !WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            return;
        }
        CountDownLatch countDownLatch = new CountDownLatch(1);
        TestUserApprovalStatusListener listener = new TestUserApprovalStatusListener(
                countDownLatch);
        try {
            sWifiManager.addSuggestionUserApprovalStatusListener(mExecutor, listener);
            assertTrue(countDownLatch.await(TEST_WAIT_DURATION_MS, TimeUnit.MILLISECONDS));
        } finally {
            sWifiManager.removeSuggestionUserApprovalStatusListener(listener);
        }
    }

    private static class TestUserApprovalStatusListener implements
            WifiManager.SuggestionUserApprovalStatusListener {
        private final CountDownLatch mBlocker;

        public TestUserApprovalStatusListener(CountDownLatch countDownLatch) {
            mBlocker = countDownLatch;
        }
        @Override
        public void onUserApprovalStatusChange(int status) {
            mBlocker.countDown();
        }
    }

    /**
     * Tests {@link WifiManager#setStaConcurrencyForMultiInternetMode)} raise security exception
     * without permission.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testIsStaConcurrencyForMultiInternetSupported() throws Exception {
        // ensure no crash.
        sWifiManager.isStaConcurrencyForMultiInternetSupported();
    }

    /**
     * Tests {@link WifiManager#setStaConcurrencyForMultiInternetMode)} raise security exception
     * without permission.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testSetStaConcurrencyForMultiInternetModeWithoutPermission() throws Exception {
        if (!WifiFeature.isWifiSupported(sContext)
                || !sWifiManager.isStaConcurrencyForMultiInternetSupported()) {
            // skip the test if WiFi is not supported or multi internet feature not supported.
            return;
        }
        try {
            sWifiManager.setStaConcurrencyForMultiInternetMode(
                    WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED);
            fail("setWifiPasspointEnabled() expected to fail - privileged call");
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Tests {@link WifiManager#setStaConcurrencyForMultiInternetMode)} does not crash.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testSetStaConcurrencyForMultiInternetMode() throws Exception {
        if (!WifiFeature.isWifiSupported(sContext)
                || !sWifiManager.isStaConcurrencyForMultiInternetSupported()) {
            // skip the test if WiFi is not supported or multi internet feature not supported.
            return;
        }

        // The below API only works with privileged permissions (obtained via shell identity
        // for test)
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // Try to disable multi internet
            sWifiManager.setStaConcurrencyForMultiInternetMode(
                    WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED);
            PollingCheck.check(
                    "Wifi multi internet disable failed!", 2_000,
                    () -> sWifiManager.getStaConcurrencyForMultiInternetMode()
                            == WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED);
            // Try to enable multi internet
            sWifiManager.setStaConcurrencyForMultiInternetMode(
                    WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP);
            PollingCheck.check(
                    "Wifi multi internet turn on failed!", 2_000,
                    () -> sWifiManager.getStaConcurrencyForMultiInternetMode()
                            == WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    static class TestWifiNetworkStateChangeListener implements
            WifiManager.WifiNetworkStateChangedListener {
        final int mCmmRole;
        private List<Integer> mStateList = new ArrayList<>();

        TestWifiNetworkStateChangeListener(int cmmRole) {
            mCmmRole = cmmRole;
        }

        @Override
        public void onWifiNetworkStateChanged(int cmmRole, int state) {
            if (cmmRole != mCmmRole) {
                return;
            }
            if (mStateList.contains(state)) {
                // ignore duplicate state transitions
                return;
            }
            mStateList.add(state);
        }

        public List<Integer> getStateList() {
            return mStateList;
        }

        public void clear() {
            mStateList.clear();
        }
    }

    @Test
    public void testWifiNetworkStateChangeListener() throws Exception {
        TestWifiNetworkStateChangeListener testListener = new TestWifiNetworkStateChangeListener(
                WifiManager.WifiNetworkStateChangedListener.WIFI_ROLE_CLIENT_PRIMARY);
        // Verify permission check
        assertThrows(SecurityException.class,
                () -> sWifiManager.addWifiNetworkStateChangedListener(mExecutor, testListener));

        // Disable wifi
        setWifiEnabled(false);
        waitForDisconnection();

        try {
            // Register listener then enable wifi
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.addWifiNetworkStateChangedListener(mExecutor, testListener));
            setWifiEnabled(true);

            // Trigger a scan & wait for connection to one of the saved networks.
            sWifiManager.startScan();
            waitForConnection();

            PollingCheck.check(
                    "Wifi network state change listener did not receive connected!", 1_000,
                    () -> testListener.getStateList().contains(
                            WifiManager.WifiNetworkStateChangedListener
                                    .WIFI_NETWORK_STATUS_CONNECTED));
            int firstState = testListener.getStateList().get(0);
            int lastState = testListener.getStateList().get(testListener.getStateList().size() - 1);
            assertEquals(WifiManager.WifiNetworkStateChangedListener
                    .WIFI_NETWORK_STATUS_CONNECTING, firstState);
            assertEquals(WifiManager.WifiNetworkStateChangedListener
                    .WIFI_NETWORK_STATUS_CONNECTED, lastState);

            // Disable wifi and verify disconnect is reported.
            testListener.clear();
            setWifiEnabled(false);
            waitForDisconnection();
            PollingCheck.check(
                    "Wifi network state change listener did not receive disconnected!", 1_000,
                    () -> testListener.getStateList().contains(
                            WifiManager.WifiNetworkStateChangedListener
                                    .WIFI_NETWORK_STATUS_DISCONNECTED));
        } finally {
            sWifiManager.removeWifiNetworkStateChangedListener(testListener);
        }
    }

    /**
     * Tests {@link WifiConfiguration#setBssidAllowlist(List)}.
     */
    @Test
    public void testBssidAllowlist() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        List<WifiConfiguration> savedNetworks = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();

            WifiInfo wifiInfo = sWifiManager.getConnectionInfo();
            String connectedBssid = wifiInfo.getBSSID();
            int networkId = wifiInfo.getNetworkId();

            // Set empty BSSID allow list to block all APs
            savedNetworks = sWifiManager.getConfiguredNetworks();
            for (WifiConfiguration network : savedNetworks) {
                network.setBssidAllowlist(Collections.emptyList());
                sWifiManager.updateNetwork(network);
            }

            // Disable and re-enable Wifi to avoid reconnect to the secondary candidate
            sWifiManager.setWifiEnabled(false);
            waitForDisconnection();
            sWifiManager.setWifiEnabled(true);
            // Now trigger scan and ensure that the device does not connect to any networks.
            sWifiManager.startScan();
            ensureNotConnected();

            // Set the previous connected BSSID on that network. Other network set with a fake
            // (not visible) BSSID only
            for (WifiConfiguration network : savedNetworks) {
                if (network.networkId == networkId) {
                    network.setBssidAllowlist(List.of(MacAddress.fromString(connectedBssid)));
                    sWifiManager.updateNetwork(network);
                } else {
                    network.setBssidAllowlist(List.of(MacAddress.fromString(TEST_BSSID)));
                    sWifiManager.updateNetwork(network);
                }
            }

            // Trigger a scan & wait for connection to one of the saved networks.
            sWifiManager.startScan();
            waitForConnection();
            wifiInfo = sWifiManager.getConnectionInfo();
            assertEquals(networkId, wifiInfo.getNetworkId());
        } finally {
            // Reset BSSID allow list to accept all APs
            for (WifiConfiguration network : savedNetworks) {
                assertNotNull(network.getBssidAllowlist());
                network.setBssidAllowlist(null);
                sWifiManager.updateNetwork(network);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#notifyMinimumRequiredWifiSecurityLevelChanged(int)}
     * raise security exception without permission.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testNotifyMinimumRequiredWifiSecurityLevelChangedWithoutPermission()
            throws Exception {
        if (!WifiFeature.isWifiSupported(sContext)) {
            // skip the test if WiFi is not supported.
            return;
        }
        assertThrows(SecurityException.class,
                () -> sWifiManager.notifyMinimumRequiredWifiSecurityLevelChanged(
                        DevicePolicyManager.WIFI_SECURITY_PERSONAL));
    }

    /**
     * Tests {@link WifiManager#notifyMinimumRequiredWifiSecurityLevelChanged(int)}
     * raise security exception without permission.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testNotifyWifiSsidPolicyChangedWithoutPermission() throws Exception {
        if (!WifiFeature.isWifiSupported(sContext)) {
            // skip the test if WiFi is not supported.
            return;
        }
        WifiSsidPolicy policy = new WifiSsidPolicy(
                WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_ALLOWLIST, new ArraySet<>(Arrays.asList(
                WifiSsid.fromBytes("ssid".getBytes(StandardCharsets.UTF_8)))));
        try {
            sWifiManager.notifyWifiSsidPolicyChanged(policy);
            fail("Expected security exception due to lack of permission");
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Verifies that
     * {@link WifiManager#reportCreateInterfaceImpact(int, boolean, Executor, BiConsumer)} raises
     * a security exception without permission.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testIsItPossibleToCreateInterfaceNotAllowed() throws Exception {
        assertThrows(SecurityException.class, () -> sWifiManager.reportCreateInterfaceImpact(
                WifiManager.WIFI_INTERFACE_TYPE_AP, false, mExecutor,
                (canBeCreatedLocal, interfacesWhichWillBeDeletedLocal) -> {
                    // should not get here (security exception!)
                }));
    }

    /**
     * Verifies
     * {@link WifiManager#reportCreateInterfaceImpact(int, boolean, Executor, BiConsumer)} .
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testIsItPossibleToCreateInterface() throws Exception {
        AtomicBoolean called = new AtomicBoolean(false);
        AtomicBoolean canBeCreated = new AtomicBoolean(false);
        AtomicReference<Set<WifiManager.InterfaceCreationImpact>>
                interfacesWhichWillBeDeleted = new AtomicReference<>(null);
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.reportCreateInterfaceImpact(
                        WifiManager.WIFI_INTERFACE_TYPE_AP, false, mExecutor,
                        (canBeCreatedLocal, interfacesWhichWillBeDeletedLocal) -> {
                            synchronized (mLock) {
                                canBeCreated.set(canBeCreatedLocal);
                                called.set(true);
                                interfacesWhichWillBeDeleted.set(interfacesWhichWillBeDeletedLocal);
                                mLock.notify();
                            }
                        }));
        synchronized (mLock) {
            mLock.wait(TEST_WAIT_DURATION_MS);
        }
        assertTrue(called.get());
        if (canBeCreated.get()) {
            for (WifiManager.InterfaceCreationImpact entry : interfacesWhichWillBeDeleted.get()) {
                int interfaceType = entry.getInterfaceType();
                assertTrue(interfaceType == WifiManager.WIFI_INTERFACE_TYPE_STA
                        || interfaceType == WifiManager.WIFI_INTERFACE_TYPE_AP
                        || interfaceType == WifiManager.WIFI_INTERFACE_TYPE_DIRECT
                        || interfaceType == WifiManager.WIFI_INTERFACE_TYPE_AWARE);
                Set<String> packages = entry.getPackages();
                for (String p : packages) {
                    assertNotNull(p);
                }
            }
        }

        // verify the WifiManager.InterfaceCreationImpact APIs
        int interfaceType = WifiManager.WIFI_INTERFACE_TYPE_STA;
        Set<String> packages = Set.of("package1", "packages2");
        WifiManager.InterfaceCreationImpact element = new WifiManager.InterfaceCreationImpact(
                interfaceType, packages);
        assertEquals(interfaceType, element.getInterfaceType());
        assertEquals(packages, element.getPackages());
    }

    /**
     * Tests {@link WifiManager#isEasyConnectDppAkmSupported)} does not crash.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testIsEasyConnectDppAkmSupported() throws Exception {
        sWifiManager.isEasyConnectDppAkmSupported();
    }

    /**
     * Tests {@link WifiManager#getMaxNumberOfChannelsPerNetworkSpecifierRequest)} works
     */
    @Test
    public void testGetMaxNumberOfChannelsPerNetworkSpecifierRequest() {
        assertTrue(sWifiManager.getMaxNumberOfChannelsPerNetworkSpecifierRequest() > 0);
    }

    /**
     * Tests {@link WifiManager#isTlsV13Supported)} does not crash.
     */
    @Test
    public void testIsTlsV13Supported() throws Exception {
        sWifiManager.isTlsV13Supported();
    }

    /**
     * Tests {@link WifiManager#isTlsMinimumVersionSupported)} does not crash.
     */
    @Test
    public void testIsTlsMinimumVersionSupported() throws Exception {
        sWifiManager.isTlsMinimumVersionSupported();
    }

    private void fillQosPolicyParamsList(List<QosPolicyParams> policyParamsList,
            int size, boolean uniqueIds) {
        policyParamsList.clear();
        for (int i = 0; i < size; i++) {
            int policyId = uniqueIds ? i + 2 : 5;
            policyParamsList.add(new QosPolicyParams.Builder(
                    policyId, QosPolicyParams.DIRECTION_DOWNLINK)
                            .setUserPriority(QosPolicyParams.USER_PRIORITY_VIDEO_LOW)
                            .setIpVersion(QosPolicyParams.IP_VERSION_4)
                            .build());
        }
    }

    /**
     * Check whether the application QoS feature is enabled.
     *
     * The feature is enabled if the overlay is true, the experiment feature flag
     * is true, and the supplicant service implements V2 of the AIDL interface.
     */
    private boolean applicationQosFeatureEnabled() {
        boolean overlayEnabled;
        try {
            WifiResourceUtil resourceUtil = new WifiResourceUtil(sContext);
            overlayEnabled = resourceUtil
                    .getWifiBoolean("config_wifiApplicationCentricQosPolicyFeatureEnabled");
        } catch (Exception e) {
            Log.i(TAG, "Unable to retrieve the QoS overlay value");
            return false;
        }

        // Supplicant V2 is supported if the vendor partition indicates API > T.
        boolean halSupport = PropertyUtil.isVndkApiLevelNewerThan(Build.VERSION_CODES.TIRAMISU);
        boolean featureFlagEnabled = DeviceConfig.getBoolean(DEVICE_CONFIG_NAMESPACE,
                "application_qos_policy_api_enabled", true);

        return overlayEnabled && featureFlagEnabled && halSupport;
    }

    /**
     * Tests that {@link WifiManager#addQosPolicies(List, Executor, Consumer)},
     * {@link WifiManager#removeQosPolicies(int[])}, and
     * {@link WifiManager#removeAllQosPolicies()} do not crash.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void testAddAndRemoveQosPolicies() throws Exception {
        final Mutable<Boolean> callbackReceived = new Mutable<Boolean>(false);
        final Mutable<Boolean> policiesRejected = new Mutable<Boolean>(true);
        Consumer<List<Integer>> listener = new Consumer<List<Integer>>() {
            @Override
            public void accept(List value) {
                synchronized (mLock) {
                    callbackReceived.value = true;
                    List<Integer> statusList = value;
                    for (Integer status : statusList) {
                        if (status != WifiManager.QOS_REQUEST_STATUS_FAILURE_UNKNOWN) {
                            policiesRejected.value = false;
                            break;
                        }
                    }
                    Log.i(TAG, "Callback received for QoS add request, size=" + statusList.size()
                            + ", rejected=" + policiesRejected.value);
                    mLock.notify();
                }
            }
        };

        // Test that invalid inputs trigger an Exception.
        final List<QosPolicyParams> policyParamsList = new ArrayList<>();
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> sWifiManager.addQosPolicies(policyParamsList, null, listener));
        assertThrows("null listener should trigger exception", NullPointerException.class,
                () -> sWifiManager.addQosPolicies(policyParamsList, mExecutor, null));
        assertThrows("null policy list should trigger exception", NullPointerException.class,
                () -> sWifiManager.addQosPolicies(null, mExecutor, listener));

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            boolean enabled = applicationQosFeatureEnabled();

            // If the feature is disabled, verify that all policies are rejected.
            if (!enabled) {
                Log.i(TAG, "QoS policy APIs are not enabled");
                fillQosPolicyParamsList(policyParamsList, 4, true);
                sWifiManager.addQosPolicies(policyParamsList, mExecutor, listener);
                synchronized (mLock) {
                    mLock.wait(TEST_WAIT_DURATION_MS);
                }
                assertTrue(callbackReceived.value);
                assertTrue(policiesRejected.value);
                return;
            }

            // Empty params list
            assertThrows("empty list should trigger exception", IllegalArgumentException.class,
                    () -> sWifiManager.addQosPolicies(new ArrayList<>(), mExecutor, listener));

            // More than {@link WifiManager#getMaxNumberOfPoliciesPerQosRequest()}
            // policies in the list
            fillQosPolicyParamsList(policyParamsList,
                    sWifiManager.getMaxNumberOfPoliciesPerQosRequest() + 1, true);
            assertThrows("large list should trigger exception", IllegalArgumentException.class,
                    () -> sWifiManager.addQosPolicies(policyParamsList, mExecutor, listener));

            // Params list contains duplicate policy ids
            fillQosPolicyParamsList(policyParamsList, 4, false);
            assertThrows("duplicate ids should trigger exception", IllegalArgumentException.class,
                    () -> sWifiManager.addQosPolicies(policyParamsList, mExecutor, listener));

            // Valid list
            fillQosPolicyParamsList(policyParamsList, 4, true);
            sWifiManager.addQosPolicies(policyParamsList, mExecutor, listener);

            // sleep to wait for a response from supplicant
            synchronized (mLock) {
                mLock.wait(TEST_WAIT_DURATION_MS);
            }

            int[] policyIds = new int[policyParamsList.size()];
            for (int i = 0; i < policyParamsList.size(); i++) {
                policyIds[i] = policyParamsList.get(i).getPolicyId();
            }
            sWifiManager.removeQosPolicies(policyIds);

            // sleep to wait for a response from supplicant
            synchronized (mLock) {
                mLock.wait(TEST_WAIT_DURATION_MS);
            }
            sWifiManager.removeAllQosPolicies();
        } catch (Exception e) {
            fail("addAndRemoveQosPolicy unexpected Exception " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private boolean qosR3Supported() {
        return SdkLevel.isAtLeastV() && Flags.androidVWifiApi();
    }

    /**
     * Tests the builder and get methods for {@link QosPolicyParams}.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void testQosPolicyParamsBuilder() throws Exception {
        final int policyId = 5;
        final int direction = QosPolicyParams.DIRECTION_DOWNLINK;
        final int ipVersion = QosPolicyParams.IP_VERSION_6;
        final int dscp = 12;
        final int userPriority = QosPolicyParams.USER_PRIORITY_VIDEO_LOW;
        final String ipv6Address = "2001:db8:3333:4444:5555:6666:7777:8888";
        final InetAddress srcAddr = InetAddress.getByName(ipv6Address);
        final InetAddress dstAddr = InetAddress.getByName(ipv6Address);
        final int srcPort = 123;
        final int protocol = QosPolicyParams.PROTOCOL_TCP;
        final int dstPort = 17;
        final int[] dstPortRange = new int[]{15, 22};
        final byte[] flowLabel = new byte[]{17, 18, 19};

        int minServiceIntervalMicros = 2000;
        int maxServiceIntervalMicros = 5000;
        int minDataRateKbps = 500;
        int delayBoundMicros = 200;
        QosCharacteristics qosCharacteristics = new QosCharacteristics.Builder(
                minServiceIntervalMicros, maxServiceIntervalMicros,
                minDataRateKbps, delayBoundMicros).build();

        // Invalid parameter
        assertThrows("Invalid dscp should trigger an exception", IllegalArgumentException.class,
                () -> new QosPolicyParams.Builder(policyId, direction)
                        .setDscp(70)
                        .build());

        // Valid downlink parameters
        QosPolicyParams.Builder builder =
                new QosPolicyParams.Builder(policyId, QosPolicyParams.DIRECTION_DOWNLINK)
                        .setSourceAddress(srcAddr)
                        .setDestinationAddress(dstAddr)
                        .setUserPriority(userPriority)
                        .setIpVersion(ipVersion)
                        .setSourcePort(srcPort)
                        .setProtocol(protocol)
                        .setDestinationPort(dstPort)
                        .setFlowLabel(flowLabel);
        if (qosR3Supported()) {
            // Optional field for downlink policies
            builder.setQosCharacteristics(qosCharacteristics);
        }
        QosPolicyParams downlinkParams = builder.build();

        assertEquals(policyId, downlinkParams.getPolicyId());
        assertEquals(QosPolicyParams.DIRECTION_DOWNLINK, downlinkParams.getDirection());
        assertEquals(srcAddr, downlinkParams.getSourceAddress());
        assertEquals(dstAddr, downlinkParams.getDestinationAddress());
        assertEquals(userPriority, downlinkParams.getUserPriority());
        assertEquals(ipVersion, downlinkParams.getIpVersion());
        assertEquals(srcPort, downlinkParams.getSourcePort());
        assertEquals(protocol, downlinkParams.getProtocol());
        assertEquals(dstPort, downlinkParams.getDestinationPort());
        assertArrayEquals(flowLabel, downlinkParams.getFlowLabel());
        if (qosR3Supported()) {
            assertEquals(qosCharacteristics, downlinkParams.getQosCharacteristics());
        }

        // Valid uplink parameters
        if (qosR3Supported()) {
            QosPolicyParams uplinkParams =
                new QosPolicyParams.Builder(policyId, QosPolicyParams.DIRECTION_UPLINK)
                            .setSourceAddress(srcAddr)
                            .setDestinationAddress(dstAddr)
                            .setDscp(dscp)
                            .setSourcePort(srcPort)
                            .setProtocol(protocol)
                            .setDestinationPortRange(dstPortRange[0], dstPortRange[1])
                            .setQosCharacteristics(qosCharacteristics)
                            .build();
            assertEquals(policyId, uplinkParams.getPolicyId());
            assertEquals(QosPolicyParams.DIRECTION_UPLINK, uplinkParams.getDirection());
            assertEquals(srcAddr, uplinkParams.getSourceAddress());
            assertEquals(dstAddr, uplinkParams.getDestinationAddress());
            assertEquals(dscp, uplinkParams.getDscp());
            assertEquals(srcPort, uplinkParams.getSourcePort());
            assertEquals(protocol, uplinkParams.getProtocol());
            assertArrayEquals(dstPortRange, uplinkParams.getDestinationPortRange());
            assertEquals(qosCharacteristics, uplinkParams.getQosCharacteristics());
        }
    }

    /**
     * Verifies when the link layer stats polling interval is overridden by
     * {@link WifiManager#setLinkLayerStatsPollingInterval(int)},
     * the new interval is set correctly by checking
     * {@link WifiManager#getLinkLayerStatsPollingInterval(Executor, Consumer)}
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testSetAndGetLinkLayerStatsPollingInterval() throws Exception {
        AtomicInteger currentInterval = new AtomicInteger(-1);
        Consumer<Integer> listener = new Consumer<Integer>() {
            @Override
            public void accept(Integer value) {
                synchronized (mLock) {
                    currentInterval.set(value);
                    mLock.notify();
                }
            }
        };

        // SecurityException
        assertThrows(SecurityException.class,
                () -> sWifiManager.setLinkLayerStatsPollingInterval(
                        TEST_LINK_LAYER_STATS_POLLING_INTERVAL_MS));
        assertThrows(SecurityException.class,
                () -> sWifiManager.getLinkLayerStatsPollingInterval(mExecutor, listener));
        // null executor
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> sWifiManager.getLinkLayerStatsPollingInterval(null, listener));
        // null listener
        assertThrows("null listener should trigger exception", NullPointerException.class,
                () -> sWifiManager.getLinkLayerStatsPollingInterval(mExecutor, null));

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        try {
            uiAutomation.adoptShellPermissionIdentity();
            assertThrows(IllegalArgumentException.class,
                    () -> sWifiManager.setLinkLayerStatsPollingInterval(
                            -TEST_LINK_LAYER_STATS_POLLING_INTERVAL_MS));
            sWifiManager.setLinkLayerStatsPollingInterval(
                    TEST_LINK_LAYER_STATS_POLLING_INTERVAL_MS);
            sWifiManager.getLinkLayerStatsPollingInterval(mExecutor, listener);
            synchronized (mLock) {
                mLock.wait(TEST_WAIT_DURATION_MS);
            }
            assertEquals(TEST_LINK_LAYER_STATS_POLLING_INTERVAL_MS, currentInterval.get());
            // set the interval to automatic handling after the test
            sWifiManager.setLinkLayerStatsPollingInterval(0);
        } catch (UnsupportedOperationException ex) {
            // Expected if the device does not support this API
        } catch (Exception e) {
            fail("setLinkLayerStatsPollingInterval / getLinkLayerStatsPollingInterval "
                    + "unexpected Exception " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#getMaxMloAssociationLinkCount(Executor, Consumer)} and
     * {@link WifiManager#getMaxMloStrLinkCount(Executor, Consumer)}.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testMloCapabilities() throws Exception {
        AtomicInteger linkCount = new AtomicInteger();
        Consumer<Integer> getListener = new Consumer<Integer>() {
            @Override
            public void accept(Integer value) {
                synchronized (mLock) {
                    linkCount.set(value);
                    mLock.notify();
                }
            }
        };

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // Test that invalid inputs trigger an exception.
            assertThrows("null executor should trigger exception", NullPointerException.class,
                    () -> sWifiManager.getMaxMloAssociationLinkCount(null, getListener));
            assertThrows("null listener should trigger exception", NullPointerException.class,
                    () -> sWifiManager.getMaxMloAssociationLinkCount(mExecutor, null));
            assertThrows("null executor should trigger exception", NullPointerException.class,
                    () -> sWifiManager.getMaxMloStrLinkCount(null, getListener));
            assertThrows("null listener should trigger exception", NullPointerException.class,
                    () -> sWifiManager.getMaxMloStrLinkCount(mExecutor, null));

            linkCount.set(Integer.MIN_VALUE);
            sWifiManager.getMaxMloStrLinkCount(mExecutor, getListener);
            PollingCheck.check("getMaxMloStrLinkCount failed", TEST_WAIT_DURATION_MS,
                    () -> (linkCount.get() >= -1));

            linkCount.set(Integer.MIN_VALUE);
            sWifiManager.getMaxMloAssociationLinkCount(mExecutor, getListener);
            PollingCheck.check("getMaxMloAssociationLinkCount failed", TEST_WAIT_DURATION_MS,
                    () -> (linkCount.get() >= -1));
        } catch (Exception e) {
            fail("Unexpected exception " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }
    /**
     * Tests {@link WifiManager#setLinkMode} and {@link WifiManager#getLinkMode} works
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void testMloMode() {
        // Get listener.
        AtomicInteger getMode = new AtomicInteger();
        Consumer<Integer> getListener = new Consumer<Integer>() {
            @Override
            public void accept(Integer value) {
                synchronized (mLock) {
                    getMode.set(value);
                    mLock.notify();
                }
            }
        };
        // Set listener.
        AtomicBoolean setStatus = new AtomicBoolean();
        Consumer<Boolean> setListener = new Consumer<Boolean>() {
            @Override
            public void accept(Boolean value) {
                synchronized (mLock) {
                    setStatus.set(value);
                    mLock.notify();
                }
            }
        };
        // Test that invalid inputs trigger an exception.
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> sWifiManager.setMloMode(WifiManager.MLO_MODE_DEFAULT, null, setListener));
        assertThrows("null listener should trigger exception", NullPointerException.class,
                () -> sWifiManager.setMloMode(WifiManager.MLO_MODE_DEFAULT, mExecutor, null));
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> sWifiManager.getMloMode(null, getListener));
        assertThrows("null listener should trigger exception", NullPointerException.class,
                () -> sWifiManager.getMloMode(mExecutor, null));

        // Test that invalid inputs trigger an IllegalArgumentException.
        assertThrows("Invalid mode", IllegalArgumentException.class,
                () -> sWifiManager.setMloMode(-1, mExecutor, setListener));
        assertThrows("Invalid mode", IllegalArgumentException.class,
                () -> sWifiManager.setMloMode(1000, mExecutor, setListener));

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        // Test set if supported.
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // Check getMloMode() returns values in range.
            sWifiManager.getMloMode(mExecutor, getListener);
            assertThat(getMode.get()).isIn(Range.closed(WifiManager.MLO_MODE_DEFAULT,
                    WifiManager.MLO_MODE_LOW_POWER));
            // Try to set default MLO mode and get.
            sWifiManager.setMloMode(WifiManager.MLO_MODE_DEFAULT, mExecutor, setListener);
            if (setStatus.get()) {
                sWifiManager.getMloMode(mExecutor, getListener);
                assertTrue(getMode.get() == WifiManager.MLO_MODE_DEFAULT);
            }
            // Try to set low latency MLO mode and get.
            sWifiManager.setMloMode(WifiManager.MLO_MODE_LOW_LATENCY, mExecutor, setListener);
            if (setStatus.get()) {
                sWifiManager.getMloMode(mExecutor, getListener);
                assertTrue(getMode.get() == WifiManager.MLO_MODE_LOW_LATENCY);
            }
            // Try to set high throughput MLO mode and get.
            sWifiManager.setMloMode(WifiManager.MLO_MODE_HIGH_THROUGHPUT, mExecutor, setListener);
            if (setStatus.get()) {
                sWifiManager.getMloMode(mExecutor, getListener);
                assertTrue(getMode.get() == WifiManager.MLO_MODE_DEFAULT);
            }
            // Try to set low power MLO mode and get.
            sWifiManager.setMloMode(WifiManager.MLO_MODE_LOW_POWER, mExecutor, setListener);
            if (setStatus.get()) {
                sWifiManager.getMloMode(mExecutor, getListener);
                assertTrue(getMode.get() == WifiManager.MLO_MODE_DEFAULT);
            }
        } catch (Exception e) {
            fail("Unexpected exception " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testGetSupportedSimultaneousBandCombinations() {
        AtomicInteger nEntries = new AtomicInteger();
        Consumer<List<int[]>> getListener = new Consumer<List<int[]>>() {
            @Override
            public void accept(List<int[]> bands) {
                synchronized (mLock) {
                    nEntries.set(bands.size());
                    mLock.notify();
                }
            }
        };
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();

            assertThrows("null executor should trigger exception", NullPointerException.class,
                    () -> sWifiManager.getSupportedSimultaneousBandCombinations(null, getListener));
            assertThrows("null listener should trigger exception", NullPointerException.class,
                    () -> sWifiManager.getSupportedSimultaneousBandCombinations(mExecutor, null));

            nEntries.set(-1);
            sWifiManager.getSupportedSimultaneousBandCombinations(mExecutor, getListener);
            PollingCheck.check("getSupportedSimultaneousBandCombinations failed",
                    TEST_WAIT_DURATION_MS,
                    () -> (nEntries.get() > -1));
        } catch (Exception e) {
            fail("Unexpected exception " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#isThirdPartyAppEnablingWifiConfirmationDialogEnabled()}
     * and {@link WifiManager#setThirdPartyAppEnablingWifiConfirmationDialogEnabled(boolean)}
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void testGetAndSetThirdPartyAppEnablingWifiConfirmationDialogEnabled() {
        // Expect a SecurityException without the required permissions.
        assertThrows(SecurityException.class,
                () -> sWifiManager.isThirdPartyAppEnablingWifiConfirmationDialogEnabled());
        assertThrows(SecurityException.class,
                () -> sWifiManager.setThirdPartyAppEnablingWifiConfirmationDialogEnabled(true));

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();

            // Store a new value.
            boolean defaultVal =
                    sWifiManager.isThirdPartyAppEnablingWifiConfirmationDialogEnabled();
            boolean newVal = !defaultVal;
            sWifiManager.setThirdPartyAppEnablingWifiConfirmationDialogEnabled(newVal);
            assertEquals(newVal,
                    sWifiManager.isThirdPartyAppEnablingWifiConfirmationDialogEnabled());

            // Restore the original value.
            sWifiManager.setThirdPartyAppEnablingWifiConfirmationDialogEnabled(defaultVal);
            assertEquals(defaultVal,
                    sWifiManager.isThirdPartyAppEnablingWifiConfirmationDialogEnabled());
        } catch (Exception e) {
            fail("Unexpected exception " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    static class TestWifiLowLatencyLockListener implements WifiManager.WifiLowLatencyLockListener {
        private boolean mIsActivated = false;
        private int[] mOwnerUids = null;
        private int[] mActiveUids = null;

        public void clear() {
            mIsActivated = false;
            mOwnerUids = null;
            mActiveUids = null;
        }

        @Override
        public void onActivatedStateChanged(boolean activated) {
            mIsActivated = activated;
        }

        @Override
        public void onOwnershipChanged(@NonNull int[] ownerUids) {
            mOwnerUids = ownerUids;
        }

        @Override
        public void onActiveUsersChanged(@NonNull int[] activeUids) {
            mActiveUids = activeUids;
        }

        public boolean isActivated() {
            return mIsActivated;
        }

        public boolean isLockOwned(int myUid) {
            if (mOwnerUids == null) return false;
            for (int uid : mOwnerUids) {
                if (uid == myUid) return true;
            }
            return false;
        }

        public boolean isActiveLockUser(int myUid) {
            if (mActiveUids == null) return false;
            for (int uid : mActiveUids) {
                if (uid == myUid) return true;
            }
            return false;
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testWifiLowLatencyLockListener() throws Exception {
        TestWifiLowLatencyLockListener testListener = new TestWifiLowLatencyLockListener();
        // Verify permission check
        assertThrows(SecurityException.class,
                () -> sWifiManager.addWifiLowLatencyLockListener(mExecutor, testListener));

        // Disable wifi
        setWifiEnabled(false);
        waitForDisconnection();

        WifiLock wifiLowLatencyLock = sWifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                TAG);

        try {
            // Register listener then enable wifi
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.addWifiLowLatencyLockListener(mExecutor, testListener));
            setWifiEnabled(true);

            // Trigger a scan & wait for connection to one of the saved networks.
            sWifiManager.startScan();
            waitForConnection();

            // TODO: b/281356259 - Move this to a foreground activity.
            ShellIdentityUtils.invokeWithShellPermissions(() -> wifiLowLatencyLock.acquire());

            if (sWifiManager.isLowLatencyModeSupported()) {
                PollingCheck.check("Lock is not activated!", 1_000,
                        () -> testListener.isActivated());
            }

            PollingCheck.check("Lock is not owned!", 1_000,
                    () -> testListener.isLockOwned(myUid()));

            if (sWifiManager.isLowLatencyModeSupported()) {
                PollingCheck.check("Not an active Lock user!", 1_000,
                        () -> testListener.isActiveLockUser(myUid()));
            }

            ShellIdentityUtils.invokeWithShellPermissions(() -> wifiLowLatencyLock.release());

            // Note: The lock cannot be tested for deactivation after release because other
            // applications can still keep it active.

            PollingCheck.check("Still owns the Lock after release!", 1_000,
                    () -> !testListener.isLockOwned(myUid()));
        } catch (Exception e) {
            fail("Unexpected exception " + e);
        } finally {
            testListener.clear();
            sWifiManager.removeWifiLowLatencyLockListener(testListener);
        }
    }

    /**
     * Tests the builder and get methods for {@link OuiKeyedData}.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
                 codeName = "VanillaIceCream")
    @Test
    public void testOuiKeyedDataBuilder() throws Exception {
        int oui = 0x00112233;
        PersistableBundle data = new PersistableBundle();
        String key = "intField";
        data.putInt(key, 12345);

        assertThrows("Zero OUI should trigger an exception", IllegalArgumentException.class,
                () -> new OuiKeyedData.Builder(0, data).build());
        assertThrows(">24-bit OUI should trigger an exception", IllegalArgumentException.class,
                () -> new OuiKeyedData.Builder(0x11223344, data).build());
        assertThrows("Null data should trigger an exception", IllegalArgumentException.class,
                () -> new OuiKeyedData.Builder(oui, null).build());

        OuiKeyedData ouiKeyedData = new OuiKeyedData.Builder(oui, data).build();
        assertEquals(oui, ouiKeyedData.getOui());
        assertTrue(data.keySet().equals(ouiKeyedData.getData().keySet()));
        assertEquals(data.getInt(key), ouiKeyedData.getData().getInt(key));
    }

    /**
     * Tests {@link WifiManager#isWepSupported()} does not crash.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testIsWepSupported() throws Exception {
        sWifiManager.isWepSupported();
    }

    /**
     * Tests {@link WifiManager#isWpaPersonalSupported()} does not crash.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testIsWpaPersonalSupported() throws Exception {
        sWifiManager.isWpaPersonalSupported();
    }

    /**
     * Tests {@link WifiManager#setWepAllowed()} and
     * {@link WifiManager#queryWepAllowed()}.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testSetAndQueryWepAllowed() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        Mutable<Boolean> isQuerySucceeded = new Mutable<Boolean>(false);
        boolean currentWepAllowed = false;
        boolean isRestoreRequired = false;
        long now, deadline;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            Mutable<Boolean> isWepAllowed = new Mutable<Boolean>(false);
            sWifiManager.queryWepAllowed(mExecutor,
                    new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean value) {
                        synchronized (mLock) {
                            isWepAllowed.value = value;
                            isQuerySucceeded.value = true;
                            mLock.notify();
                        }
                    }
                });
            synchronized (mLock) {
                now = System.currentTimeMillis();
                deadline = now + TEST_WAIT_DURATION_MS;
                while (!isQuerySucceeded.value && now < deadline) {
                    mLock.wait(deadline - now);
                    now = System.currentTimeMillis();
                }
            }
            assertTrue(isQuerySucceeded.value);
            // Reset for next query
            isQuerySucceeded.value = false;
            currentWepAllowed = isWepAllowed.value;
            isRestoreRequired = true;
            sWifiManager.setWepAllowed(!currentWepAllowed);
            sWifiManager.queryWepAllowed(mExecutor,
                    new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean value) {
                        synchronized (mLock) {
                            isWepAllowed.value = value;
                            isQuerySucceeded.value = true;
                            mLock.notify();
                        }
                    }
                });
            synchronized (mLock) {
                now = System.currentTimeMillis();
                deadline = now + TEST_WAIT_DURATION_MS;
                while (!isQuerySucceeded.value && now < deadline) {
                    mLock.wait(deadline - now);
                    now = System.currentTimeMillis();
                }
            }
            assertEquals(isWepAllowed.value, !currentWepAllowed);
        } finally {
            if (isRestoreRequired) {
                sWifiManager.setWepAllowed(currentWepAllowed);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#enableMscs(MscsParams)}, {@link WifiManager#disableMscs()},
     * and all get/set methods in {@link MscsParams}.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    public void testEnableAndDisableMscs() {
        int frameClassifierFields = MscsParams.FRAME_CLASSIFIER_IP_VERSION;
        int userPriorityBitmap = 0; // don't match any user priorities using MSCS
        int userPriorityLimit = 7;
        int streamTimeoutUs = 30000000; // 30 seconds (value is longer than TEST_WAIT_DURATION_MS)
        MscsParams params = new MscsParams.Builder()
                .setFrameClassifierFields(frameClassifierFields)
                .setUserPriorityBitmap(userPriorityBitmap)
                .setUserPriorityLimit(userPriorityLimit)
                .setStreamTimeoutUs(streamTimeoutUs)
                .build();
        assertEquals(frameClassifierFields, params.getFrameClassifierFields());
        assertEquals(userPriorityBitmap, params.getUserPriorityBitmap());
        assertEquals(userPriorityLimit, params.getUserPriorityLimit());
        assertEquals(streamTimeoutUs, params.getStreamTimeoutUs());

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiManager.enableMscs(params);
            synchronized (mLock) {
                // Wait for the request to get sent to the AP.
                mLock.wait(TEST_WAIT_DURATION_MS);
            }
            sWifiManager.disableMscs();
        } catch (Exception e) {
            fail("testEnableAndDisableMscs encountered an exception: " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests the result from {@link WifiUriParser#parseUri(String)} can be added.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testZxingNetworkFromUriParserCanBeAdded() throws Exception {
        String testUriZx = "WIFI:S:testAbC;T:nopass";
        UriParserResults result = WifiUriParser.parseUri(testUriZx);
        assertNotNull(result);
        assertEquals(result.getUriScheme(), UriParserResults.URI_SCHEME_ZXING_WIFI_NETWORK_CONFIG);
        WifiConfiguration config = result.getWifiConfiguration();
        assertNotNull(config);
        // These below API's only work with privileged permissions (obtained via shell identity
        // for test)
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation();
        int networkId = INVALID_NETWORK_ID;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // Verify that the network is added
            networkId = sWifiManager.addNetwork(config);
            assertNotEquals(INVALID_NETWORK_ID, networkId);
        } finally {
            if (networkId != INVALID_NETWORK_ID) {
                // Clean up the previously added network
                sWifiManager.removeNetwork(networkId);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /*
     * Tests the builder and get methods for {@link QosCharacteristics}.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testQosCharacteristicsBuilder() {
        int minServiceIntervalMicros = 2000;
        int maxServiceIntervalMicros = 5000;
        int minDataRateKbps = 500;
        int delayBoundMicros = 200;
        int maxMsduSizeOctets = 4;
        int serviceStartTimeMicros = 250;
        int serviceStartTimeLinkId = 0x5;
        int meanDataRateKbps = 1500;
        int burstSizeOctets = 2;
        int msduLifetimeMillis = 400;
        int deliveryRatio = QosCharacteristics.DELIVERY_RATIO_99;
        int countExponent = 5;

        QosCharacteristics qosCharacteristics = new QosCharacteristics.Builder(
                minServiceIntervalMicros, maxServiceIntervalMicros,
                minDataRateKbps, delayBoundMicros)
                .setBurstSizeOctets(burstSizeOctets)
                .setMaxMsduSizeOctets(maxMsduSizeOctets)
                .setServiceStartTimeInfo(serviceStartTimeMicros, serviceStartTimeLinkId)
                .setMeanDataRateKbps(meanDataRateKbps)
                .setMsduLifetimeMillis(msduLifetimeMillis)
                .setMsduDeliveryInfo(deliveryRatio, countExponent)
                .build();

        assertEquals(minServiceIntervalMicros, qosCharacteristics.getMinServiceIntervalMicros());
        assertEquals(maxServiceIntervalMicros, qosCharacteristics.getMaxServiceIntervalMicros());
        assertEquals(minDataRateKbps, qosCharacteristics.getMinDataRateKbps());
        assertEquals(delayBoundMicros, qosCharacteristics.getDelayBoundMicros());
        assertEquals(maxMsduSizeOctets, qosCharacteristics.getMaxMsduSizeOctets());
        assertEquals(serviceStartTimeMicros, qosCharacteristics.getServiceStartTimeMicros());
        assertEquals(serviceStartTimeLinkId, qosCharacteristics.getServiceStartTimeLinkId());
        assertEquals(meanDataRateKbps, qosCharacteristics.getMeanDataRateKbps());
        assertEquals(burstSizeOctets, qosCharacteristics.getBurstSizeOctets());
        assertEquals(msduLifetimeMillis, qosCharacteristics.getMsduLifetimeMillis());
        assertEquals(deliveryRatio, qosCharacteristics.getDeliveryRatio());
        assertEquals(countExponent, qosCharacteristics.getCountExponent());
    }

    private int querySendDhcpHostnameRestrictionSynchronous() throws Exception {
        Mutable<Integer> queriedRestriction = new Mutable<>(0);
        Mutable<Boolean> isQuerySucceeded = new Mutable<>(false);
        sWifiManager.querySendDhcpHostnameRestriction(mExecutor, (value) -> {
            synchronized (mLock) {
                queriedRestriction.value = value;
                isQuerySucceeded.value = true;
                mLock.notify();
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
        assertTrue(isQuerySucceeded.value);
        return queriedRestriction.value;
    }

    /**
     * Tests {@link WifiManager#setSendDhcpHostnameRestriction(int)} and
     * {@link WifiManager#querySendDhcpHostnameRestriction(Executor, IntConsumer)}.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    public void testSetAndQuerySendDhcpHostnameRestriction() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        int previousRestriction = 0;
        boolean isRestoreRequired = false;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            previousRestriction = querySendDhcpHostnameRestrictionSynchronous();

            sWifiManager.setSendDhcpHostnameRestriction(
                    WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_OPEN);
            isRestoreRequired = true;
            assertEquals(querySendDhcpHostnameRestrictionSynchronous(),
                    WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_OPEN);

            sWifiManager.setSendDhcpHostnameRestriction(
                    WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_OPEN
                            | WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_SECURE);
            assertEquals(querySendDhcpHostnameRestrictionSynchronous(),
                    WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_OPEN
                            | WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_SECURE);
        } finally {
            if (isRestoreRequired) {
                sWifiManager.setSendDhcpHostnameRestriction(previousRestriction);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiConfiguration#setWifi7Enabled(boolean)}. Validate default behavior, disable
     * Wi-Fi 7 and Enable Wi-Fi 7.
     */
    @ApiTest(apis = {"android.net.wifi.WifiConfiguration#setWifi7Enabled",
            "android.net.wifi.WifiConfiguration#isWifi7Enabled"})
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testEnableWifi7() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        TestActionListener actionListener = new TestActionListener(mLock);
        setWifiEnabled(true);
        WifiConfiguration wifi7Network = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // Make sure device supports Wi-Fi 7
            assumeTrue(sWifiManager.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE));

            List<WifiConfiguration> savedNetworks = sWifiManager.getConfiguredNetworks();
            wifi7Network = TestHelper.findFirstAvailableSavedNetwork(sWifiManager,
                    savedNetworks, TestHelper.AP_CAPABILITY_BIT_WIFI7);
            // TODO: b/322011012
            assumeTrue("Unable to locate Wi-Fi 7 networks in range.\n", wifi7Network != null);

            // Default behavior: check new connection is Wi-Fi 7
            sWifiManager.disconnect();
            waitForDisconnection();
            assertTrue(wifi7Network.isWifi7Enabled());
            sWifiManager.connect(wifi7Network.networkId, actionListener);
            waitForConnection();
            assertTrue(sWifiManager.getConnectionInfo().getWifiStandard()
                    == ScanResult.WIFI_STANDARD_11BE);

            // Disable Wi-Fi 7 while connected: check new connection is not Wi-Fi 7
            wifi7Network.setWifi7Enabled(false);
            assertFalse(wifi7Network.isWifi7Enabled());
            sWifiManager.updateNetwork(wifi7Network);
            waitForConnection();
            assertTrue(sWifiManager.getConnectionInfo().getWifiStandard()
                    != ScanResult.WIFI_STANDARD_11BE);

            // Enable Wi-Fi 7: check new connection is Wi-Fi 7
            sWifiManager.disconnect();
            waitForDisconnection();
            wifi7Network.setWifi7Enabled(true);
            assertTrue(wifi7Network.isWifi7Enabled());
            sWifiManager.connect(wifi7Network.networkId, actionListener);
            waitForConnection();
            assertTrue(sWifiManager.getConnectionInfo().getWifiStandard()
                    == ScanResult.WIFI_STANDARD_11BE);

        } finally {
            // Restore
            if (wifi7Network != null) {
                wifi7Network.setWifi7Enabled(true);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    class TestTwtSessionCallback implements TwtSessionCallback {
        final AtomicReference<TwtSession> mTwtSession = new AtomicReference<>();
        final AtomicInteger mTwtTeardownReasonCode = new AtomicInteger(-1);
        final AtomicInteger mTwtErrorCode = new AtomicInteger(-1);

        @Override
        public void onFailure(int errorCode) {
            synchronized (mLock) {
                mTwtErrorCode.set(errorCode);
                mLock.notify();
            }
        }

        @Override
        public void onTeardown(int reasonCode) {
            synchronized (mLock) {
                mTwtTeardownReasonCode.set(reasonCode);
                mLock.notify();
            }
        }

        @Override
        public void onCreate(TwtSession twtSession) {
            synchronized (mLock) {
                mTwtSession.set(twtSession);
            }
        }
    }

    /**
     * Validate setting up a TWT session if device supports and get stats and finally close. If the
     * connection is multi-link, pick the first link and set up the TWT session.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName =
            "VanillaIceCream")
    @Test
    @ApiTest(apis = {"android.net.wifi.WifiManager#getTwtCapabilities",
            "android.net.wifi.WifiManager#twtSessionSetup",
            "android.net.wifi.twt.TwtSession#getStats",
            "android.net.wifi.twt.TwtSession#close",
            "android.net.wifi.twt.TwtSession#getWakeDurationMicros",
            "android.net.wifi.twt.TwtSession#getWakeIntervalMicros",
            "android.net.wifi.twt.TwtSession#getMloLinkId",
            "android.net.wifi.twt.TwtRequest#Builder",
            "android.net.wifi.twt.TwtRequest.Builder#setLinkId",
            "android.net.wifi.twt.TwtRequest#getMinWakeDurationMicros",
            "android.net.wifi.twt.TwtRequest#getMaxWakeDurationMicros",
            "android.net.wifi.twt.TwtRequest#getMinWakeIntervalMicros",
            "android.net.wifi.twt.TwtRequest#getMaxWakeIntervalMicros",
            "android.net.wifi.twt.TwtRequest#getLinkId"})
    public void testTwt() throws Exception {
        AtomicReference<Bundle> twtCapabilities = new AtomicReference<>();
        AtomicReference<Bundle> twtStats = new AtomicReference<>();
        long now, deadline;
        Consumer<Bundle> twtCapabilityCallback = capabilities -> {
            synchronized (mLock) {
                twtCapabilities.set(capabilities);
                mLock.notify();
            }
        };
        Consumer<Bundle> twtStatsCallback = stats -> {
            synchronized (mLock) {
                twtStats.set(stats);
                mLock.notify();
            }
        };
        TestTwtSessionCallback testTwtSessionCallback = new TestTwtSessionCallback();
        TestActionListener actionListener = new TestActionListener(mLock);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiManager.getTwtCapabilities(mExecutor, twtCapabilityCallback);
            synchronized (mLock) {
                now = System.currentTimeMillis();
                deadline = now + TEST_WAIT_DURATION_MS;
                while (twtCapabilities.get() == null && now < deadline) {
                    mLock.wait(deadline - now);
                    now = System.currentTimeMillis();
                }
            }
            assertNotNull("getTwtCapabilities() timed out !", twtCapabilities.get());
            // Assume device is a TWT requester
            assumeTrue(twtCapabilities.get().getBoolean(
                    WifiManager.TWT_CAPABILITIES_KEY_BOOLEAN_TWT_REQUESTER));
            assertTrue(twtCapabilities.get().getInt(
                    WifiManager.TWT_CAPABILITIES_KEY_INT_MIN_WAKE_DURATION_MICROS) >= 0);
            assertTrue(twtCapabilities.get().getInt(
                    WifiManager.TWT_CAPABILITIES_KEY_INT_MAX_WAKE_DURATION_MICROS) >= 0);
            assertTrue(twtCapabilities.get().getLong(
                    WifiManager.TWT_CAPABILITIES_KEY_LONG_MIN_WAKE_INTERVAL_MICROS) >= 0);
            assertTrue(twtCapabilities.get().getLong(
                    WifiManager.TWT_CAPABILITIES_KEY_LONG_MAX_WAKE_INTERVAL_MICROS) >= 0);

            // Connect to an available TWT responder network
            List<WifiConfiguration> savedNetworks = sWifiManager.getConfiguredNetworks();
            WifiConfiguration twtNetwork = TestHelper.findFirstAvailableSavedNetwork(sWifiManager,
                    savedNetworks, TestHelper.AP_CAPABILITY_BIT_TWT_RESPONDER);
            // TODO: Make it an assert once the TWT setup is available for CTS test
            assumeTrue("Unable to locate TWT capable networks in range.\n", twtNetwork != null);
            sWifiManager.disconnect();
            waitForDisconnection();
            sWifiManager.connect(twtNetwork.networkId, actionListener);
            waitForConnection();

            // Get the first link id, if the connection is multi-link
            List<MloLink> mloLinks = sWifiManager.getConnectionInfo().getAssociatedMloLinks();
            int linkId = MloLink.INVALID_MLO_LINK_ID;
            if (!mloLinks.isEmpty()) {
                linkId = mloLinks.getFirst().getLinkId();
            }

            // Build and validate twtRequest
            int minWakeDuration = twtCapabilities.get().getInt(
                    WifiManager.TWT_CAPABILITIES_KEY_INT_MIN_WAKE_DURATION_MICROS);
            int maxWakeDuration = twtCapabilities.get().getInt(
                    WifiManager.TWT_CAPABILITIES_KEY_INT_MAX_WAKE_DURATION_MICROS);
            long minWakeInterval = twtCapabilities.get().getLong(
                    WifiManager.TWT_CAPABILITIES_KEY_LONG_MIN_WAKE_INTERVAL_MICROS);
            long maxWakeInterval = twtCapabilities.get().getLong(
                    WifiManager.TWT_CAPABILITIES_KEY_LONG_MAX_WAKE_INTERVAL_MICROS);
            TwtRequest.Builder builder = new TwtRequest.Builder(minWakeDuration, maxWakeDuration,
                    minWakeInterval, maxWakeInterval);
            if (linkId != MloLink.INVALID_MLO_LINK_ID) {
                builder.setLinkId(linkId);
            }
            TwtRequest twtRequest = builder.build();
            assertEquals(twtRequest.getMinWakeDurationMicros(), minWakeDuration);
            assertEquals(twtRequest.getMaxWakeDurationMicros(), maxWakeDuration);
            assertEquals(twtRequest.getMinWakeIntervalMicros(), minWakeInterval);
            assertEquals(twtRequest.getMaxWakeIntervalMicros(), maxWakeInterval);
            assertEquals(twtRequest.getLinkId(), linkId);

            // Verify TWT session setup
            sWifiManager.setupTwtSession(twtRequest, mExecutor, testTwtSessionCallback);
            synchronized (mLock) {
                now = System.currentTimeMillis();
                deadline = now + TEST_WAIT_DURATION_MS;
                while (testTwtSessionCallback.mTwtSession.get() == null && now < deadline) {
                    mLock.wait(deadline - now);
                    now = System.currentTimeMillis();
                }
            }
            assertNotNull("setupTwtSession() timed out !",
                    testTwtSessionCallback.mTwtSession.get());
            assertTrue(testTwtSessionCallback.mTwtSession.get().getWakeDurationMicros() > 0);
            assertTrue(testTwtSessionCallback.mTwtSession.get().getWakeIntervalMicros() > 0);
            assertTrue(testTwtSessionCallback.mTwtSession.get().getMloLinkId() == linkId);

            // Verify TWT session get stats
            testTwtSessionCallback.mTwtSession.get().getStats(mExecutor, twtStatsCallback);
            synchronized (mLock) {
                now = System.currentTimeMillis();
                deadline = now + TEST_WAIT_DURATION_MS;
                while (twtStats.get() == null && now < deadline) {
                    mLock.wait(deadline - now);
                    now = System.currentTimeMillis();
                }
            }
            assertNotNull("TwtSession#getStats() timed out !", twtStats.get());
            assertTrue(twtStats.get().getInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_TX_PACKET_COUNT)
                    >= 0);
            assertTrue(twtStats.get().getInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_RX_PACKET_COUNT)
                    >= 0);
            assertTrue(twtStats.get().getInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_TX_PACKET_SIZE)
                    >= 0);
            assertTrue(twtStats.get().getInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_RX_PACKET_SIZE)
                    >= 0);
            assertTrue(
                    twtStats.get().getInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_EOSP_DURATION_MICROS)
                            >= 0);
            assertTrue(twtStats.get().getInt(TwtSession.TWT_STATS_KEY_INT_EOSP_COUNT) >= 0);

            // Verify TWT session close
            testTwtSessionCallback.mTwtSession.get().close();
            synchronized (mLock) {
                now = System.currentTimeMillis();
                deadline = now + TEST_WAIT_DURATION_MS;
                while (testTwtSessionCallback.mTwtTeardownReasonCode.get() == -1
                        && now < deadline) {
                    mLock.wait(deadline - now);
                    now = System.currentTimeMillis();
                }
            }
            assertNotEquals("TwtSession#teardown() timed out !", -1,
                    testTwtSessionCallback.mTwtTeardownReasonCode.get());
            assertTrue(testTwtSessionCallback.mTwtTeardownReasonCode.get()
                    == TwtSessionCallback.TWT_REASON_CODE_LOCALLY_REQUESTED);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName =
            "VanillaIceCream")
    @Test
    public void testTwtSessionAutoCloseable() throws Exception {
        AtomicReference<Bundle> twtCapabilities = new AtomicReference<>();
        long now, deadline;
        Consumer<Bundle> twtCapabilityCallback = capabilities -> {
            synchronized (mLock) {
                twtCapabilities.set(capabilities);
                mLock.notify();
            }
        };
        TestTwtSessionCallback testTwtSessionCallback = new TestTwtSessionCallback();
        TestActionListener actionListener = new TestActionListener(mLock);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiManager.getTwtCapabilities(mExecutor, twtCapabilityCallback);
            synchronized (mLock) {
                now = System.currentTimeMillis();
                deadline = now + TEST_WAIT_DURATION_MS;
                while (twtCapabilities.get() == null && now < deadline) {
                    mLock.wait(deadline - now);
                    now = System.currentTimeMillis();
                }
            }
            assertNotNull("getTwtCapabilities() timed out !", twtCapabilities.get());
            // Assume device is a TWT requester
            assumeTrue(twtCapabilities.get().getBoolean(
                    WifiManager.TWT_CAPABILITIES_KEY_BOOLEAN_TWT_REQUESTER));

            // Connect to an available TWT responder network
            List<WifiConfiguration> savedNetworks = sWifiManager.getConfiguredNetworks();
            WifiConfiguration twtNetwork = TestHelper.findFirstAvailableSavedNetwork(sWifiManager,
                    savedNetworks, TestHelper.AP_CAPABILITY_BIT_TWT_RESPONDER);
            assumeTrue("Unable to locate TWT capable networks in range.\n", twtNetwork != null);
            sWifiManager.disconnect();
            waitForDisconnection();
            sWifiManager.connect(twtNetwork.networkId, actionListener);
            waitForConnection();

            // Verify TWT session setup
            sWifiManager.setupTwtSession(new TwtRequest.Builder(twtCapabilities.get().getInt(
                            WifiManager.TWT_CAPABILITIES_KEY_INT_MIN_WAKE_DURATION_MICROS),
                            twtCapabilities.get().getInt(
                                    WifiManager.TWT_CAPABILITIES_KEY_INT_MAX_WAKE_DURATION_MICROS),
                            twtCapabilities.get().getLong(
                                    WifiManager.TWT_CAPABILITIES_KEY_LONG_MIN_WAKE_INTERVAL_MICROS),
                            twtCapabilities.get().getLong(
                                    WifiManager.TWT_CAPABILITIES_KEY_LONG_MAX_WAKE_INTERVAL_MICROS)).build(),
                    mExecutor, testTwtSessionCallback);
            synchronized (mLock) {
                now = System.currentTimeMillis();
                deadline = now + TEST_WAIT_DURATION_MS;
                while (testTwtSessionCallback.mTwtSession.get() == null && now < deadline) {
                    mLock.wait(deadline - now);
                    now = System.currentTimeMillis();
                }
            }
            assertNotNull("setupTwtSession() timed out !",
                    testTwtSessionCallback.mTwtSession.get());

        } finally {
            // Test Twt Session, if setup, closes automatically
            if (testTwtSessionCallback.mTwtSession.get() != null) {
                synchronized (mLock) {
                    now = System.currentTimeMillis();
                    deadline = now + TEST_WAIT_DURATION_MS;
                    while (testTwtSessionCallback.mTwtTeardownReasonCode.get() == -1
                            && now < deadline) {
                        mLock.wait(deadline - now);
                        now = System.currentTimeMillis();
                    }
                }
                assertNotEquals("TwtSession auto close timed out !", -1,
                        testTwtSessionCallback.mTwtTeardownReasonCode.get());
                assertEquals(testTwtSessionCallback.mTwtTeardownReasonCode.get(),
                        TwtSessionCallback.TWT_REASON_CODE_LOCALLY_REQUESTED);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }
    /**
     * Tests {@link WifiManager#isD2dSupportedWhenInfraStaDisabled()},
     * {@link WifiManager#setD2dAllowedWhenInfraStaDisabled()} and
     * {@link WifiManager#queryD2dAllowedWhenInfraStaDisabled()}.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testD2dAllowedWhenInfraStaDisabled() throws Exception {
        if (!sWifiManager.isD2dSupportedWhenInfraStaDisabled()) {
            // skip the test if feature is not supported.
            return;
        }
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        Mutable<Boolean> isQuerySucceeded = new Mutable<Boolean>(false);
        boolean currentD2dAllowed = false;
        boolean isRestoreRequired = false;
        long now, deadline;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            Mutable<Boolean> isD2dAllowed = new Mutable<Boolean>(false);
            sWifiManager.queryD2dAllowedWhenInfraStaDisabled(mExecutor,
                    new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean value) {
                        synchronized (mLock) {
                            isD2dAllowed.value = value;
                            isQuerySucceeded.value = true;
                            mLock.notify();
                        }
                    }
                });
            synchronized (mLock) {
                now = System.currentTimeMillis();
                deadline = now + TEST_WAIT_DURATION_MS;
                while (!isQuerySucceeded.value && now < deadline) {
                    mLock.wait(deadline - now);
                    now = System.currentTimeMillis();
                }
            }
            assertTrue("d2d allowed query fail", isQuerySucceeded.value);
            // Reset for next query
            isQuerySucceeded.value = false;
            currentD2dAllowed = isD2dAllowed.value;
            isRestoreRequired = true;
            sWifiManager.setD2dAllowedWhenInfraStaDisabled(!currentD2dAllowed);
            sWifiManager.queryD2dAllowedWhenInfraStaDisabled(mExecutor,
                    new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean value) {
                        synchronized (mLock) {
                            isD2dAllowed.value = value;
                            isQuerySucceeded.value = true;
                            mLock.notify();
                        }
                    }
                });
            synchronized (mLock) {
                now = System.currentTimeMillis();
                deadline = now + TEST_WAIT_DURATION_MS;
                while (!isQuerySucceeded.value && now < deadline) {
                    mLock.wait(deadline - now);
                    now = System.currentTimeMillis();
                }
            }
            assertEquals("set/query d2d allowed should match",
                    isD2dAllowed.value, !currentD2dAllowed);
        } finally {
            if (isRestoreRequired) {
                sWifiManager.setD2dAllowedWhenInfraStaDisabled(currentD2dAllowed);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests {@link WifiManager#retrieveWifiBackupData()},
     * {@link WifiManager#restoreWifiBackupData()}.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @Test
    public void testWifiBackupRestore() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        Mutable<Boolean> isQuerySucceeded = new Mutable<Boolean>(false);
        Mutable<byte[]> backupWifiData = new Mutable<byte[]>();
        long now, deadline;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiManager.retrieveWifiBackupData(mExecutor,
                    new Consumer<byte[]>() {
                    @Override
                    public void accept(byte[] value) {
                        synchronized (mLock) {
                            isQuerySucceeded.value = true;
                            backupWifiData.value = value;
                            mLock.notify();
                        }
                    }
                });
            // Test no crash when calling backup/restore api
            synchronized (mLock) {
                now = System.currentTimeMillis();
                deadline = now + TEST_WAIT_DURATION_MS;
                while (!isQuerySucceeded.value && now < deadline) {
                    mLock.wait(deadline - now);
                    now = System.currentTimeMillis();
                }
            }
            assertTrue("retrieve Wi-Fi backup data fail", isQuerySucceeded.value);
            sWifiManager.restoreWifiBackupData(backupWifiData.value);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
