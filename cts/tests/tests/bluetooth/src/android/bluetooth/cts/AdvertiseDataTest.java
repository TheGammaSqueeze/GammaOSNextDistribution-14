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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.TransportBlock;
import android.bluetooth.le.TransportDiscoveryData;
import android.os.Parcel;
import android.os.ParcelUuid;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit test cases for {@link AdvertiseData}.
 * <p>
 * To run the test, use adb shell am instrument -e class 'android.bluetooth.le.AdvertiseDataTest' -w
 * 'com.android.bluetooth.tests/android.bluetooth.BluetoothTestRunner'
 */
@RunWith(AndroidJUnit4.class)
public class AdvertiseDataTest {

    private AdvertiseData.Builder mAdvertiseDataBuilder;

    @Before
    public void setUp() {
        Assume.assumeTrue(TestUtils.isBleSupported(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
        mAdvertiseDataBuilder = new AdvertiseData.Builder();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void emptyData() {
        Parcel parcel = Parcel.obtain();
        AdvertiseData data = mAdvertiseDataBuilder.build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel =
                AdvertiseData.CREATOR.createFromParcel(parcel);
        assertEquals(data, dataFromParcel);
        assertFalse(dataFromParcel.getIncludeDeviceName());
        assertFalse(dataFromParcel.getIncludeTxPowerLevel());
        assertEquals(0, dataFromParcel.getManufacturerSpecificData().size());
        assertTrue(dataFromParcel.getServiceData().isEmpty());
        assertTrue(dataFromParcel.getServiceUuids().isEmpty());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void emptyServiceUuid() {
        Parcel parcel = Parcel.obtain();
        AdvertiseData data = mAdvertiseDataBuilder.setIncludeDeviceName(true).build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel =
                AdvertiseData.CREATOR.createFromParcel(parcel);
        assertEquals(data, dataFromParcel);
        assertTrue(dataFromParcel.getIncludeDeviceName());
        assertTrue(dataFromParcel.getServiceUuids().isEmpty());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void emptyManufacturerData() {
        Parcel parcel = Parcel.obtain();
        int manufacturerId = 50;
        byte[] manufacturerData = new byte[0];
        AdvertiseData data =
                mAdvertiseDataBuilder.setIncludeDeviceName(true)
                        .addManufacturerData(manufacturerId, manufacturerData).build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel =
                AdvertiseData.CREATOR.createFromParcel(parcel);
        assertEquals(data, dataFromParcel);
        TestUtils.assertArrayEquals(new byte[0], dataFromParcel.getManufacturerSpecificData().get(manufacturerId));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void emptyServiceData() {
        Parcel parcel = Parcel.obtain();
        ParcelUuid uuid = ParcelUuid.fromString("0000110A-0000-1000-8000-00805F9B34FB");
        byte[] serviceData = new byte[0];
        AdvertiseData data =
                mAdvertiseDataBuilder.setIncludeDeviceName(true)
                        .addServiceData(uuid, serviceData).build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel =
                AdvertiseData.CREATOR.createFromParcel(parcel);
        assertEquals(data, dataFromParcel);
        TestUtils.assertArrayEquals(new byte[0], dataFromParcel.getServiceData().get(uuid));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void serviceUuid() {
        Parcel parcel = Parcel.obtain();
        ParcelUuid uuid = ParcelUuid.fromString("0000110A-0000-1000-8000-00805F9B34FB");
        ParcelUuid uuid2 = ParcelUuid.fromString("0000110B-0000-1000-8000-00805F9B34FB");

        AdvertiseData data =
                mAdvertiseDataBuilder.setIncludeDeviceName(true)
                        .addServiceUuid(uuid).addServiceUuid(uuid2).build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel =
                AdvertiseData.CREATOR.createFromParcel(parcel);
        assertEquals(data, dataFromParcel);
        assertTrue(dataFromParcel.getServiceUuids().contains(uuid));
        assertTrue(dataFromParcel.getServiceUuids().contains(uuid2));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void serviceSolicitationUuids() {
        AdvertiseData emptyData = mAdvertiseDataBuilder.build();
        assertEquals(0, emptyData.getServiceSolicitationUuids().size());

        Parcel parcel = Parcel.obtain();
        ParcelUuid uuid = ParcelUuid.fromString("0000110A-0000-1000-8000-00805F9B34FB");
        ParcelUuid uuid2 = ParcelUuid.fromString("0000110B-0000-1000-8000-00805F9B34FB");

        AdvertiseData data =
                mAdvertiseDataBuilder.setIncludeDeviceName(true)
                        .addServiceSolicitationUuid(uuid).addServiceSolicitationUuid(uuid2).build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel =
                AdvertiseData.CREATOR.createFromParcel(parcel);
        assertEquals(data, dataFromParcel);
        assertTrue(dataFromParcel.getServiceSolicitationUuids().contains(uuid));
        assertTrue(dataFromParcel.getServiceSolicitationUuids().contains(uuid2));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void manufacturerData() {
        Parcel parcel = Parcel.obtain();
        ParcelUuid uuid = ParcelUuid.fromString("0000110A-0000-1000-8000-00805F9B34FB");
        ParcelUuid uuid2 = ParcelUuid.fromString("0000110B-0000-1000-8000-00805F9B34FB");

        int manufacturerId = 50;
        byte[] manufacturerData = new byte[] {
                (byte) 0xF0, 0x00, 0x02, 0x15 };
        AdvertiseData data =
                mAdvertiseDataBuilder.setIncludeDeviceName(true)
                        .addServiceUuid(uuid).addServiceUuid(uuid2)
                        .addManufacturerData(manufacturerId, manufacturerData).build();

        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel =
                AdvertiseData.CREATOR.createFromParcel(parcel);
        assertEquals(data, dataFromParcel);
        TestUtils.assertArrayEquals(manufacturerData,
                dataFromParcel.getManufacturerSpecificData().get(manufacturerId));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void serviceData() {
        Parcel parcel = Parcel.obtain();
        ParcelUuid uuid = ParcelUuid.fromString("0000110A-0000-1000-8000-00805F9B34FB");
        byte[] serviceData = new byte[] {
                (byte) 0xF0, 0x00, 0x02, 0x15 };
        AdvertiseData data =
                mAdvertiseDataBuilder.setIncludeDeviceName(true)
                        .addServiceData(uuid, serviceData).build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel =
                AdvertiseData.CREATOR.createFromParcel(parcel);
        assertEquals(data, dataFromParcel);
        TestUtils.assertArrayEquals(serviceData, dataFromParcel.getServiceData().get(uuid));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void transportDiscoveryData() {
        Parcel parcel = Parcel.obtain();
        ParcelUuid uuid = ParcelUuid.fromString("0000110A-0000-1000-8000-00805F9B34FB");
        List<TransportBlock> transportBlocks = new ArrayList();
        transportBlocks.add(new TransportBlock(1, 0, 4, new byte[] {
                (byte) 0xF0, 0x00, 0x02, 0x15 }));
        TransportDiscoveryData discoveryData = new TransportDiscoveryData(0, transportBlocks);
        AdvertiseData data =
                mAdvertiseDataBuilder.setIncludeDeviceName(true)
                        .addTransportDiscoveryData(discoveryData).build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel =
                AdvertiseData.CREATOR.createFromParcel(parcel);

        assertEquals(discoveryData.getTransportDataType(),
                dataFromParcel.getTransportDiscoveryData().get(0).getTransportDataType());

        assertEquals(discoveryData.getTransportBlocks().get(0).getOrgId(),
                dataFromParcel.getTransportDiscoveryData().get(0).getTransportBlocks().get(0).getOrgId());

        assertEquals(discoveryData.getTransportBlocks().get(0).getTdsFlags(),
                dataFromParcel.getTransportDiscoveryData().get(0).getTransportBlocks().get(0).getTdsFlags());

        assertEquals(discoveryData.getTransportBlocks().get(0).totalBytes(),
                dataFromParcel.getTransportDiscoveryData().get(0).getTransportBlocks().get(0).totalBytes());

        TestUtils.assertArrayEquals(discoveryData.toByteArray(),
                dataFromParcel.getTransportDiscoveryData().get(0).toByteArray());

        assertEquals(data, dataFromParcel);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void includeTxPower() {
        Parcel parcel = Parcel.obtain();
        AdvertiseData data = mAdvertiseDataBuilder.setIncludeTxPowerLevel(true).build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel =
                AdvertiseData.CREATOR.createFromParcel(parcel);
        assertEquals(dataFromParcel.getIncludeTxPowerLevel(), true);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void describeContents() {
        AdvertiseData data = new AdvertiseData.Builder().build();
        assertEquals(0, data.describeContents());
    }
}
