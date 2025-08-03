/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.net.wifi.p2p.cts;

import static android.net.wifi.p2p.WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP;
import static android.net.wifi.p2p.WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL;
import static android.net.wifi.p2p.WifiP2pGroup.NETWORK_ID_PERSISTENT;
import static android.net.wifi.p2p.WifiP2pGroup.NETWORK_ID_TEMPORARY;

import static org.junit.Assert.assertThrows;

import android.net.MacAddress;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.p2p.WifiP2pConfig;
import android.os.Build;
import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.test.AndroidTestCase;

import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.wifi.flags.Flags;

import java.util.Arrays;
import java.util.List;

public class WifiP2pConfigTest extends AndroidTestCase {
    private static final String TEST_NETWORK_NAME = "DIRECT-xy-Hello";
    private static final String TEST_PASSPHRASE = "8etterW0r1d";
    private static final int TEST_OWNER_BAND = WifiP2pConfig.GROUP_OWNER_BAND_5GHZ;
    private static final int TEST_OWNER_FREQ = 2447;
    private static final String TEST_DEVICE_ADDRESS = "aa:bb:cc:dd:ee:ff";

    public void testWifiP2pConfigCopyConstructor() {
        WifiP2pConfig.Builder builder = new WifiP2pConfig.Builder()
                .setNetworkName(TEST_NETWORK_NAME)
                .setPassphrase(TEST_PASSPHRASE)
                .setGroupOperatingBand(TEST_OWNER_BAND)
                .setDeviceAddress(MacAddress.fromString(TEST_DEVICE_ADDRESS))
                .enablePersistentMode(true);
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            builder.setGroupClientIpProvisioningMode(
                    GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL);
        }

        WifiP2pConfig copiedConfig = new WifiP2pConfig(builder.build());

