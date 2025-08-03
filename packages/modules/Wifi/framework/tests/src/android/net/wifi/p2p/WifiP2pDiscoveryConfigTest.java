/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.net.wifi.OuiKeyedData;
import android.net.wifi.OuiKeyedDataUtil;
import android.os.Parcel;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class WifiP2pDiscoveryConfigTest {
    private static final int TEST_SCAN_TYPE = WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ;
    private static final int TEST_FREQUENCY_MHZ = 900;

    @Before
    public void setUp() {
        assumeTrue(SdkLevel.isAtLeastV());
    }

    /** Tests that the builder throws an exception if given an invalid integer argument. */
    @Test
    public void testBuilderInvalidIntegerArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                new WifiP2pDiscoveryConfig.Builder(-1 /* scan type */).build());

        WifiP2pDiscoveryConfig.Builder singleFreqBuilder =
                new WifiP2pDiscoveryConfig.Builder(WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ);
        assertThrows(IllegalArgumentException.class, () -> singleFreqBuilder.setFrequencyMhz(-1));
        // Expect exception on build, since a valid frequency was not set.
        assertThrows(IllegalArgumentException.class, () -> singleFreqBuilder.build());
    }

    /** Tests that the builder throws an exception if given a null vendor data value. */
    @Test
    public void testBuilderNullVendorData() {
        WifiP2pDiscoveryConfig.Builder builder =
                new WifiP2pDiscoveryConfig.Builder(TEST_SCAN_TYPE);
        assertThrows(IllegalArgumentException.class, () -> builder.setVendorData(null));
    }

    /** Tests that this class can be properly parceled and unparceled. */
    @Test
    public void testParcelReadWrite() throws Exception {
        List<OuiKeyedData> vendorData = OuiKeyedDataUtil.createTestOuiKeyedDataList(5);
        WifiP2pDiscoveryConfig config =
                new WifiP2pDiscoveryConfig.Builder(TEST_SCAN_TYPE)
                        .setFrequencyMhz(TEST_FREQUENCY_MHZ)
                        .setVendorData(vendorData)
                        .build();
        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0); // Rewind data position back to the beginning for read.
        WifiP2pDiscoveryConfig unparceledConfig =
                WifiP2pDiscoveryConfig.CREATOR.createFromParcel(parcel);

        assertEquals(TEST_SCAN_TYPE, unparceledConfig.getScanType());
        assertEquals(TEST_FREQUENCY_MHZ, unparceledConfig.getFrequencyMhz());
        assertTrue(vendorData.equals(unparceledConfig.getVendorData()));
    }
}
