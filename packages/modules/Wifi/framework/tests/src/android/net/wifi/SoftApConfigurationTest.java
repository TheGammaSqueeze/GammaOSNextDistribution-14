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

package android.net.wifi;

import static android.net.wifi.ScanResult.InformationElement.EID_VSA;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;

import android.net.MacAddress;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.util.SparseIntArray;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@SmallTest
public class SoftApConfigurationTest {
    private static final String TEST_CHAR_SET_AS_STRING = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String TEST_BSSID = "aa:22:33:aa:bb:cc";
    private static final List<ScanResult.InformationElement> TEST_TWO_VENDOR_ELEMENTS =
            new ArrayList<>(Arrays.asList(
                    new ScanResult.InformationElement(EID_VSA, 0, new byte[]{ 1, 2, 3, 4 }),
                    new ScanResult.InformationElement(
                            EID_VSA,
                            0,
                            new byte[]{ (byte) 170, (byte) 187, (byte) 204, (byte) 221 })
                    ));
    private static final List<ScanResult.InformationElement> TEST_TWO_VENDOR_ELEMENTS_INVALID =
            new ArrayList<>(Arrays.asList(
                    new ScanResult.InformationElement(EID_VSA, 0, new byte[]{ 1, 2, 3, 4 }),
                    new ScanResult.InformationElement(
                            (byte) 222,
                            0,
                            new byte[]{ (byte) 170, (byte) 187, (byte) 204, (byte) 221 })
                    ));

    private SoftApConfiguration parcelUnparcel(SoftApConfiguration configIn) {
        Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(configIn, 0);
        parcel.setDataPosition(0);
        SoftApConfiguration configOut =
                parcel.readParcelable(SoftApConfiguration.class.getClassLoader());
        parcel.recycle();
        return configOut;
    }

    /**
     * Helper method to generate random string.
     *
     * Note: this method has limited use as a random string generator.
     * The characters used in this method do no not cover all valid inputs.
     * @param length number of characters to generate for the string
     * @return String generated string of random characters
     */
    private String generateRandomString(int length) {
        Random random = new Random();
        StringBuilder stringBuilder = new StringBuilder(length);
        int index = -1;
        while (stringBuilder.length() < length) {
            index = random.nextInt(TEST_CHAR_SET_AS_STRING.length());
            stringBuilder.append(TEST_CHAR_SET_AS_STRING.charAt(index));
        }
        return stringBuilder.toString();
    }

    @Test
    public void testBasicSettings() {
        MacAddress testBssid = MacAddress.fromString(TEST_BSSID);
        String utf8Ssid = "ssid";
        SoftApConfiguration original = new SoftApConfiguration.Builder()
                .setSsid(utf8Ssid)
                .build();
        assertThat(original.getSsid()).isEqualTo(utf8Ssid);
        assertThat(original.getWifiSsid()).isEqualTo(WifiSsid.fromUtf8Text(utf8Ssid));
        assertThat(original.getPassphrase()).isNull();
        assertThat(original.getSecurityType()).isEqualTo(SoftApConfiguration.SECURITY_TYPE_OPEN);
        assertThat(original.getBand()).isEqualTo(SoftApConfiguration.BAND_2GHZ);
        assertThat(original.getChannel()).isEqualTo(0);
        assertThat(original.isHiddenSsid()).isEqualTo(false);
        assertThat(original.getMaxNumberOfClients()).isEqualTo(0);
        assertThat(original.getPersistentRandomizedMacAddress()).isNull();
        if (SdkLevel.isAtLeastS()) {
            assertThat(original.isBridgedModeOpportunisticShutdownEnabled())
                    .isEqualTo(true);
            assertThat(original.isIeee80211axEnabled())
                    .isEqualTo(true);
            assertThat(original.isUserConfiguration())
                    .isEqualTo(true);
            if (SdkLevel.isAtLeastT()) {
                assertThat(original.getBridgedModeOpportunisticShutdownTimeoutMillis())
                        .isEqualTo(SoftApConfiguration.DEFAULT_TIMEOUT);
                assertThat(original.getMacRandomizationSetting())
                        .isEqualTo(SoftApConfiguration.RANDOMIZATION_NON_PERSISTENT);
                assertThat(original.getVendorElements().size()).isEqualTo(0);
            } else {
                assertThat(original.getMacRandomizationSetting())
                        .isEqualTo(SoftApConfiguration.RANDOMIZATION_PERSISTENT);
            }
        }

        SoftApConfiguration unparceled = parcelUnparcel(original);
        assertThat(unparceled).isNotSameInstanceAs(original);
        assertThat(unparceled).isEqualTo(original);
        assertThat(unparceled.hashCode()).isEqualTo(original.hashCode());

        SoftApConfiguration copy = new SoftApConfiguration.Builder(original).build();
        assertThat(copy).isNotSameInstanceAs(original);
        assertThat(copy).isEqualTo(original);
        assertThat(copy.hashCode()).isEqualTo(original.hashCode());
    }

