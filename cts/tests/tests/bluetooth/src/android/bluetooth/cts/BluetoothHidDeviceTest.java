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

/* You may obtain a copy of the License at
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BluetoothHidDeviceTest {
    private static final String TAG = BluetoothHidDevice.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private Context mContext;
    private boolean mHasBluetooth;
    private boolean mIsHidSupported;
    private boolean mIsProfileReady;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;
    private BluetoothHidDevice mBluetoothHidDevice;


    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mHasBluetooth =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
        if (!mHasBluetooth) return;

        mIsHidSupported = TestUtils.isProfileEnabled(BluetoothProfile.HID_DEVICE);
        if (!mIsHidSupported) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothHidDevice = null;

        mAdapter.getProfileProxy(mContext, new BluetoothHidServiceListener(),
                BluetoothProfile.HID_DEVICE);
    }

    @After
    public void tearDown() throws Exception {
        if (!(mHasBluetooth && mIsHidSupported)) {
            return;
        }
        if (mAdapter != null && mBluetoothHidDevice != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, mBluetoothHidDevice);
            mBluetoothHidDevice = null;
            mIsProfileReady = false;
        }
        mAdapter = null;
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void closeProfileProxy() {
        assumeTrue(mHasBluetooth && mIsHidSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHidDevice);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, mBluetoothHidDevice);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        assumeTrue(mHasBluetooth && mIsHidSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHidDevice);

        assertEquals(new ArrayList<BluetoothDevice>(),
                mBluetoothHidDevice.getDevicesMatchingConnectionStates(
                        new int[]{BluetoothProfile.STATE_CONNECTED}));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices =
                mBluetoothHidDevice.getDevicesMatchingConnectionStates(null);
        assertTrue(connectedDevices.isEmpty());
    }

    @Test
    public void getConnectionState() {
        assumeTrue(mHasBluetooth && mIsHidSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHidDevice);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns STATE_DISCONNECTED when invalid input is given
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothHidDevice.getConnectionState(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns STATE_DISCONNECTED if bluetooth is not enabled
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothHidDevice.getConnectionState(testDevice));
    }

    @Test
    public void connect() {
        assumeTrue(mHasBluetooth && mIsHidSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHidDevice);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertFalse(mBluetoothHidDevice.connect(testDevice));
        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mBluetoothHidDevice.connect(testDevice));
    }

    @Test
    public void disconnect() {
        assumeTrue(mHasBluetooth && mIsHidSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHidDevice);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertFalse(mBluetoothHidDevice.disconnect(testDevice));
        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mBluetoothHidDevice.connect(testDevice));
    }

    @Test
    public void getConnectedDevices() {
        assumeTrue(mHasBluetooth && mIsHidSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHidDevice);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mBluetoothHidDevice.getConnectedDevices();
        assertTrue(connectedDevices.isEmpty());
    }

    @Test
    public void setConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsHidSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHidDevice);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertFalse(mBluetoothHidDevice.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN));
        assertFalse(mBluetoothHidDevice.setConnectionPolicy(
                null, BluetoothProfile.CONNECTION_POLICY_ALLOWED));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothHidDevice.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
    }

    private boolean waitForProfileConnect() {
        mProfileConnectionlock.lock();
        try {
            // Wait for the Adapter to be disabled
            while (!mIsProfileReady) {
                if (!mConditionProfileConnection.await(
                        PROXY_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    // Timeout
                    Log.e(TAG, "Timeout while waiting for Profile Connect");
                    break;
                } // else spurious wakeups
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForProfileConnect: interrupted");
        } finally {
            mProfileConnectionlock.unlock();
        }
        return mIsProfileReady;
    }

    private boolean waitForProfileDisconnect() {
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mProfileConnectionlock.lock();
        try {
            while (mIsProfileReady) {
                if (!mConditionProfileConnection.await(
                        PROXY_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    // Timeout
                    Log.e(TAG, "Timeout while waiting for Profile Disconnect");
                    break;
                } // else spurious wakeups
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForProfileDisconnect: interrupted");
        } finally {
            mProfileConnectionlock.unlock();
        }
        return !mIsProfileReady;
    }

    private final class BluetoothHidServiceListener implements
            BluetoothProfile.ServiceListener {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothHidDevice = (BluetoothHidDevice) proxy;
            mIsProfileReady = true;
            try {
                mConditionProfileConnection.signal();
            } finally {
                mProfileConnectionlock.unlock();
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            mProfileConnectionlock.lock();
            mIsProfileReady = false;
            try {
                mConditionProfileConnection.signal();
            } finally {
                mProfileConnectionlock.unlock();
            }
        }
    }


}
