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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.net.MacAddress;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Test;

import java.util.List;

/**
 * Unit tests for {@link android.net.wifi.SoftApInfo}.
 */
@SmallTest
public class SoftApInfoTest {
    private static final String TEST_AP_INSTANCE = "wlan1";
    private static final int TEST_FREQUENCY = 2412;
    private static final int TEST_BANDWIDTH = SoftApInfo.CHANNEL_WIDTH_20MHZ;
    private static final int TEST_WIFI_STANDARD = ScanResult.WIFI_STANDARD_LEGACY;
    private static final MacAddress TEST_AP_MAC = MacAddress.fromString("aa:bb:cc:dd:ee:ff");
    private static final long TEST_SHUTDOWN_TIMEOUT_MILLIS = 100_000;
    private static final List<OuiKeyedData> TEST_VENDOR_DATA =
            OuiKeyedDataUtil.createTestOuiKeyedDataList(5);

    /**
     * Verifies copy constructor.
     */
    @Test
    public void testCopyOperator() throws Exception {
        SoftApInfo info = new SoftApInfo();
        info.setFrequency(TEST_FREQUENCY);
        info.setBandwidth(TEST_BANDWIDTH);
        info.setBssid(TEST_AP_MAC);
        info.setWifiStandard(TEST_WIFI_STANDARD);
        info.setApInstanceIdentifier(TEST_AP_INSTANCE);
        if (SdkLevel.isAtLeastV()) {
            info.setVendorData(TEST_VENDOR_DATA);
        }

        SoftApInfo copiedInfo = new SoftApInfo(info);

        assertEquals(info, copiedInfo);
        assertEquals(info.hashCode(), copiedInfo.hashCode());
    }

    /**
     * Verifies parcel serialization/deserialization.
     */
    @Test
    public void testParcelOperation() throws Exception {
        SoftApInfo info = new SoftApInfo();
        info.setFrequency(TEST_FREQUENCY);
        info.setBandwidth(TEST_BANDWIDTH);
        info.setBssid(TEST_AP_MAC);
        info.setWifiStandard(TEST_WIFI_STANDARD);
        info.setApInstanceIdentifier(TEST_AP_INSTANCE);
        if (SdkLevel.isAtLeastV()) {
            info.setVendorData(TEST_VENDOR_DATA);
        }

        Parcel parcelW = Parcel.obtain();
        info.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        SoftApInfo fromParcel = SoftApInfo.CREATOR.createFromParcel(parcelR);

        assertEquals(info, fromParcel);
        assertEquals(info.hashCode(), fromParcel.hashCode());
    }


    /**
     * Verifies the initial value same as expected.
     */
    @Test
    public void testInitialValue() throws Exception {
        SoftApInfo info = new SoftApInfo();
        assertEquals(info.getFrequency(), 0);
        assertEquals(info.getBandwidth(), SoftApInfo.CHANNEL_WIDTH_INVALID);
        if (SdkLevel.isAtLeastS()) {
            assertEquals(info.getBssid(), null);
            assertEquals(info.getWifiStandard(), ScanResult.WIFI_STANDARD_UNKNOWN);
            assertEquals(info.getApInstanceIdentifier(), null);
        }
        if (SdkLevel.isAtLeastV()) {
            assertNotNull(info.getVendorData());
        }
    }

    /**
     * Verifies the set/get method same as expected.
     */
    @Test
    public void testGetXXXAlignedWithSetXXX() throws Exception {
        SoftApInfo info = new SoftApInfo();
        info.setFrequency(TEST_FREQUENCY);
        info.setBandwidth(TEST_BANDWIDTH);
        info.setBssid(TEST_AP_MAC);
        info.setWifiStandard(TEST_WIFI_STANDARD);
        info.setApInstanceIdentifier(TEST_AP_INSTANCE);
        info.setAutoShutdownTimeoutMillis(TEST_SHUTDOWN_TIMEOUT_MILLIS);
        assertEquals(info.getFrequency(), TEST_FREQUENCY);
        assertEquals(info.getBandwidth(), TEST_BANDWIDTH);
        if (SdkLevel.isAtLeastS()) {
            assertEquals(info.getBssid(), TEST_AP_MAC);
            assertEquals(info.getWifiStandard(), TEST_WIFI_STANDARD);
            assertEquals(info.getApInstanceIdentifier(), TEST_AP_INSTANCE);
        }
        assertEquals(info.getAutoShutdownTimeoutMillis(), TEST_SHUTDOWN_TIMEOUT_MILLIS);
        if (SdkLevel.isAtLeastV()) {
            info.setVendorData(TEST_VENDOR_DATA);
            assertTrue(TEST_VENDOR_DATA.equals(info.getVendorData()));
        }
    }

}
