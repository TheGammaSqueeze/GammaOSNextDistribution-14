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

import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PAID;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE;
import static android.net.wifi.WifiEnterpriseConfig.Eap.AKA;
import static android.net.wifi.WifiEnterpriseConfig.Eap.WAPI_CERT;
import static android.os.Process.myUid;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiSsid;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.os.Build;
import android.os.ParcelUuid;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.support.test.uiautomator.UiDevice;
import android.telephony.TelephonyManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.wifi.flags.Flags;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@LargeTest
@RunWith(AndroidJUnit4.class)
public class WifiNetworkSuggestionTest extends WifiJUnit4TestBase {
    private static final String TAG = "WifiNetworkSuggestionTest";

    private static final String TEST_SSID = "testSsid";
    private static final String TEST_BSSID = "00:df:aa:bc:12:23";
    private static final String TEST_PASSPHRASE = "testPassword";
    private static final int TEST_PRIORITY = 5;
    private static final int TEST_PRIORITY_GROUP = 1;
    private static final int TEST_SUB_ID = 1;
    private static final ParcelUuid GROUP_UUID = ParcelUuid
            .fromString("0000110B-0000-1000-8000-00805F9B34FB");
    private static final int DURATION_NETWORK_DISCONNECT_MILLIS = 3_000;
    private static final int DURATION_NETWORK_LINGER_MILLIS = 30_000;
    private static final int DURATION_NETWORK_UPDATE = 10_000;

    private static boolean sWasVerboseLoggingEnabled;
    private static boolean sWasScanThrottleEnabled;
    private static boolean sWasWifiEnabled;
    private static boolean sShouldRunTest = false;

    private static Context sContext;
    private static WifiManager sWifiManager;
    private static ConnectivityManager sConnectivityManager;
    private static UiDevice sUiDevice;
    private static WifiConfiguration sTestNetwork;
    private static ConnectivityManager.NetworkCallback sNsNetworkCallback;
    private static TestHelper sTestHelper;

    private ScheduledExecutorService mExecutorService;

    private static final int DURATION_MILLIS = 10_000;

    @BeforeClass
    public static void setUpClass() throws Exception {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        // skip the test if WiFi is not supported
        // Don't use assumeTrue in @BeforeClass
        if (!WifiFeature.isWifiSupported(sContext)) return;
        // skip the test if location is not supported
        if (!sContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION)) return;
        // skip if the location is disabled
        if (!sContext.getSystemService(LocationManager.class).isLocationEnabled()) return;
        sShouldRunTest = true;

        sWifiManager = sContext.getSystemService(WifiManager.class);
        assertThat(sWifiManager).isNotNull();
        sConnectivityManager = sContext.getSystemService(ConnectivityManager.class);
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
        // turn screen on
        sTestHelper.turnScreenOn();


