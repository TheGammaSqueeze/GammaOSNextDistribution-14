/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import android.bluetooth.le.TransportBlock;
import android.content.Context;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit test cases for {@link TransportBlock}.
 * <p>
 * To run the test, use adb shell am instrument -e class 'android.bluetooth.le.TransportBlockTest' -w
 * 'com.android.bluetooth.tests/android.bluetooth.BluetoothTestRunner'
 */
@RunWith(AndroidJUnit4.class)
public class TransportBlockTest {

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        Assume.assumeTrue(TestUtils.isBleSupported(mContext));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void init() {
        Parcel parcel = Parcel.obtain();
        TransportBlock data = new TransportBlock(1, 0, 2, new byte[] {
                (byte) 0xF0, 0x00 });
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        TransportBlock dataFromParcel =
                TransportBlock.CREATOR.createFromParcel(parcel);
        assertEquals(data, dataFromParcel);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void initEmpty() {
        Parcel parcel = Parcel.obtain();
        TransportBlock data = new TransportBlock(1, 0, 0, null);
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        TransportBlock dataFromParcel =
                TransportBlock.CREATOR.createFromParcel(parcel);
        assertEquals(data, dataFromParcel);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void totalBytes() {
        Parcel parcel = Parcel.obtain();
        TransportBlock data = new TransportBlock(1, 0, 2, new byte[] {
                (byte) 0xF0, 0x00 });
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        TransportBlock dataFromParcel =
                TransportBlock.CREATOR.createFromParcel(parcel);
        assertEquals(data.totalBytes(), 5);
        assertEquals(dataFromParcel.totalBytes(), 5);
        assertEquals(data, dataFromParcel);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void getValues() {
        Parcel parcel = Parcel.obtain();
        TransportBlock data = new TransportBlock(1, 3, 2, new byte[] {
                (byte) 0xF0, 0x00 });
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        TransportBlock dataFromParcel =
                TransportBlock.CREATOR.createFromParcel(parcel);
        assertEquals(data.getOrgId(), 1);
        assertEquals(dataFromParcel.getOrgId(), 1);
        assertEquals(data.getTdsFlags(), 3);
        assertEquals(dataFromParcel.getTdsFlags(), 3);
        assertEquals(data.getTransportDataLength(), 2);
        assertEquals(dataFromParcel.getTransportDataLength(), 2);
        TestUtils.assertArrayEquals(data.getTransportData(), dataFromParcel.getTransportData());
        assertEquals(data, dataFromParcel);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void toByteArray() {
        Parcel parcel = Parcel.obtain();
        TransportBlock data = new TransportBlock(1, 0, 2, new byte[] {
                (byte) 0xF0, 0x00 });
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        TransportBlock dataFromParcel =
                TransportBlock.CREATOR.createFromParcel(parcel);
        TestUtils.assertArrayEquals(data.toByteArray(), dataFromParcel.toByteArray());
        assertEquals(data, dataFromParcel);
    }
}
