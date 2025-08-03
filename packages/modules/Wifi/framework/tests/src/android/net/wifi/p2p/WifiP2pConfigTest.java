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

package android.net.wifi.p2p;

import static android.net.wifi.p2p.WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP;
import static android.net.wifi.p2p.WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.net.MacAddress;
import android.net.wifi.OuiKeyedDataUtil;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Test;

/**
 * Unit test harness for {@link android.net.wifi.p2p.WifiP2pConfig}
 */
@SmallTest
public class WifiP2pConfigTest {

    private static final String DEVICE_ADDRESS = "aa:bb:cc:dd:ee:ff";
    /**
     * Check network name setter
     */
    @Test
    public void testBuilderInvalidNetworkName() throws Exception {
        WifiP2pConfig.Builder b = new WifiP2pConfig.Builder();

        // sunny case
        try {
            b.setNetworkName("DIRECT-ab-Hello");
        } catch (IllegalArgumentException e) {
            fail("Unexpected IllegalArgumentException");
        }

        // sunny case, no trailing string
        try {
            b.setNetworkName("DIRECT-WR");
        } catch (IllegalArgumentException e) {
            fail("Unexpected IllegalArgumentException");
        }

        // sunny case with maximum bytes for the network name
        try {
            b.setNetworkName("DIRECT-abcdefghijklmnopqrstuvwxy");
        } catch (IllegalArgumentException e) {
            fail("Unexpected IllegalArgumentException");
        }

        // less than 9 characters.
        try {
            b.setNetworkName("DIRECT-z");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) { }

        // not starts with DIRECT-xy.
        try {
            b.setNetworkName("ABCDEFGHIJK");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) { }

        // not starts with uppercase DIRECT-xy
        try {
            b.setNetworkName("direct-ab");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) { }

        // x and y are not selected from upper case letters, lower case letters or
        // numbers.
        try {
            b.setNetworkName("direct-a?");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) { }

        // over maximum bytes
        try {
            b.setNetworkName("DIRECT-abcdefghijklmnopqrstuvwxyz");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) { }
    }

    /**
     * Check passphrase setter
     */
    @Test
    public void testBuilderInvalidPassphrase() throws Exception {
        WifiP2pConfig.Builder b = new WifiP2pConfig.Builder();

        // sunny case
        try {
            b.setPassphrase("abcd1234");
        } catch (IllegalArgumentException e) {
            fail("Unexpected IllegalArgumentException");
        }

        // null string.
        try {
            b.setPassphrase(null);
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected exception.
        }

        // less than 8 characters.
        try {
            b.setPassphrase("12abcde");
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected exception.
        }

        // more than 63 characters.
        try {
            b.setPassphrase(
                    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890+/");
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected exception.
        }
    }

    /** Verify that a default config can be built. */
    @Test
    public void testBuildDefaultConfig() {
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS)).build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
    }

    /** Verify that a non-persistent config can be built. */
    @Test
    public void testBuildNonPersistentConfig() throws Exception {
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .enablePersistentMode(false).build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
        assertEquals(WifiP2pGroup.NETWORK_ID_TEMPORARY, c.netId);
    }

    /** Verify that a config by default has group client IP provisioning with DHCP IPv4. */
    @Test
    public void testBuildConfigWithGroupClientIpProvisioningModeDefault() throws Exception {
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
        assertEquals(c.getGroupClientIpProvisioningMode(),
                GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP);
    }

    /** Verify that a config with group client IP provisioning with IPv4 DHCP can be built. */
    @Test
    public void testBuildConfigWithGroupClientIpProvisioningModeIpv4Dhcp() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .setGroupClientIpProvisioningMode(GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP)
                .build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
        assertEquals(c.getGroupClientIpProvisioningMode(),
                GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP);
    }

    /** Verify that a config with group client IP provisioning with IPv6 link-local can be built. */
    @Test
    public void testBuildConfigWithGroupClientIpProvisioningModeIpv6LinkLocal() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .setGroupClientIpProvisioningMode(GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL)
                .build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
        assertEquals(c.getGroupClientIpProvisioningMode(),
                GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL);
    }

    /**
     * Verify that the builder throws IllegalArgumentException if invalid group client IP
     * provisioning mode is set.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithInvalidGroupClientIpProvisioningMode()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiP2pConfig c = new WifiP2pConfig.Builder().setGroupClientIpProvisioningMode(5).build();
    }

    /**
     * Verify that the builder throws IllegalStateException if none of
     * network name, passphrase, and device address is set.
     */
    @Test(expected = IllegalStateException.class)
    public void testBuildThrowIllegalStateExceptionWithoutNetworkNamePassphraseDeviceAddress()
            throws Exception {
        WifiP2pConfig c = new WifiP2pConfig.Builder().build();
    }

    /**
     * Verify that the builder throws IllegalStateException if only network name is set.
     */
    @Test(expected = IllegalStateException.class)
    public void testBuildThrowIllegalStateExceptionWithOnlyNetworkName()
            throws Exception {
        WifiP2pConfig c = new WifiP2pConfig.Builder().setNetworkName("DIRECT-ab-Hello").build();
    }

    /**
     * Verify that the builder throws IllegalStateException if only passphrase is set.
     */
    @Test(expected = IllegalStateException.class)
    public void testBuildThrowIllegalStateExceptionWithOnlyPassphrase()
            throws Exception {
        WifiP2pConfig c = new WifiP2pConfig.Builder().setPassphrase("12345677").build();
    }


    /** Verify that a config by default has join existing group field set to false */
    @Test
    public void testBuildConfigWithJoinExistingGroupDefault() throws Exception {
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
        assertFalse(c.isJoinExistingGroup());
    }

    /** Verify that a config with join existing group field can be built. */
    @Test
    public void testBuildConfigWithJoinExistingGroupSet() throws Exception {
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .setJoinExistingGroup(true)
                .build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
        assertTrue(c.isJoinExistingGroup());
    }

    @Test
    /*
     * Verify WifiP2pConfig basic operations
     */
    public void testWifiP2pConfig() throws Exception {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = DEVICE_ADDRESS;
        if (SdkLevel.isAtLeastV()) {
            config.setVendorData(OuiKeyedDataUtil.createTestOuiKeyedDataList(5));
        }

        WifiP2pConfig copiedConfig = new WifiP2pConfig(config);
        // no equals operator, use toString for comparison.
        assertEquals(config.toString(), copiedConfig.toString());

        Parcel parcelW = Parcel.obtain();
        config.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        WifiP2pConfig configFromParcel = WifiP2pConfig.CREATOR.createFromParcel(parcelR);

        if (SdkLevel.isAtLeastV()) {
            assertTrue(config.getVendorData().equals(configFromParcel.getVendorData()));
        }
        // no equals operator, use toString for comparison.
        assertEquals(config.toString(), configFromParcel.toString());
    }

    @Test
    /*
     * Verify WifiP2pConfig invalidate API
     */
    public void testInvalidate() throws Exception {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = DEVICE_ADDRESS;
        config.invalidate();
        assertEquals("", config.deviceAddress);
    }
}
