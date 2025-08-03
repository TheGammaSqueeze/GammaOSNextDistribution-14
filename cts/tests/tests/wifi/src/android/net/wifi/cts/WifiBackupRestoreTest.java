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

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.wifi.WifiConfiguration.METERED_OVERRIDE_METERED;
import static android.net.wifi.WifiConfiguration.METERED_OVERRIDE_NONE;
import static android.net.wifi.WifiConfiguration.METERED_OVERRIDE_NOT_METERED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.content.Context;
import android.net.IpConfiguration;
import android.net.LinkAddress;
import android.net.ProxyInfo;
import android.net.StaticIpConfiguration;
import android.net.Uri;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.ThrowingRunnable;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tests for wifi backup/restore functionality.
 */
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@SmallTest
@RunWith(AndroidJUnit4.class)
public class WifiBackupRestoreTest extends WifiJUnit4TestBase {
    private static final String TAG = "WifiBackupRestoreTest";
    private static final String LEGACY_SUPP_CONF_FILE =
            "assets/BackupLegacyFormatSupplicantConf.txt";
    private static final String LEGACY_IP_CONF_FILE =
            "assets/BackupLegacyFormatIpConf.txt";
    private static final String V1_0_FILE = "assets/BackupV1.0Format.xml";
    private static final String V1_1_FILE = "assets/BackupV1.1Format.xml";
    private static final String V1_2_FILE = "assets/BackupV1.2Format.xml";

    public static final String EXPECTED_LEGACY_STATIC_IP_LINK_ADDRESS = "192.168.48.2";
    public static final int EXPECTED_LEGACY_STATIC_IP_LINK_PREFIX_LENGTH = 8;
    public static final String EXPECTED_LEGACY_STATIC_IP_GATEWAY_ADDRESS = "192.168.48.1";
    public static final String[] EXPECTED_LEGACY_STATIC_IP_DNS_SERVER_ADDRESSES =
            new String[]{"192.168.48.1", "192.168.48.10"};
    public static final String EXPECTED_LEGACY_STATIC_PROXY_HOST = "192.168.48.1";
    public static final int EXPECTED_LEGACY_STATIC_PROXY_PORT = 8000;
    public static final String EXPECTED_LEGACY_STATIC_PROXY_EXCLUSION_LIST = "";
    public static final String EXPECTED_LEGACY_PAC_PROXY_LOCATION = "http://";

    private static Context sContext;
    private static WifiManager sWifiManager;
    private static UiDevice sUiDevice;
    private static boolean sWasVerboseLoggingEnabled;
    private static boolean sShouldRunTest = false;

    private static final int DURATION = 10_000;
    private static final int DURATION_SCREEN_TOGGLE = 2000;

    @BeforeClass
    public static void setUpClass() throws Exception {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        // skip the test if WiFi is not supported
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
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScanThrottleEnabled(false));

        if (!sWifiManager.isWifiEnabled()) setWifiEnabled(true);
        sUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        turnScreenOn();
        PollingCheck.check("Wifi not enabled", DURATION, () -> sWifiManager.isWifiEnabled());

