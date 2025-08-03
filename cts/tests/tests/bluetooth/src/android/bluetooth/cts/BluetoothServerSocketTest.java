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

import static org.junit.Assert.assertThrows;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;

import androidx.test.InstrumentationRegistry;

import java.io.IOException;

public class BluetoothServerSocketTest extends AndroidTestCase {
    private static final int SCAN_STOP_TIMEOUT = 1000;
    private BluetoothServerSocket mBluetoothServerSocket;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;
    private boolean mHasBluetooth;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);
        if (!mHasBluetooth) return;
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        mBluetoothServerSocket = mAdapter.listenUsingL2capChannel();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mHasBluetooth) {
            mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
            if (mHasBluetooth && mBluetoothServerSocket != null) {
                mBluetoothServerSocket.close();
            }
            mAdapter = null;
            mBluetoothServerSocket = null;
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    public void test_accept() throws IOException {
        assertThrows(IOException.class, () -> mBluetoothServerSocket.accept(SCAN_STOP_TIMEOUT));
    }
}
