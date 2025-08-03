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

import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAssignedNumbers.OrganizationId;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.TransportBlockFilter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Parcel;
import android.os.ParcelUuid;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit test cases for Bluetooth LE scan filters.
 * <p>
 * To run this test, use adb shell am instrument -e class 'android.bluetooth.ScanFilterTest' -w
 * 'com.android.bluetooth.tests/android.bluetooth.BluetoothTestRunner'
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ScanFilterTest {

    private static final String LOCAL_NAME = "Ped";
    private static final String DEVICE_MAC = "01:02:03:04:05:AB";
    private static final String UUID1 = "0000110a-0000-1000-8000-00805f9b34fb";
    private static final String UUID2 = "0000110b-0000-1000-8000-00805f9b34fb";
    private static final String UUID3 = "0000110c-0000-1000-8000-00805f9b34fb";
    private static final int AD_TYPE_RESOLVABLE_SET_IDENTIFIER = 0x2e;

    private ScanResult mScanResult;
    private ScanFilter.Builder mFilterBuilder;
    private BluetoothAdapter mBluetoothAdapter;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Assume.assumeTrue(TestUtils.isBleSupported(context));

        byte[] scanRecord = new byte[] {
                0x02, 0x01, 0x1a, // advertising flags
                0x05, 0x02, 0x0b, 0x11, 0x0a, 0x11, // 16 bit service uuids
                0x04, 0x09, 0x50, 0x65, 0x64, // setName
                0x02, 0x0A, (byte) 0xec, // tx power level
                0x05, 0x16, 0x0b, 0x11, 0x50, 0x64, // service data
                0x05, (byte) 0xff, (byte) 0xe0, 0x00, 0x02, 0x15, // manufacturer specific data
                0x05, 0x14, 0x0c, 0x11, 0x0a, 0x11, // 16 bit service solicitation uuids
                0x03, 0x50, 0x01, 0x02, // an unknown data type won't cause trouble
                0x07, 0x2E, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06 // resolvable set identifier
        };

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Bluetooth is not supported
            assertFalse(context.getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));
        } else {
            assertTrue(context.getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(DEVICE_MAC);
            mScanResult = new ScanResult(device, TestUtils.parseScanRecord(scanRecord),
                    -10, 1397545200000000L);
            mFilterBuilder = new ScanFilter.Builder();
            TestUtils.adoptPermissionAsShellUid(BLUETOOTH_PRIVILEGED);
        }
    }

    @After
    public void tearDown() {
        if (mFilterBuilder != null) {
            TestUtils.dropPermissionAsShellUid();
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setNameFilter() {
        ScanFilter filter = mFilterBuilder.setDeviceName(LOCAL_NAME).build();
        assertEquals(LOCAL_NAME, filter.getDeviceName());
        assertTrue("setName filter fails", filter.matches(mScanResult));

        filter = mFilterBuilder.setDeviceName("Pem").build();
        assertFalse("setName filter fails", filter.matches(mScanResult));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void deviceAddressFilter() {
        ScanFilter filter = mFilterBuilder.setDeviceAddress(DEVICE_MAC).build();
        assertEquals(DEVICE_MAC, filter.getDeviceAddress());
        assertTrue("device filter fails", filter.matches(mScanResult));

        filter = mFilterBuilder.setDeviceAddress("11:22:33:44:55:66").build();
        assertFalse("device filter fails", filter.matches(mScanResult));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setServiceUuidFilter() {
        ScanFilter filter = mFilterBuilder.setServiceUuid(
                ParcelUuid.fromString(UUID1)).build();
        assertEquals(UUID1, filter.getServiceUuid().toString());
        assertTrue("uuid filter fails", filter.matches(mScanResult));

        filter = mFilterBuilder.setServiceUuid(
                ParcelUuid.fromString(UUID3)).build();
        assertEquals(UUID3, filter.getServiceUuid().toString());
        assertFalse("uuid filter fails", filter.matches(mScanResult));

        ParcelUuid mask = ParcelUuid.fromString("FFFFFFF0-FFFF-FFFF-FFFF-FFFFFFFFFFFF");
        filter = mFilterBuilder
                .setServiceUuid(ParcelUuid.fromString(UUID3),
                        mask)
                .build();
        assertEquals(mask.toString(), filter.getServiceUuidMask().toString());
        assertTrue("uuid filter fails", filter.matches(mScanResult));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setServiceSolicitationUuidFilter() {
        ScanFilter filter = mFilterBuilder.setServiceSolicitationUuid(
                ParcelUuid.fromString(UUID1)).build();
        assertEquals(UUID1, filter.getServiceSolicitationUuid().toString());
        assertTrue("uuid filter fails", filter.matches(mScanResult));

        filter = mFilterBuilder.setServiceSolicitationUuid(
                ParcelUuid.fromString(UUID2)).build();
        assertEquals(UUID2, filter.getServiceSolicitationUuid().toString());
        assertFalse("uuid filter fails", filter.matches(mScanResult));

        ParcelUuid mask = ParcelUuid.fromString("FFFFFFF0-FFFF-FFFF-FFFF-FFFFFFFFFFFF");
        filter = mFilterBuilder
                .setServiceSolicitationUuid(ParcelUuid.fromString(UUID3), mask)
                .build();
        assertEquals(mask.toString(), filter.getServiceSolicitationUuidMask().toString());
        assertTrue("uuid filter fails", filter.matches(mScanResult));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setServiceDataFilter() {
        byte[] setServiceData = new byte[] {
                0x50, 0x64 };
        ParcelUuid serviceDataUuid = ParcelUuid.fromString(UUID2);
        ScanFilter filter = mFilterBuilder.setServiceData(serviceDataUuid, setServiceData).build();
        assertEquals(serviceDataUuid, filter.getServiceDataUuid());
        assertTrue("service data filter fails", filter.matches(mScanResult));

        byte[] emptyData = new byte[0];
        filter = mFilterBuilder.setServiceData(serviceDataUuid, emptyData).build();
        assertTrue("service data filter fails", filter.matches(mScanResult));

        byte[] prefixData = new byte[] {
                0x50 };
        filter = mFilterBuilder.setServiceData(serviceDataUuid, prefixData).build();
        assertTrue("service data filter fails", filter.matches(mScanResult));

        byte[] nonMatchData = new byte[] {
                0x51, 0x64 };
        byte[] mask = new byte[] {
                (byte) 0x00, (byte) 0xFF };
        filter = mFilterBuilder.setServiceData(serviceDataUuid, nonMatchData, mask).build();
        assertEquals(nonMatchData, filter.getServiceData());
        assertEquals(mask, filter.getServiceDataMask());
        assertTrue("partial service data filter fails", filter.matches(mScanResult));

        filter = mFilterBuilder.setServiceData(serviceDataUuid, nonMatchData).build();
        assertFalse("service data filter fails", filter.matches(mScanResult));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setManufacturerSpecificData() {
        byte[] manufacturerData = new byte[] {
                0x02, 0x15 };
        int manufacturerId = 0xE0;
        ScanFilter filter =
                mFilterBuilder.setManufacturerData(manufacturerId, manufacturerData).build();
        assertEquals(manufacturerId, filter.getManufacturerId());
        assertEquals(manufacturerData, filter.getManufacturerData());
        assertTrue("manufacturer data filter fails", filter.matches(mScanResult));

        byte[] emptyData = new byte[0];
        filter = mFilterBuilder.setManufacturerData(manufacturerId, emptyData).build();
        assertTrue("manufacturer data filter fails", filter.matches(mScanResult));

        byte[] prefixData = new byte[] {
                0x02 };
        filter = mFilterBuilder.setManufacturerData(manufacturerId, prefixData).build();
        assertTrue("manufacturer data filter fails", filter.matches(mScanResult));

        // Test data mask
        byte[] nonMatchData = new byte[] {
                0x02, 0x14 };
        filter = mFilterBuilder.setManufacturerData(manufacturerId, nonMatchData).build();
        assertFalse("manufacturer data filter fails", filter.matches(mScanResult));
        byte[] mask = new byte[] {
                (byte) 0xFF, (byte) 0x00
        };
        filter = mFilterBuilder.setManufacturerData(manufacturerId, nonMatchData, mask).build();
        assertEquals(manufacturerId, filter.getManufacturerId());
        assertEquals(nonMatchData, filter.getManufacturerData());
        assertEquals(mask, filter.getManufacturerDataMask());
        assertTrue("partial setManufacturerData filter fails", filter.matches(mScanResult));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setAdvertisingDataTypeWithData() {
        byte[] adData = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
        byte[] adDataMask = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF};
        ScanFilter filter = mFilterBuilder.setAdvertisingDataTypeWithData(
                AD_TYPE_RESOLVABLE_SET_IDENTIFIER, adData, adDataMask).build();
        assertEquals(AD_TYPE_RESOLVABLE_SET_IDENTIFIER, filter.getAdvertisingDataType());
        TestUtils.assertArrayEquals(adData, filter.getAdvertisingData());
        TestUtils.assertArrayEquals(adDataMask, filter.getAdvertisingDataMask());
        assertTrue("advertising data filter fails", filter.matches(mScanResult));
        filter = mFilterBuilder.setAdvertisingDataTypeWithData(0x01, adData, adDataMask).build();
        assertFalse("advertising data filter fails", filter.matches(mScanResult));
        byte[] nonMatchAdData = {0x01, 0x02, 0x04, 0x04, 0x05, 0x06};
        filter = mFilterBuilder.setAdvertisingDataTypeWithData(AD_TYPE_RESOLVABLE_SET_IDENTIFIER,
                nonMatchAdData, adDataMask).build();
        assertFalse("advertising data filter fails", filter.matches(mScanResult));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void readWriteParcel() {
        ScanFilter filter = mFilterBuilder.build();
        testReadWriteParcelForFilter(filter);

        filter = mFilterBuilder.setDeviceName(LOCAL_NAME).build();
        testReadWriteParcelForFilter(filter);

        filter = mFilterBuilder.setDeviceAddress("11:22:33:44:55:66").build();
        testReadWriteParcelForFilter(filter);

        filter = mFilterBuilder.setServiceUuid(
                ParcelUuid.fromString(UUID3)).build();
        testReadWriteParcelForFilter(filter);

        filter = mFilterBuilder.setServiceUuid(
                ParcelUuid.fromString(UUID3),
                ParcelUuid.fromString("FFFFFFF0-FFFF-FFFF-FFFF-FFFFFFFFFFFF")).build();
        testReadWriteParcelForFilter(filter);

        byte[] serviceData = new byte[] {
                0x50, 0x64 };

        ParcelUuid serviceDataUuid = ParcelUuid.fromString(UUID2);
        filter = mFilterBuilder.setServiceData(serviceDataUuid, serviceData).build();
        testReadWriteParcelForFilter(filter);

        filter = mFilterBuilder.setServiceData(serviceDataUuid, new byte[0]).build();
        testReadWriteParcelForFilter(filter);

        byte[] serviceDataMask = new byte[] {
                (byte) 0xFF, (byte) 0xFF };
        filter = mFilterBuilder.setServiceData(serviceDataUuid, serviceData, serviceDataMask)
                .build();
        testReadWriteParcelForFilter(filter);

        byte[] manufacturerData = new byte[] {
                0x02, 0x15 };
        int manufacturerId = 0xE0;
        filter = mFilterBuilder.setManufacturerData(manufacturerId, manufacturerData).build();
        testReadWriteParcelForFilter(filter);

        filter = mFilterBuilder.setServiceData(serviceDataUuid, new byte[0]).build();
        testReadWriteParcelForFilter(filter);

        byte[] manufacturerDataMask = new byte[] {
                (byte) 0xFF, (byte) 0xFF
        };
        filter = mFilterBuilder.setManufacturerData(manufacturerId, manufacturerData,
                manufacturerDataMask).build();
        testReadWriteParcelForFilter(filter);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void describeContents() {
        final int expected = 0;
        assertEquals(expected, new ScanFilter.Builder().build().describeContents());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void builderSetTransportBlockFilter() {
        final int orgId = OrganizationId.BLUETOOTH_SIG;
        final int tdsFlag = 0x2;
        final int tdsFlagMask = 0b11;
        final byte[] transportData = new byte[] { 0x42, 0x43 };
        final byte[] transportDataMask = new byte[] { 0x44, 0x45 };

        TransportBlockFilter transportBlockFilter = new TransportBlockFilter.Builder(orgId)
                .setTdsFlags(tdsFlag, tdsFlagMask)
                .setTransportData(transportData, transportDataMask).build();

        if (mBluetoothAdapter.getOffloadedTransportDiscoveryDataScanSupported()
                != FEATURE_SUPPORTED) {
            assertThrows(IllegalArgumentException.class,
                    () -> mFilterBuilder.setTransportBlockFilter(transportBlockFilter));
            return;
        }

        final ScanFilter filter = mFilterBuilder
                .setTransportBlockFilter(transportBlockFilter)
                .build();

        final TransportBlockFilter returnedTransportBlockFilter = filter.getTransportBlockFilter();
        assertNotNull(returnedTransportBlockFilter);
        assertEquals(orgId, returnedTransportBlockFilter.getOrgId());
        assertEquals(tdsFlag, returnedTransportBlockFilter.getTdsFlags());
        assertEquals(tdsFlagMask, returnedTransportBlockFilter.getTdsFlagsMask());
        assertArrayEquals(transportData, returnedTransportBlockFilter.getTransportData());
        assertArrayEquals(transportDataMask, returnedTransportBlockFilter.getTransportDataMask());
    }

    private void testReadWriteParcelForFilter(ScanFilter filter) {
        Parcel parcel = Parcel.obtain();
        filter.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ScanFilter filterFromParcel =
                ScanFilter.CREATOR.createFromParcel(parcel);
        assertEquals(filter, filterFromParcel);
    }
}
