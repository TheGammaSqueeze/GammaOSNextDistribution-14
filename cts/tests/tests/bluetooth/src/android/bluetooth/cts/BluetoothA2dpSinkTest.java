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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BluetoothA2dpSinkTest {
    private static final String TAG = BluetoothA2dpSinkTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;;

    private BluetoothA2dpSink mBluetoothA2dpSink;
    private boolean mIsA2dpSinkSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mHasBluetooth = TestUtils.hasBluetooth();
        if (!mHasBluetooth) return;

        mIsA2dpSinkSupported = TestUtils.isProfileEnabled(BluetoothProfile.A2DP_SINK);
        if (!mIsA2dpSinkSupported) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothA2dpSink = null;

        mAdapter.getProfileProxy(mContext, new BluetoothA2dpSinkServiceListener(),
                BluetoothProfile.A2DP_SINK);
    }

    @After
    public void tearDown() throws Exception {
        if (!(mHasBluetooth && mIsA2dpSinkSupported)) {
            return;
        }
        if (mAdapter != null && mBluetoothA2dpSink != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.A2DP_SINK, mBluetoothA2dpSink);
            mBluetoothA2dpSink = null;
            mIsProfileReady = false;
        }
        mUiAutomation.dropShellPermissionIdentity();
        mAdapter = null;
    }

    @Test
    public void closeProfileProxy() {
        assumeTrue(mHasBluetooth && mIsA2dpSinkSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dpSink);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.A2DP_SINK, mBluetoothA2dpSink);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    @Test
    public void getConnectedDevices() {
        assumeTrue(mHasBluetooth && mIsA2dpSinkSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dpSink);

        assertEquals(mBluetoothA2dpSink.getConnectedDevices(), new ArrayList<BluetoothDevice>());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mBluetoothA2dpSink.getConnectedDevices());
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        assumeTrue(mHasBluetooth && mIsA2dpSinkSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dpSink);

        assertEquals(mBluetoothA2dpSink.getDevicesMatchingConnectionStates(
                new int[]{BluetoothProfile.STATE_CONNECTED}),
                new ArrayList<BluetoothDevice>());
    }

    @Test
    public void getConnectionState() {
        assumeTrue(mHasBluetooth && mIsA2dpSinkSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dpSink);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertEquals(mBluetoothA2dpSink.getConnectionState(testDevice),
                BluetoothProfile.STATE_DISCONNECTED);

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class,
                () -> mBluetoothA2dpSink.getConnectionState(testDevice));
    }

    @Test
    public void getConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsA2dpSinkSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dpSink);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothA2dpSink.getConnectionPolicy(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothA2dpSink.getConnectionPolicy(testDevice));
    }

    @Test
    public void setConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsA2dpSinkSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dpSink);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertFalse(mBluetoothA2dpSink.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN));
        assertFalse(mBluetoothA2dpSink.setConnectionPolicy(
                null, BluetoothProfile.CONNECTION_POLICY_ALLOWED));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothA2dpSink.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
    }

    @Test
    public void isAudioPlaying() {
        assumeTrue(mHasBluetooth && mIsA2dpSinkSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dpSink);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertFalse(mBluetoothA2dpSink.isAudioPlaying(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothA2dpSink.isAudioPlaying(testDevice));
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

    private final class BluetoothA2dpSinkServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothA2dpSink = (BluetoothA2dpSink) proxy;
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
