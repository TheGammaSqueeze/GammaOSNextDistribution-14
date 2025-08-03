/*
 * Copyright 2022 The Android Open Source Project
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

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class BluetoothGattServerCallbackTest {

    private final BluetoothGattServerCallback mCallbacks = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId,
                int offset, BluetoothGattCharacteristic characteristic) {
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattCharacteristic characteristic,
                boolean preparedWrite, boolean responseNeeded,
                int offset, byte[] value) {
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId,
                int offset, BluetoothGattDescriptor descriptor) {
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattDescriptor descriptor,
                boolean preparedWrite, boolean responseNeeded,
                int offset, byte[] value) {
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
        }

        @Override
        public void onPhyUpdate(BluetoothDevice device, int txPhy, int rxPhy, int status) {
        }

        @Override
        public void onPhyRead(BluetoothDevice device, int txPhy, int rxPhy, int status) {
        }

    };
    private final UUID TEST_UUID = UUID.fromString("0000110a-0000-1000-8000-00805f9b34fb");
    private final byte[] mBytes = new byte[]{};
    private Context mContext;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;
    private BluetoothGattService mBluetoothGattService;
    private BluetoothGattDescriptor mBluetoothGattDescriptor;
    private BluetoothGattCharacteristic mBluetoothGattCharacteristic;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        mBluetoothDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        mBluetoothGattService = new BluetoothGattService(TEST_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        mBluetoothGattCharacteristic = new BluetoothGattCharacteristic(TEST_UUID, 0x0A, 0x11);
        mBluetoothGattDescriptor = new BluetoothGattDescriptor(TEST_UUID, 0x11);
    }

    @After
    public void tearDown() throws Exception {
        mAdapter = null;
        mBluetoothDevice = null;
        mBluetoothGattService = null;
        mBluetoothGattCharacteristic = null;
        mBluetoothGattDescriptor = null;
        if (mUiAutomation != null) {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void allMethods() {
        mCallbacks.onConnectionStateChange(mBluetoothDevice, BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_CONNECTED);
        mCallbacks.onServiceAdded(BluetoothGatt.GATT_SUCCESS, mBluetoothGattService);
        mCallbacks.onCharacteristicReadRequest(mBluetoothDevice, 0, 0,
                mBluetoothGattCharacteristic);
        mCallbacks.onCharacteristicWriteRequest(mBluetoothDevice, 0,
                mBluetoothGattCharacteristic,
                true, true, 0, mBytes);
        mCallbacks.onDescriptorReadRequest(mBluetoothDevice, 0, 0,
                mBluetoothGattDescriptor);
        mCallbacks.onDescriptorWriteRequest(mBluetoothDevice, 0,
                mBluetoothGattDescriptor, true,
                true, 0, mBytes);
        mCallbacks.onExecuteWrite(mBluetoothDevice, 0, true);
        mCallbacks.onNotificationSent(mBluetoothDevice, BluetoothGatt.GATT_SUCCESS);
        mCallbacks.onMtuChanged(mBluetoothDevice, 0);
        mCallbacks.onPhyUpdate(mBluetoothDevice, BluetoothDevice.PHY_LE_2M,
                BluetoothDevice.PHY_LE_2M, BluetoothGatt.GATT_SUCCESS);
        mCallbacks.onPhyRead(mBluetoothDevice, BluetoothDevice.PHY_LE_2M,
                BluetoothDevice.PHY_LE_2M,
                BluetoothGatt.GATT_SUCCESS);
    }
}
