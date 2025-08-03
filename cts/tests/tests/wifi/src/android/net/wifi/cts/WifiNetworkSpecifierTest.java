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

import static android.os.Process.myUid;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.PatternMatcher;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiDevice;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Tests the entire connection flow using {@link WifiNetworkSpecifier} embedded in a
 * {@link NetworkRequest} & passed into {@link ConnectivityManager#requestNetwork(NetworkRequest,
 * ConnectivityManager.NetworkCallback)}.
 *
 * Assumes that all the saved networks is either open/WPA1/WPA2/WPA3 authenticated network.
 */
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@LargeTest
@RunWith(AndroidJUnit4.class)
public class WifiNetworkSpecifierTest extends WifiJUnit4TestBase {
    private static final String TAG = "WifiNetworkSpecifierTest";

    private static final String CA_SUITE_B_ECDSA_CERT_STRING =
            "-----BEGIN CERTIFICATE-----\n"
                    + "MIICTzCCAdSgAwIBAgIUdnLttwNPnQzFufplGOr9bTrGCqMwCgYIKoZIzj0EAwMw\n"
                    + "XjELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNBMQwwCgYDVQQHDANNVFYxEDAOBgNV\n"
                    + "BAoMB0FuZHJvaWQxDjAMBgNVBAsMBVdpLUZpMRIwEAYDVQQDDAl1bml0ZXN0Q0Ew\n"
                    + "HhcNMjAwNzIxMDIyNDA1WhcNMzAwNTMwMDIyNDA1WjBeMQswCQYDVQQGEwJVUzEL\n"
                    + "MAkGA1UECAwCQ0ExDDAKBgNVBAcMA01UVjEQMA4GA1UECgwHQW5kcm9pZDEOMAwG\n"
                    + "A1UECwwFV2ktRmkxEjAQBgNVBAMMCXVuaXRlc3RDQTB2MBAGByqGSM49AgEGBSuB\n"
                    + "BAAiA2IABFmntXwk9icqhDQFUP1xy04WyEpaGW4q6Q+8pujlSl/X3iotPZ++GZfp\n"
                    + "Mfv3YDHDBl6sELPQ2BEjyPXmpsKjOUdiUe69e88oGEdeqT2xXiQ6uzpTfJD4170i\n"
                    + "O/TwLrQGKKNTMFEwHQYDVR0OBBYEFCjptsX3g4g5W0L4oEP6N3gfyiZXMB8GA1Ud\n"
                    + "IwQYMBaAFCjptsX3g4g5W0L4oEP6N3gfyiZXMA8GA1UdEwEB/wQFMAMBAf8wCgYI\n"
                    + "KoZIzj0EAwMDaQAwZgIxAK61brUYRbLmQKiaEboZgrHtnPAcGo7Yzx3MwHecx3Dm\n"
                    + "5soIeLVYc8bPYN1pbhXW1gIxALdEe2sh03nBHyQH4adYoZungoCwt8mp/7sJFxou\n"
                    + "9UnRegyBgGzf74ROWdpZHzh+Pg==\n"
                    + "-----END CERTIFICATE-----\n";
    public static final X509Certificate CA_SUITE_B_ECDSA_CERT =
            loadCertificate(CA_SUITE_B_ECDSA_CERT_STRING);

    private static final String CLIENT_SUITE_B_ECDSA_CERT_STRING =
            "-----BEGIN CERTIFICATE-----\n"
                    + "MIIB9zCCAX4CFDpfSZh3AH07BEfGWuMDa7Ynz6y+MAoGCCqGSM49BAMDMF4xCzAJ\n"
                    + "BgNVBAYTAlVTMQswCQYDVQQIDAJDQTEMMAoGA1UEBwwDTVRWMRAwDgYDVQQKDAdB\n"
                    + "bmRyb2lkMQ4wDAYDVQQLDAVXaS1GaTESMBAGA1UEAwwJdW5pdGVzdENBMB4XDTIw\n"
                    + "MDcyMTAyMjk1MFoXDTMwMDUzMDAyMjk1MFowYjELMAkGA1UEBhMCVVMxCzAJBgNV\n"
                    + "BAgMAkNBMQwwCgYDVQQHDANNVFYxEDAOBgNVBAoMB0FuZHJvaWQxDjAMBgNVBAsM\n"
                    + "BVdpLUZpMRYwFAYDVQQDDA11bml0ZXN0Q2xpZW50MHYwEAYHKoZIzj0CAQYFK4EE\n"
                    + "ACIDYgAEhxhVJ7dcSqrto0X+dgRxtd8BWG8cWmPjBji3MIxDLfpcMDoIB84ae1Ew\n"
                    + "gJn4YUYHrWsUDiVNihv8j7a/Ol1qcIY2ybH7tbezefLmagqA4vXEUXZXoUyL4ZNC\n"
                    + "DWcdw6LrMAoGCCqGSM49BAMDA2cAMGQCMH4aP73HrriRUJRguiuRic+X4Cqj/7YQ\n"
                    + "ueJmP87KF92/thhoQ9OrRo8uJITPmNDswwIwP2Q1AZCSL4BI9dYrqu07Ar+pSkXE\n"
                    + "R7oOqGdZR+d/MvXcFSrbIaLKEoHXmQamIHLe\n"
                    + "-----END CERTIFICATE-----\n";
    public static final X509Certificate CLIENT_SUITE_B_ECDSA_CERT =
            loadCertificate(CLIENT_SUITE_B_ECDSA_CERT_STRING);

