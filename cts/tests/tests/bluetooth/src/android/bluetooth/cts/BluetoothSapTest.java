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
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSap;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class BluetoothSapTest {
    private static final String TAG = BluetoothSapTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;

    private BluetoothSap mBluetoothSap;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    private boolean mIsSapSupported;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mHasBluetooth = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);

        if (!mHasBluetooth) return;

        mIsSapSupported = TestUtils.isProfileEnabled(BluetoothProfile.SAP);
        if (!mIsSapSupported) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        mAdapter = mContext.getSystemService(BluetoothManager.class).getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothSap = null;

        mAdapter.getProfileProxy(mContext, new BluetoothSapServiceListener(),
                BluetoothProfile.SAP);
    }

    @After
    public void tearDown() throws Exception {
        if (mHasBluetooth && mIsSapSupported) {
            if (mAdapter != null && mBluetoothSap != null) {
                mBluetoothSap.close();
                mBluetoothSap = null;
                mIsProfileReady = false;
            }
            mUiAutomation.dropShellPermissionIdentity();
            mAdapter = null;
        }
    }

    @Test
    public void closeProfileProxy() {
        assumeTrue(mHasBluetooth && mIsSapSupported);

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothSap);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.SAP, mBluetoothSap);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    @Test
    @MediumTest
    public void getConnectedDevices() {
        assumeTrue(mHasBluetooth && mIsSapSupported);

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothSap);

        assertNotNull(mBluetoothSap.getConnectedDevices());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mBluetoothSap.getConnectedDevices());
    }

    @Test
    @MediumTest
    public void getDevicesMatchingConnectionStates() {
        assumeTrue(mHasBluetooth && mIsSapSupported);

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothSap);

        int[] connectionState = new int[]{BluetoothProfile.STATE_CONNECTED};

        assertTrue(mBluetoothSap.getDevicesMatchingConnectionStates(connectionState).isEmpty());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class,
                () -> mBluetoothSap.getDevicesMatchingConnectionStates(connectionState));
    }

    @Test
    @MediumTest
    public void getConnectionState() {
        assumeTrue(mHasBluetooth && mIsSapSupported);

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothSap);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertEquals(mBluetoothSap.getConnectionState(testDevice),
                BluetoothProfile.STATE_DISCONNECTED);

        mUiAutomation.dropShellPermissionIdentity();
        assertEquals(mBluetoothSap.getConnectionState(testDevice),
                BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    @MediumTest
    public void setgetConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsSapSupported);

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothSap);

        assertThrows(NullPointerException.class, () -> mBluetoothSap.setConnectionPolicy(null, 0));
        assertThrows(NullPointerException.class, () -> mBluetoothSap.getConnectionPolicy(null));

        mUiAutomation.dropShellPermissionIdentity();
        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        assertThrows(SecurityException.class, () -> mBluetoothSap.setConnectionPolicy(testDevice,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
        assertThrows(SecurityException.class, () -> mBluetoothSap.getConnectionPolicy(testDevice));
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

    private final class BluetoothSapServiceListener implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothSap = (BluetoothSap) proxy;
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