        assertWifiP2pConfigHasFields(copiedConfig, TEST_NETWORK_NAME, TEST_PASSPHRASE,
                TEST_OWNER_BAND, TEST_DEVICE_ADDRESS, NETWORK_ID_PERSISTENT,
                ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)
                        ? GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL
                        : GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP);
    }

    public void testWifiP2pConfigBuilderForPersist() {
        WifiP2pConfig config = new WifiP2pConfig.Builder()
                .setNetworkName(TEST_NETWORK_NAME)
                .setPassphrase(TEST_PASSPHRASE)
                .setGroupOperatingBand(TEST_OWNER_BAND)
                .setDeviceAddress(MacAddress.fromString(TEST_DEVICE_ADDRESS))
                .enablePersistentMode(true)
                .build();

        assertWifiP2pConfigHasFields(config, TEST_NETWORK_NAME, TEST_PASSPHRASE,
                TEST_OWNER_BAND, TEST_DEVICE_ADDRESS, NETWORK_ID_PERSISTENT,
                GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP);
    }

    public void testWifiP2pConfigBuilderForNonPersist() {
        WifiP2pConfig config = new WifiP2pConfig.Builder()
                .setNetworkName(TEST_NETWORK_NAME)
                .setPassphrase(TEST_PASSPHRASE)
                .setGroupOperatingFrequency(TEST_OWNER_FREQ)
                .setDeviceAddress(MacAddress.fromString(TEST_DEVICE_ADDRESS))
                .enablePersistentMode(false)
                .build();

        assertWifiP2pConfigHasFields(config, TEST_NETWORK_NAME, TEST_PASSPHRASE,
                TEST_OWNER_FREQ, TEST_DEVICE_ADDRESS, NETWORK_ID_TEMPORARY,
                GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP);
    }

    public void testWifiP2pConfigBuilderForGroupClientIpProvisioningModeDefault() {
        WifiP2pConfig config = new WifiP2pConfig.Builder()
                .setNetworkName(TEST_NETWORK_NAME)
                .setPassphrase(TEST_PASSPHRASE)
                .setGroupOperatingFrequency(TEST_OWNER_FREQ)
                .setDeviceAddress(MacAddress.fromString(TEST_DEVICE_ADDRESS))
                .build();

        assertWifiP2pConfigHasFields(config, TEST_NETWORK_NAME, TEST_PASSPHRASE,
                TEST_OWNER_FREQ, TEST_DEVICE_ADDRESS, NETWORK_ID_TEMPORARY,
                GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    public void testWifiP2pConfigBuilderForGroupClientIpProvisioningModeIpv4Dhcp() {
        WifiP2pConfig config = new WifiP2pConfig.Builder()
                .setNetworkName(TEST_NETWORK_NAME)
                .setPassphrase(TEST_PASSPHRASE)
                .setGroupOperatingFrequency(TEST_OWNER_FREQ)
                .setDeviceAddress(MacAddress.fromString(TEST_DEVICE_ADDRESS))
                .setGroupClientIpProvisioningMode(GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP)
                .build();

        assertWifiP2pConfigHasFields(config, TEST_NETWORK_NAME, TEST_PASSPHRASE,
                TEST_OWNER_FREQ, TEST_DEVICE_ADDRESS, NETWORK_ID_TEMPORARY,
                GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    public void testWifiP2pConfigBuilderForGroupClientIpProvisioningModeIpv6LinkLocal() {
        WifiP2pConfig config = new WifiP2pConfig.Builder()
                .setNetworkName(TEST_NETWORK_NAME)
                .setPassphrase(TEST_PASSPHRASE)
                .setGroupOperatingFrequency(TEST_OWNER_FREQ)
                .setDeviceAddress(MacAddress.fromString(TEST_DEVICE_ADDRESS))
                .setGroupClientIpProvisioningMode(GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL)
                .build();

        assertWifiP2pConfigHasFields(config, TEST_NETWORK_NAME, TEST_PASSPHRASE,
                TEST_OWNER_FREQ, TEST_DEVICE_ADDRESS, NETWORK_ID_TEMPORARY,
                GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL);
    }

    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.S_V2)
    public void testWifiP2pConfigBuilderForIpv6LinkLocalNotSupportedBelowTiramisu() {
        assertThrows(UnsupportedOperationException.class, () ->
                new WifiP2pConfig.Builder()
                        .setDeviceAddress(MacAddress.fromString("aa:bb:cc:dd:ee:ff"))
                        .setGroupClientIpProvisioningMode(
                                GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL)
                        .build());
    }

    public void testWifiP2pConfigBuilderWithJoinExistingGroupSet() {
        WifiP2pConfig config = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(TEST_DEVICE_ADDRESS))
                .setJoinExistingGroup(true)
                .build();
        assertEquals(config.deviceAddress, TEST_DEVICE_ADDRESS);
        assertTrue(config.isJoinExistingGroup());
    }

    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    public void testWifiP2pConfigBuilderWithVendorData() {
        OuiKeyedData vendorDataElement =
                new OuiKeyedData.Builder(0x00aabbcc, new PersistableBundle()).build();
        List<OuiKeyedData> vendorData = Arrays.asList(vendorDataElement);
        WifiP2pConfig config = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(TEST_DEVICE_ADDRESS))
                .build();
        config.setVendorData(vendorData);
        assertTrue(vendorData.equals(config.getVendorData()));
    }

    private static void assertWifiP2pConfigHasFields(WifiP2pConfig config,
            String networkName, String passphrase, int groupOwnerFrequency, String deviceAddress,
            int networkId, int groupClientIpProvisioningMode) {
        assertEquals(config.getNetworkName(), networkName);
        assertEquals(config.getPassphrase(), passphrase);
        assertEquals(config.getGroupOwnerBand(), groupOwnerFrequency);
        assertEquals(config.deviceAddress, deviceAddress);
        assertEquals(config.getNetworkId(), networkId);
        assertEquals(config.getGroupClientIpProvisioningMode(), groupClientIpProvisioningMode);
        assertFalse(config.isJoinExistingGroup());
    }
}
