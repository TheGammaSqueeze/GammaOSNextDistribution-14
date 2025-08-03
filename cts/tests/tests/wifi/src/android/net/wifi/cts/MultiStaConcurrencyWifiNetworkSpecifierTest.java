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

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiDevice;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Tests multiple concurrent connection flow on devices that support multi STA concurrency
 * (indicated via {@link WifiManager#isStaConcurrencyForLocalOnlyConnectionsSupported()}.
 *
 * Tests the entire connection flow using {@link WifiNetworkSpecifier} embedded in a
 * {@link NetworkRequest} & passed into {@link ConnectivityManager#requestNetwork(NetworkRequest,
 * ConnectivityManager.NetworkCallback)} along with a concurrent internet connection using
 * {@link WifiManager#connect(int, WifiManager.ActionListener)}.
 *
 * Assumes that all the saved networks is either open/WPA1/WPA2/WPA3 authenticated network.
 *
 * TODO(b/177591382): Refactor some of the utilities to a separate file that are copied over from
 * WifiManagerTest & WifiNetworkSpecifierTest.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@LargeTest
@RunWith(AndroidJUnit4.class)
public class MultiStaConcurrencyWifiNetworkSpecifierTest extends WifiJUnit4TestBase {
    private static final String TAG = "MultiStaConcurrencyWifiNetworkSpecifierTest";
    private static boolean sWasVerboseLoggingEnabled;
    private static boolean sWasScanThrottleEnabled;
    private static boolean sWasWifiEnabled;

    private static boolean sShouldRunTest = false;

    private Context mContext;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;
    private UiDevice mUiDevice;
    private WifiConfiguration mTestNetworkForPeerToPeer;
    private WifiConfiguration mTestNetworkForInternetConnection;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private ConnectivityManager.NetworkCallback mNrNetworkCallback;
    private TestHelper mTestHelper;

    private static final int DURATION = 10_000;
    private static final int DURATION_UI_INTERACTION = 25_000;
    private static final int DURATION_NETWORK_CONNECTION = 60_000;
    private static final int DURATION_SCREEN_TOGGLE = 2000;
    private static final int SCAN_RETRY_CNT_TO_FIND_MATCHING_BSSID = 3;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        // skip the test if WiFi is not supported. Don't use assumeTrue in @BeforeClass
        if (!WifiFeature.isWifiSupported(context)) return;

        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        assertThat(wifiManager).isNotNull();
        if (!wifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported()) {
            return;
        }
        sShouldRunTest = true;
        // turn on verbose logging for tests
        sWasVerboseLoggingEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.isVerboseLoggingEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.setVerboseLoggingEnabled(true));
        // Disable scan throttling for tests.
        sWasScanThrottleEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.isScanThrottleEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.setScanThrottleEnabled(false));

        // enable Wifi
        sWasWifiEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> wifiManager.isWifiEnabled());
        if (!wifiManager.isWifiEnabled()) {
            ShellIdentityUtils.invokeWithShellPermissions(() -> wifiManager.setWifiEnabled(true));
        }
        PollingCheck.check("Wifi not enabled", DURATION, () -> wifiManager.isWifiEnabled());
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (!sShouldRunTest) {
            return;
        }
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        if (!WifiFeature.isWifiSupported(context)) return;

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
        mTestHelper = new TestHelper(mContext, mUiDevice);

        assertWithMessage("Please enable location for this test!")
                .that(mContext.getSystemService(LocationManager.class).isLocationEnabled())
                .isTrue();

        // turn screen on
        mTestHelper.turnScreenOn();

        // Clear any existing app state before each test.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.removeAppState(myUid(), mContext.getPackageName()));

        // We need 2 AP's for the test. If there are 2 networks saved on the device and in range,
        // use those. Otherwise, check if there are 2 BSSID's in range for the only saved network.
        // This assumes a CTS test environment with at least 2 connectable bssid's (Is that ok?).
        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.getPrivilegedConfiguredNetworks());
        List<WifiConfiguration> matchingNetworksWithBssid =
                TestHelper.findMatchingSavedNetworksWithBssid(mWifiManager, savedNetworks, 2);
        assertWithMessage("Need at least 2 saved network bssids in range")
                .that(matchingNetworksWithBssid.size()).isAtLeast(2);
        // Pick any 2 bssid for test.
        mTestNetworkForPeerToPeer = matchingNetworksWithBssid.get(0);
        // Try to find a bssid for another saved network in range. If none exists, fallback
        // to using 2 bssid's for the same network.
        mTestNetworkForInternetConnection = matchingNetworksWithBssid.stream()
                .filter(w -> !w.SSID.equals(mTestNetworkForPeerToPeer.SSID))
                .findAny()
                .orElse(matchingNetworksWithBssid.get(1));

        // Disconnect & disable auto-join on the saved network to prevent auto-connect from
        // interfering with the test.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    for (WifiConfiguration savedNetwork : savedNetworks) {
                        mWifiManager.disableNetwork(savedNetwork.networkId);
                    }
                    mWifiManager.disconnect();
                });

        // Wait for Wifi to be disconnected.
        PollingCheck.check(
                "Wifi not disconnected",
                20_000,
                () -> mWifiManager.getConnectionInfo().getNetworkId() == -1);
    }

    @After
    public void tearDown() throws Exception {
        if (!sShouldRunTest) {
            return;
        }

        // Re-enable networks.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    for (WifiConfiguration savedNetwork :
                            mWifiManager.getConfiguredNetworks()) {
                        mWifiManager.enableNetwork(savedNetwork.networkId, false);
                    }
                });

        // Release the requests after the test.
        if (mNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
        }
        if (mNrNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mNrNetworkCallback);
        }

        // Clear any existing app state after each test.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.removeAppState(myUid(), mContext.getPackageName()));
        mTestHelper.turnScreenOff();
    }

    private void testSuccessfulConnectionWithSpecifier(
            WifiConfiguration network, WifiNetworkSpecifier specifier) throws Exception {
        mNrNetworkCallback = mTestHelper.testConnectionFlowWithSpecifier(
                network, specifier, false);
    }

    private void testUserRejectionWithSpecifier(
            WifiConfiguration network, WifiNetworkSpecifier specifier) throws Exception {
        mNrNetworkCallback = mTestHelper.testConnectionFlowWithSpecifier(
                network, specifier, true);
    }

    /**
     * Tests the concurrent connection flow.
     * 1. Connect to a network using internet connectivity API.
     * 2. Connect to a network using peer to peer API.
     * 3. Verify that both connections are active.
     */
    @Test
    public void testConnectToPeerPeerNetworkWhenConnectedToInternetNetwork() throws Exception {
        // First trigger internet connectivity.
        mNetworkCallback = mTestHelper.testConnectionFlowWithConnect(
                mTestNetworkForInternetConnection);

        // Now trigger peer to peer connectivity.
        WifiNetworkSpecifier specifier =
                TestHelper.createSpecifierBuilderWithCredentialFromSavedNetworkWithBssid(
                        mTestNetworkForPeerToPeer)
                .build();
        testSuccessfulConnectionWithSpecifier(mTestNetworkForPeerToPeer, specifier);

        // Ensure that there are 2 wifi connections available for apps.
        assertThat(mTestHelper.getNumWifiConnections()).isEqualTo(2);
    }

    /**
     * Tests the concurrent connection flow.
     * 1. Connect to a network using peer to peer API.
     * 2. Connect to a network using internet connectivity API.
     * 3. Verify that both connections are active only the network for peer-to-peer and network
     *    for internet have different SSIDs. If they have the same SSID, verify there's exactly one
     *    connection.
     */
    @Test
    public void testConnectToInternetNetworkWhenConnectedToPeerPeerNetwork() throws Exception {
        // First trigger peer to peer connectivity.
        WifiNetworkSpecifier specifier =
                TestHelper.createSpecifierBuilderWithCredentialFromSavedNetworkWithBssid(
                        mTestNetworkForPeerToPeer)
                        .build();
        testSuccessfulConnectionWithSpecifier(mTestNetworkForPeerToPeer, specifier);

        // Now trigger internet connectivity.
        mNetworkCallback = mTestHelper.testConnectionFlowWithConnect(
                mTestNetworkForInternetConnection);

        // Ensure that there are 2 wifi connections available for apps.
        assertThat(mTestHelper.getNumWifiConnections()).isEqualTo(
                mTestNetworkForPeerToPeer.SSID.equals(mTestNetworkForInternetConnection.SSID)
                        ? 1 : 2);
    }

    /**
     * Tests the concurrent connection flow.
     * 1. Connect to a network using internet connectivity API.
     * 2. Trigger connect to a network using peer to peer API which is rejected by user.
     * 3. Verify that only one connection is active.
     */
    @Test
    public void testPeerToPeerConnectionRejectWhenConnectedToInternetNetwork() throws Exception {
        // First trigger internet connectivity.
        mNetworkCallback = mTestHelper.testConnectionFlowWithConnect(
                mTestNetworkForInternetConnection);

        // Now trigger peer to peer connectivity.
        WifiNetworkSpecifier specifier =
                TestHelper.createSpecifierBuilderWithCredentialFromSavedNetworkWithBssid(
                        mTestNetworkForPeerToPeer)
                        .build();
        testUserRejectionWithSpecifier(mTestNetworkForPeerToPeer, specifier);

        // Ensure that there is only 1 wifi connection available for apps.
        assertThat(mTestHelper.getNumWifiConnections()).isEqualTo(1);
    }
}