    @Test
    public void testSetWifiSsid() {
        assumeTrue(SdkLevel.isAtLeastT());

        // UTF-8
        WifiSsid wifiSsidUtf8 = WifiSsid.fromUtf8Text("ssid");
        SoftApConfiguration utf8Config = new SoftApConfiguration.Builder()
                .setWifiSsid(wifiSsidUtf8)
                .build();
        assertThat(utf8Config.getWifiSsid()).isEqualTo(wifiSsidUtf8);
        assertThat(utf8Config.getSsid()).isEqualTo("ssid");

        SoftApConfiguration unparceled = parcelUnparcel(utf8Config);
        assertThat(unparceled).isNotSameInstanceAs(utf8Config);
        assertThat(unparceled).isEqualTo(utf8Config);
        assertThat(unparceled.hashCode()).isEqualTo(utf8Config.hashCode());

        // Non-UTF-8
        byte[] nonUtf8Bytes =
                new byte[]{(byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67, (byte) 0x89};
        WifiSsid wifiSsidNonUtf8 = WifiSsid.fromBytes(nonUtf8Bytes);
        SoftApConfiguration nonUtf8Config = new SoftApConfiguration.Builder()
                .setWifiSsid(wifiSsidNonUtf8)
                .build();
        assertThat(nonUtf8Config.getWifiSsid()).isEqualTo(wifiSsidNonUtf8);
        assertThat(nonUtf8Config.getSsid()).isEqualTo(WifiManager.UNKNOWN_SSID);

        unparceled = parcelUnparcel(nonUtf8Config);
        assertThat(unparceled).isNotSameInstanceAs(nonUtf8Config);
        assertThat(unparceled).isEqualTo(nonUtf8Config);
        assertThat(unparceled.hashCode()).isEqualTo(nonUtf8Config.hashCode());
    }

    @Test
    public void testWpa2() {
        SoftApConfiguration original = new SoftApConfiguration.Builder()
                .setPassphrase("secretsecret", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .build();
        assertThat(original.getPassphrase()).isEqualTo("secretsecret");
        assertThat(original.getSecurityType()).isEqualTo(
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        assertThat(original.getBand()).isEqualTo(SoftApConfiguration.BAND_2GHZ);
        assertThat(original.getChannel()).isEqualTo(0);
        assertThat(original.isHiddenSsid()).isEqualTo(false);
        assertThat(original.getMaxNumberOfClients()).isEqualTo(0);

        SoftApConfiguration unparceled = parcelUnparcel(original);
        assertThat(unparceled).isNotSameInstanceAs(original);
        assertThat(unparceled).isEqualTo(original);
        assertThat(unparceled.hashCode()).isEqualTo(original.hashCode());

        SoftApConfiguration copy = new SoftApConfiguration.Builder(original).build();
        assertThat(copy).isNotSameInstanceAs(original);
        assertThat(copy).isEqualTo(original);
        assertThat(copy.hashCode()).isEqualTo(original.hashCode());
    }

    @Test
    public void testWpa2WithAllFieldCustomized() {
        MacAddress testRandomizedMacAddress = MacAddress.fromString(TEST_BSSID);
        List<MacAddress> testBlockedClientList = new ArrayList<>();
        List<MacAddress> testAllowedClientList = new ArrayList<>();
        testBlockedClientList.add(MacAddress.fromString("11:22:33:44:55:66"));
        testAllowedClientList.add(MacAddress.fromString("aa:bb:cc:dd:ee:ff"));
        SoftApConfiguration.Builder originalBuilder = new SoftApConfiguration.Builder()
                .setPassphrase("secretsecret", SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setChannel(149, SoftApConfiguration.BAND_5GHZ)
                .setHiddenSsid(true)
                .setMaxNumberOfClients(10)
                .setAutoShutdownEnabled(true)
                .setShutdownTimeoutMillis(500000)
                .setClientControlByUserEnabled(true)
                .setBlockedClientList(testBlockedClientList)
                .setAllowedClientList(testAllowedClientList)
                .setRandomizedMacAddress(testRandomizedMacAddress);
        if (SdkLevel.isAtLeastS()) {
            originalBuilder.setMacRandomizationSetting(SoftApConfiguration.RANDOMIZATION_NONE);
            originalBuilder.setBridgedModeOpportunisticShutdownEnabled(false);
            originalBuilder.setIeee80211axEnabled(false);
            originalBuilder.setUserConfiguration(false);
        }

        if (SdkLevel.isAtLeastT()) {
            originalBuilder.setBridgedModeOpportunisticShutdownTimeoutMillis(300_000);
            originalBuilder.setVendorElements(TEST_TWO_VENDOR_ELEMENTS);
        }

        SoftApConfiguration original = originalBuilder.build();
        assertThat(original.getPassphrase()).isEqualTo("secretsecret");
        assertThat(original.getSecurityType()).isEqualTo(
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        assertThat(original.getBand()).isEqualTo(SoftApConfiguration.BAND_5GHZ);
        assertThat(original.getChannel()).isEqualTo(149);
        assertThat(original.isHiddenSsid()).isEqualTo(true);
        assertThat(original.getMaxNumberOfClients()).isEqualTo(10);
        assertThat(original.isAutoShutdownEnabled()).isEqualTo(true);
        assertThat(original.getShutdownTimeoutMillis()).isEqualTo(500000);
        assertThat(original.isClientControlByUserEnabled()).isEqualTo(true);
        assertThat(original.getBlockedClientList()).isEqualTo(testBlockedClientList);
        assertThat(original.getAllowedClientList()).isEqualTo(testAllowedClientList);
        assertThat(original.getPersistentRandomizedMacAddress())
                .isEqualTo(testRandomizedMacAddress);
        if (SdkLevel.isAtLeastS()) {
            assertThat(original.getMacRandomizationSetting())
                    .isEqualTo(SoftApConfiguration.RANDOMIZATION_NONE);
            assertThat(original.isBridgedModeOpportunisticShutdownEnabled())
                    .isEqualTo(false);
            assertThat(original.isIeee80211axEnabled())
                    .isEqualTo(false);
            assertThat(original.isUserConfiguration())
                    .isEqualTo(false);
        }
        if (SdkLevel.isAtLeastT()) {
            assertThat(original.getBridgedModeOpportunisticShutdownTimeoutMillis())
                    .isEqualTo(300_000);
            assertThat(original.getVendorElements())
                    .isEqualTo(TEST_TWO_VENDOR_ELEMENTS);
        }

        SoftApConfiguration unparceled = parcelUnparcel(original);
        assertThat(unparceled).isNotSameInstanceAs(original);
        assertThat(unparceled).isEqualTo(original);
        assertThat(unparceled.hashCode()).isEqualTo(original.hashCode());

        SoftApConfiguration copy = new SoftApConfiguration.Builder(original).build();
        assertThat(copy).isNotSameInstanceAs(original);
        assertThat(copy).isEqualTo(original);
        assertThat(copy.hashCode()).isEqualTo(original.hashCode());
    }

    @Test
    public void testWpa3Sae() {
        SoftApConfiguration original = new SoftApConfiguration.Builder()
                .setPassphrase("secretsecret", SoftApConfiguration.SECURITY_TYPE_WPA3_SAE)
                .setChannel(149, SoftApConfiguration.BAND_5GHZ)
                .setHiddenSsid(true)
                .build();
        assertThat(original.getPassphrase()).isEqualTo("secretsecret");
        assertThat(original.getSecurityType()).isEqualTo(
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
        assertThat(original.getBand()).isEqualTo(SoftApConfiguration.BAND_5GHZ);
        assertThat(original.getChannel()).isEqualTo(149);
        assertThat(original.isHiddenSsid()).isEqualTo(true);


        SoftApConfiguration unparceled = parcelUnparcel(original);
        assertThat(unparceled).isNotSameInstanceAs(original);
        assertThat(unparceled).isEqualTo(original);
        assertThat(unparceled.hashCode()).isEqualTo(original.hashCode());

        SoftApConfiguration copy = new SoftApConfiguration.Builder(original).build();
        assertThat(copy).isNotSameInstanceAs(original);
        assertThat(copy).isEqualTo(original);
        assertThat(copy.hashCode()).isEqualTo(original.hashCode());
    }

    @Test
    public void testWpa3SaeTransition() {
        SoftApConfiguration original = new SoftApConfiguration.Builder()
                .setPassphrase("secretsecret",
                        SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION)
                .setChannel(149, SoftApConfiguration.BAND_5GHZ)
                .setHiddenSsid(true)
                .build();
        assertThat(original.getSecurityType()).isEqualTo(
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION);
        assertThat(original.getPassphrase()).isEqualTo("secretsecret");
        assertThat(original.getBand()).isEqualTo(SoftApConfiguration.BAND_5GHZ);
        assertThat(original.getChannel()).isEqualTo(149);
        assertThat(original.isHiddenSsid()).isEqualTo(true);


        SoftApConfiguration unparceled = parcelUnparcel(original);
        assertThat(unparceled).isNotSameInstanceAs(original);
        assertThat(unparceled).isEqualTo(original);
        assertThat(unparceled.hashCode()).isEqualTo(original.hashCode());

        SoftApConfiguration copy = new SoftApConfiguration.Builder(original).build();
        assertThat(copy).isNotSameInstanceAs(original);
        assertThat(copy).isEqualTo(original);
        assertThat(copy.hashCode()).isEqualTo(original.hashCode());
    }

    @Test
    public void testWpa3OweTransition() {
        assumeTrue(SdkLevel.isAtLeastT());
        SoftApConfiguration original = new SoftApConfiguration.Builder()
                .setPassphrase(null,
                        SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION)
                .setChannel(149, SoftApConfiguration.BAND_5GHZ)
                .setHiddenSsid(false)
                .build();
        assertThat(original.getSecurityType()).isEqualTo(
                SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION);
        assertThat(original.getPassphrase()).isEqualTo(null);
        assertThat(original.getBand()).isEqualTo(SoftApConfiguration.BAND_5GHZ);
        assertThat(original.getChannel()).isEqualTo(149);
        assertThat(original.isHiddenSsid()).isEqualTo(false);

        SoftApConfiguration unparceled = parcelUnparcel(original);
        assertThat(unparceled).isNotSameInstanceAs(original);
        assertThat(unparceled).isEqualTo(original);
        assertThat(unparceled.hashCode()).isEqualTo(original.hashCode());

        SoftApConfiguration copy = new SoftApConfiguration.Builder(original).build();
        assertThat(copy).isNotSameInstanceAs(original);
        assertThat(copy).isEqualTo(original);
        assertThat(copy.hashCode()).isEqualTo(original.hashCode());

        assertThrows(IllegalArgumentException.class,
                () -> new SoftApConfiguration.Builder().setPassphrase(
                        "something or other",
                        SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION));
    }

    @Test
    public void testWpa3Owe() {
        assumeTrue(SdkLevel.isAtLeastT());
        SoftApConfiguration original = new SoftApConfiguration.Builder()
                .setPassphrase(null,
                        SoftApConfiguration.SECURITY_TYPE_WPA3_OWE)
                .setChannel(149, SoftApConfiguration.BAND_5GHZ)
                .setHiddenSsid(false)
                .build();
        assertThat(original.getSecurityType()).isEqualTo(
                SoftApConfiguration.SECURITY_TYPE_WPA3_OWE);
        assertThat(original.getPassphrase()).isEqualTo(null);
        assertThat(original.getBand()).isEqualTo(SoftApConfiguration.BAND_5GHZ);
        assertThat(original.getChannel()).isEqualTo(149);
        assertThat(original.isHiddenSsid()).isEqualTo(false);

        SoftApConfiguration unparceled = parcelUnparcel(original);
        assertThat(unparceled).isNotSameInstanceAs(original);
        assertThat(unparceled).isEqualTo(original);
        assertThat(unparceled.hashCode()).isEqualTo(original.hashCode());

        SoftApConfiguration copy = new SoftApConfiguration.Builder(original).build();
        assertThat(copy).isNotSameInstanceAs(original);
        assertThat(copy).isEqualTo(original);
        assertThat(copy.hashCode()).isEqualTo(original.hashCode());

        assertThrows(IllegalArgumentException.class,
                () -> new SoftApConfiguration.Builder().setPassphrase(
                        "something or other",
                        SoftApConfiguration.SECURITY_TYPE_WPA3_OWE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidBroadcastBssid() {
        SoftApConfiguration original = new SoftApConfiguration.Builder()
                .setBssid(MacAddress.BROADCAST_ADDRESS)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidMulticastBssid() {
        SoftApConfiguration original = new SoftApConfiguration.Builder()
                .setBssid(MacAddress.fromString("01:aa:bb:cc:dd:ee"))
                .build();
    }

    @Test
    public void testValidWpa2PasswordLength() {
        // ASCII
        new SoftApConfiguration.Builder().setPassphrase(
                "password",
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        // Pass without IllegalArgumentException

        // UTF-8
        new SoftApConfiguration.Builder().setPassphrase(
                "パスワード",
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        // Pass without IllegalArgumentException
    }

    @Test
    public void testValidWpa3SaeTransitionPasswordLength() {
        // ASCII
        new SoftApConfiguration.Builder().setPassphrase(
                "password",
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION);
        // Pass without IllegalArgumentException

        // UTF-8
        new SoftApConfiguration.Builder().setPassphrase(
                "パスワード",
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION);
        // Pass without IllegalArgumentException
    }

    @Test
    public void testInvalidWpa2PasswordLength() {
        assumeFalse(SdkLevel.isAtLeastT());
        // Too short ASCII
        try {
            new SoftApConfiguration.Builder().setPassphrase(
                    "Short!",
                    SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
            fail("Short ASCII password did not throw exception!");
        } catch (IllegalArgumentException e) {
            // Success
        }

        // Too long ASCII
        try {
            new SoftApConfiguration.Builder().setPassphrase(
                    "This ASCII passphrase is definitely much too long to be valid...",
                    SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
            fail("Long ASCII password did not throw exception!");
        } catch (IllegalArgumentException e) {
            // Success
        }

        // Too short UTF-8
        try {
            new SoftApConfiguration.Builder().setPassphrase(
                    "短い",
                    SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
            fail("Short UTF-8 password did not throw exception!");
        } catch (IllegalArgumentException e) {
            // Success
        }

        // Too long UTF-8
        try {
            new SoftApConfiguration.Builder().setPassphrase(
                    "このパスワードはちょっと長すぎて使えられません。",
                    SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
            fail("Long UTF-8 password did not throw exception!");
        } catch (IllegalArgumentException e) {
            // Success
        }
    }

    @Test
    public void testInvalidWpa3SaeTransitionPasswordLength() {
        assumeFalse(SdkLevel.isAtLeastT());
        // Too short ASCII
        try {
            new SoftApConfiguration.Builder().setPassphrase(
                    "Short!",
                    SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION);
            fail("Short ASCII password did not throw exception!");
        } catch (IllegalArgumentException e) {
            // Success
        }

        // Too long ASCII
        try {
            new SoftApConfiguration.Builder().setPassphrase(
                    "This ASCII passphrase is definitely much too long to be valid...",
                    SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION);
            fail("Long ASCII password did not throw exception!");
        } catch (IllegalArgumentException e) {
            // Success
        }

        // Too short UTF-8
        try {
            new SoftApConfiguration.Builder().setPassphrase(
                    "短い",
                    SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION);
            fail("Short UTF-8 password did not throw exception!");
        } catch (IllegalArgumentException e) {
            // Success
        }

        // Too long UTF-8
        try {
            new SoftApConfiguration.Builder().setPassphrase(
                    "このパスワードはちょっと長すぎて使えられません。",
                    SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION);
            fail("Long UTF-8 password did not throw exception!");
        } catch (IllegalArgumentException e) {
            // Success
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalieShutdownTimeoutMillis() {
        SoftApConfiguration original = new SoftApConfiguration.Builder()
                .setShutdownTimeoutMillis(-2)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZeroIsInvalidBridgedModeOpportunisticShutdownTimeoutMillis() {
        assumeTrue(SdkLevel.isAtLeastT());
        SoftApConfiguration original = new SoftApConfiguration.Builder()
                .setBridgedModeOpportunisticShutdownTimeoutMillis(0)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidBridgedModeOpportunisticShutdownTimeoutMillis() {
        assumeTrue(SdkLevel.isAtLeastT());
        SoftApConfiguration original = new SoftApConfiguration.Builder()
                .setBridgedModeOpportunisticShutdownTimeoutMillis(-2)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetClientListExceptionWhenExistMacAddressInBothList() {
        final MacAddress testMacAddress_1 = MacAddress.fromString("22:33:44:55:66:77");
        final MacAddress testMacAddress_2 = MacAddress.fromString("aa:bb:cc:dd:ee:ff");
        ArrayList<MacAddress> testAllowedClientList = new ArrayList<>();
        testAllowedClientList.add(testMacAddress_1);
        testAllowedClientList.add(testMacAddress_2);
        ArrayList<MacAddress> testBlockedClientList = new ArrayList<>();
        testBlockedClientList.add(testMacAddress_1);
        SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder();
        configBuilder.setBlockedClientList(testBlockedClientList)
                .setAllowedClientList(testAllowedClientList)
                .build();
    }

    @Test
    public void testToWifiConfigurationWithUnsupportedParameter() {
        SoftApConfiguration sae_config = new SoftApConfiguration.Builder()
                .setPassphrase("secretsecret", SoftApConfiguration.SECURITY_TYPE_WPA3_SAE)
                .build();

        assertNull(sae_config.toWifiConfiguration());
        SoftApConfiguration band_6g_config = new SoftApConfiguration.Builder()
                .setBand(SoftApConfiguration.BAND_6GHZ)
                .build();

        assertNull(band_6g_config.toWifiConfiguration());
    }

    @Test
    public void testToWifiConfigurationWithSupportedParameter() {
        SoftApConfiguration softApConfig_2g = new SoftApConfiguration.Builder()
                .setPassphrase("secretsecret",
                        SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setChannel(11, SoftApConfiguration.BAND_2GHZ)
                .setHiddenSsid(true)
                .build();
        WifiConfiguration wifiConfig_2g = softApConfig_2g.toWifiConfiguration();
        assertThat(wifiConfig_2g.getAuthType()).isEqualTo(WifiConfiguration.KeyMgmt.WPA2_PSK);
        assertThat(wifiConfig_2g.preSharedKey).isEqualTo("secretsecret");
        assertThat(wifiConfig_2g.apBand).isEqualTo(WifiConfiguration.AP_BAND_2GHZ);
        assertThat(wifiConfig_2g.apChannel).isEqualTo(11);
        assertThat(wifiConfig_2g.hiddenSSID).isEqualTo(true);

        SoftApConfiguration softApConfig_5g = new SoftApConfiguration.Builder()
                .setPassphrase("secretsecret",
                        SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setChannel(149, SoftApConfiguration.BAND_5GHZ)
                .setHiddenSsid(true)
                .build();
        WifiConfiguration wifiConfig_5g = softApConfig_5g.toWifiConfiguration();
        assertThat(wifiConfig_5g.getAuthType()).isEqualTo(WifiConfiguration.KeyMgmt.WPA2_PSK);
        assertThat(wifiConfig_5g.preSharedKey).isEqualTo("secretsecret");
        assertThat(wifiConfig_5g.apBand).isEqualTo(WifiConfiguration.AP_BAND_5GHZ);
        assertThat(wifiConfig_5g.apChannel).isEqualTo(149);
        assertThat(wifiConfig_5g.hiddenSSID).isEqualTo(true);

        SoftApConfiguration softApConfig_2g5g = new SoftApConfiguration.Builder()
                .setPassphrase("secretsecret",
                        SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                .setBand(SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ)
                .setHiddenSsid(true)
                .build();
        WifiConfiguration wifiConfig_2g5g = softApConfig_2g5g.toWifiConfiguration();
        assertThat(wifiConfig_2g5g.getAuthType()).isEqualTo(WifiConfiguration.KeyMgmt.WPA2_PSK);
        assertThat(wifiConfig_2g5g.preSharedKey).isEqualTo("secretsecret");
        assertThat(wifiConfig_2g5g.apBand).isEqualTo(WifiConfiguration.AP_BAND_ANY);
        assertThat(wifiConfig_2g5g.apChannel).isEqualTo(0);
        assertThat(wifiConfig_2g5g.hiddenSSID).isEqualTo(true);

        SoftApConfiguration softApConfig_sae_transition = new SoftApConfiguration.Builder()
                .setPassphrase("secretsecret",
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION)
                .build();

        WifiConfiguration wifiConfig_sae_transition =
                softApConfig_sae_transition.toWifiConfiguration();
        assertThat(wifiConfig_sae_transition.getAuthType())
                .isEqualTo(WifiConfiguration.KeyMgmt.WPA2_PSK);
        assertThat(wifiConfig_sae_transition.preSharedKey).isEqualTo("secretsecret");
    }

    @Test
    public void testDualBands() {
        assumeTrue(SdkLevel.isAtLeastS());
        int[] dual_bands = new int[2];
        dual_bands[0] = SoftApConfiguration.BAND_2GHZ;
        dual_bands[1] = SoftApConfiguration.BAND_5GHZ;
        SoftApConfiguration dual_bands_config = new SoftApConfiguration.Builder()
                .setSsid("ssid")
                .setBands(dual_bands)
                .build();
        assertTrue(Arrays.equals(dual_bands, dual_bands_config.getBands()));
        assertThat(dual_bands_config.getBand()).isEqualTo(SoftApConfiguration.BAND_2GHZ);
    }

    @Test
    public void testDualChannels() {
        assumeTrue(SdkLevel.isAtLeastS());
        int[] expected_dual_bands = new int[2];
        expected_dual_bands[0] = SoftApConfiguration.BAND_2GHZ;
        expected_dual_bands[1] = SoftApConfiguration.BAND_5GHZ;
        SparseIntArray dual_channels = new SparseIntArray(2);
        dual_channels.put(SoftApConfiguration.BAND_5GHZ, 149);
        dual_channels.put(SoftApConfiguration.BAND_2GHZ, 2);
        SoftApConfiguration dual_channels_config = new SoftApConfiguration.Builder()
                .setSsid("ssid")
                .setChannels(dual_channels)
                .build();
        assertTrue(Arrays.equals(expected_dual_bands, dual_channels_config.getBands()));
        assertThat(dual_channels_config.getBand()).isEqualTo(SoftApConfiguration.BAND_2GHZ);
        assertTrue(dual_channels.toString().equals(dual_channels_config.getChannels().toString()));
        assertThat(dual_channels_config.getChannel()).isEqualTo(2);

        // Test different parameters.
        dual_channels.clear();
        dual_channels.put(SoftApConfiguration.BAND_5GHZ, 149);
        dual_channels.put(SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ, 0);
        expected_dual_bands[0] = SoftApConfiguration.BAND_5GHZ;
        expected_dual_bands[1] = SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ;
        dual_channels_config = new SoftApConfiguration.Builder()
                .setSsid("ssid")
                .setChannels(dual_channels)
                .build();
        assertTrue(Arrays.equals(expected_dual_bands, dual_channels_config.getBands()));
        assertThat(dual_channels_config.getBand()).isEqualTo(SoftApConfiguration.BAND_5GHZ);
        assertTrue(dual_channels.toString().equals(dual_channels_config.getChannels().toString()));
        assertThat(dual_channels_config.getChannel()).isEqualTo(149);
    }

    @Test
    public void testInvalidBandWhenSetBands() {
        assumeTrue(SdkLevel.isAtLeastS());
        boolean isIllegalArgumentExceptionHappened = false;
        int[] dual_bands = new int[2];
        dual_bands[0] = SoftApConfiguration.BAND_2GHZ;
        dual_bands[1] = -1;
        try {
            SoftApConfiguration dual_channels_config = new SoftApConfiguration.Builder()
                    .setSsid("ssid")
                    .setBands(dual_bands)
                    .build();
            isIllegalArgumentExceptionHappened = false;
        } catch (IllegalArgumentException iae) {
            isIllegalArgumentExceptionHappened = true;
        }
        assertTrue(isIllegalArgumentExceptionHappened);

        try {
            SoftApConfiguration dual_channels_config = new SoftApConfiguration.Builder()
                    .setSsid("ssid")
                    .setBands(new int[0])
                    .build();
            isIllegalArgumentExceptionHappened = false;
        } catch (IllegalArgumentException iae) {
            isIllegalArgumentExceptionHappened = true;
        }
        assertTrue(isIllegalArgumentExceptionHappened);

        try {
            SoftApConfiguration dual_channels_config = new SoftApConfiguration.Builder()
                    .setSsid("ssid")
                    .setBands(new int[3])
                    .build();
            isIllegalArgumentExceptionHappened = false;
        } catch (IllegalArgumentException iae) {
            isIllegalArgumentExceptionHappened = true;
        }
        assertTrue(isIllegalArgumentExceptionHappened);
    }

    @Test
    public void testInvalidConfigWhenSetChannels() {
        assumeTrue(SdkLevel.isAtLeastS());
        boolean isIllegalArgumentExceptionHappened = false;
        SparseIntArray invalid_channels = new SparseIntArray();
        try {
            SoftApConfiguration zero_channels_config = new SoftApConfiguration.Builder()
                    .setSsid("ssid")
                    .setChannels(invalid_channels)
                    .build();
            isIllegalArgumentExceptionHappened = false;
        } catch (IllegalArgumentException iae) {
            isIllegalArgumentExceptionHappened = true;
        }
        assertTrue(isIllegalArgumentExceptionHappened);

        try {
            invalid_channels.clear();
            invalid_channels.put(SoftApConfiguration.BAND_2GHZ, 2);
            invalid_channels.put(SoftApConfiguration.BAND_5GHZ, 11);
            SoftApConfiguration invalid_band_channels_config = new SoftApConfiguration.Builder()
                    .setSsid("ssid")
                    .setChannels(invalid_channels)
                    .build();
            isIllegalArgumentExceptionHappened = false;
        } catch (IllegalArgumentException iae) {
            isIllegalArgumentExceptionHappened = true;
        }
        assertTrue(isIllegalArgumentExceptionHappened);

        try {
            invalid_channels.clear();
            invalid_channels.put(SoftApConfiguration.BAND_2GHZ, 2);
            invalid_channels.put(SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ,
                    149);
            SoftApConfiguration invalid_dual_channels_config = new SoftApConfiguration.Builder()
                    .setSsid("ssid")
                    .setChannels(invalid_channels)
                    .build();
            isIllegalArgumentExceptionHappened = false;
        } catch (IllegalArgumentException iae) {
            isIllegalArgumentExceptionHappened = true;
        }
        assertTrue(isIllegalArgumentExceptionHappened);

        try {
            invalid_channels.clear();
            invalid_channels.put(SoftApConfiguration.BAND_2GHZ, 2);
            invalid_channels.put(SoftApConfiguration.BAND_5GHZ, 149);
            invalid_channels.put(SoftApConfiguration.BAND_6GHZ, 2);
            SoftApConfiguration three_channels_config = new SoftApConfiguration.Builder()
                    .setSsid("ssid")
                    .setChannels(invalid_channels)
                    .build();
            isIllegalArgumentExceptionHappened = false;
        } catch (IllegalArgumentException iae) {
            isIllegalArgumentExceptionHappened = true;
        }
        assertTrue(isIllegalArgumentExceptionHappened);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidConfigWhenSet60GhzChannels() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        SparseIntArray invalid_channels = new SparseIntArray();
        invalid_channels.put(SoftApConfiguration.BAND_60GHZ, 99);
        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .setSsid("ssid")
                .setChannels(invalid_channels)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidVendorElementsID() {
        assumeTrue(SdkLevel.isAtLeastT());
        SoftApConfiguration original = new SoftApConfiguration.Builder()
                .setVendorElements(TEST_TWO_VENDOR_ELEMENTS_INVALID)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidVendorElementsDuplicate() {
        assumeTrue(SdkLevel.isAtLeastT());
        List<ScanResult.InformationElement> dupElements = new ArrayList<>(TEST_TWO_VENDOR_ELEMENTS);
        dupElements.addAll(TEST_TWO_VENDOR_ELEMENTS);
        SoftApConfiguration original = new SoftApConfiguration.Builder()
                .setVendorElements(dupElements)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsExceptionWhenBssidSetButMacRandomizationSettingIsPersistent() {
        assumeTrue(SdkLevel.isAtLeastS());
        MacAddress testBssid = MacAddress.fromString(TEST_BSSID);
        SoftApConfiguration config_setBssidAfterSetMacRandomizationSettingToPersistent =
                new SoftApConfiguration.Builder()
                .setMacRandomizationSetting(SoftApConfiguration.RANDOMIZATION_PERSISTENT)
                .setBssid(testBssid)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsExceptionWhenBssidSetButMacRandomizationSettingIsNonPersistent() {
        assumeTrue(SdkLevel.isAtLeastS());
        MacAddress testBssid = MacAddress.fromString(TEST_BSSID);
        SoftApConfiguration config_setBssidAfterSetMacRandomizationSettingToNonPersistent =
                new SoftApConfiguration.Builder()
                .setMacRandomizationSetting(SoftApConfiguration.RANDOMIZATION_NON_PERSISTENT)
                .setBssid(testBssid)
                .build();
    }

    @Test
    public void testSetBssidSucceededWWithDisableMacRandomizationSetting() {
        assumeTrue(SdkLevel.isAtLeastS());
        MacAddress testBssid = MacAddress.fromString(TEST_BSSID);
        SoftApConfiguration config_setBssidAfterSetMacRandomizationSettingToNone =
                new SoftApConfiguration.Builder()
                .setMacRandomizationSetting(SoftApConfiguration.RANDOMIZATION_NONE)
                .setBssid(testBssid)
                .build();
        assertEquals(config_setBssidAfterSetMacRandomizationSettingToNone.getBssid(),
                testBssid);
    }

    @Test
    public void testSetAllowedAcsChannels() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .build();
        assertEquals(0, config.getAllowedAcsChannels(SoftApConfiguration.BAND_2GHZ).length);
        assertEquals(0, config.getAllowedAcsChannels(SoftApConfiguration.BAND_5GHZ).length);
        assertEquals(0, config.getAllowedAcsChannels(SoftApConfiguration.BAND_6GHZ).length);

        int[] channels2g = {1, 6, 11};
        int[] channels5g = {36, 149, 136};
        int[] channels6g = {1, 2, 3};

        config = new SoftApConfiguration.Builder()
                .setAllowedAcsChannels(SoftApConfiguration.BAND_2GHZ, channels2g)
                .setAllowedAcsChannels(SoftApConfiguration.BAND_5GHZ, channels5g)
                .setAllowedAcsChannels(SoftApConfiguration.BAND_6GHZ, channels6g)
                .build();
        assertArrayEquals(channels2g, config.getAllowedAcsChannels(SoftApConfiguration.BAND_2GHZ));
        assertArrayEquals(channels5g, config.getAllowedAcsChannels(SoftApConfiguration.BAND_5GHZ));
        assertArrayEquals(channels6g, config.getAllowedAcsChannels(SoftApConfiguration.BAND_6GHZ));
    }

    @Test
    public void testSetAllowedAcsChannelsInvalidValues() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        int[] channels2g = {1, 6, 11, 50};
        int[] channels5g = {36, 7, 149, 800};
        int[] channels6g = {1, 2, -1, 3};

        assertThrows(IllegalArgumentException.class,
                () -> new SoftApConfiguration.Builder()
                        .setAllowedAcsChannels(SoftApConfiguration.BAND_2GHZ, channels2g));
        assertThrows(IllegalArgumentException.class,
                () -> new SoftApConfiguration.Builder()
                        .setAllowedAcsChannels(SoftApConfiguration.BAND_5GHZ, channels5g));
        assertThrows(IllegalArgumentException.class,
                () -> new SoftApConfiguration.Builder()
                        .setAllowedAcsChannels(SoftApConfiguration.BAND_6GHZ, channels6g));
    }

    @Test
    public void testMaxChannelBandwidth() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        SoftApConfiguration config = new SoftApConfiguration.Builder()
                .build();
        assertEquals(SoftApInfo.CHANNEL_WIDTH_AUTO, config.getMaxChannelBandwidth());

        config = new SoftApConfiguration.Builder()
                .setMaxChannelBandwidth(SoftApInfo.CHANNEL_WIDTH_20MHZ)
                .build();
        assertEquals(SoftApInfo.CHANNEL_WIDTH_20MHZ, config.getMaxChannelBandwidth());

        config = new SoftApConfiguration.Builder()
                .setMaxChannelBandwidth(SoftApInfo.CHANNEL_WIDTH_AUTO)
                .build();
        assertEquals(SoftApInfo.CHANNEL_WIDTH_AUTO, config.getMaxChannelBandwidth());

        // Invalid value
        assertThrows(IllegalArgumentException.class,
                () -> new SoftApConfiguration.Builder()
                        .setMaxChannelBandwidth(SoftApInfo.CHANNEL_WIDTH_80MHZ_PLUS_MHZ));
    }

    @Test
    public void testVendorDataValid() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());

        // Default value should be an empty list.
        SoftApConfiguration config = new SoftApConfiguration.Builder().build();
        List<OuiKeyedData> retrievedVendorData = config.getVendorData();
        assertNotNull(retrievedVendorData);
        assertEquals(0, retrievedVendorData.size());

        List<OuiKeyedData> mockVendorData = Arrays.asList(mock(OuiKeyedData.class));
        config = new SoftApConfiguration.Builder().setVendorData(mockVendorData).build();
        assertTrue(mockVendorData.equals(config.getVendorData()));
    }

    @Test
    public void testVendorDataInvalid() {
        assumeTrue(SdkLevel.isAtLeastV());
        SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder();
        // Vendor data must be non-null, if provided.
        assertThrows(IllegalArgumentException.class, () -> builder.setVendorData(null));
    }

    @Test
    public void testVendorDataParcelUnparcel() {
        assumeTrue(SdkLevel.isAtLeastV());

        int oui = 0x00112233;
        String fieldKey = "fieldKey";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(fieldKey, 12345);

        OuiKeyedData ouiKeyedData = new OuiKeyedData.Builder(oui, bundle).build();
        List<OuiKeyedData> vendorData = Arrays.asList(ouiKeyedData);
        SoftApConfiguration config =
                new SoftApConfiguration.Builder().setVendorData(vendorData).build();

        SoftApConfiguration unparceled = parcelUnparcel(config);
        assertThat(unparceled).isNotSameInstanceAs(config);
        assertThat(unparceled).isEqualTo(config);
        assertThat(unparceled.hashCode()).isEqualTo(config.hashCode());
        OuiKeyedData unparceledOuiKeyedData = unparceled.getVendorData().get(0);
        assertEquals(bundle.getInt(fieldKey), unparceledOuiKeyedData.getData().getInt(fieldKey));
    }
}