        List<WifiConfiguration> savedNetworks = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.getPrivilegedConfiguredNetworks());
        assertWithMessage("Need at least one saved network").that(savedNetworks).isNotEmpty();
    }

    @Before
    public void setup() {
        assumeTrue(sShouldRunTest);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (!sShouldRunTest) return;
        if (!sWifiManager.isWifiEnabled()) setWifiEnabled(true);
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setVerboseLoggingEnabled(sWasVerboseLoggingEnabled));
    }

    private static void setWifiEnabled(boolean enable) throws Exception {
        ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.setWifiEnabled(enable));
    }

    private static void turnScreenOn() throws Exception {
        sUiDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        sUiDevice.executeShellCommand("wm dismiss-keyguard");
        // Since the screen on/off intent is ordered, they will not be sent right now.
        Thread.sleep(DURATION_SCREEN_TOGGLE);
    }

    private void flipMeteredOverride(WifiConfiguration network) {
        if (network.meteredOverride == METERED_OVERRIDE_NONE) {
            network.meteredOverride = METERED_OVERRIDE_METERED;
        } else if (network.meteredOverride == METERED_OVERRIDE_METERED) {
            network.meteredOverride = METERED_OVERRIDE_NOT_METERED;
        } else if (network.meteredOverride == METERED_OVERRIDE_NOT_METERED) {
            network.meteredOverride = METERED_OVERRIDE_NONE;
        }
    }

    /** WifiConfiguration#isEnterprise() is @hide, so copy/paste partial implementation here. */
    private static boolean isEnterprise(WifiConfiguration config) {
        WifiEnterpriseConfig enterpriseConfig = config.enterpriseConfig;
        return enterpriseConfig != null
                && enterpriseConfig.getEapMethod() != WifiEnterpriseConfig.Eap.NONE;
    }

    /**
     * Tests for {@link WifiManager#retrieveBackupData()} &
     * {@link WifiManager#restoreBackupData(byte[])}
     * Note: If the network was not created by an app with OVERRIDE_WIFI_CONFIG permission (held
     * by AOSP settings app for example), then the backup data will not contain that network. If
     * the device does not contain any such pre-existing saved network, then this test will be
     * a no-op, will only ensure that the device does not crash when invoking the API's.
     */
    @Test
    public void testCanRestoreBackupData() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        WifiConfiguration origNetwork = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();

            // Pick a regular saved network to modify (non-enterprise, non-Passpoint)
            origNetwork = sWifiManager.getConfiguredNetworks().stream()
                    .filter(n -> {
                        boolean canOverrideConfig = sContext.checkPermission(
                                android.Manifest.permission.OVERRIDE_WIFI_CONFIG, -1, n.creatorUid)
                                == PERMISSION_GRANTED;
                        return canOverrideConfig && !isEnterprise(n) && !n.isPasspoint();
                    })
                    .findAny()
                    .orElse(null);
            if (origNetwork == null) {
                Log.e(TAG, "Need a non-enterprise and non-Passpoint network created by an app "
                        + "holding OVERRIDE_WIFI_CONFIG permission to fully evaluate the "
                        + "functionality");
            } else {

                // Retrieve backup data.
                byte[] backupData = sWifiManager.retrieveBackupData();
                // Modify the metered bit.
                final String origNetworkSsid = origNetwork.SSID;
                WifiConfiguration modNetwork = new WifiConfiguration(origNetwork);
                flipMeteredOverride(modNetwork);
                int networkId = sWifiManager.updateNetwork(modNetwork);
                assertThat(networkId).isEqualTo(origNetwork.networkId);
                assertThat(sWifiManager.getConfiguredNetworks()
                        .stream()
                        .filter(n -> n.SSID.equals(origNetworkSsid))
                        .findAny()
                        .get().meteredOverride)
                        .isNotEqualTo(origNetwork.meteredOverride);

                // Restore the original backup data & ensure that the metered bit is back to orig.
                sWifiManager.restoreBackupData(backupData);
                int metered = sWifiManager.getConfiguredNetworks()
                        .stream()
                        .filter(n -> n.SSID.equals(origNetworkSsid))
                        .findAny()
                        .get().meteredOverride;
                // Adopt two behaviors
                assertThat(metered == origNetwork.meteredOverride
                        || metered == modNetwork.meteredOverride).isTrue();
            }
        } finally {
            // Restore the orig network
            if (origNetwork != null) {
                sWifiManager.updateNetwork(origNetwork);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests for {@link WifiManager#retrieveSoftApBackupData()} &
     * {@link WifiManager#restoreSoftApBackupData(byte[])}
     */
    @Test
    public void testCanRestoreSoftApBackupData() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        SoftApConfiguration origSoftApConfig = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();


            // get soft ap configuration and set it back to update configuration to user
            // configuration.
            sWifiManager.setSoftApConfiguration(sWifiManager.getSoftApConfiguration());

            // Retrieve original soft ap config.
            origSoftApConfig = sWifiManager.getSoftApConfiguration();

            // Retrieve backup data.
            byte[] backupData = sWifiManager.retrieveSoftApBackupData();

            // Modify softap config and set it.
            String origSsid = origSoftApConfig.getSsid();
            char lastOrigSsidChar = origSsid.charAt(origSsid.length() - 1);
            String updatedSsid = new StringBuilder(origSsid.substring(0, origSsid.length() - 1))
                    .append((lastOrigSsidChar == 'a' || lastOrigSsidChar == 'A') ? 'b' : 'a')
                    .toString();
            SoftApConfiguration modSoftApConfig = new SoftApConfiguration.Builder(origSoftApConfig)
                    .setSsid(updatedSsid)
                    .build();
            sWifiManager.setSoftApConfiguration(modSoftApConfig);
            // Ensure that it does not match the orig softap config.
            assertThat(sWifiManager.getSoftApConfiguration()).isNotEqualTo(origSoftApConfig);

            // Restore the original backup data & ensure that the orig softap config is restored.
            sWifiManager.restoreSoftApBackupData(backupData);
            assertThat(sWifiManager.getSoftApConfiguration()).isEqualTo(origSoftApConfig);
        } finally {
            if (origSoftApConfig != null) {
                sWifiManager.setSoftApConfiguration(origSoftApConfig);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Read the content of the given resource file into a String.
     *
     * @param filename String name of the file
     * @return Byte array of the contents of the file.
     * @throws IOException
     */
    private byte[] loadResourceFile(String filename) throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream(filename);
        DataInputStream dis = new DataInputStream(in);
        byte[] data = new byte[dis.available()];
        dis.readFully(data);
        return data;
    }

    private WifiConfiguration createExpectedLegacyWepWifiConfiguration() {
        WifiConfiguration configuration = new WifiConfiguration();
        configuration.SSID = "\"TestSsid1\"";
        configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        configuration.wepKeys = new String[4];
        configuration.wepKeys[0] = "\"WepAscii12345\"";
        configuration.wepKeys[1] = "\"WepAs\"";
        configuration.wepKeys[2] = "45342312ab";
        configuration.wepKeys[3] = "45342312ab45342312ab34ac12";
        configuration.wepTxKeyIndex = 1;
        return configuration;
    }

    private WifiConfiguration createExpectedLegacyPskWifiConfiguration() {
        WifiConfiguration configuration = new WifiConfiguration();
        configuration.SSID = "\"TestSsid2\"";
        configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        configuration.preSharedKey = "\"TestPsk123\"";
        return configuration;
    }

    private WifiConfiguration createExpectedLegacyOpenWifiConfiguration() {
        WifiConfiguration configuration = new WifiConfiguration();
        configuration.SSID = "\"TestSsid3\"";
        configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        return configuration;
    }

    private IpConfiguration createExpectedLegacyDHCPIpConfigurationWithPacProxy() throws Exception {
        IpConfiguration ipConfiguration = new IpConfiguration();
        ipConfiguration.setIpAssignment(IpConfiguration.IpAssignment.DHCP);
        ipConfiguration.setProxySettings(IpConfiguration.ProxySettings.PAC);
        ipConfiguration.setHttpProxy(ProxyInfo.buildPacProxy(
                Uri.parse(EXPECTED_LEGACY_PAC_PROXY_LOCATION)));
        return ipConfiguration;
    }

    private StaticIpConfiguration createExpectedLegacyStaticIpconfiguration() throws Exception {
        return new StaticIpConfiguration.Builder()
                .setIpAddress(
                        new LinkAddress(
                                InetAddress.getByName(EXPECTED_LEGACY_STATIC_IP_LINK_ADDRESS),
                                EXPECTED_LEGACY_STATIC_IP_LINK_PREFIX_LENGTH))
                .setGateway(InetAddress.getByName(EXPECTED_LEGACY_STATIC_IP_GATEWAY_ADDRESS))
                .setDnsServers(Arrays.asList(EXPECTED_LEGACY_STATIC_IP_DNS_SERVER_ADDRESSES)
                        .stream()
                        .map(s -> {
                            try {
                                return InetAddress.getByName(s);
                            } catch (UnknownHostException e) {
                                return null;
                            }
                        })
                        .collect(Collectors.toList()))
                .build();
    }

    private IpConfiguration createExpectedLegacyStaticIpConfigurationWithPacProxy()
            throws Exception {
        IpConfiguration ipConfiguration = new IpConfiguration();
        ipConfiguration.setIpAssignment(IpConfiguration.IpAssignment.STATIC);
        ipConfiguration.setStaticIpConfiguration(createExpectedLegacyStaticIpconfiguration());
        ipConfiguration.setProxySettings(IpConfiguration.ProxySettings.PAC);
        ipConfiguration.setHttpProxy(ProxyInfo.buildPacProxy(
                Uri.parse(EXPECTED_LEGACY_PAC_PROXY_LOCATION)));
        return ipConfiguration;
    }

    private IpConfiguration createExpectedLegacyStaticIpConfigurationWithStaticProxy()
            throws Exception {
        IpConfiguration ipConfiguration = new IpConfiguration();
        ipConfiguration.setIpAssignment(IpConfiguration.IpAssignment.STATIC);
        ipConfiguration.setStaticIpConfiguration(createExpectedLegacyStaticIpconfiguration());
        ipConfiguration.setProxySettings(IpConfiguration.ProxySettings.STATIC);
        ipConfiguration.setHttpProxy(ProxyInfo.buildDirectProxy(
                EXPECTED_LEGACY_STATIC_PROXY_HOST, EXPECTED_LEGACY_STATIC_PROXY_PORT,
                Arrays.asList(EXPECTED_LEGACY_STATIC_PROXY_EXCLUSION_LIST)));
        return ipConfiguration;
    }

    /**
     * Check that expected configrations could be found in restored configurations.
     * As multi-type configurations would be converted to several single-type configurations,
     * two list could not be compared directly.
     */
    private void assertConfigurationsEqual(
            List<WifiConfiguration> expected, List<WifiConfiguration> actual) {
        assertThat(actual.size() >= expected.size()).isTrue();
        for (WifiConfiguration expectedConfiguration : expected) {
            String expectedConfigKey = expectedConfiguration.getKey();
            boolean didCompare = false;
            for (WifiConfiguration actualConfiguration : actual) {
                String actualConfigKey = actualConfiguration.getKey();
                if (actualConfigKey.equals(expectedConfigKey)) {
                    assertConfigurationEqual(
                            expectedConfiguration, actualConfiguration);
                    didCompare = true;
                }
            }
            assertWithMessage("Didn't find matching config for key = "
                    + expectedConfigKey).that(didCompare).isTrue();
        }
    }

    /**
     * Asserts that the 2 WifiConfigurations are equal.
     */
    private void assertConfigurationEqual(
            WifiConfiguration expected, WifiConfiguration actual) {
        assertThat(actual).isNotNull();
        assertThat(expected).isNotNull();
        assertWithMessage("Network: " + actual.toString())
                .that(actual.SSID).isEqualTo(expected.SSID);
        assertWithMessage("Network: " + actual.toString())
                .that(actual.preSharedKey).isEqualTo(expected.preSharedKey);
        assertWithMessage("Network: " + actual.toString())
                .that(actual.wepKeys).isEqualTo(expected.wepKeys);
        assertWithMessage("Network: " + actual.toString())
                .that(actual.wepTxKeyIndex).isEqualTo(expected.wepTxKeyIndex);
        assertWithMessage("Network: " + actual.toString())
                .that(actual.hiddenSSID).isEqualTo(expected.hiddenSSID);
        assertWithMessage("Network: " + actual.toString())
                .that(actual.requirePmf).isEqualTo(expected.requirePmf);
        assertWithMessage("Network: " + actual.toString())
                .that(actual.allowedKeyManagement).isEqualTo(expected.allowedKeyManagement);
        assertWithMessage("Network: " + actual.toString())
                .that(actual.shared).isEqualTo(expected.shared);
        assertWithMessage("Network: " + actual.toString())
                .that(actual.allowAutojoin).isEqualTo(expected.allowAutojoin);
        assertWithMessage("Network: " + actual.toString())
                .that(actual.getIpConfiguration()).isEqualTo(expected.getIpConfiguration());
        assertWithMessage("Network: " + actual.toString())
                .that(actual.meteredOverride).isEqualTo(expected.meteredOverride);
        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(sContext)) {
            assertWithMessage("Network: " + actual.toString())
                    .that(actual.getProfileKey()).isEqualTo(expected.getProfileKey());
        } else {
            assertWithMessage("Network: " + actual.toString())
                    .that(actual.getKey()).isEqualTo(expected.getKey());
        }
    }

    private void testRestoreFromBackupData(
            List<WifiConfiguration> expectedConfigurations, ThrowingRunnable restoreMethod)
        throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        List<WifiConfiguration> restoredSavedNetworks = null;
        try {
            uiAutomation.adoptShellPermissionIdentity();
            Set<String> origSavedSsids = sWifiManager.getConfiguredNetworks().stream()
                    .map(n -> n.SSID)
                    .collect(Collectors.toSet());

            restoreMethod.run();
            Thread.sleep(500);
            restoredSavedNetworks = sWifiManager.getPrivilegedConfiguredNetworks().stream()
                    .filter(n -> !origSavedSsids.contains(n.SSID))
                    .collect(Collectors.toList());
            assertConfigurationsEqual(
                    expectedConfigurations, restoredSavedNetworks);
        } finally {
            // clean up all restored networks.
            if (restoredSavedNetworks != null) {
                for (WifiConfiguration network : restoredSavedNetworks) {
                    sWifiManager.removeNetwork(network.networkId);
                }
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private List<WifiConfiguration> createExpectedLegacyConfigurations() throws Exception {
        List<WifiConfiguration> expectedConfigurations = new ArrayList<>();
        WifiConfiguration wepNetwork = createExpectedLegacyWepWifiConfiguration();
        wepNetwork.setIpConfiguration(createExpectedLegacyDHCPIpConfigurationWithPacProxy());
        expectedConfigurations.add(wepNetwork);

        WifiConfiguration pskNetwork = createExpectedLegacyPskWifiConfiguration();
        pskNetwork.setIpConfiguration(createExpectedLegacyStaticIpConfigurationWithPacProxy());
        expectedConfigurations.add(pskNetwork);

        WifiConfiguration openNetwork = createExpectedLegacyOpenWifiConfiguration();
        openNetwork.setIpConfiguration(
                createExpectedLegacyStaticIpConfigurationWithStaticProxy());
        expectedConfigurations.add(openNetwork);
        return expectedConfigurations;
    }

    /**
     * Verify that 3 network configuration is deserialized correctly from AOSP
     * legacy supplicant/ipconf backup data format.
     */
    @Test
    public void testRestoreFromLegacyBackupFormat() throws Exception {
        testRestoreFromBackupData(createExpectedLegacyConfigurations(),
                () -> sWifiManager.restoreSupplicantBackupData(
                        loadResourceFile(LEGACY_SUPP_CONF_FILE),
                        loadResourceFile(LEGACY_IP_CONF_FILE)));

    }

    private List<WifiConfiguration> createExpectedV1_0Configurations() throws Exception {
        List<WifiConfiguration> expectedConfigurations = new ArrayList<>();
        WifiConfiguration wepNetwork = createExpectedLegacyWepWifiConfiguration();
        wepNetwork.setIpConfiguration(createExpectedLegacyDHCPIpConfigurationWithPacProxy());
        expectedConfigurations.add(wepNetwork);

        WifiConfiguration pskNetwork = createExpectedLegacyPskWifiConfiguration();
        pskNetwork.setIpConfiguration(createExpectedLegacyStaticIpConfigurationWithPacProxy());
        expectedConfigurations.add(pskNetwork);

        WifiConfiguration openNetwork = createExpectedLegacyOpenWifiConfiguration();
        openNetwork.setIpConfiguration(
                createExpectedLegacyStaticIpConfigurationWithStaticProxy());
        expectedConfigurations.add(openNetwork);
        return expectedConfigurations;
    }

    /**
     * Verify that 3 network configuration is deserialized correctly from AOSP 1.0 format.
     */
    @Test
    public void testRestoreFromV1_0BackupFormat() throws Exception {
        testRestoreFromBackupData(createExpectedV1_0Configurations(),
                () -> sWifiManager.restoreBackupData(loadResourceFile(V1_0_FILE)));
    }

    private List<WifiConfiguration> createExpectedV1_1Configurations() throws Exception {
        List<WifiConfiguration> expectedConfigurations = new ArrayList<>();
        WifiConfiguration wepNetwork = createExpectedLegacyWepWifiConfiguration();
        wepNetwork.setIpConfiguration(createExpectedLegacyDHCPIpConfigurationWithPacProxy());
        wepNetwork.meteredOverride = METERED_OVERRIDE_METERED;
        expectedConfigurations.add(wepNetwork);

        WifiConfiguration pskNetwork = createExpectedLegacyPskWifiConfiguration();
        pskNetwork.setIpConfiguration(createExpectedLegacyStaticIpConfigurationWithPacProxy());
        pskNetwork.meteredOverride = METERED_OVERRIDE_NONE;
        expectedConfigurations.add(pskNetwork);

        WifiConfiguration openNetwork = createExpectedLegacyOpenWifiConfiguration();
        openNetwork.setIpConfiguration(
                createExpectedLegacyStaticIpConfigurationWithStaticProxy());
        openNetwork.meteredOverride = METERED_OVERRIDE_NOT_METERED;
        expectedConfigurations.add(openNetwork);
        return expectedConfigurations;
    }

    /**
     * Verify that 3 network configuration is deserialized correctly from AOSP 1.1 format.
     */
    @Test
    public void testRestoreFromV1_1BackupFormat() throws Exception {
        testRestoreFromBackupData(createExpectedV1_1Configurations(),
                () -> sWifiManager.restoreBackupData(loadResourceFile(V1_1_FILE)));
    }

    private List<WifiConfiguration> createExpectedV1_2Configurations() throws Exception {
        List<WifiConfiguration> expectedConfigurations = new ArrayList<>();
        WifiConfiguration wepNetwork = createExpectedLegacyWepWifiConfiguration();
        wepNetwork.setIpConfiguration(createExpectedLegacyDHCPIpConfigurationWithPacProxy());
        wepNetwork.meteredOverride = METERED_OVERRIDE_METERED;
        wepNetwork.allowAutojoin = true;
        expectedConfigurations.add(wepNetwork);

        WifiConfiguration pskNetwork = createExpectedLegacyPskWifiConfiguration();
        pskNetwork.setIpConfiguration(createExpectedLegacyStaticIpConfigurationWithPacProxy());
        pskNetwork.meteredOverride = METERED_OVERRIDE_NONE;
        pskNetwork.allowAutojoin = false;
        expectedConfigurations.add(pskNetwork);

        WifiConfiguration openNetwork = createExpectedLegacyOpenWifiConfiguration();
        openNetwork.setIpConfiguration(
                createExpectedLegacyStaticIpConfigurationWithStaticProxy());
        openNetwork.meteredOverride = METERED_OVERRIDE_NOT_METERED;
        openNetwork.allowAutojoin = false;
        expectedConfigurations.add(openNetwork);
        return expectedConfigurations;
    }

    /**
     * Verify that 3 network configuration is deserialized correctly from AOSP 1.2 format.
     */
    @Test
    public void testRestoreFromV1_2BackupFormat() throws Exception {
        testRestoreFromBackupData(createExpectedV1_2Configurations(),
                () -> sWifiManager.restoreBackupData(loadResourceFile(V1_2_FILE)));
    }
}