    private static final byte[] CLIENT_SUITE_B_ECC_KEY_DATA = new byte[]{
            (byte) 0x30, (byte) 0x81, (byte) 0xb6, (byte) 0x02, (byte) 0x01, (byte) 0x00,
            (byte) 0x30, (byte) 0x10, (byte) 0x06, (byte) 0x07, (byte) 0x2a, (byte) 0x86,
            (byte) 0x48, (byte) 0xce, (byte) 0x3d, (byte) 0x02, (byte) 0x01, (byte) 0x06,
            (byte) 0x05, (byte) 0x2b, (byte) 0x81, (byte) 0x04, (byte) 0x00, (byte) 0x22,
            (byte) 0x04, (byte) 0x81, (byte) 0x9e, (byte) 0x30, (byte) 0x81, (byte) 0x9b,
            (byte) 0x02, (byte) 0x01, (byte) 0x01, (byte) 0x04, (byte) 0x30, (byte) 0xea,
            (byte) 0x6c, (byte) 0x4b, (byte) 0x6d, (byte) 0x43, (byte) 0xf9, (byte) 0x6c,
            (byte) 0x91, (byte) 0xdc, (byte) 0x2d, (byte) 0x6e, (byte) 0x87, (byte) 0x4f,
            (byte) 0x0a, (byte) 0x0b, (byte) 0x97, (byte) 0x25, (byte) 0x1c, (byte) 0x79,
            (byte) 0xa2, (byte) 0x07, (byte) 0xdc, (byte) 0x94, (byte) 0xc2, (byte) 0xee,
            (byte) 0x64, (byte) 0x51, (byte) 0x6d, (byte) 0x4e, (byte) 0x35, (byte) 0x1c,
            (byte) 0x22, (byte) 0x2f, (byte) 0xc0, (byte) 0xea, (byte) 0x09, (byte) 0x47,
            (byte) 0x3e, (byte) 0xb9, (byte) 0xb6, (byte) 0xb8, (byte) 0x83, (byte) 0x9e,
            (byte) 0xed, (byte) 0x59, (byte) 0xe5, (byte) 0xe7, (byte) 0x0f, (byte) 0xa1,
            (byte) 0x64, (byte) 0x03, (byte) 0x62, (byte) 0x00, (byte) 0x04, (byte) 0x87,
            (byte) 0x18, (byte) 0x55, (byte) 0x27, (byte) 0xb7, (byte) 0x5c, (byte) 0x4a,
            (byte) 0xaa, (byte) 0xed, (byte) 0xa3, (byte) 0x45, (byte) 0xfe, (byte) 0x76,
            (byte) 0x04, (byte) 0x71, (byte) 0xb5, (byte) 0xdf, (byte) 0x01, (byte) 0x58,
            (byte) 0x6f, (byte) 0x1c, (byte) 0x5a, (byte) 0x63, (byte) 0xe3, (byte) 0x06,
            (byte) 0x38, (byte) 0xb7, (byte) 0x30, (byte) 0x8c, (byte) 0x43, (byte) 0x2d,
            (byte) 0xfa, (byte) 0x5c, (byte) 0x30, (byte) 0x3a, (byte) 0x08, (byte) 0x07,
            (byte) 0xce, (byte) 0x1a, (byte) 0x7b, (byte) 0x51, (byte) 0x30, (byte) 0x80,
            (byte) 0x99, (byte) 0xf8, (byte) 0x61, (byte) 0x46, (byte) 0x07, (byte) 0xad,
            (byte) 0x6b, (byte) 0x14, (byte) 0x0e, (byte) 0x25, (byte) 0x4d, (byte) 0x8a,
            (byte) 0x1b, (byte) 0xfc, (byte) 0x8f, (byte) 0xb6, (byte) 0xbf, (byte) 0x3a,
            (byte) 0x5d, (byte) 0x6a, (byte) 0x70, (byte) 0x86, (byte) 0x36, (byte) 0xc9,
            (byte) 0xb1, (byte) 0xfb, (byte) 0xb5, (byte) 0xb7, (byte) 0xb3, (byte) 0x79,
            (byte) 0xf2, (byte) 0xe6, (byte) 0x6a, (byte) 0x0a, (byte) 0x80, (byte) 0xe2,
            (byte) 0xf5, (byte) 0xc4, (byte) 0x51, (byte) 0x76, (byte) 0x57, (byte) 0xa1,
            (byte) 0x4c, (byte) 0x8b, (byte) 0xe1, (byte) 0x93, (byte) 0x42, (byte) 0x0d,
            (byte) 0x67, (byte) 0x1d, (byte) 0xc3, (byte) 0xa2, (byte) 0xeb
    };
    public static final PrivateKey CLIENT_SUITE_B_ECC_KEY =
            loadPrivateKey("EC", CLIENT_SUITE_B_ECC_KEY_DATA);

