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

package android.bluetooth.le;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import androidx.test.filters.SmallTest;
import junit.framework.TestCase;

public class ScanFilterTest extends TestCase {
    @SmallTest
    public void testIrkFilterParcelable() {
        // arrange: an IRK filter
        Parcel parcel = Parcel.obtain();
        var filter =
                new ScanFilter.Builder()
                        .setDeviceAddress(
                                "11:22:33:44:55:66",
                                BluetoothDevice.ADDRESS_TYPE_PUBLIC,
                                new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16})
                        .build();

        // act: serialize and deserialize
        filter.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ScanFilter filterFromParcel = ScanFilter.CREATOR.createFromParcel(parcel);

        // assert: no change
        assertEquals(filter, filterFromParcel);
    }
}
