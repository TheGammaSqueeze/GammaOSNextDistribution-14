/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Tests a small part of the {@link BluetoothGatt} methods without a real Bluetooth device.
 * Other tests that run with real bluetooth connections are located in CtsVerifier.
 */
@RunWith(AndroidJUnit4.class)
public class BasicBluetoothGattTest {
    private static final String TAG = BasicBluetoothGattTest.class.getSimpleName();
    private static final UUID TEST_UUID = UUID.fromString("0000110a-0000-1000-8000-00805f9b34fb");

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue(TestUtils.isBleSupported(context));

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .adoptShellPermissionIdentity(android.Manifest.permission.BLUETOOTH_CONNECT);

        mBluetoothAdapter = context.getSystemService(BluetoothManager.class).getAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            assertTrue(BTAdapterUtils.enableAdapter(mBluetoothAdapter, context));
        }
        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        mBluetoothGatt = mBluetoothDevice.connectGatt(
                context, /*autoConnect=*/ true, new BluetoothGattCallback() {});
        if (mBluetoothGatt == null) {
            try {
                Thread.sleep(500); // Bt is not binded yet. Wait and retry
            } catch (InterruptedException e) {
                Log.e(TAG, "delay connectGatt interrupted");
            }
            mBluetoothGatt = mBluetoothDevice.connectGatt(
                    context, /*autoConnect=*/ true, new BluetoothGattCallback() {});
        }
        assertNotNull(mBluetoothGatt);

    }

    @After
    public void tearDown() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .dropShellPermissionIdentity();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getServices() throws Exception {
        // getServices() returns an empty list if service discovery has not yet been performed.
        List<BluetoothGattService> services = mBluetoothGatt.getServices();
        assertNotNull(services);
        assertTrue(services.isEmpty());
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void connect() throws Exception {
        mBluetoothGatt.connect();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void setPreferredPhy() throws Exception {
        mBluetoothGatt.setPreferredPhy(BluetoothDevice.PHY_LE_1M, BluetoothDevice.PHY_LE_1M,
                BluetoothDevice.PHY_OPTION_NO_PREFERRED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectedDevices() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBluetoothGatt.getConnectedDevices());
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectionState() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBluetoothGatt.getConnectionState(null));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getDevicesMatchingConnectionStates() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBluetoothGatt.getDevicesMatchingConnectionStates(null));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void writeCharacteristic_withValueOverMaxLength() {
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(TEST_UUID,
                0x0A, 0x11);
        BluetoothGattService service = new BluetoothGattService(TEST_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        service.addCharacteristic(characteristic);

        // 512 is the max attribute length
        byte[] value = new byte[513];
        Arrays.fill(value, (byte) 0x01);

        assertThrows(IllegalArgumentException.class, () -> mBluetoothGatt.writeCharacteristic(
                characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT));
    }
}
