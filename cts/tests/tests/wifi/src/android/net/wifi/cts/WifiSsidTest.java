/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.net.wifi.WifiSsid;
import android.os.Parcel;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class WifiSsidTest extends WifiJUnit3TestBase {

    private static final String TEST_SSID_UTF_8 = "Test SSID";
    private static final String TEST_SSID_UTF_8_QUOTED = "\"" + TEST_SSID_UTF_8 + "\"";
    private static final byte[] TEST_SSID_UTF_8_BYTES =
            TEST_SSID_UTF_8.getBytes(StandardCharsets.UTF_8);
    private static final String TEST_SSID_UTF_8_HEX = "546573742053534944";

    private static final byte[] TEST_SSID_NON_UTF_8_BYTES =
            "服務集識別碼".getBytes(Charset.forName("GBK"));
    private static final String TEST_SSID_NON_UTF_8_HEX = "B7FE84D5BCAFD7528465B461";

    /**
     * Verify the behavior of fromByteArray()
     */
    public void testFromByteArray() {
        WifiSsid wifiSsidUtf8 = WifiSsid.fromBytes(TEST_SSID_UTF_8_BYTES);
        assertThat(wifiSsidUtf8).isNotNull();
        assertThat(wifiSsidUtf8.getBytes()).isEqualTo(TEST_SSID_UTF_8_BYTES);

        WifiSsid wifiSsidNonUtf8 = WifiSsid.fromBytes(TEST_SSID_NON_UTF_8_BYTES);
        assertThat(wifiSsidNonUtf8).isNotNull();
        assertThat(wifiSsidNonUtf8.getBytes()).isEqualTo(TEST_SSID_NON_UTF_8_BYTES);

        WifiSsid wifiSsidEmpty = WifiSsid.fromBytes(new byte[0]);
        assertThat(wifiSsidEmpty).isNotNull();
        assertThat(wifiSsidEmpty.getBytes()).isEmpty();

        WifiSsid wifiSsidNull = WifiSsid.fromBytes(null);
        assertThat(wifiSsidNull).isNotNull();
        assertThat(wifiSsidNull.getBytes()).isEmpty();
    }

    /**
     * Verify the behavior of the Parcelable interface implementation.
     */
    public void testParcelable() throws Exception {
        List<WifiSsid> testWifiSsids = Arrays.asList(
                WifiSsid.fromBytes(TEST_SSID_UTF_8_BYTES),
                WifiSsid.fromBytes(TEST_SSID_NON_UTF_8_BYTES));

        for (WifiSsid wifiSsid : testWifiSsids) {
            Parcel parcel = Parcel.obtain();
            wifiSsid.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            assertThat(WifiSsid.CREATOR.createFromParcel(parcel)).isEqualTo(wifiSsid);
        }
    }
}
