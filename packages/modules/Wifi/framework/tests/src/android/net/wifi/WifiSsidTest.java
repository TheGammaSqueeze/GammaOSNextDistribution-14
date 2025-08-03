/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static org.junit.Assert.fail;

import android.net.wifi.util.HexEncoding;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link android.net.wifi.WifiSsid}.
 */
@SmallTest
public class WifiSsidTest {

    private static final String TEST_SSID_UTF_8 = "Test SSID";
    private static final String TEST_SSID_UTF_8_QUOTED = "\"" + TEST_SSID_UTF_8 + "\"";
    private static final byte[] TEST_SSID_UTF_8_BYTES =
            TEST_SSID_UTF_8.getBytes(StandardCharsets.UTF_8);
    private static final String TEST_SSID_UTF_8_HEX =
            HexEncoding.encodeToString(TEST_SSID_UTF_8_BYTES, false /* upperCase */);

    private static final byte[] TEST_SSID_NON_UTF_8_BYTES =
            "服務集識別碼".getBytes(Charset.forName("GBK"));
    private static final String TEST_SSID_NON_UTF_8_HEX =
            HexEncoding.encodeToString(TEST_SSID_NON_UTF_8_BYTES, false /* upperCase */);

    /**
     * Verify the behavior of fromByteArray()
     */
    @Test
    public void testFromByteArray() {
        WifiSsid wifiSsidUtf8 = WifiSsid.fromBytes(TEST_SSID_UTF_8_BYTES);
        assertThat(wifiSsidUtf8).isNotNull();
        assertThat(wifiSsidUtf8.getBytes()).isEqualTo(TEST_SSID_UTF_8_BYTES);
        assertThat(wifiSsidUtf8.getUtf8Text()).isEqualTo(TEST_SSID_UTF_8);
        assertThat(wifiSsidUtf8.toString()).isEqualTo(TEST_SSID_UTF_8_QUOTED);

        WifiSsid wifiSsidNonUtf8 = WifiSsid.fromBytes(TEST_SSID_NON_UTF_8_BYTES);
        assertThat(wifiSsidNonUtf8).isNotNull();
        assertThat(wifiSsidNonUtf8.getBytes()).isEqualTo(TEST_SSID_NON_UTF_8_BYTES);
        assertThat(wifiSsidNonUtf8.getUtf8Text()).isNull();
        assertThat(wifiSsidNonUtf8.toString()).isEqualTo(TEST_SSID_NON_UTF_8_HEX);

        WifiSsid wifiSsidEmpty = WifiSsid.fromBytes(new byte[0]);
        assertThat(wifiSsidEmpty).isNotNull();
        assertThat(wifiSsidEmpty.getBytes()).isEmpty();
        assertThat(wifiSsidEmpty.getUtf8Text().toString()).isEmpty();
        assertThat(wifiSsidEmpty.toString()).isEmpty();

        WifiSsid wifiSsidNull = WifiSsid.fromBytes(null);
        assertThat(wifiSsidNull).isNotNull();
        assertThat(wifiSsidNull.getBytes()).isEmpty();
        assertThat(wifiSsidNull.getUtf8Text().toString()).isEmpty();
        assertThat(wifiSsidNull.toString()).isEmpty();
    }

    /**
     * Verify the behavior of fromUtf8String()
     */
    @Test
    public void testFromUtf8String() {
        WifiSsid wifiSsidUtf8 = WifiSsid.fromUtf8Text(TEST_SSID_UTF_8);
        assertThat(wifiSsidUtf8).isNotNull();
        assertThat(wifiSsidUtf8.getBytes()).isEqualTo(TEST_SSID_UTF_8_BYTES);
        assertThat(wifiSsidUtf8.getUtf8Text()).isEqualTo(TEST_SSID_UTF_8);
        assertThat(wifiSsidUtf8.toString()).isEqualTo(TEST_SSID_UTF_8_QUOTED);

        WifiSsid wifiSsidEmpty = WifiSsid.fromUtf8Text("");
        assertThat(wifiSsidEmpty).isNotNull();
        assertThat(wifiSsidEmpty.getBytes()).isEmpty();
        assertThat(wifiSsidEmpty.getUtf8Text().toString()).isEmpty();
        assertThat(wifiSsidEmpty.toString()).isEmpty();

        WifiSsid wifiSsidNull = WifiSsid.fromUtf8Text(null);
        assertThat(wifiSsidNull).isNotNull();
        assertThat(wifiSsidNull.getBytes()).isEmpty();
        assertThat(wifiSsidNull.getUtf8Text().toString()).isEmpty();
        assertThat(wifiSsidNull.toString()).isEmpty();
    }

