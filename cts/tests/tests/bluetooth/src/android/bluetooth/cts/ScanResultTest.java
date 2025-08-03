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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
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
 * Unit test cases for Bluetooth LE scans.
 * <p>
 * To run this test, use adb shell am instrument -e class 'android.bluetooth.ScanResultTest' -w
 * 'com.android.bluetooth.tests/android.bluetooth.BluetoothTestRunner'
 */
@RunWith(AndroidJUnit4.class)
public class ScanResultTest {
    private static final String DEVICE_ADDRESS = "01:02:03:04:05:06";
    private static final byte[] SCAN_RECORD = new byte[] {
            1, 2, 3 };
    private static final int RSSI = -10;
    private static final long TIMESTAMP_NANOS = 10000L;

    @Before
    public void setUp() {
        Assume.assumeTrue(TestUtils.isBleSupported(
                InstrumentationRegistry.getInstrumentation().getContext()));
    }

    /**
     * Test read and write parcel of ScanResult
     */
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void scanResultParceling() {
        BluetoothDevice device =
                BluetoothAdapter.getDefaultAdapter().getRemoteDevice(DEVICE_ADDRESS);
        ScanResult result = new ScanResult(device, TestUtils.parseScanRecord(SCAN_RECORD), RSSI,
                TIMESTAMP_NANOS);
        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        // Need to reset parcel data position to the beginning.
        parcel.setDataPosition(0);
        ScanResult resultFromParcel = ScanResult.CREATOR.createFromParcel(parcel);

        assertEquals(RSSI, resultFromParcel.getRssi());
        assertEquals(TIMESTAMP_NANOS, resultFromParcel.getTimestampNanos());
        assertEquals(device, resultFromParcel.getDevice());
        TestUtils.assertArrayEquals(SCAN_RECORD, resultFromParcel.getScanRecord().getBytes());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void describeContents() {
        BluetoothDevice device =
                BluetoothAdapter.getDefaultAdapter().getRemoteDevice(DEVICE_ADDRESS);
        ScanResult result = new ScanResult(device, TestUtils.parseScanRecord(SCAN_RECORD), RSSI,
                TIMESTAMP_NANOS);
        assertEquals(0, result.describeContents());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void constructor() {
        BluetoothDevice device =
                BluetoothAdapter.getDefaultAdapter().getRemoteDevice(DEVICE_ADDRESS);
        int eventType = 0xAAAA;
        int primaryPhy = 0xAAAB;
        int secondaryPhy = 0xAABA;
        int advertisingSid = 0xAABB;
        int txPower = 0xABAA;
        int rssi = 0xABAB;
        int periodicAdvertisingInterval = 0xABBA;
        long timestampNanos = 0xABBB;
        ScanResult result = new ScanResult(device, eventType, primaryPhy, secondaryPhy,
                advertisingSid, txPower, rssi, periodicAdvertisingInterval, null, timestampNanos);
        assertEquals(result.getDevice(), device);
        assertNull(result.getScanRecord());
        assertEquals(result.getRssi(), rssi);
        assertEquals(result.getTimestampNanos(), timestampNanos);
        assertEquals(result.getDataStatus(), 0x01);
        assertEquals(result.getPrimaryPhy(), primaryPhy);
        assertEquals(result.getSecondaryPhy(), secondaryPhy);
        assertEquals(result.getAdvertisingSid(), advertisingSid);
        assertEquals(result.getTxPower(), txPower);
        assertEquals(result.getPeriodicAdvertisingInterval(), periodicAdvertisingInterval);

        // specific value of event type for isLegacy and isConnectable to be true
        ScanResult result2 = new ScanResult(device, 0x11, primaryPhy, secondaryPhy,
                advertisingSid, txPower, rssi, periodicAdvertisingInterval, null, timestampNanos);
        assertTrue(result2.isLegacy());
        assertTrue(result2.isConnectable());
    }
}
