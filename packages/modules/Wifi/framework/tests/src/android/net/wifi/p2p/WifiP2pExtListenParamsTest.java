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

package android.net.wifi.p2p;

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

public class WifiP2pExtListenParamsTest {
    @Before
    public void setUp() {
        assumeTrue(SdkLevel.isAtLeastV());
    }

    /** Tests that the builder throws an exception if given a null vendor data value. */
    @Test
    public void testBuilderNullVendorData() {
        WifiP2pExtListenParams.Builder builder = new WifiP2pExtListenParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setVendorData(null));
    }

    /** Tests that this class can be properly parceled and unparceled. */
    @Test
    public void testParcelReadWrite() throws Exception {
        List<OuiKeyedData> vendorData = OuiKeyedDataUtil.createTestOuiKeyedDataList(5);
        WifiP2pExtListenParams params =
                new WifiP2pExtListenParams.Builder()
                        .setVendorData(vendorData)
                        .build();
        Parcel parcel = Parcel.obtain();
        params.writeToParcel(parcel, 0);
        parcel.setDataPosition(0); // Rewind data position back to the beginning for read.
        WifiP2pExtListenParams unparceledParams =
                WifiP2pExtListenParams.CREATOR.createFromParcel(parcel);
        assertTrue(vendorData.equals(unparceledParams.getVendorData()));
    }
}