        // enable Wifi
        sWasWifiEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.isWifiEnabled());
        if (!sWifiManager.isWifiEnabled()) {
            ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.setWifiEnabled(true));
        }
        PollingCheck.check("Wifi not enabled", DURATION_MILLIS, () -> sWifiManager.isWifiEnabled());

        // check we have >= 1 saved network
        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.getPrivilegedConfiguredNetworks());
        if (savedNetworks.isEmpty()) {
            return;
        }
        // Pick any network in range.

        List<WifiConfiguration> networks = TestHelper.findMatchingSavedNetworksWithBssid(
                sWifiManager, savedNetworks, 2);
        if (!networks.isEmpty()) {
            sTestNetwork = networks.get(0);
        }

        // Disable auto-join on the saved network to prevent auto-connect from
        // interfering with the test.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    for (WifiConfiguration savedNetwork : savedNetworks) {
                        sWifiManager.disableNetwork(savedNetwork.networkId);
                    }
                });
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (!sShouldRunTest) return;

        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScanThrottleEnabled(sWasScanThrottleEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setVerboseLoggingEnabled(sWasVerboseLoggingEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setWifiEnabled(sWasWifiEnabled));

        // Re-enable networks.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    for (WifiConfiguration savedNetwork : sWifiManager.getConfiguredNetworks()) {
                        sWifiManager.enableNetwork(savedNetwork.networkId, false);
                    }
                });
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(sShouldRunTest);
        mExecutorService = Executors.newSingleThreadScheduledExecutor();

        // Disconnect current network if any.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.disconnect());

        // Wait for Wifi to be disconnected.
        PollingCheck.check(
                "Wifi not disconnected",
                20_000,
                () -> sWifiManager.getConnectionInfo().getNetworkId() == -1);

        // Clear any existing app state before each test.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.removeAppState(myUid(), sContext.getPackageName()));
    }

    @After
    public void tearDown() throws Exception {
        if (!sShouldRunTest) return;
        // Release the requests after the test.
        if (sNsNetworkCallback != null) {
            sConnectivityManager.unregisterNetworkCallback(sNsNetworkCallback);
        }
        mExecutorService.shutdownNow();
        // Clear any existing app state after each test.
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.removeAppState(myUid(), sContext.getPackageName()));
    }

    private static final String CA_SUITE_B_RSA3072_CERT_STRING =
            "-----BEGIN CERTIFICATE-----\n"
                    + "MIIEnTCCAwWgAwIBAgIUD87Y8fFLzLr1HQ/64aEnjNq2R/4wDQYJKoZIhvcNAQEM\n"
                    + "BQAwXjELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNBMQwwCgYDVQQHDANNVFYxEDAO\n"
                    + "BgNVBAoMB0FuZHJvaWQxDjAMBgNVBAsMBVdpLUZpMRIwEAYDVQQDDAl1bml0ZXN0\n"
                    + "Q0EwHhcNMjAwNzIxMDIxNzU0WhcNMzAwNTMwMDIxNzU0WjBeMQswCQYDVQQGEwJV\n"
                    + "UzELMAkGA1UECAwCQ0ExDDAKBgNVBAcMA01UVjEQMA4GA1UECgwHQW5kcm9pZDEO\n"
                    + "MAwGA1UECwwFV2ktRmkxEjAQBgNVBAMMCXVuaXRlc3RDQTCCAaIwDQYJKoZIhvcN\n"
                    + "AQEBBQADggGPADCCAYoCggGBAMtrsT0otlxh0QS079KpRRbU1PQjCihSoltXnrxF\n"
                    + "sTWZs2weVEeYVyYU5LaauCDDgISCMtjtfbfylMBeYjpWB5hYzYQOiTzo0anWhMyb\n"
                    + "Ngb7gpMVZuIl6lwMYRyVRKwHWnTo2EUg1ZzW5rGe5fs/KHj6//hoNFm+3Oju0TQd\n"
                    + "nraQULpoERPF5B7p85Cssk8uNbviBfZXvtCuJ4N6w7PNceOY/9bbwc1mC+pPZmzV\n"
                    + "SOAg0vvbIQRzChm63C3jBC3xmxSOOZVrKN4zKDG2s8P0oCNGt0NlgRMrgbPRekzg\n"
                    + "4avkbA0vTuc2AyriTEYkdea/Mt4EpRg9XuOb43U/GJ/d/vQv2/9fsxhXmsZrn8kr\n"
                    + "Qo5MMHJFUd96GgHmvYSU3Mf/5r8gF626lvqHioGuTAuHUSnr02ri1WUxZ15LDRgY\n"
                    + "quMjDCFZfucjJPDAdtiHcFSej/4SLJlN39z8oKKNPn3aL9Gv49oAKs9S8tfDVzMk\n"
                    + "fDLROQFHFuW715GnnMgEAoOpRwIDAQABo1MwUTAdBgNVHQ4EFgQUeVuGmSVN4ARs\n"
                    + "mesUMWSJ2qWLbxUwHwYDVR0jBBgwFoAUeVuGmSVN4ARsmesUMWSJ2qWLbxUwDwYD\n"
                    + "VR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQwFAAOCAYEAit1Lo/hegZpPuT9dlWZJ\n"
                    + "bC8JvAf95O8lnn6LFb69pgYOHCLgCIlvYXu9rdBUJgZo+V1MzJJljiO6RxWRfKbQ\n"
                    + "8WBYkoqR1EqriR3Kn8q/SjIZCdFSaznTyU1wQMveBQ6RJWXSUhYVfE9RjyFTp7B4\n"
                    + "UyH2uCluR/0T06HQNGfH5XpIYQqCk1Zgng5lmEmheLDPoJpa92lKeQFJMC6eYz9g\n"
                    + "lF1GHxPxkPfbMJ6ZDp5X6Yopu6Q6uEXhVKM/iQVcgzRkx9rid+xTYl+nOKyK/XfC\n"
                    + "z8P0/TFIoPTW02DLge5wKagdoCpy1B7HdrAXyUjoH4B8MsUkq3kYPFSjPzScuTtV\n"
                    + "kUuDw5ipCNeXCRnhbYqRDk6PX5GUu2cmN9jtaH3tbgm3fKNOsd/BO1fLIl7qjXlR\n"
                    + "27HHbC0JXjNvlm2DLp23v4NTxS7WZGYsxyUj5DZrxBxqCsTXu/01w1BrQKWKh9FM\n"
                    + "aVrlA8omfVODK2CSuw+KhEMHepRv/AUgsLl4L4+RMoa+\n"
                    + "-----END CERTIFICATE-----\n";
    private static final X509Certificate CA_SUITE_B_RSA3072_CERT =
            loadCertificate(CA_SUITE_B_RSA3072_CERT_STRING);

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
    private static final X509Certificate CA_SUITE_B_ECDSA_CERT =
            loadCertificate(CA_SUITE_B_ECDSA_CERT_STRING);

    private static final String CLIENT_SUITE_B_RSA3072_CERT_STRING =
            "-----BEGIN CERTIFICATE-----\n"
                    + "MIIERzCCAq8CFDopjyNgaj+c2TN2k06h7okEWpHJMA0GCSqGSIb3DQEBDAUAMF4x\n"
                    + "CzAJBgNVBAYTAlVTMQswCQYDVQQIDAJDQTEMMAoGA1UEBwwDTVRWMRAwDgYDVQQK\n"
                    + "DAdBbmRyb2lkMQ4wDAYDVQQLDAVXaS1GaTESMBAGA1UEAwwJdW5pdGVzdENBMB4X\n"
                    + "DTIwMDcyMTAyMjkxMVoXDTMwMDUzMDAyMjkxMVowYjELMAkGA1UEBhMCVVMxCzAJ\n"
                    + "BgNVBAgMAkNBMQwwCgYDVQQHDANNVFYxEDAOBgNVBAoMB0FuZHJvaWQxDjAMBgNV\n"
                    + "BAsMBVdpLUZpMRYwFAYDVQQDDA11bml0ZXN0Q2xpZW50MIIBojANBgkqhkiG9w0B\n"
                    + "AQEFAAOCAY8AMIIBigKCAYEAwSK3C5K5udtCKTnE14e8z2cZvwmB4Xe+a8+7QLud\n"
                    + "Hooc/lQzClgK4MbVUC0D3FE+U32C78SxKoTaRWtvPmNm+UaFT8KkwyUno/dv+2XD\n"
                    + "pd/zARQ+3FwAfWopAhEyCVSxwsCa+slQ4juRIMIuUC1Mm0NaptZyM3Tj/ICQEfpk\n"
                    + "o9qVIbiK6eoJMTkY8EWfAn7RTFdfR1OLuO0mVOjgLW9/+upYv6hZ19nAMAxw4QTJ\n"
                    + "x7lLwALX7B+tDYNEZHDqYL2zyvQWAj2HClere8QYILxkvktgBg2crEJJe4XbDH7L\n"
                    + "A3rrXmsiqf1ZbfFFEzK9NFqovL+qGh+zIP+588ShJFO9H/RDnDpiTnAFTWXQdTwg\n"
                    + "szSS0Vw2PB+JqEABAa9DeMvXT1Oy+NY3ItPHyy63nQZVI2rXANw4NhwS0Z6DF+Qs\n"
                    + "TNrj+GU7e4SG/EGR8SvldjYfQTWFLg1l/UT1hOOkQZwdsaW1zgKyeuiFB2KdMmbA\n"
                    + "Sq+Ux1L1KICo0IglwWcB/8nnAgMBAAEwDQYJKoZIhvcNAQEMBQADggGBAMYwJkNw\n"
                    + "BaCviKFmReDTMwWPRy4AMNViEeqAXgERwDEKwM7efjsaj5gctWfKsxX6UdLzkhgg\n"
                    + "6S/T6PxVWKzJ6l7SoOuTa6tMQOZp+h3R1mdfEQbw8B5cXBxZ+batzAai6Fiy1FKS\n"
                    + "/ka3INbcGfYuIYghfTrb4/NJKN06ZaQ1bpPwq0e4gN7800T2nbawvSf7r+8ZLcG3\n"
                    + "6bGCjRMwDSIipNvOwoj3TG315XC7TccX5difQ4sKOY+d2MkVJ3RiO0Ciw2ZbEW8d\n"
                    + "1FH5vUQJWnBUfSFznosGzLwH3iWfqlP+27jNE+qB2igEwCRFgVAouURx5ou43xuX\n"
                    + "qf6JkdI3HTJGLIWxkp7gOeln4dEaYzKjYw+P0VqJvKVqQ0IXiLjHgE0J9p0vgyD6\n"
                    + "HVVcP7U8RgqrbIjL1QgHU4KBhGi+WSUh/mRplUCNvHgcYdcHi/gHpj/j6ubwqIGV\n"
                    + "z4iSolAHYTmBWcLyE0NgpzE6ntp+53r2KaUJA99l2iGVzbWTwqPSm0XAVw==\n"
                    + "-----END CERTIFICATE-----\n";
    private static final X509Certificate CLIENT_SUITE_B_RSA3072_CERT =
            loadCertificate(CLIENT_SUITE_B_RSA3072_CERT_STRING);

    private static final byte[] CLIENT_SUITE_B_RSA3072_KEY_DATA = new byte[]{
            (byte) 0x30, (byte) 0x82, (byte) 0x06, (byte) 0xfe, (byte) 0x02, (byte) 0x01,
            (byte) 0x00, (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09, (byte) 0x2a,
            (byte) 0x86, (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x01,
            (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x04, (byte) 0x82,
            (byte) 0x06, (byte) 0xe8, (byte) 0x30, (byte) 0x82, (byte) 0x06, (byte) 0xe4,
            (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x02, (byte) 0x82, (byte) 0x01,
            (byte) 0x81, (byte) 0x00, (byte) 0xc1, (byte) 0x22, (byte) 0xb7, (byte) 0x0b,
            (byte) 0x92, (byte) 0xb9, (byte) 0xb9, (byte) 0xdb, (byte) 0x42, (byte) 0x29,
            (byte) 0x39, (byte) 0xc4, (byte) 0xd7, (byte) 0x87, (byte) 0xbc, (byte) 0xcf,
            (byte) 0x67, (byte) 0x19, (byte) 0xbf, (byte) 0x09, (byte) 0x81, (byte) 0xe1,
            (byte) 0x77, (byte) 0xbe, (byte) 0x6b, (byte) 0xcf, (byte) 0xbb, (byte) 0x40,
            (byte) 0xbb, (byte) 0x9d, (byte) 0x1e, (byte) 0x8a, (byte) 0x1c, (byte) 0xfe,
            (byte) 0x54, (byte) 0x33, (byte) 0x0a, (byte) 0x58, (byte) 0x0a, (byte) 0xe0,
            (byte) 0xc6, (byte) 0xd5, (byte) 0x50, (byte) 0x2d, (byte) 0x03, (byte) 0xdc,
            (byte) 0x51, (byte) 0x3e, (byte) 0x53, (byte) 0x7d, (byte) 0x82, (byte) 0xef,
            (byte) 0xc4, (byte) 0xb1, (byte) 0x2a, (byte) 0x84, (byte) 0xda, (byte) 0x45,
            (byte) 0x6b, (byte) 0x6f, (byte) 0x3e, (byte) 0x63, (byte) 0x66, (byte) 0xf9,
            (byte) 0x46, (byte) 0x85, (byte) 0x4f, (byte) 0xc2, (byte) 0xa4, (byte) 0xc3,
            (byte) 0x25, (byte) 0x27, (byte) 0xa3, (byte) 0xf7, (byte) 0x6f, (byte) 0xfb,
            (byte) 0x65, (byte) 0xc3, (byte) 0xa5, (byte) 0xdf, (byte) 0xf3, (byte) 0x01,
            (byte) 0x14, (byte) 0x3e, (byte) 0xdc, (byte) 0x5c, (byte) 0x00, (byte) 0x7d,
            (byte) 0x6a, (byte) 0x29, (byte) 0x02, (byte) 0x11, (byte) 0x32, (byte) 0x09,
            (byte) 0x54, (byte) 0xb1, (byte) 0xc2, (byte) 0xc0, (byte) 0x9a, (byte) 0xfa,
            (byte) 0xc9, (byte) 0x50, (byte) 0xe2, (byte) 0x3b, (byte) 0x91, (byte) 0x20,
            (byte) 0xc2, (byte) 0x2e, (byte) 0x50, (byte) 0x2d, (byte) 0x4c, (byte) 0x9b,
            (byte) 0x43, (byte) 0x5a, (byte) 0xa6, (byte) 0xd6, (byte) 0x72, (byte) 0x33,
            (byte) 0x74, (byte) 0xe3, (byte) 0xfc, (byte) 0x80, (byte) 0x90, (byte) 0x11,
            (byte) 0xfa, (byte) 0x64, (byte) 0xa3, (byte) 0xda, (byte) 0x95, (byte) 0x21,
            (byte) 0xb8, (byte) 0x8a, (byte) 0xe9, (byte) 0xea, (byte) 0x09, (byte) 0x31,
            (byte) 0x39, (byte) 0x18, (byte) 0xf0, (byte) 0x45, (byte) 0x9f, (byte) 0x02,
            (byte) 0x7e, (byte) 0xd1, (byte) 0x4c, (byte) 0x57, (byte) 0x5f, (byte) 0x47,
            (byte) 0x53, (byte) 0x8b, (byte) 0xb8, (byte) 0xed, (byte) 0x26, (byte) 0x54,
            (byte) 0xe8, (byte) 0xe0, (byte) 0x2d, (byte) 0x6f, (byte) 0x7f, (byte) 0xfa,
            (byte) 0xea, (byte) 0x58, (byte) 0xbf, (byte) 0xa8, (byte) 0x59, (byte) 0xd7,
            (byte) 0xd9, (byte) 0xc0, (byte) 0x30, (byte) 0x0c, (byte) 0x70, (byte) 0xe1,
            (byte) 0x04, (byte) 0xc9, (byte) 0xc7, (byte) 0xb9, (byte) 0x4b, (byte) 0xc0,
            (byte) 0x02, (byte) 0xd7, (byte) 0xec, (byte) 0x1f, (byte) 0xad, (byte) 0x0d,
            (byte) 0x83, (byte) 0x44, (byte) 0x64, (byte) 0x70, (byte) 0xea, (byte) 0x60,
            (byte) 0xbd, (byte) 0xb3, (byte) 0xca, (byte) 0xf4, (byte) 0x16, (byte) 0x02,
            (byte) 0x3d, (byte) 0x87, (byte) 0x0a, (byte) 0x57, (byte) 0xab, (byte) 0x7b,
            (byte) 0xc4, (byte) 0x18, (byte) 0x20, (byte) 0xbc, (byte) 0x64, (byte) 0xbe,
            (byte) 0x4b, (byte) 0x60, (byte) 0x06, (byte) 0x0d, (byte) 0x9c, (byte) 0xac,
            (byte) 0x42, (byte) 0x49, (byte) 0x7b, (byte) 0x85, (byte) 0xdb, (byte) 0x0c,
            (byte) 0x7e, (byte) 0xcb, (byte) 0x03, (byte) 0x7a, (byte) 0xeb, (byte) 0x5e,
            (byte) 0x6b, (byte) 0x22, (byte) 0xa9, (byte) 0xfd, (byte) 0x59, (byte) 0x6d,
            (byte) 0xf1, (byte) 0x45, (byte) 0x13, (byte) 0x32, (byte) 0xbd, (byte) 0x34,
            (byte) 0x5a, (byte) 0xa8, (byte) 0xbc, (byte) 0xbf, (byte) 0xaa, (byte) 0x1a,
            (byte) 0x1f, (byte) 0xb3, (byte) 0x20, (byte) 0xff, (byte) 0xb9, (byte) 0xf3,
            (byte) 0xc4, (byte) 0xa1, (byte) 0x24, (byte) 0x53, (byte) 0xbd, (byte) 0x1f,
            (byte) 0xf4, (byte) 0x43, (byte) 0x9c, (byte) 0x3a, (byte) 0x62, (byte) 0x4e,
            (byte) 0x70, (byte) 0x05, (byte) 0x4d, (byte) 0x65, (byte) 0xd0, (byte) 0x75,
            (byte) 0x3c, (byte) 0x20, (byte) 0xb3, (byte) 0x34, (byte) 0x92, (byte) 0xd1,
            (byte) 0x5c, (byte) 0x36, (byte) 0x3c, (byte) 0x1f, (byte) 0x89, (byte) 0xa8,
            (byte) 0x40, (byte) 0x01, (byte) 0x01, (byte) 0xaf, (byte) 0x43, (byte) 0x78,
            (byte) 0xcb, (byte) 0xd7, (byte) 0x4f, (byte) 0x53, (byte) 0xb2, (byte) 0xf8,
            (byte) 0xd6, (byte) 0x37, (byte) 0x22, (byte) 0xd3, (byte) 0xc7, (byte) 0xcb,
            (byte) 0x2e, (byte) 0xb7, (byte) 0x9d, (byte) 0x06, (byte) 0x55, (byte) 0x23,
            (byte) 0x6a, (byte) 0xd7, (byte) 0x00, (byte) 0xdc, (byte) 0x38, (byte) 0x36,
            (byte) 0x1c, (byte) 0x12, (byte) 0xd1, (byte) 0x9e, (byte) 0x83, (byte) 0x17,
            (byte) 0xe4, (byte) 0x2c, (byte) 0x4c, (byte) 0xda, (byte) 0xe3, (byte) 0xf8,
            (byte) 0x65, (byte) 0x3b, (byte) 0x7b, (byte) 0x84, (byte) 0x86, (byte) 0xfc,
            (byte) 0x41, (byte) 0x91, (byte) 0xf1, (byte) 0x2b, (byte) 0xe5, (byte) 0x76,
            (byte) 0x36, (byte) 0x1f, (byte) 0x41, (byte) 0x35, (byte) 0x85, (byte) 0x2e,
            (byte) 0x0d, (byte) 0x65, (byte) 0xfd, (byte) 0x44, (byte) 0xf5, (byte) 0x84,
            (byte) 0xe3, (byte) 0xa4, (byte) 0x41, (byte) 0x9c, (byte) 0x1d, (byte) 0xb1,
            (byte) 0xa5, (byte) 0xb5, (byte) 0xce, (byte) 0x02, (byte) 0xb2, (byte) 0x7a,
            (byte) 0xe8, (byte) 0x85, (byte) 0x07, (byte) 0x62, (byte) 0x9d, (byte) 0x32,
            (byte) 0x66, (byte) 0xc0, (byte) 0x4a, (byte) 0xaf, (byte) 0x94, (byte) 0xc7,
            (byte) 0x52, (byte) 0xf5, (byte) 0x28, (byte) 0x80, (byte) 0xa8, (byte) 0xd0,
            (byte) 0x88, (byte) 0x25, (byte) 0xc1, (byte) 0x67, (byte) 0x01, (byte) 0xff,
            (byte) 0xc9, (byte) 0xe7, (byte) 0x02, (byte) 0x03, (byte) 0x01, (byte) 0x00,
            (byte) 0x01, (byte) 0x02, (byte) 0x82, (byte) 0x01, (byte) 0x80, (byte) 0x04,
            (byte) 0xb1, (byte) 0xcc, (byte) 0x53, (byte) 0x3a, (byte) 0xb0, (byte) 0xcb,
            (byte) 0x04, (byte) 0xba, (byte) 0x59, (byte) 0xf8, (byte) 0x2e, (byte) 0x81,
            (byte) 0xb2, (byte) 0xa9, (byte) 0xf3, (byte) 0x3c, (byte) 0xa5, (byte) 0x52,
            (byte) 0x90, (byte) 0x6f, (byte) 0x98, (byte) 0xc4, (byte) 0x69, (byte) 0x5b,
            (byte) 0x83, (byte) 0x84, (byte) 0x20, (byte) 0xb1, (byte) 0xae, (byte) 0xc3,
            (byte) 0x04, (byte) 0x46, (byte) 0x6a, (byte) 0x24, (byte) 0x2f, (byte) 0xcd,
            (byte) 0x6b, (byte) 0x90, (byte) 0x70, (byte) 0x20, (byte) 0x45, (byte) 0x25,
            (byte) 0x1a, (byte) 0xc3, (byte) 0x02, (byte) 0x42, (byte) 0xf3, (byte) 0x49,
            (byte) 0xe2, (byte) 0x3e, (byte) 0x21, (byte) 0x87, (byte) 0xdd, (byte) 0x6a,
            (byte) 0x94, (byte) 0x2a, (byte) 0x1e, (byte) 0x0f, (byte) 0xdb, (byte) 0x77,
            (byte) 0x5f, (byte) 0xc1, (byte) 0x2c, (byte) 0x03, (byte) 0xfb, (byte) 0xcf,
            (byte) 0x91, (byte) 0x82, (byte) 0xa1, (byte) 0xbf, (byte) 0xb0, (byte) 0x73,
            (byte) 0xfa, (byte) 0xda, (byte) 0xbc, (byte) 0xf8, (byte) 0x9f, (byte) 0x45,
            (byte) 0xd3, (byte) 0xe8, (byte) 0xbb, (byte) 0x38, (byte) 0xfb, (byte) 0xc2,
            (byte) 0x2d, (byte) 0x76, (byte) 0x51, (byte) 0x96, (byte) 0x18, (byte) 0x03,
            (byte) 0x15, (byte) 0xd9, (byte) 0xea, (byte) 0x82, (byte) 0x25, (byte) 0x83,
            (byte) 0xff, (byte) 0x5c, (byte) 0x85, (byte) 0x06, (byte) 0x09, (byte) 0xb2,
            (byte) 0x46, (byte) 0x12, (byte) 0x64, (byte) 0x02, (byte) 0x74, (byte) 0x4f,
            (byte) 0xbc, (byte) 0x9a, (byte) 0x25, (byte) 0x18, (byte) 0x01, (byte) 0x07,
            (byte) 0x17, (byte) 0x25, (byte) 0x55, (byte) 0x7c, (byte) 0xdc, (byte) 0xe1,
            (byte) 0xd1, (byte) 0x5a, (byte) 0x2f, (byte) 0x25, (byte) 0xaf, (byte) 0xf6,
            (byte) 0x8f, (byte) 0xa4, (byte) 0x9a, (byte) 0x5a, (byte) 0x3a, (byte) 0xfe,
            (byte) 0x2e, (byte) 0x93, (byte) 0x24, (byte) 0xa0, (byte) 0x27, (byte) 0xac,
            (byte) 0x07, (byte) 0x75, (byte) 0x33, (byte) 0x01, (byte) 0x54, (byte) 0x23,
            (byte) 0x0f, (byte) 0xe8, (byte) 0x9f, (byte) 0xfa, (byte) 0x36, (byte) 0xe6,
            (byte) 0x3a, (byte) 0xd5, (byte) 0x78, (byte) 0xb0, (byte) 0xe4, (byte) 0x6a,
            (byte) 0x16, (byte) 0x50, (byte) 0xbd, (byte) 0x0f, (byte) 0x9f, (byte) 0x32,
            (byte) 0xa1, (byte) 0x6b, (byte) 0xf5, (byte) 0xa4, (byte) 0x34, (byte) 0x58,
            (byte) 0xb6, (byte) 0xa4, (byte) 0xb3, (byte) 0xc3, (byte) 0x83, (byte) 0x08,
            (byte) 0x18, (byte) 0xc7, (byte) 0xef, (byte) 0x95, (byte) 0xe2, (byte) 0x1b,
            (byte) 0xba, (byte) 0x35, (byte) 0x61, (byte) 0xa3, (byte) 0xb4, (byte) 0x30,
            (byte) 0xe0, (byte) 0xd1, (byte) 0xc1, (byte) 0xa2, (byte) 0x3a, (byte) 0xc6,
            (byte) 0xb4, (byte) 0xd2, (byte) 0x80, (byte) 0x5a, (byte) 0xaf, (byte) 0xa4,
            (byte) 0x54, (byte) 0x3c, (byte) 0x66, (byte) 0x5a, (byte) 0x1c, (byte) 0x4d,
            (byte) 0xe1, (byte) 0xd9, (byte) 0x98, (byte) 0x44, (byte) 0x01, (byte) 0x1b,
            (byte) 0x8c, (byte) 0xe9, (byte) 0x80, (byte) 0x54, (byte) 0x83, (byte) 0x3d,
            (byte) 0x96, (byte) 0x25, (byte) 0x41, (byte) 0x1c, (byte) 0xad, (byte) 0xae,
            (byte) 0x3b, (byte) 0x7a, (byte) 0xd7, (byte) 0x9d, (byte) 0x10, (byte) 0x7c,
            (byte) 0xd1, (byte) 0xa7, (byte) 0x96, (byte) 0x39, (byte) 0xa5, (byte) 0x2f,
            (byte) 0xbe, (byte) 0xc3, (byte) 0x2c, (byte) 0x64, (byte) 0x01, (byte) 0xfe,
            (byte) 0xa2, (byte) 0xd1, (byte) 0x6a, (byte) 0xcf, (byte) 0x4c, (byte) 0x76,
            (byte) 0x3b, (byte) 0xc8, (byte) 0x35, (byte) 0x21, (byte) 0xda, (byte) 0x98,
            (byte) 0xcf, (byte) 0xf9, (byte) 0x29, (byte) 0xff, (byte) 0x30, (byte) 0x59,
            (byte) 0x36, (byte) 0x53, (byte) 0x0b, (byte) 0xbb, (byte) 0xfa, (byte) 0xba,
            (byte) 0xc4, (byte) 0x03, (byte) 0x23, (byte) 0xe0, (byte) 0xd3, (byte) 0x33,
            (byte) 0xff, (byte) 0x32, (byte) 0xdb, (byte) 0x30, (byte) 0x64, (byte) 0xc7,
            (byte) 0x56, (byte) 0xca, (byte) 0x55, (byte) 0x14, (byte) 0xee, (byte) 0x58,
            (byte) 0xfe, (byte) 0x96, (byte) 0x7e, (byte) 0x1c, (byte) 0x34, (byte) 0x16,
            (byte) 0xeb, (byte) 0x76, (byte) 0x26, (byte) 0x48, (byte) 0xe2, (byte) 0xe5,
            (byte) 0x5c, (byte) 0xd5, (byte) 0x83, (byte) 0x37, (byte) 0xd9, (byte) 0x09,
            (byte) 0x71, (byte) 0xbc, (byte) 0x54, (byte) 0x25, (byte) 0xca, (byte) 0x2e,
            (byte) 0xdb, (byte) 0x36, (byte) 0x39, (byte) 0xcc, (byte) 0x3a, (byte) 0x81,
            (byte) 0x95, (byte) 0x9e, (byte) 0xf4, (byte) 0x01, (byte) 0xa7, (byte) 0xc0,
            (byte) 0x20, (byte) 0xce, (byte) 0x70, (byte) 0x55, (byte) 0x2c, (byte) 0xe0,
            (byte) 0x93, (byte) 0x72, (byte) 0xa6, (byte) 0x25, (byte) 0xda, (byte) 0x64,
            (byte) 0x19, (byte) 0x18, (byte) 0xd2, (byte) 0x31, (byte) 0xe2, (byte) 0x7c,
            (byte) 0xf2, (byte) 0x30, (byte) 0x9e, (byte) 0x8d, (byte) 0xc6, (byte) 0x14,
            (byte) 0x8a, (byte) 0x38, (byte) 0xf0, (byte) 0x94, (byte) 0xeb, (byte) 0xf4,
            (byte) 0x64, (byte) 0x92, (byte) 0x3d, (byte) 0x67, (byte) 0xa6, (byte) 0x2c,
            (byte) 0x52, (byte) 0xfc, (byte) 0x60, (byte) 0xca, (byte) 0x2a, (byte) 0xcf,
            (byte) 0x24, (byte) 0xd5, (byte) 0x42, (byte) 0x5f, (byte) 0xc7, (byte) 0x9f,
            (byte) 0xf3, (byte) 0xb4, (byte) 0xdf, (byte) 0x76, (byte) 0x6e, (byte) 0x53,
            (byte) 0xa1, (byte) 0x7b, (byte) 0xae, (byte) 0xa5, (byte) 0x84, (byte) 0x1f,
            (byte) 0xfa, (byte) 0xc0, (byte) 0xb4, (byte) 0x6c, (byte) 0xc9, (byte) 0x02,
            (byte) 0x81, (byte) 0xc1, (byte) 0x00, (byte) 0xf3, (byte) 0x17, (byte) 0xd9,
            (byte) 0x48, (byte) 0x17, (byte) 0x87, (byte) 0x84, (byte) 0x16, (byte) 0xea,
            (byte) 0x2d, (byte) 0x31, (byte) 0x1b, (byte) 0xce, (byte) 0xec, (byte) 0xaf,
            (byte) 0xdc, (byte) 0x6b, (byte) 0xaf, (byte) 0xc8, (byte) 0xf1, (byte) 0x40,
            (byte) 0xa7, (byte) 0x4f, (byte) 0xef, (byte) 0x48, (byte) 0x08, (byte) 0x5e,
            (byte) 0x9a, (byte) 0xd1, (byte) 0xc0, (byte) 0xb1, (byte) 0xfe, (byte) 0xe7,
            (byte) 0x03, (byte) 0xd5, (byte) 0x96, (byte) 0x01, (byte) 0xe8, (byte) 0x40,
            (byte) 0xca, (byte) 0x78, (byte) 0xcb, (byte) 0xb3, (byte) 0x28, (byte) 0x1a,
            (byte) 0xf0, (byte) 0xe5, (byte) 0xf6, (byte) 0x46, (byte) 0xef, (byte) 0xcd,
            (byte) 0x1a, (byte) 0x0f, (byte) 0x13, (byte) 0x2d, (byte) 0x38, (byte) 0xf8,
            (byte) 0xf7, (byte) 0x88, (byte) 0x21, (byte) 0x15, (byte) 0xce, (byte) 0x48,
            (byte) 0xf4, (byte) 0x92, (byte) 0x7e, (byte) 0x9b, (byte) 0x2e, (byte) 0x2f,
            (byte) 0x22, (byte) 0x3e, (byte) 0x5c, (byte) 0x67, (byte) 0xd7, (byte) 0x58,
            (byte) 0xf6, (byte) 0xef, (byte) 0x1f, (byte) 0xb4, (byte) 0x04, (byte) 0xc7,
            (byte) 0xfd, (byte) 0x8c, (byte) 0x4e, (byte) 0x27, (byte) 0x9e, (byte) 0xb9,
            (byte) 0xef, (byte) 0x0f, (byte) 0xf7, (byte) 0x4a, (byte) 0xc2, (byte) 0xf4,
            (byte) 0x64, (byte) 0x6b, (byte) 0xe0, (byte) 0xfb, (byte) 0xe3, (byte) 0x45,
            (byte) 0xd5, (byte) 0x37, (byte) 0xa0, (byte) 0x2a, (byte) 0xc6, (byte) 0xf3,
            (byte) 0xf6, (byte) 0xcc, (byte) 0xb5, (byte) 0x94, (byte) 0xbf, (byte) 0x56,
            (byte) 0xa0, (byte) 0x61, (byte) 0x36, (byte) 0x88, (byte) 0x35, (byte) 0xd5,
            (byte) 0xa5, (byte) 0xad, (byte) 0x20, (byte) 0x48, (byte) 0xda, (byte) 0x70,
            (byte) 0x35, (byte) 0xd9, (byte) 0x75, (byte) 0x66, (byte) 0xa5, (byte) 0xac,
            (byte) 0x86, (byte) 0x7a, (byte) 0x75, (byte) 0x49, (byte) 0x88, (byte) 0x40,
            (byte) 0xce, (byte) 0xb0, (byte) 0x6f, (byte) 0x57, (byte) 0x15, (byte) 0x54,
            (byte) 0xd3, (byte) 0x2f, (byte) 0x11, (byte) 0x9b, (byte) 0xe3, (byte) 0x87,
            (byte) 0xc8, (byte) 0x8d, (byte) 0x98, (byte) 0xc6, (byte) 0xe0, (byte) 0xbc,
            (byte) 0x85, (byte) 0xb9, (byte) 0x04, (byte) 0x43, (byte) 0xa9, (byte) 0x41,
            (byte) 0xce, (byte) 0x42, (byte) 0x1a, (byte) 0x57, (byte) 0x10, (byte) 0xd8,
            (byte) 0xe4, (byte) 0x6a, (byte) 0x51, (byte) 0x10, (byte) 0x0a, (byte) 0xec,
            (byte) 0xe4, (byte) 0x57, (byte) 0xc7, (byte) 0xee, (byte) 0xe9, (byte) 0xd6,
            (byte) 0xcb, (byte) 0x3e, (byte) 0xba, (byte) 0xfa, (byte) 0xe9, (byte) 0x0e,
            (byte) 0xed, (byte) 0x87, (byte) 0x04, (byte) 0x9a, (byte) 0x48, (byte) 0xba,
            (byte) 0xaf, (byte) 0x08, (byte) 0xf5, (byte) 0x02, (byte) 0x81, (byte) 0xc1,
            (byte) 0x00, (byte) 0xcb, (byte) 0x63, (byte) 0xd6, (byte) 0x54, (byte) 0xb6,
            (byte) 0xf3, (byte) 0xf3, (byte) 0x8c, (byte) 0xf8, (byte) 0xd0, (byte) 0xd2,
            (byte) 0x84, (byte) 0xc1, (byte) 0xf5, (byte) 0x12, (byte) 0xe0, (byte) 0x02,
            (byte) 0x80, (byte) 0x42, (byte) 0x92, (byte) 0x4e, (byte) 0xa4, (byte) 0x5c,
            (byte) 0xa5, (byte) 0x64, (byte) 0xec, (byte) 0xb7, (byte) 0xdc, (byte) 0xe0,
            (byte) 0x2d, (byte) 0x5d, (byte) 0xac, (byte) 0x0e, (byte) 0x24, (byte) 0x48,
            (byte) 0x13, (byte) 0x05, (byte) 0xe8, (byte) 0xff, (byte) 0x96, (byte) 0x93,
            (byte) 0xba, (byte) 0x3c, (byte) 0x88, (byte) 0xcc, (byte) 0x80, (byte) 0xf9,
            (byte) 0xdb, (byte) 0xa8, (byte) 0x4d, (byte) 0x86, (byte) 0x47, (byte) 0xc8,
            (byte) 0xbf, (byte) 0x34, (byte) 0x2d, (byte) 0xda, (byte) 0xb6, (byte) 0x28,
            (byte) 0xf0, (byte) 0x1e, (byte) 0xd2, (byte) 0x46, (byte) 0x0d, (byte) 0x6f,
            (byte) 0x36, (byte) 0x8e, (byte) 0x84, (byte) 0xd8, (byte) 0xaf, (byte) 0xf7,
            (byte) 0x69, (byte) 0x23, (byte) 0x77, (byte) 0xfb, (byte) 0xc5, (byte) 0x04,
            (byte) 0x08, (byte) 0x18, (byte) 0xac, (byte) 0x85, (byte) 0x80, (byte) 0x87,
            (byte) 0x1c, (byte) 0xfe, (byte) 0x8e, (byte) 0x5d, (byte) 0x00, (byte) 0x7f,
            (byte) 0x5b, (byte) 0x33, (byte) 0xf5, (byte) 0xdf, (byte) 0x70, (byte) 0x81,
            (byte) 0xad, (byte) 0x81, (byte) 0xf4, (byte) 0x5a, (byte) 0x37, (byte) 0x8a,
            (byte) 0x79, (byte) 0x09, (byte) 0xc5, (byte) 0x55, (byte) 0xab, (byte) 0x58,
            (byte) 0x7c, (byte) 0x47, (byte) 0xca, (byte) 0xa5, (byte) 0x80, (byte) 0x49,
            (byte) 0x5f, (byte) 0x71, (byte) 0x83, (byte) 0xfb, (byte) 0x3b, (byte) 0x06,
            (byte) 0xec, (byte) 0x75, (byte) 0x23, (byte) 0xc4, (byte) 0x32, (byte) 0xc7,
            (byte) 0x18, (byte) 0xf6, (byte) 0x82, (byte) 0x95, (byte) 0x98, (byte) 0x39,
            (byte) 0xf7, (byte) 0x92, (byte) 0x31, (byte) 0xc0, (byte) 0x89, (byte) 0xba,
            (byte) 0xd4, (byte) 0xd4, (byte) 0x58, (byte) 0x4e, (byte) 0x38, (byte) 0x35,
            (byte) 0x10, (byte) 0xb9, (byte) 0xf1, (byte) 0x27, (byte) 0xdc, (byte) 0xff,
            (byte) 0xc7, (byte) 0xb2, (byte) 0xba, (byte) 0x1f, (byte) 0x27, (byte) 0xaf,
            (byte) 0x99, (byte) 0xd5, (byte) 0xb0, (byte) 0x39, (byte) 0xe7, (byte) 0x43,
            (byte) 0x88, (byte) 0xd3, (byte) 0xce, (byte) 0x38, (byte) 0xc2, (byte) 0x99,
            (byte) 0x43, (byte) 0xfc, (byte) 0x8a, (byte) 0xe3, (byte) 0x60, (byte) 0x0d,
            (byte) 0x0a, (byte) 0xb8, (byte) 0xc4, (byte) 0x29, (byte) 0xca, (byte) 0x0d,
            (byte) 0x30, (byte) 0xaf, (byte) 0xca, (byte) 0xd0, (byte) 0xaa, (byte) 0x67,
            (byte) 0xb1, (byte) 0xdd, (byte) 0xdb, (byte) 0x7a, (byte) 0x11, (byte) 0xad,
            (byte) 0xeb, (byte) 0x02, (byte) 0x81, (byte) 0xc0, (byte) 0x71, (byte) 0xb8,
            (byte) 0xcf, (byte) 0x72, (byte) 0x35, (byte) 0x67, (byte) 0xb5, (byte) 0x38,
            (byte) 0x8f, (byte) 0x16, (byte) 0xd3, (byte) 0x29, (byte) 0x82, (byte) 0x35,
            (byte) 0x21, (byte) 0xd4, (byte) 0x49, (byte) 0x20, (byte) 0x74, (byte) 0x2d,
            (byte) 0xc0, (byte) 0xa4, (byte) 0x44, (byte) 0xf5, (byte) 0xd8, (byte) 0xc9,
            (byte) 0xe9, (byte) 0x90, (byte) 0x1d, (byte) 0xde, (byte) 0x3a, (byte) 0xa6,
            (byte) 0xd7, (byte) 0xe5, (byte) 0xe8, (byte) 0x4e, (byte) 0x83, (byte) 0xd7,
            (byte) 0xe6, (byte) 0x2f, (byte) 0x92, (byte) 0x31, (byte) 0x21, (byte) 0x3f,
            (byte) 0xfa, (byte) 0xd2, (byte) 0x85, (byte) 0x92, (byte) 0x1f, (byte) 0xff,
            (byte) 0x61, (byte) 0x00, (byte) 0xf6, (byte) 0xda, (byte) 0x6e, (byte) 0xc6,
            (byte) 0x7f, (byte) 0x5a, (byte) 0x35, (byte) 0x79, (byte) 0xdc, (byte) 0xdc,
            (byte) 0xa3, (byte) 0x2e, (byte) 0x9f, (byte) 0x35, (byte) 0xd1, (byte) 0x5c,
            (byte) 0xda, (byte) 0xb9, (byte) 0xf7, (byte) 0x58, (byte) 0x7d, (byte) 0x4f,
            (byte) 0xb6, (byte) 0x13, (byte) 0xd7, (byte) 0x2c, (byte) 0x0a, (byte) 0xa8,
            (byte) 0x4d, (byte) 0xf2, (byte) 0xe4, (byte) 0x67, (byte) 0x4f, (byte) 0x8b,
            (byte) 0xa6, (byte) 0xca, (byte) 0x1a, (byte) 0xbb, (byte) 0x02, (byte) 0x63,
            (byte) 0x8f, (byte) 0xb7, (byte) 0x46, (byte) 0xec, (byte) 0x7a, (byte) 0x8a,
            (byte) 0x09, (byte) 0x0a, (byte) 0x45, (byte) 0x3a, (byte) 0x8d, (byte) 0xa8,
            (byte) 0x83, (byte) 0x4b, (byte) 0x0a, (byte) 0xdb, (byte) 0x4b, (byte) 0x99,
            (byte) 0xf3, (byte) 0x69, (byte) 0x95, (byte) 0xf0, (byte) 0xcf, (byte) 0xe9,
            (byte) 0xf7, (byte) 0x67, (byte) 0xc9, (byte) 0x45, (byte) 0x18, (byte) 0x2f,
            (byte) 0xf0, (byte) 0x5c, (byte) 0x90, (byte) 0xbd, (byte) 0xa6, (byte) 0x66,
            (byte) 0x8c, (byte) 0xfe, (byte) 0x60, (byte) 0x5d, (byte) 0x6c, (byte) 0x27,
            (byte) 0xec, (byte) 0xc1, (byte) 0x84, (byte) 0xb2, (byte) 0xa1, (byte) 0x97,
            (byte) 0x9e, (byte) 0x16, (byte) 0x29, (byte) 0xa7, (byte) 0xe0, (byte) 0x38,
            (byte) 0xa2, (byte) 0x36, (byte) 0x05, (byte) 0x5f, (byte) 0xda, (byte) 0x72,
            (byte) 0x1a, (byte) 0x5f, (byte) 0xa8, (byte) 0x7d, (byte) 0x41, (byte) 0x35,
            (byte) 0xf6, (byte) 0x4e, (byte) 0x0a, (byte) 0x88, (byte) 0x8e, (byte) 0x00,
            (byte) 0x98, (byte) 0xa6, (byte) 0xca, (byte) 0xc1, (byte) 0xdf, (byte) 0x72,
            (byte) 0x6c, (byte) 0xfe, (byte) 0x29, (byte) 0xbe, (byte) 0xa3, (byte) 0x9b,
            (byte) 0x0b, (byte) 0x5c, (byte) 0x0b, (byte) 0x9d, (byte) 0xa7, (byte) 0x71,
            (byte) 0xce, (byte) 0x04, (byte) 0xfa, (byte) 0xac, (byte) 0x01, (byte) 0x8d,
            (byte) 0x52, (byte) 0xa0, (byte) 0x3d, (byte) 0xdd, (byte) 0x02, (byte) 0x81,
            (byte) 0xc1, (byte) 0x00, (byte) 0xc1, (byte) 0xc0, (byte) 0x2e, (byte) 0xa9,
            (byte) 0xee, (byte) 0xca, (byte) 0xff, (byte) 0xe4, (byte) 0xf8, (byte) 0x15,
            (byte) 0xfd, (byte) 0xa5, (byte) 0x68, (byte) 0x1b, (byte) 0x2d, (byte) 0x4a,
            (byte) 0xe6, (byte) 0x37, (byte) 0x06, (byte) 0xb3, (byte) 0xd7, (byte) 0x64,
            (byte) 0xad, (byte) 0xb9, (byte) 0x05, (byte) 0x26, (byte) 0x97, (byte) 0x94,
            (byte) 0x3a, (byte) 0x9e, (byte) 0x1c, (byte) 0xd0, (byte) 0xcd, (byte) 0x7b,
            (byte) 0xf4, (byte) 0x88, (byte) 0xe2, (byte) 0xa5, (byte) 0x6d, (byte) 0xed,
            (byte) 0x24, (byte) 0x77, (byte) 0x52, (byte) 0x39, (byte) 0x43, (byte) 0x0f,
            (byte) 0x4e, (byte) 0x75, (byte) 0xd8, (byte) 0xa3, (byte) 0x59, (byte) 0x5a,
            (byte) 0xc2, (byte) 0xba, (byte) 0x9a, (byte) 0x5b, (byte) 0x60, (byte) 0x31,
            (byte) 0x0d, (byte) 0x58, (byte) 0x89, (byte) 0x13, (byte) 0xe8, (byte) 0x95,
            (byte) 0xdd, (byte) 0xae, (byte) 0xcc, (byte) 0x1f, (byte) 0x73, (byte) 0x48,
            (byte) 0x55, (byte) 0xd8, (byte) 0xfb, (byte) 0x67, (byte) 0xce, (byte) 0x18,
            (byte) 0x85, (byte) 0x59, (byte) 0xad, (byte) 0x1f, (byte) 0x93, (byte) 0xe1,
            (byte) 0xb7, (byte) 0x54, (byte) 0x80, (byte) 0x8e, (byte) 0x5f, (byte) 0xbc,
            (byte) 0x1c, (byte) 0x96, (byte) 0x66, (byte) 0x2e, (byte) 0x40, (byte) 0x17,
            (byte) 0x2e, (byte) 0x01, (byte) 0x7a, (byte) 0x7d, (byte) 0xaa, (byte) 0xff,
            (byte) 0xa3, (byte) 0xd2, (byte) 0xdf, (byte) 0xe2, (byte) 0xf3, (byte) 0x54,
            (byte) 0x51, (byte) 0xeb, (byte) 0xba, (byte) 0x7c, (byte) 0x2a, (byte) 0x22,
            (byte) 0xc6, (byte) 0x42, (byte) 0xbc, (byte) 0xa1, (byte) 0x6c, (byte) 0xcf,
            (byte) 0x73, (byte) 0x2e, (byte) 0x07, (byte) 0xfc, (byte) 0xf5, (byte) 0x67,
            (byte) 0x25, (byte) 0xd0, (byte) 0xfa, (byte) 0xeb, (byte) 0xb4, (byte) 0xd4,
            (byte) 0x19, (byte) 0xcc, (byte) 0x64, (byte) 0xa1, (byte) 0x2e, (byte) 0x78,
            (byte) 0x45, (byte) 0xd9, (byte) 0x7f, (byte) 0x1b, (byte) 0x4c, (byte) 0x10,
            (byte) 0x31, (byte) 0x44, (byte) 0xe8, (byte) 0xcc, (byte) 0xf9, (byte) 0x1b,
            (byte) 0x87, (byte) 0x31, (byte) 0xd6, (byte) 0x69, (byte) 0x85, (byte) 0x4a,
            (byte) 0x49, (byte) 0xf6, (byte) 0xb2, (byte) 0xe0, (byte) 0xb8, (byte) 0x98,
            (byte) 0x3c, (byte) 0xf6, (byte) 0x78, (byte) 0x46, (byte) 0xc8, (byte) 0x3d,
            (byte) 0x60, (byte) 0xc1, (byte) 0xaa, (byte) 0x2f, (byte) 0x28, (byte) 0xa1,
            (byte) 0x14, (byte) 0x6b, (byte) 0x75, (byte) 0x4d, (byte) 0xb1, (byte) 0x3d,
            (byte) 0x80, (byte) 0x49, (byte) 0x33, (byte) 0xfd, (byte) 0x71, (byte) 0xc0,
            (byte) 0x13, (byte) 0x1e, (byte) 0x16, (byte) 0x69, (byte) 0x80, (byte) 0xa4,
            (byte) 0x9c, (byte) 0xd7, (byte) 0x02, (byte) 0x81, (byte) 0xc1, (byte) 0x00,
            (byte) 0x8c, (byte) 0x33, (byte) 0x2d, (byte) 0xd9, (byte) 0xf3, (byte) 0x42,
            (byte) 0x4d, (byte) 0xca, (byte) 0x5e, (byte) 0x60, (byte) 0x14, (byte) 0x10,
            (byte) 0xf6, (byte) 0xf3, (byte) 0x71, (byte) 0x15, (byte) 0x88, (byte) 0x54,
            (byte) 0x84, (byte) 0x21, (byte) 0x04, (byte) 0xb1, (byte) 0xaf, (byte) 0x02,
            (byte) 0x11, (byte) 0x7f, (byte) 0x42, (byte) 0x3e, (byte) 0x86, (byte) 0xcb,
            (byte) 0x6c, (byte) 0xf5, (byte) 0x57, (byte) 0x78, (byte) 0x4a, (byte) 0x03,
            (byte) 0x9b, (byte) 0x80, (byte) 0xc2, (byte) 0x04, (byte) 0x3a, (byte) 0x6b,
            (byte) 0xb3, (byte) 0x30, (byte) 0x31, (byte) 0x7e, (byte) 0xc3, (byte) 0x89,
            (byte) 0x09, (byte) 0x4e, (byte) 0x86, (byte) 0x59, (byte) 0x41, (byte) 0xb5,
            (byte) 0xae, (byte) 0xd5, (byte) 0xc6, (byte) 0x38, (byte) 0xbc, (byte) 0xd7,
            (byte) 0xd7, (byte) 0x8e, (byte) 0xa3, (byte) 0x1a, (byte) 0xde, (byte) 0x32,
            (byte) 0xad, (byte) 0x8d, (byte) 0x15, (byte) 0x81, (byte) 0xfe, (byte) 0xac,
            (byte) 0xbd, (byte) 0xd0, (byte) 0xca, (byte) 0xbc, (byte) 0xd8, (byte) 0x6a,
            (byte) 0xe1, (byte) 0xfe, (byte) 0xda, (byte) 0xc4, (byte) 0xd8, (byte) 0x62,
            (byte) 0x71, (byte) 0x20, (byte) 0xa3, (byte) 0xd3, (byte) 0x06, (byte) 0x11,
            (byte) 0xa9, (byte) 0x53, (byte) 0x7a, (byte) 0x44, (byte) 0x89, (byte) 0x3d,
            (byte) 0x28, (byte) 0x5e, (byte) 0x7d, (byte) 0xf0, (byte) 0x60, (byte) 0xeb,
            (byte) 0xb5, (byte) 0xdf, (byte) 0xed, (byte) 0x4f, (byte) 0x6d, (byte) 0x05,
            (byte) 0x59, (byte) 0x06, (byte) 0xb0, (byte) 0x62, (byte) 0x50, (byte) 0x1c,
            (byte) 0xb7, (byte) 0x2c, (byte) 0x44, (byte) 0xa4, (byte) 0x49, (byte) 0xf8,
            (byte) 0x4f, (byte) 0x4b, (byte) 0xab, (byte) 0x71, (byte) 0x5b, (byte) 0xcb,
            (byte) 0x31, (byte) 0x10, (byte) 0x41, (byte) 0xe0, (byte) 0x1a, (byte) 0x15,
            (byte) 0xdc, (byte) 0x4c, (byte) 0x5d, (byte) 0x4f, (byte) 0x62, (byte) 0x83,
            (byte) 0xa4, (byte) 0x80, (byte) 0x06, (byte) 0x36, (byte) 0xba, (byte) 0xc9,
            (byte) 0xe2, (byte) 0xa4, (byte) 0x11, (byte) 0x98, (byte) 0x6b, (byte) 0x4c,
            (byte) 0xe9, (byte) 0x90, (byte) 0x55, (byte) 0x18, (byte) 0xde, (byte) 0xe1,
            (byte) 0x42, (byte) 0x38, (byte) 0x28, (byte) 0xa3, (byte) 0x54, (byte) 0x56,
            (byte) 0x31, (byte) 0xaf, (byte) 0x5a, (byte) 0xd6, (byte) 0xf0, (byte) 0x26,
            (byte) 0xe0, (byte) 0x7a, (byte) 0xd9, (byte) 0x6c, (byte) 0x64, (byte) 0xca,
            (byte) 0x5d, (byte) 0x6d, (byte) 0x3d, (byte) 0x9a, (byte) 0xfe, (byte) 0x36,
            (byte) 0x93, (byte) 0x9e, (byte) 0x62, (byte) 0x94, (byte) 0xc6, (byte) 0x07,
            (byte) 0x83, (byte) 0x96, (byte) 0xd6, (byte) 0x27, (byte) 0xa6, (byte) 0xd8
    };
    private static final PrivateKey CLIENT_SUITE_B_RSA3072_KEY =
            loadPrivateKey("RSA", CLIENT_SUITE_B_RSA3072_KEY_DATA);

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
    private static final X509Certificate CLIENT_SUITE_B_ECDSA_CERT =
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
    private static final PrivateKey CLIENT_SUITE_B_ECC_KEY =
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

    private WifiNetworkSuggestion.Builder createBuilderWithCommonParams() {
        return createBuilderWithCommonParams(false);
    }

    private WifiNetworkSuggestion.Builder createBuilderWithCommonParams(boolean isPasspoint) {
        WifiNetworkSuggestion.Builder builder = new WifiNetworkSuggestion.Builder();
        if (!isPasspoint) {
            builder.setSsid(TEST_SSID);
            builder.setBssid(MacAddress.fromString(TEST_BSSID));
            builder.setIsEnhancedOpen(false);
            builder.setIsHiddenSsid(true);
        }
        builder.setPriority(TEST_PRIORITY);
        builder.setIsAppInteractionRequired(true);
        builder.setIsUserInteractionRequired(true);
        builder.setIsMetered(true);
        builder.setCarrierId(TelephonyManager.UNKNOWN_CARRIER_ID);
        builder.setCredentialSharedWithUser(true);
        builder.setIsInitialAutojoinEnabled(true);
        builder.setUntrusted(false);
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
            builder.setOemPaid(false);
            builder.setOemPrivate(false);
            builder.setSubscriptionId(TEST_SUB_ID);
        }
        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            builder.setPriorityGroup(TEST_PRIORITY_GROUP);
        }
        return builder;
    }

    private void validateCommonParams(WifiNetworkSuggestion suggestion) {
        validateCommonParams(suggestion, false);
    }

    private void validateCommonParams(WifiNetworkSuggestion suggestion, boolean isPasspoint) {
        assertNotNull(suggestion);
        assertNotNull(suggestion.getWifiConfiguration());
        if (!isPasspoint) {
            assertEquals(TEST_SSID, suggestion.getSsid());
            assertEquals(TEST_BSSID, suggestion.getBssid().toString());
            assertFalse(suggestion.isEnhancedOpen());
            assertTrue(suggestion.isHiddenSsid());
        }
        assertEquals(TEST_PRIORITY, suggestion.getPriority());
        assertTrue(suggestion.isAppInteractionRequired());
        assertTrue(suggestion.isUserInteractionRequired());
        assertTrue(suggestion.isMetered());
        assertTrue(suggestion.isCredentialSharedWithUser());
        assertTrue(suggestion.isInitialAutojoinEnabled());
        assertFalse(suggestion.isUntrusted());
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
            assertFalse(suggestion.isOemPaid());
            assertFalse(suggestion.isOemPrivate());
            assertEquals(TEST_SUB_ID, suggestion.getSubscriptionId());
        }
        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            assertEquals(TEST_PRIORITY_GROUP, suggestion.getPriorityGroup());
            assertEquals(TelephonyManager.UNKNOWN_CARRIER_ID, suggestion.getCarrierId());
        }
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa2Passphrase() throws Exception {
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                .setWpa2Passphrase(TEST_PASSPHRASE)
                .build();
        validateCommonParams(suggestion);
        assertEquals(TEST_PASSPHRASE, suggestion.getPassphrase());
        assertNull(suggestion.getEnterpriseConfig());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa3Passphrase() throws Exception {
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3Passphrase(TEST_PASSPHRASE)
                        .build();
        validateCommonParams(suggestion);
        assertEquals(TEST_PASSPHRASE, suggestion.getPassphrase());
        assertNull(suggestion.getEnterpriseConfig());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testBuilderWithWpa3SaeH2eOnlyMode() throws Exception {
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3Passphrase(TEST_PASSPHRASE)
                        .setIsWpa3SaeH2eOnlyModeEnabled(true)
                        .build();
        validateCommonParams(suggestion);
        assertEquals(TEST_PASSPHRASE, suggestion.getPassphrase());
        assertNull(suggestion.getEnterpriseConfig());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWapiPassphrase() throws Exception {
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWapiPassphrase(TEST_PASSPHRASE)
                        .build();
        validateCommonParams(suggestion);
        assertEquals(TEST_PASSPHRASE, suggestion.getPassphrase());
        assertNull(suggestion.getEnterpriseConfig());
        assertNull(suggestion.getPasspointConfig());
    }

    private static WifiEnterpriseConfig createEnterpriseConfig() {
        WifiEnterpriseConfig config = new WifiEnterpriseConfig();
        config.setEapMethod(AKA);
        return config;
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa2Enterprise() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = createEnterpriseConfig();
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa2EnterpriseConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa3Enterprise() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = createEnterpriseConfig();
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3EnterpriseConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa3EnterpriseWithStandardApi() throws Exception {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            // Skip the test if wifi module version is older than S.
            return;
        }
        WifiEnterpriseConfig enterpriseConfig = createEnterpriseConfig();
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3EnterpriseStandardModeConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa3EnterpriseWithSuiteBRsaCerts() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        enterpriseConfig.setCaCertificate(CA_SUITE_B_RSA3072_CERT);
        enterpriseConfig.setClientKeyEntryWithCertificateChain(CLIENT_SUITE_B_RSA3072_KEY,
                new X509Certificate[] {CLIENT_SUITE_B_RSA3072_CERT});
        enterpriseConfig.setAltSubjectMatch("domain.com");
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3EnterpriseConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa3EnterpriseWithSuiteBEccCerts() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        enterpriseConfig.setCaCertificate(CA_SUITE_B_ECDSA_CERT);
        enterpriseConfig.setClientKeyEntryWithCertificateChain(CLIENT_SUITE_B_ECC_KEY,
                new X509Certificate[] {CLIENT_SUITE_B_ECDSA_CERT});
        enterpriseConfig.setAltSubjectMatch("domain.com");
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3EnterpriseConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa3Enterprise192bitWithSuiteBRsaCerts() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        enterpriseConfig.setCaCertificate(CA_SUITE_B_RSA3072_CERT);
        enterpriseConfig.setClientKeyEntryWithCertificateChain(CLIENT_SUITE_B_RSA3072_KEY,
                new X509Certificate[] {CLIENT_SUITE_B_RSA3072_CERT});
        enterpriseConfig.setAltSubjectMatch("domain.com");
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3EnterpriseConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWpa3Enterprise192bitWithSuiteBEccCerts() throws Exception {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            // Skip the test if wifi module version is older than S.
            return;
        }
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        enterpriseConfig.setCaCertificate(CA_SUITE_B_ECDSA_CERT);
        enterpriseConfig.setClientKeyEntryWithCertificateChain(CLIENT_SUITE_B_ECC_KEY,
                new X509Certificate[] {CLIENT_SUITE_B_ECDSA_CERT});
        enterpriseConfig.setAltSubjectMatch("domain.com");
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3Enterprise192BitModeConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithWapiEnterprise() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WAPI_CERT);
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWapiEnterpriseConfig(enterpriseConfig)
                        .build();
        validateCommonParams(suggestion);
        assertNull(suggestion.getPassphrase());
        assertNotNull(suggestion.getEnterpriseConfig());
        assertEquals(enterpriseConfig.getEapMethod(),
                suggestion.getEnterpriseConfig().getEapMethod());
        assertNull(suggestion.getPasspointConfig());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class, with SubscriptionGroup
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testBuilderWithSubscriptionGroup() throws Exception {
        WifiNetworkSuggestion suggestion =
                new WifiNetworkSuggestion.Builder()
                        .setSsid(TEST_SSID)
                        .setSubscriptionGroup(GROUP_UUID)
                        .build();
        assertEquals(GROUP_UUID, suggestion.getSubscriptionGroup());
    }

    /**
     * Helper function for creating a {@link PasspointConfiguration} for testing.
     *
     * @return {@link PasspointConfiguration}
     */
    private static PasspointConfiguration createPasspointConfig() {
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("fqdn");
        homeSp.setFriendlyName("friendly name");
        homeSp.setRoamingConsortiumOis(new long[] {0x55, 0x66});
        Credential cred = new Credential();
        cred.setRealm("realm");
        cred.setUserCredential(null);
        cred.setCertCredential(null);
        cred.setSimCredential(new Credential.SimCredential());
        cred.getSimCredential().setImsi("1234*");
        cred.getSimCredential().setEapType(23); // EAP-AKA
        cred.setCaCertificate(null);
        cred.setClientCertificateChain(null);
        cred.setClientPrivateKey(null);
        PasspointConfiguration config = new PasspointConfiguration();
        config.setHomeSp(homeSp);
        config.setCredential(cred);
        return config;
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @Test
    public void testBuilderWithPasspointConfig() throws Exception {
        PasspointConfiguration passpointConfig = createPasspointConfig();
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams(true)
                        .setPasspointConfig(passpointConfig)
                        .build();
        validateCommonParams(suggestion, true);
        assertNull(suggestion.getPassphrase());
        assertNull(suggestion.getEnterpriseConfig());
        assertEquals(passpointConfig.getUniqueId(), suggestion.getPasspointConfig().getUniqueId());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testBuilderWithCarrierMergedNetwork() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        enterpriseConfig.setCaCertificate(CA_SUITE_B_ECDSA_CERT);
        enterpriseConfig.setClientKeyEntryWithCertificateChain(CLIENT_SUITE_B_ECC_KEY,
                new X509Certificate[] {CLIENT_SUITE_B_ECDSA_CERT});
        enterpriseConfig.setAltSubjectMatch("domain.com");
        WifiNetworkSuggestion suggestion =
                createBuilderWithCommonParams()
                        .setWpa3Enterprise192BitModeConfig(enterpriseConfig)
                        .setCarrierMerged(true)
                        .build();
        validateCommonParams(suggestion);
        assertTrue(suggestion.isCarrierMerged());
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class with non enterprise
     * network will fail.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testBuilderWithCarrierMergedNetworkWithNonEnterpriseNetwork() throws Exception {
        try {
            createBuilderWithCommonParams()
                    .setWpa2Passphrase(TEST_PASSPHRASE)
                    .setCarrierMerged(true)
                    .build();
        } catch (IllegalStateException e) {
            return;
        }
        fail("Did not receive expected IllegalStateException when tried to build a carrier merged "
                + "network suggestion with non enterprise config");
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class with unmetered network
     * will fail.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testBuilderWithCarrierMergedNetworkWithUnmeteredNetwork() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        enterpriseConfig.setCaCertificate(CA_SUITE_B_ECDSA_CERT);
        enterpriseConfig.setClientKeyEntryWithCertificateChain(CLIENT_SUITE_B_ECC_KEY,
                new X509Certificate[] {CLIENT_SUITE_B_ECDSA_CERT});
        enterpriseConfig.setAltSubjectMatch("domain.com");
        try {
            createBuilderWithCommonParams()
                    .setWpa3Enterprise192BitModeConfig(enterpriseConfig)
                    .setCarrierMerged(true)
                    .setIsMetered(false)
                    .build();
        } catch (IllegalStateException e) {
            return;
        }
        fail("Did not receive expected IllegalStateException when tried to build a carrier merged "
                + "network suggestion with unmetered config");
    }

    /**
     * Tests {@link android.net.wifi.WifiNetworkSuggestion.Builder} class with non-unicode ssid
     */
    @Test
    public void testBuilderWithNonUnicodeSsid() {
        byte[] ssid = "服務集識別碼".getBytes(Charset.forName("GBK"));
        WifiNetworkSuggestion suggestion = new WifiNetworkSuggestion.Builder()
                .setWifiSsid(WifiSsid.fromBytes(ssid))
                .build();
        assertArrayEquals(ssid, suggestion.getWifiSsid().getBytes());
        assertNull(suggestion.getSsid());

        // If WifiSsid is empty, will throw an exception.
        assertThrows(IllegalArgumentException.class,
                () -> new WifiNetworkSuggestion.Builder().setWifiSsid(WifiSsid.fromBytes(null)));
    }

    /**
     * Connect to a network using suggestion API then remove with
     * {@link WifiManager#ACTION_REMOVE_SUGGESTION_DISCONNECT}
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testConnectToSuggestionThenRemoveWithImmediateDisconnect() throws Exception {
        assertNotNull(sTestNetwork);
        WifiNetworkSuggestion suggestion =
                TestHelper.createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
                        sTestNetwork)
                        .build();
        sNsNetworkCallback = sTestHelper.testConnectionFlowWithSuggestion(
                sTestNetwork, suggestion, mExecutorService,
                Set.of()/* restrictedNetworkCapability */, false/* restrictedNetwork */);
        TestHelper.TestNetworkCallback callback = (TestHelper.TestNetworkCallback)
                sNsNetworkCallback;
        while (callback.waitForAnyCallback(DURATION_NETWORK_UPDATE));
        sWifiManager.removeNetworkSuggestions(List.of(suggestion),
                WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT);
        callback.waitForAnyCallback(DURATION_NETWORK_DISCONNECT_MILLIS);
        assertTrue(callback.onLostCalled);
    }

    /**
     * Connect to a network using suggestion API, then remove with
     * {@link WifiManager#ACTION_REMOVE_SUGGESTION_LINGER}
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testConnectToSuggestionThenRemoveWithLingering() throws Exception {
        assertNotNull(sTestNetwork);
        WifiNetworkSuggestion suggestion =
                TestHelper.createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
                                sTestNetwork)
                        .build();
        sNsNetworkCallback = sTestHelper.testConnectionFlowWithSuggestion(
                sTestNetwork, suggestion, mExecutorService,
                Set.of()/* restrictedNetworkCapability */, false/* restrictedNetwork */);
        TestHelper.TestNetworkCallback callback = (TestHelper.TestNetworkCallback)
                sNsNetworkCallback;
        while (callback.waitForAnyCallback(DURATION_NETWORK_UPDATE));
        sWifiManager.removeNetworkSuggestions(List.of(suggestion),
                WifiManager.ACTION_REMOVE_SUGGESTION_LINGER);
        callback.waitForAnyCallback(DURATION_NETWORK_DISCONNECT_MILLIS);
        // Should not disconnect immediately
        assertFalse(callback.onLostCalled);
        // After linger time out, should disconnect.
        Thread.sleep(DURATION_NETWORK_LINGER_MILLIS);
        assertTrue(callback.onLostCalled);
    }

    /**
     * Connect to a restricted network using suggestion API.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testConnectToRestrictedSuggestion() throws Exception {
        assertNotNull(sTestNetwork);
        WifiNetworkSuggestion suggestion =
                TestHelper.createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
                        sTestNetwork)
                        .setRestricted(true)
                        .build();
        assertTrue(suggestion.isRestricted());
        sNsNetworkCallback = sTestHelper.testConnectionFlowWithSuggestion(
                sTestNetwork, suggestion, mExecutorService,
                Set.of()/* restrictedNetworkCapability */, true);
    }

    /**
     * Connect to a network using restricted suggestion API.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testConnectToOemPaidSuggestion() throws Exception {
        assertNotNull(sTestNetwork);
        WifiNetworkSuggestion suggestion =
                TestHelper.createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
                        sTestNetwork)
                        .setOemPaid(true)
                        .build();
        sNsNetworkCallback = sTestHelper.testConnectionFlowWithSuggestion(
                sTestNetwork, suggestion, mExecutorService, Set.of(NET_CAPABILITY_OEM_PAID), false);
    }

    /**
     * Connect to a network using restricted suggestion API.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testConnectToOemPaidAndOemPrivateSuggestion() throws Exception {
        assertNotNull(sTestNetwork);
        WifiNetworkSuggestion suggestion =
                TestHelper.createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
                        sTestNetwork)
                        .setOemPaid(true)
                        .setOemPrivate(true)
                        .build();
        sNsNetworkCallback = sTestHelper.testConnectionFlowWithSuggestion(
                sTestNetwork, suggestion, mExecutorService,
                Set.of(NET_CAPABILITY_OEM_PAID, NET_CAPABILITY_OEM_PRIVATE), false);
    }

    /**
     * Connect to a network using restricted suggestion API.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testConnectToOemPrivateSuggestion() throws Exception {
        assertNotNull(sTestNetwork);
        WifiNetworkSuggestion suggestion =
                TestHelper.createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
                        sTestNetwork)
                        .setOemPrivate(true)
                        .build();
        sNsNetworkCallback = sTestHelper.testConnectionFlowWithSuggestion(
                sTestNetwork, suggestion, mExecutorService, Set.of(NET_CAPABILITY_OEM_PRIVATE),
                false);
    }

    /**
     * Simulate connection failure to a network using restricted suggestion API & different net
     * capability (need corresponding net capability requested for platform to connect).
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testConnectToOemPaidSuggestionFailure() throws Exception {
        assertNotNull(sTestNetwork);
        WifiNetworkSuggestion suggestion =
                TestHelper.createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
                        sTestNetwork)
                        .setOemPaid(true)
                        .build();
        sNsNetworkCallback = sTestHelper.testConnectionFailureFlowWithSuggestion(
                sTestNetwork, suggestion, mExecutorService, Set.of(NET_CAPABILITY_OEM_PRIVATE));
    }

    /**
     * Simulate connection failure to a network using restricted suggestion API & different net
     * capability (need corresponding net capability requested for platform to connect).
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testConnectToOemPrivateSuggestionFailure() throws Exception {
        assertNotNull(sTestNetwork);
        WifiNetworkSuggestion suggestion =
                TestHelper.createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
                        sTestNetwork)
                        .setOemPrivate(true)
                        .build();
        sNsNetworkCallback = sTestHelper.testConnectionFailureFlowWithSuggestion(
                sTestNetwork, suggestion, mExecutorService, Set.of(NET_CAPABILITY_OEM_PAID));
    }

    /**
     * Simulate connection failure to a restricted network using suggestion API & restricted net
     * capability (need corresponding restricted bit set in suggestion for platform to connect).
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testConnectSuggestionFailureWithOemPaidNetCapability() throws Exception {
        assertNotNull(sTestNetwork);
        WifiNetworkSuggestion suggestion =
                TestHelper.createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
                        sTestNetwork)
                        .build();
        sNsNetworkCallback = sTestHelper.testConnectionFailureFlowWithSuggestion(
                sTestNetwork, suggestion, mExecutorService, Set.of(NET_CAPABILITY_OEM_PAID));
    }

    /**
     * Simulate connection failure to a restricted network using suggestion API & restricted net
     * capability (need corresponding restricted bit set in suggestion for platform to connect).
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testConnectSuggestionFailureWithOemPrivateNetCapability() throws Exception {
        assertNotNull(sTestNetwork);
        WifiNetworkSuggestion suggestion =
                TestHelper.createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
                        sTestNetwork)
                        .build();
        sNsNetworkCallback = sTestHelper.testConnectionFailureFlowWithSuggestion(
                sTestNetwork, suggestion, mExecutorService, Set.of(NET_CAPABILITY_OEM_PRIVATE));
    }

    /**
     * Tests {@link WifiNetworkSuggestion.Builder#setWifi7Enabled(boolean)}. Validate default
     * behavior.
     */
    @ApiTest(apis = {"android.net.wifi.WifiNetworkSuggestion.Builder#setWifi7Enabled",
            "android.net.wifi.WifiNetworkSuggestion#isWifi7Enabled"})
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testWifi7EnabledDefaultBehavior() throws Exception {
        // Make sure device supports Wi-Fi 7
        assumeTrue(sWifiManager.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE));
        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.getPrivilegedConfiguredNetworks());
        assertWithMessage("Need at least one saved network").that(
                savedNetworks.isEmpty()).isFalse();
        WifiConfiguration wifi7Network = TestHelper.findFirstAvailableSavedNetwork(sWifiManager,
                savedNetworks, TestHelper.AP_CAPABILITY_BIT_WIFI7);
        // TODO: b/322011012
        assumeTrue("Unable to locate Wi-Fi 7 networks in range.\n", wifi7Network != null);
        WifiNetworkSuggestion suggestion =
                TestHelper.createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
                        wifi7Network).build();
        assertTrue(suggestion.isWifi7Enabled());
        // Connect with default behavior: Enable Wi-Fi 7
        sNsNetworkCallback = sTestHelper.testConnectionFlowWithSuggestion(wifi7Network, suggestion,
                mExecutorService, Set.of(), false);
        // Check new connection is Wi-Fi 7
        assertTrue(sWifiManager.getConnectionInfo().getWifiStandard()
                == ScanResult.WIFI_STANDARD_11BE);
    }

    /**
     * Tests {@link WifiNetworkSuggestion.Builder#setWifi7Enabled(boolean)}. Validate disable
     * Wi-Fi 7.
     */
    @ApiTest(apis = {"android.net.wifi.WifiNetworkSuggestion.Builder#setWifi7Enabled",
            "android.net.wifi.WifiNetworkSuggestion#isWifi7Enabled"})
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testWifi7Disabled() throws Exception {
        // Make sure device supports Wi-Fi 7
        assumeTrue(sWifiManager.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE));
        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.getPrivilegedConfiguredNetworks());
        assertWithMessage("Need at least one saved network").that(
                savedNetworks.isEmpty()).isFalse();
        WifiConfiguration wifi7Network = TestHelper.findFirstAvailableSavedNetwork(sWifiManager,
                savedNetworks, TestHelper.AP_CAPABILITY_BIT_WIFI7);
        // TODO: b/322011012
        assumeTrue("Unable to locate Wi-Fi 7 networks in range.\n", wifi7Network != null);
        WifiNetworkSuggestion suggestion =
                TestHelper.createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
                        wifi7Network).setWifi7Enabled(false).build();
        assertFalse(suggestion.isWifi7Enabled());
        // Disable Wi-Fi 7 and connect
        sNsNetworkCallback = sTestHelper.testConnectionFlowWithSuggestion(wifi7Network, suggestion,
                mExecutorService, Set.of(), false);
        // Check new connection is not Wi-Fi 7
        assertTrue(sWifiManager.getConnectionInfo().getWifiStandard()
                != ScanResult.WIFI_STANDARD_11BE);
    }

    /**
     * Tests {@link WifiNetworkSuggestion.Builder#setWifi7Enabled(boolean)}. Validate enable
     * Wi-Fi 7.
     */
    @ApiTest(apis = {"android.net.wifi.WifiNetworkSuggestion.Builder#setWifi7Enabled",
            "android.net.wifi.WifiNetworkSuggestion#isWifi7Enabled"})
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testWifi7Enabled() throws Exception {
        // Make sure device supports Wi-Fi 7
        assumeTrue(sWifiManager.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE));
        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.getPrivilegedConfiguredNetworks());
        assertWithMessage("Need at least one saved network").that(
                savedNetworks.isEmpty()).isFalse();
        WifiConfiguration wifi7Network = TestHelper.findFirstAvailableSavedNetwork(sWifiManager,
                savedNetworks, TestHelper.AP_CAPABILITY_BIT_WIFI7);
        // TODO: b/322011012
        assumeTrue("Unable to locate Wi-Fi 7 networks in range.\n", wifi7Network != null);
        WifiNetworkSuggestion suggestion =
                TestHelper.createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
                        wifi7Network).setWifi7Enabled(true).build();
        assertTrue(suggestion.isWifi7Enabled());
        // Enable Wi-Fi 7 and connect
        sNsNetworkCallback = sTestHelper.testConnectionFlowWithSuggestion(wifi7Network, suggestion,
                mExecutorService, Set.of(), false);
        // Check new connection is Wi-Fi 7
        assertTrue(sWifiManager.getConnectionInfo().getWifiStandard()
                == ScanResult.WIFI_STANDARD_11BE);
    }
}
