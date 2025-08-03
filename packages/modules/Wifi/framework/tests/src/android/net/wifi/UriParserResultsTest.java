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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.net.module.util.MacAddressUtils;

import org.junit.Test;

/** Unit tests for {@link android.net.wifi.UriParserResults}. */
@SmallTest
public class UriParserResultsTest {
    private static final String TEST_PUBLIC_KEY = "testPublicKey";
    private static final String TEST_INFORMATION = "testInformation";

    WifiConfiguration generateTestWifiConfig() {
        WifiConfiguration config = new WifiConfiguration();
        final String mSsid = "\"TestAP\"";
        final String bSsid = MacAddressUtils.createRandomUnicastAddress().toString();
        config.SSID = mSsid;
        config.BSSID = bSsid;
        return config;
    }

    private void testParcelOperation(UriParserResults testResult) throws Exception {
        Parcel parcelW = Parcel.obtain();
        testResult.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        UriParserResults fromParcel = UriParserResults.CREATOR.createFromParcel(parcelR);

        assertEquals(testResult, fromParcel);
        assertEquals(testResult.hashCode(), fromParcel.hashCode());
    }

    /** Verifies parcel serialization/deserialization. */
    @Test
    public void testParcelOperation() throws Exception {
        final WifiConfiguration testWifiConfig = generateTestWifiConfig();
        testParcelOperation(
                new UriParserResults(
                        UriParserResults.URI_SCHEME_ZXING_WIFI_NETWORK_CONFIG,
                        null,
                        null,
                        testWifiConfig));
        testParcelOperation(
                new UriParserResults(
                        UriParserResults.URI_SCHEME_DPP,
                        TEST_PUBLIC_KEY,
                        TEST_INFORMATION,
                        null));
    }

    @Test
    public void testGetXXXMethods() throws Exception {
        final WifiConfiguration testWifiConfig = generateTestWifiConfig();
        UriParserResults testResultZxing =
                new UriParserResults(
                        UriParserResults.URI_SCHEME_ZXING_WIFI_NETWORK_CONFIG,
                        null,
                        null,
                        testWifiConfig);
        assertEquals(
                testResultZxing.getUriScheme(),
                UriParserResults.URI_SCHEME_ZXING_WIFI_NETWORK_CONFIG);
        assertNull(testResultZxing.getPublicKey());
        assertNull(testResultZxing.getInformation());
        assertEquals(testWifiConfig.toString(), testResultZxing.getWifiConfiguration().toString());

        UriParserResults testResultDpp =
                new UriParserResults(
                        UriParserResults.URI_SCHEME_DPP,
                        TEST_PUBLIC_KEY,
                        TEST_INFORMATION,
                        null);
        assertEquals(testResultDpp.getUriScheme(), UriParserResults.URI_SCHEME_DPP);
        assertEquals(testResultDpp.getPublicKey(), TEST_PUBLIC_KEY);
        assertEquals(testResultDpp.getInformation(), TEST_INFORMATION);
        assertNull(testResultDpp.getWifiConfiguration());
    }
}
