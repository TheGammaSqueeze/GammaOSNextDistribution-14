/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.MacAddress;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiConfiguration;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiConfigurationTestUtil;
import com.android.server.wifi.WifiGlobals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

/**
 * Unit tests for {@link com.android.server.wifi.util.NativeUtil}.
 */
@SmallTest
public class NativeUtilTest extends WifiBaseTest {
    /**
     * Test that parsing a typical colon-delimited MAC address works.
     */
    @Test
    public void testMacAddressToByteArray() throws Exception {
        assertArrayEquals(new byte[]{0x61, 0x52, 0x43, 0x34, 0x25, 0x16},
                NativeUtil.macAddressToByteArray("61:52:43:34:25:16"));
    }

    /**
     * Test that parsing an empty MAC address works.
     */
    @Test
    public void testEmptyMacAddressToByteArray() throws Exception {
        assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0},
                NativeUtil.macAddressToByteArray(""));
        assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0},
                NativeUtil.macAddressToByteArray(null));
    }

    /**
     * Test that converting a colon delimited MAC address to android.net.MacAddress works. Also test
     * invalid input exception is handled by NativeUtil#getMacAddressOrNull() and returns 'null'.
     */
    @Test
    public void testGetMacAddressOrNull() throws Exception {
        String macAddressStr = "11:22:33:44:55:66";
        assertEquals(MacAddress.fromString(macAddressStr),
                NativeUtil.getMacAddressOrNull(macAddressStr));
        assertNull(NativeUtil.getMacAddressOrNull(":44:55:66"));
        assertNull(NativeUtil.getMacAddressOrNull(null));
    }

    /**
     * Test that conversion of byte array of mac address to typical colon-delimited MAC address
     * works.
     */
    @Test
    public void testByteArrayToMacAddress() throws Exception {
        assertEquals("61:52:43:34:25:16",
                NativeUtil.macAddressFromByteArray(new byte[]{0x61, 0x52, 0x43, 0x34, 0x25, 0x16}));
    }

    /**
     * Test that parsing a typical colon-delimited MAC OUI address works.
     */
    @Test
    public void testMacAddressOuiToByteArray() throws Exception {
        assertArrayEquals(new byte[]{0x61, 0x52, 0x43},
                NativeUtil.macAddressOuiToByteArray("61:52:43"));
    }

    /**
     * Test that parsing a hex string to byte array works.
     */
    @Test
    public void testHexStringToByteArray() throws Exception {
        assertArrayEquals(new byte[]{0x45, 0x12, 0x23, 0x34},
                NativeUtil.hexStringToByteArray("45122334"));
    }

    /**
     * Test that conversion of byte array to hexstring works.
     */
    @Test
    public void testHexStringFromByteArray() throws Exception {
        assertEquals("45122334",
                NativeUtil.hexStringFromByteArray(new byte[]{0x45, 0x12, 0x23, 0x34}));
    }

    /**
     * Test that conversion of ssid bytes to quoted ASCII string ssid works.
     */
    @Test
    public void testAsciiSsidDecode() throws Exception {
        assertEquals(
                new ArrayList<>(
                        Arrays.asList((byte) 's', (byte) 's', (byte) 'i', (byte) 'd', (byte) '_',
                                (byte) 't', (byte) 'e', (byte) 's', (byte) 't', (byte) '1',
                                (byte) '2', (byte) '3')),
                NativeUtil.decodeSsid("\"ssid_test123\""));
    }

    /**
     * Test that conversion of ssid bytes to quoted UTF8 string ssid works.
     */
    @Test
    public void testUtf8SsidDecode() throws Exception {
        assertEquals(
                new ArrayList<>(
                        Arrays.asList((byte) 0x41, (byte) 0x6e, (byte) 0x64, (byte) 0x72,
                                (byte) 0x6f, (byte) 0x69, (byte) 0x64, (byte) 0x41, (byte) 0x50,
                                (byte) 0xe3, (byte) 0x81, (byte) 0x8f, (byte) 0xe3, (byte) 0x81,
                                (byte) 0xa0, (byte) 0xe3, (byte) 0x81, (byte) 0x95, (byte) 0xe3,
                                (byte) 0x81, (byte) 0x84)),
                NativeUtil.decodeSsid("\"AndroidAPください\""));
    }

    /**
     * Test that conversion of non utf-8 SSID string to bytes fail.
     */
    @Test
    public void testNonUtf8SsidDecodeFails() throws Exception {
        try {
            NativeUtil.decodeSsid("\"\ud800\"");
            fail("Expected ssid decode to fail");
        } catch (IllegalArgumentException e) {
        }
    }

    /**
     * Test that conversion of SSID string with len > 32 to bytes fail.
     */
    @Test
    public void testLargeSsidDecodeFails() throws Exception {
        try {
            NativeUtil.decodeSsid("\"asdrewqdfgyuiopldsqwertyuiolhdergcv\"");
            fail("Expected ssid decode to fail");
        } catch (IllegalArgumentException e) {
        }
    }

    /**
     * Test that conversion of ssid bytes to hex string ssid works.
     */
    @Test
    public void testNonSsidDecode() throws Exception {
        assertEquals(
                new ArrayList<>(
                        Arrays.asList((byte) 0xf5, (byte) 0xe4, (byte) 0xab, (byte) 0x78,
                                (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a)),
                NativeUtil.decodeSsid("f5e4ab78ab3432439a"));
    }

    /**
     * Test that conversion of ssid bytes to quoted string ssid works.
     */
    @Test
    public void testSsidEncode() throws Exception {
        assertEquals(
                "\"ssid_test123\"",
                NativeUtil.encodeSsid(new ArrayList<>(
                        Arrays.asList((byte) 's', (byte) 's', (byte) 'i', (byte) 'd', (byte) '_',
                                (byte) 't', (byte) 'e', (byte) 's', (byte) 't', (byte) '1',
                                (byte) '2', (byte) '3'))));
    }

    /**
     * Test that conversion of byte array to hex string works when the byte array contains 0.
     */
    @Test
    public void testNullCharInSsidEncode() throws Exception {
        assertEquals(
                "007369645f74657374313233",
                NativeUtil.encodeSsid(new ArrayList<>(
                        Arrays.asList((byte) 0x00, (byte) 's', (byte) 'i', (byte) 'd', (byte) '_',
                                (byte) 't', (byte) 'e', (byte) 's', (byte) 't', (byte) '1',
                                (byte) '2', (byte) '3'))));
    }

    /**
     * Test that conversion of byte array to hex string ssid works.
     */
    @Test
    public void testNonSsidEncode() throws Exception {
        assertEquals(
                "f5e4ab78ab3432439a",
                NativeUtil.encodeSsid(new ArrayList<>(
                        Arrays.asList((byte) 0xf5, (byte) 0xe4, (byte) 0xab, (byte) 0x78,
                                (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a))));
    }

    /**
     * Test that conversion of SSID bytes with len > 32 to string fail.
     */
    @Test
    public void testLargeSsidEncodeFails() throws Exception {
        try {
            NativeUtil.encodeSsid(new ArrayList<>(
                    Arrays.asList((byte) 0xf5, (byte) 0xe4, (byte) 0xab, (byte) 0x78, (byte) 0x78,
                            (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a,
                            (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a,
                            (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a,
                            (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a,
                            (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a,
                            (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a)));
            fail("Expected ssid encode to fail");
        } catch (IllegalArgumentException e) {
        }
    }

    /**
     * Test that parsing of quoted SSID to byte array and vice versa works.
     */
    @Test
    public void testSsidEncodeDecode() throws Exception {
        String ssid = "\"ssid_test123\"";
        assertEquals(ssid, NativeUtil.encodeSsid(NativeUtil.decodeSsid(ssid)));
    }

    /**
     * Test that the enclosing quotes are removed properly.
     */
    @Test
    public void testRemoveEnclosingQuotes() throws Exception {
        assertEquals("abcdefgh", NativeUtil.removeEnclosingQuotes("\"abcdefgh\""));
        assertEquals("abcdefgh", NativeUtil.removeEnclosingQuotes("abcdefgh"));
    }

    /**
     * Test PMF is disable when SAE is selected when SAE auto-upgrade offload is supported.
     */
    @Test
    public void testPmfIsDisableWhenPmfEnabledAndAutoUpgradeOffloadSupported() throws Exception {
        WifiGlobals globals = mock(WifiGlobals.class);
        when(globals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(true);
        assertFalse(NativeUtil.getOptimalPmfSettingForConfig(
                    WifiConfigurationTestUtil.createPskSaeNetwork(),
                    true, globals));
    }

    /**
     * Test pairwise & group cihpers are merged when SAE auto-upgrade offload is supported.
     */
    @Test
    public void testPairwiseCiphersMergedForSaeAutoUpgradeOffloadSupported() throws Exception {
        WifiGlobals globals = mock(WifiGlobals.class);
        when(globals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(true);

        SecurityParams saeParams = SecurityParams.createSecurityParamsBySecurityType(
                WifiConfiguration.SECURITY_TYPE_SAE);
        BitSet expectedPairwiseCiphers = new BitSet();
        expectedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        expectedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        expectedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.GCMP_128);
        expectedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.GCMP_256);

        BitSet optimalPairwiseCiphers = NativeUtil.getOptimalPairwiseCiphersForConfig(
                WifiConfigurationTestUtil.createPskSaeNetwork(),
                saeParams.getAllowedPairwiseCiphers(), globals);
        assertEquals(expectedPairwiseCiphers, optimalPairwiseCiphers);

        BitSet expectedGroupCiphers = new BitSet();
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.GCMP_128);
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.GCMP_256);
        BitSet optimalGroupCiphers = NativeUtil.getOptimalGroupCiphersForConfig(
                WifiConfigurationTestUtil.createPskSaeNetwork(),
                saeParams.getAllowedGroupCiphers(), globals);
        assertEquals(expectedGroupCiphers, optimalGroupCiphers);
    }

    /**
     * Test pairwise and group cihpers are not merged when SAE auto-upgrade offload
     * is not supported.
     */
    @Test
    public void testPairwiseCiphersNotMergedForSaeAutoUpgradeOffloadNotSupported()
            throws Exception {
        WifiGlobals globals = mock(WifiGlobals.class);
        when(globals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(false);

        SecurityParams saeParams = SecurityParams.createSecurityParamsBySecurityType(
                WifiConfiguration.SECURITY_TYPE_SAE);
        BitSet expectedPairwiseCiphers = new BitSet();
        expectedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        expectedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.GCMP_128);
        expectedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.GCMP_256);

        BitSet optimalPairwiseCiphers = NativeUtil.getOptimalPairwiseCiphersForConfig(
                WifiConfigurationTestUtil.createPskSaeNetwork(),
                saeParams.getAllowedPairwiseCiphers(), globals);
        assertEquals(expectedPairwiseCiphers, optimalPairwiseCiphers);

        BitSet expectedGroupCiphers = new BitSet();
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.GCMP_128);
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.GCMP_256);
        BitSet optimalGroupCiphers = NativeUtil.getOptimalGroupCiphersForConfig(
                WifiConfigurationTestUtil.createPskSaeNetwork(),
                saeParams.getAllowedGroupCiphers(), globals);
        assertEquals(expectedGroupCiphers, optimalGroupCiphers);
    }
}
