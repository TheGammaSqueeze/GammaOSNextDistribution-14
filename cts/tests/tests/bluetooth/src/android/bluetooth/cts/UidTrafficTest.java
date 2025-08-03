/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.bluetooth.cts;


import android.bluetooth.UidTraffic;
import android.os.Parcel;
import android.test.AndroidTestCase;


public class UidTrafficTest extends AndroidTestCase {

    UidTraffic mUidTraffic;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        final Parcel uidTrafficParcel = Parcel.obtain();
        uidTrafficParcel.writeInt(1000);
        uidTrafficParcel.writeLong(2000);
        uidTrafficParcel.writeLong(3000);
        uidTrafficParcel.setDataPosition(0);
        mUidTraffic = UidTraffic.CREATOR.createFromParcel(uidTrafficParcel);
        uidTrafficParcel.recycle();
    }

    public void test_UidTrafficClone() {
        assertNotNull(mUidTraffic);
        UidTraffic clonedUidTraffic = mUidTraffic.clone();
        assertNotNull(clonedUidTraffic);
        assertEquals(mUidTraffic.getUid(), clonedUidTraffic.getUid());
        assertEquals(mUidTraffic.getRxBytes(), clonedUidTraffic.getRxBytes());
        assertEquals(mUidTraffic.getTxBytes(), clonedUidTraffic.getTxBytes());
    }

    public void test_UidTrafficGet() {
        assertNotNull(mUidTraffic);
        assertEquals(mUidTraffic.getUid(), 1000);
        assertEquals(mUidTraffic.getRxBytes(), 2000);
        assertEquals(mUidTraffic.getTxBytes(), 3000);
    }
}
