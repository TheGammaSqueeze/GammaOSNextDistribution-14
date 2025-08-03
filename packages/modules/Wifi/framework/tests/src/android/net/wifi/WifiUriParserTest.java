/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import androidx.test.filters.SmallTest;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.util.List;

/** Unit tests for {@link com.android.server.wifi.WifiUriParser}. */
@SmallTest
public class WifiUriParserTest {

    private static final String TEST_DPP_INFORMATION = "Easy_Connect_Demo";
    private static final String TEST_DPP_PUBLIC_KEY = "MDkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDIg"
            + "ACDmtXD1Sz6/5B4YRdmTkbkkFLDwk8f0yRnfm1Gokpx/0=";
    private static final String TEST_DPP_URI = "DPP:C:81/1;I:" + TEST_DPP_INFORMATION
            + ";K:" + TEST_DPP_PUBLIC_KEY + ";;";

    private void verifyZxParsing(
            UriParserResults uri,
            String expectedSSID,
            int expectedAuthType,
            List<SecurityParams> expectedSecurityParamsList,
            String expectedPreShareKey,
            boolean isWep) {
        assertNotNull(uri);
        WifiConfiguration config = uri.getWifiConfiguration();
        assertNotNull(config);
        assertThat(config.SSID).isEqualTo(expectedSSID);
        assertThat(config.getAuthType()).isEqualTo(expectedAuthType);
        if (isWep) {
            assertThat(config.wepKeys[0]).isEqualTo(expectedPreShareKey);
        } else {
            assertThat(config.preSharedKey).isEqualTo(expectedPreShareKey);
        }
        List<SecurityParams> configSecurityParamsList = config.getSecurityParamsList();
        assertEquals(expectedSecurityParamsList, configSecurityParamsList);
        assertNull(uri.getPublicKey());
        assertNull(uri.getInformation());
        assertEquals(UriParserResults.URI_SCHEME_ZXING_WIFI_NETWORK_CONFIG, uri.getUriScheme());
    }

    @Test
    public void testZxParsing() {
        // Test no password
        List<SecurityParams> expectedSecurityParamsList =
                ImmutableList.of(
                        SecurityParams.createSecurityParamsBySecurityType(
                                WifiConfiguration.SECURITY_TYPE_OPEN),
                        SecurityParams.createSecurityParamsBySecurityType(
                                WifiConfiguration.SECURITY_TYPE_OWE));
        UriParserResults uri = WifiUriParser.parseUri("WIFI:S:testAbC;T:nopass");
        verifyZxParsing(
                uri,
                "\"testAbC\"",
                WifiConfiguration.KeyMgmt.NONE,
                expectedSecurityParamsList,
                null,
                false);
        // invalid code but it should work.
        uri = WifiUriParser.parseUri("WIFI:S:testAbC; T:nopass");
        verifyZxParsing(
                uri,
                "\"testAbC\"",
                WifiConfiguration.KeyMgmt.NONE,
                expectedSecurityParamsList,
                null,
                false);

        // Test WEP
        expectedSecurityParamsList =
                ImmutableList.of(
                        SecurityParams.createSecurityParamsBySecurityType(
                                WifiConfiguration.SECURITY_TYPE_WEP));
        uri = WifiUriParser.parseUri("WIFI:S:reallyLONGone;T:WEP;P:somepasswo#%^**123rd");
        verifyZxParsing(
                uri,
                "\"reallyLONGone\"",
                WifiConfiguration.KeyMgmt.NONE,
                expectedSecurityParamsList,
                "\"somepasswo#%^**123rd\"",
                true);
        // invalid code but it should work.
        uri = WifiUriParser.parseUri("WIFI:S:reallyLONGone;T:WEP; P:somepassword");
        verifyZxParsing(
                uri,
                "\"reallyLONGone\"",
                WifiConfiguration.KeyMgmt.NONE,
                expectedSecurityParamsList,
                "\"somepassword\"",
                true);

        // Test WPA
        expectedSecurityParamsList =
                ImmutableList.of(
                        SecurityParams.createSecurityParamsBySecurityType(
                                WifiConfiguration.SECURITY_TYPE_PSK));
        uri = WifiUriParser.parseUri("WIFI:S:anotherone;T:WPA;P:3#=3j9asicla");
        verifyZxParsing(
                uri,
                "\"anotherone\"",
                WifiConfiguration.KeyMgmt.WPA_PSK,
                expectedSecurityParamsList,
                "\"3#=3j9asicla\"",
                false);
        // invalid code but it should work.
        uri = WifiUriParser.parseUri("WIFI: S:anotherone;T:WPA;P:abcdefghihklmn");
        verifyZxParsing(
                uri,
                "\"anotherone\"",
                WifiConfiguration.KeyMgmt.WPA_PSK,
                expectedSecurityParamsList,
                "\"abcdefghihklmn\"",
                false);

        // Test SAE
        expectedSecurityParamsList =
                ImmutableList.of(
                        SecurityParams.createSecurityParamsBySecurityType(
                                WifiConfiguration.SECURITY_TYPE_SAE));
        uri = WifiUriParser.parseUri("WIFI:S:xx;T:SAE;P:a");
        verifyZxParsing(
                uri,
                "\"xx\"",
                WifiConfiguration.KeyMgmt.SAE,
                expectedSecurityParamsList,
                "\"a\"",
                false);
        // invalid code but it should work.
        uri = WifiUriParser.parseUri("WIFI: S:xx; T:SAE;   P:a");
        verifyZxParsing(
                uri,
                "\"xx\"",
                WifiConfiguration.KeyMgmt.SAE,
                expectedSecurityParamsList,
                "\"a\"",
                false);
    }

    @Test
    public void testDppParsing() {
        UriParserResults uri = WifiUriParser.parseUri(TEST_DPP_URI);
        assertEquals(UriParserResults.URI_SCHEME_DPP, uri.getUriScheme());
        assertEquals(TEST_DPP_INFORMATION, uri.getInformation());
        assertEquals(TEST_DPP_PUBLIC_KEY, uri.getPublicKey());
        assertNull(uri.getWifiConfiguration());
    }

    @Test
    public void testInvalidUriParsing() {
        assertThrows(IllegalArgumentException.class,
                () -> WifiUriParser.parseUri("Invalid Uri"));
        // Empty SSID
        assertThrows(IllegalArgumentException.class,
                () -> WifiUriParser.parseUri("WIFI:S:;T:nopass"));
        // Empty passphrase
        assertThrows(IllegalArgumentException.class,
                () -> WifiUriParser.parseUri("WIFI: S:xx; T:SAE;   P:"));
    }
}
