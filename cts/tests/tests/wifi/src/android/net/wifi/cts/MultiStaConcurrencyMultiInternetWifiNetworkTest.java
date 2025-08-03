/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.os.Process.myUid;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TransportInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiDevice;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Tests multiple concurrent connection flow on devices that support multi STA concurrency
 * (indicated via {@link WifiManager#isStaConcurrencyForMultiInternetSupported()}.
 *
 * Tests the entire connection flow using issuing connectivity manager requests with
 * network specifier containing bands.
 *
 * Assumes that all the saved networks is either open/WPA1/WPA2/WPA3 authenticated network.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@LargeTest
@RunWith(AndroidJUnit4.class)
public class MultiStaConcurrencyMultiInternetWifiNetworkTest extends WifiJUnit4TestBase {
    private static final String TAG = "MultiStaConcurrencyMultiInternetWifiNetworkTest";
    private static boolean sWasVerboseLoggingEnabled;
    private static boolean sWasScanThrottleEnabled;
    private static boolean sWasWifiEnabled;
    private static boolean sShouldRunTest = false;

    private Context mContext;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;
    private UiDevice mUiDevice;
    private ScheduledExecutorService mExecutorService;
    private TestHelper mTestHelper;
    // Map from band to list of WifiConfiguration, for matching networks.
    private Map<Integer, List<WifiConfiguration>> mMatchingNetworksMap;
    // Map from network SSID to set of bands.
    private Map<String, Set<Integer>> mMatchingNetworksBands;

    private static final int DURATION_MILLIS = 20_000;

    private final ConnectivityManager.NetworkCallback mNetworkCallback =
            new ConnectivityManager.NetworkCallback(
                    ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                @Override
                public void onLinkPropertiesChanged(@NonNull Network network,
                        @NonNull LinkProperties lp) {
                    final boolean isPrimary = isPrimaryWifiNetwork(
                            mConnectivityManager.getNetworkCapabilities(network));
                    Log.d(TAG, "onLinkPropertiesChanged: " + network + " primary " + isPrimary);
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network,
                        @NonNull NetworkCapabilities networkCapabilities) {
                    final boolean isPrimary = isPrimaryWifiNetwork(
                            mConnectivityManager.getNetworkCapabilities(network));
                    Log.d(TAG, "onCapabilitiesChanged: " + network + " primary " + isPrimary
                            + " cap " + networkCapabilities);
                }

                @Override
                public void onLost(@NonNull Network network) {
                    final boolean isPrimary = isPrimaryWifiNetwork(
                            mConnectivityManager.getNetworkCapabilities(network));
                }
            };

    @BeforeClass
    public static void setUpClass() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        // skip the test if WiFi is not supported.
        // Don't use assumeTrue in @BeforeClass
        if (!WifiFeature.isWifiSupported(context)) return;
        if (!SdkLevel.isAtLeastT()) return;

        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        assertThat(wifiManager).isNotNull();
        if (!wifiManager.isStaConcurrencyForMultiInternetSupported()) {
            return;
        }
        sShouldRunTest = true;
        Log.i(TAG, "sShouldRunTest " + sShouldRunTest);

        // Turn on verbose logging for tests
        sWasVerboseLoggingEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.isVerboseLoggingEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.setVerboseLoggingEnabled(true));
        // Disable scan throttling for tests.
        sWasScanThrottleEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.isScanThrottleEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.setScanThrottleEnabled(false));

        // Enable Wifi
        sWasWifiEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.isWifiEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(() -> wifiManager.setWifiEnabled(true));
        // Make sure wifi is enabled
        PollingCheck.check("Wifi not enabled", DURATION_MILLIS, () -> wifiManager.isWifiEnabled());
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (!sShouldRunTest) return;

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        assertThat(wifiManager).isNotNull();

        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.setScanThrottleEnabled(sWasScanThrottleEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.setVerboseLoggingEnabled(sWasVerboseLoggingEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.setWifiEnabled(sWasWifiEnabled));
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(sShouldRunTest);
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mWifiManager = mContext.getSystemService(WifiManager.class);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mExecutorService = Executors.newSingleThreadScheduledExecutor();
        mTestHelper = new TestHelper(mContext, mUiDevice);

        // Turn screen on
        mTestHelper.turnScreenOn();

        // Clear any existing app state before each test.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.removeAppState(myUid(), mContext.getPackageName()));

        // This test assumes a CTS test environment with at least 2 connectable bssid's, in
        // different bands. We need 2 AP's for the test:
        // 1. Dual-band (DBS) AP [the bands being 2.4 + 5]
        // 2. Single-band AP with a different SSID.
        // We need 2 saved networks for the 2 AP's and the device in range to proceed.
        // The test will check if there are 2 BSSIDs in range and in different bands from
        // the saved network.
        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.getPrivilegedConfiguredNetworks());
        mMatchingNetworksMap =
                TestHelper.findMatchingSavedNetworksWithBssidByBand(mWifiManager, savedNetworks,
                        0, 2);
        assertWithMessage("Need at least 2 saved network bssids in different bands").that(
                mMatchingNetworksMap.size()).isAtLeast(2);

        mMatchingNetworksBands = new ArrayMap<>();
        for (Map.Entry<Integer, List<WifiConfiguration>> entry : mMatchingNetworksMap.entrySet()) {
            final int band = entry.getKey();
            for (WifiConfiguration network : entry.getValue()) {
                if (mMatchingNetworksBands.containsKey(network.SSID)) {
                    mMatchingNetworksBands.get(network.SSID).add(band);
                } else {
                    mMatchingNetworksBands.put(network.SSID, new HashSet<>(Arrays.asList(band)));
                }
            }
        }
        // Disconnect networks already connected. Make sure the test starts with no network
        // connections.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    mWifiManager.disconnect();
                });

        // Wait for Wifi to be disconnected.
        PollingCheck.check(
                "Wifi not disconnected",
                DURATION_MILLIS,
                () -> mTestHelper.getNumWifiConnections() == 0);
    }

    @After
    public void tearDown() throws Exception {
        if (!sShouldRunTest) return;
        // Re-enable networks.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    for (WifiConfiguration savedNetwork : mWifiManager.getConfiguredNetworks()) {
                        mWifiManager.enableNetwork(savedNetwork.networkId, false);
                    }
                    setMultiInternetMode(WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED);
                });

        mExecutorService.shutdownNow();
        // Clear any existing app state after each test.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.removeAppState(myUid(), mContext.getPackageName()));
        mTestHelper.turnScreenOff();
    }

    private boolean isPrimaryWifiNetwork(@Nullable NetworkCapabilities networkCapabilities) {
        if (networkCapabilities == null) {
            return false;
        }
        final TransportInfo transportInfo = networkCapabilities.getTransportInfo();
        if (!(transportInfo instanceof WifiInfo)) {
            return false;
        }
        return ((WifiInfo) transportInfo).isPrimary();
    }

    private void setMultiInternetMode(int multiInternetMode) {
        mWifiManager.setStaConcurrencyForMultiInternetMode(multiInternetMode);
        try {
            PollingCheck.check("Wifi not enabled", DURATION_MILLIS,
                    () -> mWifiManager.isWifiEnabled());
        } catch (Exception e) {
            fail("Cant get wifi state");
        }
        int mode = mWifiManager.getStaConcurrencyForMultiInternetMode();
        assertEquals(multiInternetMode, mode);
    }

    /**
     * Tests the concurrent connection flow.
     * 1. Connect to a network using internet connectivity API.
     * 2. Connect to a network using enabling multi internet API.
     * 3. Verify that both connections are active.
     */
    @Test
    public void testConnectToSecondaryNetworkWhenConnectedToInternetNetworkMultiAp()
            throws Exception {
        assertWithMessage("Need at least 2 saved network ssids in different bands").that(
                mMatchingNetworksBands.size()).isAtLeast(2);
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    setMultiInternetMode(WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP);
                });
        mTestHelper.testMultiInternetConnectionFlowWithShellIdentity(mExecutorService, true);
    }

    /**
     * Tests the concurrent connection flow.
     * 1. Connect to a network using internet connectivity API.
     * 2. Connect to a network using enabling multi internet API.
     * 3. Verify that both connections are active.
     */
    @Test
    public void testConnectToSecondaryNetworkWhenConnectedToInternetNetworkDBS() throws Exception {
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    setMultiInternetMode(WifiManager.WIFI_MULTI_INTERNET_MODE_DBS_AP);
                });

        mTestHelper.testMultiInternetConnectionFlowWithShellIdentity(mExecutorService, true);
    }

    /**
     * Tests the concurrent connection flow fails without enabling the MultiInternetState.
     * 1. Connect to a network using internet connectivity API.
     * 2. Connect to a network using enabling multi internet API.
     * 3. Verify that both connections are active.
     */
    @Test
    public void testConnectToSecondaryNetworkWhenConnectedToInternetNetworkFail() throws Exception {
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    setMultiInternetMode(WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED);
                });

        mTestHelper.testMultiInternetConnectionFlowWithShellIdentity(mExecutorService, false);
    }
}
