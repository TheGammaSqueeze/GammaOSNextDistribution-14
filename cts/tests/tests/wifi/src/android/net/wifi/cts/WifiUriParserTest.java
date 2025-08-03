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

package android.net.wifi.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.net.wifi.UriParserResults;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiUriParser;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.text.TextUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.wifi.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
public class WifiUriParserTest extends WifiJUnit4TestBase {

    private void testZxingUriParserAndVerify(String uri,
            String expectedSsid, int expectedAuthType, String expectedPassword) throws Exception {
        UriParserResults result = WifiUriParser.parseUri(uri);
        assertNotNull(result);
        assertEquals(result.getUriScheme(), UriParserResults.URI_SCHEME_ZXING_WIFI_NETWORK_CONFIG);
        WifiConfiguration config = result.getWifiConfiguration();
        assertNotNull(config);
        assertEquals(config.SSID, expectedSsid);
        if (TextUtils.isEmpty(expectedPassword)) {
            assertTrue(TextUtils.isEmpty(config.preSharedKey));
        } else {
            assertEquals(config.preSharedKey, expectedPassword);
        }
        assertThat(config.getAuthType()).isEqualTo(expectedAuthType);
        assertNull(result.getPublicKey());
        assertNull(result.getInformation());
    }

    private void testDppUriParserAndVerify(String uri, String expectedPublicKey,
            String expectedInformation) throws Exception {
        UriParserResults result = WifiUriParser.parseUri(uri);
        assertNotNull(result);
        assertEquals(result.getUriScheme(), UriParserResults.URI_SCHEME_DPP);
        assertNull(result.getWifiConfiguration());
        assertEquals(result.getPublicKey(), expectedPublicKey);
        assertEquals(result.getInformation(), expectedInformation);
    }

    /**
     * Tests {@link WifiUriParser#parseUri(String)}.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testUriParser() throws Exception {
        // Test zxing open network
        testZxingUriParserAndVerify("WIFI:S:testAbC;T:nopass",
                "\"testAbC\"", WifiConfiguration.KeyMgmt.NONE, "");
        // Test zxing wpa2 network with specific password
        testZxingUriParserAndVerify("WIFI:S:anotherone;T:WPA;P:3#=3j9asicla",
                "\"anotherone\"", WifiConfiguration.KeyMgmt.WPA_PSK, "\"3#=3j9asicla\"");
        // Test DPP
        testDppUriParserAndVerify("DPP:C:81/1;I:Easy_Connect_Demo;K:TestPublicKey;;",
                "TestPublicKey", "Easy_Connect_Demo");
        // Test onError
        assertThrows("Invalid uri should trigger exception", IllegalArgumentException.class,
                    () -> WifiUriParser.parseUri("invalidUri"));
    }
}