    private static X509Certificate loadCertificate(String blob) {
        try {
            final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            InputStream stream = new ByteArrayInputStream(blob.getBytes(StandardCharsets.UTF_8));

            return (X509Certificate) certFactory.generateCertificate(stream);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static PrivateKey loadPrivateKey(String algorithm, byte[] fakeKey) {
        try {
            KeyFactory kf = KeyFactory.getInstance(algorithm);
            return kf.generatePrivate(new PKCS8EncodedKeySpec(fakeKey));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static boolean sWasVerboseLoggingEnabled;
    private static boolean sWasScanThrottleEnabled;
    private static WifiConfiguration sTestNetwork;
    private static boolean sShouldRunTest = false;

    private static Context sContext;
    private static WifiManager sWifiManager;
    private static ConnectivityManager sConnectivityManager;
    private ConnectivityManager.NetworkCallback mNrNetworkCallback;
    private static TestHelper sTestHelper;

    private static final int DURATION = 10_000;

    @BeforeClass
    public static void setUpClass() throws Exception {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        sTestHelper = new TestHelper(sContext, uiDevice);
        // skip the test if WiFi is not supported
        if (!WifiFeature.isWifiSupported(sContext)) return;
        sShouldRunTest = true;

        sWifiManager = sContext.getSystemService(WifiManager.class);
        assertThat(sWifiManager).isNotNull();
        sConnectivityManager = sContext.getSystemService(ConnectivityManager.class);

        // turn screen on
        sTestHelper.turnScreenOn();

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
        if (!sWifiManager.isWifiEnabled()) {
            ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.setWifiEnabled(true));
        }
        PollingCheck.check("Wifi not enabled", DURATION, () -> sWifiManager.isWifiEnabled());

        // check we have >= 1 saved network
        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.getPrivilegedConfiguredNetworks());
        assertWithMessage("Need at least one saved network")
                .that(savedNetworks.isEmpty()).isFalse();

        // Pick any network in range.
        sTestNetwork = TestHelper.findMatchingSavedNetworksWithBssid(sWifiManager, savedNetworks, 2)
                .get(0);
    }

    private static void enableAllSavedNetworks(@NonNull WifiManager wifiManager) {
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    for (WifiConfiguration savedNetwork : wifiManager.getConfiguredNetworks()) {
                        wifiManager.enableNetwork(savedNetwork.networkId, false);
                    }
                });
    }