    /**
     * Verify the behavior of fromString()
     */
    @Test
    public void testFromString() {
        WifiSsid wifiSsidUtf8 = WifiSsid.fromString(TEST_SSID_UTF_8_QUOTED);
        assertThat(wifiSsidUtf8).isNotNull();
        assertThat(wifiSsidUtf8.getBytes()).isEqualTo(TEST_SSID_UTF_8_BYTES);
        assertThat(wifiSsidUtf8.getUtf8Text()).isEqualTo(TEST_SSID_UTF_8);
        assertThat(wifiSsidUtf8.toString()).isEqualTo(TEST_SSID_UTF_8_QUOTED);

        WifiSsid wifiSsidUtf8Hex = WifiSsid.fromString(TEST_SSID_UTF_8_HEX);
        assertThat(wifiSsidUtf8Hex).isNotNull();
        assertThat(wifiSsidUtf8Hex.getBytes()).isEqualTo(TEST_SSID_UTF_8_BYTES);
        assertThat(wifiSsidUtf8Hex.getUtf8Text()).isEqualTo(TEST_SSID_UTF_8);
        assertThat(wifiSsidUtf8Hex.toString()).isEqualTo(TEST_SSID_UTF_8_QUOTED);

        WifiSsid wifiSsidNonUtf8 = WifiSsid.fromString(TEST_SSID_NON_UTF_8_HEX);
        assertThat(wifiSsidNonUtf8).isNotNull();
        assertThat(wifiSsidNonUtf8.getBytes()).isEqualTo(TEST_SSID_NON_UTF_8_BYTES);
        assertThat(wifiSsidNonUtf8.getUtf8Text()).isNull();
        assertThat(wifiSsidNonUtf8.toString()).isEqualTo(TEST_SSID_NON_UTF_8_HEX);

        WifiSsid wifiSsidEmpty = WifiSsid.fromUtf8Text("");
        assertThat(wifiSsidEmpty).isNotNull();
        assertThat(wifiSsidEmpty.getBytes()).isEmpty();
        assertThat(wifiSsidEmpty.getUtf8Text().toString()).isEmpty();
        assertThat(wifiSsidEmpty.toString()).isEmpty();

        WifiSsid wifiSsidNull = WifiSsid.fromUtf8Text(null);
        assertThat(wifiSsidNull).isNotNull();
        assertThat(wifiSsidNull.getBytes()).isEmpty();
        assertThat(wifiSsidNull.getUtf8Text().toString()).isEmpty();
        assertThat(wifiSsidNull.toString()).isEmpty();

        try {
            WifiSsid.fromString("0123456");
            fail("Expected IllegalArgumentException for odd-length hexadecimal string");
        } catch (IllegalArgumentException e) {
            // Success
        }
    }

    /**
     * Verify that SSID created from bytes, UTF-8 String, and toString()-formatted String with the
     * same content are equal.
     *
     * @throws Exception
     */
    @Test
    public void testEquals() throws Exception {
        WifiSsid fromBytesUtf8 = WifiSsid.fromBytes(TEST_SSID_UTF_8_BYTES);
        WifiSsid fromUtf8StringUtf8 = WifiSsid.fromUtf8Text(TEST_SSID_UTF_8);
        WifiSsid fromStringUtf8 = WifiSsid.fromString(TEST_SSID_UTF_8_QUOTED);
        assertThat(fromBytesUtf8).isNotNull();
        assertThat(fromUtf8StringUtf8).isNotNull();
        assertThat(fromStringUtf8).isNotNull();
        assertThat(fromBytesUtf8).isEqualTo(fromUtf8StringUtf8);
        assertThat(fromBytesUtf8).isEqualTo(fromStringUtf8);
        assertThat(fromUtf8StringUtf8).isEqualTo(fromStringUtf8);

        WifiSsid fromBytesNonUtf8 = WifiSsid.fromBytes(TEST_SSID_NON_UTF_8_BYTES);
        WifiSsid fromStringNonUtf8 = WifiSsid.fromString(TEST_SSID_NON_UTF_8_HEX);
        assertThat(fromBytesNonUtf8).isNotNull();
        assertThat(fromStringNonUtf8).isNotNull();
        assertThat(fromBytesNonUtf8).isEqualTo(fromStringNonUtf8);

        assertThat(fromBytesUtf8).isNotEqualTo(fromBytesNonUtf8);
    }

    /**
     * Verify the behavior of the Parcelable interface implementation.
     */
    @Test
    public void testParcelable() throws Exception {
        List<WifiSsid> testWifiSsids = Arrays.asList(
                WifiSsid.fromBytes(TEST_SSID_UTF_8_BYTES),
                WifiSsid.fromBytes(TEST_SSID_NON_UTF_8_BYTES),
                WifiSsid.fromUtf8Text(TEST_SSID_UTF_8),
                WifiSsid.fromString(TEST_SSID_UTF_8_QUOTED),
                WifiSsid.fromString(TEST_SSID_UTF_8_HEX),
                WifiSsid.fromString(TEST_SSID_NON_UTF_8_HEX));

        for (WifiSsid wifiSsid : testWifiSsids) {
            Parcel parcel = Parcel.obtain();
            wifiSsid.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            assertThat(WifiSsid.CREATOR.createFromParcel(parcel)).isEqualTo(wifiSsid);
        }
    }
}
