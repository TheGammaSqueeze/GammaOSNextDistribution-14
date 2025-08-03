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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class BluetoothGattServerTest {

    private static final int LATCH_TIMEOUT_MS = 1000;
    private final UUID TEST_UUID = UUID.fromString("0000110a-0000-1000-8000-00805f9b34fb");
    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothManager mBluetoothManager;
    private UiAutomation mUIAutomation;
    private CountDownLatch mLatch;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        mUIAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUIAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        mBluetoothAdapter = mContext.getSystemService(BluetoothManager.class).getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mBluetoothAdapter, mContext));
        mBluetoothManager = mContext.getSystemService(BluetoothManager.class);
        mLatch = new CountDownLatch(1);
        mBluetoothGattServer = mBluetoothManager.openGattServer(mContext,
                new BluetoothGattServerCallback() {
                    @Override
                    public void onServiceAdded(int status, BluetoothGattService service) {
                        mLatch.countDown();
                    }
                });
    }

    @After
    public void tearDown() throws Exception {
        if (mUIAutomation != null) {
            mUIAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        }

        if (mBluetoothAdapter != null && mBluetoothGattServer != null) {
            mBluetoothGattServer.close();
            mBluetoothGattServer = null;
        }

        mBluetoothAdapter = null;
        mLatch = null;

        if (mUIAutomation != null) {
            mUIAutomation.dropShellPermissionIdentity();
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectedDevices() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBluetoothGattServer.getConnectedDevices());
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectionState() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBluetoothGattServer.getConnectionState(null));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getDevicesMatchingConnectionStates() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBluetoothGattServer.getDevicesMatchingConnectionStates(null));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getService() {
        // Service is null after initialization with public constructor
        assertNull(mBluetoothGattServer.getService(TEST_UUID));
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(TEST_UUID,
                0x0A, 0x11);
        BluetoothGattService service = new BluetoothGattService(TEST_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        service.addCharacteristic(characteristic);
        // If service is added successfully, latch.countDown() happens in the callback
        mBluetoothGattServer.addService(service);
        try {
            mLatch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("should not throw an InterruptedException");
        }

        assertEquals(mBluetoothGattServer.getService(TEST_UUID), service);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getServices() {
        assertEquals(mBluetoothGattServer.getServices(), new ArrayList<BluetoothGattService>());
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void readPhy() {
        BluetoothDevice testDevice = mBluetoothAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        mUIAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mBluetoothGattServer.readPhy(testDevice));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void setPreferredPhy() {
        BluetoothDevice testDevice = mBluetoothAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        mUIAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mBluetoothGattServer.setPreferredPhy(testDevice,
                BluetoothDevice.PHY_LE_1M_MASK, BluetoothDevice.PHY_LE_1M_MASK,
                BluetoothDevice.PHY_OPTION_NO_PREFERRED));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void notifyCharacteristicChanged_withValueOverMaxLength() {
        BluetoothDevice testDevice = mBluetoothAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(TEST_UUID,
                0x0A, 0x11);
        BluetoothGattService service = new BluetoothGattService(TEST_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        service.addCharacteristic(characteristic);

        // 512 is the max attribute length
        byte[] notification = new byte[513];
        Arrays.fill(notification, (byte) 0x01);

        assertThrows(IllegalArgumentException.class,
                () -> mBluetoothGattServer.notifyCharacteristicChanged(testDevice, characteristic,
                        false, notification));
    }
}