    private static void disableAllSavedNetworks(@NonNull WifiManager wifiManager) {
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    for (WifiConfiguration savedNetwork : wifiManager.getConfiguredNetworks()) {
                        wifiManager.disableNetwork(savedNetwork.networkId);
                    }
                });
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (!sShouldRunTest) return;
        assertThat(sWifiManager).isNotNull();

        if (!sWifiManager.isWifiEnabled()) {
            ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.setWifiEnabled(true));
        }

        // Re-enable networks.
        enableAllSavedNetworks(sWifiManager);

        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScanThrottleEnabled(sWasScanThrottleEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setVerboseLoggingEnabled(sWasVerboseLoggingEnabled));
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(sShouldRunTest);

        // Clear any existing app state before each test.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.removeAppState(myUid(), sContext.getPackageName()));

        // Disconnect & disable auto-join on the saved network to prevent auto-connect from
        // interfering with the test.
        disableAllSavedNetworks(sWifiManager);
        ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.disconnect());

        // Wait for Wifi to be disconnected.
        PollingCheck.check(
                "Wifi not disconnected",
                20000,
                () -> sWifiManager.getConnectionInfo().getNetworkId() == -1);
    }

    @After
    public void tearDown() throws Exception {
        if (!sShouldRunTest) return;
        // If there is failure, ensure we unregister the previous request.
        if (mNrNetworkCallback != null) {
            sConnectivityManager.unregisterNetworkCallback(mNrNetworkCallback);
        }

        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.removeAppState(myUid(), sContext.getPackageName()));
    }

    private void testSuccessfulConnectionWithSpecifier(WifiNetworkSpecifier specifier)
            throws Exception {
        mNrNetworkCallback = sTestHelper.testConnectionFlowWithSpecifier(
                sTestNetwork, specifier, false);
    }

    private void testUserRejectionWithSpecifier(WifiNetworkSpecifier specifier)
            throws Exception {
        mNrNetworkCallback = sTestHelper.testConnectionFlowWithSpecifier(
                sTestNetwork, specifier, true);
    }

    /**
     * Tests the entire connection flow using a specific SSID in the specifier.
     */
    @Test
    public void testConnectionWithSpecificSsid() throws Exception {
        WifiNetworkSpecifier specifier =
                TestHelper.createSpecifierBuilderWithCredentialFromSavedNetwork(
                        sTestNetwork, true)
                .build();
        assertThat(specifier.getPreferredChannelFrequenciesMhz().length != 0).isTrue();
        assertThat(ScanResult.convertFrequencyMhzToChannelIfSupported(specifier
                .getPreferredChannelFrequenciesMhz()[0]) != ScanResult.UNSPECIFIED).isTrue();
        testSuccessfulConnectionWithSpecifier(specifier);
    }

    /**
     * Tests the entire connection flow using a SSID pattern in the specifier.
     */
    @Test
    public void testConnectionWithSsidPattern() throws Exception {
        // Creates a ssid pattern by dropping the last char in the saved network & pass that
        // as a prefix match pattern in the request.
        String ssidUnquoted = WifiInfo.sanitizeSsid(sTestNetwork.SSID);
        assertThat(ssidUnquoted.length()).isAtLeast(2);
        String ssidPrefix = ssidUnquoted.substring(0, ssidUnquoted.length() - 1);
        // Note: The match may return more than 1 network in this case since we use a prefix match,
        // But, we will still ensure that the UI interactions in the test still selects the
        // saved network for connection.
        WifiNetworkSpecifier specifier =
                TestHelper.createSpecifierBuilderWithCredentialFromSavedNetwork(sTestNetwork, true)
                .setSsidPattern(new PatternMatcher(ssidPrefix, PatternMatcher.PATTERN_PREFIX))
                .build();
        assertThat(specifier.getPreferredChannelFrequenciesMhz().length != 0).isTrue();
        assertThat(ScanResult.convertFrequencyMhzToChannelIfSupported(specifier
                .getPreferredChannelFrequenciesMhz()[0]) != ScanResult.UNSPECIFIED).isTrue();
        testSuccessfulConnectionWithSpecifier(specifier);
    }

    /**
     * Tests the entire connection flow using a specific BSSID in the specifier.
     */
    @Test
    public void testConnectionWithSpecificBssid() throws Exception {
        WifiNetworkSpecifier specifier =
                TestHelper.createSpecifierBuilderWithCredentialFromSavedNetworkWithBssid(
                        sTestNetwork)
                        .build();
        testSuccessfulConnectionWithSpecifier(specifier);
    }

    /**
     * Tests the entire connection flow using a BSSID pattern in the specifier.
     */
    @Test
    public void testConnectionWithBssidPattern() throws Exception {
        // Note: The match may return more than 1 network in this case since we use a prefix match,
        // But, we will still ensure that the UI interactions in the test still selects the
        // saved network for connection.
        WifiNetworkSpecifier specifier =
                TestHelper.createSpecifierBuilderWithCredentialFromSavedNetworkWithBssid(
                        sTestNetwork)
                        .setBssidPattern(MacAddress.fromString(sTestNetwork.BSSID),
                                MacAddress.fromString("ff:ff:ff:00:00:00"))
                        .build();
        testSuccessfulConnectionWithSpecifier(specifier);
    }

    /**
     * Tests the entire connection flow using a BSSID pattern in the specifier.
     */
    @Test
    public void testUserRejectionWithSpecificSsid() throws Exception {
        WifiNetworkSpecifier specifier =
                TestHelper.createSpecifierBuilderWithCredentialFromSavedNetwork(
                        sTestNetwork, true)
                        .build();
        assertThat(specifier.getPreferredChannelFrequenciesMhz().length != 0).isTrue();
        assertThat(ScanResult.convertFrequencyMhzToChannelIfSupported(specifier
                .getPreferredChannelFrequenciesMhz()[0]) != ScanResult.UNSPECIFIED).isTrue();
        testUserRejectionWithSpecifier(specifier);
    }

    /**
     * Tests using the specifier to set a band.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testWifiBandInNetworkCallback() throws Exception {
        // Enable all networks and wait for Internet connectivity to be restored.
        // The callbacks in this test will match the existing network as soon as they are filed.
        enableAllSavedNetworks(sWifiManager);
        sTestHelper.assertWifiInternetConnectionAvailable();

        final LinkedBlockingQueue<Pair<Integer, Integer>> results = new LinkedBlockingQueue<>();
        final int[] bands = { ScanResult.WIFI_BAND_24_GHZ, ScanResult.WIFI_BAND_5_GHZ,
                ScanResult.WIFI_BAND_6_GHZ, ScanResult.WIFI_BAND_60_GHZ };
        final ArrayList<NetworkCallback> registeredCallbacks = new ArrayList<>();
        for (final int band : bands) {
            final NetworkCallback callback = new NetworkCallback() {
                @Override public void onCapabilitiesChanged(final Network net,
                        final NetworkCapabilities caps) {
                    results.offer(new Pair(band, TestHelper.getBandFromFrequency(
                            ((WifiInfo) caps.getTransportInfo()).getFrequency())));
                }
            };

            final WifiNetworkSpecifier specifier =
                    new WifiNetworkSpecifier.Builder().setBand(band).build();
            assertThat(specifier.getBand()).isEqualTo(band);

            final NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build();
            sConnectivityManager.registerNetworkCallback(request, callback);
            registeredCallbacks.add(callback);
        }

        try {
            // There should be at least one network callback about availability of the wifi network
            // on the right band. If the device is currently connected to multiple WiFi networks,
            // there will be several. Wait for a relatively long time for any callback, but only
            // a short time for subsequent ones (as the last timeout will be incurred always).
            Pair<Integer, Integer> result = results.poll(10, TimeUnit.SECONDS);
            assertThat(result).isNotNull();
            while (null != result) {
                assertThat(result.first).isEqualTo(result.second);
                result = results.poll(200, TimeUnit.MILLISECONDS);
            }
        } finally {
            for (final NetworkCallback cb : registeredCallbacks) {
                sConnectivityManager.unregisterNetworkCallback(cb);
            }
        }
    }

    /**
     * Tests the entire connection flow using a specific SSID in the specifier and ensure that the
     * device auto connects back to some saved network or suggestions in range of the device (that
     * can provide internet connectivity) when the request is released.
     */
    @Test
    public void testEnsureAutoConnectToInternetConnectionOnRelease() throws Exception {
        WifiNetworkSpecifier specifier =
                TestHelper.createSpecifierBuilderWithCredentialFromSavedNetwork(
                        sTestNetwork, true)
                        .build();
        assertThat(specifier.getPreferredChannelFrequenciesMhz().length != 0).isTrue();
        assertThat(ScanResult.convertFrequencyMhzToChannelIfSupported(specifier
                .getPreferredChannelFrequenciesMhz()[0]) != ScanResult.UNSPECIFIED).isTrue();
        testSuccessfulConnectionWithSpecifier(specifier);

        // Now release the network request.
        sConnectivityManager.unregisterNetworkCallback(mNrNetworkCallback);
        mNrNetworkCallback = null;

        // Enable all saved networks on the device
        enableAllSavedNetworks(sWifiManager);
        try {
            // Wait for the device to auto-connect back to some saved or suggested network (which
            // can provide internet connectivity.
            // Note: On devices with concurrency support, this may return true immediately (since
            // the internet connection may be present concurrently).
            sTestHelper.assertWifiInternetConnectionAvailable();
        } finally {
            // need to always disable saved networks again since the other tests in this class
            // assume it
            disableAllSavedNetworks(sWifiManager);
        }
    }

    /**
     * Tests the builder for WPA2 enterprise networks.
     * Note: Can't do end to end tests for such networks in CTS environment.
     */
    @Test
    public void testBuilderForWpa2Enterprise() {
        WifiNetworkSpecifier specifier1 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(sTestNetwork.SSID))
                .setWpa2EnterpriseConfig(new WifiEnterpriseConfig())
                .build();
        WifiNetworkSpecifier specifier2 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(sTestNetwork.SSID))
                .setWpa2EnterpriseConfig(new WifiEnterpriseConfig())
                .build();
        assertThat(specifier1.canBeSatisfiedBy(specifier2)).isTrue();
    }

    /**
     * Tests the builder for WPA3 enterprise networks.
     * Note: Can't do end to end tests for such networks in CTS environment.
     */
    @Test
    public void testBuilderForWpa3Enterprise() {
        WifiNetworkSpecifier specifier1 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(sTestNetwork.SSID))
                .setWpa3EnterpriseConfig(new WifiEnterpriseConfig())
                .build();
        WifiNetworkSpecifier specifier2 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(sTestNetwork.SSID))
                .setWpa3EnterpriseConfig(new WifiEnterpriseConfig())
                .build();
        assertThat(specifier1.canBeSatisfiedBy(specifier2)).isTrue();
    }

    /**
     * Tests the builder for WPA3 enterprise networks.
     * Note: Can't do end to end tests for such networks in CTS environment.
     */
    @Test
    public void testBuilderForWpa3EnterpriseWithStandardApi() {
        WifiNetworkSpecifier specifier1 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(sTestNetwork.SSID))
                .setWpa3EnterpriseStandardModeConfig(new WifiEnterpriseConfig())
                .build();
        WifiNetworkSpecifier specifier2 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(sTestNetwork.SSID))
                .setWpa3EnterpriseConfig(new WifiEnterpriseConfig())
                .build();
        assertThat(specifier1.canBeSatisfiedBy(specifier2)).isTrue();
    }

    /**
     * Tests the builder for WPA3 enterprise networks.
     * Note: Can't do end to end tests for such networks in CTS environment.
     */
    @Test
    public void testBuilderForWpa3Enterprise192bit() {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        enterpriseConfig.setCaCertificate(CA_SUITE_B_ECDSA_CERT);
        enterpriseConfig.setClientKeyEntryWithCertificateChain(CLIENT_SUITE_B_ECC_KEY,
                new X509Certificate[] {CLIENT_SUITE_B_ECDSA_CERT});
        enterpriseConfig.setAltSubjectMatch("domain.com");

        WifiNetworkSpecifier specifier1 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(sTestNetwork.SSID))
                .setWpa3Enterprise192BitModeConfig(enterpriseConfig)
                .build();
        WifiNetworkSpecifier specifier2 = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(sTestNetwork.SSID))
                .setWpa3Enterprise192BitModeConfig(enterpriseConfig)
                .build();
        assertThat(specifier1.canBeSatisfiedBy(specifier2)).isTrue();
    }

    /**
     * Test WifiNetworkSpecifier redaction.
     */
    @Test
    public void testRedact() {
        WifiNetworkSpecifier specifier = TestHelper
                .createSpecifierBuilderWithCredentialFromSavedNetworkWithBssid(sTestNetwork)
                .setBssidPattern(MacAddress.fromString(sTestNetwork.BSSID),
                        MacAddress.fromString("ff:ff:ff:00:00:00"))
                .setBand(ScanResult.WIFI_BAND_5_GHZ)
                .build();
        assertThat(specifier.getPreferredChannelFrequenciesMhz().length == 0).isTrue();

        final NetworkSpecifier redacted = specifier.redact();
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
            assertThat(new WifiNetworkSpecifier.Builder().setBand(ScanResult.WIFI_BAND_5_GHZ)
                    .build().equals(redacted)).isTrue();
        } else {
            assertThat(redacted.equals(specifier)).isTrue();
        }
    }
}
